---
topic: testing
---

# 196. The class count of a bench run is the JVM class-load log

Date: 2026-10-02

## Status

Accepted. Extends [ADR 0193](0193-the-start-up-bench-is-a-vm-verb-over-a-staged-generation.md), which made the
start-up bench a `vm` verb. The verbs are in [the vm-lane README](../../README.md), section
**The bench**, and the grammar of each is its `--help`.

## Context

IJPL-257496 asks two counts of JetBrains Light: no class of a language plugin whose file types are absent in a
Markdown-only project, and under 30k named classes at the first highlighting. The bench of ADR 0193 could answer
neither.

- Its only total class count was `classLoading.count` of the start-up report, which is `ClassPath.classRequests`: a
  request count. A welcome run reports 257k requests against about 12k plugin classes.
- Its two class logs have no time. `plugin-classes.txt` names the plugin of each class in load order, and
  `class-report.txt` names the jar. Neither tells which classes were loaded before an anchor of the run.
- No run started the IDE on a project. `welcome` stops at the welcome screen. `open-project` opens a second project
  in a running IDE through the socket lock, which has no span, so it reports no number.
- The stage refused a dist whose config names additional modules. JetBrains Light with the language plugins is such a
  dist.

## Decision

**The JVM class-load log is the class count of a run, and a project run starts the IDE on the project from the
command line.**

1. **Every run passes `-Xlog:class+load` with the uptime decorator.** The JVM writes one line per class with its time
   and its source. The source tells a JDK class, a class of a jar, and a class of an IDE loader; a hidden class has
   `/0x` in its name. The log is the truth for the count: it is outside the IDE, it costs one line per class, and the
   reporter of the issue used it.
2. **The plugin class log gives the owner.** The record joins the JVM log with `plugin-classes.txt` by class name.
   A class of an IDE loader without an owner is a platform class. `class-report.txt` stays in a run directory for a
   person, and the record does not read it: it names the content-module jars only, never a jar of the core class
   path, so it cannot attribute a platform class.
3. **An anchor is a bound on the JVM clock.** The report's `jvm.loadingTime` is the offset between the JVM start and
   the IDE clock of the trace and the FUS events. `classes.named@<anchor>` counts the named classes before the frame,
   the welcome screen, the highlighted editor, and the quit.
4. **The `project` arm starts the IDE on the project.** The project lives inside the sandbox, beside the welcome
   project, so the editor state of the prime run is copied per run. The prime run opens the project with its first
   file, and a measured run opens the directory; the IDE restores the editor. The gate is the instant event
   `editor highlighting completed`. An arm owns its gate: the two welcome arms keep the FUS wait.
5. **`bench classes` answers from the files.** It prints the counts of one arm at an anchor: the totals by source, one
   table by plugin with a zero row for a named plugin, and one by content module. The maps live in `result.json`,
   and `summary.json` keeps the statistics only.
6. **The project arm is an integration test.** It passes `idea.is.integration.test=true`, as every perf test of the
   IDE Starter does. Air offers its welcome page in a project window unless that property is set. The page is an
   editor tab that takes the selected tab from the file, so the daemon never highlights the file.
7. **The prime run of the project arm waits for the report only.** It opens the file from the command line, and the
   report does not wait for the highlighting of such an editor, so the event lands after the drain and reaches no
   file. The prime counts for nothing.
8. **The stage accepts additional modules.** The dist is assembled with them, and the launch passes none.

## Alternatives considered

- **Count through `ClassLoadingMXBean` inside the IDE.** Rejected. It needs an IDE change per anchor, and it gives
  no owner.
- **Attribute from `plugin-classes.txt` alone, with the `run activity` spans as anchors.** The Python measurer did
  this. Rejected. The log has no platform classes and no time, and the split moves with the host load.
- **Repair `open-project` for the project run.** Rejected. The second-project path has no span, and the issue
  measures the start of the IDE on a project, which the direct launch is.
- **Run JetBrains Light through `//build:jetbrains_light_all_plugins_dist`.** Rejected. It bundles CLion Nova, which
  needs a .NET backend and fails every project open. The run configuration `JetBrains Light (Languages)` bundles the
  language plugins without C++, which is the setup of the issue.

## Consequences

- `classLoading.count` is `classLoading.requests`. A count row of `compare` prints as a whole number.
- `--profile` charges a stack that defines a class to the nearest Java frame above the loader frames, over all
  threads. That is the list of triggers of the issue.
- A JetBrains Light session has no modal arm, so the noise check of `compare` uses the first arm both sessions have.
- The welcome digest grows to about 53 lines without a profile.
- A run directory with a colon in its path fails, because the `-Xlog` option cannot carry one.
- The first session on this host, 3 runs of the `project` arm over the Markdown project on
  `//build:jetbrains_light_languages_dist` at a host load of 21 on 18 CPUs, counted 45989 named classes, 43600 of
  them before the first highlighting, 23796 of the platform and 15488 of the plugins. JavaScript loaded 1321,
  Database 1038, Python 983, Ruby 701, Kotlin 681 and Go 285 classes before a Markdown file was highlighted.
