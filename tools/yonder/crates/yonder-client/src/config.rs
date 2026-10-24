//! The files of the client in its directory, `~/.config/yonder/` by default: the identity of the client, and one host
//! file per alias in `hosts/<alias>.json`.

use std::fs;
use std::io::{self, Write as _};
use std::net::SocketAddr;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use serde::{Deserialize, Serialize};
use yonder_link::{Fingerprint, Identity};

use crate::{Refusal, code, prepare_hint, refused};

/// The count of the host file writes of this process, for the names of their temporary files.
static WRITES: AtomicU64 = AtomicU64::new(0);

/// What the client knows of one host: the pin of its agent and where it answered last.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct HostFile {
    /// The ssh alias that `prepare` used, and the name of this file.
    pub alias: String,
    /// The fingerprint of the agent. A connection to an agent that shows another certificate fails.
    pub fingerprint: Fingerprint,
    pub port: u16,
    /// The host name of the alias from `ssh -G`, which the client resolves when no cached address answers.
    pub hostname: String,
    /// The addresses that answered, the last one that answered first.
    pub addresses: Vec<SocketAddr>,
    /// The main repository on the host. A leading `~/` is the home directory of the agent.
    pub repo: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Config {
    dir: PathBuf,
}

impl Config {
    pub const fn new(dir: PathBuf) -> Self {
        Self { dir }
    }

    /// `.config/yonder` in the home directory `home`.
    pub fn in_home(home: &Path) -> Self {
        Self::new(home.join(".config").join("yonder"))
    }

    /// The configuration of the user of this process.
    pub fn of_user() -> Result<Self, Refusal> {
        std::env::home_dir()
            .filter(|home| !home.as_os_str().is_empty())
            .map(|home| Self::in_home(&home))
            .ok_or_else(|| refused(code::CONFIG, "this process has no home directory for ~/.config/yonder"))
    }

    pub fn dir(&self) -> &Path {
        &self.dir
    }

    /// The identity of this client, created on the first call.
    pub fn identity(&self) -> Result<Identity, Refusal> {
        Identity::load_or_create(&self.dir).map_err(|error| {
            refused(
                code::CONFIG,
                format!("cannot read or create the client identity in {}: {error}", self.dir.display()),
            )
        })
    }

    pub fn host_path(&self, alias: &str) -> PathBuf {
        self.dir.join("hosts").join(format!("{alias}.json"))
    }

    pub fn read_host(&self, alias: &str) -> Result<HostFile, Refusal> {
        check_alias(alias)?;
        let path = self.host_path(alias);
        let text = match fs::read_to_string(&path) {
            Ok(text) => text,
            Err(error) if error.kind() == io::ErrorKind::NotFound => {
                return Err(refused(
                    code::NO_HOST,
                    format!("no host file {} for {alias}; {}", path.display(), prepare_hint(alias)),
                ));
            }
            Err(error) => return Err(refused(code::BAD_HOST_FILE, format!("cannot read {}: {error}", path.display()))),
        };
        let host: HostFile = serde_json::from_str(&text)
            .map_err(|error| refused(code::BAD_HOST_FILE, format!("{} is not a host file: {error}", path.display())))?;
        if host.alias != alias {
            return Err(refused(
                code::BAD_HOST_FILE,
                format!("{} names the alias {:?}, not {alias:?}", path.display(), host.alias),
            ));
        }
        Ok(host)
    }

    /// Writes the host file through a temporary file beside it, so a reader never sees a part.
    pub fn write_host(&self, host: &HostFile) -> Result<(), Refusal> {
        check_alias(&host.alias)?;
        let path = self.host_path(&host.alias);
        let cannot = |error: io::Error| refused(code::CONFIG, format!("cannot write {}: {error}", path.display()));
        let mut text = serde_json::to_string_pretty(host).map_err(|error| cannot(io::Error::other(error)))?;
        text.push('\n');
        let dir = path.parent().unwrap_or(&self.dir);
        fs::create_dir_all(dir).map_err(cannot)?;
        // One name per process and per write, so two writers never share a temporary file.
        let temp = dir.join(format!(
            ".{}.json.tmp-{}-{}",
            host.alias,
            std::process::id(),
            WRITES.fetch_add(1, Ordering::Relaxed)
        ));
        let written = fs::File::create(&temp)
            .and_then(|mut file| file.write_all(text.as_bytes()))
            .and_then(|()| fs::rename(&temp, &path));
        if let Err(error) = written {
            let _ = fs::remove_file(&temp);
            return Err(cannot(error));
        }
        Ok(())
    }
}

/// Refuses an alias that cannot name a host file: it is `[0-9A-Za-z._-]+`, at most 128 bytes, and no dot first.
pub fn check_alias(alias: &str) -> Result<(), Refusal> {
    if yonder_link::proto::is_run_id(alias) {
        Ok(())
    } else {
        Err(refused(
            code::USAGE,
            format!("the alias {alias:?} cannot name a host file: use letters, digits, `.`, `_` and `-`"),
        ))
    }
}

#[cfg(test)]
mod tests;
