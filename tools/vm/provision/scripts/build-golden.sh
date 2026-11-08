#!/bin/zsh

script_dir="${0:A:h}"
source "$script_dir/lib.sh"

"$script_dir/check-host.sh"
"$script_dir/bootstrap-packer.sh"

# Before Packer, because `packer validate` already needs the binary: the guest half of this build is a Bazel
# target now, so building a golden image builds the Rust agent first. resolve_guest_agent_binary states what that costs an
# operator; ADR 0108 states why the pipeline took the dependency.
resolve_guest_agent_binary

# `vm.cmd image build` names the candidate and serves Packer's SSH to it over the exec channel of Tart on a loopback
# port, because macOS Local Network privacy refuses the guest address (crates/avl-vm/src/image/ssh_relay.rs of the
# UI-lane controller). Without both values Packer would wait for an SSH that never connects.
candidate_vm="${AIR_IMAGE_CANDIDATE_VM:-}"
ssh_proxy_port="${AIR_IMAGE_SSH_PROXY_PORT:-}"
[[ -n "$candidate_vm" && -n "$ssh_proxy_port" ]] ||
  die "run the build through ./community/tools/vm.cmd --backend tart image build, which serves Packer's SSH"
[[ "$candidate_vm" == "${GOLDEN_VM}-candidate-"* ]] ||
  die "the candidate $candidate_vm is not a candidate of the golden $GOLDEN_VM"

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
  -var "ssh_proxy_port=$ssh_proxy_port" \
  air-macos.pkr.hcl

"$script_dir/fetch-base.sh"
vm_exists "$GOLDEN_VM" && die "golden VM already exists; refusing to replace it: $GOLDEN_VM"
vm_exists "$candidate_vm" && die "candidate VM already exists: $candidate_vm"

note "building unapproved candidate $candidate_vm from local pinned base $BASE_VM"
"$AIR_PACKER_BIN" build \
  -var "base_vm=$BASE_VM" \
  -var "golden_vm=$candidate_vm" \
  -var "macos_version=$MACOS_VERSION" \
  -var "junie_version=$JUNIE_VERSION" \
  -var "node_major=$NODE_MAJOR" \
  -var "guest_agent_binary=$AIR_GUEST_AGENT_BINARY" \
  -var "ssh_proxy_port=$ssh_proxy_port" \
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
