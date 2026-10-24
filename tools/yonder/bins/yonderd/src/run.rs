//! The run directory: `~/.cache/yonderd/runs/<run>/`.
//!
//! | File | Content |
//! |---|---|
//! | `spec.json` | the [`RunSpec`]; its time of change is the start of the run |
//! | `events.log` | one [`Event`] per line, as JSON |
//! | `exit.json` | the [`ExitRecord`], when the run has ended; its time of change is the end of the run |
//! | `lock` | the flock of the supervisor, held for its whole life |
//! | `cancel` | the cancel request of a client, which the supervisor polls |
//! | `pid` | the pid of the supervisor, as information only |
//! | `stage/` | the staged entries, under their paths relative to the slot root |
//! | `supervisor.log` | the stderr of the supervisor, such as a panic |
//!
//! The supervisor writes the last event before `exit.json`, so a reader that sees `exit.json` and then reads the log to
//! its end has every event. A run is alive while a process holds the flock of `lock`. A run that is not alive and has no
//! `exit.json` lost its supervisor, and [`RunDir::end_if_lost`] ends it. The directory has the mode 0700, and each
//! file the mode 0600.

use std::fs::{self, File};
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use yonder_link::proto::{RunInfo, RunSpec, Stream, is_run_id};

use crate::files::{append_private, open_private, private_dir, try_flock, write_private};
use crate::refused::{AGENT_ERROR, BAD_RUN_ID, RUN_EXISTS, Refusal, UNKNOWN_RUN, refused};

const SPEC: &str = "spec.json";
const EVENTS: &str = "events.log";
const EXIT: &str = "exit.json";
const PID: &str = "pid";
const LOCK: &str = "lock";
const CANCEL: &str = "cancel";
const STAGE: &str = "stage";
pub(crate) const SUPERVISOR_LOG: &str = "supervisor.log";

/// The largest text of one event. A longer line becomes several events, so each reply stays far below the message
/// limit of the channel.
pub(crate) const MAX_EVENT_TEXT: usize = 64 * 1024;

/// How long the agent keeps a run after its end.
pub(crate) const KEEP: Duration = Duration::from_secs(24 * 3600);

/// The exit code that [`RunInfo::exit`] gives a run that ended with a refusal. A client also exits with it.
pub(crate) const REFUSED_EXIT: i32 = 255;

/// One line of the output of a run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct Event {
    pub(crate) stream: Stream,
    pub(crate) text: String,
}

/// How a run ended: `{"code": N}`, or `{"refused": {"code": "…", "message": "…"}}`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(untagged)]
pub(crate) enum ExitRecord {
    Code { code: i32 },
    Refused { refused: RefusedRecord },
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct RefusedRecord {
    pub(crate) code: String,
    pub(crate) message: String,
}

impl ExitRecord {
    pub(crate) fn refused(refusal: &Refusal) -> Self {
        Self::Refused {
            refused: RefusedRecord {
                code: refusal.code.to_string(),
                message: refusal.message.clone(),
            },
        }
    }

    /// The exit code of [`RunInfo::exit`].
    pub(crate) const fn code(&self) -> i32 {
        match self {
            Self::Code { code } => *code,
            Self::Refused { .. } => REFUSED_EXIT,
        }
    }
}

/// One run directory.
#[derive(Clone, Debug)]
pub(crate) struct RunDir {
    path: PathBuf,
}

impl RunDir {
    pub(crate) const fn new(path: PathBuf) -> Self {
        Self { path }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    pub(crate) fn events_path(&self) -> PathBuf {
        self.path.join(EVENTS)
    }

    pub(crate) fn stage_path(&self) -> PathBuf {
        self.path.join(STAGE)
    }

    pub(crate) fn spec(&self) -> io::Result<RunSpec> {
        let path = self.path.join(SPEC);
        let text = fs::read(&path)?;
        serde_json::from_slice(&text).map_err(|error| io::Error::new(io::ErrorKind::InvalidData, format!("{}: {error}", path.display())))
    }

    /// The end of the run, or `None` while it goes on.
    pub(crate) fn exit(&self) -> io::Result<Option<ExitRecord>> {
        let path = self.path.join(EXIT);
        match fs::read(&path) {
            Ok(text) => serde_json::from_slice(&text)
                .map(Some)
                .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, format!("{}: {error}", path.display()))),
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
            Err(error) => Err(error),
        }
    }

    pub(crate) fn write_exit(&self, exit: &ExitRecord) -> io::Result<()> {
        let json = serde_json::to_vec(exit).map_err(io::Error::other)?;
        write_private(&self.path.join(EXIT), &json)
    }

    pub(crate) fn write_pid(&self, pid: u32) -> io::Result<()> {
        write_private(&self.path.join(PID), pid.to_string().as_bytes())
    }

    pub(crate) fn lock_path(&self) -> PathBuf {
        self.path.join(LOCK)
    }

    /// Whether a supervisor holds the flock of the run.
    pub(crate) fn is_alive(&self) -> bool {
        // A missing lock file is a run without a supervisor. Another error counts as alive, so it ends no run.
        match File::open(self.lock_path()) {
            Ok(file) => !try_flock(&file).unwrap_or(false),
            Err(error) => error.kind() != io::ErrorKind::NotFound,
        }
    }

    /// Asks the supervisor to interrupt the run.
    pub(crate) fn request_cancel(&self) -> io::Result<()> {
        write_private(&self.path.join(CANCEL), b"")
    }

    pub(crate) fn cancel_requested(&self) -> bool {
        self.path.join(CANCEL).exists()
    }

    /// Writes the end of a run that lost its supervisor, such as after a kill or a reboot: no process holds its flock,
    /// and it has no `exit.json`. Answers whether it did.
    pub(crate) fn end_if_lost(&self) -> bool {
        // The supervisor writes `exit.json` before its flock goes, so the end is read after the flock test.
        if self.is_alive() || !matches!(self.exit(), Ok(None)) {
            return false;
        }
        let lost = refused(AGENT_ERROR, "the supervisor of the run ended without an exit code");
        self.write_exit(&ExitRecord::refused(&lost)).is_ok()
    }

    /// The start of the run, as epoch milliseconds.
    pub(crate) fn started_ms(&self) -> u64 {
        fs::metadata(self.path.join(SPEC))
            .and_then(|metadata| metadata.modified())
            .map_or(0, epoch_ms)
    }

    /// The end of the run, or `None` while it goes on.
    pub(crate) fn ended(&self) -> Option<SystemTime> {
        fs::metadata(self.path.join(EXIT)).and_then(|metadata| metadata.modified()).ok()
    }

    pub(crate) fn info(&self) -> io::Result<RunInfo> {
        let spec = self.spec()?;
        Ok(RunInfo {
            run: spec.run,
            holder: spec.holder,
            commit: spec.commit,
            command: spec.command,
            started_ms: self.started_ms(),
            exit: self.exit()?.as_ref().map(ExitRecord::code),
        })
    }
}

/// The parent of the run directories.
#[derive(Clone, Debug)]
pub(crate) struct Runs {
    dir: PathBuf,
}

impl Runs {
    pub(crate) const fn new(dir: PathBuf) -> Self {
        Self { dir }
    }

    /// The kept run `run`.
    pub(crate) fn get(&self, run: &str) -> Result<RunDir, Refusal> {
        if !is_run_id(run) {
            return Err(refused(BAD_RUN_ID, format!("{run:?} cannot name a run directory")));
        }
        let path = self.dir.join(run);
        if path.join(SPEC).is_file() {
            Ok(RunDir::new(path))
        } else {
            Err(refused(UNKNOWN_RUN, format!("the agent keeps no run {run}")))
        }
    }

    /// Makes the directory of a new run with its `spec.json` and an empty `events.log`, and takes the flock of the run
    /// before the spec exists. The caller hands the flock to the supervisor. So each run that a reader can see is alive
    /// or has ended.
    pub(crate) fn create(&self, spec: &RunSpec) -> Result<(RunDir, File), Refusal> {
        if !is_run_id(&spec.run) {
            return Err(refused(BAD_RUN_ID, format!("{:?} cannot name a run directory", spec.run)));
        }
        let agent_error = |error: io::Error| refused(AGENT_ERROR, format!("cannot make the run {}: {error}", spec.run));
        private_dir(&self.dir).map_err(agent_error)?;
        let path = self.dir.join(&spec.run);
        match fs::create_dir(&path) {
            Ok(()) => {}
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {
                return Err(refused(RUN_EXISTS, format!("the agent keeps a run {} already", spec.run)));
            }
            Err(error) => return Err(agent_error(error)),
        }
        let run = RunDir::new(path);
        let written = private_dir(&run.path)
            .and_then(|()| open_private(&run.lock_path()))
            .and_then(|lock| {
                if !try_flock(&lock)? {
                    return Err(io::Error::other("another process holds the lock of the new run"));
                }
                Ok(lock)
            })
            .and_then(|lock| {
                let json = serde_json::to_vec(spec).map_err(io::Error::other)?;
                append_private(&run.events_path())?;
                write_private(&run.path.join(SPEC), &json)?;
                Ok(lock)
            });
        match written {
            Ok(lock) => Ok((run, lock)),
            Err(error) => {
                let _ = fs::remove_dir_all(&run.path);
                Err(agent_error(error))
            }
        }
    }

    /// Every kept run, by id.
    pub(crate) fn list(&self) -> Vec<RunDir> {
        let Ok(entries) = fs::read_dir(&self.dir) else {
            return Vec::new();
        };
        let mut runs: Vec<PathBuf> = entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.join(SPEC).is_file())
            .collect();
        runs.sort();
        runs.into_iter().map(RunDir::new).collect()
    }

    /// Removes each run that ended [`KEEP`] or more before `now`. Answers the ids of the removed runs.
    pub(crate) fn remove_expired(&self, now: SystemTime) -> Vec<String> {
        let mut removed = Vec::new();
        for run in self.list() {
            let expired = run
                .ended()
                .is_some_and(|ended| now.duration_since(ended).is_ok_and(|age| age >= KEEP));
            if expired && fs::remove_dir_all(run.path()).is_ok() {
                removed.push(
                    run.path()
                        .file_name()
                        .map(|name| name.to_string_lossy().into_owned())
                        .unwrap_or_default(),
                );
            }
        }
        removed
    }
}

/// The writer of `events.log`. The stdout and the stderr readers of a run share it. After [`EventLog::close`] it
/// drops each event, so no event follows `exit.json`.
#[derive(Debug)]
pub(crate) struct EventLog {
    file: Mutex<Option<File>>,
}

impl EventLog {
    pub(crate) fn open(path: &Path) -> io::Result<Self> {
        let file = append_private(path)?;
        Ok(Self {
            file: Mutex::new(Some(file)),
        })
    }

    /// Appends one event per piece of `text` of at most [`MAX_EVENT_TEXT`] bytes. Each event is one write, so the
    /// lines of two streams never mix.
    pub(crate) fn write(&self, stream: Stream, text: &str) {
        let mut file = self.file.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        let Some(file) = file.as_mut() else {
            return;
        };
        for piece in pieces(text) {
            let event = Event {
                stream,
                text: piece.to_owned(),
            };
            if let Ok(mut line) = serde_json::to_vec(&event) {
                line.push(b'\n');
                let _ = file.write_all(&line);
            }
        }
    }

    pub(crate) fn close(&self) {
        let mut file = self.file.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        if let Some(file) = file.take() {
            let _ = file.sync_all();
        }
    }
}

/// `text` cut into pieces of at most [`MAX_EVENT_TEXT`] bytes, at char boundaries. An empty text is one empty piece.
fn pieces(text: &str) -> Vec<&str> {
    let mut pieces = Vec::new();
    let mut rest = text;
    while rest.len() > MAX_EVENT_TEXT {
        let mut end = MAX_EVENT_TEXT;
        while !rest.is_char_boundary(end) {
            end -= 1;
        }
        pieces.push(&rest[..end]);
        rest = &rest[end..];
    }
    pieces.push(rest);
    pieces
}

/// The complete events of `events.log` from the byte offset `offset`, each with the offset after it. A line without
/// its end is left for a later read. At most about `limit` bytes are read.
pub(crate) fn read_events(path: &Path, offset: u64, limit: usize) -> io::Result<Vec<(Event, u64)>> {
    let mut file = File::open(path)?;
    file.seek(SeekFrom::Start(offset))?;
    let mut bytes = Vec::new();
    file.take(limit as u64).read_to_end(&mut bytes)?;
    let mut events = Vec::new();
    let mut start = 0;
    while let Some(newline) = bytes[start..].iter().position(|&byte| byte == b'\n') {
        let line = &bytes[start..start + newline];
        start += newline + 1;
        let event = serde_json::from_slice(line).map_err(|error| {
            io::Error::new(
                io::ErrorKind::InvalidData,
                format!(
                    "{}: a line before offset {} is not an event: {error}",
                    path.display(),
                    offset + start as u64
                ),
            )
        })?;
        events.push((event, offset + start as u64));
    }
    if events.is_empty() && bytes.len() == limit {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("{}: a line from offset {offset} is longer than {limit} bytes", path.display()),
        ));
    }
    Ok(events)
}

/// Whether `offset` is the start of a line of `events.log`: 0, or the offset after a line end, within the file.
pub(crate) fn is_line_start(path: &Path, offset: u64) -> io::Result<bool> {
    if offset == 0 {
        return Ok(true);
    }
    let mut file = File::open(path)?;
    if offset > file.metadata()?.len() {
        return Ok(false);
    }
    file.seek(SeekFrom::Start(offset - 1))?;
    let mut byte = [0u8];
    file.read_exact(&mut byte)?;
    Ok(byte[0] == b'\n')
}

pub(crate) fn epoch_ms(time: SystemTime) -> u64 {
    time.duration_since(UNIX_EPOCH)
        .map_or(0, |since| u64::try_from(since.as_millis()).unwrap_or(u64::MAX))
}

#[cfg(test)]
mod tests;
