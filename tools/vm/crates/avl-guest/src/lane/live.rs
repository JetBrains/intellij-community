//! The live context of a lane: the real agents of the lane `UI_LIVE`, which spend the JetBrains Central account of the
//! developer.
//!
//! [`LiveRun`] is the live run of one iteration. It starts only in the lane daemon of a Linux guest whose environment
//! names the run secrets directory, and only with every declared runtime of the live context. [`central`] wires the
//! guest account to Central before the lane IDE launches. [`home`] gives the lane home the Central wiring of that
//! account, and the bin directory the launchers of the live agents and the Central guard. At the end of the iteration
//! the run deletes the copies of the lane home and tears the account down.

use std::path::{Path, PathBuf};
use std::sync::{Mutex, PoisonError};

use avl_wire::daemon::LINUX_GUEST_LIVE_LABEL;
use avl_wire::ide::IdePrepared;
use avl_wire::program::lane::LiveContext;

use self::central::{AgentProcesses, Central, GuestAccount, LiveAgentProcesses, ProcessRunner, STEP_TIMEOUT};
use self::home::{Launcher, LiveHome, WiringStamps, leftover_junie_sessions, require_isolated_project, write_bin};
use super::context::CliRuntime;
use crate::reply::{AgentRefusal, AgentRefusalExt};

pub(crate) mod central;
pub(crate) mod home;

#[cfg(test)]
mod tests;

/// The refusal code of a live lane that the lane daemon cannot launch as it states.
pub(crate) const LIVE_INPUT_CODE: &str = "lane_live_input";

/// The daemon environment variable of the Node of the guest, which the controller names.
const NODE_VARIABLE: &str = "NODE_BIN";

/// The live run of one iteration: the guest account, the declared runtimes of the live context, and the lane home that
/// the seed wrote. Each line of what it did waits in its notes until the iteration takes them.
pub(crate) struct LiveRun {
    context: LiveContext,
    account: GuestAccount,
    /// The declared Central CLI, an absolute guest path, and the version that its release track pins.
    central_cli: PathBuf,
    central_version: String,
    /// The declared runtimes of the live agents, in the order of the live context.
    runtimes: Vec<CliRuntime>,
    /// The Node of the guest, whose directory holds `npm` and `npx` too.
    node: PathBuf,
    seeded: Mutex<Option<Seeded>>,
    notes: Mutex<Vec<String>>,
    ended: Mutex<bool>,
    /// How the teardown ends the processes that the agents left: every process of the account by its executable name.
    processes: Mutex<Box<dyn AgentProcesses + Send>>,
}

/// The lane home that the seed wrote, and the stamps of the account wiring that it copied.
struct Seeded {
    lane_home: PathBuf,
    project_dir: PathBuf,
    stamps: WiringStamps,
}

impl LiveRun {
    /// The live run of `context` in the lane daemon whose environment `variable` reads, with the declared runtimes
    /// `declared` of the runtime descriptor. Only the verb `lane-daemon` asks.
    ///
    /// A run outside the lane daemon of a Linux guest is refused, and so is a descriptor that lacks a runtime of the
    /// live context. That refusal names the define of the build that adds them.
    pub(crate) fn start(
        context: &LiveContext,
        declared: &[CliRuntime],
        variable: &dyn Fn(&str) -> Option<String>,
        linux: bool,
    ) -> Result<Self, AgentRefusal> {
        let Some(account) = GuestAccount::of_lane_daemon(variable, linux)? else {
            return Err(refused(format!(
                "the lane UI_LIVE runs only in the lane daemon of a Linux guest, whose environment names the run secrets \
                 directory; pipe the login into the run: `{} | vm.cmd run --lane ui-live --test-env {}=@-`",
                context.login_source, context.login_input
            )));
        };
        let needed: Vec<&str> = std::iter::once(context.central_runtime.as_str())
            .chain(context.runtimes.iter().map(|runtime| runtime.name.as_str()))
            .collect();
        let missing: Vec<&str> = needed
            .iter()
            .copied()
            .filter(|name| !declared.iter().any(|runtime| runtime.name == *name))
            .collect();
        if !missing.is_empty() {
            return Err(refused(format!(
                "the live lane needs the declared CLI runtimes {}, and the runtime descriptor lacks {}; run it over \
                 {LINUX_GUEST_LIVE_LABEL}, which `vm.cmd run --lane ui-live` builds",
                spoken_list(&needed),
                spoken_list(&missing)
            )));
        }
        let named = |name: &str| declared.iter().find(|runtime| runtime.name == name).cloned();
        let central = named(&context.central_runtime).ok_or_else(|| AgentRefusal::internal("the Central runtime went missing"))?;
        let [cli] = central.command.as_slice() else {
            return Err(refused(format!(
                "the declared Central runtime '{}' runs through an interpreter, and the lane runs it as one executable",
                central.name
            )));
        };
        let central_version = central
            .version
            .clone()
            .ok_or_else(|| refused(format!("the declared Central runtime '{}' states no version to pin", central.name)))?;
        let node = variable(NODE_VARIABLE)
            .filter(|value| !value.trim().is_empty())
            .map(PathBuf::from)
            .ok_or_else(|| refused(format!("the lane daemon has no {NODE_VARIABLE}, the Node of the registry agents")))?;
        Ok(Self {
            runtimes: context.runtimes.iter().filter_map(|runtime| named(&runtime.name)).collect(),
            context: context.clone(),
            account,
            central_cli: PathBuf::from(cli),
            central_version,
            node,
            seeded: Mutex::new(None),
            notes: Mutex::new(Vec::new()),
            ended: Mutex::new(false),
            processes: Mutex::new(Box::new(LiveAgentProcesses)),
        })
    }

    /// This run with `processes` in place of the sweep of the account. A test passes a fake, because the real sweep
    /// ends every process of the account that has the name of an agent.
    #[cfg(test)]
    pub(crate) fn with_agent_processes(self, processes: Box<dyn AgentProcesses + Send>) -> Self {
        *self.processes.lock().unwrap_or_else(PoisonError::into_inner) = processes;
        self
    }

    /// Wires the guest account to Central with the login of the run input of the live context.
    pub(crate) fn provision(&self) -> Result<(), AgentRefusal> {
        let login = self.account.input(&self.context.login_input);
        self.with_central(|central| central.provision(&login, &mut |line| self.note(line)).map(|_| ()))
    }

    /// Seeds the context `paths` of the lane IDE: the launchers, the Central guard and the Node executables of the bin
    /// directory, and the Central wiring and the agent seeds of the home. A context that this run seeded already stays
    /// as it is.
    pub(crate) fn seed(&self, paths: &IdePrepared) -> Result<(), AgentRefusal> {
        let lane_home = PathBuf::from(&paths.home_dir);
        if self.lock_seeded().as_ref().is_some_and(|seeded| seeded.lane_home == lane_home) {
            return Ok(());
        }
        let project_dir = PathBuf::from(&paths.project_dir);
        require_isolated_project(&project_dir)?;
        let empty = std::collections::BTreeMap::new();
        let launchers: Vec<Launcher<'_>> = self
            .runtimes
            .iter()
            .map(|runtime| Launcher {
                name: &runtime.name,
                command: &runtime.command,
                environment: self
                    .context
                    .runtimes
                    .iter()
                    .find(|live| live.name == runtime.name)
                    .map_or(&empty, |live| &live.environment),
            })
            .collect();
        write_bin(
            Path::new(&paths.bin_dir),
            &launchers,
            &self.central_cli,
            self.account.home(),
            &self.node,
        )?;
        let stamps = self.home(&lane_home, &project_dir).seed(&mut |line| self.note(line))?;
        *self.lock_seeded() = Some(Seeded {
            lane_home,
            project_dir,
            stamps,
        });
        Ok(())
    }

    /// The host id `refresh-live-wiring`: copies the wiring of the account into the lane home again when the account
    /// re-wired since the seed.
    pub(crate) fn refresh(&self) -> Result<(), String> {
        let mut seeded = self.lock_seeded();
        let Some(seeded) = seeded.as_mut() else {
            return Err("the live lane home is not seeded, so its wiring cannot be refreshed".to_owned());
        };
        let home = LiveHome {
            account_home: self.account.home(),
            lane_home: &seeded.lane_home,
            project_dir: &seeded.project_dir,
            central_port: self.context.central_port,
        };
        let refreshed = home.refresh(&mut seeded.stamps, &mut |line| self.note(line));
        refreshed.map(|_| ()).map_err(|refusal| refusal.message)
    }

    /// The Junie sessions of the live project that the session index of the account names: what an earlier run, or the
    /// retyped prompt of a scenario, left there.
    pub(crate) fn leftover_junie_sessions(&self) -> Vec<String> {
        leftover_junie_sessions(self.account.home(), Path::new(&self.context.project_unpack_root))
    }

    /// The end of the iteration: deletes the credential copies of the lane home, then tears the account down. It runs
    /// once, also after a failed launch.
    pub(crate) fn end(&self) -> Result<(), AgentRefusal> {
        let mut ended = self.ended.lock().unwrap_or_else(PoisonError::into_inner);
        if *ended {
            return Ok(());
        }
        *ended = true;
        let seeded = self.lock_seeded().take();
        if let Some(seeded) = seeded {
            let removed = self
                .home(&seeded.lane_home, &seeded.project_dir)
                .remove_copies(&mut |line| self.note(line));
            if !removed.is_empty() {
                self.note(&format!(
                    "deleted the credential copies of the lane home: {}",
                    removed.iter().map(|path| path.display().to_string()).collect::<Vec<_>>().join(", ")
                ));
            }
        }
        let removed = self.with_central(|central| central.tear_down(&mut |line| self.note(line)))?;
        if !removed.is_empty() {
            self.note(&format!(
                "central provisioning torn down: {}",
                removed.iter().map(|path| path.display().to_string()).collect::<Vec<_>>().join(", ")
            ));
        }
        Ok(())
    }

    /// The lines of what the run did since the last call.
    pub(crate) fn take_notes(&self) -> Vec<String> {
        std::mem::take(&mut *self.notes.lock().unwrap_or_else(PoisonError::into_inner))
    }

    /// Runs `work` with the Central CLI of this run, the real runner and the sweep of this run.
    fn with_central<T>(
        &self,
        work: impl for<'c> FnOnce(&mut Central<'c, ProcessRunner, &'c mut Box<dyn AgentProcesses + Send>>) -> T,
    ) -> T {
        let mut processes = self.processes.lock().unwrap_or_else(PoisonError::into_inner);
        let mut central = Central {
            account: &self.account,
            cli: &self.central_cli,
            pinned_version: &self.central_version,
            port: self.context.central_port,
            agents: &self.context.central_agents,
            step_timeout: STEP_TIMEOUT,
            runner: ProcessRunner,
            processes: &mut *processes,
        };
        work(&mut central)
    }

    fn home<'h>(&'h self, lane_home: &'h Path, project_dir: &'h Path) -> LiveHome<'h> {
        LiveHome {
            account_home: self.account.home(),
            lane_home,
            project_dir,
            central_port: self.context.central_port,
        }
    }

    fn note(&self, line: &str) {
        self.notes.lock().unwrap_or_else(PoisonError::into_inner).push(line.to_owned());
    }

    fn lock_seeded(&self) -> std::sync::MutexGuard<'_, Option<Seeded>> {
        self.seeded.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

impl std::fmt::Debug for LiveRun {
    /// Names the account and the Central CLI only: no note and no input of the run.
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("LiveRun")
            .field("account_home", &self.account.home())
            .field("central_cli", &self.central_cli)
            .finish_non_exhaustive()
    }
}

impl Drop for LiveRun {
    /// A run that a panic ended still tears the account down.
    fn drop(&mut self) {
        if let Err(refusal) = self.end() {
            super::channel::log(&format!("the live run did not end cleanly: {}", refusal.message));
        }
    }
}

/// `names` as English says a list: `a`, `a and b`, `a, b and c`.
fn spoken_list(names: &[&str]) -> String {
    match names {
        [] => String::new(),
        [one] => (*one).to_owned(),
        [init @ .., last] => format!("{} and {last}", init.join(", ")),
    }
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(LIVE_INPUT_CODE, message)
}
