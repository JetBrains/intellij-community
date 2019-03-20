// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.DefaultCommitResultHandler
import com.intellij.openapi.vcs.changes.ui.DialogCommitWorkflow

class AlienCommitWorkflow(val vcs: AbstractVcs<*>, changeListName: String, changes: List<Change>, commitMessage: String?) :
  DialogCommitWorkflow(vcs.project, changes, vcsToCommit = vcs, initialCommitMessage = commitMessage) {
  val changeList = AlienLocalChangeList(changes, changeListName)

  override fun doRunBeforeCommitChecks(changeList: LocalChangeList, checks: Runnable) = checks.run()

  override fun canExecute(executor: CommitExecutor, changes: Collection<Change>) = true

  override fun doCommit(changeList: LocalChangeList, changes: List<Change>, commitMessage: String) {
    val committer = AlienCommitter(vcs, changes, commitMessage, commitHandlers, additionalData)

    committer.addResultHandler(DefaultCommitResultHandler(committer))
    committer.runCommit(DIALOG_TITLE, false)
  }
}