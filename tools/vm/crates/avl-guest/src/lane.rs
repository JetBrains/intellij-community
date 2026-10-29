//! The lane daemon: the verb `lane-daemon` and its parts. The control channel, the owner of the lane IDE, its readiness
//! and the reload of its bridge plugin, the launch inputs, the identity of the lane IDE, the files of its context, the
//! fake ACP agent package, the lane services that the lane IDE talks to instead of production, and the live context of
//! the live lane, whose real agents spend a JetBrains Central account. The program runner runs the suite documents
//! against the lane IDE, with its values, its host ids, the recycle of the IDE, the watchdog, the trace and the run
//! stream.

pub(crate) mod build_log;
pub(crate) mod channel;
pub(crate) mod context;
pub(crate) mod evidence;
pub(crate) mod host_ids;
pub(crate) mod ide;
pub(crate) mod identity;
pub(crate) mod inputs;
pub(crate) mod iteration;
pub(crate) mod live;
pub(crate) mod monitor;
pub(crate) mod own_display;
pub(crate) mod package;
pub(crate) mod performance;
pub(crate) mod program;
pub(crate) mod readiness;
pub(crate) mod recycle;
pub(crate) mod reload;
pub(crate) mod rerun;
pub(crate) mod run;
pub(crate) mod run_result;
pub(crate) mod services;
pub(crate) mod session_logs;
pub(crate) mod stream;
#[cfg(test)]
pub(crate) mod testing;
pub(crate) mod trace;
pub(crate) mod values;
pub(crate) mod watchdog;
pub(crate) mod window_manager;
