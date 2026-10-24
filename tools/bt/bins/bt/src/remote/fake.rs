//! The scripted link of the tests: each call is recorded, and each kind of call answers its scripted result.

use std::path::PathBuf;
use std::sync::{Mutex, PoisonError};

use yonder_client::{AgentInfo, Exit, Leased, PrepareRequest, Prepared, RunOutcome, RunRequest};

use super::HostLink;

type Answer<T> = Mutex<Option<Result<T, yonder_client::Refusal>>>;

/// One recorded run request.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RecordedRun {
    pub(crate) alias: String,
    pub(crate) checkout: PathBuf,
    pub(crate) repo: Option<String>,
    pub(crate) holder: String,
    pub(crate) command: Vec<String>,
    pub(crate) fetch: Vec<String>,
    pub(crate) idle_hours: u32,
}

/// One recorded prepare request.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RecordedPrepare {
    pub(crate) alias: String,
    pub(crate) agent: PathBuf,
    pub(crate) port: u16,
    pub(crate) repo: String,
    pub(crate) name: String,
}

/// A link whose calls answer the scripted results. A call without a script panics with its name.
#[derive(Default)]
pub(crate) struct FakeLink {
    pub(crate) calls: Mutex<Vec<String>>,
    pub(crate) runs: Mutex<Vec<RecordedRun>>,
    pub(crate) prepares: Mutex<Vec<RecordedPrepare>>,
    pub(crate) run: Answer<RunOutcome>,
    pub(crate) lease: Answer<Leased>,
    pub(crate) release: Answer<String>,
    pub(crate) hello: Answer<AgentInfo>,
    pub(crate) prepare: Answer<Prepared>,
}

/// A refusal of yonder for a script.
pub(crate) fn yonder_refusal(code: &'static str, message: &str) -> yonder_client::Refusal {
    yonder_client::Refusal::new(code, Exit::Refused, message)
}

/// The agent of the scripts.
pub(crate) fn agent_info() -> AgentInfo {
    AgentInfo {
        version: "0.1.0".to_owned(),
        os: "macos".to_owned(),
        arch: "aarch64".to_owned(),
        address: "192.168.1.24:7741".parse().expect("an address"),
    }
}

impl FakeLink {
    pub(crate) fn calls(&self) -> Vec<String> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn runs(&self) -> Vec<RecordedRun> {
        self.runs.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn prepares(&self) -> Vec<RecordedPrepare> {
        self.prepares.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn answer_run(&self, answer: Result<RunOutcome, yonder_client::Refusal>) {
        *self.run.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_lease(&self, answer: Result<Leased, yonder_client::Refusal>) {
        *self.lease.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_release(&self, answer: Result<String, yonder_client::Refusal>) {
        *self.release.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_hello(&self, answer: Result<AgentInfo, yonder_client::Refusal>) {
        *self.hello.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    pub(crate) fn answer_prepare(&self, answer: Result<Prepared, yonder_client::Refusal>) {
        *self.prepare.lock().unwrap_or_else(PoisonError::into_inner) = Some(answer);
    }

    fn record(&self, call: String) {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(call);
    }
}

fn scripted<T: Clone>(answer: &Answer<T>, call: &str) -> Result<T, yonder_client::Refusal> {
    answer
        .lock()
        .unwrap_or_else(PoisonError::into_inner)
        .clone()
        .unwrap_or_else(|| panic!("no scripted answer for {call}"))
}

impl HostLink for FakeLink {
    fn run(&self, alias: &str, request: &RunRequest<'_>) -> Result<RunOutcome, yonder_client::Refusal> {
        self.record(format!("run {alias}"));
        self.runs.lock().unwrap_or_else(PoisonError::into_inner).push(RecordedRun {
            alias: alias.to_owned(),
            checkout: request.checkout.to_path_buf(),
            repo: request.repo.map(str::to_owned),
            holder: request.holder.to_owned(),
            command: request.command.to_vec(),
            fetch: request.fetch.to_vec(),
            idle_hours: request.idle_hours,
        });
        scripted(&self.run, "run")
    }

    fn lease(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<Leased, yonder_client::Refusal> {
        self.record(format!("lease {alias} {repo:?} {holder}"));
        scripted(&self.lease, "lease")
    }

    fn release(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<String, yonder_client::Refusal> {
        self.record(format!("release {alias} {repo:?} {holder}"));
        scripted(&self.release, "release")
    }

    fn hello(&self, alias: &str) -> Result<AgentInfo, yonder_client::Refusal> {
        self.record(format!("hello {alias}"));
        scripted(&self.hello, "hello")
    }

    fn prepare(&self, request: &PrepareRequest<'_>) -> Result<Prepared, yonder_client::Refusal> {
        self.record(format!("prepare {}", request.alias));
        self.prepares.lock().unwrap_or_else(PoisonError::into_inner).push(RecordedPrepare {
            alias: request.alias.to_owned(),
            agent: request.agent.to_path_buf(),
            port: request.port,
            repo: request.repo.to_owned(),
            name: request.name.to_owned(),
        });
        scripted(&self.prepare, "prepare")
    }
}
