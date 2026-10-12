//! The fake executables that the fixtures of all test processes share.
//!
//! macOS scans a new executable file at its first exec, and the first execs of new files wait for each other's scans.
//! The exec of a hard link to a file that ran before does not wait. So each fake is one file under [`root`], named by
//! a hash of its content. The first process that needs the fake writes the file, and every later process uses it
//! again. Each fixture gets a hard link to it in a directory of its own. A fake finds the files of its fixture from
//! the path it was started by, so the link keeps the fixtures apart.
//!
//! A shared file is written once and never rewritten. A shared script is read-only, so a write over the link of one
//! fixture fails, and the fake of every other fixture stays the same. Each content version of a fake leaves one file
//! under [`root`] and no directory.

use std::env;
use std::fs;
use std::hash::{DefaultHasher, Hasher};
use std::io;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicU64, Ordering};

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The directory every fake of every test process lives under: `avl-tartfake` in Bazel's `TEST_TMPDIR`, else in
/// the system temporary directory.
pub(crate) fn root() -> &'static Path {
    static ROOT: OnceLock<PathBuf> = OnceLock::new();
    ROOT.get_or_init(|| {
        let base = env::var_os("TEST_TMPDIR").map_or_else(env::temp_dir, PathBuf::from);
        let root = base.join("avl-tartfake");
        if let Err(error) = fs::create_dir_all(&root) {
            panic!("create {}: {error}", root.display());
        }
        root
    })
}

/// A new directory under [`root`], named by the process id and a counter.
pub(crate) fn new_directory() -> PathBuf {
    static NEXT: AtomicU64 = AtomicU64::new(0);
    loop {
        let path = root().join(format!("{}-{}", std::process::id(), NEXT.fetch_add(1, Ordering::Relaxed)));
        match fs::create_dir(&path) {
            Ok(()) => return path,
            // Left behind by an earlier process that had the same pid.
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {}
            Err(error) => panic!("create {}: {error}", path.display()),
        }
    }
}

/// The read-only executable script `content`, as [`publish`] gives it under [`root`]. The first call of a process with
/// a content publishes the file, and each later call with the same content answers that file.
#[cfg(unix)]
pub(crate) fn script(content: &str) -> io::Result<PathBuf> {
    use std::collections::BTreeMap;
    use std::sync::{Mutex, PoisonError};
    static SCRIPTS: Mutex<BTreeMap<String, PathBuf>> = Mutex::new(BTreeMap::new());
    let mut scripts = SCRIPTS.lock().unwrap_or_else(PoisonError::into_inner);
    if let Some(path) = scripts.get(content) {
        return Ok(path.clone());
    }
    let path = publish(root(), "script", "", content.as_bytes())?;
    scripts.insert(content.to_owned(), path.clone());
    Ok(path)
}

/// The program of [`crate::fakebin`], as [`publish`] gives it under [`root`].
#[cfg(windows)]
pub(crate) fn program() -> &'static Path {
    static PROGRAM: OnceLock<PathBuf> = OnceLock::new();
    PROGRAM.get_or_init(|| {
        let program = crate::fakebin::program();
        let published = fs::read(program).and_then(|content| publish(root(), "avl-fake", env::consts::EXE_SUFFIX, &content));
        published.unwrap_or_else(|error| panic!("publish the fake program {} under {}: {error}", program.display(), root().display()))
    })
}

/// Puts `content` in `directory` as the file `<stem>-<hash><suffix>`, where the hash is the hash of `content`, and
/// answers its path. On Unix the file is read-only and executable.
///
/// A file of that name that holds `content` is used again, so every process of every run shares it. A file of that name
/// with another content is a refusal. The writer fills a staging file of its own and renames it to the name, so no
/// reader sees a partial file. When two processes publish at the same time, the second rename replaces the first file
/// with the same content. A fixture that has a link to the first file keeps it. On Windows a rename fails over a
/// program that runs, and then the file of the other process is used. A Rust release can change the hash of
/// `DefaultHasher`, which only adds one file.
fn publish(directory: &Path, stem: &str, suffix: &str, content: &[u8]) -> io::Result<PathBuf> {
    static NEXT: AtomicU64 = AtomicU64::new(0);
    let mut hasher = DefaultHasher::new();
    hasher.write(content);
    let path = directory.join(format!("{stem}-{:016x}{suffix}", hasher.finish()));
    if holds(&path, content)? {
        return Ok(path);
    }
    let staging = directory.join(format!(
        ".{stem}-{}-{}.partial",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    fs::write(&staging, content)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&staging, fs::Permissions::from_mode(0o555))?;
    }
    match fs::rename(&staging, &path) {
        Ok(()) => Ok(path),
        Err(error) => {
            // Best effort: a staging file that stays is a stale name and no fake runs it.
            let _ = fs::remove_file(&staging);
            if holds(&path, content)? { Ok(path) } else { Err(error) }
        }
    }
}

/// Whether the file at `path` holds `content`. No file answers false, and a file with another content is an error.
fn holds(path: &Path, content: &[u8]) -> io::Result<bool> {
    match fs::read(path) {
        Ok(existing) if existing == content => Ok(true),
        Ok(_) => Err(io::Error::other(format!(
            "{} holds another content than its name states; remove the file",
            path.display()
        ))),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(error),
    }
}

/// Puts the shared file `shared` at `path` as a hard link.
///
/// A copy only where no link reaches, which is another file system. Never a symbolic link: a fake finds its fixture
/// from the path it was started by, and a backend that resolves the path of a pinned tool would start the shared file.
pub(crate) fn place(shared: &Path, path: &Path) -> io::Result<()> {
    match fs::hard_link(shared, path) {
        Err(error) if error.kind() == io::ErrorKind::CrossesDevices => fs::copy(shared, path).map(drop),
        placed => placed,
    }
}
