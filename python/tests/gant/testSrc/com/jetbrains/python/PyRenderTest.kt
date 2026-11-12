// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.override.PyMethodMember
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Misc tests for how rendering things in certain cases.
 */
@Subsystems.Refactoring
@Layers.Functional
class PyRenderTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-90938"])
  fun `member chooser omits implicit self and cls type`() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
      class A:
          def foo(self, x: int): ...

          @classmethod
          def bar(cls, y): ...
      """.trimIndent())
    runReadActionBlocking {
      val cls = (myFixture.file as PyFile).topLevelClasses.first()
      assertEquals("foo(self, x: int)", PyMethodMember(cls.findMethodByName("foo", false, null)!!).text)
      assertEquals("bar(cls, y)", PyMethodMember(cls.findMethodByName("bar", false, null)!!).text)
    }
  }

  @Test
  @TestFor(issues = ["PY-90938"])
  fun `member chooser keeps explicitly annotated self type`() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
      from typing import Self
      class A:
          def foo(self: Self): ...
      """.trimIndent())
    runReadActionBlocking {
      val cls = (myFixture.file as PyFile).topLevelClasses.first()
      assertEquals("foo(self: Self@A)", PyMethodMember(cls.findMethodByName("foo", false, null)!!).text)
    }
  }
}
