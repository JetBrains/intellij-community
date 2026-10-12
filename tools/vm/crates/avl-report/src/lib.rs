//! Run reports: the content digests, report assembly, the shard merge ladder and the flake arithmetic.
//!
//! Sync and portable: pure functions over `avl-wire` documents plus synchronous file publication through
//! `avl_base::fs`. No Windows program links it; it is portable so that its tests run on the Windows host.

pub mod aggregate;
pub mod digest;
pub mod report;
