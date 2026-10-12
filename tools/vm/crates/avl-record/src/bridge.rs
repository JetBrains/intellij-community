//! The recorder's client of the in-IDE trace routes: the facts, the Swing tree, a painted frame and the IDE's own
//! spans ([avl_trace::bridge]).
//!
//! Loopback only, the launch's token in the request header, and a short bound on every call. A snapshot is taken
//! while the lane waits for its ack, so a route that hangs would stall the scenario it is only meant to watch; every
//! call therefore takes a deadline, which the recorder puts well inside the lane's own. The time left of it goes to
//! the IDE too, in [WITHIN_HEADER], so the IDE does not start a read the recorder no longer waits for.

use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Instant;

use anyhow::{Context, anyhow, bail};
use avl_trace::bridge::{
    FACTS_ROUTE, FLUSHED_SPANS_ROUTE, Facts, IdeSpans, PAINT_CONTENT_TYPE, PAINT_ROUTE, SPANS_ROUTE, TOKEN_HEADER, TREE_ROUTE, Tree,
    WITHIN_HEADER, decode_facts, decode_ide_spans, decode_tree,
};

#[cfg(test)]
mod tests;

/// Bounds what one answer may hold. A tree of a busy IDE is a few hundred kilobytes and a painted 4K screen a few
/// megabytes, so an answer past this is a bridge gone wrong rather than a big screen.
const MAX_BODY_BYTES: u64 = 64 << 20;

/// Bounds the status line and the headers of one answer, which the IDE keeps to four short lines.
const MAX_HEAD_BYTES: u64 = 64 << 10;

const JSON: &str = "application/json";

/// The base URL of a bridge on this machine.
pub(crate) fn url(port: u16) -> String {
    format!("http://127.0.0.1:{port}")
}

/// Asks one IDE's bridge, over one connection per call.
///
/// It is an HTTP/1.1 GET written by hand, because the recorder is in the runfiles of every UI lane, and each crate of
/// an HTTP client in its closure that changes reruns every lane.
#[derive(Clone)]
pub(crate) struct Client {
    address: SocketAddr,
    token: String,
}

/// What a route answered: the status, the content type, and the body up to one byte past [MAX_BODY_BYTES].
struct Answer {
    status: u16,
    content_type: String,
    body: Vec<u8>,
}

impl Client {
    /// A client of the bridge at `base`, `http://<ip>:<port>` such as [url] of the hello's port.
    ///
    /// It connects to that address and never to a proxy. The bridge is loopback-only by construction, and a lane that
    /// inherits an `HTTP_PROXY` must not route the IDE's own tree through it.
    pub(crate) fn new(base: &str, token: &str) -> anyhow::Result<Self> {
        let address = base
            .strip_prefix("http://")
            .and_then(|authority| authority.trim_end_matches('/').parse::<SocketAddr>().ok())
            .with_context(|| format!("the bridge URL {base:?} is not http://<ip>:<port>"))?;
        if token.bytes().any(|byte| byte.is_ascii_control()) {
            bail!("the bridge token holds a control character, which no request header can carry");
        }
        Ok(Self {
            address,
            token: token.to_owned(),
        })
    }

    /// Reads the facts route.
    pub(crate) fn facts(&self, deadline: Instant) -> anyhow::Result<Facts> {
        let body = self.get(FACTS_ROUTE, JSON, deadline)?;
        Ok(decode_facts(&body)?)
    }

    /// Reads the tree route and answers both the document, which is what a snapshot keeps verbatim, and its
    /// decoded form, which is where the drained inputs are read from.
    pub(crate) fn tree(&self, deadline: Instant) -> anyhow::Result<(Vec<u8>, Tree)> {
        let body = self.get(TREE_ROUTE, JSON, deadline)?;
        let tree = decode_tree(&body)?;
        Ok((body, tree))
    }

    /// Reads the spans the IDE ended since the last read of either spans route. `flushed` makes the IDE export its
    /// queued spans first, which can take seconds, so a snapshot asks without it.
    pub(crate) fn spans(&self, flushed: bool, deadline: Instant) -> anyhow::Result<IdeSpans> {
        let route = if flushed { FLUSHED_SPANS_ROUTE } else { SPANS_ROUTE };
        let body = self.get(route, JSON, deadline)?;
        Ok(decode_ide_spans(&body)?)
    }

    /// Reads the paint route's PNG.
    pub(crate) fn paint(&self, deadline: Instant) -> anyhow::Result<Vec<u8>> {
        self.get(PAINT_ROUTE, PAINT_CONTENT_TYPE, deadline)
    }

    fn get(&self, route: &str, content_type: &str, deadline: Instant) -> anyhow::Result<Vec<u8>> {
        let answer = self
            .exchange(route, content_type, deadline)
            .map_err(|error| anyhow!("GET {route}: {error:#}"))?;
        let Answer {
            status,
            content_type: answered,
            body,
        } = answer;
        if status != 200 {
            let excerpt: String = String::from_utf8_lossy(&body).trim().chars().take(200).collect();
            bail!("GET {route} answered HTTP {status}: {excerpt}");
        }
        if body.len() as u64 > MAX_BODY_BYTES {
            bail!("GET {route} answered more than {MAX_BODY_BYTES} bytes");
        }
        let media_type = answered.split(';').next().unwrap_or_default().trim();
        if !media_type.eq_ignore_ascii_case(content_type) {
            bail!("GET {route} answered the content type {answered:?}, not {content_type}");
        }
        if body.is_empty() {
            bail!("GET {route} answered an empty body");
        }
        Ok(body)
    }

    /// Sends the request and reads the answer. The IDE always sends `Content-Length`, and it closes the connection
    /// after an answer to a request with `Connection: close`, so a body without a length ends at EOF.
    fn exchange(&self, route: &str, content_type: &str, deadline: Instant) -> anyhow::Result<Answer> {
        let within = deadline.saturating_duration_since(Instant::now());
        if within.is_zero() {
            return Err(timed_out().into());
        }
        let mut stream =
            TcpStream::connect_timeout(&self.address, within).with_context(|| format!("cannot connect to {}", self.address))?;
        stream.set_write_timeout(Some(within))?;
        let request = format!(
            "GET {route} HTTP/1.1\r\nHost: {}\r\n{TOKEN_HEADER}: {}\r\nAccept: {content_type}\r\n{WITHIN_HEADER}: {}\r\nConnection: close\r\n\r\n",
            self.address,
            self.token,
            within.as_millis()
        );
        stream.write_all(request.as_bytes())?;
        let mut reader = BufReader::new(DeadlineReader { stream, deadline });

        let mut head = (&mut reader).take(MAX_HEAD_BYTES);
        let status_line = read_head_line(&mut head)?;
        let mut parts = status_line.splitn(3, ' ');
        let status = match (parts.next(), parts.next().map(str::parse::<u16>)) {
            (Some(version), Some(Ok(status))) if version.starts_with("HTTP/1.") => status,
            _ => bail!("the bridge answered the malformed status line {status_line:?}"),
        };
        let (mut answered, mut length) = (String::new(), None);
        loop {
            let line = read_head_line(&mut head)?;
            if line.is_empty() {
                break;
            }
            let (name, value) = line
                .split_once(':')
                .with_context(|| format!("the bridge answered the malformed header line {line:?}"))?;
            let value = value.trim();
            if name.eq_ignore_ascii_case("content-type") {
                value.clone_into(&mut answered);
            } else if name.eq_ignore_ascii_case("content-length") {
                let parsed = value.parse::<u64>();
                length = Some(parsed.with_context(|| format!("the bridge answered Content-Length {value:?}"))?);
            } else if name.eq_ignore_ascii_case("transfer-encoding") {
                bail!("the bridge answered Transfer-Encoding {value}, which the recorder does not read");
            }
        }

        let limit = length.map_or(MAX_BODY_BYTES + 1, |length| length.min(MAX_BODY_BYTES + 1));
        let mut body = Vec::new();
        reader.take(limit).read_to_end(&mut body)?;
        if let Some(length) = length
            && (body.len() as u64) < limit
        {
            bail!("the answer ended after {} of its {length} bytes", body.len());
        }
        Ok(Answer {
            status,
            content_type: answered,
            body,
        })
    }
}

/// One line of an answer's head, without its line end.
fn read_head_line(reader: &mut impl BufRead) -> anyhow::Result<String> {
    let mut line = Vec::new();
    reader.read_until(b'\n', &mut line)?;
    if line.pop() != Some(b'\n') {
        bail!("the answer's head ended early or passed {MAX_HEAD_BYTES} bytes");
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
            return Err(timed_out());
        }
        self.stream.set_read_timeout(Some(left))?;
        self.stream.read(buffer).map_err(|error| match error.kind() {
            // A read timeout is `WouldBlock` on Unix and `TimedOut` on Windows.
            io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut => timed_out(),
            _ => error,
        })
    }
}

fn timed_out() -> io::Error {
    io::Error::new(io::ErrorKind::TimedOut, "the deadline passed before the answer arrived")
}
