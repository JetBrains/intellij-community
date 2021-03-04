// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.process

import circlet.code.api.CodeReviewRecord
import circlet.code.api.CodeReviewService
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import circlet.workspaces.Workspace
import com.intellij.space.stats.SpaceStatsCounterCollector
import libraries.klogging.logger

private val log = logger<SpaceReviewStateUpdaterImpl>()

internal class SpaceReviewStateUpdaterImpl(
  val workspace: Workspace,
  val codeReviewRecord: CodeReviewRecord,
) : SpaceReviewStateUpdater {
  private val client: KCircletClient = workspace.client
  private val codeReviewService: CodeReviewService = client.codeReview

  private val id = codeReviewRecord.id

  override suspend fun resumeReview() {
    log.info { "resuming code review $id" }

    SpaceStatsCounterCollector.RESUME_REVIEW.log()

    codeReviewService.resumeReview(id)

    log.info { "code review $id resumed" }
  }

  override suspend fun acceptReview() {
    log.info { "accepting code review $id" }

    SpaceStatsCounterCollector.ACCEPT_CHANGES.log()

    codeReviewService.acceptChanges(this.id)

    log.info { "code review $id accepted" }
  }

  override suspend fun waitAuthorReply() {
    log.info { "turn waiting for author reply in review $id" }

    SpaceStatsCounterCollector.WAIT_FOR_RESPONSE.log()

    codeReviewService.waitAuthorReply(this.id)

    log.info { "waiting for author is turning on for review $id" }
  }

  override suspend fun submitPendingMessages() {
    log.info { "submitting messages in review $id" }

    SpaceStatsCounterCollector.WAIT_FOR_RESPONSE.log()

    codeReviewService.submitPendingMessages(this.id)

    log.info { "messages submitted in review $id" }
  }
}