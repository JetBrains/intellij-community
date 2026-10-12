//! Short renderings that the controller's lines share: elapsed times, seconds, percentages, a shell word, an
//! argv, a bounded quote of foreign text and a message's first line. One home, so two lines that spell the same
//! thing cannot disagree.

use std::time::Duration;

/// `850ms`, `12s` or `3m05s`: short enough for a footer.
pub fn format_elapsed(elapsed: Duration) -> String {
    let seconds = elapsed.as_secs();
    if seconds == 0 {
        return format!("{}ms", elapsed.as_millis());
    }
    if seconds < 60 {
        return format!("{seconds}s");
    }
    format!("{}m{:02}s", seconds / 60, seconds % 60)
}

/// [`format_elapsed`] of a millisecond count.
pub fn format_elapsed_ms(milliseconds: u64) -> String {
    format_elapsed(Duration::from_millis(milliseconds))
}

/// A value with exactly one fraction digit: `1.3`.
pub fn one_decimal(value: f64) -> String {
    format!("{value:.1}")
}

/// A millisecond duration as the timing lines spell it: `1.3s`.
pub fn seconds(milliseconds: f64) -> String {
    format!("{:.1}s", milliseconds / 1000.0)
}

/// A millisecond duration in whole seconds, without the unit: `84` for 84 000 ms.
pub fn whole_seconds(milliseconds: f64) -> String {
    format!("{:.0}", milliseconds / 1000.0)
}

/// A fraction as a whole percentage, without the sign: `37` for 0.37.
pub fn whole_percent(fraction: f64) -> String {
    format!("{:.0}", fraction * 100.0)
}

/// Quotes one value as a single POSIX shell word: `it's` becomes `'it'\''s'`.
///
/// For a here-doc script run in a guest, or a command line a person copies, such as `bt`'s rerun hint, whose
/// Kotlin backticked test names hold apostrophes. The Parallels spelling (`'…'"'"'…'`, which survives a second
/// round of concatenation on the way to `prlctl`) is a sibling and not interchangeable.
pub fn posix_shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

/// An argv from words: `words(["tart", "list"])`, or `words(&["tart", "list"])` in a test.
pub fn words<S: AsRef<str>>(words: impl IntoIterator<Item = S>) -> Vec<String> {
    words.into_iter().map(|word| word.as_ref().to_owned()).collect()
}

/// At most `limit_bytes` of foreign text (a guest's output, a daemon's answer) for a message, backed up to a
/// character boundary so the quoted prefix stays valid text and the envelope never carries half a character. The
/// oldest bytes are kept, because a tool's first complaint is usually the one that explains the rest.
///
/// A byte bound with no marker. `avl-report`'s own clip counts characters and marks the cut, because a run
/// report declares its bounds in characters and publishes whether it truncated.
pub fn clip(text: &str, limit_bytes: usize) -> &str {
    &text[..text.floor_char_boundary(limit_bytes)]
}

/// The first line of a message, without its line break (`\n` or `\r\n`) and without splitting the rest: a caller
/// may hand it an unclipped detail, which is a whole stack trace and occasionally megabytes of captured log.
///
/// The line is taken as written, leading whitespace included, so every renderer of one message shows the same line.
pub fn first_line(text: &str) -> &str {
    text.lines().next().unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn elapsed_times_are_short() {
        assert_eq!(format_elapsed(Duration::from_millis(850)), "850ms");
        assert_eq!(format_elapsed(Duration::from_millis(12_900)), "12s");
        assert_eq!(format_elapsed(Duration::from_secs(185)), "3m05s");
        assert_eq!(format_elapsed_ms(65_000), "1m05s");
    }

    #[test]
    fn seconds_and_percentages_have_the_documented_precision() {
        assert_eq!(seconds(187_670.0), "187.7s");
        assert_eq!(seconds(0.0), "0.0s");
        assert_eq!(one_decimal(2.26), "2.3");
        assert_eq!(whole_seconds(84_300.0), "84");
        assert_eq!(whole_percent(0.374), "37");
        assert_eq!(whole_percent(1.0), "100");
    }

    // The escape is the whole function, and an apostrophe is the only value that can tell a working quoting from
    // a broken one.
    #[test]
    fn posix_shell_quote_closes_and_reopens_around_an_apostrophe() {
        assert_eq!(posix_shell_quote("it's"), r"'it'\''s'");
        // A value with no apostrophe is simply wrapped, which keeps the escape invisible until it matters.
        assert_eq!(posix_shell_quote("plain words"), "'plain words'");
        // Two apostrophes are two escapes, not one.
        assert_eq!(posix_shell_quote("it's it's"), r"'it'\''s it'\''s'");
    }

    #[test]
    fn a_clip_keeps_whole_characters() {
        assert_eq!(clip("abcdef", 10), "abcdef");
        assert_eq!(clip("abcdef", 3), "abc");
        assert_eq!(clip("aé", 2), "a");
    }

    // One semantics for every renderer: the line as written, with a Windows line break gone.
    #[test]
    fn the_first_line_is_taken_as_written() {
        assert_eq!(first_line("boom\r\n\tat Foo.bar"), "boom");
        assert_eq!(first_line("  indented\nrest"), "  indented");
        assert_eq!(first_line(""), "");
    }
}
