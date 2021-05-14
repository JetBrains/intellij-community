// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.*
import circlet.client.pr
import circlet.code.api.CodeReviewRecord
import circlet.code.api.CodeReviewService
import circlet.code.codeReview
import circlet.platform.api.BatchInfo
import circlet.platform.api.Ref
import circlet.platform.api.TID
import circlet.platform.api.map
import circlet.platform.client.KCircletClient
import circlet.platform.client.XPagedListOnFlux
import circlet.platform.client.resolveAll
import circlet.platform.client.xTransformedPagedListOnFlux
import com.intellij.space.vcs.review.details.CommitSetReviewDetailsVm
import com.intellij.space.vcs.review.details.MergeRequestDetailsVm
import com.intellij.space.vcs.review.details.SpaceReviewDetailsVm
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.flatten
import runtime.reactive.property.map
import runtime.reactive.property.mapInit

internal class SpaceReviewersSelectorVm(
  override val lifetime: Lifetime,
  val review: CodeReviewRecord,
  val projectKey: ProjectKey,
  val client: KCircletClient,
  private val detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>,
  participantsVm: SpaceReviewParticipantsVm
) : Lifetimed {

  private val codeReviewService: CodeReviewService = client.codeReview
  private val projectService: Projects = client.pr

  val searchText: MutableProperty<String> = Property.createMutable("")

  val reviewersIds: Property<Set<TID>> = map(participantsVm.reviewers) { reviewers ->
    reviewers.map { it.user.id }.toSet()
  }

  val possibleReviewers: Property<XPagedListOnFlux<CheckedReviewer>> = map(searchText) { text ->
    xTransformedPagedListOnFlux(
      client = client,
      batchSize = 30,
      keyFn = { it.id },
      loadImmediately = true,
      result = { list: List<Ref<TD_MemberProfile>> ->
        list.resolveAll().map {
          CheckedReviewer(it, reviewersIds)
        }
      }
    ) { batch: BatchInfo ->
      when (detailsVm) {
        is MergeRequestDetailsVm -> {
          codeReviewService.getPossibleReviewersForMergeRequest(
            detailsVm.projectKey.identifier,
            detailsVm.repository.value,
            (detailsVm.targetBranchInfo.value?.displayName ?: "").toFullBranchName(),
            batch,
            text).map {
            it.profile
          }
        }
        is CommitSetReviewDetailsVm -> {
          projectService.getMembersWhoCanViewProject(batch, detailsVm.projectKey.identifier, text)
        }
      }
    }
  }

  val isLoading: Property<Boolean> = lifetime.flatten(
    lifetime.map(possibleReviewers) { reviewList ->
      lifetime.mapInit(reviewList.isLoading, false) {
        it
      }
    }
  )
}
