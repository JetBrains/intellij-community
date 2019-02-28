// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import org.jetbrains.idea.svn.ConflictedSvnChange
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Depth
import java.util.*

class MarkTreeConflictResolvedAction : AnAction(myText), DumbAware {

  override fun update(e: AnActionEvent) {
    val checker = MyChecker(e)
    e.presentation.isVisible = checker.isEnabled
    e.presentation.isEnabled = checker.isEnabled
    e.presentation.text = myText
  }

  private class MyChecker internal constructor(e: AnActionEvent) {
    var isEnabled: Boolean
    val change: ConflictedSvnChange?
    val project: Project?

    init {
      val dc = e.dataContext
      project = CommonDataKeys.PROJECT.getData(dc)
      val changes = VcsDataKeys.CHANGE_LEAD_SELECTION.getData(dc)

      if (project == null || changes == null || changes.size != 1) {
        isEnabled = false
        change = null
      }
      else {
        val change = changes[0]
        isEnabled = change is ConflictedSvnChange && change.conflictState.isTree
        if (isEnabled) {
          this.change = change as ConflictedSvnChange
        }
        else {
          this.change = null
        }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val checker = MyChecker(e)
    if (!checker.isEnabled) return

    val markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title")
    val result = Messages.showYesNoDialog(checker.project,
                                          SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                          Messages.getQuestionIcon())
    if (result == Messages.YES) {
      val exception = Ref<VcsException>()
      ProgressManager.getInstance().run(object : Task.Backgroundable(checker.project, markText, true) {
        override fun run(indicator: ProgressIndicator) {
          val change = checker.change
          val path = change!!.treeConflictMarkHolder
          val vcs = SvnVcs.getInstance(checker.project!!)

          try {
            vcs.getFactory(path.ioFile).createConflictClient().resolve(path.ioFile, Depth.EMPTY, false, false, true)
          }
          catch (e: VcsException) {
            exception.set(e)
          }

          VcsDirtyScopeManager.getInstance(checker.project).filePathsDirty(getDistinctFiles(change), null)
        }
      })
      if (!exception.isNull) {
        AbstractVcsHelper.getInstance(checker.project).showError(exception.get(), markText)
      }
    }
  }

  private fun getDistinctFiles(change: Change): Collection<FilePath> {
    val result = ArrayList<FilePath>(2)
    if (change.beforeRevision != null) {
      result.add(change.beforeRevision!!.file)
    }
    if (change.afterRevision != null) {
      if (change.beforeRevision == null || change.beforeRevision != null && (change.isMoved || change.isRenamed)) {
        result.add(change.afterRevision!!.file)
      }
    }
    return result
  }

  companion object {
    private val myText = SvnBundle.message("action.mark.tree.conflict.resolved.text")
  }
}
