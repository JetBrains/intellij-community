//! The local calls of Branch Deck, `bd host`, which does every git call of the client: the snapshot of the checkout,
//! the thin pack, and the apply of the changes. yonder starts no git. Each call runs as `bd -C <checkout> host …` and
//! answers one JSON object on its stdout. A failure answers the error object of bd, `{"error": {"code", "message"}}`,
//! with a nonzero exit, and the refusal names its code.

use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::Deserialize;
use serde::de::DeserializeOwned;
use yonder_link::config;

use crate::process::{Call, Limit, Ran, Stopped};
use crate::{Client, Refusal, code, refused};

/// The program of Branch Deck, found on the `PATH`.
pub(crate) const PROGRAM: &str = "bd";

/// The deadline of a snapshot and of an apply. `git status` of a large checkout reads every file, so it gets minutes.
const DEADLINE: Duration = Duration::from_secs(300);

/// The quiet limit of `bd host pack`. bd writes a progress line of git at least every second, and kills a git that is
/// quiet for 60 s itself, so its own refusal comes first. A large pack takes as long as it needs.
pub(crate) const PACK_QUIET: Duration = Duration::from_secs(90);

/// The largest first-parent distance from HEAD of the first `Have` of a run. git walks 17 commits for it, and a host
/// that ran a recent commit of the checkout has one of them.
pub(crate) const NEAR: usize = 16;

/// The largest first-parent distance from HEAD that the client asks the host about, when the host has none of the near
/// candidates. The distances are the powers of two up to it, so a host that is some thousand commits behind still has
/// one of them.
pub(crate) const FARTHEST: usize = 65_536;

/// What a snapshot reads.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Scan {
    /// HEAD without a status: the fast first call of a run of the committed HEAD.
    Head,
    /// HEAD and the status, whose lines are the local changes.
    Status,
    /// The commit of the working tree: HEAD plus the tracked and untracked changes that git does not ignore.
    WorkingTree,
}

/// The commit that a run ships, and what the client asks the host about. The answer of `bd host snapshot`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct Snapshot {
    pub(crate) commit: String,
    pub(crate) head: String,
    /// The lines of `git status --porcelain`: the changes beyond HEAD. Empty for [`Scan::Head`].
    #[serde(default)]
    pub(crate) changed: Vec<String>,
    /// The name of the local repository: the directory of its main checkout.
    #[serde(default)]
    pub(crate) repository: Option<String>,
    /// `yonder/` in the git directory: the run records and the patches. bd does not make it.
    pub(crate) patch_dir: PathBuf,
    /// The base of the later packs: `origin/master`, else `master`.
    #[serde(default)]
    pub(crate) base: Option<String>,
    /// HEAD, its first-parent ancestors at the distances 1, 2, 4 … up to the farthest, and the merge bases with
    /// `master` and `origin/master`.
    #[serde(default)]
    pub(crate) candidates: Vec<String>,
    /// `.yonder.toml` of the commit, `None` when the commit has none.
    #[serde(default)]
    pub(crate) blob: Option<Blob>,
}

/// One file of a commit.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct Blob {
    pub(crate) text: String,
}

/// The answer of `bd host pack`: a new file that the caller removes.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct Pack {
    pub(crate) path: PathBuf,
    pub(crate) bytes: u64,
    /// The count of the commits that the pack holds.
    pub(crate) commits: u64,
}

/// The answer of `bd host apply`.
#[derive(Deserialize)]
struct Applied {
    files: u64,
}

/// The error object of bd.
#[derive(Deserialize)]
struct Failed {
    error: FailedError,
}

#[derive(Deserialize)]
struct FailedError {
    code: String,
    message: String,
}

impl Client<'_> {
    /// The snapshot of `checkout` with the candidates up to `farthest`. Each scan but [`Scan::Status`] reads
    /// `.yonder.toml` of the commit.
    pub(crate) fn snapshot(&self, checkout: &Path, scan: Scan, farthest: usize) -> Result<Snapshot, Refusal> {
        let farthest = farthest.to_string();
        let mut args = vec!["snapshot"];
        match scan {
            Scan::Head => args.push("--no-status"),
            Scan::Status => {}
            Scan::WorkingTree => args.push("--working-tree"),
        }
        if scan != Scan::Status {
            args.extend(["--blob", config::FILE]);
        }
        args.extend(["--farthest", &farthest]);
        let ran = self.child(&self.bd_call(checkout, &args, Limit::Deadline(DEADLINE)));
        answer(&ran, "bd host snapshot")
    }

    /// Builds the thin pack of `commit` against the commits of `have` that the host has. `stop` kills it, and it also
    /// sends the keepalives of the connection of the pack.
    pub(crate) fn build_pack(
        &self,
        checkout: &Path,
        commit: &str,
        have: &[String],
        stop: &(dyn Fn() -> bool + Sync),
    ) -> Result<Pack, Refusal> {
        let mut args = vec!["pack", commit];
        for known in have {
            args.extend(["--have", known]);
        }
        let ran = self.processes.run(&self.bd_call(checkout, &args, Limit::Quiet(PACK_QUIET)), stop);
        answer(&ran, "bd host pack")
    }

    /// Applies the patch to the files of the working tree of `checkout`, not to its index. Answers the count of the
    /// files. A patch that does not apply refuses with `apply_failed` and the words of git.
    pub(crate) fn apply_patch(&self, checkout: &Path, patch: &Path) -> Result<u64, Refusal> {
        let patch = patch.to_string_lossy();
        let ran = self.child(&self.bd_call(checkout, &["apply", &patch], Limit::Deadline(DEADLINE)));
        answer::<Applied>(&ran, "bd host apply")
            .map(|applied| applied.files)
            .map_err(|refusal| match refusal.code.as_ref() {
                code::BD_FAILED => refused(code::APPLY_FAILED, refusal.message),
                _ => refusal,
            })
    }

    fn bd_call(&self, checkout: &Path, args: &[&str], limit: Limit) -> Call {
        let checkout = checkout.to_string_lossy();
        let mut all = vec!["-C", &checkout, "host"];
        all.extend_from_slice(args);
        Call::new(self.bd, &all, DEADLINE).with_limit(limit)
    }
}

/// The JSON answer of a bd call, or a refusal: `interrupted` after Ctrl+C, else `bd_failed` with the code of bd.
fn answer<T: DeserializeOwned>(ran: &Ran, what: &str) -> Result<T, Refusal> {
    if ran.stopped == Some(Stopped::Asked) {
        return Err(refused(code::INTERRUPTED, format!("Ctrl+C stopped {what}")));
    }
    if ran.ok() {
        return serde_json::from_slice(&ran.stdout).map_err(|error| {
            refused(
                code::BD_FAILED,
                format!("{what} answered no JSON of its shape: {error}. {}", upgrade_hint()),
            )
        });
    }
    if ran.code.is_none() && ran.stopped.is_none() {
        return Err(refused(
            code::BD_FAILED,
            format!("{what}: {}. Branch Deck (`bd`) must be on the PATH: {}", ran.said(), upgrade_hint()),
        ));
    }
    match serde_json::from_slice::<Failed>(&ran.stdout) {
        Ok(failed) => Err(refused(
            code::BD_FAILED,
            format!("{what} refused with {}: {}", failed.error.code, failed.error.message.trim()),
        )),
        Err(_) => Err(refused(
            code::BD_FAILED,
            format!("{what} failed: {}. {}", ran.said(), upgrade_hint()),
        )),
    }
}

const fn upgrade_hint() -> &'static str {
    "yonder needs a Branch Deck with `bd host snapshot`, `pack` and `apply`"
}

#[cfg(test)]
mod tests;
