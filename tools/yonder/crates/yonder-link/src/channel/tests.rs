use std::net::TcpListener;
use std::thread;

use pretty_assertions::assert_eq;

use super::*;
use crate::proto::{Reply, Request};

const DEADLINE: Duration = Duration::from_secs(5);

/// An agent on a loopback port that accepts one connection, and the directory of its allow list.
struct Agent {
    address: SocketAddr,
    identity: Identity,
    allow: PathBuf,
    _dir: tempfile::TempDir,
    listener: TcpListener,
}

fn agent(clients: &[&Identity]) -> Agent {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let allow = dir.path().join("clients");
    let mut list = AllowList::default();
    for client in clients {
        list.add(client.fingerprint(), "test");
    }
    std::fs::write(&allow, list.text()).expect("the allow list");
    let listener = TcpListener::bind("127.0.0.1:0").expect("a port");
    Agent {
        address: listener.local_addr().expect("the address"),
        identity: Identity::generate().expect("the agent identity"),
        allow,
        _dir: dir,
        listener,
    }
}

impl Agent {
    /// Accepts one connection on a thread and runs `serve` on it. The handle answers the result of the handshake.
    fn serve(&self, serve: impl FnOnce(&mut Channel) + Send + 'static) -> thread::JoinHandle<Result<Fingerprint, String>> {
        let listener = self.listener.try_clone().expect("the listener");
        let identity = self.identity.clone();
        let allow = self.allow.clone();
        thread::spawn(move || {
            let (socket, _) = listener.accept().map_err(|error| error.to_string())?;
            let mut channel = Channel::accept(socket, &identity, allow, DEADLINE).map_err(|error| error.to_string())?;
            let peer = channel.peer();
            serve(&mut channel);
            channel.close();
            Ok(peer)
        })
    }
}

#[test]
fn a_pinned_client_and_an_allowed_client_exchange_messages_and_bytes() {
    let client = Identity::generate().expect("the client identity");
    let agent = agent(&[&client]);
    let served = agent.serve(|channel| {
        let hello: Request = channel.recv().expect("the hello");
        assert_eq!(hello, Request::hello("test"));
        let mut received = Vec::new();
        assert_eq!(channel.recv_stream(&mut received).expect("the bytes"), 200_000);
        assert!(
            received
                .iter()
                .enumerate()
                .all(|(index, byte)| u64::from(*byte) == index as u64 % 251)
        );
        channel.send(&Reply::Packed).expect("the reply");
    });
    let mut channel = Channel::connect(agent.address, agent.identity.fingerprint(), &client, DEADLINE).expect("connected");
    assert_eq!(channel.peer(), agent.identity.fingerprint());
    channel.send(&Request::hello("test")).expect("sent");
    let bytes: Vec<u8> = (0..200_000u32).map(|index| u8::try_from(index % 251).expect("below 251")).collect();
    let mut chunks = 0;
    let sent = channel.send_stream(&mut bytes.as_slice(), &mut |_| chunks += 1).expect("streamed");
    assert_eq!(sent, 200_000);
    assert_eq!(chunks, 200_000_usize.div_ceil(CHUNK));
    assert_eq!(channel.recv::<Reply>().expect("the reply"), Reply::Packed);
    assert_eq!(served.join().expect("the agent thread"), Ok(client.fingerprint()));
}

#[test]
fn a_host_with_another_certificate_is_untrusted() {
    let client = Identity::generate().expect("the client identity");
    let agent = agent(&[&client]);
    let _served = agent.serve(|_| {});
    let impostor = Identity::generate().expect("another identity").fingerprint();
    let refused = Channel::connect(agent.address, impostor, &client, DEADLINE).expect_err("refused");
    assert!(matches!(refused, LinkError::Untrusted(_)), "{refused:?}");
}

#[test]
fn a_client_outside_the_allow_list_gets_no_reply() {
    let allowed = Identity::generate().expect("an allowed identity");
    let stranger = Identity::generate().expect("a stranger");
    let agent = agent(&[&allowed]);
    let served = agent.serve(|_| panic!("the agent served a stranger"));
    // TLS 1.3 ends the handshake of the client before the agent checks its certificate, so the refusal arrives at the
    // first read.
    let outcome = Channel::connect(agent.address, agent.identity.fingerprint(), &stranger, DEADLINE).and_then(|mut channel| {
        channel.send(&Request::hello("test"))?;
        channel.recv::<Reply>()
    });
    assert!(
        matches!(outcome, Err(LinkError::Untrusted(_) | LinkError::Protocol(_))),
        "{outcome:?}"
    );
    let handshake = served.join().expect("the agent thread");
    assert!(handshake.is_err(), "{handshake:?}");
}

#[test]
fn a_port_without_an_agent_is_unreachable_and_a_silent_agent_times_out() {
    let client = Identity::generate().expect("the client identity");
    let closed = TcpListener::bind("127.0.0.1:0").expect("a port").local_addr().expect("the address");
    let refused = Channel::connect(closed, client.fingerprint(), &client, DEADLINE).expect_err("refused");
    assert!(matches!(refused, LinkError::Unreachable(_)), "{refused:?}");

    // A listener that never answers the handshake.
    let silent = TcpListener::bind("127.0.0.1:0").expect("a port");
    let started = std::time::Instant::now();
    let timed_out = Channel::connect(
        silent.local_addr().expect("the address"),
        client.fingerprint(),
        &client,
        Duration::from_millis(300),
    )
    .expect_err("timed out");
    assert!(matches!(timed_out, LinkError::Unreachable(_)), "{timed_out:?}");
    assert!(started.elapsed() < Duration::from_secs(3), "{:?}", started.elapsed());
}

#[test]
fn an_oversized_frame_is_a_protocol_error() {
    let client = Identity::generate().expect("the client identity");
    let agent = agent(&[&client]);
    let served = agent.serve(|channel| {
        let outcome = channel.recv_frame();
        assert!(matches!(outcome, Err(LinkError::Protocol(_))), "{outcome:?}");
    });
    let mut channel = Channel::connect(agent.address, agent.identity.fingerprint(), &client, DEADLINE).expect("connected");
    let mut head = [KIND_BYTES, 0, 0, 0, 0];
    head[1..].copy_from_slice(&u32::try_from(CHUNK + 1).expect("small").to_be_bytes());
    channel.tls.write_all(&head).expect("written");
    channel.tls.flush().expect("flushed");
    served.join().expect("the agent thread").expect("the handshake");
}

/// A read that waits for a silent agent ends at once when another thread closes the channel.
#[test]
fn a_closer_ends_a_waiting_read_from_another_thread() {
    let client = Identity::generate().expect("the client identity");
    let agent = agent(&[&client]);
    let served = agent.serve(|_| thread::sleep(Duration::from_secs(3)));
    let mut channel = Channel::connect(agent.address, agent.identity.fingerprint(), &client, DEADLINE).expect("connected");
    let closer = channel.closer().expect("a closer");
    let started = std::time::Instant::now();
    let closing = thread::spawn(move || {
        thread::sleep(Duration::from_millis(200));
        closer.close();
    });
    let read = channel.recv::<Reply>();
    assert!(read.is_err(), "{read:?}");
    assert!(started.elapsed() < Duration::from_secs(2), "{:?}", started.elapsed());
    closing.join().expect("the closing thread");
    let _ = served.join();
}

/// The handshake and the frames through the `nc` relay, which a client on macOS takes when the Local Network permission
/// refuses its own connect.
#[cfg(target_os = "macos")]
#[test]
fn a_relayed_connection_carries_the_handshake_and_the_frames() {
    let client = Identity::generate().expect("the client identity");
    let agent = agent(&[&client]);
    let served = agent.serve(|channel| {
        let hello: Request = channel.recv().expect("the hello");
        assert_eq!(hello, Request::hello("test"));
        channel.send(&Reply::Packed).expect("the reply");
    });
    let socket = relay::connect(agent.address, DEADLINE).expect("relayed");
    let mut channel = Channel::connect_over(socket, agent.identity.fingerprint(), &client, DEADLINE).expect("connected");
    channel.send(&Request::hello("test")).expect("sent");
    assert_eq!(channel.recv::<Reply>().expect("the reply"), Reply::Packed);
    assert_eq!(served.join().expect("the agent thread"), Ok(client.fingerprint()));
}
