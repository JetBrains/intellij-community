use std::net::TcpListener;
use std::thread;

use pretty_assertions::assert_eq;

use super::*;

fn endpoint(url: &str) -> ControlEndpoint {
    ControlEndpoint {
        url: url.to_owned(),
        agent_key: "key".to_owned(),
    }
}

#[test]
fn the_endpoint_comes_from_the_two_variables_and_a_blank_value_is_missing() {
    let lookup = |url: Option<&str>, key: Option<&str>| {
        ControlEndpoint::from_environment(|name| match name {
            CONTROL_URL_ENV => url.map(str::to_owned),
            CONTROL_AGENT_KEY_ENV => key.map(str::to_owned),
            _ => None,
        })
    };
    assert_eq!(
        lookup(Some("ws://h/control?token=t"), Some("key")),
        Ok(endpoint("ws://h/control?token=t"))
    );
    assert_eq!(lookup(None, Some("key")), Err(MissingSetting::Url));
    assert_eq!(lookup(Some(" "), Some("key")), Err(MissingSetting::Url));
    assert_eq!(lookup(Some("ws://h/control"), Some("\t")), Err(MissingSetting::AgentKey));
}

#[test]
fn a_redacted_url_hides_each_token_and_keeps_the_rest() {
    assert_eq!(
        endpoint("ws://127.0.0.1:1/control?token=secret").redacted_url(),
        "ws://127.0.0.1:1/control?token=REDACTED"
    );
    assert_eq!(
        endpoint("ws://h/c?a=1&token=x&b=2").redacted_url(),
        "ws://h/c?a=1&token=REDACTED&b=2"
    );
    assert_eq!(endpoint("ws://h/c?mytoken=x").redacted_url(), "ws://h/c?mytoken=x");
    assert_eq!(endpoint("ws://h/c").redacted_url(), "ws://h/c");
}

/// A control server for one proxy: it answers the `hello`, sends each JSON object text of `commands` with the bound
/// identity in front of its members, and answers every text message of the proxy after the commands, until the proxy
/// closes.
fn serve(commands: Vec<String>) -> (String, thread::JoinHandle<Vec<Value>>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let url = format!("ws://{}/control?token=t", listener.local_addr().unwrap());
    let server = thread::spawn(move || {
        let (stream, _) = listener.accept().unwrap();
        let head = websocket::read_head(&stream).unwrap();
        let (sender, mut receiver) = websocket::complete_upgrade(stream, &head).unwrap();
        let Ok(Message::Text(hello)) = receiver.next(&sender) else {
            panic!("no hello")
        };
        let hello: Value = serde_json::from_str(&hello).unwrap();
        let identity = json!({
            "connectionId": "connection-1", "generation": 1, "processId": hello["processId"], "agentKey": hello["agentKey"],
        });
        let mut ack = identity.clone();
        ack["type"] = json!("helloAck");
        ack["protocolVersion"] = json!(PROTOCOL_VERSION);
        sender.text(&ack.to_string()).unwrap();
        let identity = identity.to_string();
        for command in commands {
            let envelope = format!("{},{}", identity.strip_suffix('}').unwrap(), command.strip_prefix('{').unwrap());
            sender.text(&envelope).unwrap();
        }
        let mut received = Vec::new();
        while let Ok(Message::Text(text)) = receiver.next(&sender) {
            received.push(serde_json::from_str(&text).unwrap());
        }
        received
    });
    (url, server)
}

#[test]
fn an_mcp_command_carries_its_server_its_tool_and_its_arguments_as_spelled() {
    let arguments = r#"{"title":"Issues","count":2,"nested":{"z":1,"a":[true]}}"#;
    let call = format!(r#"{{"type":"mcpCall","commandId":"c-1","server":"stub","tool":"echo","arguments":{arguments}}}"#);
    let list = r#"{"type":"mcpListTools","commandId":"c-2","server":"stub"}"#.to_owned();
    let (url, server) = serve(vec![call, list]);
    let (proxy, mut commands) = connect(&endpoint(&url), "proc-1").unwrap();
    let Command::Mcp { id, request } = commands.next(&proxy).unwrap() else {
        panic!("no MCP call")
    };
    assert_eq!(
        (id.as_str(), request),
        (
            "c-1",
            McpRequest::Call {
                server: "stub".to_owned(),
                tool: "echo".to_owned(),
                arguments: arguments.to_owned(),
            }
        )
    );
    assert_eq!(
        commands.next(&proxy).unwrap(),
        Command::Mcp {
            id: "c-2".to_owned(),
            request: McpRequest::ListTools { server: "stub".to_owned() },
        }
    );
    proxy.mcp_result("c-1", false, "echo:{}").unwrap();
    proxy.lifecycle(Lifecycle::StdinEof, None, None).unwrap();
    proxy.lifecycle(Lifecycle::ProcessExit, Some("exit command"), Some(3)).unwrap();
    proxy.close();
    let received = server.join().unwrap();
    let fields = |envelope: &Value| {
        let mut envelope = envelope.clone();
        for identity in ["connectionId", "generation", "processId", "agentKey"] {
            envelope.as_object_mut().unwrap().remove(identity);
        }
        envelope
    };
    assert_eq!(
        received.iter().map(fields).collect::<Vec<_>>(),
        [
            json!({ "type": "mcpResult", "commandId": "c-1", "isError": false, "text": "echo:{}" }),
            json!({ "type": "lifecycle", "kind": "stdin_eof" }),
            json!({ "type": "lifecycle", "kind": "process_exit", "details": "exit command", "exitCode": 3 }),
        ]
    );
}

#[test]
fn an_mcp_call_without_object_arguments_breaks_the_protocol() {
    let call = r#"{"type":"mcpCall","commandId":"c-1","server":"stub","tool":"echo","arguments":[1]}"#.to_owned();
    let (url, server) = serve(vec![call]);
    let (proxy, mut commands) = connect(&endpoint(&url), "proc-1").unwrap();
    let error = commands.next(&proxy).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::InvalidData);
    assert!(error.to_string().contains("has no server, tool or arguments"), "{error}");
    proxy.close();
    server.join().unwrap();
}
