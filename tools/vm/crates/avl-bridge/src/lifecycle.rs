//! The lifecycle of the IDE under test, read through the long poll of the bridge.
//!
//! The far end is `AirUiTestLifecycleHttpHandler` in `plugins/air/tests/integration/bridge/resident`. It answers
//! `GET /api/air/ui-test-events?after=<seq>&wait=<ms>` with the [Snapshot] `{seq, state}`. The answer comes at once when
//! the sequence number of the IDE is not `after`. Otherwise it comes with the next state, or with the current state when
//! the wait ends. The IDE cuts a wait to [MAX_WAIT].
//!
//! [PhaseWait] is the decision of a wait for a phase, as a function of the snapshots and the time, so a test can check
//! it without a server. [Reader::await_phase] drives it over the long poll. All of it is synchronous: one poll is one
//! call of [Client::get], and nothing starts a thread.

use std::fmt;
use std::thread;
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::client::protocol;
use crate::{Client, Error, JSON};

#[cfg(test)]
mod tests;

/// The Kotlin side's `AIR_UI_TEST_EVENTS_PREFIX` with its leading slash.
pub const EVENTS_ROUTE: &str = "/api/air/ui-test-events";

/// The schema of [State] that this client reads, the Kotlin side's `AIR_UI_TEST_EVENTS_SCHEMA_VERSION`.
pub const SCHEMA_VERSION: u32 = 2;

/// The longest wait of one poll that the IDE holds, the Kotlin side's `AIR_UI_TEST_EVENTS_MAX_WAIT`. [Reader::poll]
/// cuts a longer wait to it.
pub const MAX_WAIT: Duration = Duration::from_secs(30);

/// The longest wait of one poll of [Reader::await_phase]. A wait for a phase asks for the verdict again at least this
/// often.
pub const POLL_WAIT: Duration = Duration::from_secs(1);

/// The time that a poll waits for its answer past its wait. A bridge before `COMPONENTS_LOADED` reads a request and
/// sends no answer, so this bounds what such a poll costs.
const CALL_MARGIN: Duration = Duration::from_secs(5);

/// The pause before the next poll after a failed poll.
const RETRY_INTERVAL: Duration = Duration::from_millis(250);

/// How far along the IDE is, as the IDE itself sees it. The Kotlin side's `AirUiTestIdePhase`.
#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Phase {
    /// The driven project is not open.
    Opening,
    /// The driven project is open, and the IDE scans, indexes or shows a background task.
    Busy,
    /// The driven project is open, and the IDE has no work on it.
    Ready,
    /// The exit is scheduled. No other phase follows.
    Quitting,
}

impl fmt::Display for Phase {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::Opening => "OPENING",
            Self::Busy => "BUSY",
            Self::Ready => "READY",
            Self::Quitting => "QUITTING",
        })
    }
}

/// One observation of the lifecycle of the IDE, of [SCHEMA_VERSION]. The Kotlin side's `AirUiTestIdeState`.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct State {
    pub schema_version: u32,
    pub phase: Phase,
    /// The launch id of the IDE, or null for an IDE that no launch named.
    pub launch: Option<String>,
    pub pid: i64,
    /// The names of the open projects.
    pub projects: Vec<String>,
    /// Whether the IDE is in dumb mode.
    pub indexing: bool,
    /// The count of the background tasks in the status bar.
    pub background_tasks: u32,
    /// The digest of the loaded bridge plugin, or null when the bridge plugin is not loaded or the context plugin
    /// manifest does not name it.
    pub bridge_plugin: Option<String>,
    /// The digest of the flow vocabulary that the bridge serves, or null.
    pub vocabulary_digest: Option<String>,
    /// Why the control server of the controlled agents in the IDE did not bind. The IDE leaves the field out while it
    /// is null, and it never reports [Phase::Ready] while the field is set.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub control_bind_failure: Option<String>,
}

impl fmt::Display for State {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "{} with pid {} of the launch {}, projects {:?}, indexing {}, {} background task(s)",
            self.phase,
            self.pid,
            self.launch.as_deref().unwrap_or("<none>"),
            self.projects,
            self.indexing,
            self.background_tasks
        )?;
        if let Some(failure) = &self.control_bind_failure {
            write!(formatter, ", the control server did not bind: {failure}")?;
        }
        Ok(())
    }
}

/// One answer of the long poll: a [State] and its sequence number.
///
/// `seq` starts at one in each IDE process and grows by one with each change of the state. So a reader whose last
/// `seq` is two or more behind missed a state.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(deny_unknown_fields)]
pub struct Snapshot {
    pub seq: u64,
    pub state: State,
}

/// Reads one answer of the long poll. A state of another schema, an unknown field and a `seq` of zero are refused.
pub fn decode_snapshot(document: &[u8]) -> Result<Snapshot, String> {
    let document: Value =
        serde_json::from_slice(document).map_err(|error| format!("the lifecycle route answered a body that is not JSON: {error}"))?;
    if let Some(schema) = document.pointer("/state/schemaVersion")
        && *schema != SCHEMA_VERSION
    {
        return Err(format!(
            "the lifecycle route answered a state of the schema {schema}, and this client reads the schema {SCHEMA_VERSION}"
        ));
    }
    let snapshot: Snapshot =
        serde_json::from_value(document).map_err(|error| format!("the lifecycle route answered outside the contract: {error}"))?;
    if snapshot.seq == 0 {
        return Err("the lifecycle route answered the seq 0, and the sequence starts at 1".to_owned());
    }
    Ok(snapshot)
}

/// Reads the lifecycle of one IDE through its bridge.
#[derive(Clone, Debug)]
pub struct Reader {
    client: Client,
    launch: Option<String>,
}

impl Reader {
    /// A reader of the IDE behind `client`. With `launch`, a state that names another launch ends each wait, because
    /// the IDE of another lane answers on the port.
    pub fn new(client: Client, launch: Option<&str>) -> Self {
        Self {
            client,
            launch: launch.map(str::to_owned),
        }
    }

    /// Polls once. Without `after`, the IDE answers at once. With `after`, it holds the answer for up to `wait`, cut to
    /// [MAX_WAIT], until its sequence number is not `after`. The call ends [CALL_MARGIN] after the wait.
    pub fn poll(&self, after: Option<u64>, wait: Duration) -> Result<Snapshot, Error> {
        self.poll_until(after, wait, None)
    }

    /// Waits until the IDE is in `phase` for `quiet_for` and answers the state that reached it. See [PhaseWait] for the
    /// decision.
    ///
    /// A poll that fails with [Error::is_transient] restarts the dwell, and the next poll comes [RETRY_INTERVAL]
    /// later. Every other failed poll ends the wait, as a refused token does.
    pub fn await_phase(&self, phase: Phase, quiet_for: Duration, timeout: Duration) -> Result<State, WaitError> {
        self.await_phase_while(phase, quiet_for, timeout, &mut || None)
    }

    /// [Self::await_phase], which also asks `ended` before each poll whether the process of the IDE ended. An answer
    /// ends the wait at once with [WaitError::Ended], so a killed IDE costs no wait up to `timeout`.
    pub fn await_phase_while(
        &self,
        phase: Phase,
        quiet_for: Duration,
        timeout: Duration,
        ended: &mut dyn FnMut() -> Option<String>,
    ) -> Result<State, WaitError> {
        let deadline = Instant::now() + timeout;
        let mut wait = PhaseWait::new(phase, quiet_for, self.launch.as_deref());
        loop {
            let now = Instant::now();
            match wait.verdict(now) {
                Verdict::Reached(state) => return Ok(state.clone()),
                Verdict::Refused(refusal) => return Err(refusal),
                Verdict::Waiting => {}
            }
            if let Some(reason) = ended() {
                return Err(WaitError::Ended(reason));
            }
            let left = deadline.saturating_duration_since(now);
            if left.is_zero() {
                return Err(wait.timed_out(timeout));
            }
            let hold = match wait.after() {
                None => Duration::ZERO,
                Some(_) => wait.until_dwell_ends(now).unwrap_or(POLL_WAIT).min(POLL_WAIT).min(left),
            };
            match self.poll_until(wait.after(), hold, Some(deadline)) {
                Ok(snapshot) => wait.observe(snapshot, Instant::now()),
                Err(error) if error.is_transient() => {
                    wait.lost(error.to_string());
                    thread::sleep(RETRY_INTERVAL.min(deadline.saturating_duration_since(Instant::now())));
                }
                Err(error) => return Err(WaitError::Call(error)),
            }
        }
    }

    /// [Self::await_phase] for [Phase::Ready].
    pub fn await_ready(&self, quiet_for: Duration, timeout: Duration) -> Result<State, WaitError> {
        self.await_phase(Phase::Ready, quiet_for, timeout)
    }

    /// [Self::await_phase_while] for [Phase::Ready].
    pub fn await_ready_while(
        &self,
        quiet_for: Duration,
        timeout: Duration,
        ended: &mut dyn FnMut() -> Option<String>,
    ) -> Result<State, WaitError> {
        self.await_phase_while(Phase::Ready, quiet_for, timeout, ended)
    }

    /// [Self::poll], with a call that ends at `limit` at the latest.
    fn poll_until(&self, after: Option<u64>, wait: Duration, limit: Option<Instant>) -> Result<Snapshot, Error> {
        let wait = wait.min(MAX_WAIT);
        let route = match after {
            Some(after) => format!("{EVENTS_ROUTE}?after={after}&wait={}", wait.as_millis()),
            None => format!("{EVENTS_ROUTE}?wait={}", wait.as_millis()),
        };
        let own = Instant::now() + wait + CALL_MARGIN;
        let deadline = limit.map_or(own, |limit| own.min(limit));
        let body = self.client.get(&route, JSON, deadline)?;
        decode_snapshot(&body).map_err(|problem| Error::of_call("GET", &route, protocol(problem)))
    }
}

/// The decision of one wait for a phase, as a function of the snapshots and the time.
///
/// The IDE sends a state only when it changes. So the dwell of `quiet_for` ends when time passes with no state in
/// another phase. The dwell starts again when the reader cannot know that no such state went past: after a gap of two
/// or more in the sequence, after a state of another process, and after a failed poll.
#[derive(Debug)]
pub struct PhaseWait {
    phase: Phase,
    quiet_for: Duration,
    expected_launch: Option<String>,
    /// The latest state, which a timeout names. It stays after a failed poll.
    latest: Option<State>,
    /// The `seq` and the pid of the latest snapshot, or `None` before the first one and after a failed poll.
    cursor: Option<(u64, i64)>,
    /// When the IDE entered [Self::phase], or `None` while it is in another phase.
    in_phase_since: Option<Instant>,
    restarts: u32,
    losses: u32,
    last_loss: Option<String>,
}

/// What [PhaseWait::verdict] decides.
#[derive(Debug)]
pub enum Verdict<'a> {
    Waiting,
    Reached(&'a State),
    /// The IDE cannot reach the phase: it quits, it belongs to another launch, or its control server did not bind.
    Refused(WaitError),
}

impl PhaseWait {
    /// A wait for `phase` with the dwell `quiet_for`. With `expected_launch`, a state of another launch is refused.
    pub fn new(phase: Phase, quiet_for: Duration, expected_launch: Option<&str>) -> Self {
        Self {
            phase,
            quiet_for,
            expected_launch: expected_launch.map(str::to_owned),
            latest: None,
            cursor: None,
            in_phase_since: None,
            restarts: 0,
            losses: 0,
            last_loss: None,
        }
    }

    /// The `after` of the next poll: the `seq` of the latest snapshot, or `None` when the reader has none.
    pub const fn after(&self) -> Option<u64> {
        match self.cursor {
            Some((seq, _)) => Some(seq),
            None => None,
        }
    }

    /// Takes the answer of a poll at `now`. The same `seq` or the next one of the same process continues the dwell. A
    /// gap, a lower `seq` and another pid start it again.
    pub fn observe(&mut self, snapshot: Snapshot, now: Instant) {
        let Snapshot { seq, state } = snapshot;
        if let Some((previous, pid)) = self.cursor
            && (pid != state.pid || (seq != previous && seq != previous + 1))
        {
            self.restarts += 1;
            self.in_phase_since = None;
        }
        self.in_phase_since = if state.phase == self.phase {
            self.in_phase_since.or(Some(now))
        } else {
            None
        };
        self.cursor = Some((seq, state.pid));
        self.latest = Some(state);
    }

    /// A poll failed with `reason`. The next answer can come from another process, so the dwell starts again.
    pub fn lost(&mut self, reason: String) {
        self.cursor = None;
        self.in_phase_since = None;
        self.losses += 1;
        self.last_loss = Some(reason);
    }

    pub fn verdict(&self, now: Instant) -> Verdict<'_> {
        let Some(state) = &self.latest else {
            return Verdict::Waiting;
        };
        if let (Some(expected), Some(launch)) = (&self.expected_launch, &state.launch)
            && expected != launch
        {
            return Verdict::Refused(WaitError::OtherLaunch {
                expected: expected.clone(),
                state: Box::new(state.clone()),
            });
        }
        if state.phase == Phase::Quitting && self.phase != Phase::Quitting {
            return Verdict::Refused(WaitError::Quitting {
                phase: self.phase,
                state: Box::new(state.clone()),
            });
        }
        if state.control_bind_failure.is_some() && self.phase == Phase::Ready {
            return Verdict::Refused(WaitError::ControlBindFailed(Box::new(state.clone())));
        }
        match self.in_phase_since {
            Some(since) if now.saturating_duration_since(since) >= self.quiet_for => Verdict::Reached(state),
            _ => Verdict::Waiting,
        }
    }

    /// How long the IDE must still stay in the phase, or `None` while it is in another phase.
    pub fn until_dwell_ends(&self, now: Instant) -> Option<Duration> {
        self.in_phase_since
            .map(|since| (since + self.quiet_for).saturating_duration_since(now))
    }

    fn timed_out(&self, timeout: Duration) -> WaitError {
        WaitError::TimedOut(Box::new(TimedOut {
            phase: self.phase,
            quiet_for: self.quiet_for,
            timeout,
            latest: self.latest.clone(),
            restarts: self.restarts,
            losses: self.losses,
            last_loss: self.last_loss.clone(),
        }))
    }
}

/// Why a wait for a phase answered no state.
#[derive(Debug)]
pub enum WaitError {
    /// A poll failed, and a later poll gets the same answer: a refused token, or an answer outside the contract.
    Call(Error),
    /// The IDE quits, so it cannot reach the phase.
    Quitting { phase: Phase, state: Box<State> },
    /// The state names another launch. The IDE of another lane answers on the port.
    OtherLaunch { expected: String, state: Box<State> },
    /// The control server of the controlled agents in the IDE did not bind, so the IDE never gets ready.
    ControlBindFailed(Box<State>),
    /// The process of the IDE ended, so it cannot reach the phase. The text says how it ended.
    Ended(String),
    /// The deadline passed first.
    TimedOut(Box<TimedOut>),
}

/// What a wait saw before its deadline passed.
#[derive(Debug)]
pub struct TimedOut {
    pub phase: Phase,
    pub quiet_for: Duration,
    pub timeout: Duration,
    /// The latest state, or `None` when no poll got an answer.
    pub latest: Option<State>,
    /// How often a gap in the sequence or another process started the dwell again.
    pub restarts: u32,
    /// How many polls failed.
    pub losses: u32,
    pub last_loss: Option<String>,
}

impl fmt::Display for WaitError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Call(error) => write!(formatter, "the lifecycle long poll failed: {error}"),
            Self::Quitting { phase, state } => write!(formatter, "the IDE quits and cannot reach {phase}: {state}"),
            Self::OtherLaunch { expected, state } => write!(
                formatter,
                "the lifecycle state names the launch {}, not {expected}, so the IDE of another lane answers on the port: {state}",
                state.launch.as_deref().unwrap_or_default()
            ),
            Self::Ended(reason) => write!(formatter, "the process of the IDE ended: {reason}"),
            Self::ControlBindFailed(state) => write!(
                formatter,
                "the IDE cannot reach READY, because its ACP control server did not bind; read idea.log of the IDE for the holder of the port: {state}"
            ),
            Self::TimedOut(seen) => {
                write!(formatter, "the IDE did not reach {}", seen.phase)?;
                if !seen.quiet_for.is_zero() {
                    write!(formatter, " for {:?}", seen.quiet_for)?;
                }
                write!(formatter, " within {:?}; the latest state was ", seen.timeout)?;
                match &seen.latest {
                    Some(state) => write!(formatter, "{state}")?,
                    None => formatter.write_str("never received")?,
                }
                if seen.restarts > 0 {
                    write!(
                        formatter,
                        "; the dwell started again {} time(s) after a gap in the sequence or another process",
                        seen.restarts
                    )?;
                }
                if let Some(reason) = &seen.last_loss {
                    write!(formatter, "; {} poll(s) failed, the last one with: {reason}", seen.losses)?;
                }
                Ok(())
            }
        }
    }
}

impl std::error::Error for WaitError {}
