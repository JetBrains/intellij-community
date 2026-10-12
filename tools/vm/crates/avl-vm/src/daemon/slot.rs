//! Whether the run holding a worker's slot is this controller's own daemon, parked.
//!
//! The question belongs here because only this crate can ask it - it takes the daemon record on the host and the
//! daemon's own status over the control channel - and the caller that needs it is the lease layer, which must not
//! depend on this crate. So the trait is declared in `avl-host-sys` and implemented here, the same seam
//! [`crate::daemon::BazelHost`] uses to keep Bazel out of the guest setup.

use std::sync::Arc;

use async_trait::async_trait;
use avl_base::{Config, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ParkedDaemonProbe;

use crate::daemon::http::DaemonClient;
use crate::daemon::state::HostState;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// [`ParkedDaemonProbe`] against the real daemon of one pool.
///
/// It carries the settings and the control channel and nothing else: both of its questions are stateless reads, and
/// a probe that cached an answer would be a probe whose answer aged while the caller was deciding on it.
#[derive(Clone, Debug)]
pub(crate) struct ParkedDaemon {
    settings: Arc<Config>,
    daemon: DaemonClient,
}

/// The probe the lease operations ask.
pub(crate) const fn parked_daemon_probe(settings: Arc<Config>, daemon: DaemonClient) -> ParkedDaemon {
    ParkedDaemon { settings, daemon }
}

#[async_trait]
impl ParkedDaemonProbe for ParkedDaemon {
    /// The run id of the worker's parked daemon, or `None`.
    ///
    /// Every way of not being able to prove "parked" answers `None`: no record, a daemon that does not answer, a
    /// daemon whose protocol this controller refuses, one that is busy. The direction is deliberate - `None` makes
    /// the caller refuse - because the alternative is releasing a worker out from under an iteration on the
    /// strength of a question that failed. The protocol refusal of [`DaemonClient::status`] is folded in on purpose: a
    /// probe is not the place to fail a lease operation over a daemon version.
    async fn parked_daemon_run(&self, ctx: &Ctx, worker: &str) -> Result<Option<String>, Refusal> {
        let Some(state) = HostState::read(&self.settings, worker) else {
            return Ok(None);
        };
        match self.daemon.status(ctx, &state).await {
            Ok(Some(status)) if !status.busy => Ok(Some(state.run_id)),
            _ => Ok(None),
        }
    }

    /// The run id this controller last recorded for the worker's daemon. It asks the daemon nothing.
    fn recorded_daemon_run(&self, worker: &str) -> Option<String> {
        HostState::read(&self.settings, worker).map(|state| state.run_id)
    }
}
