//! The marker of a prompt whose answer the deterministic agent holds back while a file exists, as `AcpPromptHold` of
//! `plugins/air/integrationTests/acp-protocol` writes it: `[hold-while-exists:<file URI>]`.

use std::path::PathBuf;

const MARKER_PREFIX: &str = "[hold-while-exists:";

/// The file that the first marker in `text` names, or `None` for a text without a marker of a `file` URI.
pub(crate) fn hold_file_of(text: &str) -> Option<PathBuf> {
    let start = text.find(MARKER_PREFIX)? + MARKER_PREFIX.len();
    let end = start + text[start..].find(']')?;
    file_path(&text[start..end])
}

/// The path of the `file` URI `uri`: `file:///<path>` or `file:/<path>`, with no authority, query or fragment. A
/// percent escape is decoded. A character that a URI cannot hold unescaped refuses the URI.
fn file_path(uri: &str) -> Option<PathBuf> {
    let (scheme, rest) = uri.split_once(':')?;
    if !scheme.eq_ignore_ascii_case("file") {
        return None;
    }
    // After `//` comes the authority, which must be empty, so the path starts at once.
    let path = rest.strip_prefix("//").unwrap_or(rest);
    if !path.starts_with('/')
        || path.contains(['?', '#'])
        || path
            .chars()
            .any(|character| character <= ' ' || "[]<>\"{}|\\^`".contains(character))
    {
        return None;
    }
    let decoded = percent_decode(path)?;
    #[cfg(windows)]
    let decoded = decoded.strip_prefix('/').map(str::to_owned).unwrap_or(decoded);
    Some(PathBuf::from(decoded))
}

/// `text` with each `%XX` escape decoded, or `None` for a broken escape or for bytes that are not UTF-8.
fn percent_decode(text: &str) -> Option<String> {
    let bytes = text.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index] == b'%' {
            let hex = text.get(index + 1..index + 3)?;
            decoded.push(u8::from_str_radix(hex, 16).ok()?);
            index += 3;
        } else {
            decoded.push(bytes[index]);
            index += 1;
        }
    }
    String::from_utf8(decoded).ok()
}

/// The marker that holds the answer to a prompt while `file` exists, as `AcpPromptHold.marker` writes it.
#[cfg(test)]
pub(crate) fn marker(file: &std::path::Path) -> String {
    let path = file.to_string_lossy().replace('\\', "/");
    let path = if path.starts_with('/') { path } else { format!("/{path}") };
    let mut escaped = String::new();
    for byte in path.bytes() {
        if byte.is_ascii_alphanumeric() || b"/-._~:".contains(&byte) {
            escaped.push(char::from(byte));
        } else {
            escaped.push_str(&format!("%{byte:02X}"));
        }
    }
    format!("{MARKER_PREFIX}file://{escaped}]")
}

#[cfg(test)]
mod tests;
