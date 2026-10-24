//! The staged files of a run: `Fetch` copies them into the local checkout, and `Forget` removes the run on the agent.
//!
//! Each file goes to `<name>.yonder-part` first, and only a file of the size that the agent named takes its name. So a
//! cut connection leaves no file that looks whole. A fetch that did not copy every file is a failure, and the run then
//! keeps its stage on the agent for another `yonder fetch`.

use std::fs;
use std::io::{self, BufWriter, Write as _};
use std::path::{Path, PathBuf};

use yonder_link::proto::{Reply, Request};
use yonder_link::{Channel, LinkError};

use crate::host::Host;
use crate::run::done;
use crate::{Client, Refusal, code, refused};

/// The suffix of a file that is not complete yet.
const PART_SUFFIX: &str = ".yonder-part";

impl Client<'_> {
    /// Copies the staged files of a run into `checkout`. With `fetch`, each path must be below one of those
    /// directories; without, any plain relative path is accepted. Answers the copied paths, relative to the checkout
    /// with `/` separators.
    pub fn fetch(&self, alias: &str, run: &str, checkout: &Path, fetch: &[String]) -> Result<Vec<String>, Refusal> {
        let mut host = self.host(alias)?;
        self.fetch_from(&mut host, run, checkout, fetch)
    }

    /// Removes a run and its stage on the agent.
    pub fn forget(&self, alias: &str, run: &str) -> Result<String, Refusal> {
        let mut host = self.host(alias)?;
        self.forget_on(&mut host, run)
    }

    pub(crate) fn forget_on(&self, host: &mut Host, run: &str) -> Result<String, Refusal> {
        done(host, &Request::Forget { run: run.to_owned() }, self.timing.read)
    }

    pub(crate) fn fetch_from(&self, host: &mut Host, run: &str, checkout: &Path, fetch: &[String]) -> Result<Vec<String>, Refusal> {
        let (mut channel, mut reply) = host.request(&Request::Fetch { run: run.to_owned() }, self.timing.read)?;
        let mut fetched = Vec::new();
        let mut missed = Vec::new();
        loop {
            match reply {
                Reply::File { path, size, executable } => match local_target(checkout, &path, fetch) {
                    Ok(target) => match receive_file(&mut channel, &target, size, executable) {
                        Ok(()) => fetched.push(path),
                        Err(Received::Link(error)) => return Err(host.link_refusal(&error)),
                        Err(Received::Local(said)) => {
                            (self.say)(&format!("yonder: cannot write {}: {said}", target.display()));
                            missed.push(path);
                        }
                    },
                    Err(said) => {
                        channel.recv_stream(&mut io::sink()).map_err(|error| host.link_refusal(&error))?;
                        (self.say)(&format!("yonder: skipped {path:?} of the run {run}: {said}"));
                        missed.push(path);
                    }
                },
                Reply::Fetched { .. } => {
                    channel.close();
                    if missed.is_empty() {
                        return Ok(fetched);
                    }
                    return Err(refused(
                        code::FETCH_FAILED,
                        format!(
                            "{} of {} files of the run {run} did not come: {}",
                            missed.len(),
                            missed.len() + fetched.len(),
                            missed.join(", ")
                        ),
                    ));
                }
                Reply::Refused { code, message } => return Err(host.agent_refusal(code, &message)),
                reply => return Err(host.unexpected(&reply)),
            }
            reply = host.recv(&mut channel, self.timing.read)?;
        }
    }
}

/// Why a fetched file did not reach its target.
enum Received {
    /// The channel failed: no later file can come.
    Link(LinkError),
    /// This file failed here, and the channel stays in step.
    Local(String),
}

/// Writes the byte stream that comes next into `<target>.yonder-part`, checks its size, then renames it to `target`. A
/// local failure still reads the stream to its end, so the channel stays in step. A cut stream removes the part.
fn receive_file(channel: &mut Channel, target: &Path, size: u64, executable: bool) -> Result<(), Received> {
    let part = part_path(target);
    let opened = target
        .parent()
        .map_or(Ok(()), fs::create_dir_all)
        .and_then(|()| fs::File::create(&part));
    let file = match opened {
        Ok(file) => file,
        Err(error) => {
            channel.recv_stream(&mut io::sink()).map_err(Received::Link)?;
            return Err(Received::Local(error.to_string()));
        }
    };
    let mut writer = BufWriter::new(file);
    let received = channel.recv_stream(&mut writer);
    let flushed = writer.flush();
    drop(writer);
    let checked = match (received, flushed) {
        (Err(error), _) => Err(Received::Link(error)),
        (Ok(_), Err(error)) => Err(Received::Local(error.to_string())),
        (Ok(bytes), Ok(())) if bytes != size => Err(Received::Local(format!("{bytes} bytes came, not {size}"))),
        (Ok(_), Ok(())) => {
            #[cfg(unix)]
            let marked = mark_executable(&part, executable);
            // Windows has no executable bit.
            #[cfg(not(unix))]
            let marked: io::Result<()> = {
                let _ = executable;
                Ok(())
            };
            marked
                .and_then(|()| fs::rename(&part, target))
                .map_err(|error| Received::Local(error.to_string()))
        }
    };
    if checked.is_err() {
        let _ = fs::remove_file(&part);
    }
    checked
}

#[cfg(unix)]
fn mark_executable(path: &Path, executable: bool) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt as _;
    if executable {
        fs::set_permissions(path, fs::Permissions::from_mode(0o755))
    } else {
        Ok(())
    }
}

fn part_path(target: &Path) -> PathBuf {
    let mut name = target.as_os_str().to_owned();
    name.push(PART_SUFFIX);
    PathBuf::from(name)
}

/// The local file of a staged path: below the checkout, and below one of the `fetch` directories when they are
/// named. Answers why else.
pub(crate) fn local_target(checkout: &Path, path: &str, fetch: &[String]) -> Result<PathBuf, String> {
    let parts: Vec<&str> = path.split('/').collect();
    let plain = |part: &&str| !part.is_empty() && *part != "." && *part != ".." && !part.contains(['\\', ':']);
    if !parts.iter().all(plain) || path.ends_with(PART_SUFFIX) {
        return Err("the path is not a plain relative path".to_owned());
    }
    let below_fetch = fetch.is_empty()
        || fetch.iter().any(|dir| {
            path.strip_prefix(dir.trim_end_matches('/'))
                .is_some_and(|rest| rest.starts_with('/'))
        });
    if !below_fetch {
        return Err(format!("the path is in none of the fetch directories {}", fetch.join(", ")));
    }
    Ok(parts.iter().fold(checkout.to_path_buf(), |target, part| target.join(part)))
}

#[cfg(test)]
mod tests;
