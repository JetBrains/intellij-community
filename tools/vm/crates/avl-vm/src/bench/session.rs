//! The session directory: its inputs in `session.json`, the sandbox template, and the run directories.
//!
//! The default session directory is `<runtime root>/bench/runs/<actor id>`, which also holds the run journal
//! `events.ndjson`. The layout:
//!
//! - `session.json`: the inputs of the session, which `replay` reads. It names the generation and its digest.
//! - `build.log`: the output of the host Bazel build.
//! - `template/`: the sandbox template (`config`, `system`, `plugins`), with the FUS test scheme. A `project` session
//!   also writes its project into `template/projects/<name>`.
//! - `<arm>-prime/`, `<arm>-run-NN/`: one directory per run, each with `sandbox/`, `log/`, `launch.args` and
//!   `result.json`.
//! - `<arm>-sandbox/`: the sandbox of the run that is running now. See [`live_sandbox`].
//! - `summary.json`: the summary.

use std::path::{Path, PathBuf};
use std::time::SystemTime;

use anyhow::Context;
use avl_base::{Exit, Refusal, RefusalExt};
use avl_trace_tools::discover::VM_RUNS_DIR;
use serde::{Deserialize, Serialize};

use super::arm::Arm;
use super::files;
use super::record::RunId;
use super::summary::Summary;

pub(crate) const SESSION_FILE: &str = "session.json";
pub(crate) const SUMMARY_FILE: &str = "summary.json";
pub(crate) const RESULT_FILE: &str = "result.json";
/// The Jaeger trace of a run, which `idea.diagnostic.opentelemetry.file` names.
pub(crate) const TRACE_FILE: &str = "opentelemetry.json";
/// The JVM class-load log of a run, which `-Xlog:class+load` writes. See [`super::classload`].
pub(crate) const CLASS_LOAD_LOG: &str = "class-load.log";
pub(crate) const TEMPLATE_DIR: &str = "template";

/// The directory below the runtime root that holds the bench state: `generations/` and `runs/`.
pub(crate) const BENCH_DIR: &str = "bench";

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

/// The directory of the welcome project inside a sandbox, and of the project of a `project` session.
pub(crate) const PROJECTS_DIR: &str = "projects";

/// The argument of `bench project` that names the generated Markdown project.
pub(crate) const MARKDOWN_PROJECT: &str = "markdown";

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

/// The advanced settings of a sandbox whose project opens no editor. On the first open of a project, the IDE opens its
/// `README.md`, and the next start restores that editor. The generated Markdown project has one.
pub(crate) const NO_README_SETTINGS: &str = r#"<application>
  <component name="AdvancedSettings">
    <option name="settings">
      <map>
        <entry key="ide.open.readme.md.on.startup" value="false" />
      </map>
    </option>
  </component>
</application>
"#;

/// Keeps the IDE from opening the README of a project in this sandbox. See [`NO_README_SETTINGS`].
pub(crate) fn open_no_readme(sandbox: &Sandbox) -> anyhow::Result<()> {
    files::write_text(&sandbox.config().join("options").join("advancedSettings.xml"), NO_README_SETTINGS)
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
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SessionInfo {
    /// `welcome`, `open-project` or `project`.
    pub(crate) command: String,
    pub(crate) target: String,
    pub(crate) git: GitInfo,
    /// The staged distribution that every run of the session starts from.
    pub(crate) generation: GenerationRef,
    pub(crate) arms: Vec<Arm>,
    pub(crate) runs: u32,
    pub(crate) cold: bool,
    pub(crate) hold_ms: u64,
    pub(crate) profile: bool,
    /// The project of `open-project`, or the project of `project` inside the template.
    pub(crate) project: Option<String>,
    /// The file that the prime run of the `project` arm opens, relative to the project. A session without that arm
    /// has none.
    #[serde(default)]
    pub(crate) project_file: Option<String>,
    /// The `additional.modules` of the dist config. The distribution holds them, so the launch passes none.
    #[serde(default)]
    pub(crate) additional_modules: Vec<String>,
    /// The limit of the 1-minute load that the session checked: `--max-load`, else the CPU count. It is none when the
    /// session checked no load, and in a session from before the limit.
    #[serde(default)]
    pub(crate) max_load: Option<f64>,
    /// What the session changed or dropped, such as the agent of the profile.
    pub(crate) warnings: Vec<String>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct GitInfo {
    pub(crate) commit: String,
    pub(crate) dirty: bool,
}

/// The generation of a session: its directory and the digest that names it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct GenerationRef {
    pub(crate) path: String,
    pub(crate) dist_digest: String,
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

/// Writes the sandbox template: the general settings and the license. The FUS test scheme comes from a headless run.
pub(crate) fn write_template(template: &Sandbox, license: &Path) -> anyhow::Result<()> {
    files::write_text(&template.config().join("options").join("ide.general.xml"), GENERAL_SETTINGS)?;
    std::fs::copy(license, template.config().join("idea.key")).with_context(|| format!("cannot copy the license {}", license.display()))?;
    for dir in [template.system(), template.plugins()] {
        std::fs::create_dir_all(&dir).with_context(|| format!("cannot create {}", dir.display()))?;
    }
    Ok(())
}

/// The project of a `project` session, before the session writes it into the template.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum ProjectSource {
    /// A generated project of Markdown files only. See [`MARKDOWN_PROJECT`].
    Markdown,
    /// A copy of a directory.
    Dir(PathBuf),
}

/// The project of a `project` session: `<sandbox>/projects/<name>` in each sandbox, and its first file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Project {
    pub(crate) name: String,
    /// The file that the prime run opens, relative to the project directory.
    pub(crate) file: PathBuf,
}

/// The paths of a [`Project`] inside one sandbox.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ProjectPaths {
    pub(crate) dir: PathBuf,
    pub(crate) file: PathBuf,
}

impl Project {
    /// The paths of the project inside `sandbox`.
    pub(crate) fn paths(&self, sandbox: &Sandbox) -> ProjectPaths {
        let dir = sandbox.root.join(PROJECTS_DIR).join(&self.name);
        ProjectPaths {
            file: dir.join(&self.file),
            dir,
        }
    }
}

/// Writes the project into the template, so each sandbox copy carries it. The prime run then writes the editor
/// state into it, and each measured run starts from a copy of that state.
pub(crate) fn write_project(template: &Sandbox, source: &ProjectSource) -> anyhow::Result<Project> {
    match source {
        ProjectSource::Markdown => {
            let project = Project {
                name: MARKDOWN_PROJECT.to_owned(),
                file: PathBuf::from("README.md"),
            };
            let dir = project.paths(template).dir;
            for (name, text) in markdown_files() {
                files::write_text(&dir.join(name), &text)?;
            }
            Ok(project)
        }
        ProjectSource::Dir(source) => {
            let file = first_file(source)?.with_context(|| format!("the project {} has no file to open", source.display()))?;
            let name = source
                .file_name()
                .map_or_else(|| "project".to_owned(), |name| name.to_string_lossy().into_owned());
            let project = Project { name, file };
            files::copy_dir(source, &project.paths(template).dir)?;
            Ok(project)
        }
    }
}

/// The file of `dir` that the prime run opens: the first `*.md` file in name order, else the first regular file. A
/// hidden file and a subdirectory do not count. A directory without such a file has none.
pub(crate) fn first_file(dir: &Path) -> anyhow::Result<Option<PathBuf>> {
    let mut names = Vec::new();
    for entry in std::fs::read_dir(dir).with_context(|| format!("cannot list {}", dir.display()))? {
        let entry = entry.with_context(|| format!("cannot list {}", dir.display()))?;
        let name = entry.file_name().to_string_lossy().into_owned();
        let kind = entry
            .file_type()
            .with_context(|| format!("cannot stat {}", entry.path().display()))?;
        if kind.is_file() && !name.starts_with('.') {
            names.push(name);
        }
    }
    names.sort();
    let markdown = names
        .iter()
        .find(|name| Path::new(name).extension().is_some_and(|extension| extension == "md"));
    Ok(markdown.or(names.first()).map(PathBuf::from))
}

/// The files of the generated Markdown project: their paths, relative to the project, and their text.
pub(crate) fn markdown_files() -> Vec<(&'static str, String)> {
    let mut readme = vec!["# Markdown project".to_owned(), String::new()];
    for section in 1..=6 {
        readme.extend([
            format!("## Section {section}"),
            String::new(),
            format!("Paragraph {section} has *emphasis*, **strong text** and `code`."),
            format!(
                "It links to [the notes](notes/a.md) and to [section {}](#section-{}).",
                section % 6 + 1,
                section % 6 + 1
            ),
            format!("- item {section}"),
            String::new(),
        ]);
    }
    readme.extend(["---".to_owned(), "The end of the file.".to_owned()]);
    let readme = readme.iter().map(|line| format!("{line}\n")).collect();
    vec![
        ("README.md", readme),
        ("notes/a.md", "# Note A\n\nThe first note links to [note B](b.md).\n".to_owned()),
        ("notes/b.md", "# Note B\n\n1. one\n2. two\n3. three\n".to_owned()),
        ("CHANGELOG.md", "# Changelog\n\n## 1.0\n\n- The first version.\n".to_owned()),
    ]
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

/// The directory of the sessions of this host: `<runtime root>/bench/runs`. A session without `--session` is
/// `<bench runs>/<actor id>`.
pub(crate) fn runs_root(runtime_root: &Path) -> PathBuf {
    runtime_root.join(BENCH_DIR).join(VM_RUNS_DIR)
}

/// The argument that [`locate`] reads as the newest session of this host.
pub(crate) const LATEST: &str = "latest";

/// Finds the session that a verb argument names, in this order:
///
/// 1. `latest`: the session under `bench_runs` with a `summary.json` whose `session.json` is the newest.
/// 2. A path that exists, relative to `working_dir`.
/// 3. A directory name under `bench_runs`.
/// 4. The one session under `bench_runs` whose name contains the argument. Two or more are a usage refusal that
///    lists them.
///
/// The answer is a directory with `session.json`. Any other answer is the refusal `not_a_session`.
pub(crate) fn locate(arg: &str, working_dir: &Path, bench_runs: &Path) -> Result<PathBuf, Refusal> {
    let session = if arg == LATEST {
        latest(bench_runs)?
    } else if working_dir.join(arg).exists() {
        working_dir.join(arg)
    } else if bench_runs.join(arg).is_dir() {
        bench_runs.join(arg)
    } else {
        let mut matches: Vec<PathBuf> = sessions(bench_runs)
            .into_iter()
            .filter(|session| file_name(session).contains(arg))
            .collect();
        match matches.len() {
            0 => {
                return Err(not_a_session(format!(
                    "no session matches {arg}: it is not a path, and no session under {} has it in its name",
                    bench_runs.display()
                )));
            }
            1 => matches.remove(0),
            _ => {
                let names: Vec<String> = matches.iter().map(|session| file_name(session)).collect();
                return Err(Refusal::usage(format!(
                    "{} sessions match {arg}: {}; name a longer part of one",
                    names.len(),
                    names.join(", ")
                )));
            }
        }
    };
    if !session.join(SESSION_FILE).is_file() {
        return Err(not_a_session(format!(
            "{} is not a session directory: it has no {SESSION_FILE}",
            session.display()
        )));
    }
    Ok(session)
}

/// Reads `summary.json` of a session. A session without one, or with one of another shape, is the refusal
/// `no_summary`.
pub(crate) fn read_summary(session: &Path) -> Result<Summary, Refusal> {
    let path = session.join(SUMMARY_FILE);
    let text = files::read_optional(&path)
        .map_err(|error| Refusal::usage(format!("{error:#}")))?
        .ok_or_else(|| {
            Refusal::new(
                "no_summary",
                Exit::USAGE,
                format!(
                    "{} has no {SUMMARY_FILE}; run `bench replay {}` first",
                    session.display(),
                    session.display()
                ),
            )
        })?;
    serde_json::from_str(&text).map_err(|error| {
        Refusal::new(
            "no_summary",
            Exit::USAGE,
            format!(
                "{} is not a summary: {error}; run `bench replay {}` to write it again",
                path.display(),
                session.display()
            ),
        )
    })
}

/// One run of a session that a verb reads.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SelectedRun {
    pub(crate) id: RunId,
    pub(crate) dir: PathBuf,
    /// The metric whose median picked the run. A run that `--run` names has none.
    pub(crate) median_by: Option<&'static str>,
}

/// The run that `--run` names, else the median run of the first arm that is not modal, else of the modal arm. A name
/// that is not a run directory of the session is a usage refusal.
pub(crate) fn select_run(session: &Path, summary: &Summary, run: Option<&str>) -> Result<SelectedRun, Refusal> {
    if let Some(name) = run {
        let id = RunId::parse(name)
            .ok_or_else(|| Refusal::usage(format!("{name} is not a run directory name: use <arm>-run-NN or <arm>-prime")))?;
        let dir = session.join(id.dir_name());
        if !dir.is_dir() {
            let runs: Vec<String> = run_dirs(session)
                .unwrap_or_default()
                .into_iter()
                .map(|(id, _)| id.dir_name())
                .collect();
            let others = if runs.is_empty() {
                "it has no run directory".to_owned()
            } else {
                format!("its runs are {}", runs.join(", "))
            };
            return Err(Refusal::usage(format!(
                "{} has no run {}; {others}",
                session.display(),
                id.dir_name()
            )));
        }
        return Ok(SelectedRun { id, dir, median_by: None });
    }
    let mut arms: Vec<_> = summary.arms.values().collect();
    arms.sort_by_key(|arm| arm.arm == Arm::Modal);
    arms.iter()
        .find_map(|arm| arm.median_run())
        .map(|record| SelectedRun {
            id: RunId {
                arm: record.arm,
                kind: record.kind,
                index: record.index,
            },
            dir: session.join(&record.dir),
            median_by: Some(record.arm.main_metric()),
        })
        .ok_or_else(|| {
            Refusal::usage(format!(
                "no arm of {} has a valid measured run, so it has no median run; name a run with --run",
                session.display()
            ))
        })
}

fn not_a_session(message: String) -> Refusal {
    Refusal::new("not_a_session", Exit::USAGE, message)
}

/// The directory name of a session path, which [`locate`] accepts, else the whole path.
pub(crate) fn display_name(session: &str) -> String {
    Path::new(session)
        .file_name()
        .map_or_else(|| session.to_owned(), |name| name.to_string_lossy().into_owned())
}

fn file_name(path: &Path) -> String {
    path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default()
}

/// The directories under `bench_runs` with `session.json`, in name order. A directory that cannot be listed has none.
fn sessions(bench_runs: &Path) -> Vec<PathBuf> {
    let Ok(entries) = std::fs::read_dir(bench_runs) else {
        return Vec::new();
    };
    let mut sessions: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.join(SESSION_FILE).is_file())
        .collect();
    sessions.sort();
    sessions
}

/// The sessions under `bench_runs` with the mtime of their `session.json`, newest first. Of two sessions with one
/// mtime, the later name comes first. A session whose `session.json` has no mtime is left out.
pub(crate) fn newest_first(bench_runs: &Path) -> Vec<(PathBuf, SystemTime)> {
    let mut sessions: Vec<(PathBuf, SystemTime)> = sessions(bench_runs)
        .into_iter()
        .filter_map(|session| {
            let started = std::fs::metadata(session.join(SESSION_FILE))
                .and_then(|metadata| metadata.modified())
                .ok()?;
            Some((session, started))
        })
        .collect();
    sessions.sort_by(|left, right| right.1.cmp(&left.1).then_with(|| right.0.cmp(&left.0)));
    sessions
}

/// The first session of [`newest_first`] with a summary.
fn latest(bench_runs: &Path) -> Result<PathBuf, Refusal> {
    newest_first(bench_runs)
        .into_iter()
        .map(|(session, _)| session)
        .find(|session| session.join(SUMMARY_FILE).is_file())
        .ok_or_else(|| {
            not_a_session(format!(
                "no session under {} has a {SUMMARY_FILE}, so none is the latest",
                bench_runs.display()
            ))
        })
}

/// Refuses a run whose distribution is not the one of its session. `None` is a run that a session before the
/// generation wrote, which names no digest.
pub(crate) fn check_digest(session: &SessionInfo, run: Option<&str>) -> Result<(), String> {
    match run {
        Some(digest) if digest != session.generation.dist_digest => Err(format!(
            "the run started from the distribution {digest}, and the session is {}",
            session.generation.dist_digest
        )),
        _ => Ok(()),
    }
}

#[cfg(test)]
mod tests;
