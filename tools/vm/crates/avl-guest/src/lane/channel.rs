//! The control channel of the lane daemon, and the verb `lane-daemon` that serves it in the run slot of a worker.
//!
//! The controller starts the verb through the run supervisor and reaches it through the relay. The channel speaks
//! [`avl_wire::daemon::PROTOCOL_VERSION`]: the routes of [`avl_wire::daemon::match_route`], the token of the state file
//! in `X-Air-Daemon-Token`, one run at a time, and a chunked NDJSON stream on `/run`.
//!
//! At its boot the verb reads the launch inputs from the runtime descriptor. On a Linux guest it then proves the shared
//! objects of the distribution. It makes its directories, empties its jar store, binds the channel, and then publishes
//! the state file `daemon.json`: a temporary file with the mode 0600, renamed. The boot poll of the controller reads
//! it. The lane services and the lane IDE belong to the [`IdeOwner`]. `/shutdown` refuses new runs, waits for the run
//! in flight up to [`WEDGE_GRACE`], closes the lane services, and leaves the IDE running. A run that outlives the grace
//! ends the verb with [`EXIT_WEDGED`].

use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::SocketAddr;
use std::os::unix::fs::{DirBuilderExt, OpenOptionsExt};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Condvar, Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant};

use avl_wire::daemon::{self as wire, Conflict, MissingJars, PROTOCOL_VERSION, Route, RunRequest, StateFile, Status, conflict};
use serde::Serialize;
use serde_json::json;

use super::ide::{IdeOwner, Records};
use super::inputs::{self, LaunchInputs};
use super::iteration::{self, stored_path};
use super::package::sha256_hex;
use super::services::http::{Request, Response};
use super::services::{Handler, Service};
use super::watchdog::Watch;
use crate::cli::LaneDaemonArgs;
use crate::linux::validate::check_shared_objects;
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::step::{Runner, SystemRunner};

#[cfg(test)]
mod tests;

/// The header of the token, the controller's `TOKEN_HEADER`.
pub(crate) const TOKEN_HEADER: &str = "X-Air-Daemon-Token";

/// The state file in the state directory.
pub(crate) const STATE_FILE: &str = "daemon.json";

/// The refusal code of a boot that cannot prepare its state.
pub(crate) const BOOT_CODE: &str = "lane_daemon_boot";

/// How long `/shutdown` waits for the run in flight.
pub(crate) const WEDGE_GRACE: Duration = Duration::from_mins(1);

/// The exit status of a verb whose run outlived [`WEDGE_GRACE`] after `/shutdown`.
pub(crate) const EXIT_WEDGED: u8 = 3;

/// The largest file of the jar push. A context plugin file is the largest, and every one is below it.
const MAX_JAR_BYTES: usize = 512 << 20;
/// The variables of the run environment that name a directory of the run: the scratch directory and the outputs
/// directory. On a worker the controller names both below the scratch directory of the run, and the boot makes them.
/// A Bazel test has them already.
const RUN_DIRECTORY_VARIABLES: [&str; 2] = ["TEST_TMPDIR", "TEST_UNDECLARED_OUTPUTS_DIR"];

/// Writes one line of the log of the lane daemon on standard error, which the run supervisor keeps as `run.log`. Each
/// line starts with the time, so a reader sees where a run spent its time.
pub(crate) fn log(text: &str) {
    eprintln!("{} lane daemon: {text}", crate::clock::now_stamp());
}

/// The state of one lane daemon, which every connection thread shares.
pub(crate) struct Daemon {
    state_dir: PathBuf,
    token: String,
    boot_stamp: String,
    launch_digest: String,
    port: u16,
    busy: AtomicBool,
    shutting_down: AtomicBool,
    iterations: AtomicU32,
    product_stamp: Mutex<String>,
    ide_running: AtomicBool,
    owner: Mutex<IdeOwner>,
    shutdown: (Mutex<bool>, Condvar),
    /// The watch of the run in flight, which the monitor of `lane-run` reads. No lock of the run thread guards it.
    watch: Mutex<Option<Arc<Watch>>>,
}

impl Daemon {
    /// A daemon over the state directory `state_dir`, whose channel is on `port`.
    pub(crate) fn new(state_dir: PathBuf, launch_digest: String, port: u16, inputs: LaunchInputs) -> Self {
        Self {
            state_dir,
            token: random_hex(),
            boot_stamp: random_hex(),
            launch_digest,
            port,
            busy: AtomicBool::new(false),
            shutting_down: AtomicBool::new(false),
            iterations: AtomicU32::new(0),
            product_stamp: Mutex::new(String::new()),
            ide_running: AtomicBool::new(false),
            owner: Mutex::new(IdeOwner::new(inputs)),
            shutdown: (Mutex::new(false), Condvar::new()),
            watch: Mutex::new(None),
        }
    }

    /// Names `watch` as the watch of the run in flight, or no watch when the run ended.
    pub(crate) fn publish_watch(&self, watch: Option<Arc<Watch>>) {
        *self.watch.lock().unwrap_or_else(PoisonError::into_inner) = watch;
    }

    /// The watch of the run in flight, or `None` between runs.
    pub(crate) fn watch(&self) -> Option<Arc<Watch>> {
        self.watch.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(crate) fn boot_stamp(&self) -> &str {
        &self.boot_stamp
    }

    /// The port of the channel, which a derived port of a lane service never takes.
    pub(crate) const fn port(&self) -> u16 {
        self.port
    }

    /// Whether the IDE ran when the owner last looked: a status read never waits for a launch.
    pub(crate) fn ide_running(&self) -> bool {
        self.ide_running.load(Ordering::SeqCst)
    }

    /// The next iteration id, `iter-<boot>-<n>`: one path segment, which `/results` reads.
    pub(crate) fn next_iteration_id(&self) -> String {
        let count = self.iterations.fetch_add(1, Ordering::SeqCst) + 1;
        format!("iter-{}-{count}", &self.boot_stamp[..8])
    }

    /// The directory of one iteration, which holds its `test.xml` and its traces.
    pub(crate) fn iteration_dir(&self, iteration_id: &str) -> PathBuf {
        self.state_dir.join("iterations").join(iteration_id)
    }

    fn jars(&self) -> PathBuf {
        self.state_dir.join("jars")
    }

    /// The file that the push stored under `sha256`, or why there is none.
    pub(crate) fn stored(&self, sha256: &str) -> Result<PathBuf, String> {
        let path = stored_path(&self.jars(), sha256);
        if wire_digest(sha256) && path.is_file() {
            Ok(path)
        } else {
            Err(format!(
                "the push holds no file {sha256}; push the lane inputs and the plugin files before the run"
            ))
        }
    }

    /// Stores the file `path` under its digest, as a push of it does, and answers the digest. A run with no channel
    /// fills the jar store from its runfiles this way.
    pub(crate) fn store_file(&self, path: &Path) -> Result<String, AgentRefusal> {
        let bytes = fs::read(path).map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot read {}: {error}", path.display())))?;
        let digest = sha256_hex(&bytes);
        let target = stored_path(&self.jars(), &digest);
        if !target.is_file() {
            fs::write(&target, &bytes)
                .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot store {}: {error}", target.display())))?;
        }
        Ok(digest)
    }

    /// Quits the lane IDE and stops the lane services. A run with no channel ends this way, so it leaves no process.
    pub(crate) fn finish(&self) -> Result<(), AgentRefusal> {
        let quit = self.with_ide(|owner| {
            owner.quit(&mut Records(&mut |record| {
                if let wire::RunEventKind::Output(output) = record {
                    log(&output.text);
                }
            }))
        });
        self.lock_owner().close_services();
        quit
    }

    /// Runs `work` with the IDE owner, and then stores whether the IDE runs.
    pub(crate) fn with_ide<T>(&self, work: impl FnOnce(&mut IdeOwner) -> T) -> T {
        let mut owner = self.lock_owner();
        let result = work(&mut owner);
        self.ide_running.store(owner.is_running(), Ordering::SeqCst);
        result
    }

    fn lock_owner(&self) -> MutexGuard<'_, IdeOwner> {
        self.owner.lock().unwrap_or_else(PoisonError::into_inner)
    }

    fn status(&self) -> Status {
        Status {
            protocol_version: PROTOCOL_VERSION,
            daemon_boot_stamp: self.boot_stamp.clone(),
            controller_launch_digest: self.launch_digest.clone(),
            product_stamp: self.product_stamp.lock().unwrap_or_else(PoisonError::into_inner).clone(),
            ide_running: self.ide_running(),
            busy: self.busy.load(Ordering::SeqCst),
            iteration_count: self.iterations.load(Ordering::SeqCst),
            pid: i64::from(std::process::id()),
        }
    }

    /// Asks the main thread of the verb to shut down.
    fn request_shutdown(&self) {
        self.shutting_down.store(true, Ordering::SeqCst);
        let (requested, signal) = &self.shutdown;
        *requested.lock().unwrap_or_else(PoisonError::into_inner) = true;
        signal.notify_all();
    }

    /// Blocks until `/shutdown`.
    fn await_shutdown(&self) {
        let (requested, signal) = &self.shutdown;
        let mut requested = requested.lock().unwrap_or_else(PoisonError::into_inner);
        while !*requested {
            requested = signal.wait(requested).unwrap_or_else(PoisonError::into_inner);
        }
    }
}

/// Serves the control channel until `/shutdown`, and answers the exit status of the verb. `variable` reads the run
/// environment.
pub(crate) fn serve(args: &LaneDaemonArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<u8, AgentRefusal> {
    let inputs = inputs::read(args)?;
    prove_shared_objects(&inputs, &mut SystemRunner, cfg!(target_os = "linux"))?;
    let daemon = Arc::new(boot(args, inputs, variable)?);
    let channel = bind(&daemon, SocketAddr::new(args.bind, args.port))?;
    publish_state_file(&daemon, channel.address().port())?;
    log(&format!(
        "listening on {} (controller launch digest {})",
        channel.address(),
        daemon.launch_digest
    ));
    daemon.await_shutdown();
    let deadline = Instant::now() + WEDGE_GRACE;
    while daemon.busy.load(Ordering::SeqCst) {
        if Instant::now() >= deadline {
            log(&format!("the run outlived the shutdown grace of {} s", WEDGE_GRACE.as_secs()));
            return Ok(EXIT_WEDGED);
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    daemon.lock_owner().close_services();
    drop(channel);
    log("shut down; the lane IDE keeps running");
    Ok(0)
}

/// Proves on a Linux guest that the guest has each library that a shared object of the distribution links against.
/// It runs [`check_shared_objects`] over the distribution home before the boot makes anything. A missing library
/// refuses the boot with `linux_shared_object_unresolved`. The log names how many objects `ldd` read and how long the
/// sweep took. `lane-run` runs the same sweep in its host probe, so it does not call this.
///
/// `linux` is false on a macOS guest, and then the boot sweeps nothing.
pub(crate) fn prove_shared_objects(inputs: &LaunchInputs, runner: &mut impl Runner, linux: bool) -> Result<(), AgentRefusal> {
    if !linux {
        return Ok(());
    }
    let started = Instant::now();
    let swept = check_shared_objects(runner, &inputs.dist_home)?;
    log(&format!(
        "ldd read {swept} shared objects of {} in {} ms, and none needs a library that the guest does not have",
        inputs.dist_home.display(),
        started.elapsed().as_millis()
    ));
    Ok(())
}

/// The daemon of `args` over an empty jar store and a fresh state file.
///
/// The boot makes each directory that the daemon and the lane IDE write into, readable only by this account: the jar
/// store, the iterations, the home, the IDE root, and each directory of [`RUN_DIRECTORY_VARIABLES`] that `variable`
/// names. A directory that exists keeps its mode.
pub(crate) fn boot(args: &LaneDaemonArgs, inputs: LaunchInputs, variable: &dyn Fn(&str) -> Option<String>) -> Result<Daemon, AgentRefusal> {
    let daemon = Daemon::new(args.state_dir.clone(), args.launch_digest.clone(), args.port, inputs);
    let failure = |what: &str, path: &Path, error: std::io::Error| {
        AgentRefusal::failure(BOOT_CODE, format!("cannot {what} {}: {error}", path.display()))
    };
    let jars = daemon.jars();
    match fs::remove_dir_all(&jars) {
        Err(error) if error.kind() != std::io::ErrorKind::NotFound => return Err(failure("empty", &jars, error)),
        _ => {}
    }
    let run_directories = RUN_DIRECTORY_VARIABLES
        .iter()
        .filter_map(|name| variable(name))
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let directories = [jars, args.state_dir.join("iterations"), args.home.clone(), args.ide_root.clone()]
        .into_iter()
        .chain(run_directories);
    for directory in directories {
        fs::DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&directory)
            .map_err(|error| failure("create", &directory, error))?;
    }
    let state_file = args.state_dir.join(STATE_FILE);
    match fs::remove_file(&state_file) {
        Err(error) if error.kind() != std::io::ErrorKind::NotFound => return Err(failure("delete", &state_file, error)),
        _ => {}
    }
    Ok(daemon)
}

/// Binds the channel of `daemon` on `address`.
pub(crate) fn bind(daemon: &Arc<Daemon>, address: SocketAddr) -> Result<Service<Channel>, AgentRefusal> {
    let daemon = Arc::clone(daemon);
    Service::bind("daemon", address, move |_| Channel { daemon })
}

/// Publishes the state file of `daemon` for the channel on `port`: a temporary file with the mode 0600, renamed, so a
/// reader sees a whole document or no file.
pub(crate) fn publish_state_file(daemon: &Daemon, port: u16) -> Result<(), AgentRefusal> {
    let state = StateFile {
        protocol_version: PROTOCOL_VERSION,
        port,
        token: daemon.token.clone(),
        pid: i64::from(std::process::id()),
        daemon_boot_stamp: daemon.boot_stamp.clone(),
    };
    let target = daemon.state_dir.join(STATE_FILE);
    let temporary = daemon.state_dir.join(format!(".{STATE_FILE}.{}", std::process::id()));
    let encoded = serde_json::to_vec(&state).map_err(AgentRefusal::internal)?;
    let written = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(&temporary)
        .and_then(|mut file| file.write_all(&encoded))
        .and_then(|()| fs::rename(&temporary, &target));
    written.map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot publish {}: {error}", target.display())))
}

/// The handler of the control channel.
pub(crate) struct Channel {
    daemon: Arc<Daemon>,
}

impl Handler for Channel {
    const MAX_BODY_BYTES: usize = MAX_JAR_BYTES;

    fn handle(&self, request: &Request) -> Response {
        if request.header(TOKEN_HEADER) != Some(self.daemon.token.as_str()) {
            return error(401, "bad token");
        }
        match wire::match_route(&request.method, &request.path) {
            Some(Route::Status) => answer(200, &self.daemon.status()),
            Some(Route::MissingJars) => self.missing_jars(request),
            Some(Route::UploadJar) => self.upload_jar(request),
            Some(Route::Run) => self.run(request),
            Some(Route::IdeStop) => self.ide_stop(),
            Some(Route::Shutdown) => {
                self.daemon.request_shutdown();
                answer(200, &json!({ "shuttingDown": true }))
            }
            Some(Route::Results) => self.results(&request.path),
            None => error(404, &format!("unknown endpoint {} {}", request.method, request.path)),
        }
    }
}

impl Channel {
    fn missing_jars(&self, request: &Request) -> Response {
        #[derive(serde::Deserialize)]
        struct Offered {
            jars: Vec<String>,
        }
        let Ok(offered) = serde_json::from_slice::<Offered>(&request.body) else {
            return error(400, "the /jars body is not {\"jars\": [...]}");
        };
        let jars = self.daemon.jars();
        let missing = offered
            .jars
            .into_iter()
            .filter(|digest| !(wire_digest(digest) && stored_path(&jars, digest).is_file()))
            .collect();
        answer(200, &MissingJars { missing })
    }

    /// Stores one pushed file under its digest, after it checked the digest of the bytes.
    fn upload_jar(&self, request: &Request) -> Response {
        let digest = request.path.trim_start_matches("/jars/");
        let actual = sha256_hex(&request.body);
        if actual != digest {
            return error(400, &format!("the pushed bytes have the digest {actual}, not {digest}"));
        }
        let jars = self.daemon.jars();
        let target = stored_path(&jars, digest);
        let temporary = jars.join(format!(".{digest}.incoming"));
        let stored = fs::write(&temporary, &request.body).and_then(|()| fs::rename(&temporary, &target));
        match stored {
            Ok(()) => answer(200, &json!({ "sha256": digest })),
            Err(failure) => error(500, &format!("cannot store {}: {failure}", target.display())),
        }
    }

    fn run(&self, request: &Request) -> Response {
        let daemon = &self.daemon;
        if daemon.shutting_down.load(Ordering::SeqCst) {
            return conflict_answer(conflict::SHUTTING_DOWN, None);
        }
        let body: RunRequest = match serde_json::from_slice(&request.body) {
            Ok(body) => body,
            Err(failure) => return error(400, &format!("the /run body is not a run request: {failure}")),
        };
        if body.launch_digest != daemon.launch_digest {
            return conflict_answer(conflict::STALE_DAEMON, Some(daemon.launch_digest.clone()));
        }
        if daemon
            .busy
            .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
        {
            return conflict_answer(conflict::BUSY, None);
        }
        let busy = BusyGuard(Arc::clone(daemon));
        *daemon.product_stamp.lock().unwrap_or_else(PoisonError::into_inner) = body.product_stamp.clone();
        Response::streamed(
            200,
            "application/x-ndjson",
            Box::new(move |chunks| {
                // The XML is in the iteration directory, which `/results` serves.
                iteration::run(&busy.0, &body, chunks);
                drop(busy);
                Ok(())
            }),
        )
    }

    fn ide_stop(&self) -> Response {
        let daemon = &self.daemon;
        if daemon
            .busy
            .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
        {
            return conflict_answer(conflict::BUSY, None);
        }
        let _busy = BusyGuard(Arc::clone(daemon));
        let stopped = daemon.with_ide(|owner| {
            owner.quit(&mut Records(&mut |record| {
                if let wire::RunEventKind::Output(output) = record {
                    log(&output.text);
                }
            }))
        });
        match stopped {
            Ok(()) => answer(200, &json!({ "ideRunning": daemon.ide_running() })),
            Err(refusal) => error(500, &format!("{}: {}", refusal.code, refusal.message)),
        }
    }

    fn results(&self, path: &str) -> Response {
        let iteration = path
            .strip_prefix("/results/")
            .and_then(|rest| rest.strip_suffix(&format!("/{}", iteration::TEST_XML_FILE)))
            .unwrap_or_default();
        let file = self.daemon.iteration_dir(iteration).join(iteration::TEST_XML_FILE);
        match fs::read(&file) {
            Ok(content) => Response::with_body(200, "application/xml", content),
            Err(_) => error(404, "no such result file"),
        }
    }
}

/// Clears the busy flag when the run that set it ends, or when its answer is dropped before its writer ran.
struct BusyGuard(Arc<Daemon>);

impl Drop for BusyGuard {
    fn drop(&mut self) {
        self.0.busy.store(false, Ordering::SeqCst);
    }
}

fn answer(status: u16, value: &impl Serialize) -> Response {
    let body = serde_json::to_vec(value).unwrap_or_default();
    Response::with_body(status, "application/json", body)
}

/// A refusal of the channel. Every answer is JSON, so the controller decodes each one against a declared shape.
fn error(status: u16, message: &str) -> Response {
    answer(status, &json!({ "error": message }))
}

fn conflict_answer(reason: &str, launch_digest: Option<String>) -> Response {
    answer(
        409,
        &Conflict {
            reason: reason.to_owned(),
            launch_digest,
        },
    )
}

fn wire_digest(text: &str) -> bool {
    avl_wire::digest::is_sha256_hex(text)
}

/// 32 hex digits from the random source of the operating system, for the token and the boot stamp.
fn random_hex() -> String {
    let mut bytes = [0_u8; 16];
    let read = fs::File::open("/dev/urandom").and_then(|mut source| source.read_exact(&mut bytes));
    if read.is_err() {
        // The guest has a random source; without one, the time and the pid keep two boots apart.
        let seed = format!("{:?}|{}", std::time::SystemTime::now(), std::process::id());
        return sha256_hex(seed.as_bytes())[..32].to_owned();
    }
    hex::encode(bytes)
}
