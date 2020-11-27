// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.code.api.ChangeInReview
import circlet.code.api.PropagatedCodeDiscussion
import circlet.code.api.ReviewIdentifier
import circlet.platform.api.TID
import com.intellij.openapi.ListSelection
import com.intellij.space.SpaceVmWithClient
import com.intellij.space.vcs.SpaceRepoInfo
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.MutableProperty
import runtime.reactive.ObservableMutableMap
import runtime.reactive.Property

internal data class ChangesWithDiscussion(
  val changesInReview: List<ChangeInReview>,
  val discussions: ObservableMutableMap<TID, PropagatedCodeDiscussion>,
  val spaceRepoInfo: SpaceRepoInfo?
)

internal interface SpaceReviewChangesVm : SpaceVmWithClient, Lifetimed {
  val projectKey: ProjectKey
  val reviewIdentifier: ReviewIdentifier
  val reviewId: TID
  val selectedCommits: Property<List<ReviewCommitListItem>>
  val changes: Property<Map<String, ChangesWithDiscussion>?>
  val listSelection: MutableProperty<ListSelection<SpaceReviewChange>>

  val participantsVm: Property<SpaceReviewParticipantsVm?>
  val infoByRepos: Map<String, SpaceRepoInfo>
}