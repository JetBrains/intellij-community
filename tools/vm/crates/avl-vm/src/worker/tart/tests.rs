//! The Tart backend's suite. Hermetic: no VM, no network, and the only processes it starts are `/bin/sh`, `/bin/ps`
//! against its own pid, and the fake `tart` the fixture installs.
//!
//! The fake stays a real process: the argv shape is what has to be checked against something that re-reads it, and
//! a `tart` too old or too broken to say what it is, is what the version gate exists for.

use std::fs::FileTimes;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use avl_base::format::words;
use avl_base::{Exit, GuestOs, SCHEMA_VERSION};
use avl_host_sys::Ctx;
use avl_host_sys::share::{SHARE_MODE, SharedFolder};
use avl_host_testkit::path_runner;
use avl_testkit::tartfake::Answer;
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::hypervisor::is_unsupported;
use crate::worker::testing::Fixture;

const BASE_DIGEST: &str = "sha256:fa96c198dd980ae41914db6dd8621e6120d01ba0cd66754dcb4238e072a87444";
const MACOS_VERSION: &str = "26.6.2";
const GOLDEN: &str = "air-macos-26.6.2-golden";

fn tart(fixture: &Fixture) -> &Tart {
    fixture.manager.tart().expect("a Tart pool")
}

fn ctx() -> Ctx {
    Ctx::background()
}

/// Puts one file of a VM in place and answers its path.
fn write_vm_file(fixture: &Fixture, vm: &str, name: &str, content: &str) -> PathBuf {
    let directory = fixture.settings.tart_home.join("vms").join(vm);
    std::fs::create_dir_all(&directory).unwrap();
    let path = directory.join(name);
    std::fs::write(&path, content).unwrap();
    path
}

fn write_versions_env(fixture: &Fixture, content: &str) {
    std::fs::write(fixture.settings.image_root.join("versions.env"), content).unwrap();
}

fn write_private(path: &Path, content: &[u8]) {
    std::fs::write(path, content).unwrap();
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600)).unwrap();
}

/// Writes a golden's three clone inputs, the build's pins, and a matching mode-0600 seal - in the shape `lib.sh
/// write_golden_seal` writes it, which is `jq -n`'s pretty-printed object.
fn seal_golden(fixture: &Fixture) -> SealedGolden {
    write_vm_file(fixture, GOLDEN, "disk.img", "sealed test golden\n");
    write_vm_file(fixture, GOLDEN, "config.json", "{}\n");
    write_vm_file(fixture, GOLDEN, "nvram.bin", "test nvram\n");
    write_versions_env(
        fixture,
        &format!("# the image build's own pins\nGOLDEN_SEAL_SCHEMA=1\nCIRRUS_BASE_DIGEST={BASE_DIGEST}\nMACOS_VERSION={MACOS_VERSION}\n"),
    );
    let signature = |input| tart(fixture).vm_file_signature(GOLDEN, input).unwrap();
    let seal = SealedGolden {
        schema_version: SCHEMA_VERSION,
        golden: GOLDEN.to_owned(),
        disk_signature: signature(CloneInput::Disk),
        config_signature: signature(CloneInput::Config),
        nvram_signature: signature(CloneInput::Nvram),
        base_digest: BASE_DIGEST.to_owned(),
        macos_version: MACOS_VERSION.to_owned(),
        audited_at: "2026-08-09T00:00:00Z".to_owned(),
    };
    let mut encoded = serde_json::to_vec_pretty(&seal).unwrap();
    encoded.push(b'\n');
    write_private(&tart(fixture).seal_path(GOLDEN), &encoded);
    seal
}

// --- the version gate ------------------------------------------------------------------------------------------

/// An exact-minor pin is how a routine `brew upgrade` to 2.35 turned every Tart command into `unsupported_tart` on a
/// host where nothing was wrong. What the controller needs is a floor.
#[test]
fn the_version_gate_is_a_floor_with_an_escape_hatch() {
    let minimum = parse_version(MINIMUM_VERSION).unwrap();
    assert_eq!(minimum, [2, 32, 1]);
    for (output, want) in [
        ("2.35.0", [2, 35, 0]),
        // A missing patch component is zero, because Tart has shipped both spellings.
        ("v3.0", [3, 0, 0]),
        // The version is the first version-shaped token, whatever else the line carries.
        ("tart 2.32.1", [2, 32, 1]),
        ("2.32.1 (build 1234)", [2, 32, 1]),
    ] {
        assert_eq!(parse_version(output), Some(want), "{output}");
    }
    assert_eq!(parse_version("not a version"), None);
    // No left anchor would read `2.3` out of a build hash.
    assert_eq!(parse_version("abc2.3def"), None);

    for newer in ["2.32.1", "2.32.2", "2.33.0", "2.35.0", "3.0.0", "10.0.0"] {
        assert!(parse_version(newer).unwrap() >= minimum, "{newer}");
    }
    for older in ["2.32.0", "2.31.9", "1.99.99", "0.1.0"] {
        assert!(parse_version(older).unwrap() < minimum, "{older}");
    }
}

#[tokio::test]
async fn the_version_gate_refuses_an_old_tart_and_names_the_way_past() {
    let fixture = Fixture::macos();
    fixture.fake.answer(Answer::Version, "2.31.0\n");
    let refusal = tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("unsupported_tart", Exit::UNAVAILABLE));
    // The escape hatch belongs in the refusal itself: whoever hits the gate is bisecting a Tart regression and needs
    // the way past it in the same sentence.
    assert!(refusal.message.contains(TART_VERSION_OVERRIDE_VARIABLE), "{}", refusal.message);

    fixture.fake.answer(Answer::Version, "2.35.0\n");
    tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap();
    // The layers above run their own Tart commands with the executable the gate settled on.
    assert_eq!(fixture.manager.tart_executable(), Some(fixture.fake.executable()));

    // An absent binary is `tart_missing`, which is a different problem from a version.
    fixture.fake.answer(Answer::VersionExit, "127");
    let refusal = tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap_err();
    assert_eq!(refusal.code, "tart_missing");
}

// A `tart --version` that a signal ended is no missing binary, and one that printed nothing is no version.
#[tokio::test]
async fn a_version_without_an_answer_is_refused_and_not_a_missing_tart() {
    let fixture = Fixture::macos();
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fixture.fake.answer(answer, "--version");
        let refusal = tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
        assert!(refusal.message.contains(&format!("exited with {code}")), "{}", refusal.message);
        fixture.fake.forget(answer);
    }
    tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap();
}

#[tokio::test]
async fn the_version_override_skips_the_floor_entirely() {
    let fixture = Fixture::builder(GuestOs::Macos).env(TART_VERSION_OVERRIDE_VARIABLE, "1").build();
    fixture.fake.answer(Answer::Version, "1.0.0\n");
    tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap();
    // It does not skip *presence*: an absent binary is still absent.
    fixture.fake.answer(Answer::VersionExit, "127");
    let refusal = tart(&fixture).require_available(&ctx(), "air-macos-1").await.unwrap_err();
    assert_eq!(refusal.code, "tart_missing");
}

// --- shares --------------------------------------------------------------------------------------------------

/// The fence. A `tag=` option moves the share onto its own virtio-fs device, while the controller's remount mounts
/// only Apple's automount tag - so a tagged share is invisible inside the guest, which is invisible on the host and
/// surfaces only as an unmountable share.
#[test]
fn a_share_is_declared_by_name_never_by_tag() {
    let share = |path: &str| SharedFolder {
        name: "air-macos-repo".to_owned(),
        path: PathBuf::from(path),
        mode: SHARE_MODE,
    };
    let argument = share_argument(&share("/Users/x/idea")).unwrap();
    assert_eq!(argument, "--dir=air-macos-repo:/Users/x/idea:ro");
    assert!(!argument.contains("tag="));

    // An empty path means the caller spawned `tart run` before the host paths were resolved.
    let refusal = share_argument(&share("")).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("unsafe_share_path", Exit::DATA_ERR));
    // ':' is Tart's own field separator, so such a path would silently re-split into a different share.
    let refusal = share_argument(&share("/Users/x/a:b")).unwrap_err();
    assert_eq!(refusal.code, "unsafe_share_path");
    assert!(refusal.message.contains("':'"), "{}", refusal.message);
}

/// `tart exec` takes an argv, and a tty is asked for with `-i`: the one difference every guest call site used to
/// know about.
#[tokio::test]
async fn the_guest_command_line_is_tart_exec() {
    let fixture = Fixture::macos();
    let line = tart(&fixture)
        .guest_argv(&ctx(), "air-macos-1", &words(["/bin/echo", "one two"]), true)
        .await
        .unwrap();
    let program = fixture.fake.executable().to_string_lossy().into_owned();
    assert_eq!(line, words([&program, "exec", "-i", "air-macos-1", "/bin/echo", "one two"]));
}

// --- clone-input signatures ----------------------------------------------------------------------------------

/// The trap. `lib.sh` records `stat -f '%i:%z:%m:%c:%B'`, which is the seconds field itself, so a signature
/// *truncates* - a file modified at .987 s records the second below, not the one above. Rounding here instead would
/// move roughly half of all signatures by one second and make every seal the image pipeline wrote read as stale.
#[test]
fn a_clone_input_signature_truncates_rather_than_rounds() {
    let fixture = Fixture::macos();
    let path = write_vm_file(&fixture, GOLDEN, "disk.img", "sealed test golden\n");
    let modified = UNIX_EPOCH + Duration::new(1_755_000_000, 987_654_321);
    std::fs::File::options()
        .write(true)
        .open(&path)
        .unwrap()
        .set_times(FileTimes::new().set_modified(modified).set_accessed(modified))
        .unwrap();

    let signature = tart(&fixture).vm_file_signature(GOLDEN, CloneInput::Disk).unwrap();
    let fields: Vec<&str> = signature.split(':').collect();
    assert_eq!(fields.len(), 5, "{signature}");
    assert_eq!(fields[2], "1755000000", "rounding would give 1755000001");
    let metadata = tart(&fixture).vm_file_metadata(GOLDEN, CloneInput::Disk).unwrap();
    assert_eq!(fields[0], metadata.inode.to_string());
    assert_eq!(fields[1], "19");
    // Birth time is the fifth field, and only macOS records one: dropping it would invalidate every seal on disk.
    if cfg!(target_os = "macos") {
        assert_ne!(fields[4], "0", "no birth time was recorded: {signature}");
        // And the whole signature is the image pipeline's own, byte for byte.
        let stat = std::process::Command::new("/usr/bin/stat")
            .args(["-f", "%i:%z:%m:%c:%B"])
            .arg(&path)
            .output()
            .unwrap();
        assert_eq!(String::from_utf8_lossy(&stat.stdout).trim(), signature);
    } else {
        assert_eq!(fields[4], "0");
    }
}

/// A worker's provenance is written once and read on every boot, so it may only record what does not move while the
/// same clone exists.
#[test]
fn a_worker_disk_identity_ignores_the_times_that_move_when_the_guest_writes() {
    let fixture = Fixture::macos();
    let path = write_vm_file(&fixture, "air-macos-1", "disk.img", "test disk\n");
    let before = tart(&fixture).stable_worker_disk_identity("air-macos-1").unwrap();
    assert_eq!(before.split(':').count(), 3, "{before}");

    let later = SystemTime::now() + Duration::from_secs(3600);
    std::fs::File::options()
        .write(true)
        .open(&path)
        .unwrap()
        .set_times(FileTimes::new().set_modified(later).set_accessed(later))
        .unwrap();
    let after = tart(&fixture).stable_worker_disk_identity("air-macos-1").unwrap();
    assert_eq!(before, after);
}

#[test]
fn a_clone_input_must_be_a_plain_file() {
    let fixture = Fixture::macos();
    let refusal = tart(&fixture).vm_file_metadata(GOLDEN, CloneInput::Disk).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("vm_input_missing", Exit::NO_INPUT));

    let target = write_vm_file(&fixture, GOLDEN, "elsewhere.img", "elsewhere\n");
    let link = target.with_file_name("disk.img");
    std::os::unix::fs::symlink(&target, &link).unwrap();
    // A symlink would let the signature describe one file while the clone reads another, which is the substitution
    // the seal exists to detect.
    let refusal = tart(&fixture).vm_file_metadata(GOLDEN, CloneInput::Disk).unwrap_err();
    assert_eq!(refusal.code, "vm_input_unsafe");

    std::fs::remove_file(&link).unwrap();
    std::fs::create_dir(&link).unwrap();
    let refusal = tart(&fixture).vm_file_metadata(GOLDEN, CloneInput::Disk).unwrap_err();
    assert_eq!(refusal.code, "vm_input_unsafe");
}

// --- the golden pins -------------------------------------------------------------------------------------------

#[test]
fn the_golden_pins_come_from_the_image_build() {
    let fixture = Fixture::macos();
    let tart = tart(&fixture);
    let code = |tart: &Tart| tart.expected_golden_provenance().unwrap_err().code;
    assert_eq!(code(tart), "golden_versions_unreadable");

    write_versions_env(&fixture, "GOLDEN_SEAL_SCHEMA=1\nMACOS_VERSION=26.6.2\n");
    let refusal = tart.expected_golden_provenance().unwrap_err();
    assert_eq!(refusal.code, "golden_versions_invalid");
    assert!(refusal.message.contains("CIRRUS_BASE_DIGEST"), "{}", refusal.message);

    write_versions_env(&fixture, "GOLDEN_SEAL_SCHEMA=one\nCIRRUS_BASE_DIGEST=x\nMACOS_VERSION=26.6.2\n");
    assert_eq!(code(tart), "golden_versions_invalid");

    // A CRLF file reads, so the image pipeline does not have to care how its editor ends lines.
    write_versions_env(
        &fixture,
        &format!("# pins\r\nGOLDEN_SEAL_SCHEMA=1\r\nCIRRUS_BASE_DIGEST={BASE_DIGEST}\r\nMACOS_VERSION=26.6.2\r\n"),
    );
    assert_eq!(
        tart.expected_golden_provenance().unwrap(),
        GoldenProvenance {
            schema_version: 1,
            base_digest: BASE_DIGEST.to_owned(),
            macos_version: MACOS_VERSION.to_owned(),
        }
    );

    // The real file, whose other keys and comments are not this reader's business.
    let real = std::fs::read_to_string(avl_testkit::repo_path("tools/vm/provision/versions.env")).unwrap();
    write_versions_env(&fixture, &real);
    let pins = tart.expected_golden_provenance().unwrap();
    assert_eq!(pins.schema_version, i64::from(SCHEMA_VERSION));
    assert!(pins.base_digest.starts_with("sha256:"), "{pins:?}");
}

// --- secure receipts -------------------------------------------------------------------------------------------

/// These receipts are the controller's whole trust story for an image, so one anyone could write is not evidence of
/// anything.
#[test]
fn a_secure_receipt_must_be_a_private_regular_file_opened_without_following() {
    let directory = tempfile::tempdir().unwrap();
    let codes = ReceiptCodes {
        missing: "missing",
        unreadable: "unreadable",
        unsafe_: "unsafe",
        invalid: "invalid",
    };
    let read = |path: &Path| {
        read_secure_json_receipt::<serde_json::Value>(path, "receipt", codes).map_err(|refusal| {
            assert_eq!(
                refusal.exit,
                if refusal.code == "invalid" {
                    Exit::DATA_ERR
                } else {
                    Exit::NO_INPUT
                }
            );
            refusal.code.into_owned()
        })
    };

    assert_eq!(read(&directory.path().join("absent.json")).unwrap_err(), "missing");

    let good = directory.path().join("good.json");
    write_private(&good, br#"{"schemaVersion":1}"#);
    read(&good).unwrap();

    let chmod = |mode: u32| {
        std::fs::set_permissions(&good, std::fs::Permissions::from_mode(mode)).unwrap();
    };
    for mode in [0o644, 0o400, 0o666] {
        chmod(mode);
        assert_eq!(read(&good).unwrap_err(), "unsafe", "mode {mode:o}");
    }
    // Exactly 0600 over the *whole* permission word. The setuid bit cannot be set everywhere - Bazel's darwin
    // sandbox silently drops it - so the precondition is verified rather than assumed.
    chmod(0o4600);
    if std::fs::symlink_metadata(&good).unwrap().permissions().mode() & 0o4000 == 0 {
        eprintln!("skipping the setuid case: this filesystem dropped the bit, so the condition cannot be built here");
    } else {
        assert_eq!(read(&good).unwrap_err(), "unsafe", "setuid");
    }
    chmod(0o600);

    let link = directory.path().join("link.json");
    std::os::unix::fs::symlink(&good, &link).unwrap();
    assert_eq!(read(&link).unwrap_err(), "unsafe");

    let broken = directory.path().join("broken.json");
    write_private(&broken, b"{not json");
    assert_eq!(read(&broken).unwrap_err(), "invalid");
}

// --- the seal and the provenance -------------------------------------------------------------------------------

/// The seal `lib.sh` writes, against the clone inputs as they are now. The image moving underneath its receipt is
/// what happens when someone boots the golden by hand; recovery is re-sealing, so it is `stale` rather than
/// `invalid`.
#[test]
fn a_sealed_golden_is_accepted_only_while_its_clone_inputs_still_match() {
    let fixture = Fixture::macos();
    let written = seal_golden(&fixture);
    let sealed = tart(&fixture).require_sealed_golden(GOLDEN).unwrap();
    assert_eq!(sealed, written);

    write_vm_file(&fixture, GOLDEN, "disk.img", "someone booted it\n");
    let refusal = tart(&fixture).require_sealed_golden(GOLDEN).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("golden_seal_stale", Exit::DATA_ERR));
}

/// The golden and the controller had fallen a macOS release apart once, and each was internally consistent - which is
/// why the pins live beside the image build and are compared rather than assumed.
#[test]
fn a_seal_that_disagrees_with_the_builds_pins_is_refused() {
    let fixture = Fixture::macos();
    let seal = seal_golden(&fixture);
    let code = || tart(&fixture).require_sealed_golden(GOLDEN).unwrap_err().code;
    write_versions_env(
        &fixture,
        &format!("GOLDEN_SEAL_SCHEMA=1\nCIRRUS_BASE_DIGEST={BASE_DIGEST}\nMACOS_VERSION=27.0.0\n"),
    );
    assert_eq!(code(), "golden_seal_invalid");
    write_versions_env(
        &fixture,
        &format!("GOLDEN_SEAL_SCHEMA=1\nCIRRUS_BASE_DIGEST=sha256:other\nMACOS_VERSION={MACOS_VERSION}\n"),
    );
    assert_eq!(code(), "golden_seal_invalid");
    write_versions_env(
        &fixture,
        &format!("GOLDEN_SEAL_SCHEMA=2\nCIRRUS_BASE_DIGEST={BASE_DIGEST}\nMACOS_VERSION={MACOS_VERSION}\n"),
    );
    assert_eq!(code(), "golden_seal_invalid");

    // A receipt missing a signature names nothing, and is refused as the same claim from somewhere else.
    write_versions_env(
        &fixture,
        &format!("GOLDEN_SEAL_SCHEMA=1\nCIRRUS_BASE_DIGEST={BASE_DIGEST}\nMACOS_VERSION={MACOS_VERSION}\n"),
    );
    let mut partial = serde_json::to_value(&seal).unwrap();
    partial.as_object_mut().unwrap().remove("nvramSignature");
    write_private(&tart(&fixture).seal_path(GOLDEN), &serde_json::to_vec(&partial).unwrap());
    assert_eq!(code(), "golden_seal_invalid");
}

/// `<worker>/provenance.json` round-trips as one compact line, and goes stale when the disk is replaced: inode, size and birth time all move when a worker is re-cloned.
#[test]
fn worker_provenance_ties_a_worker_to_the_seal_it_was_cloned_from() {
    let fixture = Fixture::macos();
    seal_golden(&fixture);
    write_vm_file(&fixture, "air-macos-1", "disk.img", "test disk for air-macos-1\n");
    let tart = tart(&fixture);
    let sealed = tart.require_sealed_golden(GOLDEN).unwrap();

    let written = tart.write_worker_provenance("air-macos-1", &sealed).unwrap();
    let path = fixture.settings.worker_provenance_path("air-macos-1");
    // Mode 0600, or the receipt is not evidence: anyone who can write it can claim any provenance.
    assert_eq!(std::fs::metadata(&path).unwrap().permissions().mode() & 0o7777, 0o600);
    assert_eq!(tart.require_worker_provenance("air-macos-1", GOLDEN).unwrap(), written);

    // The keys in their order, compact, one line.
    let expected = format!(
        "{{\"schemaVersion\":1,\"worker\":\"air-macos-1\",\"golden\":\"{GOLDEN}\",\"goldenDiskSignature\":\"{}\",\
         \"goldenConfigSignature\":\"{}\",\"goldenNvramSignature\":\"{}\",\"baseDigest\":\"{BASE_DIGEST}\",\
         \"macosVersion\":\"{MACOS_VERSION}\",\"workerDiskIdentity\":\"{}\"}}\n",
        sealed.disk_signature, sealed.config_signature, sealed.nvram_signature, written.worker_disk_identity
    );
    assert_eq!(std::fs::read_to_string(&path).unwrap(), expected);

    // Another golden is a different claim, not a stale one.
    let refusal = tart.require_worker_provenance("air-macos-1", "some-other-golden").unwrap_err();
    assert_eq!(refusal.code, "worker_provenance_invalid");
    write_vm_file(&fixture, "air-macos-1", "disk.img", "a re-cloned disk of a different size\n");
    let refusal = tart.require_worker_provenance("air-macos-1", GOLDEN).unwrap_err();
    assert_eq!(refusal.code, "worker_provenance_stale");
}

// --- liveness --------------------------------------------------------------------------------------------------

fn write_identity(fixture: &Fixture, worker: &str, identity: &ProcessIdentity) {
    let mut encoded = serde_json::to_vec(identity).unwrap();
    encoded.push(b'\n');
    std::fs::write(fixture.settings.pid_path(worker), encoded).unwrap();
}

/// A pid on its own is not an identity: pids are recycled, and a recycled one reads as a live worker to anything that
/// only checks whether the number exists. A `tart.pid` with the collapsed `ps` probes reads alive only while all three
/// legs agree.
#[tokio::test]
async fn liveness_needs_the_whole_identity_triple() {
    let fixture = Fixture::macos();
    let ctx = ctx();
    let worker = "air-macos-1";
    let tart = tart(&fixture);
    let runner = path_runner();
    let own = i32::try_from(std::process::id()).unwrap();

    let live = ProcessIdentity {
        schema_version: SCHEMA_VERSION,
        worker: worker.to_owned(),
        pid: own,
        process_start: host_process_start(&ctx, &runner, own).await.unwrap().unwrap(),
        process_command: host_process_command(&ctx, &runner, own).await.unwrap().unwrap(),
    };
    // `lstart` pads the day of the month, so the recorded form has to be collapsed or a worker started in the first
    // nine days of a month never matches itself.
    assert!(!live.process_start.contains("  "), "{live:?}");

    // Written by hand, not by this crate's serializer, so the reader is held to the document's keys.
    std::fs::write(
        fixture.settings.pid_path(worker),
        format!(
            "{{\"schemaVersion\":1,\"worker\":\"{worker}\",\"pid\":{own},\"processStart\":{},\"processCommand\":{}}}\n",
            serde_json::to_string(&live.process_start).unwrap(),
            serde_json::to_string(&live.process_command).unwrap()
        ),
    )
    .unwrap();
    assert_eq!(tart.read_process_identity(worker).as_ref(), Some(&live));
    assert!(tart.running(&ctx, worker).await.unwrap());

    // Each leg of the triple on its own is enough to disqualify it.
    let stale = ProcessIdentity {
        process_start: "Sat Jan 1 00:00:00 2000".to_owned(),
        ..live.clone()
    };
    write_identity(&fixture, worker, &stale);
    assert!(!tart.running(&ctx, worker).await.unwrap());
    let stale = ProcessIdentity {
        process_command: "/opt/homebrew/bin/tart run something-else".to_owned(),
        ..live.clone()
    };
    write_identity(&fixture, worker, &stale);
    assert!(!tart.running(&ctx, worker).await.unwrap());

    // A zombie is the one dead process `ps` still answers for: the spawn releases the run process without reaping it,
    // so an identity recorded after a boot-time death carries the zombie's own answers and matches them
    // indefinitely. That exact record must still read as down.
    let child = std::process::Command::new("/bin/sh").args(["-c", "exit 0"]).spawn().unwrap();
    let zombie_pid = i32::try_from(child.id()).unwrap();
    // Deliberately never reaped; the zombie leaves the process table with this test process.
    drop(child);
    let deadline = std::time::Instant::now() + Duration::from_secs(10);
    while !host_process_defunct(&ctx, &runner, zombie_pid).await.unwrap() {
        assert!(std::time::Instant::now() < deadline, "the child never became a zombie");
        tokio::time::sleep(Duration::from_millis(10)).await;
    }
    let zombie = ProcessIdentity {
        schema_version: SCHEMA_VERSION,
        worker: worker.to_owned(),
        pid: zombie_pid,
        process_start: host_process_start(&ctx, &runner, zombie_pid).await.unwrap().unwrap(),
        process_command: host_process_command(&ctx, &runner, zombie_pid).await.unwrap().unwrap(),
    };
    write_identity(&fixture, worker, &zombie);
    assert!(!tart.running(&ctx, worker).await.unwrap());

    // Every kind of damaged record answers "not running" rather than refusing: the caller's next question is always
    // whether the worker is up, and no version of "the record is broken" answers yes.
    for damaged in [
        r#"{"schemaVersion":99,"worker":"air-macos-1","pid":1,"processStart":"x","processCommand":"y"}"#,
        r#"{"schemaVersion":1,"worker":"air-macos-2","pid":1,"processStart":"x","processCommand":"y"}"#,
        r#"{"schemaVersion":1,"worker":"air-macos-1","pid":0,"processStart":"x","processCommand":"y"}"#,
        r#"{"schemaVersion":1,"worker":"air-macos-1","pid":1,"processCommand":"y"}"#,
        "not json at all",
    ] {
        std::fs::write(fixture.settings.pid_path(worker), damaged).unwrap();
        assert_eq!(tart.read_process_identity(worker), None, "{damaged}");
    }
    std::fs::remove_file(fixture.settings.pid_path(worker)).unwrap();
    assert!(!tart.running(&ctx, worker).await.unwrap());
    assert_eq!(host_process_start(&ctx, &runner, 0).await, Ok(None));
}

// --- what Tart knows about a VM --------------------------------------------------------------------------------

#[tokio::test]
async fn the_vm_listing_answers_state_and_disk_or_nothing() {
    let fixture = Fixture::macos();
    let ctx = ctx();
    let tart = tart(&fixture);
    fixture.fake.answer(
        Answer::ListJson,
        r#"[
          {"Name":"air-macos-1","Disk":200,"State":"suspended"},
          {"Name":"air-macos-2","State":"weird"}
        ]"#,
    );
    fixture.fake.answer(Answer::ListQuiet, "air-macos-1\nair-macos-2\n");

    assert!(tart.exists(&ctx, "air-macos-2").await.unwrap());
    assert!(!tart.exists(&ctx, "air-macos-3").await.unwrap());

    assert_eq!(tart.vm_state(&ctx, "air-macos-1").await.unwrap(), Some(VmState::Suspended));
    // Anything that is neither running nor suspended is "stopped", because the only question here is whether there is
    // a guest state to spend.
    assert_eq!(tart.vm_state(&ctx, "air-macos-2").await.unwrap(), Some(VmState::Stopped));
    // A VM Tart does not know is nothing rather than stopped: those send the caller to a clone and to a start.
    assert_eq!(tart.vm_state(&ctx, "air-macos-3").await.unwrap(), None);

    // "Tart reported no disk size" and "Tart reported 0 GB" are different facts, and only the first means "ask again
    // after the clone".
    assert_eq!(tart.root_disk_gb(&ctx, "air-macos-1").await.unwrap(), Some(200));
    assert_eq!(tart.root_disk_gb(&ctx, "air-macos-2").await.unwrap(), None);

    fixture.fake.answer(Answer::ListJson, "not json");
    let refusal = tart.root_disk_gb(&ctx, "air-macos-1").await.unwrap_err();
    assert_eq!(refusal.code, "tart_list_unreadable");
}

/// `tart set --disk-size` refuses to shrink, so a worker made when the default was 400 GB stays 400 GB and asking for
/// today's 120 would fail the whole `set` rather than being ignored.
#[tokio::test]
async fn a_root_disk_only_ever_grows() {
    let fixture = Fixture::macos();
    let ctx = ctx();
    let tart = tart(&fixture);
    fixture
        .fake
        .answer(Answer::ListJson, r#"[{"Name":"air-macos-1","Disk":400,"State":"stopped"}]"#);

    assert!(!tart.grow_root_disk(&ctx, "air-macos-1", 120).await.unwrap());
    assert!(!fixture.fake.saw_call_containing("--disk-size"));

    assert!(tart.grow_root_disk(&ctx, "air-macos-1", 800).await.unwrap());
    assert!(
        fixture.fake.saw_call_containing("set air-macos-1 --disk-size 800"),
        "{:?}",
        fixture.fake.calls()
    );

    // A VM Tart does not know yet is grown rather than left alone: a fresh clone has to be sized.
    assert!(tart.grow_root_disk(&ctx, "air-macos-9", 120).await.unwrap());
}

// --- suspend ---------------------------------------------------------------------------------------------------

/// `--suspendable` is a device set that a boot chooses. With it off there is no state to keep, so a suspend is
/// refused.
#[tokio::test]
async fn suspend_needs_the_suspendable_device_set() {
    let ctx = ctx();
    let off = Fixture::builder(GuestOs::Macos).env("AIR_VM_SUSPENDABLE", "0").build();
    let refusal = tart(&off).suspend(&ctx, "air-macos-1").await.unwrap_err();
    assert!(is_unsupported(&refusal), "{refusal:?}");
    assert_eq!(refusal.exit, Exit::USAGE);
    assert!(!off.fake.saw_call_containing("suspend"));

    let macos = Fixture::macos();
    tart(&macos).suspend(&ctx, "air-macos-1").await.unwrap();
    assert!(macos.fake.saw_call_containing("suspend air-macos-1"), "{:?}", macos.fake.calls());
}
