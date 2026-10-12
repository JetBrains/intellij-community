//! The environment the guest launches its daemon with, and the one each iteration's test JVM gets.

use std::collections::BTreeMap;

use avl_affected::ui_lanes;
use avl_base::{Config, GuestArch, GuestOs};
use avl_host_sys::guest::guest_join;

use crate::lane::lanes::{JUNIT5_FILTERS, lane_selection};
use crate::lane::secrets::RUN_SECRETS_VARIABLE;

/// The environment a daemon is exec'd with that no build produces: the lane values, the guest's Node, and the
/// directory of the run secrets.
///
/// A JVM cannot change its own environment, so this is fixed for a daemon's lifetime and part of its launch
/// identity. It is the same answer for every worker of one pool, because nothing in it is a guest's own reply.
pub(crate) fn daemon_environment(settings: &Config) -> BTreeMap<String, String> {
    let mut environment = environment_union();
    // The guest's node, named by the controller: the Node of the Docker image or of the macOS golden, or the one
    // `AIR_VM_NODE` names. The supervisor used to answer this with its own interpreter, which is the same file only
    // by coincidence.
    environment.insert("NODE_BIN".to_owned(), settings.vm_node.clone());
    // A path and never a value: a run writes its `--test-env` files there, and the boot environment stays the same
    // for every run.
    environment.insert(RUN_SECRETS_VARIABLE.to_owned(), settings.vm_run_secrets.clone());
    environment
}

/// Every variable any UI lane sets by value, whether or not this run selected that lane.
///
/// The daemon is the test JVM, and a JVM's environment is fixed at exec time - so a lane's `--test_env` values
/// cannot be applied per iteration the way a one-shot run applied them. Booting the daemon with the union instead
/// makes `--lane ui-real` a pure filter question, and makes the answer the same whichever lane happened to boot it.
pub(crate) fn environment_union() -> BTreeMap<String, String> {
    let mut union: BTreeMap<String, String> = ui_lanes()
        .iter()
        .filter_map(|lane| lane_selection(lane.name).ok())
        .flat_map(|selection| selection.test_env)
        .collect();
    // JUnit filters belong to a single iteration, not to the daemon's lifetime.
    union.remove(JUNIT5_FILTERS);
    union
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

/// The `NAME=VALUE` environment one iteration's test JVM is exec'd with, in name order.
///
/// `runfiles_root` and `self_location` are guest paths: the guest's own view of the runfiles tree, which the
/// caller maps from the host tree. Every path here is a guest path, so it is joined as text with `/`, never with a
/// host path operation that writes `\` on a Windows host.
///
/// A lane value in `test_env` wins over a name this function also sets. `HOME` is the case that matters: a lane
/// that pinned it would otherwise be silently overruled by the guest's own.
pub(crate) fn guest_run_environment(
    settings: &Config,
    test_env: &BTreeMap<String, String>,
    run_id: &str,
    runfiles_root: &str,
    self_location: &str,
) -> Vec<String> {
    let run_tmp = guest_join(&settings.vm_tmp, run_id);
    let runfiles = runfiles_root.to_owned();
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
        ("TEST_UNDECLARED_OUTPUTS_DIR", guest_join(&run_tmp, "outputs")),
        ("XML_OUTPUT_FILE", guest_join(&run_tmp, "test.xml")),
        ("TEST_TMPDIR", run_tmp),
        ("RUNFILES_DIR", runfiles.clone()),
        ("JAVA_RUNFILES", runfiles.clone()),
        ("TEST_SRCDIR", runfiles),
        ("TEST_WORKSPACE", "_main".to_owned()),
        // The runfiles-tree copy, not the bazel-out original: JUnit5BazelRunner selects the test classpath roots
        // by string-comparing jar parents against SELF_LOCATION's parent.
        ("SELF_LOCATION", self_location.to_owned()),
        // jps_test sets this on the test action for JUnit5BazelRunner; the IDE targets are unsandboxed.
        ("JB_TEST_SANDBOX", "False".to_owned()),
        ("HOME", settings.vm_home.clone()),
        ("USER", settings.vm_user.clone()),
        ("LOGNAME", settings.vm_user.clone()),
        ("LANG", "en_US.UTF-8".to_owned()),
        ("PATH", path_entries.join(":")),
    ]
    .into_iter()
    .map(|(name, value)| (name.to_owned(), value))
    .collect();
    environment.extend(test_env.iter().map(|(name, value)| (name.clone(), value.clone())));
    environment.into_iter().map(|(name, value)| format!("{name}={value}")).collect()
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
