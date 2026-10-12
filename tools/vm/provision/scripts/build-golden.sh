#!/bin/zsh

script_dir="${0:A:h}"
source "$script_dir/lib.sh"

"$script_dir/check-host.sh"
"$script_dir/bootstrap-packer.sh"

# Before Packer, because `packer validate` already needs the binary: the guest half of this build is a Bazel
# target now, so building a golden image builds the Rust agent first. resolve_guest_agent_binary states what that costs an
# operator; ADR 0108 states why the pipeline took the dependency.
resolve_guest_agent_binary

mkdir -p "$PACKER_PLUGIN_PATH"
note "initializing and validating pinned Packer plugins"
cd "$AIR_PROPOSAL_ROOT"
"$AIR_PACKER_BIN" init air-macos.pkr.hcl
"$AIR_PACKER_BIN" fmt -check air-macos.pkr.hcl
"$AIR_PACKER_BIN" validate \
  -var "base_vm=$BASE_VM" \
  -var "golden_vm=$GOLDEN_VM" \
  -var "macos_version=$MACOS_VERSION" \
  -var "junie_version=$JUNIE_VERSION" \
  -var "node_major=$NODE_MAJOR" \
  -var "guest_agent_binary=$AIR_GUEST_AGENT_BINARY" \
  air-macos.pkr.hcl

"$script_dir/fetch-base.sh"
vm_exists "$GOLDEN_VM" && die "golden VM already exists; refusing to replace it: $GOLDEN_VM"
candidate_vm="${GOLDEN_VM}-candidate-$(date -u +%Y%m%dT%H%M%SZ)-$$"
vm_exists "$candidate_vm" && die "candidate VM already exists: $candidate_vm"

note "building unapproved candidate $candidate_vm from local pinned base $BASE_VM"
"$AIR_PACKER_BIN" build \
  -var "base_vm=$BASE_VM" \
  -var "golden_vm=$candidate_vm" \
  -var "macos_version=$MACOS_VERSION" \
  -var "junie_version=$JUNIE_VERSION" \
  -var "node_major=$NODE_MAJOR" \
  -var "guest_agent_binary=$AIR_GUEST_AGENT_BINARY" \
  air-macos.pkr.hcl

require_tart_vm_stopped "$candidate_vm"
candidate_disk_signature="$(tart_vm_file_signature "$candidate_vm" disk.img)"
candidate_config_signature="$(tart_vm_file_signature "$candidate_vm" config.json)"
candidate_nvram_signature="$(tart_vm_file_signature "$candidate_vm" nvram.bin)"
"$script_dir/audit-sealed-golden.sh" "$candidate_vm"
require_tart_vm_stopped "$candidate_vm"
require_tart_vm_input_signatures \
  "$candidate_vm" "$candidate_disk_signature" "$candidate_config_signature" "$candidate_nvram_signature"
note "promoting audited candidate $candidate_vm to $GOLDEN_VM"
tart rename "$candidate_vm" "$GOLDEN_VM"
write_golden_seal \
  "$GOLDEN_VM" "$candidate_disk_signature" "$candidate_config_signature" "$candidate_nvram_signature"
