//! The calls of Branch Deck (`bd`): `bd lease host`, which lends the host slots per run (`slots`). Each call runs in `/`
//! with stdin `/dev/null`, as `remote.sh` of BT runs them: bd must not take the directory of the agent for a repository,
//! and it must not wait for an input.

use std::path::Path;
use std::process::Command;
use std::time::Duration;

use crate::child::{self, Stop};

/// The deadline of `bd schema`.
const SCHEMA: Duration = Duration::from_secs(60);

fn bd() -> Command {
    let mut command = Command::new("bd");
    command.current_dir("/");
    command
}

/// `bd lease host <args> -C <repo>` within `deadline`; its stdout when it succeeded. `stop` kills it early.
pub(crate) fn host(repo: &Path, args: &[&str], deadline: Duration, stop: Stop<'_>) -> Result<String, String> {
    let output =
        child::run(bd().args(["lease", "host"]).args(args).arg("-C").arg(repo), deadline, stop).map_err(|error| error.to_string())?;
    if output.status.success() {
        Ok(output.stdout_text())
    } else {
        Err(format!(
            "bd lease host {} failed in {}: {} ({})",
            args.first().copied().unwrap_or_default(),
            repo.display(),
            output.diagnostic(),
            output.status
        ))
    }
}

/// The output of `bd schema`, `None` when it fails: the names of the outputs say what this bd can do.
pub(crate) fn schema() -> Option<String> {
    let output = child::run(bd().arg("schema"), SCHEMA, &child::never).ok()?;
    output.status.success().then(|| output.stdout_text())
}
