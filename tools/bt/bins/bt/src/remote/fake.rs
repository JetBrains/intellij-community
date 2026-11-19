//! The scripted link of the tests: each call is recorded, and each kind of call answers its scripted result.

use std::path::PathBuf;
use std::sync::{Mutex, PoisonError};

use super::{HostLink, HostOutcome, HostRefusal, HostRun, NOT_RUN};

type Answer<T> = Mutex<Option<Result<T, HostRefusal>>>;

/// One recorded run request.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RecordedRun {
    pub(crate) alias: String,
    pub(crate) checkout: PathBuf,
    pub(crate) repo: Option<String>,
    pub(crate) command: Vec<String>,
    pub(crate) fetch: Vec<String>,
}

/// A link whose calls answer the scripted results. A call without a script panics with its name.
#[derive(Default)]
pub(crate) struct FakeLink {
    pub(crate) calls: Mutex<Vec<String>>,
    pub(crate) runs: Mutex<Vec<RecordedRun>>,
    pub(crate) run: Answer<HostOutcome>,
}

/// A refusal of `brd-host` for a script, before the start of a run.
pub(crate) fn host_refused(code: &str, message: &str) -> HostRefusal {
    HostRefusal::new(code, NOT_RUN, message)
}

impl FakeLink {
    pub(crate) fn calls(&self) -> Vec<String> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn runs(&self) -> Vec<RecordedRun> {
        self.runs.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn answer_run(&self, answer: Result<HostOutcome, HostRefusal>) {
        *self.run.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    fn record(&self, call: String) {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(call);
    }
}

fn scripted<T: Clone>(answer: &Answer<T>, call: &str) -> Result<T, HostRefusal> {
    answer
        .lock()
        .unwrap_or_else(PoisonError::into_inner)
        .clone()
        .unwrap_or_else(|| panic!("no scripted answer for {call}"))
}

impl HostLink for FakeLink {
    fn run(&self, alias: &str, request: &HostRun<'_>) -> Result<HostOutcome, HostRefusal> {
        self.record(format!("run {alias}"));
        self.runs.lock().unwrap_or_else(PoisonError::into_inner).push(RecordedRun {
            alias: alias.to_owned(),
            checkout: request.checkout.to_path_buf(),
            repo: request.repo.map(str::to_owned),
            command: request.command.to_vec(),
            fetch: request.fetch.to_vec(),
        });
        scripted(&self.run, "run")
    }
}
