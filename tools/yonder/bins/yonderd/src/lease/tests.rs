use std::os::unix::fs::PermissionsExt;

use pretty_assertions::assert_eq;

use super::*;
use crate::child::never;

#[test]
fn a_lease_file_keeps_the_format_of_remote_sh_and_adds_the_repository() {
    let file = LeaseFile {
        lease_id: "208e".to_owned(),
        slot: PathBuf::from("/pool/15/idea-1"),
        used: 1_792_836_308,
        repo: Some(PathBuf::from("/home/me/projects/idea-1")),
    };
    assert_eq!(
        file.text(),
        "lease_id=208e\nslot=/pool/15/idea-1\nused=1792836308\nrepo=/home/me/projects/idea-1\n"
    );
    assert_eq!(LeaseFile::parse(&file.text()), Some(file));
    let old = LeaseFile::parse("lease_id=a\nslot=/s\n").expect("a file of remote.sh");
    assert_eq!((old.used, old.repo), (0, None), "a file without `used` is idle");
    assert_eq!(LeaseFile::parse("slot=/s\nused=1\n"), None);
    assert_eq!(LeaseFile::parse("lease_id=\nslot=/s\n"), None);
}

#[test]
fn a_held_lock_refuses_a_second_taker_until_it_drops() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let leases = Leases::new(dir.path().join("leases"), dir.path().join("repo"));
    let first = leases.try_lock("h").expect("locked").expect("free");
    assert!(leases.try_lock("h").expect("tried").is_none());
    assert!(leases.try_lock("other").expect("tried").is_some());
    drop(first);
    // A fork of another test thread shares the open lock file until its exec, so the lock can stay for a moment.
    let dropped = Instant::now();
    while leases.try_lock("h").expect("tried").is_none() {
        assert!(dropped.elapsed() < Duration::from_secs(5), "the lock stays held after its drop");
        thread::sleep(Duration::from_millis(5));
    }
}

#[test]
fn a_wait_for_a_held_lock_says_so_and_times_out() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let leases = Leases::new(dir.path().join("leases"), dir.path().join("repo"));
    let _held = leases.try_lock("h").expect("locked").expect("free");
    let mut said = Vec::new();
    let refusal = leases
        .lock("h", Duration::from_millis(300), &never, &mut |line| said.push(line.to_owned()))
        .expect_err("timed out");
    assert_eq!(refusal.code, LOCK_TIMEOUT);
    assert_eq!(said, vec!["waiting for the other run of h on this host".to_owned()]);
    let stopped = leases.lock("h", Duration::from_secs(60), &|| true, &mut |_| {}).expect("stopped");
    assert!(stopped.is_none());
}

#[test]
fn a_recorded_lease_is_private_and_names_its_repository() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let leases = Leases::new(dir.path().join("leases"), dir.path().join("repo"));
    let lease = Lease {
        lease_id: "id".to_owned(),
        slot: PathBuf::from("/slot"),
        fresh: true,
    };
    leases.record("h", &lease, 42).expect("recorded");
    assert_eq!(
        leases.read("h"),
        Some(LeaseFile {
            lease_id: "id".to_owned(),
            slot: PathBuf::from("/slot"),
            used: 42,
            repo: Some(dir.path().join("repo")),
        })
    );
    let mode = |path: &Path| fs::metadata(path).expect("the metadata").permissions().mode() & 0o777;
    assert_eq!(mode(&dir.path().join("leases")), 0o700);
    assert_eq!(mode(&dir.path().join("leases/h.lease")), 0o600);
}

#[test]
fn a_request_for_another_repository_refuses_and_keeps_the_file() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let mine = Leases::new(dir.path().join("leases"), dir.path().join("repo-a"));
    let lease = Lease {
        lease_id: "id".to_owned(),
        slot: PathBuf::from("/slot"),
        fresh: true,
    };
    mine.record("h", &lease, 42).expect("recorded");
    let other = Leases::new(dir.path().join("leases"), dir.path().join("repo-b"));
    let refusal = other.acquire("h", &never, &mut |_| {}).expect_err("refused");
    assert_eq!(refusal.code, LEASE_FAILED);
    assert!(
        refusal.message.contains("repo-a") && refusal.message.contains("repo-b"),
        "{}",
        refusal.message
    );
    let refusal = other.release("h", &never).expect_err("refused");
    assert_eq!(refusal.code, LEASE_FAILED);
    assert!(dir.path().join("leases/h.lease").is_file());
}

#[test]
fn the_lease_files_of_remote_sh_move_once() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let old = dir.path().join("bt-remote/leases");
    let new = dir.path().join("yonderd/leases");
    fs::create_dir_all(&old).expect("the old directory");
    fs::write(old.join("bt-remote-1.lease"), "lease_id=a\nslot=/s\nused=1\n").expect("a lease file");
    fs::write(old.join("bt-remote-2.lease"), "lease_id=b\nslot=/t\nused=1\n").expect("a lease file");
    fs::write(old.join("bt-remote-1.lock"), "").expect("a lock file");
    private_dir(&new).expect("the new directory");
    fs::write(new.join("bt-remote-2.lease"), "lease_id=c\nslot=/u\nused=2\n").expect("a newer lease file");
    assert_eq!(migrate(&old, &new).expect("moved"), 1);
    assert_eq!(
        fs::read_to_string(new.join("bt-remote-1.lease")).expect("moved"),
        "lease_id=a\nslot=/s\nused=1\n"
    );
    assert!(!old.join("bt-remote-1.lease").exists());
    assert_eq!(
        fs::read_to_string(new.join("bt-remote-2.lease")).expect("kept"),
        "lease_id=c\nslot=/u\nused=2\n"
    );
    assert!(old.join("bt-remote-2.lease").exists(), "a file with a target stays");
    assert_eq!(migrate(&dir.path().join("missing"), &new).expect("nothing"), 0);
}
