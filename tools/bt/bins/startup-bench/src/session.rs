//! The session directory: its inputs in `session.json`, the sandbox template, and the run directories.
//!
//! The layout:
//!
//! - `session.json`: the inputs of the session, which `replay` reads.
//! - `launch.sh`, `build.log`: the launcher that `bazel run --script_path` wrote, and the output of Bazel.
//! - `template/`: the sandbox template (`config`, `system`, `plugins`), with the FUS test scheme.
//! - `<arm>-prime/`, `<arm>-run-NN/`: one directory per run, each with `sandbox/`, `log/` and `result.json`.
//! - `<arm>-sandbox/`: the sandbox of the run that is running now. See [`live_sandbox`].
//! - `summary.json`: the summary.

use std::path::{Path, PathBuf};

use anyhow::Context;
use serde::{Deserialize, Serialize};

use crate::arm::Arm;
use crate::files;
use crate::record::RunId;

pub(crate) const SESSION_FILE: &str = "session.json";
pub(crate) const SUMMARY_FILE: &str = "summary.json";
pub(crate) const RESULT_FILE: &str = "result.json";
pub(crate) const LAUNCH_SCRIPT: &str = "launch.sh";
pub(crate) const TEMPLATE_DIR: &str = "template";

/// The directory below the checkout root that holds the default session directories.
pub(crate) const RUNS_DIR: &str = "out/startup-bench/runs";

/// The license of the dev data, below the checkout root.
pub(crate) const LICENSE_FILE: &str = "out/dev-data/idea/config/idea.key";

/// The settings that a sandbox template starts with: no exit confirmation and no tip of the day.
pub(crate) const GENERAL_SETTINGS: &str = r#"<application>
  <component name="GeneralSettings">
    <option name="confirmExit" value="false" />
    <option name="showTipsOnStartup" value="false" />
  </component>
</application>
"#;

/// The directory of the welcome project inside a sandbox.
pub(crate) const PROJECTS_DIR: &str = "projects";

/// The base directory of the projects, and thus of the welcome project. `WelcomeScreenProjectProvider` reads
/// `GeneralLocalSettings.defaultProjectDirectory`, and without it the IDE uses `~/IdeaProjects`, which every run and
/// the real IDE share.
pub(crate) fn local_settings(projects: &Path) -> String {
    format!(
        r#"<application>
  <component name="GeneralLocalSettings">
    <option name="defaultProjectDirectory" value="{}" />
  </component>
</application>
"#,
        xml_attribute(&projects.display().to_string())
    )
}

fn xml_attribute(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}

/// Points the welcome project of a sandbox at its own `projects` directory.
pub(crate) fn own_welcome_project(sandbox: &Sandbox) -> anyhow::Result<()> {
    files::write_text(
        &sandbox.config().join("options").join("ide.general.local.xml"),
        &local_settings(&sandbox.root.join(PROJECTS_DIR)),
    )
}

/// The path at which every run of an arm runs: `<session>/<arm>-sandbox`.
///
/// The path is the same for each run of the arm, so the caches that the IDE keys by the path of the welcome project
/// match in each copy of the primed sandbox. After the run, the sandbox moves into the run directory.
pub(crate) fn live_sandbox(session: &Path, arm: Arm) -> Sandbox {
    Sandbox::new(session.join(format!("{}-sandbox", arm.label())))
}

/// The inputs of a session.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SessionInfo {
    /// `welcome` or `open-project`.
    pub(crate) command: String,
    pub(crate) target: String,
    pub(crate) git: GitInfo,
    pub(crate) arms: Vec<Arm>,
    pub(crate) runs: u32,
    pub(crate) cold: bool,
    pub(crate) hold_ms: u64,
    pub(crate) profile: bool,
    pub(crate) project: Option<String>,
    /// What the session changed or dropped, such as a JVM flag that the JVM refuses.
    pub(crate) warnings: Vec<String>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct GitInfo {
    pub(crate) commit: String,
    pub(crate) dirty: bool,
}

/// The three directories of an IDE sandbox.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Sandbox {
    pub(crate) root: PathBuf,
}

impl Sandbox {
    pub(crate) fn new(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }

    pub(crate) fn config(&self) -> PathBuf {
        self.root.join("config")
    }

    pub(crate) fn system(&self) -> PathBuf {
        self.root.join("system")
    }

    pub(crate) fn plugins(&self) -> PathBuf {
        self.root.join("plugins")
    }

    /// The FUS test scheme that lets the validator keep the welcome event ids.
    pub(crate) fn test_scheme(&self) -> PathBuf {
        self.config().join("event-log-metadata").join("fus").join("test-events-scheme.json")
    }
}

/// The default session directory: `out/startup-bench/runs/<yyyymmdd-HHMMSS>` below the checkout root.
pub(crate) fn default_dir(repo_root: &Path) -> PathBuf {
    let stamp = jiff::Zoned::now().strftime("%Y%m%d-%H%M%S").to_string();
    repo_root.join(RUNS_DIR).join(stamp)
}

/// Writes the sandbox template: the general settings and the license. The FUS test scheme comes from a headless run.
pub(crate) fn write_template(template: &Sandbox, license: &Path) -> anyhow::Result<()> {
    files::write_text(&template.config().join("options").join("ide.general.xml"), GENERAL_SETTINGS)?;
    std::fs::copy(license, template.config().join("idea.key")).with_context(|| format!("cannot copy the license {}", license.display()))?;
    for dir in [template.system(), template.plugins()] {
        std::fs::create_dir_all(&dir).with_context(|| format!("cannot create {}", dir.display()))?;
    }
    Ok(())
}

/// Reads `session.json`.
pub(crate) fn read_info(session: &Path) -> anyhow::Result<SessionInfo> {
    let path = session.join(SESSION_FILE);
    let text = files::read_text(&path)?;
    serde_json::from_str(&text).with_context(|| format!("{} is not a session file", path.display()))
}

/// The run directories of a session, in name order.
pub(crate) fn run_dirs(session: &Path) -> anyhow::Result<Vec<(RunId, PathBuf)>> {
    let mut runs = Vec::new();
    for entry in std::fs::read_dir(session).with_context(|| format!("cannot list {}", session.display()))? {
        let entry = entry.with_context(|| format!("cannot list {}", session.display()))?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if let Some(id) = RunId::parse(&name)
            && entry.path().join(RESULT_FILE).exists()
        {
            runs.push((id, entry.path()));
        }
    }
    runs.sort_by(|left, right| left.1.cmp(&right.1));
    Ok(runs)
}

#[cfg(test)]
mod tests;
