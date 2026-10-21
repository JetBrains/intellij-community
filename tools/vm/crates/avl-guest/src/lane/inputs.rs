//! The launch inputs of the lane IDE, as the runtime descriptor names them: the distribution and its JBR, the IDE flags
//! file, the project, and the declared CLI runtimes.
//!
//! The lane daemon reads them once, at its boot. Every path is a guest path below the runfiles root that the controller
//! built.

use std::collections::BTreeSet;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Component, Path, PathBuf};

use avl_wire::runtime::{self, RuntimeDescriptor};

use super::context::CliRuntime;
use super::package::sha256_hex;
use crate::cli::LaneDaemonArgs;
use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The refusal code of a launch input that the lane daemon cannot read or use.
pub(crate) const INPUT_CODE: &str = "lane_daemon_input";

/// The token that a flag of the descriptor carries where the runfiles root belongs.
const RUNFILES_ROOT_TOKEN: &str = "${RUNFILES_ROOT}";

/// The prefix and the suffix of the flag that names the manifest of one declared CLI runtime, `-Dair.test.<name>.runtime`.
const RUNTIME_FLAG_PREFIX: &str = "-Dair.test.";
const RUNTIME_FLAG_SUFFIX: &str = ".runtime=";

/// The flag that names the recorder binary, the flag that turns the trace off, and the flag that lets the recorder read
/// the screen, as `ui_lane_ide.bzl` writes them.
const RECORDER_FLAG: &str = "-Dair.trace.recorder=";
const TRACE_OFF_FLAG: &str = "-Dair.flow.trace=off";
const SCREEN_FLAG: &str = "-Dair.flow.trace.screen=on";
/// The flag that asks the recorder for the Allure results. No lane target sets it, so a BUILD edit or a `--jvm_flag` of
/// `lane-run` asks for them.
pub(crate) const ALLURE_FLAG: &str = "-Dair.flow.trace.allure=on";

/// The key of the `DevIdeConfig` file that names the home of the distribution, `DevIdeConfig.HOME_PATH_KEY`.
const HOME_PATH_KEY: &str = "home.path";

/// What the lane daemon launches the lane IDE from.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LaunchInputs {
    /// The parent of every context of the lane IDE.
    pub ide_root: PathBuf,
    /// The empty home that the IDE gets as `idea.dev.project.root`. The boot makes it.
    pub home: PathBuf,
    /// The home of the distribution, which the `DevIdeConfig` file names.
    pub dist_home: PathBuf,
    /// The `DevIdeConfig` file of the distribution.
    pub ide_config: PathBuf,
    /// The JBR of the distribution, which the IDE runs on, as a production IDE does.
    pub java_home: PathBuf,
    /// The IDE JVM flags file that Bazel writes.
    pub flags_file: PathBuf,
    /// The zip of the project, and the directory inside it that the IDE opens.
    pub project_archive: PathBuf,
    pub project_root: String,
    /// Every CLI runtime that a flag of the descriptor declares, in flag order.
    pub runtimes: Vec<CliRuntime>,
    /// The names of the plugin directories of the distribution. A context plugin of one of them would load twice.
    pub distribution_plugins: BTreeSet<String>,
    /// The recorder of the scenario traces.
    pub trace: TraceInputs,
}

/// The recorder of the scenario traces, as the flags of the descriptor name it, and the root of its bundles.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct TraceInputs {
    /// The `air-trace-record` binary, or `None` when the descriptor names none or turns the trace off.
    pub recorder: Option<PathBuf>,
    /// The recorder may read the screen itself, because the display belongs to the lane or to a TeamCity build.
    pub screen: bool,
    /// The recorder writes the Allure result of each scenario, because a person asked for it with [`ALLURE_FLAG`].
    pub allure: bool,
    /// The outputs directory of the Bazel test that runs `lane-run`, which Bazel keeps after the test. The bundles then
    /// go to its `air-traces`. `None` keeps them in the directory of each iteration, where the trace pull of the
    /// controller finds them.
    pub bazel_outputs: Option<PathBuf>,
}

impl LaunchInputs {
    /// The `bin/java` of the JBR, which also runs the fake agent.
    pub(crate) fn java(&self) -> PathBuf {
        self.java_home.join("bin").join("java")
    }

    /// The `jcmd` of the JBR, which writes the thread dump of an IDE before a kill, or `None` when the JBR has none.
    pub(crate) fn jcmd(&self) -> Option<PathBuf> {
        Some(self.java_home.join("bin").join("jcmd")).filter(|jcmd| is_executable(jcmd))
    }

    /// The declared runtimes that `names` name, in the order of `names`. A name that no flag declares is a refusal.
    pub(crate) fn runtimes_named(&self, names: &[String]) -> Result<Vec<CliRuntime>, AgentRefusal> {
        names
            .iter()
            .map(|name| {
                self.runtimes.iter().find(|runtime| runtime.name == *name).cloned().ok_or_else(|| {
                    refused(format!(
                        "the lane document declares the CLI runtime '{name}', and the runtime descriptor has no flag \
                         {RUNTIME_FLAG_PREFIX}{name}{RUNTIME_FLAG_SUFFIX}"
                    ))
                })
            })
            .collect()
    }
}

/// The JBR platform of the descriptor that this guest runs, `jbr.platform` of the descriptor.
pub(crate) const fn guest_platform() -> &'static str {
    if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            "darwin_aarch64"
        } else {
            "darwin_x64"
        }
    } else if cfg!(target_arch = "aarch64") {
        "linux_aarch64"
    } else {
        "linux_x64"
    }
}

/// Reads the launch inputs that the descriptor of `args` names.
pub(crate) fn read(args: &LaneDaemonArgs) -> Result<LaunchInputs, AgentRefusal> {
    let content = fs::read(&args.descriptor)
        .map_err(|error| refused(format!("cannot read the runtime descriptor {}: {error}", args.descriptor.display())))?;
    let descriptor = runtime::parse_runtime_descriptor(&content, &args.descriptor, guest_platform())
        .map_err(|error| AgentRefusal::refused(error.code, error.message))?;
    from_descriptor(&descriptor, args)
}

/// The launch inputs of a parsed descriptor whose runfiles are below `args.runfiles_root`.
pub(crate) fn from_descriptor(descriptor: &RuntimeDescriptor, args: &LaneDaemonArgs) -> Result<LaunchInputs, AgentRefusal> {
    let root = &args.runfiles_root;
    let runfile = |file: &runtime::RuntimeFile| runtime::runtime_file_path(root, file);
    let ide_config = runfile(&descriptor.dev_dist.config);
    let dist_home = distribution_home(&ide_config)?;
    let java_home = distribution_java_home(&dist_home)?;
    let runtimes = declared_runtimes(&descriptor.static_jvm_flags, root)?;
    let plugins = dist_home.join("plugins");
    let distribution_plugins = match fs::read_dir(&plugins) {
        Ok(entries) => entries
            .filter_map(Result::ok)
            .map(|entry| entry.file_name().to_string_lossy().into_owned())
            .collect(),
        Err(_) => BTreeSet::new(),
    };
    Ok(LaunchInputs {
        ide_root: args.ide_root.clone(),
        home: args.home.clone(),
        dist_home,
        ide_config,
        java_home,
        flags_file: runfile(&descriptor.ide.flags_file),
        project_archive: runfile(&descriptor.ide.project_archive),
        project_root: descriptor.ide.project_root.clone(),
        runtimes,
        distribution_plugins,
        trace: trace_inputs(&descriptor.static_jvm_flags, root),
    })
}

/// The recorder that the flags name. `-Dair.flow.trace=off` turns the trace off whatever else they name.
fn trace_inputs(flags: &[String], runfiles_root: &Path) -> TraceInputs {
    let root = runfiles_root.display().to_string();
    let off = flags.iter().any(|flag| flag == TRACE_OFF_FLAG);
    let recorder = flags
        .iter()
        .find_map(|flag| flag.strip_prefix(RECORDER_FLAG))
        .filter(|_| !off)
        .map(|path| resolve(&path.replace(RUNFILES_ROOT_TOKEN, &root), runfiles_root));
    TraceInputs {
        recorder,
        screen: flags.iter().any(|flag| flag == SCREEN_FLAG),
        allure: flags.iter().any(|flag| flag == ALLURE_FLAG),
        bazel_outputs: None,
    }
}

/// The home of the distribution: [`HOME_PATH_KEY`] of the `DevIdeConfig` file, relative to the directory of the file
/// when it is relative.
///
/// The file is a properties file that the dev distribution rule writes, one `key=value` line each and no escape, so it
/// is read by hand: a crate for it would grow the closure of the guest agent.
fn distribution_home(ide_config: &Path) -> Result<PathBuf, AgentRefusal> {
    let text =
        fs::read_to_string(ide_config).map_err(|error| refused(format!("cannot read the IDE config {}: {error}", ide_config.display())))?;
    let home = text
        .lines()
        .filter_map(|line| line.split_once('='))
        .find(|(key, _)| key.trim() == HOME_PATH_KEY)
        .map(|(_, value)| value.trim())
        .filter(|value| !value.is_empty())
        .ok_or_else(|| refused(format!("'{HOME_PATH_KEY}' is missing from {}", ide_config.display())))?;
    let home = Path::new(home);
    if home.is_absolute() {
        return Ok(normalize(home));
    }
    let directory = ide_config.parent().unwrap_or_else(|| Path::new("/"));
    Ok(normalize(&directory.join(home)))
}

/// The Java home of the JBR of `dist_home`: `jbr`, and `jbr/Contents/Home` on macOS. A missing `bin/java` is a refusal,
/// because the lane IDE must not start on another JVM.
fn distribution_java_home(dist_home: &Path) -> Result<PathBuf, AgentRefusal> {
    let jbr = dist_home.join("jbr");
    let java_home = if cfg!(target_os = "macos") {
        jbr.join("Contents").join("Home")
    } else {
        jbr
    };
    let java = java_home.join("bin").join("java");
    if !is_executable(&java) {
        return Err(refused(format!(
            "the lane distribution {} has no JBR: {} is missing or not executable",
            dist_home.display(),
            java.display()
        )));
    }
    Ok(java_home)
}

/// The CLI runtimes that the flags `-Dair.test.<name>.runtime=<manifest>` declare, in flag order.
fn declared_runtimes(flags: &[String], runfiles_root: &Path) -> Result<Vec<CliRuntime>, AgentRefusal> {
    let root = runfiles_root.display().to_string();
    let mut runtimes: Vec<CliRuntime> = Vec::new();
    for flag in flags {
        let Some((name, manifest)) = flag
            .strip_prefix(RUNTIME_FLAG_PREFIX)
            .and_then(|rest| rest.split_once(RUNTIME_FLAG_SUFFIX))
        else {
            continue;
        };
        if name.is_empty() || name.contains(['.', '/']) {
            continue;
        }
        if runtimes.iter().any(|runtime| runtime.name == name) {
            return Err(refused(format!("the runtime descriptor declares the CLI runtime '{name}' twice")));
        }
        let manifest = resolve(&manifest.replace(RUNFILES_ROOT_TOKEN, &root), runfiles_root);
        runtimes.push(declared_runtime(name, &manifest, runfiles_root)?);
    }
    Ok(runtimes)
}

/// One declared CLI runtime from its manifest: `version`, `package`, `entry`, `lock` and, for a script, `node`. Each path
/// is a runfiles path or an absolute one.
///
/// The identity is the SHA-256 of the manifest bytes, then of the interpreter bytes when there is one, then of the lock
/// bytes, as `DeclaredCliRuntime.resolve` computes it. The command is the interpreter, when there is one, and the entry.
fn declared_runtime(name: &str, manifest: &Path, runfiles_root: &Path) -> Result<CliRuntime, AgentRefusal> {
    let shown = manifest.display();
    let bytes = fs::read(manifest).map_err(|error| refused(format!("cannot read the CLI runtime manifest {shown}: {error}")))?;
    let text = String::from_utf8_lossy(&bytes);
    let value = |key: &str| {
        text.lines()
            .filter_map(|line| line.split_once('='))
            .find(|(found, _)| found.trim() == key)
            .map(|(_, value)| value.trim().to_owned())
            .filter(|value| !value.is_empty())
    };
    let require = |key: &str| value(key).ok_or_else(|| refused(format!("the declared CLI runtime '{name}' has no '{key}' input: {shown}")));
    let entry = resolve(&require("package")?, runfiles_root).join(require("entry")?);
    let lock = resolve(&require("lock")?, runfiles_root);
    let node = value("node").map(|node| resolve(&node, runfiles_root));
    if let Some(node) = &node
        && !is_executable(node)
    {
        return Err(refused(format!(
            "the declared interpreter of '{name}' is not executable: {}",
            node.display()
        )));
    }
    if !entry.is_file() || (node.is_none() && !is_executable(&entry)) {
        return Err(refused(format!(
            "the declared entry point of '{name}' is missing or not executable: {}",
            entry.display()
        )));
    }
    let mut digested = bytes.clone();
    for input in node.iter().chain(std::iter::once(&lock)) {
        let content =
            fs::read(input).map_err(|error| refused(format!("cannot read the input {} of '{name}': {error}", input.display())))?;
        digested.extend_from_slice(&content);
    }
    let command = node
        .iter()
        .chain(std::iter::once(&entry))
        .map(|path| path.display().to_string())
        .collect();
    Ok(CliRuntime {
        name: name.to_owned(),
        command,
        identity: sha256_hex(&digested),
        version: value("version"),
    })
}

/// `location` itself when it is absolute, else below the runfiles root.
fn resolve(location: &str, runfiles_root: &Path) -> PathBuf {
    let path = Path::new(location);
    if path.is_absolute() {
        normalize(path)
    } else {
        normalize(&runfiles_root.join(path))
    }
}

/// `path` with its `.` components dropped and each `..` folded into its parent, as `Path.normalize` of the JDK does.
fn normalize(path: &Path) -> PathBuf {
    let mut normalized = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                normalized.pop();
            }
            other => normalized.push(other.as_os_str()),
        }
    }
    normalized
}

fn is_executable(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|metadata| metadata.is_file() && metadata.permissions().mode() & 0o111 != 0)
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(INPUT_CODE, message)
}
