//! `bench ls`: the sessions of this host, newest first by the mtime of their `session.json`, which is the order of
//! `latest`. A line holds the inputs of a session and, from its `summary.json`, the runs and the median per arm. The
//! name of a line is a name that [`session::locate`] accepts.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;
use std::time::SystemTime;

use jiff::Timestamp;
use jiff::tz::TimeZone;
use serde::Serialize;

use super::arm::Arm;
use super::digest::{minute, number, short};
use super::session::{self, SUMMARY_FILE};
use super::summary::Summary;

/// The width of the command column: the longest command, `open-project`.
const COMMAND_WIDTH: usize = 12;

/// The answer of `bench ls`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Listing {
    /// The directory of the sessions: `<runtime root>/bench/runs`.
    pub(crate) runs_root: String,
    /// The sessions under it, listed or not.
    pub(crate) total: usize,
    /// The newest sessions, at most `--limit`, newest first.
    pub(crate) sessions: Vec<ListedSession>,
}

/// One session of the listing.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ListedSession {
    /// The directory name, which [`session::locate`] accepts.
    pub(crate) name: String,
    pub(crate) path: String,
    /// The mtime of `session.json`, in RFC 3339 to the second.
    pub(crate) modified: String,
    /// The mtime of `session.json`, for the text.
    #[serde(skip)]
    pub(crate) modified_at: SystemTime,
    /// `welcome` or `open-project`. This and the next three fields come from `session.json`, and a `session.json`
    /// of another shape gives none.
    pub(crate) command: Option<String>,
    pub(crate) commit: Option<String>,
    pub(crate) dirty: bool,
    pub(crate) dist_digest: Option<String>,
    /// By [`Arm::key`], from `summary.json`. A session without a readable summary has none.
    pub(crate) arms: Option<BTreeMap<String, ListedArm>>,
    /// The session has a `summary.json`, readable or not.
    #[serde(skip)]
    pub(crate) has_summary: bool,
    /// Why a file of the session could not be read.
    pub(crate) notes: Vec<String>,
}

/// One arm of a listed session.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ListedArm {
    pub(crate) valid_runs: usize,
    pub(crate) measured_runs: usize,
    /// The main metric of the arm. See [`Arm::main_metric`].
    pub(crate) metric: &'static str,
    /// The median of the metric over the valid runs. An arm without a valid run with the metric has none.
    pub(crate) median: Option<f64>,
}

/// Lists the newest `limit` sessions under `runs_root`. See [`session::newest_first`].
pub(crate) fn report(runs_root: &Path, limit: u32) -> Listing {
    let sessions = session::newest_first(runs_root);
    let total = sessions.len();
    let limit = usize::try_from(limit).unwrap_or(usize::MAX);
    Listing {
        runs_root: runs_root.display().to_string(),
        total,
        sessions: sessions
            .into_iter()
            .take(limit)
            .map(|(dir, modified)| listed(&dir, modified))
            .collect(),
    }
}

fn listed(dir: &Path, modified: SystemTime) -> ListedSession {
    let path = dir.display().to_string();
    let mut notes = Vec::new();
    let info = session::read_info(dir).map_err(|error| notes.push(format!("{error:#}"))).ok();
    let has_summary = dir.join(SUMMARY_FILE).is_file();
    let arms = if has_summary {
        session::read_summary(dir)
            .map_err(|refusal| notes.push(refusal.message))
            .ok()
            .map(|summary| arms(&summary))
    } else {
        None
    };
    ListedSession {
        name: session::display_name(&path),
        path,
        modified: Timestamp::try_from(modified)
            .and_then(|timestamp| Timestamp::from_second(timestamp.as_second()))
            .map_or_else(|_| String::new(), |timestamp| timestamp.to_string()),
        modified_at: modified,
        command: info.as_ref().map(|info| info.command.clone()),
        commit: info.as_ref().map(|info| info.git.commit.clone()),
        dirty: info.as_ref().is_some_and(|info| info.git.dirty),
        dist_digest: info.as_ref().map(|info| info.generation.dist_digest.clone()),
        arms,
        has_summary,
        notes,
    }
}

fn arms(summary: &Summary) -> BTreeMap<String, ListedArm> {
    summary
        .arms
        .iter()
        .map(|(key, arm)| {
            let metric = arm.arm.main_metric();
            let listed = ListedArm {
                valid_runs: arm.valid_runs,
                measured_runs: arm.runs.len(),
                metric,
                median: arm.summary.get(metric).map(|stat| stat.median),
            };
            (key.clone(), listed)
        })
        .collect()
}

impl Listing {
    /// The text, with the times in `zone`: a heading, then one line per session and its notes.
    pub(crate) fn text(&self, zone: &TimeZone) -> String {
        let mut text = String::new();
        if self.sessions.is_empty() {
            let _ = write!(text, "vm bench ls: no session under {}", self.runs_root);
            return text;
        }
        let _ = writeln!(
            text,
            "vm bench ls: {} of {} sessions under {}, newest first",
            self.sessions.len(),
            self.total,
            self.runs_root
        );
        let _ = writeln!(
            text,
            "per arm: the valid/measured runs and the median ms of {}; open-project: \"{}\"; project: \"{}\"; empty-editor: {}",
            Arm::Modal.main_metric(),
            Arm::OpenProject.main_metric(),
            Arm::Project.main_metric(),
            Arm::EmptyEditor.main_metric()
        );
        let name_width = self.sessions.iter().map(|listed| listed.name.chars().count()).max().unwrap_or(0);
        for listed in &self.sessions {
            let unknown = || "-".to_owned();
            let dirty = if listed.dirty { "(dirty)" } else { "" };
            let arms = listed.arms.as_ref().map_or_else(
                || {
                    if listed.has_summary {
                        "(summary not readable)"
                    } else {
                        "(no summary)"
                    }
                    .to_owned()
                },
                |arms| {
                    Arm::ALL
                        .into_iter()
                        .filter_map(|arm| arms.get(arm.key()).map(|listed| (arm, listed)))
                        .map(|(arm, listed)| {
                            let median = listed.median.map_or_else(unknown, number);
                            format!("{} {}/{} {median}", arm.label(), listed.valid_runs, listed.measured_runs)
                        })
                        .collect::<Vec<_>>()
                        .join(", ")
                },
            );
            let line = format!(
                "  {:<name_width$}  {}  {:<COMMAND_WIDTH$}  {:<12} {dirty:<7}  dist {:<12}  {arms}",
                listed.name,
                minute(listed.modified_at, zone),
                listed.command.clone().unwrap_or_else(unknown),
                listed.commit.as_deref().map_or_else(unknown, short),
                listed.dist_digest.as_deref().map_or_else(unknown, short),
            );
            let _ = writeln!(text, "{}", line.trim_end());
            for note in &listed.notes {
                let _ = writeln!(text, "    {note}");
            }
        }
        text.truncate(text.trim_end().len());
        text
    }
}

#[cfg(test)]
mod tests;
