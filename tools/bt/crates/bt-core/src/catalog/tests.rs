use pretty_assertions::assert_eq;

use super::*;
use crate::fake::lanes;

/// One document with a setup, two steps and ids that repeat or are empty, as the generator writes them.
const DOCUMENT: &str = r#"{
  "suite": "rename-session",
  "lane": "UI_REAL",
  "testClassName": "RenameSessionUiTest",
  "modules": ["intellij.air.frontend"],
  "implementationFlows": ["flow-impl-rename"],
  "routes": ["ignored by every reader here"],
  "profiles": [
    {
      "name": "renames-a-session",
      "flow": "flow-rename-session",
      "setups": [{"operation": "open-project", "instructions": [{"action": "open"}]}],
      "steps": [
        {
          "flow": "flow-rename-session",
          "step": "rename",
          "operations": [
            {"operation": "type-name", "instructions": [{"action": "type"}, {"assertion": "name-shown"}]}
          ]
        },
        {
          "flow": "flow-sidebar",
          "step": "check",
          "operations": [{"operation": "open-project", "instructions": [{"assertion": ""}]}]
        }
      ]
    }
  ]
}"#;

/// A profile's program ids are its setups' and its steps' operations and instructions, once each, sorted, with
/// no empty id: the keys a trace's spans carry.
#[test]
fn a_profile_answers_every_id_of_its_program_setups_included() {
    let document = parse_suite_document(DOCUMENT.as_bytes()).expect("the document parses");
    let profile = &document.profiles[0];
    assert_eq!(profile.name, "renames-a-session");
    assert_eq!(
        profile.program_ids(),
        ["name-shown", "open", "open-project", "type", "type-name"]
    );
    let steps: Vec<&str> = profile
        .steps
        .iter()
        .map(|step| step.step.as_str())
        .collect();
    assert_eq!(steps, ["rename", "check"]);
    assert_eq!(
        profile.scenario_flows().flows(),
        ["flow-rename-session", "flow-sidebar"]
    );
    assert_eq!(document.modules, ["intellij.air.frontend"]);
    assert_eq!(document.implementation_flows, ["flow-impl-rename"]);
    assert!(document.path.is_empty() && !document.authored);
}

/// A document's lane is the catalog's word, and the lane name comes from the lane table.
#[test]
fn a_documents_lane_is_the_controllers_lane_name() {
    let document = parse_suite_document(DOCUMENT.as_bytes()).expect("the document parses");
    assert_eq!(document.lane_name(lanes()), Some("ui-real"));
    let unknown = SuiteDocument {
        lane: "HEADLESS".to_owned(),
        ..document
    };
    assert_eq!(unknown.lane_name(lanes()), None);
}

/// A document is one only with its suite, its lane and its test class, and bytes that are no JSON are refused as
/// malformed.
#[test]
fn a_document_without_its_three_keys_is_refused() {
    for content in [
        r#"{"lane": "UI", "testClassName": "A"}"#,
        r#"{"suite": "a", "testClassName": "A"}"#,
        r#"{"suite": "a", "lane": "UI"}"#,
    ] {
        assert_eq!(
            parse_suite_document(content.as_bytes()).unwrap_err(),
            "names no suite, lane or test class",
            "{content}"
        );
    }
    let malformed = parse_suite_document(b"{\"suite\": ").unwrap_err();
    assert!(malformed.starts_with("is malformed: "), "{malformed}");
}
