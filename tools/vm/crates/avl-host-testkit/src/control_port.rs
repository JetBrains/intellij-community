//! The control port of the testing-ui container, without a container: a loopback HTTP/1.1 server that answers the
//! one route the production channel speaks, with the guest answers of [`FakeGuests`] behind it.
//!
//! `POST /v1/execute?arg=…` runs the argv through the channel of the pool's one worker, so a suite seeds one set of
//! guest answers and reads one call log, whichever channel carried the command. Every request carries the bearer the
//! fake `container.cmd` wrote, or it is refused with a 401.
//!
//! The HTTP is read by hand, because this crate links no HTTP server and the one request shape is fixed.

use std::net::TcpListener as StdTcpListener;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use avl_base::sync::lock;
use avl_host_sys::{Captured, Channel as _, Ctx, SpawnOptions};
use avl_testkit::tartfake::CONTAINER_LINUX_BEARER;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};
use tokio::runtime::Handle;
use tokio::task::JoinHandle;

use crate::channel::FakeGuests;

#[cfg(test)]
mod tests;

/// The most bytes of a request head the server reads before it drops the connection.
const HEAD_LIMIT: usize = 64 * 1024;

/// The timeout a program of the exec route gets, far above what a fake answer takes.
const EXEC_TIMEOUT: Duration = Duration::from_secs(60);

/// The fake control port of one container-linux pool: the port the fake `container.cmd` writes into
/// `container.ctl_port`, and the guests that answer behind it.
pub struct FakeControlPort {
    port: u16,
    guests: Arc<FakeGuests>,
    requests: Arc<Mutex<Vec<String>>>,
    server: JoinHandle<()>,
}

impl Drop for FakeControlPort {
    fn drop(&mut self) {
        self.server.abort();
    }
}

impl FakeControlPort {
    /// Serves on a free loopback port, with every exec answered by the channel of `worker` in `guests`. Needs a
    /// tokio runtime, because the server is a tokio task; a container-linux pool is built inside one.
    pub fn start(worker: &str, guests: Arc<FakeGuests>) -> Self {
        let listener = StdTcpListener::bind("127.0.0.1:0").expect("the loopback accepts a listener");
        listener.set_nonblocking(true).expect("a listener can be non-blocking");
        let port = listener.local_addr().expect("a bound listener has an address").port();
        let requests = Arc::new(Mutex::new(Vec::new()));
        let served = Served {
            worker: worker.to_owned(),
            guests: Arc::clone(&guests),
            requests: Arc::clone(&requests),
        };
        let handle = Handle::try_current().expect("a fake control port starts inside a tokio runtime");
        let server = {
            let _entered = handle.enter();
            let listener = TcpListener::from_std(listener).expect("a listener joins the runtime");
            handle.spawn(async move {
                while let Ok((stream, _)) = listener.accept().await {
                    let served = served.clone();
                    tokio::spawn(serve(stream, served));
                }
            })
        };
        Self {
            port,
            guests,
            requests,
            server,
        }
    }

    /// The TCP port on `127.0.0.1`, which `container.ctl_port` names.
    pub const fn port(&self) -> u16 {
        self.port
    }

    /// The guests that answer the exec route, and record what it was handed.
    pub const fn guests(&self) -> &Arc<FakeGuests> {
        &self.guests
    }

    /// `METHOD target` of every request so far, in order, the refused ones included.
    pub fn requests(&self) -> Vec<String> {
        lock(&self.requests).clone()
    }
}

/// What every connection shares.
#[derive(Clone)]
struct Served {
    worker: String,
    guests: Arc<FakeGuests>,
    requests: Arc<Mutex<Vec<String>>>,
}

/// One request head: the method, the target, and the headers with their names in lower case.
struct Head {
    method: String,
    target: String,
    headers: Vec<(String, String)>,
}

impl Head {
    fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(header, _)| header == name)
            .map(|(_, value)| value.as_str())
    }

    fn content_length(&self) -> usize {
        self.header("content-length").and_then(|value| value.parse().ok()).unwrap_or(0)
    }
}

async fn serve(stream: TcpStream, served: Served) {
    let mut stream = BufReader::new(stream);
    let Some(head) = read_head(&mut stream).await else {
        return;
    };
    lock(&served.requests).push(format!("{} {}", head.method, head.target));
    if head.header("authorization") != Some(format!("Bearer {CONTAINER_LINUX_BEARER}").as_str()) {
        answer(&mut stream, "401 Unauthorized", b"unauthorized\n").await;
        return;
    }
    match (head.method.as_str(), head.target.as_str()) {
        ("POST", target) if target == "/v1/execute" || target.starts_with("/v1/execute?") => {
            exec(&mut stream, &head, &served).await;
        }
        (method, target) => {
            answer(&mut stream, "404 Not Found", format!("no route for {method} {target}\n").as_bytes()).await;
        }
    }
}

/// Reads the request head up to the empty line. `None` when the client went away or the head is over the limit.
async fn read_head(stream: &mut BufReader<TcpStream>) -> Option<Head> {
    let mut bytes = Vec::new();
    loop {
        let start = bytes.len();
        let read = stream.read_until(b'\n', &mut bytes).await.ok()?;
        if read == 0 || bytes.len() > HEAD_LIMIT {
            return None;
        }
        if matches!(&bytes[start..], b"\r\n" | b"\n") {
            break;
        }
    }
    let text = String::from_utf8_lossy(&bytes);
    let mut lines = text.lines();
    let mut words = lines.next()?.split_whitespace();
    let method = words.next()?.to_owned();
    let target = words.next()?.to_owned();
    let headers = lines
        .filter_map(|line| line.split_once(':'))
        .map(|(name, value)| (name.trim().to_ascii_lowercase(), value.trim().to_owned()))
        .collect();
    Some(Head { method, target, headers })
}

/// The execute route: the argv out of the query, the body as stdin, and the answer of the fake guest as frames, a 200
/// for exit 0 and a 502 for any other code. A refusal of the fake answer is a 500 with its message.
async fn exec(stream: &mut BufReader<TcpStream>, head: &Head, served: &Served) {
    let mut body = vec![0; head.content_length()];
    if stream.read_exact(&mut body).await.is_err() {
        return;
    }
    let argv = argv_of(&head.target);
    let options = SpawnOptions {
        stdin: (!body.is_empty()).then_some(body),
        ..SpawnOptions::within(EXEC_TIMEOUT)
    };
    let channel = served.guests.channel(&served.worker);
    match channel.exec(&Ctx::background(), &argv, &options).await {
        Ok(captured) => {
            let status = if captured.exit_code == 0 { "200 OK" } else { "502 Bad Gateway" };
            answer(stream, status, &frames(&captured)).await;
        }
        Err(refusal) => {
            answer(stream, "500 Internal Server Error", format!("{}\n", refusal.message).as_bytes()).await;
        }
    }
}

/// One answer, then the connection is closed: the production client opens a connection per request.
async fn answer(stream: &mut BufReader<TcpStream>, status: &str, body: &[u8]) {
    let text = format!("HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len());
    let mut bytes = text.into_bytes();
    bytes.extend_from_slice(body);
    // A client that went away is its own business: the request log already holds the request.
    let _ = stream.write_all(&bytes).await;
    let _ = stream.shutdown().await;
}

/// The `arg` values of the query, in order, each decoded as a form value.
fn argv_of(target: &str) -> Vec<String> {
    let query = target.split_once('?').map(|(_, query)| query).unwrap_or_default();
    query
        .split('&')
        .filter_map(|pair| pair.strip_prefix("arg="))
        .map(percent_decode)
        .collect()
}

/// A form value: `%XX` is the byte, and `+` is a space, as the guest server reads the query.
fn percent_decode(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        let byte = bytes[index];
        let escaped = (byte == b'%' && index + 2 < bytes.len())
            .then(|| std::str::from_utf8(&bytes[index + 1..index + 3]).ok())
            .flatten()
            .and_then(|hex| u8::from_str_radix(hex, 16).ok());
        if let Some(decoded_byte) = escaped {
            decoded.push(decoded_byte);
            index += 3;
        } else {
            decoded.push(if byte == b'+' { b' ' } else { byte });
            index += 1;
        }
    }
    String::from_utf8_lossy(&decoded).into_owned()
}

/// The answer of a program that ran, as the guest server frames it: `stream=stdout;bytes=<n>`, the bytes,
/// `stream=stderr;bytes=<n>`, the bytes, and `exit-code=<code>`, with a newline after the bytes.
fn frames(captured: &Captured) -> Vec<u8> {
    let mut body = Vec::new();
    for (stream, bytes) in [("stdout", captured.stdout.as_bytes()), ("stderr", captured.stderr.as_bytes())] {
        body.extend_from_slice(format!("stream={stream};bytes={}\n", bytes.len()).as_bytes());
        body.extend_from_slice(bytes);
        body.push(b'\n');
    }
    body.extend_from_slice(format!("exit-code={}\n", captured.exit_code).as_bytes());
    body
}
