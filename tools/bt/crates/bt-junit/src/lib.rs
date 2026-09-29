//! The JUnit XML reader, as a scanner rather than a parser.
//!
//! Why not a DOM, or a streaming XML parser: `system-out` and `system-err` carry the captured platform log,
//! which embeds arbitrary text that looks like XML (`a < b`, `<init>`, a whole second document), and the file is
//! routinely truncated because the JVM that was writing it was killed. So this reads tags in one pass, skips
//! those bodies wholesale, keeps whatever partial results it got, and reports whether the document was complete
//! enough to be authoritative. A truncated document is the normal case for the runs this reader exists to
//! explain. The tag boundaries and nesting rules are ours; attributes and entities are quick-xml's.
//!
//! `bt` and the Air UI-lane tooling read documents through this crate. The lane tooling links it through a path
//! dependency and re-exports it as `avl_wire::junit`, so `avl_wire::report`, the host's report builder and
//! aggregates read the same documents. [`simple_class_name_pattern`] is the class-name filter `bt` and the
//! controller's `/run` request both build, so it is spelled here once.

use std::borrow::Cow;
use std::cmp::Ordering;

use quick_xml::events::attributes::Attributes;

#[cfg(test)]
mod tests;

/// Which element carried a failure: JUnit spells a thrown assertion and a thrown error differently, and the
/// report quotes the distinction.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FailureKind {
    Failure,
    Error,
}

impl FailureKind {
    const fn element(self) -> &'static str {
        match self {
            Self::Failure => "failure",
            Self::Error => "error",
        }
    }
}

/// What one test case did.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Outcome {
    Passed,
    Failed,
    Skipped,
}

/// The failure element of a failed case.
///
/// `type` and `message` are optional because absence is meaningful and different from emptiness: a failure with
/// no `message` attribute and one whose message is the empty string are different documents, and the report's
/// fallback chain reads one as "look at the detail instead" and the other as an empty message.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CaseFailure {
    pub kind: FailureKind,
    pub r#type: Option<String>,
    pub message: Option<String>,
    pub detail: String,
}

/// One `<testcase>`.
#[derive(Clone, Debug, PartialEq)]
pub struct TestCase {
    pub class_name: String,
    pub name: String,
    pub outcome: Outcome,
    pub failure: Option<CaseFailure>,
    pub time_seconds: f64,
}

/// One `<testsuite>`.
#[derive(Clone, Debug, PartialEq)]
pub struct Suite {
    pub name: String,
    pub timestamp: Option<String>,
    /// The suite's own `time` attribute, in seconds.
    pub time_seconds: f64,
    /// Zero-based position in the document, retained after report-oriented timestamp sorting.
    pub document_index: usize,
    pub tests: u32,
    pub failures: u32,
    pub errors: u32,
    pub skipped: u32,
    pub cases: Vec<TestCase>,
    /// The "all tests filtered out by bucketing" stub a non-matching shard writes.
    pub bucketing_stub: bool,
    /// Bazel's synthesized wrapper suite carries the runner's raw exit code. Exit code 0 from a wrapper that did
    /// report one is not the same as no wrapper suite at all, and the second is the common case.
    pub wrapper_exit_code: Option<i32>,
}

/// How much of the document can be trusted.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum IntegrityStatus {
    Complete,
    Empty,
    Truncated,
    Malformed,
}

impl IntegrityStatus {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Complete => "complete",
            Self::Empty => "empty",
            Self::Truncated => "truncated",
            Self::Malformed => "malformed",
        }
    }
}

/// The verdict on the document itself, which decides whether the XML or the daemon's progress stream owns a
/// run's failures.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Integrity {
    pub status: IntegrityStatus,
    /// The first problem, for every status but complete.
    pub message: Option<String>,
}

impl Integrity {
    pub fn is_complete(&self) -> bool {
        self.status == IntegrityStatus::Complete
    }
}

/// The reader's answer: suites in chronological order, plus what can be trusted about them.
#[derive(Clone, Debug, PartialEq)]
pub struct Report {
    pub suites: Vec<Suite>,
    pub integrity: Integrity,
}

/// Tolerant partial suites in their document order.
///
/// Discards the integrity verdict on purpose: `bt` wants whatever cases the document held, and a truncated file
/// is the normal outcome of the runs it inspects.
pub fn parse(xml: &str) -> Vec<Suite> {
    scan(xml).0
}

/// Integrity metadata plus suites in stable timestamp order ([`compare_suites_by_timestamp`]).
pub fn parse_report(xml: &str) -> Report {
    let (mut suites, integrity) = scan(xml);
    // `sort_by` is stable, which is what keeps equal instants in document order.
    suites.sort_by(compare_suites_by_timestamp);
    Report { suites, integrity }
}

/// The stable chronological order reports are published in.
///
/// Timestamps are compared as *instants*, so `...T10:00:00+02:00` sorts before `...T09:00:00Z` even though the
/// text says otherwise. Suites with a missing or unparseable timestamp keep document order, last.
pub fn compare_suites_by_timestamp(left: &Suite, right: &Suite) -> Ordering {
    match (instant_millis(left), instant_millis(right)) {
        (Some(left_millis), Some(right_millis)) => left_millis.cmp(&right_millis),
        (Some(_), None) => Ordering::Less,
        (None, Some(_)) => Ordering::Greater,
        (None, None) => Ordering::Equal,
    }
    .then(left.document_index.cmp(&right.document_index))
}

fn instant_millis(suite: &Suite) -> Option<i64> {
    let timestamp: jiff::Timestamp = suite.timestamp.as_deref()?.parse().ok()?;
    Some(timestamp.as_millisecond())
}

// --- the scan ---------------------------------------------------------------------------------------------

#[derive(Default)]
struct Scanner {
    suites: Vec<Suite>,
    current: Option<Suite>,
    pending: Option<TestCase>,
    root_open: bool,
    root_closed: bool,
    malformed: Option<String>,
    truncated: Option<String>,
}

impl Scanner {
    fn note_malformed(&mut self, message: impl Into<String>) {
        self.malformed.get_or_insert_with(|| message.into());
    }

    fn note_truncated(&mut self, message: impl Into<String>) {
        self.truncated.get_or_insert_with(|| message.into());
    }

    fn finish_suite(&mut self) {
        if let Some(mut suite) = self.current.take() {
            suite.bucketing_stub = suite.name == "Bucketing" && suite.tests == 0;
            self.suites.push(suite);
        }
    }

    /// Files a case into the open suite; a case outside any suite is dropped.
    fn finish_case(&mut self, case: TestCase) {
        if let Some(suite) = &mut self.current {
            suite.cases.push(case);
        }
    }

    fn finish_pending(&mut self) {
        if let Some(case) = self.pending.take() {
            self.finish_case(case);
        }
    }

    fn integrity(&self) -> Integrity {
        // Truncation wins over malformedness when both were seen: a document that was cut off explains its own
        // structural oddities, and reporting it as malformed would send a reader looking for a writer bug.
        if let Some(message) = &self.truncated {
            Integrity {
                status: IntegrityStatus::Truncated,
                message: Some(message.clone()),
            }
        } else if let Some(message) = &self.malformed {
            Integrity {
                status: IntegrityStatus::Malformed,
                message: Some(message.clone()),
            }
        } else {
            Integrity {
                status: IntegrityStatus::Complete,
                message: None,
            }
        }
    }
}

/// One tag, between `<` and `>`.
struct Tag<'a> {
    closing: bool,
    name: &'a str,
    /// Everything between `<` and `>` without a closing slash: the name, then the attributes.
    body: &'a str,
    self_closing: bool,
}

impl<'a> Tag<'a> {
    fn parse(tag: &'a str) -> Self {
        let inner = &tag[1..tag.len() - 1];
        let (closing, inner) = match inner.strip_prefix('/') {
            Some(rest) => (true, rest),
            None => (false, inner),
        };
        let trimmed = inner.trim_end();
        let (self_closing, body) = match trimmed.strip_suffix('/') {
            Some(rest) => (true, rest),
            None => (false, inner),
        };
        let name_end = body
            .find(|char: char| char.is_whitespace() || char == '/')
            .unwrap_or(body.len());
        Tag {
            closing,
            name: &body[..name_end],
            body,
            self_closing,
        }
    }

    fn opens(&self, name: &str) -> bool {
        !self.closing && self.name == name
    }

    fn closes(&self, name: &str) -> bool {
        self.closing && self.name == name
    }

    /// The unescaped value of every well-formed attribute; the last of duplicates wins, which only decides what
    /// a broken document says.
    fn attribute(&self, name: &str) -> Option<String> {
        let mut attributes = Attributes::new(self.body, self.name.len());
        attributes.with_checks(false);
        attributes
            .filter_map(Result::ok)
            .filter(|attribute| attribute.key.as_ref() == name)
            .last()
            .map(|attribute| unescape(&attribute.value).into_owned())
    }

    fn count(&self, name: &str) -> u32 {
        self.attribute(name)
            .and_then(|value| value.trim().parse().ok())
            .unwrap_or(0)
    }

    fn seconds(&self, name: &str) -> f64 {
        self.attribute(name)
            .and_then(|value| value.trim().parse::<f64>().ok())
            .filter(|value| value.is_finite())
            .unwrap_or(0.0)
    }
}

fn scan(xml: &str) -> (Vec<Suite>, Integrity) {
    // `str::trim` does not strip a byte-order mark; a document of nothing else is still empty.
    if xml
        .trim_matches(|char: char| char.is_whitespace() || char == '\u{feff}')
        .is_empty()
    {
        return (
            Vec::new(),
            Integrity {
                status: IntegrityStatus::Empty,
                message: Some("JUnit XML is empty".to_owned()),
            },
        );
    }

    let mut state = Scanner::default();
    let mut index = 0;
    while let Some(next) = xml[index..].find('<') {
        index += next;

        if let Some(skip) = skip_opaque(xml, index) {
            if let Some(description) = skip.unterminated {
                state.note_truncated(format!("unterminated {description}"));
            }
            index = skip.end;
            continue;
        }

        let Some(tag_end) = find_tag_end(xml, index) else {
            state.note_truncated("unterminated XML tag");
            break;
        };
        let tag = Tag::parse(&xml[index..=tag_end]);
        index = tag_end + 1;

        if tag.opens("testsuites") {
            if state.root_closed {
                state.note_malformed("content follows the closed <testsuites> root");
            } else if state.root_open {
                state.note_malformed("nested <testsuites> root");
            }
            state.root_open = true;
            if tag.self_closing {
                state.root_closed = true;
            }
        } else if tag.closes("testsuites") {
            if !state.root_open {
                state.note_malformed("</testsuites> appears before its root");
            }
            if state.root_closed {
                state.note_malformed("duplicate </testsuites> root close");
            }
            if state.pending.is_some() {
                state.note_malformed("the document closes while a <testcase> is open");
                state.finish_pending();
            }
            if state.current.is_some() {
                state.note_malformed("the document closes while a <testsuite> is open");
                state.finish_suite();
            }
            state.root_closed = true;
        } else if tag.opens("testsuite") {
            if !state.root_open || state.root_closed {
                state.note_malformed("<testsuite> lies outside the <testsuites> root");
            }
            if state.pending.is_some() {
                state.note_malformed("a new <testsuite> starts while a <testcase> is open");
                state.finish_pending();
            }
            if state.current.is_some() {
                state.note_malformed("nested <testsuite> element");
                state.finish_suite();
            }
            state.current = Some(Suite {
                name: tag.attribute("name").unwrap_or_default(),
                timestamp: tag.attribute("timestamp"),
                time_seconds: tag.seconds("time"),
                document_index: state.suites.len(),
                tests: tag.count("tests"),
                failures: tag.count("failures"),
                errors: tag.count("errors"),
                skipped: tag.count("skipped"),
                cases: Vec::new(),
                bucketing_stub: false,
                wrapper_exit_code: None,
            });
            if tag.self_closing {
                state.finish_suite();
            }
        } else if tag.closes("testsuite") {
            if state.current.is_none() {
                state.note_malformed("</testsuite> appears without an open suite");
                continue;
            }
            if state.pending.is_some() {
                state.note_malformed("</testsuite> appears while a <testcase> is open");
                state.finish_pending();
            }
            state.finish_suite();
        } else if tag.opens("testcase") {
            if state.current.is_none() {
                state.note_malformed("<testcase> appears outside a suite");
            }
            let case = TestCase {
                class_name: tag.attribute("classname").unwrap_or_default(),
                name: tag.attribute("name").unwrap_or_default(),
                outcome: Outcome::Passed,
                failure: None,
                time_seconds: tag.seconds("time"),
            };
            if state.pending.is_some() {
                state.note_malformed("nested <testcase> element");
                state.finish_pending();
            }
            if tag.self_closing {
                state.finish_case(case);
            } else {
                state.pending = Some(case);
            }
        } else if tag.closes("testcase") {
            if state.pending.is_none() {
                state.note_malformed("</testcase> appears without an open case");
                continue;
            }
            state.finish_pending();
        } else if tag.opens("skipped") {
            if !tag.self_closing {
                let text = element_text(xml, index, "skipped");
                index = text.end;
                if !text.complete {
                    state.note_truncated("unterminated <skipped> element");
                }
            }
            match &mut state.pending {
                Some(case) => case.outcome = Outcome::Skipped,
                None => state.note_malformed("<skipped> appears outside a test case"),
            }
        } else if tag.opens("failure") || tag.opens("error") {
            let kind = if tag.name == "failure" {
                FailureKind::Failure
            } else {
                FailureKind::Error
            };
            let mut detail = String::new();
            if !tag.self_closing {
                let text = element_text(xml, index, kind.element());
                index = text.end;
                detail = text.content;
                if !text.complete {
                    state.note_truncated(format!("unterminated <{}> element", kind.element()));
                }
            }
            let message = tag.attribute("message");
            // Bazel's synthesized wrapper suite reports the runner's own exit code in a failure whose case
            // carries no class name. It is the only place that number appears.
            let unnamed_case = state
                .pending
                .as_ref()
                .is_none_or(|case| case.class_name.is_empty());
            if let (Some(code), Some(suite), true) = (
                message.as_deref().and_then(wrapper_exit_code),
                &mut state.current,
                unnamed_case,
            ) {
                suite.wrapper_exit_code = Some(code);
            }
            let Some(case) = &mut state.pending else {
                state.note_malformed(format!("<{}> appears outside a test case", kind.element()));
                continue;
            };
            let repeated = case.outcome == Outcome::Failed;
            case.outcome = Outcome::Failed;
            case.failure = Some(CaseFailure {
                kind,
                r#type: tag.attribute("type"),
                message,
                detail,
            });
            if repeated {
                state.note_malformed("multiple failure elements in one test case");
            }
        }
    }

    if state.pending.is_some() {
        state.note_truncated("document ended while a <testcase> was open");
        state.finish_pending();
    }
    if state.current.is_some() {
        state.note_truncated("document ended while a <testsuite> was open");
        state.finish_suite();
    }
    if !state.root_open {
        state.note_malformed("no <testsuites> root was found");
    } else if !state.root_closed {
        state.note_truncated("document ended before </testsuites>");
    }
    let integrity = state.integrity();
    (state.suites, integrity)
}

/// The exit code in Bazel's `exited with error code N`.
fn wrapper_exit_code(message: &str) -> Option<i32> {
    const MARKER: &str = "exited with error code ";
    let digits_start = message.find(MARKER)? + MARKER.len();
    let rest = &message[digits_start..];
    let digits = &rest[..rest
        .find(|char: char| !char.is_ascii_digit())
        .unwrap_or(rest.len())];
    digits.parse().ok()
}

/// Resolves entities, keeping the text as written when it is not well-formed XML text (a lone `&`, an unknown
/// entity): a failure message is shown to a reader, and a half-resolved one is worse than a raw one.
///
/// One pass, so `&amp;lt;` is the text `&lt;` and never a `<`: resolving `&amp;` first would silently turn
/// escaped markup into markup.
fn unescape(text: &str) -> Cow<'_, str> {
    quick_xml::escape::unescape(text).unwrap_or(Cow::Borrowed(text))
}

/// The index of the `>` closing the tag that starts at `start`, skipping quoted attribute values.
///
/// `>` is legal unescaped inside an attribute, and assertion messages such as `expected: <"/a"> but was:
/// <"/b">` reach the XML with it intact.
fn find_tag_end(text: &str, start: usize) -> Option<usize> {
    let mut quote = None;
    for (offset, byte) in text.as_bytes()[start..].iter().enumerate() {
        match quote {
            Some(open) => {
                if *byte == open {
                    quote = None;
                }
            }
            None if *byte == b'"' || *byte == b'\'' => quote = Some(*byte),
            None if *byte == b'>' => return Some(start + offset),
            None => {}
        }
    }
    None
}

struct OpaqueSkip {
    end: usize,
    /// What was left open when the document ended.
    unterminated: Option<String>,
}

/// Advances past a region that must not be interpreted: a CDATA section, a comment, or a captured-output body.
fn skip_opaque(text: &str, index: usize) -> Option<OpaqueSkip> {
    let rest = &text[index..];
    for (open, close, description) in [
        ("<![CDATA[", "]]>", "CDATA section"),
        ("<!--", "-->", "comment"),
    ] {
        if rest.starts_with(open) {
            return Some(match rest.find(close) {
                Some(end) => OpaqueSkip {
                    end: index + end + close.len(),
                    unterminated: None,
                },
                None => OpaqueSkip {
                    end: text.len(),
                    unterminated: Some(description.to_owned()),
                },
            });
        }
    }
    for element in ["system-out", "system-err"] {
        if !rest
            .strip_prefix('<')
            .is_some_and(|name| name.starts_with(element))
        {
            continue;
        }
        let Some(tag_end) = find_tag_end(text, index) else {
            return Some(OpaqueSkip {
                end: text.len(),
                unterminated: Some(format!("<{element}> start tag")),
            });
        };
        if text[..tag_end].ends_with('/') {
            return Some(OpaqueSkip {
                end: tag_end + 1,
                unterminated: None,
            });
        }
        let closing = format!("</{element}>");
        return Some(match text[tag_end..].find(&closing) {
            Some(close_at) => OpaqueSkip {
                end: tag_end + close_at + closing.len(),
                unterminated: None,
            },
            None => OpaqueSkip {
                end: text.len(),
                unterminated: Some(format!("<{element}> body")),
            },
        });
    }
    None
}

struct ElementText {
    content: String,
    end: usize,
    complete: bool,
}

/// The text content of the element whose start tag ended just before `from`, resolving CDATA sections: text is
/// unescaped, CDATA is taken as written, and anything up to the closing tag is text.
fn element_text(text: &str, from: usize, element: &str) -> ElementText {
    let closing = format!("</{element}>");
    let mut content = String::new();
    let mut index = from;
    loop {
        let rest = &text[index..];
        let close = rest.find(&closing);
        if let Some(cdata) = rest
            .find("<![CDATA[")
            .filter(|cdata| close.is_none_or(|close| *cdata < close))
        {
            content.push_str(&unescape(&rest[..cdata]));
            let section = &rest[cdata + "<![CDATA[".len()..];
            let Some(end) = section.find("]]>") else {
                content.push_str(section);
                return ElementText {
                    content,
                    end: text.len(),
                    complete: false,
                };
            };
            content.push_str(&section[..end]);
            index += cdata + "<![CDATA[".len() + end + "]]>".len();
        } else {
            let Some(close) = close else {
                content.push_str(&unescape(rest));
                return ElementText {
                    content,
                    end: text.len(),
                    complete: false,
                };
            };
            content.push_str(&unescape(&rest[..close]));
            return ElementText {
                content,
                end: index + close + closing.len(),
                complete: true,
            };
        }
    }
}

// --- the class-name filter ------------------------------------------------------------------------------------

/// One *simple* class name as a JUnit class-name pattern.
///
/// `includeClassNamePatterns` is matched against the fully qualified name, so an anchored literal of a simple name
/// matches nothing at all. The leading alternation accepts any package, and it mirrors `JUnit5BazelRunner`'s own
/// simple-name handling. The suite documents name each suite by its simple class name, never by its FQN.
pub fn simple_class_name_pattern(simple_name: &str) -> String {
    format!(r"(^|.*\.){simple_name}$")
}
