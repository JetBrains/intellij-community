package com.intellij.terminal.frontend.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupBottomPanelProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.TERMINAL_CONFIGURABLE_ID
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import java.awt.event.KeyEvent
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.KeyStroke

internal class TerminalLookupBottomPanelProvider : LookupBottomPanelProvider {
  override fun createBottomPanel(lookup: Lookup): JComponent? {
    return if (lookup.editor.isOutputModelEditor) {
      doCreatePanel(lookup)
    }
    else null
  }

  private fun doCreatePanel(lookup: Lookup): JComponent {
    val panel = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {}

      row {
        resizableRow()

        val shortcutHint = createInsertionShortcutHint()
        if (shortcutHint != null) {
          cell(shortcutHint)
            .align(AlignY.CENTER)
        }

        actionButton(TerminalCommandCompletionSettingsAction())
          .align(AlignY.CENTER)
          .align(AlignX.RIGHT)
      }
    }

    panel.background = JBUI.CurrentTheme.CompletionPopup.Advertiser.background()
    panel.border = JBUI.Borders.empty(0, 12, 0, 5)
    panel.preferredSize = JBDimension(240, 28)
    return panel
  }

  private fun createInsertionShortcutHint(): JComponent? {
    val shortcutText = getInsertionShortcutText()
    if (shortcutText == null) {
      // If there is no shortcut, then do not show the hint
      return null
    }

    val hint = JBHtmlPane(JBHtmlPaneStyleConfiguration(), createCustomPaneConfiguration())
    hint.isOpaque = false
    hint.foreground = JBUI.CurrentTheme.CompletionPopup.Advertiser.foreground()
    hint.font = JBUI.Fonts.label().lessOn(1f)

    hint.text = TerminalBundle.message("terminal.command.completion.insertion.shortcut.hint", shortcutText)

    return hint
  }

  private fun createCustomPaneConfiguration(): JBHtmlPaneConfiguration {
    val foreground = JBUI.CurrentTheme.CompletionPopup.Advertiser.foreground()
    val borderColor = JBColor.namedColor("Component.borderColor", JBColor(0xC9CCD6, 0x4E5157))
    return JBHtmlPaneConfiguration.builder()
      .customStyleSheet("""
        kbd {
          color: #${ColorUtil.toHex(foreground)};
          border-color: #${ColorUtil.toHex(borderColor)};
          font-weight: bold;
          font-size: 90%;
        }
      """.trimIndent())
      .build()
  }

  private fun getInsertionShortcutText(): String? {
    val actionId = "Terminal.EnterCommandCompletion"
    val shortcuts = KeymapManager.getInstance().activeKeymap
      .getShortcuts(actionId)
      .mapNotNull { it as? KeyboardShortcut }
    if (shortcuts.isEmpty()) {
      // No keyboard shortcut is set for the action
      return null
    }

    val presents = getInsertionPresets()
    val chosenPreset = presents.find { preset -> shortcuts.contains(preset.shortcut) }
    if (chosenPreset != null) {
      return "<kbd>${chosenPreset.text}</kbd>"
    }
    else {
      return """<shortcut actionId="$actionId"/>"""
    }
  }

  /**
   * Use overridden shortcut text for some preset shortcuts. To show them in the same way in all OS.
   * (to not show them as glyphs on macOS)
   */
  private fun getInsertionPresets(): List<ShortcutPreset> {
    return listOf(
      ShortcutPreset(
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null),
        "Enter"
      ),
      ShortcutPreset(
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null),
        "Tab"
      )
    )
  }

  private data class ShortcutPreset(val shortcut: Shortcut, val text: String)
}

private class TerminalCommandCompletionSettingsAction : DumbAwareAction(
  TerminalBundle.message("action.Terminal.CommandCompletionSettings.text"),
  null,
  AllIcons.General.Settings,
) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // TODO: it should highlight the Command Completion options group in the settings dialog.
    ShowSettingsUtil.getInstance().showSettingsDialog(project, Predicate { configurable: Configurable ->
      configurable is ConfigurableWithId && configurable.getId() == TERMINAL_CONFIGURABLE_ID
    }, null)
  }
}