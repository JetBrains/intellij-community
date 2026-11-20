use pretty_assertions::assert_eq;

use super::*;
use crate::fake::{FakeAccio, jps_json, module_entry_json, refusal, target_json, tests_module_json};

/// The `modules` entry of `community/platform/util` as accio answers it: three `.iml` files in one root, and the test
/// root `testSrc` of `intellij.platform.util.tests` beside two resource roots.
const UTIL_ENTRY: &str = r#"{
  "root": "community/platform/util",
  "jps": {
    "name": "intellij.platform.util",
    "manifest": "community/platform/util/intellij.platform.util.iml",
    "production_module": null,
    "roots": [{"path": "community/platform/util/src"}, {"path": "community/platform/util/resources", "kind": "java-resource"}],
    "test": null
  },
  "jps_more": [
    {
      "name": "intellij.platform.util.tests",
      "manifest": "community/platform/util/intellij.platform.util.tests.iml",
      "roots": [
        {"path": "community/platform/util/testData", "kind": "java-test-resource", "test": true},
        {"path": "community/platform/util/testResources", "kind": "java-test-resource", "test": true},
        {"path": "community/platform/util/testSrc", "test": true}
      ],
      "test": null
    },
    {
      "name": "intellij.platform.util.ui",
      "manifest": "community/platform/util/intellij.platform.util.ui.iml",
      "roots": [{"path": "community/platform/util/ui/src"}],
      "test": null
    }
  ],
  "bazel": {
    "build": "community/platform/util/BUILD.bazel",
    "targets": [
      {"name": "util-tests_test", "kind": "jps_test", "line": 5, "test": true,
       "deps": [{"label": ":util-tests_test_lib", "attr": "runtime_deps", "scope": "test",
                 "targets": [{"root": "community/platform/util", "name": "util-tests_test_lib"}]}]},
      {"name": null, "kind": "content_module_jar", "line": 20, "test": false},
      {"name": "util", "kind": "jvm_library", "line": 30, "test": false, "module_name": "intellij.platform.util"},
      {"name": "util-tests_test_lib", "kind": "jvm_library", "line": 90, "test": true, "module_name": "intellij.platform.util.tests"}
    ]
  }
}"#;

#[test]
fn a_modules_answer_names_the_test_module_of_a_file_among_several_iml_files() {
    let answer = parse_modules(&format!(
        r#"{{"total": 1, "complete": false, "note": "60 build file(s) not read whole", "modules": [{UTIL_ENTRY}],
            "paths": [{{"path": "community/platform/util/testSrc/a/FooTest.java", "root": "community/platform/util"}}]}}"#
    ))
    .expect("the answer parses");
    assert!(!answer.complete);
    let entry = answer.entry_of("community/platform/util/testSrc/a/FooTest.java").expect("the root");
    assert_eq!(
        entry.jps_modules().map(|module| module.name.as_str()).collect::<Vec<_>>(),
        [
            "intellij.platform.util",
            "intellij.platform.util.tests",
            "intellij.platform.util.ui"
        ]
    );
    let owner = entry.test_module_of("community/platform/util/testSrc/a/FooTest.java");
    assert_eq!(owner.map(|module| module.name.as_str()), Some("intellij.platform.util.tests"));
    // A resource root holds no test source, and a production root is no test root.
    assert_eq!(entry.test_module_of("community/platform/util/testData/a/Foo.java"), None);
    assert_eq!(entry.test_module_of("community/platform/util/src/a/Foo.java"), None);
    let test_target = &entry.targets()[0];
    assert_eq!(
        test_target.deps_of("runtime_deps").map(|dep| dep.name.as_str()).collect::<Vec<_>>(),
        ["util-tests_test_lib"]
    );
    assert!(!test_target.is_filtered());
}

/// Without `paths`, the longest module root above the path holds it.
#[test]
fn the_entry_of_a_path_is_the_longest_module_root_above_it() {
    let answer = ModulesAnswer {
        modules: vec![
            ModuleEntry {
                root: "plugins/air".to_owned(),
                ..ModuleEntry::default()
            },
            ModuleEntry {
                root: "plugins/air/shared".to_owned(),
                ..ModuleEntry::default()
            },
        ],
        ..ModulesAnswer::default()
    };
    let root = |path: &str| answer.entry_of(path).map(|entry| entry.root.as_str());
    assert_eq!(root("plugins/air/shared/A.kt"), Some("plugins/air/shared"));
    assert_eq!(root("plugins/air/sharedx/A.kt"), Some("plugins/air"));
    assert_eq!(root("plugins/other/A.kt"), None);
}

#[test]
fn a_target_that_pins_the_junit5_filters_is_filtered() {
    let target: BazelTarget = serde_json::from_str(&target_json(
        "plugins/x",
        "jps_test",
        "x-budget_test",
        None,
        &["JB_TEST_JUNIT5_FILTERS"],
        &["x-tests_test_lib"],
    ))
    .expect("a target");
    assert!(target.is_filtered());
}

#[test]
fn a_symbol_answer_is_one_object_per_name_and_keeps_the_fqn_of_a_nested_class() {
    let accio = FakeAccio {
        classes: vec![
            ("plugins/x/testSrc/OuterTest.kt".to_owned(), "a.b.OuterTest$Inner".to_owned()),
            ("plugins/x/testSrc/FooTest.kt".to_owned(), "a.b.FooTest".to_owned()),
        ],
        ..FakeAccio::default()
    };
    let text = accio
        .answer(&["symbol", "Inner", "FooTest", "Missing", "--kind", "class", "--limit", "200"])
        .expect("the model answers");
    let answers = parse_symbols(&text, &["Inner", "FooTest", "Missing"]).expect("the answer parses");
    assert_eq!(answers[0].hits[0].fqn.as_deref(), Some("a.b.OuterTest$Inner"));
    assert_eq!(answers[0].hits[0].top_level.as_deref(), Some("a.b.OuterTest"));
    assert_eq!(answers[1].hits[0].top_level, None);
    assert_eq!(answers[2].hits, Vec::new());
    assert_eq!(answers[2].note.as_deref(), Some("no declaration named Missing"));
}

#[test]
fn an_answer_without_an_index_or_of_another_length_is_refused() {
    let unavailable = refusal(parse_symbols(
        r#"[{"available": false, "note": "no index of this repository", "target": "Foo"}]"#,
        &["Foo"],
    ));
    assert_eq!((unavailable.code.as_ref(), unavailable.exit), (ACCIO_FAILED, INFRA));
    assert_eq!(
        unavailable.message,
        "accio symbol has no index of this checkout: no index of this repository"
    );
    let short = refusal(parse_symbols(r#"[{"complete": true, "hits": []}]"#, &["Foo", "Bar"]));
    assert_eq!(short.message, "accio symbol answered 1 objects for 2 names");
    let modules = refusal(parse_modules(r#"{"available": false, "note": "no index of this repository"}"#));
    assert_eq!(
        modules.message,
        "accio modules has no index of this checkout: no index of this repository"
    );
    assert!(
        refusal(parse_modules("not json"))
            .message
            .starts_with("accio modules printed an answer that bt cannot read")
    );
}

#[test]
fn the_fixture_module_roots_parse() {
    let usual: ModuleEntry =
        serde_json::from_str(&tests_module_json("plugins/x", "intellij.x", "intellij.x.tests", "x-tests_test")).expect("an entry");
    assert_eq!(
        usual
            .test_module_of("plugins/x/testSrc/FooTest.kt")
            .map(|module| module.name.as_str()),
        Some("intellij.x.tests")
    );
    let bare: ModuleEntry = serde_json::from_str(&module_entry_json(
        "plugins/y",
        Some(&jps_json("intellij.y", "plugins/y", &[("tests", true)], None)),
        &[],
        None,
    ))
    .expect("an entry");
    assert_eq!(bare.targets(), &[]);
    assert_eq!(
        bare.test_module_of("plugins/y/tests/A.kt").map(|module| module.name.as_str()),
        Some("intellij.y")
    );
}
