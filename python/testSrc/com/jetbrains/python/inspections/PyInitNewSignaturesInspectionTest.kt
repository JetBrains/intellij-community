// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyInitNewSignatureInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection?> = PyInitNewSignatureInspection::class.java

  fun testBasic() = doMultiFileTest("test.py")

  fun testWrongTypes() =
    doTestByText("""
      class Base:
          def __new__(cls, a: int, b: str): ...
  
      class Derived1(Base):
          def __init__(self, a: int, b: str): ...
          
      class Derived2(Base):
          # b has wrong type
          def __init__<warning descr="Signature is not compatible to __new__">(self, a: int, b: int)</warning>: ...
      """.trimIndent())

  fun testWrongNames() =
    doTestByText("""
      class Base1:
          def __new__(cls, first: int, second: str): ...
  
      class Derived1(Base1):
          def __init__<warning descr="Signature is not compatible to __new__">(self, a: int, b: str)</warning>: ...
      
      class Base2:
          def __init__(self, first: int, second: str): ...
      
      class Derived2(Base2):
          def __new__<warning descr="Signature is not compatible to __init__">(self, a: int, b: str)</warning>: ...
      """.trimIndent())

  fun testPositionalOnly() =
    doTestByText("""
      class Base:
          def __new__(cls, a: int, b: str, /): ...
  
      class Derived1(Base):
          def __init__(self, blah: int, name_is_not_important: str, /): ...
          
      class Derived2(Base):
          # second param has wrong type
          def __init__<warning descr="Signature is not compatible to __new__">(self, x: int, y: int, /)</warning>: ...
      """.trimIndent())
}
