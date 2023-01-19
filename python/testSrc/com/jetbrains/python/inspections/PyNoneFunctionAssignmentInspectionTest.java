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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyNoneFunctionAssignmentInspectionTest extends PyInspectionTestCase {

  public void testPass() {
    doTest();
  }

  public void testReturnNone() {
    doTest();
  }

  public void testNoReturn() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testNoType() {
    doTest();
  }

  // PY-10883
  public void testMethodWithInheritors() {
    doTest();
  }

  // PY-10883
  public void testDecoratedMethod() {
    doTest();
  }

  // PY-28729
  public void testGenericSubstitutedWithNone() {
    doTestByText(
      """
        test1 = max([])
        test2 = max([], default=None)
        test3 = max([], default=0)"""
    );
  }

  // PY-30467
  public void testAssigningAbstractMethodResult() {
    doTestByText("""
                   from abc import ABC, abstractmethod

                   class A(ABC):
                       def get_something(self):
                           something = self.get_another_thing()
                           return something

                       @abstractmethod
                       def get_another_thing(self):
                           pass
                   """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyNoneFunctionAssignmentInspection.class;
  }
}
