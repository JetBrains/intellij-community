use std::fs;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

use nix::errno::Errno;
use pretty_assertions::assert_eq;

use super::*;

/// Writes the shell script `body` as the program `name` in `directory`, and answers its path.
fn program(directory: &Path, name: &str, body: &str) -> PathBuf {
    let path = directory.join(name);
    fs::write(&path, format!("#!/bin/sh\n{body}\n")).unwrap();
    fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).unwrap();
    path
}

/// Waits at most 10 s for `file` to have a last line, and answers the content.
fn written(file: &Path) -> String {
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        if let Ok(content) = fs::read_to_string(file)
            && content.ends_with('\n')
        {
            return content;
        }
        assert!(Instant::now() < deadline, "{} has no content", file.display());
        thread::sleep(Duration::from_millis(20));
    }
}

/// Whether the process `pid` is gone. A reaped child no longer answers a signal.
fn is_gone(pid: u32) -> bool {
    signal::kill(Pid::from_raw(i32::try_from(pid).unwrap()), None) == Err(Errno::ESRCH)
}

// The run owns its display when the host has Xvfb, on the PATH or in a usual directory. An empty entry of the PATH is no
// directory. Without Xvfb, the run keeps the display of the build. The warning names Xvfb and the issue, in the run
// stream and as a TeamCity message.
#[test]
fn the_run_owns_its_display_only_with_xvfb() {
    let only = |programs: &'static [&'static str]| move |path: &Path| programs.iter().any(|program| path == Path::new(program));

    assert_eq!(
        find_xvfb(Some(":/opt/x/bin"), ":0", &only(&["/opt/x/bin/Xvfb", "/usr/bin/Xvfb"])),
        Ok(PathBuf::from("/opt/x/bin/Xvfb"))
    );
    assert_eq!(
        find_xvfb(None, ":0", &only(&["/usr/local/bin/Xvfb"])),
        Ok(PathBuf::from("/usr/local/bin/Xvfb"))
    );

    let warning = find_xvfb(Some(""), ":0", &only(&["Xvfb"])).unwrap_err();
    assert_eq!(
        warning,
        "WARN: lane-run records the display :0 of the build, with the desktop of the host: Xvfb is not on the PATH nor \
         in /usr/bin, /usr/local/bin, and the display of the run needs it (IJAI-4779)"
    );
    assert_eq!(
        warning_message("WARN: no 'Xvfb'"),
        "##teamcity[message text='WARN: no |'Xvfb|'' status='WARNING']"
    );
}

// Xvfb -displayfd writes the number of the display and a line end. Anything else names no display.
#[test]
fn the_displayfd_line_names_the_display() {
    assert_eq!(display_of("93\n").as_deref(), Some(":93"));
    assert_eq!(display_of("0").as_deref(), Some(":0"));
    assert_eq!(display_of(""), None);
    assert_eq!(display_of(":93\n"), None);
    assert_eq!(display_of("9 3"), None);
}

// The display starts Xvfb with -displayfd at the screen of a lane guest, and answers the display that Xvfb names. A
// drop stops Xvfb.
#[test]
fn the_display_starts_xvfb_on_a_free_display_and_its_drop_stops_it() {
    let root = tempfile::TempDir::new().unwrap();
    let arguments = root.path().join("xvfb-args");
    let xvfb = program(
        root.path(),
        "Xvfb",
        &format!("echo \"$@\" > '{}'\necho 93\nexec sleep 600", arguments.display()),
    );

    let display = OwnDisplay::start(&xvfb, root.path()).unwrap();

    assert_eq!(display.display(), ":93");
    assert_eq!(written(&arguments), "-displayfd 1 -ac -nolisten tcp -screen 0 1920x1080x24\n");
    let pid = display.xvfb.id();
    drop(display);
    assert!(is_gone(pid), "Xvfb runs after the drop");
}

// An Xvfb that exits before it names a display, or that writes something else, is a refusal that names its log.
#[test]
fn an_xvfb_that_names_no_display_is_refused() {
    let root = tempfile::TempDir::new().unwrap();

    for (body, reason) in [
        (
            "echo 'no screens' >&2\nexit 1",
            "Xvfb named no display: it exited before it took clients",
        ),
        (
            "echo ready\nexec sleep 600",
            "Xvfb named no display: it wrote \"ready\" instead of a display number",
        ),
    ] {
        let xvfb = program(root.path(), "Xvfb", body);

        let refusal = OwnDisplay::start(&xvfb, root.path()).unwrap_err();

        assert_eq!(refusal.code, DISPLAY_CODE);
        assert_eq!(
            refusal.message,
            format!("{reason}; its log is {}", root.path().join("xvfb.log").display())
        );
    }
}
