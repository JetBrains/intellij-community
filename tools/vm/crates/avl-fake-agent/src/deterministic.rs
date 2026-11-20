//! The deterministic agent: a small ACP agent over standard input and output, which answers each request with a fixed
//! frame and needs no control server.
//!
//! One process serves every session that the IDE opens on it, because the agent client pool of the IDE keeps one
//! process per agent and leases it per session. So each session owns its id, its prompt counter and its selected
//! model. A request that names a session that this process never handed out gets a JSON-RPC error. The variables of
//! the environment turn on the behaviors that only some fixtures need: see [Settings::from_environment].
//!
//! The fixed texts are [answer], [loaded_prompt], [loaded_answer] and [edited_line]. `testdata/answers.json` holds
//! their forms, and a Kotlin test of the authored agents reads that file.

use std::collections::HashMap;
use std::fmt::Display;
use std::fs;
use std::io::{self, BufRead, Write};
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

use serde_json::Value;

use crate::environment::Environment;
use crate::hold::hold_file_of;
use crate::json::{format_id, parse_object, quote, quote_all};
use crate::lines::read_line;

/// The id of each session of a process is `<SESSION_ID_PREFIX><nonce>-<count>`.
pub(crate) const SESSION_NONCE_ENV: &str = "ACP_DEBUG_TEST_SESSION_NONCE";

/// How long a prompt takes to answer, in milliseconds. Zero, the default, answers at once.
pub(crate) const RESPONSE_DELAY_MS_ENV: &str = "ACP_DEBUG_TEST_RESPONSE_DELAY_MS";

/// `true` makes each prompt first ask the client for one tool permission.
pub(crate) const ASK_PERMISSION_ENV: &str = "ACP_DEBUG_TEST_ASK_PERMISSION";

/// `true` makes the agent advertise `loadSession` and answer `session/load`.
pub(crate) const LOAD_SESSION_ENV: &str = "ACP_DEBUG_TEST_LOAD_SESSION";

/// `true` makes the agent advertise `sessionCapabilities.fork`, answer `session/fork`, and load sessions too.
pub(crate) const FORK_SESSION_ENV: &str = "ACP_DEBUG_TEST_FORK_SESSION";

/// The files that each prompt changes, as a comma-separated list of `edit:<path>` and `delete:<path>`, each relative to
/// the working directory of the session.
pub(crate) const EDIT_FILES_ENV: &str = "ACP_DEBUG_TEST_EDIT_FILES";

const SESSION_ID_PREFIX: &str = "acp-integration-session-";
const PERMISSION_ID_PREFIX: &str = "acp-integration-permission-";
const PERMISSION_TOOL_CALL_ID_PREFIX: &str = "acp-integration-tool-call-";
const EDIT_TOOL_CALL_ID_PREFIX: &str = "acp-integration-edit-";
const PERMISSION_TITLE: &str = "Edit the scenario file";
const DEFAULT_MODEL_ID: &str = "air-flow-small";
const LARGE_MODEL_ID: &str = "air-flow-large";
const EDIT_PREFIX: &str = "edit:";
const DELETE_PREFIX: &str = "delete:";
const HOLD_POLL: Duration = Duration::from_millis(50);
/// The longest hold, so a hold that nobody releases does not keep the agent alive forever.
const HOLD_DEADLINE: Duration = Duration::from_mins(15);

/// The JSON-RPC error code of a request that names an unknown session.
const INVALID_PARAMS: i32 = -32602;
/// The JSON-RPC error code of a prompt that cannot change its files.
const INTERNAL_ERROR: i32 = -32603;

/// The answer to the prompt `prompt` of a session.
pub(crate) fn answer(prefix: &str, prompt: impl Display) -> String {
    format!("{prefix} {prompt}")
}

/// The user prompt of the turn that a `session/load` replays.
pub(crate) fn loaded_prompt(prefix: &str) -> String {
    format!("{prefix} loaded prompt")
}

/// The agent answer of the turn that a `session/load` of `session_id` replays. It names the loaded session, so a client
/// can tell which session it loaded.
pub(crate) fn loaded_answer(prefix: &str, session_id: &str) -> String {
    format!("{prefix} loaded answer of {session_id}")
}

/// The line that the prompt `prompt` appends to each file that it edits.
pub(crate) fn edited_line(prefix: &str, prompt: impl Display) -> String {
    format!("{prefix} edit {prompt}\n")
}

/// The id of the session `count` of the process with `nonce`.
pub(crate) fn session_id(nonce: &str, count: impl Display) -> String {
    format!("{SESSION_ID_PREFIX}{nonce}-{count}")
}

/// What the environment asks of one process.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Settings {
    pub(crate) event_prefix: String,
    pub(crate) session_nonce: String,
    pub(crate) response_delay: Duration,
    pub(crate) ask_permission: bool,
    pub(crate) load_session: bool,
    pub(crate) fork_session: bool,
    pub(crate) file_changes: Vec<FileChange>,
}

/// One change of [EDIT_FILES_ENV]: the file at `path`, relative to the working directory, and whether it goes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct FileChange {
    path: String,
    delete: bool,
}

impl Settings {
    /// The settings of `environment`, with the answers prefixed by `event_prefix`. A value that is not what its
    /// variable takes is refused rather than read as the default, because a fixture that asks for a behavior must
    /// get it.
    pub(crate) fn from_environment(event_prefix: &str, environment: &Environment) -> Result<Self, String> {
        let session_nonce = match environment.get(SESSION_NONCE_ENV) {
            Some(nonce) if nonce.trim().is_empty() => return Err(format!("{SESSION_NONCE_ENV} must not be blank")),
            Some(nonce) => nonce.to_owned(),
            None => random_nonce(),
        };
        let flag = |name: &str| environment.get(name).is_some_and(|value| value.eq_ignore_ascii_case("true"));
        let fork_session = flag(FORK_SESSION_ENV);
        Ok(Self {
            event_prefix: event_prefix.to_owned(),
            session_nonce,
            response_delay: response_delay(environment.get(RESPONSE_DELAY_MS_ENV))?,
            ask_permission: flag(ASK_PERMISSION_ENV),
            load_session: fork_session || flag(LOAD_SESSION_ENV),
            fork_session,
            file_changes: file_changes(environment.get(EDIT_FILES_ENV))?,
        })
    }
}

/// Runs the deterministic agent of the process, whose answers start with `prefix`, until the end of standard input.
pub(crate) fn run_standalone(prefix: &str, environment: &Environment) -> i32 {
    let served = Settings::from_environment(prefix, environment).and_then(|settings| {
        serve(
            &settings,
            &mut io::stdin().lock(),
            &mut io::stdout().lock(),
            &mut io::stderr().lock(),
        )
    });
    match served {
        Ok(()) => 0,
        Err(failure) => {
            crate::process::say(format!("ACP deterministic agent failed: {failure}"));
            1
        }
    }
}

fn response_delay(value: Option<&str>) -> Result<Duration, String> {
    let value = value.unwrap_or_default().trim();
    if value.is_empty() {
        return Ok(Duration::ZERO);
    }
    let millis: i64 = value
        .parse()
        .map_err(|_not_a_number| format!("{RESPONSE_DELAY_MS_ENV} must be a number of milliseconds, got '{value}'"))?;
    u64::try_from(millis)
        .map(Duration::from_millis)
        .map_err(|_negative| format!("{RESPONSE_DELAY_MS_ENV} must not be negative, got {millis}"))
}

fn file_changes(value: Option<&str>) -> Result<Vec<FileChange>, String> {
    let value = value.unwrap_or_default().trim();
    if value.is_empty() {
        return Ok(Vec::new());
    }
    let mut entries: Vec<&str> = value.split(',').collect();
    while entries.last().is_some_and(|entry| entry.is_empty()) {
        entries.pop();
    }
    entries
        .into_iter()
        .map(|entry| {
            let entry = entry.trim();
            let (delete, path) = match (entry.strip_prefix(DELETE_PREFIX), entry.strip_prefix(EDIT_PREFIX)) {
                (Some(path), _) => (true, path),
                (None, Some(path)) => (false, path),
                (None, None) => {
                    return Err(format!(
                        "{EDIT_FILES_ENV} entry must start with '{EDIT_PREFIX}' or '{DELETE_PREFIX}', got '{entry}'"
                    ));
                }
            };
            if path.trim().is_empty() || Path::new(path).is_absolute() {
                return Err(format!("{EDIT_FILES_ENV} entry must name a relative path, got '{entry}'"));
            }
            Ok(FileChange {
                path: path.to_owned(),
                delete,
            })
        })
        .collect()
}

/// A nonce that differs on each call, in the form of a UUID.
fn random_nonce() -> String {
    use std::hash::{BuildHasher, Hasher};
    let mut bytes = [0_u8; 16];
    for chunk in bytes.chunks_mut(8) {
        let value = std::collections::hash_map::RandomState::new().build_hasher().finish().to_le_bytes();
        chunk.copy_from_slice(&value[..chunk.len()]);
    }
    let hex: String = bytes.iter().map(|byte| format!("{byte:02x}")).collect();
    format!("{}-{}-{}-{}-{}", &hex[..8], &hex[8..12], &hex[12..16], &hex[16..20], &hex[20..])
}

/// Everything that one session owns. Two sessions of one process share nothing.
struct SessionState {
    prompt: u64,
    selected_model_id: String,
    /// The working directory that `session/new` or `session/load` named.
    working_directory: Option<PathBuf>,
}

impl SessionState {
    fn new(params: Option<&Value>) -> Self {
        Self {
            prompt: 0,
            selected_model_id: DEFAULT_MODEL_ID.to_owned(),
            working_directory: working_directory(params),
        }
    }
}

/// One process of the agent: its settings, its sessions, and its three streams.
struct Agent<'a, I, O, E> {
    settings: &'a Settings,
    sessions: HashMap<String, SessionState>,
    session_count: u64,
    input: &'a mut I,
    output: &'a mut O,
    diagnostics: &'a mut E,
}

/// Answers each frame of `input` on `output` until the end of `input`. A frame that cannot be answered goes to
/// `diagnostics`. A request without an id and a failed write end the run with the reason.
pub(crate) fn serve(
    settings: &Settings,
    input: &mut impl BufRead,
    output: &mut impl Write,
    diagnostics: &mut impl Write,
) -> Result<(), String> {
    let mut agent = Agent {
        settings,
        sessions: HashMap::new(),
        session_count: 0,
        input,
        output,
        diagnostics,
    };
    agent.serve()
}

impl<I: BufRead, O: Write, E: Write> Agent<'_, I, O, E> {
    fn serve(&mut self) -> Result<(), String> {
        while let Some(request) = read_line(self.input).map_err(|error| format!("cannot read the input: {error}"))? {
            if request.trim().is_empty() {
                continue;
            }
            let frame = match parse_object(&request) {
                Ok(frame) => frame,
                Err(reason) => {
                    self.say(&format!("ACP deterministic agent cannot read frame ({reason}): {request}"));
                    continue;
                }
            };
            // No method means a response to a request of this agent. The one request that it sends is the permission
            // ask, and [Agent::await_permission_response] reads that response where it waits for it.
            let Some(method) = frame.get("method").and_then(Value::as_str) else {
                self.say(&format!("ACP deterministic agent got an unexpected response frame: {request}"));
                continue;
            };
            if !self.answer(method, frame.get("id"), frame.get("params"))? {
                return Ok(());
            }
        }
        Ok(())
    }

    /// Answers one request, and answers `false` when the input ended inside it.
    fn answer(&mut self, method: &str, id: Option<&Value>, params: Option<&Value>) -> Result<bool, String> {
        let settings = self.settings;
        match method {
            "initialize" => {
                let result = initialize_result(settings.load_session, settings.fork_session, !settings.file_changes.is_empty());
                self.write(&response(id, &result)?)?;
            }
            "session/load" if settings.load_session => {
                let Some(session_id) = string_at(params, "sessionId").filter(|value| !value.trim().is_empty()) else {
                    return self.unknown_session(id, method, params).map(|()| true);
                };
                let session_id = session_id.to_owned();
                self.sessions
                    .entry(session_id.clone())
                    .or_insert_with(|| SessionState::new(None))
                    .working_directory = working_directory(params);
                let prefix = &settings.event_prefix;
                self.write(&text_update(&session_id, "user_message_chunk", &loaded_prompt(prefix)))?;
                self.write(&text_update(
                    &session_id,
                    "agent_message_chunk",
                    &loaded_answer(prefix, &session_id),
                ))?;
                self.write(&response(id, "{}")?)?;
            }
            "session/fork" if settings.fork_session => {
                if self.session(params).is_none() {
                    return self.unknown_session(id, method, params).map(|()| true);
                }
                let session_id = self.new_session(params);
                self.write(&response(id, &format!("{{\"sessionId\":{}}}", quote(&session_id)))?)?;
            }
            "session/new" => {
                let session_id = self.new_session(params);
                let result = format!(
                    "{{\"sessionId\":{},\"configOptions\":[{}]}}",
                    quote(&session_id),
                    model_config_option(DEFAULT_MODEL_ID)
                );
                self.write(&response(id, &result)?)?;
            }
            "session/set_config_option" => {
                let Some(state) = self.session_mut(params) else {
                    return self.unknown_session(id, method, params).map(|()| true);
                };
                if string_at(params, "configId") == Some("model")
                    && let Some(value) = string_at(params, "value")
                {
                    value.clone_into(&mut state.selected_model_id);
                }
                let result = format!("{{\"configOptions\":[{}]}}", model_config_option(&state.selected_model_id));
                self.write(&response(id, &result)?)?;
            }
            "session/prompt" => return self.prompt(id, params),
            _ if id.is_some() => self.write(&response(id, "{}")?)?,
            _ => {}
        }
        Ok(true)
    }

    /// Answers one prompt, and answers `false` when the input ended while the prompt waited for its permission.
    ///
    /// The agent works for one delay, then asks, then works for one more delay after the answer. A scenario therefore
    /// reads Running first, then Needs attention, then Running again.
    fn prompt(&mut self, id: Option<&Value>, params: Option<&Value>) -> Result<bool, String> {
        let settings = self.settings;
        let Some(state) = self.session_mut(params) else {
            self.unknown_session(id, "session/prompt", params)?;
            return Ok(true);
        };
        state.prompt += 1;
        let prompt = state.prompt;
        let session_id = string_at(params, "sessionId").unwrap_or_default().to_owned();
        if settings.ask_permission {
            thread::sleep(settings.response_delay);
            let permission_id = format!("{PERMISSION_ID_PREFIX}{}-{prompt}", settings.session_nonce);
            self.write(&permission_request(&permission_id, &session_id, prompt))?;
            if !self.await_permission_response(&permission_id)? {
                return Ok(false);
            }
        }
        thread::sleep(settings.response_delay);
        self.await_hold_release(params);
        if !settings.file_changes.is_empty()
            && let Err(failure) = self.change_files(&session_id, params)?
        {
            self.say(&format!(
                "ACP deterministic agent cannot change the files of prompt {prompt}: {failure}"
            ));
            self.write(&error(id, INTERNAL_ERROR, &failure))?;
            return Ok(true);
        }
        self.write(&text_update(
            &session_id,
            "agent_message_chunk",
            &answer(&settings.event_prefix, prompt),
        ))?;
        self.write(&response(id, "{\"stopReason\":\"end_turn\"}")?)?;
        Ok(true)
    }

    fn new_session(&mut self, params: Option<&Value>) -> String {
        self.session_count += 1;
        let session_id = session_id(&self.settings.session_nonce, self.session_count);
        self.sessions.insert(session_id.clone(), SessionState::new(params));
        session_id
    }

    fn session(&self, params: Option<&Value>) -> Option<&SessionState> {
        self.sessions.get(string_at(params, "sessionId")?)
    }

    fn session_mut(&mut self, params: Option<&Value>) -> Option<&mut SessionState> {
        self.sessions.get_mut(string_at(params, "sessionId")?)
    }

    /// A request of a session that this process cannot place. It goes to the diagnostics, and a request with an id
    /// gets the error `Invalid params`, so the caller fails rather than reads the state of another session.
    fn unknown_session(&mut self, id: Option<&Value>, method: &str, params: Option<&Value>) -> Result<(), String> {
        let session_id = string_at(params, "sessionId").unwrap_or("null").to_owned();
        self.say(&format!("ACP deterministic agent got {method} for unknown session '{session_id}'"));
        if id.is_none() {
            return Ok(());
        }
        self.write(&error(id, INVALID_PARAMS, &format!("unknown sessionId '{session_id}'")))
    }

    /// Reads frames until the client answers the permission request `permission_id`, and answers whether it did.
    /// Every other frame that arrives meanwhile goes to the diagnostics and is dropped. `false` means that the input
    /// ended first.
    fn await_permission_response(&mut self, permission_id: &str) -> Result<bool, String> {
        while let Some(line) = read_line(self.input).map_err(|error| format!("cannot read the input: {error}"))? {
            if line.trim().is_empty() {
                continue;
            }
            let Ok(frame) = parse_object(&line) else {
                self.say(&format!(
                    "ACP deterministic agent cannot read frame while awaiting a permission response: {line}"
                ));
                continue;
            };
            let method = frame.get("method").and_then(Value::as_str);
            if method.is_none() && frame.get("id").and_then(Value::as_str) == Some(permission_id) {
                self.say(&format!(
                    "ACP deterministic agent got the permission response for '{permission_id}': {line}"
                ));
                return Ok(true);
            }
            self.say(&format!(
                "ACP deterministic agent dropped a frame while awaiting permission '{permission_id}': {line}"
            ));
        }
        self.say(&format!(
            "ACP deterministic agent lost its input while awaiting permission '{permission_id}'"
        ));
        Ok(false)
    }

    /// Holds the answer back while the file that the prompt names with a hold marker exists, but not longer than
    /// [HOLD_DEADLINE]. A hold starts with [hold_started_line], so a test releases the hold only after it started.
    fn await_hold_release(&mut self, params: Option<&Value>) {
        let Some(file) = hold_file(params).filter(|file| file.exists()) else {
            return;
        };
        self.say(&hold_started_line(&file));
        let deadline = Instant::now() + HOLD_DEADLINE;
        while file.exists() {
            if Instant::now() > deadline {
                self.say(&format!(
                    "ACP deterministic agent answers a held prompt after the deadline, because nobody deleted {}",
                    file.display()
                ));
                return;
            }
            thread::sleep(HOLD_POLL);
        }
    }

    /// Changes the files of the settings in the working directory of the session, sends one completed edit tool call
    /// for each, and then the report of the turn. The inner error is the reason why the prompt changed nothing.
    ///
    /// Each file is read before the first change, so a missing file fails the prompt before any file changes.
    fn change_files(&mut self, session_id: &str, params: Option<&Value>) -> Result<Result<(), String>, String> {
        let settings = self.settings;
        let Some(state) = self.session_mut(params) else {
            return Ok(Err("the session is unknown".to_owned()));
        };
        let prompt = state.prompt;
        let Some(root) = state.working_directory.clone() else {
            return Ok(Err("the session named no working directory".to_owned()));
        };
        let mut files = Vec::new();
        for change in &settings.file_changes {
            let file = root.join(&change.path);
            if !file.is_file() {
                let action = if change.delete { "delete" } else { "edit" };
                return Ok(Err(format!("no file {} to {action}", file.display())));
            }
            let old_text = fs::read_to_string(&file).map_err(|error| format!("cannot read {}: {error}", file.display()))?;
            files.push((file, old_text));
        }
        for (index, (change, (file, old_text))) in settings.file_changes.iter().zip(&files).enumerate() {
            let path = file.display().to_string();
            let diff = if change.delete {
                fs::remove_file(file).map_err(|error| format!("cannot delete {path}: {error}"))?;
                deletion_diff(&path, old_text)
            } else {
                let new_text = format!("{old_text}{}", edited_line(&settings.event_prefix, prompt));
                fs::write(file, &new_text).map_err(|error| format!("cannot write {path}: {error}"))?;
                format!(
                    "{{\"type\":\"diff\",\"path\":{},\"oldText\":{},\"newText\":{}}}",
                    quote(&path),
                    quote(old_text),
                    quote(&new_text)
                )
            };
            let verb = if change.delete { "Delete" } else { "Edit" };
            let name = file.file_name().unwrap_or_default().to_string_lossy();
            let update = format!(
                "{{\"sessionUpdate\":\"tool_call\",\"toolCallId\":{},\"title\":{},\"kind\":\"edit\",\"status\":\"completed\",\
                 \"content\":[{diff}],\"locations\":[{{\"path\":{}}}]}}",
                quote(&format!("{EDIT_TOOL_CALL_ID_PREFIX}{prompt}-{}", index + 1)),
                quote(&format!("{verb} {name}")),
                quote(&path)
            );
            self.write(&session_update(session_id, &update))?;
        }
        let request_id = params
            .and_then(|params| params.pointer("/_meta/jetbrains/air/agentFileChangeReportRequest/requestId"))
            .and_then(Value::as_str);
        let Some(request_id) = request_id else {
            return Ok(Err("the prompt requested no file change report".to_owned()));
        };
        let paths: Vec<String> = files.iter().map(|(file, _)| file.display().to_string()).collect();
        let update = format!(
            "{{\"sessionUpdate\":\"session_info_update\",\"_meta\":{{\"jetbrains\":{{\"air\":{{\"version\":1,\
             \"agentFileChangeReport\":{{\"version\":1,\"requestId\":{},\"status\":\"reported\",\"paths\":{},\
             \"declaredComplete\":true,\"truncated\":false}}}}}}}}}}",
            quote(request_id),
            quote_all(paths.iter().map(String::as_str))
        );
        self.write(&session_update(session_id, &update))?;
        Ok(Ok(()))
    }

    fn write(&mut self, frame: &str) -> Result<(), String> {
        writeln!(self.output, "{frame}")
            .and_then(|()| self.output.flush())
            .map_err(|error| format!("cannot write the output: {error}"))
    }

    fn say(&mut self, line: &str) {
        let _ = writeln!(self.diagnostics, "{line}").and_then(|()| self.diagnostics.flush());
    }
}

/// The diagnostic line that starts the hold of a prompt that names `file`.
pub(crate) fn hold_started_line(file: &Path) -> String {
    format!("ACP deterministic agent holds a prompt while {} exists", file.display())
}

/// The file that a text block of the prompt of `params` names with a hold marker.
fn hold_file(params: Option<&Value>) -> Option<PathBuf> {
    params?
        .get("prompt")?
        .as_array()?
        .iter()
        .find_map(|block| block.get("text").and_then(Value::as_str).and_then(hold_file_of))
}

fn string_at<'v>(params: Option<&'v Value>, name: &str) -> Option<&'v str> {
    params?.get(name)?.as_str()
}

/// The absolute form of the `cwd` of `params`, or `None` when it names none.
fn working_directory(params: Option<&Value>) -> Option<PathBuf> {
    let cwd = string_at(params, "cwd").filter(|cwd| !cwd.trim().is_empty())?;
    Some(std::path::absolute(cwd).unwrap_or_else(|_unresolved| PathBuf::from(cwd)))
}

fn initialize_result(load_session: bool, fork_session: bool, edits_files: bool) -> String {
    let meta = if edits_files {
        ",\"_meta\":{\"jetbrains\":{\"air\":{\"version\":1,\"capabilities\":[\"agentFileChangeReport\",\"diffPatch\"]}}}"
    } else {
        ""
    };
    let session = if fork_session {
        ",\"sessionCapabilities\":{\"fork\":{}}"
    } else {
        ""
    };
    format!(
        "{{\"protocolVersion\":1,\"agentCapabilities\":{{\"loadSession\":{load_session}{session}}},\"authMethods\":[],\
         \"implementation\":{{\"name\":\"acp-debug-integration-mock\",\"version\":\"1\"}}{meta}}}"
    )
}

/// The one option of every session: the model select, with the two models that every fixture sees.
fn model_config_option(selected_model_id: &str) -> String {
    format!(
        "{{\"type\":\"select\",\"id\":\"model\",\"name\":\"Model\",\"category\":\"model\",\"currentValue\":{},\
         \"options\":[{{\"value\":\"{DEFAULT_MODEL_ID}\",\"name\":\"Air Flow Small\"}},\
         {{\"value\":\"{LARGE_MODEL_ID}\",\"name\":\"Air Flow Large\"}}]}}",
        quote(selected_model_id)
    )
}

/// The ACP diff of the deletion of the file at `path`, whose text was `old_text`. An ACP diff cannot say that a file
/// is gone, so the diff carries the Air Git patch of the deletion.
fn deletion_diff(path: &str, old_text: &str) -> String {
    let header = path.strip_prefix('/').unwrap_or(path);
    let mut lines: Vec<&str> = old_text.split('\n').collect();
    if lines.last() == Some(&"") {
        lines.pop();
    }
    let mut patch = format!(
        "diff --git a/{header} b/{header}\ndeleted file mode 100644\n--- a/{header}\n+++ /dev/null\n@@ -1,{} +0,0 @@\n",
        lines.len()
    );
    for line in &lines {
        patch.push('-');
        patch.push_str(line);
        patch.push('\n');
    }
    if !old_text.ends_with('\n') {
        patch.push_str("\\ No newline at end of file\n");
    }
    format!(
        "{{\"type\":\"diff\",\"path\":{},\"oldText\":null,\"newText\":\"\",\"_meta\":{{\"jetbrains\":{{\"air\":{{\"version\":1,\
         \"diffPatch\":{{\"version\":1,\"format\":\"git_patch\",\"text\":{}}}}}}}}}}}",
        quote(path),
        quote(&patch)
    )
}

/// One `session/update` notification of `session_id` with the update object `update`.
fn session_update(session_id: &str, update: &str) -> String {
    format!(
        "{{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{{\"sessionId\":{},\"update\":{update}}}}}",
        quote(session_id)
    )
}

/// One `session/update` notification of `session_id`: the text chunk `text` of the kind `kind`.
fn text_update(session_id: &str, kind: &str, text: &str) -> String {
    session_update(
        session_id,
        &format!(
            "{{\"sessionUpdate\":{},\"content\":{{\"type\":\"text\",\"text\":{}}}}}",
            quote(kind),
            quote(text)
        ),
    )
}

/// One `session/request_permission` request with exactly one allow and one reject option. A third option would put a
/// row between Allow and Reject on the card of the client, and move the row that a scenario picks.
fn permission_request(permission_id: &str, session_id: &str, prompt: u64) -> String {
    format!(
        "{{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"session/request_permission\",\"params\":{{\"sessionId\":{},\
         \"toolCall\":{{\"toolCallId\":{},\"title\":{},\"kind\":\"edit\",\"status\":\"pending\"}},\
         \"options\":[{{\"optionId\":\"allow\",\"name\":\"Allow\",\"kind\":\"allow_once\"}},\
         {{\"optionId\":\"reject\",\"name\":\"Reject\",\"kind\":\"reject_once\"}}]}}}}",
        quote(permission_id),
        quote(session_id),
        quote(&format!("{PERMISSION_TOOL_CALL_ID_PREFIX}{prompt}")),
        quote(PERMISSION_TITLE)
    )
}

fn error(id: Option<&Value>, code: i32, message: &str) -> String {
    format!(
        "{{\"jsonrpc\":\"2.0\",\"id\":{},\"error\":{{\"code\":{code},\"message\":{}}}}}",
        format_id(id),
        quote(message)
    )
}

/// The response with `result` to the request `id`. A request without an id ends the run, because the client of a
/// notification waits for no answer, and so a notification of a request method is a fixture fault.
fn response(id: Option<&Value>, result: &str) -> Result<String, String> {
    let id = id.ok_or_else(|| "ACP request has no id".to_owned())?;
    Ok(format!(
        "{{\"jsonrpc\":\"2.0\",\"id\":{},\"result\":{result}}}",
        format_id(Some(id))
    ))
}

#[cfg(test)]
mod tests;
