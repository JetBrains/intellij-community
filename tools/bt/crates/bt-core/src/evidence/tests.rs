use pretty_assertions::assert_eq;

use super::*;
use crate::fake::FakeRuntime;

const TESTLOGS: &str = "/exec/bazel-out/darwin_arm64-fastbuild/testlogs";

fn attempt(label: &str, shard: i64, status: &str, dir: Option<&str>) -> BepAttempt {
    BepAttempt {
        label: label.to_owned(),
        run: 1,
        shard,
        attempt: 1,
        status: status.to_owned(),
        cached: false,
        xml_path: dir.map(|dir| format!("{TESTLOGS}/{dir}/test.xml")),
        log_path: dir.map(|dir| format!("{TESTLOGS}/{dir}/test.log")),
        duration_ms: 1000,
    }
}

fn summary(attempts: Vec<BepAttempt>) -> BepSummary {
    BepSummary {
        attempts,
        saw_any_event: true,
        ..BepSummary::default()
    }
}

/// The files of the fake under the run directories, repo-relative.
fn kept(fake: &FakeRuntime) -> Vec<String> {
    fake.state()
        .files
        .keys()
        .filter_map(|path| {
            path.strip_prefix("/repo/")
                .filter(|path| path.starts_with(RUNS_DIR))
                .map(str::to_owned)
        })
        .collect()
}

#[test]
fn a_failed_shard_keeps_its_log_its_report_and_its_outputs() {
    let fake = FakeRuntime::new([]);
    let shard = "pkg/target_test/shard_2_of_2";
    for (name, text) in [
        ("test.log", "the log"),
        ("test.xml", "<testsuites/>"),
        ("test.outputs/logs/idea.log", "the IDE log"),
        ("test.outputs/logs/threadDump-timeoutRunBlocking-1.log", "a dump"),
    ] {
        fake.put_absolute(&format!("{TESTLOGS}/{shard}/{name}"), text);
    }
    fake.put_absolute(&format!("{TESTLOGS}/pkg/target_test/shard_1_of_2/test.log"), "a green shard");
    let bep = summary(vec![
        attempt("//pkg:target_test", 1, "PASSED", Some("pkg/target_test/shard_1_of_2")),
        attempt("//pkg:target_test", 2, "FAILED", Some(shard)),
    ]);

    let run = keep_failed_evidence(&fake, &bep).expect("a failed shard keeps evidence");

    assert_eq!(run, "out/bt-runs/0000000001000");
    assert_eq!(
        kept(&fake),
        [
            format!("{run}/{shard}/test.log"),
            format!("{run}/{shard}/test.outputs/logs/idea.log"),
            format!("{run}/{shard}/test.outputs/logs/threadDump-timeoutRunBlocking-1.log"),
            format!("{run}/{shard}/test.xml"),
        ]
    );
    assert_eq!(
        fake.state().files[&format!("/repo/{run}/{shard}/test.outputs/logs/idea.log")],
        "the IDE log"
    );
}

#[test]
fn a_green_run_keeps_nothing() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(&format!("{TESTLOGS}/pkg/target_test/test.log"), "green");
    let bep = summary(vec![attempt("//pkg:target_test", 1, "PASSED", Some("pkg/target_test"))]);

    assert_eq!(keep_failed_evidence(&fake, &bep), None);
    assert!(kept(&fake).is_empty());
}

/// A target that bazel killed before it wrote a log has nothing to keep, and an empty run directory would push a
/// useful one out of the retention.
#[test]
fn a_failure_without_files_keeps_no_run_directory() {
    let fake = FakeRuntime::new([]);
    let bep = summary(vec![
        attempt("//pkg:target_test", 1, "TIMEOUT", Some("pkg/target_test")),
        attempt("//pkg:other_test", 1, "FAILED", None),
    ]);

    assert_eq!(keep_failed_evidence(&fake, &bep), None);
    assert!(kept(&fake).is_empty());
}

#[test]
fn a_path_outside_testlogs_is_kept_under_the_label_and_the_shard() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/elsewhere/test.log", "the log");
    let mut outside = attempt("@community//tools/bt:bt_test", 3, "FAILED", None);
    outside.log_path = Some("/elsewhere/test.log".to_owned());

    let run = keep_failed_evidence(&fake, &summary(vec![outside])).expect("the log is kept");

    assert_eq!(
        kept(&fake),
        [format!("{run}/community/tools/bt/bt_test/run_1_shard_3_attempt_1/test.log")]
    );
}

#[test]
fn two_runs_in_one_millisecond_get_two_directories() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(&format!("{TESTLOGS}/pkg/target_test/test.log"), "red");
    let bep = summary(vec![attempt("//pkg:target_test", 1, "FAILED", Some("pkg/target_test"))]);

    let first = keep_failed_evidence(&fake, &bep);
    let second = keep_failed_evidence(&fake, &bep);

    assert_eq!(first.as_deref(), Some("out/bt-runs/0000000001000"));
    assert_eq!(second.as_deref(), Some("out/bt-runs/0000000001000-2"));
}

#[test]
fn only_the_last_runs_stay_and_a_foreign_directory_is_kept() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(&format!("{TESTLOGS}/pkg/target_test/test.log"), "red");
    fake.put(&format!("{RUNS_DIR}/notes/readme.txt"), "not a run");
    let bep = summary(vec![attempt("//pkg:target_test", 1, "FAILED", Some("pkg/target_test"))]);

    let mut runs = Vec::new();
    for _ in 0..KEPT_RUNS + 2 {
        fake.state().clock += 10;
        runs.push(keep_failed_evidence(&fake, &bep).expect("each red run keeps evidence"));
    }

    let mut left: Vec<String> = kept(&fake)
        .iter()
        .filter_map(|path| {
            path.strip_prefix(&format!("{RUNS_DIR}/"))
                .and_then(|rest| rest.split('/').next())
                .map(str::to_owned)
        })
        .collect();
    left.dedup();
    let mut want: Vec<String> = runs[2..]
        .iter()
        .map(|run| run.trim_start_matches(&format!("{RUNS_DIR}/")).to_owned())
        .collect();
    want.push("notes".to_owned());
    assert_eq!(left, want);
    assert_eq!(
        fake.state().removed,
        runs[..2].iter().map(|run| format!("/repo/{run}")).collect::<Vec<_>>()
    );
}
