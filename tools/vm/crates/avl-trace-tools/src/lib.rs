//! The tools over scenario traces, which the recorder does not run: the packer, the discovery of bundles and the
//! viewer's URLs. The documents themselves are `avl-trace`'s.
//!
//! [pack] zips a trace root so it can leave the machine that recorded it: `air-trace pack` and the guest agent's
//! `trace-pack-ready` run it. [discover] finds the bundles on this machine and says what each one is: the viewer's
//! server, the planner and the controller's trace sync read with it. [viewer] spells the viewer's URLs and the
//! header that names its server.

pub mod discover;
pub mod pack;
pub mod viewer;

#[cfg(test)]
mod testdata;
