use pretty_assertions::assert_eq;
use yonder_link::proto::RunInfo;

use super::*;
use crate::testkit::{ALIAS, FakeProcesses, Setup, calm, client};

/// An agent that keeps the run `1-1` with the fetch directories `fetch`, and stages one file inside them and one
/// outside.
fn agent_of_one_run(fetch: Vec<String>) -> Setup {
    Setup::new(move |request, channel| {
        let _ = match request {
            Request::Runs => channel.send(&Reply::Runs {
                runs: vec![RunInfo {
                    run: "1-1".to_owned(),
                    holder: "h".to_owned(),
                    commit: "c".to_owned(),
                    command: vec!["true".to_owned()],
                    started_ms: 1,
                    exit: Some(0),
                    phases: Vec::new(),
                    fetch: fetch.clone(),
                }],
            }),
            Request::Fetch { .. } => {
                for path in ["out/bt-runs/1/a.log", "scripts/hook.sh"] {
                    let _ = channel.send(&Reply::File {
                        path: path.to_owned(),
                        size: 1,
                        executable: false,
                        kind: FileKind::Staged,
                    });
                    let _ = channel.send_bytes(b"x");
                    let _ = channel.send_end();
                }
                channel.send(&Reply::Fetched { files: 2, bytes: 2 })
            }
            _ => channel.send(&Reply::refused("bad_request", "not in this script")),
        };
    })
}

/// `yonder fetch` without `--fetch` takes the fetch directories of the run, so a staged file outside them stays on the
/// host. A run that names none can still have outputs and changes, which need no directory, but a staged file of it is
/// skipped, and nothing comes.
#[test]
fn a_fetch_without_directories_takes_the_ones_of_the_run() {
    let processes = FakeProcesses::default();
    let setup = agent_of_one_run(vec!["out/bt-runs".to_owned()]);
    let checkout = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "1-1", &checkout, &[])
        .expect_err("one file is outside the directories");
    assert_eq!(refusal.code, code::FETCH_FAILED);
    assert!(refusal.message.contains("scripts/hook.sh"), "{}", refusal.message);
    assert!(checkout.join("out/bt-runs/1/a.log").is_file());
    assert!(!checkout.join("scripts/hook.sh").exists());

    let setup = agent_of_one_run(Vec::new());
    let checkout = setup.checkout();
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .fetch(ALIAS, "1-1", &checkout, &[])
        .expect_err("no directories");
    assert_eq!(refusal.code, code::FETCH_FAILED);
    assert!(refusal.message.contains("2 of 2 files"), "{}", refusal.message);
    assert!(!checkout.exists());
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
