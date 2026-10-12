//! What the viewer is told without asking: `runs-changed` when a scan's listing differs from the last one,
//! `bundle-append` when a running directory bundle's JSON Lines grow, and `run-appended` when a controller run's
//! journal grows.
//!
//! The watches are not recursive, so every directory under the roots is watched on its own, except inside a
//! bundle: a finished one never changes, and a running one changes only in its own files. The watcher is an
//! accelerator, never the source of truth. A periodic scan still runs, because a watch can be refused (kqueue holds
//! a descriptor per file of every watched directory and inotify has a per-user budget, so the count is capped) and
//! because a bundle going stale is only the clock moving.
//!
//! The watcher lives on a thread of its own, and no scan, request or shutdown waits for it. On macOS every change
//! of the watched set stops and restarts the FSEvents stream, and both are synchronous calls to the system's
//! fseventsd, which take seconds on a loaded machine.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::File;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, Weak};

use avl_base::journal::FILE_NAME as JOURNAL_FILE;
use avl_base::sync::lock;
use avl_trace::bundle::{LOGS_FILE, MANIFEST_FILE, SPANS_FILE};
use avl_trace_tools::discover::{SourceKind, Status};
use notify::event::{AccessKind, AccessMode, ModifyKind};
use notify::{EventKind, RecursiveMode};
use serde::Serialize;
use tokio::sync::mpsc;
use tokio::time::{Instant, interval_at, sleep};
use tokio_util::sync::CancellationToken;

use super::read_at_most;
use super::state::{Server, Snap, Watch};

/// One event for the open event streams: its name and its JSON data.
#[derive(Clone, Debug)]
pub(crate) struct Frame {
    pub event: &'static str,
    pub data: Arc<str>,
}

/// One `bundle-append`: complete lines appended to one file of a running bundle. The viewer reads the id, and asks the
/// bundle again.
#[derive(Serialize, Debug)]
pub(crate) struct AppendEvent<'a> {
    pub id: &'a str,
    pub file: &'a str,
    pub lines: Vec<String>,
}

/// The directories one scan wants watched, numbered in the order the scans asked.
type WatchRequest = (u64, Vec<PathBuf>);

/// Where scans hand the directories they want watched to the watcher thread, and how far that thread got.
///
/// The thread owns the watcher, so a scan sends and goes on, and the watch loop ends the thread by dropping the
/// sender, without joining it.
#[derive(Default)]
pub(crate) struct Watches {
    sender: Mutex<Option<std::sync::mpsc::Sender<WatchRequest>>>,
    requested: AtomicU64,
    applied: AtomicU64,
}

/// The platform's own file watcher: FSEvents on macOS, inotify on Linux.
pub(crate) fn native_watch() -> Watch {
    Arc::new(|events| {
        let watcher = notify::recommended_watcher(move |event| {
            // The loop is gone when this fails, and then nobody wants the event.
            let _ = events.send(event);
        })?;
        Ok(Box::new(watcher))
    })
}

/// How far each JSON Lines file of one running bundle has been announced.
pub(crate) struct Tail {
    dir: PathBuf,
    offsets: HashMap<&'static str, u64>,
}

/// The files a running bundle grows line by line.
const TAILED_FILES: [&str; 2] = [SPANS_FILE, LOGS_FILE];

/// Bounds one `bundle-append`; a burst larger than this goes out as several.
const MAX_APPEND_BYTES: usize = 1 << 20;

/// Bounds one read of a tailed file, so a file replaced by a large one is caught up over several polls rather than
/// read whole at once.
const MAX_POLL_BYTES: u64 = 16 << 20;

impl Server {
    /// Whether the watcher thread runs and has applied the last directories a scan sent it.
    #[cfg(test)]
    pub(crate) fn watches_settled(&self) -> bool {
        let requested = self.watches.requested.load(Ordering::SeqCst);
        lock(&self.watches.sender).is_some() && requested > 0 && self.watches.applied.load(Ordering::SeqCst) == requested
    }

    /// Hands the directories a scan wants watched to the watcher thread, and does not wait for it; nothing when
    /// there is no watcher.
    pub(crate) fn request_watches(&self, wanted: Vec<PathBuf>) {
        let sender = lock(&self.watches.sender);
        let Some(sender) = sender.as_ref() else {
            return;
        };
        let number = self.watches.requested.fetch_add(1, Ordering::SeqCst) + 1;
        // The thread ends only after the watch loop dropped the sender, and then nobody wants the watches.
        let _ = sender.send((number, wanted));
    }

    /// Moves the watcher to a thread of its own, which applies what the scans ask for until the watch loop ends.
    fn start_watcher_thread(self: &Arc<Self>, watcher: Box<dyn notify::Watcher + Send>) {
        let (requests, received) = std::sync::mpsc::channel();
        let server = Arc::downgrade(self);
        let max_watches = self.settings.max_watches;
        let spawned = std::thread::Builder::new()
            .name("air-trace-watcher".to_owned())
            // The watcher is dropped on this thread once the loop ends, so the last stop of its stream waits here.
            .spawn(move || {
                let mut watcher = watcher;
                keep_watching(&server, watcher.as_mut(), &received, max_watches);
            });
        match spawned {
            Ok(_detached) => *lock(&self.watches.sender) = Some(requests),
            Err(error) => (self.settings.log)(&format!(
                "air-trace serve: no file watcher thread ({error}); rescanning every {:?} instead",
                self.settings.rescan_interval
            )),
        }
    }

    /// Starts a tail for every running directory bundle, at the end of its last complete line, and ends the tails
    /// of bundles no longer running.
    ///
    /// Starting at the end is what makes the stream and a fetch fit together: the tail starts at the scan, the
    /// listing that names the bundle comes from that scan, and a viewer fetches the file only after reading the
    /// listing. So every byte is either in the fetch or in an event, and [AppendEvent::offset] removes the overlap.
    pub(crate) fn update_tails(&self, current: &Snap) {
        let mut tails = lock(&self.tails);
        let running: BTreeMap<&str, &Path> = current
            .found
            .bundles
            .iter()
            .filter(|found| found.source.kind == SourceKind::Dir && found.status == Status::Running)
            .filter_map(|found| match &found.location {
                avl_trace_tools::discover::Location::Dir(dir) => Some((found.id.as_str(), dir.as_path())),
                _ => None,
            })
            .collect();
        tails.retain(|id, _| running.contains_key(id.as_str()));
        for (id, dir) in running {
            if tails.contains_key(id) {
                continue;
            }
            let offsets = TAILED_FILES.iter().map(|name| (*name, complete_length(&dir.join(name)))).collect();
            tails.insert(
                id.to_owned(),
                Tail {
                    dir: dir.to_path_buf(),
                    offsets,
                },
            );
        }
    }

    /// Announces every complete line appended to a tailed file since the last poll.
    pub(crate) fn poll_tails(&self) {
        let mut tails = lock(&self.tails);
        for (id, tail) in tails.iter_mut() {
            for name in TAILED_FILES {
                let offset = tail.offsets.get(name).copied().unwrap_or(0);
                let Ok(appended) = read_appended(&tail.dir.join(name), offset) else {
                    continue;
                };
                let mut batch: Vec<String> = Vec::new();
                let mut size = 0;
                for line in appended.lines {
                    if size > 0 && size + line.len() + 1 > MAX_APPEND_BYTES {
                        self.publish_append(id, name, std::mem::take(&mut batch));
                        size = 0;
                    }
                    size += line.len() + 1;
                    batch.push(String::from_utf8_lossy(&line).into_owned());
                }
                if !batch.is_empty() {
                    self.publish_append(id, name, batch);
                }
                tail.offsets.insert(name, appended.next);
            }
        }
    }

    fn publish_append(&self, id: &str, file: &str, lines: Vec<String>) {
        self.publish("bundle-append", &AppendEvent { id, file, lines });
    }

    /// Says `run-appended` for a run whose journal size differs from the size last announced, and answers whether
    /// it did.
    pub(crate) fn announce_journal(&self, run_id: &str) -> bool {
        let Some(path) = self.journals.journal_path(run_id) else {
            return false;
        };
        let Ok(info) = std::fs::metadata(path) else {
            return false;
        };
        if !self.journals.observe(run_id, info.len()) {
            return false;
        }
        self.publish("run-appended", &serde_json::json!({ "runId": run_id }));
        true
    }

    /// Announces every running run whose journal grew, and then asks for a scan, which reads the new counts into the
    /// listing.
    pub(crate) fn poll_journals(&self) {
        let mut grew = false;
        for run_id in self.journals.running() {
            grew |= self.announce_journal(&run_id);
        }
        if grew {
            self.rescan_soon.notify_one();
        }
    }

    /// Scans on file events, debounced, and on a timer, until cancel ends.
    pub(crate) async fn watch_loop(self: Arc<Self>, cancel: CancellationToken) {
        let (sender, mut events) = mpsc::unbounded_channel();
        match (self.settings.watch)(sender) {
            Ok(watcher) => self.start_watcher_thread(watcher),
            Err(error) => (self.settings.log)(&format!(
                "air-trace serve: no file watcher ({error}); rescanning every {:?} instead",
                self.settings.rescan_interval
            )),
        }
        self.blocking(Self::scan).await;

        // Disarmed until a change arms it; a disarmed timer is never polled.
        let debounce = sleep(self.settings.debounce);
        tokio::pin!(debounce);
        let mut armed = false;
        let rescan_every = self.settings.rescan_interval;
        let tail_every = self.settings.tail_interval;
        let mut rescan = interval_at(Instant::now() + rescan_every, rescan_every);
        let mut tail = interval_at(Instant::now() + tail_every, tail_every);
        let mut reported = false;
        loop {
            tokio::select! {
                () = cancel.cancelled() => break,
                Some(event) = events.recv() => match event {
                    Ok(event) => match classify(&event) {
                        Change::Tail => self.blocking(Self::poll_tails).await,
                        Change::Journal => self.blocking(Self::poll_journals).await,
                        Change::Listing => {
                            debounce.as_mut().reset(Instant::now() + self.settings.debounce);
                            armed = true;
                        }
                        Change::None => {}
                    },
                    Err(error) => {
                        if !reported {
                            (self.settings.log)(&format!(
                                "air-trace serve: the file watcher reported {error}; the periodic rescan covers \
                                 what it misses"
                            ));
                            reported = true;
                        }
                    }
                },
                () = self.rescan_soon.notified() => {
                    debounce.as_mut().reset(Instant::now() + self.settings.debounce);
                    armed = true;
                }
                () = &mut debounce, if armed => {
                    armed = false;
                    self.blocking(Self::scan).await;
                }
                _ = rescan.tick() => {
                    self.blocking(Self::scan).await;
                }
                _ = tail.tick() => {
                    self.blocking(|server| {
                        server.poll_tails();
                        server.poll_journals();
                    })
                    .await;
                }
            }
        }
        // Ends the watcher thread without joining it: its last commit and its stream's stop are fseventsd calls.
        lock(&self.watches.sender).take();
    }

    /// Runs blocking file-system work of the server off the async workers.
    pub(crate) async fn blocking<T: Send + 'static>(self: &Arc<Self>, work: impl FnOnce(&Self) -> T + Send + 'static) -> T {
        let server = Arc::clone(self);
        match tokio::task::spawn_blocking(move || work(&server)).await {
            Ok(value) => value,
            // The work panicked; the panic is the failure, carried on.
            Err(error) => std::panic::resume_unwind(error.into_panic()),
        }
    }
}

/// The watcher thread's work: applies the newest directories the scans sent, until the watch loop drops the sender.
fn keep_watching(
    server: &Weak<Server>,
    watcher: &mut dyn notify::Watcher,
    requests: &std::sync::mpsc::Receiver<WatchRequest>,
    max_watches: usize,
) {
    let mut watched = HashSet::new();
    while let Ok(mut request) = requests.recv() {
        // Lists queued behind a slow commit are stale; only the newest counts.
        while let Ok(newer) = requests.try_recv() {
            request = newer;
        }
        let (number, wanted) = request;
        let added = apply_watches(watcher, &mut watched, &wanted, max_watches);
        let Some(server) = server.upgrade() else {
            return;
        };
        // A directory is watched only after the scan that found it, so whatever was created in it between the two
        // raised no event. One more scan sees it; that scan adds no watch unless something new appeared again.
        if added {
            server.rescan_soon.notify_one();
        }
        server.watches.applied.store(number, Ordering::SeqCst);
    }
}

/// Makes the watched directories the first `max_watches` of `wanted`, in one commit, and answers whether it added
/// one.
fn apply_watches(watcher: &mut dyn notify::Watcher, watched: &mut HashSet<PathBuf>, wanted: &[PathBuf], max_watches: usize) -> bool {
    let mut next = HashSet::new();
    for dir in wanted {
        if next.len() >= max_watches {
            break;
        }
        next.insert(dir.clone());
    }
    let removed: Vec<PathBuf> = watched.difference(&next).cloned().collect();
    let added: Vec<PathBuf> = next.difference(watched).cloned().collect();
    if removed.is_empty() && added.is_empty() {
        return false;
    }
    // One batch: the FSEvents watcher restarts its stream once for the batch rather than once per path.
    let mut paths = watcher.paths_mut();
    for dir in &removed {
        let _ = paths.remove(dir);
        watched.remove(dir);
    }
    let mut any_added = false;
    for dir in added {
        if paths.add(&dir, RecursiveMode::NonRecursive).is_ok() {
            watched.insert(dir);
            any_added = true;
        }
    }
    let _ = paths.commit();
    any_added
}

/// What a file event may have changed.
#[derive(Debug, PartialEq, Eq)]
enum Change {
    /// A running bundle's JSON Lines grew.
    Tail,
    /// A running run's journal grew.
    Journal,
    /// The listing may differ: anything created, removed or renamed, and a write to a manifest or a zip.
    Listing,
    /// Every other write, a `test.log` or a worker's `tart.log`, is noise here.
    None,
}

fn classify(event: &notify::Event) -> Change {
    let write = matches!(
        event.kind,
        EventKind::Modify(ModifyKind::Data(_) | ModifyKind::Any) | EventKind::Access(AccessKind::Close(AccessMode::Write))
    );
    let structural = matches!(
        event.kind,
        EventKind::Create(_) | EventKind::Remove(_) | EventKind::Modify(ModifyKind::Name(_)) | EventKind::Any
    );
    let mut change = Change::None;
    for path in &event.paths {
        let base = path.file_name().map(|name| name.to_string_lossy()).unwrap_or_default();
        if structural {
            return Change::Listing;
        }
        if !write {
            continue;
        }
        if base == MANIFEST_FILE || has_zip_extension(path) {
            return Change::Listing;
        }
        if base == SPANS_FILE || base == LOGS_FILE {
            change = Change::Tail;
        } else if base == JOURNAL_FILE && change == Change::None {
            change = Change::Journal;
        }
    }
    change
}

fn has_zip_extension(path: &Path) -> bool {
    path.extension().is_some_and(|extension| extension.eq_ignore_ascii_case("zip"))
}

/// The complete lines written after an offset: where they end, and the lines.
pub(crate) struct Appended {
    pub next: u64,
    pub lines: Vec<Vec<u8>>,
}

/// The complete lines written after offset. A file shorter than offset was replaced, which a recorder never does,
/// and is read again from its start.
pub(crate) fn read_appended(path: &Path, offset: u64) -> io::Result<Appended> {
    let file = File::open(path)?;
    let size = file.metadata()?.len();
    let offset = if size < offset { 0 } else { offset };
    let nothing = |next| Appended { next, lines: Vec::new() };
    if size == offset {
        return Ok(nothing(offset));
    }
    let length = (size - offset).min(MAX_POLL_BYTES);
    let chunk = read_at_most(&file, offset, length)?;
    let Some(end) = chunk.iter().rposition(|byte| *byte == b'\n') else {
        // One line longer than a poll can read is no log record; skip it rather than stall on it.
        return Ok(nothing(if length == MAX_POLL_BYTES { offset + length } else { offset }));
    };
    Ok(Appended {
        next: offset + end as u64 + 1,
        lines: chunk[..end].split(|byte| *byte == b'\n').map(<[u8]>::to_vec).collect(),
    })
}

/// A file's length up to and including its last newline: where its complete lines end.
pub(crate) fn complete_length(path: &Path) -> u64 {
    const WINDOW: u64 = 64 << 10;
    let Ok(file) = File::open(path) else {
        return 0;
    };
    let Ok(info) = file.metadata() else {
        return 0;
    };
    let mut end = info.len();
    while end > 0 {
        let start = end.saturating_sub(WINDOW);
        let Ok(chunk) = read_at_most(&file, start, end - start) else {
            return 0;
        };
        if let Some(index) = chunk.iter().rposition(|byte| *byte == b'\n') {
            return start + index as u64 + 1;
        }
        end = start;
    }
    0
}
