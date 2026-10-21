//! The restart of the held IDE on its own context, which the program runner asks for: `restart-ide` of a scenario, and
//! the recycle of an IDE whose reset did not hold. Also what the runner reads of the owner while a run goes on: the
//! held run, which the watchdog kills, and the lane services, which a host id steers.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, PoisonError};

use super::{IdeOwner, LaneLaunch, LaneServices, Records, refused};
use crate::lane::identity::{BRIDGE_PORT, BRIDGE_TOKEN_PURPOSE};
use crate::lane::inputs::TraceInputs;
use crate::lane::readiness::{self, Bridge};
use crate::lane::services::Service;
use crate::lane::services::cloud::Cloud;
use crate::lane::services::feedback::Feedback;
use crate::lane::services::registry::Registry;
use crate::reply::AgentRefusal;

/// The IDE run that a kill of the watchdog ends: its context directory, its run id, and the `jcmd` that dumps its
/// threads first.
pub(crate) type RunTarget = (PathBuf, String, Option<PathBuf>);

/// The run that the watchdog kills. The owner publishes it right after a launch returns, and clears it in a quit. The
/// watchdog reads it without the lock of the owner, so a kill during a relaunch reaches the run that the relaunch
/// launched, and never the run that it quit.
#[derive(Clone, Debug, Default)]
pub(crate) struct KillTarget(Arc<Mutex<Option<RunTarget>>>);

impl KillTarget {
    /// The run that a kill ends now, or `None` while no IDE run is held.
    pub(crate) fn current(&self) -> Option<RunTarget> {
        self.0.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub(super) fn publish(&self, target: Option<RunTarget>) {
        *self.0.lock().unwrap_or_else(PoisonError::into_inner) = target;
    }
}

/// Why the owner relaunches the IDE on its context: the base of the launch name, the line of the run log, and whether
/// the start restores the editor tabs of the previous run.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Relaunch {
    /// A scenario runs `restart-ide`.
    Restart,
    /// A reset did not hold. The decision of an iteration relaunches as a recycle too: for an IDE that exited, a bridge
    /// that does not answer, a change of the context plugins, a failed reload, and the lane IDE after a bare suite.
    Recycle,
}

impl Relaunch {
    /// The base of the launch name.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Restart => "restart",
            Self::Recycle => "recycle",
        }
    }

    /// Whether the start keeps the editor tabs that the previous run left in the context. A `restart-ide` tests what a
    /// restart of the IDE restores, so it keeps them. A recycle replaces an IDE whose state did not hold, or which a kill
    /// ended, so its start opens no tab of that run.
    pub(crate) const fn restores_editors(self) -> bool {
        match self {
            Self::Restart => true,
            Self::Recycle => false,
        }
    }

    const fn reason(self) -> &'static str {
        match self {
            Self::Restart => "the scenario runs restart-ide",
            Self::Recycle => "a reset did not hold",
        }
    }
}

impl IdeOwner {
    /// Quits the held IDE, performs `while_down` on its context directory, launches it again on the same context as
    /// `<cause>-<n>-<millis>`, and waits for readiness.
    ///
    /// The launch happens even when `while_down` fails, so the lane keeps an IDE. The failure of `while_down` is then the
    /// failure of the restart.
    pub(crate) fn restart(
        &mut self,
        launch: &LaneLaunch<'_>,
        cause: Relaunch,
        while_down: &mut dyn FnMut(&Path) -> Result<(), String>,
        records: &mut Records<'_>,
    ) -> Result<(), AgentRefusal> {
        let Some(mut previous) = self.held.clone() else {
            return Err(refused("a restart was asked for, and the owner holds no IDE"));
        };
        records.note(format!(
            "the IDE of '{}' relaunches on its context, because {}",
            launch.identity.key(),
            cause.reason()
        ));
        self.quit(records)?;
        let down = while_down(previous.context());
        // The quit reported the end of this run already, so the relaunch below reports no second end.
        previous.exit_reported = true;
        self.held = Some(previous);
        self.relaunch(launch, cause, records)?;
        let key = launch.identity.key();
        let bridge = Bridge::new(BRIDGE_PORT, &launch.identity.secret(BRIDGE_TOKEN_PURPOSE))?;
        readiness::await_ready(&bridge, key, launch.vocabulary_digest, &mut self.run_end(), &mut |text| {
            records.note(text);
        })?;
        down.map_err(|error| refused(format!("the file operations while the IDE of '{key}' was down failed: {error}")))
    }

    /// The run that a kill of the watchdog ends, as the owner publishes it.
    pub(crate) fn kill_target(&self) -> KillTarget {
        self.target.clone()
    }

    /// Publishes the held IDE run as the target of a kill, or no target when the owner holds no IDE.
    pub(super) fn publish_target(&self) {
        self.target.publish(
            self.held
                .as_ref()
                .map(|held| (held.context().to_path_buf(), held.run_id(), self.inputs.jcmd())),
        );
    }

    /// The context directory of the held IDE.
    pub(crate) fn context_dir(&self) -> Option<PathBuf> {
        self.held.as_ref().map(|held| held.context().to_path_buf())
    }

    /// The pid of the held IDE run, as its launch named it.
    pub(crate) fn pid(&self) -> Option<i32> {
        self.held.as_ref().and_then(|held| held.pid)
    }

    /// The recorder of the scenario traces, as the runtime descriptor names it.
    pub(crate) const fn trace_inputs(&self) -> &TraceInputs {
        &self.inputs.trace
    }

    /// The lane services of the current config key.
    pub(crate) const fn services(&self) -> Option<&LaneServices> {
        self.services.as_ref()
    }
}

impl LaneServices {
    pub(crate) fn registry(&self) -> Option<&Registry> {
        self.registry.as_ref().map(Service::handler)
    }

    pub(crate) fn feedback(&self) -> Option<&Feedback> {
        self.feedback.as_ref().map(Service::handler)
    }

    pub(crate) fn cloud(&self) -> &Cloud {
        self.cloud.handler()
    }
}
