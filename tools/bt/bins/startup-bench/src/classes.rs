//! The reader of `plugin-classes.txt`, the class log that `plugin.classloader.debug` names.
//!
//! A line is `<fqn> [m] <pluginId>` for a class of the main module of a plugin, or
//! `<fqn> [sub = <module>.xml] <pluginId>` for a class of a content module. The plugin part can carry a `:<prefix>`
//! suffix, which the reader drops. A plugin id can hold a space, such as `Lombook Plugin`.

use std::collections::BTreeMap;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};

/// The class counts of one log.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct ClassCounts {
    pub(crate) total: u64,
    pub(crate) by_plugin: BTreeMap<String, u64>,
    pub(crate) by_module: BTreeMap<String, u64>,
}

/// A count with its name, for the top lists.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct Count {
    pub(crate) name: String,
    pub(crate) classes: u64,
}

impl ClassCounts {
    /// The `limit` largest counts of a map, the largest first, then by name.
    pub(crate) fn top(counts: &BTreeMap<String, u64>, limit: usize) -> Vec<Count> {
        let mut sorted: Vec<Count> = counts
            .iter()
            .map(|(name, classes)| Count {
                name: name.clone(),
                classes: *classes,
            })
            .collect();
        sorted.sort_by(|left, right| right.classes.cmp(&left.classes).then_with(|| left.name.cmp(&right.name)));
        sorted.truncate(limit);
        sorted
    }
}

/// Parses a log. A line of another shape is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<ClassCounts> {
    let mut counts = ClassCounts::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        let (module, plugin) = parse_line(line).with_context(|| format!("line {}: {line}", index + 1))?;
        counts.total += 1;
        *counts.by_plugin.entry(plugin.to_owned()).or_default() += 1;
        if let Some(module) = module {
            *counts.by_module.entry(module.to_owned()).or_default() += 1;
        }
    }
    Ok(counts)
}

/// The content module, when the class is in one, and the plugin id of one line.
fn parse_line(line: &str) -> anyhow::Result<(Option<&str>, &str)> {
    let Some((_, rest)) = line.split_once(" [") else {
        bail!("no `[` marker after the class name");
    };
    let Some((marker, plugin)) = rest.split_once("] ") else {
        bail!("no `] ` after the marker");
    };
    let module = match marker {
        "m" => None,
        sub => match sub.strip_prefix("sub = ").and_then(|module| module.strip_suffix(".xml")) {
            Some(module) => Some(module),
            None => bail!("the marker [{sub}] is neither [m] nor [sub = <module>.xml]"),
        },
    };
    let plugin = plugin.split_once(':').map_or(plugin, |(id, _)| id).trim();
    if plugin.is_empty() {
        bail!("no plugin id after the marker");
    }
    Ok((module, plugin))
}

#[cfg(test)]
mod tests;
