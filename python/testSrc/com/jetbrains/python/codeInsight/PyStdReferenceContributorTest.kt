// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyStdReferenceContributor::class])
class PyStdReferenceContributorTest : PyCodeInsightTestCase() {
  @Test
  fun `module dunder all names a missing definition`() = test("""
    def defined(): ...

    __all__ = ["defined", "missing"]
    #                      ^^^^^^^ ERROR Unresolved reference 'missing'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-90959"])
  fun `dunder all of another object names no definition`() = test("""
    from unittest.mock import MagicMock

    mock_module = MagicMock()
    mock_module.__all__ = ["SchemaA", "SchemaB"]
    """.trimIndent())
}
