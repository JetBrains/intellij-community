// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.codeWithMe.ClientId
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.client.sessions
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.colors.pages.ANSIColoredConsoleColorsPage
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.FontComboBox
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.enteredTextSatisfies
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueIs
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.render.fontInfoRenderer
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.system.OS
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle.message
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode.ALWAYS
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode.ONLY_PARAMETERS
import org.jetbrains.plugins.terminal.block.completion.feedback.TerminalCompletionFeedbackSurvey
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils
import org.jetbrains.plugins.terminal.block.feedback.askForFeedbackIfReworkedTerminalDisabled
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.ui.TerminalContrastRatio
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

@ApiStatus.Internal
const val TERMINAL_CONFIGURABLE_ID: String = "terminal"

internal class TerminalOptionsConfigurable(private val project: Project) : BoundSearchableConfigurable(
  displayName = IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name"),
  helpTopic = "reference.settings.terminal",
  _id = TERMINAL_CONFIGURABLE_ID
) {
  override fun createPanel(): DialogPanel {
    val optionsProvider = TerminalOptionsProvider.instance
    val projectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project)
    val blockTerminalOptions = BlockTerminalOptions.getInstance()

    return panel {
      lateinit var terminalEngineComboBox: ComboBox<TerminalEngine>
      val shellPathField: TextFieldWithHistoryWithBrowseButton = createShellPathField()

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
          }

          terminalEngineComboBox = comboBox(values, renderer)
            .label(message("settings.terminal.engine"))
            .bindItem(
              getter = { optionsProvider.terminalEngine },
              setter = {
                val oldEngine = optionsProvider.terminalEngine
                val newEngine = it!!
                optionsProvider.terminalEngine = newEngine
                askForFeedbackIfReworkedTerminalDisabled(project, oldEngine, newEngine)
              }
            )
            .component
        }

        group(message("terminal.command.completion")) {
          rowsRange {
            lateinit var completionEnabledCheckBox: JBCheckBox

            row {
              completionEnabledCheckBox = checkBox(message("terminal.command.completion.show"))
                .bindSelected(
                  getter = { optionsProvider.showCompletionPopupAutomatically },
                  setter = {
                    optionsProvider.showCompletionPopupAutomatically = it
                    if (!it) {
                      ApplicationManager.getApplication().invokeLater(
                        { TerminalFeedbackUtils.showFeedbackNotificationOnDemand(project, TerminalCompletionFeedbackSurvey::class) },
                        ModalityState.nonModal(), // show the notification after settings dialog is closed
                        project.disposed
                      )
                    }
                  },
                )
                .component
            }
            indent {
              buttonsGroup {
                row {
                  radioButton(message("terminal.command.completion.show.always"), value = ALWAYS)
                }
                row {
                  radioButton(message("terminal.command.completion.show.parameters"), value = ONLY_PARAMETERS)
                }
              }.bind(optionsProvider::commandCompletionShowingMode)
                .enabledIf(completionEnabledCheckBox.selected)
            }

            row {
              shortcutCombobox(
                labelText = message("terminal.command.completion.shortcut.trigger"),
                presets = listOf(getCtrlSpacePreset(project), TAB_SHORTCUT_PRESET),
                actionId = "Terminal.CommandCompletion.Gen2"
              )
            }
            row {
              shortcutCombobox(
                labelText = message("terminal.command.completion.shortcut.insert"),
                presets = listOf(ENTER_SHORTCUT_PRESET, TAB_SHORTCUT_PRESET),
                actionId = "Terminal.EnterCommandCompletion"
              )
            }
          }.visible(TerminalCommandCompletion.isEnabled())

          TerminalCloudCompletionSettingsProvider.getProvider()?.addSettingsRow(this)
        }.bottomGap(BottomGap.NONE)
          .visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.REWORKED)
                       .and(shellPathField.shellWithIntegrationSelected())
                       .and(ComponentPredicate.fromValue(AppModeAssertions.isMonolith())))

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
        var fontSettings = TerminalFontSettingsService.getInstance().getSettings()
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
          cell(fontComboBox())
            .label(message("settings.fallback.font.name"))
            .bind(
              componentGet = { comboBox -> comboBox.fontName },
              componentSet = {comboBox, value -> comboBox.fontName = value },
              MutableProperty(
                getter = { fontSettings.fallbackFontFamily },
                setter = { fontSettings = fontSettings.copy(fallbackFontFamily = it) },
              ).toNullableProperty()
            )
        }

        row {
          textField()
            .label(message("settings.font.size"))
            .columns(4)
            .bindText(
              getter = { fontSettings.fontSize.toFormattedString() },
              setter = { fontSettings = fontSettings.copy(fontSize = TerminalFontSize.parse(it)) },
            )
          textField()
            .label(message("settings.line.height"))
            .columns(4)
            .bindText(
              getter = { fontSettings.lineSpacing.toFormattedString() },
              setter = { fontSettings = fontSettings.copy(lineSpacing = TerminalLineSpacing.parse(it)) },
            )
          textField()
            .label(message("settings.column.width"))
            .columns(4)
            .bindText(
              getter = { fontSettings.columnSpacing.toFormattedString() },
              setter = { fontSettings = fontSettings.copy(columnSpacing = TerminalColumnSpacing.parse(it)) },
            )
        }

        row {
          cell(ActionLink(message("settings.colors")) { actionEvent ->
            ColorAndFontOptions.selectOrEditColor(
              DataManager.getInstance().getDataContext(actionEvent.source as? Component?),
              if (terminalEngineComboBox.selectedItem == TerminalEngine.REWORKED) {
                ANSIColoredConsoleColorsPage.getSearchableReworkedTerminalName()
              }
              else {
                ANSIColoredConsoleColorsPage.getSearchableClassicTerminalName()
              },
              ANSIColoredConsoleColorsPage.getSearchableName(),
            )
          })
        }.visibleIf(terminalEngineComboBox.selectedValueMatches {
          it == TerminalEngine.REWORKED ||
          it == TerminalEngine.CLASSIC
        })

        onApply {
          TerminalFontSettingsService.getInstance().setSettings(fontSettings)
        }
      }

      group(message("settings.terminal.application.settings")) {
        row(message("settings.shell.path")) {
          cell(shellPathField)
            .setupShellField(project)
            .align(AlignX.FILL)
            .component
        }
        row(message("settings.tab.name")) {
          textField()
            .bindText(optionsProvider::tabName)
            .align(AlignX.FILL)
        }
        row {
          val enforceContrastCheckbox = checkBox(message("settings.enforce.minimum.contrast.ratio"))
            .bindSelected(optionsProvider::enforceMinContrastRatio)
            .gap(RightGap.SMALL)

          fun parseRatio(text: String): TerminalContrastRatio {
            val float = text.toFloatOrNull()
            return if (float != null) TerminalContrastRatio.ofFloat(float) else TerminalContrastRatio.DEFAULT_VALUE
          }

          textField()
            .columns(4)
            .enabledIf(enforceContrastCheckbox.selected)
            .gap(RightGap.SMALL)
            .bindText(
              getter = { optionsProvider.minContrastRatio.toFormattedString() },
              setter = { optionsProvider.minContrastRatio = parseRatio(it) }
            )

          contextHelp(message("settings.enforce.minimum.contrast.ratio.description"))
        }.visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.REWORKED))
        row {
          checkBox(message("settings.show.separators.between.blocks"))
            .bindSelected(blockTerminalOptions::showSeparatorsBetweenBlocks)
            .visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.REWORKED)
                         .and(shellPathField.shellWithIntegrationSelected()))
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
          checkBox(ApplicationBundle.message("advanced.setting.terminal.escape.moves.focus.to.editor"))
            .bindSelected(
              getter = { AdvancedSettings.getBoolean("terminal.escape.moves.focus.to.editor") },
              setter = { AdvancedSettings.setBoolean("terminal.escape.moves.focus.to.editor", it) },
            )
        }
        row {
          checkBox(message("settings.copy.to.clipboard.on.selection"))
            .bindSelected(optionsProvider::copyOnSelection)
            .visible(isMac(project) || isWindows(project))
        }
        row {
          checkBox(message("settings.paste.on.middle.mouse.button.click"))
            .bindSelected(optionsProvider::pasteOnMiddleMouseButton)
        }
        row {
          checkBox(message("settings.override.ide.shortcuts"))
            .bindSelected(optionsProvider::overrideIdeShortcuts)
          cell(ActionLink(message("settings.configure.terminal.keybindings"), ActionListener { e ->
            val settings = DataManager.getInstance().getDataContext(e.getSource() as ActionLink?).getData(Settings.KEY)
            if (settings != null) {
              val configurable = settings.find("preferences.keymap")
              settings.select(configurable, "Terminal").doWhenDone(Runnable {
                // Remove once https://youtrack.jetbrains.com/issue/IDEA-212247 is fixed
                EdtExecutorService.getScheduledExecutorInstance().schedule(Runnable {
                  settings.select(configurable, "Terminal")
                }, 100, TimeUnit.MILLISECONDS)
              })
            }
          }).apply { toolTipText = message("settings.keymap.plugins.terminal") })
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
            .visible(isMac(project))
        }
        row {
          checkBox(message("settings.terminal.smart.command.handling"))
            .bindSelected(RunCommandUsingIdeUtil::isEnabled)
            .visibleIf(terminalEngineComboBox.selectedValueIs(TerminalEngine.CLASSIC)
                         .and(ComponentPredicate.fromValue(RunCommandUsingIdeUtil.isVisible)))
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

  private fun createShellPathField(): TextFieldWithHistoryWithBrowseButton {
    val shellPathField = textFieldWithHistoryWithBrowseButton(
      project,
      FileChooserDescriptorFactory.singleFile().withDescription(message("settings.terminal.shell.executable.path.browseFolder.description")),
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
    )
    shellPathField.childComponent.setEditor(object : BasicComboBoxEditor() {
      override fun createEditorComponent(): JTextField = JBTextField().also {
        it.border = null
      }
    })
    return shellPathField
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
    val updateForeground = {
      component.foreground = if (component.text == defaultValue) getDefaultValueColor() else getChangedValueColor()
    }
    component.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = updateForeground()
    })
    updateForeground()
    if (component is JBTextField) {
      component.emptyText.text = defaultValue
    }
  }
}

private fun Cell<TextFieldWithHistoryWithBrowseButton>.setupShellField(project: Project): Cell<TextFieldWithHistoryWithBrowseButton> = apply {
  val cell = this
  val textEditor: JTextField = this.component.childComponent.textEditor
  if (textEditor is JBTextField) {
    textEditor.emptyText.text = message("settings.shell.path.detecting.default")
  }
  val projectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project)
  val defaultShellPathRef = AtomicReference<String>()
  this.component.initOnShow("Terminal (default shell path detection)") {
    val defaultShellPath: String? = withContext(Dispatchers.Default) {
      try {
        projectOptionsProvider.defaultShellPath().also {
          defaultShellPathRef.set(it)
          LOG.debug { "Detected default shell path: $it" }
        }
      }
      catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        // the coroutine is alive => the exception was thrown by `projectOptionsProvider.defaultShellPath()`
        LOG.warn("Cannot determine default shell path", e)
        null
      }
    }
    if (textEditor is JBTextField) {
      textEditor.emptyText.clear()
    }
    if (defaultShellPath != null && textEditor.text.isEmpty()) {
      textEditor.text = defaultShellPath
    }
    cell.setupDefaultValue({ childComponent.textEditor }, defaultShellPath)
  }
  cell.bindText(getter = {
    projectOptionsProvider.shellPathWithoutDefault ?: defaultShellPathRef.get().orEmpty()
  }, setter = { value ->
    projectOptionsProvider.shellPathWithoutDefault = Strings.nullize(value, defaultShellPathRef.get())
  })
}

private fun newUiPredicate(): ComponentPredicate {
  return if (ExperimentalUI.isNewUI()) {
    ComponentPredicate.TRUE
  }
  else ComponentPredicate.FALSE
}

private fun TextFieldWithHistoryWithBrowseButton.shellWithIntegrationSelected(): ComponentPredicate {
  return childComponent.textEditor.enteredTextSatisfies { text ->
    isShellWithIntegration(text)
  }
}

private fun isShellWithIntegration(text: String): Boolean {
  val command = ParametersListUtil.parse(text, false, OS.CURRENT != OS.Windows)
  val shellPath = command.firstOrNull() ?: return false
  val shellName = PathUtil.getFileName(shellPath)

  return LocalShellIntegrationInjector.supportsBlocksShellIntegration(shellName, LocalEelDescriptor /* to be replaced with proper EelDescriptor */)
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
  renderer = fontInfoRenderer(true)
  isMonospacedOnly = true
}

/**
 * [TerminalOptionsConfigurable] is created on backend under local [ClientId].
 * But some options need to be shown depending on client OS.
 * So, it is a hack to check the client OS from the configurable code.
 */
private fun isMac(project: Project): Boolean {
  return getClientSystemInfo(project)?.macClient ?: SystemInfo.isMac
}

private fun isWindows(project: Project): Boolean {
  return getClientSystemInfo(project)?.windowsClient ?: SystemInfo.isWindows
}

private fun getClientSystemInfo(project: Project): ClientSystemInfo? {
  val clientId = project.sessions(ClientKind.CONTROLLER).singleOrNull()?.clientId ?: return null
  return ClientId.withExplicitClientId(clientId) {
    ClientSystemInfo.getInstance()
  }
}

private val LOG = logger<TerminalOptionsConfigurable>()

private fun Row.shortcutCombobox(
  @NlsContexts.Label labelText: String,
  presets: List<ShortcutPreset>,
  actionId: String,
) {
  val comboBox = comboBox(
    items = presets.map { ShortcutItem.Preset(it) } + ShortcutItem.Custom,
    renderer = textListCellRenderer { item ->
      when (item) {
        is ShortcutItem.Preset -> item.preset.text
        is ShortcutItem.Custom -> message("terminal.command.completion.shortcut.custom")
        null -> ""
      }
    }
  ).label(labelText)
    .bindItem(
      getter = {
        val currentShortcut = getActionShortcut(actionId)
        presets.find { it.shortcut == currentShortcut }?.let { ShortcutItem.Preset(it) } ?: ShortcutItem.Custom
      },
      setter = { item ->
        if (item is ShortcutItem.Preset) {
          setActionShortcut(actionId, item.preset.shortcut)
        }
      }
    ).component

  link(message("terminal.command.completion.shortcut.change")) {
    val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(it.source as Component))
    val keymapPanel = allSettings?.find(KeymapPanel::class.java)

    if (keymapPanel != null) {
      allSettings.select(keymapPanel).doWhenDone {
        keymapPanel.selectAction(actionId)
      }
    } else {
      val newKeymapPanel = KeymapPanel()
      ShowSettingsUtil.getInstance().editConfigurable(it.source as Component, newKeymapPanel) {
        newKeymapPanel.selectAction(actionId)
      }
    }
  }.visibleIf(comboBox.selectedValueIs(ShortcutItem.Custom))
}

private fun getActionShortcut(actionId: String): Shortcut? {
  val keymapManager = KeymapManager.getInstance() ?: return null
  return keymapManager.activeKeymap.getShortcuts(actionId).firstOrNull()
}

private fun setActionShortcut(actionId: String, value: Shortcut?) {
  val keymapManager = KeymapManager.getInstance() as? KeymapManagerEx ?: return

  var keymapToModify = keymapManager.activeKeymap
  if (!keymapToModify.canModify()) {
    val allKeymaps = keymapManager.allKeymaps
    val name = SchemeNameGenerator.getUniqueName(
      KeyMapBundle.message("new.keymap.name", keymapToModify.presentableName)
    ) { newName: String ->
      allKeymaps.any { it.name == newName || it.presentableName == newName }
    }

    val newKeymap = keymapToModify.deriveKeymap(name)
    keymapManager.schemeManager.addScheme(newKeymap)
    keymapManager.activeKeymap = newKeymap
    keymapToModify = newKeymap
  }
  keymapToModify.removeAllActionShortcuts(actionId)
  if (value != null) {
    keymapToModify.addShortcut(actionId, value)
  }
}

private val TAB_SHORTCUT_PRESET = ShortcutPreset(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null),
  "Tab"
)
private val ENTER_SHORTCUT_PRESET = ShortcutPreset(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null),
  "Enter"
)
private fun getCtrlSpacePreset(project: Project): ShortcutPreset {
  return ShortcutPreset(
    KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), null),
    if (isMac(project)) "⌃ Space" else "Ctrl+Space"
  )
}

private class ShortcutPreset(val shortcut: Shortcut, val text: String)

private sealed class ShortcutItem {
  data class Preset(val preset: ShortcutPreset) : ShortcutItem()
  object Custom : ShortcutItem()
}
