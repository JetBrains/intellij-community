//! A run on a host, and the requests of one reply.
//!
//! A run:
//!
//! 1. Reads the snapshot of HEAD with `bd host snapshot` (`bd.rs`): the commit, the candidate commits that the host
//!    may have, and `.yonder.toml`. Then it refuses a checkout with local changes, because only the commit reaches the
//!    host, and meanwhile it asks the host which of the candidates it has (`Have`). The candidates include the base
//!    that the last base sync of the holder recorded. A host that has none of them refuses with `behind` here. With
//!    [`RunOptions::working_tree`] it takes the commit of the working tree instead, which bd writes, and asks the host
//!    after it. With [`RunOptions::repo_by_name`] the first `Have` goes to the
//!    repository of the same name as the local one beside the repository of the host file, and to the one of the host
//!    file when the host lacks it. The hello of the agent names its features: a run that needs one that the agent
//!    lacks refuses here, and an option that the agent would ignore gets a warning.
//! 2. Starts the run (`Start`). The run lives on the agent, not on the connection. Ctrl+C before this step stops the
//!    local work, and nothing runs on the host. A start whose reply does not come is cancelled, so the host does not
//!    wait for its pack.
//! 3. Follows the output (`Follow`) on a thread of its own from the start on, so the steps of the host show while the
//!    pack builds. Each stderr line goes to the stderr sink as it comes, and the stdout lines are collected. A
//!    connection that is quiet for 120 s is lost: the client connects again from the last offset, with a backoff, for
//!    up to 10 min after the last reply of the agent. `Reply::Phases` before the end gives the steps of the host.
//! 4. Meanwhile it builds the thin pack of the commits that the host lacks, while the host takes the lock and the
//!    lease, and sends it (`Pack`) to `refs/yonder/<run>` on a connection of its own. `pack.rs` builds and sends it.
//!    When the pack fails, the client cancels the run, stops the follow, and refuses with the code of the pack.
//! 5. After the pack, a thread syncs the base of the next packs, see `base.rs`.
//! 6. Copies the staged files of the `fetch` directories and the outputs into the local checkout (`Fetch`), then
//!    removes the run on the agent (`Forget`). The fetch takes only what the run record asked for: the client writes
//!    it before the start, see `record.rs`. An output replaces the local copy of its path. The client removes the run
//!    only when it saw the end of the run and every file came. Otherwise the run stays on the agent, and the client
//!    names `yonder fetch` and `yonder forget`.
//! 7. Waits for the base sync. With [`RunOptions::apply`], it applies the patch of the changes of the command to the
//!    working tree (`bd host apply`, the files only, not the index); the patch stays in `yonder/<run>.patch` of the git
//!    directory of the checkout. Then it answers the steps of the run with their durations.
//!
//! A refusal tells by its [`crate::Exit`] whether the command ran: a refusal before the command is
//! [`crate::Exit::NotRun`], so the caller can run it locally instead.
//!
//! Ctrl+C after the start, the build of the pack included, cancels the run on the host. The first press sends `Cancel`
//! on a thread of its own and stops the pack, and the output follows up to its end. The second press stops the follow
//! at once. A press after the end of the run closes the connection of the base sync.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, PoisonError, mpsc};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use yonder_link::Closer;
use yonder_link::proto::{Phase, Reply, Request, RunInfo, RunSpec, Stream};

use crate::base::BaseSync;
use crate::bd::{FARTHEST, NEAR, Scan, Snapshot};
use crate::host::{AgentInfo, Host, Latest};
use crate::pack::{PackTarget, plan_pack};
use crate::phases::{phase, phases_line};
use crate::record::{RunFiles, RunRecord, outputs_of};
use crate::{Client, Exit, Refusal, Timing, code, refused};

/// How many changed paths the refusal of local changes shows.
const SHOWN_CHANGES: usize = 5;

/// How often the watch of a run asks whether the person pressed Ctrl+C.
const INTERRUPT_POLL: Duration = Duration::from_millis(100);

/// How long the cancel of a run connects again after a failed try.
const CANCEL_FOR: Duration = Duration::from_secs(120);

/// How long the end of a run waits for the last lines of its cancel.
const CANCEL_GRACE: Duration = Duration::from_millis(500);

/// How many times the cancel of a run whose pack did not reach the host tries.
const ABANDON_TRIES: u32 = 3;

/// What a run does, from the side of the client.
#[derive(Clone, Copy, Debug)]
pub struct RunRequest<'a> {
    /// The root of the local checkout: its HEAD runs, and the fetched files go below it.
    pub checkout: &'a Path,
    /// The main repository on the host, or `None` for the one of the host file.
    pub repo: Option<&'a str>,
    /// Who the run comes from: one per local checkout. The host lends the slot this holder used last first, and the base
    /// ref of the thin packs is the holder's.
    pub holder: &'a str,
    /// The command and its arguments, run in the root of the slot.
    pub command: &'a [String],
    /// Directories relative to the slot root, such as `out/bt-runs`, whose new entries come back.
    pub fetch: &'a [String],
}

/// What a run adds to a [`RunRequest`]. The default is the run of `bt --host`: the committed HEAD of a checkout without
/// local changes, the niceness of the agent, the repository of the host file, no outputs and no changes.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "each bool is one option of a run that no other option depends on, as the flags of the CLI are"
)]
pub struct RunOptions {
    /// Run the working tree: HEAD plus the tracked and untracked changes that git does not ignore, as one commit.
    /// Without it, a checkout with local changes is refused.
    pub working_tree: bool,
    /// The niceness of the prepare steps and the command on the host, 0 to 19.
    pub nice: Option<u8>,
    /// Bring back the outputs that `.yonder.toml` of the commit names.
    pub outputs: bool,
    /// Bring back the changes of the command and apply them to the working tree.
    pub apply: bool,
    /// When [`RunRequest::repo`] names none: try the repository of the same name as the local one beside the
    /// repository of the host file first, such as `~/projects/branch-deck` beside `~/projects/idea-1`.
    pub repo_by_name: bool,
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
    /// The steps of the run in their order: `pack` and `upload` of the client, the steps of the host, `fetch`, and
    /// `total`, the whole time of the call. [`crate::phases_line`] makes the line for a person.
    pub phases: Vec<Phase>,
}

/// The end of a followed run.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Followed {
    pub code: i32,
    pub stdout: String,
    /// The steps of the run on the host, from `Reply::Phases`. Empty when the agent sent none.
    pub phases: Vec<Phase>,
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
    /// The agent ended the run with a refusal of its own, such as `lease_failed`, after the steps of the host.
    Refused(Refusal, Vec<Phase>),
    /// The client did not see the end: the link is lost, or a second Ctrl+C stopped the follow.
    Unseen(Refusal),
}

/// What the watch of a run and the follow share.
struct Watch {
    /// The follow ended.
    finished: Arc<AtomicBool>,
    /// The connection of the follow, which a second Ctrl+C closes.
    current: Mutex<Option<Closer>>,
    /// A second Ctrl+C came, or the pack of the run failed: the follow stops.
    abandoned: AtomicBool,
}

impl Watch {
    fn new() -> Self {
        Self {
            finished: Arc::new(AtomicBool::new(false)),
            current: Mutex::new(None),
            abandoned: AtomicBool::new(false),
        }
    }

    /// Stops the follow at once: it closes the connection of the follow, and no new one comes.
    fn stop_follow(&self) {
        if !self.abandoned.swap(true, Ordering::AcqRel)
            && let Some(closer) = self.current.lock().unwrap_or_else(PoisonError::into_inner).take()
        {
            closer.close();
        }
    }
}

/// A followed run whose files came.
struct Finished {
    followed: Followed,
    fetched: Fetched,
    /// The step `fetch`, when the run names fetch directories, outputs or changes.
    fetch: Option<Phase>,
}

impl Client<'_> {
    /// Runs a command of the committed HEAD of the local checkout on the host of `alias`, as `bt --host` does.
    pub fn run(&self, alias: &str, request: &RunRequest<'_>) -> Result<RunOutcome, Refusal> {
        self.run_with(alias, request, &RunOptions::default())
    }

    /// Runs a command on the host of `alias` with `options`. A refusal before the command is [`Exit::NotRun`], and Ctrl+C
    /// before the command is [`Exit::Interrupted`].
    pub fn run_with(&self, alias: &str, request: &RunRequest<'_>, options: &RunOptions) -> Result<RunOutcome, Refusal> {
        if request.command.is_empty() {
            return Err(not_run(refused(code::USAGE, "a run needs a command")));
        }
        let started = Instant::now();
        let mut host = self.host(alias).map_err(not_run)?;
        let run = run_id();
        let reference = format!("refs/yonder/{run}");
        let recorded = self.config.base(alias, request.holder);
        let (snapshot, (repo, present)) = if options.working_tree {
            let snapshot = self.snapshot(request.checkout, Scan::WorkingTree, NEAR).map_err(not_run)?;
            self.refuse_interrupt().map_err(not_run)?;
            let found = self
                .present_in_repo(&mut host, request, options, &snapshot, recorded.as_deref())
                .map_err(not_run)?;
            (snapshot, found)
        } else {
            let snapshot = self.snapshot(request.checkout, Scan::Head, NEAR).map_err(not_run)?;
            self.refuse_interrupt().map_err(not_run)?;
            // A checkout with local changes refuses at once: its refusal closes the connection of the `Have`.
            let asked = Arc::new(Latest::default());
            let (clean, found) = thread::scope(|scope| {
                let mut asking = host.watched(Arc::clone(&asked));
                let (snapshot, recorded) = (&snapshot, recorded.as_deref());
                let found = scope.spawn(move || self.present_in_repo(&mut asking, request, options, snapshot, recorded));
                let clean = self.check_clean(request.checkout);
                if clean.is_err() {
                    asked.close();
                }
                let found = found
                    .join()
                    .unwrap_or_else(|_| Err(refused(code::LINK, "the question about the commits of the host failed")));
                (clean, found)
            });
            clean.map_err(not_run)?;
            (snapshot, found.map_err(not_run)?)
        };
        let commit = snapshot.commit.clone();
        let files = RunFiles::new(&snapshot.patch_dir).map_err(not_run)?;
        let record = RunRecord {
            commit: commit.clone(),
            fetch: request.fetch.to_vec(),
            outputs: if options.outputs {
                outputs_of(&snapshot).map_err(not_run)?
            } else {
                Vec::new()
            },
            changes: options.apply,
        };
        let target = PackTarget {
            checkout: request.checkout,
            repo: &repo,
            commit: &commit,
            reference: &reference,
        };
        let plan = if present.contains(&commit) {
            None
        } else {
            Some(plan_pack(alias, &present).map_err(not_run)?)
        };
        self.refuse_interrupt().map_err(not_run)?;
        let spec = RunSpec {
            run: run.clone(),
            repo: repo.clone(),
            holder: request.holder.to_owned(),
            commit: commit.clone(),
            reference: reference.clone(),
            command: request.command.to_vec(),
            fetch: request.fetch.to_vec(),
            nice: options.nice,
            outputs: options.outputs,
            changes: options.apply,
        };
        files.write(&run, &record).map_err(not_run)?;
        self.start(&mut host, spec).map_err(not_run)?;
        (self.say)(&run_line(&run, alias, &snapshot, &repo, &host.file().repo));

        let sync = BaseSync {
            checkout: request.checkout,
            repo: &repo,
            holder: request.holder,
            commit: &commit,
            recorded: recorded.as_deref(),
            snapshot: &snapshot,
        };
        let watch = Watch::new();
        let mut phases = Vec::new();
        let ended = thread::scope(|scope| {
            let mut followed = host.clone();
            let (run, watch, record) = (&run, &watch, &record);
            let patch = files.patch(run);
            let following = scope.spawn(move || self.follow_and_fetch(&mut followed, run, request.checkout, record, &patch, watch));
            if let Some(plan) = &plan {
                match self.send_pack(&mut host, &target, plan, self.say) {
                    Ok(steps) => {
                        phases.push(phase("pack", steps.build));
                        phases.push(phase("upload", steps.upload));
                    }
                    // The watch of the follow sent the cancel of the press, and the output follows up to its end.
                    Err(_) if self.interrupted() => {}
                    Err(refusal) => {
                        let refusal = self.abandon(&mut host, run, &format!("the pack did not reach {alias}"), refusal);
                        watch.stop_follow();
                        let _ = following.join();
                        // The checkout waits for the pack, so the command never started.
                        return Err((not_run(refusal), Vec::new()));
                    }
                }
            }
            let latest = Arc::new(Latest::default());
            let mut synced = host.watched(Arc::clone(&latest));
            let syncing = scope.spawn(move || self.sync_base(&mut synced, &sync));
            let finished = following
                .join()
                .unwrap_or_else(|_| Err((refused(code::LINK, "the follow of the run failed"), Vec::new())));
            if !syncing.is_finished() {
                (self.say)(&format!(
                    "> {alias}: the sync of the base of the next packs goes on; Ctrl+C stops it"
                ));
            }
            while !syncing.is_finished() {
                if self.interrupted() {
                    latest.close();
                }
                thread::sleep(INTERRUPT_POLL);
            }
            let _ = syncing.join();
            finished
        });
        let finished = match ended {
            Ok(finished) => finished,
            Err((refusal, steps)) => {
                // A run that the agent refused has no outcome, so the client says its steps here.
                if !steps.is_empty() {
                    phases.extend(steps);
                    (self.say)(&phases_line(&phases));
                }
                return Err(refusal);
            }
        };
        if options.apply {
            self.apply_changes(alias, request.checkout, &run, &finished.fetched, finished.followed.code)?;
        }
        phases.extend(finished.followed.phases.iter().cloned());
        phases.extend(finished.fetch);
        phases.push(phase("total", started.elapsed()));
        Ok(RunOutcome {
            run,
            commit,
            code: finished.followed.code,
            stdout: finished.followed.stdout,
            fetched: finished.fetched.files,
            phases,
        })
    }

    /// The repository of the run on the host, and the candidates of `commit` that it has.
    /// With [`RunOptions::repo_by_name`], a host that lacks the repository of the local name gets the repository of the
    /// host file instead.
    fn present_in_repo(
        &self,
        host: &mut Host,
        request: &RunRequest<'_>,
        options: &RunOptions,
        snapshot: &Snapshot,
        recorded: Option<&str>,
    ) -> Result<(String, Vec<String>), Refusal> {
        let default = host.file().repo.clone();
        let named = match request.repo {
            Some(repo) => Some(repo.to_owned()),
            None if options.repo_by_name => snapshot
                .repository
                .as_deref()
                .and_then(|name| sibling(&default, name))
                .filter(|repo| *repo != default),
            None => None,
        };
        let asked = match &named {
            Some(named) => match self.present(host, named, request.checkout, &snapshot.candidates, &[recorded]) {
                Err(refusal) if refusal.code == "repo_missing" && request.repo.is_none() => {
                    (self.say)(&format!(
                        "yonder: {} has no {named}; the run goes to {default}, the repository of its host file. --repo names another one",
                        host.alias()
                    ));
                    None
                }
                found => Some(found.map(|present| (named.clone(), present))),
            },
            None => None,
        };
        let (repo, present) = match asked {
            Some(found) => found?,
            None => (
                default.clone(),
                self.present(host, &default, request.checkout, &snapshot.candidates, &[recorded])?,
            ),
        };
        Ok((repo, present))
    }

    /// Applies the patch of the changes of the command to the working tree. A patch that does not apply stays, and the
    /// refusal names it. Changes that did not come refuse too, and name the fetch that copies them.
    fn apply_changes(&self, alias: &str, checkout: &Path, run: &str, fetched: &Fetched, code_of_command: i32) -> Result<(), Refusal> {
        if !fetched.complete {
            return Err(Refusal::new(
                code::APPLY_FAILED,
                Exit::Refused,
                format!(
                    "the command of the run {run} exited with {code_of_command}, and its changes did not come: `yonder fetch {alias} {run}` copies them into the git directory of this checkout"
                ),
            ));
        }
        let Some(patch) = &fetched.changes else {
            (self.say)("yonder: the command changed no file");
            return Ok(());
        };
        match self.apply_patch(checkout, patch) {
            Ok(files) => {
                (self.say)(&format!(
                    "yonder: applied the changes of the command to {files} files of this working tree; `git apply -R {}` undoes them",
                    patch.display()
                ));
                Ok(())
            }
            Err(refusal) if refusal.code == code::APPLY_FAILED => Err(Refusal::new(
                code::APPLY_FAILED,
                Exit::Refused,
                format!(
                    "the command of the run {run} exited with {code_of_command}, and its changes do not apply to this working tree: {}. The patch is {}",
                    refusal.message,
                    patch.display()
                ),
            )),
            Err(refusal) => Err(Refusal::new(refusal.code, Exit::Refused, refusal.message)),
        }
    }

    /// Sends `Start`. A reply that does not come can still leave the run on the agent, so the client cancels it then.
    fn start(&self, host: &mut Host, spec: RunSpec) -> Result<(), Refusal> {
        let run = spec.run.clone();
        let (mut channel, _) = host.connect()?;
        channel.send(&Request::Start(spec)).map_err(|error| host.link_refusal(&error))?;
        match host.recv(&mut channel, self.timing.read) {
            Ok(Reply::Started { .. }) => Ok(()),
            Ok(Reply::Refused { code, message }) => Err(host.agent_refusal(code, &message)),
            Ok(reply) => Err(host.unexpected(&reply)),
            Err(refusal) if is_link(&refusal) => {
                let what = format!("the start of the run {run} got no reply from {}", host.alias());
                Err(self.abandon(host, &run, &what, refusal))
            }
            Err(refusal) => Err(refusal),
        }
    }

    /// Follows a started run to its end, then copies the files that `record` asks for, with the changes to `patch`, and
    /// removes the run on the host. A run that the agent refused
    /// is removed too, and its failure carries the steps of the host. A refusal whose steps have no `command` is
    /// [`Exit::NotRun`], but for a run that vanished on the host after it started ([`UNKNOWN_RUN`]): whether its command
    /// ran is unknown, so it is [`Exit::Refused`], and a caller never runs the command a second time.
    fn follow_and_fetch(
        &self,
        host: &mut Host,
        run: &str,
        checkout: &Path,
        record: &RunRecord,
        patch: &Path,
        watch: &Watch,
    ) -> Result<Finished, (Refusal, Vec<Phase>)> {
        let alias = host.alias().to_owned();
        let followed = match self.follow(host, run, true, watch) {
            Ended::Exit(followed) => followed,
            Ended::Refused(refusal, steps) if refusal.code == UNKNOWN_RUN => {
                return Err((vanished(refusal, run, &alias), steps));
            }
            Ended::Refused(refusal, steps) => {
                self.forget_or_say(host, run);
                let ran = steps.iter().any(|step| step.name == "command");
                return Err((if ran { refusal } else { not_run(refusal) }, steps));
            }
            Ended::Unseen(refusal) if refusal.code == code::INTERRUPTED => {
                return Err((Refusal::new(refusal.code, Exit::Interrupted, refusal.message), Vec::new()));
            }
            Ended::Unseen(refusal) => return Err((refusal, Vec::new())),
        };
        if !record.asks_for_files() {
            self.forget_or_say(host, run);
            return Ok(Finished {
                followed,
                fetched: Fetched {
                    complete: true,
                    ..Fetched::default()
                },
                fetch: None,
            });
        }
        let fetching = Instant::now();
        let fetched = self.fetch_run(host, run, checkout, record, patch);
        let fetch = Some(phase("fetch", fetching.elapsed()));
        let fetched = match fetched {
            Ok(fetched) => {
                self.forget_or_say(host, run);
                fetched
            }
            Err(refusal) => {
                (self.say)(&format!(
                    "yonder: cannot copy the files of the run {run} from {alias}: {}. The run stays on {alias}: `yonder fetch {alias} {run}` in this checkout copies its files again, and `yonder forget {alias} {run}` removes it",
                    refusal.message
                ));
                Fetched::default()
            }
        };
        Ok(Finished { followed, fetched, fetch })
    }

    /// Cancels a started run whose start or pack failed, so its supervisor does not wait for the pack. Answers the
    /// refusal with its code, the failed step `what`, and what became of the run.
    fn abandon(&self, host: &mut Host, run: &str, what: &str, refusal: Refusal) -> Refusal {
        let alias = host.alias().to_owned();
        let mut backoff = self.timing.first_backoff;
        let mut tries = 1;
        let cancelled = loop {
            match cancel_on(host, run, self.timing.read) {
                Err(failed) if is_link(&failed) && tries < ABANDON_TRIES => {
                    tries += 1;
                    thread::sleep(backoff);
                    backoff = (backoff * 2).min(self.timing.max_backoff);
                }
                cancelled => break cancelled,
            }
        };
        let fate = match cancelled {
            Ok(_) => format!("{alias} has the cancel of the run {run}"),
            Err(failed) => format!(
                "cannot cancel the run {run} on {alias}: {}. `yonder cancel {alias} {run}` cancels it",
                failed.message
            ),
        };
        Refusal::new(refusal.code, Exit::Refused, format!("{what}: {}; {fate}", refusal.message))
    }

    /// Follows a run from its first line, as [`Client::run`] does. Ctrl+C here cancels nothing.
    pub fn attach(&self, alias: &str, run: &str) -> Result<Followed, Refusal> {
        let mut host = self.host(alias)?;
        match self.follow(&mut host, run, false, &Watch::new()) {
            Ended::Exit(followed) => Ok(followed),
            Ended::Refused(refusal, _) | Ended::Unseen(refusal) => Err(refusal),
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

    /// The hello of the agent: its version and platform, and the address that answered.
    pub fn hello(&self, alias: &str) -> Result<AgentInfo, Refusal> {
        let mut host = self.host(alias)?;
        let (channel, info) = host.connect()?;
        channel.close();
        Ok(info)
    }

    /// The commits of `near` and `extra` that the host has. `near` are the candidates of the snapshot, which git finds
    /// at once. Only a host that has none of them gets a second `Have` with the first-parent walk up to [`FARTHEST`],
    /// which reads up to 65537 commits.
    pub(crate) fn present(
        &self,
        host: &mut Host,
        repo: &str,
        checkout: &Path,
        near: &[String],
        extra: &[Option<&str>],
    ) -> Result<Vec<String>, Refusal> {
        let ask = |candidates: &[String], host: &mut Host| {
            let mut found = Vec::new();
            for candidate in candidates.iter().map(String::as_str).map(Some).chain(extra.iter().copied()) {
                add_candidate(&mut found, candidate);
            }
            self.have(host, repo, found)
        };
        let present = ask(near, host)?;
        if !present.is_empty() {
            return Ok(present);
        }
        let far = self.snapshot(checkout, Scan::Head, FARTHEST)?;
        ask(&far.candidates, host)
    }

    /// Refuses a checkout with local changes: tracked or untracked files that differ from HEAD. `git status` of a large
    /// checkout reads every file, so a run asks the host meanwhile.
    fn check_clean(&self, checkout: &Path) -> Result<(), Refusal> {
        let changed = self.snapshot(checkout, Scan::Status, 1)?.changed;
        if changed.is_empty() {
            return Ok(());
        }
        let more = match changed.len() {
            0..=SHOWN_CHANGES => String::new(),
            count => format!("\n  (+{} more)", count - SHOWN_CHANGES),
        };
        Err(refused(
            code::LOCAL_CHANGES,
            format!(
                "a remote run takes the committed HEAD only, and this checkout has local changes. Commit or stash them first:\n  {}{more}",
                changed[..changed.len().min(SHOWN_CHANGES)].join("\n  ")
            ),
        ))
    }

    /// The commits of `candidates` that the host has.
    pub(crate) fn have(&self, host: &mut Host, repo: &str, candidates: Vec<String>) -> Result<Vec<String>, Refusal> {
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
    /// second one stops the follow. [`Watch::stop_follow`] stops it from another thread.
    fn follow(&self, host: &mut Host, run: &str, cancel_on_interrupt: bool, watch: &Watch) -> Ended {
        let watched = host.clone();
        thread::scope(|scope| {
            if cancel_on_interrupt {
                scope.spawn(|| self.watch_interrupts(&watched, run, watch));
            }
            let ended = self.follow_loop(host, run, watch);
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
            if presses >= 2 {
                watch.stop_follow();
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
            phases: Vec::new(),
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
                        phases: state.phases,
                    });
                }
                Err(Broken::Refused(refusal)) if is_local(&refusal) => return Ended::Unseen(refusal),
                Err(Broken::Refused(refusal)) => return Ended::Refused(refusal, state.phases),
                Err(Broken::Link(refusal)) => {
                    if state.last_reply.elapsed() >= self.timing.reconnect_for {
                        return Ended::Unseen(Refusal::new(
                            refusal.code.clone(),
                            Exit::Refused,
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
                            Stream::Stdout if self.stdout.is_some() => {
                                if let Some(stdout) = self.stdout {
                                    stdout(&text);
                                }
                            }
                            Stream::Stdout => {
                                state.stdout.push_str(&text);
                                state.stdout.push('\n');
                            }
                        }
                    }
                }
                // A new connection after a lost one can bring the steps again, and the last ones hold.
                Reply::Phases { phases } => state.phases = phases,
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
    /// The steps of the host from the last `Reply::Phases`.
    phases: Vec<Phase>,
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

/// Adds a commit to the candidates when it is a new one.
fn add_candidate(candidates: &mut Vec<String>, commit: Option<&str>) {
    if let Some(commit) = commit
        && !commit.is_empty()
        && !candidates.iter().any(|known| known == commit)
    {
        candidates.push(commit.to_owned());
    }
}

/// The files of a run that came back.
#[derive(Debug, Default)]
pub(crate) struct Fetched {
    /// The staged files and the files of the outputs, relative to the checkout root with `/` separators.
    pub(crate) files: Vec<String>,
    /// The patch of the changes of the command, when it came.
    pub(crate) changes: Option<PathBuf>,
    /// Every file came. A fetch that failed keeps the run on the host.
    pub(crate) complete: bool,
}

/// A refusal before the command of a run: the host did not run it. Ctrl+C keeps its own exit.
/// The refusal of the agent that keeps no run of the id (`API.md` of `yonder-link`). After a start it means that the run
/// was removed on the host, by a `yonder forget` or by the expiry of a day after its end.
const UNKNOWN_RUN: &str = "unknown_run";

/// The refusal of a run that vanished on the host after it started: the command may have run, so the exit is no
/// [`Exit::NotRun`], and there is nothing left to forget.
fn vanished(refusal: Refusal, run: &str, alias: &str) -> Refusal {
    Refusal::new(
        refusal.code,
        Exit::Refused,
        format!(
            "{}: the run {run} started on {alias} and is gone there, so whether its command ran is unknown. `yonder runs {alias}` lists the runs that {alias} keeps",
            refusal.message
        ),
    )
}

fn not_run(refusal: Refusal) -> Refusal {
    let exit = if refusal.code == code::INTERRUPTED {
        Exit::Interrupted
    } else {
        Exit::NotRun
    };
    Refusal::new(refusal.code, exit, refusal.message)
}

/// The line of a started run: `yonder: run <id> of <commit> on <alias>`, then the repository when it is not the one of
/// the host file, and what the commit holds beyond HEAD.
fn run_line(run: &str, alias: &str, snapshot: &Snapshot, repo: &str, default_repo: &str) -> String {
    let mut line = format!("yonder: run {run} of {} on {alias}", short(&snapshot.commit));
    if repo != default_repo {
        line.push_str(&format!(" in {repo}"));
    }
    if !snapshot.changed.is_empty() {
        line.push_str(&format!(
            ": the working tree, HEAD {} and {} changed paths",
            short(&snapshot.head),
            snapshot.changed.len()
        ));
    }
    line
}

/// The repository `name` beside `repo` on the host: `~/projects/branch-deck` beside `~/projects/idea-1`.
fn sibling(repo: &str, name: &str) -> Option<String> {
    let parent = repo.trim_end_matches('/').rsplit_once('/')?.0;
    (!parent.is_empty()).then(|| format!("{parent}/{name}"))
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
