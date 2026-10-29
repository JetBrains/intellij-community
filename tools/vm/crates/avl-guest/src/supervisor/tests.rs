use std::cell::{Cell, RefCell};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::ffi::OsString;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use avl_wire::supervisor::{AgentExit, CancellationRequest, EnvironmentPolicy};
use pretty_assertions::assert_eq;

use super::identity::{classify_exit, normalized_exit_code};
use super::launch::{CHANNEL_VARIABLES, LaunchHost, SuperviseLaunch, supervise_launch_for};
use super::log::{last_complete_lines, read_log_suffix};
use super::state::{ActivePointer, claim_active, read_active, write_json_atomic};
use super::supervise::{CONTEXT_UTF8_LOCALE, ENVIRONMENT_ALLOWLIST, child_environment, child_spawn_argv};

// --- the decisions of a supervised run --------------------------------------------------------------------------

// Exit 0 succeeds and every other exit fails; a cancellation is the result whatever the exit, and the KILL it sent
// is the recorded signal, with no exit code.
#[test]
fn the_finish_of_a_run_follows_its_cancellation() {
    use super::identity::ExitOutcome;
    use super::supervise::{exit_result, finish_of};
    use avl_wire::supervisor::{CancellationRecord, Outcome};
    assert_eq!(exit_result(Some(0)), Outcome::Succeeded);
    assert_eq!(exit_result(Some(3)), Outcome::Failed);
    assert_eq!(exit_result(None), Outcome::Failed);
    let exited = |code: Option<i32>, signal: Option<&str>| ExitOutcome {
        code,
        signal: signal.map(str::to_owned),
    };
    assert_eq!(finish_of(None, exited(Some(0), None)), (Outcome::Succeeded, Some(0), None));
    assert_eq!(
        finish_of(None, exited(None, Some("SIGSEGV"))),
        (Outcome::Failed, None, Some("SIGSEGV".to_owned()))
    );
    let mut record = CancellationRecord {
        requested_at: "2026-10-01T12:00:00Z".to_owned(),
        grace_ms: 10_000,
        term_sent_at: Some("2026-10-01T12:00:00Z".to_owned()),
        kill_sent_at: None,
    };
    assert_eq!(
        finish_of(Some(&record), exited(Some(143), None)),
        (Outcome::Canceled, Some(143), None)
    );
    record.kill_sent_at = Some("2026-10-01T12:00:10Z".to_owned());
    assert_eq!(
        finish_of(Some(&record), exited(Some(137), None)),
        (Outcome::Canceled, None, Some("SIGKILL".to_owned()))
    );
}
use super::*;
use crate::cli::RunArgs;
use crate::testing::{agent_launcher, run_agent};

// --- a scripted world ----------------------------------------------------------------------------------------

/// Processes and a clock the test owns. A TERM ends a group unless the group was told to ignore it; a KILL always
/// does. Sleeping advances the clock and runs `on_sleep` once, which is how a test stages a write that lands while
/// the code under test waits.
struct FakeSystem {
    now: Cell<Timestamp>,
    identities: RefCell<HashMap<i32, ProcessIdentity>>,
    groups: RefCell<HashSet<i32>>,
    ignores_term: RefCell<HashSet<i32>>,
    /// The groups that even a KILL does not end, as a process in the D state or a zombie of a live parent does.
    survives_kill: RefCell<HashSet<i32>>,
    signals: RefCell<Vec<(i32, Signal)>>,
    commands: RefCell<HashMap<i32, String>>,
    on_sleep: RefCell<Option<Box<dyn FnOnce()>>>,
}

impl FakeSystem {
    fn new() -> Self {
        Self {
            now: Cell::new("2026-09-26T10:00:00Z".parse().unwrap()),
            identities: RefCell::default(),
            groups: RefCell::default(),
            ignores_term: RefCell::default(),
            survives_kill: RefCell::default(),
            signals: RefCell::default(),
            commands: RefCell::default(),
            on_sleep: RefCell::default(),
        }
    }

    fn process(&self, pid: i32, pgid: i32, start: &str) -> ProcessIdentity {
        let identity = ProcessIdentity {
            pid,
            pgid,
            process_start: start.to_owned(),
        };
        self.identities.borrow_mut().insert(pid, identity.clone());
        self.groups.borrow_mut().insert(pgid);
        identity
    }

    fn end_group(&self, pgid: i32) {
        self.groups.borrow_mut().remove(&pgid);
        self.identities.borrow_mut().retain(|_, identity| identity.pgid != pgid);
    }
}

impl System for FakeSystem {
    fn identity(&self, pid: i32) -> Option<ProcessIdentity> {
        self.identities.borrow().get(&pid).cloned()
    }

    fn group_alive(&self, pgid: i32) -> bool {
        self.groups.borrow().contains(&pgid)
    }

    fn group_members(&self, pgid: i32) -> Vec<GroupMember> {
        let commands = self.commands.borrow();
        let mut members: Vec<GroupMember> = self
            .identities
            .borrow()
            .values()
            .filter(|identity| identity.pgid == pgid)
            .map(|identity| GroupMember {
                pid: identity.pid,
                command: commands.get(&identity.pid).cloned().unwrap_or_default(),
            })
            .collect();
        members.sort_by_key(|member| member.pid);
        members
    }

    fn signal_group(&self, pgid: i32, signal: Signal) {
        self.signals.borrow_mut().push((pgid, signal));
        let ends = if signal == Signal::SIGKILL {
            !self.survives_kill.borrow().contains(&pgid)
        } else {
            !self.ignores_term.borrow().contains(&pgid)
        };
        if ends {
            self.end_group(pgid);
        }
    }

    fn now(&self) -> Timestamp {
        self.now.get()
    }

    fn sleep(&self, duration: Duration) {
        self.now.set(self.now.get().checked_add(duration).unwrap());
        let hook = self.on_sleep.borrow_mut().take();
        if let Some(hook) = hook {
            hook();
        }
    }
}

fn run_state(run_id: &str, phase: Phase, system: &FakeSystem) -> RunState {
    let mut state = RunState::new(run_id, phase);
    state.argv = vec!["/bin/sleep".to_owned(), "60".to_owned()];
    state.cwd = Some("/".to_owned());
    state.created_at = Some(stamp(system.now()));
    state
}

/// The run `run-a` while its child `child` runs, without a supervisor that it records.
fn child_run(system: &FakeSystem, child: ProcessIdentity) -> RunState {
    let mut running = run_state("run-a", Phase::Running, system);
    running.pid = Some(child.pid);
    running.pgid = Some(child.pgid);
    running.process_start = Some(child.process_start);
    running
}

/// A child with the pid 400 that the fake system does not know: it exited, and its start time names no process.
fn vanished_child() -> ProcessIdentity {
    ProcessIdentity {
        pid: 400,
        pgid: 400,
        process_start: "gone".to_owned(),
    }
}

/// A root holding `state` as its run and, when `owns_slot`, as the slot's owner.
fn seeded(state: &RunState, owns_slot: bool) -> tempfile::TempDir {
    let root = tempfile::tempdir().unwrap();
    let paths = RunPaths::new(root.path(), &state.run_id);
    fs::create_dir_all(&paths.directory).unwrap();
    write_json_atomic(&paths.state, state).unwrap();
    if owns_slot {
        let active = ActivePointer {
            schema_version: SCHEMA_VERSION,
            run_id: state.run_id.clone(),
            supervisor_pid: 10,
            supervisor_pgid: 10,
            supervisor_start: "Sat Sep 26 10:00:00 2026".to_owned(),
            claimed_at: stamp(Timestamp::now()),
        };
        assert!(claim_active(root.path(), &active).unwrap());
    }
    root
}

fn on_disk(root: &Path, run_id: &str) -> serde_json::Value {
    serde_json::from_slice(&fs::read(RunPaths::new(root, run_id).state).unwrap()).unwrap()
}

// --- reconcile -----------------------------------------------------------------------------------------------

/// A terminal state whose process group still has members is not a free slot: JCEF's helpers outlive their
/// supervisor, and handing the slot out would start a second IDE beside them.
#[test]
fn a_finished_run_whose_group_is_still_alive_is_orphaned() {
    let system = FakeSystem::new();
    let child = system.process(200, 200, "Sat Sep 26 10:00:01 2026");
    let mut finished = run_state("run-a", Phase::Finished, &system);
    finished.pgid = Some(child.pgid);
    finished.outcome = Some(Outcome::Succeeded);
    let root = seeded(&finished, true);

    let reconciled = reconcile(&system, root.path(), finished).unwrap();
    assert_eq!(reconciled.phase, Phase::Orphaned);
    assert_eq!(reconciled.outcome, Some(Outcome::Orphaned));
    assert_eq!(reconciled.pending_outcome, Some(Outcome::Succeeded));
    assert_eq!(on_disk(root.path(), "run-a")["phase"], "orphaned");
    assert!(read_active(root.path()).unwrap().is_some(), "an orphaned run gave up the slot");

    // Once the group is gone, the orphan resolves to the outcome it had, and the slot is free.
    system.end_group(child.pgid);
    let resolved = reconcile(&system, root.path(), reconciled).unwrap();
    assert_eq!(resolved.phase, Phase::Finished);
    assert_eq!(resolved.outcome, Some(Outcome::Succeeded));
    assert!(resolved.orphaned_resolved_at.is_some());
    assert!(read_active(root.path()).unwrap().is_none());
}

/// An orphan with nothing pending resolves to `supervisor_lost`, keeping the exit code it recorded.
#[test]
fn an_orphan_whose_group_died_finishes_with_its_pending_outcome() {
    let system = FakeSystem::new();
    let mut orphaned = run_state("run-a", Phase::Orphaned, &system);
    orphaned.pgid = Some(300);
    orphaned.exit_code = Some(3);
    let root = seeded(&orphaned, true);

    let finished = reconcile(&system, root.path(), orphaned).unwrap();
    assert_eq!(finished.phase, Phase::Finished);
    assert_eq!(finished.outcome, Some(Outcome::SupervisorLost));
    assert_eq!(finished.exit_code, Some(3));
    // The explicit nulls the host asserts on are written, not left out.
    let written = on_disk(root.path(), "run-a");
    assert_eq!(written["signal"], serde_json::Value::Null);
    assert!(written.as_object().unwrap().contains_key("signal"), "{written}");
    assert_eq!(written["nativeExitCode"], 3);
    assert!(read_active(root.path()).unwrap().is_none());
}

/// A supervisor that has not recorded itself yet gets the start budget, counted from the run's creation.
#[test]
fn a_starting_run_is_given_the_start_budget_and_then_lost() {
    let system = FakeSystem::new();
    let starting = run_state("run-a", Phase::Starting, &system);
    let root = seeded(&starting, false);

    system.sleep(Duration::from_secs(14));
    assert_eq!(reconcile(&system, root.path(), starting.clone()).unwrap().phase, Phase::Starting);

    system.sleep(Duration::from_secs(2));
    let lost = reconcile(&system, root.path(), starting).unwrap();
    assert_eq!(lost.phase, Phase::Finished);
    assert_eq!(lost.outcome, Some(Outcome::SupervisorLost));
    assert_eq!(lost.exit_code, Some(255));
    assert_eq!(
        lost.failure.as_deref(),
        Some("supervisor exited before recording the child identity")
    );
}

/// A starting supervisor that recorded itself and is alive keeps its run however long it takes; a half-recorded
/// identity is a state nobody can act on.
#[test]
fn a_starting_run_with_a_live_supervisor_is_kept_and_a_half_identity_is_refused() {
    let system = FakeSystem::new();
    let supervisor = system.process(100, 100, "Sat Sep 26 10:00:00 2026");
    let mut starting = run_state("run-a", Phase::Starting, &system);
    starting.supervisor_pid = Some(supervisor.pid);
    starting.supervisor_pgid = Some(supervisor.pgid);
    starting.supervisor_start = Some(supervisor.process_start);
    let root = seeded(&starting, false);
    system.sleep(Duration::from_secs(60));
    assert_eq!(reconcile(&system, root.path(), starting.clone()).unwrap().phase, Phase::Starting);

    starting.supervisor_start = None;
    let refusal = reconcile(&system, root.path(), starting).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("invalid_state", AgentExit::Failure));
}

/// A running run whose child and supervisor both vanished waits out the handoff, then is lost - or orphaned, when
/// its group still has members.
#[test]
fn a_running_run_with_vanished_identities_is_lost_or_orphaned() {
    let system = FakeSystem::new();
    let running = child_run(&system, vanished_child());
    let root = seeded(&running, true);

    let before = system.now();
    let lost = reconcile(&system, root.path(), running.clone()).unwrap();
    assert!(
        system.now().duration_since(before) >= jiff::SignedDuration::from_millis(500),
        "no handoff grace"
    );
    assert_eq!(lost.outcome, Some(Outcome::SupervisorLost));
    assert_eq!(lost.failure.as_deref(), Some("child exited without an observable wait status"));
    assert!(read_active(root.path()).unwrap().is_none());

    let root = seeded(&running, true);
    system.groups.borrow_mut().insert(400);
    let orphaned = reconcile(&system, root.path(), running).unwrap();
    assert_eq!(orphaned.phase, Phase::Orphaned);
    assert!(orphaned.failure.unwrap().contains("still has members"));
}

/// The supervisor can exit right after reaping its child and just before its terminal rename is visible. A
/// reconcile inside that window answers the supervisor's own terminal state, not a synthesized loss.
#[test]
fn a_terminal_state_that_lands_during_the_handoff_wins() {
    let system = FakeSystem::new();
    let running = child_run(&system, vanished_child());
    let root = seeded(&running, true);
    let mut finished = running.clone();
    finished.phase = Phase::Finished;
    finished.outcome = Some(Outcome::Succeeded);
    finished.exit_code = Some(0);
    let state_path = RunPaths::new(root.path(), "run-a").state;
    *system.on_sleep.borrow_mut() = Some(Box::new(move || {
        write_json_atomic(&state_path, &finished).unwrap();
    }));

    let reconciled = reconcile(&system, root.path(), running).unwrap();
    assert_eq!(reconciled.outcome, Some(Outcome::Succeeded));
    assert!(read_active(root.path()).unwrap().is_none());
}

/// A live child keeps a running run as it is.
#[test]
fn a_running_run_with_a_live_child_is_unchanged() {
    let system = FakeSystem::new();
    let child = system.process(500, 500, "Sat Sep 26 10:00:02 2026");
    let running = child_run(&system, child);
    let root = seeded(&running, true);
    assert_eq!(reconcile(&system, root.path(), running.clone()).unwrap(), running);

    // A recycled pid - the same number, another start time - is not the child.
    system.process(500, 500, "Sat Sep 26 11:11:11 2026");
    let lost = reconcile(&system, root.path(), running).unwrap();
    assert_eq!(lost.phase, Phase::Orphaned, "the group is alive, so the slot is held");
}

// --- the members that outlive the child ------------------------------------------------------------------------

/// The IDE leaves processes in its group that it started without a group of their own: the JCEF helper, and an agent
/// that is still in its TERM handler. Once the child is reaped, the supervisor KILLs them and names them in the
/// supervisor log, so the run finishes and the reconcile does not orphan it.
#[test]
fn the_members_that_outlive_the_child_are_killed_and_named() {
    use super::supervise::end_leftover_members;
    let system = FakeSystem::new();
    system.process(701, 700, "Sat Sep 26 10:00:03 2026");
    system.process(702, 700, "Sat Sep 26 10:00:04 2026");
    system.commands.borrow_mut().insert(701, "/jbr/lib/cef_server --pid=700".to_owned());
    system
        .commands
        .borrow_mut()
        .insert(702, format!("/jbr/bin/java -cp {}", "x".repeat(400)));
    let directory = tempfile::tempdir().unwrap();
    let paths = RunPaths::new(directory.path(), "run-a");
    fs::create_dir_all(&paths.directory).unwrap();

    end_leftover_members(&system, 700, &paths, "run-a");
    assert_eq!(*system.signals.borrow(), vec![(700, Signal::SIGKILL)]);
    assert!(!system.group_alive(700));
    let log = fs::read_to_string(&paths.supervisor_log).unwrap();
    let line = log.lines().next().unwrap().split_once(' ').unwrap().1;
    let java = format!("/jbr/bin/java -cp {}", "x".repeat(200 - "/jbr/bin/java -cp ".len()));
    assert_eq!(
        line,
        format!(
            "finish run-a: KILL to group 700, members that outlived the child: 2 [701 /jbr/lib/cef_server --pid=700; 702 {java}]; the group is gone"
        )
    );

    // A group that is already empty is not signalled, and the log gets no line.
    end_leftover_members(&system, 700, &paths, "run-a");
    assert_eq!(system.signals.borrow().len(), 1);
    assert_eq!(fs::read_to_string(&paths.supervisor_log).unwrap().lines().count(), 1);
}

/// The members of a group come from the `pid pgid args` table of `ps`. A line of another group, a header and a
/// line that does not parse are no member.
#[test]
fn the_members_of_a_group_are_read_from_the_ps_table() {
    use super::identity::members_in_table;
    let table = "  PID  PGID ARGS\n  701   700 /jbr/lib/cef_server  --pid=700\n  702   702 /bin/sleep 60\n  703   700 \n garbage\n";
    assert_eq!(
        members_in_table(table, 700),
        vec![
            GroupMember {
                pid: 701,
                command: "/jbr/lib/cef_server --pid=700".to_owned(),
            },
            GroupMember {
                pid: 703,
                command: String::new(),
            },
        ]
    );
    assert_eq!(members_in_table(table, 0), Vec::new());
}

// --- cancel --------------------------------------------------------------------------------------------------

fn cancel_args(root: &Path, grace_ms: u64) -> CancelArgs {
    CancelArgs {
        run: RunArgs {
            root: RootArgs { root: root.to_path_buf() },
            run_id: "run-a".to_owned(),
        },
        grace_ms,
        thread_dump: None,
    }
}

/// A run whose supervisor is gone is cancelled by the cancel client itself, identity-checked, with the status
/// nobody reaped synthesized: TERM when the group obeys within the grace period.
#[test]
fn an_orphan_cancel_terms_the_group_and_synthesizes_the_exit() {
    let system = FakeSystem::new();
    let child = system.process(600, 600, "Sat Sep 26 10:00:03 2026");
    let running = child_run(&system, child);
    let root = seeded(&running, true);

    let finished = cancel(&system, &cancel_args(root.path(), 5_000)).unwrap();
    assert_eq!(system.signals.borrow().as_slice(), [(600, Signal::SIGTERM)]);
    assert_eq!(finished.outcome, Some(Outcome::CanceledOrphan));
    assert_eq!(finished.signal, Some(Some("SIGTERM".to_owned())));
    assert_eq!(finished.exit_code, Some(143));
    assert_eq!(finished.native_exit_code, Some(None));
    let cancellation = finished.cancellation.unwrap();
    assert_eq!(cancellation.grace_ms, 5_000);
    assert!(cancellation.term_sent_at.is_some() && cancellation.kill_sent_at.is_none());
    assert!(read_active(root.path()).unwrap().is_none());
    // The request the cancel dropped is the one it honoured.
    let request: CancellationRequest = serde_json::from_slice(&fs::read(RunPaths::new(root.path(), "run-a").cancel).unwrap()).unwrap();
    assert_eq!(request.grace_ms, 5_000);
}

/// A group that ignores TERM is KILLed once the grace period is over.
#[test]
fn an_orphan_cancel_kills_a_group_that_ignores_term() {
    let system = FakeSystem::new();
    let child = system.process(700, 700, "Sat Sep 26 10:00:04 2026");
    system.ignores_term.borrow_mut().insert(700);
    let running = child_run(&system, child);
    let root = seeded(&running, true);

    let finished = cancel(&system, &cancel_args(root.path(), 1_000)).unwrap();
    assert_eq!(system.signals.borrow().as_slice(), [(700, Signal::SIGTERM), (700, Signal::SIGKILL)]);
    assert_eq!(finished.signal, Some(Some("SIGKILL".to_owned())));
    assert_eq!(finished.exit_code, Some(137));
    assert!(finished.cancellation.unwrap().kill_sent_at.is_some());
}

/// A running run whose live supervisor and live child it records, and the slot that the supervisor owns.
fn stuck_run(system: &FakeSystem) -> (RunState, tempfile::TempDir) {
    let supervisor = system.process(10, 10, "Sat Sep 26 10:00:00 2026");
    let child = system.process(700, 700, "Sat Sep 26 10:00:04 2026");
    let mut running = child_run(system, child);
    running.supervisor_pid = Some(supervisor.pid);
    running.supervisor_pgid = Some(supervisor.pgid);
    running.supervisor_start = Some(supervisor.process_start);
    let root = seeded(&running, true);
    (running, root)
}

/// A supervisor that does not end the cancel within the grace and its deadlines is killed, and so is the process group
/// of its child. The run finishes as canceled, and the slot is free.
#[test]
fn a_cancel_past_its_deadline_kills_the_supervisor_and_the_group_and_frees_the_slot() {
    let system = FakeSystem::new();
    let (_, root) = stuck_run(&system);
    let end = cancel_to_end(&system, &cancel_args(root.path(), 1_000)).unwrap();
    let CancelEnd::Killed(finished) = end else {
        panic!("not killed: {end:?}")
    };
    assert_eq!(system.signals.borrow().as_slice(), [(10, Signal::SIGKILL), (700, Signal::SIGKILL)]);
    assert_eq!(finished.phase, Phase::Finished);
    assert_eq!(finished.outcome, Some(Outcome::CanceledOrphan));
    assert_eq!(finished.signal, Some(Some("SIGKILL".to_owned())));
    assert!(finished.failure.unwrap().contains("killed it and the process group 700"));
    assert!(read_active(root.path()).unwrap().is_none(), "the killed run kept the slot");
    assert!(!root.path().join(ABANDONED_FILE).exists());
}

/// A process group that outlives the KILL is abandoned: the run finishes with the survivors named, the record names
/// them in the slot root, and the slot is free. The cancel verb answers the finished state, not a refusal.
#[test]
fn a_group_that_outlives_the_kill_is_abandoned_and_its_slot_freed() {
    let system = FakeSystem::new();
    let (_, root) = stuck_run(&system);
    system.survives_kill.borrow_mut().insert(700);
    system.commands.borrow_mut().insert(700, "jcef_helper --type=gpu".to_owned());
    let end = cancel_to_end(&system, &cancel_args(root.path(), 1_000)).unwrap();
    let CancelEnd::Abandoned(finished) = end else {
        panic!("not abandoned: {end:?}")
    };
    assert_eq!(finished.outcome, Some(Outcome::CanceledOrphan));
    assert!(finished.failure.unwrap().contains("outlived SIGKILL"));
    assert!(read_active(root.path()).unwrap().is_none(), "the abandoned run kept the slot");
    let record: AbandonedRun = serde_json::from_slice(&fs::read(root.path().join(ABANDONED_FILE)).unwrap()).unwrap();
    assert_eq!((record.run_id.as_str(), record.pgid), ("run-a", 700));
    assert_eq!(record.members, ["700 jcef_helper --type=gpu"]);
}

/// A finished run is answered as it is: nothing is signalled and no request is dropped.
#[test]
fn cancelling_a_finished_run_signals_nothing() {
    let system = FakeSystem::new();
    let mut finished = run_state("run-a", Phase::Finished, &system);
    finished.outcome = Some(Outcome::Succeeded);
    let root = seeded(&finished, false);
    assert_eq!(
        cancel(&system, &cancel_args(root.path(), 0)).unwrap().outcome,
        Some(Outcome::Succeeded)
    );
    assert!(system.signals.borrow().is_empty());
    assert!(!RunPaths::new(root.path(), "run-a").cancel.exists());
}

// --- a supervisor that ended and that nobody reaped ---------------------------------------------------------------

/// A supervisor that ended stays a zombie until its parent reaps it. A zombie keeps its pid, its group and its start
/// time, and KILL does not end it.
fn zombie_supervisor(system: &FakeSystem) -> ProcessIdentity {
    let zombie = system.process(10, 10, "Sat Sep 26 10:00:00 2026");
    system.survives_kill.borrow_mut().insert(10);
    zombie
}

fn with_supervisor(mut state: RunState, supervisor: &ProcessIdentity) -> RunState {
    state.supervisor_pid = Some(supervisor.pid);
    state.supervisor_pgid = Some(supervisor.pgid);
    state.supervisor_start = Some(supervisor.process_start.clone());
    state
}

// The identity check reads a zombie supervisor as a live one. A run whose supervisor published its end is finished
// at once anyway, because every wait reads the state file first. That is why a parent that never reaps keeps working.
// Only a run that its supervisor left running reads as running while the zombie lasts. A cancel still ends that run
// within the deadline of the cancel, so no wait is unbounded. The reap repairs the run at the next status.
#[test]
fn a_zombie_supervisor_reads_as_alive_and_only_its_reap_repairs_a_run_it_left_running() {
    let system = FakeSystem::new();
    let zombie = zombie_supervisor(&system);
    let mut finished = with_supervisor(run_state("run-a", Phase::Finished, &system), &zombie);
    finished.outcome = Some(Outcome::Succeeded);
    let root = seeded(&finished, false);
    let before = system.now();
    assert_eq!(status(&system, root.path(), "run-a").unwrap().phase, Phase::Finished);
    assert_eq!(
        cancel(&system, &cancel_args(root.path(), 0)).unwrap().outcome,
        Some(Outcome::Succeeded)
    );
    assert_eq!(system.now(), before, "a finished run with a zombie supervisor waits for nothing");

    let mut left = with_supervisor(run_state("run-a", Phase::Running, &system), &zombie);
    left.pid = Some(700);
    left.pgid = Some(700);
    left.process_start = Some("gone".to_owned());
    let root = seeded(&left, true);
    assert_eq!(
        status(&system, root.path(), "run-a").unwrap().phase,
        Phase::Running,
        "the zombie reads as a live supervisor"
    );
    let before = system.now();
    let end = cancel_to_end(&system, &cancel_args(root.path(), 0)).unwrap();
    let waited = system.now().duration_since(before);
    let CancelEnd::Killed(canceled) = end else {
        panic!("not killed: {end:?}")
    };
    assert_eq!(canceled.outcome, Some(Outcome::CanceledOrphan));
    let bound = jiff::SignedDuration::try_from(FINISH_TIMEOUT + CANCEL_HANDOFF_TIMEOUT + POLL_INTERVAL).unwrap();
    assert!(waited <= bound, "the cancel waited {waited} for a zombie");

    let root = seeded(&left, true);
    system.end_group(10);
    let lost = status(&system, root.path(), "run-a").unwrap();
    assert_eq!(
        lost.outcome,
        Some(Outcome::SupervisorLost),
        "the reaped supervisor still reads as alive"
    );
}

// --- the active pointer --------------------------------------------------------------------------------------

/// The slot is claimed with a link, so of two claims exactly one wins and the loser sees the winner's pointer.
#[test]
fn of_two_claims_on_the_slot_exactly_one_wins() {
    let root = tempfile::tempdir().unwrap();
    let pointer = |run_id: &str, pid: i32| ActivePointer {
        schema_version: SCHEMA_VERSION,
        run_id: run_id.to_owned(),
        supervisor_pid: pid,
        supervisor_pgid: pid,
        supervisor_start: "Sat Sep 26 10:00:00 2026".to_owned(),
        claimed_at: "2026-09-26T10:00:00.000Z".to_owned(),
    };
    assert!(claim_active(root.path(), &pointer("run-a", 1)).unwrap());
    assert!(!claim_active(root.path(), &pointer("run-b", 2)).unwrap());
    assert_eq!(read_active(root.path()).unwrap().unwrap().run_id, "run-a");
    // Only the pointer is left: the candidates were removed whichever way the link went.
    let names: Vec<_> = fs::read_dir(root.path()).unwrap().map(|entry| entry.unwrap().file_name()).collect();
    assert_eq!(names, [OsString::from("active.json")]);
    state::clear_active(root.path(), "run-b");
    assert!(
        read_active(root.path()).unwrap().is_some(),
        "another run cleared the owner's pointer"
    );
    state::clear_active(root.path(), "run-a");
    assert!(read_active(root.path()).unwrap().is_none());
}

// --- the log -------------------------------------------------------------------------------------------------

fn log_with(content: &[u8]) -> (tempfile::TempDir, PathBuf) {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("run.log");
    fs::write(&path, content).unwrap();
    (directory, path)
}

#[test]
fn a_log_tail_keeps_the_last_complete_lines() {
    let (_directory, path) = log_with(b"one\r\ntwo\nthree\nfragment");
    let (raw, truncated) = read_log_suffix(&path, Some(2)).unwrap();
    assert!(!truncated);
    assert_eq!(last_complete_lines(&raw, 2), ("two\nthree\n".to_owned(), true));
    // A tail longer than the log is the whole log, and says it is complete.
    assert_eq!(last_complete_lines(&raw, 50), ("one\ntwo\nthree\n".to_owned(), false));
    // A log with no newline yet has no complete line.
    assert_eq!(last_complete_lines("partial", 5), (String::new(), false));
    // An absent log is an empty one.
    assert_eq!(
        read_log_suffix(&path.with_file_name("absent.log"), None).unwrap(),
        (String::new(), false)
    );
}

/// Reading backwards stops once the tail is covered, and the front of the oldest chunk read can land inside a
/// multi-byte character; its continuation bytes are trimmed rather than decoded into garbage.
#[test]
fn a_log_read_from_the_middle_starts_on_a_character() {
    let mut content = Vec::new();
    // 70 KiB of three-byte characters, then two lines: more than one 64 KiB chunk, so the read starts mid-file.
    while content.len() < 70 * 1024 {
        content.extend_from_slice("€".as_bytes());
    }
    content.extend_from_slice(b"\nlast one\nlast two\n");
    let (_directory, path) = log_with(&content);
    let (raw, truncated) = read_log_suffix(&path, Some(1)).unwrap();
    assert!(truncated);
    assert!(raw.starts_with('€'), "the read did not start on a character boundary");
    assert!(!raw.contains('\u{fffd}'));
    assert_eq!(last_complete_lines(&raw, 1).0, "last two\n");

    // Without a tail the reply is capped at 1 MiB.
    let big = vec![b'x'; usize::try_from(log::MAX_LOG_RESPONSE_BYTES).unwrap() + 10];
    let (_directory, path) = log_with(&big);
    let (raw, truncated) = read_log_suffix(&path, None).unwrap();
    assert!(truncated);
    assert_eq!(raw.len() as u64, log::MAX_LOG_RESPONSE_BYTES);
}

#[test]
fn invalid_utf8_from_a_child_is_replaced_rather_than_refused() {
    let (_directory, path) = log_with(b"ok \xff\xfe bytes\n");
    let (raw, _) = read_log_suffix(&path, None).unwrap();
    assert_eq!(raw, "ok \u{fffd}\u{fffd} bytes\n");
}

// --- exits and identity --------------------------------------------------------------------------------------

#[test]
fn a_normalized_exit_code_is_the_code_else_128_plus_the_signal_else_255() {
    assert_eq!(normalized_exit_code(Some(3), Some("SIGKILL")), 3);
    assert_eq!(normalized_exit_code(None, Some("SIGKILL")), 137);
    assert_eq!(normalized_exit_code(None, Some("SIGTERM")), 143);
    assert_eq!(normalized_exit_code(None, Some("SIGINT")), 130);
    assert_eq!(normalized_exit_code(None, Some("SIGHUP")), 129);
    assert_eq!(normalized_exit_code(None, Some("not a signal")), 255);
    assert_eq!(normalized_exit_code(None, None), 255);
}

#[test]
fn an_exit_is_classified_by_its_code_or_by_its_signal_name() {
    let status = |script: &str| Command::new("/bin/sh").args(["-c", script]).status().unwrap();
    assert_eq!(
        classify_exit(status("exit 4")),
        identity::ExitOutcome {
            code: Some(4),
            signal: None
        }
    );
    assert_eq!(classify_exit(status("kill -KILL $$")).signal.as_deref(), Some("SIGKILL"));
    assert_eq!(classify_exit(status("kill -TERM $$")).signal.as_deref(), Some("SIGTERM"));
    assert_eq!(classify_exit(status("kill -TERM $$")).code, None);
}

/// This process's own identity reads back through `ps` and matches itself; its group is alive, and a group that
/// cannot exist is not.
#[test]
fn a_live_process_has_an_identity_that_matches_itself() {
    if !Path::new("/bin/ps").is_file() {
        eprintln!("skipped: /bin/ps is not on this host");
        return;
    }
    let own = identity::read_identity(i32::try_from(std::process::id()).unwrap()).unwrap();
    assert!(!own.process_start.is_empty() && !own.process_start.contains("  "), "{own:?}");
    assert!(identity_matches(&LiveSystem, &own));
    let mut recycled = own.clone();
    recycled.process_start.push('!');
    assert!(!identity_matches(&LiveSystem, &recycled));
    assert!(identity::group_alive(own.pgid));
    assert!(!identity::group_alive(0));
    assert_eq!(identity::read_identity(0), None);
}

// --- the child's environment and argv ------------------------------------------------------------------------

#[test]
fn the_child_environment_drops_the_channel_and_fixes_the_path() {
    let inherited = [
        ("TART_VM_TOKEN", "secret"),
        ("TART_VM_WORKER", "air-linux-3"),
        ("PARALLELS_VM_TOKEN", "secret"),
        ("DISPLAY", ":88"),
        ("PATH", "/somewhere"),
        ("USER", "worker"),
    ]
    .map(|(name, value)| (OsString::from(name), OsString::from(value)));
    let environment = child_environment(LaunchHost::Macos, inherited, &EnvironmentPolicy::Inherit);
    let get = |name: &str| {
        environment
            .get(&OsString::from(name))
            .map(|value| value.to_string_lossy().into_owned())
    };
    assert_eq!(get("TART_VM_TOKEN"), None);
    assert_eq!(get("TART_VM_WORKER"), None);
    assert_eq!(get("PARALLELS_VM_TOKEN"), None);
    assert_eq!(get("DISPLAY").as_deref(), Some(":88"));
    assert_eq!(get("HOME").as_deref(), Some("/Users/worker"));
    assert_eq!(get("IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP").as_deref(), Some("true"));
}

/// The PATH and the `HOME` fallback follow the guest OS. The PATH names no Node: the controller sets the PATH of
/// the pinned Node through `/usr/bin/env` in the start argv, and a Node directory here drifted from that pin once.
#[test]
fn the_child_path_and_home_follow_the_guest_os() {
    let cases = [
        (
            LaunchHost::Macos,
            "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
            "/Users/",
        ),
        (LaunchHost::Linux, "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin", "/home/"),
    ];
    for (host, path, home_parent) in cases {
        let environment = child_environment(
            host,
            [(OsString::from("USER"), OsString::from("worker"))],
            &EnvironmentPolicy::Inherit,
        );
        assert_eq!(environment[&OsString::from("PATH")], OsString::from(path), "{host:?}");
        assert_eq!(
            environment[&OsString::from("HOME")],
            OsString::from(format!("{home_parent}worker")),
            "{host:?}"
        );

        let without_user = child_environment(host, [(OsString::from("HOME"), OsString::new())], &EnvironmentPolicy::Inherit);
        assert_eq!(
            without_user[&OsString::from("HOME")],
            OsString::from(format!("{home_parent}admin")),
            "{host:?}"
        );
        let with_home = child_environment(
            host,
            [(OsString::from("HOME"), OsString::from("/srv/worker"))],
            &EnvironmentPolicy::Inherit,
        );
        assert_eq!(with_home[&OsString::from("HOME")], OsString::from("/srv/worker"), "{host:?}");
    }
}

/// The context policy copies only the allowlist, and `HOME` and `PATH` come from the layout of the context. A
/// credential of the supervisor's environment and the variables of the exec channel never reach the IDE.
#[test]
fn the_context_environment_is_the_layout_and_the_allowlist() {
    let inherited = [
        ("TART_VM_TOKEN", "secret"),
        ("PARALLELS_VM_TOKEN", "secret"),
        ("ANTHROPIC_API_KEY", "secret"),
        ("CODEX_HOME", "/Users/worker/.codex"),
        ("HOME", "/Users/worker"),
        ("PATH", "/somewhere"),
        ("DISPLAY", ":88"),
        ("XAUTHORITY", "/tmp/xauth"),
        ("_JAVA_AWT_WM_NONREPARENTING", "1"),
        ("LANG", "en_US.UTF-8"),
        ("TMPDIR", "/var/folders/x/T/"),
        ("USER", "worker"),
        ("LOGNAME", "worker"),
    ]
    .map(|(name, value)| (OsString::from(name), OsString::from(value)));
    let policy = EnvironmentPolicy::Context {
        context_dir: "/data/ide/key-1".to_owned(),
    };
    let expected: BTreeMap<OsString, OsString> = [
        ("DISPLAY", ":88"),
        ("XAUTHORITY", "/tmp/xauth"),
        ("_JAVA_AWT_WM_NONREPARENTING", "1"),
        ("LANG", "en_US.UTF-8"),
        ("TMPDIR", "/var/folders/x/T/"),
        ("USER", "worker"),
        ("LOGNAME", "worker"),
        ("HOME", "/data/ide/key-1/home"),
        ("PATH", "/data/ide/key-1/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"),
        ("IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP", "true"),
    ]
    .into_iter()
    .map(|(name, value)| (OsString::from(name), OsString::from(value)))
    .collect();
    assert_eq!(child_environment(LaunchHost::Linux, inherited, &policy), expected);
    for name in CHANNEL_VARIABLES {
        assert!(!ENVIRONMENT_ALLOWLIST.contains(&name), "{name}");
    }
}

/// The IDE of a context always runs under a UTF-8 locale, so it can read a non-ASCII file name. An inherited UTF-8
/// locale stays as it is. A missing, an empty or a non-UTF-8 one gets `LC_ALL` set to the UTF-8 locale, because
/// `LC_ALL` wins over `LANG`.
#[test]
fn the_context_environment_has_a_utf8_locale() {
    let policy = EnvironmentPolicy::Context {
        context_dir: "/data/ide/key-1".to_owned(),
    };
    let utf8 = Some(CONTEXT_UTF8_LOCALE);
    /// The inherited locale variables, and the `LANG` and `LC_ALL` that the IDE gets.
    type LocaleCase = (&'static [(&'static str, &'static str)], Option<&'static str>, Option<&'static str>);
    let cases: [LocaleCase; 8] = [
        (&[], None, utf8),
        (&[("LANG", "")], Some(""), utf8),
        (&[("LANG", "C")], Some("C"), utf8),
        (&[("LANG", "en_US.ISO-8859-1")], Some("en_US.ISO-8859-1"), utf8),
        (&[("LANG", "en_US.UTF-8")], Some("en_US.UTF-8"), None),
        (&[("LC_ALL", "de_DE.utf8")], None, Some("de_DE.utf8")),
        (&[("LC_ALL", ""), ("LANG", "en_US.UTF-8")], Some("en_US.UTF-8"), Some("")),
        (&[("LC_ALL", "POSIX"), ("LANG", "en_US.UTF-8")], Some("en_US.UTF-8"), utf8),
    ];
    for (inherited, lang, lc_all) in cases {
        let inherited = inherited.iter().map(|(name, value)| (OsString::from(name), OsString::from(value)));
        for host in [LaunchHost::Linux, LaunchHost::Macos] {
            let environment = child_environment(host, inherited.clone(), &policy);
            let value = |name: &str| environment.get(&OsString::from(name)).and_then(|value| value.to_str());
            assert_eq!(
                (value("LANG"), value("LC_ALL")),
                (lang, lc_all),
                "{host:?} {:?}",
                inherited.clone().collect::<Vec<_>>()
            );
        }
    }
    let inherit = child_environment(
        LaunchHost::Linux,
        [(OsString::from("LANG"), OsString::from("C"))],
        &EnvironmentPolicy::Inherit,
    );
    assert_eq!(
        inherit.get(&OsString::from("LC_ALL")),
        None,
        "the inherit policy keeps the locale of the supervisor"
    );
}

#[test]
fn a_cmd_launcher_runs_through_the_shell() {
    let argv = |words: &[&str]| words.iter().map(ToString::to_string).collect::<Vec<_>>();
    assert_eq!(child_spawn_argv(&argv(&["./tests.cmd", "-x"])), ["/bin/sh", "./tests.cmd", "-x"]);
    assert_eq!(child_spawn_argv(&argv(&["/bin/sleep", "1"])), ["/bin/sleep", "1"]);
}

// --- the launch ----------------------------------------------------------------------------------------------

fn strings(argv: &[OsString]) -> Vec<String> {
    argv.iter().map(|word| word.to_string_lossy().into_owned()).collect()
}

/// Every guest launches the bare `supervise` verb of this binary. The verb is one that the dispatch accepts, so the
/// supervisor supervises the run that `start` waits for.
#[test]
fn the_launch_is_the_supervise_verb_itself() {
    let launch = supervise_launch_for(
        Path::new("/home/admin/WorkerData/state/vm-guest-agent"),
        Path::new("/home/admin/WorkerData/state/ui-runs"),
        "run-ui-daemon-1",
    );
    let argv = strings(&launch.argv);
    assert_eq!(
        argv,
        [
            "/home/admin/WorkerData/state/vm-guest-agent",
            "supervise",
            "--root",
            "/home/admin/WorkerData/state/ui-runs",
            "--run",
            "run-ui-daemon-1",
        ]
    );
    let parsed = crate::cli::parse(&argv.iter().map(OsString::from).collect::<Vec<_>>());
    assert!(matches!(parsed.unwrap().verb, crate::cli::Verb::Supervise(_)));
}

/// The launch host is the guest OS of this build. No guest runs systemd, so the OS is the whole answer.
#[test]
fn the_launch_host_is_the_guest_os() {
    let wanted = if cfg!(target_os = "linux") {
        LaunchHost::Linux
    } else {
        LaunchHost::Macos
    };
    assert_eq!(LaunchHost::current(), wanted);
    assert_eq!((LaunchHost::Linux.as_str(), LaunchHost::Macos.as_str()), ("linux", "macos"));
}

/// A launch that cannot run is a refusal at once, and not a `start_timeout` 15 s later.
#[test]
fn a_launch_that_cannot_run_is_refused() {
    let directory = tempfile::tempdir().unwrap();
    let log = open_append(&directory.path().join("supervisor.log")).unwrap();
    let launch = SuperviseLaunch {
        argv: vec![directory.path().join("no-such-agent").into()],
    };
    let refusal = launch.start(&log).unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("internal_error", AgentExit::Failure),
        "{refusal:?}"
    );
    assert!(SuperviseLaunch { argv: Vec::new() }.start(&log).is_err());
}

// --- the contract --------------------------------------------------------------------------------------------

/// The `contract` line is what the host reads from any installed agent: the wire's lists and the digest of the bytes
/// that are running.
#[test]
fn the_contract_publishes_the_wire_and_the_digest_of_its_own_bytes() {
    let answered = run_agent(&["contract"], b"");
    assert_eq!(answered.exit, 0);
    let contract: Contract = serde_json::from_str(&answered.stdout).unwrap();
    let own = std::env::current_exe().unwrap();
    assert_eq!(contract, Contract::current(digest_file(&own).unwrap()));
    assert_eq!(contract.self_digest.len(), 64);
    assert!(answered.stdout.ends_with("}\n") && answered.stdout.lines().count() == 1);
}

// --- a real run ----------------------------------------------------------------------------------------------

/// Not a test of its own: the agent process the round trip below launches. The launcher script execs this test
/// binary filtered to this function, with the agent's argv in the environment; without that environment it
/// returns at once.
#[test]
fn agent_process() {
    let Some(count) = std::env::var_os("AVL_GUEST_AGENT_ARGC") else {
        return;
    };
    let count: usize = count.to_string_lossy().parse().unwrap();
    let args = (0..count).map(|index| std::env::var_os(format!("AVL_GUEST_AGENT_ARG_{index}")).unwrap());
    let stdin = io::stdin();
    let stdout = io::stdout();
    let stderr = io::stderr();
    let exit = crate::run(args, &mut stdin.lock(), &mut stdout.lock(), &mut stderr.lock());
    std::process::exit(i32::from(exit));
}

/// The parts of a test of a real supervisor run: a temporary directory, the slot root `ui-runs` in it, a launcher
/// that runs this test binary as the agent, and the run.
struct RealRun {
    directory: tempfile::TempDir,
    root: PathBuf,
    launcher: Launcher,
    run: RunArgs,
}

/// The parts of a test of the real run `run_id`, or `None` when the host lacks one of `tools`. The test is then
/// skipped, and the skip names the tools.
fn real_run(run_id: &str, tools: &[&str]) -> Option<RealRun> {
    if !tools.iter().all(|tool| Path::new(tool).is_file()) {
        eprintln!("skipped: {} is not on this host", tools.join(" or "));
        return None;
    }
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().join("ui-runs");
    let launcher = Launcher {
        self_exe: agent_launcher(directory.path()),
        host: LaunchHost::Macos,
    };
    let run = RunArgs {
        root: RootArgs { root: root.clone() },
        run_id: run_id.to_owned(),
    };
    Some(RealRun {
        directory,
        root,
        launcher,
        run,
    })
}

/// `start` launches a detached supervisor that runs the child in a session of its own; `status` and `log` see it
/// running; `cancel` TERMs its group through the supervisor, which records the cancellation and frees the slot.
#[test]
fn a_real_run_starts_reports_and_cancels() {
    let Some(RealRun {
        directory,
        root,
        launcher,
        run,
    }) = real_run("run-round-trip", &["/bin/ps", "/bin/sleep"])
    else {
        return;
    };
    let args = StartArgs {
        run: run.clone(),
        cwd: directory.path().to_path_buf(),
        snapshot_id: None,
        argv: vec!["/bin/sh".into(), "-c".into(), "echo started; exec /bin/sleep 60".into()],
    };

    let started = start(&LiveSystem, &launcher, &args).unwrap_or_else(|refusal| {
        let log = fs::read_to_string(root.join("run-round-trip/supervisor.log")).unwrap_or_default();
        panic!("start was refused: {refusal}; supervisor log:\n{log}")
    });
    assert_eq!(started.phase, Phase::Running, "{started:?}");
    // The log names the detected host before anything the spawn wrote, so a reaped run names its cause.
    let supervisor_log = fs::read_to_string(root.join("run-round-trip/supervisor.log")).unwrap();
    assert!(
        supervisor_log
            .lines()
            .next()
            .is_some_and(|line| line.ends_with(" start run-round-trip: launch host macos, supervisor in the released spawn")),
        "{supervisor_log}"
    );
    let pgid = started.pgid.unwrap();
    assert_eq!(started.pid, Some(pgid), "the child is not the leader of its own group");
    assert_ne!(
        pgid,
        identity::read_identity(i32::try_from(std::process::id()).unwrap()).unwrap().pgid
    );

    let status = status(&LiveSystem, &root, "run-round-trip").unwrap();
    assert_eq!(status.phase, Phase::Running);
    let active = active(&LiveSystem, &run.root).unwrap();
    assert_eq!(active.active.map(|state| state.run_id).as_deref(), Some("run-round-trip"));

    // The child writes its line just after it starts; the log verb sees it once it is there.
    let log_args = LogArgs {
        run: run.clone(),
        tail: Some(5),
    };
    let until = std::time::Instant::now() + Duration::from_secs(10);
    let mut reply = log(&LiveSystem, &log_args).unwrap();
    while reply.content != "started\n" && std::time::Instant::now() < until {
        thread::sleep(POLL_INTERVAL);
        reply = log(&LiveSystem, &log_args).unwrap();
    }
    assert_eq!(reply.content, "started\n");

    // A second run under the same id is refused as already there.
    let again = start(&LiveSystem, &launcher, &args).unwrap_err();
    assert_eq!((again.code.as_ref(), again.exit), ("run_exists", AgentExit::CantCreate));

    let finished = cancel(
        &LiveSystem,
        &CancelArgs {
            run: run.clone(),
            grace_ms: 5_000,
            thread_dump: None,
        },
    )
    .unwrap();
    assert_eq!(finished.phase, Phase::Finished);
    assert_eq!(finished.outcome, Some(Outcome::Canceled));
    assert_eq!(finished.signal, Some(Some("SIGTERM".to_owned())));
    assert_eq!(finished.exit_code, Some(143));
    assert!(finished.cancellation.unwrap().term_sent_at.is_some());
    assert!(!identity::group_alive(pgid), "the child's group outlived the cancel");
    assert_eq!(super::active(&LiveSystem, &run.root).unwrap().active, None);
}

/// A child that exits and leaves a process in its group does not orphan its run: the supervisor kills that process,
/// names it in the supervisor log, and the run finishes with the exit of the child.
#[test]
fn a_real_run_whose_child_leaves_a_member_finishes() {
    let Some(RealRun {
        directory,
        root,
        launcher,
        run,
    }) = real_run("run-leftover", &["/bin/ps", "/bin/sleep"])
    else {
        return;
    };
    // The child lives long enough to be identified, and its background sleep stays in its group.
    let args = StartArgs {
        run: run.clone(),
        cwd: directory.path().to_path_buf(),
        snapshot_id: None,
        argv: vec!["/bin/sh".into(), "-c".into(), "/bin/sleep 60 & exec /bin/sleep 1".into()],
    };
    let started = start(&LiveSystem, &launcher, &args).unwrap();
    let pgid = started.pgid.unwrap();

    let until = std::time::Instant::now() + Duration::from_secs(15);
    let mut state = status(&LiveSystem, &root, "run-leftover").unwrap();
    while state.phase == Phase::Running && std::time::Instant::now() < until {
        thread::sleep(POLL_INTERVAL);
        state = status(&LiveSystem, &root, "run-leftover").unwrap();
    }
    let supervisor_log = fs::read_to_string(root.join("run-leftover/supervisor.log")).unwrap();
    assert_eq!(
        (state.phase, state.outcome.clone(), state.exit_code),
        (Phase::Finished, Some(Outcome::Succeeded), Some(0)),
        "{state:?}\n{supervisor_log}"
    );
    assert!(!identity::group_alive(pgid), "the leftover member outlived the run");
    let finish = supervisor_log
        .lines()
        .find(|line| line.contains(" finish run-leftover: "))
        .unwrap_or_default();
    assert!(
        finish.contains(&format!("KILL to group {pgid}, members that outlived the child: 1 ["))
            && finish.contains("/bin/sleep 60]")
            && finish.ends_with("; the group is gone"),
        "{supervisor_log}"
    );
    assert_eq!(active(&LiveSystem, &run.root).unwrap().active, None);
}

/// The `ps` state of `pid`, such as `Z` for a zombie, or `None` when no process has the pid.
fn process_state(pid: i32) -> Option<String> {
    let output = Command::new("/bin/ps")
        .args(["-o", "stat=", "-p", &pid.to_string()])
        .output()
        .ok()?;
    let state = String::from_utf8_lossy(&output.stdout).trim().to_owned();
    (output.status.success() && !state.is_empty()).then_some(state)
}

// lane-run and the lane daemon are the parent of each supervisor that they spawn, so an ended supervisor stays their
// zombie until they reap it. reap_ended reaps it, and then no process has its pid. Another test of this process can
// reap it first, through a quit of the IDE owner, and that is a reap too.
#[test]
fn an_ended_supervisor_stays_a_zombie_until_its_parent_reaps_it() {
    let Some(RealRun {
        directory,
        root,
        launcher,
        run,
    }) = real_run("run-reaped", &["/bin/ps"])
    else {
        return;
    };
    let args = StartArgs {
        run,
        cwd: directory.path().to_path_buf(),
        snapshot_id: None,
        argv: vec!["/bin/sh".into(), "-c".into(), "exec /bin/sleep 1".into()],
    };
    start(&LiveSystem, &launcher, &args).unwrap();
    let until = std::time::Instant::now() + Duration::from_secs(20);
    let mut state = status(&LiveSystem, &root, "run-reaped").unwrap();
    while state.phase != Phase::Finished && std::time::Instant::now() < until {
        thread::sleep(POLL_INTERVAL);
        state = status(&LiveSystem, &root, "run-reaped").unwrap();
    }
    assert_eq!(state.phase, Phase::Finished, "{state:?}");
    let pid = state.supervisor_pid.unwrap();
    let mut ended = process_state(pid);
    while ended.as_deref().is_some_and(|state| !state.starts_with('Z')) && std::time::Instant::now() < until {
        thread::sleep(POLL_INTERVAL);
        ended = process_state(pid);
    }
    assert!(
        ended.as_deref().is_none_or(|state| state.starts_with('Z')),
        "the supervisor did not end: {ended:?}"
    );
    let reaped = reap_ended();
    assert!(
        reaped.contains(&u32::try_from(pid).unwrap()) || ended.is_none(),
        "reap_ended did not reap the zombie supervisor {pid}: {reaped:?}"
    );
    assert_eq!(process_state(pid), None, "the supervisor stayed after its reap");
}

// A lane daemon spawns its supervisors from its own image on Linux. An install renames a new agent onto the path of the
// running daemon, and `current_exe` then names `<path> (deleted)`, which no spawn finds. Elsewhere the path stays, and
// a rename there gives the new build.
#[test]
fn a_supervisor_is_spawned_from_the_image_of_this_process_on_linux() {
    let current = || Ok(PathBuf::from("/home/admin/WorkerData/state/vm-guest-agent (deleted)"));
    assert_eq!(own_image(true, current()), PathBuf::from(LINUX_OWN_IMAGE));
    assert_eq!(
        own_image(false, Ok(PathBuf::from("/Users/admin/WorkerData/state/vm-guest-agent"))),
        PathBuf::from("/Users/admin/WorkerData/state/vm-guest-agent")
    );
    assert_eq!(own_image(false, Err(io::Error::other("unreadable"))), PathBuf::new());
}

// A spawn of the link of the image runs this binary: the test binary starts itself through it and lists its tests.
#[cfg(target_os = "linux")]
#[test]
fn the_image_of_this_process_starts_through_its_link() {
    let status = Command::new(LINUX_OWN_IMAGE)
        .args(["--list", "--format", "terse"])
        .stdout(std::process::Stdio::null())
        .status()
        .unwrap();
    assert!(status.success(), "{status}");
}
