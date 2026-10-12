//! What a change, a flow or a suite reaches, as a lane selection.
//!
//! [`avl_affected::affected_suites`] answers which generated suites the paths a caller names reach, and
//! [`bt_core::named_suites`] answers which suites a flow id or a suite id names. [`select_suites`] turns either
//! answer into the filters one iteration runs. The join and the refusals are `bt`'s, because they are resolved
//! from the checkout, `bt` owns everything resolved from the checkout, and the host `bt.cmd` gives the same
//! refusals.
//!
//! Two refusals, and they are the point of the command as much as the selection is:
//!
//! - Nothing reached. An empty selection is the false green this repository keeps rediscovering: a run that
//!   executes no test is not a passing run. It refuses, and it names each path with the reason the join gave.
//! - Two lanes reached, for one iteration. One iteration selects one lane by tag, so `shard`, `flake` and the host
//!   `bt` refuse the answer and ask for `--lane`. The refusal states the number of suites each lane holds, and
//!   where the answer came from, so the caller chooses on facts.
//!
//! `run` is the exception to the second refusal. It runs one iteration per reached lane, in the declared lane
//! order, on the selected backend ([`Reached::every_lane`]). The VM keeps the physical input of a GUI-chat suite off
//! the host, so every lane runs on every backend.

use std::collections::BTreeMap;

use avl_affected::{affected_suites, air_area};
use avl_base::{Config, Outcome, Refusal, Reporter};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::{Ctx, Runner};
use bt_core::{
    Affected, CHANGED_PATHS_SUBJECT, Selector, affected_text, choose_lane, classes_of_lane, counted_lane_names, describe_named,
    lane_counts, lane_counts_text, named_suites, suite_class_filters,
};
use serde::Serialize;

use crate::lane::lanes::{JUNIT5_FILTERS, Selection, as_usage, lane_selection};
use crate::lane::runtime::bt_runtime;
use avl_base::RefusalExt;

/// Whether a fully qualified class name can be embedded in a JUnit class-name pattern as a literal.
///
/// The filter values are Java regex patterns applied with `Matcher.matches()`, and the controller escapes literal
/// names itself - `\Q…\E` here. A backslash is the one character that could end the quoted span early and silently
/// widen the filter, and no JVM class name contains one. A report that carries such a name is therefore corrupt
/// rather than exotic, and the safe response is to leave the class out of the plan: with remainder-by-exclusion, a
/// class the plan never names still runs.
///
/// `shard` builds these filters from a measured plan, whose class names are fully qualified. A generated suite is
/// named by its simple class name, which has its own rule: [`bt_core::suite_class_filters`].
pub(crate) fn is_patternable_class_name(class_name: &str) -> bool {
    !class_name.is_empty() && !class_name.contains('\\')
}

/// One class name as an anchored literal Java pattern.
pub(crate) fn class_name_pattern(class_name: &str) -> String {
    format!(r"^\Q{class_name}\E$")
}

/// What one iteration over generated suites runs, and the answer it came from: `run --changed`, a flow selector,
/// or a suite selector.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct AffectedSelection {
    pub selection: Selection,
    pub affected: Affected,
    /// The lane the iteration selects, after `--lane` has settled a two-lane answer.
    pub lane: String,
    /// The lane's classes, in the answer's order. Reported so a caller can see the whole selection without
    /// re-reading the filters.
    pub classes: Vec<String>,
}

/// Turns generated suites into one iteration's selection: one lane, its tag, and one class filter per suite of
/// that lane.
///
/// The one place this happens. `run --changed`, a flow selector and a suite selector all end here, so they share
/// the lane choice, its two-lane refusal, and the class-name rule. `subject` names what the caller asked for in a
/// refusal; see [`bt_core::choose_lane`].
pub(crate) fn select_suites(affected: Affected, requested_lane: Option<&str>, subject: &str) -> Result<AffectedSelection, Refusal> {
    let lane = choose_lane(air_area(), &affected, requested_lane, subject).map_err(avl_affected::refusal)?;
    let selected = lane_selection(&lane)?;
    let classes = classes_of_lane(&affected, &lane);
    let class_filters = suite_class_filters(&classes, &lane).map_err(avl_affected::refusal)?;
    // The lane's own tag comes first and the class filters after it, which is the order `shard` uses: the tag says
    // which lane's suites exist and the class list says which of them this iteration wants.
    let filters: Vec<String> = selected
        .test_env
        .get(JUNIT5_FILTERS)
        .cloned()
        .into_iter()
        .chain(class_filters)
        .collect();
    Ok(AffectedSelection {
        selection: Selection {
            label: selected.label,
            lane: Some(lane.clone()),
            test_env: BTreeMap::from([(JUNIT5_FILTERS.to_owned(), filters.join(";"))]),
        },
        affected,
        lane,
        classes,
    })
}

/// The generated suites a change, a flow or a suite reached, and how a refusal names what was asked.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Reached {
    pub affected: Affected,
    /// A plural noun phrase for what was asked, as [`bt_core::choose_lane`] reads it.
    pub subject: String,
}

impl Reached {
    /// The one iteration of the reached suites; see [`select_suites`].
    ///
    /// `requested_lane` is the caller's `--lane`. It is required only to settle a resolution that spans two lanes;
    /// naming a lane the resolution does not reach is refused rather than run as an empty selection.
    pub(crate) fn one_lane(&self, requested_lane: Option<&str>) -> Result<AffectedSelection, Refusal> {
        select_suites(self.affected.clone(), requested_lane, &self.subject)
    }

    /// One selection per reached lane, in declared lane order: what `run` executes without `--lane`.
    ///
    /// `requested_lane` keeps its meaning: it settles the answer to that one lane. An answer that reaches no suite
    /// keeps the refusal of [`bt_core::choose_lane`].
    pub(crate) fn every_lane(&self, requested_lane: Option<&str>) -> Result<Vec<AffectedSelection>, Refusal> {
        if requested_lane.is_some() || self.affected.lanes.is_empty() {
            return Ok(vec![self.one_lane(requested_lane)?]);
        }
        lane_counts(air_area(), &self.affected)
            .iter()
            .map(|count| self.one_lane(Some(&count.lane)))
            .collect()
    }
}

/// The generated suites the named paths reach.
pub(crate) fn resolve_changed_suites(settings: &Config, paths: &[String], reporter: &Reporter) -> Result<Reached, Refusal> {
    Ok(Reached {
        affected: resolve_affected(settings, paths, reporter)?,
        subject: CHANGED_PATHS_SUBJECT.to_owned(),
    })
}

/// The generated suites a flow or a suite selector names.
///
/// A flow no suite covers and an id nothing knows are refused by [`bt_core::named_suites`], with their own codes.
pub(crate) fn resolve_named_suites(settings: &Config, selector: &str, reporter: &Reporter) -> Result<Reached, Refusal> {
    let runtime = bt_runtime(settings.host_repo()?, reporter);
    let classified = Selector::classify(selector).map_err(as_usage)?;
    let affected = named_suites(&runtime, air_area(), &classified).map_err(as_usage)?;
    Ok(Reached {
        affected,
        subject: describe_named(&classified),
    })
}

/// What one iteration should run for the named paths. The suites that call it are Unix only.
#[cfg(all(test, unix))]
pub(crate) fn resolve_affected_selection(
    settings: &Config,
    paths: &[String],
    requested_lane: Option<&str>,
    reporter: &Reporter,
) -> Result<AffectedSelection, Refusal> {
    resolve_changed_suites(settings, paths, reporter)?.one_lane(requested_lane)
}

/// What one iteration should run for a flow or a suite selector: the selection `run --changed` makes, from the
/// suites the selector names rather than the suites a change reaches. The suites that call it are Unix only.
#[cfg(all(test, unix))]
pub(crate) fn resolve_named_suite_selection(
    settings: &Config,
    selector: &str,
    requested_lane: Option<&str>,
    reporter: &Reporter,
) -> Result<AffectedSelection, Refusal> {
    resolve_named_suites(settings, selector, reporter)?.one_lane(requested_lane)
}

/// Whether a `run` selector is a flow or a suite id. The spelling alone decides, so this reads nothing, and a
/// malformed selector answers false for the ordinary resolution to refuse.
pub(crate) fn is_named_suite_selector(selector: &str) -> bool {
    Selector::classify(selector).is_ok_and(|classified| classified.kind.names_suites())
}

/// The join alone, with no lane chosen and no filter built: what `suites` answers.
pub(crate) fn resolve_affected(settings: &Config, paths: &[String], reporter: &Reporter) -> Result<Affected, Refusal> {
    let runtime = bt_runtime(settings.host_repo()?, reporter);
    affected_suites(&runtime, air_area(), paths).map_err(as_usage)
}

// --- the read-only command ------------------------------------------------------------------------------------

/// `suites`' payload: the resolution, verbatim, and the subtotal the text form also states.
///
/// `laneCounts` is a derived key rather than a changed one. A caller that reads `suites`, `unmapped` or `lanes`
/// sees the same bytes it saw before the subtotal existed.
///
/// The values come from BT's crates as JSON text, because BT links another build of `serde`.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SuitesData {
    suites: serde_json::Value,
    unmapped: serde_json::Value,
    lanes: serde_json::Value,
    lane_counts: Vec<serde_json::Value>,
}

impl SuitesData {
    fn of(affected: &Affected) -> Result<Self, Refusal> {
        let parse = |text: String| {
            serde_json::from_str::<serde_json::Value>(&text)
                .map_err(|error| Refusal::internal(format!("BT's answer did not parse: {error}")))
        };
        let mut answer = parse(affected.to_json_text())?;
        Ok(Self {
            suites: answer["suites"].take(),
            unmapped: answer["unmapped"].take(),
            lanes: answer["lanes"].take(),
            lane_counts: lane_counts(air_area(), affected)
                .iter()
                .map(|count| parse(count.to_json_text()))
                .collect::<Result<_, _>>()?,
        })
    }
}

/// Which generated suites the named paths reach; runs nothing.
///
/// It takes no receipt and touches no worker: the answer is resolved from the checkout, so it is the one command of
/// this controller a caller can run while somebody else's lane holds every slot. That is deliberate - deciding what
/// to run is what a caller does *before* it asks for a worker.
pub(crate) async fn command_suites(
    ctx: &Ctx,
    settings: &Config,
    runner: &Runner,
    paths: Vec<String>,
    reporter: &Reporter,
) -> Result<Outcome, Refusal> {
    if paths.is_empty() {
        return Err(Refusal::usage(format!("usage: {} suites <changed-path>...", reporter.program())));
    }
    // The join reads the checkout through `host_repo`, which is only answerable once the host paths have resolved.
    ensure_host_paths(ctx, runner, settings).await?;
    let affected = resolve_affected(settings, &paths, reporter)?;
    Outcome::new(SuitesData::of(&affected)?, render_suites(&affected))
}

/// The `--text` rendering: the suites, then what could not be mapped, then the command to run.
///
/// The last line subtotals the suites per lane. A coarse answer runs to 19 suites over three lanes, and the caller
/// reads the sizes off that line instead of counting the ones above it. When the answer reaches several lanes, the
/// line also names the lanes the next `run` runs, one iteration each.
fn render_suites(affected: &Affected) -> String {
    let resolution = affected_text(affected);
    if affected.suites.is_empty() {
        return resolution;
    }
    let counts = lane_counts(air_area(), affected);
    let mut next = "run --changed <the same paths>".to_owned();
    if counts.len() > 1 {
        next.push_str(&format!(" (runs {})", counted_lane_names(&counts).join(", ")));
    }
    format!(
        "{resolution}\n{} suite(s): {}; next: {next}",
        affected.suites.len(),
        lane_counts_text(&counts)
    )
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
