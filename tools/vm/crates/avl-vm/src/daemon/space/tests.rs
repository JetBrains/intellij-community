use avl_host_sys::{Captured, Ctx};
use avl_host_testkit::{answer_exit, answer_text, handler, refusal, said};
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::{PLENTIFUL_DF, df_with_free_kib};

// The fourth column of df's last line, and `None` for everything else - which every caller treats as "do not block
// the run".
#[test]
fn parse_guest_free_bytes_reads_the_last_line_fourth_column() {
    assert_eq!(parse_guest_free_bytes(&df_with_free_kib("1024")), Some(1024 * 1024));
    let multiline = "Filesystem 1024-blocks Used Available\n/dev/old 9 9 9\n/dev/disk1 999 1 2048 1% /vm\n";
    assert_eq!(parse_guest_free_bytes(multiline), Some(2048 * 1024), "only the last line counts");
    for (name, reply) in [
        ("a header-only reply", "Filesystem 1024-blocks Used Available\n".to_owned()),
        ("a truncated line", "Filesystem\n/dev/disk1 999 1\n".to_owned()),
        ("an empty reply", String::new()),
        ("a non-integer column", df_with_free_kib("lots")),
        ("a negative column", df_with_free_kib("-5")),
        ("a float", df_with_free_kib("8.0")),
        ("an exponent", df_with_free_kib("1e3")),
        ("a signed column", df_with_free_kib("+5")),
    ] {
        assert_eq!(parse_guest_free_bytes(&reply), None, "{name}");
    }
    assert_eq!(parse_guest_free_bytes(&df_with_free_kib("0")), Some(0), "zero free is an answer");
}

async fn require(fixture: &Fixture) -> Result<(), Refusal> {
    let ctx = Ctx::background();
    let channel = fixture.channel();
    fixture
        .host
        .require_guest_free_space(&fixture.host.guest(&ctx, channel.as_ref()), None)
        .await
}

// Above the soft threshold the whole check costs one df and deletes nothing.
#[tokio::test]
async fn above_the_soft_threshold_costs_one_df_and_deletes_nothing() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    require(&fixture).await.unwrap();
    let channel = fixture.channel();
    assert_eq!(channel.calls_containing("/bin/df").len(), 1);
    assert!(channel.calls_containing("/bin/ls").is_empty());
    assert!(channel.calls_containing("/bin/rm").is_empty());
}

// Below the soft threshold retention runs keeping two: the two newest entries of each tree survive.
#[tokio::test]
async fn below_the_soft_threshold_prunes_retaining_the_two_newest() {
    let fixture = Fixture::new().await;
    // 10 GiB free: below the 20 GiB soft threshold, above the 5 GiB floor.
    fixture.on("df", answer_text(df_with_free_kib("10485760")));
    fixture.on(
        "ls",
        handler(|argv, _| {
            if argv[2].contains("teamcity-artifacts-for-publish") {
                return Ok(said("newest\nsecond\nthird\nfourth\n"));
            }
            // The allure tree does not exist yet: the same answer as nothing to prune.
            Ok(Captured {
                exit_code: 1,
                ..Captured::default()
            })
        }),
    );
    require(&fixture).await.unwrap();
    let removals = fixture.channel().calls_containing("/bin/rm -rf");
    assert_eq!(removals.len(), 1, "one chunked rm, saw {removals:?}");
    let removal = &removals[0];
    assert!(
        !removal.contains("newest") && !removal.contains("second"),
        "the two newest survive: {removal}"
    );
    assert!(
        removal.contains(
            "/vm/data/out/ide-tests/tests/teamcity-artifacts-for-publish/third /vm/data/out/ide-tests/tests/\
             teamcity-artifacts-for-publish/fourth"
        ),
        "the rm names every doomed path explicitly: {removal}"
    );
}

// At the floor a post-mortem is a luxury: retention keeps nothing, and a disk still full afterwards refuses the run
// with the command that finds the culprit.
#[tokio::test]
async fn at_the_floor_retention_keeps_nothing_and_refuses_when_still_full() {
    let fixture = Fixture::new().await;
    // 1 GiB free, before and after: below the 5 GiB floor.
    fixture.on("df", answer_text(df_with_free_kib("1048576")));
    fixture.on("ls", answer_text("only\n"));
    let failure = refusal(require(&fixture).await);
    // A resource the run cannot reach, not a wire the controller cannot read.
    assert_eq!(failure.code, "guest_disk_full");
    assert_eq!(failure.exit, Exit::UNAVAILABLE);
    assert!(
        failure.message.contains("prlctl exec") && failure.message.contains("du -sh"),
        "the refusal hands the reader a command, said {:?}",
        failure.message
    );
    let removals = fixture.channel().calls_containing("/bin/rm -rf");
    assert_eq!(removals.len(), 1);
    assert!(removals[0].contains("only"), "even the newest entry goes");
}

// An unparsable df never blocks the run: `None` means "do not know".
#[tokio::test]
async fn an_unparsable_df_never_blocks_the_run() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_text("garbage"));
    require(&fixture).await.unwrap();
    assert!(fixture.channel().calls_containing("/bin/rm").is_empty());
}

// The removal argv is chunked at fifty paths.
#[tokio::test]
async fn pruning_chunks_the_removal_argv_at_fifty() {
    let fixture = Fixture::new().await;
    let listing: String = (0..120).map(|index| format!("entry-{index:03}\n")).collect();
    fixture.on(
        "ls",
        handler(move |argv, _| {
            if argv[2].contains("allure") {
                return Ok(Captured {
                    exit_code: 1,
                    ..Captured::default()
                });
            }
            Ok(said(&listing))
        }),
    );
    let ctx = Ctx::background();
    let channel = fixture.channel();
    let doomed = fixture
        .host
        .prune_guest_artifacts(&fixture.host.guest(&ctx, channel.as_ref()), 2, None)
        .await
        .unwrap();
    assert_eq!(doomed, 118, "120 entries retaining 2 dooms 118");
    let removals = channel.calls_containing("/bin/rm -rf");
    let sizes: Vec<usize> = removals.iter().map(|removal| removal.matches("/vm/data/").count()).collect();
    assert_eq!(sizes, [50, 50, 18]);
}

// A df the guest refuses to answer propagates: it is the guest's error, and nothing swallows it.
#[tokio::test]
async fn a_df_the_guest_refuses_propagates() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_exit(1));
    let ctx = Ctx::background();
    let channel = fixture.channel();
    let failure = refusal(guest_free_bytes(&fixture.host.guest(&ctx, channel.as_ref())).await);
    assert_eq!(failure.code, "subprocess_failed");
}
