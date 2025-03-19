// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.application.options.EditorFontsConstants
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.FontComboBox
import com.intellij.ui.FontInfoRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.selectedValueIs
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle.message
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import org.jetbrains.plugins.terminal.settings.TerminalOsSpecificOptions
import java.awt.Color
import java.util.*
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
const val TERMINAL_CONFIGURABLE_ID: String = "terminal"

internal class TerminalOptionsConfigurable(private val project: Project) : BoundSearchableConfigurable(
  displayName = IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name"),
  helpTopic = "reference.settings.terminal",
  _id = TERMINAL_CONFIGURABLE_ID
) {
  override fun createPanel(): DialogPanel {
    val optionsProvider = TerminalOptionsProvider.instance
    val osSpecificOptions = TerminalOsSpecificOptions.getInstance()
    val projectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project)
    val blockTerminalOptions = BlockTerminalOptions.getInstance()

    return panel {
      lateinit var terminalEngineComboBox: ComboBox<TerminalEngine>

      panel {
        row {
          val values = if (TerminalUtil.isGenOneTerminalOptionVisible()
                           // Normally, New Terminal can't be enabled if 'getGenOneTerminalVisibilityValue' is false.
                           // But if it is enabled for some reason (for example, the corresponding registry key was switched manually),
                           // show this option as well to avoid the errors.
                           || optionsProvider.terminalEngine == TerminalEngine.NEW_TERMINAL) {
            listOf(TerminalEngine.REWORKED, TerminalEngine.CLASSIC, TerminalEngine.NEW_TERMINAL)
          }
          else listOf(TerminalEngine.REWORKED, TerminalEngine.CLASSIC)

          val renderer = listCellRenderer<TerminalEngine?> {
            text(value?.presentableName ?: "")
            if (value == TerminalEngine.REWORKED) {
              icon(AllIcons.General.Beta)
            }
          }

          terminalEngineComboBox = comboBox(values, renderer)
            .label(message("settings.terminal.engine"))
            .bindItem(optionsProvider::terminalEngine.toNullableProperty())
            .component
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
          }.bind(blockTerminalOptions::promptStyle)

          panel {
            configurables(LocalTerminalCustomizer.EP_NAME.extensionList.mapNotNull { it.getBlockTerminalConfigurable(project) })
          }

        }.visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.NEW_TERMINAL))

      }.visibleIf(newUiPredicate())

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
          cell(EnvironmentVariablesTextFieldWithBrowseButton())
            .bind(
              componentGet = { component -> component.data },
              componentSet = { component, data -> component.data = data },
              MutableProperty(getter = { projectOptionsProvider.getEnvData() }, setter = { projectOptionsProvider.setEnvData(it) })
            )
            .align(AlignX.FILL)
        }
      }

      group(message("settings.terminal.font.settings")) {
        var fontSettings = TerminalFontOptions.getInstance().getSettings()
        row(message("settings.font.name")) {
          cell(fontComboBox())
            .bind(
              componentGet = { comboBox -> comboBox.fontName },
              componentSet = {comboBox, value -> comboBox.fontName = value },
              MutableProperty(
                getter = { fontSettings.fontFamily },
                setter = { fontSettings = fontSettings.copy(fontFamily = it) },
              ).toNullableProperty()
            )
        }

        row {
          textField()
            .label(message("settings.font.size"))
            .columns(4)
            .bindText(
              getter = { fontSettings.fontSize.fontSizeToString() },
              setter = { fontSettings = fontSettings.copy(fontSize = it.parseFontSize()) },
            )
          textField()
            .label(message("settings.line.height"))
            .columns(4)
            .bindText(
              getter = { fontSettings.lineSpacing.spacingToString() },
              setter = { fontSettings = fontSettings.copy(lineSpacing = it.parseSpacing()) },
            )
          textField()
            .label(message("settings.column.width"))
            .columns(4)
            .bindText(
              getter = { fontSettings.columnSpacing.spacingToString() },
              setter = { fontSettings = fontSettings.copy(columnSpacing = it.parseSpacing()) },
            )
        }

        onApply {
          TerminalFontOptions.getInstance().setSettings(fontSettings)
        }
      }

      group(message("settings.terminal.application.settings")) {
        row(message("settings.shell.path")) {
          cell(textFieldWithHistoryWithBrowseButton(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withDescription(message("settings.terminal.shell.executable.path.browseFolder.description")),
            historyProvider = {
              // Use shells detector directly because this code is executed on backend.
              // But in any other cases, shell should be fetched from backend using TerminalShellsDetectorApi.
              TerminalShellsDetector.detectShells().map { shellInfo ->
                val filteredOptions = shellInfo.options.filter {
                  // Do not show login and interactive options in the UI.
                  // They anyway will be substituted implicitly in the shell starting logic.
                  // So, there is no need to specify them in the settings.
                  it != LocalTerminalStartCommandBuilder.INTERACTIVE_CLI_OPTION && !LocalTerminalDirectRunner.LOGIN_CLI_OPTIONS.contains(it)
                }
                val shellCommand = (listOf(shellInfo.path) + filteredOptions)
                ParametersListUtil.join(shellCommand)
              }
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
            .visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.REWORKED))
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
            .bindSelected(osSpecificOptions::copyOnSelection)
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

private fun Panel.configurables(configurables: List<UnnamedConfigurable>) {
  for (configurable in configurables) {
    val component = configurable.createComponent() ?: continue
    row {
      cell(component).onApply {
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

private fun newUiPredicate(): ComponentPredicate {
  return if (ExperimentalUI.isNewUI()) {
    ComponentPredicate.TRUE
  }
  else ComponentPredicate.FALSE
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

private fun fontComboBox(): FontComboBox = FontComboBox().apply {
  renderer = object : FontInfoRenderer() {
    override fun isEditorFont(): Boolean = true
  }
  isMonospacedOnly = true
}

private fun Float.fontSizeToString(): String = formatWithOneDecimalDigit()

private fun String.parseFontSize(): Float =
  try {
    toFloat().coerceIn(EditorFontsConstants.getMinEditorFontSize().toFloat()..EditorFontsConstants.getMaxEditorFontSize().toFloat())
  }
  catch (_: Exception) {
    EditorFontsConstants.getDefaultEditorFontSize().toFloat()
  }

private fun Float.spacingToString(): String = formatWithOneDecimalDigit()

private fun Float.formatWithOneDecimalDigit(): String = String.format(Locale.ROOT, "%.1f", this)

// We only have getMin/MaxEditorLineSpacing(), and nothing for column spacing,
// but using the same values for column spacing seems reasonable.
private fun String.parseSpacing(): Float =
  try {
    toFloat().coerceIn(EditorFontsConstants.getMinEditorLineSpacing()..EditorFontsConstants.getMaxEditorLineSpacing())
  }
  catch (_: Exception) {
    1.0f
  }

private val LOG = logger<TerminalOptionsConfigurable>()
