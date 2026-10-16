//! `bench classes`: the loaded classes of one arm by plugin and by module, at an anchor of the run. The totals come
//! from `summary.json`, and the class maps from `result.json` of each valid run of the arm. It reads only the files
//! of the session, so it answers on any host.
//!
//! A count is the median over the valid runs. A plugin or a module that a run does not name has 0 classes in that run.
//! The entries of `--entries` come from the class logs of the median run of the arm, which the record joins too.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::arm::Arm;
use super::classload;
use super::digest::{number, table_line, write_group};
use super::files;
use super::options::ClassesArgs;
use super::pluginlog;
use super::record::{
    CLASS_ANCHORS, CLASSES_HIDDEN, CLASSES_JAR, CLASSES_JDK, CLASSES_NAMED, CLASSES_PLATFORM, CLASSES_PLUGINS, PLUGIN_CLASSES_FILE,
    QUIT_ANCHOR, RunRecord, named_at,
};
use super::session::{self, CLASS_LOAD_LOG, RESULT_FILE};
use super::stats;
use super::summary::{ArmSummary, Stat};
use super::timeline::Timeline;
use super::trace::{as_f64, tenth};

/// The totals of the report before the row of the anchor, in order.
const TOTALS: [&str; 6] = [
    CLASSES_NAMED,
    CLASSES_HIDDEN,
    CLASSES_JDK,
    CLASSES_JAR,
    CLASSES_PLATFORM,
    CLASSES_PLUGINS,
];

/// The answer of `bench classes`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ClassReport {
    pub(crate) session: String,
    pub(crate) arm: Arm,
    /// The anchor of the plugin table and of the last total. An arm without an anchor has none, and its plugin table
    /// covers the whole run.
    pub(crate) at: Option<String>,
    /// The valid runs of the arm.
    pub(crate) runs: usize,
    /// The statistics of the class metrics over the valid runs, by metric. A metric that no run has is left out.
    pub(crate) totals: BTreeMap<String, Stat>,
    /// The plugins by the median of their classes before `at`, the most first. See [`ClassesArgs::plugins`].
    pub(crate) by_plugin: Vec<ClassRow>,
    /// The content modules by the median of their classes over the whole run, the most first, at most `--top`.
    pub(crate) by_module: Vec<ClassRow>,
    /// The plugins that `--plugin` names and that have no class before `at`.
    pub(crate) missing: Vec<String>,
    /// The first classes of each plugin of `by_plugin` before `at`, with `--entries`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) entries: Option<ClassEntries>,
    /// The anchors that the arm has, in the order of [`CLASS_ANCHORS`].
    #[serde(skip)]
    pub(crate) anchors: Vec<&'static str>,
    /// The plugins and the modules with a class, before the cut to `--top`.
    #[serde(skip)]
    pub(crate) total_plugins: usize,
    #[serde(skip)]
    pub(crate) total_modules: usize,
    /// The plugin table holds the plugins of `--plugin`.
    #[serde(skip)]
    pub(crate) named: bool,
}

/// One row of a table: a plugin id or a module name, and the median of its classes.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct ClassRow {
    pub(crate) name: String,
    pub(crate) classes: f64,
}

/// The first classes of each plugin of the plugin table, from the class logs of one run.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ClassEntries {
    /// The run directory: the median run of the arm.
    pub(crate) run: String,
    /// The classes of each plugin at most: `--entries`.
    pub(crate) limit: usize,
    /// One per row of the plugin table, in its order.
    pub(crate) plugins: Vec<PluginEntries>,
}

/// The first classes of one plugin before the anchor, in load order.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct PluginEntries {
    pub(crate) plugin: String,
    pub(crate) classes: Vec<ClassEntry>,
}

/// One class of a plugin and the time that the JVM loaded it.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct ClassEntry {
    pub(crate) name: String,
    /// Milliseconds from the process start, to a tenth: the uptime of the class-load log minus `jvm.loadingTime`.
    pub(crate) ms: f64,
}

/// Reads the arm that `args` selects and its valid runs.
pub(crate) fn report(session: &Path, args: &ClassesArgs) -> Result<ClassReport, Refusal> {
    let summary = session::read_summary(session)?;
    let arm = session::select_arm(session, &summary, args.arm)?;
    let anchors: Vec<&'static str> = CLASS_ANCHORS
        .into_iter()
        .filter(|anchor| arm.summary.contains_key(&named_at(anchor)))
        .collect();
    let at = match args.at.as_deref() {
        Some(at) if anchors.contains(&at) => Some(at.to_owned()),
        Some(at) => {
            let have = if anchors.is_empty() {
                "it has no anchor: a run needs class-load.log and startup-stats.json for one".to_owned()
            } else {
                format!("its anchors are {}", anchors.join(", "))
            };
            return Err(Refusal::usage(format!(
                "the {} arm of {} has no class count at {at}; {have}",
                arm.arm.label(),
                session.display()
            )));
        }
        None => default_anchor(arm.arm, &anchors),
    };
    let runs = arm
        .runs
        .iter()
        .filter(|run| run.valid)
        .map(|run| read_run(&session.join(&run.dir)))
        .collect::<Result<Vec<RunRecord>, Refusal>>()?;
    let plugin_maps: Vec<&BTreeMap<String, u64>> = runs
        .iter()
        .filter_map(|run| match &at {
            Some(at) => run.classes_by_plugin_at.get(at),
            None => Some(&run.classes_by_plugin),
        })
        .collect();
    let plugins = medians(&plugin_maps);
    let modules = medians(&runs.iter().map(|run| &run.classes_by_module).collect::<Vec<_>>());
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let (by_plugin, missing) = if args.plugins.is_empty() {
        (rows(&plugins, top), Vec::new())
    } else {
        named_rows(&plugins, &args.plugins)
    };
    let mut totals: BTreeMap<String, Stat> = TOTALS
        .iter()
        .filter_map(|metric| arm.summary.get(*metric).map(|stat| ((*metric).to_owned(), *stat)))
        .collect();
    if let Some(at) = &at {
        let metric = named_at(at);
        if let Some(stat) = arm.summary.get(&metric) {
            totals.insert(metric, *stat);
        }
    }
    let entries = match args.entries {
        Some(limit) => Some(entries(
            session,
            arm,
            at.as_deref(),
            &by_plugin,
            usize::try_from(limit).unwrap_or(usize::MAX),
        )?),
        None => None,
    };
    Ok(ClassReport {
        session: session.display().to_string(),
        arm: arm.arm,
        at,
        runs: runs.len(),
        totals,
        by_plugin,
        by_module: rows(&modules, top),
        missing,
        entries,
        anchors,
        total_plugins: with_classes(&plugins),
        total_modules: with_classes(&modules),
        named: !args.plugins.is_empty(),
    })
}

/// The anchor of the plugin table without `--at`: the main anchor of the arm, see [`Arm::main_anchor`], else the last
/// anchor that the arm has. An arm without an anchor has none.
fn default_anchor(arm: Arm, anchors: &[&'static str]) -> Option<String> {
    let main = arm.main_anchor();
    anchors
        .iter()
        .find(|anchor| **anchor == main)
        .or_else(|| anchors.last())
        .map(|anchor| (*anchor).to_owned())
}

/// The first `limit` classes of each plugin of `rows` that the median run of `arm` loaded before the anchor `at`, or
/// over the whole run without one. The run needs its class-load log, its plugin log and the `jvm.loadingTime` of its
/// report, which turns the JVM clock of the log into the process clock. A run without one is a usage refusal.
fn entries(session: &Path, arm: &ArmSummary, at: Option<&str>, rows: &[ClassRow], limit: usize) -> Result<ClassEntries, Refusal> {
    let run = arm.median_run().ok_or_else(|| {
        Refusal::usage(format!(
            "the {} arm of {} has no median run, so --entries has no run to read",
            arm.arm.label(),
            session.display()
        ))
    })?;
    let dir = session.join(&run.dir);
    let read = |name: &str| -> Result<String, Refusal> {
        files::read_optional(&dir.join(name))
            .map_err(|error| Refusal::usage(format!("{error:#}")))?
            .ok_or_else(|| Refusal::usage(format!("--entries reads {name} of the median run {}, and it has none", run.dir)))
    };
    let refuse = |name: &'static str| move |error: anyhow::Error| Refusal::usage(format!("{name} of {}: {error:#}", run.dir));
    let log = classload::parse(&read(CLASS_LOAD_LOG)?).map_err(refuse(CLASS_LOAD_LOG))?;
    let owners = pluginlog::parse(&read(PLUGIN_CLASSES_FILE)?).map_err(refuse(PLUGIN_CLASSES_FILE))?;
    let offset_ms = stats::parse(&read("startup-stats.json")?)
        .map_err(refuse("startup-stats.json"))?
        .jvm_loading_ms()
        .map(as_f64)
        .ok_or_else(|| {
            Refusal::usage(format!(
                "--entries needs jvm.loadingTime of startup-stats.json of {}, and it has none",
                run.dir
            ))
        })?;
    let anchor_ms = match at {
        None => None,
        Some(QUIT_ANCHOR) => Timeline::read(&dir).map_err(refuse("the trace"))?.quit_ms(),
        Some(anchor) => run.metrics.get(anchor).copied(),
    };
    if let (Some(at), None) = (at, anchor_ms) {
        return Err(Refusal::usage(format!(
            "the median run {} has no {at}, so --entries has no bound",
            run.dir
        )));
    }
    let bound = anchor_ms.map(|ms| ms + offset_ms);
    let plugins = rows
        .iter()
        .map(|row| PluginEntries {
            plugin: row.name.clone(),
            classes: classload::of_plugin(&log, &owners, &row.name, bound)
                .take(limit)
                .map(|class| ClassEntry {
                    name: class.name.clone(),
                    ms: tenth(class.uptime_ms - offset_ms),
                })
                .collect(),
        })
        .collect();
    Ok(ClassEntries {
        run: run.dir.clone(),
        limit,
        plugins,
    })
}

/// Reads `result.json` of a run directory. A file of another shape is a usage refusal that names it.
fn read_run(run_dir: &Path) -> Result<RunRecord, Refusal> {
    let path = run_dir.join(RESULT_FILE);
    files::read_text(&path)
        .and_then(|text| serde_json::from_str(&text).map_err(anyhow::Error::from))
        .map_err(|error| Refusal::usage(format!("{} is not a run result: {error:#}", path.display())))
}

/// The median count of each name over the maps. A map without the name counts 0.
fn medians(maps: &[&BTreeMap<String, u64>]) -> BTreeMap<String, f64> {
    let names: std::collections::BTreeSet<&String> = maps.iter().flat_map(|map| map.keys()).collect();
    names
        .into_iter()
        .filter_map(|name| {
            #[expect(clippy::cast_precision_loss, reason = "a class count stays far below 2^53")]
            let values: Vec<f64> = maps.iter().map(|map| map.get(name).copied().unwrap_or(0) as f64).collect();
            Stat::of(&values).map(|stat| (name.clone(), stat.median))
        })
        .collect()
}

/// The names with a median above 0.
fn with_classes(medians: &BTreeMap<String, f64>) -> usize {
    medians.values().filter(|classes| **classes > 0.0).count()
}

/// The `limit` rows with the most classes, the most first, then by name. A row of 0 classes is left out.
fn rows(medians: &BTreeMap<String, f64>, limit: usize) -> Vec<ClassRow> {
    let mut rows: Vec<ClassRow> = medians
        .iter()
        .filter(|(_, classes)| **classes > 0.0)
        .map(|(name, classes)| ClassRow {
            name: name.clone(),
            classes: *classes,
        })
        .collect();
    sort(&mut rows);
    rows.truncate(limit);
    rows
}

/// One row per plugin of `named`, in the order of [`rows`], and the names without a class.
fn named_rows(medians: &BTreeMap<String, f64>, named: &[String]) -> (Vec<ClassRow>, Vec<String>) {
    let mut rows: Vec<ClassRow> = Vec::new();
    for name in named {
        if !rows.iter().any(|row| row.name == *name) {
            rows.push(ClassRow {
                name: name.clone(),
                classes: medians.get(name).copied().unwrap_or(0.0),
            });
        }
    }
    sort(&mut rows);
    let missing = rows.iter().filter(|row| row.classes == 0.0).map(|row| row.name.clone()).collect();
    (rows, missing)
}

fn sort(rows: &mut [ClassRow]) {
    rows.sort_by(|left, right| right.classes.total_cmp(&left.classes).then_with(|| left.name.cmp(&right.name)));
}

impl ClassReport {
    /// The text: the arm, its anchors, the totals, the table by plugin and the table by module.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        let runs = if self.runs == 1 { "run" } else { "runs" };
        let _ = writeln!(
            text,
            "vm bench classes {} {} arm, {} valid {runs}",
            session::display_name(&self.session),
            self.arm.label(),
            self.runs
        );
        let where_ = self
            .at
            .as_deref()
            .map_or_else(|| "over the whole run".to_owned(), |at| format!("at {at}"));
        if self.anchors.is_empty() {
            let _ = writeln!(
                text,
                "anchors: none; a run needs class-load.log and startup-stats.json for one, so the plugin table is {where_}"
            );
        } else {
            let _ = writeln!(text, "anchors: {}; the plugin table is {where_}", self.anchors.join(", "));
        }
        let _ = writeln!(
            text,
            "{}",
            table_line("classes (median of the valid runs)", &["median", "min", "max"].map(str::to_owned))
        );
        let mut totals: Vec<String> = TOTALS.map(str::to_owned).to_vec();
        totals.extend(self.at.as_deref().map(named_at));
        totals.retain(|metric| self.totals.contains_key(metric));
        write_group(&mut text, "totals", &totals, |metric| {
            self.totals
                .get(metric)
                .map(|stat| vec![number(stat.median), number(stat.min), number(stat.max)])
                .unwrap_or_default()
        });
        if self.totals.is_empty() {
            let _ = writeln!(text, "no class count: the runs have no class-load.log");
        }
        let plugin_title = if self.named {
            format!("by plugin {where_}, the {} plugins of --plugin", self.by_plugin.len())
        } else {
            format!("by plugin {where_}, {} of {} plugins", self.by_plugin.len(), self.total_plugins)
        };
        table(&mut text, &plugin_title, &self.by_plugin);
        let module_title = format!(
            "by content module over the whole run, {} of {} modules",
            self.by_module.len(),
            self.total_modules
        );
        table(&mut text, &module_title, &self.by_module);
        if self.by_plugin.is_empty() && self.by_module.is_empty() {
            let _ = writeln!(text, "no class map: the runs have no plugin-classes.txt");
        }
        if let Some(entries) = &self.entries {
            entries.write(&mut text, &where_);
        }
        if !self.missing.is_empty() {
            let _ = writeln!(text, "no class {where_}: {}", self.missing.join(", "));
        }
        text.truncate(text.trim_end().len());
        text
    }
}

impl ClassEntries {
    /// The block of the text: one heading per plugin, then one line per class with its ms from the process start.
    fn write(&self, text: &mut String, where_: &str) {
        let first = if self.limit == 1 {
            "the first class".to_owned()
        } else {
            format!("the first {} classes", self.limit)
        };
        let _ = writeln!(
            text,
            "{first} of each plugin {where_}, in {} (ms from the process start):",
            self.run
        );
        for plugin in &self.plugins {
            if plugin.classes.is_empty() {
                let _ = writeln!(text, "  {}: none", plugin.plugin);
                continue;
            }
            let _ = writeln!(text, "  {}:", plugin.plugin);
            for class in &plugin.classes {
                let _ = writeln!(text, "  {:>7}  {}", number(class.ms), class.name);
            }
        }
    }
}

/// One table of [`ClassRow`]s under its title. A table without a row prints nothing.
fn table(text: &mut String, title: &str, rows: &[ClassRow]) {
    let names: Vec<String> = rows.iter().map(|row| row.name.clone()).collect();
    write_group(text, title, &names, |name| {
        rows.iter()
            .find(|row| row.name == name)
            .map(|row| vec![number(row.classes)])
            .unwrap_or_default()
    });
}

#[cfg(test)]
mod tests;
