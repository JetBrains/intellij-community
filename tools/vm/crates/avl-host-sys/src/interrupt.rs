//! The process-wide interrupt service: one SIGINT/SIGTERM handler for the whole `vm` process.
//!
//! tokio's signal registration is permanent for the process, so a per-spawn handler would pile up, and a handler that forwards to one child cannot know about
//! the others. So there is one service, started by `vm` main, and everything else asks it:
//!
//! - the first signal records its name (an envelope or a journal says which one ended the run), cancels the root
//!   [`CancellationToken`] every operation context derives from, and forwards the signal to every registered child
//!   process group;
//! - a second Ctrl-C kills every registered group and leaves at once with the shell's status for that signal (130
//!   for SIGINT), for the operator who does not want to wait for a cooperative stop.
//!
//! The groups, not the processes: the controller's children spawn children of their own (`tart exec` runs an ssh
//! client that runs the guest command), and a signal delivered only to the parent leaves a guest command running
//! with the run slot still taken. A child in its own group also does not receive the terminal's Ctrl-C by itself,
//! which is why the forwarding has to be explicit.
//!
//! On Windows the console events stand for the two signals: Ctrl-C is SIGINT, and Ctrl-Break, the close of the
//! console window and a shutdown are SIGTERM. The `listen` module is the seam that receives them, and
//! [`ProcessGroup`] is the seam that forwards them.

use std::collections::HashMap;
use std::fmt;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use avl_base::Refusal;
use avl_base::sync::lock;
use tokio_util::sync::CancellationToken;

use crate::ctx::Ctx;
use crate::proc::group::ProcessGroup;
use avl_base::RefusalExt;

mod listen;
#[cfg(test)]
mod tests;

/// The number of SIGINT, and of SIGTERM, on every Unix. Named here and not taken from `libc`, because the exit
/// status of the controller is the same on Windows, where no signal has a number.
const SIGINT_NUMBER: i32 = 2;
const SIGTERM_NUMBER: i32 = 15;

/// A signal the service answers.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Signal {
    Interrupt,
    Terminate,
}

impl Signal {
    /// `SIGINT` or `SIGTERM`: the spelling envelopes and journals record.
    pub const fn name(self) -> &'static str {
        match self {
            Self::Interrupt => "SIGINT",
            Self::Terminate => "SIGTERM",
        }
    }

    /// The status a shell reports for a process this signal ended: 130 or 143.
    pub const fn exit_status(self) -> i32 {
        128 + self.number()
    }

    const fn number(self) -> i32 {
        match self {
            Self::Interrupt => SIGINT_NUMBER,
            Self::Terminate => SIGTERM_NUMBER,
        }
    }
}

impl fmt::Display for Signal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.name())
    }
}

struct Inner {
    token: CancellationToken,
    received: OnceLock<Signal>,
    /// Registration id to process group.
    groups: Mutex<HashMap<u64, ProcessGroup>>,
    next: AtomicU64,
}

/// A handle to the interrupt service. A clone is another handle to the same service.
#[derive(Clone)]
pub struct Interrupts(Arc<Inner>);

/// The installed service and its listening task. A mutex rather than a `OnceLock`, because two listeners over one
/// service would each see the same first signal, and the second would read it as the operator's second Ctrl-C.
static INSTALLED: Mutex<Option<Installed>> = Mutex::new(None);

struct Installed {
    service: Interrupts,
    /// The listener belongs to the runtime that spawned it, and ends with it.
    listener: tokio::task::JoinHandle<()>,
}

impl Interrupts {
    /// Installs the process's SIGINT and SIGTERM handlers, once, and answers the service. A second call answers the
    /// service the first one installed. Must run inside a tokio runtime, which owns the listening task for as long
    /// as the runtime lives; a call after that runtime ended listens again from the current one, for the same
    /// service, so nothing holding the service stops being interrupted.
    pub fn install() -> Result<Self, Refusal> {
        install_into(&INSTALLED)
    }

    /// A service with no signal handlers, for tests and for tools that never install one: [`Interrupts::deliver`]
    /// is the only way it is ever interrupted.
    pub fn detached() -> Self {
        Self(Arc::new(Inner {
            token: CancellationToken::new(),
            received: OnceLock::new(),
            groups: Mutex::new(HashMap::new()),
            next: AtomicU64::new(0),
        }))
    }

    /// The root token, cancelled on the first signal.
    pub fn token(&self) -> CancellationToken {
        self.0.token.clone()
    }

    /// A fresh operation context under the root token.
    pub fn context(&self) -> Ctx {
        Ctx::new(self.token())
    }

    /// The first signal received, if one was.
    pub fn received(&self) -> Option<Signal> {
        self.0.received.get().copied()
    }

    /// What the first signal does, answering whether this was the first: records it, cancels the root token and
    /// forwards it to every registered group. A later one changes nothing here; the installed listener decides
    /// what a second signal means.
    pub fn deliver(&self, signal: Signal) -> bool {
        if self.0.received.set(signal).is_err() {
            return false;
        }
        self.0.token.cancel();
        self.each_group(|group| group.forward(signal));
        true
    }

    /// Registers a child's process group, so the first signal reaches it. Dropping the registration unregisters
    /// it. A group registered after the signal arrived is signalled at once: it was spawned by an operation that
    /// had not yet looked at its token, and it must not outlive the interrupt either.
    pub(crate) fn register_group(&self, group: &ProcessGroup) -> GroupRegistration {
        let id = self.0.next.fetch_add(1, Ordering::Relaxed);
        lock(&self.0.groups).insert(id, group.clone());
        if let Some(signal) = self.received() {
            group.forward(signal);
        }
        GroupRegistration {
            interrupts: self.clone(),
            id,
        }
    }

    /// Runs `act` on every registered group, outside the lock: a signal to a group is a system call.
    fn each_group(&self, act: impl Fn(&ProcessGroup)) {
        let groups: Vec<ProcessGroup> = lock(&self.0.groups).values().cloned().collect();
        for group in &groups {
            act(group);
        }
    }
}

/// [`Interrupts::install`] over a slot of its own, so a test can install without sharing the process's.
fn install_into(slot: &Mutex<Option<Installed>>) -> Result<Interrupts, Refusal> {
    let mut installed = lock(slot);
    if let Some(current) = &*installed
        && !current.listener.is_finished()
    {
        return Ok(current.service.clone());
    }
    let runtime = tokio::runtime::Handle::try_current()
        .map_err(|error| Refusal::internal(format!("the interrupt service needs a tokio runtime: {error}")))?;
    let mut signals = listen::Listener::new()?;
    // The service outlives a listener whose runtime ended: its handles are held by whoever installed it.
    let service = match installed.take() {
        Some(ended) => ended.service,
        None => Interrupts::detached(),
    };
    let listener = service.clone();
    let listener = runtime.spawn(async move {
        while let Some(received) = signals.recv().await {
            if listener.deliver(received) {
                continue;
            }
            // The second signal: the operator is done waiting for a cooperative stop.
            listener.each_group(ProcessGroup::kill);
            std::process::exit(received.exit_status());
        }
    });
    *installed = Some(Installed {
        service: service.clone(),
        listener,
    });
    Ok(service)
}

impl fmt::Debug for Interrupts {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Interrupts")
            .field("received", &self.received())
            .field("groups", &lock(&self.0.groups).len())
            .finish()
    }
}

/// A registered child group; dropping it unregisters the group.
#[must_use = "the group is unregistered when this drops"]
pub struct GroupRegistration {
    interrupts: Interrupts,
    id: u64,
}

impl Drop for GroupRegistration {
    fn drop(&mut self) {
        lock(&self.interrupts.0.groups).remove(&self.id);
    }
}
