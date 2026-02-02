package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupBottomPanelProvider
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.TERMINAL_CONFIGURABLE_ID
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import java.awt.event.KeyEvent
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent

internal class TerminalLookupBottomPanelProvider : LookupBottomPanelProvider {
  override fun createBottomPanel(lookup: Lookup): JComponent? {
    return if (lookup.editor.isOutputModelEditor) {
      doCreatePanel(lookup)
    }
    else null
  }

  private fun doCreatePanel(lookup: Lookup): JComponent {
    val shouldShowPromotion = TerminalCompletionPopupPromotion.shouldShowPromotion()

    val panel = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {}

      row {
        resizableRow()

        if (shouldShowPromotion) {
          promotion()
          TerminalCompletionPopupPromotion.promotionShown()
        }
        else {
          shortcutHint(lookup)
        }

        actionButton(TerminalCommandCompletionSettingsAction())
          .align(AlignY.CENTER)
          .align(AlignX.RIGHT)
          .customize(UnscaledGaps(left = 6))
      }
    }

    panel.background = JBUI.CurrentTheme.CompletionPopup.Advertiser.background()
    panel.border = JBUI.Borders.empty(0, 12, 0, 5)
    val prefWidth = if (shouldShowPromotion) 300 else 240
    panel.preferredSize = JBDimension(prefWidth, 28)
    return panel
  }

  private fun Row.promotion() {
    icon(AllIcons.General.New_badge)
      .align(AlignY.CENTER)
      .customize(UnscaledGaps(right = 6))

    label(TerminalBundle.message("terminal.command.completion.popup.promotion"))
      .align(AlignY.CENTER)
      .component
      .apply {
        foreground = JBUI.CurrentTheme.CompletionPopup.Advertiser.foreground()
        font = JBUI.Fonts.label().lessOn(1f)
      }
  }

  private fun Row.shortcutHint(lookup: Lookup) {
    val shortcutText = getInsertionShortcutText()
    if (shortcutText == null || lookup !is LookupImpl) {
      // If there is no shortcut, then do not show the hint
      return
    }

    val textState = ShortcutHintTextState(lookup, shortcutText)
    lookup.addPrefixChangeListener(textState, lookup)

    cell(createHintComponent())
      .align(AlignY.CENTER)
      .bindText(textState.value)
  }

  private fun createHintComponent(): JTextComponent {
    val hint = JBHtmlPane(JBHtmlPaneStyleConfiguration(), createCustomPaneConfiguration())
    hint.isOpaque = false
    hint.foreground = JBUI.CurrentTheme.CompletionPopup.Advertiser.foreground()
    hint.font = JBUI.Fonts.label().lessOn(1f)
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
    val actionId = "Terminal.CommandCompletion.InsertSuggestion"
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

internal class TerminalCommandCompletionSettingsAction : DumbAwareAction(
  TerminalBundle.message("action.Terminal.CommandCompletionSettings.text"),
  null,
  AllIcons.General.Settings,
) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    TerminalCompletionPopupPromotion.doNotShowAgain()

    // TODO: it should highlight the Command Completion options group in the settings dialog.
    ShowSettingsUtil.getInstance().showSettingsDialog(project, Predicate { configurable: Configurable ->
      configurable is ConfigurableWithId && configurable.getId() == TERMINAL_CONFIGURABLE_ID
    }, null)
  }
}

private object TerminalCompletionPopupPromotion {
  private const val PROMOTION_SHOWN_COUNT_PROPERTY = "TerminalCompletionPopupPromotion.shownCount"
  private const val PROMOTION_SHOWN_COUNT_LIMIT = 3

  private var shownCount: Int
    get() = PropertiesComponent.getInstance().getInt(PROMOTION_SHOWN_COUNT_PROPERTY, 0)
    set(value) = PropertiesComponent.getInstance().setValue(PROMOTION_SHOWN_COUNT_PROPERTY, value, 0)

  fun shouldShowPromotion(): Boolean {
    return shownCount < PROMOTION_SHOWN_COUNT_LIMIT
  }

  fun promotionShown() {
    shownCount++
  }

  fun doNotShowAgain() {
    shownCount = PROMOTION_SHOWN_COUNT_LIMIT
  }
}

/**
 * Tracks the currently selected lookup item and updates the hint text accordingly:
 * if a typed string matches the lookup item, then show the "Execute" hint instead of "Insert".
 */
private class ShortcutHintTextState(
  private val lookup: LookupImpl,
  private val shortcutText: String,
) : PrefixChangeListener {
  val value: ObservableMutableProperty<String> = AtomicProperty(defaultText())

  override fun afterAppend(c: Char) {
    scheduleUpdate()
  }

  override fun afterTruncate() {
    scheduleUpdate()
  }

  private fun scheduleUpdate() {
    invokeLater(ModalityState.stateForComponent(lookup.component)) {
      if (!lookup.isLookupDisposed) {
        value.set(calculateHintText())
      }
    }
  }

  private fun calculateHintText(): String {
    val selectedItem = lookup.currentItem ?: return defaultText()
    val typedPrefix = lookup.itemPattern(selectedItem)
    return if (canExecuteWithChosenItem(selectedItem.lookupString, typedPrefix)) {
      executeText()
    }
    else defaultText()
  }

  private fun defaultText(): String {
    return TerminalBundle.message("terminal.command.completion.insertion.shortcut.hint", shortcutText)
  }

  private fun executeText(): String {
    return TerminalBundle.message("terminal.command.completion.execution.shortcut.hint", shortcutText)
  }
}