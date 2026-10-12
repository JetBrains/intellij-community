#!/bin/zsh

set -euo pipefail

# The controller passes the root-disk size it sized this worker to and the data root it will use, so a
# pool whose workers are smaller than 400 GB does not have to disagree with its own guest script. The
# defaults are what the controller used before the arguments existed, which is also what an older sealed
# golden's copy of this script assumes.
readonly expected_root_bytes="${1:-400000000000}"
readonly worker_root="${2:-/Users/admin/WorkerData}"
readonly worker_user="${3:-admin}"

[[ "$expected_root_bytes" == <-> ]] || {
  print -u2 -- "air-init-worker-storage: expected root size must be a byte count, got $expected_root_bytes"
  exit 1
}
[[ "$worker_root" == /* ]] || {
  print -u2 -- "air-init-worker-storage: worker root must be an absolute path, got $worker_root"
  exit 1
}

(( EUID == 0 )) || {
  print -u2 -- "air-init-worker-storage must run as root"
  exit 1
}

root_info="$(diskutil info -plist /)"
root_container="$(print -r -- "$root_info" | plutil -extract APFSContainerReference raw -o - - 2>/dev/null || true)"
root_physical_store="$(print -r -- "$root_info" | plutil -extract APFSPhysicalStores.0.APFSPhysicalStore raw -o - - 2>/dev/null || true)"
second_physical_store="$(print -r -- "$root_info" | plutil -extract APFSPhysicalStores.1.APFSPhysicalStore raw -o - - 2>/dev/null || true)"

[[ "$root_container" == disk<-> && "$root_physical_store" == disk<->s<-> && -z "$second_physical_store" ]] || {
  print -u2 -- "refusing root storage initialization: / must be a single-store APFS container"
  exit 1
}

store_info="$(diskutil info -plist "$root_physical_store")"
root_whole_disk="$(print -r -- "$store_info" | plutil -extract ParentWholeDisk raw -o - - 2>/dev/null || true)"
[[ "$root_whole_disk" == disk<-> ]] || {
  print -u2 -- "refusing root storage initialization: could not resolve the root physical disk"
  exit 1
}

whole_info="$(diskutil info -plist "$root_whole_disk")"
actual_root_bytes="$(print -r -- "$whole_info" | plutil -extract TotalSize raw -o - - 2>/dev/null || true)"
root_is_internal="$(print -r -- "$whole_info" | plutil -extract Internal raw -o - - 2>/dev/null || true)"
[[ "$actual_root_bytes" == "$expected_root_bytes" && "$root_is_internal" == true ]] || {
  print -u2 -- "refusing root storage initialization: $root_whole_disk is $actual_root_bytes bytes/internal=$root_is_internal; expected the internal $expected_root_bytes-byte worker root"
  exit 1
}

# Tart may grow the cloned root container automatically on first boot. Query
# the validated store first so repeated initialization is idempotent; only ask
# diskutil to consume remaining space when the container is still smaller.
resize_limits="$(diskutil apfs resizeContainer "$root_physical_store" limits -plist)"
current_size="$(print -r -- "$resize_limits" | plutil -extract CurrentSize raw -o - - 2>/dev/null || true)"
maximum_size="$(print -r -- "$resize_limits" | plutil -extract MaximumSize raw -o - - 2>/dev/null || true)"
[[ "$current_size" == <-> && "$maximum_size" == <-> && "$current_size" -le "$maximum_size" ]] || {
  print -u2 -- "refusing root storage initialization: invalid APFS resize limits current=$current_size maximum=$maximum_size"
  exit 1
}
if (( current_size < maximum_size )); then
  # A zero size means grow to fit on this already validated root disk.
  diskutil apfs resizeContainer "$root_physical_store" 0
fi

# Only the two directories a worker actually owns. The guest holds no checkout and runs no Bazel, so
# there is nothing to store beyond `state` and what the parity layout creates under $worker_root
# (out, tmp, build-download).
#
# The sealed golden still carries the previous copy of this script at
# /usr/local/sbin/air-init-worker-storage, so workers cloned before the next `image build` keep three
# empty checkout/bazel directories. They are inert; nothing reads them.
/usr/bin/install -d -o "$worker_user" -g staff -m 0755 \
  "$worker_root" \
  "$worker_root/state"
