use std::time::Duration;

use pretty_assertions::assert_eq;
use yonder_link::proto::RunSpec;

use super::*;
use crate::run::{ExitRecord, KEEP};

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

/// A run a day after its end goes, and a run that goes on or that ended now stays
#[test]
fn a_run_goes_a_day_after_its_end() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let home = Home::new(dir.path().to_path_buf());
    let now = SystemTime::now();
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

    assert_eq!(clean(&home, now), vec!["removed the run 2000-3, which ended a day ago".to_owned()]);
    let mut left: Vec<String> = runs.list().iter().filter_map(|run| run.spec().ok()).map(|spec| spec.run).collect();
    left.sort();
    assert_eq!(left, ["3000-4", "goes-on"]);
}

/// The repository list keeps each repository of a pack once
#[test]
fn the_repository_list_keeps_each_repository_once() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let home = Home::new(dir.path().to_path_buf());
    let repo = dir.path().join("repo");
    assert_eq!(repos(&home), Vec::<PathBuf>::new());
    remember_repo(&home, &repo).expect("listed");
    remember_repo(&home, &repo).expect("listed once");
    assert_eq!(
        fs::read_to_string(home.repos_file()).expect("the list"),
        format!("{}\n", repo.display())
    );
    assert_eq!(repos(&home), [repo]);
}
