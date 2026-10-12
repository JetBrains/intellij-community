use std::thread;

use pretty_assertions::assert_eq;

use super::*;

const MS: Duration = Duration::from_millis(1);

// Every operation is safe with collection off, because that is the ordinary path and the call sites must not each
// carry an `if`.
#[test]
fn nothing_is_collected_without_a_timeline() {
    let timeline = Timeline::default();
    let guard = timeline.begin("boot-poll");
    let open = guard.handle();
    open.record_subprocess(&["tart", "exec"], Duration::from_secs(1));
    open.record_poll(3, None);
    timeline.declare_guest_head("tart");
    assert!(!open.is_collecting(), "a phase was opened with no timeline");
    assert_eq!(open.elapsed(), Duration::ZERO);
    assert!(PhaseHandle::default().elapsed().is_zero());
    drop(guard);
    assert!(timeline.take().is_empty());
}

#[test]
fn a_phase_records_guest_and_host_separately() {
    let timeline = Timeline::collecting();
    timeline.declare_guest_head("tart");
    let guard = timeline.begin("state-prep");
    let open = guard.handle();
    assert!(open.is_collecting());
    open.record_subprocess(&["tart", "exec", "air-linux-1", "mkdir"], 90 * MS);
    open.record_subprocess(&["tart", "exec", "air-linux-1", "rm"], 110 * MS);
    open.record_subprocess(&["git", "rev-parse"], 20 * MS);
    drop(guard);

    let timings = timeline.take();
    assert_eq!(timings.len(), 1);
    let timing = &timings[0];
    assert_eq!(timing.name, "state-prep");
    assert_eq!((timing.guest_calls, timing.guest_ms), (2, 200));
    assert_eq!((timing.host_calls, timing.host_ms), (1, 20));
}

// A spawn whose argv head was never declared is a host call, not an uncounted one: a caller can fail to declare
// itself into the guest column, and then the cost still appears.
#[test]
fn an_undeclared_head_counts_as_a_host_call() {
    let timeline = Timeline::collecting();
    let guard = timeline.begin("install-agent");
    guard.handle().record_subprocess(&["prlctl", "exec"], 50 * MS);
    drop(guard);
    let timing = &timeline.take()[0];
    assert_eq!((timing.guest_calls, timing.host_calls), (0, 1), "{timing:?}");
}

// Absent and zero are different facts. A phase that never polled reports no iterations; one whose first probe
// succeeded reports zero sleep. The JSON keeps them apart too.
#[test]
fn poll_recording_keeps_absent_apart_from_zero() {
    let timeline = Timeline::collecting();
    drop(timeline.begin("no-poll"));

    let first_probe_won = timeline.begin("health-poll");
    first_probe_won.handle().record_poll(1, None);
    drop(first_probe_won);

    let slept = timeline.begin("boot-poll");
    slept.handle().record_poll(10, Some(Duration::from_secs(27)));
    drop(slept);

    let timings = timeline.take();
    assert_eq!(timings[0].poll_iterations, None);
    assert_eq!(timings[1].poll_iterations, Some(1));
    assert_eq!(timings[1].last_unsuccessful_probe_ms, None);
    assert_eq!(timings[2].last_unsuccessful_probe_ms, Some(27_000));

    let quiet = serde_json::to_value(&timings[0]).expect("a timing serializes");
    assert_eq!(quiet.get("pollIterations"), None, "{quiet}");
    assert_eq!(quiet.get("lastUnsuccessfulProbeMs"), None, "{quiet}");
    let polled = serde_json::to_value(&timings[1]).expect("a timing serializes");
    assert_eq!(polled["pollIterations"], 1);
    assert_eq!(polled.get("lastUnsuccessfulProbeMs"), None, "{polled}");
}

// Two calls of 2.7 s and fifteen of 50 ms are the same guest column and opposite repairs, so the phase says which
// of the two it saw: the dearest single call, not the total and not the last one.
#[test]
fn the_slowest_guest_call_names_the_dearest_of_two() {
    let timeline = Timeline::collecting();
    timeline.declare_guest_head("tart");

    let dear = timeline.begin("prepare-launch");
    let open = dear.handle();
    open.record_subprocess(&["tart", "exec", "air-linux-1", "mkdir"], 200 * MS);
    open.record_subprocess(&["tart", "exec", "air-linux-1", "tee"], 10_600 * MS);
    open.record_subprocess(&["tart", "exec", "air-linux-1", "chmod"], 150 * MS);
    // A host call is not a candidate, however dear: nothing local is answered by a persistent channel.
    open.record_subprocess(&["bazel", "build"], Duration::from_secs(40));
    drop(dear);

    let instant = timeline.begin("parity-probe");
    instant
        .handle()
        .record_subprocess(&["tart", "exec", "air-linux-1", "test"], Duration::ZERO);
    drop(instant);

    drop(timeline.begin("gc-runtimes"));

    let timings = timeline.take();
    assert_eq!(timings[0].slowest_guest_call_ms, Some(10_600));
    assert!(
        timings[0]
            .slowest_guest_call
            .as_deref()
            .is_some_and(|skeleton| skeleton.ends_with("tee")),
        "{:?}",
        timings[0].slowest_guest_call
    );
    // A phase whose only guest call returned instantly still has a dearest one; reporting nothing would read as
    // "this phase reached no guest".
    assert_eq!(timings[1].slowest_guest_call_ms, Some(0));
    assert_eq!(timings[2].slowest_guest_call_ms, None);
    assert_eq!(timings[2].slowest_guest_call, None);
}

// The skeleton is the shape of the argv and none of its payload: a guest command line carries the bridge token,
// `-D` properties naming paths under someone's home, and the account `sudo` runs as.
#[test]
fn the_slowest_guest_call_skeleton_withholds_secrets() {
    const TOKEN: &str = "br1dge-t0ken-nobody-may-log";
    let timeline = Timeline::collecting();
    timeline.declare_guest_head("tart");
    let guard = timeline.begin("supervisor-start");
    guard.handle().record_subprocess(
        &[
            "tart".to_owned(),
            "exec".to_owned(),
            "air-linux-1".to_owned(),
            "/usr/bin/env".to_owned(),
            format!("AIR_UI_BRIDGE_TOKEN={TOKEN}"),
            "/vm/data/state/vm-guest-agent".to_owned(),
            "@/vm/data/daemon/daemon-jvm.args".to_owned(),
            "-Dair.ui.home=/Users/somebody/Library/Application Support/JetBrains/air-ui-daemon-runtime".to_owned(),
        ],
        Duration::from_secs(1),
    );
    drop(guard);

    let skeleton = timeline.take()[0].slowest_guest_call.clone().expect("a guest call has a skeleton");
    for withheld in [TOKEN, "AIR_UI_BRIDGE_TOKEN", "somebody", "Application Support"] {
        assert!(!skeleton.contains(withheld), "the skeleton quotes {withheld:?}: {skeleton}");
    }
    assert!(
        skeleton.starts_with("tart exec air-linux-1 "),
        "the skeleton must still identify the call: {skeleton}"
    );
    assert!(skeleton.len() <= SKELETON_BOUNDS.bytes, "{skeleton}");
    // Capped by element count too, so a long argv cannot carry its tail by staying under the byte limit.
    let long = vec!["arg"; 20];
    assert_eq!(guest_call_skeleton(&long).split_whitespace().count(), SKELETON_BOUNDS.elements);
}

// Nesting: the inner phase takes the work given its handle, and the outer handle keeps recording into the outer
// phase before and after.
#[test]
fn an_inner_phase_wins_for_its_body_only() {
    let timeline = Timeline::collecting();
    timeline.declare_guest_head("tart");

    let outer = timeline.begin("start-daemon");
    outer.handle().record_subprocess(&["tart", "before"], MS);
    {
        let inner = timeline.begin("stage-runtime");
        inner.handle().record_subprocess(&["tart", "inside"], MS);
    }
    outer.handle().record_subprocess(&["tart", "after"], MS);
    drop(outer);

    let timings = timeline.take();
    assert_eq!(timings.len(), 2);
    assert_eq!((timings[0].name.as_str(), timings[0].guest_calls), ("start-daemon", 2));
    assert_eq!((timings[1].name.as_str(), timings[1].guest_calls), ("stage-runtime", 1));
}

// Several workers at once: every call lands on the phase of the worker that made it.
#[test]
fn concurrent_workers_do_not_race() {
    const WORKERS: usize = 8;
    const CALLS_EACH: u32 = 25;
    let timeline = Timeline::collecting();
    thread::scope(|scope| {
        for _ in 0..WORKERS {
            let timeline = timeline.clone();
            scope.spawn(move || {
                timeline.declare_guest_head("tart");
                let guard = timeline.begin("iteration");
                let open = guard.handle();
                for call in 0..CALLS_EACH {
                    open.record_subprocess(&["tart", "exec"], MS);
                    open.record_poll(call, None);
                }
            });
        }
    });
    let timings = timeline.take();
    assert_eq!(timings.len(), WORKERS);
    for timing in &timings {
        assert_eq!(timing.guest_calls, CALLS_EACH, "{timing:?}");
    }
}

// The one shape that reaches the degenerate branch, pinned because its answer is what makes a caller error fail
// closed: no byte bound renders the ellipsis, never the argv.
#[test]
fn argv_bounds_with_no_byte_bound_render_nothing_of_the_argv() {
    let rendered = redact_guest_argv(
        &["tart", "exec", "AIR_UI_BRIDGE_TOKEN=br1dge-t0ken-nobody-may-log"],
        ArgvBounds::default(),
    );
    assert_eq!(rendered, REDACTED_ELEMENT);
}
