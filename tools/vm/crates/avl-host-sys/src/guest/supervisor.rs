//! The run supervisor's invocation and the guest agent's own verbs: the argv, the reply envelope, and the refusals
//! a failed call answers.

use std::collections::HashMap;
use std::time::Duration;

use async_trait::async_trait;
use avl_base::format::{clip, words};
use avl_base::{Exit, Refusal};
use avl_wire::supervisor::{
    AgentExit, Command, LogReply, ReceivedEnvelope, ReceivedError, RunState, SCHEMA_VERSION, USAGE_CODE,
    decode_run_state as decode_wire_run_state,
};
use avl_wire::verb::AgentVerb;
use serde::Deserialize;
use serde_json::json;
use serde_json::value::RawValue;

use super::{AGENT_USAGE_LINE_PREFIXES, Guest, quoted_first_line, root_argv, sudo_complaint, user_argv};
use crate::ctx::Ctx;
use crate::proc::{Captured, FAILURE_OUTPUT_TAIL_BYTES, SpawnOptions};
use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

/// The two ways one supervisor invocation can differ.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SupervisorOptions {
    /// Asks for a session the IDE can open a window in. See [`Guest::invoke_supervisor`].
    pub aqua: bool,
    /// Bounds the guest call. Each caller names it, because a verb that reads a state file answers in a second and
    /// a verb that waits takes as long as its wait.
    pub timeout: Duration,
}

/// The timeout of a supervisor verb that reads the state file of the run slot: one exec round trip and a file read.
const ACTIVE_RUN_TIMEOUT: Duration = Duration::from_secs(30);

/// Which of the guest's two accounts one agent verb runs under.
///
/// Spelled by the caller rather than looked up from the verb, because it is the verb's own requirement and a table
/// here would be a second copy of the requirement the verb states.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AgentAccount {
    /// [`user_argv`]'s prefix: the account the daemon and the IDE run as. `stage`, `gc` and `launch-prep` must be
    /// it - everything they create is read and written later by that account.
    Worker,
    /// [`root_argv`]'s. Only `validate-guest` of the Linux boot asks for it.
    Root,
}

/// What holds one worker's run slot, for the callers that treat a parked daemon differently from work in
/// progress.
///
/// The slot itself cannot tell them apart: the daemon takes it with `supervisor start` and keeps it for its whole
/// life, so a run id in the slot proves an owner and not an activity. Deciding that needs the daemon's own account
/// of itself, which is what [`ParkedDaemonProbe`] fetches.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum RunSlot {
    /// Nothing owns the worker's runs root.
    Free,
    /// This controller's own daemon, holding the slot with no iteration in flight.
    ParkedDaemon(RunState),
    /// Anything else that holds the slot - another run, or a daemon that cannot be shown to be idle. The answer a
    /// caller refuses on, and the answer every unprovable case collapses to.
    Executing(RunState),
}

/// Whether the run holding one worker's slot is this controller's own daemon, idle.
///
/// Declared here and implemented by the daemon crate for the reason [`super::BazelHost`] is: a lease operation must
/// not learn the daemon protocol to ask one question about it.
#[async_trait]
pub trait ParkedDaemonProbe: Send + Sync {
    /// The run id of the worker's parked daemon, or `None` for anything else - no recorded daemon, another run in
    /// the slot, a daemon that does not answer, an iteration in flight.
    async fn parked_daemon_run(&self, ctx: &Ctx, worker: &str) -> Result<Option<String>, Refusal>;

    /// The run id this controller last recorded for the worker's daemon. It asks the daemon nothing and cannot
    /// fail, and it exists for the refusal rather than the decision: a slot held by the very run the record names
    /// is a daemon that stopped answering, which `daemon stop` retires; a daemon run that no record names is a
    /// failed start's or a dead controller's, which `daemon stop` retires too; and a slot held by some other run
    /// is work in flight.
    fn recorded_daemon_run(&self, worker: &str) -> Option<String>;
}

/// How much of the guest agent's *own* structured message travels in a refusal.
///
/// Larger than the argv bound on purpose, and the difference is the trust class. That bound is for text nobody on
/// this side wrote; this bounds text the agent composed itself from this controller's own manifest, so what remains
/// to bound is the envelope's size and not its contents - which is why the number is the module's existing answer
/// to how much text one refusal may carry.
pub(crate) const GUEST_AGENT_MESSAGE_BYTES: usize = FAILURE_OUTPUT_TAIL_BYTES;

/// `EX_USAGE`, which the guest agent's dispatch exits with for a verb it does not have and for arguments it cannot
/// parse. The number is the diagnosis: it is what an agent older than its controller looks like. The agent's own
/// declaration, not a second `64`.
pub(crate) const GUEST_AGENT_USAGE_EXIT: i32 = AgentExit::Usage.code();

/// The diagnosis both usage shapes carry, the old agent's usage block and the Rust agent's `usage` envelope. A pull
/// through a verb that the installed agent does not have appends it too.
pub const OLDER_AGENT_HINT: &str = "the installed guest agent does not have this verb or these arguments, which is what \
                                an agent older than this controller looks like";

/// What every disagreement about this wire answers. The prose names which half is unlikely to be stale, because
/// the actionable half is not obvious: the guest copy is reinstalled whenever a run, a lease operation or an
/// observation begins, so a mismatch is almost always a controller rebuilt without its agent.
fn supervisor_protocol_error(code: &'static str, detail: &str) -> Refusal {
    Refusal::new(
        code,
        Exit::SOFTWARE,
        format!(
            "{detail}. The guest run supervisor and this controller disagree about its reply schema (controller \
             speaks v{SCHEMA_VERSION}); a renamed field is the usual cause. The guest copy is installed from the \
             built `vm-guest-agent` binary when a run, a lease operation or an observation begins, so a stale \
             guest copy is the unlikely one."
        ),
    )
}

/// A refusal's exit status kept the guest's own where there is one: a protocol failure on a command that exited 0
/// still has to leave nonzero.
fn exit_or(exit_code: i32, fallback: Exit) -> Exit {
    Exit::from_status(exit_code, fallback)
}

/// The code and message one structured refusal from the agent asks for: the agent's own where it stated them, and
/// the caller's fallback where it did not. One function for both wires, so the bound cannot be applied to one and
/// forgotten on the other. The fallback is never clipped: it is this controller's own sentence.
fn agent_envelope_error(failure: Option<&ReceivedError>, code: String, message: String) -> (String, String) {
    let Some(failure) = failure else {
        return (code, message);
    };
    let code = if failure.code.is_empty() { code } else { failure.code.clone() };
    let message = if failure.message.is_empty() {
        message
    } else {
        clip(&failure.message, GUEST_AGENT_MESSAGE_BYTES).to_owned()
    };
    (code, message)
}

/// What a failed agent verb refused with: the agent's own code and message when it answered an envelope, and its
/// exit code plus - for one shape of stderr only - the first line it printed when it did not.
///
/// The envelope is the normal answer: both edges that can fail on guest work write one. A 64 is the dispatch
/// printing its usage block for a verb or an argument it does not have, the one shape only an older agent produces,
/// because every verb that fails *inside* the agent answers 70.
///
/// Quoting a usage line is a deliberate exception to the withholding contract of [`Guest::raw`], and the prefix is
/// tested, not just the line number: a first line is no evidence of who wrote it, and a verb whose own failure
/// message quotes a subprocess puts that subprocess's bytes on this stream too.
///
/// # The sudo arm
///
/// These verbs deliberately do not go through [`Guest::raw`], so until 2026-08-27 a guest that had genuinely lost
/// passwordless sudo refused `guest_agent_failed ... exited with 1` with sudo's sentence withheld. It is a code of its
/// own rather than a quoted line, because a verb sudo refused never ran, and `guest_agent_failed` is a claim about the
/// agent. The prefix is stronger evidence here than in [`Guest::raw`]: the argv is always `sudo … <agent> <verb>`, and
/// the agent's own failures are envelopes and usage lines.
fn agent_refusal(verb: AgentVerb, worker: &str, exit_code: i32, raw: &str) -> Refusal {
    if let Ok(envelope) = ReceivedEnvelope::read(raw)
        && let Some(error) = envelope
            .error
            .as_ref()
            .filter(|error| !error.code.is_empty() || !error.message.is_empty())
    {
        let (code, spoken) = agent_envelope_error(Some(error), "guest_agent_failed".to_owned(), "it stated no reason".to_owned());
        // The verb and the worker are prefixed here and not on the supervisor's envelope path, because a
        // supervisor refusal reaches an operator attributed structurally, while these reach a refusal that carries
        // no scope: without the prefix a pool-wide operation would report a staging check with no worker in it.
        let mut message = format!("the guest agent's {verb} in {worker} refused: {spoken}");
        // The Rust agent answers a verb or an argument it does not have with an envelope rather than a usage
        // block, and that envelope is still what an older agent looks like.
        if code == USAGE_CODE {
            message.push_str(". ");
            message.push_str(OLDER_AGENT_HINT);
        }
        return Refusal::new(code, exit_or(exit_code, Exit::FAILURE), message);
    }
    // Read before the agent's own shapes, because a verb sudo refused never ran.
    if let Some(complaint) = sudo_complaint(raw) {
        return Refusal::new(
            "guest_sudo_refused",
            exit_or(exit_code, Exit::FAILURE),
            format!(
                "passwordless sudo is not working in {worker}, so the guest agent's {verb} never ran: {complaint}. \
                 Every privileged guest command is a sudo -H, with -u for the worker account, and nothing in this \
                 repository installs that sudoers policy: a worker inherits it from its base image"
            ),
        )
        .with_details(json!({ "exitCode": exit_code }));
    }
    let mut message = format!("the guest agent's {verb} in {worker} exited with {exit_code}");
    if let Some(usage) = quoted_first_line(raw, AGENT_USAGE_LINE_PREFIXES) {
        message.push_str(": ");
        message.push_str(&usage);
    }
    if exit_code == GUEST_AGENT_USAGE_EXIT {
        message.push_str(". Exit 64 is EX_USAGE from the agent's own dispatch: ");
        message.push_str(OLDER_AGENT_HINT);
    }
    Refusal::new("guest_agent_failed", exit_or(exit_code, Exit::FAILURE), message).with_details(json!({ "exitCode": exit_code }))
}

/// Reads one run state out of a supervisor reply: the one place the phase vocabulary is checked.
///
/// `run_id` is the run the caller asked about, compared by the wire's decoder - the check that catches an agent
/// answering about the previous run in the slot. `None` for `active`, whose question was "whatever holds the
/// slot": the identity is then read out of the reply first, and the schema and phase gates still apply.
fn decode_run_state(data: &RawValue, run_id: Option<&str>, label: &str) -> Result<RunState, Refusal> {
    #[derive(Deserialize)]
    struct Identity {
        #[serde(rename = "runId", default)]
        run_id: String,
    }
    let run_id = match run_id {
        Some(run_id) => run_id.to_owned(),
        None => serde_json::from_str::<Identity>(data.get())
            .ok()
            .map(|identity| identity.run_id)
            .filter(|run_id| !run_id.is_empty())
            .ok_or_else(|| supervisor_protocol_error("supervisor_protocol", &format!("{label} names no run")))?,
    };
    decode_wire_run_state(data.get().as_bytes(), &run_id)
        .map_err(|error| supervisor_protocol_error("supervisor_protocol", &format!("{label} is unreadable: {error}")))
}

/// What the agent said, on the stream its own outcome selects: stdout on success and stderr on failure, because
/// that is the envelope contract the agent shares with this controller's own commands. One spelling of that rule
/// for both invocations here, because a reply read off the wrong stream is an empty document that every decoder
/// reports as a protocol failure rather than as the refusal the agent actually sent.
fn agent_speech(captured: &Captured) -> &str {
    if captured.exit_code == 0 {
        &captured.stdout
    } else {
        &captured.stderr
    }
}

impl Guest<'_> {
    /// Runs one supervisor command in the guest and answers the `data` that command declared.
    ///
    /// The command is its own parameter rather than the first argument, which is what lets the guest's echoed
    /// `command` be compared with what was asked. Not through [`Guest::raw`]: a failed supervisor call answers a
    /// *structured* refusal on stderr, and the whole point is to report the agent's own code rather than "exited
    /// with 1".
    ///
    /// "aqua" means "where the IDE can open a window". On macOS that is the console user's launchd session,
    /// reachable only through `launchctl asuser`; on Linux it is any process with DISPLAY set at the X server the
    /// guest runs, so `setsid` is enough to detach it from this exec.
    pub async fn invoke_supervisor(&self, command: Command, args: &[String], options: SupervisorOptions) -> Result<Box<RawValue>, Refusal> {
        let settings = self.settings;
        let mut user_command = words([
            "/usr/bin/env",
            // The authorizer's own check spawns a network call the guest cannot make, and a run that waits for it
            // times out inside the IDE rather than here.
            "IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true",
            &settings.vm_agent,
            command.as_str(),
        ]);
        user_command.extend_from_slice(args);
        let argv = if !options.aqua {
            user_argv(settings, &user_command)
        } else if settings.is_macos_guest() {
            let mut argv = words(["/bin/launchctl", "asuser", &settings.vm_uid]);
            argv.extend(user_argv(settings, &user_command));
            argv
        } else {
            let display = format!("DISPLAY={}", settings.guest_display);
            let mut session = words(["/usr/bin/setsid", "--wait", "/usr/bin/env", &display]);
            session.extend(user_command);
            user_argv(settings, &session)
        };
        let capture = SpawnOptions::timeout(options.timeout, "supervisor_timeout");
        let captured = self.channel.exec(self.ctx, &argv, &capture).await?;
        let Ok(envelope) = ReceivedEnvelope::read(agent_speech(&captured)) else {
            return Err(Refusal::new(
                "supervisor_protocol",
                exit_or(captured.exit_code, Exit::SOFTWARE),
                format!("guest run supervisor returned invalid JSON (exit {})", captured.exit_code),
            ));
        };
        let data = envelope.data.filter(|data| data.get() != "null");
        let Some(data) = data.filter(|_| captured.exit_code == 0 && envelope.ok) else {
            let (code, message) = agent_envelope_error(
                envelope.error.as_ref(),
                "supervisor_failed".to_owned(),
                format!("guest run supervisor exited {}", captured.exit_code),
            );
            return Err(Refusal::new(code, exit_or(captured.exit_code, Exit::FAILURE), message));
        };
        // After the `ok` check, so a refusal from an agent of another schema is reported as what the agent said
        // rather than as a version quarrel.
        if envelope.schema_version != i64::from(SCHEMA_VERSION) {
            return Err(supervisor_protocol_error(
                "supervisor_schema_unsupported",
                &format!(
                    "the guest run supervisor answered {command} with schema {}",
                    envelope.schema_version
                ),
            ));
        }
        if envelope.command != command.as_str() {
            return Err(supervisor_protocol_error(
                "supervisor_protocol",
                &format!("{command} was asked for and the guest answered for {:?}", envelope.command),
            ));
        }
        Ok(data)
    }

    /// Runs one of the guest agent's own verbs - the ones that answer a document of their own rather than a
    /// supervisor envelope - and answers what it printed on stdout.
    ///
    /// These verbs went through [`Guest::raw`], which could only report `sudo in WORKER exited with 64`: every guest
    /// call is re-targeted with sudo, and 64 is `EX_USAGE` from the agent's own dispatch four argv elements further
    /// along. A session on 2026-08-24 read that, concluded passwordless sudo was gone, and abandoned two workers;
    /// the fault was an agent older than this controller. The argv is [`Guest::as_user`]'s or [`Guest::as_root`]'s
    /// unchanged, because what was wrong was the reporting and not the wire.
    pub async fn invoke_agent(
        &self,
        account: AgentAccount,
        verb: AgentVerb,
        args: &[String],
        options: &SpawnOptions,
    ) -> Result<String, Refusal> {
        let mut invocation = words([self.settings.vm_agent.as_str(), verb.as_str()]);
        invocation.extend_from_slice(args);
        let argv = match account {
            AgentAccount::Worker => user_argv(self.settings, &invocation),
            AgentAccount::Root => root_argv(self.settings, &invocation),
        };
        let captured = self.channel.exec(self.ctx, &argv, options).await?;
        if captured.exit_code == 0 {
            return Ok(captured.stdout);
        }
        Err(agent_refusal(verb, self.worker(), captured.exit_code, agent_speech(&captured)))
    }

    /// Runs one of the verbs that answer a run state, and decodes it through the wire, comparing its identity with
    /// `run_id` when the caller has one to compare.
    pub async fn supervisor_run_state_reply(
        &self,
        command: Command,
        args: &[String],
        run_id: Option<&str>,
        options: SupervisorOptions,
    ) -> Result<RunState, Refusal> {
        let data = self.invoke_supervisor(command, args, options).await?;
        decode_run_state(&data, run_id, &format!("the supervisor's {command} reply"))
    }

    /// Which run owns one supervisor root's slot, or `None` for a free slot.
    ///
    /// This wire's null convention is inverted, and both halves are load-bearing: an explicit `{"active": null}` is
    /// the good case and the answer the controller acts on most often, so a *missing* key is the protocol failure.
    pub async fn supervisor_active_reply(&self, root: &str, options: SupervisorOptions) -> Result<Option<RunState>, Refusal> {
        let data = self.invoke_supervisor(Command::Active, &words(["--root", root]), options).await?;
        let Ok(mut fields) = serde_json::from_str::<HashMap<String, Box<RawValue>>>(data.get()) else {
            return Err(supervisor_protocol_error(
                "supervisor_protocol",
                "the supervisor's active reply is not an object",
            ));
        };
        let Some(active) = fields.remove("active") else {
            return Err(supervisor_protocol_error(
                "supervisor_protocol",
                "the supervisor's active reply has no active field",
            ));
        };
        if active.get() == "null" {
            return Ok(None);
        }
        decode_run_state(&active, None, "the supervisor's active run").map(Some)
    }

    /// One run's captured output. Every field is required: a log reply whose `content` silently became `""` reads as
    /// a run that printed nothing, so a renamed field is a refusal rather than an empty string.
    pub async fn supervisor_log_reply(&self, args: &[String], options: SupervisorOptions) -> Result<LogReply, Refusal> {
        let data = self.invoke_supervisor(Command::Log, args, options).await?;
        serde_json::from_str(data.get()).map_err(|error| {
            supervisor_protocol_error("supervisor_protocol", &format!("the supervisor's log reply is unreadable: {error}"))
        })
    }

    /// The run holding this worker's run slot, or `None`.
    pub async fn active_run(&self) -> Result<Option<RunState>, Refusal> {
        self.supervisor_active_reply(
            &self.settings.vm_runs_root,
            SupervisorOptions {
                aqua: false,
                timeout: ACTIVE_RUN_TIMEOUT,
            },
        )
        .await
    }

    /// What holds the worker's run slot: a holder is a parked daemon only when `probe` names it as this controller's
    /// idle daemon, and executing otherwise.
    pub async fn judge_run_slot(&self, probe: &dyn ParkedDaemonProbe) -> Result<RunSlot, Refusal> {
        let Some(active) = self.active_run().await? else {
            return Ok(RunSlot::Free);
        };
        let parked = probe.parked_daemon_run(self.ctx, self.worker()).await?;
        // The comparison, not the non-empty answer alone: a probe naming a daemon that is not the run in the slot has
        // found two owners of one slot, and the one this operation would step on is the slot's.
        if parked.as_deref() == Some(active.run_id.as_str()) {
            return Ok(RunSlot::ParkedDaemon(active));
        }
        Ok(RunSlot::Executing(active))
    }

    /// Refuses an operation while a run owns the slot.
    ///
    /// The strict question: any owner refuses. `observe exec` asks it because a parked daemon still owns a live IDE
    /// whose pointer and focus an arbitrary guest command can take. `daemon start` asks no question of the slot: it
    /// retires what it finds there itself (`retire_unrecorded_daemon`). For the operations a parked daemon must
    /// survive, see [`Guest::reject_executing_run`].
    pub async fn reject_active_run(&self, operation: &str) -> Result<(), Refusal> {
        match self.active_run().await? {
            None => Ok(()),
            Some(active) => Err(Refusal::new(
                "run_active",
                Exit::FAILURE,
                format!("cannot {operation}; worker {} has active run {}", self.worker(), active.run_id),
            )),
        }
    }

    /// Refuses an operation only while something is *executing* in the worker's run slot.
    ///
    /// A parked daemon of this controller's own passes. The refusal names what it found in the shapes a caller can
    /// act on: work in flight names that run, and a daemon that stopped answering names the record it was compared
    /// against and the command that retires it. A daemon run that no record names is a failed start's or a dead
    /// controller's, and `daemon stop` retires it.
    pub async fn reject_executing_run(&self, probe: &dyn ParkedDaemonProbe, operation: &str) -> Result<(), Refusal> {
        let RunSlot::Executing(active) = self.judge_run_slot(probe).await? else {
            return Ok(());
        };
        let worker = self.worker();
        let recorded = probe.recorded_daemon_run(worker);
        let message = if recorded.as_deref() == Some(active.run_id.as_str()) {
            format!(
                "cannot {operation}; worker {worker} holds daemon run {} in its run slot, and that daemon does not \
                 answer as idle; retire it with `daemon stop` and try again",
                active.run_id
            )
        } else if avl_wire::daemon::is_daemon_run(&active.run_id) {
            format!(
                "cannot {operation}; worker {worker} holds daemon run {} in its run slot, and no daemon record on \
                 this host names it; retire it with `daemon stop` and try again",
                active.run_id
            )
        } else {
            format!("cannot {operation}; worker {worker} has run {} in flight", active.run_id)
        };
        Err(Refusal::new("run_active", Exit::FAILURE, message))
    }
}
