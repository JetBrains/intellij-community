use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use avl_wire::ide::IdePrepared;
use avl_wire::program::lane::{LaneDocument, parse_lane_document};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;
use crate::lane::identity::{KeyInputs, LaneIdentity};
use crate::lane::services::testing::{FIXTURE_JAVA, fixture_jar};

/// The daemon port of the default lane daemon, `AIR_VM_DAEMON_PORT`.
const DAEMON_PORT: u16 = 27_100;

/// The digest of the option file templates that `AirLaneDigestTest` pins on the Kotlin side.
const KOTLIN_OPTION_FILES_DIGEST: &str = "7960b0efbb3ac177ba7c285404e033ed2b7bdc8b59f9aef8dcd61826bd57773f";

/// The controlled agent of the fixture whose control channel the IDE serves in these tests. The committed document of
/// the lane gives the host every controlled agent, so the tests move this one to the IDE.
const IDE_OWNED_AGENT: &str = "genui-agent";

fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/lane-context/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// The bytes of the fixture: the committed lane document of the lane `UI`.
fn document_bytes() -> Vec<u8> {
    fs::read(testdata("ui.json")).expect("the fixture lane document")
}

fn document() -> LaneDocument {
    parse_lane_document(&document_bytes()).expect("the fixture lane document reads")
}

/// The fixture document with [`IDE_OWNED_AGENT`] served by the IDE.
fn document_with_ide_agent() -> LaneDocument {
    let mut document = document();
    let agent = document
        .agents
        .iter_mut()
        .find(|agent| agent.key == IDE_OWNED_AGENT)
        .expect("the fixture has the agent");
    assert_eq!(
        agent.control_owner,
        Some(ControlOwner::Host),
        "the committed document gives the agent to the host"
    );
    agent.control_owner = Some(ControlOwner::Ide);
    document
}

fn runtimes() -> Vec<CliRuntime> {
    vec![
        CliRuntime {
            name: "codex".to_owned(),
            command: vec!["/runtimes/codex/bin/codex".to_owned()],
            identity: "codex-1".to_owned(),
        },
        CliRuntime {
            name: "pi".to_owned(),
            command: vec!["/runtimes/node/bin/node".to_owned(), "/runtimes/pi/it's/cli.js".to_owned()],
            identity: "pi-1".to_owned(),
        },
    ]
}

fn identity(document: &LaneDocument, runtimes: &[CliRuntime]) -> LaneIdentity {
    let identities: BTreeMap<String, String> = runtimes
        .iter()
        .map(|runtime| (runtime.name.clone(), runtime.identity.clone()))
        .collect();
    let (lane_document, option_files) = (sha256_hex(&document_bytes()), option_files_digest());
    let inputs = KeyInputs {
        lane: &document.lane,
        lane_document: &lane_document,
        product_stamp: None,
        runtimes: &identities,
        option_files: &option_files,
    };
    LaneIdentity::new(inputs.config_key(), DAEMON_PORT)
}

/// A prepared context below a new temporary directory, as `ide-prepare` lays it out.
struct Prepared {
    _root: tempfile::TempDir,
    context: PathBuf,
    paths: IdePrepared,
}

fn prepared() -> Prepared {
    let root = tempfile::tempdir().unwrap();
    // The real path, so the absolute and the real project path are one trusted path on every host.
    let context = real_path(root.path()).unwrap().join("context");
    let child = |name: &str| {
        let path = context.join(name);
        fs::create_dir_all(&path).unwrap();
        path.display().to_string()
    };
    let paths = IdePrepared {
        config_dir: child("config"),
        system_dir: child("system"),
        plugins_dir: child("plugins"),
        project_dir: child("project/plain-project"),
        home_dir: child("home"),
        bin_dir: child("bin"),
        context_dir: context.display().to_string(),
    };
    Prepared {
        _root: root,
        context,
        paths,
    }
}

/// Every file below `context` in path order, with its mode when the writer sets one and its text, `<ctx>` for the
/// context. A file that is no text is its SHA-256.
fn tree(context: &Path) -> String {
    let real = real_path(context).unwrap();
    let mask = |text: &str| {
        text.replace(&real.display().to_string(), "<ctx>")
            .replace(&context.display().to_string(), "<ctx>")
    };
    let mut rendered = String::new();
    for entry in walkdir::WalkDir::new(context).sort_by_file_name() {
        let entry = entry.unwrap();
        let relative = entry.path().strip_prefix(context).unwrap().display().to_string();
        let mode = entry.metadata().unwrap().permissions().mode() & 0o777;
        // A mode of the owner alone is a mode that the writer set.
        let shown = if mode & 0o700 == mode {
            format!(" {mode:o}")
        } else if entry.file_type().is_file() && mode & 0o100 != 0 {
            " u+x".to_owned()
        } else {
            String::new()
        };
        if entry.file_type().is_dir() {
            if !shown.is_empty() {
                let _ = writeln!(rendered, "== {relative}/{shown}");
            }
            continue;
        }
        let bytes = fs::read(entry.path()).unwrap();
        let _ = writeln!(rendered, "== {relative}{shown}");
        match String::from_utf8(bytes) {
            Ok(text) => {
                let _ = writeln!(rendered, "{}", mask(&text).trim_end_matches('\n'));
            }
            Err(error) => {
                let _ = writeln!(rendered, "sha256 {}", sha256_hex(error.as_bytes()));
            }
        }
    }
    rendered
}

fn written(document: &LaneDocument) -> (Prepared, LaneIdentity, ServicePorts) {
    let runtimes = runtimes();
    let identity = identity(document, &runtimes);
    let prepared = prepared();
    let ports = LaneContext {
        document,
        identity: &identity,
        java: Path::new(FIXTURE_JAVA),
        fake_agent_jar: &fixture_jar(),
        runtimes: &runtimes,
    }
    .write(&prepared.paths)
    .unwrap();
    (prepared, identity, ports)
}

#[test]
fn the_option_templates_are_the_bytes_of_the_kotlin_templates() {
    assert_eq!(option_files_digest(), KOTLIN_OPTION_FILES_DIGEST);
}

#[test]
fn the_context_of_the_ui_lane_is_the_golden() {
    let (prepared, _, _) = written(&document_with_ide_agent());
    expect_test::expect_file![testdata("ui-context.txt")].assert_eq(&tree(&prepared.context));
}

/// `ui-acp-json.dump` holds the `acp.json` lines of the lane `UI` from the launch dump of the JVM host, with its masks:
/// `<ctx>` for the context, `<java>` for each command, `<key-derived>` for the control environment.
#[test]
fn acp_json_equals_the_launch_dump_of_the_jvm_host_except_the_key_derived_values() {
    let (prepared, _, _) = written(&document());
    let written = fs::read_to_string(Path::new(&prepared.paths.home_dir).join(ACP_JSON)).unwrap();
    let real = real_path(&prepared.context).unwrap().display().to_string();
    let masked = written
        .replace(&real, "<ctx>")
        .replace(&prepared.context.display().to_string(), "<ctx>");
    let mut masked: Value = serde_json::from_str(&masked).unwrap();
    for entry in masked["agent_servers"].as_object_mut().unwrap().values_mut() {
        entry["command"] = Value::from("<java>");
        let env = entry["env"].as_object_mut().unwrap();
        for name in [CONTROL_URL_ENV_NAME, CONTROL_AGENT_KEY_ENV_NAME] {
            if let Some(value) = env.get_mut(name) {
                *value = Value::from("<key-derived>");
            }
        }
    }
    let dump = fs::read_to_string(testdata("ui-acp-json.dump")).unwrap();
    let dump_text: String = dump.lines().map(|line| line.strip_prefix("UI acp.json ").unwrap()).collect();
    assert_eq!(masked, serde_json::from_str::<Value>(&dump_text).unwrap());
    assert_eq!(
        entry_names(&written),
        entry_names(&dump_text),
        "the entries keep the order of the dump"
    );
}

const CONTROL_URL_ENV_NAME: &str = crate::lane::identity::CONTROL_URL_ENV;
const CONTROL_AGENT_KEY_ENV_NAME: &str = crate::lane::identity::CONTROL_AGENT_KEY_ENV;

/// The entry names of an `acp.json` text in their order: the quoted text before each `:{"command":`.
fn entry_names(text: &str) -> Vec<String> {
    let parts: Vec<&str> = text.split(":{\"command\":").collect();
    parts[..parts.len() - 1]
        .iter()
        .filter_map(|part| part.strip_suffix('"')?.rsplit_once('"').map(|(_, name)| name.to_owned()))
        .collect()
}

#[test]
fn the_private_files_and_the_launchers_get_their_modes() {
    let (prepared, _, _) = written(&document_with_ide_agent());
    let bridge = Path::new(&prepared.paths.config_dir).join(BRIDGE_DIR);
    let mode = |path: &Path| fs::metadata(path).unwrap().permissions().mode() & 0o777;
    assert_eq!(mode(&bridge), 0o700);
    for file in [TOKEN_FILE, CONTROL_FILE, LANE_SERVICES_FILE] {
        assert_eq!(mode(&bridge.join(file)), 0o600, "{file}");
    }
    for runtime in ["codex", "pi"] {
        assert_ne!(mode(&Path::new(&prepared.paths.bin_dir).join(runtime)) & 0o100, 0, "{runtime}");
    }
}

#[test]
fn a_second_write_keeps_the_general_settings_and_the_next_start_file_and_drops_a_stale_control_file() {
    let (owned, none, runtimes) = (document_with_ide_agent(), document(), runtimes());
    let identity = identity(&none, &runtimes);
    let prepared = prepared();
    let jar = fixture_jar();
    let context = |document| LaneContext {
        document,
        identity: &identity,
        java: Path::new(FIXTURE_JAVA),
        fake_agent_jar: &jar,
        runtimes: &runtimes,
    };
    context(&owned).write(&prepared.paths).unwrap();
    let config = Path::new(&prepared.paths.config_dir);
    let general = config.join(OPTIONS_DIR).join(GENERAL_SETTINGS_FILE);
    fs::write(&general, "<application/>").unwrap();
    let next_start = config.join(BRIDGE_DIR).join(NEXT_START_CONTROL_FILE);
    fs::write(&next_start, "{}").unwrap();
    fs::set_permissions(config.join(BRIDGE_DIR).join(TOKEN_FILE), fs::Permissions::from_mode(0o644)).unwrap();

    context(&none).write(&prepared.paths).unwrap();
    assert_eq!(fs::read_to_string(&general).unwrap(), "<application/>");
    assert_eq!(fs::read_to_string(&next_start).unwrap(), "{}");
    assert!(!config.join(BRIDGE_DIR).join(CONTROL_FILE).exists());
    let token = config.join(BRIDGE_DIR).join(TOKEN_FILE);
    assert_eq!(fs::metadata(&token).unwrap().permissions().mode() & 0o777, 0o600);
    assert_eq!(fs::read_to_string(&token).unwrap(), identity.secret(BRIDGE_TOKEN_PURPOSE));
}

#[test]
fn the_files_carry_the_values_of_the_identity() {
    let (prepared, identity, ports) = written(&document_with_ide_agent());
    let bridge = Path::new(&prepared.paths.config_dir).join(BRIDGE_DIR);
    let read = |name: &str| serde_json::from_str::<Value>(&fs::read_to_string(bridge.join(name)).unwrap()).unwrap();
    assert_eq!(
        ports,
        ServicePorts {
            registry: Some(identity.registry_port()),
            cloud: identity.cloud_port(),
            feedback: None,
            codex: Some(codex::PORT),
        }
    );
    let services = read(LANE_SERVICES_FILE);
    assert_eq!(services["token"], Value::from(identity.secret(LANE_SERVICES_PURPOSE)));
    assert_eq!(
        services["registry"],
        Value::from(format!("http://127.0.0.1:{}", identity.registry_port()))
    );
    let control = read(CONTROL_FILE);
    assert_eq!(control["port"], Value::from(identity.control_port(ControlOwner::Ide)));
    let login = terminal_login_key(IDE_OWNED_AGENT);
    assert_eq!(
        control["credentials"],
        serde_json::json!([
            { "agentKey": IDE_OWNED_AGENT, "token": identity.secret(IDE_OWNED_AGENT), "terminalLogin": false },
            { "agentKey": login, "token": identity.secret(&login), "terminalLogin": true },
        ])
    );
    let acp: Value = serde_json::from_str(&fs::read_to_string(Path::new(&prepared.paths.home_dir).join(ACP_JSON)).unwrap()).unwrap();
    let servers = &acp["agent_servers"];
    assert_eq!(
        servers["air-scenario-genui-agent"]["env"][CONTROL_URL_ENV_NAME],
        Value::from(identity.control_url(ControlOwner::Ide, IDE_OWNED_AGENT))
    );
    assert_eq!(
        servers["air-scenario-workflow-source"]["env"][CONTROL_URL_ENV_NAME],
        Value::from(identity.control_url(ControlOwner::Host, "workflow-source"))
    );
}

#[test]
fn a_document_that_the_daemon_cannot_launch_as_it_states_is_refused() {
    let (document, runtimes) = (document(), runtimes());
    let identity = identity(&document, &runtimes);
    let jar = fixture_jar();
    let refusal = |document: &LaneDocument, runtimes: &[CliRuntime], java: &str| {
        let refusal = LaneContext {
            document,
            identity: &identity,
            java: Path::new(java),
            fake_agent_jar: &jar,
            runtimes,
        }
        .check()
        .unwrap_err();
        assert_eq!(refusal.code, INPUT_CODE);
        refusal.message
    };
    let mut live = document.clone();
    live.lane = "UI_LIVE".to_owned();
    assert!(refusal(&live, &runtimes, FIXTURE_JAVA).contains("no live lane"));
    assert!(refusal(&document, &runtimes[..1], FIXTURE_JAVA).contains("CLI runtimes"));
    let mut services = document.clone();
    services.services.remove("codexModel");
    assert!(refusal(&services, &runtimes, FIXTURE_JAVA).contains("services"));
    assert!(refusal(&document, &runtimes, "jbr/bin/java").contains("not absolute"));
}
