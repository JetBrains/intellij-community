#!/bin/zsh

source "${0:A:h}/lib.sh"

require_command curl
require_command jq

# What makes a base trustworthy is that it is fetched by digest: a pinned reference is content-addressed,
# so the registry client cannot hand back anything but the pinned bytes. Assert that much for the macOS
# base, because it is the guarantee everything else rests on. It is what the image build clones.
require_pinned_digest_reference "$CIRRUS_BASE_REFERENCE" "$CIRRUS_BASE_DIGEST"

# Then look at the moving tag - as news, not as a gate. Cirrus republishes its tags on its own schedule,
# and a build that refuses to run once it has is a pin with an expiry date: the digest keeps naming the
# same immutable image, while the check invents a failure out of upstream activity nobody here consumes.
# So report a moved tag loudly and keep building from the pin.
report_base_freshness() {
  local tag_reference="$1"
  local pinned_digest="$2"
  local pin_variable="$3"
  local actual_digest
  actual_digest="$(registry_tag_digest "$tag_reference")"
  if [[ -z "$actual_digest" ]]; then
    warn "could not read $tag_reference from the registry; freshness is unknown"
    warn "nothing is affected: the base is fetched by digest $pinned_digest"
  elif [[ "$actual_digest" == "$pinned_digest" ]]; then
    note "$tag_reference still resolves to pinned digest $actual_digest"
  else
    warn "$tag_reference has moved on: it now resolves to $actual_digest"
    warn "the pinned $pinned_digest is used regardless; bump $pin_variable to adopt the newer base"
  fi
}

report_base_freshness "$CIRRUS_BASE_TAG_REFERENCE" "$CIRRUS_BASE_DIGEST" CIRRUS_BASE_DIGEST
