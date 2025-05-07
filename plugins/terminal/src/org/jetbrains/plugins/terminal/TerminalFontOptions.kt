// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.AppConsoleFontOptions
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.AppFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

@State(
  name = TerminalFontOptions.COMPONENT_NAME,
  storages = [Storage("terminal-font.xml")],
)
@ApiStatus.Internal
class TerminalFontOptions : AppFontOptions<PersistentTerminalFontPreferences>() {
  companion object {
    @JvmStatic fun getInstance(): TerminalFontOptions = service<TerminalFontOptions>()

    internal const val COMPONENT_NAME: String = "TerminalFontOptions"
  }

  private val listeners = CopyOnWriteArrayList<TerminalFontOptionsListener>()

  private var columnSpacing = DEFAULT_TERMINAL_COLUMN_SPACING

  fun addListener(listener: TerminalFontOptionsListener, disposable: Disposable) {
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  fun getSettings(): TerminalFontSettings {
    val preferences = fontPreferences
    return TerminalFontSettings(
      fontFamily = preferences.fontFamily,
      fontSize = preferences.getSize2D(preferences.fontFamily),
      lineSpacing = preferences.lineSpacing,
      columnSpacing = columnSpacing,
    )
  }

  fun setSettings(settings: TerminalFontSettings) {
    val oldSettings = getSettings()

    val newPreferences = FontPreferencesImpl()
    // start with the console preferences as the default
    AppConsoleFontOptions.getInstance().fontPreferences.copyTo(newPreferences)
    // then overwrite the subset that the terminal settings provide
    newPreferences.clearFonts()
    newPreferences.addFontFamily(settings.fontFamily)
    newPreferences.setFontSize(settings.fontFamily, settings.fontSize)
    newPreferences.lineSpacing = settings.lineSpacing
    // then apply the settings that aren't a part of FontPreferences
    columnSpacing = settings.columnSpacing
    // apply the FontPreferences part, the last line because it invokes incModificationCount()
    update(newPreferences)

    if (settings != oldSettings) {
      fireListeners()
    }
  }

  override fun createFontState(fontPreferences: FontPreferences): PersistentTerminalFontPreferences =
    PersistentTerminalFontPreferences(fontPreferences).also {
      it.COLUMN_SPACING = columnSpacing
    }

  override fun loadState(state: PersistentTerminalFontPreferences) {
    columnSpacing = state.COLUMN_SPACING
    super.loadState(state)

    // In the case of RemDev settings are synced from backend to frontend using `loadState` method.
    // So, notify the listeners on every `loadState` to not miss the change.
    fireListeners()
  }

  override fun noStateLoaded() {
    // the state is mostly inherited from the console settings
    val defaultState = PersistentTerminalFontPreferences(AppConsoleFontOptions.getInstance().fontPreferences)
    // except the line spacing: it is only inherited if it's different from the default, otherwise we use our own default
    val userSetConsoleLineSpacing = defaultState.LINE_SPACING
    val defaultConsoleLineSpacing = FontPreferences.DEFAULT_LINE_SPACING
    if (sameLineSpacings(userSetConsoleLineSpacing, defaultConsoleLineSpacing)) {
      defaultState.LINE_SPACING = DEFAULT_TERMINAL_LINE_SPACING
    }
    loadState(defaultState)
  }

  private fun fireListeners() {
    for (listener in listeners) {
      listener.fontOptionsChanged()
    }
  }
}

// readability delegates
internal fun sameFontSizes(a: Float, b: Float): Boolean = sameFloatValues(a, b)
internal fun sameLineSpacings(a: Float, b: Float): Boolean = sameFloatValues(a, b)
internal fun sameColumnSpacings(a: Float, b: Float): Boolean = sameFloatValues(a, b)

// the usual pain with comparing floating values, the threshold is an arbitrary "a difference that small doesn't make sense" value
private fun sameFloatValues(userSetConsoleLineSpacing: Float, defaultConsoleLineSpacing: Float): Boolean =
  abs(userSetConsoleLineSpacing - defaultConsoleLineSpacing) < 0.0001

@ApiStatus.Internal
interface TerminalFontOptionsListener {
  fun fontOptionsChanged()
}

// All this weirdness with similar nondescript names like TerminalFontOptions and TerminalFontSettings
// comes from the way the AppFontOptions API is designed.
// We need an implementation of FontPreferences to use in update() and createFontState(),
// but its API is not very extendable and is not very easy to use in the settings GUI.
// Other settings resolve this by using AppFontOptionsPanel, which has a handy getFontPreferences() method,
// but it's rather heavyweight and comes with the color schema and whatnot.
// So we need here a lightweight "mutable preferences" class to use in the settings GUI,
// and we need a state to be persisted, and we need a lot of boilerplate code to convert these things back and forth.
// An alternative would be to drop AppFontOptions completely, but then we wouldn't be able to easily reuse its logic
// and easily grab the defaults from AppConsoleFontOptions.

// To reduce possible confusion, the name TerminalFontSettings was chosen here to make it different
// from FontPreferences and AppFontOptions and PersistentFontPreferences.

@ApiStatus.Internal
data class TerminalFontSettings(
  val fontFamily: String,
  val fontSize: Float,
  val lineSpacing: Float,
  val columnSpacing: Float,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalFontSettings

    if (!sameFontSizes(fontSize, other.fontSize)) return false
    if (!sameLineSpacings(lineSpacing, other.lineSpacing)) return false
    if (!sameColumnSpacings(columnSpacing, other.columnSpacing)) return false
    if (fontFamily != other.fontFamily) return false

    return true
  }

  override fun hashCode(): Int {
    var result = (fontSize.toInt()).hashCode()
    result = 31 * result + ((lineSpacing * 10).toInt()).hashCode()
    result = 31 * result + ((columnSpacing * 10).toInt()).hashCode()
    result = 31 * result + fontFamily.hashCode()
    return result
  }
}

@ApiStatus.Internal
class PersistentTerminalFontPreferences: AppEditorFontOptions.PersistentFontPreferences {
  @Suppress("unused") // for serialization
  constructor(): super() {
    LINE_SPACING = DEFAULT_TERMINAL_LINE_SPACING // to ensure that values different from OUR default are saved
  }

  constructor(fontPreferences: FontPreferences): super(fontPreferences)

  var COLUMN_SPACING: Float = DEFAULT_TERMINAL_COLUMN_SPACING
}

internal val DEFAULT_TERMINAL_FONT_SIZE: Float get() = FontPreferences.DEFAULT_FONT_SIZE.toFloat()
internal const val DEFAULT_TERMINAL_LINE_SPACING = 1.0f
internal const val DEFAULT_TERMINAL_COLUMN_SPACING = 1.0f
