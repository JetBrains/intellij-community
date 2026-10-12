//! The `idea.log` slice.

use std::fs::{File, Metadata};
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

use anyhow::{Context, bail};
use avl_trace::bridge::Facts;

#[cfg(test)]
mod tests;

/// Bounds one slice of `idea.log`. A scenario writes kilobytes; a log that grew by more than this in one scenario is
/// a runaway, and its last bytes are the ones worth keeping.
const MAX_SLICE_BYTES: u64 = 32 << 20;

/// Copies the part of the IDE's `idea.log` written while one scenario ran.
///
/// The facts route says where the log is and how long it was when the scenario started; the recorder reads the file
/// itself, since it runs on the IDE's machine. The file is held open from the start. That is what keeps the slice
/// whole across the two things that move a log away: a rotation renames it, and a restart may begin a new one. An
/// open descriptor still reads the renamed file to its end, shutdown lines included, and when the log the IDE writes
/// at the end is another file, that file's bytes are appended as a second slice.
///
/// Windows is the exception: a file held open there cannot be renamed, which would stop the IDE's own rotation, so on
/// Windows the slicer reopens the log by name instead and loses the tail of a rotated one.
#[derive(Default)]
pub(crate) struct LogSlicer {
    begun: Option<Begun>,
}

struct Begun {
    path: PathBuf,
    start: u64,
    held: Option<File>,
    identity: Identity,
}

impl LogSlicer {
    /// Starts the slice at the facts' log size, or answers why no slice can be taken.
    pub(crate) fn begin(&mut self, facts: &Facts) -> anyhow::Result<()> {
        let path = PathBuf::from(&facts.log_path);
        let file = File::open(&path).context("cannot read the IDE's log")?;
        let identity = Identity::of(&file.metadata()?, &path);
        self.begun = Some(Begun {
            held: (!cfg!(windows)).then_some(file),
            path,
            start: facts.log_size,
            identity,
        });
        Ok(())
    }

    /// Writes the slice to `output`. `latest` is the facts read at the end, `None` when the bridge did not answer, in
    /// which case only the file the scenario started in is read.
    pub(crate) fn cut(&mut self, output: &Path, latest: Option<&Facts>) -> anyhow::Result<()> {
        let Some(begun) = self.begun.take() else {
            return Ok(());
        };
        let mut target = File::create(output)?;
        let mut first = match begun.held {
            Some(file) => file,
            None => File::open(&begun.path)?,
        };
        let mut failures = Vec::new();
        if let Err(error) = copy_slice(&mut target, &mut first, begun.start) {
            failures.push(error.to_string());
        }
        if let Some(latest) = latest.filter(|latest| !latest.log_path.is_empty()) {
            let path = Path::new(&latest.log_path);
            if let Ok(metadata) = path.metadata()
                && Identity::of(&metadata, path) != begun.identity
            {
                match File::open(path).and_then(|mut second| copy_slice(&mut target, &mut second, 0)) {
                    Ok(()) => {}
                    Err(error) => failures.push(error.to_string()),
                }
            }
        }
        if !failures.is_empty() {
            bail!("{}", failures.join("; "));
        }
        Ok(())
    }
}

/// Which file a path names, to tell a rotated log from the one the scenario started in.
///
/// Off unix it is the path, so a log rotated under the same name compares equal to the one the scenario began with
/// and only the first file's slice is kept. Stable Rust exposes no file index on Windows (`file_index` is nightly),
/// and the creation time is no substitute, since NTFS tunnelling hands a file recreated under a just-renamed name the
/// old one's. Windows only has to compile this crate: no lane records there.
#[derive(PartialEq, Eq)]
enum Identity {
    #[cfg(unix)]
    Inode { device: u64, inode: u64 },
    #[cfg(not(unix))]
    Path(PathBuf),
}

impl Identity {
    #[cfg(unix)]
    fn of(metadata: &Metadata, _path: &Path) -> Self {
        use std::os::unix::fs::MetadataExt;
        Self::Inode {
            device: metadata.dev(),
            inode: metadata.ino(),
        }
    }

    #[cfg(not(unix))]
    fn of(_metadata: &Metadata, path: &Path) -> Self {
        Self::Path(path.to_owned())
    }
}

/// Copies `file` from `start` to its current end. A file now shorter than `start` was truncated in place, so all of
/// it is new; a slice longer than [MAX_SLICE_BYTES] keeps its last bytes.
fn copy_slice(target: &mut impl Write, file: &mut File, start: u64) -> io::Result<()> {
    let end = file.metadata()?.len();
    let start = if end < start { 0 } else { start };
    let start = start.max(end.saturating_sub(MAX_SLICE_BYTES));
    file.seek(SeekFrom::Start(start))?;
    io::copy(&mut file.take(end - start), target)?;
    Ok(())
}
