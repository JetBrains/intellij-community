//! The live context of a lane: the real agents of the lane `UI_LIVE`, which spend the JetBrains Central account of the
//! developer.
//!
//! [`central`] wires the guest account to Central before the lane IDE launches, and tears that account down at the end
//! of the iteration. [`home`] gives the lane home the Central wiring of that account, and the bin directory the
//! launchers of the live agents and the Central guard.

pub(crate) mod central;
pub(crate) mod home;
