//! One snapshot: the grab, the tree, the IDE's exported spans, the still and the inputs the tree drained.

use std::time::{Duration, Instant, SystemTime};

use avl_trace::bridge::Input;
use avl_trace::bundle::{bundle_file, snap_image_path, snap_tree_path};
use avl_trace::otlp::{KeyValue, attr, event};
use avl_trace::protocol::{CaptureSource, SnapCommand};

use crate::bridge::Client;
use crate::bundle::write_file_atomically;
use crate::capture::Frame;
use crate::scenario::Scenario;
use crate::{lock, unix_ms};

impl Scenario {
    pub(crate) fn check_snap(&self, command: &SnapCommand) -> Result<(), String> {
        if command.span != 0 && !self.open.contains_key(&command.span) {
            return Err(format!("the snapshot names the span {}, which is not open", command.span));
        }
        Ok(())
    }

    /// Takes one snapshot and answers what went wrong, empty when the snapshot has everything it can have here.
    ///
    /// Only the grab and the tree happen while the lane waits: those are the moment. The picture is queued for the
    /// still writer, and the snapshot record names its file at once, so the record's position in `logs.jsonl`
    /// follows the lane's commands and not the encoder's pace. The file is the snapshot's own, or the previous
    /// picture's when the screen did not change since. A still that then fails to encode says so in an
    /// `air.trace.error` of its own.
    ///
    /// What a snapshot cannot have on this machine is not a failure: with no capture source there is no picture, and
    /// with no bridge no tree. The record still says why, and the ack stays positive, so a lane on a guest without
    /// X11 does not log a failed snapshot at every instruction.
    pub(crate) fn snap(&mut self, command: &SnapCommand, at: SystemTime, client: Option<&Client>, budget: Duration) -> String {
        self.ordinal += 1;
        let ordinal = self.ordinal;
        let deadline = Instant::now() + budget;
        let (mut failures, mut missing) = (Vec::new(), Vec::new());

        let mut taken_ms = unix_ms(at);
        let frame = match &self.source {
            None => {
                missing.push(format!("no picture: {}", self.capture_reason));
                None
            }
            Some(source) => {
                let mut frame = Frame::default();
                match source.grab(deadline, &mut frame) {
                    Ok(()) => {
                        taken_ms = unix_ms(frame.at);
                        Some(frame)
                    }
                    Err(error) => {
                        failures.push(format!("no picture: {}: {error:#}", source.kind()));
                        None
                    }
                }
            }
        };

        let mut inputs = Vec::new();
        let mut tree_written = false;
        match client {
            None => missing.push("no tree: the lane named no bridge".to_owned()),
            Some(client) => match client.tree(deadline) {
                Err(error) => failures.push(format!("no tree: {error:#}")),
                Ok((document, tree)) => {
                    inputs = tree.inputs;
                    let mut content = document.trim_ascii().to_vec();
                    content.push(b'\n');
                    let path = bundle_file(&self.dir, &snap_tree_path(ordinal));
                    match write_file_atomically(&path, &content) {
                        Ok(()) => tree_written = true,
                        Err(error) => failures.push(format!("no tree: {error}")),
                    }
                }
            },
        }

        // The IDE's spans cost the snapshot nothing it waits for: the read takes what the IDE already exported, and a
        // failure is recorded once in the scenario rather than in this snapshot.
        self.read_ide_spans(client, false, deadline, unix_ms(at));

        let mut image = None;
        if let (Some(frame), Some(stills)) = (frame, &self.stills) {
            let name = snap_image_path(ordinal);
            lock(&self.still_spans).insert(name.clone(), command.span);
            match stills.submit(deadline, name, frame) {
                Ok(named) => image = Some(named),
                Err(error) => failures.push(format!("no picture: {error:#}")),
            }
        }

        let now_ms = unix_ms((self.clock)());
        for input in &inputs {
            self.input(input, now_ms);
        }
        let mut attributes = vec![
            KeyValue::string(attr::SNAPSHOT_PHASE, command.phase.as_str()),
            KeyValue::int(attr::SNAPSHOT_ORDINAL, i64::from(ordinal)),
        ];
        if let Some(image) = &image {
            attributes.push(KeyValue::string(attr::SNAPSHOT_IMAGE, image));
        }
        if tree_written {
            attributes.push(KeyValue::string(attr::SNAPSHOT_TREE, snap_tree_path(ordinal)));
        }
        let source = match (&image, &self.source) {
            (Some(_), Some(source)) => source.kind(),
            _ => CaptureSource::None,
        };
        attributes.push(KeyValue::string(attr::SNAPSHOT_SOURCE, source.as_str()));
        let problems: Vec<&str> = failures.iter().chain(&missing).map(String::as_str).collect();
        if !problems.is_empty() {
            attributes.push(KeyValue::string(attr::SNAPSHOT_ERROR, problems.join("; ")));
        }
        self.emit(event::SNAPSHOT, command.span, taken_ms, attributes);
        failures.join("; ")
    }

    /// Records one input the bridge performed, on the innermost span that contains it.
    ///
    /// The bridge's log is drained by every tree read, so the first snapshot of a scenario can drain inputs from
    /// before it: the previous scenario's teardown. Those belong to no span of this bundle, and the viewer places a
    /// record by its span, so they are left out rather than pinned to a span they did not happen in.
    fn input(&self, input: &Input, now_ms: i64) {
        if input.at_ms < self.started_ms {
            return;
        }
        let mut attributes = vec![KeyValue::string(attr::INPUT_GESTURE, &input.gesture)];
        if let Some(point) = input.point {
            attributes.extend([
                KeyValue::int(attr::INPUT_X, i64::from(point.x)),
                KeyValue::int(attr::INPUT_Y, i64::from(point.y)),
                KeyValue::int(attr::INPUT_POINT_AT_MS, input.point_at_ms),
            ]);
        }
        let bounds = input.target.bounds;
        attributes.extend([
            KeyValue::int_array(
                attr::INPUT_TARGET_BOUNDS,
                &[bounds.x, bounds.y, bounds.width, bounds.height].map(i64::from),
            ),
            KeyValue::string(attr::INPUT_TARGET_COMPONENT, &input.target.component),
        ]);
        self.emit(event::INPUT, self.innermost(input.at_ms, now_ms), input.at_ms, attributes);
    }
}
