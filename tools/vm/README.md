# vm: the Air UI-lane tooling

A cargo workspace (`Cargo.toml`, `Cargo.lock`, `rust-toolchain`) that Bazel builds too, through `avl.MODULE.bazel`
(repository `@avl`) and `avl.bzl`. The workspace is a package of the community module, beside BT. How to operate the
controller is the `vm-ui-tests` skill; the model behind it is `docs/vm-ui-tests.md`. [The UI-lane tooling is one Rust implementation in one cargo workspace that Bazel builds](openspec/changes/archive/2026-09-26-the-ui-lane-tooling-is-rust/design.md) records the port from Go, and `community/tools/vm/docs/a-windows-host-runs-the-controller-natively-plan.md` records the native
Windows host. [The UI-lane crates follow the re-key domains, and the audience of each binary picks its error model](openspec/changes/archive/2026-10-02-the-ui-lane-crates-follow-the-re-key-domains/design.md) records the rework: the crate rule, the closures, the error model and the command line.

`avl` stands for Air VM Lane; `avl.MODULE.bazel` says why the name is short. An agent that changes this workspace
follows [`AGENTS.md`](AGENTS.md).

## Documents

- [`docs/`](docs) holds the guide to the UI lanes, `vm-ui-tests.md`, and the notes on the Linux guest, the Windows
  lane and the open work.
- [`docs/decisions/`](docs/decisions) holds the older ADRs of the controller and takes no new record.
- [`openspec/`](openspec/README.md) is the OpenSpec root of the workspace. A new decision is the `design.md` of a
  change there, and the archive keeps it as the decision record.
- [`spec/`](spec) holds the specs of the controller and the recorder. Its [`README.md`](spec/README.md) states the
  path conventions and the gate.

## Binaries

| wrapper | label | crate | what it is |
| --- | --- | --- | --- |
| `./community/tools/vm.cmd` | `:vm` | `avl-vm` | the lane controller |
| `./community/tools/trace.cmd` | `:air-trace` | `air-trace` | `serve`, `plan`, `pack` and `teamcity-publish` |
| (none; `ui_lane_ide.bzl`) | `:air-trace-record` | `avl-record` | the scenario recorder that the lane daemon starts for each iteration |
| (none) | `:acp-fake-agent` | `avl-fake-agent` | the fake ACP agent that the Air tests start as the process of an agent |

Each wrapper builds its binary on every call, so the binary is always built from the working copy. Both halves run
`bazel build` and read the path of the binary from the build event file.
`AIR_VM_BIN` and `AIR_TRACE_BIN` name a built binary instead and skip Bazel. BT, `./community/tools/bt.cmd`, is
the community workspace `community/tools/bt`; its README is its crate map. A UI lane takes the
recorder from its runfiles: `ui_lane_ide.bzl` names `:air-trace-record` for a lane on the host,
`:air-trace-record-linux-arm64` for one in an arm64 Linux guest and `:air-trace-record-linux-x86_64` for one in
an x86_64 Linux guest. The fake agent has the same three labels: `:acp-fake-agent`, `:acp-fake-agent-linux-arm64` and
`:acp-fake-agent-linux-x86_64`.

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

`docker/` holds the image of the Linux worker (`--backend docker`, [The Linux worker is a Docker container built from the repository, and it is the default worker](openspec/changes/archive/2026-10-08-the-linux-worker-is-a-docker-container-and-the-default/design.md)): the `Dockerfile` and its
entrypoint `air-display`. The entrypoint starts the X display and the window manager. The image carries the guest
Node ([the Docker image carries Node, and the Tart Linux worker is retired](openspec/changes/archive/2026-10-08-the-linux-worker-is-a-docker-container-and-the-default/design.md)). `avl-vm` embeds both files and
builds the image; nobody builds it by hand. The base is `DOCKER_BASE_IMAGE` in `community/tools/vm/provision/versions.env`.
`container.MODULE.bazel` pins Apple `container`, the engine of a Mac on macOS 26 or newer ([The Docker backend runs on the engine of the host or on Apple container, by the macOS version](openspec/changes/archive/2026-11-18-the-docker-backend-runs-on-the-host-engine-or-apple-container/design.md)).
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
`build/openspec/changes/archive/2026-10-01-every-dist-tool-is-a-rust-binary/design.md` states the same rule for the distribution tools.
The crates form a DAG with the contracts at the bottom. `avl-wire`,
`avl-trace`, `avl-control` and `avl-testkit` depend on no sibling, and `avl-trace-tools` and `avl-bridge` stand on `avl-trace` alone.
`avl-junit` stands on `avl-wire` and on `serde`, `serde_json` and `jiff`, which the guest agent links already. So the guest agent can write the JUnit XML and the IJ Perf file.
`avl-record` links `avl-trace` and `avl-bridge` and no other sibling, so the recorder links no packer, no discovery and
no URL code. `avl-base` stands on `avl-wire`, and on `avl-trace-tools` for the viewer links its renderer prints. Of the
siblings, `avl-guest` depends on `avl-wire`, `avl-junit`, `avl-trace`, `avl-trace-tools`, `avl-bridge` and
`avl-control` alone and never on `avl-base`, so the agent links no controller code. The recorder and the agent link one client of the bridge,
so they send one token header and follow one deadline rule.
`bt-junit` of BT's workspace, the JUnit XML reader, is linked by `air-trace`, which reads a `test.xml`, and by `avl-vm`,
which takes the class pattern of BT from it. The tests of `avl-junit` read back its XML with it. The agent links no
crate of BT. `bt-core` is linked by `avl-affected` and by the
two crates that resolve a lane or a selector, `avl-vm` and `air-trace`.
`avl-host-sys`, `avl-trace-tools`, `air-trace` and `avl-vm` link `fscopy` of the distribution tools for the
real path. `refusal` of BT's workspace holds the refusal type, and `avl-base`, `avl-affected`, `air-trace` and
`avl-guest` link it. `avl-affected` also links `distpath` of the distribution tools for the slash paths of BT, and `checkout-files` of the
distribution tools, which `bt-core` links for the file listing of a checkout. rules_rs finds the path of such a crate
only in a dependency of a member of this workspace, so `avl-affected` names it. `avl-guest`
links `dev-launch` of the distribution tools, which composes the argument file of the lane IDE. Through it the agent links
`java-properties` and a regex engine.
`avl.bzl` maps each of these seven crates to its label in the community module
(`_CROSS_MODULE_CRATES`), so Bazel builds it once. That build links the community module's `serde` and `regex`, so a value crosses as JSON text, and
`avl-affected` compiles its patterns with its own `regex!`. `avl-host-testkit` is only ever a
dev-dependency, and nothing depends on `avl-vm`.

The crates with `portable = True` in their `BUILD.bazel` also build and test on Windows, x86_64 and arm64. Every
crate is portable except `avl-guest`, which runs only inside a Linux or macOS guest (`community/tools/vm/docs/a-windows-host-runs-the-controller-natively-plan.md`). A Windows host
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

The five shipped binaries pin the crates they link. `<bin>_closure_test` in the crate of each binary compares them
with its `closure.txt`: `vm_closure_test` in `avl-vm`, `air-trace_closure_test` in `air-trace`,
`air-trace-record_closure_test` in `avl-record`, `vm-guest-agent_closure_test` in `avl-guest` and
`acp-fake-agent_closure_test` in `avl-fake-agent`. The closure is the
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
| | `avl-control` | the proxy side of the fake-agent control channel in the IDE, which the spawner of the guest agent and the fake agent speak: the WebSocket of RFC 6455 as a server and as a client, base64 in its two alphabets and SHA-1, and the control protocol of one agent process with its `hello`, its identity, its envelopes and its commands. It links `serde_json` alone, because the guest agent pins its closure |
| base | `avl-base` | the exit vocabulary `Exit` and the controller's refusal, `refusal::Refusal<Exit>` with the constructors of `RefusalExt`, the `{ok,…}` envelope, the `AIR_VM_*` settings and their per-guest defaults (`src/config.rs`), phase timing, ids, the journal and history, atomic file publication. The image pins are read from `community/tools/vm/provision/versions.env` at compile time |
| guest | `avl-guest` | the guest half and the agent binary: the run supervisor, the Linux boot and macOS image verbs, the lane IDE contexts (the preparation checks its document and lays out the directories of one context, the launch checks its document, writes the argument file and the launch record, and starts the IDE run in the closed environment of its context, `ide-gc` stops IDE runs and removes old logs, and `cancel --thread-dump` writes a thread dump before the kill), `trace-pack-ready`, the `relay` verb that bridges its stdin and stdout to a loopback port inside the guest, the `runfiles-tree` verb that builds the runfiles tree of the host's MANIFEST with the copies of the staged runfiles, the `read-file` verb that writes one file on stdout unchanged for a pull, and the `lane-daemon` verb (`src/lane`): the control channel of the lane daemon, the owner of the lane IDE and its readiness, the lane services with the fake agent package, and the context files |
| host system | `avl-host-sys` | subprocesses, each with its timeout, the responsible process of a child on macOS (`Responsibility`), and the byte stream of an exec channel child, the one wait loop with its budget and backoff (`Poll`), the batched lookup of the pinned files (`external_files`), the process-wide interrupt service and the operation context, locks and the one host-process identity that the Tart pid receipt, the lock record and the run marker share (`ProcessIdentity`), the process table of a macOS host with the responsible process and the memory of each process (`host_processes`, `process_memory`), private files, the one mapping of host paths to guest paths (`GuestPaths`), the choice of a runfiles tree or a MANIFEST and the plan of the guest tree with its staged runfiles (`HostRunfiles`); what a guest must be to run host-built outputs (the read-only share set and its mount, the parity layout, TCC admission, the agent install, the run supervisor, the Linux guest's packages); the detached trace viewer: its probe and the one start of a detached server, which `air-trace serve --detach` and the controller share (`viewer`) |
| workers | `avl-vm` (`worker`) | the four backends, Tart, Parallels, Docker and `container-linux`, of which a Windows host has Docker and `container-linux` (the binaries, the share grammars, the sealed golden, worker provenance, the Docker image and its containers), the Apple `container` engine and its CLI dialect, one dispatch for each lifecycle operation, the one resolver of the pinned tools (`PinnedTools`), readiness gates and the `pool` commands, lease ownership, receipts, the run marker that names the run in flight on each worker (`run.json`) and the `lease` commands |
| reports | `avl-report` | the content digests that decide whether a warm iteration reuses the lane daemon, its IDE and its shares, report assembly, the TeamCity service messages from the run stream, the shard merge ladder and the flake arithmetic |
| | `avl-junit` | the JUnit XML of a run from its run stream, the fold of the stream into cases that the report and the TeamCity messages read too, one TeamCity service message, and the IJ Perf file of a scenario with its plan. The controller and the guest agent both write the XML and the IJ Perf file, so the crate links no controller code and does no file I/O. The recorded stream, the golden of its XML and the golden of the IJ Perf file are its `testdata` |
| lanes | `avl-vm` (`lane`) | lane and selector resolution for the controller, the `suites` command, the host Bazel build, the guest launch environment, the lane IDEs that the controller stops or keeps and the check that no IDE runs from the shares (`ide`), and `exec`, `peekaboo`, `pull`, `ls`, `vnc`, `status` |
| runs | `avl-vm` (`daemon`) | `run`, `shard`, `flake` and `daemon`: the HTTP client that reaches the guest daemon through the relay, the daemon start, the push of the files of the build, iterations, the verdict, the pull of each iteration's traces beside its report, the shard split and the flake trial chains, and the leased-run driver the three share: build first, lease, one body per worker, release, and the interrupt policy |
| run time | `avl-vm` (`report`) | `report time`: where the time of a run went, from its journal, its iteration reports and the spans of its trace bundles, with no worker; `--baseline` adds the ratio of each test class |
| bench | `avl-vm` (`bench`) | `vm bench`, the start-up measurement of the IDE on this host: the generation of a dev distribution, the Starter-shaped IDE launch and its supervision, the session, the readers of the files the IDE writes (the start-up report, the trace, the FUS log, the class log, the CPU profile), the memory sample at the gate, the gate, the summary and the digest |
| | `avl-vm` | the `vm` binary: the clap command tree, which renders the help, the global arguments and the dispatch, the output form and renderer the descriptors allow, the live terminal dashboard of `run`, `shard` and `flake` (`console`), `image validate\|build`, and the viewer a prose run starts (`ensure_viewer`, through `avl_host_sys::viewer`) |
| suites | `avl-affected` | which suites a changed path reaches (the suite's own files, the lane harness, the flow tags, the specs' `[@test]` links, the JPS modules), the UI lanes a VM worker runs with their test labels (`lanes`, which the controller and the planner share), and the bridge: the Air area that `bt.json` of the checkout names, read at the start of each program (`plugins/air/tests/integration/lanes.json`, with each UI lane's test label, which the architecture gate tests `air-architecture-model_test` and `air-docs-model_test` and the flow generator `docs/scripts/flowCatalog.ts` read too), and a BT refusal as the controller's |
| agents | `avl-fake-agent` | the fake ACP agent, the `acp-fake-agent` binary, std-only and blocking: the controlled proxy, which passes each line of the IDE to the control server and applies each command of the test process, the deterministic agent with its fixed answers, whose forms are the golden `testdata/answers.json` that a Kotlin test reads, the terminal sign-in and TUI modes, and the MCP client of a session over streamable HTTP and stdio. It links `avl-control` alone of the siblings |
| traces | `avl-record` | the recorder that the lane daemon starts, the `air-trace-record` binary: the protocol's state machine, the bundle writer, the Allure result of each bundle when a run asks for it, the snapshots, the `idea.log` slice; where the pixels come from (X11 over MIT-SHM, else the IDE's paint route), the stills, lossless WebP or, when the scenario has a video, lossy AVIF from libaom (`aom/`), the H.264 video the recorder encodes itself, its fragmented MP4 and frame index; ffmpeg only for a Mac's screen |
| | `air-trace` | the `air-trace` binary: dispatch to `pack`, which is `avl-trace-tools`', and the modules `serve` and `plan`. `air-trace serve`: the bundles discovery finds, by byte range and live, the planner route, the bundles of a TeamCity build (`--teamcity`), and the docs site; `air-trace teamcity-publish`: after a Bazel test on TeamCity, the one archive artifact `air-traces.zip` with the viewer page, `runs.json` in the listing form of `serve` and one zip for each bundle, the messages that publish it and link each test to its trace, which `avl-report` writes, the viewer, which it builds as `serve` builds the site when `--viewer` is absent, and the build from the agent's property files; `air-trace plan`: from a bundle, a flow text, a profile, a `test.xml`, a report, a path or a name to the scenarios, the bundles the input names, the bundles of those scenarios on disk, and the commands |
| tests | `avl-testkit` | test helpers: repository files under cargo and under Bazel, the paths of the scenario-trace goldens (`traces`), fake executables, the Tart fake with its fake `docker` and fake Apple `container`, and the loopback HTTP server that stands in for the IDE's bridge (`src/http.rs`). On Windows, which runs no shell script, the fake `docker` and the fake `git` are the Rust program `avl-fake` (`src/fakebin.rs`), linked under the tool's name and found through `AVL_FAKE`. A test compares it with the script, call by call, on Unix. The fakes are hard links to one file each, which all test processes share and which is named by its content (`src/shared.rs`), so only the first process after a change of a fake writes a new executable that macOS scans at its first exec |
| | `avl-host-testkit` | the host-side fixtures the controller crates' suites share, a dev-dependency only: the fake guest channels and their answers, the pool environment over the Tart fake, the fake host `git`, the Bazel that resolves the pinned Tart, Docker CLI and `docker-buildx` to the fakes, the parked-daemon probe, the fake process table of the `ps` probe. It stands below `avl-vm`, so each module's suite assembles its own `Manager` over it; `avl-host-sys`'s own suite keeps a fake channel of its own |

## The bench

`vm bench` measures the start-up of the IDE on a macOS host, and one search workload after the start-up: Find in
Files. [The start-up bench is a vm verb, and every IDE run starts from a staged generation](openspec/changes/archive/2026-10-01-the-start-up-bench-is-a-vm-verb-over-a-staged-generation/design.md) records why the verb is in `vm`.

The help is the one guide to its use. `./community/tools/vm.cmd bench --help` states the verb for each question, the
order of a class attribution, the measurement of a change, the host and the exit codes. The help of a verb states its
grammar and its defaults. This section states only the measurement model and the files of a session.

Every run is an integration test, as every run of the IDE Starter is, so a welcome run measures what the perf test of
the welcome screen measures. `session.json` names this measurement model, and
[a welcome run measures what the perf test measures](openspec/changes/archive/2026-10-12-a-welcome-run-measures-what-the-perf-test-measures/design.md)
records it. A metric of a welcome span or of a modal span is the sum of the spans of its name, as the perf test sums them, and a name that
repeats is a note of the run. The digest shows these sums in the groups `welcome spans` and `modal spans`. The group
`span ends (ms from the process start)` holds the end of the first span of each start-up step, on the clock of the
anchors such as `welcomeBecameVisible`. That clock starts at the load of `com.intellij.idea.Main` in `class-load.log`,
the metric `jvm.processStart`. The report's `jvm.loadingTime` starts it about 100 ms too early, and only a log without
the main class uses it. The record
[The bench clock starts at the load of the main class](openspec/changes/archive/2026-10-22-the-class-count-of-a-bench-run-is-the-jvm-class-load-log/design.md)
states why. A modal run opens no project, so the IDE writes its start-up report at the exit, and
the record reads it after the quit. Its `totalDuration` is negative or missing, so it is a note of the run and no
metric.

The IDE of a run starts as its own responsible process of macOS, as an app that Launch Services starts. After the
gate and before the thread dump, the run writes `memory.json`. It holds the physical footprint and the resident size
of the IDE, of each descendant, and of each process whose responsible process is the IDE. The WebKit XPC processes of a
web view are children of launchd, so only the responsible process names them. The record
[The bench finds the WebKit processes of the IDE by the responsible process](openspec/changes/archive/2026-11-20-the-bench-finds-webkit-processes-by-the-responsible-process/design.md)
states why. The digest group `memory at the gate (MB)` holds the footprint of the IDE, of the JCEF processes
(`cef_server` and its helpers), of the WebKit processes, of the other processes and of all, and the resident size of
the IDE and of all. An MB is 2^20 bytes, as Activity Monitor counts it. The group `processes at the gate (count)`
holds the processes of each group. A failed sample is a note of the run. The empty-editor arm opens no Markdown file,
so the IDE starts no Markdown preview. The project arm opens a Markdown file of its project, so its memory holds the
preview when the IDE shows one.

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
[The bench names the trigger of each plugin loader](openspec/changes/archive/2026-10-19-the-bench-names-the-trigger-of-each-plugin-loader/design.md)
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

A session stages a generation under `<runtime root>/bench/generations/<digest>/`. The generation holds the clone of
the `.dist` directory, the dist config, `OpenedPackages.txt` and `bench-preferences.jar`, the in-memory Java preferences
of `@community//tools/vm/bench-preferences`. The home in the `.dist` directory holds the JBR at
`jbr/Contents/Home`, as a macOS production distribution does, and every run starts with that JBR. The digest of these
four inputs is the key, so a generation that exists is reused. Its files are read-only. Its `generation.json`
names the commit of the checkout that built it. A run starts from the home that the dist config names: the `.dist`
directory, or its `Contents` for a macOS app.
The sandbox template takes the license `out/dev-data/idea/config/idea.key` of the checkout, or of the main checkout when
the checkout is a linked worktree without a license.
Besides the config and the system of its sandbox, each run gives the IDE the `home` of the sandbox as `user.home`, the
Java preferences in its memory and the passwords in its memory, so the IDE sees no account, no preferences and no common
data of the host, as a lane with a fresh home does. A cargo-sync run keeps the `user.home` of the host, because the
Rust plugin finds the toolchain in `~/.cargo` below it. The record
[A bench run sees no account and no data of the host](openspec/changes/archive/2026-11-22-a-bench-run-sees-no-account-and-no-data-of-the-host/design.md)
states why.

A session with `--generation` runs over a generation that exists, with no build and no stage. It names the commit that
built the generation, so `ls` and `compare` show the commit of the measured bytes.

A session directory holds:

| file | what it holds |
| --- | --- |
| `events.ndjson` | the run journal |
| `session.json` | the inputs, with the generation, its digest, the measurement model `measureModel`, which `compare` notes when two sessions differ in it, and the `suppressedPlugin` and the `withProperty` of the twin arms |
| `build.log`, `template/`, `template-log/` | the host build, the sandbox template with the FUS test scheme, and its headless run |
| `<arm>-prime/`, `<arm>-run-NN/` | one run, by the label of its arm, such as `project-without-<id>-run-01` for a twin: `sandbox/`, `log/`, `launch.args`, `launch.log`, the files the IDE writes, `class-load.log` of the JVM, `threads.txt`, `memory.json`, `plugin-classes.txt`, `fus.jsonl`, `result.json` with the class maps by plugin and by module and the activation of each module, and with `--profile` the profile `cpu-gate.collapsed` and the log `profile-stop.log` of its stop |
| `<arm>-sandbox/` | the sandbox of the run that is running now, at one path per arm |
| `summary.json` | `distDigest`, `measureModel`, `maxLoad`, `warnings`, the runs and the median, min and max of each metric per arm, and the non-modal minus modal `delta` |

The lock of a session and of `gc` is `<runtime root>/bench/session.lock`.

A run of the find-in-files arm writes the script `find-in-files.ijperf` of the performance plugin into its run
directory. The script is the line `%findInFiles -headless -project=<project> <query>;<query>`, and
`-Dtestscript.filename` names it. `-Dperformance.execute.script.right.after.ide.opened=true` runs it once the first
project opened: JetBrains Light holds the dumb mode, and the plugin would wait for the smart mode. `-headless` runs each
search to its last usage without the popup, because the popup search of JetBrains Light does not finish in a bench run.
`-project=<project>` runs the searches in the project of the project directory, after it opened and its
`FindInProjectDirectoriesProvider` gave its directories. JetBrains Light opens the project as a member project of a
workspace, because the workspace feature is on by default. It opens the host project of the workspace first, and the
script starts there, but the frame gives the member project to Find in Files. The gate is the span
`findInFiles#search: <query>` of each query. The metric `findInFiles.search` is the sum of the searches. `--directory
<dir>` adds a second pass over that directory of the project, with its subdirectories: the line `%findInFiles -headless
-project=<project> -directory=<path> <query>;<query>`, the spans `findInFiles#searchInDirectory: <query>` and their sum
`findInFiles.searchInDirectory`. The two passes take two paths through Find in Files, and the digest shows both. A
directory pass in the project directories skips the walk of the tree once accio covered all candidates. With
`--property ijlight.workspace.enabled=false`, the project opens outside a workspace, the IDE logs "No project directory
found for search", and the directory pass walks the whole tree of its directory. A run with that line in `idea.log` is
not valid, and its reason names the property. Each search is a metric with its duration, and `<span> results` holds its
result count, so a twin of `--with-property ijlight.accio.enabled=false` shows whether accio and the platform walk find
the same. The session builds the pool of `--context-plugins` from the working tree, also with `--generation`, and copies
its plugin directories into the `plugins` directory of the template. Each run gets the environment of `vm.cmd`, so the
accio of `PATH` answers the searches of the accio plugin. `--property accio.binary=<path>` and
`--property accio.home=<dir>` name another accio and its index for every run. For numbers that compare across
sessions, pass `accio.home` and index it before the session with `ACCIO_HOME=<dir> <accio> index --project <project>`,
so every session searches with an index that is ready.

## Verification

```bash
(cd community && ./bazel.cmd test //tools/vm/... //tools/bt/... //build/rust-tools:duplicate-code_test)
./bazel.cmd test @community//tools/vm/... //plugins/air/tests/integration/vm-contract/...
./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-arm64 @community//tools/vm:air-trace-record-linux-arm64 @community//tools/vm:acp-fake-agent-linux-arm64
./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-x86_64 @community//tools/vm:air-trace-record-linux-x86_64 @community//tools/vm:acp-fake-agent-linux-x86_64
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
- After a change of a spec or a move of a file that a spec names, run `./community/tools/bt.cmd //plugins/air:air-plugin-tests_test --filter com.intellij.air.plugin.AirSpecReferencesTest`.
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
`distpath` and `fscopy` of the distribution tools. rules_rs finds such a path only from a direct dependency of a member, so each member
that uses the crate names it. rules_rs also reads the `Cargo.toml` of the crate without its own workspace. So that
crate spells its `[package]` values inline, with no `.workspace = true`. A fetch of its repository proves it:

```sh
./bazel.cmd fetch --force --repo=@@rules_rs++crate+avl__<crate>-<version>
```
