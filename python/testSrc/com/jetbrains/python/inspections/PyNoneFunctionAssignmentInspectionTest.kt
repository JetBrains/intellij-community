/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyNoneFunctionAssignmentInspectionTest : PyInspectionTestCase() {
  fun testPass() {
    doTest()
  }

  fun testReturnNone() {
    doTest()
  }

  fun testNoReturn() {
    doTest()
  }

  fun testTrueNegative() {
    doTest()
  }

  fun testNoType() {
    doTest()
  }

  // PY-10883
  fun testMethodWithInheritors() {
    doTest()
  }

  // PY-10883
  fun testDecoratedMethod() {
    doTest()
  }

  // PY-28729
  fun testGenericSubstitutedWithNone() {
    doTestByText(
      """
        test1 = max([])
        test2 = max([], default=None)
        test3 = max([], default=0)
        """.trimIndent()
    )
  }

  // PY-30467
  fun testAssigningAbstractMethodResult() {
    doTestByText(
      """
                   from abc import ABC, abstractmethod
                   
                   class A(ABC):
                       def get_something(self):
                           something = self.get_another_thing()
                           return something
                   
                       @abstractmethod
                       def get_another_thing(self):
                           pass
                   
                   """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-80351"])
  fun `test used in other contexts`() {
    doTestByText("""
             def f() -> None:
                 return None
             
             <weak_warning descr="Function 'f' doesn't return anything">y = f() # comment</weak_warning>
             print(<weak_warning descr="Function 'f' doesn't return anything">f()</weak_warning>)
             f"{<weak_warning descr="Function 'f' doesn't return anything">f()</weak_warning>}"
             [<weak_warning descr="Function 'f' doesn't return anything">f()</weak_warning>]
             (<weak_warning descr="Function 'f' doesn't return anything">f()</weak_warning>,)
             
             f()
             """.trimIndent()
    )
  }

  override fun getInspectionClass(): Class<out PyInspection?> {
    return PyNoneFunctionAssignmentInspection::class.java
  }
}
