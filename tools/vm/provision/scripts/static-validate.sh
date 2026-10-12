#!/bin/zsh

script_dir="${0:A:h}"
source "$script_dir/lib.sh"

"$script_dir/check-host.sh"
"$script_dir/bootstrap-packer.sh"

# Static validation builds the guest agent too, and it has to: Packer's `file` provisioner stats its source
# during `packer validate`, so a template naming a binary that does not exist is not a valid configuration.
# Every required variable is passed here and by `build-golden.sh` alike, or this script reports a valid
# configuration for a build that cannot start. ADR 0108 has the dependency's argument.
resolve_guest_agent_binary

mkdir -p "$PACKER_PLUGIN_PATH"
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

for script in "$AIR_PROPOSAL_ROOT"/scripts/*.sh; do
  zsh -n "$script"
done

"$script_dir/verify-base-digest.sh"
note "static validation passed; no VM was downloaded, created, or changed"
