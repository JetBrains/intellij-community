//! The one wait loop: a budget counted on a [`PollClock`], and a pause between two probes that grows.
//!
//! A loop probes, then asks [`Poll::pause`] for its next turn. The pause starts at the first pause of the loop's
//! [`Backoff`] and doubles after each turn up to the cap. So a probe that answers early is seen early, and a long wait
//! spawns few probes: a five-minute suspend probes `ps` about 150 times at 100 ms doubling to 2 s, where it probed
//! 3 000 times at a flat 100 ms. The last pause ends at the budget, so a wait ends within its budget and one probe.

use std::time::Duration;

use avl_base::clock::elapsed;
use jiff::Timestamp;

use crate::ctx::{Ctx, PollClock};

#[cfg(test)]
mod tests;

/// The pause between two probes of one wait: `first`, doubled after each probe up to `cap`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Backoff {
    first: Duration,
    cap: Duration,
}

impl Backoff {
    /// Starts at `first` and doubles up to `cap`: for a cheap probe, such as a `ps` or a `tart list`, whose answer
    /// can come at any moment.
    pub const fn doubling(first: Duration, cap: Duration) -> Self {
        Self { first, cap }
    }

    /// The same pause between every two probes: for a probe that is a whole exec round trip, where a shorter pause
    /// would spend the budget on spawns, and for a probe that costs no process at all.
    pub const fn fixed(pause: Duration) -> Self {
        Self { first: pause, cap: pause }
    }
}

/// One wait: its budget, the moment it started, and the pause before the next probe.
pub struct Poll<'a> {
    clock: &'a dyn PollClock,
    started: Timestamp,
    budget: Duration,
    backoff: Backoff,
    next: Duration,
}

impl<'a> Poll<'a> {
    /// A wait of `budget` that starts now on `clock`. A wait that watches a real process polls on
    /// [`avl_base::SystemClock`]; only a wait over a call-driven fake may take a fake clock.
    pub fn start(clock: &'a dyn PollClock, budget: Duration, backoff: Backoff) -> Self {
        Self {
            clock,
            started: clock.now(),
            budget,
            backoff,
            next: backoff.first,
        }
    }

    /// What is left of the budget. A probe in flight that may outlast it takes this as its own timeout.
    pub fn left(&self) -> Duration {
        self.budget.saturating_sub(self.elapsed())
    }

    /// How long the wait has run.
    pub fn elapsed(&self) -> Duration {
        elapsed(self.started, self.clock.now())
    }

    /// Whether the budget is spent.
    pub fn spent(&self) -> bool {
        self.left().is_zero()
    }

    /// Waits for the next probe, and answers whether the loop may probe again.
    ///
    /// It waits the current pause, but no longer than what is left of the budget, and then doubles the next pause up
    /// to the cap. It answers false, and waits nothing, when the budget is spent, and false when the operation was
    /// cancelled. A caller tells the two apart with [`Ctx::is_cancelled`].
    pub async fn pause(&mut self, ctx: &Ctx) -> bool {
        let left = self.left();
        if left.is_zero() {
            return false;
        }
        let pause = self.next.min(left);
        self.next = self.next.saturating_mul(2).min(self.backoff.cap);
        self.clock.sleep(ctx, pause).await
    }
}
