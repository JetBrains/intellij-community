//! The lane table of an area, and the bazel command line a run becomes.
//!
//! The lane table is data, in a JSON file an area names in `bt.json`, because it has readers outside Rust: a
//! product's own tests can compare it with the BUILD files. See [`Lanes`] for the shape.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::path::Path;

use refusal::Refusal;
use serde::{Deserialize, Deserializer};

use crate::areas::Areas;
use crate::exit::fail_infra;
use crate::runtime::{Platform, Runtime, repo_file};
use crate::selector::Resolution;

/// One lane table, a flat object. Only `lanes` is required:
///
/// - `lanes`: every lane in declared order, which is the order a caller sees in the "Known lanes" refusal. Each
///   is a [`LaneSpec`]. An IDE-launching lane also carries its catalog name, its test label and its JUnit tag,
///   which the Air UI-lane controller and its trace planner read instead of a table of their own.
/// - `integrationCategoryTags`: what an integration target of the area *is*, and what a broad run excludes. The
///   CI exclusion of the area should be this list, so it stays correct when a lane is added.
/// - `integrationRoot`: the target pattern prefix of the integration tree. A multi-target run the caller spelled
///   out inside it is asking for exactly those tests, so [`wildcard_guards`] keeps their tags.
/// - `propertyTag`: marks the property-test targets. Neither a category nor an integration lane: it selects a lane
///   of its own and, through `dedicatedSuiteTags`, keeps those targets out of the broad runs.
/// - `dedicatedSuiteTags`: what a broad run skips because a suite of its own owns those targets: its own lane
///   here, its own build configuration on CI. Adding a tag before its build configuration exists would leave the
///   suite running nowhere.
/// - `excludedTestLibs`: the `*_test_lib` library behind each target a broad run excludes, which a lane with
///   `buildsExcludedTestLibs` compiles in a second spawn. The library and not the `*_test` rule: an IDE-launching
///   rule declares the assembled dev distribution as `data`, and building the rule recomposes it. A tag filter
///   cannot select a library, so the area's own test keeps this list equal to the tagged targets.
/// - The suite catalog, all three or none ([`Catalog`]): `flowProfileDir`, where the flow catalog generator writes
///   one document per generated suite; `flowTextDir`, where the flow text generator writes one plain-text rendering
///   per story flow; `authoredSuitesFile`, the committed copy of the authored suites.
/// - `specDir` and `laneWideModules`: what the Air UI-lane controller joins a changed path through: where the
///   specs live, and every JPS module that holds the lane harness, with the lanes it serves (`[]` is every lane).
///   This crate reads neither.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Lanes {
    #[serde(default)]
    flow_profile_dir: Option<String>,
    #[serde(default)]
    flow_text_dir: Option<String>,
    #[serde(default)]
    authored_suites_file: Option<String>,
    #[serde(default)]
    spec_dir: Option<String>,
    #[serde(default)]
    integration_root: Option<String>,
    #[serde(default)]
    integration_category_tags: Vec<String>,
    #[serde(default)]
    property_tag: Option<String>,
    #[serde(default)]
    dedicated_suite_tags: Vec<String>,
    #[serde(default)]
    excluded_test_libs: Vec<String>,
    #[serde(default)]
    lane_wide_modules: BTreeMap<String, Vec<String>>,
    lanes: Vec<LaneSpec>,
}

/// The suite catalog of a lane table: the three paths the suite join reads, all repo-relative.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Catalog<'a> {
    pub flow_profile_dir: &'a str,
    pub flow_text_dir: &'a str,
    pub authored_suites_file: &'a str,
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
    /// The one test target of an IDE-launching lane, for a caller that has a resolved label and asks whose lane
    /// it is.
    #[serde(default)]
    pub test_label: Option<String>,
    /// The JUnit tag an IDE-launching lane's suites carry, which is how a daemon that holds every lane's classpath
    /// selects a lane. A Bazel run has no use for it, because the lane's Bazel tag already selects its one module.
    #[serde(default)]
    pub junit_tag: Option<String>,
    /// Whether the lane compiles `excludedTestLibs` in a second spawn; see [`LaneSpec::build_only`].
    #[serde(default)]
    builds_excluded_test_libs: bool,
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
        let catalog = [&lanes.flow_profile_dir, &lanes.flow_text_dir, &lanes.authored_suites_file];
        if catalog.iter().any(|path| path.is_some()) && catalog.iter().any(|path| path.is_none()) {
            return Err("a suite catalog names flowProfileDir, flowTextDir and authoredSuitesFile together".to_owned());
        }
        Ok(lanes)
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
            authored_suites_file: self.authored_suites_file.as_deref()?,
        })
    }

    pub fn spec_dir(&self) -> Option<&str> {
        self.spec_dir.as_deref()
    }

    pub fn integration_root(&self) -> Option<&str> {
        self.integration_root.as_deref()
    }

    pub fn integration_category_tags(&self) -> &[String] {
        &self.integration_category_tags
    }

    pub fn property_tag(&self) -> Option<&str> {
        self.property_tag.as_deref()
    }

    pub fn dedicated_suite_tags(&self) -> &[String] {
        &self.dedicated_suite_tags
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

    /// The tags a broad local run excludes: the categories, then the dedicated suites.
    fn broad_run_excluded_tags(&self) -> impl Iterator<Item = &String> {
        self.integration_category_tags.iter().chain(&self.dedicated_suite_tags)
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
/// Every area adds the tags its broad runs exclude, joined into one `--test_tag_filters`. A pattern rooted inside
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
        for tag in lanes.broad_run_excluded_tags() {
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
}

/// The shard count for a run.
///
/// Sharding splits one target across N test JVMs. Each JVM pays ~10 s to bootstrap the IntelliJ test framework
/// before it runs anything, so shards are only worth buying where they are the sole source of parallelism.
///
/// A run covering many targets already saturates the machine across targets, and there the fixed cost dominates:
/// measured on `--lane fast` (63 targets, 6221 tests, warm server, nothing to compile), forced=6 spends 3m24s
/// against 1m55s for forced=2. Below 2 it regresses again, because the lane becomes bound by its heaviest target.
pub fn default_shards(resolution: &Resolution, lane: Option<&LaneSpec>) -> u32 {
    // A lane that names its own count owns the decision: for IDE-launching lanes the cost and isolation unit is
    // the IDE, not JVM bootstrap, and the flow lanes rely on one shared instance.
    if let Some(shards) = lane.and_then(|lane| lane.shards) {
        return shards;
    }
    if resolution.multi_target { 2 } else { 6 }
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
