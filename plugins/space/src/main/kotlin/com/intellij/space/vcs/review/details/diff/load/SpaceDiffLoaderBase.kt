// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff.load

import circlet.client.api.ProjectKey
import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffVcsDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitRevisionNumber
import libraries.coroutines.extra.Lifetime

internal abstract class SpaceDiffLoaderBase(protected val parentLifetime: Lifetime) : SpaceReviewDiffLoader {
  @RequiresBackgroundThread
  override fun loadDiffData(
    project: Project,
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffData = createDiffData(project, spaceReviewChange, loadDiffSides(projectKey, spaceReviewChange, selectedCommitHashes))

  protected abstract fun loadDiffSides(
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffSides

  private fun createDiffData(
    project: Project,
    spaceReviewChange: SpaceReviewChange,
    diffSides: DiffSides
  ): DiffData {
    val (leftFileText, rightFileText) = diffSides
    val gitCommitChange = spaceReviewChange.gitCommitChange

    val (oldFilePath, newFilePath) = spaceReviewChange.changeFilePathInfo
    val diffContentFactory = DiffContentFactoryImpl.getInstanceEx()
    val titles = listOf(
      gitCommitChange.old?.let { "${it.commit} (${oldFilePath!!.name})" },
      gitCommitChange.new?.let { "${it.commit} (${newFilePath!!.name})" }
    )
    val diffContents = listOf(
      if (oldFilePath != null && leftFileText != null) {
        diffContentFactory.create(project, leftFileText, oldFilePath).apply {
          putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(oldFilePath, GitRevisionNumber(gitCommitChange.old!!.commit)))
        }
      }
      else {
        diffContentFactory.createEmpty()
      },
      if (newFilePath != null && rightFileText != null) {
        diffContentFactory.create(project, rightFileText, newFilePath).apply {
          putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(newFilePath, GitRevisionNumber(gitCommitChange.new!!.commit)))
        }
      }
      else {
        diffContentFactory.createEmpty()
      }
    )
    return DiffData(diffContents, titles)
  }

  protected data class DiffSides(val left: String?, val right: String?)
}