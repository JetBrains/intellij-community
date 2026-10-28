---
name: yonder
description: Use to run a command, a build or a test on another machine of the user with `yonder run <host>` or `bt --host <host>`, or to follow, cancel or fetch such a run.
---

# yonder

yonder runs a command of this checkout on another machine of the user, the host. The agent `yonderd` on the host
checks out the commit in a warm Branch Deck slot and runs the command there. The output streams back, and the exit
code is the one of the command. Use it for heavy work: a long test suite, flake trials with many runs, or a measurement
that needs a quiet machine.

`yonder skill install` writes this skill into `~/.claude/skills/yonder/`. The full documents of this build are in
`references/`: `agent.md` (the agent, the environment of a run, `.yonder.toml`, the upgrade), `client.md` (the
client, its refusals, the order of a run) and `link.md` (the refusal codes of the agent).

## Who runs what

| Command | Who |
|---|---|
| `yonder run`, `attach`, `runs`, `cancel`, `fetch`, `forget`, `bt --host <host> …` | An agent, at any time. Cancel and forget only your own runs. |
| `yonder upgrade <host>` | An agent, only after the user agrees: it deploys a new agent to a machine that the user shares. |
| `yonder prepare <host>`, `bt --host <host> --prepare`, the install of Homebrew, and every `ssh` or `sudo` on the host | The user only. They use ssh, and the ssh key can need the user at the machine. |

A run needs no ssh: each end pins the certificate of the other end, so a run works from a background session too.
Never install, configure or restart the agent by hand. Tell the user what a refusal names.

## Run a command

```sh
yonder run <host> [--head] [--fetch <dir>]... [--apply] [--nice <0-19>] [--repo <path>] [--holder <name>] -- <command> <args>
```

- yonder ships the working tree as it is: HEAD plus the tracked and untracked changes that git does not ignore, as one
  commit. Your index, files and refs stay as they are. Ignored files stay local, and the host has its own.
- `--head` runs the committed HEAD only, and refuses a checkout with local changes.
- `--fetch <dir>` copies the new entries of `<dir>` back into this checkout. The path is relative to the root of the
  slot, such as `out/test-results`. Repeatable.
- `--apply` brings back the changes that the command made to tracked files, such as `cargo fmt` or new snapshots, and
  applies them with `git apply` to the working tree, not to the index. A patch that does not apply refuses with
  `apply_failed` and keeps the patch in `<git dir>/yonder/<run>.patch`.
- `--nice` is 10 by default, so the runs of `bt --host`, at the niceness of the agent, go first.
- The run goes to the repository of the same name as this one on the host, beside the main repository of the host
  file. `--repo` names another one.
- The command gets the login environment of the user on the host, 65536 open files, and `YONDER_RUN`,
  `YONDER_HOLDER`, `YONDER_COMMIT` and `YONDER_SLOT`. The tools come from the login `PATH` of the host.
- The command needs no login shell: it has the login environment already. A shell line goes through `zsh -c` or
  `zsh -lc`. On macOS never use `bash -lc` or `sh -lc`: they run `/etc/profile`, whose `path_helper` puts `/usr/bin`
  first, so the command gets the old Apple Git of `/usr/bin` and not the git of Homebrew. The run then writes a
  warning after its `running in` line.

```sh
yonder run studio --fetch out/test-results -- zsh -c 'make test > out/test-results/log.txt'
```
- A Mac host stays awake while a client talks to it and while a run goes on.

`bt --host <host> <the usual bt args>` runs a test of the monorepo the same way. bt links the client. It runs the
committed HEAD only and refuses a dirty tree. The new run directories of `out/bt-runs` come back.

### The lines of a run

- `yonder: run <run> of <commit> on <host>` comes first. Keep the run id: `attach`, `cancel` and `fetch` need it.
- `yonder: running in <slot> (slot <n>): load …, nice …, … open files, git <version>, …` comes right before the
  command. The git is the one that the `PATH` of the run finds first. A login shell of bash or sh on macOS can find
  another one, and then a warning line follows.
- `yonder: phases: …` comes at the end. Read it before you guess where the time went.

The steps of the phases line, in their order:

| Step | Where | A long step means |
|---|---|---|
| `pack`, `upload` | here | The pack of the missing commits is large: the host is far behind this history. |
| `git` | host | The host upgraded its git with Homebrew, and the run waited for the other runs and for Homebrew. Only such a run has it. |
| `slot` | host | Every slot was lent, and the run waited in the queue. |
| `pack_wait` | host | The slot was ready before the pack came. |
| `checkout` | host | A diff with many files, or a forced checkout. The log then says `checked out with a scan of every file, because …`. |
| `prepare` | host | A `[[prepare]]` step of `.yonder.toml` ran, because its inputs changed. |
| `command` | host | The command itself. |
| `stage`, `fetch` | host, here | The outputs and the fetched files are large. |
| `total` | here | The whole call. |

`yonder runs <host>` lists the runs that the host keeps, each with its steps of 0.1 s or more.

## Exit codes

| Exit | Meaning | What to do |
|---|---|---|
| The code of the command | The command ran to its end. | Read the output as for a local run. |
| 75 | The host did not run the command: a refusal before the start, such as `unreachable`, `behind` or `lease_failed`. | Run the command locally. |
| 130 | Ctrl+C before the command, or a second Ctrl+C. | Before the command, nothing ran. After a second Ctrl+C, the run ends on the host: `yonder attach <host> <run>` shows its end. |
| 255 with `unknown_run` | The run started and is gone on the host. Whether its command ran is unknown. | Never rerun it locally on your own. Tell the user. |
| 255, any other | The link broke after the start, the changes did not apply, or a request other than a run failed. | Read the message. A lost run goes on: `yonder attach`. |
| 2 | A usage error. | Fix the command line. |

`bt --host` maps a refusal of yonder to the bt exit 6 with the code `host_<code>`, and a checkout with local changes
to 2.

## A lost link, attach and cancel

The run lives on the host, not on the connection. A lost link connects again, with waits from 1 s up to 30 s, and
the output follows from the last line it showed. After 10 min without a reply the client exits with 255 and names the
command that follows the run.

- `yonder attach <host> <run>` follows a run from its first line. Ctrl+C leaves, and the run goes on.
- `yonder cancel <host> <run>` asks the host to interrupt a run. `attach` shows its end.
- Ctrl+C in `yonder run` cancels the run on the host and shows the output to its end. A second Ctrl+C leaves at once.
- `yonder fetch <host> <run> [--fetch <dir>]` copies the files of a run whose copy failed (`fetch_failed`). Run it in
  the checkout of the run: the record of the run there says which files come. A file that the run did not ask for
  never comes. Without the record, only `--fetch` directories come.
- `yonder forget <host> <run>` removes a run and its files on the host. The host keeps an ended run for 24 h.

## The host slots

The host keeps a fixed set of warm slots per repository (`branchdeck.hostSlots` of its git config, 4 by default). Each
run borrows one for that run only, through `bd lease host` of Branch Deck. The run gets the slot that its holder used
last, else the one used longest ago, else it waits in a queue with one line a minute. After an hour it refuses with
`lock_timeout`. At the end of each run the slot comes back: the processes of the run stop, and its changes go. The
ignored files stay as the warm cache. So nothing keeps or returns a slot by hand.

## Move commits, not trees

Code reaches the host only as a thin pack of the commits that the host lacks. Never rsync or scp a working tree, and
never push to the host by hand. Bring files back with `--fetch`, the outputs of `.yonder.toml`, or `--apply`.

A host that has none of the commits near HEAD refuses with `behind`, because the pack would hold the whole history.
The message names the prepare step that moves the host. Only the user runs it, such as `bt --host <host> --prepare`
in the IntelliJ monorepo.

## `.yonder.toml`

A repository asks the host for more with `.yonder.toml` in its root. The host reads it from the commit of the run.
An unknown key or a value of the wrong shape ends the run with `bad_config` before the command. A step that fails ends
it with `prepare_failed`. `references/agent.md` has the full format, in the section `.yonder.toml`.

```toml
# Paths that the command makes and that come back to this checkout. The host removes each one before the command.
# The first file of an output replaces the local file or directory of that output. An output that the run did not
# make brings nothing, so the local copy of an earlier run stays.
outputs = ["tests/results", "playwright-report"]

[env]
RUST_BACKTRACE = "1"                   # a text, as it is
PATH = { prepend = ["~/.cargo/bin"] }  # also `append`; a relative entry is below the slot root
E2E_PORT = { port = 3100 }             # 3100 plus the slot number, or the next free TCP port
CI = { unset = true }                  # removed

# Runs before the command when its inputs changed since its last success in the slot, or a path of `creates` is missing.
[[prepare]]
name = "pnpm install"
run = ["pnpm", "install", "--frozen-lockfile"]
inputs = ["pnpm-lock.yaml"]
creates = ["node_modules"]
```

`.yonder.toml` installs no tool on the host. The host provides the tools on its login `PATH`.

## Refusals

A refusal says `yonder: <code>: <message>`. Act on the code, and give the message to the user when the cause is on the
host.

| Code | Meaning |
|---|---|
| `unreachable` | No address answered. The message names each address and the name lookup. The host can sleep or be off. |
| `behind` | The host lacks the history near HEAD. The user runs the prepare step that the message names. |
| `untrusted` | The agent identity changed, or the agent does not allow this client. The user runs the prepare again. |
| `no_host` | This machine has no host file for the alias: the host was never prepared from here. |
| `old_agent` | The agent knows no upgrade request. Ask the user for `yonder prepare <host>`. |
| `lease_failed`, `lock_timeout` | The host lent no slot. Exit 75: run locally, or try again later. |
| `local_changes` | `--head` or `bt --host` with uncommitted or untracked changes. Commit first. |
| `old_git` | The git of the host is older than 2.56.0, and the agent cannot upgrade it. Exit 75: run locally. Give the message to the user: on macOS without Homebrew, the user installs Homebrew once at the host (it needs an administrator) and then runs `brew install git`. |

## The Local Network permission on macOS

macOS decides a connection to the local network by the responsible app of the process: the terminal app or the IDE
that started it. An app without the permission, or without a team ID such as a local IDE build, gets `No route to
host` for each child. The yonder client handles this itself: after a refused connect it connects through
`/usr/bin/nc`, which it starts responsible for itself. TLS stays end to end. So a run reaches the host from any app.

- A plain `nc` or `ping` from such an app fails too, so it does not tell the permission from the network.
- When the relay cannot pass the permission, `unreachable` says `macOS Local Network privacy can refuse …`. Without
  that sentence, `No route to host` is the network: the host sleeps or is off. Tell the user. Do not work around it.

## The upgrade

`yonder upgrade <host>` updates the agent of a macOS host over the link, without ssh. Run it only after the user
agrees. In a checkout of the monorepo it builds the agent with Bazel. Elsewhere it needs `--agent <path>`, a
`yonderd` binary, and without it refuses with `usage`. `--force` sends the same binary again. A watchdog on the host brings back the previous binary when the new one does
not start, within about 80 s (`rolled_back`). Runs in flight go on. A Linux host needs `yonder prepare` instead.

`yonder upgrade` also replaces this skill in `~/.claude/skills/yonder/` when the installed copy is unedited and comes
from another build, and names both builds. Two builds have no order, so an older `yonder` binary puts back its older
skill. An edited copy stays: `yonder skill install --force` replaces it.

## The files

- Here: `~/.config/yonder/identity.crt` and `identity.key`, and one host file per alias in `hosts/<alias>.json`.
- On the host: `~/.cache/yonderd/runs/<run>/` (`events.log`, `exit.json`, `supervisor.log`) and, on macOS,
  `~/Library/Logs/yonderd.log`. Read them only through the user or a run, never over ssh on your own.
