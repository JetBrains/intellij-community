// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.application.options.EditorFontsConstants
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
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow
import kotlin.math.roundToInt

@State(
  name = TerminalFontSettingsService.COMPONENT_NAME,
  storages = [Storage("terminal-font.xml")],
)
@ApiStatus.Internal
class TerminalFontSettingsService : AppFontOptions<TerminalFontSettingsState>() {
  companion object {
    @JvmStatic fun getInstance(): TerminalFontSettingsService = service<TerminalFontSettingsService>()

    internal const val COMPONENT_NAME: String = "TerminalFontOptions"
  }

  private val listeners = CopyOnWriteArrayList<TerminalFontSettingsListener>()

  private var columnSpacing: TerminalColumnSpacing = DEFAULT_TERMINAL_COLUMN_SPACING

  fun addListener(listener: TerminalFontSettingsListener, disposable: Disposable) {
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  @ApiStatus.Internal // for Java
  @JvmName("getSettings")
  internal fun getSettings(): TerminalFontSettings = TerminalFontSettings(fontPreferences, columnSpacing)

  internal fun setSettings(settings: TerminalFontSettings) {
    val oldSettings = getSettings()

    val newPreferences = FontPreferencesImpl()
    // start with the console preferences as the default
    AppConsoleFontOptions.getInstance().fontPreferences.copyTo(newPreferences)
    // then overwrite the subset that the terminal settings provide
    settings.copyTo(newPreferences)
    // then apply the settings that aren't a part of FontPreferences
    columnSpacing = settings.columnSpacing
    // apply the FontPreferences part, the last line because it invokes incModificationCount()
    update(newPreferences)

    if (settings != oldSettings) {
      fireListeners()
    }
  }

  override fun getFontPreferences(): FontPreferences {
    val result = super.getFontPreferences()
    // The default fallback family isn't serialized, but we need to explicitly add it anyway if we're not using the default font.
    // Otherwise, if the explicitly set font is missing some characters, the default font won't be used as a fallback.
    if (result.effectiveFontFamilies.size == 1 && result.fontFamily != FontPreferences.DEFAULT_FONT_NAME) {
      (result as FontPreferencesImpl).register(FontPreferences.DEFAULT_FONT_NAME, result.getSize2D(result.fontFamily))
    }
    return result
  }

  override fun createFontState(fontPreferences: FontPreferences): TerminalFontSettingsState =
    TerminalFontSettingsState(fontPreferences).also {
      it.COLUMN_SPACING = columnSpacing.floatValue
    }

  override fun loadState(state: TerminalFontSettingsState) {
    columnSpacing = TerminalColumnSpacing.ofFloat(state.COLUMN_SPACING)
    super.loadState(state)

    // In the case of RemDev settings are synced from backend to frontend using `loadState` method.
    // So, notify the listeners on every `loadState` to not miss the change.
    fireListeners()
  }

  override fun noStateLoaded() {
    // the state is mostly inherited from the console settings
    val defaultState = TerminalFontSettingsState(AppConsoleFontOptions.getInstance().fontPreferences)
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
      listener.fontSettingsChanged()
    }
  }
}

// The following three classes use the Private Data Class pattern
// to deal with a known Kotlin problem (KT-11914): a data class exposes its private constructor
// through the copy() function because it's always private.
// So we're hiding all data classes as implementation details here,
// exposing only the abstract interfaces.

internal sealed class TerminalFontSize {
  companion object {
    private val validRange: ClosedFloatingPointRange<Float>
      get() = EditorFontsConstants.getMinEditorFontSize().toFloat()..EditorFontsConstants.getMaxEditorFontSize().toFloat()

    fun ofFloat(value: Float): TerminalFontSize =
      TerminalFontSizeImpl(TerminalSettingsFloatValueImpl.ofFloat(value, digits = FONT_SIZE_PRECISION).coerceIn(validRange))

    fun parse(value: String): TerminalFontSize = TerminalFontSizeImpl(TerminalSettingsFloatValueImpl.parse(
      value = value,
      defaultValue = EditorFontsConstants.getDefaultEditorFontSize().toFloat(),
      digits = FONT_SIZE_PRECISION,
    ).coerceIn(validRange))
  }

  abstract val floatValue: Float
  abstract val intValue: Int
  abstract fun toFormattedString(): String

  fun scale(): TerminalFontSize = ofFloat(UISettingsUtils.getInstance().scaleFontSize(floatValue))

  private data class TerminalFontSizeImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalFontSize() {
    override val floatValue: Float get() = impl.toFloat()
    override val intValue: Int get() = floatValue.roundToInt()
    override fun toFormattedString(): String = impl.toFormattedString()
  }
}

// these two are the same, but we don't want them to be mutually assignable

internal sealed class TerminalLineSpacing {
  companion object {
    private val validRange: ClosedFloatingPointRange<Float>
      get() = EditorFontsConstants.getMinEditorLineSpacing()..EditorFontsConstants.getMaxEditorLineSpacing()

    fun ofFloat(value: Float): TerminalLineSpacing =
      TerminalLineSpacingImpl(TerminalSettingsFloatValueImpl.ofFloat(value, digits = SPACING_PRECISION).coerceIn(validRange))

    fun parse(value: String): TerminalLineSpacing = TerminalLineSpacingImpl(TerminalSettingsFloatValueImpl.parse(
      value = value,
      defaultValue = 1.0f,
      digits = SPACING_PRECISION,
    ).coerceIn(validRange))
  }

  abstract val floatValue: Float
  abstract fun toFormattedString(): String

  private data class TerminalLineSpacingImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalLineSpacing() {
    override val floatValue: Float get() = impl.toFloat()
    override fun toFormattedString(): String = impl.toFormattedString()
  }
}

internal sealed class TerminalColumnSpacing {
  companion object {
    // there are no default column spacing values in the API,
    // but these will do just fine
    private val validRange: ClosedFloatingPointRange<Float>
      get() = EditorFontsConstants.getMinEditorLineSpacing()..EditorFontsConstants.getMaxEditorLineSpacing()

    fun ofFloat(value: Float): TerminalColumnSpacing =
      TerminalColumnSpacingImpl(TerminalSettingsFloatValueImpl.ofFloat(value, digits = SPACING_PRECISION).coerceIn(validRange))

    fun parse(value: String): TerminalColumnSpacing = TerminalColumnSpacingImpl(TerminalSettingsFloatValueImpl.parse(
      value = value,
      defaultValue = 1.0f,
      digits = SPACING_PRECISION,
    ).coerceIn(validRange))
  }

  abstract val floatValue: Float
  abstract fun toFormattedString(): String

  private data class TerminalColumnSpacingImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalColumnSpacing() {
    override val floatValue: Float get() = impl.toFloat()
    override fun toFormattedString(): String = impl.toFormattedString()
  }
}

/**
 * A container for floating-point values with equality support and sensible precision.
 */
private data class TerminalSettingsFloatValueImpl(
  private val rawIntValue: Int,
  private val digits: Int,
) {
  companion object {
    fun ofFloat(value: Float, digits: Int): TerminalSettingsFloatValueImpl =
      TerminalSettingsFloatValueImpl(rawIntValue = (value * multiplier(digits)).roundToInt(), digits = digits)

    fun parse(value: String, defaultValue: Float, digits: Int): TerminalSettingsFloatValueImpl =
      try {
        ofFloat(value.toFloat(), digits)
      }
      catch (_: Exception) {
        ofFloat(defaultValue, digits)
      }

    private fun multiplier(digits: Int): Float = 10f.pow(digits)
  }

  private val multiplier: Float = multiplier(digits)

  private val actualDigits: Int
    get() {
      var actualDigits = digits
      var value = rawIntValue
      while (actualDigits > 1 && value % 10 == 0) {
        --actualDigits
        value /= 10
      }
      return actualDigits
    }

  fun coerceIn(range: ClosedFloatingPointRange<Float>): TerminalSettingsFloatValueImpl =
    ofFloat(toFloat().coerceIn(range), digits)

  fun toFloat(): Float = rawIntValue.toFloat() / multiplier

  fun toFormattedString(): String = String.format(Locale.ROOT, "%.${actualDigits}f", toFloat())
}

@ApiStatus.Internal
interface TerminalFontSettingsListener {
  fun fontSettingsChanged()
}

// All this weirdness with similar nondescript names like TerminalFontSettings(Service(State))
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
// The rest was named to somehow indicate the fact that all this stuff is related to TerminalFontSettings.
// Therefore, our AppFontOptions thing became TerminalFontSettingsService,
// as it's exactly what it is: a service for maintaining the font settings,
// and its PersistentFontPreferences are named TerminalFontSettingsState,
// as, again, it's exactly what it is.

internal data class TerminalFontSettings(
  val fontFamily: String,
  val fallbackFontFamily: String,
  val fontSize: TerminalFontSize,
  val lineSpacing: TerminalLineSpacing,
  val columnSpacing: TerminalColumnSpacing,
) {
  constructor(preferences: FontPreferences, columnSpacing: TerminalColumnSpacing) : this(
    fontFamily = preferences.fontFamily,
    fallbackFontFamily = preferences.effectiveFontFamilies.getOrNull(1) ?: FontPreferences.DEFAULT_FONT_NAME,
    fontSize = TerminalFontSize.ofFloat(preferences.getSize2D(preferences.fontFamily)),
    lineSpacing = TerminalLineSpacing.ofFloat(preferences.lineSpacing),
    columnSpacing = columnSpacing,
  )

  fun copyTo(preferences: FontPreferencesImpl) {
    preferences.clearFonts()
    copyMainFont(this, preferences)
    copyFallbackFont(this, preferences)
    copyFontSize(this, preferences)
    copyLineSpacing(this, preferences)
  }
}

private fun copyMainFont(settings: TerminalFontSettings, preferences: FontPreferencesImpl) {
  preferences.addFontFamily(settings.fontFamily)
  // These two are not really used by the terminal at the moment,
  // but are needed so that the families are saved,
  // otherwise migration in com.intellij.openapi.editor.colors.impl.AppFontOptions.copyState
  // will be triggered, and that can mess up the saved settings completely (IJPL-184027).
  val regularSubFamily = FontFamilyService.getRecommendedSubFamily(settings.fontFamily)
  preferences.regularSubFamily = regularSubFamily
  val boldSubFamily = FontFamilyService.getRecommendedBoldSubFamily(settings.fontFamily, regularSubFamily)
  preferences.boldSubFamily = boldSubFamily
}

private fun copyFallbackFont(settings: TerminalFontSettings, preferences: FontPreferencesImpl) {
  if (settings.fallbackFontFamily != settings.fontFamily) {
    preferences.addFontFamily(settings.fallbackFontFamily)
  }
}

private fun copyFontSize(settings: TerminalFontSettings, preferences: FontPreferencesImpl) {
  for (fontFamily in preferences.effectiveFontFamilies) {
    preferences.setFontSize(fontFamily, settings.fontSize.floatValue)
  }
}

private fun copyLineSpacing(settings: TerminalFontSettings, preferences: FontPreferencesImpl) {
  preferences.lineSpacing = settings.lineSpacing.floatValue
}

@ApiStatus.Internal
class TerminalFontSettingsState: AppEditorFontOptions.PersistentFontPreferences {
  @Suppress("unused") // for serialization
  constructor(): super() {
    LINE_SPACING = DEFAULT_TERMINAL_LINE_SPACING.floatValue // to ensure that values different from OUR default are saved
    SECONDARY_FONT_FAMILY = FontPreferences.DEFAULT_FONT_NAME
  }

  constructor(fontPreferences: FontPreferences): super(fontPreferences)

  var COLUMN_SPACING: Float = DEFAULT_TERMINAL_COLUMN_SPACING.floatValue
}

internal val DEFAULT_TERMINAL_FONT_SIZE: TerminalFontSize get() = TerminalFontSize.ofFloat(FontPreferences.DEFAULT_FONT_SIZE.toFloat())
internal val DEFAULT_TERMINAL_LINE_SPACING = TerminalLineSpacing.ofFloat(1.0f)
internal val DEFAULT_TERMINAL_COLUMN_SPACING = TerminalColumnSpacing.ofFloat(1.0f)
private const val FONT_SIZE_PRECISION = 1
private const val SPACING_PRECISION = 2
