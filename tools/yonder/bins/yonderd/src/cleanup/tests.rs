use pretty_assertions::assert_eq;
use yonder_link::proto::RunSpec;

use super::*;
use crate::run::{ExitRecord, KEEP};
use crate::testing::git as git_in;

/// A repository with one commit at `repo`. Answers the commit.
fn repo_with_commit(repo: &Path) -> String {
    fs::create_dir_all(repo).expect("the repository directory");
    git_in(repo, &["init", "-q"]);
    git_in(
        repo,
        &["-c", "maintenance.auto=false", "commit", "-q", "--allow-empty", "-m", "first"],
    );
    git_in(repo, &["rev-parse", "HEAD"])
}

fn spec(run: &str) -> RunSpec {
    RunSpec {
        run: run.to_owned(),
        repo: "~/repo".to_owned(),
        holder: "h".to_owned(),
        commit: "a".repeat(40),
        reference: format!("refs/yonder/{run}"),
        command: vec!["true".to_owned()],
        fetch: vec![],
        ..RunSpec::default()
    }
}

#[test]
fn a_ref_without_a_run_goes_and_a_ref_of_a_kept_or_fresh_run_and_a_base_ref_stay() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let home = Home::new(dir.path().to_path_buf());
    let repo = dir.path().join("repo");
    let commit = repo_with_commit(&repo);
    let unlisted = dir.path().join("unlisted");
    let unlisted_commit = repo_with_commit(&unlisted);
    let now = SystemTime::now();
    let fresh = format!("{}-2", epoch_ms(now) - 1000);
    let runs = Runs::new(home.runs_dir());
    let _goes_on = runs.create(&spec("goes-on")).expect("a run that goes on");
    let (ended_long_ago, _) = runs.create(&spec("2000-3")).expect("an old run");
    ended_long_ago.write_exit(&ExitRecord::Code { code: 0 }).expect("its end");
    fs::File::options()
        .write(true)
        .open(ended_long_ago.path().join("exit.json"))
        .and_then(|file| file.set_modified(now - KEEP - Duration::from_secs(60)))
        .expect("an old end");
    let (ended_now, _) = runs.create(&spec("3000-4")).expect("a new run");
    ended_now.write_exit(&ExitRecord::Code { code: 0 }).expect("its end");
    for run in ["1000-1", fresh.as_str(), "goes-on", "2000-3", "3000-4"] {
        git_in(&repo, &["update-ref", &format!("refs/yonder/{run}"), &commit]);
    }
    git_in(&repo, &["update-ref", "refs/yonder/base/master", &commit]);
    git_in(&unlisted, &["update-ref", "refs/yonder/1000-1", &unlisted_commit]);
    remember_repo(&home, &repo).expect("listed");
    remember_repo(&home, &repo).expect("listed once");
    assert_eq!(
        fs::read_to_string(home.repos_file()).expect("the list"),
        format!("{}\n", repo.display())
    );

    let lines = clean(&home, now);
    assert_eq!(
        lines,
        vec![
            "removed the run 2000-3, which ended a day ago".to_owned(),
            format!("deleted refs/yonder/1000-1 in {}, which no run uses", repo.display()),
            format!("deleted refs/yonder/2000-3 in {}, which no run uses", repo.display()),
        ]
    );
    let left = git::pack_refs(&repo).expect("the refs");
    let mut expected = vec![
        format!("refs/yonder/{fresh}"),
        "refs/yonder/3000-4".to_owned(),
        "refs/yonder/base/master".to_owned(),
        "refs/yonder/goes-on".to_owned(),
    ];
    expected.sort();
    assert_eq!(left, expected);
    assert_eq!(git::pack_refs(&unlisted).expect("the refs"), vec!["refs/yonder/1000-1".to_owned()]);
}

/// The packs of the runs pile up in a listed repository until the cleanup runs its git maintenance.
#[test]
fn the_cleanup_repacks_a_listed_repository_whose_packs_piled_up() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let home = Home::new(dir.path().to_path_buf());
    let repo = dir.path().join("repo");
    repo_with_commit(&repo);
    for index in 0..3 {
        git_in(
            &repo,
            &[
                "-c",
                "maintenance.auto=false",
                "commit",
                "-q",
                "--allow-empty",
                "-m",
                &format!("run {index}"),
            ],
        );
        git_in(&repo, &["repack", "-q"]);
    }
    // A commit starts a maintenance of its own in the background, and its lock would make the cleanup skip the run.
    git_in(&repo, &["config", "gc.autoPackLimit", "2"]);
    let packs = || {
        fs::read_dir(repo.join(".git/objects/pack"))
            .expect("the pack directory")
            .filter(|entry| {
                entry
                    .as_ref()
                    .is_ok_and(|entry| entry.path().extension().is_some_and(|ext| ext == "pack"))
            })
            .count()
    };
    assert_eq!(packs(), 3);
    remember_repo(&home, &repo).expect("listed");
    assert_eq!(clean(&home, SystemTime::now()), Vec::<String>::new());
    assert_eq!(packs(), 1);
}

#[test]
fn a_run_id_is_fresh_within_the_grace_after_its_time() {
    let now = SystemTime::now();
    let ms = epoch_ms(now);
    assert!(is_fresh(&format!("{ms}-1"), now));
    assert!(is_fresh(&format!("{}-1", ms + 5000), now), "a clock ahead of the agent");
    assert!(!is_fresh(&format!("{}-1", ms - 2 * 3600 * 1000), now));
    assert!(!is_fresh("goes-on", now));
}
