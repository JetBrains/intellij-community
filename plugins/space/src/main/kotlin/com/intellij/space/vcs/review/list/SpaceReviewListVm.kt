package com.intellij.space.vcs.review.list

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.code.api.CodeReviewStateFilter
import circlet.code.api.CodeReviewWithCount
import circlet.code.api.ReviewSorting
import circlet.platform.api.ADate
import circlet.platform.client.XPagedListOnFlux
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.SpaceProjectInfo
import libraries.coroutines.extra.Lifetimed
import org.jetbrains.annotations.PropertyKey
import runtime.reactive.MutableProperty
import runtime.reactive.Property

interface SpaceReviewsListVm : Lifetimed {
  val spaceProjectInfo: SpaceProjectInfo

  val sorting: MutableProperty<ReviewSorting>

  val quickFiltersMap: Property<Map<ReviewListQuickFilter, ReviewListFilters>>

  val spaceReviewsFilterSettings: MutableProperty<ReviewListFilters>


  val isLoading: MutableProperty<Boolean>

  val reviews: Property<XPagedListOnFlux<CodeReviewWithCount>>

  fun refresh()
}

data class ReviewListFilters(
  val projectKey: ProjectKey,
  val state: CodeReviewStateFilter? = null,
  val text: String = "",
  val author: TD_MemberProfile? = null,
  val reviewer: TD_MemberProfile? = null,
  val createdFrom: ADate? = null,
  val createdTo: ADate? = null
)

enum class ReviewListQuickFilter(@PropertyKey(resourceBundle = SpaceBundle.BUNDLE) private val key: String) {
  OPEN("review.quick.filters.open"),
  AUTHORED_BY_ME("review.quick.filters.includes.my.changes"),
  NEEDS_MY_ATTENTION("review.quick.filters.needs.me.attention"),
  NEEDS_MY_REVIEW("review.quick.filters.needs.my.review"),
  CLOSED("review.quick.filters.closed");

  override fun toString(): String = SpaceBundle.message(key)
}