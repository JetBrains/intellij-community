use std::os::unix::process::ExitStatusExt;
use std::process::ExitStatus;

use pretty_assertions::assert_eq;

use super::*;

fn output(code: i32, stdout: &str, stderr: &str) -> Output {
    Output {
        status: ExitStatus::from_raw(code << 8),
        stdout: stdout.as_bytes().to_vec(),
        stderr: stderr.as_bytes().to_vec(),
    }
}

/// A refusal of bd keeps the code and the message of its error object, and a failure without one keeps its stderr
#[test]
fn a_failed_call_names_the_code_of_the_error_object_of_bd() {
    let repo = Path::new("/r");
    assert_eq!(
        answer(repo, "have", &output(0, "{\"schema\":2}\n", "")),
        Ok("{\"schema\":2}".to_owned())
    );
    let refused = answer(
        repo,
        "snapshot",
        &output(
            4,
            r#"{"schema":2,"error":{"code":"no_lease","message":"the run has no slot"}}"#,
            "progress",
        ),
    )
    .expect_err("refused");
    assert_eq!(refused.code.as_deref(), Some("no_lease"));
    assert_eq!(
        refused.message,
        "bd lease host snapshot failed in /r: the run has no slot (exit status: 4)"
    );
    let crashed = answer(repo, "have", &output(1, "", "a panic")).expect_err("failed");
    assert_eq!(crashed.code, None);
    assert_eq!(crashed.message, "bd lease host have failed in /r: a panic (exit status: 1)");
}
