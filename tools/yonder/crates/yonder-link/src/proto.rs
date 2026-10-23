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

/// The prefix of the refs that a client keeps on the host as bases of its packs, see [`Request::Pack`].
pub const BASE_REFS: &str = "refs/yonder/base/";

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
    /// pack travels, and waits for the reference before the checkout, up to [`PACK_WAIT_SECONDS`]. A cancel ends that
    /// wait too. A commit that the host has already needs no pack, and the supervisor does not wait for it.
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
}

/// What one run does.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
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
    /// The agent refuses or the run failed. `code` is stable; the API.md of this crate lists each code.
    Refused {
        code: String,
        message: String,
    },
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

/// One step of a run and its duration: `lock`, `lease`, `pack_wait`, `checkout`, `command`, `stage`. A client
/// adds its own steps, such as `pack`, `upload` and `fetch`, when it reports a run.
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
    pub fn refused(code: &str, message: impl Into<String>) -> Self {
        Self::Refused {
            code: code.to_owned(),
            message: message.into(),
        }
    }
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
