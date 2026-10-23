//! The lane services: the fake ACP registry, the JetBrains Cloud emulator, the feedback capture and the Codex mock
//! model. The lane IDE talks to them instead of production, and an in-IDE body steers them through the control route
//! `/_lane/<service>/` of each one.
//!
//! A [Service] owns one listener, on loopback for a lane service. One thread accepts, and each connection gets a thread
//! of its own, so an answer that waits holds only its own request. A WebSocket upgrade keeps that thread for its
//! connection. Each service keeps its state behind one mutex. There is no async runtime, because the guest agent pins
//! its crate closure. The control channel of the lane daemon is a [Service] too, on the address that the controller
//! names.

use std::io::Write;
use std::net::{Ipv4Addr, SocketAddr, TcpListener, TcpStream};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use self::http::{Request, RequestError, Response};

use crate::reply::{AgentRefusal, AgentRefusalExt};

pub(crate) mod cloud;
pub(crate) mod codex;
pub(crate) mod control;
pub(crate) mod encoding;
pub(crate) mod feedback;
pub(crate) mod http;
pub(crate) mod registry;
#[cfg(test)]
pub(crate) mod testing;
#[cfg(test)]
mod tests;
pub(crate) mod websocket;

/// The refusal code of a listener that cannot bind its port.
pub(crate) const BIND_CODE: &str = "lane_service_bind";

/// Bounds the wait for each read and each write of one connection, so a client that stops halfway frees its thread.
const SOCKET_TIMEOUT: Duration = Duration::from_secs(60);

/// What a service answers, and what it ends when it stops.
pub(crate) trait Handler: Send + Sync + 'static {
    /// The largest request body that the service reads. A larger one gets a 413.
    const MAX_BODY_BYTES: usize = http::MAX_BODY_BYTES;

    /// Answers one request of the IDE. A request of the control route goes to [`Handler::control`].
    fn handle(&self, request: &Request) -> Response;

    /// The control route of the service: its name in `/_lane/<name>/`, and the lane-service token that a control
    /// request carries. A service without a control route answers `None`, and [`Handler::handle`] gets every request.
    fn control_route(&self) -> Option<(&'static str, &str)> {
        None
    }

    /// Answers the request of the control route `route`, the path after `/_lane/<name>/`.
    fn control(&self, request: &Request, _route: &str) -> Response {
        control::unknown_route(request)
    }

    /// Ends every wait of an answer in flight. The service calls it when it stops.
    fn stop(&self) {}
}

/// One service on a port. It stops on drop: the listener closes, and every answer that waits ends.
pub(crate) struct Service<H: Handler> {
    address: SocketAddr,
    handler: Arc<H>,
    stopping: Arc<AtomicBool>,
    acceptor: Option<JoinHandle<()>>,
}

impl<H: Handler> Service<H> {
    /// Binds `127.0.0.1:<port>` and serves the handler that `handler` builds for the bound address. The port 0 binds
    /// any free port. `name` names the threads and a refusal.
    pub(crate) fn start(name: &'static str, port: u16, handler: impl FnOnce(SocketAddr) -> H) -> Result<Self, AgentRefusal> {
        Self::bind(name, SocketAddr::from((Ipv4Addr::LOCALHOST, port)), handler)
    }

    /// Binds `address` and serves the handler that `handler` builds for the bound address. The port 0 binds any free
    /// port. `name` names the threads and a refusal.
    pub(crate) fn bind(name: &'static str, address: SocketAddr, handler: impl FnOnce(SocketAddr) -> H) -> Result<Self, AgentRefusal> {
        let listener = TcpListener::bind(address)
            .map_err(|error| AgentRefusal::failure(BIND_CODE, format!("the lane {name} cannot bind {address}: {error}")))?;
        let address = listener
            .local_addr()
            .map_err(|error| AgentRefusal::failure(BIND_CODE, format!("the lane {name} has no bound address: {error}")))?;
        let handler = Arc::new(handler(address));
        let stopping = Arc::new(AtomicBool::new(false));
        let acceptor = {
            let (handler, stopping) = (handler.clone(), stopping.clone());
            thread::Builder::new()
                .name(format!("lane-{name}-accept"))
                .spawn(move || accept(name, &listener, &handler, &stopping))
                .map_err(|error| AgentRefusal::failure(BIND_CODE, format!("the lane {name} cannot start its thread: {error}")))?
        };
        Ok(Self {
            address,
            handler,
            stopping,
            acceptor: Some(acceptor),
        })
    }

    /// The bound address.
    pub(crate) const fn address(&self) -> SocketAddr {
        self.address
    }

    /// The base URL of the service, `http://127.0.0.1:<port>`.
    pub(crate) fn url(&self) -> String {
        format!("http://{}", self.address)
    }

    /// The handler, for what the service states about itself, such as the properties of the IDE launch.
    pub(crate) fn handler(&self) -> &H {
        &self.handler
    }
}

impl<H: Handler> Drop for Service<H> {
    fn drop(&mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        self.handler.stop();
        // Wakes the accept loop so it sees the flag. A listener on every interface also listens on loopback.
        let mut wake = self.address;
        if wake.ip().is_unspecified() {
            wake.set_ip(Ipv4Addr::LOCALHOST.into());
        }
        let _ = TcpStream::connect_timeout(&wake, Duration::from_secs(1));
        if let Some(acceptor) = self.acceptor.take() {
            let _ = acceptor.join();
        }
    }
}

fn accept<H: Handler>(name: &'static str, listener: &TcpListener, handler: &Arc<H>, stopping: &AtomicBool) {
    for stream in listener.incoming() {
        if stopping.load(Ordering::SeqCst) {
            return;
        }
        let Ok(stream) = stream else { continue };
        let handler = handler.clone();
        // A connection without a thread gets no answer, and its client sees the end of the connection.
        let _ = thread::Builder::new()
            .name(format!("lane-{name}-connection"))
            .spawn(move || serve(stream, &*handler));
    }
}

/// Answers `request`: a request of the control route with [`Handler::control`] or its refusal, and any other request
/// with [`Handler::handle`].
fn answer<H: Handler>(handler: &H, request: &Request) -> Response {
    match handler
        .control_route()
        .and_then(|(name, token)| control::route(request, name, token))
    {
        Some(Ok(route)) => handler.control(request, route),
        Some(Err(refusal)) => refusal,
        None => handler.handle(request),
    }
}

fn serve<H: Handler>(mut stream: TcpStream, handler: &H) {
    let _ = stream.set_read_timeout(Some(SOCKET_TIMEOUT));
    let _ = stream.set_write_timeout(Some(SOCKET_TIMEOUT));
    let (response, head_only) = match http::read_request(&stream, H::MAX_BODY_BYTES) {
        Ok(request) => (answer(handler, &request), request.method == "HEAD"),
        Err(RequestError::Closed) => return,
        Err(RequestError::Malformed(message)) => (control::refuse(400, "bad_request", &message), false),
        Err(RequestError::TooLarge) => (
            control::refuse(
                413,
                "body_too_large",
                &format!("a request body is at most {} bytes", H::MAX_BODY_BYTES),
            ),
            false,
        ),
    };
    if let Ok(Some(upgraded)) = http::write_response(&mut stream, response, head_only) {
        upgraded(stream);
        return;
    }
    let _ = stream.flush();
    let _ = stream.shutdown(std::net::Shutdown::Both);
}
