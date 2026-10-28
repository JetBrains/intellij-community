# yonder: run a commit on another machine

yonder runs a command of a checkout on another machine of the user, in a warm host slot of Branch Deck there. `bt --host` runs
the committed HEAD. `yonder run <alias> -- <command>` runs the working tree, committed or not, in any repository: the
output streams back, and the exit code is the one of the command, or 75 when the host did not run it. A repository asks
the host for prepare steps, an environment and outputs with `.yonder.toml` in its root, and `--apply` brings the changes
of the command back into the working tree. The agent gives each command the login environment of the user and a raised
open-files limit. This Cargo workspace holds the client, the agent and the link that they share. Bazel builds
every crate through `rules_rs` (repository `@yonder`, declared in `community/MODULE.bazel`). `cargo` serves the
edit-test loop.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/yonder-link` | The identity files, the TLS 1.3 channel with pinned certificates, the frames, and the protocol. `API.md` lists the refusal codes. | `@community//tools/yonder/crates/yonder-link:yonder-link_test` |
| `crates/yonder-client` | The client: the host files, the address choice, a run with its thin pack, its output and its fetch, the calls of `bd host` that do its git work, `prepare` over ssh, and `upgrade` over the link. `bt --host` links it. `API.md` lists the refusal codes. | `@community//tools/yonder/crates/yonder-client:yonder-client_test` |
| `bins/yonder` | The client CLI: `run`, `attach`, `cancel`, `runs`, `fetch`, `forget`, `prepare`, `upgrade` and `skill`. `:yonder` in `BUILD.bazel` is its optimized binary. | `@community//tools/yonder/bins/yonder:yonder_test` |
| `skill` | The agent skill of yonder: `SKILL.md`. The `yonder` binary carries it, see [The agent skill](#the-agent-skill). | `@community//tools/yonder/bins/yonder:yonder_test` |
| `bins/yonderd` | The agent on a host: macOS and Linux. `serve` answers the requests, `supervise` runs one run in a host slot that it borrows, `setup` starts the agent under launchd, `upgrade` and `watchdog` replace its binary. `API.md` lists the commands, the refusals, the environment of a run and the format of `.yonder.toml`. | `@community//tools/yonder/bins/yonderd:yonderd_test`, `:yonderd_cli_test` |

## The host

A host has the agent (`yonder prepare <alias>` installs it over ssh), Branch Deck (`bd` on the login `PATH`, with the
`bd lease host` commands `have`, `ingest`, `show`, `snapshot` and `changes`, which do the git work of the agent), a
clone of each repository
under the directory of the repository of its host file, such as `~/projects/branch-deck` beside `~/projects/idea-1`, and
the tools that the commands of those repositories need on the login `PATH` of its user, such as pnpm. The agent gives
each run the login environment of the user, so a tool that a terminal of the host finds, a run finds too.

A host needs git 2.56.0 or later, the floor of Branch Deck. The keeper of the agent runs `bd lease host git --ensure`,
and Branch Deck runs `brew install git` or `brew upgrade git` once no host slot is lent. A host without Homebrew refuses
each run with `old_git`, and the hint of Branch Deck names what the user installs. "The git of the host" in
`bins/yonderd/API.md` has the steps.

## The upgrade

`yonder upgrade <alias>` updates the agent of a macOS host over the link, without ssh, so a background session can do
it. In a checkout of the monorepo it builds `@community//tools/yonder:yonderd` with Bazel, as `prepare` does, or it
sends `--agent <path>`. The agent checks the binary, keeps the old one as `<binary>.previous`, and exits, so that
launchd starts the new one. A watchdog there brings back the old binary when the new one does not listen in time. A run
goes on. An agent that knows no upgrade request refuses, and `yonder prepare` installs a new one. `bins/yonderd/API.md`
has the steps, and `crates/yonder-client/API.md` the refusals.

## The agent skill

`skill/SKILL.md` tells an agent how to use yonder in any project: the commands, who runs which one, the exit codes, the
phases line, and the refusals. `yonder skill install` writes it into `~/.claude/skills/yonder/` (`--dir` names another
directory), with the three `API.md` files of this build as `references/agent.md`, `client.md` and `link.md`. The binary
carries the files (`include_str!`), so the install works outside the monorepo. `yonder skill print` prints `SKILL.md`.

- The marker `.yonder-skill.sha256` lists the SHA-256 of each file as the install wrote it, in the format of
  `shasum -a 256`. A copy whose files match the marker comes from another build, and an install replaces it. A copy
  without a marker, or with a file that differs from it, has local edits: an install refuses with `skill_edited` and
  exit 255, and `--force` replaces it. A file that the marker does not list, such as a note of the user, stays. A
  marker holds only `SKILL.md` and `references/<name>.md`; a line with another path counts as no line.
- The build of a copy is the first 12 hex digits of the SHA-256 of its marker. An answer names the build that it
  replaced and the build of the binary. Two builds have no order: the version of the crate is the same for each
  build, and a Bazel build has no time. So the copy of the running binary always wins, and an older binary puts back
  its older skill.
- `yonder upgrade` replaces an installed copy of another build first, and says so. It installs no missing copy, and it
  keeps a copy with local edits.
- An `API.md` is a reference of the skill, so it must read on its own, without the files beside it in this source.

## Sleep

A Mac that a connection woke sleeps again when it is idle, and a running build does not count as activity. So `serve`
holds the power assertions of `caffeinate -i -s` while a client has a connection after its hello (a follow sends a
heartbeat every 15 s, so a gone client loses its connection and lets the host sleep), and each supervisor holds them
for its whole life, so a command runs to its end without its client. `-w` ends each `caffeinate` with the process it
watches. Elsewhere than on macOS nothing holds them.

## The host slots

The host keeps a fixed set of warm slots per repository (`branchdeck.hostSlots` of its git config, 4 by default), and a
run borrows one for its run only, through `bd lease host` of Branch Deck: the slot that its holder (`yonder-<hash of
the checkout path>`, `bt-remote-<hash>`, or `--holder`) used last, else the one used longest ago, else it waits in a
queue and says so once a minute. A remote slot holds no state of its own: the commit comes from the client, and the
stage carries the outputs back. So nothing on the client keeps or returns a slot, and the slot count never grows with
the checkouts. The give-back at the end of each run stops what the run left in the slot and discards its changes; the
ignored files stay as the warm cache. The keeper of the agent fills the slots and takes up what a gone run left.

## The link

The agent listens on TCP port 7741. Each end has a self-signed certificate in its identity directory, and the SHA-256
of the certificate is its fingerprint. A client checks that the agent shows the fingerprint of its host file. The
agent checks that the client shows a fingerprint of its allow list, and it reads the list at each handshake. TLS 1.3
only, on the ring provider. Every read and every write has a deadline, so no call waits for ever.

On macOS, Local Network privacy refuses a connection with `EHOSTUNREACH` when the responsible app of the client has no
permission. The client then connects through `/usr/bin/nc`, which it starts responsible for itself, so the connection
works from any terminal app or IDE. TLS stays end to end. The process remembers the address, so its later connections
take the relay at once. The `relay` and `disclaim` modules of `yonder-link` have the
details.

One connection carries one request. A run lives on the agent, not on the connection: a client that lost its
connection follows the output again from the last offset it saw.

## The gates

```sh
cargo fmt --check && cargo clippy --all-targets && cargo test      # in this directory
cd community && ./bazel.cmd test //tools/yonder/...
cd community && ./bazel.cmd test //build/rust-tools:duplicate-code_test   # no copy-paste clone in the Rust tools
```
