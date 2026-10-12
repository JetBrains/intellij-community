use std::path::{Path, PathBuf};

use avl_base::{Backend, Exit};
use avl_host_sys::Ctx;
use avl_host_testkit::agent::{agent_envelope, answer_pull};
use avl_host_testkit::{outcome_of, refusal};
use avl_testkit::tartfake::Answer;
use avl_wire::pull::FileReceipt;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::{Transfer, command_pull, pull_guest_file};
use crate::lane::observe::testing::{Fixture, parallels_fixture};
use avl_base::RefusalExt;

/// Bytes that survive no text channel: every value 0..255, several times over, so a decode that mangled anything -
/// a newline translation, a truncation, an eager end of stream - cannot produce it.
fn corpus() -> Vec<u8> {
    (0..4).flat_map(|_| 0..=u8::MAX).collect()
}

/// Re-wraps one long base64 line the way encoders on the wire do: 76-column lines, mostly `\n` with some `\r\n`
/// mixed in, and a trailing line break.
fn wrap(encoded: &str) -> String {
    encoded
        .as_bytes()
        .chunks(76)
        .enumerate()
        .map(|(index, line)| {
            let ending = if index % 3 == 2 { "\r\n" } else { "\n" };
            format!("{}{ending}", String::from_utf8_lossy(line))
        })
        .collect()
}

/// A fresh private directory with one destination path inside it, so the cleanup checks can list it afterwards.
fn destination() -> (TempDir, PathBuf) {
    let directory = tempfile::tempdir().expect("a destination directory");
    let destination = directory.path().join("artifact.bin");
    (directory, destination)
}

fn assert_no_temporaries_left(directory: &Path) {
    let left: Vec<String> = std::fs::read_dir(directory)
        .expect("the directory is readable")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .filter(|name| name.starts_with(".pull-"))
        .collect();
    assert!(left.is_empty(), "pull temporaries were left behind: {left:?}");
}

async fn pull(fixture: &Fixture, source: &str, destination: &Path) -> Result<u64, avl_base::Refusal> {
    pull_from(fixture, "air-docker-1", source, destination).await
}

async fn pull_from(fixture: &Fixture, worker: &str, source: &str, destination: &Path) -> Result<u64, avl_base::Refusal> {
    pull_guest_file(&Ctx::background(), &fixture.manager, worker, source, destination).await
}

// The channels that ADR 0182 and 0183 measured byte-clean move the bytes as they are; Parallels keeps base64.
#[test]
fn a_pull_moves_raw_bytes_on_tart_and_docker_and_base64_on_parallels() {
    assert_eq!(
        [Backend::Tart, Backend::Docker, Backend::Parallels].map(Transfer::of),
        [Transfer::Raw, Transfer::Raw, Transfer::Base64]
    );
}

#[tokio::test]
async fn pull_publishes_the_guest_bytes_atomically() {
    let fixture = Fixture::docker();
    let corpus = corpus();
    answer_pull(&fixture.fake, &corpus);
    let (directory, destination) = destination();
    let size = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect("the pull succeeds");
    assert_eq!(size, u64::try_from(corpus.len()).expect("a length fits"));
    assert!(
        std::fs::read(&destination).expect("the artifact is readable") == corpus,
        "the published bytes are not the guest's bytes"
    );
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = std::fs::metadata(&destination).expect("the artifact exists").permissions().mode() & 0o777;
        assert_eq!(mode, 0o600, "the artifact is mode {mode:o}");
    }
    let artifact = avl_host_sys::private::open_no_follow(&destination).expect("the artifact opens");
    let metadata = artifact.metadata().expect("the artifact has metadata");
    let protection = avl_host_sys::private::protection(&artifact, &metadata).expect("the artifact has a protection");
    assert!(protection.is_exactly(0o600) && protection.owned_by_caller(), "{protection:?}");
    assert_no_temporaries_left(directory.path());
    // The guest reads the file as the worker user, through the agent's own verb.
    let read = format!(
        "exec air-docker-1 /usr/bin/sudo -H -u admin {} read-file /guest/artifact.bin",
        fixture.settings.vm_agent
    );
    assert!(
        fixture.fake.saw_call_containing(&read),
        "the guest read did not use the fixed read-file argv: {:?}",
        fixture.fake.calls()
    );
}

/// The receipt is the proof of a clean transfer: bytes that are not the ones the guest named are refused, and nothing
/// is published. A read whose receipt is missing is refused too.
#[tokio::test]
async fn pull_refuses_bytes_the_receipt_does_not_name() {
    let fixture = Fixture::docker();
    fixture.fake.answer(Answer::ReadFile, "changed on the way");
    let sent = serde_json::to_value(FileReceipt::of(b"what the guest sent")).unwrap();
    fixture
        .fake
        .answer(Answer::ReadFileReceipt, agent_envelope("read-file", &sent) + "\n");
    let (directory, destination) = destination();
    let refusal = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect_err("bytes that the receipt does not name were published");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("pull_digest_mismatch", Exit::DATA_ERR));
    assert!(
        refusal.message.contains("18 bytes") && refusal.message.contains("19 bytes"),
        "{}",
        refusal.message
    );
    assert!(!destination.exists(), "a mismatched pull left a destination behind");
    assert_no_temporaries_left(directory.path());

    fixture.fake.forget(Answer::ReadFileReceipt);
    let refusal = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect_err("a read without a receipt was published");
    assert_eq!(refusal.code, "pull_failed");
    assert!(refusal.message.contains("named no receipt"), "{}", refusal.message);
    assert!(!destination.exists());
    assert_no_temporaries_left(directory.path());

    // An agent older than this controller has no read-file, and its usage exit says so.
    fixture.fake.forget(Answer::ReadFile);
    fixture.fake.answer(Answer::ExecExit, "64");
    let refusal = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect_err("a usage exit was published");
    assert_eq!(refusal.code, "pull_failed");
    assert!(refusal.message.contains("older than this controller"), "{}", refusal.message);
    assert_no_temporaries_left(directory.path());
}

/// The in-process decoder accepts what BSD `base64 -D` accepts - wrapped lines and CRLF included - byte for byte.
/// The `base64` crate refuses line breaks on its own, which is what this pins. A Parallels pull is base64.
#[tokio::test]
async fn pull_decodes_a_wrapped_base64_stream_in_process() {
    let (fixture, _) = parallels_fixture();
    let corpus = corpus();
    let wrapped = wrap(&STANDARD.encode(&corpus));
    assert!(wrapped.contains("\r\n") && wrapped.matches('\n').count() > 10);
    fixture.fake.answer(Answer::ExecStdout, wrapped);
    let (directory, destination) = destination();
    pull_from(&fixture, "macOS", "/guest/artifact.bin", &destination)
        .await
        .expect("the pull succeeds");
    assert!(
        std::fs::read(&destination).expect("the artifact is readable") == corpus,
        "a newline-wrapped stream did not decode to the guest's bytes"
    );
    assert_no_temporaries_left(directory.path());
}

#[tokio::test]
async fn pull_refuses_to_overwrite_an_existing_artifact() {
    let fixture = Fixture::docker();
    answer_pull(&fixture.fake, b"new");
    let (directory, destination) = destination();
    std::fs::write(&destination, "already published").expect("the earlier artifact is written");
    let refusal = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect_err("an existing destination was overwritten");
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("pull_destination_exists", Exit::CANT_CREATE)
    );
    // Refused, not replaced: the no-clobber move is what makes overwriting impossible rather than merely checked.
    assert_eq!(
        std::fs::read_to_string(&destination).expect("the artifact is readable"),
        "already published"
    );
    assert_no_temporaries_left(directory.path());
}

#[tokio::test]
async fn pull_refuses_a_destination_it_cannot_link_safely() {
    let fixture = Fixture::docker();
    answer_pull(&fixture.fake, b"payload");
    let (directory, _) = destination();
    // A destination the filesystem itself refuses (a name past NAME_MAX): the move fails with something other than
    // "exists", which must surface as `pull_destination_unsafe` and not as the overwrite refusal.
    let destination = directory.path().join("x".repeat(300));
    let refusal = pull(&fixture, "/guest/artifact.bin", &destination)
        .await
        .expect_err("an unlinkable destination was accepted");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("pull_destination_unsafe", Exit::NO_PERM));
    // The cause travels: a planted symlink and a full disk must not read identically.
    assert!(
        refusal.details().as_ref().is_some_and(|details| details["cause"].is_string()),
        "{:?}",
        refusal.details()
    );
    assert_no_temporaries_left(directory.path());
}

#[tokio::test]
async fn pull_refuses_a_failed_guest_read_and_cleans_up() {
    let fixture = Fixture::docker();
    fixture.fake.answer(Answer::ExecExit, "1");
    fixture
        .fake
        .answer(Answer::ExecStderr, "base64: /guest/missing.bin: No such file or directory\n");
    let (directory, destination) = destination();
    let refusal = pull(&fixture, "/guest/missing.bin", &destination)
        .await
        .expect_err("a failed guest read was accepted");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("pull_failed", Exit::FAILURE));
    // Withheld, per the module contract: guest stderr never reaches a refusal.
    assert!(!refusal.message.contains("No such file"), "{}", refusal.message);
    assert!(!destination.exists(), "a failed pull left a destination behind");
    assert_no_temporaries_left(directory.path());
}

#[tokio::test]
async fn pull_refuses_a_stream_that_is_not_base64() {
    let (fixture, _) = parallels_fixture();
    fixture.fake.answer(Answer::ExecStdout, "this is not base64!\n");
    let (directory, destination) = destination();
    let refusal = pull_from(&fixture, "macOS", "/guest/artifact.bin", &destination)
        .await
        .expect_err("an undecodable stream was accepted");
    assert_eq!(refusal.code, "pull_failed");
    assert!(!destination.exists(), "an undecodable pull left a destination behind");
    assert_no_temporaries_left(directory.path());
}

/// The command form: the destination is resolved inside the worker's own artifact directory, and the reply names
/// what landed where.
#[tokio::test]
async fn the_pull_command_publishes_into_the_workers_artifact_directory() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    answer_pull(&fixture.fake, b"evidence");
    let outcome = outcome_of(
        command_pull(
            &Ctx::background(),
            &fixture.manager,
            "/guest/log.txt".to_owned(),
            "logs/log.txt".to_owned(),
            Some(&receipt),
        )
        .await,
    );
    let destination = outcome.data["destination"].as_str().expect("a destination").to_owned();
    // A Docker worker keys its directory apart, so a container cannot share state with a Tart slot.
    assert!(destination.ends_with("artifacts/docker-air-docker-1/logs/log.txt"), "{destination}");
    assert_eq!(
        outcome.data,
        json!({
            "worker": "air-docker-1",
            "source": "/guest/log.txt",
            "destination": destination,
            "bytes": 8,
        })
    );
    assert_eq!(outcome.text, destination);

    // A destination outside the artifact directory is refused before anything reaches the guest.
    let calls = fixture.fake.calls().len();
    let refusal = refusal(
        command_pull(
            &Ctx::background(),
            &fixture.manager,
            "/guest/log.txt".to_owned(),
            "../escape.txt".to_owned(),
            Some(&receipt),
        )
        .await,
    );
    assert_eq!(refusal.code, "pull_destination_unsafe");
    assert_eq!(fixture.fake.calls().len(), calls);
}
