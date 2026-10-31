//! `air-local-app`: the local app of the local launch scenarios of the Air UI lanes.
//!
//! The lane IDE starts it through the launcher of its declared CLI runtime, with no argument. So its command line names
//! no file, and the consent of the IDE can store a grant for it. A native program that is no interpreter is the one kind
//! of local app that the lane IDE itself, `java` with an argument file, cannot stand in for.
//!
//! The binary first writes its ready line `{"pid":<pid>}` on standard output. Then it reads one line on standard input:
//! `{"socket":"<path>","request":{...}}`, the socket of the IDE and one request of the local launch protocol. It sends
//! the request over the local peer handshake v2 itself, because the IDE refuses an opener or an envelope that another
//! process wrote. It writes each reply line of the IDE on standard output, until the IDE closes the connection. A
//! failure is one `ERROR:` line on standard error and the exit code 1. An argument is refused with the exit code 2.

use std::ffi::OsString;
use std::io::{self, BufRead, BufReader, Write};
use std::os::unix::net::UnixStream;
use std::process::ExitCode;

use anyhow::{Context, bail};
use serde_json::{Value, json};

/// The opener of the local peer handshake v2.
const OPENER: &str = r#"{"proto":"local-peer","v":2}"#;

fn main() -> ExitCode {
    ExitCode::from(run(
        std::env::args_os().skip(1),
        &mut io::stdin().lock(),
        &mut io::stdout().lock(),
        &mut io::stderr(),
    ))
}

/// Serves one request with the streams of the process and answers the exit code.
fn run(mut args: impl Iterator<Item = OsString>, input: &mut impl BufRead, output: &mut impl Write, errors: &mut impl Write) -> u8 {
    if let Some(argument) = args.next() {
        let _ = writeln!(errors, "ERROR: air-local-app takes no argument, but got {}", argument.display());
        return 2;
    }
    match serve(input, output) {
        Ok(()) => 0,
        Err(error) => {
            let _ = writeln!(errors, "ERROR: {error:#}");
            1
        }
    }
}

fn serve(input: &mut impl BufRead, output: &mut impl Write) -> anyhow::Result<()> {
    write_line(output, &json!({ "pid": std::process::id() }).to_string()).context("cannot write the ready line")?;
    let order = read_order(input)?;
    let stream = UnixStream::connect(&order.socket).with_context(|| format!("cannot connect to the socket {}", order.socket))?;
    let mut writer = &stream;
    let mut reader = BufReader::new(&stream);
    write_line(&mut writer, OPENER).context("cannot write the opener")?;
    let nonce = read_nonce(&mut reader)?;
    let envelope = json!({ "nonce": nonce, "request": order.request });
    write_line(&mut writer, &envelope.to_string()).context("cannot write the envelope")?;
    for line in reader.lines() {
        let line = line.context("cannot read a reply line of the IDE")?;
        write_line(output, &line).context("cannot write a reply line")?;
    }
    Ok(())
}

/// The socket of the IDE and the request that the standard input names.
struct Order {
    socket: String,
    request: Value,
}

fn read_order(input: &mut impl BufRead) -> anyhow::Result<Order> {
    let mut line = String::new();
    input.read_line(&mut line).context("cannot read the request on standard input")?;
    let order: Value =
        serde_json::from_str(&line).with_context(|| format!("the standard input holds no JSON object: {:?}", line.trim_end()))?;
    let Some(socket) = order.get("socket").and_then(Value::as_str) else {
        bail!("the standard input names no socket: {}", line.trim_end());
    };
    let Some(request) = order.get("request").filter(|request| request.is_object()) else {
        bail!("the standard input names no request object: {}", line.trim_end());
    };
    Ok(Order {
        socket: socket.to_owned(),
        request: request.clone(),
    })
}

/// Reads the greeting of the IDE and answers its nonce.
fn read_nonce(reader: &mut impl BufRead) -> anyhow::Result<String> {
    let mut greeting = String::new();
    reader.read_line(&mut greeting).context("cannot read the greeting of the IDE")?;
    if greeting.is_empty() {
        bail!("the IDE closed the socket before its greeting");
    }
    let nonce = serde_json::from_str::<Value>(&greeting)
        .ok()
        .and_then(|greeting| greeting.get("nonce").and_then(Value::as_str).map(str::to_owned));
    nonce.with_context(|| format!("the IDE answered no greeting: {}", greeting.trim_end()))
}

/// Writes `line` and its line feed with one write, and flushes it.
fn write_line(output: &mut impl Write, line: &str) -> io::Result<()> {
    output.write_all(format!("{line}\n").as_bytes())?;
    output.flush()
}
