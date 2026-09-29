use std::collections::BTreeSet;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;
use crate::fake::{areas, lanes};
use crate::runtime::Platform;
use crate::selector::Resolution;

fn lane(name: &str) -> &'static LaneSpec {
    lanes()
        .get(name)
        .unwrap_or_else(|| panic!("no lane {name}"))
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
    // did not override it would ignore every shard count below.
    for expected in [
        "--test_output=summary",
        "--test_summary=terse",
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
    assert!(
        has(&whole, "--test_sharding_strategy=forced=6"),
        "{whole:?}"
    );
    assert!(
        !whole
            .iter()
            .any(|argument| argument.starts_with("--test_filter")),
        "{whole:?}"
    );
}

/// Each shard is another test JVM and another IDE, and the flow lanes rely on one shared instance.
#[test]
fn an_ide_launching_lane_pins_one_shard() {
    for name in ["ui", "ui-real", "gui-chat"] {
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
    assert!(has(
        &build_bazel_args(&plan),
        "--test_sharding_strategy=forced=12"
    ));
}

#[test]
fn a_package_runs_through_the_junit_filter_environment_variable() {
    let args = build_bazel_args(&base_plan(Resolution {
        include_package: Some("com.intellij.air.threads".to_owned()),
        ..labelled("//p:t")
    }));
    assert!(
        has(
            &args,
            "--test_env=JB_TEST_JUNIT5_FILTERS=include-package=com.intellij.air.threads"
        ),
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
    let no_cache = RunPlan {
        no_cache: true,
        ..plan
    };
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
    assert_eq!(
        args[args.len() - 2..],
        ["//p:t", "--test_arg=--jvm_flag=-Dfoo=bar"]
    );
}

/// About forwarding, not about the flags themselves: the lane's own extras are the expectation, so the exclusion
/// value stays spelled once rather than restated here to drift.
#[test]
fn lane_extras_and_test_env_names_are_forwarded() {
    let fast = lane("fast");
    assert!(
        !fast.extra.is_empty(),
        "the fast lane carries no extras, so this asserts nothing"
    );
    let plan = RunPlan {
        extra: fast.extra.clone(),
        test_env: vec!["CLAUDE_BIN".to_owned()],
        ..base_plan(Resolution {
            multi_target: true,
            ..labelled(&fast.targets[0])
        })
    };
    let args = build_bazel_args(&plan);
    for expected in fast
        .extra
        .iter()
        .map(String::as_str)
        .chain(["--test_env=CLAUDE_BIN"])
    {
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
        assert!(
            has(&lane("acp").extra, expected),
            "the acp lane does not forward {expected}"
        );
    }
}

/// `--build_tests_only` drops an excluded target from the build as well, so the validation lane has to compile what
/// it will not run. It names the libraries outright, so the spawn must stay a build of libraries: a wildcard or a
/// `*_test` label here brings back the dev distribution the list exists to avoid.
#[test]
fn the_fast_lane_builds_what_it_excludes_from_the_run() {
    let fast = lane("fast");
    assert!(
        has(&fast.extra, &lanes().broad_run_exclusions()),
        "{:?}",
        fast.extra
    );
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

/// Every IDE-launching lane runs a target the fast lane excludes, so that target's library must be in the build
/// spawn. `AirIntegrationTagTest` checks the whole list against the BUILD files.
#[test]
fn every_ide_launching_lane_has_its_library_in_the_build_spawn() {
    for name in ["ui", "ui-real", "gui-chat"] {
        let mut target = lane(name).targets[0].clone();
        if let Some(directory) = target.strip_suffix("/...") {
            // A lane rooted at a package runs the one target named after that package.
            let package = directory
                .rsplit_once('/')
                .map_or(directory, |(_, package)| package);
            target = format!("{directory}:{package}_test");
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
    assert!(has(
        &lane("ui").extra,
        "--test_tag_filters=air-integration-ui-flow"
    ));
    assert!(has(
        &lane("gui-chat").extra,
        "--test_tag_filters=air-integration-gui-chat"
    ));
    assert_eq!(
        lane("ui-real").targets,
        ["//plugins/air/tests/integration/ui-real:ui-real_test"]
    );
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
            assert!(
                has(&spec.extra, &format!("--test_tag_filters={tag}")),
                "lane {}",
                spec.name
            );
        }
    }
}

/// Codex and Pi are Bazel-declared test runtimes, so no lane may reach a host installation of either.
#[test]
fn no_lane_overrides_a_declared_runtime() {
    for spec in lanes().iter() {
        for variable in ["--test_env=PI_BIN", "--test_env=CODEX_BIN"] {
            assert!(
                !has(&spec.extra, variable),
                "the {} lane forwards {variable}",
                spec.name
            );
        }
    }
}

/// The property lane selects by tag without being an integration lane: rooted at the whole subtree so the tag, not
/// a directory, finds the targets, and outside both integration axes so CI never excludes them.
#[test]
fn the_property_lane_selects_its_tag_across_the_whole_subtree() {
    let property = lane("property");
    let tag = lanes()
        .property_tag()
        .expect("the table names a property tag");
    assert_eq!(property.targets, ["//plugins/air/..."]);
    assert!(
        has(&property.extra, &format!("--test_tag_filters={tag}")),
        "{:?}",
        property.extra
    );
    // Nothing about a property target makes JVM bootstrap the wrong thing to amortise, so it keeps the throughput
    // default rather than the IDE lanes' one-shard pin.
    assert_eq!(property.shards, None);
    assert!(!tag.starts_with("air-integration-"));
    assert!(
        !lanes()
            .integration_category_tags()
            .iter()
            .any(|category| category == tag)
    );
    assert!(
        !lanes()
            .iter()
            .any(|spec| spec.integration_tag.as_deref() == Some(tag))
    );
    // `--lane fast` excludes these targets, so this lane is what covers them.
    assert!(lane("fast").extra.join(" ").contains(&format!("-{tag}")));
}

/// The category axis must stay excluded, because `testTagFilters` on the CI configurations is that same list and
/// lives outside this repository. The dedicated-suite tags are excluded for the other reason: a suite of its own
/// runs those targets.
#[test]
fn the_fast_lane_excludes_the_ci_categories_and_the_dedicated_suite_tags() {
    let fast = lane("fast");
    let joined = fast.extra.join(" ");
    for tag in lanes()
        .integration_category_tags()
        .iter()
        .chain(lanes().dedicated_suite_tags())
    {
        assert!(
            joined.contains(&format!("-{tag}")),
            "the fast lane does not exclude {tag}"
        );
    }
    for tag in lanes()
        .iter()
        .filter_map(|spec| spec.integration_tag.as_deref())
    {
        assert!(
            !joined.contains(tag),
            "the fast lane names the lane tag {tag}"
        );
    }
    // One flag, not one per axis: --test_tag_filters is last-wins.
    let filters = fast
        .extra
        .iter()
        .filter(|flag| flag.starts_with("--test_tag_filters"))
        .count();
    assert_eq!(filters, 1);
}

/// The local half of what a dedicated-suite tag promises: excluding a tag from `fast` while no lane selects it would
/// leave the suite unrunnable locally.
#[test]
fn every_dedicated_suite_tag_is_still_covered_by_a_lane() {
    for tag in lanes().dedicated_suite_tags() {
        let covered = lanes()
            .iter()
            .filter(|spec| spec.name != "fast")
            .any(|spec| {
                // `all` carries no filters at all, so it runs everything the subtree holds.
                (spec.extra.is_empty() && spec.is_multi_target())
                    || has(&spec.extra, &format!("--test_tag_filters={tag}"))
            });
        assert!(
            covered,
            "{tag} is excluded from fast and selected by no lane"
        );
    }
}

#[test]
fn the_two_tag_axes_are_disjoint_and_every_lane_tag_names_a_real_lane() {
    for spec in lanes().iter() {
        let Some(tag) = &spec.integration_tag else {
            continue;
        };
        assert!(
            !lanes().integration_category_tags().contains(tag),
            "{tag} is both a lane tag and a category"
        );
        assert!(
            spec.catalog_lane.is_some(),
            "the integration lane {} has no catalog name",
            spec.name
        );
        // The controller and the trace planner read these two instead of a table of their own, and a lane without
        // them is one a VM worker cannot run.
        let label = spec.test_label.as_deref().unwrap_or_default();
        assert!(
            label.starts_with("//plugins/air/tests/integration/") && label.ends_with("_test"),
            "the integration lane {} has no test label: {label:?}",
            spec.name
        );
        assert!(
            spec.junit_tag
                .as_deref()
                .is_some_and(|tag| tag.starts_with("air-")),
            "the integration lane {} has no JUnit tag",
            spec.name
        );
    }
    for spec in lanes().iter().filter(|spec| spec.integration_tag.is_none()) {
        assert!(
            spec.test_label.is_none() && spec.junit_tag.is_none(),
            "{} runs no IDE, so no VM worker selects it by label or JUnit tag",
            spec.name
        );
    }
    assert_eq!(
        lanes().integration_lane_names(),
        ["ui", "ui-real", "gui-chat"]
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
            "fast", "property", "ui", "ui-real", "gui-chat", "headless", "all", "claude", "codex",
            "pi", "junie", "acp"
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
        assert_eq!(
            wildcard_guards(areas(), &labels(&[label])),
            Vec::<String>::new(),
            "{label}"
        );
    }
    let mixed = wildcard_guards(
        areas(),
        &labels(&[
            "//plugins/air/tests/integration/...",
            "//plugins/air/shared/core/...",
        ]),
    );
    assert!(has(&mixed, "--build_tests_only"), "{mixed:?}");
    // An empty list is not "all integration": guarding it is the safe reading.
    assert!(!wildcard_guards(areas(), &[]).is_empty());
}

/// The guards are the areas' own tags, so a checkout without an area adds none.
#[test]
fn wildcard_guards_come_from_the_areas() {
    let labels = ["//plugins/air/shared/core/...".to_owned()];
    assert_eq!(
        wildcard_guards(&Areas::default(), &labels),
        Vec::<String>::new()
    );
}

/// Passthrough args land last and win, so this flag used to redirect the events away from the file the wrapper then
/// parsed, reported as INFRA on a run that had in fact passed.
#[test]
fn bep_override_adopts_the_callers_path() {
    let over =
        |values: &[&str]| bep_override(&values.iter().map(ToString::to_string).collect::<Vec<_>>());
    assert_eq!(
        over(&["--build_event_json_file=/tmp/mine.json"]).as_deref(),
        Some("/tmp/mine.json")
    );
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
            authored_suites_file: "plugins/air/tests/integration/flow-profiles/resources/authored-suites.json",
        })
    );
    assert_eq!(lanes().catalog_lane_names(), ["GUI_CHAT", "UI", "UI_REAL"]);
}
