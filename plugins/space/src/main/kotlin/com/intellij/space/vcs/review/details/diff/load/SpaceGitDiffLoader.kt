// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff.load

import circlet.client.api.GitCommitChange
import circlet.client.api.ProjectKey
import com.intellij.openapi.vcs.FilePath
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.review.details.SpaceReviewChange
import git4idea.changes.GitChangeUtils
import libraries.coroutines.extra.Lifetime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class SpaceGitDiffLoader(parentLifetime: Lifetime) : SpaceDiffLoaderBase(parentLifetime) {
  private val cache: ConcurrentMap<GitCommitChange, DiffSides> = ConcurrentHashMap()

  init {
    parentLifetime.add {
      cache.clear()
    }
  }

  override fun loadDiffSides(projectKey: ProjectKey, spaceReviewChange: SpaceReviewChange, selectedCommitHashes: List<String>): DiffSides {
    val gitCommitChange = spaceReviewChange.gitCommitChange
    val cachedValue = cache[gitCommitChange]
    if (cachedValue != null) {
      SpaceStatsCounterCollector.DIFF_LOADED.log(SpaceStatsCounterCollector.LoaderType.GIT)
      return cachedValue
    }
    val repo = spaceReviewChange.spaceRepoInfo?.repository ?: throw GitDiffLoadingException("Repository is not mapped")
    val oldRevision = gitCommitChange.old?.commit
    val newRevision = gitCommitChange.new?.commit
    val filePaths = mutableSetOf<FilePath>().apply {
      val oldPath = spaceReviewChange.changeFilePathInfo.old
      val newPath = spaceReviewChange.changeFilePathInfo.new
      oldPath?.let { add(it) }
      newPath?.let { add(it) }
    }
    val (leftRangePart, rightRangePart) = when {
      oldRevision != null && newRevision != null -> oldRevision to newRevision
      oldRevision == null && newRevision != null -> "$newRevision^" to newRevision
      oldRevision != null && newRevision == null -> oldRevision to gitCommitChange.revision
      else -> throw GitDiffLoadingException("Old and new revisions are null")
    }
    val change = GitChangeUtils.getDiff(repo.project, repo.root, leftRangePart, rightRangePart, filePaths).single()
    return DiffSides(change.beforeRevision?.content, change.afterRevision?.content).also {
      SpaceStatsCounterCollector.DIFF_LOADED.log(SpaceStatsCounterCollector.LoaderType.GIT)
      cache.putIfAbsent(gitCommitChange, it)
    }
  }

  class GitDiffLoadingException(message: String) : Exception(message)
}