//! The warm lease of each holder, as `remote.sh` of BT keeps it.
//!
//! One lease of a main repository per holder, which is one local checkout. The lease id, the slot and the repository
//! stay in `<leases>/<holder>.lease`, so a later run of the same holder checks out its commit in the same slot, beside
//! the same Bazel server. The flock of `<leases>/<holder>.lock` makes a second run of the same holder wait. The kernel
//! drops the flock when its process ends, so a lock of a killed run never stays.
//!
//! After a run, the agent returns the lease of each other holder that no run used for the idle hours of the run. It
//! first checks out the HEAD of the main repository of that lease in its slot, so the return gives up no commit.
//!
//! A lease file names the repository of its lease. A request of the holder for another repository refuses, and the
//! file stays. A file without `repo=`, such as one that `setup` moved from `remote.sh`, belongs to the repository of
//! the next request.
//!
//! A lease file also keeps the fingerprint of each prepare step of `.yonder.toml` that succeeded in the slot, as
//! `prepared.<key>=<fingerprint>` lines, so the next run of the lease skips a step whose inputs did not change.

use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

use crate::child::Stop;
use crate::files::{open_private, private_dir, try_flock, write_private};
use crate::refused::{AGENT_ERROR, LEASE_FAILED, LOCK_TIMEOUT, Refusal, refused};
use crate::{bd, git};

/// How often a wait for the lock of a holder tries again.
const LOCK_POLL: Duration = Duration::from_millis(200);

/// The leases of the holders, as a request for one main repository sees them.
#[derive(Clone, Debug)]
pub(crate) struct Leases {
    dir: PathBuf,
    repo: PathBuf,
}

/// The lease of a holder.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Lease {
    pub(crate) lease_id: String,
    pub(crate) slot: PathBuf,
    /// `false` when the holder had the lease already.
    pub(crate) fresh: bool,
    /// The fingerprint of each prepare step that succeeded in the slot, by the key of the step.
    pub(crate) prepared: BTreeMap<String, String>,
}

/// The content of a lease file: `lease_id=`, `slot=`, `used=`, `repo=` and `prepared.<key>=` lines.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LeaseFile {
    pub(crate) lease_id: String,
    pub(crate) slot: PathBuf,
    /// The epoch second of the end of the last run.
    pub(crate) used: u64,
    /// The main repository of the lease. `None` in a file of `remote.sh`.
    pub(crate) repo: Option<PathBuf>,
    /// The fingerprint of each prepare step that succeeded in the slot, by the key of the step.
    pub(crate) prepared: BTreeMap<String, String>,
}

/// The flock of a holder. Dropping it unlocks.
#[derive(Debug)]
pub(crate) struct HolderLock {
    _file: File,
}

impl LeaseFile {
    pub(crate) fn parse(text: &str) -> Option<Self> {
        let field = |key: &str| {
            text.lines()
                .find_map(|line| line.strip_prefix(key).and_then(|rest| rest.strip_prefix('=')))
                .filter(|value| !value.is_empty())
        };
        Some(Self {
            lease_id: field("lease_id")?.to_owned(),
            slot: PathBuf::from(field("slot")?),
            used: field("used").and_then(|value| value.parse().ok()).unwrap_or(0),
            repo: field("repo").map(PathBuf::from),
            prepared: text
                .lines()
                .filter_map(|line| line.strip_prefix("prepared.")?.split_once('='))
                .filter(|(key, value)| !key.is_empty() && !value.is_empty())
                .map(|(key, value)| (key.to_owned(), value.to_owned()))
                .collect(),
        })
    }

    pub(crate) fn text(&self) -> String {
        let mut text = format!("lease_id={}\nslot={}\nused={}\n", self.lease_id, self.slot.display(), self.used);
        if let Some(repo) = &self.repo {
            text.push_str(&format!("repo={}\n", repo.display()));
        }
        for (key, fingerprint) in &self.prepared {
            text.push_str(&format!("prepared.{key}={fingerprint}\n"));
        }
        text
    }
}

impl Leases {
    pub(crate) const fn new(dir: PathBuf, repo: PathBuf) -> Self {
        Self { dir, repo }
    }

    fn file(&self, holder: &str) -> PathBuf {
        self.dir.join(format!("{holder}.lease"))
    }

    fn read(&self, holder: &str) -> Option<LeaseFile> {
        LeaseFile::parse(&fs::read_to_string(self.file(holder)).ok()?)
    }

    /// Refuses a lease file of another repository.
    fn check_repo(&self, holder: &str, file: &LeaseFile) -> Result<(), Refusal> {
        match &file.repo {
            Some(repo) if *repo != self.repo => Err(refused(
                LEASE_FAILED,
                format!(
                    "the lease of {holder} belongs to {}, not to {}; release it there first",
                    repo.display(),
                    self.repo.display()
                ),
            )),
            _ => Ok(()),
        }
    }

    /// Takes the lock of `holder` when no other process holds it.
    pub(crate) fn try_lock(&self, holder: &str) -> io::Result<Option<HolderLock>> {
        private_dir(&self.dir)?;
        let file = open_private(&self.dir.join(format!("{holder}.lock")))?;
        Ok(try_flock(&file)?.then_some(HolderLock { _file: file }))
    }

    /// Takes the lock of `holder`, and waits up to `wait` while another run holds it. Answers `None` when `stop`
    /// answers `true` during the wait. `say` gets a line when the wait starts.
    pub(crate) fn lock(
        &self,
        holder: &str,
        wait: Duration,
        stop: Stop<'_>,
        say: &mut dyn FnMut(&str),
    ) -> Result<Option<HolderLock>, Refusal> {
        let started = Instant::now();
        let mut said = false;
        loop {
            let taken = self
                .try_lock(holder)
                .map_err(|error| refused(AGENT_ERROR, format!("cannot lock {holder} in {}: {error}", self.dir.display())))?;
            if let Some(lock) = taken {
                return Ok(Some(lock));
            }
            if !said {
                say(&format!("waiting for the other run of {holder} on this host"));
                said = true;
            }
            if started.elapsed() >= wait {
                return Err(refused(
                    LOCK_TIMEOUT,
                    format!("the other run of {holder} held its lease for {} s; giving up", wait.as_secs()),
                ));
            }
            if stop() {
                return Ok(None);
            }
            thread::sleep(LOCK_POLL);
        }
    }

    /// Returns the leases of the holders other than `holder` that no run used for `idle_hours`. A holder whose lock
    /// another run holds is skipped. `stop` ends the sweep early.
    pub(crate) fn sweep(&self, holder: &str, idle_hours: u32, now: u64, stop: Stop<'_>, say: &mut dyn FnMut(&str)) {
        let Ok(entries) = fs::read_dir(&self.dir) else {
            return;
        };
        let mut idle: Vec<String> = entries
            .filter_map(Result::ok)
            .filter_map(|entry| entry.file_name().to_str()?.strip_suffix(".lease").map(str::to_owned))
            .filter(|other| other != holder)
            .filter(|other| {
                let used = self.read(other).map_or(0, |file| file.used);
                now.saturating_sub(used) >= u64::from(idle_hours) * 3600
            })
            .collect();
        idle.sort();
        for other in idle {
            if stop() {
                return;
            }
            if let Ok(Some(_lock)) = self.try_lock(&other) {
                self.give_back(&other, stop, say);
            }
        }
    }

    /// Returns the lease of an idle holder, after its slot went back to the HEAD of the main repository of the lease.
    fn give_back(&self, holder: &str, stop: Stop<'_>, say: &mut dyn FnMut(&str)) {
        let path = self.file(holder);
        let Some(file) = self.read(holder) else {
            let _ = fs::remove_file(&path);
            return;
        };
        let repo = file.repo.clone().unwrap_or_else(|| self.repo.clone());
        if !bd::lease_alive(&repo, &file.lease_id, stop) {
            if !stop() {
                let _ = fs::remove_file(&path);
            }
            return;
        }
        let head = match git::head(&repo, stop) {
            Ok(head) => head,
            Err(error) => {
                say(&format!("cannot read HEAD of {}: {error}", repo.display()));
                return;
            }
        };
        if let Err(error) = git::checkout(&file.slot, &head, stop) {
            say(&format!(
                "cannot check out {head} in the idle slot {}: {error}",
                file.slot.display()
            ));
            return;
        }
        match bd::lease_return(&file.slot, &file.lease_id, false, stop) {
            Ok(()) => {
                let _ = fs::remove_file(&path);
                say(&format!("returned the idle lease {} of {}", file.lease_id, file.slot.display()));
            }
            Err(error) => say(&format!(
                "cannot return the idle lease {} of {}: {error}",
                file.lease_id,
                file.slot.display()
            )),
        }
    }

    /// The warm lease of `holder`, or a new one from `bd lease get`. The caller holds the lock of `holder`. `stop`
    /// kills a bd call early, and the answer is then a refusal.
    pub(crate) fn acquire(&self, holder: &str, stop: Stop<'_>, say: &mut dyn FnMut(&str)) -> Result<Lease, Refusal> {
        if let Some(file) = self.read(holder) {
            self.check_repo(holder, &file)?;
            if bd::lease_alive(&self.repo, &file.lease_id, stop) {
                say(&format!("lease {} (warm)", file.slot.display()));
                return Ok(Lease {
                    lease_id: file.lease_id,
                    slot: file.slot,
                    fresh: false,
                    prepared: file.prepared,
                });
            }
            if stop() {
                return Err(refused(LEASE_FAILED, "the run was cancelled"));
            }
            let _ = fs::remove_file(self.file(holder));
        }
        // A new slot is a clone of the whole checkout and can take minutes, so the log says so before the wait.
        say(&format!("taking a new lease of {holder} (bd lease get)"));
        let leased = bd::lease_get(&self.repo, holder, stop).map_err(|message| refused(LEASE_FAILED, message))?;
        say(&format!("lease {} (new)", leased.path.display()));
        Ok(Lease {
            lease_id: leased.lease_id,
            slot: leased.path,
            fresh: true,
            prepared: BTreeMap::new(),
        })
    }

    /// Writes the lease file of `holder` with `used` set to `now`.
    pub(crate) fn record(&self, holder: &str, lease: &Lease, now: u64) -> io::Result<()> {
        let file = LeaseFile {
            lease_id: lease.lease_id.clone(),
            slot: lease.slot.clone(),
            used: now,
            repo: Some(self.repo.clone()),
            prepared: lease.prepared.clone(),
        };
        private_dir(&self.dir)?;
        write_private(&self.file(holder), file.text().as_bytes())
    }

    /// Returns the lease of `holder`. It passes `--confirm-preserved`, because the commit in the slot came from the local
    /// checkout of the holder, which keeps it. The caller holds the lock of `holder`. A failed return keeps the file.
    pub(crate) fn release(&self, holder: &str, stop: Stop<'_>) -> Result<String, Refusal> {
        let path = self.file(holder);
        let Some(file) = self.read(holder) else {
            let _ = fs::remove_file(&path);
            return Ok(format!("{holder} holds no lease on this host"));
        };
        self.check_repo(holder, &file)?;
        if bd::lease_alive(&self.repo, &file.lease_id, stop) {
            bd::lease_return(&file.slot, &file.lease_id, true, stop).map_err(|error| {
                refused(
                    LEASE_FAILED,
                    format!("cannot return the lease {} of {}: {error}", file.lease_id, file.slot.display()),
                )
            })?;
        }
        let _ = fs::remove_file(&path);
        Ok(format!("released the lease {} of {}", file.lease_id, file.slot.display()))
    }
}

/// Moves each lease file of `remote.sh` in `old` to `new` when `new` has no file of that holder. Answers the count.
pub(crate) fn migrate(old: &Path, new: &Path) -> io::Result<usize> {
    let Ok(entries) = fs::read_dir(old) else {
        return Ok(0);
    };
    let mut moved = 0;
    for entry in entries.filter_map(Result::ok) {
        let name = entry.file_name();
        if !name.to_string_lossy().ends_with(".lease") || new.join(&name).exists() {
            continue;
        }
        let Ok(text) = fs::read_to_string(entry.path()) else {
            continue;
        };
        if LeaseFile::parse(&text).is_none() {
            continue;
        }
        private_dir(new)?;
        write_private(&new.join(&name), text.as_bytes())?;
        fs::remove_file(entry.path())?;
        moved += 1;
    }
    Ok(moved)
}

/// The current epoch second.
pub(crate) fn now_seconds() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |since| since.as_secs())
}

#[cfg(test)]
mod tests;
