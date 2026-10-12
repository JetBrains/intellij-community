//! `air-trace-record`: the sidecar a lane JVM starts, which turns the lane protocol into bundles.
//!
//! It reads [avl_trace::protocol] commands on standard input and answers the acked ones on standard output, one
//! JSON object per line; standard error is for diagnostics only. It owns the capture ladder, the snapshots, the
//! video, the log slice and the bundle writer. Standard input reaching EOF before `done` is the one signal it needs
//! to finish a truncated bundle, which is why it is a process of its own rather than code in the JVM: a watchdog's
//! `exitProcess` kills the JVM, not this.
//!
//! Nothing here may fail a test. Every capture, bridge and encoder failure becomes an `air.trace.error` record or a
//! reason in the manifest, a line the recorder cannot use is answered with a negative ack, and the process exits 0
//! whatever it recorded.
//!
//! The binary has no library: nothing links the recorder, and its process tests are `tests/cli.rs`. The crate's own
//! seams, [Options], exist for one test above all: the replay of the golden lane transcript into
//! the golden bundle. That needs the clock, the machine's names, the capture ladder, the video and the bridge's
//! address to be the test's, and nothing else to be random. The ids already are not: [avl_trace::otlp] derives the
//! trace id from the scenario and the span ids from the lane's.

use std::ffi::OsString;
use std::fmt;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::process::ExitCode;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crossbeam_channel::{Receiver, Sender, bounded, never, select};

/// Writes one diagnostics line formatted like `format!`.
macro_rules! say {
    ($diagnostics:expr, $($argument:tt)*) => {
        $diagnostics.say(format_args!($($argument)*))
    };
}

mod bridge;
mod bundle;
mod capture;
mod frames;
mod idealog;
mod ladder;
mod retention;
mod scenario;
mod session;
mod snap;
mod stills;
mod video;

#[cfg(test)]
mod testing;
#[cfg(test)]
mod tests;

use ladder::{CaptureLadder, SystemLadder, SystemVideographer, Videographer};
use session::Session;

/// What a command line the recorder cannot use exits with: one with any argument, since the recorder takes none.
const EXIT_USAGE: u8 = 2;

/// Runs `air-trace-record`, the whole program behind `main`, and answers its exit code: 0 however the session ended.
///
/// The lane JVM starts the recorder with no argument, so an argument is refused with one `ERROR:` line on `stderr`
/// and [EXIT_USAGE], and no session starts. The lane protocol arrives on `stdin`, the acks leave on `stdout`, and
/// `stderr` takes the diagnostics.
///
/// SIGINT and SIGTERM end the session as truncated, like the end of standard input. SIGPIPE needs nothing: a Rust
/// binary starts with it ignored, so an ack written to a lane that died is a failed write, which the session
/// discards, and the recorder goes on to close the bundle.
fn run(
    args: impl IntoIterator<Item = OsString>,
    stdin: impl Read + Send + 'static,
    stdout: impl Write,
    mut stderr: impl Write + Send + 'static,
) -> u8 {
    if let Some(argument) = args.into_iter().next() {
        // A refusal that cannot be printed has nowhere else to go; the exit code still says what happened.
        let _ = writeln!(
            stderr,
            "ERROR: air-trace-record takes no argument, but got {:?}",
            argument.to_string_lossy()
        );
        return EXIT_USAGE;
    }
    let diagnostics = Diagnostics::new(stderr);
    let (stop, stopped) = bounded(1);
    install_stop_signals(stop, &diagnostics);
    serve(Options::system(diagnostics), stdin, stdout, stopped);
    0
}

fn main() -> ExitCode {
    ExitCode::from(run(std::env::args_os().skip(1), io::stdin(), io::stdout(), io::stderr()))
}

#[cfg(unix)]
fn install_stop_signals(stop: Sender<()>, diagnostics: &Diagnostics) {
    use signal_hook::consts::{SIGINT, SIGTERM};
    match signal_hook::iterator::Signals::new([SIGINT, SIGTERM]) {
        Ok(mut signals) => {
            thread::spawn(move || {
                for _ in signals.forever() {
                    let _ = stop.try_send(());
                }
            });
        }
        Err(error) => say!(
            diagnostics,
            "cannot watch SIGINT and SIGTERM, only the end of the input stops the recorder: {error}"
        ),
    }
}

#[cfg(not(unix))]
fn install_stop_signals(stop: Sender<()>, diagnostics: &Diagnostics) {
    use std::sync::atomic::{AtomicBool, Ordering};

    use signal_hook::consts::{SIGINT, SIGTERM};
    let raised = Arc::new(AtomicBool::new(false));
    for signal in [SIGINT, SIGTERM] {
        if let Err(error) = signal_hook::flag::register(signal, raised.clone()) {
            say!(diagnostics, "cannot watch the signal {signal}: {error}");
        }
    }
    thread::spawn(move || {
        while !raised.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(50));
        }
        let _ = stop.try_send(());
    });
}

/// The clock every receipt, span and record is stamped with.
pub(crate) type Clock = Arc<dyn Fn() -> SystemTime + Send + Sync>;

/// The system clock.
pub(crate) fn system_clock() -> Clock {
    Arc::new(SystemTime::now)
}

/// An instant as epoch milliseconds, the lane's resolution, negative before the epoch.
pub(crate) fn unix_ms(instant: SystemTime) -> i64 {
    match instant.duration_since(UNIX_EPOCH) {
        Ok(after) => i64::try_from(after.as_millis()).unwrap_or(i64::MAX),
        Err(before) => -i64::try_from(before.duration().as_millis()).unwrap_or(i64::MAX),
    }
}

/// Epoch milliseconds as an instant.
#[cfg(test)]
pub(crate) fn from_unix_ms(ms: i64) -> SystemTime {
    if ms >= 0 {
        UNIX_EPOCH + Duration::from_millis(ms.unsigned_abs())
    } else {
        UNIX_EPOCH - Duration::from_millis(ms.unsigned_abs())
    }
}

/// The diagnostics stream: standard error in the real process, a buffer in a test. Lines come from the command
/// loop, the frame loop and the still encoder, hence the lock.
#[derive(Clone)]
pub(crate) struct Diagnostics(Arc<Mutex<dyn Write + Send>>);

impl Diagnostics {
    pub(crate) fn new(writer: impl Write + Send + 'static) -> Self {
        Self(Arc::new(Mutex::new(writer)))
    }

    /// Writes one line, prefixed with whose line it is. A diagnostics stream that fails is not worth a word.
    pub(crate) fn say(&self, message: fmt::Arguments<'_>) {
        let mut writer = lock(&self.0);
        let _ = writeln!(writer, "air-trace-record: {message}");
        let _ = writer.flush();
    }
}

/// Locks a mutex whose holder may have panicked. Every lock of this crate guards state that stays consistent
/// between statements, so a panic elsewhere leaves nothing half-written to refuse.
pub(crate) fn lock<T: ?Sized>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

/// Everything a session takes from its surroundings.
pub(crate) struct Options {
    pub(crate) clock: Clock,
    /// The resource's `host.name` and `os.type`.
    pub(crate) host_name: String,
    pub(crate) os_type: String,
    /// The base URL of the bridge a hello names.
    pub(crate) bridge_url: Box<dyn Fn(u16) -> String + Send>,
    pub(crate) ladder: Box<dyn CaptureLadder>,
    pub(crate) video: Box<dyn Videographer>,
    /// The raw video's tick.
    pub(crate) frame_interval: Duration,
    /// Bounds one snapshot's grab, tree and wait for the still writer; see [DEFAULT_SNAP_BUDGET].
    pub(crate) snap_budget: Duration,
    pub(crate) diagnostics: Diagnostics,
}

/// The snapshot bound of the real process. The lane waits two seconds for a snapshot's ack; the recorder's own
/// bound is shorter, so that even a bridge that answers at the last moment gets its ack back in time and the lane
/// never has to discard a late one.
///
/// A replay that feeds a whole transcript in a few milliseconds sets a far longer one. It snaps faster than any lane
/// can, so its stills queue up behind the encoder, and under a loaded test machine a real-time bound would drop one
/// of them at random.
pub(crate) const DEFAULT_SNAP_BUDGET: Duration = Duration::from_millis(1800);

impl Options {
    /// The options of the real process: the system clock, this machine, the full capture ladder, and a video by the
    /// recorder's own encoder, or by the machine's ffmpeg for a Mac's screen.
    fn system(diagnostics: Diagnostics) -> Self {
        let clock = system_clock();
        let os_type = os_type().to_owned();
        Self {
            host_name: host_name(),
            bridge_url: Box::new(bridge::url),
            ladder: Box::new(SystemLadder::new(diagnostics.clone(), clock.clone(), os_type.clone())),
            video: Box::new(SystemVideographer::new(os_type.clone(), clock.clone())),
            frame_interval: video::FRAME_INTERVAL,
            snap_budget: DEFAULT_SNAP_BUDGET,
            os_type,
            clock,
            diagnostics,
        }
    }
}

/// This machine's `os.type` as the bundles spell it, which is Go's `GOOS`: `darwin`, not `macos`.
fn os_type() -> &'static str {
    match std::env::consts::OS {
        "macos" => "darwin",
        other => other,
    }
}

/// This machine's name, `unknown` when it cannot be read. std has no `gethostname`, and this crate no crate for it.
fn host_name() -> String {
    let name = if cfg!(target_os = "linux") {
        std::fs::read_to_string("/proc/sys/kernel/hostname").ok()
    } else if cfg!(windows) {
        std::env::var("COMPUTERNAME").ok()
    } else {
        std::process::Command::new("hostname")
            .output()
            .ok()
            .filter(|output| output.status.success())
            .and_then(|output| String::from_utf8(output.stdout).ok())
    };
    name.map(|name| name.trim().to_owned())
        .filter(|name| !name.is_empty())
        .unwrap_or_else(|| "unknown".to_owned())
}

/// One line of standard input and when it arrived.
struct Line {
    content: Vec<u8>,
    at: SystemTime,
}

/// Serves one session: every line of `input` until EOF or until `stop` fires, then the open scenario is finished as
/// truncated.
///
/// Lines are read on a thread of their own and stamped there, as they arrive, so a line waiting behind a slow
/// snapshot still carries the moment the lane sent it. A dropped `stop` sender stops nothing.
pub(crate) fn serve(options: Options, input: impl Read + Send + 'static, acks: impl Write, stop: Receiver<()>) {
    let (lines, received) = bounded(64);
    let clock = options.clock.clone();
    let diagnostics = options.diagnostics.clone();
    thread::spawn(move || read_lines(input, &clock, &lines, &diagnostics));
    let mut session = Session::new(options, acks);
    let mut stop = stop;
    loop {
        select! {
            recv(stop) -> signal => {
                if signal.is_ok() {
                    session.close("the recorder was told to stop");
                    return;
                }
                stop = never();
            }
            recv(received) -> line => if let Ok(line) = line {
                session.handle(&line.content, line.at);
            } else {
                session.close("the lane closed the recorder's input");
                return;
            },
        }
    }
}

fn read_lines(input: impl Read, clock: &Clock, lines: &Sender<Line>, diagnostics: &Diagnostics) {
    let mut reader = BufReader::with_capacity(64 << 10, input);
    loop {
        let mut content = Vec::new();
        let read = reader.read_until(b'\n', &mut content);
        if !content.is_empty() {
            while content.last().is_some_and(|byte| matches!(byte, b'\n' | b'\r')) {
                content.pop();
            }
            if lines.send(Line { content, at: clock() }).is_err() {
                return;
            }
        }
        match read {
            Ok(0) => return,
            Ok(_) => {}
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => {
                say!(diagnostics, "reading the lane's input: {error}");
                return;
            }
        }
    }
}

/// Joins reasons with `; `, skipping the empty ones.
pub(crate) fn join_reasons<'a>(reasons: impl IntoIterator<Item = &'a str>) -> String {
    reasons
        .into_iter()
        .filter(|reason| !reason.is_empty())
        .collect::<Vec<_>>()
        .join("; ")
}
