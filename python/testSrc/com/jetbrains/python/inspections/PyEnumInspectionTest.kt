// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyEnumInspectionTest : PyInspectionTestCase() {
  fun testEnumWithMembersCannotBeSubclassed() {
    doTestByText(
      """
        from enum import Enum, EnumType
        
        class EnumWithNoMembers(metaclass=EnumType):
          pass

        class Shape(EnumWithNoMembers):
          SQUARE = 1
          CIRCLE = 2

        class ExtendedShape(<error descr="Enum class 'Shape' is final and cannot be subclassed">Shape</error>):
          TRIANGLE = 3
        
        class Color(Enum):
          RED = 1
        
        class ShapeAndColor(<error descr="Enum class 'Shape' is final and cannot be subclassed">Shape</error>, EnumWithNoMembers, <error descr="Enum class 'Color' is final and cannot be subclassed">Color</error>):
          pass
      """.trimIndent()
    )
  }

  fun testEnumMemberAsInitSubclassArgument() {
    doTestByText(
      """
        from enum import Enum
        from typing import Any

        class SomeEnum(Enum):
            A = "A"
            B = "B"

        class Test:
            def __init_subclass__(cls, /, some_param: SomeEnum, **kwargs: Any):
                pass

        class Test2(Test, some_param=SomeEnum.A):
            pass
      """.trimIndent()
    )
  }

  fun testDeclaredEnumMemberType() {
    doTestByText(
      """
        from enum import Enum
        
        class Color(Enum):
          _value_: int
          RED = 1
          GREEN = <warning descr="Type 'str' is not assignable to declared type 'int'">"green"</warning>
          
          R = RED
      """.trimIndent()
    )
  }

  // PY-80565
  fun testEnumMemberAssignedToAuto() {
    doTestByText(
      """
        from enum import auto, Enum
        
        class MyIntEnum(Enum):
            _value_: int
            FOO = auto()
        
        class MyStrEnum(Enum):
            _value_: str
            FOO = <warning descr="Type 'int' is not assignable to declared type 'str'">auto()</warning>
        """.trimIndent()
    )
  }

  // PY-80565
  fun testCustomGenerateNextValue() {
    doTestByText(
      """
        from enum import auto, Enum
        
        class MyVal: ...

        class MyEnumBase(Enum):
            _value_: MyVal        

            @staticmethod
            def _generate_next_value_(name: str, start: int, count: int, last_values: list[MyVal]) -> MyVal: ...
        
        
        class MyEnumDerived(MyEnumBase):
            FOO = auto()
            BAR = <warning descr="Type 'int' is not assignable to declared type 'MyVal'">2</warning>
      """.trimIndent()
    )
  }

  // PY-80565
  fun testCustomGenerateNextValueMultiFile() {
    doMultiFileTest()
  }

  // PY-80565
  fun testStrEnumMemberAssignedToAuto() {
    doTestByText(
      """
        from enum import auto, StrEnum
        
        class Example(StrEnum):
            FOO = auto()
            BAR = <warning descr="Type 'int' is not assignable to declared type 'str'">1</warning>
        """.trimIndent()
    )
  }

  fun testTypeAnnotationsForEnumMember() {
    doTestByText(
      """
        from enum import Enum
        
        class Pet(Enum):
          CAT = 1
          DOG: <warning descr="Type annotations are not allowed for enum members">int</warning> = 2
      """.trimIndent()
    )
  }

  override fun getInspectionClass(): Class<out PyInspection> = PyEnumInspection::class.java
}