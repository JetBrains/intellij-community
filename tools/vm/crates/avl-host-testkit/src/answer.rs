//! How a fake guest answers: the [`Captured`] builders, and the answers a test hands a [`crate::FakeChannel`].

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use avl_base::Refusal;
use avl_base::sync::lock;
use avl_host_sys::{Captured, SpawnOptions};

#[cfg(test)]
mod tests;

/// How one guest command is answered: from its argv, and from its options when the command reads stdin.
pub type Answer = Arc<dyn Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync>;

/// An exit 0 that printed `stdout`.
pub fn said(stdout: &str) -> Captured {
    Captured {
        stdout: stdout.to_owned(),
        ..Captured::default()
    }
}

/// A failure with an exit code and a stderr.
pub fn failed(exit_code: i32, stderr: &str) -> Captured {
    Captured {
        exit_code,
        stderr: stderr.to_owned(),
        ..Captured::default()
    }
}

/// Whether an argv contains a word, as one whole element.
pub fn has(argv: &[String], word: &str) -> bool {
    argv.iter().any(|element| element == word)
}

/// Dispatches on argv fragments, in the order given, falling back to "exited 0, said nothing". A fragment matches
/// anywhere in the argv joined by spaces.
pub fn answer_guest(
    rules: Vec<(&'static str, Captured)>,
) -> impl Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync + 'static {
    move |argv, _| {
        let joined = argv.join(" ");
        Ok(rules
            .iter()
            .find(|(fragment, _)| joined.contains(fragment))
            .map(|(_, captured)| captured.clone())
            .unwrap_or_default())
    }
}

/// An answer printing `stdout`, exit 0.
pub fn answer_text(stdout: impl Into<String>) -> Answer {
    let stdout = stdout.into();
    Arc::new(move |_, _| Ok(said(&stdout)))
}

/// An answer printing nothing, with `code`.
pub fn answer_exit(code: i32) -> Answer {
    Arc::new(move |_, _| Ok(failed(code, "")))
}

/// An answer from a closure.
pub fn handler(answer: impl Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync + 'static) -> Answer {
    Arc::new(answer)
}

/// Answers by verb: the agent's own subcommand for the agent binary, the basename for everything else (`df`, `ls`,
/// `cat`, `test`, ...). A handler gets the argv with its sudo, session and `env` prefixes stripped. An unrouted verb
/// exits 0 and says nothing.
pub struct Verbs {
    agent: String,
    handlers: Mutex<HashMap<String, Answer>>,
}

impl Verbs {
    /// A router for the guest agent installed at `agent`.
    pub fn new(agent: &str) -> Arc<Self> {
        Arc::new(Self {
            agent: agent.to_owned(),
            handlers: Mutex::default(),
        })
    }

    /// Installs the answer to one verb, replacing the one it had.
    pub fn on(&self, verb: &str, answer: Answer) {
        lock(&self.handlers).insert(verb.to_owned(), answer);
    }

    /// Answers one guest command by its verb.
    pub fn route(&self, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        let effective = effective_argv(argv);
        let Some(program) = effective.first() else {
            return Ok(Captured::default());
        };
        let verb = match effective.get(1) {
            Some(verb) if *program == self.agent => verb.as_str(),
            _ => base_name(program),
        };
        let handler = lock(&self.handlers).get(verb).cloned();
        handler.map_or_else(|| Ok(Captured::default()), |handler| handler(effective, options))
    }
}

/// Strips the `sudo -H -u <user>`, `launchctl asuser <uid>`, `setsid --wait` and `env NAME=VALUE` prefixes
/// production code wraps guest commands in, so a router routes on what actually runs.
pub fn effective_argv(argv: &[String]) -> &[String] {
    let mut index = 0;
    while let Some(token) = argv.get(index) {
        index += match token.as_str() {
            "/usr/bin/sudo" | "-H" | "/usr/bin/env" => 1,
            "-u" | "/usr/bin/setsid" => 2,
            "/bin/launchctl" => 3,
            assignment if assignment.contains('=') && !assignment.starts_with('/') && !assignment.starts_with('-') => 1,
            _ => return &argv[index..],
        };
    }
    &[]
}

/// The last component of a guest path.
pub fn base_name(path: &str) -> &str {
    path.rsplit('/').next().unwrap_or(path)
}
