//! The deterministic agent hosts every session of one process, so what it must never do is let two sessions see the
//! counters, the model or the id of each other. These are the contracts that a UI lane relies on.

use std::io::Cursor;
use std::sync::{Arc, Mutex, PoisonError};
use std::time::Instant;

use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::hold::marker;

/// Two `session/new` requests.
const TWO_SESSIONS: &str = r#"{"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
"#;

/// One `session/new` request and one prompt of its session.
const NEW_AND_PROMPT: &str = r#"{"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1"}}
"#;

/// One session, one prompt, and the answer that allows the permission ask of that prompt.
const ALLOWED_PERMISSION_INPUT: &str = r#"{"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1"}}
{"jsonrpc":"2.0","id":"acp-integration-permission-test-1","result":{"outcome":{"outcome":"selected","optionId":"allow"}}}
"#;

/// The output of one run, parsed back into frames, so the assertions read fields rather than substrings.
struct Run {
    output: String,
    diagnostics: String,
    frames: Vec<Value>,
}

impl Run {
    /// The `sessionId` of the result of the `index`th `session/new`, in the order of the output.
    fn session_id_of(&self, index: usize) -> Option<&str> {
        self.frames
            .iter()
            .filter_map(|frame| frame.pointer("/result/sessionId").and_then(Value::as_str))
            .nth(index)
    }

    /// The `currentValue` of the model option in the response of the request `id`.
    fn current_model_of(&self, id: u64) -> Option<&str> {
        let frame = self.frames.iter().find(|frame| frame["id"] == id)?;
        frame.pointer("/result/configOptions/0/currentValue").and_then(Value::as_str)
    }

    /// The `update` of each `session/update` notification, in the order of the output.
    fn updates(&self) -> Vec<&Value> {
        self.frames
            .iter()
            .filter(|frame| frame["method"] == "session/update")
            .map(|frame| &frame["params"]["update"])
            .collect()
    }

    fn chunk_texts(&self) -> Vec<&str> {
        self.chunks("/params/update/content/text")
    }

    fn chunk_session_ids(&self) -> Vec<&str> {
        self.chunks("/params/sessionId")
    }

    fn chunks(&self, pointer: &str) -> Vec<&str> {
        self.frames
            .iter()
            .filter(|frame| frame["method"] == "session/update")
            .map(|frame| frame.pointer(pointer).and_then(Value::as_str).unwrap_or_default())
            .collect()
    }
}

fn settings(pairs: &[(&str, &str)]) -> Settings {
    let mut pairs = pairs.to_vec();
    if !pairs.iter().any(|(name, _)| *name == SESSION_NONCE_ENV) {
        pairs.push((SESSION_NONCE_ENV, "test"));
    }
    let prefix = pairs
        .iter()
        .find(|(name, _)| *name == "prefix")
        .map_or("ACP debug integration event", |(_, value)| *value);
    Settings::from_environment(prefix, &Environment::of(&pairs)).unwrap()
}

fn run(pairs: &[(&str, &str)], input: &str) -> Run {
    run_with(&settings(pairs), input, &mut Vec::new())
}

fn run_with(settings: &Settings, input: &str, diagnostics: &mut impl Write) -> Run {
    let mut output = Vec::new();
    let mut captured = Vec::new();
    serve(
        settings,
        &mut Cursor::new(input.as_bytes()),
        &mut output,
        &mut Tee(&mut captured, diagnostics),
    )
    .unwrap();
    let output = String::from_utf8(output).unwrap();
    let frames = output.lines().map(|line| serde_json::from_str::<Value>(line).unwrap()).collect();
    Run {
        output,
        diagnostics: String::from_utf8(captured).unwrap(),
        frames,
    }
}

/// Writes each byte to both writers.
struct Tee<'a, A, B>(&'a mut A, &'a mut B);

impl<A: Write, B: Write> Write for Tee<'_, A, B> {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0.write_all(bytes)?;
        self.1.write_all(bytes)?;
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        self.1.flush()
    }
}

#[test]
fn every_session_new_hands_out_its_own_id() {
    let run = run(
        &[],
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n\
         {\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{}}\n",
    );
    assert_eq!(run.session_id_of(0), Some("acp-integration-session-test-1"));
    assert_eq!(run.session_id_of(1), Some("acp-integration-session-test-2"));
    assert_eq!(run.diagnostics, "");
}

#[test]
fn separate_processes_never_reuse_provider_session_ids() {
    let input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n";
    let first = run(&[(SESSION_NONCE_ENV, "first")], input);
    let second = run(&[(SESSION_NONCE_ENV, "second")], input);
    assert_eq!(first.session_id_of(0), Some("acp-integration-session-first-1"));
    assert_eq!(second.session_id_of(0), Some("acp-integration-session-second-1"));
    let random = Settings::from_environment("p", &Environment::of(&[])).unwrap();
    let other = Settings::from_environment("p", &Environment::of(&[])).unwrap();
    assert_ne!(random.session_nonce, other.session_nonce);
    assert_eq!(random.session_nonce.len(), 36, "{}", random.session_nonce);
}

#[test]
fn prompt_counters_are_scoped_to_their_session() {
    let run = run(
        &[("prefix", "event")],
        &format!(
            "{TWO_SESSIONS}{}",
            r#"{"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1"}}
{"jsonrpc":"2.0","id":4,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1"}}
{"jsonrpc":"2.0","id":5,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-2"}}
"#
        ),
    );
    assert_eq!(run.chunk_texts(), ["event 1", "event 2", "event 1"]);
    assert_eq!(run.diagnostics, "");
}

#[test]
fn each_update_notification_names_the_prompting_session() {
    let run = run(
        &[],
        &format!(
            "{TWO_SESSIONS}{}",
            r#"{"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-2"}}
{"jsonrpc":"2.0","id":4,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1"}}
"#
        ),
    );
    assert_eq!(
        run.chunk_session_ids(),
        ["acp-integration-session-test-2", "acp-integration-session-test-1"]
    );
}

#[test]
fn selecting_a_model_leaves_every_other_session_on_the_default() {
    let run = run(
        &[],
        &format!(
            "{TWO_SESSIONS}{}",
            r#"{"jsonrpc":"2.0","id":3,"method":"session/set_config_option","params":{"sessionId":"acp-integration-session-test-1","configId":"model","value":"air-flow-large"}}
{"jsonrpc":"2.0","id":4,"method":"session/set_config_option","params":{"sessionId":"acp-integration-session-test-2","configId":"other","value":"any"}}
"#
        ),
    );
    assert_eq!(run.current_model_of(3), Some("air-flow-large"), "{}", run.output);
    assert_eq!(run.current_model_of(4), Some("air-flow-small"), "{}", run.output);
    assert_eq!(run.diagnostics, "");
}

#[test]
fn a_request_naming_an_unknown_session_fails_instead_of_answering_as_another() {
    let run = run(
        &[],
        r#"{"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-integration-session-9"}}
{"jsonrpc":"2.0","id":3,"method":"session/set_config_option","params":{"sessionId":"acp-integration-session-9","configId":"model","value":"air-flow-large"}}
"#,
    );
    assert_eq!(run.frames.len(), 3, "{}", run.output);
    for frame in &run.frames[1..] {
        assert_eq!(frame["error"]["code"], -32602, "{}", run.output);
        assert!(frame["error"]["message"].as_str().unwrap().contains("acp-integration-session-9"));
    }
    assert!(
        run.diagnostics
            .contains("session/prompt for unknown session 'acp-integration-session-9'")
    );
    assert!(run.diagnostics.contains("session/set_config_option for unknown session"));
}

#[test]
fn responses_echo_numeric_and_string_request_ids_verbatim() {
    let run = run(
        &[],
        r#"{"jsonrpc":"2.0","id":17,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":"call-a","method":"session/new","params":{}}
{"jsonrpc":"2.0","id":null,"method":"session/cancel","params":{}}
"#,
    );
    assert!(run.output.contains("\"id\":17,"), "{}", run.output);
    assert!(run.output.contains("\"id\":\"call-a\","), "{}", run.output);
    assert!(run.output.contains("\"id\":null,\"result\":{}"), "{}", run.output);
}

#[test]
fn an_unreadable_frame_is_reported_and_the_next_one_still_answers() {
    let run = run(
        &[],
        "not json at all\n{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n",
    );
    assert_eq!(run.frames.len(), 1, "{}", run.output);
    assert!(run.session_id_of(0).is_some());
    assert!(run.diagnostics.contains("cannot read frame"), "{}", run.diagnostics);
}

#[test]
fn a_request_method_without_an_id_ends_the_run() {
    let mut output = Vec::new();
    let failure = serve(
        &settings(&[]),
        &mut Cursor::new(b"{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"params\":{}}\n"),
        &mut output,
        &mut Vec::new(),
    );
    assert_eq!(failure, Err("ACP request has no id".to_owned()));
}

/// The delay is spent before the chunk, so the whole wait is time that the session spends running.
#[test]
fn a_prompt_delay_is_spent_before_the_answer() {
    let started = Instant::now();
    let run = run(&[(RESPONSE_DELAY_MS_ENV, "200")], NEW_AND_PROMPT);
    assert_eq!(run.chunk_texts().len(), 1);
    assert!(started.elapsed() >= Duration::from_millis(200));
    assert_eq!(run.diagnostics, "");
}

/// A prompt that names a hold file gets its answer only after the file is gone. A later prompt that names the file
/// again answers at once. The test deletes the file only after the agent states the start of the hold, so the result
/// does not depend on the speed of the agent.
#[test]
fn a_prompt_with_the_hold_marker_is_answered_after_the_release() {
    let directory = tempfile::tempdir().unwrap();
    let file = std::path::absolute(directory.path().join("hold")).unwrap();
    fs::write(&file, "").unwrap();
    let text = quote(&format!("work {}", marker(&file)));
    let started = hold_started_line(&file);
    /// Deletes the hold file once the diagnostics state the start of the hold.
    struct Release {
        file: PathBuf,
        started: String,
        seen: Arc<Mutex<String>>,
    }
    impl Write for Release {
        fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
            let mut seen = self.seen.lock().unwrap_or_else(PoisonError::into_inner);
            seen.push_str(&String::from_utf8_lossy(bytes));
            if seen.contains(&self.started) && self.file.exists() {
                fs::remove_file(&self.file)?;
            }
            Ok(bytes.len())
        }

        fn flush(&mut self) -> io::Result<()> {
            Ok(())
        }
    }
    let mut release = Release {
        file: file.clone(),
        started: started.clone(),
        seen: Arc::default(),
    };
    let input = format!(
        "{{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{{}}}}\n\
         {{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\",\"params\":{{\"sessionId\":\"acp-integration-session-test-1\",\"prompt\":[{{\"type\":\"text\",\"text\":{text}}}]}}}}\n\
         {{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"session/prompt\",\"params\":{{\"sessionId\":\"acp-integration-session-test-1\",\"prompt\":[{{\"type\":\"text\",\"text\":{text}}}]}}}}\n"
    );
    let run = run_with(&settings(&[]), &input, &mut release);
    assert_eq!(run.chunk_texts().len(), 2);
    assert!(!file.exists());
    assert_eq!(
        run.diagnostics.lines().collect::<Vec<_>>(),
        [started.as_str()],
        "the second prompt names a file that is gone"
    );
}

/// A value that is not what its variable takes is refused; a silent default would hide the fixture fault.
#[test]
fn a_setting_that_its_variable_cannot_take_is_refused() {
    let refusal = |pairs: &[(&str, &str)]| Settings::from_environment("p", &Environment::of(pairs)).unwrap_err();
    assert_eq!(
        refusal(&[(RESPONSE_DELAY_MS_ENV, "soon")]),
        "ACP_DEBUG_TEST_RESPONSE_DELAY_MS must be a number of milliseconds, got 'soon'"
    );
    assert_eq!(
        refusal(&[(RESPONSE_DELAY_MS_ENV, "-1")]),
        "ACP_DEBUG_TEST_RESPONSE_DELAY_MS must not be negative, got -1"
    );
    assert_eq!(
        refusal(&[(EDIT_FILES_ENV, "a.txt")]),
        "ACP_DEBUG_TEST_EDIT_FILES entry must start with 'edit:' or 'delete:', got 'a.txt'"
    );
    assert_eq!(
        refusal(&[(EDIT_FILES_ENV, "edit:/abs")]),
        "ACP_DEBUG_TEST_EDIT_FILES entry must name a relative path, got 'edit:/abs'"
    );
    assert_eq!(
        refusal(&[(SESSION_NONCE_ENV, " ")]),
        "ACP_DEBUG_TEST_SESSION_NONCE must not be blank"
    );
}

#[test]
fn the_switches_take_true_in_any_case_and_a_fork_also_loads() {
    let settings = settings(&[(ASK_PERMISSION_ENV, "TRUE"), (FORK_SESSION_ENV, "true"), (LOAD_SESSION_ENV, "no")]);
    assert!(settings.ask_permission && settings.fork_session && settings.load_session);
    let plain = self::settings(&[(ASK_PERMISSION_ENV, "yes"), (EDIT_FILES_ENV, " edit:a , delete:b,")]);
    assert!(!plain.ask_permission && !plain.load_session);
    assert_eq!(
        plain.file_changes,
        [
            FileChange {
                path: "a".to_owned(),
                delete: false
            },
            FileChange {
                path: "b".to_owned(),
                delete: true
            }
        ]
    );
}

/// With the switch, one prompt asks for a permission and answers only after the client selected an option.
#[test]
fn a_prompt_asks_for_permission_and_answers_after_the_response() {
    let run = run(&[(ASK_PERMISSION_ENV, "true"), ("prefix", "event")], ALLOWED_PERMISSION_INPUT);
    let ask = &run.frames[1];
    assert_eq!(ask["method"], "session/request_permission", "{}", run.output);
    assert_eq!(ask["id"], "acp-integration-permission-test-1");
    assert_eq!(ask["params"]["sessionId"], "acp-integration-session-test-1");
    assert_eq!(ask["params"]["toolCall"]["toolCallId"], "acp-integration-tool-call-1");
    assert_eq!(ask["params"]["options"][0]["optionId"], "allow");
    assert_eq!(ask["params"]["options"][0]["kind"], "allow_once");
    assert_eq!(ask["params"]["options"][1]["name"], "Reject");
    assert_eq!(run.chunk_texts(), ["event 1"]);
    assert!(
        run.diagnostics
            .contains("got the permission response for 'acp-integration-permission-test-1'")
    );
}

/// With both switches, the agent works for one delay before it asks and for one more after the answer.
#[test]
fn a_prompt_with_a_delay_works_before_the_ask_and_after_the_answer() {
    let started = Instant::now();
    let run = run(
        &[(ASK_PERMISSION_ENV, "true"), (RESPONSE_DELAY_MS_ENV, "200")],
        ALLOWED_PERMISSION_INPUT,
    );
    assert_eq!(run.frames[1]["method"], "session/request_permission");
    assert_eq!(run.chunk_texts().len(), 1);
    assert!(started.elapsed() >= Duration::from_millis(400));
}

#[test]
fn a_prompt_asks_for_no_permission_without_the_switch() {
    let run = run(&[], NEW_AND_PROMPT);
    assert!(!run.output.contains("session/request_permission"));
    assert_eq!(run.chunk_texts().len(), 1);
    assert_eq!(run.diagnostics, "");
}

/// A prompt whose ask is never answered never answers either, so the waiting state cannot end on its own.
#[test]
fn an_unanswered_permission_leaves_the_prompt_waiting() {
    let run = run(&[(ASK_PERMISSION_ENV, "true")], NEW_AND_PROMPT);
    assert_eq!(run.chunk_texts(), Vec::<&str>::new());
    assert!(
        run.diagnostics
            .contains("lost its input while awaiting permission 'acp-integration-permission-test-1'")
    );
}

#[test]
fn the_agent_advertises_no_session_load_and_no_air_capability_without_the_switches() {
    let run = run(&[], "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n");
    assert_eq!(run.frames[0]["result"]["agentCapabilities"]["loadSession"], false);
    assert_eq!(run.frames[0]["result"].get("_meta"), None);
    assert_eq!(run.frames[0]["result"]["agentCapabilities"].get("sessionCapabilities"), None);
}

/// With the switch, a `session/load` makes the named session its own: it replays one turn and answers an empty result,
/// and the first prompt of that session gets the first answer.
#[test]
fn a_loaded_session_replays_one_turn_and_answers_its_prompts() {
    let run = run(
        &[(LOAD_SESSION_ENV, "true"), ("prefix", "event")],
        r#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/load","params":{"sessionId":"junie-session-7","cwd":"/project","mcpServers":[]}}
{"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"junie-session-7"}}
"#,
    );
    assert_eq!(run.frames[0]["result"]["agentCapabilities"]["loadSession"], true);
    assert_eq!(run.frames[3], json!({ "jsonrpc": "2.0", "id": 2, "result": {} }));
    assert_eq!(run.chunk_session_ids(), ["junie-session-7"; 3]);
    assert_eq!(
        run.chunk_texts(),
        ["event loaded prompt", "event loaded answer of junie-session-7", "event 1"]
    );
    assert_eq!(run.frames[1]["params"]["update"]["sessionUpdate"], "user_message_chunk");
    assert_eq!(run.diagnostics, "");
}

#[test]
fn a_load_that_names_no_session_is_refused() {
    let run = run(
        &[(LOAD_SESSION_ENV, "true")],
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/load\",\"params\":{}}\n",
    );
    assert_eq!(run.frames[0]["error"]["code"], -32602);
}

#[test]
fn a_fork_answers_a_new_session_that_loads_and_answers_its_prompts() {
    let run = run(
        &[(FORK_SESSION_ENV, "true"), ("prefix", "event")],
        r#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/new","params":{"cwd":"/project","mcpServers":[]}}
{"jsonrpc":"2.0","id":3,"method":"session/fork","params":{"sessionId":"acp-integration-session-test-1","cwd":"/project","mcpServers":[]}}
{"jsonrpc":"2.0","id":4,"method":"session/load","params":{"sessionId":"acp-integration-session-test-2","cwd":"/project","mcpServers":[]}}
{"jsonrpc":"2.0","id":5,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-2"}}
"#,
    );
    assert_eq!(
        run.frames[0]["result"]["agentCapabilities"]["sessionCapabilities"]["fork"],
        json!({})
    );
    assert_eq!(run.frames[0]["result"]["agentCapabilities"]["loadSession"], true);
    assert_eq!(run.frames[2]["result"]["sessionId"], "acp-integration-session-test-2");
    assert_eq!(
        run.chunk_texts(),
        [
            "event loaded prompt",
            "event loaded answer of acp-integration-session-test-2",
            "event 1"
        ]
    );
    assert_eq!(run.diagnostics, "");
}

#[test]
fn a_fork_is_off_by_default_and_refuses_an_unknown_session() {
    let plain = run(
        &[],
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/fork\",\"params\":{\"sessionId\":\"unknown\"}}\n",
    );
    assert_eq!(
        plain.frames[0]["result"],
        json!({}),
        "a request of an unknown method gets an empty result"
    );
    let run = run(
        &[(FORK_SESSION_ENV, "true")],
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/fork\",\"params\":{\"sessionId\":\"unknown\",\"cwd\":\"/project\"}}\n",
    );
    assert_eq!(run.frames[0]["error"]["code"], -32602);
}

/// `initialize`, one `session/new` in `workspace`, and one prompt that requests the report `turn-1`.
fn initialize_new_and_prompt(workspace: &Path) -> String {
    format!(
        "{{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{{}}}}\n\
         {{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{{\"cwd\":{},\"mcpServers\":[]}}}}\n\
         {{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"session/prompt\",\"params\":{{\"sessionId\":\"acp-integration-session-test-1\",\
         \"_meta\":{{\"jetbrains\":{{\"air\":{{\"agentFileChangeReportRequest\":{{\"version\":1,\"requestId\":\"turn-1\"}}}}}}}}}}}}\n",
        quote(&workspace.display().to_string())
    )
}

/// With the switch, one prompt edits two files and deletes a third. Each change is one completed edit tool call with
/// its diff, the deletion as a Git patch to `/dev/null`. The report of the turn names the three files before the answer.
#[test]
fn a_prompt_changes_the_files_and_reports_them_before_the_answer() {
    let directory = tempfile::tempdir().unwrap();
    let workspace = std::path::absolute(directory.path()).unwrap();
    fs::write(workspace.join("a.txt"), "alpha\n").unwrap();
    fs::write(workspace.join("b.txt"), "beta\n").unwrap();
    fs::write(workspace.join("c.txt"), "gamma one\ngamma two\n").unwrap();
    let run = run(
        &[(EDIT_FILES_ENV, "edit:a.txt, edit:b.txt, delete:c.txt"), ("prefix", "event")],
        &initialize_new_and_prompt(&workspace),
    );
    assert_eq!(
        run.frames[0]["result"]["_meta"]["jetbrains"]["air"]["capabilities"],
        json!(["agentFileChangeReport", "diffPatch"])
    );
    assert_eq!(fs::read_to_string(workspace.join("a.txt")).unwrap(), "alpha\nevent edit 1\n");
    assert_eq!(fs::read_to_string(workspace.join("b.txt")).unwrap(), "beta\nevent edit 1\n");
    assert!(!workspace.join("c.txt").exists());
    let updates = run.updates();
    let kinds: Vec<&str> = updates.iter().map(|update| update["sessionUpdate"].as_str().unwrap()).collect();
    assert_eq!(
        kinds,
        ["tool_call", "tool_call", "tool_call", "session_info_update", "agent_message_chunk"]
    );
    let path = |name: &str| workspace.join(name).display().to_string();
    let edit = updates[1];
    assert_eq!((&edit["kind"], &edit["status"]), (&json!("edit"), &json!("completed")));
    assert_eq!(edit["content"][0]["path"], path("b.txt"));
    assert_eq!(edit["content"][0]["oldText"], "beta\n");
    assert_eq!(edit["content"][0]["newText"], "beta\nevent edit 1\n");
    assert_eq!(edit["title"], "Edit b.txt");
    let deletion = &updates[2]["content"][0];
    assert_eq!(deletion["newText"], "");
    assert_eq!(deletion["oldText"], Value::Null);
    let patch = &deletion["_meta"]["jetbrains"]["air"]["diffPatch"];
    assert_eq!(patch["format"], "git_patch");
    assert!(
        patch["text"]
            .as_str()
            .unwrap()
            .contains("+++ /dev/null\n@@ -1,2 +0,0 @@\n-gamma one\n-gamma two\n")
    );
    let report = &updates[3]["_meta"]["jetbrains"]["air"]["agentFileChangeReport"];
    assert_eq!(report["requestId"], "turn-1");
    assert_eq!(report["status"], "reported");
    assert_eq!(report["paths"], json!([path("a.txt"), path("b.txt"), path("c.txt")]));
    assert_eq!(run.frames.last().unwrap()["result"]["stopReason"], "end_turn");
    assert_eq!(run.diagnostics, "");
}

/// A missing file fails the prompt before any change, so the scenario never sees a part of the turn.
#[test]
fn a_missing_file_fails_the_prompt_and_changes_nothing() {
    let directory = tempfile::tempdir().unwrap();
    let workspace = std::path::absolute(directory.path()).unwrap();
    fs::write(workspace.join("a.txt"), "alpha\n").unwrap();
    let run = run(
        &[(EDIT_FILES_ENV, "edit:a.txt,delete:c.txt")],
        &initialize_new_and_prompt(&workspace),
    );
    assert_eq!(fs::read_to_string(workspace.join("a.txt")).unwrap(), "alpha\n");
    assert!(run.updates().is_empty(), "{}", run.output);
    let error = &run.frames.last().unwrap()["error"];
    assert_eq!(error["code"], -32603);
    assert!(error["message"].as_str().unwrap().contains("c.txt"));
    assert!(run.diagnostics.contains("cannot change the files of prompt 1"));
}

#[test]
fn a_deletion_diff_marks_a_text_without_a_last_line_feed() {
    let diff: Value = serde_json::from_str(&deletion_diff("/w/a", "one\ntwo")).unwrap();
    assert_eq!(
        diff["_meta"]["jetbrains"]["air"]["diffPatch"]["text"],
        "diff --git a/w/a b/w/a\ndeleted file mode 100644\n--- a/w/a\n+++ /dev/null\n@@ -1,2 +0,0 @@\n-one\n-two\n\\ No newline at end of file\n"
    );
}

/// The forms of the fixed texts, with each value as a placeholder. A Kotlin test of the authored agents reads this
/// golden, so the texts of the two sides cannot drift.
#[test]
fn the_fixed_answers_are_the_forms_of_the_golden() {
    let answers = json!({
        "answer": answer("{prefix}", "{prompt}"),
        "editedLine": edited_line("{prefix}", "{prompt}"),
        "loadedAnswer": loaded_answer("{prefix}", "{sessionId}"),
        "loadedPrompt": loaded_prompt("{prefix}"),
        "sessionId": session_id("{nonce}", "{count}"),
    });
    let text = format!("{}\n", serde_json::to_string_pretty(&answers).unwrap());
    expect_test::expect_file![avl_testkit::testdata_path!("answers.json")].assert_eq(&text);
}

/// The bytes of one run of every request kind, so a change of a frame shows in a review.
#[test]
fn a_run_of_every_request_writes_the_golden_transcript() {
    let run = run(
        &[(FORK_SESSION_ENV, "true"), (ASK_PERMISSION_ENV, "true"), ("prefix", "event")],
        r#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"session/new","params":{"mcpServers":[]}}
{"jsonrpc":"2.0","id":3,"method":"session/set_config_option","params":{"sessionId":"acp-integration-session-test-1","configId":"model","value":"air-flow-large"}}
{"jsonrpc":"2.0","id":4,"method":"session/prompt","params":{"sessionId":"acp-integration-session-test-1","prompt":[{"type":"text","text":"hi"}]}}
{"jsonrpc":"2.0","id":"acp-integration-permission-test-1","result":{"outcome":{"outcome":"selected","optionId":"allow"}}}
{"jsonrpc":"2.0","id":5,"method":"session/fork","params":{"sessionId":"acp-integration-session-test-1"}}
{"jsonrpc":"2.0","id":6,"method":"session/load","params":{"sessionId":"acp-integration-session-test-2"}}
{"jsonrpc":"2.0","id":7,"method":"session/prompt","params":{"sessionId":"unknown"}}
{"jsonrpc":"2.0","id":8,"method":"authenticate","params":{}}
"#,
    );
    expect_test::expect_file![avl_testkit::testdata_path!("transcript.jsonl")].assert_eq(&run.output);
}
