//! What the host knows about a process by its pid: the seam behind [`Runner::probe_process`].
//!
//! On Unix `/bin/ps` answers. On Windows the process object answers, through `OpenProcess`, so no subprocess runs. A
//! runner built [`Runner::with_process_table`] asks its [`ProcessTable`] instead about each pid that the table holds.

use avl_base::Refusal;

use super::Runner;
use crate::ctx::Ctx;

#[cfg(test)]
mod tests;

/// What a probe asks about a process.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PsField {
    /// When the process started. On Unix it is `lstart=` with whitespace runs collapsed: `ps` pads the day of the
    /// month to two columns ("Aug  9" against "Aug 19"), so a raw answer differs from its own recorded form for the
    /// first nine days of every month. On Windows it is the creation time of the process, in RFC 3339.
    StartTime,
    /// On Unix `command=`, uncollapsed: its spacing is part of what makes a command line distinguishing. On Windows
    /// it is the full path of the executable image, because Windows has no documented call that reads the command
    /// line of another process.
    Command,
    /// On Unix `stat=`, collapsed. On Windows it is `R` for a process that runs, and `Z` for a process that has
    /// ended while a handle still holds it, which is the zombie of Windows.
    State,
}

/// The processes a runner probes in place of the host's process table: what a suite gives
/// [`Runner::with_process_table`], so that no probe answer about a pid the table holds depends on the host.
pub trait ProcessTable: Send + Sync {
    /// Whether the table answers for `pid`. The host answers every pid that the table does not hold.
    fn holds(&self, pid: i32) -> bool;

    /// One field of the process `pid` that the table holds, or `None` when there is no such process.
    fn field(&self, pid: i32, field: PsField) -> Option<String>;
}

impl Runner {
    /// Answers one field of the process `pid`, or `None` when there is no such process. A probe that gives no answer is
    /// the refusal `probe_unanswered` ([`super::probe_unanswered`]), and not a dead process: a `ps` that a signal
    /// ended, or that printed nothing, says nothing about the process it was asked about. A dead process is the one
    /// answer that lets a caller delete the record of a live `tart run`.
    ///
    /// On Unix the probe is the one spawn an interrupt must not reach. After the first Ctrl-C the operation's
    /// token is cancelled and every newly registered group is signalled at once, so an ordinary spawn of `ps`
    /// would die before printing and a live `tart run` would read as dead - and the caller would delete the only
    /// record of it. So `ps` runs unregistered, under no cancellation, bounded by its own timeout; `ctx` only
    /// collects the phase time.
    ///
    /// `/bin/ps` absolutely, not `ps`: this runs with the controller's scrubbed environment and a PATH the operator
    /// chose, and the one thing worse than no answer here is an answer from something else.
    pub async fn probe_process(&self, ctx: &Ctx, pid: i32, field: PsField) -> Result<Option<String>, Refusal> {
        if pid <= 0 {
            return Ok(None);
        }
        if let Some(table) = &self.process_table
            && table.holds(pid)
        {
            return Ok(table.field(pid, field));
        }
        imp::probe(self, ctx, pid, field).await
    }
}

#[cfg(unix)]
use unix as imp;
#[cfg(windows)]
use windows as imp;

#[cfg(unix)]
mod unix {
    use std::sync::LazyLock;
    use std::time::{Duration, Instant};

    use avl_base::Refusal;
    use regex::Regex;

    use super::PsField;
    use crate::ctx::Ctx;
    use crate::proc::{Captured, Interruptible, ProbeExit, ProbeOutput, Runner, SpawnOptions, probe_unanswered};

    /// How long a liveness probe may take. `ps` answers in milliseconds; the bound only keeps a probe that no
    /// interrupt can stop from hanging a stop path.
    const PROBE_TIMEOUT: Duration = Duration::from_secs(10);

    const fn format(field: PsField) -> &'static str {
        match field {
            PsField::StartTime => "lstart=",
            PsField::Command => "command=",
            PsField::State => "stat=",
        }
    }

    const fn collapsed(field: PsField) -> bool {
        !matches!(field, PsField::Command)
    }

    pub(super) async fn probe(runner: &Runner, ctx: &Ctx, pid: i32, field: PsField) -> Result<Option<String>, Refusal> {
        let argv = [
            "/bin/ps".to_owned(),
            "-o".to_owned(),
            format(field).to_owned(),
            "-p".to_owned(),
            pid.to_string(),
        ];
        let started = Instant::now();
        let captured = runner
            .run_captured(
                &Ctx::background(),
                &argv,
                &SpawnOptions::timeout(PROBE_TIMEOUT, "subprocess_timeout"),
                Interruptible::No,
            )
            .await;
        ctx.phase().record_subprocess(&argv, started.elapsed());
        answer(&argv, field, &captured?)
    }

    /// What one `ps` capture says about the process: its field, `None` for no such process (`ps` exits 1), or
    /// `probe_unanswered` for an exit 0 with nothing printed, a `ps` that cannot run, and a `ps` that a signal ended.
    pub(super) fn answer(argv: &[String], field: PsField, captured: &Captured) -> Result<Option<String>, Refusal> {
        match captured.probe_exit() {
            ProbeExit::Answered => {}
            ProbeExit::Negative => return Ok(None),
            ProbeExit::CannotRun | ProbeExit::Killed => return Err(probe_unanswered(argv, captured, ProbeOutput::Quoted)),
        }
        let value = captured.stdout.trim();
        if value.is_empty() {
            return Err(probe_unanswered(argv, captured, ProbeOutput::Quoted));
        }
        Ok(Some(if collapsed(field) {
            static WHITESPACE_RUN: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").expect("a constant pattern compiles"));
            WHITESPACE_RUN.replace_all(value, " ").into_owned()
        } else {
            value.to_owned()
        }))
    }
}

#[cfg(windows)]
mod windows {
    use std::future::{Ready, ready};

    use avl_base::Refusal;
    use jiff::Timestamp;
    use windows_sys::Win32::Foundation::{CloseHandle, FILETIME, HANDLE, STILL_ACTIVE};
    use windows_sys::Win32::System::Threading::{
        GetExitCodeProcess, GetProcessTimes, OpenProcess, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION, QueryFullProcessImageNameW,
    };

    use super::PsField;
    use crate::ctx::Ctx;
    use crate::proc::Runner;

    /// The 100-nanosecond intervals from 1601-01-01, where a `FILETIME` counts from, to the Unix epoch.
    const UNIX_EPOCH_TICKS: u64 = 116_444_736_000_000_000;

    /// The longest path an image name can have, in UTF-16 units.
    const IMAGE_NAME_UNITS: usize = 32_768;

    /// No subprocess and no wait, so the answer is ready at once and nothing is recorded into the phase. No process
    /// object to open is no such process: there is no probe that could fail to answer.
    pub(super) fn probe(_runner: &Runner, _ctx: &Ctx, pid: i32, field: PsField) -> Ready<Result<Option<String>, Refusal>> {
        let answer = u32::try_from(pid).ok().and_then(Process::open).and_then(|process| match field {
            PsField::StartTime => process.start_time(),
            PsField::Command => process.image(),
            PsField::State => process.state(),
        });
        ready(Ok(answer))
    }

    /// An open handle to a process, with the least access that answers every field.
    struct Process(HANDLE);

    impl Process {
        fn open(pid: u32) -> Option<Self> {
            // SAFETY: plain values; a null handle is the failure, and nothing else is returned.
            let handle = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid) };
            (!handle.is_null()).then_some(Self(handle))
        }

        fn start_time(&self) -> Option<String> {
            let mut times = [FILETIME::default(); 4];
            let [created, exited, kernel, user] = &mut times;
            // SAFETY: the handle is open, and each pointer is a valid place for one FILETIME.
            let read = unsafe { GetProcessTimes(self.0, created, exited, kernel, user) };
            if read == 0 {
                return None;
            }
            let ticks = (u64::from(created.dwHighDateTime) << 32) | u64::from(created.dwLowDateTime);
            let nanoseconds = i128::from(ticks.checked_sub(UNIX_EPOCH_TICKS)?) * 100;
            Some(Timestamp::from_nanosecond(nanoseconds).ok()?.to_string())
        }

        fn image(&self) -> Option<String> {
            let mut name = vec![0_u16; IMAGE_NAME_UNITS];
            let mut units = u32::try_from(name.len()).ok()?;
            // SAFETY: the handle is open, and `units` is the capacity of `name`, which the call does not exceed.
            let read = unsafe { QueryFullProcessImageNameW(self.0, PROCESS_NAME_WIN32, name.as_mut_ptr(), &raw mut units) };
            let units = usize::try_from(units).ok()?;
            (read != 0 && units > 0).then(|| String::from_utf16_lossy(&name[..units]))
        }

        fn state(&self) -> Option<String> {
            let mut status = 0;
            // SAFETY: the handle is open, and `status` is a valid place for the exit code.
            let read = unsafe { GetExitCodeProcess(self.0, &raw mut status) };
            let running = status == STILL_ACTIVE.cast_unsigned();
            (read != 0).then(|| if running { "R" } else { "Z" }.to_owned())
        }
    }

    impl Drop for Process {
        fn drop(&mut self) {
            // SAFETY: the handle is open, and nothing uses it after this.
            let _ = unsafe { CloseHandle(self.0) };
        }
    }
}
