// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import circlet.code.api.CodeDiscussionAnchor
import circlet.code.api.CodeReviewService
import circlet.code.api.ReviewIdentifier
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import com.intellij.diff.util.Side
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.review.details.SpaceReviewChange
import libraries.coroutines.extra.Lifetime

internal class SpaceReviewCommentSubmitterImpl(
  override val lifetime: Lifetime,
  client: KCircletClient,
  private val projectKey: ProjectKey,
  private val reviewId: ReviewIdentifier,
  private val spaceReviewChange: SpaceReviewChange,
  private val pendingStateProvider: () -> Boolean
) : SpaceReviewCommentSubmitter {

  private val reviewService: CodeReviewService = client.codeReview

  override suspend fun submitComment(side: Side, line: Int, text: String) {
    if (text.trim().isEmpty()) return

    val diffContext = spaceReviewChange.gitCommitChange.toDiffContext()
    val revision = diffContext.getRevision(side)
    val filePath = diffContext.getFilePath(side)

    val anchor = CodeDiscussionAnchor(
      projectKey,
      repository = spaceReviewChange.repository,
      revision = revision,
      filename = filePath,
      oldLine = if (side == Side.LEFT) line else null,
      line = if (side == Side.RIGHT) line else null
    )

    val isPending = pendingStateProvider()
    SpaceStatsCounterCollector.SEND_MESSAGE.log(SpaceStatsCounterCollector.SendMessagePlace.NEW_DISCUSSION, isPending)
    reviewService.createDiscussion(
      anchor = anchor,
      text = text.trim(),
      reviewId = reviewId,
      diffContext = diffContext,
      pending = isPending
    )
  }
}