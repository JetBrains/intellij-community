//! `bench`: the start-up measurement of an IDE on this host, from an immutable staged copy of its Bazel dev
//! distribution.
//!
//! A session builds the self-contained `.dist` of a row and the row launcher with the host Bazel build, stages the
//! distribution and the JBR of the launcher as a generation keyed by their digest, and starts every IDE run from that
//! generation. A run has its own sandbox and the instrument options of the platform: the start-up report, the trace,
//! the class logs and the FUS log, which the controller reads before the quit. A run counts only when it passed the
//! gate of its arm: the FUS event of the welcome screen, or the highlighted editor of the `project` arm. The answer is
//! the summary, which `summary.json` holds too, and a text digest. It takes no worker and no lease.
//!
//! - [`options`]: the verbs and their options;
//! - [`command`]: a `welcome`, an `open-project` or a `project` session from the build to the summary, `replay`, `gc`,
//!   `trace`, `activities`, `classes`, `compare` and `ls`;
//! - [`timeline`], [`spans`], [`activities`]: the spans of one run in milliseconds from the process start, `trace`,
//!   which prints the spans of a window, and `activities`, which groups the activities of a window by class and by
//!   plugin;
//! - [`classes`]: the loaded classes of one arm by plugin and by module, at an anchor of the run;
//! - [`resolve`], [`stage`], [`gc`]: what Bazel built, the generation, and the removal of old generations;
//! - [`launch`], [`load`]: the Starter-shaped IDE process and its supervision, and the host load at each run start;
//! - [`binary`]: the age of the controller binary that `AIR_VM_BIN` names, against its sources;
//! - [`session`], [`record`], [`summary`], [`digest`]: the session layout, one run, the summary and its text;
//! - [`compare`]: the medians of several sessions against the first one, and the noise of the modal arm;
//! - [`ls`]: the sessions of this host, newest first, with their inputs and their medians;
//! - [`fus`], [`stats`], [`trace`], [`pluginlog`], [`classload`], [`profile`]: the readers of the files that the IDE and
//!   the JVM write;
//! - [`arm`], [`files`]: the start-up variants, and the file helpers.

mod activities;
mod arm;
mod binary;
mod classes;
mod classload;
mod command;
mod compare;
mod digest;
mod files;
mod fus;
mod gc;
mod launch;
mod load;
mod ls;
mod options;
mod pluginlog;
mod profile;
mod record;
mod resolve;
mod session;
mod spans;
mod stage;
mod stats;
mod summary;
#[cfg(test)]
mod testing;
mod timeline;
mod trace;

pub(crate) use command::Bench;
pub(crate) use options::{BenchVerb, EXIT_CODES, LONG_ABOUT};
