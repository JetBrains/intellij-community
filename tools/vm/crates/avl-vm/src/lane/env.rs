//! The environment that the controller starts the lane daemon with.

use std::collections::BTreeMap;

use avl_base::{Config, GuestArch, GuestOs};

use crate::lane::secrets::RUN_SECRETS_VARIABLE;

/// The `LANG` of the lane daemon. See [`daemon_environment`].
pub(crate) const DAEMON_LOCALE: &str = "C.UTF-8";

/// The environment of the lane daemon that no build produces: the guest's Node, the directory of the run secrets,
/// and the locale.
///
/// The lane daemon keeps its environment for its lifetime, so this is part of its launch identity. It is the same
/// answer for every worker of one pool, because nothing in it is a guest's own reply.
pub(crate) fn daemon_environment(settings: &Config) -> BTreeMap<String, String> {
    let mut environment = BTreeMap::new();
    // The guest's node, named by the controller: the Node of the Docker image or of the macOS golden, or the one
    // `AIR_VM_NODE` names. The supervisor used to answer this with its own interpreter, which is the same file only
    // by coincidence.
    environment.insert("NODE_BIN".to_owned(), settings.vm_node.clone());
    // A path and never a value: a run writes its `--test-env` files there, and the boot environment stays the same
    // for every run.
    environment.insert(RUN_SECRETS_VARIABLE.to_owned(), settings.vm_run_secrets.clone());
    // The locale of the lane daemon. The context policy of the supervisor passes it on to the lane IDE. The Linux worker
    // image ships only the C locales. A locale that is not installed falls back to POSIX, and a JVM in it cannot name a
    // file with a character outside ASCII. `C.UTF-8` is in every glibc since 2.35. It is here and not in the run
    // environment, because a daemon keeps its environment for its lifetime. A change must restart the warm daemon.
    environment.insert("LANG".to_owned(), DAEMON_LOCALE.to_owned());
    environment
}

/// The JBR and distribution platform this guest's descriptor must declare.
///
/// The three guests are the three `ui_lane_ide.bzl` offers: darwin_aarch64 and linux_aarch64 from an arm64 host, and
/// linux_x64 from an x86_64 host, whose Docker worker runs the host's own architecture. There is no Windows lane
/// distribution or JBR at all. The pairing matters because a mismatch is silent otherwise:
/// `Contents/Home` does exist inside the macOS tarball, and the first complaint is an exec-format error from a
/// daemon that already reported itself started. This is also the check that catches a host whose Starlark
/// selection and [`GuestArch::of`] disagree.
pub(crate) const fn guest_jbr_platform(guest_os: GuestOs, guest_arch: GuestArch) -> &'static str {
    match (guest_os, guest_arch) {
        (GuestOs::Macos, _) => "darwin_aarch64",
        // The lane's IDE follows the guest, not the host: a Linux worker needs the Linux distribution and the
        // linux_aarch64 JBR out of a build running on macOS.
        (GuestOs::Linux, GuestArch::Arm64) => "linux_aarch64",
        (GuestOs::Linux, GuestArch::X86_64) => "linux_x64",
    }
}

/// The platform directories a guest keeps its own executables in: the tail of the daemon's PATH. Homebrew is
/// macOS's prefix, and naming it on a Linux guest only makes the PATH lie.
pub(crate) fn guest_platform_path_entries(guest_os: GuestOs) -> Vec<&'static str> {
    let homebrew = (guest_os == GuestOs::Macos).then_some("/opt/homebrew/bin");
    homebrew
        .into_iter()
        .chain(["/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"])
        .collect()
}

/// The `NAME=VALUE` environment that the lane daemon starts with, in name order: the account, the PATH, and then
/// `daemon_environment`.
///
/// The lane daemon is no Bazel test, so it gets no variable of one. The lane IDE gets only the variables of its context
/// policy, and `lane-run` reads the Bazel variables that a real Bazel test sets.
///
/// A value of `daemon_environment` wins over a name that this function also sets.
pub(crate) fn guest_run_environment(settings: &Config, daemon_environment: &BTreeMap<String, String>) -> Vec<String> {
    let node_dir = settings
        .vm_node
        .rsplit_once('/')
        .map_or(".", |(dir, _)| if dir.is_empty() { "/" } else { dir })
        .to_owned();
    // The guest's Node first, then the platform's own directories. The Node comes first because every agent CLI a
    // lane drives is a Node program, and a distribution `node` on the PATH can be older than the pin.
    let mut path_entries: Vec<String> = Vec::new();
    for entry in std::iter::once(node_dir.as_str()).chain(guest_platform_path_entries(settings.guest_os)) {
        if !path_entries.iter().any(|seen| seen == entry) {
            path_entries.push(entry.to_owned());
        }
    }
    let mut environment: BTreeMap<String, String> = [
        ("HOME", settings.vm_home.clone()),
        ("USER", settings.vm_user.clone()),
        ("LOGNAME", settings.vm_user.clone()),
        ("PATH", path_entries.join(":")),
    ]
    .into_iter()
    .map(|(name, value)| (name.to_owned(), value))
    .collect();
    environment.extend(daemon_environment.iter().map(|(name, value)| (name.clone(), value.clone())));
    environment.into_iter().map(|(name, value)| format!("{name}={value}")).collect()
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
