//! The process-level tests of `acp-fake-agent`: each test starts the binary as the IDE does, with a control server of
//! the test process in place of `AcpFakeAgentControlServer`, and with MCP servers of its own.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_acp-fake-agent`. Cargo sets it when it compiles the test,
//! and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, mpsc};
use std::thread;
use std::time::{Duration, Instant};

use avl_control::json::raw_member;
use avl_control::proxy;
use avl_control::websocket::{self, Message, Sender};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

/// The token of the one credential of the control server.
const TOKEN: &str = "secret";
/// The agent key of the tests.
const AGENT: &str = "fake";
/// Bounds every wait of a test.
const WAIT: Duration = Duration::from_secs(20);
/// The variable of the `stdio` entry that turns the test binary into the stdio MCP server of [stdio_stub_server].
const STDIO_STUB_ENV: &str = "ACP_FAKE_AGENT_TEST_STDIO_STUB";
const STDIO_STUB_VALUE: &str = "from-entry";

fn agent_binary() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_acp-fake-agent")
        .or_else(|| option_env!("CARGO_BIN_EXE_acp-fake-agent").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_acp-fake-agent");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Serializes the starts of the agent. On macOS the standard library marks a new pipe close-on-exec only after it made
/// the pipe, so a process that another test thread starts at that moment can inherit an end of the pipe. A pipe end
/// in a sibling keeps a closed output of a test open.
static SPAWN: Mutex<()> = Mutex::new(());

/// Starts the agent with `args` and the control variables of `control`, or none, plus `env`.
fn start(args: &[&str], control: Option<&Control>, env: &[(&str, &str)]) -> Child {
    let mut command = Command::new(agent_binary());
    command
        .args(args)
        .env_remove("ACP_FAKE_AGENT_CONTROL_URL")
        .env_remove("ACP_FAKE_AGENT_CONTROL_KEY")
        .env_remove("ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX")
        .envs(env.iter().copied())
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    if let Some(control) = control {
        command
            .env("ACP_FAKE_AGENT_CONTROL_URL", control.url())
            .env("ACP_FAKE_AGENT_CONTROL_KEY", AGENT);
    }
    let _spawn = SPAWN.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    command.spawn().unwrap()
}

/// Waits for the exit of `child`, and answers its code, its standard output and its standard error.
fn finish(child: Child) -> (Option<i32>, String, String) {
    let (exit, stdout, stderr) = wait_output(child);
    (exit, String::from_utf8_lossy(&stdout).into_owned(), stderr)
}

fn wait_output(mut child: Child) -> (Option<i32>, Vec<u8>, String) {
    let deadline = Instant::now() + WAIT;
    while child.try_wait().unwrap().is_none() {
        assert!(Instant::now() < deadline, "the agent did not exit within {WAIT:?}");
        thread::sleep(Duration::from_millis(20));
    }
    let output = child.wait_with_output().unwrap();
    (
        output.status.code(),
        output.stdout,
        String::from_utf8_lossy(&output.stderr).into_owned(),
    )
}

/// The control server in the IDE as the tests need it: the token check of the upgrade, the `helloAck`, and every
/// envelope of each proxy in arrival order. The end of a connection is the envelope `<end>`.
struct Control {
    address: SocketAddr,
    sessions: mpsc::Receiver<Session>,
}

/// One proxy after its `hello`: the commands that a test sends, and the envelopes that the proxy sent.
struct Session {
    hello: Value,
    sender: Arc<Sender>,
    identity: String,
    envelopes: mpsc::Receiver<Value>,
    commands: AtomicU64,
}

impl Control {
    fn start() -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let (sessions_sender, sessions) = mpsc::channel();
        thread::spawn(move || {
            for (connection, stream) in (1_u64..).zip(listener.incoming()) {
                let Ok(stream) = stream else { return };
                let sessions = sessions_sender.clone();
                thread::spawn(move || serve_control(stream, connection, &sessions));
            }
        });
        Self { address, sessions }
    }

    fn url(&self) -> String {
        format!("ws://{}/control?token={TOKEN}", self.address)
    }

    fn session(&self) -> Session {
        self.sessions.recv_timeout(WAIT).expect("a proxy that said hello")
    }
}

impl Session {
    /// Sends the command `kind` with the members of the JSON object text `fields`, and answers its id.
    fn command(&self, kind: &str, fields: &str) -> String {
        let id = format!("command-{}", self.commands.fetch_add(1, Ordering::SeqCst) + 1);
        let fields = fields.trim().strip_prefix('{').unwrap().strip_suffix('}').unwrap();
        let separator = if fields.is_empty() { "" } else { "," };
        let envelope = format!(
            "{{\"type\":{},{},\"commandId\":\"{id}\"{separator}{fields}}}",
            json!(kind),
            self.identity
        );
        self.sender.text(&envelope).unwrap();
        id
    }

    /// The next envelope of the proxy, without its identity.
    fn next(&self) -> Value {
        let mut envelope = self.envelopes.recv_timeout(WAIT).expect("an envelope of the proxy");
        if let Some(members) = envelope.as_object_mut() {
            for identity in ["connectionId", "generation", "processId", "agentKey"] {
                members.remove(identity);
            }
        }
        envelope
    }

    /// The next envelope that is not an `inbound` frame.
    fn next_not_inbound(&self) -> Value {
        loop {
            let envelope = self.next();
            if envelope["type"] != "inbound" {
                return envelope;
            }
        }
    }

    /// Sends the MCP command `kind` with `fields`, and answers its `mcpResult`.
    fn mcp(&self, kind: &str, fields: &str) -> (bool, String) {
        let id = self.command(kind, fields);
        let result = self.next_not_inbound();
        assert_eq!(
            (&result["type"], &result["commandId"]),
            (&json!("mcpResult"), &json!(id)),
            "{result}"
        );
        (result["isError"].as_bool().unwrap(), result["text"].as_str().unwrap().to_owned())
    }

    /// Sends a `closeStdout` command, and checks its `stdout_closed` and its `writeAck`.
    fn close_stdout(&self) {
        let id = self.command("closeStdout", "{}");
        assert_eq!(
            self.next(),
            json!({ "type": "lifecycle", "kind": "stdout_closed", "details": "stdout closed by control" })
        );
        assert_eq!(self.next(), json!({ "type": "writeAck", "commandId": id }));
    }

    /// Checks that the proxy reports one `control_failure` and then ends its connection.
    fn expect_control_failure(&self) {
        assert_eq!(
            self.next(),
            json!({ "type": "lifecycle", "kind": "control_failure", "details": "IOException" })
        );
        assert_eq!(self.next(), json!({ "type": "<end>" }));
    }

    /// Sends the exit command `code`, and checks its `writeAck` and its `process_exit`.
    fn exit(&self, code: i64) {
        let id = self.command("exit", &format!("{{\"code\":{code}}}"));
        assert_eq!(self.next_not_inbound(), json!({ "type": "writeAck", "commandId": id }));
        assert_eq!(
            self.next(),
            json!({ "type": "lifecycle", "kind": "process_exit", "details": "exit command", "exitCode": code })
        );
    }
}

fn serve_control(stream: TcpStream, connection: u64, sessions: &mpsc::Sender<Session>) {
    let Ok(Some((sender, mut receiver, hello))) = proxy::accept_hello(stream, &format!("/control?token={TOKEN}")) else {
        return;
    };
    let sender = Arc::new(sender);
    let identity = format!(
        "\"connectionId\":\"connection-{connection}\",\"generation\":{connection},\"processId\":{},\"agentKey\":{}",
        hello["processId"], hello["agentKey"]
    );
    sender
        .text(&format!("{{\"type\":\"helloAck\",{identity},\"protocolVersion\":2}}"))
        .unwrap();
    let (envelope_sender, envelopes) = mpsc::channel();
    let session = Session {
        hello,
        sender: Arc::clone(&sender),
        identity,
        envelopes,
        commands: AtomicU64::new(0),
    };
    if sessions.send(session).is_err() {
        return;
    }
    loop {
        match receiver.next(&sender) {
            Ok(Message::Text(text)) => {
                if envelope_sender.send(serde_json::from_str(&text).unwrap()).is_err() {
                    return;
                }
            }
            Ok(Message::Close(code)) => {
                sender.close(code.unwrap_or(websocket::NORMAL_CLOSURE));
                let _ = envelope_sender.send(json!({ "type": "<end>" }));
                return;
            }
            Err(_) => {
                let _ = envelope_sender.send(json!({ "type": "<end>" }));
                return;
            }
        }
    }
}

#[test]
fn a_missing_control_setting_exits_with_2_and_names_the_setting() {
    let (exit, stdout, stderr) = finish(start(&[], None, &[]));
    assert_eq!(
        (exit, stdout.as_str(), stderr.as_str()),
        (Some(2), "", "ACP fake-agent control URL is not configured\n")
    );
    let (exit, _, stderr) = finish(start(
        &["--air-terminal-login"],
        None,
        &[("ACP_FAKE_AGENT_CONTROL_URL", "ws://h/c")],
    ));
    assert_eq!(
        (exit, stderr.as_str()),
        (Some(2), "ACP fake-agent terminal-login control key is not configured\n")
    );
    let (exit, _, stderr) = finish(start(&["--air-terminal-tui"], None, &[]));
    assert_eq!(
        (exit, stderr.as_str()),
        (Some(2), "ACP fake-agent terminal-tui control URL is not configured\n")
    );
}

/// A control server that does not listen fails the dial, and the line hides the token of the URL.
#[test]
fn a_control_server_that_does_not_listen_exits_with_1() {
    let unused = TcpListener::bind("127.0.0.1:0").unwrap().local_addr().unwrap();
    let url = format!("ws://{unused}/control?token={TOKEN}");
    let child = start(
        &[],
        None,
        &[("ACP_FAKE_AGENT_CONTROL_URL", &url), ("ACP_FAKE_AGENT_CONTROL_KEY", AGENT)],
    );
    let (exit, _, stderr) = finish(child);
    assert_eq!(exit, Some(1));
    assert!(stderr.starts_with("ACP fake-agent proxy failed: IOException: "), "{stderr}");
    assert!(
        stderr.ends_with(&format!(";control=ws://{unused}/control?token=REDACTED\n")),
        "{stderr}"
    );
}

/// The deterministic agent answers without a control server, and it reads each of its switches from its environment.
#[test]
fn the_standalone_agent_answers_deterministically_without_a_control_server() {
    let mut child = start(
        &[],
        None,
        &[
            ("ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX", "standalone response"),
            ("ACP_DEBUG_TEST_SESSION_NONCE", "standalone"),
            ("ACP_DEBUG_TEST_LOAD_SESSION", "true"),
            ("ACP_DEBUG_TEST_EVENT_PREFIX", "ignored"),
        ],
    );
    child
        .stdin
        .take()
        .unwrap()
        .write_all(
            b"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\r\n\
              {\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{}}\n\
              {\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"session/prompt\",\"params\":{\"sessionId\":\"acp-integration-session-standalone-1\"}}\n",
        )
        .unwrap();
    let (exit, stdout, stderr) = finish(child);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    for expected in [
        "\"id\":1,\"result\":{\"protocolVersion\":1,\"agentCapabilities\":{\"loadSession\":true}",
        "\"id\":2,\"result\":{\"sessionId\":\"acp-integration-session-standalone-1\"",
        "\"text\":\"standalone response 1\"",
        "\"id\":3,\"result\":{\"stopReason\":\"end_turn\"}",
    ] {
        assert!(stdout.contains(expected), "{expected} in {stdout}");
    }
}

#[test]
fn the_standalone_agent_reports_a_refused_setting_with_exit_1() {
    let child = start(
        &[],
        None,
        &[
            ("ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX", "p"),
            ("ACP_DEBUG_TEST_RESPONSE_DELAY_MS", "soon"),
        ],
    );
    assert_eq!(
        finish(child),
        (
            Some(1),
            String::new(),
            "ACP deterministic agent failed: ACP_DEBUG_TEST_RESPONSE_DELAY_MS must be a number of milliseconds, got 'soon'\n".to_owned()
        )
    );
}

/// The `hello` binds the process and its agent key, and an exit command right after it ends the process with its code.
#[test]
fn an_exit_command_after_the_hello_ends_the_proxy_with_its_code() {
    let control = Control::start();
    let child = start(&[], Some(&control), &[]);
    let session = control.session();
    assert_eq!(session.hello["type"], "hello");
    assert_eq!(
        (&session.hello["agentKey"], &session.hello["protocolVersion"]),
        (&json!(AGENT), &json!(2))
    );
    assert_eq!(session.hello["sequenceOrigin"], 0);
    let process_id = session.hello["processId"].as_str().unwrap().to_owned();
    assert!(process_id.starts_with(&format!("proc-{}-", child.id())), "{process_id}");
    session.exit(7);
    let (exit, stdout, stderr) = finish(child);
    assert_eq!((exit, stdout.as_str()), (Some(7), ""));
    assert!(
        stderr.contains(&format!("acp-fake-agent-proxy-start;agentKey={AGENT};pid=")),
        "{stderr}"
    );
    assert!(stderr.contains(&format!(";processId={process_id};control=ws://")), "{stderr}");
    assert!(stderr.contains("acp-fake-agent-proxy-exit;code=7"), "{stderr}");
    assert!(!stderr.contains("token=secret") && stderr.contains("token=REDACTED"), "{stderr}");
}

/// Each line of the IDE goes out as the next `inbound` frame, a line end with a carriage return included. The end of
/// the input reports `stdin_eof`, and the process runs on until its exit command.
#[test]
fn the_proxy_forwards_each_line_and_writes_each_command() {
    let control = Control::start();
    let mut child = start(&[], Some(&control), &[]);
    let session = control.session();
    let mut stdin = child.stdin.take().unwrap();
    stdin.write_all(b"{\"arbitrary\":\"test-process-owned\"}\r\nsecond\n").unwrap();
    let inbound =
        |frame: u64, line: &str| json!({ "type": "inbound", "frameId": format!("frame-{frame}"), "sequence": frame, "rawLine": line });
    assert_eq!(session.next(), inbound(1, "{\"arbitrary\":\"test-process-owned\"}"));
    assert_eq!(session.next(), inbound(2, "second"));
    drop(stdin);
    assert_eq!(
        session.next(),
        json!({ "type": "lifecycle", "kind": "stdin_eof", "details": "stdin closed" })
    );
    let write = session.command(
        "write",
        r#"{"rawLine":"{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"dynamic\":true}}"}"#,
    );
    assert_eq!(session.next(), json!({ "type": "writeAck", "commandId": write }));
    let malformed = session.command("writeMalformed", r#"{"bytesBase64":"e25vdC1qc29u"}"#);
    assert_eq!(session.next(), json!({ "type": "writeAck", "commandId": malformed }));
    session.exit(0);
    let (exit, stdout, _) = finish(child);
    assert_eq!(
        (exit, stdout.as_str()),
        (Some(0), "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"dynamic\":true}}\n{not-json")
    );
}

/// A `closeStdout` ends the pipe of the output while the process runs. A later write fails the proxy: it reports one
/// `control_failure` and exits with 1.
#[test]
fn a_closed_stdout_ends_the_pipe_and_a_later_write_fails_the_proxy() {
    let control = Control::start();
    let mut child = start(&[], Some(&control), &[]);
    let session = control.session();
    let mut stdout = child.stdout.take().unwrap();
    let ended = thread::spawn(move || {
        let mut bytes = Vec::new();
        stdout.read_to_end(&mut bytes).unwrap();
        bytes
    });
    session.close_stdout();
    assert_eq!(ended.join().unwrap(), b"");
    assert!(
        child.try_wait().unwrap().is_none(),
        "the process runs on after the close of its output"
    );
    let again = session.command("closeStdout", "{}");
    assert_eq!(
        session.next(),
        json!({ "type": "writeAck", "commandId": again }),
        "a second close only acknowledges"
    );
    session.command("write", r#"{"rawLine":"late"}"#);
    session.expect_control_failure();
    let (exit, _, stderr) = finish(child);
    assert_eq!(exit, Some(1));
    assert!(stderr.contains("Cannot write after stdout closed"), "{stderr}");
}

/// A write to an output that nobody reads fails, and the failure report names no detail of the output.
#[test]
fn a_failed_write_reports_one_sanitized_control_failure() {
    let control = Control::start();
    let mut child = start(&[], Some(&control), &[]);
    let session = control.session();
    drop(child.stdout.take());
    session.command("write", r#"{"rawLine":"response"}"#);
    session.expect_control_failure();
    assert_eq!(wait_output(child).0, Some(1));
}

/// A control connection that ends before an exit command ends the process with 1.
#[test]
fn a_lost_control_connection_ends_the_proxy_with_1() {
    let control = Control::start();
    let child = start(&[], Some(&control), &[]);
    let session = control.session();
    session.sender.close(websocket::NORMAL_CLOSURE);
    let (exit, _, stderr) = finish(child);
    assert_eq!(exit, Some(1));
    assert!(
        stderr.contains("ACP fake-agent proxy failed: IOException: Fake-agent control channel failed"),
        "{stderr}"
    );
}

/// The streamable-HTTP MCP server of the tests, with two tools: `echo` answers `<tool>:<arguments>`, and `fail` answers
/// the same text with `isError`. It requires the bearer token of its entry, answers `initialize` with a session id,
/// answers a tool call on a chunked stream of events behind one unrelated notification, and records every method.
struct StubMcp {
    address: SocketAddr,
    methods: Arc<Mutex<Vec<String>>>,
    sessions: Arc<Mutex<Vec<String>>>,
    deleted: Arc<Mutex<u32>>,
}

const STUB_TOKEN: &str = "stub-token";

impl StubMcp {
    fn start() -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let stub = Self {
            address: listener.local_addr().unwrap(),
            methods: Arc::default(),
            sessions: Arc::default(),
            deleted: Arc::default(),
        };
        let (methods, sessions, deleted) = (Arc::clone(&stub.methods), Arc::clone(&stub.sessions), Arc::clone(&stub.deleted));
        thread::spawn(move || {
            for stream in listener.incoming() {
                let Ok(stream) = stream else { return };
                let _ = serve_mcp(&stream, &methods, &sessions, &deleted);
            }
        });
        stub
    }

    fn url(&self) -> String {
        format!("http://{}/mcp", self.address)
    }

    /// The `session/new` entry of this server, as the ACP model writes an `http` server.
    fn server_json(&self, name: &str) -> String {
        format!(
            r#"{{"type":"http","name":"{name}","url":"{}","headers":[{{"name":"Authorization","value":"Bearer {STUB_TOKEN}"}}]}}"#,
            self.url()
        )
    }

    /// The MCP config file that names this server, as Air writes it for a terminal CLI.
    fn config_json(&self, name: &str) -> String {
        format!(
            r#"{{"mcpServers":{{"{name}":{{"type":"http","url":"{}","headers":{{"Authorization":"Bearer {STUB_TOKEN}"}}}}}}}}"#,
            self.url()
        )
    }

    fn methods(&self) -> Vec<String> {
        self.methods.lock().unwrap().clone()
    }
}

fn serve_mcp(stream: &TcpStream, methods: &Mutex<Vec<String>>, sessions: &Mutex<Vec<String>>, deleted: &Mutex<u32>) -> std::io::Result<()> {
    let head = websocket::read_head(stream)?;
    let header = |name: &str| {
        head.lines().find_map(|line| {
            line.split_once(':')
                .filter(|(key, _)| key.eq_ignore_ascii_case(name))
                .map(|(_, value)| value.trim().to_owned())
        })
    };
    let mut body = vec![0; header("Content-Length").and_then(|length| length.parse().ok()).unwrap_or(0)];
    (&*stream).read_exact(&mut body)?;
    let mut writer = stream;
    let mut answer = |status: &str, headers: &str, body: &str| {
        writer.write_all(
            format!(
                "HTTP/1.1 {status}\r\n{headers}Content-Length: {}\r\nConnection: close\r\n\r\n{body}",
                body.len()
            )
            .as_bytes(),
        )
    };
    if header("Authorization").as_deref() != Some(&format!("Bearer {STUB_TOKEN}")) {
        return answer("401 Unauthorized", "", "");
    }
    if head.starts_with("DELETE ") {
        *deleted.lock().unwrap() += 1;
        return answer("200 OK", "", "");
    }
    let request: Value = serde_json::from_slice(&body).unwrap_or_default();
    methods
        .lock()
        .unwrap()
        .push(request["method"].as_str().unwrap_or_default().to_owned());
    let Some(id) = request.get("id") else {
        return answer("202 Accepted", "", "");
    };
    let json = "Content-Type: application/json\r\n";
    match request["method"].as_str().unwrap_or_default() {
        "initialize" => {
            let session = format!("session-{}", sessions.lock().unwrap().len() + 1);
            sessions.lock().unwrap().push(session.clone());
            let result = r#"{"protocolVersion":"2025-11-25","capabilities":{"tools":{}},"serverInfo":{"name":"stub","version":"1"}}"#;
            answer(
                "200 OK",
                &format!("{json}Mcp-Session-Id: {session}\r\n"),
                &format!(r#"{{"jsonrpc":"2.0","id":{id},"result":{result}}}"#),
            )
        }
        _ if !header("Mcp-Session-Id").is_some_and(|session| sessions.lock().unwrap().contains(&session)) => {
            answer("404 Not Found", "", "")
        }
        "tools/list" => answer(
            "200 OK",
            json,
            &format!(
                r#"{{"jsonrpc":"2.0","id":{id},"result":{{"tools":[{{"name":"echo","inputSchema":{{"type":"object"}}}},{{"name":"fail","inputSchema":{{"type":"object"}}}}]}}}}"#
            ),
        ),
        "tools/call" => {
            let arguments = arguments_of(&String::from_utf8_lossy(&body));
            let tool = request["params"]["name"].as_str().unwrap_or_default();
            let text = json!(format!("{tool}:{arguments}"));
            let result = format!(
                r#"{{"jsonrpc":"2.0","id":{id},"result":{{"content":[{{"type":"text","text":{text}}}],"isError":{}}}}}"#,
                tool == "fail"
            );
            let events = format!(
                "event: message\ndata: {{\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\",\"params\":{{}}}}\n\nevent: message\ndata: {result}\n\n"
            );
            let (first, second) = events.split_at(events.len() / 2);
            writer.write_all(
                b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n",
            )?;
            for chunk in [first, second] {
                writer.write_all(format!("{:x}\r\n{chunk}\r\n", chunk.len()).as_bytes())?;
            }
            writer.write_all(b"0\r\n\r\n")
        }
        _ => answer(
            "200 OK",
            json,
            &format!(r#"{{"jsonrpc":"2.0","id":{id},"error":{{"code":-32601,"message":"Method not found"}}}}"#),
        ),
    }
}

/// The text of `params.arguments` of the JSON-RPC request `request`, as the request spells it.
fn arguments_of(request: &str) -> String {
    raw_member(&raw_member(request, "params").unwrap(), "arguments").unwrap()
}

/// Writes a `session/new` that offers the `servers` entries, and checks that the proxy passes it on unchanged.
fn offer(stdin: &mut impl Write, session: &Session, servers: &str) {
    let line = format!(r#"{{"jsonrpc":"2.0","id":2,"method":"session/new","params":{{"cwd":"/tmp","mcpServers":[{servers}]}}}}"#);
    stdin.write_all(format!("{line}\n").as_bytes()).unwrap();
    stdin.flush().unwrap();
    assert_eq!(session.next()["rawLine"], line);
}

#[test]
fn an_mcp_call_reaches_the_http_server_of_the_current_session_and_answers_the_tool_text() {
    let stub = StubMcp::start();
    let control = Control::start();
    let mut child = start(&[], Some(&control), &[]);
    let session = control.session();
    let (is_error, text) = session.mcp("mcpCall", r#"{"server":"stub","tool":"echo","arguments":{}}"#);
    assert!(is_error && text.contains("No MCP server 'stub'"), "{text}");
    let missing = std::env::temp_dir().join("acp-missing-mcp-server").display().to_string();
    let mut stdin = child.stdin.take().unwrap();
    let missing_entry = format!(
        r#"{{"type":"stdio","name":"missing","command":{},"args":[],"env":[]}}"#,
        json!(missing)
    );
    offer(&mut stdin, &session, &format!("{},{missing_entry}", stub.server_json("stub")));
    assert_eq!(
        session.mcp("mcpListTools", r#"{"server":"stub"}"#),
        (false, r#"["echo","fail"]"#.to_owned())
    );
    assert_eq!(
        session.mcp(
            "mcpCall",
            r#"{"server":"stub","tool":"echo","arguments":{"title":"Issues","count":2}}"#
        ),
        (false, r#"echo:{"title":"Issues","count":2}"#.to_owned())
    );
    assert_eq!(
        session.mcp("mcpCall", r#"{"server":"stub","tool":"fail","arguments":{}}"#),
        (true, "fail:{}".to_owned())
    );
    let (is_error, text) = session.mcp("mcpCall", r#"{"server":"missing","tool":"echo","arguments":{}}"#);
    assert!(is_error && text.starts_with("IOException"), "{text}");
    assert!(session.mcp("mcpCall", r#"{"server":"other","tool":"echo","arguments":{}}"#).0);
    session.exit(0);
    let (exit, _, stderr) = finish(child);
    assert_eq!(exit, Some(0));
    assert!(
        stderr.contains("acp-fake-agent-proxy-mcp-list-tools;server=stub;isError=false"),
        "{stderr}"
    );
    assert!(
        stderr.contains("acp-fake-agent-proxy-mcp-call;server=stub;tool=echo;isError=false"),
        "{stderr}"
    );
    assert_eq!(
        stub.sessions.lock().unwrap().len(),
        1,
        "one MCP session serves every request of the server"
    );
    assert_eq!(
        stub.methods(),
        ["initialize", "notifications/initialized", "tools/list", "tools/call", "tools/call"]
    );
    assert_eq!(
        *stub.deleted.lock().unwrap(),
        0,
        "the exit command leaves the MCP session, so the process ends right after process_exit"
    );
}

/// A `stdio` entry starts its command with the arguments and the environment of the entry. One process serves every
/// request of the server. The exit command sends a termination signal to the process and does not wait for it, so the
/// process ends soon after the agent.
#[test]
fn an_mcp_call_starts_the_stdio_server_of_the_current_session_and_the_exit_stops_it() {
    let control = Control::start();
    let mut child = start(&[], Some(&control), &[]);
    let session = control.session();
    let test_binary = std::path::absolute(std::env::current_exe().unwrap()).unwrap().display().to_string();
    let entry = format!(
        r#"{{"name":"stdio-stub","command":{},"args":["--exact","stdio_stub_server","--nocapture","-q","--test-threads=1"],"env":[{{"name":"{STDIO_STUB_ENV}","value":"{STDIO_STUB_VALUE}"}}]}}"#,
        json!(test_binary)
    );
    let mut stdin = child.stdin.take().unwrap();
    offer(&mut stdin, &session, &entry);
    assert_eq!(
        session.mcp("mcpListTools", r#"{"server":"stdio-stub"}"#),
        (false, r#"["echo","process"]"#.to_owned())
    );
    assert_eq!(
        session.mcp("mcpCall", r#"{"server":"stdio-stub","tool":"echo","arguments":{"count":2}}"#),
        (false, r#"echo:{"count":2}"#.to_owned())
    );
    let first = session.mcp("mcpCall", r#"{"server":"stdio-stub","tool":"process","arguments":{}}"#);
    let second = session.mcp("mcpCall", r#"{"server":"stdio-stub","tool":"process","arguments":{}}"#);
    assert_eq!(first, second, "one process serves every request of the server");
    let (pid, value) = first.1.split_once(':').unwrap();
    assert_eq!(value, STDIO_STUB_VALUE);
    assert_ne!(pid, std::process::id().to_string());
    session.exit(0);
    assert_eq!(finish(child).0, Some(0));
    #[cfg(unix)]
    {
        let pid = nix::unistd::Pid::from_raw(pid.parse().unwrap());
        let deadline = Instant::now() + WAIT;
        while nix::sys::signal::kill(pid, None).is_ok() && Instant::now() < deadline {
            thread::sleep(Duration::from_millis(20));
        }
        assert!(
            nix::sys::signal::kill(pid, None).is_err(),
            "the exit of the agent stops the stdio server"
        );
    }
}

/// The stdio MCP server of [an_mcp_call_starts_the_stdio_server_of_the_current_session_and_the_exit_stops_it]: the test
/// binary itself, started with [STDIO_STUB_ENV]. Without the variable the test does nothing. It answers `initialize`,
/// lists `echo` and `process`, and answers `echo:<arguments>` and `<pid>:<value of the variable>`. The lines of libtest
/// are no JSON objects, and the agent skips them.
#[test]
fn stdio_stub_server() {
    let Ok(value) = std::env::var(STDIO_STUB_ENV) else { return };
    let mut stdout = std::io::stdout();
    for line in BufReader::new(std::io::stdin()).lines() {
        let line = line.unwrap();
        let request: Value = serde_json::from_str(&line).unwrap();
        let Some(id) = request.get("id") else { continue };
        let result = match request["method"].as_str().unwrap_or_default() {
            "initialize" => json!({ "protocolVersion": "2025-11-25", "capabilities": { "tools": {} } }).to_string(),
            "tools/list" => r#"{"tools":[{"name":"echo"},{"name":"process"}]}"#.to_owned(),
            "tools/call" => {
                let arguments = arguments_of(&line);
                let text = match request["params"]["name"].as_str().unwrap_or_default() {
                    "process" => format!("{}:{value}", std::process::id()),
                    tool => format!("{tool}:{arguments}"),
                };
                format!(r#"{{"content":[{{"type":"text","text":{}}}]}}"#, json!(text))
            }
            _ => "{}".to_owned(),
        };
        stdout
            .write_all(format!("not a frame\n{{\"jsonrpc\":\"2.0\",\"id\":{id},\"result\":{result}}}\n").as_bytes())
            .unwrap();
        stdout.flush().unwrap();
    }
}

/// The next inbound frame, which must be a typed line of the TUI.
fn typed_line(session: &Session) -> String {
    let frame = session.next();
    let line: Value = serde_json::from_str(frame["rawLine"].as_str().unwrap()).unwrap();
    assert_eq!(line["kind"], "air-terminal-input-line", "{frame}");
    line["line"].as_str().unwrap().to_owned()
}

/// The TUI reports its launch, takes its MCP servers from the config file of `--mcp-location`, and reports each typed
/// line without the bracketed paste markers. The typed line `exit` ends it.
#[test]
fn the_tui_calls_the_configured_server_and_reports_each_typed_line() {
    let stub = StubMcp::start();
    let location = tempfile::tempdir().unwrap();
    std::fs::write(location.path().join("mcp.json"), stub.config_json("stub")).unwrap();
    let location_text = location.path().display().to_string();
    let control = Control::start();
    let mut child = start(
        &["--air-terminal-tui", "--skip-update-check", "--mcp-location", &location_text],
        Some(&control),
        &[],
    );
    let session = control.session();
    assert!(session.hello["processId"].as_str().unwrap().starts_with("tui-"));
    let launch: Value = serde_json::from_str(session.next()["rawLine"].as_str().unwrap()).unwrap();
    assert_eq!(launch["kind"], "air-terminal-login-launch");
    assert_eq!(
        launch["args"],
        json!(["--air-terminal-tui", "--skip-update-check", "--mcp-location", location_text])
    );
    assert_eq!(launch["env"]["ACP_FAKE_AGENT_CONTROL_KEY"], AGENT);
    assert_eq!(
        session.mcp("mcpCall", r#"{"server":"stub","tool":"echo","arguments":{"action":"present"}}"#),
        (false, r#"echo:{"action":"present"}"#.to_owned())
    );
    let mut stdin = child.stdin.take().unwrap();
    stdin
        .write_all("\u{1b}[200~Click check: lane.confirm\r{\"viewId\":\"v\"}\u{1b}[201~\r\rexit\n".as_bytes())
        .unwrap();
    stdin.flush().unwrap();
    assert_eq!(typed_line(&session), "Click check: lane.confirm");
    assert_eq!(typed_line(&session), "{\"viewId\":\"v\"}");
    assert_eq!(typed_line(&session), "exit");
    assert_eq!(
        session.next(),
        json!({ "type": "lifecycle", "kind": "stdin_eof", "details": "stdin closed" })
    );
    assert_eq!(
        session.next(),
        json!({ "type": "lifecycle", "kind": "process_exit", "details": "input ended", "exitCode": 0 })
    );
    let (exit, stdout, _) = finish(child);
    assert_eq!(
        (exit, stdout.as_str()),
        (Some(0), "\u{1b}[?2004hAir terminal TUI fixture is ready\n")
    );
    assert_eq!(stub.methods(), ["initialize", "notifications/initialized", "tools/call"]);
}

/// Without a config flag the TUI offers no server, and the end of its input ends it.
#[test]
fn the_tui_without_a_config_offers_no_server_and_ends_with_its_input() {
    let control = Control::start();
    let mut child = start(&["--air-terminal-tui"], Some(&control), &[]);
    let session = control.session();
    assert_eq!(
        serde_json::from_str::<Value>(session.next()["rawLine"].as_str().unwrap()).unwrap()["kind"],
        "air-terminal-login-launch"
    );
    let (is_error, text) = session.mcp("mcpCall", r#"{"server":"air-session","tool":"air_ui","arguments":{}}"#);
    assert!(is_error && text.contains("No MCP server 'air-session'"), "{text}");
    drop(child.stdin.take());
    assert_eq!(
        session.next(),
        json!({ "type": "lifecycle", "kind": "stdin_eof", "details": "stdin closed" })
    );
    assert_eq!(finish(child).0, Some(0));
}

/// The terminal sign-in process reports how Air launched it, prints what the test tells it, and exits with the code of
/// the exit command. It reads no input.
#[test]
fn the_terminal_login_reports_its_launch_and_prints_what_the_test_says() {
    let control = Control::start();
    let child = start(
        &["--air-terminal-login", "--method", "browser"],
        Some(&control),
        &[("FAKE_AGENT_LOGIN", "1")],
    );
    let session = control.session();
    assert!(session.hello["processId"].as_str().unwrap().starts_with("login-"));
    let launch: Value = serde_json::from_str(session.next()["rawLine"].as_str().unwrap()).unwrap();
    assert_eq!(launch["args"], json!(["--air-terminal-login", "--method", "browser"]));
    assert_eq!(launch["env"]["FAKE_AGENT_LOGIN"], "1");
    assert_eq!(launch["cwd"], std::env::current_dir().unwrap().display().to_string());
    let write = session.command("write", r#"{"rawLine":"Signed in"}"#);
    assert_eq!(session.next(), json!({ "type": "writeAck", "commandId": write }));
    session.close_stdout();
    session.exit(5);
    let (exit, stdout, _) = finish(child);
    assert_eq!((exit, stdout.as_str()), (Some(5), "Signed in\n"));
}
