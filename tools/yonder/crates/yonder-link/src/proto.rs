//! The protocol: one request per connection.
//!
//! Each connection starts with [`Request::Hello`] and its [`Reply::Hello`], so a client learns at once that the agent
//! speaks another schema. Then the client sends one more request. The replies of each request are listed on it. Any
//! request can be answered by [`Reply::Refused`] instead.
//!
//! A request that waits, for a lock, for `bd` or for `git`, gets [`Reply::Heartbeat`] every [`HEARTBEAT_SECONDS`]
//! before its last reply. A client resets its read deadline at each heartbeat, so a short deadline holds for every
//! request.
//!
//! A run lives on the agent, not on the connection. [`Request::Start`] answers at once, and [`Request::Follow`] reads
//! the output from an offset, so a client that lost its connection follows again from the last offset it saw.

use serde::{Deserialize, Serialize};

/// The version of this protocol. A change that an older end cannot read raises it.
pub const SCHEMA_VERSION: u32 = 2;

/// How often the agent sends [`Reply::Heartbeat`] while a request waits or a followed run is quiet.
pub const HEARTBEAT_SECONDS: u64 = 15;

/// How long the supervisor of a run waits for the pack of its reference, see [`Request::Start`].
pub const PACK_WAIT_SECONDS: u64 = 30 * 60;

/// How long the supervisor of a run waits for the next chunk of the pack of its reference. A client sends a chunk at
/// least every [`HEARTBEAT_SECONDS`] on the connection of the pack, an empty one while it builds the pack. So a client
/// that is gone ends the wait of its run in this time, and the lock and the lease of the holder go.
pub const PACK_STALL_SECONDS: u64 = 120;

/// The prefix of the refs that a client keeps on the host as bases of its packs, see [`Request::Pack`].
pub const BASE_REFS: &str = "refs/yonder/base/";

/// How long an agent that answered [`Reply::Upgraded`] waits for its other requests to end before it exits, so that
/// launchd starts the new binary. A follow does not count: its client connects again.
pub const UPGRADE_DRAIN_SECONDS: u64 = 20;

/// How long the watchdog of an upgrade waits for the new binary to listen, after the drain, before it restores the
/// previous binary and restarts the agent.
pub const UPGRADE_WATCHDOG_SECONDS: u64 = 60;

/// The largest agent binary of [`Request::Upgrade`].
pub const MAX_BINARY: u64 = 256 * 1024 * 1024;

/// What an agent does beyond the base of [`SCHEMA_VERSION`]. [`Reply::Hello`] lists the features of the agent. An agent
/// ignores a field of a request that it does not know, so a client checks a feature before it relies on its field.
pub mod feature {
    /// The agent reads `.yonder.toml` in the root of the commit: its prepare steps, its environment and its outputs, see
    /// [`RunSpec::outputs`](super::RunSpec::outputs).
    pub const CONFIG: &str = "config";
    /// The agent gives the prepare steps and the command the login environment of its user and a raised limit of open
    /// files.
    pub const LOGIN_ENV: &str = "login_env";
    /// [`RunSpec::nice`](super::RunSpec::nice).
    pub const NICE: &str = "nice";
    /// [`RunSpec::changes`](super::RunSpec::changes).
    pub const CHANGES: &str = "changes";
    /// [`Request::Upgrade`](super::Request::Upgrade), and the commands `yonderd upgrade` and `yonderd watchdog`. Only an
    /// agent on macOS has it, because only launchd starts the new binary.
    pub const UPGRADE: &str = "upgrade";
}

/// The features of an agent of this build on every platform.
pub const FEATURES: [&str; 4] = [feature::CONFIG, feature::LOGIN_ENV, feature::NICE, feature::CHANGES];

/// The features of an agent of this build on this platform, for its [`Reply::Hello`]: [`FEATURES`], and
/// [`feature::UPGRADE`] on macOS.
pub fn features() -> Vec<&'static str> {
    let upgrade = cfg!(target_os = "macos").then_some(feature::UPGRADE);
    FEATURES.into_iter().chain(upgrade).collect()
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "request", rename_all = "snake_case")]
pub enum Request {
    /// The first message of each connection. Reply: [`Reply::Hello`], or [`Reply::Refused`] with `schema`.
    Hello { schema_version: u32, client_version: String },
    /// Which of `commits` the repository has as commit objects. Reply: [`Reply::Have`].
    Have { repo: String, commits: Vec<String> },
    /// A thin pack follows as a byte stream. The agent indexes it into the repository and points `reference` at
    /// `commit`. Reply: [`Reply::Packed`].
    ///
    /// A `reference` under [`BASE_REFS`] is a base of later packs, such as the master of the client. The agent keeps it
    /// until the next pack to the same name, and its cleanup never deletes it.
    Pack { repo: String, commit: String, reference: String },
    /// Starts a run. Reply: [`Reply::Started`] as soon as the run has its directory.
    ///
    /// A start can come before the pack of its `reference`. The supervisor then takes the lock and the lease while the
    /// pack travels, and waits for the pack before the checkout, up to [`PACK_WAIT_SECONDS`], and up to
    /// [`PACK_STALL_SECONDS`] without a chunk of it. A cancel ends that wait too. A commit that the host has already
    /// needs no pack, and the supervisor does not wait for it.
    Start(RunSpec),
    /// The output of a run from the byte offset `offset` of its log. Replies: [`Reply::Output`] and
    /// [`Reply::Heartbeat`] while the run goes on, then [`Reply::Phases`], then [`Reply::Exit`], or [`Reply::Refused`]
    /// when the run failed before or without an exit code of its command.
    Follow { run: String, offset: u64 },
    /// Asks the supervisor of a run to interrupt it: SIGINT to the process group of the command, then SIGKILL after the
    /// grace time, or no command at all when the run has not started it yet. Reply: [`Reply::Done`] as soon as the
    /// supervisor has the request. The run ends later, and [`Request::Follow`] shows its end.
    Cancel { run: String },
    /// The files that the run staged. Replies: [`Reply::File`] each followed by its bytes, then [`Reply::Fetched`].
    Fetch { run: String },
    /// Removes a run and its stage. Reply: [`Reply::Done`].
    Forget { run: String },
    /// The runs that the agent keeps. Reply: [`Reply::Runs`].
    Runs,
    /// Takes or reuses the lease of `holder` without a run. Reply: [`Reply::Leased`].
    Lease { repo: String, holder: String },
    /// Returns the lease of `holder`. Reply: [`Reply::Done`].
    Release { repo: String, holder: String },
    /// Replaces the agent binary that the LaunchAgent names. The new binary follows as a byte stream of `size` bytes
    /// whose SHA-256 is `sha256` (64 lower-case hex digits). Replies: [`Reply::Heartbeat`] while the agent checks the
    /// binary, then [`Reply::Upgraded`]. Feature [`feature::UPGRADE`].
    ///
    /// After the reply the agent waits up to [`UPGRADE_DRAIN_SECONDS`] for its other requests, and exits, so that
    /// launchd starts the new binary. A watchdog restores the previous binary when the new one does not listen within
    /// [`UPGRADE_WATCHDOG_SECONDS`] after that. A run goes on, because its supervisor has a session of its own.
    Upgrade { size: u64, sha256: String },
}

/// What one run does. A field after `idle_hours` is optional: an older client does not send it, and an older agent
/// ignores it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct RunSpec {
    /// The id that the client chose: `<epoch ms>-<pid>`. It names the run directory, so it is `[0-9A-Za-z._-]+`.
    pub run: String,
    /// The main repository on the host. A leading `~/` is the home directory of the agent.
    pub repo: String,
    /// The lease holder: one per local checkout, so two checkouts never share a slot.
    pub holder: String,
    /// The commit to check out in the slot.
    pub commit: String,
    /// The ref that [`Request::Pack`] made. The run deletes it on every way out.
    pub reference: String,
    /// The command and its arguments, run in the root of the slot.
    pub command: Vec<String>,
    /// Directories relative to the slot root, such as `out/bt-runs`. Each entry that the command adds to one goes to
    /// the stage of the run, for [`Request::Fetch`].
    pub fetch: Vec<String>,
    /// A lease of another holder that no run used for this many hours goes back to the pool after the run.
    pub idle_hours: u32,
    /// The niceness of the prepare steps and the command, 0 to 19. `None` keeps the niceness of the agent. Feature
    /// [`feature::NICE`].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub nice: Option<u8>,
    /// Stage the outputs that `.yonder.toml` of the commit names, for [`Request::Fetch`]. The agent removes each output
    /// before the command, so only the outputs of this run come back. Without it the agent leaves the outputs alone.
    /// Feature [`feature::CONFIG`].
    #[serde(default, skip_serializing_if = "is_false")]
    pub outputs: bool,
    /// Stage the changes that the command made to the files of the slot, as a patch for `git apply`, for
    /// [`Request::Fetch`]. Feature [`feature::CHANGES`].
    #[serde(default, skip_serializing_if = "is_false")]
    pub changes: bool,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "reply", rename_all = "snake_case")]
pub enum Reply {
    Hello {
        schema_version: u32,
        agent_version: String,
        /// `std::env::consts::OS` and `ARCH` of the agent.
        os: String,
        arch: String,
        /// The features of the agent, see [`feature`]. An older agent sends none.
        #[serde(default, skip_serializing_if = "Vec::is_empty")]
        features: Vec<String>,
        /// The SHA-256 of the binary of the agent, see [`crate::digest`]. An older agent, or one that cannot read its
        /// binary, sends none.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        binary_sha256: Option<String>,
    },
    Have {
        commits: Vec<String>,
    },
    Packed,
    Started {
        run: String,
    },
    /// One line of the output of a run, without its line end. `end` is the offset after it.
    Output {
        stream: Stream,
        text: String,
        end: u64,
    },
    Heartbeat {
        elapsed_ms: u64,
    },
    /// How long each step of the run took on the host, in the order of the steps. It comes before the last reply of a
    /// `Follow` of a run that has ended.
    Phases {
        phases: Vec<Phase>,
    },
    /// The exit code of the command. A command that a signal ended answers 128 plus the signal number.
    Exit {
        code: i32,
    },
    /// One staged file, relative to the slot root with `/` separators. Its bytes follow as a byte stream.
    File {
        path: String,
        size: u64,
        executable: bool,
        /// What the file is. An older agent sends only [`FileKind::Staged`], and a client that set neither
        /// [`RunSpec::outputs`] nor [`RunSpec::changes`] gets only those.
        #[serde(default, skip_serializing_if = "FileKind::is_staged")]
        kind: FileKind,
    },
    Fetched {
        files: u64,
        bytes: u64,
    },
    Runs {
        runs: Vec<RunInfo>,
    },
    Leased {
        slot: String,
        lease_id: String,
        /// `false` when the holder had the lease already.
        fresh: bool,
    },
    Done {
        message: String,
    },
    /// The new binary of [`Request::Upgrade`] is in place. `previous_sha256` names the binary that the watchdog
    /// restores, and `version` is the version that the new binary printed.
    Upgraded {
        previous_sha256: String,
        sha256: String,
        version: String,
    },
    /// The agent refuses or the run failed. `code` is stable; the API.md of this crate lists each code.
    Refused {
        code: String,
        message: String,
    },
}

/// What a fetched file is.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum FileKind {
    /// A new entry of a `fetch` directory of the run.
    #[default]
    Staged,
    /// A file of the output `root`, a path that `.yonder.toml` names. The path of the file is `root` or below it. The
    /// client replaces its own copy of `root` with the one of the run.
    Output { root: String },
    /// The changes that the command made to the files of the slot, as a patch for `git apply` in the root of a
    /// checkout. The path of the file is a name, not a path of the slot.
    Changes,
}

impl FileKind {
    pub const fn is_staged(&self) -> bool {
        matches!(self, Self::Staged)
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Stream {
    Stdout,
    Stderr,
}

/// A run that the agent keeps.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RunInfo {
    pub run: String,
    pub holder: String,
    pub commit: String,
    pub command: Vec<String>,
    pub started_ms: u64,
    /// `None` while the run goes on.
    pub exit: Option<i32>,
    /// The steps that have ended so far.
    #[serde(default)]
    pub phases: Vec<Phase>,
    /// The `fetch` directories of [`RunSpec`]. An agent before this field sends none.
    #[serde(default)]
    pub fetch: Vec<String>,
}

/// One step of a run and its duration: `lock`, `lease`, `pack_wait`, `checkout`, `prepare`, `command`, `stage`. An older
/// agent has no `prepare`. A client adds its own steps, such as `pack`, `upload` and `fetch`, when it reports a run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Phase {
    pub name: String,
    pub ms: u64,
}

impl Request {
    /// The hello of this build of the client.
    pub fn hello(client_version: &str) -> Self {
        Self::Hello {
            schema_version: SCHEMA_VERSION,
            client_version: client_version.to_owned(),
        }
    }
}

impl Reply {
    /// The hello of an agent of `agent_version` and the binary `binary_sha256` on the platform of this build, with the
    /// features of this build.
    pub fn hello(agent_version: &str, binary_sha256: Option<String>) -> Self {
        Self::Hello {
            schema_version: SCHEMA_VERSION,
            agent_version: agent_version.to_owned(),
            os: std::env::consts::OS.to_owned(),
            arch: std::env::consts::ARCH.to_owned(),
            features: features().into_iter().map(str::to_owned).collect(),
            binary_sha256,
        }
    }

    pub fn refused(code: &str, message: impl Into<String>) -> Self {
        Self::Refused {
            code: code.to_owned(),
            message: message.into(),
        }
    }
}

/// serde calls `skip_serializing_if` with a reference.
const fn is_false(value: &bool) -> bool {
    !*value
}

/// Whether `run` can name a run directory, a lease file and the last part of a git ref. A run id names the ref
/// `refs/yonder/<run>`, and a holder names `refs/yonder/base/<holder>`, so the rules of git refs hold too: no `..`, and
/// no `.` or `.lock` at the end.
pub fn is_run_id(run: &str) -> bool {
    !run.is_empty()
        && run.len() <= 128
        && !run.starts_with('.')
        && !run.ends_with('.')
        && !run.ends_with(".lock")
        && !run.contains("..")
        && run
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
}

#[cfg(test)]
mod tests;
