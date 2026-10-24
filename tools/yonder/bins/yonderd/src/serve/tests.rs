use std::fs;
use std::process::Command;

use super::*;

/// The agent of a temporary home, as `serve` makes it.
fn agent(home: &Path) -> Agent {
    let identity = Identity::generate().expect("an identity");
    Agent {
        home: Home::new(home.to_path_buf()),
        tls: ServerTls::new(&identity, home.join("clients")).expect("the TLS setup"),
        exe: PathBuf::from("yonderd"),
        repos: Mutex::default(),
    }
}

/// The agent checks a repository once: a path that a check found stays found while it is a directory, and a missing
/// one is checked again at each request.
#[test]
fn a_repository_that_a_check_found_needs_no_second_check() {
    let home = tempfile::tempdir().expect("a temporary home");
    let repo = home.path().join("repo");
    fs::create_dir_all(&repo).expect("the repository directory");
    let agent = agent(home.path());
    assert_eq!(agent.repo("~/repo").expect_err("no repository yet").code, REPO_MISSING);
    let init = Command::new("git").arg("init").arg("-q").arg(&repo).status().expect("git init");
    assert!(init.success());
    assert_eq!(agent.repo("~/repo").expect("a repository"), repo);
    // Without its .git a fresh check refuses the directory; the agent answers from what it found.
    fs::rename(repo.join(".git"), home.path().join("moved.git")).expect("the .git moved away");
    assert!(!git::is_repo(&repo));
    assert_eq!(agent.repo("~/repo").expect("a found repository"), repo);
    fs::remove_dir_all(&repo).expect("the directory removed");
    assert_eq!(agent.repo("~/repo").expect_err("no directory").code, REPO_MISSING);
}

#[test]
fn a_fetch_directory_stays_inside_the_slot() {
    assert!(is_inner_path("out/bt-runs"));
    assert!(is_inner_path("out"));
    assert!(!is_inner_path(""));
    assert!(!is_inner_path("/out"));
    assert!(!is_inner_path("out/../.."));
    assert!(!is_inner_path("./out"));
}

#[test]
fn a_holder_names_a_lease_file() {
    check_holder("yonder-0123456789ab").expect("a holder");
    assert_eq!(check_holder("../x").expect_err("refused").code, BAD_REQUEST);
    assert_eq!(check_holder("").expect_err("refused").code, BAD_REQUEST);
}
