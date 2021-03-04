// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.fullRow
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.promo.*
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.stats.SpaceStatsCounterCollector
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

internal fun prettyBorder() = JBUI.Borders.empty(16, 20, 4, 20)

internal fun buildLoginPanelWithPromo(
  state: SpaceLoginState.Disconnected,
  statsExplorePlace: SpaceStatsCounterCollector.ExplorePlace,
  statsLoginPlace: SpaceStatsCounterCollector.LoginPlace,
  packParent: () -> Unit,
  loginAction: (String) -> Unit
): JComponent {
  val wrapper = Wrapper().apply {
    val loginPanel = loginPanel(statsLoginPlace, state, loginAction).apply {
      border = prettyBorder()
    }
    val promoPanel = promoPanel(statsExplorePlace) {
      SpaceStatsCounterCollector.ADV_LOG_IN_LINK.log()
      setContent(loginPanel)
      packParent()
      repaint()
    }.apply {
      border = prettyBorder()
    }

    val content = if (state.server.isNotBlank() || state.error?.isNotBlank() == true) loginPanel else promoPanel
    setContent(content)
    repaint()
  }
  val borderLayoutPanel = BorderLayoutPanel().addToCenter(wrapper)
  toolbarPromoBanner()?.let { borderLayoutPanel.addToTop(it) }
  return borderLayoutPanel
}

private fun promoPanel(statsExplorePlace: SpaceStatsCounterCollector.ExplorePlace, loginAction: () -> Unit) = panel {
  fullRow { createSpaceByJetbrainsLabel()() }
  fullRow { promoText(if (SystemInfo.isMac) 60 else 52)() }
  fullRow {
    JButton(SpaceBundle.message("space.promo.explore.space.button")).apply {
      addActionListener {
        SpaceStatsCounterCollector.EXPLORE_SPACE.log(statsExplorePlace)
        BrowserUtil.browse(EXPLORE_SPACE_PROMO_URL)
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

private fun loginPanel(
  statsLoginPlace: SpaceStatsCounterCollector.LoginPlace,
  state: SpaceLoginState.Disconnected,
  loginAction: (String) -> Unit
) = panel {
  val serverField = organizationUrlTextField(state.server)
  val loginButton = loginButton(statsLoginPlace, default = true) {
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

private fun singUpLabel(): BrowserLink = BrowserLink(SpaceBundle.message("login.sign.up"), SIGN_UP_SPACE_URL).apply {
  addActionListener {
    SpaceStatsCounterCollector.SIGN_UP_LINK.log()
  }
}