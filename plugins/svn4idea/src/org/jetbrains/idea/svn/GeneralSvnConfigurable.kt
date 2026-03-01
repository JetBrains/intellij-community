// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.Consumer
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier

internal class GeneralSvnConfigurable(private val project: Project) : BoundSearchableConfigurable(
  SvnConfigurable.getGroupDisplayName(),
  SvnConfigurable.HELP_ID,
  SvnConfigurable.ID
), NoScroll {

  private lateinit var configurationDirectoryText: TextFieldWithBrowseButton
  private lateinit var useCustomConfigurationDirectory: JBCheckBox

  override fun createPanel(): DialogPanel {
    val settings = SvnConfiguration.getInstance(project)
    lateinit var result: DialogPanel

    val commandLineClient = TextFieldWithBrowseButton(null, disposable)
    commandLineClient.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(SvnBundle.message("dialog.title.select.path.to.subversion.executable"))
      .withDescription(SvnBundle.message("label.select.path.to.subversion.executable")))
    configurationDirectoryText = TextFieldWithBrowseButton(null, disposable)

    result = panel {
      row(SvnBundle.message("label.path.to.svn.executable")) {
        val applicationSettings17 = SvnApplicationSettings.getInstance()
        cell(commandLineClient)
          .align(AlignX.FILL)
          .onReset {
            commandLineClient.setText(applicationSettings17.commandLinePath)
          }.onApply {
            applicationSettings17.commandLinePath = commandLineClient.getText().trim()
          }.onIsModified {
            applicationSettings17.commandLinePath != commandLineClient.getText().trim()
          }
      }

      row {
        checkBox(SvnBundle.message("command.line.interactive.mode.title"))
          .comment(SvnBundle.message("command.line.interactive.mode.description"))
          .bindSelected(settings::isRunUnderTerminal, settings::setRunUnderTerminal)
      }

      row {
        useCustomConfigurationDirectory = checkBox(SvnBundle.message("settings.use.custom.directory"))
          .onChanged {
            val configuration = SvnConfiguration.getInstance(project)
            val path = configuration.getConfigurationDirectory()
            if (!useCustomConfigurationDirectory.isSelected || path == null) {
              configurationDirectoryText.setText(SvnUtil.USER_CONFIGURATION_PATH.getValue().toString())
            }
            else {
              configurationDirectoryText.setText(path)
            }
          }.gap(RightGap.SMALL)
          .component

        cell(configurationDirectoryText)
          .align(AlignX.FILL)
          .enabledIf(useCustomConfigurationDirectory.selected)
      }

      row {
        button(SvnBundle.message("button.text.clear.authentication.cache")) {
          SvnAuthenticationNotifier.clearAuthenticationCache(project, result, configurationDirectoryText.getText())
        }.commentRight(SvnBundle.message("label.text.delete.stored.credentials"))
          .align(AlignY.BOTTOM)
      }.resizableRow()
    }

    configurationDirectoryText.addActionListener { _ ->
      @NonNls val path = configurationDirectoryText.getText().trim()
      SvnConfigurable.selectConfigurationDirectory(path, Consumer { s: String? -> configurationDirectoryText.setText(s) }, project, result)
    }

    return result
  }

  override fun apply() {
    val settings = SvnConfiguration.getInstance(project)
    val applicationSettings17 = SvnApplicationSettings.getInstance()
    val oldCommandLinePath = applicationSettings17.commandLinePath

    super.apply()

    settings.setConfigurationDirParameters(!useCustomConfigurationDirectory.isSelected, configurationDirectoryText.getText())

    val vcs17 = SvnVcs.getInstance(project)
    val isClientValid = vcs17.checkCommandLineVersion()
    if (!project.isDefault && isClientValid && oldCommandLinePath != applicationSettings17.commandLinePath) {
      vcs17.invokeRefreshSvnRoots()
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }
  }

  override fun isModified(): Boolean {
    val settings = SvnConfiguration.getInstance(project)
    if (settings.isUseDefaultConfiguration == useCustomConfigurationDirectory.isSelected
        || settings.getConfigurationDirectory() != configurationDirectoryText.getText().trim()) {
      return true
    }

    return super.isModified()
  }

  override fun reset() {
    super.reset()

    val settings = SvnConfiguration.getInstance(project)
    var path = settings.getConfigurationDirectory()
    if (settings.isUseDefaultConfiguration || path == null) {
      path = SvnUtil.USER_CONFIGURATION_PATH.getValue().toString()
    }
    configurationDirectoryText.setText(path)
    useCustomConfigurationDirectory.setSelected(!settings.isUseDefaultConfiguration)
  }
}
