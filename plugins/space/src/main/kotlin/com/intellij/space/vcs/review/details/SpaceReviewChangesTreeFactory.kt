// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.ChangeInReview
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.space.vcs.review.details.diff.SpaceDiffVm
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nullable
import runtime.reactive.Property
import javax.swing.JComponent

internal object SpaceReviewChangesTreeFactory {
  fun create(project: Project,
             parentPanel: JComponent,
             changesVm: SpaceReviewChangesVm,
             spaceDiffVm: Property<SpaceDiffVm>): JComponent {

    val tree = object : ChangesTree(project, false, false) {
      init {
        changesVm.changes.forEach(changesVm.lifetime) {
          it ?: return@forEach
          val builder = TreeModelBuilder(project, grouping)

          it.forEach { (repo, changesWithDiscussion) ->
            val spaceRepoInfo = changesWithDiscussion.spaceRepoInfo
            val repoNode = SpaceRepositoryNode(repo, spaceRepoInfo != null)

            val changes = changesWithDiscussion.changesInReview
            addChanges(builder, repoNode, changes, spaceRepoInfo)
            updateTreeModel(builder.build())

            if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
          }
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

      val spaceDiffFile = SpaceDiffFile(spaceDiffVm.value, changesVm)
      FileEditorManager.getInstance(project).openFile(spaceDiffFile, true)
      true
    }

    tree.addSelectionListener {
      val selection = VcsTreeModelData.getListSelectionOrAll(tree).map { it as? SpaceReviewChange }
      // do not reset selection to zero
      if (!selection.isEmpty) changesVm.listSelection.value = selection
    }
    DataManager.registerDataProvider(parentPanel) {
      if (tree.isShowing) tree.getData(it) else null
    }
    tree.installPopupHandler(ActionManager.getInstance().getAction("space.review.changes.popup") as ActionGroup)
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  private fun addChanges(builder: TreeModelBuilder,
                         repositoryNode: ChangesBrowserNode<*>,
                         changesInReview: List<ChangeInReview>,
                         spaceRepoInfo: SpaceRepoInfo?) {
    builder.insertSubtreeRoot(repositoryNode)

    changesInReview.forEach { changeInReview: ChangeInReview ->
      val spaceChange = SpaceReviewChange(changeInReview, spaceRepoInfo)
      builder.insertChangeNode(
        spaceChange.filePath,
        repositoryNode,
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

internal class SpaceReviewChangeNode(spaceReviewChange: SpaceReviewChange)
  : AbstractChangesBrowserFilePathNode<SpaceReviewChange>(spaceReviewChange,
                                                          spaceReviewChange.fileStatus) {

  override fun filePath(userObject: SpaceReviewChange): FilePath = userObject.filePath
}