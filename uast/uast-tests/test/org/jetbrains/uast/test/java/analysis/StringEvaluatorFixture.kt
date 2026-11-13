// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.analysis.UNeDfaConfiguration
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.getUastParentOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail

/**
 * Renders the segments of a [PartiallyKnownString]: a known segment in quotes,
 * the sorted possible values of an unknown segment in braces, and `NULL` for an unknown segment without them.
 */
val PartiallyKnownString.debugConcatenation: String
  get() = buildString {
    for (segment in segments) {
      when (segment) {
        is StringEntry.Known -> append("'").append(segment.value).append("'")
        is StringEntry.Unknown -> {
          segment.possibleValues
            ?.map { it.debugConcatenation }
            ?.sorted()
            ?.joinTo(this, "|", "{", "}") { it }
          ?: append("NULL")
        }
      }
    }
  }

/**
 * The fixture of the [UStringEvaluator] tests. Create it with [stringEvaluatorFixture].
 */
class StringEvaluatorFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Calls [additionalSetup], configures `myFile.java` with [source], and evaluates the returned expression at the caret.
   * Then it compares the [debugConcatenation] of the value with [expected], and calls [additionalAssertions].
   * The evaluation and the checks run in a read action.
   */
  fun doTest(
    @Language("Java", prefix = """@SuppressWarnings("ALL")""") source: String,
    expected: String,
    additionalSetup: () -> Unit = {},
    configuration: () -> UNeDfaConfiguration<PartiallyKnownString> = { UNeDfaConfiguration() },
    additionalAssertions: (PartiallyKnownString) -> Unit = {},
  ) {
    additionalSetup()
    val file = fixture.configureByText("myFile.java", source)
    ReadAction.runBlocking<RuntimeException> {
      val elementAtCaret = file.findElementAt(fixture.caretOffset)?.getUastParentOfType<UReturnExpression>()?.returnExpression
                           ?: fail("Cannot find UElement at caret")
      val pks = UStringEvaluator().calculateValue(elementAtCaret, configuration()) ?: fail("Cannot evaluate string")
      assertEquals(expected, pks.debugConcatenation)
      additionalAssertions(pks)
    }
  }
}

/**
 * Creates a [StringEvaluatorFixture] for one test. Declare it as an instance field.
 */
fun stringEvaluatorFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<StringEvaluatorFixture> =
  testFixture("stringEvaluatorFixture") {
    initialized(StringEvaluatorFixture(lightJavaCodeInsightFixture(projectFixture).init())) {}
  }
