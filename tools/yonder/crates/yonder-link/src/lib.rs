//! The link of yonder: what the client and the agent share.
//!
//! - [`identity`]: the self-signed certificate of one end, and its fingerprint.
//! - [`allow`]: the fingerprints of the clients that an agent accepts.
//! - [`channel`]: TLS 1.3 over TCP, each end pinned by its fingerprint, and the frames on it.
//! - [`proto`]: the requests and the replies.

pub mod allow;
pub mod channel;
pub mod identity;
pub mod proto;
mod relay;

pub use allow::AllowList;
pub use channel::{Channel, ClientTls, Closer, Frame, LinkError, ServerTls};
pub use identity::{Fingerprint, Identity};

/// The TCP port of the agent when a host file names none.
pub const DEFAULT_PORT: u16 = 7741;
