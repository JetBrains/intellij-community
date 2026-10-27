//! The keeper of the host slots: a thread of `serve` that wakes once a minute, and at each start of a run of a
//! repository. Per repository it returns the leases that holders kept before the host slots (`lease::retire`), then
//! takes up what no process acts on (`slots::reclaim`: a lend or a give-back of a gone supervisor, a clean or a shrink
//! that died), then leases the missing slots (`slots::fill`). A fill can clone the whole checkout, so it runs on a thread
//! of its own, one per repository at a time, and only once no lease of a holder is left: a fill beside the old leases
//! would add slots that they hold, and nor while a supervisor of before the host slots lives (`RunDir::is_legacy`). A failed fill waits 1, 5, then 15 minutes before the next one. The repositories
//! start from the repository list of the packs, the runs on disk and the lease files, so a restart of `serve` keeps
//! them. While Homebrew upgrades git (`host_git`), a tick does nothing.

use std::collections::{BTreeSet, HashMap};
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, RecvTimeoutError};
use std::sync::{Arc, Mutex, PoisonError};
use std::thread;
use std::time::{Duration, Instant};

use crate::child::never;
use crate::home::Home;
use crate::run::Runs;
use crate::serve::log;
use crate::{cleanup, host_git, lease, slots, tuning};

/// The waits after a failed fill: the first, the second, and every later one.
const FILL_BACKOFF: [Duration; 3] = [Duration::from_mins(1), Duration::from_mins(5), Duration::from_mins(15)];

/// The fills of each repository.
#[derive(Debug, Default)]
struct Fill {
    running: bool,
    failures: usize,
    not_before: Option<Instant>,
}

/// Starts the keeper of `home`; the sender wakes it for a repository.
pub(crate) fn start(home: Home) -> mpsc::Sender<PathBuf> {
    let (wake, woken) = mpsc::channel::<PathBuf>();
    thread::spawn(move || keep(&home, &woken));
    wake
}

fn keep(home: &Home, woken: &mpsc::Receiver<PathBuf>) {
    let mut repos: BTreeSet<PathBuf> = Runs::new(home.runs_dir())
        .list()
        .iter()
        .filter_map(|run| run.spec().ok())
        .map(|spec| home.expand(&spec.repo))
        .chain(cleanup::repos(home))
        .chain(lease::repositories(&home.leases_dir()))
        .collect();
    let fills: Arc<Mutex<HashMap<PathBuf, Fill>>> = Arc::default();
    let mut said_unsupported = false;
    loop {
        if slots::supported() {
            // bd uses git: no slot work while Homebrew replaces it.
            if !host_git::upgrading(home) {
                for repo in &repos {
                    tend(home, repo, &fills);
                }
            }
        } else if !said_unsupported {
            log(slots::UNSUPPORTED);
            said_unsupported = true;
        }
        match woken.recv_timeout(tuning::keeper_tick()) {
            Ok(repo) => {
                repos.insert(repo);
                repos.extend(woken.try_iter());
            }
            Err(RecvTimeoutError::Timeout) => {}
            Err(RecvTimeoutError::Disconnected) => return,
        }
    }
}

/// One tick of one repository.
fn tend(home: &Home, repo: &Path, fills: &Arc<Mutex<HashMap<PathBuf, Fill>>>) {
    // A supervisor of before the host slots takes a lease of its holder, also one that still waits for the lock of its
    // holder and so shows no lease yet: no fill while one lives, or the pool would pass the count of the host slots
    let legacy = Runs::new(home.runs_dir())
        .list()
        .iter()
        .any(|run| run.is_legacy() && run.spec().is_ok_and(|spec| home.expand(&spec.repo) == repo));
    let retired = lease::retire(&home.leases_dir(), repo, &never, &mut |line| log(line)) && !legacy;
    match slots::reclaim(repo) {
        Ok(lines) => {
            for line in lines {
                log(&format!("the host slots of {}: {line}", repo.display()));
            }
        }
        Err(error) => log(&format!("cannot reclaim the host slots of {}: {error}", repo.display())),
    }
    if !retired {
        return;
    }
    {
        let mut fills = fills.lock().unwrap_or_else(PoisonError::into_inner);
        let fill = fills.entry(repo.to_path_buf()).or_default();
        if fill.running || fill.not_before.is_some_and(|not_before| Instant::now() < not_before) {
            return;
        }
        fill.running = true;
    }
    let (repo, fills) = (repo.to_path_buf(), Arc::clone(fills));
    thread::spawn(move || {
        let filled = slots::fill(&repo, &never);
        let mut fills = fills.lock().unwrap_or_else(PoisonError::into_inner);
        let fill = fills.entry(repo.clone()).or_default();
        fill.running = false;
        match filled {
            Ok(added) => {
                fill.failures = 0;
                fill.not_before = None;
                if !added.is_empty() {
                    log(&format!("added the host slots {added:?} of {}", repo.display()));
                }
            }
            Err(error) => {
                let wait = FILL_BACKOFF[fill.failures.min(FILL_BACKOFF.len() - 1)];
                fill.failures += 1;
                fill.not_before = Some(Instant::now() + wait);
                log(&format!(
                    "cannot add the host slots of {}: {error}; again in {} min",
                    repo.display(),
                    wait.as_secs() / 60
                ));
            }
        }
    });
}
