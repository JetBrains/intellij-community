// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.GitCommitChange
import circlet.client.api.ProjectKey
import circlet.client.codeView
import circlet.platform.client.KCircletClient
import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffVcsDataKeys
import com.intellij.diff.contents.DiffContent
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.space.vcs.review.details.getFileContent
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitRevisionNumber
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val coroutineContext = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

internal class SpaceReviewDiffLoader(
  private val parentLifetime: Lifetime,
  client: KCircletClient
) {
  private val codeViewService = client.codeView
  private val cache: ConcurrentMap<GitCommitChange, CompletableFuture<DiffData>> = ConcurrentHashMap()

  init {
    parentLifetime.add {
      cache.clear()
    }
  }

  @RequiresBackgroundThread
  fun loadDiff(
    project: Project,
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffData {
    val gitCommitChange = spaceReviewChange.gitCommitChange
    cache.computeIfAbsent(gitCommitChange) {
      loadDiffAsync(project, projectKey, spaceReviewChange, selectedCommitHashes, gitCommitChange)
    }
    return (cache[gitCommitChange] ?: loadDiffAsync(project, projectKey, spaceReviewChange, selectedCommitHashes, gitCommitChange)).get()
  }

  private fun loadDiffAsync(
    project: Project,
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>,
    gitCommitChange: GitCommitChange
  ): CompletableFuture<DiffData> {
    val result = CompletableFuture<DiffData>()
    launch(parentLifetime, coroutineContext) {
      val sideBySideDiff = codeViewService.getSideBySideDiff(projectKey,
                                                             spaceReviewChange.repository,
                                                             gitCommitChange,
                                                             false,
                                                             selectedCommitHashes)
      val leftFileText = getFileContent(sideBySideDiff.left)
      val rightFileText = getFileContent(sideBySideDiff.right)

      val (oldFilePath, newFilePath) = spaceReviewChange.changeFilePathInfo
      val diffContentFactory = DiffContentFactoryImpl.getInstanceEx()
      val titles = listOf(
        gitCommitChange.old?.let { "${it.commit} (${oldFilePath!!.name})" },
        gitCommitChange.new?.let { "${it.commit} (${newFilePath!!.name})" }
      )
      val diffContents = listOf(
        oldFilePath?.let {
          diffContentFactory.create(project, leftFileText, it).apply {
            putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(it, GitRevisionNumber(gitCommitChange.old!!.commit)))
          }
        } ?: diffContentFactory.createEmpty(),
        newFilePath?.let {
          diffContentFactory.create(project, rightFileText, it).apply {
            putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(it, GitRevisionNumber(gitCommitChange.new!!.commit)))
          }
        } ?: diffContentFactory.createEmpty()
      )

      result.complete(DiffData(diffContents, titles))
    }
    return result
  }

  data class DiffData(val contents: List<DiffContent>, val titles: List<String?>)
}