// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser
import com.intellij.vcs.commit.SingleChangeListCommitWorkflowUi

class AlienCommitChangeListDialog(workflow: AlienCommitWorkflow, changeList: AlienLocalChangeList) : CommitChangeListDialog(workflow) {
  private val browser = AlienChangeListBrowser(project, changeList)

  init {
    browser.viewer.setIncludedChanges(changeList.changes)
    browser.viewer.rebuildTree()
  }

  override fun getBrowser(): CommitDialogChangesBrowser = browser

  override fun addChangeListListener(listener: SingleChangeListCommitWorkflowUi.ChangeListListener, parent: Disposable) = Unit
}