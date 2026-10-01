//! The drive-letter test of a forward-slash path. `distpath` holds the slash-path arithmetic, `clean`, `join` and `dir`.
//!
//! Pure string work rather than `std::path`, for the reason [`crate::Runtime::platform`] is injectable: `Path`
//! follows the host that compiled this binary, not the platform under test, so a mac host would not parse `C:\…`
//! and no Windows case could be driven from anywhere else.

/// Whether a path starts with a drive letter and a colon, `C:`.
pub const fn has_windows_drive(path: &str) -> bool {
    let bytes = path.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}
