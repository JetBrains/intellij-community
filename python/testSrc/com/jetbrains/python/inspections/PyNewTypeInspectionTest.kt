// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyNewTypeInspectionTest : PyInspectionTestCase() {
  fun testNewTypeCannotBeSubclassed() {
    doTestByText(
      """
        from typing import NewType
      
        Base1 = NewType("Base1", str)
      
        class Derived1(<error descr="'Base1' cannot be subclassed">Base1</error>):
            pass
        
        Base2 = NewType("Base2", str)
        
        class Derived2(<error descr="'Base1' cannot be subclassed">Base1</error>, <error descr="'Base2' cannot be subclassed">Base2</error>):
            pass
      """.trimIndent()
    )
  }

  fun testVariableNameDoesNotMatchNewTypeName() {
    doTestByText(
      """
        from typing import NewType
        
        <warning descr="Variable name 'A' does not match NewType name 'B'">A</warning> = NewType("B", int)
        X = NewType("X", str)
      """.trimIndent()
    )
  }

  fun testType() {
    doTestByText(
      """
        from typing import NewType, Literal, TypedDict, TypeVar
        
        class TD(TypedDict):
            name: str

        NewType1 = NewType(tp=int, name="NewType1")
        NewType2 = NewType("NewType2", NewType1)
        NewType3 = NewType("NewType3", <warning descr="Expected class">int | str</warning>)
        NewType4 = NewType("NewType4", <warning descr="Expected class">1</warning>)
        NewType5 = NewType("NewType5", <warning descr="NewType cannot be used with 'Literal[1]'">Literal[1]</warning>)
        NewType6 = NewType("NewType6", <warning descr="NewType cannot be used with 'TypedDict'">TD</warning>)
        NewType7 = NewType("NewType7", list[int])
        T = TypeVar("T")
        NewType8 = NewType("NewType8", <warning descr="NewType cannot be generic">list[T]</warning>)
      """.trimIndent()
    )
  }

  override fun getInspectionClass(): Class<out PyInspection> = PyNewTypeInspection::class.java
}