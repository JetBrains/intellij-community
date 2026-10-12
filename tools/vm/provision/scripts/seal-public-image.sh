#!/bin/zsh

set -euo pipefail

# This is the final Packer SSH provisioner. Use only supported TCC reset APIs;
# an offline post-shutdown audit of copied DB/WAL/SHM files remains the
# publication authority. Reject MDM/PPPC because either can restore grants.
sudo /usr/local/sbin/air-audit-public-image --allow-ssh-host-keys --allow-sensitive-tcc
enrollment_status="$(sudo /usr/bin/profiles status -type enrollment 2>&1)"
[[ "$enrollment_status" == *"Enrolled via DEP: No"* && "$enrollment_status" == *"MDM enrollment: No"* ]] || {
  print -u2 -- "refusing to seal an MDM-enrolled image"
  exit 1
}
configuration_profiles="$(sudo /usr/bin/profiles show -type configuration -output stdout-xml 2>/dev/null || true)"
[[ "$configuration_profiles" != *"com.apple.TCC.configuration-profile-policy"* ]] || {
  print -u2 -- "refusing to seal an image with a PPPC profile"
  exit 1
}

sudo /bin/launchctl asuser 501 /usr/bin/sudo -H -u admin /usr/bin/tccutil reset All
sudo /usr/bin/tccutil reset All
sudo /usr/local/sbin/air-audit-public-image --allow-ssh-host-keys

# Final filesystem mutation: remove the base image's shared SSH identity. Do
# not stop/restart sshd; Packer's existing authenticated communicator remains
# valid and performs the graceful shutdown after this provisioner returns.
sudo rm -f -- /private/etc/ssh/ssh_host_*_key(N) /private/etc/ssh/ssh_host_*_key.pub(N)
sudo /usr/local/sbin/air-audit-public-image
sudo /bin/sync
