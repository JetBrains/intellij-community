//! The lane home of a live run over a temporary guest account that `central add` wired: the copy of the wiring and
//! nothing else, the seeds of Claude, Codex and Junie, the Central guard, the launchers, and the removal of the copies.
//!
//! The fake wiring of the account holds a token-like value, [`FAKE_TOKEN`]. No note and no refusal may name it.

use std::collections::BTreeMap;
use std::fs;
use std::os::unix::fs::{PermissionsExt, symlink};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, SystemTime};

use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;

/// A token-like value of the fake account wiring, which no note and no refusal may name.
const FAKE_TOKEN: &str = "faketokenfaketokenfaketokenfake";

/// The Claude base URL that `central add claude-code` writes: the local proxy on the pinned port.
const LANE_PROXY: &str = "http://127.0.0.1:19516/wire/abc/claude";

/// What `central add` wrote into a guest account, with unrelated settings beside the wiring: a permission, an MCP
/// server, a model and a project trust of the account, none of which may reach the lane.
struct Wiring {
    claude_env: Vec<(&'static str, String)>,
    key_helper: bool,
    codex: bool,
    provider: &'static str,
}

impl Default for Wiring {
    fn default() -> Self {
        Self {
            claude_env: vec![
                ("ANTHROPIC_BASE_URL", LANE_PROXY.to_owned()),
                ("ANTHROPIC_AUTH_TOKEN", FAKE_TOKEN.to_owned()),
                ("OTHER", "host-only".to_owned()),
            ],
            key_helper: true,
            codex: true,
            provider: "\"central\"",
        }
    }
}

struct Fixture {
    directory: tempfile::TempDir,
    account: PathBuf,
    lane: PathBuf,
    project: PathBuf,
}

impl Fixture {
    fn new() -> Self {
        Self::wired(&Wiring::default())
    }

    fn wired(wiring: &Wiring) -> Self {
        let directory = tempfile::tempdir().unwrap();
        let root = real_path(directory.path()).unwrap();
        let fixture = Self {
            account: root.join("account"),
            lane: root.join("context/home"),
            project: root.join("air-ui-live/project"),
            directory,
        };
        fs::create_dir_all(&fixture.project).unwrap();
        wire(&fixture.account, wiring);
        fixture
    }

    fn home(&self) -> LiveHome<'_> {
        LiveHome {
            account_home: &self.account,
            lane_home: &self.lane,
            project_dir: &self.project,
            central_port: 19516,
        }
    }

    fn seed(&self) -> (Result<WiringStamps, AgentRefusal>, Vec<String>) {
        let mut notes = Vec::new();
        let seeded = self.home().seed(&mut |line| notes.push(line.to_owned()));
        (seeded, notes)
    }

    fn lane_text(&self, file: &str) -> String {
        fs::read_to_string(self.lane.join(file)).unwrap()
    }

    fn lane_json(&self, file: &str) -> Value {
        serde_json::from_str(&self.lane_text(file)).unwrap()
    }
}

fn wire(account: &Path, wiring: &Wiring) {
    fs::create_dir_all(account.join(".claude")).unwrap();
    let env = wiring
        .claude_env
        .iter()
        .map(|(key, value)| format!("\"{key}\": \"{value}\""))
        .collect::<Vec<_>>()
        .join(", ");
    let helper = if wiring.key_helper {
        ", \"apiKeyHelper\": \"central token claude\""
    } else {
        ""
    };
    fs::write(
        account.join(".claude/settings.json"),
        format!("{{\"env\": {{{env}}}, \"permissions\": {{\"allow\": [\"host-only\"]}}{helper}}}"),
    )
    .unwrap();
    fs::create_dir_all(account.join(".codex")).unwrap();
    let name = wiring.provider.trim_matches(['"', '\'']);
    let provider = if wiring.codex {
        format!(
            "model_provider = {}\n\n[model_providers.{name}]\nname = \"JetBrains Central\"\nbase_url = \"http://127.0.0.1:19516/wire/abc/codex\"\n\
             experimental_bearer_token = \"{FAKE_TOKEN}\"\n\n[model_providers.{name}.http_headers]\nX-Wire = \"abc\"\n",
            wiring.provider
        )
    } else {
        String::new()
    };
    fs::write(
        account.join(".codex/config.toml"),
        format!("model = \"gpt-host\"\n{provider}\n[mcp_servers.x]\ncommand = \"host-only\"\n\n[projects.\"/somewhere\"]\ntrust_level = \"trusted\"\n"),
    )
    .unwrap();
}

fn mode(path: &Path) -> u32 {
    fs::metadata(path).unwrap().permissions().mode() & 0o777
}

fn strings(value: &Value) -> Vec<String> {
    value
        .as_array()
        .unwrap()
        .iter()
        .map(|item| item.as_str().unwrap().to_owned())
        .collect()
}

// Recipe C copies the wiring sections alone. A file of the lane home that carries a token is readable by its owner only,
// and no note names a value of the wiring.
#[test]
fn a_central_launch_copies_only_the_wiring_keeps_the_trust_seed_and_writes_no_vendor_key() {
    let fixture = Fixture::new();

    let (seeded, notes) = fixture.seed();

    seeded.unwrap();
    for file in [CLAUDE_SETTINGS, CODEX_CONFIG, CLAUDE_GLOBAL] {
        assert_eq!(mode(&fixture.lane.join(file)), 0o600, "{file} carries a token");
    }
    let settings = fixture.lane_json(CLAUDE_SETTINGS);
    let keys: Vec<&String> = settings.as_object().unwrap().keys().collect();
    assert_eq!(keys, ["apiKeyHelper", "env"]);
    let env: Vec<&String> = settings["env"].as_object().unwrap().keys().collect();
    assert_eq!(env, ["ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL"]);
    assert_eq!(
        settings["apiKeyHelper"],
        format!("HOME='{}' central token claude", fixture.account.display()),
        "the helper asks the Central CLI, whose login is bound to the account home"
    );
    let config = fixture.lane_text(CODEX_CONFIG);
    assert!(config.starts_with("model_provider = \"central\""), "{config}");
    for part in [
        "[model_providers.central]",
        "base_url = \"http://127.0.0.1:19516/wire/abc/codex\"",
        "[model_providers.central.http_headers]",
        "trust_level = \"trusted\"",
    ] {
        assert!(config.contains(part), "{part} is missing from {config}");
    }
    assert!(config.contains(&fixture.project.display().to_string()), "{config}");
    for part in ["host-only", "gpt-host", "/somewhere"] {
        assert!(!config.contains(part), "nothing but the wiring is copied: {config}");
    }
    assert!(fixture.lane.join(CLAUDE_GLOBAL).exists(), "the onboarding seed stays");
    for absent in [CLAUDE_CREDENTIALS, CENTRAL_STATE, JUNIE_KEY] {
        assert!(fixture.lane.join(absent).symlink_metadata().is_err(), "{absent}");
    }
    assert_eq!(
        notes,
        ["central: wiring copied from the account configs (claude: env keys 2 and the key helper, codex: provider central)"]
    );
    assert!(!notes.join("\n").contains(FAKE_TOKEN));
    assert!(
        fs::read_to_string(fixture.account.join(CODEX_CONFIG))
            .unwrap()
            .contains("host-only"),
        "the account config is only read"
    );
}

// `central add codex` writes the provider name as a TOML literal string.
#[test]
fn a_provider_named_in_a_literal_string_is_copied() {
    let fixture = Fixture::wired(&Wiring {
        provider: "'wire'",
        ..Wiring::default()
    });

    let (seeded, notes) = fixture.seed();

    seeded.unwrap();
    let config = fixture.lane_text(CODEX_CONFIG);
    assert!(
        config.starts_with("model_provider = 'wire'") && config.contains("[model_providers.wire]"),
        "{config}"
    );
    assert!(notes[0].ends_with("codex: provider wire)"), "{notes:?}");
}

// The seed of a later iteration finds its own wiring in place.
#[test]
fn a_second_seed_keeps_one_provider_line() {
    let fixture = Fixture::new();
    fixture.seed().0.unwrap();

    fixture.seed().0.unwrap();

    let config = fixture.lane_text(CODEX_CONFIG);
    assert_eq!(
        config.lines().filter(|line| line.starts_with("model_provider")).count(),
        1,
        "{config}"
    );
}

// A run that never saw its Junie session, or that was killed, leaves it in the session index of the account.
#[test]
fn only_the_junie_sessions_of_the_lane_project_are_leftovers() {
    let fixture = Fixture::new();
    let root = fixture.project.parent().unwrap();
    let index = fixture.account.join(JUNIE_INDEX);
    fs::create_dir_all(index.parent().unwrap()).unwrap();
    let elsewhere = fixture.directory.path().join("elsewhere");
    let sibling = fixture.directory.path().join("air-ui-live-other");
    fs::write(
        &index,
        [
            format!(
                "{{\"sessionId\":\"session-261003-132728-7mjn\",\"projectDir\":\"{}\"}}",
                root.join("project/plain").display()
            ),
            format!("{{\"sessionId\":\"foreign\",\"projectDir\":\"{}\"}}", elsewhere.display()),
            format!("{{\"sessionId\":\"sibling\",\"projectDir\":\"{}\"}}", sibling.display()),
            "not json".to_owned(),
            "{\"sessionId\":\"no-project\"}".to_owned(),
            String::new(),
        ]
        .join("\n"),
    )
    .unwrap();

    assert_eq!(leftover_junie_sessions(&fixture.account, root), ["session-261003-132728-7mjn"]);
    assert_eq!(
        leftover_junie_sessions(&fixture.directory.path().join("absent"), root),
        Vec::<String>::new()
    );
}

// Junie writes its sessions into the account home whatever `HOME` says, and Air reads the lane home. The seed creates
// the target, so the link never dangles, and the close removes the link, never the target.
#[test]
fn the_seed_links_the_junie_sessions_of_the_account_to_a_target_it_creates_and_the_close_unlinks_them() {
    let fixture = Fixture::new();

    fixture.seed().0.unwrap();

    let link = fixture.lane.join(JUNIE_SESSIONS);
    let target = fixture.account.join(JUNIE_SESSIONS);
    assert!(target.is_dir());
    assert_eq!(fs::read_link(&link).unwrap(), target);
    fs::write(target.join("index.jsonl"), "{}\n").unwrap();
    assert!(fixture.home().remove_copies(&mut |_| {}).contains(&link));
    assert!(link.symlink_metadata().is_err(), "the close removes the link");
    assert!(target.join("index.jsonl").exists());
}

#[test]
fn a_real_junie_sessions_directory_in_the_lane_home_is_refused() {
    let fixture = Fixture::new();
    fs::create_dir_all(fixture.lane.join(JUNIE_SESSIONS)).unwrap();

    let refusal = fixture.seed().0.unwrap_err();

    assert!(
        refusal.message.contains(".junie/sessions exists and is not a link"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("Delete it"), "{}", refusal.message);
}

/// Runs the guard `guard` with `home` as `HOME`, and answers its exit code and its output with the errors merged in.
fn run_guard(guard: &Path, home: &Path, arguments: &[&str]) -> (i32, String) {
    let output = Command::new("/bin/sh")
        .arg(guard)
        .args(arguments)
        .env("HOME", home)
        .output()
        .unwrap();
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    (output.status.code().unwrap(), text)
}

// An agent hook ran `central proxy start` under the lane home on 2026-10-03, which opened a browser login. The guard
// passes the reads on with the account home, and answers every other subcommand with 0, so a hook neither blocks nor
// retries.
#[test]
fn the_central_guard_passes_reads_to_the_central_cli_with_the_account_home_and_refuses_the_rest() {
    let fixture = Fixture::new();
    let calls = fixture.directory.path().join("real-central-calls.log");
    let real = fixture.directory.path().join("real-central");
    fs::write(&real, format!("#!/bin/sh\necho \"$* HOME=$HOME\" >> '{}'\n", calls.display())).unwrap();
    fs::set_permissions(&real, fs::Permissions::from_mode(0o755)).unwrap();
    let bin = fixture.directory.path().join("context/bin");

    write_bin(&bin, &[], &real, &fixture.account, Path::new("/nonexistent/node")).unwrap();

    for name in CENTRAL_NAMES {
        assert_eq!(run_guard(&bin.join(name), &fixture.lane, &["status"]).0, 0, "{name}");
    }
    let refused = run_guard(
        &bin.join("central"),
        &fixture.lane,
        &["proxy", "start", "--ensure-updated", "--caller", "agent"],
    );
    let login = run_guard(&bin.join("central"), &fixture.lane, &["login"]);
    let home = fixture.account.display();
    assert_eq!(
        fs::read_to_string(&calls).unwrap(),
        format!("status HOME={home}\nstatus HOME={home}\nstatus HOME={home}\n"),
        "only the reads reach the Central CLI, with the account home"
    );
    assert_eq!(refused.0, 0, "an agent hook neither blocks nor retries");
    assert!(
        refused
            .1
            .contains("[ui-live] central proxy start --ensure-updated --caller agent is not run from the lane"),
        "{}",
        refused.1
    );
    assert!(login.1.contains("is not run from the lane"), "{}", login.1);
    assert!(
        fixture.lane.join(CENTRAL_STATE).symlink_metadata().is_err(),
        "no Central state under the lane home"
    );
}

// A Central state below the lane home holds a token of the account, so the close deletes it.
#[test]
fn the_close_deletes_the_state_of_a_central_instance_that_ran_under_the_lane_home() {
    let fixture = Fixture::new();
    let state = fixture.lane.join(CENTRAL_STATE);
    fs::create_dir_all(state.join("agents")).unwrap();
    fs::write(state.join("tokens.enc"), "fake").unwrap();
    fs::write(state.join("agents/plugin-state-codex.json"), "fake").unwrap();
    let mut notes = Vec::new();

    let removed = fixture.home().remove_copies(&mut |line| notes.push(line.to_owned()));

    assert!(
        removed.contains(&state.join("tokens.enc")) && removed.contains(&state.join("agents/plugin-state-codex.json")),
        "{removed:?}"
    );
    assert!(state.symlink_metadata().is_err(), "the state goes whole");
    assert!(
        notes[0].starts_with("WARNING: a Central instance ran under the lane HOME"),
        "{notes:?}"
    );
}

// A proxy restart rewrites the account wiring with a new secret, and the next scenario must not use the old one.
#[test]
fn a_refresh_copies_the_wiring_again_only_after_the_account_re_wired() {
    let fixture = Fixture::new();
    let mut stamps = fixture.seed().0.unwrap();
    let mut notes = Vec::new();

    assert!(
        !fixture
            .home()
            .refresh(&mut stamps, &mut |line| notes.push(line.to_owned()))
            .unwrap(),
        "nothing changed"
    );

    let later = SystemTime::now() + Duration::from_secs(60);
    for file in [CLAUDE_SETTINGS, CODEX_CONFIG] {
        let path = fixture.account.join(file);
        fs::write(&path, fs::read_to_string(&path).unwrap().replace("/wire/abc/", "/wire/new/")).unwrap();
        fs::File::options().write(true).open(&path).unwrap().set_modified(later).unwrap();
    }
    assert!(
        fixture
            .home()
            .refresh(&mut stamps, &mut |line| notes.push(line.to_owned()))
            .unwrap()
    );

    assert!(
        notes[0].starts_with("Central re-wired the account at ") && notes[0].contains("; the lane wiring is refreshed"),
        "{notes:?}"
    );
    assert!(!notes.join("\n").contains(FAKE_TOKEN));
    let settings = fixture.lane_text(CLAUDE_SETTINGS);
    assert!(settings.contains("/wire/new/") && !settings.contains("/wire/abc/"), "{settings}");
    let config = fixture.lane_text(CODEX_CONFIG);
    assert!(config.contains("/wire/new/") && !config.contains("/wire/abc/"), "{config}");
    assert!(config.contains(&fixture.project.display().to_string()), "{config}");
    assert_eq!(config.lines().filter(|line| line.starts_with("model_provider")).count(), 1);
    assert_eq!(mode(&fixture.lane.join(CLAUDE_SETTINGS)), 0o600);
    assert!(
        !fixture.home().refresh(&mut stamps, &mut |_| {}).unwrap(),
        "the refreshed copy is current"
    );
}

// Claude asks whether to use a key that it finds, so a copied key is approved the way Claude records it. No note names
// the key or the approved part of it.
#[test]
fn a_copied_claude_key_is_approved_in_the_onboarding_seed_and_no_note_names_it() {
    let key = format!("fake-key-{}", "k".repeat(40));
    let fixture = Fixture::wired(&Wiring {
        claude_env: vec![("ANTHROPIC_BASE_URL", LANE_PROXY.to_owned()), ("ANTHROPIC_API_KEY", key.clone())],
        ..Wiring::default()
    });

    let (seeded, notes) = fixture.seed();

    seeded.unwrap();
    let suffix = &key[key.len() - 20..];
    assert_eq!(
        strings(&fixture.lane_json(CLAUDE_GLOBAL)["customApiKeyResponses"]["approved"]),
        [suffix]
    );
    assert!(!notes.join("\n").contains(suffix), "{notes:?}");
}

// A refusal names the file and the `central add` that wires it, never a value of the file.
#[test]
fn an_account_whose_claude_is_not_wired_fails_the_seed() {
    let fixture = Fixture::wired(&Wiring {
        claude_env: vec![("OTHER", FAKE_TOKEN.to_owned())],
        key_helper: false,
        ..Wiring::default()
    });

    let refusal = fixture.seed().0.unwrap_err();

    assert!(
        refusal.message.contains("Claude is not wired to Central in the guest account"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("central add claude-code"), "{}", refusal.message);
    assert!(!refusal.message.contains(FAKE_TOKEN));
}

#[test]
fn an_account_whose_codex_is_not_wired_fails_the_seed() {
    let fixture = Fixture::wired(&Wiring {
        codex: false,
        ..Wiring::default()
    });

    let refusal = fixture.seed().0.unwrap_err();

    assert!(
        refusal.message.contains("Codex is not wired to Central in the guest account"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("central add codex"), "{}", refusal.message);
}

#[test]
fn a_copied_wiring_that_names_no_local_proxy_fails_the_seed() {
    let fixture = Fixture::wired(&Wiring {
        claude_env: vec![("ANTHROPIC_BASE_URL", "https://central.example/wire".to_owned())],
        key_helper: false,
        ..Wiring::default()
    });

    let refusal = fixture.seed().0.unwrap_err();

    assert_eq!(
        refusal.message,
        "the copied Central wiring in the lane's .claude/settings.json names no local proxy on port 19516; check \
         ~/.claude/settings.json of the guest account"
    );
}

#[test]
fn the_end_of_a_run_deletes_the_copied_wiring_and_the_account_keeps_its_own() {
    let fixture = Fixture::new();
    fixture.seed().0.unwrap();
    fs::write(fixture.lane.join(CODEX_AUTH), "fake").unwrap();

    let removed = fixture.home().remove_copies(&mut |_| {});

    for file in [CLAUDE_SETTINGS, CODEX_CONFIG, CLAUDE_GLOBAL, CODEX_AUTH] {
        assert!(removed.contains(&fixture.lane.join(file)), "{file} in {removed:?}");
        assert!(!fixture.lane.join(file).exists(), "{file}");
    }
    assert!(fixture.account.join(CLAUDE_SETTINGS).exists() && fixture.account.join(CODEX_CONFIG).exists());
}

// A file that a link reaches outside the lane home is the account's own, and the close never deletes it.
#[test]
fn the_close_never_deletes_a_file_that_a_link_reaches() {
    let fixture = Fixture::new();
    fs::create_dir_all(&fixture.lane).unwrap();
    let elsewhere = fixture.directory.path().join("elsewhere");
    fs::create_dir_all(&elsewhere).unwrap();
    fs::write(elsewhere.join("auth.json"), "the account's").unwrap();
    symlink(&elsewhere, fixture.lane.join(".codex")).unwrap();

    let removed = fixture.home().remove_copies(&mut |_| {});

    assert!(!removed.contains(&fixture.lane.join(CODEX_AUTH)), "{removed:?}");
    assert_eq!(fs::read_to_string(elsewhere.join("auth.json")).unwrap(), "the account's");
}

#[test]
fn the_claude_seed_completes_the_onboarding_and_trusts_the_project() {
    let fixture = Fixture::new();

    fixture.seed().0.unwrap();

    let seed = fixture.lane_json(CLAUDE_GLOBAL);
    assert_eq!(seed["hasCompletedOnboarding"], true);
    let trust = &seed["projects"][fixture.project.display().to_string()];
    assert_eq!(
        (&trust["hasTrustDialogAccepted"], &trust["hasCompletedProjectOnboarding"]),
        (&Value::Bool(true), &Value::Bool(true))
    );
    assert!(seed.get("customApiKeyResponses").is_none());
}

// A project below a directory with a `.mcp.json` finds its servers, and Claude would ask about them before anything else.
#[test]
fn the_claude_seed_declines_the_servers_of_an_enclosing_mcp_json() {
    let fixture = Fixture::new();
    let checkout = fixture.project.parent().unwrap();
    fs::write(
        checkout.join(".mcp.json"),
        "{\"mcpServers\": {\"Glean\": {\"url\": \"x\"}, \"ijproxy\": {\"command\": \"y\"}}}",
    )
    .unwrap();

    fixture.seed().0.unwrap();

    let projects = fixture.lane_json(CLAUDE_GLOBAL)["projects"].clone();
    for key in [fixture.project.display().to_string(), checkout.display().to_string()] {
        assert_eq!(strings(&projects[&key]["disabledMcpjsonServers"]), ["Glean", "ijproxy"], "{key}");
        assert_eq!(projects[&key]["enabledMcpjsonServers"], serde_json::json!([]), "{key}");
    }
}

#[test]
fn the_claude_seed_keeps_a_global_config_the_agent_already_wrote() {
    let fixture = Fixture::new();
    fs::create_dir_all(&fixture.lane).unwrap();
    fs::write(fixture.lane.join(CLAUDE_GLOBAL), "{\"written\": \"by claude\"}").unwrap();

    fixture.seed().0.unwrap();

    assert_eq!(fixture.lane_text(CLAUDE_GLOBAL), "{\"written\": \"by claude\"}");
}

// On its first run Junie offers to import the Claude Code configuration that `central add` wrote, as a full-screen step
// before the folder trust question. The seed marks the step done for the account and the project, once.
#[test]
fn the_seed_marks_the_first_run_import_of_junie_done_and_never_overwrites_it() {
    let fixture = Fixture::new();
    let state = fixture.account.join(JUNIE_MIGRATION_STATE);

    fixture.seed().0.unwrap();

    let written: Value = serde_json::from_str(&fs::read_to_string(&state).unwrap()).unwrap();
    assert_eq!(strings(&written["migratedProjects"]), [fixture.project.display().to_string()]);
    fs::write(&state, "{\"migratedProjects\":[\"/elsewhere\"]}").unwrap();
    fixture.seed().0.unwrap();
    assert_eq!(
        fs::read_to_string(&state).unwrap(),
        "{\"migratedProjects\":[\"/elsewhere\"]}",
        "a marker that Junie wrote stays"
    );
}

#[test]
fn a_lane_home_that_is_the_account_home_is_refused() {
    let fixture = Fixture::new();
    let home = LiveHome {
        lane_home: &fixture.account,
        ..fixture.home()
    };

    let refusal = home.seed(&mut |_| {}).unwrap_err();

    assert!(refusal.message.contains("is the guest account home"), "{}", refusal.message);
    assert!(
        fixture.account.join(CLAUDE_SETTINGS).exists(),
        "nothing was copied over the wiring of the account"
    );
}

// The lane IDE inherits no variable, so a variable that a CLI needs is an export of its launcher. The Node executables
// of the guest are links, and a name that the bin directory holds already stays.
#[test]
fn the_launchers_export_their_environment_and_the_node_executables_are_linked() {
    let fixture = Fixture::new();
    let node_dir = fixture.directory.path().join("node/bin");
    fs::create_dir_all(&node_dir).unwrap();
    for name in ["node", "npm", "npx"] {
        fs::write(node_dir.join(name), "#!/bin/sh\n").unwrap();
    }
    let bin = fixture.directory.path().join("context/bin");
    fs::create_dir_all(&bin).unwrap();
    fs::write(bin.join("npx"), "kept").unwrap();
    let claude = ["/runtimes/claude/claude".to_owned()];
    let environment = BTreeMap::from([("DISABLE_AUTOUPDATER".to_owned(), "1".to_owned())]);
    let none = BTreeMap::new();
    let junie = ["/runtimes/junie/bin/junie".to_owned()];
    let launchers = [
        Launcher {
            name: "claude",
            command: &claude,
            environment: &environment,
        },
        Launcher {
            name: "junie",
            command: &junie,
            environment: &none,
        },
    ];

    write_bin(
        &bin,
        &launchers,
        Path::new("/runtimes/central/central"),
        &fixture.account,
        &node_dir.join("node"),
    )
    .unwrap();

    assert_eq!(
        fs::read_to_string(bin.join("claude")).unwrap(),
        "#!/bin/sh\nexport DISABLE_AUTOUPDATER='1'\nexec '/runtimes/claude/claude' \"$@\"\n"
    );
    assert_eq!(
        fs::read_to_string(bin.join("junie")).unwrap(),
        "#!/bin/sh\nexec '/runtimes/junie/bin/junie' \"$@\"\n"
    );
    assert_eq!(fs::read_link(bin.join("node")).unwrap(), node_dir.join("node"));
    assert_eq!(fs::read_link(bin.join("npm")).unwrap(), node_dir.join("npm"));
    assert_eq!(fs::read_to_string(bin.join("npx")).unwrap(), "kept");
}

// A real agent reads the instructions and the configuration of every directory above its project.
#[test]
fn a_project_below_agent_instructions_is_refused() {
    let fixture = Fixture::new();
    require_isolated_project(&fixture.project).unwrap();
    fs::write(fixture.project.parent().unwrap().join("AGENTS.md"), "read me").unwrap();

    let refusal = require_isolated_project(&fixture.project).unwrap_err();

    assert!(
        refusal.message.contains("sits below agent instructions or configuration"),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("AGENTS.md"), "{}", refusal.message);
}

#[test]
fn the_codex_trust_replaces_an_entry_of_the_project_and_keeps_the_others() {
    let project = PathBuf::from("/tmp/air-ui-live/project");
    let config = "model = \"x\"\n\n[projects]\n\"/tmp/air-ui-live/project\" = { trust_level = \"untrusted\" }\n\"/other\" = { trust_level = \"trusted\" }\n\n[tools]\nweb = true\n";

    let trusted = with_trusted_directories(config, std::slice::from_ref(&project));

    assert_eq!(
        trusted,
        "model = \"x\"\n\n[projects]\n\"/other\" = { trust_level = \"trusted\" }\n\"/tmp/air-ui-live/project\" = { trust_level = \"trusted\" }\n\n[tools]\nweb = true\n"
    );
    assert_eq!(
        with_trusted_directories("", &[project]),
        "[projects]\n\"/tmp/air-ui-live/project\" = { trust_level = \"trusted\" }\n"
    );
}

// Codex asks about a project inside a worktree until the worktree root and the main checkout of a linked worktree are
// trusted too.
#[test]
fn a_project_in_a_linked_worktree_trusts_the_worktree_and_its_main_checkout() {
    let directory = tempfile::tempdir().unwrap();
    let main = directory.path().join("main");
    fs::create_dir_all(main.join(".git/worktrees/lane")).unwrap();
    let worktree = directory.path().join("lane");
    fs::create_dir_all(worktree.join("project")).unwrap();
    fs::write(
        worktree.join(".git"),
        format!("gitdir: {}\n", main.join(".git/worktrees/lane").display()),
    )
    .unwrap();

    assert_eq!(
        codex_trusted_directories(&worktree.join("project")),
        [worktree.join("project"), worktree.clone(), main]
    );
    assert_eq!(codex_trusted_directories(directory.path()), [directory.path().to_owned()]);
}
