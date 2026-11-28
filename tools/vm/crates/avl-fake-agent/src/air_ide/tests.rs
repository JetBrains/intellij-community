use std::ffi::OsStr;

use super::*;

#[cfg(unix)]
#[test]
fn a_run_that_exits_with_0_answers_its_output_and_another_answers_an_error_with_its_output() {
    use pretty_assertions::assert_eq;

    let shell = OsStr::new("sh");
    let script = |text: &str| ["-c".to_owned(), text.to_owned()];
    assert_eq!(
        run(shell, &script("printf '{\"viewId\":\"v1\"}'")),
        Outcome {
            is_error: false,
            text: r#"{"viewId":"v1"}"#.to_owned()
        }
    );
    let failed = run(shell, &script("echo refused >&2; echo partial; exit 2"));
    assert!(failed.is_error);
    assert!(
        failed.text.contains("refused") && failed.text.contains("partial"),
        "{}",
        failed.text
    );
}

#[test]
fn a_program_that_does_not_exist_answers_an_error() {
    let missing = run(OsStr::new("acp-fake-agent-no-such-program"), &["ui".to_owned()]);
    assert!(missing.is_error);
    assert!(missing.text.contains("did not start"), "{}", missing.text);
}
