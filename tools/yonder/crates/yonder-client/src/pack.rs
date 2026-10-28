//! The thin pack of a run or of a base. `bd host pack` builds it into a private file of bd first, so a slow link never
//! stalls git and a stuck git never holds a connection past its quiet limit. The client opens the connection of the pack before the
//! build, and sends an empty chunk every `HEARTBEAT_SECONDS` of the protocol while git works: the supervisor of the run
//! then knows that its client is alive. Then the client sends the file on that connection.
//!
//! A host that has none of the candidate commits would need the whole history, which is no thin pack. The client
//! refuses that with `behind` before it starts the run, and starts no pack.

use std::fs::{self, File};
use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, PoisonError};
use std::time::{Duration, Instant};

use yonder_link::proto::{Reply, Request};
use yonder_link::{Channel, LinkError};

use crate::host::Host;
use crate::{Client, Refusal, code, refused, size_text};

/// How often the upload says how far it is.
const PROGRESS_EVERY: Duration = Duration::from_secs(10);

/// What a pack goes to.
pub(crate) struct PackTarget<'a> {
    pub(crate) checkout: &'a Path,
    pub(crate) repo: &'a str,
    pub(crate) commit: &'a str,
    pub(crate) reference: &'a str,
}

/// The pack of a target against the commits that the host has: what [`plan_pack`] answers.
pub(crate) struct PackPlan<'a> {
    present: &'a [String],
}

/// How long the two halves of a sent pack took.
pub(crate) struct PackSteps {
    pub(crate) build: Duration,
    pub(crate) upload: Duration,
}

impl Client<'_> {
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
        steps(&format!("> {alias}: pack the commits that {alias} lacks"));
        let building = Instant::now();
        let (mut channel, _) = host.connect()?;
        let request = Request::Pack {
            repo: target.repo.to_owned(),
            commit: target.commit.to_owned(),
            reference: target.reference.to_owned(),
        };
        channel.send(&request).map_err(|error| host.link_refusal(&error))?;
        let (built, mut channel) = self.build_beside(channel, target, plan.present);
        let built = built.map_err(|failed| match failed {
            Built::Interrupted => interrupted(),
            Built::Bd(refusal) => refusal,
            Built::Link(error) => host.link_refusal(&error),
        });
        let file = match built {
            Ok(file) => file,
            Err(refusal) => {
                channel.close();
                return Err(refusal);
            }
        };
        let (build, size) = (building.elapsed(), file.bytes);
        steps(&format!("> {alias}: pack of {} commits, {} ready", file.commits, size_text(size)));
        let uploading = Instant::now();
        self.stream_pack(host, &mut channel, &file, size, steps)?;
        steps(&format!("> {alias}: sent {}", size_text(size)));
        Ok(PackSteps {
            build,
            upload: uploading.elapsed(),
        })
    }

    /// Builds the pack with bd, and sends an empty chunk on `channel` at each keepalive time meanwhile. The watch of
    /// the child asks the stop often, so the stop sends the chunks. A failed chunk stops the build.
    fn build_beside(&self, channel: Channel, target: &PackTarget<'_>, present: &[String]) -> (Result<PackFile, Built>, Channel) {
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
        let built = self.build_pack(target.checkout, target.commit, present, &stop).map(PackFile::from);
        let kept = kept.into_inner().unwrap_or_else(PoisonError::into_inner);
        // A pack that is not sent goes with its guard here.
        let built = match (kept.failed, built) {
            (Some(error), _) => Err(Built::Link(error)),
            (None, Err(refusal)) if refusal.code == code::INTERRUPTED => Err(Built::Interrupted),
            (None, Err(refusal)) => Err(Built::Bd(refusal)),
            (None, Ok(_)) if self.interrupted() => Err(Built::Interrupted),
            (None, Ok(pack)) => Ok(pack),
        };
        (built, kept.channel)
    }

    /// Sends the built pack on its connection, and waits for `Packed`. `steps` takes the progress lines.
    fn stream_pack(&self, host: &Host, channel: &mut Channel, file: &PackFile, size: u64, steps: &dyn Fn(&str)) -> Result<(), Refusal> {
        let alias = host.alias().to_owned();
        let stop = || self.interrupted();
        let mut reader = Upload {
            file: File::open(&file.path).map_err(|error| refused(code::BD_FAILED, format!("cannot open the pack: {error}")))?,
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

/// The pack of the objects of the commit that the host lacks. Refuses with `behind` when the host has none of the
/// candidates.
pub(crate) fn plan_pack<'a>(alias: &str, present: &'a [String]) -> Result<PackPlan<'a>, Refusal> {
    if present.is_empty() {
        return Err(refused(
            code::BEHIND,
            format!(
                "{alias} has none of the commits near HEAD, so the pack would hold the whole history. `bt --host {alias} --prepare` moves the master of the host to the local master"
            ),
        ));
    }
    Ok(PackPlan { present })
}

/// The connection of a pack while bd builds the pack.
struct Keepalive {
    channel: Channel,
    sent: Instant,
    failed: Option<LinkError>,
}

/// Why the build of a pack gave no file.
enum Built {
    Interrupted,
    Bd(Refusal),
    /// An empty chunk failed: the connection is gone.
    Link(LinkError),
}

fn interrupted() -> Refusal {
    refused(code::INTERRUPTED, "Ctrl+C stopped the build of the pack")
}

/// The pack file of bd, removed on every way out.
struct PackFile {
    path: PathBuf,
    bytes: u64,
    commits: u64,
}

impl From<crate::bd::Pack> for PackFile {
    fn from(pack: crate::bd::Pack) -> Self {
        Self {
            path: pack.path,
            bytes: pack.bytes,
            commits: pack.commits,
        }
    }
}

impl Drop for PackFile {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
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
