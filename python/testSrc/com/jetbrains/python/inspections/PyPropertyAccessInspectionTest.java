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

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyPropertyAccessInspectionTest extends PyInspectionTestCase {
  public void testTest() {
    doTest();
  }

  // PY-2313
  public void testOverrideAssignment() {
    doTest();
  }

  // PY-20322
  public void testAbcAbstractProperty() {
    doTest();
  }

  // PY-28206
  public void testSlotOverridesProperty() {
    doTestByText(
      "class A(object):\n" +
      "    @property\n" +
      "    def name(self):\n" +
      "        return 'a'\n" +
      "\n" +
      "class B(A):\n" +
      "    __slots__ = ('name',)\n" +
      "\n" +
      "    def __init__(self, name):\n" +
      "        self.name = name"
    );
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyPropertyAccessInspection.class;
  }
}
