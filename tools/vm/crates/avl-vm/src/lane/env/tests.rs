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

/// The daemon is booted with what no build produces and no lane sets: the Node of the guest, the directory of the run
/// secrets as a path that is the same for every run, and the locale. It learns no value of a secret, because the boot
/// environment is part of the launch digest and outlives the run. No JUnit filter reaches it, because a filter belongs
/// to one iteration.
#[test]
fn the_daemon_environment_is_the_node_the_run_secrets_directory_and_the_locale() {
    let settings = docker();
    let environment = daemon_environment(&settings);
    assert_eq!(
        environment,
        BTreeMap::from([
            ("AIR_VM_RUN_SECRETS".to_owned(), "/dev/shm/air-run-secrets".to_owned()),
            ("LANG".to_owned(), DAEMON_LOCALE.to_owned()),
            ("NODE_BIN".to_owned(), "/usr/local/bin/node".to_owned()),
        ])
    );
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
/// a PATH entry: the lane daemon receives it as a variable of its own.
#[test]
fn the_guest_path_is_the_guests_node_then_the_platform() {
    let settings = docker();
    let daemon_environment = BTreeMap::from([
        ("AIR_FLOW_UI_REAL".to_owned(), "true".to_owned()),
        // A value that looks like a resolved binary still contributes no directory.
        ("SOME_BIN".to_owned(), "/opt/some/bin/some".to_owned()),
    ]);
    let environment = environment_of(&guest_run_environment(&settings, &daemon_environment));

    // The image's Node is in `/usr/local/bin`, the first platform directory, so the PATH names it once.
    assert_eq!(settings.vm_node, "/usr/local/bin/node");
    assert_eq!(environment["PATH"], guest_platform_path_entries(GuestOs::Linux).join(":"));
    // An operator's Node in its own directory comes first, because the agent CLIs a lane drives are Node programs.
    let mut own_node = docker();
    own_node.vm_node = "/opt/air-node/bin/node".to_owned();
    let first = environment_of(&guest_run_environment(&own_node, &BTreeMap::new()));
    let wanted: Vec<&str> = std::iter::once("/opt/air-node/bin")
        .chain(guest_platform_path_entries(GuestOs::Linux))
        .collect();
    assert_eq!(first["PATH"], wanted.join(":"));
    assert_eq!(environment["AIR_FLOW_UI_REAL"], "true");

    // The dedup moves a platform directory to the front when the Node is there.
    let mut on_platform_path = docker();
    on_platform_path.vm_node = "/usr/bin/node".to_owned();
    let shared = environment_of(&guest_run_environment(&on_platform_path, &BTreeMap::new()));
    assert_eq!(shared["PATH"], "/usr/bin:/usr/local/bin:/bin:/usr/sbin:/sbin");
}

/// The lane daemon is no Bazel test, so its run environment is the account, the PATH and the daemon environment, and no
/// variable of a Bazel test. The lane IDE gets only the variables of its context policy, and `lane-run` reads the Bazel
/// variables that a real Bazel test sets.
#[test]
fn the_run_environment_is_the_account_the_path_and_the_daemon_environment() {
    let settings = tart(GuestOs::Macos);
    let daemon_environment = daemon_environment(&settings);
    let pairs = guest_run_environment(&settings, &daemon_environment);
    let environment = environment_of(&pairs);
    let names: Vec<&str> = environment.keys().map(String::as_str).collect();
    assert_eq!(names, ["AIR_VM_RUN_SECRETS", "HOME", "LANG", "LOGNAME", "NODE_BIN", "PATH", "USER"]);
    assert_eq!(environment["HOME"], settings.vm_home);
    assert_eq!(environment["USER"], settings.vm_user);
    assert_eq!(environment["LOGNAME"], settings.vm_user);
    // In name order, because nothing downstream reads position and a reproducible rendering is a chosen one.
    let mut sorted = pairs.clone();
    sorted.sort();
    assert_eq!(pairs, sorted);
}

/// A value of the daemon environment wins over a name this function also sets. `HOME` is the case that matters: a value
/// that pinned it would otherwise be silently overruled by the guest's own.
#[test]
fn a_daemon_environment_value_overrides_the_run_environment() {
    let pairs = guest_run_environment(&docker(), &BTreeMap::from([("HOME".to_owned(), "/tmp/lane-home".to_owned())]));
    assert_eq!(environment_of(&pairs)["HOME"], "/tmp/lane-home");
    assert_eq!(pairs.iter().filter(|pair| pair.starts_with("HOME=")).count(), 1);
}
