#![expect(
    clippy::float_cmp,
    reason = "each time is parsed from a short decimal literal and compared with the same literal"
)]

use pretty_assertions::assert_eq;
use proptest::prelude::*;

use super::*;

fn names(suites: &[Suite]) -> Vec<&str> {
    suites.iter().map(|suite| suite.name.as_str()).collect()
}

#[test]
fn a_complete_document_is_complete() {
    let report = parse_report(
        r#"<?xml version="1.0"?>
<testsuites>
  <testsuite name="A" timestamp="2026-08-22T10:00:00Z" tests="2" failures="1" errors="0" skipped="0" time="1.5">
    <testcase classname="p.A" name="ok" time="0.5"/>
    <testcase classname="p.A" name="bad" time="1.0"><failure message="boom" type="java.lang.AssertionError">at p.A.bad</failure></testcase>
  </testsuite>
</testsuites>"#,
    );
    assert_eq!(
        report.integrity,
        Integrity {
            status: IntegrityStatus::Complete,
            message: None
        }
    );
    assert!(report.integrity.is_complete());
    let [suite] = report.suites.as_slice() else {
        panic!("read {} suites", report.suites.len())
    };
    assert_eq!((suite.tests, suite.failures, suite.time_seconds), (2, 1, 1.5));
    assert_eq!(suite.cases.len(), 2);
    assert_eq!(suite.cases[0].outcome, Outcome::Passed);
    assert_eq!(suite.cases[0].time_seconds, 0.5);
    let failed = &suite.cases[1];
    assert_eq!(failed.outcome, Outcome::Failed);
    assert_eq!(
        failed.failure,
        Some(CaseFailure {
            kind: FailureKind::Failure,
            r#type: Some("java.lang.AssertionError".to_owned()),
            message: Some("boom".to_owned()),
            detail: "at p.A.bad".to_owned(),
        })
    );
}

// The reason this is a scanner. `system-out` carries the captured platform log, which routinely contains text
// that looks like XML - including a whole second document, a bare `<` and an unbalanced tag.
#[test]
fn captured_output_is_never_interpreted() {
    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="1">"#,
        r#"<testcase classname="p.A" name="ok"/>"#,
        r#"<system-out>&lt;testsuite name="NOT REAL"&gt;<testcase classname="x" name="y"/> a < b <init></system-out>"#,
        r#"<system-err><testsuite name="ALSO NOT REAL"></system-err>"#,
        r#"</testsuite></testsuites>"#,
    ));
    assert_eq!(report.integrity.status, IntegrityStatus::Complete, "{:?}", report.integrity);
    assert_eq!(names(&report.suites), ["A"]);
    assert_eq!(report.suites[0].cases.len(), 1, "captured output leaked into the structure");
}

// A `>` inside an attribute value is legal, and assertion messages reach the XML carrying one - and a raw `<`.
#[test]
fn a_greater_than_inside_an_attribute_does_not_end_the_tag() {
    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="1">"#,
        r#"<testcase classname="p.A" name="t"><failure message="expected: &lt;&quot;/a&quot;> but was: <&quot;/b&quot;>">d</failure></testcase>"#,
        r#"</testsuite></testsuites>"#,
    ));
    assert_eq!(report.integrity.status, IntegrityStatus::Complete, "{:?}", report.integrity);
    let failure = report.suites[0].cases[0].failure.as_ref().expect("the failure was dropped");
    assert_eq!(failure.message.as_deref(), Some(r#"expected: <"/a"> but was: <"/b">"#));
}

#[test]
fn a_truncated_document_keeps_what_it_got() {
    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="2">"#,
        r#"<testcase classname="p.A" name="ok"/>"#,
        r#"<testcase classname="p.A" name="cut"#,
    ));
    assert_eq!(report.integrity.status, IntegrityStatus::Truncated);
    assert!(!report.integrity.is_complete());
    assert_eq!(names(&report.suites), ["A"]);
    assert_eq!(report.suites[0].cases.len(), 1, "a truncated suite lost its finished case");
}

// `str::trim` does not strip a byte-order mark; a document made only of them is still empty.
#[test]
fn an_empty_document_is_empty_and_not_malformed() {
    for blank in ["", "   ", "\n\t", "\u{feff}", "\u{feff}\u{feff}"] {
        let report = parse_report(blank);
        assert_eq!(report.integrity.status, IntegrityStatus::Empty, "{blank:?}");
        assert!(report.suites.is_empty());
    }
}

#[test]
fn a_missing_root_is_malformed() {
    let report = parse_report(r#"<testsuite name="A" tests="0"></testsuite>"#);
    assert_eq!(report.integrity.status, IntegrityStatus::Malformed, "{:?}", report.integrity);
    assert_eq!(
        report.integrity.message.as_deref(),
        Some("<testsuite> lies outside the <testsuites> root")
    );
}

// Truncation explains its own structural oddities, so it wins the verdict.
#[test]
fn truncation_outranks_malformedness() {
    let report = parse_report(r#"<testsuite name="A" tests="0"><testcase classname="c" name="n""#);
    assert_eq!(report.integrity.status, IntegrityStatus::Truncated, "{:?}", report.integrity);
}

// Entities resolve in one pass: `&amp;lt;` is the text `&lt;` and never a `<`, which would turn escaped markup
// into markup inside a failure message a reader is shown. A numeric reference above U+FFFF is the code point it
// names, and text that is not well-formed is kept as written.
#[test]
fn ampersand_is_resolved_last() {
    assert_eq!(unescape("&amp;lt;"), "&lt;");
    assert_eq!(
        unescape("&lt;a&gt; &quot;b&quot; &apos;c&apos; &amp; &#65; &#x42;"),
        r#"<a> "b" 'c' & A B"#
    );
    assert_eq!(unescape("&#128512;"), "\u{1F600}");
    assert_eq!(unescape("fish & chips"), "fish & chips");

    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A &amp;amp; B" tests="1"><testcase classname="p.A" name="t">"#,
        r#"<failure message="&amp;lt;init&amp;gt;">x &lt; y</failure></testcase></testsuite></testsuites>"#,
    ));
    assert_eq!(report.suites[0].name, "A &amp; B");
    let failure = report.suites[0].cases[0].failure.as_ref().unwrap();
    assert_eq!(
        (failure.message.as_deref(), failure.detail.as_str()),
        (Some("&lt;init&gt;"), "x < y")
    );
}

// Counts and times read plainly: surrounding whitespace is ignored, and anything that is not a number is zero.
#[test]
fn number_attributes_read_plainly() {
    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests=" 12 " failures="3" errors="lots" skipped="1.5" time="1.5">"#,
        r#"<testcase classname="p.A" name="t" time="nan"/></testsuite></testsuites>"#,
    ));
    let suite = &report.suites[0];
    assert_eq!(
        (suite.tests, suite.failures, suite.errors, suite.skipped, suite.time_seconds),
        (12, 3, 0, 0, 1.5)
    );
    assert_eq!(suite.cases[0].time_seconds, 0.0);
}

// Suites are ordered by instant and not by timestamp text: `10:00+02:00` is earlier than `09:00Z`.
#[test]
fn suites_are_ordered_by_instant_then_document_position() {
    let report = parse_report(concat!(
        "<testsuites>",
        r#"<testsuite name="second" timestamp="2026-08-22T09:00:00Z" tests="0"/>"#,
        r#"<testsuite name="first" timestamp="2026-08-22T10:00:00+02:00" tests="0"/>"#,
        r#"<testsuite name="undated" tests="0"/>"#,
        r#"<testsuite name="undated-too" timestamp="not a timestamp" tests="0"/>"#,
        "</testsuites>",
    ));
    assert_eq!(names(&report.suites), ["first", "second", "undated", "undated-too"]);
    assert_eq!(report.suites[0].document_index, 1, "sorting lost the document position");
}

#[test]
fn equal_timestamps_keep_document_order() {
    let report = parse_report(concat!(
        "<testsuites>",
        r#"<testsuite name="a" timestamp="2026-08-22T09:00:00Z" tests="0"/>"#,
        r#"<testsuite name="b" timestamp="2026-08-22T09:00:00Z" tests="0"/>"#,
        r#"<testsuite name="c" timestamp="2026-08-22T09:00:00Z" tests="0"/>"#,
        "</testsuites>",
    ));
    assert_eq!(names(&report.suites), ["a", "b", "c"]);
}

#[test]
fn parse_keeps_document_order() {
    let suites = parse(concat!(
        "<testsuites>",
        r#"<testsuite name="late" timestamp="2026-08-22T11:00:00Z" tests="0"/>"#,
        r#"<testsuite name="early" timestamp="2026-08-22T09:00:00Z" tests="0"/>"#,
        "</testsuites>",
    ));
    assert_eq!(names(&suites), ["late", "early"]);
}

#[test]
fn the_bucketing_stub_is_recognized() {
    let stub = parse_report(r#"<testsuites><testsuite name="Bucketing" tests="0"/></testsuites>"#);
    assert!(stub.suites[0].bucketing_stub, "the bucketing stub was not recognized");
    let other = parse_report(r#"<testsuites><testsuite name="Bucketing" tests="1"/></testsuites>"#);
    assert!(
        !other.suites[0].bucketing_stub,
        "a suite that ran something was called a bucketing stub"
    );
}

// A wrapper that reported 0 and no wrapper at all are different facts, and the second is the common one.
#[test]
fn the_wrapper_exit_code_is_absent_until_a_wrapper_reports_one() {
    let plain = parse_report(r#"<testsuites><testsuite name="A" tests="1"><testcase classname="p.A" name="t"/></testsuite></testsuites>"#);
    assert_eq!(plain.suites[0].wrapper_exit_code, None);
    let wrapped = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="0">"#,
        r#"<testcase name="w"><failure message="exited with error code 0">x</failure></testcase>"#,
        r#"</testsuite></testsuites>"#,
    ));
    assert_eq!(wrapped.suites[0].wrapper_exit_code, Some(0));
    let crashed = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="0">"#,
        r#"<testcase name="w"><error message="Test runner exited with error code 42"/></testcase>"#,
        r#"</testsuite></testsuites>"#,
    ));
    assert_eq!(crashed.suites[0].wrapper_exit_code, Some(42));
    assert_eq!(
        crashed.suites[0].cases[0].failure.as_ref().map(|failure| failure.kind),
        Some(FailureKind::Error)
    );
}

#[test]
fn skipped_and_its_reason_are_read() {
    let report = parse_report(concat!(
        r#"<testsuites><testsuite name="A" tests="1" skipped="1">"#,
        r#"<testcase classname="p.A" name="t"><skipped>a condition ruled it out</skipped></testcase>"#,
        r#"</testsuite></testsuites>"#,
    ));
    assert_eq!(report.integrity.status, IntegrityStatus::Complete);
    assert_eq!(report.suites[0].cases[0].outcome, Outcome::Skipped);
    assert_eq!(report.suites[0].skipped, 1);
}

#[test]
fn cdata_and_comments_are_skipped_wholesale() {
    let report = parse_report(concat!(
        r#"<testsuites><!-- <testsuite name="fake" tests="9"/> -->"#,
        r#"<testsuite name="A" tests="1"><testcase classname="p.A" name="t">"#,
        r#"<failure message="m"><![CDATA[at p.A.t(<A.java>:1) </failure>]]></failure></testcase></testsuite></testsuites>"#,
    ));
    assert_eq!(report.integrity.status, IntegrityStatus::Complete, "{:?}", report.integrity);
    assert_eq!(names(&report.suites), ["A"], "a comment produced a suite");
    let failure = report.suites[0].cases[0].failure.as_ref().unwrap();
    assert_eq!(failure.detail, "at p.A.t(<A.java>:1) </failure>");
}

const DOCUMENT: &str = concat!(
    r#"<?xml version="1.0" encoding="UTF-8"?><testsuites>"#,
    r#"<testsuite name="A" timestamp="2026-08-22T10:00:00Z" tests="3" failures="1" skipped="1" time="2.5">"#,
    r#"<testcase classname="p.A" name="ok" time="0.5"/>"#,
    r#"<testcase classname="p.A" name="bad" time="1"><failure message="exp &lt;1&gt;" type="E"><![CDATA[at p.A]]> &amp; more</failure>"#,
    r#"<system-out>log <init> é 😀</system-out></testcase>"#,
    r#"<testcase classname="p.A" name="skip"><skipped/></testcase><!-- note -->"#,
    r#"</testsuite><testsuite name="B"><testcase classname="p.B" name="t"><error message="x"/></testcase></testsuite>"#,
    "</testsuites>",
);

proptest! {
    // A killed JVM cuts the file anywhere. Every prefix reads without panicking, never gains a case the whole
    // document lacks, and is complete only when it is the whole document.
    #[test]
    fn every_prefix_of_a_document_reads(cut in 0usize..=DOCUMENT.len()) {
        prop_assume!(DOCUMENT.is_char_boundary(cut));
        let whole = parse_report(DOCUMENT);
        prop_assert_eq!(whole.integrity.status, IntegrityStatus::Complete);
        let prefix = parse_report(&DOCUMENT[..cut]);
        let cases = |report: &Report| report.suites.iter().map(|suite| suite.cases.len()).sum::<usize>();
        prop_assert!(cases(&prefix) <= cases(&whole));
        if cut < DOCUMENT.len() {
            prop_assert_ne!(prefix.integrity.status, IntegrityStatus::Complete);
        }
    }

    // Arbitrary text never panics the reader.
    #[test]
    fn arbitrary_text_never_panics(text in "(<|>|/|\"|'|&|;|!|\\[|\\]|-|testsuites?|testcase|failure|system-out|CDATA| |=|a|é)*") {
        let _ = parse_report(&text);
    }
}

#[test]
fn a_simple_class_name_pattern_accepts_any_package_and_nothing_longer() {
    let pattern = regex::Regex::new(&simple_class_name_pattern("AirSmokeTest")).unwrap();
    assert_eq!(pattern.as_str(), r"(^|.*\.)AirSmokeTest$");
    for matched in ["AirSmokeTest", "com.example.AirSmokeTest"] {
        assert!(pattern.is_match(matched), "{matched}");
    }
    for unmatched in ["com.example.NotAirSmokeTest", "com.example.AirSmokeTestKt"] {
        assert!(!pattern.is_match(unmatched), "{unmatched}");
    }
}
