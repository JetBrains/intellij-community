//! The one thing this module does that no fake can stand in for is start a real process, and the suite has to do it
//! without a shell: `/bin/sh` is not there on Windows, where `bt.cmd` runs. So the child is this test binary
//! re-executed to run [`child_process`] alone, and a variable set on that one command tells it what to do. The
//! variable is set on the child's [`Command`] only, never on this process, so parallel tests cannot see each other's.

use std::io::Write;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use pretty_assertions::assert_eq;

use super::*;

const CHILD_VARIABLE: &str = "AVL_BT_OS_RUNTIME_CHILD";

// The markers the truncation case looks for: the head has to be gone and the tail has to be there.
const FLOOD_HEAD: &str = "the first line, which the tail must have dropped";
const FLOOD_TAIL: &str = "the last line, which the tail must have kept";

/// The child half of this binary. Run on its own it does nothing; re-executed by [`child`] it writes to the
/// descriptors directly and exits, so the harness never reports and none of its own summary reaches what the spawn
/// collected.
#[test]
#[ignore = "the child process of the spawn tests, which run it themselves"]
fn child_process() {
    let Ok(script) = std::env::var(CHILD_VARIABLE) else {
        return;
    };
    let mut stdout = io::stdout();
    let code = match script.as_str() {
        "streams" => {
            let _ = stdout.write_all(b"said on stdout\n");
            let _ = stdout.flush();
            let _ = io::stderr().write_all(b"said on stderr\n");
            3
        }
        "flood" => {
            // More than twice the retained limit, so the trimming runs and the assertions see its result rather than
            // one long buffer cut once at the end.
            let _ = writeln!(stdout, "{FLOOD_HEAD}");
            let line = format!("{}\n", "x".repeat(1023));
            for _ in 0..2 * MAX_RETAINED_OUTPUT / line.len() {
                let _ = stdout.write_all(line.as_bytes());
            }
            // On the same descriptor as the flood: two descriptors arrive in no particular order, and this line has
            // to be the last thing written.
            let _ = writeln!(stdout, "{FLOOD_TAIL}");
            0
        }
        "cwd" => {
            #[expect(
                clippy::disallowed_methods,
                reason = "the parent resolves the expected directory with the same call, so both sides agree on /private on macOS"
            )]
            let _ = write!(
                stdout,
                "cwd={}",
                std::env::current_dir()
                    .and_then(|dir| dir.canonicalize())
                    .map(|dir| dir.display().to_string())
                    .unwrap_or_default()
            );
            0
        }
        script if script.starts_with("git-isolation:") => {
            // The repository of the parent, which the inherited variables of git name a different one.
            let repo = Path::new(&script["git-isolation:".len()..]);
            write_in(repo, "module/Driver.kt", "first\n");
            git_in(repo, &["init", "-q", "-b", "main"], None);
            git_in(repo, &["add", "module/Driver.kt"], None);
            git_in(repo, &["commit", "-q", "-m", "first"], None);
            git_in(repo, &["branch", "published"], None);
            let read = OsRuntime::builder(repo).build().revision_text("HEAD", "module/Driver.kt");
            i32::from(read.ok().flatten().as_deref() != Some("first\n"))
        }
        script if script.starts_with("spawn-probe:") => {
            // The child and its own child run the same script. The marker file tells them apart: the first one makes
            // it and spawns, the second one finds it and reports what it inherited.
            let marker = Path::new(&script["spawn-probe:".len()..]);
            if marker.exists() {
                let _ = write!(
                    stdout,
                    "GIT_DIR {}",
                    if std::env::var_os("GIT_DIR").is_some() {
                        "inherited"
                    } else {
                        "removed"
                    }
                );
                0
            } else {
                std::fs::write(marker, "").expect("the marker of the probe");
                let ran = spawn_child(None, &child_argv(), None);
                let _ = write!(stdout, "{}", ran.output);
                ran.exit_code
            }
        }
        script => {
            if let Some(Ok(milliseconds)) = script.strip_prefix("sleep:").map(str::parse::<u64>) {
                thread::sleep(Duration::from_millis(milliseconds));
                0
            } else {
                let _ = writeln!(io::stderr(), "unknown child script {script}");
                98
            }
        }
    };
    let _ = stdout.flush();
    std::process::exit(code);
}

/// The argv that re-executes this binary as [`child_process`].
fn child_argv() -> Vec<OsString> {
    let exe = std::env::current_exe().expect("a test binary knows its own path");
    [
        exe.into_os_string(),
        "--exact".into(),
        "os_runtime::tests::child_process".into(),
        "--ignored".into(),
        "--nocapture".into(),
        "--test-threads=1".into(),
        "-q".into(),
    ]
    .into()
}

/// The child's command in `dir`, armed with the script it runs.
fn child(dir: Option<&Path>, script: &str) -> Command {
    let mut command = command_for(dir, &child_argv()).expect("the argv is not empty");
    command.env(CHILD_VARIABLE, script);
    command
}

#[test]
fn both_of_a_childs_streams_are_collected_and_its_exit_code_is_answered() {
    let result = run_to_end(child(None, "streams"), HEARTBEAT_INTERVAL, None);
    assert_eq!(result.exit_code, 3, "{}", result.output);
    // Merged, not one or the other: bazel puts its progress on stderr and its results on stdout, and a failed run
    // is diagnosed from both.
    assert!(result.output.contains("said on stdout"), "{}", result.output);
    assert!(result.output.contains("said on stderr"), "{}", result.output);
}

/// Apart, not merged: the stdout is the answer of the child, and each stderr line reaches the sink as it comes.
#[test]
fn a_streamed_child_answers_its_stdout_and_passes_on_its_stderr_lines() {
    let lines = Mutex::new(Vec::new());
    let sink = |line: &str| lines.lock().expect("the sink lock").push(line.to_owned());
    let result = stream_to_end(child(None, "streams"), &sink);
    assert_eq!(result.exit_code, 3, "{}", result.output);
    // The harness of the child writes its own header to the stdout first.
    assert!(result.output.ends_with("said on stdout\n"), "{}", result.output);
    assert!(!result.output.contains("said on stderr"), "{}", result.output);
    assert!(lines.lock().expect("the sink lock").iter().any(|line| line == "said on stderr"));
}

/// The tail, because a lane run's bazel output is megabytes and only its end says why it failed.
#[test]
fn output_is_truncated_to_its_tail() {
    let result = run_to_end(child(None, "flood"), HEARTBEAT_INTERVAL, None);
    assert_eq!(result.exit_code, 0, "{}", &result.output[..200.min(result.output.len())]);
    assert!(result.output.len() <= MAX_RETAINED_OUTPUT, "{}", result.output.len());
    // And close to the limit: the child wrote twice it, so a tail far short of the limit would mean the trimming
    // threw away more than the head.
    assert!(result.output.len() >= MAX_RETAINED_OUTPUT - 1024, "{}", result.output.len());
    assert!(!result.output.contains(FLOOD_HEAD), "nothing was truncated");
    assert!(
        result.output.contains(FLOOD_TAIL),
        "the tail was dropped, which is the half that says what happened"
    );
}

/// A cut may not leave the tail starting mid-character: the output travels inside a JSON envelope an agent reads,
/// and a split character would arrive there as a replacement character.
#[test]
fn the_tail_starts_between_characters_rather_than_inside_one() {
    // Two bytes per character against an odd limit, so the cut lands mid-character however the text is aligned.
    let mut tail = Tail::new(5);
    tail.push("é".repeat(8).as_bytes());
    assert_eq!(tail.text(), "éé");
}

/// 127 rather than an error, because that is a run outcome the classifier reads as infrastructure rather than one
/// every caller would have to re-classify.
#[test]
fn a_command_that_cannot_start_is_an_infra_exit() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let absent = dir.path().join("not-a-program");
    let result = spawn_child(None, &[absent.into_os_string()], None);
    assert_eq!(result.exit_code, 127, "{result:?}");
    assert!(result.output.contains("not-a-program"), "{result:?}");
    let empty = spawn_child(None, &[], None);
    assert_eq!(empty.exit_code, 127);
    assert_ne!(empty.output, "");
}

/// The controller's spawn, which is a refusal: a selector is resolved from inside a held lease.
#[test]
fn refusing_to_spawn_starts_nothing_and_says_what_it_was_asked() {
    let argv: Vec<OsString> = ["/bin/sh", "bazel.cmd", "test", "//plugins/air/..."].map(OsString::from).into();
    let result = refuse_spawn(&argv);
    assert_eq!(result.exit_code, 127);
    assert!(result.output.contains("must not spawn"), "{}", result.output);
    assert!(result.output.contains("//plugins/air/..."), "{}", result.output);
    // And a runtime that was never told what spawning means for it refuses too, rather than inheriting one.
    let dir = tempfile::tempdir().expect("a temporary directory");
    let silent = OsRuntime::builder(dir.path()).build().spawn(&[OsString::from("bazel")], None);
    assert_eq!(silent.exit_code, 127);
    assert!(silent.output.contains("must not spawn"), "{}", silent.output);
}

/// A long bazel run must not look hung, so the heartbeat beats while the child is alive, and never after it,
/// because a progress line arriving late would interleave with the digest.
#[test]
fn the_heartbeat_beats_while_the_child_runs_and_stops_with_it() {
    let beats = AtomicU64::new(0);
    let last = AtomicU64::new(0);
    let beat = |elapsed: u64| {
        beats.fetch_add(1, Ordering::SeqCst);
        last.store(elapsed, Ordering::SeqCst);
    };
    let result = run_to_end(child(None, "sleep:200"), Duration::from_millis(5), Some(&beat));
    assert_eq!(result.exit_code, 0, "{}", result.output);
    let seen = beats.load(Ordering::SeqCst);
    assert!(seen > 0, "nothing reported that the child was still running");
    assert!(last.load(Ordering::SeqCst) > 0);
    thread::sleep(Duration::from_millis(50));
    assert_eq!(
        beats.load(Ordering::SeqCst),
        seen,
        "the heartbeat kept beating after the child was gone"
    );
}

/// `bazel.cmd` is spawned with the checkout as its working directory, which is what makes a repo-relative target
/// pattern resolve.
#[test]
fn a_child_runs_in_the_directory_it_was_given() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let entered = run_to_end(child(Some(dir.path()), "cwd"), HEARTBEAT_INTERVAL, None);
    #[expect(
        clippy::disallowed_methods,
        reason = "the child reports its directory through the same call, so both sides agree on /private on macOS"
    )]
    let want = dir.path().canonicalize().expect("the directory exists");
    assert_eq!(entered.exit_code, 0, "{}", entered.output);
    assert!(entered.output.ends_with(&format!("cwd={}", want.display())), "{}", entered.output);
    // A directory it could not enter refuses rather than running somewhere else.
    let absent = spawn_child(Some(&dir.path().join("gone")), &child_argv(), None);
    assert_eq!(absent.exit_code, 127, "{absent:?}");
}

#[test]
fn the_runtime_answers_for_the_host_and_routes_its_two_sinks() {
    let said = Arc::new(Mutex::new(Vec::<String>::new()));
    let failed = Arc::new(Mutex::new(Vec::<String>::new()));
    let host = OsRuntime::builder("/repo")
        .write({
            let said = Arc::clone(&said);
            move |text| said.lock().unwrap().push(text.to_owned())
        })
        .write_error({
            let failed = Arc::clone(&failed);
            move |text| failed.lock().unwrap().push(text.to_owned())
        })
        .build();
    assert_eq!(host.repo_root(), Path::new("/repo"));
    assert_eq!(host.platform(), Platform::current());
    assert!(host.now_ms() > 0);
    host.write("digest");
    host.write_error("progress");
    assert_eq!(*said.lock().unwrap(), ["digest"]);
    assert_eq!(*failed.lock().unwrap(), ["progress"]);
}

/// The sink a consumer that passes nothing gets: one line per call, which is where the runtime's contract puts the
/// newline, in the sink rather than in its callers.
#[test]
fn the_default_sink_appends_the_newline_its_callers_do_not() {
    let mut buffer = Vec::new();
    write_line(&mut buffer, "first");
    write_line(&mut buffer, "second");
    assert_eq!(String::from_utf8_lossy(&buffer), "first\nsecond\n");
}

/// What splitting on `\n` yields, streamed: the empty last line of a file ending in a newline, a truncated one kept
/// whole, a `\r` stripped only before a newline, and nothing at all for a file that is not there.
#[test]
fn lines_are_split_the_way_the_bep_reader_expects() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let host = OsRuntime::builder(dir.path()).build();
    let file = dir.path().join("bep.json");
    std::fs::write(&file, "one\r\ntwo\n{\"trunc\r").expect("the file is writable");
    assert_eq!(host.read_lines(&file).collect::<Vec<_>>(), ["one", "two", "{\"trunc\r"]);
    std::fs::write(&file, "one\n").expect("the file is writable");
    assert_eq!(host.read_lines(&file).collect::<Vec<_>>(), ["one", ""]);
    assert_eq!(host.read_lines(&dir.path().join("gone")).count(), 0);
    // The temporary file is a fresh one, and removing it twice is fine.
    let temporary = host.temp_file("avl-bt-test");
    assert!(temporary.exists());
    host.remove(&temporary).expect("the file is removable");
    host.remove(&temporary).expect("an absent file is removed already");
}

fn git_available() -> bool {
    git_command().arg("--version").output().is_ok_and(|output| output.status.success())
}

/// The directory of a test repository, or `None` where no git is installed: nothing here can stand in for git.
fn repository_dir() -> Option<tempfile::TempDir> {
    if !git_available() {
        eprintln!("skipped: no git on this host");
        return None;
    }
    Some(tempfile::tempdir().expect("a temporary directory"))
}

/// Writes a file of the repository, with its directories.
fn write_in(repo: &Path, path: &str, text: &str) {
    let full = repo.join(path);
    std::fs::create_dir_all(full.parent().expect("a nested path")).expect("mkdir");
    std::fs::write(full, text).expect("write");
}

/// Runs git in `repo`, and answers its stdout without the line end. It is isolated from the configuration of the
/// host, which may sign, hook or template a repository.
fn git_in(repo: &Path, args: &[&str], index: Option<&Path>) -> String {
    let mut command = git_command();
    command
        .arg("-C")
        .arg(repo)
        .args(["-c", "user.name=bt", "-c", "user.email=bt@example.com"])
        .args(args)
        .env("GIT_CONFIG_GLOBAL", if cfg!(windows) { "NUL" } else { "/dev/null" })
        .env("GIT_CONFIG_NOSYSTEM", "1");
    if let Some(index) = index {
        command.env("GIT_INDEX_FILE", index);
    }
    let output = command.output().expect("git runs");
    assert!(output.status.success(), "git {args:?}: {output:?}");
    String::from_utf8(output.stdout).expect("UTF-8").trim_end().to_owned()
}

/// The listing is that of `git ls-files`, so the case needs a repository that git makes: a tracked file, an untracked
/// one, an ignored one, and a tracked file that is gone from the disk. Skipped where no git is installed.
#[test]
fn the_listing_holds_tracked_and_untracked_files_and_nothing_ignored() {
    let Some(dir) = repository_dir() else {
        return;
    };
    let repo = dir.path();
    let write = |path: &str, text: &str| write_in(repo, path, text);
    write(".gitignore", "out/\n");
    write("module/src/Tracked.kt", "tracked");
    write("module/src/Gone.kt", "gone");
    write("module/src/with space.kt", "untracked");
    write("module/out/Built.class", "ignored");
    write("other/Outside.kt", "outside the directory");
    git_in(repo, &["init", "-q"], None);
    git_in(
        repo,
        &[
            "add",
            ".gitignore",
            "module/src/Tracked.kt",
            "module/src/Gone.kt",
            "other/Outside.kt",
        ],
        None,
    );
    std::fs::remove_file(repo.join("module/src/Gone.kt")).expect("remove");

    let module = repo.join("module");
    let files = OsRuntime::builder(repo).build().list_files(&module).expect("git lists the module");

    assert_eq!(
        files,
        [module.join("src").join("Tracked.kt"), module.join("src").join("with space.kt"),]
    );
}

/// A link to the directory and a `..` in it give the files of its real path, with the paths spelled as the caller spelled
/// the directory. A link from outside the checkout reads the checkout too.
#[cfg(unix)]
#[test]
fn the_listing_follows_the_real_path_of_the_directory() {
    let Some(dir) = repository_dir() else {
        return;
    };
    let repo = dir.path().join("repo");
    write_in(&repo, "module/src/Tracked.kt", "tracked");
    write_in(&repo, "module/src/Untracked.kt", "untracked");
    git_in(&repo, &["init", "-q"], None);
    git_in(&repo, &["add", "module/src/Tracked.kt"], None);
    std::os::unix::fs::symlink("module", repo.join("link")).expect("a link");
    let outside = dir.path().join("outside");
    std::os::unix::fs::symlink(repo.join("module"), &outside).expect("a link");

    let runtime = OsRuntime::builder(&repo).build();
    for listed in [repo.join("link/src"), repo.join("module/src/../src"), outside.join("src")] {
        assert_eq!(
            runtime.list_files(&listed).expect("the directory is listed"),
            [listed.join("Tracked.kt"), listed.join("Untracked.kt")],
            "{}",
            listed.display()
        );
    }
}

/// A revision reads a file as the commit holds it, answers no text for a file that the commit does not hold, and is an
/// error when the checkout holds no such commit. The upstream base is where `HEAD` meets the upstream of its branch.
#[test]
fn a_revision_reads_its_files_and_the_upstream_base_is_the_merge_base() {
    if !git_available() {
        eprintln!("skipped: no git on this host");
        return;
    }
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path();
    let git = |args: &[&str]| -> String {
        let output = git_command()
            .arg("-C")
            .arg(repo)
            .args([
                "-c",
                "user.name=test",
                "-c",
                "user.email=test@example.com",
                "-c",
                "commit.gpgsign=false",
            ])
            .args(args)
            .env("GIT_CONFIG_GLOBAL", if cfg!(windows) { "NUL" } else { "/dev/null" })
            .env("GIT_CONFIG_NOSYSTEM", "1")
            .output()
            .expect("git runs");
        assert!(output.status.success(), "git {args:?}: {output:?}");
        String::from_utf8_lossy(&output.stdout).trim().to_owned()
    };
    std::fs::create_dir_all(repo.join("module")).expect("mkdir");
    std::fs::write(repo.join("module/Driver.kt"), "first\n").expect("write");
    git(&["init", "-q", "-b", "main"]);
    git(&["add", "module/Driver.kt"]);
    git(&["commit", "-q", "-m", "first"]);
    let base = git(&["rev-parse", "HEAD"]);
    git(&["branch", "published"]);
    git(&["branch", "-q", "--set-upstream-to=published"]);
    std::fs::write(repo.join("module/Driver.kt"), "second\n").expect("write");
    git(&["commit", "-q", "-am", "second"]);

    let host = OsRuntime::builder(repo).build();
    assert_eq!(host.upstream_base().expect("the branch has an upstream"), base);
    assert_eq!(
        host.revision_text(&base, "module/Driver.kt").expect("git reads"),
        Some("first\n".to_owned())
    );
    assert_eq!(
        host.revision_text("HEAD", "module/Driver.kt").expect("git reads"),
        Some("second\n".to_owned())
    );
    assert_eq!(host.revision_text(&base, "module/Gone.kt").expect("git reads"), None);
    host.revision_text("no-such-revision", "module/Driver.kt")
        .expect_err("the checkout holds no such revision");
    git(&["checkout", "-q", "--detach"]);
    host.upstream_base().expect_err("a detached HEAD without origin has no upstream");
}

/// A directory that cannot be listed is an error that names it, never an empty listing: an empty one reads as "this
/// directory holds no file", which is a different answer. The listing needs no git.
#[test]
fn a_directory_that_cannot_be_listed_is_an_error() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let absent = dir.path().join("gone");
    let error = OsRuntime::builder(&absent)
        .build()
        .list_files(&absent)
        .expect_err("an absent directory is not listable");
    assert!(error.to_string().contains(&absent.display().to_string()), "{error}");
}

/// Only a linked work tree has a tree: no git, a main checkout and a host slot answer none. The tree of a clean work
/// tree is the tree of HEAD. With local changes it is the tree that `git add -A` would stage: a changed file, a new
/// file, the files of a new directory, a deleted file, and no ignored file. A nested checkout is left out, both under
/// a tracked directory and under a new directory. The answer
/// is the same in each mode of `status.showUntrackedFiles`. The index of the work tree stays as it was.
#[test]
fn the_tree_of_a_checkout_holds_its_local_changes_and_leaves_its_index_alone() {
    let Some(dir) = repository_dir() else {
        return;
    };
    let main = dir.path().join("main");
    let main_runtime = OsRuntime::builder(&main).build();
    for (path, text) in [
        (".gitignore", "out/\n"),
        ("src/Changed.kt", "before"),
        ("src/Deleted.kt", "deleted"),
    ] {
        write_in(&main, path, text);
    }
    assert_eq!(main_runtime.checkout_tree().expect("no git is no error"), None);
    assert_eq!(main_runtime.git_common_path("bt/plan").expect("no git is no error"), None);

    git_in(&main, &["init", "-q"], None);
    git_in(&main, &["add", "-A"], None);
    git_in(&main, &["commit", "-q", "-m", "first"], None);
    assert_eq!(main_runtime.checkout_tree().expect("a main checkout is no error"), None);
    git_in(&main, &["worktree", "add", "-q", "--detach", "../lease"], None);
    let repo = &dir.path().join("lease");
    let runtime = OsRuntime::builder(repo).build();
    let head = git_in(repo, &["rev-parse", "HEAD"], None);
    let head_tree = git_in(repo, &["rev-parse", "HEAD^{tree}"], None);

    let clean = runtime
        .checkout_tree()
        .expect("git reads a clean checkout")
        .expect("a linked work tree");
    assert_eq!(
        clean,
        CheckoutTree {
            head: head.clone(),
            tree: head_tree.clone(),
            changed: Vec::new(),
        }
    );
    assert_eq!(runtime.commit_tree(&head).expect("the tree of HEAD"), head_tree);
    let host_slot = OsRuntime::builder(repo).host_slot(true).build();
    assert_eq!(host_slot.checkout_tree().expect("a host slot is no error"), None);
    let records = runtime.git_path("bd-checks.jsonl").expect("a git path");
    assert!(records.ends_with(".git/worktrees/lease/bd-checks.jsonl"), "{}", records.display());
    // The main checkout and its linked work tree name one file in the common git directory.
    let main_plan = main_runtime
        .git_common_path("bt/plan")
        .expect("git names the common git directory")
        .expect("a main checkout");
    let lease_plan = runtime
        .git_common_path("bt/plan")
        .expect("git names the common git directory")
        .expect("a linked work tree");
    assert!(main_plan.ends_with(".git/bt/plan"), "{}", main_plan.display());
    write_in(&main, ".git/bt/plan", "shared");
    assert_eq!(std::fs::read_to_string(&lease_plan).expect("the same file"), "shared");

    for (path, text) in [
        ("src/Changed.kt", "after"),
        ("src/with space.kt", "new"),
        ("out/Built.class", "ignored"),
        ("added/Top.kt", "new"),
        ("added/deep/File.kt", "new"),
        ("glob[1]/File.kt", "new"),
        ("added/out/Built.class", "ignored"),
        ("added/nested/Inner.kt", "nested"),
        ("only-nested/nested/Inner.kt", "nested"),
        ("only-ignored/out/Built.class", "ignored"),
        ("src/nested/Inner.kt", "nested"),
    ] {
        write_in(repo, path, text);
    }
    git_in(&repo.join("src/nested"), &["init", "-q"], None);
    git_in(&repo.join("added/nested"), &["init", "-q"], None);
    git_in(&repo.join("only-nested/nested"), &["init", "-q"], None);
    std::fs::remove_file(repo.join("src/Deleted.kt")).expect("remove");
    let status = git_in(repo, &["status", "--porcelain"], None);

    let dirty = runtime
        .checkout_tree()
        .expect("git reads a changed checkout")
        .expect("a git checkout");

    // Outside the checkout, so `add -A` does not stage the index file itself.
    let scratch = tempfile::tempdir().expect("a temporary directory");
    let expected_index = scratch.path().join("expected-index");
    git_in(repo, &["read-tree", "HEAD"], Some(&expected_index));
    git_in(
        repo,
        &[
            "add",
            "-A",
            "--",
            ".",
            ":(exclude)src/nested",
            ":(exclude)added/nested",
            ":(exclude)only-nested/nested",
        ],
        Some(&expected_index),
    );
    let expected = git_in(repo, &["write-tree"], Some(&expected_index));
    assert_eq!(
        dirty,
        CheckoutTree {
            head,
            tree: expected,
            changed: [
                "added/Top.kt",
                "added/deep/File.kt",
                "glob[1]/File.kt",
                "src/Changed.kt",
                "src/Deleted.kt",
                "src/with space.kt",
            ]
            .map(str::to_owned)
            .to_vec(),
        }
    );
    assert_ne!(dirty.tree, head_tree);
    assert_eq!(
        git_in(repo, &["status", "--porcelain"], None),
        status,
        "the index of the checkout changed"
    );

    git_in(repo, &["config", "status.showUntrackedFiles", "all"], None);
    assert_eq!(
        runtime
            .checkout_tree()
            .expect("git reads a changed checkout")
            .expect("a git checkout"),
        dirty,
        "the mode `all` gives another tree"
    );
}

/// The pathspecs of the untracked directories go in parts of at most the limit, one separator byte for each name, in
/// their order. A name over the limit is a part of its own.
#[test]
fn the_pathspecs_go_in_parts_of_at_most_the_limit() {
    assert_eq!(pathspec_parts(&[], 8), Vec::<&[&str]>::new());
    let dirs = ["a/", "bb/", "ccc/", "dddddddddd/", "e/"];
    assert_eq!(pathspec_parts(&dirs, 8), [&["a/", "bb/"][..], &["ccc/"], &["dddddddddd/"], &["e/"]]);
    assert_eq!(pathspec_parts(&dirs, 1024), [&dirs[..]]);
}

/// A new directory with a name that is not UTF-8 gives the same tree as `git add -A`: the files come from one status in
/// the mode `all`. Only Linux makes such a name, because macOS and Windows refuse it.
#[cfg(target_os = "linux")]
#[test]
fn a_new_directory_with_a_name_that_is_not_utf8_is_in_the_tree() {
    use std::ffi::OsStr;
    use std::os::unix::ffi::OsStrExt;

    let Some(dir) = repository_dir() else {
        return;
    };
    let main = dir.path().join("main");
    write_in(&main, "src/Kept.kt", "kept");
    git_in(&main, &["init", "-q"], None);
    git_in(&main, &["add", "-A"], None);
    git_in(&main, &["commit", "-q", "-m", "first"], None);
    git_in(&main, &["worktree", "add", "-q", "--detach", "../lease"], None);
    let repo = &dir.path().join("lease");
    let odd = repo.join(OsStr::from_bytes(b"odd-\xff"));
    std::fs::create_dir_all(odd.join("deep")).expect("mkdir");
    std::fs::write(odd.join("deep/File.kt"), "new").expect("write");
    write_in(repo, "added/Top.kt", "new");

    let tree = OsRuntime::builder(repo)
        .build()
        .checkout_tree()
        .expect("git reads a changed checkout")
        .expect("a linked work tree");

    let scratch = tempfile::tempdir().expect("a temporary directory");
    let expected_index = scratch.path().join("expected-index");
    git_in(repo, &["read-tree", "HEAD"], Some(&expected_index));
    git_in(repo, &["add", "-A"], Some(&expected_index));
    assert_eq!(tree.tree, git_in(repo, &["write-tree"], Some(&expected_index)));
    assert_eq!(tree.changed, ["added/Top.kt", "odd-\u{fffd}/deep/File.kt"]);
}

/// The kept evidence of a run: a copy creates its directories, a copy of a read-only bazel output is a file the
/// retention can delete on every platform, and removing a directory that is gone is no error.
#[test]
fn a_kept_copy_is_writable_and_its_run_directory_is_removable() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let source = dir.path().join("test.log");
    std::fs::write(&source, "the log").expect("the source is written");
    let mut permissions = std::fs::metadata(&source).expect("the source exists").permissions();
    permissions.set_readonly(true);
    std::fs::set_permissions(&source, permissions).expect("the source is read-only");
    let runtime = OsRuntime::builder(dir.path()).build();
    let run = dir.path().join("out/bt-runs/0000000001000");
    let copy = run.join("pkg/target_test/test.log");

    runtime.copy_file(&source, &copy).expect("the copy is made");

    assert_eq!(std::fs::read_to_string(&copy).expect("the copy is readable"), "the log");
    assert!(!std::fs::metadata(&copy).expect("the copy exists").permissions().readonly());
    runtime.remove_dir_all(&run).expect("the run directory is removed");
    assert!(!run.exists());
    runtime.remove_dir_all(&run).expect("a missing directory is no error");
}

/// The accio command line has the shape of the bazel one: the wrapper of the checkout through the shell of the
/// platform, with the separator of the platform, then the project and the format.
#[test]
fn the_accio_command_runs_the_wrapper_of_the_checkout() {
    let text = |command: Vec<OsString>| command.iter().map(|part| part.to_string_lossy().into_owned()).collect::<Vec<_>>();
    assert_eq!(
        text(accio_command(
            Path::new("/repo"),
            Platform::Darwin,
            ACCIO_WRAPPERS[0],
            &["symbol", "FooTest", "--kind", "class"]
        )),
        [
            "/bin/sh",
            "/repo/community/tools/accio.cmd",
            "symbol",
            "FooTest",
            "--kind",
            "class",
            "--project",
            "/repo",
            "--format",
            "json"
        ]
    );
    assert_eq!(
        text(accio_command(
            Path::new(r"C:\repo\"),
            Platform::Windows,
            ACCIO_WRAPPERS[1],
            &["modules"]
        )),
        [
            "cmd.exe",
            "/c",
            r"C:\repo\tools\accio.cmd",
            "modules",
            "--project",
            r"C:\repo\",
            "--format",
            "json"
        ]
    );
}

/// A checkout without the wrapper is refused, and no accio of the PATH stands in for it.
#[test]
fn a_checkout_without_an_accio_wrapper_is_refused() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let runtime = OsRuntime::resolver(dir.path(), |_| {});
    let refused = runtime.accio(&["symbol", "FooTest"]).expect_err("a refusal");
    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        (crate::accio::ACCIO_FAILED, crate::exit::INFRA)
    );
    assert!(
        refused
            .message
            .ends_with("has no accio wrapper: community/tools/accio.cmd or tools/accio.cmd"),
        "{}",
        refused.message
    );
}

/// The resolver runtime refuses every spawn, and it still runs accio: in the checkout root, with its stdout as the
/// answer. The refusal of a failed call names the tail of its stderr and the tail of its stdout, where accio writes its
/// error, and a call without output says so. Each call may wait [`ACCIO_WAIT_SECS`] for the index.
#[cfg(unix)]
#[test]
fn the_resolver_runtime_runs_the_accio_wrapper_in_the_checkout_root() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let wrapper = dir.path().join("tools/accio.cmd");
    std::fs::create_dir_all(wrapper.parent().expect("a parent")).expect("the tools directory");
    std::fs::write(
        &wrapper,
        concat!(
            "if [ \"$1\" = fail ]; then echo 'no index' >&2; exit 3; fi\n",
            "if [ \"$1\" = stdout ]; then echo 'Error: --project: No such file'; exit 1; fi\n",
            "if [ \"$1\" = silent ]; then exit 1; fi\n",
            "if [ \"$1\" = wait ]; then echo \"$ACCIO_WAIT\"; exit 0; fi\n",
            "pwd -P\nprintf '%s\\n' \"$@\"\n",
        ),
    )
    .expect("the wrapper");
    let runtime = OsRuntime::resolver(dir.path(), |_| {});
    let answered = runtime.accio(&["symbol", "FooTest"]).expect("an answer");
    #[expect(
        clippy::disallowed_methods,
        reason = "the wrapper prints the real path of its working directory, which is under /private on macOS"
    )]
    let real_root = std::fs::canonicalize(dir.path()).expect("the real path");
    let project = dir.path().to_string_lossy().into_owned();
    assert_eq!(
        answered.lines().collect::<Vec<_>>(),
        [
            &*real_root.to_string_lossy(),
            "symbol",
            "FooTest",
            "--project",
            &project,
            "--format",
            "json"
        ]
    );
    let refused = runtime.accio(&["fail"]).expect_err("a refusal");
    assert!(refused.message.ends_with("exited with 3\nstderr:\nno index"), "{}", refused.message);
    let refused = runtime.accio(&["stdout"]).expect_err("a refusal");
    assert!(
        refused.message.ends_with("exited with 1\nstdout:\nError: --project: No such file"),
        "{}",
        refused.message
    );
    assert_eq!(runtime.accio(&["wait"]).expect("an answer").trim(), ACCIO_WAIT_SECS.to_string());
    let refused = runtime.accio(&["silent"]).expect_err("a refusal");
    assert!(
        refused.message.ends_with("exited with 1, and printed nothing on stdout or stderr"),
        "{}",
        refused.message
    );
}

/// A parent git, such as `git rebase --exec`, exports the variables that name its repository. A test that runs git
/// under them, and the runtime itself, still write only their own repository, so the repository of the parent keeps
/// its config, its refs and its index.
#[test]
fn an_inherited_git_dir_reaches_no_git_of_the_runtime_or_its_tests() {
    let Some(dir) = repository_dir() else {
        return;
    };
    let decoy = dir.path().join("decoy");
    let own = dir.path().join("own");
    std::fs::create_dir_all(&decoy).expect("mkdir");
    git_in(&decoy, &["init", "-q", "-b", "main"], None);
    let config = || std::fs::read_to_string(decoy.join(".git/config")).expect("the config of the decoy");
    let before = config();

    let mut command = child(None, &format!("git-isolation:{}", own.display()));
    command
        .env("GIT_DIR", decoy.join(".git"))
        .env("GIT_WORK_TREE", &decoy)
        .env("GIT_INDEX_FILE", decoy.join(".git/index"))
        .env("GIT_COMMON_DIR", decoy.join(".git"));
    let ran = command.output().expect("the child runs");
    assert!(ran.status.success(), "{ran:?}");

    assert_eq!(config(), before, "the child changed the config of the decoy");
    assert_eq!(git_in(&decoy, &["for-each-ref"], None), "", "the child wrote a ref of the decoy");
    assert!(!decoy.join(".git/index").exists(), "the child wrote the index of the decoy");
    assert_eq!(git_in(&own, &["log", "--format=%s"], None), "first");
    assert_eq!(
        git_in(&own, &["branch", "--list", "published", "--format=%(refname)"], None),
        "refs/heads/published"
    );
}

/// bt runs under a parent git too, such as a `git rebase --exec` step. A child of the spawn, such as bazel, a test JVM
/// or a `cargo test`, gets none of the variables of that git, so its own git calls stay in their own repository.
#[test]
fn a_spawned_child_inherits_no_git_dir_of_the_parent() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let mut command = child(None, &format!("spawn-probe:{}", dir.path().join("marker").display()));
    command.env("GIT_DIR", dir.path().join("decoy.git"));
    let ran = command.output().expect("the child runs");
    assert!(ran.status.success(), "{ran:?}");
    // The harness of each test binary prints its own header first, so only the end is the answer of the probe.
    let stdout = String::from_utf8_lossy(&ran.stdout);
    assert!(stdout.ends_with("GIT_DIR removed"), "{stdout}");
}
