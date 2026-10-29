//! The machine a pool runs on, and the questions whose answer depends on which one it is.
//!
//! # A match per question
//!
//! [`Machine`] is the configured backend, exactly one of [`Tart`], [`Parallels`], [`Docker`] and [`ContainerLinux`]. It
//! answers the questions a caller asks without caring which backend it has, and each answer is a `match` delegating
//! to the backend's own method of the same name:
//!
//! - **the version gate** - [`Machine::require_available`]: a floor with an escape hatch on Tart, a registered
//!   Apple-Virtualization VM on Parallels;
//! - **the guest-exec argv shape** - [`Machine::guest_argv`]: `tart exec [-i] <worker> <argv…>` and `docker exec [-i]
//!   <worker> <argv…>` versus `prlctl exec <worker> "<one shell string>"`, the single difference every guest call
//!   site used to know;
//! - **liveness** - [`Machine::running`]: Tart's worker is a host process the controller owns, identified by a pid
//!   plus its `ps` start time and command; Parallels' worker is a VM it does not own, and the only honest question
//!   is `prlctl status`. Docker's worker is a container the controller owns by name, so liveness is `docker
//!   inspect`; the container-linux worker is the container the skill's script lists for this checkout;
//! - **the share mount** - [`Machine::share_mount`]: VirtioFS on Tart and Parallels, bind mounts on Docker and
//!   container-linux.
//!
//! The lifecycle of a worker needs the manager's host state, locks and guest channel, so its operations are methods
//! of the worker manager, in the lifecycle module of the worker module. Each is one `match` on the machine too, and
//! each arm calls the body in the backend's own file under `worker/`. Every backend answers each lifecycle operation.
//! An `unsupported` refusal is an answer that keeps its code, and the next section says why. The lifecycle covers
//! these operations:
//!
//! - the readiness gate of a lease operation and the readiness gate of a lease release;
//! - whether a lease release may skip the guest of a worker that runs nothing;
//! - the start and the stop of a worker;
//! - `pool init` and `pool gc`;
//! - the unmaking of a slot that `pool recycle` runs, and the delete of the builder VM of the Apple `container` engine.
//!
//! Every caller of the other operations already knows which backend it has, so it asks the concrete backend. These
//! operations are:
//!
//! - the share declaration and the suspend;
//! - the root-disk growth, the clone and the delete;
//! - the VM state;
//! - the sealed-golden receipt and the provenance receipt;
//! - the console-login wait;
//! - the Peekaboo exec as the current user.
//!
//! # Why every refusal keeps its code
//!
//! An operation a backend cannot do answers [`UNSUPPORTED_CODE`] rather than a zero value, because callers automate
//! against it: `pool gc` on Parallels, Peekaboo on Tart, and a Parallels-plus-Linux pairing are all
//! `unsupported_backend_operation` at exit 2. Stubbing them to "succeeded, did nothing" would turn a refusal a
//! caller can branch on into a worker that silently never got its shares.

use avl_base::{Exit, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ShareMount;

use crate::worker::container_linux::ContainerLinux;
use crate::worker::docker::{ContainerState, Docker};
#[cfg(unix)]
use crate::worker::parallels::Parallels;
#[cfg(unix)]
use crate::worker::tart::Tart;

#[cfg(test)]
mod tests;

/// The refusal code every "this backend cannot do that" answer carries. Stable: callers match on it.
pub(crate) const UNSUPPORTED_CODE: &str = "unsupported_backend_operation";

/// A refusal with [`UNSUPPORTED_CODE`] and prose naming the backend and what it will not do.
///
/// The prose is required because these refusals are read by a human deciding what to run instead: "pool gc removes
/// Tart worker clones; Parallels owns its own VM" tells the operator there is nothing to garbage-collect, where a
/// bare "unsupported" would read as a missing feature.
pub(crate) fn unsupported(message: impl Into<String>) -> Refusal {
    Refusal::new(UNSUPPORTED_CODE, Exit::USAGE, message)
}

/// Whether a refusal is one of these, by code: a caller asking "may I skip this backend" gets the same answer
/// whoever built the refusal.
#[cfg(test)]
pub(crate) fn is_unsupported(refusal: &Refusal) -> bool {
    refusal.code == UNSUPPORTED_CODE
}

/// The argv of a host command of a backend: its `program`, then the `arguments`.
pub(crate) fn host_argv(program: String, arguments: &[&str]) -> Vec<String> {
    std::iter::once(program)
        .chain(arguments.iter().map(|argument| (*argument).to_owned()))
        .collect()
}

/// `<head> exec [-i] <worker> <argv…>`, the guest command line of Tart and of Docker.
///
/// The one spelling of that line, and the one place its head is declared to phase timing, so a spawn that did not
/// come through here is a host call by construction. `-i` keeps stdin open and is passed exactly when the caller has
/// stdin. Never `-t`: a tty would rewrite the relay's bytes and the file writes that `tee` carries. `docker exec`
/// lands as root, so the `sudo -H -u admin` prefix every guest step already has applies unchanged.
///
/// The head is the `resolved_program` of the backend, not the program of the gate: `daemon stop` and `daemon log`
/// reach the guest without the gate, and an empty head would reach the spawn. The head is not read from the
/// settings either, because the pinned executable that the gate resolved lives in the backend.
fn exec_argv(ctx: &Ctx, head: String, worker: &str, argv: &[String], interactive: bool) -> Vec<String> {
    ctx.timeline().declare_guest_head(&head);
    let mut line = vec![head, "exec".to_owned()];
    if interactive {
        line.push("-i".to_owned());
    }
    line.push(worker.to_owned());
    line.extend_from_slice(argv);
    line
}

// --- the machine ---------------------------------------------------------------------------------------------

/// The configured backend's concrete implementation. Exactly one, so a branch cannot reach for the wrong one.
///
/// Every question takes a worker name rather than the value holding one, because a backend instance serves the
/// whole pool: `pool gc` walks every slot. On Windows the Docker and the container-linux backends are the only ones.
/// What the controller can observe of one slot without a start: the facts that rank a free slot for a lease.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum SlotState {
    Running,
    /// The machine exists and does not run, so it keeps its disk or its volume.
    Stopped,
    /// No machine has the name of the slot.
    Absent,
    /// The backend gave no answer.
    Unknown,
}

#[cfg_attr(
    windows,
    expect(
        clippy::large_enum_variant,
        reason = "a manager holds one machine for its whole life, so a box would save no memory"
    )
)]
pub(crate) enum Machine {
    #[cfg(unix)]
    Tart(Tart),
    #[cfg(unix)]
    Parallels(Parallels),
    Docker(Docker),
    ContainerLinux(ContainerLinux),
}

impl Machine {
    /// The Docker backend, when this pool is a Docker one.
    #[cfg(test)]
    pub(crate) const fn docker(&self) -> Option<&Docker> {
        match self {
            Self::Docker(docker) => Some(docker),
            #[cfg(unix)]
            Self::Tart(_) | Self::Parallels(_) => None,
            Self::ContainerLinux(_) => None,
        }
    }

    /// Refuses unless this backend can be used for this worker right now.
    ///
    /// Not just "is the binary there": on Parallels it includes whether the VM is registered and is an
    /// Apple-Virtualization macOS machine; on Tart a version floor; on Docker an engine that runs containers of the
    /// guest's architecture natively. All three answer [`Exit::UNAVAILABLE`] for an absent tool, which is what a
    /// caller retries against.
    pub(crate) async fn require_available(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        match self {
            #[cfg(unix)]
            Self::Tart(tart) => tart.require_available(ctx, worker).await,
            #[cfg(unix)]
            Self::Parallels(parallels) => parallels.require_available(ctx, worker).await,
            Self::Docker(docker) => docker.require_available(ctx, worker).await,
            Self::ContainerLinux(container_linux) => container_linux.require_available(ctx, worker).await,
        }
    }

    /// The host command line that runs `argv` inside the worker.
    ///
    /// `interactive` asks for stdin to be carried. Tart and Docker have a flag for it (`-i`); Parallels' equivalent
    /// lives on the current-user path, so the request is not passed there - an interactive request answered
    /// non-interactively still runs the command.
    ///
    /// The head is resolved by the backend, so no guest command line has an empty executable, whichever verb builds
    /// it and whether or not that verb passed [`Machine::require_available`] first. Tart refuses an executable it
    /// cannot resolve with `tart_missing`, and Docker refuses one with `docker_missing`, both [`Exit::UNAVAILABLE`].
    pub(crate) async fn guest_argv(&self, ctx: &Ctx, worker: &str, argv: &[String], interactive: bool) -> Result<Vec<String>, Refusal> {
        match self {
            #[cfg(unix)]
            Self::Tart(tart) => Ok(exec_argv(ctx, tart.resolved_program(ctx).await?, worker, argv, interactive)),
            #[cfg(unix)]
            Self::Parallels(parallels) => Ok(parallels.guest_argv(ctx, worker, argv)),
            Self::Docker(docker) => Ok(exec_argv(ctx, docker.resolved_program(ctx).await?, worker, argv, interactive)),
            Self::ContainerLinux(container_linux) => Ok(container_linux.guest_argv(ctx, worker, argv, interactive)),
        }
    }

    /// How the guest holds this pool's shares. A VirtioFS guest keeps a dead node for a file the host replaced, so its
    /// share refresh is a remount. A bind mount serves the new bytes within a second.
    pub(crate) const fn share_mount(&self) -> ShareMount {
        match self {
            #[cfg(unix)]
            Self::Tart(_) | Self::Parallels(_) => ShareMount::VirtioFs,
            Self::Docker(_) | Self::ContainerLinux(_) => ShareMount::Bind,
        }
    }

    /// The state of each slot in `workers`, asked without a start and without a change: the facts that rank a free
    /// slot for a lease ([`crate::worker::lease::SlotReason`]).
    ///
    /// Tart answers from its run process and its VM list. Docker answers from `inspect` once its engine answers, and a
    /// Docker engine that is down is not started, so each slot is then [`SlotState::Unknown`]. A backend with one
    /// slot, Parallels and `container-linux`, answers [`SlotState::Unknown`] and asks nothing. A probe that fails is
    /// [`SlotState::Unknown`] too: the ranking is a preference, never a refusal.
    pub(crate) async fn slot_states(&self, ctx: &Ctx, workers: &[&str]) -> Vec<SlotState> {
        let mut states = Vec::with_capacity(workers.len());
        match self {
            #[cfg(unix)]
            Self::Tart(tart) => {
                for worker in workers {
                    states.push(match tart.running(ctx, worker).await {
                        Ok(true) => SlotState::Running,
                        Ok(false) => match tart.exists(ctx, worker).await {
                            Ok(true) => SlotState::Stopped,
                            Ok(false) => SlotState::Absent,
                            Err(_) => SlotState::Unknown,
                        },
                        Err(_) => SlotState::Unknown,
                    });
                }
            }
            Self::Docker(docker) => {
                let reached = docker.reach_engine(ctx).await.unwrap_or(false);
                for worker in workers {
                    states.push(if reached {
                        match docker.state(ctx, worker).await {
                            Ok(ContainerState::Running) => SlotState::Running,
                            Ok(ContainerState::Absent) => SlotState::Absent,
                            Ok(_) => SlotState::Stopped,
                            Err(_) => SlotState::Unknown,
                        }
                    } else {
                        SlotState::Unknown
                    });
                }
            }
            #[cfg(unix)]
            Self::Parallels(_) => states.resize(workers.len(), SlotState::Unknown),
            Self::ContainerLinux(_) => states.resize(workers.len(), SlotState::Unknown),
        }
        states
    }

    /// Whether the worker's machine is up, by whatever this backend can observe. Not "is the guest ready": a
    /// running VM sitting at a login window answers true.
    pub(crate) async fn running(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        match self {
            #[cfg(unix)]
            Self::Tart(tart) => tart.running(ctx, worker).await,
            #[cfg(unix)]
            Self::Parallels(parallels) => parallels.running(ctx, worker).await,
            Self::Docker(docker) => docker.running(ctx, worker).await,
            Self::ContainerLinux(container_linux) => container_linux.running(ctx, worker).await,
        }
    }
}
