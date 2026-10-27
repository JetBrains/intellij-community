# yonderd

The agent of yonder on a host: macOS and Linux. `crates/yonder-link/API.md` lists the codes of `Reply::Refused`.

## The commands

| Command | Output |
|---|---|
| `serve [--port 7741]` | One JSON line `{"listening": "<address>", "fingerprint": "<64 hex>"}`, then the log on stderr. |
| `supervise <run dir> [--lock-fd <descriptor>]` | Nothing. `serve` starts it for each `Start` with the flock of the run, and it writes `exit.json` in the run directory. |
| `setup --client <fingerprint> --name <name> [--port 7741]` | One JSON line `{"fingerprint": "<64 hex>", "port": 7741, "launchd": "bootstrapped" \| "restarted" \| "none"}`. |
| `version` | One JSON line `{"version": "<cargo version>", "schema_version": 2, "features": [...], "sha256": "<64 hex>"}`. The SHA-256 of the binary is its build id, because the version is the same for each build. |
| `check --plist <plist>` | The line of `version` with `"plist": "same" \| "changed" \| "unknown"`: whether `setup` of this build writes the LaunchAgent `plist` as it is. An upgrade runs it on the new binary. |
| `upgrade <binary> [--force] [--launchctl launchctl]` | One JSON line `{"previous_sha256": "<64 hex>", "sha256": "<64 hex>", "version": "<cargo version>"}`. It installs `binary` as the request does, then runs `launchctl kickstart -k gui/<uid>/com.jetbrains.yonderd`. The bootstrap run of `yonder upgrade` starts it. |
| `watchdog <binary> --sha256 <64 hex> --wait-ms <ms> [--launchctl launchctl]` | Lines in the log of the agent. An upgrade starts it, see [The upgrade](#the-upgrade). |
| `dump-env <file>` | Nothing. The login shell runs it to write its environment into the file, see [The environment of a run](#the-environment-of-a-run). |

A command that refuses prints one JSON line `{"refused": {"code": "<code>", "message": "<text>"}}` on stderr and
exits with 1. A usage error exits with 2.

| Input | Code |
|---|---|
| A command line that clap refuses, such as a fingerprint that is not 64 hex digits | exit 2, no JSON |
| No `HOME` | `agent_error` |
| An identity, an allow list, a plist, a lease file or a port that the agent cannot read or write | `agent_error` |
| A failed `launchctl print`, `bootstrap`, `bootout` or `kickstart` | `launchd_failed` |
| An `upgrade` or a `watchdog` that refuses, see [The upgrade](#the-upgrade) | `bad_binary`, `plist_changed`, `downgrade`, `upgrade_failed`, `launchd_failed` |

A run borrows a host slot of the pool of Branch Deck for its run only, through `bd lease host` (`lend`, `give-back`,
`fill`, `reclaim`; the "Host slots" of `spec/cli/lease.spec.md` in the Branch Deck repository). The bd of the host
must name `lease_host_lend` in `bd schema`; else a run refuses with `lease_failed`. The keeper of `serve` wakes once a
minute and at each `Start` of a repository: it returns each lease that a holder kept in `~/.cache/yonderd/leases/`
before the host slots (`bd lease return --confirm-preserved`, once no old supervisor holds the lock of the holder),
then runs `bd lease host reclaim`, then `bd lease host fill` once no such lease of the repository is left. A failed
fill waits 1, 5, then 15 minutes.

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
| A `Lease` request of an older client | `bad_request`: host slots are lent per run (a `Release` answers `Done`) |
| A start while the git of the host is older than the minimum and the agent cannot upgrade it, see [The git of the host](#the-git-of-the-host) | `old_git` |
| A run whose upgrade of git failed while it waited, or whose `PATH` finds a git older than the minimum or no git | `old_git`, as the end of the run, before the command |
| A run that waited for an upgrade of git for 91 min | `lock_timeout` |
| A run whose bd lacks `bd lease host`, or whose lend fails | `lease_failed` |
| A run that got no host slot within an hour | `lock_timeout` |
| A run whose commit the repository lacks, and whose pack does not come within 30 min, or sends no chunk for 2 min | `pack_missing`, as the end of the run |
| A `.yonder.toml` of the commit with an unknown key or a value of the wrong shape | `bad_config`, as the end of the run, before the command |
| A prepare step of `.yonder.toml` that exits with a code other than 0 | `prepare_failed`, as the end of the run, before the command |
| A port variable of `.yonder.toml` without a free port among its 100 | `agent_error`, as the end of the run, before the command |
| An upgrade of more than `MAX_BINARY` bytes, or a SHA-256 that is not 64 lower-case hex digits | `bad_request` |
| An upgrade whose binary has another size or SHA-256, or does not answer `check` as an agent of this schema with the feature `upgrade` | `bad_binary` |
| An upgrade whose binary writes another LaunchAgent than the one on the host | `plist_changed` |
| An upgrade to a lower version without `force` | `downgrade` |
| An upgrade on Linux, without a LaunchAgent of the shape of `setup`, while a watchdog lives, during another upgrade, or with a failed file call | `upgrade_failed` |

- Each git, bd and launchctl child of the agent runs in a process group of its own. A deadline or a cancel kills the
  whole group, so a stopped `bd lease host fill` leaves no clone behind.
- A run lends a slot with the pid of its supervisor and the holder of its spec as the affinity: the slot that holder
  used last, else the one used longest ago, else a place in the queue. While every slot is lent it asks again every
  2 s, writes `yonder: waiting for a host slot of <repo>: …` to its log at the start and once a minute, and refuses
  with `lock_timeout` after an hour. A cancel ends the wait.
- On every way out, after its `exit.json`, a run gives its slot back (`bd lease host give-back --run`): the processes
  that it left in the slot stop and its changes go; a run that got no slot leaves the queue. No signal cuts the
  give-back, and its lines go to `supervisor.log`. Nothing reads the slot after the run: the stage holds what comes
  back.
- `Have` and the end of `Pack` send `Reply::Heartbeat` while they wait.
- A `Start` can come before the `Pack` of its ref. The run borrows its slot, then waits for the pack
  before the checkout. A commit that the repository has already needs no pack. Else the wait ends when the pack has
  landed: the pack writes `packed` in the run directory after the start ref. Each chunk of the pack writes `packing`,
  at most once a second, and a wait without a chunk for `PACK_STALL_SECONDS` ends the run, because its client is
  gone. An empty chunk counts, so a client sends one while it builds its pack. A wait longer than 1 s writes the line
  `yonder: waiting for the pack of <commit>` to the log. A cancel ends the wait too.
- The checkout writes only the files that differ between the commit of the slot and the commit of the run, so its
  time follows the diff, not the tree. Branch Deck moves the slot, which it owns: `bd lease host checkout --run <id>
  --commit <id>` (`reset --merge` of the detached HEAD, no `status`, since the give-back of the last run left the slot
  clean). With a bd whose `bd schema` lacks `lease_host_checkout`, the agent runs the same `git reset --merge` itself.
  A local change or an untracked file in a path of the diff gets the forced checkout `git checkout -f --detach` instead
  (the agent's own checkout also for a slot on a branch or an unreadable HEAD), which stats every file. Then the log has
  the line `yonder: checked out with a scan of every file, because <reason>`. A cancel kills bd and its git.
- A pack that comes after the end of its run makes no ref. Its objects land.
- A follow of an ended run sends `Reply::Phases` right before its `Reply::Exit` or its `Reply::Refused`. `Runs` shows
  the phases that have ended so far. The phases are `git`, `slot`, `pack_wait`, `checkout`, `prepare`, `command` and
  `stage`, in this order. Only a run that waited for an upgrade of git has `git`. A run that ends early has only the phases up to its last step.
- The hello lists the features of the agent: `config`, `login_env`, `nice` and `changes`, and `upgrade` on macOS
  (`proto::feature`). It names the SHA-256 of the binary of the agent too. An agent
  ignores a field of a start that it does not know, so a client checks the feature first.
- Right before the command the run writes one line: `yonder: running in <slot> (slot <n>): load <l> on <c> cores, nice
  <n>, <limit> open files, git <version>, the login environment of <shell> from <age> ago`. The git is the one that the
  `PATH` of the command finds first, with the answer of `git --version`, such as `git 2.50.1 (Apple Git-155)`.
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
3. The `bin` directory of Homebrew right before `/usr/bin` in `PATH`, as in the `PATH` of the agent, see [The git of the
   host](#the-git-of-the-host). A login shell can put `/usr/bin` first again.
4. `YONDER_RUN`, `YONDER_HOLDER`, `YONDER_COMMIT` and `YONDER_SLOT`, the number of the slot: the name of the parent
   directory of the slot when it is a number below 1000, as in the pool of `bd lease`, else a number below 100 from its
   path.
5. `[env]` of `.yonder.toml`.

The run then checks the git that this `PATH` finds first, as [The git of the host](#the-git-of-the-host) says.

`serve` and each supervisor raise the soft open-files limit to 65536, or to the hard limit and `kern.maxfilesperproc`
when they are lower. With `RunSpec::nice`, the prepare steps and the command run at that niceness.

## The git of the host

The agent, bd, the prepare steps and the commands use the git that their `PATH` finds first. Each of them needs git
2.56.0 or later (`host_git::MINIMUM`).

1. At its start, before its first thread, `serve` puts the `bin` directory of Homebrew right before `/usr/bin` in its
   own `PATH`, so each child finds the git of Homebrew first: git, bd, the supervisors and Homebrew itself. Homebrew is
   `brew` on the `PATH`, else `/opt/homebrew/bin/brew`, else `/usr/local/bin/brew`. Its `bin` directory, `$(brew
   --prefix)/bin`, is the directory of `brew`. The agent looks for Homebrew on macOS only.
2. Once it listens, and before it takes the first connection, `serve` runs `git --version` of the git of its `PATH`.
   So no start comes before this check. A version such as `2.50.1 (Apple Git-155)`
   counts as `2.50.1`, and a release candidate `2.56.0.rc1` comes before `2.56.0`. An answer without a version counts
   as too old.
3. A git that is too old gets `brew install git` when the `bin` directory of Homebrew has no git, else `brew upgrade
   git`. It runs on a thread of its own, so the listener and the watchdog of an upgrade of the agent do not wait for
   Homebrew. `serve` holds the flock of `~/.cache/yonderd/git.lock` from the plan to the end of Homebrew.
4. Homebrew replaces the links of `bin/git` and can remove the old helpers of `libexec/git-core`, so it starts only
   while no run uses git. Each supervisor holds a shared flock of `~/.cache/yonderd/git-users.lock` from the end of its
   wait to the end of its process, the give-back included, and so does each fill of the keeper. A run that started
   before an upgrade of the agent counts too, because its supervisor outlives `serve`. The upgrade takes that flock
   exclusively, up to 60 min; then Homebrew starts anyway, and the log of `serve` and the log of each run that goes on
   say so. The `index-pack` and the `update-ref` of a pack take the shared flock at once, also while the upgrade waits
   for it, and go on without it while Homebrew runs, because a wait would stall the pack of the client. Other short git
   and bd calls of `serve` take no flock, so they can meet the swap of Homebrew in a short window: the git of `Have`,
   the repository check and the ref delete of a start, the cleanup thread, and `reclaim` and the lease return of the
   keeper. A failure there is a refusal of that request, or a line of the log of `serve`. Homebrew runs with
   `HOMEBREW_AUTO_UPDATE_SECS=0`, so it updates its formulae first, and `HOMEBREW_NO_INSTALL_CLEANUP=1`, so the old
   keg stays. Its deadline is 30 min.
5. A Homebrew that fails on the lock of another Homebrew process (`has already locked`, `already in progress`), such as
   the one of a `serve` that exited during its upgrade, waits 10 s. A new enough git then ends the upgrade, else
   Homebrew tries again, up to the deadline.
6. A run that starts while `git.lock` is held waits before it borrows a slot. Its log says ``yonder: waiting for the
   upgrade of git on the host: `brew upgrade git` waits for the runs that use git to end, for <s> s``, or `` `brew
   upgrade git` runs``, at the start and once a minute. The run gets the phase `git`, and a cancel ends the wait. The
   keeper of the slots does nothing while the flock is held.
7. A start refuses with `old_git` when the git stays too old: no Homebrew, a failed `brew`, a git of Homebrew that is
   still too old, or another old git before the one of Homebrew on the `PATH`. On macOS without Homebrew the message
   names the install of Homebrew, which asks for the password of an administrator, and then `brew install git`.
   Elsewhere it names a newer source of packages than the distribution, such as the git-core PPA of Ubuntu, whose
   24.04 has git 2.43, or a build from the source. A start after a refusal checks again, so a git that the user
   installed counts without a restart of the agent.

A login shell can find another git than the one of the line before the command. On macOS `bash -l` and `sh -l` run
`/etc/profile`, whose `path_helper` puts `/usr/bin` before `/opt/homebrew/bin`, so `bash -lc '…'` gets the Apple Git of
`/usr/bin`. zsh runs `path_helper` in `/etc/zprofile` too, but `~/.zprofile` of a Homebrew user runs `brew shellenv`
after it and puts Homebrew first again. On 2026-10-27 the Mac Studio gave `/opt/homebrew/bin/git` to `zsh -lc` and
to `bash -c`, and `/usr/bin/git` to `bash -lc` and `sh -lc`. So a command needs no login shell, because it has the
login environment already, or uses `zsh -lc`. A command whose program is `bash` or `sh` with `-l` or `--login` on
macOS gets the line ``yonder: `bash -l` runs /etc/profile, …`` after the line before the command
(`host_git::login_shell_warning`).

`~/.cache/yonderd/git.json` keeps the last state for the runs: `{"state": "ready" | "upgrading" | "refused",
"message", "at_ms"}`. The message names the git, the command of Homebrew, or the refusal. A run that waited reads it,
and a refused state ends the run with `old_git`.

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
# success in the slot, or when a path of `creates` is missing.
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
  runs once per slot. `~/.cache/yonderd/slots/<lease id>.prepared` keeps the fingerprints of a slot with the
  `generation` of its files that the lend answered. Branch Deck moves it with each give-back that discarded changes and
  each clean, before the slot is idle again, so a new slot, or a slot whose files were removed, runs each step once.
- A step and the command use the tools of the login environment of the host. The host provides them, as its setup
  does git and bd: a repository whose steps call pnpm needs a pnpm on the login `PATH` of the host (`brew install
  pnpm`; pnpm itself switches to the version of `packageManager`). `.yonder.toml` installs no tool of the host.
- A step runs in the slot root, in a process group of its own, with the environment of the run. Both of its streams go
  to the log as stderr events, so the stdout of a run holds only the command. A cancel interrupts it as it does the
  command.
- An output is a file or a directory, usually one that git ignores. The agent leaves the outputs alone for a client that
  does not ask for them.

## The changes of a command

With `RunSpec::changes`, the supervisor writes the tree of the slot before the command and after it, as `git add --all`
sees it, into a copy of the index of the slot (`before.index`, `after.index` in the run directory). `git status` of the
copy names the paths that differ. Up to 256 of them go through `git add --all` by their paths; with more, or when a path
went away before the add, a plain `git add --all` runs. Then `git write-tree` writes the tree. With the
fsmonitor and the untracked cache of the repository configuration, the status reads only what changed. The index of the
slot stays as it is. The patch from the first tree to the second, `git diff-tree -p --binary
--full-index --no-renames`, goes to `changes.patch`. A command that changed nothing has no patch. The scan before the
command comes after the prepare steps, so their changes stay out of the patch.

## The fetch

A fetch sends the staged entries of the `fetch` directories (`FileKind::Staged`), then the files of each output
(`FileKind::Output` with its path), then `changes.patch` (`FileKind::Changes`). A client that asked for neither outputs
nor changes gets only the staged entries, as an older agent sends them.

## The files

Each directory of the agent has the mode 0700, and each file that it writes has the mode 0600. A staged entry keeps
the mode that it had in the slot. A copy across two file systems gives each staged file 0600, or 0700 when it is
executable. A run directory adds `outputs/` and `outputs.json` for the outputs and `changes.patch` for the changes.
The fingerprints of a slot are a `generation=` line and a `prepared.<key>=<fingerprint>` line for each prepare step
that succeeded in it.

## The upgrade

`Request::Upgrade` and `yonderd upgrade <binary>` replace the binary that the first program argument of the LaunchAgent
names, usually `~/.local/bin/yonderd`. They never change the identity, the allow list or the LaunchAgent: those stay
with `setup`, so `yonder prepare`.

1. A watchdog of an earlier upgrade that still lives, as its flock on `~/.cache/yonderd/upgrade.lock` shows, refuses
   the upgrade.
2. The new binary goes to a staged file `.yonderd.upgrade-<pid>-<n>` beside it, with the mode 0700. The request checks
   its size and its SHA-256.
3. `<staged> check --plist <plist>` must answer within 10 s with the schema of this agent, the feature `upgrade`, and
   `"plist": "same"`. So a binary of another platform, a broken one, one that no later upgrade could replace, or one
   whose `setup` would write another LaunchAgent never goes in.
4. A version below the one of `<binary> version` refuses without `force`. A version that is not three numbers, or
   `0.0.0`, has no order: a Bazel build names no version.
5. The current binary stays as `<binary>.previous`, a hard link or a copy. The staged file takes the name of the binary.
6. `~/.cache/yonderd/listening.json` goes, and `yonderd watchdog` starts in a session of its own, with its lines in
   `~/Library/Logs/yonderd.log`. It runs from `<binary>.previous` when that binary has the feature `upgrade`, else from
   the new binary. A watchdog that cannot start undoes the swap.
7. The request answers `Reply::Upgraded`, waits up to `UPGRADE_DRAIN_SECONDS` for the requests that go on, less the
   follows, and exits. launchd starts the new binary, because the LaunchAgent has `KeepAlive`. The command runs
   `launchctl kickstart -k` instead.

Each `serve` writes `listening.json` once it listens: `{"sha256", "pid", "version"}` of its binary. The watchdog holds
the flock of `upgrade.lock` while it lives. It waits for the file of a live process of the new binary and a TCP
connection to the port of the LaunchAgent on the loopback address: `UPGRADE_DRAIN_SECONDS` plus
`UPGRADE_WATCHDOG_SECONDS` for a request, `UPGRADE_WATCHDOG_SECONDS` for the command. Else it renames
`<binary>.previous` back, runs `launchctl kickstart -k gui/<uid>/com.jetbrains.yonderd`, and waits as long again for a
TCP connection to the port. It writes `~/.cache/yonderd/upgrade.json`: `{"sha256", "outcome", "at_ms"}`, with the
outcome `listened`, `rolled_back` or `rolled_back_not_listening`. The check is no TLS hello, because the watchdog is no
client of the allow list, and the upgrade never changes that list. A run goes on through the restart, because its supervisor has a session of its own
and the LaunchAgent has `AbandonProcessGroup`. Its client follows again from the new `serve`.

On Linux the agent lists no feature `upgrade` and refuses the request. A host there needs `yonder prepare` and a restart
of `serve` after a new binary. `serve` starts each supervisor from its own executable, and Linux names a replaced
executable `(deleted)`.

## The test durations

`YONDERD_TEST_SHELL` names a shell for the login environment in place of the one of the user. `none` turns the capture
off. `YONDERD_TEST_BREW` names a fake Homebrew, and `none` names none. `YONDERD_TEST_GIT_MINIMUM` sets another minimum
of the git of the host, such as `0.0.0`. `YONDERD_TEST_CAFFEINATE` names the program of the power assertions in place of `/usr/bin/caffeinate`, and `none`
turns them off. A process test shortens these durations through the environment of `serve`, in milliseconds:

| Variable | Duration | Default |
|---|---|---|
| `YONDERD_TEST_GRACE_MS` | from SIGINT to SIGKILL of a cancelled command | 120 s |
| `YONDERD_TEST_HANDSHAKE_MS` | the total deadline of a TLS handshake | 10 s |
| `YONDERD_TEST_HEARTBEAT_MS` | between two heartbeats | `HEARTBEAT_SECONDS` |
| `YONDERD_TEST_PACK_WAIT_MS` | the wait of a run for its pack | `PACK_WAIT_SECONDS` |
| `YONDERD_TEST_PACK_STALL_MS` | the wait of a run for the next chunk of its pack | `PACK_STALL_SECONDS` |
| `YONDERD_TEST_UPGRADE_DRAIN_MS` | the wait of an upgraded `serve` for its other requests | `UPGRADE_DRAIN_SECONDS` |
| `YONDERD_TEST_UPGRADE_WAIT_MS` | the wait of the watchdog for the new binary, after the drain | `UPGRADE_WATCHDOG_SECONDS` |

`YONDERD_TEST_FOLLOW_HOLD` names a file. While the file exists, each follow waits before its next read of the run
files. A test holds the follow thread with it, to check that the heartbeats go on.
