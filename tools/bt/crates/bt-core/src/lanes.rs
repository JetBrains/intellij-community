//! The lane table of an area, and the bazel command line a run becomes.
//!
//! The lane table is data, in a JSON file an area names in `bt.json`, because it has readers outside Rust: a
//! product's own tests can compare it with the BUILD files. See [`Lanes`] for the shape.

use std::collections::{BTreeMap, BTreeSet};
use std::ffi::OsString;
use std::path::Path;

use refusal::Refusal;
use serde::{Deserialize, Deserializer};

use crate::areas::Areas;
use crate::exit::fail_infra;
use crate::runtime::{Platform, Runtime, repo_file};
use crate::selector::Resolution;
use crate::shard_plan::{PLANNED_SHARDS, SHARD_PLAN_ENV};

/// One lane table, a flat object. Only `lanes` is required:
///
/// - `lanes`: every lane in declared order, which is the order a caller sees in the "Known lanes" refusal. Each
///   is a [`LaneSpec`]. An IDE-launching lane also carries its catalog name and its test label, which the Air
///   UI-lane controller and its trace planner read instead of a table of their own.
/// - `tags`: every Bazel tag a test target of the area may carry, each a [`TagSpec`]. It is the one description of
///   them. The area's own tests hold the BUILD files to it, and [`Lanes::parse`] refuses a table whose lanes and
///   tags disagree. The categories with an exclusion reason, in table order, are what a lane with all of them
///   excludes. A multi-target run that the caller spelled out excludes them except the `fast-lane` ones. The CI
///   exclusion of the area should be the `ci` ones, so it stays correct when a lane is added. Adding a
///   `dedicated-suite` category before its build configuration exists would leave the suite running nowhere.
/// - `integrationRoot`: the target pattern prefix of the integration tree. A multi-target run the caller spelled
///   out inside it is asking for exactly those tests, so [`wildcard_guards`] keeps their tags.
/// - `excludedTestLibs`: the `*_test_lib` library behind each target a broad run excludes, which a lane with
///   `buildsExcludedTestLibs` compiles in a second spawn. The library and not the `*_test` rule: an IDE-launching
///   rule declares the assembled dev distribution as `data`, and building the rule recomposes it. A tag filter
///   cannot select a library, so the area's own test keeps this list equal to the tagged targets.
/// - The suite catalog, both or none ([`Catalog`]): `flowProfileDir`, where the flow catalog generator writes
///   one document per generated suite; `flowTextDir`, where the flow text generator writes one plain-text rendering
///   per story flow.
/// - `specDir`, `laneWideModules`, `laneWidePaths` and `declarationReach`: what the Air UI-lane controller joins a
///   changed path through. `specDir` is where the specs live. `laneWideModules` names every JPS module that holds the
///   lane harness, with the lanes it serves (`[]` is every lane). `laneWidePaths` names every harness file or
///   directory that no module of the area holds, by its repo-relative path, in the same shape. `declarationReach`
///   names the harness modules whose Kotlin sources the controller joins by declaration, each a [`DeclarationReach`].
///   This crate reads none of them.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Lanes {
    #[serde(default)]
    flow_profile_dir: Option<String>,
    #[serde(default)]
    flow_text_dir: Option<String>,
    #[serde(default)]
    spec_dir: Option<String>,
    #[serde(default)]
    integration_root: Option<String>,
    #[serde(default)]
    tags: Vec<TagSpec>,
    #[serde(default)]
    excluded_test_libs: Vec<String>,
    #[serde(default)]
    lane_wide_modules: BTreeMap<String, Vec<String>>,
    #[serde(default)]
    lane_wide_paths: BTreeMap<String, Vec<String>>,
    #[serde(default)]
    declaration_reach: BTreeMap<String, DeclarationReach>,
    lanes: Vec<LaneSpec>,
}

/// A harness module whose Kotlin sources the Air UI-lane controller joins by declaration: a changed declaration reaches
/// the suites whose requests reach it, and only a declaration that every suite reaches is lane-wide. All paths are
/// repo-relative.
#[derive(Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct DeclarationReach {
    /// The source directories of the module, and those of the modules that call into it inside the IDE.
    pub sources: Vec<String>,
    /// The module descriptors whose classes the IDE creates by itself, such as a service or an HTTP handler.
    pub descriptors: Vec<String>,
    /// The simple name of the sealed type of every request that the server decodes.
    pub request_type: String,
    /// The flow vocabulary file of the module, which the controller joins by its entries.
    #[serde(default)]
    pub vocabulary: Option<String>,
}

/// The suite catalog of a lane table: the two paths the suite join reads, both repo-relative.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Catalog<'a> {
    pub flow_profile_dir: &'a str,
    pub flow_text_dir: &'a str,
}

/// One lane: the targets it runs and the flags that are its definition.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LaneSpec {
    pub name: String,
    /// One line for `bt --help`, which lists the lanes of every area.
    #[serde(default)]
    pub description: Option<String>,
    /// One target or pattern, or a list of them: `"target"` holds a string or an array.
    #[serde(rename = "target", deserialize_with = "one_or_many")]
    pub targets: Vec<String>,
    pub extra: Vec<String>,
    /// The count this lane must run with when the throughput default is wrong for it. `None` is "no opinion",
    /// which is different from 1.
    ///
    /// An IDE-launching lane is one execution slot: each shard is another test JVM and another IDE. Flow lanes
    /// rely on one shared IDE, while GUI-chat may also own the physical pointer. A VM lease serialises *callers*
    /// and cannot see a shard split inside one invocation, so these lanes pin one shard explicitly.
    #[serde(default)]
    pub shards: Option<u32>,
    /// The Bazel tag that marks this lane's one target, for an IDE-launching lane. The module holding a lane's
    /// test classes *is* the lane, so a lane tag plus `--test_tag_filters` is the whole mechanism, with no
    /// `JB_TEST_JUNIT5_FILTERS` anywhere.
    #[serde(default)]
    pub integration_tag: Option<String>,
    /// The flow catalog's name for this lane (`UI_REAL`), for an IDE-launching lane.
    #[serde(default)]
    pub catalog_lane: Option<String>,
    /// The flow catalog's name for the suites of an explicit-only lane (`UI_PERF`). A suite document of this lane
    /// belongs to the lane, and no changed path, flow or suite id reaches it (ADR 0200). A lane states this or
    /// [`LaneSpec::catalog_lane`], never both.
    #[serde(default)]
    pub document_lane: Option<String>,
    /// The one test target of an IDE-launching lane, for a caller that has a resolved label and asks whose lane
    /// it is.
    #[serde(default)]
    pub test_label: Option<String>,
    /// Whether the lane compiles `excludedTestLibs` in a second spawn; see [`LaneSpec::build_only`].
    #[serde(default)]
    builds_excluded_test_libs: bool,
}

/// One Bazel tag of the area's test targets, as the lane table describes it.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct TagSpec {
    pub name: String,
    pub axis: TagAxis,
    /// Why a broad run excludes the targets that carry the tag, or `None` when it runs them. A category only: the
    /// category of a lane tag carries the exclusion.
    #[serde(default)]
    pub excluded_from_broad_runs: Option<BroadRunExclusion>,
    /// The category that every target of a lane tag also carries. A lane tag only.
    #[serde(default)]
    pub category: Option<String>,
    /// One or two sentences for a reader of the table.
    pub description: String,
}

/// The two axes of the tags. A target carries exactly one category, and a lane tag when an IDE-launching lane owns it.
#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum TagAxis {
    /// What the target is, and whether a broad run excludes it.
    Category,
    /// Which IDE-launching lane the target is. The only thing a lane selects by.
    Lane,
}

/// Why a broad run excludes a category.
#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum BroadRunExclusion {
    /// The `testTagFilters` of the CI configurations name the tag. That configuration lives outside the repository,
    /// so a target of the category cannot run on a CI agent of the main configuration.
    Ci,
    /// A lane and a CI configuration of its own run the targets.
    DedicatedSuite,
    /// Only the lanes that name the tag exclude the targets, for their cost. A multi-target run that the caller spelled
    /// out and the CI configurations run them, so a directory selector still runs what the default loop leaves out.
    FastLane,
}

/// A string or an array of strings, as a list.
fn one_or_many<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Vec<String>, D::Error> {
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum OneOrMany {
        One(String),
        Many(Vec<String>),
    }
    let targets = match OneOrMany::deserialize(deserializer)? {
        OneOrMany::One(target) => vec![target],
        OneOrMany::Many(targets) => targets,
    };
    if targets.is_empty() {
        return Err(serde::de::Error::custom("a lane names at least one target"));
    }
    Ok(targets)
}

impl Lanes {
    /// Parses a lane table, and answers why it is not one otherwise.
    pub fn parse(text: &str) -> Result<Self, String> {
        let lanes: Self = serde_json::from_str(text).map_err(|error| error.to_string())?;
        if lanes.flow_profile_dir.is_some() != lanes.flow_text_dir.is_some() {
            return Err("a suite catalog names flowProfileDir and flowTextDir together".to_owned());
        }
        lanes.check_tags()?;
        lanes.check_document_lanes()?;
        if let Some(module) = lanes
            .declaration_reach
            .keys()
            .find(|module| !lanes.lane_wide_modules.contains_key(*module))
        {
            return Err(format!(
                "declarationReach names {module}, which laneWideModules does not name: the reach narrows a harness module"
            ));
        }
        Ok(lanes)
    }

    /// Refuses a lane that is reached and explicit-only at once, a document lane of a lane that launches no IDE, and a
    /// suite lane that two lanes name.
    fn check_document_lanes(&self) -> Result<(), String> {
        let mut named = BTreeSet::new();
        for lane in &self.lanes {
            if let Some(document_lane) = &lane.document_lane {
                if lane.catalog_lane.is_some() {
                    return Err(format!(
                        "the lane {} states catalogLane and documentLane; a lane is reached or explicit-only, never both",
                        lane.name
                    ));
                }
                if lane.integration_tag.is_none() {
                    return Err(format!(
                        "the lane {} states the documentLane {document_lane} and launches no IDE",
                        lane.name
                    ));
                }
            }
            for suite_lane in lane.catalog_lane.iter().chain(&lane.document_lane) {
                if !named.insert(suite_lane.as_str()) {
                    return Err(format!("two lanes name the suite lane {suite_lane}"));
                }
            }
        }
        Ok(())
    }

    /// The tags and the lanes agree: each tag is described once, a lane tag names a described category and a lane
    /// that selects by it, and a lane selects by a described lane tag.
    fn check_tags(&self) -> Result<(), String> {
        let mut names = BTreeSet::new();
        for tag in &self.tags {
            if !names.insert(tag.name.as_str()) {
                return Err(format!("the tag {} is described twice", tag.name));
            }
            match tag.axis {
                TagAxis::Category => {
                    if tag.category.is_some() {
                        return Err(format!("the category {} names a category of its own", tag.name));
                    }
                }
                TagAxis::Lane => {
                    let category = tag
                        .category
                        .as_deref()
                        .ok_or_else(|| format!("the lane tag {} names no category", tag.name))?;
                    if !self.category_tags().any(|spec| spec.name == category) {
                        return Err(format!(
                            "the lane tag {} names the category {category}, which the table does not describe",
                            tag.name
                        ));
                    }
                    if tag.excluded_from_broad_runs.is_some() {
                        return Err(format!(
                            "the lane tag {} carries an exclusion; its category {category} carries it",
                            tag.name
                        ));
                    }
                    if !self.lanes.iter().any(|lane| lane.integration_tag.as_deref() == Some(&tag.name)) {
                        return Err(format!("the lane tag {} selects no lane", tag.name));
                    }
                }
            }
        }
        for lane in &self.lanes {
            if let Some(tag) = &lane.integration_tag
                && !self.lane_tags().any(|spec| &spec.name == tag)
            {
                return Err(format!(
                    "the lane {} selects by {tag}, which the table describes as no lane tag",
                    lane.name
                ));
            }
        }
        Ok(())
    }

    /// Reads the lane table at a repo-relative path.
    pub fn load(runtime: &dyn Runtime, path: &str) -> Result<Self, Refusal> {
        let text = runtime
            .read_text_file(&repo_file(runtime, path))
            .map_err(|error| fail_infra(format!("the lane table {path} is unreadable: {error}")))?;
        Self::parse(&text).map_err(|reason| fail_infra(format!("the lane table {path} is malformed: {reason}")))
    }

    pub fn get(&self, name: &str) -> Option<&LaneSpec> {
        self.lanes.iter().find(|lane| lane.name == name)
    }

    /// Every lane in declared order.
    pub fn iter(&self) -> impl Iterator<Item = &LaneSpec> {
        self.lanes.iter()
    }

    /// Every lane name in declared order.
    pub fn names(&self) -> impl Iterator<Item = &str> {
        self.lanes.iter().map(|lane| lane.name.as_str())
    }

    /// Every lane that launches an IDE, in declared order: the lanes with an integration tag.
    pub fn integration_lane_names(&self) -> Vec<&str> {
        self.lanes
            .iter()
            .filter(|lane| lane.integration_tag.is_some())
            .map(|lane| lane.name.as_str())
            .collect()
    }

    /// The lane the flow catalog calls `UI`, `UI_REAL` or `GUI_CHAT`.
    pub fn by_catalog_lane(&self, catalog_lane: &str) -> Option<&LaneSpec> {
        self.lanes.iter().find(|lane| lane.catalog_lane.as_deref() == Some(catalog_lane))
    }

    /// The lane of a suite document whose lane is `suite_lane`: a catalog lane, or the document lane of an
    /// explicit-only lane.
    pub fn by_suite_lane(&self, suite_lane: &str) -> Option<&LaneSpec> {
        self.lanes
            .iter()
            .find(|lane| lane.catalog_lane.as_deref() == Some(suite_lane) || lane.document_lane.as_deref() == Some(suite_lane))
    }

    /// Every catalog lane name, sorted.
    pub fn catalog_lane_names(&self) -> Vec<&str> {
        let mut names: Vec<&str> = self.lanes.iter().filter_map(|lane| lane.catalog_lane.as_deref()).collect();
        names.sort_unstable();
        names
    }

    /// The suite catalog, or `None` for a table that names none.
    pub fn catalog(&self) -> Option<Catalog<'_>> {
        Some(Catalog {
            flow_profile_dir: self.flow_profile_dir.as_deref()?,
            flow_text_dir: self.flow_text_dir.as_deref()?,
        })
    }

    pub fn spec_dir(&self) -> Option<&str> {
        self.spec_dir.as_deref()
    }

    pub fn integration_root(&self) -> Option<&str> {
        self.integration_root.as_deref()
    }

    /// Every tag a test target of the area may carry, in table order.
    pub fn tags(&self) -> &[TagSpec] {
        &self.tags
    }

    /// The tag of this name, or `None` for one the table does not describe.
    pub fn tag(&self, name: &str) -> Option<&TagSpec> {
        self.tags.iter().find(|tag| tag.name == name)
    }

    /// The category axis, in table order.
    pub fn category_tags(&self) -> impl Iterator<Item = &TagSpec> {
        self.tags.iter().filter(|tag| tag.axis == TagAxis::Category)
    }

    /// The lane axis, in table order.
    pub fn lane_tags(&self) -> impl Iterator<Item = &TagSpec> {
        self.tags.iter().filter(|tag| tag.axis == TagAxis::Lane)
    }

    pub fn excluded_test_libs(&self) -> &[String] {
        &self.excluded_test_libs
    }

    /// The lanes a harness module serves, `[]` meaning every lane, or `None` when the module is not the harness.
    /// The module name must match exactly: the dotted-prefix rule of the module relation would make a parent module
    /// cover every test module under it, and those run in no UI lane.
    pub fn lane_wide_lanes(&self, module: &str) -> Option<&[String]> {
        self.lane_wide_modules.get(module).map(Vec::as_slice)
    }

    pub fn lane_wide_modules(&self) -> impl Iterator<Item = (&str, &[String])> {
        self.lane_wide_modules
            .iter()
            .map(|(module, lanes)| (module.as_str(), lanes.as_slice()))
    }

    /// The harness directory that holds a repo-relative path, with the lanes it serves (`[]` meaning every lane), or
    /// `None` when no harness directory holds the path. A directory holds itself and every path under it. When two
    /// directories hold the path, the longer one answers.
    pub fn lane_wide_path_lanes(&self, path: &str) -> Option<(&str, &[String])> {
        self.lane_wide_paths()
            .filter(|(directory, _)| {
                path.strip_prefix(directory)
                    .is_some_and(|rest| rest.is_empty() || rest.starts_with('/'))
            })
            .max_by_key(|(directory, _)| directory.len())
    }

    /// How the controller joins the Kotlin sources of a harness module by declaration, or `None` when every path of
    /// the module is lane-wide.
    pub fn declaration_reach(&self, module: &str) -> Option<&DeclarationReach> {
        self.declaration_reach.get(module)
    }

    pub fn lane_wide_paths(&self) -> impl Iterator<Item = (&str, &[String])> {
        self.lane_wide_paths
            .iter()
            .map(|(directory, lanes)| (directory.as_str(), lanes.as_slice()))
    }

    /// The tags a broad local run excludes: the categories with an exclusion reason, in table order.
    fn broad_run_excluded_tags(&self) -> impl Iterator<Item = &String> {
        self.category_tags()
            .filter(|tag| tag.excluded_from_broad_runs.is_some())
            .map(|tag| &tag.name)
    }

    /// The tags [`wildcard_guards`] excludes: the categories with an exclusion reason other than
    /// [`BroadRunExclusion::FastLane`], in table order.
    fn wildcard_excluded_tags(&self) -> impl Iterator<Item = &String> {
        self.category_tags()
            .filter(|tag| {
                tag.excluded_from_broad_runs
                    .is_some_and(|exclusion| exclusion != BroadRunExclusion::FastLane)
            })
            .map(|tag| &tag.name)
    }

    /// The one `--test_tag_filters` value a broad local run carries.
    ///
    /// One value rather than one flag per axis: bazel's `--test_tag_filters` is last-wins, so a second occurrence
    /// would silently replace the first instead of adding to it.
    pub fn broad_run_exclusions(&self) -> String {
        tag_exclusions(self.broad_run_excluded_tags())
    }
}

fn tag_exclusions<'t>(tags: impl Iterator<Item = &'t String>) -> String {
    let negated: Vec<String> = tags.map(|tag| format!("-{tag}")).collect();
    format!("--test_tag_filters={}", negated.join(","))
}

impl LaneSpec {
    /// The labels of a second bazel spawn that compiles what this lane excludes from its run.
    ///
    /// `--build_tests_only` builds only the test targets bazel is going to run, so a `--test_tag_filters` exclusion
    /// drops those targets from the *build* as well. `--lane fast` therefore reported green while `ui_test` was
    /// never compiled, and the documented remedy was a second command nobody remembered.
    ///
    /// Only a lane carries this. A wildcard pattern the caller spelled out gets [`wildcard_guards`], which exists
    /// to keep such a run safe rather than complete.
    pub fn build_only<'l>(&self, lanes: &'l Lanes) -> &'l [String] {
        if self.builds_excluded_test_libs {
            lanes.excluded_test_libs()
        } else {
            &[]
        }
    }

    /// The argv of the lane's extra `bazel build`, or `None` when the lane needs none. The labels are the whole
    /// target list: the lane's own target is not appended, because a library is named outright and a wildcard
    /// would drag the test rules back in.
    pub fn build_only_args(&self, lanes: &Lanes) -> Option<Vec<String>> {
        let labels = self.build_only(lanes);
        if labels.is_empty() {
            return None;
        }
        Some(std::iter::once("build".to_owned()).chain(labels.iter().cloned()).collect())
    }

    /// Whether the run can hit more than one test target: the lane names several, or a wildcard pattern.
    pub fn is_multi_target(&self) -> bool {
        self.targets.len() > 1 || self.targets.iter().any(|target| target.ends_with("/..."))
    }
}

/// What `--lane fast` adds, for a multi-target run the caller spelled out themselves.
///
/// Without it a pattern over an area builds every non-test target in the subtree and then launches real IDEs
/// through IDE Starter, which also wants the network: one keystroke from the safe command.
///
/// Every area adds the tags its broad runs exclude, joined into one `--test_tag_filters`. A `fast-lane` category is not
/// one of them: a directory run is how a caller runs the targets that the fast lane leaves out. A pattern rooted inside
/// an area's integration tree is asking for exactly those tests, so that area keeps its tags. Either way
/// `-- --test_tag_filters=…` still wins, because passthrough args land last. An empty list is guarded, which is the
/// safe reading.
pub fn wildcard_guards(areas: &Areas, labels: &[String]) -> Vec<String> {
    let mut tags: Vec<&String> = Vec::new();
    for area in areas.iter() {
        let lanes = area.lanes();
        let in_integration = !labels.is_empty()
            && lanes
                .integration_root()
                .is_some_and(|root| labels.iter().all(|label| label.starts_with(root)));
        if in_integration {
            continue;
        }
        for tag in lanes.wildcard_excluded_tags() {
            if !tags.contains(&tag) {
                tags.push(tag);
            }
        }
    }
    if tags.is_empty() {
        return Vec::new();
    }
    vec!["--build_tests_only".to_owned(), tag_exclusions(tags.into_iter())]
}

/// The argv that runs the repository's bazel wrapper.
///
/// `bazel.cmd` is the repository's shell/batch hybrid: its first line is the `:<<"::CMDLITERAL"` heredoc trick, so
/// despite the executable bit it has no shebang and `posix_spawn` rejects it with ENOEXEC. A shell must interpret
/// it, which is exactly what happens when it is typed at a prompt.
///
/// On Windows `std::process::Command` quotes each argument with the MSVC rules and `cmd.exe /c` re-parses the
/// line, so an argument holding `&|^` or a quote could break. Today's arguments are flag-like; a lane that ever
/// passes such a value needs `CommandExt::raw_arg`.
pub fn bazel_command(repo_root: &Path, platform: Platform, args: &[String]) -> Vec<OsString> {
    let (head, separator): (&[&str], char) = match platform {
        Platform::Windows => (&["cmd.exe", "/c"], '\\'),
        Platform::Darwin | Platform::Linux => (&["/bin/sh"], '/'),
    };
    let mut wrapper = repo_root.as_os_str().to_owned();
    let root = repo_root.to_string_lossy();
    if !root.ends_with(['/', '\\']) {
        wrapper.push(separator.to_string());
    }
    wrapper.push("bazel.cmd");
    head.iter()
        .map(OsString::from)
        .chain(std::iter::once(wrapper))
        .chain(args.iter().map(OsString::from))
        .collect()
}

/// Everything the argv builder needs, decided before bazel is spawned.
#[derive(Clone, Debug, Default)]
pub struct RunPlan {
    pub resolution: Resolution,
    /// The lane's own flags, or [`wildcard_guards`] for a multi-target run spelled out by hand.
    pub extra: Vec<String>,
    pub shards: u32,
    pub bep_path: String,
    /// False when `bep_path` came from the caller's passthrough, and so is not ours to delete.
    pub owns_bep_file: bool,
    pub no_cache: bool,
    pub test_env: Vec<String>,
    pub passthrough: Vec<String>,
    /// The shard plan file of a planned run; see [`uses_shard_plan`].
    pub shard_plan: Option<String>,
    /// Where bazel writes its profile, for a multi-target run.
    pub profile: Option<String>,
}

/// The shard count for a run.
///
/// Sharding splits one target across N test JVMs. Each JVM pays ~10 s to bootstrap the IntelliJ test framework
/// before it runs anything, so shards are only worth buying where they are the sole source of parallelism.
///
/// A run covering many targets already saturates the machine across targets, and there the fixed cost dominates:
/// measured on `--lane fast` (63 targets, 6221 tests, warm server, nothing to compile), forced=6 by test spends 3m24s
/// against 1m55s for forced=2. One count for all targets is still wrong for most of them, so such a run forces
/// [`PLANNED_SHARDS`] and the shard plan gives each target its own count; see [`crate::shard_plan`].
pub fn default_shards(resolution: &Resolution, lane: Option<&LaneSpec>) -> u32 {
    // A lane that names its own count owns the decision: for IDE-launching lanes the cost and isolation unit is
    // the IDE, not JVM bootstrap, and the flow lanes rely on one shared instance.
    if let Some(shards) = lane.and_then(|lane| lane.shards) {
        return shards;
    }
    if uses_shard_plan(resolution, lane) { PLANNED_SHARDS } else { 6 }
}

/// Whether a run without an explicit shard count takes the shard plan: a run over many targets, unless its lane names
/// its own count.
pub fn uses_shard_plan(resolution: &Resolution, lane: Option<&LaneSpec>) -> bool {
    resolution.multi_target && lane.and_then(|lane| lane.shards).is_none()
}

pub const BEP_FLAG: &str = "--build_event_json_file";

/// The BEP path the caller asked bazel for, or `None`.
///
/// The wrapper reads its results out of the BEP file it asks bazel for, but passthrough args land last and win, so
/// a caller-supplied `--build_event_json_file` used to redirect the events away and leave us parsing an empty file,
/// reported as INFRA on a run that had succeeded. Adopt their path instead: bazel writes one file, both read it.
pub fn bep_override(passthrough: &[String]) -> Option<String> {
    let mut found = None;
    for (index, argument) in passthrough.iter().enumerate() {
        if let Some(value) = argument.strip_prefix(BEP_FLAG).and_then(|rest| rest.strip_prefix('=')) {
            found = Some(value.to_owned());
        } else if argument == BEP_FLAG
            && let Some(value) = passthrough.get(index + 1)
        {
            found = Some(value.clone());
        }
    }
    // `--build_event_json_file=` with no path is bazel's own "disable" spelling, and adopting "" would make us read
    // the working directory as a file.
    found.filter(|path| !path.is_empty())
}

/// The whole bazel command line.
///
/// `--test_output` must be overridden because the repository default is `streamed` (community/common.bazelrc),
/// which silently disables local sharding; `summary` is chosen over `errors` because failures are read from
/// test.xml, so streaming whole logs into our pipe would only cost memory. The UI flags exist purely to keep
/// bazel's progress chatter out of an agent's context.
///
/// `--noincompatible_check_sharding_support` is for the `rust_clippy_test` targets of the vm-lane crates. Their
/// runner reads the markers of clippy actions that ran at build time, and it never touches
/// `TEST_SHARD_STATUS_FILE`. With the check on, a forced shard count fails every one of them with "Sharding
/// requested, but the test runner did not advertise support" (measured 2026-09-29, 19 targets). With the check
/// off, each shard reads the same markers, which costs nothing. The check is never reached under `streamed`
/// output, which is why a plain `bazel test` of the same targets passes.
pub fn build_bazel_args(plan: &RunPlan) -> Vec<String> {
    let resolution = &plan.resolution;
    let mut args: Vec<String> = [
        "test",
        "--test_output=summary",
        "--test_summary=terse",
        "--noincompatible_check_sharding_support",
        "--noshow_progress",
        "--curses=no",
        "--color=no",
        "--show_result=0",
    ]
    .map(str::to_owned)
    .into();
    args.push(format!("{BEP_FLAG}={}", plan.bep_path));

    // A filter pins one class, and one class cannot usefully be split across JVMs: `explicit` leaves the target's
    // own declared sharding, which for a filtered run is none.
    match &resolution.filter {
        Some(filter) => {
            args.push("--test_sharding_strategy=explicit".to_owned());
            args.push(format!("--test_filter={filter}"));
        }
        None => args.push(format!("--test_sharding_strategy=forced={}", plan.shards)),
    }
    // One variable for every entry: a second `--test_env` of the same name would replace the first.
    let junit_filters: Vec<String> = resolution
        .include_package
        .iter()
        .map(|package| format!("include-package={package}"))
        .chain(resolution.junit5_filters.iter().cloned())
        .collect();
    if !junit_filters.is_empty() {
        args.push(format!("--test_env=JB_TEST_JUNIT5_FILTERS={}", junit_filters.join(";")));
    }
    if let Some(shard_plan) = &plan.shard_plan {
        args.push(format!("--test_env={SHARD_PLAN_ENV}={shard_plan}"));
    }
    if let Some(profile) = &plan.profile {
        args.push(format!("--profile={profile}"));
    }
    if plan.no_cache {
        args.push("--cache_test_results=no".to_owned());
    }
    // One failing target must not hide the rest when the run covers many.
    if resolution.multi_target {
        args.push("-k".to_owned());
    }
    args.extend(plan.extra.iter().cloned());
    args.extend(plan.test_env.iter().map(|name| format!("--test_env={name}")));
    args.extend(resolution.labels.iter().cloned());
    // Caller args land last so they win over anything above.
    args.extend(plan.passthrough.iter().cloned());
    args
}

#[cfg(test)]
mod tests;
