# The UI-lane crates follow the re-key domains, and the audience of each binary picks its error model

## Context

The controller `vm`, the planner `air-trace`, the recorder `air-trace-record` and the guest agent `vm-guest-agent`
share one Cargo workspace. A crate is the unit that a binary links. So a change of a crate changes the bytes of each
binary that links it. The cost of a new byte depends on the binary:

- The recorder is data of every UI-lane test target. A change of its closure makes Bazel run each target again.
- The controller installs the guest agent in every worker. A change of the agent bytes makes each worker install it
  again once.
- The wrappers build `vm` and `air-trace` on every call. A change of their closures costs build time.

A crate with one consumer gave no benefit for this cost. Its seam made internals public, and it let a second binary
link the whole crate for a small part of it. The workspace also had several error models, spawns without a timeout,
two golden conventions and duplicated mechanisms. This change sets one rule for each of them.

## Decisions

1. **A crate is a re-key domain.** A crate exists when two binaries share it, or when it is the frozen-bytes engine of
   one binary with its own goldens. Code that one binary uses is a module of that binary. The
   [crate map of the workspace](../../../../README.md#crates) names the current crates and their owners.
2. **Each shipped binary pins its closure.** Each shipped binary has a `closure.txt` and a `<bin>_closure_test`. So a
   new crate in a closure is a diff that a reviewer sees.
3. **The audience of a binary picks its error model and its command line.** The
   [Rust Code Style](../../../../../../.agents/skills/rust-code-style/SKILL.md) skill states the rule for every Rust
   tool workspace.
   - The shared `refusal` crate of BT holds `Refusal`, with the exit vocabulary as a type parameter. The controller
     uses `avl_base::Exit`, `air-trace` its own closed `Exit`, and the guest agent the closed `AgentExit`.
   - A conversion maps an exit by its meaning, never by its number. So exit 6 stays a red lane of the controller.
   - Inside the runner and the backends, a typed value carries each decision, for example `ProcError` and
     `ParityError`. A refusal is built at the command edge.
   - Each binary runs through one `run` function. `main.rs` only passes the values of the process.
   - `vm`, `air-trace` and the guest agent read their command line with clap derive, and clap renders the help.
   - The lane JVM starts the recorder with no argument. So the recorder refuses any argument with exit 2.
4. **One engine dispatch.** Each question about a worker is one match on `Machine` in
   [the hypervisor module](../../../../crates/avl-vm/src/worker/hypervisor.rs). Each arm calls the body in the file of
   its backend.
5. **Every spawn names its timeout.** `SpawnOptions` has no default, and each site states its timeout. An expired
   timeout kills the process group and answers `ProcError::TimedOut`. Three spawn shapes keep no timeout, because
   something else ends them: an interactive session, the relay stream and a detached session. Every wait loop is
   `Poll` of `avl-host-sys`, with a budget and a backoff.
6. **One pinned-tool resolver.** `PinnedTools` resolves every pinned tool of a pool with one `cquery`. Each backend
   keeps its own refusal codes.
7. **One golden convention.** A golden is an `expect-test` snapshot, and `UPDATE_EXPECT=1` rewrites it. A golden that
   another side reads stays a plain file, and the test holds its output to the file with `expect_file!`. A WebP still
   is binary, so the replay compares its bytes.
8. **A fixture per test.** A test builds one fixture and shares it across its cases. A pure decision has tests of its
   own, without a fixture.
9. **The transfers move only the bytes that they need.** The
   [packing and the VM pull](../../../../spec/scenario-trace.spec.md#packing-and-the-vm-pull) state the contract.
   - A pull on Tart and Docker moves the raw bytes through the guest verb `read-file`. The receipt of the verb names
     the size and the SHA-256, and a mismatch is `pull_digest_mismatch`. A pull on Parallels stays base64.
   - The stage manifest arrives on the standard input of `stage`.
   - The hot-jar push opens one connection for each 8 MiB begun, at least 1 and at most 4.
10. **The server answers only what the site reads.** The server test writes
    [the route transcript](../../../../crates/avl-trace/testdata/serve-transcript.json) from its own answers, and the
    docs site test reads the same file. [The server](../../../../spec/scenario-trace.spec.md#the-server) states the
    contract.
11. **The workspace follows the shared configuration of
    [rust-tools](../../../../../../build/rust-tools/README.md).** `sync.mjs` writes the lint tables, `rustfmt.toml`
    and `clippy.toml`, with a width of 140 columns. A site exception is `#[expect]`, never `#[allow]`. No crate calls
    a banned method. `avl.bzl` is a thin binding of `rust_tool_crate`.

## Alternatives rejected

- **Shard the folded `vm` test.** Two shards took 73 s each against 74 s for the whole test, because libtest already
  runs the cases in parallel.
- **Keep the crates with one consumer.** A seam made internals public and let a second binary link the whole crate.
- **A third refusal type.** One shared type with an exit parameter needs no conversion between tools.
- **Base64 pulls on every backend.** Base64 adds a third more bytes and a host decode, and the receipt proves a raw
  pull.
- **Split the modules over 1 000 lines.** A split by length gives one concern two files and changes no re-key
  domain.

## Consequences

- The recorder links `avl-trace` and `avl-bridge` alone of the siblings. So only a change of those crates, of
  `avl-record` or of a third-party crate in its closure runs the UI-lane test targets again.
- A change of the guest bytes costs one agent install on each worker at its next `run`, `shard`, `flake` or
  `daemon start`.
- `closure.txt` changes only on purpose. The workspace README states the command that regenerates it.
- A crate of another workspace that a member names by path spells its `[package]` values inline. rules_rs reads its
  `Cargo.toml` without its own workspace.
