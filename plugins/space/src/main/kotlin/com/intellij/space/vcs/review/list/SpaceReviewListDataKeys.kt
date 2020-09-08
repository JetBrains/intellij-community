package com.intellij.space.vcs.review.list

import circlet.code.api.CodeReviewWithCount
import com.intellij.openapi.actionSystem.DataKey

internal object SpaceReviewListDataKeys {
  @JvmStatic
  internal val SELECTED_REVIEW: DataKey<CodeReviewWithCount> = DataKey.create("com.intellij.space.vcs.review.selected")

  @JvmStatic
  internal val REVIEWS_LIST_VM: DataKey<SpaceReviewsListVm> = DataKey.create("space.code.review.vm")
}
