use super::*;

/// A flood writes its first event at once, then one line per minute with the count of the events since the last line.
#[test]
fn a_flood_of_events_writes_one_line_per_minute_with_its_count() {
    let tally = Tally::new("refused connections");
    let start = Instant::now();
    assert_eq!(
        tally.note(start, "from 10.0.0.1:1: bad".to_owned()).as_deref(),
        Some("refused connections: 1 since the last line, the last from 10.0.0.1:1: bad")
    );
    for second in 1..60 {
        assert_eq!(
            tally.note(start + Duration::from_secs(second), format!("from 10.0.0.{second}:1: bad")),
            None
        );
    }
    assert_eq!(tally.flush(start + Duration::from_secs(59)), None, "a minute did not end");
    assert_eq!(
        tally.flush(start + EVERY).as_deref(),
        Some("refused connections: 59 since the last line, the last from 10.0.0.59:1: bad")
    );
    assert_eq!(tally.flush(start + EVERY * 3), None, "no event since the last line");
    assert_eq!(
        tally.note(start + EVERY * 3, "from 10.0.0.2:1: bad".to_owned()).as_deref(),
        Some("refused connections: 1 since the last line, the last from 10.0.0.2:1: bad")
    );
}
