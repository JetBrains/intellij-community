use pretty_assertions::assert_eq;

use crate::bench::{Host, replay};
use crate::launch::Progress;
use crate::testkit::{fixture_session, testdata_dir, testdata_text};

/// The variable that rewrites the golden from the current digest, under `cargo test` only.
const UPDATE: &str = "STARTUP_BENCH_UPDATE_GOLDEN";

/// The digest of a replay of the fixture session equals `testdata/replay-digest.txt`.
#[test]
fn the_replay_digest_matches_the_golden() {
    let (dir, session) = fixture_session();
    let host = Host {
        repo_root: dir.path(),
        working_dir: dir.path(),
    };
    let mut progress_text = Vec::new();
    let mut summary = replay(&session, &host, &mut Progress::new(&mut progress_text)).expect("a summary");
    summary.session = "<session>".to_owned();
    let digest = format!("{}\n", super::render(&summary));
    if std::env::var_os(UPDATE).is_some() {
        std::fs::write(testdata_dir().join("replay-digest.txt"), &digest).expect("the golden");
    }
    assert_eq!(
        digest,
        testdata_text("replay-digest.txt"),
        "run with {UPDATE}=1 to update the golden"
    );
    assert!(digest.lines().count() <= 60, "the digest is about 40 lines");
}
