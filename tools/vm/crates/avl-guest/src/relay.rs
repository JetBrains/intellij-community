//! `relay <port>`: the controller's HTTP connection to the UI daemon, carried over the hypervisor exec channel.
//!
//! The host spawns this verb through `tart exec -i` or `prlctl exec` and speaks HTTP/1.1 on its standard streams.
//! The verb connects to the daemon on the guest loopback and copies bytes both ways. So no host packet goes to the
//! guest network, and macOS Local Network privacy has nothing to refuse.
//!
//! Stdout is the HTTP stream itself, so the verb writes no envelope there. A refusal is the failure envelope on
//! stderr, as for every other verb.

use std::io::{self, Read, Write};
use std::net::{Ipv4Addr, Shutdown, SocketAddr, TcpStream};
use std::thread;

use avl_wire::verb::AgentVerb;

use crate::reply::AgentRefusalExt;
use crate::reply::{self, AgentRefusal, Streams};

#[cfg(test)]
mod tests;

/// The most bytes one read takes before they go on.
const CHUNK: usize = 64 * 1024;

/// Connects to `127.0.0.1:<port>` and bridges `input` and `streams.stdout` to it until the daemon side ends.
///
/// A connection the guest refuses writes nothing on stdout, so the host never reads a partial HTTP stream as
/// the daemon's reply. The exit is [`avl_wire::supervisor::AgentExit::Refused`], because the verb ran and its
/// check refused.
pub(crate) fn relay(port: u16, input: impl Read + Send + 'static, streams: &mut Streams<'_>) -> u8 {
    let address = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
    let socket = match TcpStream::connect(address) {
        Ok(socket) => socket,
        Err(error) => return refuse(streams, format!("cannot connect to {address}: {error}")),
    };
    match bridge(socket, input, streams.stdout) {
        Ok(()) => 0,
        Err(error) => refuse(streams, format!("the relay to {address} failed: {error}")),
    }
}

fn refuse(streams: &mut Streams<'_>, message: String) -> u8 {
    reply::fail(
        streams,
        AgentVerb::Relay.as_str(),
        AgentRefusal::for_verb(AgentVerb::Relay, message),
    )
}

/// Copies `input` into `socket` on a thread of its own, and `socket` into `output` on this thread.
///
/// The end of `input` shuts down only the write half of the socket. The daemon then reads the end of the request,
/// and its reply still comes back. The function returns when the socket ends or when `output` takes no more
/// bytes, because then the host has its reply or has gone away. It does not wait for the input thread: that
/// thread can block on a read that never ends, and the process exits under it.
///
/// Only a failed read of the socket is an error. The host reads the stream as cut short.
pub(crate) fn bridge<W: Write + ?Sized>(socket: TcpStream, input: impl Read + Send + 'static, output: &mut W) -> io::Result<()> {
    // An HTTP request and each server-sent event are small writes, and each one must go at once.
    socket.set_nodelay(true)?;
    let mut to_socket = socket.try_clone()?;
    thread::spawn(move || {
        let mut input = input;
        // A failed read or write ends this direction the way the end of the input does. The daemon side reports
        // its own end on the other direction.
        let _ = copy(&mut input, &mut to_socket);
        let _ = to_socket.shutdown(Shutdown::Write);
    });
    let mut from_socket = socket;
    copy(&mut from_socket, output)
}

/// Copies `from` into `to` until `from` ends or `to` takes no more bytes, and flushes `to` after each chunk.
///
/// Only a failed read is an error. A failed write ends the copy as the end of `from` does, because its only meaning
/// is that the reader went away. The flush after each chunk sends a server-sent event out of the guest when the
/// daemon writes it, not when a buffer is full.
fn copy<R: Read + ?Sized, W: Write + ?Sized>(from: &mut R, to: &mut W) -> io::Result<()> {
    let mut buffer = vec![0; CHUNK];
    loop {
        let read = match from.read(&mut buffer) {
            Ok(0) => return Ok(()),
            Ok(read) => read,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error),
        };
        if to.write_all(&buffer[..read]).and_then(|()| to.flush()).is_err() {
            return Ok(());
        }
    }
}
