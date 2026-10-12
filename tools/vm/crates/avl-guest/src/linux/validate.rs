//! `validate-guest`: proving a Linux worker can run a UI lane, before a lane discovers it cannot.
//!
//! Runs as root on every boot of a Docker worker, after the agent install. Every check exists because its absence was
//! quiet: a worker whose C library is not the one the JBR is built against, whose X server never came up, whose
//! window manager never registered, or whose runtime cannot resolve one shared object still accepts a run. The
//! display failure surfaces minutes later as an IDE that cannot open a window, and the missing `.so` as one SEVERE
//! `Failed to preload Skiko` line in `idea.log` that nobody reads while the lane goes on measuring nothing.
//!
//! # What it does not hold
//!
//! **No package list and no soname-to-package mapping.** The controller's `GUEST_PACKAGES` does not arrive here. This
//! verb reads what the guest *has*: the C library, the display, and the shared objects under the staged runtime
//! root. A package added to that list is covered here without being named here twice.
//!
//! **No Node check.** The Docker image installs the pinned Node. The image tag is a digest over the Dockerfile, so
//! the pin is the proof.

use serde::Serialize;
use std::collections::{BTreeSet, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

use super::{
    FLUXBOX_PROCESS, LDD_TOOL, LOADERS, SUPPORTING_WM_CHECK_PROPERTY, WAIT_ATTEMPTS, WAIT_INTERVAL, XDPYINFO_TOOL, XPROP_TOOL,
    XVFB_PROCESS, on_display, supporting_window_id, wait_budget,
};
use crate::cli::ValidateGuestArgs;
use crate::reply::AgentRefusalExt;
use crate::reply::{AgentRefusal, quoted};
use crate::shape;
use crate::stage::is_file;
use crate::step::{Runner, Step};

#[cfg(test)]
mod tests;

/// How many unresolved shared objects one refusal names. A guest whose loader configuration is broken answers
/// `not found` once per object in the tree, and a refusal that scrolls the object that mattered off the screen has
/// stopped being a diagnosis; the count of what is not shown is reported beside the ones that are.
const MAX_REPORTED_UNRESOLVED: usize = 10;

/// The marker `ldd` prints for a dependency it could not resolve, matched as a substring of the line: that is the
/// method the guide tells an operator to run by hand, so the check and the manual procedure cannot disagree.
const LDD_NOT_FOUND: &str = "not found";

/// Reads the release out of what the glibc loader answers for `--version`.
///
/// `ld.so (Ubuntu GLIBC 2.39-0ubuntu8.8) stable release version 2.39.` is the real line on the Tart base (Ubuntu
/// 24.04), measured on `air-linux-2`. On the Docker base (Ubuntu 26.04), the line is
/// `ld.so (Ubuntu GLIBC 2.43-2ubuntu2.4) stable release version 2.43.`. `/lib/ld-linux-aarch64.so.1 --version`
/// printed that line in an arm64 container on 2026-09-29.
/// The trailing `version 2.39` is parsed, because the parenthesised part carries the distribution's packaging
/// string and two guests of one glibc release do not agree on it.
fn glibc_release(answer: &str) -> Option<&str> {
    shape::release_after_version(answer)
}

/// Where the glibc loader and the musl one are. A seam, because those are absolute paths under `/lib` and a
/// suite that had to create one of them is a suite nobody may run.
#[derive(Clone, Debug)]
pub(crate) struct LoaderPaths {
    pub glibc: PathBuf,
    pub musl: PathBuf,
}

impl Default for LoaderPaths {
    fn default() -> Self {
        Self {
            glibc: PathBuf::from(LOADERS.glibc),
            musl: PathBuf::from(LOADERS.musl),
        }
    }
}

/// What a passing validation answers: the evidence, not just the verdict.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct GuestReport {
    /// The release the guest's dynamic loader answered: `2.39` on the Tart base and `2.43` on the Docker base.
    /// It is the number a future `GLIBC_2.xx not found` would be read against.
    pub glibc: String,
    pub display: String,
    /// The id of the window that claims `_NET_SUPPORTING_WM_CHECK`. An id that changes between two boots of the
    /// same worker is a window manager that restarted, and nothing else says so.
    pub window_manager: String,
    /// How many passes the two hops took before one answered: 1 on a warm worker, more on a first boot - the only
    /// number that says how close a boot came to the budget.
    pub window_manager_probes: u32,
    /// How many objects under the runtime root `ldd` read.
    pub shared_objects: usize,
    /// The first boot's answer: nothing is staged yet. Present rather than silent, because a sweep of an empty tree
    /// and a sweep that found no problems are the same success and only this field tells them apart.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub note: Option<String>,
}

/// One window-manager observation that is not a live window manager.
enum WindowManager {
    /// A root window with no `_NET_SUPPORTING_WM_CHECK` on it at all: the one state a boot may still grow out of.
    Absent,
    /// A refusal; a stale property is one, and no amount of waiting improves it.
    Refused(AgentRefusal),
}

/// The guest as this verb reads it: two values it was told, the loaders' paths, and the runner. The filesystem
/// sweep walks a real directory, because "which files under this root look like shared objects" is a question a
/// temporary directory answers more honestly than a table would.
pub(crate) struct GuestValidator<R> {
    display: String,
    /// Where staged daemon runtime generations live (`config.Config.GuestRuntimeRoot`).
    runtime_root: PathBuf,
    pub loaders: LoaderPaths,
    pub runner: R,
}

impl<R: Runner> GuestValidator<R> {
    pub(crate) fn new(args: &ValidateGuestArgs, runner: R) -> Self {
        Self {
            display: args.display.clone(),
            runtime_root: args.runtime_root.clone(),
            loaders: LoaderPaths::default(),
            runner,
        }
    }

    /// Answers what the worker is, or the first way in which it cannot run a lane.
    ///
    /// Dependency-first: the C library every other check's own program is linked against, then the display, then
    /// the window manager on it, then the filesystem sweep. **The C library is first because a guest without it
    /// cannot run `xdpyinfo` either**, and before this check such a guest refused `linux_display_not_answering` -
    /// naming Xvfb for a fault in the loader.
    pub(crate) fn validate(&mut self) -> Result<GuestReport, AgentRefusal> {
        let glibc = self.check_glibc()?;
        // Not "the unit is active": an Xvfb that is still starting and one that died on a bad `-screen` are both
        // `active` for a moment, and only a client connecting tells them apart.
        let probe = on_display(Step::new([XDPYINFO_TOOL]).silent(), &self.display);
        self.runner.run(&probe).map_err(|error| {
            AgentRefusal::refused(
                "linux_display_not_answering",
                format!(
                    "the X display {} does not answer {XDPYINFO_TOOL}, so {XVFB_PROCESS} is not serving it: {error:#}",
                    self.display
                ),
            )
        })?;
        let (window_manager, window_manager_probes) = self.await_window_manager()?;
        let (shared_objects, note) = self.check_shared_objects()?;
        Ok(GuestReport {
            glibc,
            display: self.display.clone(),
            window_manager,
            window_manager_probes,
            shared_objects,
            note,
        })
    }

    /// Makes one pass of the driver's two hops.
    ///
    /// The root's `_NET_SUPPORTING_WM_CHECK` names a window; that window must point the same property back at
    /// itself, which EWMH requires and which tells a live fluxbox from a root property left behind by one that
    /// exited. A worker in that state passes hop one and fails in the lane: the driver sees a registered window
    /// manager, starts none, and every window it tries to raise stays where it was.
    ///
    /// `xprop`'s own failure is not read, and its absence of an answer is: a display that stopped answering, a
    /// property that is not set and an `xprop` that exited are all "no window manager is registered".
    fn probe_window_manager(&mut self) -> Result<String, WindowManager> {
        let root = self.runner.run(&self.xprop(&["-root"])).unwrap_or_default();
        let root_window = supporting_window_id(&root);
        if root_window.is_empty() {
            return Err(WindowManager::Absent);
        }
        let answered = self.runner.run(&self.xprop(&["-id", &root_window])).unwrap_or_default();
        let self_window = supporting_window_id(&answered);
        if self_window != root_window {
            return Err(WindowManager::Refused(AgentRefusal::refused(
                "linux_window_manager_stale",
                format!(
                    "the window manager on {} is stale: the root names {root_window} and that window answers {} \
                     for its own {SUPPORTING_WM_CHECK_PROPERTY} instead of pointing at itself, which is a property \
                     left behind by a window manager that has exited - the driver would see a registered window \
                     manager, start none, and be unable to raise or resize a window",
                    self.display,
                    quoted(&self_window)
                ),
            )));
        }
        Ok(root_window)
    }

    /// One `xprop` hop, told the display twice - as `-display` and as `DISPLAY` - which is what
    /// `XorgWindowManagerHandler` passes. Captured: an `xprop` that finds nothing writes `not found.` to stdout.
    fn xprop(&self, target: &[&str]) -> Step {
        let argv =
            std::iter::once(XPROP_TOOL)
                .chain(target.iter().copied())
                .chain(["-display", &self.display, SUPPORTING_WM_CHECK_PROPERTY]);
        on_display(Step::new(argv).capture(), &self.display)
    }

    /// Waits for a window manager to register, rather than asking once and refusing.
    ///
    /// A window manager starts a moment after the X server, so a first boot can find the display with no window
    /// manager yet. On a Tart Linux worker on 2026-08-25, one probe refused `linux_window_manager_missing` on a guest
    /// that was healthy thirty seconds later.
    ///
    /// A stale property leaves on the pass that sees it, unwaited: only an absent one is a state a later probe can
    /// improve on, and waiting a stale one out would turn the one real fault this check sees into a timeout.
    fn await_window_manager(&mut self) -> Result<(String, u32), AgentRefusal> {
        for attempt in 1..=WAIT_ATTEMPTS {
            match self.probe_window_manager() {
                Ok(window) => return Ok((window, attempt)),
                Err(WindowManager::Refused(refusal)) => return Err(refusal),
                Err(WindowManager::Absent) => {}
            }
            if attempt < WAIT_ATTEMPTS {
                self.runner.pause(WAIT_INTERVAL);
            }
        }
        Err(AgentRefusal::refused(
            "linux_window_manager_missing",
            format!(
                "no window manager is registered on {} after {} of waiting: the root window has no \
                 {SUPPORTING_WM_CHECK_PROPERTY}, so {FLUXBOX_PROCESS} is not running or exited without ever taking \
                 the display",
                self.display,
                wait_budget()
            ),
        ))
    }

    /// Proves this guest's C library is the glibc every binary the lane stages is built against.
    ///
    /// The JBR is a *glibc* build of the guest's architecture, and it does not run on a musl guest.
    /// Two observations and one repair: the loader the JBR's `PT_INTERP` names must be there, and it must answer
    /// its own `--version`. The musl loader is statted, never run, so a musl guest hears the word musl rather than
    /// a usage block.
    fn check_glibc(&mut self) -> Result<String, AgentRefusal> {
        const REPAIR: &str = " A worker needs a glibc base image. The pinned DOCKER_BASE_IMAGE is one.";
        let refused = |message: String| AgentRefusal::refused("linux_glibc_missing", message + REPAIR);
        let (glibc, musl) = (self.loaders.glibc.display(), self.loaders.musl.display());
        if !is_file(&self.loaders.glibc) {
            if is_file(&self.loaders.musl) {
                return Err(refused(format!(
                    "this guest is a musl guest: {glibc} is absent and {musl} is present. The JBR this lane \
                     stages is a {} glibc build, and it does not run here.",
                    LOADERS.jbr_platform
                )));
            }
            return Err(refused(format!(
                "{glibc} is not on this guest. It is the interpreter the JBR's own ELF header names, so the \
                 kernel refuses to load the daemon's java before one instruction of it runs."
            )));
        }
        let loader = self.loaders.glibc.to_string_lossy().into_owned();
        let answered = self
            .runner
            .run(&Step::new([loader.as_str(), "--version"]).capture())
            .map_err(|error| {
                refused(format!(
                    "{glibc} is on this guest and does not run: {error:#}. A file of that name that is not the \
                     glibc loader leaves every binary this lane stages unloadable."
                ))
            })?;
        match glibc_release(&answered) {
            Some(release) => Ok(release.to_owned()),
            None => Err(refused(format!(
                "{glibc} ran and reported no release of its own. So it is not the glibc loader the JBR is built \
                 against."
            ))),
        }
    }

    /// Refuses a guest that cannot resolve something the staged runtime links against, by the method that found
    /// the gap: `ldd` over the runtime's *own* shared objects, reading the lines it marks `not found`. Measured on
    /// `air-linux-1` on 2026-08-19: `libEGL.so.1` is what `libskiko-linux-arm64.so` needs, and five more are what
    /// `libcef.so` and `libjcef.so` need.
    ///
    /// The runtime root and not the IDE root: the IDE root is empty in production, which made the check inert
    /// until 2026-08-27. One root rather than `/`, which would spend minutes against libraries no lane loads.
    fn check_shared_objects(&mut self) -> Result<(usize, Option<String>), AgentRefusal> {
        let libraries = find_shared_objects(&self.runtime_root).map_err(|error| {
            AgentRefusal::refused(
                "linux_runtime_root_unreadable",
                format!(
                    "the staged runtime root {} could not be swept for shared objects: {error}",
                    self.runtime_root.display()
                ),
            )
        })?;
        let shipped = shipped_sonames(&libraries);
        let mut unresolved = Vec::new();
        for library in &libraries {
            // `ldd`'s own exit status is deliberately not read: it fails for a file that is not an ELF object, and
            // a `.so`-named file that is not one is not this check's business. Only a resolvable object produces
            // the `not found` lines this reads.
            let path = library.to_string_lossy();
            let output = self.runner.run(&Step::new([LDD_TOOL, &path]).capture()).unwrap_or_default();
            let missing: Vec<String> = unresolved_sonames(&output)
                .into_iter()
                .filter(|soname| !shipped.contains(soname))
                .collect();
            if !missing.is_empty() {
                unresolved.push(format!("{path} needs {}", missing.join(" ")));
            }
        }
        if !unresolved.is_empty() {
            let hidden = unresolved.len().saturating_sub(MAX_REPORTED_UNRESOLVED);
            let suffix = if hidden > 0 {
                format!(" (and {hidden} more)")
            } else {
                String::new()
            };
            unresolved.truncate(MAX_REPORTED_UNRESOLVED);
            return Err(AgentRefusal::refused(
                "linux_shared_object_unresolved",
                format!(
                    "shared objects the staged runtime links against are not found in this guest: {}{suffix}; add \
                     the package that carries them to GUEST_PACKAGES in crates/avl-vm/src/worker/docker.rs",
                    unresolved.join("; ")
                ),
            ));
        }
        if libraries.is_empty() {
            return Ok((
                0,
                Some(format!(
                    "no shared objects under {} yet, so nothing is staged on this worker to check ldd against",
                    self.runtime_root.display()
                )),
            ));
        }
        Ok((libraries.len(), None))
    }
}

/// Every soname the swept tree carries a file for, by base name.
///
/// **This makes the sweep usable against the real runtime root.** Measured on `air-linux-2` on 2026-08-27: of 96
/// shared objects, 54 report exactly one `not found`, always `libjvm.so` - which is in the tree at
/// `jbr/lib/server/libjvm.so`. The JVM does not resolve it through the loader's search path either: the launcher
/// loads it from `server/` itself. So a soname the tree ships is not a base-image gap. Tree-wide rather than per
/// generation, deliberately the looser rule: every generation is a copy of one JBR.
///
/// What this cannot mask is the six sonames the check was written for: none is a file name in the tree (the JBR's
/// SwiftShader object is the *unversioned* `libEGL.so`, while `libegl1` supplies `libEGL.so.1`).
fn shipped_sonames(libraries: &[PathBuf]) -> HashSet<String> {
    libraries
        .iter()
        .filter_map(|library| library.file_name())
        .map(|name| name.to_string_lossy().into_owned())
        .collect()
}

/// Lists the shared objects under the runtime root (`*.so` and `*.so.*`, regular files only), in a stable order.
///
/// An absent root is not an error: a worker is provisioned before anything is staged, so on a first boot the empty
/// answer is the correct one. A root that exists and cannot be *read* is an error, which is the one distinction the
/// script's `2>/dev/null || true` threw away. Regular files only: the staged tree hardlinks and symlinks nothing,
/// so a symlink would be `ldd`'d twice under two names. Sorted whole, so two boots of the same broken worker name
/// the same objects under the cap.
pub(crate) fn find_shared_objects(root: &Path) -> walkdir::Result<Vec<PathBuf>> {
    if !fs::metadata(root).is_ok_and(|info| info.is_dir()) {
        return Ok(Vec::new());
    }
    let mut libraries = Vec::new();
    for entry in WalkDir::new(root).follow_links(false) {
        let entry = entry?;
        if !entry.file_type().is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy();
        if name.ends_with(".so") || name.contains(".so.") {
            libraries.push(entry.into_path());
        }
    }
    libraries.sort();
    Ok(libraries)
}

/// The dependencies `ldd` could not find, deduplicated and ordered (the script's `awk '/not found/ { print $1 }' |
/// sort -u`). The first field is the soname, the half an operator can search a package index for.
pub(crate) fn unresolved_sonames(output: &str) -> Vec<String> {
    output
        .lines()
        .filter(|line| line.contains(LDD_NOT_FOUND))
        .filter_map(|line| line.split_whitespace().next())
        .map(str::to_owned)
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}
