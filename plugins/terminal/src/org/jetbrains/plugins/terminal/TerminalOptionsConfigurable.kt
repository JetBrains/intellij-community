// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.UIUtil.setEnabledRecursively
import org.jetbrains.plugins.terminal.TerminalBundle.message
import org.jetbrains.plugins.terminal.TerminalUtil.*
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.event.DocumentEvent

internal class TerminalOptionsConfigurable(private val project: Project) : BoundSearchableConfigurable(
  displayName = IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name"),
  helpTopic = "reference.settings.terminal",
  _id = "terminal"
) {
  override fun createPanel(): DialogPanel {
    val optionsProvider = TerminalOptionsProvider.instance
    val projectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project)
    val blockTerminalOptions = BlockTerminalOptions.getInstance()

    return panel {

      // This one takes a bit more than just setting a flag to apply,
      // so we bind the checkbox to a local var and then use its value in an onApply callback.
      var isGenOneTerminalEnabled = isGenOneTerminalEnabled()

      val environmentVarsButton = EnvironmentVariablesTextFieldWithBrowseButton()
      lateinit var newTerminalCheckbox: Cell<JBCheckBox>
      lateinit var newTerminalConfigurables: List<Cell<JComponent>>

      panel {

        fun updateNewUiEnabledState(isEnabled: Boolean) {
          newTerminalConfigurables.forEach {
            setEnabledRecursively(it.component, isEnabled)
          }
        }

        row {
          @Suppress("DialogTitleCapitalization") // New Terminal is used as a proper noun here, so it's a false positive
          newTerminalCheckbox = checkBox(message("settings.enable.new.ui"))
            .bindSelected(
              getter = { isGenOneTerminalEnabled },
              setter = { isGenOneTerminalEnabled = it }
            )
            .onChanged { newUiCheckbox ->
              updateNewUiEnabledState(newUiCheckbox.isSelected)
            }
            .onApply {
              setGenOneTerminalEnabled(project, isGenOneTerminalEnabled)
            }
          icon(AllIcons.General.Beta)
        }
        indent {
          buttonsGroup(title = message("settings.prompt.style")) {
            row {
              radioButton(message("settings.singleLine.prompt"), value = TerminalPromptStyle.SINGLE_LINE)
            }
            row {
              radioButton(message("settings.doubleLine.prompt"), value = TerminalPromptStyle.DOUBLE_LINE)
            }
            row {
              radioButton(message("settings.shell.prompt"), value = TerminalPromptStyle.SHELL)
              contextHelp(message("settings.shell.prompt.description"))
            }
          }.enabledIf(newTerminalCheckbox.selected)
            .bind(blockTerminalOptions::promptStyle)
          panel {
            newTerminalConfigurables = configurables(LocalTerminalCustomizer.EP_NAME.extensionList.mapNotNull { it.getBlockTerminalConfigurable(project) })
            updateNewUiEnabledState(isGenOneTerminalEnabled)
          }
        }
      }.visible(ExperimentalUI.isNewUI())

      group(message("settings.terminal.project.settings")) {
        row(message("settings.start.directory")) {
          textFieldWithBrowseButton(
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withDescription(message("settings.start.directory.browseFolder.description")),
            project,
          ).setupDefaultValue( { childComponent }, projectOptionsProvider.defaultStartingDirectory)
            .bindText(
              getter = { projectOptionsProvider.startingDirectory ?: projectOptionsProvider.defaultStartingDirectory ?: "" },
              setter = { projectOptionsProvider.startingDirectory = it },
            )
            .align(AlignX.FILL)
        }
        row(message("settings.environment.variables")) {
          cell(environmentVarsButton)
            .bind(
              componentGet = { component -> component.data },
              componentSet = { component, data -> component.data = data },
              MutableProperty(getter = { projectOptionsProvider.getEnvData() }, setter = { projectOptionsProvider.setEnvData(it) })
            )
            .align(AlignX.FILL)
        }
      }

      group(message("settings.terminal.application.settings")) {
        row(message("settings.shell.path")) {
          cell(textFieldWithHistoryWithBrowseButton(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withDescription(message("settings.terminal.shell.executable.path.browseFolder.description")),
            historyProvider = {
              detectShells(environmentVarsButton.envs)
            },
          )).setupDefaultValue({ childComponent.textEditor }, projectOptionsProvider.defaultShellPath())
            .bindText(projectOptionsProvider::shellPath)
            .align(AlignX.FILL)
        }
        row(message("settings.tab.name")) {
          textField()
            .bindText(optionsProvider::tabName)
            .align(AlignX.FILL)
        }
        row {
          checkBox(message("settings.show.separators.between.blocks"))
            .bindSelected(blockTerminalOptions::showSeparatorsBetweenBlocks)
            .visible(isGenTwoTerminalEnabled() && !isGenOneTerminalEnabled())
        }
        row {
          checkBox(message("settings.audible.bell"))
            .bindSelected(optionsProvider::audibleBell)
        }
        row {
          checkBox(message("settings.close.session.when.it.ends"))
            .bindSelected(optionsProvider::closeSessionOnLogout)
        }
        row {
          checkBox(message("settings.mouse.reporting"))
            .bindSelected(optionsProvider::mouseReporting)
        }
        row {
          checkBox(message("settings.copy.to.clipboard.on.selection"))
            .bindSelected(optionsProvider::copyOnSelection)
        }
        row {
          checkBox(message("settings.paste.on.middle.mouse.button.click"))
            .bindSelected(optionsProvider::pasteOnMiddleMouseButton)
        }
        row {
          checkBox(message("settings.override.ide.shortcuts"))
            .bindSelected(optionsProvider::overrideIdeShortcuts)
        }
        row {
          checkBox(message("settings.shell.integration"))
            .bindSelected(optionsProvider::shellIntegration)
        }
        row {
          checkBox(message("settings.highlight.hyperlinks"))
            .bindSelected(optionsProvider::highlightHyperlinks)
        }
        row {
          checkBox(message("settings.use.option.as.meta.key.label"))
            .bindSelected(optionsProvider::useOptionAsMetaKey)
            .visible(SystemInfo.isMac)
        }
        panel {
          configurables(LocalTerminalCustomizer.EP_NAME.extensionList.mapNotNull { it.getConfigurable(project) })
        }
        row(message("settings.cursor.shape.label")) {
          comboBox(
            items = TerminalUiSettingsManager.CursorShape.entries,
            renderer = textListCellRenderer { it?.text },
          ).bindItem(optionsProvider::cursorShape.toNullableProperty())
        }
      }
    }
  }
}

private fun Panel.configurables(configurables: List<UnnamedConfigurable>): List<Cell<JComponent>> {
  val result = mutableListOf<Cell<JComponent>>()
  for (configurable in configurables) {
    val component = configurable.createComponent() ?: continue
    row {
      result += cell(component).onApply {
        try {
          configurable.apply()
        }
        catch (e: Exception) {
          LOG.warn("Terminal configurable $configurable threw an exception on apply", e)
        }
      }.onReset {
        try {
          configurable.reset()
        }
        catch (e: Exception) {
          LOG.warn("Terminal configurable $configurable threw an exception on reset", e)
        }
      }.onIsModified {
        configurable.isModified
      }
    }
  }
  return result
}

private fun <T : JComponent> Cell<T>.setupDefaultValue(
  textComponent: T.() -> JTextField,
  defaultValue: String?,
): Cell<T> = apply {
  if (!defaultValue.isNullOrEmpty()) {
    val component = this.component.textComponent()
    component.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        component.foreground = if (component.text == defaultValue) getDefaultValueColor() else getChangedValueColor()
      }
    })
    if (component is JBTextField) {
      component.emptyText.text = defaultValue
    }
  }
}

private fun getDefaultValueColor(): Color {
  return findColorByKey("TextField.inactiveForeground", "nimbusDisabledText")
}

private fun getChangedValueColor(): Color {
  return findColorByKey("TextField.foreground")
}

private fun findColorByKey(vararg colorKeys: String): Color =
  colorKeys.firstNotNullOfOrNull { UIManager.getColor(it) } ?:
  throw IllegalStateException("Can't find color for keys " + colorKeys.contentToString())

private val LOG = logger<TerminalOptionsConfigurable>()
