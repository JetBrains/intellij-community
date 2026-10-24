use std::os::unix::fs::PermissionsExt;

use pretty_assertions::assert_eq;

use super::*;

fn mode(path: &Path) -> u32 {
    fs::metadata(path).expect("the metadata").permissions().mode() & 0o777
}

#[test]
fn the_modes_do_not_follow_the_umask() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let private = dir.path().join("a/b");
    private_dir(&private).expect("made");
    assert_eq!(mode(&private), 0o700);
    let file = private.join("f");
    fs::write(&file, "old").expect("an old file");
    fs::set_permissions(&file, Permissions::from_mode(0o644)).expect("a loose mode");
    write_private(&file, b"new").expect("written");
    assert_eq!(fs::read_to_string(&file).expect("read"), "new");
    assert_eq!(mode(&file), 0o600);
    let log = private.join("log");
    append_private(&log).expect("opened");
    assert_eq!(mode(&log), 0o600);
}

#[test]
fn a_held_flock_refuses_a_second_open_file() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let path = dir.path().join("lock");
    let first = open_private(&path).expect("opened");
    assert!(try_flock(&first).expect("tried"));
    let second = open_private(&path).expect("opened");
    assert!(!try_flock(&second).expect("tried"));
}

#[test]
fn a_shared_flock_is_refused_only_by_an_exclusive_one() {
    // Each lock file is fresh: a fork of another test thread can hold a dropped lock until its exec.
    let dir = tempfile::tempdir().expect("a temporary directory");
    let probed = dir.path().join("probed");
    let probe = open_private(&probed).expect("opened");
    assert!(try_flock_shared(&probe).expect("tried"));
    let other_probe = open_private(&probed).expect("opened");
    assert!(
        try_flock_shared(&other_probe).expect("tried"),
        "two probes do not refuse each other"
    );

    let held = dir.path().join("held");
    let holder = open_private(&held).expect("opened");
    assert!(try_flock(&holder).expect("tried"));
    assert!(!try_flock_shared(&open_private(&held).expect("opened")).expect("tried"));
}
