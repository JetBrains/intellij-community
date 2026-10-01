use super::*;

/// A refusal shows its message, and it keeps the code and the exit code that a caller branches on.
#[test]
fn a_refusal_shows_its_message_and_keeps_its_code_and_exit() {
    let refused = Refusal::new("usage", 2, "Unknown option: --nope");
    assert_eq!(refused.to_string(), "Unknown option: --nope");
    assert_eq!((refused.code.as_ref(), refused.exit), ("usage", 2));
    assert_eq!(refused.details_json_text(), None);
}

/// A code can also be text that the tool composes at run time.
#[test]
fn a_code_can_be_owned_text() {
    let refused = Refusal::new(format!("{}_infra", "bt"), 6, "broken");
    assert_eq!(refused.code, "bt_infra");
}

/// The details are the JSON text that the caller gives, unchanged.
#[test]
fn the_details_are_the_given_json_text() {
    let refused = Refusal::new("no_affected_suite", 2, "nothing").with_details_json_text(r#"{"flow":"flow-x","lanes":["ui"]}"#);
    assert_eq!(refused.details_json_text(), Some(r#"{"flow":"flow-x","lanes":["ui"]}"#));
}
