package com.intellij.space.vcs.review.details

import circlet.client.api.TD_MemberProfile
import circlet.code.api.CodeReviewParticipant
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property

interface SpaceReviewParticipantsVm : Lifetimed {
  val authors: Property<List<CodeReviewParticipant>?>

  val reviewers: Property<List<CodeReviewParticipant>?>

  suspend fun addReviewer(profile: TD_MemberProfile)

  suspend fun removeReviewer(profile: TD_MemberProfile)
}