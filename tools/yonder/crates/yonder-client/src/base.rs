//! The base sync of a run. A host whose master is behind the local one gets large run packs. So while a run follows its
//! command, a thread sends a thin pack of the local `origin/master`, or of `master`, to `refs/yonder/base/<holder>` on
//! the host. The agent keeps that ref, and the client records its commit in `bases/<alias>/<holder>`. The next run of
//! the checkout names that commit in its `Have`, so its pack holds only the commits after it.
//!
//! The sync goes at most once per run, and only after the upload of the run pack. It skips the pack when the host has
//! the commit already. It has the idle deadlines of the run pack. A failure says one warning line and never fails the
//! run. Ctrl+C stops it without a line: the run closes the latest connection of the sync, also while the host indexes
//! its pack.

use std::path::Path;

use yonder_link::proto::BASE_REFS;

use crate::git::base_commit;
use crate::host::Host;
use crate::pack::PackTarget;
use crate::{Client, Refusal, code};

/// What the base sync of a run needs.
pub(crate) struct BaseSync<'a> {
    pub(crate) checkout: &'a Path,
    pub(crate) repo: &'a str,
    pub(crate) holder: &'a str,
    /// The commit of the run. The host has it once the run pack is there.
    pub(crate) commit: &'a str,
    /// The base that the last sync of the holder recorded.
    pub(crate) recorded: Option<&'a str>,
}

impl Client<'_> {
    /// Syncs the base, records its commit, and says one warning line when that fails.
    pub(crate) fn sync_base(&self, host: &mut Host, sync: &BaseSync<'_>) {
        let alias = host.alias().to_owned();
        let synced = self
            .send_base(host, sync)
            .and_then(|base| base.map_or(Ok(()), |base| self.config.write_base(&alias, sync.holder, &base)));
        match synced {
            Ok(()) => {}
            // Ctrl+C closes the connection of the sync, so its failure is no news.
            Err(_) if self.interrupted() => {}
            Err(refusal) if refusal.code == code::INTERRUPTED => {}
            Err(refusal) => (self.say)(&format!(
                "yonder: warning: cannot sync the base of the next packs to {alias} ({}): {}. The next run can need a larger pack",
                refusal.code, refusal.message
            )),
        }
    }

    /// Sends the base when the host lacks it. Answers the commit of the base, or `None` when the checkout has no base.
    fn send_base(&self, host: &mut Host, sync: &BaseSync<'_>) -> Result<Option<String>, Refusal> {
        let Some(base) = base_commit(self.processes, sync.checkout, &|| self.interrupted()) else {
            return Ok(None);
        };
        if base == sync.commit {
            return Ok(Some(base));
        }
        let present = self.present(host, sync.repo, sync.checkout, &base, &[Some(sync.commit), sync.recorded])?;
        if present.contains(&base) {
            return Ok(Some(base));
        }
        let alias = host.alias().to_owned();
        let reference = format!("{BASE_REFS}{}", sync.holder);
        let target = PackTarget {
            checkout: sync.checkout,
            repo: sync.repo,
            commit: &base,
            reference: &reference,
        };
        // The lines of the command go on meanwhile, so the steps of the sync stay quiet.
        let plan = self.plan_pack(&alias, &target, &present)?;
        self.send_pack(host, &target, &plan, &|_| {})?;
        Ok(Some(base))
    }
}
