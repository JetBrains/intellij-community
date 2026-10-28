//! The requests of a run: Start, Follow, Cancel, Fetch, Forget and Runs. The run itself belongs to its supervisor;
//! these requests only start it, write its cancel request and read its directory. No request signals a pid.
//!
//! A follow reads the run files on its own thread, and a second thread sends its heartbeats. The two share the channel
//! through a mutex, so a slow read of the disk holds no heartbeat back, and a heartbeat never comes between the frames
//! of one reply. Between two reads the follow waits for a change of the run files through [`Watch`].

use std::fs::{self, File, OpenOptions};
use std::io::{self, Read};
use std::os::fd::{AsRawFd, RawFd};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::{Condvar, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use yonder_link::config::is_inner_path;
use yonder_link::proto::{FileKind, Reply, RunSpec, is_run_id};
use yonder_link::{Channel, LinkError};

use crate::files::append_private;
use crate::refused::{AGENT_ERROR, BAD_REQUEST, BAD_RUN_ID, RUN_EXISTS, Refusal, SPAWN_FAILED, refused, reply};
use crate::run::{ExitRecord, RunDir, SUPERVISOR_LOG, epoch_ms, is_line_start, read_events};
use crate::serve::{Agent, check_holder, log};
use crate::watch::Watch;
use crate::{child, git, tuning};

/// The longest wait of a follow for a change of the run files. A run that lost its supervisor changes no file, so this
/// bounds the time until the follow ends it.
const FOLLOW_RECHECK: Duration = Duration::from_secs(1);

/// How often a held follow looks at its hold file, see [`tuning::follow_hold`].
const HOLD_POLL: Duration = Duration::from_millis(10);

/// The most bytes of the log that one read of a follow takes.
const FOLLOW_READ: usize = 1024 * 1024;

/// The deadline of each write to a client that follows or fetches.
const WRITE_DEADLINE: Duration = Duration::from_secs(60);

impl Agent {
    /// Makes the run directory, starts `yonderd supervise` in a session of its own, and answers at once. A refused
    /// start deletes the ref that the pack of the run made, unless a kept run has the same id. It never deletes the
    /// ref of another run.
    pub(crate) fn start(&self, spec: &RunSpec) -> Result<Reply, Refusal> {
        let started = self.start_checked(spec);
        if let Err(refusal) = &started
            && refusal.code != RUN_EXISTS
            && is_run_id(&spec.run)
            && spec.reference == run_ref(&spec.run)
        {
            let repo = self.home.expand(&spec.repo);
            if git::check_repo(&repo).is_ok()
                && let Err(error) = git::delete_ref(&repo, &spec.reference)
            {
                log(&format!("cannot delete {} in {}: {error}", spec.reference, repo.display()));
            }
        }
        started
    }

    fn start_checked(&self, spec: &RunSpec) -> Result<Reply, Refusal> {
        if !is_run_id(&spec.run) {
            return Err(refused(BAD_RUN_ID, format!("{:?} cannot name a run directory", spec.run)));
        }
        check_holder(&spec.holder)?;
        if spec.command.is_empty() {
            return Err(refused(BAD_REQUEST, "the run has no command"));
        }
        if !git::is_commit_id(&spec.commit) {
            return Err(refused(BAD_REQUEST, format!("{:?} is not a full commit id", spec.commit)));
        }
        if spec.reference != run_ref(&spec.run) {
            return Err(refused(
                BAD_REQUEST,
                format!("{:?} is not the ref {} of the run", spec.reference, run_ref(&spec.run)),
            ));
        }
        if let Some(bad) = spec.fetch.iter().find(|dir| !is_inner_path(dir)) {
            return Err(refused(
                BAD_REQUEST,
                format!("the fetch directory {bad:?} is not a path inside the slot"),
            ));
        }
        self.repo(&spec.repo)?;
        self.git.check()?;
        let (run, lock) = self.runs().create(spec)?;
        if let Err(refusal) = self.spawn_supervisor(&run, &lock) {
            let _ = fs::remove_dir_all(run.path());
            return Err(refusal);
        }
        log(&format!("started the run {} of {}", spec.run, spec.holder));
        self.wake_keeper(self.home.expand(&spec.repo));
        Ok(Reply::Started { run: spec.run.clone() })
    }

    /// Starts the supervisor with the descriptor of the flock of the run. The supervisor keeps the flock after this
    /// process closes its own descriptor.
    fn spawn_supervisor(&self, run: &RunDir, lock: &File) -> Result<(), Refusal> {
        let spawn_failed = |error: io::Error| refused(SPAWN_FAILED, format!("cannot start the supervisor of the run: {error}"));
        let stderr = append_private(&run.path().join(SUPERVISOR_LOG)).map_err(spawn_failed)?;
        let descriptor: RawFd = lock.as_raw_fd();
        let mut command = Command::new(&self.exe);
        command
            .arg("supervise")
            .arg(run.path())
            .arg("--lock-fd")
            .arg(descriptor.to_string())
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(stderr);
        child::detach(&mut command, Some(descriptor));
        let mut child = command.spawn().map_err(spawn_failed)?;
        if let Err(error) = run.write_pid(child.id()) {
            log(&format!(
                "cannot write the pid of the supervisor of {}: {error}",
                run.path().display()
            ));
        }
        // The thread reaps the supervisor at its end. It lives as long as the run, and a run ends by itself.
        thread::spawn(move || child.wait());
        Ok(())
    }

    /// Sends the events of the run from `offset`, a heartbeat while the run is quiet, and its end: the phases, then the
    /// exit code or the refusal.
    pub(crate) fn follow(&self, channel: &mut Channel, run: &str, offset: u64) -> Result<(), LinkError> {
        let run = match self.runs().get(run) {
            Ok(run) => run,
            Err(refusal) => return channel.send(&reply(&refusal)),
        };
        match is_line_start(&run.events_path(), offset) {
            Ok(true) => {}
            Ok(false) => {
                return channel.send(&Reply::refused(
                    BAD_REQUEST,
                    format!("the offset {offset} is not the start of a line of the log"),
                ));
            }
            Err(error) => return channel.send(&Reply::refused(AGENT_ERROR, format!("cannot read the log: {error}"))),
        }
        channel.set_write_deadline(WRITE_DEADLINE)?;
        // The watch comes before the first read, so no change between a read and a wait is lost.
        let events_path = run.events_path();
        let mut watch = Watch::new(&[&events_path, run.path()]);
        let started_ms = run.started_ms();
        let shared = FollowChannel::new(channel);
        thread::scope(|scope| {
            scope.spawn(|| shared.beat(tuning::heartbeat(), started_ms));
            // The heartbeat thread ends on every way out of the follow, a panic too, so the scope never waits for it.
            let _finish = Finish(&shared);
            follow_to_end(&run, offset, &shared, &mut watch)
        })
    }

    /// Writes the cancel request of the run, which its supervisor polls, and answers at once. A run without a
    /// supervisor ends here.
    pub(crate) fn cancel(&self, run_id: &str) -> Result<Reply, Refusal> {
        let run = self.runs().get(run_id)?;
        let ended = run
            .exit()
            .map_err(|error| refused(AGENT_ERROR, format!("cannot read the end of the run: {error}")))?;
        if ended.is_some() {
            return Ok(Reply::Done {
                message: format!("the run {run_id} has ended already"),
            });
        }
        if run.end_if_lost() {
            return Ok(Reply::Done {
                message: format!("the run {run_id} had lost its supervisor; it has ended"),
            });
        }
        run.request_cancel()
            .map_err(|error| refused(AGENT_ERROR, format!("cannot write the cancel request of {run_id}: {error}")))?;
        log(&format!("asked the supervisor of the run {run_id} to interrupt it"));
        Ok(Reply::Done {
            message: format!("the supervisor of the run {run_id} has the cancel request"),
        })
    }

    /// Sends each staged file of an ended run, then the counts.
    pub(crate) fn fetch(&self, channel: &mut Channel, run_id: &str) -> Result<(), LinkError> {
        let run = match self.runs().get(run_id) {
            Ok(run) => run,
            Err(refusal) => return channel.send(&reply(&refusal)),
        };
        match run.exit() {
            Ok(Some(_)) => {}
            Ok(None) => {
                return channel.send(&Reply::refused(
                    BAD_REQUEST,
                    format!("the run {run_id} goes on; fetch its files after its end"),
                ));
            }
            Err(error) => return channel.send(&Reply::refused(AGENT_ERROR, format!("cannot read the end of the run: {error}"))),
        }
        let files = match run_files(&run) {
            Ok(files) => files,
            Err(error) => return channel.send(&Reply::refused(AGENT_ERROR, format!("cannot read the stage: {error}"))),
        };
        channel.set_write_deadline(WRITE_DEADLINE)?;
        let (mut count, mut bytes) = (0u64, 0u64);
        for staged in files {
            let (file, size) = match open_regular(&staged.path) {
                Ok(opened) => opened,
                Err(error) => {
                    return channel.send(&Reply::refused(
                        AGENT_ERROR,
                        format!("cannot read {}: {error}", staged.path.display()),
                    ));
                }
            };
            channel.send(&Reply::File {
                path: staged.relative,
                size,
                executable: staged.executable,
                kind: staged.kind,
            })?;
            let sent = channel.send_stream(&mut file.take(size), &mut |_| {})?;
            if sent != size {
                return channel.send(&Reply::refused(
                    AGENT_ERROR,
                    format!("{} shrank from {size} to {sent} bytes while it was sent", staged.path.display()),
                ));
            }
            bytes += sent;
            count += 1;
        }
        channel.send(&Reply::Fetched { files: count, bytes })
    }

    /// Removes an ended run and its stage.
    pub(crate) fn forget(&self, run_id: &str) -> Result<Reply, Refusal> {
        let run = self.runs().get(run_id)?;
        run.end_if_lost();
        let ended = run
            .exit()
            .map_err(|error| refused(AGENT_ERROR, format!("cannot read the end of the run: {error}")))?;
        if ended.is_none() {
            return Err(refused(BAD_REQUEST, format!("the run {run_id} goes on; cancel it first")));
        }
        fs::remove_dir_all(run.path()).map_err(|error| refused(AGENT_ERROR, format!("cannot remove the run {run_id}: {error}")))?;
        Ok(Reply::Done {
            message: format!("forgot the run {run_id}"),
        })
    }

    /// The kept runs.
    pub(crate) fn list_runs(&self) -> Reply {
        Reply::Runs {
            runs: self.runs().list().iter().filter_map(|run| run.info().ok()).collect(),
        }
    }
}

/// The ref that the pack of the run `run` makes: `refs/yonder/<run>`.
pub(crate) fn run_ref(run: &str) -> String {
    format!("{}{run}", git::REF_PREFIX)
}

/// The loop of a follow: it reads the run files and sends what is new, until the end of the run.
fn follow_to_end(run: &RunDir, offset: u64, channel: &FollowChannel<'_>, watch: &mut Watch) -> Result<(), LinkError> {
    let hold = tuning::follow_hold();
    let mut offset = offset;
    loop {
        if let Some(hold) = &hold {
            while hold.exists() {
                thread::sleep(HOLD_POLL);
            }
        }
        channel.check()?;
        // The end is read before the log: the supervisor writes the last event before `exit.json`.
        let ended = match run.exit_with_phases() {
            Ok(ended) => ended,
            Err(error) => return channel.send(&[Reply::refused(AGENT_ERROR, format!("cannot read the end of the run: {error}"))]),
        };
        let events = match read_events(&run.events_path(), offset, FOLLOW_READ) {
            Ok(events) => events,
            Err(error) => return channel.send(&[Reply::refused(AGENT_ERROR, format!("cannot read the log: {error}"))]),
        };
        if let Some((_, end)) = events.last() {
            offset = *end;
            let replies: Vec<Reply> = events
                .into_iter()
                .map(|(event, end)| Reply::Output {
                    stream: event.stream,
                    text: event.text,
                    end,
                })
                .collect();
            channel.send(&replies)?;
            continue;
        }
        if let Some((ended, phases)) = ended {
            let last = match ended {
                ExitRecord::Code { code } => Reply::Exit { code },
                ExitRecord::Refused { refused } => Reply::refused(&refused.code, refused.message),
            };
            return channel.send(&[Reply::Phases { phases }, last]);
        }
        if run.end_if_lost() {
            continue;
        }
        watch.wait(FOLLOW_RECHECK);
    }
}

/// Ends the heartbeat thread of a follow when it drops.
struct Finish<'f, 'c>(&'f FollowChannel<'c>);

impl Drop for Finish<'_, '_> {
    fn drop(&mut self) {
        self.0.finish();
    }
}

/// The channel of a follow, which its follow thread and its heartbeat thread share.
struct FollowChannel<'c> {
    state: Mutex<FollowState<'c>>,
    /// Wakes the heartbeat thread at the end of the follow.
    wake: Condvar,
}

struct FollowState<'c> {
    channel: &'c mut Channel,
    /// When the last reply went out.
    sent: Instant,
    /// Set at the end of the follow, which ends the heartbeat thread.
    finished: bool,
    /// Why a heartbeat failed. The follow thread ends with it.
    failed: Option<LinkError>,
}

impl<'c> FollowChannel<'c> {
    fn new(channel: &'c mut Channel) -> Self {
        Self {
            state: Mutex::new(FollowState {
                channel,
                sent: Instant::now(),
                finished: false,
                failed: None,
            }),
            wake: Condvar::new(),
        }
    }

    fn lock(&self) -> MutexGuard<'_, FollowState<'c>> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Sends `replies` in one write, with no heartbeat between them.
    fn send(&self, replies: &[Reply]) -> Result<(), LinkError> {
        let mut state = self.lock();
        if let Some(error) = state.failed.take() {
            return Err(error);
        }
        state.channel.send_all(replies)?;
        state.sent = Instant::now();
        Ok(())
    }

    /// The failure of a heartbeat, if one failed.
    fn check(&self) -> Result<(), LinkError> {
        self.lock().failed.take().map_or(Ok(()), Err)
    }

    fn finish(&self) {
        self.lock().finished = true;
        self.wake.notify_all();
    }

    /// Sends a heartbeat each time no reply went out for `every`, until [`FollowChannel::finish`] or a failed send.
    fn beat(&self, every: Duration, started_ms: u64) {
        let mut state = self.lock();
        while !state.finished {
            let due = state.sent + every;
            let now = Instant::now();
            if now < due {
                state = self.wake.wait_timeout(state, due - now).unwrap_or_else(PoisonError::into_inner).0;
                continue;
            }
            let heartbeat = Reply::Heartbeat {
                elapsed_ms: epoch_ms(SystemTime::now()).saturating_sub(started_ms),
            };
            if let Err(error) = state.channel.send(&heartbeat) {
                state.failed = Some(error);
                return;
            }
            state.sent = Instant::now();
        }
    }
}

/// Opens a staged file without following a link and without a wait on a FIFO, and answers it with the size of the
/// opened file. Anything but a regular file is refused.
fn open_regular(path: &Path) -> io::Result<(File, u64)> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NONBLOCK | libc::O_NOFOLLOW)
        .open(path)?;
    let metadata = file.metadata()?;
    if !metadata.is_file() {
        return Err(io::Error::new(io::ErrorKind::InvalidInput, "it is not a regular file"));
    }
    Ok((file, metadata.len()))
}

/// A file of the stage.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct StagedFile {
    /// The path relative to the slot root, with `/` separators.
    pub(crate) relative: String,
    pub(crate) path: PathBuf,
    pub(crate) executable: bool,
    pub(crate) kind: FileKind,
}

/// The files that a fetch sends, in this order: the staged entries of the fetch directories, the files of each output,
/// and the patch of the changes.
pub(crate) fn run_files(run: &RunDir) -> io::Result<Vec<StagedFile>> {
    let mut files = Vec::new();
    staged_files(&run.stage_path(), "", &FileKind::Staged, &mut files)?;
    for root in run.output_roots()? {
        let path = run.outputs_path().join(&root);
        let kind = FileKind::Output { root: root.clone() };
        let metadata = fs::symlink_metadata(&path)?;
        if metadata.is_dir() {
            staged_files(&path, &root, &kind, &mut files)?;
        } else if metadata.is_file() {
            files.push(StagedFile {
                relative: root,
                path,
                executable: metadata.permissions().mode() & 0o111 != 0,
                kind,
            });
        }
    }
    let changes = run.changes_path();
    if changes.is_file() {
        files.push(StagedFile {
            relative: "changes.patch".to_owned(),
            path: changes,
            executable: false,
            kind: FileKind::Changes,
        });
    }
    Ok(files)
}

/// Adds each regular file under `dir` to `files` as a file of `kind`, in name order. A missing stage has no files. A
/// link and any other entry that is neither a file nor a directory is skipped.
pub(crate) fn staged_files(dir: &Path, prefix: &str, kind: &FileKind, files: &mut Vec<StagedFile>) -> io::Result<()> {
    let entries = match fs::read_dir(dir) {
        Ok(entries) => entries,
        Err(error) if error.kind() == io::ErrorKind::NotFound && prefix.is_empty() => return Ok(()),
        Err(error) => return Err(error),
    };
    let mut entries = entries.collect::<io::Result<Vec<_>>>()?;
    entries.sort_by_key(fs::DirEntry::file_name);
    for entry in entries {
        let name = entry.file_name().to_string_lossy().into_owned();
        let relative = if prefix.is_empty() { name } else { format!("{prefix}/{name}") };
        let metadata = fs::symlink_metadata(entry.path())?;
        if metadata.is_dir() {
            staged_files(&entry.path(), &relative, kind, files)?;
        } else if metadata.is_file() {
            files.push(StagedFile {
                relative,
                path: entry.path(),
                executable: metadata.permissions().mode() & 0o111 != 0,
                kind: kind.clone(),
            });
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests;
