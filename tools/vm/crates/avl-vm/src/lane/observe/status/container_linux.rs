//! `status` for the one-worker container-linux pool.

use crate::worker::container_linux::ContainerLinux;
use crate::worker::worker::Manager;
use avl_base::{Backend, Outcome, Refusal};
use avl_host_sys::Ctx;
use serde::Serialize;

use super::{HostPaths, Layout, LeaseSummary, abandoned_fragment, lease_summary, leased_word, pool_report, tri_state_word, verdict_word};

/// The container-linux worker's row, whose field order is the JSON order.
///
/// A shape of its own, as the Docker row is: the container has no image this controller built, no engine, no
/// console session and no create record. It has one fact of its own, the noVNC page of its display.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ContainerLinuxWorkerStatus {
    worker: String,
    /// `running` or `stopped`, as the skill's script lists the container of this checkout. `unknown` where the host
    /// paths did not resolve, because the checkout it serves tells the container apart from the containers of other
    /// checkouts.
    state: &'static str,
    /// Asked only of a running container.
    guest_agent: bool,
    worker_storage_ready: bool,
    parity_ready: Option<bool>,
    parity_error: Option<String>,
    /// The noVNC page of the container's display, once the guest wrote it, and null after a stop.
    novnc: Option<String>,
    lease: Option<LeaseSummary>,
    /// The IDE runs whose processes outlived a kill, as on the Docker row.
    abandoned_ide_runs: Option<Vec<String>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ContainerLinuxStatusData {
    backend: Backend,
    workers: Vec<ContainerLinuxWorkerStatus>,
    host_repo: String,
    host_bazel_user_root: String,
    /// One host fact for the pool, as on the Tart report.
    host_paths_error: Option<String>,
}

/// `status` for the one-worker container-linux pool.
///
/// Read-only toward the container: the script's `list`, the files the guest wrote, and read-only probes through the
/// control port of a running container. It never starts or stops anything.
pub(super) async fn status(ctx: &Ctx, manager: &Manager, container_linux: &ContainerLinux) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    container_linux.require_available(ctx, "").await?;
    // Before the rows, for the reason the Tart report gives: the parity question needs these paths, and so does the
    // liveness question, which compares the checkout of each listed container with this one.
    let host_paths = HostPaths::resolve(ctx, manager.runner(), settings).await;
    let novnc = container_linux.novnc()?.map(|novnc| novnc.url);
    let mut workers = Vec::new();
    for worker in &settings.workers {
        let lease = lease_summary(settings, worker)?;
        let running = if host_paths.error.is_none() {
            Some(container_linux.running(ctx, worker).await?)
        } else {
            None
        };
        let (guest_agent, layout) = if running == Some(true) {
            Layout::probe_running(ctx, manager, worker, &host_paths).await
        } else {
            (false, Layout::default())
        };
        workers.push(ContainerLinuxWorkerStatus {
            worker: worker.clone(),
            state: tri_state_word(running, "running", "stopped"),
            guest_agent,
            worker_storage_ready: layout.worker_storage_ready,
            parity_ready: layout.parity.ready,
            parity_error: layout.parity.error,
            novnc: novnc.clone(),
            lease,
            abandoned_ide_runs: layout.abandoned_ide_runs,
        });
    }
    let text = pool_report(host_paths.error.as_deref(), workers.iter().map(render_line));
    Outcome::new(
        ContainerLinuxStatusData {
            backend: settings.backend,
            workers,
            host_repo: host_paths.repo,
            host_bazel_user_root: host_paths.bazel_user_root,
            host_paths_error: host_paths.error,
        },
        text,
    )
}

fn render_line(row: &ContainerLinuxWorkerStatus) -> String {
    format!(
        "{}: {} lease={} parity={} novnc={}{}",
        row.worker,
        row.state,
        leased_word(row.lease.as_ref()),
        verdict_word(row.parity_ready, row.parity_error.as_deref()),
        row.novnc.as_deref().unwrap_or("none"),
        abandoned_fragment(row.abandoned_ide_runs.as_deref()),
    )
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
