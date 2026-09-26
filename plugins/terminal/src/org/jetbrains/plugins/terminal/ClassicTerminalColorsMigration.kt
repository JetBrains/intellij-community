// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.INHERITED_ATTRS_MARKER
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.options.Scheme
import com.intellij.terminal.BlockTerminalColors
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Migrates user-customized Classic Terminal ANSI colors to their Reworked Terminal counterparts.
 * See [com.intellij.openapi.options.colors.pages.ANSIColoredConsoleColorsPage] for how the two 16-color
 * palettes ([ConsoleHighlighter] for Classic, [BlockTerminalColors] for Reworked) are shown side by side.
 */
@ApiStatus.Internal
object ClassicTerminalColorsMigration {
  // Pairs of (Classic key, Reworked key) for the same logical ANSI color,
  // in the same order/grouping as ANSIColoredConsoleColorsPage#ATTRS / #getReworkedTerminalAttributes.
  @VisibleForTesting
  val ANSI_KEY_PAIRS: List<Pair<TextAttributesKey, TextAttributesKey>> = listOf(
    ConsoleHighlighter.BLACK to BlockTerminalColors.BLACK,
    ConsoleHighlighter.RED to BlockTerminalColors.RED,
    ConsoleHighlighter.GREEN to BlockTerminalColors.GREEN,
    ConsoleHighlighter.YELLOW to BlockTerminalColors.YELLOW,
    ConsoleHighlighter.BLUE to BlockTerminalColors.BLUE,
    ConsoleHighlighter.MAGENTA to BlockTerminalColors.MAGENTA,
    ConsoleHighlighter.CYAN to BlockTerminalColors.CYAN,
    ConsoleHighlighter.GRAY to BlockTerminalColors.WHITE,

    ConsoleHighlighter.DARKGRAY to BlockTerminalColors.BLACK_BRIGHT,
    ConsoleHighlighter.RED_BRIGHT to BlockTerminalColors.RED_BRIGHT,
    ConsoleHighlighter.GREEN_BRIGHT to BlockTerminalColors.GREEN_BRIGHT,
    ConsoleHighlighter.YELLOW_BRIGHT to BlockTerminalColors.YELLOW_BRIGHT,
    ConsoleHighlighter.BLUE_BRIGHT to BlockTerminalColors.BLUE_BRIGHT,
    ConsoleHighlighter.MAGENTA_BRIGHT to BlockTerminalColors.MAGENTA_BRIGHT,
    ConsoleHighlighter.CYAN_BRIGHT to BlockTerminalColors.CYAN_BRIGHT,
    ConsoleHighlighter.WHITE to BlockTerminalColors.WHITE_BRIGHT,
  )

  fun migrateCustomizedColors() {
    for (scheme in EditorColorsManager.getInstance().allSchemes) {
      if (scheme !is AbstractColorsScheme) continue
      migrateCustomizedColors(scheme)
    }
  }

  @VisibleForTesting
  fun migrateCustomizedColors(scheme: AbstractColorsScheme) {
    val baseline = getBaselineScheme(scheme)
    if (baseline == null) {
      return // skip the migration if it is unclear what scheme to consider as an "unmodified base"
    }

    val ownAttributes = scheme.directlyDefinedAttributes
    var migratedAnyColor = false

    for ((classicKey, reworkedKey) in ANSI_KEY_PAIRS) {
      val customized = getCustomizedValue(ownAttributes, baseline, classicKey) ?: continue
      if (getCustomizedValue(ownAttributes, baseline, reworkedKey) != null) continue // don't override existing Reworked customization
      scheme.setAttributes(reworkedKey, customized.clone())
      migratedAnyColor = true
      thisLogger().info(
        "Migrated Classic Terminal ANSI color '${classicKey.externalName}' to Reworked Terminal color " +
        "'${reworkedKey.externalName}' in color scheme '${scheme.name}'"
      )
    }

    if (migratedAnyColor) {
      scheme.setSaveNeeded(true)
    }
  }

  /** Returns [key]'s value in [ownAttributes] if it was customized (differs from [baseline]), else `null`. */
  @VisibleForTesting
  fun getCustomizedValue(
    ownAttributes: Map<String, TextAttributes>,
    baseline: EditorColorsScheme,
    key: TextAttributesKey,
  ): TextAttributes? {
    val ownValue = ownAttributes[key.externalName] ?: return null
    if (ownValue === INHERITED_ATTRS_MARKER) return null
    return ownValue.takeIf { it != baseline.getAttributes(key) }
  }

  /**
   * Returns the scheme to compare [scheme] against, or `null` if there's no safe baseline.
   */
  private fun getBaselineScheme(scheme: AbstractColorsScheme): EditorColorsScheme? {
    if (!scheme.name.startsWith(Scheme.EDITABLE_COPY_PREFIX)) return scheme.parentScheme

    val baseName = Scheme.getBaseName(scheme.name)
    val baseScheme = DefaultColorSchemesManager.getInstance().getScheme(baseName)
                     ?: EditorColorsManager.getInstance().getScheme(baseName)
    return baseScheme?.takeIf { it !== scheme }
  }
}
