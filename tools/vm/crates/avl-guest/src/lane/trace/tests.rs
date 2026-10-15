use std::sync::{Arc, Mutex};

use pretty_assertions::assert_eq;

use super::*;

/// A transport that keeps each line and acks each acked one at once, with `ok`.
#[derive(Clone, Default)]
pub(crate) struct Recording {
    pub lines: Arc<Mutex<Vec<String>>>,
    pub refuse_hello: bool,
}

impl Transport for Recording {
    fn send(&mut self, line: Vec<u8>, ack_within: Option<Duration>) -> Result<Option<Ack>, String> {
        let mut lines = self.lines.lock().unwrap();
        lines.push(String::from_utf8(line).unwrap());
        let number = u32::try_from(lines.len()).unwrap();
        let hello = number == 1;
        Ok(ack_within.map(|_| Ack {
            ok: !(hello && self.refuse_hello),
            line: number,
            ..Ack::default()
        }))
    }

    fn close(&mut self) {}
}

fn hello() -> HelloCommand {
    Trace::hello(Path::new("/vm/iterations/iter-1"), "iter-1", false, None)
}

fn scenario(name: &str) -> ScenarioCommand {
    ScenarioCommand {
        name: name.to_owned(),
        test_class: "AirSuiteGeneratedFlowUiTest".to_owned(),
        suite: Some("suite".to_owned()),
        lane: "UI".to_owned(),
        flow: None,
        fixture: None,
        flags: std::collections::BTreeMap::new(),
        labels: Vec::new(),
        bridge: None,
        program: None,
    }
}

fn failure(message: &str) -> TraceFailure {
    TraceFailure {
        type_name: "java.lang.AssertionError".to_owned(),
        message: Some(message.to_owned()),
        stack: vec!["at A.b(A.kt:1)".to_owned()],
        aborted: false,
    }
}

// The span ids count from 1 in each scenario, each span sits under the innermost open one, and the first failure is
// photographed once, in the span that failed, before the spans it unwinds through end with it.
#[test]
fn a_failure_is_photographed_once_in_its_own_span_and_fails_the_spans_it_unwinds() {
    let recording = Recording::default();
    let mut trace = Trace::open(Box::new(recording.clone()), hello());
    trace.open_scenario(scenario("fails"));
    let step = trace.open_span(SpanKind::Step, "only", "[flow] only", None);
    let assertion = trace.open_span(
        SpanKind::Assertion,
        "holds",
        "[assertion] holds: it holds",
        Some((Some("it holds"), Side::Ide, 180_000)),
    );
    trace.snap(SnapshotPhase::Boundary);
    let failed = failure("expected <a> & <b>");
    trace.end_span(assertion, Some(&failed));
    trace.end_span(step, Some(&failed));
    trace.finish_scenario(Some(&failed));
    trace.open_scenario(scenario("next"));
    assert_eq!(trace.open_span(SpanKind::Reset, "before", "[reset] before next", None), 1);
    let lines = recording.lines.lock().unwrap().clone();
    assert_eq!(
        lines[1..9].to_vec(),
        [
            r#"{"op":"scenario","name":"fails","testClass":"AirSuiteGeneratedFlowUiTest","suite":"suite","lane":"UI"}"#,
            r#"{"op":"span","id":1,"kind":"step","key":"step:only","title":"[flow] only"}"#,
            r#"{"op":"span","id":2,"parent":1,"kind":"assertion","key":"assertion:holds","title":"[assertion] holds: it holds","expectation":"it holds","side":"ide","timeoutMs":180000}"#,
            r#"{"op":"snap","span":2,"phase":"boundary"}"#,
            r#"{"op":"snap","span":2,"phase":"failure"}"#,
            r#"{"op":"end","id":2,"status":"failed","error":{"type":"java.lang.AssertionError","message":"expected <a> & <b>","stack":["at A.b(A.kt:1)"]}}"#,
            r#"{"op":"end","id":1,"status":"failed","error":{"type":"java.lang.AssertionError","message":"expected <a> & <b>","stack":["at A.b(A.kt:1)"]}}"#,
            r#"{"op":"done","status":"failed","message":"expected <a> & <b>","trace":"java.lang.AssertionError\nat A.b(A.kt:1)"}"#,
        ]
    );
    for line in &lines {
        protocol::decode_command(line.as_bytes()).unwrap_or_else(|error| panic!("{line}: {error}"));
    }
}

// A recorder that refuses the hello records nothing, and the session sends no further line.
#[test]
fn a_refused_hello_makes_the_session_quiet() {
    let recording = Recording {
        refuse_hello: true,
        ..Recording::default()
    };
    let mut trace = Trace::open(Box::new(recording.clone()), hello());
    trace.open_scenario(scenario("quiet"));
    trace.call("Ping", 1, 1, None);
    trace.finish_scenario(None);
    assert_eq!(recording.lines.lock().unwrap().len(), 1);
}

// An attachment is a file under `<root>/<runId>/attach`, handed over in the span that is open.
#[test]
fn an_attachment_is_a_file_under_the_attach_directory_of_the_run() {
    let root = tempfile::TempDir::new().unwrap();
    let recording = Recording::default();
    let mut trace = Trace::open(Box::new(recording.clone()), Trace::hello(root.path(), "iter-1", false, None));
    trace.open_scenario(scenario("attaches"));
    let span = trace.open_span(SpanKind::Action, "dump", "[action] dump", Some((None, Side::Host, 1_000)));
    assert!(trace.attach("IDE dump", "text/plain", b"threads", "txt"));
    let file = root.path().join(ROOT_DIR_NAME).join("iter-1").join("attach").join("1.txt");
    assert_eq!(fs::read_to_string(&file).unwrap(), "threads");
    let attach = recording.lines.lock().unwrap().last().cloned().unwrap();
    assert_eq!(
        attach,
        format!(
            r#"{{"op":"attach","name":"IDE dump","mime":"text/plain","file":"{}","span":{span}}}"#,
            file.display()
        )
    );
}
