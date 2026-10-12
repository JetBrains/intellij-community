//! The generation: an immutable copy of the dev distribution and its JBR, which every IDE run of a session starts
//! from.
//!
//! A generation is `<runtime root>/bench/generations/<digest>/`:
//!
//! - `<dist name>/`: the clone of the `.dist` directory that Bazel built, such as `idea_dist.dist`. The name stays,
//!   because the launch passes it as `dev.build.dir`;
//! - `jbr/`: the clone of the JBR home of the row launcher;
//! - `ide.config`: the copy of the config of the distribution;
//! - `OpenedPackages.txt`: the copy of the `--add-opens` list of the checkout, which the launch reads;
//! - `generation.json`: the record, written last.
//!
//! The digest is the key: the content of the distribution tree, the config text, the opened packages and the JBR
//! tree, through the
//! [`FileDigestCache`] of `<runtime root>/bench/digest-cache.json`, so a second session over the same build reads
//! only stats. The stage clones into `.stage-<digest>-<pid>` and publishes it with one `rename`, under the lock
//! `generations.lock`, so a reader never sees half a generation and two sessions never stage one digest twice. A
//! generation that exists is reused as it is.
//!
//! The files of a generation are read-only. A relative link inside a tree stays a link. A link that leaves its tree
//! is refused, because the generation would then read the bytes of Bazel again.

use std::fs;
use std::io;
use std::path::{Component, Path, PathBuf};
use std::time::Duration;

use avl_base::fs::create_private_dir;
use avl_base::{Exit, Refusal, RefusalExt, Reporter};
use avl_host_sys::Ctx;
use avl_host_sys::lock::LockManager;
use avl_report::digest::{FileDigestCache, PathDigest, path_sensitive_digest, sha256_text};
use serde::{Deserialize, Serialize};

use super::launch::OPENED_PACKAGES_FILE;
use super::session::BENCH_DIR;

/// The record of a generation, the last file the stage writes.
pub(crate) const RECORD_FILE: &str = "generation.json";

/// The directory of the JBR inside a generation.
pub(crate) const JBR_DIR: &str = "jbr";

/// The copy of the config inside a generation.
const CONFIG_FILE: &str = "ide.config";

/// The version of the generation layout. It is a part of the key, so a change of the layout stages anew.
const LAYOUT: &str = "bench-generation-1";

/// How long a stage waits for another stage that holds the lock.
const LOCK_WAIT: Duration = Duration::from_secs(600);

/// What the stage copies, as Bazel built it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Sources {
    /// The label of the distribution.
    pub(crate) target: String,
    /// The `.dist` directory.
    pub(crate) dist: PathBuf,
    /// The `.ide.config` beside it.
    pub(crate) config: PathBuf,
    /// The home of the JBR, the directory with `bin/java`.
    pub(crate) jbr: PathBuf,
    /// `OpenedPackages.txt` of the checkout.
    pub(crate) opened_packages: PathBuf,
}

/// The fields of a dist config that the launch reads.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct DistConfig {
    /// `home.path`: the name of the `.dist` directory, relative to the config.
    pub(crate) home: String,
    /// `main.class.name`.
    pub(crate) main_class: String,
    /// `platform.prefix`, when the config names one.
    pub(crate) platform_prefix: Option<String>,
    /// `additional.modules`, in config order. The distribution already holds these modules, so the launch passes
    /// none of them.
    pub(crate) additional_modules: Vec<String>,
}

/// A staged generation.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Generation {
    pub(crate) digest: String,
    pub(crate) root: PathBuf,
    /// The distribution inside the generation.
    pub(crate) dist: PathBuf,
    /// `bin/java` of the JBR inside the generation.
    pub(crate) java: PathBuf,
    pub(crate) config: DistConfig,
    /// The generation existed before this stage.
    pub(crate) reused: bool,
}

/// `generation.json`.
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Record {
    digest: String,
    target: String,
    dist_name: String,
    /// Where the bytes came from, for a reader of the generation. The launch never reads these paths.
    sources: RecordSources,
    files: u64,
    bytes: u64,
    staged_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct RecordSources {
    dist: String,
    config: String,
    jbr: String,
    opened_packages: String,
}

/// A refusal for a distribution that the bench cannot stage: the invocation named a target of another shape.
fn unsupported(message: impl Into<String>) -> Refusal {
    Refusal::new("bench_dist_unsupported", Exit::USAGE, message)
}

/// A refusal for a stage that failed on the disk.
fn failed(message: impl Into<String>) -> Refusal {
    Refusal::new("bench_stage_failed", Exit::FAILURE, message)
}

/// Parses a dist config: `key=value` lines. It refuses a config without `home.path` or `main.class.name`.
/// `additional.modules` is a list that a comma separates.
pub(crate) fn parse_config(text: &str) -> Result<DistConfig, String> {
    let mut home = None;
    let mut main_class = None;
    let mut platform_prefix = None;
    let mut additional_modules = Vec::new();
    for line in text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
    {
        let Some((key, value)) = line.split_once('=') else {
            return Err(format!("the line `{line}` is not key=value"));
        };
        let value = value.trim();
        match key.trim() {
            "home.path" => home = Some(value.to_owned()),
            "main.class.name" => main_class = Some(value.to_owned()),
            "platform.prefix" if !value.is_empty() => platform_prefix = Some(value.to_owned()),
            "additional.modules" => {
                additional_modules = value
                    .split(',')
                    .map(str::trim)
                    .filter(|module| !module.is_empty())
                    .map(str::to_owned)
                    .collect();
            }
            _ => {}
        }
    }
    let home = home.ok_or("it has no home.path")?;
    if home.contains(['/', '\\']) || home.is_empty() || home == "." || home == ".." {
        return Err(format!("its home.path {home} is not the name of a sibling directory"));
    }
    Ok(DistConfig {
        home,
        main_class: main_class.ok_or("it has no main.class.name")?,
        platform_prefix,
        additional_modules,
    })
}

/// The key of a generation: the layout version, the distribution tree, the config text, the opened packages and the
/// JBR tree, in order.
pub(crate) fn key(cache: &mut FileDigestCache, sources: &Sources, config_text: &str, dist_name: &str) -> io::Result<String> {
    let dist = cache.digest(&sources.dist)?;
    let opened_packages = cache.digest(&sources.opened_packages)?;
    let jbr = cache.digest(&sources.jbr)?;
    Ok(path_sensitive_digest(&[
        PathDigest::new("@layout", sha256_text(LAYOUT)),
        PathDigest::new(dist_name, dist),
        PathDigest::new(CONFIG_FILE, sha256_text(config_text)),
        PathDigest::new(OPENED_PACKAGES_FILE, opened_packages),
        PathDigest::new(JBR_DIR, jbr),
    ]))
}

/// Stages the generation of `sources`, or reuses the one with the same key.
pub(crate) async fn stage(
    ctx: &Ctx,
    locks: &LockManager,
    runtime_root: &Path,
    sources: &Sources,
    reporter: &Reporter,
) -> Result<Generation, Refusal> {
    let bench = runtime_root.join(BENCH_DIR);
    let generations = bench.join("generations");
    create_private_dir(&generations)?;
    let config_text = fs::read_to_string(&sources.config)
        .map_err(|error| unsupported(format!("cannot read the dist config {}: {error}", sources.config.display())))?;
    let config = parse_config(&config_text)
        .map_err(|reason| unsupported(format!("the dist config {} is refused: {reason}", sources.config.display())))?;
    let dist_name = sources
        .dist
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    if dist_name != config.home {
        return Err(unsupported(format!(
            "the dist config {} names the home {}, and the distribution is {}",
            sources.config.display(),
            config.home,
            sources.dist.display()
        )));
    }
    let _lock = locks
        .acquire_queued(
            ctx,
            &bench.join("generations.lock"),
            "bench stage",
            LOCK_WAIT,
            "bench_stage_busy",
            "another vm bench stages a generation; try again when it is done",
        )
        .await?;

    reporter.note(format!("hashing {} and {}", sources.dist.display(), sources.jbr.display()), None);
    let cache_path = bench.join("digest-cache.json");
    let (mut cache, digest) = {
        let sources = sources.clone();
        let config_text = config_text.clone();
        let dist_name = dist_name.clone();
        blocking(move || {
            let mut cache = FileDigestCache::open(&cache_path);
            let digest = key(&mut cache, &sources, &config_text, &dist_name);
            (cache, digest)
        })
        .await?
    };
    let digest = digest.map_err(|error| failed(format!("cannot hash the distribution: {error}")))?;
    let root = generations.join(&digest);
    let generation = Generation {
        digest: digest.clone(),
        dist: root.join(&dist_name),
        java: root.join(JBR_DIR).join("bin").join(java_name()),
        root: root.clone(),
        config,
        reused: root.join(RECORD_FILE).is_file(),
    };
    if generation.reused {
        cache.save()?;
        return Ok(generation);
    }

    reporter.note(format!("staging the generation {}", root.display()), None);
    let temporary = generations.join(format!(".stage-{digest}-{}", std::process::id()));
    remove_stale(&generations, &digest)?;
    let record_sources = RecordSources {
        dist: sources.dist.display().to_string(),
        config: sources.config.display().to_string(),
        jbr: sources.jbr.display().to_string(),
        opened_packages: sources.opened_packages.display().to_string(),
    };
    let staged = {
        let sources = sources.clone();
        let temporary = temporary.clone();
        let dist_name = dist_name.clone();
        blocking(move || -> Result<(FileDigestCache, Totals), Refusal> {
            let mut totals = Totals::default();
            fs::create_dir(&temporary).map_err(|error| failed(format!("cannot create {}: {error}", temporary.display())))?;
            clone_tree(&sources.dist, &temporary.join(&dist_name), &mut totals)?;
            clone_tree(&sources.jbr, &temporary.join(JBR_DIR), &mut totals)?;
            fs::write(temporary.join(CONFIG_FILE), &config_text)
                .map_err(|error| failed(format!("cannot write the config copy: {error}")))?;
            fscopy::clone_or_copy(&sources.opened_packages, &temporary.join(OPENED_PACKAGES_FILE))
                .map_err(|error| failed(format!("cannot copy {}: {error}", sources.opened_packages.display())))?;
            // The source is hashed again, from the stats the first pass cached. A build that rewrote a file during the
            // clone moves its stat, and the copy then holds bytes the key does not name.
            let again = key(&mut cache, &sources, &config_text, &dist_name)
                .map_err(|error| failed(format!("cannot hash the distribution again: {error}")))?;
            if again != digest {
                return Err(Refusal::new(
                    "bench_dist_changed",
                    Exit::FAILURE,
                    format!(
                        "{} changed while the bench cloned it; a build ran at the same time, so run the bench again",
                        sources.dist.display()
                    ),
                ));
            }
            let record = Record {
                digest: digest.clone(),
                target: sources.target.clone(),
                dist_name,
                sources: record_sources,
                files: totals.files,
                bytes: totals.bytes,
                staged_at: jiff::Timestamp::now().to_string(),
            };
            let text = serde_json::to_string_pretty(&record).map_err(|error| failed(format!("cannot encode {RECORD_FILE}: {error}")))?;
            fs::write(temporary.join(RECORD_FILE), text + "\n").map_err(|error| failed(format!("cannot write {RECORD_FILE}: {error}")))?;
            Ok((cache, totals))
        })
        .await?
    };
    let (mut cache, totals) = match staged {
        Ok(staged) => staged,
        Err(refusal) => {
            let _ = make_writable_and_remove(&temporary);
            return Err(refusal);
        }
    };
    if let Err(error) = fs::rename(&temporary, &root) {
        let _ = make_writable_and_remove(&temporary);
        return Err(failed(format!(
            "cannot publish {} as {}: {error}",
            temporary.display(),
            root.display()
        )));
    }
    cache.save()?;
    reporter.note(
        format!(
            "staged {} files, {} MiB, as {}",
            totals.files,
            totals.bytes / (1024 * 1024),
            root.display()
        ),
        None,
    );
    Ok(generation)
}

/// The digest that the record of a generation names.
pub(crate) fn read_digest(root: &Path) -> Result<String, Refusal> {
    let path = root.join(RECORD_FILE);
    let record: Record = fs::read_to_string(&path)
        .map_err(anyhow::Error::from)
        .and_then(|text| serde_json::from_str(&text).map_err(anyhow::Error::from))
        .map_err(|error| failed(format!("cannot read the generation record {}: {error:#}", path.display())))?;
    Ok(record.digest)
}

/// Runs a file system pass on the blocking pool, so the hash of gigabytes does not stall the runtime.
async fn blocking<T: Send + 'static>(work: impl FnOnce() -> T + Send + 'static) -> Result<T, Refusal> {
    tokio::task::spawn_blocking(work)
        .await
        .map_err(|error| Refusal::internal(format!("the stage task failed: {error}")))
}

const fn java_name() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}

/// Removes the temporary directories of an earlier stage of `digest` that did not finish. The caller holds the lock,
/// so no live stage owns one.
fn remove_stale(generations: &Path, digest: &str) -> Result<(), Refusal> {
    let prefix = format!(".stage-{digest}-");
    let entries = fs::read_dir(generations).map_err(|error| failed(format!("cannot list {}: {error}", generations.display())))?;
    for entry in entries.flatten() {
        if entry.file_name().to_string_lossy().starts_with(&prefix) {
            make_writable_and_remove(&entry.path())
                .map_err(|error| failed(format!("cannot remove {}: {error}", entry.path().display())))?;
        }
    }
    Ok(())
}

/// What one stage copied.
#[derive(Debug, Default)]
struct Totals {
    files: u64,
    bytes: u64,
}

/// Clones the tree `source` to `destination`, which must not exist. A file is cloned and made read-only, a
/// directory is created, and a relative link that stays inside the tree is copied as a link.
fn clone_tree(source: &Path, destination: &Path, totals: &mut Totals) -> Result<(), Refusal> {
    clone_dir(source, source, destination, totals)
}

fn clone_dir(root: &Path, source: &Path, destination: &Path, totals: &mut Totals) -> Result<(), Refusal> {
    fs::create_dir(destination).map_err(|error| failed(format!("cannot create {}: {error}", destination.display())))?;
    let entries = fs::read_dir(source).map_err(|error| failed(format!("cannot list {}: {error}", source.display())))?;
    for entry in entries {
        let entry = entry.map_err(|error| failed(format!("cannot list {}: {error}", source.display())))?;
        let path = entry.path();
        let target = destination.join(entry.file_name());
        let metadata = fs::symlink_metadata(&path).map_err(|error| failed(format!("cannot stat {}: {error}", path.display())))?;
        if metadata.is_symlink() {
            let link = fs::read_link(&path).map_err(|error| failed(format!("cannot read the link {}: {error}", path.display())))?;
            let relative = path
                .parent()
                .and_then(|parent| parent.strip_prefix(root).ok())
                .unwrap_or(Path::new(""));
            if !stays_inside(relative, &link) {
                return Err(unsupported(format!(
                    "{} links to {}, outside {}; a generation holds its own bytes",
                    path.display(),
                    link.display(),
                    root.display()
                )));
            }
            let is_directory = fs::metadata(&path).is_ok_and(|target| target.is_dir());
            fscopy::symlink(&link, &target, is_directory)
                .map_err(|error| failed(format!("cannot create the link {}: {error}", target.display())))?;
        } else if metadata.is_dir() {
            clone_dir(root, &path, &target, totals)?;
        } else if metadata.is_file() {
            fscopy::clone_or_copy(&path, &target).map_err(|error| failed(format!("cannot clone {}: {error}", path.display())))?;
            let mut permissions = metadata.permissions();
            permissions.set_readonly(true);
            fs::set_permissions(&target, permissions)
                .map_err(|error| failed(format!("cannot make {} read-only: {error}", target.display())))?;
            totals.files += 1;
            totals.bytes += metadata.len();
        } else {
            return Err(unsupported(format!("{} is neither a file, a directory nor a link", path.display())));
        }
    }
    Ok(())
}

/// Tells whether a relative link in the directory `relative` of a tree resolves inside the tree, lexically.
pub(crate) fn stays_inside(relative: &Path, link: &Path) -> bool {
    let mut depth = relative
        .components()
        .filter(|component| matches!(component, Component::Normal(_)))
        .count();
    for component in link.components() {
        match component {
            Component::Normal(_) => depth += 1,
            Component::CurDir => {}
            Component::ParentDir => {
                if depth == 0 {
                    return false;
                }
                depth -= 1;
            }
            Component::RootDir | Component::Prefix(_) => return false,
        }
    }
    true
}

/// Removes a tree whose files the stage made read-only. Windows refuses to remove a read-only file.
pub(crate) fn make_writable_and_remove(path: &Path) -> io::Result<()> {
    if cfg!(windows) {
        make_writable(path)?;
    }
    match fs::remove_dir_all(path) {
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        other => other,
    }
}

fn make_writable(path: &Path) -> io::Result<()> {
    let metadata = fs::symlink_metadata(path)?;
    if metadata.is_dir() {
        for entry in fs::read_dir(path)? {
            make_writable(&entry?.path())?;
        }
    } else if metadata.is_file() {
        let mut permissions = metadata.permissions();
        #[expect(
            clippy::permissions_set_readonly_false,
            reason = "the file is about to be removed, and Windows refuses to remove a read-only file"
        )]
        permissions.set_readonly(false);
        fs::set_permissions(path, permissions)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests;
