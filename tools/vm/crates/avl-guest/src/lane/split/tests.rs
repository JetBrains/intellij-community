use std::collections::BTreeMap;
use std::fs;

use pretty_assertions::assert_eq;
use tempfile::TempDir;

use avl_wire::daemon::LaneClass;

use super::*;

fn durations(rows: &[(&str, u64)]) -> SeasonDurations {
    SeasonDurations {
        season: "AirPluginUiTests".to_owned(),
        by_class: rows
            .iter()
            .map(|(class, milliseconds)| ((*class).to_owned(), *milliseconds))
            .collect(),
    }
}

fn classes(names: &[&str]) -> Vec<LaneClass> {
    names
        .iter()
        .map(|name| LaneClass {
            suite: name.to_lowercase(),
            test_class_name: (*name).to_owned(),
        })
        .collect()
}

fn names(classes: &[LaneClass]) -> Vec<&str> {
    classes.iter().map(|class| class.test_class_name.as_str()).collect()
}

#[test]
fn a_season_file_keys_each_duration_by_the_simple_class_name_and_skips_a_malformed_row() {
    let text = "com.intellij.air.integration.flow.AirGenUiHostUiTest,295990\n\
                \n\
                com.intellij.air.integration.acp.AcpLocalAgentsUiTest, 4180 \n\
                com.intellij.air.other.AcpLocalAgentsUiTest,9000\n\
                com.intellij.air.integration.flow.NoDuration,\n\
                no comma at all\n\
                com.intellij.air.integration.flow.Negative,-5\n";

    let (durations, skipped) = SeasonDurations::parse("AirPluginUiTests", text);

    assert_eq!(
        durations.by_class,
        BTreeMap::from([
            ("AcpLocalAgentsUiTest".to_owned(), 9000),
            ("AirGenUiHostUiTest".to_owned(), 295_990)
        ]),
        "two rows of one simple name keep the larger duration"
    );
    assert_eq!(skipped, 3);
}

#[test]
fn reading_a_season_names_the_file_and_a_missing_or_empty_file_is_a_note_and_no_durations() {
    let directory = TempDir::new().unwrap();
    fs::write(directory.path().join("AirPluginUiTests.csv"), "a.b.First,1000\nbroken\n").unwrap();
    fs::write(directory.path().join("Empty.csv"), "\n").unwrap();

    let (read, note) = SeasonDurations::read(directory.path(), "AirPluginUiTests");
    assert_eq!(read, Some(durations(&[("First", 1000)])));
    let file = directory.path().join("AirPluginUiTests.csv");
    assert_eq!(
        note,
        format!(
            "lane-run splits the lane by the durations of 1 suite(s) of the season AirPluginUiTests from {}, and it skips 1 \
             row(s) that are no <test class>,<milliseconds>",
            file.display()
        )
    );

    let (missing, note) = SeasonDurations::read(directory.path(), "AirPluginMissing");
    assert_eq!(missing, None);
    assert!(
        note.starts_with("lane-run splits the lane by position, because it cannot read the durations of the season AirPluginMissing from "),
        "{note}"
    );

    let (empty, note) = SeasonDurations::read(directory.path(), "Empty");
    assert_eq!(empty, None);
    assert_eq!(
        note,
        format!(
            "lane-run splits the lane by position, because {} holds no duration of the season Empty",
            directory.path().join("Empty.csv").display()
        )
    );
}

#[test]
fn the_greedy_split_gives_the_longest_suite_first_to_the_runner_with_the_least_total() {
    let season = durations(&[("A", 600), ("B", 500), ("C", 400), ("D", 300), ("E", 200), ("Stale", 9_999)]);

    let plan = season.plan(&["E", "D", "C", "B", "A"], 2).unwrap();

    // A to 0, B to 1, C to 1 (500 < 600), D to 0 (600 < 900), and E to 0 (900 = 900, so the lower index).
    assert_eq!(
        plan.runner_of,
        BTreeMap::from([
            ("A".to_owned(), 0),
            ("B".to_owned(), 1),
            ("C".to_owned(), 1),
            ("D".to_owned(), 0),
            ("E".to_owned(), 0),
        ])
    );
    assert_eq!(plan.totals, [1100, 900]);
    assert_eq!((plan.defaulted, plan.median), (0, 400));
}

#[test]
fn a_suite_without_a_duration_takes_the_median_and_equal_durations_go_by_class_name() {
    let season = durations(&[("Long", 900), ("Mid", 300), ("Short", 100)]);

    let plan = season.plan(&["New", "Short", "Mid", "Long", "Other"], 2).unwrap();

    assert_eq!((plan.defaulted, plan.median), (2, 300));
    // Long 900 to 0; Mid, New and Other take 300 each, by name: Mid to 1, New to 1, Other to 1; Short to 0.
    assert_eq!(
        plan.runner_of,
        BTreeMap::from([
            ("Long".to_owned(), 0),
            ("Mid".to_owned(), 1),
            ("New".to_owned(), 1),
            ("Other".to_owned(), 1),
            ("Short".to_owned(), 0),
        ])
    );
    assert_eq!(plan.totals, [1000, 900]);
    assert_eq!(
        season.plan(&["New", "Other"], 2),
        None,
        "a season that names no selected suite gives no plan"
    );
}

#[test]
fn the_runners_of_a_duration_split_take_each_selected_suite_once_and_compute_the_same_split() {
    let season = durations(&[("A", 600_000), ("B", 500_000), ("C", 400_000), ("D", 300_000), ("E", 200_000)]);
    let selected = ["A", "B", "C", "D", "E", "Unnamed"];
    for count in 1..=4 {
        let mut runs = Vec::new();
        for index in 0..count {
            let split = RunnerSplit {
                index,
                count,
                durations: Some(season.clone()),
            };
            let share = split.share("ui", Vec::new(), classes(&selected));
            runs.extend(share.undocumented.into_iter().map(|class| class.test_class_name));
        }
        runs.sort();
        assert_eq!(runs, selected, "the {count} runner(s) run each suite once");
    }

    let first = RunnerSplit {
        index: 0,
        count: 2,
        durations: Some(season),
    }
    .share("ui", Vec::new(), classes(&selected));
    assert_eq!(names(&first.undocumented), ["A", "E", "Unnamed"], "a runner keeps the lane order");
    assert_eq!(
        first.note.as_deref(),
        Some(
            "lane ui: the runner with index 0 of 2 runners takes 3 of the 6 selected suite(s) by the duration split of the \
             season AirPluginUiTests: an expected 20.0 min of 40.0 min, and the runners expect 20.0 min, 20.0 min; 1 suite(s) \
             that the season does not name take the median 6.7 min"
        )
    );
}

#[test]
fn the_position_split_runs_without_durations_and_when_the_season_names_no_selected_suite() {
    let selected = classes(&["A", "B", "C"]);
    let position = RunnerSplit {
        index: 1,
        count: 2,
        durations: None,
    }
    .share("ui", Vec::new(), selected.clone());
    assert_eq!(names(&position.undocumented), ["B"]);
    assert_eq!(
        position.note.as_deref(),
        Some(
            "lane ui: the runner with index 1 of 2 runners takes 1 of the 3 selected suite(s) by the position split: each one \
             whose position in the lane order is 1 modulo 2"
        )
    );

    let unnamed = RunnerSplit {
        index: 0,
        count: 2,
        durations: Some(durations(&[("Gone", 1000)])),
    }
    .share("ui", Vec::new(), selected.clone());
    assert_eq!(names(&unnamed.undocumented), ["A", "C"]);
    assert_eq!(
        unnamed.note.as_deref(),
        Some(
            "lane ui: the runner with index 0 of 2 runners takes 2 of the 3 selected suite(s) by the position split, because \
             the season AirPluginUiTests names no selected suite: each one whose position in the lane order is 0 modulo 2"
        )
    );

    let whole = RunnerSplit::WHOLE.share("ui", Vec::new(), selected);
    assert_eq!(names(&whole.undocumented), ["A", "B", "C"]);
    assert_eq!(whole.note, None, "the whole lane has no note");
}
