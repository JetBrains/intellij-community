// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.openapi.util.Key

internal object SpaceDiffKeys {
  val DIFF_REQUEST_DATA = Key.create<SpaceReviewDiffRequestData>("space.review.diff.request.data")
}