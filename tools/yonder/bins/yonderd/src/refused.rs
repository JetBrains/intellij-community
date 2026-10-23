//! The refusals of the agent. A request gets the code as [`Reply::Refused`], and a command prints it on stderr. The
//! codes of the requests are the ones of `crates/yonder-link/API.md`. `API.md` of this crate adds the codes of the
//! commands.

use yonder_link::proto::Reply;

/// A refusal of the agent. Each command exits with [`EXIT_REFUSED`] when it refuses.
pub(crate) type Refusal = refusal::Refusal<u8>;

/// The exit code of a command that refuses.
pub(crate) const EXIT_REFUSED: u8 = 1;

pub(crate) const SCHEMA: &str = "schema";
pub(crate) const BAD_REQUEST: &str = "bad_request";
pub(crate) const BAD_RUN_ID: &str = "bad_run_id";
pub(crate) const RUN_EXISTS: &str = "run_exists";
pub(crate) const UNKNOWN_RUN: &str = "unknown_run";
pub(crate) const REPO_MISSING: &str = "repo_missing";
pub(crate) const PACK_FAILED: &str = "pack_failed";
pub(crate) const PACK_MISSING: &str = "pack_missing";
pub(crate) const LOCK_TIMEOUT: &str = "lock_timeout";
pub(crate) const LEASE_FAILED: &str = "lease_failed";
pub(crate) const CHECKOUT_FAILED: &str = "checkout_failed";
pub(crate) const SPAWN_FAILED: &str = "spawn_failed";
pub(crate) const AGENT_ERROR: &str = "agent_error";
/// `.yonder.toml` of the commit cannot be read, or it has an unknown key or a value of the wrong shape.
pub(crate) const BAD_CONFIG: &str = "bad_config";
/// A prepare step of `.yonder.toml` failed, so the command did not run.
pub(crate) const PREPARE_FAILED: &str = "prepare_failed";

/// `setup` cannot start the agent under launchd.
pub(crate) const LAUNCHD_FAILED: &str = "launchd_failed";

/// A refusal with the code `code`.
pub(crate) fn refused(code: &'static str, message: impl Into<String>) -> Refusal {
    Refusal::new(code, EXIT_REFUSED, message)
}

/// The reply that carries `refusal` to a client.
pub(crate) fn reply(refusal: &Refusal) -> Reply {
    Reply::refused(&refusal.code, refusal.message.clone())
}
