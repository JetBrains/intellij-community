package com.intellij.space.vcs.review

import circlet.code.api.CodeReviewWithCount
import runtime.reactive.MutableProperty
import runtime.reactive.mutableProperty

class SpaceSelectedReviewVmImpl : SpaceSelectedReviewVm {
  override val selectedReview: MutableProperty<CodeReviewWithCount?> = mutableProperty(null)
}