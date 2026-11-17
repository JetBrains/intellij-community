//! Streamable HTTP of MCP over one blocking HTTP/1.1 client.
//!
//! A plain answer is one JSON body. A `tools/call` answer can be a stream of server-sent events, and the transport
//! reads its events until the response of the request. The client speaks `http` only, because every MCP server of a
//! test listens on loopback. Each request has its own connection with `Connection: close`.
//!
//! The client is written by hand, because the workspace has no blocking HTTP client crate, and the agent needs only
//! the subset above: a body with a length, in chunks, or up to the end of the connection.

use std::collections::BTreeMap;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

use super::Frame;
use crate::lines::read_line;

/// The header that carries the MCP session.
const SESSION_ID_HEADER: &str = "Mcp-Session-Id";
const EVENT_STREAM: &str = "text/event-stream";
/// The longest line of an answer head.
const MAX_HEAD_LINE: u64 = 16 << 10;

/// The connection of a client to one streamable-HTTP server.
pub(super) struct HttpTransport {
    name: String,
    url: String,
    headers: BTreeMap<String, String>,
    timeout: Duration,
    session: Option<String>,
}

impl HttpTransport {
    pub(super) fn new(name: &str, url: &str, headers: &BTreeMap<String, String>, timeout: Duration) -> Self {
        Self {
            name: name.to_owned(),
            url: url.to_owned(),
            headers: headers.clone(),
            timeout,
            session: None,
        }
    }

    /// Sends the request `body` with the id `id`, and answers the response of that id.
    pub(super) fn request(&mut self, id: u64, method: &str, body: &str) -> io::Result<Frame> {
        let answer = self.post(body)?;
        if answer.status != 200 {
            let status = answer.status;
            let text = answer.text()?;
            return Err(io::Error::other(format!(
                "MCP server '{}' answered '{method}' with HTTP {status}: {text}",
                self.name
            )));
        }
        if self.session.is_none() {
            self.session = answer
                .header(SESSION_ID_HEADER)
                .filter(|value| !value.trim().is_empty())
                .map(str::to_owned);
        }
        if answer.header("Content-Type").is_some_and(|kind| kind.starts_with(EVENT_STREAM)) {
            let mut events = Events::new(answer.body);
            while let Some(data) = events.next_data()? {
                let frame = Frame::parse(&self.name, data)?;
                if frame.answers(id) {
                    return Ok(frame);
                }
            }
        } else {
            let frame = Frame::parse(&self.name, answer.text()?)?;
            if frame.answers(id) {
                return Ok(frame);
            }
        }
        Err(io::Error::other(format!(
            "MCP server '{}' answered '{method}' without the response of request {id}",
            self.name
        )))
    }

    /// Sends the `notifications/initialized` `body` after a successful `initialize`.
    pub(super) fn initialized(&self, body: &str) -> io::Result<()> {
        if self.session.is_none() {
            return Err(io::Error::other(format!(
                "MCP server '{}' answered initialize without {SESSION_ID_HEADER}",
                self.name
            )));
        }
        let status = self.post(body)?.status;
        if status / 100 != 2 {
            return Err(io::Error::other(format!(
                "MCP server '{}' rejected notifications/initialized with {status}",
                self.name
            )));
        }
        Ok(())
    }

    /// Whether the server gave a session that the client has not closed.
    pub(super) const fn is_alive(&self) -> bool {
        self.session.is_some()
    }

    /// Ends the MCP session with `DELETE`. A server that closed it already answers 404, which is fine.
    pub(super) fn close(&mut self) {
        let Some(session) = self.session.take() else { return };
        let mut headers = self.headers();
        headers.push((SESSION_ID_HEADER, session.as_str()));
        let _ = send("DELETE", &self.url, &headers, b"", self.timeout);
    }

    fn post(&self, body: &str) -> io::Result<Answer> {
        let mut headers = vec![
            ("Content-Type", "application/json"),
            ("Accept", "application/json, text/event-stream"),
        ];
        headers.extend(self.headers());
        if let Some(session) = &self.session {
            headers.push((SESSION_ID_HEADER, session.as_str()));
        }
        send("POST", &self.url, &headers, body.as_bytes(), self.timeout)
    }

    fn headers(&self) -> Vec<(&str, &str)> {
        self.headers.iter().map(|(name, value)| (name.as_str(), value.as_str())).collect()
    }
}

/// The answer of one request: its status, its headers, and its body.
pub(super) struct Answer {
    pub(super) status: u16,
    headers: Vec<(String, String)>,
    pub(super) body: BufReader<Box<dyn Read>>,
}

impl Answer {
    /// The value of the first header `name`, by its case-insensitive name.
    pub(super) fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(known, _)| known.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }

    /// The whole body as text. A byte that is not UTF-8 becomes the replacement character.
    pub(super) fn text(mut self) -> io::Result<String> {
        let mut bytes = Vec::new();
        self.body.read_to_end(&mut bytes)?;
        Ok(String::from_utf8_lossy(&bytes).into_owned())
    }
}

/// Sends one request to `url`, `http://<authority><path>`, and reads the head of the answer. Each connect, read and
/// write waits at most `timeout`.
pub(super) fn send(method: &str, url: &str, headers: &[(&str, &str)], body: &[u8], timeout: Duration) -> io::Result<Answer> {
    let rest = url
        .strip_prefix("http://")
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, format!("the MCP URL {url} is no http URL")))?;
    let (authority, path) = rest.find('/').map_or((rest, "/"), |slash| rest.split_at(slash));
    let address_text = if authority.contains(':') {
        authority.to_owned()
    } else {
        format!("{authority}:80")
    };
    let address = address_text
        .to_socket_addrs()?
        .next()
        .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, format!("{authority} names no address")))?;
    let stream = TcpStream::connect_timeout(&address, timeout)?;
    stream.set_nodelay(true)?;
    stream.set_read_timeout(Some(timeout))?;
    stream.set_write_timeout(Some(timeout))?;
    let mut head = format!("{method} {path} HTTP/1.1\r\nHost: {authority}\r\n");
    for (name, value) in headers {
        head.push_str(&format!("{name}: {value}\r\n"));
    }
    head.push_str(&format!("Content-Length: {}\r\nConnection: close\r\n\r\n", body.len()));
    // The head and the body go out in one write, so without Nagle they leave in one segment when they fit.
    let mut request = head.into_bytes();
    request.extend_from_slice(body);
    let mut writer = &stream;
    writer.write_all(&request)?;
    writer.flush()?;
    read_answer(BufReader::new(stream), method == "HEAD")
}

/// Reads the head of an answer from `reader`, and frames its body.
pub(super) fn read_answer<R: BufRead + 'static>(mut reader: R, head_only: bool) -> io::Result<Answer> {
    let status_line = head_line(&mut reader)?;
    let status = status_line
        .split(' ')
        .nth(1)
        .and_then(|status| status.parse::<u16>().ok())
        .ok_or_else(|| invalid(&format!("the answer starts with {status_line:?}")))?;
    let mut headers = Vec::new();
    loop {
        let line = head_line(&mut reader)?;
        if line.is_empty() {
            break;
        }
        if let Some((name, value)) = line.split_once(':') {
            headers.push((name.trim().to_owned(), value.trim().to_owned()));
        }
    }
    let header = |name: &str| {
        headers
            .iter()
            .find(|(known, _): &&(String, String)| known.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.clone())
    };
    let body: Box<dyn Read> = if head_only || status / 100 == 1 || status == 204 || status == 304 {
        Box::new(io::empty())
    } else if header("Transfer-Encoding").is_some_and(|coding| coding.eq_ignore_ascii_case("chunked")) {
        Box::new(Chunked::new(reader))
    } else if let Some(length) = header("Content-Length") {
        let length = length
            .parse::<u64>()
            .map_err(|_bad| invalid(&format!("the Content-Length {length:?} is no number")))?;
        Box::new(reader.take(length))
    } else {
        Box::new(reader)
    };
    Ok(Answer {
        status,
        headers,
        body: BufReader::new(body),
    })
}

/// One line of an answer head, without its line end.
fn head_line(reader: &mut impl BufRead) -> io::Result<String> {
    match read_line(&mut reader.take(MAX_HEAD_LINE))? {
        Some(line) => Ok(line),
        None => Err(io::Error::new(io::ErrorKind::UnexpectedEof, "the answer head ended early")),
    }
}

/// The body of an answer in the chunked encoding: each chunk, with its extensions and the trailer dropped.
struct Chunked<R> {
    inner: R,
    left: u64,
    done: bool,
}

impl<R: BufRead> Chunked<R> {
    const fn new(inner: R) -> Self {
        Self {
            inner,
            left: 0,
            done: false,
        }
    }
}

impl<R: BufRead> Read for Chunked<R> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        if self.done || buffer.is_empty() {
            return Ok(0);
        }
        if self.left == 0 {
            let line = head_line(&mut self.inner)?;
            let size = line.split(';').next().unwrap_or_default().trim();
            self.left = u64::from_str_radix(size, 16).map_err(|_bad| invalid(&format!("the chunk size {size:?} is no hex number")))?;
            if self.left == 0 {
                while !head_line(&mut self.inner)?.is_empty() {}
                self.done = true;
                return Ok(0);
            }
        }
        let wanted = usize::try_from(self.left).unwrap_or(usize::MAX).min(buffer.len());
        let count = self.inner.read(&mut buffer[..wanted])?;
        if count == 0 {
            return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "the answer ended inside a chunk"));
        }
        self.left -= count as u64;
        if self.left == 0 && !head_line(&mut self.inner)?.is_empty() {
            return Err(invalid("a chunk does not end with a line end"));
        }
        Ok(count)
    }
}

/// The events of a stream of server-sent events, as the `data` of each.
pub(super) struct Events<R> {
    reader: R,
    ended: bool,
}

impl<R: BufRead> Events<R> {
    pub(super) const fn new(reader: R) -> Self {
        Self { reader, ended: false }
    }

    /// The `data` lines of the next event, joined with line feeds, or `None` at the end of the stream. An event ends
    /// with an empty line or with the stream, and an event without data is skipped.
    pub(super) fn next_data(&mut self) -> io::Result<Option<String>> {
        let mut data: Option<String> = None;
        while !self.ended {
            let Some(line) = read_line(&mut self.reader)? else {
                self.ended = true;
                break;
            };
            if line.is_empty() {
                if data.is_some() {
                    return Ok(data);
                }
                continue;
            }
            let Some(value) = line.strip_prefix("data:") else { continue };
            let value = value.strip_prefix(' ').unwrap_or(value);
            match &mut data {
                Some(data) => {
                    data.push('\n');
                    data.push_str(value);
                }
                None => data = Some(value.to_owned()),
            }
        }
        Ok(data)
    }
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.to_owned())
}

#[cfg(test)]
mod tests;
