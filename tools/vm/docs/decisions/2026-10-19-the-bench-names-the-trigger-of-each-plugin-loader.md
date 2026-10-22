---
topic: testing
---

# The bench names the trigger of each plugin loader

Date: 2026-10-19

## Status

Accepted. Extends [ADR 0196](0196-the-class-count-of-a-bench-run-is-the-jvm-class-load-log.md), which made the JVM
class-load log the class count of a run and `plugin-classes.txt` the owner of a class. The verbs are in
[the vm-lane README](../../README.md), section **The bench**, and the grammar of each is its `--help`.

## Context

IJAI-1571 asks where Air is among all plugins by its classes at the start, and which code pulls each Air module. The
first numbers of the issue came from the `diagnostics classloaders` command of MR 222450, which runs inside the IDE.
The bench of ADR 0196 could not answer these questions.

- `bench classes` gave the classes of each plugin, but no rank of a plugin and no count of its loaders.
- The module table held the content modules only, over the whole run, with no plugin and no anchor.
- The time of a class load was a sum over all plugins: `classLoading.edtTime` of the report.
- No file named the stack that loaded the first class of a loader. Only `--profile` gave triggers, as CPU samples
  over all threads.
- The arms of a session were fixed, so a session could not measure the start without one plugin.

## Decision

**The debug log of the plugin class loaders carries the thread, the time and the activation stack, and the bench
reads them from the files of a run.**

1. **The debug log has three new parts.** `PluginClassLoader` writes the thread of each define, `edt` or `bg`, and
   the nanos of `findClass` in that loader. The record of the first class of a loader continues with its activation
   stack, one `\tat <frame>` line per frame. The loader drops the frames of the class-loading machinery on top and
   keeps at most 40 frames. A publish on the message bus takes about 15 frames of the bus, so 25 frames can end
   before the publisher. A define that fails adds a `\terror: <message>` line. The loader writes each record in one
   `write` call, so threads never mix the lines of two records. The reader still takes the old shape, because
   `--generation` can reuse a distribution from before the change.
   Amended 2026-10-19: the log moved to the tab-separated format v2, which costs the IDE less to write. The spec
   [Plugin Class Log](../../../../platform/core-impl/spec/plugin-class-log.spec.md) is the contract of the format. Its
   trailer `#cost` is the self-measurement of the log. The bench reports its two times as the metrics
   `pluginLog.writeMs` and `pluginLog.stackMs`, so `compare` shows the cost of the log. A log without the trailer has
   no such metric. The reader also takes the legacy shape without a header.

   Amended 2026-10-19: the thread of a class comes from the JVM, not from the IDE. `class-load.log` has the `tid`
   decorator and the `os+thread` lines. The JVM names a Java thread only when the thread ends, so each run writes a
   `jcmd Thread.print` dump after its gate, before the stop of the profile. The dump names the tid of the EDT. A dump
   that fails is a note of the run. This is version 2 of the measurement model. A run without the dump gives no
   thread, and the tables print `-`.
2. **A loader is the unit of the tables.** A loader is the main module or a content module of a plugin. The record
   joins the JVM log with the plugin log per loader, before each anchor and over the whole run. It keeps the classes,
   the EDT and BG nanos and the EDT class count of each loader. It also keeps one activation per loader: the first
   class, its first load on the process clock, its stack and its trigger frame. `result.json` holds these maps, and
   `summary.json` keeps the statistics only.
3. **`bench classes` ranks a plugin and splits its time.** `--plugin` prints the rank of each named plugin and its
   part of the plugin classes at the anchor. The plugin table adds the loaders, the EDT ms and the BG ms of a plugin.
   The module table follows `--at`. It holds the main modules as `<plugin id> (main)` and names the plugin of each
   module. A log of the old shape has no time, so a time cell is `-`, and a note says why. A table never shows a
   false 0.
4. **The trigger frame is the nearest frame of another owner.** The search starts at the top of the activation
   stack. It skips the frames of `java.`, `jdk.`, `sun.`, `kotlin.` and `kotlinx.`, because the runtime and the
   language libraries call a plugin class for another frame. It also skips the frames of
   `com.intellij.serviceContainer.`, `com.intellij.openapi.extensions.` and `com.intellij.util.messages.`. The first
   session put 64 of 100 Air loaders under one frame, `ComponentManagerImplKt.doLoadClass`. That frame is the
   container, not the code that asked for the extension or published the topic. With the skip, the trigger of
   `intellij.air.frontend.prompt.ui` is `TypedHandlerPreloader.execute`. A frame class without an owner in the plugin log is a
   platform class. The search removes the `<loader>/<module>@<version>/` prefix of a frame first. It takes the host
   class of a lambda. A loader without a stack has no trigger. A stack without a frame of another owner has none too.
5. **`bench triggers` groups the loaders by their trigger.** It reads one run, by default the median run of the arm,
   as `classes --entries` does. A row holds the trigger frame, its loaders, their classes and EDT ms before the
   anchor, and the first load of the earliest loader. The loaders of each trigger follow the table. This is the
   trigger table of IJAI-1571.
6. **A twin arm suppresses one plugin.** `--suppress-plugin <id>` of `welcome` and `project` adds a twin after each
   arm: `<arm>-without-<id>`. A run of a twin passes `-Didea.suppressed.plugins.id=<id>`, so the IDE loads no
   plugin of that id. A twin has its own prime run and its own sandbox, and its runs interleave with those of its
   arm. `session.json` names the plugin in `suppressedPlugin` and the twins in `arms`. The summary, `compare` and the
   `--arm` of every reading verb take a twin as an arm. The noise check of `compare` takes the plain arms only. In the
   controller, an arm is a struct of its kind and its suppressed plugin, and its key and its label are strings. A
   plain arm keeps its old key, so a session from before the twin replays as it did.

## Alternatives considered

- **Call the `diagnostics` command of MR 222450 from the bench.** Rejected. IDEA does not include
  `intellij.platform.diagnostic.cli`, so the command is not in the measured distribution. The command also counts at
  the moment of its call, inside the IDE. The bench counts at anchors of the run from the files outside the IDE, as
  ADR 0196 decided, and a replay computes the counts again.
- **Log the JVM cause stack of every class** (`-XX:LogClassLoadingCauseFor=*` with `-Xlog:class+load+cause`).
  Rejected. On one generation, it moved the median `welcomeBecameVisible` of a welcome session from 1839 ms to
  8290 ms. It took about 40 % of the CPU samples and wrote about 19 MB per run, so the IDE keeps the activation stack
  of each loader.
- **Take the triggers from `--profile`.** Rejected. A CPU sample shows a define only when the sampler hits it. The
  profiler costs time, and its triggers mix all plugins. The activation stack names the trigger of each loader once,
  with no sampling.
- **Write the stack of each class.** Rejected. A record grows by up to 25 lines, and the first class of a loader
  already names the code that activated the loader.
- **Measure the suppressed start in a second session.** Rejected. The second session runs at another host load. The
  runs of an interleaved twin share the noise of the runs of its arm.

## Consequences

- A distribution from before the change gives the old log. Its tables have loaders and classes, but `-` for the
  times, and `bench triggers` puts every loader in the row `(no activation stack)`.
- `result.json` grows by the loader maps and the activations, about one entry per loader and anchor. `summary.json`
  does not grow.
- A `result.json` of an earlier controller has no loader maps. `bench classes` says so, and `bench replay` writes the
  maps again from the logs of the run.
- A twin doubles the runs of a session.
- A trigger names the code that woke a loader, not the code that loaded most of its classes. The classes of a row are
  all classes of its loaders up to the anchor. In the first session, `TypedHandlerPreloader.execute` woke
  `intellij.air.frontend.prompt.ui` with 12 classes. The empty-editor arm then loaded about 1000 classes of that
  module for the Air composer, and the row of the preloader holds them all. A stack per class would name each cause,
  and the alternatives above reject its cost.
- The trigger of a loader is the first frame of another owner. When a platform class reaches the plugin through a
  frame of a third plugin, the third plugin is the trigger. The stack in `result.json` keeps the whole chain.
