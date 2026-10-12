//! How the controller reads the answer of a probe: a command that it asks a question, such as `ps`, `docker inspect`
//! or `prlctl status`, and not a command that it asks to act.
//!
//! A probe that gave no answer is a refusal, never a state. A `cat` that a signal ended inside a fake left its shell to
//! exit 0 with nothing printed, and a `docker inspect` that a signal ended exits 137. Each was once read as a state:
//! an unknown word, no container, a dead process. So every probe reads its exit by [`Captured::probe_exit`], and
//! every probe without an answer refuses with [`probe_unanswered`].

use avl_base::{Exit, Refusal, RefusalExt as _};
use serde_json::json;

use super::{Captured, base_name, output_tail};

#[cfg(test)]
mod tests;

/// What the exit of a probe says.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProbeExit {
    /// Exit 0: the probe answered. A probe whose answer is its output also needs that output, and its site refuses
    /// an empty or malformed one with [`probe_unanswered`].
    Answered,
    /// An exit from 1 to 125, or 128: the tool's own negative answer, such as "no such container", "no such process",
    /// or the fatal exit of `git`.
    Negative,
    /// 126 or 127: a shell's codes for a program that is not executable or not found. A version gate reads it as a
    /// missing tool. Every other probe reads it as no answer.
    CannotRun,
    /// 129 or more, which is 128 plus the signal that ended the program, or a negative code: no answer.
    Killed,
}

impl Captured {
    /// The exit of this capture, read as the exit of a probe.
    pub const fn probe_exit(&self) -> ProbeExit {
        match self.exit_code {
            0 => ProbeExit::Answered,
            1..=125 | 128 => ProbeExit::Negative,
            126 | 127 => ProbeExit::CannotRun,
            _ => ProbeExit::Killed,
        }
    }
}

/// Whether a refusal of a probe may quote what the probe printed.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProbeOutput {
    /// A host command: its output is the diagnosis.
    Quoted,
    /// A guest command: a guest process can echo the UI-test bridge token, so its output stays out of the refusal.
    Withheld,
}

/// The refusal for a probe that gave no answer: `probe_unanswered`, exit 75. It names the command and its exit code,
/// and it holds both streams unless `output` withholds them.
pub fn probe_unanswered(argv: &[String], captured: &Captured, output: ProbeOutput) -> Refusal {
    let command = argv
        .split_first()
        .map(|(program, arguments)| {
            std::iter::once(base_name(program))
                .chain(arguments.iter().map(String::as_str))
                .collect::<Vec<_>>()
                .join(" ")
        })
        .unwrap_or_default();
    let code = captured.exit_code;
    let (said, details) = match output {
        ProbeOutput::Quoted => {
            let stderr = output_tail(&captured.stderr);
            let said = if stderr.is_empty() {
                format!("printed {:?}", output_tail(&captured.stdout))
            } else {
                format!("printed {:?}, and on stderr {stderr:?}", output_tail(&captured.stdout))
            };
            let details = json!({ "command": argv, "exitCode": code, "stdout": captured.stdout, "stderr": captured.stderr });
            (said, details)
        }
        ProbeOutput::Withheld => ("its output is withheld".to_owned(), json!({ "command": argv, "exitCode": code })),
    };
    Refusal::new(
        "probe_unanswered",
        Exit::TEMP_FAIL,
        format!(
            "`{command}` exited with {code} and {said}, which is no answer, so the state it asks about is not known; \
             run the command again, and look at the host if this repeats"
        ),
    )
    .with_details(details)
}
