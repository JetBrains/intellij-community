use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_program_that_does_not_exist_answers_no_code() {
    let ran = OsProcesses.run(&Call::new("yonder-no-such-program", &["x"], Duration::from_secs(5)), &never);
    assert_eq!(ran.code, None);
    assert!(ran.stderr.starts_with("cannot start yonder-no-such-program: "), "{}", ran.stderr);
}

#[cfg(unix)]
mod unix {
    use std::time::Instant;

    use pretty_assertions::assert_eq;

    use super::super::*;

    fn sh(script: &str, deadline: Duration) -> Call {
        Call::new("/bin/sh", &["-c", script], deadline)
    }

    #[test]
    fn a_child_answers_its_code_stdout_and_stderr_apart() {
        let ran = OsProcesses.run(&sh("echo out; echo err >&2; exit 3", Duration::from_secs(10)), &never);
        assert_eq!(
            (ran.code, ran.stdout_text().as_str(), ran.stderr.as_str()),
            (Some(3), "out\n", "err\n")
        );
        assert_eq!(ran.said(), "exit 3: err");
    }

    #[test]
    fn the_input_goes_to_the_stdin_and_the_directory_is_the_working_one() {
        let dir = tempfile::tempdir().expect("a temporary directory");
        std::fs::write(dir.path().join("marker"), "").expect("a marker");
        let call = sh("cat; ls", Duration::from_secs(10))
            .in_dir(dir.path().to_path_buf())
            .with_input(b"bytes\n".to_vec());
        let ran = OsProcesses.run(&call, &never);
        assert!(ran.ok(), "{ran:?}");
        assert_eq!(ran.stdout_text(), "bytes\nmarker\n");
    }

    #[test]
    fn a_child_past_its_deadline_is_killed() {
        let started = Instant::now();
        let ran = OsProcesses.run(&sh("echo started; sleep 30", Duration::from_millis(300)), &never);
        assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
        assert_eq!((ran.code, ran.stopped), (None, Some(Stopped::Limit)));
        assert!(ran.stderr.ends_with("/bin/sh stopped after 0 s, its deadline"), "{}", ran.stderr);
    }

    #[test]
    fn a_child_writes_its_stdout_into_a_file() {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let file = dir.path().join("pack");
        let ran = OsProcesses.run_to_file(&sh("printf abc", Duration::from_secs(10)), &file, &never);
        assert!(ran.ok(), "{ran:?}");
        assert!(ran.stdout.is_empty());
        assert_eq!(std::fs::read(&file).expect("the file"), b"abc");
    }

    /// A child under a quiet limit runs as long as it writes progress, longer than the limit in all, and stops when it
    /// is quiet. The busy case has a wide margin, because a loaded machine delays each `sleep`.
    #[test]
    fn a_quiet_limit_watches_the_progress_and_not_the_whole_run() {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let quiet = |script: &str, limit: Duration| sh(script, Duration::ZERO).with_limit(Limit::Quiet(limit));
        let busy = OsProcesses.run_to_file(
            &quiet(
                "i=0; while [ $i -lt 32 ]; do echo $i >&2; sleep 0.25; i=$((i + 1)); done; printf done",
                Duration::from_secs(5),
            ),
            &dir.path().join("busy"),
            &|| false,
        );
        assert!(busy.ok(), "{busy:?}");
        let started = Instant::now();
        let silent = OsProcesses.run_to_file(&quiet("sleep 30", Duration::from_secs(1)), &dir.path().join("silent"), &never);
        assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
        assert_eq!((silent.code, silent.stopped), (None, Some(Stopped::Limit)));
        assert!(silent.stderr.ends_with("/bin/sh stopped: no output for 1 s"), "{}", silent.stderr);
    }

    /// A child that exits while a grandchild holds its stdout answers after the drain time, with the output that came,
    /// and the grandchild ends with the group.
    #[test]
    fn a_grandchild_that_holds_the_pipe_costs_the_drain_time_and_ends_with_the_group() {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let pid_file = dir.path().join("grandchild.pid");
        let started = Instant::now();
        // The grandchild closes each descriptor above 2 first, so a descriptor of another test thread that reached the
        // child before its close-on-exec flag does not live on in it.
        let script = format!(
            "echo before; (i=3; while [ $i -lt 256 ]; do eval \"exec $i>&-\"; i=$((i + 1)); done; sleep 30) & echo $! >'{}'",
            pid_file.display()
        );
        let ran = OsProcesses.run(&sh(&script, Duration::from_secs(60)), &never);
        let waited = started.elapsed();
        assert!(ran.ok(), "{ran:?}");
        assert_eq!(ran.stdout_text(), "before\n");
        assert!(waited >= DRAIN && waited < DRAIN + Duration::from_secs(5), "{waited:?}");
        let pid = std::fs::read_to_string(&pid_file).expect("the pid");
        let alive = || {
            Command::new("kill")
                .args(["-0", pid.trim()])
                .stderr(Stdio::null())
                .status()
                .is_ok_and(|status| status.success())
        };
        let ended = Instant::now();
        while alive() && ended.elapsed() < Duration::from_secs(5) {
            thread::sleep(Duration::from_millis(20));
        }
        assert!(!alive(), "the grandchild outlived its group");
    }

    /// Ctrl+C stops a child and its group at once, with its stdout in a pipe or in a file.
    #[test]
    fn a_stop_kills_the_child_and_its_group() {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let call = sh("sleep 30 & sleep 30", Duration::from_secs(60));
        for to_file in [false, true] {
            let started = Instant::now();
            let stop = || started.elapsed() > Duration::from_millis(200);
            let ran = if to_file {
                OsProcesses.run_to_file(&call, &dir.path().join("x"), &stop)
            } else {
                OsProcesses.run(&call, &stop)
            };
            assert!(
                started.elapsed() < Duration::from_secs(10),
                "to a file {to_file}: {:?}",
                started.elapsed()
            );
            assert_eq!((ran.code, ran.stopped), (None, Some(Stopped::Asked)), "to a file {to_file}");
            assert!(
                ran.stderr.ends_with("/bin/sh stopped after Ctrl+C"),
                "to a file {to_file}: {}",
                ran.stderr
            );
        }
    }

    #[test]
    fn the_last_progress_part_is_the_reason() {
        let ran = Ran {
            code: Some(128),
            stderr: "Counting objects: 10\rCounting objects: 20\rfatal: out of memory\n".to_owned(),
            ..Ran::default()
        };
        assert_eq!(ran.said(), "exit 128: fatal: out of memory");
    }
}
