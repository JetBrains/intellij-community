use pretty_assertions::assert_eq;

use super::*;

/// The test vectors of RFC 4648, section 10.
const RFC_4648: [(&str, &str); 7] = [
    ("", ""),
    ("f", "Zg=="),
    ("fo", "Zm8="),
    ("foo", "Zm9v"),
    ("foob", "Zm9vYg=="),
    ("fooba", "Zm9vYmE="),
    ("foobar", "Zm9vYmFy"),
];

#[test]
fn the_standard_alphabet_answers_the_vectors_of_rfc_4648_with_padding() {
    for (bytes, text) in RFC_4648 {
        assert_eq!(Base64::Standard.encode(bytes.as_bytes()), text);
        assert_eq!(Base64::Standard.decode(text), Some(bytes.as_bytes().to_vec()), "{text}");
    }
    assert_eq!(Base64::Standard.encode(&[0xfb, 0xff]), "+/8=");
}

#[test]
fn the_url_alphabet_has_no_padding() {
    for (bytes, text) in RFC_4648 {
        let unpadded = text.trim_end_matches('=');
        assert_eq!(Base64::Url.encode(bytes.as_bytes()), unpadded);
        assert_eq!(Base64::Url.decode(unpadded), Some(bytes.as_bytes().to_vec()), "{unpadded}");
    }
    assert_eq!(Base64::Url.encode(&[0xfb, 0xff]), "-_8");
}

#[test]
fn a_text_outside_the_alphabet_or_with_a_misplaced_padding_decodes_to_nothing() {
    for text in ["Zg=", "Z===", "Zm9v=", "Zg==Zg==", "-_8=", "Zm 9v"] {
        assert_eq!(Base64::Standard.decode(text), None, "{text}");
    }
    for text in ["Zg==", "+/8", "Z"] {
        assert_eq!(Base64::Url.decode(text), None, "{text}");
    }
}

fn hex(digest: [u8; 20]) -> String {
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

#[test]
fn sha1_answers_the_vectors_of_fips_180() {
    assert_eq!(hex(sha1(b"")), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
    assert_eq!(hex(sha1(b"abc")), "a9993e364706816aba3e25717850c26c9cd0d89d");
    assert_eq!(
        hex(sha1(b"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")),
        "84983e441c3bd26ebaae4aa1f95129e5e54670f1"
    );
    assert_eq!(hex(sha1(&vec![b'a'; 1_000_000])), "34aa973cd4c4daa4f61eeb2bdbad27316534016f");
}
