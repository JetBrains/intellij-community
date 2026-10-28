//! A log line for an event that a host of the network can repeat at will, such as a refused handshake. The first event
//! writes a line at once. The events of the next minute only count, and the first line after that minute names the
//! count and the last event. So a flood writes one line per minute, and launchd, which never rotates the log, does not
//! fill the disk.

use std::sync::{Mutex, PoisonError};
use std::time::{Duration, Instant};

/// The shortest time between two lines of one tally.
pub(crate) const EVERY: Duration = Duration::from_secs(60);

/// The count of one kind of event since its last line.
#[derive(Debug)]
pub(crate) struct Tally {
    /// What the events are, such as `refused connections`.
    what: &'static str,
    state: Mutex<State>,
}

#[derive(Debug)]
struct State {
    count: u64,
    last: String,
    logged: Option<Instant>,
}

impl Tally {
    pub(crate) const fn new(what: &'static str) -> Self {
        Self {
            what,
            state: Mutex::new(State {
                count: 0,
                last: String::new(),
                logged: None,
            }),
        }
    }

    /// Counts one event at `now`. Answers the line to write when no line came in the last [`EVERY`].
    pub(crate) fn note(&self, now: Instant, event: String) -> Option<String> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        state.count += 1;
        state.last = event;
        self.line(&mut state, now)
    }

    /// The line of the counted events, when no line came in the last [`EVERY`].
    pub(crate) fn flush(&self, now: Instant) -> Option<String> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        if state.count == 0 {
            return None;
        }
        self.line(&mut state, now)
    }

    fn line(&self, state: &mut State, now: Instant) -> Option<String> {
        if state.logged.is_some_and(|logged| now.saturating_duration_since(logged) < EVERY) {
            return None;
        }
        let line = format!("{}: {} since the last line, the last {}", self.what, state.count, state.last);
        state.count = 0;
        state.logged = Some(now);
        Some(line)
    }
}

#[cfg(test)]
mod tests;
