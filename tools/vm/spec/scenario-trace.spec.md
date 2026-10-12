---
name: Scenario Traces
description: Requirements for the trace bundle a UI scenario leaves, the recorder's protocol, the capture, the pack, the server and the planner.
style: plain-1
targets:
  - ../crates/avl-trace/**
  - ../crates/avl-record/**
  - ../crates/air-trace/**
  - ../crates/avl-guest/src/tracepack.rs
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
video, a lossless picture and a Swing tree at every instruction boundary and held check, and the gestures. It
also holds the bridge calls, the scenario's slice of `idea.log`, and the generated program the scenario ran. The
OpenTelemetry spans that the IDE ended in the scenario are in it too, under the lane step that caused them.

A Rust sidecar, `air-trace-record`, writes the bundle while the lane runs. `air-trace serve` shows the bundles in the
Air docs site. `air-trace plan` says what to run to record new ones.

[Flow UI Scenario Traces](../../../../plugins/air/spec/docs/flow-trace.spec.md) specifies the lane's side of a trace,
the bridge routes and the viewer.

The contract is declared once, in Rust, in `community/tools/vm/crates/avl-trace`. Two golden files there
pin the Kotlin and TypeScript ends to it.

## Goals

- A bundle is self-contained, so a CI publisher can adopt it later without a viewer change.
- A killed lane JVM still leaves a readable bundle, marked as truncated.
- The parts that must run are Rust, so a guest needs no JVM to record, pack or serve.

## Non-goals

- Deciding a verdict. A trace is evidence, and every capture problem is recorded instead of thrown.
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
  `snap/NNNN.tree.json` for each snapshot, and a `snap/NNNN.webp` for each snapshot whose screen changed. It
  also holds `idea.log` and `bundle.json`. A file the recorder could not produce is absent, and the manifest
  or a log record says why.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- `bundle.json` is written last. Its `schema` is `air-trace/1`, and a reader refuses any other value. It
  names the run, the test class, the scenario, the flow, the lane, the launcher and the status.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- The manifest names the capture source and the video codec. Each carries a reason when it is `none`. A
  bundle without a manifest is still being written, or its recorder was killed outright.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- The status is `passed`, `failed`, `aborted` or `truncated`. A truncated manifest names the running span by
  its OTLP id, and only a truncated one does.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- `video.mp4` is H.264 in fragmented MP4 at a constant frame rate, without B-frames. Frame i plays at i over
  the frame rate, and the first frame plays at 0. `video.index.json` lists the capture time of every frame in order.
  An unchanged screen is still a frame, so the index has no gaps.
  [@test] ../crates/avl-record/src/video/h264/tests.rs
  [@test] ../crates/avl-record/src/video/mp4/tests.rs
  [@test] ../crates/avl-record/src/frames/tests.rs

- A snapshot's picture is lossless WebP, and its tree is the bridge's tree document. A snapshot of an
  unchanged screen gets no picture file. Its `air.snapshot.image` names the previous picture's file. So the
  last picture file by name is the last snapshot's picture.
  [@test] ../crates/avl-record/src/stills/tests.rs
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- When the recorder cannot write a picture, the next snapshot of the same screen writes its own file. The
  `air.trace.error` of the failure names the file that is missing.
  [@test] ../crates/avl-record/src/stills/tests.rs

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
  It also carries `air.program`, the generated profile verbatim.
  [@test] ../crates/avl-trace/src/bundle/tests.rs

- Every other lane span carries `air.span.kind` and `air.span.key`. An assertion span also carries its
  expectation sentence. Every span carries `air.span.status`, because OTLP has no word for aborted.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- The span kinds are `reset`, `setup`, `step`, `operation`, `action`, `assertion`, `journey` and `restart`.
  A span key is `<kind>:<id>` and holds no counter, time or path.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- Every log record is an event correlated with a span. The events are `air.span.started`, `air.snapshot`,
  `air.input`, `air.bridge.call`, `air.video` and `air.driver.step`. The other two are `exception` and
  `air.trace.error`.
  [@test] ../crates/avl-trace/src/otlp/tests.rs

- `air.span.started` is written when a span opens. It is how a truncated bundle names the spans that were
  running.
  [@test] ../crates/avl-record/src/session/tests.rs

- A failed span has OTLP status ERROR and an `exception` record with the type, the message and the stack.
  The stack is the frames in the order the JVM prints them, then each cause's first message line and frames.
  The lane caps the message at 16,000 characters and the stack at 200 lines.
  [@test] ../crates/avl-record/src/session/tests.rs

- Each Driver step is flattened in pre-order and correlated with the innermost span that contains its start.
  The recorder makes that choice once, at the end, when every span interval is known.
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
  `scenario`, `span`, `end`, `snap`, `call`, `restart`, `driverSteps` and `done`.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

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

- Only the lane knows three kinds of time: a call's start and duration, and a Driver step's start and stop.
  The recorder stamps everything else when the line arrives. A transcript therefore holds no other timestamp.
  [@test] ../crates/avl-trace/src/protocol/tests.rs

- Standard input reaching EOF, SIGINT or SIGTERM ends the open scenario as truncated. The recorder writes
  every open span as aborted, the root included, then the video and the manifest. It exits 0.
  [@test] ../crates/avl-record/src/tests.rs

- The recorder repairs three lane mistakes and records each as a `protocol` error. A new scenario before
  `done` truncates the previous one. An `end` of an outer span, or a `done`, aborts the spans still open
  inside it.
  [@test] ../crates/avl-record/src/session/tests.rs

- The recorder ignores SIGPIPE, so a lane that died cannot stop it from finishing the bundle.
  [@test] ../crates/avl-record/src/tests.rs

### Capture and video

- The recorder takes pixels from the first available source. X11 comes first, then the IDE's paint route,
  then none. The hello ack and the manifest name the source, and the reason for each missing rung.
  [@test] ../crates/avl-record/src/capture/tests.rs

- The recorder reads the screen itself only when the hello says `screen`. The Linux guest's lane targets set
  `-Dair.flow.trace.screen=on`, because the guest's `Xvfb` belongs to the lane. A direct lane on a
  developer's machine records the IDE's paint and no screen video. It records the screen only when the person
  running it passes the property or sets `AIR_TRACE_SCREEN=on`. Their screen shows everything else they have
  open. On macOS, reading it asks for Screen Recording permission, and that prompt can take the focus from the
  IDE.
  [@test] ../crates/avl-record/src/ladder/tests.rs (`the_ladder_reads_x_only_where_allowed_and_where_the_ide_draws_to_it`)
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

- The WebP encoding runs after the ack, on a bounded writer. `done` waits for every still before it writes
  the manifest.
  [@test] ../crates/avl-record/src/stills/tests.rs

- The `idea.log` slice is the bytes written between the scenario's start and its end. A rotated or replaced
  log adds the new file. A slice is capped at 32 MiB.
  [@test] ../crates/avl-record/src/idealog/tests.rs

- Under the checkout's root the recorder keeps the newest 20 runs, the current run always among them. The
  daemon's and Bazel's roots belong to owners who already clean them up.
  [@test] ../crates/avl-record/src/retention.rs

### Packing and the VM pull

- `air-trace pack SOURCE DEST` writes a deterministic zip of every bundle under a directory. Entries are in
  byte order with one fixed time and mode.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs

- Compressed media are stored, and everything else is deflated. A stored entry sits at one offset, so a
  server answers a byte range straight out of the zip.
  [@test] ../crates/avl-trace-tools/src/pack/tests.rs

- The guest agent's `trace-pack-ready <sourceDir> <destinationZip> <ledger> [--all]` verb runs the code of
  `air-trace pack` on the guest. It packs only the bundles that the ledger does not name. Without `--all` it
  packs only the finished bundles, the ones with a manifest. It adds each packed bundle to the ledger, and it
  writes no zip when nothing is new.
  [@test] ../crates/avl-guest/src/tracepack/tests.rs

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

- The Linux guest installs no video encoder. The recorder carries its own, so a Linux worker records video.
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs

### The server

- `air-trace serve` listens on `127.0.0.1:7357` unless told otherwise. It answers only requests addressed to
  a loopback name, and refuses a request the browser marks as cross-site.
  [@test] ../crates/air-trace/src/serve/tests.rs

- By default it looks in `out/air-traces`, in the Air lanes' `bazel-testlogs`, and in the controller's pulled
  zips. `--root` adds a place, and `--no-default-roots` drops the defaults.
  [@test] ../crates/air-trace/src/serve/tests.rs

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

- `GET /__air/events` sends `run-appended` with `{runId}` when a run's journal grows. The watcher follows the
  runs directory and the directory of each running run. A record that changes a count also changes the
  listing, and `runs-changed` follows.
  [@test] ../crates/air-trace/src/serve/tests/service.rs (`run_appended_is_sent_when_a_journal_grows`)

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

- The planner answers commands and never starts a run. The exit is 0 when it reached something, 3 when it
  reached nothing, 2 for usage, and 1 for an unreadable checkout.
  [@test] ../crates/air-trace/src/plan/tests.rs

- The planner and the controller read the UI lanes, their test labels and their JUnit tags from one table.
  That table reads them from bt's lane table. Neither tool keeps a lane table of its own.
  [@test] ../crates/avl-affected/src/lanes/tests.rs
  [@test] ../../bt/crates/bt-core/src/lanes/tests.rs

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

- The controller's trace pull never fails an iteration. An iteration without a trace directory reports
  neither `traces` nor `tracesError`.
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
- A real run writes more `call` records than the golden transcript. The in-IDE reset and the scenario
  preparation call the bridge inside spans the golden leaves empty.
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
