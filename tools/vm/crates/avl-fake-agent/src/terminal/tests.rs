use std::io::Cursor;
use std::time::{Duration, UNIX_EPOCH};

use pretty_assertions::assert_eq;

use super::*;
use crate::mcp::ServerSpec;

fn typed(input: &str) -> Vec<String> {
    let mut lines = Vec::new();
    read_typed_lines(&mut Cursor::new(input.as_bytes()), |line| {
        lines.push(line.to_owned());
        Ok(())
    })
    .unwrap();
    lines
}

/// A typed line ends with a carriage return or a line feed, loses the paste markers and its outer white space, and an
/// empty line is skipped. The line `exit` ends the read, and a last line without an end still arrives.
#[test]
fn each_typed_line_arrives_without_the_paste_markers() {
    assert_eq!(
        typed("\u{1b}[200~Click check: lane.confirm\r{\"viewId\":\"v\"}\u{1b}[201~\r\r  exit \nafter\n"),
        ["Click check: lane.confirm", "{\"viewId\":\"v\"}", "exit"]
    );
    assert_eq!(typed("one\r\ntwo"), ["one", "two"]);
    assert_eq!(typed(""), Vec::<String>::new());
}

#[test]
fn the_launch_report_names_the_arguments_the_directory_and_the_sorted_environment() {
    let environment = Environment::of(&[("B", "2"), ("A", "a\"1")]);
    let args = vec!["--air-terminal-login".to_owned(), "x y".to_owned()];
    assert_eq!(
        launch_report(&args, &environment, Path::new("/work")),
        r#"{"kind":"air-terminal-login-launch","args":["--air-terminal-login","x y"],"cwd":"/work","env":{"A":"a\"1","B":"2"}}"#
    );
}

/// The TUI reads the config file of `--mcp-config`, as Claude Code does, and the directory of `--mcp-location`, as Junie
/// does.
#[test]
fn the_tui_reads_the_servers_of_both_config_flags() {
    let location = tempfile::tempdir().unwrap();
    let config = location.path().join(MCP_CONFIG_FILE);
    fs::write(
        &config,
        r#"{"mcpServers":{"air-session":{"type":"http","url":"http://127.0.0.1:1/mcp","headers":{"Authorization":"Bearer t"}},"other":{"type":"stdio","url":"x"}}}"#,
    )
    .unwrap();
    let words = |words: &[&str]| words.iter().map(|word| (*word).to_owned()).collect::<Vec<_>>();
    let config_text = config.display().to_string();
    let location_text = location.path().display().to_string();
    let claude = words(&["--session-id", "s-1", MCP_CONFIG_ARG, &config_text, "--", "prompt"]);
    let junie = words(&["--skip-update-check", MCP_LOCATION_ARG, &location_text]);
    let expected = vec![(
        "air-session".to_owned(),
        ServerSpec::Http {
            url: "http://127.0.0.1:1/mcp".to_owned(),
            headers: [("Authorization".to_owned(), "Bearer t".to_owned())].into(),
        },
    )];
    assert_eq!(configured_servers(&claude), expected);
    assert_eq!(configured_servers(&junie), expected);
    assert_eq!(configured_servers(&words(&["--session-id", "s-1"])), Vec::new());
    assert_eq!(configured_servers(&words(&[MCP_CONFIG_ARG, "/no/such/file.json"])), Vec::new());
}

/// A Claude command makes the TUI write the first line of the Claude history under `CLAUDE_CONFIG_DIR`, so the Claude
/// source lists the session. A command without `--session-id` writes nothing.
#[test]
fn the_tui_writes_the_claude_history_of_a_session_id_command() {
    let home = tempfile::tempdir().unwrap();
    let environment = Environment::of(&[(CLAUDE_CONFIG_DIR_ENV, &home.path().display().to_string())]);
    let args: Vec<String> = ["--session-id", "s-1", "--mcp-config", "/tmp/mcp.json", "--", "Make", "a", "view"]
        .iter()
        .map(|word| (*word).to_owned())
        .collect();
    let now = UNIX_EPOCH + Duration::from_hours(497_698);
    let history = write_claude_history(&args, &environment, Path::new("/work/my project"), now)
        .unwrap()
        .unwrap();
    assert_eq!(history, home.path().join("projects").join("-work-my-project").join("s-1.jsonl"));
    assert_eq!(
        fs::read_to_string(&history).unwrap(),
        "{\"type\":\"user\",\"sessionId\":\"s-1\",\"cwd\":\"/work/my project\",\"isSidechain\":false,\
         \"timestamp\":\"2026-10-11T10:00:00.000Z\",\"message\":{\"role\":\"user\",\"content\":\"Make a view\"}}\n"
    );
    let plain = vec!["--skip-update-check".to_owned()];
    assert_eq!(write_claude_history(&plain, &environment, Path::new("/work"), now).unwrap(), None);
}

/// A time has the text of `Date.toISOString` of JavaScript, which Claude Code writes.
#[test]
fn an_instant_has_the_text_of_an_iso_string_with_milliseconds() {
    let at = |seconds: u64, nanos: u32| instant_text(UNIX_EPOCH + Duration::new(seconds, nanos));
    assert_eq!(at(0, 0), "1970-01-01T00:00:00.000Z");
    assert_eq!(at(951_782_400, 0), "2000-02-29T00:00:00.000Z");
    assert_eq!(at(1_791_712_800, 123_456_789), "2026-10-11T10:00:00.123Z");
}
