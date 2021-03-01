// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.settings

import circlet.client.api.englishFullName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.promo.bigPromoBanner
import com.intellij.space.promo.fullPromoText
import com.intellij.space.promo.spaceLinkLabel
import com.intellij.space.ui.LoginComponents.buildConnectingPanel
import com.intellij.space.ui.LoginComponents.loginPanel
import com.intellij.space.ui.LoginComponents.separatorRow
import com.intellij.space.ui.cleanupUrl
import com.intellij.space.ui.resizeIcon
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.space.utils.SpaceUrls
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import libraries.klogging.logger
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.reactive.SequentialLifetimes
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

val log = logger<SpaceSettingsPanel>()

class SpaceSettingsPanel :
  BoundConfigurable(SpaceBundle.message("configurable.name"), null),
  SearchableConfigurable,
  LifetimedDisposable by LifetimedDisposableImpl() {
  private val sqLifetime = SequentialLifetimes(lifetime)

  override fun getId(): String = "settings.space"

  override fun createPanel(): DialogPanel = panel {
    val panelLifetime = sqLifetime.next()
    val accountPanel = BorderLayoutPanel()
    row {
      cell(isFullWidth = true) { accountPanel(pushX, growX) }
    }
    val separatorRow = separatorRow()
    val cloneTypeRow = row {
      cell(isFullWidth = true) {
        label(SpaceBundle.message("settings.panel.clone.repositories.with.label"))
        comboBox(EnumComboBoxModel(CloneType::class.java), SpaceSettings.getInstance()::cloneType)
        createConfigureHttpAndSshLink()().withLargeLeftGap()
      }
    }

    SpaceWorkspaceComponent.getInstance().loginState.forEach(panelLifetime) { loginState ->
      accountPanel.removeAll()
      accountPanel.addToCenter(createView(accountPanel, loginState))
      accountPanel.revalidate()
      accountPanel.repaint()

      val rowsVisibility = when (loginState) {
        is SpaceLoginState.Connected -> true
        else -> false
      }
      separatorRow.visible = rowsVisibility
      cloneTypeRow.visible = rowsVisibility
    }
  }

  private fun createView(wrapper: JComponent, st: SpaceLoginState): JComponent {
    when (st) {
      is SpaceLoginState.Disconnected -> return buildSettingsLoginPanel(st) { server ->
        SpaceWorkspaceComponent.getInstance().signInManually(server, lifetime, wrapper)
      }

      is SpaceLoginState.Connecting -> return buildConnectingPanel(st) {
        st.cancel()
      }

      is SpaceLoginState.Connected -> {
        val serverComponent = JLabel(cleanupUrl(st.server)).apply {
          foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
          font = font.deriveFont((font.size * 0.9).toFloat())
        }
        val logoutButton = JButton(SpaceBundle.message("settings.panel.log.out.button.text")).apply {
          addActionListener {
            SpaceWorkspaceComponent.getInstance().signOut()
          }
        }

        val namePanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP)).apply {
          add(JLabel(st.workspace.me.value.englishFullName())) // NON-NLS
          add(serverComponent)
        }

        val infoPanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP)).apply {
          add(namePanel)
          val logoutButtonPanel = BorderLayoutPanel().addToLeft(logoutButton).apply {
            border = JBUI.Borders.emptyTop(5)
          }
          add(logoutButtonPanel)
        }

        val avatarLabel = JLabel()
        SpaceUserAvatarProvider.getInstance().avatars.forEach(lifetime) { avatars ->
          avatarLabel.icon = resizeIcon(avatars.circle, 64)
        }
        return JPanel().apply {
          layout = MigLayout(LC().gridGap("${JBUI.scale(10)}", "0")
                               .insets("0", "0", "0", "0")
                               .fill()).apply {
            columnConstraints = "[][]"
          }
          add(avatarLabel, CC().pushY().alignY("center"))
          add(infoPanel, CC().push())
        }
      }
    }
  }

  private fun createConfigureHttpAndSshLink(): JComponent {
    val contentPanel = BorderLayoutPanel().apply {
      isOpaque = false
    }
    SpaceWorkspaceComponent.getInstance().workspace.forEach(lifetime) {
      contentPanel.removeAll()
      if (it != null) {
        val url = SpaceUrls.git(it.me.value.username)
        val browserLink = BrowserLink(SpaceBundle.message("settings.panel.configure.git.ssh.keys.http.password.link"), url)
        contentPanel.addToCenter(browserLink)
      }
      contentPanel.revalidate()
      contentPanel.repaint()
    }
    return contentPanel
  }

  companion object {
    fun openSettings(project: Project?) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, SpaceSettingsPanel::class.java)
    }
  }
}

internal fun buildSettingsLoginPanel(st: SpaceLoginState.Disconnected,
                                     loginAction: (String) -> Unit
): DialogPanel {
  return panel {
    loginPanel(st, isLoginActionDefault = false, withOrganizationsUrlLabel = true, loginAction)
    row {
      buildSettingsPromoPanel()()
    }
  }
}

private fun buildSettingsPromoPanel(): JComponent {
  return JPanel(null).apply {
    layout = MigLayout(LC().gridGap("${JBUI.scale(8)}", "${JBUI.scale(4)}")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[][]"
    }

    add(JLabel(resizeIcon(SpaceIcons.Main, 30)), CC().pushY().spanY(10).alignY("top"))

    val browserLink = spaceLinkLabel()
    add(browserLink, CC().pushX().wrap())
    add(fullPromoText(80), CC().pushX().wrap())
    bigPromoBanner()?.let {
      add(it, CC().pushX().wrap().gapTop("${JBUI.scale(8)}"))
    }
  }
}
