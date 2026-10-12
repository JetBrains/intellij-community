//! TCC admission: a macOS worker must hold no sensitive privacy decision.

use std::sync::LazyLock;

use avl_base::format::words;
use avl_base::{Exit, Refusal};

use super::{GUEST_COMMAND_TIMEOUT, Guest};
use crate::proc::SpawnOptions;

#[cfg(test)]
mod tests;

/// The privacy services a worker must hold nothing for.
///
/// Not "the interesting ones": these are the services whose grant lets a program read or drive something outside
/// its own sandbox, and a lane run has no business holding any of them. A worker that does is a worker whose image
/// was booted and used by a human, which is the state golden-image provenance exists to prevent.
pub const SENSITIVE_TCC_SERVICES: &[&str] = &[
    "kTCCServiceAccessibility",
    "kTCCServiceAddressBook",
    "kTCCServiceAppleEvents",
    "kTCCServiceBluetoothAlways",
    "kTCCServiceCalendar",
    "kTCCServiceCamera",
    "kTCCServiceListenEvent",
    "kTCCServiceMediaLibrary",
    "kTCCServiceMicrophone",
    "kTCCServiceMotion",
    "kTCCServicePhotos",
    "kTCCServicePostEvent",
    "kTCCServiceScreenCapture",
    "kTCCServiceSpeechRecognition",
    "kTCCServiceSystemPolicyAllFiles",
    "kTCCServiceSystemPolicyDesktopFolder",
    "kTCCServiceSystemPolicyDocumentsFolder",
    "kTCCServiceSystemPolicyDownloadsFolder",
    "kTCCServiceSystemPolicyNetworkVolumes",
    "kTCCServiceSystemPolicyRemovableVolumes",
];

/// What admission counts as a sensitive TCC decision on a *running* worker: any access row for a sensitive service
/// that is not an explicit denial, plus every policy row.
///
/// `auth_value = 0` is TCC's "denied", and macOS records one on its own whenever a program asks for a permission it
/// does not have - `tart-guest-agent` requests Accessibility on every boot and is refused, which writes exactly such
/// a row. Counting it would hold no capability yet fail admission forever. Everything that does confer something -
/// allowed, limited, and the unknown state - still fails, as does any policy row.
///
/// The offline golden-image audit stays stricter and counts every row: an image that was never booted has no
/// reason to carry a decision of any kind.
pub static TCC_DECISION_QUERY: LazyLock<String> = LazyLock::new(|| {
    let quoted = SENSITIVE_TCC_SERVICES
        .iter()
        .map(|service| format!("'{service}'"))
        .collect::<Vec<_>>()
        .join(",");
    format!(
        "SELECT\n  (SELECT count(*) FROM access WHERE service IN ({quoted}) AND auth_value != 0)\n  + (SELECT \
         count(*) FROM active_policy)\n  + (SELECT count(*) FROM policies)\n  + (SELECT count(*) FROM \
         access_overrides);"
    )
});

/// The one refusal every way of failing admission answers.
///
/// Deliberately one message for a dozen causes. Admission is a gate, not a diagnosis: a caller must not be able to
/// branch on *why* a worker was refused, because the only correct response to any of them is to stop using that
/// worker. The evidence - which database, which count - would also be the exact thing an attacker would tune
/// against.
pub fn worker_tcc_admission_error(worker: &str) -> Refusal {
    Refusal::new(
        "worker_tcc_admission_failed",
        Exit::DATA_ERR,
        format!("worker {worker} did not pass fail-closed TCC admission"),
    )
}

impl Guest<'_> {
    /// Refuses a macOS worker holding any sensitive privacy decision.
    ///
    /// Fail-closed in every direction: a database that cannot be read, a query that cannot be run, a count that is
    /// not a number, output that hit the capture limit, and a nonzero count are all the same refusal. A gate that
    /// warns is not a gate.
    ///
    /// macOS-only, and the caller's job to know it: a Linux guest has no TCC, so both databases are absent there and
    /// this refuses. That is the right direction to be wrong in, but it is not a diagnosis.
    pub async fn require_clean_worker_tcc(&self) -> Result<(), Refusal> {
        let worker = self.worker();
        let databases = [
            "/Library/Application Support/com.apple.TCC/TCC.db".to_owned(),
            format!("/Users/{}/Library/Application Support/com.apple.TCC/TCC.db", self.settings.vm_user),
        ];
        for database in databases {
            if !self.clean_tcc_database(&database).await {
                return Err(worker_tcc_admission_error(worker));
            }
        }
        Ok(())
    }

    /// Whether one TCC database answers a zero count, with every other outcome - including not knowing - `false`.
    async fn clean_tcc_database(&self, database: &str) -> bool {
        let options = SpawnOptions::within(GUEST_COMMAND_TIMEOUT);
        let present = self
            .channel
            .exec(self.ctx, &words(["/usr/bin/sudo", "-H", "/bin/test", "-f", database]), &options)
            .await;
        if !present.is_ok_and(|present| present.exit_code == 0) {
            return false;
        }
        // `-readonly` *and* `mode=ro` in the URI: the first refuses a write, the second keeps sqlite from creating
        // the `-wal` and `-shm` sidecars beside a database this controller must not modify at all.
        let uri = format!("file:{database}?mode=ro");
        let query = self
            .channel
            .exec(
                self.ctx,
                &words([
                    "/usr/bin/sudo",
                    "-H",
                    "/usr/bin/sqlite3",
                    "-readonly",
                    "-cmd",
                    ".timeout 5000",
                    &uri,
                    TCC_DECISION_QUERY.as_str(),
                ]),
                &options,
            )
            .await;
        let Ok(query) = query else {
            return false;
        };
        if query.exit_code != 0 || query.stdout_truncated || query.stderr_truncated {
            return false;
        }
        let raw = query.stdout.trim();
        // The shape before the parse, so a count too large for an integer is a refusal rather than a saturation.
        !raw.is_empty() && raw.bytes().all(|byte| byte.is_ascii_digit()) && raw.parse::<i64>() == Ok(0)
    }
}
