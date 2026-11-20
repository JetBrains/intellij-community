//! The in-memory [`Runtime`], and the fixture builders the tests share: this crate's, the `bt` binary's, and the
//! tests of a crate that resolves through this one.
//!
//! No test invokes bazel or touches the real filesystem: resolution, a whole bazel run, result collection and the
//! digest are all exercised against a map of files. Fixtures are assembled by the builders here rather than
//! committed: a real failing test.xml measured 146 KB, almost all of it `<system-out>`, and a lane BEP runs to tens
//! of MB. To capture a real BEP for manual inspection instead, add `--build_event_json_file=/tmp/bep.json` to any
//! bazel test run.

use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::ffi::OsString;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{LazyLock, Mutex, MutexGuard};

use refusal::Refusal;

use crate::areas::{AREAS_FILE, Area, Areas};
use crate::lanes::Lanes;
use crate::runtime::{CheckoutTree, DirEntry, Heartbeat, Platform, Runtime, SpawnResult};

pub const REPO_ROOT: &str = "/repo";

/// The home directory of the fake user.
pub const HOME_DIR: &str = "/home/user";

/// The directory of the fixture area, the one [`air_tree`] fills.
pub const AREA_DIR: &str = "plugins/air";

/// Where [`area_files`] puts the fixture lane table.
pub const LANES_FILE: &str = "plugins/air/tests/integration/lanes.json";

/// The fixture lane table: a copy of the Air area's, with its suite catalog, so every rule here is tested against a
/// realistic table. It is a copy because the community module cannot read the file of the ultimate root. The
/// contract test `//plugins/air/tests/integration/vm-contract:areas-test` keeps the copy equal to the real table.
pub const LANES_TEXT: &str = include_str!("../testdata/lanes.json");

static AREAS: LazyLock<Areas> = LazyLock::new(|| {
    let lanes = Lanes::parse(LANES_TEXT).expect("the fixture lane table parses");
    Areas::new(vec![Area::new(AREA_DIR, LANES_FILE, lanes)])
});

/// The fixture areas: [`area`] alone.
pub fn areas() -> &'static Areas {
    &AREAS
}

/// The fixture area, over the fixture lane table.
pub fn area() -> &'static Area {
    AREAS.iter().next().expect("the fixture has one area")
}

/// The fixture lane table, parsed.
pub fn lanes() -> &'static Lanes {
    area().lanes()
}

/// The timing of target `label`: the seconds of each class, by its suite name.
pub fn timing(label: &str, classes: &[(&str, f64)]) -> crate::result::TargetTiming {
    crate::result::TargetTiming {
        label: label.to_owned(),
        class_seconds: classes.iter().map(|(class, seconds)| ((*class).to_owned(), *seconds)).collect(),
    }
}

/// The fixture lane table [`LANES_TEXT`] after `edit` changed its fast lane, as JSON text.
pub fn lanes_text_with_fast_lane(edit: impl FnOnce(&mut serde_json::Map<String, serde_json::Value>)) -> String {
    let mut lanes: serde_json::Value = serde_json::from_str(LANES_TEXT).expect("the fixture lane table parses");
    let fast = lanes["lanes"]
        .as_array_mut()
        .expect("the fixture lane table has lanes")
        .iter_mut()
        .find(|lane| lane.get("name").and_then(serde_json::Value::as_str) == Some("fast"))
        .expect("the fixture has a fast lane");
    edit(fast.as_object_mut().expect("a lane is an object"));
    serde_json::to_string(&lanes).expect("the fixture lane table encodes")
}

/// The files `bt.json` and the lane table add to a tree, for a test that loads the areas from the checkout. The fake
/// omits the optional class-time budget; tests of that gate add a synthetic baseline explicitly.
pub fn area_files() -> [(String, String); 2] {
    let lane_text = lanes_text_with_fast_lane(|fast| {
        fast.remove("classTimeBudget");
    });
    [
        (
            AREAS_FILE.to_owned(),
            format!(r#"{{ "areas": [ {{ "dir": "{AREA_DIR}", "lanes": "{LANES_FILE}" }} ] }}"#),
        ),
        (LANES_FILE.to_owned(), lane_text),
    ]
}

/// The fake's state. Behind a mutex because a directory walk fans out over worker threads.
#[derive(Default)]
pub struct State {
    pub files: BTreeMap<String, String>,
    /// Every read attempted, in order, so a test can pin how much I/O a code path does.
    pub reads: Vec<String>,
    pub spawned: Vec<Vec<String>>,
    /// The absolute paths whose [`Runtime::write_text_file`] fails.
    pub write_failures: BTreeSet<String>,
    /// Each spawn as `spawn <command line>` and each [`Runtime::checkout_tree`] as `tree`, in order, so a test can pin
    /// which comes first.
    pub order: Vec<String>,
    pub removed: Vec<String>,
    pub stdout: Vec<String>,
    pub stderr: Vec<String>,
    pub spawn_result: SpawnResult,
    /// The answers of the next spawns, in order. A spawn takes the first one, and answers [`State::spawn_result`]
    /// when none is left.
    pub spawn_results: VecDeque<SpawnResult>,
    /// Answers by command: a spawn whose argv, joined by spaces, holds the text of a rule answers the result of the
    /// first such rule, before the queue of [`State::spawn_results`] is asked. A `bazel query` needs a rule: without
    /// one the spawn panics, so a test cannot read a query that it did not script as a tree without targets.
    pub spawn_rules: Vec<(String, SpawnResult)>,
    pub clock: u64,
    /// The answer of [`Runtime::self_build_ms`].
    pub self_build_ms: Option<u64>,
    /// Makes every read fail loudly on top of being recorded. Both matter: the production helpers swallow a
    /// missing file, so a failure alone could be absorbed into an empty scan and read as success, while the
    /// recording alone would let a swallowed failure pass for laziness.
    pub forbidden: bool,
    /// The files of the tree the checkout ignores; see [`FakeRuntime::ignore`].
    pub ignored: BTreeSet<String>,
    /// The files of each revision the checkout holds, by repo-relative path; see [`FakeRuntime::put_revision`].
    pub revisions: BTreeMap<String, BTreeMap<String, String>>,
    /// The answer of [`Runtime::upstream_base`]: `None` stands for a checkout without an upstream.
    pub upstream_base: Option<String>,
    /// The answers of the next [`Runtime::checkout_tree`] calls, in order. A call takes the first one, and answers
    /// `None`, a checkout that is no linked work tree, when none is left.
    pub checkout_trees: VecDeque<CheckoutTree>,
    /// The tree of each commit, for [`Runtime::commit_tree`].
    pub commit_trees: BTreeMap<String, String>,
    /// The absolute common git directory of [`Runtime::git_common_path`]. `None` stands for a checkout outside git, so
    /// a test that names none keeps the shard plan in the checkout only.
    pub git_common_dir: Option<String>,
    /// The model that [`Runtime::accio`] answers from.
    pub accio: FakeAccio,
    /// Each [`Runtime::accio`] call, as its arguments joined by spaces, in order.
    pub accio_calls: Vec<String>,
}

/// The accio of the fake: a model of the class index and of the module roots. A test states the classes and the roots
/// once, and every call shape answers from them, a batch `symbol` call included.
///
/// The model takes JSON text, not values: the Air UI-lane workspace links this crate with another build of
/// `serde_json`.
#[derive(Clone, Debug, Default)]
pub struct FakeAccio {
    /// The classes of the index, each as its file and its FQN in the binary form of the runner.
    pub classes: Vec<(String, String)>,
    /// The module roots, each as the JSON text of one entry of the `modules` answer.
    pub modules: Vec<String>,
    /// The scripted answers by the arguments of a call joined by spaces. The model answers only a call without one.
    pub answers: BTreeMap<String, Result<String, Refusal>>,
    /// The names whose `symbol` answer the limit cuts: `complete` is `false`.
    pub incomplete: BTreeSet<String>,
}

impl FakeAccio {
    /// The answer of one call, as accio prints it with `--format json`.
    ///
    /// # Panics
    ///
    /// On a command that the model does not know: a test must script it.
    pub fn answer(&self, args: &[&str]) -> Result<String, Refusal> {
        if let Some(answer) = self.answers.get(&args.join(" ")) {
            return answer.clone();
        }
        let operands: Vec<&str> = args.iter().skip(1).copied().take_while(|arg| !arg.starts_with("--")).collect();
        match args.first().copied() {
            Some("symbol") => Ok(serde_json::Value::Array(operands.iter().map(|name| self.symbol(name)).collect()).to_string()),
            Some("modules") => {
                let paths: Vec<&str> = args
                    .iter()
                    .skip_while(|arg| **arg != "--paths")
                    .skip(1)
                    .copied()
                    .take_while(|arg| !arg.starts_with("--"))
                    .collect();
                Ok(self.modules_of(&paths).to_string())
            }
            _ => panic!("the fake accio has no model of `accio {}`", args.join(" ")),
        }
    }

    fn symbol(&self, query: &str) -> serde_json::Value {
        let hits: Vec<serde_json::Value> = self
            .classes
            .iter()
            .filter(|(_, fqn)| {
                if query.contains('.') {
                    fqn.replace('$', ".") == query
                } else {
                    simple_name_of(fqn) == query
                }
            })
            .map(|(file, fqn)| class_hit(file, fqn))
            .collect();
        let mut answer = serde_json::json!({
            "complete": !self.incomplete.contains(query),
            "total": hits.len(),
            "match": if hits.is_empty() { "none" } else { "exact" },
            "quality": "exact",
            "hits": hits,
        });
        if answer["hits"].as_array().is_some_and(Vec::is_empty) {
            answer["note"] = serde_json::Value::from(format!("no declaration named {query}"));
        }
        answer
    }

    fn modules_of(&self, paths: &[&str]) -> serde_json::Value {
        let entries: Vec<serde_json::Value> = self
            .modules
            .iter()
            .map(|text| serde_json::from_str(text).expect("a fixture module entry is JSON"))
            .collect();
        let root_of = |entry: &serde_json::Value| entry["root"].as_str().unwrap_or_default().to_owned();
        let mut matched: Vec<serde_json::Value> = Vec::new();
        let mut named = Vec::new();
        for path in paths {
            let found = entries
                .iter()
                .filter(|entry| {
                    let root = root_of(entry);
                    root.is_empty() || *path == root || path.strip_prefix(&root).is_some_and(|rest| rest.starts_with('/'))
                })
                .max_by_key(|entry| root_of(entry).len());
            let root = found.map_or_else(String::new, root_of);
            named.push(serde_json::json!({ "path": path, "root": root }));
            if let Some(found) = found
                && !matched.contains(found)
            {
                matched.push(found.clone());
            }
        }
        serde_json::json!({
            "total": matched.len(),
            "complete": true,
            "quality": "exact",
            "modules": matched,
            "paths": named,
        })
    }
}

/// The simple name of an FQN in the binary form: the part after the last `.` and the last `$`.
fn simple_name_of(fqn: &str) -> &str {
    let tail = fqn.rsplit('.').next().unwrap_or(fqn);
    tail.rsplit('$').next().unwrap_or(tail)
}

/// One `symbol` hit of a class, with `top_level` for a nested class.
fn class_hit(file: &str, fqn: &str) -> serde_json::Value {
    let language = if file.ends_with(".java") { "java" } else { "kotlin" };
    let mut hit = serde_json::json!({
        "file": file,
        "line": 1,
        "kind": "class",
        "group": "class",
        "language": language,
        "name": simple_name_of(fqn),
        "fqn": fqn,
    });
    if let Some((top_level, _)) = fqn.split_once('$') {
        hit["top_level"] = serde_json::Value::from(top_level);
    }
    hit
}

pub struct FakeRuntime {
    root: PathBuf,
    platform: Platform,
    state: Mutex<State>,
}

/// A path in the fake tree's POSIX shape. The code under test joins with `Path::join`, which on a Windows root
/// leaves a backslash before the first `/`, so one fixture serves both platforms and the recorded reads stay
/// comparable to the POSIX literals the assertions use.
pub fn key(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

impl FakeRuntime {
    pub fn new<'a>(files: impl IntoIterator<Item = (&'a str, &'a str)>) -> Self {
        Self::on(Platform::Darwin, REPO_ROOT, files)
    }

    /// [`FakeRuntime::new`] under another platform and root, so a Windows code path can be driven from any host.
    pub fn on<'a>(platform: Platform, root: &str, files: impl IntoIterator<Item = (&'a str, &'a str)>) -> Self {
        let fake = Self {
            root: PathBuf::from(root),
            platform,
            state: Mutex::new(State {
                clock: 1000,
                ..State::default()
            }),
        };
        for (path, text) in files {
            fake.put(path, text);
        }
        fake
    }

    /// A tree given as owned pairs, as the fixture builders answer it.
    pub fn with_tree(tree: &BTreeMap<String, String>) -> Self {
        Self::new(tree.iter().map(|(path, text)| (path.as_str(), text.as_str())))
    }

    pub fn state(&self) -> MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    fn absolute_key(&self, relative: &str) -> String {
        format!("{}/{relative}", key(&self.root))
    }

    pub fn put(&self, relative: &str, text: &str) {
        let absolute = self.absolute_key(relative);
        self.state().files.insert(absolute, text.to_owned());
    }

    pub fn put_absolute(&self, path: &str, text: &str) {
        self.state().files.insert(path.to_owned(), text.to_owned());
    }

    pub fn forbid_file_system(&self) {
        self.state().forbidden = true;
    }

    /// Makes a file of the tree one the checkout ignores: [`Runtime::read_dir`] still lists it, and
    /// [`Runtime::list_files`] does not.
    pub fn ignore(&self, relative: &str) {
        let absolute = self.absolute_key(relative);
        self.state().ignored.insert(absolute);
    }

    pub fn reads(&self) -> Vec<String> {
        self.state().reads.clone()
    }

    /// Makes the checkout hold `revision`, with the repo-relative file `relative` at `text`. A revision with no file
    /// at all is [`FakeRuntime::add_revision`].
    pub fn put_revision(&self, revision: &str, relative: &str, text: &str) {
        self.state()
            .revisions
            .entry(revision.to_owned())
            .or_default()
            .insert(relative.to_owned(), text.to_owned());
    }

    /// Makes the checkout hold `revision`, with no file that [`FakeRuntime::put_revision`] did not put.
    pub fn add_revision(&self, revision: &str) {
        self.state().revisions.entry(revision.to_owned()).or_default();
    }

    /// Makes `revision` the answer of [`Runtime::upstream_base`], and a revision that the checkout holds.
    pub fn set_upstream_base(&self, revision: &str) {
        self.add_revision(revision);
        self.state().upstream_base = Some(revision.to_owned());
    }

    /// Adds a class to the index of the fake accio: its repo-relative file and its FQN in the binary form.
    pub fn add_class(&self, file: &str, fqn: &str) {
        self.state().accio.classes.push((file.to_owned(), fqn.to_owned()));
    }

    /// Adds a module root to the fake accio, as the JSON text of one entry of the `modules` answer.
    pub fn add_module(&self, entry: impl Into<String>) {
        self.state().accio.modules.push(entry.into());
    }

    /// Scripts the answer of one accio call, by its arguments joined by spaces.
    pub fn answer_accio(&self, args: &str, answer: Result<String, Refusal>) {
        self.state().accio.answers.insert(args.to_owned(), answer);
    }

    /// Every accio call so far, as its arguments joined by spaces.
    pub fn accio_calls(&self) -> Vec<String> {
        self.state().accio_calls.clone()
    }

    pub fn spawned(&self) -> Vec<Vec<String>> {
        self.state().spawned.clone()
    }

    /// Records the argv, advances the clock by 5 s, and answers the next scripted result.
    fn scripted_spawn(&self, command: &[OsString]) -> SpawnResult {
        let mut state = self.state();
        state
            .spawned
            .push(command.iter().map(|part| part.to_string_lossy().into_owned()).collect());
        state.clock += 5000;
        let joined = crate::os_runtime::command_line(command);
        state.order.push(format!("spawn {joined}"));
        if let Some((_, result)) = state.spawn_rules.iter().find(|(text, _)| joined.contains(text.as_str())) {
            return result.clone();
        }
        assert!(!joined.contains(" query "), "a bazel query without a spawn rule: {joined}");
        match state.spawn_results.pop_front() {
            Some(result) => result,
            None => state.spawn_result.clone(),
        }
    }

    fn note(&self, what: &str, path: &str) -> io::Result<()> {
        let mut state = self.state();
        state.reads.push(format!("{what} {path}"));
        if state.forbidden {
            return Err(io::Error::other(format!("unexpected {what} of {path}")));
        }
        Ok(())
    }
}

fn not_found(path: &str) -> io::Error {
    io::Error::new(io::ErrorKind::NotFound, format!("ENOENT: {path}"))
}

impl Runtime for FakeRuntime {
    fn repo_root(&self) -> &Path {
        &self.root
    }

    fn platform(&self) -> Platform {
        self.platform
    }

    fn read_text_file(&self, path: &Path) -> io::Result<String> {
        let path = key(path);
        self.note("read", &path)?;
        self.state().files.get(&path).cloned().ok_or_else(|| not_found(&path))
    }

    fn write_text_file(&self, path: &Path, text: &str) -> io::Result<()> {
        let path = key(path);
        let mut state = self.state();
        if state.write_failures.contains(&path) {
            return Err(io::Error::new(io::ErrorKind::PermissionDenied, format!("EACCES: {path}")));
        }
        state.files.insert(path, text.to_owned());
        Ok(())
    }

    fn read_lines(&self, path: &Path) -> Box<dyn Iterator<Item = String> + '_> {
        let text = self.state().files.get(&key(path)).cloned().unwrap_or_default();
        Box::new(text.split('\n').map(str::to_owned).collect::<Vec<_>>().into_iter())
    }

    fn read_dir(&self, path: &Path) -> io::Result<Vec<DirEntry>> {
        let path = key(path);
        self.note("readDir", &path)?;
        let prefix = format!("{path}/");
        let mut entries: Vec<DirEntry> = Vec::new();
        for held in self.state().files.keys() {
            let Some(rest) = held.strip_prefix(&prefix) else {
                continue;
            };
            let (name, is_dir) = match rest.split_once('/') {
                Some((name, _)) => (name, true),
                None => (rest, false),
            };
            match entries.iter_mut().find(|entry| entry.name == name) {
                Some(entry) => entry.is_dir |= is_dir,
                None => entries.push(DirEntry {
                    name: name.to_owned(),
                    is_dir,
                }),
            }
        }
        if entries.is_empty() {
            return Err(not_found(&path));
        }
        Ok(entries)
    }

    /// Every file under the directory, less the ones [`FakeRuntime::ignore`] named, which is how the fake stands in
    /// for a checkout's ignore rules.
    fn list_files(&self, dir: &Path) -> io::Result<Vec<PathBuf>> {
        let dir = key(dir);
        self.note("listFiles", &dir)?;
        let prefix = format!("{dir}/");
        let state = self.state();
        Ok(state
            .files
            .keys()
            .filter(|held| held.starts_with(&prefix) && !state.ignored.contains(*held))
            .map(PathBuf::from)
            .collect())
    }

    /// A revision that [`FakeRuntime::put_revision`] or [`FakeRuntime::add_revision`] did not name is one the checkout
    /// does not hold.
    fn revision_text(&self, revision: &str, path: &str) -> io::Result<Option<String>> {
        self.note("revision", &format!("{revision}:{path}"))?;
        let state = self.state();
        let files = state.revisions.get(revision).ok_or_else(|| not_found(revision))?;
        Ok(files.get(path).cloned())
    }

    fn upstream_base(&self) -> io::Result<String> {
        self.state()
            .upstream_base
            .clone()
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "the checkout has no upstream"))
    }

    fn exists(&self, path: &Path) -> bool {
        let path = key(path);
        let prefix = format!("{path}/");
        let state = self.state();
        state.files.contains_key(&path) || state.files.keys().any(|held| held.starts_with(&prefix))
    }

    fn checkout_tree(&self) -> io::Result<Option<CheckoutTree>> {
        let mut state = self.state();
        state.order.push("tree".to_owned());
        Ok(state.checkout_trees.pop_front())
    }

    fn commit_tree(&self, commit: &str) -> io::Result<String> {
        self.state()
            .commit_trees
            .get(commit)
            .cloned()
            .ok_or_else(|| io::Error::other(format!("fatal: Needed a single revision: {commit}^{{tree}}")))
    }

    /// The spelling of a main checkout: the git directory is `.git` of the root.
    fn git_path(&self, name: &str) -> io::Result<PathBuf> {
        Ok(self.root.join(".git").join(name))
    }

    /// The file in [`State::git_common_dir`], or `None` when the test named no common git directory.
    fn git_common_path(&self, name: &str) -> io::Result<Option<PathBuf>> {
        Ok(self.state().git_common_dir.as_ref().map(|dir| PathBuf::from(dir).join(name)))
    }

    /// Records the argv, beats once as a 30 s run would, and answers the scripted result.
    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
        let result = self.scripted_spawn(command);
        // Outside the lock: the heartbeat writes a progress line through this same runtime.
        if let Some(beat) = heartbeat {
            beat(30_000);
        }
        result
    }

    /// Records the argv and answers the scripted result, whose output stands for the stdout of the child.
    fn spawn_streaming(&self, command: &[OsString]) -> SpawnResult {
        self.scripted_spawn(command)
    }

    /// Records the call and answers it from [`State::accio`].
    fn accio(&self, args: &[&str]) -> Result<String, Refusal> {
        let accio = {
            let mut state = self.state();
            state.accio_calls.push(args.join(" "));
            state.accio.clone()
        };
        accio.answer(args)
    }

    fn temp_file(&self, prefix: &str) -> PathBuf {
        PathBuf::from(format!("/tmp/{prefix}.json"))
    }

    fn remove(&self, path: &Path) -> io::Result<()> {
        self.state().removed.push(key(path));
        Ok(())
    }

    /// Copies the text of a held file. Directories need no creation, because the fake holds files only.
    fn copy_file(&self, from: &Path, to: &Path) -> io::Result<()> {
        let from = key(from);
        let mut state = self.state();
        let text = state.files.get(&from).cloned().ok_or_else(|| not_found(&from))?;
        state.files.insert(key(to), text);
        Ok(())
    }

    /// Nothing to create: the fake holds files only.
    fn create_dir_all(&self, _: &Path) -> io::Result<()> {
        Ok(())
    }

    /// Drops every held file under the directory, and records the directory as removed.
    fn remove_dir_all(&self, path: &Path) -> io::Result<()> {
        let path = key(path);
        let prefix = format!("{path}/");
        let mut state = self.state();
        state.files.retain(|held, _| !held.starts_with(&prefix));
        state.removed.push(path);
        Ok(())
    }

    fn home_dir(&self) -> Option<PathBuf> {
        Some(PathBuf::from(HOME_DIR))
    }

    fn now_ms(&self) -> u64 {
        self.state().clock
    }

    fn self_build_ms(&self) -> Option<u64> {
        self.state().self_build_ms
    }

    fn write(&self, text: &str) {
        self.state().stdout.push(text.to_owned());
    }

    fn write_error(&self, text: &str) {
        self.state().stderr.push(text.to_owned());
    }
}

// --- fixture builders ----------------------------------------------------------------------------------------

pub fn build_bazel_text(target_name: &str) -> String {
    format!(
        r#"### auto-generated section `build` start
load("@rules_jvm//:jvm.bzl", "jvm_library")
load("@rules_jvm//:test.bzl", "jps_test")

jvm_library(
  name = "air-thing-tests_test_lib",
  srcs = glob(["testSrc/**/*.kt"]),
)

jps_test(
  name = "{target_name}",
  timeout = "moderate",
  sandbox = True,
  runtime_deps = [":air-thing-tests_test_lib"],
)
### auto-generated section `build` end"#
    )
}

/// The files of the fixture area: three modules with a test target, one with a test root and no target, and one
/// production-only module. [`air_accio`] is what accio knows of them.
pub fn air_tree() -> BTreeMap<String, String> {
    [
        ("plugins/air/shared/core/BUILD.bazel", build_bazel_text("ai-agent-core-tests_test")),
        (
            "plugins/air/shared/core/testSrc/AgentThreadIdentityTest.kt",
            "package com.intellij.air.shared.core\n\nclass AgentThreadIdentityTest {\n}\n".to_owned(),
        ),
        (
            "plugins/air/shared/core/testSrc/AgentThreadCliTest.kt",
            "package com.intellij.air.shared.core\n\nclass AgentThreadCliTest {\n}\n".to_owned(),
        ),
        (
            "plugins/air/backend/vcs/BUILD.bazel",
            build_bazel_text("air-backend-vcs-tests_test"),
        ),
        (
            "plugins/air/backend/vcs/testSrc/context/AgentPromptChangesTreeContextContributorTest.kt",
            "package com.intellij.air.backend.vcs.context\n\nclass AgentPromptChangesTreeContextContributorTest {\n}\n".to_owned(),
        ),
        (
            "plugins/air/frontend/prompt/vcs/BUILD.bazel",
            build_bazel_text("air-frontend-prompt-vcs-tests_test"),
        ),
        (
            "plugins/air/frontend/prompt/vcs/testSrc/context/AgentPromptChangesTreeContextContributorTest.kt",
            "package com.intellij.air.frontend.prompt.vcs.context\n\nclass AgentPromptChangesTreeContextContributorTest {\n}\n".to_owned(),
        ),
        (
            "plugins/air/notest/testSrc/OrphanTest.kt",
            "package com.intellij.air.notest\n\nclass OrphanTest {\n}\n".to_owned(),
        ),
        (
            "plugins/air/shared/api/src/Api.kt",
            "package com.intellij.air.shared.api\n\nclass Api\n".to_owned(),
        ),
    ]
    .into_iter()
    .map(|(path, text)| (path.to_owned(), text))
    .collect()
}

/// What accio knows of [`air_tree`]: each class with its FQN, and each module root.
pub fn air_accio() -> FakeAccio {
    let classes = [
        (
            "plugins/air/shared/core/testSrc/AgentThreadIdentityTest.kt",
            "com.intellij.air.shared.core.AgentThreadIdentityTest",
        ),
        (
            "plugins/air/shared/core/testSrc/AgentThreadCliTest.kt",
            "com.intellij.air.shared.core.AgentThreadCliTest",
        ),
        (
            "plugins/air/backend/vcs/testSrc/context/AgentPromptChangesTreeContextContributorTest.kt",
            "com.intellij.air.backend.vcs.context.AgentPromptChangesTreeContextContributorTest",
        ),
        (
            "plugins/air/frontend/prompt/vcs/testSrc/context/AgentPromptChangesTreeContextContributorTest.kt",
            "com.intellij.air.frontend.prompt.vcs.context.AgentPromptChangesTreeContextContributorTest",
        ),
        ("plugins/air/notest/testSrc/OrphanTest.kt", "com.intellij.air.notest.OrphanTest"),
        ("plugins/air/shared/api/src/Api.kt", "com.intellij.air.shared.api.Api"),
    ];
    let notest = "plugins/air/notest";
    let api = "plugins/air/shared/api";
    FakeAccio {
        classes: classes.iter().map(|(file, fqn)| ((*file).to_owned(), (*fqn).to_owned())).collect(),
        modules: vec![
            tests_module_json(
                "plugins/air/shared/core",
                "intellij.air.shared.core",
                "intellij.air.shared.core.tests",
                "ai-agent-core-tests_test",
            ),
            tests_module_json(
                "plugins/air/backend/vcs",
                "intellij.air.backend.vcs",
                "intellij.air.backend.vcs.tests",
                "air-backend-vcs-tests_test",
            ),
            tests_module_json(
                "plugins/air/frontend/prompt/vcs",
                "intellij.air.frontend.prompt.vcs",
                "intellij.air.frontend.prompt.vcs.tests",
                "air-frontend-prompt-vcs-tests_test",
            ),
            // A module with a test root and no `BUILD.bazel`.
            module_entry_json(
                notest,
                Some(&jps_json("intellij.air.notest.tests", notest, &[("testSrc", true)], None)),
                &[],
                None,
            ),
            // A production-only module.
            module_entry_json(
                api,
                Some(&jps_json("intellij.air.shared.api", api, &[("src", false)], None)),
                &[],
                Some(&[]),
            ),
        ],
        ..FakeAccio::default()
    }
}

/// [`air_tree`] with [`air_accio`].
pub fn fake_air_tree() -> FakeRuntime {
    let fake = FakeRuntime::with_tree(&air_tree());
    fake.state().accio = air_accio();
    fake
}

/// The refusal an operation raised, failing the test when it did not raise one.
#[track_caller]
pub fn refusal<T: std::fmt::Debug>(result: Result<T, Refusal>) -> Refusal {
    match result {
        Ok(value) => panic!("expected a refusal, got {value:?}"),
        Err(refusal) => refusal,
    }
}

/// The `lane/class` of each affected suite, joined by commas.
pub fn classes_of(affected: &crate::suites::Affected) -> String {
    affected
        .suites
        .iter()
        .map(|suite| format!("{}/{}", suite.lane, suite.class))
        .collect::<Vec<_>>()
        .join(",")
}

// --- the suite catalog -----------------------------------------------------------------------------------------

/// A suite document in the shape `generateFlowCatalog.ts` writes, cut down to the fields the join reads: the
/// suite's identity, the flow of its one profile and step, and what its routes reach.
#[derive(Clone, Debug, Default)]
pub struct SuiteFixture {
    pub suite: &'static str,
    pub lane: &'static str,
    pub class_name: &'static str,
    pub flow: &'static str,
    pub modules: Vec<&'static str>,
    pub implementation_flows: Vec<&'static str>,
}

fn json_strings(values: &[&str]) -> String {
    values.iter().map(|value| format!("\"{value}\"")).collect::<Vec<_>>().join(", ")
}

impl SuiteFixture {
    /// The document. An empty `implementationFlows` is absent rather than `[]`, which is also how the controller
    /// meets a document an older generator wrote.
    pub fn text(&self) -> String {
        let mut reach = format!(r#"  "modules": [{}],"#, json_strings(&self.modules));
        if !self.implementation_flows.is_empty() {
            reach.push_str(&format!(
                "\n  \"implementationFlows\": [{}],",
                json_strings(&self.implementation_flows)
            ));
        }
        format!(
            r#"{{
  "suite": "{suite}",
  "lane": "{lane}",
  "testClassName": "{class}",
{reach}
  "profiles": [
    {{ "name": "{suite}-first", "flow": "{flow}",
      "steps": [ {{ "flow": "{flow}", "step": "one" }} ] }}
  ]
}}"#,
            suite = self.suite,
            lane = self.lane,
            class = self.class_name,
            flow = self.flow,
        )
    }
}

/// One document for a suite the base catalog does not state, so it reaches nothing.
pub fn suite_document_text(suite: &'static str, lane: &'static str, class_name: &'static str, flow: &'static str) -> String {
    SuiteFixture {
        suite,
        lane,
        class_name,
        flow,
        ..SuiteFixture::default()
    }
    .text()
}

/// The three suites of the base catalog, keyed by suite id so a test can restate what one reaches.
pub fn base_suite_fixtures() -> BTreeMap<&'static str, SuiteFixture> {
    [
        SuiteFixture {
            suite: "manage-launch-preset-quick-start",
            lane: "UI",
            class_name: "AirManageLaunchPresetQuickStartGeneratedFlowUiTest",
            flow: "flow-manage-launch-preset",
            modules: vec!["intellij.air.session"],
            implementation_flows: vec![],
        },
        SuiteFixture {
            suite: "manage-launch-preset",
            lane: "GUI_CHAT",
            class_name: "AirManageLaunchPresetGeneratedFlowUiTest",
            flow: "flow-manage-launch-preset",
            modules: vec!["intellij.air.session"],
            implementation_flows: vec![],
        },
        SuiteFixture {
            suite: "new-session-terminal",
            lane: "UI_REAL",
            class_name: "AirNewSessionTerminalGeneratedFlowUiTest",
            flow: "flow-new-session-terminal",
            modules: vec!["intellij.air.terminal"],
            implementation_flows: vec![],
        },
    ]
    .into_iter()
    .map(|fixture| (fixture.suite, fixture))
    .collect()
}

/// The path of a suite's document in the tree.
pub fn document_path(suite: &str) -> String {
    let catalog = lanes().catalog().expect("the fixture lane table names a catalog");
    format!("{}/{suite}.json", catalog.flow_profile_dir)
}

/// Restates what one suite of the base catalog reaches. The generator writes both facts on the suite document, so
/// a test states them there too.
pub fn reaching(tree: &mut BTreeMap<String, String>, suite: &str, modules: &[&'static str], implementation_flows: &[&'static str]) {
    let mut fixture = base_suite_fixtures()
        .remove(suite)
        .unwrap_or_else(|| panic!("no base fixture for suite {suite}"));
    fixture.modules = modules.to_vec();
    fixture.implementation_flows = implementation_flows.to_vec();
    tree.insert(document_path(suite), fixture.text());
}

pub fn kdoc(tags: &[&str]) -> String {
    let mut lines = vec!["package air".to_owned(), String::new(), "/**".to_owned()];
    lines.extend(tags.iter().map(|tag| format!(" * {tag}")));
    lines.push(" */".to_owned());
    lines.push("class Subject".to_owned());
    lines.join("\n") + "\n"
}

/// The join under test has two halves that fail differently, so the fixture states both: a suite reached by its own
/// `[suite: …]` attribute, and a suite reached only because it walks a flow another file declares.
pub fn flow_catalog_tree() -> BTreeMap<String, String> {
    let mut tree = BTreeMap::new();
    // The declaring file of the launch-preset story: one profile naming its suite, and a second flow with no
    // scenario of its own.
    tree.insert(
        "plugins/air/frontend/src/Surfaces.kt".to_owned(),
        kdoc(&[
            "@flow flow-manage-launch-preset",
            "@flowStep manage-preset-start-last-used-session ext:user -> module:x : click",
            "@flow flow-new-session-launch New-session launch pipeline [kind: implementation]",
            "@flowTest manage-preset-quick-start [suite: manage-launch-preset-quick-start] [fixture: quick-start]",
        ]),
    );
    // A file that only walks the flow: no profile, no suite attribute.
    tree.insert(
        "plugins/air/frontend/src/Rows.kt".to_owned(),
        kdoc(&["@flow flow-manage-launch-preset"]),
    );
    // Production code the flow model does not name at all. This tree declares no `.iml`, so nothing above it says
    // which module it belongs to either.
    tree.insert(
        "plugins/air/frontend/src/Plain.kt".to_owned(),
        "package air\n\nclass Plain\n".to_owned(),
    );
    // The reach travels on each suite document. It deliberately reaches neither `Plain.kt` nor its directory.
    for (suite, fixture) in base_suite_fixtures() {
        tree.insert(document_path(suite), fixture.text());
    }
    tree
}

// --- the module roots of the fake accio ------------------------------------------------------------------------

/// A JPS module of a `modules` entry, as JSON text. Each root is a path relative to `dir` and whether it is a test
/// source root. `test` is the JSON text of the test module that the module names, if any.
pub fn jps_json(name: &str, dir: &str, roots: &[(&str, bool)], test: Option<&str>) -> String {
    let roots: Vec<serde_json::Value> = roots
        .iter()
        .map(|(path, test)| {
            let mut root = serde_json::json!({ "path": format!("{dir}/{path}") });
            if *test {
                root["test"] = serde_json::Value::Bool(true);
            }
            root
        })
        .collect();
    let mut module = serde_json::json!({
        "name": name,
        "manifest": format!("{dir}/{name}.iml"),
        "roots": roots,
    });
    if let Some(test) = test {
        module["test"] = serde_json::from_str(test).expect("a fixture test module is JSON");
    }
    module.to_string()
}

/// One rule of a `BUILD.bazel` in `root`, as JSON text. Each runtime dependency is a target name in the same root.
pub fn target_json(root: &str, kind: &str, name: &str, module_name: Option<&str>, env_keys: &[&str], runtime_deps: &[&str]) -> String {
    let deps: Vec<serde_json::Value> = runtime_deps
        .iter()
        .map(|dep| {
            serde_json::json!({
                "label": format!(":{dep}"),
                "attr": "runtime_deps",
                "scope": "test",
                "targets": [{ "root": root, "name": dep }],
            })
        })
        .collect();
    let mut target = serde_json::json!({
        "name": name,
        "kind": kind,
        "line": 1,
        "test": kind != "jvm_library" || name.ends_with("_test_lib"),
        "deps": deps,
    });
    if let Some(module_name) = module_name {
        target["module_name"] = serde_json::Value::from(module_name);
    }
    if !env_keys.is_empty() {
        target["env_keys"] = serde_json::json!(env_keys);
    }
    target.to_string()
}

/// One entry of the `modules` answer, as JSON text: the root, its JPS modules, and the targets of its `BUILD.bazel`.
/// `targets` is `None` for a root without a `BUILD.bazel`.
pub fn module_entry_json(root: &str, jps: Option<&str>, jps_more: &[&str], targets: Option<&[String]>) -> String {
    let parse = |text: &str| -> serde_json::Value { serde_json::from_str(text).expect("a fixture part is JSON") };
    let mut entry = serde_json::json!({ "root": root });
    if let Some(jps) = jps {
        entry["jps"] = parse(jps);
    }
    if !jps_more.is_empty() {
        entry["jps_more"] = jps_more.iter().map(|text| parse(text)).collect();
    }
    if let Some(targets) = targets {
        entry["bazel"] = serde_json::json!({
            "build": format!("{root}/BUILD.bazel"),
            "targets": targets.iter().map(|text| parse(text)).collect::<Vec<_>>(),
        });
    }
    entry.to_string()
}

/// The usual module root, as JSON text: the main module with `src`, its test module with `testSrc`, the library of the
/// main module, the `_test_lib` of the test module, and the `jps_test` that runs the `_test_lib`.
pub fn tests_module_json(root: &str, main_module: &str, tests_module: &str, test_target: &str) -> String {
    let test_library = format!("{}_test_lib", test_target.strip_suffix("_test").unwrap_or(test_target));
    let main_library = main_module.rsplit('.').next().unwrap_or(main_module);
    let tests = jps_json(tests_module, root, &[("testSrc", true)], None);
    module_entry_json(
        root,
        Some(&jps_json(main_module, root, &[("src", false)], Some(&tests))),
        &[],
        Some(&[
            target_json(root, "jps_test", test_target, None, &[], &[&test_library]),
            target_json(root, "jvm_library", main_library, Some(main_module), &[], &[]),
            target_json(root, "jvm_library", &test_library, Some(tests_module), &[], &[]),
        ]),
    )
}

// --- test.xml and BEP builders ---------------------------------------------------------------------------------

/// The failure element of one case.
#[derive(Clone, Debug, Default)]
pub struct FailureSpec {
    pub failure_type: String,
    pub message: String,
    pub stack: String,
}

#[derive(Clone, Debug, Default)]
pub struct CaseSpec {
    pub name: String,
    /// Defaults to the suite's name.
    pub class_name: String,
    pub failure: Option<FailureSpec>,
    pub skipped: bool,
}

/// A passing case.
pub fn case(name: &str) -> CaseSpec {
    CaseSpec {
        name: name.to_owned(),
        ..CaseSpec::default()
    }
}

/// A failing case.
pub fn failing(name: &str, failure_type: &str, message: &str, stack: &str) -> CaseSpec {
    CaseSpec {
        failure: Some(FailureSpec {
            failure_type: failure_type.to_owned(),
            message: message.to_owned(),
            stack: stack.to_owned(),
        }),
        ..case(name)
    }
}

#[derive(Clone, Debug, Default)]
pub struct SuiteSpec {
    pub name: String,
    pub cases: Vec<CaseSpec>,
    pub system_out: String,
    pub timestamp: String,
}

/// A suite of these cases.
pub fn suite(name: &str, cases: Vec<CaseSpec>) -> SuiteSpec {
    SuiteSpec {
        name: name.to_owned(),
        cases,
        ..SuiteSpec::default()
    }
}

fn escape_attribute(value: &str) -> String {
    value.replace('&', "&amp;").replace('<', "&lt;").replace('"', "&quot;")
}

/// A test.xml document in the shape the repository's JUnit 5 runner writes.
pub fn suite_xml(spec: &SuiteSpec) -> String {
    let mut failures = 0;
    let mut skipped = 0;
    let mut body = Vec::new();
    for entry in &spec.cases {
        let class_name = if entry.class_name.is_empty() {
            &spec.name
        } else {
            &entry.class_name
        };
        let attributes = format!(
            r#"name="{}" classname="{}" time="0.01""#,
            escape_attribute(&entry.name),
            escape_attribute(class_name)
        );
        if let Some(failure) = &entry.failure {
            failures += 1;
            body.push(format!(
                "    <testcase {attributes}>\n      <failure message=\"{}\" type=\"{}\"><![CDATA[{}]]></failure>\n    </testcase>",
                escape_attribute(&failure.message),
                failure.failure_type,
                failure.stack
            ));
        } else if entry.skipped {
            skipped += 1;
            body.push(format!("    <testcase {attributes}><skipped /></testcase>"));
        } else {
            body.push(format!("    <testcase {attributes} />"));
        }
    }
    let timestamp = if spec.timestamp.is_empty() {
        String::new()
    } else {
        format!(r#" timestamp="{}""#, spec.timestamp)
    };
    let tail = if spec.system_out.is_empty() {
        String::new()
    } else {
        format!("\n    <system-out><![CDATA[{}]]></system-out>", spec.system_out)
    };
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="{name}"{timestamp} tests="{tests}"
      failures="{failures}" errors="0" skipped="{skipped}" time="0.5">
{body}{tail}
  </testsuite>
</testsuites>"#,
        name = spec.name,
        tests = spec.cases.len(),
        body = body.join("\n"),
    )
}

/// The verbatim shape bazel synthesizes when the runner wrote no report of its own.
pub fn wrapper_xml(label: &str, exit_code: i32, log: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="{label}" tests="1" failures="0" errors="1">
    <testcase name="{label}" status="run" duration="1200" time="1.2">
      <error message="exited with error code {exit_code}"></error>
    </testcase>
    <system-out><![CDATA[{log}]]></system-out>
  </testsuite>
</testsuites>"#
    )
}

pub const BUCKETING_XML: &str =
    r#"<testsuites><testsuite name="Bucketing" tests="0" failures="0" errors="0" skipped="0"></testsuite></testsuites>"#;

/// Events as the NDJSON lines of a BEP file.
pub fn bep_lines(events: &[serde_json::Value]) -> String {
    events.iter().map(serde_json::Value::to_string).collect::<Vec<_>>().join("\n")
}

/// The BEP of a build that failed to load the pattern, as bazel 9 writes it: the `aborted` event of the pattern with
/// the description of the cause, and the end of the build with exit code 1.
pub fn loading_failure_events(pattern: &str, description: &str) -> [serde_json::Value; 2] {
    [
        serde_json::json!({
            "id": {"pattern": {"pattern": [pattern]}},
            "aborted": {"reason": "LOADING_FAILURE", "description": description},
        }),
        serde_json::json!({"id": {"buildFinished": {}}, "finished": {"exitCode": {"name": "BUILD_FAILURE", "code": 1}}}),
    ]
}

/// One attempt; zero and empty fields take the defaults a real passing attempt would have.
#[derive(Clone, Debug, Default)]
pub struct AttemptSpec {
    pub label: &'static str,
    pub status: &'static str,
    pub shard: i64,
    pub run: i64,
    pub attempt: i64,
    pub cached: bool,
    pub duration_ms: u64,
    pub xml: &'static str,
    pub log: &'static str,
}

pub fn attempt(label: &'static str) -> AttemptSpec {
    AttemptSpec {
        label,
        ..AttemptSpec::default()
    }
}

pub fn test_result_event(spec: &AttemptSpec) -> serde_json::Value {
    let or_one = |value: i64| if value == 0 { 1 } else { value };
    let or = |value: &'static str, fallback: &'static str| {
        if value.is_empty() { fallback } else { value }
    };
    serde_json::json!({
        "id": {
            "testResult": {
                "label": spec.label,
                "run": or_one(spec.run),
                "shard": or_one(spec.shard),
                "attempt": or_one(spec.attempt),
            }
        },
        "testResult": {
            "status": or(spec.status, "PASSED"),
            "cachedLocally": spec.cached,
            "testAttemptDurationMillis": if spec.duration_ms == 0 { 1234 } else { spec.duration_ms },
            "testActionOutput": [
                { "name": "test.log", "uri": format!("file://{}", or(spec.log, "/exec/testlogs/pkg/target/test.log")) },
                { "name": "test.xml", "uri": format!("file://{}", or(spec.xml, "/exec/testlogs/pkg/target/test.xml")) },
            ],
        },
    })
}
