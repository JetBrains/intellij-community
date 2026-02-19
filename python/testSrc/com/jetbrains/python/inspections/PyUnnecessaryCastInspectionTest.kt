// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyUnnecessaryCastInspectionTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<out PyInspection> = PyUnnecessaryCastInspection::class.java

  fun `test basic`() {
    doTestByText(
      """
        from typing import cast

        def f(a: int):
            <weak_warning descr="Unnecessary cast; type is already 'int'">cast(int,</weak_warning>a)
      """.trimIndent()
    )
  }

  fun `test literal`() {
    doTestByText(
      """
        from typing import cast, Literal
  
        one: Literal[1] = 1
        cast(int, one)
        <weak_warning descr="Unnecessary cast; type is already 'Literal[1]'">cast(Literal[1],</weak_warning> one)
      """.trimIndent()
    )
  }

  fun `test union`() {
    doTestByText(
      """
from typing import cast


      """.trimIndent()
    )
  }

  fun `test okay`(){
    doTestByText(
      """
        from typing import cast
  
        class B: ...
        class C(B): ...
  
        cast(B, C())  # ok
        
        a: int | str
        b = cast(str, a)  # ok
      """.trimIndent()
    )
  }

  fun `test quickfix remove`() {
    val text = """
        from typing import cast

        def f(a: int):
            <weak_warning descr="Unnecessary cast; type is already 'int'"><caret>cast(int,</weak_warning> a)
      """.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    configureInspection()
    val hint = PyPsiBundle.message("QFIX.remove.cast.call")
    val action = myFixture.findSingleIntention(hint)
    myFixture.launchAction(action)
    myFixture.checkResult(
      """
        from typing import cast

        def f(a: int):
            a
      """.trimIndent()
    )
  }
}
