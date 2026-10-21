//! The trace viewer that `teamcity-publish` puts beside the bundles: a built one, or one that the step builds in the
//! checkout.
//!
//! The build is the docs project's `pnpm run build:viewer`, with the node and the pnpm of `air-trace serve`'s site
//! build ([site_tools]), after `pnpm install --frozen-lockfile` in each directory without `node_modules`. It writes
//! `<checkout>/out/air-trace-viewer`. A viewer there that is newer than its sources is not built again. The build has
//! a time limit, and its output goes to stderr as it comes.

use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use avl_host_sys::viewer::format_duration;
use tokio_util::sync::CancellationToken;

use crate::serve::{BuildOutput, SiteTools, build_site_with, site_installs, site_needs_build, site_tools, tools_text};

/// The page of the viewer, at the top of its build.
pub(super) const VIEWER_PAGE: &str = "index.html";
/// The last step of the viewer build.
const VIEWER_BUILD: [&str; 2] = ["run", "build:viewer"];
/// How long the viewer build may take, the install of its dependencies included.
pub(super) const VIEWER_BUILD_TIMEOUT: Duration = Duration::from_mins(10);

/// Where the viewer of a publish comes from.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum ViewerSource {
    /// A built viewer, `--viewer`.
    Built(PathBuf),
    /// The checkout whose docs project the step builds the viewer in, within the timeout. The tools are the ones of
    /// [site_tools] when `None`.
    Build {
        checkout: PathBuf,
        tools: Option<SiteTools>,
        timeout: Duration,
    },
    /// No `--viewer` and no checkout.
    Missing,
}

/// The directory of the built viewer, or why there is none. A build says on stderr what it runs, its output, and the
/// time it took.
pub(super) fn viewer_dir(source: &ViewerSource, stderr: &mut dyn Write) -> io::Result<Result<PathBuf, String>> {
    let (checkout, tools, timeout) = match source {
        ViewerSource::Built(dir) => return Ok(Ok(dir.clone())),
        ViewerSource::Missing => return Ok(Err("no --viewer DIR, and no checkout to build one in".to_owned())),
        ViewerSource::Build { checkout, tools, timeout } => (checkout, tools, *timeout),
    };
    let project = checkout.join("plugins").join("air").join("docs");
    let kernel = checkout.join("community").join("tools").join("docs-site");
    let built = checkout.join("out").join("air-trace-viewer");
    if !project.join("package.json").is_file() {
        return Ok(Err(format!(
            "no --viewer DIR, and {} holds no docs project to build one from",
            checkout.display()
        )));
    }
    if site_needs_build(Some(&project), Some(&kernel), Some(&built)).is_none() {
        writeln!(
            stderr,
            "air-trace teamcity-publish: the viewer in {} is newer than its sources; no build",
            built.display()
        )?;
        return Ok(Ok(built));
    }
    let why = if built.join(VIEWER_PAGE).is_file() {
        "the viewer is older than its sources"
    } else {
        "the viewer is not built"
    };
    writeln!(
        stderr,
        "air-trace teamcity-publish: {why}; building it with `pnpm run build:viewer` in {}",
        project.display()
    )?;
    let start = Instant::now();
    let ended = run_build(&project, &kernel, tools.clone(), timeout, stderr)?;
    let seconds = start.elapsed().as_secs_f64();
    Ok(match ended {
        Ok(tools) => {
            writeln!(
                stderr,
                "air-trace teamcity-publish: built the viewer in {seconds:.1} s {}",
                tools_text(&tools)
            )?;
            Ok(built)
        }
        Err(reason) => Err(format!("the viewer build failed after {seconds:.1} s: {reason}")),
    })
}

/// Runs the viewer build on a runtime of its own thread, and writes each line of its output to stderr as it comes.
/// Answers the tools it built with, or why it failed. At the timeout the build is dropped, which kills the step that
/// runs.
fn run_build(
    project: &Path,
    kernel: &Path,
    tools: Option<SiteTools>,
    timeout: Duration,
    stderr: &mut dyn Write,
) -> io::Result<Result<SiteTools, String>> {
    let (sender, lines) = std::sync::mpsc::channel::<String>();
    std::thread::scope(|scope| {
        let worker = scope.spawn(move || {
            let runtime = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|error| format!("cannot start the build's runtime: {error}"))?;
            let output: BuildOutput = Arc::new(move |chunk: &str| {
                for line in chunk.trim_end_matches('\n').split('\n') {
                    let _ = sender.send(line.to_owned());
                }
            });
            let cancel = CancellationToken::new();
            let build = async {
                let tools = match tools {
                    Some(tools) => tools,
                    None => site_tools(&output, &cancel).await?,
                };
                let installs = site_installs(project, Some(kernel));
                build_site_with(&tools, &installs, project, &VIEWER_BUILD, &output, &cancel).await?;
                Ok(tools)
            };
            // The timer of the timeout needs the runtime, so it starts inside it.
            match runtime.block_on(async { tokio::time::timeout(timeout, build).await }) {
                Ok(built) => built.map_err(|refused: crate::Refusal| refused.message),
                Err(_) => Err(format!("it did not end within {}", format_duration(timeout))),
            }
        });
        for line in lines {
            writeln!(stderr, "{line}")?;
        }
        Ok(worker.join().unwrap_or_else(|_| Err("the viewer build panicked".to_owned())))
    })
}

/// Copies the built viewer into out. Each entry at the top of the viewer, a file or a directory, replaces the entry of
/// that name in out. Answers the entries, each with whether it is a directory, or why the viewer is not published. A
/// viewer inside out, or out inside the viewer, is refused, because the copy would remove or copy itself.
pub(super) fn copy_viewer(viewer: &Path, out: &Path) -> Result<Vec<(String, bool)>, String> {
    if !viewer.join(VIEWER_PAGE).is_file() {
        return Err(format!("{} holds no {VIEWER_PAGE}", viewer.display()));
    }
    let failed = |path: &Path, error: &dyn std::fmt::Display| format!("cannot copy {}: {error}", path.display());
    if let (Some(real_viewer), Some(real_out)) = (real_path_of(viewer), real_path_of(out))
        && (real_viewer.starts_with(&real_out) || real_out.starts_with(&real_viewer))
    {
        return Err(format!("{} and the output {} overlap", viewer.display(), out.display()));
    }
    fs::create_dir_all(out).map_err(|error| failed(out, &error))?;
    let mut entries = Vec::new();
    for entry in fs::read_dir(viewer).map_err(|error| failed(viewer, &error))? {
        let entry = entry.map_err(|error| failed(viewer, &error))?;
        let is_dir = fs::metadata(entry.path()).map_err(|error| failed(&entry.path(), &error))?.is_dir();
        entries.push((entry.file_name().to_string_lossy().into_owned(), is_dir));
    }
    entries.sort();
    for (name, is_dir) in &entries {
        let source = viewer.join(name);
        let target = out.join(name);
        let removed = match fs::symlink_metadata(&target) {
            Ok(existing) if existing.is_dir() => fs::remove_dir_all(&target),
            Ok(_) => fs::remove_file(&target),
            Err(_) => Ok(()),
        };
        removed.map_err(|error| failed(&target, &error))?;
        if *is_dir {
            copy_tree(&source, &target)?;
        } else {
            copy_file(&source, &target).map_err(|error| failed(&source, &error))?;
        }
    }
    Ok(entries)
}

/// The real path of `path`, which may not exist yet: the real path of its nearest ancestor that exists, with the rest
/// of `path` after it.
fn real_path_of(path: &Path) -> Option<PathBuf> {
    let path = std::path::absolute(path).ok()?;
    let existing = path.ancestors().find(|ancestor| ancestor.exists())?;
    let rest = path.strip_prefix(existing).ok()?;
    Some(fscopy::real_path(existing).ok()?.join(rest))
}

/// Copies a directory tree, following symbolic links, as a Bazel output tree has them.
fn copy_tree(source: &Path, destination: &Path) -> Result<(), String> {
    for entry in walkdir::WalkDir::new(source).follow_links(true).sort_by_file_name() {
        let entry = entry.map_err(|error| format!("cannot copy {}: {error}", source.display()))?;
        let target = destination.join(entry.path().strip_prefix(source).unwrap_or(entry.path()));
        let copied = if entry.file_type().is_dir() {
            fs::create_dir_all(&target)
        } else {
            copy_file(entry.path(), &target)
        };
        copied.map_err(|error| format!("cannot copy {}: {error}", entry.path().display()))?;
    }
    Ok(())
}

/// Copies the content of a file into a new file. The copy is writable, though a Bazel output is not, so the next run
/// can replace it.
fn copy_file(source: &Path, destination: &Path) -> io::Result<()> {
    io::copy(&mut fs::File::open(source)?, &mut fs::File::create(destination)?).map(drop)
}

#[cfg(test)]
mod tests;
