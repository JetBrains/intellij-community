#!/bin/zsh

source "${0:A:h}/lib.sh"

if [[ -x "$AIR_PACKER_BIN" ]]; then
  actual="$($AIR_PACKER_BIN version | /usr/bin/awk 'NR == 1 { sub(/^Packer v/, ""); print }')"
  [[ "$actual" == "$PACKER_VERSION" ]] || die "unexpected local Packer version: $actual"
  note "Packer $actual already installed in the local VM state directory"
  exit 0
fi

require_command curl
require_command shasum
require_command unzip

mkdir -p "$(dirname "$AIR_PACKER_BIN")"
archive="$(mktemp -t air-packer).zip"
trap '/bin/rm -f "$archive"' EXIT

url="https://releases.hashicorp.com/packer/$PACKER_VERSION/packer_${PACKER_VERSION}_darwin_arm64.zip"
note "downloading Packer $PACKER_VERSION"
curl -fsSL "$url" -o "$archive"
actual_sha="$(shasum -a 256 "$archive" | /usr/bin/awk '{print $1}')"
[[ "$actual_sha" == "$PACKER_DARWIN_ARM64_SHA256" ]] || die "Packer archive checksum mismatch"

unzip -q "$archive" -d "$(dirname "$AIR_PACKER_BIN")"
chmod 0755 "$AIR_PACKER_BIN"
[[ "$($AIR_PACKER_BIN version | /usr/bin/awk 'NR == 1 {print $2}')" == "v$PACKER_VERSION" ]] || \
  die "installed Packer did not report the expected version"
note "installed verified Packer at $AIR_PACKER_BIN"
