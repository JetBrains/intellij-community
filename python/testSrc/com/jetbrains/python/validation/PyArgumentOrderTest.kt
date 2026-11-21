// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Components.Highlighting
@Layers.Functional
@TestFor(classes = [PyCompatibilityVisitor::class])
class PyArgumentOrderTest : PyCodeInsightTestCase() {
  @Test
  fun `positional argument after keyword argument in a call`() = test("""
    def f(*args, **kwargs): ...

    f(a=1, 2)
    #      └ ERROR Positional argument after keyword argument
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80133"])
  fun `positional argument after keyword argument in a class definition`() = test("""
    class B:
        def __init_subclass__(cls, **kwargs): ...

    class C(a=1, B): ...
    #            └ ERROR Positional argument after keyword argument
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80133"])
  fun `repeated keyword argument in a class definition`() = test("""
    class B:
        def __init_subclass__(cls, **kwargs): ...

    class C(B, a=1, a=2): ...
    #               ^^^ ERROR Keyword argument repeated
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80133"])
  fun `valid base class list`() = test("""
    class M(type): ...

    class B:
        def __init_subclass__(cls, **kwargs): ...

    bases = (B,)
    options = {}

    class C(B, *bases, metaclass=M, **options): ...
    """.trimIndent())
}
