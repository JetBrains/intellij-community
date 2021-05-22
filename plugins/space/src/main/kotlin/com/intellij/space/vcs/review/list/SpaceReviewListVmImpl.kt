// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.CodeReviewListItem
import circlet.code.api.CodeReviewService
import circlet.code.api.CodeReviewStateFilter
import circlet.code.api.ReviewSorting
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import circlet.platform.client.XPagedListOnFlux
import circlet.platform.client.xPagedListOnFlux
import com.intellij.space.vcs.SpaceProjectInfo
import libraries.coroutines.extra.Lifetime
import runtime.reactive.*
import runtime.reactive.property.map
import runtime.reactive.property.mapInit


internal class SpaceReviewsListVmImpl(override val lifetime: Lifetime,
                                      val client: KCircletClient,
                                      override val spaceProjectInfo: SpaceProjectInfo,
                                      val me: Property<TD_MemberProfile>) : SpaceReviewsListVm {
  private val codeReviewService: CodeReviewService = client.codeReview

  override val sorting: MutableProperty<ReviewSorting> = mutableProperty(ReviewSorting.CreatedAtDesc)

  override val quickFiltersMap: Property<Map<ReviewListQuickFilter, ReviewListFilters>> = lifetime.cellProperty {
    defaultQuickFiltersMap(spaceProjectInfo.key, me)
  }

  override val spaceReviewsQuickFilter: MutableProperty<ReviewListFilters> = mutableProperty(
    quickFiltersMap.value[DEFAULT_QUICK_FILTER] ?: error("Unable to find quick filter settings")
  )
  override val textToSearch: MutableProperty<String> = mutableProperty("")

  private val refresh = Property.createMutable(Unit)

  override fun refresh() {
    refresh.value = refresh.forceNotify()
  }

  override val reviews: Property<XPagedListOnFlux<CodeReviewListItem>> =
    lifetime.map(refresh, textToSearch, spaceReviewsQuickFilter) { _, textToSearch, filterSettings ->
      lifetime.xPagedListOnFlux(
        client = client,
        batchSize = DEFAULT_BATCH_SIZE,
        keyFn = { it.review.id },
        loadImmediately = true
      ) { batch ->
        codeReviewService.listReviewsV2(
          batchInfo = batch,
          project = spaceProjectInfo.key.identifier,
          state = filterSettings.state,
          sort = sorting.value,
          text = textToSearch,
          author = filterSettings.author?.identifier,
          reviewer = filterSettings.reviewer?.identifier,
          from = filterSettings.createdFrom,
          to = filterSettings.createdTo
        )
      }
    }

  override val isLoading: Property<Boolean> = lifetime.flatten(
    lifetime.map(reviews) { reviewList ->
      lifetime.mapInit(reviewList.isLoading, false) {
        it
      }
    }
  )
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
  ),
  ReviewListQuickFilter.ASSIGNED_TO_ME to ReviewListFilters(
    projectKey = spaceProjectKey,
    reviewer = me.live
  )
)

private const val DEFAULT_BATCH_SIZE = 30

internal val DEFAULT_QUICK_FILTER = ReviewListQuickFilter.NEEDS_MY_ATTENTION
