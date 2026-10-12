use avl_base::FakeClock;
use pretty_assertions::assert_eq;
use tokio_util::sync::CancellationToken;

use super::*;

fn clock() -> FakeClock {
    FakeClock::at("2026-10-01T12:00:00Z")
}

/// The pauses a wait sleeps until its budget is spent.
async fn pauses(budget: Duration, backoff: Backoff) -> Vec<Duration> {
    let clock = clock();
    let ctx = Ctx::background();
    let mut poll = Poll::start(&clock, budget, backoff);
    let mut pauses = Vec::new();
    let mut before = poll.elapsed();
    while poll.pause(&ctx).await {
        pauses.push(poll.elapsed().saturating_sub(before));
        before = poll.elapsed();
    }
    pauses
}

// The pause doubles to its cap, and the last one ends at the budget: a five-minute wait probes far fewer times than
// at a flat 100 ms, and never past its budget.
#[tokio::test]
async fn the_pause_doubles_to_its_cap_and_the_last_ends_at_the_budget() {
    let schedule = pauses(
        Duration::from_secs(5),
        Backoff::doubling(Duration::from_millis(100), Duration::from_secs(2)),
    )
    .await;
    let millis: Vec<u128> = schedule.iter().map(Duration::as_millis).collect();
    assert_eq!(millis, [100, 200, 400, 800, 1600, 1900]);

    let suspend = pauses(
        Duration::from_mins(5),
        Backoff::doubling(Duration::from_millis(100), Duration::from_secs(2)),
    )
    .await;
    assert_eq!(suspend.iter().sum::<Duration>(), Duration::from_mins(5));
    assert!(suspend.len() < 160, "{} probes", suspend.len() + 1);
}

#[tokio::test]
async fn a_fixed_backoff_keeps_its_pause() {
    let schedule = pauses(Duration::from_secs(7), Backoff::fixed(Duration::from_secs(2))).await;
    let millis: Vec<u128> = schedule.iter().map(Duration::as_millis).collect();
    assert_eq!(millis, [2000, 2000, 2000, 1000]);
}

// A spent budget waits nothing, and a cancelled operation stops the wait; the caller reads which off its context.
#[tokio::test]
async fn a_spent_budget_or_a_cancellation_ends_the_wait() {
    let clock = clock();
    let ctx = Ctx::background();
    let mut spent = Poll::start(&clock, Duration::ZERO, Backoff::fixed(Duration::from_secs(1)));
    assert!(spent.spent());
    assert!(!spent.pause(&ctx).await);
    assert_eq!(spent.elapsed(), Duration::ZERO, "a spent wait sleeps nothing");

    let token = CancellationToken::new();
    let cancelled = Ctx::new(token.clone());
    token.cancel();
    let mut poll = Poll::start(&clock, Duration::from_secs(10), Backoff::fixed(Duration::from_secs(1)));
    assert!(!poll.pause(&cancelled).await);
    assert!(cancelled.is_cancelled());
    assert_eq!(poll.left(), Duration::from_secs(9));
}
