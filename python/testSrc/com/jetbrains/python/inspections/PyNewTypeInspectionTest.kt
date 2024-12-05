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

  override fun getInspectionClass(): Class<out PyInspection> = PyNewTypeInspection::class.java
}