// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnLocallyDeletedChange
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Depth

/**
 * @author irengrig
 */
class MarkLocallyDeletedTreeConflictResolvedAction : DumbAwareAction(SvnBundle.message("action.Subversion.MarkTreeResolved.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    val locallyDeletedChecker = MyLocallyDeletedChecker(e)
    if (!locallyDeletedChecker.isEnabled) return

    val markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title")
    val project = locallyDeletedChecker.project
    val result = Messages.showYesNoDialog(project,
                                          SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                          Messages.getQuestionIcon())
    if (result == Messages.YES) {
      val exception = Ref<VcsException>()
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, markText, true) {
        override fun run(indicator: ProgressIndicator) {
          resolveLocallyDeletedTextConflict(locallyDeletedChecker, exception)
        }
      })
      if (!exception.isNull) {
        AbstractVcsHelper.getInstance(project).showError(exception.get(), markText)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val locallyDeletedChecker = MyLocallyDeletedChecker(e)
    e.presentation.isVisible = locallyDeletedChecker.isEnabled
    e.presentation.isEnabled = locallyDeletedChecker.isEnabled
  }

  private fun resolveLocallyDeletedTextConflict(checker: MyLocallyDeletedChecker, exception: Ref<VcsException>) {
    val path = checker.path
    resolve(checker.project, exception, path!!)
    VcsDirtyScopeManager.getInstance(checker.project!!).filePathsDirty(listOf(path), null)
  }

  private fun resolve(project: Project?, exception: Ref<VcsException>, path: FilePath) {
    val vcs = SvnVcs.getInstance(project!!)

    try {
      vcs.getFactory(path.ioFile).createConflictClient().resolve(path.ioFile, Depth.EMPTY, false, false, true)
    }
    catch (e: VcsException) {
      exception.set(e)
    }

  }

  private class MyLocallyDeletedChecker internal constructor(e: AnActionEvent) {
    var isEnabled: Boolean
    var path: FilePath?
    val project: Project?

    init {
      val dc = e.dataContext
      project = CommonDataKeys.PROJECT.getData(dc)
      if (project == null) {
        path = null
        isEnabled = false
      }
      else {
        val missingFiles = e.getData(ChangesListView.LOCALLY_DELETED_CHANGES)

        if (missingFiles == null || missingFiles.isEmpty()) {
          path = null
          isEnabled = false
        }
        else {
          /*if (missingFiles == null || missingFiles.size() != 1) {
        final Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || changes.length != 1 || changes[0].getAfterRevision() != null) {
          myPath = null;
          myEnabled = false;
          return;
        }
        myEnabled = changes[0] instanceof ConflictedSvnChange && ((ConflictedSvnChange) changes[0]).getConflictState().isTree();
        if (myEnabled) {
          myPath = changes[0].getBeforeRevision().getFile();
        } else {
          myPath = null;
        }
        return;
      } */

          val change = missingFiles[0]
          isEnabled = change is SvnLocallyDeletedChange && change.conflictState.isTree
          if (isEnabled) {
            path = change.path
          }
          else {
            path = null
          }
        }
      }
    }
  }
}
