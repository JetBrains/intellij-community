//! The relay of a connection through `/usr/bin/nc` on macOS.
//!
//! macOS refuses a connection to the local network with `EHOSTUNREACH` when the responsible process of the client has
//! no Local Network permission. An app without a team ID, such as a local build of the IDE, gets no permission even
//! when its switch is on. A process that is responsible for itself, such as a `launchd` job, gets none either, unless it
//! is a binary of the system. So the client starts the system `nc` responsible for itself (see the `disclaim` module),
//! connects it to the agent, and talks TLS through a loopback socket whose other end copies to and from `nc`. TLS and
//! the pinned certificates stay end to end: `nc` carries only the encrypted bytes.

use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{IpAddr, Ipv4Addr, Shutdown, SocketAddr, TcpListener, TcpStream};
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex, mpsc};
use std::thread;
use std::time::Duration;

use crate::channel::LinkError;

/// The system `nc`.
pub(crate) const NC: &str = "/usr/bin/nc";

/// Whether a failed connect is the refusal of the Local Network permission, which the relay can pass.
pub(crate) fn is_local_network_refusal(error: &io::Error) -> bool {
    cfg!(target_os = "macos") && error.kind() == io::ErrorKind::HostUnreachable && Path::new(NC).exists()
}

/// Why macOS Local Network privacy can be the cause of `error` on a connect to `address`, or `None`. It can be when the
/// connect failed with `EHOSTUNREACH` on an address of the local network, and the relay cannot pass the refusal.
pub fn local_network_cause(error: &LinkError, address: SocketAddr) -> Option<String> {
    match error {
        LinkError::Unreachable(error) if cfg!(target_os = "macos") => cause(error.kind(), address.ip(), unavailable()),
        _ => None,
    }
}

fn cause(kind: io::ErrorKind, ip: IpAddr, unavailable: Option<String>) -> Option<String> {
    if kind != io::ErrorKind::HostUnreachable || !is_local(ip) {
        return None;
    }
    unavailable.map(|reason| {
        format!(
            "macOS Local Network privacy can refuse {ip}, because {reason}. Then the app that started this process needs the Local Network permission (System Settings, Privacy & Security, Local Network)"
        )
    })
}

/// Why the relay cannot pass a refusal of the Local Network permission, or `None` when it can.
fn unavailable() -> Option<String> {
    if !Path::new(NC).exists() {
        return Some(format!("there is no {NC}"));
    }
    #[cfg(target_os = "macos")]
    let disclaim = crate::disclaim::unavailable();
    #[cfg(not(target_os = "macos"))]
    let disclaim = None::<String>;
    disclaim.map(|reason| format!("{NC} cannot be responsible for itself: {reason}"))
}

/// Whether `ip` is an address of the local network: a private or a link-local one.
const fn is_local(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(v4) => v4.is_private() || v4.is_link_local(),
        IpAddr::V6(v6) => v6.is_unique_local() || v6.is_unicast_link_local(),
    }
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
    let mut command = Command::new(nc);
    command
        .args(["-v", "-G", &seconds, &host, &address.port().to_string()])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    // Without the disclaim, `nc` keeps the responsible process of the client, and `local_network_cause` names why.
    #[cfg(target_os = "macos")]
    let _ = crate::disclaim::disclaim(&mut command);
    let mut child = command
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
    let up = match inner.try_clone() {
        Ok(up) => up,
        Err(error) => {
            end(&child);
            return Err(error);
        }
    };
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
                None => {
                    break Err(io::Error::new(
                        io::ErrorKind::HostUnreachable,
                        format!("nc: {}", first.trim().trim_start_matches("nc: ")),
                    ));
                }
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
