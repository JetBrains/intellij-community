//! A byte stream into one guest process, over the stdin and stdout of a host child.
//!
//! The controller reaches a guest port through the hypervisor exec channel and not through the guest's network
//! address: macOS Local Network privacy refuses that address to a host app that is not Apple's. The exec channel
//! runs the guest agent's `relay` verb, which connects to the port inside the guest and copies bytes both ways.
//! So on the host a connection is a child process, and this module makes that child read and write like a socket.
//!
//! The failure contract is the one an HTTP client needs. A connection that never opened must fail as a refused
//! connection with a reason, and not as an empty response. A child that ends before it delivered a byte is that
//! case. After the first byte, the end of the child is the end of the response, and so an ordinary EOF.
//!
//! The guest agent writes its refusal as one failure envelope on stderr. The refusal quotes the message of that
//! envelope, because that message is the agent's own text. Other stderr is quoted as it is.

use std::fmt;
use std::io;
use std::pin::Pin;
use std::sync::{Arc, OnceLock};
use std::task::{Context, Poll, ready};
use std::time::{Duration, Instant};

use avl_base::Refusal;
use avl_wire::supervisor::{AgentExit, ReceivedEnvelope};
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, ReadBuf};
use tokio::process::{ChildStderr, ChildStdin, ChildStdout};
use tokio::sync::oneshot;
use tokio::time::Sleep;

use super::{FAILURE_OUTPUT_TAIL_BYTES, Interruptible, Runner, Spawned, base_name, check_argv, exit_code};
use crate::ctx::Ctx;
use avl_base::RefusalExt;

/// How long a reader waits for the exit status after EOF on stdout. The child that closed its stdout is ending,
/// so its status follows in milliseconds. A child that stays alive past this bound closed stdout on purpose, and
/// its EOF is an ordinary EOF.
const EXIT_AFTER_EOF_GRACE: Duration = Duration::from_secs(2);

/// How long a reader waits for bytes after a failed exit and before it reports the refusal. The exit notice and
/// the readiness of the pipe arrive on different paths, so the notice can arrive first while the last bytes still
/// wait in the pipe.
const BYTES_AFTER_EXIT_GRACE: Duration = Duration::from_millis(200);

/// How long the supervisor waits for stderr to end after the child exited while its process group still has a
/// member. That member can hold stderr open for as long as it runs, and the refusal must not wait for it.
const STDERR_AFTER_EXIT_GRACE: Duration = Duration::from_millis(200);

/// How long the supervisor waits for stderr to end after the child exited and left its process group empty. No
/// member holds the pipe then, so its EOF already follows the last line the child wrote, and the wait ends when the
/// reader drained it. Under load that drain can take longer than [`STDERR_AFTER_EXIT_GRACE`], and a refusal that
/// lost its line names no reason. The bound is for a process that left the group and still holds stderr.
const STDERR_TO_EOF_BOUND: Duration = Duration::from_secs(2);

/// A byte stream into one guest process: on the host, the relay child's stdout to read and stdin to write.
///
/// One type for the production stream and the test streams, so an HTTP client has one transport to code against.
pub struct GuestStream {
    io: Box<dyn StreamIo>,
}

/// What a [`GuestStream`] holds. A trait of its own, because a trait object can name only one non-auto trait.
trait StreamIo: AsyncRead + AsyncWrite + Send + Unpin {}

impl<T: AsyncRead + AsyncWrite + Send + Unpin> StreamIo for T {}

impl GuestStream {
    /// Over any duplex stream. The test channels hand in one end of `tokio::io::duplex`.
    pub fn from_io(io: impl AsyncRead + AsyncWrite + Send + Unpin + 'static) -> Self {
        Self { io: Box::new(io) }
    }
}

impl GuestStream {
    /// A stream whose first read fails with [`io::ErrorKind::ConnectionRefused`] and `message`, as a connect to a
    /// port where nothing listens fails. Writes before that read are accepted and discarded, so a client that
    /// sends its request first still meets the refusal where it meets a refused relay: at the first read.
    pub fn refused(message: impl Into<String>) -> Self {
        Self::from_io(Refused(message.into()))
    }
}

/// The stream of [`GuestStream::refused`].
struct Refused(String);

impl AsyncRead for Refused {
    fn poll_read(self: Pin<&mut Self>, _cx: &mut Context<'_>, _buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        Poll::Ready(Err(io::Error::new(io::ErrorKind::ConnectionRefused, self.0.clone())))
    }
}

impl AsyncWrite for Refused {
    fn poll_write(self: Pin<&mut Self>, _cx: &mut Context<'_>, buf: &[u8]) -> Poll<io::Result<usize>> {
        Poll::Ready(Ok(buf.len()))
    }

    fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Poll::Ready(Ok(()))
    }

    fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Poll::Ready(Ok(()))
    }
}

impl From<PipedChild> for GuestStream {
    fn from(child: PipedChild) -> Self {
        Self::from_io(child)
    }
}

impl fmt::Debug for GuestStream {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("GuestStream").finish_non_exhaustive()
    }
}

impl AsyncRead for GuestStream {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.io).poll_read(cx, buf)
    }
}

impl AsyncWrite for GuestStream {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &[u8]) -> Poll<io::Result<usize>> {
        Pin::new(&mut *self.io).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.io).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.io).poll_shutdown(cx)
    }
}

/// How one piped child ended, as the reader and the writer see it.
#[derive(Clone, Debug)]
struct Ended {
    code: i32,
    /// The last non-empty line of stderr, bounded, or empty.
    said: String,
}

/// A child whose stdin and stdout are a byte stream. [`Runner::spawn_piped`] starts one.
///
/// A supervisor task owns the child process. It reaps the child, keeps the tail of its stderr, and forwards a
/// cancellation of the operation. Dropping this value tells the supervisor, which then kills the whole process
/// group, so a dropped connection strands no `tart exec` and no guest relay.
pub struct PipedChild {
    stdin: Option<ChildStdin>,
    stdout: ChildStdout,
    /// The command as a refusal names it: the base name of the program and its arguments.
    command: String,
    /// Set by the supervisor once the child is reaped.
    ended: Arc<OnceLock<Ended>>,
    /// Resolves for the reader when [`PipedChild::ended`] is set. `None` after it resolved.
    ended_notice: Option<oneshot::Receiver<()>>,
    /// The same notice for the writer. A receiver keeps one waker, and a split stream reads and writes on two
    /// tasks.
    write_notice: Option<oneshot::Receiver<()>>,
    /// Dropped with this value, which is how the supervisor learns that the stream was dropped.
    _dropped: oneshot::Sender<()>,
    /// Whether the reader received at least one byte.
    delivered: bool,
    /// Whether stdout reached EOF.
    stdout_eof: bool,
    /// One of the two grace timers above, whichever case the reader met first. A stream meets at most one.
    grace: Option<Pin<Box<Sleep>>>,
    /// The writer's wait for the exit status after a closed stdin, bounded by [`EXIT_AFTER_EOF_GRACE`].
    write_grace: Option<Pin<Box<Sleep>>>,
    /// The refusal the reader answered. Every later read receives it again.
    failure: Option<String>,
}

impl fmt::Debug for PipedChild {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("PipedChild")
            .field("command", &self.command)
            .field("ended", &self.ended.get())
            .field("delivered", &self.delivered)
            .finish_non_exhaustive()
    }
}

impl Runner {
    /// Starts `argv` with piped stdin and stdout, and answers the two pipes as one byte stream.
    ///
    /// The child is like the child of [`Runner::capture`]: it has the scrubbed environment and its own process
    /// group, the interrupt service reaches that group, and a cancellation of the operation asks it to stop. The
    /// phase of `ctx` records the child when it ends, with the lifetime of the stream as its time.
    ///
    /// The reader sees the failure contract of the module. A child that ends before the first byte makes the read
    /// fail with [`io::ErrorKind::ConnectionRefused`], and the message quotes the command, its exit status and the
    /// last line of its stderr. After the first byte, the end of the child is an ordinary EOF. A write after a
    /// failed exit before the first byte answers the same refusal as the reader. A write after the first byte, or
    /// after a clean exit, fails with [`io::ErrorKind::BrokenPipe`].
    ///
    /// Must run inside a tokio runtime, which runs the supervisor task of the child.
    pub fn spawn_piped(&self, ctx: &Ctx, argv: &[String]) -> Result<PipedChild, Refusal> {
        check_argv(argv)?;
        let started = Instant::now();
        let mut command = self.command(argv, None, None);
        command
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped());
        let mut spawned = match self.spawn(command, argv, Interruptible::Yes) {
            Ok(spawned) => spawned,
            Err(refusal) => {
                ctx.phase().record_subprocess(argv, started.elapsed());
                return Err(refusal);
            }
        };
        let (Some(stdin), Some(stdout), Some(stderr)) =
            (spawned.child.stdin.take(), spawned.child.stdout.take(), spawned.child.stderr.take())
        else {
            return Err(Refusal::internal("a piped child has no pipes"));
        };
        let ended = Arc::new(OnceLock::new());
        let (notify, ended_notice) = oneshot::channel();
        let (notify_writer, write_notice) = oneshot::channel();
        let (dropped, stop) = oneshot::channel();
        tokio::spawn(supervise(Supervised {
            spawned,
            ctx: ctx.clone(),
            argv: argv.to_vec(),
            started,
            stderr,
            ended: Arc::clone(&ended),
            notify: [notify, notify_writer],
            stop,
        }));
        Ok(PipedChild {
            stdin: Some(stdin),
            stdout,
            command: std::iter::once(base_name(&argv[0]))
                .chain(argv[1..].iter().map(String::as_str))
                .collect::<Vec<_>>()
                .join(" "),
            ended,
            ended_notice: Some(ended_notice),
            write_notice: Some(write_notice),
            _dropped: dropped,
            delivered: false,
            stdout_eof: false,
            grace: None,
            write_grace: None,
            failure: None,
        })
    }
}

/// What the supervisor task owns.
struct Supervised {
    spawned: Spawned,
    ctx: Ctx,
    argv: Vec<String>,
    started: Instant,
    stderr: ChildStderr,
    ended: Arc<OnceLock<Ended>>,
    notify: [oneshot::Sender<()>; 2],
    stop: oneshot::Receiver<()>,
}

/// Reaps the child and publishes how it ended, then waits for the stream to drop.
///
/// A stream that drops before the child ended drops the spawn with its guard armed: the guard signals the group
/// and the leader is killed and reaped in the background, as for an abandoned [`Runner::capture`]. After the child
/// ended, the guard stays armed only while the group has a member. A member keeps the group id from reuse, so the
/// signal reaches only this group. An empty group has an id the kernel can give to another process, so the
/// supervisor disarms the guard when it finds the group empty: once after the reap, and again when the stream
/// drops. This covers every path, whether the reader, the writer or neither saw the end of the child.
async fn supervise(supervised: Supervised) {
    let Supervised {
        mut spawned,
        ctx,
        argv,
        started,
        stderr,
        ended,
        notify,
        mut stop,
    } = supervised;
    let mut tail = std::pin::pin!(stderr_tail(stderr));
    let mut said = None;
    let mut asked_to_stop = false;
    let status = loop {
        tokio::select! {
            status = spawned.child.wait() => break status,
            line = &mut tail, if said.is_none() => said = Some(line),
            _ = &mut stop => {
                ctx.phase().record_subprocess(&argv, started.elapsed());
                return;
            }
            () = ctx.cancelled(), if !asked_to_stop => {
                spawned.guard.stop();
                asked_to_stop = true;
            }
        }
    };
    let said = match said {
        Some(said) => said,
        None => said_after_exit(&mut tail, group_has_member(&spawned)).await,
    };
    let outcome = match status {
        Ok(status) => Ended {
            code: exit_code(status),
            said,
        },
        Err(error) => Ended {
            code: 1,
            said: format!("could not wait for it: {error}"),
        },
    };
    disarm_when_empty(&mut spawned);
    let _ = ended.set(outcome);
    for notify in notify {
        let _ = notify.send(());
    }
    let _ = stop.await;
    disarm_when_empty(&mut spawned);
    ctx.phase().record_subprocess(&argv, started.elapsed());
}

/// Disarms the guard of a reaped leader when its process group has no member left, and takes the group out of the
/// interrupt service with it. So neither the drop nor a signal to the controller reaches a group id the kernel may
/// have given to another process.
fn disarm_when_empty(spawned: &mut Spawned) {
    if spawned.guard.armed && !group_has_member(spawned) {
        spawned.guard.armed = false;
        spawned.guard.registration = None;
    }
}

/// Whether the process group of a reaped leader still has a member, which can hold the pipes the leader had.
fn group_has_member(spawned: &Spawned) -> bool {
    spawned.guard.group.as_ref().is_some_and(|group| !group.is_empty())
}

/// What stderr said, when the child exited before `tail` reached EOF. With `group_has_member`, the wait is
/// [`STDERR_AFTER_EXIT_GRACE`], else it reads to EOF within [`STDERR_TO_EOF_BOUND`]. The constants say why.
pub(super) async fn said_after_exit(tail: impl Future<Output = String>, group_has_member: bool) -> String {
    let bound = if group_has_member {
        STDERR_AFTER_EXIT_GRACE
    } else {
        STDERR_TO_EOF_BOUND
    };
    tokio::time::timeout(bound, tail).await.unwrap_or_default()
}

/// Reads stderr to its end and answers the last non-empty line of its newest [`FAILURE_OUTPUT_TAIL_BYTES`].
async fn stderr_tail(mut stderr: ChildStderr) -> String {
    let mut kept: Vec<u8> = Vec::new();
    let mut chunk = vec![0; 8 * 1024];
    loop {
        match stderr.read(&mut chunk).await {
            Ok(0) | Err(_) => break,
            Ok(read) => {
                kept.extend_from_slice(&chunk[..read]);
                if kept.len() > FAILURE_OUTPUT_TAIL_BYTES {
                    kept.drain(..kept.len() - FAILURE_OUTPUT_TAIL_BYTES);
                }
            }
        }
    }
    String::from_utf8_lossy(&kept)
        .lines()
        .map(str::trim)
        .rfind(|line| !line.is_empty())
        .unwrap_or_default()
        .to_owned()
}

impl PipedChild {
    /// How the child ended, when the supervisor has published it. Registers the reader's waker while it has not.
    fn poll_ended(&mut self, cx: &mut Context<'_>) -> Poll<Option<Ended>> {
        ready!(poll_notice(&mut self.ended_notice, cx));
        Poll::Ready(self.ended.get().cloned())
    }

    /// Polls the grace timer, and starts it with `duration` when no timer runs yet.
    fn poll_grace(&mut self, cx: &mut Context<'_>, duration: Duration) -> Poll<()> {
        self.grace
            .get_or_insert_with(|| Box::pin(tokio::time::sleep(duration)))
            .as_mut()
            .poll(cx)
    }

    /// The refused connection of a child that failed before the first byte, as the reader answers it. The first
    /// refusal fixes the message for every later read.
    fn refusal(&mut self, ended: &Ended) -> io::Error {
        let message = self.failure.get_or_insert_with(|| refusal_message(&self.command, ended));
        io::Error::new(io::ErrorKind::ConnectionRefused, message.clone())
    }
}

/// Polls one notice of the supervisor. An error is a supervisor that ended without a notice. Then the cell tells
/// whether it published.
fn poll_notice(notice: &mut Option<oneshot::Receiver<()>>, cx: &mut Context<'_>) -> Poll<()> {
    if let Some(receiver) = notice {
        let _ = ready!(Pin::new(receiver).poll(cx));
        *notice = None;
    }
    Poll::Ready(())
}

/// Why the connection of `command` never opened.
///
/// An installed agent that answers [`AgentExit::Usage`] is older than this controller and has no `relay` verb.
/// The message says so, because "exited with 64" names no repair.
fn refusal_message(command: &str, ended: &Ended) -> String {
    let usage = AgentExit::Usage.code();
    if ended.code == usage {
        format!("the installed guest agent has no relay verb (exit {usage})")
    } else if ended.said.is_empty() {
        format!("{command} exited with {}; it printed nothing", ended.code)
    } else {
        format!("{command} exited with {}: {}", ended.code, reason(&ended.said))
    }
}

/// The reason in the last stderr line: the message of a failure envelope, else the line itself.
fn reason(said: &str) -> String {
    ReceivedEnvelope::read(said)
        .ok()
        .and_then(|envelope| envelope.error)
        .map(|error| error.message)
        .filter(|message| !message.is_empty())
        .unwrap_or_else(|| said.to_owned())
}

impl AsyncRead for PipedChild {
    fn poll_read(self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        let this = self.get_mut();
        if let Some(failure) = &this.failure {
            return Poll::Ready(Err(io::Error::new(io::ErrorKind::ConnectionRefused, failure.clone())));
        }
        if !this.stdout_eof {
            let before = buf.filled().len();
            match Pin::new(&mut this.stdout).poll_read(cx, buf) {
                Poll::Ready(Ok(())) if buf.filled().len() > before => {
                    this.delivered = true;
                    return Poll::Ready(Ok(()));
                }
                // A read into a full buffer says nothing about the stream.
                Poll::Ready(Ok(())) if buf.remaining() == 0 => return Poll::Ready(Ok(())),
                Poll::Ready(Ok(())) => this.stdout_eof = true,
                Poll::Ready(Err(error)) => return Poll::Ready(Err(error)),
                Poll::Pending => {}
            }
        }
        if this.delivered {
            return if this.stdout_eof { Poll::Ready(Ok(())) } else { Poll::Pending };
        }
        // No byte yet, so the exit status decides what the end of the stream means.
        match this.poll_ended(cx) {
            Poll::Ready(Some(ended)) if ended.code == 0 => {
                if this.stdout_eof {
                    Poll::Ready(Ok(()))
                } else {
                    Poll::Pending
                }
            }
            Poll::Ready(Some(ended)) => {
                if !this.stdout_eof {
                    ready!(this.poll_grace(cx, BYTES_AFTER_EXIT_GRACE));
                }
                Poll::Ready(Err(this.refusal(&ended)))
            }
            // The supervisor ended without a status: the runtime is shutting down.
            Poll::Ready(None) => Poll::Ready(Ok(())),
            Poll::Pending => {
                if !this.stdout_eof {
                    return Poll::Pending;
                }
                ready!(this.poll_grace(cx, EXIT_AFTER_EOF_GRACE));
                Poll::Ready(Ok(()))
            }
        }
    }
}

impl PipedChild {
    /// The error of a write after the child ended. Before the first byte, a failed child is a connection that
    /// never opened, so the writer answers the refusal of the reader. A client that is still writing a large
    /// request body then receives the reason too.
    ///
    /// The writer does not fix the refusal for the reader. The child can have written a reply before it failed, and
    /// the reader must still receive that reply.
    fn write_error(&self, ended: &Ended) -> io::Error {
        if !self.delivered && ended.code != 0 {
            let message = self.failure.clone().unwrap_or_else(|| refusal_message(&self.command, ended));
            io::Error::new(io::ErrorKind::ConnectionRefused, message)
        } else {
            self.broken_pipe()
        }
    }

    fn broken_pipe(&self) -> io::Error {
        let reason = self.ended.get().map_or_else(
            || "its stdin is closed".to_owned(),
            |ended| format!("it exited with {}", ended.code),
        );
        io::Error::new(io::ErrorKind::BrokenPipe, format!("cannot write to {}: {reason}", self.command))
    }
}

impl AsyncWrite for PipedChild {
    fn poll_write(self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &[u8]) -> Poll<io::Result<usize>> {
        let this = self.get_mut();
        if let Some(ended) = this.ended.get().cloned() {
            return Poll::Ready(Err(this.write_error(&ended)));
        }
        let Some(stdin) = &mut this.stdin else {
            return Poll::Ready(Err(this.broken_pipe()));
        };
        match ready!(Pin::new(stdin).poll_write(cx, buf)) {
            Err(error) if error.kind() == io::ErrorKind::BrokenPipe => {
                // The child closed its stdin, so it is ending. Its exit status decides the error, but the wait for
                // it is bounded: a child can close stdin and stay alive.
                if !this.delivered {
                    let notice = poll_notice(&mut this.write_notice, cx);
                    if notice.is_pending() {
                        ready!(
                            this.write_grace
                                .get_or_insert_with(|| Box::pin(tokio::time::sleep(EXIT_AFTER_EOF_GRACE)))
                                .as_mut()
                                .poll(cx)
                        );
                    }
                }
                match this.ended.get().cloned() {
                    Some(ended) => Poll::Ready(Err(this.write_error(&ended))),
                    None => Poll::Ready(Err(this.broken_pipe())),
                }
            }
            written => Poll::Ready(written),
        }
    }

    fn poll_flush(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        let this = self.get_mut();
        match &mut this.stdin {
            Some(stdin) => Pin::new(stdin).poll_flush(cx),
            None => Poll::Ready(Ok(())),
        }
    }

    /// Closes stdin. This is the end of the writes of the connection, and not the end of one request: an HTTP client
    /// shuts a connection down only when it closes it. What the child still writes stays readable.
    fn poll_shutdown(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        let this = self.get_mut();
        if let Some(stdin) = &mut this.stdin {
            ready!(Pin::new(stdin).poll_flush(cx))?;
        }
        this.stdin = None;
        Poll::Ready(Ok(()))
    }
}
