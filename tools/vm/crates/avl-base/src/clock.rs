//! The wall clock, injected where a test must decide what time it is.

use std::fmt;
use std::sync::Mutex;
use std::time::Duration;

use jiff::{SignedDuration, Timestamp};

use crate::sync::lock;

pub use avl_wire::progress::stamp;

/// What time it is. The reporter stamps records with it and times phases by it.
pub trait Clock: Send + Sync {
    fn now(&self) -> Timestamp;
}

/// The system's clock.
#[derive(Clone, Copy, Debug, Default)]
pub struct SystemClock;

impl Clock for SystemClock {
    fn now(&self) -> Timestamp {
        Timestamp::now()
    }
}

/// A clock that moves only when a test moves it. Shared through an `Arc`, so the test keeps a handle to advance
/// while the code under test reads it.
pub struct FakeClock(Mutex<Timestamp>);

impl FakeClock {
    pub const fn new(now: Timestamp) -> Self {
        Self(Mutex::new(now))
    }

    /// A clock at an RFC 3339 instant, such as `2026-09-24T12:00:00Z`.
    ///
    /// # Panics
    /// When `instant` is not RFC 3339: a test's own literal is wrong.
    pub fn at(instant: &str) -> Self {
        let now = instant
            .parse()
            .unwrap_or_else(|error| panic!("{instant:?} is not an RFC 3339 instant: {error}"));
        Self::new(now)
    }

    pub fn advance(&self, by: Duration) {
        let by = SignedDuration::try_from(by).unwrap_or(SignedDuration::MAX);
        let mut now = lock(&self.0);
        *now = now.saturating_add(by).unwrap_or(*now);
    }

    pub fn set(&self, now: Timestamp) {
        *lock(&self.0) = now;
    }
}

impl Clock for FakeClock {
    fn now(&self) -> Timestamp {
        *lock(&self.0)
    }
}

impl fmt::Debug for FakeClock {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_tuple("FakeClock").field(&self.now()).finish()
    }
}

/// A duration in whole milliseconds, as the documents carry it: saturated at `u64::MAX`, which no real duration
/// reaches, rather than wrapped.
pub fn millis(duration: Duration) -> u64 {
    u64::try_from(duration.as_millis()).unwrap_or(u64::MAX)
}

/// The time from `earlier` to `later`, or zero when `later` is not later.
pub fn elapsed(earlier: Timestamp, later: Timestamp) -> Duration {
    Duration::try_from(later.duration_since(earlier)).unwrap_or(Duration::ZERO)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_fake_clock_moves_only_when_told() {
        let clock = FakeClock::at("2026-09-24T12:00:00Z");
        let start = clock.now();
        assert_eq!(clock.now(), start);
        clock.advance(Duration::from_millis(1500));
        assert_eq!(elapsed(start, clock.now()), Duration::from_millis(1500));
        assert_eq!(elapsed(clock.now(), start), Duration::ZERO);
    }
}
