use serde_json::json;

use super::*;

#[test]
fn the_helpers_build_the_documented_refusals() {
    let usage = Refusal::usage(format!("--lane {} is unknown", "x"));
    assert_eq!((usage.code.as_ref(), usage.exit), ("usage", Exit::USAGE));
    assert_eq!(usage.message, "--lane x is unknown");
    let environment = Refusal::invalid_environment("AIR_VM_CPU must be a positive integer");
    assert_eq!((environment.code.as_ref(), environment.exit), ("invalid_environment", Exit::USAGE));
    let detailed = Refusal::new("lease_busy", Exit::TEMP_FAIL, format!("held by {}", "shard-1")).with_details(json!({"holder": "shard-1"}));
    assert_eq!(detailed.details(), Some(json!({"holder": "shard-1"})));
    assert_eq!(detailed.details_json_text(), Some(r#"{"holder":"shard-1"}"#));

    let io: Result<(), std::io::Error> = Err(std::io::Error::other("denied"));
    let refused = io
        .or_refuse("state_write_failed", Exit::FAILURE, || "cannot create /x".to_owned())
        .expect_err("an error is refused");
    assert_eq!(refused.message, "cannot create /x: denied");
}

#[test]
fn a_guest_status_is_kept_unless_it_is_not_a_failure() {
    assert_eq!(Exit::from_status(64, Exit::SOFTWARE).code(), 64);
    assert_eq!(Exit::from_status(0, Exit::SOFTWARE), Exit::SOFTWARE);
    assert_eq!(Exit::from_status(-1, Exit::FAILURE), Exit::FAILURE);
    assert_eq!(Exit::from_status(300, Exit::FAILURE), Exit::FAILURE);
    assert_eq!(Exit::TESTS_FAILED.code(), 6);
    assert_eq!(Exit::BUILD_FAILED.code(), 5);
}

/// The details sort the keys of an object, as the envelope wrote them when they were a value, and text that is no
/// JSON value arrives as the reason.
#[test]
fn the_details_are_a_value_with_sorted_keys() {
    #[derive(Serialize)]
    struct Evidence {
        worker: &'static str,
        holder: &'static str,
    }
    let refused = Refusal::new("lease_busy", Exit::TEMP_FAIL, "busy").with_details(Evidence {
        worker: "w1",
        holder: "shard-1",
    });
    assert_eq!(refused.details_json_text(), Some(r#"{"holder":"shard-1","worker":"w1"}"#));
    assert_eq!(Refusal::new("x", Exit::FAILURE, "x").details(), None);
    let broken = Refusal::new("x", Exit::FAILURE, "x").with_details_json_text("{");
    assert!(
        broken
            .details()
            .is_some_and(|value| value.as_str().is_some_and(|text| text.starts_with("the details did not parse"))),
        "{broken:?}"
    );
}

#[test]
fn a_runtime_descriptor_refusal_keeps_its_code_and_is_the_softwares_fault() {
    let refused = avl_wire::runtime::parse_runtime_descriptor(b"[]", std::path::Path::new("/out/ui_daemon.runtime.json"), "linux-x64")
        .map_err(descriptor_refusal)
        .unwrap_err();
    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        (avl_wire::runtime::code::SCHEMA_VERSION, Exit::SOFTWARE)
    );
    assert!(refused.message.contains("schemaVersion is absent"), "{}", refused.message);
}
