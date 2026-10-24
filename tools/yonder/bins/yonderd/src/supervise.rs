//! `yonderd supervise <run dir> --lock-fd <descriptor>`: one run, from the lock of its holder to `exit.json`. It is the
//! port of `remote.sh` of BT. `serve` starts it in a session of its own, so a restart or an upgrade of `serve` does not
//! end the run. `serve` takes the flock of the run before the run is visible and hands its descriptor over. The
//! supervisor holds it for its whole life, so a run is alive exactly while its supervisor is.
//!
//! The steps:
//!
//! 1. Take the flock of the holder. A second run of the same holder waits for it, up to an hour.
//! 2. Return the leases of the other holders that no run used for the idle hours.
//! 3. Take the warm lease of the holder, or a new one from `bd lease get`.
//! 4. Wait for the pack, up to `PACK_WAIT_SECONDS` of the protocol. A start can come before the pack of its reference,
//!    so the lock and the lease go on while the pack travels. A commit that the main repository has already needs no
//!    pack. Else the run waits for its reference, which the pack makes after its objects.
//! 5. Check out the commit in the slot. The slot belongs to the holder, so a file that the last run left changed is
//!    discarded.
//! 6. Run the command in the slot root, in its own process group, with stdin `/dev/null`. Its lines go to `events.log`.
//!    When the command exits, the other processes of its group get SIGTERM, and SIGKILL 5 s later.
//! 7. Move the new entries of each `fetch` directory to the stage.
//!
//! Each step is a phase: `lock`, `sweep`, `lease`, `pack_wait`, `checkout`, `command` and `stage`. At the end of each
//! step, whatever its outcome, the supervisor rewrites `phases.json` with the steps that have ended.
//!
//! On every way out it writes the lease file, drops the lock of the holder, deletes the ref of the pack, and writes
//! `exit.json` last. The `cancel` file of the run, or SIGINT, SIGTERM or SIGHUP, interrupts the run. Before the
//! command starts, the run kills the bd or git child of the current step and ends with the exit code 130. After it
//! started, the command group gets SIGINT, and SIGKILL after the grace time. Its progress lines go to `events.log` as
//! stderr events with the prefix `yonder: `.

use std::collections::BTreeSet;
use std::ffi::OsString;
use std::fs::{self, File, Permissions};
use std::io::{self, BufRead, BufReader, Read};
use std::os::fd::{FromRawFd, RawFd};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::panic::{self, AssertUnwindSafe};
use std::path::Path;
use std::process::{Child, Command, ExitStatus, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, mpsc};
use std::thread;
use std::time::{Duration, Instant};

use yonder_link::proto::{Phase, RunSpec, Stream};

use crate::child::Stop;
use crate::files::{FILE_MODE, open_private, private_dir, try_flock};
use crate::home::Home;
use crate::lease::{HolderLock, Lease, Leases, now_seconds};
use crate::refused::{AGENT_ERROR, CHECKOUT_FAILED, PACK_MISSING, Refusal, SPAWN_FAILED, refused};
use crate::run::{EventLog, ExitRecord, MAX_EVENT_TEXT, RunDir};
use crate::{git, tuning};

/// How long a run waits for the other run of its holder.
const LOCK_WAIT: Duration = Duration::from_secs(3600);

/// How often the supervisor looks at the command and at the cancel request.
const POLL: Duration = Duration::from_millis(100);

/// The first pause between two looks for the reference of the run. Each pause doubles, up to [`PACK_POLL_MAX`].
const PACK_POLL_FIRST: Duration = Duration::from_millis(50);

/// The longest pause between two looks for the reference of the run.
const PACK_POLL_MAX: Duration = Duration::from_secs(1);

/// A wait for the pack that is longer than this gets a progress line.
const PACK_WAIT_SAY: Duration = Duration::from_secs(1);

/// How long the supervisor reads the pipes of a command group that has ended. A daemon can keep them open.
const DRAIN: Duration = Duration::from_secs(5);

/// How long the supervisor waits for the command after SIGKILL.
const KILL_WAIT: Duration = Duration::from_secs(10);

/// How long the group of a command that has exited can take to empty by itself.
const GROUP_SETTLE: Duration = Duration::from_millis(500);

/// How long the other processes of the group have after SIGTERM, once the command has exited.
const GROUP_TERM_WAIT: Duration = Duration::from_secs(5);

/// The exit code of a run that a cancel ended before its command started: 128 plus SIGINT.
const INTERRUPTED: i32 = 128 + libc::SIGINT;

/// Set by SIGINT, SIGTERM and SIGHUP.
static SIGNALLED: AtomicBool = AtomicBool::new(false);

extern "C" fn on_signal(_signal: libc::c_int) {
    SIGNALLED.store(true, Ordering::SeqCst);
}

fn install_signal_handlers() {
    for signal in [libc::SIGINT, libc::SIGTERM, libc::SIGHUP] {
        // SAFETY: the handler only stores to an atomic, which is async-signal-safe.
        unsafe {
            libc::signal(signal, on_signal as *const () as libc::sighandler_t);
        }
    }
}

/// Runs the run of `run_dir` to its end, and writes `exit.json`. `lock` is the descriptor of the flock of the run that
/// `serve` took. Without it, the supervisor takes the flock itself.
pub(crate) fn supervise(home: &Home, run_dir: &Path, lock: Option<RawFd>) {
    install_signal_handlers();
    let run = RunDir::new(run_dir.to_path_buf());
    let lock = match adopt_lock(&run, lock) {
        Ok(lock) => lock,
        Err(error) => {
            eprintln!("yonderd supervise: cannot hold the lock of {}: {error}", run_dir.display());
            return;
        }
    };
    let exit = match run_to_end(home, &run) {
        Ok(code) => ExitRecord::Code { code },
        Err(refusal) => ExitRecord::refused(&refusal),
    };
    if let Err(error) = run.write_exit(&exit) {
        eprintln!("yonderd supervise: cannot write the end of {}: {error}", run_dir.display());
    }
    drop(lock);
}

/// The flock of the run: the inherited descriptor, which the command must not inherit in turn, or a new one.
fn adopt_lock(run: &RunDir, descriptor: Option<RawFd>) -> io::Result<File> {
    let Some(descriptor) = descriptor else {
        let file = open_private(&run.lock_path())?;
        return if try_flock(&file)? {
            Ok(file)
        } else {
            Err(io::Error::other("another supervisor holds the run"))
        };
    };
    // SAFETY: fcntl takes plain integers; a bad descriptor answers an error.
    if unsafe { libc::fcntl(descriptor, libc::F_SETFD, libc::FD_CLOEXEC) } == -1 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: `serve` passes an open descriptor of the lock file, and nothing else in this process owns it.
    Ok(unsafe { File::from_raw_fd(descriptor) })
}

/// What the cleanup of a run needs from its steps.
#[derive(Default)]
struct Held {
    lock: Option<HolderLock>,
    lease: Option<Lease>,
}

fn run_to_end(home: &Home, run: &RunDir) -> Result<i32, Refusal> {
    let spec = run
        .spec()
        .map_err(|error| refused(AGENT_ERROR, format!("cannot read the spec of the run: {error}")))?;
    let repo = home.expand(&spec.repo);
    let log = match EventLog::open(&run.events_path()) {
        Ok(log) => Arc::new(log),
        Err(error) => {
            if let Err(error) = git::delete_ref(&repo, &spec.reference) {
                eprintln!("yonderd supervise: cannot delete {} in {}: {error}", spec.reference, repo.display());
            }
            return Err(refused(AGENT_ERROR, format!("cannot open the log of the run: {error}")));
        }
    };
    let leases = Leases::new(home.leases_dir(), repo.clone());
    let say = |message: &str| log.write(Stream::Stderr, &format!("yonder: {message}"));
    let stop = || SIGNALLED.load(Ordering::SeqCst) || run.cancel_requested();
    let mut held = Held::default();
    let outcome = panic::catch_unwind(AssertUnwindSafe(|| steps(&spec, run, &repo, &leases, &log, &stop, &mut held)))
        .unwrap_or_else(|payload| Err(refused(AGENT_ERROR, format!("the supervisor failed: {}", panic_text(&*payload)))));

    if let Some(lease) = &held.lease
        && let Err(error) = leases.record(&spec.holder, lease, now_seconds())
    {
        say(&format!("cannot write the lease file of {}: {error}", spec.holder));
    }
    drop(held.lock);
    if let Err(error) = git::delete_ref(&repo, &spec.reference) {
        say(&format!("cannot delete {} in {}: {error}", spec.reference, repo.display()));
    }
    log.close();
    outcome
}

fn steps(
    spec: &RunSpec,
    run: &RunDir,
    repo: &Path,
    leases: &Leases,
    log: &Arc<EventLog>,
    stop: Stop<'_>,
    held: &mut Held,
) -> Result<i32, Refusal> {
    let mut say = |message: &str| log.write(Stream::Stderr, &format!("yonder: {message}"));
    let stage = run.stage_path();
    private_dir(&stage).map_err(|error| refused(AGENT_ERROR, format!("cannot make {}: {error}", stage.display())))?;
    let mut phases = Phases { run, ended: Vec::new() };

    let Some(lock) = phases.time("lock", || leases.lock(&spec.holder, LOCK_WAIT, stop, &mut say))? else {
        return Ok(INTERRUPTED);
    };
    held.lock = Some(lock);
    if stop() {
        return Ok(INTERRUPTED);
    }
    phases.time("sweep", || {
        leases.sweep(&spec.holder, spec.idle_hours, now_seconds(), stop, &mut say);
    });
    if stop() {
        return Ok(INTERRUPTED);
    }
    let lease = match phases.time("lease", || leases.acquire(&spec.holder, stop, &mut say)) {
        Ok(lease) => lease,
        Err(_) if stop() => return Ok(INTERRUPTED),
        Err(refusal) => return Err(refusal),
    };
    let slot = lease.slot.clone();
    held.lease = Some(lease);
    if stop() {
        return Ok(INTERRUPTED);
    }
    if !phases.time("pack_wait", || wait_for_pack(repo, spec, tuning::pack_wait(), stop, &mut say))? {
        return Ok(INTERRUPTED);
    }
    if let Err(error) = phases.time("checkout", || git::checkout(&slot, &spec.commit, stop)) {
        if stop() {
            return Ok(INTERRUPTED);
        }
        return Err(refused(
            CHECKOUT_FAILED,
            format!("cannot check out {} in {}: {error}", spec.commit, slot.display()),
        ));
    }
    if stop() {
        return Ok(INTERRUPTED);
    }
    let before: Vec<BTreeSet<OsString>> = spec.fetch.iter().map(|dir| entries(&slot.join(dir))).collect();
    let code = phases.time("command", || run_command(spec, &slot, log, stop))?;
    for error in phases.time("stage", || stage_new_entries(&slot, &spec.fetch, &before, &stage)) {
        say(&error);
    }
    Ok(code)
}

/// The steps of a run that have ended, each with its duration.
struct Phases<'a> {
    run: &'a RunDir,
    ended: Vec<Phase>,
}

impl Phases<'_> {
    /// Runs `step`, adds its duration as the phase `name` whatever its outcome, and rewrites `phases.json`.
    fn time<T>(&mut self, name: &str, step: impl FnOnce() -> T) -> T {
        let started = Instant::now();
        let outcome = step();
        self.ended.push(Phase {
            name: name.to_owned(),
            ms: u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX),
        });
        if let Err(error) = self.run.write_phases(&self.ended) {
            eprintln!(
                "yonderd supervise: cannot write the phases of {}: {error}",
                self.run.path().display()
            );
        }
        outcome
    }
}

/// Waits for the pack of the run, up to `deadline`. A commit that `repo` has already needs no pack, because the client
/// sends none then. Else the wait ends when the reference of the run exists: the pack points it at the commit after
/// the commit has landed, so the delete of the reference at the end of the run comes after it. Answers `false` when
/// `stop` ended the wait. A wait longer than [`PACK_WAIT_SAY`] says so once.
fn wait_for_pack(repo: &Path, spec: &RunSpec, deadline: Duration, stop: Stop<'_>, say: &mut dyn FnMut(&str)) -> Result<bool, Refusal> {
    let look = |revision: &str| {
        git::has_commit(repo, revision)
            .map_err(|error| refused(AGENT_ERROR, format!("cannot look for {revision} in {}: {error}", repo.display())))
    };
    if look(&spec.commit)? {
        return Ok(true);
    }
    let started = Instant::now();
    let mut said = false;
    let mut pause = PACK_POLL_FIRST;
    loop {
        if look(&spec.reference)? {
            return Ok(true);
        }
        if started.elapsed() >= deadline {
            return Err(refused(
                PACK_MISSING,
                format!(
                    "the pack of {} did not come in {} s, and {} is missing in {}",
                    spec.reference,
                    deadline.as_secs_f64(),
                    spec.commit,
                    repo.display()
                ),
            ));
        }
        let until = (Instant::now() + pause).min(started + deadline);
        loop {
            if stop() {
                return Ok(false);
            }
            if !said && started.elapsed() > PACK_WAIT_SAY {
                say(&format!("waiting for the pack of {}", spec.commit));
                said = true;
            }
            let left = until.saturating_duration_since(Instant::now());
            if left.is_zero() {
                break;
            }
            thread::sleep(left.min(POLL));
        }
        pause = (pause * 2).min(PACK_POLL_MAX);
    }
}

/// Runs the command of the run in the slot root, in its own process group, and answers its exit code. The other
/// processes of the group end before the answer.
fn run_command(spec: &RunSpec, slot: &Path, log: &Arc<EventLog>, stop: Stop<'_>) -> Result<i32, Refusal> {
    let Some((program, arguments)) = spec.command.split_first() else {
        return Err(refused(SPAWN_FAILED, "the run has no command"));
    };
    let mut child = Command::new(program)
        .args(arguments)
        .current_dir(slot)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .process_group(0)
        .spawn()
        .map_err(|error| refused(SPAWN_FAILED, format!("cannot start `{program}` in {}: {error}", slot.display())))?;
    let group = libc::pid_t::try_from(child.id()).map_err(|error| refused(AGENT_ERROR, error.to_string()))?;
    let (done, readers) = mpsc::channel();
    for (stream, pipe) in [
        (
            Stream::Stdout,
            child.stdout.take().map(|pipe| Box::new(pipe) as Box<dyn Read + Send>),
        ),
        (
            Stream::Stderr,
            child.stderr.take().map(|pipe| Box::new(pipe) as Box<dyn Read + Send>),
        ),
    ] {
        let (log, done) = (Arc::clone(log), done.clone());
        if let Some(pipe) = pipe {
            thread::spawn(move || {
                read_lines(pipe, &mut |line| log.write(stream, line));
                let _ = done.send(());
            });
        }
    }
    drop(done);

    let grace = tuning::grace();
    let mut interrupted: Option<Instant> = None;
    let status = loop {
        match child.try_wait() {
            Ok(Some(status)) => break Some(status),
            Ok(None) => {}
            Err(error) => return Err(refused(AGENT_ERROR, format!("cannot wait for `{program}`: {error}"))),
        }
        if interrupted.is_none() && stop() {
            signal_group(group, libc::SIGINT);
            interrupted = Some(Instant::now());
        }
        if interrupted.is_some_and(|at| at.elapsed() >= grace) {
            log.write(
                Stream::Stderr,
                &format!("yonder: the command did not end {} ms after SIGINT; SIGKILL", grace.as_millis()),
            );
            signal_group(group, libc::SIGKILL);
            // A command that left its group still gets the signal through its pid, which std has not reaped yet.
            let _ = child.kill();
            break wait_up_to(&mut child, KILL_WAIT);
        }
        thread::sleep(POLL);
    };
    end_group(group, log);
    let drained = Instant::now() + DRAIN;
    for _ in 0..2 {
        if readers.recv_timeout(drained.saturating_duration_since(Instant::now())).is_err() {
            break;
        }
    }
    Ok(status.map_or(128 + libc::SIGKILL, exit_code))
}

/// The status of `child` within `deadline`, or `None`.
fn wait_up_to(child: &mut Child, deadline: Duration) -> Option<ExitStatus> {
    let started = Instant::now();
    while started.elapsed() < deadline {
        if let Ok(Some(status)) = child.try_wait() {
            return Some(status);
        }
        thread::sleep(POLL);
    }
    None
}

/// Ends the processes that the command left in its group: SIGTERM, then SIGKILL after [`GROUP_TERM_WAIT`]. A group
/// that empties within [`GROUP_SETTLE`] counts as empty: the system can need a moment to remove an ended process.
fn end_group(group: libc::pid_t, log: &EventLog) {
    if settle(group) {
        return;
    }
    log.write(Stream::Stderr, "yonder: the command left processes in its group; SIGTERM");
    signal_group(group, libc::SIGTERM);
    let started = Instant::now();
    while group_alive(group) {
        if started.elapsed() >= GROUP_TERM_WAIT {
            log.write(
                Stream::Stderr,
                "yonder: the processes of the group did not end after SIGTERM; SIGKILL",
            );
            signal_group(group, libc::SIGKILL);
            settle(group);
            return;
        }
        thread::sleep(POLL);
    }
}

/// Waits up to [`GROUP_SETTLE`] for the group to empty. Answers whether it is empty.
fn settle(group: libc::pid_t) -> bool {
    let started = Instant::now();
    while group_alive(group) {
        if started.elapsed() >= GROUP_SETTLE {
            return false;
        }
        thread::sleep(Duration::from_millis(10));
    }
    true
}

fn signal_group(group: libc::pid_t, signal: libc::c_int) {
    // SAFETY: killpg takes plain integers and touches no memory of this process.
    unsafe {
        libc::killpg(group, signal);
    }
}

/// Whether a process of the group `group` is alive.
fn group_alive(group: libc::pid_t) -> bool {
    // SAFETY: signal 0 only checks that the group exists.
    let result = unsafe { libc::killpg(group, 0) };
    result == 0 || io::Error::last_os_error().raw_os_error() == Some(libc::EPERM)
}

/// The exit code of a command: its code, or 128 plus the number of the signal that ended it.
pub(crate) fn exit_code(status: ExitStatus) -> i32 {
    status.code().unwrap_or_else(|| 128 + status.signal().unwrap_or(0))
}

/// Calls `line` with each line of `reader`, without its line end. A line longer than [`MAX_EVENT_TEXT`] comes in
/// pieces. A last line without its end comes too.
pub(crate) fn read_lines(reader: impl Read, line: &mut dyn FnMut(&str)) {
    let mut reader = BufReader::new(reader);
    let mut pending: Vec<u8> = Vec::new();
    let emit = |bytes: &mut Vec<u8>, line: &mut dyn FnMut(&str)| {
        if bytes.last() == Some(&b'\r') {
            bytes.pop();
        }
        line(&String::from_utf8_lossy(bytes));
        bytes.clear();
    };
    loop {
        let available = match reader.fill_buf() {
            Ok([]) => break,
            Ok(available) => available,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(_) => break,
        };
        // A full piece goes out before more bytes come, and its line end goes with it.
        if pending.len() >= MAX_EVENT_TEXT {
            if available[0] == b'\n' {
                reader.consume(1);
            }
            emit(&mut pending, line);
            continue;
        }
        let room = MAX_EVENT_TEXT - pending.len();
        if let Some(newline) = available.iter().take(room + 1).position(|&byte| byte == b'\n') {
            pending.extend_from_slice(&available[..newline]);
            reader.consume(newline + 1);
            emit(&mut pending, line);
        } else {
            let taken = available.len().min(room);
            pending.extend_from_slice(&available[..taken]);
            reader.consume(taken);
        }
    }
    if !pending.is_empty() {
        emit(&mut pending, line);
    }
}

/// The names in `dir`, or none when it is missing.
fn entries(dir: &Path) -> BTreeSet<OsString> {
    fs::read_dir(dir)
        .map(|entries| entries.filter_map(Result::ok).map(|entry| entry.file_name()).collect())
        .unwrap_or_default()
}

/// Moves each entry of each `fetch` directory of the slot that `before` does not name to the same path under `stage`.
/// Answers a message for each entry that cannot move.
pub(crate) fn stage_new_entries(slot: &Path, fetch: &[String], before: &[BTreeSet<OsString>], stage: &Path) -> Vec<String> {
    let mut errors = Vec::new();
    for (dir, before) in fetch.iter().zip(before) {
        let source = slot.join(dir);
        let target = stage.join(dir);
        for name in entries(&source).difference(before) {
            let moved = fs::create_dir_all(&target).and_then(|()| move_entry(&source.join(name), &target.join(name)));
            if let Err(error) = moved {
                errors.push(format!("cannot stage {}: {error}", source.join(name).display()));
            }
        }
    }
    errors
}

/// Moves a file or a directory. Across two file systems it copies, then removes the source.
fn move_entry(source: &Path, target: &Path) -> io::Result<()> {
    match fs::rename(source, target) {
        Err(error) if error.kind() == io::ErrorKind::CrossesDevices => {
            copy_tree(source, target)?;
            if fs::symlink_metadata(source)?.is_dir() {
                fs::remove_dir_all(source)
            } else {
                fs::remove_file(source)
            }
        }
        other => other,
    }
}

/// Copies a tree into the stage: each directory with the mode 0700, each file with 0600, or 0700 when it is executable.
fn copy_tree(source: &Path, target: &Path) -> io::Result<()> {
    let metadata = fs::symlink_metadata(source)?;
    if metadata.is_dir() {
        private_dir(target)?;
        for entry in fs::read_dir(source)? {
            let entry = entry?;
            copy_tree(&entry.path(), &target.join(entry.file_name()))?;
        }
        Ok(())
    } else if metadata.file_type().is_symlink() {
        std::os::unix::fs::symlink(fs::read_link(source)?, target)
    } else {
        fs::copy(source, target)?;
        let executable = metadata.permissions().mode() & 0o111 != 0;
        fs::set_permissions(target, Permissions::from_mode(if executable { 0o700 } else { FILE_MODE }))
    }
}

fn panic_text(payload: &(dyn std::any::Any + Send)) -> String {
    payload
        .downcast_ref::<&str>()
        .map(|text| (*text).to_owned())
        .or_else(|| payload.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "a panic".to_owned())
}

#[cfg(test)]
mod tests;
