use pretty_assertions::assert_eq;

use super::*;

fn captured(exit_code: i32, stdout: &str, stderr: &str) -> Captured {
    Captured {
        exit_code,
        stdout: stdout.to_owned(),
        stderr: stderr.to_owned(),
        ..Captured::default()
    }
}

// The tool's own codes are its answer. A shell's cannot-run codes and a death by signal are not.
#[test]
fn every_exit_code_reads_as_one_kind_of_probe_exit() {
    for (code, exit) in [
        (0, ProbeExit::Answered),
        (1, ProbeExit::Negative),
        (2, ProbeExit::Negative),
        (125, ProbeExit::Negative),
        (128, ProbeExit::Negative),
        (126, ProbeExit::CannotRun),
        (127, ProbeExit::CannotRun),
        (128 + 9, ProbeExit::Killed),
        (128 + 15, ProbeExit::Killed),
        (255, ProbeExit::Killed),
        (-1, ProbeExit::Killed),
    ] {
        assert_eq!(captured(code, "", "").probe_exit(), exit, "{code}");
    }
}

// The refusal names the command by its program name, the exit code and both streams.
#[test]
fn an_unanswered_host_probe_quotes_both_streams() {
    let argv = ["/opt/bin/docker", "inspect", "w"].map(str::to_owned);
    let refusal = probe_unanswered(&argv, &captured(137, "", "Killed: 9\n"), ProbeOutput::Quoted);
    assert_eq!(refusal.code, "probe_unanswered");
    assert_eq!(refusal.exit, Exit::TEMP_FAIL);
    assert!(
        refusal
            .message
            .starts_with("`docker inspect w` exited with 137 and printed \"\", and on stderr \"Killed: 9\", which is no answer"),
        "{}",
        refusal.message
    );
    let details = refusal.details().expect("the refusal holds details");
    assert_eq!(details["exitCode"], 137);
    assert_eq!(details["stderr"], "Killed: 9\n");
}

// A guest probe can print the bridge token, so its refusal holds no output.
#[test]
fn an_unanswered_guest_probe_withholds_its_output() {
    let argv = ["prlctl", "exec", "w", "/usr/bin/defaults read"].map(str::to_owned);
    let refusal = probe_unanswered(&argv, &captured(0, "token=secret", "secret"), ProbeOutput::Withheld);
    assert!(!refusal.message.contains("secret"), "{}", refusal.message);
    assert!(!refusal.details().expect("details").to_string().contains("secret"));
}
