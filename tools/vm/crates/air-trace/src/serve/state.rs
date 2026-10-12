//! The server's state: what it is built from, the last scan, and the listing as the viewer reads it.

use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

use avl_base::sync::lock;
use avl_trace_tools::discover::{Bundle, Options, Root, RootState, Scanner, Snapshot, Status, Summary};
use futures::future::BoxFuture;
use serde::Serialize;
use sha2::{Digest, Sha256};
use tokio::sync::{Notify, broadcast, mpsc};
use tokio_util::sync::CancellationToken;

use super::BUNDLE_ROUTE;
use super::events::{Frame, Tail, Watches, native_watch};
use super::journals::{ControllerRun, Journals};
use super::service::{Activity, SiteBuild, build_site};
use crate::Refusal;
use crate::plan::{Input, PlanResult};

/// A line of the server's log.
pub(crate) type Log = Arc<dyn Fn(&str) + Send + Sync>;

/// Where the site build's output goes, a chunk of lines at a time.
pub(crate) type BuildOutput = Arc<dyn Fn(&str) + Send + Sync>;

/// The site build: the docs project to build, where its output goes, and what ends it early.
pub(crate) type BuildSite = Arc<dyn Fn(PathBuf, BuildOutput, CancellationToken) -> BoxFuture<'static, Result<(), Refusal>> + Send + Sync>;

/// The planner.
pub(crate) type Resolve = Arc<dyn Fn(&Path, Input) -> anyhow::Result<PlanResult> + Send + Sync>;

/// Where a file watcher sends each event it sees.
pub(crate) type WatchEvents = mpsc::UnboundedSender<notify::Result<notify::Event>>;

/// Starts the file watcher, which sends each event it sees to the sender.
pub(crate) type Watch = Arc<dyn Fn(WatchEvents) -> notify::Result<Box<dyn notify::Watcher + Send>> + Send + Sync>;

/// What a server is built from. The command line fills it; a test fills it directly.
pub(crate) struct Settings {
    /// The checkout, which the planner needs; `None` for a server started outside one.
    pub repo_root: Option<PathBuf>,
    pub site_dir: Option<PathBuf>,
    /// The inflate cache: one directory for each version of a zip whose deflated entries were asked for in ranges.
    pub cache_dir: PathBuf,
    pub roots: Vec<Root>,
    /// The controller's runs, `<runtime root>/runs`, each with its journal; `None` for none.
    pub runs_dir: Option<PathBuf>,
    pub log: Log,
    /// How long a directory bundle without a manifest may go unchanged and still be running.
    pub stale_after: Duration,
    /// How long the watcher waits after the last file event before it rescans.
    pub debounce: Duration,
    /// The scan the watcher runs regardless, for what it cannot see: a watch it had to skip, a staleness that is
    /// only the clock moving.
    pub rescan_interval: Duration,
    /// How often a running bundle's JSON Lines and the running runs' journals are checked, besides on events.
    pub tail_interval: Duration,
    /// Bounds the directories watched; a kqueue or inotify watcher holds a descriptor or a watch for each.
    pub max_watches: usize,
    /// Stops the server after that long with no request in progress and no open event stream.
    pub idle_exit: Option<Duration>,
    /// Holds the server's process id while it listens.
    pub pid_file: Option<PathBuf>,
    /// The docs site's project, `<repo>/plugins/air/docs`, which the server builds the site from when the site is
    /// missing or older than its sources; `None` for a site the server does not build.
    pub site_project: Option<PathBuf>,
    pub build_site: BuildSite,
    pub resolve: Resolve,
    /// The file watcher: the platform's own, or one that sees nothing for a server that only rescans.
    pub watch: Watch,
}

impl Settings {
    pub(crate) fn new(cache_dir: PathBuf, roots: Vec<Root>) -> Self {
        Self {
            repo_root: None,
            site_dir: None,
            cache_dir,
            roots,
            runs_dir: None,
            log: Arc::new(|_| {}),
            stale_after: Duration::from_mins(5),
            debounce: Duration::from_millis(300),
            rescan_interval: Duration::from_secs(10),
            tail_interval: Duration::from_secs(1),
            max_watches: 4096,
            idle_exit: None,
            pid_file: None,
            site_project: None,
            build_site: Arc::new(|project, output, cancel| Box::pin(build_site(project, output, cancel))),
            resolve: Arc::new(crate::plan::resolve),
            watch: native_watch(),
        }
    }
}

/// One scan's result, with the listing's generation.
pub(crate) struct Snap {
    pub found: Snapshot,
    pub controller_runs: Vec<ControllerRun>,
    /// Counts the listings that differed from the one before; `runs-changed` carries the same number, so a viewer
    /// can tell a listing it already has from a newer one.
    pub generation: u64,
    fingerprint: String,
}

/// `air-trace serve`.
pub(crate) struct Server {
    pub settings: Settings,
    pub scanner: Scanner,
    pub journals: Journals,
    pub hub: broadcast::Sender<Frame>,
    /// Serializes scans.
    pub scan_lock: Mutex<()>,
    /// The directories the scans want watched, on their way to the watcher thread.
    pub watches: Watches,
    current: Mutex<Option<Arc<Snap>>>,
    /// Every running directory bundle's tail, by bundle id; ordered so a poll announces bundles in one order.
    pub tails: Mutex<BTreeMap<String, Tail>>,
    /// Serializes filling the inflate cache.
    pub inflate: Mutex<()>,
    pub activity: Activity,
    pub site_build: Mutex<SiteBuild>,
    /// Asks the watch loop for one more scan after new watches were added. One stored permit at most.
    pub rescan_soon: Notify,
    /// Ends the server: its open event streams, its watch loop, its idle stop.
    pub shutdown: CancellationToken,
}

/// How many events a slow viewer may fall behind by. One that falls further is dropped; its EventSource reconnects
/// and lists the runs again, which is cheaper than a queue that grows.
const SUBSCRIBER_BUFFER: usize = 256;

/// How long an inflate cache directory nobody read is kept.
const CACHE_RETENTION: Duration = Duration::from_hours(7 * 24);

impl Server {
    pub(crate) fn new(settings: Settings) -> Arc<Self> {
        prune_cache(&settings.cache_dir, SystemTime::now() - CACHE_RETENTION);
        let scanner = Scanner::new(Options {
            stale_after: settings.stale_after,
            ..Options::default()
        });
        let journals = Journals::new(settings.runs_dir.clone());
        Arc::new(Self {
            settings,
            scanner,
            journals,
            hub: broadcast::channel(SUBSCRIBER_BUFFER).0,
            scan_lock: Mutex::new(()),
            watches: Watches::default(),
            current: Mutex::new(None),
            tails: Mutex::default(),
            inflate: Mutex::new(()),
            activity: Activity::default(),
            site_build: Mutex::default(),
            rescan_soon: Notify::new(),
            shutdown: CancellationToken::new(),
        })
    }

    /// Says an event to every open event stream.
    pub(crate) fn publish(&self, event: &'static str, data: &impl Serialize) {
        let Ok(data) = serde_json::to_string(data) else {
            return;
        };
        // No subscriber is not an error: nobody listens.
        let _ = self.hub.send(Frame { event, data: data.into() });
    }

    /// The last scan, if any.
    pub(crate) fn current(&self) -> Option<Arc<Snap>> {
        lock(&self.current).clone()
    }

    /// The last scan, scanning first when it is older than max_age.
    pub(crate) fn snapshot_now(&self, max_age: Duration) -> Arc<Snap> {
        if let Some(current) = self.current()
            && SystemTime::now().duration_since(current.found.scanned_at).unwrap_or_default() < max_age
        {
            return current;
        }
        self.scan()
    }

    /// The bundle with this id, rescanning once if the last scan did not have it.
    pub(crate) fn lookup(&self, id: &str) -> Option<Arc<Bundle>> {
        if let Some(found) = self.snapshot_now(Duration::from_hours(1)).found.by_id.get(id) {
            return Some(Arc::clone(found));
        }
        self.scan().found.by_id.get(id).cloned()
    }

    /// Looks in every root and replaces the current snapshot. When the listing differs from the last one it counts a
    /// new generation and says `runs-changed`.
    pub(crate) fn scan(&self) -> Arc<Snap> {
        let _scanning = lock(&self.scan_lock);
        // Lines appended to a bundle that is about to be seen finished go out before its status changes.
        self.poll_tails();

        let found = self.scanner.scan(&self.settings.roots);
        let (controller_runs, run_watch) = self.journals.list(SystemTime::now());
        let fingerprint = fingerprint_of(&found, &controller_runs);
        let mut next = Snap {
            found,
            controller_runs,
            generation: 0,
            fingerprint,
        };
        // Before the snapshot is published: a viewer that reads a running bundle from the listing must find its
        // tail already started, or lines written in between would be in neither its fetch nor the stream.
        self.update_tails(&next);

        let changed = {
            let mut current = lock(&self.current);
            let changed = current.as_ref().is_none_or(|previous| previous.fingerprint != next.fingerprint);
            next.generation = current.as_ref().map_or(0, |previous| previous.generation);
            if changed {
                next.generation += 1;
            }
            let next = Arc::new(next);
            *current = Some(Arc::clone(&next));
            (changed, next)
        };
        let (changed, next) = changed;
        if changed {
            self.publish("runs-changed", &serde_json::json!({ "generation": next.generation }));
        }
        for run in &next.controller_runs {
            self.announce_journal(&run.run_id);
        }
        // The running runs' directories come first, so a cap on the watches never drops them.
        let wanted: Vec<PathBuf> = run_watch.into_iter().chain(next.found.watch.iter().cloned()).collect();
        self.request_watches(wanted);
        next
    }
}

/// Removes the inflate cache's directories for zips not read since `before`. Each directory is one version of one
/// zip, so a zip rewritten by every Bazel run would otherwise leave one behind per run.
fn prune_cache(cache_dir: &Path, before: SystemTime) {
    let Ok(entries) = fs::read_dir(cache_dir) else {
        return;
    };
    for entry in entries.filter_map(Result::ok) {
        let stale = entry
            .metadata()
            .is_ok_and(|info| info.is_dir() && info.modified().is_ok_and(|modified| modified < before));
        if stale {
            let _ = fs::remove_dir_all(entry.path());
        }
    }
}

/// What decides whether two listings differ: the bundles and what the listing says of each, and the controller runs
/// and their counts. The roots' own state is left out, because `runs-changed` is about runs, and a root's state is
/// not. A journal record that changes no count, such as a phase's progress, changes no listing either;
/// `run-appended` announces it.
fn fingerprint_of(found: &Snapshot, controller_runs: &[ControllerRun]) -> String {
    let mut digest = Sha256::new();
    for bundle in &found.bundles {
        digest.update(format!(
            "{}|{}|{}|{}|{}|{}|{}\n",
            bundle.id,
            bundle.status,
            bundle.started_at.as_deref().unwrap_or_default(),
            bundle.duration_ms,
            bundle.thumbnail.as_deref().unwrap_or_default(),
            bundle.has_video,
            bundle.error.as_deref().unwrap_or_default()
        ));
    }
    for run in controller_runs {
        digest.update(format!(
            "run|{}|{}|{}|{}|{}|{}|{}|{}|{}\n",
            run.run_id,
            run.status.as_str(),
            run.command,
            run.iterations,
            run.tests_started,
            run.tests_passed,
            run.tests_failed,
            run.traces,
            run.error.as_deref().unwrap_or_default()
        ));
    }
    hex::encode(digest.finalize())
}

// --- the listing, as the viewer reads it ---------------------------------------------------------------------

/// `GET /__air/runs`.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Runs<'a> {
    pub generation: u64,
    /// The controller's runs, from their journals, newest first. Their scenarios are also in `runs`, grouped by the
    /// bundles' own run ids, which are iteration ids.
    pub controller_runs: &'a [ControllerRun],
    pub runs: Vec<Run>,
    pub roots: &'a [RootState],
}

/// The bundles of one run id, newest scenario last.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Run {
    pub run_id: String,
    /// The earliest scenario's start.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    /// The worst of its scenarios': running, failed, invalid, truncated, aborted, passed, in that order.
    pub status: Status,
    pub scenarios: Vec<Scenario>,
}

/// One bundle in the listing: what its files say, and where this server serves them.
#[derive(Serialize, Clone)]
pub(crate) struct Scenario {
    #[serde(flatten)]
    pub summary: Summary,
    /// The bundle's base, `/__air/bundle/<id>/`; a file of the bundle is the URL plus its path.
    pub url: String,
}

/// A found bundle as the listing names it.
pub(crate) fn listed(found: &Summary) -> Scenario {
    Scenario {
        summary: found.clone(),
        url: format!("{BUNDLE_ROUTE}{}/", found.id),
    }
}

/// The statuses of [Run::status], most urgent first.
const STATUS_RANK: [Status; 6] = [
    Status::Running,
    Status::Failed,
    Status::Invalid,
    Status::Truncated,
    Status::Aborted,
    Status::Passed,
];

fn rank_of(status: Status) -> usize {
    STATUS_RANK.iter().position(|ranked| *ranked == status).unwrap_or(STATUS_RANK.len())
}

/// A snapshot's bundles, grouped by run, newest run first.
pub(crate) fn runs_of(current: &Snap) -> Runs<'_> {
    let mut runs: Vec<Run> = Vec::new();
    let mut by_run: HashMap<String, usize> = HashMap::new();
    for found in &current.found.bundles {
        let scenario = listed(&found.summary);
        let index = *by_run.entry(scenario.summary.run_id.clone()).or_insert_with(|| {
            runs.push(Run {
                run_id: scenario.summary.run_id.clone(),
                started_at: None,
                status: Status::Passed,
                scenarios: Vec::new(),
            });
            runs.len() - 1
        });
        let run = &mut runs[index];
        if let Some(started) = &scenario.summary.started_at
            && run.started_at.as_ref().is_none_or(|earliest| started < earliest)
        {
            run.started_at = Some(started.clone());
        }
        if rank_of(scenario.summary.status) < rank_of(run.status) {
            run.status = scenario.summary.status;
        }
        run.scenarios.push(scenario);
    }
    runs.sort_by(|left, right| right.started_at.cmp(&left.started_at).then_with(|| left.run_id.cmp(&right.run_id)));
    Runs {
        generation: current.generation,
        controller_runs: &current.controller_runs,
        runs,
        roots: &current.found.roots,
    }
}
