// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyInspectionTestCase

@Subsystems.Inspections
@Layers.Functional
class PyUnnecessaryCastInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection> = PyUnnecessaryCastInspection::class.java

  fun `test basic`() {
    doTestByText(
      """
        from typing import cast

        def f(a: int):
            <weak_warning descr="Unnecessary cast; type is already 'int'">cast(int,</weak_warning>a)
      """.trimIndent()
    )
  }

  fun `test literal`() {
    doTestByText(
      """
        from typing import cast, Literal
  
        one: Literal[1] = 1
        cast(int, one)
        <weak_warning descr="Unnecessary cast; type is already 'Literal[1]'">cast(Literal[1],</weak_warning> one)
      """.trimIndent()
    )
  }

  fun `test union`() {
    doTestByText(
      """
from typing import cast


      """.trimIndent()
    )
  }

  fun `test okay`(){
    doTestByText(
      """
        from typing import cast
  
        class B: ...
        class C(B): ...
  
        cast(B, C())  # ok
        
        a: int | str
        b = cast(str, a)  # ok
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of Any`() {
    doTestByText(
      """
        from typing import Any, cast

        def func(val: Any) -> int:
            return cast(int, val)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of a value to Any`() {
    doTestByText(
      """
        from typing import Any, cast

        def func(val: int) -> Any:
            return cast(Any, val)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of a collection of Any`() {
    doTestByText(
      """
        from typing import Any, cast

        def func(val: list[Any]) -> list[int]:
            return cast(list[int], val)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of a callable to a callable with any parameters`() {
    doTestByText(
      """
        from typing import Callable, cast

        def func(cb: Callable[[str], int]) -> Callable[..., int]:
            return cast(Callable[..., int], cb)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of a callable to its type`() {
    doTestByText(
      """
        from typing import Callable, cast

        def func(cb: Callable[[str], int]) -> Callable[[str], int]:
            return <weak_warning descr="Unnecessary cast; type is already '(str) -> int'">cast(Callable[[str], int],</weak_warning> cb)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of an unresolved value`() {
    doTestByText(
      """
        from typing import cast

        def func() -> int:
            return cast(int, undefined_name)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of Any to Any`() {
    doTestByText(
      """
        from typing import Any, cast

        def func(val: Any) -> Any:
            return <weak_warning descr="Unnecessary cast; type is already 'Any'">cast(Any,</weak_warning> val)
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-92667"])
  fun `test cast of a collection of Any to the same type`() {
    doTestByText(
      """
        from typing import Any, cast

        def func(val: list[Any]) -> list[Any]:
            return <weak_warning descr="Unnecessary cast; type is already 'list[Any]'">cast(list[Any],</weak_warning> val)
      """.trimIndent()
    )
  }

  fun `test quickfix remove`() {
    val text = """
        from typing import cast

        def f(a: int):
            <weak_warning descr="Unnecessary cast; type is already 'int'"><caret>cast(int,</weak_warning> a)
      """.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    configureInspection()
    val hint = PyPsiBundle.message("QFIX.remove.cast.call")
    val action = myFixture.findSingleIntention(hint)
    myFixture.launchAction(action)
    myFixture.checkResult(
      """
        from typing import cast

        def f(a: int):
            a
      """.trimIndent()
    )
  }
}
