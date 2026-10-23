//! The environment and the limits of the prepare steps and the command of a run.
//!
//! The environment is the one of the agent, then the login environment of the user on top, then the variables of the
//! run (`YONDER_RUN`, `YONDER_HOLDER`, `YONDER_COMMIT`, `YONDER_SLOT`), then `[env]` of `.yonder.toml`. The open-files
//! limit goes up to [`OPEN_FILES`], because launchd starts a job with 256: too few for a parallel test run.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::io;
use std::net::{Ipv4Addr, Ipv6Addr, SocketAddr, TcpListener, TcpStream};
use std::path::Path;
use std::time::Duration;

use yonder_link::proto::RunSpec;

use crate::config::EnvValue;
use crate::home::Home;
use crate::refused::{AGENT_ERROR, Refusal, refused};

/// The open-files limit of a run, or the hard limit when it is lower.
pub(crate) const OPEN_FILES: u64 = 65_536;

/// How many ports after the first one a port variable tries.
const PORT_TRIES: u16 = 100;

/// How long a probe of a port waits for a connection.
const PORT_PROBE: Duration = Duration::from_millis(200);

/// The variables of a run on top of `base`: the variables of the run, then `[env]` in the order of the names.
pub(crate) fn run_env(
    mut env: BTreeMap<OsString, OsString>,
    config: &[(String, EnvValue)],
    home: &Home,
    spec: &RunSpec,
    slot: &Path,
) -> Result<BTreeMap<OsString, OsString>, Refusal> {
    let number = slot_number(slot);
    for (name, value) in [
        ("YONDER_RUN", spec.run.as_str()),
        ("YONDER_HOLDER", spec.holder.as_str()),
        ("YONDER_COMMIT", spec.commit.as_str()),
        ("YONDER_SLOT", &number.to_string()),
    ] {
        env.insert(OsString::from(name), OsString::from(value));
    }
    for (name, value) in config {
        let key = OsString::from(name);
        match value {
            EnvValue::Text(text) => {
                env.insert(key, OsString::from(text));
            }
            EnvValue::List { prepend, append } => {
                let expand = |entry: &String| list_entry(entry, home, slot);
                let inherited = env.get(&key).filter(|value| !value.is_empty()).cloned();
                let entries: Vec<OsString> = prepend
                    .iter()
                    .map(expand)
                    .chain(inherited)
                    .chain(append.iter().map(expand))
                    .collect();
                env.insert(key, join(&entries));
            }
            EnvValue::Port(base) => {
                let first = base.saturating_add(number);
                let port = free_port(first).ok_or_else(|| {
                    refused(
                        AGENT_ERROR,
                        format!(
                            "env.{name} of .yonder.toml: no free TCP port from {first} to {}",
                            first.saturating_add(PORT_TRIES - 1)
                        ),
                    )
                })?;
                env.insert(key, OsString::from(port.to_string()));
            }
            EnvValue::Unset => {
                env.remove(&key);
            }
        }
    }
    Ok(env)
}

/// One entry of a list variable: `~/` is the home directory, an absolute path stays, and a relative path is below the
/// slot root, such as `node_modules/.bin`.
fn list_entry(entry: &str, home: &Home, slot: &Path) -> OsString {
    if entry == "~" || entry.starts_with("~/") || Path::new(entry).is_absolute() {
        home.expand(entry).into_os_string()
    } else {
        slot.join(entry).into_os_string()
    }
}

fn join(entries: &[OsString]) -> OsString {
    let mut joined = OsString::new();
    for (index, entry) in entries.iter().enumerate() {
        if index > 0 {
            joined.push(":");
        }
        joined.push(entry);
    }
    joined
}

/// The number of a slot: the name of its parent directory when that is a number, as in the pool of `bd lease`
/// (`<pool>/<n>/<repo>`), else a number below 100 from the path.
pub(crate) fn slot_number(slot: &Path) -> u16 {
    if let Some(number) = slot
        .parent()
        .and_then(Path::file_name)
        .and_then(|name| name.to_str())
        .and_then(|name| name.parse::<u16>().ok())
        .filter(|number| *number < 1000)
    {
        return number;
    }
    let hash = slot.to_string_lossy().bytes().fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
        (hash ^ u64::from(byte)).wrapping_mul(0x0100_0000_01b3)
    });
    u16::try_from(hash % 100).unwrap_or(0)
}

/// The first free TCP port from `first`, of [`PORT_TRIES`] ports.
pub(crate) fn free_port(first: u16) -> Option<u16> {
    (0..PORT_TRIES)
        .filter_map(|offset| first.checked_add(offset))
        .find(|port| *port != 0 && is_free(*port))
}

/// Whether nothing listens on the TCP port of the loopback addresses and this process can bind it.
fn is_free(port: u16) -> bool {
    let answers = |address: SocketAddr| TcpStream::connect_timeout(&address, PORT_PROBE).is_ok();
    TcpListener::bind((Ipv4Addr::LOCALHOST, port)).is_ok()
        && !answers(SocketAddr::from((Ipv4Addr::LOCALHOST, port)))
        && !answers(SocketAddr::from((Ipv6Addr::LOCALHOST, port)))
}

/// Raises the soft open-files limit of this process to [`OPEN_FILES`], or to the hard limit when it is lower. The
/// children of the process inherit it. Answers the soft limit after the call.
pub(crate) fn raise_open_files() -> io::Result<u64> {
    let mut limit = libc::rlimit { rlim_cur: 0, rlim_max: 0 };
    // SAFETY: getrlimit writes the struct that it gets, which lives for the call.
    if unsafe { libc::getrlimit(libc::RLIMIT_NOFILE, &raw mut limit) } != 0 {
        return Err(io::Error::last_os_error());
    }
    let target = OPEN_FILES.min(limit.rlim_max).min(max_files_per_process());
    if limit.rlim_cur >= target {
        return Ok(limit.rlim_cur);
    }
    limit.rlim_cur = target;
    // SAFETY: setrlimit reads the struct that it gets, which lives for the call.
    if unsafe { libc::setrlimit(libc::RLIMIT_NOFILE, &raw const limit) } != 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(target)
}

/// `kern.maxfilesperproc` of macOS, above which `setrlimit` refuses a soft limit.
#[cfg(target_os = "macos")]
fn max_files_per_process() -> u64 {
    let mut value: libc::c_int = 0;
    let mut size = size_of::<libc::c_int>();
    // SAFETY: the name is NUL-terminated, and the value and its size live for the call.
    let result = unsafe {
        libc::sysctlbyname(
            c"kern.maxfilesperproc".as_ptr(),
            (&raw mut value).cast(),
            &raw mut size,
            std::ptr::null_mut(),
            0,
        )
    };
    if result == 0 && value > 0 {
        u64::try_from(value).unwrap_or(u64::MAX)
    } else {
        u64::MAX
    }
}

/// Linux has no limit beside the hard one.
#[cfg(not(target_os = "macos"))]
const fn max_files_per_process() -> u64 {
    u64::MAX
}

/// The load average of the last minute, or `None` when the system does not tell.
pub(crate) fn load() -> Option<f64> {
    let mut loads = [0f64; 3];
    // SAFETY: getloadavg writes at most the given count into the array, which lives for the call.
    let count = unsafe { libc::getloadavg(loads.as_mut_ptr(), 3) };
    (count >= 1).then_some(loads[0])
}

#[cfg(test)]
mod tests;
