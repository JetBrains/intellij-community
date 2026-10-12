---
topic: testing
---

# 65. The controller reads only what it wrote

Date: 2026-09-28

## Status

Accepted. It amends [ADR 0059](0059-the-ui-lane-tooling-is-rust.md): its "Contracts kept" rows for the host state
files and for the Go agent's `RunState` and process identity are struck. The settings are listed in the skill's
`references/settings.md`.

## Context

ADR 0059 kept two contracts with programs that no longer exist. The next controller run had to read the host state
files the Go controller wrote, and the Rust agent had to read the run state a Go agent left on a warm worker. Both
were bridges for the cutover. The Go controller and the Go agent were deleted with the port, and on 2026-09-27 the
Rust controller recycled both Linux workers.

The bridges stayed in the code:

- The daemon record `HostState` kept the Go layout, reading an absent or null `guestIp` and `daemonBootStamp` as
  empty (`avl-daemon/src/state.rs`).
- `Lease`, `AgentReceipt`, `InitReceipt` and the `tart.pid` identity defaulted fields on read, so a record from
  before a field existed still decoded. A lease or a lease receipt naming no backend or guest was Tart on macOS.
- A lock directory of the pid-liveness protocol was cleared, with a line on stderr (`avl-host-sys/src/lock.rs`).
- The Parallels backend deleted the retired read/write share `air-macos-ui` whenever it touched the share set.
- The hidden `--token` and `--worker` options were refused by name (`avl-vm/src/cli.rs`).
- A zero timeout meant no timeout, as it had for the Go callers (`avl-host-sys/src/proc.rs`).
- A finished run state read without its terminal nulls got them filled in (`avl-wire/src/supervisor.rs`).
- A setting could have up to three names. `VM_*`, `PARALLELS_VM_*`, `TART_*` and `MACOS_VM_*` names stood beside the
  `AIR_VM_*` one, and some settings had only an unprefixed name (`avl-base/src/config.rs`).
- The daemon budgets and the viewer, dashboard and theme switches were read from the environment outside
  `Config`, each at its point of use.

Each bridge cost a reader, a test and a sentence in the skill. None protected a file that the Rust controller
writes.

## Decision

1. **The controller reads only what it wrote.** A host state file is private to the controller that writes it. It
   is not a contract with an older controller, and it is never migrated. Every field of a lease, a lease receipt,
   an agent receipt, an init receipt, a `tart.pid` and a daemon record is required. A file that does not decode is
   treated as its kind already treated damage:

   | file | when it does not decode |
   |---|---|
   | `lease.json` | `corrupt_lease`, naming its path |
   | a lease receipt | `invalid_lease_file`; it selects no pool, so `--backend` or the default decides |
   | `guest-agent.json` | no receipt: the agent is installed again |
   | `guest-init.json` | `guest_init_required`: a run or a pool start provisions the worker |
   | `tart.pid` | the worker is not running |
   | `daemon.json` | no daemon: the next start launches a fresh one |

2. **The guest agent reads only what it wrote.** A worker's run state and process identity are the installed
   agent's. The identity stays `ps -o lstart=` because that is what both guests answer and what the host records.
3. **One `AIR_VM_*` name per setting.** No setting has an alias, and none keeps an unprefixed name. Two exceptions
   keep the Tart ecosystem's names: `TART_HOME`, which the tart binary and the image scripts read too, and
   `TART_BIN`, the override [ADR 0158](0158-the-lane-controller-gets-tart-from-bazel.md) records. `HOME` is the
   system's.
4. **Every setting is read once.** `Config::load` reads the `AIR_VM_DAEMON_*` budgets with the other settings. The
   viewer, dashboard and theme switches are a `Presentation` with its own loader, because the dashboard is chosen
   before a pool is selected.
5. **A removed option is an unknown argument.** `--token` and `--worker` get clap's usage refusal at exit 2, like
   any unknown option. The codes `token_option_removed` and `worker_option_removed` are gone.
6. **What stays a contract.** The journals are read by the trace viewer
   ([ADR 0164](../../../../../plugins/air/docs/decisions/0164-a-vm-run-is-an-event-stream-with-a-home.md)) and by run history, so their record shape is still
   a contract. A report inside its `reportSchemaVersion` stays readable, because the shard baseline and the viewer
   read reports from earlier runs. The guest agent's exit 64 without an envelope still means an older agent,
   because a controller can meet the agent of an older checkout. The rest of ADR 0059's table stands.

## Consequences

- **A stale Tart worker is recycled, not repaired.** `pool recycle <worker>` replaces one whose files an older
  controller wrote. The macOS workers and the Parallels VM were not recycled on 2026-09-27; their agent receipts
  lack `hostRepo`, so the next lease operation installs the agent again, which is what it already did.
- **The retired Parallels share goes by hand.** A Parallels VM that still has `air-macos-ui` loses it with
  `prlctl set <vm> --shf-host-del air-macos-ui`.
- **A lock directory refuses.** A directory at a lock path is `lease_lock_failed`. Nothing writes one.
- **A zero timeout fires at once.** `SpawnOptions::timeout` is `None` for no timeout.
- **The runtime root keeps its directory.** It is still `~/Library/Application Support/JetBrains/macos-vm-ui-tests`,
  which the image pipeline shares. Only its constant, now `RUNTIME_ROOT`, and its variable change.
- **A malformed daemon budget refuses every command.** It is `invalid_environment` at load, for `pool status` and
  `trace.cmd serve` too, and a `--timeout` or `--progress-timeout` flag no longer rescues it.
- **These names no longer work, and nothing warns:**

  | old | now |
  |---|---|
  | `VM_USER` (Tart), `PARALLELS_VM_USER` (Parallels) | `AIR_VM_USER` |
  | `VM_DATA`, `PARALLELS_VM_DATA` | `AIR_VM_DATA` |
  | `VM_NODE`, `PARALLELS_VM_NODE` | `AIR_VM_NODE` |
  | `VM_UID`, `PARALLELS_VM_UID` | `AIR_VM_UID` |
  | `PARALLELS_VM_HOME`, `PARALLELS_VM_OUT`, `PARALLELS_VM_TMP`, `PARALLELS_VM_DOWNLOAD_CACHE` | `AIR_VM_HOME`, `AIR_VM_OUT`, `AIR_VM_TMP`, `AIR_VM_DOWNLOAD_CACHE` |
  | `TART_BOOT_TIMEOUT` | `AIR_VM_BOOT_TIMEOUT` |
  | `MACOS_VM_RUNTIME_ROOT`, `TART_RUNTIME_ROOT` | `AIR_VM_RUNTIME_ROOT` |
  | `MACOS_VM_HOST_REPO`, `TART_HOST_REPO` | `AIR_VM_HOST_REPO` |
  | `MACOS_VM_BAZEL_USER_ROOT`, `MACOS_VM_IMAGE_ROOT` | `AIR_VM_BAZEL_USER_ROOT`, `AIR_VM_IMAGE_ROOT` |
  | `VM_RUNS_ROOT`, `VM_SSH_HOST_KEY_FINGERPRINT` | `AIR_VM_RUNS_ROOT`, `AIR_VM_SSH_HOST_KEY_FINGERPRINT` |
  | `VM_CPU`, `VM_MEMORY_MB`, `VM_ROOT_DISK_GB` | `AIR_VM_CPU`, `AIR_VM_MEMORY_MB`, `AIR_VM_ROOT_DISK_GB` |
  | `VM_DISPLAY`, the macOS screen | `AIR_VM_RESOLUTION`; `AIR_VM_DISPLAY` stays the X display and `AIR_VM_SCREEN` the guest's Xvfb geometry |
  | `HOST_GIT` | `AIR_VM_HOST_GIT` |
  | `TART_WORKERS`, `TART_GOLDEN_VM` | `AIR_VM_WORKERS`, `AIR_VM_GOLDEN_VM` |
  | `AIR_TART_VERSION_OVERRIDE` | `AIR_VM_TART_VERSION_OVERRIDE` |
  | `PARALLELS_VM`, `PARALLELS_BIN`, `PARALLELS_PEEKABOO_BIN` | `AIR_VM_PARALLELS_VM`, `AIR_VM_PARALLELS_BIN`, `AIR_VM_PEEKABOO_BIN` |

  An exported `MACOS_VM_RUNTIME_ROOT` is the one to check first: without it the controller uses the default root,
  which on the pool host is the live pool.

## Alternatives rejected

- **A one-time migration.** It would run once per host and then never, and its tests would use fixtures from a
  program nobody can run. A recycle costs one clone and one boot, and a Linux worker holds nothing to lose.
- **A warning on an old name for a while.** The wrapper builds the controller from the checkout, and the only
  callers are this skill and the people who read it. A warning period would keep every alias reader alive.
- **A new runtime root, `vm-ui-tests`.** It would orphan every macOS worker's provenance and the golden seal, and
  move the Packer state that `provision/scripts/lib.sh` keeps in the same directory. The old name costs nothing.
- **Renaming `TART_BIN` too.** It would split the pair with `TART_HOME` and amend ADR 0158 for a name nothing else
  reads either way.
