//! `yonderd serve`: the listener of the agent. A thread per connection. Each connection: the TLS handshake within a
//! total deadline, then [`Request::Hello`], then one request. At most [`MAX_HANDSHAKES`] handshakes go on at a time, and
//! at most [`MAX_HANDSHAKES_PER_PEER`] of one address, so one host of the network cannot hold all of them. A socket
//! over a cap is closed at once. A request that waits for a lock, bd or git sends a heartbeat while it
//! waits: `Have`, the end of `Pack`, `Lease` and `Release`. The requests of a repository and of a lease are here; the
//! requests of a run are in `run_requests`. A thread runs the cleanup of `cleanup` every hour.

use std::collections::{HashMap, HashSet};
use std::io::{self, Write};
use std::net::{IpAddr, Shutdown, SocketAddr, TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, RecvTimeoutError};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use yonder_link::proto::{Reply, Request, SCHEMA_VERSION};
use yonder_link::{Channel, Frame, Identity, LinkError, ServerTls};

use crate::child::never;
use crate::files::private_dir;
use crate::git::NotRepo;
use crate::home::Home;
use crate::lease::{Leases, now_seconds};
use crate::refused::{AGENT_ERROR, BAD_REQUEST, LOCK_TIMEOUT, PACK_FAILED, REPO_MISSING, Refusal, SCHEMA, refused, reply};
use crate::run::{RunDir, Runs};
use crate::{cleanup, git, tuning};

/// The deadline of each read of the handshake and of a request.
const READ_DEADLINE: Duration = Duration::from_secs(10);

/// The most handshakes that go on at a time.
pub(crate) const MAX_HANDSHAKES: usize = 32;

/// The most handshakes of one peer address that go on at a time. A run opens a few connections at once.
pub(crate) const MAX_HANDSHAKES_PER_PEER: usize = 8;

/// The deadline of each chunk of a pack. The client builds the pack into a file before it sends it, and sends an empty
/// chunk every `HEARTBEAT_SECONDS` while it builds.
const PACK_READ_DEADLINE: Duration = Duration::from_secs(60);

/// The shortest time between two progress notes of a pack.
const PACK_PROGRESS_EVERY: Duration = Duration::from_secs(1);

/// How long a lease request waits for a run of the same holder.
const LEASE_LOCK_WAIT: Duration = Duration::from_secs(50);

/// The most commits of one [`Request::Have`].
const MAX_HAVE: usize = 1024;

/// How often the agent runs the cleanup.
const CLEANUP_EVERY: Duration = Duration::from_secs(3600);

/// The agent of one host.
#[derive(Debug)]
pub(crate) struct Agent {
    pub(crate) home: Home,
    /// The TLS configuration of the identity and the allow list, built once. The allow list is read at each handshake.
    pub(crate) tls: ServerTls,
    /// The binary of the agent, which `supervise` runs.
    pub(crate) exe: PathBuf,
    /// The repositories that a check found, so each later request of a run skips the `git rev-parse`.
    pub(crate) repos: Mutex<HashSet<PathBuf>>,
}

/// Listens on `bind:port` and serves each connection. Prints one JSON line with the address and the fingerprint on
/// `out` once it listens. It answers only when it cannot listen.
pub(crate) fn serve(home: Home, bind: IpAddr, port: u16, out: &mut dyn Write) -> Result<(), Refusal> {
    let identity = Identity::load_or_create(&home.identity_dir()).map_err(|error| {
        refused(
            AGENT_ERROR,
            format!("cannot load the identity in {}: {error}", home.identity_dir().display()),
        )
    })?;
    for dir in [home.identity_dir(), home.cache_dir()] {
        private_dir(&dir).map_err(|error| refused(AGENT_ERROR, format!("cannot make {}: {error}", dir.display())))?;
    }
    let exe = std::env::current_exe().map_err(|error| refused(AGENT_ERROR, format!("cannot find the agent binary: {error}")))?;
    let listener = TcpListener::bind(SocketAddr::new(bind, port))
        .map_err(|error| refused(AGENT_ERROR, format!("cannot listen on {bind}:{port}: {error}")))?;
    let address = listener.local_addr().map_err(|error| refused(AGENT_ERROR, error.to_string()))?;
    let line = serde_json::json!({"listening": address.to_string(), "fingerprint": identity.fingerprint().to_string()});
    let _ = writeln!(out, "{line}").and_then(|()| out.flush());
    let tls = ServerTls::new(&identity, home.allow_file()).map_err(|error| refused(AGENT_ERROR, format!("cannot set up TLS: {error}")))?;
    let agent = Arc::new(Agent {
        home,
        tls,
        exe,
        repos: Mutex::default(),
    });

    let home = agent.home.clone();
    thread::spawn(move || {
        loop {
            for line in cleanup::clean(&home, SystemTime::now()) {
                log(&line);
            }
            thread::sleep(CLEANUP_EVERY);
        }
    });

    let handshakes = Arc::new(Handshakes::default());
    for socket in listener.incoming() {
        match socket {
            Ok(socket) => {
                let peer = socket.peer_addr().map_or(IpAddr::from([0, 0, 0, 0]), |address| address.ip());
                let Some(admitted) = Handshakes::admit(&handshakes, peer) else {
                    log(&format!("closed a connection from {peer}: its cap of handshakes is full"));
                    drop(socket);
                    continue;
                };
                let agent = Arc::clone(&agent);
                thread::spawn(move || agent.connection(socket, admitted));
            }
            Err(error) => {
                log(&format!("cannot accept a connection: {error}"));
                thread::sleep(Duration::from_millis(100));
            }
        }
    }
    Ok(())
}

/// The handshakes that go on, in all and per peer address.
#[derive(Debug, Default)]
pub(crate) struct Handshakes {
    counts: Mutex<HandshakeCounts>,
}

#[derive(Debug, Default)]
struct HandshakeCounts {
    total: usize,
    per_peer: HashMap<IpAddr, usize>,
}

impl Handshakes {
    /// Counts a handshake of `peer` when both caps have room. The answer drops the count when it drops.
    pub(crate) fn admit(handshakes: &Arc<Self>, peer: IpAddr) -> Option<Admitted> {
        let mut counts = handshakes.counts.lock().unwrap_or_else(PoisonError::into_inner);
        let of_peer = counts.per_peer.get(&peer).copied().unwrap_or(0);
        if counts.total >= MAX_HANDSHAKES || of_peer >= MAX_HANDSHAKES_PER_PEER {
            return None;
        }
        counts.total += 1;
        counts.per_peer.insert(peer, of_peer + 1);
        Some(Admitted {
            handshakes: Arc::clone(handshakes),
            peer,
        })
    }
}

/// One counted handshake, see [`Handshakes::admit`].
#[derive(Debug)]
pub(crate) struct Admitted {
    handshakes: Arc<Handshakes>,
    peer: IpAddr,
}

impl Drop for Admitted {
    fn drop(&mut self) {
        let mut counts = self.handshakes.counts.lock().unwrap_or_else(PoisonError::into_inner);
        counts.total -= 1;
        if let Some(of_peer) = counts.per_peer.get_mut(&self.peer) {
            *of_peer -= 1;
            if *of_peer == 0 {
                counts.per_peer.remove(&self.peer);
            }
        }
    }
}

/// A line of the log of `serve`: its stderr, which launchd writes to `~/Library/Logs/yonderd.log`.
pub(crate) fn log(message: &str) {
    eprintln!("yonderd: {message}");
}

impl Agent {
    pub(crate) fn runs(&self) -> Runs {
        Runs::new(self.home.runs_dir())
    }

    /// Serves one connection. `admitted` counts this one until its handshake ends.
    fn connection(&self, socket: TcpStream, admitted: Admitted) {
        let address = socket
            .peer_addr()
            .map_or_else(|_| "an unknown address".to_owned(), |address| address.to_string());
        let accepted = handshake(socket, &self.tls);
        drop(admitted);
        let mut channel = match accepted {
            Ok(channel) => channel,
            Err(error) => {
                log(&format!("refused a connection from {address}: {error}"));
                return;
            }
        };
        if let Err(error) = self.converse(&mut channel) {
            log(&format!(
                "the connection of {} from {address} failed: {error}",
                channel.peer().short()
            ));
        }
        channel.close();
    }

    fn converse(&self, channel: &mut Channel) -> Result<(), LinkError> {
        match channel.recv::<Request>()? {
            Request::Hello { schema_version, .. } if schema_version == SCHEMA_VERSION => {
                channel.send(&Reply::hello(env!("CARGO_PKG_VERSION")))?;
            }
            Request::Hello { schema_version, .. } => {
                return channel.send(&Reply::refused(
                    SCHEMA,
                    format!("the agent speaks the schema {SCHEMA_VERSION}, not {schema_version}"),
                ));
            }
            _ => return channel.send(&Reply::refused(BAD_REQUEST, "the first request of a connection is a hello")),
        }
        let request = channel.recv::<Request>()?;
        match request {
            Request::Hello { .. } => answer(channel, Err(refused(BAD_REQUEST, "a connection carries one hello"))),
            Request::Have { repo, commits } => match self.repo(&repo) {
                Ok(repo) => {
                    let result = with_heartbeats(channel, &mut || {}, move || have(&repo, &commits))?;
                    answer(channel, result)
                }
                Err(refusal) => answer(channel, Err(refusal)),
            },
            Request::Pack { repo, commit, reference } => self.pack(channel, &repo, &commit, &reference),
            Request::Start(spec) => answer(channel, self.start(&spec)),
            Request::Follow { run, offset } => self.follow(channel, &run, offset),
            Request::Cancel { run } => answer(channel, self.cancel(&run)),
            Request::Fetch { run } => self.fetch(channel, &run),
            Request::Forget { run } => answer(channel, self.forget(&run)),
            Request::Runs => answer(channel, Ok(self.list_runs())),
            Request::Lease { repo, holder } => self.lease(channel, &repo, &holder, false),
            Request::Release { repo, holder } => self.lease(channel, &repo, &holder, true),
        }
    }

    /// The main repository `repo`, with `~/` expanded, when it is a git repository. A path that a check found stays
    /// found while it is a directory.
    pub(crate) fn repo(&self, repo: &str) -> Result<PathBuf, Refusal> {
        let path = self.home.expand(repo);
        if self.known_repos().contains(&path) && path.is_dir() {
            return Ok(path);
        }
        match git::check_repo(&path) {
            Ok(()) => {}
            Err(NotRepo::Missing(why)) => {
                return Err(refused(
                    REPO_MISSING,
                    format!("{} is not a git repository on this host: {why}", path.display()),
                ));
            }
            Err(NotRepo::Unchecked(why)) => {
                return Err(refused(
                    AGENT_ERROR,
                    format!("cannot check the repository {} on this host: {why}", path.display()),
                ));
            }
        }
        self.known_repos().insert(path.clone());
        Ok(path)
    }

    fn known_repos(&self) -> MutexGuard<'_, HashSet<PathBuf>> {
        self.repos.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Reads the pack that follows into `git index-pack`, then points `reference` at `commit`. A refused pack is read
    /// to its end too, so the client gets the refusal and not a broken connection. The watchdog of the child ends a
    /// write to a stalled `index-pack` at its deadline. Heartbeats go out while `index-pack` finishes.
    ///
    /// The pack of a kept run tells its supervisor how far it is: each chunk and each heartbeat writes `packing`, at
    /// most once a second, and the landed pack writes `packed`. A pack that comes after the end of its run makes no ref,
    /// because no run would delete it: its objects land, and the cleanup has nothing to do.
    fn pack(&self, channel: &mut Channel, repo: &str, commit: &str, reference: &str) -> Result<(), LinkError> {
        channel.set_read_deadline(PACK_READ_DEADLINE)?;
        let checked = self.repo(repo).and_then(|repo| {
            if !git::is_commit_id(commit) {
                return Err(refused(BAD_REQUEST, format!("{commit:?} is not a full commit id")));
            }
            if !git::is_pack_ref(reference) {
                return Err(refused(
                    BAD_REQUEST,
                    format!(
                        "{reference:?} is not a ref {}<run id> or {}<name>",
                        git::REF_PREFIX,
                        yonder_link::proto::BASE_REFS
                    ),
                ));
            }
            Ok(repo)
        });
        let repo = match checked {
            Ok(repo) => repo,
            Err(refusal) => {
                drain(channel)?;
                return channel.send(&reply(&refusal));
            }
        };
        let mut running = match git::index_pack(&repo) {
            Ok(running) => running,
            Err(error) => {
                drain(channel)?;
                return channel.send(&Reply::refused(PACK_FAILED, error));
            }
        };
        let run = reference.strip_prefix(git::REF_PREFIX).and_then(|run| self.runs().get(run).ok());
        let progress = PackProgress::new(run.clone());
        let mut stdin = running.take_stdin();
        let received = loop {
            match channel.recv_frame() {
                Ok(Frame::Bytes(chunk)) => {
                    progress.note();
                    // index-pack can end early on a bad pack; the rest of the stream is read and dropped.
                    if stdin.as_mut().is_some_and(|pipe| pipe.write_all(&chunk).is_err()) {
                        stdin = None;
                    }
                }
                Ok(Frame::End) => break Ok(()),
                Ok(Frame::Message(_)) => break Err(LinkError::Protocol("a message came inside a pack".to_owned())),
                Err(error) => break Err(error),
            }
        };
        drop(stdin);
        let (reference, commit) = (reference.to_owned(), commit.to_owned());
        let home = self.home.clone();
        let indexed = with_heartbeats(channel, &mut || progress.note(), move || {
            let indexed = git::finish_index_pack(running);
            received?;
            let result = indexed
                .map_err(|error| format!("git index-pack failed in {}: {error}", repo.display()))
                .and_then(|()| point_ref(&repo, &reference, &commit, run.as_ref()));
            if result.is_ok()
                && let Err(error) = cleanup::remember_repo(&home, &repo)
            {
                log(&format!("cannot add {} to the repository list: {error}", repo.display()));
            }
            Ok(result)
        })?;
        match indexed? {
            Ok(()) => channel.send(&Reply::Packed),
            Err(message) => channel.send(&Reply::refused(PACK_FAILED, message)),
        }
    }

    /// Takes the lease of `holder`, or returns it with `release`. Heartbeats go out while it waits for the lock of the
    /// holder or for bd. A lease request makes no idle sweep, because it names no idle hours.
    fn lease(&self, channel: &mut Channel, repo: &str, holder: &str, release: bool) -> Result<(), LinkError> {
        let checked = self.repo(repo).and_then(|repo| check_holder(holder).map(|()| repo));
        let repo = match checked {
            Ok(repo) => repo,
            Err(refusal) => return channel.send(&reply(&refusal)),
        };
        let leases = Leases::new(self.home.leases_dir(), repo);
        let holder = holder.to_owned();
        let result = with_heartbeats(channel, &mut || {}, move || {
            let lock = leases
                .lock(&holder, LEASE_LOCK_WAIT, &never, &mut |line| log(line))?
                .ok_or_else(|| refused(LOCK_TIMEOUT, format!("the lock of {holder} is held")))?;
            let answer = if release {
                leases.release(&holder, &never).map(|message| Reply::Done { message })
            } else {
                leases.acquire(&holder, &never, &mut |line| log(line)).and_then(|lease| {
                    leases
                        .record(&holder, &lease, now_seconds())
                        .map_err(|error| refused(AGENT_ERROR, format!("cannot write the lease file of {holder}: {error}")))?;
                    Ok(Reply::Leased {
                        slot: lease.slot.display().to_string(),
                        lease_id: lease.lease_id,
                        fresh: lease.fresh,
                    })
                })
            };
            drop(lock);
            answer
        })?;
        answer(channel, result)
    }
}

/// The commits of `commits` that the repository `repo` has. `git cat-file` can take long on a busy disk, so the caller
/// runs it with heartbeats.
fn have(repo: &Path, commits: &[String]) -> Result<Reply, Refusal> {
    if commits.len() > MAX_HAVE {
        return Err(refused(
            BAD_REQUEST,
            format!("a have names {} commits, more than {MAX_HAVE}", commits.len()),
        ));
    }
    if let Some(bad) = commits.iter().find(|commit| !git::is_commit_id(commit)) {
        return Err(refused(BAD_REQUEST, format!("{bad:?} is not a full commit id")));
    }
    git::have(repo, commits)
        .map(|commits| Reply::Have { commits })
        .map_err(|error| refused(AGENT_ERROR, error))
}

/// The TLS handshake of `socket` within the total deadline of [`tuning::handshake`]. A timer thread shuts the socket
/// down at the deadline, so a client that sends a byte at a time cannot hold the handshake longer.
fn handshake(socket: TcpStream, tls: &ServerTls) -> Result<Channel, LinkError> {
    let deadline = tuning::handshake();
    let cut = socket.try_clone().map_err(LinkError::Io)?;
    let (done, ended) = mpsc::channel::<()>();
    thread::spawn(move || {
        if ended.recv_timeout(deadline) == Err(RecvTimeoutError::Timeout) {
            let _ = cut.shutdown(Shutdown::Both);
        }
    });
    let accepted = Channel::accept(socket, tls, READ_DEADLINE.min(deadline));
    let _ = done.send(());
    accepted
}

/// Points `reference` at `commit` once the objects of a pack landed, and tells the supervisor of `run`. A run that has
/// ended gets no ref, and a run that ends during the update gets its ref deleted, so no ref outlives its run.
fn point_ref(repo: &Path, reference: &str, commit: &str, run: Option<&RunDir>) -> Result<(), String> {
    let ended = |run: &RunDir| matches!(run.exit(), Ok(Some(_)));
    if run.is_some_and(ended) {
        return Ok(());
    }
    git::update_ref(repo, reference, commit)
        .map_err(|error| format!("git update-ref {reference} {commit} failed in {}: {error}", repo.display()))?;
    let Some(run) = run else {
        return Ok(());
    };
    if ended(run) {
        return git::delete_ref(repo, reference).map_err(|error| format!("cannot delete {reference} in {}: {error}", repo.display()));
    }
    if let Err(error) = run.mark_packed() {
        log(&format!("cannot mark the pack of {} as landed: {error}", run.path().display()));
    }
    Ok(())
}

/// The progress notes of the pack of a run, at most one per [`PACK_PROGRESS_EVERY`].
struct PackProgress {
    run: Option<RunDir>,
    noted: Mutex<Option<Instant>>,
}

impl PackProgress {
    const fn new(run: Option<RunDir>) -> Self {
        Self {
            run,
            noted: Mutex::new(None),
        }
    }

    fn note(&self) {
        let Some(run) = &self.run else {
            return;
        };
        let mut noted = self.noted.lock().unwrap_or_else(PoisonError::into_inner);
        if noted.is_some_and(|at| at.elapsed() < PACK_PROGRESS_EVERY) {
            return;
        }
        *noted = Some(Instant::now());
        if let Err(error) = run.note_pack_progress() {
            log(&format!("cannot note the pack progress of {}: {error}", run.path().display()));
        }
    }
}

/// Runs `work` on a thread, and sends a heartbeat every [`tuning::heartbeat`] until it answers. `beat` runs at each
/// heartbeat.
fn with_heartbeats<T: Send + 'static>(
    channel: &mut Channel,
    beat: &mut dyn FnMut(),
    work: impl FnOnce() -> T + Send + 'static,
) -> Result<T, LinkError> {
    let (sender, receiver) = mpsc::channel();
    thread::spawn(move || {
        let _ = sender.send(work());
    });
    let started = Instant::now();
    let heartbeat = tuning::heartbeat();
    loop {
        match receiver.recv_timeout(heartbeat) {
            Ok(result) => return Ok(result),
            Err(RecvTimeoutError::Timeout) => {
                beat();
                channel.send(&Reply::Heartbeat {
                    elapsed_ms: u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX),
                })?;
            }
            Err(RecvTimeoutError::Disconnected) => return Err(LinkError::Protocol("the request failed inside the agent".to_owned())),
        }
    }
}

/// Refuses a holder that cannot name a lease file.
pub(crate) fn check_holder(holder: &str) -> Result<(), Refusal> {
    if yonder_link::proto::is_run_id(holder) {
        Ok(())
    } else {
        Err(refused(BAD_REQUEST, format!("{holder:?} cannot name a lease holder")))
    }
}

/// Sends the reply, or the refusal.
fn answer(channel: &mut Channel, result: Result<Reply, Refusal>) -> Result<(), LinkError> {
    match result {
        Ok(answer) => channel.send(&answer),
        Err(refusal) => channel.send(&reply(&refusal)),
    }
}

/// Reads a byte stream to its end and drops it.
fn drain(channel: &mut Channel) -> Result<(), LinkError> {
    channel.recv_stream(&mut io::sink()).map(drop)
}

/// Whether `dir` is a relative path that stays inside its base: no root, no `..`, no `.`.
pub(crate) fn is_inner_path(dir: &str) -> bool {
    !dir.is_empty()
        && Path::new(dir)
            .components()
            .all(|component| matches!(component, std::path::Component::Normal(_)))
}

#[cfg(test)]
mod tests;
