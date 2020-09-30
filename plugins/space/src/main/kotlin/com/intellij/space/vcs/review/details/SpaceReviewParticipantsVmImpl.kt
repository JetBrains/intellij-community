package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.CodeReviewParticipant
import circlet.code.api.CodeReviewParticipantRole
import circlet.code.api.CodeReviewParticipants
import circlet.code.api.ReviewIdentifier
import circlet.code.codeReview
import circlet.platform.api.Ref
import circlet.platform.client.KCircletClient
import circlet.platform.client.property
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import runtime.reactive.cellProperty
import runtime.reactive.live

internal class SpaceReviewParticipantsVmImpl(ref: Ref<CodeReviewParticipants>,
                                             private val projectKey: ProjectKey,
                                             private val reviewIdentifier: ReviewIdentifier,
                                             private val client: KCircletClient,
                                             override val lifetime: Lifetime) : SpaceReviewParticipantsVm {
  private val participants = ref.property()

  override val authors: Property<List<CodeReviewParticipant>?> = cellProperty {
    participants.live.participants?.filter { it.role == CodeReviewParticipantRole.Author }
  }

  override val reviewers: Property<List<CodeReviewParticipant>?> = cellProperty {
    participants.live.participants?.filter { it.role == CodeReviewParticipantRole.Reviewer }
  }

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