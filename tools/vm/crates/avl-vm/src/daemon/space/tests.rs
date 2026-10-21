use avl_host_sys::Ctx;
use avl_host_sys::guest::user_argv;
use avl_host_testkit::{answer_exit, answer_text, refusal};
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
        .require_guest_free_space(&fixture.host.guest(&ctx, channel.as_ref()))
        .await
}

// Above the floor the whole check costs one df and deletes nothing, also with little room left.
#[tokio::test]
async fn above_the_floor_the_check_costs_one_df_and_deletes_nothing() {
    for free_kib in [PLENTIFUL_DF.to_owned(), df_with_free_kib("10485760")] {
        let fixture = Fixture::new().await;
        fixture.on("df", answer_text(&free_kib));
        require(&fixture).await.unwrap();
        let channel = fixture.channel();
        assert_eq!(channel.calls_containing("/bin/df").len(), 1);
        assert!(channel.calls_containing("/bin/ls").is_empty());
        assert!(channel.calls_containing("/bin/rm").is_empty());
    }
}

// Below the floor the run is refused with the command that finds what holds the space, and the check deletes nothing.
#[tokio::test]
async fn below_the_floor_the_run_is_refused_with_the_command_that_finds_the_culprit() {
    let fixture = Fixture::new().await;
    // 1 GiB free: below the 5 GiB floor.
    fixture.on("df", answer_text(df_with_free_kib("1048576")));
    let failure = refusal(require(&fixture).await);
    // A resource the run cannot reach, not a wire the controller cannot read.
    assert_eq!(failure.code, "guest_disk_full");
    assert_eq!(failure.exit, Exit::UNAVAILABLE);
    assert!(
        failure.message.contains("vm.cmd exec") && failure.message.contains("du -sh"),
        "the refusal hands the reader a command, said {:?}",
        failure.message
    );
    assert!(fixture.channel().calls_containing("/bin/rm").is_empty());
}

// An unparsable df never blocks the run: `None` means "do not know".
#[tokio::test]
async fn an_unparsable_df_never_blocks_the_run() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_text("garbage"));
    require(&fixture).await.unwrap();
    assert!(fixture.channel().calls_containing("/bin/rm").is_empty());
}

// The removal of the retired roots is one `rm -rf` of `daemon-runtime`, `out` and `build-download` under `<vmData>`, as
// the guest user who owns them. A missing directory is no error, because `rm -rf` answers 0 for it. A failed removal is
// a note that names the roots and the worker, and not a refusal.
#[tokio::test]
async fn the_retired_roots_go_with_one_rm_and_a_failure_is_a_note() {
    assert_eq!(RETIRED_ROOTS, ["daemon-runtime", "out", "build-download"]);
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let channel = fixture.channel();
    let guest = fixture.host.guest(&ctx, channel.as_ref());
    fixture.host.remove_retired_roots(&guest, None).await;
    let roots: Vec<String> = RETIRED_ROOTS
        .iter()
        .map(|root| guest_join(&fixture.settings.vm_data, root))
        .collect();
    let mut argv = words(["/bin/rm", "-rf"]);
    argv.extend_from_slice(&roots);
    assert_eq!(channel.calls_containing("/bin/rm"), [user_argv(&fixture.settings, &argv).join(" ")]);
    assert!(roots.iter().all(|root| root.starts_with("/vm/data/")), "{roots:?}");

    fixture.on("rm", answer_exit(1));
    fixture.stderr.clear();
    fixture.host.remove_retired_roots(&guest, None).await;
    let notes = fixture.stderr.text();
    let fragments = roots
        .iter()
        .map(String::as_str)
        .chain([fixture.worker.as_str(), "keeps the retired roots"]);
    for fragment in fragments {
        assert!(notes.contains(fragment), "{fragment} is not in\n{notes}");
    }
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
