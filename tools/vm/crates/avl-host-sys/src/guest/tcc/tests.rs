#[cfg(unix)]
use avl_base::{GuestOs, RefusalExt};

use super::*;
#[cfg(unix)]
use crate::guest::testing::{FakeChannel, Host, failed, has, said};
#[cfg(unix)]
use crate::proc::Captured;

// `auth_value = 0` is TCC's "denied", and macOS writes one on its own whenever a program asks for a permission it does
// not have. Counting it would make the gate unsatisfiable on any worker that has booted once.
#[test]
fn tcc_decision_query_counts_everything_but_an_explicit_denial() {
    let query = TCC_DECISION_QUERY.as_str();
    assert!(query.contains("AND auth_value != 0"), "{query}");
    for table in ["active_policy", "policies", "access_overrides"] {
        assert!(query.contains(&format!("FROM {table}")), "{query}");
    }
    for service in SENSITIVE_TCC_SERVICES {
        assert!(query.contains(&format!("'{service}'")), "{service} is not in the query");
    }
}

// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn require_clean_worker_tcc_passes_only_for_two_zero_counts() {
    let host = Host::new(GuestOs::Macos);
    let channel = FakeChannel::answering("air-macos-1", |argv| {
        Ok(if has(argv, "/usr/bin/sqlite3") {
            said("0\n")
        } else {
            Captured::default()
        })
    });
    host.guest(&channel).require_clean_worker_tcc().await.unwrap();
    // Both databases: the system one and the worker user's own, read-only in both senses.
    let queries: Vec<_> = channel
        .calls()
        .into_iter()
        .filter(|call| has(&call.argv, "/usr/bin/sqlite3"))
        .collect();
    assert_eq!(queries.len(), 2, "{:?}", channel.lines());
    let user_database = format!(
        "file:/Users/{}/Library/Application Support/com.apple.TCC/TCC.db?mode=ro",
        host.settings.vm_user
    );
    assert!(has(&queries[1].argv, &user_database), "{:?}", queries[1].argv);
    assert!(has(&queries[0].argv, "-readonly"));
}

// Fail-closed in every direction: a gate that warns is not a gate.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn require_clean_worker_tcc_refuses_every_way_of_not_knowing() {
    let host = Host::new(GuestOs::Macos);
    let query = |answer: Captured| {
        move |argv: &[String]| {
            Ok(if has(argv, "/usr/bin/sqlite3") {
                answer.clone()
            } else {
                Captured::default()
            })
        }
    };
    let cases: Vec<(&str, FakeChannel)> = vec![
        (
            "the database is absent",
            FakeChannel::answering("air-macos-1", |_| Ok(failed(1, ""))),
        ),
        ("the query failed", FakeChannel::answering("air-macos-1", query(failed(1, "")))),
        (
            "the count is not a number",
            FakeChannel::answering("air-macos-1", query(said("Error: database is locked\n"))),
        ),
        ("the count is nonzero", FakeChannel::answering("air-macos-1", query(said("1\n")))),
        // A truncated answer is an answer nobody read the end of, which cannot be parsed as a count.
        (
            "the answer was truncated",
            FakeChannel::answering(
                "air-macos-1",
                query(Captured {
                    stdout: "0\n".to_owned(),
                    stdout_truncated: true,
                    ..Captured::default()
                }),
            ),
        ),
        (
            "the count overflows",
            FakeChannel::answering("air-macos-1", query(said("99999999999999999999999999\n"))),
        ),
        ("the count is signed", FakeChannel::answering("air-macos-1", query(said("-0\n")))),
        (
            "the channel failed",
            FakeChannel::answering("air-macos-1", |_| Err(Refusal::internal("no channel"))),
        ),
    ];
    for (name, channel) in cases {
        let refusal = host.guest(&channel).require_clean_worker_tcc().await.unwrap_err();
        assert_eq!(refusal.code, "worker_tcc_admission_failed", "{name}");
        assert_eq!(refusal.message, "worker air-macos-1 did not pass fail-closed TCC admission");
    }
}
