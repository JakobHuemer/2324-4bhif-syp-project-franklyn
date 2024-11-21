use anyhow::Result;
use bytes::Bytes;
use fastwebsockets::{handshake, FragmentCollector, Frame, Payload};
use futures::{SinkExt, StreamExt};
use http_body_util::Empty;
use hyper::header::{CONNECTION, UPGRADE};
use hyper::upgrade::Upgraded;
use hyper::Request;
use hyper_util::rt::TokioIo;
use iced::{stream, Subscription, futures::channel::mpsc};
use image::RgbaImage;
use reqwest::multipart::Form;
use serde::Deserialize;
use std::collections::HashMap;
use std::future::Future;
use tokio::task;
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
    Connected(FragmentCollector<TokioIo<Upgraded>>),
    Disconnected,
}

#[derive(Debug, Clone)]
pub enum Event {
    Reconnect,
    Disconnect,
    Received(WsMessage),
}

#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type", content = "payload")]
pub enum WsMessage {
    #[serde(rename = "CAPTURE_SCREEN")]
    CaptureScreen { frame_type: Option<FrameType> },
    #[serde(rename = "DISCONNECT")]
    Disconnect,

    // used for process_screenshots to update session_id
    SetId(String),
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum FrameType {
    Alpha,
    Beta,
    Unspecified,
}

pub async fn connect(
    pin: &str,
    server: &str,
    firstname: &str,
    lastname: &str,
) -> Result<(FragmentCollector<TokioIo<Upgraded>>, String)> {
    let stream = TcpStream::connect(&server).await?;

    let res = reqwest::Client::new()
        .post(format!("http://{server}/exams/join/{pin}"))
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
        .header("Host", server)
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
    server: String,
    firstname: String,
    lastname: String,
) -> Subscription<Event> {

    let connection_cloj = stream::channel(100, move |mut output| async move {
        let mut state = State::Disconnected;

        let (mut sender, receiver) = mpsc::channel(5);
        let s = server.clone();
        task::spawn(async move { process_screenshots(s, receiver).await });

        loop {
            match &mut state {
                State::Disconnected => {
                    match connect(&pin, &server, &firstname, &lastname).await {
                        Ok((ws, session)) => {
                            sender.send(WsMessage::SetId(session)).await.unwrap();
                            state = State::Connected(ws);
                        }
                        Err(e) => {
                            eprintln!("Disconnected with error: {e:?}");
                            tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
                        }
                    }
                }
                State::Connected(ws) => {
                    match handle_message(ws).await {
                        Event::Received(ws_msg) => sender.send(ws_msg).await.unwrap(),
                        Event::Reconnect => state = State::Disconnected,
                        Event::Disconnect => _ = output.send(Event::Disconnect).await,
                    }
                }
            }
        }
    });

    struct Connect;
    Subscription::run_with_id(std::any::TypeId::of::<Connect>(), connection_cloj)
}

pub async fn handle_message(ws: &mut FragmentCollector<TokioIo<Upgraded>>) -> Event {
    let msg = match ws.read_frame().await {
        Ok(msg) => msg,
        Err(_e) => {
            let _ = ws.write_frame(Frame::close_raw(vec![].into())).await;
            return Event::Reconnect;
        }
    };

    match msg.payload {
        Payload::Bytes(buf) => {
            let raw = buf.iter().map(|&b| b as char).collect::<String>();
            let msg = serde_json::from_str::<WsMessage>(&raw);

            match msg {
                Ok(WsMessage::Disconnect) 
                | Err(_) => Event::Disconnect,
                Ok(msg) => Event::Received(msg),
            }
        }
        _ => return Event::Disconnect,
    }
}

async fn process_screenshots(server: String, mut receiver: mpsc::Receiver<WsMessage>) {
    let mut session = String::new();
    let mut cur_img = None::<RgbaImage>;

    loop {
        let msg = receiver.select_next_some().await;

        let (file_part, image, option) = match msg {
            WsMessage::SetId(id) => {
                session = id;
                continue;
            }
            WsMessage::CaptureScreen { frame_type } => match frame_type.unwrap() {
                FrameType::Alpha => screen::take_screenshot(true, cur_img),
                FrameType::Beta | FrameType::Unspecified => screen::take_screenshot(false, cur_img),
            },
            _ => unreachable!(),
        };

        let path = format!(
            "http://{}/telemetry/by-session/{}/screen/upload/{}",
            server, session, option,
        );

        if let Err(e) = reqwest::Client::new()
            .post(path)
            .multipart(Form::new().part("image", file_part))
            .send()
            .await {
                eprintln!("{:?}", e);
        }

        cur_img = Some(image);
    }
}
