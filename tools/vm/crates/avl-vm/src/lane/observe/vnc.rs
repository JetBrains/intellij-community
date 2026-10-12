//! `vnc`: the worker's host-side VNC endpoint, read out of the tart runtime log.

use std::path::Path;
use std::sync::LazyLock;

use crate::worker::hypervisor::unsupported;
use crate::worker::worker::Manager;
use avl_base::{Backend, Exit, Outcome, Refusal};
use avl_host_sys::Ctx;
use regex::Regex;
use serde::Serialize;

use super::{split_lines, with_leased_worker};

/// How much of the tart runtime log a `vnc` answer carries: enough to hold the endpoint line a recent boot printed,
/// and little enough that the envelope stays a message rather than a log dump.
pub(crate) const VNC_TAIL_LINES: usize = 40;

const DIAGNOSTIC: &str = "Tart host-side VNC does not require guest Screen Recording or Accessibility permissions.";

/// The endpoint `tart run --vnc-experimental` prints into its runtime log. Applied per line over lines split on
/// `\r?\n`, so `\S+` cannot swallow the `\r` of a CRLF log into the endpoint.
static VNC_ENDPOINT: LazyLock<Regex> = LazyLock::new(|| {
    // An invariant: a literal pattern, exercised by the tests.
    Regex::new(r"(?i)((?:vnc|rfb)://\S+)").expect("a literal pattern compiles")
});

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct VncData {
    worker: String,
    diagnostic: &'static str,
    endpoint: Option<String>,
    runtime_log: String,
    tail: String,
}

/// Answers the worker's host-side VNC endpoint.
///
/// Read, never probed: the endpoint is something `tart run` printed once at boot, so the answer is the last
/// [`VNC_TAIL_LINES`] lines of its log with the first endpoint-shaped token winning. Parallels is refused - its
/// diagnostics are the Parallels Desktop console, and this controller does not open GUI windows.
pub(crate) async fn command_vnc(ctx: &Ctx, manager: &Manager, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    if settings.backend == Backend::Parallels {
        return Err(unsupported(
            "Parallels diagnostics use the Parallels Desktop console; the controller does not open GUI windows",
        ));
    }
    if settings.backend == Backend::Docker {
        return Err(unsupported(
            "a Docker worker has no VNC endpoint: its display is an Xvfb inside the container, which no host port \
             exposes; a run's traces carry its screenshots",
        ));
    }
    let data = with_leased_worker(ctx, manager, lease_file, "vnc", async |current| {
        let worker = current.worker;
        if !manager.machine().running(ctx, &worker).await? {
            return Err(Refusal::new("worker_stopped", Exit::FAILURE, format!("worker {worker} is stopped")));
        }
        let path = settings.tart_log_path(&worker);
        // An unreadable log is an empty tail, not a refusal: the answer is still where the log would be.
        let content = std::fs::read(&path)
            .map(|content| String::from_utf8_lossy(&content).into_owned())
            .unwrap_or_default();
        let lines: Vec<&str> = if content.is_empty() {
            Vec::new()
        } else {
            split_lines(&content).collect()
        };
        let tail = &lines[lines.len().saturating_sub(VNC_TAIL_LINES)..];
        let endpoint = tail
            .iter()
            .find_map(|line| VNC_ENDPOINT.captures(line).map(|matched| matched[1].to_owned()));
        Ok(VncData {
            worker,
            diagnostic: DIAGNOSTIC,
            endpoint,
            runtime_log: path.to_string_lossy().into_owned(),
            tail: tail.join("\n"),
        })
    })
    .await?;
    let mut text = format!("{DIAGNOSTIC}\nRuntime log: {}", data.runtime_log);
    if let Some(endpoint) = &data.endpoint {
        text.push_str(&format!("\nEndpoint: {endpoint}"));
    }
    text.push('\n');
    text.push_str(&data.tail);
    Outcome::new(data, text)
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
