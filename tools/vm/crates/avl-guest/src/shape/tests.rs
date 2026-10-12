use super::*;

/// The two lines are the loaders' own: the Tart base (Ubuntu 24.04) and the Docker base (Ubuntu 26.04).
#[test]
fn the_release_is_the_first_one_after_version() {
    for (text, release) in [
        ("ld.so (Ubuntu GLIBC 2.39-0ubuntu8.8) stable release version 2.39.", Some("2.39")),
        ("ld.so (Ubuntu GLIBC 2.43-2ubuntu2.4) stable release version 2.43.", Some("2.43")),
        ("version 2.39.1 and more", Some("2.39.1")),
        ("version 2.39.x", Some("2.39")),
        ("version 2, then version 3.1", Some("3.1")),
        ("version 2.", None),
        ("version .39", None),
        ("versions 2.39", None),
        ("no release", None),
        ("", None),
    ] {
        assert_eq!(release_after_version(text), release, "{text:?}");
    }
}
