# bt: the Bazel test wrapper

This Cargo workspace holds BT, the agent-facing wrapper over `bazel test` and `bazel build` that `community/tools/bt.cmd`
runs. Bazel
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
takes the slash-path rules from `distpath` of the dev-dist tools through a path dependency. `_CROSS_MODULE_CRATES` in
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
2. The `BUILD.bazel` of that directory gives the `jps_test` whose `runtime_deps` name a library of the module or its
   `_test_lib`. A library belongs to the module when its `module_name` is the module, or when it sits in the
   `build <module>` section that the BUILD generator writes. One directory can hold several modules, so each module
   gets its own target, or none.
3. The label is `//<dir>:<name>`. In an ultimate root, a directory under `community/` gives
   `@community//<rest>:<name>`.

The module runs when the migrated list names it, or when an area owns its directory. The migrated list is
`community/build/bazel-migrated-test-modules.txt`, or `build/bazel-migrated-test-modules.txt` in a community
checkout. `tests.cmd` reads the same list. bt refuses every other module with exit 2 and the code
`module_not_migrated`. The message gives the `./tests.cmd --module <module> --test <FQN>` command, and it gives the
label for an explicit run. A module without a target of its own gets the code `module_without_test_target` and the same
`tests.cmd` command. When the `BUILD.bazel` declares exactly one unfiltered `jps_test`, this refusal also gives the
label of that target for an explicit run. `--filter` takes an FQN, `FQN#method` or a package, as for a label.

## Builds

`bt build <pattern>...` runs `bazel build -k` over target patterns that start with `//` or `@`, with the UI of bazel
silenced. The digest is `BUILD OK`, or `BUILD FAILED` with the compile errors and the targets that did not build.
`bins/bt/src/build.rs` holds it. The exit code is 0, 5 for a build failure, or 6 for any other exit of bazel.

Bazel exits 0 for a pattern under an ignored directory: it only warns, and it builds nothing for that pattern. So a
green build is `BUILD OK` only when the BEP names a completed target and bazel dropped no pattern. Otherwise the digest
is `BUILD EMPTY`, the exit code is 4, and the build records nothing. In the ultimate root, the root `.bazelignore`
lists `community`, so `//community/<rest>` runs as `@community//<rest>`, as a directory selector does. A test run maps
its label the same way. bt runs every pattern in the checkout root, whatever the working directory. A `//` pattern
whose directory is not in the checkout is refused with exit 2, and the message names the `@community` spelling when
the directory is under `community/`.
`--json`, `--dry-run`, `--verbose` and `--host` work as for a test run.

## The check record

A green run records the tree of the checkout that it saw, for `bd lease pick` of Branch Deck. A repository whose
`.branchdeck.toml` sets `pick_requires_check` in `[lease]` lands a lease only when a record names the tree of its HEAD.
bt is the only writer. `bins/bt/src/check.rs` holds the rules:

1. Only a linked work tree records, such as a lease of Branch Deck: its `.git` is a file. The main checkout pays one
   stat and records nothing, because no pick lands it.
2. The tree is `HEAD^{tree}` of a clean work tree. With local changes, it is the tree that `git add -A` would give,
   which `Runtime::checkout_tree` writes from a copy of the index. The index of the work tree stays as it was. An
   untracked nested checkout is left out, and a directory replaced by a symbolic link gives a warning and no record.
3. A local run reads the tree before its first bazel spawn and again after a green result. It records only when both
   are the same tree. A red run, a dry run and a run during which the files changed record nothing.
4. A record of a tree with local changes names those paths on the stderr. The pick finds the record only for a commit
   of the same files, so commit or delete scratch files before the last check.
5. A `--host` run records the tree of the commit that the report of `bd-host` names as the one that ran.
6. The file is `bd-checks.jsonl` in the git directory (`git rev-parse --git-path bd-checks.jsonl`), so each linked work
   tree has its own. Each write replaces the file in one step. It keeps the last 100 records, one JSON object a line:

```json
{"schema":1,"tree":"<tree>","head":"<commit>","tool":"bt","command":"test","patterns":["//plugins/air/..."],"exit":0,"at":1793112160851}
```

`command` is `test` or `build`. `patterns` holds the target patterns of bazel, then `--filter <value>` for a test run
that took only some classes. For a `--host` run it holds the arguments of bt on the host, and that record also has
`"host":"<alias>"`. `at` is in milliseconds since the epoch. A raw
`./bazel.cmd` run records nothing, because `bazel.cmd` gives the process to bazelisk.

## Runs on another machine

`--host <alias>` runs the same command on another machine, such as `bt --host studio --lane fast`. The flag can stand
before or after the selector. `bins/bt/src/remote.rs` starts `bd-host` of Branch Deck, and the agent `bd-host serve`
on the host does the rest. bt finds `bd-host` on the PATH, else at `~/.local/bin/bd-host`, where `bd install` links
it. Without it, bt refuses with 6 and `host_not_installed`, and the message names `bd install`. The contract of
`bd-host` is `spec/cli/host.spec.md` of Branch Deck. No step of a run uses ssh. The command line is:

```sh
bd-host run <alias> --head --fetch out/bt-runs [--repo <path>] --report-fd 3 -- ./community/tools/bt.cmd <args>
```

1. `bd-host` refuses a checkout with local changes, untracked files included, because only the commit reaches the
   host.
2. `bd-host` sends the git objects that the host lacks, as a thin pack, to the main repository of the host.
   `--host-repo` or `BT_HOST_REPO` names that repository. Without them, the host file of `--prepare` names it.
3. The agent checks out the commit in a host slot, and runs `./community/tools/bt.cmd` there with the same arguments,
   less `--host`. Its stderr and the lines of `bd-host` stream here as they come. The stdout of `bd-host` is the
   digest or the `--json` payload, and bt reads it. `bd-host` writes one JSON report of the run to the descriptor 3:
   the exit code of the command, the commit, the fetched files, or a refusal code. The exit code of the command is the
   exit code here. A refusal of `bd-host` is 6, with the code `host_<code>` and the exit code of `bd-host` in the
   details (`{"bd_host_exit": 75}` when the command did not run). Local changes are 2.
4. The new run directories of `out/bt-runs` come back to `out/bt-runs` here, and the digest names each local copy.
5. The run lives on the agent, not on the connection, and `bd-host` follows it again after a lost connection.
   `bd-host` stays in the process group of bt, so Ctrl+C reaches it, and bt only waits. Ctrl+C cancels the run on the
   host and shows its output to the end. A second Ctrl+C leaves at once with 6, and with `--json` it writes the payload
   of an infrastructure outcome.
6. A host that has none of the commits near HEAD would need the whole history. bt refuses that with `host_behind`,
   and `--prepare` moves the master of the host.

### The host slots

The host lends each run a host slot of Branch Deck for that run only: the slot that this checkout used last, else the
one used longest ago, else the run waits in a queue, one line a minute. `bd-host` names the affinity of a checkout
from its path. The host keeps a fixed set of warm slots, so two checkouts never make it clone and nothing returns a
slot by hand.

### Prepare a host

`bt --host <alias> --prepare` makes a machine of the user ready for `--host` runs. The user runs it in a terminal.
Each step is idempotent and prints `ok`, `done` or `missing`. An optional tool prints `absent`. `--dry-run` changes
nothing. `bins/bt/src/prepare.rs` holds the steps, and `bins/bt/src/ssh.rs` bounds each ssh call:

1. ssh: the alias answers in batch mode. Otherwise the step names the `~/.ssh/config` settings to check
   (`AddressFamily inet`, and `IdentitiesOnly yes` only with an `IdentityFile`), and the other steps do not run.
2. The repository (`--host-repo`): a missing one is created with `feature.manyFiles`, `core.untrackedCache` and
   `core.fsmonitor=true`. Then the local master goes to `refs/heads/sync` there, and `checkout -B master sync` makes
   it the master. The step warns about the size first, because the whole history crosses the network. An existing
   master moves to the local master only when it is clean and an ancestor. An existing repository without
   `core.fsmonitor=true` gets it, and the step says what the value was. The give-back of a host slot runs one
   `status` after each run, and without fsmonitor it reads every file: 18-19 s on the Studio, against 2.5 s.
3. PATH: `~/.local/bin` goes on the PATH in `~/.zshenv`. `bd-host` lives there, and the agent reads the login
   environment through it. The host needs no `bd`, because the agent links the lease pool of Branch Deck.
4. Tools: git, Xcode, rustup, docker and tmux. The step only reports them, and it never installs one.
5. The agent: `bd-host setup <alias> --repo <repo> --name 'bt on <host name>'`. Over ssh, it copies the local
   `bd-host` to `~/.local/bin/bd-host` on the host when the host has another build, adds this client to the allow list
   of the agent, and starts the LaunchAgent `com.jetbrains.bd-host`. The host file
   `~/.config/bd-host/hosts/<alias>.json` here pins the agent. Then the agent must answer a hello, and an agent of
   another build gets the hint `bd-host upgrade <alias>`. With `--dry-run`, the step only asks the agent for its
   hello.
6. Credentials: the host must have the `~/.netrc` entries of the Bazel remote services (the hosts that `.bazelrc` and
   its imports name), the `~/.m2/settings.xml` servers of the `.idea/jarRepositories.xml` repositories on the Space
   package host, and the expiry marker of the token of each such server. `IJPrivatePackagesAuthorizer` names that
   marker by the hash of the token and keeps the expiry as its modification time, so the copy keeps that time. A local
   marker that is missing or in the past means that the local token must be renewed first. The step prints host names
   and server ids only, never a value. It copies a missing entry only after a yes at the terminal, or with `--yes`.
   Without a terminal it refuses, so an agent cannot start the copy. `credentials.rs` merges each entry into the host
   file and writes the file through the stdin of ssh, atomically and with mode 600. Then the remote cache must answer
   404, not 401.

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
cd community && ./bazel.cmd test //build/rust-tools:duplicate-code_test   # no copy-paste clone in the Rust tools
cd community/tools/bt && cargo test && cargo clippy --all-targets
./bazel.cmd test @community//tools/bt/... @community//tools/vm/...    # from the ultimate root
```

The clippy tests run only from `community/`. The clippy aspect of rules_rust skips a target of an external
repository, and from the ultimate root this module is one. There the tests are incompatible, and `bazel test` reports
them as skipped.
