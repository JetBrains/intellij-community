// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.space.editor.SpaceVirtualFilesManager
import com.intellij.space.vcs.review.details.SpaceReviewCommit.subject
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.space.vcs.review.details.diff.SpaceDiffVm
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
import javax.swing.JComponent

internal class SpaceReviewCommitListPanel(
  private val parentDisposable: Disposable,
  private val reviewDetailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>
) : BorderLayoutPanel() {
  init {
    val reviewCommitsList = OnePixelSplitter(true, "space.review.commit.list.v2", 0.6f).apply {
      val changesVm = reviewDetailsVm.commitChangesVm
      firstComponent = createCommitListWithDetails(reviewDetailsVm.lifetime, changesVm)
      secondComponent = createChangesBrowserPanel(reviewDetailsVm.spaceDiffVm, changesVm)
    }
    addToCenter(reviewCommitsList)
  }

  private fun createCommitListWithDetails(
    lifetime: Lifetime,
    changesVm: SpaceReviewChangesVm
  ): JComponent {
    val commitList = SpaceReviewCommitListFactory.createCommitList(
      object : SpaceReviewCommitListVm {
        override val commits = reviewDetailsVm.commits
        override val lifetime = lifetime
      },
      object : SpaceReviewCommitListController {
        override fun setSelectedCommitsIndices(indices: List<Int>) {
          changesVm.selectedCommitIndices.value = indices
        }
      }
    )

    val commitDetails = createCommitsDetailsPanel(reviewDetailsVm.ideaProject, changesVm)
    return OnePixelSplitter(true, "space.review.commit.list.details.v2", 0.6f).apply {
      firstComponent = commitList
      secondComponent = commitDetails
    }
  }

  private fun createCommitsDetailsPanel(project: Project, changesVm: SpaceReviewChangesVm): JComponent {
    val lifetime = changesVm.lifetime
    val commitListPanel = object : CommitDetailsListPanel<CommitDetailsPanel>(parentDisposable) {
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
        val subject = commit.subject()
        val author = VcsUserImpl(commit.author.name, commit.author.email)
        val committer = VcsUserImpl(commit.committer.name, commit.committer.email)
        VcsCommitMetadataImpl(hash, parents, commit.commitDate, root, subject, author, message, committer, commit.authorDate)
      }
      commitListPanel.setCommits(selectedCommitsInfo)
    }

    return commitListPanel
  }

  private fun createChangesBrowserPanel(
    diffVm: SpaceDiffVm,
    changesVm: SpaceReviewChangesVm
  ): JComponent {
    val tree = SpaceReviewChangesTreeFactory.create(
      reviewDetailsVm.ideaProject,
      parentDisposable,
      this,
      changesVm,
      object : SpaceDiffFileProvider {
        override fun getSpaceDiffFile(): SpaceDiffFile {
          return reviewDetailsVm.ideaProject.service<SpaceVirtualFilesManager>()
            .findOrCreateDiffFile(reviewDetailsVm.selectedChangesVm, diffVm)
        }
      }
    ).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }

    return BorderLayoutPanel()
      .addToTop(createChangesBrowserToolbar(tree))
      .addToCenter(tree)
  }
}


