use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use bt_core::fake::{
    AttemptSpec, FakeRuntime, REPO_ROOT, area_files, attempt, bep_lines, build_bazel_text, case,
    failing, flow_catalog_tree, lanes, refusal, suite, suite_xml, test_result_event,
};
use bt_core::runtime::SpawnResult;

/// A fake tree with `bt.json` and the fixture lane table, which every invocation reads first.
fn with_areas(fake: FakeRuntime) -> FakeRuntime {
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    fake
}

fn fake_air_tree() -> FakeRuntime {
    with_areas(bt_core::fake::fake_air_tree())
}

const CORE_LABEL: &str = "//plugins/air/shared/core:ai-agent-core-tests_test";
const BEP_PATH: &str = "/tmp/air-bazel-test-bep.json";
const XML_PATH: &str = "/exec/testlogs/p/t/test.xml";

fn run(argv: &[&str], fake: &FakeRuntime) -> Outcome {
    execute(argv, fake).unwrap_or_else(|failure| panic!("{argv:?} refused: {failure:?}"))
}

fn dry_run(argv: &[&str]) -> String {
    let fake = fake_air_tree();
    let mut argv = argv.to_vec();
    argv.push("--dry-run");
    let outcome = run(&argv, &fake);
    assert!(
        fake.spawned().is_empty(),
        "--dry-run spawned {:?}",
        fake.spawned()
    );
    outcome.text
}

#[track_caller]
fn assert_mentions(text: &str, expected: &[&str]) {
    for expected in expected {
        assert!(text.contains(expected), "no {expected:?} in:\n{text}");
    }
}

fn spawn_answers(fake: &FakeRuntime, exit_code: i32, output: &str) {
    fake.state().spawn_result = SpawnResult {
        exit_code,
        output: output.to_owned(),
    };
}

/// A run of the core target whose BEP names one attempt with this status, reading the given test.xml.
fn one_attempt(fake: &FakeRuntime, status: &'static str, xml: &str) {
    fake.put_absolute(XML_PATH, xml);
    fake.put_absolute(
        BEP_PATH,
        &bep_lines(&[test_result_event(&AttemptSpec {
            status,
            xml: XML_PATH,
            ..attempt(CORE_LABEL)
        })]),
    );
}

#[test]
fn help_exits_green_with_the_usage_text() {
    let outcome = run(&["--help"], &fake_air_tree());
    assert_eq!(outcome.exit_code, exit::GREEN);
    assert!(
        outcome.text.contains("Agent-friendly wrapper"),
        "{}",
        outcome.text
    );
}

/// No arguments still prints the help, because the help is the answer; but it is a usage failure, so a script that
/// ran `bt` with an unset variable does not read green.
#[test]
fn no_arguments_is_a_usage_failure_that_still_prints_the_help() {
    let outcome = run(&[], &fake_air_tree());
    assert_eq!(outcome.exit_code, exit::USAGE);
    assert!(outcome.text.contains("Usage:"), "{}", outcome.text);
}

#[test]
fn dry_run_resolves_without_spawning_bazel() {
    assert_mentions(
        &dry_run(&["AgentThreadIdentityTest"]),
        &[
            &format!("label   {CORE_LABEL}"),
            "filter  com.intellij.air.shared.core.AgentThreadIdentityTest",
            "--test_sharding_strategy=explicit",
        ],
    );
}

#[test]
fn dry_run_on_a_lane_prints_the_lane_flags() {
    assert_mentions(
        &dry_run(&["--lane", "fast"]),
        &[
            "label   //plugins/air/...",
            "--test_tag_filters=-air-integration-ui,-air-integration-headless",
        ],
    );
}

/// The excluded targets are compiled first, in their own spawn, so `--lane fast` still answers whether they build.
/// Order matters: a compile break must not cost a whole lane run first.
#[test]
fn the_fast_lane_builds_the_excluded_targets_before_it_runs_anything() {
    let fake = fake_air_tree();
    run(&["--lane", "fast"], &fake);
    let spawned = fake.spawned();
    assert_eq!(spawned.len(), 2, "{spawned:?}");
    let first = spawned[0].join(" ");
    assert!(first.contains(" build "), "{first}");
    assert!(
        first.contains("//plugins/air/tests/integration/ui:ui_test_lib"),
        "{first}"
    );
    assert!(spawned[1].join(" ").contains(" test "), "{:?}", spawned[1]);
}

/// A target that does not compile is the answer, not a footnote after a green lane.
#[test]
fn a_failed_excluded_build_stops_the_lane_and_reports_build_failed() {
    let fake = fake_air_tree();
    spawn_answers(&fake, 1, "ERROR: ui_test does not compile");
    let outcome = run(&["--lane", "fast"], &fake);
    assert_eq!(outcome.exit_code, exit::BUILD_FAILED, "{}", outcome.text);
    assert_eq!(fake.spawned().len(), 1, "the lane ran anyway");
    assert!(
        fake.state()
            .stderr
            .join("\n")
            .contains("ui_test does not compile"),
        "bazel's own words are the only evidence of a failed build"
    );
}

/// The OS runtime creates the BEP file to reserve its name, so the early answers must remove it too, not only a run.
#[test]
fn the_bep_file_is_deleted_after_a_dry_run_and_after_a_failed_excluded_build() {
    let fake = fake_air_tree();
    let outcome = run(&["--lane", "fast", "--dry-run"], &fake);
    assert_eq!(outcome.exit_code, exit::GREEN, "{}", outcome.text);
    assert_eq!(fake.state().removed, [BEP_PATH], "after --dry-run");

    let fake = fake_air_tree();
    spawn_answers(&fake, 1, "ERROR: ui_test does not compile");
    let outcome = run(&["--lane", "fast"], &fake);
    assert_eq!(outcome.exit_code, exit::BUILD_FAILED, "{}", outcome.text);
    assert_eq!(
        fake.state().removed,
        [BEP_PATH],
        "after a failed excluded build"
    );
}

/// A lane parallelises across its targets already, so per-target shards are only fixed JVM cost.
#[test]
fn a_lane_shards_each_target_twice_and_a_single_target_six_times() {
    assert_mentions(
        &dry_run(&["--lane", "fast"]),
        &["--test_sharding_strategy=forced=2"],
    );
    assert_mentions(
        &dry_run(&[CORE_LABEL]),
        &["--test_sharding_strategy=forced=6"],
    );
}

#[test]
fn an_explicit_shard_count_wins_over_the_breadth_derived_default() {
    assert_mentions(
        &dry_run(&["--lane", "fast", "--shards", "9"]),
        &["--test_sharding_strategy=forced=9"],
    );
}

#[test]
fn every_lane_dry_runs_to_a_concrete_target() {
    for spec in lanes().iter() {
        let outcome = run(&["--lane", &spec.name, "--dry-run"], &fake_air_tree());
        assert_eq!(outcome.exit_code, exit::GREEN, "lane {}", spec.name);
        assert!(
            outcome.text.contains(&spec.targets[0]),
            "lane {}:\n{}",
            spec.name,
            outcome.text
        );
    }
}

/// bt's own mode rules are pinned by their wording; the parse errors clap raises are pinned by the flag or the
/// value they name, since clap words those.
#[test]
fn the_invocation_modes_are_refused_when_they_conflict() {
    let cases: [(&[&str], &str); 9] = [
        (&["FooTest", "--lane", "fast"], "mutually exclusive"),
        (&["--lane", "nope"], "Unknown lane: nope"),
        // The same rule raw bazel has: a wildcard pattern and a test filter cannot be combined.
        (
            &["--lane", "fast", "--filter", "a.b.C"],
            "cannot be combined with the wildcard pattern",
        ),
        (
            &["FooTest", "--filter", "a.b.C"],
            "only valid with an explicit //label",
        ),
        (
            &["plugins/air/shared/core", "--filter", "Foo"],
            "only valid with an explicit //label",
        ),
        (
            &["//plugins/air/...", "--filter", "a.b.C"],
            "only valid with an explicit //label",
        ),
        (&["FooTest", "BarTest"], "BarTest"),
        (&["FooTest", "--shards", "six"], "six"),
        (&["--lane", "fast", "--lane", "ui"], "--lane"),
    ];
    for (argv, message) in cases {
        let fake = fake_air_tree();
        let failure = refusal(execute(argv, &fake));
        assert!(
            failure.message.contains(message),
            "{argv:?} refused with {:?}, want {message:?}",
            failure.message
        );
        assert_eq!(failure.exit, exit::USAGE, "{argv:?}");
        assert!(
            fake.spawned().is_empty(),
            "{argv:?} spawned before refusing"
        );
    }
}

/// `--lane` narrows only a flow or a suite selector. Beside any other selector it is still a second mode.
#[test]
fn lane_beside_a_class_selector_is_still_refused() {
    let failure = refusal(execute(
        ["AgentThreadIdentityTest", "--lane", "ui", "--dry-run"],
        &fake_air_tree(),
    ));
    assert!(
        failure.message.contains("mutually exclusive"),
        "{}",
        failure.message
    );
}

/// A flow selector reaches `execute` as its lane narrowed to the flow's suites, and `--lane` beside it settles a
/// two-lane flow instead of being a second mode.
#[test]
fn a_flow_selector_dry_runs_as_its_lane_and_lane_settles_it() {
    let fake = with_areas(FakeRuntime::with_tree(&flow_catalog_tree()));
    let settled = run(
        &["flow-manage-launch-preset", "--lane", "ui", "--dry-run"],
        &fake,
    );
    assert_mentions(
        &settled.text,
        &[
            "label   //plugins/air/tests/integration/ui/...",
            "suite   AirManageLaunchPresetQuickStartGeneratedFlowUiTest (manage-launch-preset-quick-start, lane ui)",
            r"filter  include-classname=(^|.*\.)AirManageLaunchPresetQuickStartGeneratedFlowUiTest$",
            "--test_sharding_strategy=forced=1",
        ],
    );
    assert!(!settled.text.contains("--test_filter"), "{}", settled.text);
    let ambiguous = refusal(execute(["flow-manage-launch-preset", "--dry-run"], &fake));
    assert_eq!(ambiguous.code, "affected_lanes_ambiguous");
    // `--list` shows every lane before `--lane` narrows it.
    let listed = run(&["flow-manage-launch-preset", "--list"], &fake);
    assert_mentions(
        &listed.text,
        &[
            "gui-chat AirManageLaunchPresetGeneratedFlowUiTest (manage-launch-preset)",
            "ui AirManageLaunchPresetQuickStartGeneratedFlowUiTest (manage-launch-preset-quick-start)",
        ],
    );
    assert!(fake.spawned().is_empty());
}

#[test]
fn list_prints_candidates_without_running_bazel() {
    let fake = fake_air_tree();
    let outcome = run(
        &["AgentPromptChangesTreeContextContributorTest", "--list"],
        &fake,
    );
    assert!(fake.spawned().is_empty());
    assert_mentions(
        &outcome.text,
        &[
            "com.intellij.air.backend.vcs.context.AgentPromptChangesTreeContextContributorTest",
            "//plugins/air/backend/vcs:air-backend-vcs-tests_test",
            "//plugins/air/frontend/prompt/vcs:air-frontend-prompt-vcs-tests_test",
        ],
    );
    let near = run(&["AgentThreadCliTst", "--list"], &fake);
    assert_eq!(near.text, "(no candidates)\nsimilar: AgentThreadCliTest");
    let package = run(&["com.intellij.air.shared.core", "--list"], &fake);
    assert_mentions(&package.text, &[CORE_LABEL]);
}

/// A label outside plugins/air is not resolved at all, which is what makes the wrapper usable for any target.
#[test]
fn an_external_label_with_a_filter_passes_both_through_untouched() {
    assert_mentions(
        &dry_run(&[
            "//tests/ideaProjectStructure:projectStructureTests_test",
            "--filter",
            "com.intellij.ideaProjectStructure.fast.KotlinFacetsConfigurationTest",
        ]),
        &[
            "label   //tests/ideaProjectStructure:projectStructureTests_test",
            "--test_filter=com.intellij.ideaProjectStructure.fast.KotlinFacetsConfigurationTest",
        ],
    );
}

/// The hazard this closes: `//plugins/air/...` without the lane's flags builds every non-test target and launches
/// real IDEs through IDE Starter, one keystroke from the safe command.
#[test]
fn a_hand_written_wildcard_gets_the_fast_lane_guards() {
    for (selector, label) in [
        ("plugins/air/shared/core", "//plugins/air/shared/core/..."),
        ("//plugins/air/...", "//plugins/air/..."),
    ] {
        assert_mentions(
            &dry_run(&[selector]),
            &[
                &format!("label   {label}"),
                "--build_tests_only",
                "--test_tag_filters=-air-integration-ui,-air-integration-headless",
                " -k ",
            ],
        );
    }
}

#[test]
fn a_directory_inside_the_integration_tree_keeps_its_tags() {
    let fake = fake_air_tree();
    fake.put(
        "plugins/air/tests/integration/headless/acp/BUILD.bazel",
        &build_bazel_text("acp_test"),
    );
    let text = run(
        &["plugins/air/tests/integration/headless/acp", "--dry-run"],
        &fake,
    )
    .text;
    assert_mentions(
        &text,
        &["label   //plugins/air/tests/integration/headless/acp/..."],
    );
    assert!(!text.contains("--test_tag_filters"), "{text}");
}

#[test]
fn the_all_lane_carries_no_extras_so_it_still_covers_integration() {
    let text = dry_run(&["--lane", "all"]);
    for forbidden in ["--test_tag_filters", "--build_tests_only"] {
        assert!(!text.contains(forbidden), "{text}");
    }
}

#[test]
fn a_directory_that_does_not_exist_fails_before_bazel_is_spawned() {
    let fake = fake_air_tree();
    let failure = refusal(execute(["plugins/air/does/not/exist"], &fake));
    assert!(
        failure.message.contains("No such directory"),
        "{}",
        failure.message
    );
    assert!(fake.spawned().is_empty());
}

fn green_run(cases: Vec<bt_core::fake::CaseSpec>) -> FakeRuntime {
    let fake = fake_air_tree();
    fake.put_absolute(
        XML_PATH,
        &suite_xml(&suite("AgentThreadIdentityTest", cases)),
    );
    fake.put_absolute(
        BEP_PATH,
        &bep_lines(&[
            test_result_event(&AttemptSpec {
                xml: XML_PATH,
                ..attempt(CORE_LABEL)
            }),
            json!({"id": {"buildFinished": {}}, "finished": {"overallSuccess": true}}),
        ]),
    );
    fake
}

#[test]
fn a_full_run_spawns_bazel_once_digests_the_result_and_deletes_the_bep_file() {
    let fake = green_run(vec![case("a"), case("b")]);
    let outcome = run(&["AgentThreadIdentityTest"], &fake);
    let spawned = fake.spawned();
    assert_eq!(spawned.len(), 1);
    assert_eq!(
        spawned[0][..2],
        ["/bin/sh", &format!("{REPO_ROOT}/bazel.cmd")]
    );
    assert!(
        spawned[0].iter().any(|argument| argument
            == "--test_filter=com.intellij.air.shared.core.AgentThreadIdentityTest"),
        "{:?}",
        spawned[0]
    );
    assert_eq!(outcome.exit_code, exit::GREEN);
    assert_eq!(outcome.text, format!("PASS  2 tests  5.0s  {CORE_LABEL}"));
    // No payload without --json: stdout stays untouched for a caller that did not ask for one.
    assert_eq!(outcome.json, None);
    assert_eq!(fake.state().removed, [BEP_PATH]);
}

/// The `--json` payload is the stable contract.
#[test]
fn json_returns_a_structured_payload_alongside_the_digest() {
    let fake = fake_air_tree();
    one_attempt(
        &fake,
        "FAILED",
        &suite_xml(&suite(
            "AgentThreadIdentityTest",
            vec![failing(
                "a",
                "org.opentest4j.AssertionFailedError",
                "boom",
                "\tat com.intellij.air.shared.core.AgentThreadIdentityTest.a(X.kt:9)",
            )],
        )),
    );
    spawn_answers(&fake, 3, "");

    let outcome = run(&["AgentThreadIdentityTest", "--json"], &fake);
    assert_eq!(outcome.exit_code, exit::TEST_FAILED);
    assert!(
        outcome.text.starts_with("FAIL"),
        "the digest travels beside the payload"
    );
    let payload = outcome.json.expect("--json builds a payload");
    assert_eq!(
        (payload.status, payload.exit_code),
        (RunStatus::Fail, exit::TEST_FAILED)
    );
    assert_eq!(payload.failures.len(), 1);
    assert_eq!(payload.failures[0].name, "a");
    assert!(
        payload.failures[0]
            .frames
            .first()
            .is_some_and(|frame| frame.contains("AgentThreadIdentityTest.a(X.kt:9)")),
        "{:?}",
        payload.failures[0].frames
    );

    // The payload has to round-trip, so a downstream skill can parse stdout unconditionally, and the empty
    // collections have to be `[]` rather than `null`, or every reader needs a null check.
    let decoded: serde_json::Value =
        serde_json::from_str(&serde_json::to_string(&payload).expect("encodes")).expect("decodes");
    assert_eq!(decoded["targets"][0]["label"], CORE_LABEL);
    assert_eq!(decoded["flaky"], json!([]));
    assert_eq!(decoded["errors"], json!([]));
    // Every key of the published contract, in its published order, so a rename cannot pass unnoticed.
    let mut keys: Vec<&str> = decoded
        .as_object()
        .expect("an object")
        .keys()
        .map(String::as_str)
        .collect();
    keys.sort_unstable();
    let mut sorted_contract = [
        "status",
        "exitCode",
        "durationMs",
        "cachedTargets",
        "ranTargets",
        "targets",
        "failures",
        "flaky",
        "errors",
        "degraded",
    ];
    sorted_contract.sort_unstable();
    assert_eq!(keys, sorted_contract);
    let wire = serde_json::to_string(&payload).expect("encodes");
    assert!(
        wire.starts_with(r#"{"status":"FAIL","exitCode":3,"durationMs":5000,"cachedTargets":0,"ranTargets":1,"targets":[{"label":"#),
        "{wire}"
    );
    let failure = decoded["failures"][0].as_object().expect("an object");
    for key in [
        "className",
        "name",
        "type",
        "message",
        "frames",
        "framesOmitted",
    ] {
        assert!(failure.contains_key(key), "a failure has no {key:?}");
    }
    assert_eq!(
        serde_json::from_value::<JsonPayload>(decoded).expect("the payload reads back"),
        payload
    );
}

/// The digest goes to the caller and progress goes to stderr, which is what keeps `--json` stdout machine-clean.
#[test]
fn the_invoked_command_line_is_echoed_to_stderr_never_to_stdout() {
    let fake = fake_air_tree();
    fake.put_absolute(BEP_PATH, "");
    run(&["AgentThreadIdentityTest"], &fake);
    let state = fake.state();
    assert!(
        state
            .stderr
            .iter()
            .any(|line| line.starts_with("> bazel test ")),
        "{:?}",
        state.stderr
    );
    // The BEP path is a temporary file nobody can act on, and it would make every echo differ.
    assert!(
        !state
            .stderr
            .iter()
            .any(|line| line.contains("--build_event_json_file")),
        "{:?}",
        state.stderr
    );
    assert!(state.stdout.is_empty(), "{:?}", state.stdout);
}

#[test]
fn a_bazel_that_cannot_even_start_is_infrastructure() {
    let fake = fake_air_tree();
    fake.put_absolute(BEP_PATH, "");
    spawn_answers(&fake, 127, "bazel.cmd: not found");
    let outcome = run(&["AgentThreadIdentityTest"], &fake);
    assert_eq!(outcome.exit_code, exit::INFRA);
    assert!(outcome.text.contains("INFRA"), "{}", outcome.text);
    // INFRA is the one status where bazel's own words are the only evidence there is.
    assert!(
        fake.state()
            .stderr
            .join("\n")
            .contains("bazel.cmd: not found"),
        "{:?}",
        fake.state().stderr
    );
}

/// Passthrough args land last and win, so this flag used to redirect the events away from the file the wrapper then
/// parsed, reported as INFRA on a run that had in fact passed.
#[test]
fn a_passthrough_bep_path_is_adopted_rather_than_breaking_result_collection() {
    for passthrough in [
        &["--build_event_json_file=/tmp/mine.json"][..],
        &["--build_event_json_file", "/tmp/mine.json"][..],
    ] {
        let fake = fake_air_tree();
        fake.put_absolute(
            XML_PATH,
            &suite_xml(&suite("AgentThreadIdentityTest", vec![case("a")])),
        );
        fake.put_absolute(
            "/tmp/mine.json",
            &bep_lines(&[test_result_event(&AttemptSpec {
                xml: XML_PATH,
                ..attempt(CORE_LABEL)
            })]),
        );
        let mut argv = vec!["AgentThreadIdentityTest", "--"];
        argv.extend_from_slice(passthrough);
        let outcome = run(&argv, &fake);
        assert_eq!(
            outcome.exit_code,
            exit::GREEN,
            "{passthrough:?}: {}",
            outcome.text
        );
        assert!(outcome.text.contains("PASS  1 test"), "{}", outcome.text);
        // The file is the caller's, so it outlives the run.
        assert!(fake.state().removed.is_empty(), "{passthrough:?}");
    }
}

#[test]
fn the_bep_file_is_deleted_even_when_the_run_blows_up() {
    // No BEP file written at all: the reader sees nothing, and the cleanup must still happen.
    let fake = fake_air_tree();
    spawn_answers(&fake, 1, "boom");
    run(&["AgentThreadIdentityTest"], &fake);
    assert_eq!(fake.state().removed, [BEP_PATH]);

    // And a run that unwinds: the guard runs on the panic's way out.
    struct Exploding(FakeRuntime);
    impl Runtime for Exploding {
        fn repo_root(&self) -> &std::path::Path {
            self.0.repo_root()
        }
        fn platform(&self) -> bt_core::runtime::Platform {
            self.0.platform()
        }
        fn read_text_file(&self, path: &std::path::Path) -> std::io::Result<String> {
            self.0.read_text_file(path)
        }
        fn read_lines(&self, _: &std::path::Path) -> Box<dyn Iterator<Item = String> + '_> {
            panic!("the BEP reader blew up")
        }
        fn read_dir(
            &self,
            path: &std::path::Path,
        ) -> std::io::Result<Vec<bt_core::runtime::DirEntry>> {
            self.0.read_dir(path)
        }
        fn list_files(&self, dir: &std::path::Path) -> std::io::Result<Vec<PathBuf>> {
            self.0.list_files(dir)
        }
        fn exists(&self, path: &std::path::Path) -> bool {
            self.0.exists(path)
        }
        fn spawn(
            &self,
            command: &[OsString],
            heartbeat: Option<bt_core::runtime::Heartbeat<'_>>,
        ) -> SpawnResult {
            self.0.spawn(command, heartbeat)
        }
        fn temp_file(&self, prefix: &str) -> PathBuf {
            self.0.temp_file(prefix)
        }
        fn remove(&self, path: &std::path::Path) -> std::io::Result<()> {
            self.0.remove(path)
        }
        fn now_ms(&self) -> u64 {
            self.0.now_ms()
        }
        fn write(&self, text: &str) {
            self.0.write(text);
        }
        fn write_error(&self, text: &str) {
            self.0.write_error(text);
        }
    }
    let exploding = Exploding(fake_air_tree());
    let unwound = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        execute(["AgentThreadIdentityTest"], &exploding)
    }));
    assert!(unwound.is_err(), "the run was supposed to blow up");
    assert_eq!(exploding.0.state().removed, [BEP_PATH]);
}

/// The digest of a run whose one test failed under the given name.
fn failed_run(name: &str) -> String {
    let fake = fake_air_tree();
    one_attempt(
        &fake,
        "FAILED",
        &suite_xml(&suite(
            "AgentThreadIdentityTest",
            vec![failing(
                name,
                "E",
                "boom",
                "\tat com.intellij.air.X.y(X.kt:1)",
            )],
        )),
    );
    spawn_answers(&fake, 3, "");
    run(&["AgentThreadIdentityTest"], &fake).text
}

/// The selector the digest's rerun hint carries, unquoted. Parsed out of the rendered digest rather than taken from
/// [`rerun_selector`], because the defect this covers was in the rendering: bare single quotes around a name that
/// holds an apostrophe.
fn rerun_line(digest: &str) -> String {
    const PREFIX: &str = "rerun  ./community/tools/bt.cmd ";
    let quoted = digest
        .lines()
        .find_map(|line| line.strip_prefix(PREFIX))
        .unwrap_or_else(|| panic!("no rerun hint in:\n{digest}"));
    let inner = quoted
        .strip_prefix('\'')
        .and_then(|rest| rest.strip_suffix('\''))
        .unwrap_or_else(|| panic!("the hint is not one quoted word: {quoted}"));
    inner.replace(r"'\''", "'")
}

#[test]
fn a_rerun_hint_names_the_first_failure() {
    assert_mentions(
        &failed_run("broken"),
        &["rerun  ./community/tools/bt.cmd 'AgentThreadIdentityTest#broken'"],
    );
}

/// Kotlin backticked test names hold apostrophes, which the method pattern admits on purpose. Bare single quotes
/// around one produced a command the shell could not even parse.
#[test]
fn a_rerun_hint_quotes_an_apostrophe_in_the_method_name() {
    assert_mentions(
        &failed_run("the client's token is not logged"),
        &[
            r"rerun  ./community/tools/bt.cmd 'AgentThreadIdentityTest#the client'\''s token is not logged'",
        ],
    );
}

/// A dynamic test reaches test.xml under its display name, which names no method. The class rerun is the one that
/// works, so the hint is the class.
#[test]
fn a_rerun_hint_falls_back_to_the_class_for_a_display_name() {
    for name in [
        "[1] value=x",
        "template.[1] value=x",
        "a/b",
        "holds; a semicolon",
    ] {
        assert_eq!(
            rerun_line(&failed_run(name)),
            "AgentThreadIdentityTest",
            "{name:?}"
        );
    }
}

/// The hint and the selector parser are one grammar, not two. Every hint this tool prints has to be something the
/// parser reads back, or the one command a reader copies after a failure refuses at exit 2.
#[test]
fn every_rerun_hint_round_trips_through_the_selector_parser() {
    for name in [
        "broken",
        "the client's token is not logged",
        "a name with spaces",
        "[1] value=x",
        "template.[1] value=x",
        "holds; a semicolon",
        "<init>",
        "a/b",
    ] {
        let hint = rerun_line(&failed_run(name));
        let selector = Selector::classify(&hint)
            .unwrap_or_else(|failure| panic!("the hint for {name:?} does not parse: {failure:?}"));
        assert_eq!(selector.kind, SelectorKind::SimpleName, "{name:?}");
    }
}

/// A `#method` run that executes nothing has one known cause, and the digest has to name it: the reader's next
/// command is the class rerun, not a third guess at what went wrong.
#[test]
fn a_method_filtered_run_that_executes_nothing_names_the_method_filter() {
    let fake = fake_air_tree();
    one_attempt(
        &fake,
        "FAILED",
        &suite_xml(&suite("AgentThreadIdentityTest", Vec::new())),
    );
    spawn_answers(&fake, 3, "");
    let outcome = run(&["AgentThreadIdentityTest#no such method"], &fake);
    // Zero executed tests never read as green, whatever the runner did with the filter.
    assert_eq!(outcome.exit_code, exit::NO_TESTS, "{}", outcome.text);
    assert_mentions(&outcome.text, &["the #method filter matched no method"]);
    // The class-level run of the same class keeps the generic cause: nothing narrowed to a method there.
    let class_level = run(&["AgentThreadIdentityTest"], &fake);
    assert!(
        !class_level.text.contains("#method filter"),
        "{}",
        class_level.text
    );
}

/// A long run reports that it is still running, through the progress sink and never in the answer.
#[test]
fn the_heartbeat_reaches_stderr_as_a_progress_line() {
    let fake = green_run(vec![case("a")]);
    let outcome = run(&["AgentThreadIdentityTest"], &fake);
    assert!(
        fake.state()
            .stderr
            .iter()
            .any(|line| line == "  … still running, 30.0s elapsed"),
        "{:?}",
        fake.state().stderr
    );
    assert!(!outcome.text.contains("still running"), "{}", outcome.text);
}
