// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@TestFor(classes = [PyUnnecessaryCastInspection::class])
@Subsystems.Inspections
@Layers.Functional
class PyUnnecessaryCastInspectionTest : PyCodeInsightTestCase() {
  override val defaultInspections: Set<Class<out LocalInspectionTool>> = setOf(PyUnnecessaryCastInspection::class.java)

  @Test
  fun `cast of a variable to its type`() = test("""
    from typing import cast

    def f(a: int):
        cast(int, a)
    #   ^^^^^^^^^ WEAK_WARNING Unnecessary cast; type is already 'int'
    """.trimIndent())

  @Test
  fun `cast of a variable declared with a literal type`() = test("""
    from typing import cast, Literal

    one: Literal[1] = 1
    cast(int, one)
    cast(Literal[1], one)  # WEAK_WARNING Unnecessary cast; type is already 'Literal[1]'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-93811"])
  fun `cast of a literal expression to its class`() = test("""
    from typing import cast

    cast(int, 1)  # WEAK_WARNING Unnecessary cast; type is already 'int'
    cast("int", 1)  # WEAK_WARNING Unnecessary cast; type is already 'int'
    cast(int, -1)  # WEAK_WARNING Unnecessary cast; type is already 'int'
    cast(str, "s")  # WEAK_WARNING Unnecessary cast; type is already 'str'
    cast(bool, True)  # WEAK_WARNING Unnecessary cast; type is already 'bool'
    cast(bytes, b"s")  # WEAK_WARNING Unnecessary cast; type is already 'bytes'
    cast(int, (1))  # WEAK_WARNING Unnecessary cast; type is already 'int'
    cast(int, 1 if bool() else 2)  # WEAK_WARNING Unnecessary cast; type is already 'int'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-93811"])
  fun `cast of an enum member to its class`() = test("""
    from enum import Enum
    from typing import cast

    class Color(Enum):
        RED = 1

    cast(Color, Color.RED)  # WEAK_WARNING Unnecessary cast; type is already 'Color'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-93811"])
  fun `cast of a literal expression to its literal type`() = test("""
    from typing import cast, Literal

    cast(Literal[1], 1)  # WEAK_WARNING Unnecessary cast; type is already 'Literal[1]'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-93811"])
  fun `cast of a literal expression to another type`() = test("""
    from typing import cast, Literal

    cast(float, 1)
    cast(object, 1)
    cast(Literal[2], 1)
    cast(str, 1)

    def f(one: Literal[1]):
        cast(int, one if bool() else 1)
    """.trimIndent())

  @Test
  fun `cast to another type`() = test("""
    from typing import cast

    class B: ...
    class C(B): ...

    cast(B, C())  # ok

    a: int | str
    b = cast(str, a)  # ok
    """.trimIndent())

  @Test
  fun `cast of Any`() = test("""
    from typing import Any, cast

    def func(val: Any) -> int:
        return cast(int, val)  # WEAK_WARNING Unnecessary cast; type is already 'int' FIXME # a cast of Any narrows the type
    """.trimIndent())

  @Test
  fun `quick fix removes the cast`() = testQuickFix(
    """
    from typing import cast

    def f(a: int):
        cast(int, a)
    """.trimIndent(),
    PyPsiBundle.message("QFIX.remove.cast.call"),
    """
    from typing import cast

    def f(a: int):
        a
    """.trimIndent(),
    // The inspection registers the weak warning and an information highlight over the whole call, both with the fix.
    expectedMatchCount = 2,
  )
}
