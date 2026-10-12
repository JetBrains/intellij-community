//! The host Bazel a backend resolves its pinned tools through, without Bazel: the Tart of `tart.MODULE.bazel`, the
//! Docker CLI and its `docker-buildx` plugin of `docker.MODULE.bazel`, and the `limactl` of `lima.MODULE.bazel`.

use std::path::{Path, PathBuf};
use std::sync::Mutex;

use async_trait::async_trait;
use avl_base::config::{TART_LABEL, docker_buildx_label, docker_cli_label, label_target, limactl_label};
use avl_base::sync::lock;
use avl_base::{GuestArch, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::BazelHost;
use tempfile::TempDir;

/// Where the pinned Tart lives under the output base, the path `cquery` answers for the archive's executable.
const PINNED_TART: &str = "external/community++http_archive+air_tart/tart.app/Contents/MacOS/tart";

/// A [`BazelHost`] that resolves each pinned label to one fake executable: `cquery` answers the archive's relative
/// path of each label its expression names, one line each, and `info` a temporary output base that holds a symlink
/// there. An expression is one label or several joined by ` + `, as the batched lookup asks. A backend canonicalizes
/// the path, so the fake runs from its own path and finds its answers. A label that is not pinned, and every other
/// question, answers nothing. It records what it was asked.
pub struct PinnedBazel {
    output_base: TempDir,
    /// Each pinned label and the path under the output base that `cquery` answers for it.
    pins: Vec<(String, String)>,
    asked: Mutex<Vec<String>>,
    labels: Mutex<Vec<String>>,
}

impl Default for PinnedBazel {
    fn default() -> Self {
        Self {
            output_base: tempfile::tempdir().expect("a temporary output base"),
            pins: Vec::new(),
            asked: Mutex::default(),
            labels: Mutex::default(),
        }
    }
}

impl PinnedBazel {
    /// Resolves the pinned Tart to the executable at `tart`.
    pub fn tart(tart: &Path) -> Self {
        Self::default().pin(TART_LABEL, PINNED_TART, tart)
    }

    /// Resolves the pinned Docker CLI of a host of `arch` to the executable at `docker`.
    #[must_use]
    pub fn with_docker(self, arch: GuestArch, docker: &Path) -> Self {
        let label = docker_cli_label(arch);
        let relative = format!("external/community++http_archive+{}/docker/docker", label_target(&label));
        self.pin(&label, &relative, docker)
    }

    /// Resolves the pinned `docker-buildx` plugin of a host of `arch` to the file at `plugin`. The link is named
    /// `docker-buildx`, as the downloaded file is.
    #[must_use]
    pub fn with_buildx(self, arch: GuestArch, plugin: &Path) -> Self {
        let label = docker_buildx_label(arch);
        let relative = format!("external/community++http_file+{}/file/docker-buildx", label_target(&label));
        self.pin(&label, &relative, plugin)
    }

    /// Resolves the pinned `limactl` of a host of `arch` to the executable at `limactl`.
    #[must_use]
    pub fn with_limactl(self, arch: GuestArch, limactl: &Path) -> Self {
        let label = limactl_label(arch);
        let relative = format!("external/community++http_archive+{}/bin/limactl", label_target(&label));
        self.pin(&label, &relative, limactl)
    }

    fn pin(mut self, label: &str, relative: &str, executable: &Path) -> Self {
        let link = self.output_base.path().join(relative);
        std::fs::create_dir_all(link.parent().expect("a pinned path has a parent")).expect("the archive directory is created");
        std::os::unix::fs::symlink(executable, &link).expect("the pinned executable is linked");
        self.pins.push((label.to_owned(), relative.to_owned()));
        self
    }

    /// Every command asked so far (`cquery`, `info`), in order.
    pub fn asked(&self) -> Vec<String> {
        lock(&self.asked).clone()
    }

    /// The arguments of every `cquery` so far, each joined by spaces.
    pub fn labels(&self) -> Vec<String> {
        lock(&self.labels).clone()
    }
}

#[async_trait]
impl BazelHost for PinnedBazel {
    async fn query(&self, _ctx: &Ctx, command: &str, args: &[String]) -> Result<String, Refusal> {
        lock(&self.asked).push(command.to_owned());
        Ok(match command {
            "cquery" => {
                let joined = args.join(" ");
                let named: Vec<&str> = args.last().map(|expression| expression.split(" + ").collect()).unwrap_or_default();
                let answer = self
                    .pins
                    .iter()
                    .filter(|(label, _)| named.contains(&label.as_str()))
                    .map(|(_, relative)| format!("{relative}\n"))
                    .collect();
                lock(&self.labels).push(joined);
                answer
            }
            "info" => format!("{}\n", self.output_base.path().display()),
            _ => String::new(),
        })
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        Ok(PathBuf::new())
    }
}
