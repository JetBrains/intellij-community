---
topic: testing
---

# A welcome run measures what the perf test measures

Date: 2026-10-12

## Status

Accepted. Extends [ADR 0193](0193-the-start-up-bench-is-a-vm-verb-over-a-staged-generation.md), which made the
start-up bench a `vm` verb, and [ADR 0196](0196-the-class-count-of-a-bench-run-is-the-jvm-class-load-log.md), which
made the JVM class-load log the class count of a run. Item 1 widens item 6 of ADR 0196 from the project arm to every
arm. The verbs are in [the vm-lane README](../../README.md), section **The bench**, and the grammar of each is its
`--help`.

## Context

The perf test of the welcome screen is `WelcomeScreenCollectorService.kt` of `tests/startupPerformanceTests`. CI
publishes its numbers to ij-perf. A bench session is the local check of a change before CI measures it. These
differences kept the two numbers apart.

- The IDE Starter passes `idea.is.integration.test=true` on every run (`IDERunContext.calculateVmOptions`). The bench
  passed it in the project arms only, for the reason of ADR 0196, item 6. A welcome run of the bench thus started an
  IDE that offers a person what an integration test skips. CI never measures that IDE.
- The perf test publishes each welcome span as the sum of the spans of its name (`WelcomeScreenSpanPostProcessor`).
  The bench took the first span of a name. A name that repeats gave the bench a smaller number than CI.
- A duration tells how long a step took, not when it ended. The work on the welcome path needs the end of each
  start-up step on one clock with `welcomeBecameVisible`. The report's `jvm.loadingTime` was only the offset of the
  class counts, so a change of the JVM start showed in no metric.

The IDE writes new spans too: the paint of the left panel and of the right tab of the non-modal welcome screen, and
the steps of the modal welcome frame.

## Decision

**A welcome run starts the IDE that the perf test starts, and the bench computes a welcome span as the perf test
computes it.**

1. **Every arm is an integration test.** Every measured start passes `idea.is.integration.test=true`, as the IDE
   Starter does. The project arms keep `idea.trust.all.projects=true`. `session.json` and `summary.json` name the
   measurement model: `integration-test`, version 1. A session from before the model has none. `bench compare`
   prints a note after its session lines when a session has another model than A. A delta between two models mixes
   the change under test with the change of the model.
2. **A welcome span metric is the sum of its spans.** A welcome span, a modal span and a name under
   `welcome right tab body: createContent ` each have one metric. It is the sum of the durations of the spans of the
   name, as `WelcomeScreenSpanPostProcessor` sums them. The coroutine helper twins, `<name>: scheduled` and
   `<name>: completing`, have names of their own, so they never count. A name that repeats adds a note to the run.
   The trace gives the spans, and the report gives them when the trace has no span of the name. A platform span keeps
   its first span.
3. **Span ends and `jvm.loadingTime` are metrics.** `<span> end`, such as `toolwindow creating end`, is the end of
   the first span of a start-up step, in milliseconds from the process start. That is the clock of the anchors, of
   the FUS durations and of the report items: `StartUpMeasurer.getStartTime()`, which is the start of `bootstrap`.
   The steps start at `app initialization` and end at the modal welcome frame. Between them are the application
   callbacks, the project open of the welcome screen, the tool windows, the left panel, the editors and the frame
   allocator. The report's `jvm.loadingTime`, the time of the JVM before that clock starts, is a metric of its own.
   The digest shows the ends in the group `span ends (ms from the process start)`, and the modal spans in the group
   `modal spans`.
4. **The profile ends at the gate.** When a profiled run passes its gate, it reads the PID of the IDE from
   `idea.log`. It then runs `jcmd <pid> JVMTI.agent_load <library> stop,file=cpu-gate.collapsed,collapsed` with the
   JBR of the generation. The second load of the library reaches the agent that `-agentpath` started, and
   `-XX:+EnableDynamicAgentLoading` allows the load. The profile then holds the start-up, and not the hold and the
   quit. `--profile-event wall` samples the wall clock in place of the CPU. A run that cannot stop the profiler keeps
   the whole-run `cpu.collapsed`, with a note that names why, and the digest says which profile it read.
5. **A session can run over a staged generation.** `--generation` takes the start of a digest, opens
   `<runtime root>/bench/generations/<digest>`, and skips the build and the stage. A prefix that no generation has,
   or that several have, is refused with the candidates. The stage records the commit of the checkout that built the
   bytes in `generation.json`, and a session over the generation copies that commit into `session.json`. `ls` and
   `compare` thus show the commit of the measured bytes, not the commit of the checkout of the controller. An A/B/A
   run then measures the base again from its own bytes.
6. **The modal arm has a report written at the exit.** A start without a project ends no start-up, so the modal
   welcome frame creates the start-up service of the IDE, which writes the report at the exit. The coroutine spans of
   the start reach the trace then too, `bootstrap` among them, so the trace origin is the process start. The record
   reads both after the quit. The modal arm thus has the report metrics, the platform spans, the modal spans and
   their ends. Such a report has a negative or no `totalDuration`, which the record drops with a note.

## Alternatives considered

- **An option that sets the integration-test property per session.** Rejected. A number that CI cannot reproduce
  cannot be checked against ij-perf, and the perf test is the reference of the bench.
- **Keep the first span, and add the sum under a second name.** Rejected. One span would have two numbers, and only
  the sum has a counterpart in ij-perf.
- **Show the ends in `bench trace` only.** Rejected. An end needs the median over the runs and the delta of
  `compare`, and only a metric has both.
- **Put the ends on the JVM clock.** Rejected. The anchors and the FUS durations count from the process clock, so an
  end on that clock compares with `welcomeBecameVisible` as it is. The offset to the JVM clock is the metric
  `jvm.loadingTime`.
- **Name the measurement model by the commit of the controller.** Rejected. The commit changes with every change of
  the controller. The model changes only when the runs pass something else to the IDE.

## Consequences

- A welcome session of this model does not compare with one from before it, because the IDE of a welcome run
  differs. `compare` says so. A replay cannot repair it, because the property changed what the IDE ran, so the
  baseline is measured again.
- The sums and the ends come from the files of a run, so a replay of an older session computes them too. Its model
  stays none.
- The digest grows by the span ends, and by the modal spans in a session with the modal arm.
- A span that the IDE does not write gives no metric, and the digest names it in its `not measured` line. A build
  from before the new spans still gives a summary.
- The stop starts a `jcmd` JVM at the gate, while the non-modal welcome screen can still paint. The late spans of a
  profiled run thus carry the load of that JVM, and the profile ends before the paint.
- A generation from before the record of the commit names none, so a session over it names no commit and warns.
- The class-load log is asynchronous, with `-Xlog:async:stall` and `-XX:AsyncLogBufferSize=32M`. A flush of each
  class line under the global log lock cost a run 481 to 773 ms of CPU over its threads, 64 ms of it on the EDT. The
  default mode of JBR 25, `drop`, loses a line when the buffer is full. In the `stall` mode a full buffer makes the
  logging thread wait, so no class line is dropped and the class count stays exact. The buffer holds the class log of a whole start, so a wait stays rare, at the price of 32 MB of native
  memory. A run that the quit kills loses the lines that are still in the buffer.
- The profile leaves out the stacks under `Profiler::dlopen_hook`, where async-profiler parses the symbols of each
  library that the JVM loads. A run without the profiler never spends that time, about half a second on the EDT of a
  profiled start, and the other threads wait on its lock. The EDT frames and the class-loading triggers both leave
  these stacks out, and the digest notes how many samples it left out.
- A modal metric of the report is the state at the exit, not at the welcome event. The class counts of the modal
  arm still stop at their anchor, because the class-load log has the time of each class.
