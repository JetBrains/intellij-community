//! The macOS golden-image pipeline's guest half: what its verbs are told, where they apply, and how they refuse.
//!
//! `provision-image` turns a freshly cloned Cirrus base into the golden image and `validate-image` proves the
//! result, as consecutive `shell` provisioners of the one `build` block in
//! `community/tools/vm/provision/air-macos.pkr.hcl`. Packer passes the three pins by name
//! (`--macos-version --node-major --junie-version`, [`ImagePins`](crate::cli::ImagePins)) and reads nothing but
//! the exit status. What the two verbs share is declared here, because the pair's whole value is that the second
//! one checks the first one's work.

pub(crate) mod provision;
pub(crate) mod validate;

#[cfg(test)]
mod tests;

use std::fs;
use std::io;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

/// The account the base image publishes and this pipeline seals.
///
/// `admin`/`admin` is the Cirrus image's documented public bootstrap contract - the pair `air-macos.pkr.hcl` hands
/// its SSH communicator, the account `/etc/kcpassword` autologs in, and the user `validate-image` requires the
/// finished image to still name. A constant and not a lookup: a verb that sanitized `$USER`'s home instead would
/// pass every check it runs while leaving the real home untouched.
pub(crate) const IMAGE_ACCOUNT: &str = "admin";

/// The other half of that published pair. Separate from [`IMAGE_ACCOUNT`] although the strings are equal, because
/// they are not the same fact: this one is handed to `sysadminctl` on a command line the guest's whole process
/// table can read, which is safe only for a password upstream publishes.
pub(crate) const IMAGE_PASSWORD: &str = "admin";

/// The one home directory these verbs read, sanitize and write a login profile into.
pub(crate) const IMAGE_HOME: &str = "/Users/admin";

/// The Homebrew keg the base image's Node lives in, derived from the pinned major so `versions.env` stays the only
/// place the number appears: `/opt/homebrew/opt/node@24/bin` for 24.
pub(crate) fn node_bin_directory(node_major: u32) -> String {
    format!("/opt/homebrew/opt/node@{node_major}/bin")
}

/// Where Junie's installer puts its shim, under the home being provisioned.
pub(crate) fn local_bin_directory(home: &Path) -> String {
    format!("{}/.local/bin", home.display())
}

/// The rest of both verbs' PATH, in order. A *replacement* and not an addition: an image build must not depend on
/// the environment of whatever invoked it, which is why `/opt/homebrew/bin` is named even though the base image's
/// own shell profile would have supplied it.
pub(crate) const SYSTEM_PATH: [&str; 6] = ["/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"];

/// What the Cirrus base must already provide, in the order the scripts checked them.
///
/// Checked, never installed. `tart-guest-agent` is what makes `tart exec` work at all - the channel the controller
/// reaches every worker over. Checking rather than installing keeps the base pin meaningful: if the base stops
/// shipping one of these, the answer is a reviewed new base digest, not a `brew install` that makes the image's
/// contents depend on when it was built.
pub(crate) const REQUIRED_BASE_EXECUTABLES: [&str; 4] = ["git", "node", "npm", "tart-guest-agent"];

/// The directory the worker scripts are installed into.
pub(crate) const WORKER_SCRIPT_DIRECTORY: &str = "/usr/local/sbin";

/// One helper script Packer uploads and `provision-image` installs.
pub(crate) struct WorkerScript {
    /// The basename of a `provisioner "file"` block's upload.
    pub upload: &'static str,
    /// The name other things invoke it by.
    pub installed: &'static str,
    /// What stops working without it, because "not executable" is only actionable next to that.
    pub purpose: &'static str,
}

/// The three helper scripts. Both names of every pair are decided elsewhere and neither is derived from the other:
/// changing either side alone must break the build in a way that says which side was changed.
pub(crate) const WORKER_SCRIPTS: [WorkerScript; 3] = [
    WorkerScript {
        upload: "air-init-worker-storage.sh",
        installed: "air-init-worker-storage",
        purpose: "a worker prepares its WorkerData volume with it on every boot",
    },
    WorkerScript {
        upload: "air-audit-public-image.sh",
        installed: AUDIT_SCRIPT_NAME,
        purpose: "it is the publishability gate this verb ends with",
    },
    WorkerScript {
        upload: "air-ensure-ssh-host-keys.sh",
        installed: "air-ensure-ssh-host-keys",
        purpose: "a worker regenerates its own SSH host keys with it, and the image ships none",
    },
];

/// The installed name of the gate `validate-image` ends with, needed on its own because it is invoked.
pub(crate) const AUDIT_SCRIPT_NAME: &str = "air-audit-public-image";

pub(crate) fn installed_worker_script(name: &str) -> String {
    format!("{WORKER_SCRIPT_DIRECTORY}/{name}")
}

/// The environment every step of an image verb runs with.
///
/// Passed to each step rather than written into this process's own environment, which is what an `export` in
/// the scripts amounted to: every child resolves and runs against one PATH. Each of the other three is load
/// bearing: `HOMEBREW_NO_AUTO_UPDATE` stops `brew` from refreshing its formula index (the image would depend on
/// the minute it was built), `HOMEBREW_NO_INSTALL_CLEANUP` stops it from removing the pinned Node keg the PATH
/// points at, and `npm_config_update_notifier` stops npm writing a background update check into the image's home.
pub(crate) fn image_environment(path: &[String]) -> Vec<(String, String)> {
    [
        ("PATH", path.join(":")),
        ("HOMEBREW_NO_AUTO_UPDATE", "1".to_owned()),
        ("HOMEBREW_NO_INSTALL_CLEANUP", "1".to_owned()),
        ("npm_config_update_notifier", "false".to_owned()),
    ]
    .into_iter()
    .map(|(name, value)| (name.to_owned(), value))
    .collect()
}

/// How the image verbs read a guest path: `fs::metadata` in the guest, re-rooted in a test.
pub(crate) type Stat = Box<dyn Fn(&Path) -> io::Result<fs::Metadata>>;

/// Whether a path is a regular file anyone may execute. Symlinks are followed, which is required: everything
/// under `/opt/homebrew/bin` is a symlink into a formula's keg, and so is the Junie shim.
pub(crate) fn is_executable(stat: &Stat, path: &Path) -> bool {
    stat(path).is_ok_and(|info| info.is_file() && info.permissions().mode() & 0o111 != 0)
}

/// Where a tool is on `search_path`, read the way `command -v` reads PATH.
pub(crate) fn resolve(stat: &Stat, search_path: &[String], name: &str) -> Option<PathBuf> {
    search_path
        .iter()
        .map(|directory| Path::new(directory).join(name))
        .find(|candidate| is_executable(stat, candidate))
}

/// Whether `version` (`v24.10.0`) names this major. The trailing dot is the whole check: without it a `v240` would
/// satisfy a pin of 24.
pub(crate) fn is_node_major(version: &str, major: u32) -> bool {
    version.starts_with(&format!("v{major}."))
}
