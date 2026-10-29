use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_lane_is_the_target_and_its_shard() {
    assert_eq!(
        testlogs_lane(Path::new("plugins/air/tests/integration/ui/ui_lane_test")),
        "ui_lane_test"
    );
    assert_eq!(testlogs_lane(Path::new("a/ui_lane_test/shard_2_of_4")), "ui_lane_test/shard_2_of_4");
    assert_eq!(testlogs_lane(Path::new("a/ui_lane_test/run_1_of_3")), "ui_lane_test/run_1_of_3");
    assert_eq!(testlogs_lane(Path::new("/testlogs/plugins/air/ui/ui_lane_test")), "ui_lane_test");
    assert_eq!(testlogs_lane(Path::new("")), "tests");
}

// A failed bundle has the artifact directory of its path, and its failure picture keeps the extension of its still.
#[test]
fn a_failed_bundle_names_its_artifact_directory_and_its_failure_picture() {
    assert_eq!(
        failed_target("ui_lane_test", "iter-1/AirComposerUiTest", "attaches_a_file"),
        "air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/attaches_a_file"
    );
    assert_eq!(failure_picture("snap/0012.webp"), "failure.webp");
    assert_eq!(failure_picture("snap/0012.png"), "failure.png");
    assert_eq!(failure_picture("still"), "failure.webp");
}

// A later bundle of the same path takes the next free stem, and the same scenario in another class or lane does not.
#[test]
fn a_later_bundle_of_the_same_path_takes_the_next_free_stem() {
    let mut stems = Stems::default();
    assert_eq!(stems.take("ui_lane_test", "iter-1/AirComposerUiTest", "attaches"), "attaches");
    assert_eq!(stems.take("ui_lane_test", "iter-1/AirComposerUiTest", "attaches"), "attaches.2");
    assert_eq!(stems.take("ui_lane_test", "iter-1/AirComposerUiTest", "attaches"), "attaches.3");
    assert_eq!(stems.take("ui_lane_test", "iter-1/AirOtherUiTest", "attaches"), "attaches");
    assert_eq!(stems.take("ui_real_lane_test", "iter-1/AirComposerUiTest", "attaches"), "attaches");
}
