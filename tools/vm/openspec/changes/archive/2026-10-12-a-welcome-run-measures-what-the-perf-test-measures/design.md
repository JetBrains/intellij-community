# A welcome run starts the IDE of the perf test and computes its spans as the perf test does

## Context

The perf test of the welcome screen is `WelcomeScreenCollectorService.kt` of `tests/startupPerformanceTests`, and CI
publishes its numbers to ij-perf. A bench session is the local check of a change before CI measures it. So a bench
number must mean what the CI number means.

The IDE Starter passes `idea.is.integration.test=true` on every run. The perf test publishes each welcome span as the
sum of the spans of its name, in `WelcomeScreenSpanPostProcessor`. The work on the welcome path also needs the end of
each start-up step on one clock with `welcomeBecameVisible`.

The [bench verb](../2026-10-01-the-start-up-bench-is-a-vm-verb-over-a-staged-generation/design.md) and the
[class-load log](../2026-10-22-the-class-count-of-a-bench-run-is-the-jvm-class-load-log/design.md) are the base. The
README section [The bench](../../../../README.md#the-bench) lists the verbs.

## Decisions

1. **Every arm is an integration test.** Every measured start passes `idea.is.integration.test=true`, as the IDE
   Starter does. The project arms also pass `idea.trust.all.projects=true`. `session.json` and `summary.json` name the
   measurement model, `integration-test` with a version. The version changes only when the runs pass something else to
   the IDE. `bench compare` adds a note when a session has another model than A.
2. **A welcome span metric is the sum of its spans.** A welcome span, a modal span and a name under
   `welcome right tab body: createContent ` each have one metric. The metric is the sum of the durations of the spans
   of the name.
   The coroutine helper twins `<name>: scheduled` and `<name>: completing` have names of their own, so they never
   count. A name that repeats adds a note to the run. A platform span keeps its first span.
3. **Span ends and `jvm.loadingTime` are metrics.** `<span> end` is the end of the first span of a start-up step, in
   ms from the process start. That is the clock of the anchors, of the FUS durations and of the report items. The
   digest shows the ends in the group `span ends (ms from the process start)`, and the modal spans in `modal spans`.
4. **The profile ends at the gate.** A profiled run reads the PID of the IDE from `idea.log` at its gate. It then runs
   `jcmd <pid> JVMTI.agent_load <library> stop,file=cpu-gate.collapsed,collapsed` with the JBR of the generation. The
   profile thus holds the start-up and not the hold and the quit. `--profile-event wall` samples the wall clock. A run
   that cannot stop the profiler keeps the whole-run `cpu.collapsed`, with a note.
5. **A session can run over a staged generation.** `--generation` takes the start of a digest and skips the build and
   the stage. A prefix with no match or several matches is refused with the candidates. `generation.json` records the
   commit that built the bytes, and a session copies it into `session.json`. An A/B/A run thus measures the base
   again from its own bytes.
6. **The modal arm has a report written at the exit.** The modal welcome frame creates the start-up service, which
   writes the report at the exit. The coroutine spans reach the trace then too, so the trace origin is the process
   start. The record reads both after the quit. A report with a negative or no `totalDuration` loses that value, with
   a note.

## Alternatives rejected

- **An option that sets the integration-test property per session.** A number that CI cannot reproduce cannot be
  checked against ij-perf.
- **Keep the first span, and add the sum under a second name.** One span then has two numbers, and only the sum has a
  counterpart in ij-perf.
- **Show the ends in `bench trace` only.** An end needs the median over the runs and the delta of `compare`, and only a
  metric has both.
- **Put the ends on the JVM clock.** The anchors and the FUS durations count from the process clock, and
  `jvm.loadingTime` is the offset between the two clocks.
- **Name the measurement model by the commit of the controller.** The commit changes with every change of the
  controller, but the model changes only when the runs pass something else.

## Consequences

- A session of one model does not compare with a session of another model. A replay cannot repair it, so the baseline
  is measured again.
- The sums and the ends come from the files of a run, so a replay of an older session computes them too.
- A span that the IDE does not write gives no metric, and the digest names it in its `not measured` line.
- The `jcmd` JVM of the profile stop starts at the gate, so the late spans of a profiled run carry its load.
- The class-load log is asynchronous, with `-Xlog:async:stall` and `-XX:AsyncLogBufferSize=32M`. A synchronous flush
  costs a start about half a second of CPU. The `stall` mode drops no line, so the class count stays exact.
- The profile leaves out the stacks under `Profiler::dlopen_hook`, which a run without the profiler never spends. The
  digest notes how many samples it leaves out.
- A modal metric of the report is the state at the exit, not at the welcome event. The class counts of the modal arm
  still stop at their anchor.
