use std::thread;

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

/// The first calls of many threads at once answer one identity, and its key and its certificate belong together.
#[test]
fn first_calls_at_the_same_time_answer_one_identity() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let created: Vec<Identity> = thread::scope(|scope| {
        // All calls start before the first join, so they run at the same time.
        let mut calls = Vec::new();
        for _ in 0..8 {
            calls.push(scope.spawn(|| Identity::load_or_create(dir.path())));
        }
        calls
            .into_iter()
            .map(|call| call.join().expect("a thread").expect("an identity"))
            .collect()
    });
    let loaded = Identity::load(dir.path()).expect("loaded");
    for identity in &created {
        assert_eq!(
            (&identity.cert_der, &identity.key_der),
            (&loaded.cert_der, &loaded.key_der),
            "each call answers the pair on disk"
        );
    }
    let names: Vec<String> = fs::read_dir(dir.path())
        .expect("the directory")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .filter(|name| name.contains(".tmp-"))
        .collect();
    assert!(names.is_empty(), "temporary files stay: {names:?}");
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
