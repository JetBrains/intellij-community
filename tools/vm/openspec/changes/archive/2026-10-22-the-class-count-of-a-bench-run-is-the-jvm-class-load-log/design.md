# The JVM class-load log counts the classes of a bench run, on the clock of the main class

## Context

A bench run must answer two class questions of JetBrains Light. Which plugin classes load in a project that has no
file type of the plugin? How many named classes load before the first highlighting?

The start-up report gives `classLoading.count`, which is `ClassPath.classRequests`. That is a request count, not a
class count. `plugin-classes.txt` names the plugin of each class, and `class-report.txt` names the jar, but neither has
a time. The report value `jvm.loadingTime` puts the process start about 100 ms too early on the JVM clock, and the
error differs between runs.

The [generation](../2026-10-01-the-start-up-bench-is-a-vm-verb-over-a-staged-generation/design.md) is the input of
every run. The README section [The bench](../../../../README.md#the-bench) lists the verbs.

## Decisions

1. **Every run passes `-Xlog:class+load` with the uptime decorator.** The JVM writes one line per class with its time
   and its source. The source tells a JDK class, a class of a jar and a class of an IDE loader. The log is outside the
   IDE and costs one line per class.
2. **`plugin-classes.txt` gives the owner.** The record joins the JVM log with the plugin class log by class name. A
   class of an IDE loader without an owner is a platform class. The record does not read `class-report.txt`, because
   it names no jar of the core class path.
3. **An anchor is a bound on the JVM clock, from the load of `com.intellij.idea.Main`.** The process clock starts at
   the first statement of `main` of that class. The record reads its uptime in `class-load.log` as the metric
   `jvm.processStart`, and every class anchor uses it. A log without the class takes `jvm.loadingTime`, with a note.
   `classes.named@<anchor>` counts the named classes before the frame, the welcome screen, the highlighted editor and
   the quit.
4. **The `project` arm starts the IDE on the project.** The project lives inside the sandbox. The prime run opens the
   project with its first file, and a measured run opens the directory, so the IDE restores the editor. The gate is
   the instant event `editor highlighting completed`. An arm owns its gate, and the welcome arms keep the FUS wait.
5. **`bench classes` answers from the files.** It prints the totals by source, one table by plugin and one by module,
   at an anchor. A named plugin without classes gets a zero row. The maps live in `result.json`, and `summary.json`
   keeps the statistics only.
6. **Every arm is an integration test.** The [welcome decision](../2026-10-12-a-welcome-run-measures-what-the-perf-test-measures/design.md)
   states the rule. Without it, Air offers its welcome page as an editor tab, and the daemon never highlights the file.
7. **The prime run of the `project` arm waits for the report only.** The report does not wait for the highlighting of a
   file of the command line, so the prime run counts for nothing.
8. **The stage accepts additional modules.** The dist is assembled with them, and the launch passes none.
9. **`bench classes --span <name>` counts the loads in the window of a span.** The window is the first span of the name
   in a run. The tables hold the EDT loads by plugin, by module and by package. `--thread all` counts every thread,
   `--run` takes one run, and `--timeline <ms>` adds buckets.
10. **`bench project` takes `--profile`, as `welcome` does.** The profile of the empty-editor arm ends at its gate, the
    end of `air.emptyState.createComponent`.
11. **The digest shows each composer span in the order of the median run.** A run records its composer spans in the
    order of their first start, so a nested span follows its parent.

## Alternatives rejected

- **Count through `ClassLoadingMXBean` inside the IDE.** It needs an IDE change per anchor, and it gives no owner.
- **Attribute from `plugin-classes.txt` alone, with the `run activity` spans as anchors.** That log has no platform
  classes and no time.
- **Repair `open-project` for the project run.** The second-project path has no span, and the question is the start of
  the IDE on a project.
- **Run JetBrains Light through `//build:jetbrains_light_all_plugins_dist`.** It bundles CLion Nova, which needs a .NET
  backend and fails every project open.
- **Add the `timemillis` decorator to `-Xlog`.** The old sessions lack it, and the main class gives the same 1 ms.
- **Correct `jvm.loadingTime` by a fixed offset.** The error differs by about 25 ms between the runs of one session.
- **Cut the profile to the window of a span.** The collapsed format of async-profiler has no time per sample.

## Consequences

- `classLoading.count` of the report is the metric `classLoading.requests`.
- `--profile` charges a stack that defines a class to the nearest Java frame above the loader frames.
- A JetBrains Light session has no modal arm, so the noise check of `compare` uses the first arm both sessions have.
- A run directory with a colon in its path fails, because the `-Xlog` option cannot carry one.
- A replay of an older session computes the anchor counts again on the clock of the main class.
- `--span` reads the class logs of each run, so it is slower than the other reading verbs.
