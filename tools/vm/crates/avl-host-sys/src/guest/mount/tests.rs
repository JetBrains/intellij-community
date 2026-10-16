use avl_base::GuestOs;
use pretty_assertions::assert_eq;

// The script and the device are read only by the Unix tests, which have a macOS guest.
#[cfg(unix)]
use super::*;
use crate::guest::testing::{FakeChannel, Host, failed};

// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
fn macos_device() -> &'static VirtiofsMount {
    GuestOs::Macos.profile().virtiofs.expect("the macOS guest has the device")
}

// Every binary absolute, because `sudo -H`'s secure_path on a macOS guest has no /sbin: a bare `mount` was "command
// not found", the sweep unmounted nothing while exiting 0, and the mount then failed "Resource busy".
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[test]
fn remount_script_spells_every_binary_absolutely() {
    let host = Host::new(GuestOs::Macos);
    let device = macos_device();
    let script = remount_script(&host.settings, device);
    for required in [
        format!("{} | /usr/bin/awk", device.mount_binary),
        format!(r#"{0} "$mounted" || {0} -f "$mounted""#, device.umount_binary),
        device.mount_shares.to_owned(),
        r#"/bin/mkdir -p "$MOUNT""#.to_owned(),
        r#"/bin/rm -f "$LIST""#.to_owned(),
    ] {
        assert!(script.contains(&required), "the script is missing {required:?}:\n{script}");
    }
    assert!(script.contains(device.mounted_filesystem), "{script}");
}

// The script really does reduce a macOS `mount` line to the bare mount point, a path with a space included: run
// through the host's `/bin/sh` and `awk`.
#[cfg(unix)]
#[test]
fn the_remount_sweep_reduces_a_mount_line_to_the_mount_point() {
    let host = Host::new(GuestOs::Macos);
    let script = remount_script(&host.settings, macos_device());
    let sweep = script.lines().find(|line| line.contains("/usr/bin/awk")).unwrap();
    let awk = &sweep[sweep.find("/usr/bin/awk").unwrap()..sweep.find(" > ").unwrap()];
    let listing = "tag on /Volumes/X Y (AppleVirtIOFS, local)\n/dev/disk1 on / (apfs)\n";
    let shell = format!("printf '%s' '{listing}' | {awk}");
    let output = std::process::Command::new("/bin/sh").args(["-c", &shell]).output().unwrap();
    assert_eq!(String::from_utf8_lossy(&output.stdout), "/Volumes/X Y\n", "{shell}");
}

// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn remount_shares_runs_the_script_it_just_wrote() {
    let host = Host::new(GuestOs::Macos);
    let channel = FakeChannel::new("air-macos-1");
    host.guest(&channel).remount_shares().await.unwrap();
    let script = format!("{}/state/remount-shares.sh", host.settings.vm_data);
    let write = channel.saw("/usr/bin/tee").expect("the script was written");
    assert!(write.argv.contains(&script), "{:?}", channel.lines());
    // The bytes travel on stdin, which is the only channel a guest exec has for content.
    assert_eq!(
        write.options.stdin.as_deref(),
        Some(remount_script(&host.settings, macos_device()).as_bytes())
    );
    assert_eq!(channel.lines().last().unwrap(), &format!("/usr/bin/sudo -H /bin/sh {script}"));
}

// A Docker worker holds its shares as bind mounts, so a remount is refused before the guest is touched.
#[tokio::test]
async fn remount_shares_refuses_a_guest_without_the_device() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).remount_shares().await.unwrap_err();
    assert_eq!(refusal.code, "unsupported_backend_operation");
    assert!(channel.calls().is_empty(), "{:?}", channel.lines());
}

// The mount point itself is probed, which is all the Bazel share can be asked: it has no entry that is guaranteed to
// be there.
#[tokio::test]
async fn require_share_mounted_probes_the_mount_point_itself() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let name = &host.settings.bazel_share_name;
    let mount = host.guest(&channel).require_share_mounted(name).await.unwrap();
    assert_eq!(mount, format!("{}/{name}", host.settings.guest.share_mount));
    assert_eq!(channel.lines(), [format!("/bin/test -e {mount}")]);
}

#[tokio::test]
async fn require_share_mounted_refuses_an_unmounted_share() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "")));
    let refusal = host
        .guest(&channel)
        .require_share_mounted(&host.settings.bazel_share_name)
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "guest_share_unmounted");
}
