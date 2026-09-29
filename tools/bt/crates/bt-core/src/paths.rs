//! Forward-slash path arithmetic on strings, for both dialects.
//!
//! Pure string work rather than `std::path`, for the reason [`crate::Runtime::platform`] is injectable: `Path`
//! follows the host that compiled this binary, not the platform under test, so a mac host would not parse `C:\…`
//! and no Windows case could be driven from anywhere else.

/// `path` with `.` and `..` segments resolved and repeated separators collapsed: a rooted `..` stays at the root, an unrooted one is kept, and an empty result is `.`.
pub fn clean(path: &str) -> String {
    let rooted = path.starts_with('/');
    let mut segments: Vec<&str> = Vec::new();
    for segment in path.split('/') {
        match segment {
            "" | "." => {}
            ".." => match segments.last() {
                Some(&last) if last != ".." => {
                    segments.pop();
                }
                _ if rooted => {}
                _ => segments.push(".."),
            },
            other => segments.push(other),
        }
    }
    let joined = segments.join("/");
    match (rooted, joined.is_empty()) {
        (true, _) => format!("/{joined}"),
        (false, true) => ".".to_owned(),
        (false, false) => joined,
    }
}

/// `base` and `relative` joined and cleaned.
pub fn join(base: &str, relative: &str) -> String {
    clean(&format!("{base}/{relative}"))
}

/// The directory part of a forward-slash path, `.` when it has none.
pub fn dir(path: &str) -> String {
    match path.rsplit_once('/') {
        Some(("", _)) => "/".to_owned(),
        Some((head, _)) => clean(head),
        None => ".".to_owned(),
    }
}

/// Whether a path starts with a drive letter and a colon, `C:`.
pub const fn has_windows_drive(path: &str) -> bool {
    let bytes = path.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

#[cfg(test)]
mod tests {
    use super::{clean, dir, join};

    #[test]
    fn clean_follows_the_go_rules() {
        for (raw, want) in [
            ("", "."),
            ("a/b/../c", "a/c"),
            ("/../a", "/a"),
            ("../a", "../a"),
            ("a/../..", ".."),
            ("/repo//plugins/./air/", "/repo/plugins/air"),
            ("C:/repo/../x", "C:/x"),
        ] {
            assert_eq!(clean(raw), want, "{raw}");
        }
        assert_eq!(join("/repo", "../other"), "/other");
        assert_eq!(dir("a/b/c.kt"), "a/b");
        assert_eq!(dir("c.kt"), ".");
    }
}
