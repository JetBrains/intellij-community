use pretty_assertions::assert_eq;

use super::*;
use crate::exit;
use crate::fake::{AREA_DIR, FakeRuntime, LANES_FILE, area_files, refusal};

fn table(lanes: &str) -> Lanes {
    Lanes::parse(&format!(r#"{{ "lanes": [{lanes}] }}"#)).expect("the table parses")
}

fn fake_with(files: &[(&str, &str)]) -> FakeRuntime {
    FakeRuntime::new(files.iter().copied())
}

/// `bt.json` names each area's directory and lane table, and the table is read at load time.
#[test]
fn the_areas_are_read_from_bt_json() {
    let files = area_files();
    let fake = FakeRuntime::new(files.iter().map(|(path, text)| (path.as_str(), text.as_str())));
    let areas = Areas::load(&fake).expect("the areas load");
    assert_eq!(areas.dirs(), [AREA_DIR]);
    let area = areas.iter().next().expect("one area");
    assert_eq!(area.lanes_file(), LANES_FILE);
    assert!(area.lanes().get("fast").is_some());
}

/// A checkout without the file has no area, which is the answer a community checkout gets.
#[test]
fn a_checkout_without_bt_json_has_no_area() {
    let areas = Areas::load(&FakeRuntime::new([])).expect("no file is no area");
    assert!(areas.is_empty());
    assert!(areas.lane("fast").expect("no refusal").is_none());
}

/// A file that is there and wrong is refused, and so is a lane table it names that is not there.
#[test]
fn a_malformed_bt_json_or_a_missing_lane_table_is_refused() {
    for (text, fragment) in [
        ("{", "bt.json is malformed"),
        (r#"{ "areas": [ { "dir": "a" } ] }"#, "bt.json is malformed"),
        (r#"{ "areas": [], "extra": 1 }"#, "bt.json is malformed"),
        (r#"{ "areas": [ { "dir": "/abs", "lanes": "x.json" } ] }"#, "is repo-relative"),
        (
            r#"{ "areas": [ { "dir": "a", "lanes": "a/lanes.json" } ] }"#,
            "the lane table a/lanes.json is unreadable",
        ),
    ] {
        let failure = refusal(Areas::load(&fake_with(&[(AREAS_FILE, text)])));
        assert_eq!(failure.exit, exit::INFRA, "{text}");
        assert!(failure.message.contains(fragment), "{text}: {}", failure.message);
    }
}

/// A path belongs to the area whose directory holds it, and a nested area wins over the one around it.
#[test]
fn by_dir_answers_the_innermost_owning_area() {
    let areas = Areas::new(vec![
        Area::new("plugins", "plugins/lanes.json", table("")),
        Area::new("plugins/air", "plugins/air/lanes.json", table("")),
    ]);
    assert_eq!(areas.by_dir("plugins/air/shared").map(Area::dir), Some("plugins/air"));
    assert_eq!(areas.by_dir("plugins/air").map(Area::dir), Some("plugins/air"));
    assert_eq!(areas.by_dir("plugins/airx").map(Area::dir), Some("plugins"));
    assert_eq!(areas.by_dir("community/x").map(Area::dir), None);
}

/// Two areas that declare one lane name are refused: which one runs would depend on the order of `bt.json`.
#[test]
fn a_lane_two_areas_declare_is_refused() {
    let lane = r#"{ "name": "fast", "target": "//a/...", "extra": [] }"#;
    let areas = Areas::new(vec![
        Area::new("a", "a/lanes.json", table(lane)),
        Area::new("b", "b/lanes.json", table(lane)),
    ]);
    let failure = refusal(areas.lane("fast"));
    assert_eq!((failure.code.as_ref(), failure.exit), ("bt_infra", exit::INFRA));
    assert!(failure.message.contains("declared by the areas a, b"), "{}", failure.message);
    assert_eq!(areas.lane_names(), ["fast", "fast"]);

    let single = Areas::new(vec![Area::new("a", "a/lanes.json", table(lane))]);
    let (area, spec) = single.lane("fast").expect("one area").expect("the lane");
    assert_eq!((area.dir(), spec.name.as_str()), ("a", "fast"));
    assert!(single.lane("slow").expect("no refusal").is_none());
}

/// A flow or a suite selector needs the one area with a suite catalog; with none it names nothing.
#[test]
fn a_flow_selector_without_a_catalog_is_refused() {
    let areas = Areas::new(vec![Area::new("a", "a/lanes.json", table(""))]);
    let failure = refusal(areas.with_catalog());
    assert_eq!(failure.exit, exit::USAGE);
    assert!(failure.message.contains("names a suite catalog"), "{}", failure.message);
    let area = areas.iter().next().expect("one area");
    assert!(
        refusal(area.catalog()).message.contains("names no suite catalog"),
        "an area without a catalog refuses the catalog readers"
    );
}

/// A catalog is all three paths or none: a table with one of them would answer from a directory nobody writes.
#[test]
fn a_partial_catalog_is_malformed() {
    let reason = Lanes::parse(r#"{ "flowProfileDir": "p", "lanes": [] }"#).expect_err("refused");
    assert!(reason.contains("together"), "{reason}");
}

/// `target` holds one target or several; a lane of several runs as a multi-target run.
#[test]
fn a_lane_target_is_a_string_or_a_list() {
    let lanes = table(
        r#"{ "name": "one", "target": "//a:t", "extra": [] },
           { "name": "many", "target": ["//a/...", "@community//tools/bt/..."], "extra": [] },
           { "name": "labels", "target": ["//a:t", "//b:t"], "extra": [] }"#,
    );
    let one = lanes.get("one").expect("one");
    assert_eq!(one.targets, ["//a:t"]);
    assert!(!one.is_multi_target());
    let many = lanes.get("many").expect("many");
    assert_eq!(many.targets, ["//a/...", "@community//tools/bt/..."]);
    assert!(many.is_multi_target());
    assert!(lanes.get("labels").expect("labels").is_multi_target());

    let empty = Lanes::parse(r#"{ "lanes": [ { "name": "x", "target": [], "extra": [] } ] }"#).expect_err("an empty list names no target");
    assert!(empty.contains("at least one target"), "{empty}");
}
