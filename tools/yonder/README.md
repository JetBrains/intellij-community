# yonder: run a commit on another machine

yonder runs a command of a checkout on another machine of the user, in a warm Branch Deck lease there. `bt --host` runs
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
| `crates/yonder-client` | The client: the host files, the address choice, a run with its thin pack, its output and its fetch, `prepare` over ssh, and `upgrade` over the link. `bt --host` links it. `API.md` lists the refusal codes. | `@community//tools/yonder/crates/yonder-client:yonder-client_test` |
| `bins/yonder` | The client CLI: `run`, `attach`, `cancel`, `runs`, `fetch`, `forget`, `lease`, `release`, `prepare` and `upgrade`. `:yonder` in `BUILD.bazel` is its optimized binary. | `@community//tools/yonder/bins/yonder:yonder_test` |
| `bins/yonderd` | The agent on a host: macOS and Linux. `serve` answers the requests, `supervise` runs one run in a warm lease, `setup` starts the agent under launchd, `upgrade` and `watchdog` replace its binary. `API.md` lists the commands, the refusals, the environment of a run and the format of `.yonder.toml`. | `@community//tools/yonder/bins/yonderd:yonderd_test`, `:yonderd_cli_test` |

## The host

A host has the agent (`yonder prepare <alias>` installs it over ssh), Branch Deck (`bd` on the login `PATH`), a clone of each repository
under the directory of the repository of its host file, such as `~/projects/branch-deck` beside `~/projects/idea-1`, and
the tools that the commands of those repositories need on the login `PATH` of its user, such as pnpm. The agent gives
each run the login environment of the user, so a tool that a terminal of the host finds, a run finds too.

## The upgrade

`yonder upgrade <alias>` updates the agent of a macOS host over the link, without ssh, so a background session can do
it. In a checkout of the monorepo it builds `@community//tools/yonder:yonderd` with Bazel, as `prepare` does, or it
sends `--agent <path>`. The agent checks the binary, keeps the old one as `<binary>.previous`, and exits, so that
launchd starts the new one. A watchdog there brings back the old binary when the new one does not listen in time. A run
goes on. An agent that knows no upgrade request gets one run that carries the binary in `yonder-upgrade/` of the
working tree, and the binary installs itself there. `bins/yonderd/API.md` has the steps, and `crates/yonder-client/API.md` the refusals.

## The warm lease

The lease holder of a run is one per local checkout path (`yonder-<hash>`, or `--holder`), so the runs of a checkout
follow each other in the same warm slot on the host, with its build outputs. The lease stays after a run.
`yonder release <alias>` returns it at once. A run of another holder returns a lease that no run used for
`--idle-hours` (8 by default).

## The link

The agent listens on TCP port 7741. Each end has a self-signed certificate in its identity directory, and the SHA-256
of the certificate is its fingerprint. A client checks that the agent shows the fingerprint of its host file. The
agent checks that the client shows a fingerprint of its allow list, and it reads the list at each handshake. TLS 1.3
only, on the ring provider. Every read and every write has a deadline, so no call waits for ever.

On macOS, Local Network privacy refuses a connection with `EHOSTUNREACH` when the responsible app of the client has no
permission. The client then connects through `/usr/bin/nc`, which it starts responsible for itself, so the connection
works from any terminal app or IDE. TLS stays end to end. The `relay` and `disclaim` modules of `yonder-link` have the
details.

One connection carries one request. A run lives on the agent, not on the connection: a client that lost its
connection follows the output again from the last offset it saw.

## The gates

```sh
cargo fmt --check && cargo clippy --all-targets && cargo test      # in this directory
cd community && ./bazel.cmd test //tools/yonder/...
cd community && ./bazel.cmd test //build/rust-tools:duplicate-code_test   # no copy-paste clone in the Rust tools
```
