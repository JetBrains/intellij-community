use pretty_assertions::assert_eq;

use super::*;
use crate::testkit::{ALIAS, FakeProcesses, Setup, calm, client};

/// An agent that stages, for each fetch, one file inside `out/bt-runs` and one outside, a file of the output `src`,
/// and the changes.
fn agent_of_one_run() -> Setup {
    Setup::new(move |request, channel| {
        let _ = match request {
            Request::Fetch { .. } => {
                let files = [
                    ("out/bt-runs/1/a.log", FileKind::Staged),
                    ("scripts/hook.sh", FileKind::Staged),
                    ("src/main.rs", FileKind::Output { root: "src".to_owned() }),
                    ("changes.patch", FileKind::Changes),
                ];
                for (path, kind) in files {
                    let _ = channel.send(&Reply::File {
                        path: path.to_owned(),
                        size: 1,
                        executable: false,
                        kind,
                    });
                    let _ = channel.send_bytes(b"x");
                    let _ = channel.send_end();
                }
                channel.send(&Reply::Fetched { files: 4, bytes: 4 })
            }
            _ => channel.send(&Reply::refused("bad_request", "not in this script")),
        };
    })
}

/// A checkout with a local `src/main.rs`, which a fetch must not replace unless the record asks for the output `src`.
fn checkout_with_src(setup: &Setup) -> PathBuf {
    let checkout = setup.checkout();
    fs::create_dir_all(checkout.join("src")).expect("src");
    fs::write(checkout.join("src/main.rs"), "mine").expect("a local file");
    checkout
}

/// `yonder fetch` without `--fetch` takes what the local record of the run asked for, and nothing that the host adds.
#[test]
fn a_fetch_without_directories_takes_what_the_record_asked_for() {
    let processes = FakeProcesses::default();
    let setup = agent_of_one_run();
    let checkout = checkout_with_src(&setup);
    let record = RunRecord {
        commit: "c".to_owned(),
        fetch: vec!["out/bt-runs".to_owned()],
        outputs: vec!["results".to_owned()],
        changes: true,
    };
    let files = RunFiles::new(&processes.run_files()).expect("the run files");
    files.write("1-1", &record).expect("the record");
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "1-1", &checkout, &[])
        .expect_err("two files are outside the record");
    assert_eq!(refusal.code, code::FETCH_FAILED);
    assert!(
        refusal.message.contains("2 of 4 files") && refusal.message.contains("scripts/hook.sh, src/main.rs"),
        "{}",
        refusal.message
    );
    assert!(checkout.join("out/bt-runs/1/a.log").is_file());
    assert!(!checkout.join("scripts/hook.sh").exists());
    assert_eq!(fs::read_to_string(checkout.join("src/main.rs")).expect("the local file"), "mine");
    assert_eq!(fs::read_to_string(files.patch("1-1")).expect("the changes"), "x");
}

/// Without a record, an explicit `--fetch` takes the staged files only, and no `--fetch` refuses before a connection.
#[test]
fn a_fetch_without_a_record_takes_the_staged_files_of_its_directories_only() {
    let processes = FakeProcesses::default();
    let setup = agent_of_one_run();
    let checkout = checkout_with_src(&setup);
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "1-1", &checkout, &[])
        .expect_err("no record and no --fetch");
    assert_eq!(refusal.code, code::USAGE);
    assert!(refusal.message.contains("has no record of the run 1-1"), "{}", refusal.message);
    assert!(setup.agent.requests().is_empty(), "{:?}", setup.agent.requests());

    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "1-1", &checkout, &fetch())
        .expect_err("three files are outside --fetch");
    assert!(refusal.message.contains("3 of 4 files"), "{}", refusal.message);
    assert!(checkout.join("out/bt-runs/1/a.log").is_file());
    assert_eq!(fs::read_to_string(checkout.join("src/main.rs")).expect("the local file"), "mine");
    assert!(!processes.run_files().join("1-1.patch").exists());

    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "../1-1", &checkout, &fetch())
        .expect_err("a bad run id");
    assert_eq!(refusal.code, code::USAGE);
}

/// An agent whose fetch sends `files`: each a path, a kind, the size that it names, and the bytes that it sends.
fn agent_sending(files: Vec<(&'static str, FileKind, u64, Vec<u8>)>) -> Setup {
    Setup::new(move |request, channel| {
        if let Request::Fetch { .. } = request {
            for (path, kind, size, bytes) in &files {
                let _ = channel.send(&Reply::File {
                    path: (*path).to_owned(),
                    size: *size,
                    executable: false,
                    kind: kind.clone(),
                });
                for chunk in bytes.chunks(64 * 1024) {
                    let _ = channel.send_bytes(chunk);
                }
                let _ = channel.send_end();
            }
            let _ = channel.send(&Reply::Fetched { files: 0, bytes: 0 });
        }
    })
}

/// Fetches the run `1-1` with the record of `record` in a checkout that `local` makes first.
fn fetch_with(setup: &Setup, record: &RunRecord, local: impl FnOnce(&Path)) -> (PathBuf, Result<Vec<String>, Refusal>) {
    let processes = FakeProcesses::default();
    let checkout = setup.checkout();
    local(&checkout);
    RunFiles::new(&processes.run_files())
        .expect("the run files")
        .write("1-1", record)
        .expect("the record");
    let fetched = client(setup, &processes, &|_| {}, &calm).fetch(ALIAS, "1-1", &checkout, &[]);
    (checkout, fetched)
}

fn staged_record() -> RunRecord {
    RunRecord {
        fetch: fetch(),
        outputs: vec!["out/results".to_owned()],
        ..RunRecord::default()
    }
}

/// A local link in a parent directory would take a write or the removal of an output outside the checkout, so the
/// fetch refuses the file. The directory that the link names stays as it was.
#[cfg(unix)]
#[test]
fn a_link_in_a_parent_directory_takes_no_file_and_removes_nothing() {
    let outside = tempfile::tempdir().expect("a directory outside");
    fs::create_dir_all(outside.path().join("results")).expect("results");
    fs::write(outside.path().join("results/keep.txt"), "keep").expect("a file outside");
    let setup = agent_sending(vec![
        ("out/bt-runs/1/a.log", FileKind::Staged, 1, b"x".to_vec()),
        (
            "out/results/new.txt",
            FileKind::Output {
                root: "out/results".to_owned(),
            },
            1,
            b"x".to_vec(),
        ),
    ]);
    let (_, fetched) = fetch_with(&setup, &staged_record(), |checkout| {
        std::os::unix::fs::symlink(outside.path(), checkout.join("out")).expect("a link");
    });
    let refusal = fetched.expect_err("both files are refused");
    assert!(refusal.message.contains("2 of 2 files"), "{}", refusal.message);
    assert_eq!(
        fs::read_to_string(outside.path().join("results/keep.txt")).expect("the file outside"),
        "keep"
    );
    assert!(!outside.path().join("bt-runs").exists());
    assert!(!outside.path().join("results/new.txt").exists());
}

/// A part that a link names would write through the link. The fetch removes the link, and the file it names stays.
#[cfg(unix)]
#[test]
fn a_planted_part_link_is_removed_and_its_target_stays() {
    let outside = tempfile::tempdir().expect("a directory outside");
    let victim = outside.path().join("victim");
    fs::write(&victim, "victim").expect("a file outside");
    let setup = agent_sending(vec![("out/bt-runs/1/a.log", FileKind::Staged, 3, b"new".to_vec())]);
    let (checkout, fetched) = fetch_with(&setup, &staged_record(), |checkout| {
        let dir = checkout.join("out/bt-runs/1");
        fs::create_dir_all(&dir).expect("the directory");
        std::os::unix::fs::symlink(&victim, dir.join("a.log.yonder-part")).expect("a link");
    });
    assert_eq!(fetched.expect("the fetch"), ["out/bt-runs/1/a.log"]);
    assert_eq!(fs::read_to_string(&victim).expect("the file outside"), "victim");
    assert_eq!(fs::read_to_string(checkout.join("out/bt-runs/1/a.log")).expect("the file"), "new");
    let part = fs::symlink_metadata(checkout.join("out/bt-runs/1/a.log.yonder-part"));
    assert_eq!(part.map_err(|error| error.kind()).err(), Some(io::ErrorKind::NotFound));
}

/// A stream over its size writes no more than its size, and the next file of the fetch still comes.
#[test]
fn a_stream_over_its_size_writes_no_more_and_the_next_file_comes() {
    let setup = agent_sending(vec![
        ("out/bt-runs/1/big.log", FileKind::Staged, 4, vec![b'x'; 300 * 1024]),
        ("out/bt-runs/1/small.log", FileKind::Staged, 2, b"ok".to_vec()),
    ]);
    let (checkout, fetched) = fetch_with(&setup, &staged_record(), |_| {});
    let refusal = fetched.expect_err("the big file is refused");
    assert!(
        refusal.message.contains("1 of 2 files") && refusal.message.contains("out/bt-runs/1/big.log"),
        "{}",
        refusal.message
    );
    assert!(!checkout.join("out/bt-runs/1/big.log").exists());
    assert!(!checkout.join("out/bt-runs/1/big.log.yonder-part").exists());
    assert_eq!(
        fs::read_to_string(checkout.join("out/bt-runs/1/small.log")).expect("the next file"),
        "ok"
    );
}

#[test]
fn a_capped_writer_passes_its_size_and_drops_the_rest() {
    let mut capped = Capped {
        inner: Vec::new(),
        left: 5,
    };
    capped.write_all(b"abc").expect("a write");
    capped.write_all(b"defgh").expect("a write over the cap");
    assert_eq!(capped.inner, b"abcde");
}

fn fetch() -> Vec<String> {
    vec!["out/bt-runs".to_owned()]
}

#[test]
fn a_fetched_path_stays_below_a_fetch_directory_of_the_checkout() {
    let fetch = fetch();
    let root = Path::new("/c");
    assert_eq!(
        local_target(root, "out/bt-runs/1/a.log", &fetch).expect("a staged file"),
        root.join("out").join("bt-runs").join("1").join("a.log")
    );
    for path in [
        "/etc/passwd",
        "out/bt-runs/../x",
        "out/bt-runs",
        "out/other/a",
        "out//bt-runs/a",
        "out/bt-runs/c:\\x",
        "out/bt-runs/1/a.log.yonder-part",
    ] {
        local_target(root, path, &fetch).expect_err(path);
    }
}

/// A host that sends a hook or a git config would run code here, so no path inside a `.git` directory comes, in any
/// spelling that a file system of the client reads as `.git`. A path without fetch directories comes nowhere.
#[test]
fn no_path_goes_into_a_git_directory_or_comes_without_fetch_directories() {
    let root = Path::new("/c");
    let everything = vec!["out".to_owned()];
    for path in [
        "out/.git/hooks/post-checkout",
        "out/.GIT/config",
        "out/.Git./config",
        "out/.git /config",
        "out/GIT~1/config",
        "out/sub/.git",
    ] {
        let refused = local_target(root, path, &everything).expect_err(path);
        assert_eq!(refused, "the path is inside a .git directory", "{path}");
    }
    assert_eq!(
        local_target(root, "out/.github/workflow.yml", &everything).expect("a name that only starts with .git"),
        root.join("out").join(".github").join("workflow.yml")
    );
    local_target(root, "a/b.txt", &[]).expect_err("no fetch directory");
}

#[test]
fn a_part_file_is_named_after_its_target() {
    assert_eq!(part_path(Path::new("/c/a.log")), Path::new("/c/a.log.yonder-part"));
}
