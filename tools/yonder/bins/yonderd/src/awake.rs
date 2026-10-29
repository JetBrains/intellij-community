//! Keeps the host awake while a client talks to it and while a run goes on. A Mac that a connection woke (a wake for
//! network access) sleeps again when it is idle, and a running build does not count as activity, so on 2026-10-26 the
//! Studio fell asleep in the middle of runs. `caffeinate` of macOS (`/usr/bin/caffeinate`) holds the power assertions
//! `PreventUserIdleSystemSleep` (`-i`) and `PreventSystemSleep` (`-s`, on AC power) while it lives, and `-w <pid>` ends
//! it with the process it watches, so a crash of the agent or of a supervisor never keeps the host awake.
//!
//! - `serve` holds one shared assertion while any client has a connection after its hello ([`Awake::hold`]): the pack,
//!   the start, the follow with its heartbeats and the fetch. A client that went away loses its connection to the
//!   deadline of the heartbeats, and the host may sleep again; the next connection wakes it.
//! - A supervisor holds one for its whole life ([`hold_for_life`]), so the command runs to its end even when its client
//!   is gone.
//!
//! Elsewhere than on macOS no program holds an assertion. `YONDERD_TEST_CAFFEINATE` names a fake one for a test, and
//! `none` turns it off.

use std::ffi::OsString;
use std::process::{Child, Command, Stdio};
use std::sync::{Mutex, PoisonError};

use crate::serve::log;

/// The environment variable of a fake `caffeinate` for a test, or `none`.
pub(crate) const TEST_CAFFEINATE: &str = "YONDERD_TEST_CAFFEINATE";

/// The program that holds the assertions: the one of [`TEST_CAFFEINATE`], else `/usr/bin/caffeinate` on macOS.
pub(crate) fn program() -> Option<OsString> {
    match std::env::var_os(TEST_CAFFEINATE) {
        Some(program) if program.is_empty() || program == "none" => None,
        Some(program) => Some(program),
        None => cfg!(target_os = "macos").then(|| OsString::from("/usr/bin/caffeinate")),
    }
}

/// `<program> -i -s -w <pid>`, in `/`, with no streams. A program that does not start is a line of the log: the run goes
/// on, only the host may sleep.
fn start(program: &OsString, pid: u32) -> Option<Child> {
    Command::new(program)
        .args(["-i", "-s", "-w", &pid.to_string()])
        .current_dir("/")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .inspect_err(|error| {
            log(&format!(
                "cannot start {} to keep the host awake: {error}",
                program.to_string_lossy()
            ));
        })
        .ok()
}

/// The assertion of `serve`, shared by its connections.
#[derive(Debug)]
pub(crate) struct Awake {
    program: Option<OsString>,
    held: Mutex<Held>,
}

#[derive(Debug, Default)]
struct Held {
    /// The connections that hold the host awake.
    count: usize,
    /// The program that holds the assertion while `count` is above zero.
    child: Option<Child>,
}

impl Awake {
    pub(crate) fn new(program: Option<OsString>) -> Self {
        Self {
            program,
            held: Mutex::default(),
        }
    }

    /// Holds the host awake until the answer drops. The first holder starts the program, and the last one stops it.
    pub(crate) fn hold(&self) -> Holding<'_> {
        let mut held = self.held.lock().unwrap_or_else(PoisonError::into_inner);
        if held.count == 0
            && let Some(program) = &self.program
        {
            held.child = start(program, std::process::id());
        }
        held.count += 1;
        Holding(self)
    }
}

/// One holder of [`Awake`].
#[derive(Debug)]
pub(crate) struct Holding<'a>(&'a Awake);

impl Drop for Holding<'_> {
    fn drop(&mut self) {
        let mut held = self.0.held.lock().unwrap_or_else(PoisonError::into_inner);
        held.count -= 1;
        if held.count == 0
            && let Some(mut child) = held.child.take()
        {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

/// Holds the host awake for the life of this process, a supervisor: the program watches this process and ends with it,
/// so it is never waited for here.
pub(crate) fn hold_for_life() {
    if let Some(program) = program() {
        drop(start(&program, std::process::id()));
    }
}

#[cfg(test)]
mod tests;
