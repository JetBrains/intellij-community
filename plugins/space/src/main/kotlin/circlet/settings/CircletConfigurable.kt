package circlet.settings

import circlet.client.api.*
import circlet.components.*
import circlet.messages.*
import circlet.platform.api.oauth.*
import circlet.utils.*
import circlet.workspaces.*
import com.intellij.openapi.options.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import libraries.klogging.*
import runtime.*
import runtime.async.*
import runtime.reactive.*
import java.awt.*
import javax.swing.*
import javax.swing.Action.*
import java.awt.FlowLayout
import javax.swing.event.*


val log = logger<CircletConfigurable>()

sealed class LoginState(val server: String) {
    class Disconnected(server: String, val error: String?) : LoginState(server)
    class Connected(server: String, val workspace: Workspace) : LoginState(server)
    class Connecting(server: String, val lt: LifetimeSource) : LoginState(server)
}

class CircletConfigurable : SearchableConfigurable {

    private val uiLifetime = LifetimeSource()

    var state = mutableProperty(initialState())

    private fun initialState(): LoginState {
        val workspace = circletWorkspace.workspace.value ?: return LoginState.Disconnected("", null)
        return LoginState.Connected(workspace.client.server, workspace)
    }

    private val panel = JPanel()

    init {
        panel.layout = BorderLayout()
        val settings = circletServerSettings.settings
        circletWorkspace.workspace.forEach(uiLifetime) { ws ->
            if (ws == null) {
                state.value = LoginState.Disconnected(settings.value.server, "")
            }
            else {
                state.value = LoginState.Connected(ws.client.server, ws)
            }
        }


        val connectionPanel = JPanel(FlowLayout())

        state.forEach(uiLifetime) { st ->
            connectionPanel.removeAll()
            connectionPanel.add(createView(st))
            panel.revalidate()
            panel.repaint()
        }

        panel.add(connectionPanel)
    }

    private fun createView(st: LoginState): JComponent {
        when (st) {
            is LoginState.Disconnected -> {
                val server = JTextField(20).apply {
                    text = st.server
                }
                return JPanel(FlowLayout()).apply {
                        add(JLabel().apply {
                            text = "Server:"
                        })
                        add(server)
                        val connectButton = JButton("Sign In")
                        add(connectButton.apply {
                            addActionListener {
                                connectButton.isEnabled = false
                                val serverName = server.text
                                signIn(serverName)
                            }
                        })
                        if (st.error != null) {
                            add(JLabel().apply {
                                text = st.error
                            })
                        }
                    }
            }
            is LoginState.Connecting -> {
                return JPanel(FlowLayout()).apply {
                        add(JLabel("Connection to ${st.server}..."))
                        val connectButton = JButton("Cancel")
                        add(connectButton.apply {
                            addActionListener {
                                st.lt.terminate()
                                state.value = LoginState.Disconnected(st.server, null)
                            }
                        })
                    }
            }
            is LoginState.Connected -> {
                return JPanel(FlowLayout()).apply {
                        add(JLabel("Connected to ${st.server} as ${st.workspace.me.value.englishFullName()}"))
                        val connectButton = JButton("Disconnect")
                        add(connectButton.apply {
                            addActionListener {
                                circletWorkspace.signOut()
                                state.value = LoginState.Disconnected(st.server, null)
                            }
                        })
                    }
            }
        }
    }

    private fun signIn(serverName: String) {
        launch(uiLifetime, Ui) {
            uiLifetime.usingSource { connectLt ->
                try {
                    state.value = LoginState.Connecting(serverName, connectLt)
                    when (val response = circletWorkspace.signIn(connectLt, serverName)) {
                        is OAuthTokenResponse.Error -> {
                            state.value = LoginState.Disconnected(serverName, response.description)
                        }
                    }
                }
                catch (th: Throwable) {
                    log.error(th)
                    state.value = LoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, panel)
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun getId(): String = "circlet.settings.connection"

    override fun getDisplayName(): String = CircletBundle.message("connection-configurable.display-name")

    override fun apply() {
    }

    override fun reset() {
    }

    override fun createComponent(): JComponent? = panel

    override fun disposeUIResources() {
        uiLifetime.terminate()
    }
}

data class CircletServerSettings(
    var enabled: Boolean = false,
    var server: String = ""
)

class LoginDialogWrapper : DialogWrapper(true) {

    val server = JTextField(20).apply {
        text = "http://localhost:8000"
    }

    init {
        init()
        myOKAction.putValue(NAME, "Connect")
        title = ProductName
    }

    override fun createCenterPanel(): JComponent? {
        return JPanel(FlowLayout()).apply {
            add(JLabel().apply {
                text = "Server:"
            })
            add(server)
            pack()
        }
    }
}
