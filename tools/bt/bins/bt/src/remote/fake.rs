//! The scripted link of the tests: each call is recorded, and each kind of call answers its scripted result.

use std::path::PathBuf;
use std::sync::{Mutex, PoisonError};

use super::{HostAgent, HostHello, HostLink, HostOutcome, HostRefusal, HostRun, HostSetup, NOT_RUN};

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

/// One recorded setup request.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RecordedSetup {
    pub(crate) alias: String,
    pub(crate) repo: String,
    pub(crate) name: String,
}

/// A link whose calls answer the scripted results. A call without a script panics with its name.
#[derive(Default)]
pub(crate) struct FakeLink {
    pub(crate) calls: Mutex<Vec<String>>,
    pub(crate) runs: Mutex<Vec<RecordedRun>>,
    pub(crate) setups: Mutex<Vec<RecordedSetup>>,
    pub(crate) run: Answer<HostOutcome>,
    pub(crate) hello: Answer<HostHello>,
    pub(crate) setup: Answer<String>,
}

/// A refusal of `bd-host` for a script, before the start of a run.
pub(crate) fn host_refused(code: &str, message: &str) -> HostRefusal {
    HostRefusal::new(code, NOT_RUN, message)
}

/// The hello of the scripts.
pub(crate) fn hello() -> HostHello {
    HostHello {
        address: "192.168.1.24:7742".to_owned(),
        agent: HostAgent {
            version: "0.37.0".to_owned(),
            os: "macos".to_owned(),
            arch: "aarch64".to_owned(),
        },
        same_build: true,
    }
}

impl FakeLink {
    pub(crate) fn calls(&self) -> Vec<String> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn runs(&self) -> Vec<RecordedRun> {
        self.runs.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn setups(&self) -> Vec<RecordedSetup> {
        self.setups.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn answer_run(&self, answer: Result<HostOutcome, HostRefusal>) {
        *self.run.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_hello(&self, answer: Result<HostHello, HostRefusal>) {
        *self.hello.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_setup(&self, answer: Result<String, HostRefusal>) {
        *self.setup.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
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

    fn hello(&self, alias: &str) -> Result<HostHello, HostRefusal> {
        self.record(format!("hello {alias}"));
        scripted(&self.hello, "hello")
    }

    fn setup(&self, request: &HostSetup<'_>) -> Result<String, HostRefusal> {
        self.record(format!("setup {}", request.alias));
        self.setups.lock().unwrap_or_else(PoisonError::into_inner).push(RecordedSetup {
            alias: request.alias.to_owned(),
            repo: request.repo.to_owned(),
            name: request.name.to_owned(),
        });
        scripted(&self.setup, "setup")
    }
}
