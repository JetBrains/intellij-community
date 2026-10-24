//! The hourly cleanup of `serve`: it ends each run that lost its supervisor, removes each run a day after its end,
//! deletes each ref of a pack that no run uses, and runs `git maintenance run --auto` in each listed repository. Each
//! pack adds a pack file to its repository, and git repacks only when its maintenance runs.
//!
//! A pack makes `refs/yonder/<run>`, and the supervisor of the run deletes it. A pack that no start follows would keep
//! its ref for ever. So each pack adds its repository to the repository list, and the cleanup deletes each
//! `refs/yonder/<run>` of a listed repository whose run has no directory. A run directory goes a day after the end of
//! its run, and then its ref goes too. A ref whose run id names a time within the last [`PACK_GRACE`] stays, because
//! its start can still come. A base ref under [`BASE_REFS`] always stays: the next pack to its name replaces it.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use yonder_link::proto::BASE_REFS;

use crate::files::{private_dir, write_private};
use crate::git;
use crate::home::Home;
use crate::run::{Runs, epoch_ms};

/// How long a ref without a run directory stays after the time in its run id.
pub(crate) const PACK_GRACE: Duration = Duration::from_secs(3600);

/// Ends the lost runs, removes the expired runs and deletes the stale refs. Answers a log line for each change and each
/// failure.
pub(crate) fn clean(home: &Home, now: SystemTime) -> Vec<String> {
    let runs = Runs::new(home.runs_dir());
    let mut lines: Vec<String> = runs
        .list()
        .iter()
        .filter(|run| run.end_if_lost())
        .map(|run| format!("ended the run {}, which lost its supervisor", run.path().display()))
        .collect();
    lines.extend(
        runs.remove_expired(now)
            .into_iter()
            .map(|run| format!("removed the run {run}, which ended a day ago")),
    );
    for repo in repos(home) {
        if !repo.is_dir() {
            continue;
        }
        let references = match git::pack_refs(&repo) {
            Ok(references) => references,
            Err(error) => {
                lines.push(format!("cannot list the pack refs of {}: {error}", repo.display()));
                continue;
            }
        };
        for reference in references {
            if reference.starts_with(BASE_REFS) {
                continue;
            }
            let Some(run) = reference.strip_prefix(git::REF_PREFIX) else {
                continue;
            };
            if runs.get(run).is_ok() || is_fresh(run, now) {
                continue;
            }
            match git::delete_ref(&repo, &reference) {
                Ok(()) => lines.push(format!("deleted {reference} in {}, which no run uses", repo.display())),
                Err(error) => lines.push(format!("cannot delete {reference} in {}: {error}", repo.display())),
            }
        }
        if let Err(error) = git::maintenance(&repo) {
            lines.push(format!("cannot run the git maintenance of {}: {error}", repo.display()));
        }
    }
    lines
}

/// Adds `repo` to the repository list, once.
pub(crate) fn remember_repo(home: &Home, repo: &Path) -> io::Result<()> {
    let mut known = repos(home);
    if known.iter().any(|known| known == repo) {
        return Ok(());
    }
    known.push(repo.to_path_buf());
    private_dir(&home.cache_dir())?;
    let text: String = known.iter().map(|repo| format!("{}\n", repo.display())).collect();
    write_private(&home.repos_file(), text.as_bytes())
}

/// The repositories that a pack went to: one path per line of the repository list.
fn repos(home: &Home) -> Vec<PathBuf> {
    fs::read_to_string(home.repos_file())
        .map(|text| text.lines().filter(|line| !line.is_empty()).map(PathBuf::from).collect())
        .unwrap_or_default()
}

/// Whether the run id `run` starts with an epoch millisecond within [`PACK_GRACE`] before `now`, as the
/// `<epoch ms>-<pid>` ids of the client do. An id without a time is not fresh.
pub(crate) fn is_fresh(run: &str, now: SystemTime) -> bool {
    let Some(started) = run.split('-').next().and_then(|ms| ms.parse::<u64>().ok()) else {
        return false;
    };
    let grace = u64::try_from(PACK_GRACE.as_millis()).unwrap_or(u64::MAX);
    epoch_ms(now).saturating_sub(started) < grace
}

#[cfg(test)]
mod tests;
