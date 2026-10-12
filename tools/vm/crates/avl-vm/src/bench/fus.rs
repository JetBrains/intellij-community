//! The reader of the FUS event log: `<system>/event-log-data/logs/FUS/*.log`, one JSON object per line.
//!
//! The IDE uploads and removes the log files, so the controller reads them while the IDE runs and keeps each
//! complete line that it saw in `fus.jsonl`.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use anyhow::Context;
use serde::Deserialize;

/// The group of the welcome screen events.
pub(crate) const WELCOME_GROUP: &str = "welcome.screen.startup.performance";
/// The event of both screens. Its `is_modal` field tells the screen.
pub(crate) const WELCOME_BECAME_VISIBLE: &str = "welcome.screen.became.visible";
pub(crate) const FRAME_BECAME_VISIBLE: &str = "non.modal.welcome.screen.frame.became.visible";
pub(crate) const FRAME_BECAME_INTERACTIVE: &str = "non.modal.welcome.screen.frame.became.interactive";

/// One event line.
#[derive(Clone, Debug, PartialEq, Deserialize)]
pub(crate) struct Event {
    pub(crate) group: Group,
    pub(crate) event: EventBody,
}

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct Group {
    pub(crate) id: String,
}

#[derive(Clone, Debug, PartialEq, Deserialize)]
pub(crate) struct EventBody {
    pub(crate) id: String,
    #[serde(default)]
    pub(crate) data: serde_json::Map<String, serde_json::Value>,
}

impl Event {
    /// The `duration_ms` field.
    pub(crate) fn duration_ms(&self) -> Option<f64> {
        self.event.data.get("duration_ms").and_then(serde_json::Value::as_f64)
    }

    /// The `is_modal` field.
    pub(crate) fn is_modal(&self) -> Option<bool> {
        self.event.data.get("is_modal").and_then(serde_json::Value::as_bool)
    }
}

/// Parses `fus.jsonl`. A line that is not an event is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<Vec<Event>> {
    text.lines()
        .enumerate()
        .filter(|(_, line)| !line.trim().is_empty())
        .map(|(index, line)| serde_json::from_str(line).with_context(|| format!("line {} is not a FUS event", index + 1)))
        .collect()
}

/// The first event of the welcome group with this id.
pub(crate) fn first<'a>(events: &'a [Event], id: &str) -> Option<&'a Event> {
    events.iter().find(|event| event.group.id == WELCOME_GROUP && event.event.id == id)
}

/// The FUS log directory of a sandbox system directory.
pub(crate) fn log_dir(system: &Path) -> PathBuf {
    system.join("event-log-data").join("logs").join("FUS")
}

/// The complete lines of the FUS logs, kept across the reads of a run in the order of the first read.
#[derive(Debug, Default)]
pub(crate) struct Collector {
    seen: HashSet<String>,
    lines: Vec<String>,
}

impl Collector {
    /// Reads every `*.log` file of `dir` in name order and keeps each new complete line. A line without its newline
    /// is still being written, so the next read takes it.
    pub(crate) fn read(&mut self, dir: &Path) {
        let Ok(entries) = std::fs::read_dir(dir) else {
            return;
        };
        let mut files: Vec<PathBuf> = entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.extension().is_some_and(|extension| extension == "log"))
            .collect();
        files.sort();
        for file in files {
            let Ok(text) = std::fs::read_to_string(&file) else {
                continue;
            };
            self.add(&text);
        }
    }

    /// Keeps each new complete line of `text`.
    pub(crate) fn add(&mut self, text: &str) {
        let complete = text.rfind('\n').map_or("", |end| &text[..end]);
        for line in complete.lines().filter(|line| !line.trim().is_empty()) {
            if self.seen.insert(line.to_owned()) {
                self.lines.push(line.to_owned());
            }
        }
    }

    /// Tells whether a kept line holds this event id.
    pub(crate) fn has_event(&self, id: &str) -> bool {
        let needle = format!("\"id\":\"{id}\"");
        self.lines.iter().any(|line| line.contains(&needle))
    }

    /// The kept lines as the text of `fus.jsonl`.
    pub(crate) fn text(&self) -> String {
        self.lines.iter().map(|line| format!("{line}\n")).collect()
    }
}

#[cfg(test)]
mod tests;
