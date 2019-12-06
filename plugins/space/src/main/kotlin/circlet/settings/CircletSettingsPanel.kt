package circlet.settings

import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.ui.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.components.labels.*
import com.intellij.ui.components.panels.*
import com.intellij.ui.layout.*
import com.intellij.util.ui.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import platform.common.*
import runtime.*
import runtime.reactive.*
import java.awt.*
import java.util.concurrent.*
import javax.swing.*

val log = logger<CircletSettingsPanel>()

class CircletSettingsPanel :
    BoundConfigurable(ProductName, null),
    SearchableConfigurable,
    Disposable {

    private val uiLifetime = LifetimeSource()
    private var loginState = mutableProperty(initialState())
    private fun initialState(): CircletLoginState {
        val workspace = circletWorkspace.workspace.value ?: return CircletLoginState.Disconnected("")
        return CircletLoginState.Connected(workspace.client.server, workspace)
    }

    private val settings = CircletSettings.getInstance()

    private val accountPanel = JPanel(BorderLayout())

    private val linkLabel: LinkLabel<Any> = LinkLabel<Any>("Configure Git SSH Keys & HTTP Password", AllIcons.Ide.External_link_arrow, null).apply {
        iconTextGap = 0
        setHorizontalTextPosition(SwingConstants.LEFT)
    }

    init {
        circletWorkspace.workspace.forEach(uiLifetime) { ws ->
            if (ws == null) {
                loginState.value = CircletLoginState.Disconnected(settings.serverSettings.server)
            }
            else {
                loginState.value = CircletLoginState.Connected(ws.client.server, ws)
            }
        }

        loginState.forEach(uiLifetime) { st ->
            accountPanel.removeAll()
            accountPanel.add(createView(st), BorderLayout.NORTH)
            updateUi(st)
            accountPanel.revalidate()
            accountPanel.repaint()
        }
    }

    override fun getId(): String = "settings.space"

    override fun createPanel(): DialogPanel = panel {
        row {
            cell(isFullWidth = true) { accountPanel(pushX, growX) }
            largeGapAfter()
        }
        row {
            cell(isFullWidth = true) {
                label("Clone repositories with:")
                comboBox(EnumComboBoxModel(CloneType::class.java), settings::cloneType)
                linkLabel()
            }
        }
    }


    override fun dispose() {
        uiLifetime.terminate()
    }

    private fun createView(st: CircletLoginState): JComponent {
        when (st) {
            is CircletLoginState.Disconnected -> return buildLoginPanel(st) { server ->
                signIn(server)
            }

            is CircletLoginState.Connecting -> return buildConnectingPanel(st) {
                st.lt.terminate()
                loginState.value = CircletLoginState.Disconnected(st.server)
            }

            is CircletLoginState.Connected -> {
                val serverComponent = JLabel(cleanupUrl(st.server)).apply {
                    foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
                }
                val logoutButton = JButton("Log Out").apply {
                    addActionListener {
                        circletWorkspace.signOut()
                        loginState.value = CircletLoginState.Disconnected(st.server)
                    }
                }

                val namePanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP)).apply {
                    add(JLabel(st.workspace.me.value.englishFullName()))
                    add(serverComponent)
                }

                val avatarLabel = JLabel()
                CircletUserAvatarProvider.getInstance().avatars.forEach(uiLifetime) { avatars ->
                    avatarLabel.icon = resizeIcon(avatars.circle, 50)
                }
                return JPanel(GridBagLayout()).apply {
                    var gbc = GridBag().nextLine().next().anchor(GridBag.LINE_START).insetRight(UIUtil.DEFAULT_HGAP)
                    add(avatarLabel, gbc)
                    gbc = gbc.next().weightx(1.0).anchor(GridBag.WEST)
                    add(namePanel, gbc)
                    gbc = gbc.nextLine().next().next().anchor(GridBag.WEST)
                    add(logoutButton, gbc)
                }
            }
        }
    }

    private fun updateUi(st: CircletLoginState) {
        when (st) {
            is CircletLoginState.Connected -> {
                linkLabel.isVisible = true
                val profile = circletWorkspace.workspace.value?.me?.value ?: return
                val gitConfigPage = Navigator.m.member(profile.username).git.absoluteHref(st.server)
                linkLabel.setListener({ _, _ -> BrowserUtil.browse(gitConfigPage) }, null)
            }
            else -> {
                linkLabel.isVisible = false
                linkLabel.setListener(null, null)
            }
        }
    }

    private fun signIn(serverName: String) {
        launch(uiLifetime, Ui) {
            uiLifetime.usingSource { connectLt ->
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
                    log.error(th)
                    loginState.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, accountPanel)
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    companion object {
        fun openSettings(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CircletSettingsPanel::class.java)
        }
    }
}

