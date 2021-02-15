// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.TD_MemberProfile
import circlet.code.api.CodeReviewParticipant
import circlet.code.api.CodeReviewPendingMessageCounter
import circlet.code.api.CodeReviewRecord
import circlet.code.api.ReviewerState
import circlet.platform.api.Ref
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property

sealed class ParticipantStateControlVM {
  class ReviewerDropdown(val turnBased: Boolean, val pendingCounterRef: Ref<CodeReviewPendingMessageCounter>) : ParticipantStateControlVM()
  class ReviewerResumeReviewButton(val reviewerState: ReviewerState?) : ParticipantStateControlVM()

  class AuthorEndTurnButton(val canPassTurn: Boolean, val canFinishReview: Boolean) : ParticipantStateControlVM()
  object AuthorResumeReviewButton : ParticipantStateControlVM()

  object WithoutControls : ParticipantStateControlVM()
}


interface SpaceReviewParticipantsVm : Lifetimed {
  val codeReviewRecord: CodeReviewRecord

  val authors: Property<List<CodeReviewParticipant>>

  val reviewers: Property<List<CodeReviewParticipant>>

  val me: TD_MemberProfile

  val controlVm: Property<ParticipantStateControlVM>

  suspend fun addReviewer(profile: TD_MemberProfile)

  suspend fun removeReviewer(profile: TD_MemberProfile)
}