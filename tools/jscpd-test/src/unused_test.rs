// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{failure_of, scope_args};
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

#[test]
fn each_listed_declaration_fails_with_its_place() {
    let json = r#"{"total": 2, "complete": true, "quality": "heuristic", "note": "a lead, not a proof", "hits": [
        {"file": "plugins/air/a.kt", "line": 27, "kind": "method", "name": "fromAgentPath", "parent": "AcpPathConverter", "via": ["x"]},
        {"file": "plugins/air/messages/AirBundle.properties", "line": 3, "kind": "key", "name": "air.unused"}
    ]}"#;
    assert_eq!(
        failure_of(json, "See the guide.").as_deref(),
        Some(
            "accio unused lists 2 declarations that nothing uses. Delete each one, or keep it with the mark of its \
             language that `accio unused --help` names. See the guide.\n\
             plugins/air/a.kt:27 method AcpPathConverter.fromAgentPath\n\
             plugins/air/messages/AirBundle.properties:3 key air.unused"
        )
    );
}

#[test]
fn an_empty_complete_answer_passes() {
    assert_eq!(failure_of(r#"{"total": 0, "complete": true, "hits": []}"#, ""), None);
}

#[test]
fn an_answer_that_is_not_complete_or_not_available_fails_with_its_note() {
    assert_eq!(
        failure_of(r#"{"complete": false, "note": "cut by --limit", "hits": []}"#, "").as_deref(),
        Some("the answer of accio unused is not complete: cut by --limit")
    );
    assert_eq!(
        failure_of(r#"{"available": false, "note": "no index"}"#, "").as_deref(),
        Some("accio unused has no answer: no index")
    );
}

#[test]
fn text_instead_of_an_answer_fails_with_the_text() {
    let failure = failure_of("Error: no index at /h", "").expect("a failure");
    assert!(failure.starts_with("accio unused printed no answer ("), "{failure}");
    assert!(failure.ends_with("):\nError: no index at /h"), "{failure}");
}
