//! The lane daemon: the verb `lane-daemon` and its parts. The control channel, the owner of the lane IDE, its readiness
//! and the reload of its bridge plugin, the launch inputs, the identity of the lane IDE, the files of its context, the
//! fake ACP agent package, and the lane services that the lane IDE talks to instead of production. The program runner
//! runs the suite documents against the lane IDE, with its values, its host ids, the recycle of the IDE, the watchdog,
//! the trace and the run stream.

pub(crate) mod channel;
pub(crate) mod context;
pub(crate) mod host_ids;
pub(crate) mod ide;
pub(crate) mod identity;
pub(crate) mod inputs;
pub(crate) mod iteration;
pub(crate) mod package;
pub(crate) mod program;
pub(crate) mod readiness;
pub(crate) mod recycle;
pub(crate) mod reload;
pub(crate) mod run;
pub(crate) mod services;
pub(crate) mod session_logs;
pub(crate) mod stream;
pub(crate) mod trace;
pub(crate) mod values;
pub(crate) mod watchdog;
