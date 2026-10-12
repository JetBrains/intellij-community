//! `bench classes`: the loaded classes of one arm by plugin and by module, at an anchor of the run. The totals come
//! from `summary.json`, and the class maps from `result.json` of each valid run of the arm. It reads only the files
//! of the session, so it answers on any host.
//!
//! A count is the median over the valid runs. A plugin or a module that a run does not name has 0 classes in that run.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::arm::Arm;
use super::digest::{number, table_line, write_group};
use super::files;
use super::options::ClassesArgs;
use super::record::{
    CLASS_ANCHORS, CLASSES_HIDDEN, CLASSES_JAR, CLASSES_JDK, CLASSES_NAMED, CLASSES_PLATFORM, CLASSES_PLUGINS, RunRecord, named_at,
};
use super::session::{self, RESULT_FILE};
use super::summary::{ArmSummary, Stat, Summary};

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

/// Reads the arm that `args` selects and its valid runs.
pub(crate) fn report(session: &Path, args: &ClassesArgs) -> Result<ClassReport, Refusal> {
    let summary = session::read_summary(session)?;
    let arm = select_arm(session, &summary, args.arm)?;
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
        None => anchors.last().map(|anchor| (*anchor).to_owned()),
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
    Ok(ClassReport {
        session: session.display().to_string(),
        arm: arm.arm,
        at,
        runs: runs.len(),
        totals,
        by_plugin,
        by_module: rows(&modules, top),
        missing,
        anchors,
        total_plugins: with_classes(&plugins),
        total_modules: with_classes(&modules),
        named: !args.plugins.is_empty(),
    })
}

/// The arm that `arm` names, else the first arm of [`Arm::ALL`] that is not modal and has a valid run, else the modal
/// arm. An arm that the session does not have, or without a valid run, is a usage refusal.
fn select_arm<'a>(session: &Path, summary: &'a Summary, arm: Option<Arm>) -> Result<&'a ArmSummary, Refusal> {
    let arms: Vec<&str> = summary.arms.values().map(|arm| arm.arm.label()).collect();
    let selected = match arm {
        Some(arm) => summary.arms.get(arm.key()).ok_or_else(|| {
            Refusal::usage(format!(
                "{} has no {} arm; its arms are {}",
                session.display(),
                arm.label(),
                arms.join(", ")
            ))
        })?,
        None => Arm::ALL
            .into_iter()
            .filter(|arm| *arm != Arm::Modal)
            .chain([Arm::Modal])
            .filter_map(|arm| summary.arms.get(arm.key()))
            .find(|arm| arm.valid_runs > 0)
            .ok_or_else(|| Refusal::usage(format!("no arm of {} has a valid measured run", session.display())))?,
    };
    if selected.valid_runs == 0 {
        return Err(Refusal::usage(format!(
            "the {} arm of {} has no valid measured run",
            selected.arm.label(),
            session.display()
        )));
    }
    Ok(selected)
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
        if !self.missing.is_empty() {
            let _ = writeln!(text, "no class {where_}: {}", self.missing.join(", "));
        }
        text.truncate(text.trim_end().len());
        text
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
