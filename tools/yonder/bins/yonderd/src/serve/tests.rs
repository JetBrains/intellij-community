use std::fs;
use std::process::Command;

use super::*;

/// The agent of a temporary home, as `serve` makes it.
fn agent(home: &Path) -> Agent {
    let identity = Identity::generate().expect("an identity");
    Agent::new(
        Home::new(home.to_path_buf()),
        ServerTls::new(&identity, home.join("clients")).expect("the TLS setup"),
        PathBuf::from("yonderd"),
        PathBuf::from("launchctl"),
    )
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
    assert!(git::check_repo(&repo).is_err());
    assert_eq!(agent.repo("~/repo").expect("a found repository"), repo);
    fs::remove_dir_all(&repo).expect("the directory removed");
    assert_eq!(agent.repo("~/repo").expect_err("no directory").code, REPO_MISSING);
}

/// One peer fills its own cap and leaves room for the others, and the total cap holds for all peers.
#[test]
fn a_peer_gets_its_own_cap_of_handshakes_within_the_total_cap() {
    let handshakes = Arc::new(Handshakes::default());
    let peer = |last: u8| IpAddr::from([192, 168, 1, last]);
    let busy: Vec<Admitted> = (0..MAX_HANDSHAKES_PER_PEER)
        .map(|_| Handshakes::admit(&handshakes, peer(1)).expect("room for the peer"))
        .collect();
    assert!(Handshakes::admit(&handshakes, peer(1)).is_none(), "the cap of the peer is full");
    let peers = u8::try_from(MAX_HANDSHAKES / MAX_HANDSHAKES_PER_PEER).expect("a peer count of one byte");
    let others: Vec<Admitted> = (2..=peers)
        .flat_map(|last| (0..MAX_HANDSHAKES_PER_PEER).map(move |_| last))
        .map(|last| Handshakes::admit(&handshakes, peer(last)).expect("room for another peer"))
        .collect();
    assert_eq!(busy.len() + others.len(), MAX_HANDSHAKES);
    assert!(Handshakes::admit(&handshakes, peer(peers + 1)).is_none(), "the total cap is full");
    drop(busy);
    assert!(
        Handshakes::admit(&handshakes, peer(1)).is_some(),
        "an ended handshake frees its count"
    );
    drop(others);
    assert!(
        Handshakes::admit(&handshakes, peer(peers + 1)).is_some(),
        "the other peers freed the total cap"
    );
}

#[test]
fn a_holder_names_a_base_ref() {
    check_holder("yonder-0123456789ab").expect("a holder");
    assert_eq!(check_holder("../x").expect_err("refused").code, BAD_REQUEST);
    assert_eq!(check_holder("").expect_err("refused").code, BAD_REQUEST);
}
