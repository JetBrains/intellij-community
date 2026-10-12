//! The pull of one guest file without an encoding: what the agent's `read-file` verb names after the bytes.
//!
//! The verb writes the file on standard output and then its success envelope on standard error, with this receipt as
//! the data. The controller counts and hashes what arrived, and a receipt that names other bytes is a transfer that
//! changed them.

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

#[cfg(test)]
mod tests;

/// The size and the SHA-256 of the bytes one `read-file` wrote.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct FileReceipt {
    pub bytes: u64,
    /// Lowercase hex.
    pub sha256: String,
}

impl FileReceipt {
    /// The receipt of `content`.
    pub fn of(content: &[u8]) -> Self {
        let mut hasher = ReceiptHasher::default();
        hasher.update(content);
        hasher.finish()
    }
}

/// Counts and hashes bytes as they go through, so the sender and the receiver of a stream name it the same way.
#[derive(Default)]
pub struct ReceiptHasher {
    bytes: u64,
    hasher: Sha256,
}

impl ReceiptHasher {
    pub fn update(&mut self, chunk: &[u8]) {
        self.hasher.update(chunk);
        self.bytes += chunk.len() as u64;
    }

    /// The receipt of every byte so far.
    pub fn finish(self) -> FileReceipt {
        FileReceipt {
            bytes: self.bytes,
            sha256: hex::encode(self.hasher.finalize()),
        }
    }
}
