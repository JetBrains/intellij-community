// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyTypeParameter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** Find usages and rename reach the quoted type hints that [PyTypingAnnotationInjector] injects. */
@TestFor(classes = [PyTypingAnnotationInjector::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyTypingAnnotationInjectorTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-93096"])
  fun `find usages of a type parameter of a type alias include the quoted occurrences`() = runInEdtAndWait {
    configureWithCaret("""
      type Mapping[T] = dict["T", "list[T]"]
      #            └ CARET
    """)
    val typeParameter = myFixture.elementAtCaret as? PyTypeParameter ?: fail("No type parameter at the caret")
    val usages = myFixture.findUsages(typeParameter).map { it.element!!.text }
    assertEquals(listOf("T", "T"), usages)
  }

  @Test
  @TestFor(issues = ["PY-93096"])
  fun `rename of a type parameter of a type alias updates the quoted occurrences`() = runInEdtAndWait {
    configureWithCaret("""
      type Pair[T] = tuple["T", "list[T]"]
      #         └ CARET
    """)
    myFixture.renameElementAtCaret("Item")
    myFixture.checkResult("""
      type Pair[Item] = tuple["Item", "list[Item]"]
    """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-93096"])
  fun `rename of a type parameter of a function updates the quoted occurrences`() = runInEdtAndWait {
    configureWithCaret("""
      def first[T](items: "list[T]") -> "T":
      #         └ CARET
          return items[0]
    """)
    myFixture.renameElementAtCaret("Item")
    myFixture.checkResult("""
      def first[Item](items: "list[Item]") -> "Item":
          return items[0]
    """.trimIndent())
  }
}
