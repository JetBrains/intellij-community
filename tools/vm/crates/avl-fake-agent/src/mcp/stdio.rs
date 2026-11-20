//! A `stdio` MCP server: a child process that speaks newline-delimited JSON-RPC on its standard input and output.
//!
//! The process gets the environment of the agent and the environment of its entry. Its standard error is the one of
//! the agent. A line of its output that is no JSON object is skipped, because a server or its launcher can print a
//! banner or a log line. On Unix the process leads a process group of its own, so the close stops its descendants too.

use std::collections::BTreeMap;
use std::io::{BufReader, Write};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError};
use std::thread;
use std::time::{Duration, Instant};
use std::{io, iter};

use super::Frame;
use crate::json::parse_object;
use crate::lines::read_line;
use crate::process::say;

/// How long the close waits for the exit of a process after the end of its input.
const CLOSE_GRACE: Duration = Duration::from_secs(2);
/// How long the close waits for the exit of the process group after a termination signal.
#[cfg(unix)]
const DESTROY_GRACE: Duration = Duration::from_secs(3);
const POLL: Duration = Duration::from_millis(20);

pub(super) struct StdioTransport {
    name: String,
    timeout: Duration,
    child: Child,
    stdin: Option<ChildStdin>,
    /// The lines of the output, until the end of the output disconnects the channel.
    lines: Receiver<String>,
    ended: bool,
    closed: bool,
}

impl StdioTransport {
    /// Starts `command` with `args`, and the environment of the agent with `env`.
    pub(super) fn start(name: &str, command: &str, args: &[String], env: &BTreeMap<String, String>, timeout: Duration) -> io::Result<Self> {
        let mut process = Command::new(command);
        process
            .args(args)
            .envs(env)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit());
        #[cfg(unix)]
        std::os::unix::process::CommandExt::process_group(&mut process, 0);
        let mut child = process.spawn().map_err(|error| {
            io::Error::new(
                error.kind(),
                format!("cannot start the MCP server '{name}' with {command}: {error}"),
            )
        })?;
        let stdin = child.stdin.take();
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| io::Error::other("the child has no standard output"))?;
        let (sender, lines) = mpsc::channel();
        let reader = BufReader::new(stdout);
        thread::Builder::new()
            .name(format!("acp-fixture-mcp-stdio-{name}"))
            .spawn(move || {
                let mut reader = reader;
                for line in iter::from_fn(|| read_line(&mut reader).ok().flatten()) {
                    if !line.trim().is_empty() && sender.send(line).is_err() {
                        return;
                    }
                }
            })?;
        Ok(Self {
            name: name.to_owned(),
            timeout,
            child,
            stdin,
            lines,
            ended: false,
            closed: false,
        })
    }

    /// Sends the request `body`, and answers the response of the id `id`.
    pub(super) fn request(&mut self, id: u64, method: &str, body: &str) -> io::Result<Frame> {
        self.write(body)?;
        let deadline = Instant::now() + self.timeout;
        loop {
            let line = if self.ended {
                Err(RecvTimeoutError::Disconnected)
            } else {
                self.lines.recv_timeout(deadline.saturating_duration_since(Instant::now()))
            };
            let line = match line {
                Ok(line) => line,
                Err(RecvTimeoutError::Disconnected) => {
                    self.ended = true;
                    let exit = self.exit_description();
                    let message = format!("MCP server '{}' closed stdout before the response of '{method}'{exit}", self.name);
                    return Err(io::Error::new(io::ErrorKind::UnexpectedEof, message));
                }
                Err(RecvTimeoutError::Timeout) => {
                    let exit = self.exit_description();
                    let seconds = self.timeout.as_secs();
                    let message = format!("MCP server '{}' did not answer '{method}' within {seconds} s{exit}", self.name);
                    return Err(io::Error::new(io::ErrorKind::TimedOut, message));
                }
            };
            if parse_object(&line).is_err() {
                say(format_args!("acp-fixture-mcp-stdio-skipped-line;server={};line={line}", self.name));
                continue;
            }
            if let Ok(frame) = Frame::parse(&self.name, line)
                && frame.answers(id)
            {
                return Ok(frame);
            }
        }
    }

    /// Writes `body` as one line of the input of the process.
    pub(super) fn write(&mut self, body: &str) -> io::Result<()> {
        let stdin = self
            .stdin
            .as_mut()
            .ok_or_else(|| io::Error::new(io::ErrorKind::BrokenPipe, "the MCP server is closed"))?;
        stdin.write_all(body.as_bytes())?;
        stdin.write_all(b"\n")?;
        stdin.flush()
    }

    /// Whether the process runs and the client has not closed it.
    pub(super) fn is_alive(&mut self) -> bool {
        !self.closed && matches!(self.child.try_wait(), Ok(None))
    }

    /// Closes the input, which ends a well-behaved server, then stops what is still alive of the process and of its
    /// descendants.
    pub(super) fn close(&mut self) {
        if self.closed {
            return;
        }
        self.closed = true;
        drop(self.stdin.take());
        self.wait_for_exit(CLOSE_GRACE);
        self.destroy();
        let _ = self.child.wait();
    }

    /// Closes the input and sends a termination signal to what is still alive of the process and of its descendants.
    /// It does not wait for an exit.
    pub(super) fn abandon(&mut self) {
        if self.closed {
            return;
        }
        self.closed = true;
        drop(self.stdin.take());
        let _ = self.terminate();
    }

    fn wait_for_exit(&mut self, grace: Duration) {
        let deadline = Instant::now() + grace;
        while matches!(self.child.try_wait(), Ok(None)) && Instant::now() < deadline {
            thread::sleep(POLL);
        }
    }

    /// Sends a termination signal to the process group, and answers the group when it got the signal.
    #[cfg(unix)]
    fn terminate(&self) -> Option<nix::unistd::Pid> {
        use nix::sys::signal::{Signal, killpg};

        let group = i32::try_from(self.child.id()).map(nix::unistd::Pid::from_raw).ok()?;
        killpg(group, Signal::SIGTERM).ok().map(|()| group)
    }

    /// Kills the process, and answers whether it got the kill. Windows has no process group here, so a descendant of
    /// the process stays.
    #[cfg(not(unix))]
    fn terminate(&mut self) -> Option<()> {
        self.child.kill().ok()
    }

    /// Sends a termination signal to the process group, waits [DESTROY_GRACE] for its members, and kills the rest.
    #[cfg(unix)]
    fn destroy(&mut self) {
        use nix::sys::signal::{Signal, killpg};

        let Some(group) = self.terminate() else {
            return;
        };
        let deadline = Instant::now() + DESTROY_GRACE;
        while Instant::now() < deadline {
            // The leader is reaped first, because a zombie leader keeps the group alive for the probe.
            let _ = self.child.try_wait();
            if killpg(group, None).is_err() {
                return;
            }
            thread::sleep(POLL);
        }
        let _ = killpg(group, Signal::SIGKILL);
    }

    /// Kills the process, as [Self::terminate] does.
    #[cfg(not(unix))]
    fn destroy(&mut self) {
        let _ = self.terminate();
    }

    fn exit_description(&mut self) -> String {
        match self.child.try_wait() {
            Ok(Some(status)) => match status.code() {
                Some(code) => format!("; the process exited with code {code}"),
                None => format!("; the process ended with {status}"),
            },
            _ => String::new(),
        }
    }
}
