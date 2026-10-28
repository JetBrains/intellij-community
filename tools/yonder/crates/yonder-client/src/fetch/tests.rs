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
    let files = RunFiles::of(&processes, &checkout, &never).expect("the run files");
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
