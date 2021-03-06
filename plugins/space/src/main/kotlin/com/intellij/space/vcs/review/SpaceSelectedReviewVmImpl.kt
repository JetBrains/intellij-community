// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.code.api.CodeReviewListItem
import circlet.workspaces.Workspace
import com.intellij.space.vcs.SpaceProjectInfo
import runtime.reactive.MutableProperty
import runtime.reactive.mutableProperty

internal class SpaceSelectedReviewVmImpl(
  override val workspace: Workspace,
  override val projectInfo: SpaceProjectInfo
) : SpaceSelectedReviewVm {
  override val selectedReview: MutableProperty<CodeReviewListItem?> = mutableProperty(null)
}