# yonderd

The agent of yonder on a host: macOS and Linux. `crates/yonder-link/API.md` lists the codes of `Reply::Refused`.

## The commands

| Command | Output |
|---|---|
| `serve [--port 7741]` | One JSON line `{"listening": "<address>", "fingerprint": "<64 hex>"}`, then the log on stderr. |
| `supervise <run dir> [--lock-fd <descriptor>]` | Nothing. `serve` starts it for each `Start` with the flock of the run, and it writes `exit.json` in the run directory. |
| `setup --client <fingerprint> --name <name> [--port 7741]` | One JSON line `{"fingerprint": "<64 hex>", "port": 7741, "launchd": "bootstrapped" \| "restarted" \| "none"}`. |
| `version` | One JSON line `{"version": "<cargo version>", "schema_version": 2, "features": [...]}`. |
| `dump-env <file>` | Nothing. The login shell runs it to write its environment into the file, see [The environment of a run](#the-environment-of-a-run). |

A command that refuses prints one JSON line `{"refused": {"code": "<code>", "message": "<text>"}}` on stderr and
exits with 1. A usage error exits with 2.

| Input | Code |
|---|---|
| A command line that clap refuses, such as a fingerprint that is not 64 hex digits | exit 2, no JSON |
| No `HOME` | `agent_error` |
| An identity, an allow list, a plist, a lease file or a port that the agent cannot read or write | `agent_error` |
| A failed `launchctl print`, `bootstrap`, `bootout` or `kickstart` | `launchd_failed` |

`setup` moves each lease file of `~/.cache/bt-remote/leases/` into `~/.cache/yonderd/leases/` when the agent has no
file of that holder.

## The requests

| Input | Code |
|---|---|
| A first request that is not a hello, or a second hello | `bad_request` |
| A commit that is not 40 or 64 lower-case hex digits | `bad_request` |
| More than 1024 commits in one `Have` | `bad_request` |
| A pack ref that is not `refs/yonder/<run id>` or `refs/yonder/base/<name>`, where a name has the rules of a run id | `bad_request` |
| A start ref that is not `refs/yonder/<run>` of its own run | `bad_request`, and the ref stays |
| A holder that cannot name a file (`[0-9A-Za-z._-]+`, no leading dot) | `bad_request` |
| A start with no command, or a fetch directory with a root, `.` or `..` | `bad_request` |
| A follow offset that is not 0 or the offset after a line end of the log | `bad_request` |
| A fetch or a forget of a run that goes on | `bad_request` |
| A repository path that is no directory, or that git says is no repository | `repo_missing`, with the words of git |
| A repository check that cannot run, such as a git that cannot start | `agent_error`, with the error of the start |
| A lease request for a holder whose lease file names another repository | `lease_failed`, and the file stays |
| A run whose commit the repository lacks, and whose pack does not come within 30 min, or sends no chunk for 2 min | `pack_missing`, as the end of the run |
| A `.yonder.toml` of the commit with an unknown key or a value of the wrong shape | `bad_config`, as the end of the run, before the command |
| A prepare step of `.yonder.toml` that exits with a code other than 0 | `prepare_failed`, as the end of the run, before the command |
| A port variable of `.yonder.toml` without a free port among its 100 | `agent_error`, as the end of the run, before the command |

- Each git, bd and launchctl child of the agent runs in a process group of its own. A deadline or a cancel kills the
  whole group, so a stopped `bd lease get` leaves no clone behind.
- A `Lease` or `Release` waits up to 50 s for a run of the same holder, then refuses with `lock_timeout`. A run waits
  up to an hour.
- `Lease` makes no idle sweep, because the request has no idle hours. Only a run returns the idle leases of other
  holders, after its `exit.json`, so no run waits for the sweep. The lines of the sweep go to `supervisor.log`.
- `Have`, `Lease`, `Release` and the end of `Pack` send `Reply::Heartbeat` while they wait.
- A `Start` can come before the `Pack` of its ref. The run takes the lock and the lease, then waits for the pack
  before the checkout. A commit that the repository has already needs no pack. Else the wait ends when the pack has
  landed: the pack writes `packed` in the run directory after the start ref. Each chunk of the pack writes `packing`,
  at most once a second, and a wait without a chunk for `PACK_STALL_SECONDS` ends the run, because its client is
  gone. An empty chunk counts, so a client sends one while it builds its pack. A wait longer than 1 s writes the line
  `yonder: waiting for the pack of <commit>` to the log. A cancel ends the wait too.
- A pack that comes after the end of its run makes no ref. Its objects land.
- A follow of an ended run sends `Reply::Phases` right before its `Reply::Exit` or its `Reply::Refused`. `Runs` shows
  the phases that have ended so far. The phases are `lock`, `lease`, `pack_wait`, `checkout`, `prepare`, `command` and
  `stage`, in this order. A run that ends early has only the phases up to its last step.
- The hello lists the features of the agent: `config`, `login_env`, `nice` and `changes` (`proto::feature`). An agent
  ignores a field of a start that it does not know, so a client checks the feature first.
- Right before the command the run writes one line: `yonder: running in <slot> (slot <n>): load <l> on <c> cores, nice
  <n>, <limit> open files, the login environment of <shell> from <age> ago`.
- A follow sends its heartbeats from a thread of their own, so a slow read of the run files holds none back. On macOS a
  kqueue tells the follow about a change of the log or of the run directory at once. On Linux the follow reads the
  run files every 200 ms.
- The cleanup never deletes a base ref `refs/yonder/base/<name>`. The next pack to the same name replaces it.
- `Cancel` writes the cancel request of the run and answers at once. A run that its supervisor lost ends at the next
  `Cancel`, `Follow` or cleanup, with `agent_error`.
- A refused `Start` deletes the ref of its pack, unless a kept run has the same id.
- A run that ends with a refusal shows the exit code 255 in `Runs`. A run that a cancel ends before its command starts
  ends with the exit code 130.
- A TLS handshake has 10 s in all. At most 32 handshakes go on at a time, and at most 8 of one peer address. The agent
  closes a socket over a cap at once.

## The environment of a run

launchd starts the agent with the system directories on `PATH` and an open-files limit of 256. A prepare step and the
command get more:

1. The environment of the agent.
2. The login environment of the user: the variables that the login shell of the user sets or changes, less `PWD`,
   `OLDPWD`, `SHLVL` and `_`. The shell is the one of the user database, run as `<shell> -l -i -c 'exec yonderd dump-env
   <file>'`, as a terminal tab runs it, with stdin, stdout and stderr on `/dev/null` and a deadline of 30 s. A shell
   that fails gets a second try with `-l` only. `serve` captures at its start, and a run captures again when the last
   capture is an hour old. A failed capture keeps the last one and says so in the log of the run.
   `~/.cache/yonderd/login-env.json` holds the capture.
3. `YONDER_RUN`, `YONDER_HOLDER`, `YONDER_COMMIT` and `YONDER_SLOT`, the number of the slot: the name of the parent
   directory of the slot when it is a number below 1000, as in the pool of `bd lease`, else a number below 100 from its
   path.
4. `[env]` of `.yonder.toml`.

`serve` and each supervisor raise the soft open-files limit to 65536, or to the hard limit and `kern.maxfilesperproc`
when they are lower. With `RunSpec::nice`, the prepare steps and the command run at that niceness.

## `.yonder.toml`

A repository asks the host for more with `.yonder.toml` in its root. The supervisor reads it from the commit of the run,
so a file that the slot has beside the commit never counts. An unknown key or a value of the wrong shape ends the run
with `bad_config` before the command.

```toml
# The paths that the command makes and that come back to a client that asks for them (`RunSpec::outputs`). The agent
# removes each one before the command, so only the outputs of this run come back. TOML wants top-level keys first.
outputs = ["tests/results", "playwright-report", "target/nextest/ci/junit.xml"]

# The environment of the prepare steps and the command, on top of the login environment, in the order of the names.
[env]
RUST_BACKTRACE = "1"                          # a text, as it is
PATH = { prepend = ["~/.cargo/bin"] }         # entries before (and `append`: after) the inherited value
E2E_PORT = { port = 3100 }                    # 3100 plus the number of the slot, or the next free TCP port
CI = { unset = true }                         # removed

# A step that prepares the slot. It runs before the command when its fingerprint differs from the one of its last
# success in the lease, or when a path of `creates` is missing.
[[prepare]]
name = "pnpm install"                         # the name in the log; the command line when missing
run = ["pnpm", "install", "--frozen-lockfile"]
inputs = ["pnpm-lock.yaml", "pnpm-workspace.yaml"]
creates = ["node_modules"]
```

- A path of `outputs`, `inputs` and `creates` is relative to the slot root, without `..`.
- A list entry of `prepend` and `append` that starts with `~/` is below the home directory, and a relative one is below
  the slot root, such as `node_modules/.bin`.
- The fingerprint of a step covers its `run`, and each path of `inputs` with its object id at the commit of the run: a
  file is its blob, and a directory its tree. A path that the commit lacks counts as missing. A step without `inputs`
  runs once per lease. The lease file keeps the fingerprints, so a new lease runs each step once: the agent cannot know
  what an earlier holder did to the slot.
- A step runs in the slot root, in a process group of its own, with the environment of the run. Both of its streams go
  to the log as stderr events, so the stdout of a run holds only the command. A cancel interrupts it as it does the
  command.
- An output is a file or a directory, usually one that git ignores. The agent leaves the outputs alone for a client that
  does not ask for them.

## The changes of a command

With `RunSpec::changes`, the supervisor writes the tree of the slot before the command and after it: `git add --all`
into a copy of the index of the slot (`before.index`, `after.index` in the run directory), then `git write-tree`, without
fsmonitor. The index of the slot stays as it is. The patch from the first tree to the second, `git diff-tree -p --binary
--full-index --no-renames`, goes to `changes.patch`. A command that changed nothing has no patch. The scan before the
command comes after the prepare steps, so their changes stay out of the patch.

## The fetch

A fetch sends the staged entries of the `fetch` directories (`FileKind::Staged`), then the files of each output
(`FileKind::Output` with its path), then `changes.patch` (`FileKind::Changes`). A client that asked for neither outputs
nor changes gets only the staged entries, as an older agent sends them.

## The files

Each directory of the agent has the mode 0700, and each file that it writes has the mode 0600. A staged entry keeps
the mode that it had in the slot. A copy across two file systems gives each staged file 0600, or 0700 when it is
executable. A run directory adds `outputs/` and `outputs.json` for the outputs and `changes.patch` for the changes. A
lease file adds a `prepared.<key>=<fingerprint>` line for each prepare step that succeeded in its slot.

## Upgrades

On Linux, a host needs a restart of `serve` after an upgrade of the binary. `serve` starts each supervisor from its own
executable, and Linux names a replaced executable `(deleted)`.

## The test durations

`YONDERD_TEST_SHELL` names a shell for the login environment in place of the one of the user. `none` turns the capture
off. A process test shortens these durations through the environment of `serve`, in milliseconds:

| Variable | Duration | Default |
|---|---|---|
| `YONDERD_TEST_GRACE_MS` | from SIGINT to SIGKILL of a cancelled command | 120 s |
| `YONDERD_TEST_HANDSHAKE_MS` | the total deadline of a TLS handshake | 10 s |
| `YONDERD_TEST_HEARTBEAT_MS` | between two heartbeats | `HEARTBEAT_SECONDS` |
| `YONDERD_TEST_PACK_WAIT_MS` | the wait of a run for its pack | `PACK_WAIT_SECONDS` |
| `YONDERD_TEST_PACK_STALL_MS` | the wait of a run for the next chunk of its pack | `PACK_STALL_SECONDS` |

`YONDERD_TEST_FOLLOW_HOLD` names a file. While the file exists, each follow waits before its next read of the run
files. A test holds the follow thread with it, to check that the heartbeats go on.
