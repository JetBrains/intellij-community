//! The transport under the control channel: one relay through a worker's exec channel per HTTP connection.
//!
//! The URI authority names the worker and the daemon's guest port, `http://<worker>:<port>/…`. The connector gives
//! the worker to its resolver, asks that channel for a stream to `127.0.0.1:<port>` inside the guest, and hands the
//! stream to hyper. No host socket touches the guest network, so no host privacy setting or route can take part.

use std::future::Future;
use std::io;
use std::pin::Pin;
use std::sync::Arc;
use std::task::{Context, Poll};
use std::time::Duration;

use crate::worker::worker::ChannelFactory;
use avl_host_sys::{Ctx, GuestStream};
use hyper::Uri;
use hyper_util::client::legacy::connect::{Connected, Connection};
use hyper_util::rt::TokioIo;
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};

#[cfg(test)]
mod tests;

/// Bounds the relay spawn of every connection. The spawn returns when the child process starts, not when the guest
/// connects, so this budget catches only a channel that cannot start a child. A relay that the guest refuses fails on
/// its first read and reads as [`Transport::Connect`](super::Transport::Connect). A relay that hangs before its first
/// byte reads as [`Transport::NoReply`](super::Transport::NoReply) when the route budget ends. The `/run` stream is
/// unaffected, because this budget bounds only the connect phase.
pub(crate) const CONNECT_TIMEOUT: Duration = Duration::from_secs(3);

/// Opens one relay per connection that hyper asks for. The client pools connections per authority, so one worker's
/// requests share a relay, and each worker of a `shard` has a pool of its own.
#[derive(Clone)]
pub(crate) struct RelayConnector {
    resolve: ChannelFactory,
}

impl RelayConnector {
    pub(crate) fn new(resolve: ChannelFactory) -> Self {
        Self { resolve }
    }
}

impl tower_service::Service<Uri> for RelayConnector {
    type Response = TokioIo<RelayStream>;
    type Error = io::Error;
    type Future = Pin<Box<dyn Future<Output = io::Result<Self::Response>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, uri: Uri) -> Self::Future {
        let resolve = Arc::clone(&self.resolve);
        Box::pin(async move {
            let (worker, port) = worker_and_port(&uri)?;
            let channel = resolve(&worker);
            // The relay child outlives this call, and no caller context reaches a connector. The runner registers
            // the child with the interrupt service, so a signal still stops it.
            let ctx = Ctx::background();
            let connected = tokio::time::timeout(CONNECT_TIMEOUT, channel.connect(&ctx, port))
                .await
                .map_err(|_elapsed| {
                    io::Error::new(
                        io::ErrorKind::TimedOut,
                        format!("the relay did not start within {} s", CONNECT_TIMEOUT.as_secs()),
                    )
                })?;
            let stream = connected
                .map_err(|refusal| io::Error::new(io::ErrorKind::ConnectionRefused, format!("{}: {}", refusal.code, refusal.message)))?;
            Ok(TokioIo::new(RelayStream(stream)))
        })
    }
}

/// The worker and the guest port a control-channel URI names.
fn worker_and_port(uri: &Uri) -> io::Result<(String, u16)> {
    let authority = uri
        .authority()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, format!("{uri} names no worker")))?;
    let port = authority
        .port_u16()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, format!("{uri} names no daemon port")))?;
    Ok((authority.host().to_owned(), port))
}

/// A relay stream as hyper's legacy client needs it: a transport that describes itself as a plain connection.
pub(crate) struct RelayStream(GuestStream);

impl Connection for RelayStream {
    fn connected(&self) -> Connected {
        Connected::new()
    }
}

impl AsyncRead for RelayStream {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_read(cx, buf)
    }
}

impl AsyncWrite for RelayStream {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &[u8]) -> Poll<io::Result<usize>> {
        Pin::new(&mut self.0).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0).poll_shutdown(cx)
    }
}
