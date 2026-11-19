// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.ketraterm.KetraTermClipboardHandler
import com.intellij.terminal.frontend.view.ketraterm.KetraTermSettings
import com.intellij.terminal.frontend.view.ketraterm.intellijOsc8HyperlinkPresentation
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.terminal.TerminalUiSettingsListener
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.ui.AwtTransformers
import io.github.ketraterm.render.api.TerminalRenderCursorShape
import io.github.ketraterm.render.api.TerminalRenderUnderline
import io.github.ketraterm.ui.swing.settings.SwingPromptDecoration
import io.github.ketraterm.ui.swing.settings.SwingSettings
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.Font
import javax.swing.SwingUtilities
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.junit.jupiter.api.Test
import com.jediterm.core.Color as TerminalColor

@TestApplication
internal class KetraTermSettingsTest {
  @Test
  fun `shell links preserve terminal colors and honor the highlight setting`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val options = TerminalOptionsProvider.instance
      val previous = options.highlightHyperlinks
      try {
        options.highlightHyperlinks = true
        val adapter = KetraTermSettings(TestSettings())
        val shell = requireNotNull(adapter.currentSettings().osc8HyperlinkPresentation)
        assertThat(shell.normal?.foregroundArgb).isNull()
        assertThat(shell.normal?.underlineStyle).isEqualTo(TerminalRenderUnderline.DOTTED)
        assertThat(shell.hovered?.foregroundArgb).isNull()
        assertThat(shell.hovered?.underlineStyle).isEqualTo(TerminalRenderUnderline.SINGLE)
        assertThat(shell.hovered?.underlineThickness).isEqualTo(2)
        options.highlightHyperlinks = false
        val hidden = KetraTermSettings(TestSettings())
        assertThat(hidden.currentSettings().osc8HyperlinkPresentation?.isVisible).isFalse()
        assertThat(hidden.currentSettings().osc8HyperlinkPresentation?.normal?.foregroundArgb).isNull()
      }
      finally {
        options.highlightHyperlinks = previous
      }
    }
  }

  @Test
  fun `shell link activation resolves the current scheme attributes`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val scheme = EditorColorsManager.getInstance().globalScheme.clone() as com.intellij.openapi.editor.colors.EditorColorsScheme
      for (color in listOf(Color.RED, Color.GREEN)) {
        scheme.setAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES, TextAttributes(color, null, color, EffectType.WAVE_UNDERSCORE, Font.PLAIN))
        val presentation = intellijOsc8HyperlinkPresentation(scheme)
        assertThat(presentation.active?.foregroundArgb).isEqualTo(color.rgb)
        assertThat(presentation.active?.underlineStyle).isEqualTo(TerminalRenderUnderline.CURLY)
        assertThat(presentation.normal?.foregroundArgb).isNull()
      }
    }
  }

  @Test
  fun `grid spacing uses native defaults with and without prompt decorations`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val options = TerminalOptionsProvider.instance
      val original = options.shellIntegration
      try {
        for (enabled in listOf(true, false)) {
          options.shellIntegration = enabled
          val settings = KetraTermSettings(TestSettings()).currentSettings()
          val defaults = SwingSettings.create {
            it.promptDecoration = if (enabled) SwingPromptDecoration.DIVIDER else SwingPromptDecoration.NONE
          }
          assertThat(settings.padding).isEqualTo(defaults.padding)
          assertThat(settings.alternateScreenPadding).isEqualTo(defaults.alternateScreenPadding)
          assertThat(settings.shellIntegrationDecorationGutterWidth).isEqualTo(defaults.shellIntegrationDecorationGutterWidth)
        }
      }
      finally {
        options.shellIntegration = original
      }
    }
  }

  @Test
  fun `interaction settings update the native view without new host settings`(): Unit = timeoutRunBlocking {
    val options = TerminalOptionsProvider.instance
    val original = withContext(Dispatchers.EDT) {
      listOf(options.mouseReporting, options.copyOnSelection, options.pasteOnMiddleMouseButton, options.shellIntegration)
    }
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val updates = Channel<SwingSettings>(Channel.UNLIMITED)
    try {
      withContext(Dispatchers.EDT) {
        options.mouseReporting = true
        options.copyOnSelection = false
        options.pasteOnMiddleMouseButton = false
        options.shellIntegration = true
        KetraTermSettings(TestSettings()).apply {
          assertThat(currentSettings().promptDecoration).isEqualTo(SwingPromptDecoration.DIVIDER)
          installListeners(scope) { updates.trySend(currentSettings()).getOrThrow() }
        }
        options.mouseReporting = false
        options.copyOnSelection = true
        options.pasteOnMiddleMouseButton = true
        options.shellIntegration = false
      }
      val updated = updates.receive()
      assertThat(updated.mouseReportingEnabled).isFalse()
      assertThat(updated.copyOnSelection).isTrue()
      assertThat(updated.middleClickPaste).isTrue()
      assertThat(updated.promptDecoration).isEqualTo(SwingPromptDecoration.NONE)
    }
    finally {
      job.cancelAndJoin()
      withContext(Dispatchers.EDT) {
        options.mouseReporting = original[0]
        options.copyOnSelection = original[1]
        options.pasteOnMiddleMouseButton = original[2]
        options.shellIntegration = original[3]
      }
      updates.close()
    }
  }

  @Test
  fun `column spacing maps the host multiplier to added pixels`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val provider = TestSettings()
      provider.columnSpacingValue = 2f
      assertThat(KetraTermSettings(provider).currentSettings().columnSpacing).isGreaterThan(0)
      provider.columnSpacingValue = 1f
      assertThat(KetraTermSettings(provider).currentSettings().columnSpacing).isZero()
      provider.columnSpacingValue = 0.5f
      assertThat(KetraTermSettings(provider).currentSettings().columnSpacing).isZero()
    }
  }

  @Test
  fun `settings reuse the snapshot and preserve font and color values`(): Unit = timeoutRunBlocking {
    val (snapshot, secondRead) = withContext(Dispatchers.EDT) {
      val provider = TestSettings()
      val settings = KetraTermSettings(provider)
      settings.currentSettings() to settings.currentSettings()
    }

    assertThat(secondRead).isSameAs(snapshot)
    assertThat(snapshot.font.size2D).isEqualTo(15.5f)
    assertThat(snapshot.lineHeight).isEqualTo(1.25f)
    assertThat(snapshot.cursorBlinkMillis).isZero()
    assertThat(snapshot.cursorShape).isEqualTo(TerminalRenderCursorShape.UNDERLINE)
    assertThat(snapshot.palette.defaultForeground).isEqualTo(Color(0xD0D1D2).rgb)
    assertThat(snapshot.palette.defaultBackground).isEqualTo(Color(0x101112).rgb)
    assertThat(snapshot.palette.indexedColor(1)).isEqualTo(Color(0xA01020).rgb)
    assertThat(snapshot.palette.indexedColor(16)).isEqualTo(Color(0x000000).rgb)
    assertThat(snapshot.palette.indexedColor(21)).isEqualTo(Color(0x0000FF).rgb)
    assertThat(snapshot.palette.indexedColor(196)).isEqualTo(Color(0xFF0000).rgb)
    assertThat(snapshot.palette.indexedColor(231)).isEqualTo(Color(0xFFFFFF).rgb)
    assertThat(snapshot.palette.indexedColor(232)).isEqualTo(Color(0x080808).rgb)
    assertThat(snapshot.palette.indexedColor(255)).isEqualTo(Color(0xEEEEEE).rgb)
    assertThat(snapshot.palette.isDark).isTrue()
  }

  @Test
  fun `live updates publish on the EDT and cancellation removes listeners`(): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val updates = Channel<SwingSettings>(Channel.UNLIMITED)
    try {
      val (provider, adapter) = withContext(Dispatchers.EDT) {
        val provider = TestSettings()
        val adapter = KetraTermSettings(provider)
        adapter.installListeners(scope) {
          check(SwingUtilities.isEventDispatchThread())
          updates.trySend(adapter.currentSettings()).getOrThrow()
        }
        provider to adapter
      }
      val initial = withContext(Dispatchers.EDT) { adapter.currentSettings() }
      withContext(Dispatchers.EDT) {
        provider.fontSize = 19.5f
        provider.fireFontChanged()
      }
      val fontUpdate = updates.receive()
      assertThat(fontUpdate.font.size2D).isEqualTo(19.5f)
      assertThat(initial.font.size2D).isEqualTo(15.5f)
      assertThat(fontUpdate).isNotSameAs(initial)

      withContext(Dispatchers.EDT) {
        provider.ansiColors[1].foregroundColor = Color(0xB02030)
        val manager = EditorColorsManager.getInstance()
        ApplicationManager.getApplication().messageBus.syncPublisher(EditorColorsManager.TOPIC).globalSchemeChange(manager.globalScheme)
      }
      val paletteUpdate = updates.receive()
      assertThat(paletteUpdate.palette.indexedColor(1)).isEqualTo(Color(0xB02030).rgb)
      assertThat(initial.palette.indexedColor(1)).isEqualTo(Color(0xA01020).rgb)
      assertThat(paletteUpdate.palette.indexedColor(196)).isEqualTo(Color(0xFF0000).rgb)
      assertThat(paletteUpdate.palette.indexedColor(255)).isEqualTo(Color(0xEEEEEE).rgb)

      job.cancelAndJoin()
      val remainingListeners = withContext(Dispatchers.EDT) {
        provider.fontSize = 22f
        provider.fireFontChanged()
        provider.listeners.size
      }
      assertThat(remainingListeners).isZero()
      assertThat(updates.tryReceive().isFailure).isTrue()
      assertThat(withContext(Dispatchers.EDT) { adapter.currentSettings() }).isSameAs(paletteUpdate)
    }
    finally {
      job.cancelAndJoin()
      updates.close()
    }
  }

  @Test
  fun `shortcut policy updates reach the host without replacing the renderer snapshot`(): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val changes = Channel<Boolean>(Channel.UNLIMITED)
    val options = TerminalOptionsProvider.instance
    val original = withContext(Dispatchers.EDT) { options.overrideIdeShortcuts }
    try {
      var rendererUpdates = 0
      val adapter = withContext(Dispatchers.EDT) {
        KetraTermSettings(TestSettings()).apply {
          installListeners(scope, onHostSettingsChanged = {
            check(SwingUtilities.isEventDispatchThread())
            changes.trySend(options.overrideIdeShortcuts).getOrThrow()
          }) { rendererUpdates++ }
        }
      }
      val snapshot = withContext(Dispatchers.EDT) { adapter.currentSettings() }
      withContext(Dispatchers.EDT) { options.overrideIdeShortcuts = !original }

      assertThat(changes.receive()).isEqualTo(!original)
      withContext(Dispatchers.EDT) {
        assertThat(adapter.currentSettings()).isSameAs(snapshot)
        assertThat(rendererUpdates).isZero()
      }
      job.cancelAndJoin()
      withContext(Dispatchers.EDT) { options.overrideIdeShortcuts = original }
      assertThat(changes.tryReceive().isFailure).isTrue()
    }
    finally {
      job.cancelAndJoin()
      withContext(Dispatchers.EDT) { options.overrideIdeShortcuts = original }
      changes.close()
    }
  }

  @Test
  fun `alternate screen spacing follows the live host setting`(): Unit = timeoutRunBlocking {
    val setting = "terminal.use.1.0.line.spacing.for.alternative.screen.buffer"
    val original = withContext(Dispatchers.EDT) { AdvancedSettings.getBoolean(setting) }
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val updates = Channel<SwingSettings>(Channel.UNLIMITED)
    try {
      val adapter = withContext(Dispatchers.EDT) {
        AdvancedSettings.setBoolean(setting, true)
        KetraTermSettings(TestSettings()).apply {
          val primary = currentSettings()
          assertThat(setAlternateScreen(true)).isTrue()
          val alternate = currentSettings()
          assertThat(alternate.lineHeight).isEqualTo(1f)
          assertThat(primary.lineHeight).isEqualTo(1.25f)
          assertThat(setAlternateScreen(true)).isFalse()
          assertThat(currentSettings()).isSameAs(alternate)
          assertThat(setAlternateScreen(false)).isTrue()
          assertThat(currentSettings().lineHeight).isEqualTo(1.25f)
          setAlternateScreen(true)
          installListeners(scope) {
            check(SwingUtilities.isEventDispatchThread())
            updates.trySend(currentSettings()).getOrThrow()
          }
        }
      }
      withContext(Dispatchers.EDT) { AdvancedSettings.setBoolean(setting, false) }
      val updated = updates.receive()
      assertThat(updated.lineHeight).isEqualTo(1.25f)

      job.cancelAndJoin()
      withContext(Dispatchers.EDT) {
        AdvancedSettings.setBoolean(setting, true)
        assertThat(adapter.currentSettings()).isSameAs(updated)
      }
      assertThat(updates.tryReceive().isFailure).isTrue()
    }
    finally {
      job.cancelAndJoin()
      withContext(Dispatchers.EDT) { AdvancedSettings.setBoolean(setting, original) }
      updates.close()
    }
  }

  @Test
  fun `link styling follows the live host setting`(): Unit = timeoutRunBlocking {
    val options = TerminalOptionsProvider.instance
    val original = withContext(Dispatchers.EDT) { options.highlightHyperlinks }
    val job = Job(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + job)
    val updates = Channel<SwingSettings>(Channel.UNLIMITED)
    try {
      withContext(Dispatchers.EDT) {
        options.highlightHyperlinks = false
        KetraTermSettings(TestSettings()).apply {
          installListeners(scope) { updates.trySend(currentSettings()).getOrThrow() }
        }
        options.highlightHyperlinks = true
      }
      val highlighted = updates.receive().osc8HyperlinkPresentation!!
      assertThat(highlighted.isVisible).isTrue()
      assertThat(highlighted.normal?.foregroundArgb).isNull()
      assertThat(highlighted.normal?.underlineStyle).isEqualTo(TerminalRenderUnderline.DOTTED)

      withContext(Dispatchers.EDT) { options.highlightHyperlinks = false }
      val hidden = updates.receive().osc8HyperlinkPresentation!!
      assertThat(hidden.isVisible).isFalse()
      assertThat(hidden.normal?.foregroundArgb).isNull()
      assertThat(hidden.hovered).isEqualTo(highlighted.hovered)
      assertThat(highlighted.isVisible).isTrue()
    }
    finally {
      job.cancelAndJoin()
      withContext(Dispatchers.EDT) { options.highlightHyperlinks = original }
      updates.close()
    }
  }

  @Test
  fun `clipboard actions share the IntelliJ clipboard`(): Unit = timeoutRunBlocking {
    val actual = withContext(Dispatchers.EDT) {
      val manager = CopyPasteManager.getInstance()
      val previous = manager.contents
      try {
        val clipboard = KetraTermClipboardHandler(TestSettings())
        clipboard.copyText("terminal α\nsecond line")
        val copied = manager.getContents<String>(DataFlavor.stringFlavor)
        manager.setContents(StringSelection("IDE β\nnext line"))
        copied to clipboard.readText()
      }
      finally {
        manager.setContents(previous ?: StringSelection(""))
      }
    }

    assertThat(actual.first).isEqualTo("terminal α\nsecond line")
    assertThat(actual.second).isEqualTo("IDE β\nnext line")
  }

  private class TestSettings : JBTerminalSystemSettingsProviderBase() {
    var columnSpacingValue = 1f
    override fun getColumnSpacing(): Float = columnSpacingValue
    var fontSize: Float = 15.5f
    val listeners = ArrayList<TerminalUiSettingsListener>()
    val ansiColors: Array<TextAttributes> = Array(16) { index ->
      TextAttributes(if (index == 1) Color(0xA01020) else Color(index * 0x010101), null, null, null, Font.PLAIN)
    }
    private val palette = object : TerminalColorPalette() {
      override val defaultForeground: TerminalColor = AwtTransformers.fromAwtColor(Color(0xD0D1D2))!!
      override val defaultBackground: TerminalColor = AwtTransformers.fromAwtColor(Color(0x101112))!!
      override fun getAttributesByColorIndex(index: Int): TextAttributes = ansiColors[index]
    }

    override fun getTerminalFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, 15).deriveFont(fontSize)
    override fun getTerminalFontSize(): Float = fontSize
    override fun getLineSpacing(): Float = 1.25f
    override fun getCursorShape(): CursorShape = CursorShape.STEADY_UNDERLINE
    override fun caretBlinkingMs(): Int = 0
    override fun getTerminalColorPalette(): TerminalColorPalette = palette

    override fun addUiSettingsListener(parentDisposable: Disposable, listener: TerminalUiSettingsListener) {
      listeners.add(listener)
      Disposer.register(parentDisposable) { listeners.remove(listener) }
    }

    fun fireFontChanged() {
      for (listener in listeners) {
        listener.fontChanged()
      }
    }
  }
}
