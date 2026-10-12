---
topic: testing
---

# 59. The UI-lane tooling is Rust

Date: 2026-09-26

## Status

Accepted. Supersedes [ADR 0105](0105-the-ui-lane-controller-is-go.md),
[ADR 0107](../../../../../plugins/air/docs/decisions/0107-bt-is-the-go-binary.md) and [ADR 0108](0108-the-guest-half-of-the-image-pipeline-is-go.md) as the
record of the language. Their decisions that do not depend on the language stay in force, and
[What stays from 0105, 0107 and 0108](#what-stays-from-0105-0107-and-0108) names them.

Amended by [ADR 0065](0065-the-controller-reads-only-what-it-wrote.md): the controller and the guest agent read
only what they wrote, so the two compatibility rows of [Contracts kept](#contracts-kept) are struck.

Amended by [ADR 0186](0186-a-windows-host-runs-the-controller-natively.md): a Windows host runs the controller
natively, so the Windows paragraphs of [Platforms](#platforms) are superseded.

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): the workspace has 13 crates under the crate
rule of that ADR. The refusal is the type of the shared `refusal` crate. So the crate table, the point "One refusal
type" and the test layout record the state at the port. Two rows of [Crates replace hand-written
code](#crates-replace-hand-written-code) do too.

Describes the cargo workspace at `//plugins/air/tests/integration/vm-lane` and its binaries: `vm` behind
`plugins/air/scripts/vm.cmd`, `bt` behind `bt.cmd`, `air-trace` behind `trace.cmd`, the recorder
`air-trace-record` that a lane starts, and the guest agent `vm-guest-agent`. How the lane works is
[the VM guide](../vm-ui-tests.md). The crate map with each crate's concern is the vm-lane
[`README.md`](../../README.md).

## Context

The tooling was the Go module `jetbrains.com/air/vmlane`, built by rules_go. ADR 0105 records why it replaced the
TypeScript, and ADR 0107 and ADR 0108 record how `BT` and the guest half of the image pipeline followed.

Much of that Go existed to be byte-identical to the TypeScript it replaced. It emulated JavaScript's `Number()`,
`toFixed` and UTF-16 offsets, it worked around `json.Marshal`'s HTML escaping, and it pinned digests that the
TypeScript had computed. The parity gates of 0105 and 0107 used that code, and they were consumed before the
TypeScript was deleted. After the deletion nothing was left to be identical to, but the emulation and its tests
stayed.

The stdlib-first rule also left a lot of hand-written infrastructure: an argv parser, an HTTP router with Range,
ETag and SSE, WebSocket framing, a JUnit scanner, atomic file publication and terminal control.

On 2026-09-26 the user decided to move all of vm-lane to Rust, with these rules:

- **Parity is not a goal.** A well-tested crate is preferred over hand-written code. A module is redesigned where
  Rust does it better. The Go tells a porter what the tool does, not how to write it.
- **Only real cross-program contracts stay compatible.** A contract that changes, changes on both ends in the same
  commit.

## Decision

**The UI-lane tooling is one Rust implementation.** The crates were built beside the Go, one per commit, and each
crate stayed `manual` until it was ported. The wrappers switched and the Go was deleted in one commit. So there was
never a period with two live implementations, which is the property 0105 arranged its port around.

### One cargo workspace, built by Bazel

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): each shipped binary has a process test
`tests/cli.rs`, so its crate has a second test target. The tests of a module live in `<module>/tests.rs`.

- The workspace is `vm-lane/Cargo.toml` with `Cargo.lock`, `.cargo/config.toml` and `rust-toolchain`. The crates
  are `vm-lane/crates/<crate>/`. The directory name is the package name and the Bazel target name, because
  rules_rs `all_crate_deps` expects that.
- `avl.MODULE.bazel` reads the workspace with rules_rs `crate.from_cargo` into the repository `@avl`. The name is
  short on purpose, because long repository names break the Windows build on path length.
- `avl_crate` in `avl.bzl` derives the library, the test and the binaries of a crate from its `Cargo.toml`. The
  `BUILD.bazel` file does not repeat the dependencies.
- The test of a crate runs through `rust_test_junit`, which writes one JUnit case per test. BT counts cases, and a
  plain `rust_test` reports one case per target. This is the Rust half of the false green that `vm_go_test`
  closed for Go.
- Unit tests live in `src`, next to the code. There is no `tests/` directory, so each crate has one test target.
- Every feature of a third-party crate is set in `[workspace.dependencies]` and nowhere else. rules_rs resolves
  one feature set per crate for the whole workspace, so a feature set in a member would be a second answer.
- `[workspace.lints]` is the lint policy of every crate. Bazel applies it through `generate_lint_config` in
  `avl.MODULE.bazel` and `rust_lints_as_errors` of `community/build/rust-tools/defs.bzl`, which makes each crate's
  `<crate>-clippy` test fail on a warning.

### Crates by concern, not by Go package

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): the table records the 17 crates of the
port. The workspace has 13 crates now, and the vm-lane `README.md` holds the crate map.

| crate | concern | binary |
|---|---|---|
| `avl-wire` | the documents that cross a boundary: the run supervisor and the guest verbs, staging, the Bazel runtime descriptor, the uiDaemon protocol, the progress stream, the run report and its aggregates, the JUnit reader | |
| `avl-trace` | the scenario traces: the lane protocol, OTLP, the bundle, the bridge tree, the viewer URLs, the packer, bundle discovery | |
| `avl-base` | refusals and exit codes, the envelope and the reporter, settings, phase timing, ids, the journal and history, portable file publication | |
| `avl-bt` | selector resolution, the lane table, affected suites, the Bazel test run, BEP and `test.xml`, the digest, `--json` | `bt` |
| `avl-guest` | the guest agent: the run supervisor, the runtime stager, the provisioning and validation verbs, `trace-pack-ready`, the `relay` verb ([ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md)) | `vm-guest-agent` |
| `avl-record` | the recorder a lane starts: the protocol state machine, the bundle writer, snapshots, X11 and IDE capture, WebP stills, the ffmpeg video | `air-trace-record` |
| `avl-trace-serve` | `air-trace serve` and `air-trace plan` | |
| `air-trace` | the dispatcher of `pack`, `serve` and `plan` | `air-trace` |
| `avl-console` | the live dashboard of `run`, `shard` and `flake` | |
| `avl-host-sys` | subprocesses, the guest exec channel and the byte stream of its child, locks, private files and the pull gate, the read-only share set, the Linux guest's packages, the interrupt service, the operation context | |
| `avl-report` | the content digests, report assembly, the shard merge ladder, the flake arithmetic | |
| `avl-worker` | the Tart and Parallels backends, the readiness gates and the pool, leases | |
| `avl-lane` | the controller's lane resolution, `suites`, the host Bazel build, the guest launch environment, the observation commands | |
| `avl-daemon` | `run`, `shard`, `flake` and `daemon`, and the leased-run driver they share | |
| `avl-vm` | the controller's entry point: usage, global arguments and dispatch | `vm` |
| `avl-testkit` | test helpers: repository paths under cargo and Bazel, golden files, fake executables, the fake `tart` and `prlctl` | |
| `avl-host-testkit` | the controller suites' shared host fixtures, a dev-dependency only: fake guest channels, the pool over the fake `tart`, a fake host `git`, the pinned-Tart Bazel, the parked-daemon probe | |

A crate owns a concern. Bundle discovery moved from the server into `avl-trace`, so the controller can name a
pulled trace zip without the HTTP stack. `host/digest`, `host/report` and `host/aggregate` became one crate,
because they change together. The runtime-descriptor half of `host/digest` later moved to `avl_wire::runtime`,
beside the other contracts, and `avl-report` kept the identities.

`bt`, the reports, the guest agent and the recorder are synchronous. The controller crates and `air-trace serve`
use tokio. So `bt` and the guest agent carry no async runtime.

### Crates replace hand-written code

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): no crate links reqwest. The recorder and
the viewer probe send their requests over a `TcpStream` of the standard library, and the controller reaches the daemon
with hyper. No file is published through a tempfile persist. `avl_base::fs` publishes through `fs::rename` and
`fs::hard_link`.

| concern | crate | Go counterpart |
|---|---|---|
| command lines | clap (derive, env) | the hand-written argv parsers and their option tables |
| JSON | serde, serde_json | `encoding/json` with hand-written field tables, escape and number helpers, null-or-absent mirrors |
| JUnit | quick-xml, for attributes and entities | the hand-written JUnit scanner |
| zips | zip | `archive/zip` |
| WebP stills | image-webp, png | webp-go-pure |
| X11 capture | x11rb with MIT-SHM | xgb |
| file watching | notify | fsnotify |
| HTTP server | axum, tower-http | the hand-written router, Range and ETag serving, SSE loop and idle plumbing |
| HTTP client | reqwest, without TLS | `net/http` |
| atomic files and locks | tempfile, `std::fs::File::lock` | most of `host/fsatomic` and `host/lock` |
| processes and signals | nix, signal-hook, `process_group(0)` | the per-spawn signal forwarding |
| terminal | console, anstyle, terminal-colorsaurus | Bubble Tea, Lip Gloss, `x/ansi` and the terminal ioctls |

The dashboard uses `console`, not ratatui. The inline viewport of ratatui has a fixed height and renders into a
cell buffer. The live region grows with the running workers, ends as the taller verdict card, and puts OSC 8 trace
links into the scrollback. Its colours are
[ADR 0063](0063-the-dashboards-palette-is-derived-from-the-terminals-colours.md).

The JUnit reader keeps its own state machine for truncated and nested documents. Only ranges into stored zip
entries are still served by hand. No dependency links a C library. The `-sys` crates in `Cargo.lock` are
operating system bindings, and the WebAssembly bindings that reqwest names for a target this workspace does not
build.

### Redesigned where Rust does it better

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): the refusal is `refusal::Refusal<Exit>` of
the shared `refusal` crate, so the first point records the state at the port. `avl-base` keeps the exit vocabulary
`Exit` and the constructors of `RefusalExt`.

- **One refusal type.** `avl_base::Refusal` carries a code, a message, an exit status and details. It travels
  as the error itself: every command answers `Result<Outcome, Refusal>`, so the reporter never searches a
  wrapper for it. The sysexits codes that agents read did not change.
- **One interrupt service.** The `vm` binary starts it once. SIGINT or SIGTERM cancels the root token and kills
  every registered process group, and a second Ctrl-C leaves at once. It replaces the per-spawn signal forwarding
  and `lease.WatchInterrupts`.
- **One operation context.** The cancellation token, the phase handle and the run id travel as one value. Go
  carried them in `context.Context`.
- **A dropped phase is a failed phase.** A phase that ends without an outcome, for example on an early `?`, is
  published as failed and never as green.
- **The lane table is data.** `crates/avl-bt/data/lanes.json` holds the lanes, the excluded test libraries and the
  flow directories. The trace planner and `avl-lane` derive their UI lanes from it, so each lane is spelled once.
- **The pins are read, not copied.** The Linux base image, the golden VM and the Node pins come from `versions.env`
  at compile time. The Go constant that duplicated the base image has no Rust counterpart.
- **The Tart path is the worker manager's.** It is not written back into the shared settings.

### Contracts kept

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): the transcript
`crates/avl-trace/testdata/serve-transcript.json` pins the `air-trace serve` row on both sides. The server test writes
it, and `src/trace/runJournal.test.ts` of the docs site reads it. The tests that the rows name in `avl-daemon` and
`avl-trace-serve` moved with their modules into `avl-vm` and `air-trace`.

| contract | far end | pinned by |
|---|---|---|
| the lane protocol NDJSON | Kotlin `AirTraceWire`, `AirTraceRecorderProcess` | the golden transcript `crates/avl-trace/testdata/lane-transcript.ndjson`, whose every line re-encodes byte for byte; `AirTraceTranscriptTest` reads the same file |
| OTLP `spans.jsonl` and `logs.jsonl`, `bundle.json`, `video.index.json`, snapshot trees, viewer URLs, the `X-Air-Trace` header | the docs site: `src/trace/format.ts`, `src/router.ts`, `src/routeTable.ts` | the golden bundles in `crates/avl-trace/testdata`, read by `src/trace/goldenBundles.ts` |
| the `air-trace serve` API and its SSE stream | the docs site: `src/trace/runJournal.ts` and the run pages | the `avl-trace-serve` tests, against the paths, shapes, headers and first frame the site reads |
| the uiDaemon routes, `RunEvent` and the state file | Kotlin `AirUiDaemonProtocol`, `AirUiDaemonServer` | `avl-wire` tests and `AirUiDaemonProtocolTest` |
| the daemon runtime descriptor and its `schemaVersion` | Starlark `air_ui_daemon_runtime.bzl` | the `avl_wire::runtime` tests, a refusal per way the descriptor can be wrong, and an `avl-daemon` test that reads the rule's `_SCHEMA_VERSION` |
| the `vm` envelope, the report, the aggregates, `--stream` | agents and their scripts | the shapes; Go byte quirks may change |
| `bt --json` and the refusal line | agents and their scripts | `avl-bt` tests (ADR 0107's output contract) |
| the guest agent's exit statuses and the `usage` envelope at 64 | the controller, which reads a 64 without an envelope as an older agent | `avl-wire` and `avl-guest` tests (ADR 0108's closed set) |
| ~~host state files: leases, locks, workers, the Tart seal, journals, history~~ | ~~the next controller run, which reads what the Go controller wrote~~ | ~~`schemaVersion: 1` shapes, the legacy runtime root name, RFC 3339 times~~ Struck by [ADR 0065](0065-the-controller-reads-only-what-it-wrote.md). The journals stay a contract with the viewer, under ADR 0164. |
| ~~the Go agent's `RunState` and process identity~~ | ~~a warm worker that still runs the Go agent until the Rust one is installed~~ | ~~`avl-wire` and `avl-guest` tests~~ Struck by [ADR 0065](0065-the-controller-reads-only-what-it-wrote.md): the Rust agent is installed before any supervisor call. |

The golden bundles were regenerated by the Rust encoder at the cutover, and the replay test compares them byte for
byte. The Go-written bundles were compared as values and pixels until then.

### Dropped

These behaviours had no reader after the TypeScript was deleted. They were not ported, and neither were the tests
that pinned them.

- **JavaScript number emulation.** `Number()` coercion of JUnit attributes, `toFixed` rounding, JavaScript
  rounding of JUnit milliseconds, UTF-16 code-unit offsets and bounds, and surrogate pairing. A number at an exact
  `.5` tie can now round the other way in prose.
- **HTML-escape workarounds.** `SetEscapeHTML(false)`, the `CompactJSON` and `IndentedJSON` pair and its
  `unencodable:` text. serde_json never escapes HTML.
- **Integral floats without `.0`.** A float field can now read `249364.0` where Go wrote `249364`. Every reader
  parses JSON.
- **Parity dumps.** `AIR_JUNIT_CORPUS`, `AIR_JUNIT_CORPUS_OUT`, `AIR_CONFIG_PARITY_OUT`, `AIR_BT_PARITY_OUT`,
  `AIR_DIGEST_PARITY_OUT` and `AIR_DIGEST_PARITY_TREE` went with their tests.
- **Digest pins from the deleted TypeScript.** Digests only have to agree between this controller and itself. The
  byte pins are replaced by composition and sensitivity tests. The digest cache now keys on integral nanosecond
  modification times, because a float key missed about one file in nine.
- **The key order of the uiDaemon `/run` body.** kotlinx reads it by key.
- **Guards of hand-written tables.** Tests of Go map order, of option-key maps, of vocabulary lists beside their
  constants, of a slice-aliasing hazard and of the `recover()` boundary. A Rust enum, a clap type or an owned
  value makes each of those mistakes impossible to write.
- **Dead Go code.** `IdeLaunchArgFile`, `IdeLaunchPaths`, `CombinedDigest`, `FieldSpec`, `CheckBody` and
  `ValidateBody` had no caller.
- **`run --help` as an argument of the command.** clap answers each subcommand's help.
- **Go build machinery.** `vm_go_test`, `GO_TEST_WRAP_TESTV` and the `race` select in `defs.bzl`.

- **Changed refusals in `run`, `daemon` and the command line.** A transport failure to the guest daemon is refused
  as `daemon_http_failed` with exit 69, where Go surfaced a raw error as `internal_error` with exit 1. A stage-check
  reply without `complete` is refused instead of read as "stage again". An arity error carries clap's message with
  the command's usage line, where Go had a sentence per command; the code, `usage`, and the exit, 2, are unchanged.
  An unknown `image` verb is a usage refusal before the backend is consulted.
- **An interrupt never deletes an identity.** The first Ctrl-C cancels every operation, where Go let each spawn
  finish, so the liveness probe runs outside the interrupt service and a worker or daemon operation that was
  interrupted keeps its records: `worker_interrupted` and `daemon_stop_interrupted` (exit 70) replace a deleted
  `pid.json`, suspended-state record or `daemon.json`.
- **The detached viewer's session.** `vm` starts `trace.cmd serve` through the start `serve --detach` makes, in a
  session of its own, and does not wait for it. One log, `serve.log`, holds the wrapper's build and the server.

### Platforms

Amended by [ADR 0186](0186-a-windows-host-runs-the-controller-natively.md): a Windows host runs `vm` and `air-trace`
natively and drives the Docker backend only, so the Windows paragraphs below record the state before that ADR.

**Windows is not a supported host, for the reasons 0105 gave.** The hypervisor backends are Tart and Parallels, and the
guests are macOS and Linux. The guest supervisor detaches with `setsid` and cancels a process group. There is no Windows lane
distribution, JBR or guest-agent build.

**Windows keeps `bt.cmd` and the recorder.** `bt.cmd` is a developer tool on Windows, and the recorder,
`air-trace-record`, is data of every UI lane target. So their closures are portable, and so is every crate that a
portable crate's test links. `avl_crate(portable = True)` in the crate's `BUILD.bazel` declares it. `avl-report`
is portable too, so its tests also run on the Windows host. Every other crate is incompatible with Windows, the
viewer's server and the planner (`avl-trace-serve`, `air-trace`) included. Inside a portable crate, the X11
capture is behind `cfg(target_os = "linux")`. `:bt-windows-x86_64` builds `bt` for Windows from a macOS or Linux
host, and `:clippy-windows-x86_64` checks `bt` and the recorder.

0105's rule stands: a security gate with no implementation on a platform refuses there. The private-directory and
pull gates are in `avl-host-sys`, which Windows does not build, so no Windows stub is necessary.

**A shipped binary is always optimized.** `avl_binary` in `avl.bzl` builds its binary with
`compilation_mode=opt`, for the host or one named platform. In fastbuild every crate is `-Copt-level=0`, and the
WebP stills, the X11 frame copies and the zip deflate then run ten times slower or worse inside a lane that measures
time. An `avl_binary` gives exactly one file. `bazel run --script_path` in the wrappers, `$(rlocationpath)` in
`ui_lane_ide.bzl` and the controller's `cquery` for the guest agent each need one. The labels are at the vm-lane
root: `:vm`, `:bt`, `:air-trace`, `:air-trace-record`, `:air-trace-record-linux-arm64`, `:vm-guest-agent`,
`:vm-guest-agent-linux-arm64`, `:vm-guest-agent-darwin-arm64` and `:bt-windows-x86_64`.

**The Linux guest binaries are static musl binaries.** `:vm-guest-agent-linux-arm64` and
`:air-trace-record-linux-arm64` build for `aarch64-unknown-linux-musl`, so they run on the public Ubuntu base with no dependency on its C library.
The Go builds had the same property through `pure = "on"`.

0108's trap still holds for the cross labels: never reach one through `out/bazel-bin`. The controller and
`provision/scripts/lib.sh` resolve the guest agent by label, through `cquery`.

### The toolchain stays at 1.93.1

`MODULE.bazel` declares one Rust toolchain for the whole repository, 1.93.1. `fleet/native/rust-toolchain` pins
the same version, and it is not only a cargo pin. `fleet/native/ijent/cross-compile.sh` runs cargo in a prebuilt
image that has the musl and Windows targets for 1.93.1 only, and `test.ijent.Dockerfile` copies the file. A newer
pin there makes rustup fetch a bare toolchain in the container, and the IJent build fails. The IJent images must be
rebuilt and repinned before the version can move.

So `vm-lane/rust-toolchain` pins 1.93.1, and the workspace declares `rust-version = "1.93"`. A newer local cargo
builds the workspace. Clippy's `incompatible_msrv` then refuses a standard library API that is newer than 1.93.

### What stays from 0105, 0107 and 0108

- **One implementation issues lane verdicts** (0105).
- **`BT` does not write the controller's envelope** (0107). With `--json`, stdout carries one payload. A refusal is
  one bare line on stderr at one of `bt`'s own exit codes. `bt.cmd` still rebuilds on every call and caches
  nothing, and `AIR_BT_BIN` still overrides the build.
- **A guest self-check is a verb of the guest agent** (0108). `provision-image`, `validate-image`,
  `provision-guest` and `validate-guest` are verbs of the Rust agent. The exit statuses are a closed set, and a
  check class is a code in the envelope, never a status.
- **The publishability gates stay shell** (0108).

## Consequences

- **The verification is Bazel and cargo.** There is no Go SDK step any more.

  ```bash
  ./bazel.cmd test //plugins/air/tests/integration/vm-lane/...
  (cd plugins/air/tests/integration/vm-lane && cargo test)
  ```

  Linux-only code does not compile on a macOS host. Build `:vm-guest-agent-linux-arm64` and
  `:air-trace-record-linux-arm64` to check it.
- **What the cutover measured.**

  | what | result |
  |---|---|
  | Rust test functions against Go test functions | 1414 Rust tests; the porters mapped 1275 Go tests and dropped 52, each with its reason |
  | `./bazel.cmd test //plugins/air/tests/integration/vm-lane/...` | 16 targets pass; 1414 cases pass and 2 are skipped by design |
  | `bt.cmd --lane fast` | PASS, 19227 tests over 118 targets. The first run exposed that the libtest JUnit wrapper did not shard, so every Rust target failed under the lane's forced sharding; the wrapper shards now |
  | the smoke comparison against the Go binaries, through the wrappers: `vm --help`, `vm status --json`, `vm lease --json`, `vm suites`, `vm pool status`, `trace plan`, `bt --dry-run --json` and a real `bt AirIntegrationTagTest --json` | equal exit codes and equal JSON key sets; `status` and `lease` read the state the Go controller wrote; one difference found and fixed (`vm pool status` named the wrong command in its refusal) |
  | the WebP stills | image-webp, release build: about 2 ms and 4 to 8 KB per 1280x800 test frame. The regenerated golden stills are pixel-equal to the Go ones and larger (2.8 KB became 6.5 KB for the largest); every JSON and text golden came out byte-identical |
  | `bt.cmd --help` warm, against ADR 0107's 1.6 s | 0.42 to 0.55 s |
  | the real `vm.cmd run --lane ui` on a worker | not yet run: the user runs it before the push |

- **Go leaves the build.** `go.bazel.work` loses its `./plugins/air/tests/integration/vm-lane` entry.
  `go.MODULE.bazel` loses `com_github_charmbracelet_x_ansi`, `com_github_fsnotify_fsnotify`,
  `com_github_jezek_xgb` and `com_github_seriousbug_webp_go_pure`, which only vm-lane used. idecli still uses
  Bubble Tea, Lip Gloss and `golang.org/x/sys`.
- **The Kotlin guards read `lanes.json`.** `AirIntegrationTagTest` and `AirArchitectureModelTest` read
  `crates/avl-bt/data/lanes.json` and no longer scan Go source. `AirSpecReferencesTest` resolves a `[@test]` link
  to a Rust `#[test]` or `#[tokio::test]` function. The spec links were rewritten from the porters' Go-to-Rust
  test maps.
- **The goldens moved into the crates.** The lane transcript and the golden bundles are in
  `crates/avl-trace/testdata`, and the recorder's IDE fixtures are in `crates/avl-record/testdata`. The public
  filegroup `//plugins/air/tests/integration/vm-lane/crates/avl-trace:testdata` gives them to the Kotlin contract
  tests. `UPDATE_EXPECT=1`, the variable of `expect-test`, rewrites a golden text file. Amended 2026-10-01: the test
  kit's own update variable `AVL_UPDATE_GOLDEN=1` is gone, so the workspace has one golden convention.
- **The parity apparatus of 0105 and 0107 is gone.** Its comparators under `out/go-parity/` could not run after the
  TypeScript was deleted, and now their Go half is deleted too. A future rewrite builds its own comparator.
- **How to add a crate.**
  1. Create `crates/<crate>/Cargo.toml` with the workspace keys and `[lints] workspace = true`. Name each
     dependency with `.workspace = true`.
  2. Add the crate to `members` and to `[workspace.dependencies]` in the root `Cargo.toml`. Add a new third-party
     dependency there too, with all of its features.
  3. Add `crates/<crate>/BUILD.bazel` with one `avl_crate()`. Set `portable = True` only if `bt`,
     `air-trace-record` or a portable crate's test links the crate. Give a shipped binary an `avl_binary` at the vm-lane root.
  4. Let cargo update `Cargo.lock`. A new third-party crate also changes `MODULE.bazel.lock`, so regenerate it
     just before the commit and do not merge it by hand.

## Alternatives rejected

- **A parity gate like 0105's.** The far side would be the Go, which was deleted in the same change. The contracts
  that matter are pinned by golden files and by the tests of their far ends instead.
- **Fastbuild binaries for `bazel run`.** They are ten times slower or worse in the parts a lane times.
- **A move to current stable Rust with the port.** It needs the IJent images rebuilt first. The workspace
  gains nothing from a newer compiler that is worth that dependency.
- **ratatui for the dashboard.** Its inline viewport has a fixed height, and the dashboard's live region does not.
