// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.vcs.commit.ChangeListCommitState
import com.intellij.vcs.commit.CommitHandlersNotifier
import com.intellij.vcs.commit.ShowNotificationCommitResultHandler
import com.intellij.vcs.commit.SingleChangeListCommitWorkflow

class AlienCommitWorkflow(val vcs: AbstractVcs, changeListName: String, changes: List<Change>, commitMessage: String?) :
  SingleChangeListCommitWorkflow(vcs.project, changes, vcsToCommit = vcs, initialCommitMessage = commitMessage) {
  val changeList = AlienLocalChangeList(changes, changeListName)

  override fun doRunBeforeCommitChecks(checks: Runnable) = checks.run()

  override fun canExecute(executor: CommitExecutor, changes: Collection<Change>) = true

  override fun doCommit(commitState: ChangeListCommitState) {
    with(AlienCommitter(vcs, commitState.changes, commitState.commitMessage, commitContext)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(ShowNotificationCommitResultHandler(this))
      addResultHandler(getEndExecutionHandler())

      runCommit(DIALOG_TITLE, false)
    }
  }
}