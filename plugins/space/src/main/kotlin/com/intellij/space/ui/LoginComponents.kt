// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.fullRow
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceLoginState
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JLabel

internal object LoginComponents {
  fun LayoutBuilder.loginPanel(
    state: SpaceLoginState.Disconnected,
    isLoginActionDefault: Boolean = false,
    withOrganizationsUrlLabel: Boolean = false,
    loginAction: (String) -> Unit
  ) {
    val serverField = organizationUrlTextField(
      state.server,
      30,
      SpaceBundle.message("login.input.field.organizations.url.placeholder")
    )

    val loginButton = loginButton(default = isLoginActionDefault, onClick = {
      it.isEnabled = false
      loginAction(serverField.text.trim())
    })

    fullRow { logIntoSpaceLabel()() }

    if (withOrganizationsUrlLabel) {
      row(SpaceBundle.message("login.panel.organization.url.label")) { serverField() }
      row("") { loginButton() }
    }
    else {
      fullRow { serverField() }
      fullRow { loginButton() }
    }

    state.error?.let {
      val errorText = errorText(it)
      if (withOrganizationsUrlLabel) {
        fullRow { errorText(growX, pushX) }
      }
      else {
        row("") {
          errorText(growX, pushX)
        }
      }
    }

    separatorRow().largeGapAfter()
  }

  internal fun organizationUrlTextField(
    @NlsSafe initialText: String = "",
    columns: Int = 0,
    @NlsContexts.StatusText placeholderText: String = SpaceBundle.message("login.input.field.organizations.url.placeholder")
  ): JBTextField {
    return JBTextField(initialText, columns).apply {
      emptyText.text = placeholderText
      accessibleContext.accessibleName = placeholderText
      FragmentedSettingsUtil.setupPlaceholderVisibility(this)
    }
  }

  internal fun loginButton(default: Boolean = false, onClick: (JButton) -> Unit): JButton =
    JButton(SpaceBundle.message("login.panel.log.in.button")).apply {
      addActionListener {
        onClick(this)
      }
      ComponentUtil.putClientProperty(this, DarculaButtonUI.DEFAULT_STYLE_KEY, default)
    }

  internal fun logIntoSpaceLabel(): JBLabel = JBLabel(SpaceBundle.message("login.panel.log.in.to.space.label"),
                                                      UIUtil.ComponentStyle.LARGE).apply {
    font = JBFont.label().biggerOn(3.0f)
  }

  internal fun errorText(@NlsContexts.DetailedDescription error: String): JLabel =
    ComponentPanelBuilder.createCommentComponent(error, true).apply {
      foreground = JBColor.RED
    }

  internal fun RowBuilder.separatorRow(): Row = row {
    SeparatorComponent(0, OnePixelDivider.BACKGROUND, null)()
  }

  internal fun buildConnectingPanel(st: SpaceLoginState.Connecting, withBorder: Boolean = false, cancelAction: () -> Unit): DialogPanel {
    return panel {
      val cancelButton = JButton(IdeBundle.message("button.cancel")).apply {
        addActionListener { cancelAction() }
      }
      row(SpaceBundle.message("login.panel.connecting.to.server.label", st.server)) {
        cancelButton()
      }
    }.apply {
      if (withBorder) {
        border = JBUI.Borders.empty(8, 12)
      }
    }
  }
}