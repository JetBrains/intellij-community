// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.*
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import org.jdom.Element
import java.awt.Color
import java.awt.Font
import java.util.*
import kotlin.math.roundToInt

/**
  A delegating color scheme for a terminal text editor.
  
  This thing shouldn't really exist, but with the current implementation it's hard to replace it with something that:

  a) is safe (no accidental harmless call can change the global color scheme);

  b) can update the parent/delegate scheme when the global scheme changes;

  c) can provide consistent settings to the editor without resetting something when you set something else.

  Alternatives that don't work:

  1) call setter methods on `editor.colorScheme` directly - fails (c), as setting the font size resets the font family, for example;
  2) use `DelegateColorScheme` - fails (a), as everything is propagated to the delegate by default (which is mutable!);
  3) use `EditorColorsSchemeImpl` - fails (b), as it requires a read-only parent, but, again, the global scheme is not read-only.
*/
internal class TerminalColorScheme : EditorColorsScheme {
  companion object {
    val KEY = Key.create<TerminalColorScheme>("TERMINAL_EDITOR_COLOR_SCHEME")
  }
  
  internal var globalScheme = EditorColorsManager.getInstance().globalScheme

  private var fontPreferences = globalScheme.fontPreferences
  private var lineSpacing = globalScheme.lineSpacing
  private var useLigatures = globalScheme.isUseLigatures

  override fun getName(): String = globalScheme.name

  override fun getMetaProperties(): Properties = globalScheme.metaProperties

  override fun setName(name: String?) {
    notSupported()
  }

  override fun getAttributes(key: TextAttributesKey?): TextAttributes? = globalScheme.getAttributes(key)

  override fun setAttributes(key: TextAttributesKey, attributes: TextAttributes?) {
    notSupported()
  }

  override fun getAttributes(key: TextAttributesKey?, useDefaults: Boolean): TextAttributes? = globalScheme.getAttributes(key, useDefaults)

  override fun getDefaultBackground(): Color = globalScheme.defaultBackground

  override fun getDefaultForeground(): Color = globalScheme.defaultForeground

  override fun getColor(key: ColorKey?): Color? = globalScheme.getColor(key)

  override fun setColor(key: ColorKey?, color: Color?) {
    notSupported()
  }

  override fun getFontPreferences(): FontPreferences = fontPreferences

  override fun setFontPreferences(preferences: FontPreferences) {
    this.fontPreferences = preferences
  }

  override fun getEditorFontName(): @NlsSafe String = fontPreferences.fontFamily

  override fun setEditorFontName(fontName: String?) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun getEditorFontSize(): Int = editorFontSize2D.roundToInt()

  override fun getEditorFontSize2D(): Float = fontPreferences.getSize2D(fontPreferences.fontFamily)

  override fun setEditorFontSize(fontSize: Int) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun setEditorFontSize(fontSize: Float) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun getFont(key: EditorFontType?): Font {
    LOG.warn(Throwable("EditorImpl failed to find the font for $key and delegated to us, but we don't support this"))
    return globalScheme.getFont(key)
  }

  override fun getLineSpacing(): Float = lineSpacing

  override fun setLineSpacing(lineSpacing: Float) {
    this.lineSpacing = lineSpacing
  }

  override fun isUseLigatures(): Boolean = useLigatures

  override fun setUseLigatures(useLigatures: Boolean) {
    this.useLigatures = useLigatures
  }

  override fun clone(): Any {
    notSupported()
  }

  override fun getConsoleFontPreferences(): FontPreferences = fontPreferences

  override fun setConsoleFontPreferences(preferences: FontPreferences) {
    this.fontPreferences = preferences
  }

  override fun setUseEditorFontPreferencesInConsole() {
    notSupported()
  }

  override fun isUseEditorFontPreferencesInConsole(): Boolean = true

  override fun setUseAppFontPreferencesInEditor() {
    notSupported()
  }

  override fun isUseAppFontPreferencesInEditor(): Boolean = false

  override fun getConsoleFontName(): @NlsSafe String = editorFontName

  override fun setConsoleFontName(fontName: String?) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun getConsoleFontSize(): Int = editorFontSize

  override fun getConsoleFontSize2D(): Float = editorFontSize2D

  override fun setConsoleFontSize(fontSize: Int) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun setConsoleFontSize(fontSize: Float) {
    throw UnsupportedOperationException("Use setFontPreferences instead")
  }

  override fun getConsoleLineSpacing(): Float = lineSpacing

  override fun setConsoleLineSpacing(lineSpacing: Float) {
    setLineSpacing(lineSpacing)
  }

  override fun readExternal(parentNode: Element?) {
    notSupported()
  }

  override fun isReadOnly(): Boolean = false
}

private fun notSupported(): Nothing {
  throw UnsupportedOperationException("TerminalColorScheme is not a general purpose color scheme and only supports a subset of functions")
}

private val LOG = logger<TerminalColorScheme>()
