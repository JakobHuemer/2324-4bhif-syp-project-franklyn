use anyhow::Result;
use bytes::Bytes;
use fastwebsockets::{handshake, FragmentCollector, Frame, Payload};
use futures::SinkExt;
use http_body_util::Empty;
use hyper::header::{CONNECTION, UPGRADE};
use hyper::upgrade::Upgraded;
use hyper::Request;
use hyper_util::rt::TokioIo;
use iced::{stream, Subscription};
use image::RgbaImage;
use reqwest::multipart::Form;
use serde::Deserialize;
use std::collections::HashMap;
use std::future::Future;
use tokio::net::TcpStream;

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
    Disconnect,
    UpdateImage(RgbaImage),
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", content = "payload")]
enum WsMessage {
    #[serde(rename = "CAPTURE_SCREEN")]
    CaptureScreen { frame_type: Option<FrameType> },
    #[serde(rename = "DISCONNECT")]
    Disconnect,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
enum FrameType {
    Alpha,
    Beta,
    Unspecified,
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
                    match handle_message(&server_address, &session_id, ws, current_image.as_ref())
                        .await 
                    {
                        Event::UpdateImage(img) => {
                            current_image = Some(img.clone());
                            let _ = output.send(Event::UpdateImage(img)).await;
                        }
                        event => _ = output.send(event).await,
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
) -> Event {
    let msg = match ws.read_frame().await {
        Ok(msg) => msg,
        Err(_e) => {
            let _ = ws.write_frame(Frame::close_raw(vec![].into())).await;
            return Event::Nothing;
        }
    };

    let Ok(payload) = (match msg.payload {
        Payload::Bytes(buf) => {
            let s = buf.iter().map(|&b| b as char).collect::<String>();
            serde_json::from_str::<WsMessage>(&s)
        }
        _ => panic!("TODO: figure out if we get other payloads"),
    }) else {
        return Event::Nothing;
    };

    let (file_part, image, option) = match payload {
        WsMessage::Disconnect => return Event::Disconnect,
        WsMessage::CaptureScreen { frame_type } => match frame_type.unwrap() {
            FrameType::Alpha => screen::take_screenshot(true, cur_img),
            FrameType::Beta | FrameType::Unspecified => screen::take_screenshot(false, cur_img),
        },
    };

    let path = format!(
        "http://{}/telemetry/by-session/{}/screen/upload/{}",
        server_address, session_id, option,
    );

    if let Err(e) = reqwest::Client::new()
        .post(path)
        .multipart(Form::new().part("image", file_part))
        .send()
        .await {
            eprintln!("{:?}", e);
    }

    Event::UpdateImage(image)
}
