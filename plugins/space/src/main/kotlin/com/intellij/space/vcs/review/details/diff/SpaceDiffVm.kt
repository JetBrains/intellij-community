// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import circlet.platform.api.TID
import circlet.platform.client.KCircletClient
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import com.intellij.space.vcs.review.details.diff.load.SpaceReviewDiffLoader
import runtime.reactive.Property

internal interface SpaceDiffVm {
  val client: KCircletClient
  val reviewKey: String
  val reviewId: TID
  val projectKey: ProjectKey

  val changesVm: Property<SpaceReviewChangesVm>

  val spaceReviewDiffLoader: SpaceReviewDiffLoader
  val participantVm: Property<SpaceReviewParticipantsVm?>
}