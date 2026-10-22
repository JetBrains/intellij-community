//! Run reports: the content digests, report assembly, the shard merge ladder and the flake arithmetic, the TeamCity
//! service messages, and the IJ Perf file of a scenario. The report, the messages and the file read the run stream as
//! `avl-junit` folds it into cases.
//!
//! Sync and portable: pure functions over `avl-wire` documents plus synchronous file publication through
//! `avl_base::fs`. No Windows program links it; it is portable so that its tests run on the Windows host.

pub mod aggregate;
pub mod digest;
pub mod performance;
pub mod report;
pub mod teamcity;

#[cfg(test)]
mod testing;
