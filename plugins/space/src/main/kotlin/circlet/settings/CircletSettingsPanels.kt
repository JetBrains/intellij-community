package circlet.settings

import com.intellij.openapi.ui.*
import com.intellij.ui.layout.*
import javax.swing.*


internal fun buildLoginPanel(st: CircletLoginState.Disconnected, loginAction: (String) -> Unit): DialogPanel {
    return panel {
        val serverField = JTextField(st.server)

        val loginButton = JButton("Log In").apply {
            addActionListener {
                isEnabled = false
                loginAction(serverField.text)
            }
        }

        row("Organization URL:") {
            serverField()
        }
        row("") {
            loginButton()
        }
    }
}

internal fun buildConnectingPanel(st: CircletLoginState.Connecting, cancelAction: () -> Unit): DialogPanel {
    return panel {
        val cancelButton = JButton("Cancel").apply {
            addActionListener { cancelAction() }
        }
        row("Connection to ${st.server}\u2026") {
            cancelButton()
        }
    }
}
