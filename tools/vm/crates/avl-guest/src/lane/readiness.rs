//! The readiness of the lane IDE, and the requests of the bridge that the lane daemon sends for it.
//!
//! The gate, in order: the lifecycle long poll waits for `READY` with no quiet time, because the IDE decides readiness
//! after the first scan of its project. A state that names a failed bind of the control server ends the wait at once,
//! because such an IDE never gets ready. The vocabulary digest of the bridge must be the digest of the vocabulary file
//! of the run. The chain of `InputRobot` and `Ping` proves that gestures go through the input-events robot and that the
//! typed bridge answers. The Junie offer of the IDE start gets `Skip`, unless the caller keeps it open.
//!
//! A bare IDE passes the first two steps only, see [`await_lifecycle_ready`]: it measures its own start-up, so it gets
//! no request of the bridge before its first instruction.
//!
//! A request is the JSON of one `AirUiTestRequest`: `{"type": "<class name>", ...fields}`, posted to the request
//! endpoint. Its transport deadline is its `timeoutMs` and a grace, as the Kotlin client derives it.

use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use avl_bridge::lifecycle::{Reader, WaitError};
use avl_bridge::{Client, url};
use serde_json::{Value, json};

use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The refusal code of an IDE that did not get ready.
pub(crate) const NOT_READY_CODE: &str = "lane_ide_not_ready";

/// The refusal code of an IDE whose control server of the controlled agents did not bind.
pub(crate) const CONTROL_BIND_CODE: &str = "lane_control_bind_failed";

/// The refusal code of a bridge that serves another flow vocabulary than the documents of the run.
pub(crate) const VOCABULARY_SKEW_CODE: &str = "lane_vocabulary_skew";

/// How long the IDE may take to open its project and become quiet.
pub(crate) const READY_TIMEOUT: Duration = Duration::from_mins(10);

/// The robot that the input-events contract selects.
pub(crate) const INPUT_EVENTS_ROBOT: &str = "com.jetbrains.performancePlugin.remotedriver.robot.InputEventsRobot";

/// The request endpoint of the bridge, `AIR_UI_TEST_HTTP_PREFIX` with its leading slash.
pub(crate) const REQUEST_ROUTE: &str = "/api/air/ui-test";

/// The package of every request class of the bridge, the prefix of its serial name.
const REQUEST_PACKAGE: &str = "com.intellij.air.integration.bridge.";

/// The grace of a transport deadline past the deadline of the request in the IDE.
const TRANSPORT_GRACE: Duration = Duration::from_secs(10);

/// The default deadline of a bridge operation, `AIR_UI_TEST_DEFAULT_OPERATION_TIMEOUT_MS`.
const DEFAULT_OPERATION: Duration = Duration::from_secs(10);

/// The control of the Junie offer that stops it, as `AgentThreadsBundle` states it.
const JUNIE_SKIP: &str = "Skip";

/// How long the tooltip of the Junie offer may take to close after the `Skip`.
const JUNIE_TOOLTIP_CLOSE: Duration = Duration::from_secs(30);

/// The JSON of the bridge request `name` with `fields`, an object.
pub(crate) fn request(name: &str, fields: Value) -> Value {
    let mut request = match fields {
        Value::Object(fields) => fields,
        _ => serde_json::Map::new(),
    };
    request.insert("type".to_owned(), Value::String(format!("{REQUEST_PACKAGE}{name}")));
    Value::Object(request)
}

/// The bridge of one lane IDE, on its fixed port with the token of its launch.
pub(crate) struct Bridge {
    client: Client,
}

impl Bridge {
    /// The bridge on the loopback `port`.
    pub(crate) fn new(port: u16, token: &str) -> Result<Self, AgentRefusal> {
        Self::at(&url(port), token)
    }

    /// The bridge at `base`, `http://<ip>:<port>`.
    pub(crate) fn at(base: &str, token: &str) -> Result<Self, AgentRefusal> {
        let client = Client::new(base, token).map_err(|error| AgentRefusal::internal(error.to_string()))?;
        Ok(Self { client })
    }

    /// Sends `request`, whose deadline in the IDE is `within`, and answers its value.
    pub(crate) fn call(&self, request: &Value, within: Duration) -> Result<Value, AgentRefusal> {
        let name = request.get("type").and_then(Value::as_str).unwrap_or_default().to_owned();
        self.post(request, within)
            .map_err(|error| AgentRefusal::refused(NOT_READY_CODE, format!("the bridge request {name} failed: {error}")))
    }

    /// Sends `request`, whose deadline in the IDE is `within`, and answers its value or the error of the client. The
    /// transport waits `within` and the grace of the bridge.
    pub(crate) fn post(&self, request: &Value, within: Duration) -> Result<Value, avl_bridge::Error> {
        self.client
            .post_json(REQUEST_ROUTE, &request.to_string(), Instant::now() + within + TRANSPORT_GRACE)
    }

    /// GETs the route `route`, whose answer has the media type `accept`, within `within`.
    pub(crate) fn get(&self, route: &str, accept: &str, within: Duration) -> Result<Vec<u8>, avl_bridge::Error> {
        self.client.get(route, accept, Instant::now() + within)
    }

    /// The lifecycle reader of the IDE of `launch`.
    fn reader(&self, launch: &str) -> Reader {
        Reader::new(self.client.clone(), Some(launch))
    }
}

/// Waits until the IDE of `launch` is ready, and drives it to the state a scenario expects. `vocabulary_digest` is the
/// digest of the vocabulary file of the run. With `keep_junie_offer`, the Junie offer of the IDE start stays open. `note` gets each
/// fact a person reads in the log.
pub(crate) fn await_ready(
    bridge: &Bridge,
    launch: &str,
    vocabulary_digest: &str,
    keep_junie_offer: bool,
    note: &mut dyn FnMut(String),
) -> Result<(), AgentRefusal> {
    await_lifecycle_ready(bridge, launch, vocabulary_digest, note)?;
    ping(bridge)?;
    if !keep_junie_offer {
        skip_junie_offer(bridge, note)?;
    }
    Ok(())
}

/// Waits until the lifecycle of the IDE of `launch` reports `READY`, and holds the bridge to `vocabulary_digest`, the
/// digest of the vocabulary file of the run. It sends no request of the bridge, because the resident plugin serves the lifecycle
/// route: the readiness of a bare IDE ends here.
pub(crate) fn await_lifecycle_ready(
    bridge: &Bridge,
    launch: &str,
    vocabulary_digest: &str,
    note: &mut dyn FnMut(String),
) -> Result<(), AgentRefusal> {
    let started = Instant::now();
    let state = bridge
        .reader(launch)
        .await_ready(Duration::ZERO, READY_TIMEOUT)
        .map_err(|error| not_ready(launch, &error))?;
    note(format!(
        "the IDE of '{launch}' is ready as pid {} after {} ms",
        state.pid,
        started.elapsed().as_millis()
    ));
    check_vocabulary(
        launch,
        state.vocabulary_digest.as_deref(),
        vocabulary_digest,
        &MISSING_DIGEST_NOTED,
        note,
    )
}

/// Proves that the bridge answers and drives gestures through the input-events robot: one chain of `InputRobot` and a
/// `Ping` of a fresh nonce.
pub(crate) fn ping(bridge: &Bridge) -> Result<(), AgentRefusal> {
    let nonce = format!("air-ui-test-bridge-{}", nanos());
    check_robot_and_ping(
        &bridge.call(&robot_and_ping(&nonce), 2 * (DEFAULT_OPERATION + TRANSPORT_GRACE))?,
        &nonce,
    )
}

/// The refusal of a wait for readiness that `error` ended. A failed bind of the control server names its own text.
fn not_ready(launch: &str, error: &WaitError) -> AgentRefusal {
    if let WaitError::ControlBindFailed(state) = error {
        let failure = state.control_bind_failure.as_deref().unwrap_or_default();
        return AgentRefusal::refused(
            CONTROL_BIND_CODE,
            format!(
                "the IDE of '{launch}' cannot get ready, because its control server of the controlled agents did not \
                 bind: {failure}; idea.log of the IDE names the holder of the port"
            ),
        );
    }
    AgentRefusal::refused(NOT_READY_CODE, format!("the IDE of '{launch}' did not get ready: {error}"))
}

/// Whether the daemon noted a bridge without a vocabulary digest already: it notes that once per daemon.
static MISSING_DIGEST_NOTED: AtomicBool = AtomicBool::new(false);

/// Refuses a bridge whose vocabulary is not the vocabulary of the run. A bridge that names no vocabulary
/// passes, and `note` gets one line about it, the first time that `noted` sees one.
pub(crate) fn check_vocabulary(
    launch: &str,
    served: Option<&str>,
    expected: &str,
    noted: &AtomicBool,
    note: &mut dyn FnMut(String),
) -> Result<(), AgentRefusal> {
    match served {
        Some(served) if served != expected => Err(AgentRefusal::refused(
            VOCABULARY_SKEW_CODE,
            format!(
                "the bridge of '{launch}' serves the flow vocabulary {served}, and the documents of the run name {expected}; \
                 the bridge plugin and the documents jar come from two builds, so build them together"
            ),
        )),
        Some(_) => Ok(()),
        None => {
            if !noted.swap(true, Ordering::SeqCst) {
                note(format!(
                    "the bridge of '{launch}' names no vocabulary digest, so readiness cannot hold it to {expected}; the \
                     daemon says this once"
                ));
            }
            Ok(())
        }
    }
}

/// The chain `[InputRobot, Ping(nonce)]`, one request.
pub(crate) fn robot_and_ping(nonce: &str) -> Value {
    request(
        "StepSequence",
        json!({ "steps": [request("InputRobot", json!({})), request("Ping", json!({ "nonce": nonce }))] }),
    )
}

/// Refuses the answer of [`robot_and_ping`] unless the robot is the input-events robot and the ping echoes `nonce`.
pub(crate) fn check_robot_and_ping(answer: &Value, nonce: &str) -> Result<(), AgentRefusal> {
    let not_ready = |message: String| AgentRefusal::refused(NOT_READY_CODE, message);
    let steps = answer
        .as_array()
        .filter(|steps| steps.len() == 2)
        .ok_or_else(|| not_ready(format!("the robot and ping chain answered {answer}, not two step outcomes")))?;
    for (index, step) in steps.iter().enumerate() {
        if let Some(failure) = step.get("failure").filter(|failure| !failure.is_null()) {
            let shown = avl_bridge::BridgeFailure::decode(failure).map_or_else(|_| failure.to_string(), |failure| failure.to_string());
            return Err(not_ready(format!("step {} of the robot and ping chain failed: {shown}", index + 1)));
        }
    }
    let robot = steps[0].get("value").and_then(Value::as_str).unwrap_or_default();
    if robot != INPUT_EVENTS_ROBOT {
        return Err(not_ready(format!(
            "the lane drives gestures with {INPUT_EVENTS_ROBOT}, and the IDE drives them with '{robot}'; the IDE must \
             launch with driver.robot.use.input.events=true"
        )));
    }
    let echoed = steps[1].get("value").and_then(Value::as_str).unwrap_or_default();
    if echoed != nonce {
        return Err(not_ready(format!(
            "the bridge answered the ping with '{echoed}', expected '{nonce}'"
        )));
    }
    Ok(())
}

/// Answers `Skip` on the Junie offer of the IDE start. An offer that did not decide within its budget is cancelled by
/// the IDE, and no tooltip opens later.
fn skip_junie_offer(bridge: &Bridge, note: &mut dyn FnMut(String)) -> Result<(), AgentRefusal> {
    let offer = bridge.call(&request("AwaitJunieStartupOffer", json!({})), Duration::from_secs(15))?;
    if offer.get("ended").and_then(Value::as_bool) != Some(true) {
        note("the Junie offer of the IDE start did not decide within its budget; the IDE cancelled it".to_owned());
    }
    if offer.get("tooltip").is_none_or(Value::is_null) {
        return Ok(());
    }
    let target = bridge.call(
        &request("JuniePromoTooltipControlTarget", json!({ "text": JUNIE_SKIP })),
        DEFAULT_OPERATION,
    )?;
    let clicked = bridge.call(
        &request("PerformTargetInput", json!({ "target": target, "input": { "type": "click" } })),
        DEFAULT_OPERATION,
    )?;
    if clicked != Value::Bool(true) {
        return Err(AgentRefusal::refused(
            NOT_READY_CODE,
            format!("the click on {JUNIE_SKIP} of the Junie offer was not performed: {clicked}"),
        ));
    }
    let closing = request(
        "AwaitJuniePromoTooltip",
        json!({ "showing": false, "timeoutMs": JUNIE_TOOLTIP_CLOSE.as_millis() }),
    );
    bridge.call(&closing, JUNIE_TOOLTIP_CLOSE)?;
    note("the Junie offer of the IDE start got Skip".to_owned());
    Ok(())
}

/// The pid that the bridge of the IDE of `launch` answers with now: one lifecycle poll that does not wait.
pub(crate) fn bridge_pid(bridge: &Bridge, launch: &str) -> Result<i64, String> {
    bridge
        .reader(launch)
        .poll(None, Duration::ZERO)
        .map(|snapshot| snapshot.state.pid)
        .map_err(|error| format!("the bridge of '{launch}' did not answer a lifecycle poll: {error}"))
}

/// Asks the IDE to quit the way a user does, so the project workspace is flushed. The IDE answers when the exit is
/// scheduled.
pub(crate) fn quit(bridge: &Bridge) -> Result<(), AgentRefusal> {
    bridge.call(&request("Quit", json!({})), DEFAULT_OPERATION).map(|_| ())
}

fn nanos() -> u128 {
    SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |elapsed| elapsed.as_nanos())
}
