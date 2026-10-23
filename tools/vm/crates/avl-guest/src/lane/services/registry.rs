//! The fake ACP registry and package server of a lane: `/registry.json`, `/packages/acp-scenario-agent-<platform>.zip`,
//! one armed distribution fault, the hold of an answer, and the log of the answered requests.
//!
//! The registry publishes only the agents that the lane names. A request is logged when it is answered, so a held
//! request is not in the log until its release.

use std::sync::{Condvar, Mutex, MutexGuard};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use super::control;
use super::http::{Request, Response};
use super::{Handler, Service};
use crate::lane::package::{self, PLATFORMS, Packages};
use crate::reply::AgentRefusal;

#[cfg(test)]
mod tests;

/// The name of the service in its control route and its threads.
pub(crate) const NAME: &str = "registry";

/// The environment variable that carries [package::PACKAGE_TOKEN] to the agent.
pub(crate) const PACKAGE_TOKEN_ENV: &str = "ACP_FAKE_AGENT_PACKAGE_TOKEN";

/// The environment variable that tells the agent how it was started.
pub(crate) const STARTUP_TOKEN_ENV: &str = "ACP_FAKE_AGENT_STARTUP_TOKEN";

const INVALID_ARCHIVE: &[u8] = b"not-a-zip-archive";
const MALFORMED_REGISTRY: &str = "{this is not valid registry json";
const BAD_CHECKSUM: &str = "0000000000000000000000000000000000000000000000000000000000000000";
const PACKAGE_PREFIX: &str = "/packages/acp-scenario-agent-";

const DEFAULT_REGISTRY_STATUS: u16 = 503;
const DEFAULT_PACKAGE_STATUS: u16 = 404;
const DEFAULT_HOLD_MS: u64 = 30_000;
/// Bounds the hold that a control request can arm.
const MAX_HOLD_MS: u64 = 600_000;

/// One distribution fault. At most one is armed at a time.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub(crate) enum Fault {
    None,
    /// `registry.json` answers the registry status with an empty body.
    RegistryHttpError,
    /// A package answers the package status with an empty body.
    PackageHttpError,
    /// `registry.json` states a wrong checksum for every archive.
    ChecksumMismatch,
    /// A package answers bytes that are no zip, and `registry.json` states their checksum.
    InvalidArchive,
    /// A package announces its whole length and sends only the first half.
    TruncatedResponse,
    /// Every answer, the index included, waits for the release or the end of the hold.
    SlowResponse,
    /// Only a package answer waits. The index answers at once.
    SlowPackageResponse,
    /// `registry.json` answers text that is no JSON.
    MalformedRegistry,
}

impl Fault {
    const ALL: [Self; 9] = [
        Self::None,
        Self::RegistryHttpError,
        Self::PackageHttpError,
        Self::ChecksumMismatch,
        Self::InvalidArchive,
        Self::TruncatedResponse,
        Self::SlowResponse,
        Self::SlowPackageResponse,
        Self::MalformedRegistry,
    ];

    /// The id of the fault, as the control route spells it.
    pub(crate) const fn id(self) -> &'static str {
        match self {
            Self::None => "none",
            Self::RegistryHttpError => "registry-http-error",
            Self::PackageHttpError => "package-http-error",
            Self::ChecksumMismatch => "checksum-mismatch",
            Self::InvalidArchive => "invalid-archive",
            Self::TruncatedResponse => "truncated-response",
            Self::SlowResponse => "slow-response",
            Self::SlowPackageResponse => "slow-package-response",
            Self::MalformedRegistry => "malformed-registry",
        }
    }

    fn of_id(id: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|fault| fault.id() == id)
    }

    const fn takes_status(self) -> bool {
        matches!(self, Self::RegistryHttpError | Self::PackageHttpError)
    }

    const fn holds(self) -> bool {
        matches!(self, Self::SlowResponse | Self::SlowPackageResponse)
    }
}

/// One agent that the registry publishes: its registry id, its display name, and the environment that its manifest adds
/// to the two package variables.
#[derive(Clone, Debug)]
pub(crate) struct RegistryAgent {
    pub registry_id: String,
    pub display_name: String,
    pub environment: Vec<(String, String)>,
}

/// One answered request of the IDE, as the control route lists it.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Event {
    pub sequence: usize,
    pub method: String,
    pub path: String,
    pub received_at_ms: u64,
    pub status: u16,
    pub response_bytes: usize,
    pub truncated: bool,
    pub fault: Fault,
}

/// The armed fault and its options, as the control route states it.
#[derive(Clone, Copy, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct Armed {
    fault: Fault,
    registry_status: u16,
    package_status: u16,
    hold_ms: u64,
}

impl Armed {
    const CLEAR: Self = Self {
        fault: Fault::None,
        registry_status: DEFAULT_REGISTRY_STATUS,
        package_status: DEFAULT_PACKAGE_STATUS,
        hold_ms: DEFAULT_HOLD_MS,
    };
}

struct State {
    armed: Armed,
    /// The generation of the armed hold. A held answer waits while the generation it saw is still armed.
    hold: Option<u64>,
    generation: u64,
    events: Vec<Event>,
    stopped: bool,
}

/// The handler of the registry service.
pub(crate) struct Registry {
    base_url: String,
    token: String,
    packages: Packages,
    agents: Vec<RegistryAgent>,
    state: Mutex<State>,
    released: Condvar,
}

/// Starts the registry on `127.0.0.1:<port>` with the archives of `packages` and the agents `agents`.
pub(crate) fn start(port: u16, token: &str, packages: Packages, agents: Vec<RegistryAgent>) -> Result<Service<Registry>, AgentRefusal> {
    Service::start(NAME, port, |address| Registry {
        base_url: format!("http://{address}/"),
        token: token.to_owned(),
        packages,
        agents,
        state: Mutex::new(State {
            armed: Armed::CLEAR,
            hold: None,
            generation: 0,
            events: Vec::new(),
            stopped: false,
        }),
        released: Condvar::new(),
    })
}

impl Handler for Registry {
    fn control_route(&self) -> Option<(&'static str, &str)> {
        Some((NAME, &self.token))
    }

    fn control(&self, request: &Request, route: &str) -> Response {
        match (route, request.method.as_str()) {
            ("mark", "GET") => control::answer(&serde_json::json!({ "mark": self.mark() })),
            ("events", "GET") => match control::since(request) {
                Ok(since) => {
                    let prefix = request.query_param("prefix").unwrap_or_default();
                    let events = self.events_since(since, &prefix);
                    control::answer(&serde_json::json!({ "mark": self.mark(), "events": events }))
                }
                Err(refusal) => refusal,
            },
            ("fault", "GET") => control::answer(&self.lock().armed),
            ("fault", "POST") => match control::body::<ArmRequest>(request) {
                Ok(arm) => match arm.armed() {
                    Ok(armed) => control::answer(&self.set_armed(armed)),
                    Err(refusal) => control::refuse(400, refusal.code, &refusal.message),
                },
                Err(refusal) => refusal,
            },
            ("fault", "DELETE") => control::answer(&self.set_armed(Armed::CLEAR)),
            ("mark" | "events", _) => control::method_not_allowed(request, "GET"),
            ("fault", _) => control::method_not_allowed(request, "GET, POST or DELETE"),
            _ => control::unknown_route(request),
        }
    }

    fn handle(&self, request: &Request) -> Response {
        let received_at_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_or(0, |elapsed| u64::try_from(elapsed.as_millis()).unwrap_or(u64::MAX));
        let (armed, hold) = {
            let state = self.lock();
            (state.armed, state.hold)
        };
        let response = self.answer(request, armed, hold);
        let mut state = self.lock();
        let sequence = state.events.len() + 1;
        state.events.push(Event {
            sequence,
            method: request.method.clone(),
            path: request.path.clone(),
            received_at_ms,
            status: response.status,
            response_bytes: response.sent_bytes(),
            truncated: matches!(response.body, super::http::Body::Truncated { .. }),
            fault: armed.fault,
        });
        response
    }

    fn stop(&self) {
        let mut state = self.lock();
        state.stopped = true;
        state.hold = None;
        self.released.notify_all();
    }
}

impl Registry {
    fn lock(&self) -> MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    fn answer(&self, request: &Request, armed: Armed, hold: Option<u64>) -> Response {
        if request.path == "/registry.json" {
            return match armed.fault {
                Fault::RegistryHttpError => Response::with_body(armed.registry_status, "text/plain", Vec::new()),
                Fault::MalformedRegistry => Response::with_body(200, "application/json", MALFORMED_REGISTRY),
                fault => {
                    if fault == Fault::SlowResponse {
                        self.wait_for_release(hold, armed.hold_ms);
                    }
                    Response::with_body(200, "application/json", self.registry_json(fault))
                }
            };
        }
        let Some(platform) = request.path.strip_prefix(PACKAGE_PREFIX).and_then(|rest| rest.strip_suffix(".zip")) else {
            return Response::with_body(404, "text/plain", Vec::new());
        };
        let Some(archive) = self.packages.archive(platform) else {
            return Response::with_body(404, "text/plain", Vec::new());
        };
        match armed.fault {
            Fault::PackageHttpError => Response::with_body(armed.package_status, "text/plain", Vec::new()),
            Fault::InvalidArchive => Response::with_body(200, "application/zip", INVALID_ARCHIVE),
            Fault::TruncatedResponse => {
                let keep = (archive.len() / 2).max(1);
                Response::truncated(200, "application/zip", archive.len(), archive[..keep].to_vec())
            }
            fault => {
                if fault.holds() {
                    self.wait_for_release(hold, armed.hold_ms);
                }
                Response::with_body(200, "application/zip", archive)
            }
        }
    }

    /// Waits while the hold of `generation` is armed, at most `hold_ms`. A hold that ends unreleased still answers, so
    /// the client sees a slow answer and never a hang.
    fn wait_for_release(&self, generation: Option<u64>, hold_ms: u64) {
        let Some(generation) = generation else { return };
        let deadline = Instant::now() + Duration::from_millis(hold_ms);
        let mut state = self.lock();
        while state.hold == Some(generation) && !state.stopped {
            let left = deadline.saturating_duration_since(Instant::now());
            if left.is_zero() {
                return;
            }
            state = self
                .released
                .wait_timeout(state, left)
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .0;
        }
    }

    /// The text of `registry.json` under `fault`, which decides the checksums.
    fn registry_json(&self, fault: Fault) -> String {
        let invalid_checksum = package::sha256_hex(INVALID_ARCHIVE);
        let checksum_override = match fault {
            Fault::ChecksumMismatch => Some(BAD_CHECKSUM),
            Fault::InvalidArchive => Some(invalid_checksum.as_str()),
            _ => None,
        };
        registry_json(&self.base_url, &self.packages, &self.agents, checksum_override)
    }

    /// The count of the requests that the registry answered so far: the mark that a later count starts after.
    pub(crate) fn mark(&self) -> usize {
        self.lock().events.len()
    }

    /// The answered requests after `mark` whose path starts with `prefix`, in order.
    pub(crate) fn events_since(&self, mark: usize, prefix: &str) -> Vec<Event> {
        control::after(&self.lock().events, mark)
            .iter()
            .filter(|event| event.path.starts_with(prefix))
            .cloned()
            .collect()
    }

    /// Every answered request, one line each, for a failure message.
    pub(crate) fn request_log(&self) -> String {
        let state = self.lock();
        if state.events.is_empty() {
            return "the fake registry answered no request".to_owned();
        }
        state
            .events
            .iter()
            .map(|event| {
                format!(
                    "#{} {} {} -> {} ({})",
                    event.sequence,
                    event.method,
                    event.path,
                    event.status,
                    event.fault.id()
                )
            })
            .collect::<Vec<_>>()
            .join("\n")
    }

    /// The id of the armed fault, `none` when no fault is armed.
    pub(crate) fn armed_fault(&self) -> &'static str {
        self.lock().armed.fault.id()
    }

    /// Arms the fault `fault` with its options. Arming the whole fault restores the default of each option, and a held
    /// answer of an earlier fault is released.
    pub(crate) fn arm(&self, fault: &str, status: Option<u16>, hold_ms: Option<u64>) -> Result<(), String> {
        let request = ArmRequest {
            fault: fault.to_owned(),
            status,
            hold_ms,
        };
        let armed = request.armed().map_err(|refusal| refusal.message)?;
        self.set_armed(armed);
        Ok(())
    }

    /// Clears the armed fault, and releases a held answer.
    pub(crate) fn clear(&self) {
        self.set_armed(Armed::CLEAR);
    }

    fn set_armed(&self, armed: Armed) -> Armed {
        let mut state = self.lock();
        state.armed = armed;
        state.generation += 1;
        state.hold = armed.fault.holds().then_some(state.generation);
        self.released.notify_all();
        state.armed
    }
}

/// The body of `POST /_lane/registry/fault`.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct ArmRequest {
    fault: String,
    status: Option<u16>,
    hold_ms: Option<u64>,
}

/// Why an arm request names no fault that the registry can arm, with the code of its refusal.
struct ArmRefusal {
    code: &'static str,
    message: String,
}

impl ArmRefusal {
    const fn bad(message: String) -> Self {
        Self {
            code: "bad_request",
            message,
        }
    }
}

impl ArmRequest {
    fn armed(self) -> Result<Armed, ArmRefusal> {
        let ids = || Fault::ALL.map(Fault::id).join(", ");
        let fault = Fault::of_id(&self.fault).ok_or_else(|| ArmRefusal {
            code: "unknown_fault",
            message: format!("the fault {:?} is not one of {}", self.fault, ids()),
        })?;
        if self.status.is_some() && !fault.takes_status() {
            return Err(ArmRefusal::bad(format!("the fault {} takes no status", fault.id())));
        }
        if self.hold_ms.is_some() && !fault.holds() {
            return Err(ArmRefusal::bad(format!("the fault {} takes no holdMs", fault.id())));
        }
        if let Some(status) = self.status.filter(|status| !(100..=599).contains(status)) {
            return Err(ArmRefusal::bad(format!("the status {status} is no HTTP status")));
        }
        if let Some(hold_ms) = self.hold_ms.filter(|hold_ms| !(1..=MAX_HOLD_MS).contains(hold_ms)) {
            return Err(ArmRefusal::bad(format!("the holdMs {hold_ms} is not in 1..={MAX_HOLD_MS}")));
        }
        let mut armed = Armed { fault, ..Armed::CLEAR };
        match fault {
            Fault::RegistryHttpError => armed.registry_status = self.status.unwrap_or(DEFAULT_REGISTRY_STATUS),
            Fault::PackageHttpError => armed.package_status = self.status.unwrap_or(DEFAULT_PACKAGE_STATUS),
            _ => {}
        }
        armed.hold_ms = self.hold_ms.unwrap_or(DEFAULT_HOLD_MS);
        Ok(armed)
    }
}

/// The text of `registry.json`: every agent of `agents` with an archive per platform under `base_url`.
/// `checksum_override` replaces the checksum of every archive.
///
/// The text is composed by hand in the field order of the Java registry, so the IDE reads the same document.
pub(crate) fn registry_json(base_url: &str, packages: &Packages, agents: &[RegistryAgent], checksum_override: Option<&str>) -> String {
    let mut json = String::from("{\"version\":\"air-fixture-registry-v1\",\"agents\":[");
    for (index, agent) in agents.iter().enumerate() {
        if index > 0 {
            json.push(',');
        }
        json.push_str(&format!(
            "{{\"id\":{},\"name\":{},\"version\":\"{}\",\"description\":\"Test-controlled offline ACP proxy\",\"distribution\":{{\"binary\":{{",
            quote(&agent.registry_id),
            quote(&agent.display_name),
            package::VERSION
        ));
        for (index, platform) in PLATFORMS.iter().enumerate() {
            if index > 0 {
                json.push(',');
            }
            let checksum = checksum_override.or_else(|| packages.sha256(platform)).unwrap_or_default();
            json.push_str(&format!(
                "\"{platform}\":{{\"archive\":\"{base_url}packages/acp-scenario-agent-{platform}.zip\",\"sha256\":\"{checksum}\",\"cmd\":{}",
                quote(&packages.command(platform))
            ));
            let args = Packages::args(platform);
            if !args.is_empty() {
                let args: Vec<String> = args.iter().map(|argument| quote(argument)).collect();
                json.push_str(&format!(",\"args\":[{}]", args.join(",")));
            }
            let environment: Vec<String> = environment(agent)
                .iter()
                .map(|(name, value)| format!("{}:{}", quote(name), quote(value)))
                .collect();
            json.push_str(&format!(",\"env\":{{{}}}}}", environment.join(",")));
        }
        json.push_str("}}}");
    }
    json.push_str("]}");
    json
}

/// The manifest environment of `agent`: the two package variables, then its own. Its own value of a package variable
/// replaces that value in place.
fn environment(agent: &RegistryAgent) -> Vec<(String, String)> {
    let mut environment = vec![
        (PACKAGE_TOKEN_ENV.to_owned(), package::PACKAGE_TOKEN.to_owned()),
        (STARTUP_TOKEN_ENV.to_owned(), "registry-installed".to_owned()),
    ];
    for (name, value) in &agent.environment {
        match environment.iter_mut().find(|(known, _)| known == name) {
            Some(entry) => entry.1.clone_from(value),
            None => environment.push((name.clone(), value.clone())),
        }
    }
    environment
}

fn quote(text: &str) -> String {
    serde_json::Value::from(text).to_string()
}
