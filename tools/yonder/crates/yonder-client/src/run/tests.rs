use std::fs;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU32};
use std::sync::{Mutex, PoisonError};

use pretty_assertions::assert_eq;
use yonder_link::{Channel, Frame};

use super::*;
use crate::testkit::{ALIAS, FakeProcesses, Said, Setup, calm, client, ok};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// A clean checkout at [`COMMIT`], whose first-parent ancestors are `c1`, and whose pack is `PACK`.
fn checkout() -> FakeProcesses {
    FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("7\n")),
        ("rev-list", ok(&format!("{COMMIT}\nc1\n"))),
        ("merge-base", ok("m0\n")),
        ("pack-objects", ok("PACK")),
    ])
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
        idle_hours: 8,
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
        Request::Lease { .. } => "lease",
        Request::Release { .. } => "release",
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
    let _ = channel.send(&Reply::File {
        path: path.to_owned(),
        size: bytes.len() as u64,
        executable: false,
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
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => {
                output(channel, Stream::Stderr, "progress", 30);
                let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 15_000 });
                output(channel, Stream::Stdout, "the digest", 60);
                let _ = channel.send(&Reply::Phases {
                    phases: vec![step("lock", 0), step("command", 25_100)],
                });
                let _ = channel.send(&Reply::Exit { code: 3 });
            }
            _ => answer_besides_follow(request, channel, &packed, &["c1"]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let checkout_root = setup.checkout();
    let (command, fetch) = (command(), fetch());
    let outcome = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &fetch))
        .expect("the run");

    assert_eq!(outcome.code, 3);
    assert_eq!(outcome.stdout, "the digest\n");
    assert_eq!(outcome.commit, COMMIT);
    assert_eq!(outcome.fetched, ["out/bt-runs/0000000002000/test.log"]);
    assert_eq!(
        fs::read_to_string(checkout_root.join("out/bt-runs/0000000002000/test.log")).expect("the fetched file"),
        "hello"
    );

    // The start comes before the pack, so the host takes the lease while the pack travels, and the follow goes on
    // beside the pack.
    let requests = setup.agent.requests();
    let mut kinds = requests.iter().map(kind).collect::<Vec<_>>();
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
    assert_eq!((spec.holder.as_str(), spec.idle_hours), ("bt-remote-0123456789ab", 8));
    assert_eq!((spec.command.clone(), spec.fetch.clone()), (command, fetch));
    assert!(yonder_link::proto::is_run_id(&outcome.run), "{}", outcome.run);

    assert_eq!(*packed.lock().unwrap_or_else(PoisonError::into_inner), b"PACK");
    let pack = processes
        .calls()
        .into_iter()
        .find(|call| call.text().contains("pack-objects"))
        .expect("the pack");
    assert_eq!(String::from_utf8_lossy(&pack.input), format!("{COMMIT}\n^c1\n"));

    let lines = said.lines();
    assert!(lines.contains(&"progress".to_owned()), "{lines:#?}");
    for line in ["> studio: pack 7 commits", "> studio: pack of 4 B ready", "> studio: sent 4 B"] {
        assert!(lines.contains(&line.to_owned()), "{line} in {lines:#?}");
    }
    assert_eq!(
        pack.text(),
        "git pack-objects --revs --thin --stdout --progress",
        "the pack writes progress, which its quiet limit watches"
    );
    assert_eq!(
        outcome.phases.iter().map(|phase| phase.name.as_str()).collect::<Vec<_>>(),
        ["pack", "upload", "lock", "command", "fetch", "total"],
        "the steps of the client wrap the steps of the host"
    );
    assert_eq!(outcome.phases[3].ms, 25_100);
}

#[test]
fn a_host_that_has_the_commit_gets_no_pack() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => {
                let _ = channel.send(&Reply::Exit { code: 0 });
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    assert_eq!(
        setup.agent.requests().iter().map(kind).collect::<Vec<_>>(),
        ["have", "start", "follow", "forget"]
    );
    assert!(!processes.texts().iter().any(|text| text.contains("pack-objects")));
}

/// A lost connection follows again from the last offset, and a record that came twice shows once.
#[test]
fn a_lost_connection_follows_again_from_the_last_offset() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { offset: 0, .. } => output(channel, Stream::Stderr, "first", 10),
            Request::Follow { .. } => {
                output(channel, Stream::Stderr, "first", 10);
                output(channel, Stream::Stdout, "done", 20);
                let _ = channel.send(&Reply::Exit { code: 0 });
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
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
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => thread::sleep(Duration::from_secs(4)),
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("no reply");
    assert_eq!(refusal.code, code::TIMED_OUT);
    assert!(refusal.message.contains("`yonder attach studio "), "{}", refusal.message);
    assert!(
        !setup.agent.requests().iter().any(|request| kind(request) == "forget"),
        "a run whose end the client did not see stays on the host"
    );
}

/// Ctrl+C sends `Cancel` on its own connection, and the output follows up to the exit.
#[test]
fn ctrl_c_cancels_the_run_and_follows_it_to_its_end() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let pressed = Arc::new(AtomicBool::new(false));
    let cancelled = Arc::new(AtomicBool::new(false));
    let setup = Setup::new({
        let (packed, pressed, cancelled) = (Arc::clone(&packed), Arc::clone(&pressed), Arc::clone(&cancelled));
        move |request, channel| match request {
            Request::Follow { .. } => {
                pressed.store(true, Ordering::SeqCst);
                let started = Instant::now();
                while !cancelled.load(Ordering::SeqCst) && started.elapsed() < Duration::from_secs(5) {
                    thread::sleep(Duration::from_millis(50));
                }
                output(channel, Stream::Stderr, "interrupted", 20);
                let _ = channel.send(&Reply::Exit { code: 130 });
            }
            Request::Cancel { .. } => {
                cancelled.store(true, Ordering::SeqCst);
                let _ = channel.send(&Reply::Done {
                    message: "the run ended".to_owned(),
                });
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let interrupted = || u32::from(pressed.load(Ordering::SeqCst));
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &say, &interrupted)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
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
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => {
                let _ = channel.send(&Reply::Phases {
                    phases: vec![step("lock", 0), step("lease", 3_909_000)],
                });
                let _ = channel.send(&Reply::refused("lease_failed", "bd lease get failed: no free slot"));
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("the lease failed");
    assert_eq!(refusal.code, "lease_failed");
    assert_eq!(refusal.message, "studio: bd lease get failed: no free slot");
    assert_eq!(kind(setup.agent.requests().last().expect("a request")), "forget");
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
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .lease(ALIAS, None, "h")
        .expect_err("out of turn");
    assert_eq!(refusal.code, code::LINK);
}

const BASE: &str = "fedcba9876543210fedcba9876543210fedcba98";

/// A clean checkout at [`COMMIT`] whose `origin/master` is [`BASE`].
fn checkout_with_base() -> FakeProcesses {
    FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("origin/master^{commit}", ok(&format!("{BASE}\n"))),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("7\n")),
        ("rev-list", ok(&format!("{COMMIT}\nc1\n"))),
        ("merge-base", ok("m0\n")),
        ("pack-objects", ok("PACK")),
    ])
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

/// While the command goes on, the base goes once to `refs/yonder/base/<holder>`, after the run pack. The next run names
/// the base in its `Have`, so its pack leaves out the base, and its sync skips the base that the host has.
#[test]
fn the_base_goes_once_and_the_next_run_builds_on_it() {
    let based = Arc::new(AtomicBool::new(false));
    let setup = Setup::new({
        let based = Arc::clone(&based);
        move |request, channel| {
            let _ = match request {
                Request::Have { .. } => {
                    let mut commits = vec!["c1".to_owned()];
                    if based.load(Ordering::SeqCst) {
                        commits.push(BASE.to_owned());
                    }
                    channel.send(&Reply::Have { commits })
                }
                Request::Pack { reference, .. } => {
                    let _ = channel.recv_stream(&mut Vec::new());
                    if reference.starts_with(yonder_link::proto::BASE_REFS) {
                        based.store(true, Ordering::SeqCst);
                    }
                    channel.send(&Reply::Packed)
                }
                // A long command: it ends after the base came, or after 5 s.
                Request::Follow { .. } => {
                    let started = Instant::now();
                    while !based.load(Ordering::SeqCst) && started.elapsed() < Duration::from_secs(5) {
                        thread::sleep(Duration::from_millis(20));
                    }
                    channel.send(&Reply::Exit { code: 0 })
                }
                Request::Start(spec) => channel.send(&Reply::Started { run: spec.run.clone() }),
                _ => channel.send(&Reply::Done {
                    message: "removed".to_owned(),
                }),
            };
        }
    });
    let processes = checkout_with_base();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let holder = "bt-remote-0123456789ab";
    let client = client(&setup, &processes, &say, &calm);
    client
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the first run");
    let first = pack_refs(&setup);
    assert_eq!(first.len(), 2, "{first:?}");
    assert!(first[0].starts_with("refs/yonder/") && !first[0].contains("/base/"), "{first:?}");
    assert_eq!(first[1], format!("refs/yonder/base/{holder}"));
    assert_eq!(setup.config.base(ALIAS, holder).as_deref(), Some(BASE));

    client
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the second run");
    assert_eq!(pack_refs(&setup).len(), 3, "the second run sends its own pack only");
    let haves: Vec<Vec<String>> = setup
        .agent
        .requests()
        .into_iter()
        .filter_map(|request| match request {
            Request::Have { commits, .. } => Some(commits),
            _ => None,
        })
        .collect();
    assert_eq!(haves.len(), 4, "{haves:#?}");
    assert!(
        haves[2].contains(&BASE.to_owned()),
        "the second run asks about the base: {haves:#?}"
    );
    let packs: Vec<String> = processes
        .calls()
        .into_iter()
        .filter(|call| call.text().contains("pack-objects"))
        .map(|call| String::from_utf8_lossy(&call.input).into_owned())
        .collect();
    assert_eq!(packs.len(), 3, "{packs:#?}");
    assert_eq!(packs[2], format!("{COMMIT}\n^c1\n^{BASE}\n"));
    let lines = said.lines();
    assert!(!lines.iter().any(|line| line.contains("warning")), "{lines:#?}");
}

/// An agent that refuses the base ref, as an agent before schema 2 does, gives one warning line, and the run ends with
/// the code of its command.
#[test]
fn a_failed_base_sync_only_warns() {
    let setup = Setup::new(|request, channel| {
        let _ = match request {
            Request::Have { .. } => channel.send(&Reply::Have {
                commits: vec!["c1".to_owned()],
            }),
            Request::Pack { reference, .. } if reference.starts_with(yonder_link::proto::BASE_REFS) => {
                let _ = channel.recv_stream(&mut Vec::new());
                channel.send(&Reply::refused("bad_request", "a pack ref that is not refs/yonder/<run id>"))
            }
            Request::Pack { .. } => {
                let _ = channel.recv_stream(&mut Vec::new());
                channel.send(&Reply::Packed)
            }
            Request::Start(spec) => channel.send(&Reply::Started { run: spec.run.clone() }),
            Request::Follow { .. } => {
                thread::sleep(Duration::from_millis(300));
                channel.send(&Reply::Exit { code: 4 })
            }
            _ => channel.send(&Reply::Done {
                message: "removed".to_owned(),
            }),
        };
    });
    let processes = checkout_with_base();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
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

/// A checkout with local changes starts no run. Its refusal closes the `Have` that goes on meanwhile, so the refusal
/// comes at once even from a host that does not answer.
#[test]
fn local_changes_start_no_run_and_refuse_at_once() {
    let setup = Setup::new(|_, _| thread::sleep(Duration::from_secs(5)));
    let processes = FakeProcesses::new(&[("status --porcelain", ok(" M a.txt\n")), ("rev-parse", ok(&format!("{COMMIT}\n")))]);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let started = Instant::now();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("local changes");
    assert_eq!(refusal.code, code::LOCAL_CHANGES);
    assert!(started.elapsed() < Duration::from_secs(1), "{:?}", started.elapsed());
    assert!(
        setup.agent.requests().iter().all(|request| kind(request) == "have"),
        "{:?}",
        setup.agent.requests()
    );
}

#[test]
fn lease_release_and_runs_answer_the_replies_of_the_agent() {
    let setup = Setup::new(|request, channel| {
        let _ = match request {
            Request::Lease { holder, .. } => channel.send(&Reply::Leased {
                slot: format!("/pool/1/idea-1 of {holder}"),
                lease_id: "L1".to_owned(),
                fresh: true,
            }),
            Request::Release { repo, .. } => channel.send(&Reply::Done {
                message: format!("released the lease L1 in {repo}"),
            }),
            Request::Runs => channel.send(&Reply::Runs { runs: Vec::new() }),
            _ => channel.send(&Reply::refused("bad_request", "no")),
        };
    });
    let processes = FakeProcesses::default();
    let client = client(&setup, &processes, &|_| {}, &calm);
    assert_eq!(
        client.lease(ALIAS, None, "h").expect("leased"),
        Leased {
            slot: "/pool/1/idea-1 of h".to_owned(),
            lease_id: "L1".to_owned(),
            fresh: true,
        }
    );
    assert_eq!(
        client.release(ALIAS, Some("/w/idea"), "h").expect("released"),
        "released the lease L1 in /w/idea"
    );
    assert!(client.runs(ALIAS).expect("the runs").is_empty());
    assert_eq!(client.cancel(ALIAS, "r1").expect_err("refused").code, "bad_request");
}

/// A request that waits gets heartbeats before its reply, and each heartbeat starts the read deadline again. The wait
/// here is twice the deadline of the short timing.
#[test]
fn heartbeats_keep_a_waiting_request_alive() {
    let setup = Setup::new(|request, channel| {
        if let Request::Lease { .. } = request {
            for _ in 0..4 {
                thread::sleep(Duration::from_millis(1000));
                let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 1000 });
            }
            let _ = channel.send(&Reply::Leased {
                slot: "/pool/1/idea-1".to_owned(),
                lease_id: "L1".to_owned(),
                fresh: false,
            });
        }
    });
    let processes = FakeProcesses::default();
    let leased = client(&setup, &processes, &|_| {}, &calm).lease(ALIAS, None, "h").expect("leased");
    assert_eq!(leased.lease_id, "L1");
}

/// A host without any of the candidate commits would need the whole history. The client refuses, names the count,
/// and builds no pack.
#[test]
fn a_host_without_any_candidate_is_behind_and_gets_no_pack() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| answer_besides_follow(request, channel, &packed, &[])
    });
    let processes = checkout();
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("behind");
    assert_eq!(refusal.code, code::BEHIND);
    assert!(refusal.message.contains("all 7 commits of the history"), "{}", refusal.message);
    assert!(refusal.message.contains("`bt --host studio --prepare`"), "{}", refusal.message);
    assert_eq!(
        setup.agent.requests().iter().map(kind).collect::<Vec<_>>(),
        ["have", "have"],
        "the near candidates, then the long walk"
    );
    assert!(!processes.texts().iter().any(|text| text.contains("pack-objects")));
}

/// A host that has none of the near candidates gets a second `Have` with the long first-parent walk, and the pack
/// builds on the commit that it has from there.
#[test]
fn a_host_without_a_near_candidate_gets_the_long_walk() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Have { commits, .. } => {
                let has: Vec<String> = commits.iter().filter(|commit| *commit == "far").cloned().collect();
                let _ = channel.send(&Reply::Have { commits: has });
            }
            Request::Follow { .. } => {
                let _ = channel.send(&Reply::Exit { code: 0 });
            }
            _ => answer_besides_follow(request, channel, &packed, &[]),
        }
    });
    let processes = FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("40\n")),
        ("--max-count=17", ok(&format!("{COMMIT}\nc1\n"))),
        ("--max-count=65537", ok(&format!("{COMMIT}\nc1\nfar\n"))),
        ("pack-objects", ok("PACK")),
    ]);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    let haves: Vec<Vec<String>> = setup
        .agent
        .requests()
        .into_iter()
        .filter_map(|request| match request {
            Request::Have { commits, .. } => Some(commits),
            _ => None,
        })
        .collect();
    assert_eq!(
        haves,
        [
            vec![COMMIT.to_owned(), "c1".to_owned()],
            vec![COMMIT.to_owned(), "c1".to_owned(), "far".to_owned()]
        ]
    );
    let pack = processes
        .calls()
        .into_iter()
        .find(|call| call.text().contains("pack-objects"))
        .expect("the pack");
    assert_eq!(String::from_utf8_lossy(&pack.input), format!("{COMMIT}\n^far\n"));
}

/// Ctrl+C after `Have` stops the pack, and nothing more reaches the host.
#[test]
fn ctrl_c_before_the_start_stops_the_pack_and_reaches_no_host() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let pressed = Arc::new(AtomicBool::new(false));
    let setup = Setup::new({
        let (packed, pressed) = (Arc::clone(&packed), Arc::clone(&pressed));
        move |request, channel| {
            if let Request::Have { .. } = request {
                pressed.store(true, Ordering::SeqCst);
            }
            answer_besides_follow(request, channel, &packed, &["c1"]);
        }
    });
    let processes = checkout();
    let presses = || u32::from(pressed.load(Ordering::SeqCst));
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &presses)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("interrupted");
    assert_eq!(refusal.code, code::INTERRUPTED);
    assert_eq!(setup.agent.requests().iter().map(kind).collect::<Vec<_>>(), ["have"]);
}

/// A pack that the agent reads late: each write waits up to the read deadline, not the short connect deadline.
#[test]
fn a_slow_agent_reader_takes_a_large_pack() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| {
            if let Request::Pack { .. } = request {
                thread::sleep(Duration::from_millis(2500));
            }
            if let Request::Follow { .. } = request {
                let _ = channel.send(&Reply::Exit { code: 0 });
            } else {
                answer_besides_follow(request, channel, &packed, &["c1"]);
            }
        }
    });
    // Larger than the socket buffers of the loopback, so the writes wait for the reader.
    let pack = "x".repeat(4 * 1024 * 1024);
    let processes = FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("1\n")),
        ("rev-list", ok(&format!("{COMMIT}\nc1\n"))),
        ("pack-objects", ok(&pack)),
    ]);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let timing = Timing {
        connect: Duration::from_secs(1),
        read: Duration::from_secs(5),
        ..crate::testkit::FAST
    };
    let outcome = Client::new(setup.config, &processes, &|_| {}, &calm)
        .with_timing(timing)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    assert_eq!(packed.lock().unwrap_or_else(PoisonError::into_inner).len(), pack.len());
}

/// An agent that drops the connection in the middle of the pack is a lost link, not a git failure. The run started
/// already, so the client cancels it, and nothing follows it.
#[test]
fn a_link_lost_during_the_pack_cancels_the_started_run_with_a_link_refusal() {
    let setup = Setup::new(|request, channel| match request {
        Request::Have { .. } => {
            let _ = channel.send(&Reply::Have {
                commits: vec!["c1".to_owned()],
            });
        }
        Request::Start(spec) => {
            let _ = channel.send(&Reply::Started { run: spec.run.clone() });
        }
        // One chunk, then the connection closes.
        Request::Pack { .. } => {
            let _ = channel.recv_frame();
        }
        Request::Cancel { .. } => {
            let _ = channel.send(&Reply::Done {
                message: "the host has the cancel".to_owned(),
            });
        }
        _ => {}
    });
    let processes = FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("1\n")),
        ("rev-list", ok(&format!("{COMMIT}\nc1\n"))),
        ("pack-objects", ok(&"y".repeat(4 * 1024 * 1024))),
    ]);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("the link broke");
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
    assert_eq!(
        setup
            .agent
            .requests()
            .iter()
            .map(kind)
            .filter(|kind| *kind != "follow")
            .collect::<Vec<_>>(),
        ["have", "start", "pack", "cancel"]
    );
}

/// A cancel whose first connection fails connects again, and the run still ends with its own exit.
#[test]
fn a_failed_cancel_connects_again() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let cancels = Arc::new(AtomicU32::new(0));
    let setup = Setup::new({
        let (packed, cancels) = (Arc::clone(&packed), Arc::clone(&cancels));
        move |request, channel| match request {
            Request::Follow { .. } => {
                let started = Instant::now();
                while cancels.load(Ordering::SeqCst) < 2 && started.elapsed() < Duration::from_secs(5) {
                    thread::sleep(Duration::from_millis(200));
                    let _ = channel.send(&Reply::Heartbeat { elapsed_ms: 200 });
                }
                let _ = channel.send(&Reply::Exit { code: 130 });
            }
            // The first cancel gets no reply: the connection closes.
            Request::Cancel { .. } => {
                if cancels.fetch_add(1, Ordering::SeqCst) > 0 {
                    let _ = channel.send(&Reply::Done {
                        message: "the host has the cancel".to_owned(),
                    });
                }
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    // Ctrl+C before the start would refuse the run, so the presses count from the first Follow only.
    let (count, watcher) = press_after(&setup, "follow", 1);
    let counted = || count.load(Ordering::SeqCst);
    let outcome = client(&setup, &processes, &say, &counted)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
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
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => {
                for _ in 0..50 {
                    thread::sleep(Duration::from_millis(200));
                    if channel.send(&Reply::Heartbeat { elapsed_ms: 200 }).is_err() {
                        return;
                    }
                }
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let (count, watcher) = press_after(&setup, "follow", 2);
    let presses = || count.load(Ordering::SeqCst);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let started = Instant::now();
    let refusal = client(&setup, &processes, &|_| {}, &presses)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("stopped");
    watcher.join().expect("the watcher");
    assert_eq!(refusal.code, code::INTERRUPTED);
    assert!(started.elapsed() < Duration::from_secs(4), "{:?}", started.elapsed());
    assert!(refusal.message.contains("`yonder attach studio "), "{}", refusal.message);
    assert!(!setup.agent.requests().iter().any(|request| kind(request) == "forget"));
}

/// A fetch that the connection cuts leaves no part file and no forget, and the client names `yonder fetch`.
#[test]
fn a_cut_fetch_leaves_no_part_and_keeps_the_run() {
    let packed = Arc::new(Mutex::new(Vec::new()));
    let setup = Setup::new({
        let packed = Arc::clone(&packed);
        move |request, channel| match request {
            Request::Follow { .. } => {
                let _ = channel.send(&Reply::Exit { code: 3 });
            }
            Request::Fetch { .. } => {
                let _ = channel.send(&Reply::File {
                    path: "out/bt-runs/1/big.log".to_owned(),
                    size: 10,
                    executable: false,
                });
                let _ = channel.send_bytes(b"four");
            }
            _ => answer_besides_follow(request, channel, &packed, &[COMMIT]),
        }
    });
    let processes = checkout();
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, fetch) = (command(), fetch());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &fetch))
        .expect("the run");
    assert_eq!(outcome.code, 3);
    assert!(outcome.fetched.is_empty());
    let target = checkout_root.join("out/bt-runs/1/big.log");
    assert!(!target.exists());
    assert!(!checkout_root.join("out/bt-runs/1/big.log.yonder-part").exists());
    assert!(!setup.agent.requests().iter().any(|request| kind(request) == "forget"));
    let lines = said.lines();
    assert!(
        lines
            .iter()
            .any(|line| line.contains("`yonder fetch studio ") && line.contains("`yonder forget studio ")),
        "{lines:#?}"
    );
}

/// A start whose reply does not come can leave a run on the agent, which would wait for its pack. The client cancels it.
#[test]
fn a_start_without_its_reply_is_cancelled() {
    let setup = Setup::new(|request, channel| {
        let _ = match request {
            Request::Have { .. } => channel.send(&Reply::Have {
                commits: vec![COMMIT.to_owned()],
            }),
            Request::Cancel { .. } => channel.send(&Reply::Done {
                message: "the host has the cancel".to_owned(),
            }),
            // The start closes its connection without a reply.
            _ => Ok(()),
        };
    });
    let processes = checkout();
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect_err("no reply");
    assert!(matches!(refusal.code.as_ref(), code::LINK | code::TIMED_OUT), "{refusal:?}");
    assert!(refusal.message.starts_with("the start of the run "), "{}", refusal.message);
    assert!(
        refusal.message.contains("; studio has the cancel of the run "),
        "{}",
        refusal.message
    );
    assert_eq!(
        setup.agent.requests().iter().map(kind).collect::<Vec<_>>(),
        ["have", "start", "cancel"]
    );
}

/// The host takes the lock and the lease while git builds the pack, and empty chunks keep the connection of the pack
/// alive meanwhile. The follow starts with the run, so the lines of the host show during the build.
#[test]
fn the_pack_builds_after_the_start_and_keeps_its_connection_alive() {
    let keepalives = Arc::new(AtomicU32::new(0));
    let setup = Setup::new({
        let keepalives = Arc::clone(&keepalives);
        let packed = Arc::new(Mutex::new(Vec::new()));
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
            }
            Request::Follow { .. } => {
                output(channel, Stream::Stderr, "yonder: lease /pool/1 (warm)", 30);
                let _ = channel.send(&Reply::Exit { code: 0 });
            }
            _ => answer_besides_follow(request, channel, &packed, &["c1"]),
        }
    });
    let processes = checkout().with_pack_time(Duration::from_millis(400));
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let outcome = client(&setup, &processes, &say, &calm)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    assert!(
        keepalives.load(Ordering::SeqCst) >= 3,
        "{} empty chunks",
        keepalives.load(Ordering::SeqCst)
    );
    let lines = said.lines();
    let at = |text: &str| {
        lines
            .iter()
            .position(|line| line == text)
            .unwrap_or_else(|| panic!("{text} in {lines:#?}"))
    };
    assert!(at("yonder: lease /pool/1 (warm)") < at("> studio: pack of 4 B ready"), "{lines:#?}");
    assert_eq!(
        outcome.phases.iter().map(|phase| phase.name.as_str()).collect::<Vec<_>>(),
        ["pack", "upload", "total"]
    );
}

/// Ctrl+C while git builds the pack cancels the run on the host, and the output follows up to its end.
#[test]
fn ctrl_c_during_the_build_of_the_pack_cancels_the_run_on_the_host() {
    let cancelled = Arc::new(AtomicBool::new(false));
    let setup = Setup::new({
        let cancelled = Arc::clone(&cancelled);
        let packed = Arc::new(Mutex::new(Vec::new()));
        move |request, channel| match request {
            Request::Follow { .. } => {
                let started = Instant::now();
                while !cancelled.load(Ordering::SeqCst) && started.elapsed() < Duration::from_secs(5) {
                    thread::sleep(Duration::from_millis(20));
                }
                output(channel, Stream::Stderr, "yonder: the run was cancelled before its command", 20);
                let _ = channel.send(&Reply::Exit { code: 130 });
            }
            Request::Cancel { .. } => {
                cancelled.store(true, Ordering::SeqCst);
                let _ = channel.send(&Reply::Done {
                    message: "the supervisor has the cancel request".to_owned(),
                });
            }
            _ => answer_besides_follow(request, channel, &packed, &["c1"]),
        }
    });
    let processes = checkout().with_pack_time(Duration::from_secs(5));
    let (count, watcher) = press_after(&setup, "pack", 1);
    let presses = || count.load(Ordering::SeqCst);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let started = Instant::now();
    let outcome = client(&setup, &processes, &|_| {}, &presses)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run ends with its own code");
    watcher.join().expect("the watcher");
    assert_eq!(outcome.code, 130);
    assert!(cancelled.load(Ordering::SeqCst));
    assert!(
        started.elapsed() < Duration::from_secs(4),
        "the build was not stopped: {:?}",
        started.elapsed()
    );
}

/// Ctrl+C after the end of the run closes the connection of the base sync, also while the host indexes the base.
#[test]
fn ctrl_c_stops_the_base_sync_while_the_host_indexes_it() {
    let indexing = Arc::new(AtomicBool::new(false));
    let setup = Setup::new({
        let indexing = Arc::clone(&indexing);
        move |request, channel| {
            let _ = match request {
                Request::Have { .. } => channel.send(&Reply::Have {
                    commits: vec!["c1".to_owned()],
                }),
                Request::Pack { reference, .. } => {
                    let _ = channel.recv_stream(&mut Vec::new());
                    if reference.starts_with(yonder_link::proto::BASE_REFS) {
                        indexing.store(true, Ordering::SeqCst);
                        for _ in 0..50 {
                            thread::sleep(Duration::from_millis(200));
                            if channel.send(&Reply::Heartbeat { elapsed_ms: 200 }).is_err() {
                                return;
                            }
                        }
                    }
                    channel.send(&Reply::Packed)
                }
                Request::Start(spec) => channel.send(&Reply::Started { run: spec.run.clone() }),
                Request::Follow { .. } => channel.send(&Reply::Exit { code: 0 }),
                _ => channel.send(&Reply::Done {
                    message: "removed".to_owned(),
                }),
            };
        }
    });
    let processes = checkout_with_base();
    let presses = || u32::from(indexing.load(Ordering::SeqCst));
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let (command, no_fetch) = (command(), Vec::new());
    let checkout_root = setup.checkout();
    let started = Instant::now();
    let outcome = client(&setup, &processes, &say, &presses)
        .run(ALIAS, &request(&checkout_root, &command, &no_fetch))
        .expect("the run");
    assert_eq!(outcome.code, 0);
    assert!(
        started.elapsed() < Duration::from_secs(5),
        "the sync held the end: {:?}",
        started.elapsed()
    );
    let lines = said.lines();
    assert!(!lines.iter().any(|line| line.contains("warning")), "{lines:#?}");
}
