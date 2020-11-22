// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import circlet.code.api.ChangeInReview
import circlet.code.api.CodeDiscussionAnchor
import circlet.code.api.CodeReviewService
import circlet.code.api.ReviewIdentifier
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import com.intellij.diff.util.Side
import libraries.coroutines.extra.Lifetime

class SpaceReviewCommentSubmitterImpl(
  override val lifetime: Lifetime,
  client: KCircletClient,
  private val projectKey: ProjectKey,
  private val reviewId: ReviewIdentifier,
  private val changeInReview: ChangeInReview,
  private val pendingStateProvider: () -> Boolean
) : SpaceReviewCommentSubmitter {

  private val reviewService: CodeReviewService = client.codeReview

  override suspend fun submitComment(side: Side, line: Int, text: String) {
    if (text.trim().isEmpty()) return

    val diffContext = changeInReview.change.toDiffContext()
    val revision = diffContext.getRevision(side)
    val filePath = diffContext.getFilePath(side)

    val anchor = CodeDiscussionAnchor(
      projectKey,
      repository = changeInReview.repository,
      revision = revision,
      filename = filePath,
      oldLine = if (side == Side.LEFT) line else null,
      line = if (side == Side.RIGHT) line else null
    )
    reviewService.createDiscussion(
      anchor = anchor,
      text = text.trim(),
      reviewId = reviewId,
      diffContext = diffContext,
      pending = pendingStateProvider()
    )
  }
}