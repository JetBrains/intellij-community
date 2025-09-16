// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyAssertTypeInspectionTest : PyInspectionTestCase() {
  fun `test basic`() {
    doTestByText(
      """
        from typing import assert_type

        def greet(name: str) -> None:
          assert_type(name, str)  # OK, inferred type of `name` is `str`
          assert_type(<warning descr="Expected type 'int', got 'str' instead">name</warning>, int)

          assert_type(<warning descr="Expected type 'Any', got 'str' instead">name</warning>, "")
          assert_type(<warning descr="Expected type 'Any', got 'str' instead">name</warning>, unresolved)
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
  fun `test assert self param and Self type`() {
    doTestByText("""
        from typing import Self
        class Shape:
           def set_scale(self, scale: float) -> Self:
              assert_type(self, Self)
              return self
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

  override fun getInspectionClass(): Class<out PyInspection> = PyAssertTypeInspection::class.java
}