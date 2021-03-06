// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import circlet.client.api.TD_MemberProfile
import circlet.client.api.englishFullName
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.review.SpaceReviewDataKeys.REVIEWS_LIST_VM
import com.intellij.space.vcs.review.SpaceReviewDataKeys.SELECTED_REVIEW
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.util.function.Supplier

class SpaceRefreshReviewsListAction : DumbAwareAction(SpaceBundle.messagePointer("action.refresh.reviews.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val listVm = e.getData(REVIEWS_LIST_VM) ?: return
    SpaceStatsCounterCollector.REFRESH_REVIEWS_ACTION.log(SpaceStatsCounterCollector.RefreshReviewsPlace.CONTEXT_MENU)
    listVm.refresh()
  }
}

class SpaceReviewOpenInBrowserAction : DumbAwareAction(SpaceBundle.messagePointer("action.go.to.review.text")) {
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Ide.External_link_arrow
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = e.getData(SELECTED_REVIEW) ?: return
    val review = data.review.resolve()
    val reviewLink = SpaceUrls.review(review.project, review.number)

    BrowserUtil.browse(reviewLink)
  }
}

class SpaceReviewAuthorActionGroup : ActionGroup() {
  override fun isDumbAware(): Boolean = true

  override fun update(e: AnActionEvent) {
    val data = e.getData(SELECTED_REVIEW) ?: let {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val review = data.review.resolve()

    val profile = review.createdBy?.resolve() ?: let {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true
    e.presentation.text = profile.englishFullName() // NON-NLS
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val data = e?.getData(SELECTED_REVIEW) ?: return emptyArray()
    val review = data.review.resolve()
    val server = SpaceWorkspaceComponent.getInstance().workspace.value!!.client.server

    val actions: MutableList<ActionGroup> = mutableListOf()
    review.createdBy?.resolve()?.let { author ->
      actions += UserActionGroup(author, server)
    }
    actions += review.participants.map { it.user.resolve() }
      .map { UserActionGroup(it, server) }.toList()
    return actions.toTypedArray()
  }

  class UserActionGroup(private val profile: TD_MemberProfile, private val server: String) : ActionGroup() {
    override fun isDumbAware(): Boolean = true

    override fun isPopup(): Boolean {
      return true
    }

    override fun update(e: AnActionEvent) {
      e.presentation.text = profile.englishFullName() // NON-NLS
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return arrayOf(
        GoToAction(SpaceBundle.messagePointer("action.go.to.chat.text"), SpaceUrls.p2pChat(profile)),
        GoToAction(SpaceBundle.messagePointer("action.go.to.profile.text"), SpaceUrls.member(profile.username))
      )
    }

  }

  class GoToAction(@NlsActions.ActionText text: @NotNull Supplier<@Nls String>, private val link: String) : DumbAwareAction(text) {
    override fun update(e: AnActionEvent) {
      e.presentation.icon = AllIcons.Ide.External_link_arrow
    }

    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(link)
  }
}
