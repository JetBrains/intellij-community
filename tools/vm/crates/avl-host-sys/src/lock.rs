//! Mutual exclusion between controller processes.
//!
//! Several agents share one checkout and one worker pool, so two controller invocations racing for the same worker
//! is the normal case rather than the exceptional one. What makes that safe is `flock`: the exclusion is the
//! kernel's, it lives on an open descriptor, and the kernel drops it when that descriptor closes - which includes
//! the process being killed. A controller that dies holding a lock therefore strands nothing, and that is the whole
//! problem a lock here has to solve.
//!
//! On Windows the lock is a `LockFile` byte-range lock, which the kernel also drops when the handle closes. A
//! Windows lock is mandatory: no other handle can read or write the bytes it covers. So it covers one byte far
//! past the end of the file, `WINDOWS_LOCK_OFFSET`, and not the owner record. Any process can then read the
//! record while the lock is held, as on Unix.
//!
//! The file carries an owner record naming the holder's pid and the pid's start time, and it is *diagnostics* that
//! decides nothing. It answers "who holds this, and since when" for an operator, and after a crash it answers "who
//! died holding it". Reclaiming a lock because that record says its owner is gone cannot be made correct: judging
//! the record and evicting the holder are two syscalls, and in between the lock can be released and taken again by
//! somebody live, so the eviction would remove a holder nobody ever examined.
//!
//! One rule follows, commented where it is implemented: the lock file is never unlinked ([`LockGuard`]).

use std::fs::{File, TryLockError};
use std::io;
use std::path::{Path, PathBuf};
use std::time::Duration;

use avl_base::{Exit, OrRefuse, Refusal, SCHEMA_VERSION, SystemClock};
use jiff::Timestamp;
use serde::{Deserialize, Serialize};
use tokio::sync::OnceCell;

use crate::ctx::Ctx;
use crate::poll::{Backoff, Poll};
use crate::private::{PrivateOpen, open_private_file};
use crate::proc::{PsField, Runner};

#[cfg(test)]
mod tests;

/// How long a queued acquisition sleeps between attempts, at most. Fixed and short, because an attempt is one `flock`
/// and costs no process, and a waiter should take a released lock at once.
const QUEUE_POLL: Duration = Duration::from_millis(100);

/// Acquires locks for this process.
///
/// The `ps` probe of its own start time goes through a [`Runner`], because it is a host subprocess and the phase
/// table should see it; it is resolved once, since a lock is taken inside a contention poll loop.
pub struct LockManager {
    runner: Runner,
    pid: u32,
    process_start: OnceCell<String>,
}

/// What a held lock records about the invocation holding it. Diagnostics: nothing reads it back to decide
/// anything, and a release clears it, so content in a lock file names either the current holder or the invocation
/// that died holding it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OwnerRecord {
    pub schema_version: u32,
    pub pid: u32,
    pub process_start: String,
    pub operation: String,
    pub acquired_at: String,
}

impl LockManager {
    pub fn new(runner: Runner) -> Self {
        Self {
            runner,
            pid: std::process::id(),
            process_start: OnceCell::new(),
        }
    }

    /// Takes a lock, or answers `None` because somebody else holds it.
    ///
    /// One `open` and one non-blocking `flock`. There is no reclaim step and no second attempt, because there is
    /// nothing left to reclaim: a lock whose holder died was released by the kernel before this ran, and a lock
    /// that is still held is held by a live process. Two descriptors on one file exclude each other even inside a
    /// single process, so a second acquisition of a lock this process already holds answers `None` too.
    pub async fn try_acquire(&self, ctx: &Ctx, path: &Path, operation: &str) -> Result<Option<LockGuard>, Refusal> {
        let file = open_lock_file(path)?;
        match try_lock(&file) {
            Ok(()) => {}
            Err(TryLockError::WouldBlock) => return Ok(None),
            Err(TryLockError::Error(error)) => {
                return Err(Refusal::new(
                    "lease_lock_failed",
                    Exit::FAILURE,
                    format!("cannot take {}: {error}", path.display()),
                ));
            }
        }
        self.describe(ctx, &file, operation).await;
        Ok(Some(LockGuard { file }))
    }

    /// Takes a lock, or refuses with `lease_busy` and the caller's own message because somebody else holds it
    /// ([`Self::try_acquire`]).
    pub async fn acquire(&self, ctx: &Ctx, path: &Path, operation: &str, busy_message: &str) -> Result<LockGuard, Refusal> {
        self.try_acquire(ctx, path, operation)
            .await?
            .ok_or_else(|| Refusal::new("lease_busy", Exit::TEMP_FAIL, busy_message))
    }

    /// Waits for a lock rather than refusing at once, and refuses with `timeout_code` when the budget or the
    /// operation runs out.
    ///
    /// For the operations where contention is expected and short - several `lease acquire` invocations arriving at
    /// once - as against the lifecycle operations, where a busy worker means "come back later" and waiting would
    /// hide a stuck run. A poll and not a blocking `flock`: that waits in the kernel with no deadline, and nothing
    /// can interrupt it. The poll is [`Poll`], whose last pause ends at the budget.
    pub async fn acquire_queued(
        &self,
        ctx: &Ctx,
        path: &Path,
        operation: &str,
        timeout: Duration,
        timeout_code: &'static str,
        timeout_message: &str,
    ) -> Result<LockGuard, Refusal> {
        let mut wait = Poll::start(&SystemClock, timeout, Backoff::fixed(QUEUE_POLL));
        loop {
            if let Some(guard) = self.try_acquire(ctx, path, operation).await? {
                return Ok(guard);
            }
            if !wait.pause(ctx).await {
                return Err(Refusal::new(timeout_code, Exit::TEMP_FAIL, timeout_message));
            }
        }
    }

    /// Takes several locks in the caller's order, or none: a failure part-way releases what was taken.
    ///
    /// A consistent order is what keeps two concurrent multi-worker commands from each holding half of what the
    /// other needs; `shard` holds every worker it leased for the length of a run.
    pub async fn acquire_all(&self, ctx: &Ctx, paths: &[PathBuf], operation: &str, busy_message: &str) -> Result<LockSet, Refusal> {
        let mut taken = LockSet(Vec::with_capacity(paths.len()));
        for path in paths {
            // On a refusal `taken` drops here, releasing the partial set in reverse.
            taken.0.push(self.acquire(ctx, path, operation, busy_message).await?);
        }
        Ok(taken)
    }

    /// Writes the owner record into a lock this process has just taken.
    ///
    /// Best effort, and that is safe precisely because the record decides nothing: the exclusion is already held,
    /// and giving up a lock because a diagnostic could not be written would refuse work for a reason the caller
    /// cannot act on. The start time is left empty when `ps` did not answer.
    async fn describe(&self, ctx: &Ctx, file: &File, operation: &str) {
        let process_start = self
            .process_start
            .get_or_init(|| async {
                let pid = i32::try_from(self.pid).unwrap_or(0);
                // Best effort, as the record is: a `ps` without an answer leaves the field empty.
                self.runner
                    .probe_process(ctx, pid, PsField::StartTime)
                    .await
                    .ok()
                    .flatten()
                    .unwrap_or_default()
            })
            .await;
        let record = OwnerRecord {
            schema_version: SCHEMA_VERSION,
            pid: self.pid,
            process_start: process_start.clone(),
            operation: operation.to_owned(),
            acquired_at: Timestamp::now().to_string(),
        };
        let Ok(mut line) = serde_json::to_vec(&record) else {
            return;
        };
        line.push(b'\n');
        let _ = file.set_len(0);
        let _ = write_record(file, &line);
    }
}

/// A held lock. Dropping it releases the lock.
///
/// The lock file is **never** unlinked, and that is a rule rather than tidiness. A `flock` binds to the inode, so a
/// contender holding the old inode open and a contender that created a fresh file at the same path lock two
/// different objects and both believe they hold the name. An empty lock file left behind costs a directory entry; a
/// deleted one costs mutual exclusion. The record is cleared instead, so an empty lock file names nobody.
#[derive(Debug)]
pub struct LockGuard {
    file: File,
}

impl Drop for LockGuard {
    fn drop(&mut self) {
        let _ = self.file.set_len(0);
        // Unlocked explicitly rather than by the close that follows: a child another thread is forking holds a copy
        // of this descriptor until it execs, and the kernel releases a `flock` on close only once every copy is
        // gone. An unlock releases it whatever copies exist.
        let _ = unlock(&self.file);
    }
}

/// Several held locks, released in the reverse of the order they were taken.
#[derive(Debug)]
pub struct LockSet(Vec<LockGuard>);

impl Drop for LockSet {
    fn drop(&mut self) {
        while let Some(guard) = self.0.pop() {
            drop(guard);
        }
    }
}

/// Opens the lock file private, creating it and never truncating it.
fn open_lock_file(path: &Path) -> Result<File, Refusal> {
    open_private_file(path, PrivateOpen::ReadWrite)
        .or_refuse("lease_lock_failed", Exit::FAILURE, || format!("cannot open {}", path.display()))
}

/// Where the Windows lock sits: one byte at 2^62, which no lock file reaches. Every contender locks the same byte,
/// so the exclusion is the same as a lock of the whole file.
#[cfg(windows)]
const WINDOWS_LOCK_OFFSET: u64 = 1 << 62;

/// Takes the lock without waiting: a `flock` on Unix.
#[cfg(unix)]
fn try_lock(file: &File) -> Result<(), TryLockError> {
    file.try_lock()
}

/// Takes the lock without waiting: a `LockFile` of the byte at [`WINDOWS_LOCK_OFFSET`] on Windows. `LockFile`
/// never waits, and answers `ERROR_LOCK_VIOLATION` when another handle holds the byte.
#[cfg(windows)]
fn try_lock(file: &File) -> Result<(), TryLockError> {
    use std::os::windows::io::AsRawHandle;
    use windows_sys::Win32::Foundation::ERROR_LOCK_VIOLATION;
    use windows_sys::Win32::Storage::FileSystem::LockFile;

    let (low, high) = windows_lock_offset();
    // SAFETY: the handle is open for the duration of the call.
    if unsafe { LockFile(file.as_raw_handle(), low, high, 1, 0) } != 0 {
        return Ok(());
    }
    let error = io::Error::last_os_error();
    if error.raw_os_error() == Some(ERROR_LOCK_VIOLATION.cast_signed()) {
        Err(TryLockError::WouldBlock)
    } else {
        Err(TryLockError::Error(error))
    }
}

#[cfg(unix)]
fn unlock(file: &File) -> io::Result<()> {
    file.unlock()
}

#[cfg(windows)]
fn unlock(file: &File) -> io::Result<()> {
    use std::os::windows::io::AsRawHandle;
    use windows_sys::Win32::Storage::FileSystem::UnlockFile;

    let (low, high) = windows_lock_offset();
    // SAFETY: the handle is open for the duration of the call.
    if unsafe { UnlockFile(file.as_raw_handle(), low, high, 1, 0) } == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}

/// [`WINDOWS_LOCK_OFFSET`] as the low and the high word `LockFile` takes.
#[cfg(windows)]
#[expect(clippy::cast_possible_truncation, reason = "the split into two 32-bit words is the point")]
const fn windows_lock_offset() -> (u32, u32) {
    (WINDOWS_LOCK_OFFSET as u32, (WINDOWS_LOCK_OFFSET >> 32) as u32)
}

/// Writes the owner record at the start of the file, whatever the file position is.
fn write_record(file: &File, line: &[u8]) -> io::Result<()> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::FileExt;
        file.write_all_at(line, 0)
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::FileExt;
        let mut line = line;
        let mut offset = 0;
        while !line.is_empty() {
            let written = file.seek_write(line, offset)?;
            if written == 0 {
                return Err(io::ErrorKind::WriteZero.into());
            }
            line = &line[written..];
            offset += written as u64;
        }
        Ok(())
    }
}
