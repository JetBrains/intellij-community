---
topic: testing
---

# The bench finds the WebKit processes of the IDE by the responsible process

Date: 2026-11-20

## Status

Accepted. The verbs are in [the vm-lane README](../../README.md), section **The bench**, and the grammar of each is its
`--help`.

## Context

A run of `vm bench` measures the memory of the IDE at its gate. The memory of a web view is not in the IDE process.
JCEF runs in `cef_server` and its helpers, which are descendants of the IDE. A `WKWebView` runs in the XPC processes
`com.apple.WebKit.WebContent`, `com.apple.WebKit.GPU` and `com.apple.WebKit.Networking`. Their parent is launchd, so a
sum over the process tree of the IDE misses them. One WebKit process can serve another app, such as Safari.

macOS charges each process to a responsible process. `responsibility_get_pid_responsible_for_pid` of libSystem
answers it. These facts were measured on the macOS host of the bench:

- The WebKit processes of BranchDeck, Peekaboo and Safari name the pid of their app. Launch Services started each app,
  so each app is its own responsible process.
- A test program that holds a `WKWebView` ran as a child of a shell inside JetBrains Light. The program and its three
  WebKit processes named the pid of JetBrains Light. A WebKit process of a bench IDE could then not be told from a
  WebKit process of the IDE of the user.
- The same program ran with `responsibility_spawnattrs_setdisclaim(attributes, 1)` on its `posix_spawn` attributes. The
  program and its three WebKit processes named the pid of the program.
- A shell that had no responsible process answered its own pid, and so did each child that inherited from it. A
  disclaimed child of that shell answered its own pid, and its child named it.

## Decision

**The IDE of a run starts as its own responsible process, and the memory sample holds each process whose responsible
process is the IDE.**

1. **The IDE disclaims its responsibility.** The controller spawns the IDE with `posix_spawn`, `POSIX_SPAWN_SETEXEC`
   and the disclaim, in the hook between the fork and the exec. The IDE keeps its pid, its process group and its
   descriptors. An app that Launch Services starts has the same responsibility, so the start is nearer to the
   production start.
2. **The sample is the tree and the responsible processes.** After the gate, the run reads the process table with
   libproc. The sample holds the IDE that `idea.log` names, each descendant, and each process whose responsible process
   is the IDE. A process of another IDE names another responsible process, so the sample holds no process of it.
3. **The group comes from the name.** A process whose name starts with `cef_server`, or a descendant of one, is JCEF. A
   process whose name starts with `com.apple.WebKit.` is WebKit. The IDE is its own group, and the rest is the other
   group.
4. **A metric is the physical footprint.** `ri_phys_footprint` of `proc_pid_rusage` is the memory that Activity Monitor
   shows. The resident size is a second metric, because it counts each shared page once per process.
5. **A doubtful sample has no WebKit metric.** An IDE that names another responsible process has no WebKit process in
   the sample. The record then gives no WebKit metric and adds a note.

## Alternatives considered

- **Sum the process tree only.** Rejected. The WebKit processes are children of launchd.
- **Take every WebKit process of the user.** Rejected. Safari and the IDE of the user also have WebKit processes.
- **Match the responsible process of the controller.** Rejected. The controller can run under an app, such as a
  terminal or an IDE, and the WebKit processes of that app then name the same pid.
- **Start the IDE through Launch Services with `open`.** Rejected. The controller then loses the exit code, the log
  and the process group of the IDE.
- **Use the `footprint` tool.** Rejected. It is a subprocess for each sample, and libproc gives the same value.

## Consequences

- The privacy prompts of macOS now name the Java binary of the generation, not the app of the controller. The
  sandbox, the project and the checkout of a run are outside the protected folders.
- A process that disclaims its responsibility again is not in the sample unless it descends from the IDE.
