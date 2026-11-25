# The controller and the guest agent read only what they wrote, and each setting has one name

## Context

The host state files, the run state of the guest agent and the settings are the controller's own. No other program
writes them. A reader for an older layout costs a reader, a test and a sentence in the skill. An alias of a setting
and a refusal of a removed option cost the same. None of them protects a file that the current controller writes.

## Decisions

1. **The controller reads only what it wrote.** A host state file is private to the controller that writes it. It is
   never migrated. Every field of a lease, a lease receipt, an agent receipt, an init receipt, a `tart.pid` and a
   daemon record is required. A file that does not decode counts as damage of its kind:
   - `lease.json` is `corrupt_lease`, with its path;
   - a lease receipt is `invalid_lease_file`, and it selects no pool;
   - `guest-agent.json` means no receipt, so the agent is installed again;
   - `guest-init.json` is `guest_init_required`;
   - `tart.pid` means that the worker is not running;
   - `daemon.json` means no daemon, so the next start launches a fresh one.
2. **The guest agent reads only what it wrote.** The run state and the process identity of a worker belong to the
   installed agent. The identity is `ps -o lstart=`, because both guests answer it and the host records it.
3. **One `AIR_VM_*` name per setting.** No setting has an alias or an unprefixed name. `TART_HOME` and `TART_BIN` keep
   the names of the Tart ecosystem, and `HOME` is the system name. The
   [settings reference](../../../../../../../.agents/skills/vm-ui-tests/references/settings.md) lists the names.
4. **Every setting is read once.** `Config::load` reads the `AIR_VM_DAEMON_*` budgets with the other settings. The
   viewer, dashboard and theme switches are a `Presentation` with its own loader, because the dashboard is chosen
   before a pool is selected.
5. **A removed option is an unknown argument.** It gets the usage refusal of clap at exit 2.
6. **Some files stay a contract.** The trace viewer and the run history read the journals, so their record shape is a
   contract. A report inside its `reportSchemaVersion` stays readable, because the shard baseline and the viewer read
   older reports. The guest agent exit 64 without an envelope means an older agent.

## Alternatives rejected

- **A one-time migration.** It runs once per host, and a recycle costs one clone and one boot.
- **A warning on an old name for a while.** It keeps every alias reader alive.
- **A new runtime root, `vm-ui-tests`.** It orphans the macOS worker provenance and the golden seal.
- **Renaming `TART_BIN` too.** It splits the pair with `TART_HOME`.

## Consequences

- `pool recycle <worker>` replaces a stale worker. The controller does not repair it.
- A lock directory at a lock path is `lease_lock_failed`.
- A zero timeout fires at once. It never means no timeout.
- The runtime root keeps its directory, `~/Library/Application Support/JetBrains/macos-vm-ui-tests`. The image
  pipeline shares it. `HostOs::runtime_root` names it, and `AIR_VM_RUNTIME_ROOT` overrides it.
- A malformed daemon budget refuses every command with `invalid_environment` at load.
- An old setting name has no effect, and nothing warns.
