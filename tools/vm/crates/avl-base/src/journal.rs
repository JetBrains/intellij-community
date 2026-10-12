//! A run's progress stream kept on disk, as the run happens.
//!
//! A journal is `<runtime root>/runs/<runId>/events.ndjson`: every [`Record`] the command publishes, one per line,
//! in order, whatever the output form. It is the one live record of a run. The trace viewer reads it to show a
//! run in flight, and a person or an agent can read it after a run that printed nothing.
//!
//! A journal is evidence and never a verdict. A journal that cannot be written says so once, through the
//! reporter, and the run goes on.

use std::fs::{File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use avl_trace_tools::discover::{VM_RUN_TRACES_DIR, VM_RUNS_DIR};
use avl_wire::progress::{Record, RunFinished};

use crate::config::validate_name;
use crate::fs::create_private_dir;
use crate::refusal::{Exit, OrRefuse, Refusal};
use crate::report::{Reporter, SinkGuard};
use crate::sync::lock;

#[cfg(test)]
mod tests;

/// The journal's name inside the run's directory.
pub const FILE_NAME: &str = "events.ndjson";

/// The run's directory under the runtime root: `runs/<runId>`. The run id is validated, because it becomes a path
/// component.
pub fn run_dir(runtime_root: &Path, run_id: &str) -> Result<PathBuf, Refusal> {
    validate_name(run_id, "run id")?;
    Ok(runtime_root.join(VM_RUNS_DIR).join(run_id))
}

/// Where an iteration's pulled traces go: `runs/<runId>/traces/<iterationId>`, one zip for each pull. The trace
/// viewer finds them there without reading a report.
pub fn traces_dir(runtime_root: &Path, run_id: &str, iteration_id: &str) -> Result<PathBuf, Refusal> {
    let run = run_dir(runtime_root, run_id)?;
    validate_name(iteration_id, "iteration id")?;
    Ok(run.join(VM_RUN_TRACES_DIR).join(iteration_id))
}

#[derive(Default)]
struct JournalFile {
    file: Option<File>,
    failed: Option<io::Error>,
}

/// One run's journal, open for appending. [`Journal::sink`] is what the reporter writes into.
pub struct Journal {
    path: PathBuf,
    file: Arc<Mutex<JournalFile>>,
}

impl Journal {
    /// Creates the run's directory (0700) and its journal (0600). A journal that already exists is refused: a run
    /// id names one run, and two runs appending to one journal would make both unreadable.
    pub fn create(runtime_root: &Path, run_id: &str) -> Result<Self, Refusal> {
        let directory = run_dir(runtime_root, run_id)?;
        create_private_dir(&directory)?;
        let path = directory.join(FILE_NAME);
        let file = open_private_new(&path).or_refuse("state_write_failed", Exit::FAILURE, || {
            format!("cannot create the run journal {}", path.display())
        })?;
        Ok(Self {
            path,
            file: Arc::new(Mutex::new(JournalFile {
                file: Some(file),
                failed: None,
            })),
        })
    }

    /// The sink that appends each record. After the first failed write it writes nothing more, and
    /// [`Journal::close`] reports the failure.
    pub fn sink(&self) -> impl FnMut(&Record) + Send + 'static {
        let file = self.file.clone();
        move |record: &Record| {
            let mut journal = lock(&file);
            let JournalFile {
                file: Some(handle),
                failed: None,
            } = &mut *journal
            else {
                return;
            };
            let written = serde_json::to_vec(record).map_err(io::Error::from).and_then(|mut line| {
                line.push(b'\n');
                handle.write_all(&line)
            });
            if let Err(error) = written {
                journal.failed = Some(error);
            }
        }
    }

    /// Closes the journal, and answers the first write failure, if any.
    pub fn close(self) -> Result<(), Refusal> {
        let mut journal = lock(&self.file);
        let file = journal.file.take();
        drop(file);
        match journal.failed.take() {
            Some(error) => Err(Refusal::new(
                "state_write_failed",
                Exit::FAILURE,
                format!("the run journal {} stopped: {error}", self.path.display()),
            )),
            None => Ok(()),
        }
    }
}

fn open_private_new(path: &Path) -> io::Result<File> {
    let mut options = OpenOptions::new();
    options.append(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options.open(path)
}

/// A journal attached to a reporter; dropping it removes the sink and closes the journal, and a write failure is
/// noted through the reporter.
#[must_use = "the journal is detached when the guard drops"]
pub struct AttachGuard {
    reporter: Reporter,
    attached: Option<(SinkGuard, Journal)>,
}

impl Drop for AttachGuard {
    fn drop(&mut self) {
        if let Some((sink, journal)) = self.attached.take() {
            drop(sink);
            if let Err(refusal) = journal.close() {
                self.reporter.note(refusal.message, None);
            }
        }
    }
}

/// Opens the run's journal and adds it to the reporter's sinks until the guard drops. When the journal cannot be
/// opened, the reason is noted and the guard does nothing: a run without a journal still runs.
pub fn attach(runtime_root: &Path, reporter: &Reporter, run_id: &str) -> AttachGuard {
    let attached = match Journal::create(runtime_root, run_id) {
        Ok(journal) => Some((reporter.add_sink(journal.sink()), journal)),
        Err(refusal) => {
            reporter.note(format!("no run journal: {}", refusal.message), None);
            None
        }
    };
    AttachGuard {
        reporter: reporter.clone(),
        attached,
    }
}

/// The last record of a run: the exit status, and the refusal's code and the first line of its message for a run
/// that failed.
pub fn run_finished(refusal: Option<&Refusal>) -> RunFinished {
    match refusal {
        None => RunFinished {
            exit_code: 0,
            code: None,
            message: None,
        },
        Some(refused) => RunFinished {
            exit_code: i32::from(refused.exit),
            message: refused.message.lines().next().map(str::to_owned),
            code: Some(refused.code.clone().into_owned()),
        },
    }
}
