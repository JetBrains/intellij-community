//! The reader of `plugin-classes.txt`, the class log that `plugin.classloader.debug` names.
//!
//! The log has two shapes. The spec `community/platform/core-impl/spec/plugin-class-log.spec.md` is the contract of
//! the current shape, the tab-separated format v2. Its first line is the header `#plugin-classes\t2`, and [`parse_v2`]
//! reads it. A header of another version is an error.
//!
//! A log without a header has the legacy shape v1, which [`parse_v1`] reads for a distribution from before the spec.
//! A v1 record is one line `<fqn> [m] <pluginId>` for a class of the main module of a plugin, or
//! `<fqn> [sub = <module>.xml] <pluginId>` for a class of a content module. The reader drops a `:<prefix>` suffix of
//! the plugin id. A later v1 log adds the thread, `edt` or `bg`, and the `findClass` nanos to each record. Its first
//! class of a loader continues with `\tat <frame>` lines, and a define error with a `\terror: <message>` line.

use std::collections::BTreeMap;

use anyhow::{Context, bail};

/// The prefix of the header line of a versioned log. The version follows it.
const HEADER_PREFIX: &str = "#plugin-classes\t";
/// The version of the format v2.
const V2: &str = "2";

/// The prefix of a frame line of an activation stack.
const FRAME_PREFIX: &str = "\tat ";
/// The prefix of the line of a define error.
const ERROR_PREFIX: &str = "\terror: ";

/// The class counts of one log. Each record counts once, also a second record of one class name. A record with a
/// define error does not count.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct ClassCounts {
    pub(crate) total: u64,
    pub(crate) by_plugin: BTreeMap<String, u64>,
    /// By the loader of the records.
    pub(crate) by_loader: BTreeMap<Owner, Tally>,
}

/// The plugin and the content module that own a class: the loader of the class.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(crate) struct Owner {
    pub(crate) plugin: String,
    /// The content module. A class of the main module of a plugin has none.
    pub(crate) module: Option<String>,
}

impl Owner {
    /// The name of a loader in a table with a plugin column: the content module, else `<plugin id> (main)`.
    pub(crate) fn loader_name(plugin: &str, module: Option<&str>) -> String {
        module.map_or_else(|| format!("{plugin} (main)"), str::to_owned)
    }

    /// The name of a loader in a list without a plugin column: `<module> (<plugin id>)`, else `<plugin id> (main)`.
    pub(crate) fn loader_label(plugin: &str, module: Option<&str>) -> String {
        module.map_or_else(|| format!("{plugin} (main)"), |module| format!("{module} ({plugin})"))
    }
}

/// The thread that defined a class.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Thread {
    Edt,
    Background,
}

/// How the loader defined a class: on which thread, and how long `findClass` took. A v1 log of the new shape names the
/// thread. A v2 log names none, and the JVM class-load log gives it, see [`Define::on`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Define {
    pub(crate) thread: Option<Thread>,
    pub(crate) nanos: u64,
}

impl Define {
    /// The define with the thread of the log, else `thread`.
    #[must_use]
    pub(crate) fn on(self, thread: Option<Thread>) -> Self {
        Self {
            thread: self.thread.or(thread),
            ..self
        }
    }
}

/// One class of the log: its owner, and its define when the log has the new shape.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct PluginClass {
    pub(crate) owner: Owner,
    pub(crate) define: Option<Define>,
}

/// The first class of a loader and the stack that loaded it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Activation {
    /// The class of the record with the stack, else the class of the first record of the loader.
    pub(crate) first_class: String,
    /// The frames, the top frame first. A log of the old shape has none.
    pub(crate) stack: Vec<String>,
}

/// The classes of one loader, or of one plugin, and the defines that the log times.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Tally {
    pub(crate) classes: u64,
    /// The classes with a define on a known thread, see [`Define`].
    pub(crate) timed: u64,
    pub(crate) edt_classes: u64,
    pub(crate) edt_nanos: u64,
    pub(crate) bg_nanos: u64,
}

impl Tally {
    /// Adds one class, with its define when the log has one. A define without a thread counts as a class with no time.
    pub(crate) const fn add(&mut self, define: Option<Define>) {
        self.classes += 1;
        if let Some(Define {
            thread: Some(thread),
            nanos,
        }) = define
        {
            self.timed += 1;
            match thread {
                Thread::Edt => {
                    self.edt_classes += 1;
                    self.edt_nanos += nanos;
                }
                Thread::Background => self.bg_nanos += nanos,
            }
        }
    }

    /// Tells whether the log times every class of the tally, so its EDT and BG times are whole.
    pub(crate) const fn is_timed(&self) -> bool {
        self.classes > 0 && self.timed == self.classes
    }
}

/// One log: the classes, the activation of each loader, and the counts of its records.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct PluginLog {
    /// By the class name, such as `com.intellij.Foo$Bar`. The first record of a class name sets its owner.
    pub(crate) classes: BTreeMap<String, PluginClass>,
    /// By the loader. Each loader with a record has one.
    pub(crate) activations: BTreeMap<Owner, Activation>,
    pub(crate) counts: ClassCounts,
    /// The records with a define error.
    pub(crate) errors: u64,
    /// The cost of the write that the IDE measured, from the trailer of a v2 log. A v1 log, and a v2 log of a killed
    /// IDE, has none.
    pub(crate) cost: Option<PluginLogCost>,
}

/// The trailer `#cost` of a v2 log: what the writer spent on the log.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct PluginLogCost {
    /// The records that the writer wrote.
    pub(crate) records: u64,
    /// The nanos of the writes of the records.
    pub(crate) write_nanos: u64,
    /// The activation stacks that the writer captured.
    pub(crate) stacks: u64,
    /// The nanos of the capture of the stacks.
    pub(crate) stack_nanos: u64,
}

impl PluginLog {
    /// The owner of a class, when a plugin loader defined it.
    pub(crate) fn owner(&self, class: &str) -> Option<&Owner> {
        self.classes.get(class).map(|class| &class.owner)
    }
}

/// One record: the class line and the lines that continue it.
struct Record<'a> {
    name: &'a str,
    owner: Owner,
    define: Option<Define>,
    stack: Vec<String>,
    error: bool,
}

/// Parses a log in one pass. The header line selects the format. A line of another shape is an error that names the
/// line.
pub(crate) fn parse(text: &str) -> anyhow::Result<PluginLog> {
    let header = text.lines().enumerate().find(|(_, line)| !line.trim().is_empty());
    if let Some((index, line)) = header
        && let Some(version) = line.strip_prefix(HEADER_PREFIX)
    {
        if version.trim_end() != V2 {
            bail!(
                "line {}: {line}: the version {} of plugin-classes.txt is unknown; the reader takes version {V2}",
                index + 1,
                version.trim_end()
            );
        }
        return parse_v2(text, index + 1);
    }
    parse_v1(text)
}

/// Parses a v1 log, which has no header.
fn parse_v1(text: &str) -> anyhow::Result<PluginLog> {
    let mut log = PluginLog::default();
    let mut record: Option<Record<'_>> = None;
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        let context = || format!("line {}: {line}", index + 1);
        if let Some(frame) = line.strip_prefix(FRAME_PREFIX) {
            let Some(record) = record.as_mut() else {
                bail!("{}: a frame line before the first class line", context());
            };
            record.stack.push(frame.trim().to_owned());
            continue;
        }
        if line.starts_with(ERROR_PREFIX) {
            let Some(record) = record.as_mut() else {
                bail!("{}: an error line before the first class line", context());
            };
            record.error = true;
            continue;
        }
        let parsed = parse_line(line).with_context(context)?;
        if let Some(done) = record.replace(parsed) {
            add(&mut log, done);
        }
    }
    if let Some(done) = record {
        add(&mut log, done);
    }
    Ok(log)
}

fn add(log: &mut PluginLog, record: Record<'_>) {
    if record.error {
        log.errors += 1;
        return;
    }
    count_class(log, record.name, &record.owner, record.define);
    let activation = log.activations.entry(record.owner.clone()).or_insert_with(|| Activation {
        first_class: record.name.to_owned(),
        stack: Vec::new(),
    });
    if activation.stack.is_empty() && !record.stack.is_empty() {
        activation.first_class = record.name.to_owned();
        activation.stack = record.stack;
    }
}

/// Counts one defined class of `owner`. The first record of a class name sets its owner and its define.
fn count_class(log: &mut PluginLog, name: &str, owner: &Owner, define: Option<Define>) {
    let counts = &mut log.counts;
    counts.total += 1;
    *counts.by_plugin.entry(owner.plugin.clone()).or_default() += 1;
    counts.by_loader.entry(owner.clone()).or_default().add(define);
    log.classes.entry(name.to_owned()).or_insert_with(|| PluginClass {
        owner: owner.clone(),
        define,
    });
}

/// One loader of a v2 log: its owner and the class of its first `C` record.
struct V2Loader {
    owner: Owner,
    first_class: Option<String>,
}

/// Parses a v2 log, see the spec of the module doc. `header_line` is the number of the line with the header, which the
/// parse skips.
///
/// The first `C` record of a loader gives the first class of its activation, and its `S` records give the stack. The
/// reader takes an `S` record at any place after the `L` record of its loader, and it takes a log without the `#cost`
/// trailer. An `E` record counts in [`PluginLog::errors`] and not as a class. A record of another kind or shape is an
/// error that names the line. So is an `S` record of a loader without an `L` record at the end of the log.
fn parse_v2(text: &str, header_line: usize) -> anyhow::Result<PluginLog> {
    let mut log = PluginLog::default();
    let mut loaders: BTreeMap<u32, V2Loader> = BTreeMap::new();
    let mut stacks: BTreeMap<u32, (usize, Vec<String>)> = BTreeMap::new();
    for (index, line) in text
        .lines()
        .enumerate()
        .skip(header_line)
        .filter(|(_, line)| !line.trim().is_empty())
    {
        let number = index + 1;
        let context = || format!("line {number}: {line}");
        let Some((kind, rest)) = line.split_once('\t') else {
            bail!("{}: no tab after the record kind", context());
        };
        match kind {
            "L" => {
                let [id, module, plugin] = fields(rest).with_context(context)?;
                let id = loader_id(id).with_context(context)?;
                if plugin.trim().is_empty() {
                    bail!("{}: no plugin id", context());
                }
                let owner = Owner {
                    plugin: plugin.to_owned(),
                    module: (module != "-").then(|| module.to_owned()),
                };
                if loaders.insert(id, V2Loader { owner, first_class: None }).is_some() {
                    bail!("{}: the loader {id} has a second L record", context());
                }
            }
            "S" => {
                let [id, frame] = fields(rest).with_context(context)?;
                let id = loader_id(id).with_context(context)?;
                stacks
                    .entry(id)
                    .or_insert_with(|| (number, Vec::new()))
                    .1
                    .push(frame.trim().to_owned());
            }
            "C" => {
                let [id, nanos, name] = fields(rest).with_context(context)?;
                let id = loader_id(id).with_context(context)?;
                let nanos = number_field(nanos, "nanos").with_context(context)?;
                let Some(loader) = loaders.get_mut(&id) else {
                    bail!("{}: the loader {id} has no L record before it", context());
                };
                loader.first_class.get_or_insert_with(|| name.to_owned());
                count_class(&mut log, name, &loader.owner, Some(Define { thread: None, nanos }));
            }
            "E" => {
                let [id, _class_and_message] = fields(rest).with_context(context)?;
                loader_id(id).with_context(context)?;
                log.errors += 1;
            }
            "#cost" => {
                let [records, write_nanos, stacks, stack_nanos] = fields(rest).with_context(context)?;
                log.cost = Some(PluginLogCost {
                    records: number_field(records, "record count").with_context(context)?,
                    write_nanos: number_field(write_nanos, "write nanos").with_context(context)?,
                    stacks: number_field(stacks, "stack count").with_context(context)?,
                    stack_nanos: number_field(stack_nanos, "stack nanos").with_context(context)?,
                });
            }
            other => bail!("{}: the record kind `{other}` is unknown", context()),
        }
    }
    if let Some((id, (number, _))) = stacks.iter().find(|(id, _)| !loaders.contains_key(id)) {
        bail!("line {number}: an S record of the loader {id}, which has no L record");
    }
    for (id, loader) in loaders {
        if let Some(first_class) = loader.first_class {
            let stack = stacks.remove(&id).map(|(_, stack)| stack).unwrap_or_default();
            log.activations.insert(loader.owner, Activation { first_class, stack });
        }
    }
    Ok(log)
}

/// The `N` tab-separated fields of the rest of a v2 record. The last field takes the rest of the line, with its tabs.
fn fields<const N: usize>(rest: &str) -> anyhow::Result<[&str; N]> {
    let mut parts = rest.splitn(N, '\t');
    let fields: [Option<&str>; N] = std::array::from_fn(|_| parts.next().filter(|field| !field.is_empty()));
    if fields.iter().any(Option::is_none) {
        bail!("the record has fewer than {N} fields after its kind");
    }
    Ok(fields.map(Option::unwrap_or_default))
}

/// The id of a loader: a whole number.
fn loader_id(id: &str) -> anyhow::Result<u32> {
    id.parse().with_context(|| format!("the loader id `{id}` is not a whole number"))
}

/// A field that is a whole number, such as nanos.
fn number_field(value: &str, name: &str) -> anyhow::Result<u64> {
    value.parse().with_context(|| format!("the {name} `{value}` is not a whole number"))
}

/// The record of one class line: the class name, the owner, and the define of the new shape.
fn parse_line(line: &str) -> anyhow::Result<Record<'_>> {
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
    let (plugin, define) = split_define(plugin);
    let plugin = plugin.split_once(':').map_or(plugin, |(id, _)| id).trim();
    if plugin.is_empty() {
        bail!("no plugin id after the marker");
    }
    Ok(Record {
        name: name.trim(),
        owner: Owner {
            plugin: plugin.to_owned(),
            module: module.map(str::to_owned),
        },
        define,
        stack: Vec::new(),
        error: false,
    })
}

/// Splits the thread and the nanos of the new shape off the end of the plugin part. A plugin part without both is of
/// the old shape and keeps all its text.
fn split_define(plugin: &str) -> (&str, Option<Define>) {
    let parsed = plugin.trim_end().rsplit_once(' ').and_then(|(rest, nanos)| {
        let nanos = nanos.parse::<u64>().ok()?;
        let (rest, thread) = rest.rsplit_once(' ')?;
        let thread = match thread {
            "edt" => Thread::Edt,
            "bg" => Thread::Background,
            _ => return None,
        };
        Some((
            rest,
            Define {
                thread: Some(thread),
                nanos,
            },
        ))
    });
    match parsed {
        Some((rest, define)) => (rest, Some(define)),
        None => (plugin, None),
    }
}

#[cfg(test)]
mod tests;
