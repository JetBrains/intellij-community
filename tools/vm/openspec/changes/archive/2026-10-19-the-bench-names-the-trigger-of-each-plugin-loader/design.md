# The bench names the trigger frame of each plugin loader from the debug log of the loaders

## Context

A bench session must rank a plugin by its classes at the start, and name the code that pulls each module of the
plugin. The [class-load log](../2026-10-22-the-class-count-of-a-bench-run-is-the-jvm-class-load-log/design.md) gives
the classes of each plugin, but no rank, no loader count, no time per plugin and no stack. The README section
[The bench](../../../../README.md#the-bench) lists the verbs.

## Decisions

1. **The debug log of the plugin class loaders carries the time and the activation stack.** `PluginClassLoader`
   writes the nanos of `findClass` of each define. The record of the first class of a loader also holds its activation
   stack, at most 40 frames. The log has the tab-separated format v2, and the
   [Plugin Class Log](../../../../../../platform/core-impl/spec/plugin-class-log.spec.md) spec is its contract. The
   trailer `#cost` gives the metrics `pluginLog.writeMs` and `pluginLog.stackMs`. The reader also takes the legacy
   shapes, because `--generation` can reuse an old distribution.
2. **The thread of a class comes from the JVM.** `class-load.log` has the `tid` decorator and the `os+thread` lines.
   The JVM names a Java thread only when the thread ends, so each run writes a `jcmd Thread.print` dump after its
   gate. The dump names the tid of the EDT. A run without the dump gives no thread, and the tables print `-`.
3. **A loader is the unit of the tables.** A loader is the main module or a content module of a plugin. The record keeps
   the classes, the EDT and BG nanos and the EDT class count of each loader, before each anchor and over the run. It
   also keeps one activation per loader: the first class, its first load, its stack and its trigger frame.
4. **`bench classes` ranks a plugin and splits its time.** `--plugin` prints the rank of each named plugin. The plugin
   table adds the loaders, the EDT ms and the BG ms. The module table holds the main modules as
   `<plugin id> (main)`. A cell without a time is `-`, so a table never shows a false 0.
5. **The trigger frame is the nearest frame of another owner.** The search starts at the top of the activation stack.
   It skips the runtime, the language libraries, the class loaders, the containers, the extension points and the
   message bus. These frames call a plugin class for another frame. A frame class without an owner is a platform
   class. `vm bench triggers --help` lists the skipped packages.
6. **`bench triggers` groups the loaders by their trigger.** It reads one run, by default the median run of the arm. A
   row holds the trigger frame, its loaders, their classes and EDT ms before the anchor, and the first load.
7. **A twin arm changes one input of each arm.** `--suppress-plugin <id>` adds `<arm>-without-<id>`, whose runs pass
   `-Didea.suppressed.plugins.id=<id>`. `--with-property` adds a twin with one system property. A twin has its own
   prime run and sandbox, and its runs interleave with the runs of its arm. The noise check of `compare` takes the
   plain arms only.

## Alternatives rejected

- **Call the `diagnostics classloaders` command inside the IDE.** IDEA does not include
  `intellij.platform.diagnostic.cli`, and the command counts at its call, not at the anchors of a run.
- **Log the JVM cause stack of every class on every run.** It slows a start down many times. It stays the opt-in
  `--class-causes` for `bench causes`, so a default run keeps only the activation stack of each loader.
- **Take the triggers from `--profile`.** A CPU sample shows a define only when the sampler hits it, and the profiler
  costs time.
- **Measure the suppressed start in a second session.** The second session runs at another host load, but an
  interleaved twin shares the noise of its arm.

## Consequences

- A distribution with the old log gives loaders and classes, but `-` for the times. `bench triggers` then puts every
  loader in the row `(no activation stack)`.
- `result.json` grows by the loader maps and the activations, and `summary.json` does not grow.
- `bench replay` writes the loader maps again from the logs of a run of an earlier controller.
- A twin doubles the runs of a session.
- A trigger names the code that woke a loader, not the code that loaded most of its classes. `bench causes` names the
  cause of each class.
- When a platform class reaches the plugin through a frame of a third plugin, the third plugin is the trigger. The
  stack in `result.json` keeps the whole chain.
