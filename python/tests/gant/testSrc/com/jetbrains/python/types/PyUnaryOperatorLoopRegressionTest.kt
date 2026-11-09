// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyUnaryOperatorLoopRegressionTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-93578"])
  fun `rotation of two loop variables is not reported`() = test("""
    def rotate():
        dx, dy = 1, 0
        for _ in range(4):
            dx, dy = -dy, dx
    #                ^^^ WARNING Expected type 'Literal[0]', got 'Literal[0, -1]' instead FIXME
    """.trimIndent())
}
