//! The thin pack of a run or of a base. git builds it into a temporary file first, so a slow link never stalls git and a
//! stuck git never holds a connection. Then the client sends the file to the agent on a connection of its own.
//!
//! A host that has none of the candidate commits would need the whole history, which is no thin pack. The client
//! refuses that with `behind` and starts no pack.

use std::fs::{self, File};
use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use yonder_link::proto::{Reply, Request};

use crate::git::{commit_count, git_refusal, pack_call};
use crate::host::Host;
use crate::process::Stopped;
use crate::{Client, Refusal, code, refused, size_text};

/// How often the upload says how far it is.
const PROGRESS_EVERY: Duration = Duration::from_secs(10);

/// The count of the packs of this process, for the names of their files.
static PACKS: AtomicU64 = AtomicU64::new(0);

/// What a pack goes to.
pub(crate) struct PackTarget<'a> {
    pub(crate) checkout: &'a Path,
    pub(crate) repo: &'a str,
    pub(crate) commit: &'a str,
    pub(crate) reference: &'a str,
}

/// A pack file that git built, ready for its upload.
pub(crate) struct BuiltPack {
    file: PackFile,
    size: u64,
}

impl Client<'_> {
    /// Builds the thin pack of the objects of the commit that the host lacks. `steps` takes the step lines.
    pub(crate) fn build_pack(
        &self,
        alias: &str,
        target: &PackTarget<'_>,
        present: &[String],
        steps: &dyn Fn(&str),
    ) -> Result<BuiltPack, Refusal> {
        let stop = || self.interrupted();
        let count = commit_count(self.processes, target.checkout, target.commit, present, &stop);
        if present.is_empty() {
            let history = count.map_or_else(|| "the whole history".to_owned(), |all| format!("all {all} commits of the history"));
            return Err(refused(
                code::BEHIND,
                format!(
                    "{alias} has none of the commits near HEAD, so the pack would hold {history}. `bt --host {alias} --prepare` moves the master of the host to the local master"
                ),
            ));
        }
        let commits = count.map_or_else(|| "the".to_owned(), |count| count.to_string());
        steps(&format!("> {alias}: pack {commits} commits"));

        let file = PackFile::new();
        let built = self
            .processes
            .run_to_file(&pack_call(target.checkout, target.commit, present), file.path(), &stop);
        if built.stopped == Some(Stopped::Asked) {
            return Err(interrupted());
        }
        if !built.ok() {
            return Err(git_refusal("git pack-objects", &built));
        }
        let size = fs::metadata(file.path())
            .map(|meta| meta.len())
            .map_err(|error| refused(code::GIT_FAILED, format!("cannot read the pack {}: {error}", file.path().display())))?;
        steps(&format!("> {alias}: pack of {} ready", size_text(size)));
        if self.interrupted() {
            return Err(interrupted());
        }
        Ok(BuiltPack { file, size })
    }

    /// Sends a built pack on a connection of its own. The agent points the reference at the commit there. `steps`
    /// takes the progress lines. Ctrl+C stops the upload at the next chunk.
    pub(crate) fn upload(&self, host: &mut Host, target: &PackTarget<'_>, pack: &BuiltPack, steps: &dyn Fn(&str)) -> Result<(), Refusal> {
        let alias = host.alias().to_owned();
        let size = pack.size;
        let stop = || self.interrupted();
        let (mut channel, _) = host.connect()?;
        let request = Request::Pack {
            repo: target.repo.to_owned(),
            commit: target.commit.to_owned(),
            reference: target.reference.to_owned(),
        };
        channel.send(&request).map_err(|error| host.link_refusal(&error))?;
        let mut reader = Upload {
            file: File::open(pack.file.path()).map_err(|error| refused(code::GIT_FAILED, format!("cannot open the pack: {error}")))?,
            stop: &stop,
        };
        let mut last_said = Instant::now();
        let sent = channel.send_stream(&mut reader, &mut |bytes| {
            if last_said.elapsed() >= PROGRESS_EVERY {
                last_said = Instant::now();
                steps(&format!("> {alias}: sent {} of {}", size_text(bytes), size_text(size)));
            }
        });
        if let Err(error) = sent {
            if self.interrupted() {
                return Err(refused(code::INTERRUPTED, "Ctrl+C stopped the upload of the pack"));
            }
            // An agent that refuses the pack replies and closes; its reply says more than the failed write.
            return Err(match host.recv(&mut channel, self.timing.connect) {
                Ok(Reply::Refused { code, message }) => host.agent_refusal(code, &message),
                _ => host.link_refusal(&error),
            });
        }
        match host.recv(&mut channel, self.timing.read)? {
            Reply::Packed => {
                steps(&format!("> {alias}: sent {}", size_text(size)));
                Ok(())
            }
            Reply::Refused { code, message } => Err(host.agent_refusal(code, &message)),
            reply => Err(host.unexpected(&reply)),
        }
    }
}

fn interrupted() -> Refusal {
    refused(code::INTERRUPTED, "Ctrl+C came before the run started; nothing runs on the host")
}

/// The pack in the temporary directory, removed on every way out.
struct PackFile(PathBuf);

impl PackFile {
    fn new() -> Self {
        let name = format!("yonder-{}-{}.pack", std::process::id(), PACKS.fetch_add(1, Ordering::Relaxed));
        Self(std::env::temp_dir().join(name))
    }

    fn path(&self) -> &Path {
        &self.0
    }
}

impl Drop for PackFile {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0);
    }
}

/// The pack file as the channel reads it. Ctrl+C ends the read with an error, so the upload stops at the next chunk.
struct Upload<'a> {
    file: File,
    stop: &'a dyn Fn() -> bool,
}

impl Read for Upload<'_> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        if (self.stop)() {
            return Err(io::Error::other("Ctrl+C"));
        }
        self.file.read(buffer)
    }
}

#[cfg(test)]
mod tests;
