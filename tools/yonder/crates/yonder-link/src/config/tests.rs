use pretty_assertions::assert_eq;

use super::*;

/// The example of `API.md` of the agent.
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
    assert_eq!(parse("").expect("an empty file"), RepoConfig::default());
}

#[test]
fn a_typo_or_a_bad_value_is_an_error_that_names_the_file() {
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
        let message = parse(text).expect_err(text).to_string();
        assert!(message.contains(said), "{text}: {message}");
        assert!(message.starts_with(".yonder.toml: "), "{message}");
    }
}

#[test]
fn an_inner_path_stays_inside_its_base() {
    assert!(is_inner_path("out/bt-runs"));
    assert!(is_inner_path("out"));
    assert!(!is_inner_path(""));
    assert!(!is_inner_path("/out"));
    assert!(!is_inner_path("out/../.."));
    assert!(!is_inner_path("./out"));
}
