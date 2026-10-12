//! The guest agent's replies, as a fake guest answers them.

use avl_host_sys::Captured;
use avl_testkit::tartfake::{Answer, Fake};
use avl_wire::pull::FileReceipt;
use avl_wire::verb::AgentVerb;
use serde_json::{Value, json};

use crate::answer::said;

/// One agent envelope, the shape the supervisor invocation decodes.
pub fn agent_envelope(command: &str, data: &Value) -> String {
    json!({"schemaVersion": 1, "ok": true, "command": command, "data": data}).to_string()
}

/// [`agent_envelope`] as the exit 0 that printed it.
pub fn agent_reply(command: &str, data: &Value) -> Captured {
    said(&agent_envelope(command, data))
}

/// One supervisor run state, minimal and declared.
pub fn run_state(run_id: &str, phase: &str) -> Value {
    json!({"schemaVersion": 1, "runId": run_id, "snapshotId": null, "phase": phase})
}

/// The supervisor's `active` reply: the run slot held by a running `run_id`, or free.
pub fn active_reply(run_id: Option<&str>) -> Captured {
    let active = run_id.map_or(Value::Null, |run_id| run_state(run_id, "running"));
    agent_reply("active", &json!({ "active": active }))
}

/// The value following a flag in an argv, or "".
pub fn argv_value<'a>(argv: &'a [String], flag: &str) -> &'a str {
    argv.iter()
        .position(|token| token == flag)
        .and_then(|index| argv.get(index + 1))
        .map_or("", String::as_str)
}

/// Makes every raw pull through `fake` answer `content`: the bytes on stdout, and on stderr the success envelope with
/// the receipt that the agent's `read-file` writes after them.
pub fn answer_pull(fake: &Fake, content: &[u8]) {
    fake.answer(Answer::ReadFile, content);
    let receipt = serde_json::to_value(FileReceipt::of(content)).expect("a receipt encodes");
    fake.answer(
        Answer::ReadFileReceipt,
        agent_envelope(AgentVerb::ReadFile.as_str(), &receipt) + "\n",
    );
}
