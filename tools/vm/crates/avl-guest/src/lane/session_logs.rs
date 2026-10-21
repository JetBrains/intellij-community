//! The recorded sessions of a run in the context of the lane IDE, as [`avl_wire::session_log`] states them: the zips
//! that the run request names, unpacked before the first suite and removed at the end of the run.
//!
//! A recorded session can be a private conversation. So its zip leaves the jar store once it is unpacked, and its copy
//! leaves the context when the run ends. A copy of an earlier run that ended without that goes before the new copies.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};

use avl_wire::daemon::SessionLog;
use avl_wire::session_log::{CONTEXT_DIRECTORY, unpack};
use serde_json::{Value, json};

use super::channel::log;

#[cfg(test)]
mod tests;

/// One recorded session in the context: its label and its events file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct StagedLog {
    pub label: String,
    pub events: PathBuf,
}

/// The runner value of `logs`, the `AirSessionLogFile` list of the bridge, or `None` for no log: a runner with no log
/// answers no value.
pub(crate) fn runner_value(logs: &[StagedLog]) -> Option<Value> {
    (!logs.is_empty()).then(|| {
        Value::Array(
            logs.iter()
                .map(|log| json!({"label": log.label, "eventsPath": log.events.display().to_string()}))
                .collect(),
        )
    })
}

/// The copies of one run. They leave the context when this value is dropped, also on a panic of the run.
#[derive(Debug, Default)]
pub(crate) struct Staged {
    directory: Option<PathBuf>,
    pub logs: Vec<StagedLog>,
}

impl Drop for Staged {
    fn drop(&mut self) {
        if let Some(directory) = self.directory.take()
            && let Err(error) = fs::remove_dir_all(&directory)
        {
            log(&format!("cannot remove the recorded sessions {}: {error}", directory.display()));
        }
    }
}

/// Unpacks each log of `logs` into `<context>/`[`CONTEXT_DIRECTORY`]`/<label>/`. `stored` answers the zip that the push
/// stored under a digest. A run with no IDE context stages nothing. The zips leave the store once every log is
/// unpacked, because two logs may share a digest.
pub(crate) fn stage(
    context: Option<&Path>,
    logs: &[SessionLog],
    stored: &dyn Fn(&str) -> Result<PathBuf, String>,
) -> Result<Staged, String> {
    let (Some(context), false) = (context, logs.is_empty()) else {
        return Ok(Staged::default());
    };
    let directory = context.join(CONTEXT_DIRECTORY);
    if directory.exists() {
        fs::remove_dir_all(&directory).map_err(|error| format!("cannot remove {}: {error}", directory.display()))?;
    }
    let mut staged = Staged {
        directory: Some(directory.clone()),
        logs: Vec::new(),
    };
    let mut zips = BTreeSet::new();
    for session in logs {
        let plain = |character: char| character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-');
        if session.label.is_empty()
            || session.label.chars().any(|character| !plain(character))
            || session.label == "."
            || session.label == ".."
        {
            return Err(format!("the recorded session label '{}' is no plain name", session.label));
        }
        let zip = stored(&session.sha256)?;
        let events = unpack(&zip, &directory.join(&session.label))?;
        staged.logs.push(StagedLog {
            label: session.label.clone(),
            events,
        });
        zips.insert(zip);
    }
    for zip in zips {
        if let Err(error) = fs::remove_file(&zip) {
            log(&format!("cannot remove the stored session log {}: {error}", zip.display()));
        }
    }
    Ok(staged)
}
