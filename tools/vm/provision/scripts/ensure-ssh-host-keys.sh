#!/bin/zsh

set -euo pipefail

(( $# == 1 )) || {
  print -u2 -- "usage: air-ensure-ssh-host-keys /Users/admin/WorkerData/state/ssh-host-key-fingerprint"
  exit 2
}
readonly ready_marker="$1"
[[ "$ready_marker" == /Users/admin/WorkerData/state/* ]] || {
  print -u2 -- "SSH host-key marker must be stored under WorkerData state"
  exit 2
}
private_keys=(/etc/ssh/ssh_host_ecdsa_key /etc/ssh/ssh_host_ed25519_key /etc/ssh/ssh_host_rsa_key)

missing=false
for private_key in $private_keys; do
  [[ -s "$private_key" && -s "$private_key.pub" ]] || missing=true
done
if [[ "$missing" == true ]]; then
  rm -f -- /etc/ssh/ssh_host_*_key(N) /etc/ssh/ssh_host_*_key.pub(N) "$ready_marker"
  /usr/bin/ssh-keygen -A
fi

for private_key in $private_keys; do
  [[ -s "$private_key" && -s "$private_key.pub" ]] || {
    print -u2 -- "failed to generate SSH host key pair: $private_key"
    exit 1
  }
  chmod 0600 "$private_key"
  chmod 0644 "$private_key.pub"
done

fingerprint="$(/usr/bin/ssh-keygen -E sha256 -lf /etc/ssh/ssh_host_ed25519_key.pub | /usr/bin/awk '{print $2}')"
temporary_marker="${ready_marker}.$$"
mkdir -p "${ready_marker:h}"
print -r -- "$fingerprint" > "$temporary_marker"
chmod 0600 "$temporary_marker"
mv -f "$temporary_marker" "$ready_marker"
print -r -- "$fingerprint"
