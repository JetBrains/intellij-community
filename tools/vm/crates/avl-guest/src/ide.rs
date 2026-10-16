//! The lane IDE: the layout of one context, one launch on it, and the collection of the IDE runs under an IDE root.
//!
//! The UI daemon decides what to launch and when. This module owns the files of a context, and the run supervisor
//! owns the process: `ide-launch` starts the IDE as the run `run-ide-<launchName>` with the context as the run slot,
//! so a preparation or a launch never lands beside a live IDE.
//!
//! The documents are declared in `avl_wire::ide`. Every refusal is `guest_<verb>_failed` with exit 70, except the
//! two that a caller acts on: `ide_running` and `ide_display_missing`.

pub(crate) mod gc;
pub(crate) mod launch;
pub(crate) mod prepare;
pub(crate) mod reap;

#[cfg(test)]
mod fixture;

use std::fs;
use std::os::unix::fs::DirBuilderExt;
use std::path::Path;

use avl_wire::supervisor::{Phase, RunState};
use avl_wire::verb::AgentVerb;

use crate::cli::RootArgs;
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::{self, System};

/// The refusal of a preparation or a launch for a context whose run slot holds a live IDE.
pub(crate) const CODE_IDE_RUNNING: &str = "ide_running";

/// The live run of the run slot `context`, or `None` when the slot is free or the context does not exist.
///
/// A run that the reconcile finishes now is not live. An unreadable slot is a refusal: a caller that read it as free
/// would land a launch beside a live IDE.
pub(crate) fn live_run(system: &dyn System, context: &Path) -> Result<Option<RunState>, AgentRefusal> {
    if !context.join("active.json").exists() {
        return Ok(None);
    }
    let reply = supervisor::active(
        system,
        &RootArgs {
            root: context.to_path_buf(),
        },
    )?;
    Ok(reply.active.filter(|state| state.phase != Phase::Finished))
}

/// Refuses with [`CODE_IDE_RUNNING`] when the run slot `context` holds a live IDE.
pub(crate) fn require_free_slot(system: &dyn System, context: &Path) -> Result<(), AgentRefusal> {
    match live_run(system, context)? {
        None => Ok(()),
        Some(run) => Err(AgentRefusal::refused(
            CODE_IDE_RUNNING,
            format!(
                "the context {} holds the live IDE run {}; quit or cancel it before a new launch",
                context.display(),
                run.run_id
            ),
        )),
    }
}

/// Creates `path` and its missing parents, each one readable only by this account.
pub(crate) fn create_private_dirs(verb: AgentVerb, path: &Path) -> Result<(), AgentRefusal> {
    fs::DirBuilder::new()
        .recursive(true)
        .mode(0o700)
        .create(path)
        .map_err(|error| AgentRefusal::for_verb(verb, format!("cannot create {}: {error}", path.display())))
}
