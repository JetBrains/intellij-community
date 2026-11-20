//! The fake ACP agent package that the lane's registry serves: one archive for the platform of the guest, and its
//! checksum.
//!
//! The archive holds the fake agent binary of the guest as `bin/acp-fake-agent`, which the manifest names as its
//! command with no argument. The registry lists the platform of the guest alone, so an IDE of another platform finds no
//! package, and its install fails.

use std::fs;
use std::path::Path;

use sha2::{Digest, Sha256};

use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The version that `registry.json` gives every agent.
pub(crate) const VERSION: &str = "1.0.0+air-fixture-1";

/// The value of the agent's package token variable, which proves to the agent that the registry installed it.
pub(crate) const PACKAGE_TOKEN: &str = "air-scenario-package-v1";

/// The refusal code of an input that the package cannot use.
pub(crate) const INPUT_CODE: &str = "lane_package_input";

/// The entry of the fake agent binary in the archive, and the manifest command.
pub(crate) const BINARY_ENTRY: &str = "bin/acp-fake-agent";

/// The archive of the guest platform.
pub(crate) struct Packages {
    platform: &'static str,
    bytes: Vec<u8>,
    sha256: String,
}

impl Packages {
    /// Packs the fake agent binary at `agent` for the registry key `platform`, which [guest_platform] answers for a
    /// lane.
    pub(crate) fn assemble(agent: &Path, platform: &'static str) -> Result<Self, AgentRefusal> {
        let binary = fs::read(agent)
            .map_err(|error| AgentRefusal::failure(INPUT_CODE, format!("cannot read the fake agent {}: {error}", agent.display())))?;
        let bytes = stored_zip(&[(BINARY_ENTRY, &binary)])
            .map_err(|message| AgentRefusal::failure(INPUT_CODE, format!("cannot pack the fake agent {}: {message}", agent.display())))?;
        let sha256 = sha256_hex(&bytes);
        Ok(Self { platform, bytes, sha256 })
    }

    /// The registry key of the one archive, the platform of this guest, such as `linux-aarch64`.
    pub(crate) const fn platform(&self) -> &'static str {
        self.platform
    }

    /// The archive of `platform`, or `None` for another platform than [Self::platform].
    pub(crate) fn archive(&self, platform: &str) -> Option<&[u8]> {
        (platform == self.platform).then_some(self.bytes.as_slice())
    }

    /// The lowercase hex SHA-256 of the archive.
    pub(crate) fn sha256(&self) -> &str {
        &self.sha256
    }

    /// The manifest `cmd` of the archive: the packaged binary.
    pub(crate) fn command() -> String {
        format!("./{BINARY_ENTRY}")
    }
}

/// The registry key of the platform that this guest agent runs on: the OS as the registry names it, and the CPU.
pub(crate) fn guest_platform() -> &'static str {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => "darwin-aarch64",
        ("macos", _) => "darwin-x86_64",
        ("windows", "aarch64") => "windows-aarch64",
        ("windows", _) => "windows-x86_64",
        (_, "aarch64") => "linux-aarch64",
        _ => "linux-x86_64",
    }
}

/// The lowercase hex SHA-256 of `bytes`, as the registry `sha256` field states it.
pub(crate) fn sha256_hex(bytes: &[u8]) -> String {
    hex::encode(Sha256::digest(bytes))
}

/// The general purpose flag of every entry: the name is UTF-8.
const UTF8_FLAG: u16 = 0x0800;

/// The version needed to extract a stored entry, which is also the version made by, with MS-DOS as the host.
const STORED_VERSION: u16 = 10;

/// The MS-DOS time of an entry time before 1980: 1980-01-01 00:00:00.
const DOS_TIME_BEFORE_1980: u32 = (1 << 21) | (1 << 16);

/// The extended timestamp extra field with the modification time 0: the id `UT`, 5 data bytes, the flag of the
/// modification time, and the time.
const EXTENDED_TIMESTAMP: [u8; 9] = [0x55, 0x54, 5, 0, 1, 0, 0, 0, 0];

/// Writes `entries` in the given order as one zip archive with every entry STORED at the entry time 0.
///
/// The layout is the one that `java.util.zip.ZipOutputStream` writes for a STORED entry with a known size, CRC and
/// the time 0, which the Java packer of the fake agent used: the UTF-8 flag, MS-DOS as the host, no file attributes,
/// and the extended timestamp. It is written by hand, because the `zip` crate writes a Unix host and file
/// attributes, which an installer would apply. An entry or an archive past 4 GiB is refused, because the layout
/// has no ZIP64 records.
fn stored_zip(entries: &[(&str, &[u8])]) -> Result<Vec<u8>, String> {
    let mut archive = Vec::new();
    let mut central = Vec::new();
    for &(name, data) in entries {
        let offset = u32_of(archive.len(), "the archive")?;
        let size = u32_of(data.len(), name)?;
        let name_length = u16::try_from(name.len()).map_err(|_overflow| format!("the entry name {name} is too long"))?;
        let crc = crc32(data);

        archive.extend_from_slice(&0x0403_4b50_u32.to_le_bytes());
        archive.extend_from_slice(&STORED_VERSION.to_le_bytes());
        push_entry_fields(&mut archive, crc, size, name_length);
        archive.extend_from_slice(name.as_bytes());
        archive.extend_from_slice(&EXTENDED_TIMESTAMP);
        archive.extend_from_slice(data);

        central.extend_from_slice(&0x0201_4b50_u32.to_le_bytes());
        central.extend_from_slice(&STORED_VERSION.to_le_bytes());
        central.extend_from_slice(&STORED_VERSION.to_le_bytes());
        push_entry_fields(&mut central, crc, size, name_length);
        // The comment length, the disk number, the internal and the external file attributes.
        central.extend_from_slice(&[0; 10]);
        central.extend_from_slice(&offset.to_le_bytes());
        central.extend_from_slice(name.as_bytes());
        central.extend_from_slice(&EXTENDED_TIMESTAMP);
    }
    let central_offset = u32_of(archive.len(), "the archive")?;
    let central_size = u32_of(central.len(), "the central directory")?;
    let count = u16::try_from(entries.len()).map_err(|_overflow| "the archive has too many entries".to_owned())?;
    archive.extend_from_slice(&central);
    archive.extend_from_slice(&0x0605_4b50_u32.to_le_bytes());
    archive.extend_from_slice(&[0; 4]);
    archive.extend_from_slice(&count.to_le_bytes());
    archive.extend_from_slice(&count.to_le_bytes());
    archive.extend_from_slice(&central_size.to_le_bytes());
    archive.extend_from_slice(&central_offset.to_le_bytes());
    archive.extend_from_slice(&[0; 2]);
    Ok(archive)
}

/// The fields that the local header and the central header share, from the flag to the extra field length.
fn push_entry_fields(target: &mut Vec<u8>, crc: u32, size: u32, name_length: u16) {
    target.extend_from_slice(&UTF8_FLAG.to_le_bytes());
    // The method STORED.
    target.extend_from_slice(&[0; 2]);
    target.extend_from_slice(&DOS_TIME_BEFORE_1980.to_le_bytes());
    target.extend_from_slice(&crc.to_le_bytes());
    target.extend_from_slice(&size.to_le_bytes());
    target.extend_from_slice(&size.to_le_bytes());
    target.extend_from_slice(&name_length.to_le_bytes());
    let extra_length = u16::try_from(EXTENDED_TIMESTAMP.len()).unwrap_or(u16::MAX);
    target.extend_from_slice(&extra_length.to_le_bytes());
}

fn u32_of(length: usize, what: &str) -> Result<u32, String> {
    u32::try_from(length).map_err(|_overflow| format!("{what} is larger than 4 GiB"))
}

/// The CRC-32 of the zip format. It is written by hand, because the crate that `flate2` links is no direct dependency
/// here, and a new one changes the lock of the guest agent.
fn crc32(bytes: &[u8]) -> u32 {
    !bytes.iter().fold(!0_u32, |crc, &byte| {
        CRC_TABLE[usize::from(crc.to_le_bytes()[0] ^ byte)] ^ (crc >> 8)
    })
}

const CRC_TABLE: [u32; 256] = {
    let mut table = [0_u32; 256];
    let mut index = 0;
    while index < 256 {
        #[expect(clippy::cast_possible_truncation, reason = "the index is below 256")]
        let mut crc = index as u32;
        let mut bit = 0;
        while bit < 8 {
            crc = if crc & 1 == 1 { 0xEDB8_8320 ^ (crc >> 1) } else { crc >> 1 };
            bit += 1;
        }
        table[index] = crc;
        index += 1;
    }
    table
};
