use std::io::Cursor;

use pretty_assertions::assert_eq;

use super::*;

fn lines(input: &[u8]) -> Vec<String> {
    let mut reader = Cursor::new(input);
    std::iter::from_fn(|| read_line(&mut reader).unwrap()).collect()
}

#[test]
fn a_line_ends_with_a_line_feed_or_a_carriage_return_and_a_line_feed() {
    assert_eq!(lines(b"one\ntwo\r\nthree"), ["one", "two", "three"]);
    assert_eq!(lines(b"\n\r\n"), ["", ""]);
    assert_eq!(lines(b"lone\rreturn\n"), ["lone\rreturn"]);
    assert_eq!(lines(b""), Vec::<String>::new());
}

#[test]
fn a_byte_that_is_not_utf_8_becomes_the_replacement_character() {
    assert_eq!(lines(b"a\xffb\n"), ["a\u{fffd}b"]);
}
