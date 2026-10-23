// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{Case, junit_report, root_of, xml_text};
use std::path::{Path, PathBuf};

#[test]
fn the_root_is_the_resolved_path_without_the_repository_path() {
    let root = root_of(Path::new("/c/idea/plugins/air/.jscpd.json"), "plugins/air/.jscpd.json").expect("a root");
    assert_eq!(root, PathBuf::from("/c/idea"));
    let error = root_of(Path::new("/c/idea/other/.jscpd.json"), "plugins/air/.jscpd.json").expect_err("no suffix");
    assert_eq!(
        error.to_string(),
        "/c/idea/other/.jscpd.json does not end with plugins/air/.jscpd.json"
    );
}

#[test]
fn the_report_holds_both_cases_under_one_class() {
    let cases = [
        Case {
            name: "airHasNoCopyPasteClone".to_owned(),
            failure: Some("jscpd found 1 copy-paste clones.\na.rs:1-5 ~ b.rs:2-6".to_owned()),
        },
        Case {
            name: "everyCloneMarkerStatesItsReasonAndEnds".to_owned(),
            failure: None,
        },
    ];
    let expected = r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuites tests="2" failures="1">
  <testsuite name="a.B" tests="2" failures="1">
    <testcase classname="a.B" name="airHasNoCopyPasteClone">
      <failure message="jscpd found 1 copy-paste clones.">jscpd found 1 copy-paste clones.
a.rs:1-5 ~ b.rs:2-6</failure>
    </testcase>
    <testcase classname="a.B" name="everyCloneMarkerStatesItsReasonAndEnds"/>
  </testsuite>
</testsuites>
"#;
    assert_eq!(junit_report("a.B", &cases), expected);
}

#[test]
fn xml_text_escapes_markup_and_drops_control_characters() {
    assert_eq!(xml_text("a<b>&\"c'\u{1b}[0m\td"), "a&lt;b&gt;&amp;&quot;c&apos;[0m\td");
}
