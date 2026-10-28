use std::fs;

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_commit_id_is_40_or_64_lower_case_hex_digits() {
    assert!(is_commit_id(&"a".repeat(40)));
    assert!(is_commit_id(&"0".repeat(64)));
    assert!(!is_commit_id(&"A".repeat(40)));
    assert!(!is_commit_id("abc"));
    assert!(!is_commit_id(&format!("{}\n", "a".repeat(39))));
}

/// A pack ref of the protocol names the ref of Branch Deck that the ingest makes
#[test]
fn a_pack_ref_is_a_run_or_a_base_of_the_ingest() {
    assert_eq!(ingest_ref("refs/yonder/1-2").as_deref(), Some("run:1-2"));
    assert_eq!(ingest_ref("refs/yonder/base/yonder-01ab").as_deref(), Some("base:yonder-01ab"));
    for bad in [
        "refs/heads/master",
        "refs/yonder/../heads/master",
        "refs/yonder/",
        "refs/yonder/base/",
        "refs/yonder/base/a/b",
        "refs/yonder/base/.hidden",
    ] {
        assert_eq!(ingest_ref(bad), None, "{bad}");
    }
    assert!(is_run_ref("refs/yonder/1-2"));
    assert!(!is_run_ref("refs/yonder/base/master"), "a base ref is no run ref");
}

/// A checkout and a bare repository pass by their files, and another directory says why it is none.
#[test]
fn a_repository_passes_by_its_files() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let checkout = dir.path().join("checkout");
    fs::create_dir_all(checkout.join(".git")).expect("a checkout");
    assert_eq!(check_repo(&checkout), Ok(()));
    let bare = dir.path().join("bare.git");
    fs::create_dir_all(bare.join("objects")).expect("a bare repository");
    fs::write(bare.join("HEAD"), "ref: refs/heads/master\n").expect("its HEAD");
    assert_eq!(check_repo(&bare), Ok(()));
    assert_eq!(
        check_repo(dir.path()),
        Err(NotRepo("it has no .git, and no HEAD and objects of a bare repository".to_owned()))
    );
    assert_eq!(
        check_repo(&dir.path().join("missing")),
        Err(NotRepo("it is no directory".to_owned()))
    );
}
