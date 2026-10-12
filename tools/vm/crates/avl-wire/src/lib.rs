//! Documents that cross a boundary: supervisor, guest verbs, staging, the Bazel runtime descriptor, the runfiles
//! MANIFEST and the host-to-guest path table, daemon, progress and report. The JUnit reader is `bt-junit` of BT's
//! workspace, which the host crates link, so the guest agent does not.
//!
//! Pure data and decoders: no I/O, no threads, no processes, so every program of the lane (including the ones
//! built for Windows) can read these wires.

#[macro_use]
mod vocabulary;

pub mod daemon;
pub(crate) mod json;
pub mod path_map;
pub mod progress;
pub mod pull;
pub mod report;
pub mod runfiles;
pub mod runtime;
pub mod stage;
pub mod supervisor;
pub mod verb;

pub use supervisor::SCHEMA_VERSION;
pub use vocabulary::UnknownWord;
