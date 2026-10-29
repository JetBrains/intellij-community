//! Ctrl+C of the person at the terminal, for a run.
//!
//! The handler only counts the presses; the client reads the count and acts on its own threads. The first Ctrl+C
//! before the start stops each local child, such as git, and nothing runs on the host. The first Ctrl+C after the
//! start, the build of the pack included, sends `Cancel` to the agent, stops the pack, and follows the output up to
//! its end. The second Ctrl+C stops the follow at once, and the run refuses with `interrupted`. The agent has the
//! cancel already, so the run on the host stops anyway. A press after the end of the run stops the base sync.

use std::sync::OnceLock;
use std::sync::atomic::{AtomicU32, Ordering};

static PRESSES: AtomicU32 = AtomicU32::new(0);

/// Installs the handler once per process. Answers why it cannot, and then Ctrl+C ends the process as without it.
pub fn install() -> Result<(), String> {
    static INSTALLED: OnceLock<Result<(), String>> = OnceLock::new();
    INSTALLED
        .get_or_init(|| {
            ctrlc::set_handler(|| {
                PRESSES.fetch_add(1, Ordering::SeqCst);
            })
            .map_err(|error| format!("cannot handle Ctrl+C: {error}"))
        })
        .clone()
}

/// How many times the person pressed Ctrl+C.
pub fn presses() -> u32 {
    PRESSES.load(Ordering::SeqCst)
}
