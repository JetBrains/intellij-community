//! The reader of `class-load.log`, the JVM class-load log that `-Xlog:class+load,os+thread` writes with the
//! `uptime,tid,tags` decorators.
//!
//! A line of the class-load log is `[<uptime>][<tid>][class,load] <name> source: <source>`. The uptime has the unit
//! `s`, `ms` or `ns`. The tid is the thread that loaded the class. The JVM pads a decorator to a fixed width, such as
//! `[4355 ]`, and the reader trims it. A log of an older run has no tid, and further decorators such as `[info]` can
//! follow the uptime. The JVM also writes `opened: <path>` for the module image, which the reader skips.
//!
//! A line with the tags `os,thread` tells about a thread. The JVM names a Java thread only when the thread ends, so the
//! reader keeps those names, and a thread dump of the running IDE names the other Java threads, see [`Threads`].
//!
//! The uptime is on the JVM clock, which starts at the JVM start. The items, the trace and the timeline use the
//! process clock, which starts later: the first statement of `main` of [`MAIN_CLASS`] reads it. [`process_start`]
//! is the start of the process clock on the JVM clock. A caller adds it to a bound on the process clock before it asks
//! the log.

use std::collections::{BTreeMap, BTreeSet};

use anyhow::{Context, bail};

use super::pluginlog::{Owner, PluginLog, Tally, Thread};
use super::trace::as_f64;

/// The marker of a hidden class in the class name, such as `java.lang.invoke.LambdaForm$MH/0x0000000801001000`.
const HIDDEN_MARKER: &str = "/0x";

/// The tags of a thread line.
const THREAD_TAGS: &str = "os,thread";

/// The prefix of the name of the EDT, such as `AWT-EventQueue-0`.
const EDT_PREFIX: &str = "AWT-EventQueue";

/// The main class of the IDE. The first statement of its `main` starts the process clock, so the load of the class is
/// that start on the JVM clock, to the millisecond of the log.
pub(crate) const MAIN_CLASS: &str = "com.intellij.idea.Main";

/// The start of the process clock on the JVM clock of a class-load log.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct ProcessStart {
    /// Milliseconds on the JVM clock.
    pub(crate) uptime_ms: f64,
    pub(crate) anchor: ClockAnchor,
}

/// What gives the start of the process clock on the JVM clock.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum ClockAnchor {
    /// The load of [`MAIN_CLASS`] in the class-load log.
    MainClass,
    /// `jvm.loadingTime` of the report: the process clock minus the start time of `RuntimeMXBean`. The JVM clock of
    /// the log starts before that start time, so this anchor puts the process start 75 to 100 ms too early. Only a log
    /// without [`MAIN_CLASS`] uses it.
    LoadingTime,
}

impl ClockAnchor {
    /// The words of a report.
    pub(crate) const fn text(self) -> &'static str {
        match self {
            Self::MainClass => "the load of com.intellij.idea.Main",
            Self::LoadingTime => "jvm.loadingTime, about 100 ms too early",
        }
    }
}

/// The start of the process clock on the JVM clock of `log`: the load of [`MAIN_CLASS`], else `loading_ms`, the
/// `jvm.loadingTime` of the report. A log without the class and without a report has none.
pub(crate) fn process_start(log: &ClassLoadLog, loading_ms: Option<i64>) -> Option<ProcessStart> {
    let main = log.classes.iter().find(|class| class.name == MAIN_CLASS).map(|class| ProcessStart {
        uptime_ms: class.uptime_ms,
        anchor: ClockAnchor::MainClass,
    });
    main.or_else(|| {
        loading_ms.map(|loading_ms| ProcessStart {
            uptime_ms: as_f64(loading_ms),
            anchor: ClockAnchor::LoadingTime,
        })
    })
}

/// One class of the class-load log.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct LoadedClass {
    /// Milliseconds on the JVM clock.
    pub(crate) uptime_ms: f64,
    pub(crate) name: String,
    pub(crate) source: Source,
    /// The thread that loaded the class. A log without the `tid` decorator has none.
    pub(crate) tid: Option<u64>,
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

/// The classes of one class-load log, in file order, and the names of its threads.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct ClassLoadLog {
    pub(crate) classes: Vec<LoadedClass>,
    pub(crate) threads: Threads,
}

/// The names of the Java threads of a JVM by their tid. A name comes from a thread dump, else from the
/// `JavaThread exiting` line of a thread that ended. The `started` line of a thread names a native thread only: a Java
/// thread starts as `Unknown thread`, and a native thread never loads a class. So the reader keeps no `started` name.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Threads {
    named: BTreeMap<u64, String>,
    /// The tids of `named` with a name of the EDT.
    edt: BTreeSet<u64>,
}

impl Threads {
    /// Tells whether a thread with a name of the EDT is known. Without one, no class has a thread.
    pub(crate) fn knows_edt(&self) -> bool {
        !self.edt.is_empty()
    }

    /// The thread of a class that `tid` loaded: the EDT, or another thread when the EDT is known.
    pub(crate) fn thread(&self, tid: Option<u64>) -> Option<Thread> {
        let tid = tid?;
        if self.edt.contains(&tid) {
            Some(Thread::Edt)
        } else {
            self.knows_edt().then_some(Thread::Background)
        }
    }

    /// Adds the names of a `jcmd <pid> Thread.print` dump. A thread line is `"<name>" #<id> [<tid>] ... nid=<tid> ...`.
    /// A name of the dump replaces a name of the log, because the dump names a live Java thread. Another line is
    /// skipped.
    pub(crate) fn add_dump(&mut self, dump: &str) {
        for line in dump.lines() {
            let Some((name, rest)) = line.strip_prefix('"').and_then(|line| line.split_once('"')) else {
                continue;
            };
            let nid = rest.split_whitespace().find_map(|word| word.strip_prefix("nid=")).and_then(|nid| {
                nid.strip_prefix("0x")
                    .map_or_else(|| nid.parse().ok(), |hex| u64::from_str_radix(hex, 16).ok())
            });
            if let Some(tid) = nid {
                self.name_thread(tid, name, true);
            }
        }
    }

    /// Reads one `os,thread` message: the `exiting` line of a Java thread. A message of another shape is skipped.
    fn add_message(&mut self, message: &str) {
        if let Some(rest) = message.strip_prefix("JavaThread exiting (name: \"")
            && let Some((name, rest)) = rest.split_once('"')
            && let Some(tid) = field(rest, "tid: ")
        {
            self.name_thread(tid, name, false);
        }
    }

    /// Names the thread `tid`. With `replace`, the name replaces a name that the thread has.
    fn name_thread(&mut self, tid: u64, name: &str, replace: bool) {
        if !replace && self.named.contains_key(&tid) {
            return;
        }
        self.named.insert(tid, name.to_owned());
        if is_edt(name) {
            self.edt.insert(tid);
        } else {
            self.edt.remove(&tid);
        }
    }
}

/// Tells whether a thread name is the name of the EDT.
fn is_edt(name: &str) -> bool {
    name.starts_with(EDT_PREFIX)
}

/// The number after `key` in a list such as `name: "pool-1", tid: 12291`.
fn field(text: &str, key: &str) -> Option<u64> {
    let (_, after) = text.split_once(key)?;
    let digits = after.find(|character: char| !character.is_ascii_digit()).unwrap_or(after.len());
    after[..digits].parse().ok()
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

    /// The named classes that the EDT loaded before the bound `uptime_ms`. A log that does not know the EDT has none.
    pub(crate) fn edt_count_before(&self, uptime_ms: f64) -> Option<u64> {
        self.threads.knows_edt().then(|| {
            count(
                self.named()
                    .filter(|class| class.uptime_ms < uptime_ms && self.threads.thread(class.tid) == Some(Thread::Edt)),
            )
        })
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
    let mut counts = BTreeMap::new();
    for class in log.named_before(before_ms) {
        if let Some(owner) = owners.owner(&class.name) {
            *counts.entry(owner.plugin.clone()).or_default() += 1;
        }
    }
    counts
}

/// The named classes of `plugin` before the bound `before_ms` on the JVM clock, in the order of the log, which is the
/// load order. The plugin log gives the owner of a class.
pub(crate) fn of_plugin<'a>(
    log: &'a ClassLoadLog,
    owners: &'a PluginLog,
    plugin: &'a str,
    before_ms: Option<f64>,
) -> impl Iterator<Item = &'a LoadedClass> {
    log.named_before(before_ms)
        .filter(move |class| owners.owner(&class.name).is_some_and(|owner| owner.plugin == plugin))
}

/// The named classes of each loader before the bound `before_ms` on the JVM clock, with the defines that the plugin
/// log times. The thread of a define is the thread of the plugin log, else the thread of the JVM log. The count skips
/// a class without an owner.
pub(crate) fn by_loader(log: &ClassLoadLog, owners: &PluginLog, before_ms: Option<f64>) -> BTreeMap<Owner, Tally> {
    let mut tallies: BTreeMap<Owner, Tally> = BTreeMap::new();
    for class in log.named_before(before_ms) {
        if let Some(owned) = owners.classes.get(&class.name) {
            let define = owned.define.map(|define| define.on(log.threads.thread(class.tid)));
            tallies.entry(owned.owner.clone()).or_default().add(define);
        }
    }
    tallies
}

/// The uptime in milliseconds on the JVM clock of the first named class of each loader.
pub(crate) fn first_loads(log: &ClassLoadLog, owners: &PluginLog) -> BTreeMap<Owner, f64> {
    let mut first: BTreeMap<Owner, f64> = BTreeMap::new();
    for class in log.named() {
        if let Some(owner) = owners.owner(&class.name) {
            first
                .entry(owner.clone())
                .and_modify(|ms| *ms = ms.min(class.uptime_ms))
                .or_insert(class.uptime_ms);
        }
    }
    first
}

/// The classes of the core loader before the bound `before_ms` on the JVM clock: the named classes that a class loader
/// defines and that have no owner in the plugin log.
pub(crate) fn platform_count(log: &ClassLoadLog, owners: &PluginLog, before_ms: Option<f64>) -> u64 {
    count(log.named_before(before_ms).filter(|class| is_platform(class, owners)))
}

/// Tells whether a class is a class of the core loader: a class loader defined it, and the plugin log names no owner.
fn is_platform(class: &LoadedClass, owners: &PluginLog) -> bool {
    matches!(class.source, Source::Loader(_)) && !owners.classes.contains_key(&class.name)
}

/// Parses a class-load log. A line of another shape is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<ClassLoadLog> {
    let mut log = ClassLoadLog::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        match parse_line(line).with_context(|| format!("line {}: {line}", index + 1))? {
            Line::Class(class) => log.classes.push(class),
            Line::Thread(message) => log.threads.add_message(message),
            Line::Skipped => {}
        }
    }
    Ok(log)
}

/// One line of the log.
enum Line<'a> {
    Class(LoadedClass),
    /// The message of an `os,thread` line.
    Thread(&'a str),
    /// An `opened:` line.
    Skipped,
}

/// The class or the thread message of one line.
fn parse_line(line: &str) -> anyhow::Result<Line<'_>> {
    let Some((uptime, mut rest)) = line.strip_prefix('[').and_then(|line| line.split_once(']')) else {
        bail!("no `[<uptime>]` at the start");
    };
    // The JVM pads a decorator to a fixed width, such as `[9.997s ]`, `[4355 ]` or `[class,load      ]`.
    let uptime_ms = parse_uptime(uptime.trim_end())?;
    let mut tid = None;
    let mut tags = "";
    while let Some(decorated) = rest.strip_prefix('[') {
        let Some((decorator, after)) = decorated.split_once(']') else {
            bail!("no `]` after a decorator");
        };
        let decorator = decorator.trim();
        if !decorator.is_empty() && decorator.chars().all(|character| character.is_ascii_digit()) {
            tid = decorator.parse().ok();
        } else {
            tags = decorator;
        }
        rest = after;
    }
    let Some(message) = rest.strip_prefix(' ') else {
        bail!("no space after the decorators");
    };
    if tags == THREAD_TAGS {
        return Ok(Line::Thread(message.trim_end()));
    }
    if message.starts_with("opened: ") {
        return Ok(Line::Skipped);
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
    Ok(Line::Class(LoadedClass {
        uptime_ms,
        name: name.to_owned(),
        source,
        tid,
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
