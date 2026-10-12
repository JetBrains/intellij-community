//! The guest, without a VM behind it: [`FakeGuests`] hands out one [`FakeChannel`] per worker, and each answers
//! what the test told it to and records what it was handed.
//!
//! What the suites assert on is mostly argv, and deliberately so: a wrong argv that still exits 0 is the defect a
//! guest double catches and a VM would hide.
//!
//! A connect to a guest port is answered by a connect handler, and recorded as the call `relay <port>`. With no
//! handler, nothing listens: the first read of the stream fails with `ConnectionRefused`, as a real relay to a
//! closed port fails. [`serve_on_connect`] makes a handler over an in-process server.

use std::collections::BTreeMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use avl_base::Refusal;
use avl_base::sync::lock;
use avl_host_sys::{Captured, Channel, Ctx, GuestStream, SpawnOptions};
use avl_wire::verb::AgentVerb;
use tokio::io::DuplexStream;

use crate::answer::{Answer, failed};

#[cfg(test)]
mod tests;

/// What a manager's `channel` dependency is: the channel into a worker by name. The same type as
/// `ChannelFactory` of the controller's `worker::worker`, spelled out because this crate stands below `avl-vm`.
pub type ChannelFactory = Arc<dyn Fn(&str) -> Arc<dyn Channel> + Send + Sync>;

/// One command a [`FakeChannel`] was handed, prefixes and all.
#[derive(Clone, Debug)]
pub struct Call {
    pub worker: String,
    pub argv: Vec<String>,
    pub options: SpawnOptions,
}

impl Call {
    /// The argv joined by spaces.
    pub fn line(&self) -> String {
        self.argv.join(" ")
    }
}

/// How a connect to a guest port is answered: the stream to that port, or the refusal the channel itself gives.
pub type ConnectHandler = Arc<dyn Fn(u16) -> Result<GuestStream, Refusal> + Send + Sync>;

/// The size of the in-process pipe of [`serve_on_connect`]: large enough that a request and its response never
/// wait on each other inside one test.
const SERVED_PIPE_BYTES: usize = 64 * 1024;

/// A connect handler over an in-process server. Each connect makes a `tokio::io::duplex` pipe, hands the server
/// end to `serve`, and answers the client end as the stream. `serve` runs on the connecting task, so a server that
/// must outlive the call spawns its own task.
pub fn serve_on_connect(
    serve: impl Fn(DuplexStream) + Send + Sync + 'static,
) -> impl Fn(u16) -> Result<GuestStream, Refusal> + Send + Sync + 'static {
    move |_port| {
        let (client, server) = tokio::io::duplex(SERVED_PIPE_BYTES);
        serve(server);
        Ok(GuestStream::from_io(client))
    }
}

/// What every channel of a pool shares: the default answer, the default connect handler and the log of every
/// call, in order.
#[derive(Default)]
struct Shared {
    answer: Mutex<Option<Answer>>,
    connect: Mutex<Option<ConnectHandler>>,
    log: Mutex<Vec<Call>>,
}

/// The channel into one worker. Mutex-guarded, because `status` probes workers concurrently.
pub struct FakeChannel {
    worker: String,
    shared: Arc<Shared>,
    answer: Mutex<Option<Answer>>,
    connect: Mutex<Option<ConnectHandler>>,
    calls: Mutex<Vec<Call>>,
}

impl FakeChannel {
    /// How every command into this worker is answered from now on, over the pool's default.
    pub fn answer(&self, answer: impl Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync + 'static) {
        *lock(&self.answer) = Some(Arc::new(answer));
    }

    /// How every connect into this worker is answered from now on, over the pool's handler.
    pub fn on_connect(&self, handler: impl Fn(u16) -> Result<GuestStream, Refusal> + Send + Sync + 'static) {
        *lock(&self.connect) = Some(Arc::new(handler));
    }

    fn record(&self, call: Call) {
        lock(&self.shared.log).push(call.clone());
        lock(&self.calls).push(call);
    }

    /// Every command so far, in order.
    pub fn calls(&self) -> Vec<Call> {
        lock(&self.calls).clone()
    }

    /// Every command so far, each joined by spaces, in order.
    pub fn lines(&self) -> Vec<String> {
        lock(&self.calls).iter().map(Call::line).collect()
    }

    /// Every command so far, joined by spaces, that contains the fragment.
    pub fn calls_containing(&self, fragment: &str) -> Vec<String> {
        self.lines().into_iter().filter(|line| line.contains(fragment)).collect()
    }

    /// Whether any command so far, joined by spaces, contains the fragment.
    pub fn saw_call_containing(&self, fragment: &str) -> bool {
        lock(&self.calls).iter().any(|call| call.line().contains(fragment))
    }

    /// The options of the first command containing the fragment.
    pub fn options_for_call_containing(&self, fragment: &str) -> Option<SpawnOptions> {
        lock(&self.calls)
            .iter()
            .find(|call| call.line().contains(fragment))
            .map(|call| call.options.clone())
    }
}

#[async_trait]
impl Channel for FakeChannel {
    async fn exec(&self, _ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        self.record(Call {
            worker: self.worker.clone(),
            argv: argv.to_vec(),
            options: options.clone(),
        });
        let answer = lock(&self.answer).clone().or_else(|| lock(&self.shared.answer).clone());
        answer.map_or_else(|| Ok(Captured::default()), |answer| answer(argv, options))
    }

    /// Answers this channel's connect handler, else the pool's, else a port where nothing listens.
    async fn connect(&self, _ctx: &Ctx, port: u16) -> Result<GuestStream, Refusal> {
        self.record(Call {
            worker: self.worker.clone(),
            argv: vec![AgentVerb::Relay.as_str().to_owned(), port.to_string()],
            // A connect is a stream and takes no spawn options, so its record names a zero timeout.
            options: SpawnOptions::within(Duration::ZERO),
        });
        let handler = lock(&self.connect).clone().or_else(|| lock(&self.shared.connect).clone());
        handler.map_or_else(
            || {
                Ok(GuestStream::refused(format!(
                    "nothing listens on 127.0.0.1:{port} inside {}",
                    self.worker
                )))
            },
            |handler| handler(port),
        )
    }

    fn worker(&self) -> &str {
        &self.worker
    }
}

/// Every worker's guest. A channel answers its own answer when it has one, else the pool's, else exit 0 with
/// nothing said.
pub struct FakeGuests {
    shared: Arc<Shared>,
    channels: Mutex<BTreeMap<String, Arc<FakeChannel>>>,
    /// Whether a worker outside the pool this was made for is a test defect rather than a new channel.
    strict: bool,
}

impl FakeGuests {
    /// Guests whose channels are created on first use, for any worker name.
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            shared: Arc::default(),
            channels: Mutex::default(),
            strict: false,
        })
    }

    /// Guests of exactly these workers: asking for a channel into any other one panics.
    pub fn of(workers: &[String]) -> Arc<Self> {
        let shared = Arc::<Shared>::default();
        let channels = workers
            .iter()
            .map(|worker| (worker.clone(), Self::create(&shared, worker)))
            .collect();
        Arc::new(Self {
            shared,
            channels: Mutex::new(channels),
            strict: true,
        })
    }

    fn create(shared: &Arc<Shared>, worker: &str) -> Arc<FakeChannel> {
        Arc::new(FakeChannel {
            worker: worker.to_owned(),
            shared: Arc::clone(shared),
            answer: Mutex::default(),
            connect: Mutex::default(),
            calls: Mutex::default(),
        })
    }

    /// How every worker's commands are answered from now on, unless its channel has an answer of its own.
    pub fn answer(&self, answer: impl Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync + 'static) {
        *lock(&self.shared.answer) = Some(Arc::new(answer));
    }

    /// How every worker's connects are answered from now on, unless its channel has a handler of its own.
    pub fn on_connect(&self, handler: impl Fn(u16) -> Result<GuestStream, Refusal> + Send + Sync + 'static) {
        *lock(&self.shared.connect) = Some(Arc::new(handler));
    }

    /// Every guest command fails, which is what a guest whose agent does not answer looks like.
    pub fn fail_everything(&self) {
        self.answer(|_, _| Ok(failed(1, "")));
    }

    /// The channel into one worker.
    pub fn channel(&self, worker: &str) -> Arc<FakeChannel> {
        let mut channels = lock(&self.channels);
        if let Some(channel) = channels.get(worker) {
            return Arc::clone(channel);
        }
        assert!(!self.strict, "{worker} is not a worker of this pool");
        let channel = Self::create(&self.shared, worker);
        channels.insert(worker.to_owned(), Arc::clone(&channel));
        channel
    }

    /// Every channel so far, by worker name.
    pub fn channels(&self) -> Vec<Arc<FakeChannel>> {
        lock(&self.channels).values().cloned().collect()
    }

    /// Every command into any worker so far, in order.
    pub fn calls(&self) -> Vec<Call> {
        lock(&self.shared.log).clone()
    }

    /// Every command into any worker so far, each joined by spaces, in order.
    pub fn lines(&self) -> Vec<String> {
        lock(&self.shared.log).iter().map(Call::line).collect()
    }

    /// Forgets the commands of every worker, so the next case of a test that shares these guests reads only its own.
    /// The answers and the connect handlers stay.
    pub fn forget_calls(&self) {
        lock(&self.shared.log).clear();
        for channel in lock(&self.channels).values() {
            lock(&channel.calls).clear();
        }
    }

    /// This pool's channels as a manager's `channel` dependency.
    pub fn factory(self: &Arc<Self>) -> ChannelFactory {
        let guests = Arc::clone(self);
        Arc::new(move |worker: &str| -> Arc<dyn Channel> { guests.channel(worker) })
    }
}
