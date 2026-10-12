# vm: agent rules

Read [`README.md`](README.md) first. Read
[Rust Code Style](../../.agents/skills/rust-code-style/SKILL.md) for the idioms: the error model,
the command line, the crate rule, the test layout, the lints and the banned methods.
[ADR 0074](docs/decisions/0074-the-ui-lane-crates-follow-the-re-key-domains.md) records the crate rule here.

- **Name no implementation language in a spec or in the guide.** Write "the controller", "the guest agent", "the
  recorder", "the server" and "the planner". The language belongs in the ADRs only.
- **Pass the gates from both roots.** Run `cd community && ./bazel.cmd test //tools/vm/...`. It runs the tests, the
  `<crate>-clippy` tests, the closure tests, `:clippy-linux-*` and `:clippy-windows-*`. From the ultimate root, run
  `./bazel.cmd test @community//tools/vm/... //plugins/air/tests/integration/vm-contract/...`. Then run
  `cargo fmt --check`, `cargo clippy --all-targets` and the Windows cross-target clippy of the README in this
  directory, and `bun community/build/rust-tools/sync.mjs --check`.
- **Build the Linux guest binaries after a change of code that a guest runs.** Run the two cross builds of the README.
  A Mac compiles no Linux-only code, so only these builds and `:clippy-linux-*` check it.
- **Run `./community/tools/bt.cmd AirSpecReferencesTest` after a change of a spec or a move of a file.** A spec names
  its tests by path. A spec of this workspace lives in `spec/`. The same test scans it.
- **Run `bazel run //:format.check` after a change of a Starlark file.**
- **Treat a closure change as a choice.** A new crate in a `closure.txt` changes the binary at each change of the
  crate. The recorder runs in every UI-lane target, and each worker installs a changed guest agent again. State the
  reason in the commit message, and regenerate the file with the command of the README.
- **Refresh the hub right after a change of `Cargo.lock`.** Run the command of the README from the root. Until then,
  every Bazel command of the root fails.
- **Link a crate of another workspace only when its `Cargo.toml` spells its `[package]` values inline.** rules_rs reads
  that file without its own workspace. Prove it with the fetch command of the README.
- **Run a lane smoke on a Docker worker before the git push of a guest change.** A guest change is a change of the
  guest agent, the exec channel, or the protocol of the jar push or the pull. The fakes do not model a real channel.
  The `vm-ui-tests` skill states how to run the lane.
- **Install the fixture areas in a test that reads the Air area.** Call `avl_affected::bridge::install_fixture` in
  the test or in its helper. A test that relies on the install of another test fails when it runs alone.
- **Keep clippy clean.** The policy is `community/build/rust-tools/lints.toml`. Change it there, run
  `bun community/build/rust-tools/sync.mjs`, then run it again with `--check`.
