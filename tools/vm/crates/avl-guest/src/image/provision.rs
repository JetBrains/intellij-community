//! `provision-image`: turning a freshly cloned Cirrus macOS base into the golden image, from inside the guest.
//!
//! The first `shell` provisioner of `air-macos.pkr.hcl`'s one build, and everything after it -
//! `sanitize-public-image.sh`, `validate-image`, `seal-public-image.sh` - assumes what it did. So this is the step
//! that decides what a *publishable* image contains, and both its halves are load bearing: it adds Junie at a
//! pinned version, and it removes every credential the public base might be carrying so that none of them can
//! influence the install or be copied into an image other people run.
//!
//! Nothing here installs Homebrew, Git or Node: those arrive with the digest-pinned base and this verb only proves
//! they are present ([`REQUIRED_BASE_EXECUTABLES`]).
//!
//! Where the work is a tool's own - `npm`'s cache layout, `defaults`' plist encoding, `install`'s ownership
//! handling, `sudo`'s privilege - it shells out. Where the work is a plain unprivileged read or delete of this
//! account's own files, it is done natively.

use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Path, PathBuf};

use serde::Serialize;

use super::{
    IMAGE_ACCOUNT, IMAGE_HOME, IMAGE_PASSWORD, REQUIRED_BASE_EXECUTABLES, SYSTEM_PATH, Stat, WORKER_SCRIPT_DIRECTORY, WORKER_SCRIPTS,
    image_environment, is_node_major, local_bin_directory, node_bin_directory, resolve,
};
use crate::cli::ImagePins;
use crate::reply::{AgentRefusal, AgentRefusalExt, refuse};
use crate::step::{Runner, Step};
use avl_wire::verb::AgentVerb;

#[cfg(test)]
mod tests;

/// Where the Junie shim comes from.
pub(crate) const JUNIE_INSTALLER_URL: &str = "https://junie.jetbrains.com/install.sh";

/// Where Packer's `file` provisioners put the worker scripts.
const UPLOAD_DIRECTORY: &str = "/tmp";

/// The paths the public base might be carrying a credential in, relative to the home being provisioned.
///
/// The base's `admin`/`admin` account is deliberately public, so the risk runs the other way, in two directions:
/// a credential left in place could **influence the install** (an inherited `.npmrc` pointing npm at another
/// registry, an inherited `.netrc` authenticating the Junie download) and could be **copied into an image other
/// people run**. Only the first is addressed by sweeping *before* the installs. A denylist cannot prove an unknown
/// path harmless, so this is the known set and `air-audit-public-image` re-checks it offline. Adding a path is
/// cheap; removing one is a change to what a published image may contain.
pub(crate) const INHERITED_CREDENTIAL_FILES: [&str; 19] = [
    ".netrc",
    ".npmrc",
    ".gitconfig",
    ".git-credentials",
    ".ssh/authorized_keys",
    ".codex/auth.json",
    ".pi/agent/auth.json",
    ".claude/.credentials.json",
    ".local/share/opencode/auth.json",
    ".config/codex/auth.json",
    ".config/gh/hosts.yml",
    ".docker/config.json",
    ".config/containers/auth.json",
    ".config/gcloud/application_default_credentials.json",
    ".config/gcloud/credentials.db",
    ".aws/credentials",
    ".azure/accessTokens.json",
    ".azure/msal_token_cache.json",
    ".kube/config",
];

/// Whatever private keys the base's account holds, by name prefix in `~/.ssh` (the script's `id_*(N)`): the names
/// are the base image's choice rather than this pipeline's.
const INHERITED_PRIVATE_KEY_PREFIX: &str = "id_";

/// The residue removed at the end, relative to the home. Every path here would otherwise be copied into an image
/// other people run: shell histories record the commands this build ran, `.npm` records every fetch and from which
/// registry, and Junie's log and update directories record both.
pub(crate) const RESIDUE_DIRECTORIES: [&str; 4] = [".npm", ".zsh_sessions", ".junie/logs", ".local/share/junie/updates"];
pub(crate) const RESIDUE_FILES: [&str; 3] = [".zsh_history", ".bash_history", ".sh_history"];

/// The line whose presence means the toolchain block has already been appended.
pub(crate) const LOGIN_PROFILE_MARKER: &str = "# AIR VM toolchain";

/// What the verb answers: the pins it was given. An image build whose reply echoes a different macOS version than
/// its argv is the one shape of "provisioned the wrong thing" no individual check can see.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ProvisionReply {
    pub macos_version: String,
    pub node_major: u32,
    pub junie_version: String,
}

/// Whether one tool is on a search path. `command -v`, as a seam: a test stubs four executables without building
/// four files.
pub(crate) type LookPath = Box<dyn Fn(&str, &[String]) -> bool>;

/// Turns one freshly cloned base into the golden image. The checks a reviewer most needs pinned - that the
/// credential sweep happens *before* anything is installed, and that its argv names every path it claims to - are
/// properties of the transcript, which is what a swapped runner answers.
pub(crate) struct ImageProvisioner<R> {
    pins: ImagePins,
    /// `/Users/admin` in the guest and a temporary directory in a test.
    pub home: PathBuf,
    /// `/tmp` in the guest; a field so the upload removal is exercisable without touching the real `/tmp`.
    pub uploads: PathBuf,
    /// The PATH every step runs with, and the one `look_path` searches.
    pub path: Vec<String>,
    pub look_path: LookPath,
    pub runner: R,
}

impl<R: Runner> ImageProvisioner<R> {
    pub(crate) fn new(pins: &ImagePins, runner: R) -> Self {
        let stat: Stat = Box::new(|path| fs::metadata(path));
        Self {
            pins: pins.clone(),
            home: PathBuf::from(IMAGE_HOME),
            uploads: PathBuf::from(UPLOAD_DIRECTORY),
            path: Vec::new(),
            look_path: Box::new(move |name, path| resolve(&stat, path, name).is_some()),
            runner,
        }
    }

    /// The PATH before the first command: the Node keg first, then the system directories. The account's
    /// `.local/bin` joins it only once Junie's installer has written the shim there.
    pub(crate) fn initial_path(&self) -> Vec<String> {
        std::iter::once(node_bin_directory(self.pins.node_major))
            .chain(SYSTEM_PATH.map(str::to_owned))
            .collect()
    }

    fn step<S: Into<String>>(&self, argv: impl IntoIterator<Item = S>) -> Step {
        let mut step = Step::new(argv);
        step.env = image_environment(&self.path);
        step
    }

    fn run(&mut self, step: &Step) -> Result<String, AgentRefusal> {
        self.runner
            .run(step)
            .map_err(|error| AgentRefusal::for_verb(AgentVerb::ProvisionImage, format!("{error:#}")))
    }

    /// The script, in the script's order.
    ///
    /// The order matters in three places. The environment is set before anything is looked up or run. The
    /// credential sweep precedes every install, so that no inherited credential can influence one. The upload
    /// cleanup follows the installs of those same uploads, so that a failure leaves the copies missing rather than
    /// the sources gone.
    pub(crate) fn provision(&mut self) -> Result<ProvisionReply, AgentRefusal> {
        self.path = self.initial_path();
        self.require_expected_base()?;
        self.remove_inherited_credentials()?;
        self.install_junie()?;
        self.enforce_unattended_session()?;
        self.install_worker_scripts()?;
        self.write_login_profile()?;
        self.remove_provisioning_residue()?;
        Ok(ProvisionReply {
            macos_version: self.pins.macos_version.clone(),
            node_major: self.pins.node_major,
            junie_version: self.pins.junie_version.clone(),
        })
    }

    /// Refuses a base image that is not the one the pins describe: the product version, the four executables the
    /// base owes, and the Node major behind the keg the PATH points at. First, because every step after would
    /// otherwise install correct software onto the wrong image and produce a golden that looks healthy.
    pub(crate) fn require_expected_base(&mut self) -> Result<(), AgentRefusal> {
        let product = self.run(&self.step(["sw_vers", "-productVersion"]).capture())?;
        // Trimmed because the script read it through `$(...)`, which drops the trailing newline.
        let found = product.trim();
        if found != self.pins.macos_version {
            refuse!(
                AgentVerb::ProvisionImage,
                "expected macOS {}, found {found}",
                self.pins.macos_version
            );
        }
        for executable in REQUIRED_BASE_EXECUTABLES {
            if !(self.look_path)(executable, &self.path) {
                refuse!(
                    AgentVerb::ProvisionImage,
                    "required executable is missing from the Cirrus base: {executable}"
                );
            }
        }
        let node = self.run(&self.step(["node", "--version"]).capture())?;
        let found = node.trim();
        if !is_node_major(found, self.pins.node_major) {
            refuse!(AgentVerb::ProvisionImage, "expected Node {}, found {found}", self.pins.node_major);
        }
        Ok(())
    }

    /// The one `sudo rm -f --` over the whole list. The `sudo` is not belt-and-braces: a credential file the base
    /// shipped root-owned would survive an unprivileged delete, and surviving is precisely the outcome this step
    /// exists to prevent, since the next thing that happens to the image is publication. `--` so a path could
    /// never be read as options.
    pub(crate) fn sweep_argv(&self) -> Vec<String> {
        let mut argv: Vec<String> = ["sudo", "rm", "-f", "--"].map(str::to_owned).to_vec();
        argv.extend(
            INHERITED_CREDENTIAL_FILES
                .iter()
                .map(|name| self.home.join(name).to_string_lossy().into_owned()),
        );
        argv.extend(
            private_keys(&self.home.join(".ssh"))
                .iter()
                .map(|key| key.to_string_lossy().into_owned()),
        );
        argv
    }

    /// Sweeps the public base's account before anything is installed into it.
    pub(crate) fn remove_inherited_credentials(&mut self) -> Result<(), AgentRefusal> {
        let sweep = self.step(self.sweep_argv());
        self.run(&sweep).map(drop)
    }

    /// Installs the pinned Junie build through its own installer (a self-updating shim rather than an npm
    /// package); `JUNIE_VERSION` stops it from resolving `latest` and from prompting to update.
    ///
    /// The download and the shell are two steps where the script had one pipeline: `set -o pipefail` *reported* a
    /// failed `curl`, but only after `bash` had executed whatever prefix of the installer arrived - and a shell
    /// script truncated mid-line is the one failure a pinned version cannot protect against. Buffered, the shell
    /// either receives a complete installer or is never started.
    pub(crate) fn install_junie(&mut self) -> Result<(), AgentRefusal> {
        let body = self.run(&self.step(["curl", "-fsSL", JUNIE_INSTALLER_URL]).capture())?;
        // `curl -f` already refuses an HTTP error, so what this catches is a 200 with nothing in it: piped into a
        // shell a successful no-op, and the image reaches `validate-image` three provisioners later with no Junie.
        if body.trim().is_empty() {
            refuse!(AgentVerb::ProvisionImage, "{JUNIE_INSTALLER_URL} answered an empty installer");
        }
        let install = self.step(["bash"]).stdin(body).env("JUNIE_VERSION", &self.pins.junie_version);
        self.run(&install)?;
        // The shim's directory goes *first*. Nothing later in this verb runs anything from there; it makes the
        // position a decision rather than an accident for whatever step is added next.
        self.path = std::iter::once(local_bin_directory(&self.home))
            .chain(self.initial_path())
            .collect();
        Ok(())
    }

    /// Makes the image log itself in and never lock or sleep: a UI lane needs a real Aqua session with an awake
    /// screen. Every write *reasserts* what the pinned base already configures, so the base pin and the audit can
    /// be believed independently.
    ///
    /// Which of them takes `sudo` is decided by the file each writes, and both mistakes are quiet.
    /// `/Library/Preferences` is system-wide and unwritable to this account. The `-currentHost` screensaver domain
    /// belongs to `admin` and must *not* be written as root: a root-owned plist in `admin`'s byhost directory is
    /// not the file the login session reads.
    pub(crate) fn enforce_unattended_session(&mut self) -> Result<(), AgentRefusal> {
        let settings: [&[&str]; 7] = [
            &[
                "sudo",
                "defaults",
                "write",
                "/Library/Preferences/com.apple.loginwindow",
                "autoLoginUser",
                IMAGE_ACCOUNT,
            ],
            &[
                "sudo",
                "defaults",
                "write",
                "/Library/Preferences/com.apple.screensaver",
                "loginWindowIdleTime",
                "-int",
                "0",
            ],
            &[
                "defaults",
                "-currentHost",
                "write",
                "com.apple.screensaver",
                "idleTime",
                "-int",
                "0",
            ],
            &[
                "defaults",
                "-currentHost",
                "write",
                "com.apple.screensaver",
                "askForPassword",
                "-int",
                "0",
            ],
            &[
                "defaults",
                "-currentHost",
                "write",
                "com.apple.screensaver",
                "askForPasswordDelay",
                "-int",
                "0",
            ],
            &[
                "sudo",
                "pmset",
                "-a",
                "sleep",
                "0",
                "displaysleep",
                "0",
                "disksleep",
                "0",
                "powernap",
                "0",
                "standby",
                "0",
                "autopoweroff",
                "0",
            ],
            // The password is on the command line, where the guest's whole process table can read it. That is
            // safe for exactly one reason: it is the value Cirrus publishes for this base. A private credential
            // must never be passed this way.
            &["sysadminctl", "-screenLock", "off", "-password", IMAGE_PASSWORD],
        ];
        for argv in settings {
            self.run(&self.step(argv.iter().copied()))?;
        }
        Ok(())
    }

    /// Puts the three helper scripts where the controller and the audits look for them.
    ///
    /// `install` and not a copy plus `chmod`: owner, group and mode in one operation, replacing an existing file
    /// atomically. Root-owned 0755 because these run as root on every boot, and a script an unprivileged account
    /// could rewrite would be a root shell in every worker in the pool.
    pub(crate) fn install_worker_scripts(&mut self) -> Result<(), AgentRefusal> {
        let directory = self.step([
            "sudo",
            "install",
            "-d",
            "-o",
            "root",
            "-g",
            "wheel",
            "-m",
            "0755",
            WORKER_SCRIPT_DIRECTORY,
        ]);
        self.run(&directory)?;
        for script in &WORKER_SCRIPTS {
            let upload = self.uploads.join(script.upload).to_string_lossy().into_owned();
            let install = self.step([
                "sudo".to_owned(),
                "install".to_owned(),
                "-o".to_owned(),
                "root".to_owned(),
                "-g".to_owned(),
                "wheel".to_owned(),
                "-m".to_owned(),
                "0755".to_owned(),
                upload,
                super::installed_worker_script(script.installed),
            ]);
            self.run(&install)?;
        }
        // Removed only once every copy is in place: `/tmp` is on the image too, so an input left there is residue
        // in something publishable - but a failed install must leave the source behind. Natively and without
        // `sudo`: these are this account's own uploads.
        for script in &WORKER_SCRIPTS {
            remove_file_if_present(&self.uploads.join(script.upload))?;
        }
        Ok(())
    }

    /// What an interactive login in the finished image evaluates, so an operator who opens a shell in a worker
    /// sees the Node and the agents the lane sees. The leading blank line keeps it from joining the previous line.
    pub(crate) fn login_profile_block(&self) -> String {
        format!(
            "\n{LOGIN_PROFILE_MARKER}\neval \"$(/opt/homebrew/bin/brew shellenv)\"\nexport PATH=\"{}:{}:$PATH\"\n",
            node_bin_directory(self.pins.node_major),
            local_bin_directory(&self.home)
        )
    }

    /// Appends the toolchain block to `.zprofile`, once. The marker is matched as an *exact whole line* (the
    /// script's `awk '$0 == "# AIR VM toolchain"'`): a substring match would be satisfied by a comment about the
    /// phrase, and the block would then silently never be written.
    pub(crate) fn write_login_profile(&self) -> Result<(), AgentRefusal> {
        let path = self.home.join(".zprofile");
        let existing = match fs::read_to_string(&path) {
            Ok(content) => content,
            Err(error) if error.kind() == io::ErrorKind::NotFound => String::new(),
            Err(error) => refuse!(AgentVerb::ProvisionImage, "cannot read {}: {error}", path.display()),
        };
        if existing.lines().any(|line| line == LOGIN_PROFILE_MARKER) {
            return Ok(());
        }
        // 0644 is what `touch` produces under the base image's umask, and append is the script's `>>`.
        let appended = OpenOptions::new()
            .create(true)
            .append(true)
            .mode(0o644)
            .open(&path)
            .and_then(|mut file| file.write_all(self.login_profile_block().as_bytes()));
        appended.map_err(|error| {
            AgentRefusal::for_verb(
                AgentVerb::ProvisionImage,
                format!("cannot append the toolchain block to {}: {error}", path.display()),
            )
        })
    }

    /// Keeps the packages and drops what provisioning left behind. `npm cache clean --force` is a subprocess
    /// because npm's cache layout is npm's business; its output is dropped (the script's `>/dev/null 2>&1`) and its
    /// *failure* is not: there was no `|| true` beside that redirection, so a cache npm cannot empty fails the build
    /// rather than shipping.
    pub(crate) fn remove_provisioning_residue(&mut self) -> Result<(), AgentRefusal> {
        self.run(&self.step(["npm", "cache", "clean", "--force"]).silent())?;
        for name in RESIDUE_DIRECTORIES {
            let path = self.home.join(name);
            match fs::remove_dir_all(&path) {
                Err(error) if error.kind() != io::ErrorKind::NotFound => {
                    refuse!(AgentVerb::ProvisionImage, "cannot remove {}: {error}", path.display())
                }
                _ => {}
            }
        }
        for name in RESIDUE_FILES {
            remove_file_if_present(&self.home.join(name))?;
        }
        Ok(())
    }
}

/// The private keys in `ssh`, sorted; none when the directory is absent or unreadable, which is the zsh `(N)`
/// qualifier: no match contributes no argument rather than a literal `id_*`.
fn private_keys(ssh: &Path) -> Vec<PathBuf> {
    let Ok(entries) = fs::read_dir(ssh) else {
        return Vec::new();
    };
    let mut keys: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_name().to_string_lossy().starts_with(INHERITED_PRIVATE_KEY_PREFIX))
        .map(|entry| entry.path())
        .collect();
    keys.sort();
    keys
}

/// `rm -f` for one path: gone afterwards, and absent beforehand is not a failure.
fn remove_file_if_present(path: &Path) -> Result<(), AgentRefusal> {
    match fs::remove_file(path) {
        Err(error) if error.kind() != io::ErrorKind::NotFound => {
            refuse!(AgentVerb::ProvisionImage, "cannot remove {}: {error}", path.display())
        }
        _ => Ok(()),
    }
}
