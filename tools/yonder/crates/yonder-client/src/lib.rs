//! The client of yonder: what `yonder` and `bt --host` share.
//!
//! - [`config`]: the identity of the client and one host file per alias, under `~/.config/yonder/`.
//! - [`host`]: the address choice and the hello of each connection to an agent.
//! - [`process`]: the seam of the local calls: `bd host` of Branch Deck, which does every git call, ssh and Bazel.
//! - [`run`]: a run of a command of the committed HEAD or of the working tree on a host, the sync of its base, the
//!   outputs and the changes that come back, and the requests of one reply.
//! - `record`: the local record of each run, which says what a fetch of the run takes.
//! - [`phases_line`]: the durations of the steps of a run.
//! - [`prepare`]: makes a host ready over ssh.
//! - [`upgrade`]: updates the agent of a host over the link, without ssh.
//! - [`Client::build_agent`]: builds the agent with Bazel in a checkout of the monorepo, for `prepare` and `upgrade`.
//! - [`interrupt`]: Ctrl+C of the person at the terminal.
//!
//! Each failure is a [`Refusal`] with a stable code of [`code`], or the code of the agent. `API.md` lists them.

mod agent;
mod base;
mod bd;
pub mod config;
mod fetch;
pub mod host;
pub mod interrupt;
mod pack;
mod phases;
pub mod prepare;
pub mod process;
mod record;
pub mod run;
#[cfg(test)]
mod testkit;
pub mod upgrade;

use std::path::Path;
use std::time::Duration;

use yonder_link::proto::{HEARTBEAT_SECONDS, UPGRADE_DRAIN_SECONDS, UPGRADE_WATCHDOG_SECONDS};

pub use agent::{AGENT_BUILD_QUIET, AGENT_TARGET, agent_label, no_monorepo};
pub use config::{Config, HostFile};
pub use host::{AgentInfo, Host};
pub use phases::{duration_text, phases_line, phases_short};
pub use prepare::{PrepareRequest, Prepared};
pub use process::{Call, Limit, OsProcesses, Processes, Ran, Stop, Stopped, never};
pub use run::{Followed, RunOptions, RunOutcome, RunRequest};
pub use upgrade::{UpgradeRequest, UpgradeWay, Upgraded};
pub use yonder_link::DEFAULT_PORT;
pub use yonder_link::proto::{Phase, RunInfo};

/// The exit vocabulary of yonder. A run leaves by the exit code of its command, which is no refusal. A refusal of a run
/// tells whether the command ran.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Exit {
    /// The host did not run the command: the caller can run it locally instead. 75, `EX_TEMPFAIL` of `sysexits.h`.
    NotRun,
    /// Ctrl+C stopped the run before its command, or a second Ctrl+C stopped the follow. 130.
    Interrupted,
    /// Any other refusal: the run can go on on the host after a lost link, its changes did not apply, or a request other
    /// than a run failed. 255.
    Refused,
}

impl Exit {
    pub const fn code(self) -> u8 {
        match self {
            Self::NotRun => 75,
            Self::Interrupted => 130,
            Self::Refused => 255,
        }
    }
}

/// A failure of the client, or a refusal of the agent with its own code.
pub type Refusal = refusal::Refusal<Exit>;

/// The stable codes of the refusals of the client. A refusal of the agent keeps the code of the agent, such as
/// `lease_failed`: the `API.md` of `yonder-link` lists those.
pub mod code {
    /// An alias that cannot name a host file, or a request without a command.
    pub const USAGE: &str = "usage";
    /// No home directory, or an identity that the client cannot read or create.
    pub const CONFIG: &str = "config";
    /// No host file for the alias: the host was never prepared.
    pub const NO_HOST: &str = "no_host";
    /// A host file that is not JSON of the host file shape.
    pub const BAD_HOST_FILE: &str = "bad_host_file";
    /// No address of the host file and no address of the host name gave a connection.
    pub const UNREACHABLE: &str = "unreachable";
    /// The agent showed a certificate that the host file does not pin, or it refused the certificate of this client.
    pub const UNTRUSTED: &str = "untrusted";
    /// The agent speaks another schema version.
    pub const SCHEMA: &str = "schema";
    /// The agent sent nothing within the deadline of a reply.
    pub const TIMED_OUT: &str = "timed_out";
    /// The connection closed, or a frame or a reply came that the protocol does not allow there.
    pub const LINK: &str = "link";
    /// The checkout has local changes, and only a commit reaches the host.
    pub const LOCAL_CHANGES: &str = "local_changes";
    /// The host has none of the commits near HEAD, so the pack would hold the whole history.
    pub const BEHIND: &str = "behind";
    /// A call of `bd host` of Branch Deck failed: bd is missing or older than the client, or its git failed. The message
    /// names the code of bd.
    pub const BD_FAILED: &str = "bd_failed";
    /// `.yonder.toml` of the shipped commit does not parse, so the client cannot tell which outputs come back.
    pub const BAD_CONFIG: &str = "bad_config";
    /// An ssh call of `prepare` failed or did not end within its deadline.
    pub const SSH_FAILED: &str = "ssh_failed";
    /// The agent binary of `prepare` cannot be read, or it does not run on the host.
    pub const AGENT_FAILED: &str = "agent_failed";
    /// Ctrl+C came before the run started, or a second Ctrl+C stopped the follow of a run.
    pub const INTERRUPTED: &str = "interrupted";
    /// Fetch did not copy every staged file of a run, so the run stays on the host.
    pub const FETCH_FAILED: &str = "fetch_failed";
    /// The agent lacks a feature that the request needs, such as an upgrade request: it is older than the client.
    pub const OLD_AGENT: &str = "old_agent";
    /// The changes of the command did not apply to the working tree. The patch stays in a file.
    pub const APPLY_FAILED: &str = "apply_failed";
    /// The new agent of an upgrade did not listen in time, and the watchdog brought back the previous agent.
    pub const ROLLED_BACK: &str = "rolled_back";
}

pub(crate) fn refused(code: &'static str, message: impl Into<String>) -> Refusal {
    Refusal::new(code, Exit::Refused, message)
}

/// The text that tells how to install the agent and write the host file.
pub(crate) fn prepare_hint(alias: &str) -> String {
    format!(
        "`bt --host {alias} --prepare` or `yonder prepare {alias}` in a checkout of the monorepo installs the agent and writes the host file"
    )
}

/// The version of this client, for the hello.
pub(crate) const VERSION: &str = env!("CARGO_PKG_VERSION");

/// The deadlines of the client.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Timing {
    /// The TCP connect and the handshake of one address.
    pub(crate) connect: Duration,
    /// The lookup of the host name.
    pub(crate) resolve: Duration,
    /// The wait for each frame of a reply, and the deadline of each write. A request that waits gets a heartbeat every
    /// 15 s. A host that clones a new slot can hold back its heartbeats for a minute, so only two quiet minutes are a
    /// lost link.
    pub(crate) read: Duration,
    /// How long `Follow` connects again after the last reply of the agent.
    pub(crate) reconnect_for: Duration,
    /// The first wait before a new connection of `Follow`. It doubles up to `max_backoff`.
    pub(crate) first_backoff: Duration,
    pub(crate) max_backoff: Duration,
    /// How long `prepare` waits for the agent that `yonderd setup` started.
    pub(crate) agent_start: Duration,
    /// How often the connection of a pack gets an empty chunk while git builds the pack.
    pub(crate) keepalive: Duration,
    /// How long `upgrade` waits for the hello of the new agent: the drain of the old agent, the wait of the watchdog,
    /// and 30 s for launchd.
    pub(crate) upgrade: Duration,
}

impl Timing {
    pub(crate) const DEFAULT: Self = Self {
        connect: Duration::from_secs(5),
        resolve: Duration::from_secs(5),
        read: Duration::from_secs(120),
        reconnect_for: Duration::from_secs(600),
        first_backoff: Duration::from_secs(1),
        max_backoff: Duration::from_secs(30),
        agent_start: Duration::from_secs(60),
        keepalive: Duration::from_secs(HEARTBEAT_SECONDS),
        upgrade: Duration::from_secs(UPGRADE_DRAIN_SECONDS + UPGRADE_WATCHDOG_SECONDS + 30),
    };
}

/// The client of one process: its configuration, its seam for the local calls, the sink of its stderr lines, and the
/// count of the Ctrl+C presses.
pub struct Client<'a> {
    config: Config,
    processes: &'a dyn Processes,
    /// The program of Branch Deck.
    bd: &'a str,
    say: &'a (dyn Fn(&str) + Sync),
    /// The sink of the stdout lines of a run, or `None` to collect them into [`RunOutcome::stdout`].
    stdout: Option<&'a (dyn Fn(&str) + Sync)>,
    presses: &'a (dyn Fn() -> u32 + Sync),
    timing: Timing,
}

impl<'a> Client<'a> {
    /// `say` takes each stderr line of a run as it comes, and each progress line of the client. `presses` answers how
    /// many times the person pressed Ctrl+C; [`interrupt::presses`] is the count of the terminal.
    pub fn new(
        config: Config,
        processes: &'a dyn Processes,
        say: &'a (dyn Fn(&str) + Sync),
        presses: &'a (dyn Fn() -> u32 + Sync),
    ) -> Self {
        Self {
            config,
            processes,
            bd: bd::PROGRAM,
            say,
            stdout: None,
            presses,
            timing: Timing::DEFAULT,
        }
    }

    /// Sends each stdout line of a run to `stdout` as it comes, instead of [`RunOutcome::stdout`] at the end.
    #[must_use]
    pub const fn with_stdout(mut self, stdout: &'a (dyn Fn(&str) + Sync)) -> Self {
        self.stdout = Some(stdout);
        self
    }

    /// Whether the person pressed Ctrl+C.
    fn interrupted(&self) -> bool {
        (self.presses)() > 0
    }

    /// Runs a local child, which Ctrl+C stops.
    fn child(&self, call: &Call) -> Ran {
        self.processes.run(call, &|| self.interrupted())
    }

    pub const fn config(&self) -> &Config {
        &self.config
    }

    /// The host of an alias, from its host file.
    pub fn host(&self, alias: &str) -> Result<Host, Refusal> {
        Host::open(&self.config, alias, self.timing)
    }

    /// Calls `bd` as `program`, such as a fake bd of a test.
    #[cfg(test)]
    pub(crate) const fn with_bd(mut self, program: &'a str) -> Self {
        self.bd = program;
        self
    }

    #[cfg(test)]
    pub(crate) const fn with_timing(mut self, timing: Timing) -> Self {
        self.timing = timing;
        self
    }
}

/// The lease holder of a local checkout on a host: `<prefix>-<12 hex digits>`, one per checkout path, so two checkouts
/// never share a slot there. The hash is FNV-1a, stable across builds and platforms, unlike the hasher of the standard
/// library.
pub fn holder(prefix: &str, checkout: &Path) -> String {
    let hash = checkout.to_string_lossy().bytes().fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
        (hash ^ u64::from(byte)).wrapping_mul(0x0100_0000_01b3)
    });
    format!("{prefix}-{:012x}", hash & 0xffff_ffff_ffff)
}

/// A count of bytes for a person: `512 B`, `3.4 MiB`.
pub(crate) fn size_text(bytes: u64) -> String {
    match bytes {
        0..1024 => format!("{bytes} B"),
        1024..1_048_576 => format!("{:.1} KiB", bytes as f64 / 1024.0),
        _ => format!("{:.1} MiB", bytes as f64 / 1_048_576.0),
    }
}

/// A value quoted for a POSIX shell.
pub(crate) fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

#[cfg(test)]
mod tests;
