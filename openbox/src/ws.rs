use std::collections::HashMap;
use std::future::Future;

use iced::{stream, Subscription};

use futures::SinkExt;
use tokio::net::TcpStream;

use anyhow::Result;
use bytes::Bytes;

use http_body_util::Empty;
use hyper::header::{CONNECTION, UPGRADE};
use hyper::upgrade::Upgraded;
use hyper::Request;
use hyper_util::rt::TokioIo;

use image::RgbaImage;
use reqwest::multipart::{Form, Part};

use fastwebsockets::{handshake, FragmentCollector, Frame, OpCode, Payload};
use log::info;

use crate::screen;

struct SpawnExecutor;

impl<Fut> hyper::rt::Executor<Fut> for SpawnExecutor
where
    Fut: Future + Send + 'static,
    Fut::Output: Send + 'static,
{
    fn execute(&self, fut: Fut) {
        tokio::task::spawn(fut);
    }
}

pub enum State {
    Disconnected,
    Connected(FragmentCollector<TokioIo<Upgraded>>),
}

#[derive(Debug, Clone)]
pub enum Event {
    Nothing,
    UpdateImage(RgbaImage),
}

pub async fn connect(
    server_address: &str,
    pin: &str,
    firstname: &str,
    lastname: &str,
) -> Result<(FragmentCollector<TokioIo<Upgraded>>, String)> {
    let stream = TcpStream::connect(&server_address).await?;

    let res = reqwest::Client::new()
        .post(format!("http://{server_address}/exams/join/{pin}"))
        .json(&HashMap::<&str, &str>::from_iter([
            ("firstname", firstname),
            ("lastname", lastname),
        ]))
        .send()
        .await?;

    let location = res.headers().get("location").unwrap().to_str().unwrap();
    let session_id = location.split('/').last().unwrap().to_string();

    let req = Request::builder()
        .method("GET")
        .uri(location)
        .header("Host", server_address)
        .header(UPGRADE, "websocket")
        .header(CONNECTION, "upgrade")
        .header("Sec-WebSocket-Key", handshake::generate_key())
        .header("Sec-WebSocket-Version", "13")
        .body(Empty::<Bytes>::new())?;

    let (ws, _) = handshake::client(&SpawnExecutor, req, stream).await?;
    Ok((FragmentCollector::new(ws), session_id))
}

pub fn subscribe(
    pin: String,
    firstname: String,
    lastname: String,
    server_address: String,
    mut current_image: Option<RgbaImage>,
) -> Subscription<Event> {
    let connection_cloj = stream::channel(100, move |mut output| async move {
        let mut state = State::Disconnected;
        let mut session_id = String::new();

        loop {
            match &mut state {
                State::Disconnected => {
                    match connect(&server_address, &pin, &firstname, &lastname).await {
                        Ok((ws, session)) => (state, session_id) = (State::Connected(ws), session),
                        Err(e) => {
                            eprintln!("Disconnected with error: {e:?}");
                            tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
                        }
                    }

                    let _ = output.send(Event::Nothing).await;
                }
                State::Connected(ws) => {
                    let new_image =
                        handle_message(&server_address, &firstname, ws, current_image.as_ref())
                            .await;

                    if let Some(image) = new_image {
                        current_image = Some(image);
                    } else {
                        let _ = output.send(Event::Nothing).await;
                    }
                }
            }
        }
    });

    struct Connect;
    Subscription::run_with_id(std::any::TypeId::of::<Connect>(), connection_cloj)
}

pub async fn handle_message(
    server_address: &str,
    session_id: &str,
    ws: &mut FragmentCollector<TokioIo<Upgraded>>,
    cur_img: Option<&RgbaImage>,
) -> Option<RgbaImage> {
    let msg = match ws.read_frame().await {
        Ok(msg) => msg,
        Err(e) => {
            eprintln!("{e:?}");
            ws.write_frame(Frame::close_raw(vec![].into()))
                .await
                .unwrap();
            return None;
        }
    };

    let payload = match msg.payload {
        Payload::Bytes(buf) => buf,
        _ => panic!("TODO: figure out if we get other payloads"),
    };

    let (file_part, image, option) = match &payload[..] {
        b"{\"type\":\"CAPTURE_SCREEN\",\"payload\":{\"frame_type\":\"ALPHA\"}}" => 
            screen::take_screenshot(true, cur_img),
        b"BETA" => todo!(),
        b"{\"type\":\"CAPTURE_SCREEN\",\"payload\":{\"frame_type\":\"UNSPECIFIED\"}}" => 
            screen::take_screenshot(false, cur_img),
        p => panic!("ERROR: invalid payload {p:?}"),
    };

    let path = format!(
        "http://{}/telemetry/by-session/{}/screen/upload/{}",
        server_address, session_id, option,
    );

    let res = reqwest::Client::new()
        .post(path)
        .multipart(Form::new().part("image", file_part))
        .send()
        .await
        .unwrap();

    println!("{res:?}");

    Some(image)
}
