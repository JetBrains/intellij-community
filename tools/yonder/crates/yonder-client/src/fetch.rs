//! The staged files of a run: `Fetch` copies them into the local checkout, and `Forget` removes the run on the agent.
//!
//! Each file goes to `<name>.yonder-part` first, and only a file of the size that the agent named takes its name. So a
//! cut connection leaves no file that looks whole. A stream writes at most the size that the agent named, and the rest
//! is read and dropped. A fetch that did not copy every file is a failure, and the run then keeps its stage on the agent
//! for another `yonder fetch`.
//!
//! No write follows a local link: a target with a link in a parent directory below the checkout is refused, and the
//! part is a new file that the open makes itself (`create_new`, and `O_NOFOLLOW` on Unix). A part that a cut fetch left
//! goes first.
//!
//! The host chooses the paths, so the client takes only what the local record of the run asked for (`record.rs`): a
//! staged path below a `fetch` directory of the record, a file of an output that the record names, and the changes when
//! the record asked for them. It never takes a path inside a `.git` directory: a hook or a `core.fsmonitor` there would
//! run code here. A file system that ignores the case, such as APFS, reads `.GIT` as `.git`, and Windows drops a
//! trailing dot or space, so these spellings count too.
//!
//! A file of an output replaces the local copy of the output: the first file of an output removes the local file or
//! directory of its path. The patch of the changes goes to the patch file of the run, in the git directory.

use std::collections::BTreeSet;
use std::fs;
use std::io::{self, BufWriter, Write};
use std::path::{Path, PathBuf};

use yonder_link::proto::{FileKind, Reply, Request, is_run_id};
use yonder_link::{Channel, LinkError};

use crate::bd::Scan;
use crate::host::Host;
use crate::record::{RunFiles, RunRecord};
use crate::run::{Fetched, done};
use crate::{Client, Refusal, code, refused};

/// The suffix of a file that is not complete yet.
const PART_SUFFIX: &str = ".yonder-part";

impl Client<'_> {
    /// Copies the files of a run into `checkout`, as its local record asks: the staged files below its `fetch`
    /// directories, its outputs and its changes. An explicit `fetch` replaces the directories of the record. Without a
    /// record, only the staged files below an explicit `fetch` come, and no `fetch` refuses. Answers the copied paths,
    /// relative to the checkout with `/` separators.
    pub fn fetch(&self, alias: &str, run: &str, checkout: &Path, fetch: &[String]) -> Result<Vec<String>, Refusal> {
        if !is_run_id(run) {
            return Err(refused(code::USAGE, format!("{run:?} cannot name a run")));
        }
        let files = RunFiles::new(&self.snapshot(checkout, Scan::Head, 1)?.patch_dir)?;
        let record = match files.read(run)? {
            Some(record) if fetch.is_empty() => record,
            Some(record) => RunRecord {
                fetch: fetch.to_vec(),
                ..record
            },
            None if fetch.is_empty() => {
                return Err(refused(
                    code::USAGE,
                    format!(
                        "{} has no record of the run {run}, so the files to take are unknown: `--fetch <dir>` takes the staged files below a directory",
                        files.dir().display()
                    ),
                ));
            }
            None => RunRecord {
                fetch: fetch.to_vec(),
                ..RunRecord::default()
            },
        };
        let mut host = self.host(alias)?;
        let fetched = self.fetch_run(&mut host, run, checkout, &record, &files.patch(run))?;
        if let Some(changes) = &fetched.changes {
            (self.say)(&format!(
                "yonder: the changes of the run {run} are in {}: `git apply` applies them",
                changes.display()
            ));
        }
        Ok(fetched.files)
    }

    /// Removes a run and its stage on the agent.
    pub fn forget(&self, alias: &str, run: &str) -> Result<String, Refusal> {
        let mut host = self.host(alias)?;
        self.forget_on(&mut host, run)
    }

    pub(crate) fn forget_on(&self, host: &mut Host, run: &str) -> Result<String, Refusal> {
        done(host, &Request::Forget { run: run.to_owned() }, self.timing.read)
    }

    /// Copies the files of a run that `record` asks for: the staged files below `checkout`, the outputs in place of
    /// their local copies, and the patch of the changes to `patch`. Each other file is skipped, and fails the fetch.
    pub(crate) fn fetch_run(
        &self,
        host: &mut Host,
        run: &str,
        checkout: &Path,
        record: &RunRecord,
        patch: &Path,
    ) -> Result<Fetched, Refusal> {
        let (mut channel, mut reply) = host.request(&Request::Fetch { run: run.to_owned() }, self.timing.read)?;
        let mut fetched = Fetched {
            complete: true,
            ..Fetched::default()
        };
        let mut missed = Vec::new();
        let mut replaced = BTreeSet::new();
        loop {
            match reply {
                Reply::File {
                    path,
                    size,
                    executable,
                    kind,
                } => {
                    let target = match &kind {
                        FileKind::Staged => local_target(checkout, &path, &record.fetch),
                        FileKind::Output { root } if !record.outputs.contains(root) => {
                            Err(format!("the run did not ask for the output {root}"))
                        }
                        FileKind::Output { root } => output_target(checkout, root, &path).inspect(|_| {
                            if replaced.insert(root.clone())
                                && let Err(error) = remove_output(checkout, root)
                            {
                                (self.say)(&format!("yonder: cannot remove the local output {root}: {error}"));
                            }
                        }),
                        FileKind::Changes if record.changes => Ok(patch.to_path_buf()),
                        FileKind::Changes => Err("the run did not ask for its changes".to_owned()),
                    };
                    // The client chose the file of the patch, so only the paths of the host have parents to check.
                    let base = if kind == FileKind::Changes {
                        patch.parent().unwrap_or(patch)
                    } else {
                        checkout
                    };
                    match target {
                        Ok(target) => match receive_file(&mut channel, base, &target, size, executable) {
                            Ok(()) if kind == FileKind::Changes => fetched.changes = Some(target),
                            Ok(()) => fetched.files.push(path),
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
                    }
                }
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
                            missed.len() + fetched.files.len() + usize::from(fetched.changes.is_some()),
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

/// The local file of a file of the output `root`: `root` itself or a path below it, below the checkout.
pub(crate) fn output_target(checkout: &Path, root: &str, path: &str) -> Result<PathBuf, String> {
    plain_target(checkout, root)?;
    let below = path == root || path.strip_prefix(root).is_some_and(|rest| rest.starts_with('/'));
    if !below {
        return Err(format!("the path is not in its output {root}"));
    }
    plain_target(checkout, path)
}

/// Removes the local copy of the output `root`. A link in a parent directory refuses, so the removal stays below the
/// checkout.
fn remove_output(checkout: &Path, root: &str) -> io::Result<()> {
    let path = checkout.join(root);
    check_parents(checkout, &path, false)?;
    remove_local(&path)
}

/// Checks each directory between `base` and `target`: no one may be a link, so that a write or a removal at `target`
/// stays below `base`. With `create`, a missing directory is made. Without it, a missing directory ends the check.
fn check_parents(base: &Path, target: &Path, create: bool) -> io::Result<()> {
    let Ok(below) = target.strip_prefix(base) else {
        return Err(io::Error::other(format!("{} is not below {}", target.display(), base.display())));
    };
    let parts: Vec<_> = below.components().collect();
    let mut dir = base.to_path_buf();
    for part in &parts[..parts.len().saturating_sub(1)] {
        dir.push(part);
        match fs::symlink_metadata(&dir) {
            Ok(metadata) if metadata.file_type().is_symlink() => {
                return Err(io::Error::other(format!("{} is a link", dir.display())));
            }
            Ok(metadata) if metadata.is_dir() => {}
            Ok(_) => return Err(io::Error::other(format!("{} is not a directory", dir.display()))),
            Err(error) if error.kind() == io::ErrorKind::NotFound && create => fs::create_dir(&dir)?,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
            Err(error) => return Err(error),
        }
    }
    Ok(())
}

/// Removes a local file, link or directory. A missing one is no failure.
fn remove_local(path: &Path) -> io::Result<()> {
    let removed = match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_dir() => fs::remove_dir_all(path),
        Ok(_) => fs::remove_file(path),
        Err(error) => Err(error),
    };
    match removed {
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        other => other,
    }
}

/// Why a fetched file did not reach its target.
enum Received {
    /// The channel failed: no later file can come.
    Link(LinkError),
    /// This file failed here, and the channel stays in step.
    Local(String),
}

/// Writes the byte stream that comes next into `<target>.yonder-part`, checks its size, then renames it to `target`.
/// The parent directories of `target` below `base` are made, and a link among them refuses. Only `size` bytes go into
/// the part. A local failure still reads the stream to its end, so the channel stays in step. A cut stream removes the
/// part.
fn receive_file(channel: &mut Channel, base: &Path, target: &Path, size: u64, executable: bool) -> Result<(), Received> {
    let part = part_path(target);
    let opened = check_parents(base, target, true).and_then(|()| open_part(&part));
    let file = match opened {
        Ok(file) => file,
        Err(error) => {
            channel.recv_stream(&mut io::sink()).map_err(Received::Link)?;
            return Err(Received::Local(error.to_string()));
        }
    };
    let mut writer = Capped {
        inner: BufWriter::new(file),
        left: size,
    };
    let received = channel.recv_stream(&mut writer);
    let flushed = writer.inner.flush();
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

/// Makes the part as a new file. A part that a cut fetch left goes first: a file or a link, never what a link names.
fn open_part(part: &Path) -> io::Result<fs::File> {
    match fs::remove_file(part) {
        Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(error),
        _ => {}
    }
    let mut options = fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(libc::O_NOFOLLOW);
    }
    options.open(part)
}

/// A writer that passes the first `left` bytes and drops the rest, so a stream over its size cannot fill the disk.
struct Capped<W> {
    inner: W,
    left: u64,
}

impl<W: Write> Write for Capped<W> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let taken = usize::try_from(self.left).map_or(buf.len(), |left| left.min(buf.len()));
        self.inner.write_all(&buf[..taken])?;
        self.left -= taken as u64;
        Ok(buf.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
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

/// The local file of a staged path: below the checkout, below one of the `fetch` directories, and outside each `.git`
/// directory. Answers why else.
pub(crate) fn local_target(checkout: &Path, path: &str, fetch: &[String]) -> Result<PathBuf, String> {
    let target = plain_target(checkout, path)?;
    let below_fetch = fetch.iter().any(|dir| {
        path.strip_prefix(dir.trim_end_matches('/'))
            .is_some_and(|rest| rest.starts_with('/'))
    });
    if !below_fetch {
        return Err(format!("the path is in none of the fetch directories {}", fetch.join(", ")));
    }
    Ok(target)
}

/// The local file of a path that the host chose: a plain relative path below the checkout, outside each `.git`
/// directory. Answers why else.
fn plain_target(checkout: &Path, path: &str) -> Result<PathBuf, String> {
    let parts: Vec<&str> = path.split('/').collect();
    let plain = |part: &&str| !part.is_empty() && *part != "." && *part != ".." && !part.contains(['\\', ':']);
    if !parts.iter().all(plain) || path.ends_with(PART_SUFFIX) {
        return Err("the path is not a plain relative path".to_owned());
    }
    if parts.iter().any(|part| is_git_dir(part)) {
        return Err("the path is inside a .git directory".to_owned());
    }
    Ok(parts.iter().fold(checkout.to_path_buf(), |target, part| target.join(part)))
}

/// Whether a path part names a `.git` directory on some file system: any case, a trailing dot or space that Windows
/// drops, and `git~1`, the short name of `.git` on NTFS.
fn is_git_dir(part: &str) -> bool {
    let name = part.trim_end_matches(['.', ' ']);
    name.eq_ignore_ascii_case(".git") || name.eq_ignore_ascii_case("git~1")
}

#[cfg(test)]
mod tests;
