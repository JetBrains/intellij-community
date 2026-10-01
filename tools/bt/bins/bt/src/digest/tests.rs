use bt_junit::{CaseFailure, FailureKind, Outcome, TestCase};
use pretty_assertions::assert_eq;

use std::path::Path;

use super::*;
use bt_core::bep::parse_bep;
use bt_core::exit;
use bt_core::fake::{AttemptSpec, FakeRuntime, attempt, bep_lines, case, suite, suite_xml, test_result_event, wrapper_xml};
use bt_core::result::{FlakyRun, collect_results};
use bt_core::runtime::Runtime;

#[test]
fn clamp_lines_preserves_clipping_and_omitted_counts() {
    let long_body = "line\n".repeat(10_000);
    let cases: [(&str, &str, usize, usize, &[&str]); 11] = [
        ("empty", "", 1, 100, &[""]),
        ("zero lines", "", 0, 100, &["…(1 more lines)"]),
        ("omit all", "first\nsecond\n", 0, 100, &["…(3 more lines)"]),
        ("trailing newline", "first\n", 1, 100, &["first", "…(1 more lines)"]),
        ("keep trailing newline", "first\n", 2, 100, &["first", ""]),
        (
            "carriage returns",
            "first\r\nsecond\r\n",
            2,
            100,
            &["first\r", "second\r", "…(1 more lines)"],
        ),
        ("long body", &long_body, 1, 100_000, &["line", "…(10000 more lines)"]),
        ("unicode clip", "α🙂\nβγ\nlast", 3, 4, &["α🙂", "β…"]),
        ("line and character clip", "α🙂\nβγ\nlast", 1, 4, &["α🙂", "…(1 more lines)"]),
        ("zero characters", "text", 1, 0, &["…"]),
        ("maximum line limit", "first\nsecond", usize::MAX, 100, &["first", "second"]),
    ];
    for (name, value, max_lines, max_chars, want) in cases {
        assert_eq!(clamp_lines(value, max_lines, max_chars), want, "{name}");
    }
}

#[test]
fn trim_frames_keeps_the_first_frame_and_the_air_frames() {
    let detail = [
        "org.opentest4j.AssertionFailedError: boom",
        "\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)",
        "\tat com.intellij.air.threads.AgentThreadCliTest.resolves(AgentThreadCliTest.kt:88)",
        "\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)",
        "\tat com.intellij.testFramework.UsefulTestCase.runBare(UsefulTestCase.java:412)",
        "\tat kotlinx.coroutines.BuildersKt.runBlocking(Builders.kt:1)",
    ]
    .join("\n");
    let trimmed = trim_frames(&detail, DEFAULT_MAX_FRAMES);
    // The first frame survives even though it is runner noise: which assertion helper threw is the difference
    // between a failed assertion and a NullPointerException.
    assert_eq!(
        trimmed,
        TrimmedStack {
            frames: vec![
                "at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)".to_owned(),
                "at com.intellij.air.threads.AgentThreadCliTest.resolves(AgentThreadCliTest.kt:88)".to_owned(),
            ],
            omitted: 3,
        }
    );
}

#[test]
fn trim_frames_caps_the_kept_frames_and_reports_the_remainder() {
    let detail: Vec<String> = (0..20)
        .map(|index| format!("\tat com.intellij.air.F{index}.m(F.kt:{index})"))
        .collect();
    let trimmed = trim_frames(&detail.join("\n"), DEFAULT_MAX_FRAMES);
    assert_eq!((trimmed.frames.len(), trimmed.omitted), (6, 14));
}

#[test]
fn trim_frames_returns_nothing_when_there_is_no_stack() {
    assert_eq!(trim_frames("just a message", DEFAULT_MAX_FRAMES), TrimmedStack::default());
}

#[test]
fn trim_frames_preserves_limits_and_ignores_non_frame_lines() {
    let detail = [
        "failure heading",
        "\tat java.lang.AssertionError.fail(AssertionError.java:1)",
        "Caused by: another exception",
        "    at kotlinx.coroutines.JobSupport.run(JobSupport.kt:2)",
        "\tat example.Helper.run(Helper.kt:3)",
        "   ... 5 more",
        "\tat com.intellij.air.Sample.run(Sample.kt:4)",
        "\tat org.junit.platform.Runner.run(Runner.java:5)",
    ]
    .join("\n");
    let interesting = [
        "at java.lang.AssertionError.fail(AssertionError.java:1)",
        "at example.Helper.run(Helper.kt:3)",
        "at com.intellij.air.Sample.run(Sample.kt:4)",
    ];
    for limit in [0, 1, 2, 3, 20] {
        let trimmed = trim_frames(&detail, limit);
        let want = &interesting[..limit.min(interesting.len())];
        assert_eq!(trimmed.frames, want, "limit {limit}");
        assert_eq!(trimmed.omitted, 5 - want.len(), "limit {limit}");
    }
}

#[test]
fn trim_frames_keeps_counts_across_line_endings() {
    let first = "at java.lang.AssertionError.fail(AssertionError.java:1)";
    let last = "at com.intellij.air.Sample.run(Sample.kt:4)";
    for newline in ["\n", "\r\n"] {
        for suffix in [String::new(), newline.to_owned(), newline.repeat(2)] {
            let detail = format!(
                "{}\t{first}{newline}{}\t{last}{suffix}",
                format!("failure heading{newline}").repeat(128),
                format!("\tat org.junit.platform.Runner.run(Runner.java:5){newline}").repeat(1000),
            );
            let trimmed = trim_frames(&detail, DEFAULT_MAX_FRAMES);
            assert_eq!(trimmed.frames, [first, last], "{newline:?} {suffix:?}");
            assert_eq!(trimmed.omitted, 1000, "{newline:?} {suffix:?}");
        }
    }
    for detail in ["", "\n", "\r\n\r\n"] {
        assert_eq!(trim_frames(detail, DEFAULT_MAX_FRAMES), TrimmedStack::default(), "{detail:?}");
    }
}

#[test]
fn format_duration_renders_milliseconds_seconds_and_minutes() {
    for (milliseconds, want) in [
        (420, "420ms"),
        (12_300, "12.3s"),
        (252_000, "4m12s"),
        // Rounding the seconds remainder on its own used to render these as 1m60s and 2m60s.
        (119_600, "2m00s"),
        (179_500, "3m00s"),
        (119_400, "1m59s"),
        // Exact ties round up.
        (3_750, "3.8s"),
        (1_250, "1.3s"),
        (1_750, "1.8s"),
        // A tie in decimal is a tie here too: there is no binary fraction below the midpoint.
        (1_150, "1.2s"),
        (1_149, "1.1s"),
    ] {
        assert_eq!(format_duration(milliseconds), want, "{milliseconds} ms");
    }
}

#[test]
fn relativize_testlogs_names_the_checkouts_symlink() {
    use bt_core::result::relativize_testlogs;
    assert_eq!(
        relativize_testlogs(
            "/Users/x/.cache/bazel/_bazel_x/abc/execroot/_main/bazel-out/darwin_arm64-fastbuild/testlogs/plugins/air/acp/acp-tests_test/shard_1_of_6/test.log"
        ),
        "out/bazel-testlogs/plugins/air/acp/acp-tests_test/shard_1_of_6/test.log"
    );
    assert_eq!(relativize_testlogs("/tmp/other.log"), "/tmp/other.log");
    // A Windows execroot, whose separators the marker has to see through. Left alone, the digest names a path under
    // bazel's output base instead of one in the checkout.
    assert_eq!(
        relativize_testlogs(r"C:\exec\bazel-out\x64_windows-fastbuild\testlogs\plugins\air\acp\acp-tests_test\test.log"),
        "out/bazel-testlogs/plugins/air/acp/acp-tests_test/test.log"
    );
}

// --- the digest --------------------------------------------------------------------------------------------------

const DIGEST_LABEL: &str = "//plugins/air/backend/session/runtime:air-backend-session-runtime-tests_test";

fn digest_target(apply: impl FnOnce(&mut TargetResult)) -> TargetResult {
    let mut target = TargetResult {
        label: DIGEST_LABEL.to_owned(),
        tests: 47,
        shards: 6,
        ..TargetResult::default()
    };
    apply(&mut target);
    target
}

fn target(label: &str, shards: u32, cached: bool, duration_ms: u64) -> TargetResult {
    TargetResult {
        label: label.to_owned(),
        shards,
        cached,
        duration_ms,
        ..TargetResult::default()
    }
}

fn digest_result(apply: impl FnOnce(&mut RunResult)) -> RunResult {
    let mut result = RunResult {
        status: RunStatus::Pass,
        exit_code: exit::GREEN,
        duration_ms: 12_300,
        targets: Vec::new(),
        failures: Vec::new(),
        flaky: Vec::new(),
        build_errors: Vec::new(),
        degraded: false,
    };
    apply(&mut result);
    result
}

fn digest_failure(apply: impl FnOnce(&mut TestCase)) -> TestCase {
    let mut failure = TestCase {
        class_name: "com.intellij.air.threads.AgentThreadCliTest".to_owned(),
        name: "resolvesWorktreeCwd".to_owned(),
        outcome: Outcome::Failed,
        failure: Some(CaseFailure {
            kind: FailureKind::Failure,
            r#type: Some("org.opentest4j.AssertionFailedError".to_owned()),
            message: Some(r#"expected: <"/repo/wt"> but was: <"/repo">"#.to_owned()),
            detail: "\tat com.intellij.air.threads.AgentThreadCliTest.resolvesWorktreeCwd(AgentThreadCliTest.kt:88)".to_owned(),
        }),
        time_seconds: 0.1,
    };
    apply(&mut failure);
    failure
}

fn render(result: &RunResult) -> String {
    render_digest(result, &RenderOptions::default())
}

#[test]
fn a_green_single_target_run_is_one_line() {
    let text = render(&digest_result(|result| {
        result.targets = vec![digest_target(|_| {})];
    }));
    assert_eq!(text, format!("PASS  47 tests  12.3s  {DIGEST_LABEL}"));
}

/// 63 labels is the noise the digest exists to remove; the cost line carries what the run actually spent.
#[test]
fn a_green_multi_target_run_summarises_counts() {
    let targets = (0..63)
        .map(|index| {
            digest_target(|target| {
                target.label = format!("//p:t{index}");
                target.tests = 20;
                target.cached = index < 11;
                target.duration_ms = index * 1000;
            })
        })
        .collect();
    let text = render(&digest_result(|result| {
        result.duration_ms = 252_000;
        result.targets = targets;
    }));
    assert_eq!(
        text,
        "PASS  1260 tests  63 targets  4m12s\ncost   52 ran · 11 cached · slowest t62 1m02s"
    );
}

/// A cache hit still reports the duration of the run it was cached from, which was not paid here.
#[test]
fn the_cost_line_ignores_cache_hits_when_naming_the_slowest_target() {
    let cost = render_cost(&[target("//p:slow-but-cached", 6, true, 500_000), target("//p:ran", 6, false, 9_000)]);
    assert_eq!(cost.as_deref(), Some("cost   1 ran · 1 cached · slowest ran 9.0s"));
}

#[test]
fn a_red_multi_target_run_reports_its_cost_too() {
    let text = render(&digest_result(|result| {
        result.status = RunStatus::Fail;
        result.exit_code = exit::TEST_FAILED;
        result.targets = vec![
            digest_target(|target| {
                target.label = "//p:a".to_owned();
                target.failed = 1;
                target.duration_ms = 4_000;
            }),
            digest_target(|target| {
                target.label = "//p:b".to_owned();
                target.cached = true;
            }),
        ];
        result.failures = vec![digest_failure(|_| {})];
    }));
    assert!(text.contains("cost   1 ran · 1 cached · slowest a 4.0s"), "{text}");
}

#[test]
fn a_single_target_run_has_no_cost_line() {
    assert_eq!(render_cost(&[digest_target(|_| {})]), None);
}

/// Targets that failed to build never reached a test JVM, so "63 ran" would be a lie.
#[test]
fn targets_that_never_ran_are_left_out_of_the_cost_line() {
    let not_built: Vec<TargetResult> = (0..3).map(|index| target(&format!("//p:x{index}"), 0, false, 0)).collect();
    assert_eq!(render_cost(&not_built), None);
    let mut mixed = not_built;
    mixed.push(target("//p:a", 6, false, 1_000));
    mixed.push(target("//p:b", 6, true, 0));
    assert_eq!(render_cost(&mixed).as_deref(), Some("cost   1 ran · 1 cached · slowest a 1.0s"));
}

#[test]
fn cost_line_preserves_target_selection() {
    let cases: Vec<(&str, Vec<TargetResult>, Option<&str>)> = vec![
        ("empty run", vec![], None),
        (
            "only one target executed",
            vec![target("//p:not-built", 0, false, 0), target("//p:ran", 1, false, 1_000)],
            None,
        ),
        (
            "all targets cached",
            vec![target("//p:first", 1, true, 1_000), target("//p:second", 2, true, 9_000)],
            Some("cost   0 ran · 2 cached"),
        ),
        (
            "equal durations select the first uncached target",
            vec![
                target("//p:cached", 1, true, 1_000),
                target("//p:z-first", 1, false, 1_000),
                target("//p:a-second", 1, false, 1_000),
            ],
            Some("cost   2 ran · 1 cached · slowest z-first 1.0s"),
        ),
        (
            "zero durations omit the slowest target",
            vec![target("//p:first", 1, false, 0), target("//p:second", 1, false, 0)],
            Some("cost   2 ran · 0 cached"),
        ),
    ];
    for (name, targets, want) in cases {
        let original = targets.clone();
        let cost = render_cost(&targets);
        assert_eq!(targets, original, "{name}: the targets changed");
        assert_eq!(cost.as_deref(), want, "{name}");
    }
}

#[test]
fn a_flaky_pass_names_the_test_and_stays_green_shaped() {
    let text = render(&digest_result(|result| {
        result.duration_ms = 22_800;
        result.targets = vec![digest_target(|target| target.flaky = 1)];
        result.flaky = vec![FlakyRun {
            id: format!("{DIGEST_LABEL} shard 3"),
            attempts: 2,
        }];
    }));
    assert_eq!(
        text.lines().next(),
        Some(format!("PASS  47 tests  22.8s  (1 flaky)  {DIGEST_LABEL}").as_str())
    );
    assert!(text.contains("flaky  "), "{text}");
}

#[test]
fn a_single_failure_renders_id_type_message_and_trimmed_frames() {
    let hint = "bt.cmd 'AgentThreadCliTest#resolvesWorktreeCwd'";
    let text = render_digest(
        &digest_result(|result| {
            result.status = RunStatus::Fail;
            result.duration_ms = 18_400;
            result.targets = vec![digest_target(|target| {
                target.failed = 1;
                target.log_path = Some("out/bazel-testlogs/plugins/air/x/test.log".to_owned());
            })];
            result.failures = vec![digest_failure(|_| {})];
        }),
        &RenderOptions {
            rerun_hint: Some(hint.to_owned()),
            ..RenderOptions::default()
        },
    );
    let want = [
        format!("FAIL  1 of 47 tests  18.4s  {DIGEST_LABEL}"),
        String::new(),
        "1) AgentThreadCliTest#resolvesWorktreeCwd".to_owned(),
        r#"   org.opentest4j.AssertionFailedError: expected: <"/repo/wt"> but was: <"/repo">"#.to_owned(),
        "   at com.intellij.air.threads.AgentThreadCliTest.resolvesWorktreeCwd(AgentThreadCliTest.kt:88)".to_owned(),
        String::new(),
        "log    out/bazel-testlogs/plugins/air/x/test.log".to_owned(),
        format!("rerun  {hint}"),
    ]
    .join("\n");
    assert_eq!(text, want);
}

#[test]
fn failures_past_the_cap_collapse_to_one_liners() {
    let failures = (0..5)
        .map(|index| digest_failure(|failure| failure.name = format!("case{index}")))
        .collect();
    let text = render_digest(
        &digest_result(|result| {
            result.status = RunStatus::Fail;
            result.targets = vec![digest_target(|target| target.failed = 5)];
            result.failures = failures;
        }),
        &RenderOptions {
            max_failures: 2,
            ..RenderOptions::default()
        },
    );
    let rendered = text
        .lines()
        .filter(|line| line.starts_with("1) ") || line.starts_with("2) "))
        .count();
    let collapsed = text.lines().filter(|line| line.starts_with("+ AgentThreadCliTest#case")).count();
    assert_eq!((rendered, collapsed), (2, 3), "{text}");
}

#[test]
fn a_build_failure_lists_diagnostics_and_no_test_counts() {
    let text = render(&digest_result(|result| {
        result.status = RunStatus::BuildFailed;
        result.targets = vec![digest_target(|target| target.tests = 0)];
        result.build_errors = vec!["plugins/air/x/testSrc/FooTest.kt:41:15: error: unresolved reference: resolveCwdd".to_owned()];
    }));
    assert!(text.contains("BUILD FAILED  0 tests ran"), "{text}");
    assert!(
        text.contains("FooTest.kt:41:15: error: unresolved reference: resolveCwdd"),
        "{text}"
    );
}

fn no_tests(apply: impl FnOnce(&mut TargetResult), options: &RenderOptions) -> String {
    render_digest(
        &digest_result(|result| {
            result.status = RunStatus::NoTests;
            result.targets = vec![digest_target(|target| {
                target.tests = 0;
                apply(target);
            })];
        }),
        options,
    )
}

/// The NO_TESTS causes look identical from the outside and need different fixes, so the digest names one.
#[test]
fn each_no_tests_cause_explains_itself() {
    let filtered = no_tests(|target| target.wrapper_exit_code = Some(42), &RenderOptions::default());
    assert!(filtered.contains("NO TESTS  0 executed"), "{filtered}");
    assert!(
        filtered.contains("the test filter matched no test class (runner exit 42)"),
        "{filtered}"
    );

    let skipped = no_tests(
        |target| {
            target.label = "//plugins/air/tests/integration/headless/acp:acp_test".to_owned();
            target.skipped = 6;
        },
        &RenderOptions::default(),
    );
    assert!(skipped.contains("NO TESTS  0 executed, 6 skipped"), "{skipped}");
    assert!(skipped.contains("assumeTrue"), "{skipped}");

    let nothing = render(&digest_result(|result| {
        result.status = RunStatus::NoTests;
    }));
    assert!(nothing.contains("no test target matched"), "{nothing}");
}

/// A `#method` run that executed nothing is the fourth cause, and it is the one a reader can act on: the method
/// filter matched no method, so the next command is the class alone.
#[test]
fn a_method_filtered_no_tests_run_blames_the_method_filter() {
    let method_filtered = RenderOptions {
        method_filtered: true,
        ..RenderOptions::default()
    };
    let named = no_tests(|_| {}, &method_filtered);
    assert!(named.contains("cause     the #method filter matched no method"), "{named}");
    assert!(named.contains("rerun the class alone"), "{named}");
    // It replaces only the cause the digest could not name; a run that narrowed to no method keeps that one.
    assert!(!no_tests(|_| {}, &RenderOptions::default()).contains("#method filter"));
    // A skipped test was matched, so the filter did its job and the skip is the cause worth naming.
    let skipped = no_tests(|target| target.skipped = 1, &method_filtered);
    assert!(skipped.contains("assumeTrue"), "{skipped}");
    assert!(!skipped.contains("#method filter"), "{skipped}");
}

// --- the rerun selector ------------------------------------------------------------------------------------------

/// [`test_case_id`] prints what test.xml carries and [`rerun_selector`] answers what `bt` can run. The two differ
/// exactly where the renderer writes a display name instead of a method name.
#[test]
fn rerun_selector_keeps_a_method_only_when_the_method_is_selectable() {
    for (class_name, name, want) in [
        // An ordinary method, and a Kotlin backticked one, both round-trip whole.
        ("com.intellij.air.x.FooTest", "resolvesCwd", "FooTest#resolvesCwd"),
        (
            "com.intellij.air.x.FooTest",
            "the client's token is not logged",
            "FooTest#the client's token is not logged",
        ),
        // A dynamic test's display name, and a test-template child's parent-prefixed one.
        ("com.intellij.air.x.FooTest", "[1] value=x", "FooTest"),
        ("com.intellij.air.x.FooTest", "renders.[1] value=x", "FooTest"),
        // Each JVM-forbidden character on its own.
        ("com.intellij.air.x.FooTest", "a.b", "FooTest"),
        ("com.intellij.air.x.FooTest", "a;b", "FooTest"),
        ("com.intellij.air.x.FooTest", "a/b", "FooTest"),
        ("com.intellij.air.x.FooTest", "<init>", "FooTest"),
        // A `@Nested` class reaches test.xml as `Outer$Nested`, and the outer class runs the nested one.
        ("com.intellij.air.x.FooTest$Inner", "resolvesCwd", "FooTest#resolvesCwd"),
        // A suite that named no class leaves the name as the only thing there is to print.
        ("", "orphan", "orphan"),
    ] {
        let failure = digest_failure(|failure| {
            failure.class_name = class_name.to_owned();
            failure.name = name.to_owned();
        });
        assert_eq!(rerun_selector(&failure), want, "{class_name} / {name:?}");
    }
}

#[test]
fn a_degraded_run_says_so() {
    let text = render(&digest_result(|result| {
        result.status = RunStatus::Fail;
        result.targets = vec![digest_target(|target| target.failed = 1)];
        result.degraded = true;
    }));
    assert!(text.contains("details were incomplete"), "{text}");
}

/// The whole reason the digest exists: a failing test's captured output measured 146 KB, and none of it may reach
/// an agent's context through here.
#[test]
fn no_digest_ever_leaks_captured_output() {
    let poison = "x".repeat(1000);
    let text = render(&digest_result(|result| {
        result.status = RunStatus::Fail;
        result.targets = vec![digest_target(|target| target.failed = 1)];
        result.failures = vec![digest_failure(|failure| {
            if let Some(detail) = failure.failure.as_mut() {
                detail.message = Some(format!("boom {poison}"));
            }
        })];
    }));
    assert!(text.len() < 1200, "the digest is {} bytes", text.len());
}

/// A 61-target lane run has 61 logs; only the ones belonging to a target that actually failed are worth pointing an
/// agent at.
#[test]
fn only_the_logs_of_failing_targets_are_blamed() {
    let text = render(&digest_result(|result| {
        result.status = RunStatus::Fail;
        result.exit_code = exit::TEST_FAILED;
        result.duration_ms = 1000;
        result.targets = vec![
            TargetResult {
                label: "//p:green".to_owned(),
                tests: 10,
                shards: 1,
                log_path: Some("green.log".to_owned()),
                ..TargetResult::default()
            },
            TargetResult {
                label: "//p:red".to_owned(),
                tests: 10,
                failed: 1,
                shards: 1,
                log_path: Some("red.log".to_owned()),
                ..TargetResult::default()
            },
        ];
    }));
    assert!(text.contains("log    red.log"), "{text}");
    assert!(!text.contains("green.log"), "{text}");
}

#[test]
fn tail_lines_keeps_only_the_end() {
    assert_eq!(tail_lines("a\nb\nc", 10), "a\nb\nc");
    assert_eq!(tail_lines("a\nb\nc\nd", 2), "c\nd");
}

#[test]
fn one_line_collapses_and_truncates() {
    assert_eq!(one_line("  a\n\tb   c  ", 120), "a b c");
    assert_eq!(one_line(&"x".repeat(200), 10), format!("{}…", "x".repeat(9)));
}

// --- digests of collected runs ----------------------------------------------------------------------------------

fn collected(xml: &str, status: &'static str, bazel_exit: i32) -> RunResult {
    let fake = FakeRuntime::new([]);
    fake.put_absolute("/exec/testlogs/p/t/test.xml", xml);
    fake.put_absolute(
        "/tmp/bep.json",
        &bep_lines(&[test_result_event(&AttemptSpec {
            status,
            xml: "/exec/testlogs/p/t/test.xml",
            cached: status == "PASSED",
            ..attempt("//p:t")
        })]),
    );
    let bep = parse_bep(fake.read_lines(Path::new("/tmp/bep.json")), fake.platform());
    collect_results(&fake, &bep, 5000, bazel_exit, "")
}

/// A cache hit still has a test.xml, and the digest names the target as cached.
#[test]
fn a_cached_replay_is_named_cached() {
    let result = collected(&suite_xml(&suite("FooTest", vec![case("a"), case("b")])), "PASSED", 0);
    let digest = render_digest(&result, &RenderOptions::default());
    assert!(digest.contains("(cached)"), "{digest}");
}

/// The synthesized wrapper suite carries a whole log in `<system-out>`, and none of it reaches the digest.
#[test]
fn the_synthesized_wrapper_suite_leaks_no_log_into_the_digest() {
    let result = collected(
        &wrapper_xml(
            "plugins/air/shared/core/ai-agent-core-tests_test",
            42,
            &format!("No tests found\n{}", "noise ".repeat(5000)),
        ),
        "FAILED",
        3,
    );
    assert_eq!(result.exit_code, exit::NO_TESTS);
    let digest = render_digest(&result, &RenderOptions::default());
    assert!(!digest.contains("noise"), "the captured log leaked into the digest:\n{digest}");
}

/// Only the wrapper code 42 earns the "the test filter matched no test class" cause; the rest fall to the
/// method-filter cause, which is the one a `#method` run can act on.
#[test]
fn a_discovery_failure_names_the_method_filter() {
    for wrapper_code in [2, 1, 255] {
        let result = collected(
            &wrapper_xml(
                "plugins/air/shared/session/air-shared-session-tests_test",
                wrapper_code,
                "Could not find method with name [no such method]",
            ),
            "FAILED",
            3,
        );
        let digest = render_digest(
            &result,
            &RenderOptions {
                method_filtered: true,
                ..RenderOptions::default()
            },
        );
        assert!(
            digest.contains("the #method filter matched no method"),
            "wrapper code {wrapper_code}:\n{digest}"
        );
    }
}
