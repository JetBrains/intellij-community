// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.actionSystem.DataKey

object SpaceReviewDataKeys {
  @JvmStatic
  internal val SELECTED_REVIEW_VM: DataKey<SpaceSelectedReviewVm> = DataKey.create("com.intellij.space.vcs.review.selected.vm")
}