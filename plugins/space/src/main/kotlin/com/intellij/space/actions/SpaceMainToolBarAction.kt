package com.intellij.space.actions

import circlet.client.api.Navigator
import circlet.client.api.englishFullName
import circlet.platform.client.ConnectionStatus
import circlet.workspaces.Workspace
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.*
import com.intellij.space.ui.*
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.clone.SpaceCloneAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import libraries.coroutines.extra.Lifetime
import runtime.reactive.view
import java.awt.Component
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent

class SpaceMainToolBarAction : DumbAwareAction() {
  private val settings = SpaceSettings.getInstance()

  override fun update(e: AnActionEvent) {
    val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
    if (!isOnNavBar) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val connected = space.loginState.value is SpaceLoginState.Connected
    if (!connected) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true

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
    val workspace = space.workspace.value
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
        val view = createView(st, component)
        if (view == null) {
          popup.cancel()
          return@view
        }
        view.border = JBUI.Borders.empty(8, 12)
        wrapper.setContent(view)
        wrapper.repaint()
        if (st is SpaceLoginState.Disconnected) {
          popup.pack(true, true)
        }
      }
      popup.show(RelativePoint(component, Point(-wrapper.preferredSize.width + component.width, component.height)))
    }
  }

  private fun createView(st: SpaceLoginState, component: Component): JComponent? {
    return when (st) {
      is SpaceLoginState.Connected -> null

      is SpaceLoginState.Connecting -> buildConnectingPanel(st) {
        st.cancel()
      }

      is SpaceLoginState.Disconnected -> buildLoginPanel(st, true) { serverName ->
        space.signInManually(serverName, space.lifetime, component)
      }
    }
  }

  private fun buildMenu(workspace: Workspace, icon: Icon, project: Project): AccountsMenuListPopup {
    val host = settings.serverSettings.server
    val serverUrl = cleanupUrl(host)
    val menuItems: MutableList<AccountMenuItem> = mutableListOf()
    menuItems += AccountMenuItem.Account(
      workspace.me.value.englishFullName(),
      serverUrl,
      resizeIcon(icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
      listOf(browseAction(SpaceBundle.message("main.toolbar.open.server", serverUrl), host, true)))
    menuItems += AccountMenuItem.Action(SpaceBundle.message("action.com.intellij.space.vcs.clone.SpaceCloneAction.text"),
                                        { SpaceCloneAction.runClone(project) },
                                        showSeparatorAbove = true)
    val projectContext = SpaceProjectContext.getInstance(project)
    val context = projectContext.context.value
    if (context.isAssociatedWithSpaceRepository) {
      val descriptions = context.reposInProject.keys
      if (descriptions.size > 1) {
        menuItems += AccountMenuItem.Group(SpaceBundle.message("open.in.browser.group.code.reviews"), descriptions.map {
          val reviewsUrl = Navigator.p.project(it.key).reviews.absoluteHref(host)
          browseAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.project.name), reviewsUrl)
        }.toList())

        menuItems += AccountMenuItem.Group(SpaceBundle.message("open.in.browser.group.checklists"), descriptions.map {
          val checklistsUrl = Navigator.p.project(it.key).checklists().absoluteHref(host)
          browseAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.project.name), checklistsUrl)
        }.toList())

        menuItems += AccountMenuItem.Group(SpaceBundle.message("open.in.browser.group.issues"), descriptions.map {
          val issuesUrl = Navigator.p.project(it.key).issues().absoluteHref(host)
          browseAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.project.name), issuesUrl)
        }.toList())
      }
      else if (descriptions.isNotEmpty()) {
        val p = Navigator.p.project(descriptions.first().key)

        menuItems += browseAction(SpaceBundle.message("main.toolbar.code.reviews.action"), p.reviews.absoluteHref(host))
        menuItems += browseAction(SpaceBundle.message("main.toolbar.checklists.action"), p.checklists().absoluteHref(host))
        menuItems += browseAction(SpaceBundle.message("main.toolbar.issues.action"), p.issues().absoluteHref(host))
      }
    }
    menuItems += AccountMenuItem.Action(SpaceBundle.message("main.toolbar.settings.action"),
                                        { SpaceSettingsPanel.openSettings(project) },
                                        showSeparatorAbove = true)
    menuItems += AccountMenuItem.Action(SpaceBundle.message("main.toolbar.log.out.action"), { space.signOut() })

    return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
  }
}
