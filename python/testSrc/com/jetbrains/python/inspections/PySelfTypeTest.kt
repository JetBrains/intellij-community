// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.codeInspection.LocalInspectionTool
import com.jetbrains.python.fixtures.PyInspectionTestCase

@Subsystems.Inspections
@Layers.Functional
internal class PySelfTypeTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection> = PyAssertTypeInspection::class.java

  override fun getAdditionalInspectionClasses(): List<Class<out LocalInspectionTool>> = listOf(
    PyTypeCheckerInspection::class.java
  )

  // PY-89296
  fun `test call classmethod with Self parameter`() {
    doTestByText("""
                   from typing import assert_type, Self

                   class A:
                       @classmethod
                       def foo(x, y: Self) -> Self: ...

                       @classmethod
                       def bar(x, y: type[Self]) -> Self: ...
                   
                   assert_type(A.foo(A()), A)
                   A.foo(<warning descr="Expected type 'A', got 'type[A]' instead">A</warning>)
                   A.bar(<warning descr="Expected type 'type[A]', got 'A' instead">A()</warning>)
                   assert_type(A.bar(A), A)
                   
                   a = A()
                   assert_type(a.foo(A()), A)
                   a.foo(<warning descr="Expected type 'A', got 'type[A]' instead">A</warning>)
                   a.bar(<warning descr="Expected type 'type[A]', got 'A' instead">A()</warning>)
                   assert_type(a.bar(A), A)
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
                 assert_type(<warning descr="Expected type 'Self@Shape', got 'type[Self@Shape]' instead">cls</warning>, Self)
                 return cls()
             
             def normal_method(self) -> Self:
                 assert_type(<warning descr="Expected type 'type[Self@Shape]', got 'Self@Shape' instead">self</warning>, type[Self])
                 assert_type(self, Self) 
                 return self
    """.trimIndent())
  }
}
