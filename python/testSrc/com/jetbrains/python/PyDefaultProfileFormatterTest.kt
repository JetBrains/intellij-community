// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.formatter.PyClassicStyleGuide
import com.jetbrains.python.formatter.PyCodeStylePropertyAccessor
import com.jetbrains.python.formatter.PyCodeStyleSettings
import com.jetbrains.python.formatter.PyDefaultStyleGuide
import com.jetbrains.python.formatter.pyCodeStyleProfile
import com.jetbrains.python.formatter.pyCommonSettings
import com.jetbrains.python.formatter.pyCustomSettings
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the PY-85946 "default" Python code style profile: the profile field map, the
 * hanging-indent behavior in [com.jetbrains.python.formatter.PyBlock], and the profile serialization
 * round-trip.
 */
@TestApplication
internal class PyDefaultProfileFormatterTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)

    @Suppress("unused")
    private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(project, tempDir)

  // --- Profile field map ---

  @Test
  @TestFor(issues = ["PY-85946"])
  fun `default profile sets Black-like values`() {
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)

    val custom = settings.getCustomSettings(PyCodeStyleSettings::class.java)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, custom.LIST_WRAPPING)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, custom.DICT_WRAPPING)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, custom.FROM_IMPORT_WRAPPING)
    assertTrue(custom.USE_TRAILING_COMMA_IN_COLLECTIONS)
    assertTrue(custom.USE_TRAILING_COMMA_IN_PARAMETER_LIST)
    assertTrue(custom.LIST_NEW_LINE_BEFORE_RIGHT_BRACKET)
    assertTrue(custom.TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS)
    assertTrue(custom.FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS)
    assertTrue(custom.DICT_NEW_LINE_AFTER_LEFT_BRACE)
    assertTrue(custom.NEW_LINE_AFTER_COLON)
    assertFalse(custom.ALIGN_COLLECTIONS_AND_COMPREHENSIONS)
    assertFalse(custom.USE_CONTINUATION_INDENT_FOR_PARAMETERS)
    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, custom.CODE_STYLE_PROFILE)

    val common = settings.getCommonSettings(PythonLanguage.getInstance())
    assertEquals(88, common.RIGHT_MARGIN)
    assertEquals(listOf(88), common.softMargins)
    assertTrue(common.WRAP_LONG_LINES)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, common.METHOD_PARAMETERS_WRAP)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, common.CALL_PARAMETERS_WRAP)
    assertTrue(common.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
    assertTrue(common.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE)
    assertTrue(common.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    assertFalse(common.ALIGN_MULTILINE_PARAMETERS)
    assertFalse(common.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)
  }

  @Test
  @TestFor(issues = ["PY-85946"])
  fun `classic profile keeps today's values`() {
    val settings = CodeStyle.createTestSettings()
    PyClassicStyleGuide.apply(settings)

    val custom = settings.getCustomSettings(PyCodeStyleSettings::class.java)
    assertEquals(CommonCodeStyleSettings.WRAP_AS_NEEDED, custom.LIST_WRAPPING)
    assertFalse(custom.USE_TRAILING_COMMA_IN_COLLECTIONS)
    assertTrue(custom.ALIGN_COLLECTIONS_AND_COMPREHENSIONS)
    assertTrue(custom.USE_CONTINUATION_INDENT_FOR_PARAMETERS)

    val common = settings.getCommonSettings(PythonLanguage.getInstance())
    assertEquals(-1, common.RIGHT_MARGIN)
    assertTrue(common.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)
  }

  // --- Serialization round-trip ---

  @Test
  @TestFor(issues = ["PY-85946"])
  fun `default profile serialization round-trip`() {
    val source = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(source)

    val element = Element("code_scheme")
    source.writeExternal(element)

    val restored = CodeStyle.createTestSettings()
    restored.readExternal(element)

    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, restored.pyCodeStyleProfile())
    val restoredCustom = restored.getCustomSettings(PyCodeStyleSettings::class.java)
    assertEquals(PyCodeStyleSettings.CHOP_DOWN_IF_LONG, restoredCustom.LIST_WRAPPING)
    assertTrue(restoredCustom.USE_TRAILING_COMMA_IN_COLLECTIONS)

    val restoredCommon = restored.pyCommonSettings
    assertNotNull(restoredCommon)
    assertEquals(88, restoredCommon!!.RIGHT_MARGIN)
    assertEquals(listOf(88), restoredCommon.softMargins)
    assertTrue(restoredCommon.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE)
  }

  @Test
  @TestFor(issues = ["PY-85946"])
  fun `classic profile serialization round-trip`() {
    val source = CodeStyle.createTestSettings()
    PyClassicStyleGuide.apply(source)

    val element = Element("code_scheme")
    source.writeExternal(element)

    val restored = CodeStyle.createTestSettings()
    restored.readExternal(element)

    assertEquals(PyClassicStyleGuide.CODE_STYLE_ID, restored.pyCodeStyleProfile())
    val restoredCustom = restored.getCustomSettings(PyCodeStyleSettings::class.java)
    assertEquals(CommonCodeStyleSettings.WRAP_AS_NEEDED, restoredCustom.LIST_WRAPPING)
    assertFalse(restoredCustom.USE_TRAILING_COMMA_IN_COLLECTIONS)
    assertTrue(restoredCustom.ALIGN_COLLECTIONS_AND_COMPREHENSIONS)
    assertTrue(restoredCustom.USE_CONTINUATION_INDENT_FOR_PARAMETERS)

    val restoredCommon = restored.pyCommonSettings
    assertNotNull(restoredCommon)
    assertEquals(-1, restoredCommon!!.RIGHT_MARGIN)
    assertEquals(emptyList<Int>(), restoredCommon.softMargins)
    assertTrue(restoredCommon.ALIGN_MULTILINE_PARAMETERS_IN_CALLS)
  }

  /**
   * Backward compatibility: a scheme serialized before PY-85946 carries no code style profile id.
   * It must read back with the user's explicit deviations preserved, no active profile, and the legacy
   * ("classic") baseline for every field the user never touched.
   */
  @Test
  @TestFor(issues = ["PY-85946"])
  fun `legacy scheme without profile reads back unchanged`() {
    val source = CodeStyle.createTestSettings()
    val sourceCustom = source.getCustomSettings(PyCodeStyleSettings::class.java)
    assertNull(sourceCustom.CODE_STYLE_PROFILE)
    // Explicit deviations an old user might have saved (one custom field, one common field).
    sourceCustom.USE_TRAILING_COMMA_IN_COLLECTIONS = true
    source.getCommonSettings(PythonLanguage.getInstance()).RIGHT_MARGIN = 100

    val element = Element("code_scheme")
    source.writeExternal(element)
    // A legacy scheme writes nothing about the new profile field, so old IDE builds can still read it.
    assertFalse(JDOMUtil.write(element).contains("CODE_STYLE_PROFILE"))

    val restored = CodeStyle.createTestSettings()
    restored.readExternal(element)

    assertNull(restored.pyCodeStyleProfile())
    val restoredCustom = restored.getCustomSettings(PyCodeStyleSettings::class.java)
    assertNull(restoredCustom.CODE_STYLE_PROFILE)
    // The explicit deviation survives the round-trip...
    assertTrue(restoredCustom.USE_TRAILING_COMMA_IN_COLLECTIONS)
    assertEquals(100, restored.getCommonSettings(PythonLanguage.getInstance()).RIGHT_MARGIN)
    // ...while untouched fields keep their legacy values instead of silently adopting the new defaults.
    assertEquals(CommonCodeStyleSettings.WRAP_AS_NEEDED, restoredCustom.LIST_WRAPPING)
    assertTrue(restoredCustom.ALIGN_COLLECTIONS_AND_COMPREHENSIONS)
    assertTrue(restoredCustom.USE_CONTINUATION_INDENT_FOR_PARAMETERS)
  }

  // --- .editorconfig property round-trip ---

  /**
   * The profile choice is exposed to .editorconfig through [PyCodeStylePropertyAccessor] as
   * `ij_python_code_style_profile`. Reading reflects the active profile; writing switches the whole
   * profile; an unknown value is rejected.
   */
  @Test
  @TestFor(issues = ["PY-85946"])
  fun `code style profile round-trips through the editorconfig property`() {
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)

    val accessor = PyCodeStylePropertyAccessor(settings.pyCustomSettings)
    assertEquals("code_style_profile", accessor.propertyName)
    assertEquals(listOf(PyDefaultStyleGuide.CODE_STYLE_ID, PyClassicStyleGuide.CODE_STYLE_ID), accessor.choices)
    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, accessor.get())

    // Writing the property switches the whole profile.
    assertTrue(accessor.set(PyClassicStyleGuide.CODE_STYLE_ID))
    assertEquals(PyClassicStyleGuide.CODE_STYLE_ID, accessor.get())
    assertEquals(CommonCodeStyleSettings.WRAP_AS_NEEDED, settings.pyCustomSettings.LIST_WRAPPING)
    assertTrue(settings.pyCustomSettings.ALIGN_COLLECTIONS_AND_COMPREHENSIONS)

    // An unknown id is rejected and leaves the active profile untouched.
    assertFalse(accessor.set("bogus"))
    assertEquals(PyClassicStyleGuide.CODE_STYLE_ID, accessor.get())
  }

  // --- Profile id survives cloning and common-settings serialization ---

  /**
   * The profile id on the common (language-shared) settings is only preserved through cloning if
   * `PyCommonCodeStyleSettings.CODE_STYLE_PROFILE` is a real public field (`@JvmField`): the platform
   * clones common settings via `copyPublicFields`, which copies public Java fields only. A plain Kotlin
   * `var` would compile to a private backing field and silently drop the id.
   */
  @Test
  @TestFor(issues = ["PY-85946"])
  fun `default profile survives settings clone`() {
    val source = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(source)
    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, source.pyCodeStyleProfile())

    val cloned = source.clone()

    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, cloned.pyCodeStyleProfile())
    assertNotNull(cloned.pyCommonSettings)
    assertEquals(PyDefaultStyleGuide.CODE_STYLE_ID, cloned.pyCommonSettings!!.CODE_STYLE_PROFILE)
  }

  /**
   * The classic profile has no soft margins, so its common-settings profile id must be serialized by
   * `DefaultJDOMExternalizer` (public-field reflection), not the soft-margins-only `XmlSerializer`
   * fallback. Before `CODE_STYLE_PROFILE` was a public `@JvmField`, the id was lost here.
   */
  @Test
  @TestFor(issues = ["PY-85946"])
  fun `classic profile common settings retain profile id without soft margins`() {
    val source = CodeStyle.createTestSettings()
    PyClassicStyleGuide.apply(source)
    assertNotNull(source.pyCommonSettings)
    assertTrue(source.pyCommonSettings!!.softMargins.isEmpty())

    val element = Element("code_scheme")
    source.writeExternal(element)

    val restored = CodeStyle.createTestSettings()
    restored.readExternal(element)

    assertNotNull(restored.pyCommonSettings)
    assertEquals(PyClassicStyleGuide.CODE_STYLE_ID, restored.pyCommonSettings!!.CODE_STYLE_PROFILE)
    assertEquals(PyClassicStyleGuide.CODE_STYLE_ID, restored.pyCodeStyleProfile())
  }

  private fun assertReformatted(
    @Language("Python") before: String,
    @Language("Python") expected: String,
    configure: (PyCodeStyleSettings) -> Unit,
  ) {
    val fixture = codeInsightFixture
    fixture.configureByText("a.py", before)

    val tempSettings = CodeStyle.createTestSettings()
    configure(tempSettings.getCustomSettings(PyCodeStyleSettings::class.java))

    CodeStyle.doWithTemporarySettings(fixture.project, tempSettings, Runnable {
      WriteCommandAction.runWriteCommandAction(fixture.project, Runnable {
        CodeStyleManager.getInstance(fixture.project).reformat(fixture.file)
      })
    })
    // Normalize the trailing EOF newline the formatter adds (BLANK_LINE_AT_FILE_END).
    assertEquals(expected.trimEnd(), fixture.file.text.trimEnd())
  }
}
