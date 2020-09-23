package com.intellij.space.vcs.review.details

import circlet.client.api.GitCommitChangeType
import circlet.client.api.isDirectory
import circlet.code.api.ChangeInReview
import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JComponent

internal object SpaceReviewChangesTreeFactory {
  fun create(project: Project,
             detailsDetailsVm: CrDetailsVm<out CodeReviewRecord>): JComponent {

    val tree = object : ChangesTree(project, false, false) {
      init {
        when (detailsDetailsVm) {
          is MergeRequestDetailsVm -> {
            detailsDetailsVm.changes.forEach(detailsDetailsVm.lifetime) { changes ->
              val builder = TreeModelBuilder(project, grouping)
              val repoNode = RepositoryNode(detailsDetailsVm.repository.value, detailsDetailsVm.repoInfo != null)

              changes?.let { addChanges(builder, repoNode, it) }
              updateTreeModel(builder.build())

              if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
            }
          }
          is CommitSetReviewDetailsVm -> {
            detailsDetailsVm.changesByRepos.forEach(detailsDetailsVm.lifetime) { map ->
              val builder = TreeModelBuilder(project, grouping)

              map?.forEach { repoName, changes ->
                val repoNode = RepositoryNode(repoName, detailsDetailsVm.reposInCurrentProject.value?.get(repoName) != null)
                addChanges(builder, repoNode, changes)
              }

              updateTreeModel(builder.build())

              if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
            }
          }
        }
      }

      override fun rebuildTree() {
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  private fun addChanges(builder: TreeModelBuilder,
                         repositoryNode: ChangesBrowserNode<*>,
                         changesInReview: List<ChangeInReview>) {
    builder.insertSubtreeRoot(repositoryNode)

    changesInReview.forEach { changeInReview: ChangeInReview ->
      val filePath = getFilePath(changeInReview)
      builder.insertChangeNode(
        filePath,
        repositoryNode,
        ReviewChangeNode(changeInReview)
      )
    }
  }
}

private val addedLinesTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.ADDED.color)
private val removedLinesTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.DELETED.color)

internal class RepositoryNode(@NlsSafe val repositoryName: String, val inCurrentProject: Boolean) : ChangesBrowserNode<String>(
  repositoryName) {
  init {
    markAsHelperNode()
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val style = if (inCurrentProject) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
    renderer.append(repositoryName, style)
  }
}

internal class ReviewChangeNode(private val changeInReview: ChangeInReview)
  : AbstractChangesBrowserFilePathNode<ChangeInReview>(changeInReview, getFileStatus(changeInReview)) {

  private val filePath: FilePath = getFilePath(changeInReview)

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    changeInReview.change.diffSize?.let { diffSize ->
      val (added, removed) = diffSize
      if (added != 0) renderer.append("${FontUtil.spaceAndThinSpace()}+$added", addedLinesTextAttributes)
      if (removed != 0) renderer.append("${FontUtil.spaceAndThinSpace()}-${removed}", removedLinesTextAttributes)
    }
  }

  override fun filePath(userObject: ChangeInReview): FilePath = filePath
}

private fun getFileStatus(changeInReview: ChangeInReview): FileStatus = when (changeInReview.change.changeType) {
  GitCommitChangeType.ADDED -> FileStatus.ADDED
  GitCommitChangeType.DELETED -> FileStatus.DELETED
  GitCommitChangeType.MODIFIED -> FileStatus.MODIFIED
}

private fun getFilePath(changeInReview: ChangeInReview): FilePath {
  val path = when (changeInReview.change.changeType) {
    GitCommitChangeType.ADDED, GitCommitChangeType.MODIFIED -> changeInReview.change.new!!.path
    GitCommitChangeType.DELETED -> changeInReview.change.old!!.path
  }.trimStart('/', '\\')

  val isDirectory = when (changeInReview.change.changeType) {
    GitCommitChangeType.ADDED, GitCommitChangeType.MODIFIED -> changeInReview.change.new!!.isDirectory()
    GitCommitChangeType.DELETED -> changeInReview.change.old!!.isDirectory()
  }
  return RemoteFilePath(path, isDirectory)
}
