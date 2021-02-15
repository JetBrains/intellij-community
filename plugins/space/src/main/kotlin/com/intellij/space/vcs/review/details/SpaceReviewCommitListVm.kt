// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property

internal interface SpaceReviewCommitListVm : Lifetimed {
  val commits: Property<List<SpaceReviewCommitListItem>>
}