//! What the tests of several modules share: a buffer more than one thread writes, a clock the test sets, and a
//! loopback HTTP server standing in for the IDE's bridge.

use std::io::{self, BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::SystemTime;

use crate::{Clock, from_unix_ms, lock};

/// A buffer more than one thread may write, for the diagnostics and the acks.
#[derive(Clone, Default)]
pub(crate) struct SharedBuffer(Arc<Mutex<Vec<u8>>>);

impl SharedBuffer {
    pub(crate) fn text(&self) -> String {
        String::from_utf8_lossy(&lock(&self.0)).into_owned()
    }
}

impl Write for SharedBuffer {
    fn write(&mut self, chunk: &[u8]) -> io::Result<usize> {
        lock(&self.0).extend_from_slice(chunk);
        Ok(chunk.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

/// A clock the test sets. The recorder, the fake bridge and the fake screen all read it, from their own threads.
#[derive(Clone, Default)]
pub(crate) struct ScriptedClock(Arc<AtomicI64>);

impl ScriptedClock {
    pub(crate) fn at_ms(ms: i64) -> Self {
        let clock = Self::default();
        clock.set_ms(ms);
        clock
    }

    pub(crate) fn set_ms(&self, ms: i64) {
        self.0.store(ms, Ordering::SeqCst);
    }

    pub(crate) fn ms(&self) -> i64 {
        self.0.load(Ordering::SeqCst)
    }

    pub(crate) fn now(&self) -> SystemTime {
        from_unix_ms(self.ms())
    }

    pub(crate) fn clock(&self) -> Clock {
        let clock = self.clone();
        Arc::new(move || clock.now())
    }

    pub(crate) fn advance_ms(&self, ms: i64) -> SystemTime {
        self.set_ms(self.ms() + ms);
        self.now()
    }
}

/// One request the fake server read: its path and headers.
pub(crate) struct Request {
    pub(crate) path: String,
    headers: Vec<(String, String)>,
}

impl Request {
    /// A header's value, by its case-insensitive name.
    pub(crate) fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }
}

/// What the fake server answers.
pub(crate) struct Response {
    pub(crate) status: u16,
    pub(crate) content_type: String,
    pub(crate) body: Vec<u8>,
}

impl Response {
    pub(crate) fn ok(content_type: &str, body: impl Into<Vec<u8>>) -> Self {
        Self {
            status: 200,
            content_type: content_type.to_owned(),
            body: body.into(),
        }
    }

    pub(crate) fn error(status: u16, message: &str) -> Self {
        Self {
            status,
            content_type: "text/plain".to_owned(),
            body: message.as_bytes().to_vec(),
        }
    }
}

type Handler = dyn Fn(&Request) -> Response + Send + Sync;

/// A loopback HTTP/1.1 server that answers every GET with its handler, one connection per request. Each connection
/// is served on a thread of its own, so a handler that hangs holds only its own request.
pub(crate) struct FakeHttp {
    url: String,
    stopping: Arc<AtomicBool>,
}

impl FakeHttp {
    pub(crate) fn start(handler: impl Fn(&Request) -> Response + Send + Sync + 'static) -> Self {
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

    pub(crate) fn url(&self) -> &str {
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
    let mut reader = BufReader::new(match stream.try_clone() {
        Ok(stream) => stream,
        Err(_) => return,
    });
    let mut line = String::new();
    if reader.read_line(&mut line).is_err() {
        return;
    }
    let path = line.split_whitespace().nth(1).unwrap_or("/").to_owned();
    let mut headers = Vec::new();
    loop {
        let mut header = String::new();
        if reader.read_line(&mut header).is_err() {
            return;
        }
        let header = header.trim_end();
        if header.is_empty() {
            break;
        }
        if let Some((name, value)) = header.split_once(':') {
            headers.push((name.trim().to_owned(), value.trim().to_owned()));
        }
    }
    let response = handler(&Request { path, headers });
    let mut stream = stream;
    let head = format!(
        "HTTP/1.1 {} X\r\nContent-Type: {}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        response.status,
        response.content_type,
        response.body.len()
    );
    let _ = stream.write_all(head.as_bytes()).and_then(|()| stream.write_all(&response.body));
}
