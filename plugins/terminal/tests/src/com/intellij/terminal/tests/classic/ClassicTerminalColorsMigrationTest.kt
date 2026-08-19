// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.classic

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.BlockTerminalColors
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.jetbrains.plugins.terminal.ClassicTerminalColorsMigration
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font

@TestApplication
internal class ClassicTerminalColorsMigrationTest {
  @Test
  fun `direct UI edit of a classic color is migrated to the reworked counterpart`() {
    val editable = scheme("_@user_Default", defaultScheme)

    // Simulates SchemeTextAttributesDescription.apply(): the user picked a different red in the settings UI.
    val customRed = attrs(baselineForeground(ConsoleHighlighter.RED).inverted())
    editable.setAttributes(ConsoleHighlighter.RED, customRed)

    ClassicTerminalColorsMigration.migrateCustomizedColors(editable)

    assertThat(editable.getDirectlyDefinedAttributes(BlockTerminalColors.RED)).isEqualTo(customRed)
  }

  @Test
  fun `only the actually customized colors are migrated, the rest are left alone`() {
    val editable = scheme("_@user_Default", defaultScheme)
    editable.setAttributes(ConsoleHighlighter.RED, attrs(baselineForeground(ConsoleHighlighter.RED).inverted()))
    // GREEN was never touched by the user.

    ClassicTerminalColorsMigration.migrateCustomizedColors(editable)

    assertThat(editable.directlyDefinedAttributes).containsKey(BlockTerminalColors.RED.externalName)
    assertThat(editable.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.GREEN.externalName)
  }

  @Test
  fun `re-picking the same color as the parent is not stored and so is not migrated`() {
    val editable = scheme("_@user_Default", defaultScheme)
    val baselineGreen = defaultScheme.getDirectlyDefinedAttributes(ConsoleHighlighter.GREEN)!!

    // EditorColorsSchemeImpl.setAttributes() itself is a no-op when the new value equals the parent's -
    // exactly what happens if the user opens the color picker and re-selects the exact same color.
    editable.setAttributes(ConsoleHighlighter.GREEN, baselineGreen.clone())

    ClassicTerminalColorsMigration.migrateCustomizedColors(editable)

    assertThat(editable.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.GREEN.externalName)
  }

  @Test
  fun `an already-customized reworked color is never overwritten`() {
    val editable = scheme("_@user_Default", defaultScheme)
    editable.setAttributes(ConsoleHighlighter.BLUE, attrs(baselineForeground(ConsoleHighlighter.BLUE).inverted()))
    val existingReworkedBlue = attrs(Color.MAGENTA)
    editable.setAttributes(BlockTerminalColors.BLUE, existingReworkedBlue)

    ClassicTerminalColorsMigration.migrateCustomizedColors(editable)

    assertThat(editable.getDirectlyDefinedAttributes(BlockTerminalColors.BLUE)).isEqualTo(existingReworkedBlue)
  }

  @Test
  fun `a bundled scheme with no parent is never treated as customized`() {
    val bundled = scheme("Darcula-like") {
      for ((classicKey, _) in ClassicTerminalColorsMigration.ANSI_KEY_PAIRS) {
        setAttributes(classicKey, attrs(Color.PINK))
      }
    }

    ClassicTerminalColorsMigration.migrateCustomizedColors(bundled)

    for ((_, reworkedKey) in ClassicTerminalColorsMigration.ANSI_KEY_PAIRS) {
      assertThat(bundled.directlyDefinedAttributes).doesNotContainKey(reworkedKey.externalName)
    }
  }

  @Test
  fun `explicitly resetting a color to inherited is not migrated`() {
    val editable = scheme("_@user_Default", defaultScheme)
    editable.setAttributes(ConsoleHighlighter.MAGENTA, AbstractColorsScheme.INHERITED_ATTRS_MARKER)

    ClassicTerminalColorsMigration.migrateCustomizedColors(editable)

    assertThat(editable.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.MAGENTA.externalName)
  }

  @Test
  fun `a scheme duplicated from a customized scheme (Save As) is also migrated`() {
    // Uses the real duplication mechanism - EditorColorsSchemeImpl#clone(), the same method ColorAndFontOptions#saveSchemeAs calls.
    // clone() creates a sibling scheme with the SAME parent and bulk-copies the source's own attributes map via
    // AbstractColorsScheme#copyTo, not a parent-child relationship to the scheme it was duplicated from.
    val customCyan = attrs(baselineForeground(ConsoleHighlighter.CYAN).inverted())
    val customTheme = scheme("My Theme", defaultScheme) {
      setAttributes(ConsoleHighlighter.CYAN, customCyan)
    }

    val duplicated = customTheme.clone() as EditorColorsSchemeImpl
    duplicated.name = "My Theme Copy"

    ClassicTerminalColorsMigration.migrateCustomizedColors(duplicated)

    assertThat(duplicated.getDirectlyDefinedAttributes(BlockTerminalColors.CYAN)).isEqualTo(customCyan)
  }

  @Test
  fun `an imported color equal to the parent's is not migrated`() {
    val imported = scheme("Imported", defaultScheme)
    val baselineYellow = defaultScheme.getDirectlyDefinedAttributes(ConsoleHighlighter.YELLOW)!!

    // Simulates AbstractColorsScheme#readAttributes populating the attributes' map directly from a .icls
    // file, bypassing the setAttributes() guard - an exported scheme can restate a value equal to the parent's.
    imported.readAttributes(attributesElement(ConsoleHighlighter.YELLOW, baselineYellow))
    assertThat(imported.getDirectlyDefinedAttributes(ConsoleHighlighter.YELLOW)).isEqualTo(baselineYellow)

    ClassicTerminalColorsMigration.migrateCustomizedColors(imported)

    assertThat(imported.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.YELLOW.externalName)
  }

  @Test
  fun `an imported color that actually differs from the parent's is migrated`() {
    val imported = scheme("Imported", defaultScheme)
    val differentWhite = attrs(baselineForeground(ConsoleHighlighter.WHITE).inverted())

    imported.readAttributes(attributesElement(ConsoleHighlighter.WHITE, differentWhite))

    ClassicTerminalColorsMigration.migrateCustomizedColors(imported)

    assertThat(imported.getDirectlyDefinedAttributes(BlockTerminalColors.WHITE_BRIGHT)).isEqualTo(differentWhite)
  }

  @Test
  fun `an editable copy of a registered scheme is compared against that scheme, not its structural parent`() {
    val grandparent = scheme("TestDarcula") {
      setAttributes(ConsoleHighlighter.RED, attrs(Color(0x11, 0x11, 0x11)))
    }
    val bundledTheme = scheme("TestHighContrastTheme", grandparent) {
      // Intentionally differs from its own parent (grandparent) by theme design, not by user action.
      setAttributes(ConsoleHighlighter.RED, attrs(Color(0xFA, 0x32, 0x32)))
    }

    val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    manager.addColorScheme(bundledTheme)
    try {
      val editableCopy = bundledTheme.clone() as EditorColorsSchemeImpl
      editableCopy.name = "_@user_TestHighContrastTheme"
      // Sanity check on the premise: the copy's parent is the BUNDLED THEME'S OWN parent, not the theme itself.
      assertThat(editableCopy.parentScheme).isSameAs(grandparent)

      ClassicTerminalColorsMigration.migrateCustomizedColors(editableCopy)

      assertThat(editableCopy.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.RED.externalName)
    }
    finally {
      manager.removeScheme(bundledTheme)
    }
  }

  @Test
  fun `an editable copy whose base name isn't currently resolvable is skipped rather than compared against its structural parent`() {
    // Safety net for the case above: if the "_@user_X" name can't be resolved to a real scheme right now,
    // we can't tell whether the structural parent is a safe comparison.
    val grandparent = scheme("TestDarcula") {
      setAttributes(ConsoleHighlighter.RED, attrs(Color(0x11, 0x11, 0x11)))
    }
    val editableCopy = scheme("_@user_SomeUnregisteredTheme", grandparent) {
      setAttributes(ConsoleHighlighter.RED, attrs(Color(0xFA, 0x32, 0x32)))
    }

    ClassicTerminalColorsMigration.migrateCustomizedColors(editableCopy)

    assertThat(editableCopy.directlyDefinedAttributes).doesNotContainKey(BlockTerminalColors.RED.externalName)
  }

  private val defaultScheme: AbstractColorsScheme
    get() = EditorColorsManager.getInstance().defaultScheme as AbstractColorsScheme

  private fun baselineForeground(key: TextAttributesKey): Color {
    return defaultScheme.getDirectlyDefinedAttributes(key)!!.foregroundColor!!
  }

  private fun Color.inverted(): Color = Color(255 - red, 255 - green, 255 - blue)

  private fun scheme(
    name: String,
    parent: AbstractColorsScheme? = null,
    configure: EditorColorsSchemeImpl.() -> Unit = {},
  ): EditorColorsSchemeImpl {
    return EditorColorsSchemeImpl(parent).also {
      it.name = name
      it.configure()
    }
  }

  private fun attrs(foreground: Color): TextAttributes {
    return TextAttributes(foreground, null, null, EffectType.BOXED, Font.PLAIN)
  }

  /**
   * Builds an `<attributes>` element the same way real scheme XML is produced (see
   * [AbstractColorsScheme.writeAttribute]), so [attributes] round-trips through
   * [AbstractColorsScheme.readAttributes] exactly as it would for a real `.icls` import - regardless of
   * which of foreground/background/effect/font-type components [attributes] actually has set.
   */
  private fun attributesElement(key: TextAttributesKey, attributes: TextAttributes): Element {
    val value = Element("value")
    attributes.writeExternal(value)
    return Element("attributes").addContent(
      Element("option").setAttribute("name", key.externalName).addContent(value)
    )
  }
}