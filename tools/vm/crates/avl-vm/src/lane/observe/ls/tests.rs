use avl_host_sys::{Captured, Ctx};
use avl_host_testkit::agent::active_reply;
use avl_host_testkit::{answer_guest, outcome_of, refusal, said};
use clap::Parser;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::{LS_ENTRY_LIMIT, LsArgs, command_ls};
use crate::lane::observe::testing::Fixture;

/// `ls` as `vm` flattens it.
#[derive(Debug, Parser)]
struct Ls {
    #[command(flatten)]
    args: LsArgs,
}

fn parse(words: &[&str]) -> Result<LsArgs, clap::Error> {
    Ls::try_parse_from(std::iter::once("ls").chain(words.iter().copied())).map(|ls| ls.args)
}

fn args(directory: &str, depth: u8) -> LsArgs {
    LsArgs {
        directory: directory.to_owned(),
        depth,
    }
}

/// The contract: `exec` refuses mid-run, `ls` deliberately does not - one fixed read-only argv over the non-GUI
/// channel is what lets a failing lane be diagnosed while the daemon holds the warm IDE.
#[tokio::test]
async fn ls_is_deliberately_allowed_while_a_run_is_active() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    let channel = fixture.channel("air-docker-1");
    channel.answer(answer_guest(vec![
        (" active ", active_reply(Some("run-1"))),
        ("/usr/bin/find", said("/tmp\n/tmp/a\r\n/tmp/b\n")),
    ]));
    let outcome = outcome_of(command_ls(&Ctx::background(), &fixture.manager, args("/tmp", 2), Some(&receipt)).await);
    assert_eq!(
        outcome.data,
        json!({
            "worker": "air-docker-1",
            "directory": "/tmp",
            "depth": 2,
            "entries": ["/tmp", "/tmp/a", "/tmp/b"],
            "truncated": false,
            "partial": false,
        })
    );
    assert!(
        channel.saw_call_containing("/usr/bin/sudo -H -u admin /usr/bin/find /tmp -maxdepth 2"),
        "the listing did not use the fixed find argv: {:?}",
        channel.lines()
    );
    // Allowed means the run slot was never even consulted: no argv reached for the guest agent.
    assert!(
        !channel.saw_call_containing(&fixture.settings.vm_agent),
        "ls consulted the run supervisor: {:?}",
        channel.lines()
    );
}

/// A listing cut off at the cap is not the same fact as an empty directory, so truncation is named on both of its
/// paths: more entries than the cap, and a stdout capture that was itself truncated.
#[tokio::test]
async fn ls_names_truncation_rather_than_hiding_it() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    let channel = fixture.channel("air-docker-1");

    let over_cap = vec!["/tmp/entry-xxx"; LS_ENTRY_LIMIT + 1].join("\n");
    channel.answer(answer_guest(vec![("/usr/bin/find", said(&over_cap))]));
    let outcome = outcome_of(command_ls(&Ctx::background(), &fixture.manager, args("/tmp", 1), Some(&receipt)).await);
    assert_eq!(outcome.data["entries"].as_array().map(Vec::len), Some(LS_ENTRY_LIMIT));
    assert_eq!(outcome.data["truncated"], json!(true));

    channel.answer(answer_guest(vec![(
        "/usr/bin/find",
        Captured {
            stdout: "/tmp/a\n".to_owned(),
            stdout_truncated: true,
            ..Captured::default()
        },
    )]));
    let outcome = outcome_of(command_ls(&Ctx::background(), &fixture.manager, args("/tmp", 1), Some(&receipt)).await);
    assert_eq!(
        outcome.data["truncated"],
        json!(true),
        "a truncated stdout capture was not named truncated"
    );
}

/// `find` exits 1 when one entry is unreadable and still lists the rest. The listing is the answer, named partial;
/// an exit of 1 with nothing listed is still a refusal, because then the directory itself was unreadable.
#[tokio::test]
async fn ls_keeps_what_find_listed_before_an_unreadable_entry() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    let channel = fixture.channel("air-docker-1");
    channel.answer(answer_guest(vec![(
        "/usr/bin/find",
        Captured {
            exit_code: 1,
            stdout: "/tmp\n/tmp/a\n".to_owned(),
            stderr: "find: '/tmp/b': Permission denied\n".to_owned(),
            ..Captured::default()
        },
    )]));
    let outcome = outcome_of(command_ls(&Ctx::background(), &fixture.manager, args("/tmp", 3), Some(&receipt)).await);
    assert_eq!(outcome.data["entries"], json!(["/tmp", "/tmp/a"]));
    assert_eq!(outcome.data["partial"], json!(true));
    assert!(
        outcome.text.ends_with("(partial: find could not read some entries)"),
        "the text listing does not name the gap: {:?}",
        outcome.text
    );

    channel.answer(answer_guest(vec![(
        "/usr/bin/find",
        Captured {
            exit_code: 1,
            stderr: "find: '/tmp': Permission denied\n".to_owned(),
            ..Captured::default()
        },
    )]));
    let refusal = refusal(command_ls(&Ctx::background(), &fixture.manager, args("/tmp", 1), Some(&receipt)).await);
    assert_eq!(refusal.code, "ls_failed");
    // Guest output is withheld from the refusal.
    assert!(!refusal.message.contains("Permission denied"), "{}", refusal.message);
}

/// Deliberately stricter than a number parser, in the fail-closed direction: `Number("0x2")` and `Number("2.0")`
/// are 2 in JavaScript, and `u8::from_str("+2")` is 2 in Rust; every one of them is refused.
#[test]
fn javascript_number_spellings_are_refused_rather_than_reinterpreted() {
    for spelling in ["0x2", "2.0", "1e1", "0b10", "+2", "", "Infinity", "two", "0", "5", "256"] {
        assert!(parse(&["/tmp", "--depth", spelling]).is_err(), "--depth {spelling:?} was accepted");
    }
    // A padded value from a shell here-doc is a real spelling and stays accepted.
    assert_eq!(
        parse(&["/tmp", "--depth", " 2 "]).map_err(|error| error.to_string()),
        Ok(args("/tmp", 2))
    );
    // One level is the default.
    assert_eq!(parse(&["/tmp"]).map_err(|error| error.to_string()), Ok(args("/tmp", 1)));
}

#[test]
fn ls_requires_an_absolute_guest_directory() {
    let error = parse(&["tmp"]).expect_err("a relative directory was accepted");
    assert!(error.to_string().contains("absolute"), "{error}");
    assert!(parse(&[]).is_err(), "no directory was accepted");
    assert!(parse(&["/tmp", "/var"]).is_err(), "a second directory was accepted");
}
