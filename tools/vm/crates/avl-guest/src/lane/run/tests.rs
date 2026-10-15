use std::collections::BTreeMap;
use std::path::PathBuf;

use std::ffi::OsString;

use pretty_assertions::assert_eq;

use super::*;
use crate::cli::{Verb, parse};

/// The arguments of `lane-run --lane ui --descriptor /r/d.json` and `extra`.
fn args(extra: &[&str]) -> LaneRunArgs {
    let argv: Vec<OsString> = ["vm-guest-agent", "lane-run", "--lane", "ui", "--descriptor", "/r/d.json"]
        .into_iter()
        .chain(extra.iter().copied())
        .map(OsString::from)
        .collect();
    match parse(&argv).unwrap().verb {
        Verb::LaneRun(args) => args,
        other => panic!("lane-run parsed as {other:?}"),
    }
}

fn environment(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
    let pairs: BTreeMap<String, String> = pairs
        .iter()
        .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
        .collect();
    move |name| pairs.get(name).cloned()
}

// The run directory is --runtime-root, then $AIR_LANE_RUNTIME_ROOT, then $TEST_TMPDIR/air-lane. A run with none of them
// is refused, and so is a relative one.
#[test]
fn the_run_directory_comes_from_the_flag_the_variable_or_the_test_directory() {
    let none = environment(&[]);
    assert_eq!(
        runtime_root(
            &args(&["--runtime-root", "/flag"]),
            &environment(&[("AIR_LANE_RUNTIME_ROOT", "/variable")])
        )
        .unwrap(),
        PathBuf::from("/flag")
    );
    assert_eq!(
        runtime_root(
            &args(&[]),
            &environment(&[("AIR_LANE_RUNTIME_ROOT", "/variable"), ("TEST_TMPDIR", "/tmp/t")])
        )
        .unwrap(),
        PathBuf::from("/variable")
    );
    assert_eq!(
        runtime_root(&args(&[]), &environment(&[("TEST_TMPDIR", "/tmp/t")])).unwrap(),
        PathBuf::from("/tmp/t/air-lane")
    );
    assert_eq!(runtime_root(&args(&[]), &none).unwrap_err().code, "lane_run_root_missing");
    assert_eq!(
        runtime_root(&args(&[]), &environment(&[("AIR_LANE_RUNTIME_ROOT", "relative")]))
            .unwrap_err()
            .code,
        "lane_run_root_missing"
    );
}

// A Bazel --test_filter adds its selectors after the flags, and $JB_TEST_JUNIT5_FILTERS adds its filters after the flags.
#[test]
fn the_selectors_and_the_filters_come_from_the_flags_and_the_environment() {
    let parsed = args(&[
        "--selector",
        "AirRenameSessionGeneratedFlowUiTest",
        "--junit5-filter",
        "exclude-classname=.*Slow",
    ]);
    let variable = environment(&[
        ("TESTBRIDGE_TEST_ONLY", "a.AirComposerGeneratedFlowUiTest#one, AirGenUiHostUiTest"),
        ("JB_TEST_JUNIT5_FILTERS", "include-package=com.intellij.air;"),
    ]);
    assert_eq!(
        selectors(&parsed, &variable),
        [
            "AirRenameSessionGeneratedFlowUiTest",
            "a.AirComposerGeneratedFlowUiTest#one",
            "AirGenUiHostUiTest"
        ]
    );
    assert_eq!(
        junit5_filters(&parsed, &variable),
        ["exclude-classname=.*Slow", "include-package=com.intellij.air"]
    );
    assert!(selectors(&args(&[]), &environment(&[])).is_empty());
}

fn summary(has_failures: bool) -> String {
    format!(
        r#"{{"event":"summary","iterationId":"i","daemonBootStamp":"b","startedAt":"s","testsStarted":1,"testsFailed":0,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":{has_failures},"ideRunning":false}}"#
    )
}

// A run is green only with a summary of no failure and no failed run. A stream that was cut off has no summary, and a
// record that does not decode is no green record.
#[test]
fn only_a_summary_without_failures_is_green() {
    let note = r#"{"event":"output","text":"lane ui: ide launch"}"#;
    assert!(is_green(format!("{note}\n{}\n", summary(false)).as_bytes()));
    assert!(!is_green(format!("{note}\n{}\n", summary(true)).as_bytes()));
    assert!(!is_green(format!("{note}\n").as_bytes()));
    assert!(!is_green(format!("{note}\nnot a record\n{}\n", summary(false)).as_bytes()));
}

// The JUnit XML goes into the run directory, and to the file of a Bazel test: $JB_XML_OUTPUT_FILE before
// $XML_OUTPUT_FILE.
#[test]
fn the_junit_xml_goes_to_the_run_directory_and_to_the_file_of_the_bazel_test() {
    let root = tempfile::TempDir::new().unwrap();
    let bazel = root.path().join("bazel/test.xml");
    let named = root.path().join("named/report.xml");
    let xml = "<testsuites/>\n";
    write_xml(root.path(), xml, &environment(&[("XML_OUTPUT_FILE", bazel.to_str().unwrap())])).unwrap();
    assert_eq!(fs::read_to_string(root.path().join("test.xml")).unwrap(), xml);
    assert_eq!(fs::read_to_string(&bazel).unwrap(), xml);
    write_xml(
        root.path(),
        xml,
        &environment(&[
            ("XML_OUTPUT_FILE", bazel.to_str().unwrap()),
            ("JB_XML_OUTPUT_FILE", named.to_str().unwrap()),
        ]),
    )
    .unwrap();
    assert_eq!(fs::read_to_string(&named).unwrap(), xml);
}

// A lane IDE takes the fixed bridge port, so a lane test runs as one shard. A run of more shards is refused before it
// prepares anything, and one shard is no split.
#[test]
fn a_run_of_more_than_one_shard_is_refused() {
    let refused = run(&args(&[]), &environment(&[("TEST_TOTAL_SHARDS", "6"), ("TEST_SHARD_INDEX", "2")])).unwrap_err();
    assert_eq!(refused.code, SHARDED_CODE);
    assert!(refused.message.contains("6 shards"), "{}", refused.message);
    refuse_shards(&environment(&[("TEST_TOTAL_SHARDS", "1")])).unwrap();
    refuse_shards(&environment(&[])).unwrap();
}
