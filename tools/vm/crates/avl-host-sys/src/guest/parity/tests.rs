use avl_base::{Environment, GuestOs, Selection};
use pretty_assertions::assert_eq;

use super::*;
use crate::guest::share_mount_path;
use crate::guest::testing::{FakeChannel, Host, failed, has};
use crate::paths::GuestPaths;
use crate::proc::Captured;
use crate::testing::fixture_backend;

/// The guest roots of the fixture's host paths: the host paths themselves on a Unix host.
fn guest_paths(host: &Host) -> GuestPaths {
    GuestPaths::of(&host.settings).unwrap()
}

/// A JSON string, for a receipt whose host path carries a `\`.
fn json_text(text: &str) -> String {
    serde_json::to_string(text).unwrap()
}

#[test]
fn parity_script_links_the_bazel_root_and_names_no_checkout() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    let script = parity_script(settings, "air-docker-1", "/mnt/AirVmShares/bazel").unwrap();
    let paths = guest_paths(&host);
    for required in [
        // Bazel's output root is one symlink onto its read-only mount.
        format!("/bin/ln -sfn '/mnt/AirVmShares/bazel' '{}'", paths.bazel_user_root()),
        format!("MARKER='{}/{PARITY_MARKER}'", settings.vm_data),
        format!("{} '{}' '{}'", settings.guest.chown, settings.vm_user, settings.vm_data),
    ] {
        assert!(script.contains(&required), "missing {required:?}:\n{script}");
    }
    // The guest gets no checkout: no path of it, no `.git`, and no directory of links.
    for absent in [host.repo.to_string_lossy().as_ref(), ".git", "PARITY", "exit 65"] {
        assert!(!script.contains(absent), "the script names {absent:?}:\n{script}");
    }
}

/// The whole script for a Windows host: every host path is its guest root, and no `C:` or `\\` reaches the guest.
#[test]
fn the_parity_script_of_a_windows_host_names_only_guest_paths() {
    let root = tempfile::tempdir().unwrap();
    let home = root.path().to_string_lossy().into_owned();
    let settings = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: fixture_backend(GuestOs::Linux),
            guest_os: GuestOs::Linux,
        },
        &Environment::from_pairs([("HOME", home), ("AIR_VM_DATA", "/data".to_owned())]),
        root.path(),
    )
    .unwrap();
    settings.set_host_paths(r"C:\Users\air\idea", r"C:\ProgramData\_bazel").unwrap();
    let script = parity_script(&settings, "air-docker-1", "/mnt/AirVmShares/bazel").unwrap();
    let owned = "'/data' '/data/state/ui-runs' '/data/out' '/data/tmp' '/data/build-download'";
    let golden = [
        "#!/bin/sh",
        "set -eu",
        "umask 022",
        "/bin/mkdir -p '/c/ProgramData'",
        "/bin/ln -sfn '/mnt/AirVmShares/bazel' '/c/ProgramData/_bazel'",
        "MARKER='/data/.air-vm-parity.json'",
        &format!("/bin/mkdir -p {owned}"),
        &format!("/bin/chown 'admin' {owned}"),
        &format!(
            r#"printf '%s' '{{"schemaVersion":1,"backend":"{}","guestOs":"linux","worker":"air-docker-1","bazelShare":"{}"}}"#,
            settings.backend, settings.bazel_share_name
        ),
        r#"' > "$MARKER""#,
        r#"/bin/chmod 644 "$MARKER""#,
        "",
    ]
    .join("\n");
    assert_eq!(script, golden);
}

// The script, run for real against a scratch tree, builds the layout and makes nothing at the checkout path. A
// second run over its own layout succeeds. The script runs through the host's `/bin/sh`.
#[cfg(unix)]
#[test]
fn the_parity_script_builds_its_layout_and_leaves_the_checkout_path_alone() {
    let scratch = tempfile::tempdir().unwrap();
    let root = scratch.path();
    let (repo, bazel, mount) = (root.join("repo"), root.join("bazel"), root.join("mount"));
    std::fs::create_dir_all(mount.join("bazel")).unwrap();
    let data = root.join("data");
    let data_text = data.to_string_lossy().into_owned();
    let home = root.to_string_lossy().into_owned();
    let environment = Environment::from_pairs([("HOME", home), ("AIR_VM_DATA", data_text.clone())]);
    // The script runs on this host, so it is the script for a guest of this host's OS: `ln -h` is BSD-only.
    let (guest_os, worker) = if cfg!(target_os = "macos") {
        (GuestOs::Macos, "air-macos-1")
    } else {
        (GuestOs::Linux, "air-docker-1")
    };
    let settings = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: avl_base::Backend::Tart,
            guest_os,
        },
        &environment,
        &root.join("scripts"),
    )
    .unwrap();
    assert_eq!(settings.vm_out, format!("{data_text}/out"));
    settings.set_host_paths(&repo, &bazel).unwrap();
    let script = parity_script(&settings, worker, &mount.join("bazel").to_string_lossy()).unwrap();
    // The chown needs root; what is under test is the layout, so it is a no-op here.
    let script = script.replace(settings.guest.chown, "/usr/bin/true");
    let run = || std::process::Command::new("/bin/sh").args(["-c", &script]).output().unwrap();

    let built = run();
    assert!(built.status.success(), "{built:?}");
    assert_eq!(std::fs::read_link(&bazel).unwrap(), mount.join("bazel"));
    assert!(data.join("out").is_dir() && data.join("tmp").is_dir());
    assert_eq!(
        std::fs::read_to_string(data.join(PARITY_MARKER)).unwrap(),
        parity_marker_content(&settings, worker)
    );
    assert!(!repo.exists(), "the layout made {}", repo.display());
    assert!(run().status.success());
}

// The testing-ui container's account owns the layout it builds, so the script hands nothing over, and provisioning
// sends no chown and no sudo. The share is probed where the skill mounts it, under `/mnt`.
// A Windows host does not drive the testing-ui container.
#[cfg(unix)]
#[tokio::test]
async fn an_unprivileged_guest_owns_its_layout_without_a_chown() {
    let host = Host::container_linux();
    let settings = &host.settings;
    let script = parity_script(settings, "air-linux-1", "/mnt/bazel").unwrap();
    assert!(!script.contains(settings.guest.chown), "{script}");
    assert!(script.contains(&format!("/bin/mkdir -p '{}'", settings.vm_data)), "{script}");
    let channel = FakeChannel::new("air-linux-1");
    host.guest(&channel).provision_parity(ShareMount::Bind).await.unwrap();
    let lines = channel.lines();
    assert!(channel.saw(settings.guest.chown).is_none(), "{lines:?}");
    assert!(!lines.iter().any(|line| line.contains("sudo")), "{lines:?}");
    let [bazel_share] = share::shares(settings).unwrap();
    let probe = format!("/bin/test -e {}", share_mount_path(settings, &bazel_share.name));
    assert!(lines.contains(&probe), "missing {probe:?} in {lines:?}");
    // The privileged Linux guest keeps its chown line, after the `mkdir` and before the marker is written.
    let privileged = Host::new(GuestOs::Linux);
    let settings = &privileged.settings;
    let script = parity_script(settings, "air-linux-1", "/mnt/bazel").unwrap();
    let lines: Vec<&str> = script.lines().collect();
    let chown = lines
        .iter()
        .position(|line| line.starts_with(settings.guest.chown))
        .unwrap_or_else(|| panic!("no chown in {script}"));
    assert!(lines[chown - 1].starts_with("/bin/mkdir -p"), "{script}");
    assert!(lines[chown + 1].starts_with("printf"), "{script}");
}

#[test]
fn parity_marker_content_is_one_json_line() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    assert_eq!(
        parity_marker_content(settings, "air-docker-1"),
        format!(
            r#"{{"schemaVersion":1,"backend":"{}","guestOs":"linux","worker":"air-docker-1","bazelShare":"{}"}}"#,
            settings.backend, settings.bazel_share_name
        ) + "\n"
    );
}

/// The mount kind alone decides the remount: a VirtioFS device gets the sweep script written and run, and bind
/// mounts get neither, because the sweep fails at `mount -t virtiofs` in a container with no such device. The share
/// probe and the parity script run for both kinds.
#[tokio::test]
async fn provision_parity_remounts_a_virtiofs_device_and_leaves_bind_mounts_alone() {
    // The VirtioFS device is the macOS guest's, of Tart and Parallels. The bind mounts are a Docker worker's.
    let cases = if cfg!(windows) {
        vec![(GuestOs::Linux, ShareMount::Bind, false)]
    } else {
        vec![
            (GuestOs::Macos, ShareMount::VirtioFs, true),
            (GuestOs::Linux, ShareMount::Bind, false),
        ]
    };
    for (guest_os, mount, remounts) in cases {
        let host = Host::new(guest_os);
        let settings = &host.settings;
        let [bazel_share] = share::shares(settings).unwrap();
        let probe = format!("/bin/test -e {}", share_mount_path(settings, &bazel_share.name));
        let sweep = format!("/bin/sh {}/state/remount-shares.sh", settings.vm_data);
        let channel = FakeChannel::new(&settings.workers[0]);
        host.guest(&channel).provision_parity(mount).await.unwrap();
        let lines = channel.lines();
        assert_eq!(
            lines.iter().any(|line| line.contains("remount-shares.sh")),
            remounts,
            "{mount:?}: {lines:?}"
        );
        assert_eq!(lines.iter().any(|line| line.contains(&sweep)), remounts, "{mount:?}: {lines:?}");
        assert!(lines.contains(&probe), "{mount:?}: missing {probe:?} in {lines:?}");
        assert!(
            lines.iter().any(|line| line.contains("provision-parity.sh")),
            "{mount:?}: {lines:?}"
        );
    }
}

/// Provisioning reads nothing of the checkout and writes a script that names none of it.
#[tokio::test]
async fn provision_parity_writes_a_script_without_the_checkout() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    std::fs::write(host.repo.join("plugins"), "x").unwrap();
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel).provision_parity(ShareMount::Bind).await.unwrap();
    let script_path = format!("{}/state/provision-parity.sh", settings.vm_data);
    let script = channel
        .calls()
        .into_iter()
        .find(|call| has(&call.argv, &script_path) && has(&call.argv, "/usr/bin/tee"))
        .and_then(|call| call.options.stdin)
        .map(|stdin| String::from_utf8(stdin).unwrap())
        .expect("a parity script was written");
    assert!(!script.contains("plugins") && !script.contains(".git"), "{script}");
    let lines = channel.lines();
    assert!(!lines.iter().any(|line| line.contains(".git")), "{lines:?}");
    // Root makes the state directory, then hands it to the worker user - without which the very next step, a `tee`
    // running as that user, fails on a root-owned directory.
    assert!(channel.saw(settings.guest.chown).is_some(), "{lines:?}");
}

#[tokio::test]
async fn ensure_parity_ready_probes_as_the_worker_user() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    write_init_receipt(settings, "air-docker-1").unwrap();
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel).ensure_parity_ready().await.unwrap();
    let as_user = format!("/usr/bin/sudo -H -u {} /bin/test", settings.vm_user);
    let paths = guest_paths(&host);
    assert_eq!(
        channel.lines(),
        [
            format!("{as_user} -f {}/{PARITY_MARKER}", settings.vm_data),
            format!("{as_user} -w {}", settings.vm_out),
            format!("{as_user} -w {}", settings.vm_tmp),
            format!("{as_user} -w {}", settings.vm_download_cache),
            format!("{as_user} -d {}", paths.bazel_user_root()),
        ]
    );
}

/// A guest whose share sits at its host path gets no layout: provisioning makes the writable roots and writes the
/// receipt, and the readiness probes skip the marker. A Windows host does not drive the testing-ui container.
#[cfg(unix)]
#[tokio::test]
async fn shares_at_their_host_paths_need_no_layout_and_no_marker() {
    let host = Host::container_linux();
    let settings = &host.settings;
    let channel = FakeChannel::new("container-linux-1");
    host.guest(&channel).provision_worker(ShareMount::Bind).await.unwrap();
    assert_eq!(
        channel.lines(),
        [format!(
            "/bin/mkdir -p {} {} {}",
            settings.vm_out, settings.vm_tmp, settings.vm_download_cache
        )]
    );
    assert!(init_receipt_path(settings, "container-linux-1").exists());
    let probes = FakeChannel::new("container-linux-1");
    host.guest(&probes).ensure_parity_ready().await.unwrap();
    let paths = guest_paths(&host);
    assert_eq!(
        probes.lines(),
        [
            format!("/bin/test -w {}", settings.vm_out),
            format!("/bin/test -w {}", settings.vm_tmp),
            format!("/bin/test -w {}", settings.vm_download_cache),
            format!("/bin/test -d {}", paths.bazel_user_root()),
        ]
    );
}

// A receipt for another Bazel output root is refused before the guest is touched: a run would otherwise read its
// outputs through the other root's share.
#[tokio::test]
async fn ensure_parity_ready_refuses_a_receipt_for_another_bazel_root() {
    let host = Host::new(GuestOs::Linux);
    let receipt = format!(
        r#"{{"schemaVersion":1,"worker":"air-docker-1","hostBazelUserRoot":"/elsewhere","guestBazelUserRoot":"/elsewhere","bazelShare":"{}"}}"#,
        host.settings.bazel_share_name
    );
    std::fs::write(init_receipt_path(&host.settings, "air-docker-1"), receipt).unwrap();
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).ensure_parity_ready().await.unwrap_err();
    assert_eq!(refusal.code, "guest_init_stale");
    assert!(refusal.message.contains("/elsewhere"), "{}", refusal.message);
    assert!(channel.calls().is_empty(), "{:?}", channel.lines());
}

// The receipt names no checkout, so a worker serves every checkout of the host. A receipt of an older controller
// names another checkout, and it still reads; the probes then decide.
#[tokio::test]
async fn a_receipt_of_another_checkout_is_not_stale() {
    let host = Host::new(GuestOs::Linux);
    let paths = guest_paths(&host);
    let receipt = format!(
        r#"{{"schemaVersion":1,"worker":"air-docker-1","hostRepo":"/elsewhere","hostBazelUserRoot":{},"guestRepo":"/elsewhere","guestBazelUserRoot":{},"repoShare":"air-macos-repo","bazelShare":"{}"}}"#,
        json_text(&host.bazel_user_root().to_string_lossy()),
        json_text(paths.bazel_user_root()),
        host.settings.bazel_share_name
    );
    std::fs::write(init_receipt_path(&host.settings, "air-docker-1"), receipt).unwrap();
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel).ensure_parity_ready().await.unwrap();
}

#[tokio::test]
async fn ensure_parity_ready_refuses_a_failed_probe() {
    let host = Host::new(GuestOs::Linux);
    write_init_receipt(&host.settings, "air-docker-1").unwrap();
    let tmp = host.settings.vm_tmp.clone();
    let channel = FakeChannel::answering("air-docker-1", move |argv| {
        Ok(if has(argv, &tmp) { failed(1, "") } else { Captured::default() })
    });
    let refusal = host.guest(&channel).ensure_parity_ready().await.unwrap_err();
    assert_eq!(refusal.code, "guest_parity_missing");
    assert!(refusal.message.contains(&host.settings.vm_tmp));
}

// A missing receipt and a stale one are repairable; a host repository nobody resolved is not. Swallowing the second
// would re-provision forever.
#[tokio::test]
async fn parity_broken_separates_provisionable_from_real() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    assert!(host.guest(&channel).parity_broken().await.unwrap());

    let home = host.dir().join("elsewhere").to_string_lossy().into_owned();
    let unresolved = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: fixture_backend(GuestOs::Linux),
            guest_os: GuestOs::Linux,
        },
        &Environment::from_pairs([("HOME", home)]),
        host.dir(),
    )
    .unwrap();
    let guest = Guest {
        settings: &unresolved,
        ..host.guest(&channel)
    };
    assert_eq!(guest.parity_broken().await.unwrap_err().code, "host_paths_unresolved");
}

// The receipt's bytes are pinned: one camelCase line in the field order, read back as written.
#[test]
fn init_receipt_round_trips_and_refuses_another_worker() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    write_init_receipt(settings, "air-docker-1").unwrap();
    let path = init_receipt_path(settings, "air-docker-1");
    let paths = guest_paths(&host);
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        format!(
            r#"{{"schemaVersion":1,"worker":"air-docker-1","hostBazelUserRoot":{},"guestBazelUserRoot":{},"bazelShare":"{}"}}"#,
            json_text(&host.bazel_user_root().to_string_lossy()),
            json_text(paths.bazel_user_root()),
            settings.bazel_share_name
        ) + "\n"
    );
    let receipt = read_init_receipt(settings, "air-docker-1").unwrap();
    assert_eq!(
        (receipt.bazel_share.as_str(), receipt.schema_version),
        (settings.bazel_share_name.as_str(), 1)
    );
    assert_eq!(read_init_receipt(settings, "air-docker-2").unwrap_err().code, "guest_init_required");
    // Mode 0600: every receipt this controller keeps is read back fail-closed after a crash.
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = std::fs::metadata(&path).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
    }
    // Another schema, and a receipt naming another worker, are the same "not provisioned".
    std::fs::write(&path, r#"{"schemaVersion":2,"worker":"air-docker-1"}"#).unwrap();
    assert_eq!(read_init_receipt(settings, "air-docker-1").unwrap_err().code, "guest_init_required");
}
