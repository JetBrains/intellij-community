// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.ast.PyAstFromImportStatement
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
class PyLazyImportResolveTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-91818"], classes = [PyAstFromImportStatement::class])
  @TestCaseOptions(copyDirectoryToProject = [CopyDirectory("resolve/lazyRelativeImport", "")])
  fun `relative lazy import resolves`() = test("""
    lazy from . import Foo

    Foo()
  """.trimIndent())
}
