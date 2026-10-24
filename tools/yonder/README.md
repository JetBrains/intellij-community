# yonder: run a commit on another machine

yonder runs a command of the committed HEAD on another machine of the user, in a warm Branch Deck lease there. `bt
--host` is its first client. This Cargo workspace holds the client, the agent and the link that they share. Bazel builds
every crate through `rules_rs` (repository `@yonder`, declared in `community/MODULE.bazel`). `cargo` serves the
edit-test loop.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/yonder-link` | The identity files, the TLS 1.3 channel with pinned certificates, the frames, and the protocol. `API.md` lists the refusal codes. | `@community//tools/yonder/crates/yonder-link:yonder-link_test` |
| `bins/yonder` | The client CLI. | `@community//tools/yonder/bins/yonder:yonder_test` |
| `bins/yonderd` | The agent on a host: macOS and Linux. | `@community//tools/yonder/bins/yonderd:yonderd_test` |

## The link

The agent listens on TCP port 7741. Each end has a self-signed certificate in its identity directory, and the SHA-256
of the certificate is its fingerprint. A client checks that the agent shows the fingerprint of its host file. The
agent checks that the client shows a fingerprint of its allow list, and it reads the list at each handshake. TLS 1.3
only, on the ring provider. Every read and every write has a deadline, so no call waits for ever.

One connection carries one request. A run lives on the agent, not on the connection: a client that lost its
connection follows the output again from the last offset it saw.

## The gates

```sh
cargo fmt --check && cargo clippy --all-targets && cargo test      # in this directory
cd community && ./bazel.cmd test //tools/yonder/...
```
