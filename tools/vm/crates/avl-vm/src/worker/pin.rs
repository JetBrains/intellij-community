//! The pinned tools: the one resolver of their host paths, and one parser of their versions for the version gate of
//! Tart and for the pin tests of every tool that `tart.MODULE.bazel`, `docker.MODULE.bazel` and `lima.MODULE.bazel`
//! declare.

use std::fmt;
use std::path::PathBuf;
use std::sync::Arc;
#[cfg(any(unix, test))]
use std::sync::LazyLock;

use avl_base::config::{TART_LABEL, docker_buildx_label, docker_cli_label, limactl_label};
use avl_base::{Backend, Config, Refusal, RefusalExt};
use avl_host_sys::guest::{BazelHost, ensure_host_paths, external_files};
use avl_host_sys::{Ctx, Runner};
#[cfg(any(unix, test))]
use regex::Regex;
use tokio::sync::OnceCell;

// The suite drives Tart pools and the fake `limactl`, a shell script, and a Windows host has the Docker backend only.
#[cfg(test)]
#[cfg(unix)]
mod tests;

/// A host tool that a `.MODULE.bazel` file of this workspace pins, and that the controller runs from the output base
/// of the host Bazel.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum PinnedTool {
    /// The Tart of `tart.MODULE.bazel`, which a Tart pool runs unless `TART_BIN` names another.
    Tart,
    /// The Docker CLI of `docker.MODULE.bazel`, which a Docker pool runs unless `DOCKER_BIN` names another.
    DockerCli,
    /// The `docker-buildx` plugin of `docker.MODULE.bazel`, which the pinned CLI gets.
    DockerBuildx,
    /// The `limactl` of `lima.MODULE.bazel`, which a Docker pool on the Lima engine runs.
    Limactl,
}

impl PinnedTool {
    /// The label of the tool for the host of `settings`.
    pub(crate) fn label(self, settings: &Config) -> String {
        match self {
            Self::Tart => TART_LABEL.to_owned(),
            Self::DockerCli => docker_cli_label(settings.guest_arch),
            Self::DockerBuildx => docker_buildx_label(settings.guest_arch),
            Self::Limactl => limactl_label(settings.guest_arch),
        }
    }

    /// The tools a pool of `settings` runs from their pins. A tool that `TART_BIN` or `DOCKER_BIN` names is the
    /// operator's, and so is the plugin of a CLI that `DOCKER_BIN` names.
    pub(crate) fn of_pool(settings: &Config) -> Vec<Self> {
        let mut tools = Vec::new();
        match settings.backend {
            Backend::Tart if settings.tart.is_none() => tools.push(Self::Tart),
            Backend::Docker if settings.docker.is_none() => tools.extend([Self::DockerCli, Self::DockerBuildx]),
            Backend::Tart | Backend::Docker | Backend::Parallels => {}
        }
        if settings.runs_lima_engine() {
            tools.push(Self::Limactl);
        }
        tools
    }
}

impl fmt::Display for PinnedTool {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Tart => "the pinned Tart",
            Self::DockerCli => "the pinned Docker CLI",
            Self::DockerBuildx => "the pinned docker-buildx plugin",
            Self::Limactl => "the pinned limactl",
        })
    }
}

/// The host paths of the pinned tools of one pool: one resolver for the Tart, Docker and Lima backends.
///
/// The first request resolves every tool of [`PinnedTool::of_pool`] and the one it asks for, with one `cquery` and one
/// `info output_base` ([`external_files`]). A later request asks Bazel nothing. So the first command of a Docker pool
/// on the Lima engine pays one analysis for its CLI, its plugin and its `limactl`, and not three.
pub(crate) struct PinnedTools {
    settings: Arc<Config>,
    runner: Runner,
    /// `None` is what a hermetic suite passes: it names its fakes through `TART_BIN` and `DOCKER_BIN`, so nothing asks
    /// Bazel. A request then answers `None`, and each backend refuses with its own code.
    bazel: Option<Arc<dyn BazelHost>>,
    /// The resolved host path of each tool. The cell also serializes the one resolution.
    paths: OnceCell<Vec<(PinnedTool, PathBuf)>>,
}

impl PinnedTools {
    pub(crate) fn new(settings: Arc<Config>, runner: Runner, bazel: Option<Arc<dyn BazelHost>>) -> Self {
        Self {
            settings,
            runner,
            bazel,
            paths: OnceCell::new(),
        }
    }

    /// The host path that the output base holds for `tool`, before its real path, or `None` when this command has no
    /// Bazel. It checks nothing on disk: each backend refuses a missing file with its own code and its own remedy.
    pub(crate) async fn path(&self, ctx: &Ctx, tool: PinnedTool) -> Result<Option<PathBuf>, Refusal> {
        let Some(bazel) = &self.bazel else {
            return Ok(None);
        };
        let paths = self
            .paths
            .get_or_try_init(|| async {
                let mut tools = PinnedTool::of_pool(&self.settings);
                if !tools.contains(&tool) {
                    tools.push(tool);
                }
                // The query runs in the host checkout, and `status` passes the gate before it resolves that path.
                ensure_host_paths(ctx, &self.runner, &self.settings).await?;
                let labels: Vec<String> = tools.iter().map(|tool| tool.label(&self.settings)).collect();
                let files = external_files(ctx, bazel.as_ref(), &labels).await?;
                Ok::<_, Refusal>(tools.into_iter().zip(files).collect())
            })
            .await?;
        match paths.iter().find(|(resolved, _)| *resolved == tool) {
            Some((_, path)) => Ok(Some(path.clone())),
            None => Err(Refusal::internal(format!(
                "{tool} is not a tool of this pool, and the pool resolved its tools already"
            ))),
        }
    }
}

/// `major.minor.patch` from the first version-shaped token of a text, such as `tart --version` or a pin, or `None`.
///
/// Anchored on a word boundary of its own (`^` or whitespace), because the output has carried a leading program name
/// in some builds and a bare number in others, and a pattern with no left anchor would read `2.3` out of a build
/// hash. A missing patch component reads as zero, so `v3.0` is `3.0.0`: Tart has shipped both spellings. Arrays
/// compare lexicographically, which is the version order. The Tart gate and the pin tests read it, so a Windows build
/// has it only for the tests.
#[cfg(any(unix, test))]
pub(crate) fn parse_version(output: &str) -> Option<[u32; 3]> {
    static VERSION_SHAPE: LazyLock<Regex> =
        LazyLock::new(|| Regex::new(r"(?:^|\s)v?(\d+)\.(\d+)(?:\.(\d+))?(?:\s|$)").expect("a constant pattern compiles"));
    let captures = VERSION_SHAPE.captures(output.trim())?;
    let component = |index: usize| captures.get(index).and_then(|matched| matched.as_str().parse::<u32>().ok());
    Some([component(1)?, component(2)?, component(3).unwrap_or(0)])
}

/// The version a `.MODULE.bazel` file of this workspace pins in `variable`, read as a test reads its data.
///
/// # Panics
/// When the file cannot be read, or declares no `variable = "<version>"` line with a version in it.
#[cfg(test)]
pub(crate) fn module_pin(file: &str, variable: &str) -> [u32; 3] {
    let path = avl_testkit::repo_path(&format!("tools/vm/{file}"));
    let content = std::fs::read_to_string(&path)
        .unwrap_or_else(|error| panic!("cannot read {}: {error}; it is test data of this crate", path.display()));
    let pattern = Regex::new(&format!(r#"(?m)^{}\s*=\s*"([^"]+)"$"#, regex::escape(variable))).expect("a constant pattern compiles");
    let pinned = pattern
        .captures(&content)
        .unwrap_or_else(|| panic!("{file} declares no {variable}; the pin has moved"));
    parse_version(&pinned[1]).unwrap_or_else(|| panic!("{file}: {variable} = {:?} is not a version", &pinned[1]))
}

/// A floor constant, parsed.
///
/// # Panics
/// When the constant is not a version.
#[cfg(test)]
pub(crate) fn floor(version: &str) -> [u32; 3] {
    parse_version(version).unwrap_or_else(|| panic!("the floor {version} is not a version"))
}
