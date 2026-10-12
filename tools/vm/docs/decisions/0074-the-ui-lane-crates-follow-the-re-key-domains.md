---
topic: testing
---

# 74. The UI-lane crates follow the re-key domains

Date: 2026-10-02

## Status

Accepted. It records the rework of the cargo workspace `plugins/air/tests/integration/vm-lane` on 2026-10-01 and
2026-10-02. The workspace now follows the crate rule of
[ADR 0043 of the build](../../../../../build/decisions/0043-the-crate-boundaries-follow-the-re-key-domains.md) and the
[Rust Code Style](../../../../.agents/skills/rust-code-style/SKILL.md) skill. The crate map is the vm-lane
[`README.md`](../../README.md), and the operator view is [the VM guide](../vm-ui-tests.md).

It amends [ADR 0059](0059-the-ui-lane-tooling-is-rust.md). ADR 0059 keeps its old text, and a line at the head of each
amended section points here.

- **The crate table.** "Crates by concern, not by Go package" names the 17 crates of the port. The workspace has 13
  crates now.
- **The error model.** "One refusal type" names `avl_base::Refusal` as a type of its own. The refusal is now the type of
  the shared `refusal` crate, with an exit vocabulary for each tool.
- **The test conventions.** "There is no `tests/` directory" no longer holds, because each shipped binary has a process
  test. A golden is an `expect-test` snapshot, as the amendment of 2026-10-01 in its Consequences says.
- **Two rows of "Crates replace hand-written code".** No crate links reqwest. No file is published through a tempfile
  persist.
- **The serve API row of "Contracts kept".** The transcript of the routes pins the API on both sides now.
- **The Unix-only claims.** The crate docs no longer call a controller crate Unix-only. Every crate except `avl-guest`
  builds on Windows. ADR 0059 already points to ADR 0186 for the paragraphs of its Platforms section.

It also amends the file paths of [ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md) and the crate names
of [ADR 0186](0186-a-windows-host-runs-the-controller-natively.md), with a line in each.
[ADR 0183](0183-a-linux-worker-may-be-a-container.md) keeps its text. The probes of ADR 0182 and ADR 0183 are the
evidence of the raw pull below.

## Context

ADR 0059 ported the Go module with one crate for each concern of the Go packages. On 2026-10-01 an audit read every
crate of the workspace. The code was well tested, but its shape cost more than its work needed:

- **17 crates, and six had one consumer.** `avl-worker`, `avl-daemon`, `avl-lane` and `avl-console` served only `vm`.
  `avl-report` served only `vm`. `avl-trace-serve` served `air-trace`, and `vm` linked it only to probe and to start
  the viewer. So `vm` linked the HTTP server stack. `air-trace` linked `avl-lane`, and through it `avl-worker` with its
  four backends, for the lane table.
- **Two refusal types.** The controller answered `avl_base::Refusal`, and BT answered a refusal type of its own.
  `avl-affected` bridged the two, with the details as JSON text. The exit 6 meant a red lane in `vm` and an
  infrastructure failure in BT.
- **More error models.** The guest agent had four: `AgentRefusal`, `Refused`, an anyhow error in the step runner and
  `CopyError`. `air-trace` had 23 `Result<_, String>` in `serve` and `plan`, and a literal exit in each subcommand.
- **Branches on a refusal code.** The controller branched in 12 places on a refusal code. Once it read `exitCode` back
  out of the details JSON.
- **Spawns without a timeout.** 80 production spawn sites had no timeout, and 11 had one. A hung engine or guest then
  held the lease and the terminal for ever.
- **Another width.** The workspace had no `rustfmt.toml`, so rustfmt used its default width of 100 columns. The
  repository width is 140.
- **Two golden conventions.** `avl_testkit::golden` rewrote a golden under `AVL_UPDATE_GOLDEN=1`, and expect-test
  rewrote one under `UPDATE_EXPECT=1`.
- **A fixture per case.** Thirteen tests of `avl-vm` built a new fixture for each of their cases.
- **Three unread server branches.** `GET /__air/zip`, the `contentBase64` input of `POST /__air/plan` and the offset of
  a `bundle-append` frame had no reader on the docs site.
- **Duplicated mechanisms.** The worker lifecycle had two dispatch mechanisms, an enum and an `async_trait`. The Tart,
  Docker and Lima backends each had their own resolver of a pinned tool. A pull encoded each file as base64 in the
  guest, and the controller decoded it.
- **A hand-written help.** The help of `vm` was a text of about 100 lines. `prescan` parsed the command line a second
  time by hand, in about 160 lines, to find the output form of a refused parse.

The audit also found defects. Seven commits of 2026-10-01 fix them, and their messages state each defect.

## Measurements

The values were read on 2026-10-01 and 2026-10-02 on one macOS arm64 host. Each value comes from git or from the
message of the commit that it names. The text names the exceptions.

**The crates.** The workspace had 17 crates before the commit "refactor vm: the sources use the repository width of
140 columns". The commit "refactor vm: avl-trace separates the bundle core from the pack and viewer tools" added
`avl-trace-tools`. The folds then left 13 crates at the commit "refactor vm: the recorder is a binary crate".

**The crate closures.** `<bin>_closure_test` compares the crates that a shipped binary links with its `closure.txt`.
The file lists the rustc crate names for x86_64 Linux, without the proc macros and the host-only crates. "Before" is
the file of the first commit that has one, "refactor vm: avl.bzl binds the shared Rust tool macro core". "After" is
the file at this record.

| Binary | Before | After | Left the closure | Joined the closure |
|---|---:|---:|---|---|
| `vm` | 164 | 118 | `avl_console`, `avl_daemon`, `avl_lane`, `avl_worker`, `avl_trace_serve`, the server stack, the HTTP client stack, `crossterm` with three crates, `zopfli`, `bumpalo` | `avl_trace_tools`, `distpath`, `fscopy`, `refusal` |
| `air-trace` | 151 | 127 | `avl_lane`, `avl_worker`, `avl_trace_serve`, the HTTP client stack, `base64`, `zopfli`, `bumpalo` | `avl_trace_tools`, `distpath`, `fscopy`, `refusal` |
| `air-trace-record` | 137 | 47 | 90 crates: the HTTP client stack with hyper and tokio, clap with its terminal crates, `wait_timeout`, `which`, the zip and discovery crates, and its own library `avl_record` | none |
| `vm-guest-agent` | 69 | 61 | `bt_junit`, `quick_xml`, the four regex crates, `anyhow`, `thiserror`, `log`, `zopfli`, `bumpalo` | `avl_trace_tools`, `fscopy`, `refusal` |

- The server stack is `axum`, `axum_core`, `http_range_header`, `inotify`, `inotify_sys`, `matchit`, `mime`,
  `mime_guess`, `notify`, `notify_types`, `ryu`, `serde_urlencoded`, `sync_wrapper`, `tower`, `tower_http`,
  `tower_layer` and `unicase`.
- The HTTP client stack is `reqwest`, `url`, `ipnet` and the 19 crates of `idna` and ICU.
- `crossterm` left with `derive_more`, `signal_hook` and `signal_hook_mio`. `vm` takes the terminal width from
  `console`.
- `zopfli` and `bumpalo` left when zip deflated through flate2 alone. `air-trace pack` of both testdata trees writes
  the same bytes as before.
- Two BT commits added `refusal` and `distpath` to the closures of `vm` and `air-trace`. They are "refactor bt: the
  refusal is a shared crate" and "refactor bt: the slash-path helpers come from distpath".

**The test suite.** The table gives the result of `./bazel.cmd test //plugins/air/tests/integration/vm-lane/...` from
the root, after each `refactor vm:` commit that ends a step of the rework.

| After the commit | Targets | Cases passed | Cases skipped |
|---|---:|---:|---:|
| lint exceptions are expectations, and the platform notes are current | 38 | 1 470 | not stated |
| a viewer probe failure is reported, not read as "no viewer" | not stated | 1 478 | not stated |
| BT no longer re-exports optimized_binary for avl.bzl | 43 | 1 483 | not stated |
| the viewer probe needs no HTTP client crate | 46 | 1 497 | 1 |
| the recorder is a binary crate | 35 | 1 491 | 1 |
| clap renders the vm help | 38 | 1 505 | 1 |
| the zip route judges a missing file by its real ancestor | 38 | 1 527 | 1 |
| the server answers only what the site reads | 38 | 1 532 | 1 |
| the push opens an extra relay only for enough bytes | 38 | 1 534 | 1 |

- The messages of the first four commits name no count. Those counts come from the gate runs of their steps.
- The fold of five crates removed their ten targets. The recorder lost the separate clippy target of its process
  test. Each removed clippy target was one case. No test case was lost, because each test moved with its module.
- The three targets of "every binary runs through one entry" are process tests.
- The workspace `cargo test` passed 1 511 tests with 1 ignored at "the server answers only what the site reads".

**The test time.** "refactor vm: the suites build a fixture per test" gave 12 of the 13 tests one fixture for all of
their cases. Before each case a test forgets the calls of the last case. The values come from the JUnit XML of uncached
root Bazel runs of `avl-vm-test`:

| | Before | After |
|---|---:|---:|
| wall time | 71.7 s | 61.1 s |
| the slowest test | 31.77 s | 8.57 s |
| the second | 18.81 s | 8.50 s |
| the third | 13.57 s | 8.50 s |
| the fourth | 11.49 s | 8.45 s |
| the fifth | 8.93 s | 8.32 s |

The cost of a fixture is the first exec of its fake executables. macOS scans a new executable file at its first exec,
and the scans of all test threads wait for each other. The first exec of a new two-line script took 0.13 to 0.33 s,
and the second took 0 s. Each fixture wrote new copies of the fake `tart`, `git` and `docker`.

**The sharding of the folded test.** At "refactor vm: vm owns the workers", `avl-vm-test` held 533 cases. It ran in
74 s under the whole suite. With `--test_sharding_strategy=forced=2`, the wrapper split it into 267 and 266 cases, 533
distinct. Each shard took 73 s. The message of that commit names the split and the 74 s. The 73 s comes from the gate
run of its step.

**The spawn sites.** Before "refactor vm: every spawn names its timeout", 80 production sites built
`SpawnOptions::default()`, which has no timeout, and 11 named one. After it, 88 production sites name a timeout. 81 name
a constant, 6 take a parameter that each caller names, and 1 takes its constant bounded by the budget left.

**The drivers.** Two commits split the drivers over 150 lines around pure decisions. The values are lines of the
driver function.

| Driver | Before | After |
|---|---:|---:|
| `iterate` | 231 | 111 |
| flake measure | 181 | 96 |
| `shard` | 155 | 99 |
| `execute_run` | 153 | 96 |
| the guest image validation | 181 | 123 |
| the guest supervisor | 172 | 112 |

**The push.** The commit "refactor vm: the push opens an extra relay only for enough bytes" measured the push on the
fakes. No lane could run on the shared engine. Each connection is a fake `tart exec -i ... relay` that bash bridges to
the fake daemon. The values are the median of 10 pushes, at a load average of 31 to 68:

| Hot jars | Bytes | Uploads at once | Relays | Median |
|---|---:|---:|---:|---:|
| 16 of 64 KiB | 1 MiB | 1 | 1 | 88 ms |
| 8 of 2 MiB | 16 MiB | 2 | 2 | 112 ms |
| 8 of 16 MiB | 128 MiB | 4 | 4 | 182 ms |

The same shapes ran twice under three bounds, at a load average of 22 to 39. Each cell gives the relays, then the
median of 10 pushes in each run:

| Hot jars | Serial | Fixed bound of 4 | Bound by bytes |
|---|---|---|---|
| 16 of 64 KiB | 1 relay, 41 / 33 ms | 4 relays, 40 / 65 ms | 1 relay, 49 / 70 ms |
| 8 of 2 MiB | 1 relay, 101 / 31 ms | 4 relays, 75 / 67 ms | 2 relays, 58 / 85 ms |
| 8 of 16 MiB | 1 relay, 265 / 118 ms | 4 relays, 93 / 158 ms | 4 relays, 111 / 90 ms |

- Under this load the medians move by more than the bounds differ. So the times give no ranking.
- The relay counts are exact. Against the fixed bound of 4, a push of up to 8 MiB opens three relays fewer. A push of
  16 MiB opens two fewer.
- On Tart each relay is a spawn of 0.65 s, which ADR 0182 measured.

**The raw pull.** The proof that two exec channels are byte-clean is older than this record:

- ADR 0182 sent 300 000 bytes through `tart exec -i air-linux-1 cat`, and they came back md5-identical.
- ADR 0183 sent `ABC` through `docker exec -i <c> cat` with no tty, and `ABC` came back.

Each pull now carries its own proof. The guest verb `read-file` names the size and the SHA-256 of the bytes that it
wrote. The controller compares them with the bytes that arrived. Five new cases test it: two in `avl-guest`, one in
`avl-wire` and two in `avl-vm`.

**The format.** "refactor vm: the sources use the repository width of 140 columns" changed 341 Rust files in 3 077
hunks of its default diff. It changed no logic.

## Decision

1. **A crate is a re-key domain.** A change of a crate changes the bytes of each binary that links it. What a new byte
   costs depends on the binary:
   - The recorder is data of every UI-lane test target. A change of its closure changes the runfiles of each target,
     so Bazel runs each target again.
   - The controller installs the guest agent in every worker. A change of its bytes makes each worker install it again
     once.
   - The wrappers build `vm` and `air-trace` on every call. A change of their closures costs build time.

   So a crate exists when two binaries share it, or when it is the frozen-bytes engine of one binary with its own
   goldens. Code that one binary uses is a module of that binary. The workspace has 13 crates:

   | Crate | What it is | Binaries that link it |
   |---|---|---|
   | `avl-vm` | the `vm` binary, with the modules `console`, `daemon`, `lane` and `worker` | `vm` |
   | `air-trace` | the `air-trace` binary, with the modules `serve` and `plan` | `air-trace` |
   | `avl-record` | the `air-trace-record` binary, a binary crate | `air-trace-record` |
   | `avl-guest` | the `vm-guest-agent` binary | `vm-guest-agent` |
   | `avl-wire` | the documents that cross a boundary | `vm`, `air-trace`, `vm-guest-agent` |
   | `avl-trace` | the scenario traces that the recorder writes | all four |
   | `avl-trace-tools` | the trace packer, the bundle discovery and the viewer URLs | `vm`, `air-trace`, `vm-guest-agent` |
   | `avl-base` | the exits, the envelope, the settings and the journal | `vm`, `air-trace` |
   | `avl-host-sys` | the subprocesses, the exec channel, the locks and the detached viewer start | `vm`, `air-trace` |
   | `avl-affected` | the suites that a path reaches, the UI lanes and the bridge from BT | `vm`, `air-trace` |
   | `avl-report` | the frozen-bytes engine of the report that an agent reads | `vm` |
   | `avl-testkit`, `avl-host-testkit` | the test helpers. Only `[dev-dependencies]` name them | none |

   The workspace also links `bt-core`, `bt-junit` and `refusal` of BT, and `distpath` and `fscopy` of the dev-dist
   tools. `avl.bzl` maps each one to its label in the community module, so Bazel builds it once.
2. **Each shipped binary pins its closure.** `vm`, `air-trace`, `air-trace-record` and `vm-guest-agent` each have a
   `closure.txt` and a `<bin>_closure_test`. So a new crate in a closure is a diff that a reviewer sees.
3. **The audience of a binary picks its error model and its command line.** The
   [Rust Code Style](../../../../.agents/skills/rust-code-style/SKILL.md) skill states the rule for every Rust
   tool workspace.
   - The shared crate `community/tools/bt/crates/refusal` holds `Refusal`, with the exit vocabulary as a type
     parameter. The controller crates use `avl_base::Exit`, the `sysexits.h` numbers. `air-trace` uses its own closed
     `Exit`, and the guest agent uses the closed `AgentExit` of ADR 0108. BT keeps its `u8` exits.
   - A conversion maps an exit by its meaning, never by its number. So `bt_infra` leaves the controller by 65, and 6
     stays a red lane.
   - Inside the runner and the backends, a typed value carries each decision: `ProcError`, `ParityError`, and `None`
     for a held lock. A refusal is built at the command edge. `From<ProcError> for Refusal` keeps each `?` as it was.
   - Each binary runs through one `run` function. It takes the arguments after the program name and the streams that
     the binary uses, and it answers the exit code. `main.rs` only passes the values of the process.
   - `vm`, `air-trace` and the guest agent read their command line with clap derive. clap renders the help of `vm`, so
     the hand-written text and `prescan` are gone. A refused parse is read a second time by clap, to find the output
     form.
   - The lane JVM starts the recorder with no argument. So the recorder has no command line, and it refuses any
     argument with one `ERROR:` line and exit 2.
4. **One engine dispatch.** Each lifecycle operation is one match on the `Machine`, in the lifecycle module of the
   worker module. Each arm calls the body in the file of its backend. The `Lifecycle` trait is gone.
5. **Every spawn names its timeout.** `SpawnOptions` has no default, and each site states its timeout with a reason.
   An expired timeout kills the process group and answers `ProcError::TimedOut`, at exit 75. Three spawn shapes keep
   no timeout, because something else ends them: an interactive session, the relay stream and a detached session.
   Every wait loop is `Poll` of `avl-host-sys`, with a budget and a backoff.
6. **One pinned-tool resolver.** `PinnedTools` resolves every pinned tool of a pool with one `cquery` and one
   `info output_base`. Each backend keeps its own refusal codes.
7. **One golden convention.** A golden is an `expect-test` snapshot: an `expect!` in the test, or a file that
   `expect_file!` names. `UPDATE_EXPECT=1` rewrites it.
   - A golden that another side reads stays a plain file: the lane transcript, the golden bundles and the transcript
     of the server routes. The replay test of the recorder and the server test hold their output to these files with
     `expect_file!`. The lane transcript is an input of the replay.
   - A WebP still is binary, so the replay compares its bytes and has no update mode.
8. **A fixture per test.** A test builds one fixture and shares it across its cases. A pure decision has tests of its
   own, without a fixture.
9. **The transfers move only the bytes that they need.**
   - A pull on Tart and Docker moves the raw bytes through the guest verb `read-file`. A pull whose bytes differ from
     the receipt is `pull_digest_mismatch`, at exit 65, and nothing is published. A pull on Parallels stays base64.
   - The stage manifest arrives on the standard input of `stage`. A full stage runs one guest exec fewer.
   - The hot-jar push opens one connection for each 8 MiB begun, at least 1 and at most 4. A push of at most 8 MiB
     opens no relay more.
10. **The server answers only what the site reads.** `GET /__air/zip`, the `contentBase64` input and the JSON
    `fileName` of `POST /__air/plan`, and the offset of `bundle-append` are gone. The server test writes
    `crates/avl-trace/testdata/serve-transcript.json` from its own answers, and `src/trace/runJournal.test.ts` of the
    docs site reads the same file. [The scenario-trace spec](../../spec/scenario-trace.spec.md) states the contract.
11. **The workspace follows the shared configuration of `community/build/rust-tools`.**
    - `sync.mjs` writes the lint tables, `rustfmt.toml` and `clippy.toml` of the workspace. The width is 140 columns.
    - A site exception is `#[expect]`, never `#[allow]`.
    - No crate calls a banned method. `avl_host_sys::fs::real_path` stands on `fscopy::resolve_links`, and a file is
      published through `fs::rename` or `fs::hard_link`.
    - `avl.bzl` is a thin binding of `rust_tool_crate`, and the core declares the closure, clippy and Windows targets.

## Alternatives rejected

### Shard the folded `vm` test

Rejected. With two shards, each shard took 73 s against 74 s for the whole test. The slow daemon tests set the time,
and libtest already runs the cases of one target in parallel. So `avl-vm-test` stays one `medium` target. The fixture
per test then cut its wall time from 71.7 s to 61.1 s.

### Keep the crates with one consumer

Rejected. A crate with one consumer changes the bytes of one binary, as a module does. Its seam made public items of
the internals, and it let a second binary link the whole crate for a small part of it. `vm` linked the server stack of
`avl-trace-serve` for one probe and one start. Its closure went from 138 to 119 crates when that link left.
`air-trace` linked the four backends of `avl-worker` for a lane table.

### A third refusal type

Rejected. The controller and BT each had their own refusal type, and a bridge converted one into the other. A third
type for `air-trace` or the guest agent would add a third conversion. The shared type takes the exit vocabulary as a
type parameter instead. So each tool keeps its own exit numbers, and a conversion maps an exit by its meaning.

### Base64 pulls on every backend

Rejected for Tart and Docker. Base64 puts a third more bytes on the channel and costs a decode on the host. ADR 0182
and ADR 0183 measured those two channels byte-clean, and the receipt now proves each transfer. Parallels keeps base64,
because no measurement proved `prlctl exec` byte-clean.

### Split the modules over 1 000 lines

Rejected. Three production modules pass 1 000 lines: `avl-base/src/config.rs` (1 608), `avl-vm/src/worker/docker.rs`
(1 132) and `avl-vm/src/worker/worker/tart.rs` (1 006). Each holds one concern: the settings with their defaults per
guest, the Docker backend and the Tart backend. A split by length gives one concern two files, and it changes no
re-key domain. The rework split the six drivers over 150 lines instead, around pure decisions with tests of their own.

## Consequences

- **The recorder links one sibling.** It links `avl-trace` and no other crate of the workspace. So only a change of
  `avl-record`, of `avl-trace` or of a third-party crate in its closure runs the UI-lane test targets again.
- **A guest byte change costs one install on each worker.** The guest links `avl-wire`, `avl-trace`, `avl-trace-tools`,
  `fscopy` and `refusal`. Its bytes changed in several commits of the rework, so each worker installs the agent once
  at its next `run`, `shard`, `flake` or `daemon start`.
- **`closure.txt` changes only on purpose.** The workspace README states the command that regenerates it.
- **A crate of another workspace that a member names by path spells its `[package]` values inline.** rules_rs reads
  its `Cargo.toml` without its own workspace. A fetch of its `rules_rs++crate+avl__<crate>-<version>` repository
  proves it, and the README states the command.

## Not verified

- **No lane smoke ran on the shared engine.** Another session used the Lima engine `air-docker-engine` and its slot
  `air-docker-1` during the rework. So a run on a Docker worker is owed. It must cover the guest agent bytes,
  the HTTP client of the recorder, the viewer probe and the timeouts. It must also cover the Docker version gate, the
  raw pulls, the stage on standard input and the push bound. The base64 pull on Parallels is owed too.
- **The Windows CI.** `air-trace-test` carries the Kotlin test data that `avl-trace-serve-test` had. On a Windows host
  that data needs the jars of the private Maven repository that ADR 0186 names.
- **The throughput of one `tart exec` stream.** The step of 8 MiB is an estimate. The time of a real pull and the
  saved exec of a stage on a real worker are not measured either.
- **The Linux X11 capture.** The copy of the shared-memory frame is linted and cross-built. No Linux host ran its tests.
- **One case of `avl-vm` failed once.** It failed in 1 of 554 cases, at a load average of about 45, and its log was not
  kept. Three full runs passed after it. Its name is not known. This fact comes from the gate run of the last step,
  not from a commit message.
