//! The lane IDE: the layout of one context, one launch on it, and the collection of the IDE runs under an IDE root.
//!
//! The lane daemon decides what to launch and when, and it calls [`prepare::prepare`] and [`launch::launch`] in its own
//! process. This module owns the files of a context, and the run supervisor owns the process. A launch starts the IDE
//! as the run `run-ide-<launchName>` with the context as the run slot, so a preparation or a launch never lands beside
//! a live IDE.
//!
//! The documents and their checks are declared in `avl_wire::ide`. A preparation and a launch check their document
//! first. Each refusal has exit 70. A preparation refuses [`CODE_PREPARE_FAILED`], a launch [`CODE_LAUNCH_FAILED`],
//! and `ide-gc` `guest_ide_gc_failed`. The two codes that a caller acts on are the exceptions: `ide_running` and
//! `ide_display_missing`.

pub(crate) mod gc;
pub(crate) mod launch;
pub(crate) mod prepare;
pub(crate) mod reap;

#[cfg(test)]
mod fixture;

use std::fs;
use std::os::unix::fs::DirBuilderExt;
use std::path::Path;

use avl_wire::ide::{self as wire_ide, ARG_FILE, CONFIG_DIR, LAUNCH_RECORD_FILE, PLUGINS_DIR, SYSTEM_DIR};
use avl_wire::supervisor::{Phase, RunState};

use crate::cli::RootArgs;
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::{self, System};

/// The refusal of a preparation or a launch for a context whose run slot holds a live IDE.
pub(crate) const CODE_IDE_RUNNING: &str = "ide_running";

/// The refusal of a preparation that cannot lay out its context.
pub(crate) const CODE_PREPARE_FAILED: &str = "guest_ide_prepare_failed";

/// The refusal of a launch that cannot start its IDE.
pub(crate) const CODE_LAUNCH_FAILED: &str = "guest_ide_launch_failed";

/// The paths of one context that a preparation and a launch both name, as guest path text.
struct ContextPaths {
    dir: String,
    config: String,
    system: String,
    plugins: String,
    arg_file: String,
    launch_record: String,
}

impl ContextPaths {
    /// The paths of the context `launch_key` under the IDE root `ide_root`.
    fn new(ide_root: &str, launch_key: &str) -> Self {
        let dir = wire_ide::context_dir(ide_root, launch_key);
        let child = |name: &str| format!("{dir}/{name}");
        Self {
            config: child(CONFIG_DIR),
            system: child(SYSTEM_DIR),
            plugins: child(PLUGINS_DIR),
            arg_file: child(ARG_FILE),
            launch_record: child(LAUNCH_RECORD_FILE),
            dir,
        }
    }

    /// The path of the entry `name` of the context.
    fn child(&self, name: &str) -> String {
        format!("{}/{name}", self.dir)
    }
}

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

/// Creates `path` and its missing parents, each one readable only by this account. A failure is a refusal with `code`.
pub(crate) fn create_private_dirs(code: &'static str, path: &Path) -> Result<(), AgentRefusal> {
    fs::DirBuilder::new()
        .recursive(true)
        .mode(0o700)
        .create(path)
        .map_err(|error| AgentRefusal::refused(code, format!("cannot create {}: {error}", path.display())))
}
