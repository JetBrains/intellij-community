//! The SSH of the Packer image build, over the exec channel of Tart.
//!
//! Packer's communicator connects to the guest address that `tart ip` names, a `192.168.64.*` address on the vmnet
//! bridge. macOS Local Network privacy refuses that address to a host process whose app the user did not grant, with
//! "No route to host", while `tart exec` reaches the same guest over vsock. ADR 0182 moved the lane daemon to the exec
//! channel for this reason, and the image build follows it: the build of 2026-11-08 timed out waiting for SSH while
//! sshd ran in the candidate.
//!
//! So `image build` serves Packer a SOCKS5 proxy on loopback, which the template names as `ssh_proxy_host` and
//! `ssh_proxy_port`. Packer asks the proxy for the guest address and port 22, and each connection runs
//! `tart exec -i <candidate> /usr/bin/nc 127.0.0.1 22`, so the bytes reach the candidate's sshd without the guest
//! network. The address that Packer asks for is not read: the proxy serves the one candidate of this build.
//!
//! The proxy answers success only after sshd sent its first byte. An SSH server speaks first, and before the guest
//! agent and sshd are up the exec child ends without a byte. A success before that byte would turn a guest that is
//! still booting into a failed SSH handshake, and Packer gives up after `ssh_handshake_attempts` of those. A refused
//! connect is what Packer retries until `ssh_timeout`.

use std::future::Future;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::sync::Arc;
use std::time::Duration;

use avl_host_sys::GuestStream;
use tokio::io::{AsyncReadExt, AsyncWriteExt, copy_bidirectional};
use tokio::net::{TcpListener, TcpStream};
use tokio::task::JoinHandle;

/// The guest port that the proxy connects to, the port of sshd.
pub(crate) const GUEST_SSH_PORT: u16 = 22;

/// How long the proxy waits for the first byte of sshd. An exec child of a guest whose agent is up and whose sshd
/// listens delivers the banner in well under a second. A child that delivers nothing in this bound is one more
/// refused connect, and Packer asks again.
const FIRST_BYTE_TIMEOUT: Duration = Duration::from_secs(20);

const SOCKS_VERSION: u8 = 5;
const NO_AUTHENTICATION: u8 = 0;
const NO_ACCEPTABLE_METHOD: u8 = 0xff;
const CONNECT: u8 = 1;
const ADDRESS_IPV4: u8 = 1;
const ADDRESS_DOMAIN: u8 = 3;
const ADDRESS_IPV6: u8 = 4;

/// The reply codes of RFC 1928 that the proxy answers.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
enum Reply {
    Succeeded = 0,
    NotAllowed = 2,
    ConnectionRefused = 5,
    CommandNotSupported = 7,
    AddressTypeNotSupported = 8,
}

/// A SOCKS5 proxy on a loopback port whose every connection is a new stream into the guest's sshd.
pub(crate) struct SshRelay {
    port: u16,
    task: JoinHandle<()>,
}

impl SshRelay {
    /// Binds a free loopback port and serves it until the relay is dropped. `open` starts one stream into the guest
    /// port [`GUEST_SSH_PORT`]. Must run inside a tokio runtime.
    pub(crate) async fn start<F, Fut>(open: F) -> io::Result<Self>
    where
        F: Fn() -> Fut + Send + Sync + 'static,
        Fut: Future<Output = io::Result<GuestStream>> + Send + 'static,
    {
        let listener = TcpListener::bind(SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).await?;
        let port = listener.local_addr()?.port();
        let open = Arc::new(open);
        let task = tokio::spawn(async move {
            loop {
                let Ok((client, _)) = listener.accept().await else {
                    // Out of descriptors, for one: a pause, so a lasting failure does not spin.
                    tokio::time::sleep(Duration::from_millis(100)).await;
                    continue;
                };
                let open = Arc::clone(&open);
                // A failed connection is Packer's to retry, and Packer logs it; the proxy has nothing to add.
                tokio::spawn(async move { drop(serve(client, open.as_ref()).await) });
            }
        });
        Ok(Self { port, task })
    }

    /// The loopback port that Packer connects to.
    pub(crate) const fn port(&self) -> u16 {
        self.port
    }
}

impl Drop for SshRelay {
    /// Stops accepting. A connection that is open keeps its own task, and it ends with its exec child.
    fn drop(&mut self) {
        self.task.abort();
    }
}

/// One SOCKS5 connection: the method negotiation, one `CONNECT`, and then the bytes both ways.
async fn serve<F, Fut>(mut client: TcpStream, open: &F) -> io::Result<()>
where
    F: Fn() -> Fut,
    Fut: Future<Output = io::Result<GuestStream>>,
{
    let mut head = [0u8; 2];
    client.read_exact(&mut head).await?;
    if head[0] != SOCKS_VERSION {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "not a SOCKS5 greeting"));
    }
    let mut methods = vec![0u8; usize::from(head[1])];
    client.read_exact(&mut methods).await?;
    if !methods.contains(&NO_AUTHENTICATION) {
        client.write_all(&[SOCKS_VERSION, NO_ACCEPTABLE_METHOD]).await?;
        return Ok(());
    }
    client.write_all(&[SOCKS_VERSION, NO_AUTHENTICATION]).await?;

    let mut request = [0u8; 4];
    client.read_exact(&mut request).await?;
    let skipped = match request[3] {
        ADDRESS_IPV4 => 4,
        ADDRESS_IPV6 => 16,
        ADDRESS_DOMAIN => usize::from(client.read_u8().await?),
        _ => return reply(&mut client, Reply::AddressTypeNotSupported).await,
    };
    let mut address = vec![0u8; skipped];
    client.read_exact(&mut address).await?;
    let port = client.read_u16().await?;
    if request[0] != SOCKS_VERSION || request[1] != CONNECT {
        return reply(&mut client, Reply::CommandNotSupported).await;
    }
    if port != GUEST_SSH_PORT {
        return reply(&mut client, Reply::NotAllowed).await;
    }

    let Ok(mut guest) = open().await else {
        return reply(&mut client, Reply::ConnectionRefused).await;
    };
    let mut first = [0u8; 512];
    let read = match tokio::time::timeout(FIRST_BYTE_TIMEOUT, guest.read(&mut first)).await {
        Ok(Ok(read)) if read > 0 => read,
        _ => return reply(&mut client, Reply::ConnectionRefused).await,
    };
    reply(&mut client, Reply::Succeeded).await?;
    client.write_all(&first[..read]).await?;
    copy_bidirectional(&mut client, &mut guest).await?;
    Ok(())
}

/// Writes the reply of a `CONNECT`. The bound address is zero: Packer reads it and does not use it.
async fn reply(client: &mut TcpStream, code: Reply) -> io::Result<()> {
    client
        .write_all(&[SOCKS_VERSION, code as u8, 0, ADDRESS_IPV4, 0, 0, 0, 0, 0, 0])
        .await
}

#[cfg(test)]
mod tests;
