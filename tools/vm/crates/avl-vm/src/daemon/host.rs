//! The daemon host: one controller process's daemon operations over one worker pool.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use crate::worker::worker::Manager;
use async_trait::async_trait;
use avl_base::{Config, Environment, Refusal, Reporter, SystemClock};
use avl_host_sys::guest::Guest;
use avl_host_sys::{Channel, Ctx, Interrupts, PollClock, Runner};

use crate::daemon::http::DaemonClient;
use crate::lane::secrets::{ProcessStdin, SecretStdin};

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// What this crate needs of the host's Bazel: the lane build, the descriptor resolution, and the two non-building
/// questions the guest-agent install asks.
///
/// A trait over [`crate::lane::Bazel`] (which implements it) so that a test of the digest lattice or the daemon
/// lifecycle never spawns Bazel - the same reasoning as [`avl_host_sys::guest::BazelHost`], which this extends.
#[async_trait]
pub(crate) trait BazelHost: avl_host_sys::guest::BazelHost {
    /// Runs one host build, logging to the path the caller names.
    async fn build(&self, ctx: &Ctx, log_path: &Path, label: &str) -> Result<(), Refusal>;
    /// Where the label's runtime descriptor landed.
    async fn runtime_descriptor(&self, ctx: &Ctx, label: &str) -> Result<PathBuf, Refusal>;
}

#[async_trait]
impl BazelHost for crate::lane::Bazel {
    async fn build(&self, ctx: &Ctx, log_path: &Path, label: &str) -> Result<(), Refusal> {
        Self::build(self, ctx, log_path, label).await
    }

    async fn runtime_descriptor(&self, ctx: &Ctx, label: &str) -> Result<PathBuf, Refusal> {
        Self::runtime_descriptor(self, ctx, label).await
    }
}

/// The two watchdog budgets one run is executed under: per execution, and between executions. Lane size never
/// enters the policy.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct WatchdogPolicy {
    pub active_execution: Duration,
    pub progress_gap: Duration,
}

/// Owns one controller process's daemon operations over one worker pool.
///
/// A struct rather than free functions because nearly everything here needs the collaborators the worker manager
/// already composes - settings, runner, reporter, the per-worker guest channel - plus the daemon control channel over
/// those guest channels, the host Bazel and the environment the invoking `USER` and `BUILD_WORKSPACE_DIRECTORY` are
/// read from. Shared by the tasks of a `shard` or a `flake` as an `Arc<Host>`, so every method takes `&self`.
pub(crate) struct Host {
    pub(crate) manager: Arc<Manager>,
    pub(crate) settings: Arc<Config>,
    pub(crate) runner: Runner,
    pub(crate) bazel: Arc<dyn BazelHost>,
    pub(crate) reporter: Reporter,
    pub(crate) environment: Environment,
    /// The process-wide interrupt service a leased run watches.
    pub(crate) interrupts: Interrupts,
    pub(crate) clock: Arc<dyn PollClock>,
    /// The control channel of every daemon in the pool, over the manager's guest channels.
    pub(crate) daemon: DaemonClient,
    /// One digest sweep at a time: the file digest cache is a single-owner document, and N workers of a pool
    /// share one.
    pub(crate) stamping: tokio::sync::Mutex<()>,
    /// What `run --test-env NAME=@-` reads: the process's stdin, or a suite's bytes.
    pub(crate) stdin: Arc<dyn SecretStdin>,
}

impl Host {
    /// A daemon host over the worker manager's pool. The interrupt service is the runner's, so the signal that
    /// reaches this host's children is the one a leased run watches.
    pub(crate) fn new(manager: Arc<Manager>, runner: Runner, bazel: Arc<dyn BazelHost>, environment: Environment) -> Self {
        Self {
            daemon: DaemonClient::over(&manager),
            settings: Arc::clone(manager.settings()),
            reporter: manager.reporter().clone(),
            interrupts: runner.interrupts().clone(),
            manager,
            runner,
            bazel,
            environment,
            clock: Arc::new(SystemClock),
            stamping: tokio::sync::Mutex::new(()),
            stdin: Arc::new(ProcessStdin),
        }
    }

    /// The same host polling on `clock`: for a suite, and for nothing else. The suites that use it are Unix only.
    #[cfg(all(test, unix))]
    #[must_use]
    pub(crate) fn with_poll_clock(mut self, clock: Arc<dyn PollClock>) -> Self {
        self.clock = clock;
        self
    }

    /// The same host reading `run --test-env NAME=@-` from `stdin`: for a suite. The suites that use it are Unix only.
    #[cfg(all(test, unix))]
    #[must_use]
    pub(crate) fn with_stdin(mut self, stdin: Arc<dyn SecretStdin>) -> Self {
        self.stdin = stdin;
        self
    }

    pub(crate) const fn manager(&self) -> &Arc<Manager> {
        &self.manager
    }

    pub(crate) const fn settings(&self) -> &Arc<Config> {
        &self.settings
    }

    pub(crate) const fn reporter(&self) -> &Reporter {
        &self.reporter
    }

    /// The control channel of every daemon in the pool.
    pub(crate) const fn daemon(&self) -> &DaemonClient {
        &self.daemon
    }

    /// The exec channel into one worker, through whatever the manager was built over.
    pub(crate) fn channel(&self, worker: &str) -> Arc<dyn Channel> {
        self.manager.channel(worker)
    }

    /// One guest session over `channel`.
    pub(crate) fn guest<'a>(&'a self, ctx: &'a Ctx, channel: &'a dyn Channel) -> Guest<'a> {
        Guest {
            ctx,
            settings: &self.settings,
            channel,
            reporter: &self.reporter,
        }
    }

    /// The watchdog policy of one run: the requested budgets where the invocation named them, the settings'
    /// otherwise.
    pub(crate) fn watchdog_policy(&self, active_execution: Option<Duration>, progress_gap: Option<Duration>) -> WatchdogPolicy {
        let budgets = &self.settings.daemon;
        WatchdogPolicy {
            active_execution: active_execution.unwrap_or(budgets.active_execution),
            progress_gap: progress_gap.unwrap_or(budgets.progress_gap),
        }
    }
}
