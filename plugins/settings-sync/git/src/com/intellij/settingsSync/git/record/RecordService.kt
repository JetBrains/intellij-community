package com.intellij.settingsSync.git.record

import com.intellij.vcs.log.VcsFullCommitDetails

/**
 * In future this service should do some smart things with history records,
 * but at the moment only ChangeRecord are supported, so implementation is trivial
 */
internal class RecordService {
  fun readRecord(commit: VcsFullCommitDetails, isFirst: Boolean, isLast: Boolean, commits: List<VcsFullCommitDetails>): HistoryRecord {
    // TODO merge records
    return ChangeRecord(commit, isFirst, isLast, commits)
  }
}