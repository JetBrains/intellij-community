use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

use avl_base::journal::{FILE_NAME as JOURNAL_FILE, run_dir};
#[cfg(unix)]
use avl_host_sys::viewer::{DetachRequest, SERVE_LOG_FILE, SERVE_PID_FILE, viewer_dir};
#[cfg(unix)]
use avl_host_sys::{Interrupts, Runner};
use avl_trace_tools::viewer::RUNS_ROUTE;
use avl_wire::progress::{
    Event as Progress, IterationReported, Phase, PhaseChange, PhaseState, Record, RunFinished, RunStarted, TraceReady,
};

use super::super::journals::STOPPED_AFTER;
use super::super::service::{PATH_PNPM, SITE_SOURCES_DIR, SiteTools, site_needs_build, site_steps};
#[cfg(unix)]
use super::super::service::{build_site_with, detach};
use super::*;
use crate::{Exit, Refusal, code};
use pretty_assertions::assert_eq;

/// A GET over a real socket, for a server that listens; answers the body.
pub(super) fn http_get(port: u16, route: &str) -> String {
    let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("connect to the server");
    write!(
        stream,
        "GET {route} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
    )
    .expect("send");
    let mut answer = String::new();
    stream.read_to_string(&mut answer).expect("read the answer");
    let (head, body) = answer.split_once("\r\n\r\n").expect("an HTTP answer");
    assert!(head.starts_with("HTTP/1.1 200"), "{head}");
    body.to_owned()
}

// --- the controller's runs -----------------------------------------------------------------------------------

/// One record of a journal, the way the controller's journal writes it.
fn journal_line(event: &Progress, at: jiff::Timestamp) -> String {
    let record = Record::new(event, at, None);
    format!("{}\n", serde_json::to_string(&record).expect("a record serializes"))
}

fn daemon_record(event: &str, status: &str) -> Progress {
    Progress::Structured {
        data: json!({"event": event, "status": status, "displayName": "scenario"}),
        line: String::new(),
    }
}

fn run_started(run_id: &str, command: &str, args: &[&str]) -> Progress {
    Progress::RunStarted(RunStarted {
        run_id: run_id.to_owned(),
        command: command.to_owned(),
        args: args.iter().map(|arg| (*arg).to_owned()).collect(),
        checkout: None,
    })
}

fn trace_ready(iteration: &str, scenario: &str) -> Progress {
    Progress::TraceReady(TraceReady {
        iteration_id: iteration.to_owned(),
        bundle_id: "b1".to_owned(),
        test_class: String::new(),
        scenario: scenario.to_owned(),
        flow: None,
        status: "failed".to_owned(),
        has_video: false,
        zip: String::new(),
        entry: String::new(),
    })
}

/// Writes a run's journal, one record a second from 2026-09-24T10:00:00Z, and answers its path.
fn write_journal(runs_dir: &Path, run_id: &str, events: &[Progress]) -> PathBuf {
    let start: jiff::Timestamp = "2026-09-24T10:00:00Z".parse().expect("a timestamp");
    let content: String = events
        .iter()
        .enumerate()
        .map(|(index, event)| {
            let seconds = i64::try_from(index).expect("a small index");
            journal_line(event, start + jiff::SignedDuration::from_secs(seconds))
        })
        .collect();
    let path = runs_dir.join(run_id).join(JOURNAL_FILE);
    write_file(&path, content.as_bytes());
    path
}

fn append(path: &Path, content: &[u8]) {
    let mut file = fs::OpenOptions::new().append(true).open(path).expect("open the journal");
    file.write_all(content).expect("append to the journal");
}

/// The runs directory is spelled here and by the controller's journal; the two must name the same directory.
#[test]
fn the_runs_directory_is_the_journals_directory() {
    let root = Path::new("/runtime");
    assert_eq!(
        run_dir(root, "run-1").expect("a valid run id"),
        root.join(VM_RUNS_DIR).join("run-1")
    );
}

#[tokio::test(flavor = "multi_thread")]
async fn controller_runs_are_listed_from_their_journals() {
    let scratch = Scratch::default();
    let runs_dir = scratch.dir();
    write_journal(
        &runs_dir,
        "run-finished",
        &[
            run_started("run-finished", "run", &["flow-new-session", "--lane", "ui"]),
            Progress::Phase(PhaseChange {
                phase: Phase::Iteration,
                state: PhaseState::Started,
                detail: None,
                elapsed_ms: 0,
                error: None,
            }),
            daemon_record("testStarted", ""),
            daemon_record("testFinished", "SUCCESSFUL"),
            daemon_record("testStarted", ""),
            daemon_record("testFinished", "FAILED"),
            trace_ready("iter-1", "one"),
            Progress::IterationReported(IterationReported {
                iteration_id: "iter-1".to_owned(),
                daemon_run_id: "run-ui-daemon-1".to_owned(),
                selection: "lane ui".to_owned(),
                status: avl_wire::report::Status::Failed,
                report_path: "/r.json".to_owned(),
                tests_failed: 1,
                tests_started: 2,
            }),
            Progress::RunFinished(RunFinished {
                exit_code: 1,
                code: Some("tests_failed".to_owned()),
                message: None,
            }),
        ],
    );
    write_journal(&runs_dir, "run-live", &[run_started("run-live", "shard", &[])]);
    let silent = write_journal(&runs_dir, "run-killed", &[run_started("run-killed", "run", &[])]);
    set_modified(&silent, SystemTime::now() - 2 * STOPPED_AFTER);
    // Not a run: a file, and a directory without a journal.
    write_file(&runs_dir.join("stray.txt"), b"");
    fs::create_dir_all(runs_dir.join("run-empty")).expect("create a directory");

    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.runs_dir = Some(runs_dir);
    let served = start_server(settings).await;
    let runs = served.list_runs().await;
    let controller_runs = runs["controllerRuns"].as_array().expect("controllerRuns is a list");
    assert_eq!(controller_runs.len(), 3, "the three with a journal: {runs}");
    let by_id = |run_id: &str| {
        controller_runs
            .iter()
            .find(|run| run["runId"] == run_id)
            .cloned()
            .unwrap_or_else(|| panic!("no run {run_id}"))
    };
    let finished = by_id("run-finished");
    assert_eq!(finished["status"], "failed");
    assert_eq!(finished["finished"]["code"], "tests_failed");
    assert_eq!(finished["command"], "run");
    assert_eq!(finished["args"], json!(["flow-new-session", "--lane", "ui"]));
    assert_eq!(
        [
            &finished["testsStarted"],
            &finished["testsPassed"],
            &finished["testsFailed"],
            &finished["traces"],
            &finished["iterations"]
        ],
        [&json!(2), &json!(1), &json!(1), &json!(1), &json!(1)]
    );
    assert_eq!(finished["startedAt"], "2026-09-24T10:00:00.000Z");
    assert_eq!(finished["updatedAt"], "2026-09-24T10:00:08.000Z");
    assert_eq!(finished["url"], "/__air/run/run-finished");
    assert_eq!(by_id("run-live")["status"], "running");
    assert_eq!(by_id("run-killed")["status"], "stopped");
}

#[tokio::test(flavor = "multi_thread")]
async fn the_run_route_answers_the_journal_from_an_offset() {
    let scratch = Scratch::default();
    let runs_dir = scratch.dir();
    let journal = write_journal(
        &runs_dir,
        "run-1",
        &[run_started("run-1", "run", &[]), Progress::Note("leasing".to_owned())],
    );
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.runs_dir = Some(runs_dir.clone());
    let served = start_server(settings).await;

    let answer = served.get("/__air/run/run-1", &[]).await;
    assert_eq!(answer.status, StatusCode::OK, "{}", answer.text());
    let first = answer.json();
    let size = fs::metadata(&journal).expect("the journal").len();
    assert_eq!(first["records"].as_array().map(Vec::len), Some(2), "{first}");
    assert_eq!((&first["from"], &first["next"]), (&json!(0), &json!(size)));
    assert_eq!(first["run"]["command"], "run");
    assert_eq!(first["records"][0]["event"], "runStarted");
    assert_eq!(first["records"][0]["data"]["runId"], "run-1");

    // A new record and half of the next one: only the whole record is answered.
    let next = journal_line(&Progress::Note("booted".to_owned()), jiff::Timestamp::now());
    append(&journal, format!("{next}{{\"event\":\"prog").as_bytes());
    let second = served.get(&format!("/__air/run/run-1?from={size}"), &[]).await.json();
    assert_eq!(second["records"].as_array().map(Vec::len), Some(1), "{second}");
    assert_eq!((&second["from"], &second["next"]), (&json!(size), &json!(size + next.len() as u64)));
    let reset = served.get("/__air/run/run-1?from=999999", &[]).await.json();
    assert_eq!((&reset["reset"], &reset["from"]), (&json!(true), &json!(0)), "{reset}");
    assert_eq!(
        reset["records"].as_array().map(Vec::len),
        Some(3),
        "past the end, every record again"
    );

    let outside = scratch.dir().join(JOURNAL_FILE);
    write_file(
        &outside,
        journal_line(&Progress::Note("secret".to_owned()), jiff::Timestamp::now()).as_bytes(),
    );
    fs::create_dir_all(runs_dir.join("linked")).expect("create the linked run");
    #[cfg(unix)]
    std::os::unix::fs::symlink(&outside, runs_dir.join("linked").join(JOURNAL_FILE)).expect("link a journal");
    let mut routes = vec![
        ("/__air/run/missing", StatusCode::NOT_FOUND),
        ("/__air/run/..", StatusCode::FORBIDDEN),
        ("/__air/run/a/b", StatusCode::FORBIDDEN),
        ("/__air/run/run-1?from=-1", StatusCode::BAD_REQUEST),
        ("/__air/run/run-1?from=one", StatusCode::BAD_REQUEST),
    ];
    if cfg!(unix) {
        routes.push(("/__air/run/linked", StatusCode::FORBIDDEN));
    }
    for (route, want) in routes {
        let answer = served.get(route, &[]).await;
        assert_eq!(answer.status, want, "{route} answered {}", answer.text());
    }
}

/// A record appended to a running run's journal is announced by the watcher, and the counts it changes reach the
/// listing.
#[tokio::test(flavor = "multi_thread")]
async fn run_appended_is_sent_when_a_journal_grows() {
    let scratch = Scratch::default();
    let runs_dir = scratch.dir();
    let journal = write_journal(&runs_dir, "run-1", &[run_started("run-1", "run", &[])]);
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.runs_dir = Some(runs_dir.clone());
    // The new run's directory at the end has only the platform's watcher to announce it.
    settings.watch = native_watch();
    let served = start_server(settings).await;
    served.list_runs().await;
    let mut events = served.subscribe().await;

    append(
        &journal,
        journal_line(&trace_ready("iter-1", "one"), jiff::Timestamp::now()).as_bytes(),
    );
    // The two come in either order: a scan that was due anyway can read the record before the watcher reports it.
    let mut seen = BTreeSet::new();
    while seen.len() < 2 {
        let event = events.wait_for(&["run-appended", "runs-changed"]).await;
        if event.name == "run-appended" {
            assert_eq!(event.data, r#"{"runId":"run-1"}"#);
        }
        seen.insert(event.name);
    }
    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    loop {
        let runs = served.list_runs().await;
        if runs["controllerRuns"].as_array().map(Vec::len) == Some(1) && runs["controllerRuns"][0]["traces"] == 1 {
            break;
        }
        assert!(
            tokio::time::Instant::now() < deadline,
            "the listing never counted the trace: {runs}"
        );
        tokio::time::sleep(Duration::from_millis(100)).await;
    }

    // A new run's directory appears under the watched runs directory.
    write_journal(&runs_dir, "run-2", &[run_started("run-2", "run", &[])]);
    loop {
        let event = events.wait_for(&["run-appended"]).await;
        if event.data == r#"{"runId":"run-2"}"# {
            return;
        }
    }
}

/// A scenario's zip under `runs/<runId>/traces/<iterationId>/` is found under the id the controller computes for
/// it, and a zip that arrives while the server runs is announced.
#[tokio::test(flavor = "multi_thread")]
async fn the_vm_runs_root_finds_per_scenario_zips() {
    let fixture = new_fixture();
    let runtime_root = fixture.scratch.dir();
    let zipped = fs::read(&fixture.zip_path).expect("read the zip");
    let traces = runtime_root.join(VM_RUNS_DIR).join("run-1").join("traces");
    let first = traces.join("iter-1").join("001.zip");
    write_file(&first, &zipped);
    let roots = default_roots(None, Ok(&runtime_root));
    let mut settings = test_settings(&fixture.scratch, roots);
    // The zip that arrives has only the platform's watcher to announce it.
    settings.watch = native_watch();
    let served = start_server(settings).await;
    let found = scenario_by(&served.list_runs().await, |_| true);
    let want = bundle_id(SourceKind::Zip, &real(&first).to_string_lossy(), str_of(&found["source"]["entry"]));
    assert_eq!(found["id"], json!(want), "the id of its real zip and entry");
    let mut events = served.subscribe().await;

    let second = traces.join("iter-2").join("001.zip");
    fs::create_dir_all(second.parent().expect("a directory")).expect("create the iteration's directory");
    tokio::time::sleep(Duration::from_millis(100)).await;
    write_file(&second, &zipped);
    let deadline = tokio::time::Instant::now() + Duration::from_secs(15);
    loop {
        events.wait_for(&["runs-changed"]).await;
        let count = served.server.current().map_or(0, |current| current.found.bundles.len());
        if count == 2 {
            return;
        }
        assert!(
            tokio::time::Instant::now() < deadline,
            "runs-changed never announced the second zip"
        );
    }
}

// --- the idle stop -------------------------------------------------------------------------------------------

#[tokio::test(flavor = "multi_thread")]
async fn the_server_stops_when_idle_but_not_while_a_stream_is_open() {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    let idle = Duration::from_millis(300);
    settings.idle_exit = Some(idle);
    let server = Server::new(settings);
    let listener = TcpListener::bind(("127.0.0.1", 0)).await.expect("listen");
    let port = listener.local_addr().expect("an address").port();
    let mut stopped = tokio::spawn(Arc::clone(&server).run(listener, CancellationToken::new()));

    // An open stream keeps the server up for three idle periods.
    let mut stream = tokio::task::spawn_blocking(move || {
        let mut stream = TcpStream::connect(("127.0.0.1", port)).expect("connect to the server");
        write!(stream, "GET {EVENTS_ROUTE} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n\r\n").expect("send");
        let mut head = [0; 64];
        let read = stream.read(&mut head).expect("read the answer's head");
        assert!(
            head[..read].starts_with(b"HTTP/1.1 200"),
            "{:?}",
            String::from_utf8_lossy(&head[..read])
        );
        stream
    })
    .await
    .expect("open the stream");
    if let Ok(result) = tokio::time::timeout(3 * idle, &mut stopped).await {
        panic!("the server stopped with a stream open: {result:?}");
    }
    stream.flush().expect("the stream is open");
    drop(stream);
    let result = tokio::time::timeout(Duration::from_secs(30), stopped)
        .await
        .expect("the idle server did not stop")
        .expect("the server did not panic");
    assert!(result.is_ok(), "the idle server stopped with {result:?}");
}

/// A watcher whose every call blocks until the test lets it go, the way FSEvents blocks on a loaded fseventsd.
struct StalledWatcher {
    entered: std::sync::mpsc::Sender<()>,
    release: std::sync::mpsc::Receiver<()>,
}

impl StalledWatcher {
    fn stall(&self) {
        // A failed send means the test no longer waits; a failed receive means it let go.
        let _ = self.entered.send(());
        let _ = self.release.recv();
    }
}

impl notify::Watcher for StalledWatcher {
    fn new<F: notify::EventHandler>(_: F, _: notify::Config) -> notify::Result<Self> {
        Err(notify::Error::generic("a stalled watcher is made by its test"))
    }

    fn watch(&mut self, _: &Path, _: notify::RecursiveMode) -> notify::Result<()> {
        self.stall();
        Ok(())
    }

    fn unwatch(&mut self, _: &Path) -> notify::Result<()> {
        self.stall();
        Ok(())
    }

    fn kind() -> notify::WatcherKind {
        notify::WatcherKind::NullWatcher
    }
}

/// Dropping FSEvents' watcher joins its run loop, which waits for fseventsd too.
impl Drop for StalledWatcher {
    fn drop(&mut self) {
        self.stall();
    }
}

/// A listening server whose watcher is stuck in its first watch.
struct Stalled {
    port: u16,
    cancel: CancellationToken,
    stopped: tokio::task::JoinHandle<io::Result<()>>,
    /// Lets the watcher go when dropped.
    _release: std::sync::mpsc::Sender<()>,
    _scratch: Scratch,
}

async fn start_stalled(idle_exit: Option<Duration>) -> Stalled {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.idle_exit = idle_exit;
    let (entered_sender, entered) = std::sync::mpsc::channel();
    let (release, release_receiver) = std::sync::mpsc::channel();
    let watcher = Mutex::new(Some(StalledWatcher {
        entered: entered_sender,
        release: release_receiver,
    }));
    settings.watch = Arc::new(move |_| {
        let watcher = watcher.lock().expect("the watcher lock").take();
        match watcher {
            Some(watcher) => Ok(Box::new(watcher)),
            None => Err(notify::Error::generic("one watcher for each test")),
        }
    });
    let server = Server::new(settings);
    let listener = TcpListener::bind(("127.0.0.1", 0)).await.expect("listen");
    let port = listener.local_addr().expect("an address").port();
    let cancel = CancellationToken::new();
    let stopped = tokio::spawn(server.run(listener, cancel.clone()));
    let entered = tokio::task::spawn_blocking(move || entered.recv_timeout(Duration::from_secs(10)))
        .await
        .expect("the wait ran");
    assert!(entered.is_ok(), "the watcher was never asked to watch");
    Stalled {
        port,
        cancel,
        stopped,
        _release: release,
        _scratch: scratch,
    }
}

impl Stalled {
    async fn wait_stopped(self, budget: Duration) {
        let result = tokio::time::timeout(budget, self.stopped)
            .await
            .expect("the server waited for its watcher to stop")
            .expect("the server did not panic");
        assert!(result.is_ok(), "the server stopped with {result:?}");
    }
}

/// The listing and the cancel do not wait for a watcher stuck in fseventsd.
#[tokio::test(flavor = "multi_thread")]
async fn the_server_stops_while_its_watcher_is_stalled() {
    let stalled = start_stalled(None).await;
    let port = stalled.port;
    let body = tokio::time::timeout(
        Duration::from_secs(5),
        tokio::task::spawn_blocking(move || http_get(port, RUNS_ROUTE)),
    )
    .await
    .expect("the listing waited for the watcher")
    .expect("the listing was read");
    assert!(body.contains("generation"), "{body}");
    stalled.cancel.cancel();
    stalled.wait_stopped(SHUTDOWN_GRACE + Duration::from_secs(2)).await;
}

/// The idle stop does not wait for a watcher stuck in fseventsd.
#[tokio::test(flavor = "multi_thread")]
async fn the_idle_server_stops_while_its_watcher_is_stalled() {
    let idle = Duration::from_millis(300);
    let stalled = start_stalled(Some(idle)).await;
    stalled.wait_stopped(idle + SHUTDOWN_GRACE + Duration::from_secs(2)).await;
}

// --- the site build ------------------------------------------------------------------------------------------

/// A docs project with one source file, touched at the given time.
fn site_project(scratch: &Scratch, source_time: SystemTime) -> PathBuf {
    let project = scratch.dir();
    write_file(&project.join("package.json"), b"{}");
    let source = project.join(SITE_SOURCES_DIR).join("main.tsx");
    write_file(&source, b"export {}");
    set_modified(&source, source_time);
    project
}

fn build_site(
    run: impl Fn(PathBuf, state::BuildOutput) -> futures::future::BoxFuture<'static, Result<(), Refusal>> + Send + Sync + 'static,
) -> state::BuildSite {
    Arc::new(move |project, output, _cancel| run(project, output))
}

#[tokio::test(flavor = "multi_thread")]
async fn the_site_is_built_once_while_the_placeholder_says_so() {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.site_project = Some(site_project(&scratch, SystemTime::now()));
    let site = settings.site_dir.clone().expect("the test names a site");
    let release = Arc::new(tokio::sync::Notify::new());
    let builds = Arc::new(AtomicUsize::new(0));
    settings.build_site = {
        let (release, builds) = (Arc::clone(&release), Arc::clone(&builds));
        build_site(move |_, output| {
            let (release, builds, site) = (Arc::clone(&release), Arc::clone(&builds), site.clone());
            Box::pin(async move {
                builds.fetch_add(1, Ordering::SeqCst);
                output("vite building for production...\n");
                release.notified().await;
                write_file(&site.join("index.html"), b"<!doctype html><title>air</title>");
                Ok(())
            })
        })
    };
    let served = start_server(settings).await;
    let building = tokio::spawn(Arc::clone(&served.server).build_site_once(CancellationToken::new()));
    let html = [("accept", "text/html")];
    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    loop {
        let page = served.get("/air/runs", &html).await.text();
        if page.contains("is building") {
            assert!(page.contains("vite building for production"), "{page}");
            assert!(page.contains(r#"http-equiv="refresh""#), "{page}");
            break;
        }
        assert!(
            tokio::time::Instant::now() < deadline,
            "the placeholder never said the site is building: {page}"
        );
        tokio::time::sleep(Duration::from_millis(20)).await;
    }
    release.notify_one();
    building.await.expect("the build did not panic");
    let page = served.get("/air/runs", &html).await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(
        page.text().contains("<title>air</title>"),
        "after the build /air/runs answered {}",
        page.text()
    );
    Arc::clone(&served.server).build_site_once(CancellationToken::new()).await;
    assert_eq!(builds.load(Ordering::SeqCst), 1, "a current site is not built again");
}

#[tokio::test(flavor = "multi_thread")]
async fn a_failed_site_build_says_why() {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.site_project = Some(site_project(&scratch, SystemTime::now()));
    settings.build_site = build_site(|project, output| {
        Box::pin(async move {
            output("error: harvest failed on Foo.kt\n");
            Err(Refusal::new(
                code::SITE_BUILD_FAILED,
                Exit::Broken,
                format!("pnpm --dir {} build: exit status 1", project.display()),
            ))
        })
    });
    let served = start_server(settings).await;
    Arc::clone(&served.server).build_site_once(CancellationToken::new()).await;
    let page = served.get("/air/runs", &[("accept", "text/html")]).await.text();
    for want in ["did not build", "exit status 1", "harvest failed on Foo.kt"] {
        assert!(page.contains(want), "the failure placeholder lacks {want:?}: {page}");
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn a_current_site_is_not_built() {
    let scratch = Scratch::default();
    let mut settings = test_settings(&scratch, vec![Root::new(RootKind::Flag, scratch.dir())]);
    settings.site_project = Some(site_project(&scratch, SystemTime::now() - Duration::from_hours(1)));
    let site = settings.site_dir.clone().expect("the test names a site");
    write_file(&site.join("index.html"), b"<!doctype html>");
    let built = Arc::new(AtomicBool::new(false));
    settings.build_site = {
        let built = Arc::clone(&built);
        build_site(move |_, _| {
            built.store(true, Ordering::SeqCst);
            Box::pin(async { Ok(()) })
        })
    };
    let served = start_server(settings).await;
    Arc::clone(&served.server).build_site_once(CancellationToken::new()).await;
    assert!(!built.load(Ordering::SeqCst), "a current site was built");

    let stale = site_project(&scratch, SystemTime::now() + Duration::from_hours(1));
    assert_eq!(
        site_needs_build(Some(&stale), Some(&site)),
        Some("the site is older than its sources")
    );
    assert_eq!(
        site_needs_build(Some(&scratch.dir()), Some(&site)),
        None,
        "a directory without package.json"
    );
}

// --- detaching -----------------------------------------------------------------------------------------------

/// Makes this test binary run `air-trace serve` when [detach] starts it again.
const CHILD_ENVIRONMENT: &str = "AIR_TRACE_SERVE_TEST_CHILD";

/// Not a test: the server a detach test starts, by running this binary again with this function as its only test.
/// The serve arguments follow `--`, where libtest reads more name filters, which match no other test. Without the
/// environment the parent sets, it does nothing.
#[test]
#[ignore = "the detached server of detach_starts_one_server_that_stops_when_idle"]
fn serve_child() {
    if std::env::var_os(CHILD_ENVIRONMENT).is_none() {
        return;
    }
    let arguments: Vec<String> = std::env::args().skip_while(|arg| arg != "--").skip(1).collect();
    let args = serve_args(&arguments.iter().map(String::as_str).collect::<Vec<_>>()).expect("the arguments parse");
    let runtime = tokio::runtime::Builder::new_multi_thread().enable_all().build().expect("a runtime");
    let code = runtime.block_on(async {
        let cancel = CancellationToken::new();
        tokio::spawn(stop_on_signal(cancel.clone()));
        serve(args, &test_program(), Context::from_process(), cancel, Streams::stdio()).await
    });
    std::process::exit(i32::from(code));
}

#[cfg(unix)]
fn free_port() -> u16 {
    std::net::TcpListener::bind(("127.0.0.1", 0))
        .and_then(|listener| listener.local_addr())
        .expect("a free port")
        .port()
}

/// Stops the detached server a test left behind.
#[cfg(unix)]
struct KillOnDrop(PathBuf);

#[cfg(unix)]
impl Drop for KillOnDrop {
    fn drop(&mut self) {
        if let Some(pid) = fs::read_to_string(&self.0).ok().and_then(|pid| pid.trim().parse().ok()) {
            let _ = nix::sys::signal::kill(nix::unistd::Pid::from_raw(pid), nix::sys::signal::Signal::SIGTERM);
        }
    }
}

/// `--detach` starts one server in the background, finds it on the next call, and the server stops by itself once
/// idle, removing its pid file.
#[cfg(unix)]
#[test]
fn detach_starts_one_server_that_stops_when_idle() {
    let scratch = Scratch::default();
    let runtime_root = scratch.dir();
    let repo = scratch.dir();
    let request = DetachRequest {
        port: free_port(),
        site: None,
        roots: vec![scratch.dir()],
        no_default_roots: true,
        idle_exit: Duration::from_secs(2),
    };
    let pid_file = viewer_dir(&runtime_root).join(SERVE_PID_FILE);
    let _cleanup = KillOnDrop(pid_file.clone());
    let command: Vec<OsString> = [
        std::env::current_exe().expect("this test binary").into_os_string(),
        "serve::tests::service::serve_child".into(),
        "--exact".into(),
        "--ignored".into(),
        "--nocapture".into(),
        "--".into(),
    ]
    .into();
    let runner = Runner::new(
        std::env::vars().chain([
            (CHILD_ENVIRONMENT.to_owned(), "1".to_owned()),
            ("BUILD_WORKSPACE_DIRECTORY".to_owned(), repo.to_string_lossy().into_owned()),
        ]),
        Interrupts::detached(),
    );

    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let code = detach(&request, Ok(runtime_root.clone()), &runner, &command, &mut stdout, &mut stderr);
    let printed = String::from_utf8_lossy(&stdout).into_owned();
    assert_eq!(code, 0, "{printed}{}", String::from_utf8_lossy(&stderr));
    assert_eq!(
        printed,
        format!("air-trace serve: {}\n", avl_trace_tools::viewer::runs_url(request.port))
    );
    let content = fs::read_to_string(&pid_file).expect("the detached server wrote its pid file");
    let pid: u32 = content.trim().parse().expect("the pid file holds a pid");
    assert_ne!(pid, std::process::id());

    let mut again = Vec::new();
    let code = detach(&request, Ok(runtime_root.clone()), &runner, &command, &mut again, &mut stderr);
    let again = String::from_utf8_lossy(&again);
    assert!(
        code == 0 && again.contains("already running"),
        "a second detach exited {code} with {again}"
    );

    // A probe is a request too, so the wait watches the pid file and probes once at its end. The budget is the idle
    // stop, the grace for open connections, and room for a loaded machine.
    let deadline = std::time::Instant::now() + request.idle_exit + SHUTDOWN_GRACE + Duration::from_secs(10);
    while pid_file.exists() {
        assert!(std::time::Instant::now() < deadline, "the detached server did not stop when idle");
        std::thread::sleep(Duration::from_millis(100));
    }
    assert!(
        !probe_viewer(request.port, Duration::from_secs(1)),
        "the server removed its pid file and still answers"
    );
    let log = fs::read_to_string(viewer_dir(&runtime_root).join(SERVE_LOG_FILE)).unwrap_or_default();
    assert!(log.contains("stopping"), "the serve log says {log:?}");
}

#[tokio::test(flavor = "multi_thread")]
async fn detach_needs_a_fixed_port() {
    let scratch = Scratch::default();
    let root = scratch.dir().to_string_lossy().into_owned();
    let (out, err) = (Captured::default(), Captured::default());
    let code = run_serve(
        &["--detach", "--port", "0", "--root", &root],
        &scratch,
        CancellationToken::new(),
        &out,
        &err,
    )
    .await;
    assert_eq!(code, 2, "{:?}", err.lines());
}

// --- the site build's steps ----------------------------------------------------------------------------------

#[test]
fn the_site_is_installed_before_it_is_built_when_its_dependencies_are_missing() {
    let tools = SiteTools::Pinned {
        node: PathBuf::from("/tools/node/bin/node"),
        pnpm: PathBuf::from("/tools/pnpm/pnpm"),
    };
    let project = Path::new("/repo/plugins/air/docs");

    let steps = site_steps(&tools, project, true);
    assert_eq!(
        steps.iter().map(|step| step.label.as_str()).collect::<Vec<_>>(),
        [
            "pnpm --dir /repo/plugins/air/docs install --frozen-lockfile",
            "pnpm --dir /repo/plugins/air/docs build",
        ]
    );
    for step in &steps {
        assert_eq!(step.program, PathBuf::from("/tools/pnpm/pnpm"));
        assert_eq!(
            step.path_prefix.as_deref(),
            Some(Path::new("/tools/node/bin")),
            "the pinned node's directory goes first on the PATH"
        );
    }
    assert_eq!(
        steps[0].args,
        ["--dir", "/repo/plugins/air/docs", "install", "--frozen-lockfile"].map(OsString::from)
    );

    let built_only = site_steps(&tools, project, false);
    assert_eq!(
        built_only.iter().map(|step| step.label.as_str()).collect::<Vec<_>>(),
        ["pnpm --dir /repo/plugins/air/docs build"],
        "a project with node_modules is not installed again"
    );
}

#[test]
fn outside_a_checkout_the_site_is_built_with_the_pnpm_on_the_path() {
    let steps = site_steps(&SiteTools::Path, Path::new("/docs"), false);
    assert_eq!(steps.len(), 1);
    assert_eq!(steps[0].program, PathBuf::from(PATH_PNPM));
    assert_eq!(steps[0].path_prefix, None, "the PATH is left as it is");
}

/// A script at `path`, executable.
#[cfg(unix)]
fn write_script(path: &Path, text: &str) {
    use std::os::unix::fs::PermissionsExt;
    write_file(path, text.as_bytes());
    fs::set_permissions(path, fs::Permissions::from_mode(0o755)).expect("chmod");
}

/// A build output that keeps every chunk.
#[cfg(unix)]
fn kept_output() -> (state::BuildOutput, Arc<Mutex<Vec<String>>>) {
    let lines = Arc::new(Mutex::new(Vec::new()));
    let kept = Arc::clone(&lines);
    let output: state::BuildOutput = Arc::new(move |chunk: &str| {
        kept.lock().expect("the output lock").push(chunk.to_owned());
    });
    (output, lines)
}

// The pinned pnpm runs both steps in order, and its scripts see the pinned node first on the PATH.
#[cfg(unix)]
#[tokio::test(flavor = "multi_thread")]
async fn the_pinned_pnpm_runs_each_step_with_the_pinned_node_first_on_the_path() {
    let scratch = Scratch::default();
    let node = scratch.dir().join("tools/node/bin/node");
    write_script(&node, "#!/bin/sh\nexit 0\n");
    let calls = scratch.dir().join("calls");
    let pnpm = scratch.dir().join("tools/pnpm/pnpm");
    write_script(
        &pnpm,
        &format!("#!/bin/sh\necho \"$*\" >> {calls}\necho \"PATH=$PATH\"\n", calls = calls.display()),
    );
    let project = scratch.dir().join("docs");
    fs::create_dir_all(&project).expect("the project");
    let (output, lines) = kept_output();

    let tools = SiteTools::Pinned { node: node.clone(), pnpm };
    build_site_with(&tools, &project, true, &output, &CancellationToken::new())
        .await
        .expect("both steps pass");

    assert_eq!(
        fs::read_to_string(&calls).expect("pnpm was called"),
        format!(
            "--dir {project} install --frozen-lockfile\n--dir {project} build\n",
            project = project.display()
        )
    );
    let joined = lines.lock().expect("the output lock").join("\n");
    let node_dir = node.parent().expect("bin").display().to_string();
    assert!(
        joined.contains(&format!("PATH={node_dir}:")),
        "pnpm's scripts must find the pinned node first: {joined}"
    );
    assert!(
        joined.contains(&format!("$ pnpm --dir {} install --frozen-lockfile", project.display())),
        "each step is announced: {joined}"
    );
}

// A step that fails names itself with its exit status, and the next step does not run.
#[cfg(unix)]
#[tokio::test(flavor = "multi_thread")]
async fn a_failing_install_stops_the_build_and_names_the_step() {
    let scratch = Scratch::default();
    let node = scratch.dir().join("node");
    write_script(&node, "#!/bin/sh\nexit 0\n");
    let calls = scratch.dir().join("calls");
    let pnpm = scratch.dir().join("pnpm");
    write_script(
        &pnpm,
        &format!(
            "#!/bin/sh\necho \"$*\" >> {calls}\n[ \"$3\" = install ] && exit 3\nexit 0\n",
            calls = calls.display()
        ),
    );
    let project = scratch.dir().join("docs");
    fs::create_dir_all(&project).expect("the project");
    let (output, _lines) = kept_output();

    let tools = SiteTools::Pinned { node, pnpm };
    let failed = build_site_with(&tools, &project, true, &output, &CancellationToken::new())
        .await
        .expect_err("the install fails");

    assert_eq!(failed.code, code::SITE_BUILD_FAILED);
    assert_eq!(
        failed.message,
        format!("pnpm --dir {} install --frozen-lockfile: exit status: 3", project.display())
    );
    assert_eq!(
        fs::read_to_string(&calls).expect("pnpm was called").lines().count(),
        1,
        "the build did not run after the failed install"
    );
}

// --- the contract with the site ------------------------------------------------------------------------------

/// The transcript of the routes the docs site reads, a file of the trace testdata: each request, its status, the
/// headers the site relies on and the shape of its answer, then the first frame of the event stream and a
/// `bundle-append`. The site's `src/trace/runJournal.test.ts` reads the same file. `UPDATE_EXPECT=1` rewrites it.
const SERVE_TRANSCRIPT: &str = "serve-transcript.json";

/// The headers a transcript keeps. An `ETag` and a `Last-Modified` change from one run to the next.
const TRANSCRIPT_HEADERS: [&str; 5] = ["accept-ranges", "cache-control", "content-range", "content-type", "x-air-trace"];

/// The shape of a JSON value: the type name of a scalar, the shape of each field of an object, and the shapes of the
/// elements of an array merged into one.
fn shape(value: &Value) -> Value {
    match value {
        Value::Null => json!("null"),
        Value::Bool(_) => json!("boolean"),
        Value::Number(_) => json!("number"),
        Value::String(_) => json!("string"),
        Value::Array(items) => Value::Array(items.iter().map(shape).reduce(merge).into_iter().collect()),
        Value::Object(fields) => Value::Object(fields.iter().map(|(name, value)| (name.clone(), shape(value))).collect()),
    }
}

/// Two shapes as one: the fields of both objects, the merged elements of both arrays, and otherwise both shapes as
/// `{"oneOf": [...]}` in a fixed order.
fn merge(left: Value, right: Value) -> Value {
    match (left, right) {
        (left, right) if left == right => left,
        (Value::Object(mut left), Value::Object(right)) if !left.contains_key("oneOf") && !right.contains_key("oneOf") => {
            for (name, value) in right {
                let merged = match left.remove(&name) {
                    Some(existing) => merge(existing, value),
                    None => value,
                };
                left.insert(name, merged);
            }
            Value::Object(left)
        }
        (Value::Array(left), Value::Array(right)) => Value::Array(left.into_iter().chain(right).reduce(merge).into_iter().collect()),
        (left, right) => {
            let alternatives = |value: Value| match value {
                Value::Object(mut fields) if fields.contains_key("oneOf") => match fields.remove("oneOf") {
                    Some(Value::Array(items)) => items,
                    _ => Vec::new(),
                },
                other => vec![other],
            };
            let mut all: Vec<Value> = alternatives(left).into_iter().chain(alternatives(right)).collect();
            all.sort_by_key(Value::to_string);
            all.dedup();
            json!({ "oneOf": all })
        }
    }
}

/// One answer of the transcript: the request as the site sends it, the status, the kept headers, and the shape of a
/// whole JSON body or the size of any other body, a range of a JSON Lines file included.
fn transcribed(request: &str, answer: &Answer) -> Value {
    let headers: serde_json::Map<String, Value> = TRANSCRIPT_HEADERS
        .iter()
        .filter_map(|name| {
            let value = answer.headers.get(*name)?.to_str().ok()?;
            // The size of a golden bundle file is the recorder's to change, not the server's.
            let value = match value.split_once('/') {
                Some((range, _)) if *name == "content-range" => format!("{range}/<size>"),
                _ => value.to_owned(),
            };
            Some(((*name).to_owned(), json!(value)))
        })
        .collect();
    let body = if answer.status != StatusCode::PARTIAL_CONTENT && answer.header(header::CONTENT_TYPE).starts_with("application/json") {
        shape(&answer.json())
    } else {
        json!(format!("{} bytes", answer.body.len()))
    };
    json!({ "request": request, "status": answer.status.as_u16(), "headers": headers, "body": body })
}

/// One frame of the event stream in the transcript: its name and the shape of its data.
fn transcribed_frame(frame: &SseEvent) -> Value {
    let data: Value = serde_json::from_str(&frame.data).unwrap_or_else(|error| panic!("{} carried {}: {error}", frame.name, frame.data));
    json!({ "event": frame.name, "data": shape(&data) })
}

// Every route the site reads answers what the transcript holds: the site's test reads the same file, so a change of a
// path, a header or a field on either side fails a test.
#[tokio::test(flavor = "multi_thread")]
async fn the_routes_answer_what_the_site_reads() {
    let fixture = new_fixture();
    let runs_dir = fixture.scratch.dir();
    write_journal(
        &runs_dir,
        "run-1",
        &[run_started("run-1", "run", &["flow-x"]), Progress::Note("leasing".to_owned())],
    );
    let mut settings = test_settings(&fixture.scratch, fixture_roots(&fixture));
    settings.runs_dir = Some(runs_dir);
    let manifest = fixture.manifest.clone();
    settings.resolve = Arc::new(move |_: &Path, input: crate::plan::Input| {
        if input.text == "nothing" {
            anyhow::bail!("nothing maps nothing");
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
    });
    let served = start_server(settings).await;
    let runs = served.list_runs().await;
    let directory = str_of(&scenario_by(&runs, |scenario| scenario["runId"] == "run-dir")["url"]).to_owned();
    let zipped = str_of(&scenario_by(&runs, is_zip)["url"]).to_owned();
    let json_post = |body: &str| {
        request(
            Method::POST,
            PLAN_ROUTE,
            &[("content-type", "application/json")],
            Body::from(body.to_owned()),
        )
    };
    let file_post = request(
        Method::POST,
        &format!("{PLAN_ROUTE}?fileName=flow-x.txt"),
        &[("content-type", "application/octet-stream")],
        Body::from("flow text"),
    );

    let routes = vec![
        transcribed(&format!("GET {RUNS_ROUTE}"), &served.get(RUNS_ROUTE, &[]).await),
        transcribed(
            &format!("GET {RUN_ROUTE}<runId>?from=0"),
            &served.get(&format!("{RUN_ROUTE}run-1?from=0"), &[]).await,
        ),
        transcribed(&format!("GET {BUNDLE_ROUTE}<directory id>/"), &served.get(&directory, &[]).await),
        transcribed(
            &format!("GET {BUNDLE_ROUTE}<directory id>/{MANIFEST_FILE}"),
            &served.get(&format!("{directory}{MANIFEST_FILE}"), &[]).await,
        ),
        transcribed(
            &format!("GET {BUNDLE_ROUTE}<directory id>/{SPANS_FILE} Range: bytes=0-9"),
            &served.get(&format!("{directory}{SPANS_FILE}"), &[("range", "bytes=0-9")]).await,
        ),
        transcribed(&format!("GET {BUNDLE_ROUTE}<zip id>/"), &served.get(&zipped, &[]).await),
        transcribed(
            &format!("GET {BUNDLE_ROUTE}<zip id>/{} Range: bytes=0-3", snap_image_path(1)),
            &served
                .get(&format!("{zipped}{}", snap_image_path(1)), &[("range", "bytes=0-3")])
                .await,
        ),
        transcribed(
            &format!("POST {PLAN_ROUTE} {{text}}"),
            &served.send(json_post(r#"{"text":"flow-x"}"#)).await,
        ),
        transcribed(
            &format!("POST {PLAN_ROUTE}?fileName=<name> application/octet-stream"),
            &served.send(file_post).await,
        ),
        transcribed(
            &format!("POST {PLAN_ROUTE} {{text}} refused"),
            &served.send(json_post(r#"{"text":"nothing"}"#)).await,
        ),
    ];

    let mut events = served.subscribe().await;
    let opened = transcribed(&format!("GET {EVENTS_ROUTE}"), &events.opened);
    let ready = transcribed_frame(events.ready.as_ref().expect("the stream opened with ready"));
    let mut logs = fs::OpenOptions::new()
        .append(true)
        .open(fixture.running_dir.join(LOGS_FILE))
        .expect("open the running bundle's logs");
    logs.write_all(b"{\"appended\":1}\n").expect("append");
    drop(logs);
    let appended = transcribed_frame(&events.wait_for(&["bundle-append"]).await);

    let transcript = json!({ "routes": routes, "events": { "opened": opened, "frames": [ready, appended] } });
    let text = serde_json::to_string_pretty(&transcript).expect("the transcript encodes") + "\n";
    let path = std::path::absolute(avl_testkit::traces::path(SERVE_TRANSCRIPT)).expect("an absolute path");
    expect_test::expect_file![path].assert_eq(&text);
}
