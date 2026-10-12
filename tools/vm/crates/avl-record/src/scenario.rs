//! One open bundle: its spans, records, video and end.

use std::collections::{HashMap, HashSet};
use std::io::Write;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime};

use avl_trace::bridge::{Facts, IdeSpan};
use avl_trace::bundle::{BundleStatus, Capture, IDEA_LOG_FILE, MANIFEST_SCHEMA, Manifest, Video, format_manifest_time};
use avl_trace::otlp::{
    IDE_SERVICE_NAME, IDE_SPAN_KIND, KeyValue, LogRecord, Resource, SERVICE_NAME, SeverityNumber, Span, SpanStatus, attr, event, span_id,
    status_code_of, trace_id,
};
use avl_trace::protocol::{
    CallCommand, CaptureSource, DriverStep, DriverStepsCommand, EndCommand, Failure, Launcher, ScenarioCommand, SpanCommand, Status,
    VideoCodec,
};

use crate::bridge::Client;
use crate::bundle::{BundleWriter, nanos, unique_bundle_dir, write_manifest};
use crate::capture::{Frame, Source};
use crate::frames::FrameLoop;
use crate::idealog::LogSlicer;
use crate::ladder::Videographer;
use crate::session::{Session, source_kind};
use crate::stills;
use crate::video::Recording;
use crate::{Clock, Diagnostics, lock, unix_ms};

/// Bounds the grab a raw video starts with, which the scenario ack waits for.
const FIRST_FRAME_TIMEOUT: Duration = Duration::from_secs(1);

/// Bounds a read of the flushed spans route, at a restart and at the scenario's end. The done ack waits for the
/// second one, so a flush that is slow costs the bundle the IDE's last spans rather than the lane its time.
pub(crate) const FLUSHED_SPANS_TIMEOUT: Duration = Duration::from_secs(2);

// The sources of an `air.trace.error` record that the contract does not spell itself.
pub(crate) const ERROR_SOURCE_PROTOCOL: &str = "protocol";
const ERROR_SOURCE_FACTS: &str = "bridge.facts";
const ERROR_SOURCE_SPANS: &str = "bridge.spans";
const ERROR_SOURCE_LOG: &str = "idea.log";
const ERROR_SOURCE_WEBP: &str = "webp";
pub(crate) const ERROR_SOURCE_VIDEO: &str = "video";
const ERROR_SOURCE_CAPTURE: &str = "capture";

pub(crate) struct OpenSpan {
    command: SpanCommand,
    start_ms: i64,
}

struct Interval {
    id: u32,
    start_ms: i64,
    end_ms: i64,
}

/// One open bundle.
pub(crate) struct Scenario {
    pub(crate) command: ScenarioCommand,
    pub(crate) dir: PathBuf,
    pub(crate) writer: Arc<BundleWriter>,
    pub(crate) clock: Clock,
    diagnostics: Diagnostics,
    run_id: String,
    launcher: Launcher,
    pub(crate) started_ms: i64,

    pub(crate) open: HashMap<u32, OpenSpan>,
    pub(crate) stack: Vec<u32>,
    /// Every span that has ended, which is what a driver step or an input is matched against.
    ended: Vec<Interval>,
    /// Every lane id this scenario has used.
    opened: HashSet<u32>,

    pub(crate) ordinal: u32,
    pub(crate) stills: Option<stills::Writer>,
    /// Maps a queued still's bundle path to its snapshot's span, for a failure the still writer reports from its own
    /// thread.
    pub(crate) still_spans: Arc<Mutex<HashMap<String, u32>>>,
    slicer: LogSlicer,
    sliced: bool,

    pub(crate) source: Option<Arc<dyn Source>>,
    pub(crate) capture_reason: String,
    recording: Option<Box<dyn Recording>>,
    frames: Option<FrameLoop>,
    video: Video,
    /// When a screen recording started, for its `air.video` record.
    video_started_ms: i64,

    /// Every span the spans routes answered in this scenario, by its IDE trace and span id. They are written at the
    /// end, when every lane span's interval is known and every IDE parent has arrived: the IDE exports a child
    /// before its parent, because the child ends first.
    ide_spans: HashMap<(String, String), IdeSpan>,
    /// Whether a spans read already failed in this scenario. The first failure is recorded, and the later ones are
    /// not: a lane without the route would otherwise log one at every snapshot.
    spans_failed: bool,
}

impl<W: Write> Session<W> {
    /// Opens a scenario's bundle. A scenario that arrives while another is open means the lane lost that one's done;
    /// it is finished as truncated first, rather than refusing the new one and losing it too.
    pub(crate) fn open_scenario(&mut self, command: ScenarioCommand, at: SystemTime) {
        let at_ms = unix_ms(at);
        if let Some(previous) = &self.scenario {
            previous.trace_error(
                ERROR_SOURCE_PROTOCOL,
                &format!("line {} opens the scenario {} before this one's done", self.line, command.name),
                at_ms,
            );
            self.finish_scenario(BundleStatus::Truncated, at);
        }
        let Some(hello) = self.hello.clone() else {
            return;
        };
        let dir = match unique_bundle_dir(hello.root.as_ref(), &hello.run_id, &command.test_class, &command.name) {
            Ok(dir) => dir,
            Err(error) => {
                let error = format!("cannot create the bundle directory: {error:#}");
                return self.ack(false, Some(error));
            }
        };
        let resource_of = |service: &str| Resource {
            attributes: vec![
                KeyValue::string(attr::SERVICE_NAME, service),
                KeyValue::string(attr::RUN_ID, &hello.run_id),
                KeyValue::string(attr::LANE, &command.lane),
                KeyValue::string(attr::LAUNCHER, hello.launcher.as_str()),
                KeyValue::string(attr::HOST_NAME, &self.options.host_name),
                KeyValue::string(attr::OS_TYPE, &self.options.os_type),
            ],
            ..Resource::default()
        };
        let writer = match BundleWriter::open(
            &dir,
            trace_id(&hello.run_id, &command.test_class, &command.name),
            resource_of(SERVICE_NAME),
            resource_of(IDE_SERVICE_NAME),
            self.options.clock.clone(),
            self.options.diagnostics.clone(),
        ) {
            Ok(writer) => Arc::new(writer),
            Err(error) => {
                return self.ack(false, Some(format!("cannot create the bundle: {error}")));
            }
        };
        let scenario = Scenario {
            dir,
            writer,
            clock: self.options.clock.clone(),
            diagnostics: self.options.diagnostics.clone(),
            run_id: hello.run_id.clone(),
            launcher: hello.launcher,
            started_ms: at_ms,
            open: HashMap::new(),
            stack: Vec::new(),
            ended: Vec::new(),
            opened: HashSet::from([0]),
            ordinal: 0,
            stills: None,
            still_spans: Arc::default(),
            slicer: LogSlicer::default(),
            sliced: false,
            source: None,
            capture_reason: String::new(),
            recording: None,
            frames: None,
            video: Video::None {
                reason: "the video did not start".to_owned(),
            },
            video_started_ms: 0,
            ide_spans: HashMap::new(),
            spans_failed: false,
            command,
        };
        scenario.emit(
            event::SPAN_STARTED,
            0,
            at_ms,
            vec![KeyValue::string(attr::SPAN_TITLE, &scenario.command.name)],
        );
        let name = scenario.command.name.clone();
        let bridge = scenario.command.bridge.clone();
        self.scenario = Some(scenario);
        if self.use_bridge(bridge.as_ref()) {
            say!(
                self.options.diagnostics,
                "{name}: the lane names another bridge endpoint, which the recorder asks from here on"
            );
        }

        // Without a bridge there are no facts to ask for, which is this lane's shape rather than a failure, so only a
        // bridge that does not answer is recorded.
        let facts = self.facts();
        let note = self.climb(facts.as_ref().ok());
        let (source, reason) = (self.source.clone(), self.source_reason.clone());
        let has_client = self.client.is_some();
        let Some(scenario) = self.scenario.as_mut() else {
            return;
        };
        match &facts {
            Err(error) => {
                if has_client {
                    scenario.trace_error(ERROR_SOURCE_FACTS, &format!("no idea.log slice: {error:#}"), at_ms);
                }
            }
            Ok(facts) => match scenario.slicer.begin(facts) {
                Ok(()) => scenario.sliced = true,
                Err(error) => scenario.trace_error(ERROR_SOURCE_LOG, &format!("{error:#}"), at_ms),
            },
        }
        if let Some(note) = note {
            scenario.trace_error(ERROR_SOURCE_CAPTURE, &note, at_ms);
        }
        scenario.source = source;
        scenario.capture_reason = reason;
        scenario.stills = Some(stills::Writer::new(&scenario.dir, scenario.still_failed()));
        let screen = hello.screen;
        scenario.start_video(&*self.options.video, screen, self.options.frame_interval, at_ms);
        self.ack(true, None);
    }
}

impl Scenario {
    /// Starts the scenario's video, or records why there is none.
    fn start_video(&mut self, videographer: &dyn Videographer, screen: bool, interval: Duration, at_ms: i64) {
        let plan = videographer.plan(self.source.as_deref(), screen);
        if plan.codec != VideoCodec::H264 {
            return self.no_video(plan.reason, at_ms);
        }
        let first = if plan.raw {
            let Some(source) = &self.source else {
                return self.no_video("nothing is captured".to_owned(), at_ms);
            };
            let mut frame = Frame::default();
            if let Err(error) = source.grab(Instant::now() + FIRST_FRAME_TIMEOUT, &mut frame) {
                return self.no_video(format!("the first frame could not be read: {error:#}"), at_ms);
            }
            Some(frame)
        } else {
            None
        };
        let mut recording = match videographer.start(&self.dir, &plan, first.as_ref()) {
            Ok(recording) => recording,
            Err(error) => return self.no_video(format!("{error:#}"), at_ms),
        };
        self.video = Video::H264;
        if !plan.raw {
            self.video_started_ms = at_ms;
            self.recording = Some(recording);
            return;
        }
        let (Some(first), Some(source), Some(sink)) = (first, self.source.clone(), recording.take_sink()) else {
            let _ = recording.stop();
            return self.no_video("the raw video has no input to write to".to_owned(), at_ms);
        };
        // The loop writes the first frame too, so an encoder that never reads cannot hold the ack. One that fails
        // that write is reported by the loop, and its stop at the scenario's end answers no frame, which replaces
        // this video with the reason there is none.
        self.emit_video_started(unix_ms(first.at));
        // The loop reports from its own thread, which cannot read the span stack, so its records go on the root;
        // they are about the whole video anyway.
        let (writer, clock) = (self.writer.clone(), self.clock.clone());
        self.frames = Some(FrameLoop::start(
            source,
            sink,
            first,
            interval,
            Box::new(move |source, error| {
                writer.trace_error_on(0, source, &format!("{error:#}"), unix_ms(clock()));
            }),
        ));
        self.recording = Some(recording);
    }

    fn no_video(&mut self, reason: String, at_ms: i64) {
        self.emit(
            event::VIDEO,
            0,
            at_ms,
            vec![
                KeyValue::string(attr::VIDEO_CODEC, VideoCodec::None.as_str()),
                KeyValue::string(attr::VIDEO_REASON, &reason),
            ],
        );
        self.video = Video::None { reason };
    }

    fn emit_video_started(&self, at_ms: i64) {
        self.emit(
            event::VIDEO,
            0,
            at_ms,
            vec![
                KeyValue::string(attr::VIDEO_CODEC, VideoCodec::H264.as_str()),
                KeyValue::string(attr::VIDEO_FILE, avl_trace::bundle::VIDEO_FILE),
                KeyValue::string(attr::VIDEO_INDEX, avl_trace::bundle::VIDEO_INDEX_FILE),
            ],
        );
    }

    // --- spans -------------------------------------------------------------------------------------------------

    pub(crate) fn open_span(&mut self, command: &SpanCommand, at: SystemTime) -> Result<(), String> {
        if self.opened.contains(&command.id) {
            return Err(format!("the span {} was already opened in this scenario", command.id));
        }
        if command.parent != 0 && !self.open.contains_key(&command.parent) {
            return Err(format!(
                "the span {} names the parent {}, which is not open",
                command.id, command.parent
            ));
        }
        let at_ms = unix_ms(at);
        self.opened.insert(command.id);
        self.open.insert(
            command.id,
            OpenSpan {
                command: command.clone(),
                start_ms: at_ms,
            },
        );
        self.stack.push(command.id);
        self.emit(
            event::SPAN_STARTED,
            command.id,
            at_ms,
            vec![
                KeyValue::string(attr::SPAN_KIND, command.kind.as_str()),
                KeyValue::string(attr::SPAN_KEY, &command.key),
                KeyValue::string(attr::SPAN_TITLE, &command.title),
                KeyValue::string(attr::SPAN_PARENT, span_id(command.parent)),
            ],
        );
        Ok(())
    }

    /// Closes a span. Spans nest, so ending one that is open but not innermost means the lane lost the inner ones'
    /// ends; they are closed first, as aborted, rather than left open to the end of the bundle.
    pub(crate) fn end_span(&mut self, command: &EndCommand, at: SystemTime) -> Result<(), String> {
        if !self.open.contains_key(&command.id) {
            return Err(format!("the span {} is not open", command.id));
        }
        let at_ms = unix_ms(at);
        while let Some(&inner) = self.stack.last()
            && inner != command.id
        {
            self.trace_error(
                ERROR_SOURCE_PROTOCOL,
                &format!("the span {} ends while {inner} inside it is open", command.id),
                at_ms,
            );
            self.close_span(inner, Status::Aborted, None, at_ms);
        }
        self.close_span(command.id, command.status, command.error.as_ref(), at_ms);
        Ok(())
    }

    fn close_span(&mut self, id: u32, status: Status, failure: Option<&Failure>, at_ms: i64) {
        let Some(opened) = self.open.remove(&id) else {
            return;
        };
        self.stack.retain(|open| *open != id);
        // Receipts are wall-clock stamps, and the Linux guest's clock is stepped now and then. A span that the step
        // would end before it started ends where it started instead, which is the one end the contract accepts.
        let at_ms = at_ms.max(opened.start_ms);
        self.ended.push(Interval {
            id,
            start_ms: opened.start_ms,
            end_ms: at_ms,
        });
        let mut attributes = vec![
            KeyValue::string(attr::SPAN_KIND, opened.command.kind.as_str()),
            KeyValue::string(attr::SPAN_KEY, &opened.command.key),
            KeyValue::string(attr::SPAN_STATUS, status.as_str()),
        ];
        if let Some(expectation) = opened.command.expectation.as_deref().filter(|e| !e.is_empty()) {
            attributes.push(KeyValue::string(attr::EXPECTATION, expectation));
        }
        let mut span_status = SpanStatus {
            code: status_code_of(status),
            ..SpanStatus::default()
        };
        if let Some(failure) = failure {
            span_status.message = match failure.message.as_deref().filter(|m| !m.is_empty()) {
                Some(message) => format!("{}: {message}", failure.r#type),
                None => failure.r#type.clone(),
            };
            self.exception(id, failure, status, at_ms);
        }
        self.writer.span(Span {
            span_id: span_id(id),
            parent_span_id: span_id(opened.command.parent),
            name: opened.command.title.clone(),
            start_time_unix_nano: nanos(opened.start_ms),
            end_time_unix_nano: nanos(at_ms),
            attributes,
            status: Some(span_status),
            ..Span::default()
        });
    }

    fn exception(&self, id: u32, failure: &Failure, status: Status, at_ms: i64) {
        let (severity, text) = if status == Status::Failed {
            (SeverityNumber::ERROR, "ERROR")
        } else {
            (SeverityNumber::WARN, "WARN")
        };
        let mut attributes = vec![KeyValue::string(attr::EXCEPTION_TYPE, &failure.r#type)];
        if let Some(message) = failure.message.as_deref().filter(|m| !m.is_empty()) {
            attributes.push(KeyValue::string(attr::EXCEPTION_MESSAGE, message));
        }
        if !failure.stack.is_empty() {
            attributes.push(KeyValue::string(attr::EXCEPTION_STACKTRACE, failure.stack.join("\n")));
        }
        self.writer.record(LogRecord {
            time_unix_nano: nanos(at_ms),
            severity_number: severity,
            severity_text: text.to_owned(),
            span_id: span_id(id),
            event_name: event::EXCEPTION.to_owned(),
            attributes,
            ..LogRecord::default()
        });
    }

    // --- records -----------------------------------------------------------------------------------------------

    pub(crate) fn emit(&self, event: &str, id: u32, at_ms: i64, attributes: Vec<KeyValue>) {
        self.writer.record(LogRecord {
            time_unix_nano: nanos(at_ms),
            span_id: span_id(id),
            event_name: event.to_owned(),
            attributes,
            ..LogRecord::default()
        });
    }

    /// Records evidence that could not be collected, on the innermost open span, where it happened. It reads the
    /// span stack, so only the command loop calls it; a thread of its own calls [BundleWriter::trace_error_on].
    pub(crate) fn trace_error(&self, source: &str, message: &str, at_ms: i64) {
        let id = self.stack.last().copied().unwrap_or(0);
        self.writer.trace_error_on(id, source, message, at_ms);
    }

    /// The span that contains `instant` and started last, the root when no other does: spans nest, so the latest
    /// start among the containing ones is the innermost. Open spans count as ending `now`. Two spans that started in
    /// the same millisecond are told apart by their ids, since the inner one was opened second.
    pub(crate) fn innermost(&self, instant_ms: i64, now_ms: i64) -> u32 {
        let (mut best, mut best_start) = (0, self.started_ms);
        let ended = self.ended.iter().map(|span| (span.id, span.start_ms, span.end_ms));
        let open = self.open.iter().map(|(id, span)| (*id, span.start_ms, now_ms));
        for (id, start, end) in ended.chain(open) {
            if start > instant_ms || instant_ms > end {
                continue;
            }
            if start > best_start || (start == best_start && id > best) {
                (best, best_start) = (id, start);
            }
        }
        best
    }

    pub(crate) fn call(&self, command: &CallCommand) -> Result<(), String> {
        if !self.opened.contains(&command.span) {
            return Err(format!(
                "the call {} names the span {}, which this scenario never opened",
                command.request, command.span
            ));
        }
        let mut attributes = vec![
            KeyValue::string(attr::CALL_REQUEST, &command.request),
            KeyValue::int(attr::CALL_DURATION_MS, command.duration_ms),
            KeyValue::bool(attr::CALL_OK, command.ok),
        ];
        if let Some(error) = command.error.as_deref().filter(|e| !e.is_empty()) {
            attributes.push(KeyValue::string(attr::CALL_ERROR, error));
        }
        let (severity_number, severity_text) = if command.ok {
            (SeverityNumber::UNSPECIFIED, String::new())
        } else {
            (SeverityNumber::WARN, "WARN".to_owned())
        };
        self.writer.record(LogRecord {
            time_unix_nano: nanos(command.start_ms),
            severity_number,
            severity_text,
            span_id: span_id(command.span),
            event_name: event::BRIDGE_CALL.to_owned(),
            attributes,
            ..LogRecord::default()
        });
        Ok(())
    }

    /// Records the Driver's Allure steps, flattened in pre-order, each on the innermost span that contains its
    /// start. They arrive at the scenario's end, when every span's interval is known.
    pub(crate) fn driver_steps(&self, command: &DriverStepsCommand, at: SystemTime) {
        let mut id = 0;
        self.walk_steps(&command.steps, 0, &mut id, unix_ms(at));
    }

    fn walk_steps(&self, steps: &[DriverStep], parent: i64, id: &mut i64, at_ms: i64) {
        for step in steps {
            *id += 1;
            let own = *id;
            let mut attributes = vec![KeyValue::string(attr::DRIVER_STEP_NAME, &step.name)];
            if let Some(status) = step.status {
                attributes.push(KeyValue::string(attr::DRIVER_STEP_STATUS, status.as_str()));
            }
            if step.stop != 0 {
                attributes.push(KeyValue::int(attr::DRIVER_STEP_DURATION_MS, step.stop - step.start));
            }
            attributes.push(KeyValue::int(attr::DRIVER_STEP_ID, own));
            attributes.push(KeyValue::int(attr::DRIVER_STEP_PARENT, parent));
            self.emit(event::DRIVER_STEP, self.innermost(step.start, at_ms), step.start, attributes);
            self.walk_steps(&step.steps, own, id, at_ms);
        }
    }

    /// The still writer's failure report: a still that is not on disk. The error names the file as the snapshot
    /// records do, since a later snapshot of the same screen may name it too.
    fn still_failed(&self) -> stills::Failed {
        let (writer, clock, spans) = (self.writer.clone(), self.clock.clone(), self.still_spans.clone());
        Box::new(move |name, error| {
            let span = lock(&spans).get(name).copied().unwrap_or(0);
            writer.trace_error_on(span, ERROR_SOURCE_WEBP, &format!("{name}: {error:#}"), unix_ms(clock()));
        })
    }

    // --- the IDE's spans ---------------------------------------------------------------------------------------

    /// Reads the spans the IDE ended since the last read, and keeps them for [Scenario::write_ide_spans].
    ///
    /// A failed read is evidence loss and never a verdict. The first failure of a scenario is recorded on the
    /// innermost open span, so only the command loop calls this. A loss the IDE's own ring reports is recorded every
    /// time.
    pub(crate) fn read_ide_spans(&mut self, client: Option<&Client>, flushed: bool, deadline: Instant, at_ms: i64) {
        let Some(client) = client else {
            return;
        };
        let answer = match client.spans(flushed, deadline) {
            Ok(answer) => answer,
            Err(error) => {
                if !std::mem::replace(&mut self.spans_failed, true) {
                    self.trace_error(ERROR_SOURCE_SPANS, &format!("no IDE spans: {error:#}"), at_ms);
                }
                return;
            }
        };
        if answer.dropped > 0 {
            self.trace_error(
                ERROR_SOURCE_SPANS,
                &format!("the IDE dropped {} of its spans before the recorder read them", answer.dropped),
                at_ms,
            );
        }
        for span in answer.spans {
            self.ide_spans.insert((span.trace_id.clone(), span.span_id.clone()), span);
        }
    }

    /// Writes the IDE spans that started in this scenario, oldest first, into the bundle's one trace.
    ///
    /// A span keeps its IDE parent when that parent is in the bundle too. Otherwise it is the child of the innermost
    /// lane span that contains its start, the rule a Driver step follows, and [attr::IDE_SPAN_PARENT] keeps the lost
    /// id. A span that started before the scenario belongs to the scenario before. It is left out, as an early input
    /// is.
    fn write_ide_spans(&mut self, at_ms: i64) {
        let started_ms = self.started_ms;
        let kept: HashMap<(String, String), IdeSpan> = std::mem::take(&mut self.ide_spans)
            .into_iter()
            .filter(|(_, span)| epoch_ms(span.start_epoch_nanos) >= started_ms)
            .collect();
        let mut spans: Vec<&IdeSpan> = kept.values().collect();
        spans.sort_by(|left, right| (left.start_epoch_nanos, &left.span_id).cmp(&(right.start_epoch_nanos, &right.span_id)));
        let mut written: HashSet<String> = self.opened.iter().map(|id| span_id(*id)).collect();
        for span in spans {
            if !written.insert(span.span_id.clone()) {
                self.writer.trace_error_on(
                    0,
                    ERROR_SOURCE_SPANS,
                    &format!(
                        "the IDE span {:?} has the span id {}, which the bundle already holds",
                        span.name, span.span_id
                    ),
                    at_ms,
                );
                continue;
            }
            let start_ms = epoch_ms(span.start_epoch_nanos);
            let end_ms = epoch_ms(span.end_epoch_nanos).max(start_ms);
            let status = if span.failed { Status::Failed } else { Status::Passed };
            let mut attributes = vec![
                KeyValue::string(attr::SPAN_KIND, IDE_SPAN_KIND),
                KeyValue::string(attr::SPAN_STATUS, status.as_str()),
                KeyValue::string(attr::IDE_TRACE_ID, &span.trace_id),
            ];
            let parent = match &span.parent_span_id {
                Some(parent) if kept.contains_key(&(span.trace_id.clone(), parent.clone())) => parent.clone(),
                lost => {
                    if let Some(lost) = lost {
                        attributes.push(KeyValue::string(attr::IDE_SPAN_PARENT, lost));
                    }
                    span_id(self.innermost(start_ms, at_ms))
                }
            };
            attributes.extend(span.otlp_attributes());
            self.writer.ide_span(
                &span.scope,
                Span {
                    span_id: span.span_id.clone(),
                    parent_span_id: parent,
                    name: span.name.clone(),
                    start_time_unix_nano: nanos(start_ms),
                    end_time_unix_nano: nanos(end_ms),
                    attributes,
                    status: Some(SpanStatus {
                        code: status_code_of(status),
                        ..SpanStatus::default()
                    }),
                    ..Span::default()
                },
            );
        }
    }

    // --- the end -----------------------------------------------------------------------------------------------

    /// Closes the bundle: open spans, the IDE's spans, the video, the stills, the log slice, the root span and, last,
    /// the manifest. A truncated bundle names the innermost span that was still running. `latest` reads the facts at
    /// the end, after the video and the stills are done, so a log that rotated meanwhile is still found.
    pub(crate) fn finish(mut self, status: BundleStatus, at_ms: i64, client: Option<&Client>, latest: &dyn Fn() -> Option<Facts>) {
        let innermost = self.stack.last().copied();
        let running = (status == BundleStatus::Truncated).then(|| span_id(innermost.unwrap_or(0)));
        if status != BundleStatus::Truncated && innermost.is_some() {
            self.trace_error(
                ERROR_SOURCE_PROTOCOL,
                &format!("done with the spans [{}] still open", join_ids(&self.stack)),
                at_ms,
            );
        }
        while let Some(&inner) = self.stack.last() {
            self.close_span(inner, Status::Aborted, None, at_ms);
        }
        self.read_ide_spans(client, true, Instant::now() + FLUSHED_SPANS_TIMEOUT, at_ms);
        self.write_ide_spans(at_ms);

        self.stop_video();
        if let Some(stills) = self.stills.take() {
            let (files, repeats) = stills.close();
            if files + repeats > 0 {
                say!(
                    self.diagnostics,
                    "{}: {} stills in {files} files, {repeats} of them a repeat of the previous one",
                    self.command.name,
                    files + repeats
                );
            }
        }
        if self.sliced
            && let Err(error) = self.slicer.cut(&self.dir.join(IDEA_LOG_FILE), latest().as_ref())
        {
            self.trace_error(ERROR_SOURCE_LOG, &format!("{error:#}"), at_ms);
        }

        let root_status = match status {
            BundleStatus::Passed => Status::Passed,
            BundleStatus::Failed => Status::Failed,
            BundleStatus::Aborted | BundleStatus::Truncated => Status::Aborted,
        };
        let at_ms = at_ms.max(self.started_ms);
        self.writer.span(Span {
            span_id: span_id(0),
            name: self.command.name.clone(),
            start_time_unix_nano: nanos(self.started_ms),
            end_time_unix_nano: nanos(at_ms),
            attributes: self.root_attributes(root_status),
            status: Some(SpanStatus {
                code: status_code_of(root_status),
                ..SpanStatus::default()
            }),
            ..Span::default()
        });
        self.writer.close();

        let source = source_kind(self.source.as_deref());
        let reason = match (source, self.capture_reason.as_str()) {
            (CaptureSource::None, "") => Some("nothing to capture with".to_owned()),
            (_, "") => None,
            (_, reason) => Some(reason.to_owned()),
        };
        let started = jiff::Timestamp::from_millisecond(self.started_ms).unwrap_or_default();
        let manifest = Manifest {
            schema: MANIFEST_SCHEMA.to_owned(),
            run_id: self.run_id.clone(),
            test_class: self.command.test_class.clone(),
            scenario: self.command.name.clone(),
            flow: self.command.flow.clone().filter(|flow| !flow.is_empty()),
            lane: self.command.lane.clone(),
            launcher: self.launcher,
            status,
            running_span: running,
            started_at: format_manifest_time(started),
            duration_ms: u64::try_from(at_ms - self.started_ms).unwrap_or(0),
            capture: Capture { source, reason },
            video: self.video.clone(),
        };
        if let Err(error) = write_manifest(&self.dir, &manifest) {
            say!(self.diagnostics, "{}: {error:#}", self.dir.display());
        }
    }

    /// Stops the frame loop and the recording, in the order that cannot hang: the loop is asked to stop first, the
    /// recording's stop refuses every later frame, which ends a loop that is still writing, and only then is the loop
    /// waited for.
    fn stop_video(&mut self) {
        let Some(mut recording) = self.recording.take() else {
            return;
        };
        if let Some(frames) = &self.frames {
            frames.halt();
        }
        let result = recording.stop();
        if let Some(frames) = self.frames.take() {
            let counts = frames.wait();
            say!(
                self.diagnostics,
                "{}: {} video frames, {} of them a changed screen",
                self.command.name,
                counts.written,
                counts.changed
            );
        }
        let now_ms = unix_ms((self.clock)());
        match result {
            Err(error) => {
                let reason = format!("{error:#}");
                self.trace_error(ERROR_SOURCE_VIDEO, &reason, now_ms);
                self.video = Video::None { reason };
            }
            Ok(stopped) => {
                if !recording.raw() {
                    self.emit_video_started(stopped.first_frame_ms.max(self.video_started_ms));
                }
            }
        }
    }

    fn root_attributes(&self, status: Status) -> Vec<KeyValue> {
        let command = &self.command;
        let mut attributes = vec![
            KeyValue::string(attr::SCENARIO_NAME, &command.name),
            KeyValue::string(attr::TEST_CLASS, &command.test_class),
        ];
        let optional = [
            (attr::SUITE, &command.suite),
            (attr::FLOW_ID, &command.flow),
            (attr::FIXTURE, &command.fixture),
        ];
        for (key, value) in optional {
            if let Some(value) = value.as_deref().filter(|value| !value.is_empty()) {
                attributes.push(KeyValue::string(key, value));
            }
        }
        let flags = avl_trace::encode(&command.flags)
            .ok()
            .and_then(|flags| String::from_utf8(flags).ok())
            .unwrap_or_else(|| "{}".to_owned());
        attributes.push(KeyValue::string(attr::FLAGS, flags));
        if let Some(program) = &command.program {
            attributes.push(KeyValue::string(attr::PROGRAM, program.get()));
        }
        attributes.push(KeyValue::string(attr::SPAN_STATUS, status.as_str()));
        attributes
    }
}

/// Epoch nanoseconds cut to the milliseconds every time of the bundle is written in ([nanos]).
const fn epoch_ms(epoch_nanos: i64) -> i64 {
    epoch_nanos / 1_000_000
}

/// The ids as a space-separated list, the way the lane's own messages print one.
fn join_ids(ids: &[u32]) -> String {
    ids.iter().map(u32::to_string).collect::<Vec<_>>().join(" ")
}
