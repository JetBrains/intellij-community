# The bench IDE is its own responsible process, and the memory sample holds its WebKit processes

## Context

A bench run measures the memory of the IDE at its gate. JCEF runs in `cef_server` and its helpers, which descend from
the IDE. A `WKWebView` runs in the XPC processes `com.apple.WebKit.WebContent`, `com.apple.WebKit.GPU` and
`com.apple.WebKit.Networking`. Their parent is launchd, so a sum over the process tree misses them. One WebKit process
can also serve another app, such as Safari.

macOS charges each process to a responsible process, and `responsibility_get_pid_responsible_for_pid` of libSystem
answers it. A child of a terminal or an IDE names the responsible process of that app. With
`responsibility_spawnattrs_setdisclaim`, a spawned process is its own responsible process, and so are its WebKit
processes. The README section [The bench](../../../../README.md#the-bench) lists the verbs.

## Decisions

1. **The IDE disclaims its responsibility.** The controller spawns the IDE with `posix_spawn`, `POSIX_SPAWN_SETEXEC`
   and the disclaim, between the fork and the exec. The IDE keeps its pid, its process group and its descriptors. An
   app that Launch Services starts has the same responsibility, so the start is nearer to production.
   [`responsibility.rs`](../../../../crates/avl-host-sys/src/proc/responsibility.rs) holds the spawn.
2. **The sample is the tree and the responsible processes.** After the gate, the run reads the process table with
   libproc. The sample holds the IDE that `idea.log` names, each descendant, and each process whose responsible process
   is the IDE. A process of another IDE names another responsible process.
3. **The group comes from the name.** A process whose name starts with `cef_server`, or a descendant of one, is JCEF.
   A process whose name starts with `com.apple.WebKit.` is WebKit. The IDE is its own group, and the rest is the other
   group.
4. **A metric is the physical footprint.** `ri_phys_footprint` of `proc_pid_rusage` is the memory that Activity Monitor
   shows. The resident size is a second metric, because it counts each shared page once per process.
5. **A doubtful sample has no WebKit metric.** When the IDE names another responsible process, the record gives no
   WebKit metric and adds a note.

## Alternatives rejected

- **Sum the process tree only.** The WebKit processes are children of launchd.
- **Take every WebKit process of the user.** Safari and the IDE of the user also have WebKit processes.
- **Match the responsible process of the controller.** The controller can run under a terminal or an IDE, and the
  WebKit processes of that app then name the same pid.
- **Start the IDE through Launch Services with `open`.** The controller then loses the exit code, the log and the
  process group of the IDE.
- **Use the `footprint` tool.** It is a subprocess for each sample, and libproc gives the same value.

## Consequences

- The privacy prompts of macOS name the Java binary of the generation, not the app of the controller. The sandbox, the
  project and the checkout of a run are outside the protected folders.
- A process that disclaims its responsibility again is not in the sample unless it descends from the IDE.
- `<run>/memory.json` holds the sample, and the digest shows it in the group `memory at the gate (MB)`.
