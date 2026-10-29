---
topic: testing
---

# The bench clock starts at the load of the main class

Date: 2026-10-22

## Status

Accepted. Amends item 3 of [ADR 0196](0196-the-class-count-of-a-bench-run-is-the-jvm-class-load-log.md), which made
`jvm.loadingTime` the offset between the JVM clock of `class-load.log` and the process clock. The verbs are in
[the vm-lane README](../../README.md), section **The bench**, and the grammar of each is its `--help`.

## Context

IJAI-4575 asks which code takes the EDT in the spans of the Air empty-state composer. A script mapped the spans of
`opentelemetry.json` onto `class-load.log` with `jvm.loadingTime`. In session 85055df7, the window of
`air.emptyState.createContent` then held the tool window classes for its first 80 ms. `AgentPromptInlineConfiguration`,
which the composer creates before that span, loaded 80 ms inside the window.

- The IDE writes `jvm.loadingTime` as the start of the process clock minus the start time of `RuntimeMXBean`. The
  uptime of the JVM log starts before that start time, so the offset was short in every run.
- The process clock starts at the first statement of `main` of `com.intellij.idea.Main`. In the 5 runs of the
  session, the load of that class is 76 to 101 ms after `jvm.loadingTime`.
- `StartUpMeasurer` loads at the end of the bootstrap step `init scope creating`. On the clock of the main class it
  loads 3 to 4 ms after that end in the 5 runs. On the clock of `jvm.loadingTime` it loads 80 to 104 ms after it.
- The anchors of ADR 0196, `classes.named@<anchor>`, `--entries` and the first load of a loader had the same error.
- The bench had no verb for the loads inside one span, and `bench project` had no `--profile`.
- The digest showed only the composer spans of a fixed list and the prologue steps. A new span such as
  `air.emptyState.content.<part>` was a metric that the digest did not show.

## Decision

**The load of the main class is the start of the process clock on the JVM clock, and the bench counts the loads
inside the window of a span on that clock.**

1. **The main class is the anchor.** The record reads the uptime of `com.intellij.idea.Main` in `class-load.log`.
   It is the metric `jvm.processStart`, and every class anchor uses it. A log without the class takes
   `jvm.loadingTime`, with a note of the run. The log has a resolution of 1 ms.
2. **`bench classes --span <name>` counts the loads of a window.** The window of a run is its first span of the name.
   The tables hold the loads of the EDT by plugin, by module and by the package of a class without a plugin. A hidden
   class counts with its host. The findClass ms come from `plugin-classes.txt`. The BG columns hold the loads of the
   other threads, and `--thread all` counts every thread. A number is the median over the runs with the span.
   `--run` takes one run, and `--timeline <ms>` adds the loads and the first classes of each bucket.
3. **`bench project` takes `--profile`, as `welcome` does.** The profile of the empty-editor arm ends at its gate,
   the end of `air.emptyState.createComponent`.
4. **The digest shows each composer span.** A run records its composer spans in the order of their first start, and
   the longer span comes first at one start. The digest shows them in that order of the median run, so a nested span
   follows its parent.

## Alternatives considered

- **Add the `timemillis` decorator to `-Xlog`.** Rejected for now. The epoch time of each line would join the log to
  the trace with no anchor. But the old sessions have no such decorator, and the main class gives the same 1 ms.
- **Correct `jvm.loadingTime` by a fixed offset.** Rejected. The error differs by 25 ms between the runs of one
  session, so only a measure per run is correct.
- **Cut the profile to the window of a span.** Not done. The collapsed format of async-profiler has no time per
  sample. A cut needs the JFR output, which has the time of each sample, and a converter with a time filter in the
  pipeline of the bench.

## Consequences

- A replay gives new anchor counts. In session 85055df7, the median `classes.named@emptyStateBuilt` moves from 26959
  to 27392, and `classes.edt@emptyStateBuilt` from 4147 to 4417.
- In run 05 of that session, the window of `air.emptyState.createContent` holds 2053 EDT loads, 1106 of them of
  `intellij.air.frontend.prompt.ui`. `AgentPromptInlineConfiguration` loads 5 ms before the window.
- `result.json` and `summary.json` hold `emptyStateOrder` for each run of the empty-editor arm.
- `--span` reads the class logs of each run, not only `result.json`, so it is slower than the other reading verbs.
