use pretty_assertions::assert_eq;

use super::*;
use crate::exit;

use crate::accio::ACCIO_FAILED;
use crate::areas::{Area, Areas};
use crate::fake::{
    AREA_DIR, FakeRuntime, LANES_FILE, LANES_TEXT, REPO_ROOT, air_tree, areas, document_path, fake_air_tree, jps_json, module_entry_json,
    refusal, target_json, tests_module_json,
};
use crate::lanes::Lanes;
use crate::modules::{MODULE_NOT_MIGRATED, MODULE_WITHOUT_TEST_TARGET};

fn classify(raw: &str) -> Selector {
    Selector::classify(raw).unwrap_or_else(|failure| panic!("{raw} refused: {failure}"))
}

#[test]
fn classify_selector_reads_each_kind() {
    for (raw, kind) in [
        ("//plugins/air/shared/core:ai-agent-core-tests_test", SelectorKind::Label),
        ("//plugins/air/...", SelectorKind::Pattern),
        ("//plugins/air/shared/*:all", SelectorKind::Pattern),
        // A label of another repository, as the hint of a module outside the migrated list prints it.
        ("@community//platform/build-scripts/tests:tests_test", SelectorKind::Label),
        ("@@community//tools/bt:bt-core_test", SelectorKind::Label),
        ("@//plugins/air/shared/core:ai-agent-core-tests_test", SelectorKind::Label),
        ("@community//platform/...", SelectorKind::Pattern),
        ("AgentThreadCliTest", SelectorKind::SimpleName),
        ("com.intellij.air.threads.AgentThreadCliTest", SelectorKind::Fqn),
        ("plugins/air/backend/vcs", SelectorKind::Dir),
        ("plugins/air/backend/vcs/...", SelectorKind::Dir),
        ("/abs/path/plugins/air", SelectorKind::Dir),
        // What a Windows shell completes a path to. No other kind of selector holds a backslash.
        (r"plugins\air\backend\vcs", SelectorKind::Dir),
        (r"C:\repo\plugins\air", SelectorKind::Dir),
    ] {
        assert_eq!(classify(raw).kind, kind, "{raw}");
    }
}

#[test]
fn a_method_is_split_off_a_simple_name() {
    let selector = classify("AgentThreadIdentityTest#buildsAndParsesValidIdentity");
    assert_eq!(
        selector,
        Selector {
            kind: SelectorKind::SimpleName,
            name: "AgentThreadIdentityTest".to_owned(),
            method: Some("buildsAndParsesValidIdentity".to_owned()),
        }
    );
    // Kotlin backticked test names carry spaces, and a selector has to be able to name one.
    assert_eq!(
        classify("FooTest#resolves cwd from worktree").method.as_deref(),
        Some("resolves cwd from worktree")
    );
}

#[test]
fn selector_method_split_preserves_names_and_label_precedence() {
    for (raw, kind, name, method) in [
        (
            "FooTest#keeps [a,b] (value)",
            SelectorKind::SimpleName,
            "FooTest",
            Some("keeps [a,b] (value)"),
        ),
        ("a.b.FooTest#checks_value", SelectorKind::Fqn, "a.b.FooTest", Some("checks_value")),
        ("FooTest", SelectorKind::SimpleName, "FooTest", None),
        ("//pkg:target#method", SelectorKind::Label, "//pkg:target#method", None),
        ("//pkg/*#method", SelectorKind::Pattern, "//pkg/*#method", None),
    ] {
        let selector = classify(raw);
        assert_eq!(
            (selector.kind, selector.name.as_str(), selector.method.as_deref()),
            (kind, name, method),
            "{raw}"
        );
    }
}

#[test]
fn selector_method_split_rejects_empty_and_repeated_separators() {
    for raw in [
        "FooTest#",
        "FooTest##method",
        "FooTest#method#other",
        "#method",
        "a.b#method",
        r"a\b#method",
    ] {
        assert_eq!(refusal(Selector::classify(raw)).exit, exit::USAGE, "{raw}");
    }
}

#[test]
fn a_selector_that_cannot_carry_a_method_refuses_one() {
    let failure = refusal(Selector::classify("plugins/air/backend/vcs#foo"));
    assert!(failure.message.contains("cannot carry a #method"), "{}", failure.message);
}

/// A package and a JPS module name are both dotted lower-case words. bt takes neither as a selector, and the refusal
/// gives the two forms that run them.
#[test]
fn a_package_or_a_module_name_is_refused_with_the_module_hints() {
    for raw in [
        "com.intellij.air.shared.core",
        "intellij.air.plugin.tests",
        "util",
        "com.intellij.air.threads#foo",
    ] {
        let failure = refusal(Selector::classify(raw));
        assert_eq!(failure.exit, exit::USAGE, "{raw}");
        let head = raw.split('#').next().unwrap_or(raw);
        assert_eq!(
            failure.message,
            format!(
                "Cannot interpret selector: {raw}\nbt takes no package selector. For a JPS module, pass --module {head}. \
                 For a package, pass --module <module> --filter {head}"
            ),
            "{raw}"
        );
    }
}

#[test]
fn an_uninterpretable_selector_is_a_usage_refusal() {
    assert_eq!(refusal("not a selector".parse::<Selector>()).exit, exit::USAGE);
}

#[test]
fn as_filter_or_package_routes_by_what_the_field_can_express() {
    let class = "com.intellij.air.threads.AgentThreadCliTest";
    assert_eq!(as_filter_or_package(Some(class)), (Some(class.to_owned()), None));
    let method = "com.intellij.air.threads.AgentThreadCliTest#resolvesCwd";
    assert_eq!(as_filter_or_package(Some(method)), (Some(method.to_owned()), None));
    // An all-lowercase dotted value cannot be a class, and `--test_filter` would match nothing, which reads as "the
    // filter matched no test class" rather than as a package run.
    let package = "com.intellij.air.threads";
    assert_eq!(as_filter_or_package(Some(package)), (None, Some(package.to_owned())));
    assert_eq!(as_filter_or_package(None), (None, None));
}

fn hint_of(filter: &str) -> String {
    no_tests_hint(Some(filter)).unwrap_or_else(|| panic!("no hint for {filter}"))
}

/// A NO TESTS run with a filter in the form of the runner: the hint names what the filter selects.
#[test]
fn a_no_tests_hint_names_what_the_filter_selects() {
    assert!(
        hint_of("FooTest#testBar").starts_with("a Class#method needs the FQN of the class"),
        "{}",
        hint_of("FooTest#testBar")
    );
    // A package segment of a Spring test starts with an upper-case letter and has no lower-case letter.
    assert!(hint_of("a.IDEA_355419.FooTest").starts_with("a class filter is a prefix"));
    assert!(hint_of("a.b.FooTest").contains("package line of the file"));
    assert!(hint_of("a.b").starts_with("a package filter runs the classes of that package and its subpackages"));
    assert_eq!(no_tests_hint(None), None);
}

/// The cause line of the digest names the `#method` part of an FQN `Class#method`. The runner selects
/// `a.Outer$Inner#method` as a method, so that form runs a nested class. A hint about the class would contradict both.
#[test]
fn an_fqn_method_filter_gets_no_hint() {
    for filter in [
        "a.b.FooTest#testBar",
        "a.b.FooTest#testBar(java.lang.String)",
        "a.b.Outer$Inner#test",
    ] {
        assert_eq!(no_tests_hint(Some(filter)), None, "{filter}");
    }
}

#[test]
fn suggest_names_offers_only_near_names_closest_first() {
    let known = ["AgentThreadCliTest", "AgentThreadIdentityTest", "ZzzTest"];
    assert_eq!(suggest_names("AgentThreadCliTst", known, 3), ["AgentThreadCliTest"]);
    // A name nothing resembles suggests nothing rather than the least-bad match.
    assert_eq!(
        suggest_names("CompletelyUnrelated", ["AgentThreadCliTest"], 3),
        Vec::<String>::new()
    );
    // The threshold is max(2, len/4), the comparison ignores case, and ties go by name.
    assert_eq!(suggest_names("abc", ["abd", "abe", "xyz", "ABC"], 3), ["ABC", "abd", "abe"]);
    assert_eq!(suggest_names("abc", ["abd", "abe"], 1), ["abd"]);
}

fn resolve_in(fake: &FakeRuntime, raw: &str) -> Result<Resolution, Refusal> {
    let selector = Selector::classify(raw)?;
    resolve_selector(fake, &selector, &ResolutionInputs::new(fake, areas()))
}

fn resolve_tree(raw: &str) -> Result<Resolution, Refusal> {
    resolve_in(&fake_air_tree(), raw)
}

/// The arguments of the `symbol` call for one name.
fn symbol_call(name: &str) -> String {
    format!("symbol {name} --kind class --limit 200")
}

/// One class name costs one `symbol` call and one `modules` call for the files of its hits.
#[test]
fn a_unique_simple_name_resolves_to_label_plus_fqn() {
    let fake = fake_air_tree();
    assert_eq!(
        resolve_in(&fake, "AgentThreadIdentityTest"),
        Ok(Resolution {
            labels: vec!["//plugins/air/shared/core:ai-agent-core-tests_test".to_owned()],
            filter: Some("com.intellij.air.shared.core.AgentThreadIdentityTest".to_owned()),
            ..Resolution::default()
        })
    );
    assert_eq!(
        fake.accio_calls(),
        [
            symbol_call("AgentThreadIdentityTest"),
            "modules --paths plugins/air/shared/core/testSrc/AgentThreadIdentityTest.kt".to_owned(),
        ]
    );
}

#[test]
fn a_method_is_appended_to_the_resolved_fqn() {
    let resolution = resolve_tree("AgentThreadIdentityTest#buildsAndParsesValidIdentity").expect("resolves");
    assert_eq!(
        resolution.filter.as_deref(),
        Some("com.intellij.air.shared.core.AgentThreadIdentityTest#buildsAndParsesValidIdentity")
    );
}

#[test]
fn an_ambiguous_simple_name_names_every_candidate() {
    let failure = refusal(resolve_tree("AgentPromptChangesTreeContextContributorTest"));
    assert_eq!(failure.exit, exit::USAGE);
    for expected in [
        "2 classes named",
        "com.intellij.air.backend.vcs.context",
        "//plugins/air/backend/vcs:air-backend-vcs-tests_test",
        "com.intellij.air.frontend.prompt.vcs.context",
        "//plugins/air/frontend/prompt/vcs:air-frontend-prompt-vcs-tests_test",
    ] {
        assert!(failure.message.contains(expected), "no {expected:?} in:\n{}", failure.message);
    }
}

/// The FQN is the query of accio, so it names one class.
#[test]
fn the_fully_qualified_form_disambiguates_a_colliding_name() {
    let fake = fake_air_tree();
    let fqn = "com.intellij.air.frontend.prompt.vcs.context.AgentPromptChangesTreeContextContributorTest";
    let resolution = resolve_in(&fake, fqn).expect("resolves");
    assert_eq!(
        resolution.labels,
        ["//plugins/air/frontend/prompt/vcs:air-frontend-prompt-vcs-tests_test"]
    );
    assert_eq!(fake.accio_calls()[0], symbol_call(fqn));
}

/// A name without a class passes on the note of accio, which holds its `did you mean` text.
#[test]
fn an_unknown_name_passes_on_the_note_of_accio() {
    let fake = fake_air_tree();
    fake.answer_accio(
        &symbol_call("AgentThreadCliTst"),
        Ok(r#"[{"complete": true, "hits": [], "match": "none", "note": "did you mean AgentThreadCliTest"}]"#.to_owned()),
    );
    let failure = refusal(resolve_in(&fake, "AgentThreadCliTst"));
    assert_eq!(
        (failure.exit, failure.message.as_str()),
        (
            exit::USAGE,
            "No test class named AgentThreadCliTst\n  accio: did you mean AgentThreadCliTest"
        )
    );
}

/// A name with more classes than the limit is refused, never cut to the first ones.
#[test]
fn a_name_that_accio_cuts_is_refused_as_too_many_classes() {
    let fake = fake_air_tree();
    fake.state().accio.incomplete.insert("AgentThreadCliTest".to_owned());
    let failure = refusal(resolve_in(&fake, "AgentThreadCliTest"));
    assert_eq!(
        (failure.exit, failure.message.as_str()),
        (
            exit::USAGE,
            "accio finds more than 200 classes named AgentThreadCliTest; pass the fully-qualified name"
        )
    );
}

/// A class of a production root is no test class, and the refusal says where accio found it.
#[test]
fn a_class_outside_every_test_source_root_is_no_test_class() {
    let failure = refusal(resolve_tree("Api"));
    assert_eq!(
        failure.message,
        "No test class named Api\n  accio finds it only outside a test source root: plugins/air/shared/api/src/Api.kt"
    );
}

/// A class in a module without a test target gets the refusal of `--module` with the class filled in.
#[test]
fn a_class_of_a_module_without_a_test_target_is_refused_with_the_tests_cmd_command() {
    let failure = refusal(resolve_tree("OrphanTest"));
    assert_eq!((failure.code.as_ref(), failure.exit), (MODULE_WITHOUT_TEST_TARGET, exit::USAGE));
    assert_eq!(
        failure.message,
        "module intellij.air.notest.tests of com.intellij.air.notest.OrphanTest has no test target in \
         plugins/air/notest/BUILD.bazel. Run: ./tests.cmd --module intellij.air.notest.tests --test \
         com.intellij.air.notest.OrphanTest"
    );
}

/// A class outside every area runs only when the migrated list names its module, as `--module` does.
#[test]
fn a_class_of_a_module_outside_the_migrated_list_is_refused_with_the_tests_cmd_command() {
    let fake = fake_air_tree();
    fake.put("community/MODULE.bazel", "");
    fake.put("community/build/bazel-migrated-test-modules.txt", "intellij.x.tests\n");
    fake.add_class("goland/tests/testSrc/GoTest.kt", "com.goide.GoTest");
    fake.add_module(tests_module_json(
        "goland/tests",
        "intellij.goland",
        "intellij.goland.tests",
        "go-tests_test",
    ));
    let failure = refusal(resolve_in(&fake, "GoTest"));
    assert_eq!((failure.code.as_ref(), failure.exit), (MODULE_NOT_MIGRATED, exit::USAGE));
    assert_eq!(
        failure.message,
        "the tests of module intellij.goland.tests do not run under Bazel yet \
         (community/build/bazel-migrated-test-modules.txt). Run: ./tests.cmd --module intellij.goland.tests --test \
         com.goide.GoTest. The label //goland/tests:go-tests_test still runs on its own: ./community/tools/bt.cmd \
         //goland/tests:go-tests_test --filter com.goide.GoTest"
    );
    fake.put("community/build/bazel-migrated-test-modules.txt", "intellij.goland.*\n");
    assert_eq!(
        resolve_in(&fake, "GoTest").map(|resolution| resolution.labels),
        Ok(vec!["//goland/tests:go-tests_test".to_owned()])
    );
}

/// `community/platform/util` holds three `.iml` files. The class belongs to the module whose test root holds its
/// file, here a module of `jps_more`, and its target is the one that runs that module.
#[test]
fn a_class_of_a_root_with_several_iml_files_resolves_to_the_target_of_its_module() {
    let root = "community/platform/util";
    let fake = fake_air_tree();
    fake.put("community/MODULE.bazel", "");
    fake.put("community/build/bazel-migrated-test-modules.txt", "intellij.platform.util.tests\n");
    fake.add_class(
        "community/platform/util/testSrc/com/intellij/openapi/util/io/FileUtilLightTest.java",
        "com.intellij.openapi.util.io.FileUtilLightTest",
    );
    fake.add_module(module_entry_json(
        root,
        Some(&jps_json("intellij.platform.util", root, &[("src", false)], None)),
        &[
            &jps_json("intellij.platform.util.tests", root, &[("testSrc", true)], None),
            &jps_json("intellij.platform.util.ui", root, &[("ui/src", false)], None),
        ],
        Some(&[
            target_json(root, "jps_test", "util-tests_test", None, &[], &["util-tests_test_lib"]),
            target_json(root, "jvm_library", "util", Some("intellij.platform.util"), &[], &[]),
            target_json(
                root,
                "jvm_library",
                "util-tests_test_lib",
                Some("intellij.platform.util.tests"),
                &[],
                &[],
            ),
        ]),
    ));
    assert_eq!(
        resolve_in(&fake, "FileUtilLightTest"),
        Ok(Resolution {
            labels: vec!["@community//platform/util:util-tests_test".to_owned()],
            filter: Some("com.intellij.openapi.util.io.FileUtilLightTest".to_owned()),
            ..Resolution::default()
        })
    );
}

/// `platform/daemon/lambdaTests` has no test module: the main module owns the test root, and a `starter_test` runs
/// the `_test_lib` of the main module.
#[test]
fn a_class_of_a_main_module_with_a_test_root_resolves_to_its_starter_test() {
    let root = "plugins/air/lambdaTests";
    let fake = fake_air_tree();
    fake.add_class("plugins/air/lambdaTests/tests/LambdaTest.kt", "com.intellij.lambda.LambdaTest");
    fake.add_module(module_entry_json(
        root,
        Some(&jps_json("intellij.air.lambdaTests", root, &[("tests", true)], None)),
        &[],
        Some(&[
            target_json(
                root,
                "jvm_library",
                "lambdaTests_test_lib",
                Some("intellij.air.lambdaTests"),
                &[],
                &[],
            ),
            target_json(root, "starter_test", "lambdaTests_test", None, &[], &["lambdaTests_test_lib"]),
        ]),
    ));
    assert_eq!(
        resolve_in(&fake, "LambdaTest").map(|resolution| resolution.labels),
        Ok(vec!["//plugins/air/lambdaTests:lambdaTests_test".to_owned()])
    );
}

/// A target that pins `JB_TEST_JUNIT5_FILTERS` runs a named subset of the same `_test_lib`, so the unfiltered target
/// is the answer for every class of the module.
#[test]
fn the_unfiltered_target_wins_over_a_filtered_one_of_the_same_test_library() {
    let root = "plugins/air/frontend/prompt/ui";
    let tests = jps_json("intellij.air.frontend.prompt.ui.tests", root, &[("testSrc", true)], None);
    let fake = fake_air_tree();
    fake.add_class(
        "plugins/air/frontend/prompt/ui/testSrc/BudgetTest.kt",
        "com.intellij.air.ui.BudgetTest",
    );
    fake.add_module(module_entry_json(
        root,
        Some(&jps_json("intellij.air.frontend.prompt.ui", root, &[("src", false)], Some(&tests))),
        &[],
        Some(&[
            target_json(
                root,
                "jps_test",
                "ui-budget_test",
                None,
                &["JB_TEST_JUNIT5_FILTERS"],
                &["ui-tests_test_lib"],
            ),
            target_json(root, "jps_test", "ui-tests_test", None, &[], &["ui-tests_test_lib"]),
            target_json(
                root,
                "jvm_library",
                "ui-tests_test_lib",
                Some("intellij.air.frontend.prompt.ui.tests"),
                &[],
                &[],
            ),
        ]),
    ));
    assert_eq!(
        resolve_in(&fake, "BudgetTest").map(|resolution| resolution.labels),
        Ok(vec!["//plugins/air/frontend/prompt/ui:ui-tests_test".to_owned()])
    );
}

/// The runner finds an `@Nested` class only through its outer class, so the filter of a nested class without a method
/// is its outer class, and a note says so. With a method, the filter keeps the `$` form, which the runner selects.
#[test]
fn a_nested_class_runs_through_its_outer_class() {
    let fake = fake_air_tree();
    fake.add_class(
        "plugins/air/shared/core/testSrc/AgentIdTest.kt",
        "com.intellij.air.shared.core.AgentIdTest$Parsing",
    );
    let resolution = resolve_in(&fake, "Parsing").expect("resolves");
    assert_eq!(resolution.filter.as_deref(), Some("com.intellij.air.shared.core.AgentIdTest"));
    assert_eq!(
        resolution.notes,
        [
            "com.intellij.air.shared.core.AgentIdTest$Parsing is a nested class, so the filter is its outer class \
          com.intellij.air.shared.core.AgentIdTest: the runner finds an @Nested class only through its outer class"
        ]
    );
    let method = resolve_in(&fake, "Parsing#readsTheId").expect("resolves");
    assert_eq!(
        method.filter.as_deref(),
        Some("com.intellij.air.shared.core.AgentIdTest$Parsing#readsTheId")
    );
    assert_eq!(method.notes, Vec::<String>::new());
}

/// A secondary class of a file resolves by its own name: accio indexes declarations, not file names.
#[test]
fn a_class_whose_filename_differs_is_still_found() {
    let fake = fake_air_tree();
    fake.add_class(
        "plugins/air/shared/core/testSrc/Fixtures.kt",
        "com.intellij.air.shared.core.SecondaryTest",
    );
    let resolution = resolve_in(&fake, "SecondaryTest").expect("resolves");
    assert_eq!(resolution.filter.as_deref(), Some("com.intellij.air.shared.core.SecondaryTest"));
}

/// A failed call is exit 6 with the words of accio, and an accio without `fqn` is refused the same way.
#[test]
fn a_failed_accio_call_is_infrastructure() {
    let fake = fake_air_tree();
    fake.answer_accio(
        &symbol_call("AgentThreadCliTest"),
        Err(accio_failed("accio exited with 1:\nno index")),
    );
    let failure = refusal(resolve_in(&fake, "AgentThreadCliTest"));
    assert_eq!((failure.code.as_ref(), failure.exit), (ACCIO_FAILED, exit::INFRA));

    let old = fake_air_tree();
    old.answer_accio(
        &symbol_call("AgentThreadCliTest"),
        Ok(r#"[{"complete": true, "hits": [{"file": "a/A.kt", "line": 1, "kind": "class", "language": "kotlin", "name": "AgentThreadCliTest"}]}]"#.to_owned()),
    );
    let failure = refusal(resolve_in(&old, "AgentThreadCliTest"));
    assert_eq!((failure.code.as_ref(), failure.exit), (ACCIO_FAILED, exit::INFRA));
    assert!(failure.message.contains("names no fqn"), "{}", failure.message);
}

/// A class of the same name in another language is no candidate, and it needs no `fqn`: only a Kotlin or a Java hit
/// can be a JUnit class.
#[test]
fn a_class_of_another_language_is_no_candidate() {
    let fake = fake_air_tree();
    fake.answer_accio(
        &symbol_call("AgentThreadCliTest"),
        Ok(r#"[{"complete": true, "hits": [
            {"file": "python/testData/agent_thread_cli_test.py", "line": 1, "kind": "class", "language": "python", "name": "AgentThreadCliTest"},
            {"file": "plugins/air/shared/core/testSrc/AgentThreadCliTest.kt", "line": 3, "kind": "class", "language": "kotlin", "name": "AgentThreadCliTest", "fqn": "com.intellij.air.shared.core.AgentThreadCliTest"}
        ]}]"#
            .to_owned()),
    );
    let resolution = resolve_in(&fake, "AgentThreadCliTest").unwrap_or_else(|failure| panic!("{failure}"));
    assert_eq!(
        resolution.filter.as_deref(),
        Some("com.intellij.air.shared.core.AgentThreadCliTest")
    );
}

/// A planner prefetches its names in one call. A name that the batch answered makes no second call, and a failed
/// batch is the refusal of each of its names, also without a second call.
#[test]
fn a_prefetch_asks_once_for_every_name_and_keeps_a_refusal() {
    let fake = fake_air_tree();
    let inputs = ResolutionInputs::new(&fake, areas());
    inputs.prefetch_symbols(&["AgentThreadCliTest", "AgentThreadIdentityTest", "AgentThreadCliTest"]);
    for name in ["AgentThreadCliTest", "AgentThreadIdentityTest"] {
        resolve_selector(&fake, &classify(name), &inputs).unwrap_or_else(|failure| panic!("{name}: {failure}"));
    }
    let symbol_calls: Vec<String> = fake.accio_calls().into_iter().filter(|call| call.starts_with("symbol")).collect();
    assert_eq!(
        symbol_calls,
        ["symbol AgentThreadCliTest AgentThreadIdentityTest --kind class --limit 200"]
    );
    // A prefetch of known names asks nothing.
    inputs.prefetch_symbols(&["AgentThreadCliTest"]);
    assert_eq!(fake.accio_calls().iter().filter(|call| call.starts_with("symbol")).count(), 1);

    let failing = fake_air_tree();
    failing.answer_accio(
        "symbol OneTest TwoTest --kind class --limit 200",
        Err(accio_failed("accio exited with 1")),
    );
    let inputs = ResolutionInputs::new(&failing, areas());
    inputs.prefetch_symbols(&["OneTest", "TwoTest"]);
    for name in ["OneTest", "TwoTest"] {
        let failure = refusal(resolve_selector(&failing, &classify(name), &inputs));
        assert_eq!(failure.code.as_ref(), ACCIO_FAILED, "{name}");
    }
    assert_eq!(failing.accio_calls(), ["symbol OneTest TwoTest --kind class --limit 200"]);
}

#[test]
fn a_label_and_a_pattern_bypass_resolution() {
    assert_eq!(
        resolve_tree("//other/pkg:some_test"),
        Ok(Resolution {
            labels: vec!["//other/pkg:some_test".to_owned()],
            ..Resolution::default()
        })
    );
    assert!(resolve_tree("//plugins/air/...").expect("resolves").multi_target);
}

/// A label names its target already, so it costs no read and no accio call. A runtime that refuses all I/O is the
/// only durable guard.
#[test]
fn a_label_and_a_pattern_touch_no_filesystem() {
    let fake = fake_air_tree();
    fake.forbid_file_system();
    for raw in ["//other/pkg:some_test", "//plugins/air/..."] {
        resolve_in(&fake, raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
    }
    assert_eq!(fake.reads(), Vec::<String>::new());
    assert_eq!(fake.accio_calls(), Vec::<String>::new());
}

#[test]
fn a_directory_becomes_a_recursive_pattern() {
    let absolute = format!("{REPO_ROOT}/plugins/air/shared/core");
    for raw in [
        "plugins/air/shared/core",
        "plugins/air/shared/core/",
        "plugins/air/shared/core/...",
        "./plugins/air/shared/core",
        absolute.as_str(),
    ] {
        let resolution = resolve_tree(raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
        assert_eq!(resolution.labels, ["//plugins/air/shared/core/..."], "{raw}");
        assert!(resolution.multi_target, "{raw}");
    }
}

/// The ultimate root ignores `community/`, so `//community/tools/vm/...` names no package there. The directory is a
/// package of the `@community` repository.
#[test]
fn a_directory_under_community_of_the_ultimate_root_is_a_pattern_of_the_community_repository() {
    let ultimate = || {
        let fake = fake_air_tree();
        fake.put("community/MODULE.bazel", "");
        fake.put("community/tools/vm/BUILD.bazel", "");
        fake
    };
    for (raw, pattern) in [
        ("community/tools/vm", "@community//tools/vm/..."),
        ("community/tools/vm/...", "@community//tools/vm/..."),
        // Without the slash, the name reads as a package.
        ("community/", "@community//..."),
        ("plugins/air/shared/core", "//plugins/air/shared/core/..."),
    ] {
        let resolution = resolve_in(&ultimate(), raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
        assert_eq!(resolution.labels, [pattern], "{raw}");
        assert!(resolution.multi_target, "{raw}");
    }

    // A community checkout is its own root, so a directory named community there is a package of it.
    let community = fake_air_tree();
    community.put("community/tools/vm/BUILD.bazel", "");
    let resolution = resolve_in(&community, "community/tools/vm").unwrap_or_else(|failure| panic!("{failure}"));
    assert_eq!(resolution.labels, ["//community/tools/vm/..."]);
}

/// Bazel only warns about `//community/tools/vm/...` in the ultimate root, and it builds nothing. So a label or a
/// pattern under `community/` runs in the `@community` repository, as a directory selector does.
#[test]
fn a_label_under_community_of_the_ultimate_root_is_a_label_of_the_community_repository() {
    let ultimate = fake_air_tree();
    ultimate.put("community/MODULE.bazel", "");
    for (raw, label) in [
        ("//community/tools/vm/...", "@community//tools/vm/..."),
        ("//community/tools/bt:bt-core_test", "@community//tools/bt:bt-core_test"),
        ("//community/...", "@community//..."),
        ("//community/...:all", "@community//...:all"),
        ("//community:target", "@community//:target"),
        ("//community", "@community//:community"),
        // Not the community directory.
        ("//communityx/...", "//communityx/..."),
        ("//plugins/air/...", "//plugins/air/..."),
        ("@community//tools/vm/...", "@community//tools/vm/..."),
    ] {
        let resolution = resolve_in(&ultimate, raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
        assert_eq!(resolution.labels, [label], "{raw}");
        assert_eq!(checkout_label(&ultimate, raw), label, "{raw}");
    }

    // A community checkout is its own root.
    let community = fake_air_tree();
    assert_eq!(checkout_label(&community, "//community/tools/vm/..."), "//community/tools/vm/...");
}

/// `bt` runs in the checkout root, whatever the working directory. A pattern whose directory is not there is refused,
/// and for a directory of the community repository the refusal names its `@community` spelling.
#[test]
fn a_pattern_of_a_missing_directory_is_a_usage_refusal() {
    let ultimate = fake_air_tree();
    ultimate.put("community/MODULE.bazel", "");
    ultimate.put("community/tools/vm/BUILD.bazel", "");
    for present in [
        "//plugins/air/...",
        "//plugins/air:x",
        "//...",
        "//:x",
        "//plugins/*:all",
        "@community//tools/vm/...",
        "@community//missing/...",
    ] {
        check_package_dir(&ultimate, present).unwrap_or_else(|failure| panic!("{present}: {failure}"));
    }

    let failure = check_package_dir(&ultimate, "//tools/vm/...").expect_err("a missing directory");
    assert_eq!(failure.exit, exit::USAGE);
    assert_eq!(
        failure.message,
        "No such directory: tools/vm\n  (bt runs every target pattern in the checkout root /repo, whatever the working \
         directory;\n   for community/tools/vm, pass @community//tools/vm/...)"
    );
    let label = check_package_dir(&ultimate, "//tools/vm:vm").expect_err("a missing directory");
    assert!(label.message.ends_with("pass @community//tools/vm:vm)"), "{}", label.message);

    // Without the community directory, the refusal names no other spelling.
    let community = fake_air_tree();
    let failure = check_package_dir(&community, "//tools/vm/...").expect_err("a missing directory");
    assert!(failure.message.ends_with("whatever the working directory)"), "{}", failure.message);
}

#[test]
fn a_path_outside_the_repository_is_a_usage_refusal() {
    for raw in ["/elsewhere/plugins/air", "../other-repo/plugins"] {
        let failure = refusal(resolve_tree(raw));
        assert!(failure.message.contains("outside the repository"), "{raw}: {}", failure.message);
    }
}

/// The Windows dialect of the cases above, driven from whatever host runs this suite: the answer has to be the same
/// on both.
#[test]
fn a_windows_directory_selector_is_resolved_against_the_repository_root() {
    let windows = || {
        let tree = air_tree();
        FakeRuntime::on(
            Platform::Windows,
            r"C:\repo",
            tree.iter().map(|(path, text)| (path.as_str(), text.as_str())),
        )
    };
    for raw in [
        r"plugins\air\shared\core",
        r"C:\repo\plugins\air\shared\core",
        // Windows compares a path without case, so refusing this one states something false about it.
        r"c:\REPO\plugins\air\shared\core",
        // Rooted but driveless: the repository's drive is the only one this wrapper has an opinion about.
        r"\repo\plugins\air\shared\core",
    ] {
        let resolution = resolve_in(&windows(), raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
        assert_eq!(resolution.labels, ["//plugins/air/shared/core/..."], "{raw}");
    }
    for raw in [
        r"D:\repo\plugins\air",
        r"C:\other\plugins\air",
        r"C:\repo",
        r"..\other-repo\plugins",
    ] {
        let failure = refusal(resolve_in(&windows(), raw));
        assert!(failure.message.contains("outside the repository"), "{raw}: {}", failure.message);
    }
}

/// A typo must not become a silently empty target pattern that reports zero tests.
#[test]
fn a_directory_that_does_not_exist_is_a_usage_refusal() {
    let failure = refusal(resolve_tree("plugins/air/does/not/exist"));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(
        failure.message.contains("No such directory: plugins/air/does/not/exist"),
        "{}",
        failure.message
    );
}

#[test]
fn a_file_path_says_so_rather_than_claiming_the_directory_is_missing() {
    let failure = refusal(resolve_tree("plugins/air/shared/core/testSrc/AgentThreadCliTest.kt"));
    assert!(failure.message.contains("Not a directory"), "{}", failure.message);
}

#[test]
fn resolving_a_directory_asks_no_accio() {
    let fake = fake_air_tree();
    resolve_in(&fake, "plugins/air/shared/core").expect("resolves");
    // One listing to prove the directory exists.
    assert_eq!(fake.reads(), [format!("readDir {REPO_ROOT}/plugins/air/shared/core")]);
    assert_eq!(fake.accio_calls(), Vec::<String>::new());
}

/// A flow id and a suite id are lower-case words joined by hyphens, and the hyphen is what keeps them apart from
/// every other kind. Each row pins one neighbour a hyphenated name could be mistaken for.
#[test]
fn a_flow_id_and_a_suite_id_are_their_own_kinds() {
    for (raw, want) in [
        ("flow-rename-session", SelectorKind::Flow),
        ("flow-java-to-kotlin", SelectorKind::Flow),
        ("rename-session", SelectorKind::Suite),
        ("add-to-agent-context-send", SelectorKind::Suite),
        ("new-session-native-chat", SelectorKind::Suite),
        ("RenameSession", SelectorKind::SimpleName),
        ("a.b.RenameSession", SelectorKind::Fqn),
        ("//plugins/air/x:rename-session", SelectorKind::Label),
        ("//plugins/air/rename-session/...", SelectorKind::Pattern),
        ("plugins/air/tests/vm-lane", SelectorKind::Dir),
        ("rename-session/", SelectorKind::Dir),
    ] {
        let selector = classify(raw);
        assert_eq!((selector.kind, selector.name.as_str()), (want, raw), "{raw}");
        assert_eq!(
            selector.kind.names_suites(),
            matches!(want, SelectorKind::Flow | SelectorKind::Suite),
            "{raw}"
        );
    }
}

/// A suite runs all of its scenarios, so neither kind takes a method, and a name that only looks close to one is
/// refused rather than read as something else.
#[test]
fn a_malformed_flow_or_suite_id_is_refused() {
    for (raw, fragment) in [
        ("flow-rename-session#renames", "A flow selector cannot carry a #method"),
        ("rename-session#renames", "A suite selector cannot carry a #method"),
        ("Rename-Session", "Cannot interpret selector"),
        ("flow-", "Cannot interpret selector"),
        ("rename--session", "Cannot interpret selector"),
        ("com.example.rename-session", "Cannot interpret selector"),
        // A single lower-case word reads as a package, which is no selector.
        ("flow", "bt takes no package selector"),
        ("rename", "bt takes no package selector"),
    ] {
        let failure = refusal(Selector::classify(raw));
        assert_eq!(failure.exit, exit::USAGE, "{raw}");
        assert!(failure.message.contains(fragment), "{raw}: {}", failure.message);
    }
}

/// A class name resolves in the whole repository, also in a checkout without an area. Its module then runs only when
/// the migrated list names it.
#[test]
fn a_name_resolves_without_an_area() {
    let fake = fake_air_tree();
    fake.put("build/bazel-migrated-test-modules.txt", "intellij.air.shared.core.tests\n");
    let empty = crate::Areas::default();
    let resolution = resolve_selector(&fake, &classify("AgentThreadCliTest"), &ResolutionInputs::new(&fake, &empty))
        .unwrap_or_else(|failure| panic!("{failure:?}"));
    assert_eq!(resolution.labels, ["//plugins/air/shared/core:ai-agent-core-tests_test"]);
}

/// A module selector resolves from the module list and one `accio modules` call, with no area.
#[test]
fn a_module_selector_resolves_from_the_module_list_and_accio() {
    let fake = fake_air_tree();
    fake.put("community/MODULE.bazel", "");
    fake.put(
        ".idea/modules.xml",
        r#"<module fileurl="file://$PROJECT_DIR$/community/plugins/x/intellij.x.tests.iml" filepath="$PROJECT_DIR$/community/plugins/x/intellij.x.tests.iml" />"#,
    );
    fake.add_module(tests_module_json("community/plugins/x", "intellij.x", "intellij.x.tests", "x_test"));
    fake.put("community/build/bazel-migrated-test-modules.txt", "intellij.x.tests\n");
    let empty = crate::Areas::default();
    let resolution = resolve_selector(
        &fake,
        &Selector::new(SelectorKind::Module, "intellij.x.tests"),
        &ResolutionInputs::new(&fake, &empty),
    )
    .unwrap_or_else(|failure| panic!("{failure:?}"));
    assert_eq!(resolution.labels, ["@community//plugins/x:x_test"]);
    assert!(!resolution.multi_target);
    assert_eq!(fake.accio_calls(), ["modules --paths community/plugins/x"]);
    assert!(fake.reads().iter().all(|read| !read.starts_with("readDir")), "{:?}", fake.reads());
}

// --- the classes that suite documents name ---------------------------------------------------------------------------

/// The FQN of a generated suite class of the ui lane.
const RENAME: &str = "com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest";

/// The test label of the ui lane in the fixture lane table, which is the label of its lane test.
const UI_LANE_TEST: &str = "//plugins/air/tests/integration/ui:ui_lane_test";

/// A suite document of `lane` whose class is `fqn`, with the `testClass` label that the generator writes.
fn class_document(suite: &str, lane: &str, fqn: &str) -> String {
    let simple = fqn.rsplit('.').next().unwrap_or(fqn);
    format!(
        r#"{{"suite": "{suite}", "lane": "{lane}", "testClassName": "{simple}",
  "labels": [{{"name": "testClass", "value": "{fqn}"}}], "profiles": []}}"#
    )
}

/// The fixture with the suite documents `documents`, each a suite id, a catalog lane and a class FQN.
fn with_documents(fake: FakeRuntime, documents: &[(&str, &str, &str)]) -> FakeRuntime {
    for (suite, lane, fqn) in documents {
        fake.put(&document_path(suite), &class_document(suite, lane, fqn));
    }
    fake
}

/// The fixture with a module whose test root holds the class `fqn`, and whose test target is `shells_test`.
fn with_shell_module(fqn: &str) -> FakeRuntime {
    let simple = fqn.rsplit('.').next().expect("an FQN");
    let fake = fake_air_tree();
    fake.add_class(&format!("plugins/air/shells/testSrc/{simple}.kt"), fqn);
    fake.add_module(tests_module_json(
        "plugins/air/shells",
        "intellij.air.shells",
        "intellij.air.shells.tests",
        "shells_test",
    ));
    fake
}

// A class that a suite document names resolves to the test label of the lane of the document. No module of the tree
// holds it, so the lane needs no test target of the module. A scenario after `#` stays on the filter, which the lane
// test reads as its selector. A class of an explicit-only lane resolves the same way, because a class names its suite.
#[test]
fn a_class_that_a_suite_document_names_resolves_to_the_test_label_of_its_lane() {
    let fake = with_documents(
        fake_air_tree(),
        &[
            ("rename-session", "UI", RENAME),
            (
                "long-session-performance",
                "UI_PERF",
                "com.intellij.air.integration.acp.AirLongSessionPerformanceUiTest",
            ),
        ],
    );
    let ui = Resolution {
        labels: vec![UI_LANE_TEST.to_owned()],
        filter: Some(RENAME.to_owned()),
        ..Resolution::default()
    };
    assert_eq!(resolve_in(&fake, "AirRenameSessionGeneratedFlowUiTest"), Ok(ui.clone()));
    assert_eq!(resolve_in(&fake, RENAME), Ok(ui));
    assert_eq!(
        resolve_in(&fake, "AirRenameSessionGeneratedFlowUiTest#renameSession").map(|resolution| resolution.filter),
        Ok(Some(format!("{RENAME}#renameSession")))
    );
    assert_eq!(
        resolve_in(&fake, "AirLongSessionPerformanceUiTest").map(|resolution| resolution.labels),
        Ok(vec!["//plugins/air/tests/integration/ui-perf:ui-perf_lane_test".to_owned()])
    );
}

// The answer for a documented class depends on the lane row alone. When the row of the ui lane names another test
// label, the class resolves to that label, and every other field of the answer stays the same.
#[test]
fn a_documented_class_follows_the_test_label_of_its_lane_row() {
    let row = format!("\"testLabel\": \"{UI_LANE_TEST}\"");
    assert_eq!(
        LANES_TEXT.matches(&row).count(),
        1,
        "the fixture table names the ui test label once"
    );
    let other = "//plugins/air/tests/integration/ui:ui_other_test";
    let flipped = LANES_TEXT.replace(&row, &format!("\"testLabel\": \"{other}\""));
    let flipped = Areas::new(vec![Area::new(
        AREA_DIR,
        LANES_FILE,
        Lanes::parse(&flipped).expect("the flipped table parses"),
    )]);
    let fake = with_documents(fake_air_tree(), &[("rename-session", "UI", RENAME)]);
    let resolve = |areas: &Areas| {
        resolve_selector(
            &fake,
            &classify("AirRenameSessionGeneratedFlowUiTest"),
            &ResolutionInputs::new(&fake, areas),
        )
        .expect("resolves")
    };
    let (lane, moved) = (resolve(areas()), resolve(&flipped));
    assert_eq!(lane.labels, [UI_LANE_TEST]);
    assert_eq!(moved.labels, [other]);
    assert_eq!(
        Resolution {
            labels: lane.labels.clone(),
            ..moved
        },
        lane
    );
}

// A module of the tree may still hold the class that a document names, as the generated shells of a lane do. The two
// answers are one class, and it runs in the lane of the document. Without the document, the module answers.
#[test]
fn a_class_in_a_module_and_in_a_document_is_one_class_of_the_lane() {
    let module_only = with_shell_module(RENAME);
    assert_eq!(
        resolve_in(&module_only, "AirRenameSessionGeneratedFlowUiTest").map(|resolution| resolution.labels),
        Ok(vec!["//plugins/air/shells:shells_test".to_owned()])
    );
    let both = with_documents(with_shell_module(RENAME), &[("rename-session", "UI", RENAME)]);
    assert_eq!(
        resolve_in(&both, "AirRenameSessionGeneratedFlowUiTest"),
        Ok(Resolution {
            labels: vec![UI_LANE_TEST.to_owned()],
            filter: Some(RENAME.to_owned()),
            ..Resolution::default()
        })
    );
}

// A document and a module that name two classes of one simple name are two classes. The simple name is refused with
// both, and the FQN of either one resolves to it.
#[test]
fn a_documented_class_and_another_class_of_its_simple_name_are_refused_by_the_simple_name() {
    let other = "com.intellij.air.frontend.AirRenameSessionGeneratedFlowUiTest";
    let fake = with_documents(with_shell_module(other), &[("rename-session", "UI", RENAME)]);
    let failure = refusal(resolve_in(&fake, "AirRenameSessionGeneratedFlowUiTest"));
    assert_eq!(failure.exit, exit::USAGE);
    for expected in ["2 classes named", RENAME, UI_LANE_TEST, other, "//plugins/air/shells:shells_test"] {
        assert!(failure.message.contains(expected), "no {expected:?} in:\n{}", failure.message);
    }
    assert_eq!(
        resolve_in(&fake, RENAME).map(|resolution| resolution.labels),
        Ok(vec![UI_LANE_TEST.to_owned()])
    );
    assert_eq!(
        resolve_in(&fake, other).map(|resolution| resolution.labels),
        Ok(vec!["//plugins/air/shells:shells_test".to_owned()])
    );
}

fn filter_of(fake: &FakeRuntime, raw: &str) -> Result<FilterForm, Refusal> {
    canonical_filter(raw, &ResolutionInputs::new(fake, areas()))
}

fn changed(value: &str, note: &str) -> FilterForm {
    FilterForm {
        value: value.to_owned(),
        note: Some(note.to_owned()),
    }
}

/// An agent gave `pkg.Class.method`. bt built the target for 1 min 27 s, and then the runner threw on the value.
#[test]
fn a_filter_with_a_clear_meaning_takes_the_form_of_the_runner_before_the_build() {
    let fake = fake_air_tree();
    let identity = "com.intellij.air.shared.core.AgentThreadIdentityTest#builds";
    for (raw, expected) in [
        (
            "a.b.FooTest.testBar",
            changed(
                "a.b.FooTest#testBar",
                "--filter a.b.FooTest.testBar runs as a.b.FooTest#testBar: the runner reads a method after #, not after a dot",
            ),
        ),
        (
            "AgentThreadIdentityTest#builds",
            changed(
                identity,
                &format!(
                    "--filter AgentThreadIdentityTest#builds runs as {identity}: the runner loads the class of a \
                     Class#method by its FQN"
                ),
            ),
        ),
        (
            "AgentThreadIdentityTest.builds",
            changed(
                identity,
                &format!("--filter AgentThreadIdentityTest.builds runs as {identity}: the runner reads a method after #, not after a dot"),
            ),
        ),
        (
            "a.b.Outer.Inner#test",
            changed(
                "a.b.Outer$Inner#test",
                "--filter a.b.Outer.Inner#test runs as a.b.Outer$Inner#test: the runner names a nested class with $ after its \
                 outer class",
            ),
        ),
        (
            "a.b.Outer.Inner.test",
            changed(
                "a.b.Outer$Inner#test",
                "--filter a.b.Outer.Inner.test runs as a.b.Outer$Inner#test: the runner names a nested class with $ after its \
                 outer class",
            ),
        ),
        (
            "a.b.Outer.Inner",
            changed(
                "a.b.Outer",
                "--filter a.b.Outer.Inner runs as a.b.Outer: the runner finds an @Nested class only through its outer class",
            ),
        ),
        (
            "a.b.Outer$Inner",
            changed(
                "a.b.Outer",
                "--filter a.b.Outer$Inner runs as a.b.Outer: the runner finds an @Nested class only through its outer class",
            ),
        ),
        (
            "com.intellij.air.worktree.*",
            changed(
                "com.intellij.air.worktree",
                "--filter com.intellij.air.worktree.* runs as com.intellij.air.worktree: --filter takes no wildcard, and a \
                 package runs every class of it",
            ),
        ),
    ] {
        assert_eq!(
            filter_of(&fake, raw).unwrap_or_else(|failure| panic!("{raw} refused: {failure}")),
            expected,
            "{raw}"
        );
    }
    // Each call of the test has its own memo, and only a simple name with a method asks accio.
    assert_eq!(
        fake.accio_calls(),
        [symbol_call("AgentThreadIdentityTest"), symbol_call("AgentThreadIdentityTest")]
    );
}

#[test]
fn a_filter_in_the_form_of_the_runner_stays_and_asks_no_accio() {
    let fake = fake_air_tree();
    for raw in [
        "a.b.FooTest",
        "a.b.FooTest#testBar",
        "a.b.Outer$Inner#test",
        "a.b",
        "FooTest",
        "a.IDEA_355419.FooTest",
        "a.b.FooTest#a test name",
    ] {
        assert_eq!(
            filter_of(&fake, raw).unwrap_or_else(|failure| panic!("{raw} refused: {failure}")),
            FilterForm {
                value: raw.to_owned(),
                note: None
            },
            "{raw}"
        );
    }
    assert_eq!(fake.accio_calls(), Vec::<String>::new());
}

#[test]
fn a_filter_that_no_form_can_express_is_refused_before_the_build() {
    let fake = fake_air_tree();
    for raw in [
        "com.intellij.air.(ATest|BTest)",
        "a.ATest,a.BTest",
        "a.*Test",
        "a.b.FooTest#",
        "*Test",
    ] {
        let failure = refusal(filter_of(&fake, raw));
        assert_eq!(failure.exit, exit::USAGE, "{raw}");
        assert!(
            failure.message.contains("no wildcard, no regex and no list"),
            "{raw}: {}",
            failure.message
        );
    }
}

#[test]
fn a_simple_name_of_two_classes_is_refused_and_of_no_class_runs_as_given() {
    let fake = fake_air_tree();
    let failure = refusal(filter_of(&fake, "AgentPromptChangesTreeContextContributorTest#test"));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(
        failure
            .message
            .starts_with("2 classes named AgentPromptChangesTreeContextContributorTest; pass the FQN in --filter"),
        "{}",
        failure.message
    );
    assert_eq!(
        filter_of(&fake, "NoSuchTest#test").expect("runs as given"),
        changed(
            "NoSuchTest#test",
            "--filter NoSuchTest#test: accio gave no class, so the runner gets the simple name and can find no test"
        )
    );
}

#[test]
fn a_method_after_a_dot_is_the_method_of_the_class() {
    assert_eq!(
        classify("FooTest.testBar"),
        Selector {
            kind: SelectorKind::SimpleName,
            name: "FooTest".to_owned(),
            method: Some("testBar".to_owned()),
        }
    );
    assert_eq!(
        classify("a.b.FooTest.testBar"),
        Selector {
            kind: SelectorKind::Fqn,
            name: "a.b.FooTest".to_owned(),
            method: Some("testBar".to_owned()),
        }
    );
}
