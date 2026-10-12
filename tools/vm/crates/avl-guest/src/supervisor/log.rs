//! The tail of a run's log, read without reading the whole file.

use std::fs::File;
use std::io;
use std::os::unix::fs::FileExt;
use std::path::Path;

/// The most a `log` reply carries; the host never learns this budget, it only sees `truncated`.
pub(crate) const MAX_LOG_RESPONSE_BYTES: u64 = 1024 * 1024;
const CHUNK_BYTES: u64 = 64 * 1024;

/// Answers the end of the log at `path` and whether anything before it was left out.
///
/// Backwards in chunks, capped, and stopping once one newline more than `tail` has been read (that newline marks
/// the start of the requested tail). The front boundary of the oldest chunk can land inside a multi-byte
/// character, so its UTF-8 continuation bytes are trimmed; anything else invalid is a child's own bytes and is
/// replaced rather than refused. An absent log is an empty one.
pub(crate) fn read_log_suffix(path: &Path, tail: Option<usize>) -> io::Result<(String, bool)> {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok((String::new(), false)),
        Err(error) => return Err(error),
    };
    let mut position = file.metadata()?.len();
    let mut remaining = MAX_LOG_RESPONSE_BYTES;
    let mut newlines = 0;
    let mut chunks: Vec<Vec<u8>> = Vec::new();
    while position > 0 && remaining > 0 {
        let length = CHUNK_BYTES.min(position).min(remaining);
        position -= length;
        // A chunk is at most CHUNK_BYTES, so it fits in memory by construction.
        let mut chunk = vec![0; usize::try_from(length).unwrap_or(usize::MAX)];
        file.read_exact_at(&mut chunk, position)?;
        remaining -= length;
        #[expect(clippy::naive_bytecount, reason = "a 64 KiB chunk per step; not worth the bytecount crate")]
        let found = chunk.iter().filter(|&&byte| byte == b'\n').count();
        newlines += found;
        chunks.push(chunk);
        if tail.is_some_and(|tail| newlines > tail) {
            break;
        }
    }
    chunks.reverse();
    let mut content = chunks.concat();
    if position > 0 {
        let boundary = content.iter().take_while(|&&byte| byte & 0xc0 == 0x80).count();
        content.drain(..boundary);
    }
    Ok((String::from_utf8_lossy(&content).into_owned(), position > 0))
}

/// Keeps the last `tail` complete lines of `raw`, and answers whether it had more.
///
/// The trailing fragment after the last newline is dropped: a line the child is still writing is not a line yet.
pub(crate) fn last_complete_lines(raw: &str, tail: usize) -> (String, bool) {
    let normalized = raw.replace("\r\n", "\n");
    let Some((complete, _fragment)) = normalized.rsplit_once('\n') else {
        return (String::new(), false);
    };
    let lines: Vec<&str> = complete.split('\n').collect();
    let kept = &lines[lines.len().saturating_sub(tail)..];
    let mut content = kept.join("\n");
    content.push('\n');
    (content, lines.len() > tail)
}
