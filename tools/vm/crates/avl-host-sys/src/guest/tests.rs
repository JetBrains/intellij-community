use avl_base::GuestOs;
use avl_base::format::words;
#[cfg(unix)]
use avl_base::{Environment, Selection};
#[cfg(unix)]
use avl_testkit::fake_executable;
use pretty_assertions::assert_eq;

use super::testing::{FakeChannel, FakeProbe, Host, failed};
use super::*;
use crate::testing::runner;

// --- reaching the guest ----------------------------------------------------------------------------------------

#[tokio::test]
async fn as_user_and_as_root_wrap_the_argv() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let guest = host.guest(&channel);
    guest
        .as_user(&words(["/bin/true", "x"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap();
    guest
        .as_root(&words(["/bin/true", "y"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap();
    let user = &host.settings.vm_user;
    assert_eq!(
        channel.lines(),
        [
            format!("/usr/bin/sudo -H -u {user} /bin/true x"),
            "/usr/bin/sudo -H /bin/true y".to_owned(),
        ]
    );
}

// The testing-ui container runs as its one unprivileged account: there is no sudo to prefix, no root to become and
// nothing to chown, so every prefix passes the argv through, and a privileged guest keeps all three.
// A Windows host does not drive the testing-ui container.
#[cfg(unix)]
#[tokio::test]
async fn an_unprivileged_guest_gets_no_sudo_and_no_chown() {
    let host = Host::container_linux();
    let settings = &host.settings;
    assert!(!settings.guest.privileged);
    let argv = words(["/bin/true", "x"]);
    assert_eq!(user_argv(settings, &argv), argv);
    assert_eq!(root_argv(settings, &argv), argv);
    assert_eq!(chown_argv(settings, &["-R"], &[&settings.vm_data]), None);
    let channel = FakeChannel::new("air-linux-1");
    let guest = host.guest(&channel);
    let within = SpawnOptions::within(Duration::from_mins(1));
    guest.as_user(&argv, &within).await.unwrap();
    guest.as_root(&argv, &within).await.unwrap();
    guest.write_file("/tmp/x", b"hello", "700").await.unwrap();
    assert_eq!(
        channel.lines(),
        ["/bin/true x", "/bin/true x", "/usr/bin/tee /tmp/x", "/bin/chmod 700 /tmp/x"]
    );

    let privileged = Host::new(GuestOs::Linux);
    let settings = &privileged.settings;
    assert!(settings.guest.privileged);
    let user = settings.vm_user.as_str();
    assert_eq!(
        user_argv(settings, &argv),
        words(["/usr/bin/sudo", "-H", "-u", user, "/bin/true", "x"])
    );
    assert_eq!(root_argv(settings, &argv), words(["/usr/bin/sudo", "-H", "/bin/true", "x"]));
    assert_eq!(
        chown_argv(settings, &["-R"], &["/data", "/data/state"]),
        Some(words([settings.guest.chown, "-R", user, "/data", "/data/state"]))
    );
    assert_eq!(
        chown_argv(settings, &[], &["/data"]),
        Some(words([settings.guest.chown, user, "/data"]))
    );
}

// A guest process can echo the UI-test bridge token, and a refusal ends up in an envelope an agent reads.
#[tokio::test]
async fn raw_withholds_what_the_guest_printed() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| {
        Ok(Captured {
            exit_code: 3,
            stdout: "TART_VM_TOKEN=secret".to_owned(),
            stderr: "secret too".to_owned(),
            ..Captured::default()
        })
    });
    let refusal = host
        .guest(&channel)
        .as_user(&words(["/bin/false", "--flag"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    assert!(!refusal.message.contains("secret"), "{}", refusal.message);
    // It names the worker and the program that actually ran, with its arguments - never the hypervisor binary, and
    // never the `sudo` every guest call is re-targeted with.
    assert!(
        refusal.message.contains("false in air-docker-1") && refusal.message.contains("(--flag)"),
        "{}",
        refusal.message
    );
    assert!(!refusal.message.contains("sudo"), "{}", refusal.message);
    assert_eq!(refusal.exit, Exit::from_status(3, Exit::FAILURE));
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 3 })));
}

// `tart exec` lands in the guest as root, so every real command is re-targeted and `argv[0]` is `/usr/bin/sudo` for
// all of them. Naming that was the whole defect: `sudo in air-linux-1 exited with 64` sent a session hunting a
// passwordless-sudo regression that did not exist. The aqua shapes are the supervisor's, pinned here so a caller
// that routes one through `raw` gets the same attribution.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[test]
fn the_effective_program_is_found_behind_every_wrapper_shape() {
    let linux = Host::new(GuestOs::Linux);
    let macos = Host::new(GuestOs::Macos);
    let (linux, macos) = (&linux.settings, &macos.settings);
    let display = format!("DISPLAY={}", linux.guest_display);
    let cases: Vec<(&str, Vec<String>, &str, String)> = vec![
        (
            "as the worker user",
            user_argv(linux, &words(["/usr/bin/tee", "/tmp/x"])),
            "tee",
            "/tmp/x".to_owned(),
        ),
        ("as root", root_argv(linux, &words(["/sbin/mount", "-a"])), "mount", "-a".to_owned()),
        (
            "aqua on macOS",
            words([
                "/bin/launchctl",
                "asuser",
                &macos.vm_uid,
                "/usr/bin/sudo",
                "-H",
                "-u",
                &macos.vm_user,
                "/usr/bin/env",
                "IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true",
                &macos.vm_agent,
                "active",
                "--root",
                &macos.vm_runs_root,
            ]),
            "vm-guest-agent",
            format!("active --root {}", macos.vm_runs_root),
        ),
        (
            "aqua on Linux",
            words([
                "/usr/bin/sudo",
                "-H",
                "-u",
                &linux.vm_user,
                "/usr/bin/setsid",
                "--wait",
                "/usr/bin/env",
                &display,
                "/usr/bin/env",
                "IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true",
                &linux.vm_agent,
                "log",
                "--tail",
                "50",
            ]),
            "vm-guest-agent",
            "log --tail 50".to_owned(),
        ),
        // Unwrapped, which the probes are.
        ("no wrapper at all", words(["/usr/bin/who"]), "who", String::new()),
        // A wrapper that consumes the whole argv names itself, because then it really is the last thing that ran.
        ("nothing but the wrapper", words(["/usr/bin/sudo", "-H"]), "sudo", "-H".to_owned()),
        // `launchctl asuser` is the one subcommand a program runs under; every other one is the command.
        (
            "launchctl as the command",
            words(["/bin/launchctl", "print", "system"]),
            "launchctl",
            "print system".to_owned(),
        ),
    ];
    for (name, line, program, arguments) in cases {
        let (found, rest) = effective_guest_program(&line);
        assert_eq!((found, rest.join(" ")), (program, arguments), "{name}");
    }
}

// The one exception to the withholding, and what removes the need for a separate `sudo -n` admission probe: sudo
// prints this prefix from its own diagnostics, before it has exec'd anything.
#[tokio::test]
async fn raw_quotes_sudos_own_complaint_and_nothing_else() {
    let host = Host::new(GuestOs::Linux);
    let sudo_failed = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "sudo: a password is required\n")));
    let refusal = host
        .guest(&sudo_failed)
        .as_user(&words(["/bin/true"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap_err();
    assert!(
        refusal.message.contains("sudo: a password is required"),
        "a broken wrapper must be legible: {}",
        refusal.message
    );
    // Anything else on that stream stays withheld, including a second line behind a `sudo:` first one.
    let guest_failed = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "sudoku: TART_VM_TOKEN=secret\nsudo: not first\n")));
    let refusal = host
        .guest(&guest_failed)
        .as_user(&words(["/bin/true"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap_err();
    assert!(
        !refusal.message.contains("secret") && !refusal.message.contains("not first"),
        "{}",
        refusal.message
    );
}

// An element that holds a value can be the UI-test bridge token, a `-D` naming a path under someone's home, or a
// `sudo` account, so it is replaced whole rather than trusted. The policy is the phase module's; there is not a
// second one here.
#[tokio::test]
async fn a_refusal_redacts_an_argv_element_that_carries_a_value() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "")));
    let payload = "b".repeat(200);
    let refusal = host
        .guest(&channel)
        .as_user(
            &words([
                "/usr/bin/java",
                "-Dair.ui.bridge.token=secret",
                "-Dide.host=127.0.0.1:8443",
                "-cp",
                "/vm/runtime/lib",
                &payload,
            ]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    let message = &refusal.message;
    for withheld in ["secret", "127.0.0.1", payload.as_str()] {
        assert!(!message.contains(withheld), "{message}");
    }
    // What it still says is which call this was, which is the point of naming an argv at all.
    for kept in ["java in air-docker-1", "-cp", "/vm/runtime/lib"] {
        assert!(message.contains(kept), "wanted {kept:?}: {message}");
    }
}

// Neither the argv a refusal names nor the one stderr line it quotes can grow without limit, and the cut lands on
// a character boundary.
#[tokio::test]
async fn a_refusals_argv_and_its_quoted_line_are_both_bounded() {
    let host = Host::new(GuestOs::Linux);
    // Ten arguments inside the per-element bound, so what has to stop the rendering is the total.
    let mut line = words(["/bin/false"]);
    line.extend(std::iter::repeat_n("a".repeat(90), 10));
    // `é` is two bytes and the prefix is odd-length, so the 240th byte of this line is a continuation byte.
    let complaint = format!("sudo: x{}", "é".repeat(400));
    let stderr = format!("{complaint}\nTART_VM_TOKEN=secret\n");
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(1, &stderr)));
    let refusal = host
        .guest(&channel)
        .as_user(&line, &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap_err();
    let message = &refusal.message;
    let (opened, closed) = (message.find('(').unwrap(), message.find(')').unwrap());
    assert!(message[opened + 1..closed].len() <= GUEST_ARGV_MESSAGE_BYTES, "{message}");
    let quoted = &message[message.find("sudo: ").unwrap()..];
    assert!(quoted.len() <= GUEST_ARGV_MESSAGE_BYTES, "{message}");
    assert!(quoted.len() > GUEST_ARGV_MESSAGE_BYTES - 2, "{message}");
    // The second line of that stream stays withheld, bound or no bound.
    assert!(!message.contains("TART_VM_TOKEN"), "{message}");
    assert!(!message.contains('\u{FFFD}'), "{message}");

    // One argument longer than the per-element bound is redacted rather than dropped: a refusal that named an argv
    // and rendered nothing of it would read as a command with no arguments.
    let long = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "")));
    let refusal = host
        .guest(&long)
        .as_user(
            &words(["/bin/false", &"c".repeat(500)]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert!(
        refusal.message.contains("false in air-docker-1 exited with 1 (…)"),
        "{}",
        refusal.message
    );
    assert!(!refusal.message.contains("cc"), "{}", refusal.message);
}

#[tokio::test]
async fn succeeds_is_a_probe_and_not_a_refusal() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "")));
    let guest = host.guest(&channel);
    assert!(
        !guest
            .succeeds(&words(["/bin/test", "-d", "/nowhere"]), Duration::from_mins(1))
            .await
    );
    // A timeout is named for the guest, so a caller can tell a silent guest from a hung host tool.
    guest.succeeds(&words(["/bin/test"]), Duration::from_secs(5)).await;
    let options = channel.calls()[1].options.clone();
    assert_eq!(
        (options.timeout, options.timeout_code),
        (Duration::from_secs(5), Some("guest_agent_timeout"))
    );
}

#[tokio::test]
async fn write_file_streams_through_tee_and_then_chmods() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let guest = host.guest(&channel);
    guest.write_file("/tmp/x", b"hello", "700").await.unwrap();
    let user = &host.settings.vm_user;
    assert_eq!(
        channel.lines(),
        [
            format!("/usr/bin/sudo -H -u {user} /usr/bin/tee /tmp/x"),
            format!("/usr/bin/sudo -H -u {user} /bin/chmod 700 /tmp/x"),
        ]
    );
    assert_eq!(channel.calls()[0].options.stdin.as_deref(), Some(&b"hello"[..]));
    // Present even for empty content: no stdin would leave `tee` reading whatever the channel inherits.
    channel.forget();
    guest.write_file("/tmp/y", b"", "600").await.unwrap();
    assert_eq!(channel.calls()[0].options.stdin.as_deref(), Some(&b""[..]));
}

#[tokio::test]
async fn write_file_names_itself_in_the_refusal() {
    let host = Host::new(GuestOs::Linux);
    let stderr = format!("no such directory{}", "x".repeat(3000));
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(4, &stderr)));
    let refusal = host.guest(&channel).write_file("/tmp/x", b"hello", "700").await.unwrap_err();
    assert_eq!(refusal.code, "guest_write_failed");
    assert_eq!(refusal.exit, Exit::from_status(4, Exit::FAILURE));
    assert!(refusal.message.contains("/tmp/x"), "{}", refusal.message);
    // Tee's own complaint travels in the details, clipped, and never in the message.
    let details = refusal.details().unwrap();
    let quoted = details["stderr"].as_str().unwrap();
    assert!(quoted.starts_with("no such directory"), "{quoted}");
    assert_eq!(quoted.len(), FAILURE_OUTPUT_TAIL_BYTES);
    assert!(!refusal.message.contains("no such directory"));
}

// A secret travels on stdin alone: the argv names the path, the file is 0600 from its creation, and nothing echoes it.
#[tokio::test]
async fn write_secret_file_carries_the_content_on_stdin_only() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let secret = b"login-export-0123456789abcdef";
    host.guest(&channel)
        .write_secret_file("/dev/shm/air-run-secrets/AIR_LIVE_CENTRAL_LOGIN", secret)
        .await
        .unwrap();
    let user = &host.settings.vm_user;
    let calls = channel.calls();
    assert_eq!(
        calls.iter().map(|call| call.argv.clone()).collect::<Vec<_>>(),
        [words([
            "/usr/bin/sudo",
            "-H",
            "-u",
            user.as_str(),
            "/bin/sh",
            "-c",
            "umask 077 && exec cat > \"$1\"",
            "sh",
            "/dev/shm/air-run-secrets/AIR_LIVE_CENTRAL_LOGIN",
        ])],
        "one call, and no chmod after the file exists"
    );
    assert_eq!(calls[0].options.stdin.as_deref(), Some(&secret[..]));
    assert!(!calls[0].line().contains("login-export"), "{}", calls[0].line());
    // `tee` would echo the content into the captured stdout; `cat` into a file prints nothing.
    assert!(!calls[0].argv.iter().any(|word| word.ends_with("tee")));
}

// A failed secret write names the path and its exit, and quotes nothing the guest printed.
#[tokio::test]
async fn write_secret_file_withholds_the_guest_output_of_a_failure() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| {
        Ok(Captured {
            exit_code: 2,
            stdout: "login-export-0123456789abcdef".to_owned(),
            stderr: "sh: 1: cannot create /dev/shm/x/NAME: login-export-0123456789abcdef".to_owned(),
            ..Captured::default()
        })
    });
    let refusal = host
        .guest(&channel)
        .write_secret_file("/dev/shm/x/NAME", b"login-export-0123456789abcdef")
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("guest_write_failed", Exit::from_status(2, Exit::FAILURE))
    );
    assert!(refusal.message.contains("/dev/shm/x/NAME"), "{}", refusal.message);
    assert!(!refusal.message.contains("login-export"), "{}", refusal.message);
    assert_eq!(refusal.details(), None);
}

// --- host paths ------------------------------------------------------------------------------------------------

/// A fake `git`: the answer is two fixed strings, and the suite must not depend on the checkout it runs from. A
/// shell script, so the two tests that use it are Unix only.
#[cfg(unix)]
fn fake_git(directory: &Path, toplevel: &Path, inside_exit: i32) -> PathBuf {
    fake_executable(
        directory,
        "fake-git",
        &format!(
            "case \"$*\" in\n*--show-toplevel*) echo '{}';;\n*--is-inside-work-tree*) echo true; exit \
             {inside_exit};;\nesac\n",
            toplevel.display()
        ),
    )
    .unwrap()
}

#[cfg(unix)]
fn unresolved_settings(root: &Path, extra: &[(&str, &str)]) -> Config {
    let home = root.to_string_lossy().into_owned();
    let mut environment = Environment::from_pairs([("HOME", home.as_str())]);
    for (name, value) in extra {
        environment.set(*name, *value);
    }
    Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: avl_base::Backend::Docker,
            guest_os: GuestOs::Linux,
        },
        &environment,
        root,
    )
    .unwrap()
}

#[cfg(unix)]
#[tokio::test]
async fn ensure_host_paths_resolves_both_paths_together() {
    let root = tempfile::tempdir().unwrap();
    let repo = root.path().join("repo");
    let bazel = root.path().join("bazel");
    std::fs::create_dir_all(&repo).unwrap();
    let git = fake_git(root.path(), &repo, 0);
    let settings = unresolved_settings(
        root.path(),
        &[
            ("AIR_VM_BAZEL_USER_ROOT", &bazel.to_string_lossy()),
            ("AIR_VM_HOST_GIT", &git.to_string_lossy()),
        ],
    );
    ensure_host_paths(&Ctx::background(), &runner(), &settings).await.unwrap();
    // Created rather than required: a host that has never built anything still has to declare the share.
    assert!(bazel.is_dir());
    // The realpath, because the guest's symlink points at the mount of whatever was declared: a path with a symlink
    // component would make the guest's absolute paths differ from the host's by that component.
    assert_eq!(
        (settings.host_repo().unwrap(), settings.host_bazel_user_root().unwrap()),
        (real_path(&repo).unwrap().as_path(), real_path(&bazel).unwrap().as_path())
    );
    // Resolved once: a second call asks git nothing, which a git that no longer exists proves.
    std::fs::remove_file(&git).unwrap();
    ensure_host_paths(&Ctx::background(), &runner(), &settings).await.unwrap();
}

#[cfg(unix)]
#[tokio::test]
async fn ensure_host_paths_refuses_something_that_is_not_a_checkout() {
    let root = tempfile::tempdir().unwrap();
    let git = fake_git(root.path(), root.path(), 1);
    let bazel = root.path().join("bazel");
    let settings = unresolved_settings(
        root.path(),
        &[
            ("AIR_VM_BAZEL_USER_ROOT", &bazel.to_string_lossy()),
            ("AIR_VM_HOST_GIT", &git.to_string_lossy()),
            ("AIR_VM_HOST_REPO", &root.path().to_string_lossy()),
        ],
    );
    let refusal = ensure_host_paths(&Ctx::background(), &runner(), &settings).await.unwrap_err();
    assert_eq!(refusal.code, "host_repo_required");
    settings.host_repo().unwrap_err();
}

// A `git` that printed nothing, or that a signal ended, says nothing about the checkout. Its fatal exit 128 outside a
// repository is the one negative answer.
#[cfg(unix)]
#[tokio::test]
async fn ensure_host_paths_refuses_a_git_without_an_answer() {
    let root = tempfile::tempdir().unwrap();
    let bazel = root.path().join("bazel");
    let resolve = |body: &str, repo_override: bool| {
        let git = fake_executable(root.path(), "fake-git", body).unwrap();
        let mut env = vec![
            ("AIR_VM_BAZEL_USER_ROOT", bazel.to_string_lossy().into_owned()),
            ("AIR_VM_HOST_GIT", git.to_string_lossy().into_owned()),
        ];
        if repo_override {
            env.push(("AIR_VM_HOST_REPO", root.path().to_string_lossy().into_owned()));
        }
        let env: Vec<(&str, &str)> = env.iter().map(|(name, value)| (*name, value.as_str())).collect();
        unresolved_settings(root.path(), &env)
    };
    for (body, code) in [
        ("exit 0\n", "probe_unanswered"),
        ("kill -KILL $$\n", "probe_unanswered"),
        ("exit 128\n", "host_repo_required"),
    ] {
        let settings = resolve(body, true);
        let refusal = ensure_host_paths(&Ctx::background(), &runner(), &settings).await.unwrap_err();
        assert_eq!(refusal.code, code, "{body:?}: {refusal:?}");
    }
    // An empty top level is no checkout to share.
    let settings = resolve("exit 0\n", false);
    let refusal = ensure_host_paths(&Ctx::background(), &runner(), &settings).await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    assert!(refusal.message.contains("--show-toplevel` exited with 0"), "{}", refusal.message);
}

// --- the console session ---------------------------------------------------------------------------------------

// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn require_console_login_is_macos_only() {
    let linux = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    linux.guest(&channel).require_console_login().await.unwrap();
    // A Linux worker's display is an Xvfb the controller starts, not a seat a user logs into: nothing is asked.
    assert!(channel.calls().is_empty(), "{:?}", channel.lines());

    let macos = Host::new(GuestOs::Macos);
    let seated = FakeChannel::spoke("air-macos-1", "admin ttys000 …\r\nadmin console Aug 23 10:00\r\n");
    macos.guest(&seated).require_console_login().await.unwrap();
    let empty = FakeChannel::spoke("air-macos-1", "admin ttys000 Aug 23 10:00\n");
    let refusal = macos.guest(&empty).require_console_login().await.unwrap_err();
    assert_eq!(refusal.code, "console_login_required");
    assert!(refusal.message.contains("air-macos-1"), "{}", refusal.message);
    // The guest command itself is refused like any other when it fails.
    let broken = FakeChannel::answering("air-macos-1", |_| Ok(failed(1, "")));
    let refusal = macos.guest(&broken).require_console_login().await.unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
}

// --- the session -----------------------------------------------------------------------------------------------

// The later host crates run guest steps on spawned tasks, one per worker, so every step's future must be `Send`.
#[test]
fn a_sessions_steps_can_run_on_a_spawned_task() {
    fn spawnable<T: Send>(_: T) {}
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let guest = host.guest(&channel);
    let bazel = testing::ForbiddenBazel;
    let peers = testing::Peers::default();
    spawnable(guest.install_agent(&bazel));
    spawnable(guest.provision_linux(&bazel, &[]));
    spawnable(guest.provision_worker(ShareMount::VirtioFs));
    spawnable(guest.ensure_ready(&peers));
    let probe = FakeProbe::default();
    spawnable(guest.reject_executing_run(&probe, "release the lease"));
    spawnable(guest.supervisor_log_reply(
        &[],
        SupervisorOptions {
            aqua: false,
            timeout: Duration::from_mins(1),
        },
    ));
    spawnable(guest.require_clean_worker_tcc());
    spawnable(ensure_host_paths(&host.ctx, &runner(), &host.settings));
}

// A guest directory configured with a trailing slash must not put two slashes into every path under it.
#[test]
fn a_guest_join_keeps_one_slash() {
    assert_eq!(guest_join("/vm/data/", "daemon"), "/vm/data/daemon");
    assert_eq!(guest_join("/vm/data", "daemon"), "/vm/data/daemon");
}
