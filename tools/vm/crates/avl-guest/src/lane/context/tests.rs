use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::Command;

use avl_wire::ide::IdePrepared;
use avl_wire::program::lane::{ControlOwner, Delivery, LaneAgent, LaneDocument, parse_lane_document};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;
use crate::lane::identity::{KeyInputs, LaneIdentity};
use crate::lane::services::testing::{FIXTURE_JAVA, fixture_jar};
use crate::lane::testing::lane_context_testdata;

/// The daemon port of the default lane daemon, `AIR_VM_DAEMON_PORT`.
const DAEMON_PORT: u16 = 27_100;

/// The digest of the option file templates, which the config key reads. A change of a template changes this value and
/// the config key of every lane.
const OPTION_FILES_DIGEST: &str = "e1abc8fc017dfeaa535168b652f8624bd88d626773b0c7083dbe88810fb110c4";

/// A controlled agent of the fixture. The IDE serves the control channel of every controlled agent.
const IDE_OWNED_AGENT: &str = "genui-agent";

/// The bytes of the fixture: the committed lane document of the lane `UI`.
fn document_bytes() -> Vec<u8> {
    fs::read(lane_context_testdata("ui.json")).expect("the fixture lane document")
}

fn document() -> LaneDocument {
    parse_lane_document(&document_bytes()).expect("the fixture lane document reads")
}

/// The fixture document with no controlled agent, so the IDE serves no control channel.
fn document_without_controlled_agents() -> LaneDocument {
    let mut document = document();
    let controlled: Vec<String> = document
        .agents
        .iter()
        .filter(|agent| agent.controlled)
        .map(|agent| agent.key.clone())
        .collect();
    document.agents.retain(|agent| !agent.controlled);
    document.registry_agents.retain(|key| !controlled.contains(key));
    document
}

fn runtimes() -> Vec<CliRuntime> {
    vec![
        CliRuntime {
            name: "codex".to_owned(),
            command: vec!["/runtimes/codex/bin/codex".to_owned()],
            identity: "codex-1".to_owned(),
            version: None,
        },
        CliRuntime {
            name: "pi".to_owned(),
            command: vec!["/runtimes/node/bin/node".to_owned(), "/runtimes/pi/it's/cli.js".to_owned()],
            identity: "pi-1".to_owned(),
            version: None,
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

/// A prepared context below a new temporary directory, as a preparation lays it out.
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
        live: None,
    }
    .write(&prepared.paths)
    .unwrap();
    (prepared, identity, ports)
}

// A bare IDE gets the option files of every lane IDE and the token of the bridge, in its private directory. It gets no
// file of the lane services, of the control server or of an agent, and its home stays empty.
#[test]
fn a_bare_context_holds_the_option_files_and_the_token_alone() {
    let prepared = prepared();
    write_bare(&prepared.paths, "bare-token").unwrap();
    let rendered = tree(&prepared.context);
    let files: Vec<&str> = rendered
        .lines()
        .filter_map(|line| line.strip_prefix("== "))
        .filter(|entry| !entry.contains("/ "))
        .collect();
    let options: Vec<String> = OPTION_FILE_TEMPLATES
        .iter()
        .map(|(name, _)| format!("config/{OPTIONS_DIR}/{name}"))
        .collect();
    let mut expected: Vec<String> = options;
    expected.push(format!("config/{BRIDGE_DIR}/{TOKEN_FILE} 600"));
    expected.sort();
    let mut found: Vec<String> = files.iter().map(|file| (*file).to_owned()).collect();
    found.sort();
    assert_eq!(found, expected, "{rendered}");
    assert!(rendered.contains(&format!("== config/{BRIDGE_DIR}/ 700")), "{rendered}");
    assert_eq!(
        fs::read_to_string(prepared.context.join("config").join(BRIDGE_DIR).join(TOKEN_FILE)).unwrap(),
        "bare-token"
    );
    // The project of a bare IDE opens no README, so the editor area starts empty: the bytes of the start-up bench.
    assert_eq!(
        fs::read_to_string(prepared.context.join("config").join(OPTIONS_DIR).join(ADVANCED_SETTINGS_FILE)).unwrap(),
        NO_README_SETTINGS
    );
}

// The IDE of a lane opens no README either: no lane tests the opening of one, and its editor area starts empty.
#[test]
fn a_lane_context_opens_no_readme() {
    let (prepared, _, _) = written(&document());
    assert_eq!(
        fs::read_to_string(prepared.context.join("config").join(OPTIONS_DIR).join(ADVANCED_SETTINGS_FILE)).unwrap(),
        NO_README_SETTINGS
    );
}

#[test]
fn the_option_templates_have_the_pinned_digest() {
    assert_eq!(option_files_digest(), OPTION_FILES_DIGEST);
}

#[test]
fn the_context_of_the_ui_lane_is_the_golden() {
    let (prepared, _, _) = written(&document());
    expect_test::expect_file![lane_context_testdata("ui-context.txt")].assert_eq(&tree(&prepared.context));
}

/// `ui-acp-json.dump` holds the `acp.json` lines of the lane `UI` from a recorded launch dump, with its masks:
/// `<ctx>` for the context, `<java>` for each command, `<key-derived>` for the control environment.
#[test]
fn acp_json_equals_the_recorded_dump_except_the_key_derived_values() {
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
    let dump = fs::read_to_string(lane_context_testdata("ui-acp-json.dump")).unwrap();
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
    let (prepared, _, _) = written(&document());
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
    let (owned, none, runtimes) = (document(), document_without_controlled_agents(), runtimes());
    let identity = identity(&none, &runtimes);
    let prepared = prepared();
    let jar = fixture_jar();
    let context = |document| LaneContext {
        document,
        identity: &identity,
        java: Path::new(FIXTURE_JAVA),
        fake_agent_jar: &jar,
        runtimes: &runtimes,
        live: None,
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
    // One controlled agent lists sessions, so its credential states it, and the one of its terminal sign-in does not.
    let mut listing = document();
    listing
        .agents
        .iter_mut()
        .filter(|agent| agent.key == IDE_OWNED_AGENT)
        .for_each(|agent| agent.lists_sessions = true);
    let (prepared, identity, ports) = written(&listing);
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
    assert_eq!(control["port"], Value::from(identity.control_port()));
    let expected: Vec<Value> = listing
        .agents
        .iter()
        .filter(|agent| agent.controlled)
        .flat_map(|agent| {
            let login = terminal_login_key(&agent.key);
            [
                serde_json::json!({
                    "agentKey": agent.key,
                    "token": identity.secret(&agent.key),
                    "terminalLogin": false,
                    "listsSessions": agent.key == IDE_OWNED_AGENT,
                }),
                serde_json::json!({ "agentKey": login, "token": identity.secret(&login), "terminalLogin": true, "listsSessions": false }),
            ]
        })
        .collect();
    assert_eq!(control["credentials"], Value::from(expected));
    let acp: Value = serde_json::from_str(&fs::read_to_string(Path::new(&prepared.paths.home_dir).join(ACP_JSON)).unwrap()).unwrap();
    let servers = &acp["agent_servers"];
    assert_eq!(
        servers["air-scenario-genui-agent"]["env"][CONTROL_URL_ENV_NAME],
        Value::from(identity.control_url(IDE_OWNED_AGENT))
    );
    assert_eq!(
        servers["air-scenario-workflow-source"]["env"][CONTROL_URL_ENV_NAME],
        Value::from(identity.control_url("workflow-source"))
    );
}

/// The fixture document with the Cloud agent of the `GUI_CHAT` lane.
fn document_with_a_cloud_agent() -> LaneDocument {
    let mut document = document();
    document.agents.push(LaneAgent {
        key: "remote-scenarios".to_owned(),
        delivery: Delivery::Cloud,
        config_name: None,
        registry_id: None,
        display_name: "Cloud".to_owned(),
        controlled: true,
        control_owner: Some(ControlOwner::Ide),
        lists_sessions: false,
        deterministic: None,
        required_executable: None,
        acp_json: None,
        env: None,
    });
    document
}

/// The trimmed output of `git` with `arguments` in `directory`.
fn git_output(directory: &Path, arguments: &[&str]) -> String {
    let output = Command::new("git")
        .args(arguments)
        .current_dir(directory)
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env("GIT_CONFIG_GLOBAL", "/dev/null")
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "git {arguments:?}: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8(output.stdout).unwrap().trim().to_owned()
}

// A lane with a Cloud agent makes its project a Git repository: one commit of every project file on `main`, and the
// upstream `origin/main` in a bare repository beside the project. A second write keeps that repository. A lane without a Cloud agent leaves the project as it is.
#[test]
fn a_lane_with_a_cloud_agent_makes_its_project_a_repository_with_an_origin() {
    let (cloud, runtimes) = (document_with_a_cloud_agent(), runtimes());
    let identity = identity(&cloud, &runtimes);
    let prepared = prepared();
    let project = PathBuf::from(&prepared.paths.project_dir);
    fs::write(project.join("README.md"), "plain\n").unwrap();
    fs::create_dir_all(project.join("src")).unwrap();
    fs::write(project.join("src/Main.kt"), "fun main() {}\n").unwrap();
    let jar = fixture_jar();
    let context = LaneContext {
        document: &cloud,
        identity: &identity,
        java: Path::new(FIXTURE_JAVA),
        fake_agent_jar: &jar,
        runtimes: &runtimes,
        live: None,
    };
    context.write(&prepared.paths).unwrap();
    let origin = prepared.context.join("project/plain-project-origin.git");
    let facts = [
        ("branch", git_output(&project, &["rev-parse", "--abbrev-ref", "HEAD"])),
        (
            "upstream",
            git_output(&project, &["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"]),
        ),
        (
            "origin",
            git_output(&project, &["remote", "get-url", "origin"]).replace(&prepared.context.display().to_string(), "<ctx>"),
        ),
        ("commits", git_output(&project, &["log", "--format=%an <%ae> %s"])),
        ("files", git_output(&project, &["ls-files"])),
        ("status", git_output(&project, &["status", "--porcelain"])),
        ("bare", git_output(&origin, &["rev-parse", "--is-bare-repository"])),
        ("remote head", git_output(&origin, &["rev-parse", CLOUD_BRANCH])),
    ];
    let head = git_output(&project, &["rev-parse", "HEAD"]);
    let rendered: Vec<String> = facts.iter().map(|(name, value)| format!("{name}: {value}")).collect();
    assert_eq!(
        rendered.join("\n").replace(&head, "<head>"),
        [
            "branch: main",
            "upstream: origin/main",
            "origin: <ctx>/project/plain-project-origin.git",
            "commits: Air Lane <air-lane@example.com> Initial commit",
            "files: README.md\nsrc/Main.kt",
            "status: ",
            "bare: true",
            "remote head: <head>",
        ]
        .join("\n")
    );
    context.write(&prepared.paths).unwrap();
    assert_eq!(
        git_output(&project, &["rev-parse", "HEAD"]),
        head,
        "a second write keeps the repository"
    );

    let (plain, _, _) = written(&document());
    let plain_project = PathBuf::from(&plain.paths.project_dir);
    assert!(
        !plain_project.join(".git").exists(),
        "a lane without a Cloud agent gets no repository"
    );
    assert!(!plain.context.join("project/plain-project-origin.git").exists());
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
            live: None,
        }
        .check()
        .unwrap_err();
        assert_eq!(refusal.code, INPUT_CODE);
        refusal.message
    };
    let mut live = document.clone();
    live.lane = "UI_LIVE".to_owned();
    assert!(refusal(&live, &runtimes, FIXTURE_JAVA).contains("launches only in a live run"));
    assert!(refusal(&document, &runtimes[..1], FIXTURE_JAVA).contains("CLI runtimes"));
    let mut services = document.clone();
    services.services.remove("codexModel");
    assert!(refusal(&services, &runtimes, FIXTURE_JAVA).contains("services"));
    assert!(refusal(&document, &runtimes, "jbr/bin/java").contains("not absolute"));
}
