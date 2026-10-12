//! `read-file <path>`: one guest file on standard output, unchanged, for the controller's pull.
//!
//! The Tart and Docker exec channels carry bytes unchanged, which ADR 0182 and ADR 0183 measured, so the file needs
//! no text encoding on them. Stdout is the file itself, so the verb answers its envelope on stderr, after the last
//! byte: the success envelope names the size and the SHA-256 of what it wrote ([`FileReceipt`]), and the controller
//! compares them with what arrived. A refusal is the failure envelope on stderr, as for every other verb, and a file
//! that cannot be opened writes nothing on stdout.

use std::fs::File;
use std::io::{self, Read, Write};
use std::path::Path;

use avl_wire::pull::{FileReceipt, ReceiptHasher};
use avl_wire::supervisor::Envelope;
use avl_wire::verb::AgentVerb;

use crate::reply::AgentRefusalExt;
use crate::reply::{self, AgentRefusal, Streams, json_line};

#[cfg(test)]
mod tests;

/// The most bytes one read takes before they go on.
const CHUNK: usize = 64 * 1024;

/// Writes the file at `path` on stdout and its receipt in the success envelope on stderr, and answers the exit status.
///
/// A failed read or write after the first byte is a refusal too, so the exit status says that stdout is not the
/// whole file.
pub(crate) fn read_file(path: &Path, streams: &mut Streams<'_>) -> u8 {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) => return refuse(streams, format!("cannot open {}: {error}", path.display())),
    };
    let receipt = match copy_hashed(file, streams.stdout) {
        Ok(receipt) => receipt,
        Err(error) => return refuse(streams, format!("cannot copy {} to standard output: {error}", path.display())),
    };
    let Some(line) = json_line(&Envelope::success(AgentVerb::ReadFile.as_str(), receipt)) else {
        return refuse(streams, "the receipt could not be encoded".to_owned());
    };
    match streams.stderr.write_all(&line).and_then(|()| streams.stderr.flush()) {
        Ok(()) => 0,
        Err(error) => refuse(streams, format!("cannot write the receipt: {error}")),
    }
}

/// Copies `from` into `to` and answers the receipt of every byte that went through.
fn copy_hashed<W: Write + ?Sized>(mut from: impl Read, to: &mut W) -> io::Result<FileReceipt> {
    let mut hasher = ReceiptHasher::default();
    let mut buffer = vec![0; CHUNK];
    loop {
        let read = match from.read(&mut buffer) {
            Ok(0) => break,
            Ok(read) => read,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error),
        };
        to.write_all(&buffer[..read])?;
        hasher.update(&buffer[..read]);
    }
    to.flush()?;
    Ok(hasher.finish())
}

fn refuse(streams: &mut Streams<'_>, message: String) -> u8 {
    reply::fail(
        streams,
        AgentVerb::ReadFile.as_str(),
        AgentRefusal::for_verb(AgentVerb::ReadFile, message),
    )
}
