// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.fullRow
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.util.NlsContexts
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.promo.*
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.ui.LoginComponents.errorText
import com.intellij.space.ui.LoginComponents.loginButton
import com.intellij.space.ui.LoginComponents.organizationUrlTextField
import com.intellij.ui.ComponentUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JButton
import javax.swing.JComponent

fun buildLoginPanelWithPromo(state: SpaceLoginState.Disconnected,
                             packParent: () -> Unit,
                             loginAction: (String) -> Unit): JComponent {
  val wrapper = Wrapper().apply {
    val loginPanel = loginPanel(state, loginAction).apply {
      border = JBUI.Borders.empty(16, 20, 0, 20)
    }
    val promoPanel = promoPanel {
      setContent(loginPanel)
      packParent()
      repaint()
    }.apply {
      border = JBUI.Borders.empty(16, 20, 0, 20)
    }

    val content = if (state.error?.isNotBlank() == true) loginPanel else promoPanel
    setContent(content)
    repaint()
  }
  val borderLayoutPanel = BorderLayoutPanel().addToCenter(wrapper)
  toolbarPromoBanner()?.let { borderLayoutPanel.addToTop(it) }
  return borderLayoutPanel
}

private fun promoPanel(loginAction: () -> Unit) = panel {
  fullRow { createSpaceByJetbrainsLabel()() }
  fullRow { fullPromoText(52)() }
  fullRow {
    JButton(SpaceBundle.message("space.promo.discover.space.button")).apply {
      addActionListener {
        BrowserUtil.browse(DISCOVER_SPACE_PROMO_URL)
      }
    }.apply {
      ComponentUtil.putClientProperty(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    }()
  }
  fullRow {
    grayTextLabel(SpaceBundle.message("login.already.have.account.label"))()
    loginLabel(loginAction)()
  }
}

private fun loginPanel(state: SpaceLoginState.Disconnected, loginAction: (String) -> Unit) = panel {
  val serverField = organizationUrlTextField(state.server)
  val loginButton = loginButton(default = true) {
    it.isEnabled = false
    loginAction(serverField.text.trim())
  }

  fullRow { label(SpaceBundle.message("login.panel.organization.url.label")) }
  fullRow { serverField() }
  fullRow { loginButton() }

  state.error?.let {
    val errorText = errorText(it)
    fullRow { errorText() }
  }

  fullRow {
    grayTextLabel(SpaceBundle.message("login.dont.have.account.label"))()
    singUpLabel()()
  }
}

private fun grayTextLabel(@NlsContexts.Label text: String) = JBLabel(text).apply {
  foreground = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES.fgColor
}

private fun loginLabel(loginAction: () -> Unit) = ActionLink(SpaceBundle.message("login.panel.log.in.button")) {
  loginAction()
}

private fun singUpLabel(): BrowserLink = BrowserLink(SpaceBundle.message("login.sign.up"), SIGN_UP_SPACE_URL)