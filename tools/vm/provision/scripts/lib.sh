#!/bin/zsh

set -euo pipefail

AIR_PROPOSAL_ROOT="${0:A:h:h}"
# shellcheck disable=SC1091
source "$AIR_PROPOSAL_ROOT/versions.env"

# `macos-vm-ui-tests` is the on-disk name of the controller state directory, kept after the skill was
# renamed to `vm-ui-tests`; it must agree with `RUNTIME_ROOT` in crates/avl-base/src/config.rs of the
# UI-lane controller, whose own copy of the name is pinned by `the_runtime_root_keeps_its_on_disk_name`.
AIR_TART_STATE_DIR="${AIR_TART_STATE_DIR:-$HOME/Library/Application Support/JetBrains/macos-vm-ui-tests/provision}"
AIR_PACKER_BIN="${AIR_PACKER_BIN:-$AIR_TART_STATE_DIR/tools/packer}"
# The checkout that holds this directory, which is what the image build's guest half is built from. Four levels,
# because `provision/` sits at `community/tools/vm/provision` below the root of the checkout.
AIR_REPO_ROOT="${AIR_REPO_ROOT:-${AIR_PROPOSAL_ROOT:h:h:h:h}}"
export PACKER_PLUGIN_PATH="${PACKER_PLUGIN_PATH:-$AIR_TART_STATE_DIR/packer-plugins}"
# Tart clone otherwise prunes up to 100 GB of unrelated local VMs when it
# needs space. Capacity failures must be explicit in this workflow.
export TART_NO_AUTO_PRUNE=1

die() {
  print -u2 -- "error: $*"
  exit 1
}

note() {
  print -- "==> $*"
}

warn() {
  print -u2 -- "warning: $*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

is_apfs_path() {
  local target_path="$1"
  local filesystem
  filesystem="$(/bin/df "$target_path" | /usr/bin/awk 'NR == 2 {print $1}')"
  /sbin/mount | /usr/bin/awk -v filesystem="$filesystem" '
    $1 == filesystem && $0 ~ /\(apfs,/ { found = 1 }
    END { exit(found ? 0 : 1) }
  '
}

# The label that builds the guest agent whose `provision-image` and `validate-image` verbs are the image
# build's two guest provisioners.
#
# Its other home is `agent_label` in crates/avl-host-sys/src/guest/agent.rs of the UI-lane controller, which
# installs the same binary into every worker. Unlike the Linux base pin in versions.env, the two copies cannot
# quietly disagree: a renamed target makes the `bazel build` below fail and print the label it could not find.
AIR_GUEST_AGENT_LABEL='@community//tools/vm:vm-guest-agent-darwin-arm64'

# Where resolve_guest_agent_binary leaves its answer. A variable and not a printed value, because the
# resolution reports progress through `note`, and `note` writes to stdout: a printed answer and a printed note
# would reach the caller as one string.
AIR_GUEST_AGENT_BINARY=""

# Bazel, run from the checkout. The `cd` is the whole point: nothing here guarantees the caller's working
# directory is inside the workspace - `build-golden.sh` changes it to `provision/` for Packer - and a subshell
# keeps that from being one more thing the next function has to know.
air_bazel() {
  (cd "$AIR_REPO_ROOT" && ./bazel.cmd "$@")
}

# resolve_guest_agent_binary builds the macOS guest agent and records where its bytes are.
#
# The prerequisite this adds is stated here because here is where it fails: `image validate` and `image build`
# now need a working `./bazel.cmd` in this checkout, `validate` and not only `build` - Packer's `file`
# provisioner stats its source during `packer validate`. Why the pipeline took that dependency on is ADR 0108.
#
# Builds, where the controller's own `resolve_agent_binary` deliberately never does: that one runs under a
# worker's lease-operation lock, where every other step costs seconds. Here there is no lock and the caller is
# about to spend minutes in Packer and, on a cold base, hours in the pull - so a warm build's seconds are free,
# and the alternative is a refusal whose only remedy is the command this would have run.
#
# `cquery --output=starlark` and not `--output=files`, and not `out/bazel-bin` either. What `cquery` prints is
# relative to the execution root, and it is a *symlink* into the transitioned output directory - so the check
# below follows it, as Packer's upload does, which is what makes the uploaded bytes and the checked file the
# same file. crates/avl-host-sys/src/guest/agent.rs documents each reading; ADR 0108's trap index says what each
# one cost.
resolve_guest_agent_binary() {
  # Declared before assignment on purpose: `local x="$(cmd)"` hides a failing command substitution's status
  # from `set -e`, and a Bazel failure has to stop the build rather than resolve to an empty path.
  local relative execution_root

  # The variable the controller already reads for this exact purpose - a hand-built agent instead of Bazel's -
  # and deliberately not a second name for it. An operator who set only one of two names would drive the
  # workers with one binary and build the image with another.
  if [[ -n "${AIR_VM_GUEST_AGENT_SOURCE:-}" ]]; then
    [[ -f "$AIR_VM_GUEST_AGENT_SOURCE" ]] || \
      die "AIR_VM_GUEST_AGENT_SOURCE does not name a file: $AIR_VM_GUEST_AGENT_SOURCE"
    AIR_GUEST_AGENT_BINARY="$AIR_VM_GUEST_AGENT_SOURCE"
    note "using the guest agent AIR_VM_GUEST_AGENT_SOURCE names: $AIR_GUEST_AGENT_BINARY"
    return
  fi

  [[ -x "$AIR_REPO_ROOT/bazel.cmd" ]] || die \
    "the image pipeline builds its guest provisioner with Bazel, and this checkout has none: $AIR_REPO_ROOT/bazel.cmd"
  note "building the macOS guest agent: $AIR_GUEST_AGENT_LABEL"
  air_bazel build "$AIR_GUEST_AGENT_LABEL"
  relative="$(air_bazel cquery --output=starlark \
    '--starlark:expr=[f.path for f in target.files.to_list()][0]' "$AIR_GUEST_AGENT_LABEL" |
    /usr/bin/awk 'NF { last = $0 } END { print last }')"
  [[ -n "$relative" ]] || die "bazel cquery reported no output file for $AIR_GUEST_AGENT_LABEL"
  execution_root="$(air_bazel info execution_root)"
  [[ -n "$execution_root" ]] || die "bazel info execution_root answered nothing"
  AIR_GUEST_AGENT_BINARY="$execution_root/$relative"
  [[ -f "$AIR_GUEST_AGENT_BINARY" ]] || \
    die "the guest agent is not where bazel cquery said it would be: $AIR_GUEST_AGENT_BINARY"
  note "guest agent: $AIR_GUEST_AGENT_BINARY"
}

# One authority for where a base comes from: the reference pinned in versions.env. Every pin in this
# workflow is a `host/repository:tag` or `host/repository@digest` string, so the host, the repository and
# the tag are read off the reference rather than restated per script - which is what lets the second pin
# (the Ubuntu base the controller clones) reuse the query instead of growing its own copy of it.
registry_host() {
  print -- "${1%%/*}"
}

registry_repository() {
  local path="${1#*/}"
  print -- "${path%%[:@]*}"
}

registry_tag() {
  print -- "${1##*:}"
}

registry_token() {
  local reference="$1"
  curl -fsSL "https://$(registry_host "$reference")/token?scope=repository:$(registry_repository "$reference"):pull" |
    jq -er .token
}

# What a tag resolves to right now, or nothing at all when the registry cannot be read. Prints rather than
# dies on every failure: freshness is news, and an unreachable registry is not a reason to stop a build that
# fetches its base by digest anyway.
registry_tag_digest() {
  local reference="$1"
  local token
  token="$(registry_token "$reference" 2>/dev/null)" || return 0
  curl -fsSIL \
    -H "Authorization: Bearer $token" \
    -H 'Accept: application/vnd.oci.image.manifest.v1+json' \
    "https://$(registry_host "$reference")/v2/$(registry_repository "$reference")/manifests/$(registry_tag "$reference")" \
    2>/dev/null |
    /usr/bin/awk 'BEGIN { IGNORECASE = 1 } /^docker-content-digest:/ { gsub("\r", "", $2); print $2 }'
}

# The integrity guarantee, asserted where it is relied on: what the pipeline downloads is addressed by
# the pinned digest, so the registry client can only produce those bytes. Nothing here consults the
# moving tag - a base fetched by tag would be unpinned no matter what any freshness check said.
require_pinned_digest_reference() {
  local reference="$1"
  local digest="$2"
  [[ "$digest" =~ '^sha256:[0-9a-f]{64}$' ]] || \
    die "a pinned base digest must be a sha256 digest: $digest"
  [[ "$reference" == "$(registry_host "$reference")/$(registry_repository "$reference")@$digest" ]] || \
    die "the base must be fetched by its pinned digest, not by a tag: $reference"
}

# Proof after the fact that the local base really came from the pin: Tart keys its OCI cache by the
# reference it pulled, so the digest reference appearing as an OCI source is the fetched bytes matching
# the pin. This is the check worth keeping; asserting that upstream has not published anything since is
# not an integrity check at all.
require_fetched_digest_reference() {
  tart list --source oci --quiet | /usr/bin/awk -v wanted="$CIRRUS_BASE_REFERENCE" '
    $0 == wanted { found = 1 }
    END { exit(found ? 0 : 1) }
  ' || die "no digest-addressed OCI source for the pinned base; refusing an unpinned local base: $CIRRUS_BASE_REFERENCE"
}

# The pinned digest's own registry metadata. Deriving the upload time from the digest is what keeps the
# two from disagreeing: a hand-maintained copy in versions.env described whichever base was pinned when
# somebody last remembered to edit it. Prints nothing when the registry cannot be read or the annotation
# is absent - this is provenance, not admission, and no reuse decision reads it.
resolve_base_upload_time() {
  local token manifest
  token="$(registry_token "$CIRRUS_BASE_TAG_REFERENCE" 2>/dev/null)" || return 0
  manifest="$(
    curl -fsSL \
      -H "Authorization: Bearer $token" \
      -H 'Accept: application/vnd.oci.image.manifest.v1+json' \
      "https://$(registry_host "$CIRRUS_BASE_TAG_REFERENCE")/v2/$(registry_repository "$CIRRUS_BASE_TAG_REFERENCE")/manifests/$CIRRUS_BASE_DIGEST" 2>/dev/null
  )" || return 0
  print -r -- "$manifest" | jq -r '.annotations["org.cirruslabs.tart.upload-time"] // empty' 2>/dev/null || return 0
}

vm_exists() {
  local wanted="$1"
  tart list --source local --quiet | /usr/bin/awk -v wanted="$wanted" '
    $0 == wanted { found = 1 }
    END { exit(found ? 0 : 1) }
  '
}

validate_tart_vm_name() {
  local vm_name="$1"
  case "$vm_name" in
    ""|*[!A-Za-z0-9._-]*) die "invalid Tart VM name: $vm_name" ;;
  esac
}

require_tart_vm_stopped() {
  local vm_name="$1"
  local vm_state
  validate_tart_vm_name "$vm_name"
  vm_state="$(tart list --source local --format json | jq -r --arg name "$vm_name" '.[] | select(.Name == $name) | .State')"
  [[ "$vm_state" == stopped ]] || die "Tart VM must exist and be stopped: $vm_name ($vm_state)"
}

tart_vm_file_signature() {
  local vm_name="$1"
  local file_name="$2"
  local tart_home="${TART_HOME:-$HOME/.tart}"
  local file
  validate_tart_vm_name "$vm_name"
  case "$file_name" in
    disk.img|config.json|nvram.bin) ;;
    *) die "unsupported Tart VM input: $file_name" ;;
  esac
  file="$tart_home/vms/$vm_name/$file_name"
  [[ -f "$file" && ! -L "$file" ]] || die "Tart VM input must be a regular non-symlink file: $file"
  /usr/bin/stat -f '%i:%z:%m:%c:%B' "$file"
}

require_tart_vm_input_signatures() {
  local vm_name="$1"
  local expected_disk="$2"
  local expected_config="$3"
  local expected_nvram="$4"
  local actual_disk actual_config actual_nvram
  actual_disk="$(tart_vm_file_signature "$vm_name" disk.img)"
  actual_config="$(tart_vm_file_signature "$vm_name" config.json)"
  actual_nvram="$(tart_vm_file_signature "$vm_name" nvram.bin)"
  [[ "$actual_disk" == "$expected_disk" ]] || die "Tart VM disk changed after provenance capture: $vm_name"
  [[ "$actual_config" == "$expected_config" ]] || die "Tart VM config changed after provenance capture: $vm_name"
  [[ "$actual_nvram" == "$expected_nvram" ]] || die "Tart VM NVRAM changed after provenance capture: $vm_name"
}

write_atomic_receipt() {
  local destination="$1"
  local content="$2"
  local temporary
  mkdir -p "${destination:h}"
  umask 077
  temporary="$(/usr/bin/mktemp "${destination}.tmp.XXXXXX")"
  print -r -- "$content" >| "$temporary" || {
    /bin/rm -f "$temporary"
    return 1
  }
  /bin/chmod 600 "$temporary" || {
    /bin/rm -f "$temporary"
    return 1
  }
  /bin/mv -f "$temporary" "$destination" || {
    /bin/rm -f "$temporary"
    return 1
  }
}

base_receipt_path() {
  print -- "$AIR_TART_STATE_DIR/base-receipt.json"
}

write_base_receipt() {
  local vm_name="$1"
  local expected_disk="$2"
  local expected_config="$3"
  local expected_nvram="$4"
  local receipt content upload_time
  require_tart_vm_stopped "$vm_name"
  require_tart_vm_input_signatures "$vm_name" "$expected_disk" "$expected_config" "$expected_nvram"
  receipt="$(base_receipt_path)"
  # fetch-base.sh resolves this from the pinned digest's manifest while it is already talking to the
  # registry. Empty when nothing resolved it: the receipt then records no upload time rather than a
  # confident wrong one, and every admission check reads the digest instead.
  upload_time="${AIR_BASE_UPLOAD_TIME:-}"
  content="$(jq -n \
    --argjson schemaVersion "$GOLDEN_SEAL_SCHEMA" \
    --arg reference "$CIRRUS_BASE_REFERENCE" \
    --arg baseDigest "$CIRRUS_BASE_DIGEST" \
    --arg uploadTime "$upload_time" \
    --arg macosVersion "$MACOS_VERSION" \
    --arg localVm "$vm_name" \
    --arg diskSignature "$expected_disk" \
    --arg configSignature "$expected_config" \
    --arg nvramSignature "$expected_nvram" \
    --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{schemaVersion:$schemaVersion,reference:$reference,baseDigest:$baseDigest,uploadTime:$uploadTime,macosVersion:$macosVersion,localVm:$localVm,diskSignature:$diskSignature,configSignature:$configSignature,nvramSignature:$nvramSignature,createdAt:$createdAt}')"
  write_atomic_receipt "$receipt" "$content"
  note "recorded immutable local-base provenance for $vm_name"
}

require_base_receipt() {
  local vm_name="$1"
  local receipt mode expected_disk expected_config expected_nvram
  receipt="$(base_receipt_path)"
  [[ -f "$receipt" && ! -L "$receipt" ]] || die "local base exists without a safe provenance receipt: $vm_name"
  mode="$(/usr/bin/stat -f '%Lp' "$receipt")"
  [[ "$mode" == 600 ]] || die "local base receipt must be mode 0600: $receipt"
  jq -e \
    --argjson schemaVersion "$GOLDEN_SEAL_SCHEMA" \
    --arg reference "$CIRRUS_BASE_REFERENCE" \
    --arg baseDigest "$CIRRUS_BASE_DIGEST" \
    --arg macosVersion "$MACOS_VERSION" \
    --arg localVm "$vm_name" \
    '.schemaVersion == $schemaVersion and .reference == $reference and .baseDigest == $baseDigest and .macosVersion == $macosVersion and .localVm == $localVm and (.diskSignature | type == "string") and (.configSignature | type == "string") and (.nvramSignature | type == "string")' \
    "$receipt" >/dev/null || die "local base receipt does not match the pinned image provenance: $receipt"
  expected_disk="$(jq -er .diskSignature "$receipt")"
  expected_config="$(jq -er .configSignature "$receipt")"
  expected_nvram="$(jq -er .nvramSignature "$receipt")"
  require_tart_vm_stopped "$vm_name"
  require_tart_vm_input_signatures "$vm_name" "$expected_disk" "$expected_config" "$expected_nvram"
}

golden_seal_path() {
  local vm_name="$1"
  local tart_home="${TART_HOME:-$HOME/.tart}"
  print -- "$tart_home/vms/$vm_name/.air-seal.json"
}

write_golden_seal() {
  local vm_name="$1"
  local expected_disk="$2"
  local expected_config="$3"
  local expected_nvram="$4"
  local seal content
  require_tart_vm_stopped "$vm_name"
  require_tart_vm_input_signatures "$vm_name" "$expected_disk" "$expected_config" "$expected_nvram"
  seal="$(golden_seal_path "$vm_name")"
  content="$(jq -n \
    --argjson schemaVersion "$GOLDEN_SEAL_SCHEMA" \
    --arg golden "$vm_name" \
    --arg diskSignature "$expected_disk" \
    --arg configSignature "$expected_config" \
    --arg nvramSignature "$expected_nvram" \
    --arg baseDigest "$CIRRUS_BASE_DIGEST" \
    --arg macosVersion "$MACOS_VERSION" \
    --arg auditedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{schemaVersion:$schemaVersion,golden:$golden,diskSignature:$diskSignature,configSignature:$configSignature,nvramSignature:$nvramSignature,baseDigest:$baseDigest,macosVersion:$macosVersion,auditedAt:$auditedAt}')"
  write_atomic_receipt "$seal" "$content"
  note "recorded sealed golden provenance for $vm_name"
}
