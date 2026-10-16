use std::fs;
use std::path::Path;

use avl_base::{Environment, Selection};
use avl_wire::report::{decode_flake_summary, decode_shard_verdict};
use pretty_assertions::assert_eq;

use super::*;
use crate::aggregate::tests::dead_shard;
use crate::aggregate::{flake_summary, merge_shard_verdict};

/// Settings whose runtime root is a fresh directory; the publish reads nothing else.
fn settings(root: &Path) -> Config {
    let environment = Environment::from_pairs([
        ("HOME", root.join("home").to_str().unwrap()),
        ("TART_BIN", "tart"),
        ("AIR_VM_RUNTIME_ROOT", root.join("runtime").to_str().unwrap()),
    ]);
    Config::load(
        avl_base::HostFacts::without_memory(),
        Selection::DEFAULT,
        &environment,
        Path::new("/repo/scripts"),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

fn dead_verdict(message: &str) -> ShardVerdict {
    merge_shard_verdict(&[dead_shard(1, "air-linux-1", message)])
}

/// The bytes a reader diffs: two-space indentation, one trailing newline, and a message carrying `<`, `>` and `&`
/// as themselves.
#[test]
fn a_shard_verdict_is_written_with_the_bytes_a_reader_diffs() {
    let root = tempfile::tempdir().unwrap();
    let settings = settings(root.path());
    let verdict = dead_verdict("<init> & <clinit> failed");
    let path = persist_shard_verdict(&settings, "shard-1-abcd", &verdict).unwrap();
    assert_eq!(
        path,
        settings.runtime_root.join("aggregates").join("shard-1-abcd").join("shard.json")
    );
    let text = fs::read_to_string(&path).unwrap();
    assert!(text.ends_with('\n') && !text.ends_with("\n\n"), "exactly one trailing newline");
    assert!(text.contains("\n  \"kind\": \"shard\""), "{text}");
    assert!(text.contains("<init> & <clinit>") && !text.contains("\\u"), "{text}");
    assert_eq!(decode_shard_verdict(text.as_bytes()).unwrap(), verdict);
}

/// A flake summary is one run's merged answer over several workers, exactly as a shard verdict is, so it lives in
/// the same tree - under its own name, because one run can hold both.
#[test]
fn a_flake_summary_lives_beside_a_shard_verdict_under_its_own_name() {
    let root = tempfile::tempdir().unwrap();
    let settings = settings(root.path());
    let summary = flake_summary(&[]);
    let path = persist_flake_summary(&settings, "flake-1-abcd", &summary).unwrap();
    assert_eq!(
        path,
        settings.runtime_root.join("aggregates").join("flake-1-abcd").join("flake.json")
    );
    let text = fs::read_to_string(&path).unwrap();
    assert!(text.contains("\n  \"kind\": \"flake\""), "{text}");
    assert_eq!(decode_flake_summary(text.as_bytes()).unwrap(), summary);
    // One run id, both documents: nothing about the naming makes the two collide.
    let verdict = persist_shard_verdict(&settings, "flake-1-abcd", &merge_shard_verdict(&[])).unwrap();
    assert_ne!(verdict, path);
}

/// Outside every worker's `reports` directory, which the balancer scans for durations; private, because an
/// aggregate carries failure messages from the product under test.
#[test]
fn an_aggregate_is_private_and_outside_every_workers_reports() {
    let root = tempfile::tempdir().unwrap();
    let settings = settings(root.path());
    let path = persist_shard_verdict(&settings, "shard-1-abcd", &merge_shard_verdict(&[])).unwrap();
    let relative = path.strip_prefix(&settings.runtime_root).unwrap();
    assert!(
        !relative
            .components()
            .any(|component| component.as_os_str() == "workers" || component.as_os_str() == "reports"),
        "{}",
        path.display()
    );
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = |path: &Path| fs::metadata(path).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode(&path), 0o600);
        assert_eq!(mode(path.parent().unwrap()), 0o700);
    }
    // The run id is a path component, so it is a name like every other.
    assert_eq!(
        persist_shard_verdict(&settings, "../escape", &merge_shard_verdict(&[]))
            .unwrap_err()
            .code,
        "unsafe_name"
    );
}

/// A second document under one run id is a collision, not an update: the first stays exactly as it was, and the
/// refused publish leaves no staging file beside it.
#[test]
fn a_second_aggregate_under_one_run_id_is_refused_with_the_first_intact() {
    let root = tempfile::tempdir().unwrap();
    let settings = settings(root.path());
    let path = persist_shard_verdict(&settings, "shard-1-abcd", &dead_verdict("the first answer")).unwrap();
    let refusal = persist_shard_verdict(&settings, "shard-1-abcd", &dead_verdict("the second answer")).unwrap_err();
    assert_eq!(refusal.code, "state_write_failed");
    assert!(refusal.message.contains("already holds an aggregate"), "{}", refusal.message);
    assert!(fs::read_to_string(&path).unwrap().contains("the first answer"));
    assert_eq!(fs::read_dir(path.parent().unwrap()).unwrap().count(), 1);
}
