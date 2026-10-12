#!/bin/zsh

source "${0:A:h}/lib.sh"

[[ "$(uname -s)" == Darwin ]] || die "Tart requires a macOS host"
[[ "$(uname -m)" == arm64 ]] || die "Tart requires Apple Silicon"

for command_name in tart curl jq shasum unzip; do
  require_command "$command_name"
done

# A floor, not an exact match: every flag this pipeline and the controller use has been present since
# TART_MIN_VERSION, and pinning an exact minor is how a routine `brew upgrade` broke every Tart command.
actual_tart_version="$(tart --version)"
oldest_version="$(printf '%s\n%s\n' "$TART_MIN_VERSION" "$actual_tart_version" | sort -t. -k1,1n -k2,2n -k3,3n | head -1)"
[[ "$oldest_version" == "$TART_MIN_VERSION" ]] || \
  die "Tart $TART_MIN_VERSION or newer is required, found $actual_tart_version"

tart_storage="${TART_HOME:-$HOME/.tart}"
mkdir -p "$tart_storage"
is_apfs_path "$tart_storage" || \
  die "Tart storage must be on APFS for copy-on-write worker clones: $tart_storage"

logical_cpus="$(sysctl -n hw.logicalcpu)"
memory_bytes="$(sysctl -n hw.memsize)"
(( logical_cpus >= WORKER_CPU * 2 )) || \
  die "two workers require at least $(( WORKER_CPU * 2 )) logical CPUs; found $logical_cpus"
(( memory_bytes >= 80 * 1024 * 1024 * 1024 )) || \
  die "two 32 GiB guests require an 80 GiB or larger host; found $(( memory_bytes / 1024 / 1024 / 1024 )) GiB"

note "host is compatible (Tart $actual_tart_version, $logical_cpus CPUs, $(( memory_bytes / 1024 / 1024 / 1024 )) GiB RAM, APFS VM storage)"
