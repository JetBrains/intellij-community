package com.intellij.space.vcs.review

import com.intellij.openapi.actionSystem.DataKey

object SpaceReviewDataKeys {
  @JvmStatic
  internal val SELECTED_REVIEW_VM: DataKey<SpaceSelectedReviewVm> = DataKey.create("com.intellij.space.vcs.review.selected.vm")
}