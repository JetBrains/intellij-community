package com.intellij.space.settings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.panel.ComponentPanelBuilder.createCommentComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JTextField

internal fun buildLoginPanel(st: SpaceLoginState.Disconnected,
                             popupMode: Boolean = false,
                             loginAction: (String) -> Unit
): DialogPanel {
  return panel {
    val serverField = JTextField(st.server, 30)

    val loginButton = JButton(SpaceBundle.message("login.panel.log.in.button")).apply {
      addActionListener {
        isEnabled = false
        var url = serverField.text
        url = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
        loginAction(url.removeSuffix("/"))
      }
    }

    row {
      cell(isFullWidth = true) {
        val jbLabel = JBLabel(SpaceBundle.message("login.panel.log.in.to.space.label"), UIUtil.ComponentStyle.LARGE).apply {
          font = JBFont.label().biggerOn(5.0f)
        }
        jbLabel()
      }
    }
    row(SpaceBundle.message("login.panel.organization.url.label")) {
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

internal fun buildConnectingPanel(st: SpaceLoginState.Connecting, cancelAction: () -> Unit): DialogPanel {
  return panel {
    val cancelButton = JButton(IdeBundle.message("button.cancel")).apply {
      addActionListener { cancelAction() }
    }
    row(SpaceBundle.message("login.panel.connecting.to.server.label", st.server)) {
      cancelButton()
    }
  }
}
