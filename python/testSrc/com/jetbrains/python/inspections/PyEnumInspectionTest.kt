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

  fun testDeclaredEnumMemberType() {
    doTestByText(
      """
        from enum import Enum, auto
        
        class Color(Enum):
          _value_: int
          RED = 1
          GREEN = <warning descr="Type 'str' is not assignable to declared type 'int'">"green"</warning>
          BLUE = auto()
          
          R = RED
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