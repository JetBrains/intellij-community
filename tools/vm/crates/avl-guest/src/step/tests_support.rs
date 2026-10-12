//! A recording [`Runner`] for the verbs' transcript tests.

use std::collections::{HashMap, HashSet};
use std::os::unix::process::ExitStatusExt;
use std::process::ExitStatus;
use std::time::Duration;

use super::{Runner, Step, StepError};

/// The failure of `step` that exited with `code`: "<command line> failed: exit status: <code>".
pub(crate) fn exited(step: &Step, code: i32) -> StepError {
    StepError::Status {
        line: step.command_line(),
        // A wait status is the exit code shifted by eight bits.
        status: ExitStatus::from_raw(code << 8),
    }
}

/// Records every step and answers by the step's joined argv ([`Step::command_line`]).
///
/// `answers` is what a captured step prints, `fails` the command lines that exit non-zero, `fails_first` the ones
/// that fail that many more times and then succeed (a first boot's X server), and `attempts` how many times each
/// command line ran. `paused` is every pause a wait took.
#[derive(Default)]
pub(crate) struct Transcript {
    pub steps: Vec<Step>,
    pub answers: HashMap<String, String>,
    pub fails: HashSet<String>,
    pub fails_first: HashMap<String, usize>,
    pub attempts: HashMap<String, usize>,
    pub paused: Vec<Duration>,
}

impl Transcript {
    /// The command lines run so far, in order.
    pub(crate) fn lines(&self) -> Vec<String> {
        self.steps.iter().map(Step::command_line).collect()
    }

    /// The position of the first step whose command line starts with `prefix`.
    pub(crate) fn index_of(&self, prefix: &str) -> Option<usize> {
        self.lines().iter().position(|line| line.starts_with(prefix))
    }

    pub(crate) fn ran(&self, prefix: &str) -> bool {
        self.index_of(prefix).is_some()
    }

    /// The first step whose command line starts with `prefix`.
    pub(crate) fn step(&self, prefix: &str) -> &Step {
        let index = self
            .index_of(prefix)
            .unwrap_or_else(|| panic!("no step starts with {prefix:?}: {:?}", self.lines()));
        &self.steps[index]
    }
}

impl Runner for Transcript {
    fn run(&mut self, step: &Step) -> Result<String, StepError> {
        let line = step.command_line();
        self.steps.push(step.clone());
        *self.attempts.entry(line.clone()).or_default() += 1;
        if let Some(remaining) = self.fails_first.get_mut(&line).filter(|left| **left > 0) {
            *remaining -= 1;
            return Err(exited(step, 1));
        }
        if self.fails.contains(&line) {
            return Err(exited(step, 1));
        }
        Ok(self.answers.get(&line).cloned().unwrap_or_default())
    }

    fn pause(&mut self, duration: Duration) {
        self.paused.push(duration);
    }
}

#[test]
fn a_transcript_records_answers_and_fails_by_command_line() {
    let mut transcript = Transcript::default();
    transcript.answers.insert("uname -m".to_owned(), "arm64\n".to_owned());
    transcript.fails.insert("false".to_owned());
    transcript.fails_first.insert("xdpyinfo".to_owned(), 1);
    assert_eq!(transcript.run(&Step::new(["uname", "-m"]).capture()).unwrap(), "arm64\n");
    assert_eq!(
        transcript.run(&Step::new(["false"])).unwrap_err().to_string(),
        "false failed: exit status: 1"
    );
    transcript.run(&Step::new(["false"])).unwrap_err();
    transcript.run(&Step::new(["xdpyinfo"])).unwrap_err();
    transcript.run(&Step::new(["xdpyinfo"])).unwrap();
    assert_eq!(transcript.lines(), ["uname -m", "false", "false", "xdpyinfo", "xdpyinfo"]);
    assert_eq!(transcript.attempts["false"], 2);
    assert_eq!(transcript.index_of("fal"), Some(1));
    transcript.pause(Duration::from_secs(1));
    assert_eq!(transcript.paused, [Duration::from_secs(1)]);
}
