//! `status` for the one-worker Parallels pool. Unix only, as the Parallels backend is.

use std::sync::Arc;

use crate::worker::parallels::{Parallels, VmState as ParallelsState};
use crate::worker::worker::Manager;
use avl_base::format::words;
use avl_base::{Backend, Config, Outcome, Refusal};
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, read_init_receipt};
use avl_host_sys::share::shares;
use avl_host_sys::{Ctx, SpawnOptions};
use avl_wire::supervisor::RunState;
use serde::Serialize;

use super::{
    HostPaths, LeaseSummary, RunSlotFacts, Verdict, console_login_seen, host_paths_fragment, lease_summary, leased_word, verdict_word,
};
use avl_base::RefusalExt;

// --- the parallels status ------------------------------------------------------------------------------------

/// The single Parallels worker's row, whose field order is the JSON order.
///
/// A shape of its own rather than the Tart row with holes, because most of the Tart facts do not exist here - no run
/// process, no provenance, no root disk this controller sized - and a row of nulls would read as a worker failing
/// checks that cannot apply to it.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
#[expect(
    clippy::struct_excessive_bools,
    reason = "a status row: each bool is one independent JSON field the report prints"
)]
struct ParallelsWorkerStatus {
    worker: String,
    exists: bool,
    info_error: Option<String>,
    state: String,
    guest_agent: bool,
    /// Needs no guest-OS guard, unlike the Tart row's: the settings refuse Parallels with anything but a macOS guest,
    /// so the console session is a question every worker reachable here can answer.
    console_login: bool,
    initialized: bool,
    shares_configured: bool,
    parity_ready: Option<bool>,
    parity_error: Option<String>,
    host_repo: Option<String>,
    host_bazel_user_root: String,
    /// Beside `hostRepo` for the reason the Tart report carries it once: here the pool is one worker, so the row
    /// *is* the report.
    host_paths_error: Option<String>,
    bazel_share: String,
    active_run: Option<RunState>,
    run_error: Option<String>,
    lease: Option<LeaseSummary>,
}

#[derive(Debug, Serialize)]
struct ParallelsStatusData {
    backend: Backend,
    workers: Vec<ParallelsWorkerStatus>,
}

/// `status` for the one-worker Parallels pool.
pub(super) async fn status(ctx: &Ctx, manager: &Manager, parallels: &Parallels) -> Result<Outcome, Refusal> {
    let settings: &Arc<Config> = manager.settings();
    let Some(worker) = settings.workers.first() else {
        return Err(Refusal::internal("the Parallels pool names no worker"));
    };
    let (info, info_error) = match parallels.require_info(ctx, worker).await {
        Ok(info) => (Some(info), None),
        Err(refusal) => (None, Some(refusal.code.into_owned())),
    };
    let host_paths = HostPaths::resolve(ctx, manager.runner(), settings).await;

    // Tart's list metadata is authoritative there; here the equivalent double check is prlctl's own state backed by
    // `prlctl status`. A probe that cannot even spawn counts as "not running" rather than failing the report.
    let running = info.as_ref().is_some_and(|info| info.state == Some(ParallelsState::Running))
        || parallels.running(ctx, worker).await.unwrap_or(false);
    let channel = manager.channel(worker);
    let guest = Guest {
        ctx,
        settings,
        channel: channel.as_ref(),
        reporter: manager.reporter(),
    };
    let guest_agent = running && guest.succeeds(&words(["/usr/bin/true"]), GUEST_COMMAND_TIMEOUT).await;
    let lease = lease_summary(settings, worker)?;

    let shares_configured = match (&info, &host_paths.error) {
        (Some(info), None) => shares(settings).is_ok_and(|shares| parallels.require_shares_configured(worker, info, &shares).is_ok()),
        _ => false,
    };
    let initialized = read_init_receipt(settings, worker).is_ok();

    let (console_login, parity, run) = if guest_agent {
        let who = guest
            .raw(&words(["/usr/bin/who"]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        let console_login = console_login_seen(&who.stdout);
        let parity = if shares_configured && initialized {
            Verdict::of(guest.ensure_parity_ready().await)
        } else {
            Verdict::default()
        };
        (console_login, parity, RunSlotFacts::read(&guest).await)
    } else {
        (false, Verdict::default(), RunSlotFacts::default())
    };

    let state = match &info {
        None => "missing".to_owned(),
        Some(_) if guest_agent => "running".to_owned(),
        Some(_) if running => "unresponsive".to_owned(),
        Some(info) => info
            .state
            .as_ref()
            .map_or_else(|| "stopped".to_owned(), |state| state.as_str().to_owned()),
    };
    let text = format!(
        "{worker}: {state} lease={} console={} shares={} parity={}{}",
        leased_word(lease.as_ref()),
        if console_login { "yes" } else { "no" },
        if shares_configured { "configured" } else { "not-configured" },
        verdict_word(parity.ready, parity.error.as_deref()),
        host_paths_fragment(host_paths.error.as_deref()),
    );
    let row = ParallelsWorkerStatus {
        worker: worker.clone(),
        exists: info.is_some(),
        info_error,
        state,
        guest_agent,
        console_login,
        initialized,
        shares_configured,
        parity_ready: parity.ready,
        parity_error: parity.error,
        host_repo: host_paths.repo(),
        host_bazel_user_root: host_paths.bazel_user_root,
        host_paths_error: host_paths.error,
        bazel_share: settings.bazel_share_name.clone(),
        active_run: run.active,
        run_error: run.error,
        lease,
    };
    Outcome::new(
        ParallelsStatusData {
            backend: settings.backend,
            workers: vec![row],
        },
        text,
    )
}
