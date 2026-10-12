use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

fn windows() -> GuestPaths {
    GuestPaths::for_roots(r"C:\Users\air\idea", "C:/ProgramData/_bazel").unwrap()
}

#[test]
fn a_unix_root_is_its_own_guest_root_and_a_path_under_it_keeps_its_text() {
    let paths = GuestPaths::for_roots("/Users/air/idea", "/Users/air/_bazel").unwrap();
    assert_eq!((paths.repo(), paths.bazel_user_root()), ("/Users/air/idea", "/Users/air/_bazel"));
    for host in [
        "/Users/air/idea",
        "/Users/air/idea/.git",
        "/Users/air/_bazel/execroot/_main/bazel-out/k8-fastbuild/bin/a.jar",
    ] {
        assert_eq!(paths.to_guest(Path::new(host)).unwrap(), host);
    }
}

#[test]
fn a_windows_root_becomes_a_drive_directory_in_the_guest() {
    let paths = windows();
    assert_eq!(
        (paths.repo(), paths.bazel_user_root()),
        ("/c/Users/air/idea", "/c/ProgramData/_bazel")
    );
    assert_eq!(
        paths.to_guest_text(r"C:\Users\air\idea\community\x.txt").unwrap(),
        "/c/Users/air/idea/community/x.txt"
    );
    // The form `real_path` answers, and the form of a Bazel MANIFEST, which writes its output root in lower case.
    assert_eq!(paths.to_guest_text("C:/Users/air/idea/.git").unwrap(), "/c/Users/air/idea/.git");
    assert_eq!(
        paths
            .to_guest_text("C:/programdata/_bazel/kxsaieyx/execroot/_main/Mixed.jar")
            .unwrap(),
        "/c/ProgramData/_bazel/kxsaieyx/execroot/_main/Mixed.jar"
    );
}

#[test]
fn a_guest_root_is_refused_for_a_path_that_is_not_absolute() {
    assert_eq!(guest_root("D:/").unwrap(), "/d");
    assert_eq!(guest_root(r"e:\a\\b\.\c\").unwrap(), "/e/a/b/c");
    for host in ["", "relative/idea", "C:idea", r"\\host\share\idea"] {
        assert_eq!(guest_root(host).unwrap_err().code, "guest_path_unmapped", "{host}");
    }
}

/// A path outside both shares cannot reach a worker, so it is refused by name rather than sent.
#[test]
fn a_path_outside_both_shares_is_refused() {
    for host in [
        r"C:\Users\air\ideas\x",
        r"D:\Users\air\idea\x",
        r"C:\Users\air\idea\..\secret",
        "/Users/air/idea/x",
    ] {
        let refusal = windows().to_guest_text(host).unwrap_err();
        assert_eq!(refusal.code, "guest_path_unmapped", "{host}");
        assert!(refusal.message.contains(host), "{}", refusal.message);
    }
}

#[test]
fn lies_below_compares_whole_components_and_a_windows_path_without_case() {
    assert!(lies_below(
        Path::new("C:/ProgramData/_bazel"),
        Path::new("C:/programdata/_bazel/x/descriptor.json")
    ));
    assert!(lies_below(Path::new("/Users/air/_bazel"), Path::new("/Users/air/_bazel/x")));
    for (root, path) in [
        ("/Users/air/_bazel", "/Users/air/_bazel"),
        ("/Users/air/_bazel", "/Users/air/_bazelx/y"),
        ("/Users/air/_bazel", "/users/air/_bazel/y"),
        ("C:/ProgramData/_bazel", "C:/ProgramData/_bazel/"),
        ("C:/ProgramData/_bazel", "D:/ProgramData/_bazel/x"),
    ] {
        assert!(!lies_below(Path::new(root), Path::new(path)), "{path} below {root}");
    }
}
