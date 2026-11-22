// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.FileContentUtilCore
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.types.PyAnyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyBuiltinCache::class])
class PyBuiltinCacheTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-92868"])
  fun `a reparse of the types stub gives a valid None type`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", "x = None")
    val oldTypesFile = runReadAction { PyBuiltinCache.getInstance(file).noneType!!.pyClass.containingFile }

    FileContentUtilCore.reparseFiles(oldTypesFile.virtualFile)

    runReadAction {
      val noneType = PyBuiltinCache.getInstance(file).noneType!!
      noneType.assertValid("NoneType")
      assertNotSame(oldTypesFile, noneType.pyClass.containingFile)
    }
  }

  @Test
  @TestFor(issues = ["PY-93538"])
  fun `a cache without builtins gives Unknown for str or unicode`() {
    val cache = PyBuiltinCache.getInstance(null)
    assertNull(cache.strType)

    assertEquals(PyAnyType.unknown, cache.strOrUnicodeType)
  }
}
