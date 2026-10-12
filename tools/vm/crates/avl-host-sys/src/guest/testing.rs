//! The seams this module's tests share.
//!
//! The one seam that matters is [`FakeChannel`]: a guest command is answered directly, so no VM, no hypervisor
//! binary and no network is involved. What the tests assert on is mostly *argv*, and deliberately so: almost every
//! defect these tests guard against was a wrong argv that still exited 0 - a `mount` with no absolute path, a probe
//! run as root instead of as the worker user, an `lstat` where a `stat` was needed.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, PoisonError};
use std::time::Duration;

use async_trait::async_trait;
use avl_base::report::{Buffer, Mode, Terminal};
use avl_base::{Config, Environment, GuestOs, Refusal, Reporter, Selection};
use avl_wire::verb::AgentVerb;

use super::{BazelHost, Guest, ParkedDaemonProbe, PeerChannels};
use crate::ctx::Ctx;
use crate::proc::{Captured, Channel, GuestStream, SpawnOptions};
use crate::testing::fixture_backend;

type Answer = dyn Fn(&[String]) -> Result<Captured, Refusal> + Send + Sync;

/// One command a [`FakeChannel`] was handed.
#[derive(Clone, Debug)]
pub(crate) struct Call {
    pub argv: Vec<String>,
    pub options: SpawnOptions,
}

impl Call {
    pub(super) fn line(&self) -> String {
        self.argv.join(" ")
    }
}

/// Records what it was handed and answers what the test told it to.
pub(crate) struct FakeChannel {
    worker: String,
    calls: Mutex<Vec<Call>>,
    answer: Box<Answer>,
}

impl FakeChannel {
    /// A channel on which every command exits 0 and says nothing, which is what most probes need.
    pub(super) fn new(worker: &str) -> Self {
        Self::answering(worker, |_| Ok(Captured::default()))
    }

    /// A channel on which every command exits 0 and prints `stdout`.
    pub(super) fn spoke(worker: &str, stdout: impl Into<String>) -> Self {
        let stdout = stdout.into();
        Self::answering(worker, move |_| Ok(said(&stdout)))
    }

    /// A channel whose answer is computed per command.
    pub(super) fn answering(worker: &str, answer: impl Fn(&[String]) -> Result<Captured, Refusal> + Send + Sync + 'static) -> Self {
        Self {
            worker: worker.to_owned(),
            calls: Mutex::default(),
            answer: Box::new(answer),
        }
    }

    pub(super) fn calls(&self) -> Vec<Call> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    /// Every recorded argv, rendered as one line.
    pub(super) fn lines(&self) -> Vec<String> {
        self.calls().iter().map(Call::line).collect()
    }

    /// The first recorded call whose argv contains `word`.
    pub(super) fn saw(&self, word: &str) -> Option<Call> {
        self.calls()
            .into_iter()
            .find(|call| call.argv.iter().any(|element| element == word))
    }

    pub(super) fn forget(&self) {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clear();
    }
}

#[async_trait]
impl Channel for FakeChannel {
    async fn exec(&self, _ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(Call {
            argv: argv.to_vec(),
            options: options.clone(),
        });
        (self.answer)(argv)
    }

    /// Records the connect as the call `relay <port>`, and answers a port where nothing listens: no suite of this
    /// crate speaks to a guest port.
    async fn connect(&self, _ctx: &Ctx, port: u16) -> Result<GuestStream, Refusal> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(Call {
            argv: vec![AgentVerb::Relay.as_str().to_owned(), port.to_string()],
            // A connect is a stream and takes no spawn options, so its record names a zero timeout.
            options: SpawnOptions::within(Duration::ZERO),
        });
        Ok(GuestStream::refused(format!(
            "nothing listens on 127.0.0.1:{port} inside {}",
            self.worker
        )))
    }

    fn worker(&self) -> &str {
        &self.worker
    }
}

/// An exit 0 that printed `stdout`.
pub(crate) fn said(stdout: &str) -> Captured {
    Captured {
        stdout: stdout.to_owned(),
        ..Captured::default()
    }
}

/// A failure with an exit code and a stderr.
pub(crate) fn failed(exit_code: i32, stderr: &str) -> Captured {
    Captured {
        exit_code,
        stderr: stderr.to_owned(),
        ..Captured::default()
    }
}

/// Whether an argv contains a word.
pub(crate) fn has(argv: &[String], word: &str) -> bool {
    argv.iter().any(|element| element == word)
}

/// Answers the two questions a resolution can ask, and records that it was asked.
///
/// The whole reason [`BazelHost`] is a trait: two `cquery`/`info` round-trips are 5.6 s against an idle server and
/// 24.2 s against a busy one, and a suite that spawned them would be neither hermetic nor fast.
#[derive(Default)]
pub(crate) struct FakeBazel {
    pub stdout: String,
    pub root: PathBuf,
    /// What `bazel info output_base` answers: a different directory from the execution root, and the one an
    /// external file is joined onto.
    pub output_base: String,
    pub state: Mutex<BazelState>,
}

#[derive(Clone, Default)]
pub(crate) struct BazelState {
    pub queries: usize,
    pub root_requests: usize,
    pub last_query: Vec<String>,
}

impl FakeBazel {
    pub(super) fn state(&self) -> BazelState {
        self.state.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }
}

#[async_trait]
impl BazelHost for FakeBazel {
    async fn query(&self, _ctx: &Ctx, command: &str, args: &[String]) -> Result<String, Refusal> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        state.queries += 1;
        state.last_query = std::iter::once(command.to_owned()).chain(args.iter().cloned()).collect();
        Ok(if command == "info" {
            self.output_base.clone()
        } else {
            self.stdout.clone()
        })
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner).root_requests += 1;
        Ok(self.root.clone())
    }
}

/// A Bazel no test may reach: a resolution that asked it would be a regression, so it fails the test loudly.
pub(crate) struct ForbiddenBazel;

#[async_trait]
impl BazelHost for ForbiddenBazel {
    async fn query(&self, _ctx: &Ctx, command: &str, _args: &[String]) -> Result<String, Refusal> {
        panic!("Bazel was asked `{command}` by a path that must not reach it")
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        panic!("Bazel was asked for its execution root by a path that must not reach it")
    }
}

/// Peers answered by name: a worker with no entry is a stopped one.
#[derive(Default)]
pub(crate) struct Peers(pub Vec<(String, Arc<dyn Channel>)>);

#[async_trait]
impl PeerChannels for Peers {
    async fn peer(&self, _ctx: &Ctx, worker: &str) -> Result<Option<Arc<dyn Channel>>, Refusal> {
        Ok(self.0.iter().find(|(name, _)| name == worker).map(|(_, channel)| channel.clone()))
    }
}

/// A resolved config over temporary directories, with the host paths already published, and the context and
/// reporters a session borrows.
///
/// Published here rather than in each test because [`Config::host_repo`] refuses to answer before the paths are
/// resolved, and almost everything here reads it: a test that forgot would be testing that refusal instead.
pub(crate) struct Host {
    pub settings: Config,
    pub repo: PathBuf,
    pub ctx: Ctx,
    /// JSON mode, so a note is silent: the shape an ordinary `--json` invocation has.
    pub quiet: Reporter,
    root: tempfile::TempDir,
}

impl Host {
    pub(super) fn new(guest_os: GuestOs) -> Self {
        let root = tempfile::tempdir().expect("a temporary directory");
        let repo = root.path().join("repo");
        let bazel = root.path().join("bazel");
        let runtime = root.path().join("runtime");
        for directory in [repo.join(".git"), bazel.clone()] {
            std::fs::create_dir_all(directory).expect("a fixture directory");
        }
        let home = root.path().to_string_lossy().into_owned();
        let runtime = runtime.to_string_lossy().into_owned();
        let environment = Environment::from_pairs([
            ("HOME", home.as_str()),
            ("AIR_VM_RUNTIME_ROOT", runtime.as_str()),
            ("AIR_VM_WORKERS", "air-docker-1,air-docker-2"),
        ]);
        let settings = Config::load(
            Selection {
                backend: fixture_backend(guest_os),
                guest_os,
            },
            &environment,
            &root.path().join("scripts"),
        )
        .unwrap_or_else(|refusal| panic!("the fixture environment was refused: {refusal:?}"));
        // The backend names the worker directory, `docker-air-docker-1` for a Docker worker.
        for worker in ["air-docker-1", "air-docker-2", "air-macos-1"] {
            std::fs::create_dir_all(settings.worker_dir(worker)).expect("a fixture directory");
        }
        settings.set_host_paths(&repo, &bazel).expect("fresh host paths");
        Self {
            settings,
            repo,
            ctx: Ctx::background(),
            quiet: Reporter::in_memory("vm").0,
            root,
        }
    }

    pub(super) fn dir(&self) -> &Path {
        self.root.path()
    }

    pub(super) fn bazel_user_root(&self) -> PathBuf {
        self.settings.host_bazel_user_root().expect("resolved by the fixture").to_owned()
    }

    /// A session into `channel` noting to the quiet reporter.
    pub(super) fn guest<'a>(&'a self, channel: &'a dyn Channel) -> Guest<'a> {
        self.guest_reporting(channel, &self.quiet)
    }

    pub(super) fn guest_reporting<'a>(&'a self, channel: &'a dyn Channel, reporter: &'a Reporter) -> Guest<'a> {
        Guest {
            ctx: &self.ctx,
            settings: &self.settings,
            channel,
            reporter,
        }
    }

    /// A file the agent install reads, standing in for `AIR_VM_GUEST_AGENT_SOURCE`.
    pub(super) fn agent_source(&self, content: &str) -> PathBuf {
        let directory = tempfile::tempdir_in(self.dir()).expect("a source directory").keep();
        let path = directory.join("vm-guest-agent");
        std::fs::write(&path, content).expect("the agent source");
        path
    }
}

/// A reporter in human mode over a buffer, so a test can read what was said. Human mode and not `--stream`,
/// because prose is the rendering a person reads.
pub(crate) fn prose() -> (Reporter, Buffer) {
    let (reporter, _, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Human(Terminal::default()));
    (reporter, stderr)
}

/// [`ParkedDaemonProbe`] with both of its answers seeded: what makes a holder idle is the daemon's own account of
/// itself, and these suites have no daemon. The default proves nothing, so every holder is executing.
#[derive(Default)]
pub(crate) struct FakeProbe {
    pub(crate) parked: Option<String>,
    pub(crate) recorded: Option<String>,
    pub(crate) refusal: Option<Refusal>,
}

#[async_trait]
impl ParkedDaemonProbe for FakeProbe {
    async fn parked_daemon_run(&self, _ctx: &Ctx, _worker: &str) -> Result<Option<String>, Refusal> {
        match &self.refusal {
            Some(refusal) => Err(refusal.clone()),
            None => Ok(self.parked.clone()),
        }
    }

    fn recorded_daemon_run(&self, _worker: &str) -> Option<String> {
        self.recorded.clone()
    }
}
