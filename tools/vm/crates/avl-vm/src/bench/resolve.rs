//! What the host Bazel built for a session: the `.dist` directory and its config, and the async-profiler library.
//!
//! The distribution holds its JBR, as a production distribution does, so the stage takes the JBR from the `.dist`
//! directory. The JBR that `avl-wire`'s runtime descriptor names is the archive for a guest, not for this host.

use std::path::{Path, PathBuf};
use std::time::Duration;

use avl_base::{Exit, Refusal, RefusalExt};
use avl_host_sys::guest::BazelHost;
use avl_host_sys::{Ctx, Runner, SpawnOptions};

use super::stage::Sources;
use crate::lane::Bazel;

/// The target whose dependencies hold the async-profiler jar.
pub(crate) const PROFILER_TARGET: &str = "//plugins/profiler/ultimate/idea-async-profiler";
/// The macOS library inside the async-profiler jar.
const PROFILER_ENTRY: &str = "binaries/macos/libasyncProfiler.dylib";
/// Bounds the `unzip` of the profiler library. It extracts one entry of a few megabytes from a local jar.
const UNZIP_TIMEOUT: Duration = Duration::from_secs(60);

/// The opened packages of the checkout, which the Starter reads too.
pub(crate) const OPENED_PACKAGES: &str = "community/platform/platform-impl/resources/META-INF/OpenedPackages.txt";

/// The suffix of the label of a self-contained distribution.
const DIST_SUFFIX: &str = "_dist";

fn unsupported(message: impl Into<String>) -> Refusal {
    Refusal::new("bench_dist_unsupported", Exit::USAGE, message)
}

/// Refuses a label that does not name a self-contained distribution, such as `//build:idea_dist`, before the build.
pub(crate) fn check_dist_label(target: &str) -> Result<(), Refusal> {
    match target.strip_suffix(DIST_SUFFIX) {
        Some(row) if row.contains(':') && !row.ends_with(':') => Ok(()),
        _ => Err(Refusal::usage(format!(
            "--target {target} is not the label of a self-contained distribution, such as //build:idea_dist"
        ))),
    }
}

/// The `.dist` directory and the `.ide.config` among the lines of `cquery --output=files`.
pub(crate) fn dist_outputs(files: &str) -> (Option<&str>, Option<&str>) {
    let lines = || files.lines().map(str::trim).filter(|line| !line.is_empty());
    (
        lines().find(|line| line.ends_with(".dist")),
        lines().find(|line| line.ends_with(".ide.config")),
    )
}

/// An output path of `cquery`, which is relative to the execution root.
async fn output_path(ctx: &Ctx, bazel: &Bazel, line: &str) -> Result<PathBuf, Refusal> {
    let path = Path::new(line);
    if path.is_absolute() {
        return Ok(path.to_path_buf());
    }
    Ok(bazel.execution_root(ctx).await?.join(path))
}

/// The sources of the generation of `target`, after the build of `target`.
pub(crate) async fn sources(ctx: &Ctx, bazel: &Bazel, repo_root: &Path, target: &str) -> Result<Sources, Refusal> {
    let files = bazel
        .query(ctx, "cquery", &["--output=files".to_owned(), target.to_owned()])
        .await?;
    let (Some(dist), config) = dist_outputs(&files) else {
        return Err(unsupported(format!(
            "{target} has no .dist output, so it is no self-contained distribution"
        )));
    };
    let dist = output_path(ctx, bazel, dist).await?;
    let config = match config {
        Some(config) => output_path(ctx, bazel, config).await?,
        None => dist.with_extension("ide.config"),
    };
    Ok(Sources {
        target: target.to_owned(),
        dist,
        config,
        opened_packages: repo_root.join(OPENED_PACKAGES),
    })
}

/// The async-profiler jar among the lines of `cquery --output=files`.
pub(crate) fn profiler_jar(files: &str) -> Option<&str> {
    files.lines().map(str::trim).find(|line| {
        let name = line.rsplit('/').next().unwrap_or(line);
        name.starts_with("async-profiler-")
            && Path::new(name).extension().is_some_and(|extension| extension == "jar")
            && !name.ends_with("-sources.jar")
    })
}

/// Extracts the macOS async-profiler library from the jar that Bazel resolves for [`PROFILER_TARGET`].
pub(crate) async fn profiler(ctx: &Ctx, bazel: &Bazel, runner: &Runner, session: &Path) -> Result<PathBuf, Refusal> {
    let refuse = |message: String| Refusal::new("profiler_unavailable", Exit::USAGE, format!("--profile: {message}"));
    let query = format!("filter(\"async-profiler\", deps({PROFILER_TARGET}))");
    let files = bazel.query(ctx, "cquery", &["--output=files".to_owned(), query.clone()]).await?;
    let Some(jar) = profiler_jar(&files) else {
        return Err(refuse(format!("no async-profiler jar among the files of {query}")));
    };
    let base = if jar.starts_with("external/") {
        PathBuf::from(bazel.query(ctx, "info", &["output_base".to_owned()]).await?.trim())
    } else {
        bazel.execution_root(ctx).await?
    };
    let jar = base.join(jar);
    let library = session.join("async-profiler").join("libasyncProfiler.dylib");
    if let Some(parent) = library.parent() {
        std::fs::create_dir_all(parent).map_err(|error| refuse(format!("cannot create {}: {error}", parent.display())))?;
    }
    let argv = vec![
        "unzip".to_owned(),
        "-p".to_owned(),
        jar.display().to_string(),
        PROFILER_ENTRY.to_owned(),
    ];
    let extracted = runner
        .checked_to_file(ctx, &argv, &library, &SpawnOptions::within(UNZIP_TIMEOUT))
        .await;
    let size = std::fs::metadata(&library).map_or(0, |metadata| metadata.len());
    if extracted.is_err() || size == 0 {
        return Err(refuse(format!("{} has no {PROFILER_ENTRY}", jar.display())));
    }
    Ok(library)
}

#[cfg(test)]
mod tests;
