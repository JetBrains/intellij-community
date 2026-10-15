use std::collections::BTreeSet;
use std::fs;
use std::net::{Ipv4Addr, SocketAddr};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use avl_wire::daemon::{NdjsonDecoder, RunEventKind, decode_state_file, decode_status};
use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;
use crate::lane::services::testing::{Answer, call};

const LAUNCH_DIGEST: &str = "launch-1";

fn inputs(root: &Path) -> LaunchInputs {
    LaunchInputs {
        ide_root: root.join("ide"),
        home: root.join("home"),
        dist_home: root.join("dist"),
        ide_config: root.join("dist.properties"),
        java_home: root.join("dist/jbr"),
        flags_file: root.join("flags.txt"),
        project_archive: root.join("project.zip"),
        project_root: "plain-project".to_owned(),
        runtimes: Vec::new(),
        distribution_plugins: BTreeSet::new(),
        trace: inputs::TraceInputs::default(),
    }
}

/// A booted daemon on a free loopback port, its channel and its published state file.
struct Booted {
    root: TempDir,
    daemon: Arc<Daemon>,
    channel: Service<Channel>,
    state: StateFile,
}

fn booted() -> Booted {
    let root = TempDir::new().unwrap();
    let state_dir = root.path().join("state");
    fs::create_dir_all(state_dir.join("jars")).unwrap();
    fs::write(state_dir.join("jars").join("stale"), "from the previous boot").unwrap();
    fs::write(state_dir.join(STATE_FILE), "{}").unwrap();
    let args = LaneDaemonArgs {
        state_dir: state_dir.clone(),
        port: 1,
        bind: Ipv4Addr::LOCALHOST.into(),
        launch_digest: LAUNCH_DIGEST.to_owned(),
        descriptor: root.path().join("ui_daemon.runtime.json"),
        runfiles_root: root.path().join("runfiles"),
        ide_root: root.path().join("ide"),
        home: root.path().join("home"),
    };
    let daemon = Arc::new(boot(&args, inputs(root.path())).unwrap());
    assert!(
        !state_dir.join(STATE_FILE).exists(),
        "the boot removes the state file of the previous daemon"
    );
    assert!(!state_dir.join("jars").join("stale").exists(), "the boot empties the jar store");
    let channel = bind(&daemon, SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).unwrap();
    publish_state_file(&daemon, channel.address().port()).unwrap();
    let published = fs::read(state_dir.join(STATE_FILE)).unwrap();
    let state = decode_state_file(&published).unwrap();
    Booted {
        root,
        daemon,
        channel,
        state,
    }
}

impl Booted {
    fn call(&self, method: &str, target: &str, body: &[u8]) -> Answer {
        call(self.channel.address(), method, target, &[(TOKEN_HEADER, &self.state.token)], body)
    }

    fn state_dir(&self) -> PathBuf {
        self.root.path().join("state")
    }
}

fn run_request(launch_digest: &str) -> Vec<u8> {
    json!({
        "selectors": [], "junit5Filters": [], "productStamp": "product-1", "launchDigest": launch_digest,
        "hotJars": [], "activeExecutionTimeoutSec": 1800, "progressGapTimeoutSec": 300, "idePlugins": [],
    })
    .to_string()
    .into_bytes()
}

// The state file is private to the account and names the protocol, the bound port and the token. The status answers
// the fields of protocol 6, and a request with another token is refused.
#[test]
fn the_state_file_names_the_channel_and_the_status_answers_protocol_6() {
    let booted = booted();
    let mode = fs::metadata(booted.state_dir().join(STATE_FILE)).unwrap().permissions().mode() & 0o777;
    assert_eq!(mode, 0o600);
    assert_eq!(
        (
            booted.state.protocol_version,
            booted.state.port,
            booted.state.daemon_boot_stamp.len()
        ),
        (PROTOCOL_VERSION, booted.channel.address().port(), 32)
    );
    let answer = booted.call("GET", "/status", b"");
    assert_eq!(answer.status, 200);
    let status = decode_status(&answer.body()).unwrap();
    assert_eq!(
        (
            status.protocol_version,
            status.controller_launch_digest.as_str(),
            status.busy,
            status.ide_running
        ),
        (6, LAUNCH_DIGEST, false, false)
    );
    assert_eq!(status.daemon_boot_stamp, booted.state.daemon_boot_stamp);
    let refused = call(booted.channel.address(), "GET", "/status", &[(TOKEN_HEADER, "wrong")], b"");
    assert_eq!((refused.status, refused.json()), (401, json!({"error": "bad token"})));
    let unknown = booted.call("POST", "/mount/quiesce", b"");
    assert_eq!(unknown.status, 404);
}

// The push asks which digests the daemon lacks, and a pushed file is stored only under the digest of its bytes.
#[test]
fn the_jar_push_stores_a_file_under_the_digest_of_its_bytes() {
    let booted = booted();
    let digest = sha256_hex(b"jar bytes");
    let other = sha256_hex(b"other");
    let offer = json!({ "jars": [digest, other] }).to_string();
    assert_eq!(
        booted.call("POST", "/jars", offer.as_bytes()).json(),
        json!({ "missing": [digest, other] })
    );
    let wrong = booted.call("PUT", &format!("/jars/{digest}"), b"other bytes");
    assert_eq!(wrong.status, 400);
    assert!(wrong.text().contains("not ") && wrong.text().contains(&digest), "{}", wrong.text());
    let stored = booted.call("PUT", &format!("/jars/{digest}"), b"jar bytes");
    assert_eq!((stored.status, stored.json()), (200, json!({ "sha256": digest })));
    assert_eq!(booted.call("POST", "/jars", offer.as_bytes()).json(), json!({ "missing": [other] }));
    assert_eq!(
        booted.daemon.stored(&digest).unwrap(),
        booted.state_dir().join("jars").join(&digest)
    );
    assert!(booted.daemon.stored(&other).unwrap_err().contains("holds no file"));
    assert_eq!(booted.call("GET", "/results/iter-1/test.xml", b"").status, 404);
}

/// The records of a `/run` answer.
fn records(answer: &Answer) -> Vec<RunEventKind> {
    assert_eq!(answer.header("Transfer-Encoding"), Some("chunked"));
    let decoded = NdjsonDecoder::default().push(&answer.body());
    assert_eq!(decoded.failure, None);
    decoded.events.into_iter().map(|event| event.kind).collect()
}

// A run of another launch generation is refused with the generation the daemon serves. A run that names no lane
// streams a failed iteration: the start, the failure, the end of the watchdog, and a summary with failures.
#[test]
fn a_run_streams_its_records_and_a_stale_one_is_refused() {
    let booted = booted();
    let stale = booted.call("POST", "/run", &run_request("launch-0"));
    assert_eq!(
        (stale.status, stale.json()),
        (409, json!({ "reason": "stale-daemon", "launchDigest": LAUNCH_DIGEST }))
    );
    let answer = booted.call("POST", "/run", &run_request(LAUNCH_DIGEST));
    assert_eq!(answer.status, 200);
    let kinds: Vec<&str> = records(&answer)
        .iter()
        .map(|record| match record {
            RunEventKind::RunStarted(_) => "runStarted",
            RunEventKind::RunFailed(failed) => {
                assert!(failed.error.contains("names no lane"), "{}", failed.error);
                "runFailed"
            }
            RunEventKind::WatchdogState(state) => {
                assert_eq!(state.phase, "completed");
                "watchdogState"
            }
            RunEventKind::Summary(summary) => {
                assert!(summary.has_failures);
                assert_eq!(summary.daemon_boot_stamp, booted.state.daemon_boot_stamp);
                "summary"
            }
            other => panic!("an unexpected record {other:?}"),
        })
        .collect();
    assert_eq!(kinds, ["runStarted", "runFailed", "watchdogState", "summary"]);
    let status = decode_status(&booted.call("GET", "/status", b"").body()).unwrap();
    assert_eq!(
        (status.iteration_count, status.busy, status.product_stamp.as_str()),
        (1, false, "product-1")
    );
    let iteration = format!("iter-{}-1", &booted.state.daemon_boot_stamp[..8]);
    assert!(booted.state_dir().join("iterations").join(iteration).is_dir());
}

// A run is refused while another holds the slot, and an IDE stop of a daemon that holds no IDE has nothing to quit.
#[test]
fn one_run_at_a_time_and_an_ide_stop_without_an_ide() {
    let booted = booted();
    booted.daemon.busy.store(true, Ordering::SeqCst);
    let busy = booted.call("POST", "/run", &run_request(LAUNCH_DIGEST));
    assert_eq!((busy.status, busy.json()), (409, json!({ "reason": "busy" })));
    assert_eq!(booted.call("POST", "/ide/stop", b"").status, 409);
    booted.daemon.busy.store(false, Ordering::SeqCst);
    let stopped = booted.call("POST", "/ide/stop", b"");
    assert_eq!((stopped.status, stopped.json()), (200, json!({ "ideRunning": false })));
}

// A shutdown answers at once, wakes the main thread of the verb, and refuses every later run.
#[test]
fn a_shutdown_wakes_the_verb_and_refuses_later_runs() {
    let booted = booted();
    let answer = booted.call("POST", "/shutdown", b"");
    assert_eq!((answer.status, answer.json()), (200, json!({ "shuttingDown": true })));
    booted.daemon.await_shutdown();
    let refused = booted.call("POST", "/run", &run_request(LAUNCH_DIGEST));
    assert_eq!((refused.status, refused.json()), (409, json!({ "reason": "shutting-down" })));
}
