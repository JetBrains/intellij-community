//! The trace viewer that `teamcity-publish` puts into its archive: a built one, or one that the step builds in the
//! checkout. The viewer is one file, `index.html`.
//!
//! The build is the docs project's `pnpm run build:viewer`, with the node and the pnpm of `air-trace serve`'s site build
//! ([site_tools]). Before it, `pnpm install --frozen-lockfile` runs in the kernel and in the docs project. The build
//! writes `<checkout>/out/air-trace-viewer`. A viewer there that is newer than its sources ([viewer_sources]) is not built
//! again. The build has a time limit, and its output goes to stderr as it comes.

use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use avl_host_sys::viewer::format_duration;
use tokio_util::sync::CancellationToken;

use crate::serve::{BuildOutput, SiteTools, build_site_with, newer_sources, site_source_dirs, site_tools, tools_text};

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
    /// No viewer, and why.
    Missing(String),
}

/// Why there is no viewer when there is neither `--viewer` nor a checkout.
pub(super) const NO_CHECKOUT: &str = "no --viewer DIR, and no checkout to build one in";
/// The value of `--node` or `--pnpm` that names no tool, as the step before the publish sets it when it finds none.
const NO_TOOL: &str = "none";

/// The node and the pnpm of the viewer build, from `--node` and `--pnpm` or from `AIR_TRACE_NODE` and
/// `AIR_TRACE_PNPM`, and whether the step runs on TeamCity.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Tools {
    pub node: Option<PathBuf>,
    pub pnpm: Option<PathBuf>,
    pub on_team_city: bool,
}

/// Where the viewer of a publish comes from. The directory of `--viewer` is a built viewer. Else the step builds the
/// viewer in the checkout, with the node and the pnpm of `tools`. A tool `none` builds no viewer. Without the two
/// tools, the step asks Bazel for them as `serve` does, except on TeamCity. There the step starts no Bazel, so it
/// builds no viewer.
pub(crate) fn viewer_source(viewer: Option<PathBuf>, checkout: Option<PathBuf>, tools: Tools) -> ViewerSource {
    if let Some(viewer) = viewer {
        return ViewerSource::Built(viewer);
    }
    let Some(checkout) = checkout else {
        return ViewerSource::Missing(NO_CHECKOUT.to_owned());
    };
    let build = |tools| ViewerSource::Build {
        checkout,
        tools,
        timeout: VIEWER_BUILD_TIMEOUT,
    };
    match (tools.node, tools.pnpm) {
        (Some(node), Some(pnpm)) if node.as_os_str() == NO_TOOL || pnpm.as_os_str() == NO_TOOL => {
            ViewerSource::Missing("AIR_TRACE_NODE or AIR_TRACE_PNPM is none, so no node and no pnpm build the viewer".to_owned())
        }
        (Some(node), Some(pnpm)) => build(Some(SiteTools::Pinned { node, pnpm })),
        (None, None) if tools.on_team_city => ViewerSource::Missing(
            "on TeamCity the step starts no Bazel to find node and pnpm; set AIR_TRACE_NODE and AIR_TRACE_PNPM, or pass \
             --viewer DIR"
                .to_owned(),
        ),
        (None, None) => build(None),
        _ => ViewerSource::Missing("only one of AIR_TRACE_NODE and AIR_TRACE_PNPM is set; the two name the tools together".to_owned()),
    }
}

/// The directory of the built viewer, or why there is none. A build says on stderr what it runs, its output, and the
/// time it took.
pub(super) fn viewer_dir(source: &ViewerSource, stderr: &mut dyn Write) -> io::Result<Result<PathBuf, String>> {
    let (checkout, tools, timeout) = match source {
        ViewerSource::Built(dir) => return Ok(Ok(dir.clone())),
        ViewerSource::Missing(reason) => return Ok(Err(reason.clone())),
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
    if !newer_sources(&built.join(VIEWER_PAGE), &viewer_sources(&project, &kernel)) {
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

/// The files that the viewer build reads. These are the sources of the site, the configuration files and the lockfile of
/// the docs project, and the lockfile of the kernel. The UI primitives of the genui catalog count too, because the
/// build takes them through an alias and Tailwind scans them.
fn viewer_sources(project: &Path, kernel: &Path) -> Vec<PathBuf> {
    let mut sources = site_source_dirs(project, Some(kernel));
    let project_files = [
        "site.config.ts",
        "vite.config.ts",
        "vite.viewer.config.ts",
        "scripts/paths.ts",
        "package.json",
        "pnpm-lock.yaml",
    ];
    sources.extend(project_files.map(|file| project.join(file)));
    sources.extend(["package.json", "pnpm-lock.yaml"].map(|file| kernel.join(file)));
    if let Some(air) = project.parent() {
        sources.push(air.join("frontend/genui/jcef/web-bundle/src/catalog/ui"));
    }
    sources
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
                // Also over an existing `node_modules`, which holds the old dependencies after a change of a lockfile. A
                // frozen install of an unchanged lockfile does nothing.
                let installs: Vec<&Path> = [kernel, project]
                    .into_iter()
                    .filter(|dir| dir.join("package.json").is_file())
                    .collect();
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

/// Copies the page of the built viewer into `staging` as its `index.html`. The viewer is one file, with its scripts,
/// styles and fonts inside. Answers the copy, or why there is no page.
pub(super) fn copy_page(viewer: &Path, staging: &Path) -> Result<PathBuf, String> {
    let page = viewer.join(VIEWER_PAGE);
    if !page.is_file() {
        return Err(format!("{} holds no {VIEWER_PAGE}", viewer.display()));
    }
    let copy = staging.join(VIEWER_PAGE);
    fs::create_dir_all(staging)
        .and_then(|()| copy_file(&page, &copy))
        .map_err(|error| format!("cannot copy {}: {error}", page.display()))?;
    Ok(copy)
}

/// Copies the content of a file into a new file. The copy is writable, though a Bazel output is not, so the next run
/// can replace it.
fn copy_file(source: &Path, destination: &Path) -> io::Result<()> {
    io::copy(&mut fs::File::open(source)?, &mut fs::File::create(destination)?).map(drop)
}

#[cfg(test)]
mod tests;
