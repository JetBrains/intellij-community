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
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.klogging.logger
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

val log = logger<SpaceSettingsPanel>()

class SpaceSettingsPanel :
  BoundConfigurable(SpaceBundle.message("configurable.name"), null),
  SearchableConfigurable,
  LifetimedDisposable by LifetimedDisposableImpl() {
  private val settings = SpaceSettings.getInstance()

  private val accountPanel = BorderLayoutPanel()

  init {
    SpaceWorkspaceComponent.getInstance().loginState.forEach(lifetime) { st ->
      accountPanel.removeAll()
      accountPanel.addToCenter(createView(st))
      accountPanel.revalidate()
      accountPanel.repaint()
    }
  }

  override fun getId(): String = "settings.space"

  override fun createPanel(): DialogPanel = panel {
    row {
      cell(isFullWidth = true) { accountPanel(pushX, growX) }
      largeGapAfter()
    }
    row {
      cell(isFullWidth = true) {
        label(SpaceBundle.message("settings.panel.clone.repositories.with.label"))
        comboBox(EnumComboBoxModel(CloneType::class.java), settings::cloneType)
        createConfigureHttpAndSshLink()()
      }
    }
  }

  private fun createView(st: SpaceLoginState): JComponent {
    when (st) {
      is SpaceLoginState.Disconnected -> return buildLoginPanel(st) { server ->
        SpaceWorkspaceComponent.getInstance().signInManually(server, lifetime, accountPanel)
      }

      is SpaceLoginState.Connecting -> return buildConnectingPanel(st) {
        st.cancel()
      }

      is SpaceLoginState.Connected -> {
        val serverComponent = JLabel(cleanupUrl(st.server)).apply {
          foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
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

        val avatarLabel = JLabel()
        SpaceUserAvatarProvider.getInstance().avatars.forEach(lifetime) { avatars ->
          avatarLabel.icon = resizeIcon(avatars.circle, 50)
        }
        return JPanel(GridBagLayout()).apply {
          var gbc = GridBag().nextLine().next().anchor(GridBag.LINE_START).insetRight(UIUtil.DEFAULT_HGAP)
          add(avatarLabel, gbc)
          gbc = gbc.next().weightx(1.0).anchor(GridBag.WEST)
          add(namePanel, gbc)
          gbc = gbc.nextLine().next().next().anchor(GridBag.WEST)
          add(logoutButton, gbc)
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
      contentPanel.isVisible = it != null
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

