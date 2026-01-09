use anyhow::Result;
use bytes::Bytes;
use fastwebsockets::{handshake, FragmentCollector, Frame, OpCode, Payload};
use http_body_util::Empty;
use hyper::header::{CONNECTION, UPGRADE};
use hyper::upgrade::Upgraded;
use hyper::Request;
use hyper_util::rt::TokioIo;
use reqwest::multipart::Form;
use serde::Deserialize;
use std::collections::HashMap;
use std::future::Future;
use tokio::net::TcpStream;

use crate::cache::CachedFrameProvider;
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

#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type", content = "payload")]
pub enum WsMessage {
    #[serde(rename = "CAPTURE_SCREEN")]
    CaptureScreen { frame_type: FrameType },
    #[serde(rename = "DISCONNECT")]
    Disconnect,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum FrameType {
    Alpha,
    Beta,
    Unspecified,
}

pub enum ClientResult {
    Disconnected,
    ServerError,
}

pub enum Connection {
    Upgrade(FragmentCollector<TokioIo<Upgraded>>, String),
    InvalidPin,
    ServerError,
}

pub async fn connect(
    pin: &str,
    server: &str,
    firstname: &str,
    lastname: &str,
) -> Result<Connection> {
    let stream = TcpStream::connect(&server).await?;

    let res = reqwest::Client::new()
        .post(format!("http://{server}/exams/join/{pin}"))
        .json(&HashMap::<&str, &str>::from_iter([
            ("firstname", firstname),
            ("lastname", lastname),
        ]))
        .send()
        .await?;

    let Some(location) = res.headers().get("location") else {
        return Ok(Connection::InvalidPin);
    };
    let location = location.to_str()?;

    let Some(session_id) = location.split('/').last() else {
        return Ok(Connection::ServerError);
    };

    let req = Request::builder()
        .method("GET")
        .uri(location)
        .header("Host", server)
        .header(UPGRADE, "websocket")
        .header(CONNECTION, "upgrade")
        .header("Sec-WebSocket-Key", handshake::generate_key())
        .header("Sec-WebSocket-Version", "13")
        .body(Empty::<Bytes>::new())?;

    let (mut ws, _) = handshake::client(&SpawnExecutor, req, stream).await?;
    ws.set_auto_pong(false);
    Ok(Connection::Upgrade(
        FragmentCollector::new(ws),
        session_id.to_string(),
    ))
}

/// Run a single client connection
pub async fn run_client(
    pin: &str,
    server: &str,
    firstname: &str,
    lastname: &str,
    client_id: u32,
    mut frame_provider: CachedFrameProvider,
) -> Result<ClientResult> {
    let connection = connect(pin, server, firstname, lastname).await?;

    let (mut ws, session) = match connection {
        Connection::Upgrade(ws, session) => (ws, session),
        Connection::InvalidPin | Connection::ServerError => {
            return Ok(ClientResult::ServerError);
        }
    };

    println!("[Client {}] Connected with session: {}", client_id, session);

    loop {
        let msg = match ws.read_frame().await {
            Ok(msg) => msg,
            Err(e) => {
                let _ = ws.write_frame(Frame::close_raw(vec![].into())).await;
                return Err(e.into());
            }
        };

        match msg.opcode {
            OpCode::Ping => {
                let _ = ws.write_frame(Frame::pong(msg.payload)).await;
                continue;
            }
            OpCode::Close => {
                return Ok(ClientResult::Disconnected);
            }
            _ => {}
        }

        let parsed = match &msg.payload {
            Payload::Bytes(buf) => {
                let raw: String = buf.iter().map(|&b| b as char).collect();
                serde_json::from_str::<WsMessage>(&raw)
            }
            _ => continue,
        };

        match parsed {
            Ok(WsMessage::Disconnect) => {
                return Ok(ClientResult::Disconnected);
            }
            Ok(WsMessage::CaptureScreen { frame_type }) => {
                let expect_alpha = matches!(frame_type, FrameType::Alpha);

                // Get next precomputed frame
                let frame = frame_provider.next_frame();

                // Get pre-encoded PNG bytes (zero CPU work, just memory access)
                let (file_part, option) = screen::get_upload_part(expect_alpha, frame);

                let Ok(file_part) = file_part else {
                    eprintln!(
                        "[Client {}] ERROR: unable to create upload part: {:?}",
                        client_id, file_part
                    );
                    continue;
                };

                let path = format!(
                    "http://{}/telemetry/by-session/{}/screen/upload/{}",
                    server, session, option,
                );

                if let Err(e) = reqwest::Client::new()
                    .post(&path)
                    .multipart(Form::new().part("image", file_part))
                    .send()
                    .await
                {
                    eprintln!("[Client {}] Upload error: {:?}", client_id, e);
                }
            }
            Err(_) => {
                // Ignore unparseable messages
            }
        }
    }
}
