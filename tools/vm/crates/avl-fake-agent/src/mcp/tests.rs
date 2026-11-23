use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;

fn http(url: &str, headers: &[(&str, &str)]) -> ServerSpec {
    ServerSpec::Http {
        url: url.to_owned(),
        headers: headers
            .iter()
            .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
            .collect(),
    }
}

/// An entry without `type` takes its transport from its `url` or `command`, an entry of another transport is skipped,
/// and a later entry of a name replaces the earlier one in its place.
#[test]
fn the_servers_of_a_session_new_are_its_http_and_stdio_entries() {
    let params = json!({ "mcpServers": [
        { "type": "http", "name": "a", "url": "http://h/a", "headers": [{ "name": "Authorization", "value": "Bearer t" }] },
        { "name": "s", "command": "/bin/server", "args": ["-x", 1], "env": [{ "name": "K", "value": "v" }, { "name": "N" }] },
        { "type": "sse", "name": "skipped", "url": "http://h/sse" },
        { "type": "stdio", "name": "no-command", "url": "http://h/x" },
        { "url": "http://h/no-name" },
        { "name": "a", "url": "http://h/a2" },
    ]});
    assert_eq!(
        session_servers(Some(&params)),
        [
            ("a".to_owned(), http("http://h/a2", &[])),
            (
                "s".to_owned(),
                ServerSpec::Stdio {
                    command: "/bin/server".to_owned(),
                    args: vec!["-x".to_owned()],
                    env: [("K".to_owned(), "v".to_owned())].into(),
                }
            ),
        ]
    );
    assert_eq!(session_servers(Some(&json!({}))), []);
    assert_eq!(session_servers(None), []);
}

#[test]
fn the_servers_of_a_config_file_are_its_http_entries() {
    let config = json!({ "mcpServers": {
        "air": { "type": "http", "url": "http://h/mcp", "headers": { "Authorization": "Bearer t", "Bad": 1 } },
        "plain": { "url": "http://h/plain" },
        "local": { "type": "stdio", "command": "x" },
    }});
    assert_eq!(
        config_servers(&config),
        [
            ("air".to_owned(), http("http://h/mcp", &[("Authorization", "Bearer t")])),
            ("plain".to_owned(), http("http://h/plain", &[])),
        ]
    );
}

/// Before any offer, and for a name that the offer does not carry, a request answers an error result. Only a
/// `session/new` changes the offer.
#[test]
fn a_request_outside_the_offer_answers_an_error_result() {
    let servers = McpServers::default();
    let call = McpRequest::Call {
        server: "stub".to_owned(),
        tool: "echo".to_owned(),
        arguments: "{}".to_owned(),
    };
    assert_eq!(
        servers.answer(&call),
        Outcome::error("No MCP server 'stub' in the current offer; offered: []".to_owned())
    );
    servers.observe(r#"{"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"text":"session/new"}}"#);
    servers.observe(r#"{"jsonrpc":"2.0","id":2,"method":"session/new","params":{"mcpServers":[{"name":"a","url":"http://h/a"},{"name":"b","url":"http://h/b"}]}}"#);
    assert_eq!(
        servers.answer(&McpRequest::ListTools { server: "stub".to_owned() }),
        Outcome::error("No MCP server 'stub' in the current offer; offered: [a, b]".to_owned())
    );
}

/// A `stdio` command that cannot start is an error result that names the failure.
#[test]
fn a_stdio_server_that_cannot_start_answers_an_error_result() {
    let servers = McpServers::default();
    let missing = std::env::temp_dir().join("acp-missing-mcp-server").display().to_string();
    servers.offer(vec![(
        "missing".to_owned(),
        ServerSpec::Stdio {
            command: missing,
            args: Vec::new(),
            env: BTreeMap::new(),
        },
    )]);
    let outcome = servers.answer(&McpRequest::ListTools {
        server: "missing".to_owned(),
    });
    assert!(outcome.is_error);
    assert!(
        outcome.text.starts_with("IOException: cannot start the MCP server 'missing'"),
        "{}",
        outcome.text
    );
}
