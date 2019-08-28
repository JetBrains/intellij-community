package circlet.settings

import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import com.intellij.openapi.*
import com.intellij.openapi.options.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.components.panels.*
import com.intellij.util.*
import com.intellij.util.ui.*
import icons.*
import libraries.klogging.*
import platform.common.*
import runtime.*
import runtime.async.*
import runtime.reactive.*
import java.awt.*
import javax.swing.*

val log = logger<CircletConfigurable>()

class CircletConfigurable : ConfigurableBase<CircletSettingUi, CircletServerSettings>("settings.space",
                                                                                      ProductName,
                                                                                      null) {
    override fun getSettings() = circletServerSettings.settings.value

    override fun createUi() = CircletSettingUi()
}

class CircletSettingUi : ConfigurableUi<CircletServerSettings>, Disposable {
    private val uiLifetime = LifetimeSource()
    private var state = mutableProperty(initialState())

    private val panel = JPanel(BorderLayout())

    private fun initialState(): CircletLoginState {
        val workspace = circletWorkspace.workspace.value ?: return CircletLoginState.Disconnected("", null)
        return CircletLoginState.Connected(workspace.client.server, workspace)
    }

    init {
        val settings = circletServerSettings.settings
        circletWorkspace.workspace.forEach(uiLifetime) { ws ->
            if (ws == null) {
                state.value = CircletLoginState.Disconnected(settings.value.server, "")
            }
            else {
                state.value = CircletLoginState.Connected(ws.client.server, ws)
            }
        }

        state.forEach(uiLifetime) { st ->
            panel.removeAll()
            panel.add(createView(st), BorderLayout.NORTH)
            panel.revalidate()
            panel.repaint()
        }
    }

    private fun createView(st: CircletLoginState): JComponent {
        when (st) {
            is CircletLoginState.Disconnected -> {
                val serverField = JTextField(st.server, 30)
                val loginButton = JButton("Log In").apply {
                    addActionListener {
                        isEnabled = false
                        signIn(serverField.text)
                    }
                }

                val panel = JPanel(GridBagLayout())
                var gbc = GridBag().nextLine().next().setDefaultAnchor(GridBag.LINE_START)
                panel.add(JLabel("Organization URL:"), gbc)
                gbc = gbc.next().insetLeft(UIUtil.DEFAULT_HGAP)
                panel.add(serverField, gbc)
                gbc = gbc.next().insetLeft(UIUtil.DEFAULT_HGAP).weightx(1.0)
                panel.add(loginButton, gbc)
                if (st.error != null) {
                    gbc = gbc.nextLine().next().next().insets(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, 0, 0)
                        .coverLine(2).weightx(1.0)
                    val errorLabel = JLabel(st.error).apply {
                        foreground = SimpleTextAttributes.ERROR_ATTRIBUTES.fgColor
                    }
                    panel.add(errorLabel, gbc)
                }

                return panel
            }
            is CircletLoginState.Connecting -> {
                return JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                    add(JLabel("Connection to ${st.server}\u2026"))
                    val connectButton = JButton("Cancel")
                    add(connectButton.apply {
                        addActionListener {
                            st.lt.terminate()
                            state.value = CircletLoginState.Disconnected(st.server, null)
                        }
                    })
                }
            }
            is CircletLoginState.Connected -> {
                val serverComponent = JLabel(st.server.removePrefix("https://").removePrefix("http://")).apply {
                    foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
                }
                val logoutButton = JButton("Log Out").apply {
                    addActionListener {
                        circletWorkspace.signOut()
                        state.value = CircletLoginState.Disconnected(st.server, null)
                    }
                }

                val namePanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP)).apply {
                    add(JLabel(st.workspace.me.value.englishFullName()))
                    add(serverComponent)
                }

                return JPanel(GridBagLayout()).apply {
                    // TODO: load real user icon
                    var gbc = GridBag().nextLine().next().anchor(GridBag.LINE_START).insetRight(UIUtil.DEFAULT_HGAP)
                    add(JLabel(IconUtil.scale(CircletIcons.mainIcon, null, 3.5f)), gbc)
                    gbc = gbc.next().weightx(1.0).anchor(GridBag.WEST)
                    add(namePanel, gbc)
                    gbc = gbc.nextLine().next().next().anchor(GridBag.WEST)
                    add(logoutButton, gbc)
                }
            }
        }
    }

    private fun signIn(serverName: String) {
        launch(uiLifetime, Ui) {
            uiLifetime.usingSource { connectLt ->
                try {
                    state.value = CircletLoginState.Connecting(serverName, connectLt)
                    when (val response = circletWorkspace.signIn(connectLt, serverName)) {
                        is OAuthTokenResponse.Error -> {
                            state.value = CircletLoginState.Disconnected(serverName, response.description)
                        }
                    }
                }
                catch (th: Throwable) {
                    log.error(th)
                    state.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, panel)
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    override fun isModified(settings: CircletServerSettings) = false

    override fun apply(settings: CircletServerSettings) {}

    override fun reset(settings: CircletServerSettings) {}

    override fun getComponent() = panel

    override fun dispose() = uiLifetime.terminate()
}

