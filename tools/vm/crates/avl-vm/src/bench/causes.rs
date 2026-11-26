//! `bench causes`: the classes of the plugin loaders of one run by the frame that entered the loader of each class.
//!
//! It reads [`CLASS_CAUSE_LOG`], which a session with `--class-causes` writes: the JVM log
//! `-Xlog:class+load+cause` with `LogClassLoadingCauseFor=*`. A record of that log is the line
//! `Java stack when loading <class>:` and a line `\tat <frame>` per frame, the top frame first, each with the `uptime`,
//! `tid` and `tags` decorators. The records of several threads interleave, so the reader joins the lines by their tid.
//!
//! The plugin log gives the owner of each class, and the class-load log gives the classes before the anchor. The
//! entry frame of a class is the frame nearest to the bottom of its stack whose class has the owner of the class, see
//! [`cause`].

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fmt::Write as _;
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};

use anyhow::Context;
use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::arm::Arm;
use super::classes::{anchor_of, read_run};
use super::classload::{self, ClassLoadLog};
use super::digest::number;
use super::files;
use super::options::CausesArgs;
use super::pluginlog::{self, Owner, PluginLog};
use super::record::{self, PLUGIN_CLASSES_FILE, QUIT_ANCHOR, RunId};
use super::session::{self, CLASS_CAUSE_LOG, CLASS_LOAD_LOG, SelectedRun};
use super::timeline::Timeline;
use super::trace::tenth;
use super::triggers::{frame_class, frame_owner, is_skipped, of_filters, run_header, strip_frame_prefix, trigger_frame};

/// The message that starts the record of one class load.
const STACK_HEADER: &str = "Java stack when loading ";
/// The prefix of the message of one frame.
const FRAME_PREFIX: &str = "\tat ";
/// The marker of a hidden class in a frame, such as `Foo$$Lambda/0x0000000801001000`.
const HIDDEN_MARKER: &str = "/0x";

/// The row of the classes without a stack in the log.
pub(crate) const NO_STACK: &str = "(no stack)";
/// The row of the classes of the baseline that the run with the stacks did not load.
pub(crate) const NOT_LOADED: &str = "(not loaded by the run with the stacks)";
/// The row of the classes whose stack has no frame of the owner and no frame of another owner.
pub(crate) const NO_FRAME: &str = "(no frame of an owner)";

/// One class load of the log: its uptime on the JVM clock, its class and its frames, the top frame first.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct CauseRecord {
    pub(crate) uptime_ms: f64,
    pub(crate) class: String,
    pub(crate) frames: Vec<String>,
}

/// The frame that entered the loader of a class, and the frame of another owner below it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Cause {
    /// The class and the method of the entry frame, see [`method_of`].
    pub(crate) entry: String,
    /// The class and the method of the nearest frame below the entry frame that the search does not skip. A class
    /// whose stack has no frame of its owner, and a stack without such a frame, has none.
    pub(crate) caller: Option<String>,
}

/// Reads the log from `reader` and calls `each` with each record whose class `keep` accepts. A record ends at the
/// next header of its thread, or at the end of the log. A line of another shape, such as `\t- locked <...>`, is
/// skipped. A line without decorators is an error that names the line.
pub(crate) fn parse(reader: impl BufRead, keep: impl Fn(&str) -> bool, mut each: impl FnMut(CauseRecord)) -> anyhow::Result<()> {
    let mut pending: HashMap<Option<u64>, Option<CauseRecord>> = HashMap::new();
    for (index, line) in reader.split(b'\n').enumerate() {
        let line = line.context("cannot read the log")?;
        let line = String::from_utf8_lossy(&line);
        let line = line.trim_end_matches('\r');
        if line.trim().is_empty() {
            continue;
        }
        let decorated = classload::decorated(line).with_context(|| format!("line {}: {line}", index + 1))?;
        if let Some(class) = decorated.message.strip_prefix(STACK_HEADER) {
            let class = class.trim_end().trim_end_matches(':');
            let record = keep(class).then(|| CauseRecord {
                uptime_ms: decorated.uptime_ms,
                class: class.to_owned(),
                frames: Vec::new(),
            });
            if let Some(Some(done)) = pending.insert(decorated.tid, record) {
                each(done);
            }
        } else if let Some(frame) = decorated.message.strip_prefix(FRAME_PREFIX)
            && let Some(Some(record)) = pending.get_mut(&decorated.tid)
        {
            record.frames.push(frame.trim_end().to_owned());
        }
    }
    let mut rest: Vec<CauseRecord> = pending.into_values().flatten().collect();
    rest.sort_by(|left, right| left.uptime_ms.total_cmp(&right.uptime_ms));
    rest.into_iter().for_each(each);
    Ok(())
}

/// The cause of a class of `owner` from its frames. The entry frame is the frame nearest to the bottom whose class
/// has `owner`, and the caller is the nearest frame below it that the search does not skip. A stack without a frame of
/// `owner` has the trigger frame of [`trigger_frame`] as its entry and no caller. A stack without either has none.
pub(crate) fn cause(owner: &Owner, frames: &[String], log: &PluginLog) -> Option<Cause> {
    let stripped: Vec<&str> = frames.iter().map(|frame| strip_frame_prefix(frame)).collect();
    let owned = |frame: &&str| frame_class(frame).is_some_and(|class| frame_owner(class, log) == Some(owner));
    let Some(index) = stripped.iter().rposition(owned) else {
        return trigger_frame(owner, frames, log).map(|frame| Cause {
            entry: method_of(&frame),
            caller: None,
        });
    };
    let caller = stripped[index + 1..]
        .iter()
        .find(|frame| frame_class(frame).is_some_and(|class| !is_skipped(class)))
        .map(|frame| method_of(frame));
    Some(Cause {
        entry: method_of(stripped[index]),
        caller,
    })
}

/// The class and the method of a frame without its prefix: the text before `(`. A hidden class loses its address, so
/// the frames of one lambda make one row: `Foo$$Lambda/0x0000000801001000.invoke` is `Foo$$Lambda.invoke`.
pub(crate) fn method_of(frame: &str) -> String {
    let head = frame.split_once('(').map_or(frame, |(head, _)| head);
    match head.split_once(HIDDEN_MARKER) {
        Some((host, rest)) => match rest.split_once('.') {
            Some((_, method)) => format!("{host}.{method}"),
            None => host.to_owned(),
        },
        None => head.to_owned(),
    }
}

/// The answer of `bench causes`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CauseReport {
    pub(crate) session: String,
    /// The run directory name.
    pub(crate) run: String,
    pub(crate) arm: Arm,
    /// The metric whose median picked the run. A run that `--run` names has none.
    pub(crate) median_by: Option<&'static str>,
    /// The anchor of the classes. A run without an anchor has none, and the report covers the whole run.
    pub(crate) at: Option<String>,
    /// The plugins of `--plugin`. Without it, the report holds every plugin.
    pub(crate) plugins: Vec<String>,
    /// The modules of `--module`. Without it, the report holds every module of the plugins.
    pub(crate) modules: Vec<String>,
    /// The classes of the report: the plugin classes before the anchor that pass the filters.
    pub(crate) classes: u64,
    /// The loaders of those classes.
    pub(crate) loaders: usize,
    /// The run of `--baseline` that gives the classes, and how its classes differ from the classes of this run.
    pub(crate) baseline: Option<Baseline>,
    /// The entry frames of those classes, before the cut to `--top`.
    pub(crate) total_entries: usize,
    /// The entry frames by their classes, the most first, at most `--top`.
    pub(crate) entries: Vec<EntryRow>,
    /// The anchors that the run has, in the order of [`record::CLASS_ANCHORS`].
    #[serde(skip)]
    pub(crate) anchors: Vec<&'static str>,
}

/// The run of `--baseline` and the difference of its classes to the classes of the run with the stacks, before the
/// anchor and after the filters.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Baseline {
    pub(crate) session: String,
    pub(crate) run: String,
    pub(crate) classes: u64,
    /// The classes that only the run with the stacks loaded. The report leaves them out.
    pub(crate) only_here: u64,
    /// The classes that only the baseline run loaded. They are in the row [`NOT_LOADED`].
    pub(crate) only_there: u64,
}

/// One entry frame and the classes that it loaded.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct EntryRow {
    /// The class and the method of the entry frame, [`NO_STACK`] or [`NO_FRAME`].
    pub(crate) entry: String,
    /// The most common caller of the classes of the row. A row without a caller has none.
    pub(crate) caller: Option<String>,
    pub(crate) classes: u64,
    /// The first load of a class of the row, in ms from the process start.
    pub(crate) first_ms: f64,
    /// The loaders of the classes of the row, the most classes first: `<module> (<plugin id>)` or `<plugin id> (main)`.
    pub(crate) loaders: Vec<String>,
    /// The first classes of the row by their load, at most `--classes`.
    pub(crate) first_classes: Vec<String>,
}

/// One class of the report.
struct Loaded<'a> {
    owner: &'a Owner,
    name: &'a str,
    /// Milliseconds from the process start.
    ms: f64,
}

/// The class logs of one run, and the bound of the anchor of a report on the JVM clock.
struct RunClasses {
    session: PathBuf,
    id: RunId,
    dir: PathBuf,
    median_by: Option<&'static str>,
    anchors: Vec<&'static str>,
    at: Option<String>,
    owners: PluginLog,
    log: ClassLoadLog,
    /// The start of the process clock on the JVM clock, in milliseconds.
    offset_ms: f64,
    /// The anchor on the JVM clock. Without an anchor, none, and the whole run counts.
    bound: Option<f64>,
}

impl RunClasses {
    /// Reads the run of `session` that `run` and `arm` select, with the anchor `at`, else the default anchor of the arm.
    fn read(session: &Path, run: Option<&str>, arm: Option<&Arm>, at: Option<&str>) -> Result<Self, Refusal> {
        let summary = session::read_summary(session)?;
        let SelectedRun { id, dir, median_by } = session::select_run(session, &summary, run, arm)?;
        let record = read_run(&dir)?;
        let (anchors, at) = anchor_of(session, &id.arm, at, |anchor| {
            record.class_maps.classes_by_loader_at.contains_key(anchor)
        })?;
        let run_name = id.dir_name();
        let refuse = |name: &'static str| {
            let run_name = run_name.clone();
            move |error: anyhow::Error| Refusal::usage(format!("{name} of {run_name}: {error:#}"))
        };
        let read = |name: &str| -> Result<String, Refusal> {
            files::read_optional(&dir.join(name))
                .map_err(|error| Refusal::usage(format!("{error:#}")))?
                .ok_or_else(|| Refusal::usage(format!("bench causes reads {name} of the run {run_name}, and it has none")))
        };
        let owners = pluginlog::parse(&read(PLUGIN_CLASSES_FILE)?).map_err(refuse(PLUGIN_CLASSES_FILE))?;
        let log = classload::parse(&read(CLASS_LOAD_LOG)?).map_err(refuse(CLASS_LOAD_LOG))?;
        let start = record::process_start_of(&dir, &log).map_err(refuse("startup-stats.json"))?;
        let offset_ms = match (start, &at) {
            (Some(start), _) => start.uptime_ms,
            (None, None) => 0.0,
            (None, Some(at)) => {
                return Err(Refusal::usage(format!(
                    "--at {at} needs {} in {CLASS_LOAD_LOG} or jvm.loadingTime in startup-stats.json of {run_name}, and it has neither",
                    classload::MAIN_CLASS
                )));
            }
        };
        let anchor_ms = match at.as_deref() {
            None => None,
            Some(QUIT_ANCHOR) => Timeline::read(&dir).map_err(refuse("the trace"))?.quit_ms(),
            Some(anchor) => record.metrics.get(anchor).copied(),
        };
        Ok(Self {
            session: session.to_path_buf(),
            id,
            dir,
            median_by,
            anchors,
            at,
            owners,
            log,
            offset_ms,
            bound: anchor_ms.map(|ms| ms + offset_ms),
        })
    }

    /// The plugin classes of the run before the anchor whose owner passes `selected`, see [`loaded`].
    fn loaded(&self, selected: &impl Fn(&Owner) -> bool) -> Vec<Loaded<'_>> {
        loaded(&self.log, &self.owners, self.bound, self.offset_ms, selected)
    }
}

/// Reads the run that `args` selects and groups its plugin classes by their entry frame. With `baseline`, a session
/// without `--class-causes`, the classes are those of the run of the same arm of `baseline`, and the run of `session`
/// gives only their stacks. The stacks slow the run of `session` down, so it can load other classes before the anchor.
pub(crate) fn report(session: &Path, baseline: Option<&Path>, args: &CausesArgs) -> Result<CauseReport, Refusal> {
    let selection = &args.selection;
    let main = RunClasses::read(session, selection.run.as_deref(), selection.arm.as_ref(), args.at.as_deref())?;
    let run_name = main.id.dir_name();
    let selected = |owner: &Owner| {
        (args.plugins.is_empty() || args.plugins.contains(&owner.plugin))
            && (args.modules.is_empty()
                || args
                    .modules
                    .iter()
                    .any(|name| owner.module.as_deref().map_or(&owner.plugin == name, |module| module == name)))
    };
    let file = File::open(main.dir.join(CLASS_CAUSE_LOG)).map_err(|error| {
        Refusal::usage(format!(
            "bench causes reads {CLASS_CAUSE_LOG} of the run {run_name}, and it has none ({error}): start the session with --class-causes"
        ))
    })?;
    let owners = &main.owners;
    let mut causes: HashMap<String, Option<Cause>> = HashMap::new();
    parse(
        BufReader::new(file),
        |class| owners.owner(class).is_some_and(selected),
        |record| {
            if let Some(owner) = owners.owner(&record.class) {
                causes
                    .entry(record.class.clone())
                    .or_insert_with(|| cause(owner, &record.frames, owners));
            }
        },
    )
    .map_err(|error| Refusal::usage(format!("{CLASS_CAUSE_LOG} of {run_name}: {error:#}")))?;
    let base = baseline
        .map(|baseline| RunClasses::read(baseline, None, Some(&main.id.arm), main.at.as_deref()))
        .transpose()?;
    let own = main.loaded(&selected);
    let (classes, missing, comparison) = match &base {
        None => (own, NO_STACK, None),
        Some(base) => {
            let classes = base.loaded(&selected);
            let base_names: HashSet<&str> = classes.iter().map(|class| class.name).collect();
            let own_names: HashSet<&str> = own.iter().map(|class| class.name).collect();
            let comparison = Baseline {
                session: base.session.display().to_string(),
                run: base.id.dir_name(),
                classes: count(classes.len()),
                only_here: count(own_names.difference(&base_names).count()),
                only_there: count(base_names.difference(&own_names).count()),
            };
            (classes, NOT_LOADED, Some(comparison))
        }
    };
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let first = usize::try_from(args.classes).unwrap_or(usize::MAX);
    let loaders = classes.iter().map(|class| class.owner).collect::<HashSet<_>>().len();
    let (entries, total_entries) = rows(&classes, &causes, missing, top, first);
    Ok(CauseReport {
        session: session.display().to_string(),
        run: run_name,
        arm: main.id.arm.clone(),
        median_by: main.median_by,
        at: main.at.clone(),
        plugins: args.plugins.clone(),
        modules: args.modules.clone(),
        classes: count(classes.len()),
        loaders,
        baseline: comparison,
        total_entries,
        entries,
        anchors: main.anchors.clone(),
    })
}

/// A count of a collection.
fn count(len: usize) -> u64 {
    u64::try_from(len).unwrap_or(u64::MAX)
}

/// The plugin classes of `log` before `bound` whose owner passes `selected`, each name once, in the order of their
/// load.
fn loaded<'a>(
    log: &'a ClassLoadLog,
    owners: &'a PluginLog,
    bound: Option<f64>,
    offset_ms: f64,
    selected: &impl Fn(&Owner) -> bool,
) -> Vec<Loaded<'a>> {
    let mut seen = HashSet::new();
    log.named_before(bound)
        .filter_map(|class| {
            let owner = owners.owner(&class.name).filter(|owner| selected(owner))?;
            seen.insert(class.name.as_str()).then(|| Loaded {
                owner,
                name: &class.name,
                ms: tenth(class.uptime_ms - offset_ms),
            })
        })
        .collect()
}

/// The rows of `classes` by their entry frame, the most classes first, at most `top`, each with its first `first`
/// classes, and the count of the rows before the cut.
fn rows(
    classes: &[Loaded<'_>],
    causes: &HashMap<String, Option<Cause>>,
    missing: &'static str,
    top: usize,
    first: usize,
) -> (Vec<EntryRow>, usize) {
    struct Group<'a> {
        classes: u64,
        first_ms: f64,
        callers: BTreeMap<&'a str, u64>,
        loaders: BTreeMap<String, u64>,
        first_classes: Vec<String>,
    }
    let mut groups: BTreeMap<&str, Group<'_>> = BTreeMap::new();
    for class in classes {
        let (entry, caller) = match causes.get(class.name) {
            None => (missing, None),
            Some(None) => (NO_FRAME, None),
            Some(Some(cause)) => (cause.entry.as_str(), cause.caller.as_deref()),
        };
        let group = groups.entry(entry).or_insert_with(|| Group {
            classes: 0,
            first_ms: class.ms,
            callers: BTreeMap::new(),
            loaders: BTreeMap::new(),
            first_classes: Vec::new(),
        });
        group.classes += 1;
        group.first_ms = group.first_ms.min(class.ms);
        if let Some(caller) = caller {
            *group.callers.entry(caller).or_default() += 1;
        }
        *group
            .loaders
            .entry(Owner::loader_label(&class.owner.plugin, class.owner.module.as_deref()))
            .or_default() += 1;
        if group.first_classes.len() < first {
            group.first_classes.push(class.name.to_owned());
        }
    }
    let mut rows: Vec<EntryRow> = groups
        .into_iter()
        .map(|(entry, group)| {
            let caller = group
                .callers
                .iter()
                .max_by(|left, right| left.1.cmp(right.1).then_with(|| right.0.cmp(left.0)))
                .map(|(caller, _)| (*caller).to_owned());
            let mut loaders: Vec<(String, u64)> = group.loaders.into_iter().collect();
            loaders.sort_by(|left, right| right.1.cmp(&left.1).then_with(|| left.0.cmp(&right.0)));
            EntryRow {
                entry: entry.to_owned(),
                caller,
                classes: group.classes,
                first_ms: group.first_ms,
                loaders: loaders.into_iter().map(|(loader, _)| loader).collect(),
                first_classes: group.first_classes,
            }
        })
        .collect();
    rows.sort_by(|left, right| {
        right
            .classes
            .cmp(&left.classes)
            .then_with(|| left.first_ms.total_cmp(&right.first_ms))
            .then_with(|| left.entry.cmp(&right.entry))
    });
    let total = rows.len();
    rows.truncate(top);
    (rows, total)
}

impl CauseReport {
    /// The text: the run, the anchor, then one block per entry frame.
    pub(crate) fn text(&self) -> String {
        let (mut text, where_, anchors) = run_header(
            "causes",
            &self.session,
            &self.run,
            self.median_by,
            self.at.as_deref(),
            &self.anchors,
        );
        let filters: Vec<String> = self.plugins.iter().chain(&self.modules).cloned().collect();
        let of = of_filters(&filters);
        let _ = writeln!(
            text,
            "anchors: {anchors}; {} classes{of} in {} loaders {where_}, {} of {} entry frames",
            self.classes,
            self.loaders,
            self.entries.len(),
            self.total_entries
        );
        if let Some(baseline) = &self.baseline {
            let _ = writeln!(
                text,
                "classes of the baseline {} {}: {}; {} only in the run with the stacks are left out, {} only in the baseline have no stack",
                session::display_name(&baseline.session),
                baseline.run,
                baseline.classes,
                baseline.only_here,
                baseline.only_there
            );
        }
        let _ = writeln!(text, "{:>7} {:>8}  entry frame", "classes", "first ms");
        for row in &self.entries {
            let _ = writeln!(text, "{:>7} {:>8}  {}", row.classes, number(row.first_ms), row.entry);
            if let Some(caller) = &row.caller {
                let _ = writeln!(text, "{:>17}  called from {caller}", "");
            }
            let _ = writeln!(text, "{:>17}  in {}", "", row.loaders.join(", "));
            if !row.first_classes.is_empty() {
                let _ = writeln!(text, "{:>17}  first: {}", "", row.first_classes.join(", "));
            }
        }
        if self.classes == 0 {
            let _ = writeln!(text, "no class {where_}: the run has no class of a named plugin or module");
        }
        text.truncate(text.trim_end().len());
        text
    }
}

#[cfg(test)]
mod tests;
