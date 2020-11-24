// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import circlet.code.api.ChangeInReview
import circlet.platform.api.TID
import circlet.platform.client.KCircletClient
import com.intellij.openapi.ListSelection
import com.intellij.space.vcs.review.details.ReviewCommitListItem
import runtime.reactive.MutableProperty
import runtime.reactive.Property

class SpaceDiffVmImpl(
  override val client: KCircletClient,
  override val reviewId: TID,
  override val reviewKey: String,
  override val projectKey: ProjectKey,
  override val selectedCommits: Property<List<ReviewCommitListItem>>,
  override val selectedChanges: MutableProperty<ListSelection<ChangeInReview>>,
) : SpaceDiffVm