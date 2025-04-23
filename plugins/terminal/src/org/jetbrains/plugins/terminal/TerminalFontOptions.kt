// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.AppConsoleFontOptions
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.AppFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

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

  private var columnSpacing: Float = DEFAULT_TERMINAL_COLUMN_SPACING.floatValue

  fun addListener(listener: TerminalFontOptionsListener, disposable: Disposable) {
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  @ApiStatus.Internal // for Java
  @JvmName("getSettings")
  internal fun getSettings(): TerminalFontSettings {
    val preferences = fontPreferences
    return TerminalFontSettings(
      fontFamily = preferences.fontFamily,
      fontSize = TerminalFontSize.ofFloat(preferences.getSize2D(preferences.fontFamily)),
      lineSpacing = TerminalLineSpacing.ofFloat(preferences.lineSpacing),
      columnSpacing = TerminalColumnSpacing.ofFloat(columnSpacing),
    )
  }

  internal fun setSettings(settings: TerminalFontSettings) {
    val oldSettings = getSettings()

    val newPreferences = FontPreferencesImpl()
    // start with the console preferences as the default
    AppConsoleFontOptions.getInstance().fontPreferences.copyTo(newPreferences)
    // then overwrite the subset that the terminal settings provide
    newPreferences.clearFonts()
    newPreferences.addFontFamily(settings.fontFamily)
    // These two are not really used by the terminal at the moment,
    // but are needed so that the families are saved,
    // otherwise migration in com.intellij.openapi.editor.colors.impl.AppFontOptions.copyState
    // will be triggered, and that can mess up the saved settings completely (IJPL-184027).
    val regularSubFamily = FontFamilyService.getRecommendedSubFamily(settings.fontFamily)
    newPreferences.regularSubFamily = regularSubFamily
    val boldSubFamily = FontFamilyService.getRecommendedBoldSubFamily(settings.fontFamily, regularSubFamily)
    newPreferences.boldSubFamily = boldSubFamily
    newPreferences.setFontSize(settings.fontFamily, settings.fontSize.floatValue)
    newPreferences.lineSpacing = settings.lineSpacing.floatValue
    // then apply the settings that aren't a part of FontPreferences
    columnSpacing = settings.columnSpacing.floatValue
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
    val userSetConsoleLineSpacing = TerminalLineSpacing.ofFloat(defaultState.LINE_SPACING)
    val defaultConsoleLineSpacing = TerminalLineSpacing.ofFloat(FontPreferences.DEFAULT_LINE_SPACING)
    if (userSetConsoleLineSpacing == defaultConsoleLineSpacing) {
      defaultState.LINE_SPACING = DEFAULT_TERMINAL_LINE_SPACING.floatValue
    }
    loadState(defaultState)
  }

  private fun fireListeners() {
    for (listener in listeners) {
      listener.fontOptionsChanged()
    }
  }
}

internal sealed class TerminalFontSize {
  companion object {
    fun ofFloat(value: Float): TerminalFontSize = TerminalFontSizeImpl(TerminalSettingsFloatValueImpl.ofFloat(value))
  }

  abstract val floatValue: Float
  abstract val intValue: Int

  fun scale(): TerminalFontSize = ofFloat(UISettingsUtils.getInstance().scaleFontSize(floatValue))

  private data class TerminalFontSizeImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalFontSize() {
    override val floatValue: Float get() = impl.toFloat()
    override val intValue: Int get() = floatValue.roundToInt()
  }
}

// these two are the same, but we don't want them to be mutually assignable

internal sealed class TerminalLineSpacing {
  companion object {
    fun ofFloat(value: Float): TerminalLineSpacing = TerminalLineSpacingImpl(TerminalSettingsFloatValueImpl.ofFloat(value))
  }

  abstract val floatValue: Float

  private data class TerminalLineSpacingImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalLineSpacing() {
    override val floatValue: Float get() = impl.toFloat()
  }
}

internal sealed class TerminalColumnSpacing {
  companion object {
    fun ofFloat(value: Float): TerminalColumnSpacing = TerminalColumnSpacingImpl(TerminalSettingsFloatValueImpl.ofFloat(value))
  }

  abstract val floatValue: Float

  private data class TerminalColumnSpacingImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalColumnSpacing() {
    override val floatValue: Float get() = impl.toFloat()
  }
}

/**
 * A container for floating-point values with equality support and sensible precision.
 */
private data class TerminalSettingsFloatValueImpl(
  private val valueTimes10000: Int,
) {
  companion object {
    fun ofFloat(value: Float): TerminalSettingsFloatValueImpl =
      TerminalSettingsFloatValueImpl((value * 10000).toInt())
  }

  fun toFloat(): Float = valueTimes10000.toFloat() / 10000.0f
}

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

internal data class TerminalFontSettings(
  val fontFamily: String,
  val fontSize: TerminalFontSize,
  val lineSpacing: TerminalLineSpacing,
  val columnSpacing: TerminalColumnSpacing,
)

@ApiStatus.Internal
class PersistentTerminalFontPreferences: AppEditorFontOptions.PersistentFontPreferences {
  @Suppress("unused") // for serialization
  constructor(): super() {
    LINE_SPACING = DEFAULT_TERMINAL_LINE_SPACING.floatValue // to ensure that values different from OUR default are saved
  }

  constructor(fontPreferences: FontPreferences): super(fontPreferences)

  var COLUMN_SPACING: Float = DEFAULT_TERMINAL_COLUMN_SPACING.floatValue
}

internal val DEFAULT_TERMINAL_FONT_SIZE: TerminalFontSize get() = TerminalFontSize.ofFloat(FontPreferences.DEFAULT_FONT_SIZE.toFloat())
internal val DEFAULT_TERMINAL_LINE_SPACING = TerminalLineSpacing.ofFloat(1.0f)
internal val DEFAULT_TERMINAL_COLUMN_SPACING = TerminalColumnSpacing.ofFloat(1.0f)
