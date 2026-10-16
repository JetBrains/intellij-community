//! The lane IDEs that the guest agent runs beside the daemon: where their contexts are, the stop of the IDEs that a
//! worker must not keep, and the check that no IDE runs from the shares.
//!
//! The daemon decides when an IDE starts, and the guest agent owns the process. Each IDE is a supervisor run in the
//! slot root of its context, `<vm_data>/ide/<launchKey>`. A cancel of the daemon run does not stop it. So the
//! controller stops the IDEs itself where the daemon cannot: at `daemon stop`, at a lease release, at a pool recycle
//! and at a daemon start.

use std::time::Duration;

use avl_base::format::words;
use avl_base::{Config, Refusal};
use avl_host_sys::SpawnOptions;
use avl_host_sys::guest::{AgentAccount, GUEST_COMMAND_TIMEOUT, Guest, SupervisorOptions, guest_join};
use avl_wire::ide::IdeGcResult;
use avl_wire::supervisor::RunState;
use avl_wire::verb::AgentVerb;

/// The timeout of `ide-gc`. Each stop is a supervisor cancel with its grace of 10 s, and a worker holds one IDE per
/// launch key.
const IDE_GC_TIMEOUT: Duration = Duration::from_mins(2);
/// The timeout of one `active` read of the slot root of an IDE context: one exec round trip and a file read.
const IDE_ACTIVE_TIMEOUT: Duration = Duration::from_secs(30);

/// The guest directory that holds one context per launch key. Each context is the slot root of its IDE run.
pub(crate) fn guest_ide_root(settings: &Config) -> String {
    guest_join(&settings.vm_data, "ide")
}

/// Which IDEs one `ide-gc` keeps.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum IdeRetention<'a> {
    /// Stops every IDE of the worker.
    StopAll,
    /// Keeps each IDE whose launch record names this product digest, and stops the others.
    KeepProduct(&'a str),
}

/// The note of a gc that stopped, kept, killed or abandoned an IDE, or `None` when the worker had no live IDE and no
/// abandoned one.
pub(crate) fn gc_note(result: &IdeGcResult) -> Option<String> {
    let mut notes: Vec<String> = Vec::new();
    match (result.kept.len(), result.stopped.len()) {
        (0, 0) => {}
        (kept, 0) => notes.push(format!("kept {kept} lane IDE(s) of this product")),
        (0, stopped) => notes.push(format!("stopped {stopped} lane IDE(s)")),
        (kept, stopped) => notes.push(format!("kept {kept} lane IDE(s) of this product and stopped {stopped}")),
    }
    if !result.killed.is_empty() {
        notes.push(format!(
            "killed {} lane IDE(s) whose cancel did not end in time",
            result.killed.len()
        ));
    }
    if !result.abandoned.is_empty() {
        let runs: Vec<&str> = result.abandoned.iter().map(|run| run.run_id.as_str()).collect();
        notes.push(format!(
            "the processes of the lane IDE run(s) {} outlived a kill; their slots are free, and `pool recycle` discards them",
            runs.join(", ")
        ));
    }
    (!notes.is_empty()).then(|| notes.join("; "))
}

/// Stops the lane IDEs of the guest that `retention` does not keep, through the guest agent's `ide-gc`.
///
/// It runs as the worker account, because that account owns the IDE runs and their contexts. A refusal of the verb is
/// the caller's refusal: an IDE that survives the stop holds the shares and the run slot of its context. The
/// controller counts the lists of the answer for its notes and its timing line, and it decides no stop from them. So
/// an answer that it cannot read counts as an answer that stopped and kept nothing.
pub(crate) async fn gc_guest_ides(guest: &Guest<'_>, retention: IdeRetention<'_>) -> Result<IdeGcResult, Refusal> {
    let root = guest_ide_root(guest.settings);
    let mut args = words(["--root", &root]);
    match retention {
        IdeRetention::StopAll => args.push("--stop-all".to_owned()),
        IdeRetention::KeepProduct(digest) => args.extend(words(["--keep-product", digest])),
    }
    let stdout = guest
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::IdeGc,
            &args,
            &SpawnOptions::timeout(IDE_GC_TIMEOUT, "ide_gc_timeout"),
        )
        .await?;
    Ok(serde_json::from_str(stdout.trim()).unwrap_or_default())
}

/// The IDE runs that are not finished, with the slot root of each: an empty list when the guest has no IDE context.
///
/// Each context directory under [`guest_ide_root`] is read with the supervisor's `active`, which answers no run for a
/// slot whose run finished. A context that the supervisor cannot read is the caller's refusal, so a caller that must
/// fail closed does.
pub(crate) async fn running_guest_ides(guest: &Guest<'_>) -> Result<Vec<(String, RunState)>, Refusal> {
    let root = guest_ide_root(guest.settings);
    let listed = guest
        .as_user(
            &words([
                "/bin/sh",
                "-c",
                "test -d \"$1\" || exit 0; exec /usr/bin/find \"$1\" -mindepth 1 -maxdepth 1 -type d",
                "sh",
                &root,
            ]),
            &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
        )
        .await?;
    let mut running = Vec::new();
    for context in listed.stdout.lines().map(str::trim).filter(|line| !line.is_empty()) {
        let active = guest
            .supervisor_active_reply(
                context,
                SupervisorOptions {
                    aqua: false,
                    timeout: IDE_ACTIVE_TIMEOUT,
                },
            )
            .await?;
        if let Some(state) = active {
            running.push((context.to_owned(), state));
        }
    }
    Ok(running)
}

// The settings of the tests are Tart settings, which a Windows host does not have.
#[cfg(test)]
#[cfg(unix)]
mod tests;
