//! The git of the host. Branch Deck owns it: `bd lease host git` answers the git that bd and the runs use (the one of
//! Homebrew before `/usr/bin`), and `--ensure` runs `brew install git` or `brew upgrade git` below the floor of a host,
//! once no slot is lent. The lends queue meanwhile, and refuse with `git_not_found` while the git stays below the floor.
//!
//! - The keeper of `serve` calls `--ensure` for each repository at its start and at each tick. The call blocks while
//!   bd waits for the lends and while Homebrew runs, so it runs on the thread of the keeper.
//! - A run puts the directory of that git first in the `PATH` of its command, before `/usr/bin`, and names its version
//!   in the line before the command.
//! - A `git_not_found` refusal of bd ends a run with `old_git` and the hint of bd.

use std::env;
use std::ffi::{OsStr, OsString};
use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::Deserialize;

use crate::bd::{self, Failed};
use crate::child::never;
use crate::refused::{AGENT_ERROR, OLD_GIT, Refusal, refused};

/// The deadline of `bd lease host git`, which runs `git --version`.
const GIT: Duration = Duration::from_secs(60);

/// The deadline of `bd lease host git --ensure`: up to 60 min for the lends to end, then Homebrew.
const ENSURE: Duration = Duration::from_mins(100);

/// The code of a refusal of bd for a git below the floor of a host.
pub(crate) const GIT_NOT_FOUND: &str = "git_not_found";

/// The git of the host, as `bd lease host git` answers it.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct HostGit {
    pub(crate) path: PathBuf,
    /// The directory of the git, which a run puts first in its `PATH`.
    #[serde(default)]
    pub(crate) dir: Option<PathBuf>,
    pub(crate) version: String,
    pub(crate) floor: String,
    pub(crate) meets_floor: bool,
    /// Whether this call upgraded or installed git with Homebrew.
    #[serde(default)]
    pub(crate) upgraded: bool,
}

/// `bd lease host git --ensure -C <repo>`: the git of the host, upgraded with Homebrew when it is below the floor.
pub(crate) fn ensure(repo: &Path) -> Result<HostGit, Failed> {
    call(repo, &["git", "--ensure"], ENSURE)
}

/// The git of the host for a run of `repo`. A git below the floor refuses the run with `old_git`.
pub(crate) fn for_run(repo: &Path) -> Result<HostGit, Refusal> {
    let git = call(repo, &["git"], GIT).map_err(|failed| refusal_of(&failed))?;
    if !git.meets_floor {
        return Err(refused(
            OLD_GIT,
            format!(
                "the git of the host, {} {}, is below the floor {}; the keeper of the agent upgrades it with Homebrew",
                git.path.display(),
                git.version,
                git.floor
            ),
        ));
    }
    Ok(git)
}

fn call(repo: &Path, args: &[&str], deadline: Duration) -> Result<HostGit, Failed> {
    let text = bd::host_call(repo, args, deadline, &never, &mut |_| {})?;
    bd::parse::<HostGit>("git", &text).map_err(|message| Failed { code: None, message })
}

/// The refusal of a run for a failed call of bd about git: `old_git` with the hint of bd for `git_not_found`.
pub(crate) fn refusal_of(failed: &Failed) -> Refusal {
    if failed.code.as_deref() == Some(GIT_NOT_FOUND) {
        refused(OLD_GIT, failed.message.clone())
    } else {
        refused(AGENT_ERROR, failed.message.clone())
    }
}

/// `path` with `dir` before `/usr/bin`. A `path` that has `dir` before `/usr/bin` already stays as it is. Else each
/// `dir` goes, and one `dir` comes right before the first `/usr/bin`, or at the end when there is none.
pub(crate) fn with_first(path: &OsStr, dir: &Path) -> OsString {
    if path.is_empty() {
        return dir.as_os_str().to_owned();
    }
    let system = Path::new("/usr/bin");
    let entries: Vec<PathBuf> = env::split_paths(path).collect();
    let first_system = entries.iter().position(|entry| entry == system);
    let first_own = entries.iter().position(|entry| entry == dir);
    if first_own.is_some_and(|own| first_system.is_none_or(|system| own < system)) {
        return path.to_owned();
    }
    let mut entries: Vec<PathBuf> = entries.into_iter().filter(|entry| entry != dir).collect();
    let at = entries.iter().position(|entry| entry == system).unwrap_or(entries.len());
    entries.insert(at, dir.to_path_buf());
    env::join_paths(entries).unwrap_or_else(|_| path.to_owned())
}

/// A warning for a command that starts a login shell of bash or sh on macOS, such as `bash -lc '…'`: that shell runs
/// `/etc/profile`, whose `path_helper` puts `/usr/bin` first, so the command gets the Apple Git of `/usr/bin` and not
/// `git`, the one of the line before the command. zsh runs `path_helper` too, but `~/.zprofile` of a Homebrew user
/// puts Homebrew first again. `None` for any other command, and elsewhere than on macOS.
pub(crate) fn login_shell_warning(argv: &[String], git: &str, macos: bool) -> Option<String> {
    let (program, arguments) = argv.split_first()?;
    let name = Path::new(program).file_name()?.to_str()?;
    if !macos || !matches!(name, "bash" | "sh") {
        return None;
    }
    let login = arguments
        .iter()
        .take_while(|argument| argument.starts_with('-'))
        .any(|argument| argument == "--login" || (!argument.starts_with("--") && argument.contains('l')));
    login.then(|| {
        format!(
            "`{name} -l` runs /etc/profile, whose path_helper puts /usr/bin first: the command can get the Apple Git of /usr/bin, not {git}. Use `zsh -lc` or no login shell"
        )
    })
}

#[cfg(test)]
mod tests;
