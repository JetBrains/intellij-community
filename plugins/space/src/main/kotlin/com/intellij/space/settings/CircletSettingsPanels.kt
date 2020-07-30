package com.intellij.space.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.panel.ComponentPanelBuilder.createCommentComponent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JTextField

internal fun buildLoginPanel(st: CircletLoginState.Disconnected,
                             popupMode: Boolean = false,
                             loginAction: (String) -> Unit
): DialogPanel {
  return panel {
    val serverField = JTextField(st.server, 30)

    val loginButton = JButton("Log In").apply {
      addActionListener {
        isEnabled = false
        var url = serverField.text
        url = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
        loginAction(url.removeSuffix("/"))
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
      val errorText = createCommentComponent(st.error, true).apply {
        foreground = JBColor.RED
      }

      buildRow(popupMode).cell {
        errorText(growX, pushX)
      }
    }
  }
}

private fun RowBuilder.buildRow(popupMode: Boolean): Row {
  if (popupMode) {
    return row {}
  }
  return row("") {}
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
