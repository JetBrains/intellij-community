//! What every controlled mode of the agent shares: the diagnostic lines, the process id, the connection to the control
//! server and the one end of the process.

use std::fmt::Display;
use std::io::{self, Write};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, PoisonError};
use std::time::{SystemTime, UNIX_EPOCH};

use avl_control::proxy::{self, Commands, ControlEndpoint, Lifecycle, MissingSetting, Proxy};

use crate::environment::Environment;
use crate::mcp::McpServers;

/// The `details` of the one `control_failure` that a process reports. The test process reads no more than the kind.
const CONTROL_FAILURE_DETAILS: &str = "IOException";

/// Writes one diagnostic line on standard error. A line that cannot be written is lost, because standard error is the
/// only place for it.
pub(crate) fn say(line: impl Display) {
    let _ = writeln!(io::stderr().lock(), "{line}");
}

/// `value` as one field of a diagnostic line: a line end becomes a space, and a `;` becomes `_`.
pub(crate) fn marker(value: &str) -> String {
    value.replace(['\n', '\r'], " ").replace(';', "_")
}

/// A process id that no other process of this agent has: `<prefix><pid>-<nanoseconds in base 36>`.
pub(crate) fn process_id(prefix: &str) -> String {
    let mut nanos = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
    let mut digits = Vec::new();
    loop {
        let digit = u32::try_from(nanos % 36).unwrap_or_default();
        digits.push(char::from_digit(digit, 36).unwrap_or('0'));
        nanos /= 36;
        if nanos == 0 {
            break;
        }
    }
    let suffix: String = digits.iter().rev().collect();
    format!("{prefix}{}-{suffix}", std::process::id())
}

/// The control endpoint that `environment` names, or the diagnostic line of the setting that it lacks. `mode` names
/// the mode in the line, as `terminal-login`, or is empty for the proxy.
pub(crate) fn endpoint(environment: &Environment, mode: &str) -> Result<ControlEndpoint, String> {
    ControlEndpoint::from_environment(|name| environment.get(name).map(str::to_owned)).map_err(|missing| {
        let setting = match missing {
            MissingSetting::Url => "URL",
            MissingSetting::AgentKey => "key",
        };
        let mode = if mode.is_empty() { String::new() } else { format!("{mode} ") };
        format!("ACP fake-agent {mode}control {setting} is not configured")
    })
}

/// How the end of the process treats the MCP servers that it started.
#[derive(Clone, Copy)]
pub(crate) enum McpEnd {
    /// Each `http` server gets `DELETE`, and each `stdio` process gets the end of its input, then a termination signal.
    /// The close waits for each of them.
    Close,
    /// No `http` server hears of the end, and each `stdio` process group gets a termination signal. The process does not
    /// wait, so it ends right after its last envelope.
    Abandon,
}

/// One agent process on the control server, after its `hello`.
pub(crate) struct Agent {
    pub(crate) proxy: Proxy,
    pub(crate) mcp: McpServers,
    /// Held by the one thread that ends the process, and never released.
    ending: Mutex<()>,
    /// Set when a thread starts to end the process.
    ended: AtomicBool,
    control_failure_sent: AtomicBool,
}

impl Agent {
    /// Connects to `endpoint` as the process `process_id`, and completes the `hello`.
    pub(crate) fn connect(endpoint: &ControlEndpoint, process_id: &str) -> io::Result<(Self, Commands)> {
        let (proxy, commands) = proxy::connect(endpoint, process_id)?;
        let agent = Self {
            proxy,
            mcp: McpServers::default(),
            ending: Mutex::new(()),
            ended: AtomicBool::new(false),
            control_failure_sent: AtomicBool::new(false),
        };
        Ok((agent, commands))
    }

    /// Whether a thread started to end the process. A failure after that start is a consequence of the end, and the
    /// process reports nothing more.
    pub(crate) fn is_ending(&self) -> bool {
        self.ended.load(Ordering::SeqCst)
    }

    /// Reports one `control_failure`. A later call reports nothing, and a report that cannot be sent is lost, because
    /// the control connection is the one that failed.
    pub(crate) fn report_control_failure(&self) {
        if !self.control_failure_sent.swap(true, Ordering::SeqCst) {
            let _ = self.proxy.lifecycle(Lifecycle::ControlFailure, Some(CONTROL_FAILURE_DETAILS), None);
        }
    }

    /// Ends the process after the exit command `code`: it reports `process_exit` and exits with the code. A code
    /// outside the range of a process exit code exits with 1. `mcp` says how the MCP servers end.
    pub(crate) fn exit(&self, code: i64, mcp: McpEnd) -> ! {
        self.finish(mcp, |proxy| {
            let _ = proxy.lifecycle(Lifecycle::ProcessExit, Some("exit command"), Some(code));
            i32::try_from(code).unwrap_or(1)
        })
    }

    /// Ends the process. `last` sends the last envelopes and answers the exit code. Then the control connection closes,
    /// each MCP server ends as `mcp` says, and the process exits. Only the first caller runs: a later caller waits for
    /// the exit.
    pub(crate) fn finish(&self, mcp: McpEnd, last: impl FnOnce(&Proxy) -> i32) -> ! {
        // The exit runs no destructor, so the guard stays held, and no other thread ends the process with another code.
        self.ended.store(true, Ordering::SeqCst);
        let _ending = self.ending.lock().unwrap_or_else(PoisonError::into_inner);
        let code = last(&self.proxy);
        self.proxy.close();
        match mcp {
            McpEnd::Close => self.mcp.close(),
            McpEnd::Abandon => self.mcp.abandon(),
        }
        let _ = io::stdout().flush();
        std::process::exit(code)
    }
}
