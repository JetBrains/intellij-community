// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.vcs.commit.CommitChangeListDialogWorkflow
import com.intellij.vcs.commit.CommitSessionInfo
import com.intellij.vcs.commit.ShowNotificationCommitResultHandler

class AlienCommitWorkflow(val vcs: AbstractVcs) :
  CommitChangeListDialogWorkflow(vcs.project) {

  init {
    updateVcses(setOf(vcs))
  }

  override val isDefaultCommitEnabled: Boolean = true
  override val isPartialCommitEnabled: Boolean = false

  override fun canExecute(sessionInfo: CommitSessionInfo, changes: Collection<Change>) = sessionInfo.isVcsCommit

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    val committer = AlienCommitter(vcs, commitState.changes, commitState.commitMessage, commitContext)
    addCommonResultHandlers(sessionInfo, committer)
    committer.addResultHandler(ShowNotificationCommitResultHandler(committer))

    committer.runCommit(DIALOG_TITLE, false)
  }
}