// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.vcs.commit.*
import org.jetbrains.annotations.Nls

class AlienCommitWorkflow(val vcs: AbstractVcs, @Nls changeListName: String, val changes: List<Change>, commitMessage: String?) :
  CommitChangeListDialogWorkflow(vcs.project, initialCommitMessage = commitMessage) {

  init {
    updateVcses(setOf(vcs))
  }

  val changeList = AlienLocalChangeList(changes, changeListName)

  override val isDefaultCommitEnabled: Boolean = true
  override val isPartialCommitEnabled: Boolean = false

  override fun canExecute(sessionInfo: CommitSessionInfo, changes: Collection<Change>) = sessionInfo.isVcsCommit

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    DefaultNameChangeListCleaner(project, commitState).use { doCommit(commitState) }
  }

  private fun doCommit(commitState: ChangeListCommitState) {
    with(AlienCommitter(vcs, commitState.changes, commitState.commitMessage, commitContext)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(ShowNotificationCommitResultHandler(this))
      addResultHandler(getEndExecutionHandler())

      runCommit(DIALOG_TITLE, false)
    }
  }
}