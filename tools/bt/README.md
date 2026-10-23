# bt: the Bazel test wrapper

This Cargo workspace holds BT, the agent-facing wrapper over `bazel test` that `community/tools/bt.cmd` runs. Bazel
builds every crate through `rules_rs` (repository `@bt`, declared in `community/MODULE.bazel`), from the community
root and from the ultimate root. `cargo` serves the edit-test loop and rust-analyzer.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/bt-core` | The areas of `bt.json` and their lane tables, selector resolution, the suite catalog, the bazel command line, the shard plan, BEP and `test.xml` reading, and the exit codes of `bt`. `fake` is the in-memory runtime the tests share. | `@community//tools/bt/crates/bt-core:bt-core_test` |
| `crates/bt-junit` | The JUnit XML reader: a scanner that keeps the cases of a truncated `test.xml`, and the simple-name class pattern. | `@community//tools/bt/crates/bt-junit:bt-junit_test` |
| `crates/refusal` | The refusal of a person- or agent-facing CLI: a stable code, a message, the exit code, and the details as JSON text. It has no dependency, and each tool keeps its own exit codes. | `@community//tools/bt/crates/refusal:refusal_test` |
| `bins/bt` | The binary: the command line and the help, one invocation end to end, the text digest, the `--json` payload, and the process boundary. | `@community//tools/bt/bins/bt:bt_test` |

`bt_rust_crate` in `defs.bzl` declares each crate: its library or binary, its `<crate>_test` and its `<crate>-clippy`.
It binds `rust_tool_crate`, the shared macro core in `community/build/rust-tools/defs.bzl`, to the `@bt` hub. bt-core
takes the slash-path rules from `distpath` of the dev-dist tools, through a path dependency. `_CROSS_MODULE_CRATES` in
`defs.bzl` maps it to its target in this module, so Bazel builds it once.
`optimized_binary` of the core declares a shipped binary: one file, and always optimized. `defs.bzl` re-exports it for
the Air UI-lane workspace. The spec of a Rust tool workspace is `community/build/rust-tools/README.md`.

## The binaries

`BUILD.bazel` declares `:bt`, which `community/tools/bt.cmd` runs: `@community//tools/bt:bt` from an ultimate root and
`//tools/bt:bt` from a community root. `windows_clippy_tests` of the core declares the Windows targets.
`:bt-windows-x86_64` and `:bt-windows-arm64` are compile checks you build by hand (`manual`), and
`:clippy-windows-x86_64` and `:clippy-windows-arm64` lint the `cfg(windows)` code from a Unix host.

## Areas

`bt.json` at the checkout root names each area: a repo-relative directory that resolution scans, and the lane table
of that directory. `crates/bt-core/src/areas.rs` and `crates/bt-core/src/lanes.rs` give the two shapes. A checkout
without `bt.json` has no area: a label and a pattern resolve, and a name, a package, a flow or a suite selector
refuses. The ultimate checkout names `plugins/air`, with its lane table `plugins/air/tests/integration/lanes.json`.

## Shards

A run over many targets forces two shards on each target and passes the shard plan `out/bt/shard-plan.tsv` to the
tests. The JUnit runner gives each target its planned count and splits the classes of the target by their recorded
seconds. bt records the class times of each run in the plan. `crates/bt-core/src/shard_plan.rs` gives the rules. The
same run writes the Bazel profile to `out/bt/profile.json.gz`.

## Modules

`--module <jps module>` runs the `jps_test` target of a JPS module. `crates/bt-core/src/modules.rs` resolves it:

1. `.idea/modules.xml` at the checkout root gives the directory of the module's `.iml`.
2. The `BUILD.bazel` of that directory gives the `jps_test` whose `runtime_deps` name the module's library or its
   `_test_lib`. One directory can hold several modules, so each module gets its own target, or none.
3. The label is `//<dir>:<name>`. In an ultimate root, a directory under `community/` gives
   `@community//<rest>:<name>`.

The module runs when the migrated list names it, or when an area owns its directory. The migrated list is
`community/build/bazel-migrated-test-modules.txt`, or `build/bazel-migrated-test-modules.txt` in a community
checkout. `tests.cmd` reads the same list. bt refuses every other module with exit 2 and the code
`module_not_migrated`. The message gives the `./tests.cmd --module <module> --test <FQN>` command, and it gives the
label for an explicit run. A module without a target of its own gets the code `module_without_test_target` and the same
`tests.cmd` command. `--filter` takes an FQN, `FQN#method` or a package, as for a label.

## Runs on another machine

`--host <ssh alias>` runs the same command on another machine, such as `bt --host studio --lane fast`. The flag can
stand before or after the selector. `bins/bt/src/remote.rs` is the local half, and `bins/bt/src/remote.sh` is the
half that runs on the host.

1. bt refuses a checkout with local changes, untracked files included, because only the commit reaches the host.
2. bt pushes HEAD to the main repository of the host as `refs/bt-remote/<run id>`. Only the missing git objects
   travel. `--host-repo` or `BT_HOST_REPO` names that repository, and the default is `~/projects/idea-1`.
3. Over ssh, the host checks out the commit in the Branch Deck lease of this checkout, and runs
   `./community/tools/bt.cmd` there with the same arguments, less `--host`. Its stderr streams here as it comes. Its
   stdout is the digest or the `--json` payload. Its exit code is the exit code here, and an ssh failure is 6.
4. The host moves the new run directories of `out/bt-runs` to a stage. bt copies them to `out/bt-runs` here with
   `scp`, and the digest names each local copy.
5. On every way out, the host deletes the ref. When this process ends early (Ctrl+C, a kill, a lost connection), the
   host sees the end of its stdin, interrupts the bt process group, and waits for it.

### The warm lease

Each local checkout is one lease holder on the host, `bt-remote-<hash of the checkout path>`. Its first run takes a
lease with `bd lease get`, and `~/.cache/bt-remote/leases/<holder>.lease` on the host records it. Each later run checks
out its commit in the same slot, so the Bazel server and the output base of the slot stay warm. Two checkouts take two
leases. A second run of the same checkout waits until the first one ends.

`bt --host <alias> --release` returns the lease. It passes `--confirm-preserved` to `bd lease return`, which gives up
the commit in the slot: the commit came from this checkout, which keeps it. Each run also returns the lease of every
other checkout that no run used for 8 hours (`--host-idle-hours`, `BT_HOST_IDLE_HOURS`). That return first checks out
the HEAD of the main repository in the slot, so it gives up no commit.

The host needs ssh in batch mode, git, `bd` on the PATH of its login shell, and the bootstrap that the `*.cmd`
wrappers do. The first run of a checkout pays for `bd lease get` and starts a Bazel server. The first run in a new
slot also bootstraps Bazel and builds everything that the remote cache does not serve. The digest of the host names
the paths of the host, except the copied `out/bt-runs` directories.

### Prepare a host

`bt --host <alias> --prepare` makes a machine of the user ready for `--host` runs. The user runs it in a terminal.
Each step is idempotent and prints `ok`, `done` or `missing`. An optional tool prints `absent`. `--dry-run` changes
nothing. `bins/bt/src/prepare.rs` holds the steps:

1. ssh: the alias answers in batch mode. Otherwise the step names the `~/.ssh/config` settings to check
   (`AddressFamily inet`, and `IdentitiesOnly yes` only with an `IdentityFile`), and the other steps do not run.
2. The repository (`--host-repo`): a missing one is created with `feature.manyFiles`, `core.untrackedCache` and
   `core.fsmonitor=false`. Then the local master goes to `refs/heads/sync` there, and `checkout -B master sync` makes
   it the master. The step warns about the size first, because the whole history crosses the network. An existing
   master moves to the local master only when it is clean and an ancestor.
3. `bd`: the local `~/.local/bin/bd` replaces another version on a host of the same platform. `~/.local/bin` goes on
   the PATH in `~/.zshenv`.
4. Tools: git, Xcode, rustup, docker and tmux. The step only reports them, and it never installs one.
5. Credentials: the host must have the `~/.netrc` entries of the Bazel remote services (the hosts that `.bazelrc` and
   its imports name), the `~/.m2/settings.xml` servers of the `.idea/jarRepositories.xml` repositories on the Space
   package host, and the expiry marker of the token of each such server. `IJPrivatePackagesAuthorizer` names that
   marker by the hash of the token and keeps the expiry as its modification time, so the copy keeps that time. A local
   marker that is missing or in the past means that the local token must be renewed first. The step prints host names
   and server ids only, never a value. It copies a missing entry only after a yes at the terminal, or with `--yes`. Without a
   terminal it refuses, so an agent cannot start the copy. `credentials.rs` merges each entry into the host file and
   writes the file through the stdin of ssh, atomically and with mode 600. Then the remote cache must answer 404, not
   401.
6. `--take-lease` takes the warm lease of this checkout, so that the first run does not wait for `bd lease get`.

A run without `--prepare` copies no credential.

## Links into the Air UI-lane workspace

The Air UI-lane workspace, `community/tools/vm`, links crates of this workspace through path
dependencies. `avl.bzl` there maps each such crate to its label in this module (`_CROSS_MODULE_CRATES`), so Bazel
builds it once. rules_rs reads the `[package]` and `[dependencies]` of such a crate without this workspace. The same
holds for a crate of this workspace that such a crate links, such as `refusal`. So each of these crates spells each
`[package]` field and each normal dependency inline. Keep the values equal to `[workspace.package]` and
`[workspace.dependencies]`.

That build links this workspace's `serde`, `serde_json` and `regex`, which are other crates than the ones the Air
workspace links. Cargo unifies them, so only Bazel shows the difference. A public API of a linked crate therefore
hands a value across as JSON text (`Refusal::details_json_text`, `Affected::to_json_text`, `LaneCount::to_json_text`),
and takes no `regex::Regex` and no `serde_json::Value` from its caller.

## Verification

```sh
cd community && ./bazel.cmd test //tools/bt/...
cd community/tools/bt && cargo test && cargo clippy --all-targets
./bazel.cmd test @community//tools/bt/... @community//tools/vm/...    # from the ultimate root
```

The clippy tests run only from `community/`. The clippy aspect of rules_rust skips a target of an external
repository, and from the ultimate root this module is one. There the tests are incompatible, and `bazel test` reports
them as skipped.
