use pretty_assertions::assert_eq;

use super::*;

const ABC: &str = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

#[test]
fn the_digest_of_parts_is_the_digest_of_the_whole_and_a_file_has_the_digest_of_its_bytes() {
    let mut digest = Sha256::default();
    digest.update(b"a");
    digest.update(b"bc");
    assert_eq!(digest.hex(), ABC);

    let dir = tempfile::tempdir().expect("a temporary directory");
    let file = dir.path().join("yonderd");
    std::fs::write(&file, b"abc").expect("a file");
    assert_eq!(sha256_file(&file).expect("the digest"), ABC);
    sha256_file(&dir.path().join("missing")).expect_err("no file");
}

#[test]
fn a_digest_text_is_64_lower_case_hex_digits() {
    assert!(is_sha256(ABC));
    for bad in ["", "abc", &ABC.to_uppercase(), &format!("{ABC}0"), &ABC.replace('a', "g")] {
        assert!(!is_sha256(bad), "{bad:?}");
    }
}
