//! The host's process table without a host process: what a runner probes in a hermetic suite.

use std::sync::Mutex;

use avl_base::sync::lock;
use avl_host_sys::{ProcessTable, PsField};

/// The pid of the first declared process. It is above the pid limit of every host, so a signal to a fake pid reaches no
/// real process, and no host process has a pid of the table.
const FIRST_PID: i32 = 0x4000_0000;

/// The start time of every declared process. The pid tells two processes apart.
const START_TIME: &str = "Sun Aug 23 00:00:00 2026";

/// A [`ProcessTable`] of the processes a suite declares.
///
/// The default table is hermetic: it holds every pid, so every pid that the suite did not declare is no such process,
/// and that includes the pid of the suite itself. A table [`FakeProcesses::beside_the_host`] holds only the pids of the
/// table, and the host answers every other pid.
#[derive(Default)]
pub struct FakeProcesses {
    commands: Mutex<Vec<String>>,
    /// Whether the host answers each pid below [`FIRST_PID`].
    beside_the_host: bool,
}

impl FakeProcesses {
    /// A table for a suite that also probes real child processes, such as a `tart run` of the fake hypervisor.
    pub fn beside_the_host() -> Self {
        Self {
            beside_the_host: true,
            ..Self::default()
        }
    }

    /// Declares a live process that runs `command`, and answers its pid.
    pub fn start(&self, command: &str) -> i32 {
        let mut commands = lock(&self.commands);
        commands.push(command.to_owned());
        FIRST_PID + i32::try_from(commands.len() - 1).expect("a suite declares fewer than 2^31 processes")
    }
}

impl ProcessTable for FakeProcesses {
    fn holds(&self, pid: i32) -> bool {
        !self.beside_the_host || pid >= FIRST_PID
    }

    fn field(&self, pid: i32, field: PsField) -> Option<String> {
        let index = usize::try_from(pid.checked_sub(FIRST_PID)?).ok()?;
        let command = lock(&self.commands).get(index)?.clone();
        Some(match field {
            PsField::StartTime => START_TIME.to_owned(),
            PsField::Command => command,
            PsField::State => "S".to_owned(),
        })
    }
}
