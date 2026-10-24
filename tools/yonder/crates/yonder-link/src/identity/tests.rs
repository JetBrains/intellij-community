use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_fingerprint_round_trips_through_its_hex_text() {
    let fingerprint = Fingerprint::of(b"a certificate");
    let text = fingerprint.to_string();
    assert_eq!(text.len(), 64);
    assert_eq!(text.parse::<Fingerprint>(), Ok(fingerprint));
    assert_eq!(fingerprint.short(), text[..12]);
    assert_eq!(serde_json::to_string(&fingerprint).expect("json"), format!("\"{text}\""));
}

#[test]
fn a_text_that_is_not_64_hex_digits_is_refused() {
    for text in ["", "abc", &"g".repeat(64), &"a".repeat(63), &"é".repeat(32)] {
        assert!(text.parse::<Fingerprint>().is_err(), "{text:?}");
    }
}

#[test]
fn an_identity_is_created_once_and_loaded_after() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let created = Identity::load_or_create(dir.path()).expect("created");
    let loaded = Identity::load_or_create(dir.path()).expect("loaded");
    assert_eq!(loaded.fingerprint(), created.fingerprint());
    assert_ne!(Identity::generate().expect("another").fingerprint(), created.fingerprint());
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt as _;
        for name in [KEY_FILE, CERT_FILE] {
            let mode = fs::metadata(dir.path().join(name)).expect("metadata").permissions().mode();
            assert_eq!(mode & 0o777, 0o600, "{name}");
        }
    }
}
