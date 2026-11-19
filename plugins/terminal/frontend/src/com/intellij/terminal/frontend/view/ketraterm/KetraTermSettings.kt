// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(io.github.ketraterm.ui.swing.suggestion.ExperimentalShellSuggestionsApi::class)

package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalUiSettingsListener
import com.intellij.ui.ColorUtil
import com.intellij.util.asDisposable
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.ui.AwtTransformers
import io.github.ketraterm.input.policy.PasteControlPolicy
import io.github.ketraterm.render.api.TerminalColorPalette
import io.github.ketraterm.render.api.TerminalRenderCursorShape
import io.github.ketraterm.ui.swing.api.SwingHyperlinkStyle
import io.github.ketraterm.ui.swing.settings.SwingPromptDecoration
import io.github.ketraterm.ui.swing.settings.SwingSettings
import io.github.ketraterm.ui.swing.settings.SwingSettingsProvider
import java.awt.Font
import javax.swing.JLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalFontSettingsListener
import org.jetbrains.plugins.terminal.TerminalFontSettingsService
import org.jetbrains.plugins.terminal.TerminalOptionsProvider

internal class KetraTermSettings(private val settings: JBTerminalSystemSettingsProviderBase) : SwingSettingsProvider {
  private val metricsSource = JLabel()
  private val indexedColors = TerminalColorPalette.defaultIndexedColors()
  private var alternateScreen = false
  private var snapshot = createSnapshot()

  override fun currentSettings(): SwingSettings = snapshot

  fun setAlternateScreen(value: Boolean): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (alternateScreen == value) return false
    alternateScreen = value
    return refreshSnapshot()
  }

  fun installListeners(
    coroutineScope: CoroutineScope,
    onHostSettingsChanged: () -> Unit = {},
    onChanged: () -> Unit,
  ) {
    val disposable = coroutineScope.asDisposable()
    fun refresh() {
      coroutineScope.launch(Dispatchers.EDT) {
        if (refreshSnapshot()) onChanged()
        onHostSettingsChanged()
      }
    }

    settings.addUiSettingsListener(disposable, object : TerminalUiSettingsListener {
      override fun cursorChanged() = refresh()
      override fun fontChanged() = refresh()
    })
    TerminalFontSettingsService.getInstance().addListener(object : TerminalFontSettingsListener {
      override fun fontSettingsChanged() = refresh()
    }, disposable)
    TerminalOptionsProvider.instance.addListener(disposable, ::refresh)
    val connection = ApplicationManager.getApplication().messageBus.connect(disposable)
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refresh() })
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { refresh() })
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == "terminal.use.1.0.line.spacing.for.alternative.screen.buffer") refresh()
      }
    })
  }

  private fun refreshSnapshot(): Boolean {
    val updated = createSnapshot()
    if (updated == snapshot) return false
    snapshot = updated
    return true
  }

  private fun createSnapshot(): SwingSettings {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val scheme = EditorColorsManager.getInstance().globalScheme
    val sourcePalette = settings.terminalColorPalette
    for (index in 0..<16) {
      indexedColors[index] = AwtTransformers.toAwtColor(sourcePalette.getForeground(TerminalColor(index)))!!.rgb
    }
    val foreground = AwtTransformers.toAwtColor(sourcePalette.defaultForeground)!!
    val background = AwtTransformers.toAwtColor(sourcePalette.defaultBackground)!!
    val selectionBackground = scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: background
    val selectionForeground = scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR) ?: foreground
    val cursorColor = scheme.getColor(EditorColors.CARET_COLOR) ?: foreground
    val hyperlinkColor = scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).foregroundColor ?: foreground
    val font = settings.terminalFont.deriveFont(settings.terminalFontSize)
    val options = TerminalOptionsProvider.instance

    return SwingSettings.create {
      it.font = font
      it.fallbackFonts = settings.fontPreferences.effectiveFontFamilies
        .filter { family -> family != font.family }
        .map { family -> Font(family, font.style, font.size).deriveFont(font.size2D) }
      it.palette = TerminalColorPalette(
        defaultForeground = foreground.rgb,
        defaultBackground = background.rgb,
        selectionForeground = selectionForeground.rgb,
        selectionBackground = selectionBackground.rgb,
        cursorForeground = background.rgb,
        cursorBackground = cursorColor.rgb,
        indexedColors = indexedColors,
        isDark = ColorUtil.isDark(background),
      )
      it.lineHeight = if (alternateScreen && settings.shouldDisableLineSpacingForAlternateScreenBuffer()) 1f else settings.lineSpacing
      val fontWidth = metricsSource.getFontMetrics(font).charWidth('W').coerceAtLeast(1)
      it.columnSpacing = ((settings.columnSpacing - 1f) * fontWidth).roundToInt().coerceAtLeast(0)
      it.mouseReportingEnabled = options.mouseReporting
      it.copyOnSelection = settings.copyOnSelect() || options.copyOnSelection
      it.middleClickPaste = settings.pasteOnMiddleMouseClick() && options.pasteOnMiddleMouseButton
      it.cursorBlinkMillis = settings.caretBlinkingMs()
      it.cursorShape = when (settings.cursorShape) {
        CursorShape.BLINK_BLOCK, CursorShape.STEADY_BLOCK -> TerminalRenderCursorShape.BLOCK
        CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> TerminalRenderCursorShape.UNDERLINE
        CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR -> TerminalRenderCursorShape.BAR
      }
      it.textAntialiasing = AntialiasingType.getKeyForCurrentScope(true)
      it.selectionBackground = selectionBackground.rgb
      it.searchMatchBackground = scheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).backgroundColor?.rgb
                                 ?: selectionBackground.rgb
      it.searchActiveMatchBackground = scheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES).backgroundColor?.rgb
                                       ?: selectionBackground.rgb
      it.hyperlinkActivationForeground = hyperlinkColor.rgb
      val osc8 = intellijOsc8HyperlinkPresentation(scheme)
      it.osc8HyperlinkPresentation = if (options.highlightHyperlinks) osc8 else osc8.copy(
        normal = SwingHyperlinkStyle(),
        isVisible = false,
      )
      it.pasteControlPolicy = PasteControlPolicy.PRESERVE
      it.smartSuggestionsEnabled = options.shellIntegration
      it.shellSuggestionsEnabled = options.showCompletionPopupAutomatically
      it.inlineShellSuggestionsEnabled = true
      it.promptDecoration = if (options.shellIntegration) SwingPromptDecoration.DIVIDER else SwingPromptDecoration.NONE
      it.shellIntegrationPromptDotsVisible = options.shellIntegration
      it.shellIntegrationFailedCommandRailsVisible = options.shellIntegration
      it.visualBellEnabled = false
    }
  }
}
