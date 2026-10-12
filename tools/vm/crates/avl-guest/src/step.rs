//! One external command a guest verb runs, and the seam every verb runs it through.
//!
//! The steps worth testing are properties of the *transcript*: that `tar` is asked for exactly this archive, that
//! a warm worker does not ask at all. A test swaps the [`Runner`] and reads what was asked; the host that runs
//! the tests has none of the guest's tools.

use std::fmt;
use std::io::{self, Write};
use std::process::{Command, ExitStatus, Stdio};
use std::thread;
use std::time::Duration;

#[cfg(test)]
pub(crate) mod tests_support;

/// What becomes of a step's own output.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum Output {
    /// Both streams go to this process's stderr. Stdout carries exactly one JSON document, so a child's progress
    /// line reaching it would make the reply unparseable to the only thing that reads it.
    #[default]
    Inherit,
    /// Stdout is kept for the caller to compare; stderr goes to this process's stderr.
    Capture,
    /// Both streams are dropped: the step's chatter is not worth a line anywhere.
    Silent,
}

/// One external command.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Step {
    pub argv: Vec<String>,
    /// What the child reads; `/dev/null` when absent, so a child that decided to prompt fails instead of hanging.
    pub stdin: Option<Vec<u8>>,
    /// Assignments beyond this process's environment, applied last.
    pub env: Vec<(String, String)>,
    pub output: Output,
}

impl Step {
    pub(crate) fn new<S: Into<String>>(argv: impl IntoIterator<Item = S>) -> Self {
        Self {
            argv: argv.into_iter().map(Into::into).collect(),
            ..Self::default()
        }
    }

    pub(crate) const fn silent(mut self) -> Self {
        self.output = Output::Silent;
        self
    }

    pub(crate) const fn capture(mut self) -> Self {
        self.output = Output::Capture;
        self
    }

    pub(crate) fn stdin(mut self, bytes: impl Into<Vec<u8>>) -> Self {
        self.stdin = Some(bytes.into());
        self
    }

    pub(crate) fn env(mut self, name: impl Into<String>, value: impl Into<String>) -> Self {
        self.env.push((name.into(), value.into()));
        self
    }

    /// The argv joined with spaces: how a transcript and a failure message name the step.
    pub(crate) fn command_line(&self) -> String {
        self.argv.join(" ")
    }
}

/// Why a step failed. The message names the command and how it ended, and quotes nothing the child wrote: it
/// travels to the host inside an envelope, so it must be this process's own text. The child's own account is on
/// stderr.
#[derive(Debug)]
pub(crate) enum StepError {
    /// The argv was empty.
    Empty,
    /// The command did not start.
    Start { line: String, source: io::Error },
    /// The wait for the command failed.
    Wait { line: String, source: io::Error },
    /// The command ran and ended with a failure status.
    Status { line: String, status: ExitStatus },
}

impl fmt::Display for StepError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Empty => formatter.write_str("an empty command was asked to run"),
            Self::Start { line, source } => write!(formatter, "{line} failed to start: {source}"),
            Self::Wait { line, source } => write!(formatter, "{line} failed: {source}"),
            Self::Status { line, status } => write!(formatter, "{line} failed: {status}"),
        }
    }
}

impl std::error::Error for StepError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Start { source, .. } | Self::Wait { source, .. } => Some(source),
            Self::Empty | Self::Status { .. } => None,
        }
    }
}

/// How a verb runs its steps. Answers the captured stdout (empty unless [`Output::Capture`]).
pub(crate) trait Runner {
    fn run(&mut self, step: &Step) -> Result<String, StepError>;

    /// The pause between two probes of something that is still starting. The runner's, so a test reaches a wait's
    /// timeout refusal without spending a minute in it.
    fn pause(&mut self, duration: Duration) {
        thread::sleep(duration);
    }
}

impl<F: FnMut(&Step) -> Result<String, StepError>> Runner for F {
    fn run(&mut self, step: &Step) -> Result<String, StepError> {
        self(step)
    }
}

/// Runs steps as real processes.
pub(crate) struct SystemRunner;

impl Runner for SystemRunner {
    fn run(&mut self, step: &Step) -> Result<String, StepError> {
        let Some((program, arguments)) = step.argv.split_first() else {
            return Err(StepError::Empty);
        };
        let mut command = Command::new(program);
        command.args(arguments).envs(step.env.iter().map(|(name, value)| (name, value)));
        command.stdin(if step.stdin.is_some() { Stdio::piped() } else { Stdio::null() });
        match step.output {
            Output::Inherit => command.stdout(Stdio::from(io::stderr())).stderr(Stdio::inherit()),
            Output::Capture => command.stdout(Stdio::piped()).stderr(Stdio::inherit()),
            Output::Silent => command.stdout(Stdio::null()).stderr(Stdio::null()),
        };
        let line = step.command_line();
        let mut child = match command.spawn() {
            Ok(child) => child,
            Err(source) => return Err(StepError::Start { line, source }),
        };
        // Written from a thread so a child that fills its stdout pipe before reading all of stdin cannot deadlock
        // with this process.
        let feeder = match (child.stdin.take(), step.stdin.clone()) {
            (Some(mut pipe), Some(bytes)) => Some(thread::spawn(move || pipe.write_all(&bytes))),
            _ => None,
        };
        let output = match child.wait_with_output() {
            Ok(output) => output,
            Err(source) => return Err(StepError::Wait { line, source }),
        };
        if let Some(feeder) = feeder {
            // A child that exits without reading its whole input breaks the pipe; its status says what happened.
            let _ = feeder.join();
        }
        if !output.status.success() {
            return Err(StepError::Status {
                line,
                status: output.status,
            });
        }
        Ok(String::from_utf8_lossy(&output.stdout).into_owned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_captured_step_answers_its_stdout_and_a_failure_names_the_command_only() {
        let answer = SystemRunner
            .run(&Step::new(["/bin/sh", "-c", "cat; echo tail"]).stdin("head\n").capture())
            .unwrap();
        assert_eq!(answer, "head\ntail\n");

        let failure = SystemRunner
            .run(&Step::new(["/bin/sh", "-c", "echo secret >&2; exit 3"]).silent())
            .unwrap_err()
            .to_string();
        assert!(failure.starts_with("/bin/sh -c echo secret >&2; exit 3 failed: "), "{failure}");
        assert!(failure.contains('3'), "{failure}");
        assert!(!failure.contains("secret\n"), "{failure}");
    }

    #[test]
    fn a_step_sees_its_own_assignments() {
        let answer = SystemRunner
            .run(
                &Step::new(["/bin/sh", "-c", "printf %s \"$AVL_STEP_PROBE\""])
                    .env("AVL_STEP_PROBE", "on")
                    .capture(),
            )
            .unwrap();
        assert_eq!(answer, "on");
    }
}
