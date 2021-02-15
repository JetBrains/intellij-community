// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.GitCommitWithGraph
import circlet.code.api.RepositoryInReview
import com.intellij.space.vcs.SpaceRepoInfo

internal data class SpaceReviewCommitListItem(
  val commitWithGraph: GitCommitWithGraph,
  val repositoryInReview: RepositoryInReview,
  val index: Int,
  val commitsInRepository: Int,
  val spaceRepoInfo: SpaceRepoInfo?
) {
  val inCurrentProject: Boolean = spaceRepoInfo != null
}