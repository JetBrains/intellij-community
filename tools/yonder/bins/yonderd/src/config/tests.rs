use pretty_assertions::assert_eq;

use super::*;

/// A commit without `.yonder.toml` has the default configuration, and the text of the file of a commit is parsed
#[test]
fn the_config_is_the_file_of_the_commit_or_the_default() {
    assert_eq!(of_blob(None).expect("no file in the commit"), RepoConfig::default());
    assert_eq!(
        of_blob(Some("outputs = [\"out\"]\n")).expect("the file of the commit").outputs,
        vec!["out".to_owned()]
    );
    assert_eq!(of_blob(Some("not toml")).expect_err("a bad file").code, BAD_CONFIG);
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
