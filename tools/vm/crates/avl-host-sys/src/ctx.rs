//! The operation context: what travels down a call chain through the host code.
//!
//! Three things travel together:
//!
//! - **cancellation**, a [`CancellationToken`]: the interrupt service cancels the root;
//! - **phase accounting**, the command's [`Timeline`] and the [`PhaseHandle`] of the phase the work is inside. A
//!   handle and not an ambient "open phase", because `shard` and
//!   `flake` run several workers at once and an ambient value would be reassigned by whichever reached its next
//!   step first;
//! - **the run id** a journal and an envelope name, when the command has one.
//!
//! A value rather than a task-local, so a call that forgot to pass it does not compile instead of silently
//! recording into nothing. Cloning is cheap: two `Arc`s and a token.

use std::fmt;
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use avl_base::phase::{PhaseGuard, PhaseHandle, Timeline};
use avl_base::{Clock, FakeClock, SystemClock};
use tokio_util::sync::CancellationToken;

#[cfg(test)]
mod tests;

/// Cancellation, the phase timeline and handle, and the run id of one operation. See the module documentation.
#[derive(Clone, Default)]
pub struct Ctx {
    token: CancellationToken,
    timeline: Timeline,
    phase: PhaseHandle,
    run_id: Option<Arc<str>>,
}

impl Ctx {
    /// A context under `token`, collecting no phases and naming no run.
    pub fn new(token: CancellationToken) -> Self {
        Self { token, ..Self::default() }
    }

    /// A context nothing cancels: for tests and for one-shot work outside a command.
    pub fn background() -> Self {
        Self::default()
    }

    pub const fn token(&self) -> &CancellationToken {
        &self.token
    }

    pub fn is_cancelled(&self) -> bool {
        self.token.is_cancelled()
    }

    /// Completes when the operation is cancelled.
    pub async fn cancelled(&self) {
        self.token.cancelled().await;
    }

    /// Sleeps for `duration`, answering `false` when the operation was cancelled first. The one spelling of a poll
    /// loop's wait, so no loop can forget that a cancelled operation must stop sleeping.
    pub async fn sleep(&self, duration: Duration) -> bool {
        tokio::select! {
            () = tokio::time::sleep(duration) => true,
            () = self.token.cancelled() => false,
        }
    }

    /// The same context collecting into `timeline`.
    #[must_use]
    pub fn with_timeline(mut self, timeline: Timeline) -> Self {
        self.timeline = timeline;
        self
    }

    pub const fn timeline(&self) -> &Timeline {
        &self.timeline
    }

    /// The phase this work records into; a no-op handle when nothing is collected.
    pub const fn phase(&self) -> &PhaseHandle {
        &self.phase
    }

    /// Opens a named phase and answers the context the work inside it runs with. Dropping the guard closes the
    /// phase. This context keeps recording into the phase it was already in.
    pub fn begin(&self, name: &str) -> (Self, PhaseGuard) {
        let guard = self.timeline.begin(name);
        let inner = Self {
            phase: guard.handle(),
            ..self.clone()
        };
        (inner, guard)
    }

    /// The same context naming a run.
    #[must_use]
    pub fn with_run_id(mut self, run_id: impl Into<Arc<str>>) -> Self {
        self.run_id = Some(run_id.into());
        self
    }

    pub fn run_id(&self) -> Option<&str> {
        self.run_id.as_deref()
    }
}

impl fmt::Debug for Ctx {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Ctx")
            .field("cancelled", &self.is_cancelled())
            .field("collecting", &self.timeline.is_collecting())
            .field("run_id", &self.run_id)
            .finish_non_exhaustive()
    }
}

/// A [`Clock`] a poll loop also waits on: the boot and health polls, and the Parallels state waits.
///
/// A seam so a poll's suite does not sleep through a 20-minute boot budget: over a [`FakeClock`], time moves only
/// when the loop sleeps. Production code polls on [`SystemClock`], whose sleep is [`Ctx::sleep`]. Only a loop over a
/// call-driven fake may take a fake clock; a loop that watches a real process must wait in real time.
#[async_trait]
pub trait PollClock: Clock {
    /// Waits `duration`, answering `false` when the operation was cancelled first.
    async fn sleep(&self, ctx: &Ctx, duration: Duration) -> bool;
}

#[async_trait]
impl PollClock for SystemClock {
    async fn sleep(&self, ctx: &Ctx, duration: Duration) -> bool {
        ctx.sleep(duration).await
    }
}

/// Advances by exactly the duration and yields once, so the loop's other tasks run between two probes, then
/// answers whether the operation still stands.
#[async_trait]
impl PollClock for FakeClock {
    async fn sleep(&self, ctx: &Ctx, duration: Duration) -> bool {
        self.advance(duration);
        tokio::task::yield_now().await;
        !ctx.is_cancelled()
    }
}
