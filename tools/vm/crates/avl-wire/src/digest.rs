//! The shape of every digest on these wires: the sha256 of the bytes, as lowercase hex text.

#[cfg(test)]
mod tests;

/// Whether `text` has the shape of every digest on these wires: sha256, lowercase hex, as text.
pub fn is_sha256_hex(text: &str) -> bool {
    text.len() == 64 && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}
