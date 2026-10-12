//! `air-trace plan`: from something a person or an agent has in hand to the scenarios it covers, and the commands
//! that would record new traces of them.
//!
//! The input is a trace bundle, a `flow-*.txt`, a flow profile, a JUnit `test.xml`, a `vm.cmd` report, a source
//! path or file name, or a name: a class, a flow id, a suite, a scenario, or a step, operation or check id out of
//! a scenario's program. [resolve_all] is the whole of it, so the CLI and the server's planner route give the same
//! answer, and nothing here shells out to another tool of this workspace: the source-path join is
//! [avl_affected::affected_suites], the call `vm.cmd suites` makes, a class outside the generated suites is resolved by
//! the selector `vm.cmd run <Class>` uses, and the scenarios come from the committed suite documents both read.
//!
//! It resolves in-process through the code the controller already runs, so the planner and the run cannot
//! disagree about what a path or a class reaches.
//!
//! What the planner answers is deliberately commands and not a run. Recording a trace costs a VM lease or the
//! user's screen, and which of the two is the reader's call; [Command::warning] says what the host one costs.

mod catalog;
mod cli;
mod commands;
mod names;
mod read;

use std::collections::{BTreeSet, HashMap};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use avl_affected::{air_area, air_areas};
use avl_base::sync::lock;
use avl_trace_tools::discover::{Bundle, Root, RootState, Scanner, Summary};
use bt_core::catalog::{SuiteDocument, SuiteProfile};
use bt_core::{OsRuntime, ResolutionInputs};
use catalog::{Catalog, lane_of, read_catalog};

use crate::Refusal;
use serde::{Serialize, Serializer};

pub(crate) use cli::{PLAN_ABOUT, PlanArgs, run_plan};
pub(crate) use commands::render;

/// What to plan for: text typed or pasted, or a dropped file's name and bytes.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Input {
    /// A class name, a flow id, a source path or name, or the path of a file to read. Several may be given at
    /// once, separated by white space, which is what pasting `git status --short` produces.
    pub text: String,
    /// The name of a dropped file. It decides how the content is read, since a dropped file has no path the
    /// planner could open.
    pub file_name: String,
    /// A dropped file's bytes.
    pub content: Vec<u8>,
}

/// How the planner read one input.
#[derive(Serialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "lowercase")]
pub(crate) enum Kind {
    /// A trace bundle: a directory holding one, a zip of several, its `spans.jsonl` or its `bundle.json`. There
    /// is nothing to run for it; it is opened.
    Bundle,
    /// A `flow-*.txt` or a flow id.
    Flow,
    /// One generated suite document.
    Profile,
    /// A JUnit XML document, read for what failed.
    Junit,
    /// A `vm.cmd` envelope, a persisted run report or an aggregate that embeds some.
    Report,
    /// A repository source path, read the way `vm.cmd suites` reads it.
    Path,
    /// A bare name: a class, a suite, a scenario, or an id out of a program.
    Name,
}

/// The reasons this planner adds to `bt`'s own, for inputs that are not a repository path. A path's reason is
/// `bt`'s, verbatim, because the next action for it is the one `vm.cmd suites` already documents.
pub(crate) mod reason {
    /// A name that is no flow, suite, scenario, program id, test class or file.
    pub(crate) const UNKNOWN_NAME: &str = "unknown_name";
    /// A test class that exists and runs in no UI lane, so it records no trace.
    pub(crate) const NOT_A_UI_LANE_CLASS: &str = "not_a_ui_lane_class";
    /// A bare file name the checkout does not hold.
    pub(crate) const NO_SUCH_FILE: &str = "no_such_file";
    /// A zip or a directory with no bundle in it.
    pub(crate) const NO_BUNDLE: &str = "no_bundle";
    /// An input that could not be read as what it looked like, such as a report that fails its own schema.
    pub(crate) const UNREADABLE: &str = "input_unreadable";
    /// A file outside the checkout that is none of the documents the planner reads.
    pub(crate) const UNRECOGNIZED: &str = "unrecognized_file";
}

/// How one input was read, so a reader can tell "your test.xml named nothing" from "that was not read as a
/// test.xml at all".
#[derive(Serialize, Clone, PartialEq, Eq, Debug)]
pub(crate) struct Reading {
    pub given: String,
    #[serde(rename = "as")]
    pub kind: Kind,
    /// What the reading found, such as the flow id or the number of failed cases.
    #[serde(skip_serializing_if = "String::is_empty")]
    pub detail: String,
}

/// One scenario the input maps to, and the lane that runs it.
#[derive(Serialize, Clone, PartialEq, Eq, Hash, Debug)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ScenarioRef {
    pub test_class: String,
    pub scenario: String,
    /// Absent for a hand-authored journey.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    pub lane: String,
}

/// The two ways to record a lane. The VM is the default, the one the `vm-ui-tests` skill prescribes; the host one
/// is offered for a reader without a worker, and says what it costs.
#[derive(Serialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "lowercase")]
pub(crate) enum CommandKind {
    Vm,
    Host,
}

impl CommandKind {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Vm => "vm",
            Self::Host => "host",
        }
    }
}

/// One way to produce new bundles for the scenarios, as the lines a person would type.
#[derive(Serialize, Clone, PartialEq, Eq, Debug)]
pub(crate) struct Command {
    /// Names the way, so a reader can pick one without parsing its lines.
    pub kind: CommandKind,
    pub title: String,
    /// The commands, from the repository root. Each one stands alone: a VM line takes its own lease.
    pub lines: Vec<String>,
    /// What running it costs beyond time, such as a host run that takes over the screen.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub warning: Option<String>,
}

/// An input that reaches no scenario, with the reason. For a repository path it is `bt`'s unmapped path,
/// verbatim.
#[derive(Serialize, Clone, PartialEq, Eq, Debug, Default)]
pub(crate) struct Unmapped {
    pub path: String,
    pub reason: String,
    /// The flow ids a path declares, for `flow_declares_no_suite`.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub flows: Vec<String>,
    /// The JPS module of a path, for `module_not_walked`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub module: Option<String>,
    /// What a resolver said beyond its reason, such as the selector's did-you-mean.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

impl Unmapped {
    fn new(path: &str, reason: &str, detail: impl Into<String>) -> Self {
        Self {
            path: path.to_owned(),
            reason: reason.to_owned(),
            detail: Some(detail.into()),
            ..Self::default()
        }
    }
}

/// The status of an archive a report names and this machine does not hold. It is the one status the planner adds
/// to those `discover` reads a bundle with.
pub(crate) const BUNDLE_MISSING: &str = "missing";

/// A bundle the input is or names: one read the way the server lists it, or one a report names that this machine
/// does not hold, whose status then reads [BUNDLE_MISSING].
#[derive(Clone, PartialEq, Eq, Debug)]
pub(crate) struct PlannedBundle {
    pub summary: Summary,
    pub missing: bool,
}

impl PlannedBundle {
    /// The status as the answer spells it.
    pub(crate) const fn status(&self) -> &str {
        if self.missing {
            BUNDLE_MISSING
        } else {
            self.summary.status.as_str()
        }
    }
}

impl std::ops::Deref for PlannedBundle {
    type Target = Summary;

    fn deref(&self) -> &Summary {
        &self.summary
    }
}

impl Serialize for PlannedBundle {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        if !self.missing {
            return self.summary.serialize(serializer);
        }
        let mut value = serde_json::to_value(&self.summary).map_err(serde::ser::Error::custom)?;
        if let Some(fields) = value.as_object_mut() {
            fields.insert("status".to_owned(), BUNDLE_MISSING.into());
        }
        value.serialize(serializer)
    }
}

/// The plan.
#[derive(Serialize, Clone, PartialEq, Eq, Debug, Default)]
pub(crate) struct PlanResult {
    pub read: Vec<Reading>,
    /// Every lane of the scenarios and the classes, in the declared lane order.
    pub lanes: Vec<String>,
    /// Every class the commands run: the scenarios' classes and any authored class a name reached.
    pub classes: Vec<String>,
    /// What the commands record. A bundle given as input is not here: it is already recorded, and is in
    /// [PlanResult::bundles].
    pub scenarios: Vec<ScenarioRef>,
    pub commands: Vec<Command>,
    pub unmapped: Vec<Unmapped>,
    /// Every bundle the input is or names, read the way the server lists it.
    pub bundles: Vec<PlannedBundle>,
    /// The bundles on this machine that record one of the scenarios, newest first. [resolve_all] leaves it empty,
    /// and [PlanResult::join_existing] fills it from a scan: the CLI's over its roots, the server's over its
    /// listing.
    pub existing: Vec<Summary>,
    /// What a reader should know that fits no field, such as a class that runs more scenarios than were asked for.
    pub notes: Vec<String>,
}

impl PlanResult {
    /// Fills [PlanResult::existing] with the bundles among found that record one of the scenarios, newest first. A
    /// scenario matches by its test class and name, as written or as the recorder sanitizes them into a path,
    /// since a bundle without a manifest knows its test class only from its directory.
    pub(crate) fn join_existing<'a>(&mut self, found: impl IntoIterator<Item = &'a Bundle>) {
        let mut existing: Vec<Summary> = found
            .into_iter()
            .filter(|bundle| {
                self.scenarios.iter().any(|reference| {
                    same_name(&bundle.test_class, &reference.test_class) && same_name(&bundle.scenario, &reference.scenario)
                })
            })
            .map(|bundle| bundle.summary.clone())
            .collect();
        existing.sort_by(|left, right| right.started_at.cmp(&left.started_at));
        self.existing = existing;
    }
}

fn same_name(found: &str, named: &str) -> bool {
    found == named || found == avl_trace::bundle::sanitize_name(named)
}

/// Plans for one input against the checkout at repo_root.
pub(crate) fn resolve(repo_root: &Path, input: Input) -> anyhow::Result<PlanResult> {
    resolve_all(repo_root, vec![input])
}

/// Plans for several inputs at once, as one answer.
///
/// One answer and not one per input, because the paths among them are joined together: `vm.cmd run --changed`
/// takes every path in one iteration, and planning them one by one would print one run per path.
pub(crate) fn resolve_all(repo_root: &Path, inputs: Vec<Input>) -> anyhow::Result<PlanResult> {
    let notes = BtNotes::default();
    let runtime = bt_runtime(repo_root, notes.clone());
    let selectors = ResolutionInputs::new(&runtime, air_areas());
    Planner::new(repo_root, &runtime, &selectors, notes, Box::new(names::git_list_files)).plan(inputs)
}

/// The runtime the planner resolves through: [OsRuntime::resolver], the controller's, with what `bt` would print
/// going to the notes rather than to a stderr the server's planner route does not have.
pub(crate) fn bt_runtime(repo_root: &Path, notes: BtNotes) -> OsRuntime {
    OsRuntime::resolver(repo_root, move |text| notes.push(text))
}

/// A lane's position among the UI lanes, which is the order every list this planner answers is grouped in. An
/// unknown lane sorts last.
fn lane_order(lane: &str) -> usize {
    let lanes = avl_affected::ui_lane_names();
    lanes.iter().position(|known| *known == lane).unwrap_or(lanes.len())
}

/// What `bt` said while resolving, which becomes notes rather than going to a stderr the server route does not
/// have.
#[derive(Clone, Default)]
pub(crate) struct BtNotes(Arc<Mutex<String>>);

impl BtNotes {
    fn push(&self, text: &str) {
        let mut notes = lock(&self.0);
        notes.push_str("bt: ");
        notes.push_str(text.trim_end_matches('\n'));
        notes.push('\n');
    }

    fn lines(&self) -> Vec<String> {
        let notes = lock(&self.0);
        notes.trim().lines().filter(|line| !line.is_empty()).map(str::to_owned).collect()
    }
}

/// Answers the tracked files with a base name, repository-relative: `git ls-files` in production, a test's own
/// list in a test.
pub(crate) type ListFiles<'r> = Box<dyn Fn(&Path, &str) -> Result<Vec<String>, Refusal> + 'r>;

/// One resolution: the checkout, the lazily read catalog, `bt`'s runtime and selector inputs, and what the inputs
/// have added so far.
pub(crate) struct Planner<'r> {
    repo_root: PathBuf,
    list_files: ListFiles<'r>,
    runtime: &'r OsRuntime,
    selectors: &'r ResolutionInputs<'r>,
    bt_notes: BtNotes,
    loaded: Option<Result<Arc<Catalog>, Refusal>>,
    /// Reads the bundles an input on disk holds; made on the first such input.
    scanner: Option<Scanner>,

    result: PlanResult,
    /// Orders a scenario within its class by its position in the suite document.
    scenario_rank: HashMap<ScenarioRef, usize>,
    /// Every class to run, with its lane.
    class_lanes: HashMap<String, String>,
    /// Every repository path to join through `bt`, in the order given.
    paths: Vec<String>,
    /// The paths that reached a suite, and the classes they reached per lane: what one `run --changed` line covers.
    changed: Vec<String>,
    changed_classes: HashMap<String, BTreeSet<String>>,
    changed_lanes: usize,
}

impl<'r> Planner<'r> {
    pub(crate) fn new(
        repo_root: &Path,
        runtime: &'r OsRuntime,
        selectors: &'r ResolutionInputs<'r>,
        bt_notes: BtNotes,
        list_files: ListFiles<'r>,
    ) -> Self {
        Planner {
            repo_root: repo_root.to_path_buf(),
            list_files,
            runtime,
            selectors,
            bt_notes,
            loaded: None,
            scanner: None,
            result: PlanResult::default(),
            scenario_rank: HashMap::new(),
            class_lanes: HashMap::new(),
            paths: Vec::new(),
            changed: Vec::new(),
            changed_classes: HashMap::new(),
            changed_lanes: 0,
        }
    }

    /// The committed suite documents, read once, on the first input that needs them. A bundle alone never does,
    /// so a bundle can be planned in a checkout without them.
    fn catalog(&mut self) -> anyhow::Result<Arc<Catalog>> {
        let loaded = self.loaded.get_or_insert_with(|| read_catalog(self.runtime).map(Arc::new));
        loaded.clone().map_err(anyhow::Error::new)
    }

    pub(crate) fn plan(mut self, inputs: Vec<Input>) -> anyhow::Result<PlanResult> {
        for input in inputs {
            if !input.content.is_empty() || (!input.file_name.is_empty() && input.text.trim().is_empty()) {
                let given = if input.file_name.is_empty() {
                    "<stdin>".to_owned()
                } else {
                    input.file_name.clone()
                };
                self.read_source(&read::Source {
                    given,
                    name: base_name(&input.file_name),
                    path: None,
                    content: input.content,
                })?;
            } else {
                self.read_text(&input.text)?;
            }
        }
        self.join_paths()?;
        self.finish();
        Ok(self.result)
    }

    // --- what the readers add ------------------------------------------------------------------------------

    fn read(&mut self, given: &str, kind: Kind, detail: impl Into<String>) {
        self.result.read.push(Reading {
            given: given.to_owned(),
            kind,
            detail: detail.into(),
        });
    }

    fn unmapped(&mut self, entry: Unmapped) {
        let known = self
            .result
            .unmapped
            .iter()
            .any(|known| known.path == entry.path && known.reason == entry.reason);
        if !known {
            self.result.unmapped.push(entry);
        }
    }

    fn note(&mut self, line: impl Into<String>) {
        let line = line.into();
        if !self.result.notes.contains(&line) {
            self.result.notes.push(line);
        }
    }

    /// Plans one scenario of one suite.
    fn add_scenario(&mut self, owner: &SuiteDocument, scenario: &SuiteProfile) {
        let reference = ScenarioRef {
            test_class: owner.test_class_name.clone(),
            scenario: scenario.name.clone(),
            flow: Some(scenario.flow.clone()).filter(|flow| !flow.is_empty()),
            lane: lane_of(owner).to_owned(),
        };
        if self.scenario_rank.contains_key(&reference) {
            return;
        }
        let rank = owner
            .profiles
            .iter()
            .position(|candidate| candidate.name == scenario.name)
            .unwrap_or(usize::MAX);
        self.scenario_rank.insert(reference.clone(), rank);
        self.result.scenarios.push(reference);
        self.class_lanes.insert(owner.test_class_name.clone(), lane_of(owner).to_owned());
    }

    /// Plans every matched scenario and answers how many there were.
    fn add_matches(&mut self, matches: &[(&SuiteDocument, &SuiteProfile)]) -> usize {
        for (owner, scenario) in matches {
            self.add_scenario(owner, scenario);
        }
        matches.len()
    }

    /// Plans a class whose scenarios the catalog does not declare, which is an authored journey.
    fn add_class(&mut self, class_name: &str, lane: &str) {
        self.class_lanes.insert(class_name.to_owned(), lane.to_owned());
    }

    fn add_bundle(&mut self, bundle: PlannedBundle) {
        if !self.result.bundles.iter().any(|known| known.id == bundle.id) {
            self.result.bundles.push(bundle);
        }
    }

    // --- the source-path join ------------------------------------------------------------------------------

    /// Joins every repository path the inputs named in one `bt` call, which is exactly what `vm.cmd suites
    /// <path>...` answers, and plans every scenario of every suite it reaches.
    ///
    /// `bt` answers per suite, and a suite runs all of its scenarios, so every scenario of a reached suite is
    /// planned. Its unmapped paths are passed through with their reason, verbatim.
    fn join_paths(&mut self) -> anyhow::Result<()> {
        if self.paths.is_empty() {
            return Ok(());
        }
        let loaded = self.catalog()?;
        let affected = avl_affected::affected_suites(self.runtime, air_area(), &self.paths)?;
        let mut unmapped_paths = BTreeSet::new();
        for entry in &affected.unmapped {
            unmapped_paths.insert(entry.path.clone());
            self.unmapped(Unmapped {
                path: entry.path.clone(),
                reason: entry.reason.clone(),
                flows: entry.flows.clone(),
                module: entry.module.clone(),
                detail: None,
            });
        }
        let mut authored = Vec::new();
        for reached in &affected.suites {
            if reached.authored {
                // An authored suite has no scenario document, so it is planned as a class, the way a name that
                // reaches an authored journey is. Its command is its own `run <Class>`, because the class is left
                // out of what the `run --changed` line covers.
                self.add_class(&reached.class, &reached.lane);
                authored.push(reached.class.clone());
                continue;
            }
            let Some(owner) = loaded.suite_by_id(&reached.suite) else {
                // `bt` read the same directory a moment ago, so this is a document that changed in between.
                anyhow::bail!(
                    "bt reached the suite {}, which the planner's read of {} does not hold",
                    reached.suite,
                    air_area().catalog()?.flow_profile_dir
                );
            };
            for scenario in &owner.profiles {
                self.add_scenario(owner, scenario);
            }
            self.changed_classes
                .entry(reached.lane.clone())
                .or_default()
                .insert(reached.class.clone());
        }
        if !authored.is_empty() {
            self.note(format!(
                "the paths also reach the authored suite class(es) {}, which have no generated profile; each one is \
                 planned as a class with its own run",
                authored.join(", ")
            ));
        }
        self.changed_lanes = affected.lanes.len();
        for path in &self.paths {
            let slashed = path.replace('\\', "/");
            let normalized = slashed.strip_prefix("./").unwrap_or(&slashed).to_owned();
            if !unmapped_paths.contains(&normalized) && !affected.suites.is_empty() {
                self.changed.push(normalized);
            }
        }
        if bt_core::reached_by_module_only(&affected) {
            self.note(format!(
                "every suite came from a {} endpoint, so the answer covers whole modules; a @flow tag on the file \
                 would narrow it",
                bt_core::VIA_MODULE.trim_end_matches(':')
            ));
        }
        Ok(())
    }

    // --- the answer ----------------------------------------------------------------------------------------

    /// Orders what the inputs added and derives the lanes, the classes and the commands.
    fn finish(&mut self) {
        let rank = &self.scenario_rank;
        self.result.scenarios.sort_by(|left, right| {
            lane_order(&left.lane)
                .cmp(&lane_order(&right.lane))
                .then_with(|| left.test_class.cmp(&right.test_class))
                .then_with(|| rank.get(left).cmp(&rank.get(right)))
        });
        let class_lanes = &self.class_lanes;
        let mut classes: Vec<String> = class_lanes.keys().cloned().collect();
        classes.sort_by(|left, right| {
            lane_order(&class_lanes[left])
                .cmp(&lane_order(&class_lanes[right]))
                .then_with(|| left.cmp(right))
        });
        self.result.lanes = avl_affected::ui_lane_names()
            .into_iter()
            .filter(|lane| classes.iter().any(|class| class_lanes[class].as_str() == *lane))
            .map(str::to_owned)
            .collect();
        self.result.classes = classes;
        self.note_wider_classes();
        self.result.commands = self.commands();
        for line in self.bt_notes.lines() {
            self.note(line);
        }
        if !self.result.bundles.is_empty() {
            self.note(
                "open a bundle in the trace viewer: ./community/tools/trace.cmd serve, then drop it on the Runs \
                 page",
            );
        }
    }

    /// Says so when a class runs more scenarios than the input asked for. `run` and BT select a class, and a
    /// scenario is a dynamic node of it that neither can name, so the other scenarios record too.
    fn note_wider_classes(&mut self) {
        let Some(Ok(loaded)) = self.loaded.clone() else {
            return;
        };
        let mut asked: HashMap<&str, usize> = HashMap::new();
        for reference in &self.result.scenarios {
            *asked.entry(reference.test_class.as_str()).or_default() += 1;
        }
        let mut notes = Vec::new();
        for class_name in &self.result.classes {
            let Some(owner) = loaded.by_class(class_name) else {
                continue;
            };
            let asked = asked.get(class_name.as_str()).copied().unwrap_or(0);
            let declared = owner.profiles.len();
            if asked == 0 || asked >= declared {
                continue;
            }
            notes.push(format!(
                "{class_name} runs {declared} scenarios and {asked} of them were asked for; a run selects the whole \
                 class, so the other {} record traces too",
                declared - asked
            ));
        }
        for note in notes {
            self.note(note);
        }
    }
}

/// A path's last segment, `/`- or `\`-separated, or "" for none.
fn base_name(path: &str) -> String {
    path.trim_end_matches(['/', '\\'])
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or_default()
        .to_owned()
}

/// What a scan could not read, or "" when it read everything.
fn unreadable(state: &RootState) -> String {
    state.error.iter().chain(&state.problems).cloned().collect::<Vec<_>>().join("; ")
}

/// Scans roots for the bundles of the result's scenarios, and notes a root it could not read in full, because a
/// bundle missing from the answer for that reason must not read as one never recorded.
pub(crate) fn join_on_disk(result: &mut PlanResult, roots: &[Root]) {
    if result.scenarios.is_empty() || roots.is_empty() {
        return;
    }
    let scanner = Scanner::new(avl_trace_tools::discover::Options::default());
    let found = scanner.scan(roots);
    scanner.close();
    result.join_existing(found.bundles.iter().map(|bundle| &**bundle));
    for state in &found.roots {
        let problem = unreadable(state);
        if problem.is_empty() {
            continue;
        }
        let mut place = format!("the {} root", state.kind);
        if !state.path.as_os_str().is_empty() {
            place.push_str(&format!(" {}", state.path.display()));
        }
        result.notes.push(format!("{place} could not be read in full: {problem}"));
    }
}

#[cfg(test)]
mod tests;
