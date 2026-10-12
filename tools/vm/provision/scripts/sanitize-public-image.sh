#!/bin/zsh

set -euo pipefail

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
)
rm -rf -- $cache_paths
sudo /usr/local/sbin/air-audit-public-image --allow-ssh-host-keys --allow-sensitive-tcc
