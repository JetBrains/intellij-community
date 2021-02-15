// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.FileHistoryPanelImpl
import com.intellij.openapi.vcs.history.FileHistoryRefresherI
import com.intellij.openapi.vcs.history.VcsAppendableHistoryPartnerAdapter
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.ui.JBColor
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.idea.svn.SvnRevisionNumber
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Revision
import javax.swing.BorderFactory.createLineBorder
import javax.swing.JPanel

private val VcsFileRevision.svnRevision: Revision
  get() = (revisionNumber as SvnRevisionNumber).revision

private const val HISTORY_LIMIT = 10

internal class HistoryConflictSide(
  private val vcs: SvnVcs,
  private val path: FilePath,
  private val from: Revision,
  private val to: Revision?
) : ConflictSidePresentation {

  private val sessionAdapter = VcsAppendableHistoryPartnerAdapter()
  private val provider = vcs.vcsHistoryProvider

  var listToReportLoaded: LongArrayList? = null

  @Throws(VcsException::class)
  override fun load() {
    provider.reportAppendableHistory(path, sessionAdapter, from, to, if (to == null) HISTORY_LIMIT else 0, to, true)

    val session = sessionAdapter.session ?: return
    if (listToReportLoaded == null) return

    for (revision in session.revisionList) {
      listToReportLoaded!!.add(revision.svnRevision.number)
    }
  }

  override fun createPanel(): JPanel? {
    val session = sessionAdapter.session ?: return null
    val revisions = session.revisionList.ifEmpty { return null }
    val lastRevision =
      if (to == null && revisions.size == HISTORY_LIMIT ||
          to != null && to.number > 0 && to == revisions.last().svnRevision) {
        revisions.removeAt(revisions.lastIndex)
      }
      else {
        null
      }

    val panel = FileHistoryPanelImpl(
      vcs, path, session, provider,
      object : FileHistoryRefresherI {
        override fun isInRefresh(): Boolean = false
        override fun refresh(canUseCache: Boolean) = Unit
        override fun selectContent() = Unit
      },
      true
    )
    panel.setBottomRevisionForShowDiff(lastRevision)
    panel.border = createLineBorder(JBColor.border())

    Disposer.register(this, panel)
    return panel
  }

  override fun dispose() = Unit
}