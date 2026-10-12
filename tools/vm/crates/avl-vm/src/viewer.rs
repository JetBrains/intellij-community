//! The trace viewer a run that a person reads links to.

use std::path::Path;

use avl_base::config::Presentation;
use avl_base::{Environment, Reporter};
use avl_host_sys::Runner;
use avl_host_sys::viewer::{DetachRequest, PROBE_TIMEOUT, SERVE_SCRIPT, Started, probe_viewer, serve_command, start_detached};
use avl_trace_tools::viewer::DEFAULT_SERVE_PORT;

#[cfg(test)]
mod tests;

/// Makes the trace viewer answer on this machine for a run that a person reads, and tells the reporter where it
/// is, so the run's first line and each trace line are links.
///
/// Only for prose: a caller that reads JSON is an agent or a script, and a server it did not ask for is a process
/// nobody stops. A viewer that already answers is used as it is. Otherwise `trace.cmd serve` is started through
/// [start_detached], the start `serve --detach` makes, with the arguments it would pass, and not awaited: the
/// wrapper's `bazel run` can take longer than the run's own build, and the links stay valid, because a bundle's id
/// depends only on where its zip is. The server stops itself after 30 minutes with no request and no open event
/// stream. `AIR_VM_VIEWER=off` ([`Presentation::viewer`]) starts nothing.
pub(crate) async fn ensure_viewer(
    runtime_root: &Path,
    presentation: &Presentation,
    environment: &Environment,
    runner: &Runner,
    reporter: &Reporter,
) {
    if !presentation.viewer {
        return;
    }
    let port = DEFAULT_SERVE_PORT;
    let probed = tokio::task::spawn_blocking(move || probe_viewer(port, PROBE_TIMEOUT)).await;
    if probe_answer(probed, reporter) {
        reporter.set_viewer(port, "");
        return;
    }
    // The checkout the wrapper ran from, where `trace.cmd` is. The configured host repository is not resolved yet
    // at this point of a run.
    let Some(checkout) = environment.get("BUILD_WORKSPACE_DIRECTORY") else {
        reporter.note(
            format!("cannot start the trace viewer: BUILD_WORKSPACE_DIRECTORY is not set; start it with ./{SERVE_SCRIPT} serve"),
            None,
        );
        return;
    };
    let checkout = Path::new(checkout);
    let started = start_detached(
        runner,
        &serve_command(checkout),
        Some(checkout),
        &DetachRequest::for_port(port),
        runtime_root,
    );
    let Started { mut child, log_path } = match started {
        Ok(started) => started,
        Err(error) => {
            reporter.note(
                format!("cannot start the trace viewer: {error}; start it with ./{SERVE_SCRIPT} serve"),
                None,
            );
            return;
        }
    };
    // Reaped if it ends while the run still goes on, so a long run leaves no zombie behind. A reaper that cannot
    // start leaves one until the run ends, which is all it costs.
    let _ = std::thread::Builder::new()
        .name("avl-viewer-reaper".to_owned())
        .spawn(move || child.wait());
    reporter.set_viewer(port, format!("the viewer is starting; its log is {}", log_path.display()));
}

/// Whether the probe found a viewer. A probe task that did not finish, a panic for one, is noted, and the run then
/// starts a viewer as if none answered.
///
/// A note and not a refusal, because [`ensure_viewer`] never refuses: the viewer serves a person who reads the run,
/// and a run is not red for want of one.
fn probe_answer(probed: Result<bool, tokio::task::JoinError>, reporter: &Reporter) -> bool {
    probed.unwrap_or_else(|error| {
        reporter.note(
            format!("the trace viewer probe failed: {error}; a viewer is started as if none answered"),
            None,
        );
        false
    })
}
