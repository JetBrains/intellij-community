//! The local files of the runs of a checkout, in `yonder/` of its git directory: `<run>.json`, the record of what the
//! client asked of the run, and `<run>.patch`, the patch of its changes. The files older than [`KEEP`] go.
//!
//! The client writes the record before the start of the run. A fetch takes only what the record asked for: the staged
//! files below its fetch directories, the files of its outputs, and the changes when it asked for them. So the host
//! cannot choose which local paths it replaces. The outputs come from `.yonder.toml` of the shipped commit, through the
//! parser that the agent uses too.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use yonder_link::config;

use crate::git::{config_text, git_dir_path};
use crate::process::{Processes, Stop};
use crate::{Refusal, code, refused};

/// How long the records and the patches stay in the git directory of a checkout.
const KEEP: Duration = Duration::from_hours(7 * 24);

/// What the client asked of a run.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct RunRecord {
    /// The shipped commit.
    pub(crate) commit: String,
    /// The directories relative to the slot root whose staged files come back.
    pub(crate) fetch: Vec<String>,
    /// The outputs of `.yonder.toml` of the commit, when the client asked for them.
    pub(crate) outputs: Vec<String>,
    /// The client asked for the changes of the command.
    pub(crate) changes: bool,
}

impl RunRecord {
    /// Whether the run brings anything back.
    pub(crate) const fn asks_for_files(&self) -> bool {
        !self.fetch.is_empty() || !self.outputs.is_empty() || self.changes
    }
}

/// The directory `yonder/` in the git directory of a checkout.
pub(crate) struct RunFiles(PathBuf);

impl RunFiles {
    /// The directory of `checkout`, made when it is missing. Its files older than [`KEEP`] go.
    pub(crate) fn of(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Result<Self, Refusal> {
        let dir = git_dir_path(processes, checkout, "yonder", stop)?;
        fs::create_dir_all(&dir).map_err(|error| refused(code::GIT_FAILED, format!("cannot make {}: {error}", dir.display())))?;
        if let Ok(entries) = fs::read_dir(&dir) {
            for entry in entries.filter_map(Result::ok) {
                let name = entry.file_name();
                let name = name.to_string_lossy();
                let old = entry
                    .metadata()
                    .and_then(|metadata| metadata.modified())
                    .ok()
                    .and_then(|modified| modified.elapsed().ok())
                    .is_some_and(|age| age >= KEEP);
                if old && (name.ends_with(".patch") || name.ends_with(".json")) {
                    let _ = fs::remove_file(entry.path());
                }
            }
        }
        Ok(Self(dir))
    }

    /// The file of the patch of the changes of `run`.
    pub(crate) fn patch(&self, run: &str) -> PathBuf {
        self.0.join(format!("{run}.patch"))
    }

    fn record(&self, run: &str) -> PathBuf {
        self.0.join(format!("{run}.json"))
    }

    pub(crate) fn write(&self, run: &str, record: &RunRecord) -> Result<(), Refusal> {
        let path = self.record(run);
        let text = serde_json::to_string_pretty(record).map_err(io::Error::other);
        text.and_then(|text| fs::write(&path, text + "\n"))
            .map_err(|error| refused(code::GIT_FAILED, format!("cannot write the run record {}: {error}", path.display())))
    }

    /// The record of `run`, or `None` when this checkout has none.
    pub(crate) fn read(&self, run: &str) -> Result<Option<RunRecord>, Refusal> {
        let path = self.record(run);
        let text = match fs::read_to_string(&path) {
            Ok(text) => text,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
            Err(error) => {
                return Err(refused(
                    code::USAGE,
                    format!("cannot read the run record {}: {error}", path.display()),
                ));
            }
        };
        serde_json::from_str(&text)
            .map(Some)
            .map_err(|error| refused(code::USAGE, format!("{} is not a run record: {error}", path.display())))
    }

    pub(crate) fn dir(&self) -> &Path {
        &self.0
    }
}

/// The outputs of `.yonder.toml` of `commit`. A commit without the file has none.
pub(crate) fn outputs_of(processes: &dyn Processes, checkout: &Path, commit: &str, stop: Stop<'_>) -> Result<Vec<String>, Refusal> {
    let Some(text) = config_text(processes, checkout, commit, stop)? else {
        return Ok(Vec::new());
    };
    let parsed = config::parse(&text).map_err(|error| refused(code::BAD_CONFIG, format!("{error}, in the commit {commit}")))?;
    Ok(parsed.outputs)
}

#[cfg(test)]
mod tests;
