use std::path::PathBuf;
use std::sync::{Arc, Mutex, PoisonError};

use pretty_assertions::assert_eq;

use super::*;
use crate::testkit::{FakeProcesses, Said, Setup, calm, client};

/// The head of a Mach-O binary of arm64, the platform of the fake agent, and a body.
const MACHO_ARM64: &[u8] = b"\xcf\xfa\xed\xfe\x0c\x00\x00\x01 a yonderd of arm64";

/// The binary that the fake agent runs before an upgrade.
const OLD: &str = "1111111111111111111111111111111111111111111111111111111111111111";

/// The features of an agent of this build on macOS.
fn with_upgrade() -> Vec<&'static str> {
    vec![feature::UPGRADE]
}

/// An agent of [`OLD`] that takes an upgrade. With `restarts`, the hellos after the upgrade name the new binary, as a
/// new `serve` would; without it, the old binary answers, as after the rollback of the watchdog.
fn upgradable(restarts: bool) -> Setup {
    let binary = Arc::new(Mutex::new(Some(OLD.to_owned())));
    let hello = Arc::clone(&binary);
    Setup::with_binary(&with_upgrade(), binary, move |request, channel| {
        let Request::Upgrade { size, sha256, .. } = request else {
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
        force: false,
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
            sha256: sha256.clone(),
            force: false,
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
    assert_eq!(setup.agent.requests(), []);

    let forced = UpgradeRequest {
        force: true,
        ..request(Some(&agent), None)
    };
    let refusal = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&forced)
        .expect_err("the script of this agent answers nothing");
    assert_eq!(refusal.code, code::LINK, "{refusal:?}");
    assert!(
        matches!(setup.agent.requests().as_slice(), [Request::Upgrade { force: true, .. }]),
        "{:?}",
        setup.agent.requests()
    );
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
    assert_eq!(setup.agent.requests(), []);
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
        refusal.message.contains("~/.cache/yonderd/upgrade.json on studio"),
        "{}",
        refusal.message
    );
}

/// An agent without the feature cannot read an upgrade request, so the client refuses before it sends the binary.
#[test]
fn an_agent_without_the_feature_is_refused_before_the_binary_goes() {
    let setup = Setup::with_features(&[], |_, _| {});
    let agent = binary_file(&setup, MACHO_ARM64);
    let refusal = client(&setup, &FakeProcesses::default(), &|_| {}, &calm)
        .upgrade(&request(Some(&agent), None))
        .expect_err("an agent without the feature");
    assert_eq!(refusal.code, code::OLD_AGENT);
    assert_eq!(
        refusal.message,
        "the agent on studio knows no upgrade request: `yonder prepare studio` installs a new one over ssh"
    );
    assert_eq!(setup.agent.requests(), []);
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
