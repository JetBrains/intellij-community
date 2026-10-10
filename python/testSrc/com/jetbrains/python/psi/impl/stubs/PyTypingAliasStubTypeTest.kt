// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.idea.TestFor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyFileImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFor(classes = [PyTypingAliasStubType::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyTypingAliasStubTypeTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-92840"])
  fun `assigned value is the same with the stub and with the AST`() {
    val vFile = myFixture.addFileToProject("aliases.py", """
      import typing
      from typing import TYPE_CHECKING, TypeAlias

      Alias = int
      Qualified = typing.List
      Explicit: TypeAlias = int
      Commented = int  # type: TypeAlias
      if TYPE_CHECKING:
          Checked = int
      Annotated: type[int] = int
      First = Second = int
      Unpacked, _ = int, str
      (Walrus := int)
      __special__ = int


      class C:
          Nested = int
          annotated: type[int] = int

          def __init__(self, new_index):
              self.index = new_index
    """.trimIndent()).virtualFile
    val expected = mapOf(
      "Alias" to "int",
      "Qualified" to "typing.List",
      "Explicit" to "int",
      "Commented" to "int",
      "Checked" to "int",
      "Annotated" to null,
      "First" to null,
      "Second" to null,
      "Unpacked" to null,
      "Walrus" to null,
      "__special__" to null,
      "C.Nested" to "int",
      "C.annotated" to null,
      "C.self.index" to null,
    )

    val file = runReadActionBlocking { myFixture.psiManager.findFile(vFile) as PyFileImpl }
    val targets = runReadActionBlocking { expected.keys.associateWith { findTarget(file, it) } }
    val stubValues = runReadActionBlocking {
      assertNull(file.treeElement, "The AST must not be loaded")
      targets.mapValues { (name, target) ->
        assertNotNull(target.stub, "$name must have a stub")
        PyTypingAliasStubType.getAssignedValueStubLike(target)?.text
      }
    }

    val modificationCount = runReadActionBlocking {
      assertNotNull(file.node)
      myFixture.psiManager.modificationTracker.modificationCount
    }
    WriteAction.runAndWait<Throwable> { myFixture.psiManager.dropPsiCaches() }
    val astValues = runReadActionBlocking {
      assertTrue(myFixture.psiManager.modificationTracker.modificationCount > modificationCount, "The cached values must be dropped")
      targets.mapValues { (name, target) ->
        assertNull(target.stub, "$name must use the AST")
        PyTypingAliasStubType.getAssignedValueStubLike(target)?.text
      }
    }

    assertEquals(expected, astValues)
    assertEquals(expected, stubValues)
  }

  private fun findTarget(file: PyFile, name: String): PyTargetExpression {
    val parts = name.split('.')
    return when (parts.size) {
      1 -> file.findTopLevelAttribute(name)
      2 -> file.findTopLevelClass(parts[0])!!.findClassAttribute(parts[1], false, null)
      else -> file.findTopLevelClass(parts[0])!!.findInstanceAttribute(parts[2], false)
    } ?: error("No target $name")
  }
}
