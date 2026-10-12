//! The recorder's test world: a scripted clock, a fake IDE with a bridge and a screen, a fixed ladder, no video, and
//! the replay of a golden transcript at the golden receipt times.

use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use avl_trace::bridge::{
    FACTS_ROUTE, FLUSHED_SPANS_ROUTE, Facts, GESTURE_PRESS, IdeAttribute, IdeSpan, IdeSpans, Input, InputTarget, PAINT_CONTENT_TYPE,
    PAINT_ROUTE, Point, Rect, SPANS_ROUTE, TOKEN_HEADER, TREE_ROUTE, Tree, decode_tree,
};
use avl_trace::bundle::{IDEA_LOG_FILE, LOGS_FILE, MANIFEST_FILE, Manifest, SPANS_FILE, bundle_dir, decode_manifest};
use avl_trace::otlp::{LogRecord, Span, decode_logs_line, decode_traces_line};
use avl_trace::protocol::{Bridge, CaptureSource, Command, HelloCommand, Launcher, VideoCodec, decode_command, encode_command};

use crate::bridge::Client;
use crate::capture::{Frame, Source};
use crate::ladder::{CaptureLadder, ScreenAccess, VideoPlan, Videographer};
use crate::session::Session;
use crate::testing::{FakeHttp, Response, ScriptedClock, SharedBuffer};
use crate::video::Recording;
use crate::{Diagnostics, Options, lock};

pub(crate) const GOLDEN_ACKS: &str = "lane-transcript.acks.ndjson";
pub(crate) const GOLDEN_TOKEN: &str = "golden-transcript-token";
pub(crate) const GOLDEN_HOST: &str = "air-linux-1";
pub(crate) const GOLDEN_VIDEO_REASON: &str = "fixture";
const GOLDEN_LOG_PREFIX_LINE: &str =
    "2026-09-23 10:14:59,870 [ 811653]   INFO - #c.i.a.i.f.AirFlowSuiteRuntime - the previous scenario's last line\n";

/// The moment the golden hello arrived: 2026-09-23T10:15:00Z.
pub(crate) const T0_MS: i64 = 1_790_158_500_000;

/// When each line of the golden transcript arrived, in milliseconds after [T0_MS].
///
/// A transcript carries no receipt times, since the recorder stamps them, so the replay has to supply them. These
/// are the ones the golden bundle was first authored with: a lane running `rename-closed-project-session` at a
/// plausible pace, every call line two milliseconds after its call returned, and every span wide enough to hold the
/// calls, snapshots and driver steps the lane timed inside it.
const RECEIPT_MS: [i64; 66] = [
    0, 120, 130, 2310, 2516, 2530, 2531, 2533, 2534, 2814, 3248, 4392, 4400, 4402, 4403, 4510, 4512, 4570, 4571, 4630, 4631, 4640, 4641,
    4643, 4644, 4724, 5480, 5540, 5545, 5546, 5621, 5900, 5905, 5906, 6420, 6480, 6482, 6540, 6541, 6550, 6551, 6553, 6554, 7672, 7710,
    7712, 7713, 8030, 8090, 8092, 8150, 8151, 8160, 8161, 8163, 8164, 8588, 8600, 8660, 8662, 8720, 8721, 8730, 10950, 10960, 10970,
];

/// A file of the recorder's own testdata.
pub(crate) fn record_testdata(name: &str) -> PathBuf {
    avl_testkit::crate_path!(&format!("testdata/{name}"))
}

// The screens of the fake IDE, one fixture picture and one fixture tree each.
const SCREEN_EDITOR: &str = "editor";
const SCREEN_SESSIONS: &str = "sessions";
const SCREEN_DIALOG: &str = "dialog";
const SCREEN_RENAMED: &str = "renamed";

/// What the fake IDE shows when the snapshot with this ordinal is taken: the editor before the setup, the sessions
/// tree through the popup, the dialog while it is open, and the renamed row after.
fn screen_at_snapshot(ordinal: usize) -> &'static str {
    match ordinal {
        1 => SCREEN_EDITOR,
        2..=7 => SCREEN_SESSIONS,
        8..=11 => SCREEN_DIALOG,
        _ => SCREEN_RENAMED,
    }
}

#[derive(Default)]
struct IdeState {
    screen: &'static str,
    pending: Vec<Input>,
    /// The token the bridge answers, the golden one until a test relaunches the IDE under another.
    token: String,
    /// What the spans route answers next, `queued` what only the flushed route adds, and `dropped` what the IDE's
    /// ring lost before the next read.
    exported: Vec<IdeSpan>,
    queued: Vec<IdeSpan>,
    dropped: u64,
    /// Makes both spans routes answer 404, as the bridge of an older checkout does.
    no_spans: bool,
}

/// The IDE under test as the recorder sees it: a screen to grab, and a bridge that answers facts, trees, paints and
/// spans. It keeps an input log that the tree route drains, and a span log that the spans routes drain.
pub(crate) struct FakeIde {
    server: FakeHttp,
    pub(crate) log_path: PathBuf,
    state: Arc<Mutex<IdeState>>,
    frames: HashMap<&'static str, Frame>,
    _dir: tempfile::TempDir,
}

impl FakeIde {
    fn new(clock: ScriptedClock) -> Arc<Self> {
        let dir = tempfile::tempdir().unwrap();
        let log_path = dir.path().join(IDEA_LOG_FILE);
        fs::write(&log_path, GOLDEN_LOG_PREFIX_LINE).unwrap();
        let mut frames = HashMap::new();
        let mut paints = HashMap::new();
        let mut trees = HashMap::new();
        for screen in [SCREEN_EDITOR, SCREEN_SESSIONS, SCREEN_DIALOG, SCREEN_RENAMED] {
            let paint = fs::read(record_testdata(&format!("ide/{screen}.png"))).unwrap();
            let mut frame = Frame::default();
            frame.set_png(&paint, std::time::UNIX_EPOCH).unwrap();
            frames.insert(screen, frame);
            paints.insert(screen, paint);
            let document = fs::read(record_testdata(&format!("ide/{screen}.tree.json"))).unwrap();
            trees.insert(screen, decode_tree(&document).unwrap());
        }
        let state = Arc::new(Mutex::new(IdeState {
            screen: SCREEN_EDITOR,
            token: GOLDEN_TOKEN.to_owned(),
            ..IdeState::default()
        }));
        let server = {
            let (state, log_path) = (state.clone(), log_path.clone());
            FakeHttp::start(move |request| {
                let mut state = lock(&state);
                if request.header(TOKEN_HEADER) != Some(state.token.as_str()) {
                    return Response::error(401, "wrong token");
                }
                match request.path.as_str() {
                    FACTS_ROUTE => {
                        let facts = Facts {
                            pid: 4242,
                            log_path: log_path.to_string_lossy().into_owned(),
                            log_size: fs::metadata(&log_path).map_or(0, |metadata| metadata.len()),
                            display: Some(":88".to_owned()),
                            screen: Rect {
                                x: 0,
                                y: 0,
                                width: 1280,
                                height: 800,
                            },
                            os: "linux".to_owned(),
                        };
                        Response::ok("application/json", avl_trace::encode(&facts).unwrap())
                    }
                    TREE_ROUTE => {
                        let mut tree: Tree = trees[state.screen].clone();
                        tree.captured_at_ms = clock.ms();
                        tree.inputs = std::mem::take(&mut state.pending);
                        Response::ok("application/json", avl_trace::encode(&tree).unwrap())
                    }
                    PAINT_ROUTE => Response::ok(PAINT_CONTENT_TYPE, paints[state.screen].clone()),
                    SPANS_ROUTE | FLUSHED_SPANS_ROUTE if !state.no_spans => {
                        if request.path == FLUSHED_SPANS_ROUTE {
                            let queued = std::mem::take(&mut state.queued);
                            state.exported.extend(queued);
                        }
                        let answer = IdeSpans {
                            spans: std::mem::take(&mut state.exported),
                            dropped: std::mem::take(&mut state.dropped),
                        };
                        Response::ok("application/json", avl_trace::encode(&answer).unwrap())
                    }
                    _ => Response::error(404, "not found"),
                }
            })
        };
        Arc::new(Self {
            server,
            log_path,
            state,
            frames,
            _dir: dir,
        })
    }

    pub(crate) fn url(&self) -> String {
        self.server.url().to_owned()
    }

    fn show(&self, screen: &'static str, inputs: Vec<Input>) {
        let mut state = lock(&self.state);
        state.screen = screen;
        state.pending.extend(inputs);
    }

    /// Makes the bridge answer `token` only, as an IDE launched under another launch key does.
    pub(crate) fn relaunch(&self, token: &str) {
        lock(&self.state).token = token.to_owned();
    }

    /// Makes `spans` the answer of the next spans read, as spans the platform already exported.
    pub(crate) fn export(&self, spans: impl IntoIterator<Item = IdeSpan>) {
        lock(&self.state).exported.extend(spans);
    }

    /// Makes `spans` the answer of the next flushed spans read only, as spans the platform still holds in its batch.
    pub(crate) fn queue(&self, spans: impl IntoIterator<Item = IdeSpan>) {
        lock(&self.state).queued.extend(spans);
    }

    /// Makes the next spans read report `count` spans the IDE's ring lost.
    pub(crate) fn drop_spans(&self, count: u64) {
        lock(&self.state).dropped = count;
    }

    /// Makes both spans routes answer 404, as the bridge of an older checkout does.
    pub(crate) fn remove_spans_routes(&self) {
        lock(&self.state).no_spans = true;
    }

    fn current_frame(&self) -> Frame {
        self.frames[lock(&self.state).screen].clone()
    }

    /// Writes to the IDE's log, as the IDE would while the scenario runs.
    fn append_log(&self, content: &[u8]) {
        let mut file = fs::OpenOptions::new().append(true).open(&self.log_path).unwrap();
        file.write_all(content).unwrap();
    }
}

/// An X11 source that shows whatever the fake IDE shows, stamped with the scripted clock.
pub(crate) struct FakeScreen {
    ide: Arc<FakeIde>,
    clock: ScriptedClock,
}

impl Source for FakeScreen {
    fn kind(&self) -> CaptureSource {
        CaptureSource::X11
    }

    fn grab(&self, _deadline: Instant, frame: &mut Frame) -> anyhow::Result<()> {
        *frame = self.ide.current_frame();
        frame.at = self.clock.now();
        Ok(())
    }
}

pub(crate) struct FixedLadder {
    pub(crate) source: Option<Arc<dyn Source>>,
    pub(crate) reason: String,
}

impl CaptureLadder for FixedLadder {
    fn climb(&mut self, _: &ScreenAccess, _: Option<&Client>) -> (Option<Arc<dyn Source>>, String) {
        (self.source.clone(), self.reason.clone())
    }
}

/// The golden bundle's videographer: the example carries no video on purpose, and says so.
pub(crate) struct NoVideo;

impl Videographer for NoVideo {
    fn plan(&self, _: Option<&dyn Source>, _: bool) -> VideoPlan {
        VideoPlan {
            codec: VideoCodec::None,
            reason: GOLDEN_VIDEO_REASON.to_owned(),
            raw: false,
        }
    }

    fn start(&self, _: &Path, _: &VideoPlan, _: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        anyhow::bail!("a video that was planned as none was started")
    }
}

pub(crate) struct Harness {
    pub(crate) root: PathBuf,
    _root: tempfile::TempDir,
    pub(crate) clock: ScriptedClock,
    pub(crate) ide: Arc<FakeIde>,
    pub(crate) acks: SharedBuffer,
    pub(crate) diagnostics: SharedBuffer,
}

impl Drop for Harness {
    fn drop(&mut self) {
        if std::thread::panicking() {
            eprintln!("the recorder's diagnostics:\n{}", self.diagnostics.text());
        }
    }
}

impl Harness {
    pub(crate) fn new() -> Self {
        let clock = ScriptedClock::at_ms(T0_MS);
        let root = tempfile::tempdir().unwrap();
        Self {
            root: root.path().to_owned(),
            _root: root,
            ide: FakeIde::new(clock.clone()),
            clock,
            acks: SharedBuffer::default(),
            diagnostics: SharedBuffer::default(),
        }
    }

    pub(crate) fn screen(&self) -> Arc<dyn Source> {
        Arc::new(FakeScreen {
            ide: self.ide.clone(),
            clock: self.clock.clone(),
        })
    }

    /// The options of the replay: the scripted clock, the golden machine, the fake screen, no video.
    pub(crate) fn options(&self) -> Options {
        let url = self.ide.url();
        Options {
            clock: self.clock.clock(),
            host_name: GOLDEN_HOST.to_owned(),
            os_type: "linux".to_owned(),
            bridge_url: Box::new(move |_| url.clone()),
            ladder: Box::new(FixedLadder {
                source: Some(self.screen()),
                reason: String::new(),
            }),
            video: Box::new(NoVideo),
            frame_interval: Duration::from_millis(100),
            // The replay snaps as fast as it can feed lines; see DEFAULT_SNAP_BUDGET for why its bound is long.
            snap_budget: Duration::from_secs(60),
            diagnostics: Diagnostics::new(self.diagnostics.clone()),
        }
    }

    pub(crate) fn session(&self, options: Options) -> Session<SharedBuffer> {
        Session::new(options, self.acks.clone())
    }

    /// Hands a session hand-written lines, each a millisecond after the previous one.
    pub(crate) fn feed(&self, session: &mut Session<SharedBuffer>, lines: &[&str]) {
        for line in lines {
            let at = self.clock.advance_ms(1);
            session.handle(line.as_bytes(), at);
        }
    }

    pub(crate) fn hello(&self, bridge: bool) -> String {
        let hello = HelloCommand {
            root: self.root.to_string_lossy().into_owned(),
            run_id: "run-1".to_owned(),
            launcher: Launcher::Bazel,
            screen: false,
            bridge: bridge.then(|| Bridge {
                port: 1,
                token: GOLDEN_TOKEN.to_owned(),
            }),
        };
        String::from_utf8(encode_command(&Command::Hello(hello)).unwrap()).unwrap()
    }

    pub(crate) fn example_bundle(&self) -> PathBuf {
        bundle_dir(&self.root, "run-1", "AirExampleUiTest", "example")
    }

    /// The golden hello with its root moved under the test's temporary directory. Nothing the recorder writes into
    /// a bundle mentions the root, so the bundle is the one the golden hello would produce.
    pub(crate) fn rooted(&self, line: &[u8]) -> Vec<u8> {
        let Command::Hello(mut hello) = decode_command(line).unwrap() else {
            panic!("the first line is not a hello");
        };
        hello.root = self.root.to_string_lossy().into_owned();
        encode_command(&Command::Hello(hello)).unwrap()
    }

    /// Feeds a golden transcript to a session line by line at the golden receipt times, playing the IDE's part in
    /// between, and closes the session a second after the last line. The IDE's part is fixed by the ordinal of each
    /// snapshot, so the replay is the same every time.
    pub(crate) fn replay(&self, lines: &[Vec<u8>]) {
        let mut session = self.session(self.options());
        let idea_log = fs::read(record_testdata(&format!("ide/{IDEA_LOG_FILE}"))).unwrap();
        let mut snaps = 0;
        for (index, line) in lines.iter().enumerate() {
            let at = T0_MS + RECEIPT_MS[index];
            self.clock.set_ms(at);
            let line = if index == 0 { self.rooted(line) } else { line.clone() };
            if line.starts_with(br#"{"op":"snap""#) {
                snaps += 1;
                self.ide.show(screen_at_snapshot(snaps), snapshot_inputs(snaps));
                match snaps {
                    1 => self.ide.export([golden_earlier_read()]),
                    6 => self.ide.export([golden_catalog_read()]),
                    // The rename's own read ends in the platform's batch, so only the flushed read at done answers it.
                    12 => self.ide.queue([golden_rename_read()]),
                    _ => {}
                }
            }
            session.handle(&line, self.clock.now());
            if index == 1 {
                self.ide.append_log(&idea_log);
            }
        }
        self.clock.advance_ms(1000);
        session.close("the transcript ended");
    }

    pub(crate) fn bundle(&self) -> PathBuf {
        bundle_dir(
            &self.root,
            "iter-6f1c2a9e-3b4d-4f7a-9c1e-2d8b5a7e0c41-3",
            "AirRenameSessionGeneratedFlowUiTest",
            "rename-closed-project-session",
        )
    }

    /// The acks the session wrote, decoded generically.
    pub(crate) fn ack_lines(&self) -> Vec<serde_json::Value> {
        self.acks
            .text()
            .lines()
            .map(|line| serde_json::from_str(line).unwrap_or_else(|_| panic!("an ack is not JSON: {line}")))
            .collect()
    }
}

/// The inputs the bridge logged before the snapshot with this ordinal. The Remote Driver's two clicks on the
/// session row do not go through the bridge, so the bridge logs only their presses: the right click that opens the
/// row's popup, then the click that selects it; the OK click of the rename dialog goes through it.
fn snapshot_inputs(ordinal: usize) -> Vec<Input> {
    let session_row = |at_ms: i64| Input {
        at_ms: T0_MS + at_ms,
        gesture: GESTURE_PRESS.to_owned(),
        point: Some(Point { x: 120, y: 167 }),
        point_at_ms: T0_MS + at_ms,
        target: InputTarget {
            bounds: Rect {
                x: 0,
                y: 76,
                width: 360,
                height: 700,
            },
            component: "com.intellij.ui.treeStructure.Tree 'Agent Sessions'".to_owned(),
        },
    };
    match ordinal {
        6 => vec![session_row(4810)],
        8 => vec![session_row(5700)],
        12 => vec![Input {
            at_ms: T0_MS + 6875,
            gesture: "buttonActionClick".to_owned(),
            point: Some(Point { x: 780, y: 427 }),
            point_at_ms: T0_MS + 7390,
            target: InputTarget {
                bounds: Rect {
                    x: 740,
                    y: 412,
                    width: 80,
                    height: 30,
                },
                component: "JButton 'OK' in the dialog 'Rename Session'".to_owned(),
            },
        }],
        _ => Vec::new(),
    }
}

/// One span of the fake IDE, from `start_ms` to `end_ms` after [T0_MS], with the attribute values as JSON text.
pub(crate) fn ide_span(
    scope: &str,
    name: &str,
    trace_id: &str,
    span_id: &str,
    parent: Option<&str>,
    start_ms: i64,
    end_ms: i64,
    attributes: &[(&str, &str)],
) -> IdeSpan {
    IdeSpan {
        scope: scope.to_owned(),
        name: name.to_owned(),
        trace_id: trace_id.to_owned(),
        span_id: span_id.to_owned(),
        parent_span_id: parent.map(str::to_owned),
        start_epoch_nanos: (T0_MS + start_ms) * 1_000_000,
        end_epoch_nanos: (T0_MS + end_ms) * 1_000_000,
        attributes: attributes
            .iter()
            .map(|(key, json)| {
                let value: IdeAttribute = serde_json::from_str(json).unwrap();
                ((*key).to_owned(), value)
            })
            .collect(),
        failed: false,
    }
}

// The golden IDE's spans: two reads of the renamed session's history, one of them still in the platform's batch at
// the end, and one read from before the scenario, which the bundle leaves out.
const GOLDEN_IDE_TRACE: &str = "4bf92f3577b34da6a3ce929d0e0e4736";
const GOLDEN_SESSION_LOG: &str = "air.sessionLog";
const GOLDEN_READ_SPAN: &str = "air.session.log.read";
const GOLDEN_THREAD_ID: &str = r#""closed-project-session-1""#;

fn golden_read(span_id: &str, start_ms: i64, end_ms: i64, attributes: &[(&str, &str)]) -> IdeSpan {
    ide_span(
        GOLDEN_SESSION_LOG,
        GOLDEN_READ_SPAN,
        GOLDEN_IDE_TRACE,
        span_id,
        None,
        start_ms,
        end_ms,
        attributes,
    )
}

fn golden_earlier_read() -> IdeSpan {
    golden_read(
        "00f067aa0ba902b7",
        60,
        64,
        &[
            ("air.session.id", r#""previous-scenario-session""#),
            ("air.reason", r#""list-projection-catalog""#),
            ("air.bytes", "912"),
        ],
    )
}

fn golden_catalog_read() -> IdeSpan {
    golden_read(
        "53995c3f42cd8ad8",
        4650,
        4653,
        &[
            ("air.session.id", GOLDEN_THREAD_ID),
            ("air.reason", r#""list-projection-catalog""#),
            ("air.bytes", "18234"),
        ],
    )
}

fn golden_rename_read() -> IdeSpan {
    golden_read(
        "a2fb4a1d1a96d312",
        7700,
        7704,
        &[
            ("air.session.id", GOLDEN_THREAD_ID),
            ("air.reason", r#""rich-projection""#),
            ("air.bytes", "18390"),
        ],
    )
}

/// Every log record of a bundle, in order, each line checked against the contract.
pub(crate) fn records(dir: &Path) -> Vec<LogRecord> {
    let content = fs::read_to_string(dir.join(LOGS_FILE)).unwrap();
    content
        .lines()
        .flat_map(|line| {
            let data = decode_logs_line(line.as_bytes()).unwrap();
            data.validate().unwrap();
            data.resource_logs
                .into_iter()
                .next()
                .unwrap()
                .scope_logs
                .into_iter()
                .next()
                .unwrap()
                .log_records
        })
        .collect()
}

/// Every span of a bundle by its OTLP id, each line checked against the contract.
pub(crate) fn spans(dir: &Path) -> BTreeMap<String, Span> {
    let content = fs::read_to_string(dir.join(SPANS_FILE)).unwrap();
    content
        .lines()
        .map(|line| {
            let data = decode_traces_line(line.as_bytes()).unwrap();
            data.validate().unwrap();
            let span = data
                .resource_spans
                .into_iter()
                .next()
                .unwrap()
                .scope_spans
                .into_iter()
                .next()
                .unwrap()
                .spans;
            let span = span.into_iter().next().unwrap();
            (span.span_id.clone(), span)
        })
        .collect()
}

pub(crate) fn manifest_of(dir: &Path) -> Manifest {
    decode_manifest(&fs::read(dir.join(MANIFEST_FILE)).unwrap()).unwrap()
}

/// Every file under `dir` by its slash path, with its content.
pub(crate) fn files_under(dir: &Path) -> BTreeMap<String, Vec<u8>> {
    let mut files = BTreeMap::new();
    let mut pending = vec![dir.to_owned()];
    while let Some(next) = pending.pop() {
        for entry in fs::read_dir(&next).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                pending.push(path);
            } else {
                let relative = path.strip_prefix(dir).unwrap();
                let name = relative
                    .components()
                    .map(|part| part.as_os_str().to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("/");
                files.insert(name, fs::read(&path).unwrap());
            }
        }
    }
    files
}
