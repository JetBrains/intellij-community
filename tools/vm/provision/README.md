# Tart/Packer macOS UI-test image

This directory builds the local golden VM consumed by the UI-lane controller in
`community/tools/vm`. Ordinary test runs never invoke Packer or mutate the
golden image.

## Pinned inputs

- Tart from the controller's Bazel pin (`community/tools/vm/tart.MODULE.bazel`), first on
  the scripts' `PATH`. A `TART_BIN` override must be Tart `2.32.1` or newer (`TART_MIN_VERSION`)
- checksum-verified Packer `1.16.0`
- Packer Tart plugin `1.21.0`
- Cirrus macOS Tahoe base gated by the OCI digest in `versions.env`
- macOS `26.6.2`, Node 24, and the Junie version in `versions.env`

Cirrus publishes only a moving `latest` tag for this base. `fetch-base.sh`
verifies the registry manifest digest, clones it once to a digest-named local
VM, and records a mode-0600 provenance receipt. The receipt binds the OCI
reference and digest, macOS version, and the base's disk, configuration, and
NVRAM clone-input signatures; it also records the registry upload time. Reuse
fails closed if the receipt is missing, unsafe, stale, or mismatched. Packer
clones that never-run local base, so a later tag move cannot alter the build.

The golden image contains Homebrew, Git, Node, Junie, and the Tart guest
agent. It enforces automatic login and no sleep/lock. The current build never
mounts or copies host source into the image and never supplies private
JetBrains credentials or a registry login. It does not install a checkout,
Bazel/Bazelisk, Peekaboo, or cache contents.
Codex and Pi come from the declared Bazel runtime for each test run. Neither is
an image prerequisite. An existing image can keep an unused Codex or Pi
installation. No image rebuild is required.
The inherited `admin`/`admin` login is the Cirrus image's documented public
bootstrap contract; `/etc/kcpassword` enables that public account's autologin
and is not a private JetBrains credential. Provisioning removes shell history,
npm/Homebrew/build caches, user private keys, and common CLI/cloud credential
files. The final seal requests supported user and system `tccutil` resets,
rejects MDM/PPPC policy, and fails if sensitive allowed rows remain.

The final Packer provisioner audits the image, deletes the base image's SSH
host key pairs as its final filesystem mutation, audits their absence, calls
`sync`, and returns without restarting sshd. Packer then powers the VM off over
its already-authenticated communicator; the running daemon retains its keys in
memory, so no new handshake is needed. On each worker's first boot, controller
readiness invokes `air-ensure-ssh-host-keys` over Tart/vsock, records the
fingerprint only under WorkerData, and rejects a fingerprint shared with the
other worker before normal work can begin. Tart guest-agent execution does not
depend on SSH.

Packer always writes an unapproved candidate, never the pool's golden name.
After Packer powers it off, `build-golden.sh` captures its disk,
configuration, and NVRAM signatures, makes an APFS copy-on-write disk copy,
attaches only that copy read-only, mounts its Data volume read-only, and audits
`WorkerData`, exact `.git` metadata, known credential/key and cache paths, host
keys, and copied TCC DB/WAL/SHM files. It promotes the candidate only after the
offline audit passes and the three captured clone inputs are proven unchanged.
The resulting mode-0600 golden seal binds those inputs to the base digest,
macOS version, and audit schema. It performs no registry operation; there is
still no publication command.

## Commands

Use the agent-facing controller from the repository root:

```sh
./community/tools/vm.cmd --backend tart image validate
./community/tools/vm.cmd --backend tart image build
./community/tools/vm.cmd --backend tart pool init
./community/tools/vm.cmd --backend tart pool start all
```

Every line names the backend, because this pipeline is the *sealed macOS* pool: unqualified, `image
validate` and `image build` exit 2 on the default Docker pool and the `pool` commands operate on
`air-docker-N` instead.

Both `image` commands need a working `./bazel.cmd` in this checkout. The guest half of the build is the
`provision-image` and `validate-image` verbs of the lane's guest agent, so each command builds
`@community//tools/vm:vm-guest-agent-darwin-arm64` first and hands
Packer its path - `image validate` included, because Packer's `file` provisioner stats its source while
validating. `AIR_VM_GUEST_AGENT_SOURCE` overrides that build with a hand-built binary. The image itself
still contains no checkout, no Bazel and no toolchain that builds the guest agent. Why the pipeline took that
dependency on is `../docs/decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md`.

`image build` is the only large download. The golden image remains a 50 GB
local Tart VM. A worker is an APFS copy-on-write clone of it whose sparse root
disk is expanded to `AIR_VM_ROOT_DISK_GB` (120 by default on macOS); clones are made on
demand, so `pool init` is a pre-warm rather than a prerequisite. On each boot,
`/usr/local/sbin/air-init-worker-storage` resolves `/` back to its single APFS
physical store, requires an internal root device of exactly the size the
controller passes it, grows that existing container to fill the device, and
creates `WorkerData` and `WorkerData/state` under `/Users/admin`. It has no
partition or volume erase path.

Those two directories are all it creates. **A worker has no checkout**: it runs
Bazel outputs the host built, read through the repository share, and its only
writable state is the seven directories under `WorkerData` that the VM guide
lists under *Guest-local writable state*
(`../docs/vm-ui-tests.md`). An older sealed golden still
carries a copy of this script that also made `checkout/`, `bazel-output/` and
`bazel-disk-cache/`; they are inert and nothing reads them, and the next
`image build` removes them.

`pool init` requires the safe, current golden seal and writes mode-0600
provenance binding each worker's stable disk identity to the sealed golden's
disk, configuration, and NVRAM inputs. Existing-worker reuse, start, and
readiness fail closed when either receipt is missing, unsafe, stale, or
mismatched. Start and physical-lane launch also require read-only, complete
queries of both system and user TCC databases to report no sensitive decision
or policy rows.

The host repository and Bazel output user root are mounted read-only at worker
start, and they are what a run reads: the parity layout gives the guest those
two trees at the host's own absolute paths, so a host-built test stub resolves
its runfiles unchanged. Nothing is copied and nothing is fetched. The Git
service, the snapshots and the guest Bazel tree that an earlier design used
instead are gone, along with the shared L2 cache that existed to serve them.

`versions.env` reserves
`registry.jetbrains.team/p/ij/containers-public/air-macos-ui-tests:26.6.2-v1`
as the future public golden-image reference. The local `GOLDEN_VM` remains the
only active source until parity passes; the current scripts neither pull nor
push that reference and persist no registry login.

The offline checks are deliberately fail-closed for their known paths, but a
denylist cannot prove that every unknown installed path is harmless. Any future
publication requires a separate explicit review of the complete installed-file
and package inventory and its allowlist.

Keep the Parallels backend. The VM guide's *The three guests* section says why.

## Boundaries

- The pool size is bounded by `AIR_VM_MAX_WORKERS` and, above that, by Apple's
  limit on concurrent virtual machines - which counts one that is still
  shutting down, and counts a Parallels macOS VM too.
- The persistent workers form one trusted pool for callers under the same host
  Unix account. Their `admin` homes and `WorkerData` survive leases and
  reboots; leases serialize operations but do not isolate mutually untrusted
  tenants.
- Tart's `--vnc-experimental` is diagnostic only. Normal tests use Robot
  screenshots and Remote Driver UI hierarchies and need no Peekaboo/TCC grant.
- Live TCC databases are changed only through `tccutil`; SQLite is read-only
  validation. The authoritative sealed-image audit must inspect copies of the
  system and user DB/WAL/SHM files from the powered-off image. If Cirrus grants
  survive or reappear, use a clean controlled base instead of editing TCC DBs.
- Never boot or mutate the digest-named base VM. Delete its local VM and
  provenance receipt together when approving a new digest.
