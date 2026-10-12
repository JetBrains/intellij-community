---
topic: testing
---

# 193. The start-up bench is a vm verb over a staged generation

Date: 2026-10-01

## Status

Accepted. It replaces `startup-bench` of the BT workspace, which IJPL-257337 added in `community/tools/bt/bins/startup-bench`.
[ADR 0196](0196-the-class-count-of-a-bench-run-is-the-jvm-class-load-log.md) extends it: the class count of a run is
the JVM class-load log, and the `project` arm starts the IDE on a project.
The verb and its layout are in [the vm-lane README](../../README.md), section **The bench**.
The exit codes are `./plugins/air/scripts/vm.cmd bench --help`, and the grammar of a verb is its own `--help`.

## Context

`startup-bench` measured the start-up of the IDE to the welcome screen for the IJPL-257337 work. It started the IDE
through the launcher script of `bazel run --script_path //build:idea`. That launcher links the jars of `bazel-out`
into a local home, so the running IDE read the output tree of the checkout. Several sessions share this checkout. A
build of another session during a run rewrote the jars under the running IDE, and the run then measured bytes that
nobody named.

The BT workspace has no staging, no interrupt service and no `{ok, …}` envelope. So `startup-bench` stopped the IDE
with its own `kill`, a Ctrl-C could leave an IDE behind, and its exit codes were the BT codes. The `vm` controller has
all three: `avl_host_sys` supervises each child in its own process group, `Interrupts` cancels every operation
context, and `avl_base` writes one envelope. Its daemon path already stages an immutable runtime generation for a
guest.

## Decision

**`vm bench` measures the start-up, and every IDE run starts from an immutable generation of the distribution, not
from `bazel-out`.**

1. **The verb is a module of `avl-vm`.** The crate rule of the README says that code one binary uses is a module of
   that binary. Only `vm` runs the bench, so `bench` is `crates/avl-vm/src/bench`, beside `daemon`, `lane` and
   `worker`. The readers of the IDE files moved over from the BT version with their tests and the fixture session.
2. **The session stages a generation.** It builds the self-contained `.dist` of the row, `//build:idea_dist` by
   default, and the row launcher, with the host Bazel build and without the guest targets. The key is the digest of
   the distribution tree, the dist config, `OpenedPackages.txt` and the JBR tree, through `FileDigestCache`. The
   stage clones the trees with `fscopy::clone_or_copy`, which is an APFS clone, into
   `<runtime root>/bench/generations/.stage-<digest>-<pid>`, and publishes it with one `rename` under a lock. A
   generation that exists is reused. The files are read-only. A link that leaves its tree is refused.
3. **The launch is the command line of the IDE Starter.** `IdeFromCodeInstaller` starts a locally built
   distribution as `<java> @<argfile>`: the JVM options of the distribution, the test options of the Starter, the
   opened packages, `-classpath` with `core-classpath.txt`, and `com.intellij.idea.Main`. The bench writes the same
   argfile over the paths of the generation, and adds its sandbox and instrument options and `-da`. It passes
   `-Didea.is.internal=true` where the Starter passes `false`, because the FUS validator reads the test scheme of the
   sandbox only in an internal application, and the gate reads the welcome events through that scheme. A hand-built
   launch of `PreBuiltDevMain` failed on a Linux guest ([the Linux guest notes](../vm-linux-guest.md), "What was
   tried and abandoned"), and the Starter shape is the one the perf tests run.
4. **The JBR is the one of the row launcher.** The bench reads `java` from `<launcher>.launch.json` beside the built
   binary and clones its JBR home into the generation. That is the JBR the BT version measured. The JBR archive that
   the runtime descriptor of `avl-wire` names is for a guest, not for this host.
5. **The controller supervises the IDE.** A run spawns the IDE with `Runner::checked_to_file` under a child context.
   The quit is a second JVM with the program argument `exit`. When the IDE does not exit, the run cancels the child
   context, which sends SIGTERM to the process group, and then drops the spawn, which sends SIGKILL. The spawn
   timeout is the ceiling of the run. Such a run is marked `terminated`. An interrupt cancels the session context,
   so no IDE outlives the controller.
6. **A run records its distribution and the host load.** `result.json` holds the generation digest and the load
   averages at the run start. `summary.json` holds `distDigest`. A replay refuses a run of another digest, and the
   digest shows the load per arm and warns when the load was above the CPU count.
7. **The exit codes are the `vm` codes.** A caller reads a bench exit code as it reads the code of `vm run`.

## Alternatives considered

- **Keep the tool in BT and add a staging step.** Rejected. BT would need its own interrupt service, process groups
  and envelope, which `vm` already has.
- **A crate of its own.** Rejected. One binary uses the code, so the crate rule makes it a module.
- **Run from `bazel-out` and refuse a build during a session.** Rejected. The controller cannot see a build of another
  session, so it cannot refuse one.
- **Drive `PreBuiltDevMain` or the dev launcher.** Rejected. See items 3 and 4.

## Consequences

- The first session over a build hashes the distribution, about 4 GB, and clones it. A later session over the same
  build reads only stats and reuses the generation.
- Generations stay on disk until `vm bench gc` removes them, and one lock of the host serializes the sessions and
  `gc`, because two sessions would measure each other.
- A session runs on a macOS host only. The async-profiler library of `--profile` is the macOS one, and the gate and
  the timeouts are measured there.
- A leased-worker mode, which runs the same session inside a worker, is a later step. The generation is the input
  such a mode would stage into a guest.
- `open-project` still reports no number, because the open path of a second project has no span.
- A session answers its questions from its own files on any host, without an IDE and without a script.
  `bench trace` prints the spans of one run between two anchors, with the `class` and `plugin` tags of an activity.
  `bench activities` groups the activities of one run by class and by plugin. `bench compare` sets the medians of
  several sessions against the noise of the modal arm. `bench activities` asks for a longer `--hold` when the quit
  cut an activity.
- A busy host or a stale `AIR_VM_BIN` binary shows in the session itself: as a refusal before its build, or as its
  warning.
- `bench ls` lists the sessions of the host, newest first, with names that the other verbs accept.
