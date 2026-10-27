use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_lease_file_names_its_lease_its_slot_and_its_repository() {
    let file = LeaseFile::parse("lease_id=208e\nslot=/pool/15/idea-1\nused=1792836308\nrepo=/home/me/projects/idea-1\nprepared.00aa=f1\n")
        .expect("a lease file");
    assert_eq!(
        file,
        LeaseFile {
            lease_id: "208e".to_owned(),
            slot: PathBuf::from("/pool/15/idea-1"),
            repo: Some(PathBuf::from("/home/me/projects/idea-1")),
        }
    );
    let old = LeaseFile::parse("lease_id=a\nslot=/s\n").expect("a file of remote.sh");
    assert_eq!(old.repo, None);
    assert_eq!(LeaseFile::parse("slot=/s\nused=1\n"), None);
    assert_eq!(LeaseFile::parse("lease_id=\nslot=/s\n"), None);
}

/// The repositories of the keeper come from the files that name one; a file without `repo=` names none
#[test]
fn the_lease_files_name_the_repositories_of_the_keeper() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    fs::write(dir.path().join("a.lease"), "lease_id=1\nslot=/s1\nrepo=/r/one\n").expect("a file");
    fs::write(dir.path().join("b.lease"), "lease_id=2\nslot=/s2\nrepo=/r/one\n").expect("a file");
    fs::write(dir.path().join("c.lease"), "lease_id=3\nslot=/s3\n").expect("a file");
    fs::write(dir.path().join("c.lock"), "").expect("a lock");
    assert_eq!(repositories(dir.path()), [PathBuf::from("/r/one")]);
}
