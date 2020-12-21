// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.code.api.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.space.messages.SpaceBundle

internal enum class ParticipantStatusBadgeKind {
  ACCEPTED,
  WAITING,
  WORKING,
  REJECTED
}

internal data class ParticipantStatusBadgeParams(
  @NlsContexts.Tooltip val label: String?,
  val kind: ParticipantStatusBadgeKind?
)

internal fun getParticipantStatusBadgeParams(
  review: CodeReviewRecord,
  participant: CodeReviewParticipant,
  reviewers: List<CodeReviewParticipant>
): ParticipantStatusBadgeParams {
  val isOpen = review.state == CodeReviewState.Opened

  val isReviewerAlsoAuthor = if (participant.role == CodeReviewParticipantRole.Author) {
    reviewers.any { it.user == participant.user }
  }
  else false

  val showTurnInfo = isOpen && review.turnBased == true && !isReviewerAlsoAuthor

  val isAccepted = participant.reviewerState == ReviewerState.Accepted
  val isRejected = participant.reviewerState == ReviewerState.Rejected

  val isInProgress = showTurnInfo && participant.reviewerState == null && participant.theirTurn == true
  val isWaitingReply = showTurnInfo && participant.reviewerState == null && participant.theirTurn == false

  val label = when {
    isAccepted -> SpaceBundle.message("review.participant.tooltip.accepted")
    isRejected -> SpaceBundle.message("review.participant.tooltip.concern")

    isInProgress && participant.role == CodeReviewParticipantRole.Reviewer -> {
      SpaceBundle.message("review.participant.tooltip.reviewing")
    }
    isInProgress && participant.role == CodeReviewParticipantRole.Author -> {
      if (reviewers.any { it.reviewerState == ReviewerState.Accepted } &&
          reviewers.none { it.reviewerState == null && it.theirTurn == false }
      ) {
        null
      }
      else {
        SpaceBundle.message("review.participant.tooltip.revising")
      }
    }
    isWaitingReply -> SpaceBundle.message("review.participant.tooltip.wait.response")
    else -> null
  }

  val kind = when {
    isAccepted -> ParticipantStatusBadgeKind.ACCEPTED
    isRejected -> ParticipantStatusBadgeKind.REJECTED
    isInProgress -> ParticipantStatusBadgeKind.WORKING
    isWaitingReply -> ParticipantStatusBadgeKind.WAITING
    else -> null
  }

  return ParticipantStatusBadgeParams(label = label, kind = kind)
}