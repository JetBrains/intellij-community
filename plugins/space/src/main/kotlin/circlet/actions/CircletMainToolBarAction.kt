package circlet.actions

import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.ui.*
import circlet.ui.AccountMenuItem
import circlet.ui.AccountMenuPopupStep
import circlet.ui.AccountsMenuListPopup
import circlet.vcs.*
import circlet.vcs.clone.*
import circlet.workspaces.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.awt.*
import com.intellij.ui.components.panels.*
import com.intellij.util.ui.*
import com.intellij.util.ui.cloneDialog.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.awt.*
import java.util.concurrent.*
import javax.swing.*

class CircletMainToolBarAction : DumbAwareAction()  {
    private val settings = CircletSettings.getInstance()

    override fun update(e: AnActionEvent) {
        val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
        e.presentation.isEnabledAndVisible = isOnNavBar
        if (!isOnNavBar) return
        val avatars = CircletUserAvatarProvider.getInstance().avatars.value
        val isConnected = circletWorkspace.workspace.value?.client?.connectionStatus?.value is ConnectionStatus.Connected
        e.presentation.icon = if (isConnected) avatars.online else avatars.offline
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component = e.inputEvent.component
        val workspace = circletWorkspace.workspace.value
        if (workspace != null) {
            buildMenu(workspace, CircletUserAvatarProvider.getInstance().avatars.value.circle, e.project!!)
                .showUnderneathOf(component)
        }
        else {
            val disconnected = CircletLoginState.Disconnected(settings.serverSettings.server)

            val loginState: MutableProperty<CircletLoginState> = mutableProperty(disconnected)

            val wrapper = Wrapper()
            val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, wrapper)
                .setRequestFocus(true)
                .setFocusable(true)
                .createPopup()
            loginState.view(circletWorkspace.lifetime) { _: Lifetime, st: CircletLoginState ->
                val view = createView(st, loginState, component)
                if (view == null) {
                    popup.cancel()
                    return@view
                }
                view.border = JBUI.Borders.empty(8, 12)
                wrapper.setContent(view)
                wrapper.repaint()
                if (st is CircletLoginState.Disconnected) {
                    popup.pack(true, true)
                }
            }
            popup.show(RelativePoint(component, Point(-wrapper.preferredSize.width + component.width, component.height)))
        }
    }

    private fun createView(st: CircletLoginState, loginState: MutableProperty<CircletLoginState>, component: Component): JComponent? {
        return when (st) {
            is CircletLoginState.Connected -> {
                null
            }

            is CircletLoginState.Connecting -> {
                buildConnectingPanel(st) {
                    st.lt.terminate()
                    loginState.value = CircletLoginState.Disconnected(st.server)
                }
            }

            is CircletLoginState.Disconnected -> {
                buildLoginPanel(st, true) { serverName ->
                    login(serverName, loginState, component)
                }
            }
        }
    }

    private fun login(serverName: String, loginState: MutableProperty<CircletLoginState>, component: Component) {
        launch(circletWorkspace.lifetime, Ui) {
            circletWorkspace.lifetime.usingSource { connectLt ->
                try {
                    loginState.value = CircletLoginState.Connecting(serverName, connectLt)
                    when (val response = circletWorkspace.signIn(connectLt, serverName)) {
                        is OAuthTokenResponse.Error -> {
                            loginState.value = CircletLoginState.Disconnected(serverName, response.description)
                        }
                    }
                } catch (th: CancellationException) {
                    throw th
                } catch (th: Throwable) {
                    log.warn(th)
                    loginState.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, component)
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    private fun buildMenu(workspace: Workspace, icon: Icon, project: Project): AccountsMenuListPopup {
        val url = settings.serverSettings.server
        val serverUrl = cleanupUrl(url)
        val menuItems: MutableList<AccountMenuItem> = mutableListOf()
        menuItems += AccountMenuItem.Account(
            workspace.me.value.englishFullName(),
            serverUrl,
            resizeIcon(icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
            listOf(AccountMenuItem.Action("Open $serverUrl",
                                          { BrowserUtil.browse(url) },
                                          AllIcons.Ide.External_link_arrow,
                                          showSeparatorAbove = true)))
        menuItems += AccountMenuItem.Action("Clone Repository...",
                                            { CircletCloneAction.runClone(project) },
                                            showSeparatorAbove = true)
        val projectContext = CircletProjectContext.getInstance(project)
        val projectInfos = projectContext.projectDescriptions

        if (projectInfos != null) {
            val keys = projectInfos.second
            if (keys.size > 1) {
                val reviewActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/${key}/review") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Code Reviews", reviewActions)

                val planningActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/$key/checklists") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Checklists", planningActions)

                val issuesActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/$key/issues") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Issues", issuesActions)
            }
            else {
                val key = keys.first().projectKey.key

                menuItems += AccountMenuItem.Action("Code Reviews",
                                                    { BrowserUtil.browse("${url}/p/$key/review") },
                                                    AllIcons.Ide.External_link_arrow)
                menuItems += AccountMenuItem.Action("Checklists",
                                                    { BrowserUtil.browse("${url}/p/$key/checklists") },
                                                    AllIcons.Ide.External_link_arrow)
                menuItems += AccountMenuItem.Action("Issues",
                                                    { BrowserUtil.browse("${url}/p/$key/issues") },
                                                    AllIcons.Ide.External_link_arrow)
            }
        }
        menuItems += AccountMenuItem.Action("Settings...",
                                            { CircletSettingsPanel.openSettings(project) },
                                            showSeparatorAbove = true)
        menuItems += AccountMenuItem.Action("Log Out...", { circletWorkspace.signOut() })

        return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
    }
}

