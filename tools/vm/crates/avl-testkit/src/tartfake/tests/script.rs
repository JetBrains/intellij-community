//! The `tart` and `prlctl` verbs of the script, which only a Unix host runs.

use std::process::Command;
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn version_answers_what_was_installed_and_the_seeded_exit_code() {
    let fake = Fake::install("2.33.0\n");
    let output = run(&fake, &["--version"]);
    assert_eq!((stdout(&output), code(&output)), ("2.33.0\n".to_owned(), 0));

    fake.answer(Answer::VersionExit, "127");
    assert_eq!(code(&run(&fake, &["--version"])), 127);
    fake.forget(Answer::VersionExit);
    assert_eq!(code(&run(&fake, &["--version"])), 0);
}

#[test]
fn an_unseeded_list_prints_nothing_rather_than_an_empty_pool() {
    let fake = Fake::install("2.33.0\n");
    let output = run(&fake, &["list", "--format", "json"]);
    assert_eq!((stdout(&output), code(&output)), (String::new(), 0));

    fake.answer(Answer::ListJson, "[]");
    fake.answer(Answer::ListQuiet, "air-macos-1\nair-macos-2\n");
    assert_eq!(stdout(&run(&fake, &["list", "--format", "json"])), "[]");
    assert_eq!(stdout(&run(&fake, &["list", "--quiet"])), "air-macos-1\nair-macos-2\n");

    fake.answer(Answer::ListExit, "1");
    assert_eq!(code(&run(&fake, &["list", "--quiet"])), 1);
}

#[test]
fn tart_run_fails_unless_told_to_stay_alive() {
    let fake = Fake::install("2.33.0\n");
    let output = run(&fake, &["run", "air-macos-1"]);
    assert_eq!((stdout(&output), code(&output)), (String::new(), 1));

    fake.answer(Answer::RunOutput, "tart: the golden image is not there\n");
    fake.answer(Answer::RunExit, "3");
    let output = run(&fake, &["run", "air-macos-1"]);
    assert_eq!(
        (stdout(&output), code(&output)),
        ("tart: the golden image is not there\n".to_owned(), 3)
    );
}

#[test]
fn a_sleeping_tart_run_stays_alive_under_its_own_argv() {
    use std::os::unix::process::CommandExt;
    let fake = Fake::install("2.33.0\n");
    fake.answer(Answer::RunSleeps, "yes");
    let mut child = Command::new(fake.executable())
        .args(["run", "air-macos-1"])
        .process_group(0)
        .spawn()
        .expect("spawn the fake tart run");
    let group = child.id();

    // The script records the call before it sleeps, so a logged call means it got that far.
    let deadline = Instant::now() + Duration::from_secs(10);
    while fake.calls().is_empty() && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(20));
    }
    std::thread::sleep(Duration::from_millis(200));
    let still_running = child.try_wait().expect("poll the fake").is_none();
    let command = Command::new("/bin/ps").args(["-o", "command=", "-p", &group.to_string()]).output();

    // The whole group, so the child `sleep` does not outlive the test. The shell's own `kill`, because a
    // sandbox may have no `/bin/kill`.
    let _ = Command::new("/bin/sh")
        .args(["-c", "kill -KILL -- \"-$0\"", &group.to_string()])
        .status();
    let _ = child.wait();

    assert!(still_running, "a seeded tart run exited early");
    if let Ok(command) = command {
        let command = stdout(&command);
        assert!(
            command.contains("tart run air-macos-1"),
            "the run process lost its argv: {command:?}"
        );
    }
}

#[test]
fn tart_exec_serves_read_file_then_falls_through_to_the_shared_exec_answer() {
    let fake = Fake::install("2.33.0\n");
    fake.answer(Answer::ExecStdout, "shared\n");
    fake.answer(Answer::ExecStderr, "noise\n");
    fake.answer(Answer::ExecExit, "4");
    let read = ["exec", "air-macos-1", "/vm/state/vm-guest-agent", "read-file", "/tmp/artifact"];
    let stderr = |output: &Output| String::from_utf8_lossy(&output.stderr).into_owned();

    let output = run(&fake, &read);
    assert_eq!(
        (stdout(&output), stderr(&output), code(&output)),
        ("shared\n".to_owned(), "noise\n".to_owned(), 4)
    );

    fake.answer(Answer::ReadFile, "payload");
    fake.answer(Answer::ReadFileReceipt, "{\"ok\":true}\n");
    let output = run(&fake, &read);
    assert_eq!(
        (stdout(&output), stderr(&output), code(&output)),
        ("payload".to_owned(), "{\"ok\":true}\n".to_owned(), 0)
    );

    fake.answer(Answer::ReadFileFails, "1");
    let output = run(&fake, &read);
    assert_eq!((stdout(&output), code(&output)), (String::new(), 1));
}

#[test]
fn tart_answers_the_loopback_for_ip_and_prlctl_does_not_know_the_verb() {
    let fake = Fake::install("2.33.0\n");
    let parallels = fake.install_beside(Binary::Parallels);
    fake.answer(Answer::Exit, "9");
    let output = run(&fake, &["ip", "air-macos-1"]);
    assert_eq!((stdout(&output), code(&output)), ("127.0.0.1\n".to_owned(), 0));
    let output = run(&parallels, &["ip", "macOS"]);
    assert_eq!((stdout(&output), code(&output)), (String::new(), 9));
}

#[test]
fn other_verbs_are_recorded_and_exit_from_the_shared_code() {
    let fake = Fake::install("2.33.0\n");
    assert_eq!(code(&run(&fake, &["clone", "golden", "air-macos-1"])), 0);
    fake.answer(Answer::Exit, "2");
    assert_eq!(code(&run(&fake, &["delete", "air-macos-1"])), 2);
    assert_eq!(code(&run(&fake, &["stop", "air-macos-1"])), 2);
    assert_eq!(fake.calls(), ["clone golden air-macos-1", "delete air-macos-1", "stop air-macos-1"]);
}

#[test]
fn prlctl_stop_start_and_suspend_move_the_listing_to_their_template() {
    let fake = Fake::install_binary(Binary::Parallels, "prlctl version 20.0.0\n");
    assert_eq!(fake.executable().file_name().and_then(|n| n.to_str()), Some("prlctl"));
    let list = ["list", "-a", "-i", "-j"];
    fake.answer(Answer::ListJson, "running");

    // No template: the state it already had.
    assert_eq!(code(&run(&fake, &["stop", "macOS"])), 0);
    assert_eq!(stdout(&run(&fake, &list)), "running");

    fake.answer(Answer::StateStopped, "stopped");
    fake.answer(Answer::StateRunning, "running again");
    fake.answer(Answer::StateSuspended, "suspended");
    run(&fake, &["stop", "macOS"]);
    assert_eq!(stdout(&run(&fake, &list)), "stopped");
    run(&fake, &["start", "macOS"]);
    assert_eq!(stdout(&run(&fake, &list)), "running again");
    fake.answer(Answer::SuspendExit, "5");
    assert_eq!(code(&run(&fake, &["suspend", "macOS"])), 5);
    assert_eq!(stdout(&run(&fake, &list)), "suspended");
}

#[test]
fn prlctl_exec_matches_inside_its_one_shell_string() {
    let fake = Fake::install_binary(Binary::Parallels, "prlctl version 20.0.0\n");
    fake.answer(Answer::Who, "test console  Aug 19 10:00\n");
    fake.answer(Answer::Autologin, "test\n");
    fake.answer(Answer::AutologinExit, "1");
    fake.answer(Answer::KcpasswordExit, "7");
    fake.answer(Answer::ExecStdout, "shared\n");

    let who = run(&fake, &["exec", "macOS", "/usr/bin/who | grep console"]);
    assert_eq!((stdout(&who), code(&who)), ("test console  Aug 19 10:00\n".to_owned(), 0));
    let autologin = run(
        &fake,
        &[
            "exec",
            "macOS",
            "defaults read /Library/Preferences/com.apple.loginwindow autoLoginUser",
        ],
    );
    assert_eq!((stdout(&autologin), code(&autologin)), ("test\n".to_owned(), 1));
    assert_eq!(code(&run(&fake, &["exec", "macOS", "test -f /etc/kcpassword"])), 7);

    // A command split over several arguments is not what `prlctl exec` receives, so it is not matched.
    let split = run(&fake, &["exec", "macOS", "sh", "-c", "/usr/bin/who"]);
    assert_eq!(stdout(&split), "shared\n");
}

#[test]
fn tart_exec_does_not_answer_the_parallels_guest_commands() {
    let fake = Fake::install("2.33.0\n");
    fake.answer(Answer::Who, "test console\n");
    fake.answer(Answer::ExecStdout, "shared\n");
    assert_eq!(stdout(&run(&fake, &["exec", "air-macos-1", "/usr/bin/who"])), "shared\n");
}

#[test]
fn status_is_shared_by_both_backends() {
    let fake = Fake::install("2.33.0\n");
    let parallels = fake.install_beside(Binary::Parallels);
    fake.answer(Answer::Status, "VM macOS exist running\n");
    assert_eq!(stdout(&run(&parallels, &["status", "macOS"])), "VM macOS exist running\n");
    assert_eq!(stdout(&run(&fake, &["status", "air-macos-1"])), "VM macOS exist running\n");
    fake.answer(Answer::StatusExit, "1");
    assert_eq!(code(&run(&parallels, &["status", "macOS"])), 1);
}

#[test]
fn fakes_installed_beside_share_answers_and_one_call_log() {
    let fake = Fake::install("2.33.0\n");
    let parallels = fake.install_beside(Binary::Parallels);
    assert_eq!(fake.directory(), parallels.directory());
    assert_ne!(fake.executable(), parallels.executable());
    assert_eq!(stdout(&run(&parallels, &["--version"])), "2.33.0\n");

    run(&fake, &["list", "--quiet"]);
    run(&parallels, &["list", "-a", "-i", "-j"]);
    assert_eq!(parallels.calls(), ["--version", "list --quiet", "list -a -i -j"]);
    assert_eq!(fake.calls(), parallels.calls());

    // A test that shares the fake between its cases forgets the calls of one case before the next.
    fake.forget_calls();
    assert!(parallels.calls().is_empty());
    run(&parallels, &["--version"]);
    assert_eq!(fake.calls(), ["--version"]);
}

// The run that takes the scan of a new file leaves no call behind, so a suite reads only the calls it made.
#[test]
fn the_run_before_a_suite_leaves_no_call() {
    let fake = Fake::install("2.33.0\n");
    fake.exec_once();
    assert!(fake.calls().is_empty(), "{:?}", fake.calls());
    run(&fake, &["list", "--quiet"]);
    assert_eq!(fake.calls(), ["list --quiet"]);
}

#[test]
fn the_call_log_keeps_every_argument_whole() {
    let fake = Fake::install("2.33.0\n");
    assert!(fake.calls().is_empty(), "nothing was spawned yet");
    assert!(!fake.saw_call_containing("exec"));

    let script = "cd '/Users/test/My Project'\nexport A=1; ./run.sh";
    run(&fake, &["exec", "-i", "air-macos-1", script, ""]);
    run(&fake, &[]);
    run(&fake, &["set", "air-macos-1", "--disk-size", "800"]);

    assert_eq!(
        fake.argvs(),
        [
            vec![
                "exec".to_owned(),
                "-i".to_owned(),
                "air-macos-1".to_owned(),
                script.to_owned(),
                String::new()
            ],
            vec![],
            vec![
                "set".to_owned(),
                "air-macos-1".to_owned(),
                "--disk-size".to_owned(),
                "800".to_owned()
            ],
        ]
    );
    assert_eq!(fake.calls().len(), 3);
    assert!(fake.saw_call_containing("exec -i"));
    assert!(fake.saw_call_containing("set air-macos-1 --disk-size 800"));
    assert!(!fake.saw_call_containing("--random-serial"));
}

#[test]
fn concurrent_calls_do_not_interleave() {
    let fake = Fake::install("2.33.0\n");
    let long = "x".repeat(16 * 1024);
    let outputs: [Output; 8] = std::thread::scope(|scope| {
        let calls: [_; 8] = std::array::from_fn(|index| {
            let fake = &fake;
            let long = &long;
            scope.spawn(move || run(fake, &["exec", &index.to_string(), long]))
        });
        calls.map(|call| call.join().unwrap())
    });
    for (index, output) in outputs.iter().enumerate() {
        assert_eq!(
            (code(output), String::from_utf8_lossy(&output.stderr).into_owned()),
            (0, String::new()),
            "call {index}: {output:?}"
        );
    }
    let mut seen: Vec<String> = fake
        .argvs()
        .into_iter()
        .map(|argv| {
            assert_eq!(argv.len(), 3, "a torn call: {argv:?}");
            assert_eq!(argv[2], long);
            argv[1].clone()
        })
        .collect();
    seen.sort();
    assert_eq!(seen, ["0", "1", "2", "3", "4", "5", "6", "7"]);
}

#[test]
fn the_directory_goes_with_the_last_fake_sharing_it() {
    let fake = Fake::install("2.33.0\n");
    let beside = fake.install_beside(Binary::Parallels);
    let directory = fake.directory().to_path_buf();
    drop(fake);
    assert!(directory.is_dir(), "a fake beside still uses it");
    drop(beside);
    assert!(!directory.exists(), "{} was left behind", directory.display());
}

#[test]
fn every_install_gets_its_own_directory() {
    let first = Fake::install("2.33.0\n");
    let second = Fake::install("2.34.0\n");
    assert_ne!(first.directory(), second.directory());
    run(&first, &["list"]);
    assert!(second.calls().is_empty());
    assert_eq!(stdout(&run(&second, &["--version"])), "2.34.0\n");
}

#[test]
fn a_tart_exec_answers_by_the_first_word_with_a_verb_file() {
    let fake = Fake::install("2.33.0\n");
    fake.answer(Answer::ExecStdout, "shared");
    fake.answer_exec_verb("active", "A");
    fake.answer_exec_verb("cancel", "C");
    let cancel = run(
        &fake,
        &[
            "exec",
            "w",
            "/usr/bin/sudo",
            "-u",
            "admin",
            "/x/vm-guest-agent",
            "cancel",
            "--run",
            "r",
        ],
    );
    assert_eq!((stdout(&cancel), code(&cancel)), ("C".to_owned(), 0));
    let active = run(&fake, &["exec", "w", "/x/vm-guest-agent", "active", "--root", "/r"]);
    assert_eq!(stdout(&active), "A");
    // A word inside a path is not a verb, and an exec no verb file claims falls through to the shared answer.
    let other = run(&fake, &["exec", "w", "/cancel/x", "status"]);
    assert_eq!(stdout(&other), "shared");
}

// The relay arm is how a suite over the production channel reaches a listener of its own, so the bytes must cross
// the fake both ways, for both spellings of the guest command.
#[test]
fn a_relay_exec_bridges_to_a_loopback_port_for_both_backends() {
    let fake = Fake::install("2.33.0\n");
    fake.answer(Answer::ExecStdout, "shared");
    let (port, served) = one_reply("to the guest".len(), "from the guest");
    let port_text = port.to_string();
    let output = run_with_stdin(
        &fake,
        &["exec", "-i", "w", "/x/vm-guest-agent", "relay", &port_text],
        "to the guest",
    );
    assert_eq!((stdout(&output), code(&output)), ("from the guest".to_owned(), 0));
    assert_eq!(served.join().expect("the listener ends"), "to the guest");

    let parallels = fake.install_beside(Binary::Parallels);
    let (port, served) = one_reply("to the macOS guest".len(), "from the macOS guest");
    // Quoted word by word, as the Parallels backend collapses an argv into one shell string.
    let line = format!("'/x/vm-guest-agent' 'relay' '{port}'");
    let output = run_with_stdin(&parallels, &["exec", "w", &line], "to the macOS guest");
    assert_eq!((stdout(&output), code(&output)), ("from the macOS guest".to_owned(), 0));
    assert_eq!(served.join().expect("the listener ends"), "to the macOS guest");

    // Any other shape is an ordinary exec: no `-i`, or a word after the port.
    assert_eq!(stdout(&run(&fake, &["exec", "w", "/x/vm-guest-agent", "relay", "1"])), "shared");
    assert_eq!(
        stdout(&run(&fake, &["exec", "-i", "w", "/x/vm-guest-agent", "relay", "1", "more"])),
        "shared"
    );
}

/// Runs the fake with `args` in an environment of `variables` over this process's, and answers its output.
fn run_in(fake: &Fake, args: &[&str], variables: &[(&str, &str)]) -> Output {
    Command::new(fake.executable())
        .args(args)
        .envs(variables.iter().copied())
        .output()
        .unwrap_or_else(|e| panic!("spawn {}: {e}", fake.executable().display()))
}

// The `container.cmd` verbs are a state machine over one container, as the Docker verbs are: `start` makes the
// next `list` name the container and writes the files of its control port, and `stop` takes both back. `exec` is
// the shared exec arms, and the bash twin beside the script answers the same.
#[test]
fn container_cmd_start_list_and_stop_move_the_one_container_through_its_states() {
    // The fake sits where a checkout's skill would, so it finds the checkout and its out/testing-ui as the real script does.
    let checkout = std::env::temp_dir().join(format!("avl-testkit-ui-{}", std::process::id()));
    let fake = Fake::install_in(checkout.join(".agents/skills/testing-ui/scripts"), "2.33.0\n").install_beside(Binary::ContainerLinux);
    let work = checkout.join("out").join("testing-ui");
    let environment = [("APP_EXEC", "/bin/true")];
    let none = "no containers of this tool are running\n";
    let listed = run(&fake, &["list"]);
    assert_eq!((stdout(&listed), code(&listed)), (none.to_owned(), 0));

    // A start without a seeded control port is a defect of the suite, refused by name.
    let unseeded = run_in(&fake, &["start", "/p"], &environment);
    assert_eq!(code(&unseeded), 2);
    assert!(String::from_utf8_lossy(&unseeded.stderr).contains("container-linux-port.txt"));
    assert!(!work.join("container.ctl_port").exists());

    fake.answer(Answer::ContainerLinuxPort, "6090");
    fake.answer(Answer::ContainerLinuxRoot, "/Users/air/idea");
    let started = run_in(&fake, &["start", "/p", "--ro", "/a:/b", "--publish", "1:2"], &environment);
    assert_eq!(code(&started), 0, "{}", String::from_utf8_lossy(&started.stderr));
    assert_eq!(fs::read_to_string(work.join("container.ctl_port")).unwrap(), "6090");
    assert_eq!(
        fs::read_to_string(work.join("container.ctl_bearer")).unwrap(),
        format!("{CONTAINER_LINUX_BEARER}\n")
    );
    assert_eq!(
        fs::read_to_string(work.join("container.json")).unwrap(),
        format!(r#"{{"novnc":"{CONTAINER_LINUX_NOVNC_URL}","vncPassword":"{CONTAINER_LINUX_VNC_PASSWORD}"}}"#) + "\n"
    );
    assert_eq!(
        fake.container_linux_start_environment(),
        [("APP_EXEC", "/bin/true")].map(|(name, value)| (name.to_owned(), value.to_owned()))
    );
    assert_eq!(fake.argvs().last().unwrap(), &["start", "/p", "--ro", "/a:/b", "--publish", "1:2"]);
    assert_eq!(
        stdout(&run(&fake, &["list"])),
        format!(
            "{CONTAINER_LINUX_CONTAINER}  running  /Users/air/idea\n  run none\n  noVNC {CONTAINER_LINUX_NOVNC_URL}  password \
             {CONTAINER_LINUX_VNC_PASSWORD}\n"
        )
    );

    fake.answer(Answer::ExecStdout, "shared");
    fake.answer_exec_verb("active", "A");
    assert_eq!(stdout(&run(&fake, &["exec", "/usr/bin/true"])), "shared");
    assert_eq!(stdout(&run(&fake, &["exec", "/x/vm-guest-agent", "active", "--root", "/r"])), "A");

    let twin = Command::new(fake.directory().join("container.sh"))
        .arg("list")
        .output()
        .expect("the bash twin runs");
    assert_eq!(stdout(&twin), stdout(&run(&fake, &["list"])));

    assert_eq!(code(&run_in(&fake, &["stop"], &environment)), 0);
    for name in ["container.ctl_port", "container.ctl_bearer", "container.json"] {
        assert!(!work.join(name).exists(), "{name} survived the stop");
    }
    assert_eq!(stdout(&run(&fake, &["list"])), none);

    fake.answer(Answer::ContainerLinuxFailedVerb, "list");
    let failed = run(&fake, &["list"]);
    assert_eq!(code(&failed), 1);
    assert_eq!(String::from_utf8_lossy(&failed.stderr), "fake container.cmd: list failed\n");
    fs::remove_dir_all(&checkout).expect("the fixture checkout is removed");
}
