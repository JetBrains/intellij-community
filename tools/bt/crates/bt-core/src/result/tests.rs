use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::bep::parse_bep;
use crate::fake::{
    AttemptSpec, BUCKETING_XML, FakeRuntime, attempt, bep_lines, case, failing, suite, suite_xml, test_result_event, wrapper_xml,
};

#[test]
fn relativized_paths_keep_first_markers_and_unmatched_values() {
    for (path, want) in [
        (r"C:\cache\testlogs\module\test.log", "out/bazel-testlogs/module/test.log"),
        (
            "/cache/testlogs/outer/testlogs/test.log",
            "out/bazel-testlogs/outer/testlogs/test.log",
        ),
        ("/cache/testlogs/", "out/bazel-testlogs/"),
    ] {
        assert_eq!(relativize_testlogs(path), want, "{path}");
    }
    // An unmatched path is answered verbatim, its own separators included.
    for path in ["", "/cache/testlogs", "testlogs/test.log", r"C:\cache\other\test.log"] {
        assert_eq!(relativize_testlogs(path), path);
    }
    for (path, want) in [
        ("/cache/execroot/_main/src/file.kt", "src/file.kt"),
        ("/cache/execroot/_main/other/execroot/_main/file.kt", "other/execroot/_main/file.kt"),
        ("/execroot/_main/", ""),
        ("/execroot/_main", "/execroot/_main"),
        (r"C:\execroot\_main\src\file.kt", r"C:\execroot\_main\src\file.kt"),
        ("", ""),
    ] {
        assert_eq!(relativize_source_path(path), want, "{path}");
    }
}

fn empty_bep() -> BepSummary {
    BepSummary {
        saw_any_event: true,
        ..BepSummary::default()
    }
}

fn classify_target(apply: impl FnOnce(&mut TargetResult)) -> TargetResult {
    let mut target = TargetResult {
        label: "//p:t".to_owned(),
        tests: 10,
        shards: 1,
        ..TargetResult::default()
    };
    apply(&mut target);
    target
}

fn plain_target() -> TargetResult {
    classify_target(|_| {})
}

fn classified(bazel_exit: i32, bep: &BepSummary, targets: &[TargetResult], build_errors: &[String], degraded: bool) -> (RunStatus, u8) {
    classify_run(ClassifyInput {
        bazel_exit,
        bep,
        targets,
        build_errors,
        degraded,
    })
}

/// The decision table that produces the exit-code contract.
#[test]
fn classify_run_maps_every_outcome_to_its_status_and_exit() {
    let failed_to_build = |label: &str| BepSummary {
        failed_to_build: vec![label.to_owned()],
        saw_any_event: true,
        ..BepSummary::default()
    };
    let analysis_failure = BepSummary {
        aborted: vec!["ANALYSIS_FAILURE".to_owned()],
        saw_any_event: true,
        ..BepSummary::default()
    };
    type Verdict = (RunStatus, u8);
    let cases: Vec<(&str, Verdict, Verdict)> = vec![
        (
            "green",
            classified(0, &empty_bep(), &[plain_target()], &[], false),
            (RunStatus::Pass, exit::GREEN),
        ),
        (
            // A retry that passed is a pass: the run's verdict is what the last attempt says.
            "a flaky but passed run",
            classified(0, &empty_bep(), &[classify_target(|target| target.flaky = 1)], &[], false),
            (RunStatus::Pass, exit::GREEN),
        ),
        (
            "failed tests",
            classified(3, &empty_bep(), &[classify_target(|target| target.failed = 2)], &[], false),
            (RunStatus::Fail, exit::TEST_FAILED),
        ),
        (
            // The runner exits 42 for "no test class matched", and bazel reports that as an ordinary test
            // failure. Reading it as one is a red run nobody can reproduce.
            "a filter that matched nothing",
            classified(
                3,
                &empty_bep(),
                &[classify_target(|target| {
                    target.tests = 0;
                    target.wrapper_exit_code = Some(42);
                })],
                &[],
                false,
            ),
            (RunStatus::NoTests, exit::NO_TESTS),
        ),
        (
            // A lane whose every test skipped for a missing *_BIN asserted nothing, and green would hide it.
            "an entirely skipped run",
            classified(
                0,
                &empty_bep(),
                &[classify_target(|target| {
                    target.tests = 0;
                    target.skipped = 6;
                })],
                &[],
                false,
            ),
            (RunStatus::NoTests, exit::NO_TESTS),
        ),
        (
            "a lane whose tag filter matched no target",
            classified(4, &empty_bep(), &[], &[], false),
            (RunStatus::NoTests, exit::NO_TESTS),
        ),
        (
            "a compile failure",
            classified(1, &failed_to_build("//p:t"), &[], &["a.kt:1:1: error: nope".to_owned()], false),
            (RunStatus::BuildFailed, exit::BUILD_FAILED),
        ),
        (
            "an analysis failure",
            classified(1, &analysis_failure, &[], &[], false),
            (RunStatus::BuildFailed, exit::BUILD_FAILED),
        ),
        (
            // A build failure has no test count to be zero, so it must be classified before the count is read.
            "a build failure with tests reported anyway",
            classified(1, &failed_to_build("//p:other"), &[plain_target()], &[], false),
            (RunStatus::BuildFailed, exit::BUILD_FAILED),
        ),
        (
            "unusable results with no target at all",
            classified(0, &BepSummary::default(), &[], &[], true),
            (RunStatus::Infra, exit::INFRA),
        ),
        (
            // Bazel exited nonzero for a reason nothing structural explains.
            "an unexplained nonzero bazel exit",
            classified(37, &empty_bep(), &[plain_target()], &[], false),
            (RunStatus::Infra, exit::INFRA),
        ),
    ];
    for (what, got, want) in cases {
        assert_eq!(got, want, "{what}");
    }
}

/// 8 is bazel's own command failure, 33 out of memory, 127 a wrapper that is not there. None of them says anything
/// about the tests.
#[test]
fn bazels_own_failure_exits_are_infrastructure() {
    for bazel_exit in [8, 33, 127] {
        assert_eq!(
            classified(bazel_exit, &empty_bep(), &[plain_target()], &[], false),
            (RunStatus::Infra, exit::INFRA),
            "bazel exit {bazel_exit}"
        );
    }
}

// --- result assembly ---------------------------------------------------------------------------------------------

fn bep_of(fake: &FakeRuntime, events: &[serde_json::Value]) -> BepSummary {
    fake.put_absolute("/tmp/bep.json", &bep_lines(events));
    parse_bep(fake.read_lines(Path::new("/tmp/bep.json")), fake.platform())
}

fn at(label: &'static str, xml: &'static str) -> AttemptSpec {
    AttemptSpec { xml, ..attempt(label) }
}

fn target_completed_failure(label: &str) -> serde_json::Value {
    json!({
        "id": {"targetCompleted": {"label": label}},
        "completed": {"failureDetail": {"message": "nope"}},
    })
}

#[test]
fn test_counts_are_summed_across_shards_and_reported_as_one_target() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/shard_1_of_2/test.xml",
        &suite_xml(&suite("FooTest", vec![case("a"), case("b")])),
    );
    fake.put_absolute(
        "/exec/testlogs/p/t/shard_2_of_2/test.xml",
        &suite_xml(&suite("BarTest", vec![case("c")])),
    );
    let bep = bep_of(
        &fake,
        &[
            test_result_event(&AttemptSpec {
                shard: 1,
                ..at("//p:t", "/exec/testlogs/p/t/shard_1_of_2/test.xml")
            }),
            test_result_event(&AttemptSpec {
                shard: 2,
                ..at("//p:t", "/exec/testlogs/p/t/shard_2_of_2/test.xml")
            }),
        ],
    );
    let result = collect_results(&fake, &bep, 5000, 0, "");
    assert_eq!(result.status, RunStatus::Pass);
    assert_eq!(result.targets.len(), 1);
    assert_eq!((result.targets[0].tests, result.targets[0].shards), (3, 2));
}

/// The stub a non-matching shard writes contributes nothing, but it must not zero the run either: that would turn
/// a green sharded run into NO_TESTS.
#[test]
fn a_bucketing_only_shard_contributes_nothing_without_zeroing_the_run() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/exec/testlogs/p/t/s1/test.xml", &suite_xml(&suite("FooTest", vec![case("a")])));
    fake.put_absolute("/exec/testlogs/p/t/s2/test.xml", BUCKETING_XML);
    let bep = bep_of(
        &fake,
        &[
            test_result_event(&AttemptSpec {
                shard: 1,
                ..at("//p:t", "/exec/testlogs/p/t/s1/test.xml")
            }),
            test_result_event(&AttemptSpec {
                shard: 2,
                ..at("//p:t", "/exec/testlogs/p/t/s2/test.xml")
            }),
        ],
    );
    let result = collect_results(&fake, &bep, 5000, 0, "");
    assert_eq!((result.targets[0].tests, result.status), (1, RunStatus::Pass));
}

#[test]
fn an_all_bucketing_run_is_no_tests() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/exec/testlogs/p/t/s1/test.xml", BUCKETING_XML);
    let bep = bep_of(&fake, &[test_result_event(&at("//p:t", "/exec/testlogs/p/t/s1/test.xml"))]);
    assert_eq!(collect_results(&fake, &bep, 5000, 0, "").status, RunStatus::NoTests);
}

#[test]
fn failures_are_collected_and_the_log_path_is_relativized() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/test.xml",
        &suite_xml(&crate::fake::SuiteSpec {
            // The body the digest must never read: 50 KB of captured platform log.
            system_out: "x".repeat(50_000),
            ..suite(
                "FooTest",
                vec![case("a"), failing("b", "E", "boom", "at com.intellij.air.F.m(F.kt:1)")],
            )
        }),
    );
    let bep = bep_of(
        &fake,
        &[test_result_event(&AttemptSpec {
            status: "FAILED",
            log: "/cache/execroot/_main/bazel-out/darwin_arm64-fastbuild/testlogs/plugins/air/x/t/test.log",
            ..at("//p:t", "/exec/testlogs/p/t/test.xml")
        })],
    );
    let result = collect_results(&fake, &bep, 5000, 3, "");
    assert_eq!((result.status, result.exit_code), (RunStatus::Fail, exit::TEST_FAILED));
    assert_eq!(
        result.failures.iter().map(|failure| failure.name.as_str()).collect::<Vec<_>>(),
        ["b"]
    );
    assert_eq!(
        result.targets[0].log_path.as_deref(),
        Some("out/bazel-testlogs/plugins/air/x/t/test.log")
    );
}

#[test]
fn the_failing_shard_log_wins_over_a_green_one() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/exec/testlogs/p/t/s1/test.xml", &suite_xml(&suite("FooTest", vec![case("a")])));
    fake.put_absolute(
        "/exec/testlogs/p/t/s2/test.xml",
        &suite_xml(&suite("BarTest", vec![failing("b", "E", "boom", "")])),
    );
    let bep = bep_of(
        &fake,
        &[
            test_result_event(&AttemptSpec {
                shard: 1,
                log: "/exec/testlogs/p/t/s1/test.log",
                ..at("//p:t", "/exec/testlogs/p/t/s1/test.xml")
            }),
            test_result_event(&AttemptSpec {
                shard: 2,
                status: "FAILED",
                log: "/exec/testlogs/p/t/s2/test.log",
                ..at("//p:t", "/exec/testlogs/p/t/s2/test.xml")
            }),
        ],
    );
    let result = collect_results(&fake, &bep, 5000, 3, "");
    assert_eq!(result.targets[0].log_path.as_deref(), Some("out/bazel-testlogs/p/t/s2/test.log"));
}

/// Shards run concurrently, so the target costs its slowest one, not their sum.
#[test]
fn a_targets_duration_is_its_slowest_shard() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/exec/testlogs/p/t/test.xml", &suite_xml(&suite("FooTest", vec![case("a")])));
    let shard = |shard: i64, duration_ms: u64| {
        test_result_event(&AttemptSpec {
            shard,
            duration_ms,
            ..at("//p:t", "/exec/testlogs/p/t/test.xml")
        })
    };
    let bep = bep_of(&fake, &[shard(1, 4_000), shard(2, 31_000), shard(3, 9_000)]);
    assert_eq!(collect_results(&fake, &bep, 40_000, 0, "").targets[0].duration_ms, 31_000);
}

/// Only the final attempt counts. Counting the failed first attempt too would report a red run that passed.
#[test]
fn only_the_final_attempt_counts_and_the_retry_is_reported_as_flaky() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/a1/test.xml",
        &suite_xml(&suite("FooTest", vec![failing("a", "E", "boom", "")])),
    );
    fake.put_absolute("/exec/testlogs/p/t/a2/test.xml", &suite_xml(&suite("FooTest", vec![case("a")])));
    let bep = bep_of(
        &fake,
        &[
            test_result_event(&AttemptSpec {
                attempt: 1,
                status: "FAILED",
                ..at("//p:t", "/exec/testlogs/p/t/a1/test.xml")
            }),
            test_result_event(&AttemptSpec {
                attempt: 2,
                status: "PASSED",
                ..at("//p:t", "/exec/testlogs/p/t/a2/test.xml")
            }),
        ],
    );
    let result = collect_results(&fake, &bep, 5000, 0, "");
    assert_eq!(result.status, RunStatus::Pass);
    assert!(result.failures.is_empty(), "{:?}", result.failures);
    assert_eq!(result.targets[0].flaky, 1);
    assert_eq!(
        result.flaky,
        [FlakyRun {
            id: "//p:t shard 1".to_owned(),
            attempts: 2
        }]
    );
}

/// A cache hit still has a test.xml, and reading it is what keeps a cached lane from reporting zero tests.
#[test]
fn a_cached_replay_still_reports_the_real_test_count() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/test.xml",
        &suite_xml(&suite("FooTest", vec![case("a"), case("b")])),
    );
    let bep = bep_of(
        &fake,
        &[test_result_event(&AttemptSpec {
            cached: true,
            ..at("//p:t", "/exec/testlogs/p/t/test.xml")
        })],
    );
    let result = collect_results(&fake, &bep, 300, 0, "");
    assert_eq!((result.targets[0].tests, result.targets[0].cached), (2, true));
}

/// The synthesized wrapper suite carries the runner's exit code and a whole log in `<system-out>`. Reporting it as
/// a test failure would print the runner's stack dump instead of naming the real cause.
#[test]
fn the_synthesized_wrapper_suite_becomes_no_tests() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/test.xml",
        &wrapper_xml(
            "plugins/air/shared/core/ai-agent-core-tests_test",
            42,
            &format!("No tests found\n{}", "noise ".repeat(5000)),
        ),
    );
    let bep = bep_of(
        &fake,
        &[test_result_event(&AttemptSpec {
            status: "FAILED",
            ..at("//p:t", "/exec/testlogs/p/t/test.xml")
        })],
    );
    let result = collect_results(&fake, &bep, 5000, 3, "");
    assert_eq!((result.status, result.exit_code), (RunStatus::NoTests, exit::NO_TESTS));
    assert!(result.failures.is_empty(), "{:?}", result.failures);
    assert_eq!(result.targets[0].wrapper_exit_code, Some(42));
}

/// A `#method` filter that names no method is a *discovery* failure, so the runner leaves by 2 rather than the 42
/// that means "the filter matched no test class". It is not 0 and it is not green: zero executed tests is NO_TESTS
/// for every wrapper code. `bt`'s digest tests pin which cause it names.
#[test]
fn a_discovery_failure_is_no_tests_rather_than_green() {
    for wrapper_code in [2, 1, 255] {
        let fake = FakeRuntime::new([]);
        fake.put_absolute(
            "/exec/testlogs/p/t/test.xml",
            &wrapper_xml(
                "plugins/air/shared/session/air-shared-session-tests_test",
                wrapper_code,
                "Could not find method with name [no such method]",
            ),
        );
        let bep = bep_of(
            &fake,
            &[test_result_event(&AttemptSpec {
                status: "FAILED",
                ..at("//p:t", "/exec/testlogs/p/t/test.xml")
            })],
        );
        // 3 is what bazel reports for a test that failed, which is how the runner's own code reaches it.
        let result = collect_results(&fake, &bep, 5000, 3, "");
        assert_eq!(
            (result.status, result.exit_code),
            (RunStatus::NoTests, exit::NO_TESTS),
            "wrapper code {wrapper_code}"
        );
        assert_eq!(result.targets[0].wrapper_exit_code, Some(wrapper_code));
    }
}

#[test]
fn compiler_diagnostics_are_read_out_of_the_captured_output() {
    let fake = FakeRuntime::new([]);
    let bep = bep_of(&fake, &[target_completed_failure("//p:t")]);
    let output = "Kotlinc Runner: Error: Unresolved reference 'nope'.\n\
                  \t/c/execroot/_main/plugins/air/x/testSrc/FooTest.kt:41:15\n";
    let result = collect_results(&fake, &bep, 5000, 1, output);
    assert_eq!((result.status, result.exit_code), (RunStatus::BuildFailed, exit::BUILD_FAILED));
    assert_eq!(
        result.build_errors,
        ["plugins/air/x/testSrc/FooTest.kt:41:15: error: Unresolved reference 'nope'."]
    );
}

/// The captured output is the reliable source, but when it has nothing the action stderr file is worth a look, on
/// the runs where bazel has not deleted it yet.
#[test]
fn the_action_stderr_file_is_the_fallback() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/err.txt",
        "plugins/air/x/testSrc/FooTest.kt:41:15: error: unresolved reference: nope\n",
    );
    let bep = bep_of(
        &fake,
        &[
            json!({
                "id": {"actionCompleted": {"label": "//p:t"}},
                "action": {"exitCode": 1, "stderr": {"uri": "file:///exec/err.txt"}},
            }),
            target_completed_failure("//p:t"),
        ],
    );
    let result = collect_results(&fake, &bep, 5000, 1, "");
    assert_eq!(
        result.build_errors,
        ["plugins/air/x/testSrc/FooTest.kt:41:15: error: unresolved reference: nope"]
    );
}

/// A failed attempt with no report means the counts are a guess, and a guess must never be reported as green.
#[test]
fn a_missing_test_xml_on_a_failed_attempt_degrades_rather_than_reporting_green() {
    let fake = FakeRuntime::new([]);
    let bep = bep_of(
        &fake,
        &[test_result_event(&AttemptSpec {
            status: "FAILED",
            ..at("//p:t", "/exec/gone/test.xml")
        })],
    );
    let result = collect_results(&fake, &bep, 5000, 3, "");
    assert!(result.degraded, "{result:?}");
    assert_ne!(result.exit_code, exit::GREEN);
}

/// A target that failed to build emitted no testResult at all, so it would otherwise vanish from the digest that is
/// supposed to explain why nothing ran.
#[test]
fn a_target_that_failed_to_build_still_appears() {
    let fake = FakeRuntime::new([]);
    let bep = bep_of(&fake, &[target_completed_failure("//p:broken")]);
    let result = collect_results(&fake, &bep, 5000, 1, "");
    assert_eq!(
        result.targets.iter().map(|target| target.label.as_str()).collect::<Vec<_>>(),
        ["//p:broken"]
    );
    // Zero shards is what keeps it out of the cost line, which reports what actually ran.
    assert_eq!(result.targets[0].shards, 0);
}

/// A runner that reported counts in the suite header without per-case failure elements still failed.
#[test]
fn the_suite_header_is_trusted_when_there_are_no_per_case_failures() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(
        "/exec/testlogs/p/t/test.xml",
        r#"<testsuites><testsuite name="FooTest" tests="2" failures="1" errors="1" skipped="0"><testcase name="a" classname="FooTest"/></testsuite></testsuites>"#,
    );
    let bep = bep_of(
        &fake,
        &[test_result_event(&AttemptSpec {
            status: "FAILED",
            ..at("//p:t", "/exec/testlogs/p/t/test.xml")
        })],
    );
    let result = collect_results(&fake, &bep, 5000, 3, "");
    assert_eq!((result.targets[0].failed, result.status), (2, RunStatus::Fail));
}

#[test]
fn parse_compile_errors_reads_the_worker_and_the_inline_forms() {
    // Verbatim shape from a real failing build, sandbox path and all.
    let worker = [
        "ERROR: /repo/plugins/air/shared/core/BUILD.bazel:84:12: compile //plugins/air/shared/core:air-shared-core-tests_test_lib (kt: 7, java: 0) failed: (Exit -1): java failed",
        "Kotlinc Runner: Error: Unresolved reference 'normalizeAirPathhh'.",
        "\t/Users/x/Library/Caches/JetBrains/MonorepoBazel/4c6/execroot/_main/plugins/air/shared/core/testSrc/AirPathUtilTest.kt:17:16",
        "Kotlinc Runner: Error: Unresolved reference 'isEqualTo'.",
        "\t/Users/x/Library/Caches/JetBrains/MonorepoBazel/4c6/execroot/_main/plugins/air/shared/core/testSrc/AirPathUtilTest.kt:17:48",
        "Use --verbose_failures to see the command lines of failed build steps.",
    ]
    .join("\n");
    assert_eq!(
        parse_compile_errors(&worker),
        [
            "plugins/air/shared/core/testSrc/AirPathUtilTest.kt:17:16: error: Unresolved reference 'normalizeAirPathhh'.",
            "plugins/air/shared/core/testSrc/AirPathUtilTest.kt:17:48: error: Unresolved reference 'isEqualTo'.",
        ]
    );

    let inline = [
        "plugins/air/x/testSrc/FooTest.kt:41:15: error: unresolved reference: resolveCwdd",
        "plugins/air/x/testSrc/FooTest.kt:52:3: warning: unused variable",
        "plugins/air/x/src/Bar.java:7: error: cannot find symbol",
    ]
    .join("\n");
    assert_eq!(
        parse_compile_errors(&inline),
        [
            "plugins/air/x/testSrc/FooTest.kt:41:15: error: unresolved reference: resolveCwdd",
            "plugins/air/x/src/Bar.java:7: error: cannot find symbol",
        ]
    );

    assert!(parse_compile_errors("INFO: Build completed successfully, 2 total actions\n").is_empty());
}
