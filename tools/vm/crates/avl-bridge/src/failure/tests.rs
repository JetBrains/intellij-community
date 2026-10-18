use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;

/// The shape `AirUiTestFailure` takes on the wire: `kotlinx.serialization` with its defaults, so a null `cause` is
/// left out and a null `message` is written.
#[test]
fn a_failure_decodes_with_its_cause_chain_and_reads_as_the_ide_describes_it() {
    let wire = json!({
        "type": "java.lang.IllegalStateException",
        "message": "the composer did not open",
        "stack": ["com.intellij.air.A.open(A.kt:12)", "com.intellij.air.B.run(B.kt:3)"],
        "cause": {"type": "java.util.concurrent.CancellationException", "message": null, "stack": [], "since": 3},
    });
    let failure = BridgeFailure::decode(&wire).unwrap();
    assert_eq!(
        failure,
        BridgeFailure {
            type_name: "java.lang.IllegalStateException".to_owned(),
            message: Some("the composer did not open".to_owned()),
            stack: vec![
                "com.intellij.air.A.open(A.kt:12)".to_owned(),
                "com.intellij.air.B.run(B.kt:3)".to_owned()
            ],
            cause: Some(Box::new(BridgeFailure {
                type_name: "java.util.concurrent.CancellationException".to_owned(),
                message: None,
                stack: Vec::new(),
                cause: None,
                aborted: false,
            })),
            aborted: false,
        }
    );
    assert_eq!(
        failure.to_string(),
        concat!(
            "java.lang.IllegalStateException: the composer did not open\n",
            "\tat com.intellij.air.A.open(A.kt:12)\n",
            "\tat com.intellij.air.B.run(B.kt:3)\n",
            "Caused by: java.util.concurrent.CancellationException"
        )
    );
}

#[test]
fn a_failure_without_its_type_or_with_a_wrong_field_is_refused() {
    for (wire, says) in [
        (json!("boom"), r#"the failure "boom" is not an object"#),
        (json!({"message": "m"}), r#"the failure {"message":"m"} has no text `type`"#),
        (json!({"type": "T", "message": 1}), "the `message` 1 of the failure T is not a text"),
        (
            json!({"type": "T", "stack": "a"}),
            r#"the `stack` "a" of the failure T is not a list"#,
        ),
        (
            json!({"type": "T", "stack": [1]}),
            "the `stack` of the failure T holds a frame that is not a text",
        ),
        (
            json!({"type": "T", "cause": {"stack": []}}),
            r#"the failure {"stack":[]} has no text `type`"#,
        ),
        (
            json!({"type": "T", "aborted": "yes"}),
            r#"the `aborted` "yes" of the failure T is not a boolean"#,
        ),
    ] {
        assert_eq!(BridgeFailure::decode(&wire), Err(says.to_owned()), "{wire}");
    }
}

/// A body that aborts its scenario throws `AirUiTestAbortException`, and the bridge marks its failure aborted. The
/// field crosses only when it is true, so an absent or a null field is no abort.
#[test]
fn a_failure_marked_aborted_decodes_as_an_abort_and_an_unmarked_one_does_not() {
    let failure = |aborted: Option<Value>| {
        let mut wire = json!({
            "type": "com.intellij.air.integration.bridge.AirUiTestAbortException",
            "message": "IJAI-3115: the lane gives no AI source",
            "stack": [],
        });
        if let Some(aborted) = aborted {
            wire["aborted"] = aborted;
        }
        BridgeFailure::decode(&wire).unwrap().aborted
    };
    assert!(failure(Some(json!(true))));
    assert!(!failure(Some(json!(false))));
    assert!(!failure(Some(Value::Null)));
    assert!(!failure(None));
}
