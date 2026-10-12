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
| `./community/tools/trace.cmd` | `:air-trace` | `air-trace` | `serve`, `plan` and `pack` |
| (none; `ui_lane_ide.bzl`) | `:air-trace-record` | `avl-record` | the scenario recorder a lane JVM starts from its runfiles |

Each wrapper runs `bazel run --script_path` on every call, so the binary is always built from the working copy.
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
whatever the command line says. One file, because `--script_path`, `$(rlocationpath)` and the controller's
`cquery ... [0]` for the agent each need exactly one. Optimized, because fastbuild Rust is `-Copt-level=0` for every
dependency, and the WebP stills, the X11 frame copies and the zip deflate would run ten times slower or worse
inside a lane that measures time. The rule is `optimized_binary` of `community/build/rust-tools/defs.bzl`, which
`avl.bzl` binds as `avl_binary`. On a Unix host, what `bazel test` runs for Windows is
`:clippy-windows-x86_64` and `:clippy-windows-arm64`. On a Windows host `vm.cmd` and `trace.cmd` build and run `:vm`
and `:air-trace` natively.

## The Docker image

`docker/` holds the image of the Linux worker (`--backend docker`, ADR 0183): the `Dockerfile` and its
entrypoint `air-display`. The entrypoint starts the X display and the window manager. The image carries the guest
Node (ADR 0210). `avl-vm` embeds both files and
builds the image; nobody builds it by hand. The base is `DOCKER_BASE_IMAGE` in `community/tools/vm/provision/versions.env`.
`docker/engine.lima.yaml` is the template of the Lima VM that runs the engine on a macOS host (ADR 0189). `avl-vm`
embeds it too. `docker.MODULE.bazel` pins the Docker CLI and its `docker-buildx` plugin, and `lima.MODULE.bazel` pins
Lima.

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
controller holds the modules `bench`, `console`, `daemon`, `lane` and `worker`, and `air-trace` holds `serve` and
`plan`.
`build/decisions/0043-the-crate-boundaries-follow-the-re-key-domains.md` states the same rule for the dev-dist tools.
The crates form a DAG with the contracts at the bottom. `avl-wire`,
`avl-trace` and `avl-testkit` depend on no sibling, and `avl-trace-tools` stands on `avl-trace` alone. `avl-record`
links `avl-trace` and no other sibling, so the recorder links no packer, no discovery and no URL code. `avl-base`
stands on `avl-wire`, and on `avl-trace-tools` for the viewer links its renderer prints. `avl-guest` depends on
`avl-wire` and `avl-trace-tools` alone and never on `avl-base`, so the agent links no controller code. `bt-junit`
of BT's workspace, the JUnit XML reader, is linked by the three crates that read a `test.xml`: `avl-report`,
`avl-vm` and `air-trace`. The agent links no crate of BT. `bt-core` is linked by `avl-affected` and by the
two crates that resolve a lane or a selector, `avl-vm` and `air-trace`.
`avl-host-sys`, `avl-trace-tools`, `air-trace` and `avl-vm` link `fscopy` of the dev-dist tools for the
real path. `refusal` of BT's workspace holds the refusal type, and `avl-base`, `avl-affected`, `air-trace` and
`avl-guest` link it. `avl-affected` also links `distpath` of the dev-dist tools for the slash paths of BT.
`avl.bzl` maps each of these five crates to its label in the community module
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
| contracts | `avl-wire` | the documents that cross a boundary: the run supervisor and every guest verb's name, staging and the generation layout, the Bazel runtime descriptor and the daemon's JVM @-file, the daemon control channel, progress records and the run journal, the agent-facing report, the runfiles MANIFEST reader, the host-to-guest path table `PathMap`, and the receipt of a raw pull. Each is declared once for both ends |
| | `avl-trace` | the scenario traces that the recorder writes: the lane protocol, OTLP, the bundle with its manifest encoder and decoder and its file names, and the bridge's trace routes. The golden transcript and bundles are its `testdata`, a public filegroup the Kotlin contract tests read. The transcript of the routes of `air-trace serve` is there too, and the docs site's test reads it |
| | `avl-trace-tools` | the tools over the traces, which the recorder does not run: the deterministic zips with the media stored (`air-trace pack` and the guest's `trace-pack-ready`), the discovery of bundles in directories and zips, and the viewer URLs with the default port of `air-trace serve` |
| base | `avl-base` | the exit vocabulary `Exit` and the controller's refusal, `refusal::Refusal<Exit>` with the constructors of `RefusalExt`, the `{ok,…}` envelope, the `AIR_VM_*` settings and their per-guest defaults (`src/config.rs`), phase timing, ids, the journal and history, atomic file publication. The image pins are read from `community/tools/vm/provision/versions.env` at compile time |
| guest | `avl-guest` | the guest half and the agent binary: the run supervisor, the runtime stager, the Linux boot and macOS image verbs, `trace-pack-ready`, the `relay` verb that bridges its stdin and stdout to a loopback port inside the guest, the `runfiles-tree` verb that builds a runfiles tree from the MANIFEST of a Windows host, and the `read-file` verb that writes one file on stdout unchanged for a pull |
| host system | `avl-host-sys` | subprocesses, each with its timeout, and the byte stream of an exec channel child, the one wait loop with its budget and backoff (`Poll`), the batched lookup of the pinned files (`external_files`), the process-wide interrupt service and the operation context, locks and host-process identity, private files, the one mapping of host paths to guest paths (`GuestPaths`), the choice of a runfiles tree or a MANIFEST (`HostRunfiles`); what a guest must be to run host-built outputs (the read-only share set and its mount, the parity layout, TCC admission, the agent install, the run supervisor, the Linux guest's packages); the detached trace viewer: its probe and the one start of a detached server, which `air-trace serve --detach` and the controller share (`viewer`) |
| workers | `avl-vm` (`worker`) | the three backends, Tart, Parallels and Docker, of which a Windows host has Docker only (the binaries, the share grammars, the sealed golden, worker provenance, the Docker image and its containers), the Lima engine VM of a Docker pool on a macOS host, one dispatch for each lifecycle operation, the one resolver of the pinned tools (`PinnedTools`), readiness gates and the `pool` commands, lease ownership, receipts and the `lease` commands |
| reports | `avl-report` | the content digests that decide whether a staged generation is reused, report assembly, the shard merge ladder and the flake arithmetic |
| lanes | `avl-vm` (`lane`) | lane and selector resolution for the controller, the `suites` command, the host Bazel build, the guest launch environment, and `exec`, `peekaboo`, `pull`, `ls`, `vnc`, `status` |
| runs | `avl-vm` (`daemon`) | `run`, `shard`, `flake` and `daemon`: the HTTP client that reaches the guest daemon through the relay, staging, the hot-jar push, iterations, the verdict, the pull of each iteration's traces beside its report, the shard split and the flake trial chains, and the leased-run driver the three share: build first, lease, one body per worker, release, and the interrupt policy |
| bench | `avl-vm` (`bench`) | `vm bench`, the start-up measurement of the IDE on this host: the generation of a dev distribution, the Starter-shaped IDE launch and its supervision, the session, the readers of the files the IDE writes (the start-up report, the trace, the FUS log, the class log, the CPU profile), the gate, the summary and the digest |
| | `avl-vm` | the `vm` binary: the clap command tree, which renders the help, the global arguments and the dispatch, the output form and renderer the descriptors allow, the live terminal dashboard of `run`, `shard` and `flake` (`console`), `image validate\|build`, and the viewer a prose run starts (`ensure_viewer`, through `avl_host_sys::viewer`) |
| suites | `avl-affected` | which suites a changed path reaches (the suite's own files, the lane harness, the flow tags, the specs' `[@test]` links, the JPS modules), the UI lanes a VM worker runs with their test labels and JUnit tags (`lanes`, which the controller and the planner share), and the bridge: the Air area that `bt.json` of the checkout names, read at the start of each program (`plugins/air/tests/integration/lanes.json`, with each UI lane's test label and JUnit tag, which `AirIntegrationTagTest`, `AirArchitectureModelTest` and the flow generator `docs/scripts/flowCatalog.ts` read too), and a BT refusal as the controller's |
| traces | `avl-record` | the recorder a lane JVM starts, the `air-trace-record` binary: the protocol's state machine, the bundle writer, the snapshots, the `idea.log` slice, retention of the IDE root; where the pixels come from (X11 over MIT-SHM, else the IDE's paint route), the lossless WebP stills, the H.264 video the recorder encodes itself, its fragmented MP4 and frame index; ffmpeg only for a Mac's screen; the client of the IDE's trace routes |
| | `air-trace` | the `air-trace` binary: dispatch to `pack`, which is `avl-trace-tools`', and the modules `serve` and `plan`. `air-trace serve`: the bundles discovery finds, by byte range and live, the planner route, and the docs site; `air-trace plan`: from a bundle, a flow text, a profile, a `test.xml`, a report, a path or a name to the scenarios, the bundles the input names, the bundles of those scenarios on disk, and the commands |
| tests | `avl-testkit` | test helpers: repository files under cargo and under Bazel, the paths of the scenario-trace goldens (`traces`), fake executables, the Tart fake with its fake `docker` and fake `limactl`. On Windows, which runs no shell script, the fake `docker` and the fake `git` are the Rust program `avl-fake` (`src/fakebin.rs`), linked under the tool's name and found through `AVL_FAKE`. A test compares it with the script, call by call, on Unix. The fakes are hard links to one file each, which all test processes share and which is named by its content (`src/shared.rs`), so only the first process after a change of a fake writes a new executable that macOS scans at its first exec |
| | `avl-host-testkit` | the host-side fixtures the controller crates' suites share, a dev-dependency only: the fake guest channels and their answers, the pool environment over the Tart fake, the fake host `git`, the Bazel that resolves the pinned Tart, Docker CLI, `docker-buildx` and `limactl` to the fakes, the parked-daemon probe, the fake process table of the `ps` probe. It stands below `avl-vm`, so each module's suite assembles its own `Manager` over it; `avl-host-sys`'s own suite keeps a fake channel of its own |

## The bench

`vm bench` measures the start-up of the IDE on a macOS host. ADR 0193 records why the verb is in `vm`.
`vm.cmd bench --help` states what a session does and the exit codes. `vm.cmd bench <verb> --help` states the grammar
and the defaults of the verb.

| verb | the question it answers | its output |
| --- | --- | --- |
| `welcome` | How long does the IDE take to show the welcome screen, in the modal and the non-modal arm? | A session directory with its `summary.json`, and a text digest. |
| `open-project <project>` | How long does a running IDE take to open a project and paint its editor? | A session with the open-project arm. |
| `project <project>` | How long does the IDE take from its start on a project to the highlighted editor, or, with `--arm empty-editor`, to the built Air composer of an empty editor area? `--cold` starts each empty-editor run with empty caches. | A session with the project arm, the empty-editor arm, or both. |
| `replay <session>` | What does a finished session give when the controller parses it again? | A new `summary.json` and the digest, without an IDE. |
| `trace <session>` | What runs between the frame and the welcome paint? | The anchors of one run, and one line per span in the window. |
| `activities <session>` | Which post-startup activities run while the welcome panel paints? | One table by class and one table by plugin for one run. |
| `classes <session>` | Which classes did the JVM load before an anchor, and which plugin owns them? | The class totals of one arm, and one table by plugin and one by module. |
| `compare <session> <session>...` | Did the change move the medians by more than the noise of the host? | The medians per arm with the delta to A, and one noise check per session. |
| `ls` | Which sessions does this host have? | One line per session, newest first, with a name that the other verbs accept. |
| `gc` | Which generations can go? | One line per removed generation, with its size. |

A session stages a generation under `<runtime root>/bench/generations/<digest>/`. The generation holds the clones of
the `.dist` directory and of the JBR home of the row launcher, the dist config and `OpenedPackages.txt`. The digest of
these four inputs is the key, so a generation that exists is reused. Its files are read-only.

A session directory holds:

| file | what it holds |
| --- | --- |
| `events.ndjson` | the run journal |
| `session.json` | the inputs, with the generation and its digest |
| `build.log`, `template/`, `template-log/` | the host build, the sandbox template with the FUS test scheme, and its headless run |
| `<arm>-prime/`, `<arm>-run-NN/` | one run: `sandbox/`, `log/`, `launch.args`, `launch.log`, the files the IDE writes, `class-load.log` of the JVM, `fus.jsonl`, `result.json` |
| `<arm>-sandbox/` | the sandbox of the run that is running now, at one path per arm |
| `summary.json` | `distDigest`, `maxLoad`, `warnings`, the runs and the median, min and max of each metric per arm, and the non-modal minus modal `delta` |

The lock of a session and of `gc` is `<runtime root>/bench/session.lock`.

What a session cannot measure yet:

- `open-project` reports no number yet. The IDE opens the second project through the socket lock without a trace
  span, and `editor highlighting completed` is only an instant event of the start-up report, which the IDE writes
  once. So each run fails with a named reason, and the verb exits with 6. That is a gap of the product, not of the
  verb.
- The modal arm writes no `startup-stats.json`, so it has no report metrics and no platform spans.

## Verification

```bash
(cd community && ./bazel.cmd test //tools/vm/... //tools/bt/...)
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

A member names a crate of another workspace by path: `bt-core`, `bt-junit` and `refusal` of BT, and `distpath` and
`fscopy` of the dev-dist tools. rules_rs finds such a path only from a direct dependency of a member, so each member
that uses the crate names it. rules_rs also reads the `Cargo.toml` of the crate without its own workspace. So that
crate spells its `[package]` values inline, with no `.workspace = true`. A fetch of its repository proves it:

```sh
./bazel.cmd fetch --force --repo=@@rules_rs++crate+avl__<crate>-<version>
```
