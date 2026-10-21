//! The launch of the detached supervisor: one spawn point for every guest.
//!
//! `start` spawns the `supervise` verb and then leaves. The supervisor spawns the run's child, which is the daemon
//! JVM, and the IDE comes up under that. So this one spawn decides what the whole tree belongs to.
//!
//! # Why the released spawn survives
//!
//! The supervisor leaves the session of the exec channel, and this process does not wait for it. On a macOS guest
//! `launchctl asuser` puts the agent in the session of the console user, and no unit holds what it starts. A Docker
//! worker runs no systemd: its pid 1 is `docker-init`, and the exec channel is `docker exec`. When the exec ends,
//! `docker-init` adopts the detached supervisor, so the run outlives the verb that started it.
//!
//! # Who reaps an ended supervisor
//!
//! A verb that ends after the spawn leaves its supervisor to the init process, which reaps it. A process that keeps
//! running, `lane-run` and the lane daemon, is the parent of every supervisor that it spawns, so it reaps each one
//! that ended with [`reap_ended`]. An ended supervisor that nobody reaps stays a zombie. A zombie still answers
//! `ps -p` and a signal 0, so the identity check reads it as a live supervisor.

use std::ffi::OsString;
use std::fs::File;
use std::io;
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::sync::{Mutex, PoisonError};

use avl_wire::supervisor::Command as Verb;

use crate::reply::{AgentRefusal, AgentRefusalExt};

/// The exec channel's own variables. Nothing under the channel gets them: a supervised child has no use for the
/// worker's capability.
pub(crate) const CHANNEL_VARIABLES: [&str; 3] = ["TART_VM_TOKEN", "TART_VM_WORKER", "PARALLELS_VM_TOKEN"];

/// The supervisors that this process spawned and did not wait for. [`reap_ended`] reaps each one that ended.
static SPAWNED: Mutex<Vec<Child>> = Mutex::new(Vec::new());

/// Reaps each supervisor that this process spawned and that ended, and answers their pids. It waits for no
/// supervisor that still runs.
pub(crate) fn reap_ended() -> Vec<u32> {
    let mut spawned = SPAWNED.lock().unwrap_or_else(PoisonError::into_inner);
    let mut reaped = Vec::new();
    spawned.retain_mut(|child| {
        // A supervisor that still runs stays. One that ended is reaped now, and so is one that cannot be waited for,
        // which nothing can reap later either.
        let runs = matches!(child.try_wait(), Ok(None));
        if !runs {
            reaped.push(child.id());
        }
        runs
    });
    reaped
}

/// The guest OS the supervisor runs on, which sets the PATH and the home of a supervised child. A runtime value, so
/// both are built and tested on whatever host runs the tests.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum LaunchHost {
    /// A Linux guest, which is a Docker container.
    Linux,
    /// A macOS guest.
    Macos,
}

impl LaunchHost {
    /// The word the supervisor log names this host with.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Linux => "linux",
            Self::Macos => "macos",
        }
    }

    pub(crate) const fn current() -> Self {
        if cfg!(target_os = "linux") { Self::Linux } else { Self::Macos }
    }
}

/// How a guest starts the detached supervisor: the `supervise` verb of this binary, released and not waited for.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SuperviseLaunch {
    pub argv: Vec<OsString>,
}

/// Composes the launch of one run's supervisor. Pure: every input is an argument.
pub(crate) fn supervise_launch_for(self_exe: &Path, root: &Path, run_id: &str) -> SuperviseLaunch {
    SuperviseLaunch {
        argv: vec![
            self_exe.into(),
            Verb::Supervise.as_str().into(),
            "--root".into(),
            root.into(),
            "--run".into(),
            run_id.into(),
        ],
    }
}

/// Makes `command` the leader of a new session, and so of a new process group whose id is its pid. A session
/// rather than just a group: it also detaches from the exec channel's terminal. The group is what lets a cancel
/// reach a child's children, and its id is half of the identity recorded for the run.
pub(crate) fn detach_into_own_session(command: &mut Command) {
    // SAFETY: the closure runs between fork and exec, and calls only setsid(2), which is async-signal-safe and
    // touches no memory of the parent.
    unsafe {
        command.pre_exec(|| nix::unistd::setsid().map(drop).map_err(io::Error::from));
    }
}

impl SuperviseLaunch {
    /// Spawns the supervisor in a session of its own, with `log` as both of its streams, and releases it: this process
    /// does not wait for it, and [`reap_ended`] reaps it after it ended.
    pub(super) fn start(&self, log: &File) -> Result<(), AgentRefusal> {
        let Some((program, arguments)) = self.argv.split_first() else {
            return Err(AgentRefusal::internal("the supervisor launch is empty"));
        };
        let mut command = Command::new(program);
        command
            .args(arguments)
            .stdin(Stdio::null())
            .stdout(log.try_clone().map_err(AgentRefusal::internal)?)
            .stderr(log.try_clone().map_err(AgentRefusal::internal)?);
        detach_into_own_session(&mut command);
        // Released on purpose: the supervisor outlives a verb that only reports that it began. The `Child` stays here, so
        // a process that keeps running can reap it.
        let child = command.spawn().map_err(AgentRefusal::internal)?;
        SPAWNED.lock().unwrap_or_else(PoisonError::into_inner).push(child);
        Ok(())
    }
}
