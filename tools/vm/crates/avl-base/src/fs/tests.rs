use pretty_assertions::assert_eq;

use super::*;

/// The permission bits of a file, on the platforms that have any. Windows has none to read, so the privacy half of
/// these tests is unix-only; existence and content are asserted everywhere.
#[cfg(unix)]
fn assert_mode(path: &Path, want: u32) {
    use std::os::unix::fs::PermissionsExt;
    let mode = fs::metadata(path).expect("the file exists").permissions().mode() & 0o777;
    assert_eq!(mode, want, "{} is mode {mode:o}", path.display());
}

#[cfg(not(unix))]
fn assert_mode(path: &Path, _want: u32) {
    assert!(path.exists(), "{}", path.display());
}

/// A temporary is either the destination's own file or gone, so anything else in the directory is litter.
fn assert_only_entries(directory: &Path, expected: &[&str]) {
    let mut found: Vec<String> = fs::read_dir(directory)
        .expect("the directory is readable")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    found.sort();
    let mut want: Vec<&str> = expected.to_vec();
    want.sort_unstable();
    assert_eq!(found, want);
}

#[test]
fn write_atomically_publishes_the_whole_file_at_the_requested_mode() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let path = root.path().join("lease.json");
    write_atomically(&path, br#"{"worker":"air-linux-1"}"#, 0o600).expect("the write");
    assert_eq!(fs::read(&path).expect("the file"), br#"{"worker":"air-linux-1"}"#);
    // 0600 and not whatever `umask` allowed: this file is a receipt read fail-closed.
    assert_mode(&path, 0o600);
    // Replacing is what current state wants.
    write_atomically(&path, b"{}", 0o600).expect("the rewrite");
    assert_eq!(fs::read(&path).expect("the file"), b"{}");
    assert_only_entries(root.path(), &["lease.json"]);
}

// The answer, not the saved write, is the point: callers skip work when the inputs did not move.
#[test]
fn write_atomically_if_changed_answers_whether_it_wrote() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let path = root.path().join("marker");
    assert_eq!(write_atomically_if_changed(&path, b"one", 0o600), Ok(true));
    assert_eq!(write_atomically_if_changed(&path, b"one", 0o600), Ok(false));
    assert_eq!(write_atomically_if_changed(&path, b"two", 0o600), Ok(true));
    // A receipt whose permissions drifted is repaired without being rewritten.
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o644)).expect("chmod");
    }
    assert_eq!(write_atomically_if_changed(&path, b"two", 0o600), Ok(false));
    assert_mode(&path, 0o600);
    assert_only_entries(root.path(), &["marker"]);
}

#[test]
fn write_exclusively_publishes_the_whole_content_privately() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let path = root.path().join("iteration-1.json");
    let content = b"{\n  \"iterationId\": \"iteration-1\"\n}\n";
    write_exclusively(&path, content, 0o600).expect("the publish");
    assert_eq!(fs::read(&path).expect("the destination"), content);
    assert_mode(&path, 0o600);
    assert_only_entries(root.path(), &["iteration-1.json"]);
}

// The refusal is a filesystem fact rather than a pre-check, and the bytes already there survive: this is why the
// publish is not a rename.
#[test]
fn publish_refuses_a_destination_that_already_exists() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let path = root.path().join("iteration-1.json");
    fs::write(&path, "the first account").expect("the first account");
    let error = write_exclusively(&path, b"the second account", 0o600).expect_err("an existing destination is refused");
    assert!(
        matches!(&error, PublishError::DestinationExists(named) if *named == path),
        "{error:?}"
    );
    // The error names the path, because every caller's own refusal quotes it.
    assert!(error.to_string().contains(&path.display().to_string()), "{error}");
    assert_eq!(fs::read_to_string(&path).expect("the file"), "the first account");
    assert_only_entries(root.path(), &["iteration-1.json"]);
}

// A publish that fails for a reason other than an existing destination is told apart from one, and leaves
// nothing behind.
#[test]
fn a_publish_that_cannot_be_taken_leaves_no_temporary() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let mut temporary = private_temporary(root.path(), "iteration-1.json").expect("a temporary");
    temporary.write_all(b"evidence").expect("the write");
    let name = temporary.path().file_name().expect("a name").to_string_lossy().into_owned();
    // A hidden name that says what it will become.
    assert!(name.starts_with(".iteration-1.json-") && name.ends_with(".tmp"), "{name}");
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = temporary.as_file().metadata().expect("metadata").permissions().mode();
        assert_eq!(mode & 0o077, 0, "the temporary is readable beyond its owner");
    }
    // A single name component past the 255 bytes every filesystem this runs on allows.
    let error = publish(temporary, &root.path().join("x".repeat(300))).expect_err("an unlinkable destination is refused");
    assert!(matches!(error, PublishError::Io { .. }), "{error:?}");
    assert_only_entries(root.path(), &[]);
}

// The move works on the directory entry, so a symlink swapped in where the temporary was is what a rename or a
// link would publish. It is refused rather than followed, and not as an existing destination: the callers map
// that one to a code of their own, and a planted symlink is not a caller asking to replace an answer.
#[cfg(unix)]
#[test]
fn publish_refuses_a_temporary_swapped_for_a_symlink() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let planted = root.path().join("elsewhere");
    fs::write(&planted, "not this run's evidence").expect("the planted file");
    let mut temporary = private_temporary(root.path(), "iteration-1.json").expect("a temporary");
    temporary.write_all(b"evidence").expect("the write");
    fs::remove_file(temporary.path()).expect("the temporary's entry is removed");
    std::os::unix::fs::symlink(&planted, temporary.path()).expect("the symlink is planted");
    let destination = root.path().join("iteration-1.json");

    let error = publish(temporary, &destination).expect_err("a symlinked temporary is refused");
    assert!(matches!(error, PublishError::Io { .. }), "{error:?}");
    assert!(
        fs::symlink_metadata(&destination).is_err(),
        "the refused publish created the destination anyway"
    );
    // The symlink is gone with the temporary, and the file it pointed at is untouched.
    assert_only_entries(root.path(), &["elsewhere"]);
    assert_eq!(fs::read_to_string(&planted).expect("the planted file"), "not this run's evidence");
}

// Callers of `private_temporary` rely on three things: a hidden name that says what the file will become, a fresh
// name on every draw, and nothing for the group or for others (the exact mode is `umask`'s to reduce, and applying
// one is the writer's job; Windows has no bits to read).
#[test]
fn a_private_temporary_is_a_distinct_hidden_file_every_time() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let mut seen = std::collections::BTreeSet::new();
    #[expect(
        clippy::collection_is_never_read,
        reason = "held keeps every temporary alive, so a later draw cannot reuse a deleted file's name"
    )]
    let mut held = Vec::new();
    for draw in 0..8 {
        let temporary = private_temporary(root.path(), "iteration-1.json").expect("a temporary");
        let name = temporary.path().file_name().expect("a name").to_string_lossy().into_owned();
        assert!(
            name.starts_with(".iteration-1.json-") && name.ends_with(".tmp"),
            "the temporary is named {name}, which says nothing about what it will become"
        );
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = temporary.as_file().metadata().expect("metadata").permissions().mode();
            assert_eq!(mode & 0o077, 0, "{name} is mode {mode:o}");
        }
        assert!(seen.insert(name.clone()), "draw {draw} reused the name {name}");
        held.push(temporary);
    }
}

// The leaf is the directory that holds the private documents, so it is made private even when it already existed.
#[cfg(unix)]
#[test]
fn create_private_dir_makes_an_existing_leaf_private() {
    use std::os::unix::fs::PermissionsExt;
    let root = tempfile::tempdir().expect("a temporary directory");
    let directory = root.path().join("runs").join("run-1");
    fs::create_dir_all(&directory).expect("mkdir");
    fs::set_permissions(&directory, fs::Permissions::from_mode(0o755)).expect("chmod");
    create_private_dir(&directory).expect("the directory");
    assert_mode(&directory, 0o700);
}
