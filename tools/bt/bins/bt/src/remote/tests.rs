use std::ffi::OsString;

use pretty_assertions::assert_eq;

use super::*;
use crate::execute::execute;
use bt_core::fake::{FakeRuntime, REPO_ROOT, area_files, refusal};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// A green payload as the host writes it with `--json`.
const PAYLOAD: &str = r#"{"status":"PASS","exitCode":0,"durationMs":5,"cachedTargets":0,"ranTargets":1,"targets":[],"failures":[],"flaky":[],"errors":[],"degraded":false}"#;

fn ok(output: &str) -> SpawnResult {
    answered(0, output)
}

fn answered(exit_code: i32, output: &str) -> SpawnResult {
    SpawnResult {
        exit_code,
        output: output.to_owned(),
    }
}

/// A checkout with `bt.json`, whose spawns answer `results` in order.
fn checkout(results: impl IntoIterator<Item = SpawnResult>) -> FakeRuntime {
    let fake = FakeRuntime::new([]);
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    fake.state().spawn_results.extend(results);
    fake
}

/// The answers of a clean checkout up to the run on the host: `git status`, `git rev-parse` and `git push`.
fn clean_and_pushed() -> [SpawnResult; 3] {
    [ok(""), ok(&format!("{COMMIT}\n")), ok("")]
}

fn run_id() -> String {
    // The fake clock starts at 1000.
    format!("1000-{}", std::process::id())
}

fn outcome(argv: &[&str], fake: &FakeRuntime) -> Outcome {
    execute(argv, fake).unwrap_or_else(|failure| panic!("{argv:?} refused: {failure:?}"))
}

#[test]
fn the_flags_of_the_host_stay_here_and_the_rest_goes_to_the_host() {
    let argv: Vec<OsString> = ["--host", "studio", "FooTest", "--host-repo=~/r", "--json", "--", "--host", "x"]
        .into_iter()
        .map(OsString::from)
        .collect();
    assert_eq!(host_args(&argv), ["FooTest", "--json", "--", "--host", "x"]);

    let after: Vec<OsString> = ["--lane", "fast", "--host=studio", "--host-idle-hours", "2"]
        .into_iter()
        .map(OsString::from)
        .collect();
    assert_eq!(host_args(&after), ["--lane", "fast"]);
}

/// Each code of bt keeps its meaning; ssh's own 255, an interrupt and an unknown code are infrastructure.
#[test]
fn the_exit_code_of_the_host_maps_one_to_one() {
    for code in [0, 2, 3, 4, 5, 6] {
        assert_eq!(i32::from(local_exit(code)), code);
    }
    for code in [1, 7, 130, 255, -1] {
        assert_eq!(local_exit(code), exit::INFRA, "{code}");
    }
}

#[test]
fn a_run_pushes_head_runs_the_host_script_and_answers_its_payload() {
    let fake = checkout(clean_and_pushed().into_iter().chain([ok(&format!("{PAYLOAD}\n")), answered(1, "")]));
    let answer = outcome(&["AgentLaunchTokensTest", "--host", "studio", "--json"], &fake);
    assert_eq!(answer.exit_code, exit::GREEN);
    assert_eq!(answer.json.map(|payload| payload.ran_targets), Some(1));
    assert_eq!(answer.text, "");

    let id = run_id();
    let spawned = fake.spawned();
    assert_eq!(spawned.len(), 5, "{spawned:#?}");
    assert_eq!(spawned[0], ["git", "status", "--porcelain"]);
    assert_eq!(spawned[1], ["git", "rev-parse", "--verify", "HEAD"]);
    assert_eq!(
        spawned[2],
        [
            "git",
            "-c",
            "core.sshCommand=ssh -o BatchMode=yes",
            "push",
            "--quiet",
            "studio:projects/idea-1",
            &format!("{COMMIT}:refs/bt-remote/{id}"),
        ]
    );
    assert_eq!(spawned[3][..4], ["ssh", "-o", "BatchMode=yes", "studio"]);
    let holder = holder(&fake);
    let expected_tail = format!("bt-remote 'run' '~/projects/idea-1' '{holder}' '{id}' '{COMMIT}' '8' 'AgentLaunchTokensTest' '--json'");
    assert!(spawned[3][4].starts_with("sh -c '"), "{}", spawned[3][4]);
    assert!(spawned[3][4].ends_with(&expected_tail), "{}", spawned[3][4]);
    assert_eq!(spawned[4][4], format!("ls -1 .cache/bt-remote/runs/{id} 2>/dev/null"));
}

/// The host script travels inside single quotes, so its own quotes must survive the login shell of the host.
#[test]
fn the_host_command_quotes_the_script_and_each_argument() {
    let host = Host {
        alias: "studio",
        repo: "/abs/repo",
        idle_hours: 3,
    };
    let script = HostScript {
        mode: "run",
        holder: "h",
        id: "7-1",
        commit: "c0",
    };
    let command = host_command(&host, &script, &["it's".to_owned()]);
    assert_eq!(
        command,
        format!(
            "sh -c {} bt-remote 'run' '/abs/repo' 'h' '7-1' 'c0' '3' 'it'\\''s'",
            posix_shell_quote(&host_script())
        )
    );
    assert!(!host_script().contains("# The host half"), "the comments stay here");
    assert!(host_script().contains("trap finish EXIT"));
    assert_eq!(host_path_word("~/projects/idea 1"), "~/'projects/idea 1'");
    assert_eq!(host_path_word("/abs"), "'/abs'");
}

#[test]
fn a_checkout_with_local_changes_is_refused_before_the_push() {
    let fake = checkout([ok(" M community/tools/bt/README.md\n?? notes.txt\n")]);
    let failure = refusal(execute(["--host", "studio", "--lane", "fast"], &fake));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_local_changes", exit::USAGE));
    assert!(failure.message.contains("?? notes.txt"), "{}", failure.message);
    assert_eq!(fake.spawned().len(), 1, "only git status ran: {:#?}", fake.spawned());
}

#[test]
fn a_failed_push_is_infrastructure_and_runs_nothing_on_the_host() {
    let fake = checkout([ok(""), ok(COMMIT), answered(128, "fatal: Could not read from remote repository.")]);
    let failure = refusal(execute(["--host", "studio", "--lane", "fast"], &fake));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_infra", exit::INFRA));
    assert!(failure.message.contains("Could not read"), "{}", failure.message);
    assert_eq!(fake.spawned().len(), 3);
}

#[test]
fn host_without_a_selector_is_a_usage_failure() {
    let fake = checkout([]);
    let failure = refusal(execute(["--host", "studio"], &fake));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(fake.spawned().is_empty());
}

/// A failed run answers the code of the host, and its kept logs come back to `out/bt-runs` here.
#[test]
fn a_failed_run_answers_the_host_code_and_copies_the_kept_logs() {
    let digest = "FAIL  1 of 3 tests failed\nkept   out/bt-runs/0000000002000  (the logs of each failed shard)\n";
    let fake = checkout(
        clean_and_pushed()
            .into_iter()
            .chain([answered(3, digest), ok("0000000002000\n"), ok(""), ok("")]),
    );
    let answer = outcome(&["--host", "studio", "FooTest"], &fake);
    assert_eq!(answer.exit_code, exit::TEST_FAILED);
    let local = format!("{REPO_ROOT}/out/bt-runs/0000000002000");
    assert!(answer.text.starts_with("FAIL  1 of 3"), "{}", answer.text);
    assert!(
        answer
            .text
            .ends_with(&format!("copied {local}  (the kept logs of the run on studio)")),
        "{}",
        answer.text
    );

    let id = run_id();
    let spawned = fake.spawned();
    assert_eq!(
        spawned[5],
        [
            "scp",
            "-rq",
            "-o",
            "BatchMode=yes",
            &format!("studio:.cache/bt-remote/runs/{id}/0000000002000"),
            &format!("{REPO_ROOT}/out/bt-runs"),
        ]
    );
    assert_eq!(spawned[6][4], format!("rm -rf .cache/bt-remote/runs/{id}"));
}

/// When ssh itself fails, the host script may never have run, so the ref is deleted from here.
#[test]
fn a_failed_ssh_deletes_the_pushed_ref_and_is_infrastructure() {
    let fake = checkout(clean_and_pushed().into_iter().chain([
        answered(255, ""),
        answered(255, "ssh: connect to host studio: Operation timed out"),
        answered(255, ""),
    ]));
    let failure = refusal(execute(["--host", "studio", "--host-repo", "/w/idea", "FooTest"], &fake));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_run_failed", exit::INFRA));
    let spawned = fake.spawned();
    assert_eq!(spawned[4][4], format!("git -C '/w/idea' update-ref -d refs/bt-remote/{}", run_id()));
    let errors = fake.state().stderr.join("\n");
    assert!(errors.contains("Operation timed out"), "{errors}");
}

/// A refusal on the host writes no digest: the host printed it on the stderr, and the code stays the host's.
#[test]
fn a_refusal_on_the_host_keeps_its_code() {
    let fake = checkout(clean_and_pushed().into_iter().chain([answered(2, ""), answered(1, "")]));
    let failure = refusal(execute(["--host", "studio", "--json", "NoSuchTest"], &fake));
    assert_eq!(failure.exit, exit::USAGE);
}

/// A release pushes nothing and needs no clean checkout: it only returns the lease of this checkout on the host.
#[test]
fn a_release_returns_the_lease_of_this_checkout_and_runs_nothing_else() {
    let fake = checkout([ok("released the lease L1 of /pool/1/idea-1\n")]);
    let answer = outcome(&["--host", "studio", "--release"], &fake);
    assert_eq!(answer.exit_code, exit::GREEN);
    assert_eq!(answer.text, "released the lease L1 of /pool/1/idea-1");
    let spawned = fake.spawned();
    assert_eq!(spawned.len(), 1, "{spawned:#?}");
    let expected = format!("bt-remote 'release' '~/projects/idea-1' '{}' 'release-1000' '' '8'", holder(&fake));
    assert!(spawned[0][4].ends_with(&expected), "{}", spawned[0][4]);

    let failure = refusal(execute(["--release"], &checkout([])));
    assert_eq!(failure.exit, exit::USAGE);
}

/// One holder per checkout: the same checkout keeps its slot, and another checkout gets its own.
#[test]
fn each_checkout_has_its_own_stable_holder() {
    let first = FakeRuntime::on(bt_core::runtime::Platform::Darwin, "/pool/1/idea-1", []);
    let second = FakeRuntime::on(bt_core::runtime::Platform::Darwin, "/pool/2/idea-1", []);
    assert_eq!(holder(&first), holder(&first));
    assert_ne!(holder(&first), holder(&second));
    assert!(holder(&first).starts_with("bt-remote-"), "{}", holder(&first));
    assert_eq!(holder(&first).len(), "bt-remote-".len() + 12);
}

/// The host half itself, run by a real `sh` with stub `bd`, `git` and `bt.cmd` on the PATH. The stub `bd` keeps the
/// ids of the live leases in a file, one per line.
#[cfg(unix)]
mod host_script {
    use std::fs;
    use std::io::Read;
    use std::os::unix::fs::PermissionsExt;
    use std::path::PathBuf;
    use std::process::{Child, Command, Stdio};
    use std::time::{Duration, Instant};

    use super::HOST_SCRIPT;

    const BD: &str = r#"#!/bin/sh
echo "bd $*" >>"$BT_TEST_LOG"
live="$BT_TEST_LOG.leases"
case "$*" in
  *"lease get"*)
    if [ -n "${BT_TEST_LEASE_FAILS:-}" ]; then echo '{"error":"no free slot"}'; exit 4; fi
    id=L$(( $(cat "$live" 2>/dev/null | wc -l) + 1 ))
    echo "$id" >>"$live"
    printf '{"path":"%s","lease_id":"%s","reused":true}\n' "$BT_TEST_SLOT" "$id" ;;
  *"lease status"*)
    printf '{"slots":['
    while read -r id; do printf '{"lease":{"lease_id":"%s"}},' "$id"; done <"$live" 2>/dev/null
    printf ']}\n' ;;
  *"lease return"*)
    id=$(printf '%s\n' "$*" | sed -n 's/.*--lease-id \([^ ]*\).*/\1/p')
    if [ -e "$live" ]; then sed "/^$id\$/d" "$live" >"$live.new"; mv "$live.new" "$live"; fi ;;
esac
"#;

    const GIT: &str = r#"#!/bin/sh
echo "git $*" >>"$BT_TEST_LOG"
case "$*" in *rev-parse*) echo HEAD0 ;; esac
"#;

    const BT: &str = r#"#!/bin/sh
echo "bt $*" >>"$BT_TEST_LOG"
mkdir -p out/bt-runs/0000000002000
echo progress >&2
if [ "${BT_TEST_MODE:-}" = hang ]; then : >"$BT_TEST_LOG.started"; sleep 60; fi
echo "the digest"
exit "${BT_TEST_EXIT:-0}"
"#;

    struct Fixture {
        _dir: tempfile::TempDir,
        root: PathBuf,
    }

    impl Fixture {
        fn new() -> Self {
            let dir = tempfile::tempdir().expect("a temporary directory");
            let root = dir.path().to_path_buf();
            for (path, text) in [("bin/bd", BD), ("bin/git", GIT), ("slot/community/tools/bt.cmd", BT)] {
                let file = root.join(path);
                fs::create_dir_all(file.parent().expect("a parent")).expect("a directory");
                fs::write(&file, text).expect("a stub");
                fs::set_permissions(&file, fs::Permissions::from_mode(0o755)).expect("an executable stub");
            }
            fs::create_dir_all(root.join("slot/out/bt-runs/0000000001000")).expect("an older run");
            fs::create_dir_all(root.join("home")).expect("a home");
            Self { _dir: dir, root }
        }

        fn path(&self, relative: &str) -> PathBuf {
            self.root.join(relative)
        }

        fn log(&self) -> String {
            fs::read_to_string(self.path("log")).unwrap_or_default()
        }

        fn live_leases(&self) -> String {
            fs::read_to_string(self.path("log.leases")).unwrap_or_default()
        }

        fn lease_file(&self, holder: &str) -> PathBuf {
            self.path(&format!("home/.cache/bt-remote/leases/{holder}.lease"))
        }

        /// The script with its stdin held open, as ssh holds it while the caller lives.
        fn spawn(&self, mode: &str, env: &[(&str, &str)]) -> Child {
            self.spawn_as(mode, "H", env)
        }

        fn spawn_as(&self, mode: &str, holder: &str, env: &[(&str, &str)]) -> Child {
            let path = format!("{}:/usr/bin:/bin", self.path("bin").display());
            let mut command = Command::new("/bin/sh");
            command
                .args([
                    "-c",
                    HOST_SCRIPT,
                    "bt-remote",
                    mode,
                    "~/repo",
                    holder,
                    "ID",
                    "C1",
                    "8",
                    "FooTest",
                    "--json",
                ])
                .env("HOME", self.path("home"))
                .env("PATH", path)
                .env("BT_TEST_LOG", self.path("log"))
                .env("BT_TEST_SLOT", self.path("slot"))
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped());
            for (name, value) in env {
                command.env(name, value);
            }
            command.spawn().expect("sh starts")
        }

        /// One whole run of the script while its caller lives.
        fn run(&self, mode: &str, env: &[(&str, &str)]) -> (i32, String) {
            self.run_as(mode, "H", env)
        }

        fn run_as(&self, mode: &str, holder: &str, env: &[(&str, &str)]) -> (i32, String) {
            let mut child = self.spawn_as(mode, holder, env);
            let stdin = child.stdin.take();
            let answer = wait(&mut child);
            drop(stdin);
            answer
        }
    }

    fn wait(child: &mut Child) -> (i32, String) {
        let deadline = Instant::now() + Duration::from_secs(30);
        let status = loop {
            if let Some(status) = child.try_wait().expect("a status") {
                break status;
            }
            assert!(Instant::now() < deadline, "the host script did not exit");
            std::thread::sleep(Duration::from_millis(50));
        };
        let mut stdout = String::new();
        child
            .stdout
            .take()
            .expect("a stdout")
            .read_to_string(&mut stdout)
            .expect("the stdout");
        (status.code().unwrap_or(-1), stdout)
    }

    #[track_caller]
    fn assert_logged(log: &str, lines: &[String]) {
        for line in lines {
            assert!(log.lines().any(|logged| logged == line), "no {line:?} in:\n{log}");
        }
    }

    fn ref_deleted(fixture: &Fixture) -> String {
        format!("git -C {} update-ref -d refs/bt-remote/ID", fixture.path("home/repo").display())
    }

    #[test]
    fn a_run_answers_the_stdout_and_code_of_bt_stages_its_new_runs_and_keeps_the_lease() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run("run", &[("BT_TEST_EXIT", "3")]), (3, "the digest\n".to_owned()));
        let log = fixture.log();
        assert_logged(
            &log,
            &[
                format!("bd lease get --holder H -C {}", fixture.path("home/repo").display()),
                format!("git -C {} checkout -q -f --detach C1", fixture.path("slot").display()),
                "bt FooTest --json".to_owned(),
                ref_deleted(&fixture),
            ],
        );
        assert!(!log.contains("lease return"), "the lease stays warm:\n{log}");
        let recorded = fs::read_to_string(fixture.lease_file("H")).expect("the lease file");
        assert!(
            recorded.starts_with(&format!("lease_id=L1\nslot={}\nused=", fixture.path("slot").display())),
            "{recorded}"
        );
        assert!(
            !fixture.path("home/.cache/bt-remote/leases/H.lock").exists(),
            "the lock is released"
        );
        assert!(
            fixture.path("home/.cache/bt-remote/runs/ID/0000000002000").is_dir(),
            "the new run is staged"
        );
        assert!(
            !fixture.path("slot/out/bt-runs/0000000002000").exists(),
            "the new run left the slot"
        );
        assert!(fixture.path("slot/out/bt-runs/0000000001000").is_dir(), "an older run stays");
    }

    /// The second run of a holder finds its lease warm: no second `bd lease get`.
    #[test]
    fn a_second_run_reuses_the_warm_lease() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run("run", &[]).0, 0);
        assert_eq!(fixture.run("run", &[]).0, 0);
        let log = fixture.log();
        assert_eq!(log.matches("lease get").count(), 1, "{log}");
        assert_eq!(log.matches("bt FooTest").count(), 2, "{log}");
    }

    /// Two holders take two leases, also when the lease file of the other holder is the last one the sweep reads.
    #[test]
    fn two_holders_take_two_leases() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run_as("run", "A", &[]).0, 0);
        assert_eq!(fixture.run_as("run", "B", &[]).0, 0);
        assert_eq!(fixture.log().matches("lease get").count(), 2, "{}", fixture.log());
        let lease_of = |holder: &str| fs::read_to_string(fixture.lease_file(holder)).expect("a lease file");
        assert!(lease_of("A").starts_with("lease_id=L1\n"), "{}", lease_of("A"));
        assert!(lease_of("B").starts_with("lease_id=L2\n"), "{}", lease_of("B"));
    }

    /// A lease that bd no longer knows is replaced by a new one.
    #[test]
    fn a_lease_that_bd_forgot_is_taken_again() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run("run", &[]).0, 0);
        fs::write(fixture.path("log.leases"), "").expect("bd forgets the lease");
        assert_eq!(fixture.run("run", &[]).0, 0);
        assert_eq!(fixture.log().matches("lease get").count(), 2, "{}", fixture.log());
    }

    /// The release gives the commit up, because the local checkout keeps it, and forgets the lease file.
    #[test]
    fn a_release_returns_the_lease_with_the_commit_given_up() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run("run", &[]).0, 0);
        let (code, stdout) = fixture.run("release", &[]);
        assert_eq!(code, 0);
        assert_eq!(stdout, format!("released the lease L1 of {}\n", fixture.path("slot").display()));
        assert_logged(
            &fixture.log(),
            &[format!(
                "bd -C {} lease return --lease-id L1 --confirm-preserved",
                fixture.path("slot").display()
            )],
        );
        assert!(!fixture.lease_file("H").exists());
        assert_eq!(fixture.live_leases(), "");
        assert_eq!(fixture.run("release", &[]), (0, "H holds no lease on this host\n".to_owned()));
    }

    /// A lease of another holder that no run used for the idle hours goes back, after its slot went back to the HEAD
    /// of the main repository, so the return gives up nothing. A recent one stays.
    #[test]
    fn a_run_returns_the_idle_lease_of_another_holder() {
        let fixture = Fixture::new();
        let leases = fixture.path("home/.cache/bt-remote/leases");
        fs::create_dir_all(&leases).expect("the lease directory");
        fs::write(leases.join("OLD.lease"), "lease_id=L8\nslot=/pool/2\nused=0\n").expect("an idle lease");
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("a clock")
            .as_secs();
        fs::write(leases.join("NEW.lease"), format!("lease_id=L9\nslot=/pool/3\nused={now}\n")).expect("a recent lease");
        fs::write(fixture.path("log.leases"), "L8\nL9\n").expect("the live leases");
        assert_eq!(fixture.run("run", &[]).0, 0);
        let log = fixture.log();
        assert_logged(
            &log,
            &[
                "git -C /pool/2 checkout -q -f --detach HEAD0".to_owned(),
                "bd -C /pool/2 lease return --lease-id L8".to_owned(),
            ],
        );
        assert!(!log.contains("L9"), "{log}");
        assert!(!leases.join("OLD.lease").exists());
        assert!(leases.join("NEW.lease").exists());
    }

    /// A second run of the same holder waits for the lock, and a lock whose process is gone is taken over.
    #[test]
    fn a_stale_lock_is_taken_over() {
        let fixture = Fixture::new();
        let lock = fixture.path("home/.cache/bt-remote/leases/H.lock");
        fs::create_dir_all(&lock).expect("a lock");
        // A pid far above the default pid limit of macOS and Linux, so no process has it.
        fs::write(lock.join("pid"), "99999999\n").expect("a dead owner");
        assert_eq!(fixture.run("run", &[]).0, 0);
        assert!(!lock.exists());
    }

    /// The end of the stdin is the caller gone: bt is interrupted, the stage removed, the ref deleted, and the lease
    /// stays for the next run.
    #[test]
    fn the_end_of_the_stdin_interrupts_bt_and_still_cleans_up() {
        let fixture = Fixture::new();
        let mut child = fixture.spawn("run", &[("BT_TEST_MODE", "hang")]);
        let started = fixture.path("log.started");
        let deadline = Instant::now() + Duration::from_secs(20);
        while !started.exists() {
            assert!(Instant::now() < deadline, "bt did not start:\n{}", fixture.log());
            std::thread::sleep(Duration::from_millis(50));
        }
        drop(child.stdin.take());
        let (code, stdout) = wait(&mut child);
        assert_ne!(code, 0);
        assert_eq!(stdout, "", "bt never reached its digest");
        assert_logged(&fixture.log(), &[ref_deleted(&fixture)]);
        assert!(!fixture.path("home/.cache/bt-remote/runs/ID").exists(), "the stage is removed");
        assert!(fixture.lease_file("H").exists(), "the lease stays warm");
    }

    #[test]
    fn a_failed_lease_is_infrastructure_and_still_deletes_the_ref() {
        let fixture = Fixture::new();
        assert_eq!(fixture.run("run", &[("BT_TEST_LEASE_FAILS", "1")]), (6, String::new()));
        let log = fixture.log();
        assert!(!log.contains("bt FooTest"), "{log}");
        assert_logged(&log, &[ref_deleted(&fixture)]);
        assert!(!fixture.lease_file("H").exists());
        assert!(
            !fixture.path("home/.cache/bt-remote/runs/ID").exists(),
            "the empty stage is removed"
        );
    }
}
