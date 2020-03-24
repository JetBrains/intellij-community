package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpaceReviewCommitListPanel(detailsDetailsVm: CrDetailsVm<out CodeReviewRecord>,
                                          repoInfo: Set<SpaceRepoInfo>) {
  val view: JPanel = JPanel(BorderLayout())

  init {
    val commitsBrowser = OnePixelSplitter(true, "space.review.commit.list", 0.7f).apply {
      firstComponent = SpaceReviewCommitListFactory.createCommitList(detailsDetailsVm)
      secondComponent = createChangeTree(detailsDetailsVm.ideaProject, detailsDetailsVm, repoInfo)
    }
    view.add(commitsBrowser, BorderLayout.CENTER)
  }

  private fun createChangeTree(project: Project,
                               detailsDetailsVm: CrDetailsVm<out CodeReviewRecord>,
                               repoInfo: Set<SpaceRepoInfo>): JComponent {

    val tree = object : ChangesTree(project, false, false) {
      init {
        if (detailsDetailsVm is MergeRequestDetailsVm) {

          detailsDetailsVm.changes.forEach(detailsDetailsVm.lifetime) { value ->

            val changes = value?.map { changeInReview ->
              changeInReview.change.changeType
              val new = changeInReview.change.new
              val old = changeInReview.change.old

              Change(
                if (old == null) null
                else GitContentRevision.createRevision(
                  VcsUtil.getFilePath(old.path),
                  GitRevisionNumber(old.commit),
                  project
                ),
                if (new == null) null
                else GitContentRevision.createRevision(
                  VcsUtil.getFilePath(new.path),
                  GitRevisionNumber(new.commit),
                  project
                )
              )
            } ?: emptyList()

            updateTreeModel(TreeModelBuilder(project, grouping)
                              .setChanges(changes, null).build())
            if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
          }
        }
      }

      override fun rebuildTree() {
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }
}
