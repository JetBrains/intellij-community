use bt_core::lanes::Lanes;
use pretty_assertions::assert_eq;

use super::*;

fn lanes() -> &'static Lanes {
    crate::bridge::install_fixture();
    air_area().lanes()
}

/// `bt` declares the integration lanes with the label and the JUnit tag of each. A lane `bt` declares without
/// them would silently be one a VM worker cannot run.
#[test]
fn every_integration_lane_bt_declares_is_runnable() {
    crate::bridge::install_fixture();
    assert_eq!(
        ui_lane_names(),
        lanes().integration_lane_names(),
        "an integration lane of bt has no testLabel or junitTag in plugins/air/tests/integration/lanes.json"
    );
}

/// Every UI lane's own test target reads as that lane, and a unit-test target as none: the relation the trace
/// planner uses to tell which lane a resolved class belongs to.
#[test]
fn a_ui_lanes_own_target_reads_as_that_lane() {
    crate::bridge::install_fixture();
    for lane in ui_lanes() {
        assert_eq!(
            ui_lane_of_label(lane.label).map(|found| found.name),
            Some(lane.name),
            "{} is {}'s target",
            lane.label,
            lane.name
        );
        let spec = lanes().get(lane.name).expect("a UI lane is a lane");
        let package = spec.targets[0].split(':').next().unwrap_or_default();
        assert!(
            lane.label.starts_with(package.trim_end_matches("/...")),
            "{} is outside {}'s target {}",
            lane.label,
            lane.name,
            spec.targets[0]
        );
    }
    assert_eq!(
        ui_lane_of_label("//plugins/air/backend/acp:air-backend-acp-tests_test"),
        None,
        "a unit-test target reads as a lane"
    );
}

/// A lane without a catalog lane runs only when a caller names it. `ui-live` is the one: each of its scenarios spends
/// a billed turn on a real account, so no flow, suite or changed path may reach it (ADR 0200).
#[test]
fn only_the_live_lane_is_explicit_only() {
    crate::bridge::install_fixture();
    assert_eq!(explicit_only_lane_names(), ["ui-live"]);
    for lane in ui_lanes() {
        let spec = lanes().get(lane.name).expect("a UI lane is a lane");
        assert_eq!(lane.explicit_only, spec.catalog_lane.is_none(), "{}", lane.name);
    }
}
