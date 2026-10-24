//! A run on a host, and the requests of one reply.
//!
//! A run:
//!
//! 1. Refuses a checkout with local changes, because only the commit reaches the host.
//! 2. Asks the host which of the candidate commits it has (`Have`), and sends a thin pack of the rest (`Pack`) to
//!    `refs/yonder/<run>`. `pack.rs` builds the pack.
//! 3. Starts the run (`Start`). The run lives on the agent, not on the connection. Ctrl+C before this step stops the
//!    local work, and nothing runs on the host.
//! 4. Follows the output (`Follow`). Each stderr line goes to the stderr sink as it comes, and the stdout lines are
//!    collected. A connection that is quiet for 60 s is lost: the client connects again from the last offset, with a
//!    backoff, for up to 10 min after the last reply of the agent. The first Ctrl+C sends `Cancel` on a thread of its
//!    own, and the output follows up to its end. The second Ctrl+C stops the follow at once.
//! 5. Copies the staged files of the `fetch` directories into the local checkout (`Fetch`), then removes the run on
//!    the agent (`Forget`). The client removes the run only when it saw the end of the run and every file came.
//!    Otherwise the run stays on the agent, and the client names `yonder fetch` and `yonder forget`.

use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, PoisonError, mpsc};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use yonder_link::Closer;
use yonder_link::proto::{Reply, Request, RunInfo, RunSpec, Stream};

use crate::git::{candidates, clean_head};
use crate::host::{AgentInfo, Host};
use crate::pack::PackTarget;
use crate::{Client, Refusal, Timing, code, refused};

/// How often the watch of a run asks whether the person pressed Ctrl+C.
const INTERRUPT_POLL: Duration = Duration::from_millis(100);

/// How long the cancel of a run connects again after a failed try.
const CANCEL_FOR: Duration = Duration::from_secs(120);

/// How long the end of a run waits for the last lines of its cancel.
const CANCEL_GRACE: Duration = Duration::from_millis(500);

/// What a run does, from the side of the client.
#[derive(Clone, Copy, Debug)]
pub struct RunRequest<'a> {
    /// The root of the local checkout: its HEAD runs, and the fetched files go below it.
    pub checkout: &'a Path,
    /// The main repository on the host, or `None` for the one of the host file.
    pub repo: Option<&'a str>,
    /// The lease holder on the host: one per local checkout.
    pub holder: &'a str,
    /// The command and its arguments, run in the root of the slot.
    pub command: &'a [String],
    /// Directories relative to the slot root, such as `out/bt-runs`, whose new entries come back.
    pub fetch: &'a [String],
    pub idle_hours: u32,
}

/// The end of a run.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RunOutcome {
    pub run: String,
    pub commit: String,
    /// The exit code of the command. A command that a signal ended answers 128 plus the signal number.
    pub code: i32,
    /// The stdout lines of the command, each with its line end.
    pub stdout: String,
    /// The files that came back, relative to the checkout root with `/` separators. Empty when the fetch failed; a
    /// stderr line then names `yonder fetch`.
    pub fetched: Vec<String>,
}

/// The end of a followed run.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Followed {
    pub code: i32,
    pub stdout: String,
}

/// The lease of a holder on a host.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Leased {
    pub slot: String,
    pub lease_id: String,
    /// `false` when the holder had the lease already.
    pub fresh: bool,
}

/// Why one connection of `Follow` ended without the end of the run.
enum Broken {
    /// The link failed: connect again.
    Link(Refusal),
    /// The agent refused, or the run failed: stop.
    Refused(Refusal),
}

/// How a followed run ended.
enum Ended {
    /// The command exited.
    Exit(Followed),
    /// The agent ended the run with a refusal of its own, such as `lease_failed`.
    Refused(Refusal),
    /// The client did not see the end: the link is lost, or a second Ctrl+C stopped the follow.
    Unseen(Refusal),
}

/// What the watch of a run and the follow share.
struct Watch {
    /// The follow ended.
    finished: Arc<AtomicBool>,
    /// The connection of the follow, which a second Ctrl+C closes.
    current: Mutex<Option<Closer>>,
    /// A second Ctrl+C came: the follow stops.
    abandoned: AtomicBool,
}

impl Client<'_> {
    /// Runs a command of the committed HEAD of the local checkout on the host of `alias`.
    pub fn run(&self, alias: &str, request: &RunRequest<'_>) -> Result<RunOutcome, Refusal> {
        if request.command.is_empty() {
            return Err(refused(code::USAGE, "a run needs a command"));
        }
        let mut host = self.host(alias)?;
        let repo = request.repo.unwrap_or(&host.file().repo).to_owned();
        let commit = clean_head(self.processes, request.checkout)?;
        self.refuse_interrupt()?;
        let run = run_id();
        let reference = format!("refs/yonder/{run}");

        let candidates = candidates(self.processes, request.checkout, &commit);
        let present = self.have(&mut host, &repo, candidates)?;
        self.refuse_interrupt()?;
        if !present.contains(&commit) {
            let target = PackTarget {
                checkout: request.checkout,
                repo: &repo,
                commit: &commit,
                reference: &reference,
            };
            self.send_pack(&mut host, &target, &present)?;
        }
        self.refuse_interrupt()?;
        let spec = RunSpec {
            run: run.clone(),
            repo,
            holder: request.holder.to_owned(),
            commit: commit.clone(),
            reference,
            command: request.command.to_vec(),
            fetch: request.fetch.to_vec(),
            idle_hours: request.idle_hours,
        };
        match host.request(&Request::Start(spec), self.timing.read)? {
            (_, Reply::Started { .. }) => {}
            (_, Reply::Refused { code, message }) => return Err(host.agent_refusal(code, &message)),
            (_, reply) => return Err(host.unexpected(&reply)),
        }
        (self.say)(&format!("yonder: run {run} of {} on {alias}", short(&commit)));

        let followed = match self.follow(&mut host, &run, true) {
            Ended::Exit(followed) => followed,
            Ended::Refused(refusal) => {
                self.forget_or_say(&mut host, &run);
                return Err(refusal);
            }
            Ended::Unseen(refusal) => return Err(refusal),
        };
        let fetched = if request.fetch.is_empty() {
            Ok(Vec::new())
        } else {
            self.fetch_from(&mut host, &run, request.checkout, request.fetch)
        };
        let fetched = match fetched {
            Ok(fetched) => {
                self.forget_or_say(&mut host, &run);
                fetched
            }
            Err(refusal) => {
                (self.say)(&format!(
                    "yonder: cannot copy the files of the run {run} from {alias}: {}. The run stays on {alias}: `yonder fetch {alias} {run}` copies its files again, and `yonder forget {alias} {run}` removes it",
                    refusal.message
                ));
                Vec::new()
            }
        };
        Ok(RunOutcome {
            run,
            commit,
            code: followed.code,
            stdout: followed.stdout,
            fetched,
        })
    }

    /// Follows a run from its first line, as [`Client::run`] does. Ctrl+C here cancels nothing.
    pub fn attach(&self, alias: &str, run: &str) -> Result<Followed, Refusal> {
        let mut host = self.host(alias)?;
        match self.follow(&mut host, run, false) {
            Ended::Exit(followed) => Ok(followed),
            Ended::Refused(refusal) | Ended::Unseen(refusal) => Err(refusal),
        }
    }

    /// Asks the agent to interrupt a run. The answer comes as soon as the host has the request; the run ends later,
    /// and [`Client::attach`] shows its end.
    pub fn cancel(&self, alias: &str, run: &str) -> Result<String, Refusal> {
        let mut host = self.host(alias)?;
        cancel_on(&mut host, run, self.timing.read)
    }

    /// The runs that the agent keeps.
    pub fn runs(&self, alias: &str) -> Result<Vec<RunInfo>, Refusal> {
        let mut host = self.host(alias)?;
        match host.request(&Request::Runs, self.timing.read)? {
            (_, Reply::Runs { runs }) => Ok(runs),
            (_, Reply::Refused { code, message }) => Err(host.agent_refusal(code, &message)),
            (_, reply) => Err(host.unexpected(&reply)),
        }
    }

    /// Takes or reuses the lease of `holder` without a run.
    pub fn lease(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<Leased, Refusal> {
        let mut host = self.host(alias)?;
        let repo = repo.unwrap_or(&host.file().repo).to_owned();
        let request = Request::Lease {
            repo,
            holder: holder.to_owned(),
        };
        match host.request(&request, self.timing.read)? {
            (_, Reply::Leased { slot, lease_id, fresh }) => Ok(Leased { slot, lease_id, fresh }),
            (_, Reply::Refused { code, message }) => Err(host.agent_refusal(code, &message)),
            (_, reply) => Err(host.unexpected(&reply)),
        }
    }

    /// Returns the lease of `holder`.
    pub fn release(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<String, Refusal> {
        let mut host = self.host(alias)?;
        let repo = repo.unwrap_or(&host.file().repo).to_owned();
        let request = Request::Release {
            repo,
            holder: holder.to_owned(),
        };
        done(&mut host, &request, self.timing.read)
    }

    /// The hello of the agent: its version and platform, and the address that answered.
    pub fn hello(&self, alias: &str) -> Result<AgentInfo, Refusal> {
        let mut host = self.host(alias)?;
        let (channel, info) = host.connect()?;
        channel.close();
        Ok(info)
    }

    /// The commits of `candidates` that the host has.
    fn have(&self, host: &mut Host, repo: &str, candidates: Vec<String>) -> Result<Vec<String>, Refusal> {
        let request = Request::Have {
            repo: repo.to_owned(),
            commits: candidates,
        };
        match host.request(&request, self.timing.read)? {
            (_, Reply::Have { commits }) => Ok(commits),
            (_, Reply::Refused { code, message }) => Err(host.agent_refusal(code, &message)),
            (_, reply) => Err(host.unexpected(&reply)),
        }
    }

    fn refuse_interrupt(&self) -> Result<(), Refusal> {
        if self.interrupted() {
            Err(refused(
                code::INTERRUPTED,
                "Ctrl+C came before the run started; nothing runs on the host",
            ))
        } else {
            Ok(())
        }
    }

    fn forget_or_say(&self, host: &mut Host, run: &str) {
        if let Err(refusal) = self.forget_on(host, run) {
            (self.say)(&format!(
                "yonder: cannot remove the run {run} on {}: {}. `yonder forget {} {run}` removes it",
                host.alias(),
                refusal.message,
                host.alias()
            ));
        }
    }

    /// Follows the output of a run up to its end. With `cancel_on_interrupt`, the first Ctrl+C sends `Cancel`, and the
    /// second one stops the follow.
    fn follow(&self, host: &mut Host, run: &str, cancel_on_interrupt: bool) -> Ended {
        let watch = Watch {
            finished: Arc::new(AtomicBool::new(false)),
            current: Mutex::new(None),
            abandoned: AtomicBool::new(false),
        };
        let watched = host.clone();
        thread::scope(|scope| {
            if cancel_on_interrupt {
                scope.spawn(|| self.watch_interrupts(&watched, run, &watch));
            }
            let ended = self.follow_loop(host, run, &watch);
            watch.finished.store(true, Ordering::Release);
            ended
        })
    }

    /// Reads the Ctrl+C count while the run goes on. The cancel runs on a thread that the scope does not wait for, so a
    /// cancel that hangs never holds the end of the run.
    fn watch_interrupts(&self, host: &Host, run: &str, watch: &Watch) {
        let mut cancel: Option<mpsc::Receiver<String>> = None;
        while !watch.finished.load(Ordering::Acquire) {
            let presses = (self.presses)();
            if presses >= 1 && cancel.is_none() {
                (self.say)(&format!(
                    "yonder: Ctrl+C: cancelling the run {run} on {}; its output follows up to its end. A second Ctrl+C stops the follow",
                    host.alias()
                ));
                cancel = Some(start_cancel(host.clone(), run.to_owned(), self.timing, Arc::clone(&watch.finished)));
            }
            if presses >= 2
                && !watch.abandoned.swap(true, Ordering::AcqRel)
                && let Some(closer) = watch.current.lock().unwrap_or_else(PoisonError::into_inner).take()
            {
                closer.close();
            }
            if let Some(said) = &cancel {
                while let Ok(line) = said.try_recv() {
                    (self.say)(&line);
                }
            }
            thread::sleep(INTERRUPT_POLL);
        }
        // The answer of the cancel can come just after the end of the run; it gets a moment, not more.
        if let Some(said) = cancel {
            let until = Instant::now() + CANCEL_GRACE;
            while let Ok(line) = said.recv_timeout(until.saturating_duration_since(Instant::now())) {
                (self.say)(&line);
            }
        }
    }

    fn follow_loop(&self, host: &mut Host, run: &str, watch: &Watch) -> Ended {
        let mut state = FollowState {
            offset: 0,
            stdout: String::new(),
            last_reply: Instant::now(),
            backoff: self.timing.first_backoff,
        };
        loop {
            let once = self.follow_once(host, run, watch, &mut state);
            if watch.abandoned.load(Ordering::Acquire) {
                return Ended::Unseen(refused(
                    code::INTERRUPTED,
                    format!(
                        "a second Ctrl+C stopped the follow of the run {run}; {} has its cancel. `yonder attach {} {run}` shows its end",
                        host.alias(),
                        host.alias()
                    ),
                ));
            }
            match once {
                Ok(code) => {
                    return Ended::Exit(Followed {
                        code,
                        stdout: state.stdout,
                    });
                }
                Err(Broken::Refused(refusal)) if is_local(&refusal) => return Ended::Unseen(refusal),
                Err(Broken::Refused(refusal)) => return Ended::Refused(refusal),
                Err(Broken::Link(refusal)) => {
                    if state.last_reply.elapsed() >= self.timing.reconnect_for {
                        return Ended::Unseen(Refusal::new(
                            refusal.code.clone(),
                            crate::Exit::Refused,
                            format!(
                                "{}; no reply for {} s. The run can go on on the host: `yonder attach {} {run}` follows it",
                                refusal.message,
                                self.timing.reconnect_for.as_secs(),
                                host.alias()
                            ),
                        ));
                    }
                    (self.say)(&format!(
                        "yonder: {}; following the run {run} again from byte {} in {} s",
                        refusal.message,
                        state.offset,
                        state.backoff.as_secs()
                    ));
                    sleep_unless(state.backoff, &watch.abandoned);
                    state.backoff = (state.backoff * 2).min(self.timing.max_backoff);
                }
            }
        }
    }

    /// One connection of `Follow`. Answers the exit code at the end of the run.
    fn follow_once(&self, host: &mut Host, run: &str, watch: &Watch, state: &mut FollowState) -> Result<i32, Broken> {
        let (mut channel, _) = host.connect().map_err(classify)?;
        *watch.current.lock().unwrap_or_else(PoisonError::into_inner) = channel.closer().ok();
        if watch.abandoned.load(Ordering::Acquire) {
            return Err(Broken::Link(refused(code::INTERRUPTED, "a second Ctrl+C")));
        }
        let request = Request::Follow {
            run: run.to_owned(),
            offset: state.offset,
        };
        channel.send(&request).map_err(|error| Broken::Link(host.link_refusal(&error)))?;
        loop {
            let mut heard = || {
                state.last_reply = Instant::now();
                state.backoff = self.timing.first_backoff;
            };
            let reply = host.recv_beating(&mut channel, self.timing.read, &mut heard).map_err(classify)?;
            heard();
            match reply {
                Reply::Output { stream, text, end } => {
                    // A record that a lost connection had delivered already comes again after the new `Follow`.
                    if end > state.offset {
                        state.offset = end;
                        match stream {
                            Stream::Stderr => (self.say)(&text),
                            Stream::Stdout => {
                                state.stdout.push_str(&text);
                                state.stdout.push('\n');
                            }
                        }
                    }
                }
                Reply::Exit { code } => {
                    channel.close();
                    return Ok(code);
                }
                Reply::Refused { code, message } => return Err(Broken::Refused(host.agent_refusal(code, &message))),
                reply => return Err(Broken::Refused(host.unexpected(&reply))),
            }
        }
    }
}

/// What a follow keeps across its connections.
struct FollowState {
    offset: u64,
    stdout: String,
    last_reply: Instant,
    backoff: Duration,
}

/// Sends `Cancel`. The agent answers as soon as the host has the request.
fn cancel_on(host: &mut Host, run: &str, deadline: Duration) -> Result<String, Refusal> {
    done(host, &Request::Cancel { run: run.to_owned() }, deadline)
}

/// Sends `Cancel` on a thread of its own, and answers the lines that it has to say. It connects again with the
/// backoff of `Follow` until the agent has the cancel, the run has ended, or [`CANCEL_FOR`] has passed.
fn start_cancel(mut host: Host, run: String, timing: Timing, finished: Arc<AtomicBool>) -> mpsc::Receiver<String> {
    let (said, lines) = mpsc::channel();
    let failed = said.clone();
    let cancelling = thread::Builder::new().name("yonder-cancel".to_owned()).spawn(move || {
        let started = Instant::now();
        let mut backoff = timing.first_backoff;
        loop {
            match cancel_on(&mut host, &run, timing.read) {
                Ok(message) => {
                    let _ = said.send(format!("yonder: {message}"));
                    return;
                }
                Err(refusal) if is_link(&refusal) && started.elapsed() < CANCEL_FOR && !finished.load(Ordering::Acquire) => {
                    let _ = said.send(format!(
                        "yonder: cannot send the cancel of the run {run}: {}; again in {} s",
                        refusal.message,
                        backoff.as_secs()
                    ));
                    thread::sleep(backoff);
                    backoff = (backoff * 2).min(timing.max_backoff);
                }
                Err(refusal) => {
                    let _ = said.send(format!("yonder: cannot cancel the run {run}: {}", refusal.message));
                    return;
                }
            }
        }
    });
    if let Err(error) = cancelling {
        let _ = failed.send(format!("yonder: cannot start the cancel: {error}"));
    }
    lines
}

/// Sleeps for `time`, and wakes early when `stop` turns `true`.
fn sleep_unless(time: Duration, stop: &AtomicBool) {
    let until = Instant::now() + time;
    while !stop.load(Ordering::Acquire) {
        let left = until.saturating_duration_since(Instant::now());
        if left.is_zero() {
            return;
        }
        thread::sleep(left.min(INTERRUPT_POLL));
    }
}

/// Whether a refusal is a lost link, so that a new connection can do better.
fn is_link(refusal: &Refusal) -> bool {
    matches!(refusal.code.as_ref(), code::UNREACHABLE | code::TIMED_OUT | code::LINK)
}

/// Whether a refusal comes from this client and not from the agent: the run can go on on the host.
fn is_local(refusal: &Refusal) -> bool {
    is_link(refusal)
        || matches!(
            refusal.code.as_ref(),
            code::UNTRUSTED | code::SCHEMA | code::CONFIG | code::NO_HOST | code::BAD_HOST_FILE | code::INTERRUPTED
        )
}

/// A request whose reply is `Done`.
pub(crate) fn done(host: &mut Host, request: &Request, deadline: Duration) -> Result<String, Refusal> {
    match host.request(request, deadline)? {
        (channel, Reply::Done { message }) => {
            channel.close();
            Ok(message)
        }
        (_, Reply::Refused { code, message }) => Err(host.agent_refusal(code, &message)),
        (_, reply) => Err(host.unexpected(&reply)),
    }
}

/// A failed connect of `Follow`: a lost link connects again, anything else stops.
fn classify(refusal: Refusal) -> Broken {
    if is_link(&refusal) {
        Broken::Link(refusal)
    } else {
        Broken::Refused(refusal)
    }
}

/// The id of a new run: `<epoch ms>-<pid>`.
fn run_id() -> String {
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |elapsed| elapsed.as_millis());
    format!("{millis}-{}", std::process::id())
}

fn short(commit: &str) -> &str {
    commit.get(..12).unwrap_or(commit)
}

#[cfg(test)]
mod tests;
