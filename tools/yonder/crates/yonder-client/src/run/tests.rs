use std::fs;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU32};
use std::sync::{Mutex, PoisonError};

use pretty_assertions::assert_eq;
use yonder_link::proto::{FileKind, RunInfo};
use yonder_link::{Channel, Frame};

use super::*;
use crate::process::{Limit, Processes, Ran};
use crate::testkit::{ALIAS, COMMIT, FakeProcesses, Said, Setup, calm, client, ok};

/// A clean checkout at [`COMMIT`], whose first-parent ancestor is `c1`, whose merge base is `m0`, and whose pack is
/// `PACK` of 7 commits: the answers of the fake bd.
fn checkout() -> FakeProcesses {
    FakeProcesses::default()
}

/// The checkout of [`checkout`], whose calls answer the rules of `first` first.
fn checkout_with(first: &[(&str, Ran)]) -> FakeProcesses {
    FakeProcesses::new(first)
}

/// The answer of `bd host snapshot` of a commit on HEAD [`COMMIT`] in the repository `branch-deck`.
struct Snap<'a> {
    commit: &'a str,
    changed: &'a [&'a str],
    candidates: &'a [&'a str],
    base: Option<&'a str>,
    config: Option<&'a str>,
}

impl Default for Snap<'_> {
    fn default() -> Self {
        Self {
            commit: COMMIT,
            changed: &[],
            candidates: &[COMMIT, "c1", "m0"],
            base: None,
            config: None,
        }
    }
}

impl Snap<'_> {
    fn answer(&self) -> Ran {
        ok(&serde_json::json!({
            "schema": 2,
            "commit": self.commit,
            "head": COMMIT,
            "changed": self.changed,
            "repository": "branch-deck",
            "git_dir": "/u/projects/branch-deck/.git",
            "patch_dir": "@PATCH_DIR@",
            "base": self.base,
            "candidates": self.candidates,
            "blob": self.config.map(|text| serde_json::json!({"path": ".yonder.toml", "id": "b1", "text": text})),
            "operation_id": "o",
        })
        .to_string())
    }
}

/// The answer of a bd that refuses with `git_error`.
fn bd_refused(message: &str) -> Ran {
    Ran {
        code: Some(5),
        stdout: serde_json::json!({"schema": 2, "error": {"code": "git_error", "message": message}})
            .to_string()
            .into_bytes(),
        ..Ran::default()
    }
}

/// The bytes of the last pack that reached the agent of [`agent`].
type Packed = Arc<Mutex<Vec<u8>>>;

/// An agent for a whole run. `script` answers each request first, and answers `true` when it did. Each other request
/// gets the replies of [`answer_besides_follow`], with `have` as the reply of `Have`.
fn agent(have: &'static [&'static str], script: impl Fn(&Request, &mut Channel) -> bool + Send + Sync + 'static) -> (Setup, Packed) {
    let packed = Packed::default();
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| {
            if !script(request, channel) {
                answer_besides_follow(request, channel, &packed, have);
            }
        }
    });
    (setup, packed)
}

/// An agent for a whole run whose `Follow` runs `follow` with the offset of the request.
fn agent_following(have: &'static [&'static str], follow: impl Fn(u64, &mut Channel) + Send + Sync + 'static) -> (Setup, Packed) {
    agent(have, move |request, channel| match request {
        Request::Follow { offset, .. } => {
            follow(*offset, channel);
            true
        }
        _ => false,
    })
}

/// Runs the command of the tests in the checkout of `setup`, with `fetch` and the short deadlines.
fn run_in(
    setup: &Setup,
    processes: &dyn Processes,
    say: &(dyn Fn(&str) + Sync),
    presses: &(dyn Fn() -> u32 + Sync),
    fetch: &[String],
) -> Result<RunOutcome, Refusal> {
    let (command, checkout) = (command(), setup.checkout());
    client(setup, processes, say, presses).run(ALIAS, &request(&checkout, &command, fetch))
}

/// The kinds of the requests that reached the agent, in their order.
fn kinds(setup: &Setup) -> Vec<&'static str> {
    setup.agent.requests().iter().map(kind).collect()
}

/// The candidates of each `Have` that reached the agent.
fn haves(setup: &Setup) -> Vec<Vec<String>> {
    setup
        .agent
        .requests()
        .into_iter()
        .filter_map(|request| match request {
            Request::Have { commits, .. } => Some(commits),
            _ => None,
        })
        .collect()
}

/// The commit and the `--have` commits of each `bd host pack`.
fn pack_inputs(processes: &FakeProcesses) -> Vec<Vec<String>> {
    processes.packs()
}

fn strings(items: &[&str]) -> Vec<String> {
    items.iter().map(|item| (*item).to_owned()).collect()
}

/// Waits until `flag` is set, up to 5 s, as a command of the host that waits for an event.
fn wait_for(flag: &AtomicBool) {
    let started = Instant::now();
    while !flag.load(Ordering::SeqCst) && started.elapsed() < Duration::from_secs(5) {
        thread::sleep(Duration::from_millis(20));
    }
}

fn exit(channel: &mut Channel, code: i32) {
    let _ = channel.send(&Reply::Exit { code });
}

fn done(channel: &mut Channel, message: &str) {
    let _ = channel.send(&Reply::Done {
        message: message.to_owned(),
    });
}

fn step(name: &str, ms: u64) -> Phase {
    Phase { name: name.to_owned(), ms }
}

fn command() -> Vec<String> {
    ["./community/tools/bt.cmd", "FooTest", "--json"].map(str::to_owned).to_vec()
}

fn fetch() -> Vec<String> {
    vec!["out/bt-runs".to_owned()]
}

fn request<'a>(checkout: &'a Path, command: &'a [String], fetch: &'a [String]) -> RunRequest<'a> {
    RunRequest {
        checkout,
        repo: None,
        holder: "bt-remote-0123456789ab",
        command,
        fetch,
    }
}

fn kind(request: &Request) -> &'static str {
    match request {
        Request::Hello { .. } => "hello",
        Request::Have { .. } => "have",
        Request::Pack { .. } => "pack",
        Request::Start(_) => "start",
        Request::Follow { .. } => "follow",
        Request::Cancel { .. } => "cancel",
        Request::Fetch { .. } => "fetch",
        Request::Forget { .. } => "forget",
        Request::Runs => "runs",
        Request::Upgrade { .. } => "upgrade",
    }
}

/// Raises the Ctrl+C count to `presses` once the agent got a request of the kind `after`, as a person who presses
/// after the start. The thread gives up after 5 s.
fn press_after(setup: &Setup, after: &'static str, presses: u32) -> (Arc<AtomicU32>, thread::JoinHandle<()>) {
    let count = Arc::new(AtomicU32::new(0));
    let (pressed, agent) = (Arc::clone(&count), setup.agent.requests_handle());
    let watcher = thread::spawn(move || {
        let started = Instant::now();
        while started.elapsed() < Duration::from_secs(5) {
            if agent
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .iter()
                .any(|request| kind(request) == after)
            {
                pressed.store(presses, Ordering::SeqCst);
                return;
            }
            thread::sleep(Duration::from_millis(20));
        }
    });
    (count, watcher)
}

fn output(channel: &mut Channel, stream: Stream, text: &str, end: u64) {
    let _ = channel.send(&Reply::Output {
        stream,
        text: text.to_owned(),
        end,
    });
}

fn send_file(channel: &mut Channel, path: &str, bytes: &[u8]) {
    send_kind(channel, path, bytes, FileKind::Staged);
}

fn send_kind(channel: &mut Channel, path: &str, bytes: &[u8], kind: FileKind) {
    let _ = channel.send(&Reply::File {
        path: path.to_owned(),
        size: bytes.len() as u64,
        executable: false,
        kind,
    });
    let _ = channel.send_bytes(bytes);
    let _ = channel.send_end();
}

/// The replies of an agent for a whole run, besides `Follow`.
fn answer_besides_follow(request: &Request, channel: &mut Channel, packed: &Mutex<Vec<u8>>, have: &[&str]) {
    let _ = match request {
        Request::Have { .. } => channel.send(&Reply::Have {
            commits: have.iter().map(|commit| (*commit).to_owned()).collect(),
        }),
        Request::Pack { .. } => {
            let mut bytes = Vec::new();
            let _ = channel.recv_stream(&mut bytes);
            *packed.lock().unwrap_or_else(PoisonError::into_inner) = bytes;
            channel.send(&Reply::Packed)
        }
        Request::Start(spec) => channel.send(&Reply::Started { run: spec.run.clone() }),
        Request::Fetch { .. } => {
            send_file(channel, "out/bt-runs/0000000002000/test.log", b"hello");
            channel.send(&Reply::Fetched { files: 1, bytes: 5 })
        }
        Request::Forget { .. } | Request::Cancel { .. } => channel.send(&Reply::Done {
            message: "removed".to_owned(),
        }),
        _ => channel.send(&Reply::refused("bad_request", "not in this script")),
    };
}

#[test]
fn a_run_packs_the_missing_objects_streams_the_stderr_and_fetches_the_staged_files() {
    let (setup, packed) = agent_following(&["c1"], |_, channel| {
        output(channel, Stream::Stderr, "progress", 30);
        let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 15_000 });
        output(channel, Stream::Stdout, "the digest", 60);
        let _ = channel.send(&Reply::Phases {
            phases: vec![step("lock", 0), step("command", 25_100)],
        });
        exit(channel, 3);
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let outcome = run_in(&setup, &processes, &say, &calm, &fetch()).expect("the run");

    assert_eq!(outcome.code, 3);
    assert_eq!(outcome.stdout, "the digest\n");
    assert_eq!(outcome.commit, COMMIT);
    assert_eq!(outcome.fetched, ["out/bt-runs/0000000002000/test.log"]);
    assert_eq!(
        fs::read_to_string(setup.checkout().join("out/bt-runs/0000000002000/test.log")).expect("the fetched file"),
        "hello"
    );

    // The start comes before the pack, so the host takes the lease while the pack travels, and the follow goes on
    // beside the pack.
    let requests = setup.agent.requests();
    let mut kinds = kinds(&setup);
    assert_eq!(kinds[..2], ["have", "start"]);
    kinds.sort_unstable();
    assert_eq!(kinds, ["fetch", "follow", "forget", "have", "pack", "start"]);
    let Request::Have { repo, commits } = &requests[0] else {
        panic!("{requests:?}")
    };
    assert_eq!(
        (repo.as_str(), commits.clone()),
        ("~/projects/idea-1", vec![COMMIT.to_owned(), "c1".to_owned(), "m0".to_owned()])
    );
    let Request::Start(spec) = &requests[1] else {
        panic!("{requests:?}")
    };
    assert_eq!(spec.reference, format!("refs/yonder/{}", outcome.run));
    assert_eq!(spec.holder, "bt-remote-0123456789ab");
    assert_eq!((spec.command.clone(), spec.fetch.clone()), (command(), fetch()));
    assert!(yonder_link::proto::is_run_id(&outcome.run), "{}", outcome.run);

    assert_eq!(*packed.lock().unwrap_or_else(PoisonError::into_inner), b"PACK");
    assert_eq!(pack_inputs(&processes), [strings(&[COMMIT, "c1"])]);
    let pack = processes
        .calls()
        .into_iter()
        .find(|call| call.text().contains(" host pack "))
        .expect("the pack");
    assert_eq!(
        pack.text(),
        format!("bd -C {} host pack {COMMIT} --have c1", setup.checkout().display())
    );
    assert!(
        matches!(pack.limit, Limit::Quiet(_)),
        "bd writes the progress of git, which the quiet limit watches"
    );
    assert!(
        processes.calls().iter().all(|call| call.program == "bd"),
        "every local call goes to bd"
    );

    let lines = said.lines();
    assert!(lines.contains(&"progress".to_owned()), "{lines:#?}");
    for line in [
        "> studio: pack the commits that studio lacks",
        "> studio: pack of 7 commits, 4 B ready",
        "> studio: sent 4 B",
    ] {
        assert!(lines.contains(&line.to_owned()), "{line} in {lines:#?}");
    }
    assert_eq!(
        outcome.phases.iter().map(|phase| phase.name.as_str()).collect::<Vec<_>>(),
        ["pack", "upload", "lock", "command", "fetch", "total"],
        "the steps of the client wrap the steps of the host"
    );
    assert_eq!(outcome.phases[3].ms, 25_100);
}

#[test]
fn a_host_that_has_the_commit_gets_no_pack() {
    let (setup, _) = agent_following(&[COMMIT], |_, channel| exit(channel, 0));
    let processes = checkout();
    let outcome = run_in(&setup, &processes, &|_| {}, &calm, &[]).expect("the run");
    assert_eq!(outcome.code, 0);
    assert_eq!(kinds(&setup), ["have", "start", "follow", "forget"]);
    assert_eq!(pack_inputs(&processes), Vec::<String>::new());
}

/// A lost connection follows again from the last offset, and a record that came twice shows once.
#[test]
fn a_lost_connection_follows_again_from_the_last_offset() {
    let (setup, _) = agent_following(&[COMMIT], |offset, channel| {
        output(channel, Stream::Stderr, "first", 10);
        if offset > 0 {
            output(channel, Stream::Stdout, "done", 20);
            exit(channel, 0);
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let outcome = run_in(&setup, &processes, &say, &calm, &[]).expect("the run");
    assert_eq!(outcome.stdout, "done\n");
    let lines = said.lines();
    assert_eq!(lines.iter().filter(|line| *line == "first").count(), 1, "{lines:#?}");
    assert!(
        lines
            .iter()
            .any(|line| line.contains(&format!("following the run {} again from byte 10", outcome.run))),
        "{lines:#?}"
    );
    let follows: Vec<u64> = setup
        .agent
        .requests()
        .iter()
        .filter_map(|request| match request {
            Request::Follow { offset, .. } => Some(*offset),
            _ => None,
        })
        .collect();
    assert_eq!(follows, [0, 10]);
}

/// An agent that stops answering ends the run after the reconnect window, and the refusal says how to follow again.
#[test]
fn a_silent_agent_times_out_after_the_reconnect_window() {
    let (setup, _) = agent_following(&[COMMIT], |_, _| thread::sleep(Duration::from_secs(4)));
    let refusal = run_in(&setup, &checkout(), &|_| {}, &calm, &[]).expect_err("no reply");
    assert_eq!(refusal.code, code::TIMED_OUT);
    assert!(refusal.message.contains("`yonder attach studio "), "{}", refusal.message);
    assert!(
        !kinds(&setup).contains(&"forget"),
        "a run whose end the client did not see stays on the host"
    );
}

/// An agent whose `Follow` waits for the cancel of the run, up to 5 s, then ends the run with 130 and the line `said`.
/// The answer is the flag of the cancel.
fn agent_that_waits_for_the_cancel(have: &'static [&'static str], said: &'static str) -> (Setup, Arc<AtomicBool>) {
    let cancelled = Arc::new(AtomicBool::new(false));
    let (setup, _) = agent(have, {
        let cancelled = Arc::clone(&cancelled);
        move |request, channel| match request {
            Request::Follow { .. } => {
                wait_for(&cancelled);
                output(channel, Stream::Stderr, said, 20);
                exit(channel, 130);
                true
            }
            Request::Cancel { .. } => {
                cancelled.store(true, Ordering::SeqCst);
                done(channel, "the supervisor has the cancel request");
                true
            }
            _ => false,
        }
    });
    (setup, cancelled)
}

/// Ctrl+C sends `Cancel` on its own connection, and the output follows up to the exit.
#[test]
fn ctrl_c_cancels_the_run_and_follows_it_to_its_end() {
    let (setup, cancelled) = agent_that_waits_for_the_cancel(&[COMMIT], "interrupted");
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (count, watcher) = press_after(&setup, "follow", 1);
    let presses = || count.load(Ordering::SeqCst);
    let outcome = run_in(&setup, &processes, &say, &presses, &[]).expect("the run");
    watcher.join().expect("the watcher");
    assert_eq!(outcome.code, 130);
    assert!(cancelled.load(Ordering::SeqCst));
    let lines = said.lines();
    assert!(
        lines.iter().any(|line| line.starts_with("yonder: Ctrl+C: cancelling the run")),
        "{lines:#?}"
    );
    // The run can end before the reply of the cancel comes, and the client then waits only a moment for it.
    assert!(lines.contains(&"interrupted".to_owned()), "{lines:#?}");
}

/// A refusal of the agent keeps its code. A run that failed on the host is removed there all the same. No outcome
/// carries the steps of the host, so the client says them itself.
#[test]
fn a_refusal_of_the_agent_keeps_its_code() {
    let (setup, _) = agent_following(&[COMMIT], |_, channel| {
        let _ = channel.send(&Reply::Phases {
            phases: vec![step("lock", 0), step("lease", 3_909_000)],
        });
        let _ = channel.send(&Reply::refused("lease_failed", "bd lease get failed: no free slot"));
    });
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let refusal = run_in(&setup, &checkout(), &say, &calm, &[]).expect_err("the lease failed");
    assert_eq!(refusal.code, "lease_failed");
    assert_eq!(refusal.message, "studio: bd lease get failed: no free slot");
    assert_eq!(kinds(&setup).last(), Some(&"forget"));
    let lines = said.lines();
    assert!(
        lines.contains(&"yonder: phases: lock 0.0s, lease 1h05m09s".to_owned()),
        "{lines:#?}"
    );
}

/// `Reply::Phases` belongs to `Follow` only. Anywhere else it is a reply out of turn.
#[test]
fn the_steps_of_a_run_outside_a_follow_are_out_of_turn() {
    let setup = Setup::new(|_, channel| {
        let _ = channel.send(&Reply::Phases { phases: Vec::new() });
    });
    let processes = FakeProcesses::default();
    let refusal = client(&setup, &processes, &|_| {}, &calm).runs(ALIAS).expect_err("out of turn");
    assert_eq!(refusal.code, code::LINK);
}

const BASE: &str = "fedcba9876543210fedcba9876543210fedcba98";

/// A clean checkout at [`COMMIT`] whose `origin/master` is [`BASE`].
fn checkout_with_base() -> FakeProcesses {
    let snapshot = Snap {
        base: Some(BASE),
        ..Snap::default()
    };
    checkout_with(&[(" host snapshot", snapshot.answer())])
}

/// The packs that reached the agent, as their references.
fn pack_refs(setup: &Setup) -> Vec<String> {
    setup
        .agent
        .requests()
        .into_iter()
        .filter_map(|request| match request {
            Request::Pack { reference, .. } => Some(reference),
            _ => None,
        })
        .collect()
}

/// An agent whose `Have` answers `c1`, and whose `Pack` to a base ref runs `base` once the stream came. `base` answers
/// the reply. A run pack gets `Packed`.
fn agent_of_bases(
    have: impl Fn() -> Vec<String> + Send + Sync + 'static,
    follow: impl Fn(&mut Channel) + Send + Sync + 'static,
    base: impl Fn(&mut Channel) -> Reply + Send + Sync + 'static,
) -> Setup {
    agent(&[], move |request, channel| {
        let _ = match request {
            Request::Have { .. } => channel.send(&Reply::Have { commits: have() }),
            Request::Pack { reference, .. } => {
                let _ = channel.recv_stream(&mut Vec::new());
                let reply = if reference.starts_with(yonder_link::proto::BASE_REFS) {
                    base(channel)
                } else {
                    Reply::Packed
                };
                channel.send(&reply)
            }
            Request::Follow { .. } => {
                follow(channel);
                Ok(())
            }
            _ => return false,
        };
        true
    })
    .0
}

/// While the command goes on, the base goes once to `refs/yonder/base/<holder>`, after the run pack. The next run names
/// the base in its `Have`, so its pack leaves out the base, and its sync skips the base that the host has.
#[test]
fn the_base_goes_once_and_the_next_run_builds_on_it() {
    let based = Arc::new(AtomicBool::new(false));
    let setup = agent_of_bases(
        {
            let based = Arc::clone(&based);
            move || {
                let mut commits = vec!["c1".to_owned()];
                if based.load(Ordering::SeqCst) {
                    commits.push(BASE.to_owned());
                }
                commits
            }
        },
        // A long command: it ends after the base came, or after 5 s.
        {
            let based = Arc::clone(&based);
            move |channel| {
                wait_for(&based);
                exit(channel, 0);
            }
        },
        {
            let based = Arc::clone(&based);
            move |_| {
                based.store(true, Ordering::SeqCst);
                Reply::Packed
            }
        },
    );
    let processes = checkout_with_base();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let holder = "bt-remote-0123456789ab";
    run_in(&setup, &processes, &say, &calm, &[]).expect("the first run");
    let first = pack_refs(&setup);
    assert_eq!(first.len(), 2, "{first:?}");
    assert!(first[0].starts_with("refs/yonder/") && !first[0].contains("/base/"), "{first:?}");
    assert_eq!(first[1], format!("refs/yonder/base/{holder}"));
    assert_eq!(setup.config.base(ALIAS, holder).as_deref(), Some(BASE));

    run_in(&setup, &processes, &say, &calm, &[]).expect("the second run");
    assert_eq!(pack_refs(&setup).len(), 3, "the second run sends its own pack only");
    let haves = haves(&setup);
    assert_eq!(haves.len(), 4, "{haves:#?}");
    assert!(
        haves[2].contains(&BASE.to_owned()),
        "the second run asks about the base: {haves:#?}"
    );
    let packs = pack_inputs(&processes);
    assert_eq!(packs.len(), 3, "{packs:#?}");
    assert_eq!(packs[2], strings(&[COMMIT, "c1", BASE]));
    let lines = said.lines();
    assert!(!lines.iter().any(|line| line.contains("warning")), "{lines:#?}");
}

/// An agent that refuses the base ref, as an agent before schema 2 does, gives one warning line, and the run ends with
/// the code of its command.
#[test]
fn a_failed_base_sync_only_warns() {
    let setup = agent_of_bases(
        || vec!["c1".to_owned()],
        |channel| {
            thread::sleep(Duration::from_millis(300));
            exit(channel, 4);
        },
        |_| Reply::refused("bad_request", "a pack ref that is not refs/yonder/<run id>"),
    );
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let outcome = run_in(&setup, &checkout_with_base(), &say, &calm, &[]).expect("the run");
    assert_eq!(outcome.code, 4);
    let warnings: Vec<String> = said.lines().into_iter().filter(|line| line.contains("warning")).collect();
    assert_eq!(warnings.len(), 1, "{warnings:#?}");
    assert!(
        warnings[0].starts_with("yonder: warning: cannot sync the base of the next packs to studio (bad_request): "),
        "{}",
        warnings[0]
    );
    assert_eq!(setup.config.base(ALIAS, "bt-remote-0123456789ab"), None);
}

/// Ctrl+C after the end of the run closes the connection of the base sync, also while the host indexes the base.
#[test]
fn ctrl_c_stops_the_base_sync_while_the_host_indexes_it() {
    let indexing = Arc::new(AtomicBool::new(false));
    let setup = agent_of_bases(|| vec!["c1".to_owned()], |channel| exit(channel, 0), {
        let indexing = Arc::clone(&indexing);
        move |channel| {
            indexing.store(true, Ordering::SeqCst);
            for _ in 0..50 {
                thread::sleep(Duration::from_millis(200));
                if channel.send(&Reply::Heartbeat { elapsed_ms: 200 }).is_err() {
                    break;
                }
            }
            Reply::Packed
        }
    });
    let presses = || u32::from(indexing.load(Ordering::SeqCst));
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let started = Instant::now();
    let outcome = run_in(&setup, &checkout_with_base(), &say, &presses, &[]).expect("the run");
    assert_eq!(outcome.code, 0);
    assert!(
        started.elapsed() < Duration::from_secs(5),
        "the sync held the end: {:?}",
        started.elapsed()
    );
    let lines = said.lines();
    assert!(!lines.iter().any(|line| line.contains("warning")), "{lines:#?}");
}

/// A checkout with local changes starts no run. Its refusal closes the `Have` that goes on meanwhile, so the refusal
/// comes at once even from a host that does not answer.
#[test]
fn local_changes_start_no_run_and_refuse_at_once() {
    let setup = Setup::new(|_, _| thread::sleep(Duration::from_secs(5)));
    let dirty = Snap {
        changed: &[" M a.txt"],
        ..Snap::default()
    };
    let processes = checkout_with(&[("snapshot --farthest 1", dirty.answer())]);
    let started = Instant::now();
    let refusal = run_in(&setup, &processes, &|_| {}, &calm, &[]).expect_err("local changes");
    assert_eq!(refusal.code, code::LOCAL_CHANGES);
    assert!(started.elapsed() < Duration::from_secs(1), "{:?}", started.elapsed());
    assert!(kinds(&setup).iter().all(|kind| *kind == "have"), "{:?}", kinds(&setup));
}

#[test]
fn runs_and_cancel_answer_the_replies_of_the_agent() {
    let setup = Setup::new(|request, channel| {
        let _ = match request {
            Request::Runs => channel.send(&Reply::Runs { runs: Vec::new() }),
            _ => channel.send(&Reply::refused("bad_request", "no")),
        };
    });
    let processes = FakeProcesses::default();
    let client = client(&setup, &processes, &|_| {}, &calm);
    assert_eq!(client.runs(ALIAS).expect("the runs"), Vec::<RunInfo>::new());
    assert_eq!(client.cancel(ALIAS, "r1").expect_err("refused").code, "bad_request");
}

/// A request that waits gets heartbeats before its reply, and each heartbeat starts the read deadline again. The wait
/// here is twice the deadline of the short timing.
#[test]
fn heartbeats_keep_a_waiting_request_alive() {
    let setup = Setup::new(|request, channel| {
        if matches!(request, Request::Runs) {
            for _ in 0..4 {
                thread::sleep(Duration::from_millis(1000));
                let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 1000 });
            }
            let _ = channel.send(&Reply::Runs { runs: Vec::new() });
        }
    });
    let processes = FakeProcesses::default();
    let runs = client(&setup, &processes, &|_| {}, &calm).runs(ALIAS).expect("the runs");
    assert_eq!(runs, Vec::<RunInfo>::new());
}

/// A host without any of the candidate commits would need the whole history. The client refuses and builds no pack.
#[test]
fn a_host_without_any_candidate_is_behind_and_gets_no_pack() {
    let (setup, _) = agent(&[], |_, _| false);
    let processes = checkout();
    let refusal = run_in(&setup, &processes, &|_| {}, &calm, &[]).expect_err("behind");
    assert_eq!(refusal.code, code::BEHIND);
    assert!(
        refusal.message.contains("the pack would hold the whole history"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("`bt --host studio --prepare`"), "{}", refusal.message);
    assert_eq!(kinds(&setup), ["have", "have"], "the near candidates, then the long walk");
    assert_eq!(pack_inputs(&processes), Vec::<String>::new());
}

/// A host that has none of the near candidates gets a second `Have` with the long first-parent walk, and the pack
/// builds on the commit that it has from there.
#[test]
fn a_host_without_a_near_candidate_gets_the_long_walk() {
    let (setup, _) = agent(&[], |request, channel| match request {
        Request::Have { commits, .. } => {
            let has: Vec<String> = commits.iter().filter(|commit| *commit == "far").cloned().collect();
            let _ = channel.send(&Reply::Have { commits: has });
            true
        }
        Request::Follow { .. } => {
            exit(channel, 0);
            true
        }
        _ => false,
    });
    let far = Snap {
        candidates: &[COMMIT, "c1", "far", "m0"],
        ..Snap::default()
    };
    let processes = checkout_with(&[("--farthest 65536", far.answer())]);
    let outcome = run_in(&setup, &processes, &|_| {}, &calm, &[]).expect("the run");
    assert_eq!(outcome.code, 0);
    assert_eq!(
        haves(&setup),
        [
            vec![COMMIT.to_owned(), "c1".to_owned(), "m0".to_owned()],
            vec![COMMIT.to_owned(), "c1".to_owned(), "far".to_owned(), "m0".to_owned()]
        ]
    );
    assert_eq!(pack_inputs(&processes), [strings(&[COMMIT, "far"])]);
}

/// Ctrl+C after `Have` stops the pack, and nothing more reaches the host.
#[test]
fn ctrl_c_before_the_start_stops_the_pack_and_reaches_no_host() {
    let pressed = Arc::new(AtomicBool::new(false));
    let (setup, _) = agent(&["c1"], {
        let pressed = Arc::clone(&pressed);
        move |request, _| {
            if let Request::Have { .. } = request {
                pressed.store(true, Ordering::SeqCst);
            }
            false
        }
    });
    let presses = || u32::from(pressed.load(Ordering::SeqCst));
    let refusal = run_in(&setup, &checkout(), &|_| {}, &presses, &[]).expect_err("interrupted");
    assert_eq!(refusal.code, code::INTERRUPTED);
    assert_eq!(kinds(&setup), ["have"]);
}

/// A pack that the agent reads late: each write waits up to the read deadline, not the short connect deadline.
#[test]
fn a_slow_agent_reader_takes_a_large_pack() {
    let (setup, packed) = agent(&["c1"], |request, channel| match request {
        Request::Pack { .. } => {
            thread::sleep(Duration::from_millis(2500));
            false
        }
        Request::Follow { .. } => {
            exit(channel, 0);
            true
        }
        _ => false,
    });
    // Larger than the socket buffers of the loopback, so the writes wait for the reader.
    let pack = "x".repeat(4 * 1024 * 1024);
    let processes = checkout().with_pack(pack.clone().into_bytes());
    let (command, checkout_root) = (command(), setup.checkout());
    let timing = Timing {
        connect: Duration::from_secs(1),
        read: Duration::from_secs(5),
        ..crate::testkit::FAST
    };
    let outcome = Client::new(setup.config, &processes, &|_| {}, &calm)
        .with_timing(timing)
        .run(ALIAS, &request(&checkout_root, &command, &[]))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    assert_eq!(packed.lock().unwrap_or_else(PoisonError::into_inner).len(), pack.len());
}

/// An agent that drops the connection in the middle of the pack is a lost link, not a git failure. The run started
/// already, so the client cancels it, and nothing follows it.
#[test]
fn a_link_lost_during_the_pack_cancels_the_started_run_with_a_link_refusal() {
    let (setup, _) = agent(&["c1"], |request, channel| match request {
        // One chunk, then the connection closes.
        Request::Pack { .. } => {
            let _ = channel.recv_frame();
            true
        }
        Request::Cancel { .. } => {
            done(channel, "the host has the cancel");
            true
        }
        Request::Follow { .. } => true,
        _ => false,
    });
    let processes = checkout().with_pack("y".repeat(4 * 1024 * 1024).into_bytes());
    let refusal = run_in(&setup, &processes, &|_| {}, &calm, &[]).expect_err("the link broke");
    assert!(matches!(refusal.code.as_ref(), code::LINK | code::TIMED_OUT), "{refusal:?}");
    assert!(
        refusal.message.starts_with("the pack did not reach studio: "),
        "{}",
        refusal.message
    );
    assert!(
        refusal.message.contains("; studio has the cancel of the run "),
        "{}",
        refusal.message
    );
    // The follow starts with the run, beside the pack, so its requests come in any order.
    let kinds: Vec<&str> = kinds(&setup).into_iter().filter(|kind| *kind != "follow").collect();
    assert_eq!(kinds, ["have", "start", "pack", "cancel"]);
}

/// A cancel whose first connection fails connects again, and the run still ends with its own exit.
#[test]
fn a_failed_cancel_connects_again() {
    let cancels = Arc::new(AtomicU32::new(0));
    let (setup, _) = agent(&[COMMIT], {
        let cancels = Arc::clone(&cancels);
        move |request, channel| match request {
            Request::Follow { .. } => {
                let started = Instant::now();
                while cancels.load(Ordering::SeqCst) < 2 && started.elapsed() < Duration::from_secs(5) {
                    thread::sleep(Duration::from_millis(200));
                    let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 200 });
                }
                exit(channel, 130);
                true
            }
            // The first cancel gets no reply: the connection closes.
            Request::Cancel { .. } => {
                if cancels.fetch_add(1, Ordering::SeqCst) > 0 {
                    done(channel, "the host has the cancel");
                }
                true
            }
            _ => false,
        }
    });
    let said = Said::default();
    let say = |line: &str| said.push(line);
    // Ctrl+C before the start would refuse the run, so the presses count from the first Follow only.
    let (count, watcher) = press_after(&setup, "follow", 1);
    let counted = || count.load(Ordering::SeqCst);
    let outcome = run_in(&setup, &checkout(), &say, &counted, &[]).expect("the run");
    watcher.join().expect("the watcher");
    assert_eq!(outcome.code, 130);
    assert_eq!(cancels.load(Ordering::SeqCst), 2);
    let lines = said.lines();
    assert!(
        lines
            .iter()
            .any(|line| line.starts_with("yonder: cannot send the cancel of the run")),
        "{lines:#?}"
    );
    assert!(lines.contains(&"yonder: the host has the cancel".to_owned()), "{lines:#?}");
}

/// A second Ctrl+C stops the follow at once, while the agent only sends heartbeats. The run stays on the host.
#[test]
fn a_second_ctrl_c_stops_the_follow_at_once() {
    let (setup, _) = agent_following(&[COMMIT], |_, channel| {
        for _ in 0..50 {
            thread::sleep(Duration::from_millis(200));
            if channel.send(&Reply::Heartbeat { elapsed_ms: 200 }).is_err() {
                return;
            }
        }
    });
    let (count, watcher) = press_after(&setup, "follow", 2);
    let presses = || count.load(Ordering::SeqCst);
    let started = Instant::now();
    let refusal = run_in(&setup, &checkout(), &|_| {}, &presses, &[]).expect_err("stopped");
    watcher.join().expect("the watcher");
    assert_eq!(refusal.code, code::INTERRUPTED);
    assert!(started.elapsed() < Duration::from_secs(4), "{:?}", started.elapsed());
    assert!(refusal.message.contains("`yonder attach studio "), "{}", refusal.message);
    assert!(!kinds(&setup).contains(&"forget"));
}

/// A fetch that the connection cuts leaves no part file and no forget, and the client names `yonder fetch`.
#[test]
fn a_cut_fetch_leaves_no_part_and_keeps_the_run() {
    let (setup, _) = agent(&[COMMIT], |request, channel| match request {
        Request::Follow { .. } => {
            exit(channel, 3);
            true
        }
        Request::Fetch { .. } => {
            let _ = channel.send(&Reply::File {
                path: "out/bt-runs/1/big.log".to_owned(),
                size: 10,
                executable: false,
                kind: FileKind::Staged,
            });
            let _ = channel.send_bytes(b"four");
            true
        }
        _ => false,
    });
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let outcome = run_in(&setup, &checkout(), &say, &calm, &fetch()).expect("the run");
    assert_eq!(outcome.code, 3);
    assert_eq!(outcome.fetched, Vec::<String>::new());
    let checkout_root = setup.checkout();
    assert!(!checkout_root.join("out/bt-runs/1/big.log").exists());
    assert!(!checkout_root.join("out/bt-runs/1/big.log.yonder-part").exists());
    assert!(!kinds(&setup).contains(&"forget"));
    let lines = said.lines();
    assert!(
        lines.iter().any(|line| line.contains("`yonder fetch studio ")
            && line.contains("` in this checkout copies its files again")
            && line.contains("`yonder forget studio ")),
        "{lines:#?}"
    );
}

/// A start whose reply does not come can leave a run on the agent, which would wait for its pack. The client cancels it.
#[test]
fn a_start_without_its_reply_is_cancelled() {
    let (setup, _) = agent(&[COMMIT], |request, _| matches!(request, Request::Start(_)));
    let refusal = run_in(&setup, &checkout(), &|_| {}, &calm, &[]).expect_err("no reply");
    assert!(matches!(refusal.code.as_ref(), code::LINK | code::TIMED_OUT), "{refusal:?}");
    assert!(refusal.message.starts_with("the start of the run "), "{}", refusal.message);
    assert!(
        refusal.message.contains("; studio has the cancel of the run "),
        "{}",
        refusal.message
    );
    assert_eq!(kinds(&setup), ["have", "start", "cancel"]);
}

/// The host takes the lock and the lease while git builds the pack, and empty chunks keep the connection of the pack
/// alive meanwhile. The follow starts with the run, so the lines of the host show during the build.
#[test]
fn the_pack_builds_after_the_start_and_keeps_its_connection_alive() {
    let keepalives = Arc::new(AtomicU32::new(0));
    let (setup, _) = agent(&["c1"], {
        let keepalives = Arc::clone(&keepalives);
        move |request, channel| match request {
            Request::Pack { .. } => {
                let mut bytes = Vec::new();
                while let Ok(Frame::Bytes(chunk)) = channel.recv_frame() {
                    if chunk.is_empty() {
                        keepalives.fetch_add(1, Ordering::SeqCst);
                    }
                    bytes.extend(chunk);
                }
                assert_eq!(bytes, b"PACK");
                let _ = channel.send(&Reply::Packed);
                true
            }
            Request::Follow { .. } => {
                output(channel, Stream::Stderr, "yonder: lease /pool/1 (warm)", 30);
                exit(channel, 0);
                true
            }
            _ => false,
        }
    });
    let processes = checkout().with_pack_time(Duration::from_millis(400));
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let outcome = run_in(&setup, &processes, &say, &calm, &[]).expect("the run");
    assert_eq!(outcome.code, 0);
    let sent = keepalives.load(Ordering::SeqCst);
    assert!(sent >= 3, "{sent} empty chunks");
    let lines = said.lines();
    let at = |text: &str| {
        lines
            .iter()
            .position(|line| line == text)
            .unwrap_or_else(|| panic!("{text} in {lines:#?}"))
    };
    assert!(
        at("yonder: lease /pool/1 (warm)") < at("> studio: pack of 7 commits, 4 B ready"),
        "{lines:#?}"
    );
    assert_eq!(
        outcome.phases.iter().map(|phase| phase.name.as_str()).collect::<Vec<_>>(),
        ["pack", "upload", "total"]
    );
}

/// Ctrl+C while git builds the pack cancels the run on the host, and the output follows up to its end.
#[test]
fn ctrl_c_during_the_build_of_the_pack_cancels_the_run_on_the_host() {
    let (setup, cancelled) = agent_that_waits_for_the_cancel(&["c1"], "yonder: the run was cancelled before its command");
    let processes = checkout().with_pack_time(Duration::from_secs(5));
    let (count, watcher) = press_after(&setup, "pack", 1);
    let presses = || count.load(Ordering::SeqCst);
    let started = Instant::now();
    let outcome = run_in(&setup, &processes, &|_| {}, &presses, &[]).expect("the run ends with its own code");
    watcher.join().expect("the watcher");
    assert_eq!(outcome.code, 130);
    assert!(cancelled.load(Ordering::SeqCst));
    assert!(
        started.elapsed() < Duration::from_secs(4),
        "the build was not stopped: {:?}",
        started.elapsed()
    );
}

/// A checkout with a tracked change and an untracked file on [`COMMIT`], whose commit of the working tree is `SNAP`. Its
/// main checkout is `~/projects/branch-deck` here.
fn dirty_checkout() -> FakeProcesses {
    let snapshot = Snap {
        commit: SNAP,
        changed: &[" M a.txt", "?? b.txt"],
        candidates: &[COMMIT, "m0"],
        base: Some(COMMIT),
        ..Snap::default()
    };
    checkout_with(&[("--working-tree", snapshot.answer())])
}

const SNAP: &str = "5555555555555555555555555555555555555555";

/// The options of the yonder CLI: the working tree, niceness 10, the outputs, the repository by its name.
const CLI: RunOptions = RunOptions {
    working_tree: true,
    nice: Some(10),
    outputs: true,
    apply: false,
    repo_by_name: true,
};

/// Runs the command of the tests with `options` in the checkout `checkout`, without fetch directories.
fn run_options(
    setup: &Setup,
    processes: &dyn Processes,
    said: &Said,
    checkout: &Path,
    options: &RunOptions,
) -> Result<RunOutcome, Refusal> {
    let say = |line: &str| said.push(line);
    let command = command();
    client(setup, processes, &say, &calm).run_with(ALIAS, &request(checkout, &command, &[]), options)
}

/// Whether a line that `said` collected starts with `prefix`.
fn said_starts(said: &Said, prefix: &str) -> bool {
    said.lines().iter().any(|line| line.starts_with(prefix))
}

/// The start of the run that reached the agent of `setup`.
fn start_spec(setup: &Setup) -> RunSpec {
    setup
        .agent
        .requests()
        .into_iter()
        .find_map(|request| match request {
            Request::Start(spec) => Some(spec),
            _ => None,
        })
        .expect("a start")
}

/// The processes of [`dirty_checkout`] for the checkout of `setup`.
fn dirty_setup(_setup: &Setup) -> FakeProcesses {
    dirty_checkout()
}

#[test]
fn a_run_of_the_working_tree_sends_its_commit_and_options_and_falls_back_to_the_repository_of_the_host_file() {
    let (setup, _) = agent(&[COMMIT], |request, channel| match request {
        Request::Have { repo, .. } if repo == "~/projects/branch-deck" => {
            let _ = channel.send(&Reply::refused("repo_missing", "~/projects/branch-deck is not a git repository"));
            true
        }
        Request::Follow { .. } => {
            output(channel, Stream::Stdout, "line 1", 10);
            output(channel, Stream::Stdout, "line 2", 20);
            exit(channel, 0);
            true
        }
        _ => false,
    });
    let processes = dirty_setup(&setup);
    let said = Said::default();
    let streamed = Said::default();
    let stdout = |line: &str| streamed.push(line);
    let say = |line: &str| said.push(line);
    let (command, checkout, fetch) = (command(), setup.checkout(), fetch());
    let outcome = client(&setup, &processes, &say, &calm)
        .with_stdout(&stdout)
        .run_with(ALIAS, &request(&checkout, &command, &fetch), &CLI)
        .expect("the run");
    assert_eq!(outcome.commit, SNAP);
    assert_eq!(outcome.stdout, "", "the lines went to the sink");
    assert_eq!(streamed.lines(), ["line 1", "line 2"]);

    let mut kinds = kinds(&setup);
    assert_eq!(kinds[..3], ["have", "have", "start"]);
    kinds.sort_unstable();
    // The third `Have` is the base sync, in the repository that took the run.
    assert_eq!(
        kinds,
        ["fetch", "follow", "forget", "have", "have", "have", "pack", "start"],
        "the outputs need a fetch"
    );
    let repos: Vec<String> = setup
        .agent
        .requests()
        .into_iter()
        .filter_map(|request| match request {
            Request::Have { repo, .. } => Some(repo),
            _ => None,
        })
        .collect();
    assert_eq!(repos, ["~/projects/branch-deck", "~/projects/idea-1", "~/projects/idea-1"]);
    let spec = start_spec(&setup);
    assert_eq!(
        (spec.commit.as_str(), spec.repo.as_str(), spec.nice, spec.outputs, spec.changes),
        (SNAP, "~/projects/idea-1", Some(10), true, false)
    );
    assert!(
        said.lines().contains(&format!(
            "yonder: run {} of 555555555555 on studio: the working tree, HEAD 0123456789ab and 2 changed paths",
            outcome.run
        )),
        "{:#?}",
        said.lines()
    );
    assert!(
        said_starts(
            &said,
            "yonder: studio has no ~/projects/branch-deck; the run goes to ~/projects/idea-1"
        ),
        "{:#?}",
        said.lines()
    );
}

#[test]
fn a_repository_of_the_local_name_on_the_host_takes_the_run() {
    let (setup, _) = agent_following(&[COMMIT], |_, channel| exit(channel, 0));
    let processes = dirty_setup(&setup);
    let said = Said::default();
    let outcome = run_options(&setup, &processes, &said, &setup.checkout(), &CLI).expect("the run");
    assert_eq!(start_spec(&setup).repo, "~/projects/branch-deck");
    let line = format!(
        "yonder: run {} of 555555555555 on studio in ~/projects/branch-deck: the working tree",
        outcome.run
    );
    assert!(said_starts(&said, &line), "{:#?}", said.lines());
}

/// An agent of `features` that has every commit and ends each run with 0. `fetch` answers a `Fetch`.
fn echo_agent(features: &[&str], fetch: impl Fn(&mut Channel) + Send + Sync + 'static) -> Setup {
    Setup::with_features(features, move |request, channel| match request {
        Request::Have { commits, .. } => {
            let _ = channel.send(&Reply::Have { commits: commits.clone() });
        }
        Request::Start(spec) => {
            let _ = channel.send(&Reply::Started { run: spec.run.clone() });
        }
        Request::Follow { .. } => exit(channel, 0),
        Request::Fetch { .. } => fetch(channel),
        _ => done(channel, "done"),
    })
}

/// An agent of this build that has every commit, ends each run with 0, and sends `files` in the fetch.
fn fetching_agent(files: Vec<(String, FileKind, Vec<u8>)>) -> Setup {
    echo_agent(&[], move |channel| {
        for (path, kind, bytes) in &files {
            send_kind(channel, path, bytes, kind.clone());
        }
        let _ = channel.send(&Reply::Fetched { files: 0, bytes: 0 });
    })
}

/// A run that vanished on the host after it started (another client forgot it) may have run its command: the exit is no
/// "not run", so a caller does not run the command again, and the client sends no forget for a run that is gone.
#[test]
fn a_run_that_vanished_on_the_host_is_no_run_that_did_not_run() {
    let (setup, _) = agent_following(&[COMMIT], |_, channel| {
        output(channel, Stream::Stderr, "building", 10);
        let _ = channel.send(&Reply::refused("unknown_run", "the agent keeps no run"));
    });
    let refusal = run_in(&setup, &checkout(), &|_| {}, &calm, &fetch()).expect_err("vanished");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("unknown_run", Exit::Refused));
    assert!(
        refusal.message.contains("whether its command ran is unknown"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("`yonder runs studio`"), "{}", refusal.message);
    assert!(!kinds(&setup).contains(&"forget"), "{:?}", kinds(&setup));
}

#[test]
fn a_refusal_before_the_command_is_not_run_and_one_after_it_is_not() {
    let refused_after = |phases: Vec<Phase>| {
        let (setup, _) = agent_following(&[COMMIT], move |_, channel| {
            let _ = channel.send(&Reply::Phases { phases: phases.clone() });
            let _ = channel.send(&Reply::refused("prepare_failed", "the prepare step \"deps\" exited with 4"));
        });
        run_in(&setup, &checkout(), &|_| {}, &calm, &fetch()).expect_err("refused")
    };
    let before = refused_after(vec![step("lock", 0), step("prepare", 10)]);
    assert_eq!((before.code.as_ref(), before.exit), ("prepare_failed", Exit::NotRun));
    let after = refused_after(vec![step("prepare", 10), step("command", 10)]);
    assert_eq!(after.exit, Exit::Refused);
    let no_host = Client::new(
        crate::Config::new(tempfile::tempdir().expect("a directory").path().to_path_buf()),
        &crate::OsProcesses,
        &|_| {},
        &calm,
    )
    .run(ALIAS, &request(Path::new("/c"), &command(), &fetch()))
    .expect_err("no host");
    assert_eq!((no_host.code.as_ref(), no_host.exit.code()), (code::NO_HOST, 75));
}

/// The checkout of the setup, whose HEAD has `.yonder.toml` of `config`, and the fake bd of it.
fn checkout_of_config(setup: &Setup, config: Option<&str>) -> (PathBuf, FakeProcesses) {
    let snapshot = Snap { config, ..Snap::default() };
    (setup.checkout(), checkout_with(&[(" host snapshot", snapshot.answer())]))
}

/// The `bd host apply` calls of `processes`, as their patch files.
fn applied(processes: &FakeProcesses) -> Vec<String> {
    processes
        .calls()
        .iter()
        .filter(|call| call.text().contains(" host apply "))
        .filter_map(|call| call.args.last().cloned())
        .collect()
}

const PATCH: &str = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n+a formatted\n";

/// The options of a run that applies the changes of its command.
const APPLY: RunOptions = RunOptions {
    working_tree: false,
    nice: None,
    outputs: false,
    apply: true,
    repo_by_name: false,
};

#[test]
fn the_outputs_replace_their_local_copies_and_the_changes_apply_to_the_working_tree() {
    let results = FileKind::Output {
        root: "results".to_owned(),
    };
    let setup = fetching_agent(vec![
        ("results/new.txt".to_owned(), results.clone(), b"new".to_vec()),
        ("results/sub/deep.txt".to_owned(), results, b"deep".to_vec()),
        ("changes.patch".to_owned(), FileKind::Changes, PATCH.as_bytes().to_vec()),
    ]);
    let (root, processes) = checkout_of_config(&setup, Some("outputs = [\"results\"]\n"));
    fs::create_dir_all(root.join("results")).expect("results");
    fs::write(root.join("results/old.txt"), "old").expect("an old local output");
    let said = Said::default();
    let options = RunOptions { outputs: true, ..APPLY };
    let outcome = run_options(&setup, &processes, &said, &root, &options).expect("the run");
    assert_eq!(outcome.fetched, ["results/new.txt", "results/sub/deep.txt"]);
    assert!(
        !root.join("results/old.txt").exists(),
        "the output of the run replaced the local one"
    );
    assert_eq!(fs::read_to_string(root.join("results/sub/deep.txt")).expect("deep"), "deep");
    let patch = processes.run_files().join(format!("{}.patch", outcome.run));
    assert_eq!(fs::read_to_string(&patch).expect("the kept patch"), PATCH);
    assert_eq!(applied(&processes), [patch.display().to_string()], "bd applies the patch");
    assert!(
        said.lines().contains(&format!(
            "yonder: applied the changes of the command to 1 files of this working tree; `git apply -R {}` undoes them",
            patch.display()
        )),
        "{:#?}",
        said.lines()
    );
}

/// The host cannot choose what replaces a local path: the record of the run names the outputs of `.yonder.toml` of the
/// shipped commit and whether the client asked for the changes. A file beyond the record is skipped, and the run keeps it
/// on the host.
#[test]
fn a_run_takes_no_output_and_no_changes_that_its_record_did_not_ask_for() {
    let setup = fetching_agent(vec![
        (
            "src/main.rs".to_owned(),
            FileKind::Output { root: "src".to_owned() },
            b"theirs".to_vec(),
        ),
        ("changes.patch".to_owned(), FileKind::Changes, PATCH.as_bytes().to_vec()),
        (
            "results/new.txt".to_owned(),
            FileKind::Output {
                root: "results".to_owned(),
            },
            b"new".to_vec(),
        ),
    ]);
    let (root, processes) = checkout_of_config(&setup, Some("outputs = [\"results\"]\n"));
    fs::create_dir_all(root.join("src")).expect("src");
    fs::write(root.join("src/main.rs"), "mine").expect("a local file");
    let said = Said::default();
    let options = RunOptions {
        outputs: true,
        ..RunOptions::default()
    };
    let outcome = run_options(&setup, &processes, &said, &root, &options).expect("the run");
    assert_eq!(outcome.fetched, Vec::<String>::new(), "the fetch failed");
    assert_eq!(fs::read_to_string(root.join("src/main.rs")).expect("the local file"), "mine");
    assert_eq!(fs::read_to_string(root.join("results/new.txt")).expect("the output"), "new");
    assert!(!processes.run_files().join(format!("{}.patch", outcome.run)).exists());
    assert!(!kinds(&setup).contains(&"forget"), "the run stays on the host");
    assert!(
        said.lines()
            .iter()
            .any(|line| line.contains("2 of 3 files") && line.contains("src/main.rs, changes.patch")),
        "{:#?}",
        said.lines()
    );
    let record: serde_json::Value =
        serde_json::from_str(&fs::read_to_string(processes.run_files().join(format!("{}.json", outcome.run))).expect("the record"))
            .expect("JSON");
    assert_eq!(
        record,
        serde_json::json!({"commit": outcome.commit, "fetch": [], "outputs": ["results"], "changes": false})
    );
}

/// A commit whose `.yonder.toml` does not parse starts no run: the client cannot tell its outputs.
#[test]
fn a_config_that_does_not_parse_starts_no_run_with_outputs() {
    let setup = fetching_agent(Vec::new());
    let (root, processes) = checkout_of_config(&setup, Some("outputs = \"results\"\n"));
    let options = RunOptions {
        outputs: true,
        ..RunOptions::default()
    };
    let refusal = run_options(&setup, &processes, &Said::default(), &root, &options).expect_err("refused");
    assert_eq!((refusal.code.as_ref(), refusal.exit), (code::BAD_CONFIG, Exit::NotRun));
    assert!(!kinds(&setup).contains(&"start"), "{:?}", kinds(&setup));
}

#[test]
fn changes_that_do_not_apply_refuse_and_keep_the_patch() {
    let setup = fetching_agent(vec![(
        "changes.patch".to_owned(),
        FileKind::Changes,
        PATCH.replace("-a\n", "-something else\n").into_bytes(),
    )]);
    let processes = checkout_with(&[(" host apply ", bd_refused("error: patch failed: a.txt:1"))]);
    let root = setup.checkout();
    let said = Said::default();
    let refusal = run_options(&setup, &processes, &said, &root, &APPLY).expect_err("refused");
    assert_eq!((refusal.code.as_ref(), refusal.exit), (code::APPLY_FAILED, Exit::Refused));
    assert!(refusal.message.contains("exited with 0"), "{}", refusal.message);
    assert!(refusal.message.contains("error: patch failed: a.txt:1"), "{}", refusal.message);
    let patch = processes.run_files().join(format!("{}.patch", run_of(&setup)));
    assert!(
        refusal.message.ends_with(&format!("The patch is {}", patch.display())),
        "{}",
        refusal.message
    );
    assert!(patch.is_file());

    let quiet = fetching_agent(Vec::new());
    let processes = checkout();
    run_options(&quiet, &processes, &said, &quiet.checkout(), &APPLY).expect("the run");
    assert!(
        said.lines().contains(&"yonder: the command changed no file".to_owned()),
        "{:#?}",
        said.lines()
    );
    assert_eq!(applied(&processes), Vec::<String>::new());
}

/// The id of the run that reached the agent of `setup`.
fn run_of(setup: &Setup) -> String {
    start_spec(setup).run
}

#[test]
fn changes_that_did_not_come_refuse_with_the_fetch_that_copies_them() {
    let setup = echo_agent(&[], |channel| {
        let _ = channel.send(&Reply::File {
            path: "changes.patch".to_owned(),
            size: 10,
            executable: false,
            kind: FileKind::Changes,
        });
        let _ = channel.send_bytes(b"four");
    });
    let refusal = run_options(&setup, &checkout(), &Said::default(), &setup.checkout(), &APPLY).expect_err("refused");
    assert_eq!((refusal.code.as_ref(), refusal.exit), (code::APPLY_FAILED, Exit::Refused));
    assert!(
        refusal.message.contains("did not come: `yonder fetch studio "),
        "{}",
        refusal.message
    );
}
