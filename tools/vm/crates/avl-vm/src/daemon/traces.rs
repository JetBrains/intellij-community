//! The trace pull of one iteration: packed zips pulled while the run streams, and once more with `--all` at its end.
//!
//! The daemon's stream says when a test ends. After each end, the sync asks the guest agent's
//! [`AgentVerb::TracePackReady`] for the bundles that finished since the last call, pulls that zip into the run's
//! directory ([`journal::traces_dir`]), and publishes one `traceReady` for each bundle in it. So a failed scenario's
//! video is on the host a few seconds after the scenario fails, while the lane goes on.
//!
//! The pulls run one at a time in [`TraceSync::run`], which the iteration polls beside its stream, and the stream
//! never waits for one: [`TraceSync::observe`] only stores a permit, and the permits coalesce, so a pull that takes
//! longer than a test covers every test that ended meanwhile. [`TraceSync::finish`] makes the last pull with
//! `--all`, which takes the bundles whose recorder never finished them.
//!
//! Never an error, by the rule the evidence pull states: every way a pull can fail becomes the report's
//! `tracesError`, and nothing here can turn a run red. The zips that arrived before a failure stay on the report.

use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;

use avl_base::format::words;
use avl_base::fs::create_private_dir;
use avl_base::sync::lock;
use avl_base::{Exit, OrRefuse, Refusal, Reporter, Scope, journal};
use avl_host_sys::guest::{AgentAccount, guest_join, user_argv};
use avl_host_sys::{Ctx, SpawnOptions};
use avl_trace::bundle::ROOT_DIR_NAME;
use avl_trace_tools::pack::Report;
use avl_wire::daemon::{RunEvent, RunEventKind};
use avl_wire::progress::{Event, Phase, TraceReady};
use avl_wire::report::{TraceArchive, TraceBundle};
use avl_wire::supervisor::Envelope;
use avl_wire::verb::AgentVerb;
use tokio::sync::Notify;
use tokio_util::sync::CancellationToken;

use crate::daemon::host::Host;
use crate::daemon::state::guest_state_dir;

#[cfg(test)]
mod tests;

/// Bounds the guest's zip of one pull.
///
/// Generous rather than tight, and it costs nothing when it is not reached: the videos and stills are stored
/// uncompressed, so packing is a copy of bytes that are already on the guest's disk, and only a guest in trouble
/// gets near five minutes. A pack that does reach it is a traces error on the report, never a failed iteration.
pub(crate) const TRACE_PACK_TIMEOUT: Duration = Duration::from_secs(300);

/// The timeout of the `test -d` that asks whether an iteration recorded traces: one exec round trip.
const TRACE_DIRECTORY_PROBE_TIMEOUT: Duration = Duration::from_secs(30);

/// What the pulls of one iteration brought, which the loop owns while it runs and hands to the last pull.
#[derive(Debug, Default)]
pub(crate) struct Pulls {
    count: usize,
    archives: Vec<TraceArchive>,
    failure: Option<String>,
}

impl Pulls {
    /// Records the first failure; a later one would only restate why the traces stopped arriving.
    fn failed(&mut self, message: String) {
        self.failure.get_or_insert(message);
    }
}

/// The traces of one iteration on one worker. See the module documentation.
pub(crate) struct TraceSync {
    worker: String,
    scope: Scope,
    run_id: Option<String>,
    /// Named by the stream's `runStarted`; the loop pulls nothing before it.
    iteration_id: Mutex<Option<String>>,
    /// One stored permit is a pending pull, however many tests ended.
    trigger: Notify,
}

impl TraceSync {
    pub(crate) fn new(worker: &str, run_id: Option<&str>) -> Self {
        Self {
            worker: worker.to_owned(),
            scope: Scope::worker(worker),
            run_id: run_id.map(str::to_owned),
            iteration_id: Mutex::new(None),
            trigger: Notify::new(),
        }
    }

    fn iteration_id(&self) -> Option<String> {
        lock(&self.iteration_id).clone()
    }

    /// The stream's hook. `runStarted` names the iteration; a test or a container that ends asks for a pull. It
    /// never waits.
    pub(crate) fn observe(&self, event: &RunEvent) {
        match &event.kind {
            RunEventKind::RunStarted(started) => {
                lock(&self.iteration_id).get_or_insert_with(|| started.iteration_id.clone());
            }
            RunEventKind::TestFinished(_) | RunEventKind::ContainerFailed(_) => {
                self.trigger.notify_one();
            }
            _ => {}
        }
    }

    /// Pulls after each trigger until `stop` or the operation is cancelled, and answers what arrived. A pull in
    /// flight when `stop` fires finishes first, so the last pull never races it.
    pub(crate) async fn run(&self, host: &Host, ctx: &Ctx, stop: &CancellationToken) -> Pulls {
        let mut pulls = Pulls::default();
        loop {
            tokio::select! {
                biased;
                () = stop.cancelled() => break,
                () = ctx.cancelled() => break,
                () = self.trigger.notified() => {
                    if let Some(iteration_id) = self.iteration_id() {
                        self.pull(host, ctx, &iteration_id, &mut pulls, false).await;
                    }
                }
            }
        }
        pulls
    }

    /// Makes the last pull and answers what the report says about the traces: nothing at all for an iteration the
    /// stream never named.
    pub(crate) async fn finish(&self, host: &Host, ctx: &Ctx, mut pulls: Pulls) -> (Vec<TraceArchive>, Option<String>) {
        let Some(iteration_id) = self.iteration_id() else {
            return (Vec::new(), None);
        };
        let phase = host
            .reporter
            .start_phase(Phase::Traces, "pull the traces the iteration left", Some(&self.scope));
        self.pull(host, ctx, &iteration_id, &mut pulls, true).await;
        match &pulls.failure {
            Some(failure) => phase.fail(failure),
            None => phase.succeed(),
        }
        (pulls.archives, pulls.failure)
    }

    /// One round: ask the guest for the bundles that are new, pull their zip, and name what it holds.
    async fn pull(&self, host: &Host, ctx: &Ctx, iteration_id: &str, pulls: &mut Pulls, all: bool) {
        let settings = &host.settings;
        let channel = host.channel(&self.worker);
        let guest_directory = guest_join(
            &guest_join(&guest_join(&guest_state_dir(settings), "iterations"), iteration_id),
            ROOT_DIR_NAME,
        );
        // A plain exec rather than a yes-or-no probe, because "no directory" and "the guest did not answer" are
        // different reports: the first is an iteration that recorded nothing yet, the second is a traces error.
        let probe = channel
            .exec(
                ctx,
                &user_argv(settings, &words(["/bin/test", "-d", &guest_directory])),
                &SpawnOptions::timeout(TRACE_DIRECTORY_PROBE_TIMEOUT, "guest_agent_timeout"),
            )
            .await;
        match probe {
            Err(refusal) => {
                return pulls.failed(format!("cannot ask whether {guest_directory} exists: {}", refusal.message));
            }
            Ok(probe) if probe.exit_code == 1 => return,
            Ok(probe) if probe.exit_code != 0 => {
                return pulls.failed(format!(
                    "test -d {guest_directory} in {} exited with {}",
                    self.worker, probe.exit_code
                ));
            }
            Ok(_) => {}
        }
        let Some(run_id) = &self.run_id else {
            return pulls.failed("the iteration runs outside a run, so its traces have no directory".to_owned());
        };
        let name = format!("{:03}.zip", pulls.count + 1);
        let guest_archive = format!("{guest_directory}-{name}");
        let mut args = vec![guest_directory.clone(), guest_archive.clone(), format!("{guest_directory}.ledger")];
        if all {
            args.push("--all".to_owned());
        }
        let answered = host
            .guest(ctx, channel.as_ref())
            .invoke_agent(
                AgentAccount::Worker,
                AgentVerb::TracePackReady,
                &args,
                &SpawnOptions::timeout(TRACE_PACK_TIMEOUT, "guest_trace_pack_timeout"),
            )
            .await;
        let answered = match answered {
            Ok(answered) => answered,
            Err(refusal) => return pulls.failed(refusal.message),
        };
        // The agent's own envelope and pack report, the declarations it writes them with. A verb that failed
        // exited nonzero and never reaches this decode.
        let report = match serde_json::from_str::<Envelope<Report>>(&answered)
            .map_err(|error| error.to_string())
            .and_then(|envelope| envelope.into_result().map_err(|error| error.to_string()))
        {
            Ok(report) => report,
            Err(error) => {
                return pulls.failed(format!(
                    "{} answered what this controller cannot read: {error}",
                    AgentVerb::TracePackReady
                ));
            }
        };
        if report.destination.is_none() {
            return;
        }
        let directory = match journal::traces_dir(&settings.runtime_root, run_id, iteration_id) {
            Ok(directory) => directory,
            Err(refusal) => return pulls.failed(refusal.message),
        };
        if let Err(refusal) = create_private_dir(&directory) {
            return pulls.failed(refusal.message);
        }
        let destination = directory.join(&name);
        let pulled = match crate::lane::pull_guest_file(ctx, &host.manager, &self.worker, &guest_archive, &destination).await {
            Ok(pulled) => pulled,
            Err(refusal) => return pulls.failed(refusal.message),
        };
        pulls.count += 1;
        let destination = real_zip_path(destination, &host.reporter, &self.scope);
        let zip = destination.to_string_lossy().into_owned();
        let mut archive = TraceArchive {
            path: zip.clone(),
            bytes: i64::try_from(pulled).unwrap_or(i64::MAX),
            bundles: Vec::new(),
        };
        let bundles = match read_bundles(&destination, &zip) {
            Ok(bundles) => bundles,
            Err(refusal) => {
                pulls.failed(refusal.message);
                Vec::new()
            }
        };
        for bundle in bundles {
            host.reporter.publish(
                Event::TraceReady(TraceReady {
                    iteration_id: iteration_id.to_owned(),
                    bundle_id: bundle.id.clone(),
                    test_class: bundle.test_class.clone(),
                    scenario: bundle.scenario.clone(),
                    flow: bundle.flow.clone(),
                    status: bundle.status.clone(),
                    has_video: bundle.has_video,
                    zip: zip.clone(),
                    entry: bundle.entry.clone(),
                }),
                Some(&self.scope),
            );
            archive.bundles.push(bundle);
        }
        pulls.archives.push(archive);
    }
}

/// The real path of a pulled zip, because the viewer names a bundle by the real path of its zip. The path is the one
/// that discovery resolves, [`fscopy::resolve_links`], so that the bundle ids here are the viewer's on every host.
///
/// A path that does not resolve stays as written, and a note says so: the bundle ids of that zip can then differ
/// from the viewer's, so its trace links can miss. The pull itself succeeded, so it is no `tracesError`.
fn real_zip_path(destination: PathBuf, reporter: &Reporter, scope: &Scope) -> PathBuf {
    match fscopy::resolve_links(&destination) {
        Ok(real) => real,
        Err(error) => {
            reporter.note(
                format!(
                    "cannot resolve the real path of the pulled traces {}: {error}; its trace links use the path as written",
                    destination.display()
                ),
                Some(scope),
            );
            destination
        }
    }
}

/// Names the bundles of a pulled zip with the viewer's own reader, so the ids are the viewer's.
///
/// A zip that cannot be read is `trace_archive_unreadable`; the caller records its message as the traces error.
fn read_bundles(path: &Path, container: &str) -> Result<Vec<TraceBundle>, Refusal> {
    let unreadable = || format!("the pulled traces {container} cannot be read");
    let file = std::fs::File::open(path).or_refuse("trace_archive_unreadable", Exit::DATA_ERR, unreadable)?;
    let mut archive = zip::ZipArchive::new(file).or_refuse("trace_archive_unreadable", Exit::DATA_ERR, unreadable)?;
    Ok(avl_trace_tools::discover::read_zip(&mut archive, container)
        .into_iter()
        .map(|bundle| TraceBundle {
            id: bundle.summary.id.clone(),
            entry: bundle.summary.source.entry.clone(),
            test_class: bundle.summary.test_class.clone(),
            scenario: bundle.summary.scenario.clone(),
            flow: bundle.summary.flow.clone(),
            status: bundle.summary.status.as_str().to_owned(),
            has_video: bundle.summary.has_video,
        })
        .collect())
}
