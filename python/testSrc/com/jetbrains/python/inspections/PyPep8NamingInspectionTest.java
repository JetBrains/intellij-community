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

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyPep8NamingInspectionTest extends PyTestCase {

  public void testFunctionVariable() {
    doTest();
  }

  public void testClassName() {
    doTest();
  }

  public void testArgumentName() {
    doTest();
  }

  public void testFunctionName() {
    doTest();
  }

  public void testImportConstant() {
    doTest();
  }

  public void testImportCamelAsLower() {
    doTest();
  }

  public void testImportLowerAsNonLower() {
    doTest();
  }

  public void testOverridden() {
    doTest();
  }

  public void testTest() {
    doTest();
  }

  public void testOverrideFromModule() {
    myFixture.configureByFiles("inspections/PyPep8NamingInspection/" + getTestName(true) + ".py",
                               "inspections/PyPep8NamingInspection/tmp1.py");
    myFixture.enableInspections(PyPep8NamingInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyPep8NamingInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyPep8NamingInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
