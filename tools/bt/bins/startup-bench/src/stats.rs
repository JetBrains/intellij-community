//! The reader of `startup-stats.json`, the start-up report that `idea.log.perf.stats.file` names.
//!
//! The reader supports the version that the repository writes, and refuses another version by name.

use anyhow::{Context, bail};
use serde::Deserialize;

/// The report version that the reader supports.
pub(crate) const SUPPORTED_VERSION: &str = "38";

/// The fields of the report that the controller reads. The reader ignores the other fields.
#[derive(Clone, Debug, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct StartupStats {
    pub(crate) version: String,
    pub(crate) items: Vec<Item>,
    #[serde(default)]
    pub(crate) trace_events: Vec<TraceEvent>,
    pub(crate) class_loading: ClassLoading,
    #[serde(default)]
    pub(crate) plugins: Vec<Plugin>,
    /// Milliseconds.
    pub(crate) total_duration: i64,
}

/// One activity. The report writes the start and the duration in milliseconds from the start of the process.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct Item {
    #[serde(rename = "n")]
    pub(crate) name: String,
    #[serde(rename = "s")]
    pub(crate) start: i64,
    #[serde(rename = "d")]
    pub(crate) duration: i64,
    #[serde(rename = "t", default)]
    pub(crate) thread: String,
}

/// One instant event. `ts` is in microseconds from the start of the process.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct TraceEvent {
    pub(crate) name: String,
    pub(crate) ts: i64,
}

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct ClassLoading {
    pub(crate) count: i64,
    /// Milliseconds, summed over the threads.
    pub(crate) time: i64,
}

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Plugin {
    pub(crate) id: String,
    pub(crate) class_count: i64,
    /// Milliseconds on the EDT.
    pub(crate) class_loading_edt_time: i64,
    #[serde(default)]
    pub(crate) modules: Vec<PluginModule>,
}

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PluginModule {
    pub(crate) name: String,
    pub(crate) class_count: i64,
    #[serde(default)]
    pub(crate) class_loading_edt_time: i64,
}

impl StartupStats {
    /// The first item with this name.
    pub(crate) fn item(&self, name: &str) -> Option<&Item> {
        self.items.iter().find(|item| item.name == name)
    }

    /// The milliseconds of class loading on the EDT, summed over the plugins.
    pub(crate) fn edt_class_loading_ms(&self) -> i64 {
        self.plugins.iter().map(|plugin| plugin.class_loading_edt_time).sum()
    }
}

/// Parses a report. An unsupported version or a missing field is an error that names it.
pub(crate) fn parse(text: &str) -> anyhow::Result<StartupStats> {
    #[derive(Deserialize)]
    struct Version {
        version: Option<String>,
    }
    let version: Version = serde_json::from_str(text).context("not a JSON object")?;
    match version.version.as_deref() {
        Some(SUPPORTED_VERSION) => {}
        Some(other) => bail!("version {other} is not supported, only {SUPPORTED_VERSION}"),
        None => bail!("no `version` field"),
    }
    serde_json::from_str(text).context("not the shape of version 38")
}

#[cfg(test)]
mod tests;
