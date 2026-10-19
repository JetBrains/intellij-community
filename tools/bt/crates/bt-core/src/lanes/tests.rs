use std::collections::BTreeSet;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;
use crate::fake::{areas, lanes};
use crate::runtime::Platform;
use crate::selector::Resolution;

fn lane(name: &str) -> &'static LaneSpec {
    lanes().get(name).unwrap_or_else(|| panic!("no lane {name}"))
}

fn base_plan(resolution: Resolution) -> RunPlan {
    RunPlan {
        resolution,
        shards: 6,
        bep_path: "/tmp/bep.json".to_owned(),
        owns_bep_file: true,
        ..RunPlan::default()
    }
}

fn labelled(label: &str) -> Resolution {
    Resolution {
        labels: vec![label.to_owned()],
        ..Resolution::default()
    }
}

fn has(args: &[String], wanted: &str) -> bool {
    args.iter().any(|argument| argument == wanted)
}

#[test]
fn the_repo_streamed_and_detailed_defaults_are_always_overridden() {
    let args = build_bazel_args(&base_plan(labelled("//p:t")));
    assert_eq!(args[0], "test");
    // `--test_output=streamed` is the repository default and it silently disables local sharding, so a run that
    // did not override it would ignore every shard count below. The sharding-support check is off, because the
    // clippy runner of a vm-lane crate never touches the shard status file.
    for expected in [
        "--test_output=summary",
        "--test_summary=terse",
        "--noincompatible_check_sharding_support",
        "--noshow_progress",
        "--curses=no",
        "--color=no",
        "--show_result=0",
        "--build_event_json_file=/tmp/bep.json",
    ] {
        assert!(has(&args, expected), "{expected} is missing from {args:?}");
    }
}

#[test]
fn a_single_class_runs_unsharded_and_everything_else_is_forced() {
    let single = build_bazel_args(&base_plan(Resolution {
        filter: Some("a.b.C".to_owned()),
        ..labelled("//p:t")
    }));
    assert!(
        has(&single, "--test_sharding_strategy=explicit") && has(&single, "--test_filter=a.b.C"),
        "{single:?}"
    );
    let whole = build_bazel_args(&base_plan(labelled("//p:t")));
    assert!(has(&whole, "--test_sharding_strategy=forced=6"), "{whole:?}");
    assert!(!whole.iter().any(|argument| argument.starts_with("--test_filter")), "{whole:?}");
}

/// Each shard is another test JVM and another IDE, and the flow lanes rely on one shared instance.
#[test]
fn an_ide_launching_lane_pins_one_shard() {
    for name in ["ui", "ui-real", "ui-live", "ui-perf", "jcp", "gui-chat"] {
        let spec = lane(name);
        assert_eq!(spec.shards, Some(1), "lane {name}");
        let resolution = Resolution {
            multi_target: spec.is_multi_target(),
            ..labelled(&spec.targets[0])
        };
        assert_eq!(default_shards(&resolution, Some(spec)), 1, "lane {name}");
    }
}

#[test]
fn a_lane_without_its_own_count_keeps_the_throughput_default() {
    let fast = lane("fast");
    assert_eq!(fast.shards, None);
    let broad = Resolution {
        multi_target: true,
        ..labelled(&fast.targets[0])
    };
    assert_eq!(default_shards(&broad, Some(fast)), 2);
    assert_eq!(default_shards(&labelled("//p:t"), None), 6);
}

#[test]
fn an_explicit_shard_count_is_honoured() {
    let plan = RunPlan {
        shards: 12,
        ..base_plan(labelled("//p:t"))
    };
    assert!(has(&build_bazel_args(&plan), "--test_sharding_strategy=forced=12"));
}

#[test]
fn a_package_runs_through_the_junit_filter_environment_variable() {
    let args = build_bazel_args(&base_plan(Resolution {
        include_package: Some("com.intellij.air.threads".to_owned()),
        ..labelled("//p:t")
    }));
    assert!(
        has(&args, "--test_env=JB_TEST_JUNIT5_FILTERS=include-package=com.intellij.air.threads"),
        "{args:?}"
    );
}

/// One failing target must not hide the rest when the run covers many; on a single target `-k` would only delay
/// the report.
#[test]
fn keep_going_is_added_only_for_a_multi_target_run() {
    let broad = Resolution {
        multi_target: true,
        ..labelled("//p/...")
    };
    assert!(has(&build_bazel_args(&base_plan(broad)), "-k"));
    assert!(!has(&build_bazel_args(&base_plan(labelled("//p:t"))), "-k"));
}

#[test]
fn cache_suppression_is_opt_in() {
    let plan = base_plan(labelled("//p:t"));
    assert!(!has(&build_bazel_args(&plan), "--cache_test_results=no"));
    let no_cache = RunPlan { no_cache: true, ..plan };
    assert!(has(&build_bazel_args(&no_cache), "--cache_test_results=no"));
}

/// Caller args land last so they win over anything the wrapper chose, and the target has to precede them.
#[test]
fn the_target_precedes_caller_passthrough() {
    let plan = RunPlan {
        passthrough: vec!["--test_arg=--jvm_flag=-Dfoo=bar".to_owned()],
        ..base_plan(labelled("//p:t"))
    };
    let args = build_bazel_args(&plan);
    assert_eq!(args[args.len() - 2..], ["//p:t", "--test_arg=--jvm_flag=-Dfoo=bar"]);
}

/// About forwarding, not about the flags themselves: the lane's own extras are the expectation, so the exclusion
/// value stays spelled once rather than restated here to drift.
#[test]
fn lane_extras_and_test_env_names_are_forwarded() {
    let fast = lane("fast");
    assert!(!fast.extra.is_empty(), "the fast lane carries no extras, so this asserts nothing");
    let plan = RunPlan {
        extra: fast.extra.clone(),
        test_env: vec!["CLAUDE_BIN".to_owned()],
        ..base_plan(Resolution {
            multi_target: true,
            ..labelled(&fast.targets[0])
        })
    };
    let args = build_bazel_args(&plan);
    for expected in fast.extra.iter().map(String::as_str).chain(["--test_env=CLAUDE_BIN"]) {
        assert!(has(&args, expected), "{expected} is missing from {args:?}");
    }
}

#[test]
fn the_acp_lane_forwards_folded_agent_requirements() {
    for expected in [
        "--test_env=ANTHROPIC_API_KEY",
        "--test_env=CLAUDE_BIN",
        "--test_env=JUNIE_API_KEY",
        "--test_env=OPENAI_API_KEY",
    ] {
        assert!(has(&lane("acp").extra, expected), "the acp lane does not forward {expected}");
    }
}

/// `--build_tests_only` drops an excluded target from the build as well, so the validation lane has to compile what
/// it will not run. It names the libraries outright, so the spawn must stay a build of libraries: a wildcard or a
/// `*_test` label here brings back the dev distribution the list exists to avoid.
#[test]
fn the_fast_lane_builds_what_it_excludes_from_the_run() {
    let fast = lane("fast");
    assert!(has(&fast.extra, &lanes().broad_run_exclusions()), "{:?}", fast.extra);
    let args = fast
        .build_only_args(lanes())
        .expect("the fast lane excludes targets from its run and compiles none of them");
    assert_eq!(args[0], "build");
    assert!(args.len() > 1);
    for label in &args[1..] {
        assert!(
            label.starts_with("//") && label.ends_with("_test_lib"),
            "{label} is not a test library"
        );
    }
}

/// Every IDE-launching lane runs a target the fast lane excludes, so the library of a JUnit target must be in the
/// build spawn. A lane test runs through the `lane-run` verb and has no library of its own. `AirIntegrationTagTest`
/// checks the whole list against the BUILD files.
#[test]
fn every_ide_launching_lane_has_its_library_in_the_build_spawn() {
    for name in ["ui", "ui-real", "ui-live", "ui-perf", "jcp", "gui-chat"] {
        let target = &lane(name).targets[0];
        if target.ends_with("_lane_test") {
            continue;
        }
        let library = format!("{target}_lib");
        assert!(
            lanes().excluded_test_libs().contains(&library),
            "lane {name} runs {target}, and the fast lane does not compile {library}"
        );
    }
}

/// A lane that runs its targets needs no second spawn, and paying for one would double its analysis.
#[test]
fn a_lane_that_excludes_nothing_builds_nothing_extra() {
    for name in ["ui", "gui-chat", "ui-real", "all"] {
        assert_eq!(lane(name).build_only_args(lanes()), None, "lane {name}");
    }
}

/// The point of the module-per-lane split: a lane is selected by its own Bazel tag, so no lane narrows a run by
/// JUnit filter any more. A filter reappearing here means a module holds two lanes.
#[test]
fn no_lane_selects_its_tests_by_a_junit_filter() {
    for spec in lanes().iter() {
        for flag in &spec.extra {
            assert!(
                !flag.contains("JB_TEST_JUNIT5_FILTERS"),
                "lane {} narrows itself with {flag}",
                spec.name
            );
        }
    }
}

#[test]
fn each_ide_launching_lane_selects_its_own_integration_tag() {
    // The lane test of a lane is its one target, so no tag filter narrows the run.
    for (name, target) in [
        ("ui", "//plugins/air/tests/integration/ui:ui_lane_test"),
        ("gui-chat", "//plugins/air/tests/integration/gui-chat:gui-chat_lane_test"),
        ("ui-real", "//plugins/air/tests/integration/ui-real:ui-real_lane_test"),
    ] {
        assert_eq!(lane(name).targets, [target], "lane {name}");
        assert!(!lane(name).is_multi_target(), "lane {name}");
    }
    for flag in &lane("ui").extra {
        assert!(
            !flag.ends_with("_BIN"),
            "the ui lane forwards {flag}, which would let it depend on a real CLI"
        );
    }
    // A lane that selects by a tag selects by its own one: the tag and the filter are spelled in two places of
    // the lane table.
    for spec in lanes().iter() {
        if let Some(tag) = &spec.integration_tag
            && spec.is_multi_target()
        {
            assert!(has(&spec.extra, &format!("--test_tag_filters={tag}")), "lane {}", spec.name);
        }
    }
}

/// Codex and Pi are Bazel-declared test runtimes, so no lane may reach a host installation of either.
#[test]
fn no_lane_overrides_a_declared_runtime() {
    for spec in lanes().iter() {
        for variable in ["--test_env=PI_BIN", "--test_env=CODEX_BIN"] {
            assert!(!has(&spec.extra, variable), "the {} lane forwards {variable}", spec.name);
        }
    }
}

/// The property lane selects by tag without being an integration lane: rooted at the whole subtree so the tag, not
/// a directory, finds the targets, and a category of its own, excluded for its dedicated suite, so the CI filter of
/// the integration categories never names it.
#[test]
fn the_property_lane_selects_its_tag_across_the_whole_subtree() {
    let property = lane("property");
    let tag = "air-property";
    let spec = lanes().tag(tag).expect("the table describes air-property");
    assert_eq!(property.targets, ["//plugins/air/..."]);
    assert!(has(&property.extra, &format!("--test_tag_filters={tag}")), "{:?}", property.extra);
    // Nothing about a property target makes JVM bootstrap the wrong thing to amortise, so it keeps the throughput
    // default rather than the IDE lanes' one-shard pin.
    assert_eq!(property.shards, None);
    assert_eq!(spec.axis, TagAxis::Category);
    assert_eq!(spec.excluded_from_broad_runs, Some(BroadRunExclusion::DedicatedSuite));
    assert!(!lanes().iter().any(|spec| spec.integration_tag.as_deref() == Some(tag)));
    // `--lane fast` excludes these targets, so this lane is what covers them.
    assert!(lane("fast").extra.join(" ").contains(&format!("-{tag}")));
}

/// The `ci` categories must stay excluded, because `testTagFilters` on the CI configurations is that same list and
/// lives outside this repository. The `dedicated-suite` categories are excluded for the other reason: a suite of its
/// own runs those targets. A category without a reason is what the lane runs.
#[test]
fn the_fast_lane_excludes_the_ci_and_the_dedicated_suite_categories() {
    let fast = lane("fast");
    let joined = fast.extra.join(" ");
    let excluded: Vec<&str> = lanes().broad_run_excluded_tags().map(String::as_str).collect();
    assert_eq!(excluded, ["air-integration-ui", "air-integration-headless", "air-property"]);
    for tag in excluded {
        assert!(joined.contains(&format!("-{tag}")), "the fast lane does not exclude {tag}");
    }
    for tag in lanes().category_tags().filter(|tag| tag.excluded_from_broad_runs.is_none()) {
        assert!(!joined.contains(&tag.name), "the fast lane excludes {}, which it runs", tag.name);
    }
    for tag in lanes().iter().filter_map(|spec| spec.integration_tag.as_deref()) {
        assert!(!joined.contains(tag), "the fast lane names the lane tag {tag}");
    }
    // One flag, not one per axis: --test_tag_filters is last-wins.
    let filters = fast.extra.iter().filter(|flag| flag.starts_with("--test_tag_filters")).count();
    assert_eq!(filters, 1);
}

/// The local half of what a `dedicated-suite` category promises: excluding a tag from `fast` while no lane selects it
/// would leave the suite unrunnable locally.
#[test]
fn every_dedicated_suite_tag_is_still_covered_by_a_lane() {
    let dedicated: Vec<&str> = lanes()
        .category_tags()
        .filter(|tag| tag.excluded_from_broad_runs == Some(BroadRunExclusion::DedicatedSuite))
        .map(|tag| tag.name.as_str())
        .collect();
    assert_eq!(dedicated, ["air-property"]);
    for tag in dedicated {
        let covered = lanes().iter().filter(|spec| spec.name != "fast").any(|spec| {
            // `all` carries no filters at all, so it runs everything the subtree holds.
            (spec.extra.is_empty() && spec.is_multi_target()) || has(&spec.extra, &format!("--test_tag_filters={tag}"))
        });
        assert!(covered, "{tag} is excluded from fast and selected by no lane");
    }
}

#[test]
fn the_two_tag_axes_are_disjoint_and_every_lane_tag_names_a_real_lane() {
    for spec in lanes().iter() {
        let Some(tag) = &spec.integration_tag else {
            continue;
        };
        let described = lanes().tag(tag).unwrap_or_else(|| panic!("the table does not describe {tag}"));
        assert_eq!(described.axis, TagAxis::Lane, "{tag} is a category, not a lane tag");
        // Every lane is an IDE-launching one, so its target carries the one category CI excludes for a display.
        assert_eq!(described.category.as_deref(), Some("air-integration-ui"), "lane tag {tag}");
        // The controller and the trace planner read the test label instead of a table of their own, and a lane
        // without it is one a VM worker cannot run.
        let label = spec.test_label.as_deref().unwrap_or_default();
        assert!(
            label.starts_with("//plugins/air/tests/integration/") && label.ends_with("_test"),
            "the integration lane {} has no test label: {label:?}",
            spec.name
        );
    }
    for spec in lanes().iter().filter(|spec| spec.integration_tag.is_none()) {
        assert!(
            spec.test_label.is_none(),
            "{} runs no IDE, so no VM worker selects it by label",
            spec.name
        );
    }
    assert_eq!(
        lanes().integration_lane_names(),
        ["ui", "ui-real", "ui-live", "ui-perf", "jcp", "gui-chat"]
    );
}

/// An integration lane without a catalog name is explicit-only: the suite join maps lanes through the catalog, so no
/// flow, suite or changed path reaches it, and it runs only when a caller names it. Its one target is a label, never a
/// pattern, so no wildcard a caller spells reaches it through the lane either. `ui-live` spends billed turns,
/// `ui-perf` takes hours, and `jcp` drives a real preprod account.
#[test]
fn an_integration_lane_without_a_catalog_name_is_explicit_only() {
    let explicit: Vec<&str> = lanes()
        .iter()
        .filter(|spec| spec.integration_tag.is_some() && spec.catalog_lane.is_none())
        .map(|spec| spec.name.as_str())
        .collect();
    assert_eq!(explicit, ["ui-live", "ui-perf", "jcp"]);
    for (name, catalog_lane) in [("ui-live", "UI_LIVE"), ("ui-perf", "UI_PERF"), ("jcp", "JCP")] {
        let spec = lane(name);
        assert!(!spec.is_multi_target(), "{:?}", spec.targets);
        assert_eq!(spec.test_label.as_deref(), Some(spec.targets[0].as_str()));
        assert_eq!(lanes().by_catalog_lane(catalog_lane).map(|spec| spec.name.as_str()), None);
    }
    // The suites of ui-live and ui-perf have documents, so their lanes name the document lane; jcp has none.
    for (suite_lane, name) in [("UI_LIVE", Some("ui-live")), ("UI_PERF", Some("ui-perf")), ("JCP", None)] {
        assert_eq!(
            lanes().by_suite_lane(suite_lane).map(|spec| spec.name.as_str()),
            name,
            "{suite_lane}"
        );
    }
    assert_eq!(lanes().by_suite_lane("UI").map(|spec| spec.name.as_str()), Some("ui"));
}

/// A lane is reached through its catalog lane or explicit-only with a document lane, never both. A document lane
/// belongs to an IDE-launching lane, and no two lanes name one suite lane.
#[test]
fn the_parser_refuses_a_document_lane_that_contradicts_its_lane() {
    let category = r#"{ "name": "air-integration-ui", "axis": "category", "excludedFromBroadRuns": "ci", "description": "x" }"#;
    let tag = |name: &str| format!(r#"{{ "name": "{name}", "axis": "lane", "category": "air-integration-ui", "description": "x" }}"#);
    let refused = |tags: &[&str], lanes: &str| {
        let tags: Vec<String> = tags.iter().map(|name| tag(name)).collect();
        let text = format!(r#"{{ "tags": [{category}, {}], "lanes": [{lanes}] }}"#, tags.join(", "));
        Lanes::parse(&text).expect_err("the table parses, and it must not")
    };
    let perf = |extra: &str| {
        format!(
            r#"{{ "name": "ui-perf", "target": "//perf:perf_test", "extra": [], "integrationTag": "t", {extra}"documentLane": "UI_PERF" }}"#
        )
    };
    assert_eq!(
        refused(&["t"], &perf(r#""catalogLane": "UI_PERF", "#)),
        "the lane ui-perf states catalogLane and documentLane; a lane is reached or explicit-only, never both"
    );
    assert_eq!(
        refused(
            &["t"],
            &format!(
                r#"{}, {{ "name": "perf", "target": "//perf:other_test", "extra": [], "documentLane": "UI_OTHER" }}"#,
                perf("")
            )
        ),
        "the lane perf states the documentLane UI_OTHER and launches no IDE"
    );
    let ui = r#"{ "name": "ui", "target": "//ui:ui_test", "extra": [], "integrationTag": "u", "catalogLane": "UI_PERF" }"#;
    assert_eq!(
        refused(&["t", "u"], &format!("{ui}, {}", perf(""))),
        "two lanes name the suite lane UI_PERF"
    );
}

/// The table is the one description of the tags, so the parser holds its two halves to each other. A table without
/// tags still parses: a lane without an `integrationTag` needs none.
#[test]
fn the_parser_refuses_a_table_whose_lanes_and_tags_disagree() {
    fn refused(tags: &str, lanes: &str) -> String {
        let text = format!(r#"{{ "tags": [{tags}], "lanes": [{lanes}] }}"#);
        Lanes::parse(&text).expect_err("the table parses, and it must not")
    }
    let ui = r#"{ "name": "ui", "target": "//ui:ui_test", "extra": [], "integrationTag": "air-integration-ui-flow" }"#;
    let fast = r#"{ "name": "fast", "target": "//...", "extra": [] }"#;
    let category = r#"{ "name": "air-integration-ui", "axis": "category", "excludedFromBroadRuns": "ci", "description": "x" }"#;
    let unit = r#"{ "name": "air-unit", "axis": "category", "description": "x" }"#;
    let flow = r#"{ "name": "air-integration-ui-flow", "axis": "lane", "category": "air-integration-ui", "description": "x" }"#;
    assert_eq!(
        refused(category, ui),
        "the lane ui selects by air-integration-ui-flow, which the table describes as no lane tag"
    );
    assert_eq!(
        refused(&format!("{category}, {flow}"), fast),
        "the lane tag air-integration-ui-flow selects no lane"
    );
    assert_eq!(
        refused(flow, ui),
        "the lane tag air-integration-ui-flow names the category air-integration-ui, which the table does not describe"
    );
    assert_eq!(
        refused(&format!("{category}, {unit}, {unit}"), fast),
        "the tag air-unit is described twice"
    );
    let self_category = unit.replace(r#""description""#, r#""category": "air-unit", "description""#);
    assert_eq!(refused(&self_category, fast), "the category air-unit names a category of its own");
    let excluded_flow = flow.replace(r#""description""#, r#""excludedFromBroadRuns": "ci", "description""#);
    assert_eq!(
        refused(&format!("{category}, {excluded_flow}"), ui),
        "the lane tag air-integration-ui-flow carries an exclusion; its category air-integration-ui carries it"
    );
    Lanes::parse(&format!(r#"{{ "lanes": [{fast}] }}"#)).expect("a table without tags parses");
    // The Air table, as the fixture copies it: the categories first, in the order of the exclusion flag.
    let described: Vec<&str> = lanes().tags().iter().map(|tag| tag.name.as_str()).collect();
    assert_eq!(
        described,
        [
            "air-unit",
            "air-integration-ui",
            "air-integration-headless",
            "air-property",
            "air-integration-ui-flow",
            "air-integration-gui-chat",
            "air-integration-ui-real",
            "air-integration-ui-live",
            "air-integration-ui-perf",
            "air-integration-jcp",
        ]
    );
}

/// The declared order is what a caller sees in the "Known lanes" refusal, and each name must be one lane.
#[test]
fn lane_names_cover_every_lane_exactly_once() {
    let names: Vec<&str> = lanes().names().collect();
    let unique: BTreeSet<&str> = names.iter().copied().collect();
    assert_eq!(names.len(), unique.len(), "{names:?}");
    assert_eq!(
        names,
        [
            "fast", "property", "ui", "ui-real", "ui-live", "ui-perf", "jcp", "gui-chat", "headless", "all", "claude", "codex", "pi",
            "opencode", "junie", "acp"
        ]
    );
}

/// `bazel.cmd` has no shebang (its first line is the `:<<"::CMDLITERAL"` heredoc trick), so `posix_spawn` rejects
/// it with ENOEXEC and a shell has to interpret it.
#[test]
fn the_shebangless_wrapper_goes_through_a_shell() {
    let args = ["test".to_owned(), "//p:t".to_owned()];
    assert_eq!(
        bazel_command(Path::new("/repo"), Platform::Darwin, &args),
        ["/bin/sh", "/repo/bazel.cmd", "test", "//p:t"]
    );
    assert_eq!(
        bazel_command(Path::new(r"C:\repo"), Platform::Windows, &args[..1]),
        ["cmd.exe", "/c", r"C:\repo\bazel.cmd", "test"]
    );
}

#[test]
fn wildcard_guards_keep_a_broad_run_safe() {
    let labels = |values: &[&str]| values.iter().map(ToString::to_string).collect::<Vec<_>>();
    // The hazard this closes: `//plugins/air/...` without the lane's flags builds every non-test target and
    // launches real IDEs through IDE Starter. The value is the one `--lane fast` carries: a hand-spelled subtree
    // is a broad local run too.
    assert_eq!(
        wildcard_guards(areas(), &labels(&["//plugins/air/shared/core/..."])),
        [
            "--build_tests_only",
            "--test_tag_filters=-air-integration-ui,-air-integration-headless,-air-property"
        ]
    );
    assert!(has(&lane("fast").extra, &lanes().broad_run_exclusions()));
    // A pattern rooted inside the integration tree is asking for exactly those tests.
    for label in [
        "//plugins/air/tests/integration/headless/acp/...",
        "//plugins/air/tests/integration/...",
    ] {
        assert_eq!(wildcard_guards(areas(), &labels(&[label])), Vec::<String>::new(), "{label}");
    }
    let mixed = wildcard_guards(
        areas(),
        &labels(&["//plugins/air/tests/integration/...", "//plugins/air/shared/core/..."]),
    );
    assert!(has(&mixed, "--build_tests_only"), "{mixed:?}");
    // An empty list is not "all integration": guarding it is the safe reading.
    assert!(!wildcard_guards(areas(), &[]).is_empty());
}

/// The guards are the areas' own tags, so a checkout without an area adds none.
#[test]
fn wildcard_guards_come_from_the_areas() {
    let labels = ["//plugins/air/shared/core/...".to_owned()];
    assert_eq!(wildcard_guards(&Areas::default(), &labels), Vec::<String>::new());
}

/// Passthrough args land last and win, so this flag used to redirect the events away from the file the wrapper then
/// parsed, reported as INFRA on a run that had in fact passed.
#[test]
fn bep_override_adopts_the_callers_path() {
    let over = |values: &[&str]| bep_override(&values.iter().map(ToString::to_string).collect::<Vec<_>>());
    assert_eq!(over(&["--build_event_json_file=/tmp/mine.json"]).as_deref(), Some("/tmp/mine.json"));
    assert_eq!(
        over(&["--build_event_json_file", "/tmp/mine.json"]).as_deref(),
        Some("/tmp/mine.json")
    );
    // The last one wins, exactly as bazel resolves a repeated flag.
    assert_eq!(
        over(&["--build_event_json_file=/a", "--build_event_json_file=/b"]).as_deref(),
        Some("/b")
    );
    // Bazel's own disable spelling is not a path.
    assert_eq!(over(&["--build_event_json_file="]), None);
    assert_eq!(over(&["--test_arg=x"]), None);
    assert_eq!(over(&["--build_event_json_filex=/a"]), None);
    // A trailing flag with no value cannot name a file.
    assert_eq!(over(&["--build_event_json_file"]), None);
}

/// The file parses, and the key the Kotlin guard reads is where the generator writes.
#[test]
fn the_lane_table_parses_with_the_paths_the_join_reads() {
    assert_eq!(
        lanes().catalog(),
        Some(Catalog {
            flow_profile_dir: "plugins/air/tests/integration/flow-profiles/resources/flow-profiles",
            flow_text_dir: "plugins/air/docs/flows",
        })
    );
    assert_eq!(lanes().catalog_lane_names(), ["GUI_CHAT", "UI", "UI_REAL"]);
}

/// A harness directory holds itself and every path under it, but not a sibling that shares its name as a prefix. The
/// longer of two directories answers, and a table without the key has no harness directory.
#[test]
fn a_lane_wide_path_holds_its_directory_and_the_longer_directory_answers() {
    let table = Lanes::parse(
        r#"{
          "laneWidePaths": { "tools/vm": [], "tools/vm/crates/leaf": ["ui"] },
          "lanes": [ { "name": "ui", "target": "//ui:ui_test", "extra": [] } ]
        }"#,
    )
    .expect("the table parses");
    let lanes_of = |path: &str| {
        table
            .lane_wide_path_lanes(path)
            .map(|(directory, lanes)| (directory, lanes.to_vec()))
    };
    assert_eq!(lanes_of("tools/vm"), Some(("tools/vm", vec![])));
    assert_eq!(lanes_of("tools/vm/crates/vm/src/lib.rs"), Some(("tools/vm", vec![])));
    assert_eq!(
        lanes_of("tools/vm/crates/leaf/src/lib.rs"),
        Some(("tools/vm/crates/leaf", vec!["ui".to_owned()]))
    );
    assert_eq!(lanes_of("tools/vm2/src/lib.rs"), None);
    assert_eq!(lanes_of("tools/bt/README.md"), None);
    let bare = Lanes::parse(r#"{ "lanes": [ { "name": "ui", "target": "//ui:ui_test", "extra": [] } ] }"#).expect("the table parses");
    assert_eq!(bare.lane_wide_path_lanes("tools/vm/src/lib.rs"), None);
    assert_eq!(bare.lane_wide_paths().count(), 0);
}

/// The fixture table names the controller workspace as the harness of every lane, as the Air table does.
#[test]
fn the_fixture_names_the_controller_workspace_as_a_lane_wide_path() {
    assert_eq!(
        lanes().lane_wide_path_lanes("community/tools/vm/crates/avl-affected/src/affected.rs"),
        Some(("community/tools/vm", &[] as &[String]))
    );
}
