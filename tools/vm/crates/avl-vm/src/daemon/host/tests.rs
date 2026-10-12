use std::time::Duration;

use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

// The watchdog policy is the settings' budgets, read once by `Config::load`, unless the run names its own.
#[tokio::test]
async fn the_watchdog_policy_is_the_settings_unless_the_run_names_its_own() {
    let fixture = Fixture::new().await;
    assert_eq!(
        fixture.host.watchdog_policy(None, None),
        WatchdogPolicy {
            active_execution: Duration::from_mins(30),
            progress_gap: Duration::from_secs(300),
        }
    );
    let tuned = Fixture::with_environment(&[("AIR_VM_DAEMON_EXECUTION_TIMEOUT", "60"), ("AIR_VM_DAEMON_PROGRESS_TIMEOUT", "120")]).await;
    assert_eq!(
        tuned.host.watchdog_policy(None, None),
        WatchdogPolicy {
            active_execution: Duration::from_secs(60),
            progress_gap: Duration::from_secs(120),
        }
    );
    // A requested budget wins over the settings.
    assert_eq!(
        tuned.host.watchdog_policy(None, Some(Duration::from_secs(9))).progress_gap,
        Duration::from_secs(9)
    );
}
