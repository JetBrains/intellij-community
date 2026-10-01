//! The suites a caller names, and the one lane an iteration of them runs.
//!
//! # Why a flow and a suite are selectors
//!
//! A flow's e2e run used to be a lease script: warm the analysis, take a lease, pull the receipt out with `jq`, run
//! each generated class, release. The class names were the only handle, and a reader who knows the flow had to look
//! them up first. The committed suite documents already state which suites tell or walk a flow, so a flow id and a
//! suite id answer that lookup here, and `vm.cmd run flow-rename-session` and `bt.cmd flow-rename-session` are one
//! line each. ADR 0157 records the decision.
//!
//! # The flow relation has one owner
//!
//! A flow id reaches the suites a `@flow` tag with that id reaches: the suites whose scenarios tell or walk the flow
//! ([`ScenarioFlows::walks`]), and the suites whose story flows implement it. The trace planner reads the scenarios
//! one level down and asks the same [`ScenarioFlows::walks`].
//!
//! # One input beyond the suite documents
//!
//! A flow that no suite covers is a different answer from a flow id with a typo, and the suite documents alone
//! cannot tell the two apart. The committed story-flow text can: every story flow has one file there (ADR 0153,
//! ADR 0154). It is read only on that refusal path.
//!
//! # One lane per iteration
//!
//! [`choose_lane`] is the refusal `run --changed` has always given, shared by the host `bt` and the controller's
//! `run`. A GUI-chat suite may drive the physical pointer, so an answer that spans two lanes is refused rather than
//! chosen for the caller, and the refusal asks for `--lane`.

use std::collections::{BTreeMap, BTreeSet, HashMap};

use bt_junit::simple_class_name_pattern;
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::areas::Area;
use crate::catalog::{SuiteDocument, read_suite_documents};
use crate::lanes::Catalog;
use crate::refusal::{Refusal, exit, fail_infra, fail_usage};
use crate::regex;
use crate::runtime::{Runtime, repo_file};
use crate::selector::{Resolution, Selector, SelectorKind, is_flow_id, suggest_names};

/// The words a refusal uses for a known flow that no suite covers.
pub const REASON_NO_SUITE_TESTS_FLOW: &str = "no e2e suite tests this flow";

/// How the refusals of `run --changed` name what the caller asked for.
pub const CHANGED_PATHS_SUBJECT: &str = "the named path(s)";

/// The prefix an [`AffectedSuite::via`] entry of the module relation carries. A constant because it is also read
/// back, by [`reached_by_module_only`].
pub const VIA_MODULE: &str = "module:";

/// One suite an answer holds, generated or authored.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct AffectedSuite {
    pub suite: String,
    /// The lane table's name for the lane (`ui`, `ui-real`, `gui-chat`), not the catalog's `UI_REAL` spelling.
    pub lane: String,
    pub class: String,
    /// Why this suite is in the answer, as `suite:<id>`, `flow:<id>`, `lane-wide:<module>`, `spec:<path>` or
    /// `module:<name>` entries, sorted. Each entry names the authored fact that matched, not the path that matched
    /// it, so a caller that disagrees with the selection can see what produced it.
    pub via: Vec<String>,
    /// A suite of the authored suites file, which has no scenario document.
    #[serde(default, skip_serializing_if = "is_false")]
    pub authored: bool,
}

fn is_false(value: &bool) -> bool {
    !value
}

/// One named path that reaches no suite, and why.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct UnmappedPath {
    pub path: String,
    /// Why the path reaches nothing, as a stable code the join that produced the answer defines.
    pub reason: String,
    /// The flow ids the path declares, when the reason is about them.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub flows: Vec<String>,
    /// The JPS module the path belongs to, when the reason is about it.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub module: Option<String>,
}

impl UnmappedPath {
    pub fn new(path: &str, reason: &str) -> Self {
        Self {
            path: path.to_owned(),
            reason: reason.to_owned(),
            ..Self::default()
        }
    }
}

/// The whole answer: what to run, what could not be mapped, and which lanes are involved.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Affected {
    pub suites: Vec<AffectedSuite>,
    pub unmapped: Vec<UnmappedPath>,
    /// The distinct lanes of the suites, sorted. More than one lane is a caller decision rather than an answer:
    /// one iteration runs one lane's tag, and a GUI-chat suite needs the guest the user chose.
    pub lanes: Vec<String>,
}

impl Affected {
    /// The answer as JSON text in field order, for a caller that links another build of `serde`.
    pub fn to_json_text(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|error| json!({"unencodable": error.to_string()}).to_string())
    }
}

/// Every reason each suite (an index into the documents) is in an answer.
pub type Via = BTreeMap<usize, BTreeSet<String>>;

pub fn note_via(via: &mut Via, suite: usize, reason: String) {
    via.entry(suite).or_default().insert(reason);
}

/// The reached suites as an [`Affected`], with their lanes in the lane table's vocabulary.
///
/// The one place an answer is assembled. The changed-path join and [`named_suites`] both end here, so a suite a
/// path reaches and a suite a flow names are the same entry, sorted the same way.
pub fn affected_answer(area: &Area, documents: &[SuiteDocument], via: &Via, unmapped: Vec<UnmappedPath>) -> Result<Affected, Refusal> {
    let lanes = area.lanes();
    let mut suites = Vec::with_capacity(via.len());
    let mut lane_names = BTreeSet::new();
    for (&index, reasons) in via {
        let document = &documents[index];
        let Some(lane) = document.lane_name(lanes) else {
            return Err(fail_infra(format!(
                "suite {} declares lane {}, which the lane table {} does not declare; it declares {}",
                document.suite,
                document.lane,
                area.lanes_file(),
                lanes.catalog_lane_names().join(", ")
            )));
        };
        lane_names.insert(lane.to_owned());
        suites.push(AffectedSuite {
            suite: document.suite.clone(),
            lane: lane.to_owned(),
            class: document.test_class_name.clone(),
            via: reasons.iter().cloned().collect(),
            authored: document.authored,
        });
    }
    suites.sort_by(|first, second| (&first.lane, &first.suite).cmp(&(&second.lane, &second.suite)));
    Ok(Affected {
        suites,
        unmapped,
        lanes: lane_names.into_iter().collect(),
    })
}

/// The documents by suite id.
pub fn suites_by_id(documents: &[SuiteDocument]) -> HashMap<&str, usize> {
    documents
        .iter()
        .enumerate()
        .map(|(index, document)| (document.suite.as_str(), index))
        .collect()
}

/// The flow relation: every flow a suite's scenarios tell or walk, and every implementation flow its story flows
/// implement, mapped to the suites. A `@flow` tag on a path and a flow selector both answer through it, so a file
/// that declares a flow and the flow's id reach the same suites. A flow has one kind, so no id arrives through
/// both halves.
pub fn suites_by_flow(documents: &[SuiteDocument]) -> BTreeMap<String, Vec<usize>> {
    let mut by_flow: BTreeMap<String, Vec<usize>> = BTreeMap::new();
    for (index, document) in documents.iter().enumerate() {
        let walked: BTreeSet<String> = document
            .profiles
            .iter()
            .flat_map(|profile| profile.scenario_flows().flows())
            .collect();
        for flow in walked.into_iter().chain(document.implementation_flows.iter().cloned()) {
            by_flow.entry(flow).or_default().push(index);
        }
    }
    by_flow
}

/// The classes of one lane's suites, in the order the answer holds them.
///
/// Separate from the resolution because the two questions have different owners: what a change reaches is the
/// join's, and what one iteration may select belongs to the caller that holds a worker.
pub fn classes_of_lane(affected: &Affected, lane: &str) -> Vec<String> {
    affected
        .suites
        .iter()
        .filter(|suite| suite.lane == lane)
        .map(|suite| suite.class.clone())
        .collect()
}

/// Whether the coarse module relation alone produced this answer.
///
/// Every `via` entry names the relation that matched, so this reads the answer rather than resolving it again. A
/// `module:` endpoint covers a whole module, so a module-only answer is wider than the change, and a flow tag on
/// the path narrows it. An answer with no suite reached nothing, which is a different report.
pub fn reached_by_module_only(affected: &Affected) -> bool {
    !affected.suites.is_empty()
        && affected
            .suites
            .iter()
            .flat_map(|suite| &suite.via)
            .all(|reason| reason.starts_with(VIA_MODULE))
}

/// How many unmapped paths [`affected_text`] lists one per line, and the no-suite refusal names.
pub const UNMAPPED_LINES_PER_REASON: usize = 10;

/// The answer as text, one line per suite and one per unmapped path.
///
/// A named directory can leave hundreds of unmapped files, and a line for each buries the suites. So past
/// [`UNMAPPED_LINES_PER_REASON`] paths, the text states each reason once, with its count and its first path. The
/// JSON answer keeps every path.
pub fn affected_text(affected: &Affected) -> String {
    let mut lines: Vec<String> = affected
        .suites
        .iter()
        .map(|suite| format!("{} {} ({}) via {}", suite.lane, suite.class, suite.suite, suite.via.join(" ")))
        .collect();
    if affected.unmapped.len() <= UNMAPPED_LINES_PER_REASON {
        for unmapped in &affected.unmapped {
            lines.push(format!("unmapped {}: {}", unmapped.path, unmapped_reason_text(unmapped)));
        }
    } else {
        let mut by_reason: Vec<(String, Vec<&UnmappedPath>)> = Vec::new();
        for unmapped in &affected.unmapped {
            let reason = unmapped_reason_text(unmapped);
            match by_reason.iter_mut().find(|(held, _)| *held == reason) {
                Some((_, paths)) => paths.push(unmapped),
                None => by_reason.push((reason, vec![unmapped])),
            }
        }
        for (reason, held) in by_reason {
            if let [only] = held.as_slice() {
                lines.push(format!("unmapped {}: {reason}", only.path));
            } else {
                lines.push(format!("unmapped {} paths: {reason}, the first {}", held.len(), held[0].path));
            }
        }
    }
    if lines.is_empty() {
        return "no suite, and nothing unmapped: no path was named".to_owned();
    }
    lines.join("\n")
}

/// The reason of one unmapped path, with the flows or the module it names.
fn unmapped_reason_text(unmapped: &UnmappedPath) -> String {
    let mut text = unmapped.reason.clone();
    if !unmapped.flows.is_empty() {
        text.push_str(&format!(" ({})", unmapped.flows.join(" ")));
    }
    if let Some(module) = &unmapped.module {
        text.push_str(&format!(" ({module})"));
    }
    text
}

/// The flows one scenario touches: the flow it tells, and the flow of each step it walks. The two differ for a
/// cross-flow journey.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ScenarioFlows {
    pub flow: String,
    pub step_flows: Vec<String>,
}

impl ScenarioFlows {
    /// Every flow the scenario tells or walks, in declaration order, with no empty id.
    pub fn flows(&self) -> Vec<String> {
        let mut flows: Vec<String> = Vec::new();
        for flow in std::iter::once(&self.flow).chain(&self.step_flows) {
            if !flow.is_empty() && !flows.contains(flow) {
                flows.push(flow.clone());
            }
        }
        flows
    }

    /// Whether the scenario tells this flow or walks one of its steps.
    pub fn walks(&self, flow: &str) -> bool {
        !flow.is_empty() && (self.flow == flow || self.step_flows.iter().any(|step| step == flow))
    }
}

/// The suites a flow or a suite selector names. A suite id names a generated suite or an authored one; a flow id
/// names generated suites only, because an authored suite states no flow.
///
/// Each suite carries `flow:<id>` or `suite:<id>` in its `via`, the spelling a path's tag gives, so every consumer
/// of an [`Affected`] reads this answer unchanged. It never answers an empty selection: an unknown id is a usage
/// refusal that names the nearest known ids, and a story flow no suite covers is `no_affected_suite` with
/// [`REASON_NO_SUITE_TESTS_FLOW`].
pub fn named_suites(runtime: &dyn Runtime, area: &Area, selector: &Selector) -> Result<Affected, Refusal> {
    if !selector.kind.names_suites() {
        return Err(fail_usage(format!(
            "{} is a {} selector, which names no generated suite",
            selector.name, selector.kind
        )));
    }
    let catalog = area.catalog()?;
    let documents = read_suite_documents(runtime, area)?;
    let by_suite = suites_by_id(&documents);
    let mut via = Via::new();
    if selector.kind == SelectorKind::Suite {
        let Some(&suite) = by_suite.get(selector.name.as_str()) else {
            let mut known: Vec<&str> = by_suite.keys().copied().collect();
            known.sort_unstable();
            return Err(unknown_id(
                "suite",
                &selector.name,
                known,
                &format!(
                    "a suite id is the name of a document under {} or a suite of {}",
                    catalog.flow_profile_dir, catalog.authored_suites_file
                ),
            ));
        };
        note_via(&mut via, suite, format!("suite:{}", selector.name));
        return affected_answer(area, &documents, &via, Vec::new());
    }

    let by_flow = suites_by_flow(&documents);
    for &suite in by_flow.get(&selector.name).into_iter().flatten() {
        note_via(&mut via, suite, format!("flow:{}", selector.name));
    }
    if !via.is_empty() {
        return affected_answer(area, &documents, &via, Vec::new());
    }
    let story_flows = committed_story_flows(runtime, &catalog);
    if story_flows.contains(&selector.name) {
        return Err(Refusal::new(
            "no_affected_suite",
            exit::USAGE,
            format!(
                "{}: {REASON_NO_SUITE_TESTS_FLOW}; run --lane with the lane you want, or name the flow's source \
                 files to `suites`, which also answers the suites their owning specs link",
                selector.name
            ),
        )
        .with_details(json!({"flow": selector.name, "reason": REASON_NO_SUITE_TESTS_FLOW})));
    }
    let mut known: Vec<&str> = story_flows
        .iter()
        .map(String::as_str)
        .chain(by_flow.keys().map(String::as_str))
        .collect();
    known.sort_unstable();
    known.dedup();
    Err(unknown_id(
        "flow",
        &selector.name,
        known,
        &format!(
            "a flow id is a story flow under {} or a flow a generated suite walks",
            catalog.flow_text_dir
        ),
    ))
}

/// How a refusal names the suites of a flow or a suite selector, as a plural subject for [`choose_lane`].
pub fn describe_named(selector: &Selector) -> String {
    if selector.kind == SelectorKind::Flow {
        format!("the scenarios of {}", selector.name)
    } else {
        format!("the scenarios of suite {}", selector.name)
    }
}

/// The refusal for a flow or a suite id nothing knows, with the nearest known ids.
fn unknown_id(what: &str, wanted: &str, known: Vec<&str>, hint: &str) -> Refusal {
    let mut lines: Vec<String> = suggest_names(wanted, known, 3)
        .into_iter()
        .map(|name| format!("  did you mean  {name}"))
        .collect();
    if lines.is_empty() {
        lines.push(format!("  (no similar id; {hint})"));
    }
    fail_usage(format!("No {what} named {wanted}\n{}", lines.join("\n")))
}

/// The id of every story flow with committed text, sorted, or nothing when the directory cannot be read. Only a
/// refusal reads it, and a missing directory makes that refusal say "unknown" rather than "not covered", which is
/// the safe direction.
fn committed_story_flows(runtime: &dyn Runtime, catalog: &Catalog<'_>) -> Vec<String> {
    let Ok(entries) = runtime.read_dir(&repo_file(runtime, catalog.flow_text_dir)) else {
        return Vec::new();
    };
    let mut flows: Vec<String> = entries
        .into_iter()
        .filter(|entry| !entry.is_dir)
        .filter_map(|entry| entry.name.strip_suffix(".txt").map(str::to_owned))
        .filter(|id| is_flow_id(id))
        .collect();
    flows.sort();
    flows
}

/// Settles which lane one iteration runs, or refuses in the caller's own words.
///
/// `requested_lane` is the caller's `--lane`. It is required only to settle an answer that spans two lanes. Naming
/// a lane the answer does not reach is refused rather than run as an empty selection. `subject` is a plural noun
/// phrase for what was asked, as [`CHANGED_PATHS_SUBJECT`] or [`describe_named`] spell it.
pub fn choose_lane(area: &Area, affected: &Affected, requested_lane: Option<&str>, subject: &str) -> Result<String, Refusal> {
    if let Some(requested) = requested_lane {
        if affected.lanes.iter().any(|lane| lane == requested) {
            return Ok(requested.to_owned());
        }
        if affected.lanes.is_empty() {
            return Err(no_affected_suite(affected, subject));
        }
        return Err(Refusal::new(
            "no_affected_suite",
            exit::USAGE,
            format!("no {requested} suite covers {subject}; they reach {}", affected.lanes.join(", ")),
        )
        .with_details(json!({"affected": affected})));
    }
    match affected.lanes.as_slice() {
        [] => Err(no_affected_suite(affected, subject)),
        [only] => Ok(only.clone()),
        _ => {
            let counts = lane_counts(area, affected);
            Err(Refusal::new(
                "affected_lanes_ambiguous",
                exit::USAGE,
                format!(
                    "{subject} reach {} suite(s): {}{}; one iteration runs one lane, so pass --lane with one of {}",
                    affected.suites.len(),
                    lane_counts_text(&counts),
                    coarse_answer_clause(affected),
                    counted_lane_names(&counts).join(", ")
                ),
            )
            .with_details(json!({"affected": affected})))
        }
    }
}

/// Where the answer came from, when the coarse relation alone produced it.
///
/// A caller that lands here named no path carrying a flow tag, so the answer covers whole modules and every lane
/// those modules reach, and a flow tag on the path would narrow it again. Empty for any other answer, because a
/// clause that is always there says nothing.
fn coarse_answer_clause(affected: &Affected) -> String {
    if !reached_by_module_only(affected) {
        return String::new();
    }
    format!("; every suite came from a {VIA_MODULE} endpoint, so the answer covers whole modules")
}

/// The refusal for an answer that holds no suite, generated or authored.
///
/// It names the paths with their reasons, because the next action differs by reason: a file no tag and no spec
/// links is a gap in the links, while a flow that declares no scenario is telling you no scenario exists. A named
/// directory can leave hundreds of paths, so the message names the first few, and the details carry every one.
fn no_affected_suite(affected: &Affected, subject: &str) -> Refusal {
    let mut reasons: Vec<String> = affected
        .unmapped
        .iter()
        .take(UNMAPPED_LINES_PER_REASON)
        .map(|unmapped| format!("{} ({})", unmapped.path, unmapped.reason))
        .collect();
    if affected.unmapped.len() > UNMAPPED_LINES_PER_REASON {
        reasons.push(format!("and {} more", affected.unmapped.len() - UNMAPPED_LINES_PER_REASON));
    }
    if reasons.is_empty() {
        reasons.push(subject.to_owned());
    }
    Refusal::new(
        "no_affected_suite",
        exit::USAGE,
        format!(
            "no suite covers {}; run --lane with the lane you want, or link the suite that tests these paths with \
             a @flow tag or with a [@test] link in the owning spec",
            reasons.join(", ")
        ),
    )
    .with_details(json!({"affected": affected}))
}

/// The number of reached suites one lane holds.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct LaneCount {
    pub lane: String,
    pub suites: usize,
}

impl LaneCount {
    /// The count as JSON text in field order, for a caller that links another build of `serde`.
    pub fn to_json_text(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|error| json!({"unencodable": error.to_string()}).to_string())
    }
}

/// Each reached lane with the number of suites it holds, in the declared lane order.
///
/// A subtotal is what makes a coarse answer usable: the widest module endpoints reach suites of all three lanes, so
/// a caller that must pass `--lane` needs the size of each. The counts rank nothing: an order by size would read as
/// a recommendation, and the lane is the user's choice whenever a GUI-chat suite can drive the physical pointer.
pub fn lane_counts(area: &Area, affected: &Affected) -> Vec<LaneCount> {
    area.lanes()
        .integration_lane_names()
        .into_iter()
        .filter_map(|lane| {
            let suites = classes_of_lane(affected, lane).len();
            (suites > 0).then(|| LaneCount {
                lane: lane.to_owned(),
                suites,
            })
        })
        .collect()
}

/// The subtotals as prose, as `ui 13, ui-real 1, gui-chat 5`.
pub fn lane_counts_text(counts: &[LaneCount]) -> String {
    counts
        .iter()
        .map(|count| format!("{} {}", count.lane, count.suites))
        .collect::<Vec<_>>()
        .join(", ")
}

/// The reached lanes alone, in the order [`lane_counts`] answered them: [`Affected::lanes`] sorts alphabetically,
/// and one message states both.
pub fn counted_lane_names(counts: &[LaneCount]) -> Vec<String> {
    counts.iter().map(|count| count.lane.clone()).collect()
}

/// One `include-classname=` filter per suite class of one lane.
///
/// The name goes into the pattern unquoted, so only a plain Java identifier is accepted: a `$`, a `.` or a
/// backslash would change what the pattern matches. The catalog writes identifiers, so a name that is not one is
/// corrupt, and refusing beats a filter that silently selects more or less than the suite.
pub fn suite_class_filters(classes: &[String], lane: &str) -> Result<Vec<String>, Refusal> {
    classes
        .iter()
        .map(|class| {
            if !regex!(r"^[A-Za-z_][A-Za-z0-9_]*$").is_match(class) {
                return Err(Refusal::new(
                    "affected_class_unpatternable",
                    exit::USAGE,
                    format!(
                        "the generated suite class {class} cannot be named in a JUnit filter; run --lane {lane} \
                         instead"
                    ),
                ));
            }
            Ok(format!("include-classname={}", simple_class_name_pattern(class)))
        })
        .collect()
}

/// What the host `bt` runs for a flow or a suite selector: one lane, narrowed to the classes of the suites the
/// selector names.
///
/// The lane's own spec is the target and the flags, so a flow run is `--lane <lane>` with a class filter and pins
/// the lane's one shard. The filters travel as `JB_TEST_JUNIT5_FILTERS`, which `JUnit5BazelRunner` reads with the
/// syntax the controller's daemon reads.
pub fn resolve_suite_run(
    runtime: &dyn Runtime,
    area: &Area,
    selector: &Selector,
    requested_lane: Option<&str>,
) -> Result<Resolution, Refusal> {
    let affected = named_suites(runtime, area, selector)?;
    let lane = choose_lane(area, &affected, requested_lane, &describe_named(selector))?;
    let junit5_filters = suite_class_filters(&classes_of_lane(&affected, &lane), &lane)?;
    // Every lane a suite can carry is one the table knows: the answer refuses a catalog lane it has no lane for.
    let spec = area
        .lanes()
        .get(&lane)
        .ok_or_else(|| fail_infra(format!("lane {lane} is not in the lane table")))?;
    Ok(Resolution {
        labels: spec.targets.clone(),
        junit5_filters,
        suites: affected.suites.into_iter().filter(|suite| suite.lane == lane).collect(),
        multi_target: spec.is_multi_target(),
        lane: Some(lane),
        ..Resolution::default()
    })
}

#[cfg(test)]
mod tests;
