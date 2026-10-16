use std::collections::BTreeMap;
use std::fmt::Write;
use std::ops::Range;

use expect_test::expect;
use pretty_assertions::assert_eq;

use super::*;

/// The daemon port of the default lane daemon, `AIR_VM_DAEMON_PORT`.
const DAEMON_PORT: u16 = 27_100;

fn runtimes() -> BTreeMap<String, String> {
    BTreeMap::from([("pi".to_owned(), "pi-1".to_owned()), ("codex".to_owned(), "codex-1".to_owned())])
}

fn sha(text: &str) -> String {
    sha256_hex(text.as_bytes())
}

/// The inputs of the golden key.
fn golden_inputs<'a>(document: &'a str, runtimes: &'a BTreeMap<String, String>, option_files: &'a str) -> KeyInputs<'a> {
    KeyInputs {
        lane: "UI",
        lane_document: document,
        product_stamp: Some("stamp-1"),
        runtimes,
        option_files,
    }
}

#[test]
fn the_identity_of_the_fixed_key_is_the_golden() {
    let (document, option_files, runtimes) = (sha("lane document"), sha("option files"), runtimes());
    let inputs = golden_inputs(&document, &runtimes, &option_files);
    let identity = LaneIdentity::new(inputs.config_key(), DAEMON_PORT);
    let mut rendered = inputs.text();
    let _ = writeln!(rendered, "key={}", identity.key());
    for (name, port) in [
        ("registry", identity.registry_port()),
        ("control.ide", identity.control_port()),
        ("feedback", identity.feedback_port()),
        ("cloud", identity.cloud_port()),
    ] {
        let _ = writeln!(rendered, "port.{name}={port}");
    }
    for purpose in [
        BRIDGE_TOKEN_PURPOSE,
        LANE_SERVICES_PURPOSE,
        "workflow-source",
        &terminal_login_key("workflow-source"),
    ] {
        let _ = writeln!(rendered, "secret.{purpose}={}", identity.secret(purpose));
    }
    let _ = writeln!(rendered, "url.ide={}", identity.control_url("workflow-source"));
    expect![[r"
        lane=UI
        laneDocument=fe73e1e46a51cb59dd961377c6f528abdc73e11a1ed059622b50eee7416449a2
        productStamp=stamp-1
        runtime.codex=codex-1
        runtime.pi=pi-1
        optionFiles=f52d3ce8c0615ab02d1c50a0ed983216fb369276666e9a9b2eabe6efacb326ba
        key=air-ui-a432e9c21a7cff01
        port.registry=27287
        port.control.ide=30425
        port.feedback=30787
        port.cloud=32287
        secret.air-ui-test-bridge=8f2ca481c13bbe723be9a1e49904742a
        secret.air-lane-services=0b09b4fb3403212c4625ef44736fb78c
        secret.workflow-source=c4176333ba8308484908c180648858ce
        secret.workflow-source-terminal-login=ae2c76b6d52e52563e45f153acc38df3
        url.ide=ws://127.0.0.1:30425/control?token=c4176333ba8308484908c180648858ce
    "]]
    .assert_eq(&rendered);
}

#[test]
fn a_lane_without_a_stamp_states_no_stamp_line_and_its_prefix_is_the_lowercase_lane() {
    let (document, option_files, runtimes) = (sha("lane document"), sha("option files"), runtimes());
    for stamp in [None, Some(""), Some("  ")] {
        let inputs = KeyInputs {
            lane: "GUI_CHAT",
            product_stamp: stamp,
            ..golden_inputs(&document, &runtimes, &option_files)
        };
        assert!(!inputs.text().contains("productStamp="), "{stamp:?}");
        // The second golden key.
        assert_eq!(inputs.config_key(), "air-gui_chat-d929fc5fa3bfa2f8");
    }
}

#[test]
fn every_input_changes_the_key() {
    let (document, option_files, runtimes) = (sha("lane document"), sha("option files"), runtimes());
    let base = golden_inputs(&document, &runtimes, &option_files);
    let (other, mut more) = (sha("another"), runtimes.clone());
    more.insert("claude".to_owned(), "claude-1".to_owned());
    let variants = [
        KeyInputs {
            lane: "UI_REAL",
            ..base.clone()
        },
        KeyInputs {
            lane_document: &other,
            ..base.clone()
        },
        KeyInputs {
            product_stamp: Some("stamp-2"),
            ..base.clone()
        },
        KeyInputs {
            runtimes: &more,
            ..base.clone()
        },
        KeyInputs {
            option_files: &other,
            ..base.clone()
        },
    ];
    for variant in variants {
        assert_ne!(variant.config_key(), base.config_key(), "{variant:?}");
    }
}

/// The first seed of the form `seed-<n>` that picks `port` in `window`, for a daemon on no port of the window.
fn seed_of(port: u16, window: &Range<u16>) -> String {
    let free = LaneIdentity::new("air-ui-test", 0);
    (0..100_000)
        .map(|index| format!("seed-{index}"))
        .find(|seed| free.derived_port(seed, window) == port)
        .expect("a seed of the port")
}

#[test]
fn a_derived_port_steps_over_the_daemon_port_and_wraps_at_the_end_of_its_window() {
    let key = "air-ui-a432e9c21a7cff01";
    let registry = LaneIdentity::new(key, DAEMON_PORT).registry_port();
    assert_eq!(LaneIdentity::new(key, registry).registry_port(), registry + 1);
    let window = 26_000..26_010;
    let last = seed_of(26_009, &window);
    assert_eq!(LaneIdentity::new(key, 26_009).derived_port(&last, &window), 26_000);
}

#[test]
fn the_ide_control_port_is_the_port_that_its_seed_picks() {
    // A key whose plain seed picks the same control port as its IDE seed. No server reserves the port of the plain seed.
    let free = LaneIdentity::new("air-ui-test", DAEMON_PORT);
    let key = (0..100_000)
        .map(|index| format!("air-ui-{index:016x}"))
        .find(|key| free.derived_port(key, &CONTROL_PORTS) == free.derived_port(&format!("{key}|{IDE_CONTROL_SEED}"), &CONTROL_PORTS))
        .expect("a key whose two control seeds collide");
    assert_eq!(
        LaneIdentity::new(&key, DAEMON_PORT).control_port(),
        free.derived_port(&key, &CONTROL_PORTS)
    );
}

#[test]
fn every_window_stays_clear_of_the_fixed_ports_and_of_the_ephemeral_floor() {
    let windows = [&REGISTRY_PORTS, &CONTROL_PORTS, &FEEDBACK_PORTS, &CLOUD_PORTS];
    for (index, window) in windows.iter().enumerate() {
        assert!(window.end <= 32_768, "{window:?}");
        for fixed in [BRIDGE_PORT, crate::lane::services::codex::PORT] {
            assert!(!window.contains(&fixed), "{window:?} holds {fixed}");
        }
        for other in &windows[index + 1..] {
            assert!(window.end <= other.start || other.end <= window.start, "{window:?} and {other:?}");
        }
    }
    assert!(REGISTRY_PORTS.contains(&DAEMON_PORT));
}

#[test]
fn a_secret_tells_two_purposes_apart() {
    let identity = LaneIdentity::new("air-ui-a432e9c21a7cff01", DAEMON_PORT);
    let secrets: Vec<String> = [BRIDGE_TOKEN_PURPOSE, LANE_SERVICES_PURPOSE, "genui-agent"]
        .map(|purpose| identity.secret(purpose))
        .into();
    assert!(secrets.iter().all(|secret| secret.len() == 32));
    assert_eq!(secrets.iter().collect::<std::collections::BTreeSet<_>>().len(), 3);
    assert_eq!(
        identity.control_environment("genui-agent"),
        [
            (
                CONTROL_URL_ENV,
                format!("ws://127.0.0.1:{}/control?token={}", identity.control_port(), secrets[2])
            ),
            (CONTROL_AGENT_KEY_ENV, "genui-agent".to_owned()),
        ]
    );
}
