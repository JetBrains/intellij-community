//! A fake `prlctl` stands in for both the hypervisor and its guest, so a share reconfiguration, a power cycle and a
//! suspend are all observable without a VM. `prlctl exec` takes a single shell string, and the fake matches inside
//! it - which only works because the controller really did collapse the command into one string.

use avl_base::format::words;
use avl_base::phase::Timeline;
use avl_base::{Environment, FakeClock, GuestOs, Selection, posix_shell_quote};
use avl_host_sys::share;
use avl_host_testkit::path_runner;
use avl_testkit::tartfake::{Answer, Binary, Fake};
use pretty_assertions::assert_eq;

use super::*;

const WORKER: &str = "macOS";

/// Bounds every wait loop on the fixture's fake clock, which moves only when a loop sleeps: a test whose subject is
/// a decision reaches it long before the budget, however slowly the fake `prlctl` spawns in a loaded lane.
const BOOT_BUDGET_SECONDS: &str = "120";

/// For a test whose subject is the refusal itself: such a wait probes until the budget is spent, so it stays short.
const REFUSAL_BUDGET_SECONDS: &str = "1";

struct Fixture {
    settings: Arc<Config>,
    parallels: Parallels,
    fake: Fake,
    host_bazel: PathBuf,
    _root: tempfile::TempDir,
}

impl Fixture {
    fn new() -> Self {
        Self::with_boot_budget(BOOT_BUDGET_SECONDS)
    }

    fn with_boot_budget(boot_timeout: &str) -> Self {
        let fake = Fake::install_binary(Binary::Parallels, "prlctl version 20.0.0\n");
        let root = tempfile::tempdir().unwrap();
        let home = root.path().to_string_lossy().into_owned();
        let executable = fake.executable().to_string_lossy().into_owned();
        let runtime = root.path().join("runtime").to_string_lossy().into_owned();
        let environment = Environment::from_pairs([
            ("HOME", home.as_str()),
            ("AIR_VM_PARALLELS_BIN", &executable),
            ("AIR_VM_PARALLELS_VM", WORKER),
            ("AIR_VM_RUNTIME_ROOT", &runtime),
            ("AIR_VM_BOOT_TIMEOUT", boot_timeout),
        ]);
        let selection = Selection {
            backend: Backend::Parallels,
            guest_os: GuestOs::Macos,
        };
        let settings = Config::load(
            avl_base::HostFacts::without_memory(),
            selection,
            &environment,
            &root.path().join("scripts"),
        )
        .unwrap();
        let host_repo = root.path().join("idea");
        let host_bazel = root.path().join("bazel");
        for path in [&host_repo, &host_bazel] {
            std::fs::create_dir_all(path).unwrap();
        }
        settings.set_host_paths(&host_repo, &host_bazel).unwrap();
        let settings = Arc::new(settings);
        // The fake `prlctl` is call-driven, so a wait decides on calls, not on time: a fake clock that moves only when
        // a loop sleeps pays the budget without a wall wait.
        let parallels = Parallels::new(settings.clone(), path_runner()).with_clock(Arc::new(FakeClock::at("2026-09-28T12:00:00Z")));
        fake.answer(Answer::Status, "VM \"macOS\" exist running\n");
        fake.answer(Answer::Who, "test console  Aug 19 10:00\n");
        fake.answer(Answer::Autologin, format!("{}\n", settings.vm_user));
        Self {
            settings,
            parallels,
            fake,
            host_bazel,
            _root: root,
        }
    }

    fn shares(&self) -> [SharedFolder; 1] {
        share::shares(&self.settings).unwrap()
    }

    /// The share object of a VM whose shares are already right.
    fn matching_folders(&self) -> String {
        serde_json::json!({
            "enabled": true,
            self.settings.bazel_share_name.clone(): {"enabled": true, "path": self.host_bazel, "mode": "ro"},
        })
        .to_string()
    }

    /// Declares the fixture's shares, answering whether that power-cycled the VM.
    async fn declare(&self) -> Result<bool, Refusal> {
        self.parallels.declare_shares(&Ctx::background(), WORKER, &self.shares()).await
    }

    async fn suspend(&self, start_if_stopped: bool) -> Result<(), Refusal> {
        self.parallels.suspend(&Ctx::background(), WORKER, start_if_stopped).await
    }
}

/// One `prlctl list -j` answer. `folders` is the raw shared-folder object, so a test states exactly what the
/// hypervisor reports, including the shapes the controller has to refuse.
fn listing(state: &str, folders: &str) -> String {
    format!(
        r#"[{{"ID":"{{uuid}}","Name":"macOS","Type":"APPLE_VZ_VM","State":"{state}","OS":"macosx",
          "Host Shared Folders":{folders}}}]"#
    )
}

// --- the gate ------------------------------------------------------------------------------------------------

// A Parallels worker is a VM the operator made, so the gate is about the VM and not only about the binary.
#[tokio::test]
async fn the_gate_checks_the_vm_and_not_just_the_binary() {
    let ctx = Ctx::background();
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    fixture.parallels.require_available(&ctx, WORKER).await.unwrap();

    fixture.fake.answer(Answer::VersionExit, "127");
    let refusal = fixture.parallels.require_available(&ctx, WORKER).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("parallels_missing", Exit::UNAVAILABLE));
    fixture.fake.answer(Answer::VersionExit, "0");

    // Every macOS-guest path here assumes Apple Virtualization, so another VM type is refused at the gate rather
    // than downstream.
    for other in [
        r#"[{"Name":"macOS","Type":"PARALLELS_VM","State":"running","OS":"macosx"}]"#,
        r#"[{"Name":"macOS","Type":"APPLE_VZ_VM","State":"running","OS":"linux"}]"#,
    ] {
        fixture.fake.answer(Answer::ListJson, other);
        let refusal = fixture.parallels.require_available(&ctx, WORKER).await.unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("parallels_vm_incompatible", Exit::DATA_ERR));
    }
}

// An unregistered VM is something the operator can fix; output this controller cannot read is not.
#[tokio::test]
async fn one_vms_worth_of_json_or_nothing() {
    let ctx = Ctx::background();
    let fixture = Fixture::new();
    fixture.fake.answer(Answer::ListExit, "1");
    let refusal = fixture.parallels.info(&ctx, WORKER).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("parallels_vm_missing", Exit::UNAVAILABLE));
    fixture.fake.answer(Answer::ListExit, "0");

    for unreadable in ["not json", "[]", "{}", r#"[{"Name":"a"},{"Name":"b"}]"#] {
        fixture.fake.answer(Answer::ListJson, unreadable);
        let refusal = fixture.parallels.info(&ctx, WORKER).await.unwrap_err();
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("parallels_protocol", Exit::SOFTWARE),
            "{unreadable}"
        );
    }
    // A state this controller does not act on is read, not refused.
    fixture.fake.answer(Answer::ListJson, r#"[{"Name":"macOS","State":"paused"}]"#);
    let info = fixture.parallels.info(&ctx, WORKER).await.unwrap();
    // It keeps prlctl's own word, which `vm status` prints.
    assert_eq!(info.state, Some(VmState::Other("paused".to_owned())));
    assert_eq!(info.state.unwrap().to_string(), "paused");
}

// --- liveness ------------------------------------------------------------------------------------------------

#[tokio::test]
async fn running_is_what_prlctl_status_says() {
    let ctx = Ctx::background();
    let fixture = Fixture::new();
    assert!(fixture.parallels.running(&ctx, WORKER).await.unwrap());
    // A word, not a substring: `notrunning` is not a verdict.
    for status in ["VM \"macOS\" exist notrunning\n", "VM \"macOS\" exist suspended\n"] {
        fixture.fake.answer(Answer::Status, status);
        assert!(!fixture.parallels.running(&ctx, WORKER).await.unwrap(), "{status}");
    }
    // A failing `prlctl status` is not a running VM whatever it printed.
    fixture.fake.answer(Answer::Status, "running\n");
    fixture.fake.answer(Answer::StatusExit, "1");
    assert!(!fixture.parallels.running(&ctx, WORKER).await.unwrap());
}

/// A `prlctl` without an answer is no state: a `status` or a `list` that a signal ended or that printed nothing, and a
/// `--version` that a signal ended. Exit 137 is not a VM that is not running, not registered, or not installed.
#[tokio::test]
async fn a_prlctl_without_an_answer_is_refused_and_not_a_state() {
    let ctx = Ctx::background();
    let fixture = Fixture::new();
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fixture.fake.answer(answer, "status");
        let refusal = fixture.parallels.running(&ctx, WORKER).await.unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
        assert!(refusal.message.contains(&format!("exited with {code}")), "{}", refusal.message);
        fixture.fake.forget(answer);
    }
    assert!(fixture.parallels.running(&ctx, WORKER).await.unwrap());

    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    fixture.fake.answer(Answer::KilledVerb, "list");
    let refusal = fixture.parallels.info(&ctx, WORKER).await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    fixture.fake.forget(Answer::KilledVerb);
    // An empty listing is an answer this controller does not speak, which was a refusal before.
    fixture.fake.answer(Answer::SilentVerb, "list");
    assert_eq!(fixture.parallels.info(&ctx, WORKER).await.unwrap_err().code, "parallels_protocol");
    fixture.fake.forget(Answer::SilentVerb);

    fixture.fake.answer(Answer::KilledVerb, "--version");
    let refusal = fixture.parallels.require_available(&ctx, WORKER).await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    fixture.fake.forget(Answer::KilledVerb);
    // `--version` answers by its exit, which is presence.
    fixture.fake.answer(Answer::SilentVerb, "--version");
    fixture.parallels.require_available(&ctx, WORKER).await.unwrap();
}

// --- shares --------------------------------------------------------------------------------------------------

// Every one of these conditions has been wrong on a real worker, and each one leaves the share looking present in
// a listing.
#[test]
fn a_shared_folder_matches_only_when_every_part_of_it_does() {
    let share = SharedFolder {
        name: "air-macos-repo".to_owned(),
        path: PathBuf::from("/Users/x/idea"),
        mode: "ro",
    };
    let entry = |enabled: Option<bool>, path: Option<&str>, mode: Option<&str>| SharedFolderEntry {
        enabled,
        path: path.map(str::to_owned),
        mode: mode.map(str::to_owned),
    };
    let cases = [
        ("present but not configured", entry(None, None, None), false),
        ("disabled", entry(Some(false), Some("/Users/x/idea"), Some("ro")), false),
        ("read-write", entry(Some(true), Some("/Users/x/idea"), Some("rw")), false),
        ("no path", entry(Some(true), Some(""), Some("ro")), false),
        ("another directory", entry(Some(true), Some("/Users/x/other"), Some("ro")), false),
        ("a match", entry(Some(true), Some("/Users/x/idea"), Some("ro")), true),
        // `prlctl` reports back whatever it was given, and a different spelling of the right directory is still
        // the right directory.
        (
            "an unresolved spelling",
            entry(Some(true), Some("/Users/x/../x/idea"), Some("ro")),
            true,
        ),
    ];
    assert!(!shared_folder_matches(None, &share), "an absent share matched");
    for (name, existing, want) in cases {
        assert_eq!(shared_folder_matches(Some(&existing), &share), want, "{name}");
    }
}

// A share set that is already right must not cost a power cycle: the cycle costs the Aqua session, and on this
// backend that can mean a human at a login window.
#[tokio::test]
async fn shares_that_already_match_are_left_alone() {
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    assert!(!fixture.declare().await.unwrap(), "a matching share set power-cycled the VM");
    for forbidden in ["set", "stop", "start"] {
        assert!(
            !fixture.fake.saw_call_containing(forbidden),
            "a matching share set still ran {forbidden}: {:?}",
            fixture.fake.calls()
        );
    }
}

// The order is the subtlety: `prlctl` accepts a share change on a running VM, but the guest's VirtioFS device
// attaches at boot, so mutating first would leave a VM running with a configuration its guest cannot see.
#[tokio::test]
async fn a_share_change_stops_the_vm_before_it_mutates_anything() {
    let fixture = Fixture::new();
    let drifted = serde_json::json!({
        "enabled": true,
        fixture.settings.bazel_share_name.clone(): {"enabled": true, "path": "/Users/x/somewhere-else", "mode": "ro"},
    })
    .to_string();
    fixture.fake.answer(Answer::ListJson, listing("running", &drifted));
    fixture.fake.answer(Answer::StateStopped, listing("stopped", &drifted));
    fixture
        .fake
        .answer(Answer::StateRunning, listing("running", &fixture.matching_folders()));

    assert!(fixture.declare().await.unwrap(), "a share change did not report a power cycle");

    let calls = fixture.fake.calls();
    let position = |fragment: &str| {
        calls
            .iter()
            .position(|call| call.contains(fragment))
            .unwrap_or_else(|| panic!("no call contains {fragment:?}: {calls:?}"))
    };
    let (stop, set, start) = (position("stop macOS"), position("--shf-host-set"), position("start macOS"));
    assert!(stop < set && set < start, "the VM was mutated outside the cycle: {calls:?}");
    // `--shf-host-set` for the drifted share, because it exists; `prlctl` refuses an add for a name it has.
    position(&format!("--shf-host-set {}", fixture.settings.bazel_share_name));
    assert!(!calls.iter().any(|call| call.contains("--shf-host-add")), "{calls:?}");
    // The master switch is applied unconditionally: a correct share set with sharing off looks right in a listing.
    position("--shf-host on --shf-host-automount on");
}

// Checked *before* the stop: the alternative is discarding a working Aqua session for one nobody can restore
// unattended.
#[tokio::test]
async fn a_power_cycle_is_refused_on_a_guest_that_cannot_come_back() {
    let fixture = Fixture::new();
    fixture.fake.answer(Answer::ListJson, listing("running", r#"{"enabled":true}"#));

    fixture.fake.answer(Answer::Autologin, "somebody-else\n");
    let refusal = fixture.declare().await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("parallels_reboot_needs_autologin", Exit::DATA_ERR)
    );
    assert!(!fixture.fake.saw_call_containing("stop macOS"), "the VM was stopped anyway");

    // Both halves are required: a guest with `autoLoginUser` and no `/etc/kcpassword` stops at the login window
    // exactly as if neither were set.
    fixture.fake.answer(Answer::Autologin, format!("{}\n", fixture.settings.vm_user));
    fixture.fake.answer(Answer::KcpasswordExit, "1");
    let refusal = fixture.declare().await.unwrap_err();
    assert_eq!(refusal.code, "parallels_reboot_needs_autologin");
}

// A guest read that gave no answer says nothing about the login, so the power cycle is refused as unanswered, the VM
// is not stopped, and the guest output stays out of the refusal.
#[tokio::test]
async fn a_power_cycle_check_without_an_answer_is_refused_and_stops_nothing() {
    let fixture = Fixture::new();
    fixture.fake.answer(Answer::ListJson, listing("running", r#"{"enabled":true}"#));
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fixture.fake.answer(answer, "exec");
        let refusal = fixture.declare().await.unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
        assert!(
            refusal.message.contains(&format!("exited with {code} and its output is withheld")),
            "{}",
            refusal.message
        );
        assert!(!fixture.fake.saw_call_containing("stop macOS"), "the VM was stopped anyway");
        fixture.fake.forget(answer);
    }
}

// The read-only counterpart, for the paths that must report drift rather than repair it.
#[tokio::test]
async fn a_shares_check_reports_drift_without_fixing_it() {
    let ctx = Ctx::background();
    let fixture = Fixture::new();
    fixture.fake.answer(Answer::ListJson, listing("running", r#"{"enabled":true}"#));
    let info = fixture.parallels.info(&ctx, WORKER).await.unwrap();
    let refusal = fixture
        .parallels
        .require_shares_configured(WORKER, &info, &fixture.shares())
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("parallels_share_mismatch", Exit::DATA_ERR));
    // The message names the repair, because it is automatic and that is the caller's next question.
    assert!(
        refusal.message.contains("provisioning reconfigures it automatically"),
        "{}",
        refusal.message
    );

    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    let info = fixture.parallels.info(&ctx, WORKER).await.unwrap();
    fixture
        .parallels
        .require_shares_configured(WORKER, &info, &fixture.shares())
        .unwrap();
}

// `prlctl` mixes the master switch in with the shares themselves, and a `null` entry is not a share.
#[tokio::test]
async fn the_shared_folder_object_separates_the_switch_from_the_shares() {
    let fixture = Fixture::new();
    fixture.fake.answer(
        Answer::ListJson,
        listing(
            "running",
            r#"{"enabled":true,"air-macos-repo":null,"air-macos-bazel":{"enabled":true,"path":"/x","mode":"ro"},"odd":7}"#,
        ),
    );
    let info = fixture.parallels.info(&Ctx::background(), WORKER).await.unwrap();
    assert!(info.automount_enabled(), "the master switch was read as off");
    assert_eq!(info.shared_folder("enabled"), None, "the switch was recorded as a share");
    // A null entry means no such share, which is the branch that decides add-versus-set.
    assert_eq!(info.shared_folder("air-macos-repo"), None);
    assert_eq!(
        info.shared_folder("air-macos-bazel"),
        Some(&SharedFolderEntry {
            enabled: Some(true),
            path: Some("/x".to_owned()),
            mode: Some("ro".to_owned()),
        })
    );
    // An entry that is not an object is still a share, with nothing known about it: it is re-set, not added.
    assert_eq!(info.shared_folder("odd"), Some(&SharedFolderEntry::default()));

    // An absent object answers "off" and "no shares", which is what a VM with sharing never configured reports.
    let never_configured = VmInfo::default();
    assert!(!never_configured.automount_enabled());
    assert_eq!(never_configured.shared_folder("air-macos-repo"), None);
}

// --- suspend -------------------------------------------------------------------------------------------------

#[tokio::test]
async fn suspend_is_already_done_when_the_vm_holds_its_state() {
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("suspended", &fixture.matching_folders()));
    fixture.suspend(false).await.unwrap();
    assert!(!fixture.fake.saw_call_containing("suspend macOS"), "{:?}", fixture.fake.calls());
}

#[tokio::test]
async fn suspending_a_running_vm_waits_for_the_state_to_land() {
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    fixture
        .fake
        .answer(Answer::StateSuspended, listing("suspended", &fixture.matching_folders()));
    fixture.suspend(false).await.unwrap();
    assert!(fixture.fake.saw_call_containing("suspend macOS"), "{:?}", fixture.fake.calls());
}

// A stopped VM has no state to save, so booting it to suspend it is the caller's decision: `pool init` hands a VM
// back as it found it and passes true, `pool stop` passes false because stopped is already the answer.
#[tokio::test]
async fn a_stopped_vm_is_started_to_be_suspended_only_when_asked() {
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("stopped", &fixture.matching_folders()));
    assert_eq!(fixture.suspend(false).await.unwrap_err().code, "suspend_failed");
    assert!(!fixture.fake.saw_call_containing("start macOS"), "started without being asked");

    fixture.fake.forget_calls();
    fixture
        .fake
        .answer(Answer::ListJson, listing("stopped", &fixture.matching_folders()));
    fixture
        .fake
        .answer(Answer::StateRunning, listing("running", &fixture.matching_folders()));
    fixture
        .fake
        .answer(Answer::StateSuspended, listing("suspended", &fixture.matching_folders()));
    fixture.suspend(true).await.unwrap();
    assert!(fixture.fake.saw_call_containing("start macOS"), "{:?}", fixture.fake.calls());
    assert!(fixture.fake.saw_call_containing("suspend macOS"), "{:?}", fixture.fake.calls());

    // A VM that keeps returning to stopped is broken, and trying the start once is what stops the loop from
    // spending the whole boot budget discovering that. Nothing suspends it in this case.
    fixture.fake.forget_calls();
    fixture.fake.forget(Answer::StateSuspended);
    fixture
        .fake
        .answer(Answer::ListJson, listing("stopped", &fixture.matching_folders()));
    fixture
        .fake
        .answer(Answer::StateRunning, listing("stopped", &fixture.matching_folders()));
    assert_eq!(fixture.suspend(true).await.unwrap_err().code, "suspend_failed");
}

// --- waiting -------------------------------------------------------------------------------------------------

#[tokio::test]
async fn the_waits_refuse_with_their_own_codes() {
    let ctx = Ctx::background();
    let fixture = Fixture::with_boot_budget(REFUSAL_BUDGET_SECONDS);
    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));

    // A guest that never answers is a boot timeout...
    fixture.fake.answer(Answer::ExecExit, "1");
    let refusal = fixture.parallels.wait_for_guest_execution(&ctx, WORKER).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("boot_timeout", Exit::FAILURE));

    // ...and a guest that answers but has no seat is a different thing entirely, which only a human can fix.
    fixture.fake.answer(Answer::ExecExit, "0");
    fixture.fake.answer(Answer::Who, "test ttys001  Aug 19 10:00\n");
    let refusal = fixture.parallels.wait_for_console_login(&ctx, WORKER).await.unwrap_err();
    assert_eq!(refusal.code, "console_login_required");
    assert!(refusal.message.contains("automatic login"), "{}", refusal.message);
    // An ssh session is not a seat; the console is.
    fixture
        .fake
        .answer(Answer::Who, "test ttys001  Aug 19 10:00\r\ntest console  Aug 19 10:00\r\n");
    fixture.parallels.wait_for_console_login(&ctx, WORKER).await.unwrap();

    let refusal = fixture
        .parallels
        .wait_for_state(&ctx, WORKER, VmState::Suspended)
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "parallels_state_timeout");
}

// A cancelled operation stops a wait loop rather than spending the rest of its budget, which is what makes SIGINT
// during a boot a cooperative abort.
#[tokio::test]
async fn a_cancelled_operation_ends_a_wait() {
    let fixture = Fixture::new();
    fixture
        .fake
        .answer(Answer::ListJson, listing("running", &fixture.matching_folders()));
    fixture.fake.answer(Answer::ExecExit, "1");
    let ctx = Ctx::background();
    ctx.token().cancel();
    let started = std::time::Instant::now();
    let refusal = fixture.parallels.wait_for_guest_execution(&ctx, WORKER).await.unwrap_err();
    assert_eq!(refusal.code, "boot_timeout");
    assert!(started.elapsed() < Duration::from_secs(5), "the wait spent its budget");
}

// --- the prlctl guest line ----------------------------------------------------------------------------------

// The one difference every guest call site used to know about: Parallels takes a single shell string it re-parses,
// where Tart takes an argv.
#[tokio::test]
async fn the_parallels_guest_line_is_one_quoted_shell_string() {
    let fixture = Fixture::new();
    let line = fixture
        .parallels
        .guest_argv(&Ctx::background(), WORKER, &words(["/bin/echo", "it's one word"]));
    let executable = fixture.fake.executable().to_string_lossy().into_owned();
    assert_eq!(
        line,
        [
            executable,
            "exec".to_owned(),
            WORKER.to_owned(),
            r#"'/bin/echo' 'it'"'"'s one word'"#.to_owned(),
        ]
    );
}

/// Settings for one backend over a fixed home, with `tart` and `prlctl` as the binaries.
fn settings_for(backend: Backend) -> Config {
    let environment = Environment::from_pairs([("HOME", "/Users/air"), ("TART_BIN", "tart"), ("AIR_VM_PARALLELS_BIN", "prlctl")]);
    Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend,
            guest_os: GuestOs::Macos,
        },
        &environment,
        Path::new("/repo/scripts"),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

// The argv head is declared where the line is built, so phase timing classifies a spawn without any caller opting
// in.
#[test]
fn building_a_guest_line_declares_its_argv_head() {
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let parallels = Parallels::new(Arc::new(settings_for(Backend::Parallels)), path_runner());
    let line = parallels.guest_argv(&ctx, WORKER, &words(["/bin/sh", "-c", "echo hi"]));
    assert_eq!(line, words(["prlctl", "exec", WORKER, "'/bin/sh' '-c' 'echo hi'"]));
    let (inner, phase) = ctx.begin("state-prep");
    inner.phase().record_subprocess(&["prlctl", "exec"], Duration::from_secs(1));
    inner.phase().record_subprocess(&["git", "rev-parse"], Duration::from_secs(1));
    drop(phase);
    let timing = &timeline.take()[0];
    assert_eq!((timing.guest_calls, timing.host_calls), (1, 1), "{timing:?}");
}

// Both spellings are valid POSIX and they are not interchangeable in practice, so they stay two functions. Each is
// proved by the shell it is written for.
#[tokio::test]
async fn the_two_quotings_are_both_correct_and_distinct() {
    assert_eq!(quote_parallels_arg("it's"), r#"'it'"'"'s'"#);
    assert_ne!(quote_parallels_arg("it's"), posix_shell_quote("it's"));
    // A value with no quote in it is simply wrapped by both, which is what makes the difference invisible until it
    // matters.
    assert_eq!(quote_parallels_arg("plain"), "'plain'");
    assert_eq!(posix_shell_quote("plain"), "'plain'");

    let value = "it's \"one\" $word `and` \\ more";
    for quoted in [quote_parallels_arg(value), posix_shell_quote(value)] {
        let script = format!("printf %s {quoted}");
        let echoed = path_runner()
            .capture(
                &Ctx::background(),
                &words(["/bin/sh", "-c", &script]),
                &SpawnOptions::within(Duration::from_mins(1)),
            )
            .await
            .unwrap();
        assert_eq!(echoed.stdout, value, "{quoted} did not round-trip through sh");
    }
}

#[test]
fn peekaboo_is_refused_off_parallels() {
    let refusal = parallels_current_user_argv(&settings_for(Backend::Tart), "air-macos-1", &words(["peekaboo"]), false, None).unwrap_err();
    assert_eq!(refusal.code, "unsupported_backend_operation");
    assert_eq!(refusal.exit, Exit::USAGE);

    let line = parallels_current_user_argv(
        &settings_for(Backend::Parallels),
        WORKER,
        &words(["peekaboo", "see"]),
        true,
        Some("/tmp/x"),
    )
    .unwrap();
    assert_eq!(
        line,
        words([
            "prlctl",
            "exec",
            WORKER,
            "--current-user",
            "--use-advanced-terminal",
            "cd '/tmp/x' && exec 'peekaboo' 'see'",
        ])
    );
}

// A hypervisor reports a share's path in whatever form it was given, so both sides are made absolute and cleaned
// before they are compared.
#[test]
fn resolved_path_is_absolute_and_clean() {
    let working = std::env::current_dir().unwrap();
    let absolute = Path::new("/Users/x/idea");
    assert_eq!(resolved_path(absolute), absolute);
    assert_eq!(resolved_path(Path::new("idea")), working.join("idea"));
    // `..` and `.` are collapsed, so two spellings of one directory compare equal.
    assert_eq!(resolved_path(Path::new("/Users/x/../x/./idea")), absolute);
    assert_eq!(resolved_path(Path::new("/../Users/x/idea")), absolute);
}
