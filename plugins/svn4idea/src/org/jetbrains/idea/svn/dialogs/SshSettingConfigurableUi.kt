// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.PortField
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.or
import com.intellij.ui.layout.selected
import com.intellij.util.EnvironmentUtil
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnConfiguration
import org.jetbrains.idea.svn.commandLine.SshTunnelRuntimeModule
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

internal class SshSettingConfigurableUi(project: Project) : ConfigurableUi<SvnConfiguration>, Disposable {

  private val svnConfiguration = SvnConfiguration.getInstance(project)

  private val executablePathTextField = JBTextField()
  private val executablePathField = TextFieldWithBrowseButton(executablePathTextField, null, this)
  private lateinit var userNameField: JBTextField
  private val portField = PortField()
  private lateinit var passwordChoice: JBRadioButton

  private lateinit var privateKeyChoice: JBRadioButton
  private val privateKeyPathField = TextFieldWithBrowseButton(null, this)

  private lateinit var subversionConfigChoice: JBRadioButton
  private lateinit var sshTunnelField: JBTextField
  private lateinit var updateTunnelButton: Cell<JButton>
  private val svnSshVariableLabel = JLabel()
  private lateinit var svnSshVariableField: JBTextField

  private var sshTunnelFromConfig: String? = null

  val panel = panel {
    val state = svnConfiguration.state

    val passwordPanel = panel {
      row(SvnBundle.message("ssh.settings.executable.label")) {
        cell(executablePathField)
          .align(AlignX.FILL)
          .bindText(state::sshExecutablePath)
      }

      row(SvnBundle.message("ssh.settings.user.name.label")) {
        userNameField = textField()
          .align(AlignX.FILL)
          .bindText(state::sshUserName)
          .component
      }

      row(SvnBundle.message("ssh.settings.port.label")) {
        cell(portField)
          .bindIntValue(state::sshPort)
      }
    }

    buttonsGroup {
      row {
        passwordChoice = radioButton(SvnBundle.message("ssh.settings.password.choice.title"), SvnConfiguration.SshConnectionType.PASSWORD)
          .component
      }

      row {
        privateKeyChoice =
          radioButton(SvnBundle.message("ssh.settings.private.key.choice.title"), SvnConfiguration.SshConnectionType.PRIVATE_KEY)
            .component
      }

      panel {
        indent {
          row(SvnBundle.message("ssh.settings.private.key.path.label")) {
            cell(privateKeyPathField)
              .align(AlignX.FILL)
              .bindText(state::sshPrivateKeyPath)
          }
        }
      }.enabledIf(privateKeyChoice.selected)


      passwordPanel.enabledIf(passwordChoice.selected or privateKeyChoice.selected)

      row {
        subversionConfigChoice = radioButton(SvnBundle.message("ssh.settings.subversion.config.choice.title"),
                                             SvnConfiguration.SshConnectionType.SUBVERSION_CONFIG)
          .selected(true)
          .component
      }

      panel {
        indent {
          row(SvnBundle.message("ssh.settings.tunnel.label")) {
            sshTunnelField = textField()
              .resizableColumn()
              .align(AlignX.FILL)
              .applyToComponent {
                emptyText.text = SshTunnelRuntimeModule.DEFAULT_SSH_TUNNEL_VALUE
              }.onChanged {
                updateSshTunnelDependentValues(sshTunnelField.getText())
              }.component

            updateTunnelButton = button(SvnBundle.message("ssh.settings.update.tunnel.title")) {
              val tunnel = sshTunnelField.getText()

              // remove tunnel from config in case it is null or empty
              svnConfiguration.setSshTunnelSetting(StringUtil.nullize(tunnel))
              setSshTunnelSetting(svnConfiguration.sshTunnelSetting)
            }
          }.layout(RowLayout.PARENT_GRID)

          row(svnSshVariableLabel) {
            svnSshVariableField = textField()
              .align(AlignX.FILL)
              .applyToComponent { isEditable = false }
              .component
            cell()
          }.layout(RowLayout.PARENT_GRID)
        }
      }.enabledIf(subversionConfigChoice.selected)
    }.bind(state::sshConnectionType)
  }

  init {
    registerBrowseDialog(executablePathField, SvnBundle.message("dialog.title.ssh.settings.browse.executable"))
    registerBrowseDialog(privateKeyPathField, SvnBundle.message("dialog.title.ssh.settings.browse.private.key"))

    setUpdateTunnelButtonEnabled(sshTunnelField.getText())
  }

  override fun reset(settings: SvnConfiguration) {
    panel.reset()

    setSshTunnelSetting(settings.sshTunnelSetting)
  }

  override fun isModified(settings: SvnConfiguration): Boolean {
    return panel.isModified()
  }

  override fun apply(settings: SvnConfiguration) {
    panel.apply()
  }

  override fun getComponent(): JComponent {
    return panel
  }

  override fun dispose() {
  }

  private fun registerBrowseDialog(component: TextFieldWithBrowseButton, dialogTitle: @NlsContexts.DialogTitle String) {
    component.addBrowseFolderListener(svnConfiguration.project,
                                      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(dialogTitle))
  }

  private fun setSshTunnelSetting(tunnelSetting: String) {
    sshTunnelFromConfig = tunnelSetting
    sshTunnelField.setText(tunnelSetting)

    updateSshTunnelDependentValues(tunnelSetting)
  }

  private fun updateSshTunnelDependentValues(tunnelSetting: @NlsSafe String?) {
    val svnSshVariableName = SshTunnelRuntimeModule.getSvnSshVariableName(
      if (StringUtil.isEmpty(tunnelSetting)) SshTunnelRuntimeModule.DEFAULT_SSH_TUNNEL_VALUE else tunnelSetting)
    val svnSshVariableValue = EnvironmentUtil.getValue(svnSshVariableName) ?: ""

    svnSshVariableLabel.setText("$svnSshVariableName:")
    svnSshVariableField.setText(svnSshVariableValue)

    val isSvnSshVariableNameInTunnel = !StringUtil.isEmpty(svnSshVariableName)
    svnSshVariableLabel.isVisible = isSvnSshVariableNameInTunnel
    svnSshVariableField.isVisible = isSvnSshVariableNameInTunnel

    executablePathTextField.emptyText.text = SshTunnelRuntimeModule.getExecutablePath(tunnelSetting)

    setUpdateTunnelButtonEnabled(tunnelSetting)
  }

  private fun setUpdateTunnelButtonEnabled(tunnelSetting: String?) {
    updateTunnelButton.enabled(tunnelSetting != sshTunnelFromConfig)
  }
}
