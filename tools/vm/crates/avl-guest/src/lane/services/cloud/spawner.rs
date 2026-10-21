//! The Agent Spawner of the emulated JCP: the WebSocket that carries ACP, and the route that deletes a session.
//!
//! Each accepted dial of the IDE connects to the control server in the IDE as one agent process, under the key of the
//! Cloud agent that the running suite uses, and passes the text frames both ways. A scenario therefore steers a Cloud
//! agent through the mailbox of a local one, and a second dial with the same key is the restart of that process. The
//! commands of the control server act on the dial:
//!
//! | command | what the dial does |
//! | --- | --- |
//! | `write` | sends the line as one text message, then acknowledges the command |
//! | `writeMalformed` | sends the bytes as one text message, then acknowledges the command |
//! | `closeStdout` | reports `stdout_closed`, acknowledges the command, and closes with 1000 |
//! | `exit` | reports `process_exit` with the code, acknowledges the command, closes the control connection, and closes with 1001 |
//! | `mcpCall`, `mcpListTools` | answers an error result, because a remote spawner offers no MCP server |
//!
//! The end of a dial before an exit reports `stdin_eof`. A dial before the attach, a control server that refuses the
//! `hello`, and a control connection that ends first each close the dial with 1011.
//!
//! An exit closes the control connection at once, as an agent process that exits does, whatever the dial does. The dial
//! then waits at most [websocket::CLOSE_GRACE] for the answer of the IDE to its close, and then its connection ends. So
//! an IDE that does not read the dial does not keep the agent process alive.
//!
//! The spawner checks that a bearer token is present and records it. It does not check the value, because the emulated
//! account mints a token for any audience.

use std::collections::{BTreeMap, VecDeque};
use std::io;
use std::net::TcpStream;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::Serialize;

use self::proxy::{Command, Commands, Lifecycle, Proxy};
use super::super::http::{self, Request, Response};
use super::super::websocket::{self, Message, Receiver, Sender};

mod proxy;
#[cfg(test)]
mod tests;

/// The path of the ACP WebSocket below the JCP base URL.
pub(crate) const ACP_PATH: &str = "/agent-spawner/acp";

/// The prefix of the session delete route below the JCP base URL.
const SESSIONS_PREFIX: &str = "/agent-spawner/sessions/";

/// The status of a dial without a bearer token, and the default of a forced status.
pub(crate) const UNAUTHORIZED: u16 = 401;

/// What an MCP command of the control server gets.
const NO_MCP_SERVER: &str = "a remote spawner offers no MCP server";

/// Where the control server in the IDE listens, with the token of the credential, and the agent key that a dial
/// connects as.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ControlEndpoint {
    /// `ws://127.0.0.1:<port>/control?token=<token>`.
    pub url: String,
    pub agent_key: String,
}

/// One dial of the IDE, in arrival order.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct Dial {
    /// The position of the dial among all dials since the start, from 0.
    pub index: usize,
    /// The `Authorization` header of the dial.
    pub authorization: Option<String>,
    /// `accepted`, or `rejected <status>`.
    pub outcome: String,
}

/// What the spawner holds, as the control route states it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Record {
    /// The agent key that a dial connects as, `null` before the attach.
    pub agent_key: Option<String>,
    /// Every dial since the last clear, in arrival order.
    pub dials: Vec<Dial>,
    /// The session ids that the IDE deleted since the last clear, in arrival order.
    pub deleted: Vec<String>,
    /// The statuses that the next dials answer instead of the upgrade, in order.
    pub fail_next: Vec<u16>,
}

#[derive(Default)]
struct State {
    control: Option<ControlEndpoint>,
    /// The count of every dial since the start, which a clear keeps.
    connections: usize,
    dials: Vec<Dial>,
    deleted: Vec<String>,
    forced: VecDeque<u16>,
    /// The two connections of each open dial, by its index, which a stop ends.
    open: BTreeMap<usize, Vec<TcpStream>>,
    stopped: bool,
}

/// The Agent Spawner of one emulator.
#[derive(Default)]
pub(crate) struct Spawner {
    state: Arc<Mutex<State>>,
}

impl Spawner {
    /// Points every later dial at the control server of `control`. `None` detaches, and a later dial closes with 1011.
    pub(crate) fn attach(&self, control: Option<ControlEndpoint>) {
        lock(&self.state).control = control;
    }

    /// The next `count` dials answer `status` instead of the upgrade.
    pub(crate) fn fail_next_dials(&self, count: usize, status: u16) {
        lock(&self.state).forced.extend(std::iter::repeat_n(status, count));
    }

    /// What the spawner holds.
    pub(crate) fn record(&self) -> Record {
        let state = lock(&self.state);
        Record {
            agent_key: state.control.as_ref().map(|control| control.agent_key.clone()),
            dials: state.dials.clone(),
            deleted: state.deleted.clone(),
            fail_next: state.forced.iter().copied().collect(),
        }
    }

    /// Forgets the dials, the deleted sessions and the forced statuses, and answers what it held. The attach and the
    /// open dials stay.
    pub(crate) fn clear(&self) -> Record {
        let record = self.record();
        let mut state = lock(&self.state);
        state.dials.clear();
        state.deleted.clear();
        state.forced.clear();
        record
    }

    /// Ends every open dial, and every later dial at once.
    pub(crate) fn stop(&self) {
        let mut state = lock(&self.state);
        state.stopped = true;
        for stream in state.open.values().flatten() {
            let _ = stream.shutdown(std::net::Shutdown::Both);
        }
    }

    /// Answers a request that this spawner owns, or `None`. `path` is below the JCP base URL, without a trailing slash.
    pub(crate) fn handle(&self, request: &Request, path: &str) -> Option<Response> {
        if path == ACP_PATH && is_upgrade(request) {
            return Some(self.dial(request));
        }
        let id = path.strip_prefix(SESSIONS_PREFIX).filter(|id| !id.contains('/'))?;
        if request.method != "DELETE" {
            return None;
        }
        lock(&self.state).deleted.push(http::form_decode(id));
        Some(Response::empty(204))
    }

    fn dial(&self, request: &Request) -> Response {
        let authorization = request.header("Authorization").map(str::to_owned);
        let mut state = lock(&self.state);
        let index = state.connections;
        state.connections += 1;
        let bearer = authorization.as_deref().is_some_and(|value| value.starts_with("Bearer "));
        // A forced status wins over the missing token, and a dial without a token still takes it.
        if let Some(status) = state.forced.pop_front().or((!bearer).then_some(UNAUTHORIZED)) {
            state.dials.push(Dial {
                index,
                authorization,
                outcome: format!("rejected {status}"),
            });
            return Response::empty(status);
        }
        state.dials.push(Dial {
            index,
            authorization,
            outcome: "accepted".to_owned(),
        });
        let shared = Arc::clone(&self.state);
        Response::upgrade(
            request.header("Sec-WebSocket-Key").unwrap_or_default(),
            Box::new(move |stream| bridge(&shared, index, stream)),
        )
    }
}

fn lock(state: &Mutex<State>) -> MutexGuard<'_, State> {
    state.lock().unwrap_or_else(PoisonError::into_inner)
}

/// Whether `request` asks for a WebSocket upgrade: `Upgrade: websocket` and a `Sec-WebSocket-Key`.
fn is_upgrade(request: &Request) -> bool {
    request
        .header("Upgrade")
        .is_some_and(|value| value.eq_ignore_ascii_case("websocket"))
        && request.header("Sec-WebSocket-Key").is_some()
}

/// The open connections of one dial in the state, which a stop ends. Its drop forgets them.
struct Open<'a> {
    state: &'a Mutex<State>,
    index: usize,
}

impl<'a> Open<'a> {
    /// Registers the dial `index` on `stream`, or answers `None` when the spawner stopped.
    fn register(state: &'a Mutex<State>, index: usize, stream: &TcpStream) -> Option<Self> {
        let open = Self { state, index };
        open.add(stream.try_clone()).then_some(open)
    }

    /// Adds `stream` to the connections of the dial, and answers `false` when the spawner stopped.
    fn add(&self, stream: io::Result<TcpStream>) -> bool {
        let Ok(stream) = stream else { return false };
        let mut state = lock(self.state);
        if state.stopped {
            return false;
        }
        state.open.entry(self.index).or_default().push(stream);
        true
    }
}

impl Drop for Open<'_> {
    fn drop(&mut self) {
        lock(self.state).open.remove(&self.index);
    }
}

/// Bridges the dial `index` on `stream` to the control server, until either side ends.
fn bridge(state: &Mutex<State>, index: usize, stream: TcpStream) {
    let _ = stream.set_read_timeout(None);
    let Some(open) = Open::register(state, index, &stream) else {
        let _ = stream.shutdown(std::net::Shutdown::Both);
        return;
    };
    let Ok((ide, mut dial)) = websocket::accept(stream) else { return };
    let control = lock(state).control.clone();
    let connected = match &control {
        None => Err(io::Error::other("no suite attached the spawner to a Cloud agent")),
        Some(control) => proxy::connect(control, &process_id(index)),
    };
    let (proxy, mut commands) = match connected {
        Ok(connected) => connected,
        Err(error) => {
            crate::lane::channel::log(&format!("the spawner dial {index} did not reach the control server: {error}"));
            ide.close(websocket::INTERNAL_ERROR);
            while let Ok(Message::Text(_)) = dial.next(&ide) {}
            ide.shutdown();
            return;
        }
    };
    let ends = Ends::default();
    if open.add(proxy.sender().try_clone_stream()) {
        thread::scope(|scope| {
            scope.spawn(|| forward_inbound(&mut dial, &ide, &proxy, &ends));
            apply_commands(index, &mut commands, &ide, &proxy, &ends);
        });
    }
    proxy.shutdown();
    ide.shutdown();
    drop(open);
}

/// The process id of the dial `index`. The time keeps it unique when a daemon that boots again counts from 0.
fn process_id(index: usize) -> String {
    let nanos = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
    format!("spawner-{index}-{nanos:x}")
}

/// What the two halves of one bridge tell each other. The forwarding half marks the end of the dial. The command half
/// marks the exit of the agent process before it closes the connections.
#[derive(Default)]
struct Ends {
    dial_ended: Mutex<bool>,
    changed: Condvar,
    exited: AtomicBool,
}

impl Ends {
    fn mark_dial_end(&self) {
        *self.dial_end() = true;
        self.changed.notify_all();
    }

    fn dial_ended(&self) -> bool {
        *self.dial_end()
    }

    /// Waits at most `timeout` for the end of the dial, and answers whether the dial ended.
    fn await_dial_end(&self, timeout: Duration) -> bool {
        let (ended, _) = self
            .changed
            .wait_timeout_while(self.dial_end(), timeout, |ended| !*ended)
            .unwrap_or_else(PoisonError::into_inner);
        *ended
    }

    fn mark_exit(&self) {
        self.exited.store(true, Ordering::SeqCst);
    }

    fn exited(&self) -> bool {
        self.exited.load(Ordering::SeqCst)
    }

    fn dial_end(&self) -> MutexGuard<'_, bool> {
        self.dial_ended.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

/// Forwards each text message of the IDE as an `inbound` frame, until the dial ends. The end reports `stdin_eof`,
/// unless the agent process exited.
fn forward_inbound(dial: &mut Receiver, ide: &Sender, proxy: &Proxy, ends: &Ends) {
    let forwarded = loop {
        match dial.next(ide) {
            Ok(Message::Text(text)) => {
                if let Err(error) = proxy.inbound(&text) {
                    break Err(error);
                }
            }
            Ok(Message::Close(code)) => {
                ide.close(code.unwrap_or(websocket::NORMAL_CLOSURE));
                break Ok(());
            }
            Err(error) => {
                if error.kind() == io::ErrorKind::InvalidData {
                    ide.close(websocket::PROTOCOL_ERROR);
                }
                break Ok(());
            }
        }
    };
    ends.mark_dial_end();
    // The exit closed both connections, and an agent process that exited reports nothing more.
    if ends.exited() {
        return;
    }
    let reported = forwarded.and_then(|()| proxy.lifecycle(Lifecycle::StdinEof, "the WebSocket closed", None));
    if let Err(error) = reported {
        let _ = proxy.lifecycle(Lifecycle::ControlFailure, &format!("{:?}", error.kind()), None);
        ide.close(websocket::INTERNAL_ERROR);
    }
    proxy.close();
}

/// Applies each command of the control server to the dial `index`, until the control connection ends. When it ends
/// before the dial and without an exit, the dial closes with 1011.
fn apply_commands(index: usize, commands: &mut Commands, ide: &Sender, proxy: &Proxy, ends: &Ends) {
    let ended = loop {
        let command = match commands.next(proxy) {
            Ok(command) => command,
            Err(error) => break error,
        };
        // A write to a dial that already closed is lost, as a write to a closed stdout is. The command is acknowledged.
        let applied = match command {
            Command::Write { id, line } => {
                let _ = ide.text(&line);
                proxy.ack(&id)
            }
            Command::WriteMalformed { id, bytes } => {
                let _ = ide.text(&String::from_utf8_lossy(&bytes));
                proxy.ack(&id)
            }
            Command::CloseStdout { id } => proxy
                .lifecycle(Lifecycle::StdoutClosed, "the WebSocket closed by control", None)
                .and_then(|()| proxy.ack(&id))
                .map(|()| {
                    ide.close(websocket::NORMAL_CLOSURE);
                }),
            Command::Exit { id, code } => proxy
                .lifecycle(Lifecycle::ProcessExit, "exit command", Some(code))
                .and_then(|()| proxy.ack(&id))
                .map(|()| exit(ide, proxy, ends)),
            Command::Mcp { id } => proxy.mcp_error(&id, NO_MCP_SERVER),
        };
        if let Err(error) = applied {
            break error;
        }
    };
    proxy.close();
    if !ends.dial_ended() && !ends.exited() {
        crate::lane::channel::log(&format!("the control channel of the spawner dial {index} ended: {ended}"));
        ide.close(websocket::INTERNAL_ERROR);
    }
}

/// Ends the agent process after the acknowledged exit command. The control connection closes at once, and the dial
/// closes with 1001. A read of the dial that already waits does not get the bound of the close. So when the IDE does
/// not answer within [websocket::CLOSE_GRACE], the connection of the dial ends without the answer.
fn exit(ide: &Sender, proxy: &Proxy, ends: &Ends) {
    ends.mark_exit();
    proxy.close();
    ide.close(websocket::GOING_AWAY);
    if !ends.await_dial_end(websocket::CLOSE_GRACE) {
        ide.shutdown();
    }
}
