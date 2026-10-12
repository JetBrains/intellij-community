use avl_base::{Exit, RefusalExt};
use bt_core::WithDetails;
use pretty_assertions::assert_eq;

use super::*;

/// The details cross between the two builds of `serde_json` as text, and arrive as the same value.
#[test]
fn a_refusal_keeps_its_code_exit_message_and_details() {
    let refused = bt_core::Refusal::new("no_affected_suite", bt_core::exit::USAGE, "no suite").with_details(bt_core::suites::Affected {
        lanes: vec!["ui".to_owned()],
        ..bt_core::suites::Affected::default()
    });
    let converted = refusal(refused);
    assert_eq!(converted.code, "no_affected_suite");
    assert_eq!(converted.message, "no suite");
    assert_eq!(converted.exit, Exit::USAGE);
    assert_eq!(
        converted.details(),
        Some(serde_json::json!({"suites": [], "unmapped": [], "lanes": ["ui"]}))
    );

    let bare = refusal(bt_core::fail_infra("broken"));
    assert_eq!((bare.code.as_ref(), bare.exit), ("bt_infra", Exit::DATA_ERR));
    assert_eq!(bare.details(), None);
}

/// Each exit that BT names becomes the controller's exit of the same meaning. BT's 6 is no red lane.
#[test]
fn every_bt_exit_maps_by_its_meaning() {
    use bt_core::exit as bt;
    let mapped: Vec<(u8, Exit)> = [bt::GREEN, bt::USAGE, bt::TEST_FAILED, bt::NO_TESTS, bt::BUILD_FAILED, bt::INFRA, 42]
        .into_iter()
        .map(|code| (code, exit(code)))
        .collect();
    assert_eq!(
        mapped,
        [
            (0, Exit::OK),
            (2, Exit::USAGE),
            (3, Exit::TESTS_FAILED),
            (4, Exit::TESTS_FAILED),
            (5, Exit::BUILD_FAILED),
            (6, Exit::DATA_ERR),
            (42, Exit::FAILURE),
        ]
    );
}

/// The fixture that the tests install is the Air area: the same directory and the same lane table file.
#[test]
fn the_fixture_is_the_air_area() {
    assert_eq!((bt_core::fake::AREA_DIR, bt_core::fake::LANES_FILE), (AIR_DIR, AIR_LANES_FILE));
    install_fixture();
    assert_eq!(air_area().dir(), AIR_DIR);
    assert!(air_area().lanes().get("fast").is_some());
}

/// Areas without the Air area are refused, and the refusal names the file that `install` reads.
#[test]
fn areas_without_the_air_area_are_refused() {
    let areas: &'static Areas = Box::leak(Box::new(Areas::default()));
    let refused = install_areas(areas).expect_err("no Air area");
    assert_eq!((refused.code.as_ref(), refused.exit), ("air_area_missing", Exit::DATA_ERR));
    assert!(refused.message.contains("bt.json"), "{}", refused.message);
}
