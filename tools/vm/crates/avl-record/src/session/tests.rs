use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use anyhow::bail;
use avl_trace::bridge::IdeSpan;
use avl_trace::bundle::{BundleStatus, IDEA_LOG_FILE, SPANS_FILE, Video, bundle_dir, snap_image_path, snap_tree_path};
use avl_trace::otlp::{
    AnyValue, I64String, IDE_SERVICE_NAME, IDE_SPAN_KIND, LogRecord, SCOPE_NAME, StatusCode, UnixNano, attr, decode_traces_line, event,
    lookup, lookup_str, span_id,
};
use avl_trace::protocol::{ScenarioCommand, Status, decode_hello_ack, encode_command};
use crossbeam_channel::{Receiver, Sender};

use super::*;
use crate::capture::Frame;
use crate::ladder::{VideoPlan, Videographer};
use crate::system_clock;
use crate::tests::harness::*;
use crate::video::{FrameSink, Recording, Stopped};

const MINIMAL_SCENARIO: &str = r#"{"op":"scenario","name":"example","testClass":"AirExampleUiTest","lane":"UI"}"#;
const SNAP: &str = r#"{"op":"snap","span":0,"phase":"boundary"}"#;
const DONE: &str = r#"{"op":"done","status":"passed"}"#;

fn error_records(dir: &Path) -> Vec<String> {
    records(dir)
        .iter()
        .filter(|record| record.event_name == event::TRACE_ERROR)
        .map(|record| {
            format!(
                "{}: {}",
                lookup_str(&record.attributes, attr::TRACE_ERROR_SOURCE).unwrap_or_default(),
                lookup_str(&record.attributes, attr::TRACE_ERROR_MESSAGE).unwrap_or_default()
            )
        })
        .collect()
}

fn ok_and_line(ack: &serde_json::Value) -> (bool, u64) {
    (ack["ok"] == true, ack["line"].as_u64().unwrap())
}

/// A line the recorder cannot use is answered whatever its op, since the lane may be waiting on it, and the refusal
/// lands in the open bundle as evidence of its own. None of it stops the scenario being recorded.
#[test]
fn every_refused_line_is_acked_and_recorded() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    let hello = h.hello(true);
    h.feed(
        &mut session,
        &[
            r#"{"op":"span","id":1,"kind":"step","key":"step:a","title":"[flow] a"}"#,
            &hello,
            r#"{"op":"span","id":1,"kind":"step","key":"step:a","title":"[flow] a"}"#,
            MINIMAL_SCENARIO,
            r#"{"op":"pause"}"#,
            r#"{"op":"span","id":2,"parent":1,"kind":"step","key":"step:b","title":"[flow] b"}"#,
            r#"{"op":"snap","span":5,"phase":"boundary"}"#,
            r#"{"op":"call","span":9,"request":"Ping","startMs":1790158500001,"durationMs":1,"ok":true}"#,
            r#"{"op":"end","id":4,"status":"passed"}"#,
            r#"{"op":"span","id":1,"kind":"step","key":"step:a","title":"[flow] a"}"#,
            r#"{"op":"span","id":2,"parent":1,"kind":"operation","key":"operation:a","title":"[operation] a"}"#,
            r#"{"op":"end","id":1,"status":"passed"}"#,
            DONE,
        ],
    );
    let answered: Vec<(bool, u64)> = h.ack_lines().iter().map(ok_and_line).collect();
    let want = [
        (false, 1),
        (true, 2),
        (false, 3),
        (true, 4),
        (false, 5),
        (false, 6),
        (false, 7),
        (false, 8),
        (false, 9),
        (true, 13),
    ];
    assert_eq!(answered, want, "{}", h.acks.text());
    // The first line is answered in the hello's shape, since that is the ack a lane waits for then.
    let text = h.acks.text();
    decode_hello_ack(text.lines().next().unwrap().as_bytes()).expect("the refusal of line 1 is a hello ack");
    let messages = error_records(&h.example_bundle()).join("\n");
    for part in [
        "line 5: ",
        "line 6: ",
        "line 7: ",
        "line 8: ",
        "line 9: ",
        "the span 1 ends while 2 inside it is open",
    ] {
        assert!(
            messages.contains(part),
            "no protocol record says {part:?}; the records are:\n{messages}"
        );
    }
    let by_id = spans(&h.example_bundle());
    assert_eq!(
        lookup_str(&by_id[&span_id(2)].attributes, attr::SPAN_STATUS),
        Some("aborted"),
        "the inner span left open"
    );
    assert_eq!(manifest_of(&h.example_bundle()).status, BundleStatus::Passed);
}

fn snapshot_of(dir: &Path) -> LogRecord {
    records(dir)
        .into_iter()
        .find(|record| record.event_name == event::SNAPSHOT)
        .unwrap_or_else(|| panic!("{} has no snapshot", dir.display()))
}

/// Without a bridge there is no tree and no log slice, and that is what this machine can do rather than a failure:
/// the snapshot is acked positively and its record says why the tree is missing.
#[test]
fn a_session_without_a_bridge_still_takes_pictures() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(&mut session, &[&h.hello(false), MINIMAL_SCENARIO, SNAP, DONE]);
    let acks = h.ack_lines();
    assert_eq!(acks.len(), 4, "{}", h.acks.text());
    assert_eq!(acks[2]["ok"], true);
    let snapshot = snapshot_of(&h.example_bundle());
    let image = snap_image_path(1);
    assert_eq!(lookup_str(&snapshot.attributes, attr::SNAPSHOT_IMAGE), Some(image.as_str()));
    assert_eq!(lookup_str(&snapshot.attributes, attr::SNAPSHOT_TREE), None);
    let error = lookup_str(&snapshot.attributes, attr::SNAPSHOT_ERROR).unwrap_or_default();
    assert!(error.contains("no tree: the lane named no bridge"), "{error}");
    assert!(avl_trace::bundle::bundle_file(&h.example_bundle(), &image).is_file());
    assert!(
        !h.example_bundle().join(IDEA_LOG_FILE).exists(),
        "a session without a bridge wrote a log slice"
    );
}

/// A second attempt at the same scenario in the same run keeps the first one's bundle.
#[test]
fn a_rerun_of_a_scenario_gets_a_bundle_of_its_own() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(
        &mut session,
        &[
            &h.hello(true),
            MINIMAL_SCENARIO,
            r#"{"op":"done","status":"failed"}"#,
            MINIMAL_SCENARIO,
            DONE,
        ],
    );
    let mut second = h.example_bundle().into_os_string();
    second.push(".2");
    assert_eq!(manifest_of(&h.example_bundle()).status, BundleStatus::Failed);
    assert_eq!(manifest_of(&PathBuf::from(second)).status, BundleStatus::Passed);
}

/// A root that cannot be created is a session that records nothing, and says so, rather than one that fails.
#[test]
fn an_unwritable_root_is_answered_with_the_reason() {
    let mut h = Harness::new();
    let blocker = h.root.join("file");
    fs::write(&blocker, "").unwrap();
    h.root = blocker;
    let mut session = h.session(h.options());
    h.feed(&mut session, &[&h.hello(true), MINIMAL_SCENARIO]);
    let text = h.acks.text();
    let ack = decode_hello_ack(text.lines().next().unwrap().as_bytes()).unwrap();
    assert!(!ack.ok && ack.capture == CaptureSource::None, "{ack:?}");
    assert!(ack.reason.unwrap_or_default().contains("cannot create"));
    let acks = h.ack_lines();
    assert_eq!(acks.len(), 2, "{text}");
    assert_eq!(acks[1]["ok"], false, "the scenario of a disabled session");
}

/// A raw video that keeps the capture times of the frames it is fed.
#[derive(Clone, Default)]
struct FakeRecording {
    state: Arc<Mutex<(Vec<i64>, bool)>>,
}

impl FakeRecording {
    fn frames(&self) -> Vec<i64> {
        crate::lock(&self.state).0.clone()
    }
}

impl FrameSink for FakeRecording {
    fn write_frame(&mut self, frame: &Frame) -> anyhow::Result<()> {
        let mut state = crate::lock(&self.state);
        if state.1 {
            bail!("stopped");
        }
        state.0.push(crate::unix_ms(frame.at));
        Ok(())
    }
}

impl Recording for FakeRecording {
    fn raw(&self) -> bool {
        true
    }

    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>> {
        Some(Box::new(self.clone()))
    }

    fn stop(&mut self) -> anyhow::Result<Stopped> {
        let mut state = crate::lock(&self.state);
        state.1 = true;
        Ok(Stopped {
            frames: state.0.len(),
            first_frame_ms: state.0[0],
        })
    }
}

struct FakeVideographer {
    recording: FakeRecording,
    dir: Arc<Mutex<Option<PathBuf>>>,
}

impl Videographer for FakeVideographer {
    fn plan(&self, _: Option<&dyn Source>, _: bool) -> VideoPlan {
        VideoPlan {
            codec: VideoCodec::H264,
            reason: String::new(),
            raw: true,
        }
    }

    fn start(&self, dir: &Path, _: &VideoPlan, first: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        if first.is_none() {
            bail!("a raw video started without its first frame");
        }
        *crate::lock(&self.dir) = Some(dir.to_owned());
        Ok(Box::new(self.recording.clone()))
    }
}

/// While a scenario is open the frame loop feeds the video from the capture source, and done stops both.
#[test]
fn the_video_is_fed_from_the_screen_while_the_scenario_is_open() {
    let h = Harness::new();
    let recording = FakeRecording::default();
    let dir = Arc::new(Mutex::new(None));
    let mut options = h.options();
    options.clock = system_clock();
    options.frame_interval = Duration::from_millis(10);
    options.video = Box::new(FakeVideographer {
        recording: recording.clone(),
        dir: dir.clone(),
    });
    let mut session = h.session(options);
    h.feed(&mut session, &[&h.hello(true), MINIMAL_SCENARIO]);
    let deadline = Instant::now() + Duration::from_secs(10);
    while recording.frames().len() < 6 && Instant::now() < deadline {
        thread::sleep(Duration::from_millis(5));
    }
    h.feed(&mut session, &[DONE]);
    let frames = recording.frames();
    assert!(frames.len() >= 6, "the video got {} frames", frames.len());
    let manifest = manifest_of(&h.example_bundle());
    assert_eq!(manifest.video, Video::H264);
    assert_eq!(crate::lock(&dir).as_deref(), Some(h.example_bundle().as_path()));
    let started = records(&h.example_bundle()).into_iter().any(|record| {
        record.event_name == event::VIDEO
            && lookup_str(&record.attributes, attr::VIDEO_CODEC) == Some("h264")
            && record.time_unix_nano == UnixNano::from_ms(frames[0])
    });
    assert!(started, "no air.video record dates the video's first frame");
    thread::sleep(Duration::from_millis(50));
    assert_eq!(recording.frames().len(), frames.len(), "the frame loop outlived the scenario");
}

/// An encoder that started and never takes its input: every write blocks until the stop ends the input, and the stop
/// then finds no frame.
struct StuckRecording {
    closing: Option<Sender<()>>,
    closed: Receiver<()>,
}

struct StuckSink(Receiver<()>);

impl FrameSink for StuckSink {
    fn write_frame(&mut self, _: &Frame) -> anyhow::Result<()> {
        let _ = self.0.recv();
        bail!("the video is stopped")
    }
}

impl Recording for StuckRecording {
    fn raw(&self) -> bool {
        true
    }

    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>> {
        Some(Box::new(StuckSink(self.closed.clone())))
    }

    fn stop(&mut self) -> anyhow::Result<Stopped> {
        self.closing = None;
        bail!("the encoder recorded no frame")
    }
}

struct StuckVideographer;

impl Videographer for StuckVideographer {
    fn plan(&self, _: Option<&dyn Source>, _: bool) -> VideoPlan {
        VideoPlan {
            codec: VideoCodec::H264,
            reason: String::new(),
            raw: true,
        }
    }

    fn start(&self, _: &Path, _: &VideoPlan, _: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        let (closing, closed) = crossbeam_channel::bounded(0);
        Ok(Box::new(StuckRecording {
            closing: Some(closing),
            closed,
        }))
    }
}

/// The first frame is written off the command loop: an encoder that never takes it must not hold the scenario's
/// ack, and so the lane, the end of the input and the recorder's exit. The bundle then says why it has no video.
#[test]
fn an_encoder_that_never_reads_does_not_hold_the_scenario_ack() {
    let h = Arc::new(Harness::new());
    let mut options = h.options();
    options.clock = system_clock();
    options.video = Box::new(StuckVideographer);
    let (acked, answered) = crossbeam_channel::bounded(1);
    let feeding = {
        let h = h.clone();
        thread::spawn(move || {
            let mut session = h.session(options);
            h.feed(&mut session, &[&h.hello(true), MINIMAL_SCENARIO]);
            let _ = acked.send(());
            session
        })
    };
    answered
        .recv_timeout(Duration::from_secs(10))
        .expect("the scenario ack waits on an encoder that never reads");
    let mut session = feeding.join().unwrap();
    h.feed(&mut session, &[DONE]);
    let answered: Vec<(bool, u64)> = h.ack_lines().iter().map(ok_and_line).collect();
    assert_eq!(answered, [(true, 1), (true, 2), (true, 3)]);
    match manifest_of(&h.example_bundle()).video {
        Video::None { reason } => assert!(reason.contains("no frame"), "{reason}"),
        video @ Video::H264 => panic!("the manifest's video is {video:?}"),
    }
}

/// A screen video run by a shell standing in for ffmpeg, which reports its progress on its output like the real one.
#[cfg(unix)]
struct ShellScreenVideographer {
    script: String,
    clock: crate::Clock,
}

#[cfg(unix)]
impl Videographer for ShellScreenVideographer {
    fn plan(&self, _: Option<&dyn Source>, _: bool) -> VideoPlan {
        VideoPlan {
            codec: VideoCodec::H264,
            reason: String::new(),
            raw: false,
        }
    }

    fn start(&self, dir: &Path, _: &VideoPlan, _: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        let argv = ["/bin/sh", "-c", self.script.as_str()].map(str::to_owned);
        Ok(Box::new(crate::video::ffmpeg::FfmpegRecording::start(
            &argv,
            dir,
            self.clock.clone(),
        )?))
    }
}

/// The recorder never sees a screen video's frames, so its `air.video` record is written at the stop, once the
/// progress reports have dated the first frame, and it names the video and its index.
#[cfg(unix)]
#[test]
fn a_screen_video_is_recorded_at_its_stop_from_the_first_progress_report() {
    use avl_trace::bundle::{VIDEO_FILE, VIDEO_INDEX_FILE, decode_video_index};
    let h = Harness::new();
    let reported_ms = T0_MS + 60_000;
    let reported = std::time::UNIX_EPOCH + Duration::from_millis(u64::try_from(reported_ms).unwrap());
    let mut options = h.options();
    options.video = Box::new(ShellScreenVideographer {
        script: format!("read key; printf 'frame=5\\nprogress=end\\n'; printf x > {VIDEO_FILE}"),
        clock: Arc::new(move || reported),
    });
    let mut session = h.session(options);
    h.feed(&mut session, &[&h.hello(true), MINIMAL_SCENARIO, DONE]);
    let bundle = h.example_bundle();
    assert_eq!(manifest_of(&bundle).video, Video::H264);
    let videos: Vec<LogRecord> = records(&bundle)
        .into_iter()
        .filter(|record| record.event_name == event::VIDEO)
        .collect();
    assert_eq!(videos.len(), 1, "the air.video records: {videos:?}");
    let video = &videos[0];
    assert_eq!(lookup_str(&video.attributes, attr::VIDEO_CODEC), Some("h264"));
    assert_eq!(lookup_str(&video.attributes, attr::VIDEO_FILE), Some(VIDEO_FILE));
    assert_eq!(lookup_str(&video.attributes, attr::VIDEO_INDEX), Some(VIDEO_INDEX_FILE));
    let first_frame_ms = reported_ms - 4 * 100;
    assert_eq!(video.time_unix_nano, UnixNano::from_ms(first_frame_ms));
    let index = decode_video_index(&fs::read(bundle.join(VIDEO_INDEX_FILE)).unwrap()).unwrap();
    assert_eq!(index.frames_ms, (0..5).map(|i| first_frame_ms + i * 100).collect::<Vec<_>>());
}

/// A scenario naming the endpoint published as it started.
fn scenario_with_bridge(name: &str, token: Option<&str>) -> String {
    let command = ScenarioCommand {
        name: name.to_owned(),
        test_class: "AirExampleUiTest".to_owned(),
        suite: None,
        lane: "UI".to_owned(),
        flow: None,
        fixture: None,
        flags: BTreeMap::default(),
        bridge: token.map(|token| Bridge {
            port: 1,
            token: token.to_owned(),
        }),
        program: None,
    };
    String::from_utf8(encode_command(&Command::Scenario(command)).unwrap()).unwrap()
}

/// One recorder serves a whole daemon iteration, and a journey that relaunches the IDE under its own launch key
/// changes the bridge's token. Each scenario names the endpoint it started with, so the scenarios after the
/// relaunch still get their trees, and one that names none keeps asking the endpoint it has.
#[test]
fn a_scenario_after_an_ide_relaunch_asks_the_new_endpoint() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(
        &mut session,
        &[&h.hello(true), &scenario_with_bridge("before", Some(GOLDEN_TOKEN)), SNAP, DONE],
    );
    h.ide.relaunch("relaunched-token");
    h.feed(
        &mut session,
        &[
            &scenario_with_bridge("after", Some("relaunched-token")),
            SNAP,
            DONE,
            &scenario_with_bridge("unnamed", None),
            SNAP,
            DONE,
        ],
    );
    for name in ["before", "after", "unnamed"] {
        let dir = bundle_dir(&h.root, "run-1", "AirExampleUiTest", name);
        let snapshot = snapshot_of(&dir);
        assert!(
            lookup_str(&snapshot.attributes, attr::SNAPSHOT_TREE).is_some(),
            "the scenario {name} got no tree: {:?}",
            lookup_str(&snapshot.attributes, attr::SNAPSHOT_ERROR)
        );
        assert!(dir.join(IDEA_LOG_FILE).is_file(), "the scenario {name} got no log slice");
    }
    assert!(h.ack_lines().iter().all(|ack| ack["ok"] == true), "{}", h.acks.text());
}

/// A lane that published no endpoint by its hello still gets one from its first scenario.
#[test]
fn a_scenario_names_the_bridge_the_hello_did_not_have() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(
        &mut session,
        &[&h.hello(false), &scenario_with_bridge("example", Some(GOLDEN_TOKEN)), SNAP, DONE],
    );
    let tree = snap_tree_path(1);
    let snapshot = snapshot_of(&h.example_bundle());
    assert_eq!(lookup_str(&snapshot.attributes, attr::SNAPSHOT_TREE), Some(tree.as_str()));
}

/// A clock step backwards between a span's start and its end ends the span where it started.
#[test]
fn a_clock_step_backwards_ends_a_span_where_it_started() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(
        &mut session,
        &[
            &h.hello(true),
            MINIMAL_SCENARIO,
            r#"{"op":"span","id":1,"kind":"step","key":"step:a","title":"[flow] a"}"#,
        ],
    );
    h.clock.advance_ms(-60_000);
    h.feed(&mut session, &[r#"{"op":"end","id":1,"status":"passed"}"#, DONE]);
    for (id, span) in spans(&h.example_bundle()) {
        assert!(
            span.end_time_unix_nano >= span.start_time_unix_nano,
            "the span {id} ends before it starts"
        );
    }
}

/// The IDE's spans join the bundle under the IDE's own resource and scopes, in the bundle's one trace. A child the
/// IDE exported before its parent still hangs under that parent, a span whose parent the bundle lacks hangs under
/// the lane span it started in, and a span from before the scenario stays out. A span still in the platform's batch
/// arrives through the flushed read at done, and one read at a restart is not lost with the IDE that goes away.
#[test]
fn the_ide_spans_join_the_bundle_under_the_spans_they_started_in() {
    const IDE_TRACE: &str = "0af7651916cd43dd8448eb211c80319c";
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.ide.export([ide_span(
        "air.sessionLog",
        "before",
        IDE_TRACE,
        "1111111111111111",
        None,
        -5,
        -4,
        &[],
    )]);
    h.feed(
        &mut session,
        &[
            &h.hello(true),
            MINIMAL_SCENARIO,
            r#"{"op":"span","id":1,"kind":"step","key":"step:open","title":"open"}"#,
        ],
    );
    // The scenario opened at 2 ms and the step at 3 ms after T0_MS, so a span from 10 ms on started inside the step.
    h.ide.export([ide_span(
        "platform",
        "child",
        IDE_TRACE,
        "2222222222222222",
        Some("3333333333333333"),
        10,
        12,
        &[
            ("air.session.id", r#""thread-1""#),
            ("air.bytes", "42"),
            ("air.cached", "true"),
            ("air.ratio", "0.5"),
        ],
    )]);
    h.clock.set_ms(T0_MS + 20);
    h.feed(&mut session, &[r#"{"op":"snap","span":1,"phase":"boundary"}"#]);
    h.ide.queue([ide_span(
        "platform",
        "parent",
        IDE_TRACE,
        "3333333333333333",
        Some("4444444444444444"),
        9,
        15,
        &[],
    )]);
    h.feed(&mut session, &[r#"{"op":"restart"}"#]);
    h.ide.queue([
        IdeSpan {
            failed: true,
            ..ide_span("air.sessionLog", "failed", IDE_TRACE, "5555555555555555", None, 30, 31, &[])
        },
        ide_span("air.sessionLog", "late", IDE_TRACE, "6666666666666666", None, 40, 41, &[]),
    ]);
    // The step ends at 23 ms, so the two spans from 30 ms on started after it, and before the done at 50 ms.
    h.feed(&mut session, &[r#"{"op":"end","id":1,"status":"passed"}"#]);
    h.clock.set_ms(T0_MS + 49);
    h.feed(&mut session, &[DONE]);

    let bundle = h.example_bundle();
    let by_id = spans(&bundle);
    assert!(
        !by_id.contains_key("1111111111111111"),
        "a span from before the scenario joined its bundle"
    );
    assert!(
        by_id.contains_key("6666666666666666"),
        "the span in the platform's batch at done never reached the bundle"
    );
    let (parent, child, failed) = (&by_id["3333333333333333"], &by_id["2222222222222222"], &by_id["5555555555555555"]);
    assert_eq!(
        child.parent_span_id, parent.span_id,
        "the child hangs under another span than its IDE parent"
    );
    assert_eq!(
        (
            parent.parent_span_id.as_str(),
            lookup_str(&parent.attributes, attr::IDE_SPAN_PARENT)
        ),
        (span_id(1).as_str(), Some("4444444444444444")),
        "the parent whose own parent the bundle lacks"
    );
    assert_eq!(
        (
            failed.parent_span_id.as_str(),
            lookup_str(&failed.attributes, attr::SPAN_STATUS),
            failed.status.as_ref().map(|status| status.code)
        ),
        (span_id(0).as_str(), Some(Status::Failed.as_str()), Some(StatusCode::ERROR)),
        "the failed span after the step"
    );
    let bundle_trace = &by_id[&span_id(0)].trace_id;
    for span in [parent, child, failed] {
        assert_eq!(
            (
                lookup_str(&span.attributes, attr::SPAN_KIND),
                lookup_str(&span.attributes, attr::IDE_TRACE_ID)
            ),
            (Some(IDE_SPAN_KIND), Some(IDE_TRACE)),
            "the IDE span {:?}",
            span.name
        );
        assert_eq!(
            &span.trace_id, bundle_trace,
            "the IDE span {:?} is in another trace than the bundle's",
            span.name
        );
    }
    let keys = ["air.session.id", "air.bytes", "air.cached", "air.ratio"];
    assert_eq!(
        keys.map(|key| lookup(&child.attributes, key).cloned()),
        [
            Some(AnyValue::StringValue("thread-1".to_owned())),
            Some(AnyValue::IntValue(I64String(42))),
            Some(AnyValue::BoolValue(true)),
            Some(AnyValue::DoubleValue(0.5)),
        ],
        "the child's attributes keep their JSON types"
    );

    let content = fs::read_to_string(bundle.join(SPANS_FILE)).unwrap();
    for line in content.lines() {
        let data = decode_traces_line(line.as_bytes()).unwrap();
        let resource = &data.resource_spans[0];
        let scope = &resource.scope_spans[0];
        let span = &scope.spans[0];
        let ide = lookup_str(&span.attributes, attr::SPAN_KIND) == Some(IDE_SPAN_KIND);
        let service = lookup_str(&resource.resource.attributes, attr::SERVICE_NAME);
        assert!(
            ide == (service == Some(IDE_SERVICE_NAME)) && ide != (scope.scope.name == SCOPE_NAME),
            "the span {:?} is written under the service {service:?} and the scope {:?}",
            span.name,
            scope.scope.name
        );
    }
    let recorded = error_records(&bundle);
    assert!(recorded.is_empty(), "the merge recorded {recorded:?}");
    assert!(h.ack_lines().iter().all(|ack| ack["ok"] == true), "{}", h.acks.text());
}

/// A bridge without the spans routes, and a ring that lost spans, are evidence loss: the first failure of a scenario
/// is written down once, a loss every time, and no ack changes.
#[test]
fn a_missing_spans_route_is_recorded_once_and_changes_no_ack() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.ide.remove_spans_routes();
    h.feed(&mut session, &[&h.hello(true), MINIMAL_SCENARIO, SNAP, SNAP, DONE]);
    let spans_errors: Vec<String> = error_records(&h.example_bundle())
        .into_iter()
        .filter(|message| message.starts_with("bridge.spans: "))
        .collect();
    assert!(
        spans_errors.len() == 1 && spans_errors[0].contains("HTTP 404"),
        "a bridge without the spans routes left {spans_errors:?}"
    );
    assert!(h.ack_lines().iter().all(|ack| ack["ok"] == true), "{}", h.acks.text());

    let lossy = Harness::new();
    let mut lossy_session = lossy.session(lossy.options());
    lossy.ide.drop_spans(7);
    lossy.feed(&mut lossy_session, &[&lossy.hello(true), MINIMAL_SCENARIO, SNAP, DONE]);
    let recorded = error_records(&lossy.example_bundle());
    assert!(
        recorded.len() == 1 && recorded[0].contains("dropped 7 of its spans"),
        "a ring that lost spans left {recorded:?}"
    );
}
