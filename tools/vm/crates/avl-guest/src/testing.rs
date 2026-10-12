//! Helpers the verbs' tests share: running the agent in memory and reading what it answered.

use std::process::{Child, Command};

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

/// The pid of a process that ran and was reaped, so no process holds it until the kernel reuses it.
pub(crate) fn finished_pid() -> u32 {
    let mut child = Command::new("/bin/sh").args(["-c", "exit 0"]).spawn().unwrap();
    child.wait().unwrap();
    child.id()
}

/// A process that runs until the drop kills it.
pub(crate) struct RunningProcess(Child);

impl RunningProcess {
    pub(crate) fn start() -> Self {
        Self(Command::new("/bin/sleep").arg("600").spawn().unwrap())
    }

    pub(crate) fn pid(&self) -> u32 {
        self.0.id()
    }
}

impl Drop for RunningProcess {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}
