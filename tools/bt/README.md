# bt: the Bazel test wrapper

This Cargo workspace holds BT, the agent-facing wrapper over `bazel test` and `bazel build` that `community/tools/bt.cmd`
runs. Bazel
builds every crate through `rules_rs` (repository `@bt`, declared in `community/MODULE.bazel`), from the community
root and from the ultimate root. `cargo` serves the edit-test loop and rust-analyzer.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/bt-core` | The areas of `bt.json` and their lane tables, selector resolution through accio, the suite catalog, the bazel command line, the shard plan, BEP and `test.xml` reading, and the exit codes of `bt`. `fake` is the in-memory runtime the tests share. | `@community//tools/bt/crates/bt-core:bt-core_test` |
| `crates/bt-junit` | The JUnit XML reader: a scanner that keeps the cases of a truncated `test.xml`, and the simple-name class pattern. | `@community//tools/bt/crates/bt-junit:bt-junit_test` |
| `crates/refusal` | The refusal of a person- or agent-facing CLI: a stable code, a message, the exit code, and the details as JSON text. It has no dependency, and each tool keeps its own exit codes. | `@community//tools/bt/crates/refusal:refusal_test` |
| `bins/bt` | The binary: the command line and the help, one invocation end to end, the text digest, the `--json` payload, and the process boundary. | `@community//tools/bt/bins/bt:bt_test` |

`bt_rust_crate` in `defs.bzl` declares each crate: its library or binary, its `<crate>_test` and its `<crate>-clippy`.
It binds `rust_tool_crate`, the shared macro core in `community/build/rust-tools/defs.bzl`, to the `@bt` hub. bt-core
takes the slash-path rules from `distpath` and the file listing of a checkout from `checkout-files`, two crates of the
distribution tools, through path dependencies. `_CROSS_MODULE_CRATES` in `defs.bzl` maps them to their targets in this
module, so Bazel builds each once.
`optimized_binary` of the core declares a shipped binary: one file, and always optimized. `defs.bzl` re-exports it for
the Air UI-lane workspace. The spec of a Rust tool workspace is `community/build/rust-tools/README.md`.

## The binaries

`BUILD.bazel` declares `:bt`, which `community/tools/bt.cmd` runs: `@community//tools/bt:bt` from an ultimate root and
`//tools/bt:bt` from a community root. `windows_clippy_tests` of the core declares the Windows targets.
`:bt-windows-x86_64` and `:bt-windows-arm64` are compile checks you build by hand (`manual`), and
`:clippy-windows-x86_64` and `:clippy-windows-arm64` lint the `cfg(windows)` code from a Unix host.

## Areas

`bt.json` at the checkout root names each area: a repo-relative directory, and the lane table of that directory.
`crates/bt-core/src/areas.rs` and `crates/bt-core/src/lanes.rs` give the two shapes. `--lane` reads the lane tables, a
flow or a suite selector reads the suite catalog of one area, and a module inside an area runs without the migrated
list. A checkout without `bt.json` has no area: a label, a pattern, a directory, a class and a module resolve, and a
flow or a suite selector refuses. The ultimate checkout names `plugins/air`, with its lane table
`plugins/air/tests/integration/lanes.json`.

## Classes

A class name or an FQN resolves in the whole repository, through the pinned accio of the checkout:
`community/tools/accio.cmd`, or `tools/accio.cmd` in a community checkout. bt never runs an accio of the PATH.
`crates/bt-core/src/selector.rs` resolves it, and `crates/bt-core/src/accio.rs` reads the answers.

1. The committed suite documents give the classes of the UI lanes. Such a class runs in the lane of its document.
2. `accio symbol <name> --kind class --limit 200` gives the other classes. A name with more classes than the limit is
   refused: pass the FQN.
3. `accio modules --paths <file>...` gives the module root of each class. The JPS module whose test source root holds
   the file owns the class. A class outside every test source root is no test class.
4. The test target and the module rule are the ones of `--module`, below, for the owner.
5. Two classes of one simple name are refused with both FQNs and their labels.

The filter is `<FQN>[#method]`. The runner finds an `@Nested` class only through its outer class, so a nested class
without a method runs through its outer class, and a `note` line says so. bt passes no `--no-build`, so accio builds
or refreshes its index by itself. bt sets `ACCIO_WAIT` to 1800 s, because the 90 s default of accio is too short for a
slot without an index under a high load. A failed call of accio is exit 6 with the code `accio_failed`. Its message
names the last lines of the stderr and of the stdout of accio, because accio writes its error to stdout.

A package is no selector, and neither is a JPS module name: both are dotted lower-case words. Run a package with
`--module <module> --filter <package>`.

## Shards

A run over many targets passes the shard plan `out/bt/shard-plan.tsv` to the tests. The JUnit runner gives each target
its planned count and splits the classes of the target by their recorded seconds. bt records the class times of each
run in the plan. `crates/bt-core/src/shard_plan.rs` gives the rules.

Bazel forces one shard count on a whole invocation, so a planned run uses up to three `bazel test` invocations. The
first one runs heavy targets, which need more than two shards, and forces eight shards. The second runs the remaining
targets with two shards. A target without a record stays in this group. The last runs targets recorded to need one
shard, and forces one shard. `--dry-run` shows these groups as `heavy`, `argv` and `single`. They write the profiles
`out/bt/profile-heavy.json.gz`, `out/bt/profile.json.gz` and `out/bt/profile-single.json.gz`. An explicit `--shards` and
a BEP file of the caller give one invocation.

A plan entry is a hint, because the plan keeps the targets of each branch that ran in the checkout. Bazel fails each
invocation that names a target that the tree does not have, also as a negative pattern. So `bazel query` finds the
test targets of the patterns in the current tree before the first invocation. The heavy and single groups name only
those targets. A stderr line names the targets of the plan that the query does not find. A plan that names no heavy
or single-shard target of the patterns needs no query. A query file that bt cannot write leaves each target in the
second group. A query that ends without a verdict, such as after an interrupt, stops the run as INFRA. `--dry-run`
runs no query, so it shows the groups of the plan unchecked, and a `note` line says so.

A shard that hits the Bazel timeout of its target is a shard timeout. The digest names the shard and the test where it
stopped, and that test is not a failure. The `--json` payload lists the shard in `shardTimeouts`. When the times of
such a target in the run want more than two shards, bt raises its record in the plan, so the next run makes it a
heavy target.

The digest of a test run ends with a `time` line: the build of bt by `bt.cmd` (`BT_SELF_BUILD_MS`, from the build
metrics of its `bazel build`), the two reads of the check record, and the bazel commands with the analysis phase that
bazel reports. A narrow cached run is almost all of that, so the line shows where its time went without a profile. A
part of 10 s or more gets a `note` line with its cause and what to do.

A test run whose build failed counts the tests that its other invocations ran, names each target that did not build,
and keeps the output of bazel in `bazel-output.log` of a run directory under `out/bt-runs`, which a `bazel` line names.
A build error without a located diagnostic, such as a crash of the compiler, is the `ERROR:` line of the failed action:
the BUILD file, the kind of the action and its target, the exit code, and the first message of the action.

## Modules

`--module <jps module>` runs the test target of a JPS module. `crates/bt-core/src/modules.rs` resolves it. A test
target is a `jps_test`, or a `starter_test` of `//build:starter_test.bzl`, the macro that wraps `jps_test` for an IDE
Starter test. accio reads the rule kind from the text of the `BUILD.bazel`, not from `bazel query`.

1. `.idea/modules.xml` at the checkout root gives the directory of the module's `.iml`.
2. `accio modules --paths <dir>` gives the targets of the `BUILD.bazel` of that directory. The test target is the one
   whose `runtime_deps` name a library of the module or its `_test_lib`. A library belongs to the module when accio
   gives it the module: its `module_name`, or the `build <module>` section that the BUILD generator writes. One
   directory can hold several modules, so each module gets its own target, or none. A target that pins
   `JB_TEST_JUNIT5_FILTERS` runs only when the module has no unfiltered target.
3. The label is `//<dir>:<name>`. In an ultimate root, a directory under `community/` gives
   `@community//<rest>:<name>`.

The module runs when the migrated list names it, or when an area owns its directory. The migrated list is
`community/build/bazel-migrated-test-modules.txt`, or `build/bazel-migrated-test-modules.txt` in a community
checkout. `tests.cmd` reads the same list. bt refuses every other module with exit 2 and the code
`module_not_migrated`. The message gives the `./tests.cmd --module <module> --test <FQN>` command, and it gives the
label for an explicit run. A module without a target of its own gets the code `module_without_test_target` and the same
`tests.cmd` command. When the `BUILD.bazel` declares exactly one unfiltered test target, this refusal also gives the
label of that target for an explicit run.

`--filter` takes an FQN, `FQN#method` or a package, as for a label. A package is a dotted name whose segments start
with a lower-case letter. The JUnit runner reads `--test_filter` as one class name prefix or one `Class#method`, and it
reads the value only when the test JVM starts, after the build. So bt reads the value first, in `canonical_filter` of
`bt-core`. It changes a value with a clear meaning into the form of the runner, and a `note` line names the change:
`pkg.Class.method`, a simple `Class#method` (accio gives the FQN), `pkg.Outer.Inner`, and `pkg.*`. It refuses a regex, a
list and another wildcard with exit 2. For a NO TESTS run with `--filter`, the `hint` line of the digest names what the
filter selects, see `no_tests_hint`. The hint does not change the status or the exit code.

When the runner stops before its first test, Bazel writes only `exited with error code N`. The digest then adds the last
exception of the runner log and its frames.

## Builds

`bt build <pattern>...` runs `bazel build -k` over target patterns that start with `//` or `@`, with the UI of bazel
silenced. The digest is `BUILD OK`, or `BUILD FAILED` with the build errors and the targets that did not build. A
build error is a compile error, or the description of a loading or analysis failure in the BEP, such as `no such
package`. A `BUILD FAILED` test run names the same build errors, and it shows the tail of the bazel output on the
stderr when it has none. `bins/bt/src/build.rs` holds `bt build`. The exit code is 0, 5 for a build failure, or 6 for
any other exit of bazel.

Bazel exits 0 for a pattern under an ignored directory: it only warns, and it builds nothing for that pattern. So a
green build is `BUILD OK` only when the BEP names a completed target and bazel dropped no pattern. Otherwise the digest
is `BUILD EMPTY`, the exit code is 4, and the build records nothing. In the ultimate root, the root `.bazelignore`
lists `community`, so `//community/<rest>` runs as `@community//<rest>`, as a directory selector does. A test run maps
its label the same way. bt runs every pattern in the checkout root, whatever the working directory. A `//` pattern
whose directory is not in the checkout is refused with exit 2, and the message names the `@community` spelling when
the directory is under `community/`.
`--json`, `--dry-run`, `--verbose` and `--host` work as for a test run.

## The check record

A green run records the tree of the checkout that it saw, for `brd lease pick` of Branch Deck. A repository whose
`.branchdeck.toml` sets `pick_requires_check` in `[lease]` lands a lease only when a record names the tree of its HEAD.
bt is the only writer. `bins/bt/src/check.rs` holds the rules:

1. Only a linked work tree records, such as a lease of Branch Deck: its `.git` is a file. The main checkout pays one
   stat and records nothing, because no pick lands it. A host slot of `brd-host` records nothing either: the agent
   sets `BD_HOST_RUN` for its command, no pick lands the slot, and the client records the commit (rule 5).
2. The tree is `HEAD^{tree}` of a clean work tree. With local changes, it is the tree that `git add -A` would give,
   which `Runtime::checkout_tree` writes from a copy of the index. The index of the work tree stays as it was. An
   untracked nested checkout is left out, and a directory replaced by a symbolic link gives a warning and no record.
3. A local run reads the tree before its first bazel spawn and again after a green result. It records only when both
   are the same tree. A red run, a dry run and a run during which the files changed record nothing. Each read is one
   `git status --untracked-files=all`. Git keeps its untracked cache only for the mode of `status.showUntrackedFiles`,
   so in the monorepo a read takes about 1.5 s with `status.showUntrackedFiles=all` and 17-49 s without it.
4. A record of a tree with local changes names those paths on the stderr. The pick finds the record only for a commit
   of the same files, so commit or delete scratch files before the last check.
5. A `--host` run records the tree of the commit that the report of `brd-host` names as the one that ran, and the
   HEAD that the report names. That commit holds the local changes of the checkout, so rule 4 applies to it too.
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
before or after the selector. `bins/bt/src/remote.rs` starts `brd-host` of Branch Deck, and the agent `brd-host serve`
on the host does the rest. bt finds `brd-host` on the PATH, else at `~/.local/bin/brd-host`, where `brd install` links
it. Without it, bt refuses with 6 and `host_not_installed`, and the message names `brd install`. The contract of
`brd-host` is `openspec/specs/cli/host/spec.md` of Branch Deck. No step of a run uses ssh. The command line is:

```sh
brd-host run <alias> --nice 0 --no-outputs --fetch out/bt-runs [--repo <path>] --report-fd 3 -- ./community/tools/bt.cmd <args>
```

1. `brd-host` writes HEAD and the local changes as one commit, untracked files included and ignored files left out.
   The index, the files and the refs of the checkout stay as they are, and no ref names the commit. A clean checkout
   ships HEAD itself.
2. `brd-host` sends the git objects that the host lacks, as a thin pack, to the main repository of the host.
   `--host-repo` or `BT_HOST_REPO` names that repository. Without them, the host file that `brd-host setup` writes
   names it.
3. The agent checks out the commit in a host slot, and runs `./community/tools/bt.cmd` there with the same arguments,
   less `--host`. Its stderr and the lines of `brd-host` stream here as they come. The stdout of `brd-host` is the
   digest or the `--json` payload, and bt reads it. `brd-host` writes one JSON report of the run to the descriptor 3:
   the exit code of the command, the commit, the fetched files, or a refusal code. The exit code of the command is the
   exit code here. A refusal of `brd-host` is 6, with the code `host_<code>` and the exit code of `brd-host` in the
   details (`{"brd_host_exit": 75}` when the command did not run). bt never runs the command locally after a
   refusal: `heavy_timeout` and `superseded` also exit 75, and their run belongs on the host.
4. The new run directories of `out/bt-runs` come back to `out/bt-runs` here, and the digest names each local copy.
5. The run lives on the agent, not on the connection, and `brd-host` follows it again after a lost connection.
   `brd-host` stays in the process group of bt, so Ctrl+C reaches it, and bt only waits. Ctrl+C cancels the run on the
   host and shows its output to the end. A second Ctrl+C leaves at once with 6, and with `--json` it writes the payload
   of an infrastructure outcome.
6. A host that has none of the commits near HEAD would need the whole history. `brd-host` refuses that, and bt
   passes the refusal on as `host_behind` with the message of `brd-host`.

### The host slots

The host lends each run a host slot of Branch Deck for that run only: the slot that this checkout used last, else the
one used longest ago, else the run waits in a queue, one line a minute. `brd-host` names the affinity of a checkout
from its path. The host keeps a fixed set of warm slots, so two checkouts never make it clone and nothing returns a
slot by hand.

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
