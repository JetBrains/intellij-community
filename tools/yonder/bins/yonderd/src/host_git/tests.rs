use std::ffi::OsStr;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use pretty_assertions::assert_eq;

use super::*;

fn version(text: &str) -> Version {
    Version::parse(text).unwrap_or_else(|| panic!("{text:?} is a version"))
}

fn found(path: &str, answer: &str) -> Found {
    Found {
        path: PathBuf::from(path),
        version: Version::of_answer(answer),
        text: answer.trim_start_matches("git version ").to_owned(),
    }
}

/// Writes an executable script `name` into `dir`.
fn script(dir: &Path, name: &str, body: &str) -> PathBuf {
    let path = dir.join(name);
    fs::write(&path, format!("#!/bin/sh\n{body}\n")).expect("write");
    fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).expect("chmod");
    path
}

#[test]
fn the_answer_of_apple_git_and_of_a_release_candidate_name_their_versions() {
    let answer = |text: &str| Version::of_answer(text).map(|version| version.to_string());
    assert_eq!(answer("git version 2.50.1 (Apple Git-155)"), Some("2.50.1".to_owned()));
    assert_eq!(answer("git version 2.56.0\n"), Some("2.56.0".to_owned()));
    assert_eq!(answer("git version 2.56.0.rc1"), Some("2.56.0-rc1".to_owned()));
    assert_eq!(answer("git version 2.56.0-rc2"), Some("2.56.0-rc2".to_owned()));
    assert_eq!(answer("git version 2.45.1.windows.1"), Some("2.45.1".to_owned()));
    assert_eq!(answer("git version 2.45.0.rc1.windows.1"), Some("2.45.0-rc1".to_owned()));
    assert_eq!(answer("git version 2.43.0.vfs.0.0"), Some("2.43.0".to_owned()));
    assert_eq!(answer("git version 2.56"), Some("2.56.0".to_owned()));
    assert_eq!(answer("git version banana"), None);
    assert_eq!(answer("hub version 2.14.2"), None);
    assert_eq!(answer(""), None);
}

#[test]
fn a_release_candidate_comes_before_its_release_and_after_the_release_before() {
    let ordered = [
        "2.9.5",
        "2.50.1",
        "2.55.9",
        "2.56.0-rc0",
        "2.56.0-rc1",
        "2.56.0-rc2",
        "2.56.0",
        "2.56.1",
        "2.57.0-rc0",
        "3.0.0",
    ];
    for pair in ordered.windows(2) {
        assert!(version(pair[0]) < version(pair[1]), "{} < {}", pair[0], pair[1]);
    }
    assert_eq!(version("2.56.0"), MINIMUM);
    assert!(version("2.56.0.rc1") < MINIMUM);
    assert!(version("2.50.1") < MINIMUM);
}

#[test]
fn the_plan_upgrades_only_through_homebrew_and_refuses_without_it() {
    let brew = Path::new("/opt/homebrew/bin/brew");
    let apple = found("/usr/bin/git", "git version 2.50.1 (Apple Git-155)");
    let old_brew = found("/opt/homebrew/bin/git", "git version 2.55.0");
    let new = found("/usr/bin/git", "git version 2.56.0");
    let rc = found("/opt/homebrew/bin/git", "git version 2.56.0.rc1");
    let broken = found("/usr/bin/git", "that fails `--version`");

    // New enough: nothing to do, with or without Homebrew.
    assert_eq!(plan(Some(&new), MINIMUM, None, false, true), Plan::Ready);
    assert_eq!(plan(Some(&new), MINIMUM, Some(brew), true, true), Plan::Ready);
    assert_eq!(plan(Some(&apple), version("2.50.0"), None, false, true), Plan::Ready);

    // Homebrew without git: an install. Homebrew with its own git first: an upgrade.
    assert_eq!(plan(Some(&apple), MINIMUM, Some(brew), false, true), Plan::Install);
    assert_eq!(plan(None, MINIMUM, Some(brew), false, true), Plan::Install);
    assert_eq!(plan(Some(&broken), MINIMUM, Some(brew), false, true), Plan::Install);
    assert_eq!(plan(Some(&old_brew), MINIMUM, Some(brew), true, true), Plan::Upgrade);
    assert_eq!(plan(Some(&rc), MINIMUM, Some(brew), true, true), Plan::Upgrade);

    // Another old git before the one of Homebrew: an upgrade would not help.
    let shadow = found("/Users/u/.local/bin/git", "git version 2.40.0");
    let Plan::Refuse(message) = plan(Some(&shadow), MINIMUM, Some(brew), true, true) else {
        panic!("a refusal");
    };
    assert!(message.contains("before the git of Homebrew in /opt/homebrew/bin"), "{message}");

    // No Homebrew: macOS names its install, another system its package manager.
    let Plan::Refuse(mac) = plan(Some(&apple), MINIMUM, None, false, true) else {
        panic!("a refusal");
    };
    assert!(
        mac.contains("git 2.50.1 (Apple Git-155) at /usr/bin/git, older than 2.56.0"),
        "{mac}"
    );
    assert!(mac.contains("raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"), "{mac}");
    assert!(mac.contains("Then run `brew install git`"), "{mac}");
    let Plan::Refuse(linux) = plan(None, MINIMUM, None, false, false) else {
        panic!("a refusal");
    };
    assert!(linux.contains("missing: the PATH of the agent finds no git"), "{linux}");
    assert!(linux.contains("with the package manager of the host"), "{linux}");
    assert!(!linux.contains("Homebrew"), "{linux}");
}

#[test]
fn the_directory_of_homebrew_goes_right_before_the_system_directory() {
    let first = |path: &str, dir: &str| with_first(OsStr::new(path), Path::new(dir)).into_string().expect("UTF-8");
    let brew = "/opt/homebrew/bin";
    assert_eq!(first("/usr/bin:/bin:/usr/sbin", brew), "/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin");
    // The tools of the user keep their place before it.
    assert_eq!(
        first("/h/.local/bin:/usr/bin:/bin:/opt/homebrew/bin:/h/.cargo/bin", brew),
        "/h/.local/bin:/opt/homebrew/bin:/usr/bin:/bin:/h/.cargo/bin"
    );
    // A PATH that has it first already stays, a later copy too.
    let kept = "/h/.local/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/opt/homebrew/bin";
    assert_eq!(first(kept, brew), kept);
    assert_eq!(first("/opt/homebrew/bin:/bin", brew), "/opt/homebrew/bin:/bin");
    // No /usr/bin: at the end.
    assert_eq!(first("/bin:/sbin", brew), "/bin:/sbin:/opt/homebrew/bin");
    assert_eq!(first("", brew), brew);
}

#[test]
fn a_login_shell_of_bash_or_sh_on_macos_gets_a_warning() {
    let warning = |argv: &[&str], macos: bool| {
        let argv: Vec<String> = argv.iter().map(|argument| (*argument).to_owned()).collect();
        login_shell_warning(&argv, "git 2.56.0", macos)
    };
    let said = warning(&["bash", "-lc", "make"], true).expect("a warning");
    assert_eq!(
        said,
        "`bash -l` runs /etc/profile, whose path_helper puts /usr/bin first: the command can get the Apple Git of /usr/bin, not git 2.56.0. Use `zsh -lc` or no login shell"
    );
    for login in [
        &["/bin/bash", "-l", "-c", "x"][..],
        &["sh", "--login", "-c", "x"],
        &["bash", "-cl", "x"],
        &["/bin/sh", "-ilc", "x"],
    ] {
        assert!(warning(login, true).is_some(), "{login:?}");
    }
    for other in [
        &["bash", "-lc", "x"][..],
        &["zsh", "-lc", "x"],
        &["bash", "-c", "ls -l"],
        &["bash", "script.sh", "-l"],
        &["bash", "--norc", "-c", "x"],
        &["/usr/bin/env", "bash", "-lc", "x"],
        &[],
    ] {
        let macos = other != ["bash", "-lc", "x"];
        assert_eq!(warning(other, macos), None, "{other:?}");
    }
}

#[test]
fn which_takes_the_first_executable_file_of_the_path() {
    let dir = tempfile::tempdir().expect("a directory");
    let (one, two) = (dir.path().join("one"), dir.path().join("two"));
    for bin in [&one, &two] {
        fs::create_dir(bin).expect("mkdir");
    }
    fs::write(one.join("git"), "not executable").expect("write");
    fs::create_dir(one.join("brew")).expect("a directory named brew");
    let git = script(&two, "git", "exit 0");
    let path = env::join_paths(["", one.to_str().expect("UTF-8"), two.to_str().expect("UTF-8")]).expect("a PATH");
    assert_eq!(which("git", &path), Some(git));
    assert_eq!(which("brew", &path), None);
}

#[test]
fn find_reads_the_version_of_the_git_of_the_path() {
    let dir = tempfile::tempdir().expect("a directory");
    script(dir.path(), "git", "echo 'git version 2.50.1 (Apple Git-155)'");
    let git = find(dir.path().as_os_str()).expect("a git");
    assert_eq!(git.version, Some(version("2.50.1")));
    assert_eq!(
        git.to_string(),
        format!("git 2.50.1 (Apple Git-155) at {}", dir.path().join("git").display())
    );
    assert!(!git.is_new_enough(MINIMUM));

    script(dir.path(), "git", "echo broken >&2; exit 3");
    let git = find(dir.path().as_os_str()).expect("a git");
    assert_eq!(git.version, None);
    assert!(git.text.contains("broken"), "{}", git.text);
    assert!(!git.is_new_enough(Version::new(0, 0, 0)));

    assert_eq!(find(OsStr::new("/nonexistent")), None);
}

#[test]
fn a_run_waits_while_serve_holds_the_lock_and_a_refused_upgrade_refuses_it_then() {
    let dir = tempfile::tempdir().expect("a directory");
    let home = Home::new(dir.path().to_path_buf());
    fs::create_dir_all(home.cache_dir()).expect("the cache");
    let mut said = Vec::new();
    assert!(!upgrading(&home), "no lock file");
    assert_eq!(wait(&home, &never, &mut |line| said.push(line.to_owned())), Ok(true));

    write_state(&home, "upgrading", "brew upgrade git");
    let lock = hold_lock(&home).expect("the lock");
    assert!(upgrading(&home));
    assert_eq!(wait(&home, &|| true, &mut |line| said.push(line.to_owned())), Ok(false));
    assert_eq!(
        said,
        ["waiting for the upgrade of git on the host: `brew upgrade git` runs since 0 s"]
    );

    write_state(&home, "refused", "`brew upgrade git` failed");
    drop(lock);
    // A child that another test forks holds a copy of the descriptor until its exec.
    let started = Instant::now();
    while upgrading(&home) {
        assert!(started.elapsed() < Duration::from_secs(5), "the lock stays");
        thread::sleep(Duration::from_millis(10));
    }
    let refusal = wait(&home, &never, &mut |_| {}).expect_err("refused");
    assert_eq!(
        (refusal.code.as_ref(), refusal.message.as_str()),
        (OLD_GIT, "`brew upgrade git` failed")
    );

    write_state(&home, "ready", "git 2.56.0 at /opt/homebrew/bin/git");
    assert_eq!(wait(&home, &never, &mut |_| {}), Ok(true));
}
