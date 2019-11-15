package circlet.settings

import com.intellij.openapi.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.layout.*
import com.intellij.util.ui.*
import javax.swing.*


internal fun buildLoginPanel(st: CircletLoginState.Disconnected,
                             loginAction: (String) -> Unit): DialogPanel {
    return panel {
        val serverField = JTextField(st.server)

        val loginButton = JButton("Log In").apply {
            addActionListener {
                isEnabled = false
                loginAction(serverField.text)
            }
        }

        row {
            cell(isFullWidth = true) {
                val jbLabel = JBLabel("Log In to Space", UIUtil.ComponentStyle.LARGE).apply {
                    font = JBFont.label().biggerOn(5.0f)
                }
                jbLabel()
            }
        }
        row("Organization URL:") {
            serverField()
        }
        row("") {
            loginButton()
        }
        if (st.error != null) {
            val errorText = JBLabel(st.error, UIUtil.ComponentStyle.REGULAR, UIUtil.FontColor.BRIGHTER)
            row("Error") {
                errorText()
            }
        }
    }
}

internal fun buildConnectingPanel(st: CircletLoginState.Connecting, cancelAction: () -> Unit): DialogPanel {
    return panel {
        val cancelButton = JButton("Cancel").apply {
            addActionListener { cancelAction() }
        }
        row("Connecting to ${st.server}\u2026") {
            cancelButton()
        }
    }
}
