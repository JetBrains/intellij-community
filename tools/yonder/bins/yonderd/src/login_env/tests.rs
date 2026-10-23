use std::os::unix::fs::PermissionsExt;

use pretty_assertions::assert_eq;

use super::*;

/// A login shell for the tests: it fails with `-i` when `$FAIL_INTERACTIVE` is set, sets `FROM_PROFILE` and
/// `PATH`, and runs its last argument, the script of `-c`.
const SHELL: &str = r#"#!/bin/sh
for flag in "$@"; do
  if [ "$flag" = -i ] && [ -n "$FAIL_INTERACTIVE" ]; then exit 3; fi
done
for script; do :; done
FROM_PROFILE=yes PATH="/profile/bin:$PATH"
export FROM_PROFILE PATH
exec /bin/sh -c "$script"
"#;

/// The `dump-env` of the tests: it writes a fixed environment into its file, plus the flags it saw.
const DUMP: &str = r#"#!/bin/sh
[ "$1" = dump-env ] || exit 9
printf '{"FROM_PROFILE":"%s","PATH":"%s","PWD":"/x","SHLVL":"2"}' "$FROM_PROFILE" "$PATH" >"$2"
"#;

fn script(dir: &Path, name: &str, text: &str) -> PathBuf {
    let path = dir.join(name);
    fs::write(&path, text).expect("a script");
    fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).expect("executable");
    path
}

#[test]
fn the_capture_keeps_what_the_shell_changed_less_the_session_variables() {
    let base = BTreeMap::from([("HOME".to_owned(), "/h".to_owned()), ("PATH".to_owned(), "/usr/bin".to_owned())]);
    let dumped = BTreeMap::from([
        ("HOME".to_owned(), "/h".to_owned()),
        ("PATH".to_owned(), "/opt/homebrew/bin:/usr/bin".to_owned()),
        ("JAVA_HOME".to_owned(), "/jdk".to_owned()),
        ("PWD".to_owned(), "/somewhere".to_owned()),
        ("SHLVL".to_owned(), "1".to_owned()),
        ("_".to_owned(), "/usr/bin/env".to_owned()),
    ]);
    assert_eq!(
        changed(&base, dumped),
        BTreeMap::from([
            ("JAVA_HOME".to_owned(), "/jdk".to_owned()),
            ("PATH".to_owned(), "/opt/homebrew/bin:/usr/bin".to_owned()),
        ])
    );
}

#[test]
fn a_dump_runs_the_shell_and_reads_the_file_of_its_last_command() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let shell = script(dir.path(), "shell", SHELL);
    let exe = script(dir.path(), "agent", DUMP);
    let dumped = dump(&exe, &shell, &["-l", "-i"], dir.path(), Duration::from_secs(30)).expect("the dump");
    assert_eq!(dumped.get("FROM_PROFILE").map(String::as_str), Some("yes"));
    assert!(dumped["PATH"].starts_with("/profile/bin:"), "{dumped:?}");
    let left: Vec<_> = fs::read_dir(dir.path()).expect("the directory").collect();
    assert_eq!(left.len(), 2, "the dump file goes after the read");
}

#[test]
fn a_shell_that_fails_interactive_gets_a_login_try_and_a_slow_one_is_killed() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let exe = script(dir.path(), "agent", DUMP);
    let failing = script(dir.path(), "failing", &SHELL.replace("$FAIL_INTERACTIVE", "yes"));
    let error = dump(&exe, &failing, &["-l", "-i"], dir.path(), Duration::from_secs(30)).expect_err("no dump");
    assert!(error.contains("exit"), "{error}");
    let captured = capture(&exe, &failing, dir.path()).expect("the second try");
    assert_eq!(captured.shell, format!("{} -l", failing.display()));
    assert_eq!(captured.vars.get("FROM_PROFILE").map(String::as_str), Some("yes"));
    assert!(!captured.vars.contains_key("PWD") && !captured.vars.contains_key("SHLVL"));

    let slow = script(dir.path(), "slow", "#!/bin/sh\nsleep 30\n");
    let started = Instant::now();
    let error = dump(&exe, &slow, &["-l"], dir.path(), Duration::from_millis(300)).expect_err("killed");
    assert!(error.contains("did not end"), "{error}");
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
}

#[test]
fn a_run_reuses_a_young_capture_and_captures_again_after_the_max_age() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let home = Home::new(dir.path().join("home"));
    let kept = LoginEnv {
        captured_ms: epoch_ms(SystemTime::now()),
        shell: "kept".to_owned(),
        vars: BTreeMap::from([("A".to_owned(), "1".to_owned())]),
    };
    fs::create_dir_all(home.cache_dir()).expect("the cache");
    write(&home, &kept).expect("kept");
    let exe = dir.path().join("no-agent");
    let mut said = Vec::new();
    assert_eq!(
        for_run(&home, &exe, MAX_AGE, &mut |line| said.push(line.to_owned())),
        Some(kept.clone())
    );
    assert_eq!(said, Vec::<String>::new());
    assert_eq!(kept.age_text(SystemTime::now() + Duration::from_secs(300)), "5 min");
}

#[test]
fn the_capture_goes_on_top_of_the_environment_of_the_agent() {
    let captured = LoginEnv {
        captured_ms: 0,
        shell: "s".to_owned(),
        vars: BTreeMap::from([("YONDER_TEST_LOGIN_VAR".to_owned(), "from the shell".to_owned())]),
    };
    let env = applied(Some(&captured));
    assert_eq!(
        env.get(&OsString::from("YONDER_TEST_LOGIN_VAR")),
        Some(&OsString::from("from the shell"))
    );
    assert_eq!(env.len(), std::env::vars_os().count() + 1);
    assert_eq!(applied(None).len(), std::env::vars_os().count());
}
