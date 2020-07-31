package com.intellij.space.actions

import circlet.client.api.Navigator
import circlet.client.api.englishFullName
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.client.ConnectionStatus
import circlet.workspaces.Workspace
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFrame
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.space
import com.intellij.space.settings.*
import com.intellij.space.ui.*
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.clone.SpaceCloneAction
import com.intellij.ui.AppIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import runtime.Ui
import runtime.reactive.MutableProperty
import runtime.reactive.mutableProperty
import runtime.reactive.view
import java.awt.Component
import java.awt.Point
import java.util.concurrent.CancellationException
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

class SpaceMainToolBarAction : DumbAwareAction() {
  private val settings = SpaceSettings.getInstance()

  override fun update(e: AnActionEvent) {
    val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
    e.presentation.isEnabledAndVisible = isOnNavBar
    if (!isOnNavBar) return
    val avatars = SpaceUserAvatarProvider.getInstance().avatars.value
    val isConnected = space.workspace.value?.client?.connectionStatus?.value is ConnectionStatus.Connected
    e.presentation.icon = if (isConnected) avatars.online
    else avatars.offline

  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent.component
    val workspace = space.workspace.value
    if (workspace != null) {
      buildMenu(workspace, SpaceUserAvatarProvider.getInstance().avatars.value.circle, e.project!!)
        .showUnderneathOf(component)
    }
    else {
      val disconnected = SpaceLoginState.Disconnected(settings.serverSettings.server)

      val loginState: MutableProperty<SpaceLoginState> = mutableProperty(disconnected)

      val wrapper = Wrapper()
      val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, wrapper)
        .setRequestFocus(true)
        .setFocusable(true)
        .createPopup()
      loginState.view(space.lifetime) { _: Lifetime, st: SpaceLoginState ->
        val view = createView(st, loginState, component)
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

  private fun createView(st: SpaceLoginState, loginState: MutableProperty<SpaceLoginState>, component: Component): JComponent? {
    return when (st) {
      is SpaceLoginState.Connected -> {
        null
      }

      is SpaceLoginState.Connecting -> {
        buildConnectingPanel(st) {
          st.lt.terminate()
          loginState.value = SpaceLoginState.Disconnected(st.server)
        }
      }

      is SpaceLoginState.Disconnected -> {
        buildLoginPanel(st, true) { serverName ->
          login(serverName, loginState, component)
        }
      }
    }
  }

  private fun login(serverName: String, loginState: MutableProperty<SpaceLoginState>, component: Component) {
    launch(space.lifetime, Ui) {
      space.lifetime.usingSource { connectLt ->
        try {
          loginState.value = SpaceLoginState.Connecting(serverName, connectLt)
          when (val response = space.signIn(connectLt, serverName)) {
            is OAuthTokenResponse.Error -> {
              loginState.value = SpaceLoginState.Disconnected(serverName, response.description)
            }
          }
        }
        catch (th: CancellationException) {
          throw th
        }
        catch (th: Throwable) {
          com.intellij.space.settings.log.warn(th)
          loginState.value = SpaceLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
        }
        val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, component)
        AppIcon.getInstance().requestFocus(frame as IdeFrame?)
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
      listOf(browseAction("Open $serverUrl", host, true)))
    menuItems += AccountMenuItem.Action("Clone Repository...",
                                        { SpaceCloneAction.runClone(project) },
                                        showSeparatorAbove = true)
    val projectContext = SpaceProjectContext.getInstance(project)
    val context = projectContext.context.value
    if (context.isAssociatedWithSpaceRepository) {
      val descriptions = context.reposInProject.keys
      if (descriptions.size > 1) {
        menuItems += AccountMenuItem.Group("Code Reviews", descriptions.map {
          val reviewsUrl = Navigator.p.project(it.key).reviews.absoluteHref(host)
          browseAction("Open for ${it.project.name} project", reviewsUrl)
        }.toList())

        menuItems += AccountMenuItem.Group("Checklists", descriptions.map {
          val checklistsUrl = Navigator.p.project(it.key).checklists().absoluteHref(host)
          browseAction("Open for ${it.project.name} project", checklistsUrl)
        }.toList())

        menuItems += AccountMenuItem.Group("Issues", descriptions.map {
          val issuesUrl = Navigator.p.project(it.key).issues().absoluteHref(host)
          browseAction("Open for ${it.project.name} project", issuesUrl)
        }.toList())
      }
      else if (descriptions.size != 0) {
        val p = Navigator.p.project(descriptions.first().key)

        menuItems += browseAction("Code Reviews", p.reviews.absoluteHref(host))
        menuItems += browseAction("Checklists", p.checklists().absoluteHref(host))
        menuItems += browseAction("Issues", p.issues().absoluteHref(host))
      }
    }
    menuItems += AccountMenuItem.Action("Settings...",
                                        { SpaceSettingsPanel.openSettings(project) },
                                        showSeparatorAbove = true)
    menuItems += AccountMenuItem.Action("Log Out...", { space.signOut() })

    return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
  }
}
