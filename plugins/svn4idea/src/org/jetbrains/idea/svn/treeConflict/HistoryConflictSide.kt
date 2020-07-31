// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.FileHistoryPanelImpl
import com.intellij.openapi.vcs.history.FileHistoryRefresherI
import com.intellij.openapi.vcs.history.VcsAppendableHistoryPartnerAdapter
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.ui.JBColor
import com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal
import gnu.trove.TLongArrayList
import org.jetbrains.idea.svn.SvnRevisionNumber
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.conflict.ConflictVersion
import javax.swing.BorderFactory
import javax.swing.JPanel

private const val HISTORY_LIMIT = 10

internal class HistoryConflictSide(
  private val vcs: SvnVcs,
  private val version: ConflictVersion,
  private val peg: Revision?
) : ConflictSidePresentation {

  private val sessionAdapter = VcsAppendableHistoryPartnerAdapter()
  private val provider = vcs.vcsHistoryProvider
  private val path = getFilePathOnNonLocal(version.repositoryRoot.appendPath(version.path, false).toDecodedString(), version.isDirectory)
  private var fileHistoryPanel: FileHistoryPanelImpl? = null

  var listToReportLoaded: TLongArrayList? = null

  @Throws(VcsException::class)
  override fun load() {
    val from = Revision.of(version.pegRevision)
    provider.reportAppendableHistory(path, sessionAdapter, from, peg, if (peg == null) HISTORY_LIMIT else 0, peg, true)
    val session = sessionAdapter.session
    if (listToReportLoaded != null && session != null) {
      val list = session.revisionList
      for (revision in list) {
        listToReportLoaded!!.add((revision.revisionNumber as SvnRevisionNumber).revision.number)
      }
    }
  }

  override fun dispose() {
    if (fileHistoryPanel != null) {
      Disposer.dispose(fileHistoryPanel!!)
    }
  }

  override fun createPanel(): JPanel? {
    val session = sessionAdapter.session ?: return EmptyConflictSide.createPanel()
    val list = session.revisionList
    if (list.isEmpty()) {
      return EmptyConflictSide.createPanel()
    }
    var last: VcsFileRevision? = null
    if (peg == null && list.size == HISTORY_LIMIT ||
        peg != null && peg.number > 0 &&
        peg == (list[list.size - 1].revisionNumber as SvnRevisionNumber).revision) {
      last = list.removeAt(list.size - 1)
    }
    fileHistoryPanel = FileHistoryPanelImpl(vcs, path, session, provider, object : FileHistoryRefresherI {
      override fun refresh(canUseCache: Boolean) {
        //we will not refresh
      }

      override fun selectContent() {}
      override fun isInRefresh(): Boolean {
        return false
      }
    }, true)
    fileHistoryPanel!!.setBottomRevisionForShowDiff(last)
    fileHistoryPanel!!.border = BorderFactory.createLineBorder(JBColor.border())
    return fileHistoryPanel
  }
}