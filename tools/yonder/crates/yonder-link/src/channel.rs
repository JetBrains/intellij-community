//! The channel: TLS 1.3 over one TCP connection, each end pinned by its fingerprint, and the frames on it.
//!
//! A frame is a kind byte, a big-endian u32 length and the payload:
//!
//! | Kind | Payload |
//! |---|---|
//! | 0, a message | one JSON value, at most [`MAX_MESSAGE`] bytes |
//! | 1, bytes | a chunk of a byte stream, at most [`CHUNK`] bytes |
//! | 2, the end | nothing: the byte stream before it is complete |
//!
//! One connection carries one request. Every read and every write has the deadline of the socket, so no call waits
//! for ever.

use std::fmt;
use std::io::{self, Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::ops::{Deref, DerefMut};
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use rustls::client::Resumption;
use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::crypto::{CryptoProvider, WebPkiSupportedAlgorithms, verify_tls13_signature};
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::server::danger::{ClientCertVerified, ClientCertVerifier};
use rustls::{CertificateError, ClientConfig, ClientConnection, DigitallySignedStruct, DistinguishedName, ServerConfig, ServerConnection};
use rustls::{ConnectionCommon, SideData, SignatureScheme, StreamOwned};
use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::allow::AllowList;
use crate::identity::{Fingerprint, Identity, SERVER_NAME};
use crate::relay;

/// The largest JSON message.
pub const MAX_MESSAGE: usize = 1024 * 1024;

/// The largest chunk of a byte stream.
pub const CHUNK: usize = 64 * 1024;

const KIND_MESSAGE: u8 = 0;
const KIND_BYTES: u8 = 1;
const KIND_END: u8 = 2;

/// Why a channel failed. The client turns each kind into a refusal of its own.
#[derive(Debug)]
pub enum LinkError {
    /// No TCP connection within the deadline, or the connection was refused or reset before the handshake ended.
    Unreachable(io::Error),
    /// The other end showed a certificate that this end does not trust: another host behind the address, or a
    /// client that the allow list of the agent does not name.
    Untrusted(String),
    /// No frame within the deadline of the socket.
    TimedOut,
    /// The other end closed the connection, or sent a frame that is not in the protocol.
    Protocol(String),
    Io(io::Error),
}

impl fmt::Display for LinkError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Unreachable(error) => write!(f, "no connection: {error}"),
            Self::Untrusted(message) | Self::Protocol(message) => f.write_str(message),
            Self::TimedOut => f.write_str("the other end did not answer in time"),
            Self::Io(error) => write!(f, "{error}"),
        }
    }
}

impl std::error::Error for LinkError {}

impl LinkError {
    fn from_io(error: io::Error) -> Self {
        if matches!(error.kind(), io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock) {
            return Self::TimedOut;
        }
        if error.kind() == io::ErrorKind::UnexpectedEof {
            return Self::Protocol("the other end closed the connection".to_owned());
        }
        // rustls reports a refused certificate as an io::Error that wraps its own error.
        if let Some(tls) = error.get_ref().and_then(|inner| inner.downcast_ref::<rustls::Error>()) {
            return match tls {
                rustls::Error::InvalidCertificate(_)
                | rustls::Error::AlertReceived(
                    rustls::AlertDescription::BadCertificate
                    | rustls::AlertDescription::AccessDenied
                    | rustls::AlertDescription::CertificateUnknown,
                ) => Self::Untrusted(format!(
                    "the other end refused the identity or showed one that is not trusted: {tls}"
                )),
                _ => Self::Protocol(format!("TLS: {tls}")),
            };
        }
        Self::Io(error)
    }
}

/// The TLS stream of either end.
trait Tls: Read + Write + Send {
    fn close(&mut self);
    fn peer_fingerprint(&self) -> Option<Fingerprint>;
}

impl<C, S> Tls for StreamOwned<C, TcpStream>
where
    C: DerefMut + Deref<Target = ConnectionCommon<S>> + Send,
    S: SideData,
{
    fn close(&mut self) {
        self.conn.send_close_notify();
        let _ = self.flush();
    }

    fn peer_fingerprint(&self) -> Option<Fingerprint> {
        self.conn.peer_certificates()?.first().map(|cert| Fingerprint::of(cert))
    }
}

/// The TLS configuration of a client of one host: the pin of the host and the identity of the client. A client builds
/// it once and connects with it as often as it needs. Session resumption is off, so each handshake checks both
/// certificates.
#[derive(Clone)]
pub struct ClientTls {
    config: Arc<ClientConfig>,
}

impl fmt::Debug for ClientTls {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("ClientTls")
    }
}

impl ClientTls {
    /// The configuration that checks that the host shows the certificate of `host`.
    pub fn new(host: Fingerprint, identity: &Identity) -> Result<Self, LinkError> {
        let provider = Arc::new(provider());
        let mut config = ClientConfig::builder_with_provider(provider.clone())
            .with_protocol_versions(&[&rustls::version::TLS13])
            .map_err(tls_setup)?
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(PinnedHost {
                host,
                signatures: Tls13 {
                    algorithms: provider.signature_verification_algorithms,
                },
            }))
            .with_client_auth_cert(identity.cert_chain(), identity.private_key())
            .map_err(tls_setup)?;
        config.resumption = Resumption::disabled();
        Ok(Self { config: Arc::new(config) })
    }
}

/// The TLS setup of an agent: its identity and the verifier of the allow list of its clients, built once. The verifier
/// reads the allow list at each handshake. Each handshake gets a configuration of its own, with a session store of its
/// own, so no session outlives its connection, and a client that left the allow list gets no later connection. The
/// agent still sends the session tickets of rustls, because they end the last read of the client handshake.
#[derive(Clone)]
pub struct ServerTls {
    provider: Arc<CryptoProvider>,
    verifier: Arc<AllowedClients>,
    identity: Identity,
}

impl fmt::Debug for ServerTls {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("ServerTls")
    }
}

impl ServerTls {
    /// The setup that accepts a client whose certificate the allow list `allow` names.
    pub fn new(identity: &Identity, allow: PathBuf) -> Result<Self, LinkError> {
        let provider = Arc::new(provider());
        let tls = Self {
            verifier: Arc::new(AllowedClients {
                allow,
                signatures: Tls13 {
                    algorithms: provider.signature_verification_algorithms,
                },
            }),
            provider,
            identity: identity.clone(),
        };
        tls.config()?;
        Ok(tls)
    }

    /// The configuration of one handshake.
    fn config(&self) -> Result<Arc<ServerConfig>, LinkError> {
        let config = ServerConfig::builder_with_provider(Arc::clone(&self.provider))
            .with_protocol_versions(&[&rustls::version::TLS13])
            .map_err(tls_setup)?
            .with_client_cert_verifier(Arc::clone(&self.verifier) as Arc<dyn ClientCertVerifier>)
            .with_single_cert(self.identity.cert_chain(), self.identity.private_key())
            .map_err(tls_setup)?;
        Ok(Arc::new(config))
    }
}

/// One connection of the protocol, after the handshake.
pub struct Channel {
    tls: Box<dyn Tls>,
    socket: TcpStream,
    peer: Fingerprint,
    /// The buffer of the frames of one write, kept between the writes.
    frames: Vec<u8>,
}

impl fmt::Debug for Channel {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Channel({})", self.peer.short())
    }
}

/// A frame as it arrives.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Frame {
    Message(Vec<u8>),
    Bytes(Vec<u8>),
    End,
}

impl Channel {
    /// Connects to an agent at `address` and checks that it shows the certificate that `tls` pins. `deadline` bounds
    /// the TCP connect and each read and write of the handshake, and stays the deadline of the socket afterwards.
    ///
    /// On macOS a connect that the Local Network permission refuses goes through the system `nc` instead; see
    /// the `relay` module.
    pub fn connect(address: SocketAddr, tls: &ClientTls, deadline: Duration) -> Result<Self, LinkError> {
        let socket = match TcpStream::connect_timeout(&address, deadline) {
            Ok(socket) => socket,
            Err(error) if relay::is_local_network_refusal(&error) => relay::connect(address, deadline).map_err(|relayed| {
                LinkError::Unreachable(io::Error::new(relayed.kind(), format!("{error}; through {}: {relayed}", relay::NC)))
            })?,
            Err(error) => return Err(LinkError::Unreachable(error)),
        };
        Self::connect_over(socket, tls, deadline)
    }

    /// The TLS client handshake over a TCP connection that is open already.
    pub(crate) fn connect_over(socket: TcpStream, tls: &ClientTls, deadline: Duration) -> Result<Self, LinkError> {
        prepare(&socket, deadline).map_err(LinkError::Io)?;
        let name = ServerName::try_from(SERVER_NAME).map_err(|error| LinkError::Protocol(error.to_string()))?;
        let connection = ClientConnection::new(Arc::clone(&tls.config), name).map_err(tls_setup)?;
        let stream = StreamOwned::new(connection, socket.try_clone().map_err(LinkError::Io)?);
        finish_handshake(Box::new(stream), socket)
    }

    /// Takes a TCP connection that the agent accepted, and checks that the client shows a certificate of the allow list
    /// of `tls`.
    pub fn accept(socket: TcpStream, tls: &ServerTls, deadline: Duration) -> Result<Self, LinkError> {
        prepare(&socket, deadline).map_err(LinkError::Io)?;
        let connection = ServerConnection::new(tls.config()?).map_err(tls_setup)?;
        let stream = StreamOwned::new(connection, socket.try_clone().map_err(LinkError::Io)?);
        finish_handshake(Box::new(stream), socket)
    }

    /// The fingerprint of the other end, checked by the handshake.
    pub const fn peer(&self) -> Fingerprint {
        self.peer
    }

    /// The deadline of each later read. `None` waits for ever, which no caller of the protocol wants.
    pub fn set_read_deadline(&self, deadline: Duration) -> Result<(), LinkError> {
        self.socket.set_read_timeout(Some(deadline)).map_err(LinkError::Io)
    }

    /// The deadline of each later write.
    pub fn set_write_deadline(&self, deadline: Duration) -> Result<(), LinkError> {
        self.socket.set_write_timeout(Some(deadline)).map_err(LinkError::Io)
    }

    /// Sends one message.
    pub fn send<T: Serialize>(&mut self, message: &T) -> Result<(), LinkError> {
        self.send_all(std::slice::from_ref(message))
    }

    /// Sends `messages` one after the other in one write, so a batch costs one TLS record per 16 KiB and not one per
    /// message.
    pub fn send_all<T: Serialize>(&mut self, messages: &[T]) -> Result<(), LinkError> {
        let mut frames = std::mem::take(&mut self.frames);
        frames.clear();
        let built = messages.iter().try_for_each(|message| {
            let json = serde_json::to_vec(message).map_err(|error| LinkError::Protocol(error.to_string()))?;
            if json.len() > MAX_MESSAGE {
                return Err(LinkError::Protocol(format!(
                    "a message of {} bytes is over the limit of {MAX_MESSAGE}",
                    json.len()
                )));
            }
            push_frame(&mut frames, KIND_MESSAGE, &json)
        });
        let sent = built.and_then(|()| {
            self.tls.write_all(&frames).map_err(LinkError::from_io)?;
            self.tls.flush().map_err(LinkError::from_io)
        });
        self.frames = frames;
        sent
    }

    /// Sends a byte stream as chunks. [`Channel::send_end`] completes it.
    pub fn send_bytes(&mut self, bytes: &[u8]) -> Result<(), LinkError> {
        for chunk in bytes.chunks(CHUNK) {
            self.write_frame(KIND_BYTES, chunk)?;
        }
        Ok(())
    }

    /// Completes a byte stream.
    pub fn send_end(&mut self) -> Result<(), LinkError> {
        self.write_frame(KIND_END, &[])?;
        self.tls.flush().map_err(LinkError::from_io)
    }

    /// Copies all of `reader` as a byte stream, and completes it. Answers the count of bytes. `progress` gets the count
    /// after each chunk.
    pub fn send_stream(&mut self, reader: &mut dyn Read, progress: &mut dyn FnMut(u64)) -> Result<u64, LinkError> {
        let mut buffer = vec![0u8; CHUNK];
        let mut total = 0u64;
        loop {
            let read = match reader.read(&mut buffer) {
                Ok(0) => break,
                Ok(read) => read,
                Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
                Err(error) => return Err(LinkError::Io(error)),
            };
            self.write_frame(KIND_BYTES, &buffer[..read])?;
            total += read as u64;
            progress(total);
        }
        self.send_end()?;
        Ok(total)
    }

    /// The next frame.
    pub fn recv_frame(&mut self) -> Result<Frame, LinkError> {
        let mut head = [0u8; 5];
        self.tls.read_exact(&mut head).map_err(LinkError::from_io)?;
        let length = u32::from_be_bytes([head[1], head[2], head[3], head[4]]) as usize;
        let limit = match head[0] {
            KIND_MESSAGE => MAX_MESSAGE,
            KIND_BYTES => CHUNK,
            KIND_END => 0,
            kind => return Err(LinkError::Protocol(format!("a frame of the unknown kind {kind}"))),
        };
        if length > limit {
            return Err(LinkError::Protocol(format!(
                "a frame of {length} bytes is over the limit of {limit}"
            )));
        }
        let mut payload = vec![0u8; length];
        self.tls.read_exact(&mut payload).map_err(LinkError::from_io)?;
        Ok(match head[0] {
            KIND_MESSAGE => Frame::Message(payload),
            KIND_BYTES => Frame::Bytes(payload),
            _ => Frame::End,
        })
    }

    /// The next message. A byte frame here is a protocol error.
    pub fn recv<T: DeserializeOwned>(&mut self) -> Result<T, LinkError> {
        match self.recv_frame()? {
            Frame::Message(json) => serde_json::from_slice(&json).map_err(|error| {
                LinkError::Protocol(format!(
                    "a message that is not in the protocol ({error}): {}",
                    String::from_utf8_lossy(&json)
                ))
            }),
            Frame::Bytes(_) | Frame::End => Err(LinkError::Protocol("bytes came where a message was due".to_owned())),
        }
    }

    /// Copies the byte stream that comes next into `writer`, up to its end. Answers the count of bytes.
    pub fn recv_stream(&mut self, writer: &mut dyn Write) -> Result<u64, LinkError> {
        let mut total = 0u64;
        loop {
            match self.recv_frame()? {
                Frame::Bytes(chunk) => {
                    writer.write_all(&chunk).map_err(LinkError::Io)?;
                    total += chunk.len() as u64;
                }
                Frame::End => return Ok(total),
                Frame::Message(_) => return Err(LinkError::Protocol("a message came inside a byte stream".to_owned())),
            }
        }
    }

    /// Ends the connection with a TLS close.
    pub fn close(mut self) {
        self.tls.close();
    }

    /// A handle that ends the connection from another thread. A read or a write that waits on this channel then fails
    /// at once.
    pub fn closer(&self) -> Result<Closer, LinkError> {
        self.socket.try_clone().map(|socket| Closer { socket }).map_err(LinkError::Io)
    }

    /// Writes one frame in one write, so it goes out as one TLS record.
    fn write_frame(&mut self, kind: u8, payload: &[u8]) -> Result<(), LinkError> {
        let mut frames = std::mem::take(&mut self.frames);
        frames.clear();
        let written = push_frame(&mut frames, kind, payload).and_then(|()| self.tls.write_all(&frames).map_err(LinkError::from_io));
        self.frames = frames;
        written
    }
}

/// Appends the frame of `kind` with `payload` to `frames`.
fn push_frame(frames: &mut Vec<u8>, kind: u8, payload: &[u8]) -> Result<(), LinkError> {
    let length = u32::try_from(payload.len()).map_err(|error| LinkError::Protocol(format!("a frame over 4 GiB: {error}")))?;
    frames.push(kind);
    frames.extend_from_slice(&length.to_be_bytes());
    frames.extend_from_slice(payload);
    Ok(())
}

/// Ends the TCP connection of a [`Channel`] from another thread, see [`Channel::closer`].
#[derive(Debug)]
pub struct Closer {
    socket: TcpStream,
}

impl Closer {
    /// Shuts the connection down in both directions. A connection that is gone already is no error.
    pub fn close(&self) {
        let _ = self.socket.shutdown(std::net::Shutdown::Both);
    }
}

/// The provider of every TLS configuration of yonder.
pub fn provider() -> CryptoProvider {
    rustls::crypto::ring::default_provider()
}

fn prepare(socket: &TcpStream, deadline: Duration) -> io::Result<()> {
    socket.set_nodelay(true)?;
    socket.set_read_timeout(Some(deadline))?;
    socket.set_write_timeout(Some(deadline))
}

#[expect(clippy::needless_pass_by_value, reason = "a map_err callback takes the error by value")]
fn tls_setup(error: rustls::Error) -> LinkError {
    LinkError::Protocol(format!("cannot set up TLS: {error}"))
}

/// Drives the handshake to its end under the deadline of the socket.
fn finish_handshake(mut tls: Box<dyn Tls>, socket: TcpStream) -> Result<Channel, LinkError> {
    // A write of no bytes makes StreamOwned complete the handshake. `write_all` would skip the call for an empty slice.
    // The client reads once more after its last handshake message: the session tickets of the agent end that read, or
    // the refusal of the client certificate.
    tls.write(&[]).and_then(|_| tls.flush()).map_err(handshake_error)?;
    // The client finishes its half before the server has read the client certificate. A read of the first frame
    // would surface a refusal there; the peer certificate is known already, which is what both ends check.
    let peer = tls
        .peer_fingerprint()
        .ok_or_else(|| LinkError::Untrusted("the other end showed no certificate".to_owned()))?;
    Ok(Channel {
        tls,
        socket,
        peer,
        frames: Vec::new(),
    })
}

fn handshake_error(error: io::Error) -> LinkError {
    match LinkError::from_io(error) {
        LinkError::Io(error) if is_connection_loss(&error) => LinkError::Unreachable(error),
        LinkError::TimedOut => LinkError::Unreachable(io::Error::new(io::ErrorKind::TimedOut, "the handshake did not end in time")),
        other => other,
    }
}

fn is_connection_loss(error: &io::Error) -> bool {
    matches!(
        error.kind(),
        io::ErrorKind::ConnectionRefused | io::ErrorKind::ConnectionReset | io::ErrorKind::ConnectionAborted | io::ErrorKind::BrokenPipe
    )
}

/// The signature checks that both verifiers share. Both configurations allow TLS 1.3 only, so a TLS 1.2 signature
/// never arrives.
#[derive(Debug)]
struct Tls13 {
    algorithms: WebPkiSupportedAlgorithms,
}

/// The three signature methods of a verifier of [`Tls13`] in the field `signatures`. `ServerCertVerifier` and
/// `ClientCertVerifier` declare the same three methods, and a trait cannot give both of them a default.
macro_rules! tls13_signatures {
    () => {
        fn verify_tls12_signature(
            &self,
            _message: &[u8],
            _cert: &CertificateDer<'_>,
            _dss: &DigitallySignedStruct,
        ) -> Result<HandshakeSignatureValid, rustls::Error> {
            Err(rustls::Error::General("yonder speaks TLS 1.3 only".to_owned()))
        }

        fn verify_tls13_signature(
            &self,
            message: &[u8],
            cert: &CertificateDer<'_>,
            dss: &DigitallySignedStruct,
        ) -> Result<HandshakeSignatureValid, rustls::Error> {
            verify_tls13_signature(message, cert, dss, &self.signatures.algorithms)
        }

        fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
            self.signatures.algorithms.supported_schemes()
        }
    };
}

/// The verifier of a client: the host must show the pinned certificate.
#[derive(Debug)]
struct PinnedHost {
    host: Fingerprint,
    signatures: Tls13,
}

impl ServerCertVerifier for PinnedHost {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        if Fingerprint::of(end_entity) == self.host {
            Ok(ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::InvalidCertificate(CertificateError::ApplicationVerificationFailure))
        }
    }

    tls13_signatures!();
}

/// The verifier of an agent: the client must show a certificate of the allow list.
#[derive(Debug)]
struct AllowedClients {
    allow: PathBuf,
    signatures: Tls13,
}

impl ClientCertVerifier for AllowedClients {
    fn root_hint_subjects(&self) -> &[DistinguishedName] {
        &[]
    }

    fn verify_client_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _now: UnixTime,
    ) -> Result<ClientCertVerified, rustls::Error> {
        let allowed = AllowList::read(&self.allow).map_err(|error| rustls::Error::General(error.to_string()))?;
        if allowed.name(&Fingerprint::of(end_entity)).is_some() {
            Ok(ClientCertVerified::assertion())
        } else {
            Err(rustls::Error::InvalidCertificate(CertificateError::ApplicationVerificationFailure))
        }
    }

    tls13_signatures!();
}

#[cfg(test)]
mod tests;
