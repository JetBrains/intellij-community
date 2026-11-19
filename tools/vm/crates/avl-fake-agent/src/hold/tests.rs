use pretty_assertions::assert_eq;

use super::*;

/// The marker names any path, also one with a space or a bracket, and a text without a valid marker names no file.
#[test]
fn the_hold_marker_names_its_file() {
    let directory = tempfile::tempdir().unwrap();
    for name in ["hold", "a hold", "hold]", "[hold] 1", "h\u{f6}ld"] {
        let file = std::path::absolute(directory.path().join(name)).unwrap();
        assert_eq!(hold_file_of(&format!("work {} more", marker(&file))), Some(file), "{name}");
    }
    assert_eq!(hold_file_of("work"), None);
    assert_eq!(hold_file_of("work [hold-while-exists:not a uri]"), None);
    assert_eq!(hold_file_of("work [hold-while-exists:https://example.com/hold]"), None);
    assert_eq!(hold_file_of("work [hold-while-exists:file://host/hold]"), None);
    assert_eq!(hold_file_of("work [hold-while-exists:file:///hold%2]"), None);
}

/// `AcpPromptHold` writes the URI of `Path.toUri`, which keeps a character outside ASCII as it is.
#[cfg(unix)]
#[test]
fn a_marker_of_the_java_side_names_its_file() {
    assert_eq!(
        hold_file_of("[hold-while-exists:file:///tmp/a%20h\u{f6}ld%5B1%5D]"),
        Some(PathBuf::from("/tmp/a h\u{f6}ld[1]"))
    );
    assert_eq!(hold_file_of("[hold-while-exists:file:/tmp/hold]"), Some(PathBuf::from("/tmp/hold")));
}
