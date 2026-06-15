// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyInvalidCastInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass() = PyInvalidCastInspection::class.java

  fun `test basic`() {
    doTestByText(
      """
        from typing import cast

        <warning descr="Cast of type 'str' to type 'int' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'object' first.">cast(int, "a")</warning>

        cast(int, object())  # ok
        cast(object, 1)  # ok

        # variance of generic type arguments is ignored by default
        cast(list[int], ["a"])  # ok
        lint = [1, 2, 3]
        cast(list[object], lint)  # ok
      """.trimIndent()
    )
  }

  fun `test Any`() {
    doTestByText(
      """
        from typing import cast
        
        cast(Any, 1)  # ok
        any: Any = 1
        cast(int, any)  # ok
      """.trimIndent()
    )
  }

  /**
   * test that the common super type is shown in the error message
   */
  fun `test common super type`() {
    doTestByText(
      """
        from typing import cast
        
        class A: pass
        
        class B1(A): pass
        class B2(A): pass
        
        <warning descr="Cast of type 'B1' to type 'B2' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'A' first.">cast(B2, B1())</warning>
      """.trimIndent()
    )
  }

  /**
   * test that the variance of invariant generics is ignored by default
   */
  fun `test generic variance ignored by default`() {
    doTestByText(
      """
        from typing import cast, Sequence

        lint = [1, 2, 3]
        cast(list[object], lint)  # ok

        cast(Sequence[object], lint)  # ok
      """.trimIndent()
    )
  }

  /**
   * test that invariant generics are reported when the "ignore variance" option is disabled
   */
  fun `test generic variance reported when option disabled`() {
    val inspection = PyInvalidCastInspection()
    inspection.ignoreGenericVariance = false
    myFixture.configureByText(
      PythonFileType.INSTANCE,
      """
        from typing import cast, Sequence

        lint = [1, 2, 3]
        <warning descr="Cast of type 'list[int]' to type 'list[object]' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'object' first.">cast(list[object], lint)</warning>

        cast(Sequence[object], lint)  # ok
      """.trimIndent()
    )
    myFixture.enableInspections(inspection)
    myFixture.checkHighlighting(isWarning, isInfo, isWeakWarning)
  }

  /**
   * test that a TypedDict is treated as a dict by default, so casts between a dict and a TypedDict are allowed
   */
  fun `test typed dict treated as dict by default`() {
    doTestByText(
      """
        from typing import TypedDict, cast

        class TD(TypedDict):
            x: int

        def f(data: dict[str, object]):
            cast(TD, data)  # ok
            cast(dict[str, object], TD(x=1))  # ok
      """.trimIndent()
    )
  }

  /**
   * test that casting a dict to a TypedDict is reported when the "ignore TypedDict structure" option is disabled
   */
  fun `test typed dict reported when option disabled`() {
    val inspection = PyInvalidCastInspection()
    inspection.ignoreTypedDictStructure = false
    myFixture.configureByText(
      PythonFileType.INSTANCE,
      """
        from typing import TypedDict, cast

        class TD(TypedDict):
            x: int

        def f(data: dict[str, object]):
            <warning descr="Cast of type 'dict[str, object]' to type 'TD' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'object' first.">cast(TD, data)</warning>
      """.trimIndent()
    )
    myFixture.enableInspections(inspection)
    myFixture.checkHighlighting(isWarning, isInfo, isWeakWarning)
  }

  fun `test quickfix add intermediate cast`() {
    val text = """
        from typing import cast
        
        class A: pass
        
        class B1(A): pass
        class B2(A): pass
        
        <warning descr="Cast of type 'B1' to type 'B2' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'A' first."><caret>cast(B2, B1())</warning>
      """.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    configureInspection()
    val hint = PyPsiBundle.message("QFIX.add.intermediate.cast", "A")
    val action = myFixture.findSingleIntention(hint)
    myFixture.launchAction(action)
    myFixture.checkResult(
      """
        from typing import cast
        
        class A: pass
        
        class B1(A): pass
        class B2(A): pass
        
        cast(B2, cast(A, B1()))
      """.trimIndent()
    )
  }

  fun `test subtype-related unions`() {
    doTestByText("""
      from typing import cast, Literal
      
      type AB = Literal["a", "b"]
      type BC = Literal["b", "c"]
      
      def foo(x: AB):
        cast(BC, x)  # ok
        
      class A1: ...
      class A2(A1): ...
      
      def bar(a1: A1 | None, x2: a2 | None):
        cast(A2 | None, a1)  # ok
        cast(A1 | None, a2)  # ok
        
        <warning descr="Cast of type 'A1 | None' to type 'int | str' may be a mistake because they are not in the same inheritance hierarchy. If this was intentional, cast the expression to 'object' first.">cast(int | str, a1)</warning>
    """.trimIndent())
  }
}
