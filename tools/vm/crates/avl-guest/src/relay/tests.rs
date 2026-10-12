//! The relay against a real loopback listener that stands in for the UI daemon.

use std::io::{self, PipeReader, Read, Write};
use std::net::{Ipv4Addr, Shutdown, TcpListener, TcpStream};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::thread;
use std::time::Duration;

use nix::libc;
use pretty_assertions::assert_eq;

use super::relay;
use crate::reply::Streams;
use crate::testing::run_agent;

/// What one relay answered: its exit status and the exact bytes of its two output streams.
struct Relayed {
    exit: u8,
    stdout: Vec<u8>,
    stderr: Vec<u8>,
}

/// Runs the relay to `port` with `input` as its standard input, and collects its output in memory.
fn relayed(port: u16, input: PipeReader) -> Relayed {
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit = relay(
        port,
        input,
        &mut Streams {
            stdin: &mut io::empty(),
            stdout: &mut stdout,
            stderr: &mut stderr,
        },
    );
    Relayed { exit, stdout, stderr }
}

/// The stand-in daemon, and the port it listens on.
fn daemon() -> (TcpListener, u16) {
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
    let port = listener.local_addr().unwrap().port();
    (listener, port)
}

/// A loopback port where nothing listens, and the sockets that hold it. While they live, the port refuses a connect,
/// and no other socket of the host receives it from a `bind` to port 0 or as the source port of a `connect`.
///
/// The holder is the client end of a loopback connection, and it binds its port before it connects. The standard
/// library cannot bind a client socket, so the holder starts as a `libc` socket. A dropped listener frees its port for
/// any process of the host. A bound socket that does not listen is not a refusal on macOS, because the kernel drops
/// the SYN.
fn closed_port() -> (u16, impl Sized) {
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
    // SAFETY: socket(2) reads no memory of this process.
    let descriptor = unsafe { libc::socket(libc::AF_INET, libc::SOCK_STREAM, 0) };
    assert!(descriptor >= 0, "{}", io::Error::last_os_error());
    // SAFETY: the descriptor is a new socket that nothing else owns.
    let holder = unsafe { OwnedFd::from_raw_fd(descriptor) };
    let length = libc::socklen_t::try_from(size_of::<libc::sockaddr_in>()).unwrap();
    let source = loopback(0);
    // SAFETY: the pointer and the length describe `source`, which lives for the whole call.
    let bound = unsafe { libc::bind(holder.as_raw_fd(), (&raw const source).cast(), length) };
    assert_eq!(bound, 0, "{}", io::Error::last_os_error());
    let target = loopback(listener.local_addr().unwrap().port());
    // SAFETY: the pointer and the length describe `target`, which lives for the whole call.
    let connected = unsafe { libc::connect(holder.as_raw_fd(), (&raw const target).cast(), length) };
    assert_eq!(connected, 0, "{}", io::Error::last_os_error());
    let holder = TcpStream::from(holder);
    let (accepted, _) = listener.accept().unwrap();
    (holder.local_addr().unwrap().port(), (holder, accepted))
}

/// The socket address `127.0.0.1:<port>`.
fn loopback(port: u16) -> libc::sockaddr_in {
    // SAFETY: `sockaddr_in` is plain data, and all zero bytes are a valid value of it.
    let mut address: libc::sockaddr_in = unsafe { std::mem::zeroed() };
    address.sin_family = libc::sa_family_t::try_from(libc::AF_INET).unwrap();
    address.sin_port = port.to_be();
    address.sin_addr.s_addr = u32::from(Ipv4Addr::LOCALHOST).to_be();
    address
}

/// Accepts the relay's connection. A read that waits too long fails the test rather than hangs it.
fn accept(listener: &TcpListener) -> TcpStream {
    let (socket, _) = listener.accept().unwrap();
    socket.set_read_timeout(Some(Duration::from_secs(30))).unwrap();
    socket
}

/// `length` bytes that are not text and that repeat only every 256 bytes, so a lost or moved chunk shows.
fn payload(length: usize, offset: usize) -> Vec<u8> {
    (0..=u8::MAX).cycle().skip(offset).take(length).collect()
}

/// Both directions arrive byte-exact, each one more than one chunk long.
#[test]
fn relay_copies_bytes_both_ways() {
    let (listener, port) = daemon();
    let request = payload(200 * 1024 + 3, 7);
    let reply = payload(300 * 1024 + 5, 13);
    let sent_reply = reply.clone();
    let daemon = thread::spawn(move || {
        let mut socket = accept(&listener);
        let mut reader = socket.try_clone().unwrap();
        let received = thread::spawn(move || {
            let mut received = Vec::new();
            reader.read_to_end(&mut received).unwrap();
            received
        });
        socket.write_all(&sent_reply).unwrap();
        socket.shutdown(Shutdown::Write).unwrap();
        received.join().unwrap()
    });
    let (input, mut feed) = io::pipe().unwrap();
    let sent_request = request.clone();
    let feeder = thread::spawn(move || feed.write_all(&sent_request).unwrap());

    let relayed = relayed(port, input);

    assert_eq!(relayed.exit, 0, "{}", String::from_utf8_lossy(&relayed.stderr));
    assert!(relayed.stdout == reply, "the reply arrived changed");
    assert!(relayed.stderr.is_empty());
    feeder.join().unwrap();
    assert!(daemon.join().unwrap() == request, "the request arrived changed");
}

/// A port nothing listens on is a refusal with nothing on stdout, so the host never reads it as a reply.
#[test]
fn relay_refused_exits_before_any_output() {
    let (port, _held) = closed_port();

    let answered = run_agent(&["relay", &port.to_string()], b"");

    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert_eq!(answered.stdout, "");
    assert_eq!(answered.code(), "guest_relay_failed");
    assert_eq!(answered.failure()["command"], "relay");
    let message = answered.failure()["error"]["message"].as_str().unwrap().to_owned();
    assert!(message.contains(&format!("127.0.0.1:{port}")), "{message}");
    assert_eq!(answered.stderr.lines().count(), 1, "{}", answered.stderr);
}

/// The end of the input ends only the request. The daemon reads the end, and its reply still comes back.
#[test]
fn relay_half_closes_on_input_eof() {
    let (listener, port) = daemon();
    let (input, mut feed) = io::pipe().unwrap();
    let relay = thread::spawn(move || relayed(port, input));
    let mut socket = accept(&listener);

    feed.write_all(b"GET /events HTTP/1.1\r\n\r\n").unwrap();
    drop(feed);
    let mut request = Vec::new();
    socket.read_to_end(&mut request).unwrap();
    assert_eq!(request, b"GET /events HTTP/1.1\r\n\r\n");
    socket.write_all(b"HTTP/1.1 200 OK\r\n\r\nsent after the input ended").unwrap();
    drop(socket);

    let relayed = relay.join().unwrap();
    assert_eq!(relayed.exit, 0, "{}", String::from_utf8_lossy(&relayed.stderr));
    assert_eq!(relayed.stdout, b"HTTP/1.1 200 OK\r\n\r\nsent after the input ended");
}

/// A host that went away ends the relay at once, while the daemon keeps its connection open.
#[test]
fn relay_ends_when_the_output_is_gone() {
    struct Gone;
    impl Write for Gone {
        fn write(&mut self, _: &[u8]) -> io::Result<usize> {
            Err(io::ErrorKind::BrokenPipe.into())
        }
        fn flush(&mut self) -> io::Result<()> {
            Ok(())
        }
    }
    let (listener, port) = daemon();
    let (input, _feed) = io::pipe().unwrap();
    let relay = thread::spawn(move || {
        let mut stderr = Vec::new();
        let exit = relay(
            port,
            input,
            &mut Streams {
                stdin: &mut io::empty(),
                stdout: &mut Gone,
                stderr: &mut stderr,
            },
        );
        (exit, stderr)
    });
    let mut socket = accept(&listener);
    socket.write_all(b"data: one event\n\n").unwrap();

    let (exit, stderr) = relay.join().unwrap();
    assert_eq!(exit, 0, "{}", String::from_utf8_lossy(&stderr));
    assert!(stderr.is_empty());
    drop(socket);
}

/// A port outside `u16` is a usage refusal, like every argv the agent cannot parse.
#[test]
fn relay_refuses_a_port_out_of_range() {
    let answered = run_agent(&["relay", "65536"], b"");
    assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()));
    assert_eq!(answered.stdout, "");
}
