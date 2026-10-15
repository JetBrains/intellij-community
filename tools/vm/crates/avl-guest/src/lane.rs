//! The lane daemon: the verb `lane-daemon` and its parts. The control channel, the owner of the lane IDE, its readiness
//! and the reload of its bridge plugin, the launch inputs, the identity of the lane IDE, the files of its context, the
//! fake ACP agent package, and the lane services that the lane IDE talks to instead of production.

pub(crate) mod channel;
pub(crate) mod context;
pub(crate) mod ide;
pub(crate) mod identity;
pub(crate) mod inputs;
pub(crate) mod iteration;
pub(crate) mod package;
pub(crate) mod readiness;
pub(crate) mod reload;
pub(crate) mod services;
