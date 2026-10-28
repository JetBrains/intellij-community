//! The repositories of the host and the files of a slot. Branch Deck owns their git: the agent asks `bd lease host
//! have`, `ingest`, `show`, `snapshot` and `changes`, and starts no git of its own here.
//!
//! A pack makes a ref of Branch Deck: `refs/yonder/<run>` of the protocol is `run:<run>`, so
//! `refs/branch-deck/host/run/<run>`, and `refs/yonder/base/<name>` is `base:<name>`. The give-back of a run deletes its
//! ref, and `reclaim` deletes a run ref without a lend after an hour and a base ref that no pack moved for 14 days.

use std::path::Path;
use std::time::Duration;

use serde::Deserialize;
use yonder_link::proto::{BASE_REFS, is_run_id};

use crate::bd;
use crate::child::{self, Running, Stop, never};

/// The deadline of a call that reads a few objects.
pub(crate) const QUICK: Duration = Duration::from_secs(60);

/// The deadline of the ingest of one pack.
pub(crate) const INGEST: Duration = Duration::from_mins(30);

/// The deadline of a scan of the files of a slot.
pub(crate) const SCAN: Duration = Duration::from_mins(30);

/// The namespace of the refs of the protocol that a pack names.
pub(crate) const REF_PREFIX: &str = "refs/yonder/";

/// Whether `text` is a full commit id: 40 or 64 lower-case hex digits.
pub(crate) fn is_commit_id(text: &str) -> bool {
    matches!(text.len(), 40 | 64) && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}

/// Whether `reference` is the ref of a run: `refs/yonder/<run id>`.
pub(crate) fn is_run_ref(reference: &str) -> bool {
    reference.strip_prefix(REF_PREFIX).is_some_and(is_run_id)
}

/// The `--ref` of `bd lease host ingest` for a ref that a pack may name: `base:<name>` for `refs/yonder/base/<name>`,
/// `run:<run>` for a run ref, else `None`. A name of a base ref has the rules of a run id.
pub(crate) fn ingest_ref(reference: &str) -> Option<String> {
    if let Some(name) = reference.strip_prefix(BASE_REFS) {
        return is_run_id(name).then(|| format!("base:{name}"));
    }
    reference
        .strip_prefix(REF_PREFIX)
        .filter(|run| is_run_id(run))
        .map(|run| format!("run:{run}"))
}

/// Why the agent cannot use a path as a repository.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct NotRepo(pub(crate) String);

/// Checks that `repo` is a git repository by its files: a checkout has `.git`, and a bare repository has `HEAD` and
/// `objects`. A stat starts no child, so a loaded host answers too.
pub(crate) fn check_repo(repo: &Path) -> Result<(), NotRepo> {
    if !repo.is_dir() {
        return Err(NotRepo("it is no directory".to_owned()));
    }
    if repo.join(".git").exists() || (repo.join("HEAD").is_file() && repo.join("objects").is_dir()) {
        return Ok(());
    }
    Err(NotRepo("it has no .git, and no HEAD and objects of a bare repository".to_owned()))
}

/// The commits of `commits` that `repo` has as commit objects, in their order: `bd lease host have`.
pub(crate) fn have(repo: &Path, commits: &[String]) -> Result<Vec<String>, String> {
    #[derive(Deserialize)]
    struct Answer {
        commits: Vec<String>,
    }
    if commits.is_empty() {
        return Ok(Vec::new());
    }
    let mut args = vec!["have"];
    args.extend(commits.iter().map(String::as_str));
    let text = bd::host(repo, &args, QUICK, &never)?;
    bd::parse::<Answer>("have", &text).map(|answer| answer.commits)
}

/// Whether `repo` has the commit `commit`.
pub(crate) fn has_commit(repo: &Path, commit: &str) -> Result<bool, String> {
    have(repo, &[commit.to_owned()]).map(|found| !found.is_empty())
}

/// Starts `bd lease host ingest`, which reads a thin pack on its stdin and points the ref `ingest_ref` at `commit`. The
/// caller writes the pack to its stdin, then calls [`finish_ingest`].
pub(crate) fn start_ingest(repo: &Path, ingest_ref: &str, commit: &str) -> Result<Running, String> {
    child::spawn(
        &mut bd::host_command(repo, &["ingest", "--ref", ingest_ref, "--commit", commit]),
        true,
        INGEST,
    )
    .map_err(|error| error.to_string())
}

/// How a pack landed.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Ingested {
    pub(crate) objects: u64,
    /// `loose` or `pack`.
    pub(crate) stored: String,
}

/// Waits for the end of an ingest of [`start_ingest`].
pub(crate) fn finish_ingest(repo: &Path, running: Running) -> Result<Ingested, String> {
    #[derive(Deserialize)]
    struct Answer {
        objects: u64,
        stored: String,
    }
    let output = running.wait(&never).map_err(|error| error.to_string())?;
    let text = bd::answer(repo, "ingest", &output).map_err(|failed| failed.message)?;
    bd::parse::<Answer>("ingest", &text).map(|answer| Ingested {
        objects: answer.objects,
        stored: answer.stored,
    })
}

/// What `bd lease host show` read from a commit.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct Shown {
    /// The text of the blob that the call named, `None` when the commit has no such file.
    pub(crate) blob: Option<String>,
    /// The object id of each path of the call, in its order: `None` for a path that the commit lacks. A directory has
    /// its tree id, which covers its whole content.
    pub(crate) ids: Vec<Option<String>>,
}

/// The text of the file `blob` and the object ids of `paths` in `commit` of `repo`: `bd lease host show`.
pub(crate) fn show(repo: &Path, commit: &str, blob: Option<&str>, paths: &[String]) -> Result<Shown, String> {
    #[derive(Deserialize)]
    struct Answer {
        blob: Option<Blob>,
        #[serde(default)]
        ids: Vec<Id>,
    }
    #[derive(Deserialize)]
    struct Blob {
        text: String,
    }
    #[derive(Deserialize)]
    struct Id {
        id: Option<String>,
    }
    let trimmed: Vec<&str> = paths.iter().map(|path| path.trim_end_matches('/')).collect();
    let mut args = vec!["show", "--commit", commit];
    if let Some(blob) = blob {
        args.extend(["--blob", blob]);
    }
    if !trimmed.is_empty() {
        args.push("--ids");
        args.extend(trimmed.iter().copied());
    }
    let text = bd::host(repo, &args, QUICK, &never)?;
    let answer = bd::parse::<Answer>("show", &text)?;
    if answer.ids.len() != paths.len() {
        return Err(format!(
            "bd lease host show answered {} ids for {} paths",
            answer.ids.len(),
            paths.len()
        ));
    }
    Ok(Shown {
        blob: answer.blob.map(|blob| blob.text),
        ids: answer.ids.into_iter().map(|id| id.id).collect(),
    })
}

/// The tree of the files of the slot lent to `run`, as `git add --all` stages them: `bd lease host snapshot`. Branch
/// Deck scans through an index copy, in the untracked mode and with the fsmonitor of the repository, so the index of
/// the slot stays as it is. `stop` kills the scan.
pub(crate) fn snapshot(repo: &Path, run: &str, stop: Stop<'_>) -> Result<String, String> {
    #[derive(Deserialize)]
    struct Answer {
        tree: String,
    }
    let text = bd::host(repo, &["snapshot", "--run", run], SCAN, stop)?;
    bd::parse::<Answer>("snapshot", &text).map(|answer| answer.tree)
}

/// Writes the patch from the tree `base` to the files of the slot lent to `run` into `patch`, for `git apply`: `bd lease
/// host changes`. Answers whether the files changed. A slot whose files have the tree `base` gets no file.
pub(crate) fn changes(repo: &Path, run: &str, base: &str, patch: &Path) -> Result<bool, String> {
    #[derive(Deserialize)]
    struct Answer {
        changed: bool,
    }
    let patch = patch.to_string_lossy();
    let text = bd::host(repo, &["changes", "--run", run, "--base", base, "--patch", &patch], SCAN, &never)?;
    bd::parse::<Answer>("changes", &text).map(|answer| answer.changed)
}

#[cfg(test)]
mod tests;
