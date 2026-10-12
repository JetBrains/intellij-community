//! The reader of `plugin-classes.txt`, the class log that `plugin.classloader.debug` names.
//!
//! A line is `<fqn> [m] <pluginId>` for a class of the main module of a plugin, or
//! `<fqn> [sub = <module>.xml] <pluginId>` for a class of a content module. The plugin part can carry a `:<prefix>`
//! suffix, which the reader drops. A plugin id can hold a space, such as `Lombook Plugin`.

use std::collections::BTreeMap;

use anyhow::{Context, bail};

/// The class counts of one log. Each line counts once, also a second line of one class name.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct ClassCounts {
    pub(crate) total: u64,
    pub(crate) by_plugin: BTreeMap<String, u64>,
    pub(crate) by_module: BTreeMap<String, u64>,
}

/// The plugin and the content module that own a class.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Owner {
    pub(crate) plugin: String,
    /// The content module. A class of the main module of a plugin has none.
    pub(crate) module: Option<String>,
}

/// One log: the owner of each class and the counts of its lines.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct PluginLog {
    /// The owner by the class name, such as `com.intellij.Foo$Bar`. The first line of a class name sets its owner.
    pub(crate) owners: BTreeMap<String, Owner>,
    pub(crate) counts: ClassCounts,
}

/// Parses a log in one pass. A line of another shape is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<PluginLog> {
    let mut log = PluginLog::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        let (name, module, plugin) = parse_line(line).with_context(|| format!("line {}: {line}", index + 1))?;
        let counts = &mut log.counts;
        counts.total += 1;
        *counts.by_plugin.entry(plugin.to_owned()).or_default() += 1;
        if let Some(module) = module {
            *counts.by_module.entry(module.to_owned()).or_default() += 1;
        }
        log.owners.entry(name.to_owned()).or_insert_with(|| Owner {
            plugin: plugin.to_owned(),
            module: module.map(str::to_owned),
        });
    }
    Ok(log)
}

/// The class name, the content module when the class is in one, and the plugin id of one line.
fn parse_line(line: &str) -> anyhow::Result<(&str, Option<&str>, &str)> {
    let Some((name, rest)) = line.split_once(" [") else {
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
    Ok((name.trim(), module, plugin))
}

#[cfg(test)]
mod tests;
