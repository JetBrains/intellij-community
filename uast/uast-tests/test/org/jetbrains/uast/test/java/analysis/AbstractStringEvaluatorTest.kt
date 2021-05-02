// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.analysis

import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.uast.UElement
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.test.java.AbstractJavaUastLightTest
import org.jetbrains.uast.toUElement

open class AbstractStringEvaluatorTest : AbstractJavaUastLightTest() {
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
    source: String,
    expected: String,
    additionalSetup: () -> Unit = {},
    configuration: () -> UStringEvaluator.Configuration = { UStringEvaluator.Configuration() },
    retrieveElement: UElement?.() -> UElement? = { this },
    additionalAssertions: (PartiallyKnownString) -> Unit = {}
  ) {
    additionalSetup()
    val file = myFixture.configureByText("myFile.java", source)
    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement()?.retrieveElement()
                         ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, configuration()) ?: fail("Cannot evaluate string")
    assertEquals(expected, pks.debugConcatenation)
    additionalAssertions(pks)
  }
}