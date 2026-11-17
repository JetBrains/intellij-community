//! The controlled proxy: the agent process passes each line of the IDE to the control server, and applies each command
//! of the test process. It owns no ACP logic.
//!
//! One thread reads standard input. Each line goes out as the next `inbound` frame. The end of the input reports
//! `stdin_eof`, and the process runs on. The main thread is the command loop:
//!
//! | command | what the proxy does |
//! | --- | --- |
//! | `write`, `writeMalformed` | writes the line with a line feed, or the bytes, on standard output, then acknowledges the command |
//! | `closeStdout` | closes standard output, reports `stdout_closed`, then acknowledges the command |
//! | `exit` | acknowledges the command, reports `process_exit` with the code, and exits with the code. It does not wait for the MCP servers: an `http` server gets no `DELETE`, and a `stdio` process group gets a termination signal |
//! | `mcpCall`, `mcpListTools` | asks the MCP server of the last `session/new`, and answers `mcpResult` |
//!
//! A write after the close of standard output, a failed write and a failure of the control connection report one
//! `control_failure` and exit with 1.

use std::io::{self, BufRead};
use std::sync::Arc;
use std::thread;

use avl_control::proxy::{Command, Commands, Lifecycle};

use crate::environment::Environment;
use crate::lines::read_line;
use crate::process::{self, Agent, McpEnd, marker, say};
use crate::stdout::AgentStdout;

/// Runs the controlled proxy. It answers 2 when the environment names no control endpoint, and 1 when the connection
/// fails. After the `hello` the process ends through [Agent::finish].
pub(crate) fn run(environment: &Environment) -> i32 {
    let endpoint = match process::endpoint(environment, "") {
        Ok(endpoint) => endpoint,
        Err(line) => {
            say(line);
            return 2;
        }
    };
    let process_id = process::process_id("proc-");
    let redacted = marker(&endpoint.redacted_url());
    let (agent, commands) = match Agent::connect(&endpoint, &process_id) {
        Ok(connected) => connected,
        Err(error) => {
            say(format_args!("ACP fake-agent proxy failed: IOException: {error};control={redacted}"));
            return 1;
        }
    };
    say(format_args!(
        "acp-fake-agent-proxy-start;agentKey={};pid={};processId={};control={redacted}",
        marker(&endpoint.agent_key),
        std::process::id(),
        marker(&process_id)
    ));
    let agent = Arc::new(agent);
    let forwarder = Arc::clone(&agent);
    let redacted_for_input = redacted.clone();
    let started = thread::Builder::new()
        .name("acp-fake-agent-input".to_owned())
        .spawn(move || forward_input(&forwarder, &mut io::stdin().lock(), &redacted_for_input));
    if let Err(error) = started {
        fail(&agent, &error, &redacted);
    }
    apply_commands(&agent, commands, &redacted)
}

/// Passes each line of `input` to the control server, then reports `stdin_eof`.
fn forward_input(agent: &Agent, input: &mut impl BufRead, redacted: &str) {
    let forwarded = (|| {
        while let Some(line) = read_line(input)? {
            agent.mcp.observe(&line);
            agent.proxy.inbound(&line)?;
        }
        agent.proxy.lifecycle(Lifecycle::StdinEof, Some("stdin closed"), None)
    })();
    if let Err(error) = forwarded {
        fail(agent, &error, redacted);
    }
}

/// Applies each command of the control server until an exit command or a failure ends the process.
fn apply_commands(agent: &Agent, mut commands: Commands, redacted: &str) -> ! {
    let mut stdout = AgentStdout::new();
    loop {
        let applied = commands.next(&agent.proxy).and_then(|command| apply(agent, &mut stdout, command));
        match applied {
            Ok(None) => {}
            Ok(Some(code)) => agent.exit(code, McpEnd::Abandon),
            Err(error) => fail(agent, &error, redacted),
        }
    }
}

/// Applies one command, and answers the exit code of an exit command.
fn apply(agent: &Agent, stdout: &mut AgentStdout, command: Command) -> io::Result<Option<i64>> {
    let proxy = &agent.proxy;
    match command {
        Command::Write { id, line } => {
            stdout.write(format!("{line}\n").as_bytes())?;
            proxy.ack(&id)?;
        }
        Command::WriteMalformed { id, bytes } => {
            stdout.write(&bytes)?;
            proxy.ack(&id)?;
        }
        Command::CloseStdout { id } => {
            if stdout.close()? {
                proxy.lifecycle(Lifecycle::StdoutClosed, Some("stdout closed by control"), None)?;
            }
            proxy.ack(&id)?;
        }
        Command::Exit { id, code } => {
            proxy.ack(&id)?;
            say(format_args!("acp-fake-agent-proxy-exit;code={code}"));
            return Ok(Some(code));
        }
        Command::Mcp { id, request } => {
            let outcome = agent.mcp.answer(&request);
            proxy.mcp_result(&id, outcome.is_error, &outcome.text)?;
        }
    }
    Ok(None)
}

/// Reports one `control_failure` and the reason, and exits with 1.
fn fail(agent: &Agent, error: &io::Error, redacted: &str) -> ! {
    if agent.is_ending() {
        agent.finish(McpEnd::Close, |_| 1);
    }
    agent.report_control_failure();
    say(format_args!(
        "ACP fake-agent proxy failed: IOException: Fake-agent control channel failed: {error};control={redacted}"
    ));
    agent.finish(McpEnd::Close, |_| 1)
}
