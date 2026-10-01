//! The helpers that the unit tests share.

use std::path::{Path, PathBuf};

/// The `testdata/` directory of this crate.
///
/// Bazel sets `BT_TESTDATA_DIR` relative to the start directory of the test. `cargo test` does not set it, so the
/// directory beside `Cargo.toml` answers through the run-time `CARGO_MANIFEST_DIR`.
pub(crate) fn testdata_dir() -> PathBuf {
    std::env::var_os("BT_TESTDATA_DIR").map_or_else(
        || PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR")).join("testdata"),
        PathBuf::from,
    )
}

/// The text of a file in [`testdata_dir`].
pub(crate) fn testdata_text(name: &str) -> String {
    let path = testdata_dir().join(name);
    std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("cannot read {}: {error}", path.display()))
}

/// A copy of the fixture session `testdata/session` in a temporary directory, because `replay` writes into it.
pub(crate) fn fixture_session() -> (tempfile::TempDir, PathBuf) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let session = dir.path().join("session");
    write_copy(&testdata_dir().join("session"), &session);
    (dir, session)
}

/// Copies a tree into new files. The runfiles of Bazel are read-only, and `fs::copy` keeps the mode.
fn write_copy(from: &Path, to: &Path) {
    std::fs::create_dir_all(to).expect("a directory");
    for entry in std::fs::read_dir(from).expect("a listing") {
        let entry = entry.expect("an entry");
        let target = to.join(entry.file_name());
        if entry.path().is_dir() {
            write_copy(&entry.path(), &target);
        } else {
            std::fs::write(&target, std::fs::read(entry.path()).expect("a fixture file")).expect("a copy");
        }
    }
}
