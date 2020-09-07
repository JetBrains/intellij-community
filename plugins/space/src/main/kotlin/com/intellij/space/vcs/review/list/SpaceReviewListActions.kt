package com.intellij.space.vcs.review.list

import circlet.client.api.Navigator
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
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.list.SpaceReviewListDataKeys.REVIEWS_LIST_VM
import com.intellij.space.vcs.review.list.SpaceReviewListDataKeys.SELECTED_REVIEW
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.util.function.Supplier

class SpaceRefreshReviewsListAction : DumbAwareAction(SpaceBundle.messagePointer("action.refresh.reviews.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val listVm = e.getData(REVIEWS_LIST_VM)
    listVm?.refresh()
  }
}

class SpaceReviewOpenInBrowserAction : DumbAwareAction(SpaceBundle.messagePointer("action.go.to.review.text")) {
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Ide.External_link_arrow
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = e.getData(SELECTED_REVIEW) ?: return
    val review = data.review.resolve()
    val server = space.workspace.value!!.client.server
    val reviewLink = Navigator.p.project(review.project).review(review.number).absoluteHref(server)

    BrowserUtil.browse(reviewLink)
  }
}

class SpaceReviewAuthorActionGroup : ActionGroup() {
  override fun isDumbAware(): Boolean = true

  override fun update(e: AnActionEvent) {
    val data = e.getData(SELECTED_REVIEW) ?: return
    val review = data.review.resolve()

    // TODO: fix review created by Space service
    val profile = review.createdBy!!.resolve()
    e.presentation.text = profile.englishFullName()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val data = e?.getData(SELECTED_REVIEW) ?: return emptyArray()
    val review = data.review.resolve()
    val server = space.workspace.value!!.client.server

    val actions: MutableList<ActionGroup> = mutableListOf()
    actions += UserActionGroup(review.createdBy!!.resolve(), server)
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
      e.presentation.text = profile.englishFullName()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return arrayOf(
        GoToAction(SpaceBundle.messagePointer("action.go.to.chat.text"), Navigator.im.p2pChat(profile).absoluteHref(server)),
        GoToAction(SpaceBundle.messagePointer("action.go.to.profile.text"), Navigator.m.member(profile.username).absoluteHref(server))
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
