package com.intellij.space.vcs.review

import circlet.code.api.CodeReviewWithCount
import runtime.reactive.MutableProperty

internal interface SpaceSelectedReviewVm {
  val selectedReview: MutableProperty<CodeReviewWithCount?>
}