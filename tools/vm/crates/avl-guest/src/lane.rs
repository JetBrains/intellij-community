//! The lane daemon's own parts: the identity of the lane IDE, the files of its context, the fake ACP agent package, and
//! the lane services that the lane IDE talks to instead of production.

pub(crate) mod context;
pub(crate) mod identity;
pub(crate) mod package;
pub(crate) mod services;
