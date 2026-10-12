//! Host system seams: subprocesses and the interrupt service, locks, private files, the guest setup with the Linux
//! guest's packages, the read-only share set both backends render, and the start of the detached trace viewer
//! ([`viewer`]).
//!
//! Unix and Windows. [`proc`] says how each platform groups a spawn, [`lock`] how it holds a lock, and [`private`]
//! what a private file is on it.
//!
//! Two pieces are process-wide and every later host crate stands on them:
//!
//! - [`interrupt::Interrupts`], the one SIGINT/SIGTERM handler of the `vm` binary. It cancels the root token and
//!   signals every registered child group; a second Ctrl-C leaves at once.
//! - [`ctx::Ctx`], what a call chain carries: the cancellation token, the phase timeline and the open
//!   phase, and the run id. One small cloneable value a call chain passes down.
//!
//! Every wait loop of the controller runs through [`poll::Poll`], one budget and one growing pause.

pub mod ctx;
pub mod fs;
pub mod guest;
pub mod interrupt;
pub mod lock;
pub mod paths;
pub mod poll;
pub mod private;
pub mod proc;
pub mod runfiles;
pub mod share;
pub mod viewer;

#[cfg(test)]
mod testing;

pub use ctx::{Ctx, PollClock};
pub use interrupt::{Interrupts, Signal};
pub use poll::{Backoff, Poll};
pub use proc::{
    Captured, Channel, GuestStream, Interruptible, PipedChild, ProbeExit, ProbeOutput, ProcError, ProcessTable, PsField, Runner,
    SpawnOptions, probe_unanswered,
};
