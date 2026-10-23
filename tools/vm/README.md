# vm: the Air UI-lane tooling

A cargo workspace (`Cargo.toml`, `Cargo.lock`, `rust-toolchain`) that Bazel builds too, through `avl.MODULE.bazel`
(repository `@avl`) and `avl.bzl`. The workspace is a package of the community module, beside BT. How to operate the
controller is the `vm-ui-tests` skill; the model behind it is `docs/vm-ui-tests.md`. ADR 0059 records the port from Go, and ADR 0186 records the native
Windows host. ADR 0074 records the rework: the crate rule, the closures, the error model and the command line.

`avl` stands for Air VM Lane; `avl.MODULE.bazel` says why the name is short. An agent that changes this workspace
follows [`AGENTS.md`](AGENTS.md).

## Documents

- [`docs/`](docs) holds the guide to the UI lanes, `vm-ui-tests.md`, and the notes on the Linux guest, the Windows
  lane and the open work.
- [`docs/decisions/`](docs/decisions) holds the ADRs of the controller. Its [`README.md`](docs/decisions/README.md)
  states how a record takes its number.
- [`spec/`](spec) holds the specs of the controller and the recorder. Its [`README.md`](spec/README.md) states the
  path conventions and the gate.

## Binaries

| wrapper | label | crate | what it is |
| --- | --- | --- | --- |
| `./community/tools/vm.cmd` | `:vm` | `avl-vm` | the lane controller |
| `./community/tools/trace.cmd` | `:air-trace` | `air-trace` | `serve`, `plan`, `pack` and `teamcity-publish` |
| (none; `ui_lane_ide.bzl`) | `:air-trace-record` | `avl-record` | the scenario recorder that the lane daemon starts for each iteration |

Each wrapper builds its binary on every call, so the binary is always built from the working copy. Both halves run
`bazel build` and read the path of the binary from the build event file.
`AIR_VM_BIN` and `AIR_TRACE_BIN` name a built binary instead and skip Bazel. BT, `./community/tools/bt.cmd`, is
the community workspace `community/tools/bt`; its README is its crate map. A UI lane takes the
recorder from its runfiles: `ui_lane_ide.bzl` names `:air-trace-record` for a lane on the host,
`:air-trace-record-linux-arm64` for one in an arm64 Linux guest and `:air-trace-record-linux-x86_64` for one in
an x86_64 Linux guest.

The guest half is `vm-guest-agent` (crate `avl-guest`), statically linked, as `:vm-guest-agent-linux-arm64`,
`:vm-guest-agent-linux-x86_64` and `:vm-guest-agent-darwin-arm64`. The controller never builds it under a lease:
the host build of `run`, `shard`, `flake` and `daemon start` adds the label for the worker's guest OS (`agent_label`
in `crates/avl-host-sys/src/guest/agent.rs`), and the install copies that output into `$AIR_VM_DATA/state`. The macOS
image pipeline builds the darwin label itself (`provision/scripts/lib.sh` of this workspace).

From the ultimate root the labels are `@community//tools/vm:<name>`, and from the community root `//tools/vm:<name>`.
The wrappers pick the form by the layout, as `bt.cmd` does. The controller reads the Air area from `bt.json` of the
checkout at its start. In a community checkout no `bt.json` names the Air area, so the controller refuses with
`air_area_missing`.

Every one of these labels is an `avl_binary` at the workspace root: one file, and always `compilation_mode=opt`,
whatever the command line says. The wrapper, `$(rlocationpath)` and the controller's `cquery ... [0]` for the agent each
need one output file. Optimized, because fastbuild Rust is `-Copt-level=0` for every
dependency, and the still encoders, the X11 frame copies and the zip deflate would run ten times slower or worse
inside a lane that measures time. The rule is `optimized_binary` of `community/build/rust-tools/defs.bzl`, which
`avl.bzl` binds as `avl_binary`. On a Unix host, what `bazel test` runs for Windows is
`:clippy-windows-x86_64` and `:clippy-windows-arm64`. On a Windows host `vm.cmd` and `trace.cmd` build and run `:vm`
and `:air-trace` natively.

## The Docker image

`docker/` holds the image of the Linux worker (`--backend docker`, ADR 0183): the `Dockerfile` and its
entrypoint `air-display`. The entrypoint starts the X display and the window manager. The image carries the guest
Node ([the Docker image carries Node, and the Tart Linux worker is retired](docs/decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). `avl-vm` embeds both files and
builds the image; nobody builds it by hand. The base is `DOCKER_BASE_IMAGE` in `community/tools/vm/provision/versions.env`.
`container.MODULE.bazel` pins Apple `container`, the engine of a Mac on macOS 26 or newer ([Apple container is a second engine of the Docker backend](docs/decisions/2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md), ADR 0224).
`docker.MODULE.bazel` pins the Docker CLI and its `docker-buildx` plugin, which a Mac runs against the engine of its
host environment.

## libaom

The recorder encodes a lossy still with libaom 3.15.2, the AV1 encoder. The source is the release tarball on the Space
file mirror. The crate `avl-aom-sys` holds the bindings of the encoder. Under cargo, its `build.rs` downloads the
release, checks its SHA-256 and builds it with cmake, which needs nasm on an x86_64 host. Bazel builds the same
release with `aom/aom.BUILD.bazel` and runs no build script. The repository is `@avl_aom` of `avl.MODULE.bazel`, and
its x86_64 assembly takes nasm of the Bazel Central Registry. `aom/config/` holds the config headers of cmake for arm64
and x86_64, and `aom/sources.bzl` the source lists. `avl.MODULE.bazel` states the steps of a version change.

## Crates

A crate is a directory under `crates/`, a Cargo package and a Bazel package of the same name. `avl_crate` in
`avl.bzl`, a binding of `rust_tool_crate` of `community/build/rust-tools/defs.bzl`, declares its library, its
`<crate>-test` target and a `<bin>-bin` per binary from its `Cargo.toml`, so a `BUILD.bazel` never repeats a
dependency. A crate without `src/lib.rs` is a binary crate, as Cargo has it, and its binary target has the name of its
`[[bin]]`. `avl-record` is one: nothing links the recorder, so its target is `:air-trace-record`. A `tests/<stem>.rs`
is the process test of the binaries of its crate, `<crate>-<stem>-test`, which the core declares with the test rule
of the unit test. Each shipped binary has one, `tests/cli.rs`, and each runs through one entry, `run`, that takes the
arguments after the program name and the streams it uses.

A crate exists when two binaries share it, or when it is the frozen-bytes engine of one binary with its own goldens:
`avl-report` writes the bytes an agent reads. Code that one binary uses is a module of that binary. So the `vm`
controller holds the modules `bench`, `console`, `daemon`, `lane`, `report` and `worker`, and `air-trace` holds `serve`,
`plan` and `publish`.
`build/docs/decisions/0043-the-crate-boundaries-follow-the-re-key-domains.md` states the same rule for the dev-dist tools.
The crates form a DAG with the contracts at the bottom. `avl-wire`,
`avl-trace` and `avl-testkit` depend on no sibling, and `avl-trace-tools` and `avl-bridge` stand on `avl-trace` alone.
`avl-junit` stands on `avl-wire` and on `serde`, `serde_json` and `jiff`, which the guest agent links already. So the guest agent can write the JUnit XML and the IJ Perf file.
`avl-record` links `avl-trace` and `avl-bridge` and no other sibling, so the recorder links no packer, no discovery and
no URL code. `avl-base` stands on `avl-wire`, and on `avl-trace-tools` for the viewer links its renderer prints. Of the
siblings, `avl-guest` depends on `avl-wire`, `avl-junit`, `avl-trace`, `avl-trace-tools` and `avl-bridge` alone and
never on `avl-base`, so the agent links no controller code. The recorder and the agent link one client of the bridge,
so they send one token header and follow one deadline rule.
`bt-junit` of BT's workspace, the JUnit XML reader, is linked by `air-trace`, which reads a `test.xml`, and by `avl-vm`,
which takes the class pattern of BT from it. The tests of `avl-junit` read back its XML with it. The agent links no
crate of BT. `bt-core` is linked by `avl-affected` and by the
two crates that resolve a lane or a selector, `avl-vm` and `air-trace`.
`avl-host-sys`, `avl-trace-tools`, `air-trace` and `avl-vm` link `fscopy` of the dev-dist tools for the
real path. `refusal` of BT's workspace holds the refusal type, and `avl-base`, `avl-affected`, `air-trace` and
`avl-guest` link it. `avl-affected` also links `distpath` of the dev-dist tools for the slash paths of BT. `avl-guest`
links `dev-launch` of the dev-dist tools, which composes the argument file of the lane IDE. Through it the agent links
`java-properties` and a regex engine.
`avl.bzl` maps each of these six crates to its label in the community module
(`_CROSS_MODULE_CRATES`), so Bazel builds it once. That build links the community module's `serde` and `regex`, so a value crosses as JSON text, and
`avl-affected` compiles its patterns with its own `regex!`. `avl-host-testkit` is only ever a
dev-dependency, and nothing depends on `avl-vm`.

The crates with `portable = True` in their `BUILD.bazel` also build and test on Windows, x86_64 and arm64. Every
crate is portable except `avl-guest`, which runs only inside a Linux or macOS guest (ADR 0186). A Windows host
drives the Docker backend only, so the Tart and Parallels modules of `avl-vm`'s `worker` are `cfg(unix)`. A test whose
fixture is a Tart or Parallels pool, or a shell script, is `cfg(unix)` too. The library of `avl-guest` carries no
platform constraint and is tagged `manual`, because rules_rs analyzes a binary's dependencies in the exec
configuration, and on a Windows host that is Windows. Its test, its clippy and its binary stay Unix only.

The lint policy is `[workspace.lints]` in `Cargo.toml`, and every crate's `[lints]` says `workspace = true`
(`avl_crate` fails otherwise). Bazel compiles each crate with it and runs clippy with it as `<crate>-clippy`. Under
Bazel every warning is an error: clippy's through `rust_lints_as_errors` of the `rust-tools` core, rustc's through
the `-Dwarnings` of the root `.bazelrc`. Under cargo, warnings stay warnings. `:clippy-linux-arm64`, `:clippy-linux-x86_64`,
`:clippy-windows-x86_64` and `:clippy-windows-arm64` at the root lint the code only those platforms compile.

No crate calls `fs::canonicalize`, which the shared `clippy.toml` bans. A host path that is compared with what `git`
and Bazel print goes through `avl_host_sys::fs::real_path`, which gives `C:/repo` on Windows. A path of the viewer
goes through `fscopy::resolve_links`, which gives `C:\repo`: discovery, the server, the planner and a pulled zip.
Discovery names a bundle by the text of the real path of its zip, so the controller names a pulled zip with the same
function.

No crate calls a `tempfile` persist either, which fails past `MAX_PATH` on Windows. `avl_base::fs` publishes a file:
`write_atomically` and `move_into_place` replace the destination with `fs::rename`, and `write_exclusively` and
`publish` refuse an existing destination, because `fs::hard_link` fails on an existing name. That refusal is the
mutual exclusion of a lease. The guest and `avl-trace-tools` do not link `avl-base`, so they rename their
temporary in the same way.

The four shipped binaries pin the crates they link. `<bin>_closure_test` in the crate of each binary compares them
with its `closure.txt`: `vm_closure_test` in `avl-vm`, `air-trace_closure_test` in `air-trace`,
`air-trace-record_closure_test` in `avl-record` and `vm-guest-agent_closure_test` in `avl-guest`. The closure is the
one for x86_64 Linux on every host, because each host adds crates of its own. The file leaves out the proc macros and
the crates that `_HOST_CRATES` of the core names. A new crate in a closure is a choice: after the change, build
`<bin>_closure` (for example `cd community && ./bazel.cmd build //tools/vm/crates/avl-vm:vm_closure`)
and copy `<bin>_closure.txt` from `out/bazel-bin` over `closure.txt`.

| concern | crate | owns |
| --- | --- | --- |
| contracts | `avl-wire` | the documents that cross a boundary: the run supervisor and every guest verb's name, the shape of a digest, the Bazel runtime descriptor, the daemon control channel, the documents of the lane IDE (the context document and the launch document with their checks, the launch record and the answer of `ide-gc`), progress records and the run journal, the agent-facing report, the runfiles MANIFEST reader, the host-to-guest path table `PathMap`, and the receipt of a raw pull. Each is declared once for both ends |
| | `avl-trace` | the scenario traces that the recorder writes: the lane protocol, OTLP, the bundle with its manifest encoder and decoder and its file names, the Allure result document, and the bridge's trace routes. The golden transcript and bundles are its `testdata`, a public filegroup. The transcript of the routes of `air-trace serve` is there too, and the docs site's test reads it |
| | `avl-trace-tools` | the tools over the traces, which the recorder does not run: the deterministic zips with the media stored (`air-trace pack` and the guest's `trace-pack-ready`), the discovery of bundles in directories and zips, and the viewer URLs with the default port of `air-trace serve` |
| | `avl-bridge` | the client of the IDE's bridge, which the recorder and the guest agent share: one HTTP/1.1 call over loopback with the launch's token, a deadline whose time left goes to the IDE, the typed error, in which a refused connect, a passed deadline and a bridge that unloads are transient and a refused token is final, the bridge failure with its cause chain, the readers of the trace routes, and the lifecycle reader with the wait for a phase over the long poll |
| base | `avl-base` | the exit vocabulary `Exit` and the controller's refusal, `refusal::Refusal<Exit>` with the constructors of `RefusalExt`, the `{ok,…}` envelope, the `AIR_VM_*` settings and their per-guest defaults (`src/config.rs`), phase timing, ids, the journal and history, atomic file publication. The image pins are read from `community/tools/vm/provision/versions.env` at compile time |
| guest | `avl-guest` | the guest half and the agent binary: the run supervisor, the Linux boot and macOS image verbs, the lane IDE contexts (the preparation checks its document and lays out the directories of one context, the launch checks its document, writes the argument file and the launch record, and starts the IDE run in the closed environment of its context, `ide-gc` stops IDE runs and removes old logs, and `cancel --thread-dump` writes a thread dump before the kill), `trace-pack-ready`, the `relay` verb that bridges its stdin and stdout to a loopback port inside the guest, the `runfiles-tree` verb that builds the runfiles tree of the host's MANIFEST with the copies of the staged runfiles, the `read-file` verb that writes one file on stdout unchanged for a pull, and the `lane-daemon` verb (`src/lane`): the control channel of the lane daemon, the owner of the lane IDE and its readiness, the lane services with the fake agent package, and the context files |
| host system | `avl-host-sys` | subprocesses, each with its timeout, and the byte stream of an exec channel child, the one wait loop with its budget and backoff (`Poll`), the batched lookup of the pinned files (`external_files`), the process-wide interrupt service and the operation context, locks and the one host-process identity that the Tart pid receipt, the lock record and the run marker share (`ProcessIdentity`), private files, the one mapping of host paths to guest paths (`GuestPaths`), the choice of a runfiles tree or a MANIFEST and the plan of the guest tree with its staged runfiles (`HostRunfiles`); what a guest must be to run host-built outputs (the read-only share set and its mount, the parity layout, TCC admission, the agent install, the run supervisor, the Linux guest's packages); the detached trace viewer: its probe and the one start of a detached server, which `air-trace serve --detach` and the controller share (`viewer`) |
| workers | `avl-vm` (`worker`) | the four backends, Tart, Parallels, Docker and `container-linux`, of which a Windows host has Docker and `container-linux` (the binaries, the share grammars, the sealed golden, worker provenance, the Docker image and its containers), the Apple `container` engine and its CLI dialect, one dispatch for each lifecycle operation, the one resolver of the pinned tools (`PinnedTools`), readiness gates and the `pool` commands, lease ownership, receipts, the run marker that names the run in flight on each worker (`run.json`) and the `lease` commands |
| reports | `avl-report` | the content digests that decide whether a warm iteration reuses the lane daemon, its IDE and its shares, report assembly, the TeamCity service messages from the run stream, the shard merge ladder and the flake arithmetic |
| | `avl-junit` | the JUnit XML of a run from its run stream, the fold of the stream into cases that the report and the TeamCity messages read too, one TeamCity service message, and the IJ Perf file of a scenario with its plan. The controller and the guest agent both write the XML and the IJ Perf file, so the crate links no controller code and does no file I/O. The recorded stream, the golden of its XML and the golden of the IJ Perf file are its `testdata` |
| lanes | `avl-vm` (`lane`) | lane and selector resolution for the controller, the `suites` command, the host Bazel build, the guest launch environment, the lane IDEs that the controller stops or keeps and the check that no IDE runs from the shares (`ide`), and `exec`, `peekaboo`, `pull`, `ls`, `vnc`, `status` |
| runs | `avl-vm` (`daemon`) | `run`, `shard`, `flake` and `daemon`: the HTTP client that reaches the guest daemon through the relay, the daemon start, the push of the files of the build, iterations, the verdict, the pull of each iteration's traces beside its report, the shard split and the flake trial chains, and the leased-run driver the three share: build first, lease, one body per worker, release, and the interrupt policy |
| run time | `avl-vm` (`report`) | `report time`: where the time of a run went, from its journal, its iteration reports and the spans of its trace bundles, with no worker; `--baseline` adds the ratio of each test class |
| bench | `avl-vm` (`bench`) | `vm bench`, the start-up measurement of the IDE on this host: the generation of a dev distribution, the Starter-shaped IDE launch and its supervision, the session, the readers of the files the IDE writes (the start-up report, the trace, the FUS log, the class log, the CPU profile), the gate, the summary and the digest |
| | `avl-vm` | the `vm` binary: the clap command tree, which renders the help, the global arguments and the dispatch, the output form and renderer the descriptors allow, the live terminal dashboard of `run`, `shard` and `flake` (`console`), `image validate\|build`, and the viewer a prose run starts (`ensure_viewer`, through `avl_host_sys::viewer`) |
| suites | `avl-affected` | which suites a changed path reaches (the suite's own files, the lane harness, the flow tags, the specs' `[@test]` links, the JPS modules), the UI lanes a VM worker runs with their test labels (`lanes`, which the controller and the planner share), and the bridge: the Air area that `bt.json` of the checkout names, read at the start of each program (`plugins/air/tests/integration/lanes.json`, with each UI lane's test label, which `AirIntegrationTagTest`, `AirTestTargetDeclarationTest`, `AirArchitectureModelTest` and the flow generator `docs/scripts/flowCatalog.ts` read too), and a BT refusal as the controller's |
| traces | `avl-record` | the recorder that the lane daemon starts, the `air-trace-record` binary: the protocol's state machine, the bundle writer, the Allure result of each bundle when a run asks for it, the snapshots, the `idea.log` slice; where the pixels come from (X11 over MIT-SHM, else the IDE's paint route), the stills, lossless WebP or, when the scenario has a video, lossy AVIF from libaom (`aom/`), the H.264 video the recorder encodes itself, its fragmented MP4 and frame index; ffmpeg only for a Mac's screen |
| | `air-trace` | the `air-trace` binary: dispatch to `pack`, which is `avl-trace-tools`', and the modules `serve` and `plan`. `air-trace serve`: the bundles discovery finds, by byte range and live, the planner route, the bundles of a TeamCity build (`--teamcity`), and the docs site; `air-trace teamcity-publish`: after a Bazel test on TeamCity, the one archive artifact `air-traces.zip` with the viewer page, `runs.json` in the listing form of `serve` and one zip for each bundle, the messages that publish it and link each test to its trace, which `avl-report` writes, the viewer, which it builds as `serve` builds the site when `--viewer` is absent, and the build from the agent's property files; `air-trace plan`: from a bundle, a flow text, a profile, a `test.xml`, a report, a path or a name to the scenarios, the bundles the input names, the bundles of those scenarios on disk, and the commands |
| tests | `avl-testkit` | test helpers: repository files under cargo and under Bazel, the paths of the scenario-trace goldens (`traces`), fake executables, the Tart fake with its fake `docker` and fake Apple `container`, and the loopback HTTP server that stands in for the IDE's bridge (`src/http.rs`). On Windows, which runs no shell script, the fake `docker` and the fake `git` are the Rust program `avl-fake` (`src/fakebin.rs`), linked under the tool's name and found through `AVL_FAKE`. A test compares it with the script, call by call, on Unix. The fakes are hard links to one file each, which all test processes share and which is named by its content (`src/shared.rs`), so only the first process after a change of a fake writes a new executable that macOS scans at its first exec |
| | `avl-host-testkit` | the host-side fixtures the controller crates' suites share, a dev-dependency only: the fake guest channels and their answers, the pool environment over the Tart fake, the fake host `git`, the Bazel that resolves the pinned Tart, Docker CLI and `docker-buildx` to the fakes, the parked-daemon probe, the fake process table of the `ps` probe. It stands below `avl-vm`, so each module's suite assembles its own `Manager` over it; `avl-host-sys`'s own suite keeps a fake channel of its own |

## The bench

`vm bench` measures the start-up of the IDE on a macOS host. ADR 0193 records why the verb is in `vm`.
`vm.cmd bench --help` states what a session does and the exit codes. `vm.cmd bench <verb> --help` states the grammar
and the defaults of the verb.

Every run is an integration test, as every run of the IDE Starter is, so a welcome run measures what the perf test of
the welcome screen measures. `session.json` names this measurement model, and
[a welcome run measures what the perf test measures](docs/decisions/2026-10-12-a-welcome-run-measures-what-the-perf-test-measures.md)
records it. A metric of a welcome span or of a modal span is the sum of the spans of its name, as the perf test sums them, and a name that
repeats is a note of the run. The digest shows these sums in the groups `welcome spans` and `modal spans`. The group
`span ends (ms from the process start)` holds the end of the first span of each start-up step, on the clock of the
anchors such as `welcomeBecameVisible`. That clock starts at the load of `com.intellij.idea.Main` in `class-load.log`,
the metric `jvm.processStart`. The report's `jvm.loadingTime` starts it about 100 ms too early, and only a log without
the main class uses it. The record
[The bench clock starts at the load of the main class](docs/decisions/2026-10-22-the-bench-clock-starts-at-the-load-of-the-main-class.md)
states why. A modal run opens no project, so the IDE writes its start-up report at the exit, and
the record reads it after the quit. Its `totalDuration` is negative or missing, so it is a note of the run and no
metric.

`--suppress-plugin <id>` of `welcome` and `project` adds a twin after each arm, such as `project-without-<id>`. A
run of a twin passes `-Didea.suppressed.plugins.id=<id>`, so the IDE loads no plugin of that id. The twin has its own
prime run and sandbox, and its runs interleave with the runs of its arm. Every verb that reads a session takes the
twin as an arm, and the noise check of `compare` uses the plain arms only.

A module is the main module or a content module of a plugin, and each module has its own class loader. The debug log
`plugin-classes.txt` names the module and the `findClass` nanos of each class. With the first class of a module, it
writes the stack that loaded the class. The spec [Plugin Class Log](../../platform/core-impl/spec/plugin-class-log.spec.md)
is the contract of its format. Its trailer `#cost` states the time that the IDE spent on the writes and on the stacks.
The run has these two times as the metrics `pluginLog.writeMs` and `pluginLog.stackMs`. A log without the trailer,
such as the log of a killed IDE, has neither metric. The reader also takes the legacy shape without a header.

The thread of a class comes from the JVM. `class-load.log` names the tid of each class and the start of each thread.
The JVM names a Java thread only when the thread ends, so each run writes the thread dump `threads.txt` with `jcmd`
after its gate. The dump runs before the stop of the profile, and both take their time from the hold. It names the tid
of the EDT, the thread `AWT-EventQueue-0`. A failed or late dump is a note of the run. A run without the dump has no
thread, so its EDT and BG times print `-`. With the dump, `classes.edt@<anchor>` counts the named classes that the EDT
loaded before the anchor.

The trigger frame of a module is the frame nearest to the top of its stack whose class has another owner. The search
skips the frames of the runtime, the language libraries, the service container, the extension points and the message
bus. A class without an owner in the log is a platform class. A log from a distribution before this shape has no time
and no stack, so the tables print `-` and say so. The decision record
[The bench names the trigger of each plugin loader](docs/decisions/2026-10-19-the-bench-names-the-trigger-of-each-plugin-loader.md)
states the rule.

`--profile` of `welcome` and `project` samples the CPU with async-profiler from the JVM start to the gate of each run. When the gate passes, the
run reads the PID of the IDE from `idea.log` and stops the profiler with `jcmd` of the generation, which writes
`cpu-gate.collapsed`. `--profile-event wall` samples the wall clock in place of the CPU. A run that cannot stop the
profiler keeps the whole-run profile `cpu.collapsed`, with a note that names why. The digest says which profile it read.

Two costs of the instruments stay out of the numbers. The JVM writes `class-load.log` asynchronously, with
`-Xlog:async:stall` and a buffer of 32 MB, because a flush of each class line under the global log lock costs a start
about half a second of CPU. A full buffer stalls the logging thread and drops no line, so the class count stays exact.
The profile leaves out the stacks under `Profiler::dlopen_hook`, where async-profiler parses the symbols of each library
that the JVM loads, and the digest notes how many samples it left out.

| verb | the question it answers | its output |
| --- | --- | --- |
| `welcome` | How long does the IDE take to show the welcome screen, in the modal and the non-modal arm? | A session directory with its `summary.json`, and a text digest. |
| `open-project <project>` | How long does a running IDE take to open a project and paint its editor? | A session with the open-project arm. |
| `project <project>` | How long does the IDE take from its start on a project to the highlighted editor, or, with `--arm empty-editor`, to the built Air composer of an empty editor area? With `--arm cargo-sync` and `--target //build:rustrover_dist`, to the first Cargo sync, as the RustRover perf test measures it. `--cold` starts each empty-editor run with empty caches. | A session with the project arm, the empty-editor arm, both, or the cargo-sync arm. |
| `replay <session>` | What does a finished session give when the controller parses it again? | A new `summary.json` and the digest, without an IDE. |
| `trace <session>` | What runs between the frame and the welcome paint? | The anchors of one run, and one line per span in the window. |
| `activities <session>` | Which post-startup activities run while the welcome panel paints? | One table by class and one table by plugin for one run. |
| `classes <session>` | Which classes did the JVM load before an anchor, and which plugin and module own them? | The class totals of one arm, one table by plugin with its modules and its EDT and BG ms, and one table by module. `--plugin` adds the rank of a plugin. |
| `classes <session> --span <name>` | Which classes did the EDT load inside a span? | The window of each run, the loads by plugin, by module and by package, and with `--timeline` the loads of each bucket. |
| `triggers <session>` | Which code pulled each module of a plugin? | One row per trigger frame of one run, with its modules, their classes, their EDT ms and the first load, then the modules of each trigger. |
| `compare <session> <session>...` | Did the change move the medians by more than the noise of the host? | The medians per arm with the delta to A, and one noise check per session. |
| `ls` | Which sessions does this host have? | One line per session, newest first, with a name that the other verbs accept. |
| `gc` | Which generations can go? | One line per removed generation, with its size. |

A session stages a generation under `<runtime root>/bench/generations/<digest>/`. The generation holds the clone of
the `.dist` directory, the dist config and `OpenedPackages.txt`. The home in the `.dist` directory holds the JBR at
`jbr/Contents/Home`, as a macOS production distribution does, and every run starts with that JBR. The digest of these
three inputs is the key, so a generation that exists is reused. Its files are read-only. Its `generation.json`
names the commit of the checkout that built it. A run starts from the home that the dist config names: the `.dist`
directory, or its `Contents` for a macOS app.

`--generation` takes the start of a digest and runs the session over that generation, with no build and no stage. The
session names the commit that built the generation, so `ls` and `compare` show the commit of the measured bytes. An
A/B/A run sets a change against the noise of this host: A1 measures the base, B the change, and A2 the bytes of A1
again. The second line of the digest of A1 names its digest, and `bench ls` names each session:

```bash
./community/tools/vm.cmd bench welcome                             # A1, on the base commit
./community/tools/vm.cmd bench welcome                             # B, after the change
./community/tools/vm.cmd bench welcome --generation 5f1c0d9e2b7a   # A2, over the generation of A1
./community/tools/vm.cmd bench compare aaaa1111 bbbb2222 cccc3333  # A1, B and A2 by a part of their names
```

`compare` sets B against A1, and A2 against A1 is the A/A run that tells the noise.

A session directory holds:

| file | what it holds |
| --- | --- |
| `events.ndjson` | the run journal |
| `session.json` | the inputs, with the generation, its digest, the measurement model `measureModel`, which `compare` notes when two sessions differ in it, and the `suppressedPlugin` of the twin arms |
| `build.log`, `template/`, `template-log/` | the host build, the sandbox template with the FUS test scheme, and its headless run |
| `<arm>-prime/`, `<arm>-run-NN/` | one run, by the label of its arm, such as `project-without-<id>-run-01` for a twin: `sandbox/`, `log/`, `launch.args`, `launch.log`, the files the IDE writes, `class-load.log` of the JVM, `threads.txt`, `plugin-classes.txt`, `fus.jsonl`, `result.json` with the class maps by plugin and by module and the activation of each module, and with `--profile` the profile `cpu-gate.collapsed` and the log `profile-stop.log` of its stop |
| `<arm>-sandbox/` | the sandbox of the run that is running now, at one path per arm |
| `summary.json` | `distDigest`, `measureModel`, `maxLoad`, `warnings`, the runs and the median, min and max of each metric per arm, and the non-modal minus modal `delta` |

The lock of a session and of `gc` is `<runtime root>/bench/session.lock`.

What a session cannot measure yet:

- `open-project` reports no number yet. The IDE opens the second project through the socket lock without a trace
  span, and `editor highlighting completed` is only an instant event of the start-up report, which the IDE writes
  once. So each run fails with a named reason, and the verb exits with 6. That is a gap of the product, not of the
  verb.

## Verification

```bash
(cd community && ./bazel.cmd test //tools/vm/... //tools/bt/... //build/rust-tools:duplicate-code_test)
./bazel.cmd test @community//tools/vm/... //plugins/air/tests/integration/vm-contract/...
./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-arm64 @community//tools/vm:air-trace-record-linux-arm64
./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-x86_64 @community//tools/vm:air-trace-record-linux-x86_64
(cd community/tools/vm && cargo test)
(cd community/tools/vm && cargo fmt --check && cargo clippy --workspace --all-targets -- -D warnings)
(cd community/tools/vm && RUSTC_BOOTSTRAP=1 cargo clippy -Zbuild-std=std,panic_abort --target x86_64-pc-windows-msvc --workspace --exclude avl-guest --all-targets)
bun community/build/rust-tools/sync.mjs --check
./community/tools/vm.cmd status
```

- Run the commands from the ultimate root. The first line also runs `:clippy-linux-*` and `:clippy-windows-*`. The
  clippy aspect skips a target of an external repository, so the clippy tests run only from `community/`.
- `//plugins/air/tests/integration/vm-contract` holds the tests that read files of the ultimate root: `bt.json` and
  the Air lane table, the `vm.cmd` examples of the docs, the runtime descriptor's writer, and the planner over the
  committed suite documents. The tests of this workspace read the copy of the Air lane table in `bt_core::fake`, and
  each one installs it with `avl_affected::bridge::install_fixture`.
- The cross-target cargo clippy lints the `cfg(windows)` code and the tests, which the Bazel clippy targets do not.
  `avl-guest` runs only inside a guest, so it is left out.
- After a change of a spec or a move of a file that a spec names, run `./community/tools/bt.cmd AirSpecReferencesTest`.
- After a change of a Starlark file, run `bazel run //:format.check`.

A golden is an `expect-test` snapshot: an `expect!` in the test, or a file that `expect_file!` names. `UPDATE_EXPECT=1
cargo test -p <crate>` rewrites it. The scenario-trace goldens stay plain files, because the Kotlin contract tests and
the docs site read them, and the replay test of `avl-record` holds its output to them with `expect_file!`. Their
stills are binary, which `expect-test` cannot hold, so a still has no update mode: the replay keeps a still that
differs and names it. The transcript of the routes of `air-trace serve` is a plain file for the same reason: the docs
site's test reads it. The server test holds its answers to it with `expect_file!`.

Bazel builds with rules_rs and the version `rust-toolchain` pins. A local cargo may be newer; `rust-version` in
`Cargo.toml` names the pinned version, so clippy's `incompatible_msrv` flags a std API the Bazel build would
refuse. `cargo test` runs the same unit tests for the host, and it resolves features the way cargo does, which
rules_rs does not always match. The Bazel test runs each `<crate>-clippy` too, with the pinned clippy, which is the
gate; `cargo clippy` agrees with it under a 1.93.1 cargo, and a newer one may warn about lints 1.93 does not have.
On a Mac neither compiles the Linux-only code (the X11 capture, the Linux guest verbs); the two Linux build lines,
`:clippy-linux-arm64` and `:clippy-linux-x86_64` do. The cross labels are `manual`, so `bazel test //...` does not
build them.

## After a change of `Cargo.lock`

`avl.MODULE.bazel` generates the crate hub `@avl` from `Cargo.toml`, `Cargo.lock` and `.cargo/config.toml` of this
directory. `community/MODULE.bazel` includes it, so the lockfiles of both roots record the facts of the hub. Both roots
run with `--lockfile_mode=error`, so every Bazel command fails until the lockfile matches again. Right after a change of
`Cargo.lock`, update both lockfiles:

```sh
cd community && ./bazel.cmd build --nobuild --lockfile_mode=update //tools/vm/...
cd .. && ./bazel.cmd build --nobuild --lockfile_mode=update @community//tools/vm/...
```

The extension watches only the workspace `Cargo.toml` and `Cargo.lock`. A dependency that moves between
`[dev-dependencies]` and `[dependencies]` of a member leaves `Cargo.lock` unchanged. Then touch the workspace
`Cargo.toml`, for example with a comment line, and run the command again.

A member names a crate of another workspace by path: `bt-core`, `bt-junit` and `refusal` of BT, and `dev-launch`,
`distpath` and `fscopy` of the dev-dist tools. rules_rs finds such a path only from a direct dependency of a member, so each member
that uses the crate names it. rules_rs also reads the `Cargo.toml` of the crate without its own workspace. So that
crate spells its `[package]` values inline, with no `.workspace = true`. A fetch of its repository proves it:

```sh
./bazel.cmd fetch --force --repo=@@rules_rs++crate+avl__<crate>-<version>
```
