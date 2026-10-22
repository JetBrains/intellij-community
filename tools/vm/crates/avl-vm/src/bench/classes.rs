//! `bench classes`: the loaded classes of one arm by plugin and by module, at an anchor of the run. The totals come
//! from `summary.json`, and the class maps from `result.json` of each valid run of the arm. It reads only the files
//! of the session, so it answers on any host.
//!
//! A count or a time is the median over the valid runs. A plugin or a module that a run does not name has 0 classes in
//! that run. A module is a loader: the main module or a content module of a plugin. The EDT and BG times are the
//! `findClass` nanos that `plugin-classes.txt` writes per class, and a log of the old shape has none. The entries of
//! `--entries` come from the class logs of the median run of the arm, which the record joins too.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::arm::Arm;
use super::classload;
use super::digest::{NAME_WIDTH, clip, number, table_line, write_group};
use super::files;
use super::options::ClassesArgs;
use super::pluginlog::{self, Owner};
use super::record::{
    CLASS_ANCHORS, CLASSES_HIDDEN, CLASSES_JAR, CLASSES_JDK, CLASSES_NAMED, CLASSES_PLATFORM, CLASSES_PLUGINS, LoaderClasses,
    PLUGIN_CLASSES_FILE, QUIT_ANCHOR, RunRecord, named_at,
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
    /// The anchor of the plugin table, of the module table and of the last total. An arm without an anchor has none,
    /// and its tables cover the whole run.
    pub(crate) at: Option<String>,
    /// The valid runs of the arm.
    pub(crate) runs: usize,
    /// The statistics of the class metrics over the valid runs, by metric. A metric that no run has is left out.
    pub(crate) totals: BTreeMap<String, Stat>,
    /// The plugins by the median of their classes before `at`, the most first. See [`ClassesArgs::plugins`].
    pub(crate) by_plugin: Vec<ClassRow>,
    /// The rank of each plugin of `--plugin` with a class before `at`, in the order of `by_plugin`.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) ranks: Vec<PluginRank>,
    /// The modules by the median of their classes before `at`, the most first, at most `--top`. With `--plugin`, the
    /// modules of the named plugins only.
    pub(crate) by_module: Vec<ModuleRow>,
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
    /// A run of the arm has the EDT and BG times of its classes.
    #[serde(skip)]
    pub(crate) timed: bool,
}

/// One row of the plugin table: a plugin id, and the medians of its classes, its modules with a class, and the EDT
/// and BG milliseconds of its classes, to a tenth. A run without the module maps gives no modules and no times.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ClassRow {
    pub(crate) name: String,
    pub(crate) classes: f64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) loaders: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) edt_ms: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) bg_ms: Option<f64>,
}

/// The place of a plugin among all plugins with a class before the anchor.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PluginRank {
    pub(crate) plugin: String,
    /// From 1: one more than the plugins with more classes.
    pub(crate) rank: usize,
    /// The plugins with a class before the anchor.
    pub(crate) of: usize,
    /// The part of the classes of all plugins, in percent to a tenth.
    pub(crate) percent: f64,
}

/// One row of the module table: a loader, and the medians of its classes and of their EDT and BG milliseconds, to a
/// tenth.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModuleRow {
    pub(crate) plugin: String,
    /// The content module. The main module of the plugin has none.
    pub(crate) module: Option<String>,
    pub(crate) classes: f64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) edt_ms: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) bg_ms: Option<f64>,
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
    let arm = session::select_arm(session, &summary, args.arm.as_ref())?;
    let (anchors, at) = anchor_of(session, &arm.arm, args.at.as_deref(), |anchor| {
        arm.summary.contains_key(&named_at(anchor))
    })?;
    let runs = arm
        .runs
        .iter()
        .filter(|run| run.valid)
        .map(|run| read_run(&session.join(&run.dir)))
        .collect::<Result<Vec<RunRecord>, Refusal>>()?;
    let plugin_maps: Vec<&BTreeMap<String, u64>> = runs
        .iter()
        .filter_map(|run| match &at {
            Some(at) => run.class_maps.classes_by_plugin_at.get(at),
            None => Some(&run.class_maps.classes_by_plugin),
        })
        .collect();
    let plugins = medians(&plugin_maps);
    let cuts: Vec<&[LoaderClasses]> = runs
        .iter()
        .map(|run| {
            let maps = &run.class_maps;
            match &at {
                Some(at) => maps.classes_by_loader_at.get(at).map_or(&[][..], Vec::as_slice),
                None => &maps.classes_by_loader,
            }
        })
        .collect();
    let loader_cuts: Vec<RunCuts<(String, Option<String>)>> = cuts
        .iter()
        .map(|cut| RunCuts::of(cut, |loader| (loader.plugin.clone(), loader.module.clone())))
        .collect();
    let plugin_cuts: Vec<RunCuts<String>> = cuts.iter().map(|cut| RunCuts::of(cut, |loader| loader.plugin.clone())).collect();
    let module_medians = median_cuts(&loader_cuts);
    let plugin_medians = median_cuts(&plugin_cuts);
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let (mut by_plugin, missing) = if args.plugins.is_empty() {
        (rows(&plugins, top), Vec::new())
    } else {
        named_rows(&plugins, &args.plugins)
    };
    let mapped = cuts.iter().any(|cut| !cut.is_empty());
    let timed = loader_cuts.iter().any(|cut| cut.timed);
    for row in &mut by_plugin {
        let none = Cut {
            edt_ms: timed.then_some(0.0),
            bg_ms: timed.then_some(0.0),
            ..Cut::default()
        };
        let cut = plugin_medians.get(&row.name).copied().or(mapped.then_some(none));
        if let Some(cut) = cut {
            row.loaders = Some(cut.loaders);
            row.edt_ms = cut.edt_ms.map(tenth);
            row.bg_ms = cut.bg_ms.map(tenth);
        }
    }
    let ranks = if args.plugins.is_empty() {
        Vec::new()
    } else {
        ranks(&plugins, &by_plugin)
    };
    let by_module = module_rows(&module_medians, &args.plugins, top);
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
        arm: arm.arm.clone(),
        at,
        runs: runs.len(),
        totals,
        by_plugin,
        ranks,
        by_module,
        missing,
        entries,
        anchors,
        total_plugins: with_classes(&plugins),
        total_modules: module_medians.values().filter(|cut| cut.classes > 0.0).count(),
        named: !args.plugins.is_empty(),
        timed,
    })
}

/// The anchors of `arm` that `has` accepts, in the order of [`CLASS_ANCHORS`], and the anchor of a report: `at`, else
/// the default of [`default_anchor`]. An `at` that the arm does not have is a usage refusal.
pub(crate) fn anchor_of(
    session: &Path,
    arm: &Arm,
    at: Option<&str>,
    has: impl Fn(&str) -> bool,
) -> Result<(Vec<&'static str>, Option<String>), Refusal> {
    let anchors: Vec<&'static str> = CLASS_ANCHORS.into_iter().filter(|anchor| has(anchor)).collect();
    let at = match at {
        Some(at) if anchors.contains(&at) => Some(at.to_owned()),
        Some(at) => {
            let have = if anchors.is_empty() {
                "it has no anchor: a run needs class-load.log and startup-stats.json for one".to_owned()
            } else {
                format!("its anchors are {}", anchors.join(", "))
            };
            return Err(Refusal::usage(format!(
                "the {} arm of {} has no class count at {at}; {have}",
                arm.label(),
                session.display()
            )));
        }
        None => default_anchor(arm, &anchors),
    };
    Ok((anchors, at))
}

/// The cut of one run before an anchor, by a key of the loader: the classes, the loaders and the times of each key.
struct RunCuts<K> {
    cuts: BTreeMap<K, Cut>,
    /// A loader of the run has the times of its classes.
    timed: bool,
}

/// The classes, the loaders with a class, and the EDT and BG times of the classes of one key in one run. A time is
/// none when a loader of the key has no times.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
struct Cut {
    classes: f64,
    loaders: f64,
    edt_ms: Option<f64>,
    bg_ms: Option<f64>,
}

impl<K: Ord> RunCuts<K> {
    fn of(loaders: &[LoaderClasses], key: impl Fn(&LoaderClasses) -> K) -> Self {
        let mut cuts: BTreeMap<K, Cut> = BTreeMap::new();
        for loader in loaders.iter().filter(|loader| loader.classes > 0) {
            let cut = cuts.entry(key(loader)).or_insert(Cut {
                edt_ms: Some(0.0),
                bg_ms: Some(0.0),
                ..Cut::default()
            });
            cut.classes += count(loader.classes);
            cut.loaders += 1.0;
            let times = loader.times;
            cut.edt_ms = cut.edt_ms.zip(times).map(|(ms, times)| ms + nanos_ms(times.edt_nanos));
            cut.bg_ms = cut.bg_ms.zip(times).map(|(ms, times)| ms + nanos_ms(times.bg_nanos));
        }
        let timed = loaders.iter().any(|loader| loader.times.is_some());
        Self { cuts, timed }
    }
}

/// The median cut of each key over the runs. A run without the key counts 0 classes and 0 loaders, and 0 ms when it
/// has times. A time is the median over the runs with a time, and none when no run has one.
fn median_cuts<K: Ord + Clone>(runs: &[RunCuts<K>]) -> BTreeMap<K, Cut> {
    let keys: std::collections::BTreeSet<&K> = runs.iter().flat_map(|run| run.cuts.keys()).collect();
    let median = |values: Vec<f64>| Stat::of(&values).map(|stat| stat.median);
    keys.into_iter()
        .map(|key| {
            let of = |field: fn(&Cut) -> Option<f64>| {
                median(
                    runs.iter()
                        .filter_map(|run| match run.cuts.get(key) {
                            Some(cut) => field(cut),
                            None => run.timed.then_some(0.0),
                        })
                        .collect(),
                )
            };
            let count_of =
                |field: fn(&Cut) -> f64| median(runs.iter().map(|run| run.cuts.get(key).map_or(0.0, field)).collect()).unwrap_or(0.0);
            let cut = Cut {
                classes: count_of(|cut| cut.classes),
                loaders: count_of(|cut| cut.loaders),
                edt_ms: of(|cut| cut.edt_ms),
                bg_ms: of(|cut| cut.bg_ms),
            };
            (key.clone(), cut)
        })
        .collect()
}

/// Milliseconds of a sum of nanos.
pub(crate) fn nanos_ms(nanos: u64) -> f64 {
    #[expect(clippy::cast_precision_loss, reason = "a sum of define nanos stays far below 2^53")]
    let nanos = nanos as f64;
    nanos / 1e6
}

#[expect(clippy::cast_precision_loss, reason = "a class count stays far below 2^53")]
const fn count(value: u64) -> f64 {
    value as f64
}

/// The rank of each row of `named` with a class among all plugins of `medians`, and its part of their classes.
fn ranks(medians: &BTreeMap<String, f64>, named: &[ClassRow]) -> Vec<PluginRank> {
    let total: f64 = medians.values().sum();
    let of = with_classes(medians);
    named
        .iter()
        .filter(|row| row.classes > 0.0)
        .map(|row| PluginRank {
            plugin: row.name.clone(),
            rank: 1 + medians.values().filter(|classes| **classes > row.classes).count(),
            of,
            percent: tenth(row.classes * 100.0 / total),
        })
        .collect()
}

/// The `limit` modules with the most classes, the most first, then by plugin and module. A module of 0 classes is left
/// out, and with `plugins` a module of another plugin too.
fn module_rows(medians: &BTreeMap<(String, Option<String>), Cut>, plugins: &[String], limit: usize) -> Vec<ModuleRow> {
    let mut rows: Vec<ModuleRow> = medians
        .iter()
        .filter(|((plugin, _), cut)| cut.classes > 0.0 && (plugins.is_empty() || plugins.contains(plugin)))
        .map(|((plugin, module), cut)| ModuleRow {
            plugin: plugin.clone(),
            module: module.clone(),
            classes: cut.classes,
            edt_ms: cut.edt_ms.map(tenth),
            bg_ms: cut.bg_ms.map(tenth),
        })
        .collect();
    rows.sort_by(|left, right| {
        right
            .classes
            .total_cmp(&left.classes)
            .then_with(|| (&left.plugin, &left.module).cmp(&(&right.plugin, &right.module)))
    });
    rows.truncate(limit);
    rows
}

/// The anchor of the plugin table without `--at`: the main anchor of the arm, see [`Arm::main_anchor`], else the last
/// anchor that the arm has. An arm without an anchor has none.
fn default_anchor(arm: &Arm, anchors: &[&'static str]) -> Option<String> {
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
pub(crate) fn read_run(run_dir: &Path) -> Result<RunRecord, Refusal> {
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
        .map(|(name, classes)| ClassRow::new(name, *classes))
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
            rows.push(ClassRow::new(name, medians.get(name).copied().unwrap_or(0.0)));
        }
    }
    sort(&mut rows);
    let missing = rows.iter().filter(|row| row.classes == 0.0).map(|row| row.name.clone()).collect();
    (rows, missing)
}

impl ClassRow {
    /// A row of a plugin without its modules and its times.
    fn new(name: &str, classes: f64) -> Self {
        Self {
            name: name.to_owned(),
            classes,
            loaders: None,
            edt_ms: None,
            bg_ms: None,
        }
    }
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
                "anchors: none; a run needs class-load.log and startup-stats.json for one, so the tables are {where_}"
            );
        } else {
            let _ = writeln!(text, "anchors: {}; the tables are {where_}", self.anchors.join(", "));
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
        plugin_table(&mut text, &plugin_title, &self.by_plugin);
        for rank in &self.ranks {
            let _ = writeln!(
                text,
                "{}: rank {} of {} plugins, {:.1} % of the plugin classes {where_}",
                rank.plugin, rank.rank, rank.of, rank.percent
            );
        }
        let module_title = if self.named {
            format!("by module {where_}, {} modules of the plugins of --plugin", self.by_module.len())
        } else {
            format!("by module {where_}, {} of {} modules", self.by_module.len(), self.total_modules)
        };
        module_table(&mut text, &module_title, &self.by_module);
        if self.by_plugin.is_empty() && self.by_module.is_empty() {
            let _ = writeln!(text, "no class map: the runs have no plugin-classes.txt");
        } else if self.total_plugins > 0 && self.total_modules == 0 {
            let _ = writeln!(
                text,
                "no module map: result.json of the runs is from before the module maps; run `bench replay` to write it again"
            );
        } else if self.total_modules > 0 && !self.timed {
            let _ = writeln!(
                text,
                "note: no EDT ms and no BG ms: plugin-classes.txt of the runs has the old shape, without the thread and the time of a class"
            );
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

/// The plugin table under its title: a line with the column names, then one line per row. A table without a row
/// prints nothing.
fn plugin_table(text: &mut String, title: &str, rows: &[ClassRow]) {
    if rows.is_empty() {
        return;
    }
    let _ = writeln!(text, "{title}:");
    let _ = writeln!(
        text,
        "{}",
        table_line("", &["classes", "loaders", "EDT ms", "BG ms"].map(str::to_owned))
    );
    for row in rows {
        let cells = [Some(row.classes), row.loaders, row.edt_ms, row.bg_ms].map(cell);
        let _ = writeln!(text, "{}", table_line(&format!("  {}", clip(&row.name, NAME_WIDTH - 2)), &cells));
    }
}

/// The module table under its title: a line with the column names, then one line per module with its plugin after the
/// numbers. A main module is `<plugin id> (main)`. A table without a row prints nothing.
fn module_table(text: &mut String, title: &str, rows: &[ModuleRow]) {
    if rows.is_empty() {
        return;
    }
    let _ = writeln!(text, "{title}:");
    let _ = writeln!(
        text,
        "{}  plugin",
        table_line("", &["classes", "EDT ms", "BG ms"].map(str::to_owned))
    );
    for row in rows {
        let name = Owner::loader_name(&row.plugin, row.module.as_deref());
        let cells = [Some(row.classes), row.edt_ms, row.bg_ms].map(cell);
        let _ = writeln!(
            text,
            "{}  {}",
            table_line(&format!("  {}", clip(&name, NAME_WIDTH - 2)), &cells),
            row.plugin
        );
    }
}

/// A cell of a median that the runs may lack: the number, else `-`.
fn cell(value: Option<f64>) -> String {
    value.map_or_else(|| "-".to_owned(), number)
}

#[cfg(test)]
mod tests;
