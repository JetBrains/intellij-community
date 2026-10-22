//! `bench classes --span`: the class loads in the window of one span, by plugin, by module and by package. It reads
//! the class logs and the trace of each run, so it answers on any host.
//!
//! The window of a run is its first span of the name, from its start to its end on the process clock. The start of
//! the process clock on the JVM clock of the class-load log, see [`record::process_start_of`], turns the window into
//! a window of the log. A load counts when its uptime is at or after the start and before the end. The log has a
//! resolution of 1 ms.
//!
//! A load is a named class or a hidden class. A hidden class counts with its host: the class before `/0x` and `$$` in
//! its name. A plugin owns a class when `plugin-classes.txt` names it, and the findClass ms are the nanos that
//! the log writes for it. A class without a plugin counts by its package, cut to [`PACKAGE_DEPTH`] names.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write as _;
use std::path::Path;

use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::arm::Arm;
use super::classes::nanos_ms;
use super::classload::{self, ClassLoadLog, ClockAnchor, LoadedClass, Source};
use super::digest::{NAME_WIDTH, clip, number, table_line};
use super::files;
use super::options::{ClassesArgs, SpanThread};
use super::pluginlog::{self, Owner, PluginLog, Thread};
use super::record::{self, PLUGIN_CLASSES_FILE, THREAD_DUMP_FILE};
use super::session::{self, CLASS_LOAD_LOG, TRACE_FILE};
use super::summary::Stat;
use super::trace::{self, tenth};

/// The names of a package that a row keeps, such as `com.intellij.openapi.wm` of
/// `com.intellij.openapi.wm.impl.ToolWindowImpl`.
pub(crate) const PACKAGE_DEPTH: usize = 4;

/// The first classes of a bucket of the timeline.
const BUCKET_CLASSES: usize = 3;

/// The prefixes of a JDK class, for a hidden class whose host has no source in the log.
const JDK_PREFIXES: [&str; 5] = ["java.", "javax.", "jdk.", "sun.", "com.sun."];

/// The answer of `bench classes --span`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SpanClassReport {
    pub(crate) session: String,
    pub(crate) arm: Arm,
    pub(crate) span: String,
    pub(crate) thread: SpanThread,
    /// The window of each run with the span, in run order.
    pub(crate) runs: Vec<RunWindow>,
    /// The runs that have no span of the name.
    pub(crate) missing: Vec<String>,
    /// The medians of the windows of `runs`.
    pub(crate) median: WindowCounts,
    /// The plugins by the median of their loads, the most first, at most `--top`.
    pub(crate) by_plugin: Vec<PluginRow>,
    /// The modules by the median of their loads, the most first, at most `--top`.
    pub(crate) by_module: Vec<ModuleRow>,
    /// The packages of the classes without a plugin by the median of their loads, the most first, at most `--top`.
    pub(crate) by_package: Vec<PackageRow>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) timeline: Option<WindowTimeline>,
    /// The rows of each table before the cut to `--top`.
    #[serde(skip)]
    pub(crate) totals: [usize; 3],
}

/// The window of one run and its counts.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunWindow {
    /// The run directory.
    pub(crate) run: String,
    /// What gives the start of the process clock on the JVM clock of the run.
    pub(crate) clock: ClockAnchor,
    /// Milliseconds from the process start, to a tenth.
    pub(crate) start_ms: f64,
    #[serde(flatten)]
    pub(crate) counts: WindowCounts,
}

/// The counts of a window. A median of several windows holds the median of each count on its own.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct WindowCounts {
    /// The length of the window in ms, to a tenth.
    pub(crate) duration_ms: f64,
    /// The loads of the thread of `--thread`: the named classes and the hidden classes.
    pub(crate) loads: f64,
    pub(crate) hidden: f64,
    /// The findClass ms of the plugin classes among the loads, to a tenth.
    pub(crate) find_ms: f64,
    /// The loads of the other threads, with `--thread edt`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) other_loads: Option<f64>,
    /// The findClass ms of the plugin classes among the loads of the other threads, with `--thread edt`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) other_find_ms: Option<f64>,
}

/// One row of the plugin table: the medians of its loads, its hidden classes, its modules with a load and the
/// findClass ms of its classes.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PluginRow {
    pub(crate) plugin: String,
    pub(crate) loads: f64,
    pub(crate) hidden: f64,
    pub(crate) modules: f64,
    pub(crate) find_ms: f64,
}

/// One row of the module table: a main module or a content module, and the medians of its loads, its hidden classes
/// and the findClass ms of its classes.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModuleRow {
    pub(crate) plugin: String,
    /// The content module. The main module of a plugin has none.
    pub(crate) module: Option<String>,
    pub(crate) loads: f64,
    pub(crate) hidden: f64,
    pub(crate) find_ms: f64,
}

/// One row of the package table: a package of classes without a plugin, and the medians of its loads and its hidden
/// classes.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PackageRow {
    pub(crate) package: String,
    /// The package is a package of the JDK.
    pub(crate) jdk: bool,
    pub(crate) loads: f64,
    pub(crate) hidden: f64,
}

/// The timeline of the window of one run.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct WindowTimeline {
    pub(crate) run: String,
    pub(crate) bucket_ms: u32,
    /// One per bucket from the start of the window, also a bucket without a load.
    pub(crate) buckets: Vec<Bucket>,
}

/// The loads of one bucket of the timeline.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Bucket {
    /// Milliseconds from the start of the window.
    pub(crate) from_ms: u32,
    pub(crate) loads: u64,
    /// The first named classes of the bucket, at most [`BUCKET_CLASSES`].
    pub(crate) first: Vec<String>,
}

/// The owner of a load in one table.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
enum Key {
    Module(Owner),
    Package { package: String, jdk: bool },
}

/// The loads of one key in one run.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
struct Tally {
    loads: u64,
    hidden: u64,
    find_nanos: u64,
}

impl Tally {
    const fn add(&mut self, hidden: bool, find_nanos: u64) {
        self.loads += 1;
        if hidden {
            self.hidden += 1;
        }
        self.find_nanos += find_nanos;
    }
}

/// The files of one run that the report reads.
struct RunFiles {
    log: ClassLoadLog,
    owners: PluginLog,
    window: Option<(f64, f64)>,
    clock: ClockAnchor,
    offset_ms: f64,
}

/// One run with the span: its window and the tallies of its loads.
struct Measured {
    window: RunWindow,
    tallies: BTreeMap<Key, Tally>,
    timeline: Option<WindowTimeline>,
}

/// Reads the runs that `args` selects and counts the loads in the window of `--span`.
pub(crate) fn report(session: &Path, args: &ClassesArgs) -> Result<SpanClassReport, Refusal> {
    let Some(span) = args.span.as_deref() else {
        return Err(Refusal::usage("bench classes --span needs a span name"));
    };
    let summary = session::read_summary(session)?;
    let (arm, dirs, timeline_dir) = if let Some(run) = &args.run {
        let selected = session::select_run(session, &summary, Some(run), args.arm.as_ref())?;
        let dir = selected.id.dir_name();
        (selected.id.arm, vec![dir.clone()], Some(dir))
    } else {
        let arm = session::select_arm(session, &summary, args.arm.as_ref())?;
        let dirs: Vec<String> = arm.runs.iter().filter(|run| run.valid).map(|run| run.dir.clone()).collect();
        (arm.arm.clone(), dirs, arm.median_run().map(|run| run.dir.clone()))
    };
    let mut runs = Vec::new();
    let mut missing = Vec::new();
    let mut timeline = None;
    for dir in &dirs {
        let bucket_ms = args.timeline.filter(|_| timeline_dir.as_ref() == Some(dir));
        match measure(&session.join(dir), dir, span, args.thread, bucket_ms)? {
            Some(mut measured) => {
                timeline = timeline.or(measured.timeline.take());
                runs.push(measured);
            }
            None => missing.push(dir.clone()),
        }
    }
    let Some(first) = runs.first().map(|run| run.window.run.clone()) else {
        return Err(Refusal::usage(format!(
            "no run of the {} arm of {} has a {span} span in {TRACE_FILE}; `bench trace --overlap` lists the spans of a run",
            arm.label(),
            session.display()
        )));
    };
    if let (Some(bucket_ms), None) = (args.timeline, &timeline) {
        // The median run has no span, so the first run with one gives the timeline.
        timeline = measure(&session.join(&first), &first, span, args.thread, Some(bucket_ms))?.and_then(|run| run.timeline);
    }
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let tallies: Vec<&BTreeMap<Key, Tally>> = runs.iter().map(|run| &run.tallies).collect();
    let (by_module, by_package, modules, packages) = key_rows(&tallies, top);
    let (by_plugin, plugins) = plugin_rows(&tallies, top);
    let windows: Vec<RunWindow> = runs.into_iter().map(|run| run.window).collect();
    Ok(SpanClassReport {
        session: session.display().to_string(),
        arm,
        span: span.to_owned(),
        thread: args.thread,
        median: median_counts(&windows),
        runs: windows,
        missing,
        by_plugin,
        by_module,
        by_package,
        timeline,
        totals: [plugins, modules, packages],
    })
}

/// Reads one run and counts the loads in the window of its first span `span`. A run without the span has none. A run
/// without a file that the report needs is a usage refusal that names the file.
fn measure(run_dir: &Path, dir: &str, span: &str, thread: SpanThread, bucket_ms: Option<u32>) -> Result<Option<Measured>, Refusal> {
    let files = read_run(run_dir, dir, span)?;
    let Some((start_ms, end_ms)) = files.window else {
        return Ok(None);
    };
    let log = &files.log;
    if thread == SpanThread::Edt && !log.threads.knows_edt() {
        return Err(Refusal::usage(format!(
            "--thread edt needs the tid of the EDT, and {dir} has no {THREAD_DUMP_FILE} that names AWT-EventQueue; use --thread all"
        )));
    }
    let (low, high) = (start_ms + files.offset_ms, end_ms + files.offset_ms);
    let selected = |class: &LoadedClass| thread == SpanThread::All || log.threads.thread(class.tid) == Some(Thread::Edt);
    let mut tallies: BTreeMap<Key, Tally> = BTreeMap::new();
    let mut counts = WindowCounts {
        duration_ms: tenth(end_ms - start_ms),
        other_loads: (thread == SpanThread::Edt).then_some(0.0),
        other_find_ms: (thread == SpanThread::Edt).then_some(0.0),
        ..WindowCounts::default()
    };
    let mut find_nanos = 0;
    let mut other_nanos = 0;
    let in_window: Vec<&LoadedClass> = log
        .classes
        .iter()
        .filter(|class| class.uptime_ms >= low && class.uptime_ms < high)
        .collect();
    for class in &in_window {
        let (key, hidden, nanos) = classify(class, &files.owners);
        if selected(class) {
            tallies.entry(key).or_default().add(hidden, nanos);
            counts.loads += 1.0;
            if hidden {
                counts.hidden += 1.0;
            }
            find_nanos += nanos;
        } else {
            counts.other_loads = counts.other_loads.map(|loads| loads + 1.0);
            other_nanos += nanos;
        }
    }
    counts.find_ms = tenth(nanos_ms(find_nanos));
    counts.other_find_ms = counts.other_find_ms.map(|_| tenth(nanos_ms(other_nanos)));
    let timeline = bucket_ms.map(|bucket_ms| {
        let selected: Vec<&LoadedClass> = in_window.iter().copied().filter(|class| selected(class)).collect();
        timeline(dir, &selected, low, high, bucket_ms)
    });
    Ok(Some(Measured {
        window: RunWindow {
            run: dir.to_owned(),
            clock: files.clock,
            start_ms: tenth(start_ms),
            counts,
        },
        tallies,
        timeline,
    }))
}

/// Reads the class logs, the thread dump and the trace of one run. The window is the first span `span` on the process
/// clock, and a trace without it gives none.
fn read_run(run_dir: &Path, dir: &str, span: &str) -> Result<RunFiles, Refusal> {
    let read = |name: &str| -> Result<String, Refusal> {
        files::read_optional(&run_dir.join(name))
            .map_err(|error| Refusal::usage(format!("{error:#}")))?
            .ok_or_else(|| Refusal::usage(format!("--span reads {name} of the run {dir}, and it has none")))
    };
    let refuse = |name: &'static str| move |error: anyhow::Error| Refusal::usage(format!("{name} of {dir}: {error:#}"));
    let trace = trace::parse(&read(TRACE_FILE)?).map_err(refuse(TRACE_FILE))?;
    let mut log = classload::parse(&read(CLASS_LOAD_LOG)?).map_err(refuse(CLASS_LOAD_LOG))?;
    if let Some(dump) = files::read_optional(&run_dir.join(THREAD_DUMP_FILE)).map_err(refuse(THREAD_DUMP_FILE))? {
        log.threads.add_dump(&dump);
    }
    let owners = pluginlog::parse(&read(PLUGIN_CLASSES_FILE)?).map_err(refuse(PLUGIN_CLASSES_FILE))?;
    let start = record::process_start_of(run_dir, &log)
        .map_err(refuse("startup-stats.json"))?
        .ok_or_else(|| {
            Refusal::usage(format!(
                "--span needs {} in {CLASS_LOAD_LOG} or jvm.loadingTime in startup-stats.json of {dir}, and it has neither",
                classload::MAIN_CLASS
            ))
        })?;
    let window = trace.origin_us().and_then(|origin| {
        let first = trace.first(span)?;
        Some((
            micros_ms(first.start_time - origin),
            micros_ms(first.start_time + first.duration - origin),
        ))
    });
    Ok(RunFiles {
        log,
        owners,
        window,
        clock: start.anchor,
        offset_ms: start.uptime_ms,
    })
}

/// Microseconds as milliseconds, without the rounding of [`trace::micros_to_ms`], so a bound keeps its microseconds.
fn micros_ms(micros: i64) -> f64 {
    trace::as_f64(micros) / 1000.0
}

/// The key of a load, whether it is hidden, and the findClass nanos of a plugin class.
fn classify(class: &LoadedClass, owners: &PluginLog) -> (Key, bool, u64) {
    let hidden = class.source == Source::Hidden;
    let name = if hidden { host(&class.name) } else { class.name.as_str() };
    if let Some(owned) = owners.classes.get(name) {
        let nanos = if hidden { 0 } else { owned.define.map_or(0, |define| define.nanos) };
        return (Key::Module(owned.owner.clone()), hidden, nanos);
    }
    let jdk = if hidden {
        JDK_PREFIXES.iter().any(|prefix| name.starts_with(prefix))
    } else {
        class.source == Source::Jdk
    };
    (
        Key::Package {
            package: package(name),
            jdk,
        },
        hidden,
        0,
    )
}

/// The class that defines a hidden class: the name before `/0x` and before a `$$` in it, such as `$$Lambda` or
/// `$$TypeSwitch`.
fn host(name: &str) -> &str {
    let name = name.split_once("/0x").map_or(name, |(host, _)| host);
    name.split_once("$$").map_or(name, |(host, _)| host)
}

/// The package of a class, cut to [`PACKAGE_DEPTH`] names. A class without a package has `(default)`.
fn package(class: &str) -> String {
    let Some((package, _)) = class.rsplit_once('.') else {
        return "(default)".to_owned();
    };
    package.split('.').take(PACKAGE_DEPTH).collect::<Vec<_>>().join(".")
}

/// The buckets of `bucket_ms` from `low` to `high` on the JVM clock, with the loads of `classes`.
fn timeline(run: &str, classes: &[&LoadedClass], low: f64, high: f64, bucket_ms: u32) -> WindowTimeline {
    let width = f64::from(bucket_ms);
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_sign_loss,
        reason = "a window of a span holds far fewer than 2^32 buckets, and the count is not negative"
    )]
    let count = ((high - low) / width).ceil().max(1.0) as u32;
    let mut buckets: Vec<Bucket> = (0..count)
        .map(|index| Bucket {
            from_ms: index * bucket_ms,
            loads: 0,
            first: Vec::new(),
        })
        .collect();
    for class in classes {
        #[expect(
            clippy::cast_possible_truncation,
            clippy::cast_sign_loss,
            reason = "a class of the window is at or after its start, and the index is below the count"
        )]
        let index = (((class.uptime_ms - low) / width) as usize).min(buckets.len() - 1);
        let bucket = &mut buckets[index];
        bucket.loads += 1;
        if class.source != Source::Hidden && bucket.first.len() < BUCKET_CLASSES {
            bucket.first.push(class.name.clone());
        }
    }
    WindowTimeline {
        run: run.to_owned(),
        bucket_ms,
        buckets,
    }
}

/// The median of `values`, 0 for none.
fn median(values: &[f64]) -> f64 {
    Stat::of(values).map_or(0.0, |stat| stat.median)
}

#[expect(clippy::cast_precision_loss, reason = "a class count stays far below 2^53")]
const fn count(value: u64) -> f64 {
    value as f64
}

/// The medians of the counts of `windows`.
fn median_counts(windows: &[RunWindow]) -> WindowCounts {
    let of = |field: fn(&WindowCounts) -> f64| median(&windows.iter().map(|window| field(&window.counts)).collect::<Vec<_>>());
    let optional = |field: fn(&WindowCounts) -> Option<f64>| {
        let values: Vec<f64> = windows.iter().filter_map(|window| field(&window.counts)).collect();
        Stat::of(&values).map(|stat| stat.median)
    };
    WindowCounts {
        duration_ms: of(|counts| counts.duration_ms),
        loads: of(|counts| counts.loads),
        hidden: of(|counts| counts.hidden),
        find_ms: tenth(of(|counts| counts.find_ms)),
        other_loads: optional(|counts| counts.other_loads),
        other_find_ms: optional(|counts| counts.other_find_ms).map(tenth),
    }
}

/// The median tally of each key over the runs: 0 for a run without the key.
fn median_tallies<K: Ord + Clone>(runs: &[&BTreeMap<K, Tally>]) -> Vec<(K, [f64; 3])> {
    let keys: BTreeSet<&K> = runs.iter().flat_map(|run| run.keys()).collect();
    keys.into_iter()
        .map(|key| {
            let of = |field: fn(&Tally) -> f64| median(&runs.iter().map(|run| run.get(key).map_or(0.0, field)).collect::<Vec<_>>());
            let medians = [
                of(|tally| count(tally.loads)),
                of(|tally| count(tally.hidden)),
                tenth(of(|tally| nanos_ms(tally.find_nanos))),
            ];
            (key.clone(), medians)
        })
        .collect()
}

/// The rows with a load, the most loads first, then by `name`, at most `limit`, and the rows before the cut.
fn ranked<T>(mut rows: Vec<T>, loads: impl Fn(&T) -> f64, name: impl Fn(&T) -> String, limit: usize) -> (Vec<T>, usize) {
    rows.retain(|row| loads(row) > 0.0);
    rows.sort_by(|left, right| loads(right).total_cmp(&loads(left)).then_with(|| name(left).cmp(&name(right))));
    let total = rows.len();
    rows.truncate(limit);
    (rows, total)
}

/// The module rows and the package rows of the tallies, and their counts before the cut.
fn key_rows(runs: &[&BTreeMap<Key, Tally>], limit: usize) -> (Vec<ModuleRow>, Vec<PackageRow>, usize, usize) {
    let mut modules = Vec::new();
    let mut packages = Vec::new();
    for (key, [loads, hidden, find_ms]) in median_tallies(runs) {
        match key {
            Key::Module(owner) => modules.push(ModuleRow {
                plugin: owner.plugin,
                module: owner.module,
                loads,
                hidden,
                find_ms,
            }),
            Key::Package { package, jdk } => packages.push(PackageRow {
                package,
                jdk,
                loads,
                hidden,
            }),
        }
    }
    let (modules, module_count) = ranked(
        modules,
        |row| row.loads,
        |row| Owner::loader_label(&row.plugin, row.module.as_deref()),
        limit,
    );
    let (packages, package_count) = ranked(packages, |row| row.loads, |row| row.package.clone(), limit);
    (modules, packages, module_count, package_count)
}

/// The plugin rows of the tallies, and their count before the cut. A plugin of a run sums its modules.
fn plugin_rows(runs: &[&BTreeMap<Key, Tally>], limit: usize) -> (Vec<PluginRow>, usize) {
    let by_plugin: Vec<BTreeMap<String, (Tally, u64)>> = runs
        .iter()
        .map(|run| {
            let mut plugins: BTreeMap<String, (Tally, u64)> = BTreeMap::new();
            for (key, tally) in *run {
                if let Key::Module(owner) = key {
                    let (sum, modules) = plugins.entry(owner.plugin.clone()).or_default();
                    sum.loads += tally.loads;
                    sum.hidden += tally.hidden;
                    sum.find_nanos += tally.find_nanos;
                    *modules += 1;
                }
            }
            plugins
        })
        .collect();
    let tallies: Vec<BTreeMap<String, Tally>> = by_plugin
        .iter()
        .map(|run| run.iter().map(|(plugin, (tally, _))| (plugin.clone(), *tally)).collect())
        .collect();
    let tallies: Vec<&BTreeMap<String, Tally>> = tallies.iter().collect();
    let rows = median_tallies(&tallies)
        .into_iter()
        .map(|(plugin, [loads, hidden, find_ms])| {
            let modules = median(
                &by_plugin
                    .iter()
                    .map(|run| run.get(&plugin).map_or(0.0, |(_, modules)| count(*modules)))
                    .collect::<Vec<_>>(),
            );
            PluginRow {
                plugin,
                loads,
                hidden,
                modules,
                find_ms,
            }
        })
        .collect();
    ranked(rows, |row| row.loads, |row| row.plugin.clone(), limit)
}

impl SpanClassReport {
    /// The text: the runs with their windows, the medians, the three tables and the timeline.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        let thread = match self.thread {
            SpanThread::Edt => "the EDT",
            SpanThread::All => "every thread",
        };
        let runs = if self.runs.len() == 1 { "run" } else { "runs" };
        let _ = writeln!(
            text,
            "vm bench classes {} {} arm, the loads of {thread} in the window of {}, {} {runs} with the span",
            session::display_name(&self.session),
            self.arm.label(),
            self.span,
            self.runs.len()
        );
        let anchors: BTreeSet<ClockAnchor> = self.runs.iter().map(|run| run.clock).collect();
        let clock: Vec<&str> = anchors.iter().map(|anchor| anchor.text()).collect();
        let _ = writeln!(
            text,
            "the process clock starts at {}; the class-load log has a resolution of 1 ms",
            clock.join(" or ")
        );
        let other = self.thread == SpanThread::Edt;
        if other {
            let _ = writeln!(
                text,
                "BG: the loads of the other threads; find ms: the findClass ms of the plugin classes"
            );
        } else {
            let _ = writeln!(text, "find ms: the findClass ms of the plugin classes");
        }
        let mut columns = vec!["start", "ms", "loads", "hidden", "find ms"];
        if other {
            columns.extend(["BG", "BG ms"]);
        }
        let columns: Vec<String> = columns.into_iter().map(str::to_owned).collect();
        let _ = writeln!(text, "{}", table_line("window (ms from the process start)", &columns));
        for run in &self.runs {
            let mut cells = vec![number(run.start_ms)];
            cells.extend(counts_cells(&run.counts, other));
            let _ = writeln!(text, "{}", table_line(&format!("  {}", run.run), &cells));
        }
        if self.runs.len() > 1 {
            let mut cells = vec![String::new()];
            cells.extend(counts_cells(&self.median, other));
            let _ = writeln!(text, "{}", table_line("  median", &cells));
        }
        for run in &self.missing {
            let _ = writeln!(text, "  {run}: no {} span", self.span);
        }
        let [plugins, modules, packages] = self.totals;
        if !self.by_plugin.is_empty() {
            let _ = writeln!(text, "by plugin, {} of {plugins} plugins:", self.by_plugin.len());
            let _ = writeln!(
                text,
                "{}",
                table_line("", &["loads", "hidden", "modules", "find ms"].map(str::to_owned))
            );
            for row in &self.by_plugin {
                let cells = [row.loads, row.hidden, row.modules, row.find_ms].map(number);
                let _ = writeln!(text, "{}", table_line(&format!("  {}", clip(&row.plugin, NAME_WIDTH - 2)), &cells));
            }
        }
        if !self.by_module.is_empty() {
            let _ = writeln!(text, "by module, {} of {modules} modules:", self.by_module.len());
            let _ = writeln!(
                text,
                "{}  plugin",
                table_line("", &["loads", "hidden", "find ms"].map(str::to_owned))
            );
            for row in &self.by_module {
                let name = Owner::loader_name(&row.plugin, row.module.as_deref());
                let cells = [row.loads, row.hidden, row.find_ms].map(number);
                let line = table_line(&format!("  {}", clip(&name, NAME_WIDTH - 2)), &cells);
                let _ = writeln!(text, "{line}  {}", row.plugin);
            }
        }
        if !self.by_package.is_empty() {
            let _ = writeln!(
                text,
                "by package of the classes without a plugin, {} of {packages} packages:",
                self.by_package.len()
            );
            let _ = writeln!(text, "{}", table_line("", &["loads", "hidden"].map(str::to_owned)));
            for row in &self.by_package {
                let name = if row.jdk {
                    format!("{} (JDK)", row.package)
                } else {
                    row.package.clone()
                };
                let cells = [row.loads, row.hidden].map(number);
                let _ = writeln!(text, "{}", table_line(&format!("  {}", clip(&name, NAME_WIDTH - 2)), &cells));
            }
        }
        if let Some(timeline) = &self.timeline {
            timeline.write(&mut text);
        }
        text.truncate(text.trim_end().len());
        text
    }
}

impl WindowTimeline {
    /// The block of the text: one line per bucket with its loads and the short names of its first classes.
    fn write(&self, text: &mut String) {
        let _ = writeln!(
            text,
            "timeline of {}, {} ms buckets from the span start: loads, the first classes",
            self.run, self.bucket_ms
        );
        for bucket in &self.buckets {
            let names: Vec<&str> = bucket
                .first
                .iter()
                .map(|name| name.rsplit_once('.').map_or(name.as_str(), |(_, short)| short))
                .collect();
            let line = format!("  +{:>5} ms {:>5}  {}", bucket.from_ms, bucket.loads, names.join(", "));
            let _ = writeln!(text, "{}", line.trim_end());
        }
    }
}

/// The cells of the counts of a window after its start: the length, the loads, the hidden classes, the findClass ms,
/// and with `other` the loads and the findClass ms of the other threads.
fn counts_cells(counts: &WindowCounts, other: bool) -> Vec<String> {
    let mut cells = vec![
        number(counts.duration_ms),
        number(counts.loads),
        number(counts.hidden),
        number(counts.find_ms),
    ];
    if other {
        cells.push(counts.other_loads.map_or_else(|| "-".to_owned(), number));
        cells.push(counts.other_find_ms.map_or_else(|| "-".to_owned(), number));
    }
    cells
}

#[cfg(test)]
mod tests;
