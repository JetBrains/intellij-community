// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.code.api.CodeReviewListItem
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.space.vcs.review.details.SpaceReviewDetailsVm
import com.intellij.space.vcs.review.list.SpaceReviewsListVm

object SpaceReviewDataKeys {
  @JvmStatic
  internal val REVIEWS_LIST_VM: DataKey<SpaceReviewsListVm> = DataKey.create("space.code.review.vm")

  @JvmStatic
  internal val SELECTED_REVIEW: DataKey<CodeReviewListItem> = DataKey.create("com.intellij.space.vcs.review.selected")

  @JvmStatic
  internal val SELECTED_REVIEW_VM: DataKey<SpaceSelectedReviewVm> = DataKey.create("com.intellij.space.vcs.review.selected.vm")

  @JvmStatic
  internal val REVIEW_DETAILS_VM: DataKey<SpaceReviewDetailsVm<*>> = DataKey.create("space.code.review.vm")
}