//! `.yonder.toml` of the commit of a run. The format and its parser are `config` of `yonder-link`, which the client
//! shares. Here the supervisor reads the file from the commit of the run, and an error of the file refuses the run with
//! `bad_config`.

use std::path::Path;

pub(crate) use yonder_link::config::{EnvValue, FILE, RepoConfig, Step};

use crate::refused::{BAD_CONFIG, Refusal, refused};
use crate::repo;

/// The configuration of `commit` in the repository `repo` (`bd lease host show`), or the default one when the commit
/// has no `.yonder.toml`.
pub(crate) fn read(repo: &Path, commit: &str) -> Result<RepoConfig, Refusal> {
    let shown = repo::show(repo, commit, Some(FILE), &[])
        .map_err(|error| refused(BAD_CONFIG, format!("cannot read {FILE} of {commit}: {error}")))?;
    of_blob(shown.blob.as_deref())
}

/// The configuration of the text of `.yonder.toml` in a commit, or the default one for a commit without the file.
pub(crate) fn of_blob(text: Option<&str>) -> Result<RepoConfig, Refusal> {
    text.map_or_else(|| Ok(RepoConfig::default()), parse)
}

/// Parses and checks the text of `.yonder.toml`. An error refuses with `bad_config`.
pub(crate) fn parse(text: &str) -> Result<RepoConfig, Refusal> {
    yonder_link::config::parse(text).map_err(|error| refused(BAD_CONFIG, error.to_string()))
}

#[cfg(test)]
mod tests;
