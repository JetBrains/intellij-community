//! The lane identity: the config key of a lane IDE, the loopback ports of its lane services, and its secrets.
//!
//! Each value is a pure function of the config key, so a daemon that boots again derives the values that the running
//! IDE already holds. The key hashes the lane document, so a launch input that the key does not read cannot exist. The
//! IDE computes none of these values: the context files of [`super::context`] hand them over.

use std::collections::BTreeMap;
use std::ops::Range;

use super::package::sha256_hex;

#[cfg(test)]
mod tests;

/// The fixed port of the UI-test bridge in the lane IDE, `AIR_UI_TEST_HTTP_PORT` of the bridge.
pub(crate) const BRIDGE_PORT: u16 = 31_347;

/// The window of the fake ACP registry. It holds the default port 27100 of the lane daemon.
pub(crate) const REGISTRY_PORTS: Range<u16> = 26_000..30_000;

/// The window of the control server of the controlled agents.
pub(crate) const CONTROL_PORTS: Range<u16> = 30_000..30_500;

/// The window of the feedback capture.
pub(crate) const FEEDBACK_PORTS: Range<u16> = 30_500..31_000;

/// The window of the JetBrains Cloud emulator.
pub(crate) const CLOUD_PORTS: Range<u16> = 32_000..32_500;

/// The purpose of the token of the UI-test bridge, which the IDE reads from `air-ui-test/token`.
pub(crate) const BRIDGE_TOKEN_PURPOSE: &str = "air-ui-test-bridge";

/// The purpose of the lane-service token of the control route of each lane service.
pub(crate) const LANE_SERVICES_PURPOSE: &str = "air-lane-services";

/// The seed suffix of the port of the control server in the IDE.
const IDE_CONTROL_SEED: &str = "ide-control";

/// The environment variable of a controlled agent that names the control server, `CONTROL_URL_ENV` of the agent.
pub(crate) const CONTROL_URL_ENV: &str = "ACP_FAKE_AGENT_CONTROL_URL";

/// The environment variable of a controlled agent that names its credential, `CONTROL_AGENT_KEY_ENV` of the agent.
pub(crate) const CONTROL_AGENT_KEY_ENV: &str = "ACP_FAKE_AGENT_CONTROL_KEY";

/// The inputs of the config key that the lane document does not hold.
#[derive(Clone, Debug)]
pub(crate) struct KeyInputs<'a> {
    /// The name of the lane in its lane document, such as `UI` or `GUI_CHAT`.
    pub lane: &'a str,
    /// The hex SHA-256 of the bytes of the lane document.
    pub lane_document: &'a str,
    /// The identity of the product under test. A blank stamp is no stamp.
    pub product_stamp: Option<&'a str>,
    /// The identity of each declared CLI runtime, by its name.
    pub runtimes: &'a BTreeMap<String, String>,
    /// The hex SHA-256 of the option file templates.
    pub option_files: &'a str,
}

impl KeyInputs<'_> {
    /// The text that the key hashes, one line each, every line ended by a line feed: `lane=`, `laneDocument=`,
    /// `productStamp=` when there is a stamp, `runtime.<name>=` sorted by the name, and `optionFiles=`.
    ///
    /// For the live lane, the runtimes also hold the Central runtime and the live runtimes of its live context. No line
    /// names a credential, because each iteration seeds its own copies into the lane home.
    pub(crate) fn text(&self) -> String {
        let mut lines = vec![format!("lane={}", self.lane), format!("laneDocument={}", self.lane_document)];
        if let Some(stamp) = self.product_stamp.filter(|stamp| !stamp.trim().is_empty()) {
            lines.push(format!("productStamp={stamp}"));
        }
        lines.extend(self.runtimes.iter().map(|(name, identity)| format!("runtime.{name}={identity}")));
        lines.push(format!("optionFiles={}", self.option_files));
        lines.iter().map(|line| format!("{line}\n")).collect()
    }

    /// The config key: `air-<lowercase lane>-` and the first 16 hex digits of the SHA-256 of [`Self::text`].
    pub(crate) fn config_key(&self) -> String {
        format!("air-{}-{}", self.lane.to_lowercase(), &sha256_hex(self.text().as_bytes())[..16])
    }
}

/// The identity of one lane IDE: its config key and every value that derives from it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LaneIdentity {
    key: String,
    daemon_port: u16,
}

impl LaneIdentity {
    /// The identity of `key` for a lane daemon on `daemon_port`. A derived port never takes the port of the daemon.
    pub(crate) fn new(key: impl Into<String>, daemon_port: u16) -> Self {
        Self {
            key: key.into(),
            daemon_port,
        }
    }

    /// The config key, which is also the launch name and the context name of the IDE.
    pub(crate) fn key(&self) -> &str {
        &self.key
    }

    /// The port of the fake ACP registry.
    pub(crate) fn registry_port(&self) -> u16 {
        self.derived_port(&self.key, &REGISTRY_PORTS)
    }

    /// The port of the control server in the IDE. It derives from `<key>|ide-control`.
    pub(crate) fn control_port(&self) -> u16 {
        self.derived_port(&format!("{}|{IDE_CONTROL_SEED}", self.key), &CONTROL_PORTS)
    }

    /// The port of the feedback capture.
    pub(crate) fn feedback_port(&self) -> u16 {
        self.derived_port(&self.key, &FEEDBACK_PORTS)
    }

    /// The port of the JetBrains Cloud emulator.
    pub(crate) fn cloud_port(&self) -> u16 {
        self.derived_port(&self.key, &CLOUD_PORTS)
    }

    /// The lane secret for `purpose`: the first 32 hex digits of the SHA-256 of `<key>|<purpose>`.
    pub(crate) fn secret(&self, purpose: &str) -> String {
        sha256_hex(format!("{}|{purpose}", self.key).as_bytes())[..32].to_owned()
    }

    /// The URL of the control server in the IDE for the credential `credential_key`, with the token of that credential.
    pub(crate) fn control_url(&self, credential_key: &str) -> String {
        format!(
            "ws://127.0.0.1:{}/control?token={}",
            self.control_port(),
            self.secret(credential_key)
        )
    }

    /// The environment of an agent process that reaches the control server in the IDE as `credential_key`.
    pub(crate) fn control_environment(&self, credential_key: &str) -> [(&'static str, String); 2] {
        [
            (CONTROL_URL_ENV, self.control_url(credential_key)),
            (CONTROL_AGENT_KEY_ENV, credential_key.to_owned()),
        ]
    }

    /// The port that `seed` picks in `range`, stepped up past the daemon port, wrapping at the end.
    fn derived_port(&self, seed: &str, range: &Range<u16>) -> u16 {
        let span = u64::from(range.end - range.start);
        let value = u64::from_str_radix(&sha256_hex(seed.as_bytes())[..8], 16).unwrap_or_default();
        (0..span)
            .map(|step| range.start + u16::try_from((value + step) % span).unwrap_or_default())
            .find(|port| *port != self.daemon_port)
            .unwrap_or(range.start)
    }
}

/// The key of the credential of the terminal sign-in of `agent_key`, `terminalLoginAgentKey` of the journeys.
pub(crate) fn terminal_login_key(agent_key: &str) -> String {
    format!("{agent_key}-terminal-login")
}
