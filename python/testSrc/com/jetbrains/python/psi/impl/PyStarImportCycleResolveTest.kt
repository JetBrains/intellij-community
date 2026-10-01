// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Resolve of a name through a cycle of star imports. Module `a` star-imports `b` and `c`, and `b` star-imports `a`.
 */
@TestFor(classes = [PyFileImpl::class, PyStarImportElementImpl::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyStarImportCycleResolveTest : PyCodeInsightTestCase() {

  private val modules = arrayOf(
    "a.py" to """
      from b import *
      from c import *
    """,
    "b.py" to """
      from a import *
    """,
    "c.py" to """
      NAME = 1
    """,
  )

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `name from a star import cycle resolves in the second module alone`() = test("""
    from b import NAME
    """, *modules)

  @Test
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `mutual star imports keep the names of both modules`() = test("""
    from mod_a import A_NAME, B_NAME

    res_a = A_NAME
    # └ TYPE int
    res_b = B_NAME
    # └ TYPE str
    """.trimIndent(),
    "mod_a.py" to """
    from mod_b import *
    A_NAME: int = 1
    """.trimIndent(),
    "mod_b.py" to """
    from mod_a import *
    B_NAME: str = 's'
    """.trimIndent())

  @Test
  fun `module level resolve in the second module alone`() {
    assertEquals(listOf(1), resolveCounts("b"))
  }

  @Test
  fun `module level resolve in the second module after the cycle was entered from the first module`() {
    // FIXME: [1, 1], because 'b' star-imports 'NAME' from 'a'
    assertEquals(listOf(1, 0), resolveCounts("a", "b"))
  }

  private fun resolveCounts(vararg moduleNames: String): List<Int> {
    for ((name, text) in modules) myFixture.addFileToProject(name, text.trimIndent())
    val disposable = Disposer.newDisposable()
    RecursionManager.disableAssertOnRecursionPrevention(disposable)
    try {
      return runReadActionBlocking {
        moduleNames.map { moduleName ->
          val file = myFixture.psiManager.findFile(myFixture.findFileInTempDir("$moduleName.py"))!! as PyFile
          file.multiResolveName("NAME").size
        }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
