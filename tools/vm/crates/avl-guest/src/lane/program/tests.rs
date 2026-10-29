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
use crate::lane::inputs::TraceInputs;
use crate::lane::stream::Counts;
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
    /// The agent key of each attach of the Agent Spawner, in order.
    pub spawner_attaches: Mutex<Vec<Option<String>>>,
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

    fn cloud_attach_spawner(&self, agent_key: Option<&str>) -> Result<(), String> {
        self.spawner_attaches.lock().unwrap().push(agent_key.map(str::to_owned));
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
    /// The answers of the successive `ResetManagedAgents` requests; a request past the list holds.
    managed_agent_resets: VecDeque<Value>,
    /// The failure of each request type that fails.
    failures: BTreeMap<String, Value>,
    /// The value of each request type that answers one; any other answers `{}`.
    answers: BTreeMap<String, Value>,
    /// How long each request type takes.
    delays: BTreeMap<String, Duration>,
    /// The successive answers of each GET route, its content type and its bytes. A route past its answers is 404.
    routes: BTreeMap<String, VecDeque<(String, Vec<u8>)>>,
    requests: Vec<(u32, Value)>,
}

/// The fake lane: the fake bridge, the pid of the IDE, its context directory, and the lane services.
struct Fake {
    _http: FakeHttp,
    client: avl_bridge::Client,
    script: Arc<Mutex<Script>>,
    pid: Arc<AtomicU32>,
    relaunches: Mutex<Vec<Relaunch>>,
    context: TempDir,
    services: FakeServices,
    /// Set by the kill of the watchdog. A request that answers after it fails, as a request to a dead IDE does.
    killed: Arc<AtomicBool>,
    /// A relaunch keeps the pid, as a launch that left the old IDE on the bridge port would.
    relaunch_keeps_pid: AtomicBool,
    /// The part of a relaunch that a refusal ends, as an IDE that does not get ready ends its readiness part.
    refused_part: Mutex<Option<RelaunchPart>>,
    /// The launches and the ends of a bare IDE, in order.
    bare: Mutex<Vec<&'static str>>,
    /// A bare launch fails, as an IDE that never gets ready does.
    bare_launch_fails: AtomicBool,
    /// The deadline of each reload of Air, in order.
    air_reloads: Mutex<Vec<Duration>>,
    /// A reload of Air fails, as one that the resident plugin refuses does.
    air_reload_fails: AtomicBool,
    /// The Junie sessions that the live run finds, and the count of the refreshes of the live wiring.
    leftovers: Mutex<Vec<String>>,
    live_refreshes: AtomicU32,
}

impl Fake {
    fn new(script: Script) -> Self {
        let script = Arc::new(Mutex::new(script));
        let pid = Arc::new(AtomicU32::new(1));
        let killed = Arc::new(AtomicBool::new(false));
        let http = {
            let (script, pid, killed) = (Arc::clone(&script), Arc::clone(&pid), Arc::clone(&killed));
            FakeHttp::start(move |request| {
                if request.method == "GET" {
                    let mut script = script.lock().unwrap();
                    script
                        .requests
                        .push((pid.load(Ordering::SeqCst), json!({ "type": format!("GET {}", request.path) })));
                    return match script.routes.get_mut(&request.path).and_then(VecDeque::pop_front) {
                        Some((content_type, body)) => Response::ok(&content_type, body),
                        None => Response::error(404, "no such attachment"),
                    };
                }
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
            refused_part: Mutex::new(None),
            bare: Mutex::new(Vec::new()),
            bare_launch_fails: AtomicBool::new(false),
            air_reloads: Mutex::new(Vec::new()),
            air_reload_fails: AtomicBool::new(false),
            leftovers: Mutex::new(Vec::new()),
            live_refreshes: AtomicU32::new(0),
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
        "ResetManagedAgents" => script.managed_agent_resets.pop_front().unwrap_or(Value::Null),
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

    fn log_dir(&self) -> Option<PathBuf> {
        None
    }
}

impl Lane for Fake {
    fn call(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error> {
        self.client
            .post_json("/api/air/ui-test", &request.to_string(), Instant::now() + within)
    }

    /// Runs the parts of a restart in the order of the owner: the launch counts the pid up, a refused part ends the
    /// relaunch, and a failure of `while_down` fails its part and is the failure of the relaunch after its last part.
    fn relaunch(
        &self,
        cause: Relaunch,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
        parts: &mut dyn RelaunchParts,
    ) -> Result<(), String> {
        self.relaunches.lock().unwrap().push(cause);
        let refused_part = *self.refused_part.lock().unwrap();
        let mut down = Ok(());
        for part in [
            RelaunchPart::Quit,
            RelaunchPart::Down,
            RelaunchPart::Launch,
            RelaunchPart::Ready,
            RelaunchPart::Ping,
        ] {
            parts.begin(part);
            if part == RelaunchPart::Quit {
                for inner in [RelaunchPart::QuitRequest, RelaunchPart::QuitExit] {
                    parts.begin(inner);
                    parts.end(inner, None);
                }
            }
            match part {
                RelaunchPart::Down => down = while_down(self.context.path()),
                RelaunchPart::Launch if !self.relaunch_keeps_pid.load(Ordering::SeqCst) => {
                    self.pid.fetch_add(1, Ordering::SeqCst);
                }
                _ => {}
            }
            let refuse = |code: &'static str, message: &str| {
                <crate::reply::AgentRefusal as crate::reply::AgentRefusalExt>::refused(code, message.to_owned())
            };
            if refused_part == Some(part) {
                let refusal = refuse(crate::lane::readiness::NOT_READY_CODE, "the IDE did not get ready");
                parts.end(part, Some(&refusal));
                return Err(format!("{}: {}", refusal.code, refusal.message));
            }
            let down_failure = match (part, &down) {
                (RelaunchPart::Down, Err(error)) => Some(refuse(crate::lane::ide::CODE, error)),
                _ => None,
            };
            parts.end(part, down_failure.as_ref());
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

    fn launch_bare(&self, key: &str) -> Result<(), String> {
        assert_eq!(key, "air-startup-class-loading");
        self.bare.lock().unwrap().push("launch");
        if self.bare_launch_fails.load(Ordering::SeqCst) {
            return Err("lane_ide_not_ready: the IDE of 'air-startup-class-loading' did not get ready".to_owned());
        }
        self.pid.fetch_add(1, Ordering::SeqCst);
        Ok(())
    }

    fn end_bare(&self) -> Result<(), String> {
        self.bare.lock().unwrap().push("end");
        Ok(())
    }

    fn trace_get(&self, route: &str, accept: &str, within: Duration) -> Result<Vec<u8>, avl_bridge::Error> {
        self.client.get(route, accept, Instant::now() + within)
    }

    fn reload_air(&self, within: Duration) -> Result<(), String> {
        self.air_reloads.lock().unwrap().push(within);
        if self.air_reload_fails.load(Ordering::SeqCst) {
            return Err("the resident plugin refused the reload of Air: a reload runs".to_owned());
        }
        Ok(())
    }

    fn refresh_live_wiring(&self) -> Result<(), String> {
        self.live_refreshes.fetch_add(1, Ordering::SeqCst);
        Ok(())
    }

    fn leftover_junie_sessions(&self) -> Result<Vec<String>, String> {
        Ok(self.leftovers.lock().unwrap().clone())
    }
}

/// The spec of an IDE id with these values.
fn ide(answers: &[&str], may_answer: &[&str], takes: &[&str]) -> Value {
    json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": answers, "mayAnswer": may_answer, "takes": takes,
        "mayTake": [], "params": {"surface": {"type": "enum", "values": ["GLOBAL_PROMPT"], "optional": true}}})
}

/// A story suite document of `profiles` over `vocabulary`, whose fixture `fixture` carries its bridge requests.
fn story(vocabulary: &Value, profiles: &Value) -> Suite {
    suite_of(&story_document(vocabulary, profiles))
}

/// A story suite with one scenario for each of `names`, whose one instruction is the action `rename-session-select`.
fn select_story(names: &[&str]) -> Suite {
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let instructions = json!([{"action": "rename-session-select"}]);
    let profiles: Vec<Value> = names.iter().map(|name| scenario(name, &instructions)).collect();
    story(&vocabulary, &Value::Array(profiles))
}

/// A story suite with the one scenario `renames`, whose one instruction is the assertion `rename-session-title`.
fn title_story() -> Suite {
    let vocabulary = json!({"runnerAnswers": [], "actions": {}, "assertions": {"rename-session-title": ide(&[], &[], &[])}});
    let instructions = json!([{"assertion": "rename-session-title", "expectation": "The title shows."}]);
    story(&vocabulary, &json!([scenario("renames", &instructions)]))
}

/// The document of [`story`].
fn story_document(vocabulary: &Value, profiles: &Value) -> Value {
    json!({
        "schemaVersion": 2, "suite": "rename-session", "lane": "UI", "testClassName": "AirRenameSessionGeneratedFlowUiTest",
        "labels": [
            {"name": "testClass", "value": "com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest"},
            {"name": "testMethod", "value": "generatedFlowScenarios"},
            {"name": "Subsystem", "value": "AI"},
        ],
        "restartsIde": false, "modules": [], "implementationFlows": [],
        "vocabulary": vocabulary,
        "reset": {"flagKeys": ["air.inline.empty.state.prompt"], "laneFlags": {},
            "agentLoginAcpAgentIds": ["acp.air-scenario-rename"]},
        "agents": {},
        "fixtures": {"fixture": {"managedAgentStartsInstalled": false, "bridge": {
            "setScenarioFixture": {"type": format!("{BRIDGE}SetScenarioFixture"), "facts": {"preset": {"id": "preset-1"}}},
            "seedSessionPresets": {"type": format!("{BRIDGE}SeedSessionPresets"), "presets": [{"id": "preset-1"}],
                "refreshAvailability": true, "timeoutMs": 30_000},
        }}},
        "profiles": profiles,
    })
}

/// The suite of `document`, which must fit its vocabulary.
fn suite_of(document: &Value) -> Suite {
    suite_of_bytes(document.to_string().into_bytes())
}

/// The suite of the document `bytes`, which must fit its vocabulary.
fn suite_of_bytes(bytes: Vec<u8>) -> Suite {
    let program = parse_suite_program(&bytes).unwrap();
    assert_eq!(program.refusals(), Vec::<String>::new());
    suites_of(vec![(program, bytes)]).unwrap().remove(0)
}

/// The vocabulary entry of the IDE part `authored-step` of the suite `suite`: the parts `parts` of the scenario
/// `scenario`, each with a deadline of 180 s.
fn authored_step(suite: &str, scenario: &str, parts: &[&str]) -> Value {
    json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": [], "mayAnswer": [], "takes": [], "mayTake": [],
        "params": {"part": {"type": "enum", "values": parts, "optional": false},
            "scenario": {"type": "enum", "values": [scenario], "optional": false},
            "suite": {"type": "enum", "values": [suite], "optional": false},
            "timeoutMs": {"type": "int", "optional": false}}})
}

/// The authored suite document `suite` of the lane `UI`. Its test class is `class` in the package `package` of the
/// integration tests, its vocabulary has `actions`, its one fixture `authored` is empty, and its profiles are
/// `profiles`.
fn authored_document(suite: &str, package: &str, class: &str, actions: &Value, profiles: &Value) -> Value {
    json!({
        "schemaVersion": 2, "suite": suite, "lane": "UI", "testClassName": class, "authored": true,
        "labels": [{"name": "testClass", "value": format!("com.intellij.air.integration.{package}.{class}")}],
        "restartsIde": false, "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": actions, "assertions": {}},
        "reset": {"flagKeys": [], "laneFlags": {}, "agentLoginAcpAgentIds": []},
        "fixtures": {"authored": {}},
        "profiles": profiles,
    })
}

/// One story scenario `name` whose one step runs `instructions`.
fn scenario(name: &str, instructions: &Value) -> Value {
    json!({"name": name, "fixture": "fixture", "flow": "flow-rename-session", "flags": {"air.inline.empty.state.prompt": "true"},
        "followsLastUsedPreset": false, "requiresPromptProbe": false, "setups": [],
        "steps": [{"flow": "flow-rename-session", "step": "rename", "operations": [{"operation": "rename", "instructions": instructions}]}]})
}

/// The routes of a fake IDE that queued the trace attachments of `list`: the list, and the bytes of each attachment of
/// `files` by its id.
fn attachment_routes<const N: usize>(list: &Value, files: [(&str, (String, Vec<u8>)); N]) -> BTreeMap<String, VecDeque<(String, Vec<u8>)>> {
    let listed = (
        ATTACHMENTS_ROUTE.to_owned(),
        VecDeque::from([("application/json".to_owned(), list.to_string().into_bytes())]),
    );
    std::iter::once(listed)
        .chain(
            files
                .into_iter()
                .map(|(id, file)| (format!("{ATTACHMENTS_ROUTE}/{id}"), VecDeque::from([file]))),
        )
        .collect()
}

/// What one run of the runner wrote: the records of the stream, the lines of the trace, and the iteration directory.
struct Ran {
    records: Vec<Value>,
    trace: Vec<String>,
    /// The counts of the summary of the run.
    counts: Counts,
    _iteration: TempDir,
}

impl Ran {
    fn events(&self, event: &str) -> Vec<&Value> {
        self.records.iter().filter(|record| record["event"] == event).collect()
    }

    /// The display name and the status of each finished case, in the order of the stream.
    fn finished_statuses(&self) -> Vec<(String, String)> {
        self.events("testFinished")
            .iter()
            .map(|record| {
                (
                    record["displayName"].as_str().unwrap().to_owned(),
                    record["status"].as_str().unwrap().to_owned(),
                )
            })
            .collect()
    }

    /// The attach commands of the trace: the name of each, the extension of its file, and its line.
    fn attachments(&self) -> impl Iterator<Item = (String, String, &str)> {
        self.trace.iter().filter(|line| line.starts_with(r#"{"op":"attach""#)).map(|line| {
            let command: Value = serde_json::from_str(line).unwrap();
            let extension = command["file"].as_str().unwrap().rsplit('.').next().unwrap().to_owned();
            (command["name"].as_str().unwrap().to_owned(), extension, line.as_str())
        })
    }

    /// Asserts that the trace closes the run with the status `aborted`.
    fn assert_done_aborted(&self) {
        let done = self.trace.iter().find(|line| line.starts_with(r#"{"op":"done""#)).unwrap();
        assert!(done.starts_with(r#"{"op":"done","status":"aborted""#), "{done}");
    }

    /// The step and the subject of each `timing` record, in stream order. Each record also states its time.
    fn timings(&self) -> Vec<(String, String)> {
        self.events("timing")
            .iter()
            .map(|record| {
                assert!(record["durationMs"].is_i64() && record["timestamp"].is_string(), "{record}");
                (
                    record["step"].as_str().unwrap().to_owned(),
                    record["subject"].as_str().unwrap_or_default().to_owned(),
                )
            })
            .collect()
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
    run_set(
        fake,
        suites,
        &Setup {
            slow_step,
            margin,
            active,
            ..Setup::default()
        },
    )
}

/// What a test run sets beside its suites: the slow-step budget, the transport margin and the active execution timeout
/// of the watchdog, the recorder, the lane document and the recorded sessions.
struct Setup {
    slow_step: Option<Duration>,
    margin: Duration,
    active: Duration,
    /// The budget of the whole run, from the start of the run.
    run_budget: Option<Duration>,
    recording: Recording,
    document: LaneDocument,
    session_logs: Vec<StagedLog>,
    /// The rerun of a `lane-run`, or `None` for the lane daemon.
    rerun: Option<Rerun>,
}

impl Default for Setup {
    fn default() -> Self {
        Self {
            slow_step: None,
            margin: INSTRUCTION_MARGIN,
            active: Duration::from_mins(30),
            run_budget: None,
            recording: Recording::default(),
            document: lane_document(),
            session_logs: Vec::new(),
            rerun: None,
        }
    }
}

/// Runs `suites` against `fake` as `setup` says.
fn run_set(fake: &Fake, suites: &[Suite], setup: &Setup) -> Ran {
    let Setup {
        slow_step,
        margin,
        active,
        run_budget,
        recording,
        document,
        session_logs,
        rerun,
    } = setup;
    let (slow_step, margin, active) = (*slow_step, *margin, *active);
    let iteration = TempDir::new().unwrap();
    let trace = Trace::open(
        Box::new(recording.clone()),
        Trace::hello(iteration.path(), "iter-golden", &TraceInputs::default(), fake.recorder_bridge()),
    );
    let mut out = Vec::new();
    let counts = {
        let stream = Stream::new(&mut out);
        let policy = Policy {
            active_execution: active,
            progress_gap: Duration::from_mins(5),
            run_end: run_budget.map(|budget| Instant::now() + budget),
        };
        let watch = match slow_step {
            Some(budget) => Watch::new(policy).with_slow_step(budget),
            None => Watch::new(policy),
        };
        std::thread::scope(|scope| {
            if slow_step.is_some() {
                scope.spawn(|| crate::lane::watchdog::watch(&watch, &stream, fake, iteration.path()));
            }
            Runner::new(fake, &stream, &watch, document, iteration.path(), trace)
                .with_instruction_margin(margin)
                .with_session_logs(session_logs.clone())
                .with_rerun(rerun.clone())
                .run(suites);
            watch.completed(&stream);
        });
        stream.counts()
    };
    let records = String::from_utf8(out)
        .unwrap()
        .lines()
        .map(|line| serde_json::from_str(line).unwrap())
        .collect();
    let trace = recording.lines.lock().unwrap().clone();
    for line in &trace {
        // The recorder reads one command per line.
        assert!(!line.contains('\n'), "a command spans lines: {line}");
        protocol::decode_command(line.as_bytes()).unwrap_or_else(|error| panic!("the recorder refuses {line}: {error}"));
    }
    Ran {
        records,
        trace,
        counts,
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

/// The vocabulary and the one scenario of the green story suite.
fn green_story() -> (Value, Value) {
    let vocabulary = json!({"runnerAnswers": ["jetBrainsCloudUrl", "terminalBaseline"],
        "actions": {"rename-session-select": ide(&["session"], &[], &["terminalBaseline"])},
        "assertions": {"rename-session-title": ide(&[], &[], &["session", "jetBrainsCloudUrl"])}});
    let instructions = json!([
        {"action": "rename-session-select", "args": {"surface": "GLOBAL_PROMPT"}},
        {"assertion": "rename-session-title", "expectation": "The session is listed under the new title."},
    ]);
    (vocabulary, scenario("rename-closed-project-session", &instructions))
}

fn green_fake() -> Fake {
    Fake::new(Script {
        answers: BTreeMap::from([("action/rename-session-select".to_owned(), json!({"session": {"id": "s-1"}}))]),
        ..Script::default()
    })
}

// The generator writes each suite document pretty-printed, and the recorder reads one command per line. So the
// program of a scenario reaches the recorder on one line, as the same value, and the stream says no trace failure.
#[test]
fn a_pretty_printed_suite_document_reaches_the_recorder_one_line_per_command() {
    let fake = green_fake();
    let (vocabulary, profile) = green_story();
    let document = story_document(&vocabulary, &json!([profile]));
    let ran = run(&fake, &[suite_of_bytes(serde_json::to_vec_pretty(&document).unwrap())], None);
    let scenario: Value = serde_json::from_str(ran.trace.iter().find(|line| line.starts_with(r#"{"op":"scenario""#)).unwrap()).unwrap();
    assert_eq!(scenario["program"], profile);
    assert_eq!(ran.events("traceFailed"), Vec::<&Value>::new());
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
}

// A recorder that refuses the scenarios records no bundle. The stream says the first cause once as `traceFailed`, and
// the scenarios run as they would with no recorder.
#[test]
fn a_recorder_that_refuses_the_scenarios_is_one_trace_failure_of_the_stream() {
    let fake = green_fake();
    let (vocabulary, profile) = green_story();
    let mut second = profile.clone();
    second["name"] = json!("rename-again");
    let recording = Recording {
        refuse_scenarios: true,
        ..Recording::default()
    };
    let ran = run_set(
        &fake,
        &[story(&vocabulary, &json!([profile, second]))],
        &Setup {
            recording,
            ..Setup::default()
        },
    );
    let failed = ran.events("traceFailed");
    assert_eq!(failed.len(), 1, "{failed:#?}");
    assert_eq!(
        failed[0]["reason"],
        "the recorder refused the scenario 'rename-closed-project-session': a command line is not a JSON object"
    );
    assert_eq!(ran.trace.len(), 3, "{:#?}", ran.trace);
    let statuses: Vec<&Value> = ran.events("testFinished").iter().map(|finished| &finished["status"]).collect();
    assert_eq!(statuses, [&json!("SUCCESSFUL"), &json!("SUCCESSFUL")]);
}

// A green scenario: the baseline, the reset before, the preparation chain, each instruction as one request with its
// arguments in camel case, the values it takes and its deadline, then the reset after. The trace is the golden.
#[test]
fn a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript() {
    let fake = green_fake();
    let (vocabulary, profile) = green_story();
    let ran = run(&fake, &[story(&vocabulary, &json!([profile]))], None);

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
    assert_eq!(
        resets
            .iter()
            .map(|reset| reset["agentLoginAcpAgentIds"].clone())
            .collect::<Vec<_>>(),
        [json!(["acp.air-scenario-rename"]), json!(["acp.air-scenario-rename"])]
    );
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
    let scenario = |step: &str| (step.to_owned(), "rename-closed-project-session".to_owned());
    assert_eq!(
        ran.timings(),
        [scenario("services-reset"), scenario("services-reset"), scenario("recorder-close")],
        "the reset of the lane services on each side, then the close of the bundle"
    );
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
    let ran = run(&fake, &[title_story()], Some(Duration::from_millis(100)));
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

// A story instruction that the IDE failed reads the trace attachments that the IDE queued, as a part does. The log of
// each session that the IDE shows goes to the span of the failed instruction.
#[test]
fn a_failed_story_instruction_files_the_session_logs_that_the_ide_queued() {
    let failure = json!({"type": "java.lang.IllegalStateException", "message": "the popup row does not name the session"});
    let list = json!({"attachments": [
        {"id": "a1", "kind": "file", "name": "events.jsonl of acp:s-1", "mime": "text/plain", "bytes": 10, "required": true,
            "atMs": 1_790_000_000_000_i64},
    ], "dropped": 0});
    let fake = Fake::new(Script {
        failures: BTreeMap::from([("assertion/rename-session-title".to_owned(), failure)]),
        routes: attachment_routes(&list, [("a1", ("text/plain".to_owned(), b"{\"seq\":0}\n".to_vec()))]),
        ..Script::default()
    });
    let ran = run(&fake, &[title_story()], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "FAILED");
    let after_assertion: Vec<String> = fake
        .requests()
        .into_iter()
        .map(|(_, kind)| kind)
        .skip_while(|kind| kind != "assertion/rename-session-title")
        .skip(1)
        .take(2)
        .collect();
    assert_eq!(
        after_assertion,
        [
            "GET /api/air/ui-test-trace/attachments",
            "GET /api/air/ui-test-trace/attachments/a1"
        ]
    );
    let attached: Vec<(String, String)> = ran.attachments().map(|(name, extension, _)| (name, extension)).collect();
    assert_eq!(attached, [("events.jsonl of acp:s-1".to_owned(), "txt".to_owned())]);
}

/// The `relaunch` spans of the trace under the `restart` span of `key`: each key, and the status and the error type of
/// its end.
fn relaunch_parts(trace: &[String], key: &str) -> Vec<(String, String, Option<String>)> {
    let lines: Vec<Value> = trace.iter().map(|line| serde_json::from_str::<Value>(line).unwrap()).collect();
    let restart = lines
        .iter()
        .find(|line| line["op"] == "span" && line["key"] == key)
        .unwrap_or_else(|| panic!("no span {key}: {trace:?}"));
    lines
        .iter()
        .filter(|line| line["op"] == "span" && line["parent"] == restart["id"])
        .map(|span| {
            let end = lines.iter().find(|line| line["op"] == "end" && line["id"] == span["id"]).unwrap();
            (
                span["key"].as_str().unwrap().to_owned(),
                end["status"].as_str().unwrap().to_owned(),
                end["error"]["type"].as_str().map(str::to_owned),
            )
        })
        .collect()
}

// A recycle whose IDE does not get ready ends its readiness part with the code of the refusal, and runs no part after it.
#[test]
fn a_recycle_that_does_not_get_ready_fails_its_ready_part_with_the_refusal_code() {
    let fake = Fake::new(Script {
        resets: VecDeque::from([json!("2 editor tabs remain open")]),
        ..Script::default()
    });
    *fake.refused_part.lock().unwrap() = Some(RelaunchPart::Ready);
    let ran = run(&fake, &[select_story(&["renames"])], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "FAILED");
    let passed = |key: &str| (key.to_owned(), "passed".to_owned(), None);
    assert_eq!(
        relaunch_parts(&ran.trace, "restart:recycle"),
        [
            passed("relaunch:quit"),
            passed("relaunch:down"),
            passed("relaunch:launch"),
            (
                "relaunch:ready".to_owned(),
                "failed".to_owned(),
                Some(crate::lane::readiness::NOT_READY_CODE.to_owned())
            ),
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
    let ran = run(&fake, &[select_story(&["renames"])], None);
    assert_eq!(*fake.relaunches.lock().unwrap(), [Relaunch::Recycle]);
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
    let ran = run(&fake, &[select_story(&["first", "second", "third"])], None);
    assert_eq!(fake.relaunches.lock().unwrap().len(), MAX_RECYCLES_IN_TEST);
    let statuses = ran.finished_statuses();
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

// `restart-ide` performs `whileDown` while the IDE is down, states the fixture again to the new IDE, and answers
// `whileDownReads` to the next instruction.
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
    assert_eq!(*fake.relaunches.lock().unwrap(), [Relaunch::Restart]);
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
    let spans: Vec<Value> = ran
        .trace
        .iter()
        .map(|line| serde_json::from_str::<Value>(line).unwrap())
        .filter(|line| line["op"] == "span")
        .collect();
    let under = |parent: &str| -> Vec<(&str, &str)> {
        let parent = spans.iter().find(|span| span["key"] == parent).unwrap();
        spans
            .iter()
            .filter(|span| span["parent"] == parent["id"])
            .map(|span| (span["key"].as_str().unwrap(), span["title"].as_str().unwrap()))
            .collect()
    };
    assert_eq!(
        under("restart:ide"),
        [
            ("relaunch:quit", "[relaunch] quit"),
            ("relaunch:down", "[relaunch] while down"),
            ("relaunch:launch", "[relaunch] launch"),
            ("relaunch:ready", "[relaunch] ready"),
            ("relaunch:ping", "[relaunch] robot and ping"),
        ]
    );
    assert_eq!(
        under("relaunch:quit"),
        [
            ("relaunch:quit/request", "[relaunch] quit request"),
            ("relaunch:quit/exit", "[relaunch] process exit")
        ]
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

/// The lane document of the tests with the Cloud agents `keys`, each a copy of its first agent.
fn with_cloud_agents(keys: &[(&str, bool)]) -> LaneDocument {
    let mut document = lane_document();
    for (key, controlled) in keys {
        let mut cloud = document.agents[0].clone();
        cloud.key = (*key).to_owned();
        cloud.delivery = Delivery::Cloud;
        cloud.controlled = *controlled;
        document.agents.push(cloud);
    }
    document
}

/// The green story whose fixture uses the agents `agents`.
fn story_with_agents(agents: &[&str]) -> Suite {
    let (vocabulary, profile) = green_story();
    let mut suite = story(&vocabulary, &json!([profile]));
    suite.program.fixtures.get_mut("fixture").unwrap().agents = agents.iter().map(|key| (*key).to_owned()).collect();
    suite
}

// The prologue of a suite points the Agent Spawner at the Cloud agent of its fixture, so a dial of the IDE reaches the
// control server under that key, and the suite runs.
#[test]
fn a_suite_with_a_cloud_agent_attaches_the_spawner_to_its_key_and_runs() {
    let fake = green_fake();
    let suite = story_with_agents(&["air-ui-flow-acp", "remote-scenarios"]);
    let ran = run_set(
        &fake,
        &[suite],
        &Setup {
            document: with_cloud_agents(&[("remote-scenarios", true)]),
            ..Setup::default()
        },
    );
    assert_green_with_spawner(&ran, &fake, &[Some("remote-scenarios".to_owned())]);
}

/// Asserts that the suite of `ran` passed without a container failure, and that it attached the spawner to `attaches`.
fn assert_green_with_spawner(ran: &Ran, fake: &Fake, attaches: &[Option<String>]) {
    assert_eq!(ran.events("containerFailed"), Vec::<&Value>::new());
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    assert_eq!(*fake.services.spawner_attaches.lock().unwrap(), attaches);
}

// A fixture with only a local agent of the lane is no Cloud suite: it detaches the spawner, and runs.
#[test]
fn a_suite_with_a_local_agent_detaches_the_spawner_and_runs() {
    let fake = green_fake();
    let ran = run(&fake, &[story_with_agents(&["air-ui-flow-acp"])], None);
    assert_green_with_spawner(&ran, &fake, &[None]);
}

// The spawner steers one key, and only a controlled agent has a credential of the control server. So a suite with two
// Cloud agents, or with an uncontrolled one, fails at once as one `lane_document` container, and no request reaches
// the IDE.
#[test]
fn a_suite_with_two_cloud_agents_or_an_uncontrolled_one_is_refused_before_any_request() {
    for (agents, document, reason) in [
        (
            vec!["remote-a", "remote-b"],
            with_cloud_agents(&[("remote-a", true), ("remote-b", true)]),
            "the suite 'rename-session' uses the Cloud agents [\"remote-a\", \"remote-b\"], and the Agent Spawner of the lane steers one",
        ),
        (
            vec!["remote-a"],
            with_cloud_agents(&[("remote-a", false)]),
            "the suite 'rename-session' uses the Cloud agent 'remote-a', which is not controlled, so the Agent Spawner has no credential for it",
        ),
    ] {
        let fake = green_fake();
        let ran = run_set(
            &fake,
            &[story_with_agents(&agents)],
            &Setup {
                document,
                ..Setup::default()
            },
        );
        let failed = ran.events("containerFailed");
        assert_eq!(failed.len(), 1, "{failed:#?}");
        assert_eq!(failed[0]["type"], kind::DOCUMENT);
        assert_eq!(failed[0]["message"], reason);
        assert_eq!(ran.events("testStarted"), Vec::<&Value>::new());
        assert_eq!(fake.requests(), Vec::<(u32, String)>::new());
        assert_eq!(*fake.services.spawner_attaches.lock().unwrap(), Vec::<Option<String>>::new());
    }
}

/// An authored suite `live-launch` of the lane `lane` whose program refreshes the live wiring before its part and hands
/// the Junie leftovers to the reset after it, as the Junie scenario of the live suite does. Its prologue hands them over
/// once before the first scenario.
fn live_suite(lane: &str) -> Suite {
    let host = json!({"side": "host", "timeoutMs": 480_000, "restarts": 0, "answers": [], "mayAnswer": [], "takes": [],
        "mayTake": [], "params": {}});
    let actions = json!({"authored-step": authored_step("live-launch", "generation", &["open"]), "refresh-live-wiring": host,
        "adopt-leftover-junie-sessions": host});
    let profiles = json!([{"name": "the composer launches a live session", "key": "generation", "fixture": "authored",
        "flags": {}, "flagKeys": [], "requiredExecutables": ["claude"], "setups": [], "steps": [],
        "program": [{"action": "refresh-live-wiring"},
            {"action": "authored-step", "args": {"part": "open", "scenario": "generation", "suite": "live-launch",
                "timeoutMs": 180_000}, "uses": {"takes": [], "answers": []}},
            {"action": "adopt-leftover-junie-sessions"}]}]);
    let mut document = authored_document("live-launch", "live", "AirLiveLaunchUiTest", &actions, &profiles);
    document["lane"] = json!(lane);
    document["prologueHostIds"] = json!(["adopt-leftover-junie-sessions"]);
    suite_of(&document)
}

/// The fixture lane document as the lane `UI_LIVE`, whose live context declares the runtimes `claude` and `junie`.
fn live_document() -> LaneDocument {
    let mut document = lane_document();
    document.lane = LIVE_LANE.to_owned();
    document.live = Some(avl_wire::program::lane::LiveContext {
        login_input: "AIR_LIVE_CENTRAL_LOGIN".to_owned(),
        login_source: "central login export".to_owned(),
        central_runtime: "central".to_owned(),
        central_port: 19516,
        central_agents: vec!["codex".to_owned()],
        runtimes: ["claude", "junie"]
            .map(|name| avl_wire::program::lane::LiveRuntime {
                name: name.to_owned(),
                environment: BTreeMap::new(),
            })
            .to_vec(),
        project_unpack_root: "/tmp/air-ui-live/project".to_owned(),
    });
    document
}

// Only a live run has the account that the live host ids read. So outside the live lane a suite whose program or
// prologue runs one fails at once as one `lane_document` container that names the word: no scenario starts, and no
// request reaches the IDE.
#[test]
fn a_suite_that_runs_a_live_host_id_outside_the_live_lane_is_refused_before_any_request() {
    let fake = Fake::new(Script::default());
    let ran = run(&fake, &[live_suite("UI")], None);
    let failed = ran.events("containerFailed");
    assert_eq!(failed.len(), 1, "{failed:#?}");
    assert_eq!(failed[0]["type"], kind::DOCUMENT);
    assert_eq!(
        failed[0]["message"],
        "the suite 'live-launch' runs the live host id 'refresh-live-wiring' outside the live lane"
    );
    assert_eq!(ran.events("testStarted"), Vec::<&Value>::new());
    assert_eq!(fake.requests(), Vec::<(u32, String)>::new());
}

// In the live lane the prologue hands each Junie session that an earlier run left to the reset, one
// `AdoptProviderSession` each, before the first reset. The program refreshes the live wiring before its part and hands
// the leftovers over again after it. A scenario may drive a CLI that the live context declares.
#[test]
fn the_live_host_ids_refresh_the_wiring_and_hand_the_junie_leftovers_to_the_reset() {
    let fake = Fake::new(Script::default());
    *fake.leftovers.lock().unwrap() = vec!["left-1".to_owned(), "left-2".to_owned()];
    let ran = run_set(
        &fake,
        &[live_suite("UI_LIVE")],
        &Setup {
            document: live_document(),
            ..Setup::default()
        },
    );

    assert_eq!(ran.events("containerFailed"), Vec::<&Value>::new());
    assert_eq!(ran.events("testFinished").len(), 1);
    let adopted: Vec<Value> = fake
        .script
        .lock()
        .unwrap()
        .requests
        .iter()
        .filter(|(_, request)| request["type"].as_str().is_some_and(|kind| kind.ends_with("AdoptProviderSession")))
        .map(|(_, request)| json!([request["agentId"], request["agentSessionId"]]))
        .collect();
    let left = [json!(["junie", "left-1"]), json!(["junie", "left-2"])];
    assert_eq!(adopted, [left.clone(), left].concat());
    assert_eq!(fake.live_refreshes.load(Ordering::SeqCst), 1);
    let kinds: Vec<String> = fake.requests().into_iter().map(|(_, kind)| kind).collect();
    let first_adopt = kinds.iter().position(|kind| kind.ends_with("AdoptProviderSession")).unwrap();
    let first_reset = kinds.iter().position(|kind| kind.ends_with("Reset")).unwrap();
    assert!(
        first_adopt < first_reset,
        "the prologue hands the leftovers over before the first reset: {kinds:?}"
    );
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
    let part = |name: &str, takes: &[&str], answers: &[&str]| {
        json!({"action": "authored-step", "args": {"part": name, "scenario": "commit", "suite": "workflow-git", "timeoutMs": 300_000},
            "uses": {"takes": takes, "answers": answers}})
    };
    let actions = json!({"authored-step": authored_step("workflow-git", "commit", &["first", "second"])});
    let profiles = json!([{"name": "a commit starts the workflow", "key": "commit", "method": "gitScenarios", "fixture": "authored",
        "flags": {}, "flagKeys": [], "requiredExecutables": [], "setups": [], "steps": [],
        "program": [part("first", &[], &["workflow"]), part("second", &["workflow"], &[])]}]);
    let document = authored_document("workflow-git", "flow.workflows", "AgentWorkflowGitUiTest", &actions, &profiles);
    let ran = run(&fake, &[suite_of(&document)], None);
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
    let actions = json!({"authored-step": authored_step("lane-recycle-smoke", "recycle", &["driven-project-read"]),
        "recycle-through-failed-reset": host(1), "restart-ide": host(1)});
    let profiles = json!([{"name": "failed reset recycles one warm IDE and retries on the replacement", "key": "recycle",
    "method": "recycleSmokeScenarios", "fixture": "authored-lane-recycle-smoke", "flags": {}, "flagKeys": [],
    "requiredExecutables": [], "setups": [], "steps": [],
    "program": [
        {"action": "recycle-through-failed-reset"},
        {"action": "restart-ide"},
        {"action": "authored-step", "args": {"part": "driven-project-read", "scenario": "recycle",
            "suite": "lane-recycle-smoke", "timeoutMs": 180_000}, "uses": {"answers": [], "takes": []}},
    ]}]);
    let mut document = authored_document("lane-recycle-smoke", "flow", "AirFlowRecycleSmokeUiTest", &actions, &profiles);
    document["restartsIde"] = json!(true);
    document["fixtures"] = json!({"authored-lane-recycle-smoke": {}});
    suite_of(&document)
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
    assert_eq!(*fake.relaunches.lock().unwrap(), [Relaunch::Recycle, Relaunch::Restart]);
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
    let lists: Vec<u32> = fake
        .requests()
        .into_iter()
        .filter(|(_, kind)| kind == "GET /api/air/ui-test-trace/attachments")
        .map(|(pid, _)| pid)
        .collect();
    assert_eq!(
        lists,
        [2, 3],
        "the runner reads the attachments before the restart quits the IDE, and after the part"
    );
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

/// The id of the start-up measurement of a bare IDE.
const STARTUP: &str = "startup-class-loading-within-budget";

/// The suite document `startup-class-loading`: one scenario of a bare IDE whose program is the start-up measurement.
fn startup_class_loading() -> Suite {
    let document = json!({
        "schemaVersion": 2, "suite": "startup-class-loading", "lane": "UI", "testClassName": "AirStartupClassLoadingBudgetUiTest",
        "authored": true, "launch": {"profile": "bare", "key": "air-startup-class-loading"},
        "labels": [{"name": "testClass", "value": "com.intellij.air.integration.startup.AirStartupClassLoadingBudgetUiTest"}],
        "restartsIde": false, "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {STARTUP: {"side": "ide", "timeoutMs": 600_000, "restarts": 0,
            "answers": [], "mayAnswer": [], "takes": [], "mayTake": [], "params": {}}}, "assertions": {}},
        "fixtures": {"bare": {}},
        "profiles": [{"name": "Air loads no more classes than its budget while a project opens", "fixture": "bare",
            "flags": {}, "flagKeys": [], "requiredExecutables": [], "setups": [], "steps": [],
            "program": [{"action": STARTUP}]}],
    });
    suite_of(&document)
}

// A suite of a bare IDE runs after the suites of the shared IDE, on an IDE of its own. The bare IDE gets no reset, no
// baseline and no preparation: its one request is the measurement. The tables that the measurement answers go into the
// run log and, once the scenario opened in the trace, to the recorder as an attachment.
#[test]
fn a_bare_suite_runs_last_on_its_own_ide_and_its_one_request_is_its_instruction() {
    let fake = Fake::new(Script {
        answers: BTreeMap::from([(
            format!("action/{STARTUP}"),
            json!({"attachments": [{"title": "Air start-up class loading", "contentType": "text/plain",
                "fileName": "air-startup-class-loading.txt", "text": "com.intellij.air: 3879 classes over 195 loaders"}]}),
        )]),
        ..Script::default()
    });
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let story = story(
        &vocabulary,
        &json!([scenario("renames", &json!([{"action": "rename-session-select"}]))]),
    );
    let ran = run(&fake, &[startup_class_loading(), story], None);
    assert_eq!(*fake.bare.lock().unwrap(), ["launch", "end"]);
    let on_bare: Vec<String> = fake
        .requests()
        .into_iter()
        .filter(|(pid, _)| *pid == 2)
        .map(|(_, kind)| kind)
        .collect();
    assert_eq!(on_bare, [format!("action/{STARTUP}")]);
    let finished = ran.finished_statuses();
    assert_eq!(
        finished,
        [
            ("renames".to_owned(), "SUCCESSFUL".to_owned()),
            (
                "Air loads no more classes than its budget while a project opens".to_owned(),
                "SUCCESSFUL".to_owned()
            ),
        ]
    );
    assert!(
        ran.events("output").iter().any(|record| record["text"]
            == format!("[attachment] {STARTUP}: Air start-up class loading\ncom.intellij.air: 3879 classes over 195 loaders")),
        "{:?}",
        ran.events("output")
    );
    let bare_trace: Vec<&String> = ran
        .trace
        .iter()
        .skip_while(|line| !line.contains(r#""name":"Air loads no more classes than its budget while a project opens""#))
        .collect();
    assert!(bare_trace[0].starts_with(r#"{"op":"scenario""#), "{bare_trace:?}");
    // The slice of a bare IDE starts at the first byte of its fresh log, so it holds the start-up records.
    assert!(bare_trace[0].ends_with(r#","logFrom":0}"#), "{bare_trace:?}");
    let timed = |step: &str, subject: &str| (step.to_owned(), subject.to_owned());
    let bare = "Air loads no more classes than its budget while a project opens";
    assert_eq!(
        ran.timings(),
        [
            timed("services-reset", "renames"),
            timed("services-reset", "renames"),
            timed("recorder-close", "renames"),
            timed("bare-launch", "startup-class-loading"),
            timed("bare-program", bare),
            timed("recorder-close", bare),
        ]
    );
    let shared = ran
        .trace
        .iter()
        .find(|line| line.starts_with(r#"{"op":"scenario""#) && line.contains(r#""name":"renames""#))
        .unwrap();
    assert!(!shared.contains("logFrom"), "{shared}");
    assert!(
        bare_trace[1].starts_with(r#"{"op":"attach""#) && bare_trace[1].contains(r#""name":"Air start-up class loading""#),
        "{bare_trace:?}"
    );
    assert!(
        bare_trace.iter().all(|line| !line.starts_with(r#"{"op":"span""#)),
        "the measurement ran before the scenario opened: {bare_trace:?}"
    );
}

// A bare IDE that does not get ready fails its suite as one container failure, and the lane gets its IDE back.
#[test]
fn a_bare_ide_that_does_not_get_ready_fails_its_suite_and_the_lane_takes_its_ide_back() {
    let fake = Fake::new(Script::default());
    fake.bare_launch_fails.store(true, Ordering::SeqCst);
    let ran = run(&fake, &[startup_class_loading()], None);
    assert_eq!(*fake.bare.lock().unwrap(), ["launch", "end"]);
    let failed = ran.events("containerFailed");
    assert_eq!(failed.len(), 1);
    assert_eq!(failed[0]["type"], "lane_launch");
    assert_eq!(
        failed[0]["message"],
        "the bare IDE of the suite 'startup-class-loading' did not get ready: lane_ide_not_ready: the IDE of \
         'air-startup-class-loading' did not get ready"
    );
    assert_eq!(ran.events("testStarted"), Vec::<&serde_json::Value>::new());
    assert!(fake.requests().is_empty(), "{:?}", fake.requests());
}

/// An authored suite of one part, `open`, which answers no value.
fn one_part_suite() -> Suite {
    let actions = json!({"authored-step": authored_step("genui-host", "theme", &["open"])});
    let profiles = json!([{"name": "the theme follows the IDE", "key": "theme", "fixture": "authored", "flags": {}, "flagKeys": [],
        "requiredExecutables": [], "setups": [], "steps": [],
        "program": [{"action": "authored-step", "args": {"part": "open", "scenario": "theme", "suite": "genui-host",
            "timeoutMs": 180_000}, "uses": {"takes": [], "answers": []}}]}]);
    suite_of(&authored_document("genui-host", "genui", "AirGenUiHostUiTest", &actions, &profiles))
}

// A failed part answers no report. So the runner reads the metrics that a performance body wrote to
// `air-performance.json` before the failure, and they reach the `testFinished` of the scenario beside its failure. A
// value that is no number is a note, and no metric.
#[test]
fn a_failed_part_reports_the_metrics_of_its_performance_file() {
    let failure = json!({"type": "java.lang.AssertionError", "message": "the open took too long", "stack": []});
    let fake = Fake::new(Script {
        failures: BTreeMap::from([("action/authored-step".to_owned(), failure)]),
        ..Script::default()
    });
    let artifacts = fake.context.path().join("air-authored-artifacts/genui-host/theme/open");
    fs::create_dir_all(&artifacts).unwrap();
    fs::write(
        artifacts.join("air-performance.json"),
        "{\"open.firstPaintMs\":120.5,\"open.freezes\":0,\"open.label\":\"x\"}\n",
    )
    .unwrap();
    let ran = run(&fake, &[one_part_suite()], None);
    let finished = ran.events("testFinished");
    // A failure without the field `aborted` is a verdict.
    assert_eq!(finished[0]["status"], "FAILED");
    assert_eq!((ran.counts.failed, ran.counts.skipped), (1, 0));
    assert_eq!(finished[0]["message"], "the open took too long");
    assert_eq!(
        finished[0]["report"],
        json!({"metrics": {"open.firstPaintMs": 120.5, "open.freezes": 0}})
    );
    let notes: Vec<&str> = ran
        .events("output")
        .iter()
        .filter_map(|note| note["text"].as_str())
        .filter(|text| text.contains("air-performance.json"))
        .collect();
    assert_eq!(notes.len(), 3, "{notes:#?}");
    assert!(
        notes[2].ends_with("the metric open.label of air-performance.json is no number: \"x\""),
        "{notes:#?}"
    );
}
// A body that aborts its scenario answers a failure that the bridge marks aborted. The scenario reached no verdict,
// so the case ends as ABORTED, and the run counts it as skipped, not as failed. The after reset still runs.
#[test]
fn an_authored_part_that_aborts_its_scenario_finishes_as_aborted_and_counts_as_skipped() {
    let failure = json!({"type": "com.intellij.air.integration.bridge.AirUiTestAbortException",
        "message": "IJAI-3115: the lane gives no AI source", "stack": [], "aborted": true});
    let fake = Fake::new(Script {
        failures: BTreeMap::from([("action/authored-step".to_owned(), failure)]),
        ..Script::default()
    });
    let ran = run(&fake, &[one_part_suite()], None);
    let finished = ran.events("testFinished");
    assert_eq!(finished.len(), 1, "{:?}", ran.records);
    assert_eq!(finished[0]["status"], "ABORTED");
    assert_eq!(finished[0]["message"], "IJAI-3115: the lane gives no AI source");
    assert_eq!((ran.counts.started, ran.counts.failed, ran.counts.skipped), (1, 0, 1));
    let requests = fake.requests();
    assert_eq!(requests.last().map(|(_, kind)| kind.as_str()), Some("Reset"), "{requests:?}");
    ran.assert_done_aborted();
}

// No instruction waits past the budget of the run: the deadline of a part is cut to the budget left, and the IDE gets
// the cut deadline as timeoutMs, so its body stops through its scope first.
#[test]
fn a_part_deadline_is_cut_to_the_budget_left() {
    let fake = Fake::new(Script::default());
    let ran = run_set(
        &fake,
        &[one_part_suite()],
        &Setup {
            run_budget: Some(Duration::from_secs(100)),
            ..Setup::default()
        },
    );
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    let parts = fake.bodies("action/authored-step");
    let timeout = parts[0]["timeoutMs"].as_u64().unwrap();
    assert!(
        timeout <= 100_000 && timeout > 90_000,
        "the part of 180000 ms got timeoutMs {timeout}"
    );

    let unbounded = Fake::new(Script::default());
    run(&unbounded, &[one_part_suite()], None);
    assert_eq!(unbounded.bodies("action/authored-step")[0]["timeoutMs"], 180_000);
}

// A part that answered reports its own metrics, so the runner reads no performance file after it.
#[test]
fn a_part_that_answered_reports_no_metric_of_its_performance_file() {
    let fake = Fake::new(Script::default());
    let artifacts = fake.context.path().join("air-authored-artifacts/genui-host/theme/open");
    fs::create_dir_all(&artifacts).unwrap();
    fs::write(artifacts.join("air-performance.json"), "{\"open.firstPaintMs\":120}\n").unwrap();
    let ran = run(&fake, &[one_part_suite()], None);
    let finished = ran.events("testFinished");
    assert_eq!(finished[0]["status"], "SUCCESSFUL");
    assert_eq!(finished[0].get("report"), None);
}

/// An authored suite of one part, `stage`, which takes the recorded sessions, and whose scenario requires them.
fn session_suite() -> Suite {
    let actions = json!({"authored-step": authored_step("real-session-cold-open", "cold-open", &["stage"])});
    let profiles = json!([{"name": "every recorded session opens", "key": "cold-open", "fixture": "authored", "flags": {},
        "flagKeys": [], "requiredExecutables": [], "requiresRunnerValues": ["realSessionLogs"], "setups": [], "steps": [],
        "program": [{"action": "authored-step", "args": {"part": "stage", "scenario": "cold-open",
            "suite": "real-session-cold-open", "timeoutMs": 180_000}, "uses": {"takes": ["realSessionLogs"], "answers": []}}]}]);
    let mut document = authored_document("real-session-cold-open", "acp", "AirRealSessionColdOpenUiTest", &actions, &profiles);
    document["vocabulary"]["runnerAnswers"] = json!(["realSessionLogs"]);
    suite_of(&document)
}

// A scenario that requires a runner value which the runner has not is skipped before its reset. No reset and no part
// of it reach the IDE.
#[test]
fn a_scenario_that_requires_an_absent_runner_value_is_skipped_before_its_reset() {
    let fake = Fake::new(Script::default());
    let ran = run(&fake, &[session_suite()], None);
    let skipped = ran.events("testSkipped");
    assert_eq!(skipped.len(), 1, "{skipped:#?}");
    assert_eq!(
        skipped[0]["reason"],
        "scenario 'every recorded session opens' never ran: the runner has no value 'realSessionLogs'"
    );
    assert_eq!(ran.events("testStarted"), Vec::<&Value>::new());
    let kinds: Vec<String> = fake.requests().into_iter().map(|(_, kind)| kind).collect();
    assert!(
        !kinds.iter().any(|kind| kind == "Reset" || kind == "action/authored-step"),
        "{kinds:?}"
    );
}

// The recorded sessions in the context are the runner value of a part that takes them: each label with the absolute
// path of its events file.
#[test]
fn the_recorded_sessions_are_the_runner_value_of_a_part_that_takes_them() {
    let fake = Fake::new(Script::default());
    let events = fake.context.path().join("air-real-session-logs/thread-7/events.jsonl");
    let ran = run_set(
        &fake,
        &[session_suite()],
        &Setup {
            session_logs: vec![StagedLog {
                label: "thread-7".to_owned(),
                events: events.clone(),
            }],
            ..Setup::default()
        },
    );
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    let parts = fake.bodies("action/authored-step");
    assert_eq!(
        parts[0]["values"],
        json!({"realSessionLogs": [{"label": "thread-7", "eventsPath": events.display().to_string()}]})
    );
}

// After a part, the runner lists the trace attachments that the IDE queued and fetches each one: the still of a step
// and a capture of the body go to the span of the part, as PNG attachments under their names. The count of dropped
// stills is a note. A fetch that fails is a note that says that the body named the file, and it fails no part.
#[test]
fn an_authored_part_files_the_stills_and_the_captures_that_the_ide_queued() {
    let list = json!({"attachments": [
        {"id": "a1", "kind": "still", "name": "after open", "mime": "image/png", "bytes": 3, "required": false,
            "atMs": 1_790_000_000_000_i64, "step": "open", "waitMs": 3, "paintMs": 40, "encodeMs": 25},
        {"id": "a2", "kind": "capture", "name": "01-session-view-pane-initial-theme", "mime": "image/png", "bytes": 3,
            "required": true, "atMs": 1_790_000_000_001_i64},
        {"id": "a3", "kind": "file", "name": "facts.txt", "mime": "text/plain; charset=utf-8", "bytes": 2, "required": true,
            "atMs": 1_790_000_000_002_i64},
    ], "dropped": 2});
    let png = |byte: u8| ("image/png".to_owned(), vec![0x89, byte, byte]);
    let fake = Fake::new(Script {
        routes: attachment_routes(&list, [("a1", png(1)), ("a2", png(2))]),
        ..Script::default()
    });
    let ran = run(&fake, &[one_part_suite()], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    let after_part: Vec<String> = fake
        .requests()
        .into_iter()
        .map(|(_, kind)| kind)
        .skip_while(|kind| kind != "action/authored-step")
        .skip(1)
        .take(4)
        .collect();
    assert_eq!(
        after_part,
        [
            "GET /api/air/ui-test-trace/attachments",
            "GET /api/air/ui-test-trace/attachments/a1",
            "GET /api/air/ui-test-trace/attachments/a2",
            "GET /api/air/ui-test-trace/attachments/a3",
        ]
    );
    let span_of = |line: &str| serde_json::from_str::<Value>(line).unwrap()["span"].clone();
    let part_span = ran
        .trace
        .iter()
        .find(|line| line.starts_with(r#"{"op":"span""#) && line.contains("[action] authored-step"))
        .map(|line| serde_json::from_str::<Value>(line).unwrap()["id"].clone())
        .unwrap();
    let attached: Vec<(String, String, Value)> = ran
        .attachments()
        .map(|(name, extension, line)| (name, extension, span_of(line)))
        .collect();
    assert_eq!(
        attached,
        [
            ("after open".to_owned(), "png".to_owned(), part_span.clone()),
            ("01-session-view-pane-initial-theme".to_owned(), "png".to_owned(), part_span),
        ]
    );
    let notes: Vec<&str> = ran
        .events("output")
        .iter()
        .filter_map(|record| record["text"].as_str())
        .filter(|text| text.starts_with("[attachment]"))
        .collect();
    assert_eq!(notes.len(), 2, "{notes:?}");
    assert!(
        notes[0].ends_with("the IDE dropped 2 still(s) before the runner read them"),
        "{notes:?}"
    );
    assert!(
        notes[1].contains("cannot fetch the file 'facts.txt' (a3) of the IDE, which its body named: "),
        "{notes:?}"
    );
}

/// The type of the install request of the managed agents.
const INSTALL: &str = "com.intellij.air.integration.bridge.InstallManagedAgents";

/// A story suite whose fixture starts with Codex installed, with the install request `install` or none.
fn installing_suite(install: Option<&Value>) -> Suite {
    let vocabulary = json!({"runnerAnswers": [], "actions": {"rename-session-select": ide(&[], &[], &[])}, "assertions": {}});
    let mut document = story_document(
        &vocabulary,
        &json!([scenario("renames", &json!([{"action": "rename-session-select"}]))]),
    );
    document["fixtures"]["fixture"]["preinstalled"] = json!(["codex"]);
    if let Some(install) = install {
        document["managedAgentInstall"] = install.clone();
    }
    suite_of(&document)
}

// A suite whose fixture starts with installed agents sends its install request once, after the managed-agent
// baseline and before the reset of its first scenario. The install answers null, and the scenario runs.
#[test]
fn a_suite_installs_its_managed_agents_once_after_the_baseline() {
    let install = json!({"type": INSTALL, "agents": [{"displayName": "Codex", "fullAgentId": "acp.registry.codex-acp"}],
        "timeoutMs": 300_000});
    let fake = Fake::new(Script {
        answers: BTreeMap::from([(INSTALL.to_owned(), Value::Null)]),
        ..Script::default()
    });
    let ran = run(&fake, &[installing_suite(Some(&install))], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    let order: Vec<String> = fake.requests().into_iter().map(|(_, kind)| kind).collect();
    assert_eq!(
        order,
        [
            "ResetManagedAgents",
            "InstallManagedAgents",
            "Reset",
            "StepSequence",
            "action/rename-session-select",
            "Reset"
        ],
        "a green scenario sends no baseline in its reset after"
    );
    assert_eq!(fake.bodies(INSTALL), [install]);
}

/// The install request of Codex, as the generator writes it.
fn codex_install() -> Value {
    json!({"type": INSTALL, "agents": [{"displayName": "Codex", "fullAgentId": "acp.registry.codex-acp"}], "timeoutMs": 300_000})
}

/// The failure of the one action of [`installing_suite`].
/// A script whose install of the registry agent passes and whose action `rename-session-select` fails.
fn failed_install_script() -> Script {
    Script {
        answers: BTreeMap::from([(INSTALL.to_owned(), Value::Null)]),
        failures: BTreeMap::from([failed_select()]),
        ..Script::default()
    }
}

fn failed_select() -> (String, Value) {
    (
        "action/rename-session-select".to_owned(),
        json!({"type": "java.lang.AssertionError", "message": "the registry agent is not launchable", "stack": []}),
    )
}

// A failed scenario can stop before it puts back an agent that it removed. So its reset after sends the managed-agent
// baseline again, after the reset in the IDE: the reset of the managed agents, then the install of the suite.
#[test]
fn a_failed_scenario_restores_the_managed_agent_baseline_in_its_reset_after() {
    let install = codex_install();
    let fake = Fake::new(failed_install_script());
    let ran = run(&fake, &[installing_suite(Some(&install))], None);
    let finished = ran.events("testFinished");
    assert_eq!(
        (finished[0]["status"].as_str(), finished[0]["message"].as_str()),
        (Some("FAILED"), Some("the registry agent is not launchable")),
        "a baseline that holds adds nothing to the failure of the scenario"
    );
    let order: Vec<String> = fake
        .requests()
        .into_iter()
        .map(|(_, kind)| kind)
        .filter(|kind| !kind.starts_with("GET "))
        .collect();
    assert_eq!(
        order,
        [
            "ResetManagedAgents",
            "InstallManagedAgents",
            "Reset",
            "StepSequence",
            "action/rename-session-select",
            "Reset",
            "ResetManagedAgents",
            "InstallManagedAgents"
        ]
    );
    assert_eq!(fake.bodies(INSTALL), [install.clone(), install]);
    let baselines = fake.bodies(&format!("{BRIDGE}ResetManagedAgents"));
    assert_eq!(baselines[0], baselines[1], "both baselines reset the same registry agents");
    assert!(fake.relaunches.lock().unwrap().is_empty());
}

// A baseline that does not hold after a failed scenario is a reset that does not hold. The isolation recycles the IDE,
// and the reset and the baseline run again on the new pid. The recycle names the scenario that broke the state.
#[test]
fn a_failed_baseline_after_a_failed_scenario_recycles_the_ide_through_the_isolation() {
    let install = codex_install();
    let fake = Fake::new(Script {
        managed_agent_resets: VecDeque::from([Value::Null, json!("a pending install did not stop")]),
        ..failed_install_script()
    });
    let ran = run(&fake, &[installing_suite(Some(&install))], None);
    assert_eq!(*fake.relaunches.lock().unwrap(), [Relaunch::Recycle]);
    assert_eq!(ran.events("testFinished")[0]["status"], "FAILED");
    let after: Vec<(u32, String)> = fake
        .requests()
        .into_iter()
        .skip_while(|(_, kind)| kind != "action/rename-session-select")
        .skip(1)
        .filter(|(_, kind)| !kind.starts_with("GET "))
        .collect();
    let on = |pid: u32, kind: &str| (pid, kind.to_owned());
    assert_eq!(
        after,
        [
            on(1, "Reset"),
            on(1, "ResetManagedAgents"),
            on(2, "Reset"),
            on(2, "ResetManagedAgents"),
            on(2, "InstallManagedAgents")
        ]
    );
    assert!(
        ran.events("output")
            .iter()
            .any(|record| record["text"].as_str().unwrap().starts_with(
                "The IDE was recycled 1 time(s): after 'renames': restoring the managed-agent baseline failed: Air \
             managed-agent baseline failed: a pending install did not stop"
            )),
        "{:#?}",
        ran.events("output")
    );
}

// An install that answers a text fails the suite as one reset failure that names the text. A fixture that starts with
// installed agents, in a document without an install request, fails the suite as a document failure.
#[test]
fn a_failed_install_and_a_missing_install_request_fail_the_suite() {
    let install = json!({"type": INSTALL, "agents": [], "timeoutMs": 300_000});
    let fake = Fake::new(Script {
        answers: BTreeMap::from([(INSTALL.to_owned(), json!("Codex: the IDE served no package"))]),
        ..Script::default()
    });
    let ran = run(&fake, &[installing_suite(Some(&install))], None);
    let failed = ran.events("containerFailed");
    assert_eq!(
        (failed[0]["type"].as_str(), failed[0]["message"].as_str()),
        (
            Some("lane_reset"),
            Some("Air managed-agent install failed: Codex: the IDE served no package")
        )
    );
    assert_eq!(ran.events("testStarted"), Vec::<&serde_json::Value>::new());
    let fake = Fake::new(Script::default());
    let ran = run(&fake, &[installing_suite(None)], None);
    let failed = ran.events("containerFailed");
    assert_eq!(failed[0]["type"], "lane_document");
    assert!(
        failed[0]["message"].as_str().unwrap().contains("states no managedAgentInstall"),
        "{}",
        failed[0]["message"]
    );
    assert!(fake.requests().is_empty(), "{:?}", fake.requests());
}

/// The authored suite `plugin-reload`: a part, the reload of Air, and a part that counts after it.
fn plugin_reload() -> Suite {
    let part = |name: &str| {
        json!({"action": "authored-step", "args": {"part": name, "scenario": "reload", "suite": "plugin-reload",
            "timeoutMs": 180_000}, "uses": {"takes": [], "answers": []}})
    };
    let actions = json!({"authored-step": authored_step("plugin-reload", "reload", &["open-session-and-count", "count-after-reload"]),
        "reload-air": {"side": "host", "timeoutMs": 420_000, "restarts": 0, "answers": [], "mayAnswer": [],
            "takes": [], "mayTake": [], "params": {}}});
    let profiles = json!([{"name": "Air reloads and leaves no class loader", "key": "reload", "fixture": "authored",
        "flags": {}, "flagKeys": [], "requiredExecutables": [], "setups": [], "steps": [],
        "program": [part("open-session-and-count"), {"action": "reload-air"}, part("count-after-reload")]}]);
    let mut document = authored_document("plugin-reload", "acp", "AirPluginUnloadLeakTest", &actions, &profiles);
    document["lane"] = json!("GUI_CHAT");
    suite_of(&document)
}

// The reload of Air runs between two parts on the same IDE, within the deadline of its vocabulary entry. The runner
// reads the attachments of the part before it first, and the trace shows the reload as a restart span. A reload that
// fails fails the scenario as a host id.
#[test]
fn a_reload_of_air_runs_between_two_parts_on_the_same_ide() {
    let fake = Fake::new(Script::default());
    let ran = run(&fake, &[plugin_reload()], None);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    assert_eq!(*fake.air_reloads.lock().unwrap(), [Duration::from_secs(420)]);
    assert!(fake.relaunches.lock().unwrap().is_empty());
    let kinds: Vec<(u32, String)> = fake
        .requests()
        .into_iter()
        .filter(|(_, kind)| kind == "action/authored-step" || kind == "GET /api/air/ui-test-trace/attachments")
        .collect();
    assert_eq!(
        kinds.iter().map(|(_, kind)| kind.as_str()).collect::<Vec<_>>(),
        [
            "action/authored-step",
            "GET /api/air/ui-test-trace/attachments",
            "GET /api/air/ui-test-trace/attachments",
            "action/authored-step",
            "GET /api/air/ui-test-trace/attachments",
        ],
        "a list after the first part, a list before the reload, and a list after the last part"
    );
    assert!(
        kinds.iter().all(|(pid, _)| *pid == 1),
        "the reload keeps the IDE process: {kinds:?}"
    );
    assert!(
        ran.trace.iter().any(|line| line.contains(r#""key":"restart:reload""#)),
        "{:?}",
        ran.trace
    );

    let failing = Fake::new(Script::default());
    failing.air_reload_fails.store(true, Ordering::SeqCst);
    let ran = run(&failing, &[plugin_reload()], None);
    let finished = ran.events("testFinished");
    assert_eq!(
        (
            finished[0]["status"].as_str(),
            finished[0]["type"].as_str(),
            finished[0]["message"].as_str()
        ),
        (
            Some("FAILED"),
            Some("lane_host_id"),
            Some("the resident plugin refused the reload of Air: a reload runs")
        )
    );
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
    let ran = run_with(
        &fake,
        &[select_story(&["hangs", "never"])],
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
    ran.assert_done_aborted();
}

/// Writes the Codex daemon directory of the IDE of `fake`: the ledger, two daemon records, a record that is no JSON
/// object, and the log of one daemon. The record `older` changed an hour before the record `cd555461`.
fn write_codex_daemons(fake: &Fake) {
    let directory = fake.context.path().join(SYSTEM_DIR).join(CODEX_DAEMONS_DIR);
    fs::create_dir_all(&directory).unwrap();
    fs::write(directory.join(CODEX_LEDGER_FILE), r#"{"version":3,"generations":[]}"#).unwrap();
    for id in ["older", "cd555461"] {
        let record = format!(
            r#"{{"launchId":"{id}","processIds":[],"startupConfigArgs":["-c","mcp_servers.idea.url=x"],"tcpUrl":"ws://127.0.0.1:1"}}"#
        );
        fs::write(directory.join(format!("{id}.json")), record).unwrap();
    }
    fs::write(directory.join("broken.json"), "not a record").unwrap();
    fs::write(directory.join("cd555461.log"), "the log of a daemon").unwrap();
    for (id, ago) in [("older", Duration::from_hours(1)), ("broken", Duration::from_hours(2))] {
        fs::File::open(directory.join(format!("{id}.json")))
            .unwrap()
            .set_modified(SystemTime::now() - ago)
            .unwrap();
    }
}

/// The Codex files that the trace of `ran` attached, as the title and the text of each.
fn codex_attachments(ran: &Ran) -> Vec<(String, String)> {
    ran.trace
        .iter()
        .map(|line| serde_json::from_str::<Value>(line).unwrap())
        .filter(|command| command["op"] == "attach" && command["name"].as_str().is_some_and(|name| name.starts_with("Codex daemon")))
        .map(|command| {
            let text = fs::read_to_string(command["file"].as_str().unwrap()).unwrap();
            (command["name"].as_str().unwrap().to_owned(), text)
        })
        .collect()
}

/// Runs one scenario of one assertion against `fake`.
fn run_one_assertion(fake: &Fake) -> Ran {
    let vocabulary = json!({"runnerAnswers": [], "actions": {}, "assertions": {"terminal-panel-mounts": ide(&[], &[], &[])}});
    let instructions = json!([{"assertion": "terminal-panel-mounts", "expectation": "The terminal panel mounts."}]);
    run(fake, &[story(&vocabulary, &json!([scenario("reopens", &instructions)]))], None)
}

/// A lane whose assertion `terminal-panel-mounts` fails.
fn failing_assertion() -> Fake {
    let failure = json!({"type": "java.lang.AssertionError", "message": "no terminal panel", "stack": ["Air.check(Air.kt:7)"]});
    Fake::new(Script {
        failures: BTreeMap::from([("assertion/terminal-panel-mounts".to_owned(), failure)]),
        ..Script::default()
    })
}

// A failed scenario attaches the Codex daemon ledger of the IDE as it is, and then each daemon record, the newest first,
// without its startup arguments and its URL. A record that is no JSON object is a note, and a daemon log stays out. A
// passed scenario attaches none, and a failed scenario of an IDE with no Codex daemon directory attaches none.
#[test]
fn a_failed_scenario_attaches_the_codex_daemon_ledger_and_records_of_the_ide() {
    let failing = failing_assertion();
    write_codex_daemons(&failing);
    let ran = run_one_assertion(&failing);
    assert_eq!(ran.events("testFinished")[0]["status"], "FAILED");
    let record = |id: &str| format!("{{\n  \"launchId\": \"{id}\",\n  \"processIds\": []\n}}");
    assert_eq!(
        codex_attachments(&ran),
        [
            (
                "Codex daemon ledger.json".to_owned(),
                r#"{"version":3,"generations":[]}"#.to_owned()
            ),
            ("Codex daemon cd555461.json".to_owned(), record("cd555461")),
            ("Codex daemon older.json".to_owned(), record("older")),
        ]
    );
    assert!(
        ran.events("output").iter().any(|record| record["text"]
            .as_str()
            .unwrap()
            .ends_with("broken.json: it is no JSON object, so its private fields cannot be dropped")),
        "{:?}",
        ran.events("output")
    );

    let passing = Fake::new(Script::default());
    write_codex_daemons(&passing);
    let ran = run_one_assertion(&passing);
    assert_eq!(ran.events("testFinished")[0]["status"], "SUCCESSFUL");
    assert_eq!(codex_attachments(&ran), []);

    let ran = run_one_assertion(&failing_assertion());
    assert_eq!(ran.events("testFinished")[0]["status"], "FAILED");
    assert_eq!(codex_attachments(&ran), []);
}

/// A rerun of `lane-run` with the injections of `value`, as `AIR_LANE_INJECT_FAILURE` spells them.
fn rerun_injecting(value: &str) -> Rerun {
    let value = value.to_owned();
    rerun::from_environment(&move |_| Some(value.clone())).unwrap()
}

/// Runs `suites` against `fake` with `rerun`, and the run budget `run_budget`.
fn run_rerun(fake: &Fake, suites: &[Suite], rerun: Option<Rerun>, run_budget: Option<Duration>) -> Ran {
    run_set(
        fake,
        suites,
        &Setup {
            run_budget,
            rerun,
            ..Setup::default()
        },
    )
}

impl Ran {
    /// The text of each `output` record that starts with `lane-run`.
    fn rerun_notes(&self) -> Vec<String> {
        self.events("output")
            .iter()
            .filter_map(|record| record["text"].as_str())
            .filter(|text| text.starts_with("lane-run"))
            .map(str::to_owned)
            .collect()
    }
}

fn finished(name: &str, status: &str) -> (String, String) {
    (name.to_owned(), status.to_owned())
}

// The rerun of lane-run runs a failed scenario once more after the last suite of the shared IDE. The IDE is recycled
// first, so the rerun runs on a new IDE process, after the prologue of its suite and its own reset. The pass of the
// rerun takes the failure out of the counts, and the stream keeps both attempts.
#[test]
fn a_failed_scenario_runs_once_more_on_a_recycled_ide_and_its_pass_clears_the_failure() {
    let fake = Fake::new(Script::default());
    let ran = run_rerun(
        &fake,
        &[select_story(&["first", "second"])],
        Some(rerun_injecting("first:first")),
        Some(Duration::from_mins(30)),
    );
    assert_eq!(
        ran.finished_statuses(),
        [
            finished("first", "FAILED"),
            finished("second", "SUCCESSFUL"),
            finished("first", "SUCCESSFUL")
        ]
    );
    let failed = ran.events("testFinished")[0];
    assert_eq!(failed["type"], kind::INJECTED);
    assert_eq!(
        failed["message"],
        "$AIR_LANE_INJECT_FAILURE fails the first attempt of the scenario 'first'"
    );
    assert_eq!(ran.counts.failed, 0);
    assert_eq!(ran.counts.started, 3);
    assert_eq!(*fake.relaunches.lock().unwrap(), [Relaunch::Recycle]);
    let selects: Vec<u32> = fake
        .requests()
        .into_iter()
        .filter(|(_, kind)| kind == "action/rename-session-select")
        .map(|(pid, _)| pid)
        .collect();
    assert_eq!(selects, [1, 1, 2], "the rerun runs on the recycled IDE");
    assert_eq!(
        fake.requests().iter().filter(|(_, kind)| kind == "ResetManagedAgents").count(),
        2,
        "the prologue runs again before the rerun"
    );
    assert_eq!(
        ran.rerun_notes(),
        [
            "lane-run reruns 1 failed scenario(s) once on a recycled IDE: 'first'",
            "lane-run rerun: 1 of 1 failed scenario(s) passed",
        ]
    );
}

/// A fake whose assertion `rename-session-title` fails each time.
fn title_never_shows() -> Fake {
    let failure = json!({"type": "java.lang.IllegalStateException", "message": "the title does not show"});
    Fake::new(Script {
        failures: BTreeMap::from([("assertion/rename-session-title".to_owned(), failure)]),
        ..Script::default()
    })
}

// A scenario that fails its rerun too stays failed, and it counts as one failed scenario.
#[test]
fn a_scenario_that_fails_twice_stays_failed_after_one_rerun() {
    let fake = title_never_shows();
    let ran = run_rerun(&fake, &[title_story()], Some(Rerun::default()), None);
    assert_eq!(
        ran.finished_statuses(),
        [finished("renames", "FAILED"), finished("renames", "FAILED")]
    );
    assert_eq!(ran.counts.failed, 1);
    assert_eq!(
        ran.rerun_notes(),
        [
            "lane-run reruns 1 failed scenario(s) once on a recycled IDE: 'renames'",
            "lane-run rerun: 0 of 1 failed scenario(s) passed",
        ]
    );
}

// The lane daemon of a worker has no rerun: a failed scenario runs once, and no failure is injected.
#[test]
fn a_runner_without_a_rerun_reruns_nothing_and_injects_nothing() {
    let fake = title_never_shows();
    let ran = run(&fake, &[title_story(), select_story(&["first"])], None);
    assert_eq!(
        ran.finished_statuses(),
        [finished("renames", "FAILED"), finished("first", "SUCCESSFUL")]
    );
    assert_eq!(ran.rerun_notes(), Vec::<String>::new());
    assert_eq!(*fake.relaunches.lock().unwrap(), []);
}

// More failed scenarios than the limit are a break: no scenario reruns, and a note says why. A run whose budget left
// cannot hold the recycle reruns nothing either.
#[test]
fn more_failed_scenarios_than_the_limit_or_too_little_budget_rerun_none() {
    let fake = Fake::new(Script::default());
    let names = ["a", "b", "c", "d"];
    let every = names.map(|name| format!("every:{name}")).join(";");
    let ran = run_rerun(&fake, &[select_story(&names)], Some(rerun_injecting(&every)), None);
    assert_eq!(ran.finished_statuses().len(), 4);
    assert_eq!(ran.counts.failed, 4);
    assert_eq!(
        ran.rerun_notes(),
        ["lane-run reruns no failed scenario: 4 scenarios and suite prologues failed, more than 3, which is a break and not a flake"]
    );
    assert_eq!(*fake.relaunches.lock().unwrap(), []);

    let fake = Fake::new(Script::default());
    let ran = run_rerun(
        &fake,
        &[select_story(&["a"])],
        Some(rerun_injecting("first:a")),
        Some(Duration::from_mins(10)),
    );
    assert_eq!(ran.finished_statuses(), [finished("a", "FAILED")]);
    let notes = ran.rerun_notes();
    assert_eq!(notes.len(), 1, "{notes:?}");
    assert!(
        notes[0].starts_with("lane-run reruns no failed scenario: the lane budget left "),
        "{notes:?}"
    );
    assert_eq!(*fake.relaunches.lock().unwrap(), []);
}

// A recycle that fails before the rerun leaves the failures as they are.
#[test]
fn a_recycle_that_fails_before_the_rerun_leaves_the_failure() {
    let fake = Fake::new(Script::default());
    *fake.refused_part.lock().unwrap() = Some(RelaunchPart::Ready);
    let ran = run_rerun(&fake, &[select_story(&["a"])], Some(rerun_injecting("first:a")), None);
    assert_eq!(ran.finished_statuses(), [finished("a", "FAILED")]);
    assert_eq!(ran.counts.failed, 1);
    let notes = ran.rerun_notes();
    assert_eq!(notes.len(), 2, "{notes:?}");
    assert!(
        notes[1].starts_with("lane-run reruns no failed scenario: the recycle before the rerun failed: lane_ide_not_ready"),
        "{notes:?}"
    );
}
