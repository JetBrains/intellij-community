// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff.load

import circlet.client.api.ProjectKey
import circlet.platform.client.KCircletClient
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.space.vcs.review.details.SpaceReviewChange
import libraries.coroutines.extra.Lifetime
import libraries.klogging.logger

internal class SpaceLocalDiffLoaderWithCallback(parentLifetime: Lifetime, client: KCircletClient) : SpaceReviewDiffLoader {
  companion object {
    private val LOG = logger<SpaceLocalDiffLoaderWithCallback>()
  }

  private val gitDiffLoader = SpaceGitDiffLoader(parentLifetime)
  private val spaceServerDiffLoader = SpaceServerDiffLoader(parentLifetime, client)

  override fun loadDiffData(
    project: Project,
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffData {
    try {
      return gitDiffLoader.loadDiffData(project, projectKey, spaceReviewChange, selectedCommitHashes)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (th: Throwable) {
      LOG.info { "Couldn't load diff by Git: ${th.message}" }
    }

    return spaceServerDiffLoader.loadDiffData(project, projectKey, spaceReviewChange, selectedCommitHashes)
  }
}