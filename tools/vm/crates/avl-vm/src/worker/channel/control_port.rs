//! The exec channel into the testing-ui container: the guest server behind the control port, and the daemon's
//! published port.
//!
//! `POST /v1/execute?arg=…` runs one program as the guest's account, with the request body as its stdin, and answers
//! both outputs and the exit code as frames, with a 200 for exit 0 and a 502 for any other code. The port and the
//! bearer are two files under the testing-ui root ([`control_port`], [`bearer`]), read on every call, so a container
//! the script started again is reached without a restart of this controller. A connect to the daemon's guest port
//! is a plain TCP connection to the host port `start` published it at: no relay process and no tunnel.

use std::sync::Arc;
use std::time::Instant;

use async_trait::async_trait;
use avl_base::clock::millis;
use avl_base::{Config, Exit, Refusal};
use avl_host_sys::{Captured, Channel, Ctx, GuestStream, SpawnOptions};
use http_body_util::{BodyExt, Full};
use hyper::body::Bytes;
use hyper::header::AUTHORIZATION;
use hyper::{Request, StatusCode};
use hyper_util::client::legacy::Client;
use hyper_util::client::legacy::connect::HttpConnector;
use hyper_util::rt::{TokioExecutor, TokioTimer};
use serde_json::json;
use tokio::net::TcpStream;

use crate::daemon::http::error_chain;
use crate::worker::container_linux::{bearer, control_port};
use avl_base::RefusalExt;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// Reaches inside the testing-ui container through the control port of its guest server.
pub(crate) struct ControlPortChannel {
    settings: Arc<Config>,
    worker: String,
    client: Client<HttpConnector, Full<Bytes>>,
}

impl ControlPortChannel {
    pub(crate) fn new(settings: Arc<Config>, worker: &str) -> Self {
        Self {
            settings,
            worker: worker.to_owned(),
            client: Client::builder(TokioExecutor::new()).pool_timer(TokioTimer::new()).build_http(),
        }
    }

    fn unreachable(&self, port: u16, detail: &str) -> Refusal {
        Refusal::new(
            "container_linux_unreachable",
            Exit::UNAVAILABLE,
            format!("the control port 127.0.0.1:{port} of {} did not answer: {detail}", self.worker),
        )
    }

    fn bearer_rejected(&self) -> Refusal {
        Refusal::new(
            "container_linux_bearer_rejected",
            Exit::NO_PERM,
            format!(
                "the control port of {} rejected the bearer under {}; the container was started again outside this \
                 controller, so run pool start",
                self.worker,
                self.settings.container_linux_root.display()
            ),
        )
    }

    fn bad_answer(&self, message: &str) -> Refusal {
        Refusal::new(
            "container_linux_bad_answer",
            Exit::SOFTWARE,
            format!(
                "the control port of {} answered what this controller cannot use: {message}",
                self.worker
            ),
        )
    }
}

#[async_trait]
impl Channel for ControlPortChannel {
    /// `POST /v1/execute` with the argv as `arg` query values and the stdin option as the body. A 200 and a 502 are
    /// the program's own answer, exit 0 and any other code, with the outputs and the code as frames. A program the
    /// guest does not have is `guest_program_missing`. A rejected bearer is `container_linux_bearer_rejected`. A
    /// control port that does not answer is `container_linux_unreachable`. The timeout of the options fires with
    /// their code, as a spawn's does.
    async fn exec(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        let Some(program) = argv.first() else {
            return Err(Refusal::internal("empty guest command"));
        };
        let port = control_port(&self.settings)?;
        let bearer = bearer(&self.settings)?;
        let request = Request::post(exec_uri(port, argv))
            .header(AUTHORIZATION, format!("Bearer {bearer}"))
            .body(Full::new(Bytes::from(options.stdin.clone().unwrap_or_default())))
            .map_err(|error| Refusal::internal(format!("cannot build the exec request: {error}")))?;
        // The program is the guest head here: there is no host argv in front of it.
        ctx.timeline().declare_guest_head(program);
        let started = Instant::now();
        let exchange = async {
            let response = self
                .client
                .request(request)
                .await
                .map_err(|error| self.unreachable(port, &error_chain(&error)))?;
            let (head, body) = response.into_parts();
            let body = body
                .collect()
                .await
                .map_err(|error| self.unreachable(port, &error_chain(&error)))?
                .to_bytes();
            Ok::<_, Refusal>((head.status, body))
        };
        let answered = tokio::select! {
            answered = tokio::time::timeout(options.timeout, exchange) => {
                answered.map_err(|_elapsed| timed_out(program, options)).flatten()
            }
            () = ctx.cancelled() => Err(Refusal::new(
                "worker_interrupted",
                Exit::SOFTWARE,
                format!("a cancellation interrupted {program} in {}", self.worker),
            )),
        };
        ctx.phase().record_subprocess(argv, started.elapsed());
        let (status, body) = answered?;
        match status {
            StatusCode::OK | StatusCode::BAD_GATEWAY => captured(&body).map_err(|message| self.bad_answer(&message)),
            StatusCode::NOT_FOUND => Err(Refusal::new(
                "guest_program_missing",
                Exit::UNAVAILABLE,
                format!("{program} is not in {}: {}", self.worker, text_of(&body)),
            )),
            StatusCode::UNAUTHORIZED => Err(self.bearer_rejected()),
            other => Err(self.bad_answer(&format!("HTTP {} for {program}: {}", other.as_u16(), text_of(&body)))),
        }
    }

    /// A connection to the daemon: `start` published its guest port at the host port of the settings, so this is a
    /// plain TCP connection. A port where nothing listens is a refused stream, as the relay of the other backends
    /// reports it. Any other guest port is not published, and is refused by name.
    async fn connect(&self, _ctx: &Ctx, port: u16) -> Result<GuestStream, Refusal> {
        let guest = self.settings.daemon.port;
        if port != guest {
            return Err(Refusal::new(
                "container_linux_port_not_published",
                Exit::SOFTWARE,
                format!(
                    "127.0.0.1:{port} of {} is not published; start publishes the daemon port {guest} only",
                    self.worker
                ),
            ));
        }
        let host_port = self.settings.daemon_host_port;
        match TcpStream::connect(("127.0.0.1", host_port)).await {
            Ok(stream) => Ok(GuestStream::from_io(stream)),
            Err(error) if error.kind() == std::io::ErrorKind::ConnectionRefused => Ok(GuestStream::refused(format!(
                "nothing listens on 127.0.0.1:{port} in {}: the published port 127.0.0.1:{host_port} refused the connection",
                self.worker
            ))),
            Err(error) => Err(Refusal::new(
                "container_linux_unreachable",
                Exit::UNAVAILABLE,
                format!(
                    "the published port 127.0.0.1:{host_port} of {} did not answer: {error}",
                    self.worker
                ),
            )),
        }
    }

    fn worker(&self) -> &str {
        &self.worker
    }
}

/// The execute route of the control port with one `arg` query value per argv element.
fn exec_uri(port: u16, argv: &[String]) -> String {
    let query: Vec<String> = argv.iter().map(|argument| format!("arg={}", percent_encode(argument))).collect();
    format!("http://127.0.0.1:{port}/v1/execute?{}", query.join("&"))
}

/// One query value with the unreserved characters of RFC 3986 kept and every other byte as `%XX`. A space and a `+`
/// are encoded too, because the server reads the query as a form, where a `+` is a space.
///
/// Written by hand, because a new dependency of this crate changes `Cargo.lock`, and a change of the lock is a
/// refresh of the Bazel crate hub.
fn percent_encode(text: &str) -> String {
    let mut encoded = String::with_capacity(text.len());
    for byte in text.bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'_' | b'~') {
            encoded.push(char::from(byte));
        } else {
            encoded.push_str(&format!("%{byte:02X}"));
        }
    }
    encoded
}

/// The answer of a program that ran: `stream=stdout;bytes=<n>`, the n bytes, `stream=stderr;bytes=<n>`, its bytes,
/// and `exit-code=<code>`, each header on its own line and a newline after the bytes. Nothing is truncated: the body
/// arrived whole. A body of another shape is no answer, and the message says where it broke.
fn captured(body: &[u8]) -> Result<Captured, String> {
    let mut rest = body;
    let stdout = frame(&mut rest, "stdout")?;
    let stderr = frame(&mut rest, "stderr")?;
    let line = next_line(&mut rest)?;
    let exit_code = line
        .strip_prefix("exit-code=")
        .and_then(|value| value.parse::<i32>().ok())
        .ok_or_else(|| format!("`exit-code=<code>` expected, got {line:?}"))?;
    if !rest.is_empty() {
        return Err(format!("{} bytes after the exit code", rest.len()));
    }
    Ok(Captured {
        exit_code,
        stdout: text_of(stdout),
        stderr: text_of(stderr),
        stdout_truncated: false,
        stderr_truncated: false,
    })
}

/// One frame of `stream`: its header line counts the bytes that follow, and a newline ends them.
fn frame<'a>(rest: &mut &'a [u8], stream: &str) -> Result<&'a [u8], String> {
    let line = next_line(rest)?;
    let count = line
        .strip_prefix(format!("stream={stream};bytes=").as_str())
        .and_then(|value| value.parse::<usize>().ok())
        .ok_or_else(|| format!("`stream={stream};bytes=<n>` expected, got {line:?}"))?;
    if rest.len() < count {
        return Err(format!("{count} bytes of {stream} announced, {} left", rest.len()));
    }
    let (bytes, after) = rest.split_at(count);
    *rest = after
        .strip_prefix(b"\n")
        .ok_or_else(|| format!("no newline after the bytes of {stream}"))?;
    Ok(bytes)
}

/// The next header line, without its newline.
fn next_line<'a>(rest: &mut &'a [u8]) -> Result<&'a str, String> {
    let end = rest
        .iter()
        .position(|byte| *byte == b'\n')
        .ok_or_else(|| format!("a header line without its newline: {:?}", String::from_utf8_lossy(rest)))?;
    let (line, after) = rest.split_at(end);
    *rest = after.get(1..).unwrap_or_default();
    std::str::from_utf8(line).map_err(|error| format!("a header line that is not UTF-8: {error}"))
}

fn text_of(body: &[u8]) -> String {
    String::from_utf8_lossy(body).into_owned()
}

/// The refusal of an exec that ran past the timeout of its options: the shape of a timed-out spawn, with the code
/// the options name.
fn timed_out(program: &str, options: &SpawnOptions) -> Refusal {
    let milliseconds = millis(options.timeout);
    Refusal::new(
        options.timeout_code.unwrap_or("subprocess_timeout"),
        Exit::TEMP_FAIL,
        format!("{program} exceeded its {milliseconds} ms timeout"),
    )
    .with_details(json!({ "timeoutMs": milliseconds }))
}
