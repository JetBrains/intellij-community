# The cargo-sync arm of the bench ends at the first CargoSyncTask line of idea.log

## Context

The RustRover perf test `RustRoverIndexingTest` publishes `rust_duration_from_start_to_cargo_sync`. It reads the `[ms]`
column of the first `CargoSyncTask started` line of `idea.log`. That column counts from the creation of the log, not
from the process start. The Rust plugin writes no span for the sync. So a bench gate on a FUS event, an editor or a
span cannot end such a run.

The anchors use the clock of the
[class-load log](../2026-10-22-the-class-count-of-a-bench-run-is-the-jvm-class-load-log/design.md). The README section
[The bench](../../../../README.md#the-bench) lists the verbs.

## Decisions

1. **The gate is a named line of the log.** `Gate::LogLine` names the line and two metrics, and the launch polls
   `log/idea.log` for it. Another product can add an arm with its own line and no new code in the launch.
2. **The metric is on the clock of the anchors.** The local time of the line and the UTC offset of the `time` tag of
   the trace give the epoch time. Less the trace origin, it is `cargoSyncStarted` in ms from the process start.
   `cargoSyncStarted.ideaLog` keeps the column, the value of the perf test. `cargoSyncStarted` is a class anchor, and
   `--profile` stops at the gate.
3. **The project is a directory.** The bench copies it into the sandbox, as it copies every project. The archive of the
   perf test is unpacked once under `<runtime root>/bench/inputs/100exercises`.
4. **The license file takes the name of the product.** The license manager reads `<short name>.key`, and the short name
   equals the platform prefix. It is `rustrover.key` for RustRover and `idea.key` for IDEA.
5. **Every run is a first start, as in CI.** Each run starts from the unprimed template with empty caches, and the
   session has no prime run. The IDE opens no README. The arm passes `llm.show.ai.promotion.window.on.start=false` and
   `org.rust.external.linter.on.the.fly=false`, the two properties of `rustRoverVmOptions` that act before the sync. A
   warm run restores an editor, so the empty editor area never builds the Air composer, and the run misses its cost.

## Consequences

- A session needs a dist of a product with the Rust plugin, such as `//build:rustrover_dist`, and `rustup` on the host.
  The IDE finds the toolchain in `~/.cargo/bin`.
- The trace must hold the `time` tag, so a run without it fails its gate with a reason.
- `--cold` changes nothing for the arm, because every run is cold.
- The bench differs from CI in the host and the start. CI starts an installer build on Linux through the IDE Starter.
  The bench starts the dev distribution with `java` on macOS.
- CI passes `-Xmx4096`, the debug loggers of the sync, `rust.internal.tracing=true` and the profiler on every run. The
  bench keeps the heap of the distribution and adds its own logs.
- CI sets `CARGO_HOME` to an empty directory, and the bench uses the `~/.cargo` of the host. This changes the work
  after the gate only.
- The wall time to the gate follows the host load closely. On a busy host, the EDT CPU samples of `--profile` compare
  two arms better than `cargoSyncStarted`.
