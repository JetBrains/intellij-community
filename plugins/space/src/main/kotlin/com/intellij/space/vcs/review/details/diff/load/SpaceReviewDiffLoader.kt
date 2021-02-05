// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff.load

import circlet.client.api.ProjectKey
import com.intellij.diff.contents.DiffContent
import com.intellij.openapi.project.Project
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal interface SpaceReviewDiffLoader {
  @RequiresBackgroundThread
  fun loadDiffData(
    project: Project,
    projectKey: ProjectKey,
    spaceReviewChange: SpaceReviewChange,
    selectedCommitHashes: List<String>
  ): DiffData
}

data class DiffData(val contents: List<DiffContent>, val titles: List<String?>)