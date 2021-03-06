// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff.load

import circlet.client.api.GitCommitChange
import circlet.client.api.ProjectKey
import circlet.client.codeView
import circlet.platform.client.KCircletClient
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.space.vcs.review.details.getFileContent
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val coroutineContext = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

internal class SpaceServerDiffLoader(parentLifetime: Lifetime, client: KCircletClient) : SpaceDiffLoaderBase(parentLifetime) {
  private val cache: ConcurrentMap<GitCommitChange, CompletableFuture<DiffSides>> = ConcurrentHashMap()
  private val codeViewService = client.codeView

  init {
    parentLifetime.add {
      cache.clear()
    }
  }

  override fun loadDiffSides(
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffSides {
    val gitCommitChange = spaceReviewChange.gitCommitChange
    cache.computeIfAbsent(gitCommitChange) {
      loadDiffAsync(projectKey, spaceReviewChange, selectedCommitHashes)
    }
    return (cache[gitCommitChange] ?: loadDiffAsync(projectKey, spaceReviewChange, selectedCommitHashes)).get().also {
      SpaceStatsCounterCollector.DIFF_LOADED.log(SpaceStatsCounterCollector.LoaderType.SPACE)
    }
  }

  private fun loadDiffAsync(
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): CompletableFuture<DiffSides> {
    val result = CompletableFuture<DiffSides>()
    launch(parentLifetime, coroutineContext) {
      val gitCommitChange = spaceReviewChange.gitCommitChange
      val sideBySideDiff = codeViewService.getSideBySideDiff(
        projectKey,
        spaceReviewChange.repository,
        gitCommitChange,
        false,
        selectedCommitHashes
      )
      val leftFileText = getFileContent(sideBySideDiff.left)
      val rightFileText = getFileContent(sideBySideDiff.right)
      result.complete(DiffSides(leftFileText, rightFileText))
    }
    return result
  }
}