//! Test helpers every crate of the workspace shares.
//!
//! A test runs under two launchers that disagree about where files are. Under cargo the working directory is the
//! crate, and cargo sets `CARGO_MANIFEST_DIR` in the test's environment. Under Bazel the working directory is the
//! runfiles tree of the main repository. `avl_crate` passes the package path as `AVL_PACKAGE` and the runfiles
//! directory of the community module as `AVL_REPO` at compile time, so a path resolves in both.
//! `env!("CARGO_MANIFEST_DIR")` is not an option: rules_rs refuses a binary that embeds the absolute path of its
//! compile action.

use std::env;
use std::fs;
use std::path::{Path, PathBuf};

pub mod fakebin;
mod shared;
pub mod tartfake;

/// A file of the calling crate, e.g. `crate_path!("testdata/lane-transcript.ndjson")`.
///
/// A macro, because `AVL_PACKAGE` and `CARGO_MANIFEST_DIR` must be the caller's, not this crate's.
#[macro_export]
macro_rules! crate_path {
    ($relative:expr) => {
        $crate::resolve_crate_path(option_env!("AVL_PACKAGE"), $relative)
    };
}

#[doc(hidden)]
pub fn resolve_crate_path(package: Option<&str>, relative: &str) -> PathBuf {
    match package {
        Some(package) if under_bazel() => runfile(&format!("{package}/{relative}")),
        _ => manifest_dir().join(relative),
    }
}

/// The crate directory of the running test under cargo.
fn manifest_dir() -> PathBuf {
    env::var_os("CARGO_MANIFEST_DIR")
        .map(PathBuf::from)
        .expect("cargo sets CARGO_MANIFEST_DIR for a test")
}

/// A file of the community module by its path from the module root, e.g. `tools/vm/provision/versions.env`.
/// Under Bazel the file must be in the test's `data`.
pub fn repo_path(relative: &str) -> PathBuf {
    if under_bazel() {
        return runfile(relative);
    }
    community_root().join(relative)
}

/// The root of the community module under cargo: the first ancestor of the crate with a `MODULE.bazel`.
fn community_root() -> PathBuf {
    let start = manifest_dir();
    let mut dir = start.as_path();
    while !dir.join("MODULE.bazel").is_file() {
        dir = dir.parent().expect("the workspace lives inside the community module");
    }
    dir.to_path_buf()
}

/// The root of the ultimate checkout around the community module, under cargo only. `None` under Bazel, and in a
/// community checkout. A test that needs a file of the ultimate root, such as a target that it builds, skips on
/// `None`.
pub fn ultimate_root() -> Option<PathBuf> {
    if under_bazel() {
        return None;
    }
    let community = community_root();
    let root = community.parent()?;
    (root.join("MODULE.bazel").is_file() && root.join("community").is_dir()).then(|| root.to_path_buf())
}

/// The runfiles directory of the community module: `community+` from the ultimate root, `_main` from the community
/// root, and `_main` for a crate that `avl_crate` did not compile.
const REPO: &str = match option_env!("AVL_REPO") {
    Some(repo) => repo,
    None => "_main",
};

fn under_bazel() -> bool {
    env::var_os("TEST_SRCDIR").is_some()
}

/// The runfile of the community module at `path`. The runfiles tree when there is one (Linux, macOS), else the
/// manifest (Windows, where Bazel builds no tree by default).
fn runfile(path: &str) -> PathBuf {
    let srcdir = env::var_os("TEST_SRCDIR").map(PathBuf::from);
    let manifest = env::var_os("RUNFILES_MANIFEST_FILE").map(PathBuf::from);
    runfile_in(srcdir.as_deref(), manifest.as_deref(), REPO, path)
}

/// A runfile by its `$(rlocationpath ...)`, the form `test_env` of `avl_crate` passes, e.g. `_main/pkg/file.jar`.
/// A miss answers the tree path, so the caller's error names the file it wanted.
pub fn rlocation(rlocationpath: &str) -> PathBuf {
    let srcdir = env::var_os("TEST_SRCDIR").map(PathBuf::from);
    let manifest = env::var_os("RUNFILES_MANIFEST_FILE").map(PathBuf::from);
    lookup(srcdir.as_deref(), manifest.as_deref(), rlocationpath)
}

/// [`runfile`] over explicit inputs, so a test can drive it without touching the process environment.
fn runfile_in(srcdir: Option<&Path>, manifest: Option<&Path>, repo: &str, path: &str) -> PathBuf {
    lookup(srcdir, manifest, &format!("{repo}/{path}"))
}

/// The runfile at `key`, an rlocation path.
fn lookup(srcdir: Option<&Path>, manifest: Option<&Path>, key: &str) -> PathBuf {
    if let Some(srcdir) = srcdir {
        let candidate = srcdir.join(key);
        if candidate.exists() {
            return candidate;
        }
    }
    if let Some(text) = manifest.and_then(|manifest| fs::read_to_string(manifest).ok())
        && let Some(found) = from_manifest(&text, key)
    {
        return found;
    }
    // Not found either way: return the tree path, so the caller's error names the file it wanted.
    srcdir.unwrap_or(Path::new("")).join(key)
}

/// Looks `key` up in a runfiles manifest (`<runfile> <target>` per line).
///
/// A manifest lists files only, and [`traces::path`] asks for the goldens by their directory before it joins a
/// file onto it. So a key no line names is read as a directory: a file under it names its source path, and that
/// path without the part below the key is the directory. The data are source files, whose runfiles layout is the
/// source layout, and an entry whose target does not end in the same relative path is skipped.
fn from_manifest(text: &str, key: &str) -> Option<PathBuf> {
    let entries = || {
        text.lines()
            .filter_map(|line| line.split_once(' '))
            .filter(|(_, target)| !target.is_empty())
    };
    if let Some((_, target)) = entries().find(|(name, _)| *name == key) {
        return Some(PathBuf::from(target));
    }
    let prefix = format!("{key}/");
    entries().find_map(|(name, target)| {
        let below = name.strip_prefix(&prefix)?;
        let target = target.replace('\\', "/");
        let directory = target.strip_suffix(below)?.strip_suffix('/')?;
        Some(PathBuf::from(directory))
    })
}

/// The scenario-trace goldens: the lane transcript and the golden bundles. Another program reads them too, so they stay
/// plain files rather than a snapshot tool's format.
///
/// They are `avl-trace`'s `testdata`, which the Kotlin contract test and the docs site read too. A test that reads them
/// needs `//tools/vm/crates/avl-trace:testdata` of the community module in its `data` under Bazel. The recorder's
/// replay test writes them: `UPDATE_EXPECT=1`, the variable of `expect-test`, rewrites the text files.
pub mod traces {
    use super::{Path, PathBuf, fs};

    /// Where the scenario-trace goldens live, from the root of the community module.
    const TRACE_TESTDATA: &str = "tools/vm/crates/avl-trace/testdata";

    /// The lane transcript every golden bundle was recorded from.
    pub const TRANSCRIPT: &str = "lane-transcript.ndjson";
    /// The lane transcript cut off mid-scenario.
    pub const TRUNCATED_TRANSCRIPT: &str = "lane-transcript-truncated.ndjson";
    /// The bundle [`TRANSCRIPT`] records.
    pub const EXAMPLE_BUNDLE: &str = "example.airtrace";
    /// The bundle [`TRUNCATED_TRANSCRIPT`] records.
    pub const TRUNCATED_EXAMPLE_BUNDLE: &str = "example-truncated.airtrace";

    /// A file or directory of the trace goldens, by its `/`-separated path under them.
    pub fn path(relative: &str) -> PathBuf {
        join(super::repo_path(TRACE_TESTDATA), relative)
    }

    /// The golden bundle [`EXAMPLE_BUNDLE`].
    pub fn example_bundle() -> PathBuf {
        path(EXAMPLE_BUNDLE)
    }

    /// A file of the golden bundle, by its bundle path.
    pub fn example_file(name: &str) -> Vec<u8> {
        let path = join(example_bundle(), name);
        fs::read(&path).unwrap_or_else(|error| panic!("read {}: {error}", path.display()))
    }

    /// The lines of a golden file, each without its newline. Every writer of these formats ends each line with one,
    /// and none writes an empty line.
    pub fn lines(path: &Path) -> Vec<Vec<u8>> {
        let content = fs::read(path).unwrap_or_else(|error| panic!("read {}: {error}", path.display()));
        let Some(content) = content.strip_suffix(b"\n") else {
            panic!("{} does not end with a newline", path.display());
        };
        let lines: Vec<Vec<u8>> = content.split(|byte| *byte == b'\n').map(<[u8]>::to_vec).collect();
        assert!(lines.iter().all(|line| !line.is_empty()), "{} has an empty line", path.display());
        lines
    }

    /// [`lines`] of a file of the trace goldens, such as [`TRANSCRIPT`].
    pub fn file_lines(relative: &str) -> Vec<Vec<u8>> {
        lines(&path(relative))
    }

    /// Copies a tree by content: Bazel's runfiles are symbolic links, a bundle is a tree of real files, and a link
    /// out of a root is what the trace server refuses.
    pub fn copy_tree(from: &Path, to: &Path) {
        fs::create_dir_all(to).unwrap_or_else(|error| panic!("create {}: {error}", to.display()));
        let entries = fs::read_dir(from).unwrap_or_else(|error| panic!("list {}: {error}", from.display()));
        for entry in entries {
            let entry = entry.unwrap_or_else(|error| panic!("list {}: {error}", from.display()));
            let (source, target) = (entry.path(), to.join(entry.file_name()));
            // `metadata` follows a runfiles link to what it names.
            let metadata = fs::metadata(&source).unwrap_or_else(|error| panic!("stat {}: {error}", source.display()));
            if metadata.is_dir() {
                copy_tree(&source, &target);
            } else {
                let content = fs::read(&source).unwrap_or_else(|error| panic!("read {}: {error}", source.display()));
                fs::write(&target, content).unwrap_or_else(|error| panic!("write {}: {error}", target.display()));
            }
        }
    }

    /// `relative` joined one segment at a time, so a `/`-separated path is native on Windows too.
    fn join(base: PathBuf, relative: &str) -> PathBuf {
        relative.split('/').fold(base, |path, segment| path.join(segment))
    }
}

/// Writes an executable shell script `name` into `dir` and returns its path: the stand-in for `tart`, `prlctl`,
/// `ffmpeg` or `bazel` in a test. `body` follows the `#!/bin/sh` line.
#[cfg(unix)]
pub fn fake_executable(dir: &Path, name: &str, body: &str) -> std::io::Result<PathBuf> {
    use std::os::unix::fs::PermissionsExt;
    let path = dir.join(name);
    fs::write(&path, format!("#!/bin/sh\n{body}"))?;
    fs::set_permissions(&path, fs::Permissions::from_mode(0o755))?;
    Ok(path)
}

/// [`fake_executable`] for a fake that many fixtures install: the file is a hard link to the one script of `body`,
/// which all test processes share. macOS scans only a new executable file at its first exec, so the exec of a link
/// does not wait. `body` is the same for each fixture, so the script finds the files of its fixture in
/// `$(dirname "$0")`. The script is read-only.
#[cfg(unix)]
pub fn shared_fake_executable(dir: &Path, name: &str, body: &str) -> std::io::Result<PathBuf> {
    let path = dir.join(name);
    shared::place(&shared::script(&format!("#!/bin/sh\n{body}"))?, &path)?;
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crate_path_finds_this_crate() {
        assert!(crate_path!("Cargo.toml").is_file());
    }

    // Windows has only the manifest, and the goldens are asked for by directory, which no manifest line names.
    #[test]
    fn a_manifest_resolves_a_file_and_a_directory() {
        let dir = env::temp_dir().join(format!("avl-testkit-manifest-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let manifest = dir.join("MANIFEST");
        fs::write(
            &manifest,
            "_main/pkg/Cargo.toml /src/pkg/Cargo.toml\n\
             _main/pkg/testdata/empty \n\
             _main/pkg/testdata/bundle/a.json /src/pkg/testdata/bundle/a.json\n\
             _main/pkg/testdatum.txt /src/pkg/testdatum.txt\n",
        )
        .unwrap();
        let resolve = |path: &str| runfile_in(None, Some(&manifest), "_main", path);
        assert_eq!(resolve("pkg/Cargo.toml"), PathBuf::from("/src/pkg/Cargo.toml"));
        assert_eq!(resolve("pkg/testdata"), PathBuf::from("/src/pkg/testdata"));
        assert_eq!(resolve("pkg/testdata/bundle"), PathBuf::from("/src/pkg/testdata/bundle"));
        // A sibling that shares the prefix is not under the directory, and a miss names the tree path.
        assert_eq!(resolve("pkg/testdat"), PathBuf::from("_main/pkg/testdat"));
        fs::remove_dir_all(&dir).unwrap();
    }

    // A copy holds the same files by content, at any depth.
    #[test]
    fn copy_tree_copies_every_file_at_every_depth() {
        let dir = env::temp_dir().join(format!("avl-testkit-copy-{}", std::process::id()));
        let (from, to) = (dir.join("from"), dir.join("to"));
        fs::create_dir_all(from.join("snap/deep")).unwrap();
        fs::write(from.join("bundle.json"), b"{}").unwrap();
        fs::write(from.join("snap/deep/0001.webp"), b"webp").unwrap();
        traces::copy_tree(&from, &to);
        assert_eq!(fs::read(to.join("bundle.json")).unwrap(), b"{}");
        assert_eq!(fs::read(to.join("snap/deep/0001.webp")).unwrap(), b"webp");
        fs::remove_dir_all(&dir).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn fake_executable_runs() {
        let dir = env::temp_dir().join(format!("avl-testkit-exe-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let exe = fake_executable(&dir, "tool", "echo hello\n").unwrap();
        let out = std::process::Command::new(&exe).output().unwrap();
        assert_eq!(out.stdout, b"hello\n");
        fs::remove_dir_all(&dir).unwrap();
    }
}
