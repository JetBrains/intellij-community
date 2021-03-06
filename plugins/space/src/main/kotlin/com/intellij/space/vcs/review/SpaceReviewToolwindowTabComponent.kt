// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.list.SpaceReviewsListVmImpl
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime

internal class SpaceReviewToolwindowTabComponent(
  parentDisposable: Disposable,
  lifetime: Lifetime,
  project: Project,
  workspace: Workspace,
  spaceProjectInfo: SpaceProjectInfo,
  spaceRepos: Set<SpaceRepoInfo>
) : BorderLayoutPanel() {
  init {
    background = UIUtil.getListBackground()
    val client = workspace.client

    val reviewsListVm = SpaceReviewsListVmImpl(
      lifetime,
      client,
      spaceProjectInfo,
      workspace.me
    )

    val reviewComponent = SpaceReviewComponent(
      parentDisposable,
      project,
      lifetime,
      spaceProjectInfo,
      spaceRepos,
      workspace,
      reviewsListVm,
      SpaceSelectedReviewVmImpl(workspace, spaceProjectInfo)
    )
    addToCenter(reviewComponent)
  }
}