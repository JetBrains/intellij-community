//! What these assert on is argv and *order*: the guest agent and its boot verb have to reach the guest in the one
//! sequence where each step's destination already exists. `AIR_VM_GUEST_AGENT_SOURCE` names the agent so the
//! install resolves without Bazel, which is why every provisioning case passes [`ForbiddenBazel`]: that is an
//! assertion, not a shortcut. A Bazel query that came back fails the test rather than passing it.

use avl_base::GuestOs;
use avl_base::format::words;
use pretty_assertions::assert_eq;

use super::*;
use crate::guest::testing::{FakeChannel, ForbiddenBazel, Host, failed, has, prose, said};
use crate::proc::Captured;

/// What the controller's `validate_argv` passes: the display, and the runtime root the sweep reads.
fn validate_argv() -> Vec<String> {
    words([":88", "/data/daemon-runtime"])
}

fn provisioned_host() -> Host {
    let mut host = Host::new(GuestOs::Linux);
    host.settings.vm_agent_source = Some(host.agent_source("agent bytes"));
    host
}

// Provisioning ends with the guest proving itself. The assertion is the whole sequence: an order that ran the verb
// before the install would run a binary that is not there. The transcript is exact, so no retired verb comes back.
#[tokio::test]
async fn provision_linux_ends_with_the_guest_proving_itself() {
    let host = provisioned_host();
    let settings = &host.settings;
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap();
    let sudo = "/usr/bin/sudo -H";
    let as_worker = format!("{sudo} -u {}", settings.vm_user);
    let (agent, data) = (&settings.vm_agent, &settings.vm_data);
    assert_eq!(
        channel.lines(),
        [
            format!("{sudo} /bin/mkdir -p {data}/state"),
            format!("{sudo} {} -R {} {data}", settings.guest.chown, settings.vm_user),
            format!("{as_worker} /usr/bin/tee {agent}.incoming"),
            format!("{as_worker} /bin/chmod 700 {agent}.incoming"),
            format!("{as_worker} /bin/mv -f {agent}.incoming {agent}"),
            format!("{sudo} {agent} validate-guest :88 /data/daemon-runtime"),
        ]
    );
    assert_eq!(channel.calls()[2].options.stdin.as_deref(), Some(&b"agent bytes"[..]));
    // The verb runs as root, because it reads the display and the loader of the whole guest.
    let call = channel.saw("validate-guest").expect("the verb ran");
    assert!(!has(&call.argv, "-u"), "validate-guest ran as the worker: {:?}", call.argv);
}

// The testing-ui container's account makes its own data directory and owns it: the boot sends no sudo and no chown,
// and the agent install crosses the channel as that account, with no prefix in front of `tee`, `chmod` and `mv`.
// A Windows host does not drive the testing-ui container.
#[cfg(unix)]
#[tokio::test]
async fn provision_linux_on_an_unprivileged_guest_runs_no_sudo_and_no_chown() {
    let mut host = Host::container_linux();
    host.settings.vm_agent_source = Some(host.agent_source("agent bytes"));
    let settings = &host.settings;
    let channel = FakeChannel::new("container-linux-1");
    host.guest(&channel)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap();
    let agent = &settings.vm_agent;
    assert_eq!(
        channel.lines(),
        [
            format!("/bin/mkdir -p {}/state", settings.vm_data),
            format!("/usr/bin/tee {agent}.incoming"),
            format!("/bin/chmod 700 {agent}.incoming"),
            format!("/bin/mv -f {agent}.incoming {agent}"),
            format!("{agent} validate-guest :88 /data/daemon-runtime"),
        ]
    );
    assert_eq!(channel.calls()[1].options.stdin.as_deref(), Some(&b"agent bytes"[..]));
}

// A worker that was never asked to prove itself is refused rather than provisioned, before the guest is touched.
#[tokio::test]
async fn provision_linux_refuses_without_a_self_check() {
    let host = provisioned_host();
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).provision_linux(&ForbiddenBazel, &[]).await.unwrap_err();
    assert_eq!(refusal.code, "linux_validation_unset");
    assert!(channel.calls().is_empty(), "{:?}", channel.lines());
}

// An agent install that failed leaves no verb to run, so the boot stops there.
#[tokio::test]
async fn provision_linux_does_not_validate_a_worker_whose_agent_install_failed() {
    let host = provisioned_host();
    let channel = FakeChannel::answering("air-docker-1", |argv| {
        Ok(if has(argv, "/bin/mv") {
            failed(1, "mv: cannot move")
        } else {
            Captured::default()
        })
    });
    host.guest(&channel)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap_err();
    assert!(channel.saw("validate-guest").is_none(), "{:?}", channel.lines());
}

// The refusal a boot verb answers is the *agent's own*, carried whole - the entire reason it goes through
// `invoke_agent` rather than `as_root`, whose refusal withholds a guest's output by contract.
#[tokio::test]
async fn provision_linux_reports_the_verbs_own_refusal() {
    let host = provisioned_host();
    let refusal = r#"{"schemaVersion":1,"ok":false,"command":"validate-guest","error":{"code":"linux_window_manager_missing","message":"no window manager is registered on :88"}}"#;
    let channel = FakeChannel::answering("air-docker-1", move |argv| {
        Ok(if has(argv, "validate-guest") {
            failed(70, refusal)
        } else {
            Captured::default()
        })
    });
    let refusal = host
        .guest(&channel)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "linux_window_manager_missing");
    assert!(
        refusal.message.contains("no window manager is registered on :88"),
        "{}",
        refusal.message
    );
    assert_eq!(refusal.exit, Exit::SOFTWARE);
}

// A green boot's report is the only account of it that survives, so it is noted as the document. That keeps the
// note from going stale against the guest's report types.
#[tokio::test]
async fn provision_linux_notes_what_the_verb_reported() {
    let host = provisioned_host();
    let validated = r#"{"display":":88","windowManager":"0x400001","windowManagerProbes":2,"sharedObjects":41}"#;
    let reply = format!(r#"{{"schemaVersion":1,"ok":true,"command":"validate-guest","data":{validated}}}"#);
    let channel = FakeChannel::answering("air-docker-1", move |argv| {
        Ok(if has(argv, "validate-guest") {
            said(&reply)
        } else {
            Captured::default()
        })
    });
    let (reporter, notes) = prose();
    host.guest_reporting(&channel, &reporter)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap();
    let text = notes.text();
    let wanted = format!("validate-guest reported {validated}");
    assert!(text.contains(&wanted), "{wanted:?} is not in\n{text}");
    // The worker is on the line, because a pool-wide operation notes several boots into one log.
    assert!(text.contains("air-docker-1"), "{text}");
}

// A verb that answered something this controller cannot read still succeeded: a boot is not failed over a progress
// line.
#[tokio::test]
async fn provision_linux_survives_a_verb_that_reported_nothing_readable() {
    let host = provisioned_host();
    let channel = FakeChannel::spoke("air-docker-1", "not an envelope");
    let (reporter, notes) = prose();
    host.guest_reporting(&channel, &reporter)
        .provision_linux(&ForbiddenBazel, &validate_argv())
        .await
        .unwrap();
    assert!(!notes.text().contains("not an envelope"), "{}", notes.text());
}

// A `data: null` is still what the verb answered, so it is noted; only an absent `data` is nothing to note.
#[tokio::test]
async fn provision_linux_notes_a_null_report() {
    for (reply, noted) in [
        (r#"{"schemaVersion":1,"ok":true,"command":"validate-guest","data":null}"#, true),
        (r#"{"schemaVersion":1,"ok":true,"command":"validate-guest"}"#, false),
    ] {
        let host = provisioned_host();
        let channel = FakeChannel::answering("air-docker-1", move |argv| {
            Ok(if has(argv, "validate-guest") {
                said(reply)
            } else {
                Captured::default()
            })
        });
        let (reporter, notes) = prose();
        host.guest_reporting(&channel, &reporter)
            .provision_linux(&ForbiddenBazel, &validate_argv())
            .await
            .unwrap();
        let text = notes.text();
        assert_eq!(text.contains("validate-guest reported null"), noted, "{text}");
    }
}

// --- worker storage --------------------------------------------------------------------------------------------

// The worker's actual disk, not the configured default for a new one: the guest script's safety gate is that `/` is
// a single internal APFS store of exactly the size it was told.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn ensure_worker_storage_sends_the_workers_own_disk_size() {
    let host = Host::new(GuestOs::Macos);
    let data = host.settings.vm_data.clone();
    // Nothing retired is left behind on this worker.
    let channel = FakeChannel::answering("air-macos-1", move |argv| {
        Ok(if has(argv, "/bin/test") && !has(argv, &data) {
            failed(1, "")
        } else {
            Captured::default()
        })
    });
    host.guest(&channel).ensure_worker_storage(400).await.unwrap();
    assert_eq!(
        channel.lines()[0],
        format!(
            "/usr/bin/sudo -H /usr/local/sbin/air-init-worker-storage 400000000000 {} {}",
            host.settings.vm_data, host.settings.vm_user
        )
    );
    assert!(channel.saw("/bin/rmdir").is_none());
}

// `rmdir` and never `rm -r`: a non-empty directory means something does use it, which is a discovery to make loudly
// later rather than a tree to delete now.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn ensure_worker_storage_rmdirs_the_retired_directories() {
    let host = Host::new(GuestOs::Macos);
    let channel = FakeChannel::new("air-macos-1");
    let (reporter, notes) = prose();
    host.guest_reporting(&channel, &reporter).ensure_worker_storage(120).await.unwrap();
    let removed: Vec<String> = channel
        .calls()
        .into_iter()
        .filter(|call| has(&call.argv, "/bin/rmdir"))
        .map(|call| call.argv.last().unwrap().clone())
        .collect();
    let data = &host.settings.vm_data;
    assert_eq!(
        removed,
        [
            format!("{data}/checkout"),
            format!("{data}/bazel-output"),
            format!("{data}/bazel-disk-cache"),
        ]
    );
    assert!(notes.text().contains("removed the retired checkout directory"), "{}", notes.text());
}

// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn ensure_worker_storage_refuses_a_worker_that_never_initialized() {
    let host = Host::new(GuestOs::Macos);
    let channel = FakeChannel::answering("air-macos-1", |argv| {
        Ok(if has(argv, "/bin/test") {
            failed(1, "")
        } else {
            Captured::default()
        })
    });
    let refusal = host.guest(&channel).ensure_worker_storage(120).await.unwrap_err();
    assert_eq!(refusal.code, "worker_storage_missing");
}

// The readiness a run needs: the parity layout first, then the run roots made and handed to the worker - and on a
// Tart macOS worker the storage and the SSH identity before either.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn ensure_ready_checks_the_layout_then_prepares_the_run_roots() {
    let host = Host::new(GuestOs::Linux);
    crate::guest::write_init_receipt(&host.settings, "air-docker-1").unwrap();
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel)
        .ensure_ready(&crate::guest::testing::Peers::default())
        .await
        .unwrap();
    let settings = &host.settings;
    let roots = [settings.vm_data.as_str(), &settings.vm_runs_root].join(" ");
    let lines = channel.lines();
    // A Linux worker is not asked about SSH keys: nothing here reaches it over SSH.
    assert!(!lines.iter().any(|line| line.contains("air-ensure-ssh-host-keys")), "{lines:?}");
    assert_eq!(
        &lines[lines.len() - 2..],
        [
            format!("/usr/bin/sudo -H /bin/mkdir -p {roots}"),
            format!("/usr/bin/sudo -H {} {} {roots}", settings.guest.chown, settings.vm_user),
        ]
    );

    let macos = Host::new(GuestOs::Macos);
    let bare = FakeChannel::answering("air-macos-1", |argv| {
        Ok(if has(argv, "/bin/test") {
            failed(1, "")
        } else {
            Captured::default()
        })
    });
    let refusal = macos
        .guest(&bare)
        .ensure_ready(&crate::guest::testing::Peers::default())
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "worker_storage_missing");
    assert_eq!(bare.calls().len(), 1, "{:?}", bare.lines());
}

// The run roots of the testing-ui container are made by the account that uses them, so nothing is handed over: the
// `mkdir` is the last step, and no sudo and no chown reach the guest.
// A Windows host does not drive the testing-ui container.
#[cfg(unix)]
#[tokio::test]
async fn ensure_ready_on_an_unprivileged_guest_makes_the_roots_without_a_chown() {
    let host = Host::container_linux();
    crate::guest::write_init_receipt(&host.settings, "container-linux-1").unwrap();
    let channel = FakeChannel::new("container-linux-1");
    host.guest(&channel)
        .ensure_ready(&crate::guest::testing::Peers::default())
        .await
        .unwrap();
    let settings = &host.settings;
    let roots = [settings.vm_data.as_str(), &settings.vm_runs_root].join(" ");
    let lines = channel.lines();
    assert_eq!(lines.last(), Some(&format!("/bin/mkdir -p {roots}")));
    assert!(
        !lines
            .iter()
            .any(|line| line.contains(settings.guest.chown) || line.contains("sudo")),
        "{lines:?}"
    );
}
