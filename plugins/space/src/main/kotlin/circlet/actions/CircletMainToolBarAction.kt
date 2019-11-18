package circlet.actions

import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.ui.*
import circlet.ui.clone.*
import circlet.workspaces.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.*
import com.intellij.util.ui.*
import com.intellij.util.ui.cloneDialog.*
import icons.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.awt.*
import java.awt.event.*
import java.util.concurrent.*
import javax.swing.*

class CircletMainToolBarAction : DumbAwareAction(), CustomComponentAction{

    override fun update(e: AnActionEvent) {
        val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
        e.presentation.isEnabledAndVisible = isOnNavBar
        if (!isOnNavBar) return

        val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
        if (component is JBLabel) {
            val avatar = CircletUserAvatarProvider.getInstance().avatar.value
            val statusIcon = if (circletWorkspace.workspace.value?.client?.connectionStatus?.value is ConnectionStatus.Connected)
            CircletIcons.statusOnline else CircletIcons.statusOffline
            val layeredIcon = LayeredIcon(2).apply {
                setIcon(resizeIcon(avatar, 16), 0)
                setIcon(statusIcon, 1, SwingConstants.SOUTH_EAST)
            }
            component.icon = layeredIcon
        }
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return JBLabel(CircletUserAvatarProvider.getInstance().avatar.value).apply {
            text = " "
            addMouseListener(object :MouseAdapter(){
                override fun mouseClicked(e: MouseEvent?) {
                    val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, this@apply)
                    val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(this@apply)
                    actionPerformed(AnActionEvent.createFromInputEvent(e, place, presentation, dataContext, true, true))
                }
            })
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component = e.inputEvent.component
        val workspace = circletWorkspace.workspace.value
        if (workspace != null) {
            buildMenu(workspace, CircletUserAvatarProvider.getInstance().avatar.value, e.project!!)
                .showUnderneathOf(component)
        }
        else {
            val disconnected = CircletLoginState.Disconnected(CircletServerSettingsComponent.getInstance().settings.value.server)

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
            }
            popup.showUnderneathOf(component)
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
                buildLoginPanel(st) { serverName ->
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
        val url = CircletServerSettingsComponent.getInstance().settings.value.server
        val serverUrl = cleanupUrl(url)
        val menuItems: MutableList<AccountMenuItem> = mutableListOf()
        menuItems += AccountMenuItem.Account(
            workspace.me.value.englishFullName(),
            serverUrl,
            resizeIcon(icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize))
        menuItems += AccountMenuItem.Action("Clone Repository...",
                                             { CircletCloneAction.runClone(project) },
                                             showSeparatorAbove = true)
        menuItems += AccountMenuItem.Action("Open $serverUrl",
                                            { BrowserUtil.browse(url) },
                                            AllIcons.Ide.External_link_arrow,
                                            showSeparatorAbove = true)
        menuItems += AccountMenuItem.Action("Projects",
                                            { BrowserUtil.browse("${url.removeSuffix("/")}/p") },
                                            AllIcons.Ide.External_link_arrow)
        menuItems += AccountMenuItem.Action("Log Out...",
                                            { circletWorkspace.signOut() },
                                            showSeparatorAbove = true)

        return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
    }
}
