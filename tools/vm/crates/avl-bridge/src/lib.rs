//! The client of the in-IDE bridge, which the recorder and the guest agent share.
//!
//! The bridge is the HTTP server of the IDE under test, on the port of its built-in server. It serves the request
//! endpoint, the trace routes of [avl_trace::bridge] and the lifecycle long poll of [lifecycle]. The far end is
//! `plugins/air/tests/integration/bridge/tests`.
//!
//! Every call goes to loopback, carries the token of the launch in [avl_trace::bridge::TOKEN_HEADER], and ends at
//! its deadline. [Error::is_transient] tells the caller which failures a later call can cure: a refused connect, a
//! passed deadline and a bridge that unloads. A refused token is final.

mod client;
mod failure;
pub mod lifecycle;
mod trace;

pub use client::{Client, Error, ErrorKind, JSON, Rejection, url};
pub use failure::BridgeFailure;
