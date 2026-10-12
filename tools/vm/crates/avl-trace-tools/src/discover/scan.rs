//! The scanner: every root's bundles, remembering what it read between scans.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::{self, BufRead, BufReader, Read, Seek, SeekFrom};
use std::path::{Component, Path, PathBuf};
use std::sync::{Arc, Mutex, PoisonError};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use avl_trace::bundle::{LOGS_FILE, MANIFEST_FILE, SNAP_DIR, SPANS_FILE, VIDEO_FILE, decode_manifest};
use serde::Serialize;

use super::roots::{Root, RootKind, within};
use super::zips::ZipCache;
use super::{
    Bundle, Location, Source, SourceKind, Status, Summary, apply_inference, apply_manifest, bundle_id, infer_from_logs, last_complete_line,
    trailing_segments,
};

/// What a [Scanner] decides with.
pub struct Options {
    /// How long a directory bundle without a manifest may go unchanged and still be running; five minutes by
    /// default.
    pub stale_after: Duration,
    /// Bounds the entries one root's walk visits. A walk that reaches it stops, keeps the bundles it found, and says
    /// so in the root's problems. A trace root holds a few hundred entries per run; the bound is for a directory
    /// someone names without knowing whether it is one, such as the checkout.
    pub max_entries: Option<usize>,
    /// The clock.
    pub now: Box<dyn Fn() -> SystemTime + Send + Sync>,
}

impl Default for Options {
    fn default() -> Self {
        Self {
            stale_after: Duration::from_mins(5),
            max_entries: None,
            now: Box::new(SystemTime::now),
        }
    }
}

/// One root as the last scan found it.
#[derive(Serialize, Clone, PartialEq, Eq, Debug)]
pub struct RootState {
    pub kind: RootKind,
    pub path: PathBuf,
    pub exists: bool,
    pub bundles: usize,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    /// What under the root could not be read, a zip being written for one; at most [MAX_PROBLEMS].
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub problems: Vec<String>,
}

pub(super) const MAX_PROBLEMS: usize = 20;

/// One scan's result.
#[derive(Debug)]
pub struct Snapshot {
    pub scanned_at: SystemTime,
    /// Every bundle found, ordered by start and then by id.
    pub bundles: Vec<Arc<Bundle>>,
    pub by_id: HashMap<String, Arc<Bundle>>,
    pub roots: Vec<RootState>,
    /// The roots' real paths, which every file served from a bundle must be under.
    pub real_roots: Vec<PathBuf>,
    /// The directories in which a change can change the next scan's answer: every directory the walks passed
    /// through above a bundle, a running bundle's own, the nearest existing ancestor of a missing root, and the
    /// glob's way to its matches. A watcher follows them; the scan itself does not.
    pub watch: Vec<PathBuf>,
}

impl Snapshot {
    /// Whether a real path is inside one of the roots' real paths.
    pub fn under_roots(&self, real: &Path) -> bool {
        self.real_roots.iter().any(|allowed| within(real, allowed))
    }
}

/// Looks in roots for bundles. It remembers what it read between scans, so a scan reads only what changed: a
/// directory bundle's files, a zip's central directory. The zips it holds stay open until [Scanner::close].
pub struct Scanner {
    options: Options,
    zips: ZipCache,
    /// Serializes scans.
    state: Mutex<ScanState>,
}

#[derive(Default)]
struct ScanState {
    /// What a directory bundle's files said, by the bundle's directory.
    dir_facts: HashMap<PathBuf, DirFacts>,
    /// Each root's last real path, by the root's configured path.
    last_real: HashMap<PathBuf, PathBuf>,
}

/// The directories a walk never enters: they hold no bundle, and a checkout's are the bulk of its entries.
const SKIPPED_DIRS: &[&str] = &[".git", "node_modules"];

impl Scanner {
    pub fn new(options: Options) -> Self {
        Self {
            options,
            zips: ZipCache::default(),
            state: Mutex::default(),
        }
    }

    /// The scanner's open zips, for a caller that serves their entries.
    pub const fn zips(&self) -> &ZipCache {
        &self.zips
    }

    /// Closes every zip the scanner holds open, once no caller holds it either.
    pub fn close(&self) {
        self.zips.clear();
    }

    /// Looks in every root.
    pub fn scan(&self, roots: &[Root]) -> Snapshot {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let now = (self.options.now)();
        let mut snapshot = Snapshot {
            scanned_at: now,
            bundles: Vec::new(),
            by_id: HashMap::new(),
            roots: Vec::new(),
            real_roots: Vec::new(),
            watch: Vec::new(),
        };
        let mut seen = Seen::default();
        for configured in roots {
            let found = self.scan_root(&mut state, configured, now, &mut seen);
            snapshot.roots.push(found.state);
            snapshot.real_roots.extend(found.real);
            for bundle in found.bundles {
                // Two roots overlap.
                if snapshot.by_id.contains_key(&bundle.id) {
                    continue;
                }
                let bundle = Arc::new(bundle);
                snapshot.by_id.insert(bundle.id.clone(), Arc::clone(&bundle));
                snapshot.bundles.push(bundle);
            }
            snapshot.watch.extend(found.watch);
        }
        snapshot
            .bundles
            .sort_by_cached_key(|bundle| format!("{}{}", bundle.started_at.as_deref().unwrap_or_default(), bundle.id));
        self.zips.retain(&seen.zips);
        state.dir_facts.retain(|dir, _| seen.dirs.contains(dir));
        snapshot
    }

    fn scan_root(&self, state: &mut ScanState, configured: &Root, now: SystemTime, seen: &mut Seen) -> RootScan {
        let mut found = RootScan {
            state: RootState {
                kind: configured.kind,
                path: configured.path.clone(),
                exists: false,
                bundles: 0,
                error: configured.error.clone(),
                problems: Vec::new(),
            },
            bundles: Vec::new(),
            watch: Vec::new(),
            real: None,
        };
        if configured.error.is_some() {
            return found;
        }
        let mut real = fscopy::resolve_links(&configured.path);
        // Bazel removes its `bazel-testlogs` convenience link whenever a build's top-level targets span two
        // configurations, and puts it back on the next build that does not; another session's build does that
        // several times a minute. The directory the link pointed at is still there, so the root keeps it until the
        // link names another one, and its bundles do not blink out of the listing with the link.
        if let Err(error) = &real
            && error.kind() == io::ErrorKind::NotFound
            && let Some(last) = state.last_real.get(&configured.path)
            && last.is_dir()
        {
            real = Ok(last.clone());
        }
        let real = match real {
            Ok(real) => real,
            Err(error) => {
                if error.kind() != io::ErrorKind::NotFound {
                    found.state.error = Some(error.to_string());
                }
                found.watch = ancestor_watch(&configured.path);
                return found;
            }
        };
        state.last_real.insert(configured.path.clone(), real.clone());
        let info = match fs::metadata(&real) {
            Ok(info) => info,
            Err(error) => {
                found.state.error = Some(error.to_string());
                return found;
            }
        };
        found.state.exists = true;

        if let Some(glob) = configured.glob {
            for matched in glob_matches(&real, glob) {
                // A match is served by its real path, and only from inside the root: a glob follows links.
                if let Ok(resolved) = fscopy::resolve_links(&matched)
                    && within(&resolved, &real)
                    && resolved.is_file()
                {
                    self.add_zip(&mut found, &resolved, seen);
                }
            }
            found.watch = glob_watch(&real, glob);
        } else if info.is_file() {
            self.add_zip(&mut found, &real, seen);
        } else if info.is_dir() {
            self.walk(state, &mut found, &real, now, seen);
        }
        found.state.bundles = found.bundles.len();
        found.real = Some(real);
        found
    }

    fn walk(&self, state: &mut ScanState, found: &mut RootScan, real: &Path, now: SystemTime, seen: &mut Seen) {
        let mut visited = 0;
        let mut entries = walkdir::WalkDir::new(real).follow_links(false).sort_by_file_name().into_iter();
        while let Some(entry) = entries.next() {
            let entry = match entry {
                Ok(entry) => entry,
                Err(error) if error.depth() == 0 => {
                    found.state.error = Some(error.to_string());
                    return;
                }
                Err(error) => {
                    let path = error.path().unwrap_or(real).display().to_string();
                    found.problem(format!("{path}: {error}"));
                    continue;
                }
            };
            visited += 1;
            if let Some(max) = self.options.max_entries
                && visited > max
            {
                found.problem(format!(
                    "{} holds more than {max} entries; only the bundles found before that are listed",
                    real.display()
                ));
                return;
            }
            let path = entry.path();
            if entry.file_type().is_dir() {
                if entry.depth() > 0 && SKIPPED_DIRS.iter().any(|skipped| entry.file_name() == *skipped) {
                    entries.skip_current_dir();
                    continue;
                }
                if path.join(SPANS_FILE).is_file() {
                    seen.dirs.insert(path.to_path_buf());
                    let bundle = self.dir_bundle(state, path, now);
                    // A finished bundle never changes, and a running one changes only in its own files, not in its
                    // snapshots' directory.
                    if bundle.status == Status::Running {
                        found.watch.push(path.to_path_buf());
                    }
                    found.bundles.push(bundle);
                    entries.skip_current_dir();
                    continue;
                }
                found.watch.push(path.to_path_buf());
                continue;
            }
            let is_zip = path.extension().is_some_and(|extension| extension.eq_ignore_ascii_case("zip"));
            if is_zip && entry.file_type().is_file() {
                self.add_zip(found, path, seen);
            }
        }
    }

    fn add_zip(&self, found: &mut RootScan, zip: &Path, seen: &mut Seen) {
        seen.zips.insert(zip.to_path_buf());
        match self.zips.get(zip) {
            Ok(index) => {
                if let Some(error) = index.error() {
                    found.problem(format!("{}: {error}", zip.display()));
                }
                found.bundles.extend(index.bundles(&zip.to_string_lossy()));
            }
            Err(error) => found.problem(format!("{}: {error}", zip.display())),
        }
    }

    /// Reads one directory bundle, or takes what its files said last time when none of them changed. Only its
    /// staleness, which is the clock, is decided on every scan.
    fn dir_bundle(&self, state: &mut ScanState, dir: &Path, now: SystemTime) -> Bundle {
        let (key, latest) = dir_state(dir);
        let facts = match state.dir_facts.get(dir) {
            Some(facts) if facts.key == key => facts,
            _ => {
                let (summary, manifest) = read_dir_summary(dir);
                state.dir_facts.insert(
                    dir.to_path_buf(),
                    DirFacts {
                        key,
                        latest,
                        summary,
                        manifest,
                    },
                );
                &state.dir_facts[dir]
            }
        };
        let mut summary = facts.summary.clone();
        let container = dir.to_string_lossy().into_owned();
        summary.id = bundle_id(SourceKind::Dir, &container, "");
        summary.source = Source {
            kind: SourceKind::Dir,
            path: container,
            entry: String::new(),
        };
        if !facts.manifest {
            let quiet = now.duration_since(facts.latest).unwrap_or_default();
            summary.status = if quiet < self.options.stale_after {
                Status::Running
            } else {
                Status::Truncated
            };
        }
        Bundle {
            summary,
            location: Location::Dir(dir.to_path_buf()),
        }
    }
}

#[derive(Default)]
struct Seen {
    zips: HashSet<PathBuf>,
    dirs: HashSet<PathBuf>,
}

struct RootScan {
    state: RootState,
    bundles: Vec<Bundle>,
    watch: Vec<PathBuf>,
    real: Option<PathBuf>,
}

impl RootScan {
    fn problem(&mut self, problem: String) {
        if self.state.problems.len() < MAX_PROBLEMS {
            self.state.problems.push(problem);
        }
    }
}

/// The matches of a `/`-separated glob under a directory, the directory's own name taken literally.
fn glob_matches(directory: &Path, glob: &str) -> Vec<PathBuf> {
    match glob::glob(&glob_pattern(directory, glob)) {
        Ok(paths) => paths.filter_map(Result::ok).collect(),
        Err(_) => Vec::new(),
    }
}

/// The pattern of `glob` under `directory`, with the characters of the directory escaped.
///
/// The root of the directory is kept as it is: `/`, `C:\`, `\\host\share\`, or a verbatim `\\?\C:\`. Escaped, the `?` of
/// that verbatim prefix is no longer a prefix, and the glob then matches nothing.
pub(super) fn glob_pattern(directory: &Path, glob: &str) -> String {
    let text = directory.to_string_lossy();
    let root = directory
        .components()
        .take_while(|component| matches!(component, Component::Prefix(_) | Component::RootDir))
        .map(|component| component.as_os_str().len())
        .sum::<usize>();
    let (root, rest) = match (text.get(..root), text.get(root..)) {
        (Some(root), Some(rest)) => (root, rest),
        _ => ("", text.as_ref()),
    };
    format!("{root}{}/{glob}", glob::Pattern::escape(rest))
}

/// What to watch for a root that does not exist yet: its nearest existing ancestor, so its creation is seen. No
/// more than three levels up, and never the file system's or the home directory's top, whose every file kqueue
/// would hold open; the periodic rescan covers a root further away than that.
fn ancestor_watch(missing: &Path) -> Vec<PathBuf> {
    let home = home_dir();
    let mut current = missing;
    for _ in 0..3 {
        let Some(parent) = current.parent().filter(|parent| !parent.as_os_str().is_empty()) else {
            return Vec::new();
        };
        if parent.parent().is_none() || home.as_deref() == Some(parent) {
            return Vec::new();
        }
        if parent.is_dir() {
            return fscopy::resolve_links(parent).map(|real| vec![real]).unwrap_or_default();
        }
        current = parent;
    }
    Vec::new()
}

fn home_dir() -> Option<PathBuf> {
    let variable = if cfg!(windows) { "USERPROFILE" } else { "HOME" };
    std::env::var_os(variable).filter(|home| !home.is_empty()).map(PathBuf::from)
}

/// The existing directories on the way to a glob's matches in which a new match can show up as a new entry: the
/// root, every directory whose next segment is a pattern, and the directories the matches sit in. So a new worker,
/// a new run and a new zip are seen as they appear.
///
/// A directory whose next segment is a fixed name is left to the periodic rescan. That is a run's report directory
/// of `workers/*/reports/*/traces/*.zip`, which holds every file of the run's report, and on macOS a kqueue watcher
/// holds a descriptor open for every entry of a watched directory. Watching months of report directories to notice
/// one `traces` directory appear would spend the process's descriptors, and then the server could not open the
/// bundle it was asked to serve.
pub(super) fn glob_watch(real: &Path, glob: &str) -> Vec<PathBuf> {
    let mut watch = vec![real.to_path_buf()];
    let segments: Vec<&str> = glob.split('/').collect();
    let last = segments.len() - 1;
    for index in 0..last {
        let next = segments[index + 1];
        if index + 1 < last && !next.contains(['*', '?', '[', '\\']) {
            continue;
        }
        let prefix = segments[..=index].join("/");
        watch.extend(glob_matches(real, &prefix).into_iter().filter(|matched| matched.is_dir()));
    }
    watch
}

/// What a directory bundle's files said at one state of them. The state is the key, so a bundle whose files did
/// not change is not read again.
struct DirFacts {
    key: String,
    latest: SystemTime,
    summary: Summary,
    /// Whether a manifest was there, decodable or not.
    manifest: bool,
}

/// A key that changes whenever anything the summary reads changes, and the latest modification among those files,
/// which decides whether a bundle without a manifest is still running.
fn dir_state(dir: &Path) -> (String, SystemTime) {
    let mut key = String::new();
    let mut latest = UNIX_EPOCH;
    for name in ["", MANIFEST_FILE, SPANS_FILE, LOGS_FILE, SNAP_DIR, VIDEO_FILE] {
        let path = if name.is_empty() { dir.to_path_buf() } else { dir.join(name) };
        let Ok(info) = fs::metadata(path) else {
            key.push_str("-|");
            continue;
        };
        let modified = info.modified().unwrap_or(UNIX_EPOCH);
        let nanos = modified.duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
        key.push_str(&format!("{}:{nanos}|", info.len()));
        latest = latest.max(modified);
    }
    (key, latest)
}

/// Reads a directory bundle's manifest, or infers what it can without one. Answers whether a manifest was there,
/// decodable or not.
fn read_dir_summary(dir: &Path) -> (Summary, bool) {
    let mut summary = Summary::default();
    if let Ok(entries) = fs::read_dir(dir.join(SNAP_DIR)) {
        summary.thumbnail = entries
            .filter_map(Result::ok)
            .filter_map(|entry| entry.file_name().into_string().ok())
            .filter(|name| name.ends_with(".webp"))
            .max()
            .map(|name| format!("{SNAP_DIR}/{name}"));
    }
    let video_present = fs::metadata(dir.join(VIDEO_FILE)).is_ok_and(|info| info.len() > 0);
    let slashed = dir.to_string_lossy().replace('\\', "/");
    let segments = trailing_segments(&slashed);
    let manifest_present = match fs::read(dir.join(MANIFEST_FILE)) {
        Ok(document) => match decode_manifest(&document) {
            Ok(manifest) => {
                apply_manifest(&mut summary, &manifest, video_present);
                return (summary, true);
            }
            Err(error) => {
                summary.status = Status::Invalid;
                summary.error = Some(error.to_string());
                true
            }
        },
        Err(_) => false,
    };
    let (first, last) = read_first_and_last_lines(&dir.join(LOGS_FILE));
    apply_inference(&mut summary, &infer_from_logs(first.as_deref(), last.as_deref()), &segments);
    summary.has_video = video_present;
    (summary, manifest_present)
}

/// How far from its end a file is read for its last line. A log record is a few hundred bytes; the window is
/// generous so that one carrying a long exception still fits.
const TAIL_WINDOW: u64 = 256 << 10;

/// A JSON Lines file's first and last complete lines, reading its start and its end only. A last line without its
/// newline is still being written and is not answered.
fn read_first_and_last_lines(path: &Path) -> (Option<Vec<u8>>, Option<Vec<u8>>) {
    let Ok(file) = fs::File::open(path) else {
        return (None, None);
    };
    let mut reader = BufReader::with_capacity(64 << 10, file);
    let mut first = Vec::new();
    if reader.read_until(b'\n', &mut first).is_err() || first.last() != Some(&b'\n') {
        return (None, None);
    }
    let Ok(size) = reader.seek(SeekFrom::End(0)) else {
        return (Some(first), None);
    };
    let start = size.saturating_sub(TAIL_WINDOW);
    let mut window = Vec::new();
    if reader.seek(SeekFrom::Start(start)).is_err() || reader.read_to_end(&mut window).is_err() {
        return (Some(first), None);
    }
    let last = last_complete_line(&window).map(<[u8]>::to_vec);
    (Some(first), last)
}
