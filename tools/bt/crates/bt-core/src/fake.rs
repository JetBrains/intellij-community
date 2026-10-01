//! The in-memory [`Runtime`], and the fixture builders the tests share: this crate's, the `bt` binary's, and the
//! tests of a crate that resolves through this one.
//!
//! No test invokes bazel or touches the real filesystem: resolution, a whole bazel run, result collection and the
//! digest are all exercised against a map of files. Fixtures are assembled by the builders here rather than
//! committed: a real failing test.xml measured 146 KB, almost all of it `<system-out>`, and a lane BEP runs to tens
//! of MB. To capture a real BEP for manual inspection instead, add `--build_event_json_file=/tmp/bep.json` to any
//! bazel test run.

use std::collections::{BTreeMap, BTreeSet};
use std::ffi::OsString;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{LazyLock, Mutex, MutexGuard};

use crate::areas::{AREAS_FILE, Area, Areas};
use crate::lanes::Lanes;
use crate::refusal::Refusal;
use crate::runtime::{DirEntry, Heartbeat, Platform, Runtime, SpawnResult};

pub const REPO_ROOT: &str = "/repo";

/// The directory of the fixture area, the one [`air_tree`] fills.
pub const AREA_DIR: &str = "plugins/air";

/// Where [`area_files`] puts the fixture lane table.
pub const LANES_FILE: &str = "plugins/air/tests/integration/lanes.json";

/// The fixture lane table: a copy of the Air area's, with its suite catalog, so every rule here is tested against a
/// realistic table. It is a copy on purpose, so a change of the real table does not change what these tests
/// state.
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
    pub clock: u64,
    /// Makes every read fail loudly on top of being recorded. Both matter: the production helpers swallow a
    /// missing file, so a failure alone could be absorbed into an empty scan and read as success, while the
    /// recording alone would let a swallowed failure pass for laziness.
    pub forbidden: bool,
    /// The files of the tree the checkout ignores; see [`FakeRuntime::ignore`].
    pub ignored: BTreeSet<String>,
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

    pub fn spawned(&self) -> Vec<Vec<String>> {
        self.state().spawned.clone()
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

    fn exists(&self, path: &Path) -> bool {
        let path = key(path);
        let prefix = format!("{path}/");
        let state = self.state();
        state.files.contains_key(&path) || state.files.keys().any(|held| held.starts_with(&prefix))
    }

    /// Records the argv, beats once as a 30 s run would, and answers the scripted result.
    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
        let result = {
            let mut state = self.state();
            state
                .spawned
                .push(command.iter().map(|part| part.to_string_lossy().into_owned()).collect());
            state.clock += 5000;
            state.spawn_result.clone()
        };
        // Outside the lock: the heartbeat writes a progress line through this same runtime.
        if let Some(beat) = heartbeat {
            beat(30_000);
        }
        result
    }

    fn temp_file(&self, prefix: &str) -> PathBuf {
        PathBuf::from(format!("/tmp/{prefix}.json"))
    }

    fn remove(&self, path: &Path) -> io::Result<()> {
        self.state().removed.push(key(path));
        Ok(())
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
