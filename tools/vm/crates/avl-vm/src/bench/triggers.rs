//! `bench triggers`: the plugin loaders of one run by the frame that triggered each one. It reads only `result.json` of
//! the run, so it answers on any host.
//!
//! The record computes the trigger frame of each loader from the activation stack of its first class, see
//! [`trigger_frame`]. The report groups the loaders with a class before the anchor by that frame.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

use avl_base::Refusal;
use serde::Serialize;

use super::arm::Arm;
use super::classes::{anchor_of, nanos_ms, read_run};
use super::digest::number;
use super::options::TriggersArgs;
use super::pluginlog::{Owner, PluginLog};
use super::record::{LoaderActivation, LoaderClasses};
use super::session::{self, SelectedRun};
use super::trace::tenth;

/// The class name prefixes of the frames that the search for a trigger skips. The runtime, the libraries of the
/// language, the service container, the extension points and the message bus call a plugin class on behalf of another
/// frame. A trigger in them would put most loaders in one row, such as `ComponentManagerImplKt.doLoadClass`.
const SKIPPED_PREFIXES: [&str; 8] = [
    "java.",
    "jdk.",
    "sun.",
    "kotlin.",
    "kotlinx.",
    "com.intellij.serviceContainer.",
    "com.intellij.openapi.extensions.",
    "com.intellij.util.messages.",
];

/// The marker of a lambda class in a frame, such as `com.intellij.Foo$$Lambda/0x0000000801001000`. The class of the
/// frame is the class before it.
const LAMBDA_MARKER: &str = "$$Lambda";

/// The row of the loaders without an activation stack.
pub(crate) const NO_STACK: &str = "(no activation stack)";
/// The row of the loaders whose stack has no frame of another owner.
pub(crate) const NO_OTHER_OWNER: &str = "(no frame of another owner)";

/// The trigger frame of a loader: the frame nearest to the top of `stack` whose class has another owner than `owner`.
/// The search skips the frames of [`SKIPPED_PREFIXES`]. A class without an owner in `log` is a platform class, which is
/// another owner. The frame is without its prefix of a loader and a module, see [`strip_frame_prefix`]. A stack
/// without such a frame, and an empty stack, has none.
pub(crate) fn trigger_frame(owner: &Owner, stack: &[String], log: &PluginLog) -> Option<String> {
    stack.iter().map(|frame| strip_frame_prefix(frame)).find_map(|frame| {
        let class = frame_class(frame)?;
        if SKIPPED_PREFIXES.iter().any(|prefix| class.starts_with(prefix)) {
            return None;
        }
        let frame_owner = log
            .owner(class)
            .or_else(|| class.split_once(LAMBDA_MARKER).and_then(|(host, _)| log.owner(host)));
        (frame_owner != Some(owner)).then(|| frame.to_owned())
    })
}

/// A frame without the prefix that `StackTraceElement.toString` writes before the class: `<loader>/`, `<module>/` or
/// `<module>@<version>/`, such as `app//com.intellij.Foo.bar(Foo.kt:12)` or
/// `java.base/java.lang.Thread.run(Thread.java:1583)`. The `/` of a hidden class, `/0x`, is part of the class.
pub(crate) fn strip_frame_prefix(frame: &str) -> &str {
    let head = frame.split_once('(').map_or(frame, |(head, _)| head);
    let cut = head
        .match_indices('/')
        .map(|(index, _)| index)
        .rfind(|index| !head[index + 1..].starts_with("0x"));
    cut.map_or(frame, |index| &frame[index + 1..])
}

/// The class of a frame without its prefix: the text before the method, which follows the last `.` before `(`.
fn frame_class(frame: &str) -> Option<&str> {
    let head = frame.split_once('(').map_or(frame, |(head, _)| head);
    head.rsplit_once('.').map(|(class, _)| class)
}

/// The answer of `bench triggers`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TriggerReport {
    pub(crate) session: String,
    /// The run directory name.
    pub(crate) run: String,
    pub(crate) arm: Arm,
    /// The metric whose median picked the run. A run that `--run` names has none.
    pub(crate) median_by: Option<&'static str>,
    /// The anchor of the loaders. A run without an anchor has none, and the report covers the whole run.
    pub(crate) at: Option<String>,
    /// The plugins of `--plugin`. Without it, the report holds every plugin.
    pub(crate) plugins: Vec<String>,
    /// The loaders with a class before the anchor, of the plugins of `--plugin`.
    pub(crate) loaders: usize,
    /// The triggers of those loaders, before the cut to `--top`.
    pub(crate) total_triggers: usize,
    /// The triggers by their classes, the most first, at most `--top`.
    pub(crate) triggers: Vec<TriggerRow>,
    /// The anchors that the run has, in the order of [`super::record::CLASS_ANCHORS`].
    #[serde(skip)]
    pub(crate) anchors: Vec<&'static str>,
    /// A loader of the report has an activation stack, so the plugin log of the run has the new shape.
    #[serde(skip)]
    pub(crate) stacks: bool,
    /// A loader of the report has the EDT time of its classes.
    #[serde(skip)]
    pub(crate) timed: bool,
}

/// One trigger frame and the loaders that it pulled.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TriggerRow {
    /// The frame, [`NO_STACK`] or [`NO_OTHER_OWNER`].
    pub(crate) trigger: String,
    pub(crate) classes: u64,
    /// The EDT milliseconds of the classes of the loaders, to a tenth. A row without a loader with a time has none.
    pub(crate) edt_ms: Option<f64>,
    /// The first load of the earliest loader, in ms from the process start.
    pub(crate) first_load_ms: Option<f64>,
    /// The loaders of the trigger, in the order of their first load, at most `--top`.
    pub(crate) loaders: Vec<TriggerLoader>,
    /// The loaders of the trigger, before the cut to `--top`.
    pub(crate) total_loaders: usize,
}

/// One loader of a trigger.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TriggerLoader {
    pub(crate) plugin: String,
    pub(crate) module: Option<String>,
    pub(crate) classes: u64,
    /// The EDT milliseconds of the classes, to a tenth. A loader without the times of its classes has none.
    pub(crate) edt_ms: Option<f64>,
    pub(crate) first_load_ms: Option<f64>,
    /// The class of the activation stack.
    pub(crate) first_class: Option<String>,
    /// The EDT nanos that `edt_ms` rounds, which the row of the trigger sums.
    #[serde(skip)]
    pub(crate) edt_nanos: Option<u64>,
}

/// Reads the run that `args` selects and groups its loaders by their trigger frame.
pub(crate) fn report(session: &Path, args: &TriggersArgs) -> Result<TriggerReport, Refusal> {
    let selection = &args.selection;
    let summary = session::read_summary(session)?;
    let SelectedRun { id, dir, median_by } = session::select_run(session, &summary, selection.run.as_deref(), selection.arm.as_ref())?;
    let run = read_run(&dir)?;
    let maps = &run.class_maps;
    let (anchors, at) = anchor_of(session, &id.arm, args.at.as_deref(), |anchor| {
        maps.classes_by_loader_at.contains_key(anchor)
    })?;
    let cut: &[LoaderClasses] = match &at {
        Some(at) => maps.classes_by_loader_at.get(at).map_or(&[], Vec::as_slice),
        None => &maps.classes_by_loader,
    };
    let activations: BTreeMap<(&str, Option<&str>), &LoaderActivation> = maps
        .activations
        .iter()
        .map(|activation| ((activation.plugin.as_str(), activation.module.as_deref()), activation))
        .collect();
    let mut groups: BTreeMap<String, Vec<TriggerLoader>> = BTreeMap::new();
    let mut loaders = 0;
    let mut stacks = false;
    let mut timed = false;
    for loader in cut
        .iter()
        .filter(|loader| loader.classes > 0)
        .filter(|loader| args.plugins.is_empty() || args.plugins.contains(&loader.plugin))
    {
        loaders += 1;
        let activation = activations.get(&(loader.plugin.as_str(), loader.module.as_deref()));
        let trigger = match activation {
            Some(activation) if !activation.stack.is_empty() => {
                stacks = true;
                activation.trigger.clone().unwrap_or_else(|| NO_OTHER_OWNER.to_owned())
            }
            Some(_) | None => NO_STACK.to_owned(),
        };
        let edt_nanos = loader.times.map(|times| times.edt_nanos);
        timed |= edt_nanos.is_some();
        groups.entry(trigger).or_default().push(TriggerLoader {
            plugin: loader.plugin.clone(),
            module: loader.module.clone(),
            classes: loader.classes,
            edt_ms: edt_nanos.map(|nanos| tenth(nanos_ms(nanos))),
            first_load_ms: activation.and_then(|activation| activation.first_load_ms),
            first_class: activation.map(|activation| activation.first_class.clone()),
            edt_nanos,
        });
    }
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let mut triggers: Vec<TriggerRow> = groups.into_iter().map(|(trigger, members)| row(trigger, members, top)).collect();
    triggers.sort_by(|left, right| {
        right
            .classes
            .cmp(&left.classes)
            .then_with(|| right.total_loaders.cmp(&left.total_loaders))
            .then_with(|| left.trigger.cmp(&right.trigger))
    });
    let total_triggers = triggers.len();
    triggers.truncate(top);
    Ok(TriggerReport {
        session: session.display().to_string(),
        run: id.dir_name(),
        arm: id.arm,
        median_by,
        at,
        plugins: args.plugins.clone(),
        loaders,
        total_triggers,
        triggers,
        anchors,
        stacks,
        timed,
    })
}

/// The row of one trigger: the sums over its loaders, and its loaders in the order of their first load, at most `top`.
fn row(trigger: String, mut loaders: Vec<TriggerLoader>, top: usize) -> TriggerRow {
    loaders.sort_by(|left, right| {
        let first = |loader: &TriggerLoader| loader.first_load_ms.unwrap_or(f64::INFINITY);
        first(left)
            .total_cmp(&first(right))
            .then_with(|| right.classes.cmp(&left.classes))
            .then_with(|| (&left.plugin, &left.module).cmp(&(&right.plugin, &right.module)))
    });
    let total_loaders = loaders.len();
    let classes = loaders.iter().map(|loader| loader.classes).sum();
    let edt_ms = loaders
        .iter()
        .filter_map(|loader| loader.edt_nanos)
        .reduce(|sum, nanos| sum + nanos)
        .map(|nanos| tenth(nanos_ms(nanos)));
    let first_load_ms = loaders.iter().filter_map(|loader| loader.first_load_ms).reduce(f64::min);
    loaders.truncate(top);
    TriggerRow {
        trigger,
        classes,
        edt_ms,
        first_load_ms,
        loaders,
        total_loaders,
    }
}

/// A cell of a value that the run may lack: the number, else `-`.
fn cell(value: Option<f64>) -> String {
    value.map_or_else(|| "-".to_owned(), number)
}

impl TriggerReport {
    /// The text: the run, the anchor, the table of the triggers, then the loaders of each trigger.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        let picked = self
            .median_by
            .map_or_else(String::new, |metric| format!(" (the median run by {metric})"));
        let _ = writeln!(
            text,
            "vm bench triggers {} {}{picked}",
            session::display_name(&self.session),
            self.run
        );
        let where_ = self
            .at
            .as_deref()
            .map_or_else(|| "over the whole run".to_owned(), |at| format!("at {at}"));
        let anchors = if self.anchors.is_empty() {
            "none".to_owned()
        } else {
            self.anchors.join(", ")
        };
        let plugins = if self.plugins.is_empty() {
            String::new()
        } else {
            format!(" of {}", self.plugins.join(", "))
        };
        let _ = writeln!(
            text,
            "anchors: {anchors}; {} loaders{plugins} with a class {where_}, {} of {} triggers",
            self.loaders,
            self.triggers.len(),
            self.total_triggers
        );
        let _ = writeln!(
            text,
            "{:>7} {:>7} {:>7} {:>8}  trigger frame",
            "loaders", "classes", "EDT ms", "first ms"
        );
        for row in &self.triggers {
            let _ = writeln!(
                text,
                "{:>7} {:>7} {:>7} {:>8}  {}",
                row.total_loaders,
                row.classes,
                cell(row.edt_ms),
                cell(row.first_load_ms),
                row.trigger
            );
        }
        if !self.triggers.is_empty() {
            let _ = writeln!(
                text,
                "the loaders of each trigger, by their first load (ms from the process start):"
            );
            let _ = writeln!(text, "  {:>7} {:>7} {:>8}  loader, first class", "classes", "EDT ms", "first ms");
        }
        for row in &self.triggers {
            let more = if row.loaders.len() < row.total_loaders {
                format!(", {} of {} loaders", row.loaders.len(), row.total_loaders)
            } else {
                String::new()
            };
            let _ = writeln!(text, "  {}{more}:", row.trigger);
            for loader in &row.loaders {
                let name = Owner::loader_label(&loader.plugin, loader.module.as_deref());
                let first_class = loader.first_class.as_deref().map_or_else(String::new, |class| format!(", {class}"));
                let _ = writeln!(
                    text,
                    "  {:>7} {:>7} {:>8}  {name}{first_class}",
                    loader.classes,
                    cell(loader.edt_ms),
                    cell(loader.first_load_ms)
                );
            }
        }
        if !self.triggers.is_empty() {
            let _ = writeln!(
                text,
                "note: a trigger woke its loaders; their classes up to the anchor can come from later code, so read the classes of a loader in bench classes"
            );
        }
        if self.loaders > 0 && !self.stacks {
            let _ = writeln!(
                text,
                "note: no loader has an activation stack: plugin-classes.txt of the run has the old shape, without the \
                 thread, the time and the stack of a class"
            );
        } else if self.loaders > 0 && !self.timed {
            let _ = writeln!(
                text,
                "note: no loader has an EDT time: plugin-classes.txt of the run has no thread and no time"
            );
        }
        if self.loaders == 0 {
            let _ = writeln!(
                text,
                "no loader {where_}: the run has no plugin-classes.txt, or no class of a named plugin"
            );
        }
        text.truncate(text.trim_end().len());
        text
    }
}

#[cfg(test)]
mod tests;
