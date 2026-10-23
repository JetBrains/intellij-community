//! Workers: the Tart, Parallels, Docker and container-linux backends, readiness gates, the pool, and leases.
//!
//! Three layers, each depending only on the one below it:
//!
//! - the backends: [`hypervisor`], the questions every backend answers; [`tart`], the Tart implementation and everything
//!   that makes a sealed golden trustworthy: the version gate, the process identity a worker's run process is
//!   recognised by, the clone-input signatures, the seal and the per-worker provenance; [`parallels`], `prlctl`
//!   against a VM the controller does not own; [`docker`], the `docker` CLI against containers the controller
//!   creates and owns by name, from an image it builds; [`container_linux`], the script of the `testing-ui` skill against
//!   the one container of this checkout, reached through its control port ([`channel`]);
//! - [`worker`], one pool's lifecycle: the lease *document*, the readiness gates every lease operation runs first,
//!   start and stop, and the `pool` commands. Each operation is one `match` on the configured backend, and each arm
//!   calls the body in the backend's own file: `worker/tart.rs`, `worker/parallels.rs`, `worker/docker.rs` and
//!   `worker/container_linux.rs`. [`run_marker`] is the second document of this layer. It names the run in flight on
//!   a worker, and a lifecycle operation of another process refuses while that run lives;
//! - [`lease`], the lease *policy*: the atomic placement, the mode-0600 receipt that is the only accepted handle to a
//!   lease, and the `lease` commands.
//!
//! The Tart and Parallels backends are Unix only: a Tart worker's run process is detached into its own session, and
//! the two macOS backends drive a macOS tool. A Windows host has the Docker and the container-linux backends, so
//! there [`tart`] and [`parallels`] do not exist, and `Config::load` refuses those two before a manager is built.

pub(crate) mod channel;
pub(crate) mod container;
pub(crate) mod container_linux;
pub(crate) mod docker;
pub(crate) mod hypervisor;
pub(crate) mod lease;
#[cfg(unix)]
pub(crate) mod parallels;
pub(crate) mod pin;
pub(crate) mod run_marker;
#[cfg(unix)]
pub(crate) mod tart;
#[expect(
    clippy::module_inception,
    reason = "the module tree of the folded avl-worker crate is kept: `worker::worker` is one pool's lifecycle beside the backends"
)]
pub(crate) mod worker;

pub(crate) mod secure;

#[cfg(test)]
pub(crate) mod testing;
