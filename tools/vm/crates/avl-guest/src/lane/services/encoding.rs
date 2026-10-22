//! The encodings of the JetBrains Cloud emulator and of its Agent Spawner: base64 in its two alphabets, and SHA-1.
//!
//! They are written by hand, because no crate in the pinned closure of the guest agent has them.

#[cfg(test)]
mod tests;

/// One alphabet of base64, RFC 4648.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Base64 {
    /// The standard alphabet with the `=` padding, which the WebSocket handshake and the control protocol use.
    Standard,
    /// The URL alphabet without padding, which a JSON Web Token uses.
    Url,
}

impl Base64 {
    const fn alphabet(self) -> &'static [u8; 64] {
        match self {
            Self::Standard => b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",
            Self::Url => b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_",
        }
    }

    const fn padded(self) -> bool {
        matches!(self, Self::Standard)
    }

    /// The base64 text of `bytes`.
    pub(crate) fn encode(self, bytes: &[u8]) -> String {
        let alphabet = self.alphabet();
        let mut text = String::with_capacity(bytes.len().div_ceil(3) * 4);
        for chunk in bytes.chunks(3) {
            let triple = chunk
                .iter()
                .enumerate()
                .fold(0_u32, |triple, (index, &byte)| triple | u32::from(byte) << (16 - 8 * index));
            for index in 0..=chunk.len() {
                let sextet = (triple >> (18 - 6 * index)) & 0x3f;
                text.push(char::from(alphabet[usize::try_from(sextet).unwrap_or_default()]));
            }
            if self.padded() {
                text.extend(std::iter::repeat_n('=', 3 - chunk.len()));
            }
        }
        text
    }

    /// The bytes of the base64 text `text`, or `None` for a character outside the alphabet or a misplaced padding.
    pub(crate) fn decode(self, text: &str) -> Option<Vec<u8>> {
        let body = if self.padded() {
            if !text.len().is_multiple_of(4) {
                return None;
            }
            text.strip_suffix("==").or_else(|| text.strip_suffix('=')).unwrap_or(text)
        } else {
            text
        };
        let alphabet = self.alphabet();
        let mut bytes = Vec::with_capacity(body.len() / 4 * 3 + 2);
        for chunk in body.as_bytes().chunks(4) {
            if chunk.len() == 1 {
                return None;
            }
            let mut joined = 0_u32;
            for (index, byte) in chunk.iter().enumerate() {
                let sextet = alphabet.iter().position(|known| known == byte)?;
                joined |= u32::try_from(sextet).ok()? << (18 - 6 * index);
            }
            bytes.extend_from_slice(&joined.to_be_bytes()[1..chunk.len()]);
        }
        Some(bytes)
    }
}

/// The SHA-1 digest of `bytes`, FIPS 180-4. The WebSocket handshake names this digest, and it needs no collision
/// resistance.
#[expect(clippy::many_single_char_names, reason = "the five working variables have the names of FIPS 180-4")]
pub(crate) fn sha1(bytes: &[u8]) -> [u8; 20] {
    let mut state: [u32; 5] = [0x6745_2301, 0xEFCD_AB89, 0x98BA_DCFE, 0x1032_5476, 0xC3D2_E1F0];
    let mut message = bytes.to_vec();
    message.push(0x80);
    while message.len() % 64 != 56 {
        message.push(0);
    }
    let bits = u64::try_from(bytes.len()).unwrap_or(u64::MAX).wrapping_mul(8);
    message.extend_from_slice(&bits.to_be_bytes());
    for block in message.chunks_exact(64) {
        let mut schedule = [0_u32; 80];
        for (word, quad) in schedule.iter_mut().zip(block.chunks_exact(4)) {
            *word = u32::from_be_bytes([quad[0], quad[1], quad[2], quad[3]]);
        }
        for index in 16..80 {
            schedule[index] = (schedule[index - 3] ^ schedule[index - 8] ^ schedule[index - 14] ^ schedule[index - 16]).rotate_left(1);
        }
        let [mut a, mut b, mut c, mut d, mut e] = state;
        for (index, word) in schedule.iter().enumerate() {
            let (f, k) = match index {
                0..20 => ((b & c) | (!b & d), 0x5A82_7999),
                20..40 => (b ^ c ^ d, 0x6ED9_EBA1),
                40..60 => ((b & c) | (b & d) | (c & d), 0x8F1B_BCDC),
                _ => (b ^ c ^ d, 0xCA62_C1D6),
            };
            let next = a.rotate_left(5).wrapping_add(f).wrapping_add(e).wrapping_add(k).wrapping_add(*word);
            (e, d, c, b, a) = (d, c, b.rotate_left(30), a, next);
        }
        for (value, add) in state.iter_mut().zip([a, b, c, d, e]) {
            *value = value.wrapping_add(add);
        }
    }
    let mut digest = [0; 20];
    for (quad, value) in digest.chunks_exact_mut(4).zip(state) {
        quad.copy_from_slice(&value.to_be_bytes());
    }
    digest
}
