//! One call to the bridge: an HTTP/1.1 request over one loopback connection, and the answer it gets.
//!
//! Every call takes a deadline, and nothing of the call waits past it: the connect, the write and each read wait
//! only for what is left. The time left goes to the IDE too, in [WITHIN_HEADER], so the IDE does not start a read
//! that the caller no longer waits for.

use std::fmt;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Instant;

use avl_trace::bridge::{TOKEN_HEADER, WITHIN_HEADER};
use serde_json::Value;

use crate::BridgeFailure;

#[cfg(test)]
mod tests;

/// Bounds what one answer may hold. A tree of a busy IDE is a few hundred kilobytes and a painted 4K screen a few
/// megabytes, so an answer past this is a bridge gone wrong rather than a big screen.
const MAX_BODY_BYTES: u64 = 64 << 20;

/// Bounds the status line and the headers of one answer, which the IDE keeps to four short lines.
const MAX_HEAD_BYTES: u64 = 64 << 10;

/// Bounds the text of a refusal that an error repeats.
const MAX_EXCERPT_CHARS: usize = 200;

/// The media type of a JSON answer, and of the request of [Client::post_json].
pub const JSON: &str = "application/json";

/// The base URL of a bridge on this machine.
pub fn url(port: u16) -> String {
    format!("http://127.0.0.1:{port}")
}

/// Asks one IDE's bridge, over one connection per call.
///
/// The HTTP/1.1 exchange is written by hand, because the recorder and the guest agent pin their crate closures, and
/// each crate of an HTTP client in them that changes reruns every lane.
#[derive(Clone, Debug)]
pub struct Client {
    address: SocketAddr,
    token: String,
}

impl Client {
    /// A client of the bridge at `base`, `http://<ip>:<port>` such as [url] of the port.
    ///
    /// It connects to that address and never to a proxy. The bridge is loopback-only by construction, and a lane that
    /// inherits an `HTTP_PROXY` must not route the IDE's own state through it.
    pub fn new(base: &str, token: &str) -> Result<Self, Error> {
        let address = base
            .strip_prefix("http://")
            .and_then(|authority| authority.trim_end_matches('/').parse::<SocketAddr>().ok())
            .ok_or_else(|| Error::of_client(ErrorKind::Base(base.to_owned())))?;
        if token.bytes().any(|byte| byte.is_ascii_control()) {
            return Err(Error::of_client(ErrorKind::Token));
        }
        Ok(Self {
            address,
            token: token.to_owned(),
        })
    }

    /// GETs `route` and answers the body of a 200 answer with the media type `accept`. Any other answer, and an empty
    /// body, is an [Error].
    pub fn get(&self, route: &str, accept: &str, deadline: Instant) -> Result<Vec<u8>, Error> {
        let call = Call { method: "GET", route };
        self.exchange(&call, accept, None, deadline)
            .and_then(|answer| answer.accepted(accept))
            .and_then(|body| {
                if body.is_empty() {
                    Err(protocol("the bridge answered an empty body".to_owned()))
                } else {
                    Ok(body)
                }
            })
            .map_err(|kind| call.error(kind))
    }

    /// POSTs the JSON text `request` to `route` and answers the `value` of the answer `{value, failure}`.
    ///
    /// A `failure` in a 200 answer is [ErrorKind::Failed]: the IDE ran the request, and the request failed there. A
    /// `value` that the answer leaves out is null.
    pub fn post_json(&self, route: &str, request: &str, deadline: Instant) -> Result<Value, Error> {
        let call = Call { method: "POST", route };
        self.exchange(&call, JSON, Some(request), deadline)
            .and_then(|answer| answer.accepted(JSON))
            .and_then(|body| outcome(&body))
            .map_err(|kind| call.error(kind))
    }

    /// POSTs the JSON text `request` to a route that answers a JSON document of its own, and answers the status and the
    /// document. The route states a refusal with one of `statuses` in the same document as a success, so such an
    /// answer is not an [Error]. Every other status than 200 and `statuses` is.
    pub fn post_document(&self, route: &str, request: &str, statuses: &[u16], deadline: Instant) -> Result<(u16, Vec<u8>), Error> {
        let call = Call { method: "POST", route };
        self.exchange(&call, JSON, Some(request), deadline)
            .and_then(|answer| answer.documented(JSON, statuses))
            .map_err(|kind| call.error(kind))
    }

    /// Sends the request and reads the answer. The IDE always sends `Content-Length`, and it closes the connection
    /// after an answer to a request with `Connection: close`, so a body without a length ends at EOF.
    fn exchange(&self, call: &Call<'_>, accept: &str, body: Option<&str>, deadline: Instant) -> Result<Answer, ErrorKind> {
        let within = deadline.saturating_duration_since(Instant::now());
        if within.is_zero() {
            return Err(ErrorKind::TimedOut);
        }
        let mut stream = TcpStream::connect_timeout(&self.address, within).map_err(|source| match source.kind() {
            io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock => ErrorKind::TimedOut,
            _ => ErrorKind::Connect {
                address: self.address,
                source,
            },
        })?;
        stream.set_write_timeout(Some(within)).map_err(of_io)?;
        let mut request = format!(
            "{} {} HTTP/1.1\r\nHost: {}\r\n{TOKEN_HEADER}: {}\r\nAccept: {accept}\r\n{WITHIN_HEADER}: {}\r\nConnection: close\r\n",
            call.method,
            call.route,
            self.address,
            self.token,
            within.as_millis()
        );
        if let Some(body) = body {
            request.push_str(&format!("Content-Type: {JSON}\r\nContent-Length: {}\r\n", body.len()));
        }
        request.push_str("\r\n");
        request.push_str(body.unwrap_or_default());
        stream.write_all(request.as_bytes()).map_err(of_io)?;
        let mut reader = BufReader::new(DeadlineReader { stream, deadline });

        let mut head = (&mut reader).take(MAX_HEAD_BYTES);
        let status_line = read_head_line(&mut head)?;
        let mut parts = status_line.splitn(3, ' ');
        let status = match (parts.next(), parts.next().map(str::parse::<u16>)) {
            (Some(version), Some(Ok(status))) if version.starts_with("HTTP/1.") => status,
            _ => return Err(protocol(format!("the bridge answered the malformed status line {status_line:?}"))),
        };
        let (mut content_type, mut length) = (String::new(), None);
        loop {
            let line = read_head_line(&mut head)?;
            if line.is_empty() {
                break;
            }
            let (name, value) = line
                .split_once(':')
                .ok_or_else(|| protocol(format!("the bridge answered the malformed header line {line:?}")))?;
            let value = value.trim();
            if name.eq_ignore_ascii_case("content-type") {
                value.clone_into(&mut content_type);
            } else if name.eq_ignore_ascii_case("content-length") {
                let parsed = value.parse::<u64>();
                length = Some(parsed.map_err(|error| protocol(format!("the bridge answered Content-Length {value:?}: {error}")))?);
            } else if name.eq_ignore_ascii_case("transfer-encoding") {
                return Err(protocol(format!(
                    "the bridge answered Transfer-Encoding {value}, which the client does not read"
                )));
            }
        }

        let limit = length.map_or(MAX_BODY_BYTES + 1, |length| length.min(MAX_BODY_BYTES + 1));
        let mut body = Vec::new();
        reader.take(limit).read_to_end(&mut body).map_err(of_io)?;
        if let Some(length) = length
            && (body.len() as u64) < limit
        {
            return Err(protocol(format!("the answer ended after {} of its {length} bytes", body.len())));
        }
        Ok(Answer {
            status,
            content_type,
            body,
        })
    }
}

/// The method and the route of one call, which every error of the call names.
struct Call<'a> {
    method: &'static str,
    route: &'a str,
}

impl Call<'_> {
    fn error(&self, kind: ErrorKind) -> Error {
        Error::of_call(self.method, self.route, kind)
    }
}

/// What a route answered: the status, the content type, and the body up to one byte past [MAX_BODY_BYTES].
struct Answer {
    status: u16,
    content_type: String,
    body: Vec<u8>,
}

impl Answer {
    /// The body of a 200 answer of the media type `accept`, within [MAX_BODY_BYTES].
    fn accepted(self, accept: &str) -> Result<Vec<u8>, ErrorKind> {
        self.documented(accept, &[]).map(|(_, body)| body)
    }

    /// The status and the body of an answer of the media type `accept`, within [MAX_BODY_BYTES], whose status is 200 or
    /// one of `statuses`. Every other status is [ErrorKind::Rejected], and 401 is [ErrorKind::Unauthorized].
    fn documented(self, accept: &str, statuses: &[u16]) -> Result<(u16, Vec<u8>), ErrorKind> {
        let Self {
            status,
            content_type,
            body,
        } = self;
        match status {
            200 => {}
            401 => return Err(ErrorKind::Unauthorized(Rejection::of(&body))),
            _ if statuses.contains(&status) => {}
            _ => {
                return Err(ErrorKind::Rejected {
                    status,
                    rejection: Rejection::of(&body),
                });
            }
        }
        if body.len() as u64 > MAX_BODY_BYTES {
            return Err(protocol(format!("the bridge answered more than {MAX_BODY_BYTES} bytes")));
        }
        let media_type = content_type.split(';').next().unwrap_or_default().trim();
        if !media_type.eq_ignore_ascii_case(accept) {
            return Err(protocol(format!(
                "the bridge answered the content type {content_type:?}, not {accept}"
            )));
        }
        Ok((status, body))
    }
}

/// The `value` of the answer `{value, failure}` of the request endpoint, or its failure.
fn outcome(body: &[u8]) -> Result<Value, ErrorKind> {
    let document: Value =
        serde_json::from_slice(body).map_err(|error| protocol(format!("the bridge answered a body that is not JSON: {error}")))?;
    let Value::Object(mut fields) = document else {
        return Err(protocol(format!(
            "the bridge answered {document}, not an object {{value, failure}}"
        )));
    };
    match fields.remove("failure") {
        None | Some(Value::Null) => Ok(fields.remove("value").unwrap_or(Value::Null)),
        Some(failure) => Err(ErrorKind::Failed(BridgeFailure::decode(&failure).map_err(protocol)?)),
    }
}

/// What the bridge said when it refused a call: the failure of a JSON body `{failure}`, and an excerpt of the text.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct Rejection {
    /// The failure of the body, when the body is the bridge's answer `{failure}`. The request endpoint answers one
    /// with each refusal. The trace routes answer a text.
    pub failure: Option<BridgeFailure>,
    /// The start of the body as text, trimmed.
    pub excerpt: String,
}

impl Rejection {
    fn of(body: &[u8]) -> Self {
        let failure = serde_json::from_slice::<Value>(body)
            .ok()
            .and_then(|document| document.get("failure").and_then(|failure| BridgeFailure::decode(failure).ok()));
        let excerpt = String::from_utf8_lossy(body).trim().chars().take(MAX_EXCERPT_CHARS).collect();
        Self { failure, excerpt }
    }
}

impl fmt::Display for Rejection {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match &self.failure {
            Some(failure) => write!(formatter, "{failure}"),
            None => formatter.write_str(&self.excerpt),
        }
    }
}

/// Why a call answered nothing the caller can use, with the method and the route of the call.
#[derive(Debug)]
pub struct Error {
    call: Option<String>,
    kind: Box<ErrorKind>,
}

impl Error {
    fn of_client(kind: ErrorKind) -> Self {
        Self {
            call: None,
            kind: Box::new(kind),
        }
    }

    pub(crate) fn of_call(method: &str, route: &str, kind: ErrorKind) -> Self {
        Self {
            call: Some(format!("{method} {route}")),
            kind: Box::new(kind),
        }
    }

    pub fn kind(&self) -> &ErrorKind {
        &self.kind
    }

    /// Whether a later call can get an answer: the connect was refused, the deadline passed, or the bridge unloads.
    /// The IDE may not listen yet, may be busy, or may load the next copy of the bridge. Every other error is final.
    pub fn is_transient(&self) -> bool {
        match self.kind.as_ref() {
            ErrorKind::Connect { source, .. } => source.kind() == io::ErrorKind::ConnectionRefused,
            ErrorKind::TimedOut => true,
            ErrorKind::Rejected { status, .. } => *status == 503,
            ErrorKind::Base(_)
            | ErrorKind::Token
            | ErrorKind::Unauthorized(_)
            | ErrorKind::Failed(_)
            | ErrorKind::Protocol(_)
            | ErrorKind::Io(_) => false,
        }
    }
}

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match &self.call {
            Some(call) => write!(formatter, "{call}: {}", self.kind),
            None => write!(formatter, "{}", self.kind),
        }
    }
}

/// The text of [Error] already holds the text of the I/O error, so the error has no source to repeat it.
impl std::error::Error for Error {}

/// What went wrong in a call, or in the construction of a [Client].
#[derive(Debug)]
pub enum ErrorKind {
    /// The base URL is not `http://<ip>:<port>`.
    Base(String),
    /// The token holds a control character.
    Token,
    /// The connect failed. With `ConnectionRefused`, no IDE listens on the port, not yet or not any more.
    Connect { address: SocketAddr, source: io::Error },
    /// The deadline passed before the answer was whole.
    TimedOut,
    /// The bridge refused the token with HTTP 401. The port belongs to another launch, so a later call gets the same
    /// answer.
    Unauthorized(Rejection),
    /// The bridge answered a status other than 200 and 401. A 503 is a bridge that unloads.
    Rejected { status: u16, rejection: Rejection },
    /// The IDE ran the request, and the request failed there.
    Failed(BridgeFailure),
    /// The answer breaks the protocol: its head, its length, its media type or its document.
    Protocol(String),
    /// Another failure of the socket.
    Io(io::Error),
}

impl fmt::Display for ErrorKind {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Base(base) => write!(formatter, "the bridge URL {base:?} is not http://<ip>:<port>"),
            Self::Token => formatter.write_str("the bridge token holds a control character, which no request header can carry"),
            Self::Connect { address, source } => write!(formatter, "cannot connect to {address}: {source}"),
            Self::TimedOut => formatter.write_str("the deadline passed before the answer arrived"),
            Self::Unauthorized(rejection) => write!(formatter, "the bridge answered HTTP 401: {rejection}"),
            Self::Rejected { status, rejection } => write!(formatter, "the bridge answered HTTP {status}: {rejection}"),
            Self::Failed(failure) => write!(formatter, "the request failed in the IDE: {failure}"),
            Self::Protocol(problem) => formatter.write_str(problem),
            Self::Io(source) => write!(formatter, "{source}"),
        }
    }
}

pub(crate) const fn protocol(problem: String) -> ErrorKind {
    ErrorKind::Protocol(problem)
}

/// A socket error. A timeout of a socket is `WouldBlock` on Unix and `TimedOut` on Windows.
fn of_io(error: io::Error) -> ErrorKind {
    match error.kind() {
        io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut => ErrorKind::TimedOut,
        _ => ErrorKind::Io(error),
    }
}

/// One line of an answer's head, without its line end.
fn read_head_line(reader: &mut impl BufRead) -> Result<String, ErrorKind> {
    let mut line = Vec::new();
    reader.read_until(b'\n', &mut line).map_err(of_io)?;
    if line.pop() != Some(b'\n') {
        return Err(protocol(format!("the answer's head ended early or passed {MAX_HEAD_BYTES} bytes")));
    }
    if line.last() == Some(&b'\r') {
        line.pop();
    }
    Ok(String::from_utf8_lossy(&line).into_owned())
}

/// The socket of one call. Each read waits only for what is left of the call's deadline.
struct DeadlineReader {
    stream: TcpStream,
    deadline: Instant,
}

impl Read for DeadlineReader {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        let left = self.deadline.saturating_duration_since(Instant::now());
        if left.is_zero() {
            return Err(io::ErrorKind::TimedOut.into());
        }
        self.stream.set_read_timeout(Some(left))?;
        self.stream.read(buffer)
    }
}
