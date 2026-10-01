use refusal::Refusal;

use crate::details::WithDetails;
use crate::exit;
use crate::fake::refusal;
use crate::selector::Selector;

/// The six codes are a published contract (the Air testing reference documents them and an agent's shell branches
/// on them), so they are pinned by value rather than by name.
#[test]
fn the_exit_code_vocabulary_is_pinned_by_value() {
    assert_eq!(
        [
            exit::GREEN,
            exit::USAGE,
            exit::TEST_FAILED,
            exit::NO_TESTS,
            exit::BUILD_FAILED,
            exit::INFRA
        ],
        [0, 2, 3, 4, 5, 6]
    );
}

/// A refusal carries a code as well as a number, so a caller can convert it into its own envelope.
#[test]
fn a_refusal_carries_a_code_and_bts_own_exit_code() {
    let failure = refusal(Selector::classify("not a selector"));
    assert_eq!(failure.code, "usage");
    assert_eq!(failure.exit, exit::USAGE);
    assert!(failure.message.contains("Cannot interpret selector"), "{}", failure.message);
}

/// The details cross into another build of `serde_json` as text, so the text must be the value's own JSON.
#[test]
fn the_details_travel_as_json_text() {
    let refused =
        Refusal::new("no_affected_suite", exit::USAGE, "nothing").with_details(serde_json::json!({"flow": "flow-x", "lanes": ["ui"]}));
    assert_eq!(refused.details_json_text(), Some(r#"{"flow":"flow-x","lanes":["ui"]}"#));
    assert_eq!(Refusal::new("usage", exit::USAGE, "bare").details_json_text(), None);
}
