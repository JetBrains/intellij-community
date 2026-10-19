//! Documents that cross a boundary: supervisor, guest verbs, the shape of a digest, the Bazel runtime descriptor, the
//! runfiles MANIFEST and the host-to-guest path table, the lane IDE launch, the program of a generated suite, daemon,
//! progress and report. The JUnit reader is `bt-junit` of BT's workspace, which the host crates link, so the guest
//! agent does not.
//!
//! Pure data and decoders: no I/O, no threads, no processes, so every program of the lane (including the ones
//! built for Windows) can read these wires.

#[macro_use]
mod vocabulary;

pub mod daemon;
pub mod digest;
pub mod ide;
pub(crate) mod json;
pub mod path_map;
pub mod program;
pub mod progress;
pub mod pull;
pub mod report;
pub mod runfiles;
pub mod runtime;
pub mod session_log;
pub mod supervisor;
pub mod verb;

pub use supervisor::SCHEMA_VERSION;
pub use vocabulary::UnknownWord;
