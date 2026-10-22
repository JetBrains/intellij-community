//! The reader of `idea.log`: the first line that holds a text, and its time.
//!
//! A line starts with `<yyyy-MM-dd HH:mm:ss,SSS> [<ms>]`. The first part is the local time of the IDE. The second part
//! is the milliseconds from the creation of the log, which the IDE takes after the process start. A line of the log
//! that holds the text but has another start is an error that names the line.

use anyhow::{Context, bail};
use jiff::civil::DateTime;
use jiff::tz::{Offset, TimeZone};

/// One line of `idea.log` and its two times.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LogLine {
    /// The local time of the IDE, to the millisecond.
    pub(crate) local: DateTime,
    /// The milliseconds from the creation of the log, the `[...]` column.
    pub(crate) log_ms: i64,
}

impl LogLine {
    /// The milliseconds since the epoch of the line, with `utc_offset_s`, the UTC offset of the IDE in seconds.
    pub(crate) fn epoch_ms(&self, utc_offset_s: i32) -> anyhow::Result<i64> {
        let offset = Offset::from_seconds(utc_offset_s).with_context(|| format!("the UTC offset {utc_offset_s} s"))?;
        let zoned = self
            .local
            .to_zoned(TimeZone::fixed(offset))
            .with_context(|| format!("the local time {}", self.local))?;
        Ok(zoned.timestamp().as_millisecond())
    }
}

/// The first line of `text` that holds `needle`. A log without such a line has none.
pub(crate) fn first_line(text: &str, needle: &str) -> anyhow::Result<Option<LogLine>> {
    let Some((index, line)) = text.lines().enumerate().find(|(_, line)| line.contains(needle)) else {
        return Ok(None);
    };
    parse_line(line).with_context(|| format!("line {}: {line}", index + 1)).map(Some)
}

/// The times at the start of one line.
fn parse_line(line: &str) -> anyhow::Result<LogLine> {
    let Some((stamp, rest)) = line.split_at_checked(23) else {
        bail!("no `yyyy-MM-dd HH:mm:ss,SSS` at the start");
    };
    let local: DateTime = stamp
        .replacen(',', ".", 1)
        .parse()
        .with_context(|| format!("the time `{stamp}` is not `yyyy-MM-dd HH:mm:ss,SSS`"))?;
    let Some(column) = rest
        .strip_prefix(" [")
        .and_then(|rest| rest.split_once(']'))
        .map(|(column, _)| column.trim())
    else {
        bail!("no `[<ms>]` after the time");
    };
    let log_ms = column
        .parse()
        .with_context(|| format!("the column `[{column}]` is not a number of milliseconds"))?;
    Ok(LogLine { local, log_ms })
}

#[cfg(test)]
mod tests;
