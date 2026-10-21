//! Every verb of the guest agent, spelled once for the agent that dispatches it and the controller that sends it.
//!
//! The agent is installed from the same tree the controller is built from, so a verb renamed here is renamed on
//! both ends at once. A verb spelled by hand on one end is a guest that answers `usage` at exit 64, which the
//! controller reads as an agent older than itself. Packer calls the two image verbs too, from
//! `air-macos.pkr.hcl`, and that spelling is not Rust.
//!
//! [`crate::supervisor::Command`] is the run supervisor's subset. It is a type of its own because those verbs
//! answer a [`crate::supervisor::Envelope`], while `ide-gc` answers a bare document.

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
        /// Cancels or keeps the live IDE runs under an IDE root, and removes the old log directories of each
        /// context: `ide-gc --root <ideRoot> [--stop-all | --keep-product <digest>]`. It answers
        /// [`crate::ide::IdeGcResult`].
        IdeGc = "ide-gc",
        /// Serves the control channel of the lane daemon in the run slot: `lane-daemon --state-dir <dir> --port <port>
        /// ...`. It owns the lane services and the lane IDE until its `/shutdown`. See [`crate::daemon`].
        LaneDaemon = "lane-daemon",
        /// Runs one iteration of a lane from a runfiles tree, with no control channel: `lane-run --lane <lane>
        /// --descriptor <file> ...`. A Bazel test of the lane rule, `bt --lane` and a TeamCity agent run it on their own
        /// host. It quits the IDE at the end and exits 0 or 1.
        LaneRun = "lane-run",
    }
}
