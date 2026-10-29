//! Helpers the verbs' tests share: running the agent in memory and reading what it answered.

use std::ffi::OsStr;
use std::path::{Path, PathBuf};

use avl_wire::supervisor::AgentExit;
use avl_wire::verb::AgentVerb;
use serde_json::Value;

use crate::reply::AgentRefusal;

/// What one in-memory run of the agent answered.
pub(crate) struct Answered {
    pub exit: u8,
    pub stdout: String,
    pub stderr: String,
}

impl Answered {
    /// The one JSON document on stdout.
    pub(crate) fn document(&self) -> Value {
        serde_json::from_str(&self.stdout).unwrap_or_else(|error| panic!("stdout is not one document ({error}): {}", self.stdout))
    }

    /// The failure envelope on stderr.
    pub(crate) fn failure(&self) -> Value {
        let envelope: Value =
            serde_json::from_str(&self.stderr).unwrap_or_else(|error| panic!("stderr is not one envelope ({error}): {}", self.stderr));
        assert_eq!(envelope["ok"], false, "{envelope}");
        assert_eq!(envelope["schemaVersion"], 1, "{envelope}");
        envelope
    }

    /// The failure envelope's code.
    pub(crate) fn code(&self) -> String {
        self.failure()["error"]["code"].as_str().unwrap_or_default().to_owned()
    }
}

/// Runs `vm-guest-agent <args>` in this process, with `stdin` as its standard input.
pub(crate) fn run_agent<S: AsRef<OsStr>>(args: &[S], stdin: &[u8]) -> Answered {
    let mut input = stdin;
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit = crate::run(args.iter().map(|arg| arg.as_ref().to_owned()), &mut input, &mut stdout, &mut stderr);
    Answered {
        exit,
        stdout: String::from_utf8_lossy(&stdout).into_owned(),
        stderr: String::from_utf8_lossy(&stderr).into_owned(),
    }
}

/// Asserts that the agent refuses `args` as a usage error: the exit 64 and the code `usage`. `case` names the case in a
/// failure.
pub(crate) fn assert_usage_refused<S: AsRef<OsStr>>(args: &[S], case: &str) {
    let answered = run_agent(args, b"");
    assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{case}");
}

/// Asserts that the agent refuses `verb` with the arguments of each case as a usage error. A case is a name and the
/// arguments after the verb.
pub(crate) fn assert_verb_usage_refused(verb: AgentVerb, cases: &[(&str, &[&str])]) {
    for (name, argv) in cases {
        let args: Vec<&str> = std::iter::once(verb.as_str()).chain(argv.iter().copied()).collect();
        assert_usage_refused(&args, name);
    }
}

/// The fixture of a validator: a guest that every check passes on. A damage case changes one thing of it.
pub(crate) trait ValidatorFixture: Sized {
    /// A guest that every check passes on.
    fn healthy() -> Self;

    /// The refusal of the validator. `case` names the case in a failure, when the validator passes.
    fn refusal(&mut self, case: &str) -> AgentRefusal;
}

/// A damage case: a name, the change of a healthy fixture `F`, and what the refusal must show.
pub(crate) type DamageCase<'a, F, E> = (&'a str, fn(&mut F), E);

/// The refusal of a healthy fixture after the damage of `damage`.
fn damaged_refusal<F: ValidatorFixture>(name: &str, damage: fn(&mut F)) -> AgentRefusal {
    let mut fixture = F::healthy();
    damage(&mut fixture);
    fixture.refusal(name)
}

/// Asserts that the validator refuses each damage case with its code. Every refusal has the one exit of a refusal,
/// because the callers treat every nonzero status alike.
pub(crate) fn assert_damage_codes<F: ValidatorFixture>(cases: &[DamageCase<'_, F, &str>]) {
    for &(name, damage, code) in cases {
        let refusal = damaged_refusal(name, damage);
        assert_eq!(refusal.code, code, "{name}: {}", refusal.message);
        assert_eq!(refusal.exit, AgentExit::Refused, "{name}");
    }
}

/// Asserts that the message of the refusal of each damage case contains each of its fragments.
pub(crate) fn assert_damage_mentions<F: ValidatorFixture>(cases: &[DamageCase<'_, F, &[&str]>]) {
    for &(name, damage, mentions) in cases {
        let refusal = damaged_refusal(name, damage);
        for fragment in mentions {
            assert!(refusal.message.contains(fragment), "{name}: {fragment}: {}", refusal.message);
        }
    }
}

/// A launcher that runs this test binary as the agent, the way `start` runs the installed one. It runs the test
/// `supervisor::tests::agent_process` with the argv of the agent in the environment.
pub(crate) fn agent_launcher(directory: &Path) -> PathBuf {
    let test_binary = std::env::current_exe().unwrap();
    avl_testkit::fake_executable(
        directory,
        "vm-guest-agent",
        &format!(
            "i=0\nfor argument in \"$@\"; do export \"AVL_GUEST_AGENT_ARG_$i=$argument\"; i=$((i+1)); done\n\
             export AVL_GUEST_AGENT_ARGC=$i\n\
             exec '{}' --exact supervisor::tests::agent_process --nocapture --test-threads=1 -q\n",
            test_binary.display()
        ),
    )
    .unwrap()
}
