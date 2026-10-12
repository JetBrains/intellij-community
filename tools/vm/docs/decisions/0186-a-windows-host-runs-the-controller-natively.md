---
topic: testing
---

# 186. A Windows host runs the controller natively

Date: 2026-09-30

## Status

Proposed. It becomes Accepted when a `vm.cmd run` passes on a Windows PC with a Docker engine, and the
[Consequences](#consequences) say that no such run has passed yet.

It supersedes the paragraph "Windows is not a supported host" in the Platforms section of
[ADR 0059](0059-the-ui-lane-tooling-is-rust.md). The other decisions of ADR 0059 stand. It builds on the Docker
worker of [ADR 0183](0183-a-linux-worker-may-be-a-container.md) and the image of
[ADR 0184](0184-the-worker-image-is-pulled-by-its-content-tag.md). The operator view is the "Windows" section of
[the VM guide](../vm-ui-tests.md#windows), and the steps are in
[the Windows lane plan](../vm-windows-lane-plan.md). The controller is `plugins/air/tests/integration/vm-lane`.

Amended 2026-09-30: [ADR 0190](0190-the-docker-engine-is-the-default-worker.md) moves the default. Every host now
takes `docker` with no backend, through `Selection::DEFAULT`, and `Selection::default_on` is gone.

Amended by [ADR 0074](0074-the-ui-lane-crates-follow-the-re-key-domains.md): `avl-worker` is the `worker` module of
`avl-vm`, and `avl-trace-serve` is the `serve` module of `air-trace`. So `air-trace-test` now carries the Kotlin test
data of the `avl-trace-serve-test` row, and it needs the same jars on a Windows host.

## Context

ADR 0059 made Windows an unsupported host. The backends were Tart and Parallels, which need macOS. The controller
used Unix process groups, signals, file modes and symbolic links. Only `bt`, the recorder and the crates their
tests link were built for Windows.

ADR 0183 then added the Docker worker. It is a Linux container on the host's Docker engine, and the controller
reaches it only through the `docker` CLI. The image has a `linux/arm64` and a `linux/amd64` half (ADR 0184). So a
Windows PC with a Docker engine can serve a worker in its own architecture.

The first plan was route 1 of the lane plan: the controller runs in a WSL2 Ubuntu distribution, as a Linux host.
The first Windows host that was available refused that route:

- **A Windows 11 arm64 VM in Parallels on Apple silicon has no nested virtualization.** `wsl --install` fails
  there with `HCS_E_HYPERV_NOT_INSTALLED`. Parallels KB 129234 and KB 116239 confirm that such a VM cannot run a
  hypervisor inside it.
- **So WSL2 cannot be the only route.** A PC or a VM without WSL2 must run the controller as a Windows program.

A Windows host still needs a Docker engine. That is Docker Desktop, or another engine that the `docker` CLI
reaches.

## Decision

**The controller builds, tests and runs natively on Windows, x86_64 and arm64. A Windows host drives only the
Docker backend.**

1. **The backend and the host paths.**
   - A Windows host refuses `--backend tart`, `parallels` and `linux` with `unsupported_host_backend`, exit 2.
   - With no backend, a Windows host takes `docker` (`Selection::default_on`). macOS and Linux keep the Tart Linux
     pool as the default.
   - The runtime root is `%LOCALAPPDATA%\JetBrains\air-vm-ui-tests`. The Bazel root is `C:/ProgramData/_bazel`, the
     `startup:windows` root of `community/common.bazelrc`. `HOME` falls back to `USERPROFILE`.
   - The Tart and Parallels modules of `avl-worker` are `cfg(unix)`, so they do not exist in a Windows build.
2. **Each Unix host interface has one seam in `avl-host-sys`** (commit 7c55a151ea9bb).

   | concern | Unix | Windows |
   |---|---|---|
   | a child's process tree (`proc/group.rs`) | `process_group(0)`, `killpg`, `setsid` | a Job Object with `KILL_ON_JOB_CLOSE`. A cancel sends `CTRL_BREAK` and then calls `TerminateJobObject`. A detached session starts with `DETACHED_PROCESS`, `CREATE_NEW_PROCESS_GROUP` and `CREATE_BREAKAWAY_FROM_JOB` |
   | an interrupt (`interrupt/listen.rs`) | SIGINT and SIGTERM | console control events: Ctrl-C is SIGINT (130), and Ctrl-Break, close and shutdown are SIGTERM (143) |
   | a private file or directory (`private.rs`) | mode 0600 or 0700 | a protected DACL for the owner. SYSTEM and Administrators keep access, as root does on Unix. A no-follow open refuses any reparse point. The refusal codes do not change |
   | the identity of a process (`proc/probe.rs`) | `ps` | `OpenProcess` and `GetProcessTimes` |
   | a canonical path (`fs::real_path`) | `std::fs::canonicalize` | the same, then `C:/…` for `\\?\C:\…`, the form that git and Bazel answer |
   | the owner record of a lock file | `write_all_at` at offset 0 | `seek_write` at offset 0 |

3. **The build of the Linux guest binaries works from a Windows host** (commit 2682f3f75b271). A Mac or a Linux
   host hides both faults.
   - The `rust_binary` of rules_rs also lists every dependency in `proc_macro_deps`, which Bazel analyzes in the
     exec configuration. On a Windows host the exec platform is Windows. So the library of a crate that is not
     portable carries no platform constraint and is tagged `manual`. Its test, its clippy and its binaries keep
     the constraint.
   - rules_rs keys the dependencies of a build script by the exec triple and resolves them by the target triple.
     So the build script of `openh264-sys2` got no dependencies on a Windows host. A patch first gave such a
     triple the union of the other triples. The patch is gone: the build script is off, and a `cc_library`
     compiles the sources (ADR 0185). The upstream fix is hermeticbuild/rules_rs#158, open on 2026-10-02.
4. **Host paths cross into the guest through one mapping** (commit 8c1d37cfdfd59). `GuestPaths` in
   `avl-host-sys/src/paths.rs` is a `PathMap` of `avl-wire` over the two shares, the checkout and the Bazel output
   root. Every host path that crosses into the guest goes through it: the parity layout and its probes, the stage
   manifest, the daemon launch, the runfiles variables and the Docker shares.
   - On a Unix host each root maps to itself, so macOS and Linux send the same bytes as before. On Windows a root
     maps to its drive form: `C:\Users\x\idea` becomes `/c/Users/x/idea`.
   - A path under neither share is refused as `guest_path_unmapped`.
   - A Windows prefix matches without ASCII case. Windows Bazel writes its output root in lower case: a real
     MANIFEST names `C:/programdata/_bazel/...`, and the controller knows the root as `C:/ProgramData/_bazel`.
   - The parity receipt keeps the host spelling and adds `guestRepo` and `guestBazelUserRoot`. A receipt from
     before reads as `guest_init_required`, so each worker provisions again once.
   - Docker binds each share with `--mount type=bind,...,readonly` and the data volume with `--mount type=volume`.
     The `-v` grammar split a host path at its `:`, so it refused every `C:\` path. The `--mount` grammar refuses a
     source path with `,`, `"` or `=` as `unsafe_share_path`. The create argv changed, so each container is created
     again once.
5. **The guest builds the runfiles tree** (commits 70c1259b2840c and 37acc1e2c65c6). Windows Bazel writes the
   runfiles MANIFEST of a test, and no runfiles tree.
   - `HostRunfiles` in `avl-host-sys/src/runfiles.rs` decides from the disk, not from the host OS. A
     `<descriptor>.runfiles` directory with an entry other than `MANIFEST` and `_repo_mapping` is a tree, and a
     Unix host keeps its path with no guest call. Otherwise the controller reads `<descriptor>.runfiles_manifest`,
     then `<descriptor>.runfiles/MANIFEST`. No Windows run has shown which of the two the descriptor gets.
   - The guest root is `<vmData>/runfiles/<digest>`. `avl_wire::runfiles::tree_digest` names it from the MANIFEST
     bytes and the path table, so the host knows the root before the guest builds it.
   - `Guest::ensure_runfiles_tree` calls the guest verb `runfiles-tree` in the `parity-probe` phase. The verb makes
     one link for each MANIFEST line, to the guest path of its target, and publishes the tree with one rename. A
     root other than the predicted one is refused as `guest_runfiles_mismatch`, and a bad reply as
     `guest_runfiles_protocol`.
   - The verb owns its destination. After a build or a reuse it keeps its tree and the newest other tree, for a
     daemon that may still read it. It removes older trees and the staging directory of a build that died.
6. **An x86_64 Windows host gets an amd64 guest** (commit 74118c7a93fd4). `GuestArch::of` gives `X86_64` for a
   Docker worker on any x86_64 host. The lane distribution for that guest is `idea_air_lane_dist_linux_x64`, which
   a Windows x64 host selects through `air_lane_guest_linux_x64_cross`. A Windows arm64 host gets the arm64 guest,
   as a Mac does.
7. **The wrappers build and run the binaries on Windows** (commit 9f6c4e284966f). The CMD halves of `vm.cmd` and
   `trace.cmd` do what the sh halves do, as `bt.cmd` does. A failed build exits 78.
8. **The tests run on Windows.**
   - Every controller crate is `avl_crate(portable = True)`.
   - The shell-script fakes stay on Unix. On Windows the Rust program `avl-fake` answers as `docker` and as `git`
     instead. A parity test runs about 70 calls through the script and through the port on Unix.
   - The Docker, lifecycle and lease suites run on both hosts. A suite whose fixture is a Tart or Parallels pool is
     `cfg(unix)`.

## Consequences

- **The evidence from the Windows 11 arm64 VM, with native Bazel, on the final tree.**

  | what | result |
  |---|---|
  | `:vm-guest-agent-linux-arm64` and `:air-trace-record-linux-arm64` with `--config=air-lane-linux` | both cross-build |
  | `//plugins/air/tests/integration/vm-lane/...` | 720 of 720 cases pass in 15 targets |
  | `avl-trace-serve-test` | not run. Its Kotlin test data needs jars from the private Maven repository `tbx/toolbox-dependencies`, which answers 401 without credentials. Caches seeded from the Mac did not help |
  | `vm.cmd --backend docker status` | `docker_missing`, exit 69, because that VM has no Docker engine |
  | `trace.cmd plan` | works |

  On the Mac, the same pattern passes 38 of 38 targets and 1424 cases, the Windows clippy targets included.
- **No lane has run on Windows yet.** No `vm.cmd run` has passed on a Windows PC with a Docker engine, on x86_64 or
  on arm64. So these facts are not confirmed:
  - that Docker Desktop takes `--mount source=C:\…`;
  - which MANIFEST name the daemon descriptor gets on Windows;
  - the MANIFEST branch through a real daemon start. The end-to-end daemon suites use Tart fixtures and are Unix
    only, so that branch has unit tests over fakes;
  - the lane time on a Windows host, so the guide's cost table has no Windows row;
  - the share settle. The 2 s settle of ADR 0183 is a VirtioFS measurement on OrbStack.

  This is why the status is Proposed.
- **Docker Desktop's file sharing is a performance risk.** A bind mount of a Windows path crosses from Windows into
  the engine's Linux VM, and the guest reads the IDE distribution and the test jars through it. Nothing measures
  that cost yet.
- **Docker Desktop is not free for JetBrains at company scale** (ADR 0183). The controller needs only a `docker`
  CLI that reaches an engine, so another engine works too.
- **WSL2 stays a route.** On a PC with WSL2, the controller can also run inside the distribution as a Linux host,
  as route 1 of the lane plan says. This ADR does not measure that route either.
- **A Windows host has no macOS coverage.** Tart, Parallels, `vnc`, `peekaboo` and `image` have no Windows
  counterpart. `peekaboo` and `image` refuse on a Docker worker, as they do on every host.
- **Two code paths for every seam.** Each seam has a Unix half and a Windows half, and the Windows half is tested
  only on a Windows host. The Windows clippy targets lint it from a Unix host, but they do not run it.

## Alternatives rejected

- **WSL2 as the only route.** A Windows VM without nested virtualization cannot run WSL2, and it is the host that
  was available.
- **Tart or Parallels on Windows.** Both need macOS.
- **A Windows stub for each Tart and Parallels type.** The stubs would answer only refusals, and `Config::load`
  already refuses those backends on Windows. A module that does not exist cannot drift.
- **Shell-script fakes through a Unix shell on Windows.** A test would then depend on a shell that the Windows CI
  host does not have.
