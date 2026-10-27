//! The one thing this module does that no fake can stand in for is start a real process, and the suite has to do it
//! without a shell: `/bin/sh` is not there on Windows, where `bt.cmd` runs. So the child is this test binary
//! re-executed to run [`child_process`] alone, and a variable set on that one command tells it what to do. The
//! variable is set on the child's [`Command`] only, never on this process, so parallel tests cannot see each other's.

use std::io::Write;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

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

/// A child that writes nothing for the idle limit is stopped, and a child that ends in time answers as usual.
#[test]
fn a_watched_child_that_stays_silent_is_stopped() {
    let started = Instant::now();
    let silent = watch_to_end(child(None, "sleep:20000"), Duration::from_millis(300));
    assert_eq!(silent.exit_code, STOPPED_IDLE, "{}", silent.output);
    assert!(
        silent.output.contains("wrote nothing for 0 s, so it was stopped"),
        "{}",
        silent.output
    );
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());

    let speaking = watch_to_end(child(None, "streams"), Duration::from_secs(20));
    assert_eq!(speaking.exit_code, 3, "{}", speaking.output);
    assert!(speaking.output.contains("said on stdout"), "{}", speaking.output);
}

/// The stall is often in a grandchild that holds the pipe, as the ssh of a git push does. Its group goes with it.
#[cfg(unix)]
#[test]
fn a_watched_child_is_stopped_with_its_grandchildren() {
    let started = Instant::now();
    let mut command = Command::new("/bin/sh");
    command.args(["-c", "sleep 30; echo late"]).stdin(Stdio::null());
    let stopped = watch_to_end(command, Duration::from_millis(300));
    assert_eq!(stopped.exit_code, STOPPED_IDLE, "{}", stopped.output);
    assert!(!stopped.output.contains("late"), "{}", stopped.output);
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
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
    assert!(!empty.output.is_empty());
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
    Command::new("git")
        .arg("--version")
        .output()
        .is_ok_and(|output| output.status.success())
}

/// The listing is git's, so the case needs a real repository: a tracked file, an untracked one, an ignored one, and
/// a tracked file that is gone from the disk. Skipped where no git is installed, because nothing here can stand in
/// for it.
#[test]
fn the_listing_holds_tracked_and_untracked_files_and_nothing_ignored() {
    if !git_available() {
        eprintln!("skipped: no git on this host");
        return;
    }
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path();
    let write = |path: &str, text: &str| {
        let full = repo.join(path);
        std::fs::create_dir_all(full.parent().expect("a nested path")).expect("mkdir");
        std::fs::write(full, text).expect("write");
    };
    let git = |args: &[&str]| {
        let output = Command::new("git")
            .arg("-C")
            .arg(repo)
            .args(args)
            // Isolated from the host's own configuration, which may sign, hook or template a repository.
            .env("GIT_CONFIG_GLOBAL", if cfg!(windows) { "NUL" } else { "/dev/null" })
            .env("GIT_CONFIG_NOSYSTEM", "1")
            .output()
            .expect("git runs");
        assert!(output.status.success(), "git {args:?}: {output:?}");
    };
    write(".gitignore", "out/\n");
    write("module/src/Tracked.kt", "tracked");
    write("module/src/Gone.kt", "gone");
    write("module/src/with space.kt", "untracked");
    write("module/out/Built.class", "ignored");
    write("other/Outside.kt", "outside the directory");
    git(&["init", "-q"]);
    git(&[
        "add",
        ".gitignore",
        "module/src/Tracked.kt",
        "module/src/Gone.kt",
        "other/Outside.kt",
    ]);
    std::fs::remove_file(repo.join("module/src/Gone.kt")).expect("remove");

    let module = repo.join("module");
    let files = OsRuntime::builder(repo).build().list_files(&module).expect("git lists the module");

    assert_eq!(
        files,
        [module.join("src").join("Tracked.kt"), module.join("src").join("with space.kt"),]
    );
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
        let output = Command::new("git")
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

/// A directory git cannot list is an error that names it, never an empty listing: an empty one reads as "this
/// directory holds no file", which is a different answer.
#[test]
fn a_directory_git_cannot_list_is_an_error() {
    if !git_available() {
        eprintln!("skipped: no git on this host");
        return;
    }
    let dir = tempfile::tempdir().expect("a temporary directory");
    let absent = dir.path().join("gone");
    let error = OsRuntime::builder(&absent)
        .build()
        .list_files(&absent)
        .expect_err("an absent directory is not listable");
    assert!(error.to_string().contains(&absent.display().to_string()), "{error}");
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
