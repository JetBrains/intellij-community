//! The proxy side of the fake-agent control protocol: one agent process on the control server in the IDE, as
//! `AcpFakeAgentControlClient` of the agent jar is one.
//!
//! The proxy says `hello` and waits for the `helloAck`, which binds its identity: the connection id, the generation,
//! the process id and the agent key. Every later envelope carries that identity, and every command must carry it too.
//! A command with another identity, an unknown type or a missing field ends the connection.

use std::io;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::Duration;

use serde_json::{Map, Value, json};

use super::ControlEndpoint;
use crate::lane::services::encoding::Base64;
use crate::lane::services::websocket::{self, Message, Receiver, Sender};

/// The version of the control protocol that the proxy says and that the `helloAck` must answer.
pub(super) const PROTOCOL_VERSION: u64 = 2;

/// Bounds the connection and the `hello`, as the control client of the agent jar does.
const HELLO_TIMEOUT: Duration = Duration::from_secs(30);

/// One `kind` of a `lifecycle` envelope.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Lifecycle {
    StdinEof,
    StdoutClosed,
    ProcessExit,
    ControlFailure,
}

impl Lifecycle {
    const fn wire(self) -> &'static str {
        match self {
            Self::StdinEof => "stdin_eof",
            Self::StdoutClosed => "stdout_closed",
            Self::ProcessExit => "process_exit",
            Self::ControlFailure => "control_failure",
        }
    }
}

/// One command of the control server.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(super) enum Command {
    /// Sends the line to the IDE.
    Write { id: String, line: String },
    /// Sends the bytes to the IDE.
    WriteMalformed { id: String, bytes: Vec<u8> },
    /// Closes the output of the agent.
    CloseStdout { id: String },
    /// Ends the agent process with the code.
    Exit { id: String, code: i64 },
    /// Calls a tool, or lists the tools, of an MCP server of the session.
    Mcp { id: String },
}

/// The identity that the `helloAck` bound.
#[derive(Clone, Debug, PartialEq, Eq)]
struct Identity {
    connection_id: String,
    generation: u64,
    process_id: String,
    agent_key: String,
}

impl Identity {
    /// The identity fields of an envelope.
    fn fields(&self) -> Map<String, Value> {
        let mut fields = Map::new();
        fields.insert("connectionId".to_owned(), json!(self.connection_id));
        fields.insert("generation".to_owned(), json!(self.generation));
        fields.insert("processId".to_owned(), json!(self.process_id));
        fields.insert("agentKey".to_owned(), json!(self.agent_key));
        fields
    }

    fn carried_by(&self, envelope: &Value) -> bool {
        envelope["connectionId"] == self.connection_id.as_str()
            && envelope["generation"].as_u64() == Some(self.generation)
            && envelope["processId"] == self.process_id.as_str()
            && envelope["agentKey"] == self.agent_key.as_str()
    }
}

/// The writing half of the proxy. Each method sends one envelope.
pub(super) struct Proxy {
    sender: Sender,
    identity: Identity,
    sequence: AtomicU64,
    frames: AtomicU64,
    closed: AtomicBool,
}

/// The reading half of the proxy: the commands of the control server.
pub(super) struct Commands {
    receiver: Receiver,
    identity: Identity,
}

/// Connects to the control server of `control` as the agent process `process_id`, and completes the `hello`.
pub(super) fn connect(control: &ControlEndpoint, process_id: &str) -> io::Result<(Proxy, Commands)> {
    let (sender, mut receiver) = websocket::connect(&control.url, &[], HELLO_TIMEOUT)?;
    let hello = json!({
        "type": "hello",
        "processId": process_id,
        "agentKey": control.agent_key,
        "protocolVersion": PROTOCOL_VERSION,
        "sequenceOrigin": 0,
    });
    sender.text(&hello.to_string())?;
    let ack = match receiver.next(&sender)? {
        Message::Text(text) => parse(&text)?,
        Message::Close(code) => return Err(broken(&format!("the control server closed with {code:?} before the helloAck"))),
    };
    let identity = Identity {
        connection_id: ack["connectionId"].as_str().unwrap_or_default().to_owned(),
        generation: ack["generation"].as_u64().unwrap_or_default(),
        process_id: process_id.to_owned(),
        agent_key: control.agent_key.clone(),
    };
    if ack["type"] != "helloAck"
        || ack["processId"] != process_id
        || ack["agentKey"] != control.agent_key.as_str()
        || ack["protocolVersion"].as_u64() != Some(PROTOCOL_VERSION)
        || identity.connection_id.trim().is_empty()
        || identity.generation == 0
    {
        return Err(invalid(&format!("the helloAck {ack} binds no valid identity")));
    }
    receiver.set_read_timeout(None)?;
    let proxy = Proxy {
        sender,
        identity: identity.clone(),
        sequence: AtomicU64::new(0),
        frames: AtomicU64::new(0),
        closed: AtomicBool::new(false),
    };
    Ok((proxy, Commands { receiver, identity }))
}

impl Proxy {
    /// Forwards one line that the IDE sent, as the next `inbound` frame.
    pub(super) fn inbound(&self, line: &str) -> io::Result<()> {
        let frame = self.frames.fetch_add(1, Ordering::SeqCst) + 1;
        let sequence = self.sequence.fetch_add(1, Ordering::SeqCst) + 1;
        self.send(
            "inbound",
            json!({ "frameId": format!("frame-{frame}"), "sequence": sequence, "rawLine": line }),
        )
    }

    /// Acknowledges the write, close or exit command `id`.
    pub(super) fn ack(&self, id: &str) -> io::Result<()> {
        self.send("writeAck", json!({ "commandId": id }))
    }

    /// Answers the MCP command `id` with an error result of `text`.
    pub(super) fn mcp_error(&self, id: &str, text: &str) -> io::Result<()> {
        self.send("mcpResult", json!({ "commandId": id, "isError": true, "text": text }))
    }

    /// Reports one event of the agent process.
    pub(super) fn lifecycle(&self, kind: Lifecycle, details: &str, exit_code: Option<i64>) -> io::Result<()> {
        let mut fields = json!({ "kind": kind.wire(), "details": details });
        if let Some(code) = exit_code {
            fields["exitCode"] = json!(code);
        }
        self.send("lifecycle", fields)
    }

    /// Closes the connection with a normal close, once. A later envelope is not sent.
    pub(super) fn close(&self) {
        if !self.closed.swap(true, Ordering::SeqCst) {
            self.sender.close(websocket::NORMAL_CLOSURE);
        }
    }

    /// Ends the connection at once, without a close frame.
    pub(super) fn shutdown(&self) {
        self.closed.store(true, Ordering::SeqCst);
        self.sender.shutdown();
    }

    /// The sender of the connection, which answers a ping of the control server.
    pub(super) const fn sender(&self) -> &Sender {
        &self.sender
    }

    fn send(&self, kind: &str, fields: Value) -> io::Result<()> {
        if self.closed.load(Ordering::SeqCst) {
            return Err(broken("the control connection is closed"));
        }
        let mut envelope = Map::new();
        envelope.insert("type".to_owned(), json!(kind));
        envelope.extend(self.identity.fields());
        if let Value::Object(fields) = fields {
            envelope.extend(fields);
        }
        self.sender.text(&Value::Object(envelope).to_string())
    }
}

impl Commands {
    /// The next command of the control server. A ping gets its pong through `proxy`. The end of the connection, a
    /// close and a command that breaks the protocol are an error.
    pub(super) fn next(&mut self, proxy: &Proxy) -> io::Result<Command> {
        let text = match self.receiver.next(proxy.sender())? {
            Message::Text(text) => text,
            Message::Close(code) => return Err(broken(&format!("the control server closed with {code:?}"))),
        };
        let envelope = parse(&text)?;
        if !self.identity.carried_by(&envelope) {
            return Err(invalid(&format!("the command {envelope} carries another identity")));
        }
        let field = |name: &str| envelope[name].as_str().filter(|value| !value.trim().is_empty());
        let id = field("commandId")
            .ok_or_else(|| invalid(&format!("the command {envelope} has no commandId")))?
            .to_owned();
        let missing = |what: &str| invalid(&format!("the command {envelope} has no {what}"));
        Ok(match envelope["type"].as_str().unwrap_or_default() {
            "write" => Command::Write {
                line: envelope["rawLine"].as_str().ok_or_else(|| missing("rawLine"))?.to_owned(),
                id,
            },
            "writeMalformed" => Command::WriteMalformed {
                bytes: envelope["bytesBase64"]
                    .as_str()
                    .and_then(|text| Base64::Standard.decode(text))
                    .ok_or_else(|| missing("bytesBase64"))?,
                id,
            },
            "closeStdout" => Command::CloseStdout { id },
            "exit" => Command::Exit {
                code: envelope["code"].as_i64().ok_or_else(|| missing("code"))?,
                id,
            },
            "mcpCall" if field("server").is_some() && field("tool").is_some() && envelope["arguments"].is_object() => Command::Mcp { id },
            "mcpListTools" if field("server").is_some() => Command::Mcp { id },
            "mcpCall" => return Err(missing("server, tool or arguments")),
            "mcpListTools" => return Err(missing("server")),
            other => return Err(invalid(&format!("the command type {other:?} is unknown"))),
        })
    }
}

fn parse(text: &str) -> io::Result<Value> {
    serde_json::from_str::<Value>(text)
        .ok()
        .filter(Value::is_object)
        .ok_or_else(|| invalid(&format!("the control envelope {text:?} is no JSON object")))
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.to_owned())
}

fn broken(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::ConnectionAborted, message.to_owned())
}
