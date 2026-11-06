//! The export of a run: what a reader of the run needs, copied into the export directory of the checkout
//! ([`Config::export_root`]) as the run goes on, when `AIR_VM_EXPORT` asks for it.
//!
//! The export directory has the layout of a runtime root, so the trace viewer reads it as it reads the runtime root:
//!
//! - `runs/<runId>/events.ndjson`: a second journal of the run, with every record;
//! - `runs/<runId>/reports/<iterationId>.json`, `.xml` and `-evidence/`: the report of each iteration, its JUnit XML
//!   and the guest files that it names;
//! - `runs/<runId>/traces/<iterationId>/<n>.zip`: the trace zips. `failed` takes each zip with a scenario that did
//!   not pass, and `all` takes every zip.
//!
//! The export is for a run on another host. `[host]` of `.branchdeck.toml` sets `AIR_VM_EXPORT` and names the
//! export directory in its `outputs`, so `bd-host` copies the directory back into the checkout of the caller.
//! `bd-host` replaces the local directory when the first file of the output arrives. So `failed` removes the export of a run
//! that failed nothing, and the export of the last red run stays in the checkout of the caller. The paths inside the
//! copied files are the paths of the other host. `air-trace serve` maps the trace zips and the reports of the journal
//! to their copies in the export.
//!
//! The sink only decides. A thread of the export makes the copies, because the sink runs under the lock of the
//! reporter, and a copy to another file system can take seconds. A copy is a clone where the file system can make
//! one, so an export on the volume of the runtime root costs no bytes. A copy that fails is noted when the run ends,
//! and the run goes on: the export is evidence, never a verdict.

use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

use avl_base::config::Export;
use avl_base::fs::create_private_dir;
use avl_base::journal::Journal;
use avl_base::report::SinkGuard;
use avl_base::sync::lock;
use avl_base::{Config, Reporter, journal};
use avl_trace_tools::discover::{Status as BundleStatus, VM_EXPORT_DIR, VM_RUN_REPORTS_DIR, VM_RUNS_DIR};
use avl_wire::progress::{IterationReported, Kind, Record, RunFinished, TraceReady};
use avl_wire::report::Status;
use serde::de::DeserializeOwned;

#[cfg(test)]
mod tests;

/// Removes the sinks of the export when it drops, waits for the copies, and notes what the export holds, or its first
/// failure.
#[must_use = "the export is detached when the guard drops"]
pub(crate) struct ExportGuard {
    reporter: Reporter,
    export: Export,
    /// The sink of the copies. It holds the sender of the copier, so its removal ends the copier.
    sink: Option<SinkGuard>,
    copier: Option<JoinHandle<()>>,
    journal: Option<(SinkGuard, Journal)>,
    root: PathBuf,
    run_dir: PathBuf,
    /// The export of the run, relative to the checkout, as both hosts name it.
    shown: PathBuf,
    state: Arc<Mutex<State>>,
}

impl Drop for ExportGuard {
    fn drop(&mut self) {
        drop(self.sink.take());
        if let Some((sink, journal)) = self.journal.take() {
            drop(sink);
            if let Err(refusal) = journal.close() {
                lock(&self.state).failed(format!("the journal of the export stopped: {}", refusal.message));
            }
        }
        if let Some(copier) = self.copier.take()
            && copier.join().is_err()
        {
            lock(&self.state).failed("the copier of the export panicked".to_owned());
        }
        let state = lock(&self.state);
        let note = if self.export == Export::Failed && !state.red {
            remove_export(&self.root, &self.run_dir);
            format!("the run failed nothing, so it exported nothing to {}", self.shown.display())
        } else if let Some(failure) = &state.failure {
            format!("the export in {} is not complete: {failure}", self.shown.display())
        } else {
            format!(
                "exported the journal, {} report(s) and {} trace zip(s) to {}",
                state.reports,
                state.zips,
                self.shown.display()
            )
        };
        drop(state);
        self.reporter.note(note, None);
    }
}

/// Exports the run `run_id` until the guard drops, or answers `None` when `AIR_VM_EXPORT` is off.
pub(crate) fn attach(reporter: &Reporter, settings: &Config, run_id: &str) -> Option<ExportGuard> {
    if settings.export == Export::Off {
        return None;
    }
    let root = settings.export_root.clone();
    let (shown, run_dir) = match journal::run_dir(Path::new(VM_EXPORT_DIR), run_id).and_then(|shown| {
        let run_dir = journal::run_dir(&root, run_id)?;
        Ok((shown, run_dir))
    }) {
        Ok(paths) => paths,
        Err(refusal) => {
            reporter.note(format!("no export of the run: {}", refusal.message), None);
            return None;
        }
    };
    let state = Arc::new(Mutex::new(State::default()));
    let (sender, receiver) = mpsc::channel();
    let copier = Copier {
        root: root.clone(),
        run_id: run_id.to_owned(),
        state: Arc::clone(&state),
    };
    let copier = match thread::Builder::new()
        .name("export".to_owned())
        .spawn(move || copier.run(&receiver))
    {
        Ok(copier) => copier,
        Err(error) => {
            reporter.note(format!("no export of the run: cannot start its copier: {error}"), None);
            return None;
        }
    };
    let journal = match Journal::create(&root, run_id) {
        Ok(journal) => Some((reporter.add_sink(journal.sink()), journal)),
        Err(refusal) => {
            lock(&state).failed(format!("cannot create the journal of the export: {}", refusal.message));
            None
        }
    };
    let sink = Exporter {
        export: settings.export,
        sender,
        zips: HashSet::new(),
        state: Arc::clone(&state),
    };
    Some(ExportGuard {
        reporter: reporter.clone(),
        export: settings.export,
        sink: Some(reporter.add_sink(sink)),
        copier: Some(copier),
        journal,
        root,
        run_dir,
        shown,
        state,
    })
}

#[derive(Default)]
struct State {
    /// Whether the run failed something: a scenario, an iteration or the run itself.
    red: bool,
    zips: usize,
    reports: usize,
    failure: Option<String>,
}

impl State {
    /// Keeps the first failure, which [`ExportGuard`] notes.
    fn failed(&mut self, failure: String) {
        self.failure.get_or_insert(failure);
    }
}

/// One copy for the copier.
enum Job {
    Zip { source: PathBuf, iteration_id: String },
    Report(PathBuf),
}

/// The sink: it decides what the export takes and hands each copy to the copier.
struct Exporter {
    export: Export,
    sender: Sender<Job>,
    /// The zips handed to the copier, by their path on this host: a zip holds one or more bundles, and each has a
    /// record.
    zips: HashSet<String>,
    state: Arc<Mutex<State>>,
}

impl avl_base::report::Sink for Exporter {
    fn write(&mut self, record: &Record) {
        match record.event {
            Kind::TraceReady => {
                if let Some(trace) = data::<TraceReady>(record) {
                    self.trace_ready(trace);
                }
            }
            Kind::IterationReported => {
                if let Some(reported) = data::<IterationReported>(record) {
                    if reported.status != Status::Passed {
                        lock(&self.state).red = true;
                    }
                    self.send(Job::Report(PathBuf::from(reported.report_path)));
                }
            }
            Kind::RunFinished if data::<RunFinished>(record).is_some_and(|finished| finished.exit_code != 0) => {
                lock(&self.state).red = true;
            }
            _ => {}
        }
    }
}

impl Exporter {
    /// Hands the zip of the bundle to the copier, once, when the export takes it.
    fn trace_ready(&mut self, trace: TraceReady) {
        let passed = trace.status == BundleStatus::Passed.as_str();
        if !passed {
            lock(&self.state).red = true;
        }
        let wanted = match self.export {
            Export::Off => false,
            Export::Failed => !passed,
            Export::All => true,
        };
        if wanted && self.zips.insert(trace.zip.clone()) {
            self.send(Job::Zip {
                source: PathBuf::from(trace.zip),
                iteration_id: trace.iteration_id,
            });
        }
    }

    fn send(&self, job: Job) {
        if self.sender.send(job).is_err() {
            lock(&self.state).failed("the copier of the export stopped".to_owned());
        }
    }
}

/// The thread that makes the copies, in the order of the records.
struct Copier {
    root: PathBuf,
    run_id: String,
    state: Arc<Mutex<State>>,
}

impl Copier {
    fn run(&self, jobs: &Receiver<Job>) {
        for job in jobs {
            match job {
                Job::Zip { source, iteration_id } => {
                    let copied = self.copy_zip(&source, &iteration_id);
                    let mut state = lock(&self.state);
                    match copied {
                        Ok(()) => state.zips += 1,
                        Err(failure) => state.failed(failure),
                    }
                }
                Job::Report(report) => {
                    let copied = self.copy_report(&report);
                    let mut state = lock(&self.state);
                    match copied {
                        Ok(()) => state.reports += 1,
                        Err(failure) => state.failed(format!("cannot export the report {}: {failure}", report.display())),
                    }
                }
            }
        }
    }

    fn copy_zip(&self, source: &Path, iteration_id: &str) -> Result<(), String> {
        let directory = journal::traces_dir(&self.root, &self.run_id, iteration_id).map_err(|refusal| refusal.message)?;
        let name = source
            .file_name()
            .ok_or_else(|| format!("the trace zip {} has no file name", source.display()))?;
        create_private_dir(&directory).map_err(|refusal| refusal.message)?;
        copy_file(source, &directory.join(name)).map_err(|error| error.to_string())
    }

    /// Copies the report at `report` into the reports of the export, with the files beside it that share its name:
    /// `<iteration>.xml` and the directory `<iteration>-evidence`. A missing sibling is no failure: a green iteration
    /// has no evidence.
    fn copy_report(&self, report: &Path) -> Result<(), String> {
        let directory = journal::run_dir(&self.root, &self.run_id)
            .map_err(|refusal| refusal.message)?
            .join(VM_RUN_REPORTS_DIR);
        create_private_dir(&directory).map_err(|refusal| refusal.message)?;
        let name = report.file_name().ok_or("the report path has no file name")?;
        copy_file(report, &directory.join(name)).map_err(|error| error.to_string())?;
        let xml = report.with_extension("xml");
        if xml.is_file() {
            copy_file(&xml, &directory.join(xml.file_name().unwrap_or_default())).map_err(|error| error.to_string())?;
        }
        let stem = report.file_stem().unwrap_or_default().to_string_lossy();
        let evidence_name = format!("{stem}-evidence");
        let evidence = report.with_file_name(&evidence_name);
        if evidence.is_dir() {
            copy_tree(&evidence, &directory.join(&evidence_name)).map_err(|error| error.to_string())?;
        }
        Ok(())
    }
}

/// Copies the regular files of the tree `source` into `destination`, with the directories that hold them.
fn copy_tree(source: &Path, destination: &Path) -> io::Result<()> {
    create_private_dir(destination).map_err(|refusal| io::Error::other(refusal.message))?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let kind = entry.file_type()?;
        let target = destination.join(entry.file_name());
        if kind.is_dir() {
            copy_tree(&entry.path(), &target)?;
        } else if kind.is_file() {
            copy_file(&entry.path(), &target)?;
        }
    }
    Ok(())
}

/// [`fscopy::clone_or_copy`], which removes what a failed copy left at `destination`, so no part of a file looks
/// whole. A destination that was there before the copy stays.
fn copy_file(source: &Path, destination: &Path) -> io::Result<()> {
    fscopy::clone_or_copy(source, destination).inspect_err(|error| {
        if error.kind() != io::ErrorKind::AlreadyExists {
            let _ = fs::remove_file(destination);
        }
    })
}

/// Removes the export of the run, then the directories above it up to the export root while they are empty. So a run
/// that exports nothing leaves no output that `bd-host` would copy back over the export of an earlier run.
fn remove_export(root: &Path, run_dir: &Path) {
    let _ = fs::remove_dir_all(run_dir);
    let _ = fs::remove_dir(root.join(VM_RUNS_DIR));
    let _ = fs::remove_dir(root);
}

fn data<T: DeserializeOwned>(record: &Record) -> Option<T> {
    serde_json::from_str(record.data.as_ref()?.get()).ok()
}
