// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import circlet.platform.api.TID
import circlet.platform.client.KCircletClient
import com.intellij.openapi.ListSelection
import com.intellij.space.vcs.review.details.ReviewCommitListItem
import com.intellij.space.vcs.review.details.SpaceReviewChange
import runtime.reactive.MutableProperty
import runtime.reactive.Property

internal interface SpaceDiffVm {
  val client: KCircletClient
  val reviewKey: String
  val reviewId: TID
  val projectKey: ProjectKey

  val selectedCommits: Property<List<ReviewCommitListItem>>
  val selectedChanges: MutableProperty<ListSelection<SpaceReviewChange>>
}