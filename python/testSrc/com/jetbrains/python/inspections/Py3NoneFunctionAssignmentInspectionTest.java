/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Py3NoneFunctionAssignmentInspectionTest extends PyInspectionTestCase {

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }

  // PY-28729
  public void testGenericSubstitutedWithNone() {
    doTestByText(
      "test1 = max([])\n" +
      "test2 = max([], default=None)\n" +
      "test3 = max([], default=0)"
    );
  }

  // PY-30467
  public void testAssigningAbstractMethodResult() {
    doTestByText("from abc import ABC, abstractmethod\n" +
                 "\n" +
                 "class A(ABC):\n" +
                 "    def get_something(self):\n" +
                 "        something = self.get_another_thing()\n" +
                 "        return something\n" +
                 "\n" +
                 "    @abstractmethod\n" +
                 "    def get_another_thing(self):\n" +
                 "        pass\n");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyNoneFunctionAssignmentInspection.class;
  }
}
