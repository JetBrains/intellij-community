use std::path::PathBuf;
use std::sync::{Arc, Condvar, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, Instant};

use avl_testkit::http::{FakeHttp, Request, Response};
use avl_trace::bridge::TOKEN_HEADER;
use pretty_assertions::assert_eq;

use super::*;
use crate::{ErrorKind, url};

const TOKEN: &str = "secret-token";
const LAUNCH: &str = "air-ui-lane";
/// The digest of the bridge plugin in the fixture of `AirUiTestLifecycleLongPollTest`.
const BRIDGE_DIGEST: &str = "d8d86ec0589420a757bd691c65bc8e3c559a4416444c2bb60724994df6bb098c";

fn state(phase: Phase) -> State {
    State {
        schema_version: SCHEMA_VERSION,
        phase,
        launch: Some(LAUNCH.to_owned()),
        pid: 4321,
        projects: vec!["plain-project".to_owned()],
        indexing: false,
        background_tasks: 0,
        bridge_plugin: None,
        vocabulary_digest: None,
        control_bind_failure: None,
    }
}

/// The published state of the fake IDE and its sequence number.
struct Published {
    seq: u64,
    state: State,
}

/// The IDE's half of the long poll, with the rules of `AirUiTestLifecycleSnapshots.awaitAfter`: an answer at once when
/// `seq` is not `after`, else at the next change or when the wait, cut to `cap`, ends.
struct FakeIde {
    published: Mutex<Published>,
    changed: Condvar,
    cap: Duration,
    /// The routes of the polls, in their order.
    routes: Mutex<Vec<String>>,
    /// The count of the next polls that the bridge refuses with 503, as a bridge that unloads does.
    outage: Mutex<u32>,
}

impl FakeIde {
    fn start(initial: State, cap: Duration) -> (Arc<Self>, FakeHttp) {
        let ide = Arc::new(Self {
            published: Mutex::new(Published { seq: 1, state: initial }),
            changed: Condvar::new(),
            cap,
            routes: Mutex::new(Vec::new()),
            outage: Mutex::new(0),
        });
        let server = {
            let ide = ide.clone();
            FakeHttp::start(move |request| ide.answer(request))
        };
        (ide, server)
    }

    fn lock(&self) -> MutexGuard<'_, Published> {
        self.published.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Publishes `state` as the next snapshot. An equal state is no new snapshot, as on the Kotlin side.
    fn publish(&self, state: State) {
        let mut published = self.lock();
        if published.state != state {
            published.seq += 1;
            published.state = state;
            self.changed.notify_all();
        }
    }

    /// Publishes `state` two sequence numbers on: a state went past that no reader saw.
    fn publish_past_a_missed_state(&self, state: State) {
        let mut published = self.lock();
        published.seq += 2;
        published.state = state;
        self.changed.notify_all();
    }

    /// Stands in for a new IDE process: its sequence starts at 1 again.
    fn restart(&self, state: State) {
        let mut published = self.lock();
        published.seq = 1;
        published.state = state;
        self.changed.notify_all();
    }

    fn routes(&self) -> Vec<String> {
        self.routes.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    fn answer(&self, request: &Request) -> Response {
        self.routes
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .push(request.path.clone());
        if request.header(TOKEN_HEADER) != Some(TOKEN) {
            return Response::error(401, "unsigned Air UI-test lifecycle request");
        }
        let mut outage = self.outage.lock().unwrap_or_else(PoisonError::into_inner);
        if *outage > 0 {
            *outage -= 1;
            return Response::error(503, "the bridge unloads");
        }
        drop(outage);
        let (mut after, mut wait) = (None, Duration::ZERO);
        let query = request.path.split_once('?').map_or("", |(_, query)| query);
        for pair in query.split('&').filter(|pair| !pair.is_empty()) {
            match pair.split_once('=') {
                Some(("after", value)) => after = Some(value.parse::<u64>().expect("a whole after")),
                Some(("wait", value)) => wait = Duration::from_millis(value.parse().expect("a whole wait")),
                _ => return Response::error(400, "not a lifecycle query"),
            }
        }
        let published = self.lock();
        let (published, _) = self
            .changed
            .wait_timeout_while(published, wait.min(self.cap), |published| after == Some(published.seq))
            .unwrap_or_else(PoisonError::into_inner);
        let snapshot = Snapshot {
            seq: published.seq,
            state: published.state.clone(),
        };
        Response::ok("application/json", serde_json::to_vec(&snapshot).expect("a snapshot encodes"))
    }
}

fn reader(server: &FakeHttp, token: &str) -> Reader {
    Reader::new(Client::new(server.url(), token).unwrap(), Some(LAUNCH))
}

/// Publishes `state` on another thread after `delay`.
fn publish_later(ide: &Arc<FakeIde>, delay: Duration, publish: impl FnOnce(&FakeIde) + Send + 'static) {
    let ide = ide.clone();
    thread::spawn(move || {
        thread::sleep(delay);
        publish(&ide);
    });
}

#[test]
fn a_reader_without_the_current_seq_gets_the_current_snapshot_at_once() {
    let (ide, server) = FakeIde::start(state(Phase::Opening), MAX_WAIT);
    ide.publish(state(Phase::Busy));
    let reader = reader(&server, TOKEN);
    // A reader of another IDE process holds a seq that this process has not reached.
    for after in [None, Some(1), Some(57)] {
        let started = Instant::now();
        let snapshot = reader.poll(after, Duration::from_secs(20)).unwrap();
        assert_eq!((snapshot.seq, snapshot.state.phase), (2, Phase::Busy), "after {after:?}");
        assert!(
            started.elapsed() < Duration::from_secs(5),
            "after {after:?} took {:?}",
            started.elapsed()
        );
    }
}

#[test]
fn a_current_reader_waits_for_the_next_snapshot() {
    let (ide, server) = FakeIde::start(state(Phase::Busy), MAX_WAIT);
    publish_later(&ide, Duration::from_millis(300), |ide| ide.publish(state(Phase::Ready)));
    let started = Instant::now();
    let snapshot = reader(&server, TOKEN).poll(Some(1), Duration::from_secs(20)).unwrap();
    assert_eq!((snapshot.seq, snapshot.state.phase), (2, Phase::Ready));
    assert!(
        started.elapsed() >= Duration::from_millis(250),
        "the answer came after {:?}",
        started.elapsed()
    );
}

/// The IDE cuts a wait to 30 s, so the client asks for no more, and its call ends a margin after that wait.
#[test]
fn a_wait_ends_with_the_current_snapshot_and_the_client_cuts_it_to_the_cap() {
    let (ide, server) = FakeIde::start(state(Phase::Busy), Duration::from_millis(200));
    let reader = reader(&server, TOKEN);
    let started = Instant::now();
    let snapshot = reader.poll(Some(1), Duration::from_secs(600)).unwrap();
    assert_eq!(snapshot.seq, 1);
    assert!(
        started.elapsed() >= Duration::from_millis(150),
        "the wait ended after {:?}",
        started.elapsed()
    );
    reader.poll(None, Duration::ZERO).unwrap();
    assert_eq!(
        ide.routes(),
        [format!("{EVENTS_ROUTE}?after=1&wait=30000"), format!("{EVENTS_ROUTE}?wait=0")]
    );
}

#[test]
fn the_bridge_plugin_and_the_vocabulary_digest_reach_the_reader() {
    let loaded = State {
        bridge_plugin: Some(BRIDGE_DIGEST.to_owned()),
        vocabulary_digest: Some("v1".to_owned()),
        ..state(Phase::Ready)
    };
    let (_ide, server) = FakeIde::start(loaded.clone(), MAX_WAIT);
    assert_eq!(reader(&server, TOKEN).poll(None, Duration::ZERO).unwrap().state, loaded);
}

fn golden() -> PathBuf {
    let path = avl_testkit::crate_path!("testdata/lifecycle-snapshot.json");
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// The golden is the document that `AirUiTestLifecycleLongPollTest` asserts for its fixture, byte for byte. So the
/// reader decodes what the IDE writes, and the fake IDE of these tests writes what the IDE writes.
#[test]
fn the_snapshot_of_the_ide_fixture_is_the_golden() {
    let expected = Snapshot {
        seq: 4,
        state: State {
            bridge_plugin: Some(BRIDGE_DIGEST.to_owned()),
            ..state(Phase::Ready)
        },
    };
    let text = serde_json::to_string(&expected).unwrap();
    expect_test::expect_file![golden()].assert_eq(&format!("{text}\n"));
    let document = std::fs::read(golden()).unwrap();
    assert_eq!(decode_snapshot(document.trim_ascii_end()).unwrap(), expected);
}

/// The frame of the Kotlin side of a failed bind: the field is there only with its text, and the phase stays BUSY.
#[test]
fn a_failed_control_bind_is_read_and_refuses_the_ready_wait_at_once() {
    let fixture = r#""phase":"BUSY","launch":"air-ui-lane","pid":4321,"projects":["plain-project"],"indexing":false,"backgroundTasks":0,"bridgePlugin":null,"vocabularyDigest":null"#;
    let document = format!(r#"{{"seq":3,"state":{{"schemaVersion":2,{fixture},"controlBindFailure":"could not bind port 30425"}}}}"#);
    let snapshot = decode_snapshot(document.as_bytes()).unwrap();
    assert_eq!(snapshot.state.control_bind_failure.as_deref(), Some("could not bind port 30425"));
    assert!(!serde_json::to_string(&state(Phase::Ready)).unwrap().contains("controlBindFailure"));

    let now = Instant::now();
    let mut wait = PhaseWait::new(Phase::Ready, Duration::ZERO, Some(LAUNCH));
    wait.observe(snapshot, now);
    let Verdict::Refused(refusal) = wait.verdict(now) else {
        panic!("a failed bind must refuse the ready wait");
    };
    assert!(matches!(refusal, WaitError::ControlBindFailed(_)));
    assert!(refusal.to_string().contains("did not bind: could not bind port 30425"), "{refusal}");

    let mut quitting = PhaseWait::new(Phase::Quitting, Duration::ZERO, Some(LAUNCH));
    quitting.observe(decode_snapshot(document.replace("BUSY", "QUITTING").as_bytes()).unwrap(), now);
    assert!(matches!(quitting.verdict(now), Verdict::Reached(_)));
}

#[test]
fn a_snapshot_outside_the_contract_is_refused() {
    let fixture = r#""phase":"READY","launch":null,"pid":1,"projects":[],"indexing":false,"backgroundTasks":0,"bridgePlugin":null,"vocabularyDigest":null"#;
    let cases = [
        (
            format!(r#"{{"seq":1,"state":{{"schemaVersion":2,{fixture},"renamed":1}}}}"#),
            "unknown field `renamed`",
        ),
        (
            format!(r#"{{"seq":1,"state":{{"schemaVersion":3,{fixture},"renamed":1}}}}"#),
            "a state of the schema 3, and this client reads the schema 2",
        ),
        (format!(r#"{{"seq":0,"state":{{"schemaVersion":2,{fixture}}}}}"#), "the seq 0"),
        (
            format!(r#"{{"seq":1,"state":{{"schemaVersion":2,{}}}}}"#, fixture.replace("READY", "DONE")),
            "unknown variant `DONE`",
        ),
        ("{".to_owned(), "not JSON"),
    ];
    for (document, refusal) in cases {
        let error = decode_snapshot(document.as_bytes()).unwrap_err();
        assert!(error.contains(refusal), "{document}: {error}");
    }
    assert_eq!(
        decode_snapshot(format!(r#"{{"seq":1,"state":{{"schemaVersion":2,{fixture}}}}}"#).as_bytes())
            .unwrap()
            .state
            .launch,
        None
    );
}

#[test]
fn an_answer_outside_the_contract_ends_a_wait() {
    let server = FakeHttp::start(|_| Response::ok("application/json", r#"{"seq":1}"#));
    let error = reader(&server, TOKEN)
        .await_ready(Duration::ZERO, Duration::from_secs(10))
        .unwrap_err();
    let WaitError::Call(call) = &error else {
        panic!("not a failed call: {error:?}");
    };
    assert!(matches!(call.kind(), ErrorKind::Protocol(_)), "{call:?}");
    assert!(
        error
            .to_string()
            .starts_with(&format!("the lifecycle long poll failed: GET {EVENTS_ROUTE}?wait=0: ")),
        "{error}"
    );
}

fn snapshot(seq: u64, phase: Phase) -> Snapshot {
    Snapshot { seq, state: state(phase) }
}

/// The decision with no server: the dwell continues over the same seq and the next one, and it starts again after
/// a gap, a lower seq, another pid and a failed poll.
#[test]
fn the_dwell_starts_again_after_each_sign_of_a_missed_state() {
    let dwell = Duration::from_secs(10);
    let start = Instant::now();
    let at = |seconds: u64| start + Duration::from_secs(seconds);
    let reached = |wait: &PhaseWait, seconds: u64| matches!(wait.verdict(at(seconds)), Verdict::Reached(_));

    let mut wait = PhaseWait::new(Phase::Ready, dwell, Some(LAUNCH));
    assert_eq!(wait.after(), None);
    assert!(matches!(wait.verdict(at(0)), Verdict::Waiting));
    wait.observe(snapshot(1, Phase::Busy), at(0));
    wait.observe(snapshot(2, Phase::Ready), at(1));
    wait.observe(snapshot(2, Phase::Ready), at(5));
    assert_eq!(
        (wait.after(), wait.until_dwell_ends(at(5))),
        (Some(2), Some(Duration::from_secs(6)))
    );
    assert!(!reached(&wait, 10) && reached(&wait, 11), "the same seq continues the dwell");

    wait.observe(snapshot(4, Phase::Ready), at(6));
    assert!(!reached(&wait, 15) && reached(&wait, 16), "a gap of two starts the dwell again");

    wait.observe(snapshot(5, Phase::Ready), at(7));
    assert!(reached(&wait, 16), "the next seq continues the dwell");

    wait.observe(snapshot(1, Phase::Ready), at(8));
    assert!(!reached(&wait, 17) && reached(&wait, 18), "a lower seq starts the dwell again");

    let restarted = Snapshot {
        seq: 2,
        state: State {
            pid: 9,
            ..state(Phase::Ready)
        },
    };
    wait.observe(restarted, at(9));
    assert!(!reached(&wait, 18) && reached(&wait, 19), "another pid starts the dwell again");

    wait.lost("refused".to_owned());
    assert_eq!((wait.after(), wait.until_dwell_ends(at(20))), (None, None));
    wait.observe(snapshot(3, Phase::Ready), at(20));
    assert!(!reached(&wait, 29) && reached(&wait, 30), "a failed poll starts the dwell again");

    wait.observe(snapshot(4, Phase::Busy), at(31));
    assert_eq!(wait.until_dwell_ends(at(31)), None);
    assert!(!reached(&wait, 100), "another phase ends the dwell");
    assert_eq!((wait.restarts, wait.losses), (3, 1));
}

#[test]
fn a_quitting_ide_and_another_launch_refuse_the_wait() {
    let now = Instant::now();
    let mut wait = PhaseWait::new(Phase::Ready, Duration::ZERO, Some(LAUNCH));
    wait.observe(snapshot(1, Phase::Quitting), now);
    let Verdict::Refused(refusal) = wait.verdict(now) else {
        panic!("a quitting IDE is not refused");
    };
    assert_eq!(
        refusal.to_string(),
        "the IDE quits and cannot reach READY: QUITTING with pid 4321 of the launch air-ui-lane, projects [\"plain-project\"], indexing false, 0 background task(s)"
    );

    let mut quitting = PhaseWait::new(Phase::Quitting, Duration::ZERO, None);
    quitting.observe(snapshot(1, Phase::Quitting), now);
    assert!(
        matches!(quitting.verdict(now), Verdict::Reached(_)),
        "a wait for QUITTING reaches it"
    );

    let other = Snapshot {
        seq: 1,
        state: State {
            launch: Some("another-lane".to_owned()),
            ..state(Phase::Ready)
        },
    };
    let mut wait = PhaseWait::new(Phase::Ready, Duration::ZERO, Some(LAUNCH));
    wait.observe(other, now);
    assert!(matches!(wait.verdict(now), Verdict::Refused(WaitError::OtherLaunch { .. })));

    let unnamed = Snapshot {
        seq: 1,
        state: State {
            launch: None,
            ..state(Phase::Ready)
        },
    };
    let mut wait = PhaseWait::new(Phase::Ready, Duration::ZERO, Some(LAUNCH));
    wait.observe(unnamed, now);
    assert!(
        matches!(wait.verdict(now), Verdict::Reached(_)),
        "a state that names no launch is not refused"
    );
}

#[test]
fn a_wait_reaches_the_phase_after_the_dwell() {
    let (ide, server) = FakeIde::start(state(Phase::Opening), MAX_WAIT);
    publish_later(&ide, Duration::from_millis(100), |ide| {
        ide.publish(state(Phase::Busy));
        thread::sleep(Duration::from_millis(100));
        ide.publish(state(Phase::Ready));
    });
    let started = Instant::now();
    let reached = reader(&server, TOKEN)
        .await_ready(Duration::from_millis(300), Duration::from_secs(20))
        .unwrap();
    assert_eq!(reached, state(Phase::Ready));
    assert!(
        started.elapsed() >= Duration::from_millis(450),
        "READY was reached after {:?}",
        started.elapsed()
    );
}

/// The reader holds READY, then sees the seq jump by two. A state went past that it did not see, so the dwell
/// starts again.
#[test]
fn a_gap_in_the_sequence_starts_the_dwell_again() {
    let (ide, server) = FakeIde::start(state(Phase::Ready), MAX_WAIT);
    publish_later(&ide, Duration::from_millis(300), |ide| {
        ide.publish_past_a_missed_state(state(Phase::Ready));
    });
    let started = Instant::now();
    reader(&server, TOKEN)
        .await_ready(Duration::from_millis(600), Duration::from_secs(20))
        .unwrap();
    assert!(
        started.elapsed() >= Duration::from_millis(850),
        "READY was reached after {:?}",
        started.elapsed()
    );
    let after_the_gap = format!("{EVENTS_ROUTE}?after=3&");
    assert!(
        ide.routes().iter().any(|route| route.starts_with(&after_the_gap)),
        "{:?}",
        ide.routes()
    );
}

/// A new IDE process starts its sequence at 1, below the reader's `after`. The IDE answers at once, and the new pid
/// starts the dwell again.
#[test]
fn a_restarted_ide_starts_the_dwell_again() {
    let (ide, server) = FakeIde::start(state(Phase::Opening), MAX_WAIT);
    ide.publish(state(Phase::Busy));
    ide.publish(state(Phase::Ready));
    publish_later(&ide, Duration::from_millis(300), |ide| {
        ide.restart(State {
            pid: 9,
            ..state(Phase::Ready)
        });
    });
    let started = Instant::now();
    let reached = reader(&server, TOKEN)
        .await_ready(Duration::from_millis(600), Duration::from_secs(20))
        .unwrap();
    assert_eq!(reached.pid, 9);
    assert!(
        started.elapsed() >= Duration::from_millis(850),
        "READY was reached after {:?}",
        started.elapsed()
    );
}

#[test]
fn a_transient_failure_is_retried_within_the_deadline() {
    let (ide, server) = FakeIde::start(state(Phase::Ready), MAX_WAIT);
    *ide.outage.lock().unwrap() = 3;
    let started = Instant::now();
    reader(&server, TOKEN).await_ready(Duration::ZERO, Duration::from_secs(20)).unwrap();
    assert_eq!(ide.routes().len(), 4, "{:?}", ide.routes());
    assert!(
        started.elapsed() >= Duration::from_millis(700),
        "three retries took {:?}",
        started.elapsed()
    );

    // A free port below the ephemeral floor, which no parallel bind of port 0 takes, so the connect is refused.
    let port = avl_testkit::free_port_below_ephemeral_floor();
    let error = Reader::new(Client::new(&url(port), TOKEN).unwrap(), None)
        .await_ready(Duration::from_secs(1), Duration::from_millis(600))
        .unwrap_err();
    let WaitError::TimedOut(seen) = &error else {
        panic!("not a timeout: {error:?}");
    };
    assert!(seen.losses >= 1, "{error}");
    let text = error.to_string();
    assert!(
        text.starts_with("the IDE did not reach READY for 1s within 600ms; the latest state was never received; ")
            && text.contains(&format!("poll(s) failed, the last one with: GET {EVENTS_ROUTE}?wait=0: ")),
        "{text}"
    );
}

#[test]
fn a_refused_token_ends_the_wait_at_once() {
    let (ide, server) = FakeIde::start(state(Phase::Ready), MAX_WAIT);
    let error = reader(&server, "wrong")
        .await_ready(Duration::ZERO, Duration::from_secs(20))
        .unwrap_err();
    let WaitError::Call(call) = &error else {
        panic!("not a failed call: {error:?}");
    };
    assert!(matches!(call.kind(), ErrorKind::Unauthorized(_)), "{call:?}");
    assert_eq!(ide.routes().len(), 1);
    assert_eq!(
        error.to_string(),
        format!(
            "the lifecycle long poll failed: GET {EVENTS_ROUTE}?wait=0: the bridge answered HTTP 401: unsigned Air UI-test lifecycle request"
        )
    );
}

// A wait that `ended` stops ends at once with the reason, and it does not wait for its deadline. No bridge answers here,
// as after a kill, so each poll is a transient failure.
#[test]
fn a_wait_whose_process_ended_ends_at_once_with_the_reason() {
    let port = avl_testkit::free_port_below_ephemeral_floor();
    let reader = Reader::new(Client::new(&url(port), "token").unwrap(), Some("launch-1"));
    let mut checks = 0;
    let started = Instant::now();
    let error = reader
        .await_ready_while(Duration::ZERO, Duration::from_secs(30), &mut || {
            checks += 1;
            (checks == 3).then(|| "its run ended with 143".to_owned())
        })
        .unwrap_err();
    assert!(started.elapsed() < Duration::from_secs(10), "the wait took {:?}", started.elapsed());
    assert_eq!(checks, 3);
    assert_eq!(error.to_string(), "the process of the IDE ended: its run ended with 143");
}
