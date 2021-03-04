// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import circlet.client.api.englishFullName
import circlet.platform.client.ConnectionStatus
import circlet.workspaces.Workspace
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.*
import com.intellij.space.ui.LoginComponents.buildConnectingPanel
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.clone.SpaceCloneAction
import com.intellij.space.vcs.review.SpaceShowReviewsAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import libraries.coroutines.extra.Lifetime
import runtime.reactive.view
import java.awt.Component
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent

class SpaceMainToolBarAction : DumbAwareAction(), RightAlignedToolbarAction {
  private val settings = SpaceSettings.getInstance()

  override fun update(e: AnActionEvent) {
    val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
    val isOnMainBar = e.place == ActionPlaces.MAIN_TOOLBAR
    if (!isOnNavBar && !isOnMainBar) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true

    val space = SpaceWorkspaceComponent.getInstance()
    val avatars = SpaceUserAvatarProvider.getInstance().avatars.value
    val isOnline = space.workspace.value?.client?.connectionStatus?.value is ConnectionStatus.Connected
    val isConnecting = space.loginState.value is SpaceLoginState.Connecting
    e.presentation.icon = when {
      isOnline -> avatars.online
      isConnecting -> AnimatedIcon.Default.INSTANCE
      else -> avatars.offline
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent.component
    val space = SpaceWorkspaceComponent.getInstance()
    val workspace = space.workspace.value
    SpaceStatsCounterCollector.OPEN_MAIN_TOOLBAR_POPUP.log(SpaceStatsCounterCollector.LoginState.convert(space.loginState.value))
    if (workspace != null) {
      buildMenu(workspace, SpaceUserAvatarProvider.getInstance().avatars.value.circle, e.project!!)
        .showUnderneathOf(component)
    }
    else {
      val wrapper = Wrapper()
      val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, wrapper)
        .setRequestFocus(true)
        .setFocusable(true)
        .createPopup()
      space.loginState.view(space.lifetime) { _: Lifetime, st: SpaceLoginState ->
        val view = createView(st, component) { popup.pack(true, true) }
        if (view == null) {
          popup.cancel()
          return@view
        }

        wrapper.setContent(view)
        wrapper.repaint()
        if (st is SpaceLoginState.Disconnected) {
          popup.pack(true, true)
        }
      }
      popup.show(RelativePoint(component, Point(-wrapper.preferredSize.width + component.width, component.height)))
    }
  }

  private fun createView(st: SpaceLoginState, component: Component, packPopup: () -> Unit): JComponent? {
    return when (st) {
      is SpaceLoginState.Connected -> null

      is SpaceLoginState.Connecting -> buildConnectingPanel(st, SpaceStatsCounterCollector.LoginPlace.MAIN_TOOLBAR, prettyBorder()) {
        st.cancel()
      }

      is SpaceLoginState.Disconnected -> buildLoginPanelWithPromo(
        st,
        SpaceStatsCounterCollector.ExplorePlace.MAIN_TOOLBAR,
        SpaceStatsCounterCollector.LoginPlace.MAIN_TOOLBAR,
        packPopup
      ) { serverName ->
        val space = SpaceWorkspaceComponent.getInstance()
        space.signInManually(serverName, space.lifetime, component)
      }
    }
  }

  private fun buildMenu(workspace: Workspace, icon: Icon, project: Project): AccountsMenuListPopup {
    val host = settings.serverSettings.server
    val serverUrl = cleanupUrl(host)
    val menuItems: MutableList<AccountMenuItem> = mutableListOf()
    menuItems += AccountMenuItem.Account(
      workspace.me.value.englishFullName(), // NON-NLS
      serverUrl,
      resizeIcon(icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
      listOf(browseAction(SpaceBundle.message("main.toolbar.open.server", serverUrl), host, true)))
    menuItems += AccountMenuItem.Action(SpaceBundle.message("action.com.intellij.space.vcs.clone.SpaceCloneAction.text"),
                                        { SpaceCloneAction.runClone(project) },
                                        showSeparatorAbove = true)
    val projectContext = SpaceProjectContext.getInstance(project)
    val context = projectContext.currentContext
    if (context.isAssociatedWithSpaceRepository) {
      val descriptions = context.reposInProject.keys
      menuItems += AccountMenuItem.Action(SpaceBundle.message("action.show.code.reviews.text"), {
        SpaceShowReviewsAction.showCodeReviews(project)
      })
      if (descriptions.size > 1) {
        menuItems += AccountMenuItem.Group(SpaceBundle.message("open.in.browser.group.checklists"), descriptions.map {
          val checklistsUrl = SpaceUrls.checklists(it.key)
          browseAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.project.name), checklistsUrl)
        }.toList())

        menuItems += AccountMenuItem.Group(SpaceBundle.message("open.in.browser.group.issues"), descriptions.map {
          val issuesUrl = SpaceUrls.issues(it.key)
          browseAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.project.name), issuesUrl)
        }.toList())
      }
      else if (descriptions.isNotEmpty()) {
        val projectKey = descriptions.first().key
        menuItems += browseAction(SpaceBundle.message("main.toolbar.checklists.action"), SpaceUrls.checklists(projectKey))
        menuItems += browseAction(SpaceBundle.message("main.toolbar.issues.action"), SpaceUrls.issues(projectKey))
      }
    }
    menuItems += AccountMenuItem.Action(SpaceBundle.message("main.toolbar.settings.action"),
                                        { SpaceSettingsPanel.openSettings(project) },
                                        showSeparatorAbove = true)
    menuItems += AccountMenuItem.Action(SpaceBundle.message("main.toolbar.log.out.action"), {
      SpaceWorkspaceComponent.getInstance().signOut(SpaceStatsCounterCollector.LogoutPlace.MAIN_TOOLBAR)
    })

    return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
  }
}
