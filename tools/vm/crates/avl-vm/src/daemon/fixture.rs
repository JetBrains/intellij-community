//! This crate's own view of [`crate::daemon::testing`]: the shared fixture over the one fake hypervisor, plus what a
//! several-worker command (`shard`, `flake`) needs of it - one fake daemon per worker - and finished run reports
//! written the way the controller persists them.

use std::ops::Deref;
use std::sync::{Arc, Mutex, PoisonError};

use avl_base::Config;
use avl_host_sys::Ctx;
use avl_host_testkit::answer_text;
use avl_testkit::tartfake::Fake;
use avl_wire::report::RunReport;
use serde_json::{Value, json};

use crate::daemon::PreparedBuild;
use crate::daemon::testing::{DaemonFixture, FakeDaemon, PASSING_XML, PLENTIFUL_DF, ndjson_line, run_started_record, summary_record};

/// A [`DaemonFixture`], with the fake `tart` of its pool named `tart`.
pub(crate) struct Fixture {
    inner: DaemonFixture,
    pub(crate) tart: Fake,
    /// The doubles of the workers after the first, which serve for as long as the fixture lives.
    daemons: Mutex<Vec<Arc<FakeDaemon>>>,
}

impl Deref for Fixture {
    type Target = DaemonFixture;

    fn deref(&self) -> &DaemonFixture {
        &self.inner
    }
}

impl Fixture {
    pub(crate) async fn new() -> Self {
        Self::with_environment(&[]).await
    }

    pub(crate) async fn with_environment(extra: &[(&str, &str)]) -> Self {
        Self::of(DaemonFixture::with_environment(extra).await)
    }

    /// The fixture over the production guest channel: see [`DaemonFixture::over_hypervisor`]. Guest answers are the
    /// fake's per-verb exec answers.
    pub(crate) async fn over_hypervisor() -> Self {
        Self::of(DaemonFixture::over_hypervisor(&[]).await)
    }

    /// The fixture over a Tart pool of the macOS guest: see [`DaemonFixture::tart_macos`].
    pub(crate) async fn tart_macos() -> Self {
        Self::of(DaemonFixture::tart_macos(&[]).await)
    }

    /// The fixture over a container-linux pool: see [`DaemonFixture::over_container_linux`].
    pub(crate) async fn over_container_linux() -> Self {
        Self::of(DaemonFixture::over_container_linux(&[]).await)
    }

    /// The fixture over a Docker pool: see [`DaemonFixture::docker`]. The fake `docker`'s calls are in `tart`'s
    /// call log, because the two fakes share one.
    pub(crate) async fn docker() -> Self {
        Self::of(DaemonFixture::docker().await)
    }

    /// The fixture a several-worker command runs over: a Tart pool of the macOS guest with two workers.
    ///
    /// Not a Docker pool, because the fake `docker` holds one container. Every worker of the pool is unleased, its
    /// guest answers `df` with plenty of space and silence to everything else, and `tart list` reports an empty pool.
    /// So a lease release finishes without a guest, because a stopped worker is released without touching one.
    /// `USER` is set, so a run id has a known prefix.
    pub(crate) async fn pool() -> Self {
        let fixture = Self::of(DaemonFixture::tart_macos(&[("USER", "suite-user")]).await);
        fixture.on("df", answer_text(PLENTIFUL_DF));
        fixture
    }

    fn of(inner: DaemonFixture) -> Self {
        Self {
            tart: inner.fake.clone(),
            inner,
            daemons: Mutex::default(),
        }
    }

    /// The pool's workers, in pool order.
    pub(crate) fn workers(&self) -> Vec<String> {
        self.settings.workers.clone()
    }

    /// Runs the build the way the command will, so a seeded daemon's launch digest matches it.
    pub(crate) async fn pool_build(&self) -> PreparedBuild {
        let scope = crate::daemon::build::BuildScope::pool(&self.settings).expect("the pool build scope");
        self.host
            .prepare_build(&Ctx::background(), &scope)
            .await
            .unwrap_or_else(|refusal| panic!("the build was refused: {refusal:?}"))
    }

    /// Gives one worker a healthy daemon whose digests match `prep`, so an iteration takes the warm path: no
    /// restart, no remount, no relaunch. The first worker gets the fixture's own double; every other worker a new
    /// one of its own.
    pub(crate) async fn warm_daemon(&self, worker: &str, prep: &PreparedBuild) -> Arc<FakeDaemon> {
        let daemon = if worker == self.worker {
            Arc::clone(&self.daemon)
        } else {
            let daemon = FakeDaemon::start(worker).await;
            self.ports.listen(daemon.port(), daemon.server_side());
            self.daemons
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .push(Arc::clone(&daemon));
            daemon
        };
        daemon.script().boot_stamp = format!("boot-{worker}");
        let mut state = daemon.host_state(&format!("run-{worker}"), &prep.launch_digest);
        state.runtime_digest = prep.runtime_digest.clone();
        state.last_product_digest = prep.product_digest.clone();
        state.last_mount_digest = prep.mount_digest.clone();
        state.last_plugins_digest = prep.plugins_digest.clone();
        state.write(&self.settings, worker).expect("the daemon state is written");
        daemon
    }

    /// Whether any worker of the pool is leased.
    pub(crate) fn leased(&self, worker: &str) -> bool {
        self.settings.lease_path(worker).exists()
    }
}

/// One green iteration's stream on a class of its own, and the XML that agrees with it.
pub(crate) fn passing_run(iteration_id: &str, class_name: &str) -> (Vec<String>, String) {
    let lines = vec![
        ndjson_line(&run_started_record(iteration_id)),
        ndjson_line(&json!({"event": "testStarted", "timestamp": "2026-08-23T12:00:01.000Z",
            "displayName": "scenario", "id": "t1", "className": class_name, "methodName": "scenario"})),
        ndjson_line(&json!({"event": "testFinished", "timestamp": "2026-08-23T12:00:02.000Z",
            "displayName": "scenario", "id": "t1", "className": class_name, "methodName": "scenario",
            "status": "SUCCESSFUL", "durationMs": 1000})),
        ndjson_line(&summary_record(iteration_id, 1, 0, 0, 0, 0, false)),
    ];
    let xml = PASSING_XML
        .replace("com.example.MyTest", class_name)
        .replace("name=\"works\"", "name=\"scenario\"");
    (lines, xml)
}

// --- finished reports ---------------------------------------------------------------------------------------------

/// A finished report of the shape the controller persists, as JSON: one suite per class, each suite's own duration
/// the class's and its one case half of it, the way a `@BeforeAll` IDE relaunch lives in the suite rather than in any
/// case.
pub(crate) fn baseline_report(classes: &[(&str, f64)]) -> Value {
    let suites: Vec<Value> = classes
        .iter()
        .enumerate()
        .map(|(index, (class_name, duration_ms))| {
            json!({
                "name": class_name,
                "timestamp": format!("2026-08-21T12:00:0{}Z", index % 10),
                "durationMs": duration_ms,
                "documentIndex": index,
                "tests": 1, "failures": 0, "errors": 0, "skipped": 0,
                "cases": [{"className": class_name, "name": "scenario", "status": "passed",
                    "durationMs": duration_ms / 2.0}],
            })
        })
        .collect();
    json!({
        "reportSchemaVersion": avl_wire::report::RUN_REPORT_SCHEMA_VERSION,
        "iterationId": "iter-boot-1",
        "daemonRunId": "run-baseline-1",
        "daemonBootStamp": "boot",
        "selection": "lane ui",
        "status": "passed",
        "startedAt": "2026-08-21T12:00:00Z",
        "completedAt": "2026-08-21T12:03:27Z",
        "durationMs": 207_000.0,
        "ordering": avl_wire::report::ORDERING,
        "execution": {"testsStarted": classes.len(), "testsFailed": 0, "testsSkipped": 0,
            "containersSkipped": 0, "containerFailures": 0},
        "xml": {"tests": classes.len(), "failures": 0, "errors": 0, "skipped": 0},
        "source": {"guestPath": "/worker/daemon/test.xml", "retrieval": "daemon_http", "integrity": "complete"},
        "suites": suites,
        "failures": [],
        "watchdog": {},
    })
}

/// The typed form of a report document.
pub(crate) fn typed(document: &Value) -> RunReport {
    serde_json::from_value(document.clone()).expect("the fixture report decodes")
}

/// Writes a report where the controller persists one: `<worker>/reports/<run>/<iteration>.json`.
pub(crate) fn write_report(settings: &Config, worker: &str, run_id: &str, iteration: &str, document: &Value) {
    let directory = settings.worker_dir(worker).join("reports").join(run_id);
    std::fs::create_dir_all(&directory).expect("the reports directory is created");
    let mut encoded = serde_json::to_vec(document).expect("a report encodes");
    encoded.push(b'\n');
    std::fs::write(directory.join(format!("{iteration}.json")), encoded).expect("the report is written");
}
