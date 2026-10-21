use super::*;

// A digest is 64 lowercase hex digits. An empty or a shorter or a longer text, an uppercase digit and a prefix are not
// digests.
#[test]
fn only_64_lowercase_hex_digits_are_a_digest() {
    assert!(is_sha256_hex(&"b".repeat(64)));
    assert!(is_sha256_hex(&"0123456789abcdef".repeat(4)));
    for refused in [
        String::new(),
        "b".repeat(63),
        "b".repeat(65),
        "B".repeat(64),
        format!("0x{}", "b".repeat(62)),
    ] {
        assert!(!is_sha256_hex(&refused), "{refused:?} was accepted as a sha256");
    }
}
