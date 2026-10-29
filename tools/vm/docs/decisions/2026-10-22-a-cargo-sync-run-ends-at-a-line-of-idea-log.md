---
topic: testing
---

# A cargo-sync run ends at a line of idea.log

Date: 2026-10-22

## Status

Accepted. Extends [ADR 0193](0193-the-start-up-bench-is-a-vm-verb-over-a-staged-generation.md) with an arm for
RustRover. [The bench clock starts at the load of the main class](2026-10-22-the-bench-clock-starts-at-the-load-of-the-main-class.md)
gives the clock of its anchors.

## Context

RUST-21131: `rust_duration_from_start_to_cargo_sync` of `RustRoverIndexingTest` went from about 4.2 s to 5.1 to 5.9 s
when Air was bundled into RustRover. The perf test reads the `[ms]` column of the first `CargoSyncTask started` line
of `idea.log`. The column counts from the creation of the log, not from the process start. The Rust plugin writes no
span for the sync. The bench had a gate on a FUS event, on the highlighted editor and on a span, but none on a line of
the log. It also copied the license as `idea.key` for every product.

## Decision

**The arm `cargo-sync` of `bench project` ends at the first `CargoSyncTask started` line of `idea.log`.**

1. **The gate is a named line of the log.** `Gate::LogLine` names the line and two metrics. The launch polls
   `log/idea.log` for it. Another product can add an arm with its own line and no new code in the launch.
2. **The metric is on the clock of the anchors.** The local time of the line and the UTC offset of the trace, the
   `time` tag of its process, give the time since the epoch. Less the origin of the trace, it is `cargoSyncStarted`
   in ms from the process start. `cargoSyncStarted.ideaLog` keeps the column, the value of the perf test.
   `cargoSyncStarted` is a class anchor, and `--profile` stops at the gate.
3. **The project is a directory.** The bench copies it into the sandbox as it copies every project. The perf test
   downloads `100-exercises-to-learn-rust-latest_stable.zip` of `rustrover/testData/regressions/regressions.properties`.
   The archive is unpacked once, beside the runs, under `<runtime root>/bench/inputs/100exercises`.
4. **The license file takes the name of the product.** The license manager reads `<short name>.key`, and the short
   name equals the platform prefix: `rustrover.key` for RustRover, `idea.key` for IDEA.
5. **Every run is a first start, as in CI.** Amended 2026-10-22. The CI metric is the first launch of
   `RustRoverIndexingTest`, which starts from a fresh config and system directory and turns off the README with
   `disableReadmeMdOnStartup`. So the arm is always cold: each run starts from the unprimed template with empty
   caches, and the session has no prime run. The IDE opens no README. The arm also passes the two properties of
   `rustRoverVmOptions` that act before the sync: `llm.show.ai.promotion.window.on.start=false` and
   `org.rust.external.linter.on.the.fly=false`.

   A warm run missed the Air cost. The prime run opened the README and saved the editor, so each later run restored
   it, and the empty editor area never built the Air composer. In the warm session 3a0a646e, Air had 744 classes
   before the gate. In the cold session 9c081401, `air.emptyState.createComponent` ended before the gate in each run.
   Air then had 4095 classes before it, with 300 findClass ms on the EDT, and the stacks with an Air frame took about
   330 ms of EDT CPU per run.

## What still differs from CI

- CI starts an installer build of RustRover through the IDE Starter on a Linux machine. The bench starts the dev
  distribution `//build:rustrover_dist` with `java` on a macOS host, without the native launcher.
- CI passes `-Xmx4096`, the debug loggers of the Cargo sync, `rust.internal.tracing=true` and the async-profiler
  agent on every run. The bench keeps the heap of the distribution and runs the profiler only with `--profile`.
- CI sets `CARGO_HOME` to an empty directory. The bench uses the `~/.cargo` of the host. This changes the work after
  the gate, not before it.
- The bench adds its own logs: `class-load.log`, `plugin-classes.txt` and the FUS test scheme.

## Consequences

- A session needs a dist of a product with the Rust plugin, such as `//build:rustrover_dist`, and `rustup` on the
  host. The IDE finds the toolchain in `~/.cargo/bin`.
- The trace must hold the `time` tag, so a run without it fails its gate with a reason.
- `--cold` changes nothing for the arm, because every run is cold.
- The wall time to the gate follows the host load closely. Under a load above the CPU count, the EDT CPU samples of
  `--profile` compare two arms better than `cargoSyncStarted`.
