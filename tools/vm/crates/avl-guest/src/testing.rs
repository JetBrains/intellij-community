//! Helpers the verbs' tests share: running the agent in memory and reading what it answered.

use std::path::{Path, PathBuf};

use serde_json::Value;

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
pub(crate) fn run_agent<S: AsRef<std::ffi::OsStr>>(args: &[S], stdin: &[u8]) -> Answered {
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
