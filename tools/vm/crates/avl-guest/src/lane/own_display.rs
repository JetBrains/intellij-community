//! The X display that `lane-run` owns on a TeamCity build on Linux: an Xvfb at the screen of a lane guest, with the
//! window manager of [`super::window_manager`] on it.
//!
//! The display of the build is the desktop of the agent, with its wallpaper, its dock and its icons. The recorder reads
//! the whole screen, so each still and each video of the build would show that desktop around the IDE frame. The agent
//! image has Xvfb, and the window manager comes with `lane-run`, so the run needs no other program of the host.
//!
//! When Xvfb is not on the host, the run keeps the display of the build, and a warning says why.

use std::fs::{self, File};
use std::io::{BufRead, BufReader};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdout, Command, Stdio};
use std::sync::mpsc;
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use avl_junit::teamcity;
use nix::sys::signal::{self, Signal};
use nix::unistd::Pid;

use super::window_manager::{NONREPARENTING_VARIABLE, WM_NAME};
use crate::linux::validate::LANE_SCREEN;
use crate::linux::{DISPLAY_VARIABLE, XVFB_PROCESS};
use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The refusal code of a display of the run that does not start.
pub(crate) const DISPLAY_CODE: &str = "lane_run_display";
/// How long Xvfb may take to name its display.
const READY_TIMEOUT: Duration = Duration::from_mins(1);
/// How long a program of the display may take to end after a SIGTERM, before it gets a SIGKILL.
const STOP_GRACE: Duration = Duration::from_secs(5);
/// How often [`stop`] asks whether a program ended.
const STOP_POLL: Duration = Duration::from_millis(50);
/// Where lane-run looks for Xvfb after the PATH. A Bazel test can have a PATH without it.
const FALLBACK_DIRS: [&str; 2] = ["/usr/bin", "/usr/local/bin"];
/// The directory of the run directory that holds the log of Xvfb.
pub(crate) const DISPLAY_DIR: &str = "display";

/// The Xvfb of a run whose build owns the screen `build_display`: the first one on `path`, the PATH of the host, then in
/// [`FALLBACK_DIRS`]. `is_program` tells a file that this process may run. Without one, the answer is the warning that
/// the run records the display of the build.
pub(crate) fn find_xvfb(path: Option<&str>, build_display: &str, is_program: &dyn Fn(&Path) -> bool) -> Result<PathBuf, String> {
    path.iter()
        .flat_map(std::env::split_paths)
        .filter(|dir| !dir.as_os_str().is_empty())
        .chain(FALLBACK_DIRS.iter().map(PathBuf::from))
        .map(|dir| dir.join(XVFB_PROCESS))
        .find(|candidate| is_program(candidate))
        .ok_or_else(|| {
            format!(
                "WARN: lane-run records the display {build_display} of the build, with the desktop of the host: \
                 {XVFB_PROCESS} is not on the PATH nor in {}, and the display of the run needs it (IJAI-4779)",
                FALLBACK_DIRS.join(", ")
            )
        })
}

/// The TeamCity message of the warning `note`, so the build log shows a run that records the desktop of the host.
pub(crate) fn warning_message(note: &str) -> String {
    teamcity::message("message", &[("text", note), ("status", "WARNING")], None)
}

/// Whether `path` is a file that this process may run.
pub(crate) fn is_program(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|metadata| metadata.is_file() && metadata.permissions().mode() & 0o111 != 0)
}

/// The display that a line of `Xvfb -displayfd` names, such as `:93` for `93`.
pub(crate) fn display_of(line: &str) -> Option<String> {
    let number = line.trim();
    (!number.is_empty() && number.bytes().all(|byte| byte.is_ascii_digit())).then(|| format!(":{number}"))
}

/// An Xvfb and the window manager on it, which the run owns. A drop stops Xvfb, which ends the connection of the window
/// manager, and then waits for the thread of the window manager.
#[derive(Debug)]
pub(crate) struct OwnDisplay {
    display: String,
    xvfb: Child,
    /// The pipe of `-displayfd`. It stays open while Xvfb runs, so a late write to it does not end Xvfb with a SIGPIPE.
    _display_fd: ChildStdout,
    window_manager: Option<JoinHandle<()>>,
}

impl OwnDisplay {
    /// Starts `xvfb` on a free display at [`LANE_SCREEN`]. Xvfb picks the display with `-displayfd` and names it when
    /// it takes clients. `directory` gets the log of Xvfb.
    ///
    /// On Linux Xvfb gets a SIGTERM when `lane-run` ends, so a hard stop that exits the process leaves no display behind.
    pub(crate) fn start(xvfb: &Path, directory: &Path) -> Result<Self, AgentRefusal> {
        let failed = |message: String| AgentRefusal::failure(DISPLAY_CODE, message);
        let log = directory.join("xvfb.log");
        let xvfb_log = File::create(&log).map_err(|error| failed(format!("cannot create {}: {error}", log.display())))?;
        let (width, height) = LANE_SCREEN;
        let mut command = Command::new(xvfb);
        command
            .args([
                "-displayfd",
                "1",
                "-ac",
                "-nolisten",
                "tcp",
                "-screen",
                "0",
                &format!("{width}x{height}x24"),
            ])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(xvfb_log);
        end_with_parent(&mut command);
        let mut xvfb_child = command
            .spawn()
            .map_err(|error| failed(format!("cannot start {}: {error}", xvfb.display())))?;
        let Some(stdout) = xvfb_child.stdout.take() else {
            stop(&mut xvfb_child);
            return Err(failed(format!("{XVFB_PROCESS} has no pipe for -displayfd")));
        };
        match read_display(stdout) {
            Ok((display, display_fd)) => Ok(Self {
                display,
                xvfb: xvfb_child,
                _display_fd: display_fd,
                window_manager: None,
            }),
            Err(reason) => {
                stop(&mut xvfb_child);
                Err(failed(format!(
                    "{XVFB_PROCESS} named no display: {reason}; its log is {}",
                    log.display()
                )))
            }
        }
    }

    /// The display, such as `:93`.
    pub(crate) fn display(&self) -> &str {
        &self.display
    }
}

impl Drop for OwnDisplay {
    fn drop(&mut self) {
        stop(&mut self.xvfb);
        if let Some(window_manager) = self.window_manager.take() {
            let _ = window_manager.join();
        }
    }
}

/// Reads the display that Xvfb writes to `stdout`, at most for [`READY_TIMEOUT`], and answers it with the pipe. The read
/// thread has ended when this returns, so the caller can change the environment of the process.
fn read_display(stdout: ChildStdout) -> Result<(String, ChildStdout), String> {
    let (sender, receiver) = mpsc::channel();
    let reader = thread::spawn(move || {
        let mut reader = BufReader::new(stdout);
        let mut line = String::new();
        let read = reader.read_line(&mut line).map(|_| line);
        let _ = sender.send(read);
        reader.into_inner()
    });
    let Ok(read) = receiver.recv_timeout(READY_TIMEOUT) else {
        // The caller stops Xvfb, which closes the pipe and ends the read. Until then the thread blocks, so it is not
        // joined here.
        return Err(format!("it wrote nothing in {} s", READY_TIMEOUT.as_secs()));
    };
    let pipe = reader.join().map_err(|_panic| "the read of -displayfd panicked".to_owned())?;
    let line = read.map_err(|error| format!("the read of -displayfd fails: {error}"))?;
    let display = display_of(&line).ok_or_else(|| {
        if line.is_empty() {
            "it exited before it took clients".to_owned()
        } else {
            format!("it wrote {:?} instead of a display number", line.trim())
        }
    })?;
    Ok((display, pipe))
}

/// Gives a run whose build owns the screen `build_display` a display of its own, with the Xvfb that [`find_xvfb`] finds
/// on the PATH `path`, in the directory [`DISPLAY_DIR`] of the run directory `root`. It sets `DISPLAY` of this process to
/// that display and [`NONREPARENTING_VARIABLE`] for the JDK, so the lane IDE and the recorder use the display. Then it
/// starts the window manager, and it answers the display with a note. A host without Xvfb keeps the display of the
/// build: the answer is then `None` with the warning of [`find_xvfb`].
///
/// lane-run calls it on its main thread before it starts any other thread, because it changes the environment of the
/// process.
pub(crate) fn take(root: &Path, build_display: &str, path: Option<&str>) -> Result<(Option<OwnDisplay>, String), AgentRefusal> {
    let xvfb = match find_xvfb(path, build_display, &is_program) {
        Ok(xvfb) => xvfb,
        Err(warning) => return Ok((None, warning)),
    };
    let directory = root.join(DISPLAY_DIR);
    fs::create_dir_all(&directory)
        .map_err(|error| AgentRefusal::failure(DISPLAY_CODE, format!("cannot create {}: {error}", directory.display())))?;
    let mut display = OwnDisplay::start(&xvfb, &directory)?;
    // SAFETY: lane-run runs this on its main thread before the monitor, the daemon or the run start a thread, the read
    // thread of `-displayfd` has ended, and the window manager starts its thread below. So no other thread reads or
    // writes the environment now.
    unsafe {
        std::env::set_var(DISPLAY_VARIABLE, display.display());
    }
    // SAFETY: as above, no other thread runs yet.
    unsafe {
        std::env::set_var(NONREPARENTING_VARIABLE, "1");
    }
    let manager = start_window_manager(display.display()).map_err(|reason| {
        AgentRefusal::failure(
            DISPLAY_CODE,
            format!("the display {} has no window manager: {reason}", display.display()),
        )
    })?;
    display.window_manager = Some(manager);
    let (width, height) = LANE_SCREEN;
    let note = format!(
        "lane-run records its own display {}, an {XVFB_PROCESS} of {width}x{height} with the window manager {WM_NAME}, \
         and not the display {build_display} of the build",
        display.display()
    );
    Ok((Some(display), note))
}

#[cfg(target_os = "linux")]
fn start_window_manager(display: &str) -> Result<JoinHandle<()>, String> {
    super::window_manager::x11::start(display)
}

/// Only Linux has the X11 half of the window manager. Only a build on Linux owns its screen.
#[cfg(not(target_os = "linux"))]
fn start_window_manager(_display: &str) -> Result<JoinHandle<()>, String> {
    Err("the window manager runs on Linux only".to_owned())
}

/// Stops `child` with a SIGTERM, so Xvfb removes its lock file, and with a SIGKILL after [`STOP_GRACE`].
fn stop(child: &mut Child) {
    if matches!(child.try_wait(), Ok(Some(_))) {
        return;
    }
    if let Ok(pid) = i32::try_from(child.id()) {
        let _ = signal::kill(Pid::from_raw(pid), Signal::SIGTERM);
    }
    let deadline = Instant::now() + STOP_GRACE;
    while Instant::now() < deadline {
        if matches!(child.try_wait(), Ok(Some(_))) {
            return;
        }
        thread::sleep(STOP_POLL);
    }
    let _ = child.kill();
    let _ = child.wait();
}

/// Asks Linux to send the child of `command` a SIGTERM when the thread that starts it ends. lane-run starts the display
/// on its main thread, so the signal comes when the process ends, also through `std::process::exit`.
#[cfg(target_os = "linux")]
fn end_with_parent(command: &mut Command) {
    use std::os::unix::process::CommandExt;
    // SAFETY: the closure runs between fork and exec, and calls only prctl(2), which is async-signal-safe and touches no
    // memory of the parent.
    unsafe {
        command.pre_exec(|| nix::sys::prctl::set_pdeathsig(Signal::SIGTERM).map_err(std::io::Error::from));
    }
}

/// Only Linux has a parent-death signal. Only a build on Linux owns its screen, so the run starts no display elsewhere.
#[cfg(not(target_os = "linux"))]
const fn end_with_parent(_command: &mut Command) {}
