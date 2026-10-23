//! The thin pack of a run or of a base. git builds it into a temporary file first, so a slow link never stalls git and a
//! stuck git never holds a connection past its quiet limit. The client opens the connection of the pack before the
//! build, and sends an empty chunk every `HEARTBEAT_SECONDS` of the protocol while git works: the supervisor of the run
//! then knows that its client is alive. Then the client sends the file on that connection.
//!
//! A host that has none of the candidate commits would need the whole history, which is no thin pack. The client
//! refuses that with `behind` before it starts the run, and starts no pack.

use std::fs::{self, File};
use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, PoisonError};
use std::time::{Duration, Instant};

use yonder_link::proto::{Reply, Request};
use yonder_link::{Channel, LinkError};

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

/// The pack of a target against the commits that the host has: what [`Client::plan_pack`] answers.
pub(crate) struct PackPlan<'a> {
    present: &'a [String],
    /// The count of the commits that the pack holds, when git can count them.
    commits: Option<u64>,
}

/// How long the two halves of a sent pack took.
pub(crate) struct PackSteps {
    pub(crate) build: Duration,
    pub(crate) upload: Duration,
}

impl Client<'_> {
    /// The pack of the objects of the commit that the host lacks. Refuses with `behind` when the host has none of the
    /// candidates.
    pub(crate) fn plan_pack<'a>(&self, alias: &str, target: &PackTarget<'_>, present: &'a [String]) -> Result<PackPlan<'a>, Refusal> {
        let commits = commit_count(self.processes, target.checkout, target.commit, present, &|| self.interrupted());
        if present.is_empty() {
            let history = commits.map_or_else(|| "the whole history".to_owned(), |all| format!("all {all} commits of the history"));
            return Err(refused(
                code::BEHIND,
                format!(
                    "{alias} has none of the commits near HEAD, so the pack would hold {history}. `bt --host {alias} --prepare` moves the master of the host to the local master"
                ),
            ));
        }
        Ok(PackPlan { present, commits })
    }

    /// Opens the connection of the pack, builds the pack while an empty chunk keeps the connection alive, and sends it.
    /// The agent points the reference at the commit. `steps` takes the step lines. Ctrl+C stops the build, and the
    /// upload at the next chunk.
    pub(crate) fn send_pack(
        &self,
        host: &mut Host,
        target: &PackTarget<'_>,
        plan: &PackPlan<'_>,
        steps: &dyn Fn(&str),
    ) -> Result<PackSteps, Refusal> {
        let alias = host.alias().to_owned();
        let commits = plan.commits.map_or_else(|| "the".to_owned(), |count| count.to_string());
        steps(&format!("> {alias}: pack {commits} commits"));
        let building = Instant::now();
        let (mut channel, _) = host.connect()?;
        let request = Request::Pack {
            repo: target.repo.to_owned(),
            commit: target.commit.to_owned(),
            reference: target.reference.to_owned(),
        };
        channel.send(&request).map_err(|error| host.link_refusal(&error))?;
        let file = PackFile::new();
        let (built, mut channel) = self.build_beside(channel, target, plan.present, &file);
        let built = match built {
            Ok(()) => fs::metadata(file.path())
                .map(|meta| meta.len())
                .map_err(|error| refused(code::GIT_FAILED, format!("cannot read the pack {}: {error}", file.path().display()))),
            Err(Built::Interrupted) => Err(interrupted()),
            Err(Built::Git(refusal)) => Err(refusal),
            Err(Built::Link(error)) => Err(host.link_refusal(&error)),
        };
        let size = match built {
            Ok(size) => size,
            Err(refusal) => {
                channel.close();
                return Err(refusal);
            }
        };
        let build = building.elapsed();
        steps(&format!("> {alias}: pack of {} ready", size_text(size)));
        let uploading = Instant::now();
        self.stream_pack(host, &mut channel, &file, size, steps)?;
        steps(&format!("> {alias}: sent {}", size_text(size)));
        Ok(PackSteps {
            build,
            upload: uploading.elapsed(),
        })
    }

    /// Builds the pack into `file`, and sends an empty chunk on `channel` at each keepalive time meanwhile. The watch
    /// of the child asks the stop often, so the stop sends the chunks. A failed chunk stops the build.
    fn build_beside(&self, channel: Channel, target: &PackTarget<'_>, present: &[String], file: &PackFile) -> (Result<(), Built>, Channel) {
        let kept = Mutex::new(Keepalive {
            channel,
            sent: Instant::now(),
            failed: None,
        });
        let stop = || {
            if self.interrupted() {
                return true;
            }
            let mut kept = kept.lock().unwrap_or_else(PoisonError::into_inner);
            if kept.sent.elapsed() >= self.timing.keepalive && kept.failed.is_none() {
                match kept.channel.send_keepalive() {
                    Ok(()) => kept.sent = Instant::now(),
                    Err(error) => kept.failed = Some(error),
                }
            }
            kept.failed.is_some()
        };
        let ran = self
            .processes
            .run_to_file(&pack_call(target.checkout, target.commit, present), file.path(), &stop);
        let kept = kept.into_inner().unwrap_or_else(PoisonError::into_inner);
        let built = match (kept.failed, ran.stopped) {
            (Some(error), _) => Err(Built::Link(error)),
            (None, Some(Stopped::Asked)) => Err(Built::Interrupted),
            (None, _) if !ran.ok() => Err(Built::Git(git_refusal("git pack-objects", &ran))),
            (None, _) if self.interrupted() => Err(Built::Interrupted),
            (None, _) => Ok(()),
        };
        (built, kept.channel)
    }

    /// Sends the built pack on its connection, and waits for `Packed`. `steps` takes the progress lines.
    fn stream_pack(&self, host: &Host, channel: &mut Channel, file: &PackFile, size: u64, steps: &dyn Fn(&str)) -> Result<(), Refusal> {
        let alias = host.alias().to_owned();
        let stop = || self.interrupted();
        let mut reader = Upload {
            file: File::open(file.path()).map_err(|error| refused(code::GIT_FAILED, format!("cannot open the pack: {error}")))?,
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
            return Err(match host.recv(channel, self.timing.connect) {
                Ok(Reply::Refused { code, message }) => host.agent_refusal(code, &message),
                _ => host.link_refusal(&error),
            });
        }
        match host.recv(channel, self.timing.read)? {
            Reply::Packed => Ok(()),
            Reply::Refused { code, message } => Err(host.agent_refusal(code, &message)),
            reply => Err(host.unexpected(&reply)),
        }
    }
}

/// The connection of a pack while git builds the pack.
struct Keepalive {
    channel: Channel,
    sent: Instant,
    failed: Option<LinkError>,
}

/// Why the build of a pack gave no file.
enum Built {
    Interrupted,
    Git(Refusal),
    /// An empty chunk failed: the connection is gone.
    Link(LinkError),
}

fn interrupted() -> Refusal {
    refused(code::INTERRUPTED, "Ctrl+C stopped the build of the pack")
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
