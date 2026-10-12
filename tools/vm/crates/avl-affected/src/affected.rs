//! Which suites the paths a caller names reach, generated or authored.
//!
//! # Why this exists
//!
//! A UI lane caches nothing. The host `bazel build` is cached, the guest execution is not, and one iteration re-runs
//! every suite it selects: the lane measured 411.3 s of test time for 26 classes on `air-linux-2` on 2026-08-28. So
//! a one-flow change that runs the whole lane pays all of it. The data to run less was already committed, but only
//! as three manual steps: read the tag on the file, grep the suite documents for it, then check the lane so a
//! GUI-chat suite does not land on a Linux worker. Every caller re-derived that, and the lane check is the step a
//! caller forgets. This answers it instead.
//!
//! # What it reads, and what it deliberately does not
//!
//! Committed inputs only, each one read and none derived:
//!
//! - The flow tags in the paths the *caller* names. Only the tag's own identifier is read. Route resolution,
//!   variants, splices, choices and profile inference stay owned by `docs/scripts/flowCatalog.ts`; a second
//!   implementation of the flow model here is exactly what this must not become.
//! - The suite documents under the flow profile directory. Each carries `suite`, `lane`, `testClassName` and the
//!   flow of every profile and step, and what its routes reach: the `module:` endpoints of every step they walk,
//!   and the implementation flows the story flows of those routes declare. The generator publishes both facts.
//! - The authored suites file, the copy of `AirJourneySuites` that a fast-lane test keeps equal to the Kotlin.
//! - The `targets` and the `[@test]` links of the specs.
//! - The `.iml` files above a path, for its JPS module, and the lane-wide modules and lane-wide paths of the Air
//!   lane table, for the harness.
//!
//! # The order the relations answer in
//!
//! A path is joined by the first of these that answers, most exact first:
//!
//! 1. The suite's own files: its document or its test class source, as `suite:<id>`.
//! 2. The harness: a path in a lane-wide module or under a lane-wide path reaches every suite of its lanes, as
//!    `lane-wide:<module>` or `lane-wide:<directory>`.
//! 3. The tags: a `[suite: <id>]` attribute as `suite:<id>`, and a `@flow` tag as `flow:<id>`.
//! 4. With no tag answered, two coarse relations answer together. The owning specs give `spec:<path>`, and for a
//!    path with no tag at all the module relation gives `module:<endpoint>`. They reach different suites: an
//!    authored suite has no module, and a route walks no spec.
//!
//! Nothing here reads `git status`. Several agents can share one working copy, so the diff is not reliably the
//! caller's. The caller names the paths it edited, which it knows and the tree does not.
//!
//! # A suite's own files are its exact link
//!
//! Two files are a suite rather than a part of what it tests: its document, and the source of its test class. A
//! path that is one of them reaches that suite as `suite:<id>`, the same entry a `[suite: <id>]` attribute gives,
//! and nothing else answers for it.
//!
//! # The harness reaches its whole lane
//!
//! A path in a lane-wide module is the lane harness: the flow driver, `AirScenarioRunner`, the state reset, the
//! generated scaffolding, the daemon or the bridge. A change there can break every suite of the lanes the module
//! serves. The list is held by module name because the module cache already names a path's module, so no path list
//! goes stale beside it. Each entry is a decision about what the module holds:
//!
//! - `intellij.air.integrationTests` is `support/`, the drivers every lane uses, and the directories without a
//!   module of their own under it, such as the lane `.bzl` rules.
//! - `…flow` is the flow driver, `AirScenarioRunner`, the state reset, and the generated shared scaffolding.
//! - `…uiDaemon` is the daemon, one JVM over the classpath of every lane.
//! - `…bridge` and `…bridge.plugin` are the test bridge every lane installs into the IDE under test.
//! - `…testSupport` is the fake agents and the fake registry the flow harness links for every lane.
//! - `…ui`, `…uiReal` and `…guiChat` are the lane leaves. Each holds its lane's generated test classes, so it serves
//!   that one lane. An authored test class in a leaf reaches its own suite first.
//!
//! Left out on purpose: `…contract` runs in the fast lane and in no UI lane, `…flowProfiles` holds suite documents
//! that each reach their own suite, and the `…headless.*` modules run in no UI lane.
//!
//! A harness directory that no module of the area holds is a lane-wide path of the lane table, by its repo-relative
//! directory. A path under it is the harness too, for the lanes the directory serves. The Air table names one: this
//! controller, `community/tools/vm`, which a change can break for every lane. The controller reads the directory
//! from the table and names no area directory itself. A path in a lane-wide module answers first, so a lane-wide
//! path only holds a path that no harness module holds.
//!
//! The harness answers after the suite's own files and before the tag relation. The harness declares no flow, so a
//! tag that matches in it is a test fixture or a comment.
//!
//! # An implementation flow reaches the suites of the story flows it implements
//!
//! A story flow declares which implementation flows it implements, so a path that declares only an implementation
//! flow can break a story flow's suites. The generator joins the two flows and publishes the result on each suite
//! document, which keeps the story-to-implementation relation on the generator's side alone.
//!
//! # The module relation answers last, and states what it is
//!
//! A tag is an exact link, so a path that carries one never consults the module relation. A path with no tag
//! reaches a suite by its own JPS module. That relation is the coarse one: it covers a whole module, and the
//! dotted-prefix rule of the flow authoring guide makes a parent name cover every module under it. A path that
//! reaches nothing keeps answering: [`reason::MODULE_NOT_WALKED`] says "no route walks your module", a different
//! decision from [`reason::NO_FLOW_TAG`], where the flow model does not name the path at all.
//!
//! # A directory is the files under it
//!
//! The join reads files, so a named directory is replaced with the files the checkout tracks or would track under
//! it ([`Runtime::list_files`]), and each file is joined as if the caller had named it.

use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::sync::Mutex;

use bt_core::areas::Area;
use bt_core::catalog::{SuiteDocument, read_suite_documents};
use bt_core::runtime::{Platform, Runtime, par_map, repo_file};
use bt_core::scan::source_simple_name;
use bt_core::suites::{Affected, UnmappedPath, VIA_MODULE, Via, affected_answer, note_via, suites_by_flow, suites_by_id};
use bt_core::{Refusal, fail_usage, paths};

use crate::regex;
use crate::specs::{SpecLink, VIA_SPEC, read_spec_links};

/// The prefix an [`bt_core::AffectedSuite::via`] entry of the lane-wide relation carries.
pub const VIA_LANE_WIDE: &str = "lane-wide:";

/// The reasons a named path reaches no suite. Each is a different next action for the caller, which is why they
/// are separate codes rather than one "unmapped". They are the `unmapped[].reason` values of the JSON answer.
pub mod reason {
    /// A path the flow model does not name at all: no flow tag, and no `.iml` above it says which module it
    /// belongs to. The caller decides between the whole lane and an authored suite.
    pub const NO_FLOW_TAG: &str = "no_flow_tag";
    /// A path in a JPS module no suite's routes walk. The module is reported so the caller sees what was looked
    /// for; the answer is either an authored step or the whole lane.
    pub const MODULE_NOT_WALKED: &str = "module_not_walked";
    /// A path that declares a flow no suite reaches. An implementation flow no story flow claims is the ordinary
    /// case. The flow ids are reported so the caller can see which flow answered nothing.
    pub const FLOW_DECLARES_NO_SUITE: &str = "flow_declares_no_suite";
    /// A path that could not be read. Reported rather than refused: one mistyped path must not withhold the
    /// answer for the others.
    pub const PATH_UNREADABLE: &str = "path_unreadable";
    /// A named directory that holds no file the checkout tracks or would track, so it has nothing to join.
    pub const EMPTY_DIRECTORY: &str = "empty_directory";
}

/// Which suites the named paths reach, generated or authored.
///
/// A refusal is answered only for a repository this join cannot be performed against at all: an unreadable suite
/// directory, a malformed document, a lane the controller does not know. A path the caller got wrong is data, in
/// [`Affected::unmapped`].
pub fn affected_suites(runtime: &dyn Runtime, area: &Area, paths: &[String]) -> Result<Affected, Refusal> {
    if paths.is_empty() {
        return Err(fail_usage("Name at least one changed path"));
    }
    let documents = read_suite_documents(runtime, area)?;
    let join = SuiteJoin::new(runtime, area, &documents);
    let (files, mut unmapped) = expand_directories(runtime, &normalize_paths(runtime, paths));
    // `via` accumulates every reason a suite is in the answer, so two paths reaching one suite produce one entry
    // carrying both. The paths are joined concurrently and merged in the order they were named.
    let mut via = Via::new();
    for joined in par_map(&files, |path| join.path(path)) {
        for (suite, reason) in joined.reached {
            note_via(&mut via, suite, reason);
        }
        unmapped.extend(joined.unmapped);
    }
    affected_answer(area, &documents, &via, unmapped)
}

/// The indexes [`affected_suites`] joins one path against. Read-only once built, so the paths can be joined
/// concurrently; the one cache it fills is locked.
struct SuiteJoin<'a> {
    runtime: &'a dyn Runtime,
    area: &'a Area,
    documents: &'a [SuiteDocument],
    by_suite: HashMap<&'a str, usize>,
    /// The two files that are a suite: its document, and the source of its test class.
    by_path: HashMap<&'a str, usize>,
    by_class: HashMap<&'a str, Vec<usize>>,
    by_flow: BTreeMap<String, Vec<usize>>,
    by_module: BTreeMap<&'a str, Vec<usize>>,
    modules: ModuleCache<'a>,
    specs: Vec<SpecLink>,
}

/// What one path reached, or why it reached nothing.
#[derive(Default)]
struct PathJoin {
    reached: Vec<(usize, String)>,
    unmapped: Option<UnmappedPath>,
}

impl PathJoin {
    const fn unmapped(unmapped: UnmappedPath) -> Self {
        Self {
            reached: Vec::new(),
            unmapped: Some(unmapped),
        }
    }
}

impl<'a> SuiteJoin<'a> {
    fn new(runtime: &'a dyn Runtime, area: &'a Area, documents: &'a [SuiteDocument]) -> Self {
        let mut join = SuiteJoin {
            runtime,
            area,
            documents,
            by_suite: suites_by_id(documents),
            by_path: HashMap::new(),
            by_class: HashMap::new(),
            by_flow: suites_by_flow(documents),
            by_module: BTreeMap::new(),
            modules: ModuleCache {
                runtime,
                by_directory: Mutex::new(HashMap::new()),
            },
            specs: Vec::new(),
        };
        for (index, document) in documents.iter().enumerate() {
            if !document.path.is_empty() {
                join.by_path.insert(&document.path, index);
            }
            join.by_class.entry(&document.test_class_name).or_default().push(index);
            for module in &document.modules {
                join.by_module.entry(module).or_default().push(index);
            }
        }
        join.specs = read_spec_links(runtime, area, &|path| join.own_suites(path));
        join
    }

    /// The suites this path is a file of: the suite document itself, or the source file of the suite's test class.
    /// A test source is found by its name, which is the name of the one class a Kotlin or Java test file declares
    /// here. A generated test class is not found this way: it lives in its lane's generated file.
    fn own_suites(&self, path: &str) -> Vec<usize> {
        if let Some(&document) = self.by_path.get(path) {
            return vec![document];
        }
        source_simple_name(path)
            .and_then(|name| self.by_class.get(name))
            .cloned()
            .unwrap_or_default()
    }

    /// Joins one repo-relative path, in the order the module comment gives.
    fn path(&self, path: &str) -> PathJoin {
        let mut answer = PathJoin::default();
        let own = self.own_suites(path);
        if !own.is_empty() {
            for suite in own {
                answer.reached.push((suite, format!("suite:{}", self.documents[suite].suite)));
            }
            return answer;
        }
        let Ok(text) = self.runtime.read_text_file(&resolve_against_repo(self.runtime, path)) else {
            return PathJoin::unmapped(UnmappedPath::new(path, reason::PATH_UNREADABLE));
        };
        let modules = self.modules.owning(self.runtime.platform(), path);
        let lanes = self.area.lanes();
        if let Some((harness, served)) = modules
            .iter()
            .find_map(|module| Some((module.as_str(), lanes.lane_wide_lanes(module)?)))
            .or_else(|| lanes.lane_wide_path_lanes(path))
        {
            // The harness declares no flow, so a tag that matches here matched a fixture or a comment. That is
            // why this answers before the tag relation does.
            for (index, document) in self.documents.iter().enumerate() {
                let lane = document.lane_name(lanes);
                if served.is_empty() || lane.is_some_and(|lane| served.iter().any(|served| served == lane)) {
                    answer.reached.push((index, format!("{VIA_LANE_WIDE}{harness}")));
                }
            }
            return answer;
        }

        // A profile names its suite in an attribute the generator requires ("@flowTest requires '[suite: <id>]'"),
        // an exact link from the declaring file to one suite rather than an inference from the flow.
        let suites = unique_matches(regex!(r"@flowTest\b[^\n]*\[suite:[ \t]*([A-Za-z0-9._-]+)[ \t]*\]"), &text);
        // A flow declaration names its flow after the tag and whitespace. `@flowStep`, `@flowTest`, `@flowAfter`
        // and the rest cannot match, because each has a letter where this requires whitespace.
        let flows = unique_matches(regex!(r"@flow[ \t]+([A-Za-z0-9._-]+)"), &text);
        for suite in &suites {
            if let Some(&document) = self.by_suite.get(suite.as_str()) {
                answer.reached.push((document, format!("suite:{suite}")));
            }
        }
        for flow in &flows {
            for &document in self.by_flow.get(flow).into_iter().flatten() {
                answer.reached.push((document, format!("flow:{flow}")));
            }
        }
        if !answer.reached.is_empty() {
            return answer;
        }

        // No tag answered, so the spec relation does: the suites the owning specs link as their tests.
        for spec in self.specs.iter().filter(|spec| spec.owns(path)) {
            for &document in &spec.suites {
                answer.reached.push((document, format!("{VIA_SPEC}{}", spec.path)));
            }
        }
        if !suites.is_empty() || !flows.is_empty() {
            if !answer.reached.is_empty() {
                return answer;
            }
            // Every tag on this path named something no suite reaches. The ids go into the answer because they are
            // what tells an unclaimed implementation flow apart from a typo. The module relation is not consulted:
            // a tag is the more specific fact, and it already says the flow model names this path.
            return PathJoin::unmapped(UnmappedPath {
                flows: flows.into_iter().chain(suites).collect(),
                ..UnmappedPath::new(path, reason::FLOW_DECLARES_NO_SUITE)
            });
        }

        // No tag, so the coarse relation answers too: the suites whose routes walk this path's own module.
        for endpoint in self.covering_endpoints(&modules) {
            for &document in &self.by_module[endpoint] {
                answer.reached.push((document, format!("{VIA_MODULE}{endpoint}")));
            }
        }
        if !answer.reached.is_empty() {
            return answer;
        }
        match modules.into_iter().next() {
            None => PathJoin::unmapped(UnmappedPath::new(path, reason::NO_FLOW_TAG)),
            Some(module) => PathJoin::unmapped(UnmappedPath {
                module: Some(module),
                ..UnmappedPath::new(path, reason::MODULE_NOT_WALKED)
            }),
        }
    }

    /// Every module endpoint that covers one of `modules`, sorted.
    fn covering_endpoints(&self, modules: &[String]) -> Vec<&'a str> {
        self.by_module
            .keys()
            .copied()
            .filter(|endpoint| modules.iter().any(|module| module_covers(endpoint, module)))
            .collect()
    }
}

/// The dotted-prefix rule `docs/guides/flow-authoring-guide.md` states for a step endpoint: an endpoint covers its
/// own module and every module nested under it, so `intellij.air.backend.acp` covers
/// `intellij.air.backend.acp.runtime` and does not cover `intellij.air.backend.acpx`.
fn module_covers(endpoint: &str, module: &str) -> bool {
    module == endpoint || module.strip_prefix(endpoint).is_some_and(|rest| rest.starts_with('.'))
}

/// Answers [`ModuleCache::owning`] once per directory. The files of a named directory share their ancestors, and
/// without it every file would list each of them again.
struct ModuleCache<'a> {
    runtime: &'a dyn Runtime,
    by_directory: Mutex<HashMap<String, Vec<String>>>,
}

impl ModuleCache<'_> {
    /// The JPS modules declared in the nearest directory above `path` that declares any.
    ///
    /// The `.iml` file is the source of truth for a module, and the module name is its basename without the
    /// suffix, so nothing here parses the file. Several `.iml` files in one directory are common (a module and its
    /// test module), and every one of them is answered rather than one being picked. On this tree the shortest name
    /// is a dotted prefix of every other name in its directory, so the covering rule reaches the same suites for
    /// all of them; where that ever stops holding, a superset is the safe direction for the relation that answers
    /// last. The shortest name comes first, so a report names the module rather than its test module.
    fn owning(&self, platform: Platform, path: &str) -> Vec<String> {
        if is_absolute(platform, path) {
            return Vec::new();
        }
        self.declared_above(&distpath::dir(path))
    }

    fn declared_above(&self, directory: &str) -> Vec<String> {
        if directory == "." || directory == "/" {
            return Vec::new();
        }
        if let Some(names) = self.lock().get(directory) {
            return names.clone();
        }
        let mut names = declared_modules(self.runtime, directory);
        if names.is_empty() {
            names = self.declared_above(&distpath::dir(directory));
        }
        self.lock().insert(directory.to_owned(), names.clone());
        names
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<String, Vec<String>>> {
        // A poisoned cache still holds only complete entries: each is inserted whole.
        avl_base::sync::lock(&self.by_directory)
    }
}

/// The modules whose `.iml` sits in this one directory, shortest name first.
fn declared_modules(runtime: &dyn Runtime, directory: &str) -> Vec<String> {
    let Ok(entries) = runtime.read_dir(&repo_file(runtime, directory)) else {
        return Vec::new();
    };
    let mut names: Vec<String> = entries
        .into_iter()
        .filter(|entry| !entry.is_dir)
        .filter_map(|entry| entry.name.strip_suffix(".iml").map(str::to_owned))
        .collect();
    names.sort_by(|first, second| (first.len(), first).cmp(&(second.len(), second)));
    names
}

/// Whether a named path is absolute in the platform's dialect.
fn is_absolute(platform: Platform, path: &str) -> bool {
    match platform {
        Platform::Windows => path.starts_with(['/', '\\']) || (paths::has_windows_drive(path) && path[2..].starts_with(['/', '\\'])),
        Platform::Darwin | Platform::Linux => path.starts_with('/'),
    }
}

/// The file to read for one named path.
///
/// A path [`normalize_paths`] could not make relative stays absolute, and is read as it stands rather than joined
/// onto the checkout root, which would produce a path nothing is at. That happens for a real directory outside the
/// checkout, and also for one inside it that the caller spelled through a symlink the root does not use (`/var`
/// against a `/private/var` root on macOS). Reading it is honest either way: the caller named the file.
fn resolve_against_repo(runtime: &dyn Runtime, path: &str) -> std::path::PathBuf {
    if is_absolute(runtime.platform(), path) {
        std::path::PathBuf::from(path)
    } else {
        repo_file(runtime, path)
    }
}

/// Every named path made repo-relative, in the order the caller gave and without duplicates.
///
/// An absolute path is what an agent has in hand after an edit, and a repo-relative one is what a document names,
/// so both are accepted. A path this cannot make relative keeps its absolute spelling.
fn normalize_paths(runtime: &dyn Runtime, paths: &[String]) -> Vec<String> {
    let root = slashed_root(runtime);
    let mut normalized: Vec<String> = Vec::with_capacity(paths.len());
    for raw in paths {
        let path = normalize_path(runtime.platform(), &root, raw);
        if !path.is_empty() && !normalized.contains(&path) {
            normalized.push(path);
        }
    }
    normalized
}

fn slashed_root(runtime: &dyn Runtime) -> String {
    to_slash(runtime.platform(), &runtime.repo_root().to_string_lossy())
}

fn to_slash(platform: Platform, path: &str) -> String {
    match platform {
        Platform::Windows => path.replace('\\', "/"),
        Platform::Darwin | Platform::Linux => path.to_owned(),
    }
}

/// One path of [`normalize_paths`]. A trailing slash is dropped, because a shell completes a directory with one
/// and a directory is named without it everywhere else.
fn normalize_path(platform: Platform, root: &str, raw: &str) -> String {
    let slashed = to_slash(platform, raw.trim());
    let mut path = slashed.strip_prefix("./").unwrap_or(&slashed);
    if let Some(relative) = path.strip_prefix(root).and_then(|rest| rest.strip_prefix('/')) {
        path = relative;
    }
    if path.len() > 1 {
        path = path.trim_end_matches('/');
    }
    path.to_owned()
}

/// Every named directory replaced with the files under it, every other path kept.
///
/// A directory is what a caller has in hand after a change that spans a module, and a join reads files, so a
/// directory used to answer [`reason::PATH_UNREADABLE`]. The files are the ones [`Runtime::list_files`] answers:
/// what the checkout tracks, and what it would track, but never what it ignores. A directory that holds no such
/// file is reported as [`reason::EMPTY_DIRECTORY`], because an answer that silently dropped the only path named
/// would read as "reaches nothing" for the wrong reason.
fn expand_directories(runtime: &dyn Runtime, paths: &[String]) -> (Vec<String>, Vec<UnmappedPath>) {
    let root = slashed_root(runtime);
    let mut files: Vec<String> = Vec::with_capacity(paths.len());
    let mut unmapped = Vec::new();
    let mut keep = |path: String| {
        if !files.contains(&path) {
            files.push(path);
        }
    };
    for path in paths {
        let directory = resolve_against_repo(runtime, path);
        if runtime.read_dir(&directory).is_err() {
            // A file, or a path nothing is at. The join reads it, and a path nothing is at is unreadable there.
            keep(path.clone());
            continue;
        }
        match runtime.list_files(&directory) {
            Err(_) => unmapped.push(UnmappedPath::new(path, reason::PATH_UNREADABLE)),
            Ok(listed) if listed.is_empty() => {
                unmapped.push(UnmappedPath::new(path, reason::EMPTY_DIRECTORY));
            }
            Ok(listed) => {
                for file in listed {
                    keep(normalize_path(runtime.platform(), &root, &file.to_string_lossy()));
                }
            }
        }
    }
    (files, unmapped)
}

/// The first capture of every match, sorted and unique.
fn unique_matches(pattern: &regex::Regex, text: &str) -> Vec<String> {
    let found: BTreeSet<String> = pattern
        .captures_iter(text)
        .filter_map(|found| Some(found.get(1)?.as_str().to_owned()))
        .collect();
    found.into_iter().collect()
}

#[cfg(test)]
mod tests;
