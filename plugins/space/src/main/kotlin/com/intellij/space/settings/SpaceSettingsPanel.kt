package com.intellij.space.settings

import circlet.client.api.Navigator
import circlet.client.api.englishFullName
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.cleanupUrl
import com.intellij.space.ui.resizeIcon
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.LifetimeSource
import libraries.klogging.logger
import platform.common.ProductName
import java.awt.BorderLayout
import java.awt.GridBagLayout
import javax.swing.*

val log = logger<SpaceSettingsPanel>()

class SpaceSettingsPanel :
  BoundConfigurable(ProductName, null),
  SearchableConfigurable,
  Disposable {

  private val uiLifetime = LifetimeSource()

  private val settings = SpaceSettings.getInstance()

  private val accountPanel = JPanel(BorderLayout())

  private val linkLabel: LinkLabel<Any> = LinkLabel<Any>(
    SpaceBundle.message("settings.panel.configure.git.ssh.keys.http.password.link"),
    AllIcons.Ide.External_link_arrow,
    null
  ).apply {
    iconTextGap = 0
    setHorizontalTextPosition(SwingConstants.LEFT)
  }

  init {
    space.loginState.forEach(uiLifetime) { st ->
      accountPanel.removeAll()
      accountPanel.add(createView(st), BorderLayout.NORTH)
      updateUi(st)
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
        linkLabel()
      }
    }
  }


  override fun dispose() {
    uiLifetime.terminate()
  }

  private fun createView(st: SpaceLoginState): JComponent {
    when (st) {
      is SpaceLoginState.Disconnected -> return buildLoginPanel(st) { server ->
        space.signInManually(server, uiLifetime, accountPanel)
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
            space.signOut()
          }
        }

        val namePanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP)).apply {
          add(JLabel(st.workspace.me.value.englishFullName()))
          add(serverComponent)
        }

        val avatarLabel = JLabel()
        SpaceUserAvatarProvider.getInstance().avatars.forEach(uiLifetime) { avatars ->
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

  private fun updateUi(st: SpaceLoginState) {
    when (st) {
      is SpaceLoginState.Connected -> {
        linkLabel.isVisible = true
        val profile = space.workspace.value?.me?.value ?: return
        val gitConfigPage = Navigator.m.member(profile.username).git.absoluteHref(st.server)
        linkLabel.setListener({ _, _ -> BrowserUtil.browse(gitConfigPage) }, null)
      }
      else -> {
        linkLabel.isVisible = false
        linkLabel.setListener(null, null)
      }
    }
  }

  companion object {
    fun openSettings(project: Project?) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, SpaceSettingsPanel::class.java)
    }
  }
}

