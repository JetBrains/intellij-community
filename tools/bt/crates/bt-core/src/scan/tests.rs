use pretty_assertions::assert_eq;

use super::*;
use crate::exit;
use crate::fake::{AREA_DIR, build_bazel_text, fake_air_tree, iml_text, refusal};

#[test]
fn iml_test_roots_pick_the_source_root_and_ignore_the_resource_root() {
    // The real shape of intellij.air.backend.session.runtime.tests.iml.
    let roots = parse_iml_test_roots(&iml_text(&[
        r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" packagePrefix="com.intellij.air.threads" />"#,
        r#"<sourceFolder url="file://$MODULE_DIR$/testData" type="java-test-resource" isTestSource="true" />"#,
    ]));
    assert_eq!(
        roots,
        [ImlTestRoot {
            path: "testSrc".to_owned(),
            package_prefix: Some("com.intellij.air.threads".to_owned())
        }]
    );
}

#[test]
fn iml_production_source_roots_are_ignored() {
    let roots = parse_iml_test_roots(&iml_text(&[
        r#"<sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />"#,
    ]));
    assert_eq!(roots, []);
}

/// Absent and empty are different: an empty prefix declares the default package, and treating it as a prefix would
/// make every package selector match this root.
#[test]
fn a_root_without_a_package_prefix_has_none() {
    let absent = parse_iml_test_roots(&iml_text(&[
        r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" />"#,
    ]));
    let empty = parse_iml_test_roots(&iml_text(&[
        r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" packagePrefix="" />"#,
    ]));
    for roots in [absent, empty] {
        assert_eq!(roots.len(), 1);
        assert_eq!(roots[0].package_prefix, None);
    }
}

#[test]
fn multiple_test_roots_and_crlf_line_endings() {
    let text = iml_text(&[
        r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" />"#,
        r#"<sourceFolder url="file://$MODULE_DIR$/testFixtures" isTestSource="true" packagePrefix="a.b" />"#,
    ])
    .replace('\n', "\r\n");
    let paths: Vec<String> = parse_iml_test_roots(&text)
        .into_iter()
        .map(|root| root.path)
        .collect();
    assert_eq!(paths, ["testSrc", "testFixtures"]);
}

#[test]
fn jps_test_name_ignores_the_sibling_library() {
    // //plugins/air/shared/core declares ai-agent-core-tests_test, not air-…: the name is not derivable.
    for target in ["air-thing-tests_test", "ai-agent-core-tests_test"] {
        assert_eq!(
            parse_jps_test_name(&build_bazel_text(target)),
            Ok(Some(target.to_owned()))
        );
    }
}

#[test]
fn jps_test_name_is_not_fooled_by_strings_or_comments() {
    let closing_paren = "jps_test(\n  args = [\"--flag=foo)bar\"],\n  name = \"target_test\",\n)";
    assert_eq!(
        parse_jps_test_name(closing_paren),
        Ok(Some("target_test".to_owned()))
    );
    let commented = "jps_test(\n  # name = \"commented_out\",\n  args = [\"--tag=#nope)\"],\n  name = \"real_test\",\n)";
    assert_eq!(
        parse_jps_test_name(commented),
        Ok(Some("real_test".to_owned()))
    );
    let loaded =
        "load(\"@rules_jvm//:test.bzl\", \"jps_test\")\n\njps_test(\n  name = \"only_test\",\n)";
    assert_eq!(
        parse_jps_test_name(loaded),
        Ok(Some("only_test".to_owned()))
    );
}

#[test]
fn a_build_file_with_no_jps_test_has_no_target() {
    assert_eq!(
        parse_jps_test_name("jvm_library(\n  name = \"lib\",\n)"),
        Ok(None)
    );
}

/// Two runnable targets in one BUILD.bazel breaks the one-target-per-module invariant resolution rests on, and
/// picking either would silently run the wrong one. That is the repository being wrong, not the caller.
#[test]
fn two_unfiltered_jps_tests_are_infrastructure() {
    let failure = refusal(parse_jps_test_name(&format!(
        "{}\n{}",
        build_bazel_text("one_test"),
        build_bazel_text("two_test")
    )));
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("bt_infra", exit::INFRA)
    );
    assert!(failure.message.contains("found 2"), "{}", failure.message);
}

/// A class must resolve to the general target: a target narrowed by a JUnit filter reports zero tests for
/// everything outside its filter.
#[test]
fn a_target_narrowed_by_junit_filters_is_skipped() {
    let narrowed = r#"jps_test(
  name = "flowPlan_test",
  env = {"JB_TEST_JUNIT5_FILTERS": "include-classname=com.intellij.air.integration.flow.AirFlowPlanTest"},
)"#;
    assert_eq!(
        parse_jps_test_name(&format!(
            "{}\n{narrowed}",
            build_bazel_text("integrationTests_test")
        )),
        Ok(Some("integrationTests_test".to_owned()))
    );
    let lane_target = "jps_test(\n  name = \"ui_test\",\n  tags = [\"air-integration-ui\"],\n)";
    let tagged_narrow = r#"jps_test(
  name = "wysiwyg-subset_test",
  env = {"JB_TEST_JUNIT5_FILTERS": "include-classname=com.intellij.air.sdd.WysiwygTest"},
  tags = ["air-integration-ui"],
)"#;
    assert_eq!(
        parse_jps_test_name(&format!("{lane_target}\n{tagged_narrow}")),
        Ok(Some("ui_test".to_owned()))
    );
}

#[test]
fn derive_package_reads_the_declaration() {
    for (what, text, want) in [
        (
            "a Kotlin package after a license header",
            "// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by ...\npackage com.intellij.air.threads\n\nclass Foo",
            "com.intellij.air.threads",
        ),
        (
            "a Java package with a semicolon",
            "package com.intellij.air.legacy;\n\npublic class Foo {}",
            "com.intellij.air.legacy",
        ),
        ("the default package", "class Foo\n", ""),
        (
            "the word package inside a later string",
            "package a.b\n\nval message = \"package c.d\"\n",
            "a.b",
        ),
        (
            "a backticked segment",
            "package com.intellij.`object`.tests\n",
            "com.intellij.object.tests",
        ),
        // `$` under `(?m)` matches only before a LF, so without the `\r` in the pattern every class in a CRLF file
        // would resolve to the default package.
        (
            "CRLF line endings",
            "package a.b\r\n\r\nclass Foo\r\n",
            "a.b",
        ),
    ] {
        assert_eq!(derive_package(text), want, "{what}");
    }
}

#[test]
fn declares_type_finds_a_top_level_declaration() {
    assert!(declares_type(
        "package a\n\ninternal class HelperTest {\n}\n",
        "HelperTest"
    ));
    assert!(declares_type(
        "@TestOnly\nobject Fixtures {\n}\n",
        "Fixtures"
    ));
    // The word boundary is what stops `FooTest` resolving to `FooTestBase` and running the wrong class.
    assert!(!declares_type("class FooTestBase\n", "FooTest"));
}

fn test_roots_of(runtime: &dyn Runtime) -> Vec<TestRoot> {
    collect_test_roots(runtime, &scan_tree(runtime, &[AREA_DIR])).expect("the roots are collected")
}

#[test]
fn the_scan_prunes_generated_and_vendored_directories() {
    let fake = fake_air_tree();
    fake.put("plugins/air/docs/node_modules/react/index.js", "");
    fake.put(
        "plugins/air/docs/node_modules/junk/Fake.kt",
        "package junk\n\nclass Fake\n",
    );
    fake.put(
        "plugins/air/generated/Generated.kt",
        "package generated\n\nclass Generated\n",
    );
    fake.put(
        "plugins/air/bazel-out/Stale.kt",
        "package stale\n\nclass Stale\n",
    );

    let scan = scan_tree(&fake, &[AREA_DIR]);
    for forbidden in ["node_modules", "/generated/", "bazel-out"] {
        for file in &scan.sources {
            assert!(
                !file.contains(forbidden),
                "{file} survived pruning of {forbidden}"
            );
        }
    }
    assert!(scan.dirs.contains("plugins/air/shared/core/testSrc"));
    assert!(
        scan.imls
            .contains(&"plugins/air/shared/core/intellij.air.shared.core.tests.iml".to_owned()),
        "{:?}",
        scan.imls
    );
}

#[test]
fn only_test_roots_with_a_runnable_target_are_collected() {
    let roots = test_roots_of(&fake_air_tree());
    for root in &roots {
        assert!(
            !root.src_dir.contains("notest") && !root.src_dir.contains("shared/api"),
            "{}",
            root.src_dir
        );
    }
    let mut labels: Vec<&str> = roots.iter().map(|root| root.label.as_str()).collect();
    labels.sort_unstable();
    assert_eq!(
        labels,
        [
            "//plugins/air/backend/vcs:air-backend-vcs-tests_test",
            "//plugins/air/frontend/prompt/vcs:air-frontend-prompt-vcs-tests_test",
            "//plugins/air/shared/core:ai-agent-core-tests_test",
        ]
    );
}

#[test]
fn the_shortened_package_prefix_is_carried_through() {
    let roots = test_roots_of(&fake_air_tree());
    let core = roots
        .iter()
        .find(|root| root.label.ends_with("ai-agent-core-tests_test"))
        .expect("the core module's root was collected");
    assert_eq!(
        core.package_prefix.as_deref(),
        Some("com.intellij.air.shared.core")
    );
    assert_eq!(core.src_dir, "plugins/air/shared/core/testSrc");
}

#[test]
fn the_index_is_keyed_by_simple_name_only() {
    let fake = fake_air_tree();
    let scan = scan_tree(&fake, &[AREA_DIR]);
    let roots = collect_test_roots(&fake, &scan).expect("the roots are collected");
    let index = build_index(&scan, &roots);
    assert_eq!(
        index.keys().collect::<Vec<_>>(),
        [
            "AgentPromptChangesTreeContextContributorTest",
            "AgentThreadCliTest",
            "AgentThreadIdentityTest"
        ]
    );
    // The colliding name keeps both candidates; collapsing them is what would silently run one module's test.
    assert_eq!(
        index["AgentPromptChangesTreeContextContributorTest"].len(),
        2
    );
}
