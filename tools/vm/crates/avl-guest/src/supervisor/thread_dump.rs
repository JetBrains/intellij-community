//! The thread dump that `cancel --thread-dump <jcmd>` takes before the TERM: `jcmd <pid> Thread.print` into a file.
//!
//! The dump of an IDE run goes into the log directory of its launch, `log/<launchName>` of the context, where the
//! other IDE logs are. The dump of another run goes into its run directory.

use std::fs::{self, OpenOptions};
use std::io;
use std::os::unix::fs::{DirBuilderExt, OpenOptionsExt};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

use avl_wire::ide::{self, LOG_DIR};
use jiff::Timestamp;

use super::POLL_INTERVAL;
use super::state::RunPaths;

#[cfg(test)]
mod tests;

/// How long `jcmd` has to write the dump. A JVM that does not answer `jcmd` in this time is the JVM the cancel is for.
pub(crate) const TIMEOUT: Duration = Duration::from_secs(30);

/// The directory of the dump of `run_id` in the run slot `root`.
pub(crate) fn directory(root: &Path, run_id: &str) -> PathBuf {
    match ide::launch_name_of(run_id) {
        Some(launch_name) => root.join(LOG_DIR).join(launch_name),
        None => RunPaths::new(root, run_id).directory,
    }
}

/// Runs `jcmd <pid> Thread.print` with both of its streams into `threadDump-before-kill-<millis>.txt` of `directory`,
/// and answers the file. `jcmd` gets [`TIMEOUT`], and an exit other than 0 is an error. The file stays in both cases,
/// because a part of a dump is still evidence.
pub(crate) fn capture(jcmd: &Path, pid: i32, directory: &Path, at: Timestamp) -> io::Result<PathBuf> {
    fs::DirBuilder::new().recursive(true).mode(0o700).create(directory)?;
    let path = directory.join(format!("threadDump-before-kill-{}.txt", at.as_millisecond()));
    let file = OpenOptions::new().write(true).create_new(true).mode(0o600).open(&path)?;
    let mut child = Command::new(jcmd)
        .args([pid.to_string().as_str(), "Thread.print"])
        .stdin(Stdio::null())
        .stdout(file.try_clone()?)
        .stderr(file)
        .spawn()?;
    let until = Instant::now() + TIMEOUT;
    loop {
        if let Some(status) = child.try_wait()? {
            if status.success() {
                return Ok(path);
            }
            return Err(io::Error::other(format!("{} exited with {status}", jcmd.display())));
        }
        if Instant::now() >= until {
            let _ = child.kill();
            let _ = child.wait();
            return Err(io::Error::new(
                io::ErrorKind::TimedOut,
                format!("{} did not finish within {} s", jcmd.display(), TIMEOUT.as_secs()),
            ));
        }
        thread::sleep(POLL_INTERVAL);
    }
}
