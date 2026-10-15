//! The two readers of the run stream that write what a build reads: the JUnit XML and the TeamCity service messages.
//!
//! Both are sinks of the reporter, beside the journal, so they read the records the journal holds. The XML of each
//! iteration goes beside its report as `<iterationId>.xml` when the report is published. The XML of the whole command
//! goes to the file that `JB_XML_OUTPUT_FILE` or `XML_OUTPUT_FILE` names, after each iteration and at the end of the
//! run. The TeamCity messages go to stdout as the records arrive, when the controller is the build step of a TeamCity
//! build. A write that fails is noted when the run ends, and the run goes on.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use avl_base::report::{SharedWriter, SinkGuard};
use avl_base::sync::lock;
use avl_base::{Environment, Reporter};
use avl_report::junit;
use avl_report::teamcity::{self, Messages};
use avl_trace::bundle::VIDEO_FILE;
use avl_wire::daemon::{RunEvent, RunEventKind, decode_run_event};
use avl_wire::progress::{IterationReported, Kind, Record, TraceReady};
use serde::de::DeserializeOwned;

#[cfg(test)]
mod tests;

/// The flow of a record of no worker.
const CONTROLLER_FLOW: &str = "controller";

/// Removes the sink when it drops, and notes the first write that failed.
#[must_use = "the readers are detached when the guard drops"]
pub(crate) struct ResultsGuard {
    reporter: Reporter,
    sink: Option<SinkGuard>,
    failure: Arc<Mutex<Option<String>>>,
}

impl Drop for ResultsGuard {
    fn drop(&mut self) {
        drop(self.sink.take());
        let failure = lock(&self.failure).take();
        if let Some(failure) = failure {
            self.reporter.note(failure, None);
        }
    }
}

/// Adds the readers to `reporter` until the guard drops. The TeamCity messages go to the stdout of the process.
pub(crate) fn attach(reporter: &Reporter, environment: &Environment) -> ResultsGuard {
    attach_to(reporter, environment, SharedWriter::new(std::io::stdout()))
}

/// [`attach`] with the TeamCity messages written to `messages`.
pub(crate) fn attach_to(reporter: &Reporter, environment: &Environment, messages: SharedWriter) -> ResultsGuard {
    let variable = |name: &str| environment.get(name).map(str::to_owned);
    let failure = Arc::new(Mutex::new(None));
    let sink = Results {
        junit: junit::destination(variable).map(PathBuf::from),
        teamcity: teamcity::is_build_step(variable).then(|| (Messages::default(), messages)),
        iterations: Vec::new(),
        failure: Arc::clone(&failure),
    };
    ResultsGuard {
        reporter: reporter.clone(),
        sink: Some(reporter.add_sink(sink)),
        failure,
    }
}

struct Results {
    /// The XML of the whole command.
    junit: Option<PathBuf>,
    teamcity: Option<(Messages, SharedWriter)>,
    /// The records of each iteration, by its worker, in the order the iterations started.
    iterations: Vec<Iteration>,
    failure: Arc<Mutex<Option<String>>>,
}

struct Iteration {
    worker: Option<String>,
    id: Option<String>,
    events: Vec<RunEvent>,
}

impl avl_base::report::Sink for Results {
    fn write(&mut self, record: &Record) {
        let worker = record.worker.as_deref();
        match record.event {
            Kind::Run => {
                if let Some(event) = record.data.as_ref().and_then(|data| decode_run_event(data.get().as_bytes()).ok()) {
                    self.run_event(worker, event);
                }
            }
            Kind::TraceReady => {
                if let (Some(trace), Some((messages, out))) = (data::<TraceReady>(record), self.teamcity.as_mut()) {
                    emit(out, &messages.trace(worker.unwrap_or(CONTROLLER_FLOW), &trace, VIDEO_FILE));
                }
            }
            Kind::IterationReported => {
                if let Some(reported) = data::<IterationReported>(record) {
                    self.iteration_reported(worker, &reported);
                }
            }
            Kind::RunFinished => {
                if let Some((messages, out)) = self.teamcity.as_mut() {
                    emit(out, &messages.finish());
                }
                self.write_command_xml();
            }
            _ => {}
        }
    }
}

impl Results {
    fn run_event(&mut self, worker: Option<&str>, event: RunEvent) {
        if let Some((messages, out)) = self.teamcity.as_mut() {
            emit(out, &messages.record(worker.unwrap_or(CONTROLLER_FLOW), &event));
        }
        let current = match &event.kind {
            RunEventKind::RunStarted(started) => {
                self.iterations.push(Iteration {
                    worker: worker.map(str::to_owned),
                    id: Some(started.iteration_id.clone()),
                    events: Vec::new(),
                });
                self.iterations.last_mut()
            }
            _ => self
                .iterations
                .iter_mut()
                .rev()
                .find(|iteration| iteration.worker.as_deref() == worker),
        };
        match current {
            Some(iteration) => iteration.events.push(event),
            None => self.iterations.push(Iteration {
                worker: worker.map(str::to_owned),
                id: None,
                events: vec![event],
            }),
        }
    }

    /// Writes the XML of the reported iteration beside its report, and the XML of the command so far.
    fn iteration_reported(&self, worker: Option<&str>, reported: &IterationReported) {
        let iteration = self
            .iterations
            .iter()
            .rev()
            .find(|iteration| iteration.worker.as_deref() == worker && iteration.id.as_deref() == Some(&reported.iteration_id));
        if let Some(iteration) = iteration {
            let path = Path::new(&reported.report_path).with_extension("xml");
            let xml = junit::render([iteration.events.as_slice()]);
            self.store(junit::write(&path, &xml).err().map(|refusal| refusal.message));
        }
        self.write_command_xml();
    }

    fn write_command_xml(&self) {
        let Some(path) = &self.junit else {
            return;
        };
        let xml = junit::render(self.iterations.iter().map(|iteration| iteration.events.as_slice()));
        self.store(junit::write(path, &xml).err().map(|refusal| refusal.message));
    }

    /// Keeps the first failure, which [`ResultsGuard`] notes.
    fn store(&self, failure: Option<String>) {
        if let Some(failure) = failure {
            lock(&self.failure).get_or_insert(format!("cannot write the JUnit XML: {failure}"));
        }
    }
}

fn data<T: DeserializeOwned>(record: &Record) -> Option<T> {
    serde_json::from_str(record.data.as_ref()?.get()).ok()
}

fn emit(out: &SharedWriter, lines: &[String]) {
    if lines.is_empty() {
        return;
    }
    let mut text = lines.join("\n");
    text.push('\n');
    out.emit(text.as_bytes());
}
