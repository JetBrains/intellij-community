#[cfg(target_os = "macos")]
use std::io::{Read, Write};
#[cfg(target_os = "macos")]
use std::net::{Shutdown, TcpListener};
#[cfg(target_os = "macos")]
use std::thread;

use pretty_assertions::assert_eq;

use super::*;

const DEADLINE: Duration = Duration::from_secs(5);

#[test]
fn only_a_host_unreachable_error_on_macos_takes_the_relay() {
    let unreachable = io::Error::from(io::ErrorKind::HostUnreachable);
    assert_eq!(
        is_local_network_refusal(&unreachable),
        cfg!(target_os = "macos") && Path::new(NC).exists()
    );
    assert!(!is_local_network_refusal(&io::Error::from(io::ErrorKind::ConnectionRefused)));
}

#[cfg(target_os = "macos")]
#[test]
fn the_relay_carries_bytes_both_ways_and_ends_with_the_peer() {
    let listener = TcpListener::bind("127.0.0.1:0").expect("a port");
    let address = listener.local_addr().expect("the address");
    let echo = thread::spawn(move || {
        let (mut socket, _) = listener.accept().expect("the relayed connection");
        let mut received = [0u8; 5];
        socket.read_exact(&mut received).expect("the bytes");
        socket.write_all(&received).expect("the echo");
        socket.shutdown(Shutdown::Both).expect("the end");
        received
    });
    let mut relayed = connect(address, DEADLINE).expect("relayed");
    relayed.set_read_timeout(Some(DEADLINE)).expect("a deadline");
    relayed.write_all(b"hello").expect("written");
    let mut back = Vec::new();
    relayed.read_to_end(&mut back).expect("the echo and the end");
    assert_eq!(back, b"hello");
    assert_eq!(&echo.join().expect("the echo thread"), b"hello");
}

#[cfg(target_os = "macos")]
#[test]
fn a_port_without_a_listener_is_an_error_of_nc() {
    let closed = TcpListener::bind("127.0.0.1:0").expect("a port").local_addr().expect("the address");
    let error = connect(closed, DEADLINE).expect_err("refused");
    assert_eq!(error.kind(), io::ErrorKind::HostUnreachable, "{error}");
    assert!(error.to_string().starts_with("nc: "), "{error}");
}

#[test]
fn a_missing_nc_is_an_error_that_names_it() {
    let error = connect_with("/nonexistent/nc", "127.0.0.1:9".parse().expect("an address"), DEADLINE).expect_err("missing");
    assert!(error.to_string().contains("/nonexistent/nc"), "{error}");
}
