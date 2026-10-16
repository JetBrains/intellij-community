//! The live context of a lane: the real agents of the lane `UI_LIVE`, which spend the JetBrains Central account of the
//! developer.
//!
//! [`central`] wires the guest account to Central before the lane IDE launches, and tears that account down at the end
//! of the iteration.

pub(crate) mod central;
