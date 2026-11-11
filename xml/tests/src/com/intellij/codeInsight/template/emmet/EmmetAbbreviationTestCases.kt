// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Named
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.json.JsonReadFeature
import java.io.File
import java.io.IOException

/**
 * One case of an Emmet abbreviation test.
 * [EmmetAbbreviationTestFixture.checkAbbreviation] runs it.
 * Create it with [EmmetAbbreviationTestCases].
 */
@TestOnly
class EmmetAbbreviationTestCase internal constructor(
  /**
   * The text of the test file with the abbreviation.
   * Without a caret marker, the caret goes to the end of the line.
   */
  val source: String,
  /**
   * The expected text.
   * Without a caret marker, each `|` marks a tab stop, and the expected text is expanded and reformatted like the source.
   */
  val expected: String,
  /**
   * The extension of the test file, for example `html`.
   */
  val extension: String,
  /**
   * When `true`, the test compares the tab stops of the expansion with the `|` markers of [expected].
   */
  val checkPosition: Boolean,
  /**
   * The setup of this case. It runs before the expansion.
   */
  val initializer: EmmetAbbreviationTestCases.TestInitializer?,
)

/**
 * Collects the [EmmetAbbreviationTestCase] items of a parameterized Emmet test.
 * A test class extends it in a nested class, adds the cases in the constructor,
 * and returns [toNamedCases] from the `@MethodSource` method.
 * The name of each case is `<name> in <extension>`. By default, the name is the source.
 */
@TestOnly
open class EmmetAbbreviationTestCases {
  private val cases = ArrayList<Named<EmmetAbbreviationTestCase>>()

  /**
   * The setup of one case, for example a code style option.
   * Register the restore of a changed option on `testRootDisposable`.
   */
  fun interface TestInitializer {
    fun init(fixture: CodeInsightTestFixture, testRootDisposable: Disposable)
  }

  /**
   * Adds a case for each key of the JSON object in [filePath] and each of [extensions].
   * A key can hold several sources, separated with `|`. The value is the expected text, with `${1:text}` replaced by `text`.
   * When the file cannot be read, a case that fails with the error message takes the place of the cases.
   */
  @Throws(IOException::class)
  protected fun addTestFromJson(filePath: String, vararg extensions: String) {
    val factory = JsonFactory.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
    try {
      factory.createParser(ObjectReadContext.empty(), File(filePath)).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) {
          throw IOException("Unexpected JSON format")
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          val key = parser.string
          parser.nextToken()
          var expected = parser.string
          for (source in StringUtil.split(key, "|")) {
            expected = expected.replace(Regex("\\$\\{\\d(:([^}]+))?}"), "$2")
            addTest(source, expected, *extensions)
          }
        }
      }
    }
    catch (e: IOException) {
      addTest(e.message.orEmpty(), "$filePath file was found.", *extensions)
    }
    catch (e: JacksonException) {
      addTest(e.message.orEmpty(), "$filePath file was found.", *extensions)
    }
  }

  protected open fun addTest(source: String, expected: String, vararg extensions: String) {
    addTest(source, expected, null, *extensions)
  }

  protected fun addTest(source: String, expected: String, setUp: TestInitializer?, vararg extensions: String) {
    for (extension in extensions) {
      add(source, expected, source, extension, false, setUp)
    }
  }

  protected fun addTestWithPositionCheck(source: String, expected: String, vararg extensions: String) {
    addTestWithPositionCheck(source, expected, null, *extensions)
  }

  protected fun addTestWithPositionCheck(source: String, expected: String, setUp: TestInitializer?, vararg extensions: String) {
    for (extension in extensions) {
      add(source, expected, source, extension, true, setUp)
    }
  }

  protected fun addTestWithName(source: String, name: String, expected: String, setUp: TestInitializer?, extension: String) {
    add(source, expected, name, extension, false, setUp)
  }

  protected fun addTestWithNameWithPositionCheck(source: String, name: String, expected: String, setUp: TestInitializer?, extension: String) {
    add(source, expected, name, extension, true, setUp)
  }

  /**
   * Returns the cases in the order of the `add` calls, named for the display in the test report.
   */
  fun toNamedCases(): List<Named<EmmetAbbreviationTestCase>> = cases.toList()

  private fun add(source: String, expected: String, name: String, extension: String, checkPosition: Boolean, setUp: TestInitializer?) {
    val displayName = "$name in $extension".replace('\n', ' ').replace("  ", "")
    cases.add(Named.of(displayName, EmmetAbbreviationTestCase(source, expected, extension, checkPosition, setUp)))
  }

  companion object {
    /**
     * Sets the HTML quote style of the project code style settings.
     */
    @JvmStatic
    fun quoteStyle(newStyle: CodeStyleSettings.QuoteStyle): TestInitializer = TestInitializer { fixture, _ ->
      CodeStyle.getSettings(fixture.project).getCustomSettings(HtmlCodeStyleSettings::class.java).HTML_QUOTE_STYLE = newStyle
    }
  }
}
