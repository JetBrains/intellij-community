// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyInspectionTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

abstract class PyNewTypeInspectionTest : PyInspectionTestCase() {
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

  fun testNewTypeInstanceAsInitSubclassArgument() {
    doTestByText(
      """
        from typing import NewType

        SomeType = NewType("SomeType", str)

        class Test:
            def __init_subclass__(cls, /, some_param: SomeType, **kwargs: Any):
                pass

        class Test2(Test, some_param=SomeType("aba")):
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
        NewType3 = NewType("NewType3", <warning descr="Expected class">1</warning>)
        NewType4 = NewType("NewType4", <warning descr="NewType cannot be used with 'Literal[1]'">Literal[1]</warning>)
        NewType5 = NewType("NewType5", <warning descr="NewType cannot be used with 'TypedDict'">TD</warning>)
        NewType6 = NewType("NewType6", list[int])
        T = TypeVar("T")
        NewType7 = NewType("NewType7", <warning descr="NewType cannot be generic">list[T]</warning>)
      """.trimIndent()
    )
  }

  override fun getInspectionClass(): Class<out PyInspection> = PyNewTypeInspection::class.java
}

class PyNewTypeInspectionTestPython38 : PyNewTypeInspectionTest() {
  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return PyLightProjectDescriptor(LanguageLevel.PYTHON39);
  }
}

class PyNewTypeInspectionTestPython314 : PyNewTypeInspectionTest() {
  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return PyLightProjectDescriptor(LanguageLevel.PYTHON314);
  }

  fun testUnionType() {
    doTestByText(
      """
        from typing import NewType, Literal, TypedDict, TypeVar
        
        NewType1 = NewType("NewType1", <warning descr="Expected class">int | str</warning>)
      """.trimIndent()
    )
  }
}