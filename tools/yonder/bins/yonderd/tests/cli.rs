//! The process-level tests of `yonderd`: a real `serve` on a loopback port with a temporary HOME, a git repository
//! with a commit, a fake `bd` on PATH that keeps its leases in a directory, and commands of `/bin/sh`. The client is
//! the `Channel` of yonder-link.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_yonderd`. Cargo sets it when it compiles the test, and
//! the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::collections::BTreeSet;
use std::ffi::OsString;
use std::fs;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::rc::Rc;
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use pretty_assertions::assert_eq;
use yonder_link::proto::{FileKind, Phase, Reply, Request, RunSpec, SCHEMA_VERSION, Stream};
use yonder_link::{AllowList, Channel, ClientTls, Fingerprint, Identity, LinkError};

/// The deadline of each connect and read of the test client.
const DEADLINE: Duration = Duration::from_secs(30);

const HOLDER: &str = "yonder-0123456789ab";

/// The phases of a run that ran its command, in their order.
const ALL_PHASES: [&str; 7] = ["lock", "lease", "pack_wait", "checkout", "prepare", "command", "stage"];

/// The start of the line that the agent writes right before the command.
const START_LINE: &str = "yonder: running in ";

fn agent_binary() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_yonderd")
        .or_else(|| option_env!("CARGO_BIN_EXE_yonderd").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_yonderd");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).expect("an absolute path")
}

/// The fake `bd`. It records each call as `<cwd>|<args>` in `$FAKE_BD_DIR/calls`, keeps the live lease ids in
/// `$FAKE_BD_DIR/alive`, and makes each slot a worktree of the main repository. `$FAKE_BD_DIR/fail-get` makes
/// `lease get` fail, `slow-get` makes it take a second, and `fail-return` makes `lease return` fail.
const FAKE_BD: &str = r#"#!/bin/sh
state=$FAKE_BD_DIR
printf '%s|%s\n' "$PWD" "$*" >>"$state/calls"
if [ "$1" = lease ] && [ "$2" = get ]; then
  holder=$4 repo=$6
  if [ -e "$state/fail-get" ]; then echo "no pool for $repo" >&2; exit 1; fi
  if [ -e "$state/slow-get" ]; then sleep 1; fi
  slot=$state/slots/$holder
  if [ ! -d "$slot" ]; then
    mkdir -p "$state/slots"
    git -C "$repo" worktree add -q --detach "$slot" >&2 || exit 1
  fi
  echo "lease-$holder" >>"$state/alive"
  printf '{"schema":2,"repository":"%s","path":"%s","lease_id":"lease-%s","holder":"%s","slot":"s","reused":false}\n' "$repo" "$slot" "$holder" "$holder"
  exit 0
fi
if [ "$1" = lease ] && [ "$2" = status ]; then
  printf '{"schema":2,"slots":['
  sep=
  if [ -e "$state/alive" ]; then
    while read -r id; do
      printf '%s{"slot":"s","path":"/p","state":"ready","lease":{"lease_id":"%s"}}' "$sep" "$id"
      sep=,
    done <"$state/alive"
  fi
  printf ']}\n'
  exit 0
fi
if [ "$1" = -C ] && [ "$3" = lease ] && [ "$4" = return ]; then
  id=$6
  if [ -e "$state/fail-return" ]; then echo "the slot has local changes" >&2; exit 1; fi
  if [ -e "$state/alive" ]; then
    while read -r line; do [ "$line" = "$id" ] || echo "$line"; done <"$state/alive" >"$state/alive.tmp"
    mv "$state/alive.tmp" "$state/alive"
  fi
  exit 0
fi
echo "fake bd: unknown call: $*" >&2
exit 2
"#;

/// A git in front of the real one, `$REAL_GIT`. With `$FAKE_BD_DIR/slow-checkout`, a checkout writes
/// `checkout-started` and waits up to 60 s before it goes on, so a test can cancel a run during its checkout. With
/// `$FAKE_BD_DIR/slow-cat-file`, a `cat-file` takes a second.
const SLOW_GIT: &str = r#"#!/bin/sh
if [ "$3" = checkout ] && [ -e "$FAKE_BD_DIR/slow-checkout" ]; then
  : >"$FAKE_BD_DIR/checkout-started"
  i=0; while [ $i -lt 600 ]; do sleep 0.1; i=$((i + 1)); done
fi
if [ "$3" = cat-file ] && [ -e "$FAKE_BD_DIR/slow-cat-file" ]; then sleep 1; fi
exec "$REAL_GIT" "$@"
"#;

/// The fake `launchctl` of `setup`. `print` answers whether `bootstrap` loaded the service.
const FAKE_LAUNCHCTL: &str = r#"#!/bin/sh
echo "$*" >>"$FAKE_LAUNCHCTL_DIR/calls"
case $1 in
  print) [ -e "$FAKE_LAUNCHCTL_DIR/loaded" ] ;;
  bootstrap) : >"$FAKE_LAUNCHCTL_DIR/loaded" ;;
  bootout) rm -f "$FAKE_LAUNCHCTL_DIR/loaded" ;;
  kickstart) exit 0 ;;
  *) exit 2 ;;
esac
"#;

fn write_script(path: &Path, text: &str) {
    fs::write(path, text).expect("a script");
    fs::set_permissions(path, fs::Permissions::from_mode(0o755)).expect("executable");
}

#[path = "../src/testing.rs"]
mod testing;

use testing::git;

/// A temporary home with a main repository, the fake bd, and the identity of a client in the allow list. A clone shares
/// the directory, so a second `serve` of the same home can start.
#[derive(Clone)]
struct World {
    dir: Rc<tempfile::TempDir>,
    client: Identity,
    commit: String,
}

impl World {
    fn new() -> Self {
        let dir = Rc::new(tempfile::tempdir().expect("a temporary directory"));
        let world = Self {
            dir,
            client: Identity::generate().expect("the client identity"),
            commit: String::new(),
        };
        let repo = world.repo();
        fs::create_dir_all(&repo).expect("the repository directory");
        git(&repo, &["init", "-q"]);
        fs::write(repo.join("a.txt"), "a").expect("a file");
        git(&repo, &["add", "a.txt"]);
        git(&repo, &["commit", "-q", "-m", "first"]);
        let commit = git(&repo, &["rev-parse", "HEAD"]);
        fs::create_dir_all(world.bin()).expect("the bin directory");
        fs::create_dir_all(world.bd_dir()).expect("the bd directory");
        write_script(&world.bin().join("bd"), FAKE_BD);
        let mut allow = AllowList::default();
        allow.add(world.client.fingerprint(), "test");
        fs::create_dir_all(world.home().join(".config/yonderd")).expect("the config directory");
        fs::write(world.home().join(".config/yonderd/clients"), allow.text()).expect("the allow list");
        Self { commit, ..world }
    }

    fn home(&self) -> PathBuf {
        self.dir.path().join("home")
    }

    fn repo(&self) -> PathBuf {
        self.home().join("repo")
    }

    fn bin(&self) -> PathBuf {
        self.dir.path().join("bin")
    }

    fn bd_dir(&self) -> PathBuf {
        self.dir.path().join("bd")
    }

    fn slot(&self, holder: &str) -> PathBuf {
        self.bd_dir().join("slots").join(holder)
    }

    fn run_dir(&self, run: &str) -> PathBuf {
        self.home().join(".cache/yonderd/runs").join(run)
    }

    fn lease_file(&self, holder: &str) -> PathBuf {
        self.home().join(".cache/yonderd/leases").join(format!("{holder}.lease"))
    }

    fn bd_calls(&self) -> String {
        fs::read_to_string(self.bd_dir().join("calls")).unwrap_or_default()
    }

    /// The agent binary with the environment of the world only.
    fn command(&self) -> Command {
        self.command_of(&agent_binary())
    }

    /// The agent binary `program` with the environment of the world only.
    fn command_of(&self, program: &Path) -> Command {
        let mut command = Command::new(program);
        command
            .env_clear()
            .env("HOME", self.home())
            .env("PATH", format!("{}:/usr/bin:/bin", self.bin().display()))
            .env("FAKE_BD_DIR", self.bd_dir())
            .env("GIT_CONFIG_NOSYSTEM", "1")
            // The login shell of the developer stays out of the tests; a test that needs one names its own.
            .env("YONDERD_TEST_SHELL", "none")
            // The power assertions of the developer's Mac stay out of the tests; a test that checks them names a fake.
            .env("YONDERD_TEST_CAFFEINATE", "none");
        command
    }

    fn spec(&self, run: &str, holder: &str, script: &str) -> RunSpec {
        RunSpec {
            run: run.to_owned(),
            repo: "~/repo".to_owned(),
            holder: holder.to_owned(),
            commit: self.commit.clone(),
            reference: format!("refs/yonder/{run}"),
            command: vec!["/bin/sh".to_owned(), "-c".to_owned(), script.to_owned()],
            fetch: vec!["out/bt-runs".to_owned()],
            idle_hours: 8,
            ..RunSpec::default()
        }
    }

    /// Starts `serve` on a loopback port.
    fn serve(self) -> Agent {
        self.serve_with(&[])
    }

    /// Starts `serve` on a loopback port with more environment, such as a test duration of `tuning.rs`.
    fn serve_with(self, env: &[(&str, &str)]) -> Agent {
        self.serve_program(&agent_binary(), 0, &[], env)
    }

    /// Starts `serve` of the binary `program` on the loopback `port` (0: any) with more arguments and more environment.
    fn serve_program(self, program: &Path, port: u16, args: &[&str], env: &[(&str, &str)]) -> Agent {
        let log = fs::File::create(self.dir.path().join("serve.log")).expect("the serve log");
        let mut command = self.command_of(program);
        for (name, value) in env {
            command.env(name, value);
        }
        let mut child = command
            .args(["serve", "--bind", "127.0.0.1", "--port", &port.to_string()])
            .args(args)
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(log)
            .spawn()
            .expect("serve started");
        let mut line = String::new();
        BufReader::new(child.stdout.take().expect("the stdout"))
            .read_line(&mut line)
            .expect("the first line");
        let listening: serde_json::Value = serde_json::from_str(&line).unwrap_or_else(|error| panic!("{error}: {line:?}"));
        let address: SocketAddr = listening["listening"].as_str().expect("the address").parse().expect("an address");
        let fingerprint: Fingerprint = listening["fingerprint"]
            .as_str()
            .expect("the fingerprint")
            .parse()
            .expect("a fingerprint");
        Agent {
            child,
            address,
            fingerprint,
            world: self,
        }
    }
}

/// A running `serve` of a world.
struct Agent {
    child: Child,
    address: SocketAddr,
    fingerprint: Fingerprint,
    world: World,
}

impl Drop for Agent {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

/// The replies of one follow.
#[derive(Debug)]
struct Followed {
    lines: Vec<(Stream, String, u64)>,
    /// The phases of `Reply::Phases`, which comes right before the end of a run.
    phases: Option<Vec<Phase>>,
    end: Reply,
}

impl Followed {
    fn phase_names(&self) -> Vec<&str> {
        self.phases.iter().flatten().map(|phase| phase.name.as_str()).collect()
    }

    fn phase_ms(&self, name: &str) -> u64 {
        self.phases
            .iter()
            .flatten()
            .find(|phase| phase.name == name)
            .unwrap_or_else(|| panic!("no phase {name}: {self:?}"))
            .ms
    }

    /// The lines of the run, less the start line of its command, which [`Followed::start_line`] answers.
    fn texts(&self) -> Vec<&str> {
        self.lines
            .iter()
            .map(|(_, text, _)| text.as_str())
            .filter(|text| !text.starts_with(START_LINE))
            .collect()
    }

    /// The lines of one stream, less the start line of the command.
    fn of(&self, stream: Stream) -> Vec<&str> {
        self.lines
            .iter()
            .filter(|(of, _, _)| *of == stream)
            .map(|(_, text, _)| text.as_str())
            .filter(|text| !text.starts_with(START_LINE))
            .collect()
    }

    /// The line that the agent writes right before the command.
    fn start_line(&self) -> &str {
        self.lines
            .iter()
            .map(|(_, text, _)| text.as_str())
            .find(|text| text.starts_with(START_LINE))
            .unwrap_or_else(|| panic!("no start line: {self:?}"))
    }
}

impl Agent {
    fn connect_as(&self, identity: &Identity) -> Result<Channel, LinkError> {
        Channel::connect(self.address, &ClientTls::new(self.fingerprint, identity)?, DEADLINE)
    }

    /// A connection after its hello, with `request` sent.
    fn ask(&self, request: &Request) -> Channel {
        let mut channel = self.connect_as(&self.world.client).expect("connected");
        channel.send(&Request::hello("test")).expect("the hello");
        let hello: Reply = channel.recv().expect("the hello reply");
        assert!(matches!(hello, Reply::Hello { .. }), "{hello:?}");
        channel.send(request).expect("the request");
        channel
    }

    /// The last reply of `request`. Any request that waits can get heartbeats first, so they are skipped.
    fn reply(&self, request: &Request) -> Reply {
        self.reply_after_heartbeats(request).1
    }

    /// The last reply of `request`, and the count of the heartbeats before it.
    fn reply_after_heartbeats(&self, request: &Request) -> (u32, Reply) {
        let mut channel = self.ask(request);
        let mut heartbeats = 0;
        loop {
            match channel.recv().expect("the reply") {
                Reply::Heartbeat { .. } => heartbeats += 1,
                reply => return (heartbeats, reply),
            }
        }
    }

    fn start(&self, spec: RunSpec) {
        let run = spec.run.clone();
        assert_eq!(self.reply(&Request::Start(spec)), Reply::Started { run });
    }

    /// Follows a run from `offset` to its end. The phases come right before the end, with no reply between them.
    fn follow(&self, run: &str, offset: u64) -> Followed {
        let mut channel = self.ask(&Request::Follow {
            run: run.to_owned(),
            offset,
        });
        let mut lines = Vec::new();
        let mut phases = None;
        loop {
            let reply = channel
                .recv::<Reply>()
                .unwrap_or_else(|error| panic!("{error}; {}", self.dump(run)));
            assert!(
                phases.is_none() || !matches!(reply, Reply::Output { .. } | Reply::Heartbeat { .. } | Reply::Phases { .. }),
                "{reply:?} came after the phases {phases:?}"
            );
            match reply {
                Reply::Output { stream, text, end } => lines.push((stream, text, end)),
                Reply::Heartbeat { .. } => {}
                Reply::Phases { phases: got } => phases = Some(got),
                end => return Followed { lines, phases, end },
            }
        }
    }

    /// Follows a run from `offset` until a line is `text`, then drops the connection.
    fn follow_until(&self, run: &str, offset: u64, text: &str) -> Vec<(Stream, String, u64)> {
        let mut channel = self.ask(&Request::Follow {
            run: run.to_owned(),
            offset,
        });
        let mut lines = Vec::new();
        loop {
            match channel
                .recv::<Reply>()
                .unwrap_or_else(|error| panic!("{error}; {}", self.dump(run)))
            {
                Reply::Output { stream, text: line, end } => {
                    let done = line == text;
                    lines.push((stream, line, end));
                    if done {
                        return lines;
                    }
                }
                Reply::Heartbeat { .. } => {}
                end => panic!("the run ended before {text:?}: {end:?}; {lines:?}; {}", self.dump(run)),
            }
        }
    }

    /// The files of a run and the log of `serve`, for a failure message.
    fn dump(&self, run: &str) -> String {
        let read = |path: PathBuf| fs::read_to_string(&path).unwrap_or_else(|error| format!("<{}: {error}>", path.display()));
        let dir = self.world.run_dir(run);
        format!(
            "events: {}\nsupervisor: {}\nserve: {}\nbd: {}",
            read(dir.join("events.log")),
            read(dir.join("supervisor.log")),
            read(self.world.dir.path().join("serve.log")),
            self.world.bd_calls()
        )
    }
}

/// A shell loop that waits until the file `gate` exists, up to 60 s, so a failed test leaves no command behind.
fn wait_for(gate: &Path) -> String {
    format!(
        "i=0; while [ ! -e '{}' ] && [ $i -lt 1200 ]; do sleep 0.05; i=$((i + 1)); done",
        gate.display()
    )
}

/// Waits up to 30 s until `done` answers `true`.
fn eventually(what: &str, done: impl Fn() -> bool) {
    let started = Instant::now();
    while !done() {
        assert!(started.elapsed() < Duration::from_secs(30), "{what} did not happen in 30 s");
        thread::sleep(Duration::from_millis(50));
    }
}

#[test]
fn hello_answers_the_schema_and_another_schema_or_request_is_refused() {
    let agent = World::new().serve();
    let mut channel = agent.connect_as(&agent.world.client).expect("connected");
    channel.send(&Request::hello("test")).expect("sent");
    let hello = channel.recv::<Reply>().expect("the reply");
    let sha256 = yonder_link::digest::sha256_file(&agent_binary()).expect("the SHA-256 of the agent");
    assert_eq!(hello, Reply::hello(env!("CARGO_PKG_VERSION"), Some(sha256)));
    assert!(
        matches!(&hello, Reply::Hello { schema_version, os, .. } if *schema_version == SCHEMA_VERSION && os == std::env::consts::OS),
        "{hello:?}"
    );
    assert!(
        matches!(&hello, Reply::Hello { features, .. } if *features == yonder_link::proto::features()),
        "{hello:?}"
    );

    let mut channel = agent.connect_as(&agent.world.client).expect("connected");
    channel
        .send(&Request::Hello {
            schema_version: SCHEMA_VERSION + 1,
            client_version: "future".to_owned(),
        })
        .expect("sent");
    let refused = channel.recv::<Reply>().expect("the reply");
    assert!(matches!(&refused, Reply::Refused { code, .. } if code == "schema"), "{refused:?}");

    let mut channel = agent.connect_as(&agent.world.client).expect("connected");
    channel.send(&Request::Runs).expect("sent");
    let refused = channel.recv::<Reply>().expect("the reply");
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
        "{refused:?}"
    );
}

#[test]
fn a_stranger_certificate_is_refused() {
    let agent = World::new().serve();
    let stranger = Identity::generate().expect("a stranger");
    let outcome = agent
        .connect_as(&stranger)
        .and_then(|mut channel| channel.exchange::<_, Reply>(&Request::hello("test")));
    // The agent refuses the certificate: the client gets no reply, and neither a deadline nor a closed port ends it.
    let Err(error) = outcome else {
        panic!("the agent answered a stranger: {outcome:?}");
    };
    assert!(!matches!(error, LinkError::TimedOut | LinkError::Unreachable(_)), "{error:?}");
    eventually("the refusal in the log of serve", || {
        fs::read_to_string(agent.world.dir.path().join("serve.log")).is_ok_and(|log| log.contains("refused a connection from"))
    });
}

#[test]
fn have_answers_the_commits_that_the_repository_has() {
    let agent = World::new().serve();
    let missing = "1".repeat(40);
    let reply = agent.reply(&Request::Have {
        repo: "~/repo".to_owned(),
        commits: vec![missing, agent.world.commit.clone()],
    });
    assert_eq!(
        reply,
        Reply::Have {
            commits: vec![agent.world.commit.clone()]
        }
    );
    let refused = agent.reply(&Request::Have {
        repo: "~/missing".to_owned(),
        commits: vec![],
    });
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "repo_missing"),
        "{refused:?}"
    );
    let refused = agent.reply(&Request::Have {
        repo: "~/repo".to_owned(),
        commits: vec!["HEAD".to_owned()],
    });
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
        "{refused:?}"
    );
}

/// A commit that adds `b.txt` in a clone of the main repository, which the main repository does not have. Answers the
/// commit and its thin pack against the commit of the world.
fn local_commit(world: &World) -> (String, Vec<u8>) {
    let local = world.dir.path().join("local");
    git(
        world.dir.path(),
        &["clone", "-q", &world.repo().to_string_lossy(), &local.to_string_lossy()],
    );
    fs::write(local.join("b.txt"), "b").expect("a file");
    git(&local, &["add", "b.txt"]);
    git(&local, &["commit", "-q", "-m", "second"]);
    let head = git(&local, &["rev-parse", "HEAD"]);
    let pack = Command::new("git")
        .arg("-C")
        .arg(&local)
        .args(["pack-objects", "--revs", "--thin", "--stdout", "-q"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .spawn()
        .and_then(|mut child| {
            child
                .stdin
                .take()
                .expect("the stdin")
                .write_all(format!("HEAD\n^{}\n", world.commit).as_bytes())?;
            child.wait_with_output()
        })
        .expect("a pack");
    assert!(pack.status.success());
    (head, pack.stdout)
}

impl Agent {
    /// Sends `pack` to `reference` and answers the reply.
    fn pack(&self, commit: &str, reference: &str, pack: &[u8]) -> Reply {
        let mut channel = self.ask(&Request::Pack {
            repo: "~/repo".to_owned(),
            commit: commit.to_owned(),
            reference: reference.to_owned(),
        });
        channel.send_stream(&mut &pack[..], &mut |_| {}).expect("the pack sent");
        channel.recv::<Reply>().expect("the reply")
    }
}

#[test]
fn a_thin_pack_lands_and_its_ref_points_at_the_commit() {
    let agent = World::new().serve();
    let world = &agent.world;
    let (head, pack) = local_commit(world);
    assert_eq!(agent.pack(&head, "refs/yonder/1-1", &pack), Reply::Packed);
    assert_eq!(git(&world.repo(), &["rev-parse", "refs/yonder/1-1"]), head);
    assert_eq!(git(&world.repo(), &["cat-file", "-p", &format!("{head}:b.txt")]), "b");
    // The cleanup of `serve` finds the refs of the packs through the repository list.
    assert_eq!(
        fs::read_to_string(world.home().join(".cache/yonderd/repos")).expect("the repository list"),
        format!("{}\n", world.repo().display())
    );

    // A refused pack is read to its end, and the refusal arrives.
    let refused = agent.pack(&head, "refs/heads/master", &pack);
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
        "{refused:?}"
    );

    // A stream that is not a pack fails in index-pack.
    let refused = agent.pack(&"2".repeat(40), "refs/yonder/1-2", b"not a pack");
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "pack_failed"),
        "{refused:?}"
    );
}

#[test]
fn a_run_streams_its_output_to_the_exit_code_stages_its_files_and_is_forgotten() {
    let agent = World::new().serve();
    let world = &agent.world;
    git(&world.repo(), &["update-ref", "refs/yonder/1-1", &world.commit]);
    let script = "echo out-1; echo err-1 >&2; mkdir -p out/bt-runs/run-a; echo report >out/bt-runs/run-a/report.txt; \
                  printf x >out/bt-runs/tool.sh; chmod +x out/bt-runs/tool.sh; cat a.txt; echo; exit 3";
    agent.start(world.spec("1-1", HOLDER, script));
    let followed = agent.follow("1-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 3 }, "{}", agent.dump("1-1"));
    assert_eq!(followed.phase_names(), ALL_PHASES);
    let slot = world.slot(HOLDER);
    // The two pipes of the command have no order between them, so each stream is checked by itself.
    assert_eq!(followed.of(Stream::Stdout), vec!["out-1", "a"], "{}", agent.dump("1-1"));
    assert_eq!(
        followed.of(Stream::Stderr),
        vec![
            format!("yonder: taking a new lease of {HOLDER} (bd lease get)").as_str(),
            format!("yonder: lease {} (new)", slot.display()).as_str(),
            "err-1"
        ],
        "{}",
        agent.dump("1-1")
    );
    let log_size = fs::metadata(world.run_dir("1-1").join("events.log")).expect("the log").len();
    assert_eq!(followed.lines.last().map(|(_, _, end)| *end), Some(log_size));

    // The ref is gone, the lease file names the slot, and bd ran in `/`.
    let refs = git(&world.repo(), &["for-each-ref", "refs/yonder/"]);
    assert_eq!(refs, "");
    let lease = fs::read_to_string(world.lease_file(HOLDER)).expect("the lease file");
    assert!(
        lease.starts_with(&format!("lease_id=lease-{HOLDER}\nslot={}\nused=", slot.display())),
        "{lease}"
    );
    assert!(world.bd_calls().lines().all(|call| call.starts_with("/|")), "{}", world.bd_calls());
    let start = followed.start_line();
    assert!(start.starts_with(&format!("{START_LINE}{} (slot ", slot.display())), "{start}");
    assert!(
        start.contains(", nice 0, ") && start.contains(" open files, the environment of the agent"),
        "{start}"
    );

    // The staged files come with their paths, sizes and modes; the slot keeps none of them.
    let mut channel = agent.ask(&Request::Fetch { run: "1-1".to_owned() });
    let mut files = Vec::new();
    loop {
        match channel.recv::<Reply>().expect("a reply") {
            Reply::File {
                path,
                size,
                executable,
                kind,
            } => {
                assert_eq!(kind, FileKind::Staged);
                let mut bytes = Vec::new();
                channel.recv_stream(&mut bytes).expect("the bytes");
                assert_eq!(bytes.len() as u64, size);
                files.push((path, String::from_utf8(bytes).expect("text"), executable));
            }
            Reply::Fetched { files: count, bytes } => {
                assert_eq!((count, bytes), (2, 8));
                break;
            }
            other => panic!("{other:?}"),
        }
    }
    assert_eq!(
        files,
        vec![
            ("out/bt-runs/run-a/report.txt".to_owned(), "report\n".to_owned(), false),
            ("out/bt-runs/tool.sh".to_owned(), "x".to_owned(), true),
        ]
    );
    assert!(!slot.join("out/bt-runs/run-a").exists());

    // A second run of the holder takes the warm lease.
    git(&world.repo(), &["update-ref", "refs/yonder/1-2", &world.commit]);
    agent.start(world.spec("1-2", HOLDER, "exit 0"));
    let second = agent.follow("1-2", 0);
    assert_eq!(second.end, Reply::Exit { code: 0 });
    assert_eq!(second.texts(), vec![format!("yonder: lease {} (warm)", slot.display()).as_str()]);

    let Reply::Runs { runs } = agent.reply(&Request::Runs) else {
        panic!("not runs");
    };
    let listed: Vec<(&str, Option<i32>)> = runs.iter().map(|run| (run.run.as_str(), run.exit)).collect();
    assert_eq!(listed, vec![("1-1", Some(3)), ("1-2", Some(0))]);
    assert_eq!(runs[0].holder, HOLDER);
    assert_eq!(runs[0].commit, world.commit);
    assert_eq!(Some(&runs[0].phases), followed.phases.as_ref());

    let done = agent.reply(&Request::Forget { run: "1-1".to_owned() });
    assert!(matches!(done, Reply::Done { .. }), "{done:?}");
    assert!(!world.run_dir("1-1").exists());
    let refused = agent.reply(&Request::Follow {
        run: "1-1".to_owned(),
        offset: 0,
    });
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "unknown_run"),
        "{refused:?}"
    );
    let refused = agent.reply(&Request::Fetch { run: "../x".to_owned() });
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_run_id"),
        "{refused:?}"
    );
}

#[test]
fn a_start_refuses_a_bad_spec() {
    let agent = World::new().serve();
    let world = &agent.world;
    let refused = |spec: RunSpec| match agent.reply(&Request::Start(spec)) {
        Reply::Refused { code, .. } => code,
        other => panic!("{other:?}"),
    };
    assert_eq!(refused(world.spec("a/b", HOLDER, "true")), "bad_run_id");
    assert_eq!(
        refused(RunSpec {
            fetch: vec!["../out".to_owned()],
            ..world.spec("2-1", HOLDER, "true")
        }),
        "bad_request"
    );
    assert_eq!(
        refused(RunSpec {
            reference: "refs/heads/master".to_owned(),
            ..world.spec("2-1", HOLDER, "true")
        }),
        "bad_request"
    );
    // A base ref outlives each run, so no run takes one.
    assert_eq!(
        refused(RunSpec {
            reference: "refs/yonder/base/master".to_owned(),
            ..world.spec("2-1", HOLDER, "true")
        }),
        "bad_request"
    );
    // The ref of another run: the start refuses it and leaves that ref alone.
    git(&world.repo(), &["update-ref", "refs/yonder/2-9", &world.commit]);
    assert_eq!(
        refused(RunSpec {
            reference: "refs/yonder/2-9".to_owned(),
            ..world.spec("2-1", HOLDER, "true")
        }),
        "bad_request"
    );
    assert_eq!(git(&world.repo(), &["rev-parse", "refs/yonder/2-9"]), world.commit);
    assert_eq!(
        refused(RunSpec {
            repo: "~/missing".to_owned(),
            ..world.spec("2-1", HOLDER, "true")
        }),
        "repo_missing"
    );
    agent.start(world.spec("2-1", HOLDER, "true"));
    assert_eq!(refused(world.spec("2-1", HOLDER, "true")), "run_exists");
    assert_eq!(agent.follow("2-1", 0).end, Reply::Exit { code: 0 });
}

#[test]
fn a_follow_from_an_offset_in_the_middle_of_a_run_goes_on_from_there() {
    let agent = World::new().serve();
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    let script = format!("echo first; echo second; {}; echo third", wait_for(&gate));
    agent.start(world.spec("3-1", HOLDER, &script));
    let seen = agent.follow_until("3-1", 0, "second");
    let after_first = seen.iter().find(|(_, text, _)| text == "first").expect("the first line").2;
    // The command goes on, so the steps before it have ended.
    let Reply::Runs { runs } = agent.reply(&Request::Runs) else {
        panic!("not runs");
    };
    let names: Vec<&str> = runs[0].phases.iter().map(|phase| phase.name.as_str()).collect();
    assert_eq!(names, ALL_PHASES[..5]);
    assert_eq!(runs[0].exit, None);

    fs::write(&gate, "").expect("the gate");
    let followed = agent.follow("3-1", after_first);
    assert_eq!(followed.texts(), vec!["second", "third"]);
    assert_eq!(followed.end, Reply::Exit { code: 0 });
    assert_eq!(followed.phase_names(), ALL_PHASES);

    // A follow that comes after the end gets the phases too.
    let again = agent.follow("3-1", 0);
    assert_eq!(again.of(Stream::Stdout), vec!["first", "second", "third"]);
    assert_eq!((again.phases, again.end), (followed.phases, Reply::Exit { code: 0 }));

    let refused = agent.reply(&Request::Follow {
        run: "3-1".to_owned(),
        offset: after_first - 1,
    });
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
        "{refused:?}"
    );
}

#[test]
fn a_cancel_interrupts_the_command_group() {
    let agent = World::new().serve();
    let world = &agent.world;
    let script = "trap 'echo interrupted; exit 7' INT; echo ready; i=0; while [ $i -lt 600 ]; do sleep 0.1; i=$((i + 1)); done";
    agent.start(world.spec("4-1", HOLDER, script));
    agent.follow_until("4-1", 0, "ready");
    let done = agent.reply(&Request::Cancel { run: "4-1".to_owned() });
    assert_eq!(
        done,
        Reply::Done {
            message: "the supervisor of the run 4-1 has the cancel request".to_owned()
        },
        "{}",
        agent.dump("4-1")
    );
    let followed = agent.follow("4-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 7 }, "{}", agent.dump("4-1"));
    assert!(followed.texts().contains(&"interrupted"), "{followed:?}");
    let again = agent.reply(&Request::Cancel { run: "4-1".to_owned() });
    assert_eq!(
        again,
        Reply::Done {
            message: "the run 4-1 has ended already".to_owned()
        }
    );
}

#[test]
fn a_second_run_of_the_holder_waits_for_the_first() {
    let agent = World::new().serve();
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    let script = format!("echo holding; {}", wait_for(&gate));
    agent.start(world.spec("5-1", HOLDER, &script));
    agent.follow_until("5-1", 0, "holding");
    agent.start(world.spec("5-2", HOLDER, "echo second"));
    let waiting = format!("yonder: waiting for the other run of {HOLDER} on this host");
    eventually("the wait of the second run", || {
        fs::read_to_string(world.run_dir("5-2").join("events.log")).is_ok_and(|log| log.contains(&waiting))
    });
    assert_eq!(fs::read_to_string(world.run_dir("5-2").join("exit.json")).ok(), None);

    fs::write(&gate, "").expect("the gate");
    assert_eq!(agent.follow("5-1", 0).end, Reply::Exit { code: 0 });
    let second = agent.follow("5-2", 0);
    assert_eq!(second.end, Reply::Exit { code: 0 });
    assert_eq!(
        second.texts(),
        vec![
            waiting.as_str(),
            format!("yonder: lease {} (warm)", world.slot(HOLDER).display()).as_str(),
            "second"
        ]
    );
}

#[test]
fn a_run_returns_the_idle_lease_of_another_holder() {
    let agent = World::new().serve();
    let world = &agent.world;
    // The fake bd gives the idle holder its slot and its live lease.
    let got = Command::new(world.bin().join("bd"))
        .args(["lease", "get", "--holder", "idle", "-C"])
        .arg(world.repo())
        .env("FAKE_BD_DIR", world.bd_dir())
        .output()
        .expect("the fake bd");
    assert!(got.status.success(), "{}", String::from_utf8_lossy(&got.stderr));
    let idle_slot = world.slot("idle");
    fs::create_dir_all(world.lease_file("idle").parent().expect("the parent")).expect("the leases directory");
    fs::write(
        world.lease_file("idle"),
        format!("lease_id=lease-idle\nslot={}\nused=1\n", idle_slot.display()),
    )
    .expect("the idle lease file");
    // A holder that a run used a moment ago stays.
    fs::write(
        world.lease_file("recent"),
        format!("lease_id=lease-recent\nslot=/nowhere\nused={}\n", now_seconds()),
    )
    .expect("the recent lease file");

    agent.start(RunSpec {
        idle_hours: 1,
        ..world.spec("6-1", HOLDER, "true")
    });
    let followed = agent.follow("6-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("6-1"));
    // The sweep comes after the end of the run, so the run waits for no other lease.
    assert_eq!(followed.phase_names(), ALL_PHASES);
    eventually("the return of the idle lease", || !world.lease_file("idle").exists());
    let returned = format!("yonderd supervise: returned the idle lease lease-idle of {}", idle_slot.display());
    eventually("the line of the sweep", || {
        fs::read_to_string(world.run_dir("6-1").join("supervisor.log")).is_ok_and(|log| log.contains(&returned))
    });
    assert!(world.lease_file("recent").exists());
    assert!(
        world
            .bd_calls()
            .contains(&format!("/|-C {} lease return --lease-id lease-idle\n", idle_slot.display())),
        "{}",
        world.bd_calls()
    );
    let alive = fs::read_to_string(world.bd_dir().join("alive")).expect("the live leases");
    assert!(!alive.contains("lease-idle"), "{alive}");
}

#[test]
fn a_failed_lease_ends_the_run_with_a_refusal_and_deletes_the_ref() {
    let agent = World::new().serve();
    let world = &agent.world;
    fs::write(world.bd_dir().join("fail-get"), "").expect("the flag");
    git(&world.repo(), &["update-ref", "refs/yonder/7-1", &world.commit]);
    agent.start(world.spec("7-1", HOLDER, "true"));
    let followed = agent.follow("7-1", 0);
    assert!(
        matches!(&followed.end, Reply::Refused { code, message } if code == "lease_failed" && message.contains("no pool")),
        "{followed:?}"
    );
    assert_eq!(followed.phase_names(), ["lock", "lease"]);
    assert_eq!(git(&world.repo(), &["for-each-ref", "refs/yonder/"]), "");
    assert!(!world.lease_file(HOLDER).exists());
    let Reply::Runs { runs } = agent.reply(&Request::Runs) else {
        panic!("not runs");
    };
    assert_eq!(runs[0].exit, Some(255));
}

#[test]
fn a_lease_is_taken_reused_and_released() {
    let agent = World::new().serve();
    let world = &agent.world;
    let lease = |fresh: bool| Reply::Leased {
        slot: world.slot(HOLDER).display().to_string(),
        lease_id: format!("lease-{HOLDER}"),
        fresh,
    };
    let request = Request::Lease {
        repo: "~/repo".to_owned(),
        holder: HOLDER.to_owned(),
    };
    assert_eq!(agent.reply(&request), lease(true));
    assert_eq!(agent.reply(&request), lease(false));
    let release = Request::Release {
        repo: "~/repo".to_owned(),
        holder: HOLDER.to_owned(),
    };
    assert_eq!(
        agent.reply(&release),
        Reply::Done {
            message: format!("released the lease lease-{HOLDER} of {}", world.slot(HOLDER).display())
        }
    );
    assert!(world.bd_calls().contains("--confirm-preserved"), "{}", world.bd_calls());
    assert_eq!(
        agent.reply(&release),
        Reply::Done {
            message: format!("{HOLDER} holds no lease on this host")
        }
    );
}

#[test]
fn serve_removes_a_run_that_ended_a_day_ago_and_the_ref_of_a_pack_without_a_run() {
    let world = World::new();
    // A pack that no start followed: its ref names no run directory, and its run id is a day old.
    git(&world.repo(), &["update-ref", "refs/yonder/1000-1", &world.commit]);
    fs::create_dir_all(world.home().join(".cache/yonderd")).expect("the cache directory");
    fs::write(world.home().join(".cache/yonderd/repos"), format!("{}\n", world.repo().display())).expect("the repository list");
    let old = world.run_dir("8-1");
    fs::create_dir_all(&old).expect("an old run");
    fs::write(
        old.join("spec.json"),
        serde_json::to_vec(&world.spec("8-1", HOLDER, "true")).expect("json"),
    )
    .expect("the spec");
    let exit = old.join("exit.json");
    fs::write(&exit, r#"{"code":0}"#).expect("the end");
    fs::File::options()
        .write(true)
        .open(&exit)
        .and_then(|file| file.set_modified(SystemTime::now() - Duration::from_secs(25 * 3600)))
        .expect("an old time");
    let agent = world.serve();
    eventually("the removal of the old run", || !agent.world.run_dir("8-1").exists());
    eventually("the removal of the stale ref", || {
        git(&agent.world.repo(), &["for-each-ref", "refs/yonder/"]).is_empty()
    });
    assert_eq!(agent.reply(&Request::Runs), Reply::Runs { runs: vec![] });
}

#[test]
fn version_prints_the_version_and_the_schema() {
    let world = World::new();
    let output = world.command().arg("version").output().expect("ran");
    assert!(output.status.success());
    let line: serde_json::Value = serde_json::from_slice(&output.stdout).expect("json");
    assert_eq!(line["schema_version"], SCHEMA_VERSION);
    assert_eq!(line["version"], env!("CARGO_PKG_VERSION"));
    assert_eq!(line["features"], serde_json::json!(yonder_link::proto::features()));
    let sha256 = yonder_link::digest::sha256_file(&agent_binary()).expect("the SHA-256 of the agent");
    assert_eq!(line["sha256"], sha256, "the build id");
}

#[test]
fn setup_adds_the_client_moves_the_leases_of_bt_and_starts_the_agent() {
    let world = World::new();
    let old_leases = world.home().join(".cache/bt-remote/leases");
    fs::create_dir_all(&old_leases).expect("the leases of remote.sh");
    fs::write(old_leases.join("bt-remote-0123.lease"), "lease_id=a\nslot=/s\nused=1\n").expect("a lease file");
    let launchctl_dir = world.dir.path().join("launchctl");
    fs::create_dir_all(&launchctl_dir).expect("the launchctl directory");
    write_script(&world.bin().join("launchctl"), FAKE_LAUNCHCTL);
    let client = Identity::generate().expect("a new client").fingerprint().to_string();
    let setup = || {
        let output = world
            .command()
            .args([
                "setup",
                "--client",
                &client,
                "--name",
                "laptop",
                "--port",
                "7742",
                "--listen-wait-ms",
                "0",
                "--launchctl",
            ])
            .arg(world.bin().join("launchctl"))
            .env("FAKE_LAUNCHCTL_DIR", &launchctl_dir)
            .output()
            .expect("ran");
        assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
        serde_json::from_slice::<serde_json::Value>(&output.stdout).expect("json")
    };
    let first = setup();
    let fingerprint = Identity::load(&world.home().join(".config/yonderd"))
        .expect("the agent identity")
        .fingerprint()
        .to_string();
    assert_eq!(first["fingerprint"], fingerprint.as_str());
    assert_eq!(first["port"], 7742);
    let allow = AllowList::read(&world.home().join(".config/yonderd/clients")).expect("the allow list");
    assert_eq!(allow.name(&client.parse().expect("a fingerprint")), Some("laptop"));
    assert_eq!(allow.name(&world.client.fingerprint()), Some("test"), "the old client stays");
    assert_eq!(mode(&world.home().join(".config/yonderd/clients")), 0o600);
    assert_eq!(mode(&world.home().join(".config/yonderd")), 0o700);
    assert_eq!(
        fs::read_to_string(world.lease_file("bt-remote-0123")).expect("the moved lease file"),
        "lease_id=a\nslot=/s\nused=1\n"
    );
    assert!(!old_leases.join("bt-remote-0123.lease").exists());

    if cfg!(target_os = "macos") {
        assert_eq!(first["launchd"], "bootstrapped");
        let plist = fs::read_to_string(world.home().join("Library/LaunchAgents/com.jetbrains.yonderd.plist")).expect("the plist");
        assert!(plist.contains("<string>7742</string>"), "{plist}");
        assert_eq!(setup()["launchd"], "restarted");
        let calls = fs::read_to_string(launchctl_dir.join("calls")).expect("the calls");
        let verbs: Vec<&str> = calls.lines().filter_map(|line| line.split(' ').next()).collect();
        assert_eq!(verbs, vec!["print", "bootstrap", "print", "kickstart"], "{calls}");
    } else {
        assert_eq!(first["launchd"], "none");
    }

    let refused = world
        .command()
        .args(["setup", "--client", "nothex", "--name", "x"])
        .output()
        .expect("ran");
    assert_eq!(refused.status.code(), Some(2));
}

#[test]
fn a_refusal_of_a_command_is_one_json_line_on_stderr() {
    let world = World::new();
    let output = world.command().env_remove("HOME").arg("serve").output().expect("ran");
    assert_eq!(output.status.code(), Some(1));
    let line: serde_json::Value = serde_json::from_slice(&output.stderr).expect("json");
    assert_eq!(line["refused"]["code"], "agent_error");
}

fn now_seconds() -> u64 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .expect("after the epoch")
        .as_secs()
}

/// The path of `git` on the PATH of this test, outside the bin directory of a world.
fn real_git() -> PathBuf {
    std::env::var_os("PATH")
        .and_then(|path| std::env::split_paths(&path).map(|dir| dir.join("git")).find(|git| git.is_file()))
        .expect("git on the PATH")
}

fn mode(path: &Path) -> u32 {
    fs::metadata(path)
        .unwrap_or_else(|error| panic!("{}: {error}", path.display()))
        .permissions()
        .mode()
        & 0o777
}

fn process_alive(pid: &str) -> bool {
    Command::new("kill")
        .args(["-0", pid])
        .stderr(Stdio::null())
        .status()
        .is_ok_and(|status| status.success())
}

#[test]
fn a_killed_supervisor_ends_the_run_for_cancel_and_follow() {
    let agent = World::new().serve();
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    agent.start(world.spec("10-1", HOLDER, &format!("echo holding; {}", wait_for(&gate))));
    agent.follow_until("10-1", 0, "holding");
    let pid = fs::read_to_string(world.run_dir("10-1").join("pid")).expect("the pid");
    let killed = Command::new("kill").args(["-9", pid.trim()]).status().expect("kill");
    assert!(killed.success());
    eventually("the end of the supervisor", || !process_alive(pid.trim()));
    // On a loaded host the flock of a killed process can stay a moment after its pid has gone.
    let lock = world.run_dir("10-1").join("lock");
    eventually("the release of the run lock", || {
        fs::File::open(&lock).is_ok_and(|file| file.try_lock_shared().is_ok())
    });
    // The follow of `follow_until` can see the lost run first, while its connection ends.
    let done = agent.reply(&Request::Cancel { run: "10-1".to_owned() });
    assert!(
        matches!(&done, Reply::Done { message } if message == "the run 10-1 had lost its supervisor; it has ended"
            || message == "the run 10-1 has ended already"),
        "{done:?}; {}",
        agent.dump("10-1")
    );
    let followed = agent.follow("10-1", 0);
    assert!(
        matches!(&followed.end, Reply::Refused { code, .. } if code == "agent_error"),
        "{followed:?}"
    );
    fs::write(&gate, "").expect("the gate");
}

#[test]
fn a_cancel_during_the_lock_wait_ends_the_run_before_its_command() {
    let agent = World::new().serve();
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    let marker = world.dir.path().join("ran");
    agent.start(world.spec("11-1", HOLDER, &format!("echo holding; {}", wait_for(&gate))));
    agent.follow_until("11-1", 0, "holding");
    agent.start(world.spec("11-2", HOLDER, &format!("touch '{}'", marker.display())));
    eventually("the wait of the second run", || {
        fs::read_to_string(world.run_dir("11-2").join("events.log")).is_ok_and(|log| log.contains("waiting for the other run"))
    });
    let done = agent.reply(&Request::Cancel { run: "11-2".to_owned() });
    assert!(matches!(done, Reply::Done { .. }), "{done:?}");
    let cancelled = agent.follow("11-2", 0);
    assert_eq!(cancelled.end, Reply::Exit { code: 130 }, "{}", agent.dump("11-2"));
    assert_eq!(cancelled.phase_names(), ["lock"]);
    assert!(!marker.exists(), "the command ran");
    fs::write(&gate, "").expect("the gate");
    assert_eq!(agent.follow("11-1", 0).end, Reply::Exit { code: 0 });
}

#[test]
fn a_cancel_during_the_checkout_kills_git_and_ends_the_run_before_its_command() {
    let world = World::new();
    write_script(&world.bin().join("git"), SLOW_GIT);
    let real = real_git();
    let agent = world.serve_with(&[("REAL_GIT", real.to_str().expect("a text path"))]);
    let world = &agent.world;
    let marker = world.dir.path().join("ran");
    fs::write(world.bd_dir().join("slow-checkout"), "").expect("the flag");
    git(&world.repo(), &["update-ref", "refs/yonder/12-1", &world.commit]);
    agent.start(world.spec("12-1", HOLDER, &format!("touch '{}'", marker.display())));
    eventually("the start of the checkout", || world.bd_dir().join("checkout-started").exists());
    let started = Instant::now();
    agent.reply(&Request::Cancel { run: "12-1".to_owned() });
    let cancelled = agent.follow("12-1", 0);
    assert_eq!(cancelled.end, Reply::Exit { code: 130 }, "{}", agent.dump("12-1"));
    assert_eq!(cancelled.phase_names(), ALL_PHASES[..4]);
    assert!(
        started.elapsed() < Duration::from_secs(20),
        "the checkout was not killed: {:?}",
        started.elapsed()
    );
    assert!(!marker.exists(), "the command ran");
    assert_eq!(git(&world.repo(), &["for-each-ref", "refs/yonder/"]), "", "the ref stays");
}

#[test]
fn a_command_that_ignores_sigint_gets_sigkill_after_the_grace_time() {
    let agent = World::new().serve_with(&[("YONDERD_TEST_GRACE_MS", "500")]);
    let world = &agent.world;
    let script = "trap '' INT; echo ready; i=0; while [ $i -lt 600 ]; do sleep 0.1; i=$((i + 1)); done";
    agent.start(world.spec("13-1", HOLDER, script));
    agent.follow_until("13-1", 0, "ready");
    agent.reply(&Request::Cancel { run: "13-1".to_owned() });
    let followed = agent.follow("13-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 128 + 9 }, "{}", agent.dump("13-1"));
    assert!(
        followed.texts().iter().any(|line| line.contains("after SIGINT; SIGKILL")),
        "{followed:?}"
    );
}

#[test]
fn a_process_that_the_command_leaves_in_its_group_gets_sigterm_and_sigkill_before_the_exit() {
    let agent = World::new().serve();
    let world = &agent.world;
    let pid_file = world.dir.path().join("left.pid");
    let script = format!(
        "(trap '' TERM; i=0; while [ $i -lt 600 ]; do sleep 0.1; i=$((i + 1)); done) & echo $! >'{}'; echo done",
        pid_file.display()
    );
    agent.start(world.spec("14-1", HOLDER, &script));
    let followed = agent.follow("14-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("14-1"));
    let texts = followed.texts();
    assert!(
        texts.contains(&"yonder: the command left processes in its group; SIGTERM"),
        "{followed:?}"
    );
    assert!(
        texts.contains(&"yonder: the processes of the group did not end after SIGTERM; SIGKILL"),
        "{followed:?}"
    );
    // The system reaps the killed process a moment later; its parent, the command, has ended.
    let left = fs::read_to_string(&pid_file).expect("the pid");
    eventually("the end of the process of the group", || !process_alive(left.trim()));
}

#[test]
fn a_refused_start_deletes_the_ref_of_its_pack() {
    let agent = World::new().serve();
    let world = &agent.world;
    git(&world.repo(), &["update-ref", "refs/yonder/15-1", &world.commit]);
    let refused = agent.reply(&Request::Start(RunSpec {
        fetch: vec!["../out".to_owned()],
        ..world.spec("15-1", HOLDER, "true")
    }));
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
        "{refused:?}"
    );
    assert_eq!(git(&world.repo(), &["for-each-ref", "refs/yonder/"]), "");
}

#[test]
fn a_slow_handshake_is_cut_at_the_deadline_and_a_socket_over_the_cap_closes_at_once() {
    let agent = World::new().serve_with(&[("YONDERD_TEST_HANDSHAKE_MS", "2000")]);
    let read_end = |stream: &mut TcpStream| {
        stream.set_read_timeout(Some(Duration::from_secs(10))).expect("a deadline");
        let started = Instant::now();
        let mut byte = [0u8; 1];
        let read = stream.read(&mut byte);
        assert!(matches!(read, Ok(0) | Err(_)), "{read:?}");
        started.elapsed()
    };

    // 8 handshakes that send nothing fill the cap of one peer, and its next socket is closed at once.
    let idle: Vec<TcpStream> = (0..8).map(|_| TcpStream::connect(agent.address).expect("connected")).collect();
    thread::sleep(Duration::from_millis(300));
    let mut over = TcpStream::connect(agent.address).expect("connected");
    let waited = read_end(&mut over);
    assert!(waited < Duration::from_millis(1500), "{waited:?}");
    drop(idle);

    // A client that sends a TLS record a byte at a time loses the connection at the total deadline.
    thread::sleep(Duration::from_millis(2500));
    let mut slow = TcpStream::connect(agent.address).expect("connected");
    slow.write_all(&[0x16, 0x03, 0x01, 0x02, 0x00]).expect("a record header");
    let mut writer = slow.try_clone().expect("a clone");
    let trickle = thread::spawn(move || {
        for _ in 0..40 {
            if writer.write_all(&[0]).is_err() {
                return;
            }
            thread::sleep(Duration::from_millis(250));
        }
    });
    let started = Instant::now();
    read_end(&mut slow);
    assert!(started.elapsed() < Duration::from_secs(5), "{:?}", started.elapsed());
    trickle.join().expect("the trickle");

    // The agent still serves.
    assert!(matches!(agent.reply(&Request::Runs), Reply::Runs { .. }));
}

#[test]
fn a_lease_sends_heartbeats_while_bd_works() {
    let agent = World::new().serve_with(&[("YONDERD_TEST_HEARTBEAT_MS", "100")]);
    let world = &agent.world;
    fs::write(world.bd_dir().join("slow-get"), "").expect("the flag");
    let (heartbeats, last) = agent.reply_after_heartbeats(&Request::Lease {
        repo: "~/repo".to_owned(),
        holder: HOLDER.to_owned(),
    });
    assert!(matches!(last, Reply::Leased { fresh: true, .. }), "{last:?}");
    assert!(heartbeats >= 3, "{heartbeats} heartbeats");
}

#[test]
fn a_failed_release_keeps_the_lease_file() {
    let agent = World::new().serve();
    let world = &agent.world;
    let lease = agent.reply(&Request::Lease {
        repo: "~/repo".to_owned(),
        holder: HOLDER.to_owned(),
    });
    assert!(matches!(lease, Reply::Leased { .. }), "{lease:?}");
    fs::write(world.bd_dir().join("fail-return"), "").expect("the flag");
    let refused = agent.reply(&Request::Release {
        repo: "~/repo".to_owned(),
        holder: HOLDER.to_owned(),
    });
    assert!(
        matches!(&refused, Reply::Refused { code, message } if code == "lease_failed" && message.contains("local changes")),
        "{refused:?}"
    );
    assert!(world.lease_file(HOLDER).is_file());
}

#[test]
fn the_files_of_the_agent_are_private() {
    let agent = World::new().serve();
    let world = &agent.world;
    git(&world.repo(), &["update-ref", "refs/yonder/16-1", &world.commit]);
    agent.start(world.spec("16-1", HOLDER, "mkdir -p out/bt-runs/r; echo x >out/bt-runs/r/f"));
    assert_eq!(agent.follow("16-1", 0).end, Reply::Exit { code: 0 });
    let home = world.home();
    for dir in [
        ".config/yonderd",
        ".cache/yonderd",
        ".cache/yonderd/runs",
        ".cache/yonderd/runs/16-1",
        ".cache/yonderd/leases",
    ] {
        assert_eq!(mode(&home.join(dir)), 0o700, "{dir}");
    }
    for file in [
        ".config/yonderd/identity.key",
        ".cache/yonderd/runs/16-1/spec.json",
        ".cache/yonderd/runs/16-1/events.log",
        ".cache/yonderd/runs/16-1/exit.json",
        ".cache/yonderd/runs/16-1/phases.json",
        ".cache/yonderd/runs/16-1/pid",
        ".cache/yonderd/runs/16-1/lock",
        ".cache/yonderd/runs/16-1/supervisor.log",
        &format!(".cache/yonderd/leases/{HOLDER}.lease"),
    ] {
        assert_eq!(mode(&home.join(file)), 0o600, "{file}");
    }
    assert_eq!(mode(&home.join(".cache/yonderd/runs/16-1/stage")), 0o700);
}

/// The text of `events.log` of a run, or nothing.
fn events(world: &World, run: &str) -> String {
    fs::read_to_string(world.run_dir(run).join("events.log")).unwrap_or_default()
}

#[test]
fn a_start_before_its_pack_takes_the_lease_waits_and_goes_on_when_the_pack_comes() {
    let agent = World::new().serve();
    let world = &agent.world;
    let (head, pack) = local_commit(world);
    agent.start(RunSpec {
        commit: head.clone(),
        ..world.spec("20-1", HOLDER, "cat b.txt; echo")
    });
    let waiting = format!("yonder: waiting for the pack of {head}");
    eventually("the wait for the pack", || events(world, "20-1").contains(&waiting));
    assert!(world.slot(HOLDER).is_dir(), "the lease comes before the pack");
    let Reply::Runs { runs } = agent.reply(&Request::Runs) else {
        panic!("not runs");
    };
    let names: Vec<&str> = runs[0].phases.iter().map(|phase| phase.name.as_str()).collect();
    assert_eq!(names, ["lock", "lease"]);

    assert_eq!(agent.pack(&head, "refs/yonder/20-1", &pack), Reply::Packed);
    let followed = agent.follow("20-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("20-1"));
    assert_eq!(followed.of(Stream::Stdout), vec!["b"]);
    assert!(followed.of(Stream::Stderr).contains(&waiting.as_str()), "{followed:?}");
    assert_eq!(followed.phase_names(), ALL_PHASES);
    assert!(followed.phase_ms("pack_wait") >= 1000, "{followed:?}");
    assert_eq!(git(&world.repo(), &["for-each-ref", "refs/yonder/"]), "", "the run deletes its ref");
}

#[test]
fn a_start_whose_pack_never_comes_ends_with_pack_missing() {
    let agent = World::new().serve_with(&[("YONDERD_TEST_PACK_WAIT_MS", "300")]);
    let world = &agent.world;
    let marker = world.dir.path().join("ran");
    agent.start(RunSpec {
        commit: "1".repeat(40),
        ..world.spec("21-1", HOLDER, &format!("touch '{}'", marker.display()))
    });
    let followed = agent.follow("21-1", 0);
    assert!(
        matches!(&followed.end, Reply::Refused { code, message } if code == "pack_missing" && message.contains("refs/yonder/21-1")),
        "{followed:?}"
    );
    assert_eq!(followed.phase_names(), ["lock", "lease", "pack_wait"]);
    assert!(
        !followed.texts().iter().any(|line| line.contains("waiting for the pack")),
        "a wait under a second says nothing: {followed:?}"
    );
    assert!(!marker.exists(), "the command ran");
    let Reply::Runs { runs } = agent.reply(&Request::Runs) else {
        panic!("not runs");
    };
    assert_eq!(runs[0].exit, Some(255));
    assert_eq!(runs[0].phases, followed.phases.expect("the phases"));
}

/// A client that builds its pack sends empty chunks, and they keep the wait of its run alive past the stall time. A
/// start whose pack connection never comes ends with `pack_missing` after the stall time, long before the deadline.
#[test]
fn the_chunks_of_a_pack_keep_its_run_waiting_and_a_start_without_them_ends_at_the_stall() {
    // The agent notes the chunks once a second, so the stall time is a few times that.
    let agent = World::new().serve_with(&[("YONDERD_TEST_PACK_STALL_MS", "3000")]);
    let world = &agent.world;
    let (head, pack) = local_commit(world);
    agent.start(RunSpec {
        commit: head.clone(),
        ..world.spec("25-1", HOLDER, "cat b.txt; echo")
    });
    let mut channel = agent.ask(&Request::Pack {
        repo: "~/repo".to_owned(),
        commit: head,
        reference: "refs/yonder/25-1".to_owned(),
    });
    let building = Instant::now();
    while building.elapsed() < Duration::from_millis(5000) {
        channel.send_keepalive().expect("an empty chunk");
        thread::sleep(Duration::from_millis(150));
    }
    channel.send_stream(&mut &pack[..], &mut |_| {}).expect("the pack sent");
    assert_eq!(channel.recv::<Reply>().expect("the reply"), Reply::Packed);
    let followed = agent.follow("25-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("25-1"));
    assert_eq!(followed.of(Stream::Stdout), vec!["b"]);
    // The wait starts after the lease, and it outlived the stall time.
    assert!(followed.phase_ms("pack_wait") >= 3000, "{followed:?}");

    let started = Instant::now();
    agent.start(RunSpec {
        commit: "1".repeat(40),
        ..world.spec("25-2", HOLDER, "true")
    });
    let stalled = agent.follow("25-2", 0);
    assert!(
        matches!(&stalled.end, Reply::Refused { code, message } if code == "pack_missing" && message.contains("sent no chunk")),
        "{stalled:?}"
    );
    assert!(started.elapsed() < Duration::from_secs(20), "{:?}", started.elapsed());
}

/// A pack that comes after the end of its run makes no ref, because no run is left to delete it.
#[test]
fn a_pack_after_the_end_of_its_run_leaves_no_ref() {
    let agent = World::new().serve();
    let world = &agent.world;
    agent.start(world.spec("26-1", HOLDER, "true"));
    assert_eq!(agent.follow("26-1", 0).end, Reply::Exit { code: 0 });
    let (head, pack) = local_commit(world);
    assert_eq!(agent.pack(&head, "refs/yonder/26-1", &pack), Reply::Packed);
    assert_eq!(git(&world.repo(), &["for-each-ref", "refs/yonder/"]), "");
    assert_eq!(git(&world.repo(), &["cat-file", "-t", &head]), "commit", "the objects land");
}

#[test]
fn a_cancel_during_the_pack_wait_ends_the_run_before_its_command() {
    let agent = World::new().serve();
    let world = &agent.world;
    let marker = world.dir.path().join("ran");
    agent.start(RunSpec {
        commit: "1".repeat(40),
        ..world.spec("22-1", HOLDER, &format!("touch '{}'", marker.display()))
    });
    eventually("the wait for the pack", || events(world, "22-1").contains("waiting for the pack"));
    let started = Instant::now();
    let done = agent.reply(&Request::Cancel { run: "22-1".to_owned() });
    assert!(matches!(done, Reply::Done { .. }), "{done:?}");
    let followed = agent.follow("22-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 130 }, "{}", agent.dump("22-1"));
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
    assert_eq!(followed.phase_names(), ["lock", "lease", "pack_wait"]);
    assert!(!marker.exists(), "the command ran");
}

#[test]
fn a_pack_to_a_base_ref_lands_and_the_cleanup_keeps_the_base_refs() {
    let world = World::new();
    git(&world.repo(), &["update-ref", "refs/yonder/base/master", &world.commit]);
    // A stale run ref that sorts after the base ref: once it is gone, the cleanup has passed the base ref.
    git(&world.repo(), &["update-ref", "refs/yonder/zz-stale", &world.commit]);
    fs::create_dir_all(world.home().join(".cache/yonderd")).expect("the cache directory");
    fs::write(world.home().join(".cache/yonderd/repos"), format!("{}\n", world.repo().display())).expect("the repository list");
    let agent = world.serve();
    let world = &agent.world;
    eventually("the removal of the stale ref", || {
        git(&world.repo(), &["for-each-ref", "--format=%(refname)", "refs/yonder/"]) == "refs/yonder/base/master"
    });

    let (head, pack) = local_commit(world);
    assert_eq!(agent.pack(&head, "refs/yonder/base/master", &pack), Reply::Packed);
    assert_eq!(git(&world.repo(), &["rev-parse", "refs/yonder/base/master"]), head);
    for bad in ["refs/yonder/base/a/b", "refs/yonder/base/.x", "refs/yonder/base/"] {
        let refused = agent.pack(&head, bad, &pack);
        assert!(
            matches!(&refused, Reply::Refused { code, .. } if code == "bad_request"),
            "{bad}: {refused:?}"
        );
    }
}

#[test]
fn heartbeats_go_on_while_the_follow_thread_is_held_in_a_file_read() {
    let world = World::new();
    let hold = world.dir.path().join("hold");
    fs::write(&hold, "").expect("the hold");
    let agent = world.serve_with(&[
        ("YONDERD_TEST_HEARTBEAT_MS", "100"),
        ("YONDERD_TEST_FOLLOW_HOLD", hold.to_str().expect("a text path")),
    ]);
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    agent.start(world.spec("23-1", HOLDER, &format!("echo ready; {}", wait_for(&gate))));
    eventually("the first line", || events(world, "23-1").contains("ready"));

    let mut channel = agent.ask(&Request::Follow {
        run: "23-1".to_owned(),
        offset: 0,
    });
    channel.set_read_deadline(Duration::from_secs(2)).expect("a deadline");
    let held = Instant::now();
    let mut heartbeats = 0;
    while held.elapsed() < Duration::from_secs(1) {
        match channel.recv::<Reply>() {
            Ok(Reply::Heartbeat { .. }) => heartbeats += 1,
            other => panic!("only heartbeats come while the follow is held: {other:?}"),
        }
    }
    assert!(heartbeats >= 5, "{heartbeats} heartbeats in a second");

    fs::remove_file(&hold).expect("the hold released");
    let first = loop {
        match channel.recv::<Reply>().expect("a reply") {
            Reply::Heartbeat { .. } => {}
            other => break other,
        }
    };
    assert!(matches!(&first, Reply::Output { .. }), "{first:?}");
    fs::write(&gate, "").expect("the gate");
    drop(channel);
    assert_eq!(agent.follow("23-1", 0).end, Reply::Exit { code: 0 });
}

#[test]
#[cfg_attr(not(target_os = "macos"), ignore = "only macOS pushes the changes of the run files; Linux polls")]
fn a_new_event_reaches_the_follow_in_less_than_50_ms() {
    let agent = World::new().serve();
    let world = &agent.world;
    let gate = world.dir.path().join("gate");
    agent.start(world.spec("24-1", HOLDER, &format!("echo ready; {}", wait_for(&gate))));
    agent.follow_until("24-1", 0, "ready");

    let mut channel = agent.ask(&Request::Follow {
        run: "24-1".to_owned(),
        offset: fs::metadata(world.run_dir("24-1").join("events.log")).expect("the log").len(),
    });
    // The command waits for its gate, so the log is quiet. The test appends an event as the supervisor would.
    let mut log = fs::OpenOptions::new()
        .append(true)
        .open(world.run_dir("24-1").join("events.log"))
        .expect("the log");
    let mut latencies = Vec::new();
    for index in 0..7 {
        thread::sleep(Duration::from_millis(300));
        let text = format!("event-{index}");
        let line = format!("{}\n", serde_json::json!({"stream": "stdout", "text": text}));
        let written = Instant::now();
        log.write_all(line.as_bytes()).expect("appended");
        loop {
            match channel.recv::<Reply>().expect("a reply") {
                Reply::Output { text: got, .. } if got == text => break,
                Reply::Heartbeat { .. } => {}
                other => panic!("{other:?}"),
            }
        }
        latencies.push(written.elapsed());
    }
    // The median: one event on a loaded host can wait for the scheduler, while a poll of 200 ms delays each event.
    latencies.sort();
    assert!(latencies[3] < Duration::from_millis(50), "the median is over 50 ms: {latencies:?}");
    fs::write(&gate, "").expect("the gate");
    drop(channel);
    assert_eq!(agent.follow("24-1", 0).end, Reply::Exit { code: 0 });
}

#[test]
fn a_have_sends_heartbeats_while_git_works() {
    let world = World::new();
    write_script(&world.bin().join("git"), SLOW_GIT);
    let real = real_git();
    let agent = world.serve_with(&[
        ("REAL_GIT", real.to_str().expect("a text path")),
        ("YONDERD_TEST_HEARTBEAT_MS", "100"),
    ]);
    let world = &agent.world;
    fs::write(world.bd_dir().join("slow-cat-file"), "").expect("the flag");
    let (heartbeats, last) = agent.reply_after_heartbeats(&Request::Have {
        repo: "~/repo".to_owned(),
        commits: vec![world.commit.clone()],
    });
    assert_eq!(
        last,
        Reply::Have {
            commits: vec![world.commit.clone()]
        }
    );
    assert!(heartbeats >= 3, "{heartbeats} heartbeats");
}

/// A login shell for the tests: it sets `FROM_PROFILE` and puts `/profile/bin` first on `PATH`, then runs the script
/// of `-c`, its last argument, as a real login shell would after its profile.
const LOGIN_SHELL: &str = r#"#!/bin/sh
for script; do :; done
FROM_PROFILE=yes PATH="/profile/bin:$PATH"
export FROM_PROFILE PATH
exec /bin/sh -c "$script"
"#;

/// Commits `files` in the main repository of the world, and answers the commit.
fn commit_files(world: &World, files: &[(&str, &str)]) -> String {
    let repo = world.repo();
    for (path, text) in files {
        let path = repo.join(path);
        fs::create_dir_all(path.parent().expect("a parent")).expect("the parent directory");
        fs::write(path, text).expect("a file");
    }
    git(&repo, &["add", "--all"]);
    git(&repo, &["commit", "-q", "-m", "files"]);
    git(&repo, &["rev-parse", "HEAD"])
}

/// The files of a fetch: path, kind and text.
fn fetch_files(agent: &Agent, run: &str) -> Vec<(String, FileKind, String)> {
    let mut channel = agent.ask(&Request::Fetch { run: run.to_owned() });
    let mut files = Vec::new();
    loop {
        match channel.recv::<Reply>().expect("a reply") {
            Reply::File { path, kind, .. } => {
                let mut bytes = Vec::new();
                channel.recv_stream(&mut bytes).expect("the bytes");
                files.push((path, kind, String::from_utf8(bytes).expect("text")));
            }
            Reply::Fetched { .. } => return files,
            other => panic!("{other:?}"),
        }
    }
}

#[test]
fn a_run_gets_the_login_environment_a_raised_open_files_limit_and_its_niceness() {
    let world = World::new();
    let shell = world.dir.path().join("login-shell");
    write_script(&shell, LOGIN_SHELL);
    let agent = world.serve_with(&[("YONDERD_TEST_SHELL", &shell.to_string_lossy())]);
    let world = &agent.world;
    agent.start(RunSpec {
        nice: Some(7),
        ..world.spec(
            "20-1",
            HOLDER,
            r#"echo "$FROM_PROFILE"; echo "$PATH" | cut -d: -f1; ulimit -n; ps -o nice= -p $$"#,
        )
    });
    let followed = agent.follow("20-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("20-1"));
    let stdout = followed.of(Stream::Stdout);
    assert_eq!(stdout[..2], ["yes", "/profile/bin"], "{followed:?}");
    let limit: u64 = stdout[2].trim().parse().expect("a limit");
    assert!(limit >= 4096, "{limit}");
    assert_eq!(stdout[3].trim(), "7");
    let start = followed.start_line();
    assert!(start.contains(", nice 7, "), "{start}");
    assert!(
        start.contains(&format!("the login environment of {} -l -i from ", shell.display())),
        "{start}"
    );
    let kept = fs::read_to_string(world.home().join(".cache/yonderd/login-env.json")).expect("the capture");
    assert!(kept.contains("FROM_PROFILE") && !kept.contains("\"PWD\""), "{kept}");
}

#[test]
fn the_config_of_the_commit_sets_the_environment_and_runs_a_prepare_step_only_when_its_inputs_change() {
    let agent = World::new().serve();
    let world = &agent.world;
    let config = r#"
[env]
GREETING = "hi"
PATH = { prepend = ["~/tools"] }
PORT = { port = 24000 }
FAKE_BD_DIR = { unset = true }
DIR = { prepend = ["~/a"], append = ["/b"] }

[[prepare]]
name = "deps"
run = ["/bin/sh", "-c", "echo preparing >&1; echo once >>prepared.log; mkdir -p deps"]
inputs = ["lock.txt"]
creates = ["deps"]
"#;
    let first = commit_files(world, &[(".yonder.toml", config), ("lock.txt", "1")]);
    let script = r#"echo "$GREETING $DIR $FAKE_BD_DIR"; echo "$PATH" | cut -d: -f1; echo "$PORT"; echo "$YONDER_RUN""#;
    let run = |id: &str, commit: &str| {
        agent.start(RunSpec {
            commit: commit.to_owned(),
            ..world.spec(id, HOLDER, script)
        });
        agent.follow(id, 0)
    };
    let slot = world.slot(HOLDER);
    let prepared = || fs::read_to_string(slot.join("prepared.log")).unwrap_or_default().lines().count();

    let followed = run("21-1", &first);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("21-1"));
    assert_eq!(followed.phase_names(), ALL_PHASES);
    let home = world.home();
    let stdout = followed.of(Stream::Stdout);
    assert_eq!(stdout[0], format!("hi {}/a:/b ", home.display()), "{followed:?}");
    assert_eq!(stdout[1], format!("{}/tools", home.display()));
    let port: u16 = stdout[2].parse().expect("a port");
    assert!((24_000..24_200).contains(&port), "{port}");
    assert_eq!(stdout[3], "21-1");
    // The output of a prepare step goes to the stderr of the run, so the stdout holds only the command.
    let stderr = followed.of(Stream::Stderr);
    assert!(stderr.contains(&"yonder: prepare: deps (deps is missing)"), "{stderr:?}");
    assert!(stderr.contains(&"preparing"), "{stderr:?}");
    assert_eq!(prepared(), 1);
    let lease = fs::read_to_string(world.lease_file(HOLDER)).expect("the lease file");
    assert!(lease.contains("\nprepared."), "{lease}");

    assert_eq!(run("21-2", &first).end, Reply::Exit { code: 0 });
    assert_eq!(prepared(), 1, "the inputs did not change");

    let second = commit_files(world, &[("lock.txt", "2")]);
    let followed = run("21-3", &second);
    assert!(
        followed.texts().contains(&"yonder: prepare: deps (lock.txt changed)"),
        "{followed:?}"
    );
    assert_eq!(prepared(), 2);

    fs::remove_dir_all(slot.join("deps")).expect("deps removed");
    assert_eq!(run("21-4", &second).end, Reply::Exit { code: 0 });
    assert_eq!(prepared(), 3, "a missing created path runs the step again");

    let failing = commit_files(
        world,
        &[(
            ".yonder.toml",
            "[[prepare]]\nname = \"broken\"\nrun = [\"/bin/sh\", \"-c\", \"echo cannot; exit 4\"]\n",
        )],
    );
    let followed = run("21-5", &failing);
    assert!(
        matches!(&followed.end, Reply::Refused { code, message } if code == "prepare_failed" && message.contains("exited with 4")),
        "{followed:?}"
    );
    assert_eq!(followed.phase_names(), ALL_PHASES[..5]);
    assert!(followed.texts().contains(&"cannot"), "{followed:?}");
    assert!(followed.of(Stream::Stdout).is_empty(), "the command did not run: {followed:?}");

    let typo = commit_files(world, &[(".yonder.toml", "[[prepar]]\nrun = [\"true\"]\n")]);
    let followed = run("21-6", &typo);
    assert!(
        matches!(&followed.end, Reply::Refused { code, message } if code == "bad_config" && message.contains("prepar")),
        "{followed:?}"
    );
}

#[test]
fn the_outputs_of_the_config_come_back_only_to_a_client_that_asks_for_them() {
    let agent = World::new().serve();
    let world = &agent.world;
    let commit = commit_files(
        world,
        &[
            (".gitignore", "results/\nci/\n"),
            (".yonder.toml", "outputs = [\"results\", \"ci/junit.xml\"]\n"),
        ],
    );
    let script = "mkdir -p results ci; echo new >results/new.txt; echo '<x/>' >ci/junit.xml";
    agent.start(RunSpec {
        commit: commit.clone(),
        outputs: true,
        ..world.spec("22-1", HOLDER, script)
    });
    assert_eq!(agent.follow("22-1", 0).end, Reply::Exit { code: 0 }, "{}", agent.dump("22-1"));
    let results = FileKind::Output {
        root: "results".to_owned(),
    };
    let junit = FileKind::Output {
        root: "ci/junit.xml".to_owned(),
    };
    assert_eq!(
        fetch_files(&agent, "22-1"),
        vec![
            ("results/new.txt".to_owned(), results.clone(), "new\n".to_owned()),
            ("ci/junit.xml".to_owned(), junit, "<x/>\n".to_owned()),
        ]
    );

    // An output of an earlier run is gone before the command, so it never comes back.
    let slot = world.slot(HOLDER);
    fs::create_dir_all(slot.join("results")).expect("results");
    fs::write(slot.join("results/stale.txt"), "stale").expect("a stale output");
    agent.start(RunSpec {
        commit: commit.clone(),
        outputs: true,
        ..world.spec("22-2", HOLDER, "mkdir -p results; echo fresh >results/fresh.txt")
    });
    assert_eq!(agent.follow("22-2", 0).end, Reply::Exit { code: 0 });
    assert_eq!(
        fetch_files(&agent, "22-2"),
        vec![("results/fresh.txt".to_owned(), results, "fresh\n".to_owned())]
    );

    // An older client asks for no outputs: the agent leaves them alone and sends none.
    fs::create_dir_all(slot.join("results")).expect("results");
    fs::write(slot.join("results/kept.txt"), "kept").expect("an output of the slot");
    agent.start(RunSpec {
        commit,
        ..world.spec("22-3", HOLDER, "echo '<y/>' >ci/junit.xml")
    });
    assert_eq!(agent.follow("22-3", 0).end, Reply::Exit { code: 0 });
    assert_eq!(fetch_files(&agent, "22-3"), Vec::new());
    assert!(slot.join("results/kept.txt").is_file());
    assert!(slot.join("ci/junit.xml").is_file());
}

#[test]
fn the_changes_of_the_command_come_back_as_a_patch_without_the_files_it_found() {
    let agent = World::new().serve();
    let world = &agent.world;
    // The first run makes the slot; a file that no commit has stays there for the next run.
    agent.start(world.spec("23-1", HOLDER, "echo junk >junk.txt"));
    assert_eq!(agent.follow("23-1", 0).end, Reply::Exit { code: 0 });
    agent.start(RunSpec {
        changes: true,
        ..world.spec("23-2", HOLDER, "echo changed >a.txt; echo new >new.txt; chmod +x new.txt")
    });
    let followed = agent.follow("23-2", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("23-2"));
    let files = fetch_files(&agent, "23-2");
    assert_eq!(files.len(), 1, "{files:?}");
    let (path, kind, patch) = &files[0];
    assert_eq!((path.as_str(), kind), ("changes.patch", &FileKind::Changes));
    assert!(!patch.contains("junk"), "{patch}");

    // The patch applies to a clone at the commit of the run.
    let clone = world.dir.path().join("clone");
    git(
        world.dir.path(),
        &["clone", "-q", &world.repo().to_string_lossy(), &clone.to_string_lossy()],
    );
    fs::write(world.dir.path().join("changes.patch"), patch).expect("the patch");
    git(&clone, &["apply", &world.dir.path().join("changes.patch").to_string_lossy()]);
    assert_eq!(fs::read_to_string(clone.join("a.txt")).expect("a.txt"), "changed\n");
    assert_eq!(fs::read_to_string(clone.join("new.txt")).expect("new.txt"), "new\n");
    let mode = fs::metadata(clone.join("new.txt")).expect("the metadata").permissions().mode();
    assert_eq!(mode & 0o111, 0o111, "the executable bit comes along");

    // A command that changes nothing brings no patch.
    agent.start(RunSpec {
        changes: true,
        ..world.spec("23-3", HOLDER, "true")
    });
    assert_eq!(agent.follow("23-3", 0).end, Reply::Exit { code: 0 });
    assert_eq!(fetch_files(&agent, "23-3"), Vec::new());
}

/// Copies the agent binary to the binary of a LaunchAgent of the world on a free port, and lets `setup` of that binary
/// write the plist with the fake launchctl. Answers the binary of the LaunchAgent and its port; the calls of `setup` to
/// the fake launchctl are gone.
fn launch_agent(world: &World) -> (PathBuf, u16) {
    let target = world.dir.path().join("agent/yonderd");
    fs::create_dir_all(target.parent().expect("a parent")).expect("the agent directory");
    fs::copy(agent_binary(), &target).expect("the binary of the LaunchAgent");
    write_script(&world.launchctl(), FAKE_LAUNCHCTL);
    fs::create_dir_all(world.launchctl_dir()).expect("the launchctl directory");
    let port = std::net::TcpListener::bind("127.0.0.1:0")
        .and_then(|listener| listener.local_addr())
        .expect("a free port")
        .port();
    let output = world
        .command_of(&target)
        .args(["setup", "--client", &world.client.fingerprint().to_string(), "--name", "test"])
        .args(["--port", &port.to_string(), "--listen-wait-ms", "0", "--launchctl"])
        .arg(world.launchctl())
        .env("FAKE_LAUNCHCTL_DIR", world.launchctl_dir())
        .output()
        .expect("setup");
    assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
    let _ = fs::remove_file(world.launchctl_dir().join("calls"));
    (target, port)
}

impl World {
    fn launchctl(&self) -> PathBuf {
        self.bin().join("launchctl")
    }

    fn launchctl_dir(&self) -> PathBuf {
        self.dir.path().join("launchctl")
    }

    fn launchctl_calls(&self) -> String {
        fs::read_to_string(self.launchctl_dir().join("calls")).unwrap_or_default()
    }

    /// The log of the agent under launchd, which the watchdog writes.
    fn agent_log(&self) -> String {
        fs::read_to_string(self.home().join("Library/Logs/yonderd.log")).unwrap_or_default()
    }

    fn upgrade_outcome(&self) -> Option<serde_json::Value> {
        serde_json::from_slice(&fs::read(self.home().join(".cache/yonderd/upgrade.json")).ok()?).ok()
    }

    /// Starts `serve` of the binary of the LaunchAgent on its port with no drain and a watchdog that waits `wait_ms`.
    fn serve_upgradable(self, (target, port): &(PathBuf, u16), wait_ms: &str) -> Agent {
        let (launchctl, launchctl_dir) = (self.launchctl(), self.launchctl_dir());
        let launchctl_dir = launchctl_dir.to_string_lossy().into_owned();
        let env = [
            ("YONDERD_TEST_UPGRADE_DRAIN_MS", "0"),
            ("YONDERD_TEST_UPGRADE_WAIT_MS", wait_ms),
            ("FAKE_LAUNCHCTL_DIR", launchctl_dir.as_str()),
        ];
        self.serve_program(target, *port, &["--launchctl", &launchctl.to_string_lossy()], &env)
    }
}

impl Agent {
    /// Sends `binary` as an upgrade of `size` bytes with `sha256`, and answers the last reply.
    fn upgrade(&self, binary: &[u8], size: u64, sha256: &str) -> Reply {
        let mut channel = self.ask(&Request::Upgrade {
            size,
            sha256: sha256.to_owned(),
            force: false,
        });
        channel.send_stream(&mut &binary[..], &mut |_| {}).expect("the binary");
        loop {
            match channel.recv().expect("the reply") {
                Reply::Heartbeat { .. } => {}
                reply => return reply,
            }
        }
    }

    /// Waits up to 30 s for the end of `serve`.
    fn wait_for_exit(&mut self) -> std::process::ExitStatus {
        let started = Instant::now();
        loop {
            if let Some(status) = self.child.try_wait().expect("the status of serve") {
                return status;
            }
            assert!(started.elapsed() < Duration::from_secs(30), "serve did not exit in 30 s");
            thread::sleep(Duration::from_millis(50));
        }
    }
}

fn inode(path: &Path) -> u64 {
    std::os::unix::fs::MetadataExt::ino(&fs::metadata(path).expect("the metadata"))
}

#[test]
#[cfg_attr(not(target_os = "macos"), ignore = "only an agent under launchd upgrades itself")]
fn an_upgrade_swaps_the_binary_and_ends_serve_and_a_run_and_the_watchdog_see_the_next_serve() {
    let world = World::new();
    let launch = launch_agent(&world);
    let target = launch.0.clone();
    let mut agent = world.clone().serve_upgradable(&launch, "30000");
    let binary = fs::read(agent_binary()).expect("the agent binary");
    let sha256 = yonder_link::digest::sha256_file(&target).expect("the SHA-256");
    let hello = agent.ask(&Request::Runs).recv::<Reply>().expect("the runs");
    assert!(matches!(hello, Reply::Runs { .. }), "{hello:?}");

    let refused = agent.upgrade(&binary, binary.len() as u64, &"0".repeat(64));
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_binary"),
        "{refused:?}"
    );
    let refused = agent.upgrade(b"short", 5, &sha256);
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "bad_binary"),
        "{refused:?}"
    );
    let names = |dir: &Path| -> Vec<String> {
        let mut names: Vec<String> = fs::read_dir(dir)
            .expect("the directory")
            .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
            .collect();
        names.sort();
        names
    };
    let agent_dir = target.parent().expect("a parent").to_path_buf();
    assert_eq!(names(&agent_dir), ["yonderd"], "a refused binary leaves no file");

    // A LaunchAgent that the new binary would write in another way stays with `yonder prepare`.
    let plist = world.home().join("Library/LaunchAgents/com.jetbrains.yonderd.plist");
    let text = fs::read_to_string(&plist).expect("the plist");
    fs::write(
        &plist,
        text.replace("<key>RunAtLoad</key>\n  <true/>", "<key>RunAtLoad</key>\n  <false/>"),
    )
    .expect("another plist");
    let refused = agent.upgrade(&binary, binary.len() as u64, &sha256);
    assert!(
        matches!(&refused, Reply::Refused { code, .. } if code == "plist_changed"),
        "{refused:?}"
    );
    fs::write(&plist, text).expect("the plist of setup");
    assert_eq!(names(&agent_dir), ["yonderd"]);

    // The run streams through the upgrade: its follow breaks with the old serve and goes on with the new one.
    let gate = world.dir.path().join("gate");
    agent.start(world.spec("1-upgrade", HOLDER, &format!("echo before; {}; echo after", wait_for(&gate))));
    let mut streaming = agent.ask(&Request::Follow {
        run: "1-upgrade".to_owned(),
        offset: 0,
    });
    let offset = loop {
        match streaming.recv::<Reply>().expect("a reply of the follow") {
            Reply::Output { text, end, .. } if text == "before" => break end,
            Reply::Output { .. } | Reply::Heartbeat { .. } => {}
            end => panic!("the run ended before its first line: {end:?}"),
        }
    };

    let before = inode(&target);
    let upgraded = agent.upgrade(&binary, binary.len() as u64, &sha256);
    assert_eq!(
        upgraded,
        Reply::Upgraded {
            previous_sha256: sha256.clone(),
            sha256: sha256.clone(),
            version: env!("CARGO_PKG_VERSION").to_owned(),
        }
    );
    assert!(agent.wait_for_exit().success(), "{}", agent.dump("1-upgrade"));
    assert!(
        loop {
            match streaming.recv::<Reply>() {
                Ok(Reply::Heartbeat { .. }) => {}
                Ok(reply) => panic!("the old serve answered after its exit: {reply:?}"),
                Err(error) => break error,
            }
        }
        .to_string()
        .contains("closed"),
        "the follow breaks with the old serve"
    );
    assert_eq!(names(&agent_dir), ["yonderd", "yonderd.previous"]);
    assert_eq!(inode(&previous_path(&target)), before);
    assert_ne!(inode(&target), before);
    assert!(!world.home().join(".cache/yonderd/listening.json").exists());

    // launchd would start the binary again.
    let renewed = world.clone().serve_upgradable(&launch, "30000");
    let short = &sha256[..12];
    eventually("the watchdog sees the new serve", || {
        world
            .agent_log()
            .contains(&format!("watchdog: the binary {short} listens with the pid"))
    });
    assert_eq!(world.upgrade_outcome().expect("the outcome")["outcome"], "listened");
    fs::write(&gate, "").expect("the gate");
    let followed = renewed.follow("1-upgrade", offset);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{followed:?}");
    assert_eq!(followed.of(Stream::Stdout), ["after"]);
    assert_eq!(world.launchctl_calls(), "");
}

#[test]
#[cfg_attr(not(target_os = "macos"), ignore = "only an agent under launchd upgrades itself")]
fn the_watchdog_restores_the_previous_binary_and_restarts_the_agent_when_no_serve_of_the_new_one_listens() {
    let world = World::new();
    let launch = launch_agent(&world);
    let target = launch.0.clone();
    let mut agent = world.clone().serve_upgradable(&launch, "500");
    let binary = fs::read(agent_binary()).expect("the agent binary");
    let sha256 = yonder_link::digest::sha256_file(&target).expect("the SHA-256");
    let before = inode(&target);
    let upgraded = agent.upgrade(&binary, binary.len() as u64, &sha256);
    assert!(matches!(upgraded, Reply::Upgraded { .. }), "{upgraded:?}");
    assert!(agent.wait_for_exit().success());

    eventually("the watchdog restarts the agent", || world.launchctl_calls().contains("kickstart"));
    let calls = world.launchctl_calls();
    assert!(
        calls.starts_with("kickstart -k gui/") && calls.ends_with("/com.jetbrains.yonderd\n"),
        "{calls}"
    );
    assert_eq!(inode(&target), before, "the previous binary is back");
    assert!(!previous_path(&target).exists());
    let log = world.agent_log();
    assert!(log.contains("so the previous binary comes back"), "{log}");
    // The fake launchctl starts no agent, so the port stays closed, and the outcome says so.
    eventually("the watchdog writes its outcome", || world.upgrade_outcome().is_some());
    assert_eq!(
        world.upgrade_outcome().expect("the outcome")["outcome"],
        "rolled_back_not_listening"
    );
}

fn previous_path(target: &Path) -> PathBuf {
    PathBuf::from(format!("{}.previous", target.display()))
}

/// The host stays awake while a client talks to the agent and while a run goes on: `serve` and the supervisor of the run
/// each start the program of the power assertions, which watches their process and ends with it.
#[test]
fn serve_and_the_supervisor_keep_the_host_awake() {
    let world = World::new();
    let log = world.dir.path().join("caffeinate.log");
    let fake = world.dir.path().join("caffeinate");
    write_script(
        &fake,
        &format!(
            "#!/bin/sh\necho \"$*\" >>'{}'\nwhile kill -0 \"$4\" 2>/dev/null; do sleep 0.05; done\n",
            log.display()
        ),
    );
    let agent = world.serve_with(&[("YONDERD_TEST_CAFFEINATE", &fake.to_string_lossy())]);
    let world = &agent.world;
    git(&world.repo(), &["update-ref", "refs/yonder/30-1", &world.commit]);
    agent.start(world.spec("30-1", HOLDER, "echo ran"));
    let followed = agent.follow("30-1", 0);
    assert_eq!(followed.end, Reply::Exit { code: 0 }, "{}", agent.dump("30-1"));
    let lines = fs::read_to_string(&log).expect("the log of the fake");
    let watched: BTreeSet<&str> = lines
        .lines()
        .map(|line| line.strip_prefix("-i -s -w ").unwrap_or_else(|| panic!("the arguments: {line}")))
        .collect();
    let serve = agent.child.id().to_string();
    assert!(watched.contains(serve.as_str()), "serve: {lines}");
    assert!(watched.iter().any(|pid| *pid != serve), "the supervisor: {lines}");
}
