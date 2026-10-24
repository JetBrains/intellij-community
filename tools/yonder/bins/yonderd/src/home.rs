//! The files of the agent under the home directory.

use std::path::{Path, PathBuf};

use crate::refused::{AGENT_ERROR, Refusal, refused};

/// The label of the LaunchAgent of the agent.
pub(crate) const LAUNCHD_LABEL: &str = "com.jetbrains.yonderd";

/// The home directory of the user that runs the agent, and the paths of the agent under it.
#[derive(Clone, Debug)]
pub(crate) struct Home {
    dir: PathBuf,
}

impl Home {
    pub(crate) const fn new(dir: PathBuf) -> Self {
        Self { dir }
    }

    /// The home directory of `HOME`.
    pub(crate) fn from_env() -> Result<Self, Refusal> {
        match std::env::var_os("HOME") {
            Some(dir) if !dir.is_empty() => Ok(Self::new(PathBuf::from(dir))),
            _ => Err(refused(AGENT_ERROR, "HOME is not set, so the agent cannot find its files")),
        }
    }

    pub(crate) fn dir(&self) -> &Path {
        &self.dir
    }

    /// The identity of the agent: `~/.config/yonderd/`.
    pub(crate) fn identity_dir(&self) -> PathBuf {
        self.dir.join(".config/yonderd")
    }

    /// The allow list of the clients: `~/.config/yonderd/clients`.
    pub(crate) fn allow_file(&self) -> PathBuf {
        self.identity_dir().join("clients")
    }

    /// The cache of the agent: `~/.cache/yonderd/`.
    pub(crate) fn cache_dir(&self) -> PathBuf {
        self.dir.join(".cache/yonderd")
    }

    /// The lease files of `remote.sh` of BT, which `setup` moves: `~/.cache/bt-remote/leases/`.
    pub(crate) fn bt_remote_leases_dir(&self) -> PathBuf {
        self.dir.join(".cache/bt-remote/leases")
    }

    /// The parent of each run directory: `~/.cache/yonderd/runs/`.
    pub(crate) fn runs_dir(&self) -> PathBuf {
        self.dir.join(".cache/yonderd/runs")
    }

    /// The repositories that a pack went to, one path per line: `~/.cache/yonderd/repos`.
    pub(crate) fn repos_file(&self) -> PathBuf {
        self.dir.join(".cache/yonderd/repos")
    }

    /// The lease file and the lock of each holder: `~/.cache/yonderd/leases/`.
    pub(crate) fn leases_dir(&self) -> PathBuf {
        self.dir.join(".cache/yonderd/leases")
    }

    /// The plist of the LaunchAgent.
    pub(crate) fn launch_agent_file(&self) -> PathBuf {
        self.dir.join(format!("Library/LaunchAgents/{LAUNCHD_LABEL}.plist"))
    }

    /// The log of `serve` under launchd.
    pub(crate) fn launchd_log_file(&self) -> PathBuf {
        self.dir.join("Library/Logs/yonderd.log")
    }

    /// `path` with a leading `~/` replaced by the home directory.
    pub(crate) fn expand(&self, path: &str) -> PathBuf {
        match path.strip_prefix("~/") {
            Some(rest) => self.dir.join(rest),
            None if path == "~" => self.dir.clone(),
            None => PathBuf::from(path),
        }
    }
}

#[cfg(test)]
mod tests;
