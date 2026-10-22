//! The recorded sessions of a lane daemon run on the host, as [`avl_wire::session_log`] states them: the logs that its
//! variable names, packed into a private directory for the push, and the names that the run request gives them.
//!
//! The lane IDE of a guest cannot read a file of the host, so each log travels as one zip by digest, as a lane input
//! does. The zips leave the host directory when the push is over. The lane daemon removes its copies at the end of the
//! run.

use std::path::Path;

use avl_base::{Environment, Exit, Refusal};
use avl_report::digest::{PathDigest, sha256_file};
use avl_wire::daemon::SessionLog;
use avl_wire::session_log::{self, VARIABLE};
use tempfile::TempDir;

#[cfg(test)]
mod tests;

/// The packed logs of one iteration: the files of the push and their names in the run request. The files leave the
/// host when this value is dropped.
#[derive(Debug, Default)]
pub(crate) struct Packed {
    directory: Option<TempDir>,
    pub(crate) files: Vec<PathDigest>,
    pub(crate) logs: Vec<SessionLog>,
}

impl Packed {
    /// The names of the run request, and the end of the files.
    pub(crate) fn into_logs(mut self) -> Vec<SessionLog> {
        self.directory = None;
        std::mem::take(&mut self.logs)
    }
}

/// Packs each log that [`VARIABLE`] of `environment` names into a private directory under `runtime_root`. A variable
/// that names no events file, or two logs of one label, is refused.
pub(crate) fn pack(environment: &Environment, runtime_root: &Path) -> Result<Packed, Refusal> {
    let Some(value) = environment.get(VARIABLE) else {
        return Ok(Packed::default());
    };
    let logs = session_log::named_logs(value).map_err(|message| Refusal::new("session_log_invalid", Exit::USAGE, message))?;
    if logs.is_empty() {
        return Ok(Packed::default());
    }
    let failed = |message: String| Refusal::new("session_log_pack_failed", Exit::SOFTWARE, message);
    let directory = tempfile::Builder::new()
        .prefix("session-logs-")
        .tempdir_in(runtime_root)
        .map_err(|error| failed(format!("cannot create a directory under {}: {error}", runtime_root.display())))?;
    let mut packed = Packed::default();
    for log in &logs {
        let zip = directory.path().join(format!("{}.zip", log.label));
        session_log::pack(log, &zip).map_err(failed)?;
        let sha256 = sha256_file(&zip).map_err(|error| failed(format!("cannot read {}: {error}", zip.display())))?;
        packed.files.push(PathDigest::new(zip.display().to_string(), sha256.clone()));
        packed.logs.push(SessionLog {
            label: log.label.clone(),
            sha256,
        });
    }
    packed.directory = Some(directory);
    Ok(packed)
}
