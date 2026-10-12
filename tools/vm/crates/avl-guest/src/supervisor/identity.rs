//! Process identity: how the supervisor proves that a pid still names the process it recorded.
//!
//! A pid is reused, so it is not an identity. The start time and the process-group id together are what make a
//! later signal safe: if any of the three has changed, the process this run recorded is gone and something else
//! is wearing its number.

use std::os::unix::process::ExitStatusExt;
use std::process::{Command, ExitStatus, Stdio};
use std::str::FromStr;

use avl_wire::supervisor::RunState;
use nix::errno::Errno;
use nix::sys::signal::{Signal, kill, killpg};
use nix::unistd::Pid;

use super::System;

/// One process, identified by more than its pid.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ProcessIdentity {
    pub pid: i32,
    pub pgid: i32,
    /// `ps -o lstart=` with its whitespace collapsed, the one spelling both guests answer and the one the host records
    /// too. It is compared across supervisor invocations, so a different source (`/proc/<pid>/stat`) would render
    /// another string and every recorded run would then look dead - `supervisor_lost`, and no cancel for a live IDE.
    pub process_start: String,
}

fn ps_field(pid: i32, field: &str) -> Option<String> {
    let output = Command::new("/bin/ps")
        .args(["-o", field, "-p", &pid.to_string()])
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let text = String::from_utf8_lossy(&output.stdout).trim().to_owned();
    (!text.is_empty()).then_some(text)
}

/// The identity of `pid`, or `None` when there is no such process.
///
/// `/bin/ps` rather than /proc or a sysctl, deliberately: it is the one spelling that answers the same question on
/// both guests, and the string it answers is the one already recorded on live workers.
pub(crate) fn read_identity(pid: i32) -> Option<ProcessIdentity> {
    if pid <= 0 {
        return None;
    }
    let start = ps_field(pid, "lstart=")?;
    let pgid: i32 = ps_field(pid, "pgid=")?.parse().ok()?;
    if pgid <= 0 {
        return None;
    }
    Some(ProcessIdentity {
        pid,
        pgid,
        process_start: start.split_whitespace().collect::<Vec<_>>().join(" "),
    })
}

/// Whether any process of `pgid` is still there. Signal 0 delivers nothing and only reports existence, and EPERM
/// counts as alive: a group another user owns is a group that exists.
pub(crate) fn group_alive(pgid: i32) -> bool {
    if pgid <= 0 {
        return false;
    }
    match kill(Pid::from_raw(-pgid), None) {
        Ok(()) => true,
        Err(error) => error == Errno::EPERM,
    }
}

/// Delivers `signal` to every process of `pgid`. A group that is already gone is not an error worth reporting:
/// every caller checks the group again afterwards.
pub(crate) fn signal_group(pgid: i32, signal: Signal) {
    if pgid > 0 {
        let _ = killpg(Pid::from_raw(pgid), signal);
    }
}

/// Whether `expected` still names the process it was recorded for.
pub(crate) fn identity_matches(system: &dyn System, expected: &ProcessIdentity) -> bool {
    if expected.pid <= 0 || expected.pgid <= 0 || expected.process_start.is_empty() {
        return false;
    }
    system.identity(expected.pid).as_ref() == Some(expected)
}

pub(crate) fn supervisor_is_alive(system: &dyn System, state: &RunState) -> bool {
    match (state.supervisor_pid, state.supervisor_pgid, &state.supervisor_start) {
        (Some(pid), Some(pgid), Some(start)) => identity_matches(
            system,
            &ProcessIdentity {
                pid,
                pgid,
                process_start: start.clone(),
            },
        ),
        _ => false,
    }
}

pub(crate) fn child_is_alive(system: &dyn System, state: &RunState) -> bool {
    match (state.pid, state.pgid) {
        (Some(pid), Some(pgid)) => identity_matches(
            system,
            &ProcessIdentity {
                pid,
                pgid,
                process_start: state.process_start.clone().unwrap_or_default(),
            },
        ),
        _ => false,
    }
}

/// How a child ended: an exit code, or the name of the signal that ended it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ExitOutcome {
    pub code: Option<i32>,
    pub signal: Option<String>,
}

impl ExitOutcome {
    /// A child whose end nobody observed: the status a launch failure records.
    pub(super) const fn failed_to_start() -> Self {
        Self {
            code: Some(127),
            signal: None,
        }
    }
}

pub(crate) fn classify_exit(status: ExitStatus) -> ExitOutcome {
    if let Some(number) = status.signal() {
        // The wire carries the SIG name, which is what `Signal::as_str` answers.
        let name = Signal::try_from(number).map_or_else(|_| format!("signal {number}"), |signal| signal.as_str().to_owned());
        return ExitOutcome {
            code: None,
            signal: Some(name),
        };
    }
    ExitOutcome {
        code: Some(status.code().unwrap_or(255)),
        signal: None,
    }
}

/// The exit code a finished run reports: its own code, else `128 + signal` the way a shell reports one, else
/// 255 for an end nobody observed.
pub(crate) fn normalized_exit_code(code: Option<i32>, signal: Option<&str>) -> i32 {
    if let Some(code) = code {
        return code;
    }
    match signal.and_then(|name| Signal::from_str(name).ok()) {
        Some(signal) => 128 + signal as i32,
        None => 255,
    }
}
