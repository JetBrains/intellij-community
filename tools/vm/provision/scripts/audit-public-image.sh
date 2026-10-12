#!/bin/zsh

set -euo pipefail

allow_ssh_host_keys=false
allow_sensitive_tcc=false
while (( $# > 0 )); do
  case "$1" in
    --allow-ssh-host-keys) allow_ssh_host_keys=true ;;
    --allow-sensitive-tcc) allow_sensitive_tcc=true ;;
    *)
      print -u2 -- "usage: air-audit-public-image [--allow-ssh-host-keys] [--allow-sensitive-tcc]"
      exit 2
      ;;
  esac
  shift
done

credential_files=(
  /Users/admin/.netrc
  /Users/admin/.npmrc
  /Users/admin/.gitconfig
  /Users/admin/.git-credentials
  /Users/admin/.ssh/authorized_keys
  /Users/admin/.codex/auth.json
  /Users/admin/.pi/agent/auth.json
  /Users/admin/.claude/.credentials.json
  /Users/admin/.local/share/opencode/auth.json
  /Users/admin/.config/codex/auth.json
  /Users/admin/.config/gh/hosts.yml
  /Users/admin/.docker/config.json
  /Users/admin/.config/containers/auth.json
  /Users/admin/.config/gcloud/application_default_credentials.json
  /Users/admin/.config/gcloud/credentials.db
  /Users/admin/.aws/credentials
  /Users/admin/.azure/accessTokens.json
  /Users/admin/.azure/msal_token_cache.json
  /Users/admin/.kube/config
)
for credential_file in $credential_files /Users/admin/.ssh/id_*(N); do
  [[ ! -e "$credential_file" ]] || {
    print -u2 -- "credential-bearing file must not be baked into the image: $credential_file"
    exit 1
  }
done

[[ ! -e /Users/admin/WorkerData ]] || {
  print -u2 -- "worker-private storage must not be baked into the golden image"
  exit 1
}
[[ ! -e /Volumes/AIRHostSource ]] || {
  print -u2 -- "host source must not be mounted while auditing the golden image"
  exit 1
}
git_metadata="$(/usr/bin/find /Users/admin -xdev \( -type d -o -type f \) -name .git -print -quit)"
[[ -z "$git_metadata" ]] || {
  print -u2 -- "Git worktree metadata must not be baked into the image: $git_metadata"
  exit 1
}

history_paths=(
  /Users/admin/.zsh_sessions
  /Users/admin/.zsh_history
  /Users/admin/.bash_history
  /Users/admin/.sh_history
)
for history_path in $history_paths; do
  [[ ! -e "$history_path" ]] || {
    print -u2 -- "shell history must not be baked into the image: $history_path"
    exit 1
  }
done

cache_paths=(
  /Users/admin/.npm
  /Users/admin/.cache
  /Users/admin/Library/Caches/Homebrew
  /Users/admin/Library/Caches/Bazel
  /Users/admin/.gradle/caches
  /Users/admin/.m2/repository
  /Users/admin/.ivy2/cache
  /Users/admin/.konan/cache
  /Users/admin/.cargo/registry/cache
  /Users/admin/.cache/bazel
  /Users/admin/.cache/bazelisk
)
for cache_path in $cache_paths; do
  [[ ! -d "$cache_path" ]] && continue
  first_entry="$(/usr/bin/find "$cache_path" -mindepth 1 -print -quit)"
  [[ -z "$first_entry" ]] || {
    print -u2 -- "build or package-manager cache must not be baked into the image: $cache_path"
    exit 1
  }
done

readonly sensitive_tcc_services="
  'kTCCServiceAccessibility',
  'kTCCServiceAddressBook',
  'kTCCServiceAppleEvents',
  'kTCCServiceBluetoothAlways',
  'kTCCServiceCalendar',
  'kTCCServiceCamera',
  'kTCCServiceListenEvent',
  'kTCCServiceMediaLibrary',
  'kTCCServiceMicrophone',
  'kTCCServiceMotion',
  'kTCCServicePhotos',
  'kTCCServicePostEvent',
  'kTCCServiceScreenCapture',
  'kTCCServiceSpeechRecognition',
  'kTCCServiceSystemPolicyAllFiles',
  'kTCCServiceSystemPolicyDesktopFolder',
  'kTCCServiceSystemPolicyDocumentsFolder',
  'kTCCServiceSystemPolicyDownloadsFolder',
  'kTCCServiceSystemPolicyNetworkVolumes',
  'kTCCServiceSystemPolicyRemovableVolumes'
"
if [[ "$allow_sensitive_tcc" != true ]]; then
  for tcc_db in \
    '/Library/Application Support/com.apple.TCC/TCC.db' \
    '/Users/admin/Library/Application Support/com.apple.TCC/TCC.db'; do
    [[ -f "$tcc_db" ]] || continue
    decision_count="$(sudo /usr/bin/sqlite3 -cmd '.timeout 5000' "file:$tcc_db?mode=ro" \
      "SELECT (SELECT count(*) FROM access WHERE service IN ($sensitive_tcc_services)) + (SELECT count(*) FROM active_policy) + (SELECT count(*) FROM policies) + (SELECT count(*) FROM access_overrides);")"
    [[ "$decision_count" == 0 ]] || {
      print -u2 -- "sensitive TCC decisions or policy must not be baked into the image: $tcc_db contains $decision_count rows"
      exit 1
    }
  done
fi

if [[ "$allow_ssh_host_keys" != true ]]; then
  for host_private_key in /private/etc/ssh/ssh_host_*_key(N); do
    print -u2 -- "SSH host private key must not be baked into the image: $host_private_key"
    exit 1
  done
fi

print -- "public image audit passed"
