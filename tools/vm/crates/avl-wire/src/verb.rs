//! Every verb of the guest agent, spelled once for the agent that dispatches it and the controller that sends it.
//!
//! The agent is installed from the same tree the controller is built from, so a verb renamed here is renamed on
//! both ends at once. A verb spelled by hand on one end is a guest that answers `usage` at exit 64, which the
//! controller reads as an agent older than itself. Packer calls the two image verbs too, from
//! `air-macos.pkr.hcl`, and that spelling is not Rust.
//!
//! [`crate::supervisor::Command`] is the run supervisor's subset. It is a type of its own because those verbs
//! answer a [`crate::supervisor::Envelope`], while the staging verbs answer a bare document.

#[cfg(test)]
mod tests;

vocabulary! {
    /// One verb of `vm-guest-agent`: its first argument, and the `command` its envelope echoes.
    pub enum AgentVerb {
        Start = "start",
        /// The detached re-invocation that `start` spawns; it never crosses the host boundary.
        Supervise = "supervise",
        Status = "status",
        Active = "active",
        Log = "log",
        Cancel = "cancel",
        Stage = "stage",
        StageCheck = "stage-check",
        LaunchPrep = "launch-prep",
        Gc = "gc",
        ProvisionImage = "provision-image",
        ValidateImage = "validate-image",
        ValidateGuest = "validate-guest",
        /// Zips only the bundles of a guest trace directory that no earlier call packed, with the same code as
        /// `air-trace pack`: `trace-pack-ready <sourceDir> <destinationZip> <ledger> [--all]`. Without `--all` it
        /// takes only the finished bundles, the ones with a manifest. The ledger is a guest file that lists the
        /// bundles already packed, one per line. The controller runs it after each test, so a scenario's trace
        /// reaches the host while the lane runs.
        TracePackReady = "trace-pack-ready",
        Contract = "contract",
        /// Bridges standard input and output to `127.0.0.1:<port>` inside the guest: `relay <port>`. The
        /// controller speaks HTTP to the UI daemon through it, so it reaches the daemon without the guest's
        /// network address. macOS Local Network privacy refuses that address to a host app that is not Apple's.
        Relay = "relay",
        /// Builds the runfiles tree of a host MANIFEST on the guest's own disk, from a
        /// [`crate::runfiles::RunfilesTreeRequest`] on standard input. A Windows host writes the MANIFEST and no
        /// tree, so the guest makes one link per line to the guest path of its target.
        RunfilesTree = "runfiles-tree",
        /// Copies one guest file to standard output unchanged: `read-file <path>`. The controller pulls a file through
        /// it on a channel that carries bytes unchanged, and [`crate::pull::FileReceipt`] on standard error names what
        /// was sent.
        ReadFile = "read-file",
    }
}
