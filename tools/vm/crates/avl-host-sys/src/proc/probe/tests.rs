#![cfg(unix)]

use pretty_assertions::assert_eq;

use super::PsField;
use super::unix::answer;
use crate::proc::Captured;

fn ps(exit_code: i32, stdout: &str) -> Captured {
    Captured {
        exit_code,
        stdout: stdout.to_owned(),
        ..Captured::default()
    }
}

fn argv() -> Vec<String> {
    ["/bin/ps", "-o", "stat=", "-p", "42"].map(str::to_owned).to_vec()
}

// A field is the answer, collapsed where the field is compared, and `ps` that lists no such pid is no such process.
#[test]
fn ps_answers_a_field_or_no_such_process() {
    assert_eq!(
        answer(&argv(), PsField::StartTime, &ps(0, "Fri Oct  2 16:00:00 2026\n")).unwrap(),
        Some("Fri Oct 2 16:00:00 2026".to_owned())
    );
    assert_eq!(
        answer(&argv(), PsField::Command, &ps(0, "tart  run w\n")).unwrap(),
        Some("tart  run w".to_owned())
    );
    assert_eq!(answer(&argv(), PsField::State, &ps(1, "")).unwrap(), None);
}

// A `ps` that printed nothing, and one that a signal ended, say nothing about the process.
#[test]
fn ps_without_an_answer_is_a_refusal_and_not_a_dead_process() {
    for captured in [ps(0, ""), ps(0, " \n"), ps(128 + 9, ""), ps(127, "")] {
        let refusal = answer(&argv(), PsField::State, &captured).unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{captured:?}");
        assert!(
            refusal.message.starts_with("`ps -o stat= -p 42` exited with"),
            "{}",
            refusal.message
        );
    }
}
