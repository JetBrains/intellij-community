//! The program runner over a fake bridge and a fake supervisor: the bridge is a loopback HTTP server that answers each
//! request type from a script, and the lane IDE is a pid that each relaunch counts up.

use std::collections::{BTreeMap, VecDeque};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use avl_testkit::http::{FakeHttp, Response};
use avl_wire::program::parse_suite_program;
use expect_test::expect_file;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};
use tempfile::TempDir;

use super::*;
use crate::lane::trace::tests::Recording;
use crate::lane::watchdog::Policy;

/// The prefix of the hand-written requests of the bridge.
const BRIDGE: &str = "com.intellij.air.integration.bridge.";

/// The lane services as a host id steers them: the answered paths of the registry, and its armed fault.
#[derive(Default)]
pub(crate) struct FakeServices {
    pub paths: Mutex<Vec<String>>,
    pub fault: Mutex<(String, Option<u16>, Option<u64>)>,
    /// The base URL of the emulated JetBrains Cloud that `account-approve-sign-in` requires.
    pub cloud_base_url: Mutex<String>,
}

impl ServiceControl for FakeServices {
    fn registry_mark(&self) -> Result<usize, String> {
        Ok(self.paths.lock().unwrap().len())
    }

    fn registry_requests_since(&self, mark: usize, prefix: &str) -> Result<usize, String> {
        Ok(self.paths.lock().unwrap()[mark..]
            .iter()
            .filter(|path| path.starts_with(prefix))
            .count())
    }

    fn registry_log(&self) -> String {
        self.paths.lock().unwrap().join("\n")
    }

    fn registry_arm(&self, fault: &str, status: Option<u16>, hold_ms: Option<u64>) -> Result<(), String> {
        *self.fault.lock().unwrap() = (fault.to_owned(), status, hold_ms);
        Ok(())
    }

    fn registry_clear(&self) -> Result<(), String> {
        *self.fault.lock().unwrap() = ("none".to_owned(), None, None);
        Ok(())
    }

    fn registry_fault(&self) -> Result<String, String> {
        Ok(self.fault.lock().unwrap().0.clone())
    }

    fn feedback_clear(&self) -> Result<(), String> {
        Ok(())
    }

    fn cloud_clear(&self) -> Result<(), String> {
        Ok(())
    }

    fn cloud_base_url(&self) -> Result<String, String> {
        Ok(self.cloud_base_url.lock().unwrap().clone())
    }
}

/// What the fake IDE answers, and what it was asked, each request with the pid of the IDE that got it.
#[derive(Default)]
struct Script {
    /// The answers of the successive `Reset` requests; a reset past the list holds.
    resets: VecDeque<Value>,
    /// The failure of each request type that fails.
    failures: BTreeMap<String, Value>,
    /// The value of each request type that answers one; any other answers `{}`.
    answers: BTreeMap<String, Value>,
    /// How long each request type takes.
    delays: BTreeMap<String, Duration>,
    requests: Vec<(u32, Value)>,
}

/// The fake lane: the fake bridge, the pid of the IDE, its context directory, and the lane services.
struct Fake {
    _http: FakeHttp,
    client: avl_bridge::Client,
    script: Arc<Mutex<Script>>,
    pid: Arc<AtomicU32>,
    relaunches: Mutex<Vec<(Relaunch, bool)>>,
    context: TempDir,
    services: FakeServices,
    /// Set by the kill of the watchdog. A request that answers after it fails, as a request to a dead IDE does.
    killed: Arc<AtomicBool>,
    /// A relaunch keeps the pid, as a launch that left the old IDE on the bridge port would.
    relaunch_keeps_pid: AtomicBool,
}

impl Fake {
    fn new(script: Script) -> Self {
        let script = Arc::new(Mutex::new(script));
        let pid = Arc::new(AtomicU32::new(1));
        let killed = Arc::new(AtomicBool::new(false));
        let http = {
            let (script, pid, killed) = (Arc::clone(&script), Arc::clone(&pid), Arc::clone(&killed));
            FakeHttp::start(move |request| {
                let body: Value = serde_json::from_slice(&request.body).unwrap();
                let kind = body["type"].as_str().unwrap_or_default().to_owned();
                let delay = {
                    let mut script = script.lock().unwrap();
                    script.requests.push((pid.load(Ordering::SeqCst), body.clone()));
                    script.delays.get(&kind).copied()
                };
                if let Some(delay) = delay {
                    std::thread::sleep(delay);
                }
                if killed.load(Ordering::SeqCst) {
                    return Response::error(503, "the IDE was killed");
                }
                let answer = answer(&mut script.lock().unwrap(), &kind, &body);
                Response::json(200, answer.to_string())
            })
        };
        let client = avl_bridge::Client::new(http.url(), "token").unwrap();
        Self {
            _http: http,
            client,
            script,
            pid,
            relaunches: Mutex::new(Vec::new()),
            context: TempDir::new().unwrap(),
            services: FakeServices::default(),
            killed,
            relaunch_keeps_pid: AtomicBool::new(false),
        }
    }

    /// The types of the requests, in order, with the pid of the IDE that got each one.
    fn requests(&self) -> Vec<(u32, String)> {
        let script = self.script.lock().unwrap();
        script
            .requests
            .iter()
            .map(|(pid, body)| {
                (
                    *pid,
                    body["type"].as_str().unwrap_or_default().trim_start_matches(BRIDGE).to_owned(),
                )
            })
            .collect()
    }

    /// The bodies of the requests of the type `kind`.
    fn bodies(&self, kind: &str) -> Vec<Value> {
        let script = self.script.lock().unwrap();
        script
            .requests
            .iter()
            .filter(|(_, body)| body["type"] == kind)
            .map(|(_, body)| body.clone())
            .collect()
    }
}

/// The answer `{value, failure}` of one request.
fn answer(script: &mut Script, kind: &str, body: &Value) -> Value {
    if let Some(failure) = script.failures.get(kind) {
        return json!({ "value": null, "failure": failure });
    }
    let value = match kind.trim_start_matches(BRIDGE) {
        "Reset" => script.resets.pop_front().unwrap_or(Value::Null),
        "ResetManagedAgents" => Value::Null,
        "StepSequence" => {
            let mut stopped = false;
            let outcomes: Vec<Value> = body["steps"]
                .as_array()
                .unwrap()
                .iter()
                .map(|step| {
                    if stopped {
                        return json!({ "ran": false });
                    }
                    let kind = step["type"].as_str().unwrap();
                    if let Some(failure) = script.failures.get(kind) {
                        stopped = true;
                        return json!({ "failure": failure, "durationMs": 3, "ran": true });
                    }
                    let value = match kind.trim_start_matches(BRIDGE) {
                        "SeedSessionPresets" => json!({ "presetIds": step["presets"].as_array().unwrap().iter().map(|preset| preset["id"].clone()).collect::<Vec<_>>() }),
                        "SessionTabs" => json!([{ "tabKey": "terminal-1", "interactionSurfaceId": "terminal", "pending": false }, { "tabKey": "native-1", "interactionSurfaceId": "native", "pending": false }]),
                        _ => Value::Null,
                    };
                    json!({ "value": value, "durationMs": 5, "ran": true })
                })
                .collect();
            Value::Array(outcomes)
        }
        _ => script.answers.get(kind).cloned().unwrap_or_else(|| json!({})),
    };
    json!({ "value": value })
}

impl Probes for Fake {
    fn intrusion(&self) -> Option<String> {
        None
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        None
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        Ok("\"AWT-EventQueue-0\" RUNNABLE\n\tat Air.wait(Air.kt:1)".to_owned())
    }

    fn kill(&self) -> Result<(), String> {
        self.killed.store(true, Ordering::SeqCst);
        Ok(())
    }
}

impl Lane for Fake {
    fn call(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error> {
        self.client
            .post_json("/api/air/ui-test", &request.to_string(), Instant::now() + within)
    }

    fn relaunch(
        &self,
        cause: Relaunch,
        keep_junie_offer: bool,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
    ) -> Result<(), String> {
        self.relaunches.lock().unwrap().push((cause, keep_junie_offer));
        let down = while_down(self.context.path());
        if !self.relaunch_keeps_pid.load(Ordering::SeqCst) {
            self.pid.fetch_add(1, Ordering::SeqCst);
        }
        down
    }

    fn services(&self) -> &dyn ServiceControl {
        &self.services
    }

    fn cloud_url(&self) -> Option<String> {
        Some("http://127.0.0.1:32000".to_owned())
    }

    fn context_dir(&self) -> Option<PathBuf> {
        Some(self.context.path().to_path_buf())
    }

    fn recorder_bridge(&self) -> Option<protocol::Bridge> {
        Some(protocol::Bridge {
            port: 31_347,
            token: "golden-transcript-token".to_owned(),
        })
    }

    fn ide_process(&self) -> Result<IdeProcess, String> {
        let pid = self.pid.load(Ordering::SeqCst);
        Ok(IdeProcess {
            launched: Some(i32::try_from(pid).unwrap()),
            bridge: i64::from(pid),
        })
    }
}

/// The spec of an IDE id with these values.
fn ide(answers: &[&str], may_answer: &[&str], takes: &[&str]) -> Value {
    json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": answers, "mayAnswer": may_answer, "takes": takes,
        "mayTake": [], "params": {"surface": {"type": "enum", "values": ["GLOBAL_PROMPT"], "optional": true}}})
}

/// A story suite document of `profiles` over `vocabulary`, whose fixture `fixture` carries its bridge requests.
fn story(vocabulary: &Value, profiles: &Value) -> Suite {
    let document = json!({
        "schemaVersion": 1, "suite": "rename-session", "lane": "UI", "testClassName": "AirRenameSessionGeneratedFlowUiTest",
        "labels": [
            {"name": "testClass", "value": "com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest"},
            {"name": "testMethod", "value": "generatedFlowScenarios"},
            {"name": "Subsystem", "value": "AI"},
        ],
        "restartsIde": false, "modules": [], "implementationFlows": [],
        "vocabulary": vocabulary,
        "reset": {"flagKeys": ["air.inline.empty.state.prompt"], "offerJuniePromo": false, "laneFlags": {}},
        "agents": {},
        "fixtures": {"fixture": {"managedAgentStartsInstalled": false, "keepsJunieStartupOffer": true, "bridge": {
            "setScenarioFixture": {"type": format!("{BRIDGE}SetScenarioFixture"), "facts": {"preset": {"id": "preset-1"}}},
            "seedSessionPresets": {"type": format!("{BRIDGE}SeedSessionPresets"), "presets": [{"id": "preset-1"}],
                "refreshAvailability": true, "timeoutMs": 30_000},
        }}},
        "profiles": profiles,
    });
    let bytes = document.to_string().into_bytes();
    let program = parse_suite_program(&bytes).unwrap();
    assert_eq!(program.refusals(), Vec::<String>::new());
    suites_of(vec![(program, bytes)]).unwrap().remove(0)
}

/// One story scenario `name` whose one step runs `instructions`.
fn scenario(name: &str, instructions: &Value) -> Value {
    json!({"name": name, "fixture": "fixture", "flow": "flow-rename-session", "flags": {"air.inline.empty.state.prompt": "true"},
        "followsLastUsedPreset": false, "requiresPromptProbe": false, "setups": [],
        "steps": [{"flow": "flow-rename-session", "step": "rename", "operations": [{"operation": "rename", "instructions": instructions}]}]})
}

/// What one run of the runner wrote: the records of the stream, the lines of the trace, and the iteration directory.
struct Ran {
    records: Vec<Value>,
    trace: Vec<String>,
    _iteration: TempDir,
}

impl Ran {
    fn events(&self, event: &str) -> Vec<&Value> {
        self.records.iter().filter(|record| record["event"] == event).collect()
    }
}

fn lane_document() -> LaneDocument {
    let bytes = fs::read(avl_testkit::crate_path!("testdata/lane-context/ui.json")).unwrap();
    avl_wire::program::lane::parse_lane_document(&bytes).unwrap()
}

/// Runs `suites` against `fake`, with the watchdog thread when `watch` has a slow-step budget for the test.
fn run(fake: &Fake, suites: &[Suite], slow_step: Option<Duration>) -> Ran {
    run_with(fake, suites, slow_step, INSTRUCTION_MARGIN, Duration::from_mins(30))
}

/// [`run`] with the transport margin `margin` and the active execution timeout `active` of the watchdog.
fn run_with(fake: &Fake, suites: &[Suite], slow_step: Option<Duration>, margin: Duration, active: Duration) -> Ran {
    let iteration = TempDir::new().unwrap();
    let recording = Recording::default();
    let trace = Trace::open(
        Box::new(recording.clone()),
        Trace::hello(iteration.path(), "iter-golden", false, fake.recorder_bridge()),
    );
    let document = lane_document();
    let mut out = Vec::new();
    {
        let stream = Stream::new(&mut out);
        let policy = Policy {
            active_execution: active,
            progress_gap: Duration::from_mins(5),
        };
        let watch = match slow_step {
            Some(budget) => Watch::new(policy).with_slow_step(budget),
            None => Watch::new(policy),
        };
        std::thread::scope(|scope| {
            if slow_step.is_some() {
                scope.spawn(|| crate::lane::watchdog::watch(&watch, &stream, fake, iteration.path()));
            }
            Runner::new(fake, &stream, &watch, &document, iteration.path(), trace)
                .with_instruction_margin(margin)
                .run(suites);
            watch.completed(&stream);
        });
    }
    let records = String::from_utf8(out)
        .unwrap()
        .lines()
        .map(|line| serde_json::from_str(line).unwrap())
        .collect();
    let trace = recording.lines.lock().unwrap().clone();
    for line in &trace {
        protocol::decode_command(line.as_bytes()).unwrap_or_else(|error| panic!("the recorder refuses {line}: {error}"));
    }
    Ran {
        records,
        trace,
        _iteration: iteration,
    }
}

/// The trace lines with the clock of each call and the temporary root taken out, so a run compares with the golden byte
/// for byte.
fn without_clock(trace: &[String]) -> String {
    trace
        .iter()
        .map(|line| {
            let mut command: Value = serde_json::from_str(line).unwrap();
            if command["op"] == "call" {
                command["startMs"] = json!(1);
                command["durationMs"] = json!(0);
            }
            if command["op"] == "hello" {
                command["root"] = json!("/vm/iterations/iter-golden/air-traces");
            }
            let decoded = protocol::decode_command(command.to_string().as_bytes()).unwrap();
            String::from_utf8(protocol::encode_command(&decoded).unwrap()).unwrap() + "\n"
        })
        .collect()
}

// A green scenario: the baseline, the reset before, the preparation chain, each instruction as one request with its
// arguments in camel case, the values it takes and its deadline, then the reset after. The trace is the golden.
#[test]
fn a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript() {
    let fake = Fake::new(Script {
        answers: BTreeMap::from([("action/rename-session-select".to_owned(), json!({"session": {"id": "s-1"}}))]),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": ["jetBrainsCloudUrl", "terminalBaseline"],
        "actions": {"rename-session-select": ide(&["session"], &[], &["terminalBaseline"])},
        "assertions": {"rename-session-title": ide(&[], &[], &["session", "jetBrainsCloudUrl"])}});
    let instructions = json!([
        {"action": "rename-session-select", "args": {"surface": "GLOBAL_PROMPT"}},
        {"assertion": "rename-session-title", "expectation": "The session is listed under the new title."},
    ]);
    let ran = run(
        &fake,
        &[story(
            &vocabulary,
            &json!([scenario("rename-closed-project-session", &instructions)]),
        )],
        None,
    );

    assert_eq!(
        fake.requests().into_iter().map(|(_, kind)| kind).collect::<Vec<_>>(),
        [
            "ResetManagedAgents",
            "Reset",
            "StepSequence",
            "action/rename-session-select",
            "assertion/rename-session-title",
            "Reset"
        ]
    );
    let select = &fake.bodies("action/rename-session-select")[0];
    assert_eq!(
        select,
        &json!({"type": "action/rename-session-select", "surface": "GLOBAL_PROMPT", "terminalBaseline": ["terminal-1"], "timeoutMs": 180_000})
    );
    let title = &fake.bodies("assertion/rename-session-title")[0];
    assert_eq!(title["session"], json!({"id": "s-1"}));
    assert_eq!(title["jetBrainsCloudUrl"], json!("http://127.0.0.1:32000"));
    let resets = fake.bodies(&format!("{BRIDGE}Reset"));
    assert_eq!(
        resets.iter().map(|reset| reset["afterScenario"].clone()).collect::<Vec<_>>(),
        [json!(false), json!(true)]
    );
    assert_eq!(resets[0]["flagKeys"], json!(["air.inline.empty.state.prompt"]));
    let chain = &fake.bodies(&format!("{BRIDGE}StepSequence"))[0];
    assert_eq!(
        chain["steps"]
            .as_array()
            .unwrap()
            .iter()
            .map(|step| step["type"].as_str().unwrap().trim_start_matches(BRIDGE))
            .collect::<Vec<_>>(),
        [
            "SetScenarioFixture",
            "SetRegistryFlag",
            "SeedSessionPresets",
            "RemountEditorEmptyState",
            "SessionTabs"
        ]
    );
    let finished = ran.events("testFinished");
    assert_eq!(finished.len(), 1);
    assert_eq!(finished[0]["status"], "SUCCESSFUL");
    assert_eq!(
        finished[0]["className"],
        "com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest"
    );
    assert_eq!(finished[0]["methodName"], "generatedFlowScenarios");
    expect_file![avl_testkit::crate_path!("testdata/lane-daemon/transcript.ndjson")].assert_eq(&without_clock(&ran.trace));
}

// A failed instruction fails its scenario with the failure of the IDE, typed. The failure is photographed in its span,
// the reset after still runs, and a step open past its budget gets the IDE dump, as a file, a record and an attachment.
#[test]
fn a_failed_slow_instruction_is_typed_photographed_and_dumped() {
    let failure = json!({"type": "java.lang.AssertionError", "message": "expected the title 'Renamed'",
        "stack": ["Air.check(Air.kt:7)"], "cause": {"type": "java.lang.IllegalStateException", "message": "no tab\nmore"}});
    let fake = Fake::new(Script {
        failures: BTreeMap::from([("assertion/rename-session-title".to_owned(), failure)]),
        delays: BTreeMap::from([("assertion/rename-session-title".to_owned(), Duration::from_millis(1_500))]),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": [], "actions": {}, "assertions": {"rename-session-title": ide(&[], &[], &[])}});
    let instructions = json!([{"assertion": "rename-session-title", "expectation": "The title shows."}]);
    let ran = run(
        &fake,
        &[story(&vocabulary, &json!([scenario("renames", &instructions)]))],
        Some(Duration::from_millis(100)),
    );
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "FAILED");
    assert_eq!(finished[0]["type"], "java.lang.AssertionError");
    assert_eq!(finished[0]["message"], "expected the title 'Renamed'");
    assert_eq!(
        finished[0]["detail"],
        "at Air.check(Air.kt:7)\nCaused by: java.lang.IllegalStateException: no tab"
    );
    assert_eq!(fake.requests().last().map(|(_, kind)| kind.clone()), Some("Reset".to_owned()));
    let slow = ran.events("slowStep");
    assert_eq!(slow.len(), 1, "{:?}", ran.records);
    assert_eq!(slow[0]["step"], "renames/rename/rename/rename-session-title");
    let dump = slow[0]["ideDump"].as_str().unwrap();
    // The slug of the step keeps its first 40 characters.
    assert!(
        dump.ends_with("slow-steps/01-renames-rename-rename-rename-session-tit-ide-dump.txt"),
        "{dump}"
    );
    assert!(fs::read_to_string(dump).unwrap().contains("\"AWT-EventQueue-0\" RUNNABLE"));
    let ops: Vec<String> = ran
        .trace
        .iter()
        .map(|line| serde_json::from_str::<Value>(line).unwrap())
        .filter(|command| matches!(command["op"].as_str(), Some("snap" | "attach")))
        .map(|command| {
            format!(
                "{} {}",
                command["op"].as_str().unwrap(),
                command["phase"].as_str().or(command["name"].as_str()).unwrap()
            )
        })
        .collect();
    assert_eq!(
        ops,
        [
            "snap boundary",
            "attach IDE dump of the slow step renames/rename/rename/rename-session-title",
            "snap failure",
        ]
    );
}

// A reset that does not hold recycles the IDE once, and the scenario runs and passes on the new pid.
#[test]
fn a_dirty_reset_recycles_once_and_the_scenario_passes_on_a_new_pid() {
    let fake = Fake::new(Script {
        resets: VecDeque::from([json!("2 editor tabs remain open")]),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let instructions = json!([{"action": "rename-session-select"}]);
    let ran = run(&fake, &[story(&vocabulary, &json!([scenario("renames", &instructions)]))], None);
    assert_eq!(*fake.relaunches.lock().unwrap(), [(Relaunch::Recycle, false)]);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    let select = fake
        .requests()
        .into_iter()
        .find(|(_, kind)| kind == "action/rename-session-select")
        .unwrap();
    assert_eq!(select.0, 2, "the scenario runs on the recycled IDE");
    assert!(
        ran.trace.iter().any(|line| line.contains(r#""key":"restart:recycle""#)),
        "{:?}",
        ran.trace
    );
    assert!(
        ran.events("output")
            .iter()
            .any(|record| record["text"].as_str().unwrap().starts_with(
                "The IDE was recycled 1 time(s): before 'renames': Air state reset (before 'renames') failed: 2 editor tabs remain open"
            ))
    );
}

// Resets that never hold spend the budget of three recycles. The scenarios that fail are failures, and every scenario
// after the exhaustion is skipped with the sentence that names the history.
#[test]
fn resets_that_never_hold_spend_the_budget_and_skip_the_rest() {
    let fake = Fake::new(Script {
        resets: (0..20).map(|_| json!("dirty")).collect(),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let instructions = json!([{"action": "rename-session-select"}]);
    let profiles = json!([
        scenario("first", &instructions),
        scenario("second", &instructions),
        scenario("third", &instructions)
    ]);
    let ran = run(&fake, &[story(&vocabulary, &profiles)], None);
    assert_eq!(fake.relaunches.lock().unwrap().len(), MAX_RECYCLES_IN_TEST);
    let statuses: Vec<(String, String)> = ran
        .events("testFinished")
        .iter()
        .map(|record| {
            (
                record["displayName"].as_str().unwrap().to_owned(),
                record["status"].as_str().unwrap().to_owned(),
            )
        })
        .collect();
    assert_eq!(
        statuses,
        [
            ("first".to_owned(), "FAILED".to_owned()),
            ("second".to_owned(), "FAILED".to_owned())
        ]
    );
    let skipped = ran.events("testSkipped");
    assert_eq!(skipped.len(), 1);
    let reason = skipped[0]["reason"].as_str().unwrap();
    assert!(
        reason.starts_with("scenario 'third' never ran: the shared IDE was recycled 3 time(s) and isolation still failed: "),
        "{reason}"
    );
    assert!(ran.events("testStarted").iter().all(|record| record["displayName"] != "third"));
}

const MAX_RECYCLES_IN_TEST: usize = crate::lane::recycle::MAX_RECYCLES;

// `restart-ide` performs `whileDown` while the IDE is down, keeps the Junie offer of a fixture that asks for it, states
// the fixture again to the new IDE, and answers `whileDownReads` to the next instruction.
#[test]
fn a_restart_performs_while_down_and_states_the_fixture_again() {
    let fake = Fake::new(Script::default());
    let file = fake.context.path().join("config/options/workspace.xml");
    fs::create_dir_all(file.parent().unwrap()).unwrap();
    fs::write(&file, "<before/>").unwrap();
    fake.script.lock().unwrap().answers.insert(
        "action/rename-session-select".to_owned(),
        json!({"whileDown": [{"type": "append", "path": "config/options/workspace.xml", "text": "<after/>"}, {"type": "read", "path": "config/options/workspace.xml"}]}),
    );
    let restart = json!({"side": "host", "timeoutMs": 840_000, "restarts": 1, "answers": ["whileDown", "whileDownReads"],
        "mayAnswer": [], "takes": [], "mayTake": ["whileDown"], "params": {}});
    let vocabulary = json!({"runnerAnswers": [],
        "actions": {"rename-session-select": ide(&["whileDown"], &[], &[]), "restart-ide": restart},
        "assertions": {"rename-session-title": ide(&[], &[], &["whileDownReads"])}});
    let instructions = json!([
        {"action": "rename-session-select"},
        {"action": "restart-ide"},
        {"assertion": "rename-session-title", "expectation": "The layout is restored."},
    ]);
    let ran = run(&fake, &[story(&vocabulary, &json!([scenario("restarts", &instructions)]))], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL", "{:?}", ran.records);
    assert_eq!(*fake.relaunches.lock().unwrap(), [(Relaunch::Restart, true)]);
    assert_eq!(fs::read_to_string(&file).unwrap(), "<before/><after/>");
    let kinds: Vec<(u32, String)> = fake.requests();
    let restated = kinds
        .iter()
        .position(|(pid, kind)| *pid == 2 && kind == "SetScenarioFixture")
        .unwrap();
    let title = kinds.iter().position(|(_, kind)| kind == "assertion/rename-session-title").unwrap();
    assert!(restated < title, "{kinds:?}");
    assert_eq!(
        fake.bodies("assertion/rename-session-title")[0]["whileDownReads"],
        json!({"config/options/workspace.xml": "<before/><after/>"})
    );
}

// A value that the IDE may answer and did not is no answer, so the instruction that takes it fails by name.
#[test]
fn a_missing_take_fails_the_scenario_with_the_sentence_of_the_host() {
    let fake = Fake::new(Script::default());
    let vocabulary = json!({"runnerAnswers": [],
        "actions": {"rename-session-select": ide(&[], &["session"], &[])},
        "assertions": {"rename-session-title": ide(&[], &[], &["session"])}});
    let instructions = json!([
        {"action": "rename-session-select"},
        {"assertion": "rename-session-title", "expectation": "The title shows."},
    ]);
    let ran = run(&fake, &[story(&vocabulary, &json!([scenario("misses", &instructions)]))], None);
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "FAILED");
    assert_eq!(finished[0]["type"], kind::VALUE);
    assert_eq!(
        finished[0]["message"],
        "the instruction 'rename-session-title' takes the value 'session', and no earlier instruction answered it"
    );
    assert_eq!(fake.bodies("assertion/rename-session-title"), Vec::<Value>::new());
}

// A story suite whose fixture carries no bridge requests fails its prologue as one container failure, and runs nothing.
#[test]
fn a_suite_without_its_bridge_requests_fails_its_prologue() {
    let fake = Fake::new(Script::default());
    let mut suite = story(
        &json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}}),
        &json!([scenario("prepared", &json!([{"action": "rename-session-select"}]))]),
    );
    suite.program.fixtures.get_mut("fixture").unwrap().bridge = None;
    let ran = run(&fake, &[suite], None);
    let failed = ran.events("containerFailed");
    assert_eq!(failed.len(), 1);
    assert_eq!(failed[0]["type"], kind::DOCUMENT);
    assert_eq!(ran.events("testStarted"), Vec::<&Value>::new());
    assert_eq!(fake.requests(), Vec::<(u32, String)>::new());
}

// An authored part is one `authored-step` with the values it takes. It answers exactly the values it declares, and its
// notes and metrics go to the `testFinished` of the scenario.
#[test]
fn an_authored_part_passes_its_values_and_reports_its_notes() {
    let fake = Fake::new(Script {
        answers: BTreeMap::from([(
            "action/authored-step".to_owned(),
            json!({"values": {"workflow": {"id": 7}}, "report": {"notes": ["committed"], "metrics": {"latencyMs": 12.5}}}),
        )]),
        ..Script::default()
    });
    let authored = json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": [], "mayAnswer": [], "takes": [], "mayTake": [],
        "params": {"part": {"type": "enum", "values": ["first", "second"], "optional": false},
            "scenario": {"type": "enum", "values": ["commit"], "optional": false},
            "suite": {"type": "enum", "values": ["workflow-git"], "optional": false},
            "timeoutMs": {"type": "int", "optional": false}}});
    let part = |name: &str, takes: &[&str], answers: &[&str]| {
        json!({"action": "authored-step", "args": {"part": name, "scenario": "commit", "suite": "workflow-git", "timeoutMs": 300_000},
            "uses": {"takes": takes, "answers": answers}})
    };
    let document = json!({
        "suite": "workflow-git", "lane": "UI", "testClassName": "AgentWorkflowGitUiTest", "authored": true,
        "labels": [{"name": "testClass", "value": "com.intellij.air.integration.flow.workflows.AgentWorkflowGitUiTest"}],
        "restartsIde": false, "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {"authored-step": authored}, "assertions": {}},
        "reset": {"flagKeys": [], "offerJuniePromo": false, "laneFlags": {}},
        "fixtures": {"authored": {}},
        "profiles": [{"name": "a commit starts the workflow", "key": "commit", "method": "gitScenarios", "fixture": "authored",
            "flags": {}, "flagKeys": [], "requiredExecutables": [], "setups": [], "steps": [],
            "program": [part("first", &[], &["workflow"]), part("second", &["workflow"], &[])]}],
    });
    let bytes = document.to_string().into_bytes();
    let suite = suites_of(vec![(parse_suite_program(&bytes).unwrap(), bytes)]).unwrap().remove(0);
    let ran = run(&fake, &[suite], None);
    let finished = ran.events("testFinished");
    assert_eq!(
        finished[0]["status"], "FAILED",
        "the second part answers a value it does not declare"
    );
    assert_eq!(
        finished[0]["message"],
        "the part 'authored-step(part=second,scenario=commit,suite=workflow-git,timeoutMs=300000)' answered the values [workflow], and it declares []"
    );
    assert_eq!(finished[0]["methodName"], "gitScenarios");
    assert_eq!(
        finished[0]["report"],
        json!({"notes": ["committed"], "metrics": {"latencyMs": 12.5}})
    );
    let parts = fake.bodies("action/authored-step");
    assert_eq!(
        parts[1],
        json!({"type": "action/authored-step", "part": "second", "scenario": "commit", "suite": "workflow-git",
            "timeoutMs": 300_000, "values": {"workflow": {"id": 7}}})
    );
    let scenario = ran.trace.iter().find(|line| line.starts_with(r#"{"op":"scenario""#)).unwrap();
    assert!(scenario.contains(r#"{"name":"testMethod","value":"gitScenarios"}"#), "{scenario}");
}

/// The authored document `lane-recycle-smoke`: the proof of the recycle, a restart, and one part that reads the driven
/// project on the restarted IDE.
fn recycle_smoke() -> Suite {
    let host = |restarts: u32| {
        json!({"side": "host", "timeoutMs": 840_000, "restarts": restarts, "answers": [], "mayAnswer": [], "takes": [],
            "mayTake": [], "params": {}})
    };
    let authored = json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": [], "mayAnswer": [], "takes": [],
        "mayTake": [], "params": {"part": {"type": "enum", "values": ["driven-project-read"], "optional": false},
            "scenario": {"type": "enum", "values": ["recycle"], "optional": false},
            "suite": {"type": "enum", "values": ["lane-recycle-smoke"], "optional": false},
            "timeoutMs": {"type": "int", "optional": false}}});
    let document = json!({
        "schemaVersion": 1, "suite": "lane-recycle-smoke", "lane": "UI", "testClassName": "AirFlowRecycleSmokeUiTest",
        "authored": true,
        "labels": [{"name": "testClass", "value": "com.intellij.air.integration.flow.AirFlowRecycleSmokeUiTest"}],
        "restartsIde": true, "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {"authored-step": authored,
            "recycle-through-failed-reset": host(1), "restart-ide": host(1)}, "assertions": {}},
        "reset": {"flagKeys": [], "offerJuniePromo": false, "laneFlags": {}},
        "fixtures": {"authored-lane-recycle-smoke": {}},
        "profiles": [{"name": "failed reset recycles one warm IDE and retries on the replacement", "key": "recycle",
            "method": "recycleSmokeScenarios", "fixture": "authored-lane-recycle-smoke", "flags": {}, "flagKeys": [],
            "requiredExecutables": [], "setups": [], "steps": [],
            "program": [
                {"action": "recycle-through-failed-reset"},
                {"action": "restart-ide"},
                {"action": "authored-step", "args": {"part": "driven-project-read", "scenario": "recycle",
                    "suite": "lane-recycle-smoke", "timeoutMs": 180_000}, "uses": {"answers": [], "takes": []}},
            ]}],
    });
    let bytes = document.to_string().into_bytes();
    let program = parse_suite_program(&bytes).unwrap();
    assert_eq!(program.refusals(), Vec::<String>::new());
    suites_of(vec![(program, bytes)]).unwrap().remove(0)
}

// The proof of the recycle fails its first reset on purpose. The runner recycles the IDE once and resets the
// replacement, then the program restarts the IDE and reads the driven project on the third IDE. The proof spends no
// recycle of the run, so a later reset that does not hold still finds the whole budget.
#[test]
fn the_recycle_proof_recycles_once_and_resets_the_replacement_on_a_new_pid() {
    let fake = Fake::new(Script::default());
    let ran = run(&fake, &[recycle_smoke()], None);
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "SUCCESSFUL", "{finished:?}");
    assert_eq!(
        *fake.relaunches.lock().unwrap(),
        [(Relaunch::Recycle, false), (Relaunch::Restart, false)]
    );
    let resets: Vec<u32> = fake
        .requests()
        .into_iter()
        .filter(|(_, kind)| kind == "Reset")
        .map(|(pid, _)| pid)
        .collect();
    assert_eq!(
        resets,
        [1, 2, 3],
        "the reset before the scenario, the reset of the replacement, and the reset after the scenario"
    );
    let part = fake
        .requests()
        .into_iter()
        .find(|(_, kind)| kind == "action/authored-step")
        .unwrap();
    assert_eq!(part.0, 3, "the part reads the IDE of the restart");
    assert!(
        ran.events("output")
            .iter()
            .any(|record| record["text"].as_str().unwrap().starts_with(
                "The IDE was recycled 1 time(s): before 'lane-recycle-smoke/recycle': the first reset of \
             'lane-recycle-smoke/recycle' fails on purpose"
            )),
        "{:?}",
        ran.events("output")
    );
    assert!(
        ran.trace.iter().any(|line| line.contains(r#""key":"restart:recycle""#)),
        "{:?}",
        ran.trace
    );
}

// A recycle that leaves the same IDE process on the bridge proves nothing, so the proof fails and names the pid.
#[test]
fn a_recycle_that_keeps_the_ide_process_fails_the_proof() {
    let fake = Fake::new(Script::default());
    fake.relaunch_keeps_pid.store(true, Ordering::SeqCst);
    let ran = run(&fake, &[recycle_smoke()], None);
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "FAILED");
    assert_eq!(finished[0]["type"], "lane_host_id");
    assert_eq!(finished[0]["message"], "the recycle kept the IDE process 1");
}

// The transport of an instruction waits its deadline and a margin. An IDE that does not answer by then fails the
// scenario, and the failure names the instruction.
#[test]
fn an_instruction_past_its_deadline_fails_and_is_named() {
    let fake = Fake::new(Script {
        delays: BTreeMap::from([("action/rename-session-select".to_owned(), Duration::from_secs(2))]),
        ..Script::default()
    });
    let mut select = ide(&[], &[], &[]);
    select["timeoutMs"] = json!(200);
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": select}, "assertions": {}});
    let instructions = json!([{"action": "rename-session-select"}]);
    let ran = run_with(
        &fake,
        &[story(&vocabulary, &json!([scenario("hangs", &instructions)]))],
        None,
        Duration::from_millis(100),
        Duration::from_mins(30),
    );
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "FAILED");
    assert_eq!(finished[0]["type"], kind::BRIDGE);
    let message = finished[0]["message"].as_str().unwrap();
    assert!(
        message.starts_with("the instruction 'rename-session-select' got no answer: POST /api/air/ui-test: the deadline passed"),
        "{message}"
    );
}

// A scenario past the active execution timeout expires the run: the watchdog writes `watchdogExpired` and kills the IDE.
// The request in flight then fails, and the scenario ends as ABORTED, not FAILED, because its failure is no verdict about
// it. No later scenario starts.
#[test]
fn a_scenario_past_its_timeout_is_aborted_and_no_later_scenario_starts() {
    let fake = Fake::new(Script {
        delays: BTreeMap::from([("action/rename-session-select".to_owned(), Duration::from_millis(2_500))]),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let instructions = json!([{"action": "rename-session-select"}]);
    let profiles = json!([scenario("hangs", &instructions), scenario("never", &instructions)]);
    let ran = run_with(
        &fake,
        &[story(&vocabulary, &profiles)],
        Some(Duration::from_mins(1)),
        INSTRUCTION_MARGIN,
        Duration::from_millis(500),
    );
    assert_eq!(ran.events("watchdogExpired").len(), 1, "{:?}", ran.records);
    assert_eq!(ran.events("watchdogExpired")[0]["reason"], "active_execution_timeout");
    let finished = ran.events("testFinished");
    assert_eq!(finished.len(), 1);
    assert_eq!(finished[0]["status"], "ABORTED");
    assert_eq!(ran.events("testStarted").len(), 1);
    let done = ran.trace.iter().find(|line| line.starts_with(r#"{"op":"done""#)).unwrap();
    assert!(done.starts_with(r#"{"op":"done","status":"aborted""#), "{done}");
}
