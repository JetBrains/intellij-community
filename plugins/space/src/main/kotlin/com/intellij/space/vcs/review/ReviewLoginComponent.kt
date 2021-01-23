// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.list.SpaceReviewsListVmImpl
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime
import runtime.reactive.property.map

internal class ReviewLoginComponent(
  parentDisposable: Disposable,
  lifetime: Lifetime,
  project: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceRepos: Set<SpaceRepoInfo>
) {

  private val isLoggedIn = lifetime.map(SpaceWorkspaceComponent.getInstance().workspace) {
    it != null
  }

  val view = Wrapper().apply {
    background = UIUtil.getListBackground()
  }

  init {
    isLoggedIn.forEach(lifetime) { isLoggedIn ->
      if (!isLoggedIn) {
        val loginLabel = LinkLabel.create(SpaceBundle.message("action.com.intellij.space.actions.SpaceLoginAction.text")) {
          SpaceSettingsPanel.openSettings(null)
        }
        view.setContent(loginLabel)
      }
      else {
        val workspace = SpaceWorkspaceComponent.getInstance().workspace.value!!
        val client = workspace.client

        val reviewsListVm = SpaceReviewsListVmImpl(lifetime,
                                                   client,
                                                   spaceProjectInfo,
                                                   workspace.me)

        val reviewComponent = SpaceReviewComponent(parentDisposable,
                                                   project,
                                                   lifetime,
                                                   spaceProjectInfo,
                                                   spaceRepos,
                                                   workspace,
                                                   reviewsListVm,
                                                   SpaceSelectedReviewVmImpl(workspace, spaceProjectInfo))
        view.setContent(reviewComponent)
      }
      view.validate()
      view.repaint()
    }
  }
}
