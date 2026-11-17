//! The MCP client of the agent: the servers that the IDE offered, and one connected client per server.
//!
//! The controlled proxy takes the offer of the last `session/new`. The terminal TUI takes the servers of its MCP config
//! file. A client connects on the first request to its server and lives until the process ends. An `http` server goes
//! over streamable HTTP ([http]). A `stdio` server is a child process ([stdio]). A request before any offer, or for a
//! server that the offer does not name, answers an error result and never fails the process.
//!
//! The client covers the requests that the tests make: `initialize`, `tools/list` and `tools/call`. It answers no
//! request of a server.

use std::collections::BTreeMap;
use std::io;
use std::sync::{Mutex, MutexGuard, PoisonError};
use std::time::Duration;

use avl_control::json::raw_member;
use avl_control::proxy::McpRequest;
use serde_json::Value;

use crate::json::{parse_object, quote, quote_all};
use crate::process::{marker, say};

mod http;
mod stdio;

#[cfg(test)]
mod tests;

/// The protocol version that the client offers in `initialize`, the `LATEST_PROTOCOL_VERSION` of the MCP Kotlin SDK
/// that the Air backend serves with.
const PROTOCOL_VERSION: &str = "2025-11-25";

/// The name that the client gives in `initialize`.
const CLIENT_NAME: &str = "acp-scenario-agent";

/// Bounds each request to a server.
const TIMEOUT: Duration = Duration::from_secs(30);

/// One MCP server entry of an offer.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum ServerSpec {
    /// A streamable-HTTP server.
    Http { url: String, headers: BTreeMap<String, String> },
    /// A server that the agent starts as a child process, with the environment of the agent and `env`.
    Stdio {
        command: String,
        args: Vec<String>,
        env: BTreeMap<String, String>,
    },
}

/// The servers of one offer, by name, in the order of the offer.
pub(crate) type Offer = Vec<(String, ServerSpec)>;

/// The outcome of one MCP command: the text of the tool, the JSON array of the tool names, or the error text.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Outcome {
    pub(crate) is_error: bool,
    pub(crate) text: String,
}

impl Outcome {
    const fn error(text: String) -> Self {
        Self { is_error: true, text }
    }
}

/// Every `http` and `stdio` entry of `params.mcpServers` of a `session/new`. An entry without `type` is an `http` entry
/// when it has a `url`, and a `stdio` entry when it has a `command`. An entry of another transport is skipped. A later
/// entry of a name replaces an earlier one in its place.
pub(crate) fn session_servers(params: Option<&Value>) -> Offer {
    let mut offer = Offer::new();
    let entries = params.and_then(|params| params.get("mcpServers")).and_then(Value::as_array);
    for server in entries.into_iter().flatten() {
        let text = |name: &str| server.get(name).and_then(Value::as_str);
        let Some(name) = text("name") else { continue };
        let kind = text("type");
        let spec = if let Some(url) = text("url").filter(|_| kind.is_none_or(|kind| kind == "http")) {
            ServerSpec::Http {
                url: url.to_owned(),
                headers: name_value_pairs(server.get("headers")),
            }
        } else if let Some(command) = text("command").filter(|_| kind.is_none_or(|kind| kind == "stdio")) {
            ServerSpec::Stdio {
                command: command.to_owned(),
                args: server
                    .get("args")
                    .and_then(Value::as_array)
                    .into_iter()
                    .flatten()
                    .filter_map(Value::as_str)
                    .map(str::to_owned)
                    .collect(),
                env: name_value_pairs(server.get("env")),
            }
        } else {
            continue;
        };
        put(&mut offer, name, spec);
    }
    offer
}

/// Every `http` entry of the `mcpServers` object of an MCP config file, as Air writes it for a terminal CLI:
/// `{"mcpServers":{"<name>":{"type":"http","url":"...","headers":{"<name>":"<value>"}}}}`.
pub(crate) fn config_servers(config: &Value) -> Offer {
    let mut offer = Offer::new();
    let entries = config.get("mcpServers").and_then(Value::as_object);
    for (name, server) in entries.into_iter().flatten() {
        let Some(url) = server.get("url").and_then(Value::as_str) else {
            continue;
        };
        if server.get("type").and_then(Value::as_str).is_some_and(|kind| kind != "http") {
            continue;
        }
        let headers = server
            .get("headers")
            .and_then(Value::as_object)
            .into_iter()
            .flatten()
            .filter_map(|(name, value)| Some((name.clone(), value.as_str()?.to_owned())))
            .collect();
        put(
            &mut offer,
            name,
            ServerSpec::Http {
                url: url.to_owned(),
                headers,
            },
        );
    }
    offer
}

fn put(offer: &mut Offer, name: &str, spec: ServerSpec) {
    match offer.iter_mut().find(|(known, _)| known == name) {
        Some(entry) => entry.1 = spec,
        None => offer.push((name.to_owned(), spec)),
    }
}

/// The `[{"name":…,"value":…}]` array of the headers of an `http` entry or of the env of a `stdio` entry.
fn name_value_pairs(array: Option<&Value>) -> BTreeMap<String, String> {
    array
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|pair| {
            let text = |name: &str| pair.get(name).and_then(Value::as_str).map(str::to_owned);
            Some((text("name")?, text("value")?))
        })
        .collect()
}

/// The servers that the IDE offered, and the clients of the servers that a request reached.
#[derive(Default)]
pub(crate) struct McpServers {
    offered: Mutex<Offer>,
    clients: Mutex<Vec<(String, Client)>>,
}

impl McpServers {
    /// Takes the offer of `line` when it is a `session/new` request.
    pub(crate) fn observe(&self, line: &str) {
        if !line.contains("session/new") {
            return;
        }
        let Ok(frame) = parse_object(line) else { return };
        if frame.get("method").and_then(Value::as_str) == Some("session/new") {
            self.offer(session_servers(frame.get("params")));
        }
    }

    /// Replaces the offer with `offer`.
    pub(crate) fn offer(&self, offer: Offer) {
        *lock(&self.offered) = offer;
    }

    /// Makes the request of an MCP command, and writes its diagnostic line.
    pub(crate) fn answer(&self, request: &McpRequest) -> Outcome {
        match request {
            McpRequest::Call { server, tool, arguments } => {
                let outcome = self.request(server, |client| client.call_tool(tool, arguments));
                say(format_args!(
                    "acp-fake-agent-proxy-mcp-call;server={};tool={};isError={}",
                    marker(server),
                    marker(tool),
                    outcome.is_error
                ));
                outcome
            }
            McpRequest::ListTools { server } => {
                let outcome = self.request(server, |client| {
                    let names = client.list_tools()?;
                    Ok(Outcome {
                        is_error: false,
                        text: quote_all(names.iter().map(String::as_str)),
                    })
                });
                say(format_args!(
                    "acp-fake-agent-proxy-mcp-list-tools;server={};isError={}",
                    marker(server),
                    outcome.is_error
                ));
                outcome
            }
        }
    }

    /// Runs `request` on the client of `server`. A client that ended or whose server the offer changed starts again.
    fn request(&self, server: &str, request: impl FnOnce(&mut Client) -> io::Result<Outcome>) -> Outcome {
        let spec = {
            let offered = lock(&self.offered);
            let Some((_, spec)) = offered.iter().find(|(name, _)| name == server) else {
                let names: Vec<&str> = offered.iter().map(|(name, _)| name.as_str()).collect();
                return Outcome::error(format!(
                    "No MCP server '{server}' in the current offer; offered: [{}]",
                    names.join(", ")
                ));
            };
            spec.clone()
        };
        let mut clients = lock(&self.clients);
        if let Some(index) = clients
            .iter_mut()
            .position(|(name, client)| name == server && !(client.is_alive() && client.spec == spec))
        {
            clients.remove(index).1.close();
        }
        if !clients.iter().any(|(name, _)| name == server) {
            match Client::start(server, spec) {
                Ok(client) => clients.push((server.to_owned(), client)),
                Err(error) => return Outcome::error(format!("IOException: {error}")),
            }
        }
        let Some((_, client)) = clients.iter_mut().find(|(name, _)| name == server) else {
            return Outcome::error(format!("IOException: the MCP server '{server}' has no client"));
        };
        request(client).unwrap_or_else(|error| Outcome::error(format!("IOException: {error}")))
    }

    /// Ends every MCP session: an `http` server gets `DELETE`, and a `stdio` process stops.
    pub(crate) fn close(&self) {
        for (_, mut client) in lock(&self.clients).drain(..) {
            client.close();
        }
    }

    /// Leaves every MCP session at once: an `http` server gets no request, and a `stdio` process group gets a
    /// termination signal. Nothing waits.
    pub(crate) fn abandon(&self) {
        for (_, mut client) in lock(&self.clients).drain(..) {
            client.transport.abandon();
        }
    }
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

/// One JSON-RPC message of a server: its text and its value.
struct Frame {
    text: String,
    value: Value,
}

impl Frame {
    /// The message `text`, which must be a JSON object.
    fn parse(server: &str, text: String) -> io::Result<Self> {
        match parse_object(&text) {
            Ok(value) => Ok(Self { text, value }),
            Err(_) => Err(io::Error::other(format!("MCP server '{server}' wrote a malformed frame: {text}"))),
        }
    }

    /// Whether this message is the response of the request `id`.
    fn answers(&self, id: u64) -> bool {
        self.value.get("method").is_none() && self.value.get("id").and_then(Value::as_u64) == Some(id)
    }

    /// The text of the member `name`, as the server spells it.
    fn member(&self, name: &str) -> String {
        raw_member(&self.text, name).unwrap_or_else(|| "null".to_owned())
    }
}

/// The connection to one server.
enum Transport {
    Http(http::HttpTransport),
    Stdio(stdio::StdioTransport),
}

impl Transport {
    fn request(&mut self, id: u64, method: &str, body: &str) -> io::Result<Frame> {
        match self {
            Self::Http(transport) => transport.request(id, method, body),
            Self::Stdio(transport) => transport.request(id, method, body),
        }
    }

    fn initialized(&mut self, body: &str) -> io::Result<()> {
        match self {
            Self::Http(transport) => transport.initialized(body),
            Self::Stdio(transport) => transport.write(body),
        }
    }

    fn is_alive(&mut self) -> bool {
        match self {
            Self::Http(transport) => transport.is_alive(),
            Self::Stdio(transport) => transport.is_alive(),
        }
    }

    fn close(&mut self) {
        match self {
            Self::Http(transport) => transport.close(),
            Self::Stdio(transport) => transport.close(),
        }
    }

    fn abandon(&mut self) {
        match self {
            Self::Http(_) => {}
            Self::Stdio(transport) => transport.abandon(),
        }
    }
}

/// The client of one server, after a successful `initialize`.
struct Client {
    name: String,
    spec: ServerSpec,
    transport: Transport,
    last_id: u64,
}

impl Client {
    /// Connects to the server `name` of `spec`, and completes the MCP handshake.
    fn start(name: &str, spec: ServerSpec) -> io::Result<Self> {
        let transport = match &spec {
            ServerSpec::Http { url, headers } => Transport::Http(http::HttpTransport::new(name, url, headers, TIMEOUT)),
            ServerSpec::Stdio { command, args, env } => Transport::Stdio(stdio::StdioTransport::start(name, command, args, env, TIMEOUT)?),
        };
        let mut client = Self {
            name: name.to_owned(),
            spec,
            transport,
            last_id: 0,
        };
        if let Err(error) = client.initialize() {
            client.close();
            return Err(error);
        }
        Ok(client)
    }

    fn initialize(&mut self) -> io::Result<()> {
        let params = format!(
            "{{\"protocolVersion\":{},\"capabilities\":{{}},\"clientInfo\":{{\"name\":{},\"version\":\"1\"}}}}",
            quote(PROTOCOL_VERSION),
            quote(CLIENT_NAME)
        );
        let response = self.request("initialize", &params)?;
        if !response.value.get("result").is_some_and(Value::is_object) {
            return Err(io::Error::other(format!(
                "MCP server '{}' rejected initialize: {}",
                self.name, response.text
            )));
        }
        self.transport
            .initialized("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
    }

    /// The names of the tools that the server lists, in the order of the server.
    fn list_tools(&mut self) -> io::Result<Vec<String>> {
        let response = self.request("tools/list", "{}")?;
        if response.value.get("error").is_some_and(Value::is_object) {
            return Err(io::Error::other(format!("tools/list failed: {}", response.member("error"))));
        }
        let tools = response.value.pointer("/result/tools").and_then(Value::as_array);
        let tools = tools.ok_or_else(|| io::Error::other(format!("tools/list response has no tools: {}", response.text)))?;
        Ok(tools
            .iter()
            .filter_map(|tool| tool.get("name").and_then(Value::as_str).map(str::to_owned))
            .collect())
    }

    /// Calls `tool` with the JSON object text `arguments`, and answers the text of the first content block.
    fn call_tool(&mut self, tool: &str, arguments: &str) -> io::Result<Outcome> {
        let response = self.request("tools/call", &format!("{{\"name\":{},\"arguments\":{arguments}}}", quote(tool)))?;
        if response.value.get("error").is_some_and(Value::is_object) {
            return Ok(Outcome::error(response.member("error")));
        }
        let Some(result) = response.value.get("result").filter(|result| result.is_object()) else {
            return Err(io::Error::other(format!("tools/call response of '{tool}' has no result")));
        };
        let text = result.pointer("/content/0/text").and_then(Value::as_str);
        Ok(Outcome {
            is_error: result.get("isError").and_then(Value::as_bool).unwrap_or(false),
            text: text.map_or_else(|| response.member("result"), str::to_owned),
        })
    }

    fn request(&mut self, method: &str, params: &str) -> io::Result<Frame> {
        self.last_id += 1;
        let id = self.last_id;
        let body = format!(
            "{{\"jsonrpc\":\"2.0\",\"id\":{id},\"method\":{},\"params\":{params}}}",
            quote(method)
        );
        self.transport.request(id, method, &body)
    }

    fn is_alive(&mut self) -> bool {
        self.transport.is_alive()
    }

    fn close(&mut self) {
        self.transport.close();
    }
}
