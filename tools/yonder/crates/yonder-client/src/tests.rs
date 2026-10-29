use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_refusal_leaves_by_255() {
    assert_eq!(refused(code::UNREACHABLE, "x").exit.code(), 255);
}

#[test]
fn a_size_reads_in_the_unit_that_fits() {
    assert_eq!(size_text(512), "512 B");
    assert_eq!(size_text(2048), "2.0 KiB");
    assert_eq!(size_text(3 * 1_048_576 + 400_000), "3.4 MiB");
}

#[test]
fn a_quoted_value_keeps_its_own_quotes() {
    assert_eq!(shell_quote("it's"), r"'it'\''s'");
}

/// One holder per checkout: the same path keeps its slot, and another path gets its own.
#[test]
fn each_checkout_has_its_own_stable_holder() {
    let first = holder("yonder", Path::new("/pool/1/idea-1"));
    assert_eq!(first, holder("yonder", Path::new("/pool/1/idea-1")));
    assert_ne!(first, holder("yonder", Path::new("/pool/2/idea-1")));
    assert!(first.starts_with("yonder-"), "{first}");
    assert_eq!(first.len(), "yonder-".len() + 12);
}
