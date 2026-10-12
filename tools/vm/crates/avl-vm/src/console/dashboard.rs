//! The drawing layer: one thread that redraws the live region under the scrollback.
//!
//! The terminal stays in cooked mode and no input is read, so Ctrl-C is a SIGINT that the controller handles, and
//! the lease is kept or released as the lease code decides. The cursor is never hidden: it sits on the empty line
//! under the live region, so a SIGINT that ends the process between two frames leaves a usable terminal, and no
//! code has to restore anything.
//!
//! A frame moves the cursor up over the rows the last frame drew, clears to the end of the screen, prints the
//! scrollback lines that became complete, and draws the live region again. Its height changes from frame to
//! frame, and on finish it becomes the verdict card, which stays.

use std::io::Write;
use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use std::time::SystemTime;

use avl_base::report::Renderer;
use avl_base::sync::lock;
use avl_base::{Clock, Scope, SystemClock};
use avl_wire::progress::{Event, Verdict};

use crate::console::paint::ColorChoice;
use crate::console::palette::TerminalColors;
use crate::console::state::State;
use crate::console::view::FRAME_EVERY;

#[cfg(test)]
mod tests;

/// The terminal's height in rows when it does not say. The live region needs about 15.
const FALLBACK_HEIGHT: usize = 40;

/// The narrowest width the dashboard lays itself out for.
const MIN_WIDTH: usize = 40;

/// How the dashboard is set up.
#[derive(Clone, Default)]
pub(crate) struct Options {
    /// How the card spells the controller in a rerun command, such as `./community/tools/vm.cmd`.
    pub rerun_prefix: String,
    /// The terminal's width in columns, until the terminal reports its size.
    pub width: u16,
    pub color: ColorChoice,
    /// The terminal's foreground and background, when it said them; the palette is derived from them.
    pub colors: Option<TerminalColors>,
    /// `NO_COLOR` as the process has it, which [`ColorChoice::Auto`] resolves against; `None` when it is unset or
    /// empty.
    pub no_color: Option<String>,
    /// `TERM` as the process has it: unset, empty or `dumb` gets neither colour nor links under `Auto`.
    pub term: Option<String>,
    /// The clock; `None` is the system's.
    pub clock: Option<Arc<dyn Clock>>,
    /// Polls the size of the process's stderr terminal on every frame. Set it when `out` is that terminal; a test
    /// that draws into a buffer leaves it off and gets `width`.
    pub follow_stderr_size: bool,
}

/// A [`Renderer`] that draws the live dashboard on a terminal.
pub(crate) struct Dashboard {
    shared: Arc<Mutex<Shared>>,
    canvas: Arc<Mutex<Canvas>>,
    clock: Arc<dyn Clock>,
    follow_stderr_size: bool,
    wake: Option<Sender<Wake>>,
    thread: Option<JoinHandle<()>>,
    finished: bool,
}

/// What the reporter and the drawing thread share. The reporter holds the lock only to change the state, and the
/// thread only to take a frame out of it: no write to the terminal happens under it, so a terminal that does not
/// drain its output cannot block a render.
struct Shared {
    state: State,
    width: usize,
    height: usize,
    /// The scrollback lines that became complete and are not printed yet, in publication order.
    pending: Vec<String>,
}

enum Wake {
    Refresh,
    Finish,
}

impl Dashboard {
    /// Starts the dashboard on `out`, normally stderr, which must be a terminal.
    pub(crate) fn start(out: Box<dyn Write + Send>, options: Options) -> Self {
        let clock = options.clock.unwrap_or_else(|| Arc::new(SystemClock));
        let shared = Arc::new(Mutex::new(Shared {
            state: State::new(
                &options.rerun_prefix,
                options
                    .color
                    .resolve(options.no_color.as_deref(), options.term.as_deref(), options.colors),
            ),
            width: usize::from(options.width).max(MIN_WIDTH),
            height: FALLBACK_HEIGHT,
            pending: Vec::new(),
        }));
        let canvas = Arc::new(Mutex::new(Canvas { out, rows: 0 }));
        let (wake, woken) = mpsc::channel();
        let thread = {
            let (shared, canvas, clock) = (shared.clone(), canvas.clone(), clock.clone());
            let follow = options.follow_stderr_size;
            std::thread::Builder::new()
                .name("avl-dashboard".to_owned())
                .spawn(move || {
                    loop {
                        let mut last = match woken.recv_timeout(FRAME_EVERY) {
                            Ok(Wake::Finish) | Err(RecvTimeoutError::Disconnected) => true,
                            Ok(Wake::Refresh) | Err(RecvTimeoutError::Timeout) => false,
                        };
                        // Events arrive in bursts; one frame answers all the wakes that queued behind it.
                        while let Ok(wake) = woken.try_recv() {
                            last |= matches!(wake, Wake::Finish);
                        }
                        draw(&shared, &canvas, clock.as_ref(), follow, last);
                        if last {
                            return;
                        }
                    }
                })
                .ok()
        };
        Self {
            shared,
            canvas,
            clock,
            follow_stderr_size: options.follow_stderr_size,
            wake: thread.is_some().then_some(wake),
            thread,
            finished: false,
        }
    }

    fn now(&self) -> SystemTime {
        SystemTime::from(self.clock.now())
    }

    /// Prints the lines that still wait, turns the live region into the verdict card, and waits until the thread
    /// drew it. Once only.
    fn stop(&mut self) {
        if self.finished {
            return;
        }
        self.finished = true;
        {
            let mut shared = lock(&self.shared);
            let lines = shared.state.drain();
            shared.pending.extend(lines);
        }
        if let Some(wake) = self.wake.take() {
            let _ = wake.send(Wake::Finish);
        }
        match self.thread.take() {
            Some(thread) => {
                let _ = thread.join();
            }
            // No thread could be started, so every frame is drawn by the caller.
            None => draw(&self.shared, &self.canvas, self.clock.as_ref(), self.follow_stderr_size, true),
        }
    }
}

impl Renderer for Dashboard {
    /// Applies one event and queues its scrollback lines for the thread. The reporter calls it with its lock held,
    /// so it only takes the state's lock and never waits for the terminal.
    fn render(&mut self, event: &Event, scope: Option<&Scope>) {
        if self.finished {
            return;
        }
        let now = self.now();
        {
            let mut shared = lock(&self.shared);
            let lines = shared.state.apply(event, scope, now);
            shared.pending.extend(lines);
        }
        match &self.wake {
            // A thread that already exited has drawn its last frame, so there is nobody to wake.
            Some(wake) => {
                let _ = wake.send(Wake::Refresh);
            }
            None => draw(&self.shared, &self.canvas, self.clock.as_ref(), self.follow_stderr_size, false),
        }
    }

    fn finish(&mut self) -> Option<Box<Verdict>> {
        self.stop();
        lock(&self.shared).state.verdict.clone()
    }

    /// Names the run and each trace by its trace viewer link.
    fn set_viewer(&mut self, port: u16, note: &str) {
        let mut shared = lock(&self.shared);
        shared.state.viewer_port = port;
        shared.state.viewer_note = note.to_owned();
    }
}

impl Drop for Dashboard {
    /// A dashboard dropped without a finish (a panic that unwinds through the command) still leaves its lines and
    /// no half-drawn live region.
    fn drop(&mut self) {
        self.stop();
    }
}

/// Draws one frame: the scrollback lines that became complete, then the live region, or the card on the last.
fn draw(shared: &Mutex<Shared>, canvas: &Mutex<Canvas>, clock: &dyn Clock, follow: bool, last: bool) {
    // A cheap ioctl, and outside the state's lock.
    let size = if follow { console::Term::stderr().size_checked() } else { None };
    let now = SystemTime::from(clock.now());
    let (pending, frame) = {
        let mut shared = lock(shared);
        if let Some((rows, columns)) = size {
            shared.height = usize::from(rows).max(1);
            shared.width = usize::from(columns).max(MIN_WIDTH);
        }
        let pending = std::mem::take(&mut shared.pending);
        let frame = if last {
            let card = shared.state.card(shared.width);
            if card.is_empty() {
                card
            } else {
                std::iter::once(String::new()).chain(card).collect()
            }
        } else {
            let mut live = shared.state.live_view(now, shared.width);
            // Rows above the top of the screen cannot be moved back to, so the live region keeps its bottom rows
            // and one row stays for the cursor.
            let fits = shared.height.saturating_sub(1).max(1);
            if live.len() > fits {
                live.drain(..live.len() - fits);
            }
            live
        };
        (pending, frame)
    };
    lock(canvas).paint(&pending, &frame, last);
}

/// The terminal, and how many rows of it the last frame's live region holds under the scrollback.
struct Canvas {
    out: Box<dyn Write + Send>,
    rows: usize,
}

impl Canvas {
    /// Replaces the live region with `frame`, printing `scrollback` above it. A `last` frame stays: the next
    /// write, if any, starts under it.
    fn paint(&mut self, scrollback: &[String], frame: &[String], last: bool) {
        let mut bytes = String::new();
        if self.rows > 0 {
            // The cursor is at the start of the empty line under the region.
            bytes.push_str(&format!("\r\x1b[{}A\x1b[J", self.rows));
        }
        for line in scrollback.iter().chain(frame) {
            bytes.push_str(line);
            bytes.push('\n');
        }
        self.rows = if last { 0 } else { frame.len() };
        // A failed write to a closed terminal has nowhere to be reported.
        let _ = self.out.write_all(bytes.as_bytes());
        let _ = self.out.flush();
    }
}
