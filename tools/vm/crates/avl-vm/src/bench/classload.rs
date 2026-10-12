//! The reader of `class-load.log`, the JVM class-load log that `-Xlog:class+load` writes with the `uptime,tags`
//! decorators.
//!
//! A line of the class-load log is `[<uptime>][class,load] <name> source: <source>`. The uptime has the unit `s`,
//! `ms` or `ns`. Further decorators such as `[info]` can follow the uptime. The JVM also writes `opened: <path>` for
//! the module image, which the reader skips.
//!
//! The uptime is on the JVM clock, which starts at the JVM start. The items, the trace and the timeline use the
//! process clock, which starts later. [`super::stats::StartupStats::jvm_loading_ms`] is the offset. A caller adds it
//! to a bound on the process clock before it asks the log.

use std::collections::BTreeMap;

use anyhow::{Context, bail};

use super::pluginlog::{Owner, PluginLog};

/// The marker of a hidden class in the class name, such as `java.lang.invoke.LambdaForm$MH/0x0000000801001000`.
const HIDDEN_MARKER: &str = "/0x";

/// One class of the class-load log.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct LoadedClass {
    /// Milliseconds on the JVM clock.
    pub(crate) uptime_ms: f64,
    pub(crate) name: String,
    pub(crate) source: Source,
}

/// The source of a class.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Source {
    /// The CDS archive, the module image or the boot loader: `shared objects file`, `jrt:/<module>` or
    /// `instance of <caller>`.
    Jdk,
    /// A jar or a directory of the class path: a `file:` or `jar:` URL, or an absolute path, which can hold a space.
    /// The JVM writes the path for an entry of `-classpath`.
    Jar(String),
    /// A class that a class loader defined from bytes. The JVM writes `__JVM_DefineClass__` for a class of an IDE
    /// loader, `__dynamic_proxy__` for a proxy, and the name of the loader class in older versions. The reader drops
    /// a ` @<hash>` suffix.
    Loader(String),
    /// A hidden class, whose name holds `/0x`.
    Hidden,
}

/// The classes of each kind of source.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct SourceCounts {
    pub(crate) jdk: u64,
    pub(crate) jar: u64,
    pub(crate) loader: u64,
    pub(crate) hidden: u64,
}

/// The classes of one class-load log, in file order.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct ClassLoadLog {
    pub(crate) classes: Vec<LoadedClass>,
}

impl ClassLoadLog {
    /// The classes that are not hidden.
    pub(crate) fn named(&self) -> impl Iterator<Item = &LoadedClass> {
        self.classes.iter().filter(|class| class.source != Source::Hidden)
    }

    /// The named classes before the bound `uptime_ms` on the JVM clock.
    pub(crate) fn count_before(&self, uptime_ms: f64) -> u64 {
        count(self.named().filter(|class| class.uptime_ms < uptime_ms))
    }

    /// The classes of each kind of source.
    pub(crate) fn by_source_kind(&self) -> SourceCounts {
        let mut counts = SourceCounts::default();
        for class in &self.classes {
            match class.source {
                Source::Jdk => counts.jdk += 1,
                Source::Jar(_) => counts.jar += 1,
                Source::Loader(_) => counts.loader += 1,
                Source::Hidden => counts.hidden += 1,
            }
        }
        counts
    }

    /// The named classes before the bound `before_ms` on the JVM clock. No bound takes every named class.
    fn named_before(&self, before_ms: Option<f64>) -> impl Iterator<Item = &LoadedClass> {
        self.named()
            .filter(move |class| before_ms.is_none_or(|bound| class.uptime_ms < bound))
    }
}

/// The named classes of each plugin before the bound `before_ms` on the JVM clock. The plugin log gives the owner of a
/// class. The count skips a class without an owner.
pub(crate) fn by_plugin(log: &ClassLoadLog, owners: &PluginLog, before_ms: Option<f64>) -> BTreeMap<String, u64> {
    by_owner(log, owners, before_ms, |owner| Some(&owner.plugin))
}

/// The named classes of each content module before the bound `before_ms` on the JVM clock. The count skips a class
/// without an owner and a class of the main module of a plugin.
pub(crate) fn by_module(log: &ClassLoadLog, owners: &PluginLog, before_ms: Option<f64>) -> BTreeMap<String, u64> {
    by_owner(log, owners, before_ms, |owner| owner.module.as_ref())
}

/// The named classes before the bound `before_ms` on the JVM clock, by the key that `key` takes from the owner.
fn by_owner(
    log: &ClassLoadLog,
    owners: &PluginLog,
    before_ms: Option<f64>,
    key: impl Fn(&Owner) -> Option<&String>,
) -> BTreeMap<String, u64> {
    let mut counts = BTreeMap::new();
    for class in log.named_before(before_ms) {
        if let Some(key) = owners.owners.get(&class.name).and_then(&key) {
            *counts.entry(key.clone()).or_default() += 1;
        }
    }
    counts
}

/// The classes of the core loader before the bound `before_ms` on the JVM clock: the named classes that a class loader
/// defines and that have no owner in the plugin log.
pub(crate) fn platform_count(log: &ClassLoadLog, owners: &PluginLog, before_ms: Option<f64>) -> u64 {
    count(log.named_before(before_ms).filter(|class| is_platform(class, owners)))
}

/// Tells whether a class is a class of the core loader: a class loader defined it, and the plugin log names no owner.
fn is_platform(class: &LoadedClass, owners: &PluginLog) -> bool {
    matches!(class.source, Source::Loader(_)) && !owners.owners.contains_key(&class.name)
}

/// Parses a class-load log. A line of another shape is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<ClassLoadLog> {
    let mut log = ClassLoadLog::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        if let Some(class) = parse_line(line).with_context(|| format!("line {}: {line}", index + 1))? {
            log.classes.push(class);
        }
    }
    Ok(log)
}

/// The class of one line, or none for an `opened:` line.
fn parse_line(line: &str) -> anyhow::Result<Option<LoadedClass>> {
    let Some((uptime, mut rest)) = line.strip_prefix('[').and_then(|line| line.split_once(']')) else {
        bail!("no `[<uptime>]` at the start");
    };
    let uptime_ms = parse_uptime(uptime)?;
    while let Some(decorated) = rest.strip_prefix('[') {
        let Some((_, after)) = decorated.split_once(']') else {
            bail!("no `]` after a decorator");
        };
        rest = after;
    }
    let Some(message) = rest.strip_prefix(' ') else {
        bail!("no space after the decorators");
    };
    if message.starts_with("opened: ") {
        return Ok(None);
    }
    let Some((name, source)) = message.split_once(" source: ") else {
        bail!("no ` source: ` after the class name");
    };
    if name.is_empty() || name.contains(' ') {
        bail!("the class name `{name}` is empty or holds a space");
    }
    let source = if name.contains(HIDDEN_MARKER) {
        Source::Hidden
    } else {
        parse_source(source)?
    };
    Ok(Some(LoadedClass {
        uptime_ms,
        name: name.to_owned(),
        source,
    }))
}

/// The milliseconds of an uptime such as `12.345s`, `12345ms` or `12345000000ns`.
fn parse_uptime(uptime: &str) -> anyhow::Result<f64> {
    let (value, to_ms): (&str, fn(f64) -> f64) = if let Some(value) = uptime.strip_suffix("ms") {
        (value, |ms| ms)
    } else if let Some(value) = uptime.strip_suffix("ns") {
        (value, |ns| ns / 1e6)
    } else if let Some(value) = uptime.strip_suffix('s') {
        (value, |s| s * 1e3)
    } else {
        bail!("the uptime `{uptime}` has no unit s, ms or ns");
    };
    let value = value
        .parse::<f64>()
        .with_context(|| format!("the uptime `{uptime}` is not a number"))?;
    Ok(to_ms(value))
}

fn parse_source(source: &str) -> anyhow::Result<Source> {
    let source = source.trim();
    if source.starts_with("shared objects file") || source.starts_with("jrt:/") || source.starts_with("instance of ") {
        return Ok(Source::Jdk);
    }
    if source.starts_with("file:") || source.starts_with("jar:") || is_absolute_path(source) {
        return Ok(Source::Jar(source.to_owned()));
    }
    let loader = source.split_once(" @").map_or(source, |(loader, _)| loader);
    if loader.is_empty() {
        bail!("the source is empty");
    }
    Ok(Source::Loader(loader.to_owned()))
}

/// Tells whether a source is a path of the class path: `/…` on a Unix host, or `C:\…` and `C:/…` on Windows. A path
/// can hold a space.
fn is_absolute_path(source: &str) -> bool {
    source.starts_with('/') || {
        let mut characters = source.chars();
        characters.next().is_some_and(|drive| drive.is_ascii_alphabetic())
            && characters.next() == Some(':')
            && characters.next().is_some_and(|separator| separator == '\\' || separator == '/')
    }
}

fn count<T>(items: impl Iterator<Item = T>) -> u64 {
    items.fold(0, |count, _| count + 1)
}

#[cfg(test)]
mod tests;
