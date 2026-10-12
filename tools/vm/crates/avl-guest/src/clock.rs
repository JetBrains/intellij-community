//! The one timestamp shape every guest document carries: UTC with milliseconds, `2026-09-28T12:00:00.005Z`.
//!
//! The reconciler parses `createdAt` back, so the shape is the one this module both writes and reads.

use jiff::Timestamp;

/// Renders `at` the way every guest document spells a time.
pub(crate) fn stamp(at: Timestamp) -> String {
    at.strftime("%Y-%m-%dT%H:%M:%S%.3fZ").to_string()
}

/// The current time, rendered.
pub(crate) fn now_stamp() -> String {
    stamp(Timestamp::now())
}

/// Reads a stamp back; `None` for anything that is not a time.
pub(crate) fn parse_stamp(text: &str) -> Option<Timestamp> {
    text.parse().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_stamp_is_utc_with_milliseconds_and_reads_back() {
        let at: Timestamp = "2026-09-26T07:08:09.5Z".parse().unwrap();
        assert_eq!(stamp(at), "2026-09-26T07:08:09.500Z");
        assert_eq!(parse_stamp("2026-09-26T07:08:09.500Z"), Some(at));
        assert_eq!(parse_stamp("yesterday"), None);
        assert_eq!(stamp(Timestamp::UNIX_EPOCH), "1970-01-01T00:00:00.000Z");
    }
}
