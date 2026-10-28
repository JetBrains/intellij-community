//! `.yonder.toml` of the commit of a run. The format and its parser are `config` of `yonder-link`, which the client
//! shares. Here the supervisor reads the file from the commit of the run, and an error of the file refuses the run with
//! `bad_config`.

use std::path::Path;

pub(crate) use yonder_link::config::{EnvValue, FILE, RepoConfig, Step};

use crate::git;
use crate::refused::{BAD_CONFIG, Refusal, refused};

/// The configuration of `commit` in the repository of `dir`, or the default one when the commit has no `.yonder.toml`.
pub(crate) fn read(dir: &Path, commit: &str) -> Result<RepoConfig, Refusal> {
    let cannot = |error: String| refused(BAD_CONFIG, format!("cannot read {FILE} of {commit}: {error}"));
    let ids = git::object_ids(dir, commit, &[FILE.to_owned()]).map_err(cannot)?;
    let Some(Some(blob)) = ids.first() else {
        return Ok(RepoConfig::default());
    };
    let bytes = git::blob(dir, blob).map_err(cannot)?;
    let text = String::from_utf8(bytes).map_err(|error| cannot(format!("it is not UTF-8: {error}")))?;
    parse(&text)
}

/// Parses and checks the text of `.yonder.toml`. An error refuses with `bad_config`.
pub(crate) fn parse(text: &str) -> Result<RepoConfig, Refusal> {
    yonder_link::config::parse(text).map_err(|error| refused(BAD_CONFIG, error.to_string()))
}

#[cfg(test)]
mod tests;
