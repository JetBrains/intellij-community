// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyInferenceVariable
import com.jetbrains.python.psi.types.PyTypeVarTypeImpl
import com.jetbrains.python.psi.types.SubstitutionsIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that [PyInferenceVariable.hashCode] agrees with [PyInferenceVariable.equals], which compares only the name.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-93781"], classes = [PyInferenceVariable::class])
class PyInferenceVariableTest : PyCodeInsightTestCase() {

  @Test
  fun `variables with one name are equal and have one hash code`() {
    val (first, second) = identifiers()
    val a = PyInferenceVariable(PyTypeVarTypeImpl("T", PyAnyType.unknown), first, "α")
    val b = PyInferenceVariable(PyTypeVarTypeImpl("U", PyAnyType.unknown), second, "α")

    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertTrue(b in hashSetOf(a))
  }

  @Test
  fun `variables of one type variable with different names are not equal`() {
    val (first, _) = identifiers()
    val typeVar = PyTypeVarTypeImpl("T", PyAnyType.unknown)
    val a = PyInferenceVariable(typeVar, first, "α")
    val b = PyInferenceVariable(typeVar, first, "β")

    assertNotEquals(a, b)
    assertEquals(2, hashSetOf(a, b).size)
  }

  private fun identifiers(): Pair<SubstitutionsIdentifier, SubstitutionsIdentifier> {
    val file = myFixture.configureByText("a.py", "x = 1\ny = 2") as PyFile
    return runReadActionBlocking {
      val (x, y) = file.topLevelAttributes
      SubstitutionsIdentifier(x) to SubstitutionsIdentifier(y)
    }
  }
}
