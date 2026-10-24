//! The durations that a process test shortens through the environment, and the hold of a follow. Each duration
//! variable holds milliseconds. `API.md` lists them. Without a variable, the agent uses the value of the protocol.

use std::path::PathBuf;
use std::time::Duration;

use yonder_link::proto::{HEARTBEAT_SECONDS, PACK_WAIT_SECONDS};

/// How long an interrupted command group has before SIGKILL: `YONDERD_TEST_GRACE_MS`, else 120 s.
pub(crate) fn grace() -> Duration {
    from_env("YONDERD_TEST_GRACE_MS", Duration::from_secs(120))
}

/// The total deadline of a TLS handshake: `YONDERD_TEST_HANDSHAKE_MS`, else 10 s.
pub(crate) fn handshake() -> Duration {
    from_env("YONDERD_TEST_HANDSHAKE_MS", Duration::from_secs(10))
}

/// How often a waiting request or a quiet follow sends a heartbeat: `YONDERD_TEST_HEARTBEAT_MS`, else
/// [`HEARTBEAT_SECONDS`].
pub(crate) fn heartbeat() -> Duration {
    from_env("YONDERD_TEST_HEARTBEAT_MS", Duration::from_secs(HEARTBEAT_SECONDS))
}

/// How long a run waits for the pack of its reference: `YONDERD_TEST_PACK_WAIT_MS`, else [`PACK_WAIT_SECONDS`].
pub(crate) fn pack_wait() -> Duration {
    from_env("YONDERD_TEST_PACK_WAIT_MS", Duration::from_secs(PACK_WAIT_SECONDS))
}

/// A file that holds a follow before each read of the run files while it exists: `YONDERD_TEST_FOLLOW_HOLD`. A test
/// holds the follow thread with it to check that the heartbeats go on.
pub(crate) fn follow_hold() -> Option<PathBuf> {
    std::env::var_os("YONDERD_TEST_FOLLOW_HOLD").map(PathBuf::from)
}

fn from_env(name: &str, default: Duration) -> Duration {
    std::env::var(name)
        .ok()
        .and_then(|value| value.parse().ok())
        .map_or(default, Duration::from_millis)
}
