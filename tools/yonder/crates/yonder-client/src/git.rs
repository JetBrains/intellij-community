//! The local git calls of a run: the clean check, the commit, the commits that the host may have, the base, and the
//! thin pack.

use std::path::Path;
use std::time::Duration;

use crate::process::{Call, Limit, Processes, Ran};
use crate::{Refusal, code, refused};

/// The deadline of a short git call. `git status` of a large checkout reads every file, so it gets minutes.
const GIT_DEADLINE: Duration = Duration::from_secs(300);

/// The quiet limit of `git pack-objects --progress`. It writes a progress line at least every second while it works, so
/// a minute without one is a stuck child. A large pack takes as long as it needs.
pub(crate) const PACK_QUIET: Duration = Duration::from_secs(60);

/// The largest first-parent distance from HEAD that the client asks the host about. The distances are the powers of
/// two up to it, so a host that is some thousand commits behind still has one of them.
const FARTHEST: usize = 65_536;

/// How many changed paths a refusal shows.
const SHOWN_CHANGES: usize = 5;

/// The commit of HEAD, refused when the checkout has local changes: tracked or untracked files that differ from HEAD.
pub(crate) fn clean_head(processes: &dyn Processes, checkout: &Path) -> Result<String, Refusal> {
    let status = git(processes, checkout, &["status", "--porcelain"]);
    if !status.ok() {
        return Err(git_refusal("git status", &status));
    }
    let text = status.stdout_text();
    let changed: Vec<&str> = text.lines().filter(|line| !line.trim().is_empty()).collect();
    if !changed.is_empty() {
        let more = match changed.len() {
            0..=SHOWN_CHANGES => String::new(),
            count => format!("\n  (+{} more)", count - SHOWN_CHANGES),
        };
        return Err(refused(
            code::LOCAL_CHANGES,
            format!(
                "a remote run takes the committed HEAD only, and this checkout has local changes. Commit or stash them first:\n  {}{more}",
                changed[..changed.len().min(SHOWN_CHANGES)].join("\n  ")
            ),
        ));
    }
    #[expect(clippy::literal_string_with_formatting_args, reason = "`HEAD^{commit}` is the peel syntax of git")]
    let head = git(processes, checkout, &["rev-parse", "--verify", "HEAD^{commit}"]);
    let commit = head.stdout_text().trim().to_owned();
    if !head.ok() || commit.is_empty() {
        return Err(git_refusal("git rev-parse HEAD", &head));
    }
    Ok(commit)
}

/// The commits that the host may have: HEAD and its first-parent ancestors at the distances 1, 2, 4 … 65536, then the
/// merge bases of HEAD with `master` and `origin/master`. A ref that this checkout lacks adds nothing.
pub(crate) fn candidates(processes: &dyn Processes, checkout: &Path, commit: &str) -> Vec<String> {
    let mut found: Vec<String> = Vec::new();
    let mut add = |line: &str| {
        if !line.is_empty() && !found.iter().any(|known| known == line) {
            found.push(line.to_owned());
        }
    };
    let ancestors = git(
        processes,
        checkout,
        &["rev-list", "--first-parent", &format!("--max-count={}", FARTHEST + 1), commit],
    );
    if ancestors.ok() {
        let text = ancestors.stdout_text();
        let lines: Vec<&str> = text.lines().map(str::trim).collect();
        for distance in std::iter::once(0).chain((0..=FARTHEST.ilog2()).map(|power| 1 << power)) {
            if let Some(line) = lines.get(distance) {
                add(line);
            }
        }
    }
    for base in ["master", "origin/master"] {
        let merged = git(processes, checkout, &["merge-base", commit, base]);
        if merged.ok() {
            add(merged.stdout_text().trim());
        }
    }
    found
}

/// Adds a commit to the candidates when it is a new one.
pub(crate) fn add_candidate(candidates: &mut Vec<String>, commit: Option<&str>) {
    if let Some(commit) = commit
        && !commit.is_empty()
        && !candidates.iter().any(|known| known == commit)
    {
        candidates.push(commit.to_owned());
    }
}

/// The commit of the base of the later packs: `origin/master`, or `master` when the checkout has no `origin/master`.
/// `None` when the checkout has neither.
pub(crate) fn base_commit(processes: &dyn Processes, checkout: &Path) -> Option<String> {
    ["origin/master", "master"].into_iter().find_map(|base| {
        let ran = git(
            processes,
            checkout,
            &["rev-parse", "--verify", "--quiet", &format!("{base}^{{commit}}")],
        );
        let commit = ran.stdout_text().trim().to_owned();
        (ran.ok() && !commit.is_empty()).then_some(commit)
    })
}

/// The count of the commits of `commit` that are not in `present`, or `None` when git cannot count them.
pub(crate) fn commit_count(processes: &dyn Processes, checkout: &Path, commit: &str, present: &[String]) -> Option<u64> {
    let mut args = vec!["rev-list".to_owned(), "--count".to_owned(), commit.to_owned()];
    args.extend(present.iter().map(|known| format!("^{known}")));
    let counted = git(processes, checkout, &args);
    counted.stdout_text().trim().parse().ok().filter(|_| counted.ok())
}

/// The call of the thin pack of `commit` against the commits that the host has. Its stdout goes into a file, and
/// its progress keeps it alive.
pub(crate) fn pack_call(checkout: &Path, commit: &str, present: &[String]) -> Call {
    let mut input = format!("{commit}\n");
    for known in present {
        input.push('^');
        input.push_str(known);
        input.push('\n');
    }
    Call::new("git", &["pack-objects", "--revs", "--thin", "--stdout", "--progress"], GIT_DEADLINE)
        .with_limit(Limit::Quiet(PACK_QUIET))
        .in_dir(checkout.to_path_buf())
        .with_input(input.into_bytes())
}

pub(crate) fn git_refusal(what: &str, ran: &Ran) -> Refusal {
    refused(code::GIT_FAILED, format!("{what} failed: {}", ran.said()))
}

fn git<S: AsRef<str>>(processes: &dyn Processes, checkout: &Path, args: &[S]) -> Ran {
    processes.run(&Call::new("git", args, GIT_DEADLINE).in_dir(checkout.to_path_buf()))
}

#[cfg(test)]
mod tests;
