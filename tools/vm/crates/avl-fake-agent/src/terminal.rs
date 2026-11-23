//! The two terminal modes of the agent. Each reports over the control channel how Air launched it, and then prints only
//! what the test process tells it to print.
//!
//! - The terminal sign-in process ([login]) is the process that Air starts in its authentication terminal for an auth
//!   method of the terminal kind.
//! - The terminal TUI process ([tui]) is the process that a terminal of the IDE starts as the CLI of an agent. A
//!   launcher of a scenario starts it with `--air-terminal-tui`. It takes the MCP servers of `--mcp-config <file>`, as
//!   Claude Code does, or of `<dir>/mcp.json` of `--mcp-location <dir>`, as Junie does. For a command with
//!   `--session-id`, it writes the first line of the Claude history, as Claude Code does at the start of a session.
//!   It turns on bracketed paste, writes [READY_LINE], and reports each line that the terminal types into it. It ends
//!   on an exit command, on the typed line [EXIT_LINE], or at the end of its input.
//!
//! In both modes the command loop is the one writer of standard output, and every scripted line is a finished line.

use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::thread;
use std::time::SystemTime;
use std::{env, fs};

use avl_control::proxy::{Command, Commands, Lifecycle};
use serde_json::Value;

use crate::environment::Environment;
use crate::json::{quote, quote_all};
use crate::mcp::{self, Offer};
use crate::process::{self, Agent, McpEnd, say};

/// The `kind` of the one inbound frame that a terminal process sends before it waits for commands.
pub(crate) const LAUNCH_REPORT_KIND: &str = "air-terminal-login-launch";
/// The `kind` of the inbound frame that carries one line that the terminal typed into the TUI process.
pub(crate) const INPUT_LINE_KIND: &str = "air-terminal-input-line";
/// The flag of the Claude Code CLI that names the MCP config file.
pub(crate) const MCP_CONFIG_ARG: &str = "--mcp-config";
/// The flag of the Junie CLI that names the directory of the MCP config file.
pub(crate) const MCP_LOCATION_ARG: &str = "--mcp-location";
/// The name of the MCP config file in the directory of [MCP_LOCATION_ARG].
pub(crate) const MCP_CONFIG_FILE: &str = "mcp.json";
/// The flag of the Claude Code CLI that names the id of a new session.
pub(crate) const SESSION_ID_ARG: &str = "--session-id";
/// The variable that names the Claude home, as the Claude Code CLI reads it.
pub(crate) const CLAUDE_CONFIG_DIR_ENV: &str = "CLAUDE_CONFIG_DIR";
/// The line that the TUI writes when it reads its input.
pub(crate) const READY_LINE: &str = "Air terminal TUI fixture is ready";
/// The typed line that ends the TUI after its report.
pub(crate) const EXIT_LINE: &str = "exit";

/// The token that ends the options of a Claude command and starts its prompt.
const PROMPT_BOUNDARY: &str = "--";
const BRACKETED_PASTE_ON: &str = "\u{1b}[?2004h";
const PASTE_START: &str = "\u{1b}[200~";
const PASTE_END: &str = "\u{1b}[201~";

/// Runs the terminal sign-in process. A sign-in process receives no `session/new`, so it offers no MCP server, and an
/// MCP call answers an error result.
pub(crate) fn login(args: &[String], environment: &Environment) -> i32 {
    match connect("terminal-login", "login-", args, environment) {
        Ok((agent, commands, redacted)) => apply_commands(&agent, commands, "terminal login", &redacted),
        Err(code) => code,
    }
}

/// Runs the terminal TUI process.
pub(crate) fn tui(args: &[String], environment: &Environment) -> i32 {
    let (agent, commands, redacted) = match connect("terminal-tui", "tui-", args, environment) {
        Ok((agent, commands, redacted)) => (Arc::new(agent), commands, redacted),
        Err(code) => return code,
    };
    agent.mcp.offer(configured_servers(args));
    let cwd = env::current_dir().unwrap_or_default();
    match write_claude_history(args, environment, &cwd, SystemTime::now()) {
        Ok(Some(history)) => say(format_args!("acp-fake-agent-tui-claude-history;{}", history.display())),
        Ok(None) => {}
        Err(error) => fail(&agent, "terminal TUI", &error, &redacted),
    }
    let ready = format!("{BRACKETED_PASTE_ON}{READY_LINE}\n");
    if let Err(error) = io::stdout().lock().write_all(ready.as_bytes()).and_then(|()| io::stdout().flush()) {
        fail(&agent, "terminal TUI", &error, &redacted);
    }
    let reader = Arc::clone(&agent);
    let started = thread::Builder::new()
        .name("acp-fake-agent-tui-input".to_owned())
        .spawn(move || forward_typed_lines(&reader, &mut io::stdin().lock()));
    if let Err(error) = started {
        fail(&agent, "terminal TUI", &error, &redacted);
    }
    apply_commands(&agent, commands, "terminal TUI", &redacted)
}

/// Connects a terminal process as the process `<prefix><pid>-<time>`, and sends its launch report. It answers the
/// agent, its commands and the redacted control URL, or the exit code of a process that cannot reach the control
/// server: 2 without an endpoint, and 1 for a failed connection.
fn connect(mode: &str, prefix: &str, args: &[String], environment: &Environment) -> Result<(Agent, Commands, String), i32> {
    let endpoint = process::endpoint(environment, mode).map_err(|line| {
        say(line);
        2
    })?;
    let redacted = endpoint.redacted_url();
    let title = if mode == "terminal-login" {
        "terminal login"
    } else {
        "terminal TUI"
    };
    let cwd = env::current_dir().unwrap_or_default();
    let connected = Agent::connect(&endpoint, &process::process_id(prefix)).and_then(|(agent, commands)| {
        agent.proxy.inbound(&launch_report(args, environment, &cwd))?;
        Ok((agent, commands))
    });
    match connected {
        Ok((agent, commands)) => Ok((agent, commands, redacted)),
        Err(error) => {
            say(format_args!(
                "ACP fake-agent {title} failed: IOException: {error};control={redacted}"
            ));
            Err(1)
        }
    }
}

/// Applies each command of the control server until an exit command or a failure ends the process.
fn apply_commands(agent: &Agent, mut commands: Commands, title: &str, redacted: &str) -> ! {
    loop {
        let applied = commands.next(&agent.proxy).and_then(|command| apply(agent, command));
        match applied {
            Ok(None) => {}
            Ok(Some(code)) => agent.exit(code, McpEnd::Close),
            Err(error) => fail(agent, title, &error, redacted),
        }
    }
}

/// Applies one command, and answers the exit code of an exit command.
fn apply(agent: &Agent, command: Command) -> io::Result<Option<i64>> {
    let proxy = &agent.proxy;
    let mut stdout = io::stdout().lock();
    match command {
        Command::Write { id, line } => {
            stdout.write_all(line.as_bytes())?;
            stdout.write_all(b"\n")?;
            stdout.flush()?;
            proxy.ack(&id)?;
        }
        Command::WriteMalformed { id, bytes } => {
            stdout.write_all(&bytes)?;
            stdout.flush()?;
            proxy.ack(&id)?;
        }
        Command::CloseStdout { id } => {
            stdout.flush()?;
            proxy.lifecycle(Lifecycle::StdoutClosed, Some("stdout closed by control"), None)?;
            proxy.ack(&id)?;
        }
        Command::Exit { id, code } => {
            proxy.ack(&id)?;
            return Ok(Some(code));
        }
        Command::Mcp { id, request } => {
            drop(stdout);
            let outcome = agent.mcp.answer(&request);
            proxy.mcp_result(&id, outcome.is_error, &outcome.text)?;
        }
    }
    Ok(None)
}

/// Writes the reason of a failure and exits with 1.
fn fail(agent: &Agent, title: &str, error: &io::Error, redacted: &str) -> ! {
    if !agent.is_ending() {
        say(format_args!(
            "ACP fake-agent {title} failed: IOException: {error};control={redacted}"
        ));
    }
    agent.finish(McpEnd::Close, |_| 1)
}

/// Reports each typed line of `input` until its end or until [EXIT_LINE], then reports `stdin_eof` and ends the process.
fn forward_typed_lines(agent: &Agent, input: &mut impl Read) {
    let forwarded = read_typed_lines(input, |line| {
        let frame = format!("{{\"kind\":{},\"line\":{}}}", quote(INPUT_LINE_KIND), quote(line));
        agent.proxy.inbound(&frame)
    })
    .and_then(|()| agent.proxy.lifecycle(Lifecycle::StdinEof, Some("stdin closed"), None));
    if let Err(error) = &forwarded {
        say(format_args!("acp-fake-agent-tui-input-failed;IOException: {error}"));
    }
    agent.finish(McpEnd::Close, |proxy| {
        i32::from(proxy.lifecycle(Lifecycle::ProcessExit, Some("input ended"), Some(0)).is_err())
    });
}

/// Reads `input` until its end or until the line [EXIT_LINE], and gives each typed line to `sink`.
///
/// A carriage return or a line feed ends a line, because a terminal types Enter as a carriage return. The bracketed
/// paste markers go, the outer white space goes, and an empty line is skipped. The line [EXIT_LINE] reaches `sink` too.
pub(crate) fn read_typed_lines(input: &mut impl Read, mut sink: impl FnMut(&str) -> io::Result<()>) -> io::Result<()> {
    let mut line = Vec::new();
    let mut buffer = [0_u8; 4096];
    loop {
        let count = match input.read(&mut buffer) {
            Ok(0) => break,
            Ok(count) => count,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error),
        };
        for &byte in &buffer[..count] {
            if byte != b'\r' && byte != b'\n' {
                line.push(byte);
                continue;
            }
            let typed = typed_line(&line);
            line.clear();
            if typed.is_empty() {
                continue;
            }
            sink(&typed)?;
            if typed == EXIT_LINE {
                return Ok(());
            }
        }
    }
    let rest = typed_line(&line);
    if rest.is_empty() { Ok(()) } else { sink(&rest) }
}

fn typed_line(raw: &[u8]) -> String {
    String::from_utf8_lossy(raw)
        .replace(PASTE_START, "")
        .replace(PASTE_END, "")
        .trim()
        .to_owned()
}

/// The launch report, which lets a test assert how the IDE resolved the command: the arguments, the working directory
/// and the environment.
pub(crate) fn launch_report(args: &[String], environment: &Environment, cwd: &Path) -> String {
    let variables: Vec<String> = environment
        .variables()
        .iter()
        .map(|(name, value)| format!("{}:{}", quote(name), quote(value)))
        .collect();
    format!(
        "{{\"kind\":{},\"args\":{},\"cwd\":{},\"env\":{{{}}}}}",
        quote(LAUNCH_REPORT_KIND),
        quote_all(args.iter().map(String::as_str)),
        quote(&cwd.display().to_string()),
        variables.join(",")
    )
}

/// The MCP servers of the config file that [MCP_CONFIG_ARG] names, or of the config file in the directory that
/// [MCP_LOCATION_ARG] names. Without a flag, or with a file that cannot be read, the process offers no server, and the
/// reason goes to the diagnostics.
pub(crate) fn configured_servers(args: &[String]) -> Offer {
    let config = flag_value(args, MCP_CONFIG_ARG)
        .map(PathBuf::from)
        .or_else(|| flag_value(args, MCP_LOCATION_ARG).map(|location| Path::new(location).join(MCP_CONFIG_FILE)));
    let Some(config) = config else {
        say(format_args!(
            "acp-fake-agent-tui-mcp;the command has no {MCP_CONFIG_ARG} and no {MCP_LOCATION_ARG}"
        ));
        return Offer::new();
    };
    match fs::read_to_string(&config) {
        Ok(text) => {
            let servers = serde_json::from_str::<Value>(&text)
                .map(|root| mcp::config_servers(&root))
                .unwrap_or_default();
            let names: Vec<&str> = servers.iter().map(|(name, _)| name.as_str()).collect();
            say(format_args!("acp-fake-agent-tui-mcp;servers=[{}]", names.join(", ")));
            servers
        }
        Err(error) => {
            say(format_args!("acp-fake-agent-tui-mcp;cannot read {}: {error}", config.display()));
            Offer::new()
        }
    }
}

/// The value after the first `flag` of `args`.
fn flag_value<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let index = args.iter().position(|arg| arg == flag)?;
    args.get(index + 1).map(String::as_str)
}

/// Writes the first line of the Claude history of a command with [SESSION_ID_ARG], and answers its file. A command
/// without the flag writes nothing.
///
/// The file is `<Claude home>/projects/<encoded working directory>/<session id>.jsonl`, as Claude Code keeps it. The
/// Claude home is [CLAUDE_CONFIG_DIR_ENV], else `$HOME/.claude`. The encoding replaces each character that is no letter
/// or digit with `-`. The one line is the user message with the prompt after `--`.
pub(crate) fn write_claude_history(
    args: &[String],
    environment: &Environment,
    working_directory: &Path,
    now: SystemTime,
) -> io::Result<Option<PathBuf>> {
    let Some(session_id) = flag_value(args, SESSION_ID_ARG) else {
        return Ok(None);
    };
    let claude_home = match environment.non_blank(CLAUDE_CONFIG_DIR_ENV) {
        Some(home) => PathBuf::from(home),
        None => environment
            .get("HOME")
            .map(PathBuf::from)
            .or_else(env::home_dir)
            .unwrap_or_default()
            .join(".claude"),
    };
    let cwd = working_directory.display().to_string();
    let encoded: String = cwd
        .chars()
        .map(|character| if character.is_alphanumeric() { character } else { '-' })
        .collect();
    let prompt = args
        .iter()
        .position(|arg| arg == PROMPT_BOUNDARY)
        .map(|boundary| args[boundary + 1..].join(" "))
        .unwrap_or_default();
    let line = format!(
        "{{\"type\":\"user\",\"sessionId\":{},\"cwd\":{},\"isSidechain\":false,\"timestamp\":{},\
         \"message\":{{\"role\":\"user\",\"content\":{}}}}}\n",
        quote(session_id),
        quote(&cwd),
        quote(&instant_text(now)),
        quote(&prompt)
    );
    let history = claude_home.join("projects").join(encoded).join(format!("{session_id}.jsonl"));
    if let Some(parent) = history.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(&history, line)?;
    Ok(Some(history))
}

/// `time` in UTC as ISO 8601 with milliseconds, as Claude Code writes it: `2026-10-11T10:00:00.000Z`.
pub(crate) fn instant_text(time: SystemTime) -> String {
    jiff::Timestamp::try_from(time)
        .unwrap_or(jiff::Timestamp::UNIX_EPOCH)
        .strftime("%Y-%m-%dT%H:%M:%S%.3fZ")
        .to_string()
}

#[cfg(test)]
mod tests;
