//! Where bundles are looked for. A root is a directory tree, a zip, or, for the VM reports, a glob under a
//! directory: those reports sit beside worker state nothing here should walk.

use std::path::{Path, PathBuf};

use avl_trace::vocabulary;
use serde::Serialize;

vocabulary! {
    /// Why a root is looked in, for the listing and the startup lines.
    pub enum RootKind {
        /// The AIR lanes' `bazel-testlogs`, whose `test.outputs` hold a Bazel run's bundles: zipped into
        /// `outputs.zip`, or as directories while the test runs and under `--nozip_undeclared_test_outputs`.
        BazelTestlogs = "bazel-testlogs",
        /// The controller's per-iteration zips, `<WorkerDir>/reports/<runId>/traces/<iterationId>.zip`.
        VmReports = "vm-reports",
        /// The controller's per-scenario zips, `<RuntimeRoot>/runs/<runId>/traces/<iterationId>/<n>.zip`, which reach
        /// the host while the run goes on.
        VmRuns = "vm-runs",
        /// The runs that the controller exported into the checkout, as [VM_RUNS_GLOB] under
        /// `<repo>/out/air/vm-export/runs`: the runs of another host, which `bd-host` copied back.
        VmExport = "vm-export",
        /// A `--root`.
        Flag = "flag",
        /// The `bazel.logs.zip` of a TeamCity build, which `--teamcity` downloaded.
        TeamCity = "teamcity",
    }
}

/// Where the bundles of a root come from, when that is not this machine: a CI build. Each bundle of the root carries
/// it, so the viewer can say which build and which shard ran a scenario.
#[derive(Serialize, Clone, PartialEq, Eq, Debug)]
pub struct Origin {
    /// The name of the build's configuration, such as `Air Plugin UI Tests / 1`.
    pub name: String,
    /// The build's id.
    pub id: String,
    /// The build's page.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
}

/// One place to look.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct Root {
    pub kind: RootKind,
    /// The directory or zip. Empty when it could not be resolved, and then the error says why.
    pub path: PathBuf,
    /// When set, the only thing looked at under the path, `/`-separated; the path is then not walked.
    pub glob: Option<&'static str>,
    pub error: Option<String>,
    /// Where the root's bundles come from, when it is not this machine.
    pub origin: Option<Origin>,
}

impl Root {
    /// A root at a path, walked whole.
    pub fn new(kind: RootKind, path: impl Into<PathBuf>) -> Self {
        Self {
            kind,
            path: path.into(),
            glob: None,
            error: None,
            origin: None,
        }
    }
}

/// Where the controller leaves an iteration's traces, under the runtime root's `workers`:
/// `<worker>/reports/<runId>/traces/<iterationId>.zip`, a worker's directory being `workers/<worker>`.
pub const VM_REPORTS_GLOB: &str = "*/reports/*/traces/*.zip";

/// The directory of the controller's runs under the runtime root: one directory for each run, as the controller's
/// journal makes it.
pub const VM_RUNS_DIR: &str = "runs";

/// The export directory of a checkout, relative to its root: what the controller exports with `AIR_VM_EXPORT`. It
/// has the layout of a runtime root, [VM_RUNS_DIR] and below.
pub const VM_EXPORT_DIR: &str = "out/air/vm-export";

/// The directory inside one run's directory that holds its pulled traces, one directory for each iteration.
pub const VM_RUN_TRACES_DIR: &str = "traces";

/// The directory inside one exported run's directory that holds the report of each iteration, its JUnit XML and its
/// evidence.
pub const VM_RUN_REPORTS_DIR: &str = "reports";

/// Where the controller leaves each scenario's trace of a run, under [VM_RUNS_DIR]:
/// `<runId>/traces/<iterationId>/<n>.zip`. Spelled out because a const cannot join [VM_RUN_TRACES_DIR] in; a test
/// pins the two together.
pub const VM_RUNS_GLOB: &str = "*/traces/*/*.zip";

/// The four places a trace is left on this machine: the Air lanes' `bazel-testlogs` and the export directory of the
/// checkout, when there is a checkout, and the controller's two roots under its runtime root. A runtime root that
/// could not be resolved gives the controller's two roots its error instead of a path.
pub fn default_roots(repo: Option<&Path>, runtime_root: Result<&Path, &str>) -> Vec<Root> {
    let mut roots = Vec::new();
    if let Some(repo) = repo {
        let testlogs = ["bazel-testlogs", "plugins", "air", "tests", "integration"];
        roots.push(Root::new(
            RootKind::BazelTestlogs,
            testlogs.iter().fold(repo.join("out"), |path, segment| path.join(segment)),
        ));
        roots.push(Root {
            glob: Some(VM_RUNS_GLOB),
            ..Root::new(RootKind::VmExport, repo.join(VM_EXPORT_DIR).join(VM_RUNS_DIR))
        });
    }
    match runtime_root {
        Ok(runtime) => {
            roots.push(Root {
                glob: Some(VM_REPORTS_GLOB),
                ..Root::new(RootKind::VmReports, runtime.join("workers"))
            });
            roots.push(Root {
                glob: Some(VM_RUNS_GLOB),
                ..Root::new(RootKind::VmRuns, runtime.join(VM_RUNS_DIR))
            });
        }
        Err(error) => {
            for kind in [RootKind::VmReports, RootKind::VmRuns] {
                roots.push(Root {
                    error: Some(error.to_owned()),
                    ..Root::new(kind, PathBuf::new())
                });
            }
        }
    }
    roots
}

/// Whether candidate is directory itself or under it, segment by segment. Both are real, absolute paths.
pub fn within(candidate: &Path, directory: &Path) -> bool {
    candidate.starts_with(directory)
}
