use pretty_assertions::assert_eq;
use serde_json::json;

use super::{BepSummary, parse_bep, uri_to_path};
use crate::fake::{AttemptSpec, FakeRuntime, attempt, bep_lines, case, suite, suite_xml, test_result_event};
use crate::result::{RunStatus, collect_results, totals_of};
use crate::runtime::Platform;

fn lines_of(text: &str) -> Vec<String> {
    text.split('\n').map(str::to_owned).collect()
}

/// Parses on a POSIX platform, which is what every case here is about except the Windows ones.
fn posix_bep(events: &[serde_json::Value]) -> BepSummary {
    parse_bep(lines_of(&bep_lines(events)), Platform::Darwin)
}

fn outputs(event: &mut serde_json::Value) -> &mut Vec<serde_json::Value> {
    event["testResult"]["testActionOutput"]
        .as_array_mut()
        .expect("the builder writes an output array")
}

#[test]
fn a_passing_test_result_and_its_artifact_paths() {
    let summary = posix_bep(&[
        test_result_event(&attempt("//p:t")),
        json!({"id": {"testSummary": {"label": "//p:t"}}, "testSummary": {"overallStatus": "PASSED", "totalRunCount": 1}}),
        json!({"id": {"buildFinished": {}}, "finished": {"overallSuccess": true}}),
    ]);
    assert_eq!(summary.attempts.len(), 1);
    assert_eq!(summary.attempts[0].status, "PASSED");
    assert_eq!(summary.attempts[0].xml_path.as_deref(), Some("/exec/testlogs/pkg/target/test.xml"));
    assert_eq!(summary.targets["//p:t"], "PASSED");
    assert_eq!(summary.overall_success, Some(true));
}

/// The false green this reader exists to prevent: `totalRunCount` counts bazel runs, so a 63-target lane would
/// report 63 tests and pass with every assertion in the repository unexecuted. Nothing may read it.
#[test]
fn the_test_summary_run_count_is_never_a_test_count() {
    let summary = posix_bep(&[json!({
        "id": {"testSummary": {"label": "//p:t"}},
        "testSummary": {"overallStatus": "PASSED", "totalRunCount": 63},
    })]);
    assert!(summary.attempts.is_empty(), "{:?}", summary.attempts);
    // The only thing kept from a summary is the per-target status; a count would have to come from test.xml.
    assert_eq!(
        summary.targets.into_iter().collect::<Vec<_>>(),
        [("//p:t".to_owned(), "PASSED".to_owned())]
    );
}

#[test]
fn a_locally_cached_result_is_marked() {
    let summary = posix_bep(&[test_result_event(&AttemptSpec {
        cached: true,
        ..attempt("//p:t")
    })]);
    assert!(summary.attempts[0].cached, "a cache hit was reported as a real run");
}

/// A remote cache hit is the same fact spelled elsewhere in the payload, and reading only one of the two makes the
/// cost line claim work that was never done.
#[test]
fn a_remotely_cached_result_is_marked_too() {
    let mut event = test_result_event(&attempt("//p:t"));
    event["testResult"]["executionInfo"] = json!({"cachedRemotely": true});
    let summary = posix_bep(&[event]);
    assert!(summary.attempts[0].cached, "a remote cache hit was reported as a real run");
}

#[test]
fn every_attempt_is_kept_so_flakiness_stays_visible() {
    let summary = posix_bep(&[
        test_result_event(&AttemptSpec {
            attempt: 1,
            status: "FAILED",
            ..attempt("//p:t")
        }),
        test_result_event(&AttemptSpec {
            attempt: 2,
            status: "FAILED",
            ..attempt("//p:t")
        }),
        test_result_event(&AttemptSpec {
            attempt: 3,
            status: "PASSED",
            ..attempt("//p:t")
        }),
    ]);
    let kept: Vec<i64> = summary.attempts.iter().map(|each| each.attempt).collect();
    assert_eq!(kept, [1, 2, 3]);
}

/// Verified against a real BEP: a failed JvmCompile emits exitCode/failureDetail and no `success` key at all, while
/// a passing target emits `"success": true`. Reading failure as `success == false` would report every broken build
/// as green.
#[test]
fn an_omitted_success_flag_is_failure_the_way_proto3_encodes_it() {
    let summary = posix_bep(&[
        json!({
            "id": {"actionCompleted": {"label": "//p:lib"}},
            "action": {
                "exitCode": 1,
                "type": "JvmCompile",
                "stderr": {"uri": "file:///exec/bazel-out/_tmp/actions/stderr-2"},
                "failureDetail": {"message": "worker spawn failed for JvmCompile"},
            },
        }),
        json!({
            "id": {"targetCompleted": {"label": "//p:t"}},
            "completed": {"failureDetail": {"message": "worker spawn failed"}},
        }),
    ]);
    assert_eq!(summary.failed_to_build, ["//p:t"]);
    assert_eq!(summary.action_stderr, ["/exec/bazel-out/_tmp/actions/stderr-2"]);
}

#[test]
fn a_successful_target_is_not_failed_to_build() {
    let summary = posix_bep(&[json!({
        "id": {"targetCompleted": {"label": "//p:t"}},
        "completed": {"success": true},
    })]);
    assert!(summary.failed_to_build.is_empty(), "{:?}", summary.failed_to_build);
}

#[test]
fn an_analysis_abort_is_recorded() {
    let summary = posix_bep(&[json!({
        "id": {"pattern": {}},
        "aborted": {"reason": "ANALYSIS_FAILURE"},
    })]);
    assert_eq!(summary.aborted, ["ANALYSIS_FAILURE"]);
}

/// A truncated final line is expected when bazel is killed mid-write, and it says nothing about the events that
/// were written whole. Failing on it would report infrastructure for a run that had results.
#[test]
fn a_truncated_final_line_is_skipped() {
    let good = bep_lines(&[test_result_event(&attempt("//p:t"))]);
    let summary = parse_bep(lines_of(&format!("{good}\n{{\"id\":{{\"testResu")), Platform::Darwin);
    assert_eq!(summary.attempts.len(), 1);
    assert!(summary.saw_any_event, "a file with one good line reported having seen nothing");
}

#[test]
fn an_empty_file_reports_having_seen_nothing() {
    let summary = parse_bep(lines_of(""), Platform::Darwin);
    assert!(!summary.saw_any_event);
    assert!(summary.attempts.is_empty());
}

/// proto3 JSON encodes an int64 as a quoted string, so a real bazel sends the duration quoted while the int32 ids
/// arrive bare. Reading only the bare form would zero every duration and make the cost line meaningless.
#[test]
fn a_quoted_int64_is_read_as_a_number() {
    let mut event = test_result_event(&attempt("//p:t"));
    event["testResult"]["testAttemptDurationMillis"] = json!("31000");
    let summary = posix_bep(&[event]);
    assert_eq!(summary.attempts[0].duration_ms, 31000);
}

/// Bazel emits the id alone as a "this event is coming" announcement; treating it as a result would invent an
/// attempt with no status.
#[test]
fn an_id_without_its_payload_is_not_an_attempt() {
    let summary = posix_bep(&[json!({"id": {"testResult": {"label": "//p:t"}}})]);
    assert!(summary.attempts.is_empty(), "{:?}", summary.attempts);
    assert!(summary.saw_any_event, "an announced event did not count as an event");
}

#[test]
fn a_non_file_uri_is_left_alone() {
    let mut event = test_result_event(&attempt("//p:t"));
    *outputs(&mut event) = vec![json!({"name": "test.xml", "uri": "bytestream://remote/blobs/abc"})];
    let summary = posix_bep(&[event]);
    assert_eq!(summary.attempts[0].xml_path.as_deref(), Some("bytestream://remote/blobs/abc"));
    // A missing artifact is absent rather than empty: result collection branches on which of the two it is.
    assert_eq!(summary.attempts[0].log_path, None);
}

#[test]
fn a_percent_encoded_uri_is_decoded() {
    let mut event = test_result_event(&attempt("//p:t"));
    outputs(&mut event)[1]["uri"] = json!("file:///exec/test%20logs/test.xml");
    let summary = posix_bep(&[event]);
    assert_eq!(summary.attempts[0].xml_path.as_deref(), Some("/exec/test logs/test.xml"));
}

#[test]
fn a_windows_drive_uri_is_decoded() {
    assert_eq!(
        uri_to_path("file:///C:/exec/test%20logs/test.xml", Platform::Windows),
        r"C:\exec\test logs\test.xml"
    );
}

/// A UNC share and the `localhost` spelling of a local one, which bazel emits for an output on a mapped drive.
#[test]
fn a_windows_uri_with_a_host_is_decoded() {
    assert_eq!(
        uri_to_path("file://server/share/test.xml", Platform::Windows),
        r"\\server\share\test.xml"
    );
    assert_eq!(
        uri_to_path("file://localhost/C:/exec/test.xml", Platform::Windows),
        r"C:\exec\test.xml"
    );
}

/// The Windows dialect end to end. Bazel reports a `file:///C:/...` URI, the report is read from the backslash path
/// it resolves to, and the digest still names the log inside the checkout.
#[test]
fn a_windows_bep_path_loads_the_report_and_keeps_the_log_in_the_checkout() {
    const TESTLOGS: &str = "C:/exec/bazel-out/x64_windows-fastbuild/testlogs/pkg/target";
    let mut event = test_result_event(&attempt("//p:t"));
    outputs(&mut event)[0]["uri"] = json!(format!("file:///{TESTLOGS}/test.log"));
    outputs(&mut event)[1]["uri"] = json!(format!("file:///{TESTLOGS}/test.xml"));
    let summary = parse_bep(lines_of(&bep_lines(&[event])), Platform::Windows);
    assert_eq!(
        summary.attempts[0].xml_path.as_deref(),
        Some(r"C:\exec\bazel-out\x64_windows-fastbuild\testlogs\pkg\target\test.xml")
    );

    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        &format!("{TESTLOGS}/test.xml"),
        &suite_xml(&suite(
            "suite",
            vec![crate::fake::CaseSpec {
                class_name: "com.intellij.air.PassTest".to_owned(),
                ..case("passes")
            }],
        )),
    );
    let result = collect_results(&fake, &summary, 1000, 0, "");
    assert_eq!(result.status, RunStatus::Pass, "{result:?}");
    assert_eq!(totals_of(&result.targets).tests, 1);
    // The reason the testlogs path is relativized: an absolute path under bazel's output base is one an agent
    // cannot open, and a backslash path used to walk straight past its marker.
    assert_eq!(
        result.targets[0].log_path.as_deref(),
        Some("out/bazel-testlogs/pkg/target/test.log")
    );
}
