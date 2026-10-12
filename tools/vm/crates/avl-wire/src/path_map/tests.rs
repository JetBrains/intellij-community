use pretty_assertions::assert_eq;

use super::*;

/// The table of a Windows host: the checkout and the Bazel output root, each on its own share.
fn windows() -> PathMap {
    PathMap::new(vec![
        PathPrefix::new(r"C:\Users\air\repo", "/mnt/AirVmShares/repo"),
        PathPrefix::new("c:/Users/air/_bazel", "/mnt/AirVmShares/bazel"),
    ])
}

#[test]
fn a_windows_path_maps_in_either_spelling_and_any_drive_case() {
    let table = windows();
    for host in [
        r"C:\Users\air\repo\a\b.txt",
        "C:/Users/air/repo/a/b.txt",
        r"c:\Users\air\repo/a\b.txt",
        r"C:\Users\air\repo\\a\.\b.txt",
    ] {
        assert_eq!(table.map(host).as_deref(), Some("/mnt/AirVmShares/repo/a/b.txt"), "{host}");
    }
    assert_eq!(
        table.map("C:/Users/air/_bazel/execroot/_main/bazel-out/x.jar").as_deref(),
        Some("/mnt/AirVmShares/bazel/execroot/_main/bazel-out/x.jar")
    );
    // The prefix itself is its guest directory.
    assert_eq!(table.map(r"C:\Users\air\repo\").as_deref(), Some("/mnt/AirVmShares/repo"));
}

/// A Windows path has no case, so a Windows prefix matches without ASCII case, and the rest keeps the case of the
/// input.
#[test]
fn a_windows_prefix_matches_without_case_and_the_rest_keeps_its_case() {
    assert_eq!(
        windows().map(r"c:\USERS\Air\Repo\Pkg\Data.txt").as_deref(),
        Some("/mnt/AirVmShares/repo/Pkg/Data.txt")
    );
    assert_eq!(windows().map(r"D:\Users\air\repo\a"), None);
    // Bazel writes its output root in lower case, and the controller knows its true case.
    let bazel = PathMap::new(vec![PathPrefix::new("C:/ProgramData/_bazel", "/mnt/AirVmShares/bazel")]);
    assert_eq!(bazel.map("C:/programdata/_bazel/x").as_deref(), Some("/mnt/AirVmShares/bazel/x"));
    assert_eq!(
        bazel
            .map("C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/Mixed.Case")
            .as_deref(),
        Some("/mnt/AirVmShares/bazel/kxsaieyx/execroot/_main/bazel-out/Mixed.Case")
    );
    assert_eq!(bazel.map("C:/programdata/_bazelx/x"), None);
}

/// A Unix path has case, so a Unix prefix matches only as written.
#[test]
fn a_unix_prefix_matches_with_case() {
    let table = PathMap::new(vec![PathPrefix::identity("/Users/air/repo")]);
    assert_eq!(table.map("/users/air/repo/a"), None);
    assert_eq!(table.map("/Users/Air/repo/a"), None);
    assert_eq!(table.map("/Users/air/repo/A").as_deref(), Some("/Users/air/repo/A"));
}

#[test]
fn a_prefix_matches_only_on_a_whole_component() {
    let table = windows();
    assert_eq!(table.map(r"C:\Users\air\repository\a"), None);
    assert_eq!(table.map(r"C:\Users\air\rep"), None);
    assert_eq!(table.map(r"C:\Users\air"), None);
}

#[test]
fn the_longest_prefix_wins_and_the_first_of_two_equal_ones() {
    let table = PathMap::new(vec![
        PathPrefix::new("C:/a", "/outer"),
        PathPrefix::new("C:/a/b", "/inner"),
        PathPrefix::new("C:/a/b", "/second"),
    ]);
    assert_eq!(table.map("C:/a/b/c").as_deref(), Some("/inner/c"));
    assert_eq!(table.map("C:/a/c").as_deref(), Some("/outer/c"));
    // The order does not decide the length.
    let reversed = PathMap::new(vec![PathPrefix::new("C:/a/b", "/inner"), PathPrefix::new("C:/a", "/outer")]);
    assert_eq!(reversed.map("C:/a/b/c").as_deref(), Some("/inner/c"));
}

/// A Unix host mounts each share at the host's own path, so its table is the identity. A backslash is a character
/// of a Unix file name, not a separator.
#[test]
fn a_unix_table_is_the_identity() {
    let table = PathMap::new(vec![
        PathPrefix::identity("/Users/air/repo"),
        PathPrefix::identity("/private/var/tmp/_bazel_air/"),
    ]);
    for host in [
        "/Users/air/repo/a/b.txt",
        "/private/var/tmp/_bazel_air/execroot/_main/x",
        r"/Users/air/repo/odd\name",
        "/Users/air/repo",
    ] {
        assert_eq!(table.map(host).as_deref(), Some(host), "{host}");
    }
    assert_eq!(table.map("/Users/air/repository"), None);
    assert_eq!(table.map("/etc/passwd"), None);
    // A Unix prefix never matches a Windows path, and the reverse.
    assert_eq!(table.map("C:/Users/air/repo/a"), None);
    assert_eq!(windows().map("/Users/air/repo/a"), None);
}

#[test]
fn a_root_prefix_holds_every_path_of_its_kind() {
    let table = PathMap::new(vec![PathPrefix::new("C:\\", "/mnt/c"), PathPrefix::identity("/")]);
    assert_eq!(table.map(r"C:\x\y").as_deref(), Some("/mnt/c/x/y"));
    assert_eq!(table.map("/x/y").as_deref(), Some("/x/y"));
    assert_eq!(table.map("/").as_deref(), Some("/"));
}

#[test]
fn a_path_that_climbs_out_or_is_not_absolute_is_outside_the_table() {
    let table = windows();
    assert_eq!(table.map(r"C:\Users\air\repo\..\secret"), None);
    for host in ["", "repo/a", r"C:repo\a", r"\\server\share\a", "C:"] {
        assert_eq!(table.map(host), None, "{host:?}");
        assert!(!is_absolute_host_path(host), "{host:?}");
    }
    assert!(is_absolute_host_path(r"C:\a"));
    assert!(is_absolute_host_path("z:/a"));
    assert!(is_absolute_host_path("/a"));
}

/// A prefix that is not absolute on both sides matches nothing, and the table names it for a reader to refuse.
#[test]
fn a_prefix_that_is_not_absolute_is_named_and_matches_nothing() {
    let table = PathMap::new(vec![PathPrefix::new("C:/a", "mnt/a"), PathPrefix::new("a", "/mnt/a")]);
    assert_eq!(table.invalid_prefix(), Some(&table.prefixes[0]));
    assert_eq!(table.map("C:/a/x"), None);
    assert_eq!(windows().invalid_prefix(), None);
}

/// The JSON is the contract between the controller and the guest.
#[test]
fn the_table_travels_as_json() {
    let table = windows();
    let text = serde_json::to_string(&table).unwrap();
    assert_eq!(
        text,
        r#"{"prefixes":[{"host":"C:\\Users\\air\\repo","guest":"/mnt/AirVmShares/repo"},{"host":"c:/Users/air/_bazel","guest":"/mnt/AirVmShares/bazel"}]}"#
    );
    assert_eq!(serde_json::from_str::<PathMap>(&text).unwrap(), table);
}
