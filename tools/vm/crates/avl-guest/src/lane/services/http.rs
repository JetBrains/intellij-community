//! The HTTP/1.1 exchange of a lane service: one request per connection, and one answer.
//!
//! A request body comes with a `Content-Length` or in the chunked encoding, because the IDE's clients send both. An
//! `Expect: 100-continue` gets the interim answer before the body. Every answer closes the connection. An answer body
//! has a `Content-Length`, except a chunked one, which only the Codex event stream uses.

use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::TcpStream;

#[cfg(test)]
mod tests;

/// Bounds the request line and the headers.
const MAX_HEAD_BYTES: u64 = 64 << 10;

/// Bounds a request body. A feedback submission with its attachments is the largest body a lane IDE sends.
pub(crate) const MAX_BODY_BYTES: usize = 64 << 20;

/// One request: the method, the path and the raw query of the target, the headers and the body.
pub(crate) struct Request {
    pub method: String,
    pub path: String,
    pub query: Option<String>,
    headers: Vec<(String, String)>,
    pub body: Vec<u8>,
}

impl Request {
    /// A header's value, by its case-insensitive name.
    pub(crate) fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }

    /// The request target as the client sent it: the path and the query.
    pub(crate) fn target(&self) -> String {
        match &self.query {
            Some(query) => format!("{}?{query}", self.path),
            None => self.path.clone(),
        }
    }

    /// The decoded value of the first query parameter `name`.
    pub(crate) fn query_param(&self, name: &str) -> Option<String> {
        form(self.query.as_deref().unwrap_or_default())
            .into_iter()
            .find(|(key, _)| key == name)
            .map(|(_, value)| value)
    }

    /// The body as text. A byte that is not UTF-8 becomes the replacement character.
    pub(crate) fn body_text(&self) -> String {
        String::from_utf8_lossy(&self.body).into_owned()
    }
}

/// What an answer sends after its head.
pub(crate) enum Body {
    /// The whole body, with its length.
    Full(Vec<u8>),
    /// A head that announces `announced` bytes, then only `sent`, then the end of the connection.
    Truncated { announced: usize, sent: Vec<u8> },
    /// The body as one chunk of the chunked encoding, then the last chunk.
    Chunked(Vec<u8>),
}

/// One answer: the status, the content type, the other headers and the body.
pub(crate) struct Response {
    pub status: u16,
    content_type: Option<String>,
    headers: Vec<(String, String)>,
    pub body: Body,
}

impl Response {
    /// An answer with `status` and an empty body without a content type.
    pub(crate) const fn empty(status: u16) -> Self {
        Self {
            status,
            content_type: None,
            headers: Vec::new(),
            body: Body::Full(Vec::new()),
        }
    }

    /// An answer with `status` and `body` of the media type `content_type`.
    pub(crate) fn with_body(status: u16, content_type: &str, body: impl Into<Vec<u8>>) -> Self {
        Self {
            status,
            content_type: Some(content_type.to_owned()),
            headers: Vec::new(),
            body: Body::Full(body.into()),
        }
    }

    /// An answer whose head announces `announced` bytes, of which only `sent` follow.
    pub(crate) fn truncated(status: u16, content_type: &str, announced: usize, sent: Vec<u8>) -> Self {
        Self {
            body: Body::Truncated { announced, sent },
            ..Self::with_body(status, content_type, Vec::new())
        }
    }

    /// An answer that sends `body` in the chunked encoding.
    pub(crate) fn chunked(status: u16, content_type: &str, body: Vec<u8>) -> Self {
        Self {
            body: Body::Chunked(body),
            ..Self::with_body(status, content_type, Vec::new())
        }
    }

    /// The same answer with the header `name: value` too.
    #[must_use]
    pub(crate) fn header(mut self, name: &str, value: &str) -> Self {
        self.headers.push((name.to_owned(), value.to_owned()));
        self
    }

    /// The number of body bytes that the answer sends.
    pub(crate) const fn sent_bytes(&self) -> usize {
        match &self.body {
            Body::Full(bytes) | Body::Chunked(bytes) => bytes.len(),
            Body::Truncated { sent, .. } => sent.len(),
        }
    }
}

/// Why a request could not be read.
#[derive(Debug)]
pub(crate) enum RequestError {
    /// The client closed the connection before a whole request, or a read failed.
    Closed,
    /// The request is not HTTP/1.1 that this server reads. It gets a 400.
    Malformed(String),
    /// The body is larger than [MAX_BODY_BYTES]. It gets a 413.
    TooLarge,
}

/// Reads one request from `stream`. It answers an `Expect: 100-continue` with the interim answer before it reads the
/// body.
pub(crate) fn read_request(stream: &TcpStream) -> Result<Request, RequestError> {
    let mut reader = BufReader::new(stream);
    let (method, target, headers) = read_head(&mut reader)?;
    let (path, query) = match target.split_once('?') {
        Some((path, query)) => (path.to_owned(), Some(query.to_owned())),
        None => (target, None),
    };
    let mut request = Request {
        method,
        path,
        query,
        headers,
        body: Vec::new(),
    };
    if request
        .header("Expect")
        .is_some_and(|value| value.eq_ignore_ascii_case("100-continue"))
    {
        let mut writer = stream;
        writer
            .write_all(b"HTTP/1.1 100 Continue\r\n\r\n")
            .map_err(|_broken| RequestError::Closed)?;
    }
    let chunked = request
        .header("Transfer-Encoding")
        .is_some_and(|value| value.to_ascii_lowercase().contains("chunked"));
    request.body = if chunked {
        read_chunked(&mut reader)?
    } else {
        let length = match request.header("Content-Length") {
            Some(value) => value
                .parse::<usize>()
                .map_err(|_invalid| RequestError::Malformed(format!("the Content-Length {value:?} is not a number")))?,
            None => 0,
        };
        if length > MAX_BODY_BYTES {
            return Err(RequestError::TooLarge);
        }
        let mut body = vec![0; length];
        reader.read_exact(&mut body).map_err(|_broken| RequestError::Closed)?;
        body
    };
    Ok(request)
}

type Head = (String, String, Vec<(String, String)>);

fn read_head(reader: &mut impl BufRead) -> Result<Head, RequestError> {
    let mut head = reader.take(MAX_HEAD_BYTES);
    let request_line = read_line(&mut head)?;
    let mut parts = request_line.split(' ');
    let (Some(method), Some(target), Some(version)) = (parts.next(), parts.next(), parts.next()) else {
        return Err(RequestError::Malformed(format!("the request line {request_line:?} is malformed")));
    };
    if method.is_empty() || !target.starts_with('/') || !version.starts_with("HTTP/1.") {
        return Err(RequestError::Malformed(format!("the request line {request_line:?} is malformed")));
    }
    let (method, target) = (method.to_owned(), target.to_owned());
    let mut headers = Vec::new();
    loop {
        let line = read_line(&mut head)?;
        if line.is_empty() {
            return Ok((method, target, headers));
        }
        let Some((name, value)) = line.split_once(':') else {
            return Err(RequestError::Malformed(format!("the header line {line:?} has no colon")));
        };
        headers.push((name.trim().to_owned(), value.trim().to_owned()));
    }
}

/// Reads one line without its line end. A line that does not end before the stream or the bound ends is
/// [RequestError::Closed].
fn read_line(reader: &mut impl BufRead) -> Result<String, RequestError> {
    let mut line = Vec::new();
    reader.read_until(b'\n', &mut line).map_err(|_broken| RequestError::Closed)?;
    if line.pop() != Some(b'\n') {
        return Err(RequestError::Closed);
    }
    if line.last() == Some(&b'\r') {
        line.pop();
    }
    String::from_utf8(line).map_err(|_invalid| RequestError::Malformed("a line of the request is not UTF-8".to_owned()))
}

fn read_chunked(reader: &mut impl BufRead) -> Result<Vec<u8>, RequestError> {
    let mut body = Vec::new();
    loop {
        let line = read_line(&mut reader.take(MAX_HEAD_BYTES))?;
        let size = line.split(';').next().unwrap_or_default().trim();
        let size =
            usize::from_str_radix(size, 16).map_err(|_invalid| RequestError::Malformed(format!("the chunk size {line:?} is not hex")))?;
        if size == 0 {
            break;
        }
        if body.len() + size > MAX_BODY_BYTES {
            return Err(RequestError::TooLarge);
        }
        let start = body.len();
        body.resize(start + size, 0);
        reader.read_exact(&mut body[start..]).map_err(|_broken| RequestError::Closed)?;
        read_line(&mut reader.take(MAX_HEAD_BYTES))?;
    }
    // The trailer ends with an empty line.
    while !read_line(&mut reader.take(MAX_HEAD_BYTES))?.is_empty() {}
    Ok(body)
}

/// Writes `response` to `stream`. A `head_only` answer to a `HEAD` request sends no body.
pub(crate) fn write_response(stream: &mut impl Write, response: &Response, head_only: bool) -> io::Result<()> {
    let mut head = format!("HTTP/1.1 {} {}\r\n", response.status, reason(response.status));
    if let Some(content_type) = &response.content_type {
        head.push_str(&format!("Content-Type: {content_type}\r\n"));
    }
    for (name, value) in &response.headers {
        head.push_str(&format!("{name}: {value}\r\n"));
    }
    match &response.body {
        Body::Full(bytes) => head.push_str(&format!("Content-Length: {}\r\n", bytes.len())),
        Body::Truncated { announced, .. } => head.push_str(&format!("Content-Length: {announced}\r\n")),
        Body::Chunked(_) => head.push_str("Transfer-Encoding: chunked\r\n"),
    }
    head.push_str("Connection: close\r\n\r\n");
    stream.write_all(head.as_bytes())?;
    if !head_only {
        match &response.body {
            Body::Full(bytes) => stream.write_all(bytes)?,
            Body::Truncated { sent, .. } => stream.write_all(sent)?,
            Body::Chunked(bytes) => {
                if !bytes.is_empty() {
                    stream.write_all(format!("{:x}\r\n", bytes.len()).as_bytes())?;
                    stream.write_all(bytes)?;
                    stream.write_all(b"\r\n")?;
                }
                stream.write_all(b"0\r\n\r\n")?;
            }
        }
    }
    stream.flush()
}

/// The reason phrase of `status`.
const fn reason(status: u16) -> &'static str {
    match status {
        200 => "OK",
        204 => "No Content",
        302 => "Found",
        400 => "Bad Request",
        401 => "Unauthorized",
        404 => "Not Found",
        405 => "Method Not Allowed",
        413 => "Content Too Large",
        500 => "Internal Server Error",
        501 => "Not Implemented",
        503 => "Service Unavailable",
        _ => "Status",
    }
}

/// The name and value pairs of the form text `text`, `a=1&b=2`, decoded as [form_decode] decodes them.
pub(crate) fn form(text: &str) -> Vec<(String, String)> {
    text.split('&')
        .filter(|parameter| !parameter.is_empty())
        .map(|parameter| {
            let (name, value) = parameter.split_once('=').unwrap_or((parameter, ""));
            (form_decode(name), form_decode(value))
        })
        .collect()
}

/// Decodes one form component: `+` is a space, and `%XX` is a byte of UTF-8. An escape that is not two hex digits
/// stays as it is.
pub(crate) fn form_decode(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        match bytes[index] {
            b'+' => decoded.push(b' '),
            b'%' => match hex_pair(bytes.get(index + 1..index + 3)) {
                Some(byte) => {
                    decoded.push(byte);
                    index += 2;
                }
                None => decoded.push(b'%'),
            },
            byte => decoded.push(byte),
        }
        index += 1;
    }
    String::from_utf8_lossy(&decoded).into_owned()
}

fn hex_pair(pair: Option<&[u8]>) -> Option<u8> {
    let pair = pair?;
    if !pair.iter().all(u8::is_ascii_hexdigit) {
        return None;
    }
    u8::from_str_radix(std::str::from_utf8(pair).ok()?, 16).ok()
}

/// Encodes one form component as `java.net.URLEncoder` does: a letter, a digit and `.-*_` stay, a space is `+`, and
/// every other byte of UTF-8 is `%XX` in upper case.
pub(crate) fn form_encode(text: &str) -> String {
    let mut encoded = String::with_capacity(text.len());
    for &byte in text.as_bytes() {
        match byte {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'.' | b'-' | b'*' | b'_' => encoded.push(char::from(byte)),
            b' ' => encoded.push('+'),
            _ => encoded.push_str(&format!("%{byte:02X}")),
        }
    }
    encoded
}
