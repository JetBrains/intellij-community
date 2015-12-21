/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author vlan
 */
public class PyStringFormatInspectionTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyStringFormatInspection/";

  public void testBasic() {
    doTest();
  }

  // PY-2836
  public void testDictionaryArgument() {
    doTest();
  }

  // PY-4647
  public void testTupleMultiplication() {
    doTest();
  }

  // PY-6756
  public void testSlice() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyStringFormatInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
