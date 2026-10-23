use pretty_assertions::assert_eq;

use super::*;
use crate::testing::git;

/// The example of `API.md`.
const EXAMPLE: &str = r#"
outputs = ["tests/results", "playwright-report", "target/nextest/ci/junit.xml"]

[env]
PATH = { prepend = ["~/.cargo/bin"] }
E2E_PORT = { port = 3100 }
RUST_BACKTRACE = "1"
CI = { unset = true }

[[prepare]]
name = "pnpm install"
run = ["pnpm", "install", "--frozen-lockfile"]
inputs = ["pnpm-lock.yaml", "pnpm-workspace.yaml"]
creates = ["node_modules"]

[[prepare]]
run = ["./gradlew", "--version"]
"#;

#[test]
fn the_example_parses_with_each_kind_of_value() {
    let config = parse(EXAMPLE).expect("the example");
    assert_eq!(
        config,
        RepoConfig {
            outputs: vec![
                "tests/results".to_owned(),
                "playwright-report".to_owned(),
                "target/nextest/ci/junit.xml".to_owned()
            ],
            env: vec![
                ("CI".to_owned(), EnvValue::Unset),
                ("E2E_PORT".to_owned(), EnvValue::Port(3100)),
                (
                    "PATH".to_owned(),
                    EnvValue::List {
                        prepend: vec!["~/.cargo/bin".to_owned()],
                        append: vec![]
                    }
                ),
                ("RUST_BACKTRACE".to_owned(), EnvValue::Text("1".to_owned())),
            ],
            prepare: vec![
                Step {
                    name: "pnpm install".to_owned(),
                    run: vec!["pnpm".to_owned(), "install".to_owned(), "--frozen-lockfile".to_owned()],
                    inputs: vec!["pnpm-lock.yaml".to_owned(), "pnpm-workspace.yaml".to_owned()],
                    creates: vec!["node_modules".to_owned()],
                },
                Step {
                    name: "./gradlew --version".to_owned(),
                    run: vec!["./gradlew".to_owned(), "--version".to_owned()],
                    inputs: vec![],
                    creates: vec![],
                },
            ],
        }
    );
}

#[test]
fn the_config_comes_from_the_commit_and_a_file_beside_it_does_not_count() {
    assert_eq!(parse("").expect("an empty file"), RepoConfig::default());
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

#[test]
fn a_typo_or_a_bad_value_refuses_with_bad_config() {
    for (text, said) in [
        ("[[prepar]]\nrun = [\"true\"]\n", "unknown field `prepar`"),
        ("[[prepare]]\nrun = [\"true\"]\ninput = [\"a\"]\n", "unknown field `input`"),
        ("[[prepare]]\nrun = []\n", "no command"),
        ("outputs = [\"../up\"]\n", "not a path inside the slot"),
        ("outputs = [\"/abs\"]\n", "not a path inside the slot"),
        (
            "[[prepare]]\nrun = [\"true\"]\ncreates = [\"a/../../b\"]\n",
            "not a path inside the slot",
        ),
        ("[env]\nA = 1\n", "env.A is a integer"),
        ("[env]\nA = { port = 1, unset = true }\n", "env.A takes one of"),
        ("[env]\nA = { unset = false }\n", "env.A takes one of"),
        ("[env]\nA = { prepand = [\"x\"] }\n", "unknown field `prepand`"),
        ("[env]\nA = { port = 70000 }\n", "env.A"),
        ("outputs = \"out\"\n", "invalid type"),
        ("not toml", "key"),
    ] {
        let refusal = parse(text).expect_err(text);
        assert_eq!(refusal.code, BAD_CONFIG, "{text}");
        assert!(refusal.message.contains(said), "{text}: {}", refusal.message);
        assert!(refusal.message.starts_with(".yonder.toml: "), "{}", refusal.message);
    }
}
