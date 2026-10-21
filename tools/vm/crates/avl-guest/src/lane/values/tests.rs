use std::collections::BTreeMap;

use pretty_assertions::assert_eq;
use serde_json::{Map, Value, json};

use super::*;

fn spec(answers: &[&str], may_answer: &[&str], takes: &[&str], may_take: &[&str]) -> IdSpec {
    let names = |names: &[&str]| names.iter().map(|name| (*name).to_owned()).collect::<Vec<_>>();
    IdSpec {
        side: avl_wire::program::Side::Ide,
        timeout_ms: 1_000,
        restarts: 0,
        answers: names(answers),
        may_answer: names(may_answer),
        takes: names(takes),
        may_take: names(may_take),
        params: BTreeMap::new(),
    }
}

// An id gets each value it takes as a field, and each value it may take when one exists. A missing value fails with
// a sentence that names the value and the instruction.
#[test]
fn a_request_gets_the_values_it_takes_and_a_missing_one_is_named() {
    let values = Values::new([
        ("consentSurface".to_owned(), json!("PROMPT")),
        ("latestSessionTab".to_owned(), Value::Null),
    ]);
    let fields = values
        .request_fields(
            "consent-prompt",
            &spec(&[], &[], &["consentSurface"], &["latestSessionTab", "consentDraft"]),
        )
        .unwrap();
    assert_eq!(Value::Object(fields), json!({"consentSurface": "PROMPT"}));
    assert_eq!(
        values
            .request_fields("consent-click", &spec(&[], &[], &["consentBannerTarget"], &[]))
            .unwrap_err(),
        "the instruction 'consent-click' takes the value 'consentBannerTarget', and no earlier instruction answered it"
    );
}

// An answered value replaces the old one, null included, so a later take of a null answer fails. A value that the id
// may answer keeps the old value when the answer leaves it out or answers null.
#[test]
fn an_answer_keeps_its_values_and_a_may_answer_keeps_the_old_one_on_null() {
    let mut values = Values::new([("session".to_owned(), json!("old")), ("draft".to_owned(), json!("kept"))]);
    let answer = json!({"session": null, "draft": null, "other": 1});
    values
        .keep_answers("open", &spec(&["session"], &["draft"], &[], &[]), &answer)
        .unwrap();
    assert_eq!(values.get("session"), None);
    assert_eq!(values.get("draft"), Some(&json!("kept")));
    assert_eq!(values.get("other"), None);
    let refused = values
        .keep_answers("open", &spec(&["session"], &[], &[], &[]), &json!("text"))
        .unwrap_err();
    assert!(refused.contains("which is no object"), "{refused}");
}

// A part gets exactly the values its instruction takes, and must answer exactly the values it declares.
#[test]
fn a_part_takes_and_answers_exactly_its_declared_values() {
    let mut values = Values::new([]);
    let missing = values.part_values("suite/scenario", &["workflow".to_owned()]).unwrap_err();
    assert_eq!(
        missing,
        "the part 'suite/scenario' takes the value 'workflow', and no earlier part answered it"
    );
    let mut answered = Map::new();
    answered.insert("workflow".to_owned(), json!({"id": 7}));
    values
        .keep_part_values("suite/scenario", &answered, &["workflow".to_owned()])
        .unwrap();
    assert_eq!(
        Value::Object(values.part_values("suite/scenario", &["workflow".to_owned()]).unwrap()),
        json!({"workflow": {"id": 7}})
    );
    let wrong = values.keep_part_values("suite/scenario", &answered, &[]).unwrap_err();
    assert_eq!(
        wrong,
        "the part 'suite/scenario' answered the values [workflow], and it declares []"
    );
}
