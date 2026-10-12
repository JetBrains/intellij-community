//! Phase timing for the operations whose cost the controller used to report as one word.
//!
//! A warm `daemon restart` was 82 s and the timing line said `ide daemon restart` about all of it. It is a
//! pipeline of roughly eighteen guest round-trips, two poll loops, a host Bazel build and a JVM boot, and the
//! breakdown is what took the warm restart from 95.7 s to 32.1 s. Four things are recorded per phase, because
//! elapsed alone cannot answer the question:
//!
//! - **Subprocess cost, split into guest and host.** If `tart exec` / `prlctl exec` invocations dominate, the fix
//!   is a persistent channel or batching, which no amount of faster host code would give.
//! - **The dearest single guest call.** Two calls of 2.7 s and fifteen of 50 ms are the same guest column and
//!   opposite repairs - as a skeleton of the argv, never the argv, because a guest command line carries secrets.
//!   See [`redact_guest_argv`].
//! - **Poll iterations and the last unsuccessful probe.** The gap between the last probe that failed and the one
//!   that succeeded is the sleep's contribution, and it is only separable if it is written down.
//! - **Unattributed time**, implicitly: a phase's elapsed minus what its subprocesses account for.
//!
//! # Handles, not ambient state
//!
//! `shard` and `flake` run several workers at once, and one ambient "open phase" would be reassigned by whichever
//! worker reached its next step first. So the open phase is a [`PhaseHandle`] the call chain carries (the host
//! crates keep it in their operation context), and collection is off unless a command asks for it: with an off
//! [`Timeline`] every operation here is a no-op, so nothing pays for it on the ordinary path.

use std::collections::HashSet;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use serde::Serialize;

use crate::clock::millis;
use crate::sync::lock;

#[cfg(test)]
mod tests;

/// One finished phase, as a command publishes it (the `phases` of the `daemon restart` envelope).
///
/// The two poll fields are optional because absent and zero are different facts: a phase that never polled
/// reports no iterations, while one whose first probe succeeded reports zero sleep.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Timing {
    pub name: String,
    pub elapsed_ms: u64,
    pub guest_calls: u32,
    pub guest_ms: u64,
    pub host_calls: u32,
    pub host_ms: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub poll_iterations: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_unsuccessful_probe_ms: Option<u64>,
    /// The dearest single guest call of this phase, and a skeleton of the argv that made it. Absent when the
    /// phase made none.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub slowest_guest_call_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub slowest_guest_call: Option<String>,
}

struct TimelineInner {
    state: Mutex<TimelineState>,
}

#[derive(Default)]
struct TimelineState {
    /// In the order they were opened. Shared, because a phase is still being written to after it is appended.
    phases: Vec<Arc<PhaseInner>>,
    /// Every argv head that reaches a guest. Both hypervisor binaries can appear in one process, since a lease
    /// receipt selects the backend, so this is a set.
    guest_heads: HashSet<String>,
}

/// Collects every phase one command opens. A clone is another handle to the same timeline; the default is off.
#[derive(Clone, Default)]
pub struct Timeline(Option<Arc<TimelineInner>>);

impl Timeline {
    /// Starts collecting.
    pub fn collecting() -> Self {
        Self(Some(Arc::new(TimelineInner {
            state: Mutex::new(TimelineState::default()),
        })))
    }

    pub const fn is_collecting(&self) -> bool {
        self.0.is_some()
    }

    /// Records that a command starting with this argv head reaches the guest.
    ///
    /// Called by whichever code builds a guest command line, which is the only place that knows. A spawn that
    /// never went through it is a host call by construction, so a caller cannot forget to declare itself into
    /// the wrong column - it can only fail to declare itself into the guest one, and then the cost still shows.
    pub fn declare_guest_head(&self, head: &str) {
        if let Some(timeline) = &self.0 {
            lock(&timeline.state).guest_heads.insert(head.to_owned());
        }
    }

    /// Every phase opened so far, in the order they were opened.
    pub fn take(&self) -> Vec<Timing> {
        let Some(timeline) = &self.0 else {
            return Vec::new();
        };
        let phases = lock(&timeline.state).phases.clone();
        phases.iter().map(|phase| phase.timing()).collect()
    }

    /// Opens a named phase. The guard's [`PhaseGuard::handle`] is what the work inside it records into, and
    /// dropping the guard closes it. Nesting is permitted: the inner phase wins for the work given its handle,
    /// and the outer handle keeps recording into the outer phase.
    pub fn begin(&self, name: &str) -> PhaseGuard {
        let Some(timeline) = &self.0 else {
            return PhaseGuard(PhaseHandle::default());
        };
        let phase = Arc::new(PhaseInner {
            timeline: timeline.clone(),
            name: name.to_owned(),
            started: Instant::now(),
            state: Mutex::new(PhaseState::default()),
        });
        lock(&timeline.state).phases.push(phase.clone());
        PhaseGuard(PhaseHandle(Some(phase)))
    }
}

struct PhaseInner {
    timeline: Arc<TimelineInner>,
    name: String,
    started: Instant,
    state: Mutex<PhaseState>,
}

#[derive(Default)]
struct PhaseState {
    elapsed: Duration,
    guest_calls: u32,
    guest_time: Duration,
    host_calls: u32,
    host_time: Duration,
    poll_iterations: Option<u32>,
    last_unsuccessful_probe: Option<Duration>,
    slowest_guest_call: Duration,
    slowest_guest_skeleton: String,
}

impl PhaseInner {
    fn timing(&self) -> Timing {
        let state = lock(&self.state);
        let guest_called = state.guest_calls > 0;
        Timing {
            name: self.name.clone(),
            elapsed_ms: millis(state.elapsed),
            guest_calls: state.guest_calls,
            guest_ms: millis(state.guest_time),
            host_calls: state.host_calls,
            host_ms: millis(state.host_time),
            poll_iterations: state.poll_iterations,
            last_unsuccessful_probe_ms: state.last_unsuccessful_probe.map(millis),
            slowest_guest_call_ms: guest_called.then(|| millis(state.slowest_guest_call)),
            slowest_guest_call: guest_called.then(|| state.slowest_guest_skeleton.clone()),
        }
    }
}

/// Closes its phase when it drops.
#[must_use = "the phase closes when the guard drops"]
pub struct PhaseGuard(PhaseHandle);

impl PhaseGuard {
    /// The open phase, to carry into the work inside it.
    pub fn handle(&self) -> PhaseHandle {
        self.0.clone()
    }
}

impl Drop for PhaseGuard {
    fn drop(&mut self) {
        if let Some(phase) = &(self.0).0 {
            lock(&phase.state).elapsed = phase.started.elapsed();
        }
    }
}

/// The phase a call chain is inside, or none when nothing is collected; every method is then a no-op, which is
/// what keeps call sites free of an `if`.
#[derive(Clone, Default)]
pub struct PhaseHandle(Option<Arc<PhaseInner>>);

impl PhaseHandle {
    pub const fn is_collecting(&self) -> bool {
        self.0.is_some()
    }

    /// Attributes one finished subprocess to this phase, in the guest column or the host one.
    pub fn record_subprocess(&self, argv: &[impl AsRef<str>], elapsed: Duration) {
        let (Some(phase), Some(head)) = (&self.0, argv.first()) else {
            return;
        };
        // The timeline's lock is released before the phase's is taken: the two are never held together.
        let guest = lock(&phase.timeline.state).guest_heads.contains(head.as_ref());
        let mut state = lock(&phase.state);
        if !guest {
            state.host_calls += 1;
            state.host_time += elapsed;
            return;
        }
        state.guest_calls += 1;
        state.guest_time += elapsed;
        // The first call is the slowest one until another is dearer, which is why the count decides rather than
        // the duration: a phase whose only guest call returned instantly still has a dearest call.
        if state.guest_calls == 1 || elapsed > state.slowest_guest_call {
            state.slowest_guest_call = elapsed;
            state.slowest_guest_skeleton = guest_call_skeleton(argv);
        }
    }

    /// Records how many times this phase's loop probed, and how long after the phase started the last
    /// *unsuccessful* probe returned: `None` when the first probe succeeded, so no sleep was ever paid.
    pub fn record_poll(&self, iterations: u32, last_unsuccessful_probe: Option<Duration>) {
        let Some(phase) = &self.0 else {
            return;
        };
        let mut state = lock(&phase.state);
        state.poll_iterations = Some(iterations);
        if last_unsuccessful_probe.is_some() {
            state.last_unsuccessful_probe = last_unsuccessful_probe;
        }
    }

    /// How long this phase has been open, which a poll loop needs to compute the argument above; zero when
    /// nothing is collected.
    pub fn elapsed(&self) -> Duration {
        self.0.as_ref().map_or(Duration::ZERO, |phase| phase.started.elapsed())
    }
}

/// How much of a guest argv a redacted rendering keeps. See [`redact_guest_argv`].
///
/// `elements` and `element_bytes` are optional: zero means that bound does not apply. `bytes` always applies,
/// because the point of a bounded rendering is that no caller can produce an unbounded one by leaving a field
/// out.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct ArgvBounds {
    /// How many argv elements are rendered at all.
    pub elements: usize,
    /// How long an element may be before it is replaced whole.
    pub element_bytes: usize,
    /// Caps the whole rendering.
    pub bytes: usize,
}

/// What a phase's dearest-call rendering keeps of an argv. Tighter than a refusal's, because this string sits in
/// a table beside five numbers and only has to identify the call.
pub const SKELETON_BOUNDS: ArgvBounds = ArgvBounds {
    elements: 6,
    element_bytes: 48,
    bytes: 120,
};

/// What an element that may carry a value is rendered as. One character, so a redacted argv still reads as one.
pub const REDACTED_ELEMENT: &str = "…";

/// The one rendering of a guest argv for a human: enough to recognise which call it was, and nothing of what it
/// carried.
///
/// A guest argv can carry the UI-test bridge token, a `-D` property naming a path under someone's home, or a
/// `sudo` account, and every string this renders lands in a JSON envelope an agent reads and may log. So an
/// element holding a value (`=` or `:`) or longer than the element bound becomes [`REDACTED_ELEMENT`], and the
/// rendering stops rather than cutting an element: `tart exec air-linux-1 … … …`.
pub fn redact_guest_argv(argv: &[impl AsRef<str>], bounds: ArgvBounds) -> String {
    let mut rendered = String::new();
    for (kept, element) in argv.iter().map(AsRef::as_ref).enumerate() {
        if bounds.elements > 0 && kept == bounds.elements {
            break;
        }
        let redacted = element.contains(['=', ':']) || (bounds.element_bytes > 0 && element.len() > bounds.element_bytes);
        let element = if redacted { REDACTED_ELEMENT } else { element };
        let separator = usize::from(kept > 0);
        // Stopped rather than truncated: a cut at a byte limit can land inside a multi-byte character, and the
        // one reader of this string is a human.
        if rendered.len() + separator + element.len() > bounds.bytes {
            break;
        }
        if separator > 0 {
            rendered.push(' ');
        }
        rendered.push_str(element);
    }
    // An argv whose first element alone did not fit still renders as one. Only `bytes` left at zero reaches this,
    // a caller error, and answering it with the ellipsis rather than the argv is what makes it fail closed.
    if rendered.is_empty() && !argv.is_empty() {
        return REDACTED_ELEMENT.to_owned();
    }
    rendered
}

/// What a phase says about its dearest guest call.
fn guest_call_skeleton(argv: &[impl AsRef<str>]) -> String {
    redact_guest_argv(argv, SKELETON_BOUNDS)
}
