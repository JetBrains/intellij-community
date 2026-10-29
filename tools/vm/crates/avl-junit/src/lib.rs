//! The JUnit XML of a run, written from the run stream of its iterations: the file that Bazel and `bt` count.
//!
//! The controller and the guest agent both write it, so it is a crate of its own. It stands on `avl-wire`, `serde`,
//! `serde_json` and `jiff`, which the guest agent links already, and it does no file I/O. So it adds one crate to the
//! closure of the guest agent.
//!
//! The shape follows `community/build/rust-tools/libtest-junit-wrapper/src/junit.rs`. The document holds one
//! `testsuite` for each test class, in the order the stream first names it. A case is
//! `<testcase classname="<testClassName>" name="<scenario>" time>`, which is the name `<FQN>.<scenario>` of ADR 0128,
//! because the generator refuses a dot in a scenario name. A failure is `<failure message type>` with the message and
//! the detail as CDATA, and a skip is `<skipped message>`. A prologue failure is one case named after the suite.
//!
//! Two cases have no record of their own and still fail the document, so a cut-off run cannot count as green: a
//! scenario that started and never finished, and a class of the plan that the run never reached.
//!
//! [`teamcity`] writes one TeamCity service message, which the controller and `lane-run` both write. [`performance`] is
//! the third shared piece: the IJ Perf file of a scenario, which the controller and `lane-run` both write too.

use std::fmt::Write as _;

use avl_wire::daemon::RunEvent;

use crate::cases::{CaseFailure, CaseOutcome, FailureOrigin, StreamCase, UNFINISHED_MESSAGE};

pub mod cases;
pub mod performance;
pub mod teamcity;
#[cfg(test)]
mod testing;
#[cfg(test)]
mod tests;

/// The environment variable that names the XML of a Bazel test. Bazel sets it, and a test cannot override it.
pub const XML_OUTPUT_FILE: &str = "XML_OUTPUT_FILE";
/// The environment variable that names the XML in place of [`XML_OUTPUT_FILE`], as `JUnit5BazelRunner` reads it.
pub const JB_XML_OUTPUT_FILE: &str = "JB_XML_OUTPUT_FILE";

/// The failure type of a case that started and never finished.
pub const TYPE_UNFINISHED: &str = "unfinished";
/// The failure type of a class of the plan that the run never reached.
pub const TYPE_UNREPORTED: &str = "unreported";

/// Where the XML goes: [`JB_XML_OUTPUT_FILE`] when it is set and not blank, else [`XML_OUTPUT_FILE`], else nowhere.
pub fn destination(variable: impl Fn(&str) -> Option<String>) -> Option<String> {
    [JB_XML_OUTPUT_FILE, XML_OUTPUT_FILE]
        .into_iter()
        .find_map(|name| variable(name).filter(|value| !value.trim().is_empty()))
}

/// A case that fails the XML, with the failure that the XML writes for it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FailedCase {
    pub class_name: String,
    pub name: String,
    pub failure: CaseFailure,
}

/// Every case of the iteration that streamed `events` that fails its XML, in the order of the stream: a failed case,
/// a case that never finished, and then a class of the plan that the run never reached. `lane-run` names them in the
/// result of its run. A failed attempt that a later pass of the same class and name follows is no failed case, because
/// the rerun of `lane-run` passed it.
pub fn failed_cases(events: &[RunEvent]) -> Vec<FailedCase> {
    let mut found = cases::cases(events);
    found.extend(cases::unreported_classes(events).into_iter().map(unreported));
    let passed_later: Vec<bool> = (0..found.len())
        .map(|index| {
            found[index + 1..].iter().any(|later| {
                later.outcome == CaseOutcome::Passed && later.class_name == found[index].class_name && later.name == found[index].name
            })
        })
        .collect();
    found
        .into_iter()
        .zip(passed_later)
        .filter(|(_, passed_later)| !passed_later)
        .filter_map(|(case, _)| {
            let failure = match case.outcome {
                CaseOutcome::Failed(failure) => failure,
                CaseOutcome::Unfinished => unfinished(),
                CaseOutcome::Passed | CaseOutcome::Skipped(_) => return None,
            };
            Some(FailedCase {
                class_name: case.class_name,
                name: case.name,
                failure,
            })
        })
        .collect()
}

/// The XML of the run whose iterations streamed `iterations`, each the records of one `/run`.
pub fn render<'a>(iterations: impl IntoIterator<Item = &'a [RunEvent]>) -> String {
    let mut suites: Vec<Suite> = Vec::new();
    for events in iterations {
        let mut cases = cases::cases(events);
        cases.extend(cases::unreported_classes(events).into_iter().map(unreported));
        for case in cases {
            match suites.iter_mut().find(|suite| suite.name == case.class_name) {
                Some(suite) => suite.cases.push(case),
                None => suites.push(Suite {
                    name: case.class_name.clone(),
                    cases: vec![case],
                }),
            }
        }
    }
    let total = suites.iter().fold(Counts::default(), |total, suite| total.plus(&suite.counts()));
    let mut out = String::new();
    out.push_str("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    let _ = writeln!(out, "<testsuites {}>", total.attributes());
    for suite in &suites {
        let _ = writeln!(
            out,
            "  <testsuite name=\"{}\" {}>",
            escape(&suite.name),
            suite.counts().attributes()
        );
        for case in &suite.cases {
            write_case(&mut out, case);
        }
        out.push_str("  </testsuite>\n");
    }
    out.push_str("</testsuites>\n");
    out
}

struct Suite {
    name: String,
    cases: Vec<StreamCase>,
}

impl Suite {
    fn counts(&self) -> Counts {
        let mut counts = Counts::default();
        for case in &self.cases {
            counts.tests += 1;
            counts.millis += case.duration_ms;
            match case.outcome {
                CaseOutcome::Passed => {}
                CaseOutcome::Failed(_) | CaseOutcome::Unfinished => counts.failures += 1,
                CaseOutcome::Skipped(_) => counts.skipped += 1,
            }
        }
        counts
    }
}

#[derive(Default)]
struct Counts {
    tests: u64,
    failures: u64,
    skipped: u64,
    millis: i64,
}

impl Counts {
    const fn plus(&self, other: &Self) -> Self {
        Self {
            tests: self.tests + other.tests,
            failures: self.failures + other.failures,
            skipped: self.skipped + other.skipped,
            millis: self.millis + other.millis,
        }
    }

    fn attributes(&self) -> String {
        format!(
            r#"tests="{}" failures="{}" errors="0" skipped="{}" time="{}""#,
            self.tests,
            self.failures,
            self.skipped,
            seconds(self.millis)
        )
    }
}

/// The case of a class that the plan named and the run never reached.
fn unreported(class_name: String) -> StreamCase {
    StreamCase {
        name: class_name.clone(),
        started_at: String::new(),
        duration_ms: 0,
        outcome: CaseOutcome::Failed(CaseFailure {
            kind: FailureOrigin::Container,
            r#type: Some(TYPE_UNREPORTED.to_owned()),
            message: format!("the run never reached the suite {class_name}: the run stream ended before its first record"),
            detail: String::new(),
        }),
        class_name,
        report: avl_wire::report::ScenarioReport::default(),
    }
}

fn write_case(out: &mut String, case: &StreamCase) {
    let head = format!(
        r#"    <testcase classname="{}" name="{}" time="{}""#,
        escape(&case.class_name),
        escape(&case.name),
        seconds(case.duration_ms)
    );
    match &case.outcome {
        CaseOutcome::Passed => {
            let _ = writeln!(out, "{head}/>");
        }
        CaseOutcome::Skipped(reason) => {
            let _ = writeln!(out, "{head}>\n      <skipped message=\"{}\"/>\n    </testcase>", escape(reason));
        }
        CaseOutcome::Failed(failure) => write_failure(out, &head, failure),
        CaseOutcome::Unfinished => write_failure(out, &head, &unfinished()),
    }
}

/// The failure of a case that started and never finished.
fn unfinished() -> CaseFailure {
    CaseFailure {
        kind: FailureOrigin::Test,
        r#type: Some(TYPE_UNFINISHED.to_owned()),
        message: UNFINISHED_MESSAGE.to_owned(),
        detail: String::new(),
    }
}

fn write_failure(out: &mut String, head: &str, failure: &CaseFailure) {
    let kind = failure
        .r#type
        .as_deref()
        .map(|kind| format!(r#" type="{}""#, escape(kind)))
        .unwrap_or_default();
    let _ = writeln!(
        out,
        "{head}>\n      <failure message=\"{}\"{kind}><![CDATA[{}]]></failure>\n    </testcase>",
        escape(failure.headline()),
        cdata(&failure.text())
    );
}

/// Milliseconds as the seconds JUnit writes.
fn seconds(millis: i64) -> String {
    let millis = millis.max(0);
    format!("{}.{:03}", millis / 1000, millis % 1000)
}

/// Whether XML 1.0 admits `character` at all. It admits a tab, a newline and a carriage return of the control
/// characters, and it forbids `U+FFFE` and `U+FFFF`.
const fn admitted(character: char) -> bool {
    matches!(character, '\t' | '\n' | '\r') || !(character.is_control() || matches!(character, '\u{fffe}' | '\u{ffff}'))
}

/// Escapes an attribute value, and drops the characters XML forbids. Every attribute is written between double
/// quotes.
fn escape(value: &str) -> String {
    let mut escaped = String::with_capacity(value.len());
    for character in value.chars().filter(|character| admitted(*character)) {
        match character {
            '&' => escaped.push_str("&amp;"),
            '<' => escaped.push_str("&lt;"),
            '>' => escaped.push_str("&gt;"),
            '"' => escaped.push_str("&quot;"),
            '\n' => escaped.push_str("&#10;"),
            '\r' => escaped.push_str("&#13;"),
            '\t' => escaped.push_str("&#9;"),
            _ => escaped.push(character),
        }
    }
    escaped
}

/// The body of a CDATA section: the forbidden characters dropped, and each `]]>` split across two sections.
fn cdata(value: &str) -> String {
    value
        .chars()
        .filter(|character| admitted(*character))
        .collect::<String>()
        .replace("]]>", "]]]]><![CDATA[>")
}
