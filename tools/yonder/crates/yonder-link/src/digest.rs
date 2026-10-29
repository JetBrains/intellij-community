//! The SHA-256 of an agent binary as 64 lower-case hex digits: the name of a build in [`Request::Upgrade`] and in the
//! hello of an agent. The version of the crate is the same for every build, so only the SHA-256 tells two builds apart.
//!
//! [`Request::Upgrade`]: crate::proto::Request::Upgrade

use std::fs::File;
use std::io::{self, Read};
use std::path::Path;

/// A SHA-256 that takes its bytes in parts.
pub struct Sha256(ring::digest::Context);

impl Default for Sha256 {
    fn default() -> Self {
        Self(ring::digest::Context::new(&ring::digest::SHA256))
    }
}

impl Sha256 {
    pub fn update(&mut self, bytes: &[u8]) {
        self.0.update(bytes);
    }

    /// The digest as 64 lower-case hex digits.
    pub fn hex(self) -> String {
        self.0.finish().as_ref().iter().map(|byte| format!("{byte:02x}")).collect()
    }
}

/// The SHA-256 of the file `path`.
pub fn sha256_file(path: &Path) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut digest = Sha256::default();
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        match file.read(&mut buffer) {
            Ok(0) => return Ok(digest.hex()),
            Ok(read) => digest.update(&buffer[..read]),
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => return Err(error),
        }
    }
}

/// Whether `text` is 64 lower-case hex digits.
pub fn is_sha256(text: &str) -> bool {
    text.len() == 64 && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}

#[cfg(test)]
mod tests;
