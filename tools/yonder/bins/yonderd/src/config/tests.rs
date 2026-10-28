use pretty_assertions::assert_eq;

use super::*;
use crate::testing::git;

#[test]
fn the_config_comes_from_the_commit_and_a_file_beside_it_does_not_count() {
    let dir = tempfile::tempdir().expect("a repository");
    let repo = dir.path();
    git(repo, &["init", "-q"]);
    git(repo, &["commit", "-q", "--allow-empty", "-m", "without"]);
    let without = git(repo, &["rev-parse", "HEAD"]);
    std::fs::write(repo.join(FILE), "outputs = [\"out\"]\n").expect("the file");
    assert_eq!(read(repo, &without).expect("no file in the commit"), RepoConfig::default());
    git(repo, &["add", FILE]);
    git(repo, &["commit", "-q", "-m", "with"]);
    let with = git(repo, &["rev-parse", "HEAD"]);
    std::fs::write(repo.join(FILE), "not toml").expect("a changed file");
    assert_eq!(read(repo, &with).expect("the file of the commit").outputs, vec!["out".to_owned()]);
}

/// The parser of `yonder-link` checks the format; the agent refuses each error of it with `bad_config`.
#[test]
fn an_error_of_the_file_refuses_with_bad_config() {
    let refusal = parse("[[prepar]]\nrun = [\"true\"]\n").expect_err("a typo");
    assert_eq!(refusal.code, BAD_CONFIG);
    assert!(
        refusal.message.starts_with(".yonder.toml: ") && refusal.message.contains("unknown field `prepar`"),
        "{}",
        refusal.message
    );
}
