//! Lanes: selector resolution for the controller, the host Bazel build, the guest launch environment, and the
//! observation commands.
//!
//! What a UI worker is allowed to run and how it is built: lane and selector resolution against `bt`
//! ([`lanes`], [`affected`]), the host-side Bazel build ([`bazel`]), and the environment the guest launches its
//! daemon with ([`env`]) and the run secrets a run hands it as files ([`secrets`]), and the lane IDEs that the guest
//! agent runs beside the daemon ([`ide`]). Beside them, the commands that observe or reach into a leased worker without
//! running tests ([`observe`]).
//!
//! Deliberately independent of the daemon that consumes it: resolution is pure and testable without a VM, and
//! keeping it here is what lets both the run path and the daemon lifecycle reach it without either depending on
//! the other.

pub(crate) mod affected;
pub(crate) mod bazel;
pub(crate) mod env;
pub(crate) mod ide;
pub(crate) mod lanes;
pub(crate) mod observe;
mod runtime;
pub(crate) mod secrets;

// The shared settings are Tart settings, and the observation fixture is a Tart and Parallels pool over the fake
// `tart` and `prlctl`, shell scripts, so the suites that use them are Unix only, and each of their `tests` modules
// says so.
#[cfg(test)]
#[cfg(unix)]
mod testing;

pub(crate) use affected::{
    AffectedSelection, class_name_pattern, command_suites, is_named_suite_selector, is_patternable_class_name, resolve_changed_suites,
    resolve_named_suites,
};
pub(crate) use bazel::Bazel;
pub(crate) use env::{daemon_environment, guest_jbr_platform, guest_run_environment};
pub(crate) use lanes::{lane_selection, resolve_selector_selection};
pub(crate) use observe::{
    LsArgs, PeekabooArgs, command_exec, command_ls, command_peekaboo, command_pull, command_status, command_vnc, pull_guest_file,
};
