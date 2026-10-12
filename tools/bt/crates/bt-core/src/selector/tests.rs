use pretty_assertions::assert_eq;

use super::*;
use crate::exit;
use crate::fake::{FakeRuntime, REPO_ROOT, air_tree, areas, fake_air_tree, refusal};
use crate::scan::ResolutionInputs;

fn classify(raw: &str) -> Selector {
    Selector::classify(raw).unwrap_or_else(|failure| panic!("{raw} refused: {failure}"))
}

#[test]
fn classify_selector_reads_each_kind() {
    for (raw, kind) in [
        ("//plugins/air/shared/core:ai-agent-core-tests_test", SelectorKind::Label),
        ("//plugins/air/...", SelectorKind::Pattern),
        ("//plugins/air/shared/*:all", SelectorKind::Pattern),
        ("AgentThreadCliTest", SelectorKind::SimpleName),
        ("com.intellij.air.threads.AgentThreadCliTest", SelectorKind::Fqn),
        ("com.intellij.air.threads", SelectorKind::Package),
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
    for raw in ["com.intellij.air.threads#foo", "plugins/air/backend/vcs#foo"] {
        let failure = refusal(Selector::classify(raw));
        assert!(failure.message.contains("cannot carry a #method"), "{raw}: {}", failure.message);
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

#[test]
fn a_unique_simple_name_resolves_to_label_plus_fqn() {
    assert_eq!(
        resolve_tree("AgentThreadIdentityTest"),
        Ok(Resolution {
            labels: vec!["//plugins/air/shared/core:ai-agent-core-tests_test".to_owned()],
            filter: Some("com.intellij.air.shared.core.AgentThreadIdentityTest".to_owned()),
            ..Resolution::default()
        })
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
        "com.intellij.air.frontend.prompt.vcs.context",
    ] {
        assert!(failure.message.contains(expected), "no {expected:?} in:\n{}", failure.message);
    }
}

#[test]
fn the_fully_qualified_form_disambiguates_a_colliding_name() {
    let resolution =
        resolve_tree("com.intellij.air.frontend.prompt.vcs.context.AgentPromptChangesTreeContextContributorTest").expect("resolves");
    assert_eq!(
        resolution.labels,
        ["//plugins/air/frontend/prompt/vcs:air-frontend-prompt-vcs-tests_test"]
    );
}

#[test]
fn an_unknown_name_suggests_the_nearest_one() {
    let failure = refusal(resolve_tree("AgentThreadCliTst"));
    assert!(
        failure.message.contains("No test class named AgentThreadCliTst"),
        "{}",
        failure.message
    );
    assert!(failure.message.contains("did you mean  AgentThreadCliTest"), "{}", failure.message);
}

#[test]
fn a_package_selector_resolves_through_the_declared_prefix() {
    let resolution = resolve_tree("com.intellij.air.shared.core").expect("resolves");
    assert_eq!(resolution.include_package.as_deref(), Some("com.intellij.air.shared.core"));
    // A package goes through the JUnit5 filter, never `--test_filter`, which would match no class.
    assert_eq!(resolution.filter, None);
}

#[test]
fn a_package_spanning_several_targets_asks_for_an_explicit_one() {
    let failure = refusal(resolve_tree("com.intellij.air"));
    assert!(failure.message.contains("spans 3 test targets"), "{}", failure.message);
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

/// The point of the lazy inputs: a label names its target already, so paying ~250 ms to scan plugins/air first is
/// pure waste. A runtime that refuses all I/O is the only durable guard.
#[test]
fn a_label_and_a_pattern_touch_no_filesystem() {
    let fake = fake_air_tree();
    fake.forbid_file_system();
    for raw in ["//other/pkg:some_test", "//plugins/air/..."] {
        resolve_in(&fake, raw).unwrap_or_else(|failure| panic!("{raw}: {failure}"));
    }
    assert_eq!(fake.reads(), Vec::<String>::new());
}

/// The @Nested and secondary-top-level-class fallback: the filename says nothing, so every indexed file is read.
#[test]
fn a_class_whose_filename_differs_is_still_found() {
    let fake = fake_air_tree();
    fake.put(
        "plugins/air/shared/core/testSrc/Fixtures.kt",
        "package com.intellij.air.shared.core\n\ninternal class SecondaryTest {\n}\n",
    );
    let resolution = resolve_in(&fake, "SecondaryTest").expect("resolves");
    assert_eq!(resolution.filter.as_deref(), Some("com.intellij.air.shared.core.SecondaryTest"));
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
fn resolving_a_directory_never_scans_the_air_tree() {
    let fake = fake_air_tree();
    resolve_in(&fake, "plugins/air/shared/core").expect("resolves");
    // One listing to prove the directory exists; the .iml and index passes never ran.
    assert_eq!(fake.reads(), [format!("readDir {REPO_ROOT}/plugins/air/shared/core")]);
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
        ("flow", SelectorKind::Package),
        ("rename", SelectorKind::Package),
        ("com.intellij.air", SelectorKind::Package),
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
    ] {
        let failure = refusal(Selector::classify(raw));
        assert_eq!(failure.exit, exit::USAGE, "{raw}");
        assert!(failure.message.contains(fragment), "{raw}: {}", failure.message);
    }
}

/// A name has nothing to resolve against in a checkout without an area, and the refusal says which selector it was.
#[test]
fn a_name_selector_without_an_area_names_itself() {
    let fake = fake_air_tree();
    let empty = crate::Areas::default();
    for raw in ["AgentThreadCliTest", "com.intellij.air.shared.core"] {
        let failure = refusal(resolve_selector(
            &fake,
            &Selector::classify(raw).expect("classifies"),
            &ResolutionInputs::new(&fake, &empty),
        ));
        assert_eq!(failure.exit, exit::USAGE, "{raw}");
        assert!(failure.message.contains(raw), "{}", failure.message);
        assert!(failure.message.contains("names no area"), "{}", failure.message);
    }
    assert!(fake.reads().is_empty(), "{:?}", fake.reads());
}

/// A module name reads as a package, so only `--module` makes a module selector.
#[test]
fn a_module_name_classifies_as_a_package() {
    assert_eq!(classify("intellij.platform.util.tests").kind, SelectorKind::Package);
    assert_eq!(SelectorKind::Module.as_str(), "module");
}

/// A module selector resolves from the module list and one BUILD.bazel, before any tree scan, and with no area.
#[test]
fn a_module_selector_resolves_without_the_tree_scan() {
    let fake = fake_air_tree();
    fake.put("community/MODULE.bazel", "");
    fake.put(
        ".idea/modules.xml",
        r#"<module fileurl="file://$PROJECT_DIR$/community/plugins/x/intellij.x.tests.iml" filepath="$PROJECT_DIR$/community/plugins/x/intellij.x.tests.iml" />"#,
    );
    fake.put(
        "community/plugins/x/BUILD.bazel",
        &crate::fake::module_build_bazel_text("intellij.x.tests", "x_test"),
    );
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
    assert!(fake.reads().iter().all(|read| !read.starts_with("readDir")), "{:?}", fake.reads());
}
