//! The lane home of a live run: what the guest agent writes into the home and the bin directory of the context, so that
//! the real agents of the lane IDE reach the JetBrains Central proxy of the guest account.
//!
//! [`central`](super::central) wired the account home: `central add` wrote the Central wiring of Claude Code and Codex
//! there. [`LiveHome::seed`] copies only the wiring sections of those files into the lane home: the proxy variables and
//! the key helper of Claude, and the model provider of Codex. Nothing else of the account configuration reaches the
//! lane. The seed also completes the onboarding of Claude, trusts the project for Claude and Codex, links the Junie
//! sessions of the account into the lane home, and marks the first-run import of Junie as done. Junie takes its home
//! from the OS account, not from `HOME`, so it reads the wiring of the account itself.
//!
//! [`write_bin`] gives the bin directory the launchers of the live runtimes, the Central guard under each name of the
//! Central CLI, and the Node executables. An agent hook that ran `central` with the lane home as `HOME` would make a
//! fresh and logged-out Central there, so the guard passes only the subcommands that read on to the Central CLI, with
//! the account home.
//!
//! [`LiveHome::remove_copies`] deletes every file of the lane home that carries a credential at the end of the
//! iteration. No note and no refusal holds a value of the wiring, and every file of the lane home that carries a token
//! is readable by its owner only.

use std::collections::BTreeMap;
use std::fs;
use std::os::unix::fs::symlink;
use std::path::{Component, Path, PathBuf};
use std::sync::LazyLock;
use std::time::SystemTime;

use regex::Regex;
use serde_json::{Map, Value, json};

use super::central::CENTRAL_STATE;
use crate::lane::context::{create_dirs, real_path, shell_quote, write_launcher, write_private};
use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The refusal code of a lane home that a live run cannot seed.
pub(crate) const HOME_CODE: &str = "lane_live_home";

/// The settings of Claude Code below a home. The Central wiring is the `env` and the key helper of this file.
const CLAUDE_SETTINGS: &str = ".claude/settings.json";

/// The global configuration of Claude Code below a home: its onboarding, its project trust and its approved keys.
const CLAUDE_GLOBAL: &str = ".claude.json";

/// The login file of Claude Code below a home, which the lane never writes and deletes when it finds one.
const CLAUDE_CREDENTIALS: &str = ".claude/.credentials.json";

/// The configuration of Codex below a home. The Central wiring is its model provider.
const CODEX_CONFIG: &str = ".codex/config.toml";

/// The login file of Codex below a home, which the lane never writes and deletes when it finds one.
const CODEX_AUTH: &str = ".codex/auth.json";

/// The key file of Junie below a home, which the lane never writes and deletes when it finds one.
const JUNIE_KEY: &str = ".junie/api-key";

/// Where Junie records its sessions below a home.
const JUNIE_SESSIONS: &str = ".junie/sessions";

/// The session index of Junie below a home: one JSON object per line.
const JUNIE_INDEX: &str = ".junie/sessions/index.jsonl";

/// Where Junie records below a home that its first-run import of other agents' configuration is done.
const JUNIE_MIGRATION_STATE: &str = ".junie/misc/migration_state.json";

/// The `env` keys of the Claude settings that carry the Central wiring.
const CLAUDE_WIRING_KEYS: [&str; 3] = ["ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_API_KEY"];

/// The key of the Claude settings that names the command that answers the key.
const CLAUDE_KEY_HELPER: &str = "apiKeyHelper";

/// The `env` key whose value Claude asks to approve.
const CLAUDE_API_KEY: &str = "ANTHROPIC_API_KEY";

/// How many characters of the end of a key Claude records as approved.
const APPROVED_KEY_SUFFIX: usize = 20;

/// The names under which an agent can call the Central CLI.
pub(crate) const CENTRAL_NAMES: [&str; 3] = ["central", "jbcentral", "wire"];

/// The Central subcommands that only read, which the guard passes on. The empty one is `central` alone: its help.
const CENTRAL_READ_ONLY_SUBCOMMANDS: [&str; 10] = ["", "status", "version", "--version", "-v", "help", "--help", "-h", "models", "list"];

/// The Node executables that a registry ACP agent runs.
const NODE_EXECUTABLES: [&str; 3] = ["node", "npm", "npx"];

/// What a real agent reads from a directory above its project.
const AGENT_PROJECT_FILES: [&str; 8] = [
    ".git",
    ".mcp.json",
    "AGENTS.md",
    "CLAUDE.md",
    ".claude",
    ".codex",
    ".junie",
    ".agents",
];

/// A top-level `model_provider` in either TOML string form: a basic `"…"` string or a literal `'…'` string.
static MODEL_PROVIDER_LINE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r#"^\s*model_provider\s*=\s*(?:"([^"']+)"|'([^"']+)')\s*(?:#.*)?$"#).expect("a literal pattern compiles"));

/// The first line of a TOML table header.
static TABLE_HEADER: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^\s*\[").expect("a literal pattern compiles"));

/// The lane home of one live launch and the account whose wiring it copies.
#[derive(Clone, Copy, Debug)]
pub(crate) struct LiveHome<'a> {
    /// The account home, which `central add` wired.
    pub account_home: &'a Path,
    /// The home of the context, `HOME` and `user.home` of the lane IDE.
    pub lane_home: &'a Path,
    /// The real path of the project that the lane IDE opens.
    pub project_dir: &'a Path,
    /// The port of the Central proxy, which the copied wiring must name.
    pub central_port: u16,
}

/// The modification times of the account files that the wiring is copied from, by their path below the home. `None`
/// stands for a file that is absent. Only the times are read.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct WiringStamps(BTreeMap<&'static str, Option<SystemTime>>);

impl WiringStamps {
    fn of(home: &Path) -> Self {
        Self(
            [CLAUDE_SETTINGS, CODEX_CONFIG]
                .into_iter()
                .map(|file| (file, fs::metadata(home.join(file)).and_then(|metadata| metadata.modified()).ok()))
                .collect(),
        )
    }

    /// The latest time of the files, for a note.
    fn latest(&self) -> Option<SystemTime> {
        self.0.values().flatten().max().copied()
    }
}

/// What the wiring copied, for the note. It names counts and the provider, never a value.
struct Copied {
    claude_keys: usize,
    claude_key_helper: bool,
    codex_provider: String,
    /// The copied `ANTHROPIC_API_KEY`, which Claude asks to approve. Never part of a note. Zeroed on drop.
    claude_api_key: Option<String>,
}

impl Copied {
    fn note(&self) -> String {
        format!(
            "central: wiring copied from the account configs (claude: env keys {}{}, codex: provider {})",
            self.claude_keys,
            if self.claude_key_helper { " and the key helper" } else { "" },
            self.codex_provider
        )
    }
}

impl Drop for Copied {
    fn drop(&mut self) {
        if let Some(key) = self.claude_api_key.take() {
            let mut bytes = key.into_bytes();
            bytes.fill(0);
            std::hint::black_box(&bytes);
        }
    }
}

impl LiveHome<'_> {
    /// Seeds the lane home for one launch of the lane IDE, write-if-absent, because the seed of a later iteration finds
    /// what an agent wrote since. Answers the stamps of the account wiring that it copied, which
    /// [`LiveHome::refresh`] compares. Each line of what it did goes to `note`.
    ///
    /// A lane home that is the account home is refused: the close deletes the lane copies, and the account holds the
    /// originals.
    pub(crate) fn seed(&self, note: &mut dyn FnMut(&str)) -> Result<WiringStamps, AgentRefusal> {
        if same_path(self.lane_home, self.account_home) {
            return Err(refused(format!(
                "the lane home {} is the guest account home; the lane needs a home of its own",
                self.lane_home.display()
            )));
        }
        create_dirs(self.lane_home)?;
        // A run that was killed before its close leaves the state of a Central instance that an agent started here.
        self.remove_lane_central_state(note);
        self.link_junie_sessions()?;
        self.seed_junie_first_run()?;
        // Before the copy, so a rewrite that lands while it copies reads as a change at the next refresh.
        let stamps = WiringStamps::of(self.account_home);
        let copied = self.copy_wiring()?;
        note(&copied.note());
        self.seed_claude_global(copied.claude_api_key.as_deref())?;
        self.trust_codex_project()?;
        Ok(stamps)
    }

    /// Copies the wiring of the account into the lane home again when the account files changed since `stamps`, and
    /// answers whether it did.
    ///
    /// The Central CLI rewrites the wiring of every agent with a new secret when its proxy restarts. A scenario asks
    /// for this before it starts an agent, because an agent reads its wiring when it starts. Only the times of the
    /// account files are compared, and the account is only read.
    pub(crate) fn refresh(&self, stamps: &mut WiringStamps, note: &mut dyn FnMut(&str)) -> Result<bool, AgentRefusal> {
        let now = WiringStamps::of(self.account_home);
        if now == *stamps {
            return Ok(false);
        }
        for file in [CLAUDE_SETTINGS, CODEX_CONFIG] {
            delete_credential_copy(&self.lane_home.join(file), self.lane_home);
        }
        let copied = self.copy_wiring()?;
        self.trust_codex_project()?;
        let changed = now.latest().map_or_else(|| "an unknown time".to_owned(), timestamp);
        *stamps = now;
        note(&format!(
            "Central re-wired the account at {changed}; the lane wiring is refreshed ({})",
            copied.note()
        ));
        Ok(true)
    }

    /// Deletes the files of the lane home that carry a credential, and answers the deleted paths: the copied wiring, the
    /// onboarding seed of Claude, any login or key file that an agent wrote, the link of the Junie sessions, and the
    /// state of a Central instance that ran under the lane home. Each file is deleted without being read, and a file
    /// that a link reaches is never deleted.
    pub(crate) fn remove_copies(&self, note: &mut dyn FnMut(&str)) -> Vec<PathBuf> {
        let mut removed = self.remove_lane_central_state(note);
        removed.extend(self.unlink_junie_sessions());
        for file in [
            CLAUDE_SETTINGS,
            CODEX_CONFIG,
            CODEX_AUTH,
            CLAUDE_CREDENTIALS,
            CLAUDE_GLOBAL,
            JUNIE_KEY,
        ] {
            let path = self.lane_home.join(file);
            if delete_credential_copy(&path, self.lane_home) {
                removed.push(path);
            }
        }
        removed
    }

    /// Recipe C: the wiring sections of the Claude and Codex configurations of the account, merged into the lane home.
    /// A configuration that holds no wiring fails the launch and names the `central add` that wires it. The lane files
    /// must then name the local proxy, by address or by port: only the presence of either is read.
    fn copy_wiring(&self) -> Result<Copied, AgentRefusal> {
        let (claude_keys, claude_key_helper, claude_api_key) = self.copy_claude()?;
        let codex_provider = self.copy_codex()?;
        for file in [CLAUDE_SETTINGS, CODEX_CONFIG] {
            if !names_proxy(&self.lane_home.join(file), self.central_port) {
                return Err(refused(format!(
                    "the copied Central wiring in the lane's {file} names no local proxy on port {}; check ~/{file} of the guest account",
                    self.central_port
                )));
            }
        }
        Ok(Copied {
            claude_keys,
            claude_key_helper,
            codex_provider,
            claude_api_key,
        })
    }

    /// Copies the wiring keys of the `env` of the Claude settings and the key helper. The helper runs with the account
    /// home as `HOME`, because the login of the Central CLI that it asks is bound to that home.
    fn copy_claude(&self) -> Result<(usize, bool, Option<String>), AgentRefusal> {
        let account = read_object(&self.account_home.join(CLAUDE_SETTINGS)).unwrap_or_default();
        let account_env = account.get("env").and_then(Value::as_object).cloned().unwrap_or_default();
        let wiring: Map<String, Value> = CLAUDE_WIRING_KEYS
            .iter()
            .filter_map(|key| account_env.get(*key).map(|value| ((*key).to_owned(), value.clone())))
            .collect();
        let key_helper = account.get(CLAUDE_KEY_HELPER);
        if wiring.is_empty() && key_helper.is_none() {
            return Err(refused(format!(
                "Claude is not wired to Central in the guest account: ~/{CLAUDE_SETTINGS} holds none of {CLAUDE_WIRING_KEYS:?} and no \
                 {CLAUDE_KEY_HELPER}; run `central add claude-code`"
            )));
        }
        let mut entries = Map::new();
        if let Some(helper) = key_helper {
            let helper = match helper.as_str() {
                Some(command) => Value::String(format!("HOME={} {command}", shell_quote(&self.account_home.display().to_string()))),
                None => helper.clone(),
            };
            entries.insert(CLAUDE_KEY_HELPER.to_owned(), helper);
        }
        let api_key = wiring.get(CLAUDE_API_KEY).and_then(Value::as_str).map(str::to_owned);
        merge_claude_settings(&self.lane_home.join(CLAUDE_SETTINGS), wiring.clone(), entries)?;
        Ok((wiring.len(), key_helper.is_some(), api_key))
    }

    /// Copies the `model_provider` line and every `[model_providers.<name>]` table of the Codex configuration, and
    /// answers the provider name. The line goes first, because a TOML key outside a table must precede every table. A
    /// lane file that names a provider already keeps it, because a later iteration seeds again.
    fn copy_codex(&self) -> Result<String, AgentRefusal> {
        let text = fs::read_to_string(self.account_home.join(CODEX_CONFIG)).unwrap_or_default();
        let lines: Vec<&str> = text.lines().collect();
        let first_table = lines.iter().position(|line| TABLE_HEADER.is_match(line)).unwrap_or(lines.len());
        let found = lines[..first_table].iter().find_map(|line| {
            let captures = MODEL_PROVIDER_LINE.captures(line)?;
            let name = captures.get(1).or_else(|| captures.get(2))?.as_str().to_owned();
            Some((*line, name))
        });
        let table = found.as_ref().map(|(_, name)| provider_table(&lines, name)).unwrap_or_default();
        let Some((provider_line, provider)) = found.filter(|_| !table.is_empty()) else {
            return Err(refused(format!(
                "Codex is not wired to Central in the guest account: ~/{CODEX_CONFIG} has no model_provider with its \
                 [model_providers] table; run `central add codex`"
            )));
        };
        let lane = self.lane_home.join(CODEX_CONFIG);
        let existing = fs::read_to_string(&lane).unwrap_or_default();
        if !existing.lines().any(|line| MODEL_PROVIDER_LINE.is_match(line)) {
            let mut written = format!("{provider_line}\n\n");
            for line in table {
                written.push_str(line);
                written.push('\n');
            }
            written.push_str(&existing);
            write_in(&lane, &written)?;
        }
        Ok(provider)
    }

    /// Writes the global configuration of Claude when it is absent: the onboarding done, the copied key approved, the
    /// project trusted, and the servers of every enclosing `.mcp.json` declined, so Claude asks nothing before the
    /// session.
    fn seed_claude_global(&self, api_key: Option<&str>) -> Result<(), AgentRefusal> {
        create_dirs(&self.lane_home.join(".claude"))?;
        let global = self.lane_home.join(CLAUDE_GLOBAL);
        if global.symlink_metadata().is_ok() {
            return Ok(());
        }
        let declined = enclosing_mcp_servers(self.project_dir);
        let mut everywhere: Vec<String> = Vec::new();
        for name in declined.iter().flat_map(|(_, names)| names) {
            if !everywhere.contains(name) {
                everywhere.push(name.clone());
            }
        }
        let mut project = Map::new();
        project.insert("hasTrustDialogAccepted".to_owned(), Value::Bool(true));
        project.insert("hasCompletedProjectOnboarding".to_owned(), Value::Bool(true));
        decline(&mut project, &everywhere);
        let mut projects = Map::new();
        projects.insert(self.project_dir.display().to_string(), Value::Object(project));
        for (directory, names) in &declined {
            if directory.as_path() != self.project_dir {
                let mut entry = Map::new();
                decline(&mut entry, names);
                projects.insert(directory.display().to_string(), Value::Object(entry));
            }
        }
        let mut config = Map::new();
        config.insert("hasCompletedOnboarding".to_owned(), Value::Bool(true));
        if let Some(key) = api_key {
            let suffix: String = key
                .chars()
                .rev()
                .take(APPROVED_KEY_SUFFIX)
                .collect::<Vec<_>>()
                .into_iter()
                .rev()
                .collect();
            config.insert("customApiKeyResponses".to_owned(), json!({"approved": [suffix], "rejected": []}));
        }
        config.insert("projects".to_owned(), Value::Object(projects));
        write_in(&global, &format!("{}\n", pretty(&Value::Object(config))))
    }

    /// Trusts the project, its worktree root and the main checkout of a linked worktree in the Codex configuration.
    fn trust_codex_project(&self) -> Result<(), AgentRefusal> {
        let file = self.lane_home.join(CODEX_CONFIG);
        let config = fs::read_to_string(&file).unwrap_or_default();
        write_in(
            &file,
            &with_trusted_directories(&config, &codex_trusted_directories(self.project_dir)),
        )
    }

    /// Links `<lane home>/.junie/sessions` to the sessions directory of the account, so Air finds the sessions that the
    /// real Junie records. Junie writes them into the account home whatever `HOME` says, and Air reads the lane home.
    /// The target is created, so the link never dangles. A real directory in place of the link is refused.
    fn link_junie_sessions(&self) -> Result<(), AgentRefusal> {
        let link = self.lane_home.join(JUNIE_SESSIONS);
        let target = self.account_home.join(JUNIE_SESSIONS);
        create_dirs(&target)?;
        match link.symlink_metadata() {
            Ok(metadata) if metadata.file_type().is_symlink() && fs::read_link(&link).is_ok_and(|named| named == target) => Ok(()),
            Ok(_) => Err(refused(format!(
                "{} exists and is not a link to {}, so Air would not see the sessions that the real Junie records in the account home. \
                 Delete it; the lane links it again.",
                link.display(),
                target.display()
            ))),
            Err(_) => {
                create_dirs(link.parent().unwrap_or(self.lane_home))?;
                symlink(&target, &link).map_err(|error| refused(format!("cannot link {} to {}: {error}", link.display(), target.display())))
            }
        }
    }

    /// Deletes the link of [`LiveHome::link_junie_sessions`], never what it names.
    fn unlink_junie_sessions(&self) -> Option<PathBuf> {
        let link = self.lane_home.join(JUNIE_SESSIONS);
        let target = self.account_home.join(JUNIE_SESSIONS);
        let linked = link.symlink_metadata().is_ok_and(|metadata| metadata.file_type().is_symlink())
            && fs::read_link(&link).is_ok_and(|named| named == target);
        (linked && fs::remove_file(&link).is_ok()).then_some(link)
    }

    /// Records in the Junie home of the account that the first-run import of Junie is done, with the project, so its TUI
    /// starts straight into the session. On its first run Junie offers to import the configuration of Claude Code that
    /// `central add` wrote, as a full-screen step before the folder trust question. A marker that Junie wrote stays.
    fn seed_junie_first_run(&self) -> Result<(), AgentRefusal> {
        let state = self.account_home.join(JUNIE_MIGRATION_STATE);
        if state.symlink_metadata().is_ok() {
            return Ok(());
        }
        create_dirs(state.parent().unwrap_or(self.account_home))?;
        let text = json!({"migratedProjects": [self.project_dir.display().to_string()]}).to_string();
        fs::write(&state, text).map_err(|error| refused(format!("cannot write {}: {error}", state.display())))
    }

    /// Deletes a Central state directory below the lane home, and answers its deleted files. A lane process that ran
    /// the Central CLI with the lane home as `HOME` leaves one, with a token of the account in it. The guard is there
    /// so that none does, so one found here is a warning. A link is never followed, and nothing in it is read.
    fn remove_lane_central_state(&self, note: &mut dyn FnMut(&str)) -> Vec<PathBuf> {
        let state = self.lane_home.join(CENTRAL_STATE);
        if !state.symlink_metadata().is_ok_and(|metadata| metadata.is_dir()) {
            return Vec::new();
        }
        note(&format!(
            "WARNING: a Central instance ran under the lane HOME; its state is deleted: {}",
            state.display()
        ));
        let files: Vec<PathBuf> = walkdir::WalkDir::new(&state)
            .follow_links(false)
            .into_iter()
            .filter_map(Result::ok)
            .filter(|entry| entry.file_type().is_file())
            .map(walkdir::DirEntry::into_path)
            .collect();
        let deleted = files
            .into_iter()
            .filter(|file| delete_credential_copy(file, self.lane_home))
            .collect();
        for entry in walkdir::WalkDir::new(&state)
            .follow_links(false)
            .contents_first(true)
            .into_iter()
            .flatten()
        {
            let _ = if entry.file_type().is_dir() {
                fs::remove_dir(entry.path())
            } else {
                fs::remove_file(entry.path())
            };
        }
        deleted
    }
}

/// One launcher of a live runtime: its name in the bin directory, its command, and what it exports.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Launcher<'a> {
    pub name: &'a str,
    pub command: &'a [String],
    pub environment: &'a BTreeMap<String, String>,
}

/// Writes into `bin_dir` the launcher of each of `launchers`, the Central guard under each of [`CENTRAL_NAMES`], and a
/// link to each Node executable beside `node`, the Node of the guest. A Node name that `bin_dir` holds already stays.
///
/// The guard passes the subcommands that only read on to the declared Central CLI `central`, with the account home as
/// `HOME`. Every other subcommand exits with 0 and a line on stderr, so an agent hook neither blocks nor retries.
pub(crate) fn write_bin(
    bin_dir: &Path,
    launchers: &[Launcher<'_>],
    central: &Path,
    account_home: &Path,
    node: &Path,
) -> Result<(), AgentRefusal> {
    create_dirs(bin_dir)?;
    for launcher in launchers {
        write_launcher(&bin_dir.join(launcher.name), launcher.command, launcher.environment)?;
    }
    let read_only = CENTRAL_READ_ONLY_SUBCOMMANDS.map(shell_quote).join("|");
    let guard = format!(
        "#!/bin/sh\n# The Central guard of the live lane: only reads reach the Central CLI, with the account home.\n\
         case \"$1\" in\n  {read_only}) HOME={} exec {} \"$@\" ;;\nesac\n\
         echo \"[ui-live] central $* is not run from the lane\" >&2\nexit 0\n",
        shell_quote(&account_home.display().to_string()),
        shell_quote(&central.display().to_string())
    );
    for name in CENTRAL_NAMES {
        write_launcher_text(&bin_dir.join(name), &guard)?;
    }
    let node_dir = node.parent().unwrap_or(node);
    for name in NODE_EXECUTABLES {
        let link = bin_dir.join(name);
        let target = node_dir.join(name);
        if link.symlink_metadata().is_err() && target.is_file() {
            symlink(&target, &link).map_err(|error| refused(format!("cannot link {} to {}: {error}", link.display(), target.display())))?;
        }
    }
    Ok(())
}

/// Refuses a live project that sits below an agent instruction or configuration, and names them. A real agent reads
/// those from every directory above its project, and the check reads every directory above it, so a leak cannot come
/// back unnoticed.
pub(crate) fn require_isolated_project(project_dir: &Path) -> Result<(), AgentRefusal> {
    let real = real_path(project_dir)?;
    let leaks: Vec<String> = real
        .ancestors()
        .skip(1)
        .flat_map(|directory| AGENT_PROJECT_FILES.map(|name| directory.join(name)))
        .filter(|path| path.symlink_metadata().is_ok())
        .map(|path| path.display().to_string())
        .collect();
    if leaks.is_empty() {
        Ok(())
    } else {
        Err(refused(format!(
            "the live project {} sits below agent instructions or configuration: {}",
            real.display(),
            leaks.join(", ")
        )))
    }
}

/// The ids of the Junie sessions of the session index of the account home whose project directory is `project_root` or
/// below it. Only the session id and the project directory of a line are read. A line that does not parse, or that
/// lacks either, is skipped, and a session of any other project is never named.
pub(crate) fn leftover_junie_sessions(account_home: &Path, project_root: &Path) -> Vec<String> {
    let Ok(index) = fs::read_to_string(account_home.join(JUNIE_INDEX)) else {
        return Vec::new();
    };
    let mut roots = vec![lexical(project_root)];
    if let Ok(real) = real_path(project_root) {
        roots.push(real);
    }
    let mut ids: Vec<String> = Vec::new();
    for line in index.lines() {
        let Ok(Value::Object(record)) = serde_json::from_str::<Value>(line) else {
            continue;
        };
        let (Some(id), Some(project)) = (
            record.get("sessionId").and_then(Value::as_str),
            record.get("projectDir").and_then(Value::as_str),
        ) else {
            continue;
        };
        let project = lexical(Path::new(project));
        if roots.iter().any(|root| project.starts_with(root)) && !ids.iter().any(|known| known == id) {
            ids.push(id.to_owned());
        }
    }
    ids
}

/// The lines of `[model_providers.<name>]` and of its sub-tables, each table from its header to the next other one.
fn provider_table<'a>(lines: &[&'a str], name: &str) -> Vec<&'a str> {
    let headers = [
        format!("model_providers.{name}"),
        format!("model_providers.\"{name}\""),
        format!("model_providers.'{name}'"),
    ];
    let owns = |line: &str| {
        let header = line.trim();
        let header = header.strip_prefix('[').unwrap_or(header);
        let header = header.split(']').next().unwrap_or_default().trim();
        headers
            .iter()
            .any(|prefix| header == prefix || header.starts_with(&format!("{prefix}.")))
    };
    let mut table = Vec::new();
    let mut inside = false;
    for line in lines {
        if TABLE_HEADER.is_match(line) {
            inside = owns(line);
        }
        if inside {
            table.push(*line);
        }
    }
    while table.last().is_some_and(|line| line.trim().is_empty()) {
        table.pop();
    }
    table
}

/// The Codex configuration `config` with a trusted entry of each of `directories` in its `[projects]` table. An entry of
/// one of them that the table holds already is replaced, and every other line stays.
fn with_trusted_directories(config: &str, directories: &[PathBuf]) -> String {
    let keys: Vec<String> = directories
        .iter()
        .map(|directory| toml_string(&directory.display().to_string()))
        .collect();
    let entries = keys
        .iter()
        .map(|key| format!("{key} = {{ trust_level = \"trusted\" }}"))
        .collect::<Vec<_>>()
        .join("\n");
    let normalized = config.replace("\r\n", "\n");
    let normalized = normalized.trim_end();
    if normalized.is_empty() {
        return format!("[projects]\n{entries}\n");
    }
    let lines: Vec<&str> = normalized.split('\n').collect();
    let Some(header) = lines.iter().position(|line| line.trim() == "[projects]") else {
        return format!("{normalized}\n\n[projects]\n{entries}\n");
    };
    let end = lines[header + 1..]
        .iter()
        .position(|line| line.trim_start().starts_with('['))
        .map_or(lines.len(), |index| header + 1 + index);
    let body = header + 1..end;
    let insertion = body
        .clone()
        .rev()
        .find(|index| !lines[*index].trim().is_empty())
        .map_or(header + 1, |index| index + 1);
    let mut text = String::new();
    for (index, line) in lines.iter().enumerate() {
        if index == insertion {
            text.push_str(&entries);
            text.push('\n');
        }
        if !body.contains(&index) || !keys.iter().any(|key| line.trim_start().starts_with(key.as_str())) {
            text.push_str(line);
            text.push('\n');
        }
    }
    if insertion == lines.len() {
        text.push_str(&entries);
        text.push('\n');
    }
    text
}

/// The directories that Codex must trust for `project_dir`: the project, the root of the worktree that holds it, and
/// the main checkout of a linked worktree.
fn codex_trusted_directories(project_dir: &Path) -> Vec<PathBuf> {
    let project = lexical(project_dir);
    let Some(root) = project
        .ancestors()
        .find(|directory| directory.join(".git").symlink_metadata().is_ok())
    else {
        return vec![project_dir.to_owned()];
    };
    let mut directories = vec![project_dir.to_owned()];
    for directory in std::iter::once(root.to_owned()).chain(linked_worktree_main_root(root)) {
        if !directories.iter().any(|known| lexical(known) == directory) {
            directories.push(directory);
        }
    }
    directories
}

/// The main checkout of the linked worktree `root`, from the `gitdir:` line of its `.git` file.
fn linked_worktree_main_root(root: &Path) -> Option<PathBuf> {
    let text = fs::read_to_string(root.join(".git")).ok()?;
    let git_dir = text.lines().find_map(|line| line.strip_prefix("gitdir:"))?.trim();
    if git_dir.is_empty() {
        return None;
    }
    let worktrees = lexical(&root.join(git_dir)).parent()?.to_owned();
    let common = worktrees.parent()?;
    (worktrees.file_name()? == "worktrees" && common.file_name()? == ".git").then(|| common.parent().map(Path::to_owned))?
}

/// The `.mcp.json` server names of `project_dir` and of each directory above it, by directory, for each directory whose
/// file names any.
fn enclosing_mcp_servers(project_dir: &Path) -> Vec<(PathBuf, Vec<String>)> {
    lexical(project_dir)
        .ancestors()
        .filter_map(|directory| {
            let names: Vec<String> = read_object(&directory.join(".mcp.json"))?
                .get("mcpServers")?
                .as_object()?
                .keys()
                .cloned()
                .collect();
            (!names.is_empty()).then(|| (directory.to_owned(), names))
        })
        .collect()
}

fn decline(entry: &mut Map<String, Value>, names: &[String]) {
    if !names.is_empty() {
        entry.insert("enabledMcpjsonServers".to_owned(), json!([]));
        entry.insert("disabledMcpjsonServers".to_owned(), json!(names));
    }
}

/// Merges `env` into the `env` object of the Claude settings `settings`, and `entries` into its top level. A value that
/// the file holds already wins, because a later iteration writes again.
fn merge_claude_settings(settings: &Path, env: Map<String, Value>, entries: Map<String, Value>) -> Result<(), AgentRefusal> {
    let mut lane = read_object(settings).unwrap_or_default();
    let mut merged_env = env;
    if let Some(Value::Object(lane_env)) = lane.get("env") {
        merged_env.extend(lane_env.clone());
    }
    lane.insert("env".to_owned(), Value::Object(merged_env));
    for (key, value) in entries {
        lane.entry(key).or_insert(value);
    }
    write_in(settings, &format!("{}\n", pretty(&Value::Object(lane))))
}

/// Whether `config` names the local proxy, by address or by port. Only the presence of either is read.
fn names_proxy(config: &Path, port: u16) -> bool {
    fs::read_to_string(config).is_ok_and(|text| text.contains("127.0.0.1") || text.contains(&format!(":{port}")))
}

/// Deletes `target` when it is a regular file whose directory is `root` or below it, and answers whether it did. A file
/// that a link reaches outside `root` is never deleted.
fn delete_credential_copy(target: &Path, root: &Path) -> bool {
    let regular = target.symlink_metadata().is_ok_and(|metadata| metadata.file_type().is_file());
    let inside = match (target.parent().map(real_path), real_path(root)) {
        (Some(Ok(parent)), Ok(root)) => parent.starts_with(root),
        _ => false,
    };
    regular && inside && fs::remove_file(target).is_ok()
}

/// Writes `text` into `file`, creating its directory, readable by its owner only.
fn write_in(file: &Path, text: &str) -> Result<(), AgentRefusal> {
    create_dirs(file.parent().unwrap_or(file))?;
    write_private(file, text.as_bytes())
}

/// A launcher whose whole text is `text`, executable by its owner.
fn write_launcher_text(file: &Path, text: &str) -> Result<(), AgentRefusal> {
    use std::os::unix::fs::PermissionsExt as _;
    fs::write(file, text)
        .and_then(|()| fs::set_permissions(file, fs::Permissions::from_mode(0o755)))
        .map_err(|error| refused(format!("cannot write {}: {error}", file.display())))
}

fn read_object(file: &Path) -> Option<Map<String, Value>> {
    match serde_json::from_str(&fs::read_to_string(file).ok()?).ok()? {
        Value::Object(object) => Some(object),
        _ => None,
    }
}

fn pretty(value: &Value) -> String {
    serde_json::to_string_pretty(value).unwrap_or_default()
}

/// `value` as a TOML basic string.
fn toml_string(value: &str) -> String {
    format!("\"{}\"", value.replace('\\', "\\\\").replace('"', "\\\""))
}

/// `path` absolute against the working directory, with `.` and `..` resolved by name.
fn lexical(path: &Path) -> PathBuf {
    let absolute = std::path::absolute(path).unwrap_or_else(|_| path.to_owned());
    let mut normalized = PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::ParentDir => {
                normalized.pop();
            }
            Component::CurDir => {}
            other => normalized.push(other),
        }
    }
    normalized
}

fn same_path(first: &Path, second: &Path) -> bool {
    lexical(first) == lexical(second)
}

fn timestamp(time: SystemTime) -> String {
    jiff::Timestamp::try_from(time).map_or_else(|_| "an unknown time".to_owned(), |timestamp| timestamp.to_string())
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(HOME_CODE, message)
}
