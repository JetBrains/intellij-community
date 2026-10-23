use std::path::PathBuf;
use std::sync::{Arc, Mutex, PoisonError};

use pretty_assertions::assert_eq;
use yonder_link::Channel;
use yonder_link::proto::{FEATURES, RunSpec, Stream};

use super::*;
use crate::testkit::{FakeProcesses, Said, Setup, calm, client, ok};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// The head of a Mach-O binary of arm64, the platform of the fake agent, and a body.
const MACHO_ARM64: &[u8] = b"\xcf\xfa\xed\xfe\x0c\x00\x00\x01 a yonderd of arm64";

/// The binary that the fake agent runs before an upgrade.
const OLD: &str = "1111111111111111111111111111111111111111111111111111111111111111";

/// The features of an agent of this build on macOS.
fn with_upgrade() -> Vec<&'static str> {
    FEATURES.into_iter().chain([feature::UPGRADE]).collect()
}

/// An agent of [`OLD`] that takes an upgrade. With `restarts`, the hellos after the upgrade name the new binary, as a
/// new `serve` would; without it, the old binary answers, as after the rollback of the watchdog.
fn upgradable(restarts: bool) -> Setup {
    let binary = Arc::new(Mutex::new(Some(OLD.to_owned())));
    let hello = Arc::clone(&binary);
    Setup::with_binary(&with_upgrade(), binary, move |request, channel| {
        let Request::Upgrade { size, sha256 } = request else {
            let _ = channel.send(&Reply::refused("bad_request", "not in this script"));
            return;
        };
        let mut bytes = Vec::new();
        channel.recv_stream(&mut bytes).expect("the binary");
        assert_eq!(bytes.len() as u64, *size);
        assert_eq!(sha256_hex(&bytes), *sha256);
        if restarts {
            *hello.lock().unwrap_or_else(PoisonError::into_inner) = Some(sha256.clone());
        }
        let _ = channel.send(&Reply::Upgraded {
            previous_sha256: OLD.to_owned(),
            sha256: sha256.clone(),
            version: "0.1.0".to_owned(),
        });
    })
}

fn binary_file(setup: &Setup, bytes: &[u8]) -> PathBuf {
    let path = setup.dir.path().join("yonderd");
    fs::write(&path, bytes).expect("the binary");
    path
}

fn request<'a>(agent: Option<&'a Path>, checkout: Option<&'a Path>) -> UpgradeRequest<'a> {
    UpgradeRequest {
        alias: "studio",
        agent,
        checkout,
        holder: "yonder-0123456789ab",
    }
}

#[test]
fn an_agent_with_the_feature_gets_the_binary_and_the_upgrade_ends_at_the_hello_of_the_new_binary() {
    let setup = upgradable(true);
    let agent = binary_file(&setup, MACHO_ARM64);
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let processes = FakeProcesses::default();
    let upgraded = client(&setup, &processes, &say, &calm)
        .upgrade(&request(Some(&agent), None))
        .expect("upgraded");

    let sha256 = sha256_hex(MACHO_ARM64);
    assert_eq!(upgraded.way, UpgradeWay::Request);
    assert_eq!(upgraded.previous_sha256.as_deref(), Some(OLD));
    assert_eq!((upgraded.sha256.as_str(), upgraded.version.as_str()), (sha256.as_str(), "0.1.0"));
    assert_eq!(upgraded.agent.address, setup.agent.address);
    assert_eq!(
        setup.agent.requests(),
        [Request::Upgrade {
            size: MACHO_ARM64.len() as u64,
            sha256: sha256.clone()
        }]
    );
    assert_eq!(
        said.lines(),
        [
            format!("yonder: sending yonderd ({} B) to studio", MACHO_ARM64.len()),
            format!(
                "yonder: studio installed yonderd {}; waiting for the new agent to answer",
                &sha256[..12]
            ),
        ]
    );
    assert!(processes.calls().is_empty(), "no build with --agent");
}

#[test]
fn the_binary_that_the_agent_runs_is_current_and_does_not_go() {
    let setup = Setup::with_binary(&with_upgrade(), Arc::new(Mutex::new(Some(sha256_hex(MACHO_ARM64)))), |_, _| {});
    let agent = binary_file(&setup, MACHO_ARM64);
    let upgraded = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&request(Some(&agent), None))
        .expect("current");
    assert_eq!(upgraded.way, UpgradeWay::Current);
    assert!(setup.agent.requests().is_empty());
}

#[test]
fn a_binary_of_another_platform_is_refused_before_it_goes() {
    let setup = upgradable(true);
    let mut elf = b"\x7fELF".to_vec();
    elf.resize(18, 0);
    elf.extend_from_slice(&0xb7_u16.to_le_bytes());
    let agent = binary_file(&setup, &elf);
    let refusal = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&request(Some(&agent), None))
        .expect_err("a binary of Linux");
    assert_eq!(refusal.code, code::AGENT_FAILED);
    assert!(
        refusal
            .message
            .ends_with("is a binary of linux aarch64, and the host runs macos aarch64"),
        "{}",
        refusal.message
    );
    assert!(setup.agent.requests().is_empty());
}

#[test]
fn the_previous_binary_that_answers_at_the_end_is_a_rollback() {
    let setup = upgradable(false);
    let agent = binary_file(&setup, MACHO_ARM64);
    let refusal = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&request(Some(&agent), None))
        .expect_err("the watchdog restored the old binary");
    assert_eq!(refusal.code, code::ROLLED_BACK);
    assert!(refusal.message.contains(&OLD[..12]), "{}", refusal.message);
    assert!(
        refusal.message.contains("~/Library/Logs/yonderd.log on studio"),
        "{}",
        refusal.message
    );
}

#[test]
fn an_agent_without_the_feature_gets_a_bootstrap_run_that_builds_the_agent_on_the_host() {
    let binary = Arc::new(Mutex::new(None));
    let started: Arc<Mutex<Option<RunSpec>>> = Arc::default();
    let new = "2".repeat(64);
    let line = format!(r#"{{"previous_sha256":"{OLD}","sha256":"{new}","version":"0.1.0"}}"#);
    let setup = Setup::with_binary(&FEATURES, Arc::clone(&binary), {
        let (started, new) = (Arc::clone(&started), new.clone());
        move |request, channel: &mut Channel| {
            let _ = match request {
                Request::Have { commits, .. } => channel.send(&Reply::Have { commits: commits.clone() }),
                Request::Start(spec) => {
                    *started.lock().unwrap_or_else(PoisonError::into_inner) = Some(spec.clone());
                    channel.send(&Reply::Started { run: spec.run.clone() })
                }
                Request::Follow { .. } => {
                    let _ = channel.send(&Reply::Output {
                        stream: Stream::Stdout,
                        text: line.clone(),
                        end: 1,
                    });
                    *binary.lock().unwrap_or_else(PoisonError::into_inner) = Some(new.clone());
                    channel.send(&Reply::Exit { code: 0 })
                }
                Request::Forget { .. } => channel.send(&Reply::Done {
                    message: "removed".to_owned(),
                }),
                _ => channel.send(&Reply::refused("bad_request", "not in this script")),
            };
        }
    });
    let checkout = setup.checkout();
    fs::create_dir_all(checkout.join("community/tools/yonder")).expect("a checkout of the monorepo");
    let processes = FakeProcesses::new(&[
        ("status --porcelain", ok("")),
        ("rev-parse", ok(&format!("{COMMIT}\n"))),
        ("rev-list --count", ok("7\n")),
        ("rev-list", ok(&format!("{COMMIT}\n"))),
    ]);
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let upgraded = client(&setup, &processes, &say, &calm)
        .upgrade(&request(None, Some(&checkout)))
        .expect("upgraded");

    let spec = started.lock().unwrap_or_else(PoisonError::into_inner).clone().expect("a run");
    assert_eq!(upgraded.way, UpgradeWay::Bootstrap(spec.run.clone()));
    assert_eq!(upgraded.sha256, new);
    assert_eq!(upgraded.previous_sha256.as_deref(), Some(OLD));
    assert_eq!(
        spec.command,
        ["/bin/sh", "-c", BOOTSTRAP, "yonder-upgrade", "@community//tools/yonder:yonderd"]
    );
    assert_eq!(
        (spec.repo.as_str(), spec.holder.as_str(), spec.commit.as_str()),
        ("~/projects/idea-1", "yonder-0123456789ab", COMMIT)
    );
    assert_eq!(spec.nice, None);
    assert!(
        said.lines()
            .iter()
            .any(|line| line.contains("knows no upgrade request, so a run there builds")),
        "{:?}",
        said.lines()
    );
    assert!(
        !processes.texts().iter().any(|text| text.contains("bazel")),
        "the host builds the agent"
    );
}

#[test]
fn a_bootstrap_needs_a_checkout_of_the_monorepo() {
    let setup = Setup::with_features(&FEATURES, |_, _| {});
    let refusal = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&request(None, Some(&setup.checkout())))
        .expect_err("no monorepo");
    assert_eq!(refusal.code, code::USAGE);
    assert!(
        refusal
            .message
            .contains("run `yonder upgrade studio` in a checkout of the monorepo"),
        "{}",
        refusal.message
    );
    assert!(setup.agent.requests().is_empty());
}

#[test]
fn the_header_names_the_platform_of_a_binary() {
    let elf = |machine: u16| {
        let mut bytes = b"\x7fELF".to_vec();
        bytes.resize(18, 0);
        bytes.extend_from_slice(&machine.to_le_bytes());
        bytes
    };
    assert_eq!(binary_platform(MACHO_ARM64), Platform::Of("macos", "aarch64"));
    assert_eq!(
        binary_platform(b"\xcf\xfa\xed\xfe\x07\x00\x00\x01"),
        Platform::Of("macos", "x86_64")
    );
    assert_eq!(binary_platform(&elf(0x3e)), Platform::Of("linux", "x86_64"));
    assert_eq!(binary_platform(&elf(0xb7)), Platform::Of("linux", "aarch64"));
    assert_eq!(binary_platform(b"\xca\xfe\xba\xbe\x00\x00\x00\x02"), Platform::Universal);
    for unknown in [&b"#!/bin/sh"[..], b"", &elf(0x28), b"\xcf\xfa\xed\xfe\x12\x00\x00\x00"] {
        assert_eq!(binary_platform(unknown), Platform::Unknown, "{unknown:?}");
    }
}

/// The script runs in a slot whose `bazel.cmd` is a fake: the binary that cquery names gets `upgrade` with its own path.
#[test]
fn the_bootstrap_script_builds_the_label_and_runs_the_binary_that_cquery_names() {
    use std::os::unix::fs::PermissionsExt;

    let dir = tempfile::tempdir().expect("a temporary directory");
    let (slot, execroot) = (dir.path().join("slot"), dir.path().join("execroot"));
    let binary = execroot.join("bazel-out/bin/external/community+/tools/yonder/yonderd");
    fs::create_dir_all(binary.parent().expect("a parent")).expect("the output directory");
    fs::create_dir_all(&slot).expect("the slot");
    let launcher = format!(
        "#!/bin/sh\ncase $1 in\n  build) echo \"building $2\" ;;\n  info) echo {} ;;\n  cquery) echo bazel-out/bin/external/community+/tools/yonder/yonderd.runfiles; echo bazel-out/bin/external/community+/tools/yonder/yonderd ;;\nesac\n",
        execroot.display()
    );
    for (path, text) in [
        (slot.join("bazel.cmd"), launcher),
        (binary.clone(), "#!/bin/sh\necho \"$0 $*\"\n".to_owned()),
    ] {
        fs::write(&path, text).expect("a script");
        fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).expect("executable");
    }
    let ran = std::process::Command::new("/bin/sh")
        .args(["-c", BOOTSTRAP, "yonder-upgrade", "@community//tools/yonder:yonderd"])
        .current_dir(&slot)
        .output()
        .expect("the script");
    assert!(ran.status.success(), "{}", String::from_utf8_lossy(&ran.stderr));
    assert_eq!(String::from_utf8_lossy(&ran.stderr), "building @community//tools/yonder:yonderd\n");
    let path = binary.display();
    assert_eq!(String::from_utf8_lossy(&ran.stdout), format!("{path} upgrade {path}\n"));
}
