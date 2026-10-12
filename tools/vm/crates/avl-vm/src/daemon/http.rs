//! The daemon's control channel: one authenticated HTTP request at a time, and the daemon's account of itself.
//!
//! Every request travels through a relay over the worker's exec channel to `127.0.0.1:<port>` inside the guest (see
//! [`connector`]). The host opens no socket to the guest network.

use std::io;
use std::sync::Arc;
use std::time::Duration;

use crate::worker::worker::{ChannelFactory, Manager};
use avl_base::format::clip;
use avl_base::{Exit, Refusal};
use avl_host_sys::Ctx;
use avl_wire::daemon::{self as wire, Endpoint, ProtocolError, Status};
use http_body_util::{BodyExt, Full};
use hyper::body::Bytes;
use hyper::body::Incoming;
use hyper::{Method, Request, Response, StatusCode};
use hyper_util::client::legacy::Client;
use hyper_util::rt::{TokioExecutor, TokioTimer};

use crate::daemon::state::HostState;
use connector::RelayConnector;

mod connector;
#[cfg(test)]
#[cfg(unix)]
mod tests;

/// Authenticates every request on the control channel.
pub(crate) const TOKEN_HEADER: &str = "X-Air-Daemon-Token";

/// Bounds a route that declares no budget of its own and is not streamed. Only such a route can reach it; today
/// every fixed route except `/run` carries its own number.
pub(crate) const DEFAULT_ROUTE_TIMEOUT: Duration = Duration::from_secs(15);

/// How much of a body that is not a status a probe quotes.
const QUOTED_BODY_BYTES: usize = 120;

/// How one exchange failed before it produced an answer. The four are kept apart because they have four different
/// repairs: a relay that never opened is the worker's exec channel or a daemon that does not listen, a silent one is
/// a starved or wedged daemon, a broken one is whatever the error says, and an interrupt is the operator's.
#[derive(Debug)]
pub(crate) enum Transport {
    Connect(String),
    NoReply,
    Broken(String),
    Interrupted,
}

impl Transport {
    /// The failure as the second half of a refusal's sentence, after the route and the daemon it names.
    fn detail(&self, budget: Duration) -> String {
        match self {
            Self::Interrupted => "was interrupted".to_owned(),
            Self::Connect(detail) | Self::Broken(detail) => format!("failed: {detail}"),
            Self::NoReply => format!("failed: no answer within {}", budget_text(budget)),
        }
    }
}

/// An error with every source it wraps, so the cause a person can act on survives: hyper's own text is "client
/// error (SendRequest)", and the relay's exit is two sources further down.
pub(crate) fn error_chain(error: &(dyn std::error::Error + 'static)) -> String {
    let mut text = error.to_string();
    let mut source = error.source();
    while let Some(cause) = source {
        text.push_str(": ");
        text.push_str(&cause.to_string());
        source = cause.source();
    }
    text
}

/// Why the relay under a failed request did not open, or `None` when it opened.
///
/// Two shapes. The connector itself can fail: no relay started. Or the relay started and exited before it delivered
/// a byte, which the first read reports as `ConnectionRefused`. The second shape arrives inside a send error, because
/// hyper writes the request before it reads. After the first byte, the relay reports no `ConnectionRefused`.
fn relay_failure(error: &hyper_util::client::legacy::Error) -> Option<String> {
    let mut deepest = None;
    let mut source = std::error::Error::source(error);
    while let Some(cause) = source {
        if let Some(io) = cause.downcast_ref::<io::Error>()
            && io.kind() == io::ErrorKind::ConnectionRefused
        {
            return Some(io.to_string());
        }
        deepest = Some(cause.to_string());
        source = cause.source();
    }
    if error.is_connect() {
        return Some(deepest.unwrap_or_else(|| error.to_string()));
    }
    None
}

/// A budget as a person reads it: whole seconds where it is whole.
fn budget_text(budget: Duration) -> String {
    if budget.subsec_millis() == 0 {
        format!("{} s", budget.as_secs())
    } else {
        format!("{:.1} s", budget.as_secs_f64())
    }
}

/// The control channel of every daemon one pool runs.
///
/// One per [`crate::daemon::Host`], and cheap to clone: the clones share one connection pool. The pool keys on
/// `<worker>:<port>`, so a warm iteration sends its requests over one relay, and each worker has its own pool.
/// Deadlines are per request, because `/run` must not inherit a client-wide timeout.
#[derive(Clone)]
pub(crate) struct DaemonClient {
    client: Client<RelayConnector, Full<Bytes>>,
}

impl std::fmt::Debug for DaemonClient {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter.debug_struct("DaemonClient").finish_non_exhaustive()
    }
}

impl DaemonClient {
    /// A client that reaches each worker through the channel `channels` names for it.
    pub(crate) fn new(channels: ChannelFactory) -> Self {
        let client = Client::builder(TokioExecutor::new())
            .pool_timer(TokioTimer::new())
            .build(RelayConnector::new(channels));
        Self { client }
    }

    /// A client over the worker manager's exec channels.
    pub(crate) fn over(manager: &Arc<Manager>) -> Self {
        let manager = Arc::clone(manager);
        Self::new(Arc::new(move |worker: &str| manager.channel(worker)))
    }

    /// Sends the request for one endpoint, authenticated, and answers the response head. `/run` reads its body as a
    /// stream; every other route reads it whole through [`DaemonClient::http`].
    pub(crate) async fn send(&self, state: &HostState, endpoint: &Endpoint, body: Bytes) -> Result<Response<Incoming>, Transport> {
        let method = match endpoint.method {
            wire::Method::Get => Method::GET,
            wire::Method::Post => Method::POST,
            wire::Method::Put => Method::PUT,
        };
        let request = Request::builder()
            .method(method)
            .uri(daemon_url(state, &endpoint.path))
            .header(TOKEN_HEADER, &state.token)
            .body(Full::new(body))
            .map_err(|error| Transport::Broken(format!("cannot build the request: {error}")))?;
        self.client.request(request).await.map_err(|error| {
            relay_failure(&error).map_or_else(
                || Transport::Broken(error_chain(&error)),
                |cause| {
                    Transport::Connect(format!(
                        "the relay to 127.0.0.1:{} inside {} did not open: {cause}",
                        state.port, state.worker
                    ))
                },
            )
        })
    }

    /// One exchange on the control channel, its failure classified.
    ///
    /// The route's budget bounds the whole exchange, the body included. A relay that the guest refuses reads as
    /// [`Transport::Connect`]. A relay that hangs before its first byte reads as [`Transport::NoReply`] when the route
    /// budget ends, the same as a daemon that does not answer.
    pub(crate) async fn exchange(
        &self,
        ctx: &Ctx,
        state: &HostState,
        endpoint: &Endpoint,
        body: Option<Bytes>,
    ) -> Result<(StatusCode, Bytes), Transport> {
        let budget = endpoint.timeout.unwrap_or(DEFAULT_ROUTE_TIMEOUT);
        let exchange = async {
            let response = self.send(state, endpoint, body.unwrap_or_default()).await?;
            let status = response.status();
            let collected = response
                .into_body()
                .collect()
                .await
                .map_err(|error| Transport::Broken(error_chain(&error)))?;
            Ok((status, collected.to_bytes()))
        };
        tokio::select! {
            answered = tokio::time::timeout(budget, exchange) => {
                answered.unwrap_or(Err(Transport::NoReply))
            }
            () = ctx.cancelled() => Err(Transport::Interrupted),
        }
    }

    /// One request on the control channel, whole body in and whole body out.
    ///
    /// The endpoint carries its own method and budget, so no call site can give `/status` a full-IDE-stop timeout
    /// or `/mount/quiesce` a five-second one. The one route with no budget - `/run` - is streamed and never comes
    /// through here; its deadline belongs to the transport watchdog.
    ///
    /// A transport failure is `daemon_http_failed` at 69: the daemon did not answer, which is a fact about the
    /// worker or the daemon rather than about this controller.
    pub(crate) async fn http(
        &self,
        ctx: &Ctx,
        state: &HostState,
        endpoint: &Endpoint,
        body: Option<Bytes>,
    ) -> Result<(StatusCode, Bytes), Refusal> {
        self.exchange(ctx, state, endpoint, body)
            .await
            .map_err(|failure| transport_refusal(state, endpoint, &failure))
    }

    /// One `/status` probe, classified.
    ///
    /// Every answer but [`StatusProbe::Healthy`] means "start a fresh one" to a caller that only asks whether a
    /// daemon is usable. A protocol mismatch is *not* one of them: a daemon speaking another protocol version would
    /// be restarted forever by a controller that is itself the stale half - so it refuses instead, naming both
    /// versions.
    pub(crate) async fn probe_status(&self, ctx: &Ctx, state: &HostState) -> Result<StatusProbe, Refusal> {
        let (status, body) = match self.exchange(ctx, state, &wire::STATUS, None).await {
            Ok(answered) => answered,
            Err(Transport::Connect(detail)) => return Ok(StatusProbe::NoConnection(detail)),
            Err(Transport::NoReply) => return Ok(StatusProbe::NoReply),
            Err(Transport::Broken(detail)) => return Ok(StatusProbe::Broken(detail)),
            Err(Transport::Interrupted) => return Ok(StatusProbe::Interrupted),
        };
        if !status.is_success() {
            return Ok(StatusProbe::NotAStatus(format!("HTTP {}", status.as_u16())));
        }
        // A body that is not a JSON object at all is an unusable daemon rather than a protocol failure.
        if serde_json::from_slice::<serde_json::Map<String, serde_json::Value>>(&body).is_err() {
            let text = String::from_utf8_lossy(&body);
            return Ok(StatusProbe::NotAStatus(format!(
                "a body that is not a JSON object: {}",
                clip(&text, QUOTED_BODY_BYTES)
            )));
        }
        // An unusable daemon is an answer, but one that answers with a body this controller cannot read is a
        // protocol failure, and folding the two together is what would restart a mismatched daemon forever.
        let decoded = wire::decode_status(&body).map_err(protocol_refusal)?;
        require_supported_protocol(decoded.protocol_version)?;
        // A rebooted daemon has a fresh token, so a matching boot stamp is implied by the 200; still verified.
        if decoded.daemon_boot_stamp != state.daemon_boot_stamp {
            return Ok(StatusProbe::OtherDaemon(format!(
                "boot stamp {}, expected {}",
                decoded.daemon_boot_stamp, state.daemon_boot_stamp
            )));
        }
        if decoded.controller_launch_digest != state.launch_digest {
            return Ok(StatusProbe::OtherDaemon(format!(
                "launch digest {}, expected {}",
                decoded.controller_launch_digest, state.launch_digest
            )));
        }
        if decoded.mount_quiesced {
            return Ok(StatusProbe::Quiesced);
        }
        Ok(StatusProbe::Healthy(decoded))
    }

    /// The daemon's own account of itself, or `None` when there is no daemon this controller can use.
    ///
    /// A protocol mismatch is *not* folded into that `None`. `None` means "start a fresh one", and a daemon speaking
    /// another protocol version would be restarted forever by a controller that is itself the stale half - so it
    /// refuses instead, naming both versions. [`DaemonClient::probe_status`] says which `None` it was.
    pub(crate) async fn status(&self, ctx: &Ctx, state: &HostState) -> Result<Option<Status>, Refusal> {
        self.probe_status(ctx, state).await.map(StatusProbe::healthy)
    }
}

/// The URL of one path on the daemon a state names. The authority is the worker and the daemon's guest port, which
/// the connector turns into a relay.
pub(crate) fn daemon_url(state: &HostState, path: &str) -> String {
    format!("http://{}:{}{path}", state.worker, state.port)
}

/// A transport failure as the refusal of one route: `daemon_http_failed` at 69.
pub(crate) fn transport_refusal(state: &HostState, endpoint: &Endpoint, failure: &Transport) -> Refusal {
    let detail = failure.detail(endpoint.timeout.unwrap_or(DEFAULT_ROUTE_TIMEOUT));
    Refusal::new(
        "daemon_http_failed",
        Exit::UNAVAILABLE,
        format!(
            "{} {} on the daemon at {}:{} {detail}",
            endpoint.method, endpoint.path, state.worker, state.port
        ),
    )
}

/// What one `/status` probe saw: the daemon this controller launched, or the reason it is not one it can use.
#[derive(Debug)]
pub(crate) enum StatusProbe {
    /// The daemon this controller launched, unquiesced.
    Healthy(Status),
    /// No relay to the daemon's guest port opened.
    NoConnection(String),
    /// A connection opened and nothing answered within the route's budget.
    NoReply,
    /// The exchange failed after the connection opened.
    Broken(String),
    /// Something answered, and not with a status: a non-2xx code or a body that is not a JSON object.
    NotAStatus(String),
    /// A daemon answered, and not the one this controller launched: another boot or another launch.
    OtherDaemon(String),
    /// This controller's daemon, quiesced for a remount.
    Quiesced,
    /// The operation was interrupted before the probe answered.
    Interrupted,
}

impl StatusProbe {
    /// The status, for a healthy probe only.
    pub(crate) fn healthy(self) -> Option<Status> {
        match self {
            Self::Healthy(status) => Some(status),
            _ => None,
        }
    }

    /// The probe's reason as one word, for a refusal's details.
    pub(crate) const fn reason(&self) -> &'static str {
        match self {
            Self::Healthy(_) => "healthy",
            Self::NoConnection(_) => "no_connection",
            Self::NoReply => "no_reply",
            Self::Broken(_) => "broken",
            Self::NotAStatus(_) => "not_a_status",
            Self::OtherDaemon(_) => "other_daemon",
            Self::Quiesced => "quiesced",
            Self::Interrupted => "interrupted",
        }
    }

    /// What the probe saw, in its own words.
    pub(crate) fn detail(&self) -> String {
        match self {
            Self::Healthy(_) => "healthy".to_owned(),
            Self::NoConnection(detail) => format!("could not connect: {detail}"),
            Self::NoReply => format!(
                "no answer within {}",
                budget_text(wire::STATUS.timeout.unwrap_or(DEFAULT_ROUTE_TIMEOUT))
            ),
            Self::Broken(detail) => format!("failed after connecting: {detail}"),
            Self::NotAStatus(detail) | Self::OtherDaemon(detail) => detail.clone(),
            Self::Quiesced => "quiesced for a remount".to_owned(),
            Self::Interrupted => "interrupted".to_owned(),
        }
    }
}

/// Refuses a daemon this controller cannot read, by name and at boot.
///
/// The daemon jar is stable-tier, so the launch digest already restarts a daemon built from another build. What
/// this catches is the other skew: this controller lives outside the Bazel graph, so a Kotlin protocol change and a
/// controller one can be committed apart. Without the check the first symptom is a decode failure mid-run, after a
/// build and an IDE launch.
pub(crate) fn require_supported_protocol(reported: u32) -> Result<(), Refusal> {
    if reported == wire::PROTOCOL_VERSION {
        return Ok(());
    }
    Err(Refusal::new(
        "daemon_protocol_unsupported",
        Exit::SOFTWARE,
        format!(
            "the guest daemon speaks run protocol {reported} and this controller speaks {}. One half of \
             `plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonProtocol.kt` and \
             `community/tools/vm/crates/avl-wire` was updated without the other — or both are \
             current and this guest still holds a daemon from before the change, which `daemon stop` retires.",
            wire::PROTOCOL_VERSION
        ),
    ))
}

/// A wire refusal as the controller's coded refusal, keeping the wire's own code.
pub(crate) fn protocol_refusal(error: ProtocolError) -> Refusal {
    Refusal::new(error.code, Exit::SOFTWARE, error.message)
}
