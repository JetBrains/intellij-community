use std::time::Duration;

use pretty_assertions::assert_eq;

use super::*;

// The one spelling of a poll loop's wait: a cancelled operation stops sleeping at once.
#[tokio::test]
async fn a_sleep_ends_when_the_operation_is_cancelled() {
    let ctx = Ctx::background();
    assert!(ctx.sleep(Duration::from_millis(1)).await);
    let token = CancellationToken::new();
    let ctx = Ctx::new(token.clone());
    token.cancel();
    let slept = tokio::time::timeout(Duration::from_secs(5), ctx.sleep(Duration::from_secs(60)))
        .await
        .expect("a cancelled sleep kept sleeping");
    assert!(!slept);
}

// A phase opened from a context records the work given the inner context, while the outer one keeps recording into
// the phase it was already in.
#[test]
fn a_phase_is_carried_by_the_context_it_answers() {
    let timeline = Timeline::collecting();
    let outer = Ctx::background().with_timeline(timeline.clone()).with_run_id("run-1");
    let (restart, restart_phase) = outer.begin("restart");
    let (inner, inner_phase) = restart.begin("state-prep");
    assert_eq!(inner.run_id(), Some("run-1"));
    inner.phase().record_subprocess(&["git"], Duration::from_millis(10));
    restart.phase().record_subprocess(&["git"], Duration::from_millis(10));
    restart.phase().record_subprocess(&["git"], Duration::from_millis(10));
    drop(inner_phase);
    drop(restart_phase);
    let timings = timeline.take();
    let calls: Vec<(&str, u32)> = timings.iter().map(|timing| (timing.name.as_str(), timing.host_calls)).collect();
    assert_eq!(calls, [("restart", 2), ("state-prep", 1)]);
    // Nothing is collected by default, so the ordinary path pays nothing.
    assert!(!Ctx::background().phase().is_collecting());
}

// A poll over a fake clock pays no wall time: the sleep moves the clock by exactly what it was asked, and a
// cancelled operation still ends the loop.
#[tokio::test]
async fn a_fake_clock_sleep_moves_the_clock_and_honours_cancellation() {
    let clock = FakeClock::at("2026-09-28T12:00:00Z");
    let started = clock.now();
    assert!(PollClock::sleep(&clock, &Ctx::background(), Duration::from_mins(20)).await);
    assert_eq!(avl_base::clock::elapsed(started, clock.now()), Duration::from_mins(20));
    let token = CancellationToken::new();
    token.cancel();
    assert!(!PollClock::sleep(&clock, &Ctx::new(token), Duration::from_secs(1)).await);
}
