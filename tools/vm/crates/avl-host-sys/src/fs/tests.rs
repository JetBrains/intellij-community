#[cfg(unix)]
use std::os::unix::fs::{DirBuilderExt, PermissionsExt, symlink};

use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

#[cfg(unix)]
fn mode(path: &Path) -> u32 {
    fs::symlink_metadata(path).unwrap().permissions().mode() & 0o777
}

/// A runtime root that is itself private, as `prepare_runtime_dirs` leaves it.
fn private_root() -> TempDir {
    let root = tempfile::tempdir().unwrap();
    restrict_directory(root.path()).unwrap();
    root
}

#[cfg(unix)]
fn code(result: Result<impl std::fmt::Debug, Refusal>) -> String {
    result.expect_err("expected a refusal").code.into_owned()
}

/// Whether `path` is a private directory of the caller, in the form of the host.
fn is_private_directory(path: &Path) -> bool {
    directory_protection(path)
        .unwrap()
        .is_some_and(|protection| protection.owned_by_caller() && protection.is_private())
}

#[test]
fn prepare_runtime_dirs_leaves_everything_private() {
    let root = tempfile::tempdir().unwrap();
    // A permissive starting mode, which is what a run under a loose umask produces.
    #[cfg(unix)]
    fs::set_permissions(root.path(), fs::Permissions::from_mode(0o755)).unwrap();
    let worker_dir = root.path().join("workers").join("air-linux-1");
    prepare_runtime_dirs(root.path(), std::slice::from_ref(&worker_dir)).unwrap();
    for path in [
        root.path().to_owned(),
        root.path().join("workers"),
        root.path().join("receipts"),
        worker_dir,
    ] {
        #[cfg(unix)]
        assert_eq!(mode(&path), 0o700, "{}", path.display());
        assert!(is_private_directory(&path), "{}", path.display());
    }
}

#[test]
fn secret_matches_compares_without_leaking_length() {
    assert!(secret_matches("a-bridge-token", "a-bridge-token"));
    assert!(!secret_matches("a-bridge-token", "a-bridge-tokeN"));
    // Hashing first is what makes a length mismatch cost the same as a content mismatch.
    assert!(!secret_matches("short", "a-much-longer-token-entirely"));
    assert!(secret_matches("", ""));
    assert!(!secret_matches("", "x"));
}

#[cfg(unix)]
#[test]
fn ensure_private_directory_refuses_every_way_it_can_be_wrong() {
    let root = tempfile::tempdir().unwrap();

    // A fresh directory is created private, and an existing private one is accepted, so this is idempotent.
    let fresh = root.path().join("fresh");
    ensure_private_directory(&fresh).unwrap();
    assert_eq!(mode(&fresh), 0o700);
    ensure_private_directory(&fresh).unwrap();

    // A file where a directory belongs.
    let as_file = root.path().join("file");
    fs::write(&as_file, b"").unwrap();
    assert_eq!(code(ensure_private_directory(&as_file)), "pull_destination_unsafe");

    // A symlink, which is why the check is `lstat` and not `stat`: pointed at a directory that would pass every
    // other test.
    let target = root.path().join("target");
    fs::DirBuilder::new().mode(0o700).create(&target).unwrap();
    let link = root.path().join("link");
    symlink(&target, &link).unwrap();
    assert_eq!(code(ensure_private_directory(&link)), "pull_destination_unsafe");

    // Group or other bits set; group-only is refused too, since 0o077 covers both halves.
    for (name, loose_mode) in [("loose", 0o755), ("group", 0o740)] {
        let loose = root.path().join(name);
        fs::create_dir(&loose).unwrap();
        fs::set_permissions(&loose, fs::Permissions::from_mode(loose_mode)).unwrap();
        let refusal = ensure_private_directory(&loose).unwrap_err();
        assert_eq!(refusal.code, "pull_destination_unsafe", "{name}");
        assert_eq!(refusal.exit, Exit::NO_PERM, "{name}");
    }

    // A directory of another owner. Only a test running as root can make one; anywhere else the system's own
    // root-owned temporary directory is one.
    if !nix::unistd::getuid().is_root() {
        let foreign = Path::new("/");
        assert_eq!(code(ensure_private_directory(foreign)), "pull_destination_unsafe");
    }
}

#[test]
fn resolve_pull_destination_refuses_every_escape() {
    let root = private_root();
    for (what, input) in [
        ("an empty path", ""),
        ("an absolute path", "/etc/passwd"),
        ("a NUL", "log\0.txt"),
        ("a traversal", "../../escaped.txt"),
        ("a traversal to the parent itself", ".."),
        ("a traversal that collapses back out", "a/../../../out.txt"),
        ("the directory itself", "."),
        ("the directory itself, spelled with a component", "a/.."),
    ] {
        let refusal = resolve_pull_destination(root.path(), "air-linux-1", input).unwrap_err();
        assert_eq!(refusal.code, "pull_destination_unsafe", "{what}: {refusal:?}");
    }

    // A path that stays inside is answered, and its parents are created private.
    let destination =
        resolve_pull_destination(root.path(), "air-linux-1", "screenshots/first.png").expect("a safe destination was refused");
    assert!(
        destination.ends_with("artifacts/air-linux-1/screenshots/first.png"),
        "{}",
        destination.display()
    );
    #[cfg(unix)]
    assert_eq!(mode(destination.parent().unwrap()), 0o700);
    assert!(is_private_directory(destination.parent().unwrap()));
    // A traversal that stays inside collapses rather than being refused.
    let collapsed = resolve_pull_destination(root.path(), "air-linux-1", "a/../second.png").unwrap();
    assert!(collapsed.ends_with("artifacts/air-linux-1/second.png"), "{}", collapsed.display());
}

// A caller asked to publish an artifact, not to replace one, so an existing destination is refused - with its own
// code, because "you already have this" is a different thing to act on from "that path is not allowed".
#[cfg(unix)]
#[test]
fn resolve_pull_destination_refuses_to_overwrite() {
    let root = private_root();
    let destination = resolve_pull_destination(root.path(), "air-linux-1", "log.txt").unwrap();
    fs::write(&destination, b"first").unwrap();
    let refusal = resolve_pull_destination(root.path(), "air-linux-1", "log.txt").unwrap_err();
    assert_eq!(refusal.code, "pull_destination_exists");
    assert_eq!(refusal.exit, Exit::CANT_CREATE);

    // Including when it is a symlink rather than a file: `lstat` is what makes that a refusal instead of a write
    // through the link.
    symlink("/etc/passwd", destination.parent().unwrap().join("linked.txt")).unwrap();
    assert_eq!(
        code(resolve_pull_destination(root.path(), "air-linux-1", "linked.txt")),
        "pull_destination_exists"
    );
}

// One symlinked component anywhere in the path is enough, which is why every level is checked rather than only the
// root.
#[cfg(unix)]
#[test]
fn resolve_pull_destination_checks_every_component() {
    let root = private_root();
    resolve_pull_destination(root.path(), "air-linux-1", "a/b/c.txt").unwrap();
    let middle = root.path().join("artifacts").join("air-linux-1").join("a").join("b");
    let elsewhere = root.path().join("elsewhere");
    fs::DirBuilder::new().mode(0o700).create(&elsewhere).unwrap();
    fs::remove_dir_all(&middle).unwrap();
    symlink(&elsewhere, &middle).unwrap();
    assert_eq!(
        code(resolve_pull_destination(root.path(), "air-linux-1", "a/b/c.txt")),
        "pull_destination_unsafe"
    );
}

#[test]
fn the_portable_form_is_the_one_git_and_bazel_print() {
    for (verbatim, want) in [
        (r"\\?\C:\Users\air\repo", "C:/Users/air/repo"),
        (r"\\?\d:\", "d:/"),
        (r"\\?\UNC\host\share\repo", "//host/share/repo"),
        // Already in the form, or a plain Windows path.
        ("C:/Users/air/repo", "C:/Users/air/repo"),
        (r"C:\Users\air", "C:/Users/air"),
        // A verbatim path with no drive form stays verbatim, since `/` would break it.
        (
            r"\\?\Volume{0b1c2d3e-0000-0000-0000-100000000000}\repo",
            r"\\?\Volume{0b1c2d3e-0000-0000-0000-100000000000}\repo",
        ),
    ] {
        assert_eq!(portable_form(verbatim), want, "{verbatim}");
    }
}

#[test]
fn a_real_path_resolves_to_an_existing_absolute_directory() {
    let root = tempfile::tempdir().unwrap();
    let nested = root.path().join("nested");
    fs::create_dir(&nested).unwrap();
    let resolved = real_path(&root.path().join("nested").join("..").join("nested")).unwrap();
    assert!(resolved.is_absolute(), "{}", resolved.display());
    assert!(resolved.is_dir(), "{}", resolved.display());
    assert!(!resolved.to_string_lossy().starts_with(r"\\?\"), "{}", resolved.display());
    assert_eq!(resolved, real_path(&nested).unwrap());
}
