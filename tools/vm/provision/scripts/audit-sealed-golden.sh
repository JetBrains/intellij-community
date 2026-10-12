#!/bin/zsh

script_dir="${0:A:h}"
source "$script_dir/lib.sh"

(( $# <= 1 )) || die "usage: ${0:t} [golden-vm]"
audit_vm="${1:-$GOLDEN_VM}"
validate_tart_vm_name "$audit_vm"
require_tart_vm_stopped "$audit_vm"

tart_home="${TART_HOME:-$HOME/.tart}"
golden_disk="$tart_home/vms/$audit_vm/disk.img"
[[ -f "$golden_disk" ]] || die "golden disk not found: $golden_disk"

audit_root="$(mktemp -d -t air-golden-audit)"
disk_copy="$audit_root/disk.img"
mount_point="$audit_root/data"
attached_disk=""
apfs_store=""
apfs_container=""
data_device=""
cleanup() {
  if [[ -n "$data_device" ]]; then
    diskutil unmount "$data_device" >/dev/null 2>&1 || true
  fi
  if [[ -n "$attached_disk" ]]; then
    hdiutil detach "$attached_disk" >/dev/null 2>&1 || true
  fi
  rm -rf "$audit_root"
}
trap cleanup EXIT

note "creating an APFS clone for read-only sealed-image audit"
/bin/cp -c "$golden_disk" "$disk_copy"
attach_output="$(hdiutil attach -readonly -nomount -imagekey diskimage-class=CRawDiskImage "$disk_copy")"
attached_disk="$(print -r -- "$attach_output" | /usr/bin/awk '$1 ~ /^\/dev\/disk[0-9]+$/ { print $1; exit }')"
[[ -n "$attached_disk" ]] || die "could not resolve the read-only attached golden disk"
apfs_store="$(print -r -- "$attach_output" | /usr/bin/awk '$1 ~ /^\/dev\/disk[0-9]+s[0-9]+$/ && $2 == "Apple_APFS" { print $1; exit }')"
[[ -n "$apfs_store" ]] || die "could not resolve the golden Apple_APFS store partition"
store_info="$(diskutil info -plist "$apfs_store")"
apfs_container="$(print -r -- "$store_info" | plutil -extract APFSContainerReference raw -o - - 2>/dev/null || true)"
[[ "$apfs_container" == disk<-> ]] || die "could not resolve the synthesized golden APFS container"

apfs_json="$(diskutil apfs list -plist "$apfs_container" | plutil -convert json -o - -)"
data_device="$(print -r -- "$apfs_json" | jq -r '.Containers[].Volumes[] | select((.Roles // []) | index("Data")) | .DeviceIdentifier' | /usr/bin/awk 'NR == 1 { print "/dev/" $0 }')"
[[ "$data_device" == /dev/disk* ]] || die "could not resolve the golden APFS Data volume"
mkdir -p "$mount_point"
diskutil mount readOnly -mountPoint "$mount_point" "$data_device" >/dev/null

credential_paths=(
  Users/admin/.netrc
  Users/admin/.npmrc
  Users/admin/.gitconfig
  Users/admin/.git-credentials
  Users/admin/.ssh/authorized_keys
  Users/admin/.codex/auth.json
  Users/admin/.config/codex/auth.json
  Users/admin/.pi/agent/auth.json
  Users/admin/.claude/.credentials.json
  Users/admin/.local/share/opencode/auth.json
  Users/admin/.config/gh/hosts.yml
  Users/admin/.docker/config.json
  Users/admin/.config/containers/auth.json
  Users/admin/.config/gcloud/application_default_credentials.json
  Users/admin/.config/gcloud/credentials.db
  Users/admin/.aws/credentials
  Users/admin/.azure/accessTokens.json
  Users/admin/.azure/msal_token_cache.json
  Users/admin/.kube/config
)
for relative_path in $credential_paths; do
  [[ ! -e "$mount_point/$relative_path" ]] || die "sealed golden contains credential path: /$relative_path"
done
for user_private_key in "$mount_point"/Users/admin/.ssh/id_*(N); do
  die "sealed golden contains user SSH private key: ${user_private_key#$mount_point}"
done

for relative_path in \
  Users/admin/WorkerData \
  Users/admin/.zsh_sessions \
  Users/admin/.zsh_history \
  Users/admin/.bash_history \
  Users/admin/.sh_history; do
  [[ ! -e "$mount_point/$relative_path" ]] || die "sealed golden contains private or transient path: /$relative_path"
done

git_metadata="$(/usr/bin/find "$mount_point/Users/admin" -xdev \( -type d -o -type f \) -name .git -print -quit)"
[[ -z "$git_metadata" ]] || die "sealed golden contains Git worktree metadata: $git_metadata"

for host_private_key in "$mount_point"/private/etc/ssh/ssh_host_*_key(N); do
  die "sealed golden contains SSH host private key: ${host_private_key#$mount_point}"
done

cache_paths=(
  Users/admin/.npm
  Users/admin/.cache
  Users/admin/Library/Caches/Homebrew
  Users/admin/Library/Caches/Bazel
  Users/admin/.gradle/caches
  Users/admin/.m2/repository
  Users/admin/.ivy2/cache
  Users/admin/.konan/cache
  Users/admin/.cargo/registry/cache
)
for relative_path in $cache_paths; do
  cache_path="$mount_point/$relative_path"
  [[ ! -d "$cache_path" ]] && continue
  [[ -z "$(/usr/bin/find "$cache_path" -mindepth 1 -print -quit)" ]] || die "sealed golden contains build cache: /$relative_path"
done

readonly sensitive_tcc_services="
  'kTCCServiceAccessibility', 'kTCCServiceAddressBook', 'kTCCServiceAppleEvents',
  'kTCCServiceBluetoothAlways', 'kTCCServiceCalendar', 'kTCCServiceCamera',
  'kTCCServiceListenEvent', 'kTCCServiceMediaLibrary', 'kTCCServiceMicrophone',
  'kTCCServiceMotion', 'kTCCServicePhotos', 'kTCCServicePostEvent',
  'kTCCServiceScreenCapture', 'kTCCServiceSpeechRecognition',
  'kTCCServiceSystemPolicyAllFiles', 'kTCCServiceSystemPolicyDesktopFolder',
  'kTCCServiceSystemPolicyDocumentsFolder', 'kTCCServiceSystemPolicyDownloadsFolder',
  'kTCCServiceSystemPolicyNetworkVolumes', 'kTCCServiceSystemPolicyRemovableVolumes'
"
for relative_db in \
  'Library/Application Support/com.apple.TCC/TCC.db' \
  'Users/admin/Library/Application Support/com.apple.TCC/TCC.db'; do
  source_db="$mount_point/$relative_db"
  [[ -f "$source_db" ]] || continue
  copied_db="$audit_root/$(print -r -- "$relative_db" | tr '/ ' '__')"
  /bin/cp "$source_db" "$copied_db"
  [[ ! -f "$source_db-wal" ]] || /bin/cp "$source_db-wal" "$copied_db-wal"
  [[ ! -f "$source_db-shm" ]] || /bin/cp "$source_db-shm" "$copied_db-shm"
  decision_count="$(/usr/bin/sqlite3 "file:$copied_db?mode=ro" \
    "SELECT (SELECT count(*) FROM access WHERE service IN ($sensitive_tcc_services)) + (SELECT count(*) FROM active_policy) + (SELECT count(*) FROM policies) + (SELECT count(*) FROM access_overrides);")"
  [[ "$decision_count" == 0 ]] || die "sealed golden contains $decision_count sensitive TCC decision/policy rows in /$relative_db"
done

note "sealed golden offline audit passed for $audit_vm; no registry action was performed"
