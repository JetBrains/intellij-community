// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{cases_of, scope_args};
use std::path::Path;

#[test]
fn a_path_entry_is_absolute_under_the_anchor_and_a_pattern_stays() {
    let entries = [".", "../../tools/bt", "!**/vm/aom/config/**", "*.rs"].map(str::to_owned);
    assert_eq!(
        scope_args(Path::new("/c/idea/community/build/rust-tools"), &entries),
        [
            "--scope",
            "/c/idea/community/build/rust-tools",
            "--scope",
            "/c/idea/community/tools/bt",
            "--scope",
            "!**/vm/aom/config/**",
            "--scope",
            "*.rs",
        ]
    );
}

/// The cases as (name, failure) pairs.
fn cases(json: &str, guide: &str) -> Vec<(String, Option<String>)> {
    cases_of(json, "noUnusedDeclaration", guide).into_iter().map(|case| (case.name, case.failure)).collect()
}

fn passed(name: &str) -> (String, Option<String>) {
    (name.to_owned(), None)
}

fn failed(name: &str, failure: &str) -> (String, Option<String>) {
    (name.to_owned(), Some(failure.to_owned()))
}

#[test]
fn each_listed_declaration_is_a_failed_case_named_without_its_line() {
    let json = r#"{"total": 2, "complete": true, "quality": "heuristic", "note": "a lead, not a proof", "hits": [
        {"file": "plugins/air/a.kt", "line": 27, "kind": "method", "name": "fromAgentPath", "parent": "AcpPathConverter", "via": ["x"]},
        {"file": "plugins/air/messages/AirBundle.properties", "line": 3, "kind": "key", "name": "air.unused"}
    ]}"#;
    let advice = "Nothing uses it. Delete it, or keep it with the mark of its language that `accio unused --help` names. \
                  See the guide.";
    assert_eq!(
        cases(json, "See the guide."),
        [
            passed("noUnusedDeclaration"),
            failed("plugins/air/a.kt method AcpPathConverter.fromAgentPath", &format!("plugins/air/a.kt:27: {advice}")),
            failed(
                "plugins/air/messages/AirBundle.properties key air.unused",
                &format!("plugins/air/messages/AirBundle.properties:3: {advice}")
            ),
        ]
    );
}

#[test]
fn a_name_that_two_declarations_share_gets_the_line_of_the_later_one() {
    let json = r#"{"complete": true, "hits": [
        {"file": "a.kt", "line": 4, "kind": "function", "name": "f"},
        {"file": "a.kt", "line": 9, "kind": "function", "name": "f"}
    ]}"#;
    let names: Vec<String> = cases(json, "").into_iter().map(|(name, _)| name).collect();
    assert_eq!(names, ["noUnusedDeclaration", "a.kt function f", "a.kt function f (line 9)"]);
}

#[test]
fn an_empty_complete_answer_passes() {
    assert_eq!(cases(r#"{"total": 0, "complete": true, "hits": []}"#, ""), [passed("noUnusedDeclaration")]);
}

#[test]
fn an_answer_that_is_not_complete_or_not_available_fails_its_own_case_with_its_note() {
    assert_eq!(
        cases(r#"{"complete": false, "note": "cut by --limit", "hits": []}"#, ""),
        [failed("noUnusedDeclaration", "the answer of accio unused is not complete: cut by --limit")]
    );
    assert_eq!(
        cases(r#"{"available": false, "note": "no index"}"#, ""),
        [failed("noUnusedDeclaration", "accio unused has no answer: no index")]
    );
}

#[test]
fn text_instead_of_an_answer_fails_its_own_case_with_the_text() {
    let [(name, failure)]: [(String, Option<String>); 1] = cases("Error: no index at /h", "").try_into().expect("one case");
    assert_eq!(name, "noUnusedDeclaration");
    let failure = failure.expect("a failure");
    assert!(failure.starts_with("accio unused printed no answer ("), "{failure}");
    assert!(failure.ends_with("):\nError: no index at /h"), "{failure}");
}
