#!/bin/zsh

source "${0:A:h}/lib.sh"

if vm_exists "$BASE_VM"; then
  require_base_receipt "$BASE_VM"
  note "using existing digest-gated local base $BASE_VM"
  exit 0
fi

"${0:A:h}/verify-base-digest.sh"
mkdir -p "$AIR_TART_STATE_DIR"

# Resolved from the pinned digest's own manifest, here where the registry is already being talked to, so
# the recorded upload time describes the base actually fetched. write_base_receipt reads it from here.
export AIR_BASE_UPLOAD_TIME="$(resolve_base_upload_time)"
[[ -n "$AIR_BASE_UPLOAD_TIME" ]] || warn "registry reported no upload time for $CIRRUS_BASE_DIGEST"

note "cloning digest-pinned Cirrus base; this is the one large network download"
tart clone "$CIRRUS_BASE_REFERENCE" "$BASE_VM"

# The bytes that just became the local base came from the digest-addressed OCI source, not the tag.
require_fetched_digest_reference

disk_signature="$(tart_vm_file_signature "$BASE_VM" disk.img)"
config_signature="$(tart_vm_file_signature "$BASE_VM" config.json)"
nvram_signature="$(tart_vm_file_signature "$BASE_VM" nvram.bin)"
write_base_receipt "$BASE_VM" "$disk_signature" "$config_signature" "$nvram_signature"
note "created never-run local base snapshot $BASE_VM"
