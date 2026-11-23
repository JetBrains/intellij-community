use pretty_assertions::assert_eq;

use super::*;
use crate::areas::Areas;
use crate::exit::{INFRA, USAGE};
use crate::fake::{FakeRuntime, REPO_ROOT, areas, module_entry_json, refusal, target_json, tests_module_json};

const ULTIMATE_LIST: &str = "community/build/bazel-migrated-test-modules.txt";
const COMMUNITY_LIST: &str = "build/bazel-migrated-test-modules.txt";

/// The module list for `.iml` paths relative to the checkout root.
fn modules_xml(imls: &[&str]) -> String {
    let entries: Vec<String> = imls
        .iter()
        .map(|iml| format!(r#"      <module fileurl="file://$PROJECT_DIR$/{iml}" filepath="$PROJECT_DIR$/{iml}" />"#))
        .collect();
    format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n  <component name=\"ProjectModuleManager\">\n    \
         <modules>\n{}\n    </modules>\n  </component>\n</project>",
        entries.join("\n")
    )
}

/// Adds the module root `dir` to the fake accio: the tests module `module`, its main module, and the test target
/// `target` that runs the tests module.
fn module_build(fake: &FakeRuntime, dir: &str, module: &str, target: &str) {
    let main = module
        .strip_suffix(".tests")
        .unwrap_or_else(|| panic!("{module} is not a tests module"));
    fake.add_module(tests_module_json(dir, main, module, target));
}

/// The migrated list, with the header comment and the blank line of the real file.
fn list_text(patterns: &[&str]) -> String {
    format!(
        "# Modules whose tests run only under Bazel.\n# One pattern per line.\n\n{}\n",
        patterns.join("\n")
    )
}

/// An ultimate root: `community/MODULE.bazel`, the module list, and the migrated list.
fn ultimate(imls: &[&str], patterns: &[&str]) -> FakeRuntime {
    let fake = FakeRuntime::new([]);
    fake.put("community/MODULE.bazel", "module(name = \"community\")\n");
    fake.put(MODULES_FILE, &modules_xml(imls));
    fake.put(ULTIMATE_LIST, &list_text(patterns));
    fake
}

fn resolve(fake: &FakeRuntime, module: &str) -> Resolution {
    resolve_module(fake, &Areas::default(), module).unwrap_or_else(|failure| panic!("{module} refused: {failure:?}"))
}

fn one_label(label: &str) -> Resolution {
    Resolution {
        labels: vec![label.to_owned()],
        ..Resolution::default()
    }
}

#[test]
fn a_migrated_community_module_of_the_ultimate_root_resolves_in_the_community_repository() {
    let fake = ultimate(&["community/plugins/x/intellij.x.tests.iml"], &["intellij.x.tests"]);
    module_build(&fake, "community/plugins/x", "intellij.x.tests", "x_test");
    assert_eq!(resolve(&fake, "intellij.x.tests"), one_label("@community//plugins/x:x_test"));
}

#[test]
fn a_migrated_module_of_the_ultimate_root_resolves_to_a_root_label() {
    let fake = ultimate(&["tests/x/intellij.x.tests.iml"], &["intellij.x.tests"]);
    module_build(&fake, "tests/x", "intellij.x.tests", "x_test");
    assert_eq!(resolve(&fake, "intellij.x.tests"), one_label("//tests/x:x_test"));
}

#[test]
fn a_module_at_the_checkout_root_resolves_to_the_root_package() {
    let fake = ultimate(&["intellij.root.tests.iml"], &["intellij.root.tests"]);
    module_build(&fake, "", "intellij.root.tests", "root_test");
    assert_eq!(resolve(&fake, "intellij.root.tests"), one_label("//:root_test"));
}

/// A community checkout has no `community/MODULE.bazel`. Its labels are its own, and its migrated list is under
/// `build/`.
#[test]
fn a_module_of_a_community_checkout_resolves_to_a_label_of_that_checkout() {
    let fake = FakeRuntime::new([]);
    fake.put(MODULES_FILE, &modules_xml(&["plugins/x/intellij.x.tests.iml"]));
    fake.put(COMMUNITY_LIST, &list_text(&["intellij.x.tests"]));
    module_build(&fake, "plugins/x", "intellij.x.tests", "x_test");
    assert_eq!(resolve(&fake, "intellij.x.tests"), one_label("//plugins/x:x_test"));
}

#[test]
fn a_prefix_pattern_matches_every_module_that_starts_with_it() {
    let fake = ultimate(&["community/ls/foo/language-server.foo.tests.iml"], &["language-server.*"]);
    module_build(&fake, "community/ls/foo", "language-server.foo.tests", "foo_test");
    assert_eq!(
        resolve(&fake, "language-server.foo.tests"),
        one_label("@community//ls/foo:foo_test")
    );

    let list = MigratedList::parse(&list_text(&["language-server.*", "intellij.x.tests"]), ULTIMATE_LIST)
        .unwrap_or_else(|failure| panic!("{failure:?}"));
    assert!(list.contains("language-server.foo.tests"));
    assert!(list.contains("intellij.x.tests"));
    assert!(!list.contains("intellij.x.tests.more"), "a name without '*' matches only itself");
    assert!(!list.contains("intellij.language-server.foo"));
}

/// An area module runs under Bazel by definition, so the migrated list is not read for it.
#[test]
fn a_module_inside_an_area_resolves_without_the_migrated_list() {
    let fake = ultimate(
        &["plugins/air/backend/json/intellij.air.backend.json.tests.iml"],
        &["intellij.other.tests"],
    );
    module_build(
        &fake,
        "plugins/air/backend/json",
        "intellij.air.backend.json.tests",
        "air-backend-json-tests_test",
    );
    let resolution = resolve_module(&fake, areas(), "intellij.air.backend.json.tests").unwrap_or_else(|failure| panic!("{failure:?}"));
    assert_eq!(resolution, one_label("//plugins/air/backend/json:air-backend-json-tests_test"));
    let list_read = format!("read {REPO_ROOT}/{ULTIMATE_LIST}");
    assert!(!fake.reads().contains(&list_read), "{:?}", fake.reads());
}

#[test]
fn a_module_outside_the_migrated_list_and_every_area_is_refused_with_the_tests_cmd_command() {
    let fake = ultimate(&["goland/intellij-go-tests/intellij.goland.tests.iml"], &["intellij.x.tests"]);
    module_build(&fake, "goland/intellij-go-tests", "intellij.goland.tests", "go-tests_test");
    let failure = refusal(resolve_module(&fake, areas(), "intellij.goland.tests"));
    assert_eq!((failure.code.as_ref(), failure.exit), (MODULE_NOT_MIGRATED, USAGE));
    assert_eq!(
        failure.message,
        "the tests of module intellij.goland.tests do not run under Bazel yet \
         (community/build/bazel-migrated-test-modules.txt). Run: ./tests.cmd --module intellij.goland.tests --test <FQN>. \
         The label //goland/intellij-go-tests:go-tests_test still runs on its own: ./community/tools/bt.cmd \
         //goland/intellij-go-tests:go-tests_test --filter <FQN>"
    );
    let details: serde_json::Value =
        serde_json::from_str(failure.details_json_text().unwrap_or_default()).unwrap_or_else(|error| panic!("{error}"));
    assert_eq!(
        details,
        json!({
            "module": "intellij.goland.tests",
            "label": "//goland/intellij-go-tests:go-tests_test",
            "migratedList": ULTIMATE_LIST,
        })
    );
}

#[test]
fn a_refusal_in_a_community_checkout_names_its_own_wrapper() {
    let fake = FakeRuntime::new([]);
    fake.put(MODULES_FILE, &modules_xml(&["plugins/x/intellij.x.tests.iml"]));
    fake.put(COMMUNITY_LIST, &list_text(&["intellij.other.tests"]));
    module_build(&fake, "plugins/x", "intellij.x.tests", "x_test");
    let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x.tests"));
    assert_eq!(failure.code.as_ref(), MODULE_NOT_MIGRATED);
    assert!(
        failure.message.contains("(build/bazel-migrated-test-modules.txt)"),
        "{}",
        failure.message
    );
    assert!(
        failure.message.contains("./tools/bt.cmd //plugins/x:x_test --filter <FQN>"),
        "{}",
        failure.message
    );
}

#[test]
fn an_unknown_module_is_refused_with_the_nearest_names() {
    let fake = ultimate(&["community/plugins/x/intellij.x.tests.iml"], &["intellij.x.tests"]);
    let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x.test"));
    assert_eq!((failure.code.as_ref(), failure.exit), (MODULE_UNKNOWN, USAGE));
    assert_eq!(
        failure.message,
        "module intellij.x.test is not in .idea/modules.xml\n  did you mean  intellij.x.tests"
    );
}

/// Without exactly one unfiltered `jps_test` in the `BUILD.bazel`, the refusal names no label.
#[test]
fn a_module_without_its_own_jps_test_is_refused() {
    let fake = ultimate(
        &[
            "community/plugins/none/intellij.none.tests.iml",
            "community/plugins/plain/intellij.plain.tests.iml",
            "community/plugins/two/intellij.two.iml",
        ],
        &["intellij.*"],
    );
    let plain = "community/plugins/plain";
    fake.add_module(module_entry_json(
        plain,
        None,
        &[],
        Some(&[target_json(plain, "jvm_library", "plain", Some("intellij.plain.tests"), &[], &[])]),
    ));
    // The test target of the tests module, and a second unfiltered test target of another library.
    let two = "community/plugins/two";
    let mut entry: serde_json::Value =
        serde_json::from_str(&tests_module_json(two, "intellij.two", "intellij.two.tests", "two-tests_test")).expect("an entry");
    let again: serde_json::Value =
        serde_json::from_str(&target_json(two, "jps_test", "again_test", None, &[], &["other_test_lib"])).expect("a target");
    entry["bazel"]["targets"].as_array_mut().expect("the targets").push(again);
    fake.add_module(entry.to_string());
    for (module, reason) in [
        (
            "intellij.none.tests",
            "module intellij.none.tests has no test target: community/plugins/none/BUILD.bazel does not exist",
        ),
        (
            "intellij.plain.tests",
            "module intellij.plain.tests has no test target: community/plugins/plain/BUILD.bazel declares no jps_test that \
             runs the module",
        ),
        (
            "intellij.two",
            "module intellij.two has no test target: community/plugins/two/BUILD.bazel declares no jps_test that runs \
             the module",
        ),
    ] {
        let failure = refusal(resolve_module(&fake, &Areas::default(), module));
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            (MODULE_WITHOUT_TEST_TARGET, USAGE),
            "{module}"
        );
        assert_eq!(
            failure.message,
            format!("{reason}. Run: ./tests.cmd --module {module} --test <FQN>"),
            "{module}"
        );
        assert_eq!(failure.details_json_text(), None, "{module}");
    }
}

/// The production module of a directory must not run the tests of the test module beside it. The refusal names the
/// only `jps_test` of the directory, so the caller can run it by its label.
#[test]
fn a_refusal_names_the_only_jps_test_of_the_build_file() {
    let fake = ultimate(
        &["community/plugins/x/intellij.x.iml", "community/plugins/x/intellij.x.tests.iml"],
        &["intellij.*"],
    );
    module_build(&fake, "community/plugins/x", "intellij.x.tests", "x-tests_test");
    let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x"));
    assert_eq!((failure.code.as_ref(), failure.exit), (MODULE_WITHOUT_TEST_TARGET, USAGE));
    assert_eq!(
        failure.message,
        "module intellij.x has no test target: community/plugins/x/BUILD.bazel declares no jps_test that runs the \
         module. Run: ./tests.cmd --module intellij.x --test <FQN>. The only jps_test of community/plugins/x/BUILD.bazel \
         is @community//plugins/x:x-tests_test: ./community/tools/bt.cmd @community//plugins/x:x-tests_test --filter <FQN>"
    );
    let details: serde_json::Value =
        serde_json::from_str(failure.details_json_text().unwrap_or_default()).unwrap_or_else(|error| panic!("{error}"));
    assert_eq!(
        details,
        json!({ "module": "intellij.x", "label": "@community//plugins/x:x-tests_test" })
    );
}

#[test]
fn a_malformed_migrated_list_is_refused_with_its_file_and_line() {
    let fake = ultimate(&["community/plugins/x/intellij.x.tests.iml"], &[]);
    module_build(&fake, "community/plugins/x", "intellij.x.tests", "x_test");
    for (patterns, message) in [
        (
            ["intellij.a.tests", " intellij.x.tests"],
            "community/build/bazel-migrated-test-modules.txt:5: ' intellij.x.tests' is not a module name or a module \
             name prefix with '*' at the end",
        ),
        (
            ["intellij.*.tests", "intellij.x.tests"],
            "community/build/bazel-migrated-test-modules.txt:4: 'intellij.*.tests' is not a module name or a module \
             name prefix with '*' at the end",
        ),
        (
            ["intellij.x.tests", "intellij.x.tests"],
            "community/build/bazel-migrated-test-modules.txt:5: duplicate pattern 'intellij.x.tests'",
        ),
    ] {
        fake.put(ULTIMATE_LIST, &list_text(&patterns));
        let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x.tests"));
        assert_eq!((failure.code.as_ref(), failure.exit), ("bt_infra", INFRA), "{patterns:?}");
        assert_eq!(failure.message, message, "{patterns:?}");
    }
}

#[test]
fn a_missing_module_list_or_migrated_list_is_an_infra_refusal() {
    let fake = FakeRuntime::new([]);
    let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x.tests"));
    assert_eq!(failure.exit, INFRA);
    assert!(
        failure.message.starts_with(".idea/modules.xml is unreadable"),
        "{}",
        failure.message
    );

    let fake = FakeRuntime::new([]);
    fake.put("community/MODULE.bazel", "");
    fake.put(MODULES_FILE, &modules_xml(&["community/plugins/x/intellij.x.tests.iml"]));
    module_build(&fake, "community/plugins/x", "intellij.x.tests", "x_test");
    let failure = refusal(resolve_module(&fake, &Areas::default(), "intellij.x.tests"));
    assert_eq!(failure.exit, INFRA);
    assert!(
        failure.message.starts_with(&format!("{ULTIMATE_LIST} is unreadable")),
        "{}",
        failure.message
    );
}

/// The module root of `root` with the main module `intellij.m`, its library `m`, the test library `m_test_lib` of the
/// main module, and the test targets `targets`, each a name and whether it pins `JB_TEST_JUNIT5_FILTERS`.
fn entry_with_targets(root: &str, targets: &[(&str, bool)]) -> ModuleEntry {
    let mut rules: Vec<String> = targets
        .iter()
        .map(|(name, filtered)| {
            let env: &[&str] = if *filtered { &["JB_TEST_JUNIT5_FILTERS"] } else { &[] };
            target_json(root, "jps_test", name, None, env, &["m_test_lib"])
        })
        .collect();
    rules.push(target_json(root, "jvm_library", "m", Some("intellij.m"), &[], &[]));
    rules.push(target_json(root, "jvm_library", "m_test_lib", None, &[], &[]));
    serde_json::from_str(&module_entry_json(root, None, &[], Some(&rules))).expect("an entry")
}

/// A target runs the module through the `_test_lib` of a library of the module too. A filtered target counts when it
/// is the only one, an unfiltered target wins over it, and two unfiltered targets are infrastructure.
#[test]
fn the_test_target_of_a_module_follows_its_test_library_and_prefers_the_unfiltered_one() {
    let only_filtered = entry_with_targets("plugins/m", &[("m-budget_test", true)]);
    assert_eq!(module_test_target(&only_filtered, "intellij.m"), Ok(Some("m-budget_test")));
    let both = entry_with_targets("plugins/m", &[("m-budget_test", true), ("m_test", false)]);
    assert_eq!(module_test_target(&both, "intellij.m"), Ok(Some("m_test")));
    assert_eq!(module_test_target(&both, "intellij.other"), Ok(None));
    let two = entry_with_targets("plugins/m", &[("m_test", false), ("m-again_test", false)]);
    let failure = refusal(module_test_target(&two, "intellij.m"));
    assert_eq!(
        (failure.exit, failure.message.as_str()),
        (
            INFRA,
            "Expected one test target of module intellij.m in plugins/m/BUILD.bazel, found 2: m_test, m-again_test"
        )
    );
}
