//! `acp-fake-agent`: the fake ACP agent that the Air tests start as the process of an agent.
//!
//! The binary has four modes. The arguments and the environment select the mode, in this order:
//!
//! | selector | mode |
//! | --- | --- |
//! | the argument `--air-terminal-login` | the terminal sign-in process ([terminal::login]) |
//! | the argument `--air-terminal-tui` | the terminal TUI process ([terminal::tui]) |
//! | a non-blank `ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX` | the deterministic agent ([deterministic]) |
//! | none of these | the controlled proxy ([controlled]) |
//!
//! Every mode but the deterministic agent reaches the control server in the IDE under test through
//! `ACP_FAKE_AGENT_CONTROL_URL` and `ACP_FAKE_AGENT_CONTROL_KEY`. Then the test process decides what the agent writes and
//! when it exits. Standard error carries the diagnostic lines only.
//!
//! The binary has no library, and its process tests are `tests/cli.rs`.

use std::ffi::OsString;
use std::process::ExitCode;

mod controlled;
mod deterministic;
mod environment;
mod hold;
mod json;
mod lines;
mod mcp;
mod process;
mod stdout;
mod terminal;

use environment::Environment;

/// The argument that selects the terminal sign-in mode. Air appends the arguments of an auth method to the command of
/// the agent, so the agent knows that the authentication terminal started it.
pub(crate) const TERMINAL_LOGIN_ARG: &str = "--air-terminal-login";

/// The argument that selects the terminal TUI mode. The launcher of a scenario puts it before the arguments of the
/// terminal command.
pub(crate) const TERMINAL_TUI_ARG: &str = "--air-terminal-tui";

/// The variable that selects the deterministic agent. Its value is the prefix of each answer.
pub(crate) const STANDALONE_RESPONSE_PREFIX_ENV: &str = "ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX";

fn main() -> ExitCode {
    let code = run(std::env::args_os().skip(1), &Environment::of_process());
    ExitCode::from(u8::try_from(code).unwrap_or(1))
}

/// Runs the mode that `args` and `environment` select, and answers the exit code of a mode that ends without an exit
/// command. A mode that connected to the control server ends the process itself.
fn run(args: impl IntoIterator<Item = OsString>, environment: &Environment) -> i32 {
    let args: Vec<String> = args.into_iter().map(|arg| arg.to_string_lossy().into_owned()).collect();
    if args.iter().any(|arg| arg == TERMINAL_LOGIN_ARG) {
        return terminal::login(&args, environment);
    }
    if args.iter().any(|arg| arg == TERMINAL_TUI_ARG) {
        return terminal::tui(&args, environment);
    }
    if let Some(prefix) = environment.non_blank(STANDALONE_RESPONSE_PREFIX_ENV) {
        return deterministic::run_standalone(prefix, environment);
    }
    controlled::run(environment)
}
