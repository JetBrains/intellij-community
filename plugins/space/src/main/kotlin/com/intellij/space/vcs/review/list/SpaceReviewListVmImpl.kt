package com.intellij.space.vcs.review.list

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.CodeReviewService
import circlet.code.api.CodeReviewStateFilter
import circlet.code.api.CodeReviewWithCount
import circlet.code.api.ReviewSorting
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import circlet.platform.client.XPagedListOnFlux
import circlet.platform.client.xPagedListOnFlux
import com.intellij.space.vcs.SpaceProjectInfo
import libraries.coroutines.extra.Lifetime
import runtime.reactive.*


internal class SpaceReviewsListVmImpl(override val lifetime: Lifetime,
                                      val client: KCircletClient,
                                      override val spaceProjectInfo: SpaceProjectInfo,
                                      val me: Property<TD_MemberProfile>) : SpaceReviewsListVm {
  private val codeReviewService: CodeReviewService = client.codeReview

  override val isLoading: MutableProperty<Boolean> = mutableProperty(false)

  override val sorting: MutableProperty<ReviewSorting> = mutableProperty(ReviewSorting.CreatedAtDesc)

  override val quickFiltersMap: Property<Map<ReviewListQuickFilter, ReviewListFilters>> = lifetime.cellProperty {
    defaultQuickFiltersMap(spaceProjectInfo.key, me)
  }

  override val spaceReviewsFilterSettings: MutableProperty<ReviewListFilters> = mutableProperty(
    quickFiltersMap.value[DEFAULT_QUICK_FILTER] ?: error("Unable to find quick filter settings")
  )

  // TODO: remove hack
  private val refresh = Property.createMutable(0)

  override fun refresh() {
    refresh.value = refresh.value.inc()
  }

  override val reviews: Property<XPagedListOnFlux<CodeReviewWithCount>> = lifetime.map(refresh, spaceReviewsFilterSettings) { _, filterSettings ->
    lifetime.xPagedListOnFlux(
      client = client,
      batchSize = DEFAULT_BATCH_SIZE,
      keyFn = { it.review.id },
      loadImmediately = true
    ) { batch ->
      codeReviewService.listReviews(
        batchInfo = batch,
        project = spaceProjectInfo.key.identifier,
        state = filterSettings.state,
        sort = sorting.value,
        text = filterSettings.text,
        author = filterSettings.author?.identifier,
        reviewer = filterSettings.reviewer?.identifier,
        from = filterSettings.createdFrom,
        to = filterSettings.createdTo
      )
    }
  }
}

private fun defaultQuickFiltersMap(spaceProjectKey: ProjectKey,
                                   me: Property<TD_MemberProfile>): Map<ReviewListQuickFilter, ReviewListFilters> = mapOf(
  ReviewListQuickFilter.OPEN to ReviewListFilters(
    projectKey = spaceProjectKey,
    state = CodeReviewStateFilter.Opened
  ),
  ReviewListQuickFilter.AUTHORED_BY_ME to ReviewListFilters(
    projectKey = spaceProjectKey,
    state = CodeReviewStateFilter.Opened,
    author = me.live
  ),
  ReviewListQuickFilter.NEEDS_MY_ATTENTION to ReviewListFilters(
    projectKey = spaceProjectKey,
    state = CodeReviewStateFilter.RequiresAuthorAttention,
    author = me.live
  ),
  ReviewListQuickFilter.NEEDS_MY_REVIEW to ReviewListFilters(
    projectKey = spaceProjectKey,
    state = CodeReviewStateFilter.NeedsReview,
    reviewer = me.live
  ),
  ReviewListQuickFilter.CLOSED to ReviewListFilters(
    projectKey = spaceProjectKey,
    state = CodeReviewStateFilter.Closed
  )
)

private const val DEFAULT_BATCH_SIZE = 30

internal val DEFAULT_QUICK_FILTER = ReviewListQuickFilter.NEEDS_MY_ATTENTION
