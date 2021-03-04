// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.ChangeInReview
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.FontUtil
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.util.VcsLogUtil
import libraries.klogging.logger
import org.jetbrains.annotations.Nullable
import runtime.reactive.LoadingValue
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.event.TreeSelectionListener

internal interface SpaceDiffFileProvider {
  fun getSpaceDiffFile(): SpaceDiffFile
}

internal object SpaceReviewChangesTreeFactory {
  private val LOG = logger<SpaceReviewChangesTreeFactory>()

  fun create(
    project: Project,
    parentDisposable: Disposable,
    parentPanel: JComponent,
    changesVm: SpaceReviewChangesVm,
    spaceDiffFileProvider: SpaceDiffFileProvider
  ): JComponent {
    val loadingPane = JBLoadingPanel(BorderLayout(), parentDisposable, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    val tree = object : ChangesTree(project, false, false) {


      init {
        val listener = TreeSelectionListener {
          val selection = VcsTreeModelData.getListSelectionOrAll(this).map { it as? SpaceReviewChange }
          // do not reset selection to zero
          if (!selection.isEmpty) changesVm.selectedChanges.value = selection
        }

        changesVm.changes.forEach(changesVm.lifetime) {
          removeTreeSelectionListener(listener)
          val builder = TreeModelBuilder(project, grouping)
          when (it) {
            is LoadingValue.Loaded -> {
              loadingPane.stopLoading()

              val commits = changesVm.allCommits.value
              val repositories = it.value.toList()
              val singleRepoChanges = repositories.singleOrNull()
              if (singleRepoChanges != null && singleRepoChanges.second.spaceRepoInfo != null) {
                // don't show repo node for single repo review
                val changesWithDiscussion = singleRepoChanges.second
                val changes = changesWithDiscussion.changesInReview
                val spaceRepoInfo = changesWithDiscussion.spaceRepoInfo
                addChanges(builder, builder.myRoot, changes, spaceRepoInfo, commits)
              }
              else {
                repositories.forEach { (repo, changesWithDiscussion) ->
                  val spaceRepoInfo = changesWithDiscussion.spaceRepoInfo
                  val repoNode = SpaceRepositoryNode(repo, spaceRepoInfo != null)
                  val changes = changesWithDiscussion.changesInReview
                  builder.insertSubtreeRoot(repoNode)
                  addChanges(builder, repoNode, changes, spaceRepoInfo, commits)
                }
              }

              addTreeSelectionListener(listener)
            }
            LoadingValue.Loading -> {
              loadingPane.startLoading()
              setEmptyText("")
            }
            is LoadingValue.Failure -> {
              loadingPane.stopLoading()
              LOG.info(it.error) { "Could not load changes for selected commits" }
              setEmptyText(SpaceBundle.message("review.changes.browser.failure.text"))
            }
          }

          updateTreeModel(builder.build())
          if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
        }
      }

      override fun rebuildTree() {
      }

      override fun getData(dataId: String): @Nullable Any? {
        return when {
          CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> {
            VcsTreeModelData.selected(this)
              .userObjects(SpaceReviewChange::class.java)
              .mapNotNull { reviewChangeNode -> reviewChangeNode.filePath.virtualFile }
              .map { OpenFileDescriptor(project, it) }
              .toTypedArray()
          }

          else -> super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)
        }
      }
    }
    tree.doubleClickHandler = Processor { e ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
      SpaceStatsCounterCollector.OPEN_REVIEW_DIFF.log(SpaceStatsCounterCollector.ReviewDiffPlace.EDITOR)
      val spaceDiffFile = spaceDiffFileProvider.getSpaceDiffFile()
      VcsEditorTabFilesManager.getInstance().openFile(project, spaceDiffFile, true)
      true
    }

    DataManager.registerDataProvider(parentPanel) {
      if (tree.isShowing) tree.getData(it) else null
    }
    tree.installPopupHandler(ActionManager.getInstance().getAction("space.review.changes.popup") as ActionGroup)
    return loadingPane.apply {
      add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    }
  }

  private fun addChanges(builder: TreeModelBuilder,
                         subtreeRoot: ChangesBrowserNode<*>,
                         changesInReview: List<ChangeInReview>,
                         spaceRepoInfo: SpaceRepoInfo?,
                         commits: List<SpaceReviewCommitListItem>) {
    val hashes = commits.filterNot { it.commitWithGraph.unreachable }
      .map { it.commitWithGraph.commit.id }
      .toSet()

    changesInReview.forEach { changeInReview: ChangeInReview ->
      val spaceChange = SpaceReviewChange(changeInReview, spaceRepoInfo, !hashes.contains(changeInReview.change.revision))
      builder.insertChangeNode(
        spaceChange.filePath,
        subtreeRoot,
        SpaceReviewChangeNode(spaceChange)
      )
    }
  }
}

internal class SpaceRepositoryNode(@NlsSafe val repositoryName: String,
                                   private val inCurrentProject: Boolean)
  : ChangesBrowserStringNode(repositoryName) {
  init {
    markAsHelperNode()
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val style = if (inCurrentProject) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
    renderer.append(repositoryName, style)
  }
}

internal class SpaceReviewChangeNode(private val spaceReviewChange: SpaceReviewChange)
  : AbstractChangesBrowserFilePathNode<SpaceReviewChange>(spaceReviewChange,
                                                          spaceReviewChange.fileStatus) {

  override fun filePath(userObject: SpaceReviewChange): FilePath = userObject.filePath

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)

    if (spaceReviewChange.unreachable) {
      @NlsSafe val hash = spaceReviewChange.gitCommitChange.revision.take(VcsLogUtil.SHORT_HASH_LENGTH)
      renderer.append(FontUtil.spaceAndThinSpace())
      renderer.append("[$hash]")
    }
  }
}

internal fun createChangesBrowserToolbar(target: JComponent): TreeActionsToolbarPanel {
  val actionManager = ActionManager.getInstance()
  val changesToolbarActionGroup = actionManager.getAction("space.review.changes.toolbar") as ActionGroup
  val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
  val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                            actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
  return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
}