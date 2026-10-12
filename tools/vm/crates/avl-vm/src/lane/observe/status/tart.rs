//! `status` for a Tart pool: the strict `tart list`, one row per worker, and the probes of a running guest. Unix
//! only, as the Tart backend is.

use std::collections::{BTreeMap, HashMap};

use crate::worker::tart::{TART_QUERY_TIMEOUT, Tart, VmState};
use crate::worker::worker::Manager;
use avl_base::format::words;
use avl_base::{Backend, Exit, OrRefuse, Outcome, Refusal};
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, parse_ssh_host_key_fingerprint};
use avl_host_sys::{Channel, Ctx, SpawnOptions};
use avl_wire::supervisor::RunState;
use futures::future::join_all;
use serde::{Deserialize, Serialize};

use super::{
    HostPaths, Layout, LeaseSummary, RunSlotFacts, Verdict, console_login_seen, lease_summary, leased_word, pool_report, tri_state_word,
    verdict_word,
};
use avl_base::RefusalExt;

// --- the strict tart listing ---------------------------------------------------------------------------------

/// One row of `tart list --source local --format json`, validated strictly: every key present and of its type.
///
/// Strict where the Tart backend's own listing reader is permissive, and deliberately so: that reader answers narrow
/// questions ("does this name exist", "how big is its disk") where an absent field only narrows the answer, while
/// `status` republishes the metadata as facts an operator acts on - `Running` in particular decides whether the
/// guest agent is even probed - and a listing this controller half-understands must refuse rather than report a
/// worker in a state nobody stated.
#[derive(Clone, Debug, Deserialize)]
struct TartListVm {
    #[serde(rename = "Name")]
    name: String,
    #[serde(rename = "Disk")]
    disk: f64,
    #[serde(rename = "Running")]
    running: bool,
    #[serde(rename = "State")]
    state: String,
}

fn parse_tart_list(stdout: &str) -> Result<Vec<TartListVm>, Refusal> {
    serde_json::from_str(stdout).or_refuse("invalid_tart_list", Exit::FAILURE, || {
        "Tart returned invalid local VM metadata".to_owned()
    })
}

// --- the tart status -----------------------------------------------------------------------------------------

/// One Tart worker's row, whose field order is the JSON order, with `parityError` and the two facts only the whole
/// pool can decide.
///
/// A verdict is an `Option<bool>` when the question may not be asked, and it has an `Option<String>` beside it when
/// the answer can carry a reason worth reading - `provenanceReady`/`provenanceError`, `tccClean`/`tccError`,
/// `parityReady`/`parityError`. `consoleLogin`, `sshHostKeyReady` and `sshHostKeyUnique` are tri-state with no reason
/// field, because the only thing to say about them beyond the verdict is whether they were asked at all.
/// `workerStorageReady` and `rootDiskReady` stay plain bools because their question always applies: the first is
/// one `test -d`, the second is arithmetic on a disk size tart already reported, and `state` on the same row already
/// says whether anything was probed.
///
/// One asymmetry that rule does not explain, and it is deliberate: on a *stopped* macOS worker `consoleLogin`,
/// `tccClean` and `parityReady` are null while `sshHostKeyReady` is `false`. The fingerprint was not asked for
/// either, but its verdict is computed from the guest OS alone rather than from what the guest answered.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
#[expect(
    clippy::struct_excessive_bools,
    reason = "a status row: each bool is one independent JSON field the report prints"
)]
struct TartWorkerStatus {
    worker: String,
    exists: bool,
    /// Null on a Linux guest: a Linux worker is cloned from a public image and provisioned from scratch on every
    /// boot, so it has no provenance receipt by design, and reporting its absence as an error made every Linux
    /// `status` read like a broken worker.
    provenance_ready: Option<bool>,
    provenance_error: Option<String>,
    state: &'static str,
    pid: Option<i32>,
    guest_agent: bool,
    /// Null on a Linux guest, which has no login session to have: its display is an Xvfb this controller starts,
    /// not a seat a user logs into.
    console_login: Option<bool>,
    worker_storage_ready: bool,
    root_disk_gb: Option<f64>,
    root_disk_expected_gb: u32,
    root_disk_ready: bool,
    /// Carries its refusal in `parityError`, because the commonest refusal is not a defect: `guest_init_stale` is
    /// what a pool shared between two checkouts reports, and the next run re-provisions the worker for this one.
    parity_ready: Option<bool>,
    parity_error: Option<String>,
    host_repo: Option<String>,
    /// Null on a Linux guest, which has no TCC at all.
    tcc_clean: Option<bool>,
    tcc_error: Option<String>,
    /// Null on a Linux guest: regenerating a host key is a Tart-plus-macOS step, where two workers are
    /// copy-on-write clones of one sealed image and start life sharing an SSH identity.
    ssh_host_key_fingerprint: Option<String>,
    active_run: Option<RunState>,
    run_error: Option<String>,
    lease: Option<LeaseSummary>,
    /// Decided in the second pass; null together with `sshHostKeyUnique` where the fingerprint question does not
    /// apply.
    ssh_host_key_ready: Option<bool>,
    ssh_host_key_unique: Option<bool>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct TartStatusData {
    backend: Backend,
    workers: Vec<TartWorkerStatus>,
    host_repo: String,
    host_bazel_user_root: String,
    /// The pool-wide fact the workers' rows cannot carry: resolving the two host paths is the host's own work, done
    /// once per command, and a refusal is one refusal - not the same string repeated on every row.
    host_paths_error: Option<String>,
}

/// `status` for a Tart pool.
pub(super) async fn status(ctx: &Ctx, manager: &Manager, tart: &Tart) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    tart.require_available(ctx, "").await?;
    // Before the fan-out, because the parity question below cannot be asked without the answer: it compares each
    // worker's receipt against these paths. `status` used to skip this, so every worker on every guest OS reported
    // the controller's own omission - `parity=not-ready(host_paths_unresolved)` - as the guest's verdict.
    let host_paths = HostPaths::resolve(ctx, manager.runner(), settings).await;
    // The executable the gate above resolved, which the settings do not carry.
    let Some(program) = manager.tart_executable() else {
        return Err(Refusal::internal("the Tart gate passed without resolving an executable"));
    };
    let listing = manager
        .runner()
        .checked(
            ctx,
            &[
                program.to_string_lossy().into_owned(),
                "list".to_owned(),
                "--source".to_owned(),
                "local".to_owned(),
                "--format".to_owned(),
                "json".to_owned(),
            ],
            &SpawnOptions::within(TART_QUERY_TIMEOUT),
        )
        .await?;
    let local: HashMap<String, TartListVm> = parse_tart_list(&listing.stdout)?
        .into_iter()
        .map(|vm| (vm.name.clone(), vm))
        .collect();

    // The fan-out: one probe per worker, concurrently, and the first refusal *by pool order* fails the whole
    // command.
    let probes = settings
        .workers
        .iter()
        .map(|worker| tart_worker_status(ctx, manager, tart, &local, worker, &host_paths));
    let mut workers = join_all(probes).await.into_iter().collect::<Result<Vec<_>, Refusal>>()?;

    // The second pass. `sshHostKeyUnique` is a per-worker field only decidable globally: a fingerprint is unique or
    // not against every *other* worker's, so a worker probed first would otherwise be reported unique against peers
    // that had not answered yet.
    let mut fingerprints: BTreeMap<String, usize> = BTreeMap::new();
    for row in &workers {
        if let Some(fingerprint) = &row.ssh_host_key_fingerprint {
            *fingerprints.entry(fingerprint.clone()).or_default() += 1;
        }
    }
    for row in &mut workers {
        // The field stays nullable, because the wire contract has a null for a guest that has no SSH host key.
        row.ssh_host_key_ready = Some(row.ssh_host_key_fingerprint.is_some());
        row.ssh_host_key_unique = row
            .ssh_host_key_fingerprint
            .as_ref()
            .map(|fingerprint| fingerprints.get(fingerprint) == Some(&1));
    }

    let text = pool_report(host_paths.error.as_deref(), workers.iter().map(render_tart_status_line));
    Outcome::new(
        TartStatusData {
            backend: settings.backend,
            workers,
            host_repo: host_paths.repo,
            host_bazel_user_root: host_paths.bazel_user_root,
            host_paths_error: host_paths.error,
        },
        text,
    )
}

/// Probes one Tart worker. Read-only throughout: `status` must be safe to run beside anything, so nothing here
/// boots, repairs or provisions.
async fn tart_worker_status(
    ctx: &Ctx,
    manager: &Manager,
    tart: &Tart,
    local: &HashMap<String, TartListVm>,
    worker: &str,
    host_paths: &HostPaths,
) -> Result<TartWorkerStatus, Refusal> {
    let settings = manager.settings();
    let identity = tart.read_process_identity(worker);
    let lease = lease_summary(settings, worker)?;
    let host_process = tart.process_alive(ctx, identity.as_ref()).await?;
    let vm = local.get(worker);

    let provenance = if vm.is_none() {
        Err("worker_vm_missing".to_owned())
    } else {
        tart.require_worker_provenance(worker, &settings.golden_vm)
            .map(drop)
            .map_err(|refusal| refusal.code.into_owned())
    };
    let provenance_ready = Some(provenance.is_ok());
    let provenance_error = provenance.err();

    // `tart exec` reports a stopped VM in stderr but exits zero. Tart's own list metadata is therefore the
    // authoritative precondition for probing the guest agent; an exit code alone would turn a stopped VM into a
    // false-positive running worker.
    let tart_running = vm.is_some_and(|vm| vm.running && vm.state != VmState::Stopped.as_str());
    let channel = manager.channel(worker);
    let guest = Guest {
        ctx,
        settings,
        channel: channel.as_ref(),
        reporter: manager.reporter(),
    };
    let guest_agent = tart_running && guest.succeeds(&words(["/usr/bin/true"]), GUEST_COMMAND_TIMEOUT).await;

    let probed = if guest_agent {
        probe_running_tart_guest(&guest, channel.as_ref(), host_paths).await?
    } else {
        Probed::default()
    };

    // The four-way state ladder. "suspended" is not "stopped": the worker is holding a multi-gigabyte saved guest
    // state, and the next start resumes into it rather than booting, so a reader deciding what a slot costs and how
    // it will come back needs the two told apart.
    let state = if guest_agent {
        "running"
    } else if host_process || tart_running {
        "unresponsive"
    } else if vm.is_some_and(|vm| vm.state == VmState::Suspended.as_str()) {
        "suspended"
    } else {
        "stopped"
    };
    Ok(TartWorkerStatus {
        worker: worker.to_owned(),
        exists: vm.is_some(),
        provenance_ready,
        provenance_error,
        state,
        pid: identity.filter(|_| host_process).map(|identity| identity.pid),
        guest_agent,
        console_login: probed.console_login,
        worker_storage_ready: probed.worker_storage_ready,
        root_disk_gb: vm.map(|vm| vm.disk),
        root_disk_expected_gb: settings.vm_root_disk_gb,
        // At least, not exactly: `tart set --disk-size` only grows, so a worker created when the default was 400 GB
        // keeps that disk and is perfectly usable against today's smaller expectation.
        root_disk_ready: vm.is_some_and(|vm| vm.disk >= f64::from(settings.vm_root_disk_gb)),
        parity_ready: probed.parity.ready,
        parity_error: probed.parity.error,
        host_repo: host_paths.repo(),
        tcc_clean: probed.tcc.ready,
        tcc_error: probed.tcc.error,
        ssh_host_key_fingerprint: probed.ssh_host_key_fingerprint,
        active_run: probed.run.active,
        run_error: probed.run.error,
        lease,
        ssh_host_key_ready: None,
        ssh_host_key_unique: None,
    })
}

/// What a running Tart guest answered.
#[derive(Debug, Default)]
struct Probed {
    console_login: Option<bool>,
    worker_storage_ready: bool,
    parity: Verdict,
    tcc: Verdict,
    ssh_host_key_fingerprint: Option<String>,
    run: RunSlotFacts,
}

async fn probe_running_tart_guest(guest: &Guest<'_>, channel: &dyn Channel, host_paths: &HostPaths) -> Result<Probed, Refusal> {
    let settings = guest.settings;
    // The macOS probes. A Tart worker is a macOS guest, so each one applies.
    let tcc = Verdict::of(guest.require_clean_worker_tcc().await);
    let who = channel
        .exec(guest.ctx, &words(["/usr/bin/who"]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
        .await?;
    let layout = Layout::probe(guest, host_paths).await;
    let mut probed = Probed {
        console_login: Some(who.exit_code == 0 && console_login_seen(&who.stdout)),
        worker_storage_ready: layout.worker_storage_ready,
        parity: layout.parity,
        tcc,
        ..Probed::default()
    };
    if probed.worker_storage_ready {
        // Read as root with no `-H`, the argv the guest's sudoers rule is written for - the same spelling as the peer
        // read in the SSH host-key check, kept rather than normalised.
        let fingerprint = channel
            .exec(
                guest.ctx,
                &words(["/usr/bin/sudo", "/bin/cat", &settings.vm_ssh_host_key_fingerprint]),
                &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
            )
            .await?;
        if fingerprint.exit_code == 0 {
            probed.ssh_host_key_fingerprint = parse_ssh_host_key_fingerprint(guest.worker(), &fingerprint.stdout).ok();
        }
    }
    probed.run = RunSlotFacts::read(guest).await;
    Ok(probed)
}

fn render_tart_status_line(row: &TartWorkerStatus) -> String {
    let pid = row.pid.map(|pid| format!(" pid={pid}")).unwrap_or_default();
    let root_disk = row.root_disk_gb.map_or_else(|| "missing".to_owned(), |disk| disk.to_string());
    // A fingerprint that exists is a collision until the second pass says it is unique, and `n/a` where the question
    // does not apply - `sshHostKeyUnique` can only be set where a fingerprint was read at all.
    let ssh_host_key = if row.ssh_host_key_unique == Some(true) {
        "unique"
    } else {
        tri_state_word(row.ssh_host_key_ready, "collision", "not-ready")
    };
    format!(
        "{}: {}{pid} lease={} root_disk={root_disk}/{}GB storage={} ssh_host_key={ssh_host_key} console={} parity={}",
        row.worker,
        row.state,
        leased_word(row.lease.as_ref()),
        row.root_disk_expected_gb,
        if row.worker_storage_ready { "ready" } else { "not-ready" },
        tri_state_word(row.console_login, "yes", "no"),
        verdict_word(row.parity_ready, row.parity_error.as_deref()),
    )
}
