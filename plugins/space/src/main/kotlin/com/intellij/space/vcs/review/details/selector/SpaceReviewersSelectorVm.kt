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
import com.intellij.space.vcs.review.details.CrDetailsVm
import com.intellij.space.vcs.review.details.MergeRequestDetailsVm
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.map

internal class SpaceReviewersSelectorVm(override val lifetime: Lifetime,
                                        val review: CodeReviewRecord,
                                        val projectKey: ProjectKey,
                                        val client: KCircletClient,
                                        private val detailsVm: CrDetailsVm<out CodeReviewRecord>,
                                        private val participantsVm: SpaceReviewParticipantsVm
) : Lifetimed {

  private val codeReviewService: CodeReviewService = client.codeReview
  private val projectService: Projects = client.pr

  val isLoading: MutableProperty<Boolean> = Property.createMutable(false)

  val searchText: MutableProperty<String> = Property.createMutable("")

  val reviewersIds: Property<Set<TID>> = map(participantsVm.reviewers) {
    it?.map { it.user.id }?.toSet() ?: emptySet()
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
          projectService.getProjectMembersWhoCanViewProject(batch,
                                                            detailsVm.projectKey.identifier,
                                                            text)
        }
        else -> throw IllegalArgumentException("Unsupported review type")
      }
    }
  }
}
