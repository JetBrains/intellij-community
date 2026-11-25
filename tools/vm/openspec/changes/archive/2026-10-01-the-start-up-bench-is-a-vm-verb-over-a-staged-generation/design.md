# The start-up bench is a vm verb, and every IDE run starts from a staged generation

## Context

`vm bench` measures the start-up of the IDE on the macOS host. It replaces the `startup-bench` tool of the BT
workspace. The README section [The bench](../../../../README.md#the-bench) is the operator view, and `vm bench --help`
is the grammar.

A launcher of `bazel run` links the jars of `bazel-out` into a local home. Several sessions share one checkout. A build
of another session can rewrite the jars under a running IDE, and the run then measures unknown bytes.

The `vm` controller already supervises each child in its own process group, cancels every operation on an interrupt,
and writes one `{ok, …}` envelope. Its daemon path already stages an immutable runtime generation for a guest.

## Decisions

1. **The verb is a module of `avl-vm`.** Only the `vm` binary runs the bench, so the crate rule of the README makes it
   the module [`bench`](../../../../crates/avl-vm/src/bench/), beside `daemon`, `lane` and `worker`.
2. **The session stages a generation.** It builds the self-contained `.dist` of the row, `//build:idea_dist` by default,
   with the host Bazel build. The key is the digest of the distribution tree, the dist config, `OpenedPackages.txt` and
   the preferences jar, through `FileDigestCache`. The stage clones the trees with `fscopy::clone_or_copy` into
   `<runtime root>/bench/generations/.stage-<digest>-<pid>`. It publishes the generation with one `rename` under a
   lock. A generation that exists is reused. The files are read-only, and a link that leaves its tree is refused.
   [`stage.rs`](../../../../crates/avl-vm/src/bench/stage.rs) holds the layout.
3. **The launch is the command line of the IDE Starter.** `IdeFromCodeInstaller` starts a local distribution as
   `<java> @<argfile>`. The bench writes the same argfile over the paths of the generation and adds its own sandbox
   and instrument options. It passes `-Didea.is.internal=true`, because the FUS validator reads the test scheme of the
   sandbox only in an internal application. The gate reads the welcome events through that scheme.
4. **The JBR is the one inside the distribution.** The dev distribution bundles the production JBR at
   `<home>/jbr/Contents/Home`. The clone of the `.dist` directory carries it, so the key needs no JBR tree of its own.
5. **The controller supervises the IDE.** A run spawns the IDE with `Runner::checked_to_file` under a child context.
   The quit is a second JVM with the program argument `exit`. When the IDE does not exit, the run cancels the child
   context, which sends SIGTERM to the process group, and then SIGKILL. The spawn timeout is the ceiling of a run, and
   such a run is `terminated`. An interrupt cancels the session context, so no IDE outlives the controller.
6. **A run records its distribution and the host load.** `result.json` holds the generation digest and the load
   averages at the run start, and `summary.json` holds `distDigest`. A replay refuses a run of another digest. The
   digest shows the load of each arm and warns when the load is above the CPU count.
7. **The exit codes are the `vm` codes.** A caller reads a bench exit code as it reads the code of `vm run`.

## Alternatives rejected

- **Keep the tool in BT and add a staging step.** BT then needs its own interrupt service, process groups and
  envelope, which `vm` already has.
- **A crate of its own.** One binary uses the code, so the crate rule makes it a module.
- **Run from `bazel-out` and refuse a build during a session.** The controller cannot see a build of another session,
  so it cannot refuse one.
- **Drive `PreBuiltDevMain` or the dev launcher.** A hand-built launch of `PreBuiltDevMain` fails on a Linux guest, and
  the perf tests run the shape of the Starter.

## Consequences

- The first session over a build hashes and clones the distribution, about 4 GB. A later session over the same build
  reads only stats and reuses the generation.
- Generations stay on disk until `vm bench gc` removes them. One lock of the host serializes the sessions and `gc`,
  because two sessions measure each other.
- A session runs on a macOS host only. The async-profiler library of `--profile` is the macOS one, and the gates and
  the timeouts are measured there.
- `open-project` reports no number, because the open path of a second project has no span.
- A session answers its questions from its own files, without an IDE. `ls`, `compare`, `classes`, `triggers`,
  `causes`, `activities`, `trace` and `replay` read a session.
- A busy host or a stale `AIR_VM_BIN` binary shows in the session itself, as a refusal before the build or as a
  warning.
