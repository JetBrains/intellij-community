//! The test doubles of the crate: scripted child processes, and an agent on a loopback port that answers each
//! connection from a script.

use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, PoisonError};
use std::thread;
use std::time::Duration;

use yonder_link::proto::{FEATURES, Reply, Request, SCHEMA_VERSION};
use yonder_link::{AllowList, Channel, Identity, ServerTls};

use crate::config::{Config, HostFile};
use crate::process::{Call, Processes, Ran, Stop, Stopped};
use crate::{Client, Timing};

/// Short deadlines, so a test of a lost link ends in seconds.
pub(crate) const FAST: Timing = Timing {
    connect: Duration::from_secs(2),
    resolve: Duration::from_secs(2),
    read: Duration::from_secs(2),
    reconnect_for: Duration::from_secs(3),
    first_backoff: Duration::from_millis(10),
    max_backoff: Duration::from_millis(50),
    agent_start: Duration::from_secs(1),
    keepalive: Duration::from_millis(50),
};

pub(crate) const ALIAS: &str = "studio";

pub(crate) fn ok(stdout: &str) -> Ran {
    Ran {
        code: Some(0),
        stdout: stdout.as_bytes().to_vec(),
        ..Ran::default()
    }
}

pub(crate) fn failed(code: i32, stderr: &str) -> Ran {
    Ran {
        code: Some(code),
        stderr: stderr.to_owned(),
        ..Ran::default()
    }
}

/// Child processes that answer by rule: the first rule whose text the joined call holds. A call without a rule
/// answers exit 0 and no output. `run_to_file` writes the stdout of the rule into the file, and a stop that answers
/// `true` stops the call as Ctrl+C would.
#[derive(Default)]
pub(crate) struct FakeProcesses {
    rules: Vec<(String, Ran)>,
    calls: Mutex<Vec<Call>>,
    /// How long `git pack-objects` works. The watch asks the stop every 10 ms meanwhile, as the real one does.
    pack_time: Duration,
}

impl FakeProcesses {
    pub(crate) fn new(rules: &[(&str, Ran)]) -> Self {
        Self {
            rules: rules.iter().map(|(text, ran)| ((*text).to_owned(), ran.clone())).collect(),
            calls: Mutex::default(),
            pack_time: Duration::ZERO,
        }
    }

    /// The same processes with a `git pack-objects` that works for `time`.
    pub(crate) const fn with_pack_time(mut self, time: Duration) -> Self {
        self.pack_time = time;
        self
    }

    pub(crate) fn calls(&self) -> Vec<Call> {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn texts(&self) -> Vec<String> {
        self.calls().iter().map(Call::text).collect()
    }

    fn answer(&self, call: &Call) -> Ran {
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(call.clone());
        let text = call.text();
        self.rules
            .iter()
            .find(|(rule, _)| text.contains(rule.as_str()))
            .map_or_else(|| ok(""), |(_, ran)| ran.clone())
    }
}

impl Processes for FakeProcesses {
    fn run(&self, call: &Call, stop: Stop<'_>) -> Ran {
        let ran = self.answer(call);
        let working = std::time::Instant::now();
        while call.text().contains("pack-objects") && working.elapsed() < self.pack_time && !stop() {
            thread::sleep(Duration::from_millis(10));
        }
        if stop() {
            return Ran {
                stopped: Some(Stopped::Asked),
                stderr: format!("{} stopped after Ctrl+C", call.program),
                ..Ran::default()
            };
        }
        ran
    }
}

/// What the agent does with the one request of a connection, after the hello.
pub(crate) type Script = dyn Fn(&Request, &mut Channel) + Send + Sync;

/// An agent on a loopback port. Each connection runs on a thread of its own: the hello, then the script.
pub(crate) struct FakeAgent {
    pub(crate) address: SocketAddr,
    pub(crate) identity: Identity,
    requests: Arc<Mutex<Vec<Request>>>,
    stop: Arc<AtomicBool>,
    _dir: tempfile::TempDir,
}

impl FakeAgent {
    /// An agent that accepts `client`, speaks `schema`, has the features `features` (none for an agent older than the
    /// features), and answers each request with `script`.
    pub(crate) fn start_with(
        client: &Identity,
        schema: u32,
        features: &[&str],
        script: impl Fn(&Request, &mut Channel) + Send + Sync + 'static,
    ) -> Self {
        let features: Vec<String> = features.iter().map(|feature| (*feature).to_owned()).collect();
        let dir = tempfile::tempdir().expect("a temporary directory");
        let allow = dir.path().join("clients");
        let mut list = AllowList::default();
        list.add(client.fingerprint(), "test");
        std::fs::write(&allow, list.text()).expect("the allow list");
        let listener = TcpListener::bind("127.0.0.1:0").expect("a port");
        let address = listener.local_addr().expect("the address");
        let identity = Identity::generate().expect("the agent identity");
        let requests = Arc::new(Mutex::new(Vec::new()));
        let stop = Arc::new(AtomicBool::new(false));
        let script: Arc<Script> = Arc::new(script);
        {
            let tls = ServerTls::new(&identity, allow).expect("the agent TLS");
            let requests = Arc::clone(&requests);
            let stop = Arc::clone(&stop);
            thread::spawn(move || {
                for socket in listener.incoming() {
                    if stop.load(Ordering::SeqCst) {
                        return;
                    }
                    let Ok(socket) = socket else { continue };
                    let (tls, requests, script) = (tls.clone(), Arc::clone(&requests), Arc::clone(&script));
                    let features = features.clone();
                    thread::spawn(move || serve(socket, &tls, (schema, features), &requests, &*script));
                }
            });
        }
        Self {
            address,
            identity,
            requests,
            stop,
            _dir: dir,
        }
    }

    /// The list of the requests, for a test thread that waits for one.
    pub(crate) fn requests_handle(&self) -> Arc<Mutex<Vec<Request>>> {
        Arc::clone(&self.requests)
    }

    /// The requests after the hello, in the order they came.
    pub(crate) fn requests(&self) -> Vec<Request> {
        self.requests.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }
}

impl Drop for FakeAgent {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::SeqCst);
        // Wakes the accept loop, which then sees the stop.
        let _ = TcpStream::connect_timeout(&self.address, Duration::from_secs(1));
    }
}

fn serve(socket: TcpStream, tls: &ServerTls, (schema, features): (u32, Vec<String>), requests: &Mutex<Vec<Request>>, script: &Script) {
    let Ok(mut channel) = Channel::accept(socket, tls, Duration::from_secs(5)) else {
        return;
    };
    let Ok(Request::Hello { .. }) = channel.recv::<Request>() else {
        return;
    };
    let hello = Reply::Hello {
        schema_version: schema,
        agent_version: "0.1.0".to_owned(),
        os: "macos".to_owned(),
        arch: "aarch64".to_owned(),
        features,
    };
    if channel.send(&hello).is_err() || schema != SCHEMA_VERSION {
        return;
    }
    let Ok(request) = channel.recv::<Request>() else {
        return;
    };
    requests.lock().unwrap_or_else(PoisonError::into_inner).push(request.clone());
    script(&request, &mut channel);
    channel.close();
}

/// A client directory with a host file of [`ALIAS`] that names the agent of the script.
pub(crate) struct Setup {
    pub(crate) dir: tempfile::TempDir,
    pub(crate) config: Config,
    pub(crate) agent: FakeAgent,
}

impl Setup {
    pub(crate) fn new(script: impl Fn(&Request, &mut Channel) + Send + Sync + 'static) -> Self {
        Self::with_schema(SCHEMA_VERSION, script)
    }

    pub(crate) fn with_schema(schema: u32, script: impl Fn(&Request, &mut Channel) + Send + Sync + 'static) -> Self {
        Self::with_agent(schema, &FEATURES, script)
    }

    /// A setup whose agent has only `features`: none for an agent older than the features.
    pub(crate) fn with_features(features: &[&str], script: impl Fn(&Request, &mut Channel) + Send + Sync + 'static) -> Self {
        Self::with_agent(SCHEMA_VERSION, features, script)
    }

    fn with_agent(schema: u32, features: &[&str], script: impl Fn(&Request, &mut Channel) + Send + Sync + 'static) -> Self {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let config = Config::new(dir.path().join("config"));
        let client = config.identity().expect("the client identity");
        let agent = FakeAgent::start_with(&client, schema, features, script);
        config.write_host(&host_file(&agent, vec![agent.address])).expect("the host file");
        Self { dir, config, agent }
    }

    /// The local checkout of the tests, inside the directory.
    pub(crate) fn checkout(&self) -> PathBuf {
        self.dir.path().join("checkout")
    }
}

/// The host file of [`ALIAS`] for `agent`, with `addresses` cached. The host name resolves to the loopback.
pub(crate) fn host_file(agent: &FakeAgent, addresses: Vec<SocketAddr>) -> HostFile {
    HostFile {
        alias: ALIAS.to_owned(),
        fingerprint: agent.identity.fingerprint(),
        port: agent.address.port(),
        hostname: "127.0.0.1".to_owned(),
        addresses,
        repo: "~/projects/idea-1".to_owned(),
    }
}

/// The lines that a client said, collected for a test.
#[derive(Default)]
pub(crate) struct Said(Mutex<Vec<String>>);

impl Said {
    pub(crate) fn push(&self, line: &str) {
        self.0.lock().unwrap_or_else(PoisonError::into_inner).push(line.to_owned());
    }

    pub(crate) fn lines(&self) -> Vec<String> {
        self.0.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }
}

/// Never pressed.
pub(crate) fn calm() -> u32 {
    0
}

/// A client of the setup with the short deadlines.
pub(crate) fn client<'a>(
    setup: &Setup,
    processes: &'a dyn Processes,
    say: &'a (dyn Fn(&str) + Sync),
    presses: &'a (dyn Fn() -> u32 + Sync),
) -> Client<'a> {
    Client::new(setup.config.clone(), processes, say, presses).with_timing(FAST)
}
