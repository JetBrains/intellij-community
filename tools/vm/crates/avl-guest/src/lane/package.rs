//! The fake ACP agent package that the lane's registry serves: one archive per platform and its checksum.
//!
//! Each archive holds the agent jar as `lib/acp-scenario-agent.jar`. A POSIX archive also holds the launcher
//! `bin/acp-scenario-agent`, which runs the `java` of the distribution's JBR by its absolute path. A Windows archive has
//! no launcher, because its manifest command is `java.exe` itself.

use std::fs;
use std::path::Path;

use sha2::{Digest, Sha256};

use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The platform keys of the registry, in the order that `registry.json` lists them.
pub(crate) const PLATFORMS: [&str; 6] = [
    "darwin-aarch64",
    "darwin-x86_64",
    "linux-aarch64",
    "linux-x86_64",
    "windows-aarch64",
    "windows-x86_64",
];

/// The version that `registry.json` gives every agent.
pub(crate) const VERSION: &str = "1.0.0+air-fixture-1";

/// The value of the agent's package token variable, which proves to the agent that the registry installed it.
pub(crate) const PACKAGE_TOKEN: &str = "air-scenario-package-v1";

/// The refusal code of an input that the package cannot use.
pub(crate) const INPUT_CODE: &str = "lane_package_input";

const JAR_ENTRY: &str = "lib/acp-scenario-agent.jar";
const LAUNCHER_ENTRY: &str = "bin/acp-scenario-agent";

/// The six archives and the two Java commands that their manifests name.
pub(crate) struct Packages {
    archives: Vec<Archive>,
    windows_java: String,
}

struct Archive {
    platform: &'static str,
    bytes: Vec<u8>,
    sha256: String,
}

impl Packages {
    /// Packs the agent jar at `agent_jar` for every platform. `java` is the absolute path of `bin/java` of the
    /// distribution's JBR, which the POSIX launcher runs. The Windows command is `java.exe` beside it.
    pub(crate) fn assemble(agent_jar: &Path, java: &Path) -> Result<Self, AgentRefusal> {
        let posix_java = absolute_text(java)?;
        let windows_java = absolute_text(&java.with_file_name("java.exe"))?;
        let jar = fs::read(agent_jar)
            .map_err(|error| AgentRefusal::failure(INPUT_CODE, format!("cannot read the agent jar {}: {error}", agent_jar.display())))?;
        let launcher = launcher(&posix_java);
        let archives = PLATFORMS
            .iter()
            .map(|&platform| {
                let mut entries: Vec<(&str, &[u8])> = Vec::new();
                if !is_windows(platform) {
                    entries.push((LAUNCHER_ENTRY, launcher.as_bytes()));
                }
                entries.push((JAR_ENTRY, &jar));
                let bytes = stored_zip(&entries).map_err(|message| {
                    AgentRefusal::failure(INPUT_CODE, format!("cannot pack the agent jar {}: {message}", agent_jar.display()))
                })?;
                let sha256 = sha256_hex(&bytes);
                Ok(Archive { platform, bytes, sha256 })
            })
            .collect::<Result<_, AgentRefusal>>()?;
        Ok(Self { archives, windows_java })
    }

    /// The archive of `platform`, or `None` for a key that [PLATFORMS] does not list.
    pub(crate) fn archive(&self, platform: &str) -> Option<&[u8]> {
        self.find(platform).map(|archive| archive.bytes.as_slice())
    }

    /// The lowercase hex SHA-256 of the archive of `platform`.
    pub(crate) fn sha256(&self, platform: &str) -> Option<&str> {
        self.find(platform).map(|archive| archive.sha256.as_str())
    }

    /// The manifest `cmd` of `platform`: the packaged launcher, or `java.exe` itself on Windows.
    pub(crate) fn command(&self, platform: &str) -> String {
        if is_windows(platform) {
            self.windows_java.clone()
        } else {
            format!("./{LAUNCHER_ENTRY}")
        }
    }

    /// The manifest `args` of `platform`: what the Windows `java.exe` command needs, and nothing otherwise.
    pub(crate) fn args(platform: &str) -> Vec<String> {
        if is_windows(platform) {
            vec!["-jar".to_owned(), format!("./{JAR_ENTRY}")]
        } else {
            Vec::new()
        }
    }

    fn find(&self, platform: &str) -> Option<&Archive> {
        self.archives.iter().find(|archive| archive.platform == platform)
    }
}

fn is_windows(platform: &str) -> bool {
    platform.starts_with("windows-")
}

/// The text of an absolute path, which a launcher and a manifest spell verbatim.
fn absolute_text(path: &Path) -> Result<String, AgentRefusal> {
    match path.to_str() {
        Some(text) if path.is_absolute() => Ok(text.to_owned()),
        _ => Err(AgentRefusal::failure(
            INPUT_CODE,
            format!("the Java command {} is not an absolute UTF-8 path", path.display()),
        )),
    }
}

/// The POSIX launcher. It keeps `exec`, so the agent owns its stdout and a reader in the IDE sees EOF when the agent
/// closes it.
pub(crate) fn launcher(java: &str) -> String {
    format!(
        "#!/bin/sh\nSCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\nexec \"{java}\" -jar \"$SCRIPT_DIR/../lib/acp-scenario-agent.jar\" \"$@\"\n"
    )
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
