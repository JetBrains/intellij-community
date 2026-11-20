//! The line reads of the agent: the frames of the IDE on standard input and the output of an MCP server.

use std::io::{self, BufRead};

/// The next line of `reader` without its line end, or `None` at the end of the input. A line ends with a line feed or
/// with a carriage return and a line feed. The last line can end without either. A byte that is not UTF-8 becomes the
/// replacement character.
pub(crate) fn read_line(reader: &mut impl BufRead) -> io::Result<Option<String>> {
    let mut line = Vec::new();
    if reader.read_until(b'\n', &mut line)? == 0 {
        return Ok(None);
    }
    if line.last() == Some(&b'\n') {
        line.pop();
        if line.last() == Some(&b'\r') {
            line.pop();
        }
    }
    Ok(Some(String::from_utf8_lossy(&line).into_owned()))
}

#[cfg(test)]
mod tests;
