use avl_base::format::words;
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
fn parity_script_builds_the_layout_and_guards_a_foreign_directory() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    let entries = words([".git", "community", "plugins"]);
    let script = parity_script(
        settings,
        "air-docker-1",
        "/mnt/AirVmShares/repo",
        "/mnt/AirVmShares/bazel",
        &entries,
    )
    .unwrap();
    let paths = guest_paths(&host);
    for required in [
        // Bazel's output root is one symlink onto its read-only mount…
        format!("/bin/ln -sfn '/mnt/AirVmShares/bazel' '{}'", paths.bazel_user_root()),
        // …while the repository root is a real directory of per-entry links, so `out` alone can divert.
        r#"/bin/ln -sfn '/mnt/AirVmShares/repo/community' "$PARITY"/'community'"#.to_owned(),
        format!(r#"/bin/ln -sfn '{}' "$PARITY"/out"#, settings.vm_out),
        format!("PARITY='{}'", paths.repo()),
        r#"if [ -e "$PARITY" ] && [ ! -f "$MARKER" ] && [ -n "$(ls -A "$PARITY" 2>/dev/null || true)" ]; then"#.to_owned(),
        "  exit 65".to_owned(),
        // The link set is rebuilt from scratch, so an entry removed from the repository root cannot linger.
        r#"for existing in "$PARITY"/* "$PARITY"/.[!.]* "$PARITY"/..?*; do"#.to_owned(),
        r#"  [ -L "$existing" ] || continue"#.to_owned(),
        format!("{} '{}' '{}'", settings.guest.chown, settings.vm_user, settings.vm_data),
    ] {
        assert!(script.contains(&required), "missing {required:?}:\n{script}");
    }
}

/// The whole script for a Windows host: every host path is its guest root, and no `C:` or `\\` reaches the guest.
#[test]
fn the_parity_script_of_a_windows_host_names_only_guest_paths() {
    let root = tempfile::tempdir().unwrap();
    let home = root.path().to_string_lossy().into_owned();
    let settings = Config::load(
        Selection {
            backend: fixture_backend(GuestOs::Linux),
            guest_os: GuestOs::Linux,
        },
        &Environment::from_pairs([("HOME", home), ("AIR_VM_DATA", "/data".to_owned())]),
        root.path(),
    )
    .unwrap();
    settings.set_host_paths(r"C:\Users\air\idea", r"C:\ProgramData\_bazel").unwrap();
    let entries = words([".git", "community"]);
    let script = parity_script(
        &settings,
        "air-docker-1",
        "/mnt/AirVmShares/repo",
        "/mnt/AirVmShares/bazel",
        &entries,
    )
    .unwrap();
    let owned = "'/data' '/data/state/ui-runs' '/data/out' '/data/tmp' '/data/build-download'";
    let golden = [
        "#!/bin/sh",
        "set -eu",
        "umask 022",
        "/bin/mkdir -p '/c/ProgramData'",
        "/bin/ln -sfn '/mnt/AirVmShares/bazel' '/c/ProgramData/_bazel'",
        "PARITY='/c/Users/air/idea'",
        "MARKER='/c/Users/air/idea/.air-vm-parity.json'",
        r#"if [ -e "$PARITY" ] && [ ! -f "$MARKER" ] && [ -n "$(ls -A "$PARITY" 2>/dev/null || true)" ]; then"#,
        r#"  echo "refusing to manage $PARITY: it exists without a parity marker" >&2"#,
        "  exit 65",
        "fi",
        &format!(r#"/bin/mkdir -p "$PARITY" {owned}"#),
        &format!("/bin/chown 'admin' {owned}"),
        r#"for existing in "$PARITY"/* "$PARITY"/.[!.]* "$PARITY"/..?*; do"#,
        r#"  [ -L "$existing" ] || continue"#,
        r#"  /bin/rm -f "$existing""#,
        "done",
        r#"/bin/ln -sfn '/mnt/AirVmShares/repo/.git' "$PARITY"/'.git'"#,
        r#"/bin/ln -sfn '/mnt/AirVmShares/repo/community' "$PARITY"/'community'"#,
        r#"/bin/ln -sfn '/data/out' "$PARITY"/out"#,
        &format!(
            r#"printf '%s' '{{"schemaVersion":1,"backend":"{}","guestOs":"linux","worker":"air-docker-1","repoShare":"{}","bazelShare":"{}"}}"#,
            settings.backend, settings.repo_share_name, settings.bazel_share_name
        ),
        r#"' > "$MARKER""#,
        r#"/bin/chmod 644 "$MARKER""#,
        "",
    ]
    .join("\n");
    assert_eq!(script, golden);
}

// The script, run for real against a scratch tree, refuses an unmarked directory it would otherwise rewrite, and
// builds the layout over one it owns: the guard is behaviour, not text.
// The script runs through the host's `/bin/sh`.
#[cfg(unix)]
#[test]
fn the_parity_script_refuses_a_foreign_tree_and_builds_its_own() {
    let scratch = tempfile::tempdir().unwrap();
    let root = scratch.path();
    let (repo, bazel, mount) = (root.join("repo"), root.join("bazel"), root.join("mount"));
    for directory in [mount.join("repo/community"), mount.join("bazel"), repo.clone()] {
        std::fs::create_dir_all(directory).unwrap();
    }
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
    let entries = ["community".to_owned()];
    let script = parity_script(
        &settings,
        worker,
        &mount.join("repo").to_string_lossy(),
        &mount.join("bazel").to_string_lossy(),
        &entries,
    )
    .unwrap();
    // The chown needs root; what is under test is the layout, so it is a no-op here.
    let script = script.replace(settings.guest.chown, "/usr/bin/true");
    let run = || std::process::Command::new("/bin/sh").args(["-c", &script]).output().unwrap();

    std::fs::write(repo.join("precious"), "a user's file").unwrap();
    let refused = run();
    assert_eq!(refused.status.code(), Some(65), "{refused:?}");
    assert!(repo.join("precious").exists());

    std::fs::remove_file(repo.join("precious")).unwrap();
    let built = run();
    assert!(built.status.success(), "{built:?}");
    assert_eq!(std::fs::read_link(repo.join("community")).unwrap(), mount.join("repo/community"));
    assert_eq!(std::fs::read_link(repo.join("out")).unwrap(), data.join("out"));
    assert_eq!(std::fs::read_link(&bazel).unwrap(), mount.join("bazel"));
    assert_eq!(
        std::fs::read_to_string(repo.join(PARITY_MARKER)).unwrap(),
        parity_marker_content(&settings, worker)
    );
    // A second build over its own marked tree succeeds: the layout is rebuilt, not refused.
    assert!(run().status.success());
}

#[test]
fn parity_script_refuses_an_unsafe_entry_name() {
    let host = Host::new(GuestOs::Linux);
    let refusal = parity_script(&host.settings, "air-docker-1", "/mnt/repo", "/mnt/bazel", &["a b".to_owned()]).unwrap_err();
    assert_eq!(refusal.code, "unsafe_name");
}

// Fail-closed: the name is interpolated into a shell script written into the guest, so anything a shell would
// reinterpret is refused rather than quoted more cleverly.
#[test]
fn validate_parity_entry_name_is_fail_closed() {
    for accepted in [".git", "community", "build.gradle.kts", "a_b-c+d~e@f"] {
        assert!(validate_parity_entry_name(accepted).is_ok(), "{accepted:?}");
    }
    for refused in ["", "a b", "a'b", "a$b", "a/b", "a\nb", "a;b", "a*b", "a\"b", "é"] {
        assert!(validate_parity_entry_name(refused).is_err(), "{refused:?}");
    }
}

#[test]
fn parity_marker_content_is_one_json_line() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    assert_eq!(
        parity_marker_content(settings, "air-docker-1"),
        format!(
            r#"{{"schemaVersion":1,"backend":"{}","guestOs":"linux","worker":"air-docker-1","repoShare":"{}","bazelShare":"{}"}}"#,
            settings.backend, settings.repo_share_name, settings.bazel_share_name
        ) + "\n"
    );
}

/// The mount kind alone decides the remount: a VirtioFS device gets the sweep script written and run, and bind
/// mounts get neither, because the sweep fails at `mount -t virtiofs` in a container with no such device. The share
/// probes and the parity script run for both kinds.
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
        let [repo_share, _] = share::shares(settings).unwrap();
        let probe = format!("/bin/test -e {}/.git", share_mount_path(settings, &repo_share.name));
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

#[tokio::test]
async fn provision_parity_skips_out_and_the_marker_and_sorts_the_rest() {
    let host = Host::new(GuestOs::Linux);
    let settings = &host.settings;
    for entry in ["plugins", "out", "community", PARITY_MARKER] {
        std::fs::write(host.repo.join(entry), "x").unwrap();
    }
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
    // `out` diverts to guest-local storage and the marker is the layout's own bookkeeping; neither is a link onto
    // the read-only mount.
    assert!(!script.contains(r#""$PARITY"/'out'"#), "{script}");
    assert!(!script.contains(&format!(r#""$PARITY"/'{PARITY_MARKER}'"#)), "{script}");
    // The rest are linked in sorted order: a directory listing has no order of its own.
    let linked: Vec<&str> = script.lines().filter_map(|line| line.split(r#""$PARITY"/'"#).nth(1)).collect();
    assert_eq!(linked, [".git'", "community'", "plugins'"]);
    // The mounts probed are the ones the backend declared.
    let [repo_share, bazel_share] = share::shares(settings).unwrap();
    let lines = channel.lines();
    for probe in [
        format!("/bin/test -e {}/.git", share_mount_path(settings, &repo_share.name)),
        format!("/bin/test -e {}", share_mount_path(settings, &bazel_share.name)),
    ] {
        assert!(lines.contains(&probe), "missing {probe:?} in {lines:?}");
    }
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
    let repo = paths.repo();
    assert_eq!(
        channel.lines(),
        [
            format!("{as_user} -f {repo}/{PARITY_MARKER}"),
            format!("{as_user} -r {repo}/.git"),
            format!("{as_user} -w {}", settings.vm_out),
            format!("{as_user} -w {}", settings.vm_tmp),
            format!("{as_user} -w {}", settings.vm_download_cache),
            format!("{as_user} -d {}", paths.bazel_user_root()),
        ]
    );
}

// A receipt from another checkout is refused before the guest is touched: a run from a second working copy would
// otherwise read its outputs through the first one's share.
#[tokio::test]
async fn ensure_parity_ready_refuses_a_receipt_for_another_checkout() {
    let host = Host::new(GuestOs::Linux);
    // Complete and well-formed, but for another checkout: it reads, and is stale.
    let paths = guest_paths(&host);
    let receipt = format!(
        r#"{{"schemaVersion":1,"worker":"air-docker-1","hostRepo":"/elsewhere","hostBazelUserRoot":{},"guestRepo":"/elsewhere","guestBazelUserRoot":{},"repoShare":"{}","bazelShare":"{}"}}"#,
        json_text(&host.bazel_user_root().to_string_lossy()),
        json_text(paths.bazel_user_root()),
        host.settings.repo_share_name,
        host.settings.bazel_share_name
    );
    std::fs::write(init_receipt_path(&host.settings, "air-docker-1"), receipt).unwrap();
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).ensure_parity_ready().await.unwrap_err();
    assert_eq!(refusal.code, "guest_init_stale");
    assert!(refusal.message.contains("/elsewhere"), "{}", refusal.message);
    assert!(channel.calls().is_empty(), "{:?}", channel.lines());
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
            r#"{{"schemaVersion":1,"worker":"air-docker-1","hostRepo":{},"hostBazelUserRoot":{},"guestRepo":{},"guestBazelUserRoot":{},"repoShare":"{}","bazelShare":"{}"}}"#,
            json_text(&host.repo.to_string_lossy()),
            json_text(&host.bazel_user_root().to_string_lossy()),
            json_text(paths.repo()),
            json_text(paths.bazel_user_root()),
            settings.repo_share_name,
            settings.bazel_share_name
        ) + "\n"
    );
    let receipt = read_init_receipt(settings, "air-docker-1").unwrap();
    assert_eq!(
        (receipt.host_repo.as_str(), receipt.repo_share.as_str(), receipt.schema_version),
        (host.repo.to_str().unwrap(), settings.repo_share_name.as_str(), 1)
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
