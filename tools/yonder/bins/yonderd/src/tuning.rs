//! The durations that a process test shortens through the environment. Each variable holds milliseconds. `API.md`
//! lists them. Without a variable, the agent uses the value of the protocol.

use std::time::Duration;

use yonder_link::proto::HEARTBEAT_SECONDS;

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

fn from_env(name: &str, default: Duration) -> Duration {
    std::env::var(name)
        .ok()
        .and_then(|value| value.parse().ok())
        .map_or(default, Duration::from_millis)
}
