use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

use ::http::{HeaderMap, Method, Request, StatusCode, header};
use avl_host_sys::viewer::probe_viewer;
use avl_testkit::traces::{copy_tree, example_bundle, example_file};
use avl_trace::bundle::{
    BundleStatus, IDEA_LOG_FILE, LOGS_FILE, MANIFEST_FILE, Manifest, ROOT_DIR_NAME, SPANS_FILE, bundle_path, decode_manifest,
    snap_image_path, snap_tree_path,
};
use avl_trace::otlp::{attr, decode_logs_line, event, lookup_str};
use avl_trace_tools::discover::{Root, RootKind, SourceKind, bundle_id, default_roots};
use avl_trace_tools::pack::{PackOptions, pack};
use axum::body::Body;
use clap::Parser;
use futures::StreamExt;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};
use tempfile::TempDir;
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;
use tower::ServiceExt;

use super::events::native_watch;
use super::http::router;
use super::state::{Server, Settings};
use super::*;
use crate::plan::{PlanResult, ScenarioRef};

mod service;

// --- fixtures ------------------------------------------------------------------------------------------------

/// Temporary directories that live as long as the test.
#[derive(Default)]
struct Scratch(Mutex<Vec<TempDir>>);

impl Scratch {
    fn dir(&self) -> PathBuf {
        let dir = TempDir::new().expect("a temporary directory");
        let path = dir.path().to_path_buf();
        self.0.lock().expect("the scratch lock").push(dir);
        path
    }
}

fn real(path: &Path) -> PathBuf {
    fscopy::resolve_links(path).unwrap_or_else(|error| panic!("resolve {}: {error}", path.display()))
}

fn example_manifest() -> Manifest {
    decode_manifest(&example_file(MANIFEST_FILE)).expect("the golden manifest decodes")
}

/// The file the example's last snapshot names, which is the bundle's thumbnail.
fn example_last_picture() -> String {
    let logs = example_file(LOGS_FILE);
    let mut picture = None;
    for line in logs.split(|byte| *byte == b'\n').filter(|line| !line.is_empty()) {
        let data = decode_logs_line(line).expect("a golden log line decodes");
        for record in data
            .resource_logs
            .iter()
            .flat_map(|resource| &resource.scope_logs)
            .flat_map(|scope| &scope.log_records)
        {
            if record.event_name == event::SNAPSHOT {
                picture = lookup_str(&record.attributes, attr::SNAPSHOT_IMAGE).map(str::to_owned);
            }
        }
    }
    picture.expect("the example's last snapshot names a picture")
}

fn write_file(path: &Path, content: &[u8]) {
    fs::create_dir_all(path.parent().expect("a file has a parent")).expect("create directories");
    fs::write(path, content).expect("write a fixture file");
}

fn set_modified(path: &Path, time: SystemTime) {
    files::set_modified(path, time).unwrap_or_else(|error| panic!("touch {}: {error}", path.display()));
}

/// Two roots the way a machine has them.
///
/// - `dir_root` holds three directory bundles: the golden bundle under another run id with a failed status, a
///   running bundle that has only its first log line, and a stale one with no manifest.
/// - `zip_root` holds `outputs.zip`, the golden bundle packed under `air-traces/` the way Bazel zips a test's
///   undeclared outputs.
struct Fixture {
    scratch: Arc<Scratch>,
    dir_root: PathBuf,
    zip_root: PathBuf,
    zip_path: PathBuf,
    /// Only the symlink tests read it.
    #[cfg_attr(not(unix), expect(dead_code, reason = "the symlink case of the traversal test is Unix only"))]
    failed_dir: PathBuf,
    running_dir: PathBuf,
    manifest: Manifest,
}

fn new_fixture() -> Fixture {
    let scratch = Arc::new(Scratch::default());
    let manifest = example_manifest();
    let dir_root = scratch.dir();
    let zip_root = scratch.dir();

    let failed_dir = dir_root.join("run-dir").join(&manifest.test_class).join(&manifest.scenario);
    copy_tree(&example_bundle(), &failed_dir);
    let failed = Manifest {
        run_id: "run-dir".to_owned(),
        status: BundleStatus::Failed,
        ..manifest.clone()
    };
    write_file(
        &failed_dir.join(MANIFEST_FILE),
        &serde_json::to_vec(&failed).expect("a manifest serializes"),
    );

    let running_dir = dir_root.join("run-live").join("AirLiveTest").join("live-scenario");
    let logs = example_file(LOGS_FILE);
    let first_line = logs.split_inclusive(|byte| *byte == b'\n').next().expect("a first line");
    write_file(&running_dir.join(LOGS_FILE), first_line);
    write_file(&running_dir.join(SPANS_FILE), b"");

    let stale_dir = dir_root.join("run-stale").join("AirStaleTest").join("stale-scenario");
    write_file(&stale_dir.join(SPANS_FILE), b"");
    let hour_ago = SystemTime::now() - Duration::from_hours(1);
    for path in [stale_dir.join(SPANS_FILE), stale_dir] {
        set_modified(&path, hour_ago);
    }

    let outputs = scratch.dir();
    let placed = bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)
        .split('/')
        .fold(outputs.join(ROOT_DIR_NAME), |path, segment| path.join(segment));
    copy_tree(&example_bundle(), &placed);
    let zip_path = zip_root.join("outputs.zip");
    pack(&outputs, &zip_path, &PackOptions::default()).expect("pack the golden bundle");
    Fixture {
        scratch,
        dir_root,
        zip_root,
        zip_path,
        failed_dir,
        running_dir,
        manifest,
    }
}

fn fixture_roots(fixture: &Fixture) -> Vec<Root> {
    vec![
        Root::new(RootKind::Flag, &fixture.dir_root),
        Root::new(RootKind::BazelTestlogs, &fixture.zip_root),
    ]
}

/// A file watcher that sees nothing. FSEvents restarts a stream through fseventsd for every watch change, and a
/// score of test servers doing that at once on a loaded machine stalls each for tens of seconds. The tests that
/// wait for a file event take the platform's watcher back.
fn no_watch() -> state::Watch {
    Arc::new(|_| Ok(Box::new(notify::NullWatcher)))
}

fn test_settings(scratch: &Scratch, roots: Vec<Root>) -> Settings {
    let mut settings = Settings::new(scratch.dir(), roots);
    settings.watch = no_watch();
    settings.repo_root = Some(scratch.dir());
    settings.site_dir = Some(scratch.dir().join("air-site"));
    settings.stale_after = Duration::from_secs(60);
    settings.debounce = Duration::from_millis(30);
    settings.rescan_interval = Duration::from_hours(1);
    settings.tail_interval = Duration::from_millis(50);
    settings
}

/// A server with its watch loop running, answering requests without a socket.
struct TestServer {
    server: Arc<Server>,
    cancel: CancellationToken,
}

impl Drop for TestServer {
    fn drop(&mut self) {
        self.cancel.cancel();
        self.server.scanner.close();
    }
}

/// Starts a server over the settings, and returns once its watcher thread watches what the first scan found.
async fn start_server(settings: Settings) -> TestServer {
    let server = Server::new(settings);
    let cancel = CancellationToken::new();
    tokio::spawn(Arc::clone(&server).watch_loop(cancel.clone()));
    while !server.watches_settled() {
        tokio::time::sleep(Duration::from_millis(5)).await;
    }
    TestServer { server, cancel }
}

struct Answer {
    status: StatusCode,
    headers: HeaderMap,
    body: Vec<u8>,
}

impl Answer {
    fn text(&self) -> String {
        String::from_utf8_lossy(&self.body).into_owned()
    }

    fn header(&self, name: header::HeaderName) -> &str {
        self.headers.get(name).and_then(|value| value.to_str().ok()).unwrap_or_default()
    }

    fn json(&self) -> Value {
        serde_json::from_slice(&self.body).unwrap_or_else(|error| panic!("{} answered {}: {error}", self.status, self.text()))
    }
}

/// A request addressed to the loopback, as a browser on this machine sends it, with the given headers on top.
fn request(method: Method, uri: &str, headers: &[(&str, &str)], body: Body) -> Request<Body> {
    let mut builder = Request::builder().method(method).uri(uri);
    if !headers.iter().any(|(name, _)| name.eq_ignore_ascii_case("host")) {
        builder = builder.header(header::HOST, "127.0.0.1:7357");
    }
    for (name, value) in headers {
        builder = builder.header(*name, *value);
    }
    builder.body(body).expect("a well-formed request")
}

impl TestServer {
    async fn send(&self, request: Request<Body>) -> Answer {
        let response = router(Arc::clone(&self.server))
            .oneshot(request)
            .await
            .unwrap_or_else(|never| match never {});
        let status = response.status();
        let headers = response.headers().clone();
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("read the body")
            .to_vec();
        Answer { status, headers, body }
    }

    async fn get(&self, uri: &str, headers: &[(&str, &str)]) -> Answer {
        self.send(request(Method::GET, uri, headers, Body::empty())).await
    }

    async fn list_runs(&self) -> Value {
        let answer = self.get(RUNS_ROUTE, &[]).await;
        assert_eq!(answer.status, StatusCode::OK, "{}", answer.text());
        answer.json()
    }

    #[cfg(unix)]
    async fn scan(&self) {
        self.server.blocking(Server::scan).await;
    }

    /// Opens the event stream and answers its events, after `ready`.
    async fn subscribe(&self) -> Events {
        let response = router(Arc::clone(&self.server))
            .oneshot(request(Method::GET, EVENTS_ROUTE, &[], Body::empty()))
            .await
            .unwrap_or_else(|never| match never {});
        assert_eq!(
            response.headers().get(header::CONTENT_TYPE).map(header::HeaderValue::as_bytes),
            Some(&b"text/event-stream"[..])
        );
        let status = response.status();
        let headers = response.headers().clone();
        let (sender, receiver) = mpsc::unbounded_channel();
        let mut body = response.into_body().into_data_stream();
        let reader = tokio::spawn(async move {
            let mut buffer = String::new();
            while let Some(Ok(chunk)) = body.next().await {
                buffer.push_str(&String::from_utf8_lossy(&chunk));
                while let Some(end) = buffer.find("\n\n") {
                    let frame: String = buffer.drain(..end + 2).collect();
                    let (mut name, mut data) = (String::new(), String::new());
                    for line in frame.lines() {
                        if let Some(value) = line.strip_prefix("event: ") {
                            name = value.to_owned();
                        } else if let Some(value) = line.strip_prefix("data: ") {
                            data = value.to_owned();
                        }
                    }
                    if !name.is_empty() && sender.send(SseEvent { name, data }).is_err() {
                        return;
                    }
                }
            }
        });
        let mut events = Events {
            receiver,
            reader,
            opened: Answer {
                status,
                headers,
                body: Vec::new(),
            },
            ready: None,
        };
        let ready = events.wait_for(&["ready"]).await;
        assert!(ready.data.contains("generation"), "ready carried {}", ready.data);
        events.ready = Some(ready);
        events
    }
}

#[derive(Debug)]
struct SseEvent {
    name: String,
    data: String,
}

struct Events {
    receiver: mpsc::UnboundedReceiver<SseEvent>,
    reader: tokio::task::JoinHandle<()>,
    /// The head of the stream's response, with no body.
    opened: Answer,
    /// The first frame of the stream.
    ready: Option<SseEvent>,
}

impl Drop for Events {
    fn drop(&mut self) {
        self.reader.abort();
    }
}

impl Events {
    /// The next event that has one of the names.
    async fn wait_for(&mut self, names: &[&str]) -> SseEvent {
        let next = async {
            loop {
                match self.receiver.recv().await {
                    Some(event) if names.contains(&event.name.as_str()) => return event,
                    Some(_) => {}
                    None => panic!("the stream ended before {names:?}"),
                }
            }
        };
        tokio::time::timeout(Duration::from_secs(15), next)
            .await
            .unwrap_or_else(|_| panic!("none of {names:?} within 15 s"))
    }
}

/// The scenario of the listing that matches.
fn scenario_by(runs: &Value, matches: impl Fn(&Value) -> bool) -> Value {
    runs["runs"]
        .as_array()
        .into_iter()
        .flatten()
        .flat_map(|run| run["scenarios"].as_array().into_iter().flatten())
        .find(|scenario| matches(scenario))
        .cloned()
        .unwrap_or_else(|| panic!("no scenario matches in {runs}"))
}

fn is_zip(scenario: &Value) -> bool {
    scenario["source"]["kind"] == "zip"
}

fn str_of(value: &Value) -> &str {
    value.as_str().unwrap_or_default()
}

// --- the listing ---------------------------------------------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn runs_list_directory_and_zip_bundles_grouped_by_run() {
    let fixture = new_fixture();
    let served = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let runs = served.list_runs().await;
    let manifest = &fixture.manifest;

    let roots = runs["roots"].as_array().expect("roots is a list");
    assert_eq!(roots.len(), 2, "{runs}");
    assert_eq!(
        (&roots[0]["exists"], &roots[0]["bundles"], &roots[1]["bundles"]),
        (&json!(true), &json!(3), &json!(1)),
        "3 bundles in the directory root and 1 in the zip root"
    );
    let run_ids: BTreeSet<&str> = runs["runs"]
        .as_array()
        .into_iter()
        .flatten()
        .map(|run| str_of(&run["runId"]))
        .collect();
    assert_eq!(run_ids, BTreeSet::from([manifest.run_id.as_str(), "run-dir", "run-stale"]));

    let zipped = scenario_by(&runs, is_zip);
    let want_entry = format!(
        "{ROOT_DIR_NAME}/{}/",
        bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)
    );
    assert_eq!(zipped["source"]["path"], json!(real(&fixture.zip_path).to_string_lossy()));
    assert_eq!(zipped["source"]["entry"], json!(want_entry));
    assert_eq!(zipped["status"], "passed");
    assert_eq!(zipped["flow"], json!(manifest.flow));
    assert_eq!((&zipped["lane"], &zipped["launcher"]), (&json!("UI"), &json!("daemon")));
    assert_eq!(zipped["startedAt"], json!(manifest.started_at));
    assert_eq!(zipped["durationMs"], json!(manifest.duration_ms));
    assert_eq!(zipped["thumbnail"], json!(example_last_picture()));
    assert_eq!(zipped["hasVideo"], json!(false));
    assert_eq!(zipped["capture"]["source"], "x11");
    let id = str_of(&zipped["id"]);
    assert_eq!(id.len(), 20, "the id is ten bytes of hex");
    assert_eq!(zipped["url"], json!(format!("/__air/bundle/{id}/")));

    let failed = scenario_by(&runs, |scenario| scenario["runId"] == "run-dir");
    assert_eq!(
        (&failed["status"], &failed["source"]["kind"], &failed["thumbnail"]),
        (&json!("failed"), &json!("dir"), &json!(example_last_picture()))
    );

    // The running bundle's run id, scenario and start come from its first log line.
    let running = scenario_by(&runs, |scenario| scenario["testClass"] == "AirLiveTest");
    assert_eq!(running["status"], "running");
    assert_eq!(running["runId"], json!(manifest.run_id));
    assert_eq!(running["scenario"], json!(manifest.scenario));
    assert_eq!(running["lane"], "UI");
    assert_eq!(running["startedAt"], json!(manifest.started_at));
    // With nothing to read, the stale bundle's names come from its path.
    let stale = scenario_by(&runs, |scenario| scenario["runId"] == "run-stale");
    assert_eq!(
        (&stale["status"], &stale["testClass"], &stale["scenario"]),
        (&json!("truncated"), &json!("AirStaleTest"), &json!("stale-scenario"))
    );

    for run in runs["runs"].as_array().into_iter().flatten() {
        let run_id = str_of(&run["runId"]);
        let want = match run_id {
            "run-dir" => "failed",
            "run-stale" => "truncated",
            _ => "running",
        };
        assert_eq!(run["status"], want, "run {run_id}");
    }

    // The same bundle at the same place keeps its id across servers.
    let other = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let again = scenario_by(&other.list_runs().await, is_zip);
    assert_eq!(again["id"], zipped["id"]);
}

/// The controller's pulled zips are found by their glob alone, and a match that links out of the runtime root is
/// not served.
#[tokio::test(flavor = "multi_thread")]
async fn the_vm_reports_root_finds_pulled_iteration_zips() {
    let fixture = new_fixture();
    let runtime_root = fixture.scratch.dir();
    let workers = runtime_root.join("workers");
    let traces = workers.join("air-linux-1").join("reports").join("run-1").join("traces");
    write_file(&traces.join("iter-1.zip"), &fs::read(&fixture.zip_path).expect("read the zip"));
    write_file(&workers.join("air-linux-1").join("tart.log"), b"not walked");
    #[cfg(unix)]
    std::os::unix::fs::symlink(&fixture.zip_path, traces.join("linked.zip")).expect("link a zip");
    let roots = default_roots(None, Ok(&runtime_root));
    let kinds: Vec<RootKind> = roots.iter().map(|root| root.kind).collect();
    assert_eq!(kinds, vec![RootKind::VmReports, RootKind::VmRuns], "without a checkout");
    let served = start_server(test_settings(&fixture.scratch, roots)).await;
    let runs = served.list_runs().await;
    assert_eq!(runs["roots"][0]["bundles"], 1, "the one pulled zip: {runs}");
    let found = scenario_by(&runs, |_| true);
    assert_eq!(found["source"]["path"], json!(real(&traces).join("iter-1.zip").to_string_lossy()));
}

/// A root reached through a link, like `out/bazel-testlogs`, keeps its last directory while the link is gone, which
/// is what Bazel does to that link between builds; a link that names another directory moves the root.
#[cfg(unix)]
#[tokio::test(flavor = "multi_thread")]
async fn a_root_keeps_its_directory_while_its_link_is_gone() {
    let fixture = new_fixture();
    let link = fixture.scratch.dir().join("bazel-testlogs");
    std::os::unix::fs::symlink(&fixture.zip_root, &link).expect("link the root");
    let served = start_server(test_settings(&fixture.scratch, vec![Root::new(RootKind::BazelTestlogs, &link)])).await;
    let before = served.list_runs().await;
    assert_eq!(before["runs"].as_array().map(Vec::len), Some(1), "{before}");
    fs::remove_file(&link).expect("remove the link");
    served.scan().await;
    let during = served.list_runs().await;
    assert_eq!(during["runs"], before["runs"], "with the link gone");
    assert_eq!(during["generation"], before["generation"]);
    assert_eq!(during["roots"][0]["exists"], true);
    std::os::unix::fs::symlink(fixture.scratch.dir(), &link).expect("link another directory");
    served.scan().await;
    let after = served.list_runs().await;
    assert_eq!(after["runs"], json!([]), "a link to another directory still lists the old runs");
}

// --- serving files -------------------------------------------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn a_stored_zip_entry_is_served_in_ranges() {
    let fixture = new_fixture();
    let served = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let zipped = scenario_by(&served.list_runs().await, is_zip);
    let original = example_file(&snap_image_path(1));
    let url = format!("{}{}", str_of(&zipped["url"]), snap_image_path(1));

    let answer = served.get(&url, &[("range", "bytes=0-11")]).await;
    assert_eq!(answer.status, StatusCode::PARTIAL_CONTENT, "{}", answer.text());
    assert_eq!(answer.header(header::CONTENT_RANGE), format!("bytes 0-11/{}", original.len()));
    assert_eq!(answer.body, original[..12]);
    assert_eq!((&answer.body[..4], &answer.body[8..12]), (&b"RIFF"[..], &b"WEBP"[..]));
    assert_eq!(answer.header(header::CONTENT_TYPE), "image/webp");

    let open = served.get(&url, &[("range", "bytes=100-")]).await;
    assert_eq!(open.status, StatusCode::PARTIAL_CONTENT);
    assert_eq!(open.body, original[100..], "an open range");
    let etag = open.header(header::ETAG).to_owned();
    let revalidated = served.get(&url, &[("if-none-match", &etag)]).await;
    assert_eq!(revalidated.status, StatusCode::NOT_MODIFIED, "a revalidation with the entry's tag");
}

#[tokio::test(flavor = "multi_thread")]
async fn a_deflated_zip_entry_is_served_whole_and_in_ranges_through_the_cache() {
    let fixture = new_fixture();
    let settings = test_settings(&fixture.scratch, fixture_roots(&fixture));
    let cache = settings.cache_dir.clone();
    let served = start_server(settings).await;
    let zipped = scenario_by(&served.list_runs().await, is_zip);
    let base = str_of(&zipped["url"]).to_owned();

    let spans = example_file(SPANS_FILE);
    let whole = served.get(&format!("{base}{SPANS_FILE}"), &[]).await;
    assert_eq!(whole.status, StatusCode::OK, "{}", whole.text());
    assert_eq!(whole.body, spans);
    assert_eq!(whole.header(header::CONTENT_LENGTH), spans.len().to_string());
    let cached = || fs::read_dir(&cache).map_or(0, Iterator::count);
    assert_eq!(cached(), 0, "a whole read filled the inflate cache");

    let logs = example_file(LOGS_FILE);
    let ranged = served.get(&format!("{base}{LOGS_FILE}"), &[("range", "bytes=10-19")]).await;
    assert_eq!(ranged.status, StatusCode::PARTIAL_CONTENT, "{}", ranged.text());
    assert_eq!(ranged.body, logs[10..20]);
    assert_eq!(cached(), 1, "a ranged read leaves the zip's one cache directory");
}

#[tokio::test(flavor = "multi_thread")]
async fn a_directory_bundle_file_is_served() {
    let fixture = new_fixture();
    let served = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let failed = scenario_by(&served.list_runs().await, |scenario| scenario["runId"] == "run-dir");
    let url = format!("{}{}", str_of(&failed["url"]), snap_tree_path(3));
    let answer = served.get(&url, &[("range", "bytes=0-0")]).await;
    assert_eq!(answer.status, StatusCode::PARTIAL_CONTENT, "{}", answer.text());
    assert_eq!(answer.text(), "{");
    assert_eq!(answer.header(header::CONTENT_TYPE), "application/json; charset=utf-8");

    let listing = served.get(&format!("/__air/bundle/{}", str_of(&failed["id"])), &[]).await;
    assert_eq!(listing.status, StatusCode::OK, "{}", listing.text());
    let paths: Vec<String> = listing.json()["files"]
        .as_array()
        .into_iter()
        .flatten()
        .map(|file| str_of(&file["path"]).to_owned())
        .collect();
    for want in [MANIFEST_FILE, SPANS_FILE, IDEA_LOG_FILE, &example_last_picture()] {
        assert!(paths.iter().any(|path| path == want), "the listing lacks {want}: {paths:?}");
    }
}

/// Nothing outside the roots is served: not by `..` in a bundle path, spelled or escaped, not by a symbolic link
/// inside a bundle, and not to a request addressed to another host.
#[tokio::test(flavor = "multi_thread")]
async fn traversal_is_refused() {
    let fixture = new_fixture();
    let outside = fixture.scratch.dir();
    let secret = outside.join("secret.txt");
    write_file(&secret, b"secret");
    // A symbolic link needs a privilege on Windows, so the link case is Unix only.
    #[cfg(unix)]
    std::os::unix::fs::symlink(&secret, fixture.failed_dir.join("escape.txt")).expect("link out of the bundle");

    let served = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let failed = scenario_by(&served.list_runs().await, |scenario| scenario["runId"] == "run-dir");
    let base = str_of(&failed["url"]);
    let mut targets = vec![
        format!("{base}../../../../../../../../etc/passwd"),
        format!("{base}snap/%2e%2e/%2e%2e/%2e%2e/%2e%2e/etc/passwd"),
        format!("{base}snap/..%2f..%2f..%2fetc%2fpasswd"),
    ];
    if cfg!(unix) {
        targets.push(format!("{base}escape.txt"));
    }
    for target in targets {
        let answer = served.get(&target, &[]).await;
        assert_eq!(answer.status, StatusCode::FORBIDDEN, "{target} answered {}", answer.text());
    }
    let elsewhere = served.get(RUNS_ROUTE, &[("host", "attacker.example:7357")]).await;
    assert_eq!(elsewhere.status, StatusCode::FORBIDDEN, "a request addressed to another host");
    let cross_site = served.get(RUNS_ROUTE, &[("sec-fetch-site", "cross-site")]).await;
    assert_eq!(cross_site.status, StatusCode::FORBIDDEN, "a cross-site request");
    for answer in [&elsewhere, &cross_site] {
        assert_eq!(answer.header(header::HeaderName::from_static("x-air-trace")), "serve");
    }
}

// --- events --------------------------------------------------------------------------------------------------

/// A bundle appearing under a root is announced by the watcher alone: the periodic rescan is an hour away. It is
/// created the way a recorder creates one, a directory at a time and then its files, so the watch has to follow it
/// down levels that did not exist when the scan before it ran.
#[tokio::test(flavor = "multi_thread")]
async fn runs_changed_is_sent_when_a_bundle_appears() {
    let fixture = new_fixture();
    let mut settings = test_settings(&fixture.scratch, fixture_roots(&fixture));
    settings.watch = native_watch();
    let served = start_server(settings).await;
    let before = served.list_runs().await["generation"].as_u64().expect("a generation");
    let mut events = served.subscribe().await;

    let fresh = fixture.dir_root.join("run-new").join("AirNewTest").join("new-scenario");
    fs::create_dir_all(&fresh).expect("create the bundle's directories");
    tokio::time::sleep(Duration::from_millis(100)).await;
    write_file(&fresh.join(SPANS_FILE), b"");
    let id = bundle_id(SourceKind::Dir, &real(&fresh).to_string_lossy(), "");

    let deadline = tokio::time::Instant::now() + Duration::from_secs(15);
    loop {
        let event = events.wait_for(&["runs-changed"]).await;
        let payload: Value = serde_json::from_str(&event.data).expect("runs-changed carries JSON");
        let found = served.server.current().is_some_and(|current| current.found.by_id.contains_key(&id));
        if found {
            assert!(
                payload["generation"].as_u64() > Some(before),
                "runs-changed carried {payload}, not past {before}"
            );
            return;
        }
        assert!(
            tokio::time::Instant::now() < deadline,
            "runs-changed never announced the new bundle"
        );
    }
}

/// Lines appended to a running bundle go out as `bundle-append`, which names the bundle and carries complete lines.
#[tokio::test(flavor = "multi_thread")]
async fn bundle_append_carries_the_new_lines_of_a_running_bundle() {
    let fixture = new_fixture();
    let served = start_server(test_settings(&fixture.scratch, fixture_roots(&fixture))).await;
    let running = scenario_by(&served.list_runs().await, |scenario| scenario["status"] == "running");
    let mut events = served.subscribe().await;

    let logs_path = fixture.running_dir.join(LOGS_FILE);
    let mut logs = fs::OpenOptions::new().append(true).open(&logs_path).expect("open the logs");
    // The second line arrives in two writes, so the first poll may see half of it; half a line is never sent.
    io::Write::write_all(&mut logs, b"{\"appended\":1}\n{\"appen").expect("append");
    tokio::time::sleep(Duration::from_millis(150)).await;
    io::Write::write_all(&mut logs, b"ded\":2}\n").expect("append");
    drop(logs);

    let mut lines = Vec::new();
    while lines.len() < 2 {
        let event = events.wait_for(&["bundle-append"]).await;
        let appended: Value = serde_json::from_str(&event.data).expect("bundle-append carries JSON");
        assert_eq!((&appended["id"], &appended["file"]), (&running["id"], &json!(LOGS_FILE)));
        assert!(appended.get("offset").is_none(), "no reader asks where a batch starts: {appended}");
        lines.extend(
            appended["lines"]
                .as_array()
                .into_iter()
                .flatten()
                .map(|line| str_of(line).to_owned()),
        );
    }
    assert_eq!(lines, vec![r#"{"appended":1}"#, r#"{"appended":2}"#]);
}

// --- the planner ---------------------------------------------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn plan_joins_the_resolved_scenarios_with_the_bundles_on_disk() {
    let fixture = new_fixture();
    let mut settings = test_settings(&fixture.scratch, fixture_roots(&fixture));
    let received = Arc::new(Mutex::new(None));
    let manifest = fixture.manifest.clone();
    settings.resolve = {
        let received = Arc::clone(&received);
        Arc::new(move |_: &Path, input: crate::plan::Input| {
            let broken = input.text == "broken";
            *received.lock().expect("the received lock") = Some(input);
            if broken {
                anyhow::bail!("nothing maps broken");
            }
            Ok(PlanResult {
                lanes: vec!["UI".to_owned()],
                classes: vec![manifest.test_class.clone()],
                scenarios: vec![ScenarioRef {
                    test_class: manifest.test_class.clone(),
                    scenario: manifest.scenario.clone(),
                    flow: manifest.flow.clone(),
                    lane: "UI".to_owned(),
                }],
                ..PlanResult::default()
            })
        })
    };
    let served = start_server(settings).await;
    let received = || received.lock().expect("the received lock").clone().expect("the planner was asked");
    let post = |uri: &str, content_type: &str, body: &str| {
        request(Method::POST, uri, &[("content-type", content_type)], Body::from(body.to_owned()))
    };

    // Text, as the viewer sends what was typed or pasted.
    let answer = served
        .send(post(PLAN_ROUTE, "application/json", r#"{"text":"flow-rename-session"}"#))
        .await;
    assert_eq!(answer.status, StatusCode::OK, "{}", answer.text());
    let input = received();
    assert_eq!(
        (input.text.as_str(), input.file_name.as_str(), input.content.as_slice()),
        ("flow-rename-session", "", &b""[..])
    );
    let result = answer.json();
    assert_eq!(result["classes"].as_array().map(Vec::len), Some(1), "the plan lost its fields");
    assert_eq!(result["scenarios"].as_array().map(Vec::len), Some(1), "the plan lost its fields");
    let mut kinds: Vec<String> = result["existing"]
        .as_array()
        .into_iter()
        .flatten()
        .map(|existing| format!("{}/{}", str_of(&existing["runId"]), str_of(&existing["source"]["kind"])))
        .collect();
    kinds.sort();
    assert_eq!(kinds, vec![format!("{}/zip", fixture.manifest.run_id), "run-dir/dir".to_owned()]);

    // A dropped file, as the viewer sends one: its bytes as the body and its name in the query.
    let raw = served
        .send(post(
            &format!("{PLAN_ROUTE}?fileName=flow-rename%20session.txt"),
            "application/octet-stream",
            "raw flow text",
        ))
        .await;
    assert_eq!(raw.status, StatusCode::OK, "{}", raw.text());
    let input = received();
    assert_eq!(
        (input.file_name.as_str(), input.content.as_slice()),
        ("flow-rename session.txt", &b"raw flow text"[..])
    );
    let nameless = served.send(post(PLAN_ROUTE, "application/octet-stream", "no name")).await;
    assert_eq!(nameless.status, StatusCode::BAD_REQUEST);
    assert!(nameless.text().contains("?fileName="), "{}", nameless.text());
    assert_eq!(nameless.json()["code"], "plan_request_unreadable", "{}", nameless.text());

    // A file in the JSON body, its name and its bytes in base64, is a request no viewer sends any more.
    let unknown = served
        .send(post(
            PLAN_ROUTE,
            "application/json",
            r#"{"fileName":"x.txt","contentBase64":"eA=="}"#,
        ))
        .await;
    assert_eq!(
        unknown.status,
        StatusCode::BAD_REQUEST,
        "an unknown field is refused: {}",
        unknown.text()
    );
    assert_eq!(unknown.json()["code"], "plan_request_unreadable", "{}", unknown.text());
    let refused = served.send(post(PLAN_ROUTE, "application/json", r#"{"text":"broken"}"#)).await;
    assert_eq!(refused.status, StatusCode::UNPROCESSABLE_ENTITY);
    // The site reads `error`; `code` is the half a caller branches on.
    let answer = refused.json();
    assert!(
        answer["error"].as_str().is_some_and(|error| error.contains("nothing maps broken")),
        "{answer}"
    );
    assert_eq!(answer["code"], "plan_failed", "{answer}");
}

/// A server started outside a checkout refuses a plan with `no_checkout`.
#[tokio::test(flavor = "multi_thread")]
async fn plan_outside_a_checkout_is_refused_with_its_code() {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.repo_root = None;
    let served = start_server(settings).await;
    let answer = served
        .send(request(
            Method::POST,
            PLAN_ROUTE,
            &[("content-type", "application/json")],
            Body::from(r#"{"text":"flow-x"}"#),
        ))
        .await;
    assert_eq!(answer.status, StatusCode::UNPROCESSABLE_ENTITY);
    let refused = answer.json();
    assert_eq!(refused["code"], "no_checkout", "{refused}");
    assert!(
        refused["error"].as_str().is_some_and(|error| error.contains("needs the checkout")),
        "{refused}"
    );
}

// --- the site ------------------------------------------------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn the_site_is_served_with_the_single_page_fallback() {
    let scratch = Scratch::default();
    let settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    let site = settings.site_dir.clone().expect("the test names a site");
    let served = start_server(settings).await;
    let html = [("accept", "text/html")];

    let unbuilt = served.get("/air/runs", &html).await;
    assert_eq!(unbuilt.status, StatusCode::OK);
    assert!(unbuilt.text().contains("pnpm build"), "{}", unbuilt.text());

    write_file(&site.join("index.html"), b"<!doctype html><title>air</title>");
    write_file(&site.join("assets").join("app-1234.js"), b"console.log(1)");
    for route in ["/air/", "/air/runs", "/air/runs/trace"] {
        let page = served.get(route, &html).await;
        assert_eq!(page.status, StatusCode::OK, "{route}");
        assert!(page.text().contains("<title>air</title>"), "{route} answered {}", page.text());
    }
    let asset = served.get("/air/assets/app-1234.js", &[]).await;
    assert_eq!((asset.status, asset.text().as_str()), (StatusCode::OK, "console.log(1)"));
    assert!(asset.header(header::CACHE_CONTROL).contains("immutable"), "{:?}", asset.headers);
    let missing = served.get("/air/assets/missing.js", &[]).await;
    assert_eq!(missing.status, StatusCode::NOT_FOUND, "a missing asset");

    let redirect = served.get("/", &[]).await;
    assert_eq!(redirect.status, StatusCode::FOUND);
    assert_eq!(redirect.header(header::LOCATION), "/air/runs");
    let bare = served.get("/air", &[]).await;
    assert_eq!((bare.status, bare.header(header::LOCATION)), (StatusCode::FOUND, "/air/"));
}

// --- tails ---------------------------------------------------------------------------------------------------

/// A tail starts after the last complete line, answers only complete lines, and reads a replaced file again from its
/// start, so `bundle-append` never sends a line twice or half a line.
#[test]
fn a_tail_answers_complete_lines_from_where_the_last_one_ended() {
    let scratch = Scratch::default();
    let path = scratch.dir().join(LOGS_FILE);
    write_file(&path, b"one\ntwo\nhal");
    assert_eq!(events::complete_length(&path), 8);
    assert_eq!(events::complete_length(&scratch.dir().join("missing")), 0);

    let appended = events::read_appended(&path, 4).expect("read the tail");
    assert_eq!(appended.next, 8);
    assert_eq!(appended.lines, vec![b"two".to_vec()]);
    let nothing = events::read_appended(&path, 8).expect("read the tail");
    assert_eq!((nothing.next, nothing.lines.len()), (8, 0), "half a line is not answered");

    write_file(&path, b"new\n");
    let replaced = events::read_appended(&path, 8).expect("read the tail");
    assert_eq!(replaced.next, 4, "a shorter file was replaced");
    assert_eq!(replaced.lines, vec![b"new".to_vec()]);
}

/// A journal line longer than one answer is skipped, so the run page moves on to the records after it. Without the
/// skip, the answer kept `next` at the line's start with `more` false, and the page asked for the same offset for ever.
#[test]
fn the_run_route_moves_past_a_journal_line_longer_than_one_answer() {
    let scratch = Scratch::default();
    let path = scratch.dir().join("journal.ndjson");
    let first = r#"{"event":"first"}"#;
    let last = r#"{"event":"last"}"#;
    let envelope = r#"{"event":"long","pad":""}"#;
    let pad_length = usize::try_from(journals::MAX_RECORDS_READ).unwrap() + 1 - envelope.len();
    let long = format!(r#"{{"event":"long","pad":"{}"}}"#, "a".repeat(pad_length));
    assert_eq!(long.len() as u64, journals::MAX_RECORDS_READ + 1);
    write_file(&path, format!("{first}\n{long}\n{last}\n").as_bytes());
    let size = fs::metadata(&path).unwrap().len();

    let mut records = Vec::new();
    let mut from = 0;
    for _ in 0..4 {
        let answer = journals::read_records(&path, from).expect("read the journal");
        assert!(answer.next > from, "the answer from {from} did not move");
        records.extend(answer.records.iter().map(|record| record.get().to_owned()));
        from = answer.next;
        if !answer.more {
            break;
        }
    }
    assert_eq!(records, [first, last]);
    assert_eq!(from, size, "the page did not reach the end of the journal");

    // A line still being written is waited for, not skipped.
    write_file(&path, br#"{"event":"half"#);
    let partial = journals::read_records(&path, 0).expect("read the journal");
    assert_eq!((partial.next, partial.more, partial.records.len()), (0, false, 0));
}

// --- the command line ----------------------------------------------------------------------------------------

#[derive(Parser)]
struct ServeCli {
    #[command(flatten)]
    args: ServeArgs,
}

fn serve_args(arguments: &[&str]) -> Result<ServeArgs, clap::Error> {
    ServeCli::try_parse_from(std::iter::once("serve").chain(arguments.iter().copied())).map(|cli| cli.args)
}

/// Collects what the command line prints, a line at a time.
#[derive(Clone, Default)]
struct Captured(Arc<Mutex<Vec<String>>>);

impl Captured {
    fn log(&self) -> Log {
        let lines = Arc::clone(&self.0);
        Arc::new(move |line| {
            lines.lock().expect("the captured lock").push(line.to_owned());
        })
    }

    fn lines(&self) -> Vec<String> {
        self.0.lock().expect("the captured lock").clone()
    }
}

fn test_program() -> DetachProgram {
    DetachProgram {
        exe: PathBuf::from("/nonexistent/air-trace"),
        leading_args: vec!["serve".into()],
    }
}

/// Runs the command line over a checkout and a runtime root of the test's own, never the machine's.
async fn run_serve(arguments: &[&str], scratch: &Scratch, cancel: CancellationToken, out: &Captured, err: &Captured) -> u8 {
    let args = match serve_args(arguments) {
        Ok(args) => args,
        Err(error) => return u8::try_from(error.exit_code()).unwrap_or(1),
    };
    let context = Context {
        repo_root: Ok(scratch.dir()),
        runtime_root: Ok(scratch.dir()),
    };
    let streams = Streams {
        out: out.log(),
        err: err.log(),
    };
    serve(args, &test_program(), context, cancel, streams).await
}

/// It listens on the port it was given, prints where, serves, and exits 0 when its cancel ends, which is what
/// SIGINT does to it.
#[tokio::test(flavor = "multi_thread")]
async fn serve_serves_until_it_is_cancelled() {
    let scratch = Arc::new(Scratch::default());
    let traces = scratch.dir();
    let traces_arg = traces.to_string_lossy().into_owned();
    let cancel = CancellationToken::new();
    let (out, err) = (Captured::default(), Captured::default());
    let running = {
        let (scratch, cancel, out, err) = (Arc::clone(&scratch), cancel.clone(), out.clone(), err.clone());
        tokio::spawn(async move {
            run_serve(
                &["--port", "0", "--no-default-roots", "--root", &traces_arg],
                &scratch,
                cancel,
                &out,
                &err,
            )
            .await
        })
    };
    let first = tokio::time::timeout(Duration::from_secs(10), async {
        loop {
            if let Some(first) = out.lines().first().cloned() {
                return first;
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    })
    .await
    .unwrap_or_else(|_| panic!("serve printed nothing; stderr {:?}", err.lines()));
    let address = first
        .strip_prefix("air-trace serve: ")
        .filter(|address| address.starts_with("http://127.0.0.1:") && address.ends_with("/air/runs"))
        .unwrap_or_else(|| panic!("serve announced {first:?}"));
    let port: u16 = address
        .trim_start_matches("http://127.0.0.1:")
        .trim_end_matches("/air/runs")
        .parse()
        .expect("a port");
    let answers = tokio::task::spawn_blocking(move || probe_viewer(port, Duration::from_secs(5)))
        .await
        .expect("the probe ran");
    assert!(answers, "the server does not answer");
    let body = tokio::time::timeout(
        Duration::from_secs(10),
        tokio::task::spawn_blocking(move || service::http_get(port, RUNS_ROUTE)),
    )
    .await
    .expect("the listing did not answer")
    .expect("the listing was read");
    let runs: Value = serde_json::from_str(&body).expect("the listing is JSON");
    assert_eq!(runs["roots"].as_array().map(Vec::len), Some(1), "{runs}");
    assert_eq!(runs["roots"][0]["path"], json!(traces.to_string_lossy()));
    cancel.cancel();
    let code = tokio::time::timeout(Duration::from_secs(10), running)
        .await
        .unwrap_or_else(|_| panic!("serve did not stop within 10 s of its cancel; the grace is {SHUTDOWN_GRACE:?}"))
        .expect("serve did not panic");
    assert_eq!(code, 0, "stderr {:?}", err.lines());
}

#[tokio::test(flavor = "multi_thread")]
async fn serve_refuses_a_malformed_command_line() {
    let scratch = Scratch::default();
    for arguments in [
        &["extra"][..],
        &["--no-default-roots"][..],
        &["--port", "x"][..],
        &["--idle-exit", "soon"][..],
    ] {
        let (out, err) = (Captured::default(), Captured::default());
        let code = run_serve(arguments, &scratch, CancellationToken::new(), &out, &err).await;
        assert_eq!(code, 2, "{arguments:?}");
    }
}

#[test]
fn an_idle_exit_reads_back_what_detach_writes() {
    for (given, want) in [
        ("30m", Duration::from_mins(30)),
        ("2s", Duration::from_secs(2)),
        ("0", Duration::ZERO),
    ] {
        let parsed = parse_duration(given).expect("a duration");
        assert_eq!(parsed, want, "{given}");
        assert_eq!(parse_duration(&format_duration(parsed)), Ok(parsed), "{given} written back");
    }
    assert_eq!(parse_duration("300ms"), Ok(Duration::from_millis(300)));
}
