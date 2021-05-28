// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.ProjectIdentifier
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.CodeReviewParticipantRole
import circlet.code.api.CodeReviewService
import circlet.code.api.ReviewIdentifier
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import com.intellij.space.stats.SpaceStatsCounterCollector

internal fun createSpaceReviewParticipantController(
  client: KCircletClient,
  projectIdentifier: ProjectIdentifier,
  reviewIdentifier: ReviewIdentifier,
  role: CodeReviewParticipantRole
): SpaceReviewParticipantController {
  return when (role) {
    CodeReviewParticipantRole.Reviewer -> SpaceReviewParticipantControllerImpl(client, projectIdentifier, reviewIdentifier, role)
    CodeReviewParticipantRole.Author -> SpaceReviewParticipantControllerImpl(client, projectIdentifier, reviewIdentifier, role)
    else -> error("unable to create participants controller for ${role}")
  }
}

internal class SpaceReviewParticipantControllerImpl(
  client: KCircletClient,
  private val projectIdentifier: ProjectIdentifier,
  private val reviewIdentifier: ReviewIdentifier,
  private val role: CodeReviewParticipantRole
) : SpaceReviewParticipantController {
  private val codeReviewService: CodeReviewService = client.codeReview

  override suspend fun addParticipant(participant: TD_MemberProfile) {
    SpaceStatsCounterCollector.EDIT_PARTICIPANT.log(role, SpaceStatsCounterCollector.ParticipantEditType.ADD)
    codeReviewService.addReviewParticipant(
      projectIdentifier,
      reviewIdentifier,
      participant.identifier,
      role
    )
  }

  override suspend fun removeParticipant(participant: TD_MemberProfile) {
    SpaceStatsCounterCollector.EDIT_PARTICIPANT.log(role, SpaceStatsCounterCollector.ParticipantEditType.REMOVE)
    codeReviewService.removeReviewParticipant(
      projectIdentifier,
      reviewIdentifier,
      participant.identifier,
      role
    )
  }
}