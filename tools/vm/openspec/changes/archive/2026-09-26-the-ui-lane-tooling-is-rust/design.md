# The UI-lane tooling is one Rust implementation in one cargo workspace that Bazel builds

## Context

The UI-lane tooling issues the verdict of a lane. It is the controller `vm`, the guest agent `vm-guest-agent`, the
recorder `air-trace-record`, and `air-trace` with the server and the planner. BT, `bt`, selects the tests of a change.
A tool with that authority needs a compiler between an edit and a run, hermetic tests, and one binary for each
program.

The workspace lives in `community/tools/vm`, and the
[workspace decision](../2026-10-07-the-ui-lane-workspace-lives-in-community/design.md) states why. BT is the separate
workspace `community/tools/bt`. The [crate decision](../2026-10-02-the-ui-lane-crates-follow-the-re-key-domains/design.md)
states the crate rule, the error model and the test layout. The [README](../../../../README.md#crates) holds the
crate map.

Two rules shape the code. A well-tested crate is better than hand-written code. Only a real contract between two
programs stays compatible, and a change of that contract changes both ends in one commit.

## Decisions

1. **One implementation issues lane verdicts.** Two live controllers are two sources of verdicts. So a second
   implementation never runs beside this one, and a replacement switches the wrappers and deletes the old code in
   one commit.

2. **The tooling is one cargo workspace, and Bazel builds it.**
   - The workspace is `Cargo.toml`, `Cargo.lock`, `.cargo/config.toml` and `rust-toolchain`. A crate is
     `crates/<crate>/`, and its directory name is its package name and its Bazel package name.
   - [`avl.MODULE.bazel`](../../../../avl.MODULE.bazel) reads the workspace with rules_rs into the hub `@avl`. The
     name is short, because a long repository name breaks the Windows build on the path length.
   - `avl_crate` in [`avl.bzl`](../../../../avl.bzl) derives the library, the tests and the binaries of a crate from
     its `Cargo.toml`. A `BUILD.bazel` file does not repeat a dependency.
   - Each test target runs through `rust_test_junit`, which writes one JUnit case for each test. BT counts cases,
     and a plain test target reports one case for the whole target.
   - Each feature of a third-party crate is set in `[workspace.dependencies]` only. rules_rs resolves one feature
     set for each crate of the workspace, so a feature in a member is a second answer.
   - `[workspace.lints]` is the lint policy of every crate. `sync.mjs` generates it from
     [`lints.toml`](../../../../../../build/rust-tools/lints.toml), and `rust_lints_as_errors` makes each
     `<crate>-clippy` test fail on a warning.

3. **A crate replaces hand-written infrastructure.** clap parses the command lines, and serde with serde_json reads
   and writes the JSON. zip writes the archives, and x11rb captures X11 with MIT-SHM. image-webp encodes a lossless
   still, and png decodes a frame of the IDE. The
   [still decision](../2026-10-23-a-scenario-with-a-video-takes-lossy-avif-stills/design.md) gives the lossy still.
   notify watches the files. axum with tower-http serves HTTP, and hyper is the client. nix and signal-hook
   handle the processes and the signals. console and anstyle draw the terminal. The JUnit reader is `bt-junit` of
   BT, over quick-xml. The crate decision adds two limits: no crate links reqwest, and no file is published through
   a tempfile persist.

4. **The dashboard draws with console.** Its live region grows with the running workers and ends as the taller
   verdict card. The [palette decision](../2026-09-27-the-dashboards-palette-is-derived-from-the-terminals-colours/design.md)
   gives its colours.

5. **The design follows the language, and parity with an older implementation is no goal.**
   - **One interrupt service.** [`interrupt.rs`](../../../../crates/avl-host-sys/src/interrupt.rs) installs one
     SIGINT and SIGTERM handler for the process. The first signal cancels the root token and forwards the signal
     to every registered process group. A second Ctrl-C kills every group and leaves at once.
   - **One operation context.** `Ctx` of [`ctx.rs`](../../../../crates/avl-host-sys/src/ctx.rs) carries the
     cancellation token, the phase timeline and handle, and the run id as one value. A call that forgets it does
     not compile.
   - **A dropped phase is a failed phase.** A `PhaseRun` of
     [`report.rs`](../../../../crates/avl-base/src/report.rs) that drops without an end is published as failed.
     So a green phase is always a statement of the code.
   - **The lane table is data.** The Air lane table
     [`lanes.json`](../../../../../../../plugins/air/tests/integration/lanes.json) holds each lane once. The
     controller and the planner read it through `bt.json` and share `avl_affected::lanes`.
   - **The pins are read, not copied.** `avl-base` reads the image pins from
     [`versions.env`](../../../../provision/versions.env) at compile time.
   - **The worker manager owns the path of a pinned tool.** `PinnedTools` resolves Tart, the Docker CLI and Apple
     `container`, and the shared settings never hold the path.

6. **These contracts stay compatible, and a test pins each one.**
   - The lane protocol NDJSON between the lane and the recorder. The golden transcript
     [`lane-transcript.ndjson`](../../../../crates/avl-trace/testdata/lane-transcript.ndjson) pins it. See
     [the lane protocol](../../../../spec/scenario-trace.spec.md#the-lane-protocol).
   - The OTLP files of a bundle, `bundle.json`, the video index, the snapshot trees and the viewer URLs, with the
     docs site at the far end. The golden bundles in `crates/avl-trace/testdata` pin them.
   - The `air-trace serve` API and its SSE stream. The transcript
     [`serve-transcript.json`](../../../../crates/avl-trace/testdata/serve-transcript.json) pins both sides. See
     [the server](../../../../spec/scenario-trace.spec.md#the-server).
   - The runtime descriptor of the daemon and its `schemaVersion`, with
     [`air_ui_daemon_runtime.bzl`](../../../../../../../plugins/air/tests/integration/air_ui_daemon_runtime.bzl) at the
     far end.
   - The `vm` envelope, the report, the aggregates and `--stream`, with agents and their scripts at the far end. The
     shapes stay, and the bytes can change.
   - `bt --json` and the refusal line of `bt`.
   - The exit statuses of the guest agent and the `usage` envelope at 64.

   The state files of the host are no contract. The
   [state decision](../2026-09-28-the-controller-reads-only-what-it-wrote/design.md) states why.

7. **A shipped binary is always optimized.** `avl_binary` binds `optimized_binary` of
   [`defs.bzl`](../../../../../../build/rust-tools/defs.bzl). It builds with `compilation_mode=opt` and gives exactly
   one file. The wrapper, `$(rlocationpath)` in `ui_lane_ide.bzl` and the `cquery` of the controller each need one
   output file.

8. **The Linux guest binaries are static musl binaries.** The `-linux-arm64` and `-linux-x86_64` labels build for the
   musl targets, so they run on the public Ubuntu base without its C library.

9. **A cross label is resolved by `cquery`, never through `out/bazel-bin`.** `out/bazel-bin` points at one
   configuration at a time, and `cquery` knows the configuration of each label. `agent_label` and
   `cquery_output_file` of the controller resolve the guest agent so. `resolve_guest_agent_binary` of
   [`lib.sh`](../../../../provision/scripts/lib.sh) does the same for the image pipeline.

10. **A guest self-check is a verb of the guest agent.** `provision-image` and `validate-image` build and prove the
    macOS golden image. `validate-guest` proves a Linux worker. A verb names what it looked at and what it found, and
    hermetic tests reach each refusal on any host.

11. **The exit statuses of the guest agent are a closed set.** `AgentExit` of
    [`supervisor.rs`](../../../../crates/avl-wire/src/supervisor.rs) declares the set. A check that fails answers 70,
    and an argument list that the verb cannot act on answers 64. The controller reads a 64 as an agent older than
    itself. That reading is sound only while every failure inside the agent answers 70. So the class of a failure is
    the code of the envelope, never a status. See
    [supervision and evidence](../../../../spec/lane-controller.spec.md#supervision-and-evidence).

12. **The image contains no checkout, no Bazel and no toolchain.** The agent binary is an input of the image build.
    [`air-macos.pkr.hcl`](../../../../provision/air-macos.pkr.hcl) uploads it, runs the two verbs and removes it
    before `seal-public-image.sh`. So `image build` and `image validate` need a working `./bazel.cmd` in the
    checkout.

13. **`AIR_VM_GUEST_AGENT_SOURCE` is the one override of the agent binary.** The workers and the image build read the
    same variable. Two names would let an operator drive the workers with one binary and build the image with
    another.

14. **The publishability gates stay shell.** `sanitize-public-image.sh`, `seal-public-image.sh` and
    `audit-public-image.sh` argue path by path against the filesystem of the image. Their reviewers argue in shell,
    and a port moves the argument away from them.

15. **BT does not write the envelope of the controller.** With `--json`, stdout carries one payload. A refusal is one
    bare line on stderr, at an exit code of `bt`. `bt.cmd` builds on every call and caches nothing, because the
    sources have no honest cache key. `AIR_BT_BIN` names a built binary instead. The
    [BT README](../../../../../bt/README.md) states the rest.

## Alternatives rejected

- **Fastbuild binaries for `bazel run`.** They are ten times slower or worse in the parts that a lane times: the
  stills, the X11 frame copies and the zip deflate.
- **ratatui for the dashboard.** Its inline viewport has a fixed height, and the live region of the dashboard grows.

## Consequences

- Bazel and cargo verify the workspace. The [README](../../../../README.md#verification) lists the commands.
- A Mac compiles no Linux-only code. Only the cross builds of the guest binaries and `:clippy-linux-*` check it.
- Every crate except `avl-guest` builds and tests on Windows. A Windows host runs `vm` and `air-trace` natively and
  drives the Docker backend only. The working note
  [A Windows host runs the controller natively](../../../../docs/a-windows-host-runs-the-controller-natively-plan.md) holds that work.
- `rust-toolchain` pins the version of the Bazel toolchain, and `rust-version` in `Cargo.toml` names it. So clippy's
  `incompatible_msrv` flags a newer standard library API under a newer local cargo.
- A new crate follows the [crate map](../../../../README.md#crates) and the crate decision.
