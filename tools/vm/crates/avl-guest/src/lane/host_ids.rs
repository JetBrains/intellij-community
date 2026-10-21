//! The host ids of the flow vocabulary: what the lane daemon performs itself, against a lane service or the lane IDE
//! process, instead of through the bridge.
//!
//! The words are [`HostAction`] and [`HostAssertion`] of the suite program. `restart-ide` quits the IDE and launches it
//! again on the same context, and performs the file operations of the value `whileDown` in between.
//! `recycle-through-failed-reset` proves the recycle of an IDE whose reset did not hold. `reload-air` reloads Air in
//! place, and the IDE keeps its process. The registry ids
//! hold, fail, release and count the package downloads of the fake registry. `account-approve-sign-in` approves the
//! JetBrains Account login that the IDE started, in the emulator, and follows its redirect to the callback of the IDE.

use std::collections::BTreeMap;
use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream, ToSocketAddrs};
use std::path::{Component, Path};
use std::time::{Duration, Instant};

use avl_wire::program::{HostAction, HostAssertion};
use serde::Deserialize;
use serde_json::{Value, json};

use super::values::Values;

#[cfg(test)]
mod tests;

/// The value that `registry-mark-requests` answers and `registry-request-count` takes.
pub(crate) const REGISTRY_BASELINE: &str = "consentRegistryBaseline";
/// The value that `account-approve-sign-in` takes.
pub(crate) const SIGN_IN_URL: &str = "signInUrl";
/// The file operations that the next `restart-ide` performs while the IDE is down. The restart empties it.
pub(crate) const WHILE_DOWN: &str = "whileDown";
/// The text of each file that the last `restart-ide` read while the IDE was down, by its path in the context.
pub(crate) const WHILE_DOWN_READS: &str = "whileDownReads";

/// How long a held package answer waits for its release.
const HOLD_MS: u64 = 120_000;
/// The status of a failed package download.
const PACKAGE_ERROR_STATUS: u16 = 503;
/// The fault ids of the fake registry, as its control route spells them.
const SLOW_PACKAGE_RESPONSE: &str = "slow-package-response";
const PACKAGE_HTTP_ERROR: &str = "package-http-error";
/// The prefix of a package request in the request log of the registry.
pub(crate) const PACKAGE_PREFIX: &str = "/packages/";
/// How often and how long `registry-request-count` waits for a count other than zero.
const COUNT_POLL: Duration = Duration::from_millis(250);
const COUNT_WAIT: Duration = Duration::from_mins(1);
/// Bounds each request of the sign-in approval.
const SIGN_IN_WAIT: Duration = Duration::from_secs(30);

/// The lane services as a host id steers them. The lane daemon steers its own services in process; a test gives a fake.
pub(crate) trait ServiceControl: Sync {
    /// The count of requests that the registry answered so far.
    fn registry_mark(&self) -> Result<usize, String>;
    /// How many requests of a path below `prefix` the registry answered after `mark`.
    fn registry_requests_since(&self, mark: usize, prefix: &str) -> Result<usize, String>;
    /// The request log of the registry, one line per answered request, for a failure message.
    fn registry_log(&self) -> String;
    /// Arms one distribution fault of the registry.
    fn registry_arm(&self, fault: &str, status: Option<u16>, hold_ms: Option<u64>) -> Result<(), String>;
    /// Clears the armed fault, and releases a held answer.
    fn registry_clear(&self) -> Result<(), String>;
    /// The id of the armed fault, `none` when no fault is armed.
    fn registry_fault(&self) -> Result<String, String>;
    /// Empties the queue of the feedback capture.
    fn feedback_clear(&self) -> Result<(), String>;
    /// Empties the record of the JetBrains Cloud emulator and of its Agent Spawner.
    fn cloud_clear(&self) -> Result<(), String>;
    /// Points the Agent Spawner of the JetBrains Cloud emulator at the control server credential `agent_key`, or at no
    /// agent for `None`.
    fn cloud_attach_spawner(&self, agent_key: Option<&str>) -> Result<(), String>;
    /// The base URL of the JetBrains Cloud emulator, which every sign-in URL of the IDE starts with.
    fn cloud_base_url(&self) -> Result<String, String>;
}

/// What a host id needs beyond the services: the restart of the IDE.
pub(crate) trait HostSide {
    fn services(&self) -> &dyn ServiceControl;

    /// Quits the IDE, performs `while_down` on its context directory, and launches it again on the same context.
    fn restart_ide(&mut self, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String>;

    /// Fails the next state reset on purpose, recycles the IDE as a reset that does not hold does, and resets the
    /// replacement. It fails unless that reset passed on a new IDE process that answers the bridge.
    fn recycle_through_failed_reset(&mut self) -> Result<(), String>;

    /// Reloads Air in place, waits until the lifecycle names the same bridge plugin again, and pings the new copy.
    fn reload_air(&mut self) -> Result<(), String>;

    /// Copies the Central wiring of the guest account into the lane home again when the account re-wired.
    fn refresh_live_wiring(&mut self) -> Result<(), String> {
        Err("the lane has no live run, so it has no Central wiring to refresh".to_owned())
    }

    /// Hands each Junie session of the live project that the guest account holds to the reset.
    fn adopt_leftover_junie_sessions(&mut self) -> Result<(), String> {
        Err("the lane has no live run, so it has no Junie session to hand over".to_owned())
    }
}

/// One host id of an instruction.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum HostId {
    Action(HostAction),
    Assertion(HostAssertion),
}

impl HostId {
    /// The host id of the action or assertion `id`, or `None` when no host word names it.
    pub(crate) fn of(assertion: bool, id: &str) -> Option<Self> {
        if assertion {
            id.parse().ok().map(Self::Assertion)
        } else {
            id.parse().ok().map(Self::Action)
        }
    }
}

/// Performs `id` with the arguments `args` of the instruction `label`, and keeps the values that it answers.
pub(crate) fn perform(
    id: HostId,
    label: &str,
    args: &BTreeMap<String, Value>,
    values: &mut Values,
    side: &mut dyn HostSide,
) -> Result<(), String> {
    match id {
        HostId::Action(HostAction::AccountApproveSignIn) => {
            let url = values
                .require(label, SIGN_IN_URL)?
                .as_str()
                .ok_or_else(|| format!("the value '{SIGN_IN_URL}' is no text"))?;
            approve_sign_in(url, &side.services().cloud_base_url()?)
        }
        HostId::Action(HostAction::ConsentHoldDownload) => side.services().registry_arm(SLOW_PACKAGE_RESPONSE, None, Some(HOLD_MS)),
        HostId::Action(HostAction::RegistryFailNextDownload) => {
            side.services().registry_arm(PACKAGE_HTTP_ERROR, Some(PACKAGE_ERROR_STATUS), None)
        }
        HostId::Action(HostAction::RegistryMarkRequests) => {
            let mark = side.services().registry_mark()?;
            values.set(REGISTRY_BASELINE, json!(mark));
            Ok(())
        }
        HostId::Action(HostAction::RecycleThroughFailedReset) => side.recycle_through_failed_reset(),
        HostId::Action(HostAction::ReleaseDownload) => side.services().registry_clear(),
        HostId::Action(HostAction::ReloadAir) => side.reload_air(),
        HostId::Action(HostAction::RestartIde) => restart_ide(values, side),
        HostId::Action(HostAction::RefreshLiveWiring) => side.refresh_live_wiring(),
        HostId::Action(HostAction::AdoptLeftoverJunieSessions) => side.adopt_leftover_junie_sessions(),
        HostId::Assertion(HostAssertion::ConsentDownloadFaultArmed) => {
            let fault = side.services().registry_fault()?;
            if fault == PACKAGE_HTTP_ERROR {
                Ok(())
            } else {
                Err(format!("expected a package HTTP fault, found {fault}"))
            }
        }
        HostId::Assertion(HostAssertion::RegistryRequestCount) => {
            let baseline = values.require(label, REGISTRY_BASELINE)?;
            let baseline = baseline
                .as_u64()
                .and_then(|baseline| usize::try_from(baseline).ok())
                .ok_or_else(|| format!("the value '{REGISTRY_BASELINE}' is {baseline}, which is no count"))?;
            let expected = args
                .get("requests")
                .and_then(Value::as_u64)
                .and_then(|expected| usize::try_from(expected).ok())
                .ok_or_else(|| format!("the instruction '{label}' states no count 'requests'"))?;
            await_request_count(side.services(), baseline, expected, COUNT_WAIT)
        }
    }
}

/// Requires that the registry answered exactly `expected` package requests after `baseline`. The registry records a
/// request when it answers it, so a held download is no request yet. Zero is read at once; any other count is awaited.
pub(crate) fn await_request_count(services: &dyn ServiceControl, baseline: usize, expected: usize, wait: Duration) -> Result<(), String> {
    let deadline = Instant::now() + wait;
    let mut found = services.registry_requests_since(baseline, PACKAGE_PREFIX)?;
    while expected != 0 && found != expected && Instant::now() < deadline {
        std::thread::sleep(COUNT_POLL);
        found = services.registry_requests_since(baseline, PACKAGE_PREFIX)?;
    }
    if found == expected {
        return Ok(());
    }
    Err(format!(
        "expected {expected} package request(s) after registry event {baseline}, found {found}\n{}",
        services.registry_log()
    ))
}

/// One file operation of `whileDown`, `AirFlowContextFileOp` of the bridge.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase", deny_unknown_fields)]
pub(crate) enum ContextFileOp {
    /// Appends `text` to the file, which must exist.
    Append { path: String, text: String },
    /// Reads the file.
    Read { path: String },
}

/// Quits the IDE, performs the operations of `whileDown` between the quit and the launch, and answers an empty
/// `whileDown` and the text of each read in `whileDownReads`, so a later restart performs no operation twice.
fn restart_ide(values: &mut Values, side: &mut dyn HostSide) -> Result<(), String> {
    let operations: Vec<ContextFileOp> = match values.get(WHILE_DOWN) {
        Some(value) => serde_json::from_value(value.clone())
            .map_err(|error| format!("the value '{WHILE_DOWN}' is not a list of file operations: {error}"))?,
        None => Vec::new(),
    };
    let mut reads = serde_json::Map::new();
    side.restart_ide(&mut |context| {
        reads = apply_context_file_ops(context, &operations)?;
        Ok(())
    })?;
    values.set(WHILE_DOWN, json!([]));
    values.set(WHILE_DOWN_READS, Value::Object(reads));
    Ok(())
}

/// Performs `operations` in order under `context`, and answers the text of each read by its path.
pub(crate) fn apply_context_file_ops(context: &Path, operations: &[ContextFileOp]) -> Result<serde_json::Map<String, Value>, String> {
    let mut reads = serde_json::Map::new();
    for operation in operations {
        let (ContextFileOp::Append { path, .. } | ContextFileOp::Read { path }) = operation;
        let relative = Path::new(path);
        let inside = !path.is_empty() && relative.components().all(|component| matches!(component, Component::Normal(_)));
        if !inside {
            return Err(format!("the path '{path}' is not a file of the IDE context {}", context.display()));
        }
        let file = context.join(relative);
        if !file.is_file() {
            return Err(format!(
                "the host finds no file at '{path}' of the IDE context {}",
                context.display()
            ));
        }
        match operation {
            ContextFileOp::Append { text, .. } => OpenOptions::new()
                .append(true)
                .open(&file)
                .and_then(|mut open| open.write_all(text.as_bytes()))
                .map_err(|error| format!("cannot append to {}: {error}", file.display()))?,
            ContextFileOp::Read { .. } => {
                let text = fs::read_to_string(&file).map_err(|error| format!("cannot read {}: {error}", file.display()))?;
                reads.insert(path.clone(), Value::String(text));
            }
        }
    }
    Ok(reads)
}

/// Approves the login of `url` in the emulator at `base_url`. The authorization answers a redirect to the callback of
/// the IDE, and the callback must answer a success or a redirect.
/// A `url` of another server is refused, because the IDE must open the emulated JetBrains Account.
pub(crate) fn approve_sign_in(url: &str, base_url: &str) -> Result<(), String> {
    if !url.starts_with(base_url) {
        return Err(format!(
            "the IDE opened {url}, which is not the emulated JetBrains Account at {base_url}"
        ));
    }
    let (status, location) = get(url)?;
    if status != 302 {
        return Err(format!(
            "the emulator answered the sign-in {url} with HTTP {status}, not a redirect"
        ));
    }
    let callback = location.ok_or_else(|| format!("the emulator answered the sign-in {url} with a redirect and no Location"))?;
    let (status, _) = get(&callback)?;
    if (200..400).contains(&status) {
        Ok(())
    } else {
        Err(format!("the IDE answered its sign-in callback {callback} with HTTP {status}"))
    }
}

/// The authority and the request target of an `http://` URL. The authority ends at the first `/`, `?` or `#`, so a
/// callback URL with a query and no path, `http://localhost:62345?code=c`, has the target `/?code=c`. The fragment
/// stays with the client and is no part of the target.
pub(crate) fn split_http_url(url: &str) -> Result<(&str, String), String> {
    let rest = url.strip_prefix("http://").ok_or_else(|| format!("the URL {url} is not http://"))?;
    let rest = rest.split_once('#').map_or(rest, |(before, _)| before);
    let end = rest.find(['/', '?']).unwrap_or(rest.len());
    let (authority, target) = rest.split_at(end);
    if authority.is_empty() {
        return Err(format!("the URL {url} names no address"));
    }
    let target = if target.starts_with('/') {
        target.to_owned()
    } else {
        format!("/{target}")
    };
    Ok((authority, target))
}

/// A connection to the first address of `authority` that accepts one. A name such as `localhost` resolves to the IPv6
/// and the IPv4 loopback, and a server listens on one of them, so each address is tried in turn, as a browser does.
fn connect_any(authority: &str) -> Result<TcpStream, String> {
    let addresses: Vec<SocketAddr> = authority
        .to_socket_addrs()
        .map_err(|error| format!("the address {authority} does not resolve: {error}"))?
        .collect();
    let mut last = format!("the address {authority} resolves to nothing");
    for address in addresses {
        match TcpStream::connect_timeout(&address, SIGN_IN_WAIT) {
            Ok(stream) => return Ok(stream),
            Err(error) => last = format!("{address}: {error}"),
        }
    }
    Err(last)
}

/// One `GET` of an `http://` URL on this machine: the status and the `Location` header of the answer.
fn get(url: &str) -> Result<(u16, Option<String>), String> {
    let (authority, target) = split_http_url(url)?;
    let mut stream = connect_any(authority).map_err(|error| format!("cannot connect to {url}: {error}"))?;
    let _ = stream.set_read_timeout(Some(SIGN_IN_WAIT));
    let _ = stream.set_write_timeout(Some(SIGN_IN_WAIT));
    let request = format!("GET {target} HTTP/1.1\r\nHost: {authority}\r\nConnection: close\r\n\r\n");
    stream
        .write_all(request.as_bytes())
        .map_err(|error| format!("cannot send GET {url}: {error}"))?;
    let mut answer = Vec::new();
    stream
        .take(64 << 10)
        .read_to_end(&mut answer)
        .map_err(|error| format!("cannot read the answer of GET {url}: {error}"))?;
    let text = String::from_utf8_lossy(&answer);
    let head = text.split("\r\n\r\n").next().unwrap_or_default();
    let mut lines = head.lines();
    let status = lines
        .next()
        .and_then(|line| line.split(' ').nth(1))
        .and_then(|status| status.parse().ok())
        .ok_or_else(|| format!("GET {url} answered no status line"))?;
    let location = lines.find_map(|line| {
        let (name, value) = line.split_once(':')?;
        name.trim().eq_ignore_ascii_case("location").then(|| value.trim().to_owned())
    });
    Ok((status, location))
}
