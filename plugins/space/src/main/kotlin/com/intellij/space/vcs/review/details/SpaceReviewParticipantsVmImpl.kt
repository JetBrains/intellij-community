// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.Ref
import circlet.platform.client.KCircletClient
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import runtime.reactive.cellProperty
import runtime.reactive.live
import runtime.reactive.property.map

internal class SpaceReviewParticipantsVmImpl(
  override val lifetime: Lifetime,
  private val projectKey: ProjectKey,
  val review: Ref<CodeReviewRecord>,
  participantsRef: Ref<CodeReviewParticipants>,
  private val pendingCounterRef: Ref<CodeReviewPendingMessageCounter>,
  private val reviewIdentifier: ReviewIdentifier,
  private val workspace: Workspace
) : SpaceReviewParticipantsVm {
  private val client: KCircletClient = workspace.client

  private val participants = participantsRef.property()
  override val codeReviewRecord: CodeReviewRecord = review.resolve()

  override val authors: Property<List<CodeReviewParticipant>> = cellProperty {
    participants.live.participants?.filter { it.role == CodeReviewParticipantRole.Author } ?: emptyList()
  }

  override val reviewers: Property<List<CodeReviewParticipant>> = cellProperty {
    participants.live.participants?.filter { it.role == CodeReviewParticipantRole.Reviewer } ?: emptyList()
  }
  override val me: TD_MemberProfile
    get() = workspace.me.value

  override val controlVm: Property<ParticipantStateControlVM> = map(
    review.property(),
    reviewers,
    authors
  )
  { reviewRecord, reviewers, authors ->
    if (reviewRecord.state != CodeReviewState.Opened) {
      ParticipantStateControlVM.WithoutControls
    }
    else {
      createControlVm(reviewers, authors, reviewRecord)
    }
  }

  private fun createControlVm(
    reviewers: List<CodeReviewParticipant>,
    authors: List<CodeReviewParticipant>,
    reviewRecord: CodeReviewRecord
  ): ParticipantStateControlVM {
    val meReviewer = reviewers.me()
    val meAuthor = authors.me()
    val isTurnBased = reviewRecord.turnBased == true

    when {
      meReviewer != null -> return when {
        meReviewer.theirTurn == true && isTurnBased -> {
          ParticipantStateControlVM.ReviewerDropdown(turnBased = true, pendingCounterRef)
        }
        (meReviewer.theirTurn == null || !isTurnBased) && meReviewer.reviewerState == null -> {
          ParticipantStateControlVM.ReviewerDropdown(turnBased = false, pendingCounterRef)
        }
        meReviewer.theirTurn == false && isTurnBased -> {
          ParticipantStateControlVM.ReviewerResumeReviewButton(meReviewer.state)
        }
        (meReviewer.theirTurn == null || !isTurnBased) && meReviewer.reviewerState != null -> {
          ParticipantStateControlVM.ReviewerResumeReviewButton(meReviewer.state)
        }
        else -> ParticipantStateControlVM.WithoutControls
      }

      meAuthor != null -> return when {
        !isTurnBased -> ParticipantStateControlVM.WithoutControls
        meAuthor.theirTurn == true -> {
          ParticipantStateControlVM.AuthorEndTurnButton(
            canPassTurn = !reviewers.all { it.reviewerState == ReviewerState.Accepted },
            canFinishReview = reviewers.any { it.reviewerState == ReviewerState.Accepted } &&
                              reviewers.none { it.reviewerState == null && it.theirTurn == false }
          )
        }
        meAuthor.theirTurn == false && reviewers.isNotEmpty() -> {
          ParticipantStateControlVM.AuthorResumeReviewButton
        }
        else -> ParticipantStateControlVM.WithoutControls
      }
      else -> return ParticipantStateControlVM.WithoutControls
    }
  }

  private fun List<CodeReviewParticipant>.me() = singleOrNull { it.user.id == me.id }

  override suspend fun addReviewer(profile: TD_MemberProfile) {
    client.codeReview.addReviewParticipant(
      projectKey.identifier,
      reviewIdentifier,
      profile.identifier,
      CodeReviewParticipantRole.Reviewer
    )
  }

  override suspend fun removeReviewer(profile: TD_MemberProfile) {
    client.codeReview.removeReviewParticipant(
      projectKey.identifier,
      reviewIdentifier,
      profile.identifier,
      CodeReviewParticipantRole.Reviewer
    )
  }
}

