//! The daemon's account of itself, without a daemon.

use async_trait::async_trait;
use avl_base::Refusal;
use avl_host_sys::Ctx;
use avl_host_sys::guest::ParkedDaemonProbe;

/// [`ParkedDaemonProbe`] with both of its answers seeded: what makes a holder idle is the daemon's own account of
/// itself, and these suites have no daemon. The default proves nothing, so every holder is executing.
#[derive(Default)]
pub struct FakeProbe {
    /// The run the daemon says it is parked on.
    pub parked: Option<String>,
    /// The run the daemon's host state records.
    pub recorded: Option<String>,
    /// When set, asking the daemon refuses with it.
    pub refusal: Option<Refusal>,
}

impl FakeProbe {
    /// A daemon that is parked on `parked` and whose host state records `recorded`.
    pub fn new(parked: Option<&str>, recorded: Option<&str>) -> Self {
        Self {
            parked: parked.map(str::to_owned),
            recorded: recorded.map(str::to_owned),
            refusal: None,
        }
    }
}

#[async_trait]
impl ParkedDaemonProbe for FakeProbe {
    async fn parked_daemon_run(&self, _ctx: &Ctx, _worker: &str) -> Result<Option<String>, Refusal> {
        match &self.refusal {
            Some(refusal) => Err(refusal.clone()),
            None => Ok(self.parked.clone()),
        }
    }

    fn recorded_daemon_run(&self, _worker: &str) -> Option<String> {
        self.recorded.clone()
    }
}
