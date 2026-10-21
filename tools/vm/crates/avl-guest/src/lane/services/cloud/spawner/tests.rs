//! The Agent Spawner over real sockets: a dial of the IDE as the codec client writes it, and a fake control server in
//! the IDE that answers the `hello` and records every envelope of the proxy.

use std::io::{ErrorKind, Read, Write};
use std::net::{SocketAddr, TcpListener};
use std::sync::{Arc, mpsc};
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::lane::services::Service;
use crate::lane::services::cloud::{self, Cloud, Organization};
use crate::lane::services::encoding::Base64;
use crate::lane::services::testing::{self, TOKEN};
use crate::lane::services::websocket::{Role, encode_frame, opcode, read_frame};

/// The token of the one credential of the fake control server.
const CONTROL_TOKEN: &str = "control-test-token";

/// The key of the Cloud agent of the tests.
const AGENT: &str = "remote-scenarios";

/// The bearer token that the IDE mints for the spawner.
const BEARER: &str = "Bearer air-lane-jcp-agent-spawner";

/// Bounds every wait of a test.
const WAIT: Duration = Duration::from_secs(10);

/// The control server in the IDE as the tests need it: one credential, the `helloAck`, every envelope of the proxy in
/// arrival order, and one session for each accepted proxy. The end of a connection is the envelope `<close>` with the
/// code of the proxy, or `<end>`.
struct FakeControl {
    address: SocketAddr,
    envelopes: mpsc::Receiver<Value>,
    sessions: mpsc::Receiver<Session>,
}

/// One accepted proxy, which a test sends commands to.
struct Session {
    sender: Arc<Sender>,
    process_id: String,
    connection: u64,
}

impl Session {
    /// Sends the command `kind` with `fields` and the identity of the session.
    fn command(&self, kind: &str, fields: &Value) {
        let mut envelope = json!({
            "type": kind,
            "connectionId": format!("connection-{}", self.connection),
            "generation": self.connection,
            "processId": self.process_id,
            "agentKey": AGENT,
        });
        envelope.as_object_mut().unwrap().extend(fields.as_object().unwrap().clone());
        self.sender.text(&envelope.to_string()).unwrap();
    }

    /// The identity fields that every envelope of the proxy carries.
    fn identity(&self) -> Value {
        json!({
            "connectionId": format!("connection-{}", self.connection),
            "generation": self.connection,
            "processId": self.process_id,
            "agentKey": AGENT,
        })
    }
}

impl FakeControl {
    fn start() -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let (envelope_sender, envelopes) = mpsc::channel();
        let (session_sender, sessions) = mpsc::channel();
        thread::spawn(move || {
            for (connection, stream) in (1..).zip(listener.incoming()) {
                let Ok(stream) = stream else { return };
                let (envelopes, sessions) = (envelope_sender.clone(), session_sender.clone());
                thread::spawn(move || serve_control(stream, connection, &envelopes, &sessions));
            }
        });
        Self {
            address,
            envelopes,
            sessions,
        }
    }

    fn endpoint(&self, token: &str) -> ControlEndpoint {
        ControlEndpoint {
            url: format!("ws://{}/control?token={token}", self.address),
            agent_key: AGENT.to_owned(),
        }
    }

    fn next(&self) -> Value {
        self.envelopes.recv_timeout(WAIT).expect("an envelope of the proxy")
    }

    fn session(&self) -> Session {
        self.sessions.recv_timeout(WAIT).expect("an accepted proxy")
    }
}

/// Reads an HTTP head byte by byte, so no byte after it leaves the stream.
fn read_head(mut stream: &TcpStream) -> String {
    let mut head = Vec::new();
    while !head.ends_with(b"\r\n\r\n") {
        let mut byte = [0];
        if stream.read(&mut byte).unwrap_or_default() == 0 {
            break;
        }
        head.push(byte[0]);
    }
    String::from_utf8(head).unwrap()
}

fn serve_control(stream: TcpStream, connection: u64, envelopes: &mpsc::Sender<Value>, sessions: &mpsc::Sender<Session>) {
    let head = read_head(&stream);
    if head.lines().next() != Some(&format!("GET /control?token={CONTROL_TOKEN} HTTP/1.1")) {
        let _ = (&stream).write_all(b"HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        return;
    }
    let key = head
        .lines()
        .find_map(|line| line.strip_prefix("Sec-WebSocket-Key: "))
        .unwrap()
        .to_owned();
    let accept = format!(
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
        websocket::accept_key(&key)
    );
    (&stream).write_all(accept.as_bytes()).unwrap();
    let (sender, mut receiver) = websocket::accept(stream).unwrap();
    let sender = Arc::new(sender);
    let Ok(Message::Text(hello)) = receiver.next(&sender) else { return };
    let hello: Value = serde_json::from_str(&hello).unwrap();
    let process_id = hello["processId"].as_str().unwrap().to_owned();
    let ack = json!({
        "type": "helloAck", "processId": process_id, "agentKey": hello["agentKey"], "protocolVersion": 2,
        "connectionId": format!("connection-{connection}"), "generation": connection,
    });
    sender.text(&ack.to_string()).unwrap();
    envelopes.send(hello).unwrap();
    sessions
        .send(Session {
            sender: Arc::clone(&sender),
            process_id,
            connection,
        })
        .unwrap();
    loop {
        match receiver.next(&sender) {
            Ok(Message::Text(text)) => envelopes.send(serde_json::from_str(&text).unwrap()).unwrap(),
            Ok(Message::Close(code)) => {
                sender.close(code.unwrap_or(websocket::NORMAL_CLOSURE));
                let _ = envelopes.send(json!({ "type": "<close>", "code": code }));
                return;
            }
            Err(_) => {
                let _ = envelopes.send(json!({ "type": "<end>" }));
                return;
            }
        }
    }
}

fn start_cloud() -> Service<Cloud> {
    cloud::start(0, TOKEN, Organization::CloudWorkspace).unwrap()
}

/// A dial of the IDE with the `Authorization` header `authorization`, as the codec client writes it.
fn dial(service: &Service<Cloud>, authorization: Option<&str>) -> io::Result<(Sender, Receiver)> {
    let headers: Vec<(&str, &str)> = authorization.map(|value| ("Authorization", value)).into_iter().collect();
    let dialed = websocket::connect(&format!("ws://{}/jcp{ACP_PATH}", service.address()), &headers, WAIT)?;
    dialed.1.set_read_timeout(Some(WAIT))?;
    Ok(dialed)
}

/// A dial with the bearer token, and the `hello` that it gives the control server.
fn dial_through(service: &Service<Cloud>, control: &FakeControl) -> (Sender, Receiver, Session) {
    let (sender, receiver) = dial(service, Some(BEARER)).unwrap();
    let hello = control.next();
    assert_eq!(hello["type"], "hello");
    (sender, receiver, control.session())
}

/// A started emulator whose spawner reaches `control`.
fn attached(control: &FakeControl) -> Service<Cloud> {
    let service = start_cloud();
    service.handler().spawner().attach(Some(control.endpoint(CONTROL_TOKEN)));
    service
}

/// `fields` with the identity of `session`.
fn envelope(session: &Session, kind: &str, fields: &Value) -> Value {
    let mut envelope = session.identity();
    envelope["type"] = json!(kind);
    envelope.as_object_mut().unwrap().extend(fields.as_object().unwrap().clone());
    envelope
}

fn outcomes(service: &Service<Cloud>) -> Vec<String> {
    service
        .handler()
        .spawner()
        .record()
        .dials
        .into_iter()
        .map(|dial| dial.outcome)
        .collect()
}

#[test]
fn a_dial_reaches_the_control_server_and_a_write_reaches_the_dial() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver) = dial(&service, Some(BEARER)).unwrap();
    let hello = control.next();
    let process_id = hello["processId"].as_str().unwrap().to_owned();
    assert!(process_id.starts_with("spawner-0-"), "{hello}");
    assert_eq!(
        hello,
        json!({ "type": "hello", "processId": process_id, "agentKey": AGENT, "protocolVersion": 2, "sequenceOrigin": 0 })
    );
    let session = control.session();
    let initialize = r#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"#;
    sender.text(initialize).unwrap();
    assert_eq!(
        control.next(),
        envelope(
            &session,
            "inbound",
            &json!({ "frameId": "frame-1", "sequence": 1, "rawLine": initialize })
        )
    );
    sender.text("second").unwrap();
    assert_eq!(control.next()["sequence"], 2);
    session.command("write", &json!({ "commandId": "c1", "rawLine": "the answer" }));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Text("the answer".to_owned()));
    assert_eq!(control.next(), envelope(&session, "writeAck", &json!({ "commandId": "c1" })));
    assert_eq!(
        serde_json::to_value(service.handler().spawner().record()).unwrap(),
        json!({
            "agentKey": AGENT,
            "dials": [{ "index": 0, "authorization": BEARER, "outcome": "accepted" }],
            "deleted": [],
            "failNext": [],
        })
    );
}

#[test]
fn a_dial_without_a_bearer_token_gets_401_before_the_upgrade() {
    let service = start_cloud();
    for authorization in [None, Some("Basic eA==")] {
        let Err(error) = dial(&service, authorization) else {
            panic!("a dial without a bearer token upgrades");
        };
        assert!(error.to_string().ends_with("answered \"HTTP/1.1 401 Unauthorized\""), "{error}");
    }
    let record = service.handler().spawner().record();
    assert_eq!(
        record.dials,
        [
            Dial {
                index: 0,
                authorization: None,
                outcome: "rejected 401".to_owned()
            },
            Dial {
                index: 1,
                authorization: Some("Basic eA==".to_owned()),
                outcome: "rejected 401".to_owned()
            },
        ]
    );
}

#[test]
fn a_forced_status_answers_the_next_dials_and_wins_over_a_missing_token() {
    let service = start_cloud();
    let address = service.address();
    let armed = testing::control(address, "POST", "/_lane/cloud/spawner/fail", r#"{"count":2,"status":403}"#);
    assert_eq!((armed.status, armed.json()["failNext"].clone()), (200, json!([403, 403])));
    let refused = |authorization| dial(&service, authorization).err().expect("a refused dial").to_string();
    assert!(refused(None).ends_with("answered \"HTTP/1.1 403 Forbidden\""));
    assert!(refused(Some(BEARER)).ends_with("answered \"HTTP/1.1 403 Forbidden\""));
    let (_sender, _receiver) = dial(&service, Some(BEARER)).unwrap();
    assert_eq!(outcomes(&service), ["rejected 403", "rejected 403", "accepted"]);
    let armed = testing::control(address, "POST", "/_lane/cloud/spawner/fail", r#"{"count":1}"#);
    assert_eq!(armed.json()["failNext"], json!([401]));
    for body in [
        r#"{"count":0}"#,
        r#"{"count":101}"#,
        r#"{"count":1,"status":302}"#,
        r#"{"count":1,"x":1}"#,
    ] {
        let answer = testing::control(address, "POST", "/_lane/cloud/spawner/fail", body);
        assert_eq!(
            (answer.status, answer.json()["error"]["code"].clone()),
            (400, json!("bad_request")),
            "{body}"
        );
    }
    assert_eq!(testing::control(address, "GET", "/_lane/cloud/spawner/fail", "").status, 405);
    assert_eq!(testing::control(address, "PUT", "/_lane/cloud/spawner", "").status, 405);
}

#[test]
fn a_dial_before_the_attach_closes_with_an_internal_error() {
    let service = start_cloud();
    let (sender, mut receiver) = dial(&service, Some(BEARER)).unwrap();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::INTERNAL_ERROR)));
    assert_eq!(outcomes(&service), ["accepted"]);
    assert_eq!(service.handler().spawner().record().agent_key, None);
}

#[test]
fn a_dial_that_the_control_server_refuses_closes_with_an_internal_error() {
    let control = FakeControl::start();
    let service = start_cloud();
    service.handler().spawner().attach(Some(control.endpoint("another-token")));
    let (sender, mut receiver) = dial(&service, Some(BEARER)).unwrap();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::INTERNAL_ERROR)));
    assert!(
        matches!(control.sessions.try_recv(), Err(mpsc::TryRecvError::Empty)),
        "the control server accepted no proxy"
    );
}

#[test]
fn the_end_of_the_dial_reports_stdin_eof_and_closes_the_control_connection() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    assert!(sender.close(websocket::NORMAL_CLOSURE));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::NORMAL_CLOSURE)));
    assert_eq!(
        control.next(),
        envelope(
            &session,
            "lifecycle",
            &json!({ "kind": "stdin_eof", "details": "the WebSocket closed" })
        )
    );
    assert_eq!(control.next(), json!({ "type": "<close>", "code": 1000 }));
}

#[test]
fn an_exit_reports_process_exit_acknowledges_and_closes_the_control_connection_and_the_dial() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    session.command("exit", &json!({ "commandId": "c2", "code": 3 }));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::GOING_AWAY)));
    assert_eq!(
        control.next(),
        envelope(
            &session,
            "lifecycle",
            &json!({ "kind": "process_exit", "details": "exit command", "exitCode": 3 })
        )
    );
    assert_eq!(control.next(), envelope(&session, "writeAck", &json!({ "commandId": "c2" })));
    sender.close(websocket::GOING_AWAY);
    assert_eq!(control.next(), json!({ "type": "<close>", "code": 1000 }));
}

#[test]
fn an_exit_ends_the_control_connection_and_the_dial_although_the_ide_never_answers_the_close() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    session.command("exit", &json!({ "commandId": "c4", "code": 0 }));
    assert_eq!(control.next()["kind"], "process_exit");
    assert_eq!(control.next(), envelope(&session, "writeAck", &json!({ "commandId": "c4" })));
    assert_eq!(control.next(), json!({ "type": "<close>", "code": 1000 }));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::GOING_AWAY)));
    let deadline = Instant::now() + WAIT;
    while !lock(&service.handler().spawner().state).open.is_empty() {
        assert!(Instant::now() < deadline, "the connection of the unanswered dial did not end");
        thread::sleep(Duration::from_millis(20));
    }
}

#[test]
fn close_stdout_reports_stdout_closed_acknowledges_and_closes_the_dial_normally() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    session.command("closeStdout", &json!({ "commandId": "c3" }));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::NORMAL_CLOSURE)));
    assert_eq!(
        control.next(),
        envelope(
            &session,
            "lifecycle",
            &json!({ "kind": "stdout_closed", "details": "the WebSocket closed by control" })
        )
    );
    assert_eq!(control.next(), envelope(&session, "writeAck", &json!({ "commandId": "c3" })));
}

#[test]
fn an_mcp_command_gets_an_error_result_and_a_malformed_write_sends_its_bytes() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    session.command(
        "mcpCall",
        &json!({ "commandId": "m1", "server": "files", "tool": "read", "arguments": {} }),
    );
    session.command("mcpListTools", &json!({ "commandId": "m2", "server": "files" }));
    for id in ["m1", "m2"] {
        assert_eq!(
            control.next(),
            envelope(
                &session,
                "mcpResult",
                &json!({ "commandId": id, "isError": true, "text": "a remote spawner offers no MCP server" })
            )
        );
    }
    let bytes = Base64::Standard.encode(&[b'{', 0xff]);
    session.command("writeMalformed", &json!({ "commandId": "w1", "bytesBase64": bytes }));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Text("{\u{fffd}".to_owned()));
    assert_eq!(control.next(), envelope(&session, "writeAck", &json!({ "commandId": "w1" })));
}

#[test]
fn a_command_of_another_identity_ends_the_control_connection_and_closes_the_dial() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    let mut stranger = json!({ "type": "write", "commandId": "c4", "rawLine": "x" });
    stranger
        .as_object_mut()
        .unwrap()
        .extend(session.identity().as_object().unwrap().clone());
    stranger["generation"] = json!(7);
    session.sender.text(&stranger.to_string()).unwrap();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::INTERNAL_ERROR)));
    assert_eq!(control.next(), json!({ "type": "<close>", "code": 1000 }));
}

#[test]
fn a_lost_control_connection_closes_the_dial_with_an_internal_error() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, session) = dial_through(&service, &control);
    session.sender.shutdown();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(websocket::INTERNAL_ERROR)));
}

#[test]
fn a_message_of_70000_bytes_reaches_the_control_server() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, _receiver, _session) = dial_through(&service, &control);
    let line = format!(r#"{{"text":"{}"}}"#, "x".repeat(70_000));
    sender.text(&line).unwrap();
    assert_eq!(control.next()["rawLine"], line.as_str());
}

/// A dial with a fixed key, as raw bytes, and the answer head that it read.
fn raw_dial(address: SocketAddr) -> (TcpStream, String) {
    let mut stream = TcpStream::connect(address).unwrap();
    stream.set_read_timeout(Some(WAIT)).unwrap();
    stream
        .write_all(
            format!(
                "GET /jcp{ACP_PATH}/ HTTP/1.1\r\nHost: {address}\r\nUpgrade: WebSocket\r\nConnection: Upgrade\r\n\
                 Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nAuthorization: {BEARER}\r\n\r\n"
            )
            .as_bytes(),
        )
        .unwrap();
    let head = read_head(&stream);
    (stream, head)
}

#[test]
fn the_upgrade_answers_the_accept_key_and_a_ping_gets_a_pong_with_its_payload() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (mut stream, head) = raw_dial(service.address());
    assert_eq!(
        head,
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\
         Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n"
    );
    stream
        .write_all(&encode_frame(opcode::PING, &[7, 8, 9], Some([1, 2, 3, 4])))
        .unwrap();
    let pong = read_frame(&mut stream, Role::Client).unwrap();
    assert_eq!((pong.opcode, pong.payload), (opcode::PONG, vec![7, 8, 9]));
    stream.write_all(&encode_frame(opcode::TEXT, b"plain", None)).unwrap();
    let close = read_frame(&mut stream, Role::Client).unwrap();
    assert_eq!(
        (close.opcode, close.payload),
        (opcode::CLOSE, websocket::PROTOCOL_ERROR.to_be_bytes().to_vec())
    );
    assert_eq!(control.next()["type"], "hello");
    assert_eq!(control.next()["kind"], "stdin_eof");
}

#[test]
fn the_session_delete_records_the_decoded_id_and_other_spawner_requests_are_unexpected() {
    let service = start_cloud();
    let address = service.address();
    let deleted = testing::call(address, "DELETE", "/jcp/agent-spawner/sessions/abc%20123", &[], b"");
    assert_eq!((deleted.status, deleted.raw_body.len()), (204, 0));
    for (method, target) in [
        ("GET", "/jcp/agent-spawner/sessions/abc"),
        ("DELETE", "/jcp/agent-spawner/sessions/a/b"),
        ("GET", "/jcp/agent-spawner/acp"),
    ] {
        assert_eq!(testing::call(address, method, target, &[], b"").status, 501, "{method} {target}");
    }
    assert_eq!(service.handler().spawner().record().deleted, ["abc 123"]);
    assert_eq!(
        testing::control(address, "GET", "/_lane/cloud/record", "").json(),
        json!({
            "discoveries": 0,
            "served": [],
            "unexpected": ["GET /jcp/agent-spawner/sessions/abc", "DELETE /jcp/agent-spawner/sessions/a/b", "GET /jcp/agent-spawner/acp"],
        })
    );
}

#[test]
fn the_record_clear_keeps_the_spawner_and_the_spawner_clear_and_the_reset_forget_it() {
    let service = start_cloud();
    let address = service.address();
    let spawner = service.handler().spawner();
    spawner.attach(Some(ControlEndpoint {
        url: "ws://127.0.0.1:1/control?token=t".to_owned(),
        agent_key: AGENT.to_owned(),
    }));
    let fill = || {
        dial(&service, None).err().expect("a dial without a token is refused");
        testing::call(address, "DELETE", "/jcp/agent-spawner/sessions/s1", &[], b"");
        spawner.fail_next_dials(1, 503);
    };
    fill();
    testing::control(address, "DELETE", "/_lane/cloud/record", "");
    let held = json!({
        "agentKey": AGENT,
        "dials": [{ "index": 0, "authorization": null, "outcome": "rejected 401" }],
        "deleted": ["s1"],
        "failNext": [503],
    });
    assert_eq!(testing::control(address, "GET", "/_lane/cloud/spawner", "").json(), held);
    assert_eq!(testing::control(address, "DELETE", "/_lane/cloud/spawner", "").json(), held);
    let empty = json!({ "agentKey": AGENT, "dials": [], "deleted": [], "failNext": [] });
    assert_eq!(testing::control(address, "GET", "/_lane/cloud/spawner", "").json(), empty);
    fill();
    service.handler().clear();
    assert_eq!(serde_json::to_value(spawner.record()).unwrap(), empty);
    dial(&service, None).err().expect("a dial without a token is refused");
    assert_eq!(spawner.record().dials[0].index, 2, "a clear keeps the count of the dials");
}

#[test]
fn a_stopped_emulator_ends_an_open_dial_and_its_control_connection() {
    let control = FakeControl::start();
    let service = attached(&control);
    let (sender, mut receiver, _session) = dial_through(&service, &control);
    drop(service);
    let error = receiver.next(&sender).expect_err("a stopped emulator ends the dial");
    assert!(
        matches!(error.kind(), ErrorKind::UnexpectedEof | ErrorKind::ConnectionReset),
        "{error}"
    );
    let ended = control.next();
    assert!(ended["type"] == "<end>" || ended["kind"] == "stdin_eof", "{ended}");
}
