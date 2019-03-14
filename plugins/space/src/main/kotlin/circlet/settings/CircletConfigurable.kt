package circlet.settings

import circlet.client.api.*
import circlet.components.*
import circlet.messages.*
import circlet.platform.api.oauth.*
import circlet.workspaces.*
import com.intellij.openapi.options.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import klogging.*
import runtime.*
import runtime.async.*
import runtime.reactive.*
import java.awt.*
import javax.swing.*
import javax.swing.Action.*

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

    private val panel = JPanel(FlowLayout())

    init {
        circletWorkspace.workspace.forEach(uiLifetime) { ws ->
            if (ws == null) {
                state.value = LoginState.Disconnected(circletSettings.settings.value.server, "")
            }
            else {
                state.value = LoginState.Connected(ws.client.server, ws)
            }
        }

        state.forEach(uiLifetime) { st ->
            panel.removeAll()
            when (st) {
                is LoginState.Disconnected -> {
                    val server = JTextField(20).apply {
                        text = st.server
                    }
                    panel.add(
                        JPanel(FlowLayout()).apply {
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
                    )
                }
                is LoginState.Connecting -> {
                    panel.add(
                        JPanel(FlowLayout()).apply {
                            add(JLabel("Connection to ${st.server}..."))
                            val connectButton = JButton("Cancel")
                            add(connectButton.apply {
                                addActionListener {
                                    st.lt.terminate()
                                    state.value = LoginState.Disconnected(st.server, null)
                                }
                            })
                        }
                    )
                }
                is LoginState.Connected -> {
                    panel.add(
                        JPanel(FlowLayout()).apply {
                            add(JLabel("Connected to ${st.server} as ${st.workspace.me.value.englishFullName()}"))
                            val connectButton = JButton("Disconnect")
                            add(connectButton.apply {
                                addActionListener {
                                    circletWorkspace.signOut()
                                    state.value = LoginState.Disconnected(st.server, null)
                                }
                            })
                        }
                    )
                }
            }
            panel.revalidate()
            panel.repaint()
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
        // do nothing.
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
        title = "Circlet"
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

