use std::ffi::OsStr;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

/// The answer of `bd lease host git`, and the refusal of a run for its failures
#[test]
fn the_git_of_the_host_comes_from_bd_and_git_not_found_is_old_git() {
    let git: HostGit = bd::parse(
        "git",
        r#"{"schema":2,"path":"/opt/homebrew/bin/git","dir":"/opt/homebrew/bin","version":"2.56.0","floor":"2.56.0","meets_floor":true,"operation_id":"x"}"#,
    )
    .expect("an answer");
    assert_eq!(git.dir.as_deref(), Some(Path::new("/opt/homebrew/bin")));
    assert!(git.meets_floor && !git.upgraded);
    let without_dir: HostGit = bd::parse(
        "git",
        r#"{"schema":2,"path":"git","version":"2.56.0","floor":"2.56.0","meets_floor":true}"#,
    )
    .expect("an answer");
    assert_eq!(without_dir.dir, None);

    let old = refusal_of(&Failed {
        code: Some(GIT_NOT_FOUND.to_owned()),
        message: "git 2.50.1 is below the floor 2.56.0: install Homebrew".to_owned(),
    });
    assert_eq!(
        (old.code.as_ref(), old.message.as_str()),
        (OLD_GIT, "git 2.50.1 is below the floor 2.56.0: install Homebrew")
    );
    let other = refusal_of(&Failed {
        code: None,
        message: "bd did not start".to_owned(),
    });
    assert_eq!(other.code, AGENT_ERROR);
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
