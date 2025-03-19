// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyAssertTypeInspectionTest : PyInspectionTestCase() {
  fun testBasic() {
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

  override fun getInspectionClass(): Class<out PyInspection> = PyAssertTypeInspection::class.java
}