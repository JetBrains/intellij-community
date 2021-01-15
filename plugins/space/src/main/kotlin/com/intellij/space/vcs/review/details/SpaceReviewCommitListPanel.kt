// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import javax.swing.JComponent

internal class SpaceReviewCommitListPanel(parentDisposable: Disposable,
                                          reviewDetailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>) : BorderLayoutPanel() {
  init {
    val commitsBrowser = OnePixelSplitter(true, "space.review.commit.list", 0.4f).apply {
      val changesVm = reviewDetailsVm.commitChangesVm
      val diffVm = reviewDetailsVm.spaceDiffVm

      val selectedCommitDetails = OnePixelSplitter(true, "space.review.commit.list.details", 0.3f).apply {
        val commitDetailsPanel = createCommitsDetailsPanel(parentDisposable, reviewDetailsVm.ideaProject, changesVm)

        val tree = SpaceReviewChangesTreeFactory.create(
          reviewDetailsVm.ideaProject,
          this,
          changesVm,
          object : SpaceDiffFileProvider {
            override fun getSpaceDiffFile(): SpaceDiffFile {
              return SpaceDiffFile(reviewDetailsVm.selectedChangesVm, diffVm)
            }
          }
        ).apply {
          border = IdeBorderFactory.createBorder(SideBorder.TOP)
        }
        val treeActionsToolbarPanel = createChangesBrowserToolbar(tree)

        firstComponent = commitDetailsPanel
        secondComponent = BorderLayoutPanel()
          .addToTop(treeActionsToolbarPanel)
          .addToCenter(tree)
      }

      firstComponent = SpaceReviewCommitListFactory.createCommitList(
        object : SpaceReviewCommitListVm {
          override val commits: Property<List<SpaceReviewCommitListItem>> = reviewDetailsVm.commits
          override val lifetime: Lifetime = reviewDetailsVm.lifetime
        },
        object : SpaceReviewCommitListController {
          override fun setSelectedCommitsIndices(indices: List<Int>) {
            reviewDetailsVm.commitChangesVm.selectedCommitIndices.value = indices
          }
        }
      )
      secondComponent = selectedCommitDetails
    }
    addToCenter(commitsBrowser)
  }

  private fun createCommitsDetailsPanel(parentDisposable: Disposable, project: Project, changesVm: SpaceReviewChangesVm): JComponent {
    val lifetime = changesVm.lifetime
    val commitListPanel: CommitDetailsListPanel<CommitDetailsPanel> = object : CommitDetailsListPanel<CommitDetailsPanel>(parentDisposable) {
      init {
        border = JBUI.Borders.empty()
      }

      override fun getCommitDetailsPanel() = CommitDetailsPanel(project) {}
    }

    val unknownRoot = LightVirtualFile()

    changesVm.selectedCommits.forEach(lifetime) { selectedCommits ->
      val selectedCommitsInfo = selectedCommits.map { selectedCommit ->
        val commit = selectedCommit.commitWithGraph.commit
        val hash = HashImpl.build(commit.id)
        val parents = commit.parents.map { id -> HashImpl.build(id) }
        val root = selectedCommit.spaceRepoInfo?.repository?.root ?: unknownRoot
        val message = commit.message.trimEnd('\n')
        val subject = message.substringBefore("\n\n")
        val author = VcsUserImpl(commit.author.name, commit.author.email)
        val committer = VcsUserImpl(commit.committer.name, commit.committer.email)
        VcsCommitMetadataImpl(hash, parents, commit.commitDate, root, subject, author, message, committer, commit.authorDate)
      }
      commitListPanel.setCommits(selectedCommitsInfo)
    }

    return commitListPanel
  }
}


