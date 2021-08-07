// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.analysis

import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.analysis.UNeDfaConfiguration
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.test.java.AbstractJavaUastLightTest

abstract class AbstractStringEvaluatorTest : AbstractJavaUastLightTest() {
  protected val PartiallyKnownString.debugConcatenation: String
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

  protected fun doTest(
    @Language("Java", prefix = """@SuppressWarnings("ALL")""") source: String,
    expected: String,
    additionalSetup: () -> Unit = {},
    configuration: () -> UNeDfaConfiguration<PartiallyKnownString> = { UNeDfaConfiguration() },
    additionalAssertions: (PartiallyKnownString) -> Unit = {}
  ) {
    additionalSetup()
    val file = myFixture.configureByText("myFile.java", source)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.getUastParentOfType<UReturnExpression>()?.returnExpression
                         ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, configuration()) ?: fail("Cannot evaluate string")
    assertEquals(expected, pks.debugConcatenation)
    additionalAssertions(pks)
  }
}