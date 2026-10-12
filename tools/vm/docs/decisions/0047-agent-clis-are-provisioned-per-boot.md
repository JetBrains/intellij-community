---
topic: testing
---

# 47. The Linux guest's agent CLIs are provisioned per boot

Date: 2026-08-28

## Status

Superseded by [ADR 0137](../../../../../plugins/air/docs/decisions/0137-agent-clis-are-declared-bazel-test-runtimes.md). It records why a Linux
worker installed the `codex` and `pi` CLIs on every boot. It also records why the macOS golden image
installed the same two at build time.
[ADR 0108](0108-the-guest-half-of-the-image-pipeline-is-go.md) owns the guest verbs the boot runs.
The mechanics and the refusal codes stay in
[`vm-linux-guest.md`](../vm-linux-guest.md) and in `guest/installagentclis.go`.

## Context

`AirDownloadConsentManagePresetsGeneratedFlowUiTest` drives a `terminal.codex` route, so it needs a
real `codex` on the guest. The macOS golden image carries both CLIs, so the class selects there. A
Linux worker is a `tart` clone of a public Ubuntu image, pinned by digest, so it has no image build
to install a CLI into. Either a boot installs the two CLIs, or the `ui` lane skips the class and
`--lane ui-real` ends in `all_tests_skipped`.

## Decision

**The Linux guest installs the two agent CLIs in per-boot provisioning.** `install-agent-clis` runs
after `stage-node` and `check-node`, as the worker account, and it runs one
`npm install --global --prefix <home>/.local`. That one invocation installs pinned `@openai/codex`
and `@earendil-works/pi-coding-agent`.

The verb guards itself on the binaries, not on a marker file. For each of `codex` and `pi` it checks
that `~/.local/bin/<name>` is a regular file, that the file carries an executable bit, and that
`--version` answers with the pin. A warm boot therefore runs no npm and fetches nothing.

The pins live in `host/config/config.go`. A test binds each const to its key in
`provision/versions.env`, so the macOS image build and the Linux boot cannot pin different builds.

### The macOS contrast serves one requirement

A macOS worker gets the same two CLIs at image-build time. Its image pipeline exists for fail-closed
TCC admission and for sealed provenance, and only an offline audit of the image can prove either.
Given that pipeline, a build-time install costs nothing more there. So the two homes are one
requirement answered on two guests, not two policies.

### Rejected: a custom published Linux image

A published image would speed only the rare cold clone. That clone pays apt plus npm, which is
roughly one to three minutes. The measured dominant first-run cost is the host Bazel analysis, at
717.7 s cold against 18.4 s warm. The [VM guide](../vm-ui-tests.md) states the same fact from
the start's side, as a 188 s cold start that a cold analysis cache turns into 799.6 s.

Three costs settle it against the saving:

- **It removes no code.** A booted worker must still prove itself, so the verb and its guard stay.
- **It adds machinery.** A Packer pipeline, seal and audit steps, and a registry publish that needs
  an inventory review.
- **It slows a pin bump.** A bump becomes rebuild, review, publish and repin, instead of a one-line
  change that the next boot applies.

### Rejected: npm's default global prefix

npm's own global prefix is the staged Node root, and the host's CLI probe never searches there. The
probe reads `$HOME/.local/bin` first, and that is also the product's own known location. So the
controller passes that prefix, and nothing else has to name the path.

## Consequences

- **A cold boot reaches registry.npmjs.org.** That is the second network reach of a boot, beside
  `apt` in `provision-guest`. Both are pinned and guarded, so a warm boot fetches nothing and a test
  run downloads nothing.
- **An offline host fails a cold clone**, at `linux_agent_cli_install_failed`. Robustness against
  that is the one reason to revisit a custom image.
- **The lane stops skipping the class.** `--lane ui` selects 24 classes and skips none of them for a
  missing CLI. A cross-guest diff still has to be read per class.
- This is test infrastructure. It changes no Air product behaviour, and it needs no behaviour-spec
  update.
