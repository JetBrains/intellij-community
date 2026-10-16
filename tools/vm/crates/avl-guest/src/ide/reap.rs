//! The JCEF helpers that an earlier IDE of a context left behind.
//!
//! A `cef_server` process can outlive the IDE that started it, and it holds the JCEF cache under the system directory
//! of the context. So a preparation kills every `cef_server` whose command line names a path under the context, and
//! no other one. The process table is `/proc/<pid>/cmdline` where the guest has `/proc`, and `ps -axo pid=,command=`
//! on macOS.

use std::fs;
use std::path::Path;
use std::process::{Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

use nix::errno::Errno;
use nix::sys::signal::{Signal, kill};
use nix::unistd::Pid;

#[cfg(test)]
mod tests;

/// The name that marks a JCEF helper in a command line.
const HELPER: &str = "cef_server";

/// How long the reaper waits for the killed helpers to leave the process table.
const EXIT_TIMEOUT: Duration = Duration::from_secs(2);
const POLL_INTERVAL: Duration = Duration::from_millis(50);

/// Kills the JCEF helpers of `context`, waits up to [`EXIT_TIMEOUT`] for them to end, and answers their pids.
pub(crate) fn reap_jcef_helpers(context: &Path) -> Vec<i32> {
    let own = i32::try_from(std::process::id()).unwrap_or_default();
    let pids = jcef_helpers(&process_table(), &context.to_string_lossy(), own);
    for &pid in &pids {
        let _ = kill(Pid::from_raw(pid), Signal::SIGKILL);
    }
    let until = Instant::now() + EXIT_TIMEOUT;
    while pids.iter().any(|&pid| process_exists(pid)) && Instant::now() < until {
        thread::sleep(POLL_INTERVAL);
    }
    pids
}

/// The pids in `table` of the JCEF helpers whose command line names a path under `context`. Never `own`.
pub(crate) fn jcef_helpers(table: &[(i32, String)], context: &str, own: i32) -> Vec<i32> {
    let under_context = format!("{}/", context.trim_end_matches('/'));
    table
        .iter()
        .filter(|(pid, command)| *pid > 0 && *pid != own && command.contains(HELPER) && command.contains(&under_context))
        .map(|(pid, _)| *pid)
        .collect()
}

/// Every process of the guest with its command line, the arguments separated by one space.
fn process_table() -> Vec<(i32, String)> {
    if Path::new("/proc/self/cmdline").is_file() {
        proc_table()
    } else {
        ps_table()
    }
}

fn proc_table() -> Vec<(i32, String)> {
    let Ok(entries) = fs::read_dir("/proc") else {
        return Vec::new();
    };
    entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let pid: i32 = entry.file_name().to_str()?.parse().ok()?;
            let raw = fs::read(entry.path().join("cmdline")).ok()?;
            let command = raw
                .split(|&byte| byte == 0)
                .filter(|argument| !argument.is_empty())
                .map(String::from_utf8_lossy)
                .collect::<Vec<_>>()
                .join(" ");
            Some((pid, command))
        })
        .collect()
}

fn ps_table() -> Vec<(i32, String)> {
    let Ok(output) = Command::new("/bin/ps")
        .args(["-axo", "pid=,command="])
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .output()
    else {
        return Vec::new();
    };
    parse_ps(&String::from_utf8_lossy(&output.stdout))
}

/// The lines of `ps -o pid=,command=`: a pid, white space, and the command line.
pub(crate) fn parse_ps(text: &str) -> Vec<(i32, String)> {
    text.lines()
        .filter_map(|line| {
            let (pid, command) = line.trim_start().split_once(char::is_whitespace)?;
            Some((pid.parse().ok()?, command.trim().to_owned()))
        })
        .collect()
}

/// Whether a process with `pid` exists. EPERM counts as alive: a process of another account exists too.
fn process_exists(pid: i32) -> bool {
    match kill(Pid::from_raw(pid), None) {
        Ok(()) => true,
        Err(error) => error == Errno::EPERM,
    }
}
