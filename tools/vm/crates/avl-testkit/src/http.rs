//! A loopback HTTP/1.1 server that stands in for the IDE's bridge.

use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

/// One request the fake server read: its method, its path, its headers and the body that `Content-Length` names.
pub struct Request {
    pub method: String,
    pub path: String,
    headers: Vec<(String, String)>,
    pub body: Vec<u8>,
}

// jscpd:ignore-start the guest agent has the same lookup, and shipped code never links a testkit
impl Request {
    /// A header's value, by its case-insensitive name.
    pub fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }
}
// jscpd:ignore-end

/// What the fake server answers.
pub struct Response {
    pub status: u16,
    pub content_type: String,
    pub body: Vec<u8>,
}

impl Response {
    pub fn ok(content_type: &str, body: impl Into<Vec<u8>>) -> Self {
        Self {
            status: 200,
            content_type: content_type.to_owned(),
            body: body.into(),
        }
    }

    pub fn error(status: u16, message: &str) -> Self {
        Self {
            status,
            content_type: "text/plain".to_owned(),
            body: message.as_bytes().to_vec(),
        }
    }

    /// An answer with `status` and a JSON body, as the bridge's request endpoint answers each status.
    pub fn json(status: u16, body: impl Into<Vec<u8>>) -> Self {
        Self {
            status,
            content_type: "application/json".to_owned(),
            body: body.into(),
        }
    }
}

type Handler = dyn Fn(&Request) -> Response + Send + Sync;

/// A loopback HTTP/1.1 server that answers every request with its handler, one connection per request. Each
/// connection is served on a thread of its own, so a handler that hangs holds only its own request.
pub struct FakeHttp {
    url: String,
    stopping: Arc<AtomicBool>,
}

impl FakeHttp {
    pub fn start(handler: impl Fn(&Request) -> Response + Send + Sync + 'static) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind a loopback port");
        let url = format!("http://{}", listener.local_addr().expect("a bound address"));
        let stopping = Arc::new(AtomicBool::new(false));
        let handler: Arc<Handler> = Arc::new(handler);
        {
            let stopping = stopping.clone();
            thread::spawn(move || {
                for stream in listener.incoming() {
                    if stopping.load(Ordering::SeqCst) {
                        return;
                    }
                    let Ok(stream) = stream else { continue };
                    let handler = handler.clone();
                    thread::spawn(move || serve(stream, &*handler));
                }
            });
        }
        Self { url, stopping }
    }

    /// The base URL of the server, `http://127.0.0.1:<port>`.
    pub fn url(&self) -> &str {
        &self.url
    }
}

impl Drop for FakeHttp {
    fn drop(&mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        // Wakes the accept loop so it sees the flag.
        let _ = TcpStream::connect(self.url.trim_start_matches("http://"));
    }
}

fn serve(stream: TcpStream, handler: &Handler) {
    let Some(request) = read_request(&stream) else {
        return;
    };
    let response = handler(&request);
    let mut stream = stream;
    let head = format!(
        "HTTP/1.1 {} X\r\nContent-Type: {}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        response.status,
        response.content_type,
        response.body.len()
    );
    let _ = stream.write_all(head.as_bytes()).and_then(|()| stream.write_all(&response.body));
}

/// Reads the head of one request and the body that its `Content-Length` names. A request that breaks off answers
/// `None`.
fn read_request(stream: &TcpStream) -> Option<Request> {
    let mut reader = BufReader::new(stream.try_clone().ok()?);
    let mut line = String::new();
    reader.read_line(&mut line).ok()?;
    let mut parts = line.split_whitespace();
    let method = parts.next().unwrap_or("GET").to_owned();
    let path = parts.next().unwrap_or("/").to_owned();
    let mut headers = Vec::new();
    loop {
        let mut header = String::new();
        reader.read_line(&mut header).ok()?;
        let header = header.trim_end();
        if header.is_empty() {
            break;
        }
        if let Some((name, value)) = header.split_once(':') {
            headers.push((name.trim().to_owned(), value.trim().to_owned()));
        }
    }
    let mut request = Request {
        method,
        path,
        headers,
        body: Vec::new(),
    };
    let length = request.header("Content-Length").and_then(|value| value.parse::<usize>().ok());
    if let Some(length) = length {
        request.body.resize(length, 0);
        reader.read_exact(&mut request.body).ok()?;
    }
    Some(request)
}
