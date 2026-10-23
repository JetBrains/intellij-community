//! The connection to the agent of one host: the address choice, and the hello of each connection.
//!
//! The client tries the cached addresses of the host file first, in their order, each with a connect deadline of 5 s.
//! Then it resolves the host name on a thread under a deadline of 5 s, IPv4 first, and tries each new address. The
//! address that answers goes first in the host file. A cached address that failed goes after the ones that did not, so
//! the next connection tries it last. When only an address of the host name answers, every cached address failed, and
//! the host file keeps the new address alone. No step waits without a deadline, so a `.local` name that does not
//! resolve costs 5 s.

use std::net::{SocketAddr, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, PoisonError, mpsc};
use std::thread;
use std::time::Duration;

use yonder_link::proto::{Reply, Request, SCHEMA_VERSION};
use yonder_link::{Channel, ClientTls, Closer, LinkError};

use crate::config::{Config, HostFile};
use crate::{Refusal, Timing, VERSION, code, prepare_hint, refused};

/// How many addresses the host file keeps.
const KEPT_ADDRESSES: usize = 4;

/// What the hello of the agent told.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AgentInfo {
    pub version: String,
    pub os: String,
    pub arch: String,
    /// The address that answered.
    pub address: SocketAddr,
}

/// One host: its host file and the identity of this client.
#[derive(Clone, Debug)]
pub struct Host {
    config: Config,
    file: HostFile,
    /// The TLS configuration of the pin of the host and the identity of the client, built once for all connections.
    tls: ClientTls,
    timing: Timing,
    /// Where each new connection leaves its closer, for a thread that ends the work of this host.
    latest: Option<Arc<Latest>>,
}

/// The latest connection of a [`Host`], which another thread can close, see [`Host::watched`].
#[derive(Debug, Default)]
pub(crate) struct Latest {
    closer: Mutex<Option<Closer>>,
    closed: AtomicBool,
}

impl Latest {
    /// Closes the latest connection, and each later one at once.
    pub(crate) fn close(&self) {
        self.closed.store(true, Ordering::SeqCst);
        let closer = self.closer.lock().unwrap_or_else(PoisonError::into_inner).take();
        if let Some(closer) = closer {
            closer.close();
        }
    }

    fn keep(&self, channel: &Channel) {
        *self.closer.lock().unwrap_or_else(PoisonError::into_inner) = channel.closer().ok();
        if self.closed.load(Ordering::SeqCst) {
            self.close();
        }
    }
}

/// Why one address gave no channel.
enum Attempt {
    /// Try the next address.
    Failed(String),
    /// Another certificate: try the next address, and name the pin when none answers.
    Untrusted(String),
    /// The agent answered and refused: no other address can do better.
    Refused(Refusal),
}

impl Host {
    pub(crate) fn open(config: &Config, alias: &str, timing: Timing) -> Result<Self, Refusal> {
        let file = config.read_host(alias)?;
        let identity = config.identity()?;
        let tls = ClientTls::new(file.fingerprint, &identity)
            .map_err(|error| refused(code::CONFIG, format!("cannot set up TLS with the client identity: {error}")))?;
        Ok(Self {
            config: config.clone(),
            file,
            tls,
            timing,
            latest: None,
        })
    }

    /// A copy whose connections leave their closer in `latest`, so another thread can end its work at once.
    pub(crate) fn watched(&self, latest: Arc<Latest>) -> Self {
        Self {
            latest: Some(latest),
            ..self.clone()
        }
    }

    pub fn alias(&self) -> &str {
        &self.file.alias
    }

    pub const fn file(&self) -> &HostFile {
        &self.file
    }

    /// A new connection after its hello.
    pub fn connect(&mut self) -> Result<(Channel, AgentInfo), Refusal> {
        let connected = self.connect_once()?;
        if let Some(latest) = &self.latest {
            latest.keep(&connected.0);
        }
        Ok(connected)
    }

    fn connect_once(&mut self) -> Result<(Channel, AgentInfo), Refusal> {
        let mut tried: Vec<String> = Vec::new();
        let mut untrusted: Option<String> = None;
        let mut note = |address: SocketAddr, attempt: Attempt| match attempt {
            Attempt::Failed(said) => {
                tried.push(format!("{address} ({said})"));
                None
            }
            Attempt::Untrusted(said) => {
                tried.push(format!("{address} (untrusted)"));
                untrusted.get_or_insert(said);
                None
            }
            Attempt::Refused(refusal) => Some(refusal),
        };
        let mut failed: Vec<SocketAddr> = Vec::new();
        for address in self.file.addresses.clone() {
            match self.attempt(address) {
                Ok(connected) => {
                    self.remember(address, &failed);
                    return Ok(connected);
                }
                Err(attempt) => {
                    if let Some(refusal) = note(address, attempt) {
                        return Err(refusal);
                    }
                    failed.push(address);
                }
            }
        }
        let resolved = resolve(&self.file.hostname, self.file.port, self.timing.resolve);
        let lookup = match &resolved {
            Ok(addresses) if addresses.is_empty() => format!("{} resolved to no address", self.file.hostname),
            Ok(addresses) => format!(
                "{} resolved to {}",
                self.file.hostname,
                addresses.iter().map(ToString::to_string).collect::<Vec<_>>().join(", ")
            ),
            Err(said) => format!("{} did not resolve: {said}", self.file.hostname),
        };
        for address in resolved.unwrap_or_default() {
            if self.file.addresses.contains(&address) {
                continue;
            }
            match self.attempt(address) {
                Ok(connected) => {
                    self.remember(address, &failed);
                    return Ok(connected);
                }
                Err(attempt) => {
                    if let Some(refusal) = note(address, attempt) {
                        return Err(refusal);
                    }
                }
            }
        }
        let alias = self.alias().to_owned();
        if let Some(said) = untrusted {
            return Err(refused(
                code::UNTRUSTED,
                format!(
                    "the agent on {alias} is not the one that the host file pins ({}), or it does not accept this client: {said}; {}",
                    self.file.fingerprint.short(),
                    prepare_hint(&alias)
                ),
            ));
        }
        let tried = if tried.is_empty() {
            "no cached address".to_owned()
        } else {
            tried.join(", ")
        };
        Err(refused(
            code::UNREACHABLE,
            format!(
                "no agent answers on {alias}: tried {tried}; {lookup}. The Mac can sleep, or the agent is not installed: {}",
                prepare_hint(&alias)
            ),
        ))
    }

    /// One request on a new connection, and its first reply. `deadline` bounds the wait for the reply.
    pub(crate) fn request(&mut self, request: &Request, deadline: Duration) -> Result<(Channel, Reply), Refusal> {
        let (mut channel, _) = self.connect()?;
        channel.send(request).map_err(|error| self.link_refusal(&error))?;
        let reply = self.recv(&mut channel, deadline)?;
        Ok((channel, reply))
    }

    /// The next reply on `channel` that is not a heartbeat. `deadline` bounds the wait for each frame, so each heartbeat
    /// starts the deadline again.
    pub(crate) fn recv(&self, channel: &mut Channel, deadline: Duration) -> Result<Reply, Refusal> {
        self.recv_beating(channel, deadline, &mut || {})
    }

    /// [`Host::recv`] that calls `heartbeat` at each heartbeat it skips.
    pub(crate) fn recv_beating(&self, channel: &mut Channel, deadline: Duration, heartbeat: &mut dyn FnMut()) -> Result<Reply, Refusal> {
        channel.set_read_deadline(deadline).map_err(|error| self.link_refusal(&error))?;
        loop {
            match channel.recv::<Reply>().map_err(|error| self.link_refusal(&error))? {
                Reply::Heartbeat { .. } => heartbeat(),
                reply => return Ok(reply),
            }
        }
    }

    /// The refusal of a failed channel.
    pub(crate) fn link_refusal(&self, error: &LinkError) -> Refusal {
        let alias = self.alias();
        match error {
            LinkError::TimedOut => refused(code::TIMED_OUT, format!("the agent on {alias} did not answer in time")),
            LinkError::Untrusted(said) => refused(code::UNTRUSTED, format!("the link to {alias} is not trusted: {said}")),
            LinkError::Unreachable(said) => refused(code::UNREACHABLE, format!("the link to {alias} broke: {said}")),
            LinkError::Protocol(said) => refused(code::LINK, format!("the link to {alias} failed: {said}")),
            LinkError::Io(said) => refused(code::LINK, format!("the link to {alias} failed: {said}")),
        }
    }

    /// The refusal for a reply that the protocol does not allow there.
    pub(crate) fn unexpected(&self, reply: &Reply) -> Refusal {
        refused(
            code::LINK,
            format!("the agent on {} sent a reply out of turn: {reply:?}", self.alias()),
        )
    }

    /// A refusal of the agent, with its own code.
    pub(crate) fn agent_refusal(&self, code: String, message: &str) -> Refusal {
        Refusal::new(code, crate::Exit::Refused, format!("{}: {message}", self.alias()))
    }

    fn attempt(&self, address: SocketAddr) -> Result<(Channel, AgentInfo), Attempt> {
        let mut channel = match Channel::connect(address, &self.tls, self.timing.connect) {
            Ok(channel) => channel,
            Err(LinkError::Untrusted(said)) => return Err(Attempt::Untrusted(said)),
            Err(error) => return Err(Attempt::Failed(error.to_string())),
        };
        let hello = channel.send(&Request::hello(VERSION)).and_then(|()| channel.recv::<Reply>());
        // The connect deadline is short, for a dead address. A write of a large pack to a slow link takes longer, so
        // each later write has the read deadline of a reply.
        let hello = hello.and_then(|reply| channel.set_write_deadline(self.timing.read).map(|()| reply));
        match hello {
            Ok(Reply::Hello {
                schema_version,
                agent_version,
                os,
                arch,
            }) if schema_version == SCHEMA_VERSION => Ok((
                channel,
                AgentInfo {
                    version: agent_version,
                    os,
                    arch,
                    address,
                },
            )),
            Ok(Reply::Hello {
                schema_version,
                agent_version,
                ..
            }) => Err(Attempt::Refused(
                self.schema_refusal(&format!("yonderd {agent_version} speaks schema {schema_version}")),
            )),
            Ok(Reply::Refused { code, message }) if code == code::SCHEMA => Err(Attempt::Refused(self.schema_refusal(&message))),
            Ok(Reply::Refused { code, message }) => Err(Attempt::Refused(self.agent_refusal(code, &message))),
            Ok(reply) => Err(Attempt::Refused(self.unexpected(&reply))),
            Err(LinkError::Untrusted(said)) => Err(Attempt::Untrusted(said)),
            Err(error) => Err(Attempt::Failed(error.to_string())),
        }
    }

    fn schema_refusal(&self, said: &str) -> Refusal {
        refused(
            code::SCHEMA,
            format!(
                "the agent on {} and this client speak two schema versions ({said}; this client speaks {SCHEMA_VERSION}); {}",
                self.alias(),
                prepare_hint(self.alias())
            ),
        )
    }

    /// Puts the address that answered first in the host file, then the cached addresses that did not fail, then the
    /// ones in `failed`. An address of the host name answers only after every cached address failed, and the cached
    /// ones then go. A host file that cannot be written keeps the old addresses.
    fn remember(&mut self, address: SocketAddr, failed: &[SocketAddr]) {
        let cached = self.file.addresses.contains(&address);
        let mut addresses = vec![address];
        addresses.extend(
            self.file
                .addresses
                .iter()
                .copied()
                .filter(|known| *known != address && !failed.contains(known)),
        );
        if cached {
            addresses.extend(failed.iter().copied().filter(|known| *known != address));
        }
        addresses.truncate(KEPT_ADDRESSES);
        if addresses != self.file.addresses {
            self.file.addresses = addresses;
            let _ = self.config.write_host(&self.file);
        }
    }
}

/// The addresses of a host name, IPv4 first, under a deadline. The lookup runs on a thread of its own, because the
/// resolver of the system has no deadline. A lookup that ends late finds the receiver gone, and its thread ends.
pub(crate) fn resolve(hostname: &str, port: u16, deadline: Duration) -> Result<Vec<SocketAddr>, String> {
    let (sender, receiver) = mpsc::channel();
    let name = hostname.to_owned();
    thread::Builder::new()
        .name("yonder-resolve".to_owned())
        .spawn(move || {
            let found = (name.as_str(), port)
                .to_socket_addrs()
                .map(Iterator::collect::<Vec<_>>)
                .map_err(|error| error.to_string());
            let _ = sender.send(found);
        })
        .map_err(|error| format!("cannot start the lookup: {error}"))?;
    let mut found = match receiver.recv_timeout(deadline) {
        Ok(found) => found?,
        Err(_) => return Err(format!("no answer in {} s", deadline.as_secs())),
    };
    found.sort_by_key(SocketAddr::is_ipv6);
    let mut addresses = Vec::with_capacity(found.len());
    for address in found {
        if !addresses.contains(&address) {
            addresses.push(address);
        }
    }
    Ok(addresses)
}

#[cfg(test)]
mod tests;
