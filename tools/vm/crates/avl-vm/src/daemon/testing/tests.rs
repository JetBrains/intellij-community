use std::time::Duration;

use avl_base::format::words;
use pretty_assertions::assert_eq;

use avl_host_sys::{Ctx, SpawnOptions};
use avl_host_testkit::{answer_exit, answer_text};

use crate::daemon::fixture::Fixture;

// The shared fixture routes the agent by verb and everything else by basename, per worker, through the manager.
#[tokio::test]
async fn the_scripted_guest_routes_by_verb_and_records_per_worker() {
    let fixture = Fixture::new().await;
    fixture.on("gc", answer_text("collected"));
    fixture.on("df", answer_exit(3));
    let channel = fixture.manager.channel(&fixture.worker);
    let ctx = Ctx::background();
    let options = SpawnOptions::within(Duration::from_mins(1));
    let agent = fixture.settings.vm_agent.clone();
    let gc = channel
        .exec(&ctx, &words(["/usr/bin/sudo", "-H", "-u", "admin", &agent, "gc"]), &options)
        .await
        .unwrap();
    assert_eq!(gc.stdout, "collected");
    let df = channel.exec(&ctx, &words(["/bin/df", "-k"]), &options).await.unwrap();
    assert_eq!(df.exit_code, 3);
    assert_eq!(fixture.channel().calls().len(), 2);
    assert_eq!(fixture.channel().calls_containing("/bin/df"), ["/bin/df -k"]);
    let lease = fixture.lease_receipt();
    assert!(lease.is_file());
}
