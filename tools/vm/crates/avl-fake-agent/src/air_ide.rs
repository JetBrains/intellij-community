//! The `air-ide` command of the control server: the agent process runs `air-ide` with the arguments of the command, as
//! an agent runs it from its shell. The process finds `air-ide` on its `PATH` and gives it its own environment, so the
//! run takes the credential and the socket that the IDE gave the launch.

use std::ffi::OsStr;
use std::process::{Command, Stdio};

use crate::mcp::Outcome;
use crate::process::{marker, say};

/// The program of the command, which the `PATH` of the process resolves.
pub(crate) const AIR_IDE: &str = "air-ide";

/// Runs `program` with `args` and no input, and writes its diagnostic line. A run that exits with 0 answers its standard
/// output. A run that exits with another code, or that does not start, answers an error with the reason and the output.
pub(crate) fn run(program: &OsStr, args: &[String]) -> Outcome {
    let command = args.iter().take(2).map(String::as_str).collect::<Vec<_>>().join(" ");
    let outcome = match Command::new(program).args(args).stdin(Stdio::null()).output() {
        Ok(output) => {
            let stdout = String::from_utf8_lossy(&output.stdout).into_owned();
            if output.status.success() {
                Outcome {
                    is_error: false,
                    text: stdout,
                }
            } else {
                let stderr = String::from_utf8_lossy(&output.stderr);
                Outcome {
                    is_error: true,
                    text: format!("{} {command} ended with {}: {}{stdout}", program.display(), output.status, stderr),
                }
            }
        }
        Err(error) => Outcome {
            is_error: true,
            text: format!("{} {command} did not start: {error}", program.display()),
        },
    };
    say(format_args!(
        "acp-fake-agent-air-ide;command={};isError={}",
        marker(&command),
        outcome.is_error
    ));
    outcome
}

#[cfg(test)]
mod tests;
