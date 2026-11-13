use std::sync::atomic::{AtomicUsize, Ordering};

use super::*;

const BANNER: &[u8] = b"SSH-2.0-OpenSSH_9.9\r\n";

/// A guest whose sshd sends its banner and then echoes what it reads.
fn echoing_sshd() -> GuestStream {
    let (host, mut guest) = tokio::io::duplex(1024);
    tokio::spawn(async move {
        guest.write_all(BANNER).await.unwrap();
        let mut buffer = [0u8; 64];
        while let Ok(read) = guest.read(&mut buffer).await {
            if read == 0 || guest.write_all(&buffer[..read]).await.is_err() {
                break;
            }
        }
    });
    GuestStream::from_io(host)
}

/// A SOCKS5 client as Packer's: no authentication, a `CONNECT` to an IPv4 address and `port`. Answers the reply code.
async fn connect(relay: &SshRelay, port: u16) -> (TcpStream, u8) {
    let mut client = TcpStream::connect((Ipv4Addr::LOCALHOST, relay.port())).await.unwrap();
    client.write_all(&[5, 1, 0]).await.unwrap();
    let mut method = [0u8; 2];
    client.read_exact(&mut method).await.unwrap();
    assert_eq!(method, [5, 0], "the proxy takes a client without authentication");
    let [high, low] = port.to_be_bytes();
    client.write_all(&[5, 1, 0, 1, 192, 168, 64, 7, high, low]).await.unwrap();
    let mut answer = [0u8; 10];
    client.read_exact(&mut answer).await.unwrap();
    (client, answer[1])
}

#[tokio::test]
async fn a_connect_reaches_sshd_and_carries_its_banner_and_bytes_both_ways() {
    let relay = SshRelay::start(|| async { Ok(echoing_sshd()) }).await.unwrap();

    let (mut client, code) = connect(&relay, GUEST_SSH_PORT).await;

    assert_eq!(code, Reply::Succeeded as u8);
    let mut banner = vec![0u8; BANNER.len()];
    client.read_exact(&mut banner).await.unwrap();
    assert_eq!(banner, BANNER);
    client.write_all(b"client hello").await.unwrap();
    let mut echoed = [0u8; 12];
    client.read_exact(&mut echoed).await.unwrap();
    assert_eq!(&echoed, b"client hello");
}

#[tokio::test]
async fn a_guest_that_ends_before_sshd_spoke_is_a_refused_connect_and_the_next_connect_tries_again() {
    let opened = Arc::new(AtomicUsize::new(0));
    let counter = Arc::clone(&opened);
    let relay = SshRelay::start(move || {
        let attempt = counter.fetch_add(1, Ordering::SeqCst);
        async move {
            // The first exec child ends without a byte, as one does before the guest agent or sshd is up.
            if attempt == 0 {
                Ok(GuestStream::refused("tart exec: the guest agent is not running"))
            } else {
                Ok(echoing_sshd())
            }
        }
    })
    .await
    .unwrap();

    assert_eq!(connect(&relay, GUEST_SSH_PORT).await.1, Reply::ConnectionRefused as u8);
    assert_eq!(connect(&relay, GUEST_SSH_PORT).await.1, Reply::Succeeded as u8);
    assert_eq!(opened.load(Ordering::SeqCst), 2);
}

#[tokio::test]
async fn an_exec_channel_that_cannot_start_is_a_refused_connect() {
    let relay = SshRelay::start(|| async { Err(io::Error::other("tart is not installed")) })
        .await
        .unwrap();

    assert_eq!(connect(&relay, GUEST_SSH_PORT).await.1, Reply::ConnectionRefused as u8);
}

#[tokio::test]
async fn a_port_other_than_sshd_opens_nothing() {
    let opened = Arc::new(AtomicUsize::new(0));
    let counter = Arc::clone(&opened);
    let relay = SshRelay::start(move || {
        counter.fetch_add(1, Ordering::SeqCst);
        async { Ok(echoing_sshd()) }
    })
    .await
    .unwrap();

    assert_eq!(connect(&relay, 5900).await.1, Reply::NotAllowed as u8);
    assert_eq!(opened.load(Ordering::SeqCst), 0);
}

#[tokio::test]
async fn a_client_that_offers_only_password_authentication_is_told_no_method_is_acceptable() {
    let relay = SshRelay::start(|| async { Ok(echoing_sshd()) }).await.unwrap();
    let mut client = TcpStream::connect((Ipv4Addr::LOCALHOST, relay.port())).await.unwrap();

    client.write_all(&[5, 1, 2]).await.unwrap();

    let mut method = [0u8; 2];
    client.read_exact(&mut method).await.unwrap();
    assert_eq!(method, [5, NO_ACCEPTABLE_METHOD]);
}
