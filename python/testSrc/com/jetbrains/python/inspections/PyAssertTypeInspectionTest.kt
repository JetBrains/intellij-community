// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.jetbrains.python.fixtures.PyInspectionTestCase

@Subsystems.Inspections
@Layers.Functional
class PyAssertTypeInspectionTest : PyInspectionTestCase() {
  fun `test basic`() {
    doTestByText(
      """
        from typing import assert_type

        def greet(name: str) -> None:
          assert_type(name, str)  # OK, inferred type of `name` is `str`
          assert_type(<warning descr="Expected type 'int', got 'str' instead">name</warning>, int)

          assert_type(<warning descr="Expected type 'Unknown', got 'str' instead">name</warning>, "")
          assert_type(<warning descr="Expected type 'Unknown', got 'str' instead">name</warning>, unresolved)
      """.trimIndent()
    )
  }

  // PY-81606
  fun `test callable`() {
    doTestByText("""
      from typing import assert_type, Callable

      def func1(x: int, /) -> int:
          return 1

      assert_type(func1, Callable[[int], int])
      assert_type(<warning descr="Expected type '(int) -> str', got '(x: int, /) -> int' instead">func1</warning>, Callable[[int], str])
      assert_type(<warning descr="Expected type '(str) -> int', got '(x: int, /) -> int' instead">func1</warning>, Callable[[str], int])
      
      def func2(x: int) -> int:
          return 1

      assert_type(<warning descr="Expected type '(int) -> int', got '(x: int) -> int' instead">func2</warning>, Callable[[int], int])
    """.trimIndent())
  }

  // PY-76860
  fun `test type Self in class methods`() {
    doTestByText("""
        from typing import Self, assert_type
        class Shape:
             @classmethod
             def from_config(cls, config: dict[str, float]) -> Self:
                 assert_type(cls, type[Self])
                 assert_type(<warning descr="Expected type 'Self@Shape', got 'type[Self@Shape]' instead">cls</warning>, Self) # E
                 ...
             
             def normal_method(self) -> Self:
                 assert_type(<warning descr="Expected type 'type[Self@Shape]', got 'Self@Shape' instead">self</warning>, type[Self]) # E
                 assert_type(self, Self) 
                 ...
    """.trimIndent())
  }

  /**
   * `Any` and `Unknown` are one gradual type, so `assert_type` must accept either spelling in a nested
   * position too. An omitted type argument gives `Unknown`, the way `ty` and `basedpyright` show it.
   */
  @TestFor(issues = ["PY-91107"])
  fun `test Unknown matches Any in a nested position`() {
    doTestByText("""
      from typing import Any, Generic, TypeVar, assert_type

      T = TypeVar("T")

      class Node(Generic[T]):
          label: T
          def __init__(self, label: T | None = None) -> None: ...

      def func(p: list, q: dict, r: tuple) -> None:
          assert_type(p, list[Any])
          assert_type(q, dict[Any, Any])
          assert_type(r, tuple[Any, ...])
          assert_type(Node(), Node[Any])
          assert_type(Node().label, Any)

      def still_reports_a_real_mismatch(p: list[int]) -> None:
          assert_type(<warning descr="Expected type 'list[str]', got 'list[int]' instead">p</warning>, list[str])
    """.trimIndent())
  }

  override fun getInspectionClass(): Class<out PyInspection> = PyAssertTypeInspection::class.java
}