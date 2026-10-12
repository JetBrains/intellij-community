//! The live terminal dashboard of `run`, `shard` and `flake`: how a person at a terminal reads their progress
//! stream.
//!
//! The dashboard has two parts. The scrollback above it gets one line for each fact worth keeping: a phase that
//! ended with its time against its typical time, a test that ended with its trace link, a decision and its reason.
//! With one worker, the tests print under a header of their class. The live region under it is redrawn in place:
//! the finished tests that wait for their traces, the checklist of the run's phases, a progress bar over the
//! discovered tests with the time left, and the test that runs now. When the run ends, the live region becomes a
//! verdict card that stays on the terminal. The card renders the run's [`avl_wire::progress::Verdict`], so it is
//! the answer of the command and no text follows it.
//!
//! The state changes only under the reporter's lock, so the scrollback lines keep the order in which the events
//! were published. `state` applies the events, `view` renders them as strings with ANSI styles and OSC 8
//! links, and `dashboard` is the one part that touches the terminal.

mod dashboard;
mod paint;
mod palette;
mod state;
mod view;

pub(crate) use dashboard::{Dashboard, Options};
pub(crate) use paint::ColorChoice;
pub(crate) use palette::{ColorDepth, TerminalColors};
