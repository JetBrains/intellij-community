//! This crate's suite fixture: the `run`, `daemon`, `shard` and `flake` suites share it. Hermetic: no VM, no network
//! beyond the loopback HTTP double, no Bazel.
//!
//! Three seams, all of them the production code's own. The hypervisor is the fake `tart` or `docker` of [`HostPool`]; the guest
//! is [`FakeGuests`] answering through a [`Verbs`] router; and the daemon's control channel is an axum server that
//! routes with [`avl_wire::daemon::match_route`], so the double cannot agree with the client while disagreeing with
//! the server. The scripted guest relays each connect to that server over an in-memory stream. Over the hypervisor,
//! the fake `tart` relays with `nc` to the server's loopback listener.
//!
//! The runner probes [`FakeProcesses`] and not the host. So a worker that reads as running does so on a loaded host
//! too.

use std::collections::{BTreeMap, VecDeque};
use std::io;
use std::net::SocketAddr;
use std::ops::Deref;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::Duration;

use crate::worker::tart::MINIMUM_VERSION;
use crate::worker::worker::{Dependencies, Lease, Manager, Timings, builds_nothing};
use async_trait::async_trait;
use avl_base::report::{Buffer, Mode, SinkGuard};
use avl_base::sync::lock;
use avl_base::{Backend, FakeClock, GuestOs, Refusal, Reporter, SCHEMA_VERSION};
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Ctx, GuestStream, Interrupts, ProcessTable, Runner};
use avl_host_testkit::agent::{active_reply, agent_reply, argv_value, run_state};
use avl_host_testkit::answer::base_name;
use avl_host_testkit::{
    Answer, ConnectHandler, FakeChannel, FakeGuests, FakeProcesses, HostPool, PinnedBazel, Verbs, answer_text, failed, handler, said,
    serve_on_connect,
};
use avl_wire::daemon::{self as wire, Route};
use avl_wire::progress::{Kind, Record, Verdict};
use avl_wire::stage::{LaunchPrepResult, RuntimeResult, arg_file_text, decode_launch_prep};
use axum::body::Body;
use axum::extract::{Request, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use hyper::body::Bytes;
use hyper_util::rt::TokioIo;
use hyper_util::service::TowerToHyperService;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use tokio::io::DuplexStream;
use tokio_util::sync::CancellationToken;

use crate::daemon::build::{BuildScope, PreparedBuild};
use crate::daemon::host::Host;
use crate::daemon::http::TOKEN_HEADER;
use crate::daemon::state::HostState;
use crate::lane::secrets::ScriptedStdin;
use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

// --- the guest replies -------------------------------------------------------------------------------------------

/// A `df -k` reply far above the soft threshold (100 GiB free).
pub(crate) const PLENTIFUL_DF: &str = "Filesystem 1024-blocks Used Available Capacity Mounted on\n/dev/disk1 999 1 104857600 1% /vm/data\n";

/// A `df -k` reply with the given Available column.
pub(crate) fn df_with_free_kib(kibibytes: &str) -> String {
    format!("Filesystem 1024-blocks Used Available Capacity Mounted on\n/dev/disk1 999 1 {kibibytes} 1% /vm/data\n")
}

// --- the fake daemon (HTTP) --------------------------------------------------------------------------------------

/// What the fake daemon answers, and what it was sent. Every field is the suite's to set.
#[derive(Debug)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "a fake's script: each bool is one independent answer a suite sets"
)]
pub(crate) struct DaemonScript {
    /// `METHOD /path` of every request, in order.
    pub(crate) requests: Vec<String>,
    pub(crate) protocol_version: u32,
    pub(crate) boot_stamp: String,
    pub(crate) launch_digest: String,
    pub(crate) mount_quiesced: bool,
    pub(crate) ide_running: bool,
    pub(crate) busy: bool,
    pub(crate) iteration_count: u32,
    /// `None` answers a healthy `/status` body.
    pub(crate) status_code: Option<u16>,
    pub(crate) missing: Vec<String>,
    pub(crate) jars_body: Vec<u8>,
    /// The path of every jar upload.
    pub(crate) uploads: Vec<String>,
    /// A digest whose upload answers this status instead of 200.
    pub(crate) upload_refused: Option<(String, u16)>,
    /// Holds every other upload open until cancelled, so a suite sees how many run at once and which ones end.
    pub(crate) upload_hold: Option<CancellationToken>,
    /// The uploads in flight now, and the most that were in flight at once.
    pub(crate) uploads_in_flight: usize,
    pub(crate) max_uploads_in_flight: usize,
    /// A 409 body every `/run` answers while set.
    pub(crate) conflict: Option<Value>,
    /// `None` streams the run lines.
    pub(crate) run_status: Option<u16>,
    /// The stream of every `/run` once [`DaemonScript::run_queue`] is empty.
    pub(crate) run_lines: Vec<String>,
    /// While not empty, the stream of the next `/run`, one entry per request, so a run of several lanes can see
    /// one iteration id per lane.
    pub(crate) run_queue: VecDeque<Vec<String>>,
    pub(crate) run_body: Vec<u8>,
    /// Aborts the connection after the run lines, as a daemon that died mid-stream does.
    pub(crate) close_mid_stream: bool,
    /// Holds a `/run` open until cancelled, so a suite can interrupt an iteration in flight.
    pub(crate) block: Option<CancellationToken>,
    /// `None` answers the results XML.
    pub(crate) results_status: Option<u16>,
    pub(crate) results_xml: Vec<u8>,
    /// Is to `results_xml` what `run_queue` is to `run_lines`.
    pub(crate) results_queue: VecDeque<Vec<u8>>,
}

/// The control channel's double: an axum router, routed by the wire's own route table. It serves on a loopback
/// listener for the `nc` relay of the fake `tart`, and over an in-memory stream for each relay of the scripted guest.
pub(crate) struct FakeDaemon {
    pub(crate) token: String,
    /// The worker whose guest this daemon runs in.
    worker: String,
    address: SocketAddr,
    script: Arc<Mutex<DaemonScript>>,
    app: axum::Router,
    server: tokio::task::JoinHandle<()>,
}

impl Drop for FakeDaemon {
    fn drop(&mut self) {
        self.server.abort();
    }
}

impl FakeDaemon {
    /// Starts the double of one worker's daemon on an ephemeral loopback port. Needs a tokio runtime.
    pub(crate) async fn start(worker: &str) -> Arc<Self> {
        let token = "fixture-token".to_owned();
        let script = Arc::new(Mutex::new(DaemonScript {
            requests: Vec::new(),
            protocol_version: wire::PROTOCOL_VERSION,
            boot_stamp: "boot-1".to_owned(),
            launch_digest: String::new(),
            mount_quiesced: false,
            ide_running: false,
            busy: false,
            iteration_count: 0,
            status_code: None,
            missing: Vec::new(),
            jars_body: Vec::new(),
            uploads: Vec::new(),
            upload_refused: None,
            upload_hold: None,
            uploads_in_flight: 0,
            max_uploads_in_flight: 0,
            conflict: None,
            run_status: None,
            run_lines: Vec::new(),
            run_queue: VecDeque::new(),
            run_body: Vec::new(),
            close_mid_stream: false,
            block: None,
            results_status: None,
            results_xml: Vec::new(),
            results_queue: VecDeque::new(),
        }));
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("the loopback accepts a listener");
        let address = listener.local_addr().expect("a bound listener has an address");
        let app = axum::Router::new().fallback(serve).with_state(Served {
            token: token.clone(),
            script: Arc::clone(&script),
        });
        let served = app.clone();
        let server = tokio::spawn(async move {
            let _ = axum::serve(listener, served).await;
        });
        Arc::new(Self {
            token,
            worker: worker.to_owned(),
            address,
            script,
            app,
            server,
        })
    }

    /// The server end of one relay: an HTTP/1.1 connection over the stream, served by the same router as the loopback
    /// listener. The connection runs on its own task until either end closes.
    pub(crate) fn server_side(&self) -> impl Fn(DuplexStream) + Send + Sync + 'static {
        let app = self.app.clone();
        move |server_end| {
            let service = TowerToHyperService::new(app.clone());
            tokio::spawn(async move {
                let _ = hyper::server::conn::http1::Builder::new()
                    .serve_connection(TokioIo::new(server_end), service)
                    .await;
            });
        }
    }

    /// The script, locked. Hold it only as long as a statement.
    pub(crate) fn script(&self) -> MutexGuard<'_, DaemonScript> {
        lock(&self.script)
    }

    pub(crate) fn saw_request(&self, fragment: &str) -> bool {
        self.script().requests.iter().any(|request| request.contains(fragment))
    }

    pub(crate) fn request_count(&self) -> usize {
        self.script().requests.len()
    }

    pub(crate) const fn port(&self) -> u16 {
        self.address.port()
    }

    /// A state the double will report healthy for, aimed at it. The digests the suite compares are its to fill.
    pub(crate) fn host_state(&self, run_id: &str, launch_digest: &str) -> HostState {
        let boot_stamp = {
            let mut script = self.script();
            script.launch_digest = launch_digest.to_owned();
            script.boot_stamp.clone()
        };
        HostState {
            run_id: run_id.to_owned(),
            port: self.port(),
            token: self.token.clone(),
            worker: self.worker.clone(),
            daemon_boot_stamp: boot_stamp,
            runtime_digest: String::new(),
            launch_digest: launch_digest.to_owned(),
            last_product_digest: String::new(),
            last_mount_digest: String::new(),
        }
    }

    /// What the guest daemon publishes for the boot poll to read, aimed at this double.
    pub(crate) fn state_file_json(&self) -> String {
        json!({
            "protocolVersion": wire::PROTOCOL_VERSION,
            "port": self.port(),
            "token": self.token,
            "pid": 7,
            "daemonBootStamp": self.script().boot_stamp,
        })
        .to_string()
    }
}

#[derive(Clone)]
struct Served {
    token: String,
    script: Arc<Mutex<DaemonScript>>,
}

async fn serve(State(served): State<Served>, request: Request) -> Response {
    let method = request.method().as_str().to_owned();
    let path = request.uri().path().to_owned();
    lock(&served.script).requests.push(format!("{method} {path}"));
    let authenticated = request
        .headers()
        .get(TOKEN_HEADER)
        .is_some_and(|value| value.as_bytes() == served.token.as_bytes());
    if !authenticated {
        return StatusCode::FORBIDDEN.into_response();
    }
    let route = wire::match_route(&method, &path);
    let body = axum::body::to_bytes(request.into_body(), usize::MAX).await.unwrap_or_default();
    match route {
        Some(Route::Status) => {
            let script = lock(&served.script);
            if let Some(code) = script.status_code {
                return status(code).into_response();
            }
            json!({
                "protocolVersion": script.protocol_version,
                "daemonBootStamp": script.boot_stamp,
                "controllerLaunchDigest": script.launch_digest,
                "mountQuiesced": script.mount_quiesced,
                "productStamp": "stamp",
                "ideRunning": script.ide_running,
                "busy": script.busy,
                "iterationCount": script.iteration_count,
                "metaspaceUsedBytes": 1,
                "pid": 42,
            })
            .to_string()
            .into_response()
        }
        Some(Route::MissingJars) => {
            let mut script = lock(&served.script);
            script.jars_body = body.to_vec();
            json!({ "missing": script.missing }).to_string().into_response()
        }
        Some(Route::UploadJar) => upload(&served.script, path).await,
        Some(Route::Run) => run(&served.script, body).await,
        Some(Route::Results) => {
            let mut script = lock(&served.script);
            if let Some(code) = script.results_status {
                return status(code).into_response();
            }
            let xml = script.results_queue.pop_front().unwrap_or_else(|| script.results_xml.clone());
            xml.into_response()
        }
        // 200 with no body, which is all the client reads of them.
        Some(Route::MountQuiesce | Route::MountResume | Route::IdeStop | Route::Shutdown) => StatusCode::OK.into_response(),
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

async fn upload(script: &Arc<Mutex<DaemonScript>>, path: String) -> Response {
    let (refused, hold) = {
        let mut script = lock(script);
        script.uploads.push(path.clone());
        script.uploads_in_flight += 1;
        script.max_uploads_in_flight = script.max_uploads_in_flight.max(script.uploads_in_flight);
        (script.upload_refused.clone(), script.upload_hold.clone())
    };
    // Counts the upload out on every end, a client that gave up and dropped it included.
    let _in_flight = InFlight(Arc::clone(script));
    if let Some((digest, code)) = refused
        && path.contains(&digest)
    {
        return status(code).into_response();
    }
    if let Some(hold) = hold {
        hold.cancelled().await;
    }
    StatusCode::OK.into_response()
}

/// One upload in flight of the double, counted out when it ends.
struct InFlight(Arc<Mutex<DaemonScript>>);

impl Drop for InFlight {
    fn drop(&mut self) {
        lock(&self.0).uploads_in_flight -= 1;
    }
}

async fn run(script: &Mutex<DaemonScript>, body: Bytes) -> Response {
    let (conflict, run_status, lines, close_mid_stream, block) = {
        let mut script = lock(script);
        script.run_body = body.to_vec();
        let lines = script.run_queue.pop_front().unwrap_or_else(|| script.run_lines.clone());
        (
            script.conflict.clone(),
            script.run_status,
            lines,
            script.close_mid_stream,
            script.block.clone(),
        )
    };
    // A client that gives up drops this future, which is the double's end of the request too.
    if let Some(block) = block {
        block.cancelled().await;
    }
    if let Some(conflict) = conflict {
        return (StatusCode::CONFLICT, conflict.to_string()).into_response();
    }
    if let Some(code) = run_status {
        return status(code).into_response();
    }
    use futures::StreamExt as _;
    let frames = futures::stream::iter(lines.into_iter().map(|line| Ok::<_, io::Error>(Bytes::from(format!("{line}\n")))));
    if !close_mid_stream {
        return Body::from_stream(frames).into_response();
    }
    // After the lines have left: a death before the response head is a refused request, not a broken stream.
    let death = futures::stream::once(async {
        tokio::time::sleep(Duration::from_millis(100)).await;
        Err(io::Error::other("the daemon died mid-stream"))
    });
    Body::from_stream(frames.chain(death)).into_response()
}

fn status(code: u16) -> StatusCode {
    StatusCode::from_u16(code).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR)
}

/// One daemon run record for the fake stream.
pub(crate) fn ndjson_line(record: &Value) -> String {
    record.to_string()
}

/// A complete summary record.
pub(crate) fn summary_record(
    iteration_id: &str,
    started: u32,
    failed: u32,
    skipped: u32,
    containers_skipped: u32,
    container_failures: u32,
    has_failures: bool,
) -> Value {
    json!({
        "event": "summary", "iterationId": iteration_id, "daemonBootStamp": "boot-1",
        "startedAt": "2026-08-23T12:00:00.000Z", "testsStarted": started, "testsFailed": failed,
        "testsSkipped": skipped, "containersSkipped": containers_skipped,
        "containerFailures": container_failures, "hasFailures": has_failures, "ideRunning": true,
    })
}

pub(crate) fn run_started_record(iteration_id: &str) -> Value {
    json!({"event": "runStarted", "iterationId": iteration_id, "daemonBootStamp": "boot-1",
        "startedAt": "2026-08-23T12:00:00.000Z"})
}

/// A complete document whose counts agree with [`passing_run_lines`]'s summary, so the report's verdict is `passed`
/// rather than an infrastructure mismatch.
pub(crate) const PASSING_XML: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
<testsuite name="com.example.MyTest" tests="1" failures="0" errors="0" skipped="0" time="1.0" timestamp="2026-08-23T12:00:01">
<testcase classname="com.example.MyTest" name="works" time="1.0"/>
</testsuite>
</testsuites>
"#;

/// A complete document with no suite at all.
pub(crate) const EMPTY_COMPLETE_XML: &str = r#"<?xml version="1.0" encoding="UTF-8"?><testsuites></testsuites>"#;

/// One green iteration's stream: one test that passed.
pub(crate) fn passing_run_lines(iteration_id: &str) -> Vec<String> {
    vec![
        ndjson_line(&run_started_record(iteration_id)),
        ndjson_line(&json!({"event": "testStarted", "timestamp": "2026-08-23T12:00:01.000Z",
            "displayName": "works", "id": "t1", "className": "com.example.MyTest", "methodName": "works"})),
        ndjson_line(&json!({"event": "testFinished", "timestamp": "2026-08-23T12:00:02.000Z",
            "displayName": "works", "id": "t1", "className": "com.example.MyTest", "methodName": "works",
            "status": "SUCCESSFUL", "durationMs": 1000})),
        ndjson_line(&summary_record(iteration_id, 1, 0, 0, 0, 0, false)),
    ]
}

/// One red iteration: its stream, and the XML that agrees with it.
pub(crate) fn failing_run(iteration_id: &str) -> (Vec<String>, String) {
    let lines = vec![
        ndjson_line(&run_started_record(iteration_id)),
        ndjson_line(
            &json!({"event": "testStarted", "timestamp": "t", "displayName": "works", "id": "t1",
            "className": "com.example.MyTest"}),
        ),
        ndjson_line(
            &json!({"event": "testFinished", "timestamp": "t", "displayName": "works", "id": "t1",
            "className": "com.example.MyTest", "status": "FAILED", "durationMs": 5, "error": "boom\nstack"}),
        ),
        ndjson_line(&summary_record(iteration_id, 1, 1, 0, 0, 0, true)),
    ];
    let xml = concat!(
        r#"<?xml version="1.0" encoding="UTF-8"?><testsuites>"#,
        r#"<testsuite name="com.example.MyTest" tests="1" failures="1" errors="0" skipped="0" time="1.0" timestamp="2026-08-23T12:00:01">"#,
        r#"<testcase classname="com.example.MyTest" name="works" time="1.0">"#,
        "<failure message=\"boom\" type=\"AssertionError\">boom\nstack</failure>",
        "</testcase></testsuite></testsuites>",
    );
    (lines, xml.to_owned())
}

// --- the runtime-descriptor fixture ------------------------------------------------------------------------------

/// The runfiles every fixture descriptor declares, with their contents.
pub(crate) const FIXTURE_RUNFILES: [(&str, &str); 8] = [
    ("_main/hot/a.jar", "hot-a"),
    ("_main/stable/one.jar", "stable-one"),
    ("_main/stable/two.jar", "stable-two"),
    ("_main/data/project.zip", "data-a"),
    ("_main/dist/x.ide.config", "config"),
    ("_main/dist/fingerprint.txt", "fingerprint"),
    ("_main/jbr/jbr.tar.gz", "jbr-archive"),
    ("_main/jbr/manifest.json", "jbr-manifest"),
];

/// The fixture's runtime descriptor, built for an arm64 Linux guest.
pub(crate) fn fixture_descriptor() -> Value {
    fixture_descriptor_for("linux_aarch64")
}

/// The fixture's runtime descriptor, with the JBR of one guest platform, such as `darwin_aarch64`.
pub(crate) fn fixture_descriptor_for(jbr_platform: &str) -> Value {
    let file = |logical_path: &str| {
        json!({
            "execPath": format!("bazel-out/bin/{logical_path}"),
            "logicalPath": logical_path,
            "owner": format!("//fixture:{}", base_name(logical_path)),
        })
    };
    json!({
        "schemaVersion": avl_wire::runtime::SCHEMA_VERSION,
        "kind": "air-ui-daemon-runtime",
        "mainClass": "com.example.Main",
        "staticJvmFlags": ["-Xmx1g", "-Dflag=<v>"],
        "classpath": {
            "hot": [file("_main/hot/a.jar")],
            "stable": [file("_main/stable/one.jar"), file("_main/stable/two.jar")],
        },
        "devDist": {
            "config": file("_main/dist/x.ide.config"),
            "fingerprint": file("_main/dist/fingerprint.txt"),
            "home": file("_main/dist/home"),
        },
        "jbr": {
            "archive": file("_main/jbr/jbr.tar.gz"),
            "javaHomeSuffix": "a&b",
            "manifest": file("_main/jbr/manifest.json"),
            "platform": jbr_platform,
            "preloadedOnly": false,
        },
        "data": [file("_main/data/project.zip")],
    })
}

/// Materializes the descriptor for `jbr_platform` and its runfiles tree in `directory`, answering the descriptor path.
pub(crate) fn write_runtime_fixture(directory: &Path, jbr_platform: &str) -> PathBuf {
    let descriptor_path = directory.join("ui_daemon.runtime.json");
    let root = avl_wire::runtime::runfiles_root(&descriptor_path);
    for (logical_path, content) in FIXTURE_RUNFILES {
        let target = root.join(logical_path);
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent).expect("the runfiles tree is created");
        }
        std::fs::write(&target, content).expect("a runfile is written");
    }
    std::fs::write(&descriptor_path, fixture_descriptor_for(jbr_platform).to_string()).expect("the descriptor is written");
    descriptor_path
}

/// Makes the fixture descriptor's runfiles look as on a Windows host: the tree moves to `store` beside the
/// descriptor, and a MANIFEST names each runfile there. Answers the store.
pub(crate) fn replace_tree_with_manifest(descriptor_path: &Path) -> PathBuf {
    let root = avl_wire::runtime::runfiles_root(descriptor_path);
    let store = descriptor_path.parent().expect("the descriptor has a directory").join("store");
    std::fs::rename(&root, &store).expect("the tree moves to the store");
    let manifest: String = FIXTURE_RUNFILES
        .iter()
        .map(|(logical_path, _)| format!("{logical_path} {}\n", store.join(logical_path).display()))
        .collect();
    std::fs::write(&avl_host_sys::runfiles::manifest_paths(descriptor_path)[0], manifest).expect("the MANIFEST is written");
    store
}

/// [`crate::daemon::BazelHost`] without Bazel: the build is a log line, and the descriptor is the fixture's.
pub(crate) struct FakeBazel {
    pub(crate) descriptor_path: PathBuf,
    build_error: Mutex<Option<Refusal>>,
    build_calls: AtomicUsize,
}

impl FakeBazel {
    pub(crate) const fn new(descriptor_path: PathBuf) -> Self {
        Self {
            descriptor_path,
            build_error: Mutex::new(None),
            build_calls: AtomicUsize::new(0),
        }
    }

    /// Makes every later build refuse with `refusal`.
    pub(crate) fn fail_builds(&self, refusal: Refusal) {
        *lock(&self.build_error) = Some(refusal);
    }

    /// Makes every later build pass again, as before [`FakeBazel::fail_builds`].
    pub(crate) fn pass_builds(&self) {
        *lock(&self.build_error) = None;
    }

    pub(crate) fn build_calls(&self) -> usize {
        self.build_calls.load(Ordering::SeqCst)
    }
}

#[async_trait]
impl avl_host_sys::guest::BazelHost for FakeBazel {
    async fn query(&self, _ctx: &Ctx, _command: &str, _args: &[String]) -> Result<String, Refusal> {
        Ok(String::new())
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        Ok(PathBuf::new())
    }
}

#[async_trait]
impl crate::daemon::host::BazelHost for FakeBazel {
    async fn build(&self, _ctx: &Ctx, log_path: &Path, label: &str) -> Result<(), Refusal> {
        self.build_calls.fetch_add(1, Ordering::SeqCst);
        let build_error = lock(&self.build_error).clone();
        if let Some(refusal) = build_error {
            return Err(refusal);
        }
        std::fs::write(log_path, format!("built {label}\n"))
            .map_err(|error| Refusal::internal(format!("cannot write the build log: {error}")))
    }

    async fn runtime_descriptor(&self, _ctx: &Ctx, _label: &str) -> Result<PathBuf, Refusal> {
        Ok(self.descriptor_path.clone())
    }
}

/// What listens on each guest port of the scripted guest. A relay to a port nothing listens on is refused, as the
/// production relay to a closed port is. All workers of the pool share this one map, so a relay through the wrong
/// worker's channel still reaches a daemon. The connector tests prove the routing by worker.
#[derive(Clone, Default)]
pub(crate) struct GuestPorts {
    listeners: Arc<Mutex<BTreeMap<u16, ConnectHandler>>>,
}

impl GuestPorts {
    /// Makes `server_side` serve every relay to `port`.
    pub(crate) fn listen(&self, port: u16, server_side: impl Fn(DuplexStream) + Send + Sync + 'static) {
        lock(&self.listeners).insert(port, Arc::new(serve_on_connect(server_side)));
    }

    /// The connect answer of the scripted guest: the listener on the port, or a refused relay.
    fn handler(&self) -> impl Fn(u16) -> Result<GuestStream, Refusal> + Send + Sync + 'static {
        let listeners = Arc::clone(&self.listeners);
        move |port| {
            let listener = lock(&listeners).get(&port).cloned();
            listener.map_or_else(
                || {
                    Ok(GuestStream::refused(format!(
                        "nothing listens on 127.0.0.1:{port} inside the guest"
                    )))
                },
                |listener| listener(port),
            )
        }
    }
}

// --- the fixture -------------------------------------------------------------------------------------------------

/// A [`Host`] over a Docker pool of the Linux guest, or a Tart pool of the macOS guest, with every collaborator faked.
///
/// The daemon environment paths are pinned (`/vm/...`) rather than derived from the temporary root, so guest paths
/// in assertions are literals.
pub(crate) struct DaemonFixture {
    pool: HostPool,
    pub(crate) manager: Arc<Manager>,
    pub(crate) runner: Runner,
    /// The interrupt service of the runner and the host; [`Interrupts::deliver`] interrupts without a signal.
    pub(crate) interrupts: Interrupts,
    pub(crate) bazel: Arc<FakeBazel>,
    pub(crate) host: Arc<Host>,
    pub(crate) daemon: Arc<FakeDaemon>,
    /// What listens inside the scripted guest. The fixture's daemon listens on its port.
    pub(crate) ports: GuestPorts,
    pub(crate) stdout: Buffer,
    pub(crate) stderr: Buffer,
    /// What `run --test-env NAME=@-` reads: empty, and not a terminal, until a suite scripts it.
    pub(crate) stdin: Arc<ScriptedStdin>,
    /// The pool's first worker, which every single-worker test uses.
    pub(crate) worker: String,
    verbs: Arc<Verbs>,
    /// `None` over the hypervisor, where the guest is the fake `tart`'s exec.
    guests: Option<Arc<FakeGuests>>,
    /// What the runner probes in place of the host.
    processes: Arc<FakeProcesses>,
}

impl Deref for DaemonFixture {
    type Target = HostPool;

    fn deref(&self) -> &HostPool {
        &self.pool
    }
}

impl DaemonFixture {
    /// The fixture with more environment variables, such as the `AIR_VM_DAEMON_*` budgets.
    ///
    /// A Docker pool of the Linux guest, with the first worker's container current and running. That is what a
    /// warm worker has. Without it, the gate of a warm iteration makes the container again and forgets the daemon.
    /// The guest of a running container answers a free run slot, so a lease release passes its check of the slot.
    pub(crate) async fn with_environment(extra: &[(&str, &str)]) -> Self {
        let fixture = Self::build(Backend::Docker, GuestOs::Linux, extra, false).await;
        fixture.seed_current_container().await;
        fixture.on("active", handler(|_, _| Ok(active_reply(None))));
        fixture
    }

    /// The fixture over a Docker pool: the fake `docker` answers the backend's own commands and records them in the
    /// fake's call log, and the scripted guest answers every guest command, as it does on Tart.
    pub(crate) async fn docker() -> Self {
        Self::build(Backend::Docker, GuestOs::Linux, &[], false).await
    }

    /// The fixture over a Tart pool of the macOS guest, for a suite whose subject is the Tart backend: its
    /// liveness, its remount, its gate. The scripted guest answers every guest command, as it does on Docker.
    pub(crate) async fn tart_macos(extra: &[(&str, &str)]) -> Self {
        Self::build(Backend::Tart, GuestOs::Macos, extra, false).await
    }

    /// The fixture over the production guest channel: no `TART_BIN` and no scripted guest. The Tart backend
    /// resolves the fake through [`PinnedBazel`], the way a checkout resolves the pinned Tart through Bazel, so
    /// every guest command is a `tart exec` the fake spawns and records. An empty executable cannot pass this
    /// fixture unnoticed, which the scripted channel, handed the in-guest argv, never saw.
    ///
    /// The fixture runs the fake once before it answers, with [`avl_testkit::tartfake::Fake::exec_once`]. The first
    /// guest command of a suite here can have a budget of ten seconds, such as `/shutdown` and `daemon log`, and the
    /// macOS scan of the new file is then not inside that budget.
    ///
    /// [`DaemonFixture::channel`] panics here; a suite reads the fake's call log and seeds its exec answers.
    pub(crate) async fn over_hypervisor(extra: &[(&str, &str)]) -> Self {
        Self::build(Backend::Tart, GuestOs::Macos, extra, true).await
    }

    async fn build(backend: Backend, guest_os: GuestOs, extra: &[(&str, &str)], over_hypervisor: bool) -> Self {
        avl_affected::bridge::install_fixture();
        let mut builder = HostPool::builder(backend, guest_os, MINIMUM_VERSION).with_git();
        if over_hypervisor {
            builder = builder.without_tart_bin();
        }
        for (name, value) in [
            ("AIR_VM_USER", "admin"),
            ("AIR_VM_HOME", "/vm/home"),
            ("AIR_VM_DATA", "/vm/data"),
            ("AIR_VM_TMP", "/vm/tmp"),
            ("AIR_VM_DOWNLOAD_CACHE", "/vm/cache"),
            ("AIR_VM_NODE", "/vm/node"),
        ]
        .iter()
        .chain(extra)
        {
            builder = builder.env(name, value);
        }
        let pool = builder.build();
        if over_hypervisor {
            pool.fake.exec_once();
        }
        let settings = Arc::clone(&pool.settings);
        let verbs = Verbs::new(&settings.vm_agent);
        let ports = GuestPorts::default();
        let guests = (!over_hypervisor).then(|| {
            let guests = FakeGuests::new();
            let verbs = Arc::clone(&verbs);
            guests.answer(move |argv, options| verbs.route(argv, options));
            guests.on_connect(ports.handler());
            guests
        });
        let interrupts = Interrupts::detached();
        let processes = Arc::new(FakeProcesses::default());
        let runner = Runner::new(pool.environment.iter().cloned(), interrupts.clone())
            .with_process_table(Arc::clone(&processes) as Arc<dyn ProcessTable>);
        let (reporter, stdout, stderr) = Reporter::in_memory("vm-test");
        reporter.set_mode(Mode::Stream);
        let manager = Arc::new(Manager::with_timings(
            Dependencies {
                settings: Arc::clone(&settings),
                locks: Arc::new(LockManager::new(runner.clone())),
                runner: runner.clone(),
                reporter,
                channel: guests.as_ref().map(FakeGuests::factory),
                bazel: over_hypervisor
                    .then(|| Arc::new(PinnedBazel::tart(pool.fake.executable())) as Arc<dyn avl_host_sys::guest::BazelHost>),
                build_guest_boot: builds_nothing(),
            },
            // The suite's poll bounds, so a Docker share refresh settles in milliseconds, not in two seconds.
            Timings::fast(),
        ));
        manager.prepare_runtime_dirs().expect("the runtime directories are created");
        let out = pool.root().join("out");
        std::fs::create_dir_all(&out).expect("the output directory is created");
        let platform = crate::lane::env::guest_jbr_platform(settings.guest_os, settings.guest_arch);
        let bazel = Arc::new(FakeBazel::new(write_runtime_fixture(&out, platform)));
        // Advances only when a poll sleeps, so a poll loop's suite never waits out a real budget.
        let clock = Arc::new(FakeClock::at("2026-08-23T12:00:00Z"));
        let stdin = Arc::new(ScriptedStdin::default());
        let host = Arc::new(
            Host::new(
                Arc::clone(&manager),
                runner.clone(),
                bazel.clone(),
                avl_base::Environment::from_pairs(pool.environment.iter().cloned()),
            )
            .with_poll_clock(clock)
            .with_stdin(Arc::clone(&stdin) as Arc<dyn crate::lane::secrets::SecretStdin>),
        );
        let worker = settings.workers[0].clone();
        let daemon = FakeDaemon::start(&worker).await;
        ports.listen(daemon.port(), daemon.server_side());
        let fixture = Self {
            pool,
            manager,
            runner,
            interrupts,
            bazel,
            host,
            daemon,
            ports,
            stdout,
            stderr,
            stdin,
            worker,
            verbs,
            guests,
            processes,
        };
        // No iteration recorded traces unless its test says so, which is what a lane with tracing off leaves. Every
        // other `test` keeps the unrouted default, exit 0.
        let traces = format!("/{}", avl_trace::bundle::ROOT_DIR_NAME);
        fixture.on(
            "test",
            handler(move |argv, _| {
                let exit_code = i32::from(argv.last().is_some_and(|last| last.ends_with(&traces)));
                Ok(failed(exit_code, ""))
            }),
        );
        fixture
    }

    /// Installs one guest answer by verb: the agent's own subcommand for the agent binary, the basename for
    /// everything else (`df`, `ls`, `cat`, `test`, ...). An unrouted verb exits 0 and says nothing.
    pub(crate) fn on(&self, verb: &str, answer: Answer) {
        self.verbs.on(verb, answer);
    }

    /// Forgets what the guests, the fake hypervisor and the daemon double were asked, so the next case of a test that
    /// shares this fixture reads only its own calls. The answers stay, and a case sets again each answer it changes.
    pub(crate) fn forget_calls(&self) {
        if let Some(guests) = &self.guests {
            guests.forget_calls();
        }
        self.pool.fake.forget_calls();
        self.daemon.script().requests.clear();
    }

    /// Forgets the host records of the first worker: its daemon record, its staged runtime and everything else in its
    /// private directory. So the next case of a test that shares this fixture starts from a worker as fresh as
    /// [`DaemonFixture::build`] left it.
    pub(crate) fn forget_worker_records(&self) {
        let directory = self.settings.worker_dir(&self.worker);
        std::fs::remove_dir_all(&directory).unwrap_or_else(|error| panic!("remove {}: {error}", directory.display()));
        self.manager.prepare_runtime_dirs().expect("the runtime directories are created");
    }

    /// The scripted channel into the first worker.
    pub(crate) fn channel(&self) -> Arc<FakeChannel> {
        self.channel_of(&self.worker)
    }

    /// The scripted channel into one worker, created on first use.
    pub(crate) fn channel_of(&self, worker: &str) -> Arc<FakeChannel> {
        let guests = self.guests.as_ref().unwrap_or_else(|| {
            panic!("no scripted channel into {worker}: a fixture over the hypervisor has none, so read the fake's calls")
        });
        guests.channel(worker)
    }

    /// Scripts every guest verb one full start needs, aimed at the fixture's HTTP double. `prep` supplies the
    /// runtime digest the stager's reply must be inside.
    pub(crate) fn install_happy_guest(&self, prep: &PreparedBuild) {
        let generation = format!("/vm/data/daemon-runtime/generations/{}", prep.runtime_digest);
        self.on("df", answer_text(PLENTIFUL_DF));
        self.on("active", handler(|_, _| Ok(active_reply(None))));
        for (verb, phase) in [("start", "running"), ("status", "running"), ("cancel", "finished")] {
            self.on(
                verb,
                handler(move |argv, _| Ok(agent_reply(verb, &run_state(argv_value(argv, "--run"), phase)))),
            );
        }
        let staged_classpath = vec![format!("{generation}/classpath/000.jar"), format!("{generation}/classpath/001.jar")];
        let result = RuntimeResult {
            root: generation.clone(),
            java_binary: format!("{generation}/jbr/bin/java"),
            classpath: staged_classpath.clone(),
            classpath_file: format!("{generation}/classpath.txt"),
            reused: false,
        };
        let staged = serde_json::to_string(&result).expect("a stage result encodes");
        self.on("stage", answer_text(staged));
        // The launch-prep double does what the guest does: it decodes the request and renders the @-file from the
        // classpath the stage above reported. A double that echoed the requested digest back would agree with the
        // controller no matter what either half actually rendered, which is the one thing this reply proves.
        self.on(
            "launch-prep",
            handler(move |_, options| {
                let stdin = options.stdin.as_deref().unwrap_or_default();
                let request = match decode_launch_prep(stdin) {
                    Ok(request) => request,
                    Err(error) => {
                        return Ok(failed(70, &error.to_string()));
                    }
                };
                let mut tokens = request.arg_file.prefix.clone();
                tokens.push("-cp".to_owned());
                tokens.push(staged_classpath.join(":"));
                tokens.push(request.arg_file.main_class.clone());
                let content = arg_file_text(&tokens);
                let answer = LaunchPrepResult {
                    arg_file: request.arg_file.destination,
                    sha256: hex::encode(Sha256::digest(content.as_bytes())),
                    bytes: content.len() as u64,
                    entries: u32::try_from(staged_classpath.len()).expect("a staged classpath fits a u32"),
                };
                Ok(said(&serde_json::to_string(&answer).expect("a launch-prep result encodes")))
            }),
        );
        self.on("cat", answer_text(self.daemon.state_file_json()));
    }

    /// Makes the supervisor's run slot stateful: `start` takes it, `active` names its holder, and a `cancel` that
    /// names the holder frees it. Call it after [`DaemonFixture::install_happy_guest`], whose handlers it overrides.
    ///
    /// The happy guest's constant `active: null` cannot show a run that a failed start left in the slot, and this
    /// double can. `holder` is what the slot holds before anything runs; the answer is the slot, for a suite to read.
    pub(crate) fn install_slot_double(&self, holder: Option<&str>) -> Arc<Mutex<Option<String>>> {
        let slot = Arc::new(Mutex::new(holder.map(str::to_owned)));
        let taken = Arc::clone(&slot);
        self.on(
            "start",
            handler(move |argv, _| {
                let run = argv_value(argv, "--run").to_owned();
                *lock(&taken) = Some(run.clone());
                Ok(agent_reply("start", &run_state(&run, "running")))
            }),
        );
        let held = Arc::clone(&slot);
        self.on("active", handler(move |_, _| Ok(active_reply(lock(&held).as_deref()))));
        let freed = Arc::clone(&slot);
        self.on(
            "cancel",
            handler(move |argv, _| {
                let run = argv_value(argv, "--run");
                let mut holder = lock(&freed);
                if holder.as_deref() == Some(run) {
                    *holder = None;
                }
                Ok(agent_reply("cancel", &run_state(run, "finished")))
            }),
        );
        slot
    }

    /// Runs the build over the first worker's scope, the way every caller does before comparing launch digests.
    pub(crate) async fn prepared(&self) -> PreparedBuild {
        let scope = BuildScope::worker(&self.settings, &self.worker).expect("the build scope is created");
        self.host
            .prepare_build(&Ctx::background(), &scope)
            .await
            .unwrap_or_else(|refusal| panic!("the build was refused: {refusal:?}"))
    }

    /// Records a daemon host state the HTTP double answers healthy for.
    pub(crate) fn seed_healthy_daemon(&self, prep: &PreparedBuild) -> HostState {
        let mut state = self.daemon.host_state("run-ui-daemon-seeded", &prep.launch_digest);
        state.runtime_digest = prep.runtime_digest.clone();
        state.last_product_digest = prep.product_digest.clone();
        state.last_mount_digest = prep.mount_digest.clone();
        state.write(&self.settings, &self.worker).expect("the daemon state is written");
        state
    }

    /// Makes the first worker's container the one this controller created with the current argv, and running: what a
    /// warm iteration on a Docker pool finds. A Tart pool has no container, and nothing changes.
    pub(crate) async fn seed_current_container(&self) {
        let Some(docker) = self.manager.machine().docker() else {
            return;
        };
        let ctx = Ctx::background();
        docker.create(&ctx, &self.worker).await.expect("the container is created");
        docker.start(&ctx, &self.worker).await.expect("the container is started");
    }

    /// Makes one worker read as running, through the pid receipt Tart liveness is: a live process of the fake table
    /// stands in for its run process. A release of it then goes through the guest. The command is no `tart run`, so
    /// no path takes the process for an orphaned run.
    pub(crate) async fn read_as_running(&self, worker: &str) {
        let pid = self.processes.start(&format!("stand-in for the run process of {worker}"));
        self.manager
            .write_process_identity(&Ctx::background(), worker, pid)
            .await
            .expect("the fake process can be identified");
    }

    /// Puts the first worker into the state the readiness gate accepts without booting anything: it reads as
    /// running, and has an init receipt for the fixture's paths.
    pub(crate) async fn mark_ready(&self) {
        self.read_as_running(&self.worker).await;
        avl_host_sys::guest::write_init_receipt(&self.settings, &self.worker).expect("the init receipt is written");
    }

    /// Makes the double answer one stream and one XML per `/run`, in order.
    pub(crate) fn queue_runs(&self, streams: Vec<Vec<String>>, documents: &[&str]) {
        let mut script = self.daemon.script();
        script.run_queue = streams.into();
        script.results_queue = documents.iter().map(|document| document.as_bytes().to_vec()).collect();
    }

    /// Scripts the double for one iteration: its stream and the XML its report reads.
    pub(crate) fn script_run(&self, lines: Vec<String>, xml: &str) {
        let mut script = self.daemon.script();
        script.run_lines = lines;
        script.results_xml = xml.as_bytes().to_vec();
    }

    /// Writes a live lease on `worker` held by `holder` on `guest_os`, the way an acquisition would have.
    pub(crate) fn write_lease(&self, worker: &str, holder: &str, guest_os: GuestOs) -> Lease {
        let lease = Lease {
            schema_version: SCHEMA_VERSION,
            backend: self.settings.backend,
            guest_os,
            worker: worker.to_owned(),
            token: format!("token-{worker}"),
            holder: holder.to_owned(),
            acquired_at: "2026-08-23T00:00:00.000Z".to_owned(),
        };
        let mut encoded = serde_json::to_vec(&lease).expect("a lease encodes");
        encoded.push(b'\n');
        std::fs::write(self.settings.lease_path(worker), encoded).expect("the lease is written");
        lease
    }

    /// [`DaemonFixture::write_lease`], plus the caller's secure receipt for it: what a command given `--lease` reads.
    pub(crate) fn leased_by(&self, worker: &str, holder: &str, guest_os: GuestOs) -> PathBuf {
        let lease = self.write_lease(worker, holder, guest_os);
        crate::worker::lease::write_lease_receipt(&self.settings, &lease)
            .unwrap_or_else(|refusal| panic!("the lease receipt was refused: {refusal:?}"))
    }

    /// The first worker's lease, held by the suite.
    pub(crate) fn lease(&self) -> Lease {
        self.write_lease(&self.worker, "suite", self.settings.guest_os)
    }

    /// The first worker's lease and its receipt.
    pub(crate) fn lease_receipt(&self) -> PathBuf {
        self.leased_by(&self.worker, "suite", self.settings.guest_os)
    }
}

/// Every progress record a reporter published while this lives.
pub(crate) struct Recorded {
    records: Arc<Mutex<Vec<Record>>>,
    _sink: SinkGuard,
}

impl Recorded {
    pub(crate) fn start(reporter: &Reporter) -> Self {
        let records: Arc<Mutex<Vec<Record>>> = Arc::default();
        let sink = {
            let records = Arc::clone(&records);
            reporter.add_sink(move |record: &Record| lock(&records).push(record.clone()))
        };
        Self { records, _sink: sink }
    }

    /// The data of every record of one kind, decoded.
    pub(crate) fn data_of<T: serde::de::DeserializeOwned>(&self, kind: Kind) -> Vec<T> {
        lock(&self.records)
            .iter()
            .filter(|record| record.event == kind)
            .filter_map(|record| record.data.as_ref())
            .map(|data| serde_json::from_str(data.get()).unwrap_or_else(|error| panic!("a {kind} record does not decode: {error}")))
            .collect()
    }

    /// The last verdict published, if one was.
    pub(crate) fn verdict(&self) -> Option<Verdict> {
        self.data_of(Kind::Verdict).pop()
    }
}
