use std::collections::BTreeMap;
use std::path::Path;

use avl_base::{Backend, GuestArch, GuestOs};
use pretty_assertions::assert_eq;

use super::*;
use crate::lane::testing::{docker, settings, tart};

fn environment_of(pairs: &[String]) -> BTreeMap<String, String> {
    pairs
        .iter()
        .filter_map(|pair| pair.split_once('='))
        .map(|(name, value)| (name.to_owned(), value.to_owned()))
        .collect()
}

/// The daemon is booted with the union, which makes `--lane ui-real` a pure filter question and makes the answer
/// the same whichever lane happened to boot it. A JUnit filter belongs to one iteration, not to that lifetime.
#[test]
fn the_daemon_environment_is_the_union_without_the_junit_filter() {
    avl_affected::bridge::install_fixture();
    assert!(
        !environment_union().contains_key("JB_TEST_JUNIT5_FILTERS"),
        "a JUnit filter reached the daemon's launch environment, where it would outlive its iteration"
    );
    let settings = docker();
    let environment = daemon_environment(&settings);
    // The controller names the Node of the Docker image.
    assert_eq!(environment.get("NODE_BIN").map(String::as_str), Some("/usr/local/bin/node"));
    assert!(!environment.contains_key("JB_TEST_JUNIT5_FILTERS"));
}

/// The daemon learns where a run's secrets are, as a path that is the same for every run, and learns no value: the
/// boot environment is part of the launch digest and outlives the run.
#[test]
fn the_daemon_environment_names_the_run_secrets_directory_only() {
    let settings = docker();
    let environment = daemon_environment(&settings);
    assert_eq!(
        environment.get("AIR_VM_RUN_SECRETS").map(String::as_str),
        Some("/dev/shm/air-run-secrets")
    );
    // The locale of the daemon JVM is here, so a change of it restarts the warm daemon.
    assert_eq!(environment.get("LANG").map(String::as_str), Some(DAEMON_LOCALE));
    let union = environment_union();
    let added: Vec<&String> = environment.keys().filter(|name| !union.contains_key(*name)).collect();
    assert_eq!(added, ["AIR_VM_RUN_SECRETS", "LANG", "NODE_BIN"]);
}

/// The guest, not the host and not the hypervisor, decides which distribution and JBR a lane is built for.
#[test]
fn the_guest_decides_the_jbr_platform() {
    for (backend, guest_os, guest_arch, wanted) in [
        (Backend::Tart, GuestOs::Macos, GuestArch::Arm64, "darwin_aarch64"),
        (Backend::Parallels, GuestOs::Macos, GuestArch::Arm64, "darwin_aarch64"),
        (Backend::Docker, GuestOs::Linux, GuestArch::Arm64, "linux_aarch64"),
        // A Docker worker on an x86_64 host: the one guest that is not arm64.
        (Backend::Docker, GuestOs::Linux, GuestArch::X86_64, "linux_x64"),
    ] {
        let mut settings = settings(backend, guest_os, Path::new("/repo"));
        settings.guest_arch = guest_arch;
        assert_eq!(
            guest_jbr_platform(settings.guest_os, settings.guest_arch),
            wanted,
            "{backend} {guest_os:?} {guest_arch}"
        );
    }
}

/// Homebrew is macOS's prefix, and naming it on a Linux guest only makes the PATH lie.
#[test]
fn only_a_macos_guest_is_searched_in_homebrew() {
    assert_eq!(
        guest_platform_path_entries(GuestOs::Macos),
        ["/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"]
    );
    assert_eq!(
        guest_platform_path_entries(GuestOs::Linux),
        ["/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"]
    );
}

/// The guest PATH is the guest's Node and then the platform's own directories, in that order. A lane value is never
/// a PATH entry: the test JVM receives it as a variable of its own.
#[test]
fn the_guest_path_is_the_guests_node_then_the_platform() {
    let settings = docker();
    let test_env = BTreeMap::from([
        ("AIR_FLOW_UI_REAL".to_owned(), "true".to_owned()),
        // A value that looks like a resolved binary still contributes no directory.
        ("SOME_BIN".to_owned(), "/opt/some/bin/some".to_owned()),
    ]);
    let environment = environment_of(&guest_run_environment(
        &settings,
        &test_env,
        "run-ui-daemon-7",
        "/share/out/ui_daemon.runtime.json.runfiles",
        "/share/self/ui_daemon.runtime.json",
    ));

    // The image's Node is in `/usr/local/bin`, the first platform directory, so the PATH names it once.
    assert_eq!(settings.vm_node, "/usr/local/bin/node");
    assert_eq!(environment["PATH"], guest_platform_path_entries(GuestOs::Linux).join(":"));
    // An operator's Node in its own directory comes first, because the agent CLIs a lane drives are Node programs.
    let mut own_node = docker();
    own_node.vm_node = "/opt/air-node/bin/node".to_owned();
    let first = environment_of(&guest_run_environment(&own_node, &BTreeMap::new(), "run-ui-daemon-7", "/d", "/s"));
    let wanted: Vec<&str> = std::iter::once("/opt/air-node/bin")
        .chain(guest_platform_path_entries(GuestOs::Linux))
        .collect();
    assert_eq!(first["PATH"], wanted.join(":"));
    assert_eq!(environment["AIR_FLOW_UI_REAL"], "true");

    // The dedup moves a platform directory to the front when the Node is there.
    let mut on_platform_path = docker();
    on_platform_path.vm_node = "/usr/bin/node".to_owned();
    let shared = environment_of(&guest_run_environment(
        &on_platform_path,
        &BTreeMap::new(),
        "run-ui-daemon-7",
        "/d",
        "/s",
    ));
    assert_eq!(shared["PATH"], "/usr/bin:/usr/local/bin:/bin:/usr/sbin:/sbin");
}

/// A run's scratch, its runfiles view, and the classpath-root comparison JUnit5BazelRunner makes.
#[test]
fn the_run_environment_names_the_runfiles_tree_and_the_runs_own_scratch() {
    let settings = tart(GuestOs::Macos);
    let pairs = guest_run_environment(
        &settings,
        &BTreeMap::new(),
        "run-ui-daemon-7",
        "/share/out/ui_daemon.runtime.json.runfiles",
        "/share/runfiles-copy/ui_daemon.runtime.json",
    );
    let environment = environment_of(&pairs);
    let runfiles = "/share/out/ui_daemon.runtime.json.runfiles";
    for name in ["RUNFILES_DIR", "JAVA_RUNFILES", "TEST_SRCDIR"] {
        assert_eq!(environment[name], runfiles, "{name}");
    }
    // The runfiles-tree copy, not the bazel-out original: the runner string-compares jar parents against this.
    assert_eq!(environment["SELF_LOCATION"], "/share/runfiles-copy/ui_daemon.runtime.json");
    let run_tmp = format!("{}/run-ui-daemon-7", settings.vm_tmp);
    assert_eq!(environment["TEST_TMPDIR"], run_tmp);
    assert_eq!(environment["XML_OUTPUT_FILE"], format!("{run_tmp}/test.xml"));
    assert_eq!(environment["TEST_UNDECLARED_OUTPUTS_DIR"], format!("{run_tmp}/outputs"));
    assert_eq!(environment["JB_TEST_SANDBOX"], "False");
    assert_eq!(environment["TEST_WORKSPACE"], "_main");
    assert_eq!(environment["USER"], settings.vm_user);
    assert_eq!(environment["LOGNAME"], settings.vm_user);
    // The locale is part of the daemon environment, which is digested, and not of the run environment.
    assert!(!environment.contains_key("LANG"));
    // In name order, because nothing downstream reads position and a reproducible rendering is a chosen one.
    let mut sorted = pairs.clone();
    sorted.sort();
    assert_eq!(pairs, sorted);
}

/// A lane value wins over a name this function also sets. `HOME` is the case that matters: a lane that pinned it
/// would otherwise be silently overruled by the guest's own.
#[test]
fn a_lane_value_overrides_the_run_environment() {
    let pairs = guest_run_environment(
        &docker(),
        &BTreeMap::from([("HOME".to_owned(), "/tmp/lane-home".to_owned())]),
        "run-ui-daemon-7",
        "/d",
        "/s",
    );
    assert_eq!(environment_of(&pairs)["HOME"], "/tmp/lane-home");
    assert_eq!(pairs.iter().filter(|pair| pair.starts_with("HOME=")).count(), 1);
}
