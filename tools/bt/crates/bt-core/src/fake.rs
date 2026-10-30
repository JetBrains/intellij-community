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

/// The files `bt.json` and the lane table add to a tree, for a test that loads the areas from the checkout.
pub fn area_files() -> [(String, String); 2] {
    [
        (
            AREAS_FILE.to_owned(),
            format!(r#"{{ "areas": [ {{ "dir": "{AREA_DIR}", "lanes": "{LANES_FILE}" }} ] }}"#),
        ),
        (LANES_FILE.to_owned(), LANES_TEXT.to_owned()),
    ]
}

/// The fake's state. Behind a mutex because the tree scan fans out over worker threads.
#[derive(Default)]
pub struct State {
    pub files: BTreeMap<String, String>,
    /// Every read attempted, in order, so a test can pin how much I/O a code path does.
    pub reads: Vec<String>,
    pub spawned: Vec<Vec<String>>,
    pub removed: Vec<String>,
    pub stdout: Vec<String>,
    pub stderr: Vec<String>,
    pub spawn_result: SpawnResult,
    /// The answers of the next spawns, in order. A spawn takes the first one, and answers [`State::spawn_result`]
    /// when none is left.
    pub spawn_results: VecDeque<SpawnResult>,
    /// The stdin of each [`Runtime::spawn_with_input`], in order.
    pub inputs: Vec<String>,
    /// The idle limit of each [`Runtime::spawn_watched`], in order.
    pub idle_limits: Vec<std::time::Duration>,
    /// Answers by command: a spawn whose argv, joined by spaces, holds the text of a rule answers the result of the
    /// first such rule, before the queue of [`State::spawn_results`] is asked.
    pub spawn_rules: Vec<(String, SpawnResult)>,
    /// The answer of [`Runtime::confirm`]: `None` stands for a stdin that is not a terminal.
    pub confirm_answer: Option<bool>,
    /// The questions that [`Runtime::confirm`] was asked, in order.
    pub questions: Vec<String>,
    pub clock: u64,
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
        if let Some((_, result)) = state.spawn_rules.iter().find(|(text, _)| joined.contains(text.as_str())) {
            return result.clone();
        }
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
        self.state().files.insert(key(path), text.to_owned());
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
        Ok(self.state().checkout_trees.pop_front())
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

    /// Records the argv and the idle limit, and answers the scripted result.
    fn spawn_watched(&self, command: &[OsString], idle: std::time::Duration) -> SpawnResult {
        self.state().idle_limits.push(idle);
        self.scripted_spawn(command)
    }

    /// Records the argv and the input, and answers the scripted result.
    fn spawn_with_input(&self, command: &[OsString], input: &str) -> SpawnResult {
        self.state().inputs.push(input.to_owned());
        self.scripted_spawn(command)
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

    fn confirm(&self, question: &str) -> Option<bool> {
        let mut state = self.state();
        state.questions.push(question.to_owned());
        state.confirm_answer
    }

    fn now_ms(&self) -> u64 {
        self.state().clock
    }

    fn write(&self, text: &str) {
        self.state().stdout.push(text.to_owned());
    }

    fn write_error(&self, text: &str) {
        self.state().stderr.push(text.to_owned());
    }
}

// --- fixture builders ----------------------------------------------------------------------------------------

pub fn iml_text(source_folders: &[&str]) -> String {
    let indented: Vec<String> = source_folders.iter().map(|folder| format!("      {folder}")).collect();
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$">
{}
    </content>
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>"#,
        indented.join("\n")
    )
}

/// A BUILD.bazel of a tests module and its main module, in the shape of `plugins/air/frontend/loopback.proxy/BUILD.bazel`.
///
/// The `jps_test` comes first, followed by the `skip generation section` marker of the tests module. The main library is
/// named after the last part of the main module, and it carries `module_name` of the main module. The `_test_lib` has
/// `associates` and no `module_name`. Only its `build <module>` section names the tests module.
pub fn module_build_bazel_text(module: &str, target_name: &str) -> String {
    let main_module = module
        .strip_suffix(".tests")
        .unwrap_or_else(|| panic!("{module} is not a tests module"));
    let main_library = main_module.rsplit('.').next().unwrap_or(main_module);
    let test_library = format!("{}_test_lib", target_name.strip_suffix("_test").unwrap_or(target_name));
    format!(
        r#"load("@community//build:tests-options.bzl", "jps_test")
load("@rules_jvm//:jvm.bzl", "jvm_library")

jps_test(
    name = "{target_name}",
    runtime_deps = [":{test_library}"],
)
### skip generation section `test {module}`

### auto-generated section `build {main_module}` start
jvm_library(
    name = "{main_library}",
    srcs = glob(["src/**/*.kt"], allow_empty = True),
    module_name = "{main_module}",
    visibility = ["//visibility:public"],
)
### auto-generated section `build {main_module}` end

### auto-generated section `build {module}` start
jvm_library(
    name = "{test_library}",
    testonly = True,
    srcs = glob(["testSrc/**/*.kt"], allow_empty = True),
    associates = [":{main_library}"],
    visibility = ["//visibility:public"],
)
### auto-generated section `build {module}` end"#
    )
}

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

/// The fixture every resolution test resolves against: three modules with runnable targets, one with a test root
/// and no target, and one production-only module.
pub fn air_tree() -> BTreeMap<String, String> {
    let source_root = |prefix: &str| {
        iml_text(&[&format!(
            r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" packagePrefix="{prefix}" />"#
        )])
    };
    [
        (
            "plugins/air/shared/core/intellij.air.shared.core.tests.iml",
            source_root("com.intellij.air.shared.core"),
        ),
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
            "plugins/air/backend/vcs/intellij.air.backend.vcs.tests.iml",
            source_root("com.intellij.air.backend.vcs"),
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
            "plugins/air/frontend/prompt/vcs/intellij.air.frontend.prompt.vcs.tests.iml",
            source_root("com.intellij.air.frontend.prompt.vcs"),
        ),
        (
            "plugins/air/frontend/prompt/vcs/BUILD.bazel",
            build_bazel_text("air-frontend-prompt-vcs-tests_test"),
        ),
        (
            "plugins/air/frontend/prompt/vcs/testSrc/context/AgentPromptChangesTreeContextContributorTest.kt",
            "package com.intellij.air.frontend.prompt.vcs.context\n\nclass AgentPromptChangesTreeContextContributorTest {\n}\n".to_owned(),
        ),
        // A module whose .iml declares a test root but which has no jps_test target at all.
        (
            "plugins/air/notest/intellij.air.notest.tests.iml",
            iml_text(&[r#"<sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" />"#]),
        ),
        (
            "plugins/air/notest/testSrc/OrphanTest.kt",
            "package com.intellij.air.notest\n\nclass OrphanTest {\n}\n".to_owned(),
        ),
        // Production-only module: no test root, so nothing is indexed from it.
        (
            "plugins/air/shared/api/intellij.air.shared.api.iml",
            iml_text(&[r#"<sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />"#]),
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

pub fn fake_air_tree() -> FakeRuntime {
    FakeRuntime::with_tree(&air_tree())
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
