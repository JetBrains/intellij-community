//! The live run of one iteration over fake CLIs: a temporary guest account, a fake Central CLI that wires it as
//! `central add` does, and fake agent runtimes. No real agent and no real account takes part, and the sweep of the agent
//! processes is a fake, because the real one ends every process of the account with the name of an agent.

use std::collections::BTreeMap;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use avl_wire::daemon::RUN_SECRETS_VARIABLE;
use avl_wire::ide::IdePrepared;
use avl_wire::program::lane::{LIVE_LANE, LiveContext, LiveRuntime, parse_lane_document};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::central::AgentProcesses;
use super::*;
use crate::lane::context::{ACP_JSON, BRIDGE_DIR, CLOUD_IDENTITY, CODEX_CONFIG, CONTROL_FILE, LANE_SERVICES_FILE, LaneContext};
use crate::lane::identity::LaneIdentity;
use crate::lane::services::codex;
use crate::lane::services::testing::{FIXTURE_JAVA, fixture_jar};

/// The one token of the fake login. No note may name it.
const LOGIN_TOKEN: &str = "smoke0-s3ss10n-token-0123456789";

/// The token of the wiring that the fake `central add` writes. No note may name it.
const WIRING_TOKEN: &str = "w1ringtokenw1ringtokenw1ringtoken";

/// A Central CLI that records `<arguments> HOME=<home>` for each call and wires the account as `central add` does: the
/// Claude settings and the Codex configuration name the local proxy and carry a token. `status` reports the proxy on
/// the pinned port. With the file `fail-import` it rejects the login as a real CLI can: it echoes the input to its
/// standard error and exits 4.
const FAKE_CENTRAL: &str = r#"#!/bin/sh
input=$(cat)
echo "$* HOME=$HOME" >> '{dir}/central-calls.log'
case "$1 $2" in
  "login import") if [ -f '{dir}/fail-import' ]; then
      printf 'rejected %s\n' "$(printf '%s' "$input" | tr '\n' ' ')" >&2; exit 4
    fi
    mkdir -p "$HOME/.jetbrains-central" ;;
  "add claude-code") mkdir -p "$HOME/.claude"
    printf '{"env": {"ANTHROPIC_BASE_URL": "http://127.0.0.1:19516/wire/claude", "ANTHROPIC_AUTH_TOKEN": "{token}"}}' > "$HOME/.claude/settings.json" ;;
  "add codex") mkdir -p "$HOME/.codex"
    printf 'model_provider = "central"\n\n[model_providers.central]\nbase_url = "http://127.0.0.1:19516/wire/codex"\nexperimental_bearer_token = "{token}"\n' > "$HOME/.codex/config.toml" ;;
  "add junie") mkdir -p "$HOME/.junie" ;;
  "status ") printf 'Proxy      running on port 19516\n' ;;
esac
exit 0
"#;

/// What the fake sweep was asked to end. It ends nothing.
struct Sweeps(Arc<Mutex<Vec<String>>>);

impl AgentProcesses for Sweeps {
    fn stop(&mut self, names: &[&str], _timeout: Duration) -> Vec<String> {
        self.0.lock().unwrap().push(names.join("|"));
        Vec::new()
    }
}

/// The live context of the lane document of `ui-live`, with the unpack root of the project under `root`.
fn context(root: &Path) -> LiveContext {
    LiveContext {
        login_input: "AIR_LIVE_CENTRAL_LOGIN".to_owned(),
        login_source: "central login export".to_owned(),
        central_runtime: "central".to_owned(),
        central_port: 19516,
        central_agents: ["codex", "claude-code", "junie"].map(str::to_owned).to_vec(),
        runtimes: vec![
            LiveRuntime {
                name: "claude".to_owned(),
                environment: BTreeMap::from([("DISABLE_AUTOUPDATER".to_owned(), "1".to_owned())]),
            },
            LiveRuntime {
                name: "junie".to_owned(),
                environment: BTreeMap::new(),
            },
        ],
        project_unpack_root: root.join("air-ui-live/project").display().to_string(),
    }
}

struct Fixture {
    directory: tempfile::TempDir,
    root: PathBuf,
    account: PathBuf,
    secrets: PathBuf,
    node: PathBuf,
    declared: Vec<CliRuntime>,
}

impl Fixture {
    fn new() -> Self {
        let directory = tempfile::tempdir().unwrap();
        let root = std::path::absolute(directory.path()).unwrap();
        let (account, secrets) = (root.join("account"), root.join("secrets"));
        fs::create_dir_all(&account).unwrap();
        fs::create_dir_all(&secrets).unwrap();
        let executable = |path: &Path, text: &str| {
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, text).unwrap();
            fs::set_permissions(path, fs::Permissions::from_mode(0o755)).unwrap();
        };
        let central = root.join("runtimes/central/central");
        executable(
            &central,
            &FAKE_CENTRAL
                .replace("{dir}", &root.display().to_string())
                .replace("{token}", WIRING_TOKEN),
        );
        let node = root.join("node/bin/node");
        for name in ["node", "npm", "npx"] {
            executable(&root.join("node/bin").join(name), "#!/bin/sh\n");
        }
        let runtime = |name: &str, version: Option<&str>| {
            let command = root.join(format!("runtimes/{name}/{name}"));
            if name != "central" {
                executable(&command, "#!/bin/sh\n");
            }
            CliRuntime {
                name: name.to_owned(),
                command: vec![command.display().to_string()],
                identity: format!("{name}-1"),
                version: version.map(str::to_owned),
            }
        };
        let declared = vec![
            runtime("codex", None),
            runtime("pi", None),
            runtime("central", Some("1.12.1-alpha.410")),
            runtime("claude", Some("2.1.288")),
            runtime("junie", Some("3596.1")),
        ];
        Self {
            directory,
            root,
            account,
            secrets,
            node,
            declared,
        }
    }

    fn variable(&self, name: &str) -> Option<String> {
        match name {
            RUN_SECRETS_VARIABLE => Some(self.secrets.display().to_string()),
            "HOME" => Some(self.account.display().to_string()),
            "NODE_BIN" => Some(self.node.display().to_string()),
            _ => None,
        }
    }

    fn start(&self, declared: &[CliRuntime]) -> Result<LiveRun, AgentRefusal> {
        LiveRun::start(&context(&self.root), declared, &|name| self.variable(name), true)
    }

    fn prepared(&self) -> IdePrepared {
        let context = self.root.join("ide/air-ui-live-1");
        let child = |name: &str| context.join(name).display().to_string();
        let project = PathBuf::from(context_project(&self.root));
        fs::create_dir_all(&project).unwrap();
        IdePrepared {
            context_dir: context.display().to_string(),
            config_dir: child("config"),
            system_dir: child("system"),
            plugins_dir: child("plugins"),
            project_dir: project.display().to_string(),
            home_dir: child("home"),
            bin_dir: child("bin"),
        }
    }

    fn calls(&self) -> Vec<String> {
        fs::read_to_string(self.root.join("central-calls.log"))
            .map(|text| {
                text.lines()
                    .map(|line| line.split(" HOME=").next().unwrap_or_default().to_owned())
                    .collect()
            })
            .unwrap_or_default()
    }
}

fn context_project(root: &Path) -> String {
    root.join("air-ui-live/project/plain-project").display().to_string()
}

// A host and a `lane-run` have no marker of the controller, so the live lane names the command that runs it in a guest.
#[test]
fn a_live_lane_outside_a_live_run_is_refused_by_name() {
    let fixture = Fixture::new();
    let refusal = LiveRun::start(
        &context(&fixture.root),
        &fixture.declared,
        &|name| (name == "HOME").then(|| fixture.account.display().to_string()),
        true,
    )
    .unwrap_err();

    assert_eq!(refusal.code, LIVE_INPUT_CODE);
    assert!(
        refusal.message.contains("runs only in the lane daemon of a Linux guest"),
        "{}",
        refusal.message
    );
    assert!(
        refusal
            .message
            .contains("`central login export | vm.cmd run --lane ui-live --test-env AIR_LIVE_CENTRAL_LOGIN=@-`"),
        "{}",
        refusal.message
    );
}

// A descriptor of another lane has none of the live runtimes. The refusal names the descriptor of the live lane, which
// has them, and the command that builds it.
#[test]
fn a_descriptor_without_the_live_runtimes_is_refused_naming_the_live_descriptor() {
    let fixture = Fixture::new();

    let refusal = fixture.start(&fixture.declared[..2]).unwrap_err();

    assert_eq!(refusal.code, LIVE_INPUT_CODE);
    assert_eq!(
        refusal.message,
        "the live lane needs the declared CLI runtimes central, claude and junie, and the runtime descriptor lacks central, \
         claude and junie; run it over //plugins/air/tests/integration/ui:ui_daemon_linux_guest_live, which \
         `vm.cmd run --lane ui-live` builds"
    );
    let partial = fixture.start(&fixture.declared[..4]).unwrap_err();
    assert!(
        partial
            .message
            .contains("lacks junie; run it over //plugins/air/tests/integration/ui:ui_daemon_linux_guest_live"),
        "{}",
        partial.message
    );
}

// SMOKE-B over fake CLIs: a login that the Central CLI rejects fails the run at the import with the code and the
// prefix that the window compares literally. Nothing runs after the import, the end still clears the account, and no
// text names the login.
#[test]
fn a_rejected_login_fails_at_the_import_with_the_literal_prefix_and_the_end_still_clears_the_account() {
    let fixture = Fixture::new();
    let login = fixture.secrets.join("AIR_LIVE_CENTRAL_LOGIN");
    fs::write(&login, format!("{{\n  \"session\": \"{LOGIN_TOKEN}\"\n}}\n")).unwrap();
    fs::write(fixture.root.join("fail-import"), "").unwrap();
    let sweeps = Arc::new(Mutex::new(Vec::new()));
    let live = fixture
        .start(&fixture.declared)
        .unwrap()
        .with_agent_processes(Box::new(Sweeps(sweeps.clone())));

    let refusal = live.provision().unwrap_err();
    let mut notes = live.take_notes();
    live.end().unwrap();
    notes.extend(live.take_notes());

    assert_eq!(refusal.code, central::CENTRAL_CODE);
    assert!(
        refusal
            .message
            .starts_with("central provisioning failed at `central login import`: exit 4; stderr: rejected "),
        "{}",
        refusal.message
    );
    assert!(!refusal.message.contains(LOGIN_TOKEN), "{}", refusal.message);
    assert!(!login.exists(), "the login input is used once");
    assert_eq!(
        fixture.calls(),
        [
            "config set release-track 1.12.1-alpha.410",
            "config set google-analytics off",
            "config set port 19516",
            "login import"
        ],
        "nothing runs after the import, and an account without the Central state needs no proxy stop"
    );
    assert_eq!(sweeps.lock().unwrap().len(), 2, "the provisioning and the end each sweep once");
    assert!(!fixture.account.join(".jetbrains-central").exists());
    let text = notes.join("\n");
    assert!(!text.contains(LOGIN_TOKEN), "a note names the login: {text}");
}

// SMOKE-0: one live iteration over fake CLIs. It provisions the account, seeds the lane home and the bin directory,
// refreshes nothing, finds the Junie leftovers of the live project, and at its end deletes the copies and tears the
// account down. No note holds the login or the wiring, and the login file is gone.
#[test]
fn one_live_iteration_provisions_seeds_and_tears_down_with_fake_clis() {
    let fixture = Fixture::new();
    let login = fixture.secrets.join("AIR_LIVE_CENTRAL_LOGIN");
    fs::write(&login, format!("{{\n  \"session\": \"{LOGIN_TOKEN}\"\n}}\n")).unwrap();
    let sweeps = Arc::new(Mutex::new(Vec::new()));
    let live = fixture
        .start(&fixture.declared)
        .unwrap()
        .with_agent_processes(Box::new(Sweeps(sweeps.clone())));

    live.provision().unwrap();
    assert!(!login.exists(), "the login input is used once");
    let prepared = fixture.prepared();
    live.seed(&prepared).unwrap();
    live.seed(&prepared).unwrap();
    live.refresh().unwrap();
    let index = fixture.account.join(".junie/sessions/index.jsonl");
    fs::write(
        &index,
        format!(
            "{{\"sessionId\":\"left-1\",\"projectDir\":\"{}\"}}\n",
            context_project(&fixture.root)
        ),
    )
    .unwrap();
    let leftovers = live.leftover_junie_sessions();
    let mut notes = live.take_notes();
    live.end().unwrap();
    live.end().unwrap();
    notes.extend(live.take_notes());

    assert_eq!(
        fixture.calls(),
        [
            "config set release-track 1.12.1-alpha.410",
            "config set google-analytics off",
            "config set port 19516",
            "login import",
            "proxy start",
            "add codex",
            "add claude-code",
            "add junie",
            "status",
            "proxy stop",
        ],
        "one provisioning, one teardown"
    );
    assert_eq!(leftovers, ["left-1"]);
    let bin = Path::new(&prepared.bin_dir);
    assert_eq!(
        fs::read_to_string(bin.join("claude")).unwrap(),
        format!(
            "#!/bin/sh\nexport DISABLE_AUTOUPDATER='1'\nexec '{}' \"$@\"\n",
            fixture.root.join("runtimes/claude/claude").display()
        )
    );
    assert!(bin.join("junie").is_file() && bin.join("central").is_file() && bin.join("npm").exists());
    let home = Path::new(&prepared.home_dir);
    for copy in [".claude/settings.json", ".codex/config.toml", ".claude.json"] {
        assert!(!home.join(copy).exists(), "the end deletes the copy {copy}");
    }
    for entry in [".jetbrains-central", ".claude", ".codex", ".junie"] {
        assert!(!fixture.account.join(entry).exists(), "the teardown deletes {entry} of the account");
    }
    assert_eq!(sweeps.lock().unwrap().len(), 2, "the provisioning and the end each sweep once");
    let text = notes.join("\n");
    assert!(
        text.contains("central provisioning: login import ok, proxy :19516, add codex|claude-code|junie ok"),
        "{text}"
    );
    assert!(
        text.contains("central: wiring copied from the account configs (claude: env keys 2"),
        "{text}"
    );
    assert!(text.contains("deleted the credential copies of the lane home"), "{text}");
    for secret in [LOGIN_TOKEN, WIRING_TOKEN] {
        assert!(!text.contains(secret), "a note names a secret: {text}");
    }
    assert!(format!("{live:?}").contains("account_home") && !format!("{live:?}").contains(LOGIN_TOKEN));
    drop(fixture.directory);
}

// The live lane runs the real agents on the Central proxy. So its context names no fake registry, no feedback capture
// and no Codex mock, and it writes no `acp.json` and no control file. The Codex configuration of the lane home is the
// wiring of the account, and the bin directory holds the launchers of the declared and of the live runtimes.
#[test]
fn a_live_context_names_no_fake_registry_and_no_codex_mock() {
    let fixture = Fixture::new();
    fs::write(
        fixture.secrets.join("AIR_LIVE_CENTRAL_LOGIN"),
        format!("{{\"session\": \"{LOGIN_TOKEN}\"}}"),
    )
    .unwrap();
    let live = fixture
        .start(&fixture.declared)
        .unwrap()
        .with_agent_processes(Box::new(Sweeps(Arc::default())));
    live.provision().unwrap();
    let fixture_document = avl_testkit::crate_path!("testdata/lane-context/ui.json");
    let mut document = parse_lane_document(&fs::read(fixture_document).unwrap()).unwrap();
    document.lane = LIVE_LANE.to_owned();
    document.agents.clear();
    document.registry_agents.clear();
    document.requirements.clear();
    document.services = BTreeMap::from([("jetBrainsCloud".to_owned(), CLOUD_IDENTITY.to_owned())]);
    document.live = Some(context(&fixture.root));
    let prepared = fixture.prepared();
    for directory in [
        &prepared.config_dir,
        &prepared.system_dir,
        &prepared.plugins_dir,
        &prepared.home_dir,
        &prepared.bin_dir,
    ] {
        fs::create_dir_all(directory).unwrap();
    }
    let identity = LaneIdentity::new("live-lane-key", 27_100);
    let java = Path::new(FIXTURE_JAVA);
    let jar = fixture_jar();
    let declared = &fixture.declared[..2];
    let context = |live| LaneContext {
        document: &document,
        identity: &identity,
        java,
        fake_agent_jar: &jar,
        runtimes: declared,
        live,
    };

    let refusal = context(None).write(&prepared).unwrap_err();
    let ports = context(Some(&live)).write(&prepared).unwrap();
    let home = Path::new(&prepared.home_dir);
    let codex_config = fs::read_to_string(home.join(CODEX_CONFIG)).unwrap();
    let mut launchers: Vec<String> = fs::read_dir(&prepared.bin_dir)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    launchers.sort();
    live.end().unwrap();

    assert!(refusal.message.contains("launches only in a live run"), "{}", refusal.message);
    assert_eq!((ports.registry, ports.feedback, ports.codex), (None, None, None));
    let bridge = Path::new(&prepared.config_dir).join(BRIDGE_DIR);
    let services: Value = serde_json::from_slice(&fs::read(bridge.join(LANE_SERVICES_FILE)).unwrap()).unwrap();
    assert_eq!(
        (&services["registry"], &services["feedback"], &services["codex"]),
        (&Value::Null, &Value::Null, &Value::Null)
    );
    assert!(services["cloud"].is_string(), "{services}");
    assert!(!bridge.join(CONTROL_FILE).exists() && !home.join(ACP_JSON).exists());
    assert!(
        codex_config.contains("127.0.0.1:19516/wire/codex"),
        "the Codex wiring of the account"
    );
    assert!(!codex_config.contains(&format!(":{}", codex::PORT)), "no Codex mock");
    for name in ["central", "claude", "codex", "junie", "pi"] {
        assert!(
            launchers.iter().any(|launcher| launcher == name),
            "the launcher {name}: {launchers:?}"
        );
    }
}
