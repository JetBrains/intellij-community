//! The relay of a connection through `/usr/bin/nc` on macOS.
//!
//! macOS refuses a connection to the local network with `EHOSTUNREACH` when the app that is responsible for the process
//! has no Local Network permission. A process that `launchd` starts outside an app, such as a background agent
//! session, cannot get that permission. The system `nc` is exempt. So the client connects `nc` to the agent and talks
//! TLS through a loopback socket whose other end copies to and from `nc`. TLS and the pinned certificates stay end to
//! end: `nc` carries only the encrypted bytes.

use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{Ipv4Addr, Shutdown, SocketAddr, TcpListener, TcpStream};
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex, mpsc};
use std::thread;
use std::time::Duration;

/// The system `nc`.
pub(crate) const NC: &str = "/usr/bin/nc";

/// Whether a failed connect is the refusal of the Local Network permission, which the relay can pass.
pub(crate) fn is_local_network_refusal(error: &io::Error) -> bool {
    cfg!(target_os = "macos") && error.kind() == io::ErrorKind::HostUnreachable && Path::new(NC).exists()
}

/// A loopback socket whose other end is `nc` connected to `address`. Answers when `nc` has connected, or the error that
/// `nc` gave. `deadline` bounds the connect of `nc`.
pub(crate) fn connect(address: SocketAddr, deadline: Duration) -> io::Result<TcpStream> {
    connect_with(NC, address, deadline)
}

pub(crate) fn connect_with(nc: &str, address: SocketAddr, deadline: Duration) -> io::Result<TcpStream> {
    let host = match address {
        SocketAddr::V6(v6) if v6.scope_id() != 0 => format!("{}%{}", v6.ip(), v6.scope_id()),
        _ => address.ip().to_string(),
    };
    let seconds = deadline.as_secs().max(1).to_string();
    let mut child = Command::new(nc)
        .args(["-v", "-G", &seconds, &host, &address.port().to_string()])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|error| io::Error::new(error.kind(), format!("cannot start {nc}: {error}")))?;
    let (Some(stdin), Some(stdout), Some(stderr)) = (child.stdin.take(), child.stdout.take(), child.stderr.take()) else {
        let _ = child.kill();
        let _ = child.wait();
        return Err(io::Error::other(format!("{nc} has no pipes")));
    };
    let child = Arc::new(Mutex::new(child));
    if let Err(error) = wait_for_connect(stderr, deadline) {
        end(&child);
        return Err(error);
    }
    let pair = loopback_pair();
    let (outer, inner) = match pair {
        Ok(pair) => pair,
        Err(error) => {
            end(&child);
            return Err(error);
        }
    };
    let up = inner.try_clone()?;
    let up_child = Arc::clone(&child);
    thread::spawn(move || copy_then_end(up, stdin, &up_child));
    thread::spawn(move || copy_then_end(stdout, inner, &child));
    Ok(outer)
}

/// Reads the stderr of `nc -v` up to its connect line. The rest of the stderr is read and dropped, so `nc` never blocks
/// on a full pipe.
fn wait_for_connect(stderr: impl Read + Send + 'static, deadline: Duration) -> io::Result<()> {
    let (said, heard) = mpsc::channel::<io::Result<()>>();
    thread::spawn(move || {
        let mut lines = BufReader::new(stderr).lines();
        let mut first = String::new();
        let verdict = loop {
            match lines.next() {
                Some(Ok(line)) if line.contains("succeeded") => break Ok(()),
                Some(Ok(line)) => {
                    if first.is_empty() {
                        first = line;
                    }
                }
                Some(Err(error)) => break Err(error),
                None => break Err(io::Error::new(io::ErrorKind::HostUnreachable, format!("nc: {}", first.trim()))),
            }
        };
        let _ = said.send(verdict);
        for _ in lines {}
    });
    // `nc -G` ends its own connect at the deadline; one more second lets it say so.
    heard
        .recv_timeout(deadline + Duration::from_secs(1))
        .unwrap_or_else(|_| Err(io::Error::new(io::ErrorKind::TimedOut, "nc did not connect in time")))
}

/// Two ends of one loopback connection.
fn loopback_pair() -> io::Result<(TcpStream, TcpStream)> {
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))?;
    let outer = TcpStream::connect(listener.local_addr()?)?;
    loop {
        let (inner, from) = listener.accept()?;
        // Another local process can connect to the port between bind and accept; only our own end counts.
        if from == outer.local_addr()? {
            return Ok((outer, inner));
        }
    }
}

/// Copies until one side ends, then ends the relay: the loopback socket and `nc`.
fn copy_then_end(mut from: impl Read, mut to: impl Write + EndOfCopy, child: &Mutex<Child>) {
    let _ = io::copy(&mut from, &mut to);
    to.end();
    end(child);
}

fn end(child: &Mutex<Child>) {
    if let Ok(mut child) = child.lock() {
        let _ = child.kill();
        let _ = child.wait();
    }
}

/// The end of one direction of the relay.
trait EndOfCopy {
    fn end(&mut self);
}

impl EndOfCopy for TcpStream {
    fn end(&mut self) {
        let _ = self.shutdown(Shutdown::Both);
    }
}

impl EndOfCopy for std::process::ChildStdin {
    fn end(&mut self) {
        let _ = self.flush();
    }
}

#[cfg(test)]
mod tests;
