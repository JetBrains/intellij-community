// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.diff.util.Side
import libraries.coroutines.extra.Lifetimed

interface SpaceReviewCommentSubmitter: Lifetimed {
  suspend fun submitComment(side: Side, line: Int, text: String)
}
