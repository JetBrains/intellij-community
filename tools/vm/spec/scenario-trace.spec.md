---
name: Scenario Traces
description: Requirements for the trace bundle a UI scenario leaves, the recorder's protocol, the capture, the pack, the server and the planner.
style: plain-1
targets:
  - ../crates/avl-trace/**
  - ../crates/avl-record/**
  - ../crates/air-trace/**
  - ../crates/avl-guest/src/tracepack.rs
  - ../crates/avl-guest/src/lane/trace.rs
  - ../crates/avl-guest/src/cli.rs
  - ../crates/avl-vm/src/daemon/iterate.rs
  - ../crates/avl-vm/src/daemon/traces.rs
  - ../crates/avl-vm/src/daemon/verdict.rs
  - ../crates/avl-base/src/plain.rs
  - ../crates/avl-report/src/report.rs
  - ../crates/avl-wire/src/report.rs
  - ../crates/avl-vm/src/worker/docker.rs
  - ../crates/avl-host-sys/src/viewer.rs
  - ../../trace.cmd
---

# Scenario Traces

Status: Draft
Date: 2026-10-07

## Summary

Every Air UI scenario leaves a trace bundle, in the manner of the Playwright trace viewer. A bundle holds a
video, a picture and a Swing tree at every instruction boundary and held check, and the gestures. It
also holds the bridge calls, the scenario's slice of `idea.log`, and the generated program the scenario ran. The
OpenTelemetry spans that the IDE ended in the scenario are in it too, under the lane step that caused them.

A Rust sidecar, `air-trace-record`, writes the bundle while the lane runs. `air-trace serve` shows the bundles in the
Air docs site. `air-trace plan` says what to run to record new ones.

[Flow UI Scenario Traces](../../../../plugins/air/spec/docs/flow-trace.spec.md) specifies the lane's side of a trace,
the bridge routes and the viewer.

The contract is declared once, in Rust, in `community/tools/vm/crates/avl-trace`. Two golden files there
pin the viewer to it.

## Goals

- A bundle is self-contained, so a CI publisher can adopt it later without a viewer change.
- A killed lane still leaves a readable bundle, marked as truncated.
- The parts that must run are Rust, so a guest needs no JVM to record, pack or serve.

## Non-goals

- Deciding a verdict from a capture problem. A trace is evidence, and every capture problem is recorded instead of
  thrown. The status of a bundle that arrived is the second account of its scenario, which the run report states.
- Uploading traces. Storage is local, and CI storage is a later decision.
- A generic OpenTelemetry viewer. The bundle is OTLP JSON so that such tools read it, but the viewer is
  Air's own.

## Requirements

### The bundle

- A bundle is one directory, `<root>/<runId>/<TestClass>/<scenario>/`, or the same tree inside a zip.
  `sanitize_name` makes each segment portable. A second attempt of one scenario in one run is written to
  `<scenario>.2`, then `.3`.
  [@test] ../crates/avl-trace/src/bundle/tests.rs
  [@test] ../crates/avl-record/src/tests.rs

- A reader finds a bundle by its `spans.jsonl` and names it by its manifest, never by its path.
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs
  [@test] ../crates/air-trace/src/serve/tests.rs

- Without a manifest, a directory bundle is `running` while its files change, and `truncated` five minutes
  after they stop. A bundle in a zip, or one bundle file read alone, is `truncated`. A manifest that does not
  decode is `invalid`, with the reason.
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs
  [@test] ../crates/air-trace/src/serve/tests.rs
  [@test] ../crates/air-trace/src/plan/tests.rs

- A bundle holds `spans.jsonl`, `logs.jsonl`, and `video.mp4` with `video.index.json`. It holds a
  `snap/NNNN.tree.json` for each snapshot, and a `snap/NNNN.webp` or `snap/NNNN.avif` for each snapshot whose screen
  changed. It also holds an `attach/NNNN.<ext>` for each attachment, `idea.log` and `bundle.json`. In a scenario with a
  video, a PNG attachment is an `attach/NNNN.avif`. A file the recorder could not produce is absent, and the manifest
  or a log record says why.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- `bundle.json` is written last. Its `schema` is `air-trace/1`, and a reader refuses any other value. It
  names the run, the test class, the scenario, the flow, the lane, the launcher and the status.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- The manifest names the capture source and the video codec. Each carries a reason when it is `none`. A
  bundle without a manifest is still being written, or its recorder was killed outright.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- The manifest names lossy pictures as `"stills":{"encoding":"lossy"}`. A manifest without `stills` has lossless
  pictures, and so has every bundle of an earlier recorder.
  [@test] ../crates/avl-trace/src/bundle/tests.rs (`the_manifest_names_lossy_stills_only`)

- The status is `passed`, `failed`, `aborted` or `truncated`. A truncated manifest names the running span by
  its OTLP id, and only a truncated one does.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- `video.mp4` is H.264 in fragmented MP4 at a constant frame rate, without B-frames. Frame i plays at i over
  the frame rate, and the first frame plays at 0. `video.index.json` lists the capture time of every frame in order.
  An unchanged screen is still a frame, so the index has no gaps.
  [@test] ../crates/avl-record/src/video/h264/tests.rs
  [@test] ../crates/avl-record/src/video/mp4/tests.rs
  [@test] ../crates/avl-record/src/frames/tests.rs

- A snapshot's picture is WebP or AVIF, and its tree is the bridge's tree document. The extension of a picture names
  its format and its media type. A snapshot of an unchanged screen gets no picture file. Its `air.snapshot.image`
  names the previous picture's file. So the last picture file by name is the last snapshot's picture.
  [@test] ../crates/avl-record/src/stills/tests.rs
  [@test] ../crates/avl-trace/src/bundle/tests.rs (`a_picture_names_its_media_type_by_its_extension`)
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`the_thumbnail_of_a_lossy_bundle_is_its_last_avif_still`)

- The pictures of a scenario that starts a video are lossy AVIF, because the video also shows each moment. They are
  8-bit 4:4:4 at the quality 50 of `avifenc`, at the speed 6 and with the `iq` tuning. The pictures of a scenario
  without a video are lossless WebP. The scenario picks the encoding after it starts the video, so a video that fails
  later leaves the pictures lossy. The comparison with the previous picture reads the grabbed frames, so it is the
  same for both encodings.
  [@test] ../crates/avl-record/src/session/tests.rs (`a_scenario_with_a_video_takes_lossy_pictures`)
  [@test] ../crates/avl-record/src/session/tests.rs (`an_encoder_that_never_reads_does_not_hold_the_scenario_ack`)
  [@test] ../crates/avl-record/src/avif/tests.rs (`a_lossy_picture_is_a_close_444_avif_still_of_the_frame`)
  [@test] ../crates/avl-record/src/stills/tests.rs (`a_still_of_an_unchanged_screen_names_the_previous_file`)

- When the recorder cannot write a picture, the next snapshot of the same screen writes its own file. The
  `air.trace.error` of the failure names the file that is missing.
  [@test] ../crates/avl-record/src/stills/tests.rs

- When the lossy encoder fails or panics on a picture, the recorder writes that picture as lossless AVIF. So the file
  keeps the name and the format of its record. An `air.trace.error` from `still` names the file. When the lossless
  picture cannot be written either, one record names the file and both causes. A panic of an encoder costs one
  picture, and the pictures queued after it are still written. A lossless WebP picture has no fallback.
  [@test] ../crates/avl-record/src/stills/tests.rs (`a_still_whose_lossy_encoder_panics_is_written_lossless`)
  [@test] ../crates/avl-record/src/stills/tests.rs (`a_still_that_every_encoder_panics_on_is_reported_and_not_named_again`)
  [@test] ../crates/avl-record/src/stills/tests.rs (`a_lossless_still_whose_encoder_panics_is_reported`)
  [@test] ../crates/avl-record/src/avif/tests.rs (`a_lossless_picture_reconstructs_the_exact_frame`)

- The recorder writes the Allure results only when the hello says `allure`. The lane says it when a person asks
  with `-Dair.flow.trace.allure=on`. That flag is a lane flag of the runtime descriptor, or a `--jvm_flag` of
  `lane-run` for one Bazel run. Without it, the run has no `allure-results` directory.
  [@test] ../crates/avl-record/src/tests.rs (`the_golden_transcript_writes_no_allure_result_unasked`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`the_hello_asks_for_the_allure_results_only_when_the_inputs_do`)
  [@test] ../crates/avl-guest/src/lane/inputs/tests.rs (`the_trace_flags_name_the_recorder_the_screen_and_the_allure_results`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_allure_flag_asks_for_the_allure_results_of_one_run`)

- When the hello says `allure`, the recorder writes one Allure result into `<root>/<runId>/allure-results/` at the
  end of each scenario. It writes the result before `bundle.json`, so a finished bundle has its result.
  [@test] ../crates/avl-record/src/tests.rs (`the_golden_transcript_writes_the_golden_allure_result`)

- The result is `<uuid>-result.json`. Its `fullName` is `<testClass>.<scenario>`, and its `historyId` is the SHA-256
  of the `fullName`. The uuid derives from the run id, the bundle's path and the start. So a retry has a uuid of its
  own and the history of the first attempt.
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_retry_shares_the_history_and_a_replay_writes_the_same_result`)
  [@test] ../crates/avl-trace/src/bundle/tests.rs (`derived_ids_are_stable`)

- The status of the result is the status of the manifest, and an aborted or a truncated bundle is `broken`. The
  `statusDetails` are the message and the trace of `done`. The result of a truncated bundle names the running span.
  [@test] ../crates/avl-trace/src/bundle/tests.rs (`the_allure_status_of_each_bundle_status`)
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_truncated_scenario_is_broken_and_names_its_running_span`)

- The labels are the labels of the scenario. The recorder adds `suite`, `testClass`, `package`, `host`, `lane` and
  `flow` when the lane sent no label of that name. The suite is the scenario's suite, or the simple name of the test
  class. The recorder always adds `traceBundle`, the path of the bundle under the run's directory.
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_failed_scenario_becomes_a_result_with_its_steps_pictures_and_attachment`)

- Each lane span is a step under the step of its parent, with the span's title and status. The IDE's spans are not
  steps. A step has a Before and an After picture with the media type of its file. It also has the files that the lane
  attached to its span.
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_failed_scenario_becomes_a_result_with_its_steps_pictures_and_attachment`)

- Before is the first picture in the span. After is the next picture of the scenario, or the last picture of the
  span when the scenario took no later one. The result names no other snapshot.
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_failed_scenario_becomes_a_result_with_its_steps_pictures_and_attachment`)

- The result attaches the path of the bundle as `text/plain`, `video.mp4` as `video/mp4` and `idea.log` as
  `text/plain`, when the bundle holds them. It also attaches the files that the lane attached to the root span.
  [@test] ../crates/avl-record/src/tests.rs (`the_golden_transcript_writes_the_golden_allure_result`)

- An attachment is a hard link `<uuid>-attachment.<ext>` in the results directory, and the result names it in
  `source`. When the link fails, the recorder copies the file. A bundle file that two steps name is one attachment.
  [@test] ../crates/avl-record/src/allure/tests.rs (`a_failed_scenario_becomes_a_result_with_its_steps_pictures_and_attachment`)
  [@test] ../crates/avl-record/src/allure/tests.rs (`an_attachment_is_copied_when_the_link_fails`)

### The spans and the log records

- `spans.jsonl` holds one OTLP `TracesData` per line, one line per ended span. `logs.jsonl` holds one OTLP
  `LogsData` per line, one line per log record.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- Trace and span ids are lowercase hex. Nanosecond times and integer values are decimal strings. A reader
  refuses a bare number, because JavaScript cannot hold a nanosecond time in one.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- The trace id derives from the run id, the test class and the scenario. A span id is the lane's span id
  plus one, so the root is `0000000000000001`. A replay of one transcript therefore writes the same bundle.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- The resource names `service.name` `air-ui-lane`, the run id, the lane, the launcher, `host.name` and
  `os.type`.
  [@test] ../crates/avl-record/src/tests.rs

- The root span carries the scenario name, the test class, the suite, the flow, the fixture and the flags.
  It also carries `air.program`, the generated profile of the `scenario` command.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- Every other lane span carries `air.span.kind` and `air.span.key`. An assertion span also carries its
  expectation sentence. Every span carries `air.span.status`, because OTLP has no word for aborted.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- An action span and an assertion span carry `air.instruction.side` and `air.instruction.timeout_ms`, when
  their `span` command states them.
  [@test] ../crates/avl-trace/src/otlp/tests.rs (`the_example_spans_are_the_transcripts_spans`)

- The span kinds are `reset`, `setup`, `step`, `operation`, `action`, `assertion`, `journey`, `restart` and
  `relaunch`. A `relaunch` span is one part of the relaunch that a `restart` span holds. A span key is
  `<kind>:<id>` and holds no counter, time or path.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- Every log record is an event correlated with a span. The events are `air.span.started`, `air.snapshot`,
  `air.input`, `air.bridge.call`, `air.video` and `air.attachment`. The other two are `exception` and
  `air.trace.error`.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- `air.span.started` is written when a span opens. It is how a truncated bundle names the spans that were
  running.
  [@test] ../crates/avl-record/src/session/tests.rs

- A failed span has OTLP status ERROR and an `exception` record with the type, the message and the stack.
  The stack is the frames in the order the JVM prints them, then each cause's first message line and frames.
  The lane caps the message at 16,000 characters and the stack at 200 lines.
  [@test] ../crates/avl-record/src/session/tests.rs

### The IDE's own spans

- The bundle holds the OpenTelemetry spans that the IDE ended in the scenario. They are under a second resource,
  whose `service.name` is `air-ui-lane-ide`, and under their own instrumentation scopes.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)
  [@test] ../crates/avl-trace/src/otlp/tests.rs (`every_example_line_is_valid_otlp`)

- An IDE span is in the bundle's one trace and keeps its span id. `air.ide.trace.id` keeps the IDE's own trace id.
  An IDE span that would take the id of a lane span is left out, and an `air.trace.error` says so.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)

- An IDE span carries `air.span.kind` `ide`, and `air.span.status` `passed` or `failed`. Only the recorder writes
  that kind. The span's own attributes keep their JSON type: a string, an integer, a double, or a boolean.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)
  [@test] ../crates/avl-trace/src/bridge/tests.rs (`ide_spans_decode_and_refuse`)

- An IDE span keeps its IDE parent when that parent is in the bundle. Otherwise it is the child of the innermost
  lane span that contains its start, and `air.ide.span.parent` keeps the lost id. The recorder makes that choice
  once, at the end, because the IDE exports a child before its parent.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)
  [@test] ../crates/avl-trace/src/otlp/tests.rs (`the_example_ide_spans_hang_under_the_lane_span_they_started_in`)

- An IDE span that started before the scenario belongs to the scenario before, and the bundle leaves it out.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)

- The recorder reads the IDE's spans at each snapshot. It reads them with a flush when the lane says the IDE
  restarts, and at the end of the scenario. So a span still in the platform's batch at the end reaches the bundle.
  [@test] ../crates/avl-record/src/session/tests.rs (`the_ide_spans_join_the_bundle_under_the_spans_they_started_in`)

- A failed spans read is evidence loss. The first one of a scenario is an `air.trace.error` from `bridge.spans`,
  and the later ones are not recorded. Each loss that the IDE's span log reports is recorded. No ack changes.
  [@test] ../crates/avl-record/src/session/tests.rs (`a_missing_spans_route_is_recorded_once_and_changes_no_ack`)

### The lane protocol

The lane's side is [Flow UI Scenario Traces](../../../../plugins/air/spec/docs/flow-trace.spec.md#the-lanes-side).

- The lane writes one JSON command per line to the recorder's standard input. The ops are `hello`,
  `scenario`, `span`, `end`, `snap`, `call`, `attach`, `restart` and `done`. The recorder refuses the old
  `driverSteps` op like any other unknown op.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- A command line holds no whitespace between its tokens. So a program that a lane reads from a pretty-printed
  suite document stays on one line. The strings of the program keep their text.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`a_program_from_a_pretty_printed_document_is_written_on_one_line`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_pretty_printed_suite_document_reaches_the_recorder_one_line_per_command`)

- The lane waits for an ack after `hello`, `scenario`, `snap` and `done` only. Every other op costs the
  scenario no time.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- Every ack names the line it answers. A lane that gave up waiting discards the late ack by its line. So a
  late ack is never taken for the next one.
  [@test] ../crates/avl-trace/src/protocol/tests.rs
  [@test] ../crates/avl-record/src/tests.rs

- The recorder refuses an unknown op, an unknown field and an unknown vocabulary word. Both ends come from one
  checkout, so an unknown field can only be a rename one end missed.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- A refused line gets a negative ack and an `air.trace.error` record, whatever its op. When line 1 is refused,
  the answer has the hello ack's shape.
  [@test] ../crates/avl-record/src/tests.rs

- Lane span ids count from 1 in each scenario, in the order the spans open. Id 0 is the scenario's root,
  which the `scenario` command opens.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- `span` can carry `side`, `ide` or `host`, on an action or an assertion only. The recorder refuses a side on
  a span of any other kind.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`only_an_instruction_span_states_a_side_and_a_deadline`)

- `span` can carry `timeoutMs`, the deadline of the id in milliseconds, on an action or an assertion only.
  The recorder refuses a deadline on a span of any other kind, and a deadline of 0.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`only_an_instruction_span_states_a_side_and_a_deadline`)

- Only the lane knows two times: a call's start and its duration. The recorder stamps everything else when the
  line arrives. A transcript therefore holds no other timestamp.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- `scenario` can carry `labels`, a list of `{name, value}` pairs in the order the lane read them. They are the
  test method and class names and the labels that the test class and method declare. A name can occur more than
  once. The recorder refuses a label without a name or a value.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`labels_attachments_and_a_failed_done_are_canonical`)

- `scenario` can carry `logFrom`, the byte of the IDE's `idea.log` where the slice of the scenario begins. Absent,
  the slice begins at the size that the IDE reports as the scenario opens. A byte past the end of the log starts the
  slice at the end.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`a_scenario_names_the_byte_where_its_log_slice_begins`)
  [@test] ../crates/avl-record/src/idealog/tests.rs (`a_slice_starts_at_the_byte_that_the_lane_names`)

- `attach` carries `name`, `mime`, `file` and `span`. The file is a path that the lane wrote under the hello's
  root. The recorder moves the file into the bundle as `attach/NNNN.<ext>` and writes an `air.attachment` record
  on the span. The record names the title, the media type and the bundle file. Ordinals count from 1 in each
  scenario. The extension is the lane file's own in lowercase, or `bin`.
  [@test] ../crates/avl-record/src/attach/tests.rs (`an_attachment_moves_into_the_bundle_and_is_recorded_on_its_span`)
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`labels_attachments_and_a_failed_done_are_canonical`)

- In a scenario with a video, an `attach` of the media type `image/png` becomes the lossy AVIF picture
  `attach/NNNN.avif`. The recorder removes the lane file. The record names `image/avif` and the picture's file.
  The picture follows the rules of a snapshot's picture. It is encoded off the lane's path, and it falls back to
  lossless. The same picture as the previous one names the file of the previous one. The attachment pictures have a
  writer of their own, so a picture and a still never name the file of the other.
  [@test] ../crates/avl-record/src/attach/tests.rs (`a_png_attachment_becomes_a_lossy_avif_picture_in_a_scenario_with_a_video`)
  [@test] ../crates/avl-record/src/attach/tests.rs (`equal_consecutive_png_attachments_name_one_file`)

- In a scenario without a video, a PNG attachment stays a PNG. A lossless WebP of the recorder is larger than the PNG
  of the IDE. A PNG with the same bytes as the previous PNG attachment that the bundle keeps as a PNG
  gets no file of its own. The recorder removes the lane file, and the record names the file of the previous one.
  [@test] ../crates/avl-record/src/attach/tests.rs (`an_attachment_moves_into_the_bundle_and_is_recorded_on_its_span`)
  [@test] ../crates/avl-record/src/attach/tests.rs (`equal_consecutive_png_attachments_name_one_file`)

- In a scenario with a video, some PNG attachments move into the bundle as they are, with their media type. One is a
  PNG that does not decode. The other is a PNG that the queue cannot take within the 1.8 s bound of a snapshot. An
  `air.trace.error` from `attach` says why the PNG is no AVIF picture. A picture that the encoder cannot write is an
  `air.trace.error` from `still` on the span of the attachment.
  [@test] ../crates/avl-record/src/attach/tests.rs (`a_png_that_does_not_decode_is_kept_as_it_is_and_recorded`)
  [@test] ../crates/avl-record/src/attach/tests.rs (`a_png_that_the_full_queue_cannot_take_is_kept_as_it_is_and_recorded`)

- The recorder refuses an `attach` whose file is outside the hello's root, and one whose span the scenario never
  opened. The file then stays where it is. A file that the recorder cannot move is evidence loss. An
  `air.trace.error` from `attach` says so, and the bundle has no record of the attachment.
  [@test] ../crates/avl-record/src/attach/tests.rs (`an_attachment_outside_the_root_or_its_scenario_is_refused_and_a_lost_one_is_recorded`)

- The recorder moves an attachment with a rename. When the rename fails, it copies the file under a partial name,
  renames the copy into place and removes the lane file.
  [@test] ../crates/avl-record/src/attach/tests.rs (`a_move_that_cannot_place_the_file_keeps_the_lane_file`)

- A failed scenario keeps the thread dumps that the IDE wrote while the scenario ran. A dump is a file of the IDE log
  directory with `thread-dump` in its name. The recorder copies each dump into the bundle as `attach/NNNN.log`. An
  `air.attachment` record on the root span names it, and the IDE keeps its file. A bundle keeps at most 4 dumps. A
  passed scenario keeps none.
  [@test] ../crates/avl-record/src/dumps/tests.rs (`a_failed_scenario_keeps_the_thread_dumps_that_the_ide_wrote_while_it_ran`)
  [@test] ../crates/avl-record/src/dumps/tests.rs (`a_passed_scenario_keeps_no_thread_dump`)

- `done` can carry the `message` and the `trace` of the failure that ended the scenario. The trace is the
  failure's class on the first line, then the lines of its stack. The recorder refuses either field on a passed
  `done`.
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`labels_attachments_and_a_failed_done_are_canonical`)

- Standard input reaching EOF, SIGINT or SIGTERM ends the open scenario as truncated. The recorder writes
  every open span as aborted, the root included, then the video and the manifest. It exits 0.
  [@test] ../crates/avl-record/src/tests.rs

- The recorder repairs three lane mistakes and records each as a `protocol` error. A new scenario before
  `done` truncates the previous one. An `end` of an outer span, or a `done`, aborts the spans still open
  inside it.
  [@test] ../crates/avl-record/src/session/tests.rs

- The recorder ignores SIGPIPE, so a lane that died cannot stop it from finishing the bundle.
  [@test] ../crates/avl-record/src/tests.rs

- The lane daemon is a lane of the protocol. Its `hello` names the launcher `daemon` and the root
  `<iterationDir>/air-traces`, and its transcript of a green scenario is held to a golden. Under a Bazel test,
  `lane-run` names the launcher `bazel` and the root `$TEST_UNDECLARED_OUTPUTS_DIR/air-traces`. A bundle does
  not hold the full `idea.log`. That log and the other evidence of a run that is not green go to
  `$TEST_UNDECLARED_OUTPUTS_DIR/lane`, as [the controller spec](lane-controller.spec.md) states under `lane-run`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`a_bazel_test_roots_the_bundles_in_its_outputs_directory`)

- The lane daemon writes one `call` for each bridge request, and one for each step of a chain after it.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)

- The lane daemon photographs the first failure of a scenario once, in the span that failed. It attaches the IDE
  dump of a slow step to the span of that step.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_slow_instruction_is_typed_photographed_and_dumped`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`a_failure_is_photographed_once_in_its_own_span_and_fails_the_spans_it_unwinds`)

- The lane daemon says on its stream when its recorder loses a trace. The causes are a recorder that did not
  start, a refused hello or scenario, and a recorder that went away. One `traceFailed` record carries the first
  reason, and a later one stays in the daemon log. A scenario that the recorder refused gets no further command.
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`a_refused_scenario_gets_no_further_command_and_is_the_failure_of_the_session`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`a_recorder_that_went_away_is_the_failure_of_the_session`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`only_a_recorder_that_did_not_start_is_a_failure_of_a_quiet_session`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_recorder_that_refuses_the_scenarios_is_one_trace_failure_of_the_stream`)
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_trace_failure_of_the_lane_daemon_decodes_and_keeps_its_bytes`)

### Capture and video

- The recorder takes pixels from the first available source. X11 comes first, then the IDE's paint route,
  then none. The hello ack and the manifest name the source, and the reason for each missing rung.
  [@test] ../crates/avl-record/src/capture/tests.rs

- The recorder reads the screen itself only when the hello says `screen`. The Linux guest's lane targets set
  `-Dair.flow.trace.screen=on`, because the guest's `Xvfb` belongs to the lane. `lane-run` says `screen` on a
  TeamCity build on Linux with a `DISPLAY`, because no person works on the display of a TeamCity agent. There it
  records an X display of its own, not the desktop of the agent. A direct lane on a developer's machine records the IDE's paint and no screen video, and it never reads the screen.
  `lane-run` refuses the property as a JVM flag, so a person cannot give it to a direct lane. The screen of a
  developer shows everything else they have open. On macOS, reading it asks for Screen Recording permission, and
  that prompt can take the focus from the IDE.
  [@test] ../crates/avl-record/src/ladder/tests.rs (`the_ladder_reads_x_only_where_allowed_and_where_the_ide_draws_to_it`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`an_unknown_jvm_flag_or_a_split_that_is_not_one_is_refused_by_name`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`only_a_teamcity_build_on_linux_with_a_display_lets_the_recorder_read_the_screen`)
  [@test] ../../../../plugins/air/tests/integration/ui_lane_ide.bzl

- X11 is read only where the IDE draws to it, which is Linux. A Mac with XQuartz has a `DISPLAY` too, and its
  root window holds nothing of the IDE. A display that failed to open is not tried again in the session.
  [@test] ../crates/avl-record/src/ladder/tests.rs (`the_ladder_reads_x_only_where_allowed_and_where_the_ide_draws_to_it`)

- The recorder dials the display's socket itself, so a grab that outlasts its bound closes the socket and
  returns. A server that stopped answering cannot hold the frame loop, a snapshot or the close. The broken
  source is opened again at the next scenario, and the reopen is recorded as a `capture` error.
  [@test] ../crates/avl-record/src/capture/x11/tests.rs

- X11 capture uses MIT-SHM and falls back to plain `GetImage` in strips. It refuses any pixel format other
  than BGRX and says why.
  [@test] ../crates/avl-record/src/capture/x11/tests.rs

- A scenario that starts without X11 tries the ladder again, because the IDE may not have been up at hello.
  [@test] ../crates/avl-record/src/ladder/tests.rs

- With X11, the recorder encodes each frame itself as H.264 in fragmented MP4, at 10 frames per second. A
  missed tick repeats the last picture with its original capture time, so seeks stay exact.
  [@test] ../crates/avl-record/src/video/h264/tests.rs
  [@test] ../crates/avl-record/src/frames/tests.rs

- The video has a keyframe at least each second, and every fragment starts with a keyframe. So a bundle whose
  recorder was killed plays to its last complete second.
  [@test] ../crates/avl-record/src/video/mp4/tests.rs
  [@test] ../crates/avl-record/src/video/h264/tests.rs

- A screen whose longer side exceeds 3840 or whose shorter side exceeds 2160 gets no video. The reason names the
  limit.
  [@test] ../crates/avl-record/src/video/h264/tests.rs

- At a clean stop the video ends with an `mfra` index of its fragments. A killed recorder leaves none.
  [@test] ../crates/avl-record/src/video/mp4/tests.rs

- On a macOS host allowed to read the screen, ffmpeg records it through `avfoundation`. The index then comes
  from ffmpeg's progress reports.
  [@test] ../crates/avl-record/src/video/ffmpeg/tests.rs

- A macOS host without ffmpeg records no screen video. The codec is then `none`, and the reason says so.
  Stopping a screen video closes ffmpeg's input, waits up to 5 s, and then kills it.
  [@test] ../crates/avl-record/src/video/ffmpeg/tests.rs

- A snapshot grabs the frame and the tree, writes the tree file, emits the inputs and the snapshot record,
  and acks. The bound is 1.8 s, below the lane's 2 s.
  [@test] ../crates/avl-record/src/session/tests.rs

- The snapshot record holds the time of each part in milliseconds, on the clock of the recorder. They are
  `air.snapshot.grab_ms`, `air.snapshot.tree_ms`, `air.snapshot.spans_ms` and `air.snapshot.still_ms`, the change check
  and the queue of the picture. When the encoders are behind, `still_ms` holds the wait for a free place in their queue.
  A part that did not run has no time. `air.snapshot.duration_ms` is the time from the
  arrival of the `snap` line to the record. So it holds a wait behind an earlier line too, and the ack follows it.
  [@test] ../crates/avl-record/src/session/tests.rs (`a_snapshot_names_the_time_of_each_of_its_parts`)
  [@test] ../crates/avl-record/src/session/tests.rs (`a_session_without_a_bridge_still_takes_pictures`)

- The encoding of a picture runs after the ack, on a bounded writer. `done` waits for every still before it writes
  the manifest.
  [@test] ../crates/avl-record/src/stills/tests.rs

- The writer runs one encoder per two cores, at least 1 and at most 4, over one queue. The encoders take the queued
  pictures at once. A picture's file name is fixed when the snapshot is taken, so the order of the writes does not
  matter. An encoder that ends with a panic is an `air.trace.error` from `still` at the end of the scenario.
  [@test] ../crates/avl-record/src/stills/tests.rs (`the_encoders_run_at_once_and_close_waits_for_every_still`)
  [@test] ../crates/avl-record/src/stills/tests.rs (`close_counts_an_encoder_that_ended_with_a_panic`)
  [@test] ../crates/avl-record/src/session/tests.rs (`a_still_encoder_that_ended_with_a_panic_is_recorded_at_the_end`)

- A hello with `keepVideo` `failed` keeps the video of a scenario that did not pass only. The recorder records a passed
  scenario as it records every other scenario. At its end it deletes `video.mp4` and `video.index.json`. It writes an
  `air.video` record with the codec `none` and the reason, and the manifest names the codec `none` with that reason.
  The stills stay as they are encoded. A failed, an aborted and a truncated scenario keep their video. A later attempt
  of a scenario that did not pass in the same session keeps its video too, because that scenario is flaky. A hello
  without the field keeps every video. `lane-run` sends `failed` when `AIR_LANE_VIDEO` says so.
  [@test] ../crates/avl-record/src/session/tests.rs (`a_passed_scenario_keeps_no_video_when_the_hello_keeps_the_failed_ones_only`)
  [@test] ../crates/avl-trace/src/protocol/tests.rs (`a_hello_keeps_every_video_unless_it_says_failed`)

- The viewer takes an `air.video` record with the codec `none` as a bundle without a video, also after a record that
  started a video.
  [@test] ../../../../plugins/air/docs/src/trace/model.test.ts (`a none video record after the start of a video leaves the bundle without a video`)

- The `idea.log` slice is the bytes written between the scenario's start and its end. A rotated or replaced
  log adds the new file. A slice is capped at 32 MiB.
  [@test] ../crates/avl-record/src/idealog/tests.rs

### Packing and the VM pull

- `air-trace pack SOURCE DEST` writes a deterministic zip of every bundle under a directory. Entries are in
  byte order with one fixed time and mode.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs

- Compressed media are stored, and everything else is deflated. A stored entry sits at one offset, so a
  server answers a byte range straight out of the zip.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs

- A pack keeps the `allure-results` directory of each run that has one. A pack that selects bundles keeps the
  result of each bundle it packs and the files that the result names. A result that it cannot read is skipped, and
  the report names it.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs (`a_pack_keeps_the_allure_results_of_its_bundles`)

- A pack lists each `allure-results` directory after it walks the tree. So a finished bundle leaves with its result,
  also when the recorder writes the result while the pack walks the tree.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs (`a_result_written_during_the_walk_leaves_with_its_bundle`)

- The guest agent's `trace-pack-ready <sourceDir> <destinationZip> <ledger> [--all]` verb runs the code of
  `air-trace pack` on the guest. It packs only the bundles that the ledger does not name. Without `--all` it
  packs only the finished bundles, the ones with a manifest. It packs the Allure result of each bundle that has one
  with the bundle. It adds each packed bundle to the ledger, and it writes no zip when nothing is new.
  [@test] ../crates/avl-guest/src/tracepack/tests.rs (`trace_pack_ready_packs_each_finished_bundle_once`)
  [@test] ../crates/avl-guest/src/tracepack/tests.rs (`trace_pack_ready_packs_the_allure_result_with_its_bundle`)

- The controller pulls the finished traces while the iteration runs. After each test that ends, it asks the
  guest for the bundles that finished since the last pull. The last pull of an iteration uses `--all`, so a
  bundle whose recorder never finished it also arrives. One pull runs at a time, and the daemon's stream never
  waits for a pull.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

- Each pull is `<RuntimeRoot>/runs/<runId>/traces/<iterationId>/<NNN>.zip`. The report's `traceArchives`
  names each zip with its bundles, and each bundle carries the viewer's id. The controller publishes one
  `traceReady` record for each bundle when it arrives.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-wire/src/report/tests.rs

- The plain verdict of `run` and `shard` ends with the traces block. Its first line counts the scenarios, the
  ones that did not pass, and the ones with a video. Then each scenario that did not pass has one line. The
  block names at most 10 scenarios.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-base/src/plain/tests.rs

- Every pack or pull failure becomes the report's `tracesError` and never changes the verdict. The zips that
  arrived before the failure stay in `traceArchives`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-wire/src/report/tests.rs

- The reason of the daemon's first `traceFailed` record comes first in `tracesError`, before a pack or pull
  failure. It never changes the verdict either.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_trace_failure_of_the_daemon_is_the_traces_error_of_an_iteration_without_traces`)

- The Linux guest installs no video encoder. The recorder carries its own, so a Linux worker records video.
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs

- One bundle of a zip packs into a zip of its own, with the bundle at the top. Its entries are inflated and written
  as a pack writes them, so a video that Bazel deflated is stored again. An entry name with `..` refuses the pack.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs (`one_bundle_of_a_zip_is_packed_with_its_media_stored`)

- `air-trace teamcity-publish` is the step after `bazel test` on TeamCity. It reads each `test.xml` under the
  testlogs, and the bundles of `test.outputs/air-traces/` and `test.outputs/outputs.zip` beside it. Each bundle
  becomes the zip `<lane>/<runId>/<TestClass>/<scenario>.zip` of the archive `air-traces.zip`. The lane is the Bazel
  target with its shard directory. The step prints the `testMetadata` of the test: the `link` named `trace viewer`,
  and the `artifact` named `trace`, `air-traces.zip!/<lane>/<runId>/<TestClass>/<scenario>.zip`. The test name is
  `<classname>.<name>` of the XML beside the bundle, and the messages carry no flow. Two bundles whose names sanitize
  to one file get `<scenario>.zip` and `<scenario>.2.zip`, and stderr says so. Testlogs without bundles print nothing
  and write nothing.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`testlogs_without_bundles_print_nothing`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`a_published_trace_names_the_case_of_its_own_xml`)

- The step publishes one archive artifact, `air-traces.zip`, with no target, so TeamCity keeps it as it is. TeamCity
  redirects a loose artifact to a signed address, where a sibling file is refused. It serves an entry of an archive
  itself, with byte ranges. The archive holds `runs.json`, then each bundle zip in the order of the metadata. It holds
  no viewer page. The listing is deflated, and the zips are stored. Each entry has one time and one mode, so two
  publishes of one testlogs tree write one archive. No bundle zip is a loose artifact.
  [@test] ../crates/air-trace/src/publish/tests.rs (`on_team_city_the_listing_names_the_build_and_each_test_links_its_trace`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests`)
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs (`an_archive_keeps_its_order_and_stores_its_zips`)

- On TeamCity the archive also holds `affected.json`, after `runs.json`. The step reads the changed files of the
  build from the file of `teamcity.build.changedFiles.file`. Each line is `<path>:<change type>:<revision>`, and a
  file of a personal patch has the revision `<personal>`. The step groups the files by revision, in the order of the
  file. It joins the paths of each commit on the checkout of the build. The checkout is `--repo`, else
  `teamcity.build.checkoutDir`, else `BUILD_WORKSPACE_DIRECTORY`. The join is the one of `vm.cmd suites`, and the
  document keeps its words. The document is `{schema: 1, revision, commits}`, with the revision of the build. Each
  commit holds its `revision` and its `paths`, each with its `change` type. Its `reached` suites carry `suite`,
  `testClassName`, the catalog `lane` and `via`. Its `unmapped` paths carry the `reason` of the join. A build without
  changed files has no `affected.json`. A build that names no such file and a file that does not read are warnings.
  A line of another format, a build without `build.vcs.number` and a refusal of the join are warnings too. The rest
  still publishes.
  [@test] ../crates/air-trace/src/publish/affected/tests.rs (`each_commit_names_the_suites_that_its_files_reach`)
  [@test] ../crates/air-trace/src/publish/affected/tests.rs (`the_changed_files_read_as_team_city_writes_them`)
  [@test] ../crates/air-trace/src/publish/affected/tests.rs (`a_build_without_its_changed_files_says_why_it_has_no_document`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_archive_holds_what_the_changed_files_reach_after_the_listing`)

- Once `air-traces.zip` is written and published, the step removes from the testlogs each bundle directory that the
  archive holds. TeamCity packs the testlogs into `bazel.logs.zip` at the end of the build, which would hold the bundles
  a second time. A directory goes only when it is a directory and no link. Its real path must be inside
  `test.outputs/air-traces` of its test, inside its testlogs. A directory stays when its zip lacks a part of it, such as
  a link to a directory or a broken link. Only a temporary `.partial` file of a recorder may go without a copy. A run or
  class directory that the removal empties goes too. The other outputs stay: `outputs.zip`, the evidence of the lane,
  and the Allure results. A test of an earlier build is not published, so its bundles stay. One stderr line says the
  count and the bytes of the removed files. A file with another hard link frees no disk space, but it leaves the
  testlogs. A directory that does not go is a warning. On TeamCity the step always removes. Elsewhere it removes only
  with `--remove-packed`, so the local Bazel outputs stay.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_packed_bundles_leave_the_testlogs_once_the_archive_is_published`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`a_packed_directory_outside_the_traces_or_behind_a_link_stays`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`a_bundle_that_its_zip_does_not_hold_whole_stays_in_the_testlogs`)
  [@test] ../crates/air-trace/tests/cli.rs (`teamcity_publish_removes_the_packed_bundles_on_team_city_or_when_asked`)

- For a failed scenario only, the step also publishes two loose files, so the Tests tab of TeamCity shows them. They
  are `air-traces-failed/<lane>/<runId>/<TestClass>/<scenario>/failure.<ext>`, the last picture in its format, and
  `…/video.mp4`, when the bundle has a video. The `image` named `failure` and the `video` named `video` of the test
  name them. A file that does not copy out of the bundle zip is a warning.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests`)

- The step never turns a build red. Each problem is a line on stderr and a `message` with the status `WARNING` on
  stdout. A problem is no testlogs, a missing testlogs directory, or an output that does not read. A bundle that does
  not pack and build properties that do not read are problems too. The rest still publishes, and the exit
  is 0. Only a usage error of the command line exits 2.
  [@test] ../crates/air-trace/src/publish/tests.rs (`testlogs_without_bundles_print_nothing`)
  [@test] ../crates/air-trace/tests/cli.rs (`teamcity_publish_answers_0_without_testlogs_and_2_for_a_usage_error`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`a_warning_is_a_message_with_the_warning_status`)

- Without `--testlogs`, the step reads every `bazel-out/<configuration>/testlogs` of the `bazel-out` that holds the
  real file of its binary. `bazel run` starts the binary through links into the `-opt` configuration, and the tests
  write their logs into another configuration.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_testlogs_are_found_beside_the_binary_that_bazel_run_starts`)

- A TeamCity agent keeps its checkout and its Bazel output base across builds. So the testlogs can hold the tests of an
  earlier build, which this build did not run. The step publishes only a test whose `test.xml` is from the start of the
  build or later. The start is `--since`, else the variable `AIR_TRACE_SINCE`, a time of RFC 3339. The first step of the
  build sets it with the clock of the agent. Bazel writes the `test.xml` with the same clock, so no skew and no margin
  apply. The step asks no server. Each skipped test is one line on stderr, and one warning counts them. A stronger
  warning says when no test is left to publish. A value of the variable that does not read is a warning, and so is a
  step on TeamCity without a start. Then every test publishes. Off TeamCity without a start, every test publishes with
  no warning.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_tests_of_an_earlier_build_are_skipped`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_start_of_the_build_is_the_flag_or_the_variable_of_the_first_step`)
  [@test] ../crates/air-trace/tests/cli.rs (`teamcity_publish_takes_the_start_of_the_build_from_air_trace_since`)

- Before it writes, the step empties its output, so the output holds the files of this run only. It empties the
  default output, and an output that holds its marker file `.air-traces-publish`. It refuses another `--out` that
  holds files, and then publishes nothing.
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_output_holds_the_files_of_this_run_only`)

- A relative path of `--testlogs` or `--out` is taken from `BUILD_WORKING_DIRECTORY`, the directory where
  `bazel run` ran. It is not taken from the runfiles tree that the program starts in.
  [@test] ../crates/air-trace/src/publish/tests.rs (`a_relative_path_is_taken_from_the_directory_where_bazel_run_ran`)

- The listing `runs.json` of the archive is the listing of `GET /__air/runs`, written with the server's own types.
  Each scenario carries `zip`, the path of its zip relative to `runs.json`. Its `source` is that zip, and its id is
  the id of that zip. `controllerRuns` is empty. Each testlogs directory is one `bazel-testlogs` root. The root counts
  its published bundles, and its problems name each output that did not read and each bundle that did not pack. A
  `zip` is a relative path with no `.` or `..` segment, so it stays in the directory of `runs.json`. Each zip holds
  its bundle at its top.
  [@test] ../crates/air-trace/src/publish/tests.rs (`on_team_city_the_listing_names_the_build_and_each_test_links_its_trace`)

- The step builds no viewer, and it needs no node and no pnpm. It never starts a Bazel, on TeamCity or elsewhere.
  `--viewer` is a usage error.
  [@test] ../crates/air-trace/tests/cli.rs (`teamcity_publish_builds_no_viewer_and_starts_no_bazel`)
  [@test] ../crates/air-trace/tests/cli.rs (`teamcity_publish_answers_0_without_testlogs_and_2_for_a_usage_error`)

- On TeamCity, the step reads the build from the agent's property files: `TEAMCITY_BUILD_PROPERTIES_FILE`, and
  the file of the configuration parameters that `teamcity.configuration.properties.file` names. The build gives each
  run and root of `runs.json` its `origin`: the name of the build configuration, the build id and the build page. The
  server is the root of `BUILD_URL` when that is `<root>/build/<build id>`, else `teamcity.serverUrl`. Files that
  do not name the build are a line on stderr, and the listing then names no build.
  [@test] ../crates/air-trace/src/publish/teamcity/tests.rs

- With the build, each test gets a `link` named `trace viewer` to the CI trace page of the hosted site:
  `<site>/ci/<buildId>/trace?zip=<zip>`. The build id is the id of the build that runs the step, and the zip is the
  path of the zip relative to `runs.json`. The zip path is written as `URLSearchParams` writes a value, with each `/`
  kept. The portal of the site reads `air-traces.zip!/runs.json` and `air-traces.zip!/<zip>` of that build from
  TeamCity. After the archive is published, one `message` of the build log names the CI page of the build,
  `<site>/ci/<buildId>`. The site is `--site`, else `AIR_TRACE_SITE`, else `https://ij-void-docs.labs.jb.gg/air`. A
  staging portal is another site. Without the build, no test gets a link, and the build log names no page.
  [@test] ../crates/avl-trace-tools/src/viewer/tests.rs (`the_ci_urls_name_the_build_and_the_zip`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`on_team_city_the_listing_names_the_build_and_each_test_links_its_trace`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_links_open_the_site_that_the_step_names`)
  [@test] ../crates/air-trace/src/publish/tests.rs (`the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests`)

### The server

- `air-trace serve` listens on `127.0.0.1:7357` unless told otherwise. It answers only requests addressed to
  a loopback name, and refuses a request the browser marks as cross-site.
  [@test] ../crates/air-trace/src/serve/tests.rs

- By default it looks in the Air lanes' `bazel-testlogs` and in the controller's pulled
  zips. `--root` adds a place, and `--no-default-roots` drops the defaults.
  [@test] ../crates/air-trace/src/serve/tests.rs

- The defaults include the export directory of the checkout, `out/air/vm-export/runs`. A run of another host
  exports its zips there with `AIR_VM_EXPORT`, and `bd-host` copies them back.
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`the_default_roots_find_the_exported_zips_of_the_checkout`)
  [@test] ../crates/avl-vm/src/daemon/export/tests.rs

- A zip entry that ends in `.zip` and lies outside every bundle is an inner zip. Discovery extracts it once into
  the cache and reads it as a zip, one level deep. Its bundles name the container `<outer>!/<entry>`, and the
  server serves them from the extraction. A planner without a cache extracts into a temporary directory. An inner
  zip over 1 GiB, or longer than its declared size, is refused. An inner zip that does not read is a problem of its
  root, and it leaves no extraction behind.
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`a_zip_inside_a_zip_is_extracted_once_and_read_as_a_zip`)
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`an_inner_zip_that_does_not_read_is_reported_and_leaves_nothing`)
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`a_zip_inside_a_bundle_is_not_opened`)
  [@test] ../crates/air-trace/src/serve/tests.rs (`a_zip_inside_a_zip_is_served_from_its_extraction`)

- `--teamcity <build id or URL>` resolves a build through the REST API, a composite build to the builds that ran
  under it. It downloads the `bazel.logs.zip` of each once into the cache, as `tc-<server hash>-<build id>/`. Each zip
  is a root with an `origin`: the name of the build configuration, the build id and the page. Each bundle and run of
  the listing carries it. A build that does not resolve or download is a root with its error, and the other builds
  still serve. `TEAMCITY_TOKEN` goes to the server of `--teamcity-url` only, through `curl`. For another server, or
  without the token, the TeamCity CLI signs the request. A server is `https`, or `http` on a loopback host.
  `--teamcity` refuses `--detach`.
  [@test] ../crates/air-trace/src/serve/teamcity/tests.rs
  [@test] ../crates/avl-trace-tools/src/discover/tests.rs (`the_origin_of_a_root_is_on_each_of_its_bundles`)

- The controller's pulled zips are the iteration zips under `workers/*/reports/*/traces/` and the scenario zips
  under `runs/*/traces/*/`. A scenario zip has the bundle id that the controller's `traceReady` names, and a
  zip that arrives while the server runs is announced.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`the_vm_runs_root_finds_per_scenario_zips`)

- `GET /__air/runs` answers every bundle found, grouped by run, and the state of each root. A bundle has a
  stable opaque id, so a viewer URL survives a restart.
  [@test] ../crates/air-trace/src/serve/tests.rs

- `GET /__air/runs` also answers `controllerRuns`: one summary for each journal under `runs/*/events.ndjson`,
  newest first. A summary has the command line, the start, the passed and failed tests, the traces, and the
  exit. A run without `runFinished` is running, and it is stopped after an hour with no record.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`controller_runs_are_listed_from_their_journals`)

- `GET /__air/run/<runId>?from=<offset>` answers the run's summary and the complete records of its journal from
  the byte offset on, with the offset to ask for next. An offset past the end reads from the start again. A run
  id is one path component, and a journal linked out of the runs directory is 403.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`the_run_route_answers_the_journal_from_an_offset`)

- `GET /__air/bundle/<id>/<path>` serves one file with `Range`.
  [@test] ../crates/air-trace/src/serve/tests.rs

- `GET /__air/events` sends `ready`, `runs-changed` and `bundle-append` as Server-Sent Events. A
  `bundle-append` event names its bundle and its file, and carries complete lines only.
  [@test] ../crates/air-trace/src/serve/tests.rs

- `GET /__air/events` sends `run-appended` with `{runId}` when a run's journal grows, and when a new run appears.
  The watcher follows the runs directory, the directory of each running run, and each run directory without a
  journal. A record that changes a count also changes the listing, and `runs-changed` follows.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`run_appended_is_sent_when_a_journal_grows`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`a_run_directory_seen_before_its_journal_is_watched`)

- A file event can come late or not at all. The server reads the runs directory every second, so a new run is
  announced without its event. A watcher that dropped events sends a rescan notice, and the notice starts a scan.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`a_new_run_is_announced_without_a_file_event`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`a_rescan_notice_of_the_watcher_starts_a_scan`)

- `POST /__air/plan` answers the planner's result for `{text}`, or for a dropped file whose bytes are the body
  and whose name is `?fileName=`. It adds the bundles of the server's listing for the scenarios the plan names,
  newest first.
  [@test] ../crates/air-trace/src/serve/tests.rs

- `POST /__air/plan` answers 400 for a request that it cannot read, and 422 for a plan that it cannot make.
  Both answers are `{error, code}`. `error` is the message, and the viewer shows it. `code` is
  `plan_request_unreadable`, `no_checkout` or `plan_failed`.
  [@test] ../crates/air-trace/src/serve/tests.rs (`plan_joins_the_resolved_scenarios_with_the_bundles_on_disk`)
  [@test] ../crates/air-trace/src/serve/tests.rs (`plan_outside_a_checkout_is_refused_with_its_code`)

- The routes that the viewer reads keep one contract, the file `serve-transcript.json` of the trace testdata. It
  holds the request, the status, the headers and the shape of the answer of each route. It also holds the first
  frames of the event stream. A test of the server and a test of the site both read it.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`the_routes_answer_what_the_site_reads`)
  [@test] ../../../../plugins/air/docs/src/trace/runJournal.test.ts (`the runs, one run and one bundle carry the fields the site reads`)

- `/air/` serves the built docs site from `out/air-site`, with the single-page fallback. `/` redirects to
  `/air/runs`.
  [@test] ../crates/air-trace/src/serve/tests.rs

- The default site needs a build when it is missing, or older than the newest file under `plugins/air/docs/src`.
  The server then runs `pnpm --dir plugins/air/docs build` once, in the background, with its output in its log. The
  placeholder page says that the site is building, and loads again. After a failure it says why, with the
  last lines of the build.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`the_site_is_built_once_while_the_placeholder_says_so`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`a_failed_site_build_says_why`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`a_current_site_is_not_built`)

- Every answer carries the header `X-Air-Trace: serve`. `avl_host_sys::viewer::probe_viewer` sends one `HEAD`
  of `/__air/runs` and knows the server by that header.
  [@test] ../crates/avl-host-sys/src/viewer/tests.rs (`the_probe_knows_the_viewer_by_its_header`)

- `air-trace serve --detach` exits 0 at once when a server answers on the port. Otherwise it starts the same
  program again in a new session and exits 0 when the new server answers, within 30 s. The detached server
  writes to `<runtime root>/viewer/serve.log` and keeps its process id in `serve.pid` beside it while it
  listens. `--detach` needs a fixed port. The controller makes the same start of `trace.cmd serve`, with the
  same arguments, and does not wait for it.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`detach_starts_one_server_that_stops_when_idle`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`detach_needs_a_fixed_port`)
  [@test] ../crates/avl-host-sys/src/viewer/tests.rs (`the_controllers_start_is_the_wrapper_with_the_detached_arguments`)

- `--idle-exit DURATION` stops the server after that long with no request in progress and no open event
  stream. It is 30 minutes with `--detach`, and 0, which never stops, without it.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`the_server_stops_when_idle_but_not_while_a_stream_is_open`)
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`detach_starts_one_server_that_stops_when_idle`)

- `avl-trace` owns the viewer's URLs: `runs_url`, `run_url` and `viewer_url`. The site's route table spells the
  same paths, and a test on each side pins the same literals.
  [@test] ../crates/avl-trace-tools/src/viewer/tests.rs (`the_viewer_urls_are_the_site_routes`)
  [@test] ../../../../plugins/air/docs/src/viewerUrls.test.ts (`the controller prints the routes of the site`)

- Every served path is checked against the roots after its links are resolved. Anything outside them is
  403, a `..` or a link out of a bundle included.
  [@test] ../crates/air-trace/src/serve/tests.rs

### The planner

- `air-trace plan INPUT...` and `POST /__air/plan` call one resolver, so both give the same answer.
  [@test] ../crates/air-trace/src/plan/tests.rs

- An input is a bundle, a `flow-*.txt`, a flow profile, a JUnit `test.xml`, or a `vm.cmd` report. It may also
  be a source path, a file name, or a name.
  [@test] ../crates/air-trace/src/plan/tests.rs

- A name is a class, a flow id, a suite, a scenario, or a step, operation or check id from a program.
  [@test] ../crates/air-trace/src/plan/tests.rs

- A source path is joined through the same code `vm.cmd suites` uses. Its unmapped reason is that code's
  reason, verbatim.
  [@test] ../crates/air-trace/src/plan/tests.rs

- A failed JUnit case maps to its scenario. A report maps its failures first, and its reproduce list only for
  classes no failure named.
  [@test] ../crates/air-trace/src/plan/tests.rs

- The answer is the scenarios per lane, the bundles the input is or names, the bundles on this machine for
  those scenarios, and the commands. The VM run comes first, and the host `bt.cmd` run is marked as taking the
  user's screen.

- Each VM line is one `vm.cmd run` that takes its own lease, so the answer spells no lease script. A GUI-chat
  line names `--backend parallels`, because that lane runs on the guest the user chose.
  [@test] ../crates/air-trace/src/plan/tests.rs

- `air-trace plan` looks for the bundles of its scenarios in the server's default roots. `--root` adds a
  place, and `--no-default-roots` drops the defaults. It lists them newest first, as the planner route does.
  A root it cannot read in full becomes a note.
  [@test] ../crates/air-trace/src/plan/tests.rs

- The planner reads a bundle with the server's reader, so the bundle has the same name, status and id in
  both. An archive that a report names and this machine does not hold is `missing`.
  [@test] ../crates/air-trace/src/plan/tests.rs

- A plan for one scenario of a class that runs several says so. Both runners select a whole class.
  [@test] ../crates/air-trace/src/plan/tests.rs

- The planner plans a suite of an explicit-only lane when its own class names it. Its warning names the cost of the
  lane. Each scenario of `ui-live` spends a billed turn, and another lane runs only when a caller names it. The
  live lane gets the piped guest run and no host line, because a host refuses the live lane.
  [@test] ../crates/air-trace/src/plan/tests.rs (`an_explicit_only_class_plans_the_piped_guest_run_and_no_host_run_of_the_live_lane`)

- The planner answers commands and never starts a run. The exit is 0 when it reached something, 3 when it
  reached nothing, 2 for usage, and 1 for an unreadable checkout.
  [@test] ../crates/air-trace/src/plan/tests.rs

- The planner and the controller read the UI lanes and their test labels from one table. That table reads them
  from bt's lane table. Neither tool keeps a list of lanes of its own. Neither table names a JUnit tag, because the
  lane daemon selects the documents of a lane by its name.
  [@test] ../crates/avl-affected/src/lanes/tests.rs
  [@test] ../../bt/crates/bt-core/src/lanes/tests.rs
  [@test] ../crates/avl-vm/src/lane/lanes/tests.rs

## User Experience

The Runs page, the run page and the trace page are in
[Flow UI Scenario Traces](../../../../plugins/air/spec/docs/flow-trace.spec.md#user-experience).

## Error Handling

- No capture, bridge, encoder, log or protocol problem reaches a test. Each becomes an `air.trace.error`
  record with its source, or a reason in the manifest.
  [@test] ../crates/avl-record/src/session/tests.rs

- A snapshot without a picture or a tree is still recorded, with `air.snapshot.error` saying which part is
  missing and why.
  [@test] ../crates/avl-record/src/session/tests.rs

- The controller's trace pull never fails an iteration. An iteration without a trace directory and without a
  `traceFailed` record reports neither `traces` nor `tracesError`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

## Testing / Local Run

- Run the Rust side with `./bazel.cmd test @community//tools/vm/...`.
- Regenerate the text files of both golden bundles after a recorder change with
  `UPDATE_EXPECT=1 cargo test -p avl-record transcript`, from `community/tools/vm`. A still has no update mode.
  The replay keeps a still that differs and names it, and that file replaces the golden one.
- Regenerate the server's transcript after a route change with
  `UPDATE_EXPECT=1 cargo test -p air-trace the_routes_answer_what_the_site_reads`, from `community/tools/vm`.
  Then run the site's reader of it with `pnpm run test:scripts`, from `plugins/air/docs`.
- Browse the bundles on this machine with `./community/tools/trace.cmd serve`, or keep one server for the
  machine in the background with `./community/tools/trace.cmd serve --detach`.
- Ask what to run with `./community/tools/trace.cmd plan <input>`.

## Open Questions / Risks

- The bundle does not record where a paint picture starts on the screen. On one display that is the point
  (0,0). A host with a display left of or above the primary one shifts tree overlays.
- The manifest's lane is the profile's spelling, such as `UI`. The planner speaks the lane names of `bt`,
  such as `ui`. A reader that joins them maps the names.
- A real run writes more `call` records than the golden transcript. The in-IDE reset calls the bridge inside
  the two reset spans, and the golden leaves them empty.
- A posted click records its point in a separate `press` entry, because its mouse events arrive after the
  gesture returns. The gesture's own entry keeps the target rectangle only.
- The stage marks no point on a macOS host's screen recording. That video is scaled down from the Retina size,
  and the viewer does not scale the points.
- Storage on CI is open.
- The recorder keeps every IDE span of a scenario. A scenario whose IDE ends many spans makes a large
  `spans.jsonl`. No filter by scope exists yet, because no bundle has needed one.

## References

- [ADR 0155](../../../../plugins/air/docs/decisions/0155-a-ui-scenario-leaves-a-trace.md), the rationale of the trace
- [VM guide](../docs/vm-ui-tests.md), section Traces
- [Flow UI Scenario Traces](../../../../plugins/air/spec/docs/flow-trace.spec.md)
- [The UI-lane Controller](lane-controller.spec.md)
- `../crates/avl-trace/testdata/lane-transcript.ndjson`
- `../crates/avl-trace/testdata/example.airtrace/`
- `../crates/avl-trace/testdata/serve-transcript.json`
