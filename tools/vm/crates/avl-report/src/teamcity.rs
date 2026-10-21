//! The TeamCity service messages of a run, written from its run stream as the records arrive.
//!
//! The controller writes them when it is the build step of a TeamCity build ([`is_build_step`]). A test class is a
//! `testSuiteStarted` and `testSuiteFinished` pair, and a case is `testStarted`, then `testFailed` with the failure
//! chain as its details or `testIgnored`, then `testFinished` with its duration. The name of a case is
//! `<testClassName>.<scenario>`, as the JUnit XML names it. The flow of each record is its worker, so the cases of two
//! workers of a shard do not nest. A note of a scenario is a text `testMetadata`, and a metric is a `number` one. A
//! trace that arrives publishes its zip, and names the bundle as an `artifact` and its video as a `video`.
//!
//! A case that started and never finished, and a class of the plan that the run never reached, fail as they fail in
//! the XML, when the `summary` of their iteration arrives or the run ends.
//!
//! A step after a Bazel test publishes its traces with [`Messages::published`]: the test reported through its XML, so
//! the step gives each trace the cases of the XML beside it and adds only artifacts and metadata. Those messages carry
//! no flow, because no test of the step runs.

use std::collections::BTreeSet;

use avl_wire::daemon::{ExecutionIdentity, PlanStarted, RunEvent, RunEventKind};
use avl_wire::progress::TraceReady;
use avl_wire::report::ScenarioReport;

use avl_junit::cases::{
    self, CaseFailure, FailureOrigin, STATUS_ABORTED, STATUS_FAILED, UNFINISHED_MESSAGE, failure_of_container, failure_of_test,
    simple_class_name,
};
pub use avl_junit::teamcity::TEAMCITY_VERSION;
use avl_junit::teamcity::{is_team_city, message, publish_artifacts};
use avl_junit::{TYPE_UNFINISHED, XML_OUTPUT_FILE};

#[cfg(test)]
mod tests;

/// The directory of the build artifacts that holds the published trace zips.
pub const TRACES_ARTIFACT_DIR: &str = "air-traces";

/// Whether this controller is the build step of a TeamCity build: [`TEAMCITY_VERSION`] is set and not blank, and
/// [`XML_OUTPUT_FILE`] is unset. A Bazel test reports through its XML, so the controller inside one writes no message.
pub fn is_build_step(variable: impl Fn(&str) -> Option<String>) -> bool {
    variable(XML_OUTPUT_FILE).is_none() && is_team_city(variable)
}

/// A `message` of the build log with the status `WARNING`. TeamCity shows it as a warning, and the build stays green.
pub fn warning(text: &str) -> String {
    message("message", &[("text", text), ("status", "WARNING")], None)
}

/// The flow of the messages of [`Messages::published`]: none, so a message names no `flowId`.
const NO_FLOW: &str = "";

/// One trace that a step after the tests publishes, by the artifact paths of its files.
#[derive(Clone, Copy, Debug)]
pub struct PublishedTrace<'a> {
    /// The zip of the bundle as an artifact path, such as `air-traces.zip!/<lane>/<runId>/<TestClass>/<scenario>.zip`.
    pub trace: &'a str,
    pub test_class: &'a str,
    pub scenario: &'a str,
    /// The artifact path of the video, when the step publishes one.
    pub video: Option<&'a str>,
    /// The artifact path of the picture of the failure, when the step publishes one.
    pub still: Option<&'a str>,
    /// The page of the published viewer that opens the trace, when the step publishes the viewer.
    pub link: Option<&'a str>,
    /// The cases of the JUnit XML of the trace's own test target, as `(class, case)`, which TeamCity imported. Only
    /// they name the trace's test, so a trace of one lane never names the test of another lane.
    pub cases: &'a [(String, String)],
}

/// The test name of the scenario `scenario` of `test_class` among the cases `named`, as `(class, case)`: the last case
/// of that name and of a class of the same simple name, else `<test_class>.<scenario>`. A recorder can name the class
/// without its package, so the simple names are compared.
fn test_name(named: &[(String, String)], test_class: &str, scenario: &str) -> String {
    named
        .iter()
        .rev()
        .find(|(class, name)| name == scenario && simple_class_name(class) == simple_class_name(test_class))
        .map_or_else(|| format!("{test_class}.{scenario}"), |(class, name)| format!("{class}.{name}"))
}

/// The state of the messages of one run: what each flow has open, and which zips are published.
#[derive(Debug, Default)]
pub struct Messages {
    flows: Vec<Flow>,
    published: BTreeSet<String>,
}

#[derive(Debug)]
struct Flow {
    id: String,
    /// The class of the open suite.
    suite: Option<String>,
    /// The execution id and the test name of each open case.
    open: Vec<(String, String)>,
    /// The class and the case name of each test the flow named, for the trace of a scenario.
    named: Vec<(String, String)>,
    plan: Vec<String>,
    /// The simple name of each class a record reached.
    reached: BTreeSet<String>,
}

impl Messages {
    /// The messages of one record of the run stream of the worker `flow`.
    pub fn record(&mut self, flow: &str, event: &RunEvent) -> Vec<String> {
        let mut out = Vec::new();
        let flow = self.flow(flow);
        match &event.kind {
            RunEventKind::PlanStarted(PlanStarted { class_names, .. }) => {
                flow.plan.clone_from(class_names);
                flow.reached.clear();
            }
            RunEventKind::TestStarted(started) => {
                if !flow.open.iter().any(|(id, _)| *id == started.execution.id) {
                    let name = flow.start(&mut out, &started.execution);
                    flow.open.push((started.execution.id.clone(), name));
                }
            }
            RunEventKind::TestFinished(finished) => {
                let name = flow.started(&mut out, &finished.execution);
                match finished.status.as_str() {
                    STATUS_FAILED => flow.failed(&mut out, &name, &failure_of_test(finished)),
                    STATUS_ABORTED => {
                        let reason = finished
                            .failure
                            .message
                            .clone()
                            .unwrap_or_else(|| "the run gave up on the test".to_owned());
                        flow.ignored(&mut out, &name, &reason);
                    }
                    _ => {}
                }
                flow.metadata(&mut out, &name, &finished.report);
                flow.finished(&mut out, &name, finished.duration_ms);
            }
            RunEventKind::TestSkipped(skipped) => {
                let name = flow.started(&mut out, &skipped.execution);
                flow.ignored(&mut out, &name, &skipped.reason);
                flow.finished(&mut out, &name, 0);
            }
            RunEventKind::ContainerSkipped(skipped) => {
                let name = flow.start(&mut out, &skipped.execution);
                flow.ignored(&mut out, &name, &skipped.reason);
                flow.finished(&mut out, &name, 0);
            }
            RunEventKind::ContainerFailed(failed) => {
                let name = flow.start(&mut out, &failed.execution);
                flow.failed(&mut out, &name, &failure_of_container(failed));
                flow.finished(&mut out, &name, 0);
            }
            RunEventKind::Summary(_) => flow.close(&mut out),
            _ => {}
        }
        out
    }

    /// The messages of a scenario trace that arrived on the host: the zip once, then the bundle and its video.
    /// `video_file` is the name of the video in a bundle.
    pub fn trace(&mut self, flow: &str, trace: &TraceReady, video_file: &str) -> Vec<String> {
        let mut out = Vec::new();
        let zip_name = trace.zip.rsplit(['/', '\\']).next().unwrap_or(&trace.zip).to_owned();
        let target = format!("{TRACES_ARTIFACT_DIR}/{flow}/{}", trace.iteration_id);
        out.extend(self.artifact(&trace.zip, &target));
        let flow = self.flow(flow);
        let name = flow.test_of(&trace.test_class, &trace.scenario);
        let bundle = format!("{target}/{zip_name}!/{}", trace.entry.trim_end_matches('/'));
        flow.trace_metadata(&mut out, &name, "trace", "artifact", &bundle);
        if trace.has_video {
            flow.trace_metadata(&mut out, &name, "video", "video", &format!("{bundle}/{video_file}"));
        }
        out
    }

    /// The metadata of the test of a trace that a step after the tests publishes. They are the zip of the bundle, the
    /// viewer page of the trace, its video and its failure picture. The step publishes the files itself.
    pub fn published(&mut self, trace: &PublishedTrace<'_>) -> Vec<String> {
        let mut out = Vec::new();
        let flow = self.flow(NO_FLOW);
        let name = test_name(trace.cases, trace.test_class, trace.scenario);
        flow.trace_metadata(&mut out, &name, "trace", "artifact", trace.trace);
        if let Some(link) = trace.link {
            flow.trace_metadata(&mut out, &name, "trace viewer", "link", link);
        }
        if let Some(video) = trace.video {
            flow.trace_metadata(&mut out, &name, "video", "video", video);
        }
        if let Some(still) = trace.still {
            flow.trace_metadata(&mut out, &name, "failure", "image", still);
        }
        out
    }

    /// Publishes the host file `file` at the top of the artifacts under its own name, once for each file. An archive
    /// stays as it is, because no target asks TeamCity to pack it.
    pub fn artifact_file(&mut self, file: &str) -> Vec<String> {
        if self.published.insert(file.to_owned()) {
            vec![message("publishArtifacts", &[], Some(file))]
        } else {
            Vec::new()
        }
    }

    /// Publishes the host file `file` into the artifact directory `target`, once for each file.
    pub fn artifact(&mut self, file: &str, target: &str) -> Vec<String> {
        if self.published.insert(file.to_owned()) {
            vec![publish_artifacts(file, target)]
        } else {
            Vec::new()
        }
    }

    /// The messages that end the run: every open case fails as unfinished, and every open suite finishes.
    pub fn finish(&mut self) -> Vec<String> {
        let mut out = Vec::new();
        for flow in &mut self.flows {
            flow.close(&mut out);
        }
        out
    }

    fn flow(&mut self, id: &str) -> &mut Flow {
        let index = self.flows.iter().position(|flow| flow.id == id).unwrap_or_else(|| {
            self.flows.push(Flow {
                id: id.to_owned(),
                suite: None,
                open: Vec::new(),
                named: Vec::new(),
                plan: Vec::new(),
                reached: BTreeSet::new(),
            });
            self.flows.len() - 1
        });
        &mut self.flows[index]
    }
}

impl Flow {
    /// Opens the suite of `class`, and finishes the suite before it.
    fn suite(&mut self, out: &mut Vec<String>, class: &str) {
        self.reached.insert(simple_class_name(class).to_owned());
        if self.suite.as_deref() == Some(class) {
            return;
        }
        self.end_suite(out);
        out.push(message("testSuiteStarted", &[("name", class), ("flowId", &self.id)], None));
        self.suite = Some(class.to_owned());
    }

    fn end_suite(&mut self, out: &mut Vec<String>) {
        if let Some(open) = self.suite.take() {
            out.push(message("testSuiteFinished", &[("name", &open), ("flowId", &self.id)], None));
        }
    }

    /// Writes the start of the case of `execution`, and answers its test name.
    fn start(&mut self, out: &mut Vec<String>, execution: &ExecutionIdentity) -> String {
        let class = cases::class_of(execution);
        self.suite(out, class);
        let name = format!("{class}.{}", execution.display_name);
        self.named.push((class.to_owned(), execution.display_name.clone()));
        out.push(message(
            "testStarted",
            &[("name", &name), ("captureStandardOutput", "false"), ("flowId", &self.id)],
            None,
        ));
        name
    }

    /// The test name of the open case of `execution`, closed here. A case with no start is started now.
    fn started(&mut self, out: &mut Vec<String>, execution: &ExecutionIdentity) -> String {
        match self.open.iter().position(|(id, _)| *id == execution.id) {
            Some(position) => self.open.remove(position).1,
            None => self.start(out, execution),
        }
    }

    fn failed(&self, out: &mut Vec<String>, name: &str, failure: &CaseFailure) {
        out.push(message(
            "testFailed",
            &[
                ("name", name),
                ("message", failure.headline()),
                ("details", &failure.text()),
                ("flowId", &self.id),
            ],
            None,
        ));
    }

    fn ignored(&self, out: &mut Vec<String>, name: &str, reason: &str) {
        out.push(message(
            "testIgnored",
            &[("name", name), ("message", reason), ("flowId", &self.id)],
            None,
        ));
    }

    fn metadata(&self, out: &mut Vec<String>, name: &str, report: &ScenarioReport) {
        for note in &report.notes {
            out.push(message(
                "testMetadata",
                &[("testName", name), ("name", "note"), ("value", note), ("flowId", &self.id)],
                None,
            ));
        }
        for (metric, value) in &report.metrics {
            out.push(message(
                "testMetadata",
                &[
                    ("testName", name),
                    ("name", metric),
                    ("type", "number"),
                    ("value", &value.to_string()),
                    ("flowId", &self.id),
                ],
                None,
            ));
        }
    }

    fn finished(&self, out: &mut Vec<String>, name: &str, duration_ms: i64) {
        out.push(message(
            "testFinished",
            &[("name", name), ("duration", &duration_ms.max(0).to_string()), ("flowId", &self.id)],
            None,
        ));
    }

    /// Ends what the iteration of the flow left open: each open case fails as unfinished, each class of the plan that
    /// no record reached fails as one case named after the class, and the open suite finishes.
    fn close(&mut self, out: &mut Vec<String>) {
        let unfinished = CaseFailure {
            kind: FailureOrigin::Test,
            r#type: Some(TYPE_UNFINISHED.to_owned()),
            message: UNFINISHED_MESSAGE.to_owned(),
            detail: String::new(),
        };
        for (_, name) in std::mem::take(&mut self.open) {
            self.failed(out, &name, &unfinished);
            self.finished(out, &name, 0);
        }
        let unreported: Vec<String> = std::mem::take(&mut self.plan)
            .into_iter()
            .filter(|class| !self.reached.contains(simple_class_name(class)))
            .collect();
        for class in unreported {
            self.suite(out, &class);
            let name = format!("{class}.{class}");
            out.push(message(
                "testStarted",
                &[("name", &name), ("captureStandardOutput", "false"), ("flowId", &self.id)],
                None,
            ));
            let failure = CaseFailure {
                kind: FailureOrigin::Container,
                r#type: None,
                message: format!("the run never reached the suite {class}: the run stream ended before its first record"),
                detail: String::new(),
            };
            self.failed(out, &name, &failure);
            self.finished(out, &name, 0);
        }
        self.end_suite(out);
    }

    /// One `testMetadata` of a trace file, with the flow when there is one.
    fn trace_metadata(&self, out: &mut Vec<String>, test: &str, label: &str, kind: &str, value: &str) {
        let mut attributes = vec![("testName", test), ("name", label), ("type", kind), ("value", value)];
        if self.id != NO_FLOW {
            attributes.push(("flowId", &self.id));
        }
        out.push(message("testMetadata", &attributes, None));
    }

    /// The test name of the scenario `scenario` of `test_class`, as the flow named it. A recorder can name the class
    /// without its package, so the simple names are compared.
    fn test_of(&self, test_class: &str, scenario: &str) -> String {
        test_name(&self.named, test_class, scenario)
    }
}
