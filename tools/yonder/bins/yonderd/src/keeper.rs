//! The keeper of the host slots: a thread of `serve` that wakes once a minute, and at each start of a run of a
//! repository. Per repository it takes up what no process acts on (`slots::reclaim`: a lend or a give-back of a gone
//! supervisor, a clean or a shrink that died), then leases the missing slots (`slots::fill`). A fill can clone the whole
//! checkout, so it runs on a thread of its own, one per repository at a time. A failed fill waits 1, 5, then 15 minutes
//! before the next one. The repositories start from the repository list of the packs and the runs on disk, so a
//! restart of `serve` keeps them. Before the slots of a repository, each tick keeps the git of the host at its floor
//! (`run_git::ensure`): the call blocks while Branch Deck waits for the lends and while Homebrew runs. A change of its
//! outcome is one line of the log.

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
use crate::{cleanup, run_git, slots, tuning};

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
        .collect();
    let fills: Arc<Mutex<HashMap<PathBuf, Fill>>> = Arc::default();
    let mut said: HashMap<PathBuf, String> = HashMap::new();
    loop {
        for repo in &repos {
            ensure_git(repo, &mut said);
            tend(repo, &fills);
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

/// Keeps the git of the host at its floor for `repo`, and logs an upgrade or a change of the outcome. `said` holds the
/// last outcome of each repository.
fn ensure_git(repo: &Path, said: &mut HashMap<PathBuf, String>) {
    let line = match run_git::ensure(repo) {
        Ok(git) if git.upgraded => {
            log(&format!(
                "Homebrew upgraded the git of the host to {} {}",
                git.path.display(),
                git.version
            ));
            format!("the git of the host is {} {}", git.path.display(), git.version)
        }
        Ok(git) => format!("the git of the host is {} {}", git.path.display(), git.version),
        Err(failed) => format!("the git of the host stays below its floor: {failed}"),
    };
    if said.get(repo) != Some(&line) {
        log(&line);
        said.insert(repo.to_path_buf(), line);
    }
}

/// One tick of one repository.
fn tend(repo: &Path, fills: &Arc<Mutex<HashMap<PathBuf, Fill>>>) {
    match slots::reclaim(repo) {
        Ok(lines) => {
            for line in lines {
                log(&format!("the host slots of {}: {line}", repo.display()));
            }
        }
        Err(error) => log(&format!("cannot reclaim the host slots of {}: {error}", repo.display())),
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
