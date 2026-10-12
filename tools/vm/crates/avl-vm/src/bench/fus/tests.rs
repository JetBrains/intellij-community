use pretty_assertions::assert_eq;

use super::{Collector, WELCOME_BECAME_VISIBLE, first, parse};

const VISIBLE: &str = r#"{"group":{"id":"welcome.screen.startup.performance","version":"2"},"event":{"count":1,"data":{"duration_ms":1715,"is_modal":false},"id":"welcome.screen.became.visible"}}"#;

#[test]
fn reads_the_welcome_event() {
    let events = parse(&format!("{VISIBLE}\n")).expect("events");
    let event = first(&events, WELCOME_BECAME_VISIBLE).expect("the event");
    assert_eq!(event.duration_ms(), Some(1715.0));
    assert_eq!(event.is_modal(), Some(false));
}

#[test]
fn refuses_a_line_that_is_not_an_event() {
    let error = parse(&format!("{VISIBLE}\n{{\"group\":1}}\n")).expect_err("a refusal");
    assert!(format!("{error:#}").starts_with("line 2 is not a FUS event: "), "{error:#}");
}

#[test]
fn the_collector_keeps_complete_lines_once() {
    let mut collector = Collector::default();
    collector.add("a\nb");
    collector.add("a\nb\nc\n");
    assert_eq!(collector.text(), "a\nb\nc\n");
    assert!(!collector.has_event(WELCOME_BECAME_VISIBLE));
    collector.add(&format!("{VISIBLE}\n"));
    assert!(collector.has_event(WELCOME_BECAME_VISIBLE));
}

#[test]
fn the_collector_reads_the_log_files_of_a_directory() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    std::fs::write(dir.path().join("1.log"), "x\n").expect("a log");
    std::fs::write(dir.path().join("0.log"), "w\n").expect("a log");
    std::fs::write(dir.path().join("0.meta"), "m\n").expect("a file");
    let mut collector = Collector::default();
    collector.read(dir.path());
    collector.read(&dir.path().join("absent"));
    assert_eq!(collector.text(), "w\nx\n");
}
