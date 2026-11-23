//! The proxy side of the fake-agent control channel in the IDE under test.
//!
//! The control server in the IDE steers each fake agent process over one WebSocket. A proxy is one agent process on
//! that server. The spawner of the Cloud emulator in the guest agent is one proxy for each dial, and the fake agent
//! `acp-fake-agent` is one for its own process. The far end is
//! `AcpFakeAgentControlServer` of `plugins/air/integrationTests/acp-protocol`.
//!
//! The crate links `serde_json` alone, because the guest agent pins its crate closure.

pub mod encoding;
pub mod json;
pub mod proxy;
pub mod websocket;
